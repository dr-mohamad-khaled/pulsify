package app.pulsify.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/** What [StatueScene] needs from the procedural animator of a rigged super wallpaper. */
interface StatueRig {
    val bones: Int
    /** Bone matrices for the shader: 3 rows of vec4 per bone. */
    val mats: FloatArray
    fun setRig(par: IntArray, heads: FloatArray)
    fun clear()
    /** A page swipe ([dir] -1 / 1). */
    fun kick(dir: Float)
    /** Where the camera will come to rest (radians around the model, 0 = its face). */
    fun rest(az: Float) {}
    /** Where the camera will settle: angle (radians) and distance. Perched birds walk towards a far camera. */
    fun rest(az: Float, dist: Float) = rest(az)
    /** A finger on the screen ([x], [y]: -0.5..0.5 from the centre, y down); [down] false when it lifts. */
    fun touch(x: Float, y: Float, down: Boolean) {}
    fun update(dt: Float, time: Float, musicBass: Float)
    /** [camAz]: where the camera is right now (radians). */
    fun pose(camAz: Float)
}

/**
 * The crow (Midnight Sentinel): a living bird on the spot, never a statue. Wings always stay folded.
 * Bones (conv/glb2sta2.py --crow): 0 body, 1 spine, 2 chest, 3 breast, 4-5 neck, 6 head, 7 jaw,
 * 8-10 tail, 11-13 left leg (thigh, shin, foot), 14-16 right leg.
 *
 * Idle: it breathes, the head moves the way a bird's does (quick snaps, then holds), it looks at you
 * most of the time and glances around, cocks its head, peers at the ground, caws, shakes its feathers.
 * A swipe startles it: the head snaps to the new camera at once. The crow then hops round in one to
 * three short hops (crouch, spring, land with a tail bob) until it faces the camera again; the parrots
 * walk on their legs instead, closer to you when the camera settles further away. With music the
 * head bobs to the bass.
 */
class StatueCrow : StatueRig {
    /** Parrots (the budgie) chirp and bob their heads instead of cawing, walk instead of hopping, and keep head turns small. */
    var parrot = false
    /** How far the head and neck may turn (1 = the crow); the budgie's smaller head would warp at full size. */
    private val headAmp get() = if (parrot) 0.45f else 1f
    override var bones = 0; private set
    private var parent = IntArray(0)
    private var head = FloatArray(0)
    override var mats = FloatArray(0); private set
    private var rot = FloatArray(0)
    private var world = arrayOf<FloatArray>()
    private var dyBody = 0f

    private var t = 0f
    private var bass = 0f
    private val rnd = java.util.Random(7)
    private fun r(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)

    // ---- facing: where the body points, where it wants to point ----
    private var yaw = Float.NaN
    private var restAz = 0f
    // ---- walking: where it stands (model units, on the ground plane) and where it is going ----
    /** Camera distance at which it stays on the spot; further away it walks up to [WALK_MAX] towards the camera. */
    var walkNear = 2.7f
    private var px = 0f; private var pz = 0f
    private var goX = 0f; private var goZ = 0f
    private enum class Leg { IDLE, TURN_TO_PATH, WALK, TURN_TO_YOU }
    private var leg = Leg.IDLE
    private var legDelay = 0f
    private var gait = 0f; private var gaitAmp = 0f
    // ---- the crow's hop-turns ----
    private var hopFrom = 0f; private var hopTo = 0f; private var hopT = -1f; private var hopsLeft = 0; private var hopDelay = 0f
    private var landBob = 0f

    // ---- gaze (world yaw / pitch, roll), bird style: snaps then holds ----
    private var gY = 0f; private var gP = 0f; private var gR = 0f
    private var tgY = 0f; private var tgP = 0f; private var tgR = 0f
    private var snap = 20f
    private var holdT = 1f
    private var glances = 0
    private var camAzNow = 0f

    // ---- idle actions ----
    private enum class Act { NONE, CAW, PEER, SHAKE, TILT, CHIRP, BOB }
    private var act = Act.NONE; private var actT = 0f; private var actLen = 0f
    private var nextAct = 3.5f
    private var startle = 0f
    // a finger on the screen: the head turns a little towards it
    private var touching = false; private var touchX = 0f; private var touchY = 0f
    override fun touch(x: Float, y: Float, down: Boolean) {
        if (down && !touching) holdT = 0f
        touching = down; if (down) { touchX = x; touchY = y }
        if (!down) holdT = 0.4f
    }

    override fun setRig(par: IntArray, heads: FloatArray) {
        bones = par.size; parent = par; head = heads
        mats = FloatArray(bones * 12); rot = FloatArray(bones * 3)
        world = Array(bones) { FloatArray(12) }
        yaw = Float.NaN
    }

