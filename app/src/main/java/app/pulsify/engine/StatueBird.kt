package app.pulsify.engine

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Continuous flight for a rigged bird drawn by [StatueScene] (STA2 files from conv/glb2sta2.py --bird).
 * Bones, in file order: 0 body, 1-4 tail, 5 chest, 6-7 neck, 8 head, 9-14 left wing (shoulder, arm,
 * forearm, hand, tip, inner feathers), 15-20 right wing, 21-22 legs.
 *
 * The flap is procedural: a fast downstroke and slower upstroke, each wing segment lagging the one
 * before it so the wave runs out to the tip, the forearm and hand folding back on the way up, the
 * hand feathering. The body bobs against the beat, the tail ripples, the head stays level and turns
 * towards the camera. Music speeds the wings up with the bass; every swipe gives a power burst and a
 * bank into the turn.
 */
class StatueBird : StatueRig {
    override var bones = 0; private set
    private var parent = IntArray(0)
    private var head = FloatArray(0)
    /** Bone matrices for the shader: 3 rows of vec4 per bone. */
    override var mats = FloatArray(0); private set

    /** Extra downward angle on the shoulders (radians), for rigs whose wings rest raised (the ringneck). */
    var wingBias = 0f
    /** Wing-beat size (1 = the motmot's). */
    var flapAmp = 1f
    private var ph = 0f
    private var burst = 0f
    private var bank = 0f; private var bankT = 0f
    private var bass = 0f
    private var t = 0f

    override fun setRig(par: IntArray, heads: FloatArray) {
        bones = par.size; parent = par; head = heads
        mats = FloatArray(bones * 12)
        rot = FloatArray(bones * 3)
        world = Array(bones) { FloatArray(12) }
    }

    override fun clear() { bones = 0 }

    override fun kick(dir: Float) { if (bones == 0) return; burst = 1f; bankT = dir }

    override fun update(dt: Float, time: Float, musicBass: Float) {
        t = time
        bass += (musicBass - bass) * min(1f, dt * 12f)
        if (bones == 0) return
        burst = max(0f, burst - dt / 1.3f)
        val f = 1.25f + 0.55f * bass + 0.9f * burst * burst
        ph += 2f * PI.toFloat() * f * dt
        if (ph > 1000f) ph -= (2f * PI.toFloat()) * 150f
        bank += (bankT * 0.32f - bank) * min(1f, dt * 3.2f); bankT *= 0.25f.pow(dt)
    }

    private var rot = FloatArray(0)
    private var world = arrayOf<FloatArray>()

    private fun wave(x: Float) = sin(x) + 0.22f * sin(2f * x + 0.9f)
    private fun upFold(x: Float): Float { val s = -sin(x); return if (s > 0f) s * s * (3f - 2f * s) else 0f }
    private fun setRot(i: Int, x: Float, y: Float, z: Float) { if (i < bones) { rot[i * 3] = x; rot[i * 3 + 1] = y; rot[i * 3 + 2] = z } }

    /** Poses the bird for this frame. [camAz] (radians) is where the camera sits around it. */
    override fun pose(camAz: Float) {
        if (bones == 0) return
        java.util.Arrays.fill(rot, 0f)
        val aw = 0.5f * flapAmp * (1f + 0.22f * bass + 0.35f * burst)
        val pitch = 0.05f * sin(ph - 1.0f)
        val dy = 0.028f * sin(ph - 1.4f) * (1f + 0.4f * burst)
        setRot(0, pitch, 0.05f * sin(t * .29f), bank + 0.05f * sin(t * .37f))
        for (k in 0 until 4) setRot(1 + k, 0.07f * sin(ph - 1.8f - 0.35f * k), 0.05f * sin(t * .5f + k * .6f) + 0.04f * bank, 0f)
        // the head looks at whoever looks at the bird
        val look = atan2(sin(camAz), cos(camAz)).coerceIn(-0.75f, 0.75f)
        setRot(6, -pitch * 0.5f, look * 0.35f, -bank * 0.3f)
        setRot(7, -pitch * 0.3f, look * 0.3f, -bank * 0.3f)
        setRot(8, 0.03f * sin(t * .8f), look * 0.35f + 0.06f * sin(t * .41f), 0f)
        for (side in intArrayOf(-1, 1)) {
            val base = if (side < 0) 9 else 15
            for (s in 0 until 5) {
                val a = aw * SEG_W[s] * (wave(ph - LAG[s]) - 0.12f) + (if (s == 0) wingBias else 0f)   // + = wing down
                val u = upFold(ph - 0.4f - LAG[s] * 0.5f)
                val sweep = SWEEP[s] * u
                val twist = if (s >= 3) 0.16f * cos(ph - 0.6f - LAG[s]) else 0f
                setRot(base + s, twist, if (side < 0) -sweep else sweep, if (side < 0) a else -a)
            }
            val a0 = aw * 0.5f * (wave(ph - 0.15f) - 0.12f)
            setRot(base + 5, 0f, 0f, if (side < 0) a0 * 0.6f else -a0 * 0.6f)
        }
        val tuck = 0.45f + 0.05f * sin(ph - 2.0f)
        setRot(21, tuck, 0f, 0f); setRot(22, tuck, 0f, 0f)

        // compose: M_i = M_parent * T(h) R T(-h)
        val L = FloatArray(12)
        for (i in 0 until bones) {
            val rx = rot[i * 3]; val ry = rot[i * 3 + 1]; val rz = rot[i * 3 + 2]
            val cx = cos(rx); val sx = sin(rx); val cy = cos(ry); val sy = sin(ry); val cz = cos(rz); val sz = sin(rz)
            // R = Rz * Ry * Rx
            val r0 = cz * cy; val r1 = cz * sy * sx - sz * cx; val r2 = cz * sy * cx + sz * sx
            val r3 = sz * cy; val r4 = sz * sy * sx + cz * cx; val r5 = sz * sy * cx - cz * sx
            val r6 = -sy; val r7 = cy * sx; val r8 = cy * cx
            val hx = head[i * 3]; val hy = head[i * 3 + 1]; val hz = head[i * 3 + 2]
            L[0] = r0; L[1] = r1; L[2] = r2; L[3] = hx - (r0 * hx + r1 * hy + r2 * hz)
            L[4] = r3; L[5] = r4; L[6] = r5; L[7] = hy - (r3 * hx + r4 * hy + r5 * hz) + (if (i == 0) dy else 0f)
            L[8] = r6; L[9] = r7; L[10] = r8; L[11] = hz - (r6 * hx + r7 * hy + r8 * hz)
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
        /** Most bones a rig may have: the shader's uniform array holds 3 vec4 per bone. */
        const val MAX_BONES = 24
        private val SEG_W = floatArrayOf(0.55f, 0.35f, 0.3f, 0.35f, 0.3f)
        private val LAG = floatArrayOf(0f, 0.25f, 0.45f, 0.7f, 0.95f)
        private val SWEEP = floatArrayOf(0f, 0.05f, 0.3f, 0.4f, 0.28f)
    }
}
