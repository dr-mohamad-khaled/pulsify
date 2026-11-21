package app.pulsify.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
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
import kotlin.math.acos
import kotlin.math.atan2
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

/** One of the five LED-face robots. */
class BotLook(val key: String, val name: String, val loco: String, val speed: Float, val eye: Int, val head: Float, val eyeY: Float, val hitW: Float, val hitH: Float)
val BOT_LOOKS = listOf(
    BotLook("orbi", "Orbi", "hover", .9f, 0xFF5EF0D8.toInt(), .55f, .54f, .8f, 1f),
    BotLook("pixel", "Pixel", "wheel", 1.15f, 0xFFFFB36B.toInt(), .66f, .67f, .62f, .9f),
    BotLook("sentinel", "Sentinel", "walk", .55f, 0xFFFF9A6B.toInt(), .95f, .95f, .55f, 1.12f),
    BotLook("bubble", "Bubble", "tripod", .6f, 0xFFFFFFFF.toInt(), .6f, .6f, .72f, .88f),
    BotLook("tink", "Tink", "hop", 1f, 0xFF7CFFB0.toInt(), .44f, .44f, .5f, .75f),
)

/**
 * Bolt's robots: Orbi (hover drone), Pixel (unicycle), Sentinel (walker), Bubble (dome brain on spider legs),
 * Tink (spring hopper). LED dot-matrix faces, pure AMOLED black around them (no floor glow, no bloom).
 * Port of the "Pulsify Bots" browser preview: Canvas into a screen-sized bitmap, uploaded as a GL texture.
 */
class BotScene : Creature {
    override var listener: SceneListener? = null
    override var bottomInset = 0f
    override val busy: Boolean get() = act != null || held || tx != null
    var look: BotLook = BOT_LOOKS[0]
    private var lastLook: BotLook? = null

    // ---------------------------------------------------------------- state
    private class Sp(var x: Float) { var v = 0f
        fun step(t: Float, f: Float, z: Float, dt: Float) { val w = TAU * f; repeat(3) { v += (w * w * (t - x) - 2 * z * w * v) * dt / 3; x += v * dt / 3 } } }
    private var time = 0f
    private var x = 0f; private var tx: Float? = null; private var vx = 0f; private var air = 0f; private var vy = 0f; private var hov = .1f; private var bob = 0f; private var thrust = 0f
    private var wheel = 0f; private var gait = 0f; private var dir = 1f; private var hopCool = 0f; private var hopVx = 0f
    private val tilt = Sp(0f); private val sq = Sp(1f); private val aL = Sp(.25f); private val aR = Sp(.25f); private val eL = Sp(.3f); private val eR = Sp(.3f); private val ant = Sp(0f)
    private val footL = FloatArray(2); private val footR = FloatArray(2); private val footB = FloatArray(2)
    private var headTilt = 0f; private var grip = 0f; private var think = 0f
    private var lookX = 0f; private var lookY = 0f; private var lookTX = 0f; private var lookTY = 0f
    private var expr = "normal"; private var blink = 0f; private var blinkT = 0f; private var nextBlink = 2f
    private var talk = 0f; private var talkAmp = 0f; private var heat = 0f; private var glitch = 0f
    private var held = false; private var falling = false; private var grabDy = .5f; private var dropAir = 0f; private var dizzyT = -9f
    private var nextLife = 5f; private var lastTouch = -9f; private val taps = ArrayList<Float>()
    private var battery = .6f; private var charging = false; private var plugT = -9f; private var low = false
    private var bass = 0f; private var treble = 0f; private var pump = 0f; private var music = false; private var sndStart = -9f; private var sndLast = -9f; private var reacted = false; private var beatClock = 0f
    private class Notif(val t0: Float, val quiet: Boolean, val icon: String = "", val app: String = "", val who: String = "", val msg: String = "")
    private var notif: Notif? = null
    private class Act(val n: String, val d: Float) { var t = 0f }
    private var act: Act? = null
    private var scanX = Float.NaN; private var scanY = 0f

    private val FACES = listOf("happy", "heart", "wink", "star", "angry", "wow", "g:?", "g:n", "dizzy", "happy")
    private fun doAct(n: String) {
        val d = mapOf("wave" to 2.4f, "scan" to 3.2f, "jump" to 1.2f, "glitch" to 1.3f, "nap" to 9f, "faces" to FACES.size * .45f, "look" to 3f, "tickle" to 1.4f, "overheat" to 4f, "hello" to 1.6f, "cheer" to 2f)[n] ?: 2f
        act = Act(n, d); tx = null
        if (n == "glitch") glitch = 1f
        if (n == "overheat") heat = 1f
        if (n == "jump" || n == "cheer") { if (air <= 0 && look.loco != "hover") { vy = 2.3f; sq.v -= 4 } else sq.v += 3 }
    }
    private fun wake() { if (act?.n == "nap") { act = null; sq.v += 4; ant.v += 6 } }

    // ---------------------------------------------------------------- layout (bitmap pixels)
    private var W = 1f; private var H = 1f; private var U = 1f; private var GY = 1f; private var CX = .5f; private var bw = 2; private var bh = 2
    private fun botScreen(ly: Float, out: FloatArray = tmp2): FloatArray { out[0] = CX + x * U; out[1] = GY - (air + ly + (if (look.loco == "hover") hov - .1f else 0f)) * U; return out }
    private val tmp2 = FloatArray(2)

    // ---------------------------------------------------------------- particles (bitmap px)
    private class Pt(val k: Int, var x: Float, var y: Float, var vx: Float, var vy: Float, val max: Float, val s: Float, val r: Float) { var life = 0f }
    private val parts = ArrayList<Pt>()
    private fun emit(k: Int, px: Float, py: Float, n: Int, sp: Float = 1f) { repeat(n) { parts += Pt(k, px + (rnd() - .5f) * 20 * U / 200, py + (rnd() - .5f) * 10 * U / 200, (rnd() - .5f) * 120 * sp * U / 200, -(40 + rnd() * 80) * sp * U / 200, 1 + rnd() * .8f, 8 + rnd() * 8, rnd() - .5f) } }

    // ---------------------------------------------------------------- input (normalised 0..1, y down)
    private var fOn = false; private var fx = 0f; private var fy = 0f; private var fx0 = 0f; private var fy0 = 0f; private var fT0 = 0f; private var fMoved = 0f; private var fOnBot = false; private var fLong = false
    private fun hitBot(px: Float, py: Float): Boolean { val bx = CX + x * U; val lift = air + (if (look.loco == "hover") hov else 0f)
        return abs(px - bx) < look.hitW * U / 2 && py < GY - lift * U + 6 && py > GY - (lift + look.hitH) * U }
    override fun touchDown(nx: Float, ny: Float): Boolean {
        fx = nx * W; fy = ny * H; fx0 = fx; fy0 = fy; fT0 = time; fMoved = 0f; fOn = true; fOnBot = hitBot(fx, fy); fLong = false; lastTouch = time; wake()
        if (fOnBot) { val gy = botScreen(0f)[1]; grabDy = (gy - fy) / U - air }
        return true
    }
    override fun touchMove(nx: Float, ny: Float): Boolean {
        if (!fOn) return false; fx = nx * W; fy = ny * H; fMoved = max(fMoved, hypot(fx - fx0, fy - fy0)); lastTouch = time
        if (fOnBot && !held && fMoved > 8 * U / 200) { held = true; falling = false; tx = null; act = null; vy = 0f; ant.v += 8; val p = botScreen(.5f); emit(SPARK, p[0], p[1], 4) }
        return true
    }
    override fun touchUp(): Boolean {
        if (!fOn) return false; fOn = false; scanX = Float.NaN
        if (held) { held = false; falling = true; dropAir = air; vy = 0f; return true }
        val quick = time - fT0 < .35f && fMoved < 10 * U / 200
        if (fOnBot && quick) {
            taps.removeAll { time - it > .9f }; taps += time
            if (taps.size >= 3) { taps.clear(); doAct("overheat"); listener?.onDiscovery("bolt overheat"); return true }
            doAct("tickle"); val p = botScreen(look.head + .2f); emit(if (rnd() < .5f) HEART else SPARK, p[0], p[1], 3); return true
        }
        if (!fOnBot && quick) { tx = clamp((fx - CX) / U, -(W / 2 / U) + .45f, W / 2 / U - .45f); if (act != null && act!!.n != "overheat") act = null }
        return true
    }
    override fun tapAt(nx: Float, ny: Float): Boolean = true
    override fun longPressAt(nx: Float, ny: Float): Boolean = true
    override fun greet() { if (act == null && !held) doAct("hello") }
    override fun swiped(dir: Float, fast: Boolean) { if (act == null && !held) doAct("look") }
    override fun perform(trick: String): Boolean {
        wake(); lastTouch = time
        when (trick) {
            "notify" -> { act = null; val n = listOf(arrayOf("💬", "WhatsApp", "Mom", "اتغديت يا حبيبي؟"), arrayOf("❤️", "Instagram", "pulsify.app", "12 new likes"), arrayOf("✉️", "Gmail", "Ahmed", "Meeting moved to 3 PM"), arrayOf("📅", "Calendar", "Reminder", "Gym in 30 minutes"))[Random.nextInt(4)]
                notif = Notif(time, false, n[0], n[1], n[2], n[3]); val p = botScreen(look.head + .35f); emit(BANG, p[0], p[1], 1, 0f) }
            "wave", "scan", "jump", "glitch", "nap", "faces", "look" -> doAct(trick)
            "dance" -> doAct("cheer")
            else -> return false
        }
        return true
    }