    override fun clear() { bones = 0 }

    override fun kick(dir: Float) {
        if (bones == 0) return
        startle = 1f
        // the head finds the new camera at once; the legs follow
        act = Act.NONE
        if (parrot) legDelay = 0.3f else hopDelay = 0.28f
    }

    override fun rest(az: Float) = rest(az, walkNear)

    override fun rest(az: Float, dist: Float) {
        if (!parrot) {
            // the crow stays on its spot and hops round to face you
            if (abs(wrap(az - restAz)) < 1e-3f) return
            restAz = az
            if (yaw.isNaN()) { yaw = az; gY = az; tgY = az; return }
            tgY = az; tgP = 0f; tgR = 0f; snap = 26f; holdT = r(1.2f, 2f); glances = 0
            if (hopDelay <= 0f) hopDelay = 0.35f
            hopsLeft = 0; planHops()
            return
        }
        if (abs(wrap(az - restAz)) < 1e-3f && leg != Leg.IDLE) return
        val k = ((dist - walkNear) * 0.45f).coerceIn(0f, WALK_MAX)
        val nx = sin(az) * k; val nz = cos(az) * k
        if (abs(wrap(az - restAz)) < 1e-3f && abs(nx - goX) + abs(nz - goZ) < 0.01f) return
        restAz = az; goX = nx; goZ = nz
        if (yaw.isNaN()) { yaw = az; gY = az; tgY = az; px = nx; pz = nz; return }
        tgY = az; tgP = 0f; tgR = 0f; snap = 26f; holdT = r(1.2f, 2f); glances = 0
        if (legDelay <= 0f) legDelay = 0.35f
        leg = if (abs(goX - px) + abs(goZ - pz) > 0.04f) Leg.TURN_TO_PATH else Leg.TURN_TO_YOU
    }

    private fun planHops() {
        val d = wrap(restAz - (if (hopT >= 0f) hopTo else yaw))
        hopsLeft = if (abs(d) < 0.2f) 0 else kotlin.math.ceil(abs(d) / 1.35f).toInt().coerceIn(1, 3)
    }

