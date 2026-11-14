package app.pulsify.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** Bolt's paint job: body, light, dark, and its screen colour. */
data class BoltCoat(val name: String, val body: Int, val light: Int, val dark: Int, val screen: Int, val accent: Int)

val BOLT_COATS = listOf(
    BoltCoat("Gunmetal", 0xFF5D667F.toInt(), 0xFF8A93AE.toInt(), 0xFF262A37.toInt(), 0xFF2DD4BF.toInt(), 0xFFFFB36B.toInt()),
    BoltCoat("Retro Cream", 0xFFE9DEC8.toInt(), 0xFFFFF8EA.toInt(), 0xFF9C8E74.toInt(), 0xFF7CFFB0.toInt(), 0xFFFF6B5B.toInt()),
    BoltCoat("Candy", 0xFFFF8FB8.toInt(), 0xFFFFD1E2.toInt(), 0xFFB34A75.toInt(), 0xFFFFE45C.toInt(), 0xFF7FE3FF.toInt()),
    BoltCoat("Midnight", 0xFF2B2F52.toInt(), 0xFF51578F.toInt(), 0xFF101226.toInt(), 0xFFCDB4FF.toInt(), 0xFF2DD4BF.toInt()),
    BoltCoat("Gold", 0xFFE0B04A.toInt(), 0xFFFFE6A3.toInt(), 0xFF8C6418.toInt(), 0xFF59B8FF.toInt(), 0xFFFF5FA2.toInt()),
)

/**
 * Bolt, a pocket robot with a TV for a head and one ball for a wheel. Runs on your battery.
 * Tap: the face changes (fast taps overheat it). Hold near it: it laser-scans your finger.
 */
class BoltScene(context: Context) : CanvasCreature(context) {

    private enum class Face { EYES, HAPPY, LOVE, SHOCK, GLITCH, WINK, SLEEPY }
    private enum class Act { NONE, CLOCK, WAVE, DANCE, POLISH, GLITCH, TUNE, RADAR, JUMP, SCAN, OVERHEAT }

    private var coat = BOLT_COATS[0]
    private var u = 100f
    private var x = 0f; private var vx = 0f; private var tx = 0f; private var homeX = 0f
    private var tilt = 0f; private var wheelRot = 0f
    private var face = Face.EYES; private var faceT = 0f
    private var act = Act.NONE; private var actT = 0f; private var actLen = 0f
    private var nextIdle = 3f; private var nextRoam = 2f
    private val lastActs = ArrayDeque<Act>()
    private var heat = 0f; private var lastTap = -9f
    private var scanX = 0f; private var scanY = 0f
    private var jumpY = 0f; private var jumpV = 0f
    private var msg = ""; private var msgT = 0f
    private var fingerDown = false
    private var lastBeat = -9f; private var beatK = 0f
    private val clockFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
    private var clockStr = ""; private var clockAt = -99f

    override val busy: Boolean get() = act != Act.NONE || sinceTouch < 2f || abs(vx) > u * 0.3f

    override fun layout() {
        u = unit * 0.15f
        if (homeX == 0f) { homeX = W * 0.5f; x = homeX; tx = homeX }
    }

    override fun update(dt: Float, input: SceneInput) {
        coat = BOLT_COATS[input.coat.coerceIn(0, BOLT_COATS.size - 1)]
        super.update(dt, input)
    }

    private val cy: Float get() = floorY - u * 1.35f - jumpY
    private fun near(px: Float, py: Float) = abs(px - x) < u * 1.2f && py > cy - u * 1.7f && py < floorY + u * 0.2f

    override fun touchDown(nx: Float, ny: Float): Boolean {
        sinceTouch = 0f; fingerDown = true
        scanX = xp(nx); scanY = yp(ny)
        return near(scanX, scanY)
    }
    override fun touchMove(nx: Float, ny: Float): Boolean { scanX = xp(nx); scanY = yp(ny); sinceTouch = 0f; return false }
    override fun touchUp(): Boolean { fingerDown = false; if (act == Act.SCAN) act = Act.NONE; return false }