    // ---------------------------------------------------------------- update
    override fun update(dt0: Float, input: SceneInput) {
        val dt = min(dt0, .05f); time += dt; val B = look
        if (lastLook !== B) { lastLook = B; air = .5f; vy = 0f; falling = true; dropAir = 0f; act = null; tx = null; parts.clear(); doAct("hello") }
        // audio + the sound timeline: a new sound = startled look-up; still going after 4 s = music -> dance
        val a0 = input.audio; val lvl = if (a0.active) a0.level else 0f
        bass = max(if (a0.active) a0.bass * a0.bass * 1.3f else 0f, bass * exp(-dt * 7)); treble = max(if (a0.active) a0.treble else 0f, treble * exp(-dt * 7)); pump = if (a0.active) a0.beat else pump * exp(-dt * 6)
        if (lvl > .02f) { if (time - sndLast > .8f) { sndStart = time; reacted = false }; sndLast = time }
        val sndDur = if (time - sndLast < .8f) time - sndStart else 0f
        if (sndDur > 0 && !reacted) { reacted = true; if (notif == null) { notif = Notif(time, true); val p = botScreen(B.head + .35f); emit(BANG, p[0], p[1], 1, 0f) } }
        music = sndDur > 4f
        if (input.charging && !charging) { plugT = time; wake(); doAct("cheer"); val p = botScreen(.3f); emit(SPARK, p[0], p[1], 8) }
        charging = input.charging; battery = input.battery.coerceIn(0f, 1f); low = input.lowBattery && !charging
        act?.let { it.t += dt; if (it.t >= it.d) act = null }
        notif?.let { if (time - it.t0 > (if (it.quiet) 2.6f else 5f)) notif = null }
        // ---------- motion
        val prevVx = vx
        if (held && fOn) {
            val nx = clamp((fx - CX) / U, -W / 2 / U + .2f, W / 2 / U - .2f); val ox = x
            x += (nx - x) * min(1f, dt * 18); vx = (x - ox) / max(dt, 1e-3f)
            air += (max(0f, (GY - fy) / U - grabDy) - air) * min(1f, dt * 18)
        } else {
            if (falling || air > 0 || vy != 0f) {
                if (B.loco == "hover") { vy += (-air * 30 - vy * 5) * dt; air += vy * dt; if (abs(air) < .003f && abs(vy) < .05f) { air = 0f; vy = 0f; falling = false }; x += vx * dt; vx *= exp(-dt * 3) }
                else {
                    vy -= 7 * dt; air += vy * dt; if (falling) { x += vx * dt; vx *= exp(-dt * 1.5f) }
                    if (air <= 0) { val hard = -vy; air = 0f; vy = 0f; sq.v -= hard * 3; tilt.v += (rnd() - .5f) * hard * 3; val p = botScreen(0f); emit(DUST, p[0], p[1], 5)
                        if (falling) { falling = false; vx = 0f; if (dropAir > .45f) dizzyT = time else ant.v += 6 }
                        if (B.loco == "hop" && tx != null) hopCool = .12f }
                }
            }
            x = clamp(x, -W / 2 / U + .3f, W / 2 / U - .3f)
            val sp = B.speed * (if (low) .5f else 1f); val t0 = tx
            if (t0 != null && !falling) {
                val d = t0 - x; dir = if (d != 0f) sign(d) else dir
                if (B.loco == "hop") { hopCool -= dt; if (air <= 0 && hopCool <= 0) { vy = 1.7f; sq.v -= 3; hopVx = clamp(d / .5f, -sp * 1.4f, sp * 1.4f) }; vx = if (air > 0) hopVx else 0f }
                else vx += (clamp(d * 3, -sp, sp) - vx) * min(1f, dt * 5)
                x += vx * dt
                if (abs(d) < .03f && air <= 0) { tx = null; vx = 0f; if (act == null) doAct(if (rnd() < .5f) "hello" else "look") }
            } else if (!falling) { vx *= exp(-dt * 6); if (B.loco != "hover") x += vx * dt }
        }
        val moving = clamp(abs(vx) / .4f, 0f, 1f); val acc = (vx - prevVx) / max(dt, 1e-3f)
        wheel -= vx * dt / .13f
        if (air <= 0 && !held) gait += abs(vx) * dt * (if (B.loco == "walk") 8f else 11f)
        // ---------- targets
        beatClock += dt
        val dancing = music && !held && !falling && !charging && act?.n != "nap" && act?.n != "overheat" && tx == null
        val bn = floor(beatClock * 2f).toInt(); val beat = (beatClock * 2f) % 1f; val move = (bn / 8) % 3; val alt = if (bn % 2 == 1) 1f else -1f
        var tAL = .25f + .05f * sin(time * 1.3f); var tAR = .25f + .05f * sin(time * 1.3f + 1); var tEL = .35f; var tER = .35f; var tiltT = 0f; var sqT = 1f; var ex = "normal"; var bobT = .012f * sin(time * 2); var hTilt = 0f
        think = max(0f, think - dt * .5f); grip = .5f + .5f * sin(time * 2)
        when (B.loco) {
            "hover" -> { hov = (if (act?.n == "nap") .03f else .1f) + .025f * sin(time * 2.2f); thrust = moving + (if (held) .8f else 0f); tiltT = -vx * .35f; bobT = 0f }
            "wheel" -> tiltT = -clamp(acc, -6f, 6f) * .035f - vx * .12f + .03f * sin(time * 1.7f)
            else -> tiltT = -vx * .08f
        }
        val g = gait
        footL[0] = dir * .03f * cos(g) * moving; footL[1] = max(0f, sin(g)) * .07f * moving
        footR[0] = dir * .03f * cos(g + PI.toFloat()) * moving; footR[1] = max(0f, sin(g + PI.toFloat())) * .07f * moving
        footB[0] = 0f; footB[1] = max(0f, sin(g + PI.toFloat() / 2)) * .05f * moving
        if (B.loco == "walk" || B.loco == "tripod") bobT = -.015f * abs(sin(g)) * moving + .006f * sin(time * 2)
        if (moving > .1f && B.loco != "hover") { tAL = .3f + .35f * sin(g) * moving; tAR = .3f - .35f * sin(g) * moving }
        val e = botScreen(B.eyeY, FloatArray(2))
        if (fOn && !held) { lookTX = clamp((fx - e[0]) / U * 1.4f, -1f, 1f); lookTY = clamp((e[1] - fy) / U * 1.4f, -1f, 1f) }
        else if (time - lastTouch > 2 && rnd() < dt * .5f) { lookTX = (rnd() - .5f) * 1.4f; lookTY = (rnd() - .5f) * .8f }
        if (tx != null) lookTX = dir * .7f
        // finger held on empty glass > 1 s: comes over and scans it
        if (fOn && !fOnBot && fMoved < 12 * U / 200 && time - fT0 > .9f && !fLong) { fLong = true; tx = clamp((fx - CX) / U + (if (fx > e[0]) -.55f else .55f), -W / 2 / U + .45f, W / 2 / U - .45f) }
        if (fOn && fLong) { scanX = fx; scanY = fy; ex = "scan"; think = 1f } else scanX = Float.NaN
        act?.let { a ->
            val t = a.t
            when (a.n) {
                "wave" -> { tAR = mix(tAR, 2.6f, win(t, 0f, .3f, 2f, 2.4f)); tER = .3f + .55f * sin(t * 12) * win(t, .3f, .45f, 1.9f, 2.1f); ex = "happy"; talk = win(t, .3f, .4f, 1.4f, 1.5f); hTilt = .1f }
                "hello" -> { tAL = mix(.25f, 1.9f, win(t, 0f, .25f, 1.2f, 1.6f)); tAR = tAL; ex = "happy"; sqT = 1 + .05f * sin(t * 10) * win(t, 0f, .2f, 1f, 1.4f) }
                "scan" -> { ex = "scan"; lookTX = sin(t * 2.2f); think = 1f; tAR = mix(tAR, 1.4f, win(t, 0f, .3f, 2.8f, 3.2f)) }
                "look" -> { lookTX = sin(t * 2.1f); lookTY = .2f * cos(t * 1.4f); hTilt = .12f * sin(t * 2.1f) }
                "jump" -> { tAL = mix(.25f, 2.5f, win(t, .05f, .25f, .8f, 1.1f)); tAR = tAL; ex = if (air > 0) "happy" else "normal" }
                "cheer" -> { tAL = 2.6f + .3f * sin(t * 14); tAR = tAL; ex = "star" }
                "glitch" -> { ex = listOf("x", "g:?", "wow", "dizzy", "g:*", "scan")[Random.nextInt(6)]; hTilt = (rnd() - .5f) * .3f }
                "nap" -> { ex = "sleep"; tAL = .08f; tAR = .08f; sqT = .93f; tiltT += .08f; bobT = -.01f; if (rnd() < dt * .8f) { val p = botScreen(B.head + .25f); emit(ZZ, p[0], p[1], 1, .4f) } }
                "faces" -> ex = FACES[min(FACES.size - 1, (t / .45f).toInt())]
                "tickle" -> { ex = if (t < .7f) "happy" else "wink"; tAL = .9f + .45f * sin(t * 22); tAR = .9f - .45f * sin(t * 22); sqT = 1 + .05f * sin(t * 25) * (1 - t / 1.4f); tiltT += .08f * sin(t * 18) * (1 - t / 1.4f); talk = 1f }
                "overheat" -> { ex = if (t < 1.8f) "x" else "dizzy"; tAL = 1.7f + .6f * sin(t * 24); tAR = 1.7f - .6f * sin(t * 24); tiltT += .06f * sin(t * 30) * (1 - t / 4); if (rnd() < dt * 9) { val p = botScreen(B.head + .3f); emit(STEAM, p[0], p[1], 1, .6f) } }
            }
        }
        heat = max(0f, heat - dt * .3f); glitch = max(0f, glitch - dt * .9f)
        nextLife -= dt
        if (nextLife < 0 && act == null && !dancing && !held && !fOn && tx == null && notif == null && time - lastTouch > 3) {
            nextLife = 6 + rnd() * 6
            if (rnd() < .35f) tx = clamp(x + (rnd() - .5f) * 2.2f, -W / 2 / U + .5f, W / 2 / U - .5f)
            else doAct(if (low) "nap" else listOf("wave", "scan", "faces", "look", "look", "jump", "glitch", if (rnd() < .15f) "nap" else "look")[Random.nextInt(8)])
        }
        if (dancing && act == null) {
            val sw = sin((bn + beat) * PI.toFloat()); val pm = max(pump, (1 - beat).pow(3) * .6f)
            when (move) {
                0 -> { sqT = 1 - .08f * pm; tAL = 1.6f + .9f * (if (alt > 0) pm else 0f); tAR = 1.6f + .9f * (if (alt < 0) pm else 0f); tEL = .8f; tER = .8f; tiltT = .1f * sw }
                1 -> { tiltT = .16f * sw; tAL = 2.5f + .3f * sw; tAR = 2.5f - .3f * sw; hTilt = .15f * sw; lookTX = sw * .6f }
                else -> { val k = bn % 4; tAL = floatArrayOf(.4f, 1.57f, 2.8f, 1.57f)[k]; tAR = floatArrayOf(2.8f, 1.57f, .4f, 1.57f)[k]; tEL = floatArrayOf(1.57f, 0f, .5f, 1.57f)[k]; tER = floatArrayOf(0f, 1.57f, .5f, 0f)[k]; tiltT = floatArrayOf(.12f, -.12f, .12f, -.12f)[k] * .6f; hTilt = floatArrayOf(-.15f, .15f, -.15f, .15f)[k] }
            }
            if (B.loco == "hop" && air <= 0 && beat < .1f && bn % 2 == 0) { vy = 1.2f; sq.v -= 2.5f }
            if (B.loco == "wheel") x += cos((bn + beat) * PI.toFloat() / 2) * .5f * dt
            if (B.loco == "walk" || B.loco == "tripod") { footL[0] = 0f; footL[1] = if (alt > 0) .05f * pm else 0f; footR[0] = 0f; footR[1] = if (alt < 0) .05f * pm else 0f; footB[1] = .03f * pm }
            bobT = .03f * pm * (if (B.loco == "hover") -1f else 1f) * (if (B.loco == "hop") 0f else 1f) - (if (B.loco == "walk") .03f * pm else 0f)
            ex = listOf("g:n", "happy", "heart", "star")[(bn / 4) % 4]; talk = 1f
            if (pm > .9f && rnd() < .5f) { val p = botScreen(B.head + .3f); emit(NOTE, p[0], p[1], 1, .6f) }
        }
        if (charging && !held) { if (act == null || act!!.n == "look") { ex = if (battery >= .995f) "heart" else "pct"; tAL = .15f; tAR = .15f }; if (rnd() < dt * 3) { val p = botScreen(.15f); emit(BOLT, p[0], p[1], 1, .3f) } }
        if (low && act == null && !dancing) { ex = "tired"; tAL = .05f; tAR = .05f; sqT = .95f }
        notif?.let { n -> if (!held) {
            val u = time - n.t0; lookTX = .15f; lookTY = 1f; ex = if (u < .45f) "g:!" else if (n.quiet) "wow" else if (u > 1.1f) "happy" else "wow"
            sqT += -.12f * win(u, 0f, .05f, .1f, .35f); if (u < .05f) ant.v += 10
            if (!n.quiet) { tAR = mix(tAR, 2.85f, win(u, .5f, .8f, 4.2f, 4.8f)); tER = .1f; talk = win(u, 1f, 1.1f, 3.4f, 3.5f) } } }
        if (held) { ex = "wow"; tAL = 2.2f + .5f * sin(time * 13); tAR = 2.2f + .5f * sin(time * 13 + 1.6f); tEL = .7f; tER = .7f; tiltT = clamp(-vx * .12f, -.6f, .6f); lookTY = -1f; lookTX = 0f; talk = .6f }
        if (falling) { ex = "wow"; tAL = 2.6f; tAR = 2.6f }
        if (time - dizzyT < 2) { ex = "dizzy"; tiltT += .1f * sin(time * 9); hTilt = .12f * sin(time * 7) }
        if (!scanX.isNaN() && !ex.startsWith("g")) ex = "scan"
        if (glitch > 0 && rnd() < .3f) ex = listOf("x", "g:?", "wow", "g:*")[Random.nextInt(4)]
        expr = ex
        nextBlink -= dt; if (nextBlink < 0) { blinkT = 1f; nextBlink = 2 + rnd() * 3.5f; if (rnd() < .2f) nextBlink = .25f }
        blinkT = max(0f, blinkT - dt * 7); blink = sin(blinkT * PI.toFloat())
        if (act == null && notif == null && !dancing && !held) talk = 0f
        talkAmp = if (talk > 0) .5f + .5f * abs(sin(time * 14) * sin(time * 5.3f)) else 0f
        lookX += (lookTX - lookX) * min(1f, dt * 10); lookY += (lookTY - lookY) * min(1f, dt * 10)
        headTilt += (hTilt - headTilt) * min(1f, dt * 8)
        tilt.step(clamp(tiltT, -.8f, .8f), if (held) 1.6f else 2.6f, if (held) .12f else .32f, dt)
        sq.step(sqT + .05f * bass, 5f, .25f, dt)
        aL.step(tAL, 4.5f, .45f, dt); aR.step(tAR, 4.5f, .45f, dt); eL.step(tEL, 5f, .5f, dt); eR.step(tER, 5f, .5f, dt)
        ant.step(-tilt.x * .8f - vx * .3f + .08f * sin(time * 1.8f) + (if (dancing) .3f * sin((bn + beat) * PI.toFloat()) else 0f), 2.2f, .12f, dt)
        bob += (bobT - bob) * min(1f, dt * 12)
        val k = U / 200f
        val it = parts.iterator()
        while (it.hasNext()) { val p = it.next(); p.life += dt; p.x += p.vx * dt; p.y += p.vy * dt; if (p.k == SPARK || p.k == DUST) p.vy += 260 * k * dt; p.vx *= exp(-dt * 1.5f); if (p.life > p.max) it.remove() }
    }