    override fun update(dt: Float, time: Float, musicBass: Float) {
        t = time
        bass += (musicBass - bass) * min(1f, dt * 14f)
        if (bones == 0) return
        if (yaw.isNaN()) { yaw = restAz; gY = restAz; tgY = restAz }
        startle = max(0f, startle - dt / 0.9f)

        // the crow's hop-turns
        landBob = max(0f, landBob - dt / 0.55f)
        if (hopDelay > 0f) hopDelay -= dt
        if (hopT >= 0f) {
            hopT += dt / HOP
            if (hopT >= 1f) { yaw = hopTo; hopT = -1f; landBob = 1f; planHops(); hopDelay = if (hopsLeft > 0) 0.08f else 0f }
        } else if (hopsLeft > 0 && hopDelay <= 0f) {
            // split what is left evenly over the hops still to come
            val d = wrap(restAz - yaw)
            hopFrom = yaw; hopTo = yaw + sign(d) * min(abs(d) / hopsLeft, 1.35f)
            hopT = 0f; hopsLeft--
        }
        if (hopT >= 0f) yaw = hopFrom + (hopTo - hopFrom) * smooth(((hopT - 0.22f) / 0.56f).coerceIn(0f, 1f))

        // walking (parrots): turn to the path, walk, turn to face you (always stepping while the body turns)
        if (legDelay > 0f) legDelay -= dt
        var stepping = false
        if (leg != Leg.IDLE && legDelay <= 0f) {
            stepping = true
            when (leg) {
                Leg.TURN_TO_PATH, Leg.TURN_TO_YOU -> {
                    val want = if (leg == Leg.TURN_TO_PATH) atan2(goX - px, goZ - pz) else restAz
                    val d = wrap(want - yaw)
                    val stepA = TURN_RATE * dt
                    if (abs(d) <= stepA) { yaw += d; leg = if (leg == Leg.TURN_TO_PATH) Leg.WALK else Leg.IDLE }
                    else yaw += sign(d) * stepA
                }
                Leg.WALK -> {
                    val dx = goX - px; val dz = goZ - pz; val dl = kotlin.math.sqrt(dx * dx + dz * dz)
                    val v = WALK_SPEED * min(1f, gaitAmp + 0.2f) * dt
                    if (dl <= v) { px = goX; pz = goZ; leg = Leg.TURN_TO_YOU }
                    else {
                        px += dx / dl * v; pz += dz / dl * v
                        yaw += wrap(atan2(dx, dz) - yaw) * min(1f, dt * 6f)
                    }
                }
                Leg.IDLE -> {}
            }
        }
        gaitAmp += ((if (stepping) 1f else 0f) - gaitAmp) * min(1f, dt * 7f)
        if (gaitAmp > 0.01f) gait += dt * 2f * PI.toFloat() * STEP_HZ else gait = 0f

        // gaze: hold, then snap to a new point
        holdT -= dt
        val moving = leg != Leg.IDLE || hopT >= 0f || hopsLeft > 0 || startle > 0.4f
        if (moving) { tgY = restAz; tgP = 0f; tgR = 0f; snap = 26f }
        else if (touching && act == Act.NONE) {
            // subtle: at most ~17 degrees sideways and ~11 up/down, spread over the neck
            tgY = restAz + touchX * 0.6f; tgP = touchY * 0.4f; tgR = -touchX * 0.1f; snap = 10f; glances = 0
        } else if (act == Act.NONE && holdT <= 0f) {
            if (glances > 0) {
                glances--
                tgY = restAz + r(-1.3f, 1.3f); tgP = r(-0.25f, 0.35f); tgR = r(-0.15f, 0.15f); snap = r(16f, 26f)
                holdT = r(0.5f, 1.6f)
            } else {
                // back to you, with a small re-adjust now and then (a bird's head is never quite still)
                tgY = restAz + r(-0.1f, 0.1f); tgP = r(-0.06f, 0.08f); tgR = if (rnd.nextFloat() < 0.25f) r(-0.3f, 0.3f) else r(-0.06f, 0.06f)
                snap = r(14f, 24f); holdT = r(0.7f, 1.8f)
                if (rnd.nextFloat() < 0.22f) glances = 1 + rnd.nextInt(3)
            }
        }
        val k = 1f - exp(-snap * dt)
        gY += wrap(tgY - gY) * k; gP += (tgP - gP) * k; gR += (tgR - gR) * k

        // idle actions
        if (act != Act.NONE) {
            actT += dt
            if (actT >= actLen) { act = Act.NONE; nextAct = r(3f, 7f); holdT = 0f }
        } else if (!moving) {
            nextAct -= dt
            if (nextAct <= 0f) {
                val n = rnd.nextInt(10)
                // (no feather shake: Dr. Khaled took it out)
                act = if (parrot) when (n) { 0, 1, 2 -> Act.CHIRP; 3, 4, 5 -> Act.BOB; 6, 7 -> Act.PEER; else -> Act.TILT }
                      else when (n) { 0, 1, 2, 3 -> Act.CAW; 4, 5, 6 -> Act.PEER; else -> Act.TILT }
                actT = 0f
                actLen = when (act) { Act.CAW -> 1.9f; Act.PEER -> 1.7f; Act.TILT -> 1.5f; Act.SHAKE -> 0.75f; Act.CHIRP -> 1.2f; Act.BOB -> 1.3f; Act.NONE -> 0f }
                tgY = restAz; tgP = 0f; tgR = 0f; snap = 22f; glances = 0
            }
        }
    }

    private fun setRot(i: Int, x: Float, y: Float, z: Float) { if (i < bones) { rot[i * 3] += x; rot[i * 3 + 1] += y; rot[i * 3 + 2] += z } }

