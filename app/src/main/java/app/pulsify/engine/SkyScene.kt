package app.pulsify.engine

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import app.pulsify.core.AudioFrame
import app.pulsify.core.Mood
import app.pulsify.core.SKY_VARIANTS
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** What the scene needs to know about the world each frame. */
class SceneInput {
    var variant = 0
    var character = 0        // 0 = sky only, 1 = Mochi, 2 = Kalboob, 3 = Kitty
    var coat = 0
    var world = -1           // painted environment id, -1 = the night-sky shader
    var hour = 12f           // local time 0..24 (or the Studio preview time)
    var accent = 0           // 0 = variant colours
    var brightness = 1f
    var mood = Mood.DAY
    var charging = false
    var battery = 1f
    var lowBattery = false
    var parallax = 0f        // -1..1 from home-screen pages
    val audio = AudioFrame()
}

/** Events the scene reports back (discoveries, XP). Called on the GL thread. */
interface SceneListener {
    fun onDiscovery(id: String) {}
    fun onBondXp(amount: Int) {}
}

/**
 * The Night Sky world: one full-screen fragment shader, all motion state kept on the CPU.
 * Coordinates are in "uv" space: centred, aspect-correct, the short side spans 1.
 */
class SkyScene(private val context: Context, var listener: SceneListener? = null) {

    private var program = 0
    private var vbo = 0
    private var w = 1
    private var h = 1
    private var time = 0f

    private val uni = HashMap<String, Int>()
    private val ripples = FloatArray(16)      // 4 × (x, y, age, strength)
    private var nextRipple = 0
    private val ripBuf: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    // Shooting star.
    private var shootActive = false
    private var sx = 0f; private var sy = 0f; private var svx = 0f; private var svy = 0f
    private var sAge = 0f; private var sLife = 1.2f
    private var nextShootIn = 5f
    private var showerLeft = 0
    private var showerTimer = 0f

    // Touch.
    private var touchDown = false
    private var tx = 0f; private var ty = 0f
    private var touchGlow = 0f
    private var downAt = 0f
    private var downX = 0f; private var downY = 0f
    private var moved = false
    private var lastMoveX = 0f; private var lastMoveY = 0f; private var lastMoveAt = 0f
    private var wish = 0f
    private var wishFired = false
    private val tapTimes = FloatArray(5)
    private var tapIdx = 0

    // Smoothed colours and parallax.
    private val a1 = FloatArray(3); private val a2 = FloatArray(3)
    private var colorsInit = false
    private var par = 0f
    private var grooveTime = 0f

    private val reported = HashSet<String>()
    private fun discover(id: String) {
        if (reported.add(id)) listener?.onDiscovery(id)
    }

    /** Seconds since the last touch; drives the frame-rate policy. */
    var sinceTouch = 99f; private set

    fun onSurfaceCreated() {
        val frag = context.assets.open("shaders/sky.frag").bufferedReader().use { it.readText() }
        program = Gl.program(VERT, frag)
        for (n in listOf("uRes", "uTime", "uBass", "uMid", "uTreble", "uBeat", "uA1", "uA2", "uVariant", "uPar",
            "uRip", "uShoot", "uShootDir", "uTouch", "uDim", "uBright", "uCharge", "uWish")) {
            uni[n] = GLES20.glGetUniformLocation(program, n)
        }
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        val tri = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
        val fb = ByteBuffer.allocateDirect(tri.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(tri)
        fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, tri.size * 4, fb, GLES20.GL_STATIC_DRAW)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        w = max(1, width); h = max(1, height)
    }

    // ---- Touch, in normalised screen coordinates (0..1, y down) ----

    private fun toUvX(nx: Float): Float = (nx - 0.5f) * w / min(w, h).toFloat()
    private fun toUvY(ny: Float): Float = (0.5f - ny) * h / min(w, h).toFloat()

    fun touchDown(nx: Float, ny: Float) {
        touchDown = true
        tx = toUvX(nx); ty = toUvY(ny)
        downX = tx; downY = ty; downAt = time
        lastMoveX = tx; lastMoveY = ty; lastMoveAt = time
        moved = false; wishFired = false
        sinceTouch = 0f
    }

    fun touchMove(nx: Float, ny: Float) {
        if (!touchDown) return
        val x = toUvX(nx); val y = toUvY(ny)
        val dt = max(1e-3f, time - lastMoveAt)
        // Velocity in screen widths per second.
        val vel = hypot(x - lastMoveX, y - lastMoveY) / dt / (w / min(w, h).toFloat())
        if (hypot(x - downX, y - downY) > 0.03f) moved = true
        if (vel > 5.5f && showerLeft == 0 && !shootActive) startShower()
        lastMoveX = x; lastMoveY = y; lastMoveAt = time
        tx = x; ty = y
        sinceTouch = 0f
    }

