package app.pulsify.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** Nutty's fur colours. */
data class NuttyCoat(val name: String, val fur: Int, val light: Int, val dark: Int, val belly: Int, val tail: Int)

val NUTTY_COATS = listOf(
    NuttyCoat("Red", 0xFFD4762F.toInt(), 0xFFF2AC6A.toInt(), 0xFF8E4618.toInt(), 0xFFF8E2C4.toInt(), 0xFFD9782F.toInt()),
    NuttyCoat("Grey", 0xFF8E8A92.toInt(), 0xFFC9C5CC.toInt(), 0xFF4E4A54.toInt(), 0xFFF1EDF2.toInt(), 0xFF9C97A0.toInt()),
    NuttyCoat("Black", 0xFF3A3238.toInt(), 0xFF6E6470.toInt(), 0xFF141016.toInt(), 0xFFC9B8A8.toInt(), 0xFF453B43.toInt()),
    NuttyCoat("Golden", 0xFFE8A83A.toInt(), 0xFFFFDC8A.toInt(), 0xFFA0661A.toInt(), 0xFFFFF1D6.toInt(), 0xFFF0B347.toInt()),
    NuttyCoat("Flying", 0xFFB59273.toInt(), 0xFFE6CBB0.toInt(), 0xFF6B4E36.toInt(), 0xFFFFF6EA.toInt(), 0xFFC4A283.toInt()),
)

/**
 * Nutty, the red squirrel. Twitchy, nosy, always one second from panicking.
 * Tap: freezes, eyes huge, tail puffs. Drag: hops after your finger. Hold: an acorn appears;
 * it snatches it, stuffs its cheek and runs off to bury it (then forgets where).
 */
class NuttyScene(context: Context) : CanvasCreature(context) {

    private enum class Act { NONE, NIBBLE, STASH, LOOKOUT, FLICK, ZOOM, CATCH, SPIN, SCRATCH, FEED, DRUM }

    private var coat = NUTTY_COATS[0]
    private var u = 100f
    private var x = 0f; private var homeX = 0f
    private var face = 1f; private var faceDraw = 1f
    private var hopT = 0f; private var hopFrom = 0f; private var hopTo = 0f; private var hopping = false; private var hopH = 0f
    private var jumpY = 0f
    private var act = Act.NONE; private var actT = 0f; private var actLen = 0f
    private var nextIdle = 3f
    private val lastActs = ArrayDeque<Act>()
    private var poke = -9f
    private var tapTimes = 0; private var lastTap = -9f
    private var dragging = false; private var fingerX = 0f; private var fingerY = 0f
    private var acornX = 0f; private var acornY = 0f; private var acornVy = 0f; private var acornOn = false; private var acornFree = false
    private var cheek = 0f
    private var mound = -1f; private var moundT = 0f
    private var msg = ""; private var msgT = 0f
    private var blinkT = 2.5f
    private var beatPhase = 0f; private var lastBeat = -9f
    private var spin = 0f
    private var dust = 0f

    override val busy: Boolean get() = act != Act.NONE || hopping || sinceTouch < 2f || dragging

    override fun layout() {
        u = unit * 0.15f
        if (homeX == 0f) { homeX = W * 0.55f; x = homeX }
        homeX = homeX.coerceIn(u * 1.6f, W - u * 1.6f); x = x.coerceIn(u, W - u)
    }

    fun setCoat(i: Int) { coat = NUTTY_COATS[i.coerceIn(0, NUTTY_COATS.size - 1)] }

    override fun update(dt: Float, input: SceneInput) {
        setCoat(input.coat)
        super.update(dt, input)
    }

    // ---- Touch ----
    private fun near(px: Float, py: Float) = hypot(px - x, py - (floorY - u * 1.0f)) < u * 1.6f