    override fun tapAt(nx: Float, ny: Float): Boolean {
        val px = xp(nx); val py = yp(ny); sinceTouch = 0f
        if (near(px, py)) {
            heat = if (time - lastTap < 0.5f) heat + 1f else 1f
            lastTap = time
            if (heat >= 4f) { start(Act.OVERHEAT); heat = 0f }
            else { face = listOf(Face.HAPPY, Face.SHOCK, Face.LOVE, Face.GLITCH, Face.WINK).filter { it != face }.random(); faceT = 2.2f; beep() }
            listener?.onBondXp(1)
            return true
        }
        // Tap elsewhere: roll over to have a look.
        tx = px.coerceIn(u * 1.2f, W - u * 1.2f)
        return false
    }

    override fun longPressAt(nx: Float, ny: Float): Boolean {
        scanX = xp(nx); scanY = yp(ny)
        start(Act.SCAN)
        listener?.onBondXp(2)
        return true
    }

    override fun swiped(dir: Float, fast: Boolean) { perform(if (fast) "glitch" else listOf("wave", "radar", "faces", "jump").random()) }

    override fun greet() { start(Act.WAVE) }

    override fun perform(trick: String): Boolean {
        val a = when (trick) {
            "clock" -> Act.CLOCK; "wave" -> Act.WAVE; "dance" -> Act.DANCE; "polish" -> Act.POLISH; "glitch" -> Act.GLITCH
            "tune" -> Act.TUNE; "radar" -> Act.RADAR; "jump" -> Act.JUMP
            "scan" -> { scanX = (x + u * 3f).coerceIn(u, W - u); scanY = cy - u * 0.5f; Act.SCAN }
            "faces" -> { face = Face.values().filter { it != face && it != Face.SLEEPY }.random(); faceT = 2.5f; return true }
            else -> return false
        }
        start(a); return true
    }

    private fun beep() { msg = listOf("beep!", "boop", "bzzt", "beep boop").random(); msgT = 0.9f }

    private fun start(a: Act) {
        act = a; actT = 0f
        actLen = when (a) {
            Act.CLOCK -> 3.5f; Act.WAVE -> 2.6f; Act.DANCE -> 5f; Act.POLISH -> 3f; Act.GLITCH -> 3.4f; Act.TUNE -> 3f
            Act.RADAR -> 3.2f; Act.JUMP -> 1.6f; Act.SCAN -> 4.5f; Act.OVERHEAT -> 2.6f; Act.NONE -> 0f
        }
        when (a) {
            Act.JUMP -> { jumpV = u * 9f }
            Act.CLOCK -> { msg = "beep! it's time"; msgT = 1.3f }
            Act.OVERHEAT -> { msg = "OVERHEATING"; msgT = 1.5f }
            Act.WAVE -> { msg = "hi human!"; msgT = 1.4f }
            else -> {}
        }
    }

