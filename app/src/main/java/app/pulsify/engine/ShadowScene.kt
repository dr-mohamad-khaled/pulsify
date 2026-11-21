package app.pulsify.engine

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

const val SHADOW_ID = 13

/** One "Shadows" face: just eyes and a grin on pitch black. */
class ShadowLook(
    val key: String, val name: String,
    val eyeCols: IntArray, val line: Int, val pupil: String, val lid: Float,
    val teeth: String, val tc: Int, val tc2: Int, val inside: Int, val glow: Int,
    val extra: String, val head: String? = null, val rim: Int = 0,
    val veins: Boolean = false, val cracks: Boolean = false, val kohl: Boolean = false,
)

private fun c(h: Long) = h.toInt()
val SHADOW_LOOKS = listOf(
    ShadowLook("neon", "Neon Grin", intArrayOf(c(0xFFFFD6F7), c(0xFFFF3FD2), c(0xFF8A0068)), c(0xFFFF2E7E), "hot", .15f, "block", c(0xFF9FF8FF), c(0xFF00D9FF), c(0xFF021626), c(0xFF00E5FF), "scribble"),
    ShadowLook("inferno", "Inferno", intArrayOf(c(0xFFFFF6C2), c(0xFFFFB02E), c(0xFFC83A00)), c(0xFFFF8A1F), "round", .3f, "jag", c(0xFFEDE6D8), c(0xFF9C9286), c(0xFF080403), c(0xFFFF6A00), "flames"),
    ShadowLook("crimson", "Crimson", intArrayOf(c(0xFFFFC7BD), c(0xFFFF2A1F), c(0xFF4A0000)), c(0xFFFF4433), "slit", .22f, "fangs", c(0xFFFF6A55), c(0xFFB3140A), c(0xFF2A0000), c(0xFFFF2A1F), "embers", "demon", c(0xFFFF2A1F), veins = true),
    ShadowLook("fiend", "Fiend", intArrayOf(c(0xFF3A0000), c(0xFFE00000), c(0xFF5C0000)), c(0xFFFF1A1A), "rings", .05f, "jag", c(0xFFFFFFFF), c(0xFFD9D9D9), c(0xFF000000), c(0xFFFF1A1A), "brows"),
    ShadowLook("glitch", "Glitch", intArrayOf(c(0xFFE0FFFF), c(0xFF00F0FF), c(0xFF003B57)), c(0xFF00F0FF), "pixel", .2f, "pixel", c(0xFFFFFFFF), c(0xFFFF2E97), c(0xFF00060A), c(0xFF00F0FF), "glitch"),
    ShadowLook("frost", "Frost Wraith", intArrayOf(c(0xFFFFFFFF), c(0xFF9BE7FF), c(0xFF1C5D8A)), c(0xFFBFF2FF), "slit", .3f, "icicle", c(0xFFE6FBFF), c(0xFF7FD8FF), c(0xFF010A14), c(0xFF7FD8FF), "frost", cracks = true),
    ShadowLook("toxic", "Toxic", intArrayOf(c(0xFFF4FFD6), c(0xFF9DFF2E), c(0xFF1E5A00)), c(0xFF7CFF2E), "slit", .25f, "drip", c(0xFFE9FFD0), c(0xFF7CFF2E), c(0xFF041200), c(0xFF7CFF2E), "drool"),
    ShadowLook("djinn", "Golden Djinn", intArrayOf(c(0xFFFFF5D1), c(0xFFFFC94A), c(0xFF7A4A00)), c(0xFFFFD873), "round", .28f, "gold", c(0xFFFFE08A), c(0xFFB07A12), c(0xFF0A0600), c(0xFFFFB000), "smoke", kohl = true),
)

/**
 * Pulsify Shadows: an evil grin in the dark, always in the middle of the screen. Only eyes and teeth
 * light up; everything else stays true AMOLED black. Port of the 2D browser preview.
 * Drawn with Android Canvas into a bitmap around the face, uploaded as a GL texture each frame.
 */
class ShadowScene : Creature {
    override var listener: SceneListener? = null
    override var bottomInset = 0f
    override val busy: Boolean get() = act != null && act!!.n != "lurk"
    var look: ShadowLook = SHADOW_LOOKS[0]

    // ---------------------------------------------------------------- springs + state
    private class Sp(var x: Float) { var v = 0f
        fun step(t: Float, f: Float, z: Float, dt: Float) { val w = TAU * f; repeat(3) { v += (w * w * (t - x) - 2 * z * w * v) * dt / 3; x += v * dt / 3 } } }
    private val sc = Sp(1f); private val lx = Sp(0f); private val ly = Sp(0f); private val openL = Sp(1f); private val openR = Sp(1f)
    private val squint = Sp(0f); private val wide = Sp(1f); private val pupilS = Sp(1f); private val angry = Sp(0f)
    private val mw = Sp(1f); private val mopen = Sp(.15f); private val grin = Sp(1f); private val smirk = Sp(0f); private val fade = Sp(1f); private val tilt = Sp(0f)
    private var shake = 0f; private var chatter = 0f; private var rage = 0f; private val pokes = ArrayList<Float>()
    private var blinkT = 0f; private var nextBlink = 3f; private var lurk = false
    private var charging = false; private var battery = .5f; private var bass = 0f; private var pump = 0f; private var hat = 0f
    private var music = false; private var sndStart = -9f; private var sndLast = -9f; private var reacted = false
    private var time = 0f; private var lastTouch = 0f; private var split = 0f; private var lastLook: ShadowLook? = null

    private class Act(val n: String, val d: Float) { var t = 0f; var side = 0f; var gx = 0f; var gy = 0f; var hold = false; var s = 0f }
    private var act: Act? = null
    private fun doAct(n: String): Act {
        val d = mapOf("laugh" to 1.6f, "chomp" to .7f, "stare" to 99f, "vanish" to 1.1f, "rage" to 2.4f, "lurk" to 99f, "poke" to 1f, "startle" to 1.3f, "look" to 2.6f,
            "smirk" to 2.2f, "roll" to 1.6f, "notify" to 3.4f, "feed" to 1.6f, "jumpscare" to 1.2f, "chatter" to 1.2f, "gaze" to 2.2f)[n] ?: 1.5f
        val a = Act(n, d); act = a
        if (n == "laugh") repeat(5) { i -> emit(HE, (rnd() - .5f) * .6f, .1f - i * .05f, 1) }
        if (n == "rage") rage = 1f
        if (n == "jumpscare") { shake = 1.2f; split = 1f }
        return a
    }