    override fun touchDown(nx: Float, ny: Float): Boolean {
        val px = xp(nx); val py = yp(ny)
        sinceTouch = 0f
        if (night && act == Act.NONE) wake()
        fingerX = px; fingerY = py
        if (near(px, py)) return true
        dragging = true
        return false
    }
    override fun touchMove(nx: Float, ny: Float): Boolean {
        fingerX = xp(nx); fingerY = yp(ny); sinceTouch = 0f
        return false
    }
    override fun touchUp(): Boolean { dragging = false; return false }

    override fun tapAt(nx: Float, ny: Float): Boolean {
        val px = xp(nx); val py = yp(ny)
        sinceTouch = 0f
        if (near(px, py)) {
            // Startled: freeze, huge eyes, tail puffs to double size.
            poke = time
            if (time - lastTap < 0.6f) tapTimes++ else tapTimes = 1
            lastTap = time
            if (tapTimes >= 3) { say("chk-chk-chk!!", 1.6f); tapTimes = 0; start(Act.ZOOM) } else say("!!", 1.1f)
            listener?.onBondXp(1)
            return true
        }
        // Tap the ground near it: it hops over to check what you dropped.
        hopToX(px)
        return false
    }

    override fun longPressAt(nx: Float, ny: Float): Boolean {
        // Offer an acorn where the finger is.
        acornX = xp(nx).coerceIn(u, W - u); acornY = min(yp(ny), floorY - u * 0.2f); acornVy = 0f; acornOn = true; acornFree = true
        start(Act.FEED)
        listener?.onBondXp(2)
        return true
    }

    override fun greet() { say(listOf("hi!!", "got nuts?", "oh! you!").random(), 1.6f); start(Act.FLICK) }

    override fun perform(trick: String): Boolean {
        wake()
        val a = when (trick) {
            "nibble" -> Act.NIBBLE; "stash" -> Act.STASH; "lookout" -> Act.LOOKOUT; "flick" -> Act.FLICK
            "zoom" -> Act.ZOOM; "catch" -> Act.CATCH; "spin" -> Act.SPIN; "scratch" -> Act.SCRATCH
            "feed" -> { acornX = (x + face * u * 3f).coerceIn(u, W - u); acornY = floorY - u * 3f; acornVy = 0f; acornOn = true; acornFree = true; Act.FEED }
            "drum" -> Act.DRUM
            else -> return false
        }
        start(a); return true
    }

    private var awakeFor = 0f
    private fun wake() { awakeFor = 20f }

    private fun say(s: String, d: Float) { msg = s; msgT = d }

    private fun start(a: Act) {
        act = a; actT = 0f
        actLen = when (a) {
            Act.NIBBLE -> 3.2f; Act.STASH -> 7f; Act.LOOKOUT -> 3.6f; Act.FLICK -> 1.6f; Act.ZOOM -> 3.4f
            Act.CATCH -> 3f; Act.SPIN -> 2f; Act.SCRATCH -> 2.4f; Act.FEED -> 9f; Act.DRUM -> 4f; Act.NONE -> 0f
        }
        if (a == Act.CATCH) { acornX = (x + face * u * 1.2f).coerceIn(u, W - u); acornY = -u; acornVy = 0f; acornOn = true; acornFree = true }
        if (a == Act.STASH) { hopToX((x + face * u * 2.2f).coerceIn(u * 1.5f, W - u * 1.5f)) }
        if (a == Act.ZOOM) dust = 1f
    }

    private fun hopToX(tx: Float) {
        val target = tx.coerceIn(u * 1.2f, W - u * 1.2f)
        if (abs(target - x) < u * 0.2f) return
        hopping = true; hopT = 0f; hopFrom = x; hopTo = target
        face = sign(target - x).let { if (it == 0f) face else it }
        hopH = min(u * 0.6f, abs(target - x) * 0.25f + u * 0.15f)
    }