    override fun step(dt: Float) {
        actT += dt; msgT -= dt; faceT -= dt
        val slow = if (lowBattery) 0.35f else 1f
        val asleep = night && act == Act.NONE && sinceTouch > 8f && !musicOn
        sleeping = asleep

        if (musicOn && audio.beat > 0.95f && time - lastBeat > 0.25f) { lastBeat = time; beatK = 1f }
        beatK = max(0f, beatK - dt * 3f)

        // Rolling: a damped spring toward the target with a little lean.
        if (!asleep && act == Act.NONE && !charging) {
            nextRoam -= dt
            if (nextRoam < 0f) { tx = (homeX + (Random.nextFloat() - 0.5f) * W * 0.55f).coerceIn(u * 1.3f, W - u * 1.3f); nextRoam = 3f + Random.nextFloat() * 4f }
        }
        if (charging) tx = max(W * 0.55f, u * 3f)
        if (act == Act.SCAN) tx = (scanX - sign(scanX - x + 0.01f) * u * 2.6f).coerceIn(u * 1.2f, W - u * 1.2f)
        val k = 6f * slow
        vx += ((tx - x) * k * k * 0.12f - vx * k * 0.9f) * dt
        x += vx * dt * slow
        x = x.coerceIn(u, W - u)
        wheelRot = -x / (u * 0.32f)
        tilt += ((-vx / (u * 12f)).coerceIn(-0.25f, 0.25f) - tilt) * min(1f, dt * 8f)

        // Jump physics.
        if (jumpY > 0f || jumpV != 0f) {
            jumpV -= u * 30f * dt; jumpY += jumpV * dt
            if (jumpY <= 0f) { jumpY = 0f; jumpV = if (abs(jumpV) > u * 3f) -jumpV * 0.3f else 0f }
        }

        if (act != Act.NONE && actT > actLen) act = Act.NONE

        if (act == Act.NONE && !asleep && !fingerDown && sinceTouch > 3f) {
            nextIdle -= dt
            if (nextIdle <= 0f) {
                val pool = when {
                    musicOn -> mutableListOf(Act.DANCE, Act.JUMP, Act.WAVE)
                    lowBattery -> mutableListOf(Act.GLITCH, Act.CLOCK, Act.TUNE)
                    else -> mutableListOf(Act.CLOCK, Act.WAVE, Act.DANCE, Act.POLISH, Act.GLITCH, Act.TUNE, Act.RADAR, Act.JUMP)
                }
                pool.removeAll(lastActs)
                val pick = pool.random()
                lastActs.addLast(pick); if (lastActs.size > 3) lastActs.removeFirst()
                start(pick)
                nextIdle = TRICK_GAP_MIN + Random.nextFloat() * TRICK_GAP_RANGE
            }
        }
        if (time - clockAt > 10f) { clockAt = time; clockStr = clockFmt.format(java.util.Date()) }
    }
    private var sleeping = false