    // ---------------------------------------------------------------- particles (face units, y down)
    private class Pt(val k: Int, var x: Float, var y: Float, var vx: Float, var vy: Float, val max: Float, var s: Float, val r: Float, val col: Int) { var life = 0f; var hang = 0f; var len = 0f; var tx = 0f; var ty = 0f }
    private val parts = ArrayList<Pt>()
    private fun emit(k: Int, x: Float, y: Float, n: Int, vx: Float = Float.NaN, vy: Float = Float.NaN, max: Float = Float.NaN, s: Float = Float.NaN, col: Int = 0) {
        repeat(n) { parts += Pt(k, x + (rnd() - .5f) * .05f, y + (rnd() - .5f) * .05f, if (vx.isNaN()) (rnd() - .5f) * .5f else vx, if (vy.isNaN()) -(.15f + rnd() * .4f) else vy,
            if (max.isNaN()) .8f + rnd() * .9f else max, if (s.isNaN()) .02f + rnd() * .03f else s, rnd() * TAU, col) }
    }
    private val scrib = List(14) { i -> val side = if (i % 2 == 1) 1f else -1f; var x = side * (.82f + rnd() * .25f); var y = -.4f + rnd() * .9f
        val pts = ArrayList<FloatArray>(); repeat(6) { pts += floatArrayOf(x, y); x += side * rnd() * .08f - side * .02f; y += (rnd() - .4f) * .12f }
        Triple(pts, intArrayOf(c(0xFFFF2E97), c(0xFF00E5FF), c(0xFFB026FF), c(0xFFFF6AD5), c(0xFF2DD4BF))[Random.nextInt(5)], rnd() * TAU) }

    // ---------------------------------------------------------------- layout
    private var w = 1; private var h = 1; private var halfW = .5f; private var halfH = 1f
    private val sUv get() = .46f   // face unit in uv (short side = 1)
    private fun toFace(nx: Float, ny: Float) = floatArrayOf((nx - .5f) * 2 * halfW / sUv, (ny - .5f) * 2 * halfH / sUv)

    // ---------------------------------------------------------------- input
    private var fDown = false; private var fx = 0f; private var fy = 0f; private var fx0 = 0f; private var fy0 = 0f; private var fT0 = 0f; private var fMoved = 0f; private var fHit = "none"; private var fUsed = false
    private var fvx = 0f; private var fvy = 0f; private var fLt = 0f
    private fun hit(x: Float, y: Float): String {
        if (fade.x < .3f) return "none"; val lx0 = x / sc.x; val ly0 = y / sc.x
        for (s in floatArrayOf(-1f, 1f)) { val g = eyeGeom(s, 1f, 0f, wide.x, 0f, .2f); if (hypot(lx0 - g.mx, (ly0 - g.my) * 1.6f) < g.w * .62f) return if (s < 0) "eyeL" else "eyeR" }
        val m = mouth(); if (abs(lx0) < m.mw && ly0 > m.top(lx0) - .07f && ly0 < m.bot(lx0) + .07f) return "mouth"
        if (hypot(lx0, ly0 * .9f) < .85f) return "face"
        return "none"
    }
    override fun touchDown(nx: Float, ny: Float): Boolean {
        val p = toFace(nx, ny); fDown = true; fx = p[0]; fy = p[1]; fx0 = fx; fy0 = fy; fT0 = time; fMoved = 0f; fHit = hit(fx, fy); fUsed = false; fvx = 0f; fvy = 0f; fLt = time
        val was = lurk; lastTouch = time
        if (was) { lurk = false; if (rnd() < .7f) { doAct("jumpscare"); fUsed = true } else act = null }
        return true
    }
    override fun touchMove(nx: Float, ny: Float): Boolean {
        if (!fDown) return false; val p = toFace(nx, ny); val dt = max(.001f, time - fLt)
        fvx = fvx * .6f + (p[0] - fx) / dt * .4f; fvy = fvy * .6f + (p[1] - fy) / dt * .4f; fLt = time
        fx = p[0]; fy = p[1]; fMoved = max(fMoved, hypot(fx - fx0, fy - fy0)); lastTouch = time; return true
    }
    override fun touchUp(): Boolean {
        if (!fDown) return false; fDown = false
        if (fUsed) return true
        if (act?.n == "stare") { doAct("laugh"); return true }
        val quick = time - fT0 < .35f && fMoved < .04f
        if (fMoved > .04f && fHit == "none" && hypot(fvx, fvy) > 4f) { doAct("startle"); return true }
        if (fMoved > .04f && fHit != "none") { grin.v += 6f; sc.v += 2f; return true }
        if (!quick) return true
        when (fHit) {
            "eyeL", "eyeR" -> { pokes.removeAll { time - it > 4f }; pokes += time; if (pokes.size >= 3) { pokes.clear(); doAct("rage") } else doAct("poke").side = if (fHit == "eyeL") -1f else 1f }
            "mouth" -> doAct("chomp")
            "face" -> doAct(if (rnd() < .7f) "laugh" else "smirk")
            else -> { val a = doAct("gaze"); a.gx = fx; a.gy = fy }
        }
        return true
    }
    override fun tapAt(nx: Float, ny: Float): Boolean = true
    override fun longPressAt(nx: Float, ny: Float): Boolean = true
    override fun greet() { if (act == null) doAct("smirk") }
    override fun swiped(dir: Float, fast: Boolean) { if (act == null || act!!.n == "look") { val a = doAct("gaze"); a.gx = dir * 2f; a.gy = 0f } }
    override fun perform(trick: String): Boolean {
        lastTouch = time; lurk = false
        when (trick) {
            "laugh", "chomp", "rage", "jumpscare", "roll", "notify" -> doAct(trick)
            "stare" -> { val a = doAct("stare"); a.hold = true }
            "vanish" -> doAct("vanish")
            "lurk" -> { doAct("lurk"); lurk = true; lastTouch = -999f }
            else -> return false
        }
        return true
    }