    // ---- Simulation ----
    override fun step(dt: Float) {
        actT += dt; msgT -= dt; moundT -= dt
        if (awakeFor > 0f) awakeFor -= dt
        val asleep = night && awakeFor <= 0f && act == Act.NONE && sinceTouch > 8f && !musicOn
        blinkT -= dt; if (blinkT < 0f) blinkT = 2.4f + Random.nextFloat() * 2.5f

        // Beat for the music dance.
        val a = audio
        if (musicOn && a.beat > 0.95f && time - lastBeat > 0.25f) { lastBeat = time; beatPhase = 0f }
        beatPhase += dt * 2f

        // Hops (quick little arcs).
        if (hopping) {
            val dur = (abs(hopTo - hopFrom) / (u * 7f)).coerceIn(0.22f, 0.9f)
            hopT += dt / dur
            val k = min(1f, hopT)
            // Squirrels move in bursts: several small bounces along the way.
            val n = max(1, (abs(hopTo - hopFrom) / (u * 1.1f)).toInt())
            x = lerpF(hopFrom, hopTo, easeF(k))
            jumpY = abs(sin(k * PI.toFloat() * n)) * hopH
            if (k >= 1f) { hopping = false; jumpY = 0f }
        } else if (dragging && act == Act.NONE && sinceTouch < 1.5f) {
            // Chase the finger in bursts: hop, stop, sit up, look, hop again.
            if (abs(fingerX - x) > u * 0.9f && Random.nextFloat() < dt * 3f) hopToX(fingerX)
        }
        faceDraw += (face - faceDraw) * min(1f, dt * 14f)

        // Falling / offered acorn.
        if (acornOn && acornFree) {
            acornVy += unit * 2.4f * dt
            acornY += acornVy * dt
            val ground = floorY - u * 0.16f
            if (acornY > ground) { acornY = ground; acornVy = -acornVy * 0.35f; if (abs(acornVy) < u * 0.4f) acornVy = 0f }
        }

        when (act) {
            Act.FEED, Act.CATCH -> {
                if (acornOn && acornFree) {
                    if (abs(acornX - x) > u * 0.6f && !hopping) hopToX(acornX - sign(acornX - x) * u * 0.45f)
                    if (abs(acornX - x) < u * 0.75f && acornY > floorY - u * 1.4f && !hopping) {
                        acornFree = false; cheek = 1f; say(if (act == Act.FEED) "mine!" else "got it!", 1.2f)
                        if (act == Act.FEED) { actT = 0f; act = Act.STASH; actLen = 7f; hopToX(if (x > W / 2) u * 1.5f else W - u * 1.5f) }
                        else actLen = actT + 1.6f
                    }
                }
                if (act == Act.CATCH && actT > 6f) act = Act.NONE
            }
            Act.STASH -> {
                // 0..1 hop away, 1..2.6 dig, 2.6..3.4 pat, 3.4.. forget.
                if (actT in 1f..2.6f) { if (moundT < 0f) mound = x + face * u * 0.62f; moundT = 30f }
                if (actT > 2.6f) { acornOn = false; cheek = 0f }
                if (actT > 4.2f && actT - dt <= 4.2f) say("...where did I put it?", 2.2f)
            }
            Act.ZOOM -> {
                // Dash to one edge and back, leaving dust.
                if (!hopping) {
                    if (actT < 1.6f) hopToX(if (x < W / 2) W - u * 1.3f else u * 1.3f)
                    else if (actT < 3f) hopToX(homeX)
                }
            }
            else -> {}
        }
        if (act != Act.NONE && actT > actLen) {
            if (act == Act.FEED) acornOn = false
            act = Act.NONE
        }
        if (cheek > 0f && act == Act.NONE) cheek = max(0f, cheek - dt * 0.5f)
        dust = max(0f, dust - dt * 0.6f)

        // Idle tricks: at least 3 a minute.
        if (act == Act.NONE && !asleep && !hopping && !dragging && sinceTouch > 3f) {
            nextIdle -= dt
            if (nextIdle <= 0f) {
                val pool = when {
                    charging -> mutableListOf(Act.NIBBLE, Act.FLICK, Act.LOOKOUT)
                    lowBattery -> mutableListOf(Act.LOOKOUT, Act.FLICK, Act.NIBBLE)
                    musicOn -> mutableListOf(Act.DRUM, Act.FLICK, Act.SPIN)
                    else -> mutableListOf(Act.NIBBLE, Act.STASH, Act.LOOKOUT, Act.FLICK, Act.ZOOM, Act.CATCH, Act.SPIN, Act.SCRATCH)
                }
                pool.removeAll(lastActs)
                val pick = pool.random()
                lastActs.addLast(pick); if (lastActs.size > 3) lastActs.removeFirst()
                start(pick)
                nextIdle = TRICK_GAP_MIN + Random.nextFloat() * TRICK_GAP_RANGE
            }
        }
        // Wander home slowly between tricks.
        if (act == Act.NONE && !hopping && !dragging && abs(x - homeX) > u * 2.5f && Random.nextFloat() < dt * 0.5f) hopToX(homeX)
        if (act == Act.SPIN) spin = actT / actLen * 2f * PI.toFloat() else spin *= 0.8f
        sleeping = asleep
    }
    private var sleeping = false