    fun touchUp() {
        if (!touchDown) return
        touchDown = false
        sinceTouch = 0f
        if (!moved && time - downAt < 0.35f) tap(tx, ty)
    }

    /** A tap from Compose (the home hero card), in normalised coordinates. */
    fun tapAt(nx: Float, ny: Float) {
        sinceTouch = 0f
        tap(toUvX(nx), toUvY(ny))
    }

    fun longPressAt(nx: Float, ny: Float) {
        sinceTouch = 0f
        tx = toUvX(nx); ty = toUvY(ny)
        makeWish()
    }

    private fun tap(x: Float, y: Float) {
        addRipple(x, y, 1f)
        discover("tap")
        listener?.onBondXp(1)
        tapTimes[tapIdx % 5] = time; tapIdx++
        // Catch a shooting star.
        if (shootActive && hypot(x - sx, y - sy) < 0.12f) {
            addRipple(sx, sy, 1.6f)
            shootActive = false
            discover("catch")
            listener?.onBondXp(5)
        }
    }

    private fun makeWish() {
        if (wishFired) return
        wishFired = true
        wish = 1f
        showerLeft = 3; showerTimer = 0.4f
        discover("wish")
    }

    private fun startShower() {
        showerLeft = 6; showerTimer = 0f
        discover("shower")
    }

    private fun addRipple(x: Float, y: Float, strength: Float) {
        val i = (nextRipple % 4) * 4
        ripples[i] = x; ripples[i + 1] = y; ripples[i + 2] = 0f; ripples[i + 3] = strength
        nextRipple++
    }

    private fun spawnShootingStar(fast: Boolean) {
        val aspect = w / min(w, h).toFloat()
        val fromLeft = Random.nextBoolean()
        sx = if (fromLeft) -aspect * 0.4f + Random.nextFloat() * 0.3f else aspect * 0.4f - Random.nextFloat() * 0.3f
        val top = 0.5f * h / min(w, h).toFloat()
        sy = top * (0.35f + Random.nextFloat() * 0.55f)
        val speed = if (fast) 1.6f else 0.9f
        val dirX = if (fromLeft) 0.86f else -0.86f
        svx = dirX * speed; svy = -0.5f * speed
        sAge = 0f; sLife = if (fast) 0.7f else 1.3f
        shootActive = true
    }

    /** Called when the phone is unlocked: a welcome shooting star. */
    fun greet() {
        if (!shootActive) spawnShootingStar(false)
    }

    // ---- Frame ----

    fun update(dt: Float, input: SceneInput) {
        time += dt
        sinceTouch += dt
        val a = input.audio

        // Ripples age out.
        for (i in 0 until 4) {
            val k = i * 4
            if (ripples[k + 3] > 0f) {
                ripples[k + 2] += dt
                if (ripples[k + 2] > 2.2f) ripples[k + 3] = 0f
            }
        }
        // Beats drop a gentle ripple somewhere low on the screen.
        if (a.beat >= 0.99f && a.source != AudioFrame.Source.NONE) {
            val aspect = w / min(w, h).toFloat()
            addRipple((Random.nextFloat() - 0.5f) * aspect * 0.8f, -0.2f - Random.nextFloat() * 0.3f, 0.35f + a.bass * 0.4f)
        }
        if (a.source == AudioFrame.Source.MUSIC) {
            grooveTime += dt
            if (grooveTime > 10f) discover("groove")
        }
        if (input.mood == Mood.NIGHT) discover("owl")

        // Shooting stars: more often at night, a shower on demand.
        if (showerLeft > 0) {
            showerTimer -= dt
            if (showerTimer <= 0f && !shootActive) {
                spawnShootingStar(true); showerLeft--; showerTimer = 0.25f
            }
        } else {
            nextShootIn -= dt * (1f + a.treble)
            if (nextShootIn <= 0f && !shootActive) {
                spawnShootingStar(false)
                nextShootIn = if (input.mood == Mood.NIGHT) 4f + Random.nextFloat() * 6f else 8f + Random.nextFloat() * 10f
            }
        }
        if (shootActive) {
            sAge += dt
            sx += svx * dt; sy += svy * dt
            if (sAge > sLife) shootActive = false
        }

        // Touch glow and wish.
        touchGlow += ((if (touchDown) 1f else 0f) - touchGlow) * min(1f, dt * 8f)
        if (touchDown && !moved && time - downAt > 0.6f) makeWish()
        wish = max(0f, wish - dt * 0.6f)

        par += (input.parallax - par) * min(1f, dt * 6f)

        // Colours ease toward the target palette.
        val v = SKY_VARIANTS[input.variant.coerceIn(0, SKY_VARIANTS.size - 1)]
        var c1 = if (input.accent != 0) input.accent else v.accent1
        var c2 = if (input.accent != 0) shiftHue(input.accent, 38f) else v.accent2
        if (input.character == 1 && input.accent == 0) { c1 = 0xFF5BF09A.toInt(); c2 = 0xFFC9A6FF.toInt() }
        if (input.character == 2 && input.accent == 0) { c1 = 0xFFFFC46B.toInt(); c2 = 0xFF9DB4FF.toInt() }
        easeColor(a1, c1, dt); easeColor(a2, c2, dt)
        colorsInit = true
    }

