package app.pulsify.engine

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import app.pulsify.core.AudioFrame
import app.pulsify.core.Mood
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.min

/**
 * One render thread with its own EGL context, paced by Choreographer.
 *
 * Frame-rate policy (the battery budget):
 *  - 60 fps while touched, while something is animating fast, or while music reacts
 *  - 30 fps idle
 *  - 15 fps asleep (night) or battery saver idle
 *  - 0 fps when paused (the frame callback is removed entirely)
 *
 * Frames are paced in whole vsyncs (every 1st, 2nd, 3rd... vsync), so motion has an even
 * rhythm on 60, 90, 120 and 144 Hz screens instead of irregular skips.
 *
 * Dynamic resolution: everything is drawn into an offscreen buffer at [scale] of the screen
 * and upscaled in one cheap pass. The scale adapts to how long frames take, so heavy scenes
 * never starve the launcher of GPU time.
 */
class GlLoop(
    context: Context,
    private val provideInput: (SceneInput, Float) -> Unit,
    private val onScaleWanted: ((Float) -> Unit)? = null,
) {
    val scene = SkyScene(context)
    val world = WorldScene(context)
    // Parts are compiled on first use only (and the creature you leave is released), so opening
    // a creature costs one shader, not all of them, and only what is on screen sits in memory.
    private var skyState = 0      // 0 not loaded, 1 ok, -1 failed
    private var worldState = 0
    private val skyOk get() = skyState == 1
    private val worldOk get() = worldState != -1
    private val okCreatures = HashSet<Creature>()
    private val badCreatures = HashSet<Creature>()
    private var loadedCreature: Creature? = null
    private var rw = 0; private var rh = 0
    val mochi = MochiScene(context)
    val kalboob = KalboobScene(context)
    val kitty = KittyScene(context)
    val kiwi = KiwiScene(context)
    val glowy = GlowyScene(context)
    val gromble = GrombleScene(context)
    val nutty = NuttyScene(context)
    val bolt = BoltScene(context)
    val gloop = GloopScene(context)
    private val creatures: List<Creature> = listOf(mochi, kalboob, kitty, kiwi, glowy, gromble, nutty, bolt, gloop)
    /** Discoveries and bond XP from the sky and every creature. */
    var listener: SceneListener? = null
        set(v) { field = v; scene.listener = v; creatures.forEach { it.listener = v } }
    /** The creature for character id 1, 2, ... (0 = sky only). */
    private val active: Creature? get() = creatures.getOrNull(input.character - 1)?.takeIf { it in okCreatures && it === loadedCreature }
    private val worldOn: Boolean get() = input.world >= 0 && input.video < 0 && worldState == 1
    private val video = VideoScene(context)
    val input = SceneInput()
    @Volatile var batterySaver = false
    /** Widget creatures (home-screen friends), when this loop has a feed of them. */
    val buddies = BuddyLayer(context)
    var buddyFeed: BuddyFeed?
        get() = buddies.feed
        set(v) { handler.post { buddies.feed = v } }
    /** Home-screen page under the screen (fractional mid-swipe), from the launcher. */
    @Volatile var pagePos = 0f
    private var lastPage = Float.NaN
    private var pageVel = 0f
    private var lag = 0f; private var lagV = 0f
    private var swipeTravel = 0f; private var swipeMax = 0f; private var swipeDir = 0f; private var settle = 0f
    private var lastSwipeReact = 0L

    /** Off in the friends playground, where only the widget creatures are shown. */
    @Volatile var showMainCreature = true

    private val thread = HandlerThread("pulsify-gl", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var config: EGLConfig? = null
    private var sceneReady = false

    private var running = false
    private var choreographer: Choreographer? = null
    private var lastFrameNs = 0L

    // Adaptive resolution: an offscreen buffer at `scale` of the surface, upscaled each frame.
    var scale = 1f; private set
    private var maxScale = 1f
    private var minScale = 0.5f
    private var surfW = 1; private var surfH = 1
    private var fbo = 0; private var fboTex = 0; private var fbW = 0; private var fbH = 0
    private var blitProg = 0; private var blitVbo = 0; private var blitTexLoc = -1
    private var workAvgNs = 0f
    private var lastScaleChangeNs = 0L
    private var slowFrames = 0

    // Vsync pacing.
    private var lastVsyncNs = 0L
    private var vsyncNs = 16_666_667f
    private var vsyncsSinceRender = 0

    // Stats for the UI.
    @Volatile var fps = 0f; private set
    @Volatile var targetFps = 30; private set
    /** In-app previews: never more than this while nobody is touching (0 = no cap). */
    @Volatile var fpsCap = 0
    @Volatile private var touching = false
    private var frameCount = 0
    private var fpsWindowStart = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            choreographer?.postFrameCallback(this)
            try {
                renderFrame(frameTimeNanos)
            } catch (t: Throwable) {
                onFrameError(t)
            }
        }
    }

    init {
        handler.post { choreographer = Choreographer.getInstance() }
    }

    fun post(block: () -> Unit) {
        postSafe(block)
    }

    private fun postSafe(block: () -> Unit): Boolean = handler.post {
        try { block() } catch (t: Throwable) { onFrameError(t) }
    }

    /** Attach a window surface (wallpaper holder surface, or a Surface around a SurfaceTexture). */
    fun attach(surface: Surface, width: Int, height: Int) {
        handler.post attach@{
            try { attachNow(surface, width, height) } catch (t: Throwable) {
                Log.e("Pulsify", "attach failed", t); app.pulsify.core.CrashLog.note("attach failed: ${t.message}")
            }
        }
    }

    private fun attachNow(surface: Surface, width: Int, height: Int) {
        run {
            if (!initEgl()) return@run
            destroySurface()
            eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)) {
                Log.e("Pulsify", "eglMakeCurrent failed: 0x" + Integer.toHexString(EGL14.eglGetError()))
                return@run
            }
            EGL14.eglSwapInterval(display, 1)
            sceneReady = true
            surfW = maxOf(1, width); surfH = maxOf(1, height)
            // Aim for about 720 px on the short side: sharp after upscaling, far cheaper than native.
            val short = minOf(surfW, surfH).toFloat()
            maxScale = (1000f / short).coerceIn(0.5f, 1f)
            minScale = (420f / short).coerceIn(0.35f, 1f)
            if (scale > maxScale || scale == 1f) scale = (720f / short).coerceIn(minScale, maxScale)
            setupTarget()
            lastFrameNs = 0L
        }
    }

    /** (Re)creates the offscreen buffer for the current scale and sizes every scene to it. */
    private fun setupTarget() {
        val w = maxOf(1, (surfW * scale).toInt()); val h = maxOf(1, (surfH * scale).toInt())
        val direct = scale >= 0.99f
        if (!direct && (w != fbW || h != fbH || fbo == 0)) {
            releaseTarget()
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0); fboTex = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, w, h, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glGenFramebuffers(1, ids, 0); fbo = ids[0]
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex, 0)
            val ok = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            if (!ok) {
                // This GPU can't render to that buffer: draw at native size instead.
                releaseTarget(); scale = 1f; maxScale = 1f; minScale = 1f
                app.pulsify.core.CrashLog.note("GL: offscreen buffer unsupported, drawing at native size")
            } else { fbW = w; fbH = h }
            if (ok && blitProg == 0) initBlit()
        }
        if (scale >= 0.99f) releaseTarget()
        val rw = if (fbo != 0) fbW else surfW; val rh = if (fbo != 0) fbH else surfH
        sizeAll(rw, rh)
    }

    private fun initBlit() {
        val vs = "attribute vec2 aPos;\nvarying vec2 vUv;\nvoid main(){ vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }\n"
        val fs = "precision mediump float;\nvarying vec2 vUv;\nuniform sampler2D uTex;\nvoid main(){ gl_FragColor = vec4(texture2D(uTex, vUv).rgb, 1.0); }\n"
        blitProg = Gl.program(vs, fs)
        blitTexLoc = GLES20.glGetUniformLocation(blitProg, "uTex")
        val ids = IntArray(1); GLES20.glGenBuffers(1, ids, 0); blitVbo = ids[0]
        val q = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
        val fb = java.nio.ByteBuffer.allocateDirect(q.size * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().put(q)
        fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, blitVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, q.size * 4, fb, GLES20.GL_STATIC_DRAW)
    }

    private fun releaseTarget() {
        if (fbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (fboTex != 0) GLES20.glDeleteTextures(1, intArrayOf(fboTex), 0)
        fbo = 0; fboTex = 0; fbW = 0; fbH = 0
    }

    fun resize(width: Int, height: Int) {
        postSafe { surfW = maxOf(1, width); surfH = maxOf(1, height); setupTarget() }
    }

    private fun sizeAll(width: Int, height: Int) {
        rw = width; rh = height
        if (skyOk) scene.onSurfaceChanged(width, height)
        if (worldState == 1) safeInit("world size") { world.onSurfaceChanged(width, height) }
        loadedCreature?.takeIf { it in okCreatures }?.onSurfaceChanged(width, height)
        video.onSurfaceChanged(width, height)
    }

    /** Loads what this frame shows and releases what it no longer shows. Each part compiles on its
     *  own: a shader this GPU rejects disables only that part. */
    /**
     * Swiping home-screen pages pushes the creature along (it slides with the pages, then
     * springs back with a little overshoot) and, once the pages settle, it reacts.
     */
    private fun swipePhysics(dt: Float, c: Creature) {
        val p = pagePos
        if (lastPage.isNaN()) lastPage = p
        val v = (p - lastPage) / max(dt, 1e-3f)
        lastPage = p
        pageVel = pageVel * 0.55f + v * 0.45f
        val target = (-pageVel * 0.045f).coerceIn(-0.16f, 0.16f)
        lagV += ((target - lag) * 140f - lagV * 11f) * dt
        lag += lagV * dt
        if (abs(pageVel) > 0.08f) {
            swipeTravel += abs(pageVel) * dt; swipeMax = max(swipeMax, abs(pageVel)); swipeDir = sign(pageVel); settle = 0f
        } else {
            settle += dt
            if (settle > 0.2f && swipeTravel > 0.45f) {
                val now = System.currentTimeMillis()
                if (now - lastSwipeReact > 2500) {
                    lastSwipeReact = now
                    try { c.swiped(swipeDir, swipeMax > 3f || swipeTravel > 1.6f) } catch (_: Throwable) {}
                }
                swipeTravel = 0f; swipeMax = 0f
            } else if (settle > 0.2f) { swipeTravel = 0f; swipeMax = 0f }
        }
        if (abs(lag) < 0.0005f && abs(lagV) < 0.001f) { lag = 0f; lagV = 0f }
    }

    private fun ensureParts() {
        val wantWorld = input.world >= 0 && input.video < 0
        val wantSky = input.world < 0 && input.video < 0
        if (wantSky && skyState == 0) {
            skyState = if (safeInit("sky") { scene.onSurfaceCreated(); if (rw > 0) scene.onSurfaceChanged(rw, rh) }) 1 else -1
        } else if (!wantSky && skyState == 1) { scene.release(); skyState = 0 }
        if (wantWorld && worldState == 0) {
            worldState = if (safeInit("world") { world.onSurfaceCreated(); if (rw > 0) world.onSurfaceChanged(rw, rh) }) 1 else -1
        } else if (!wantWorld && worldState == 1) { world.release(); worldState = 0 }
        val want = if (input.video >= 0 || !showMainCreature) null else creatures.getOrNull(input.character - 1)
        if (want !== loadedCreature) {
            loadedCreature?.let { if (it in okCreatures) { it.release(); okCreatures.remove(it) } }
            loadedCreature = want
            if (want != null && want !in badCreatures) {
                if (safeInit(want.javaClass.simpleName) { want.onSurfaceCreated(); if (rw > 0) want.onSurfaceChanged(rw, rh) }) okCreatures += want
                else badCreatures += want
            }
        }
        video.ensure(input.video)
    }

    private inline fun safeInit(what: String, block: () -> Unit): Boolean = try {
        block(); true
    } catch (t: Throwable) {
        Log.e("Pulsify", "$what failed to start", t)
        app.pulsify.core.CrashLog.note("GL: $what failed: ${t.message}")
        false
    }

    /** Blocks until the EGL surface is gone, so the caller may release the window safely. */
    fun detach() {
        val latch = CountDownLatch(1)
        val queued = handler.post {
            destroySurface()
            latch.countDown()
        }
        if (!queued) return
        latch.await(400, TimeUnit.MILLISECONDS)
    }

    fun setRunning(run: Boolean) {
        handler.post {
            if (run == running) return@post
            running = run
            video.setPlaying(run)
            val c = choreographer ?: return@post
            if (run) {
                lastFrameNs = 0L; lastVsyncNs = 0L; vsyncsSinceRender = 0
                c.postFrameCallback(frameCallback)
            } else {
                c.removeFrameCallback(frameCallback)
                fps = 0f
            }
        }
    }

    // Touch, in normalised screen coordinates (0..1, y down).
    // The creature gets first look at every touch; what it doesn't use goes to the sky.
    private var creatureOwnsGesture = false
    fun touchDown(nx: Float, ny: Float) = postSafe {
        touching = true
        creatureOwnsGesture = active?.touchDown(nx, ny) == true
        if (!creatureOwnsGesture) { if (worldOn) world.touch(nx, ny) else if (skyOk) scene.touchDown(nx, ny) }
    }
    fun touchMove(nx: Float, ny: Float) = postSafe {
        active?.touchMove(nx, ny)
        if (!creatureOwnsGesture && !worldOn && skyOk) scene.touchMove(nx, ny)
    }
    fun touchUp() = postSafe {
        touching = false
        active?.touchUp()
        if (!creatureOwnsGesture && !worldOn && skyOk) scene.touchUp()
        creatureOwnsGesture = false
    }
    fun tap(nx: Float, ny: Float) = postSafe {
        if (active?.tapAt(nx, ny) != true) { if (worldOn) world.touch(nx, ny) else if (skyOk) scene.tapAt(nx, ny) }
    }
    fun longPress(nx: Float, ny: Float) = postSafe {
        if (active?.longPressAt(nx, ny) != true) { if (worldOn) world.touch(nx, ny) else if (skyOk) scene.longPressAt(nx, ny) }
    }
    fun setBottomInset(f: Float) = postSafe { creatures.forEach { it.bottomInset = f } }
    fun perform(trick: String) = postSafe { active?.perform(trick) }
    fun greet() = postSafe { if (skyOk) scene.greet(); active?.greet() }

    @Volatile var dead = false; private set

    /** Releases everything on the GL thread and stops it. Waits briefly so the window can go safely. */
    fun quit(waitMs: Long = 0L) {
        if (dead) return
        dead = true
        val latch = CountDownLatch(1)
        val queued = handler.post {
            try {
                running = false
                choreographer?.removeFrameCallback(frameCallback)
                if (sceneReady && eglSurface != EGL14.EGL_NO_SURFACE) {
                    if (skyState == 1) scene.release()
                    if (worldState == 1) world.release()
                    okCreatures.forEach { it.release() }; okCreatures.clear(); loadedCreature = null
                    video.release()
                    buddies.release()
                    releaseTarget()
                    if (blitProg != 0) GLES20.glDeleteProgram(blitProg)
                    if (blitVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(blitVbo), 0)
                    blitProg = 0; blitVbo = 0
                }
                destroySurface()
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(display, eglContext)
                    eglContext = EGL14.EGL_NO_CONTEXT
                }
            } catch (t: Throwable) {
                Log.w("Pulsify", "GL shutdown", t)
            } finally {
                latch.countDown()
                thread.quitSafely()
            }
        }
        if (queued && waitMs > 0) latch.await(waitMs, TimeUnit.MILLISECONDS)
    }

    // A creature (or world) that keeps throwing is switched off; the rest keeps running.
    private var frameErrors = 0
    private fun onFrameError(t: Throwable) {
        frameErrors++
        Log.e("Pulsify", "Frame failed", t)
        if (frameErrors <= 3) app.pulsify.core.CrashLog.note("Frame error: ${t.javaClass.simpleName}: ${t.message} @ ${t.stackTrace.firstOrNull()}")
        val bad = active
        if (frameErrors >= 3 && bad != null) { okCreatures.remove(bad); badCreatures += bad; frameErrors = 0 }
        else if (frameErrors >= 6) { if (worldState == 1) world.release(); worldState = -1; frameErrors = 0 }
        try { EGL14.eglSwapBuffers(display, eglSurface) } catch (_: Throwable) {}
    }

    // ---- GL thread ----

    private fun renderFrame(now: Long) {
        if (eglSurface == EGL14.EGL_NO_SURFACE || !sceneReady) return

        // Learn the display's vsync period from the callbacks themselves (60/90/120/144 Hz).
        if (lastVsyncNs != 0L) {
            val d = (now - lastVsyncNs).toFloat()
            if (d in 4_000_000f..40_000_000f) vsyncNs = vsyncNs * 0.95f + d * 0.05f
        }
        lastVsyncNs = now
        vsyncsSinceRender++

        // Video loops draw exactly when the decoder has a new frame (24/30 fps, no judder).
        // (At least 4 times a second anyway, so a change of wallpaper is noticed.)
        if (input.video >= 0 && !video.wantsFrame && lastFrameNs != 0L && now - lastFrameNs < 250_000_000L) return
        val audioActive = input.audio.source != AudioFrame.Source.NONE
        val busy = (if (worldOn) world.busy else scene.busy) || active?.busy == true || buddies.busy || abs(lag) > 0.001f || abs(pageVel) > 0.05f
        // Frame budget: full rate while anything moves fast, a steady lower rate when calm.
        targetFps = when {
            batterySaver -> if (busy || audioActive) 30 else 20
            busy || audioActive -> 60
            input.mood == Mood.NIGHT -> 30
            else -> 40
        }.let { if (fpsCap > 0 && !touching) minOf(it, fpsCap) else it }
        // Render on a fixed whole number of vsyncs so the rhythm is even.
        val hz = 1e9f / vsyncNs
        val every = if (targetFps >= 60) maxOf(1, (hz / 60f + 0.15f).toInt()) else maxOf(1, Math.round(hz / targetFps))
        if (input.video < 0 && lastFrameNs != 0L && vsyncsSinceRender < every) return
        vsyncsSinceRender = 0
        val dt = if (lastFrameNs == 0L) 1f / 60f else min(0.05f, (now - lastFrameNs) / 1e9f)
        lastFrameNs = now

        val t0 = System.nanoTime()
        provideInput(input, dt)
        ensureParts()
        // Video is drawn straight to the screen at full sharpness (sampling it costs almost nothing).
        val offscreen = fbo != 0 && input.video < 0
        Gl.target = if (offscreen) fbo else 0
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target)
        GLES20.glViewport(0, 0, if (offscreen) fbW else surfW, if (offscreen) fbH else surfH)
        if (input.video >= 0) {
            video.draw()
        } else if (worldOn) {
            world.update(dt, input); world.draw(input)
        } else if (skyOk) {
            scene.update(dt, input); scene.draw(input)
        } else {
            GLES20.glClearColor(0f, 0f, 0f, 1f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        if (showMainCreature) active?.let { c ->
            c.setGround(if (worldOn) world.groundY else Float.NaN); c.update(dt, input)
            swipePhysics(dt, c)
            val vw = if (offscreen) fbW else surfW; val vh = if (offscreen) fbH else surfH
            val px = (lag * vw).toInt()
            if (px != 0) GLES20.glViewport(px, 0, vw, vh)
            c.draw(input)
            if (px != 0) GLES20.glViewport(0, 0, vw, vh)
        }
        if (buddies.feed != null) {
            try {
                buddies.frame(dt, input, Gl.target, if (offscreen) fbW else surfW, if (offscreen) fbH else surfH)
            } catch (t: Throwable) {
                Log.e("Pulsify", "buddies failed", t); app.pulsify.core.CrashLog.note("buddies: ${t.message}")
                buddies.release(); buddies.feed = null
            }
            Gl.target = if (offscreen) fbo else 0
        }
        if (offscreen) {
            // Upscale the offscreen frame to the screen in one textured triangle.
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, surfW, surfH)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glUseProgram(blitProg)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex)
            GLES20.glUniform1i(blitTexLoc, 0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, blitVbo)
            GLES20.glEnableVertexAttribArray(0)
            GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        }
        EGL14.eglSwapBuffers(display, eglSurface)
        val work = (System.nanoTime() - t0).toFloat()

        // Stats.
        frameCount++
        if (fpsWindowStart == 0L) fpsWindowStart = now
        if (now - fpsWindowStart >= 1_000_000_000L) {
            fps = frameCount * 1e9f / (now - fpsWindowStart)
            frameCount = 0
            fpsWindowStart = now
        }

        // Dynamic resolution with hysteresis. Work includes the swap, which blocks when the GPU
        // falls behind, so a GPU-bound frame shows up here too.
        workAvgNs = if (workAvgNs == 0f) work else workAvgNs * 0.9f + work * 0.1f
        val budget = vsyncNs * every
        if (work > budget * 1.1f) slowFrames++ else if (slowFrames > 0) slowFrames--
        if (maxScale - minScale < 0.01f || input.video >= 0) return
        if ((workAvgNs > budget * 0.6f || slowFrames > 6) && scale > minScale + 0.01f && now - lastScaleChangeNs > 1_500_000_000L) {
            scale = maxOf(minScale, scale - 0.1f); lastScaleChangeNs = now; slowFrames = 0
            setupTarget()
            onScaleWanted?.invoke(scale)
        } else if (workAvgNs < budget * 0.28f && scale < maxScale - 0.01f && now - lastScaleChangeNs > 10_000_000_000L) {
            scale = minOf(maxScale, scale + 0.05f); lastScaleChangeNs = now
            setupTarget()
            onScaleWanted?.invoke(scale)
        }
    }

    private fun initEgl(): Boolean {
        if (eglContext != EGL14.EGL_NO_CONTEXT) return true
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) return false
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 0, EGL14.EGL_DEPTH_SIZE, 0, EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) || num[0] == 0) return false
        config = configs[0]
        eglContext = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        return eglContext != EGL14.EGL_NO_CONTEXT
    }

    private fun destroySurface() {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, eglSurface)
            eglSurface = EGL14.EGL_NO_SURFACE
        }
    }
}
