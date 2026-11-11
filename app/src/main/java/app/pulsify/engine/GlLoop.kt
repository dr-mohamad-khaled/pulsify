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
import kotlin.math.max
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
 * Dynamic resolution: when the frame work gets close to the budget the loop asks its owner
 * to shrink the surface buffer ([onScaleWanted]); the compositor upscales for free.
 */
class GlLoop(
    context: Context,
    private val provideInput: (SceneInput, Float) -> Unit,
    private val onScaleWanted: ((Float) -> Unit)? = null,
) {
    val scene = SkyScene(context)
    val world = WorldScene(context)
    private var skyOk = false
    private var worldOk = false
    private val okCreatures = HashSet<Creature>()
    val mochi = MochiScene(context)
    val kalboob = KalboobScene(context)
    val kitty = KittyScene(context)
    val kiwi = KiwiScene(context)
    val glowy = GlowyScene(context)
    val gromble = GrombleScene(context)
    private val creatures: List<Creature> = listOf(mochi, kalboob, kitty, kiwi, glowy, gromble)
    /** Discoveries and bond XP from the sky and every creature. */
    var listener: SceneListener? = null
        set(v) { field = v; scene.listener = v; creatures.forEach { it.listener = v } }
    /** The creature for character id 1, 2, ... (0 = sky only). */
    private val active: Creature? get() = creatures.getOrNull(input.character - 1)?.takeIf { it in okCreatures }
    private val worldOn: Boolean get() = input.world >= 0 && worldOk
    val input = SceneInput()
    @Volatile var batterySaver = false

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

    // Adaptive resolution.
    var scale = 0.8f; private set
    private var workAvgNs = 0f
    private var lastScaleChangeNs = 0L

    // Stats for the UI.
    @Volatile var fps = 0f; private set
    @Volatile var targetFps = 30; private set
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
            if (!sceneReady) {
                // Each part compiles on its own: a shader this GPU rejects disables only that part.
                skyOk = safeInit("sky") { scene.onSurfaceCreated() }
                worldOk = safeInit("world") { world.onSurfaceCreated() }
                creatures.forEach { c -> if (safeInit(c.javaClass.simpleName) { c.onSurfaceCreated() }) okCreatures += c }
                sceneReady = true
            }
            sizeAll(width, height)
            lastFrameNs = 0L
        }
    }

    fun resize(width: Int, height: Int) {
        postSafe { sizeAll(width, height) }
    }

    private fun sizeAll(width: Int, height: Int) {
        if (skyOk) scene.onSurfaceChanged(width, height)
        if (worldOk) safeInit("world size") { world.onSurfaceChanged(width, height) }
        okCreatures.forEach { it.onSurfaceChanged(width, height) }
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
            val c = choreographer ?: return@post
            if (run) {
                lastFrameNs = 0L
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
        creatureOwnsGesture = active?.touchDown(nx, ny) == true
        if (!creatureOwnsGesture) { if (worldOn) world.touch(nx, ny) else if (skyOk) scene.touchDown(nx, ny) }
    }
    fun touchMove(nx: Float, ny: Float) = postSafe {
        active?.touchMove(nx, ny)
        if (!creatureOwnsGesture && !worldOn && skyOk) scene.touchMove(nx, ny)
    }
    fun touchUp() = postSafe {
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
                    scene.release(); world.release(); creatures.forEach { it.release() }
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
        if (frameErrors >= 3 && bad != null) { okCreatures.remove(bad); frameErrors = 0 }
        else if (frameErrors >= 6) { worldOk = false; frameErrors = 0 }
        try { EGL14.eglSwapBuffers(display, eglSurface) } catch (_: Throwable) {}
    }

    // ---- GL thread ----

    private fun renderFrame(now: Long) {
        if (eglSurface == EGL14.EGL_NO_SURFACE || !sceneReady) return

        val audioActive = input.audio.source != AudioFrame.Source.NONE
        val busy = (if (worldOn) world.busy else scene.busy) || active?.busy == true
        // Frame budget: smooth only while something is actually happening.
        targetFps = when {
            batterySaver -> if (busy) 30 else 12
            busy || audioActive -> 60
            input.mood == Mood.NIGHT -> 12
            else -> 24
        }
        val minInterval = 1_000_000_000L / targetFps - 4_000_000L
        if (lastFrameNs != 0L && now - lastFrameNs < minInterval) return
        val dt = if (lastFrameNs == 0L) 1f / 60f else min(0.05f, (now - lastFrameNs) / 1e9f)
        lastFrameNs = now

        val t0 = System.nanoTime()
        provideInput(input, dt)
        if (worldOn) {
            world.update(dt, input); world.draw(input)
        } else if (skyOk) {
            scene.update(dt, input); scene.draw(input)
        } else {
            GLES20.glClearColor(0f, 0f, 0f, 1f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        active?.let { it.update(dt, input); it.draw(input) }
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

        // Dynamic resolution with hysteresis.
        workAvgNs = if (workAvgNs == 0f) work else workAvgNs * 0.92f + work * 0.08f
        val budget = 1e9f / targetFps
        val maxScale = if (batterySaver) 0.75f else 1f
        val cb = onScaleWanted ?: return
        if (workAvgNs > budget * 0.7f && scale > 0.6f && now - lastScaleChangeNs > 3_000_000_000L) {
            scale = max(0.6f, scale - 0.1f)
            lastScaleChangeNs = now
            cb(scale)
        } else if ((workAvgNs < budget * 0.3f && scale < maxScale && now - lastScaleChangeNs > 8_000_000_000L) || scale > maxScale) {
            scale = min(maxScale, scale + 0.1f)
            lastScaleChangeNs = now
            cb(scale)
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