    // ---------------------------------------------------------------- update
    override fun update(dt0: Float, input: SceneInput) {
        val dt = min(dt0, .05f); time += dt
        if (lastLook !== look) { lastLook = look; parts.clear(); act = null; lurk = false; fade.x = 0f; doAct("vanish").t = .45f }
        val a0 = input.audio; val lvl = if (a0.active) a0.level else 0f
        bass = max(if (a0.active) a0.bass else 0f, bass * exp(-dt * 7)); hat = max(if (a0.active) a0.treble else 0f, hat * exp(-dt * 12)); pump = if (a0.active) a0.beat else pump * exp(-dt * 6)
        if (lvl > .02f) { if (time - sndLast > .8f) { sndStart = time; reacted = false }; sndLast = time }
        val sndDur = if (time - sndLast < .8f) time - sndStart else 0f
        if (sndDur > 0 && !reacted) { reacted = true; if (act == null || act!!.n == "look") doAct("startle").gy = -1f }
        music = sndDur > 4f
        if (input.charging && !charging) doAct("feed"); charging = input.charging; battery = input.battery.coerceIn(0f, 1f)
        var open = 1f; var sq = 0f; var wd = 1f; var pu = 1f; var an = 0f; var mW = 1f; var mo = .12f + .04f * sin(time * 1.3f); var gr = 1f; var sk = .25f * sin(time * .37f); var fd = 1f; var scT = 1f; var tl = 0f
        var lX = .4f * sin(time * .41f) * sin(time * .17f); var lY = .15f * sin(time * .3f)
        if (fDown) { lX = clamp(fx * 1.3f, -1f, 1f); lY = clamp((fy + .1f) * 1.6f, -1f, 1f)
            if (hypot(fx, fy - .35f) < .4f) { mo = .3f; gr = 1.15f }
            if (fHit == "none" && time - fT0 > .6f && fMoved < .04f && act?.n != "stare" && !fUsed) doAct("stare") }
        val a = act
        if (a == null && !fDown) {
            if (time - lastTouch > 10f && !music && !charging) { doAct("lurk"); lurk = true }
            else if (rnd() < dt * .25f) doAct(listOf("look", "smirk", "roll", "chatter", if (rnd() < .3f) "laugh" else "look")[Random.nextInt(5)])
        }
        if (a != null) {
            a.t += dt; val t = a.t
            when (a.n) {
                "laugh" -> { sq = .8f * win(t, 0f, .15f, 1.3f, 1.6f); mo = .25f + .55f * abs(sin(t * 17)) * win(t, 0f, .1f, 1.2f, 1.5f); gr = 1.25f; mW = 1.08f; chatter = sin(t * 34) * .5f; tl = .05f * sin(t * 9) * (1 - t / 1.6f); scT = 1 + .03f * abs(sin(t * 17)) }
                "chomp" -> { val op = if (t < .25f) sm(t / .2f) else 1 - sm((t - .25f) / .06f); mo = .12f + 1.1f * op; mW = 1 + .1f * op; wd = 1 + .15f * op; pu = 1 - .4f * op; scT = 1 + .08f * op
                    if (t > .26f && a.s == 0f) { a.s = 1f; shake = .8f; emit(SPARK, 0f, .38f, 10) } }
                "poke" -> { val k = win(t, 0f, .05f, .5f, .9f); if (a.side < 0) openL.x = min(openL.x, 1 - k) else openR.x = min(openR.x, 1 - k); an = .8f * k; mo = .05f; gr = .6f + .4f * (1 - k); chatter = sin(t * 50) * k * .6f; tl = a.side * -.08f * k; shake = max(shake, .25f * k) }
                "rage" -> { val k = win(t, 0f, .15f, 1.9f, 2.4f); an = 1.3f * k; wd = 1 + .25f * k; pu = 1 - .55f * k; mo = .2f + k * (.7f + .3f * sin(t * 25)); mW = 1 + .2f * k; gr = .7f; scT = 1 + .2f * k; shake = max(shake, .9f * k); split = max(split, .6f * k); rage = k
                    if (rnd() < k * .5f) emit(SPARK, rnd() - .5f, .3f, 1, vy = -(.5f + rnd())) }
                "stare" -> { val k = sm(t / 2.5f); wd = 1 + .3f * k; pu = 1 - .65f * k; mW = 1 + .35f * k; gr = 1 + .5f * k; mo = .1f + .08f * k; scT = 1 + .3f * k; lX *= 1 - k; lY *= 1 - k
                    if (!fDown && !a.hold) act = null; if (a.hold && t > 3f) doAct("laugh") }
                "gaze" -> { lX = clamp(a.gx * 1.6f, -1f, 1f); lY = clamp((a.gy + .1f) * 1.8f, -1f, 1f); val k = win(t, 0f, .08f, 1.6f, 2.2f); wd = 1 + .12f * win(t, 0f, .06f, .3f, .7f); pu = 1 - .3f * win(t, 0f, .06f, .3f, .7f); sk = .5f * sign(lX) * k; sq = .25f * win(t, .7f, 1f, 1.6f, 2.2f) }
                "vanish" -> { fd = if (t < .45f) 1 - sm(t / .4f) else sm((t - .55f) / .4f); if (t < .45f && rnd() < .5f) emit(PUFF, (rnd() - .5f) * 1.2f, (rnd() - .5f) * .8f, 2, max = 1f, s = .08f); if (t > .5f) { sq = .5f * win(t, .6f, .8f, 1f, 1.1f); gr = 1.2f } }
                "lurk" -> { fd = .1f; open = .1f + .1f * max(0f, sin(t * .6f)).pow(30); mo = .05f; gr = .7f; lurk = true; if (floor(t).toInt() % 9 == 6) { open = sm((t % 1) * 3) * .8f; lX = sin(t * 2) } }
                "startle" -> { val k = win(t, 0f, .05f, .7f, 1.2f); wd = 1 + .3f * k; pu = 1 - .6f * k; mo = .12f + .6f * k; gr = 1 - .6f * k; mW = 1 - .35f * k; scT = 1 - .08f * k; if (a.gy < 0) { lY = -1f; lX = .1f } }
                "look" -> { lX = sin(t * 2.4f) * .9f; lY = .2f * cos(t * 1.7f); sk = .5f * sin(t * 2.4f) }
                "smirk" -> { if (a.s == 0f) a.s = if (rnd() < .5f) -1f else 1f; sk = .9f * win(t, 0f, .3f, 1.8f, 2.2f) * a.s; sq = .35f * win(t, 0f, .3f, 1.8f, 2.2f); tl = .06f * sk }
                "roll" -> { lX = cos(t / 1.6f * TAU) * .9f; lY = sin(t / 1.6f * TAU) * .9f - .2f }
                "chatter" -> { chatter = sin(t * 45) * win(t, 0f, .1f, 1f, 1.2f); mo = .2f }
                "notify" -> { val k = win(t, 0f, .1f, 2.9f, 3.4f); lY = -1 * k; lX = .15f; wd = 1 + .15f * win(t, 0f, .1f, .6f, 1f); mo = if (t < .8f) .4f else .15f + .35f * abs(sin(t * 12)) * win(t, 1f, 1.1f, 2.6f, 2.8f); gr = if (t < .8f) .7f else 1.2f }
                "feed" -> { mo = .5f + .3f * sin(t * 10); sq = .6f; gr = 1.3f }
                "jumpscare" -> { val k = win(t, 0f, .03f, .5f, 1.2f); wd = 1 + .5f * k; pu = 1 - .7f * k; mW = 1 + .5f * k; mo = .3f + k; scT = 1 + .55f * k; an = .8f * k }
            }
            if (act === a && a.t > a.d) act = null
        }
        val an2 = act?.n
        if (charging && (an2 == null || an2 == "look" || an2 == "smirk")) { mo = max(mo, .45f + .05f * sin(time * 4)); gr = 1.1f; sq = .25f }
        if (!charging && battery < .15f && (an2 == null || an2 == "look")) { open = .45f; mo = .06f; gr = .7f }
        if (music && (an2 == null || an2 in setOf("look", "smirk", "roll", "chatter"))) { scT = 1 + .07f * pump; mo = .2f + .5f * pump; gr = 1.15f + .2f * pump; tl = .07f * sin(time * 2f * PI.toFloat() * .93f); chatter = hat * sin(time * 60) * .8f; sq = .35f * pump; lY += .2f * pump }
        nextBlink -= dt; if (nextBlink < 0) { blinkT = 1f; nextBlink = 2.5f + rnd() * 4; if (rnd() < .2f) nextBlink = .3f }; blinkT = max(0f, blinkT - dt * 6.5f)
        val bl = sin(blinkT * PI.toFloat())
        chatter *= exp(-dt * 6); rage = max(0f, rage - dt * .4f); shake = max(0f, shake - dt * 2.2f); split = max(0f, split - dt * 2.2f)
        sc.step(scT * (1 + .015f * sin(time * 1.6f)), 3f, .4f, dt); lx.step(lX, 3.5f, .7f, dt); ly.step(lY, 3.5f, .7f, dt)
        val ap = act; val poking = ap?.n == "poke"
        if (!(poking && ap!!.side < 0)) openL.step(open * (1 - bl), 7f, .7f, dt); if (!(poking && ap!!.side > 0)) openR.step(open * (1 - bl), 7f, .7f, dt)
        if (poking) (if (ap!!.side < 0) openR else openL).step(1.15f, 7f, .7f, dt)
        squint.step(sq, 5f, .7f, dt); wide.step(wd, 4f, .5f, dt); pupilS.step(pu, 5f, .6f, dt); angry.step(an, 4f, .7f, dt)
        mw.step(mW, 4f, .45f, dt); mopen.step(mo + if (music) .25f * bass else 0f, 7f, .5f, dt); grin.step(gr, 3.5f, .4f, dt); smirk.step(sk, 3f, .6f, dt); fade.step(fd, 2.5f, .9f, dt); tilt.step(tl, 3f, .5f, dt)
        updParts(dt)
    }