    // ---- Paint ----
    override fun paint(c: Canvas) {
        val floor = floorY
        val pk = time - poke
        val surprised = pk < 1.4f
        val puff = if (surprised) 1f - pk / 1.4f else 0f
        val col = coat
        // Mound where it buried something.
        if (moundT > 0f && mound > 0f) ink.ell(c, mound, floor + 2f, u * 0.34f, u * 0.09f, Ink.darken(0xFF6B4423.toInt(), 0.1f), 0.9f)
        // Shadow.
        ink.ell(c, x, floor + 2f, u * 0.75f * (1f - jumpY / (u * 3f)), u * 0.11f, Color.BLACK, 0.3f)

        if (sleeping) { paintSleeping(c, floor); return }

        // Pose.
        var cy = floor - u * 0.95f - jumpY
        var tailFlick = sin(time * 2.2f) * 0.08f
        var headTilt = sin(time * 0.7f) * 0.08f
        var nib = 0f; var hold = acornOn && !acornFree; var look = 0.3f
        var happy = false; var dig = 0f; var sitUp = 0f; var scratch = 0f
        var eyesBig = surprised
        when (act) {
            Act.NIBBLE -> { nib = max(0f, sin(time * 16f)) * (if (sin(time * 1.6f) > -0.3f) 1f else 0f); hold = true }
            Act.FLICK -> { tailFlick += sin(actT * 26f) * 0.32f * exp(-actT * 1.2f); if (actT < 0.9f && actT + 0.02f > 0.2f && msgT < 0f) say("chk!", 0.7f) }
            Act.LOOKOUT -> { sitUp = easeF(actT / 0.4f) * (1f - easeF((actT - 3.1f) / 0.4f)); look = if ((actT * 1.4f).toInt() % 2 == 0) -1f else 1f; headTilt = look * 0.12f }
            Act.STASH -> {
                if (actT in 1f..2.6f) { dig = abs(sin(actT * 18f)); hold = false }
                if (actT in 2.6f..3.4f) happy = true
                if (actT > 3.4f) { look = sin(actT * 4f); headTilt = sin(actT * 3f) * 0.2f }
            }
            Act.CATCH -> { look = 0f; if (acornFree) { headTilt = -0.25f; eyesBig = true } else happy = actT % 1f < 0.5f }
            Act.FEED -> { if (acornFree) eyesBig = true }
            Act.SCRATCH -> { scratch = sin(actT * 30f); headTilt = 0.25f; happy = true }
            Act.DRUM -> { hold = true; nib = 0f }
            else -> {}
        }
        if (musicOn && act == Act.NONE || act == Act.DRUM) {
            val ph = beatPhase.coerceIn(0f, 1f)
            cy -= exp(-ph * 6f) * u * 0.14f
            tailFlick += sin(time * PI.toFloat() * 2f) * 0.3f
            headTilt = sin(time * PI.toFloat() * 2f) * 0.12f
            happy = ph < 0.25f
        }
        if (lowBattery && act == Act.NONE) { look = sin(time * 5f); headTilt = sin(time * 4f) * 0.1f }
        cy -= sitUp * u * 0.25f

        val f = faceDraw
        c.save()
        c.translate(x, 0f); c.scale(if (abs(f) < 0.1f) 0.1f * sign(f + 1e-4f) else f, 1f); c.translate(-x, 0f)
        // Body squash while landing / breathing.
        val sq = 1f + sin(time * 3f) * 0.015f + (if (hopping) -0.04f * cos(hopT * PI.toFloat() * 2f) else 0f)
        c.save(); c.translate(x, floor); c.rotate(spin * 57.29578f * (if (act == Act.SPIN) 1f else 0f)); c.scale(1f / kotlin.math.sqrt(sq), sq); c.translate(-x, -floor)

        // Tail: a bushy S built from soft fur clumps.
        val tbx = x - u * 0.45f; val tby = cy + u * 0.55f
        val ang = tailFlick + puff * 0.2f
        val ca = cos(ang); val sa = sin(ang)
        for (i in 0..16) {
            val k = i / 16f
            val px = tbx - u * 0.55f * sin(k * 2.2f) - k * u * 0.25f
            val py = tby - k * u * 2.1f
            val tx = tbx + (px - tbx) * ca - (py - tby) * sa
            val ty = tby + (px - tbx) * sa + (py - tby) * ca
            val r = u * (0.32f + 0.22f * sin(k * PI.toFloat())) * (1f + puff * 0.45f) * (if (k > 0.9f) 1f - (k - 0.9f) * 4f else 1f)
            ink.glossy(c, tx, ty, r, r, col.tail, col.light, col.dark)
        }
        // Fur streaks on the tail.
        for (i in 2 until 15) {
            val k = i / 16f; val k2 = (i + 1) / 16f
            val px = tbx - u * 0.55f * sin(k * 2.2f) - k * u * 0.25f; val py = tby - k * u * 2.1f
            val px2 = tbx - u * 0.55f * sin(k2 * 2.2f) - k2 * u * 0.25f; val py2 = tby - k2 * u * 2.1f
            val x0 = tbx + (px - tbx) * ca - (py - tby) * sa; val y0 = tby + (px - tbx) * sa + (py - tby) * ca
            val x1 = tbx + (px2 - tbx) * ca - (py2 - tby) * sa; val y1 = tby + (px2 - tbx) * sa + (py2 - tby) * ca
            ink.line(c, x0 - u * 0.1f, y0, x1 - u * 0.18f, y1 - u * 0.05f, Ink.lighten(col.light, 0.4f), u * 0.025f, 0.35f)
        }
        // Hind leg, body, belly.
        val legKick = scratch * u * 0.12f
        ink.glossy(c, x - u * 0.1f, cy + u * 0.55f - legKick, u * 0.42f, u * 0.36f, Ink.darken(col.fur, 0.05f), col.light, col.dark)
        ink.glossy(c, x, cy + u * 0.18f, u * 0.48f, u * 0.62f, col.fur, col.light, col.dark)
        ink.glossy(c, x + u * 0.12f, cy + u * 0.3f, u * 0.26f, u * 0.42f, col.belly, Ink.lighten(col.belly, 0.6f), Ink.darken(col.belly, 0.15f))
        // Feet.
        val foot = Ink.darken(col.fur, 0.15f)
        ink.ell(c, x + u * 0.15f, cy + u * 0.88f + jumpY * 0.15f, u * 0.26f, u * 0.09f, foot)
        ink.ell(c, x - u * 0.2f, cy + u * 0.9f + jumpY * 0.2f, u * 0.24f, u * 0.08f, foot)

        // Head.
        val hx = x + u * 0.18f
        val hy = cy - u * 0.55f + nib * u * 0.03f + dig * u * 0.25f
        c.save(); c.translate(hx, hy); c.rotate((headTilt + dig * 0.5f) * 57.29578f)
        for (s in intArrayOf(-1, 1)) {
            c.save(); c.translate(s * u * 0.22f - u * 0.04f, -u * 0.32f)
            c.rotate((s * 0.25f + (if (eyesBig) s * -0.15f else 0f) + (if (lowBattery) s * 0.5f else 0f)) * 57.29578f)
            ink.glossy(c, 0f, 0f, u * 0.11f, u * 0.19f, col.fur, col.light, col.dark)
            ink.ell(c, 0f, u * 0.03f, u * 0.05f, u * 0.1f, 0xFFF3B8A0.toInt())
            ink.path.reset(); ink.path.moveTo(-u * 0.04f, -u * 0.17f); ink.path.lineTo(u * 0.02f, -u * 0.32f); ink.path.lineTo(u * 0.05f, -u * 0.16f); ink.path.close()
            c.drawPath(ink.path, ink.color(col.dark))
            c.restore()
        }
        ink.glossy(c, 0f, 0f, u * 0.36f, u * 0.33f, col.fur, col.light, col.dark)
        // Muzzle and cheek (stuffed when it carries an acorn).
        ink.glossy(c, u * 0.08f, u * 0.12f, u * 0.22f, u * 0.16f, col.belly, Ink.lighten(col.belly, 0.7f), Ink.darken(col.belly, 0.12f))
        if (cheek > 0.05f) ink.glossy(c, u * 0.24f, u * 0.14f, u * 0.13f * cheek, u * 0.11f * cheek, col.belly, Ink.lighten(col.belly, 0.7f), Ink.darken(col.belly, 0.15f))
        // Eyes.
        val ex = u * 0.15f; val ey = -u * 0.06f
        val blink = if (blinkT < 0.1f) 1f else 0f
        if (happy) { ink.happyEye(c, -ex + u * 0.06f, ey, u * 0.11f); ink.happyEye(c, ex + u * 0.06f, ey, u * 0.11f) }
        else {
            ink.ell(c, -ex + u * 0.06f, ey, u * 0.125f, u * 0.135f, 0xFFFFF3E0.toInt()); ink.ell(c, ex + u * 0.06f, ey, u * 0.125f, u * 0.135f, 0xFFFFF3E0.toInt())
            val r = if (eyesBig) u * 0.12f else u * 0.105f
            ink.eye(c, -ex + u * 0.06f, ey, r, blink, look, 0f, 0xFF2A1608.toInt())
            ink.eye(c, ex + u * 0.06f, ey, r, blink, look, 0f, 0xFF2A1608.toInt())
        }
        // Nose, teeth, mouth, whiskers.
        ink.ell(c, u * 0.1f, u * 0.09f, u * 0.05f, u * 0.035f, 0xFF4A2414.toInt())
        val tooth = u * 0.05f + nib * u * 0.02f
        c.drawRect(u * 0.07f, u * 0.18f, u * 0.1f, u * 0.18f + tooth, ink.color(Color.WHITE))
        c.drawRect(u * 0.105f, u * 0.18f, u * 0.135f, u * 0.18f + tooth, ink.color(Color.WHITE))
        ink.line(c, u * 0.1f, u * 0.12f, u * 0.1f, u * 0.17f, 0xFF4A2414.toInt(), u * 0.02f)
        ink.path.reset(); ink.path.moveTo(u * 0.04f, u * 0.17f); ink.path.quadTo(u * 0.1f, u * 0.2f, u * 0.16f, u * 0.17f)
        c.drawPath(ink.path, ink.stroke(0xFF4A2414.toInt(), u * 0.02f))
        for (s in intArrayOf(-1, 1)) for (i in 0 until 2) {
            ink.line(c, u * 0.1f + s * u * 0.12f, u * 0.12f + i * u * 0.04f, u * 0.1f + s * u * 0.38f, u * 0.08f + i * u * 0.07f + sin(time * 9f + i) * u * 0.01f * nib, 0xFF3C1E0F.toInt(), max(1f, u * 0.008f), 0.5f)
        }
        ink.blush(c, -u * 0.17f, u * 0.1f, u * 0.09f, u * 0.05f); ink.blush(c, u * 0.3f, u * 0.1f, u * 0.09f, u * 0.05f)
        c.restore()

        // Front paws and what they hold.
        val paw = Ink.darken(col.fur, 0.08f)
        val pawY = hy + u * 0.32f - nib * u * 0.05f
        if (charging && act == Act.NONE) {
            paintAcorn(c, x + u * 0.62f, cy + u * 0.25f, u * 0.9f, -0.15f, 0.8f + 0.2f * sin(time * 4f), battery)
            ink.ell(c, x + u * 0.3f, cy + u * 0.15f, u * 0.08f, u * 0.06f, paw); ink.ell(c, x + u * 0.42f, cy + u * 0.42f, u * 0.08f, u * 0.06f, paw)
        } else if (dig > 0f) {
            ink.ell(c, x + u * 0.42f, floor - u * 0.08f + dig * u * 0.06f, u * 0.08f, u * 0.06f, paw)
            ink.ell(c, x + u * 0.6f, floor - u * 0.06f - dig * u * 0.06f, u * 0.08f, u * 0.06f, paw)
        } else if (act == Act.DRUM || (musicOn && act == Act.NONE)) {
            val d = exp(-beatPhase.coerceIn(0f, 1f) * 8f)
            paintAcorn(c, x + u * 0.38f, cy + u * 0.2f, u * 0.22f, 0f)
            ink.ell(c, x + u * 0.3f, cy - u * 0.02f + d * u * 0.12f, u * 0.08f, u * 0.06f, paw)
            ink.ell(c, x + u * 0.5f, cy + d * u * 0.12f, u * 0.08f, u * 0.06f, paw)
        } else {
            if (hold || (lowBattery && act == Act.NONE)) paintAcorn(c, x + u * 0.3f, pawY - u * 0.05f, u * (if (lowBattery) 0.12f else 0.18f), 0.2f)
            ink.ell(c, x + u * 0.22f, pawY, u * 0.08f, u * 0.065f, paw)
            ink.ell(c, x + u * 0.38f, pawY + u * 0.02f, u * 0.08f, u * 0.065f, paw)
        }
        c.restore() // squash
        c.restore() // facing

        // Things in the world (not mirrored).
        if (acornOn && acornFree) paintAcorn(c, acornX, acornY, u * 0.22f, sin(time * 6f) * 0.3f)
        if (dig > 0.5f) for (i in 0 until 6) {
            val k = (time * 3f + i / 6f) % 1f
            ink.ell(c, x + face * (u * 0.5f - k * u * 1.2f), floor - sin(k * PI.toFloat()) * u * 0.8f, u * 0.035f, u * 0.035f, 0xFF5A3A22.toInt())
        }
        if (dust > 0.05f && hopping) for (i in 0 until 4) {
            val k = (time * 2f + i / 4f) % 1f
            ink.ell(c, x - face * (u * 0.4f + k * u), floor - k * u * 0.3f, u * (0.1f + k * 0.15f), u * (0.08f + k * 0.1f), 0xFFE8DCC8.toInt(), 0.45f * (1f - k) * dust)
        }
        if (musicOn) for (i in 0 until 3) {
            val k = (time * 0.4f + i / 3f) % 1f
            ink.note(c, x + u * (1.0f + i * 0.25f) * (if (i % 2 == 1) -1f else 1f), cy - u * 0.9f - k * u * 1.4f, u * 0.18f, intArrayOf(0xFFCDB4FF.toInt(), 0xFFFFB36B.toInt(), 0xFF9BE15D.toInt())[i], sin(k * PI.toFloat()))
        }
        if (msgT > 0f) ink.bubble(c, x + face * u * 0.2f, cy - u * 1.25f, msg, u * 0.2f, min(1f, msgT * 4f))
        else if (charging && act == Act.NONE && (time % 9f) > 7f) ink.bubble(c, x, cy - u * 1.3f, "crunchy volts", u * 0.16f)
        else if (lowBattery && act == Act.NONE && (time % 8f) > 6.5f) ink.bubble(c, x, cy - u * 1.3f, "my last acorn...", u * 0.16f)
    }