    override fun pose(camAz: Float) {
        if (bones == 0) return
        camAzNow = camAz
        java.util.Arrays.fill(rot, 0f)
        val by = if (yaw.isNaN()) 0f else yaw
        dyBody = 0f

        // breathing and a slow weight shift
        val br = sin(t * 2.3f)
        setRot(2, 0.012f * br, 0f, 0f); setRot(3, -0.02f * br, 0f, 0f)
        setRot(0, 0f, 0f, 0.025f * sin(t * 0.37f))
        for (k in 0 until 3) setRot(8 + k, 0.02f * sin(t * 0.9f - k * 0.5f), 0.025f * sin(t * 0.43f + k * 0.7f), 0f)

        // ---- walking: legs step in turn, the body sways over the standing leg, the head bobs ----
        var walkBob = 0f
        if (gaitAmp > 0.01f) {
            val g = gait; val a = gaitAmp; val sg = sin(g)
            val liftL = max(0f, sg) * a; val liftR = max(0f, -sg) * a
            setRot(11, -0.2f * sg * a - 0.12f * liftL, 0f, 0f); setRot(12, 0.32f * liftL, 0f, 0f); setRot(13, -0.25f * liftL, 0f, 0f)
            setRot(14, 0.2f * sg * a - 0.12f * liftR, 0f, 0f); setRot(15, 0.32f * liftR, 0f, 0f); setRot(16, -0.25f * liftR, 0f, 0f)
            dyBody += 0.012f * a * abs(sin(2f * g))
            setRot(0, 0.04f * a, 0f, 0.05f * a * sg)
            setRot(8, 0.04f * a * sin(2f * g), 0f, 0f)
            walkBob = 0.08f * a * sin(2f * g)
        }
        // ---- hop: crouch, spring, land ----
        if (hopT >= 0f) {
            val u = hopT
            val crouch = if (u < 0.22f) smooth(u / 0.22f) else if (u < 0.32f) 1f - smooth((u - 0.22f) / 0.1f) else 0f
            val air = if (u in 0.22f..0.78f) ((u - 0.22f) / 0.56f).let { 4f * it * (1f - it) } else 0f
            val land = if (u > 0.78f) sin(PI.toFloat() * (u - 0.78f) / 0.22f) else 0f
            dyBody += -0.055f * crouch + 0.13f * air - 0.04f * land
            setRot(0, 0.12f * crouch - 0.05f * air + 0.06f * land, 0f, 0f)
            val bend = crouch + land * 0.7f
            for (s in intArrayOf(11, 14)) { setRot(s, 0.22f * bend - 0.1f * air, 0f, 0f); setRot(s + 1, -0.35f * bend + 0.25f * air, 0f, 0f); setRot(s + 2, 0.15f * bend - 0.2f * air, 0f, 0f) }
            setRot(8, 0.18f * air - 0.08f * crouch, 0f, 0f)
        }
        // landing: the tail bobs up and settles
        if (landBob > 0f) {
            val b = sin(landBob * PI.toFloat() * 2.2f) * landBob
            setRot(8, 0.28f * b, 0f, 0f); setRot(9, 0.12f * b, 0f, 0f)
        }
        // startle: a flinch down, feathers tight
        if (startle > 0f) {
            val s = startle * startle
            dyBody += -0.02f * s
            setRot(4, -0.12f * s, 0f, 0f)
        }

        // ---- idle actions ----
        var headP = 0f; var headR = 0f; var jaw = 0f; var neckFwd = 0f
        val u = if (act != Act.NONE) (actT / actLen).coerceIn(0f, 1f) else 0f
        when (act) {
            Act.CAW -> {
                // two caws: lean in, neck forward, beak opens, tail flicks with each call
                val env = smooth((u / 0.15f).coerceAtMost(1f)) * smooth(((1f - u) / 0.15f).coerceAtMost(1f))
                val c1 = pulse(u, 0.22f, 0.22f); val c2 = pulse(u, 0.58f, 0.22f)
                val c = max(c1, c2)
                setRot(0, 0.14f * env + 0.05f * c, 0f, 0f)
                neckFwd = 0.18f * env + 0.12f * c
                headP = -0.18f * env - 0.12f * c
                jaw = 0.42f * c
                setRot(8, 0.32f * c, 0f, 0f); setRot(9, 0.1f * c, 0f, 0f)
                dyBody += -0.015f * c
            }
            Act.PEER -> {
                val e = smooth((u / 0.18f).coerceAtMost(1f)) * smooth(((1f - u) / 0.2f).coerceAtMost(1f))
                neckFwd = 0.22f * e; headP = 0.55f * e; headR = 0.2f * e * (sin(u * 9f) * 3f).coerceIn(-1f, 1f)   // one eye, then the other
                setRot(0, 0.1f * e, 0f, 0f)
            }
            Act.TILT -> {
                val e = smooth((u / 0.12f).coerceAtMost(1f)) * smooth(((1f - u) / 0.15f).coerceAtMost(1f))
                headR = (if (u < 0.5f) 0.42f else -0.38f) * e
                headP = -0.06f * e
            }
            Act.SHAKE -> {
                val d = (1f - u)
                val s = sin(u * 2f * PI.toFloat() * 9f) * d
                setRot(0, 0f, 0.05f * s, 0.06f * s); setRot(6, 0f, 0.25f * s, 0.12f * s)
                setRot(8, 0.1f * d, 0.12f * s, 0f)
                dyBody += 0.01f * d
            }
            Act.CHIRP -> {
                // three quick chirps: the beak opens a little, the head lifts, the tail flicks
                val env = smooth((u / 0.12f).coerceAtMost(1f)) * smooth(((1f - u) / 0.15f).coerceAtMost(1f))
                val c = max(pulse(u, 0.25f, 0.1f), max(pulse(u, 0.48f, 0.1f), pulse(u, 0.71f, 0.1f)))
                headP = -0.12f * env - 0.05f * c
                jaw = 0.2f * c
                setRot(8, 0.12f * c, 0f, 0f)
            }
            Act.BOB -> {
                // a parrot's head bob: quick nods from the neck, a small dip of the body
                val env = smooth((u / 0.12f).coerceAtMost(1f)) * smooth(((1f - u) / 0.15f).coerceAtMost(1f))
                val b = sin(u * 2f * PI.toFloat() * 4f) * env
                neckFwd = 0.16f * b; headP = -0.1f * b
                dyBody += -0.01f * (b * b)
            }
            Act.NONE -> {}
        }

        neckFwd += walkBob
        // ---- music: a light head bob on the bass ----
        if (bass > 0.02f) { neckFwd += 0.1f * bass; headP += 0.12f * bass }

        // ---- the head: world gaze minus the body's yaw, shared over neck and head ----
        setRot(0, 0f, by, 0f)
        // parrots: everything above the shoulders at [headAmp] so the small head never warps
        val ha = headAmp
        val rel = (wrap(gY - by) * ha).coerceIn(-1.75f * ha, 1.75f * ha)
        neckFwd *= ha; headP *= ha; headR *= ha
        val gp = gP * ha; val gr = gR * ha
        setRot(4, neckFwd * 0.5f + gp * 0.15f, rel * 0.2f, 0f)
        setRot(5, neckFwd * 0.5f + gp * 0.25f, rel * 0.3f, 0f)
        setRot(6, gp * 0.6f + headP, rel * 0.5f, gr + headR)
        setRot(7, jaw, 0f, 0f)

        // compose: M_i = M_parent * T(h) R T(-h)
        val L = FloatArray(12)
        for (i in 0 until bones) {
            val rx = rot[i * 3]; val ry = rot[i * 3 + 1]; val rz = rot[i * 3 + 2]
            val cx = cos(rx); val sx = sin(rx); val cy = cos(ry); val sy = sin(ry); val cz = cos(rz); val sz = sin(rz)
            val r0 = cz * cy; val r1 = cz * sy * sx - sz * cx; val r2 = cz * sy * cx + sz * sx
            val r3 = sz * cy; val r4 = sz * sy * sx + cz * cx; val r5 = sz * sy * cx - cz * sx
            val r6 = -sy; val r7 = cy * sx; val r8 = cy * cx
            val hx = head[i * 3]; val hy = head[i * 3 + 1]; val hz = head[i * 3 + 2]
            L[0] = r0; L[1] = r1; L[2] = r2; L[3] = hx - (r0 * hx + r1 * hy + r2 * hz) + (if (i == 0) px else 0f)
            L[4] = r3; L[5] = r4; L[6] = r5; L[7] = hy - (r3 * hx + r4 * hy + r5 * hz) + (if (i == 0) dyBody else 0f)
            L[8] = r6; L[9] = r7; L[10] = r8; L[11] = hz - (r6 * hx + r7 * hy + r8 * hz) + (if (i == 0) pz else 0f)
            val p = parent[i]
            val W = world[i]
            if (p < 0) System.arraycopy(L, 0, W, 0, 12) else mul34(world[p], L, W)
            System.arraycopy(W, 0, mats, i * 12, 12)
        }
    }