    private fun updParts(dt: Float) {
        val L = look
        if (fade.x > .2f) {
            when (L.extra) {
                "flames" -> for (s in floatArrayOf(-1f, 1f)) { val g = eyeGeom(s, openR.x, 0f, wide.x, angry.x, .3f); repeat(3) { val k = .15f + rnd() * .85f
                    emit(FLAME, mix(g.Ix, g.Ox, k), mix(g.Iy, g.Oy, k) - .02f, 1, vx = s * (.1f + rnd() * .25f) * k, vy = -(.35f + rnd() * .5f) * (1 + rage), max = .35f + rnd() * .45f * k, s = .025f + rnd() * .035f * (1.3f - k * .5f)) } }
                "embers" -> if (rnd() < dt * 10) emit(EMBER, (rnd() - .5f) * 1.8f, .5f + rnd() * .3f, 1, max = 2 + rnd() * 2, vy = -(.08f + rnd() * .15f), col = c(0xFFFF2A1F))
                "frost" -> { if (rnd() < dt * 4) emit(SNOW, (rnd() - .5f) * 2.4f, -1.1f, 1, vy = .06f + rnd() * .1f, vx = 0f, max = 12f, s = .006f + rnd() * .01f)
                    if (rnd() < dt * 5 * (.4f + mopen.x)) emit(MIST, (rnd() - .5f) * .5f, .38f, 1, vy = .05f + rnd() * .08f, vx = (rnd() - .5f) * .2f, max = 1.6f, s = .06f + rnd() * .06f) }
                "drool" -> if (rnd() < dt * 1.6f) { val m = mouth(); val x = (rnd() - .5f) * m.mw * 1.4f; val p = Pt(DRIP, x, m.bot(x) + .01f, 0f, 0f, 4f, .012f + rnd() * .01f, 0f, 0); p.hang = .3f + rnd() * .6f; parts += p }
                "smoke" -> { if (rnd() < dt * 30) { val t = rnd(); emit(SMOKE, .25f * sin(time * 1.6f - t * 7) * (.3f + t), .62f + t * .75f, 1, vy = -.08f, vx = .15f * cos(time * 1.6f - t * 7), max = 1.1f, s = .018f + rnd() * .03f, col = if (rnd() < .5f) c(0xFF9B6BFF) else c(0xFF2DD4BF)) }
                    if (rnd() < dt * 4) emit(SPARKLE, (rnd() - .5f) * 1.6f, (rnd() - .5f) * 1.2f, 1, vy = 0f, vx = 0f, max = .6f, col = c(0xFFFFE08A)) }
                "glitch" -> if (rnd() < dt * 3) split = max(split, .3f + rnd() * .7f)
            }
            if (charging && rnd() < dt * 14) { val an = rnd() * TAU; val r = .9f + rnd() * .3f; val p = Pt(ENERGY, cos(an) * r, .35f + sin(an) * r * .8f, 0f, 0f, 1.2f, .01f, 0f, L.glow); p.tx = 0f; p.ty = .36f; parts += p }
        }
        val it = parts.iterator()
        while (it.hasNext()) { val p = it.next(); p.life += dt
            when (p.k) {
                FLAME -> { p.vx += sin(time * 9 + p.r) * dt * .4f; p.s *= exp(-dt * 1.3f) }
                DRIP -> { if (p.life < p.hang) p.len = p.life / p.hang * .08f else { p.vy += 2.2f * dt; p.len = max(.01f, p.len * exp(-dt * 4)) }; if (p.y > 1.45f) { p.life = p.max; emit(SPLASH, p.x, 1.45f, 4, vy = -(.2f + rnd() * .3f), max = .4f, s = .006f) } }
                SPLASH, SPARK -> p.vy += 1.4f * dt
                ENERGY -> { p.x += (p.tx - p.x) * min(1f, dt * 2.5f); p.y += (p.ty - p.y) * min(1f, dt * 2.5f); if (hypot(p.tx - p.x, p.ty - p.y) < .05f) p.life = p.max }
                PUFF -> { p.vx *= exp(-dt * 2); p.vy *= exp(-dt * 2); p.s *= 1 + dt * .8f }
                HE -> { p.vy = -.18f; p.vx = sin(p.life * 6 + p.r) * .1f }
            }
            if (p.k != ENERGY) { p.x += p.vx * dt; p.y += p.vy * dt }
            if (p.life >= p.max) it.remove()
        }
        if (parts.size > 700) parts.subList(0, parts.size - 700).clear()
    }