    override fun paint(c: Canvas) {
        val floor = floorY
        val col = coat
        val sc = col.screen
        var armL = 0.3f + sin(time * 2f) * 0.15f; var armR = -0.3f - sin(time * 2f + 1f) * 0.15f
        var bob = 0f
        var t = tilt
        val dance = act == Act.DANCE || (musicOn && act == Act.NONE)
        if (dance) {
            val step = floor(time * 2f).toInt() and 3
            armL = floatArrayOf(1.4f, 0.2f, -1.2f, 0.2f)[step]; armR = floatArrayOf(-0.2f, -1.4f, 0.2f, 1.2f)[step]
            bob = -abs(sin(time * PI.toFloat() * 2f)) * u * 0.12f - beatK * u * 0.1f
            t += sin(time * PI.toFloat() * 2f) * 0.12f
        }
        if (act == Act.WAVE) { armR = -2.2f + sin(actT * 14f) * 0.4f }
        if (act == Act.POLISH) { armR = -1.9f + sin(actT * 10f) * 0.35f }
        if (act == Act.TUNE) { armL = -1.6f }
        if (act == Act.OVERHEAT) t += sin(actT * 50f) * 0.04f
        if (sleeping) t = 0.08f
        val cy = floor - u * 1.35f + bob - jumpY

        // Charging: the cable goes to a socket at the left edge, pulses run along it.
        if (charging) {
            val sx = u * 0.6f; val sy = floor - u * 0.9f
            ink.color(0xFF1B1F2B.toInt()); ink.r.set(sx - u * 0.25f, sy - u * 0.35f, sx + u * 0.25f, sy + u * 0.35f); c.drawRoundRect(ink.r, u * 0.1f, u * 0.1f, ink.p)
            c.drawRect(sx - u * 0.1f, sy - u * 0.15f, sx - u * 0.05f, sy - u * 0.03f, ink.color(0xFF05060A.toInt()))
            c.drawRect(sx + u * 0.05f, sy - u * 0.15f, sx + u * 0.1f, sy - u * 0.03f, ink.p)
            ink.path.reset(); ink.path.moveTo(sx + u * 0.1f, sy)
            ink.path.cubicTo(sx + u * 1.2f, floor + u * 0.1f, x - u * 1.6f, floor + u * 0.1f, x - u * 0.45f, cy + u * 0.45f)
            c.drawPath(ink.path, ink.stroke(0xFF2B2F3D.toInt(), u * 0.08f))
            for (i in 0 until 3) {
                val k = (time * 0.7f + i / 3f) % 1f
                val bx = lerpF(sx, x - u * 0.45f, k); val by = lerpF(sy, cy + u * 0.45f, k) + sin(k * PI.toFloat()) * u * 0.9f
                ink.glow(c, bx, by, u * 0.2f, u * 0.2f, 0xFF7CFFB0.toInt(), 0.6f); ink.ell(c, bx, by, u * 0.06f, u * 0.06f, 0xFF7CFFB0.toInt())
            }
        }
        // Laser scan of your finger.
        if (act == Act.SCAN) {
            val sweep = (actT * 1.2f) % 1f
            val ex = x + u * 0.3f * sign(scanX - x + 0.01f); val ey = cy - u * 0.55f
            for (i in 0 until 6) {
                val yy = scanY - u * 0.5f + i * u * 0.2f
                ink.line(c, ex, ey, scanX + (if (scanX > x) -u * 0.25f else u * 0.25f), yy, 0xFFFF506E.toInt(), 1.6f, 0.85f)
            }
            ink.color(0xFFFF506E.toInt(), 0.25f); c.drawRect(scanX - u * 0.35f, scanY - u * 0.6f + sweep * u * 1.2f, scanX + u * 0.35f, scanY - u * 0.48f + sweep * u * 1.2f, ink.p)
            ink.glow(c, scanX, scanY, u * 0.6f, u * 0.6f, 0xFFFF506E.toInt(), 0.25f)
        }

        ink.ell(c, x, floor, u * 0.8f * (1f - jumpY / (u * 4f)).coerceIn(0.4f, 1f), u * 0.12f, Color.BLACK, 0.45f)
        c.save(); c.translate(x, cy); c.rotate(t * 57.29578f)
        if (act == Act.OVERHEAT) c.translate(sin(actT * 60f) * u * 0.04f, 0f)
        // Wheel.
        val wr = u * 0.32f
        ink.glossy(c, 0f, u * 1.0f, wr, wr, 0xFF2A2F3D.toInt(), 0xFF4C5468.toInt(), 0xFF11141C.toInt())
        for (i in 0 until 6) {
            val a = wheelRot + i * 2f * PI.toFloat() / 6f
            ink.line(c, cos(a) * wr * 0.4f, u + sin(a) * wr * 0.4f, cos(a) * wr * 0.9f, u + sin(a) * wr * 0.9f, Color.WHITE, 2f, 0.15f)
        }
        // Arms.
        for ((s, a) in listOf(-1f to armL, 1f to armR)) {
            c.save(); c.translate(s * u * 0.55f, u * 0.3f); c.rotate((s * 0.4f + a * s * -1f) * 57.29578f)
            ink.path.reset(); ink.path.moveTo(0f, 0f); ink.path.lineTo(s * u * 0.35f, u * 0.35f); ink.path.lineTo(s * u * 0.45f, u * 0.7f)
            c.drawPath(ink.path, ink.stroke(0xFF596079.toInt(), u * 0.09f))
            ink.arc(c, s * u * 0.45f, u * 0.78f, u * 0.1f, 0.3f, PI.toFloat() - 0.3f, col.accent, u * 0.06f)
            if (act == Act.POLISH && s > 0) ink.ell(c, s * u * 0.45f, u * 0.8f, u * 0.13f, u * 0.09f, Color.WHITE, 0.85f)
            c.restore()
        }
        // Body.
        ink.p.shader = LinearGradient(-u * 0.55f, 0f, u * 0.55f, 0f, intArrayOf(Ink.darken(col.body, 0.3f), col.body, col.dark), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        ink.p.color = Color.WHITE; ink.p.alpha = 255
        ink.r.set(-u * 0.55f, u * 0.05f, u * 0.55f, u * 0.9f); c.drawRoundRect(ink.r, u * 0.26f, u * 0.26f, ink.p); ink.p.shader = null
        c.drawRoundRect(ink.r, u * 0.26f, u * 0.26f, ink.stroke(col.accent, 1.2f, 0.4f))
        // Chest battery: your real level.
        val lvl = battery
        ink.color(0xFF0B0D14.toInt()); ink.r.set(-u * 0.32f, u * 0.28f, u * 0.32f, u * 0.62f); c.drawRoundRect(ink.r, u * 0.08f, u * 0.08f, ink.p)
        for (i in 0 until 4) {
            val on = (i + 1) / 4f <= lvl + 0.12f
            val bc = if (!on) 0xFF1B2030.toInt() else if (charging) 0xFF7CFFB0.toInt() else if (lowBattery) 0xFFFF5B5B.toInt() else sc
            val flick = if (charging && i == (time * 3f).toInt() % 4) 0.5f + 0.5f * sin(time * 20f) else 1f
            if (on) ink.glow(c, -u * 0.21f + i * u * 0.135f, u * 0.45f, u * 0.1f, u * 0.16f, bc, 0.5f * flick)
            c.drawRect(-u * 0.26f + i * u * 0.135f, u * 0.33f, -u * 0.16f + i * u * 0.135f, u * 0.57f, ink.color(bc, flick))
        }
        // Neck and head.
        c.drawRect(-u * 0.12f, -u * 0.05f, u * 0.12f, u * 0.09f, ink.color(0xFF2A2F3D.toInt()))
        val hy = -u * 1.15f
        ink.p.shader = LinearGradient(0f, hy, 0f, hy + u * 1.15f, col.light, col.dark, Shader.TileMode.CLAMP); ink.p.color = Color.WHITE; ink.p.alpha = 255
        ink.r.set(-u * 0.85f, hy, u * 0.85f, hy + u * 1.15f); c.drawRoundRect(ink.r, u * 0.32f, u * 0.32f, ink.p); ink.p.shader = null
        c.drawRoundRect(ink.r, u * 0.32f, u * 0.32f, ink.stroke(col.accent, 1.4f, 0.5f))
        // Antenna (spins while tuning).
        val antW = if (act == Act.TUNE) sin(actT * 20f) * u * 0.2f else sin(time * 3f) * u * 0.08f
        ink.line(c, 0f, hy, antW, hy - u * 0.38f, 0xFF596079.toInt(), u * 0.05f)
        val pulse = 0.6f + 0.4f * sin(time * 4f) + beatK
        ink.glow(c, antW, hy - u * 0.42f, u * 0.3f, u * 0.3f, col.accent, 0.45f * pulse)
        ink.ell(c, antW, hy - u * 0.42f, u * 0.09f, u * 0.09f, col.accent)
        if (act == Act.TUNE || act == Act.RADAR) for (i in 0 until 3) {
            val k = (actT * 1.5f + i / 3f) % 1f
            ink.arc(c, antW, hy - u * 0.42f, u * (0.2f + k * 0.9f), -2.4f, -0.74f, col.accent, u * 0.04f, 1f - k)
        }

        // Screen.
        val sx0 = -u * 0.7f; val sy0 = hy + u * 0.14f; val sw = u * 1.4f; val sh = u * 0.86f
        ink.r.set(sx0, sy0, sx0 + sw, sy0 + sh)
        ink.p.shader = LinearGradient(0f, sy0, 0f, sy0 + sh, Ink.darken(sc, 0.88f), Ink.darken(sc, 0.95f), Shader.TileMode.CLAMP); ink.p.color = Color.WHITE; ink.p.alpha = 255
        c.drawRoundRect(ink.r, u * 0.2f, u * 0.2f, ink.p); ink.p.shader = null
        c.save(); c.clipRect(sx0 + u * 0.04f, sy0 + u * 0.04f, sx0 + sw - u * 0.04f, sy0 + sh - u * 0.04f)
        paintScreen(c, sx0, sy0, sw, sh, sc)
        // Scanlines and glass shine.
        var yy = sy0
        ink.color(Color.BLACK, 0.12f)
        while (yy < sy0 + sh) { c.drawRect(sx0, yy, sx0 + sw, yy + 1f, ink.p); yy += 3f }
        ink.ell(c, sx0 + sw * 0.3f, sy0 + sh * 0.18f, sw * 0.35f, sh * 0.12f, Color.WHITE, 0.1f, -0.2f)
        if (act == Act.POLISH) ink.glow(c, sx0 + sw * (0.2f + 0.6f * ((actT * 0.8f) % 1f)), sy0 + sh * 0.4f, u * 0.3f, u * 0.3f, Color.WHITE, 0.35f)
        c.restore()
        c.restore()

        // Smoke when it overheats.
        if (act == Act.OVERHEAT) for (i in 0 until 4) {
            val k = ((actT + i * 0.2f) % 1.2f) / 1.2f
            ink.ell(c, x + (i - 1.5f) * u * 0.2f, cy - u * 1.6f - k * u, u * (0.15f + k * 0.2f), u * (0.15f + k * 0.2f), 0xFFBBBBBB.toInt(), (1f - k) * 0.5f)
        }
        if (act == Act.POLISH && actT > 2.2f) for (i in 0 until 3) {
            val k = (actT * 2f + i / 3f) % 1f
            ink.glow(c, x - u * 0.6f + i * u * 0.6f, cy - u * 1.4f - k * u * 0.3f, u * 0.12f, u * 0.12f, Color.WHITE, 1f - k)
        }
        if (sleeping) ink.zzz(c, x + u * 0.9f, cy - u * 1.3f, time, u * 0.32f, Ink.lighten(sc, 0.2f))
        if (musicOn) for (i in 0 until 3) {
            val k = (time * 0.4f + i / 3f) % 1f
            ink.note(c, x + u * (1.3f + i * 0.3f) * (if (i % 2 == 1) -1f else 1f), cy - u * 1.2f - k * u * 1.4f, u * 0.2f, intArrayOf(0xFFCDB4FF.toInt(), 0xFFFFB36B.toInt(), sc)[i], sin(k * PI.toFloat()))
        }
        if (msgT > 0f) ink.bubble(c, x, cy - u * 1.75f, msg, u * 0.24f, min(1f, msgT * 4f))
    }

    /** The little screen face: pixels, numbers, an equalizer or a screensaver. */
    private fun paintScreen(c: Canvas, sx0: Float, sy0: Float, sw: Float, sh: Float, sc: Int) {
        val P = u * 0.075f
        val fcx = 0f; val fcy = sy0 + sh * 0.5f
        val flicker = if (lowBattery && sin(time * 23f) > 0.85f) 0.3f else 1f
        fun px(gx: Float, gy: Float, cc: Int = sc) {
            val x0 = gx.roundToInt().toFloat(); val y0 = gy.roundToInt().toFloat()
            c.drawRect(x0, y0, x0 + P * 0.86f, y0 + P * 0.86f, ink.color(cc, flicker))
        }
        ink.glow(c, fcx, fcy, sw * 0.6f, sh * 0.6f, sc, 0.12f * flicker)
        when {
            sleeping -> {
                // Screensaver: a bouncing logo. Sometimes it hits the corner.
                val w2 = sw - u * 0.5f; val h2 = sh - u * 0.28f
                val bxp = abs(((time * 0.35f) % 2f) - 1f) * w2; val byp = abs(((time * 0.27f) % 2f) - 1f) * h2
                ink.text(c, "zZ", sx0 + u * 0.22f + bxp, sy0 + u * 0.24f + byp, u * 0.2f, android.graphics.Color.HSVToColor(floatArrayOf((time * 60f) % 360f, 0.6f, 1f)))
            }
            charging && act == Act.NONE -> {
                ink.text(c, "${(battery * 100).toInt()}%", fcx + u * 0.12f, fcy + u * 0.11f, u * 0.32f, 0xFF7CFFB0.toInt())
                ink.boltShape(c, fcx - u * 0.42f, fcy, u * 0.13f, 0xFF7CFFB0.toInt())
            }
            act == Act.CLOCK -> {
                ink.text(c, clockStr, fcx, fcy + u * 0.12f, u * 0.34f, sc)
            }
            act == Act.SCAN && actT > 1.4f -> {
                ink.text(c, "HUMAN DETECTED", fcx, fcy - u * 0.06f, u * 0.14f, 0xFF7CFFB0.toInt())
                ink.text(c, "FRIEND :)", fcx, fcy + u * 0.2f, u * 0.16f, 0xFF7CFFB0.toInt())
            }
            act == Act.GLITCH && actT > 1.6f -> {
                ink.text(c, "rebooting", fcx, fcy - u * 0.04f, u * 0.13f, sc)
                c.drawRect(fcx - u * 0.45f, fcy + u * 0.08f, fcx - u * 0.45f + u * 0.9f * ((actT - 1.6f) / 1.6f).coerceIn(0f, 1f), fcy + u * 0.16f, ink.color(sc))
            }
            act == Act.RADAR -> {
                val a = actT * 4f
                ink.arc(c, fcx, fcy, u * 0.3f, 0f, 6.283f, sc, 1.2f, 0.6f)
                ink.arc(c, fcx, fcy, u * 0.15f, 0f, 6.283f, sc, 1.2f, 0.4f)
                ink.line(c, fcx, fcy, fcx + cos(a) * u * 0.3f, fcy + sin(a) * u * 0.3f, sc, 2f)
                if ((actT % 1.3f) < 0.4f) ink.ell(c, fcx + u * 0.18f, fcy - u * 0.1f, P * 0.6f, P * 0.6f, 0xFFFF6FA5.toInt())
            }
            musicOn && (act == Act.NONE || act == Act.DANCE) -> {
                // Live equalizer from your music.
                val spec = audio.spectrum
                for (i in 0 until 9) {
                    val v = (spec[(i * 3).coerceAtMost(31)] * 1.6f + 0.15f).coerceIn(0f, 1f)
                    val hgt = (v * 8f).toInt()
                    for (j in 0 until hgt) px(sx0 + u * 0.14f + i * P * 1.3f, sy0 + sh - u * 0.14f - j * P, if (j > 6) 0xFFFFB36B.toInt() else if (j > 4) 0xFFCDB4FF.toInt() else sc)
                }
            }
            else -> {
                val f = when {
                    act == Act.WAVE || act == Act.DANCE || act == Act.JUMP -> Face.HAPPY
                    act == Act.SCAN || act == Act.OVERHEAT -> Face.SHOCK
                    act == Act.GLITCH -> Face.GLITCH
                    act == Act.POLISH -> Face.WINK
                    lowBattery -> Face.SLEEPY
                    faceT > 0f -> face
                    else -> Face.EYES
                }
                val blink = (time % 3.3f) < 0.12f
                val look = ((sin(time * 0.8f) * 1.5f).roundToInt()) * P
                for (s in intArrayOf(-1, 1)) {
                    val ex = fcx + s * u * 0.32f - P + look; val ey = fcy - u * 0.12f
                    when (f) {
                        Face.HAPPY -> for ((a, b) in listOf(0 to 1, 1 to 0, 2 to 1)) px(ex + a * P, ey + b * P)
                        Face.LOVE -> for ((a, b) in listOf(0 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1, 1 to 2)) px(ex + a * P, ey + b * P - P * 0.5f, 0xFFFF6FA5.toInt())
                        Face.SHOCK -> for (a in 0 until 3) for (b in 0 until 3) if (a != 1 || b != 1) px(ex + a * P, ey + b * P - P)
                        Face.GLITCH -> for (a in 0 until 3) px(ex + a * P + (Random.nextFloat() - 0.5f) * P, ey + (Random.nextFloat() - 0.5f) * P * 2f, if (Random.nextFloat() < 0.3f) 0xFFFF6FA5.toInt() else sc)
                        Face.WINK -> if (s < 0) for (a in 0 until 3) px(ex + a * P, ey + P) else for (a in 0 until 2) for (b in 0 until 3) px(ex + a * P + P * 0.5f, ey + b * P - P)
                        Face.SLEEPY -> for (a in 0 until 3) px(ex + a * P, ey + P * 1.2f)
                        Face.EYES -> if (blink) for (a in 0 until 3) px(ex + a * P, ey + P) else for (a in 0 until 2) for (b in 0 until 3) px(ex + a * P + P * 0.5f, ey + b * P - P)
                    }
                }
                if (f == Face.SHOCK) for ((a, b) in listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)) px(fcx - P + a * P, fcy + u * 0.18f + b * P)
                else {
                    val sm = if (f == Face.HAPPY || f == Face.LOVE || f == Face.WINK) listOf(0 to 0, 1 to 1, 2 to 1, 3 to 0) else listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0)
                    for ((a, b) in sm) px(fcx - P * 2 + a * P, fcy + u * 0.2f + b * P)
                }
            }
        }
    }
}