    // ---------------------------------------------------------------- LED faces
    private fun glyphFor(e: String, side: Int): Array<String>? {
        if (e.startsWith("g:")) return G[e[2]]
        if (e == "pct" && side != 0) { val p = min(99, (battery * 100).roundToInt()); return G[(if (side < 0) p / 10 else p % 10).digitToChar()] }
        return null
    }
    private fun eyeOn(e: String, u: Int, v: Int, side: Int, lx: Float, ly: Float, open: Float, t: Float, gl: Array<String>?): Boolean {
        if (gl != null) { val r = 3 - v; val c = u + 2; return r in 0..6 && c in 0..4 && gl[r][c] == '1' }
        val x = u - lx; val y = v - ly; val uf = u.toFloat(); val vf = v.toFloat()
        return when (e) {
            "happy" -> abs(vf - ly * .4f - (1.1f - .36f * x * x)) < .85f && abs(x) <= 3.2f
            "sad" -> abs(vf - ly * .4f - (-1.6f + .3f * x * x)) < .8f && abs(x) <= 3f
            "heart" -> { val X = uf / 2.6f; val Y = (vf + .4f) / 2.6f; val a = X * X + Y * Y - 1; a * a * a - X * X * Y * Y * Y <= 0 }
            "x" -> abs(abs(uf) - abs(vf)) < .6f && abs(uf) <= 2.6f && abs(vf) <= 2.6f
            "wow" -> { val r = hypot(x, y * .95f); r > 1.5f && r < 3.15f }
            "sleep" -> (v == -1 && abs(u) <= 2) || (v == -2 && abs(u) <= 1)
            "angry" -> (x / 2.6f).pow(2) + (y / 3f).pow(2) <= 1 && vf < 1.1f + side * uf * .8f
            "tired" -> (x / 2.7f).pow(2) + (y / 3f).pow(2) <= 1 && vf <= (ly * .5f).roundToInt()
            "dizzy" -> { val r = hypot(uf, vf); if (r < .8f || r > 3.25f) false else { val a = atan2(vf, uf) / TAU + r * .2f + t * 1.6f * side; ((a % 1) + 1) % 1 < .45f } }
            "scan" -> abs(vf - (2.6f * sin(t * 5)).roundToInt()) < .6f && abs(u) <= 3
            "star" -> { val r = hypot(uf, vf); val a = atan2(vf, uf) + PI.toFloat() / 2; r <= 1.2f + 2 * abs(cos(a * 2.5f)).pow(3) }
            "wink" -> if (side > 0) abs(vf - (1.1f - .36f * uf * uf)) < .85f && abs(uf) <= 3.2f else normalEye(x, y, open)
            else -> normalEye(x, y, open)
        }
    }
    private fun normalEye(x: Float, y: Float, open: Float) = if (open < .2f) y.roundToInt() == 0 && abs(x) <= 2.2f else (x / 2.25f).pow(2) + (y / (3.05f * open)).pow(2) <= 1
    private fun eyeCol(base: Int): Int = when {
        expr == "heart" -> PINK; expr == "angry" || expr == "x" || heat > .5f -> RED
        expr == "pct" -> if (battery < .2f) RED else GREEN; expr == "g:n" -> AMB; else -> base }
    private fun ledEyes(cv: Canvas, cx: Float, cy: Float, c: Float, gap: Float, col: Int, n: Int = 2) {
        val r = c * .38f; val lx = clamp(lookX, -1f, 1f) * 1.2f; val ly = clamp(lookY, -1f, 1f) * 1.1f; val open = 1 - blink
        val on = solid(col); val off = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(11, 255, 255, 255) }
        val sides = if (n == 2) intArrayOf(-1, 1) else intArrayOf(0)
        for (side in sides) { val ex = cx + side * gap; val gl = glyphFor(expr, side)
            for (v in -3..3) for (u in -3..3) { val lit = eyeOn(expr, u, v, if (side == 0) 1 else side, lx, ly, open, time, gl); cv.drawCircle(ex + u * c, cy + v * c, r, if (lit) on else off) } }
    }
    private fun mouthShape(): String { val e = expr
        if (talk > 0) return "talk"
        return when (e) { "happy", "heart", "wink", "star", "pct" -> "smile"; "wow", "g:!", "g:?" -> "open"; "sleep", "tired", "scan" -> "flat"; "x", "dizzy" -> "zig"; "angry", "sad" -> "sad"; "g:n" -> "talk"; else -> "small" } }
    private fun ledMouth(cv: Canvas, cx: Float, cy: Float, c: Float, col: Int) {
        val r = c * .38f; val sh = mouthShape(); val on = solid(col); val off = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(9, 255, 255, 255) }
        for (v in -1..1) for (u in -3..3) {
            val lit = when (sh) {
                "smile" -> v == clamp(-1 + .2f * u * u, -1f, 1f).roundToInt()
                "sad" -> v == clamp(1 - .2f * u * u, -1f, 1f).roundToInt()
                "open" -> (abs(u) <= 1 && abs(v) == 1) || (abs(u) == 2 && v == 0)
                "flat" -> v == 0 && abs(u) <= 2
                "zig" -> v == (if ((u + 3) % 2 == 1) 1 else -1)
                "talk" -> abs(u) <= 2 && abs(v) <= (if (talkAmp > .6f) 1 else 0) && !(abs(u) == 2 && v != 0)
                else -> v == 0 && abs(u) <= 1
            }
            cv.drawCircle(cx + u * c, cy + v * c, r, if (lit) on else off)
        }
    }

    // ---------------------------------------------------------------- drawing helpers (robot units, y up)
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG); private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path(); private val rect = RectF(); private val addMode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private fun solid(col: Int, a: Float = 1f): Paint { fp.shader = null; fp.xfermode = null; fp.color = col; fp.alpha = (Color.alpha(col) * a.coerceIn(0f, 1f)).roundToInt(); return fp }
    private fun shaded(s: Shader): Paint { fp.xfermode = null; fp.shader = s; fp.color = Color.BLACK; fp.alpha = 255; return fp }
    private fun stroke(col: Int, w: Float, a: Float = 1f): Paint { sp.shader = null; sp.pathEffect = null; sp.color = col; sp.alpha = (Color.alpha(col) * a.coerceIn(0f, 1f)).roundToInt(); sp.strokeWidth = w; return sp }
    private fun lg(x0: Float, y0: Float, x1: Float, y1: Float, vararg c: Int): Shader = LinearGradient(x0, y0, x1, y1, c, null, Shader.TileMode.CLAMP)
    private fun rg(x: Float, y: Float, r: Float, c: IntArray, st: FloatArray? = null): Shader = RadialGradient(x, y, max(r, 1e-4f), c, st, Shader.TileMode.CLAMP)
    private fun aCol(col: Int, a: Float) = Color.argb((255 * a.coerceIn(0f, 1f)).roundToInt(), Color.red(col), Color.green(col), Color.blue(col))
    private fun circle(cv: Canvas, x: Float, y: Float, r: Float, p: Paint) = cv.drawCircle(x, y, r, p)
    private fun oval(cv: Canvas, x: Float, y: Float, rx: Float, ry: Float, p: Paint) { rect.set(x - rx, y - ry, x + rx, y + ry); cv.drawOval(rect, p) }
    private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) { path.reset(); rect.set(x - w / 2, y - h / 2, x + w / 2, y + h / 2); path.addRoundRect(rect, r, r, Path.Direction.CW) }
    private fun rim(cv: Canvas, p: Path, w: Float) { sp.pathEffect = null; sp.shader = lg(-w, 0f, w, 0f, aCol(VIO, .9f), aCol(VIO, 0f), aCol(AMB, 0f), aCol(AMB, .9f)); sp.alpha = 255; sp.strokeWidth = .011f; cv.drawPath(p, sp); sp.shader = null }
    private fun seg(cv: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, col: Int) = cv.drawLine(x0, y0, x1, y1, stroke(col, w))
    private fun arm2(cv: Canvas, sx: Float, sy: Float, side: Float, a: Float, b: Float, l1: Float, l2: Float, w: Float, col: Int, hand: String) {
        val a1 = -PI.toFloat() / 2 + side * a; val ex = sx + cos(a1) * l1; val ey = sy + sin(a1) * l1
        val a2 = a1 + side * b; val hx = ex + cos(a2) * l2; val hy = ey + sin(a2) * l2
        seg(cv, sx, sy, ex, ey, w, col); seg(cv, ex, ey, hx, hy, w * .9f, col); seg(cv, sx, sy, ex, ey, w * .25f, Color.argb(20, 255, 255, 255))
        circle(cv, ex, ey, w * .62f, solid(0xFF3A3150.toInt())); circle(cv, ex, ey, w * .22f, solid(AMB))
        when (hand) {
            "claw" -> { val o = .45f + .25f * grip; for (k in intArrayOf(-1, 1)) { val ha = a2 + k * o; seg(cv, hx, hy, hx + cos(ha) * w * 1.25f, hy + sin(ha) * w * 1.25f, w * .5f, col) }; circle(cv, hx, hy, w * .55f, solid(0xFF4A4062.toInt())) }
            "three" -> { for (k in intArrayOf(-1, 0, 1)) { val ha = a2 + k * .5f; seg(cv, hx, hy, hx + cos(ha) * w * 1.1f, hy + sin(ha) * w * 1.1f, w * .38f, col) }; circle(cv, hx, hy, w * .6f, solid(0xFF3A3150.toInt())) }
            else -> circle(cv, hx, hy, w * .85f, shaded(rg(hx - w * .3f, hy + w * .3f, w, intArrayOf(0xFF5B4F78.toInt(), 0xFF2A2338.toInt()))))
        }
    }
    private fun ik(hx: Float, hy: Float, fx: Float, fy: Float, l1: Float, l2: Float, bend: Float): FloatArray {
        var dx = fx - hx; var dy = fy - hy; var d = hypot(dx, dy); val mx = l1 + l2 - 1e-4f
        if (d > mx) { dx *= mx / d; dy *= mx / d; d = mx }
        val a = atan2(dy, dx); val b = acos(clamp((l1 * l1 + d * d - l2 * l2) / (2 * l1 * max(d, 1e-4f)), -1f, 1f)); val k = a + bend * b
        return floatArrayOf(hx + cos(k) * l1, hy + sin(k) * l1, hx + dx, hy + dy)
    }

    // ---------------------------------------------------------------- the five bots
    private fun drawOrbi(cv: Canvas) {
        val cy = .5f + hov + bob
        val th = .55f + .45f * thrust + .12f * sin(time * 43) + .4f * bass
        path.reset(); path.moveTo(-.075f, cy - .27f); path.quadTo(0f, cy - .27f - .3f * th, .075f, cy - .27f); path.close()
        cv.drawPath(path, shaded(lg(0f, cy - .27f, 0f, cy - .27f - .3f * th, aCol(0xFFDFFFF8.toInt(), .9f), aCol(MINT, .5f), aCol(MINT, 0f))))
        for (sd in floatArrayOf(-1f, 1f)) {
            oval(cv, sd * .305f, cy + .02f, .05f, .1f, shaded(lg(sd * .26f, 0f, sd * .36f, 0f, 0xFF2A2338.toInt(), 0xFF120F1B.toInt())))
            oval(cv, sd * .315f, cy + .02f, .032f, .07f, stroke(LAV, .01f, .55f + .45f * bass))
        }
        path.reset(); path.addCircle(0f, cy, .3f, Path.Direction.CW)
        cv.drawPath(path, shaded(rg(-.05f, cy + .07f, .32f, intArrayOf(0xFF4E4370.toInt(), 0xFF231C35.toInt(), 0xFF0B0912.toInt()), floatArrayOf(0f, .55f, 1f)))); rim(cv, path, .3f)
        cv.save(); cv.clipPath(path); oval(cv, 0f, cy - .2f, .3f, .055f, stroke(LAV, .008f, .18f)); cv.restore()
        oval(cv, 0f, cy + .02f, .225f, .158f, shaded(lg(0f, cy + .18f, 0f, cy - .14f, 0xFF100D1A.toInt(), 0xFF020205.toInt()))); oval(cv, 0f, cy + .02f, .225f, .158f, stroke(LAV, .009f, .3f))
        val col = eyeCol(look.eye); ledEyes(cv, 0f, cy + .04f, .02f, .085f, col); ledMouth(cv, 0f, cy - .088f, .016f, col)
        cv.save(); cv.rotate(-14f, -.08f, cy + .12f); oval(cv, -.08f, cy + .12f, .11f, .032f, solid(Color.WHITE, .07f)); cv.restore()
        val ab = ant.x; val tx = sin(ab) * .15f; val ty = cy + .3f + cos(ab) * .14f
        path.reset(); path.moveTo(0f, cy + .29f); path.quadTo(tx * .2f, cy + .37f, tx, ty); cv.drawPath(path, stroke(0xFF3A3150.toInt(), .014f)); circle(cv, tx, ty, .028f, solid(AMB))
        for (sd in floatArrayOf(-1f, 1f)) {
            val a = if (sd < 0) aL.x else aR.x; val an = -PI.toFloat() / 2 + sd * a; val fl = .012f * sin(time * 3 + sd)
            val hx = sd * .34f + cos(an) * .17f; val hy = cy - .04f + sin(an) * .17f + fl
            circle(cv, hx, hy, .058f, shaded(rg(hx - .01f, hy + .01f, .06f, intArrayOf(0xFF5D5180.toInt(), 0xFF1E1830.toInt())))); circle(cv, hx, hy, .058f, stroke(LAV, .008f, .6f)); circle(cv, hx, hy, .014f, solid(MINT))
        }
    }
    private fun drawPixel(cv: Canvas) {
        val by = .62f + bob
        circle(cv, 0f, .13f, .13f, solid(0xFF0B0910.toInt())); circle(cv, 0f, .13f, .13f, stroke(0xFF2B2439.toInt(), .028f))
        for (i in 0 until 10) { val a = wheel + i / 10f * TAU; seg(cv, cos(a) * .1f, .13f + sin(a) * .1f, cos(a) * .128f, .13f + sin(a) * .128f, .016f, 0xFF3B3350.toInt()) }
        circle(cv, 0f, .13f, .05f, shaded(rg(-.008f, .14f, .05f, intArrayOf(0xFF6A5D8C.toInt(), 0xFF231C33.toInt())))); circle(cv, 0f, .13f, .032f, stroke(MINT, .008f, .8f))
        val top = by - .2f
        for (sd in floatArrayOf(-1f, 1f)) seg(cv, sd * .06f, .13f, sd * .085f, top, .022f, 0xFF3A3150.toInt())
        path.reset(); for (i in 0..6) { val y = mix(.2f, top, i / 6f); if (i == 0) path.moveTo(-.03f, y) else path.lineTo(if (i % 2 == 1) .03f else -.03f, y) }; cv.drawPath(path, stroke(AMB, .01f))
        rrect(0f, by, .52f, .46f, .16f); cv.drawPath(path, shaded(lg(-.26f, by + .2f, .26f, by - .2f, 0xFF463C61.toInt(), 0xFF221C33.toInt(), 0xFF100D18.toInt()))); rim(cv, Path(path), .26f)
        rrect(0f, by - .2f, .26f, .02f, .01f); cv.drawPath(path, solid(AMB))
        for (sd in floatArrayOf(-1f, 1f)) { circle(cv, sd * .275f, by + .02f, .07f, shaded(rg(sd * .265f, by + .03f, .07f, intArrayOf(0xFF4B4066.toInt(), 0xFF15111F.toInt())))); circle(cv, sd * .275f, by + .02f, .042f, stroke(MINT, .009f, .6f + .4f * bass)) }
        rrect(0f, by + .02f, .38f, .29f, .085f); val scr = Path(path); cv.drawPath(scr, solid(0xFF020105.toInt())); cv.drawPath(scr, stroke(AMB, .007f, .35f))
        cv.save(); cv.clipPath(scr)
        val col = eyeCol(look.eye); ledEyes(cv, 0f, by + .05f, .018f, .075f, col); ledMouth(cv, 0f, by - .078f, .015f, col)
        path.reset(); path.moveTo(-.2f, by + .1f); path.lineTo(-.05f, by + .17f); path.lineTo(0f, by + .17f); path.lineTo(-.15f, by + .1f); path.close(); cv.drawPath(path, solid(Color.WHITE, .06f))
        cv.restore()
        val ab = ant.x; val ax = .13f + sin(ab) * .1f; val ay = by + .23f + cos(ab) * .1f
        seg(cv, .12f, by + .22f, ax, ay, .012f, 0xFF3A3150.toInt())
        cv.save(); cv.rotate((ab + .3f) * 57.3f, ax, ay); oval(cv, ax, ay, .05f, .018f, solid(0xFF4A4062.toInt())); cv.restore(); circle(cv, ax, ay + .01f, .012f, solid(AMB))
        arm2(cv, -.27f, by - .1f, -1f, aL.x, eL.x, .14f, .12f, .036f, 0xFF4A4062.toInt(), "claw")
        arm2(cv, .27f, by - .1f, 1f, aR.x, eR.x, .14f, .12f, .036f, 0xFF4A4062.toInt(), "claw")
    }
    private fun drawSentinel(cv: Canvas) {
        val hy = .42f + bob
        for (sd in floatArrayOf(-1f, 1f)) {
            val f = if (sd < 0) footL else footR; val k = ik(sd * .075f, hy, sd * .1f + f[0], f[1] + .03f, .22f, .22f, sd)
            seg(cv, sd * .075f, hy, k[0], k[1], .065f, 0xFF2B2440.toInt()); seg(cv, k[0], k[1], k[2], k[3], .055f, 0xFF2B2440.toInt()); seg(cv, sd * .075f, hy, k[0], k[1], .016f, Color.argb(31, 205, 180, 255))
            circle(cv, k[0], k[1], .036f, solid(0xFF3B3254.toInt())); circle(cv, k[0], k[1], .012f, solid(AMB))
            rrect(k[2] + sd * .01f, k[3] - .012f, .13f, .04f, .02f); cv.drawPath(path, solid(0xFF1C1729.toInt())); rim(cv, Path(path), .07f)
        }
        rrect(0f, hy + .03f, .21f, .08f, .03f); cv.drawPath(path, solid(0xFF231D33.toInt()))
        path.reset(); path.moveTo(-.1f, hy + .06f); path.lineTo(-.215f, hy + .33f); path.quadTo(0f, hy + .41f, .215f, hy + .33f); path.lineTo(.1f, hy + .06f); path.quadTo(0f, hy + .03f, -.1f, hy + .06f); path.close()
        val torso = Path(path); cv.drawPath(torso, shaded(lg(-.2f, hy + .35f, .2f, hy + .05f, 0xFF4A3F66.toInt(), 0xFF221C33.toInt(), 0xFF0E0B16.toInt()))); rim(cv, torso, .21f)
        seg(cv, 0f, hy + .09f, 0f, hy + .16f, .006f, Color.argb(51, 205, 180, 255))
        val cz = hy + .24f
        circle(cv, 0f, cz, .062f, solid(0xFF15111F.toInt())); circle(cv, 0f, cz, .062f, stroke(0xFF4A4062.toInt(), .01f))
        circle(cv, 0f, cz, .044f, shaded(rg(0f, cz, .044f * (1 + .3f * bass), intArrayOf(Color.WHITE, LAV, VIO))))
        for (i in 0 until 3) { rect.set(-.03f, cz - .03f, .03f, cz + .03f); cv.drawArc(rect, (time * 2 + i * TAU / 3) * 57.3f, 51.6f, false, stroke(Color.WHITE, .008f, .5f)) }
        for (sd in floatArrayOf(-1f, 1f)) { path.reset(); path.addCircle(sd * .23f, hy + .34f, .056f, Path.Direction.CW); cv.drawPath(path, shaded(rg(sd * .225f, hy + .35f, .056f, intArrayOf(0xFF5B4F78.toInt(), 0xFF1E1830.toInt())))); rim(cv, Path(path), .05f) }
        arm2(cv, -.24f, hy + .32f, -1f, aL.x, eL.x, .19f, .18f, .045f, 0xFF2E2742.toInt(), "three")
        arm2(cv, .24f, hy + .32f, 1f, aR.x, eR.x, .19f, .18f, .045f, 0xFF2E2742.toInt(), "three")
        rrect(0f, hy + .41f, .05f, .07f, .02f); cv.drawPath(path, solid(0xFF1C1729.toInt()))
        val hcy = hy + .53f
        cv.save(); cv.rotate(headTilt * 57.3f, 0f, hcy)
        for (sd in floatArrayOf(-1f, 1f)) { path.reset(); path.moveTo(sd * .12f, hcy + .02f); path.lineTo(sd * .19f, hcy + .1f); path.lineTo(sd * .13f, hcy - .03f); path.close(); cv.drawPath(path, solid(0xFF2A2338.toInt())) }
        path.reset(); rect.set(-.145f, hcy - .122f, .145f, hcy + .122f); path.addOval(rect, Path.Direction.CW); val hd = Path(path)
        cv.drawPath(hd, shaded(rg(-.025f, hcy + .03f, .15f, intArrayOf(0xFF53477A.toInt(), 0xFF241D37.toInt(), 0xFF0D0A15.toInt())))); rim(cv, hd, .14f)
        seg(cv, 0f, hcy + .122f, 0f, hcy + .155f, .014f, LAV)
        rrect(0f, hcy, .24f, .1f, .05f); val vis = Path(path); cv.drawPath(vis, solid(0xFF030206.toInt())); cv.drawPath(vis, stroke(AMB, .006f, .4f))
        ledEyes(cv, 0f, hcy + .002f, .0115f, .056f, eyeCol(look.eye))
        for (i in -2..2) seg(cv, i * .018f, hcy - .07f, i * .018f, hcy - .09f, .006f, Color.argb(64, 205, 180, 255))
        cv.restore()
    }
    private fun drawBubble(cv: Canvas) {
        val by = .34f + bob
        run { val k = ik(0f, by - .03f, .03f + footB[0], footB[1] + .05f, .2f, .22f, 1f); seg(cv, 0f, by - .03f, k[0], k[1], .03f, 0xFF1A1526.toInt()); seg(cv, k[0], k[1], k[2], k[3], .026f, 0xFF1A1526.toInt()) }
        for (sd in floatArrayOf(-1f, 1f)) {
            val f = if (sd < 0) footL else footR; val hx = sd * .16f; val k = ik(hx, by - .02f, sd * .34f + f[0], f[1] + .01f, .2f, .25f, sd)
            seg(cv, hx, by - .02f, k[0], k[1], .036f, 0xFF2E2742.toInt()); seg(cv, k[0], k[1], k[2], k[3], .03f, 0xFF2E2742.toInt())
            circle(cv, k[0], k[1], .026f, solid(0xFF4A4062.toInt())); circle(cv, k[0], k[1], .01f, solid(LAV)); circle(cv, k[2], k[3], .02f, solid(0xFF4A4062.toInt()))
        }
        val dy = by + .25f
        circle(cv, 0f, dy, .245f, solid(Color.argb(140, 30, 22, 52)))
        path.reset(); for (i in 0..40) { val a = i / 40f * TAU; val r = .155f + .012f * sin(a * 7 + time * 1.5f) + .008f * sin(a * 11 - time * 2) + .02f * bass; val px = cos(a) * r * 1.05f; val py = dy - .02f + sin(a) * r * .92f; if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }; path.close()
        val brain = Path(path); cv.drawPath(brain, shaded(rg(-.02f, dy + .01f, .17f, intArrayOf(0xFFD9C2FF.toInt(), 0xFF9A6BEA.toInt(), 0xFF4B2389.toInt()))))
        cv.save(); cv.clipPath(brain)
        for (i in 0 until 6) { val a = i * 1.7f + time * .3f; val fl = .5f + .5f * sin(time * (3 + i) + i * 2)
            path.reset(); path.moveTo(cos(a) * .15f, dy + sin(a) * .13f); path.cubicTo(cos(a + 1) * .05f, dy + .05f, cos(a + 2) * .08f, dy - .06f, cos(a + 3) * .15f, dy - .02f + sin(a + 3) * .12f)
            val p = stroke(0xFFFFF0FF.toInt(), .006f, .12f + .3f * fl * (think + .3f)); p.xfermode = addMode; cv.drawPath(path, p); p.xfermode = null }
        cv.restore()
        rrect(0f, dy - .015f, .2f, .16f, .06f); val plate = Path(path); cv.drawPath(plate, solid(Color.argb(184, 10, 4, 24))); cv.drawPath(plate, stroke(LAV, .005f, .35f))
        val col = eyeCol(look.eye); ledEyes(cv, 0f, dy + .015f, .0155f, .056f, col); ledMouth(cv, 0f, dy - .085f, .011f, col)
        circle(cv, 0f, dy, .245f, shaded(rg(0f, dy, .25f, intArrayOf(Color.argb(0, 205, 180, 255), Color.argb(0, 205, 180, 255), Color.argb(41, 205, 180, 255)), floatArrayOf(0f, .48f, 1f))))
        circle(cv, 0f, dy, .245f, stroke(LAV, .009f, .45f))
        rect.set(-.21f, dy - .21f, .21f, dy + .21f); cv.drawArc(rect, 1.9f * 57.3f, .8f * 57.3f, false, stroke(Color.WHITE, .02f, .22f))
        circle(cv, -.12f, dy + .14f, .018f, solid(Color.WHITE, .35f)); circle(cv, 0f, dy + .245f, .02f, solid(0xFF4A4062.toInt()))
        path.reset(); rect.set(-.22f, by - .065f, .22f, by + .065f); path.addOval(rect, Path.Direction.CW); val ring = Path(path)
        cv.drawPath(ring, shaded(lg(0f, by + .06f, 0f, by - .06f, 0xFF5A4E7A.toInt(), 0xFF2A2338.toInt(), 0xFF120F1B.toInt()))); rim(cv, ring, .22f)
        for (i in 0 until 7) { val a = PI.toFloat() + (i + .5f) / 7 * PI.toFloat(); val on = (floor(time * 8).toInt() % 7 == i) || bass > .5f; circle(cv, cos(a) * .19f, by + sin(a) * .05f, .011f, solid(if (on) AMB else 0xFF3A3150.toInt())) }
        arm2(cv, -.2f, by + .01f, -1f, aL.x, eL.x, .1f, .09f, .026f, 0xFF3A3150.toInt(), "claw")
        arm2(cv, .2f, by + .01f, 1f, aR.x, eR.x, .1f, .09f, .026f, 0xFF3A3150.toInt(), "claw")
    }
    private fun drawTink(cv: Canvas) {
        val by = .42f + bob
        for (sd in floatArrayOf(-1f, 1f)) {
            val f = if (sd < 0) footL else footR; val x0 = sd * .085f; val y0 = by - .16f; val x1 = sd * .1f + f[0]; val y1 = f[1] + .03f
            path.reset(); path.moveTo(x0, y0); for (i in 1 until 9) { val t = i / 9f; path.lineTo(mix(x0, x1, t) + (if (i % 2 == 1) .035f else -.035f), mix(y0, y1, t)) }; path.lineTo(x1, y1); cv.drawPath(path, stroke(0xFF9C92B8.toInt(), .013f))
            path.reset(); rect.set(x1 + sd * .01f - .065f, y1 - .012f - .028f, x1 + sd * .01f + .065f, y1 - .012f + .028f); path.addOval(rect, Path.Direction.CW); val ft = Path(path); cv.drawPath(ft, solid(0xFF231D33.toInt())); rim(cv, ft, .06f)
            seg(cv, x1 - .04f + sd * .01f, y1 - .035f, x1 + .04f + sd * .01f, y1 - .035f, .006f, aCol(AMB, .7f))
        }
        for (sd in floatArrayOf(-1f, 1f)) {
            val ab = ant.x * (if (sd < 0) 1f else 1.3f) + sd * .25f; val bx = sd * .07f; val bY = by + .19f; val tx = bx + sin(ab) * .2f; val ty = bY + cos(ab) * .17f
            path.reset(); path.moveTo(bx, bY); path.quadTo(bx + sd * .02f, bY + .12f, tx, ty); cv.drawPath(path, stroke(0xFF4A4062.toInt(), .01f)); circle(cv, tx, ty, .026f, solid(if (sd < 0) LAV else AMB))
        }
        path.reset(); rect.set(-.205f, by - .225f, .205f, by + .225f); path.addOval(rect, Path.Direction.CW); val body = Path(path)
        cv.drawPath(body, shaded(rg(-.035f, by + .05f, .24f, intArrayOf(0xFF53477A.toInt(), 0xFF241D37.toInt(), 0xFF0B0912.toInt()), floatArrayOf(0f, .5f, 1f)))); rim(cv, body, .2f)
        for (sd in floatArrayOf(-1f, 1f)) for (i in 0 until 3) seg(cv, sd * .15f, by - .07f - i * .025f, sd * .175f, by - .07f - i * .025f, .007f, Color.argb(51, 205, 180, 255))
        val ly = by + .02f
        circle(cv, 0f, ly, .135f, shaded(lg(-.13f, ly + .13f, .13f, ly - .13f, 0xFF6C6090.toInt(), 0xFF2A2338.toInt(), 0xFF0D0A14.toInt())))
        val nseg = 16; val lit = if (expr == "pct") (battery * nseg).roundToInt() else -1
        for (i in 0 until nseg) { val a = PI.toFloat() / 2 - i.toFloat() / nseg * TAU; val on = if (lit >= 0) i < lit else ((i + floor(time * 6).toInt()) % nseg < 3 || bass > .55f)
            rect.set(-.122f, ly - .122f, .122f, ly + .122f); cv.drawArc(rect, (a - .14f) * 57.3f, .11f * 57.3f, false, stroke(if (on) (if (lit >= 0) (if (battery < .2f) RED else GREEN) else MINT) else Color.argb(18, 255, 255, 255), .012f)) }
        circle(cv, 0f, ly, .104f, solid(0xFF020105.toInt()))
        ledEyes(cv, 0f, ly, .021f, 0f, eyeCol(look.eye), 1)
        cv.save(); cv.rotate(-28.6f, -.04f, ly + .055f); oval(cv, -.04f, ly + .055f, .045f, .02f, solid(Color.WHITE, .12f)); cv.restore()
        arm2(cv, -.19f, by - .02f, -1f, aL.x, eL.x, .07f, .065f, .036f, 0xFF3A3150.toInt(), "mitt")
        arm2(cv, .19f, by - .02f, 1f, aR.x, eR.x, .07f, .065f, .036f, 0xFF3A3150.toInt(), "mitt")
    }

    // ---------------------------------------------------------------- screen-space extras
    private fun drawCable(cv: Canvas) {
        if (!charging) return
        val portY = when (look.loco) { "hover" -> .5f + hov - .25f; "wheel" -> .45f; "walk" -> .45f; "tripod" -> .32f; else -> .3f }
        val p = botScreen(portY, FloatArray(2)); val k = sm((time - plugT) / .6f)
        val x0 = p[0] + U * .9f; val y0 = H + 20; val ex = mix(x0, p[0] + U * .18f, k); val ey = mix(y0, p[1], k)
        path.reset(); path.moveTo(x0, y0); path.cubicTo(x0, mix(y0, GY, .6f), ex + U * .45f, ey + U * .25f, ex, ey)
        cv.drawPath(path, stroke(0xFF1C1729.toInt(), U * .028f)); cv.drawPath(path, stroke(MINT, U * .008f, .35f))
        cv.drawRect(ex - U * .03f, ey - U * .02f, ex + U * .02f, ey + U * .02f, solid(0xFF2A2338.toInt()))
        for (i in 0 until 4) { val t = (time * .9f + i / 4f) % 1f; val mt = 1 - t
            val bx = mt * mt * mt * x0 + 3 * mt * mt * t * x0 + 3 * mt * t * t * (ex + U * .45f) + t * t * t * ex; val byy = mt * mt * mt * y0 + 3 * mt * mt * t * mix(y0, GY, .6f) + 3 * mt * t * t * (ey + U * .25f) + t * t * t * ey
            circle(cv, bx, byy, U * .016f, solid(0xFFB9FFE9.toInt(), .9f)) }
    }
    private fun drawScan(cv: Canvas) {
        val a0 = act; if (scanX.isNaN() && a0?.n != "scan") return
        val e = botScreen(look.eyeY, FloatArray(2)); val tx: Float; val ty: Float
        if (!scanX.isNaN()) { tx = scanX; ty = scanY } else { tx = e[0] + sin(a0!!.t * 2.2f) * U * 1.4f; ty = GY - U * .05f }
        val a = atan2(ty - e[1], tx - e[0]); val d = hypot(tx - e[0], ty - e[1]); val w = U * .14f
        cv.save(); cv.translate(e[0], e[1]); cv.rotate(a * 57.3f)
        path.reset(); path.moveTo(0f, -3f); path.lineTo(d, -w); path.lineTo(d, w); path.lineTo(0f, 3f); path.close()
        val p = shaded(lg(0f, 0f, d, 0f, aCol(MINT, .3f), aCol(MINT, .06f))); p.xfermode = addMode; cv.drawPath(path, p); p.xfermode = null
        val sw = sin(time * 6) * w; cv.drawLine(d, sw, d * .2f, sw * .2f, stroke(0xFFB9FFE9.toInt(), 1.5f, .6f))
        cv.restore()
        if (!scanX.isNaN()) { val dp = stroke(MINT, 2f, .8f); dp.pathEffect = DashPathEffect(floatArrayOf(8f, 7f), time * 30); cv.drawCircle(tx, ty, U * .13f, dp); dp.pathEffect = null
            if (time - fT0 - .9f > 1.4f) { textP.textSize = U * .065f; textP.color = MINT; textP.textAlign = Paint.Align.CENTER; cv.drawText("FINGERPRINT ✓  OWNER", tx, ty - U * .19f, textP) } }
    }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private fun drawHolo(cv: Canvas) {
        val n = notif ?: return; if (n.quiet) return; val u = time - n.t0; if (u < .5f) return
        val a = sm((u - .5f) / .35f) * (1 - sm((u - 4.5f) / .5f)) * (if (rnd() < .04f) .5f else 1f); if (a <= 0) return
        val e = botScreen(look.eyeY, FloatArray(2)); val pw = min(W * .86f, U * 2.3f); val ph = U * .5f; val px = clamp(e[0], pw / 2 + 12, W - pw / 2 - 12); val py = max(H * .12f, e[1] - U * (if (look.head < .6f) .95f else .75f)) - ph
        path.reset(); path.moveTo(e[0] - 3, e[1]); path.lineTo(px - pw / 2, py + ph); path.lineTo(px + pw / 2, py + ph); path.lineTo(e[0] + 3, e[1]); path.close()
        val beam = shaded(lg(0f, e[1], 0f, py + ph, aCol(MINT, .3f * a), aCol(LAV, .04f * a))); beam.xfermode = addMode; cv.drawPath(beam.let { path }, beam); beam.xfermode = null
        rect.set(px - pw / 2, py, px + pw / 2, py + ph); cv.drawRoundRect(rect, 16f * U / 200, 16f * U / 200, solid(Color.argb((184 * a).roundToInt(), 30, 25, 43)))
        cv.drawRoundRect(rect, 16f * U / 200, 16f * U / 200, stroke(LAV, 1.2f * U / 200, .55f * a))
        val fs = max(11f * U / 200, U * .055f); val x0 = px - pw / 2 + fs * 1.2f
        textP.textAlign = Paint.Align.LEFT; textP.alpha = (255 * a).roundToInt()
        textP.textSize = fs * 1.6f; textP.color = Color.WHITE; textP.alpha = (255 * a).roundToInt(); cv.drawText(n.icon, x0, py + ph / 2 + fs * .55f, textP)
        val tx = x0 + fs * 2.4f
        textP.textSize = fs * .75f; textP.color = aCol(AMB, a); cv.drawText(n.app.uppercase(), tx, py + ph * .28f, textP)
        textP.textSize = fs; textP.color = aCol(Color.WHITE, a); cv.drawText(n.who, tx, py + ph * .55f, textP)
        textP.color = aCol(0xFF9E96B0.toInt(), a); cv.drawText(n.msg.take(max(0, ((u - 1) * 28).toInt())), tx, py + ph * .82f, textP)
    }
    private fun drawParts(cv: Canvas) {
        for (p in parts) {
            val a = sm(p.life / .15f) * (1 - sm((p.life - p.max + .4f) / .4f)); val z = p.s * U / 260
            cv.save(); cv.translate(p.x, p.y); cv.rotate(p.r * 57.3f)
            when (p.k) {
                HEART -> { path.reset(); path.moveTo(0f, z * .5f); path.cubicTo(-z * 1.4f, -z * .4f, -z * .6f, -z * 1.4f, 0f, -z * .6f); path.cubicTo(z * .6f, -z * 1.4f, z * 1.4f, -z * .4f, 0f, z * .5f); cv.drawPath(path, solid(PINK, a)) }
                ZZ, NOTE, BANG -> { textP.textAlign = Paint.Align.CENTER; textP.textSize = z * (if (p.k == BANG) 4f else 2.4f); textP.color = aCol(if (p.k == ZZ) LAV else AMB, a); cv.drawText(if (p.k == ZZ) "Z" else if (p.k == BANG) "!" else if (p.max > 1.4f) "♪" else "♫", 0f, 0f, textP) }
                STEAM -> { val r = z * (1 + p.life * 2.5f); circle(cv, 0f, 0f, r, shaded(rg(0f, 0f, r, intArrayOf(Color.argb((89 * a).roundToInt(), 230, 225, 240), Color.argb(0, 230, 225, 240))))) }
                BOLT -> { path.reset(); path.moveTo(-z * .3f, -z); path.lineTo(z * .2f, -z * .1f); path.lineTo(-z * .2f, 0f); path.lineTo(z * .3f, z); cv.drawPath(path, stroke(MINT, 2f * U / 200, a)) }
                DUST -> circle(cv, 0f, 0f, z * .5f, solid(Color.argb(89, 205, 180, 255), a))
                else -> cv.drawLine(0f, 0f, -p.vx * .04f, -p.vy * .04f, stroke(AMB, 2f * U / 200, a))
            }
            cv.restore()
        }
    }

    // ---------------------------------------------------------------- GL plumbing
    private var prog = 0; private var vbo = 0; private var tex = 0; private var bmp: Bitmap? = null; private var cvs: Canvas? = null; private var uTex = 0
    private var sw = 1; private var sh = 1
    override fun onSurfaceCreated() {
        prog = Gl.program(VS, FS); uTex = GLES20.glGetUniformLocation(prog, "uTex")
        val ids = IntArray(1); GLES20.glGenBuffers(1, ids, 0); vbo = ids[0]
        val q = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f); val fb = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().put(q); fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo); GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 32, fb, GLES20.GL_STATIC_DRAW); tex = 0
    }
    override fun onSurfaceChanged(width: Int, height: Int) {
        sw = max(1, width); sh = max(1, height)
        val k = min(1f, 720f / min(sw, sh)); bw = max(8, (sw * k).roundToInt()); bh = max(8, (sh * k).roundToInt())
        W = bw.toFloat(); H = bh.toFloat(); U = min(W * .5f, H * .3f); GY = H * .7f; CX = W / 2
    }
    override fun release() { if (prog != 0) GLES20.glDeleteProgram(prog); if (vbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0); if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0); prog = 0; vbo = 0; tex = 0; bmp?.recycle(); bmp = null; cvs = null }
    override fun draw(input: SceneInput) {
        var b = bmp
        if (b == null || b.width != bw || b.height != bh) { b?.recycle(); b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888); bmp = b; cvs = Canvas(b); if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0); tex = 0 }
        val cv = cvs!!
        cv.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
        drawCable(cv); drawScan(cv)
        // the robot (robot units, y up, origin at its feet)
        cv.save(); cv.translate(CX + x * U, GY - air * U); cv.scale(U, -U)
        val piv = if (held) grabDy else when (look.loco) { "hover" -> .5f + hov; "wheel" -> .13f; "tripod" -> .3f; else -> 0f }
        cv.rotate(tilt.x * 57.3f, 0f, piv)
        val s = sq.x; cv.scale(1 + (1 - s) * .7f, s)
        when (look.key) { "orbi" -> drawOrbi(cv); "pixel" -> drawPixel(cv); "sentinel" -> drawSentinel(cv); "bubble" -> drawBubble(cv); else -> drawTink(cv) }
        cv.restore()
        if (glitch > .05f) { val p = botScreen(.5f, FloatArray(2)); for (i in 0 until 4) {
            val y = (p[1] - U * .6f + rnd() * U * 1.1f).toInt().coerceIn(0, bh - 1); val hh = (4 + rnd() * 16).toInt().coerceAtMost(bh - y); val dx = ((rnd() - .5f) * 40 * glitch).toInt(); if (hh <= 0) continue
            val row = IntArray(bw * hh); b.getPixels(row, 0, bw, 0, y, bw, hh); val out = IntArray(bw * hh)
            for (r in 0 until hh) for (xx in 0 until bw) { val sx = xx - dx; out[r * bw + xx] = if (sx in 0 until bw) row[r * bw + sx] else Color.BLACK }; b.setPixels(out, 0, bw, 0, y, bw, hh) } }
        drawHolo(cv); drawParts(cv)
        if (tex == 0) { val t = IntArray(1); GLES20.glGenTextures(1, t, 0); tex = t[0]; GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0) } else { GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex); GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, b) }
        GLES20.glUseProgram(prog); GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex); GLES20.glUniform1i(uTex, 0)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo); GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4); GLES20.glDisableVertexAttribArray(0); GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    companion object {
        private const val TAU = (PI * 2).toFloat()
        private const val LAV = 0xFFCDB4FF.toInt(); private const val AMB = 0xFFFFB36B.toInt(); private const val VIO = 0xFF9D4EDD.toInt(); private const val MINT = 0xFF2DD4BF.toInt()
        private const val PINK = 0xFFFF6FA8.toInt(); private const val RED = 0xFFFF5A5A.toInt(); private const val GREEN = 0xFF4ADE80.toInt()
        private const val HEART = 0; private const val SPARK = 1; private const val BANG = 2; private const val ZZ = 3; private const val NOTE = 4; private const val STEAM = 5; private const val BOLT = 6; private const val DUST = 7
        private val G: Map<Char, Array<String>> = mapOf(
            '0' to arrayOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"), '1' to arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
            '2' to arrayOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"), '3' to arrayOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
            '4' to arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"), '5' to arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
            '6' to arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"), '7' to arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
            '8' to arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"), '9' to arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
            '!' to arrayOf("00100", "01110", "01110", "00100", "00100", "00000", "00100"), '?' to arrayOf("01110", "10001", "00001", "00110", "00100", "00000", "00100"),
            'n' to arrayOf("00110", "00111", "00101", "00100", "01100", "11100", "11000"), 'z' to arrayOf("00000", "11111", "00010", "00100", "01000", "11111", "00000"),
            '*' to arrayOf("00100", "00100", "11111", "01110", "01110", "11011", "10001"),
        )
        private fun rnd() = Random.nextFloat()
        private fun clamp(x: Float, a: Float, b: Float) = if (x < a) a else if (x > b) b else x
        private fun sm(x: Float): Float { val c = clamp(x, 0f, 1f); return c * c * (3 - 2 * c) }
        private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
        private fun win(t: Float, a: Float, b: Float, c: Float, d: Float) = sm((t - a) / max(1e-4f, b - a)) * (1 - sm((t - c) / max(1e-4f, d - c)))
        private const val VS = "attribute vec2 aPos; varying vec2 vUv; void main(){ vUv = vec2(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5); gl_Position = vec4(aPos, 0.0, 1.0); }"
        private const val FS = "precision mediump float; varying vec2 vUv; uniform sampler2D uTex; void main(){ vec3 c = texture2D(uTex, vUv).rgb; float m = max(c.r, max(c.g, c.b)); gl_FragColor = vec4(c * smoothstep(0.012, 0.03, m), 1.0); }"
    }
}