    // ---------------------------------------------------------------- geometry
    private class EyeG(val mx: Float, val my: Float, val w: Float, val h: Float, val Ix: Float, val Iy: Float, val Ox: Float, val Oy: Float, val tx: Float, val ty: Float, val bx: Float, val by: Float, val k: Float)
    private fun eyeGeom(side: Float, open: Float, sq: Float, wd: Float, ang: Float, lid: Float): EyeG {
        val ex = side * .37f; val ey = -.13f; val w = .42f * wd; val h = .25f * wd; val a = .55f + ang * .5f
        val Ix = ex - side * w * .55f; val Iy = ey + h * .25f + a * h * .25f; val Ox = ex + side * w * .52f; val Oy = ey - h * .32f - a * h * .45f
        val mx = (Ix + Ox) / 2; val my = (Iy + Oy) / 2; val k = max(.02f, open)
        return EyeG(mx, my, w, h, Ix, Iy, Ox, Oy, mx + side * w * .05f, my - h * 2 * k * (lid + .1f), mx - side * w * .08f, my + h * 2 * k * (1 - lid) * (1 - 2 * sq), k)
    }
    private inner class Mouth(val mw: Float, val my: Float, val lift: Float, val H: Float, val sk: Float) {
        fun top(x: Float): Float { val u = x / mw; return my - lift * u * u + sk * u * .06f + chatter * .008f }
        fun bot(x: Float): Float { val u = x / mw; return top(x) + H * max(0f, 1 - u * u).pow(.8f) - chatter * .004f }
    }
    private fun mouth() = Mouth(.68f * mw.x, .33f, .22f * grin.x, (.11f + .3f * clamp(mopen.x, 0f, 1.3f)) * min(1.2f, mw.x), smirk.x)

