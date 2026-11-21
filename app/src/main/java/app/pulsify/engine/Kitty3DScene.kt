package app.pulsify.engine

import android.content.Context
import android.graphics.Color
import app.pulsify.core.AudioFrame
import app.pulsify.core.Mood
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A rectangle the 3D kitty can play with (a home-screen widget), in centred uv, y up. */
class Ledge { var l = 0f; var t = 0f; var r = 0f; var b = 0f; var id = 0 }

/** Where widgets are, shared with the render thread (screen fractions + home-screen page). */
object KittyLedges {
    /** page, left, top, right, bottom (fractions of the screen) per widget the user marked. */
    @Volatile var user: FloatArray = FloatArray(0)
}

/**
 * The 3D kitty: a real little model (three body shapes, four coats) posed by springs every frame.
 * Everything here is procedural, so the same moment never plays twice. The model is only
 * re-rendered when its pose changes; the rest of the time the last picture is simply stamped.
 */
class Kitty3DScene(private val context: Context) : Creature {

    override var listener: SceneListener? = null
    /** Body shape: 0 Explorer, 1 Dumpling, 2 Slim. Swapped on the GL thread in [update]. */
    @Volatile var design = 0
    /** The render loop shifts the whole creature by this fraction of the width while pages swipe. */
    var viewLag = 0f

    private val kit = Kit3D(context)
    private val pose = KitPose()
    private val pal = Kit3D.Palette()

    private enum class Act {
        NONE, LOOK, GROOM, STRETCH, SPIN, WAVE, POUNCE_BUG, HOP_PLAY, WALK, CHASE, POUNCE, BOOP, PURR, CARRY, FALL,
        TUMBLE, DIZZY, STUMBLE, NOTICE_CABLE, PLAY_CABLE, SLEEP, STARTLE, DANCE, YAWN, PERCH_JUMP, PERCHED, JUMP_DOWN,
        PEEK, REACH, WIGGLE, HANG_JUMP, HANG, CLING, ZOOM, BELLY
    }

    private var w = 1; private var h = 1
    private var time = 0f
    override var bottomInset = 0f
        set(v) { field = v.coerceIn(0f, 0.8f); layout() }
    private var ground = Float.NaN
    private var size = 0.55f            // cat height on screen, uv
    private var floorY = -0.5f          // the floor, uv
    private val halfW get() = 0.5f * w / min(w, h)
    private val halfH get() = 0.5f * h / min(w, h)

    // Where the cat is: feet position, velocity, support.
    private var x = 0f; private var y = -0.5f
    private var vx = 0f; private var vy = 0f
    private var grounded = true
    private var onLedge = -1                   // ledge id we stand on, or -1
    private var ledgeDx = 0f                   // offset from that ledge's left
    private var facing = 0f                    // -1 left .. 1 right (0 = toward you)
    private var spin = 0f                      // extra yaw (tail chasing, flips)
    private var roll = 0f; private var rollV = 0f   // tumble, in the screen plane
    private var hopT = 0f
    private var targetX = 0f

    // Pose springs.
    private val sYaw = Spring(); private val sPitch = Spring(); private val sSqY = Spring(1f); private val sSqX = Spring(1f)
    private val sHY = Spring(); private val sHP = Spring(); private val sHT = Spring()
    private val sEarL = Spring(); private val sEarR = Spring(); private val sDroop = Spring()
    private val sLegL = Spring(); private val sLegR = Spring(); private val sSprL = Spring(); private val sSprR = Spring()
    private val sCurl = Spring(); private val sWag = Spring(); private val sDrp = Spring()
    private val sEye = Spring(1f); private val sGlow = Spring(); private val sDim = Spring()
    private var tailPhase = 0f; private var tailSpeed = 2f
    private var blink = 1f; private var nextBlink = 2f; private var eyesShut = 0f
    private var lookX = 0f; private var lookY = 0f; private var lookTX = 0f; private var lookTY = 0f; private var nextGlance = 2f

    private var act = Act.NONE
    private var actT = 0f
    private var actDur = 0f
    private var nextIdle = 3f
    private var lastAct = Act.NONE

    // Touch.
    private var touching = false
    private var downAt = 0f
    private var downOnCat = false
    private var tX = 0f; private var tY = 0f; private var t0X = 0f; private var t0Y = 0f
    private var moved = false; private var longFired = false
    private var taps = 0; private var lastTap = -9f
    var sinceTouch = 99f; private set
    private var grabDx = 0f; private var grabDy = 0f

    // Charging cable.
    private var wasCharging = false; private var chargeKnown = false
    private var cableOut = 0f                  // 0 hidden .. 1 fully in
    private var cableX = 0.25f
    private val plug = Spring(); private val plugY = Spring()
    private val cablePts = FloatArray(24)

    // Ledges (widgets), refreshed every frame.
    private val ledges = ArrayList<Ledge>()
    private var ledgeTarget = -1
    private var peekSide = 1f
    private var hangOn = -1                    // ledge id she hangs under by her front paws, or -1
    private var hangDx = 0f
    private var lastLedgeX = Float.NaN         // to feel the page moving under her
    private var clingSide = 1f                 // which screen edge she clings to
    private var zoomLaps = 0
    private var stroke = 0f                    // how much she has been rubbed while purring
    private var lastMX = 0f; private var lastMY = 0f
    private val sSink = Spring(); private val sRoll = Spring()   // body lowered / rolled (belly flop, cling lean)

    // Little effects.
    private class P(var kind: Float, var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, var max: Float,
                    var s: Float, var r: Float, var g: Float, var b: Float, var rot: Float, var spin: Float)
    private val parts = ArrayList<P>()
    private var mood = Mood.DAY
    private var musicOn = false
    private var lastBeat = 0f

    override val busy: Boolean
        get() = touching || sinceTouch < 2f || act != Act.NONE || !grounded || abs(vx) > 0.01f || parts.isNotEmpty() ||
            abs(cableOut - (if (wasCharging) 1f else 0f)) > 0.01f || musicOn

    // ---------------------------------------------------------------- GL lifecycle

    override fun onSurfaceCreated() { kit.create(); kit.load(design) }

    override fun onSurfaceChanged(width: Int, height: Int) { w = max(1, width); h = max(1, height); layout() }

    override fun setGround(y: Float) {
        if (y == ground || (y.isNaN() && ground.isNaN())) return
        ground = y; layout()
    }

    private fun layout() {
        size = min(0.56f, halfW * 2f * 0.5f)
        floorY = if (ground.isNaN()) -halfH + 0.3f else ground
        if (h.toFloat() / w < 1.5f) { size = min(0.4f, halfW * 0.7f); floorY = -halfH + 0.12f }
        if (bottomInset > 0f) {
            val floor = -halfH + bottomInset * 2f * halfH + 0.05f
            size = min(size, (halfH - 0.1f - floor) / 1.2f)
            floorY = max(floorY, floor)
        }
        if (grounded && onLedge < 0) y = floorY
        x = x.coerceIn(-halfW + size * 0.4f, halfW - size * 0.4f)
        kit.invalidate()
    }

    override fun release() { kit.release() }

    // ---------------------------------------------------------------- helpers

    private fun toUv(nx: Float, ny: Float): Pair<Float, Float> {
        val m = min(w, h).toFloat()
        return Pair((nx - 0.5f) * w / m, (0.5f - ny) * h / m)
    }

    private fun onCat(ux: Float, uy: Float): Boolean {
        val cx = (ux - x) / size; val cy = (uy - y) / size
        return cx * cx / (0.42f * 0.42f) + (cy - 0.5f) * (cy - 0.5f) / (0.58f * 0.58f) < 1f
    }

