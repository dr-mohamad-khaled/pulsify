package app.pulsify.engine

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Painted environments. The detailed world is rendered into a texture only when it changes
 * (another world, a new size, or every few minutes as the real clock moves). Each frame then
 * costs one texture read plus a light overlay: twinkles, particles, music glow, touch bursts.
 */
class WorldScene(private val context: Context) {

    private var worldProg = 0
    private var overlayProg = 0
    private var vbo = 0
    private var fbo = 0
    private var tex = 0
    private var fboOk = false
    private var w = 1; private var h = 1
    private var tw = 1; private var th = 1

    private val wu = HashMap<String, Int>()
    private val ou = HashMap<String, Int>()

    private var cachedWorld = -1
    private var cachedHourBucket = -1
    private var dirty = true
    private var time = 0f
    private var par = 0f

    private val bursts = FloatArray(12)
    private var nextBurst = 0
    private val burstBuf: FloatBuffer = ByteBuffer.allocateDirect(12 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    // Image worlds: a day and a night picture per world, plus where its ground is.
    private var imgProg = 0
    private val iu = HashMap<String, Int>()
    private var dayTex = 0; private var nightTex = 0
    private var imgW = 1; private var imgH = 1
    private var loadedWorld = -2
    private var imageMode = false
    private var hasNightImg = false
    private var groundFrac = 0.75f
    /** The ground line in uv for the current image world (NaN when the world has no picture). */
    var groundY = Float.NaN; private set

    var sinceTouch = 99f; private set
    val busy: Boolean get() = sinceTouch < 1.5f

    /** Floor line in uv, shared with the creatures so feet sit on the ground. */
    private fun floorUv(): Float = -0.5f * h / min(w, h) + 0.3f

    fun onSurfaceCreated() {
        val vert = "attribute vec2 aPos;\nvoid main(){ gl_Position = vec4(aPos, 0.0, 1.0); }\n"
        // world.frag (the old procedural paintings) is big: compiled only if a world has no pictures.
        overlayProg = Gl.program(vert, context.assets.open("shaders/overlay.frag").bufferedReader().use { it.readText() })
        try {
            imgProg = Gl.program(vert, context.assets.open("shaders/imgworld.frag").bufferedReader().use { it.readText() })
            for (n in listOf("uDay", "uNight", "uRes", "uMap", "uNightK", "uDusk", "uHasNight")) iu[n] = GLES20.glGetUniformLocation(imgProg, n)
        } catch (t: Throwable) { imgProg = 0; Log.w("Pulsify", "image worlds off", t) }
        for (n in listOf("uTex", "uRes", "uHalf", "uFloor", "uTime", "uWorld", "uNight", "uBass", "uTreble", "uBeat", "uPar", "uBurst", "uBright", "uDim")) {
            ou[n] = GLES20.glGetUniformLocation(overlayProg, n)
        }
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0); vbo = ids[0]
        val tri = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
        val fb = ByteBuffer.allocateDirect(tri.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(tri)
        fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, tri.size * 4, fb, GLES20.GL_STATIC_DRAW)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        if (width == w && height == h && fbo != 0) return
        w = max(1, width); h = max(1, height)
        createTarget()
        updateGround()
        dirty = true
    }