    // ---------------------------------------------------------------- drawing
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG); private val str = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val path = Path(); private val rect = RectF()
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private fun solid(col: Int, a: Float = 1f): Paint { fill.shader = null; fill.xfermode = null; fill.color = col; fill.alpha = (Color.alpha(col) * a.coerceIn(0f, 1f)).roundToInt(); return fill }
    private fun line(col: Int, wd: Float, a: Float = 1f): Paint { str.shader = null; str.color = col; str.alpha = (255 * a.coerceIn(0f, 1f)).roundToInt(); str.strokeWidth = wd; return str }
    private fun withA(col: Int, a: Float) = Color.argb((255 * a.coerceIn(0f, 1f)).roundToInt(), Color.red(col), Color.green(col), Color.blue(col))

    private fun eyePath(g: EyeG) { path.reset(); path.moveTo(g.Ix, g.Iy); path.quadTo(g.tx, g.ty, g.Ox, g.Oy); path.quadTo(g.bx, g.by, g.Ix, g.Iy); path.close() }
    private fun drawEye(cv: Canvas, side: Float) {
        val L = look; val open = if (side < 0) openL.x else openR.x
        val g = eyeGeom(side, open, clamp(squint.x, 0f, 1f), wide.x, clamp(angry.x + rage * .6f, 0f, 1.4f), L.lid)
        val ix = g.mx + clamp(lx.x, -1f, 1f) * g.w * .28f; val iy = g.my + clamp(ly.x, -1f, 1f) * g.h * .35f - g.h * .1f
        if (open < .06f && squint.x < .5f) { path.reset(); path.moveTo(g.Ix, g.Iy); path.quadTo(g.mx, g.my - .01f, g.Ox, g.Oy); cv.drawPath(path, line(L.line, .014f)); return }
        eyePath(g); cv.save(); cv.clipPath(path)
        if (L.key == "glitch") { val s = .022f; var y = g.my - g.h * 1.2f
            while (y < g.my + g.h * 1.2f) { var x = g.mx - g.w * .7f; while (x < g.mx + g.w * .7f) { val d = hypot(x - ix, y - iy) / (g.w * .6f)
                cv.drawRect(x, y, x + s * .8f, y + s * .8f, if (d < .25f) solid(Color.WHITE) else solid(if (rnd() < .04f) c(0xFFFF2E97) else c(0xFF00F0FF), clamp(1 - d * .7f, .15f, 1f))); x += s }; y += s } }
        else {
            fill.xfermode = null; fill.shader = RadialGradient(ix, iy, g.w * .75f, L.eyeCols, floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP); fill.alpha = 255
            cv.drawRect(g.mx - 1, g.my - 1, g.mx + 1, g.my + 1, fill); fill.shader = null
            if (L.veins) for (i in 0 until 18) { val a = i / 18f * TAU + .2f; val r0 = g.w * .18f; path.reset(); path.moveTo(ix + cos(a) * r0, iy + sin(a) * r0)
                path.quadTo(ix + cos(a + .2f) * r0 * 2, iy + sin(a + .2f) * r0 * 2, ix + cos(a - .1f) * g.w * .7f, iy + sin(a - .1f) * g.w * .7f); cv.drawPath(path, line(c(0xFF780000), .004f, .55f)) }
            if (L.cracks) for (i in 0 until 7) { var a = i / 7f * TAU; var x = ix; var y = iy; path.reset(); path.moveTo(x, y)
                for (k in 0 until 4) { a += if (k % 2 == 1) .4f else -.4f; x += cos(a) * g.w * .12f; y += sin(a) * g.w * .12f; path.lineTo(x, y) }; cv.drawPath(path, line(Color.WHITE, .003f, .7f)) }
            if (L.pupil == "rings") { for ((r, col) in listOf(.62f to c(0xFF5C0000), .48f to c(0xFFFF1A1A), .3f to c(0xFF8A0000), .18f to c(0xFF1A0000))) cv.drawCircle(ix, iy, g.w * r * (if (r < .3f) pupilS.x else 1f), solid(col))
                rect.set(ix - g.w * .38f, iy - g.w * .38f, ix + g.w * .38f, iy + g.w * .38f); cv.drawArc(rect, 198f, 117f, false, line(Color.WHITE, .012f)) }
        }
        val ps = pupilS.x
        when (L.pupil) {
            "hot" -> { val r = g.w * .1f * ps; fill.shader = RadialGradient(ix, iy, r * 3, intArrayOf(Color.WHITE, Color.WHITE, withA(L.eyeCols[1], .8f), withA(L.eyeCols[1], 0f)), floatArrayOf(0f, .3f, .6f, 1f), Shader.TileMode.CLAMP); fill.alpha = 255; cv.drawCircle(ix, iy, r * 3, fill); fill.shader = null }
            "round" -> { cv.drawCircle(ix, iy, g.w * .17f * ps, solid(c(0xFF080404))); cv.drawCircle(ix + g.w * .05f, iy - g.w * .06f, g.w * .045f, solid(Color.WHITE)) }
            "slit" -> { rect.set(ix - g.w * .045f * (2.2f - ps), iy - g.h * .9f * ps, ix + g.w * .045f * (2.2f - ps), iy + g.h * .9f * ps); cv.drawOval(rect, solid(if (L.key == "frost") c(0xFF0A2A44) else c(0xFF0A0000)))
                cv.drawCircle(ix + g.w * .07f, iy - g.h * .2f, g.w * .03f, solid(Color.WHITE, .85f)) }
            "pixel" -> { val s = .03f * ps; cv.drawRect(ix - s, iy - s, ix + s, iy + s, solid(Color.BLACK)); cv.drawRect(ix - s * .4f + .01f, iy - s * .4f, ix + s * .4f + .01f, iy + s * .4f, solid(c(0xFFFF2E97))) }
        }
        fill.shader = LinearGradient(0f, g.my - g.h * 1.3f, 0f, g.my, Color.argb(140, 0, 0, 0), 0, Shader.TileMode.CLAMP); fill.alpha = 255; cv.drawRect(g.mx - g.w, g.my - g.h * 1.5f, g.mx + g.w, g.my, fill); fill.shader = null
        cv.restore()
        eyePath(g); cv.drawPath(path, line(L.line, if (L.key == "fiend") .006f else .011f))
        if (L.kohl) { cv.drawPath(Path().apply { moveTo(g.Ox, g.Oy); quadTo(g.Ox + side * .08f, g.Oy - .01f, g.Ox + side * .17f, g.Oy - .07f) }, line(L.line, .016f))
            val bx = g.mx - side * g.w * .05f; val by = g.my + g.h * .55f; cv.drawLine(bx, by, bx, by + .1f, line(L.line, .011f))
            cv.drawPath(Path().apply { moveTo(bx + side * .06f, by); quadTo(bx + side * .1f, by + .12f, bx + side * .16f, by + .1f); quadTo(bx + side * .2f, by + .06f, bx + side * .16f, by + .05f) }, line(L.line, .011f)) }
        if (L.extra == "brows") { val b = .06f + angry.x * .03f + rage * .03f; path.reset(); path.moveTo(g.Ix - side * .02f, g.Iy - .03f); path.lineTo(g.Ox + side * .07f, g.Oy - b * 1.6f); path.lineTo(g.Ox + side * .04f, g.Oy - b * 1.6f + .025f); path.close(); cv.drawPath(path, solid(c(0xFFFF1010))) }
    }
    private fun crescent(m: Mouth) { path.reset(); val N = 40; for (i in 0..N) { val x = -m.mw + 2 * m.mw * i / N; if (i == 0) path.moveTo(x, m.top(x)) else path.lineTo(x, m.top(x)) }
        for (i in N downTo 0) { val x = -m.mw + 2 * m.mw * i / N; path.lineTo(x, m.bot(x)) }; path.close() }
    private val tooth = Path()
    private fun drawMouth(cv: Canvas) {
        val L = look; val m = mouth()
        crescent(m); cv.drawPath(path, solid(L.inside))
        cv.save(); cv.clipPath(path)
        fill.shader = LinearGradient(0f, m.top(0f), 0f, m.bot(0f), intArrayOf(withA(L.glow, 0f), withA(L.glow, .25f * clamp(mopen.x, 0f, 1f)), withA(L.glow, 0f)), null, Shader.TileMode.CLAMP); fill.alpha = 255; cv.drawRect(-1f, -1f, 1f, 2f, fill); fill.shader = null
        val litTo = if (charging) -m.mw + 2 * m.mw * battery else 99f
        fun tc(x: Float) = if (x > litTo) c(0xFF2A2A30) else L.tc
        fun gap(x: Float) = m.bot(x) - m.top(x)
        when (L.teeth) {
            "jag", "fangs" -> { val n = if (L.teeth == "fangs") 15 else 10; val len = (if (L.teeth == "fangs") .11f else .1f) * mw.x
                for (i in 0 until n) { val x0 = -m.mw + 2 * m.mw * i / n; val x1 = -m.mw + 2 * m.mw * (i + 1) / n; val xm = (x0 + x1) / 2; val ln = min(gap(xm) * .92f, len * (if (L.teeth == "fangs" && i % 3 == 1) 1.35f else 1f))
                    tooth.reset(); tooth.moveTo(x0, m.top(x0) - .01f); tooth.lineTo(xm, m.top(xm) + ln); tooth.lineTo(x1, m.top(x1) - .01f); tooth.close(); fillTooth(cv, tc(xm), xm, m.top(xm), ln) }
                for (i in 0 until n - 1) { val x0 = -m.mw + 2 * m.mw * (i + .5f) / n; val x1 = -m.mw + 2 * m.mw * (i + 1.5f) / n; val xm = (x0 + x1) / 2; val ln = min(gap(xm) * .92f, len * .95f)
                    tooth.reset(); tooth.moveTo(x0, m.bot(x0) + .01f); tooth.lineTo(xm, m.bot(xm) - ln); tooth.lineTo(x1, m.bot(x1) + .01f); tooth.close(); fillTooth(cv, tc(xm), xm, m.bot(xm), -ln) } }
            "block", "gold", "drip" -> { val n = if (L.teeth == "drip") 8 else 7; val gp = .012f
                for (i in 0 until n) { val x0 = -m.mw * .92f + 1.84f * m.mw * i / n + gp; val x1 = -m.mw * .92f + 1.84f * m.mw * (i + 1) / n - gp; val xm = (x0 + x1) / 2; val y0 = m.top(xm) - .02f
                    val ln = min(gap(xm) * .55f, .14f * mw.x * (1 - abs(xm / m.mw) * .4f)); tooth.reset(); rect.set(x0, y0, x1, y0 + ln + .02f); tooth.addRoundRect(rect, .018f, .018f, Path.Direction.CW); fillTooth(cv, tc(xm), xm, y0, ln) }
                for (i in 0 until n - 1) { val x0 = -m.mw * .8f + 1.6f * m.mw * i / (n - 1) + gp; val x1 = -m.mw * .8f + 1.6f * m.mw * (i + 1) / (n - 1) - gp; val xm = (x0 + x1) / 2; val yb = m.bot(xm) + .02f
                    val ln = min(gap(xm) * .4f, .08f * mw.x); tooth.reset(); rect.set(x0, yb - ln - .02f, x1, yb); tooth.addRoundRect(rect, .016f, .016f, Path.Direction.CW); fillTooth(cv, tc(xm), xm, yb, -ln) } }
            "pixel" -> { val s = .045f; var x = -m.mw
                while (x < m.mw) { val tt = m.top(x + s / 2); val bb = m.bot(x + s / 2); if (bb - tt >= .02f) { val ln = min((bb - tt) * .45f, s * 1.6f)
                    val p = if (Math.floorMod(floor(x / s + time * 3).toInt(), 7) == 0) solid(L.tc2) else solid(tc(x)); cv.drawRect(x + .004f, tt, x + s - .004f, tt + ln, p); cv.drawRect(x + .004f, bb - ln * .7f, x + s - .004f, bb, p) }; x += s } }
            "icicle" -> { val n = 13
                for (i in 0 until n) { val x0 = -m.mw + 2 * m.mw * i / n; val x1 = -m.mw + 2 * m.mw * (i + 1) / n; val xm = (x0 + x1) / 2 + .01f * sin(i * 3.3f); val ln = min(gap(xm) * .95f, (.06f + .07f * ((i * 7) % 3) / 2f) * mw.x)
                    tooth.reset(); tooth.moveTo(x0, m.top(x0) - .01f); tooth.lineTo(xm, m.top(xm) + ln); tooth.lineTo(x1, m.top(x1) - .01f); tooth.close(); fillTooth(cv, tc(xm), xm, m.top(xm), ln) }
                var i = 1; while (i < n - 1) { val x0 = -m.mw + 2 * m.mw * i / n; val x1 = x0 + 2 * m.mw / n; val xm = (x0 + x1) / 2; val ln = min(gap(xm) * .5f, .05f)
                    tooth.reset(); tooth.moveTo(x0, m.bot(x0) + .01f); tooth.lineTo(xm, m.bot(xm) - ln); tooth.lineTo(x1, m.bot(x1) + .01f); tooth.close(); fillTooth(cv, tc(xm), xm, m.bot(xm), -ln); i += 2 } }
        }
        cv.restore()
        if (L.key != "fiend") { crescent(m); cv.drawPath(path, line(L.glow, .006f, .6f)) }
    }
    private fun fillTooth(cv: Canvas, col: Int, x: Float, y: Float, ln: Float) {
        val L = look; val dim = col == c(0xFF2A2A30); fill.xfermode = null
        if (dim) { cv.drawPath(tooth, solid(col)); return }
        fill.shader = when {
            L.teeth == "block" -> LinearGradient(0f, y, 0f, y + ln, Color.WHITE, col, Shader.TileMode.CLAMP)
            L.teeth == "gold" -> LinearGradient(x - .04f, y, x + .04f, y + ln, intArrayOf(c(0xFFFFF3C4), col, L.tc2), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
            L.teeth == "jag" && L.key == "inferno" -> LinearGradient(x - .03f, y, x + .03f, y + ln, intArrayOf(Color.WHITE, col, L.tc2), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
            L.teeth == "fangs" || L.teeth == "icicle" -> LinearGradient(0f, y, 0f, y + ln, L.tc2, Color.WHITE, Shader.TileMode.CLAMP)
            else -> null
        }
        if (fill.shader == null) fill.color = col; fill.alpha = 255; cv.drawPath(tooth, fill); fill.shader = null
        if (L.teeth == "jag" && L.key == "inferno") cv.drawPath(tooth, line(c(0xFF3C281E), .003f, .6f))
        if (L.teeth == "gold" && abs(x) < .06f) { val s = ((sin(time * 2.2f) * .5f + .5f)).pow(8); path.reset(); val cx = x + .015f; val cy = y + ln * .4f
            for (i in 0 until 8) { val a = i / 8f * TAU; val r = if (i % 2 == 1) .006f else .04f * s + .006f; if (i == 0) path.moveTo(cx + cos(a) * r, cy + sin(a) * r) else path.lineTo(cx + cos(a) * r, cy + sin(a) * r) }
            path.close(); val p = solid(Color.WHITE, s); p.xfermode = add; cv.drawPath(path, p); p.xfermode = null }
    }
    private fun drawHead(cv: Canvas) {
        val L = look; if (L.head != "demon") return
        path.reset(); path.moveTo(-.78f, -.1f); path.quadTo(-.95f, -.65f, -1.1f, -1f); path.quadTo(-.7f, -.75f, -.5f, -.6f); path.quadTo(0f, -.75f, .5f, -.6f); path.quadTo(.7f, -.75f, 1.1f, -1f)
        path.quadTo(.95f, -.65f, .78f, -.1f); path.quadTo(.7f, .7f, 0f, .82f); path.quadTo(-.7f, .7f, -.78f, -.1f); path.close()
        str.shader = LinearGradient(-1f, -1f, 1f, 1f, intArrayOf(withA(L.rim, .3f), withA(L.rim, .03f), withA(L.rim, .18f)), null, Shader.TileMode.CLAMP); str.alpha = 255; str.strokeWidth = .008f; cv.drawPath(path, str); str.shader = null
    }
    private fun drawExtras(cv: Canvas) {
        if (look.extra != "scribble") return
        for ((pts, col, ph) in scrib) { val a = .25f + .5f * (if (sin(time * 6 + ph) > .3f) 1f else .3f) + hat * .5f; path.reset()
            pts.forEachIndexed { i, p -> val jx = p[0] + sin(time * 9 + i + ph) * .006f; if (i == 0) path.moveTo(jx, p[1]) else path.lineTo(jx, p[1]) }
            cv.drawPath(path, line(col, .006f, a)) }
    }
    private fun drawParts(cv: Canvas) {
        val L = look
        for (p in parts) {
            val a = sm(p.life / .1f) * (1 - sm((p.life - p.max + .3f) / .3f)); val x = p.x; val y = p.y
            when (p.k) {
                FLAME -> { val t = p.life / p.max; val col = if (t < .3f) c(0xFFFFF0B4) else if (t < .6f) c(0xFFFF8C1E) else c(0xFFC82800); val r = max(.001f, p.s * (1.2f - t * .4f))
                    fill.shader = RadialGradient(x, y, r, withA(col, .5f * (1 - t)), withA(col, 0f), Shader.TileMode.CLAMP); fill.alpha = 255; fill.xfermode = add; cv.drawCircle(x, y, r, fill); fill.shader = null; fill.xfermode = null }
                EMBER, SPARKLE -> { val tw = .5f + .5f * sin(time * 12 + p.r * 9); val q = solid(if (p.col != 0) p.col else L.glow, a * (if (p.k == SPARKLE) tw else .9f)); cv.drawCircle(x, y, if (p.k == SPARKLE) .006f + .006f * tw else .006f, q) }
                SNOW -> cv.drawCircle(x + sin(time + p.r) * .02f, y, p.s, solid(c(0xFFDCF0FF), a * .8f))
                MIST, SMOKE, PUFF -> { val r = max(.001f, p.s * (1 + p.life * (if (p.k == SMOKE) .9f else .8f))); val col = if (p.col != 0) p.col else if (p.k == MIST) c(0xFFBFEFFF) else L.glow
                    fill.shader = RadialGradient(x, y, r, withA(col, a * (if (p.k == SMOKE) .22f else .18f)), withA(col, 0f), Shader.TileMode.CLAMP); fill.alpha = 255; fill.xfermode = add; cv.drawCircle(x, y, r, fill); fill.shader = null; fill.xfermode = null }
                DRIP -> { val q = solid(c(0xFFB8FF6A), .9f); rect.set(x - p.s, y + p.len - p.s * 1.3f, x + p.s, y + p.len + p.s * 1.3f); cv.drawOval(rect, q); cv.drawRect(x - p.s * .35f, y - .01f, x + p.s * .35f, y + p.len, q) }
                SPLASH, SPARK -> cv.drawCircle(x, y, if (p.k == SPARK) .006f else p.s, solid(if (p.k == SPARK) Color.WHITE else c(0xFFB8FF6A), a))
                ENERGY -> cv.drawCircle(x, y, .008f, solid(p.col, .9f))
                HE -> { cv.save(); cv.translate(x, y); cv.rotate(sin(p.r) * 17f); cv.scale(.001f, .001f); textP.textSize = 80f; textP.color = withA(L.glow, a * .85f); cv.drawText(listOf("HE", "HE", "HA", "HEH")[(p.r * 10).toInt() % 4], 0f, 0f, textP); cv.restore() }
            }
        }
    }

    // ---------------------------------------------------------------- GL plumbing
    private var prog = 0; private var vbo = 0; private var tex = 0; private var bmp: Bitmap? = null; private var cvs: Canvas? = null; private var uRect = 0; private var uTex = 0
    override fun onSurfaceCreated() {
        prog = Gl.program(VS, FS); uRect = GLES20.glGetUniformLocation(prog, "uRect"); uTex = GLES20.glGetUniformLocation(prog, "uTex")
        val ids = IntArray(1); GLES20.glGenBuffers(1, ids, 0); vbo = ids[0]
        val q = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f); val fb = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().put(q); fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo); GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 32, fb, GLES20.GL_STATIC_DRAW); tex = 0
    }
    override fun onSurfaceChanged(width: Int, height: Int) { w = max(1, width); h = max(1, height); val m = min(w, h).toFloat(); halfW = .5f * w / m; halfH = .5f * h / m }
    override fun release() { if (prog != 0) GLES20.glDeleteProgram(prog); if (vbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0); if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0); prog = 0; vbo = 0; tex = 0; bmp?.recycle(); bmp = null; cvs = null }

    // face-unit box covered by the bitmap
    private val X0 = -1.3f; private val X1 = 1.3f; private val Y0 = -1.2f; private val Y1 = 1.6f
    override fun draw(input: SceneInput) {
        val m = min(w, h); val ppu = min(sUv * m, 300f)
        val bw = ((X1 - X0) * ppu).roundToInt().coerceAtLeast(8); val bh = ((Y1 - Y0) * ppu).roundToInt().coerceAtLeast(8)
        var b = bmp
        if (b == null || b.width != bw || b.height != bh) { b?.recycle(); b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888); bmp = b; cvs = Canvas(b); if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0); tex = 0 }
        val cv = cvs!!
        cv.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
        val shx = (rnd() - .5f) * shake * .05f; val shy = (rnd() - .5f) * shake * .05f
        cv.save(); cv.scale(ppu, ppu); cv.translate(-X0 + shx, -Y0 + shy)
        paintFace(cv)
        cv.restore()
        // glitch: tear a few horizontal slices sideways
        if ((look.key == "glitch" && split > .2f) || split > .7f) for (i in 0 until 4) {
            val y = (rnd() * bh).toInt(); val hh = (3 + rnd() * 14).toInt().coerceAtMost(bh - y); if (hh <= 0) continue; val dx = ((rnd() - .5f) * 60 * split).toInt()
            val row = IntArray(bw * hh); b.getPixels(row, 0, bw, 0, y, bw, hh); val out = IntArray(bw * hh)
            for (r in 0 until hh) for (x in 0 until bw) { val sx = x - dx; out[r * bw + x] = if (sx in 0 until bw) row[r * bw + sx] else Color.BLACK }
            b.setPixels(out, 0, bw, 0, y, bw, hh)
        }
        if (tex == 0) { val t = IntArray(1); GLES20.glGenTextures(1, t, 0); tex = t[0]; GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0) } else { GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex); GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, b) }
        val vp = IntArray(4); GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0); val mm = min(vp[2], vp[3]).toFloat(); val sxN = 2f * mm / vp[2]; val syN = 2f * mm / vp[3]
        val s = sUv
        GLES20.glUseProgram(prog); GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex); GLES20.glUniform1i(uTex, 0)
        GLES20.glUniform4f(uRect, X0 * s * sxN, -Y1 * s * syN, X1 * s * sxN, -Y0 * s * syN)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo); GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4); GLES20.glDisableVertexAttribArray(0); GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
    private fun paintFace(cv: Canvas) {
        val alpha = (clamp(fade.x, 0f, 1f) * 255).roundToInt()
        if (alpha > 2) {
            cv.saveLayerAlpha(X0, Y0, X1, Y1, alpha)
            cv.rotate(tilt.x * 57.3f); cv.scale(sc.x, sc.x)
            drawHead(cv); drawExtras(cv); drawMouth(cv); drawEye(cv, -1f); drawEye(cv, 1f)
            cv.restore()
        }
        drawParts(cv)
    }

    companion object {
        private const val TAU = (PI * 2).toFloat()
        private const val FLAME = 0; private const val EMBER = 1; private const val SNOW = 2; private const val MIST = 3; private const val DRIP = 4; private const val SPLASH = 5
        private const val SMOKE = 6; private const val SPARKLE = 7; private const val SPARK = 8; private const val ENERGY = 9; private const val PUFF = 10; private const val HE = 11
        private fun rnd() = Random.nextFloat()
        private fun clamp(x: Float, a: Float, b: Float) = if (x < a) a else if (x > b) b else x
        private fun sm(x: Float): Float { val c = clamp(x, 0f, 1f); return c * c * (3 - 2 * c) }
        private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
        private fun win(t: Float, a: Float, b: Float, c: Float, d: Float) = sm((t - a) / max(1e-4f, b - a)) * (1 - sm((t - c) / max(1e-4f, d - c)))
        private const val VS = "attribute vec2 aPos; uniform vec4 uRect; varying vec2 vUv; void main(){ vUv = vec2(aPos.x, 1.0 - aPos.y); gl_Position = vec4(mix(uRect.xy, uRect.zw, aPos), 0.0, 1.0); }"
        // pixels darker than ~3% are forced to true black so AMOLED pixels stay off
        private const val FS = "precision mediump float; varying vec2 vUv; uniform sampler2D uTex; void main(){ vec3 c = texture2D(uTex, vUv).rgb; float m = max(c.r, max(c.g, c.b)); gl_FragColor = vec4(c * smoothstep(0.012, 0.03, m), 1.0); }"
    }
}