    private fun lookAt(ux: Float, uy: Float) {
        lookTX = ((ux - x) / size / 1.2f).coerceIn(-1f, 1f)
        lookTY = ((uy - (y + size * 0.75f)) / size / 1.2f).coerceIn(-1f, 1f)
        nextGlance = 2f + Random.nextFloat() * 2f
    }

    private fun discover(id: String) { if (reported.add(id)) listener?.onDiscovery(id) }
    private val reported = HashSet<String>()

    private fun start(a: Act, dur: Float = 0f) {
        if (a != Act.HANG) hangOn = -1
        if (a != Act.BELLY && a != Act.PURR) stroke = 0f
        lastAct = act; act = a; actT = 0f; actDur = dur
    }

    private fun jump(toX: Float, toY: Float, apex: Float) {
        // A ballistic hop that peaks [apex] above the higher end.
        val g = GRAV * size
        val top = max(y, toY) + apex
        val up = sqrt(2f * g * max(0.001f, top - y))
        val tUp = up / g
        val tDown = sqrt(2f * max(0.001f, top - toY) / g)
        vy = up; vx = (toX - x) / (tUp + tDown)
        grounded = false; onLedge = -1
        sSqY.vel -= 4f; sSqX.vel += 2f
        if (abs(vx) > 0.02f) facing = sign(vx)
    }