    private fun paintSleeping(c: Canvas, floor: Float) {
        val col = coat
        val br = 1f + sin(time * 1.5f) * 0.04f
        val cx = x; val cy = floor - u * 0.42f
        ink.glossy(c, cx, cy, u * 0.62f * br, u * 0.45f * br, col.fur, col.light, col.dark)
        ink.glossy(c, cx + u * 0.18f, cy - u * 0.12f, u * 0.3f, u * 0.26f, col.fur, col.light, col.dark)
        ink.closedEye(c, cx + u * 0.08f, cy - u * 0.12f, u * 0.08f, 0xFF2A1508.toInt())
        ink.closedEye(c, cx + u * 0.26f, cy - u * 0.12f, u * 0.08f, 0xFF2A1508.toInt())
        // Tail wrapped over it like a blanket.
        ink.path.reset()
        ink.path.moveTo(cx - u * 0.75f, cy + u * 0.4f)
        ink.path.cubicTo(cx - u * 0.9f, cy - u * 0.4f, cx + u * 0.1f, cy - u * 0.6f, cx + u * 0.7f, cy - u * 0.05f)
        ink.path.cubicTo(cx + u * 0.3f, cy + u * 0.3f, cx - u * 0.15f, cy + u * 0.45f, cx - u * 0.75f, cy + u * 0.4f)
        c.drawPath(ink.path, ink.color(col.tail))
        ink.glow(c, cx - u * 0.2f, cy - u * 0.15f, u * 0.5f, u * 0.25f, col.light, 0.35f)
        ink.zzz(c, cx + u * 0.6f, cy - u * 0.5f, time, u * 0.32f)
    }