    private fun createTarget() {
        deleteTarget()
        // Pictures keep full detail; the procedural paintings are soft, so half resolution is enough.
        val scale = if (imageMode) 1f else if (max(w, h) > 1600) 0.5f else 0.65f
        tw = max(1, (w * scale).roundToInt()); th = max(1, (h * scale).roundToInt())
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0); tex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, tw, th, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glGenFramebuffers(1, ids, 0); fbo = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex, 0)
        fboOk = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
        if (!fboOk) Log.w("Pulsify", "World cache unavailable; drawing worlds directly")
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target)
    }

    private fun deleteTarget() {
        if (fbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
        fbo = 0; tex = 0; fboOk = false
    }

    fun touch(nx: Float, ny: Float) {
        val m = min(w, h).toFloat()
        val i = (nextBurst % 3) * 4
        bursts[i] = (nx - 0.5f) * w / m; bursts[i + 1] = (0.5f - ny) * h / m; bursts[i + 2] = 0f; bursts[i + 3] = 1f
        nextBurst++
        sinceTouch = 0f
    }

    fun update(dt: Float, input: SceneInput) {
        time += dt; sinceTouch += dt
        for (b in 0 until 3) {
            val k = b * 4
            if (bursts[k + 3] > 0f) { bursts[k + 2] += dt; if (bursts[k + 2] > 1.6f) bursts[k + 3] = 0f }
        }
        par += (input.parallax - par) * min(1f, dt * 6f)
        // Re-render the cached painting when the world changes or the clock moves on 5 minutes.
        val bucket = (input.hour * 12f).toInt()
        if (input.world != cachedWorld || bucket != cachedHourBucket) dirty = true
        if (input.world != loadedWorld) loadImages(input.world)
    }

    /** Loads the pictures for a world (on the GL thread, only when the world changes). */
    private fun loadImages(world: Int) {
        loadedWorld = world
        freeImages()
        val spec = WORLD_IMAGES[world]
        val wasImage = imageMode
        imageMode = false
        groundY = Float.NaN
        if (spec != null && imgProg != 0) {
            try {
                // Only the size now; the pictures are decoded when the painting is drawn, and only
                // the ones the time of day needs (one at noon or midnight, both at dusk).
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open("worlds/w${world}_day.jpg").use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
                if (o.outWidth > 0) {
                    imgW = o.outWidth; imgH = o.outHeight
                    hasNightImg = try { context.assets.open("worlds/w${world}_night.jpg").close(); true } catch (_: java.io.IOException) { false }
                    groundFrac = spec
                    imageMode = true
                }
            } catch (t: Throwable) {
                Log.w("Pulsify", "world $world pictures failed", t); freeImages(); imageMode = false
            }
        }
        if (imageMode != wasImage) createTarget()
        updateGround()
        dirty = true
    }

    private fun decode(path: String): android.graphics.Bitmap? = try {
        context.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }) }
    } catch (_: java.io.IOException) { null }

    private fun upload(b: android.graphics.Bitmap): Int {
        val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
        return ids[0]
    }

    private fun freeImages() {
        if (dayTex != 0) GLES20.glDeleteTextures(1, intArrayOf(dayTex), 0)
        if (nightTex != 0) GLES20.glDeleteTextures(1, intArrayOf(nightTex), 0)
        dayTex = 0; nightTex = 0
    }

    // Cover mapping: the picture fills the screen without stretching. Tall screens crop the sides;
    // wide screens crop mostly from the top so the ground (and the creature on it) stays in view.
    private val map = FloatArray(4)
    private fun computeMap() {
        val ia = imgW.toFloat() / imgH; val sa = w.toFloat() / h
        if (sa < ia) { val k = sa / ia; map[0] = k; map[1] = 1f; map[2] = (1f - k) * 0.5f; map[3] = 0f }
        else { val k = ia / sa; map[0] = 1f; map[1] = k; map[2] = 0f; map[3] = (1f - k) * 0.25f }
    }

    private fun updateGround() {
        if (!imageMode) { groundY = Float.NaN; return }
        computeMap()
        val vImg = 1f - groundFrac
        val vScreen = (vImg - map[3]) / map[1]
        val halfH = 0.5f * h / min(w, h)
        groundY = (vScreen - 0.5f) * 2f * halfH
    }

    private fun bindTri(prog: Int) {
        GLES20.glUseProgram(prog)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
    }

    private fun drawWorld(input: SceneInput, rw: Int, rh: Int) {
        if (imageMode) {
            val nk = nightAmount(input.hour)
            val needDay = nk < 0.999f || !hasNightImg
            val needNight = nk > 0.001f && hasNightImg
            if (needDay && dayTex == 0) decode("worlds/w${loadedWorld}_day.jpg")?.let { dayTex = upload(it); it.recycle() }
            if (!needDay && dayTex != 0) { GLES20.glDeleteTextures(1, intArrayOf(dayTex), 0); dayTex = 0 }
            if (needNight && nightTex == 0) decode("worlds/w${loadedWorld}_night.jpg")?.let { nightTex = upload(it); it.recycle() }
            if (!needNight && nightTex != 0) { GLES20.glDeleteTextures(1, intArrayOf(nightTex), 0); nightTex = 0 }
            val dTex = if (dayTex != 0) dayTex else nightTex
            val nTex = if (nightTex != 0) nightTex else dayTex
            if (dTex == 0) { GLES20.glClearColor(0f, 0f, 0f, 1f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT); return }
            bindTri(imgProg)
            computeMap()
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, dTex)
            GLES20.glUniform1i(iu["uDay"]!!, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, nTex)
            GLES20.glUniform1i(iu["uNight"]!!, 1)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glUniform2f(iu["uRes"]!!, rw.toFloat(), rh.toFloat())
            GLES20.glUniform4f(iu["uMap"]!!, map[0], map[1], map[2], map[3])
            GLES20.glUniform1f(iu["uNightK"]!!, if (hasNightImg) nk else nk)
            GLES20.glUniform1f(iu["uDusk"]!!, duskAmount(input.hour))
            GLES20.glUniform1f(iu["uHasNight"]!!, if (hasNightImg) 1f else 0f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
            return
        }
        if (worldProg == 0) {
            val vert = "attribute vec2 aPos;\nvoid main(){ gl_Position = vec4(aPos, 0.0, 1.0); }\n"
            worldProg = Gl.program(vert, context.assets.open("shaders/world.frag").bufferedReader().use { it.readText() })
            for (n in listOf("uRes", "uHalf", "uFloor", "uWorld", "uHour", "uSeed")) wu[n] = GLES20.glGetUniformLocation(worldProg, n)
        }
        bindTri(worldProg)
        val m = min(w, h).toFloat()
        GLES20.glUniform2f(wu["uRes"]!!, rw.toFloat(), rh.toFloat())
        GLES20.glUniform2f(wu["uHalf"]!!, 0.5f * w / m, 0.5f * h / m)
        GLES20.glUniform1f(wu["uFloor"]!!, floorUv())
        GLES20.glUniform1f(wu["uWorld"]!!, input.world.toFloat())
        GLES20.glUniform1f(wu["uHour"]!!, input.hour)
        GLES20.glUniform1f(wu["uSeed"]!!, 3f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
    }

    fun draw(input: SceneInput) {
        GLES20.glDisable(GLES20.GL_BLEND)
        if (!fboOk) {
            // Fallback: paint directly (no overlay effects).
            GLES20.glViewport(0, 0, w, h)
            drawWorld(input, w, h)
            return
        }
        if (dirty) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            GLES20.glViewport(0, 0, tw, th)
            drawWorld(input, tw, th)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target)
            cachedWorld = input.world
            cachedHourBucket = (input.hour * 12f).toInt()
            dirty = false
        }
        GLES20.glViewport(0, 0, w, h)
        bindTri(overlayProg)
        val m = min(w, h).toFloat()
        val a = input.audio
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(ou["uTex"]!!, 0)
        GLES20.glUniform2f(ou["uRes"]!!, w.toFloat(), h.toFloat())
        GLES20.glUniform2f(ou["uHalf"]!!, 0.5f * w / m, 0.5f * h / m)
        GLES20.glUniform1f(ou["uFloor"]!!, floorUv())
        GLES20.glUniform1f(ou["uTime"]!!, time % 3600f)
        GLES20.glUniform1f(ou["uWorld"]!!, input.world.toFloat())
        GLES20.glUniform1f(ou["uNight"]!!, nightAmount(input.hour))
        GLES20.glUniform1f(ou["uBass"]!!, a.bass)
        GLES20.glUniform1f(ou["uTreble"]!!, a.treble)
        GLES20.glUniform1f(ou["uBeat"]!!, a.beat)
        GLES20.glUniform1f(ou["uPar"]!!, par)
        burstBuf.position(0); burstBuf.put(bursts); burstBuf.position(0)
        GLES20.glUniform4fv(ou["uBurst"]!!, 3, burstBuf)
        GLES20.glUniform1f(ou["uBright"]!!, input.brightness)
        GLES20.glUniform1f(ou["uDim"]!!, if (input.lowBattery) 0.4f else 0f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
    }

    fun release() {
        deleteTarget()
        freeImages(); loadedWorld = -2; imageMode = false
        if (imgProg != 0) GLES20.glDeleteProgram(imgProg)
        imgProg = 0
        if (worldProg != 0) GLES20.glDeleteProgram(worldProg)
        if (overlayProg != 0) GLES20.glDeleteProgram(overlayProg)
        if (vbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
        worldProg = 0; overlayProg = 0; vbo = 0
        cachedWorld = -1
    }

    companion object {
        fun nightAmount(hour: Float): Float {
            val day = smooth(5.5f, 8f, hour) * (1f - smooth(17.5f, 20f, hour))
            return 1f - day
        }
        /** Warm light around sunrise and sunset. */
        fun duskAmount(hour: Float): Float {
            val dawn = smooth(5f, 6.5f, hour) * (1f - smooth(7.5f, 9f, hour))
            val dusk = smooth(16.5f, 18f, hour) * (1f - smooth(19f, 20.5f, hour))
            return maxOf(dawn, dusk)
        }

        /**
         * Worlds that have pictures, and where their ground is: the fraction of the picture's
         * height, from the top, where a creature's feet touch the ground.
         */
        /** World id -> where the creature's feet touch the picture (fraction from the top). */
        val WORLD_IMAGES: Map<Int, Float> = mapOf(1 to 0.8f, 2 to 0.82f, 3 to 0.8f, 4 to 0.8f, 5 to 0.79f, 6 to 0.83f, 7 to 0.79f, 8 to 0.83f, 9 to 0.86f, 10 to 0.84f, 11 to 0.84f, 12 to 0.86f, 13 to 0.86f, 14 to 0.86f, 15 to 0.85f, 16 to 0.81f, 17 to 0.83f,
            18 to 0.82f, 19 to 0.83f, 20 to 0.82f, 21 to 0.83f, 22 to 0.84f, 23 to 0.84f, 24 to 0.83f, 25 to 0.74f, 26 to 0.78f, 27 to 0.82f)

        private fun smooth(a: Float, b: Float, x: Float): Float {
            val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
            return t * t * (3 - 2 * t)
        }
    }
}