    fun draw(input: SceneInput) {
        GLES20.glViewport(0, 0, w, h)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)

        val a = input.audio
        val dim = when {
            input.mood == Mood.NIGHT -> 0.55f
            input.lowBattery -> 0.4f
            input.mood == Mood.MORNING -> 0.1f
            else -> 0f
        }
        GLES20.glUniform2f(u("uRes"), w.toFloat(), h.toFloat())
        GLES20.glUniform1f(u("uTime"), time % 3600f)
        GLES20.glUniform1f(u("uBass"), a.bass)
        GLES20.glUniform1f(u("uMid"), a.mid)
        GLES20.glUniform1f(u("uTreble"), a.treble)
        GLES20.glUniform1f(u("uBeat"), a.beat)
        GLES20.glUniform3f(u("uA1"), a1[0], a1[1], a1[2])
        GLES20.glUniform3f(u("uA2"), a2[0], a2[1], a2[2])
        GLES20.glUniform1f(u("uVariant"), input.variant.toFloat())
        GLES20.glUniform2f(u("uPar"), par, 0f)
        ripBuf.position(0); ripBuf.put(ripples); ripBuf.position(0)
        GLES20.glUniform4fv(u("uRip"), 4, ripBuf)
        val fade = if (shootActive) min(1f, (sLife - sAge) * 3f).coerceAtLeast(0f) * min(1f, sAge * 6f) else 0f
        GLES20.glUniform4f(u("uShoot"), sx, sy, fade, 0f)
        val sl = max(1e-4f, hypot(svx, svy))
        GLES20.glUniform2f(u("uShootDir"), svx / sl, svy / sl)
        GLES20.glUniform3f(u("uTouch"), tx, ty, touchGlow)
        GLES20.glUniform1f(u("uDim"), dim)
        GLES20.glUniform1f(u("uBright"), input.brightness)
        GLES20.glUniform2f(u("uCharge"), input.battery, if (input.charging) 1f else 0f)
        GLES20.glUniform1f(u("uWish"), wish)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
    }

    /** True while something on screen is still moving fast enough to need 60 fps. */
    val busy: Boolean
        get() = touchDown || sinceTouch < 2f || shootActive || showerLeft > 0 || wish > 0f

    fun release() {
        if (program != 0) GLES20.glDeleteProgram(program)
        if (vbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
        program = 0; vbo = 0
    }

    private fun u(name: String): Int = uni[name] ?: -1

    private fun easeColor(dst: FloatArray, argb: Int, dt: Float) {
        val r = Color.red(argb) / 255f; val g = Color.green(argb) / 255f; val b = Color.blue(argb) / 255f
        if (!colorsInit) { dst[0] = r; dst[1] = g; dst[2] = b; return }
        val k = min(1f, dt * 3f)
        dst[0] += (r - dst[0]) * k; dst[1] += (g - dst[1]) * k; dst[2] += (b - dst[2]) * k
    }

    companion object {
        private const val VERT = "attribute vec2 aPos;\nvoid main(){ gl_Position = vec4(aPos, 0.0, 1.0); }\n"

        fun shiftHue(argb: Int, degrees: Float): Int {
            val hsv = FloatArray(3)
            Color.colorToHSV(argb, hsv)
            hsv[0] = (hsv[0] + degrees + 360f) % 360f
            hsv[1] = min(1f, hsv[1] * 1.05f)
            return Color.HSVToColor(hsv)
        }
    }
}