    private fun paintAcorn(c: Canvas, x: Float, y: Float, s: Float, rot: Float, glow: Float = 0f, lvl: Float = 1f) {
        c.save(); c.translate(x, y); c.rotate(rot * 57.29578f)
        if (glow > 0f) {
            ink.glow(c, 0f, s * 0.15f, s * 1.1f, s * 1.2f, 0xFF7CFFB0.toInt(), 0.45f * glow)
            ink.glossy(c, 0f, s * 0.15f, s * 0.45f, s * 0.55f, 0xFF4FDB8A.toInt(), 0xFFD8FFE6.toInt(), 0xFF2FBF6A.toInt())
            if (lvl < 1f) { c.save(); ink.r.set(-s * 0.45f, -s * 0.4f, s * 0.45f, s * 0.7f); c.clipRect(-s, -s * 0.4f, s, -s * 0.4f + s * 1.1f * (1f - lvl)); ink.ell(c, 0f, s * 0.15f, s * 0.45f, s * 0.55f, 0xFF0A2814.toInt(), 0.6f); c.restore() }
        } else ink.glossy(c, 0f, s * 0.15f, s * 0.45f, s * 0.55f, 0xFFC4823F.toInt(), 0xFFE2A565.toInt(), 0xFF9A5A24.toInt())
        ink.r.set(-s * 0.52f, -s * 0.5f, s * 0.52f, s * 0.06f)
        c.drawArc(ink.r, 180f, 180f, true, ink.color(0xFF6B4122.toInt()))
        c.drawRect(-s * 0.52f, -s * 0.24f, s * 0.52f, -s * 0.18f, ink.color(0xFF6B4122.toInt()))
        for (i in -2..2) ink.line(c, i * s * 0.18f, -s * 0.46f, i * s * 0.22f, -s * 0.2f, Color.BLACK, 1f, 0.25f)
        ink.path.reset(); ink.path.moveTo(0f, -s * 0.48f); ink.path.quadTo(s * 0.05f, -s * 0.66f, s * 0.16f, -s * 0.7f)
        c.drawPath(ink.path, ink.stroke(0xFF4A2C14.toInt(), s * 0.08f))
        if (glow > 0f) ink.boltShape(c, 0f, s * 0.18f, s * 0.2f, 0xFF0B3A1E.toInt())
        c.restore()
    }
}