    private fun burst(kind: Float, n: Int, cx: Float, cy: Float, r: Float, g: Float, b: Float, spread: Float = 1f, s: Float = 0.035f) {
        for (i in 0 until n) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val sp = (0.1f + Random.nextFloat() * 0.25f) * spread
            parts += P(kind, cx, cy, cos(a) * sp * 0.6f, 0.25f + sin(a).let { abs(it) } * sp, 0f, 1.1f + Random.nextFloat() * 0.6f,
                s * (0.7f + Random.nextFloat() * 0.6f), r, g, b, Random.nextFloat() * 0.6f - 0.3f, Random.nextFloat() - 0.5f)
        }
        if (parts.size > 60) parts.subList(0, parts.size - 60).clear()
    }
    private fun float1(kind: Float, cx: Float, cy: Float, r: Float, g: Float, b: Float, s: Float = 0.04f, life: Float = 1.6f) {
        parts += P(kind, cx, cy, (Random.nextFloat() - 0.5f) * 0.05f, 0.12f, 0f, life, s, r, g, b, 0f, (Random.nextFloat() - 0.5f) * 0.6f)
    }

    private val headX get() = x + facing * size * 0.12f
    private val headY get() = y + size * 0.78f

    // ---------------------------------------------------------------- input

    override fun touchDown(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f; touching = true; moved = false; longFired = false; downAt = time
        tX = ux; tY = uy; t0X = ux; t0Y = uy; lastMX = ux; lastMY = uy
        downOnCat = onCat(ux, uy)
        lookAt(ux, uy)
        if (act == Act.SLEEP && downOnCat) wake()
        return true
    }

    override fun touchMove(nx: Float, ny: Float): Boolean {
        if (!touching) return false
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        if (hypot(ux - t0X, uy - t0Y) > 0.035f) moved = true
        val mdx = ux - lastMX; val mdy = uy - lastMY
        lastMX = ux; lastMY = uy
        tX = ux; tY = uy
        lookAt(ux, uy)
        if (downOnCat && (act == Act.PURR || act == Act.BELLY)) {
            // Rubbing her while she purrs: she melts and flops over for belly rubs.
            stroke += hypot(mdx, mdy)
            if (act == Act.PURR && stroke > size * 0.6f) { start(Act.BELLY); stroke = size; discover("belly") }
            return true
        }
        if (moved && downOnCat && act != Act.CARRY && act != Act.SLEEP) {
            // Picked up by the scruff.
            grabDx = x - ux; grabDy = y - uy
            start(Act.CARRY); discover("carry")
        } else if (moved && !downOnCat && act != Act.CARRY && act != Act.PERCHED && grounded && act != Act.SLEEP && act != Act.PLAY_CABLE) {
            if (act != Act.CHASE) start(Act.CHASE)
        }
        return true
    }

    override fun touchUp(): Boolean {
        if (!touching) return false
        touching = false; sinceTouch = 0f
        val tap = !moved && time - downAt < 0.35f
        if (act == Act.PURR) { start(Act.NONE); sGlow.target = 0f }
        if (act == Act.BELLY) actDur = actT + 1.6f        // stays on her back a moment, then rolls up
        if (act == Act.CARRY) { start(Act.FALL); vx = (tX - t0X).coerceIn(-0.5f, 0.5f); vy = 0.15f * size; grounded = false; onLedge = -1 }
        else if (act == Act.CHASE) pounceAt(tX)
        if (tap) { if (downOnCat) boop() else pounceAt(tX) }
        return true
    }

    override fun tapAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f; lookAt(ux, uy)
        if (onCat(ux, uy)) boop() else pounceAt(ux)
        return true
    }

    override fun longPressAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        if (onCat(ux, uy)) { start(Act.PURR); discover("purr") } else pounceAt(ux)
        return true
    }

    override fun greet() { if (act == Act.NONE || act == Act.LOOK) start(Act.WAVE, 1.6f) }

    override fun swiped(dir: Float, fast: Boolean) {
        if (act == Act.SLEEP || act == Act.CARRY) return
        if (onLedge >= 0) return                          // the ledge carries her; see update()
        if (!grounded) return
        if (fast) {
            start(Act.TUMBLE, 1.1f); discover("tumble")
            // flung against the page's motion; near an edge she slams into it and clings
            jump(x - dir * size * 1.1f, y, size * 0.35f)
            rollV = -dir * 2f * PI.toFloat() / 0.75f
        } else {
            start(Act.STUMBLE, 1.2f)
            vx = -dir * 0.35f * size
        }
    }

    override fun perform(trick: String): Boolean {
        sinceTouch = 0f
        when (trick) {
            "pounce" -> pounceAt(x + (if (Random.nextBoolean()) 1 else -1) * size * 0.6f)
            "zoomies" -> zoomies()
            "chase", "tail" -> start(Act.SPIN, 1.6f)
            "belly" -> { start(Act.BELLY, 3f) }
            "purr" -> start(Act.PURR, 3f)
            "groom" -> start(Act.GROOM, 3f)
            "loaf", "box" -> start(Act.SLEEP, 6f)
            "trill" -> start(Act.WAVE, 1.6f)
            "stretch" -> start(Act.STRETCH, 2.2f)
            "knock", "cucumber" -> start(Act.STARTLE, 1.2f)
            else -> return false
        }
        return true
    }

    private fun boop() {
        listener?.onBondXp(1)
        if (time - lastTap < 0.6f) taps++ else taps = 1
        lastTap = time
        if (act == Act.SLEEP) { wake(); return }
        if (act == Act.HANG) { start(Act.FALL); vy = 0f; return }          // a boop makes her let go
        if (taps >= 3) { taps = 0; zoomies(); return }
        start(Act.BOOP, 0.7f)
        sSqY.vel -= 5f; sSqX.vel += 3f
        burst(2f, 3, headX, headY + size * 0.2f, 1f, 0.45f, 0.6f)
    }

    private fun zoomies() {
        if (act == Act.CARRY || act == Act.HANG || onLedge >= 0 || !grounded) { start(Act.SPIN, 1.4f); return }
        zoomLaps = 3 + Random.nextInt(2)
        targetX = if (x > 0f) -halfW + size * 0.45f else halfW - size * 0.45f
        hopT = 1f
        start(Act.ZOOM); discover("zoomies")
        burst(9f, 3, x, y + size * 0.5f, 1f, 0.95f, 0.6f)
    }

    private fun pounceAt(ux: Float) {
        if (act == Act.SLEEP || act == Act.CARRY || !grounded || onLedge >= 0) return
        targetX = ux.coerceIn(-halfW + size * 0.45f, halfW - size * 0.45f)
        start(Act.POUNCE)
        jump(targetX, floorY, size * (0.22f + min(0.3f, abs(targetX - x)) * 0.5f))
    }

    private fun wake() {
        start(Act.STRETCH, 2f); eyesShut = 0f
        burst(9f, 3, headX, headY + size * 0.15f, 1f, 0.95f, 0.6f)
    }

    // ---------------------------------------------------------------- update

    override fun update(dt: Float, input: SceneInput) {
        time += dt; actT += dt; sinceTouch += dt
        kit.load(design)                                   // no-op unless the body shape changed
        mood = input.mood
        musicOn = input.audio.source != AudioFrame.Source.NONE && input.audio.level > 0.02f
        palette(input, dt)
        refreshLedges(input)
        charger(input, dt)

        if (input.lowBattery && !input.charging) sDim.target = 0.35f else if (act != Act.SLEEP) sDim.target = 0f

        // Long press on the cat: purr.
        if (touching && !moved && !longFired && time - downAt > 0.55f) {
            longFired = true
            if (downOnCat) { start(Act.PURR); discover("purr") }
        }

        behave(dt, input)
        physics(dt)
        animate(dt, input)
        particles(dt)
    }

    private fun palette(input: SceneInput, dt: Float) {
        val c = KITTY_COATS[input.coat.coerceIn(0, KITTY_COATS.size - 1)]
        val k = min(1f, dt * 6f)
        val siamese = c.points > 0.5f
        ease(pal.fur, c.fur, k); ease(pal.cream, c.white, k); ease(pal.iris, c.eye, k)
        ease(pal.stripe, if (siamese) c.fur else c.stripe, k); ease(pal.point, c.stripe, k)
        pal.points += (c.points - pal.points) * k
        if (abs(c.points - pal.points) > 0.001f) kit.invalidate()
    }

    private fun ease(dst: FloatArray, argb: Int, k: Float) {
        val r = Color.red(argb) / 255f; val g = Color.green(argb) / 255f; val b = Color.blue(argb) / 255f
        if (abs(dst[0] - r) + abs(dst[1] - g) + abs(dst[2] - b) > 0.002f) kit.invalidate()
        dst[0] += (r - dst[0]) * k; dst[1] += (g - dst[1]) * k; dst[2] += (b - dst[2]) * k
    }

    private fun refreshLedges(input: SceneInput) {
        val n = input.ledgeCount
        while (ledges.size < n) ledges += Ledge()
        while (ledges.size > n) ledges.removeAt(ledges.size - 1)
        val m = min(w, h).toFloat()
        // widgets stay put on the glass while the render loop slides her with the pages: undo that slide
        val lagUv = viewLag * w / m
        for (i in 0 until n) {
            val o = i * 6
            val page = input.ledges[o]; val dx = page - input.pagePos
            val L = ledges[i]
            L.l = (input.ledges[o + 1] + dx - 0.5f) * w / m - lagUv; L.r = (input.ledges[o + 3] + dx - 0.5f) * w / m - lagUv
            L.t = (0.5f - input.ledges[o + 2]) * h / m; L.b = (0.5f - input.ledges[o + 4]) * h / m
            L.id = input.ledges[o + 5].toInt()
        }
    }

    private fun ledgeById(id: Int): Ledge? = ledges.firstOrNull { it.id == id }
    private fun visible(L: Ledge) = L.r > -halfW + 0.05f && L.l < halfW - 0.05f

    private fun charger(input: SceneInput, dt: Float) {
        val ch = input.charging
        if (!chargeKnown) { chargeKnown = true; wasCharging = ch; cableOut = if (ch) 1f else 0f; if (ch) placeCable() }
        if (ch && !wasCharging) {
            placeCable(); discover("charger")
            if (act != Act.CARRY && onLedge < 0) start(Act.NOTICE_CABLE, 0.9f)
        } else if (!ch && wasCharging) {
            if (act == Act.SLEEP || act == Act.PLAY_CABLE || act == Act.NOTICE_CABLE) { start(Act.STARTLE, 1.3f) }
        }
        wasCharging = ch
        cableOut += ((if (ch) 1f else 0f) - cableOut) * min(1f, dt * (if (ch) 2.2f else 3.5f))
        plug.step(0f, 60f, 5f, dt); plugY.step(0f, 60f, 5f, dt)
    }

    private fun placeCable() {
        // From the bottom edge, beside the cat, on whichever side has more room.
        val side = if (x > 0f) -1f else 1f
        cableX = (x + side * size * 0.55f).coerceIn(-halfW + 0.08f, halfW - 0.08f)
    }

    private fun behave(dt: Float, input: SceneInput) {
        val audio = input.audio
        when (act) {
            Act.NONE -> {
                nextIdle -= dt
                if (musicOn && audio.beat > 0.6f && grounded && onLedge < 0) { start(Act.DANCE); return }
                if (input.charging && cableOut > 0.9f && grounded && onLedge < 0 && Random.nextFloat() < dt * 0.15f) { start(Act.NOTICE_CABLE, 0.6f); return }
                if (nextIdle <= 0f) { nextIdle = TRICK_GAP_MIN + Random.nextFloat() * TRICK_GAP_RANGE; idleTrick(input) }
            }
            Act.LOOK -> if (actT > actDur) start(Act.NONE)
            Act.WALK -> {
                val d = targetX - x
                if (abs(d) < 0.012f && grounded) {
                    vx = 0f
                    val n = nextAfterWalk; nextAfterWalk = Act.NONE
                    if (n != Act.NONE) start(n, if (n == Act.PLAY_CABLE) 4.5f else 2.6f) else start(Act.NONE)
                }
                else {
                    facing = sign(d)
                    hopT += dt
                    if (grounded && hopT > 0.32f) { hopT = 0f; jump(x + sign(d) * min(abs(d), size * 0.28f), floorY, size * 0.06f) }
                }
            }
            Act.CHASE -> {
                if (!touching) { start(Act.NONE); return }
                val d = tX - x
                facing = if (abs(d) > 0.03f) sign(d) else facing
                hopT += dt
                if (grounded && abs(d) > size * 0.25f && hopT > 0.24f) { hopT = 0f; jump(x + sign(d) * min(abs(d) - size * 0.2f, size * 0.4f), floorY, size * 0.08f) }
                if (grounded && abs(d) < size * 0.5f && hypot(tX - x, tY - y) < size * 0.9f && actT > 0.6f && Random.nextFloat() < dt * 1.5f) {
                    // swat at the finger
                    sLegR.vel += 18f
                }
            }
            Act.POUNCE -> if (grounded && actT > 0.15f) {
                burst(9f, 4, x + facing * size * 0.25f, y + size * 0.1f, 1f, 0.92f, 0.55f)
                start(Act.LOOK, 0.9f)
            }
            Act.BOOP -> if (actT > actDur) start(Act.NONE)
            Act.PURR -> {
                if (Random.nextFloat() < dt * 3f) float1(2f, headX + (Random.nextFloat() - 0.5f) * size * 0.4f, headY + size * 0.25f, 1f, 0.45f, 0.62f, 0.03f)
                if (!touching && actDur > 0f && actT > actDur) start(Act.NONE)
            }
            Act.CARRY -> {
                if (!touching) { start(Act.FALL) } else {
                    // dangle from the finger with a little pendulum
                    val tx = tX + grabDx * 0.3f; val ty = tY - size * 0.95f
                    vx = (tx - x) / max(dt, 1e-3f) * 0.25f; vy = 0f
                    x += (tx - x) * min(1f, dt * 14f); y += (ty - y) * min(1f, dt * 14f)
                    grounded = false; onLedge = -1
                }
            }
            Act.FALL -> if (grounded) { if (abs(vx) > 0.4f || lastFallHeight > size * 1.2f) start(Act.DIZZY, 1.6f) else start(Act.LOOK, 0.6f) }
            Act.TUMBLE -> if (grounded && actT > 0.4f) { start(Act.DIZZY, 1.8f) }
            Act.DIZZY -> {
                if (Random.nextFloat() < dt * 4f) float1(3f, headX + cos(time * 6f) * size * 0.3f, headY + size * 0.3f, 1f, 0.9f, 0.4f, 0.03f, 0.8f)
                if (actT > actDur) start(Act.NONE)
            }
            Act.STUMBLE -> if (actT > actDur) start(Act.NONE)
            Act.NOTICE_CABLE -> {
                lookAt(cableX, floorY + size * 0.2f)
                if (actT > actDur) {
                    val side = sign(cableX - x)
                    targetX = cableX - side * size * 0.32f
                    if (abs(targetX - x) > size * 0.15f) { start(Act.WALK); nextAfterWalk = Act.PLAY_CABLE } else start(Act.PLAY_CABLE, 4.5f)
                }
            }
            Act.PLAY_CABLE -> {
                facing = sign(cableX - x)
                lookAt(cableX, floorY + size * 0.25f)
                if (cableOut < 0.2f) { start(Act.NONE); return }
                // bat the plug: alternate paws, the plug jiggles
                val beat = (actT * 2.6f) % 1f
                if (beat < dt * 2.6f) {
                    if (Random.nextBoolean()) sLegL.vel += 16f else sLegR.vel += 16f
                    plug.vel += facing * 2.5f; plugY.vel += 2f
                    if (Random.nextFloat() < 0.5f) burst(9f, 1, cableX, floorY + size * 0.3f, 1f, 0.95f, 0.5f, 0.5f, 0.025f)
                }
                if (actT > actDur) start(Act.SLEEP, 0f)
            }
            Act.SLEEP -> {
                eyesShut = 1f
                sDim.target = 0.15f
                if (Random.nextFloat() < dt * 0.7f) float1(4f, headX + size * 0.2f, headY + size * 0.2f, 0.85f, 0.9f, 1f, 0.03f, 2.2f)
                if (actDur > 0f && actT > actDur) wake()
            }
            Act.STARTLE -> {
                if (actT < dt * 1.5f) { jump(x - facing * size * 0.15f, floorY, size * 0.35f); burst(8f, 1, headX, headY + size * 0.35f, 1f, 1f, 1f, 0.2f, 0.06f) }
                eyesShut = 0f
                if (actT > actDur && grounded) start(Act.LOOK, 0.8f)
            }
            Act.DANCE -> {
                if (!musicOn) { start(Act.NONE); return }
                if (audio.beat > 0.8f && time - lastBeat > 0.25f) {
                    lastBeat = time
                    if (Random.nextBoolean()) sLegL.vel += 14f else sLegR.vel += 14f
                    sSqY.vel -= 3f
                    if (Random.nextFloat() < 0.4f) float1(5f, headX + (Random.nextFloat() - 0.5f) * size * 0.6f, headY + size * 0.3f,
                        0.6f + Random.nextFloat() * 0.4f, 0.5f + Random.nextFloat() * 0.5f, 1f, 0.035f)
                }
                if (actT > 12f) start(Act.NONE)
            }
            Act.PERCH_JUMP -> {
                if (grounded) {
                    val L = ledgeById(ledgeTarget)
                    if (L != null && abs(y - L.t) < 0.02f) { onLedge = L.id; ledgeDx = x - L.l; lastLedgeX = Float.NaN; start(Act.PERCHED, 5f + Random.nextFloat() * 5f); discover("perch") }
                    else start(Act.NONE)
                }
            }
            Act.PERCHED -> {
                val L = ledgeById(onLedge)
                if (L == null) { fallOff(); return }
                if (!visible(L)) { fallOff(); return }
                if (actT > actDur && !touching) {
                    val next = ledges.filter { it.id != L.id && visible(it) && abs(it.t - L.t) < size * 1.5f &&
                        it.t < halfH - size * 1.05f && it.r - it.l > size * 0.7f &&
                        min(abs(it.l - x), abs(it.r - x)) < size * 2.6f }
                    if (next.isNotEmpty() && Random.nextFloat() < 0.55f) {
                        // leap to the next widget
                        val N = next.random(); ledgeTarget = N.id
                        start(Act.PERCH_JUMP); jump(x.coerceIn(N.l + size * 0.3f, N.r - size * 0.3f), N.t, size * 0.3f)
                        discover("widget hop"); return
                    }
                    // hop down to the floor
                    start(Act.JUMP_DOWN)
                    val tx = (x + (if (x > 0f) -1 else 1) * size * 0.4f).coerceIn(-halfW + size * 0.45f, halfW - size * 0.45f)
                    jump(tx, floorY, size * 0.2f)
                } else {
                    if (Random.nextFloat() < dt * 0.4f) lookAt(x + (Random.nextFloat() - 0.5f) * 1.5f, y - size)
                    // the page sliding under her makes her wobble for balance
                    if (!lastLedgeX.isNaN()) sRoll.vel -= (L.l - lastLedgeX) * 5f
                    lastLedgeX = L.l
                }
            }
            Act.JUMP_DOWN -> if (grounded) { start(Act.LOOK, 0.6f) }
            Act.PEEK, Act.REACH -> if (actT > actDur) start(Act.NONE)
            Act.HANG_JUMP -> if (grounded) start(Act.LOOK, 0.6f)          // missed the edge
            Act.HANG -> {
                val L = ledgeById(hangOn)
                if (L == null || !visible(L)) { letGo(true); return }
                // little pull-ups and kicks, and a swing when the page moves
                if (Random.nextFloat() < dt * 0.8f) { sSqY.vel += 4f; sLegL.vel -= 6f; sLegR.vel -= 6f }
                if (!lastLedgeX.isNaN()) sRoll.vel -= (L.l - lastLedgeX) * 8f
                lastLedgeX = L.l
                if (actT > actDur && !touching) letGo(false)
            }
            Act.CLING -> {
                if (Random.nextFloat() < dt * 5f) burst(9f, 1, x + clingSide * size * 0.3f, y + size * 0.7f, 1f, 1f, 1f, 0.3f, 0.018f)
                if (grounded) { start(Act.DIZZY, 1.3f) }
            }
            Act.ZOOM -> {
                val d = targetX - x
                if (abs(d) < size * 0.12f && grounded) {
                    zoomLaps--
                    if (zoomLaps <= 0) { start(Act.DIZZY, 1.2f); return }
                    targetX = -targetX                                            // skid and turn around
                    sSqY.vel -= 4f; sPitch.vel -= 3f
                    burst(9f, 2, x, y + size * 0.1f, 1f, 1f, 1f, 0.5f, 0.022f)
                }
                facing = sign(targetX - x)
                hopT += dt
                if (grounded && hopT > 0.14f) { hopT = 0f; jump(x + sign(d) * min(abs(d), size * 0.6f), floorY, size * 0.1f) }
                if (Random.nextFloat() < dt * 10f) float1(9f, x - facing * size * 0.35f, y + size * (0.2f + Random.nextFloat() * 0.4f), 1f, 0.95f, 0.65f, 0.02f, 0.5f)
                if (actT > 8f) start(Act.DIZZY, 1f)
            }
            Act.BELLY -> {
                if (Random.nextFloat() < dt * 2.5f) float1(2f, x + (Random.nextFloat() - 0.5f) * size * 0.6f, y + size * 0.45f, 1f, 0.45f, 0.62f, 0.032f)
                if (touching) actDur = max(actDur, actT + 0.5f)
                if (actDur > 0f && actT > actDur && !touching) { start(Act.LOOK, 0.8f); sSqY.vel -= 5f }
                if (!touching && actDur == 0f) actDur = actT + 1.6f
            }
            Act.SPIN -> {
                spin += dt * 9f * (1f - actT / max(actDur, 0.1f)).coerceAtLeast(0.25f)
                if (grounded && actT < actDur - 0.2f && (actT * 3f) % 1f < dt * 3f) jump(x, floorY, size * 0.07f)
                if (Random.nextFloat() < dt * 6f) burst(9f, 1, x, y + size * 0.4f, 1f, 0.9f, 0.6f, 0.6f, 0.02f)
                if (actT > actDur) { spin = 0f; start(Act.DIZZY, 1.2f) }
            }
            Act.WAVE, Act.GROOM, Act.STRETCH, Act.YAWN, Act.WIGGLE -> if (actT > actDur) start(Act.NONE)
            Act.POUNCE_BUG -> {
                if (actT > 0.9f && actT - dt <= 0.9f) jump(x + facing * size * 0.35f, floorY, size * 0.25f)
                if (actT > 1.3f && grounded) { burst(9f, 3, x + facing * size * 0.25f, y + size * 0.1f, 0.7f, 1f, 0.6f); start(Act.LOOK, 0.6f) }
            }
            Act.HOP_PLAY -> { if (grounded && actT < actDur && hopT <= 0f) { hopT = 0.35f; jump(x, floorY, size * 0.12f) }; hopT -= dt; if (actT > actDur) start(Act.NONE) }
        }
    }
    private var nextAfterWalk = Act.NONE
    private var lastFallHeight = 0f
    private var fallStartY = -99f

    private fun letGo(whoops: Boolean) {
        hangOn = -1; vy = 0f; vx = 0f; grounded = false
        start(Act.FALL); if (whoops) discover("whoops")
    }

    private fun fallOff() {
        onLedge = -1; grounded = false; vy = size * 0.2f; vx = 0f
        x = x.coerceIn(-halfW + size * 0.45f, halfW - size * 0.45f)
        start(Act.FALL); discover("whoops")
    }

    private fun idleTrick(input: SceneInput) {
        val night = mood == Mood.NIGHT
        val sleepy = night || (input.lowBattery && !input.charging)
        val onFloor = grounded && onLedge < 0
        val visibleLedges = ledges.filter { visible(it) && it.t > floorY + size * 0.4f && it.t < halfH - size * 1.05f }
        val roll = Random.nextFloat()
        when {
            onLedge >= 0 -> { start(Act.LOOK, 1.5f) }
            sleepy && roll < 0.35f -> start(Act.SLEEP, 8f + Random.nextFloat() * 6f)
            input.charging && cableOut > 0.9f && roll < 0.4f -> start(Act.NOTICE_CABLE, 0.6f)
            onFloor && visibleLedges.isNotEmpty() && roll < 0.45f -> playWithWidget(visibleLedges.random())
            roll < 0.55f -> start(listOf(Act.GROOM, Act.STRETCH, Act.WAVE, Act.POUNCE_BUG, Act.SPIN, Act.HOP_PLAY, Act.WIGGLE, Act.YAWN)
                .filter { it != lastAct }.random(), 2.2f)
            onFloor && roll < 0.85f -> {
                targetX = ((Random.nextFloat() - 0.5f) * 2f * (halfW - size * 0.45f))
                if (abs(targetX - x) > size * 0.3f) start(Act.WALK) else start(Act.LOOK, 1.5f)
            }
            else -> { start(Act.LOOK, 1.8f); lookAt(x + (Random.nextFloat() - 0.5f) * 2f, y + Random.nextFloat()) }
        }
    }

    private fun playWithWidget(L: Ledge) {
        ledgeTarget = L.id
        val r = Random.nextFloat() * 1.0f
        val cx = (L.l + L.r) / 2f
        when {
            // Jump up and sit on top of it.
            r < 0.5f && L.t - floorY < size * 2.4f -> {
                val tx = (x.coerceIn(L.l + size * 0.3f, L.r - size * 0.3f))
                start(Act.PERCH_JUMP); jump(tx, L.t, size * 0.3f)
            }
            // Leap up and hang from its bottom edge, back legs dangling.
            r < 0.7f && L.b - floorY > size * (HANG_REACH + 0.2f) && L.b - floorY < size * (HANG_REACH + 1.3f) -> {
                val tx = x.coerceIn(L.l + size * 0.3f, L.r - size * 0.3f)
                start(Act.HANG_JUMP)
                jump(tx, floorY, (L.b - size * HANG_REACH) - floorY)
            }
            // Sit under it and paw at its bottom edge.
            r < 0.85f && L.b > floorY + size * 0.95f && L.b < floorY + size * 1.6f -> {
                targetX = x.coerceIn(L.l + size * 0.2f, L.r - size * 0.2f)
                nextAfterWalk = Act.REACH
                if (abs(targetX - x) > 0.02f) start(Act.WALK) else start(Act.REACH, 2.5f)
            }
            // Hide behind its side and peek out.
            else -> {
                peekSide = if (x < cx) -1f else 1f
                targetX = (if (peekSide < 0) L.l + size * 0.08f else L.r - size * 0.08f).coerceIn(-halfW + size * 0.42f, halfW - size * 0.42f)
                if (L.b > floorY + size * 0.6f) { start(Act.LOOK, 1f); return }    // floating too high to hide behind
                nextAfterWalk = Act.PEEK
                start(Act.WALK)
            }
        }
    }

    private fun physics(dt: Float) {
        if (act == Act.CARRY) return
        if (hangOn >= 0) {
            val L = ledgeById(hangOn)
            if (L != null) { x = L.l + hangDx; y = L.b - size * HANG_REACH }
            return
        }
        if (act == Act.CLING) {
            // sliding slowly down the edge of the screen, claws squeaking
            y -= size * 0.32f * dt; vx = 0f; vy = 0f
            if (y <= floorY) { y = floorY; fallStartY = y; land() }
            return
        }
        // Riding a ledge: it moves with the home-screen page.
        if (onLedge >= 0) {
            val L = ledgeById(onLedge)
            if (L == null) { fallOff() } else {
                x = L.l + ledgeDx; y = L.t
                if (x < L.l - size * 0.1f || x > L.r + size * 0.1f) fallOff()
            }
            return
        }
        if (!grounded) {
            if (fallStartY < y) fallStartY = y
            val prevVy = vy
            vy -= GRAV * size * dt
            x += vx * dt; y += vy * dt
            // at the top of a hang jump: catch the widget's bottom edge
            if (act == Act.HANG_JUMP && prevVy > 0f && vy <= 0f) {
                val L = ledgeById(ledgeTarget)
                if (L != null && visible(L) && x > L.l + size * 0.15f && x < L.r - size * 0.15f) {
                    start(Act.HANG, 3.5f + Random.nextFloat() * 3f)
                    hangOn = L.id; hangDx = x - L.l; lastLedgeX = Float.NaN
                    vx = 0f; vy = 0f; y = L.b - size * HANG_REACH
                    sSqY.vel += 5f; discover("hang")
                    return
                }
            }
            roll += rollV * dt
            // land on a ledge top when falling through it
            if (vy < 0f) for (L in ledges) {
                if (x > L.l + size * 0.12f && x < L.r - size * 0.12f && y <= L.t && y - vy * dt >= L.t - 0.002f && visible(L)
                    && (act == Act.PERCH_JUMP || act == Act.FALL || act == Act.CARRY)) {
                    y = L.t; land(); onLedge = L.id; ledgeDx = x - L.l
                    lastLedgeX = Float.NaN
                    if (act != Act.PERCH_JUMP) start(Act.PERCHED, 6f)
                    return
                }
            }
            if (y <= floorY) { y = floorY; land() }
            val wall = halfW - size * 0.42f
            if (abs(x) > wall && act == Act.TUMBLE && y > floorY + size * 0.15f) {
                // slammed into the edge of the screen: grab on
                clingSide = sign(x); x = clingSide * wall
                roll = 0f; rollV = 0f; facing = clingSide
                start(Act.CLING); discover("cling")
                sSqX.vel -= 6f
                return
            }
            x = x.coerceIn(-wall, wall)
        } else {
            if (abs(vx) > 0f && act != Act.WALK && act != Act.CHASE && act != Act.ZOOM) { x += vx * dt; vx *= exp(-6f * dt); if (abs(vx) < 0.005f) vx = 0f }
            x = x.coerceIn(-halfW + size * 0.42f, halfW - size * 0.42f)
            if (abs(y - floorY) > 0.001f && onLedge < 0) { grounded = false; vy = 0f }
        }
    }

    private fun land() {
        lastFallHeight = fallStartY - y; fallStartY = -99f
        val hard = min(1f, lastFallHeight / size)
        grounded = true; vy = 0f
        if (act != Act.WALK && act != Act.CHASE && act != Act.ZOOM) vx *= 0.3f
        sSqY.vel -= 3f + hard * 7f; sSqX.vel += 2f + hard * 4f
        roll = 0f; rollV = 0f
        if (hard > 0.6f) burst(9f, 3, x, y + size * 0.05f, 1f, 1f, 1f, 0.6f, 0.025f)
    }

    // ---------------------------------------------------------------- pose

    private fun animate(dt: Float, input: SceneInput) {
        val t = time
        // gaze wanders when nothing interesting happens
        nextGlance -= dt
        if (nextGlance <= 0f && !touching) {
            nextGlance = 1.5f + Random.nextFloat() * 3f
            lookTX = (Random.nextFloat() - 0.5f) * 1.2f; lookTY = (Random.nextFloat() - 0.4f) * 0.6f
            if (Random.nextFloat() < 0.35f) { lookTX = 0f; lookTY = 0.1f }    // look at you
        }
        lookX += (lookTX - lookX) * min(1f, dt * 6f); lookY += (lookTY - lookY) * min(1f, dt * 6f)

        // blink
        nextBlink -= dt
        if (nextBlink <= 0f) { blink = 0f; nextBlink = 2.2f + Random.nextFloat() * 3.5f; if (Random.nextFloat() < 0.2f) nextBlink = 0.18f }
        blink = min(1f, blink + dt * 9f)

        // defaults (a sitting, breathing, curious cat)
        val breathe = sin(t * 2.1f) * 0.012f
        var yawT = facing * 0.75f * (if (grounded && act != Act.WALK && act != Act.CHASE && act != Act.ZOOM) 0.45f else 1f)
        var sink = 0f; var rollT = 0f
        var pitchT = 0f
        var sqY = 1f + breathe; var sqX = 1f - breathe * 0.5f
        var hy = lookX * 0.55f - yawT * 0.5f; var hp = lookY * 0.35f; var ht = sin(t * 0.7f) * 0.05f
        var earL = sin(t * 0.9f) * 0.05f; var earR = earL; var droop = 0f
        var legL = 0f; var legR = 0f; var sprL = 0f; var sprR = 0f
        var curl = 0.25f; var wag = 0.18f; var drp = 0f
        tailSpeed = 2f
        var shut = eyesShut
        var eyeS = 1f
        var glow = 0f

        if (!grounded && act != Act.CARRY && act != Act.HANG && act != Act.CLING) {
            // airborne: tuck or stretch depending on rising/falling
            val up = (vy / (size * 2.5f)).coerceIn(-1f, 1f)
            legL = 0.5f + up * 0.5f; legR = legL; sprL = 0.25f; sprR = 0.25f
            pitchT = -up * 0.15f; curl = 0.6f + up * 0.4f; wag = 0.1f
            sqY = 1f + up * 0.12f; sqX = 1f - up * 0.06f
            earL = -0.3f; earR = -0.3f
        }

        when (act) {
            Act.BOOP -> { shut = if (actT < 0.45f) 1f else 0f; ht = 0.25f * sin(actT * 9f) * (1f - actT / 0.7f); curl = 0.9f; glow = 0.5f; earL = 0.2f; earR = 0.2f }
            Act.PURR -> {
                shut = 1f; ht = 0.22f + sin(t * 1.4f) * 0.05f; hp = 0.15f; curl = 1.1f; wag = 0.08f; tailSpeed = 1f
                sqY += sin(t * 40f) * 0.006f; glow = 0.6f; earL = -0.15f; earR = -0.15f
                // lean into the finger
                hy = ((tX - x) / size * 0.4f).coerceIn(-0.4f, 0.4f)
            }
            Act.CARRY -> {
                legL = 0.15f + sin(t * 3f) * 0.1f; legR = 0.15f + sin(t * 3f + 1f) * 0.1f; sprL = 0.2f; sprR = 0.2f
                sqY = 1.12f; sqX = 0.93f; curl = -0.5f; drp = -0.6f; wag = 0.3f; tailSpeed = 4f
                droop = 0.35f; earL = -0.4f; earR = -0.4f; eyeS = 1.08f
                yawT = 0f; pitchT = (vx * 0.6f).coerceIn(-0.3f, 0.3f)
            }
            Act.FALL -> { legL = 1.8f + sin(t * 25f) * 0.4f; legR = 1.8f + sin(t * 25f + 2f) * 0.4f; sprL = 0.6f; sprR = 0.6f; eyeS = 1.15f; earL = -0.6f; earR = -0.6f; curl = 1.2f }
            Act.TUMBLE -> { eyeS = 1.1f; legL = 1.2f; legR = 1.2f; sprL = 0.7f; sprR = 0.7f; curl = 1.4f }
            Act.DIZZY -> {
                hy = sin(t * 7f) * 0.35f; ht = cos(t * 7f) * 0.25f; hp = 0.05f
                pitchT = sin(t * 3.5f) * 0.08f; droop = 0.3f; shut = 0f; eyeS = 0.95f
                if ((t * 2f) % 1f < 0.5f) shut = 0.65f
            }
            Act.STUMBLE -> {
                val k = 1f - actT / actDur
                pitchT = sin(actT * 9f) * 0.2f * k; sprL = 1.0f * k; sprR = 1.0f * k; legL = 0.9f * k; legR = 0.9f * k
                hy = sin(actT * 7f) * 0.3f * k; eyeS = 1.08f; earL = -0.3f * k; earR = -0.3f * k
            }
            Act.CHASE -> { pitchT = 0.15f; earL = 0.25f; earR = 0.25f; eyeS = 1.12f; wag = 0.45f; tailSpeed = 6f; curl = 0.1f; sqY -= 0.06f }
            Act.POUNCE -> { eyeS = 1.12f; earL = 0.3f; earR = 0.3f }
            Act.NOTICE_CABLE -> { earL = 0.35f; earR = 0.35f; eyeS = 1.12f; hp = -0.1f; curl = 0.9f; wag = 0.05f }
            Act.PLAY_CABLE -> { pitchT = 0.12f; hp = -0.3f; eyeS = 1.1f; wag = 0.5f; tailSpeed = 5f; earL = 0.25f; earR = 0.25f; sqY -= 0.05f }
            Act.SLEEP -> {
                shut = 1f; hp = -0.45f; ht = 0.3f; pitchT = 0.25f; sqY = 0.84f + sin(t * 1.3f) * 0.015f; sqX = 1.08f
                curl = -0.2f; drp = 0.9f; wag = 0.03f; tailSpeed = 0.5f; droop = 0.25f; legL = 0.35f; legR = 0.35f
                yawT = facing * 0.2f; hy = -yawT
            }
            Act.STARTLE -> { eyeS = 1.25f; earL = -0.7f; earR = -0.7f; droop = 0.5f; curl = 1.6f; sqX = 1.08f; legL = 0.6f; legR = 0.6f }
            Act.DANCE -> {
                val b = input.audio.beat
                hp = b * 0.18f - 0.05f; ht = sin(t * 4f) * 0.18f; hy = sin(t * 2f) * 0.25f
                sqY -= b * 0.05f; curl = 0.8f; wag = 0.5f; tailSpeed = 4f; shut = if (b > 0.85f) 0.9f else 0f
                earL = b * 0.2f; earR = earL
            }
            Act.WAVE -> {
                val k = min(1f, actT * 4f) * min(1f, (actDur - actT) * 4f).coerceAtLeast(0f)
                legR = 2.3f * k; sprR = (0.35f + sin(actT * 14f) * 0.35f) * k; ht = 0.2f * k; curl = 0.9f; shut = if (actT in 0.2f..0.6f) 0.8f else 0f
                hy = 0f; yawT *= 0.3f
            }
            Act.GROOM -> {
                val k = min(1f, actT * 3f) * min(1f, (actDur - actT) * 3f).coerceAtLeast(0f)
                legL = 1.9f * k + sin(actT * 10f) * 0.15f * k; sprL = -0.3f * k
                hp = -0.25f * k; hy = -0.3f * k; ht = -0.15f * k; shut = k
            }
            Act.STRETCH -> {
                val k = sin(min(1f, actT / actDur) * PI.toFloat())
                legL = 1.2f * k; legR = 1.2f * k; pitchT = 0.35f * k; hp = 0.2f * k; shut = k * 0.9f; curl = 1.3f * k; sqY = 1f - 0.08f * k; sqX = 1f + 0.06f * k
            }
            Act.YAWN -> { val k = sin(min(1f, actT / actDur) * PI.toFloat()); hp = 0.35f * k; shut = k; droop = 0.2f * k; eyeS = 1f }
            Act.WIGGLE -> { pitchT = 0.18f; hp = -0.15f; sqY -= 0.07f; eyeS = 1.15f; wag = 0.6f; tailSpeed = 9f; earL = 0.3f; earR = 0.3f
                facing = if (facing == 0f) 1f else facing; yawT = facing * 0.6f + sin(t * 18f) * 0.08f }
            Act.POUNCE_BUG -> {
                if (actT < 0.9f) { pitchT = 0.2f; sqY -= 0.1f; eyeS = 1.15f; wag = 0.6f; tailSpeed = 9f; yawT = facing * 0.5f + sin(t * 20f) * 0.06f }
            }
            Act.SPIN -> { eyeS = 1.1f; curl = 1f }
            Act.HOP_PLAY -> { eyeS = 1.1f; curl = 1f; wag = 0.4f }
            Act.PERCHED -> { curl = -0.6f; drp = 0.4f; wag = 0.3f; tailSpeed = 1.3f; hp = -0.2f + lookY * 0.3f; yawT *= 0.3f }
            Act.REACH -> {
                val k = min(1f, actT * 3f) * min(1f, (actDur - actT) * 3f).coerceAtLeast(0f)
                hp = 0.55f * k; legR = (2.6f + sin(actT * 9f) * 0.25f) * k; sprR = 0.2f * k; eyeS = 1.12f; earL = 0.3f; earR = 0.3f
                pitchT = -0.1f * k; yawT *= 0.2f
            }
            Act.PEEK -> {
                // body hidden behind the widget, head leans out and looks at you
                val k = min(1f, actT * 2.5f) * min(1f, (actDur - actT) * 2.5f).coerceAtLeast(0f)
                hy = -peekSide * 0.5f * k; ht = -peekSide * 0.35f * k; eyeS = 1.12f
                if (actT > actDur * 0.5f && actT - dt <= actDur * 0.5f) float1(2f, headX - peekSide * size * 0.3f, headY + size * 0.3f, 1f, 0.45f, 0.6f, 0.03f)
            }
            Act.HANG_JUMP -> { legL = 2.4f; legR = 2.4f; sprL = 0.15f; sprR = 0.15f; eyeS = 1.15f; earL = 0.3f; earR = 0.3f; hp = 0.45f; yawT = 0f }
            Act.HANG -> {
                // front paws over her head on the edge, body stretched, tail swinging for balance
                legL = 2.95f + sin(t * 3f) * 0.05f; legR = 2.95f + sin(t * 3f + 1.5f) * 0.05f; sprL = 0.12f; sprR = 0.12f
                sqY = 1.1f + sin(t * 2.2f) * 0.015f; sqX = 0.94f
                hp = 0.15f + lookY * 0.2f; eyeS = 1.12f; earL = 0.15f; earR = 0.15f
                curl = -0.6f; drp = -0.4f; wag = 0.5f; tailSpeed = 3f
                yawT = 0f; rollT = sin(t * 2.4f) * 0.06f
                if (actT > actDur - 0.6f) { shut = 0f; eyeS = 1.2f; earL = -0.3f; earR = -0.3f }     // about to let go
            }
            Act.CLING -> {
                // spread-eagled against the screen edge, looking at you like it's your fault
                legL = 2.5f; legR = 2.2f; sprL = 0.5f; sprR = 0.5f
                yawT = clingSide * 1.1f; hy = -clingSide * 0.75f; hp = 0.1f; ht = -clingSide * 0.15f
                eyeS = 1.22f; earL = -0.55f; earR = -0.55f; droop = 0.4f; curl = 1.3f; wag = 0.25f; tailSpeed = 7f
                rollT = -clingSide * 0.18f; sqY = 1.06f; sqX = 0.95f
            }
            Act.ZOOM -> {
                pitchT = 0.28f; earL = -0.45f; earR = -0.45f; eyeS = 1.22f; curl = 0.05f; wag = 0.15f; tailSpeed = 11f
                hy = 0f; hp = 0.1f; droop = 0.2f; sqY -= 0.04f
                legL = 0.9f + sin(t * 26f) * 0.7f; legR = 0.9f + sin(t * 26f + 3.1f) * 0.7f
            }
            Act.BELLY -> {
                // flopped on her side, paws up and kneading the air, blissed out
                val k = min(1f, actT * 3f)
                val side = if (facing == 0f) 1f else facing
                rollT = side * 1.35f * k; sink = -0.2f * k
                legL = (1.7f + sin(t * 5f) * 0.35f) * k; legR = (1.7f + sin(t * 5f + 2f) * 0.35f) * k; sprL = 0.35f * k; sprR = 0.35f * k
                shut = 0.85f; glow = 0.75f; hp = 0.25f; ht = side * 0.25f; hy = 0f; yawT = 0f
                curl = 0.4f + sin(t * 1.2f) * 0.15f; wag = 0.25f; tailSpeed = 1.5f; earL = -0.2f; earR = -0.2f
                sqY = 1f + sin(t * 1.6f) * 0.02f
            }
            else -> {}
        }

        // eyes: blink overrides when open
        val open = (1f - shut) * blink + 0f
        val eyeOpen = max(0.08f, open)
        // music sparkle: ears and head bob even when not dancing
        if (musicOn && act != Act.DANCE && act != Act.SLEEP) { hp += input.audio.beat * 0.06f; earL += input.audio.beat * 0.1f; earR = earL }
        // turn toward you when standing still on the floor
        val stiff = if (act == Act.CARRY) 60f else 120f
        sYaw.step(yawT + spin, stiff, 14f, dt); sPitch.step(pitchT, 140f, 13f, dt)
        sSqY.step(sqY, 260f, 11f, dt); sSqX.step(sqX, 260f, 11f, dt)
        sHY.step(hy, 160f, 15f, dt); sHP.step(hp, 160f, 15f, dt); sHT.step(ht, 120f, 12f, dt)
        sEarL.step(earL, 300f, 10f, dt); sEarR.step(earR, 300f, 10f, dt); sDroop.step(droop, 200f, 12f, dt)
        sLegL.step(legL, 260f, 14f, dt); sLegR.step(legR, 260f, 14f, dt); sSprL.step(sprL, 200f, 14f, dt); sSprR.step(sprR, 200f, 14f, dt)
        sCurl.step(curl, 60f, 7f, dt); sWag.step(wag, 40f, 8f, dt); sDrp.step(drp, 50f, 8f, dt)
        sEye.step(eyeS, 260f, 12f, dt); sGlow.step(glow, 40f, 9f, dt); sDim.step(sDim.target, 30f, 9f, dt)
        sSink.step(sink, 90f, 12f, dt); sRoll.step(rollT, 70f, 7f, dt)
        if (act != Act.SPIN && abs(spin) > 0.01f) {
            // an interrupted tail chase: drop the whole turns (invisible) and let the spring settle the rest
            val turns = Math.round(spin / (2f * PI.toFloat())) * 2f * PI.toFloat()
            sYaw.v -= turns; spin = 0f
        }
        tailPhase += dt * tailSpeed

        // random ear twitches
        if (Random.nextFloat() < dt * 0.25f) { if (Random.nextBoolean()) sEarL.vel -= 14f else sEarR.vel -= 14f }

        pose.yaw = sYaw.v; pose.pitch = sPitch.v; pose.squashY = sSqY.v; pose.squashX = sSqX.v
        pose.headYaw = sHY.v; pose.headPitch = sHP.v; pose.headTilt = sHT.v
        pose.blinkL = eyeOpen; pose.blinkR = eyeOpen; pose.eyeScale = sEye.v
        pose.earFlickL = sEarL.v; pose.earFlickR = sEarR.v; pose.earDroop = sDroop.v
        pose.legSwingL = sLegL.v; pose.legSwingR = sLegR.v; pose.legSpreadL = sSprL.v; pose.legSpreadR = sSprR.v
        pose.tailCurl = sCurl.v; pose.tailWag = sWag.v; pose.tailPhase = tailPhase; pose.tailDroop = sDrp.v
        pose.glow = sGlow.v; pose.dim = sDim.v
    }

    private fun particles(dt: Float) {
        val it = parts.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life += dt
            if (p.life > p.max) { it.remove(); continue }
            p.x += p.vx * dt; p.y += p.vy * dt; p.vy -= (if (p.kind == 9f) 0.6f else 0.02f) * dt; p.rot += p.spin * dt
            p.vx *= exp(-1.5f * dt)
        }
    }

    // ---------------------------------------------------------------- draw

    override fun draw(input: SceneInput) {
        val m = min(w, h)
        val k = size / kit.height                       // uv per model unit
        val boxHalf = kit.boxR * k
        // a sprite a little sharper than the screen, capped for the GPU's sake
        val px = (boxHalf * 2f * m * 0.9f).toInt()
        kit.ensureSprite(px)
        kit.render(pose, pal)

        kit.beginFx()
        // charging cable behind the cat
        if (cableOut > 0.01f) drawCable()
        // soft contact shadow, smaller when she is in the air
        val air = if (onLedge >= 0) 0f else ((y - floorY) / size).coerceIn(0f, 2f)
        if (onLedge < 0) kit.quad(1f, x, floorY + 0.004f, size * 0.42f * (1f - air * 0.3f), size * 0.07f, 0f, 0f, 0f, 0f, 1f - min(0.8f, air * 0.6f))
        else ledgeById(onLedge)?.let { kit.quad(1f, x, it.t + 0.004f, size * 0.38f, size * 0.06f, 0f, 0f, 0f, 0f, 0.8f) }
        // the cat (box centre sits half a height above the feet)
        val cy = y + (kit.boxCz - kit.floorZ) * k + sSink.v * size
        kit.sprite(x, cy, boxHalf, roll + sRoll.v, 1f)
        // effects in front
        for (p in parts) {
            val a = (min(1f, p.life * 6f) * min(1f, (p.max - p.life) * 2.5f)).coerceIn(0f, 1f)
            val s = p.s * (0.6f + 0.4f * min(1f, p.life * 4f))
            kit.quad(p.kind, p.x, p.y, s, s, p.rot, p.r, p.g, p.b, a)
        }
        // a pulsing bolt over the plug while charging
        if (cableOut > 0.6f && input.charging) {
            val pulse = 0.6f + 0.4f * sin(time * 4f)
            kit.quad(6f, cablePts[22], cablePts[23] + size * 0.13f, size * 0.05f, size * 0.07f, 0f, 1f, 0.85f, 0.25f, cableOut * pulse)
        }
        kit.endFx()
    }

    private fun drawCable() {
        // From below the screen up to a plug that rests near the cat (and jiggles when batted).
        val bottom = -halfH - 0.05f
        val tipX = cableX + plug.v * size * 0.05f
        val tipY = floorY + size * 0.1f + plugY.v * size * 0.04f
        val lift = (tipY - bottom) * cableOut
        val ty = bottom + lift
        for (i in 0 until 12) {
            val t = i / 11f
            val sway = sin(time * 1.3f + t * 3f) * 0.004f
            // a lazy S curve
            val bx = tipX + (1f - t) * (1f - t) * size * 0.35f * (if (cableX > 0f) 1f else -1f) + sway
            val by = bottom + (ty - bottom) * (t * t * (3f - 2f * t))
            cablePts[i * 2] = bx; cablePts[i * 2 + 1] = by
        }
        kit.cable(cablePts, size * 0.018f)
    }

    // ---------------------------------------------------------------- springs

    private class Spring(var v: Float = 0f) {
        var vel = 0f
        var target = 0f
        fun step(t: Float, k: Float, d: Float, dt: Float) {
            target = t
            var rem = dt
            while (rem > 0f) {
                val h = min(rem, 1f / 120f)
                vel += ((t - v) * k - vel * d) * h
                v += vel * h
                rem -= h
            }
        }
    }

    companion object {
        const val GRAV = 9f
        /** Feet-to-raised-paws height, in cat heights: where the edge is when she hangs. */
        const val HANG_REACH = 1.12f
    }
}