    private fun mul34(a: FloatArray, b: FloatArray, o: FloatArray) {
        for (r in 0 until 3) {
            val a0 = a[r * 4]; val a1 = a[r * 4 + 1]; val a2 = a[r * 4 + 2]
            o[r * 4] = a0 * b[0] + a1 * b[4] + a2 * b[8]
            o[r * 4 + 1] = a0 * b[1] + a1 * b[5] + a2 * b[9]
            o[r * 4 + 2] = a0 * b[2] + a1 * b[6] + a2 * b[10]
            o[r * 4 + 3] = a0 * b[3] + a1 * b[7] + a2 * b[11] + a[r * 4 + 3]
        }
    }

    companion object {
        /** One hop, seconds. */
        const val HOP = 0.46f
        /** Furthest it walks from the centre (model units), so it always stays in the picture. */
        const val WALK_MAX = 0.45f
        const val WALK_SPEED = 0.36f      // model units per second
        const val TURN_RATE = 2.4f        // radians per second, stepping on the spot
        const val STEP_HZ = 1.7f          // full stride cycles per second (two steps each)
        fun wrap(a: Float): Float = atan2(sin(a), cos(a))
        private fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
        /** 0 → 1 → 0 bump centred at [c] with half-width [w]. */
        private fun pulse(u: Float, c: Float, w: Float): Float { val d = abs(u - c) / w; return if (d >= 1f) 0f else (1f - d * d).let { it * it } }
    }
}
