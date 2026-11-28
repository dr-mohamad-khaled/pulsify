package app.pulsify.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Free Fall astronaut: procedural animation on the astro2 skeleton (22 bones, pivots recovered from the skin
 * weights). Every joint follows a critically damped spring on its rotation angles, so poses change smoothly and
 * the body keeps its size (the old version blended whole bone matrices between Mixamo clips whose body
 * orientation differed by ~90 degrees, which squashed him). Joint limits keep every bend small enough that the
 * suit never crumples. Mirrors tools/freefall/freefall.js (the browser preview).
 */
class FreeFallRig {
    enum class Mode { ARCH, FLAIL, CURL, REACH, WAVE, SPLAT, THUMBS, SWIM }

    val mats = FloatArray(NB * 12)
    var mode = Mode.ARCH; private set
    private var stiff = 9f
    private var t = 0f
    private val ang = FloatArray(NB * 3); private val vel = FloatArray(NB * 3); private val p = FloatArray(NB * 3)
    private val world = Array(NB) { FloatArray(12) }
    private val lm = FloatArray(12)

    fun set(m: Mode, stiffness: Float = 9f) { mode = m; stiff = stiffness }
    /** A jolt: every joint gets a random push (impacts, pokes). */
    fun jolt(k: Float) { for (i in 0 until NB) for (a in 0..2) vel[i * 3 + a] += (Random.nextFloat() - .5f) * k * (if (i == 0) .3f else 1f) }

    // ---- pose helpers (angles about model x, y, z; side -1 = his left on +x, +1 = his right) ----
    private fun add(b: Int, x: Float, y: Float, z: Float) { p[b * 3] += x; p[b * 3 + 1] += y; p[b * 3 + 2] += z }
    private fun arm(s: Int, raise: Float, fwd: Float, elbow: Float, elbowFwd: Float = 0f, twist: Float = 0f, wrist: Float = 0f) {
        val b = if (s < 0) 6 else 10; val f = s.toFloat()
        add(b, 0f, f * raise * .18f, f * fwd * .15f)
        add(b + 1, twist, f * raise, f * fwd)
        add(b + 2, 0f, f * elbow, f * elbowFwd)
        add(b + 3, 0f, f * wrist, 0f)
    }
    private fun leg(s: Int, flex: Float, spread: Float, knee: Float, foot: Float = 0f) {
        val b = if (s < 0) 14 else 18; val f = s.toFloat()
        add(b, -flex, f * spread, 0f); add(b + 1, knee, 0f, 0f); add(b + 2, foot, 0f, 0f)
    }
    private fun spine(curl: Float, twist: Float = 0f, side: Float = 0f) { for (b in 1..3) add(b, curl / 3f, side / 3f, twist / 3f) }
    private fun head(nod: Float, turn: Float, tilt: Float = 0f) { add(4, nod * .35f, tilt * .35f, turn * .35f); add(5, nod * .65f, tilt * .65f, turn * .65f) }

    private fun pose(t: Float) {
        java.util.Arrays.fill(p, 0f)
        val L = -1; val R = 1
        when (mode) {
            Mode.ARCH -> {   // stable belly-down fall: arched back, arms out with forearms up, knees bent, legs apart
                spine(-.24f + .03f * sin(t * 1.3f)); head(-.32f, .1f * sin(t * .4f))
                val b = .06f * sin(t * 1.7f)
                arm(L, .12f + b, .22f, 1.05f + b, 0f, .15f); arm(R, .12f - b, .22f, 1.05f - b, 0f, -.15f)
                leg(L, -.12f, .24f, .85f + .08f * sin(t * 1.1f)); leg(R, -.12f, .24f, .85f + .08f * sin(t * 1.1f + 2f))
            }
            Mode.FLAIL -> {  // panicked: arms windmill, legs bicycle, head shaking
                val w = 7.2f; val pi = PI.toFloat()
                spine(.05f * sin(t * w * .5f), .18f * sin(t * w * .5f), .08f * sin(t * w * .25f))
                head(-.15f + .12f * sin(t * w * .7f), .35f * sin(t * w * .45f), .12f * sin(t * w * .6f))
                arm(L, .45f + .55f * sin(t * w), .45f * cos(t * w), .45f + .35f * sin(t * w + 1.2f), .2f)
                arm(R, .45f + .55f * sin(t * w + pi), .45f * cos(t * w + pi), .45f + .35f * sin(t * w + 4.3f), .2f)
                val k = 5.4f
                leg(L, .25f + .4f * sin(t * k), .18f, .55f + .45f * sin(t * k + 1.3f))
                leg(R, .25f + .4f * sin(t * k + pi), .18f, .55f + .45f * sin(t * k + pi + 1.3f))
            }
            Mode.CURL -> {   // tucked into a ball, tumbling
                spine(.62f); head(.38f, 0f)
                arm(L, -.35f, .95f, 0f, 1.05f, .3f); arm(R, -.35f, .95f, 0f, 1.05f, -.3f)
                leg(L, 1f, .08f, 1.25f); leg(R, 1f, .08f, 1.25f)
            }
            Mode.REACH -> {  // reaching up for a star, the other arm out for balance, looking at the hand
                spine(-.12f, .15f, -.1f); head(-.35f, .35f, -.15f)
                arm(L, 1f + .06f * sin(t * 3f), .35f, .15f, 0f, 0f, .2f * sin(t * 5f)); arm(R, -.1f, .15f, .45f)
                leg(L, .1f, .15f, .45f); leg(R, -.05f, .2f, .8f)
            }
            Mode.WAVE -> {
                spine(-.08f, -.12f); head(-.1f, -.2f, .12f)
                arm(R, .85f, .2f, .85f + .45f * sin(t * 9f), 0f, 0f, .35f * sin(t * 9f + .6f)); arm(L, .05f, .2f, .5f)
                leg(L, .05f, .18f, .6f); leg(R, .15f, .18f, .4f)
            }
            Mode.SPLAT -> {  // flattened against the screen edge: spread-eagle, head thrown back
                spine(-.3f); head(-.4f, 0f)
                arm(L, .55f, -.15f, .12f); arm(R, .55f, -.15f, .12f)
                leg(L, -.15f, .42f, .15f); leg(R, -.15f, .42f, .15f)
            }
            Mode.THUMBS -> {
                spine(-.1f); head(-.12f + .12f * max(0f, sin(t * 6f)), .15f)
                arm(R, .2f, .85f, .2f, .9f, -.4f); arm(L, .1f, .25f, .6f)
                leg(L, .2f, .15f, .7f); leg(R, .05f, .2f, .5f)
            }
            Mode.SWIM -> {   // breaststroke arms, frog-kick legs, going nowhere
                val s = sin(t * 3.4f); val c = cos(t * 3.4f)
                spine(-.15f + .08f * s); head(-.3f + .1f * s, 0f)
                arm(L, .35f + .35f * c, .5f + .45f * s, .35f + .3f * c, .3f); arm(R, .35f + .35f * c, .5f + .45f * s, .35f + .3f * c, .3f)
                leg(L, .35f + .3f * c, .3f + .15f * s, .9f + .4f * c); leg(R, .35f + .3f * c, .3f + .15f * s, .9f + .4f * c)
            }
        }
        // always alive: breathing
        add(2, .02f * sin(t * 1.9f), 0f, 0f); add(3, .02f * sin(t * 1.9f), 0f, 0f)
    }

    fun update(dt: Float, bass: Float) {
        t += dt
        pose(t)
        if (bass > .02f) { head(.12f * bass, 0f); spine(.1f * bass) }
        val h = min(dt, 1f / 30f)
        for (i in 0 until NB) {
            val w = stiff * (if (i >= 14) .8f else 1f)
            for (a in 0..2) {
                val k = i * 3 + a; val lim = LIM[k]
                val g = p[k].coerceIn(-lim, lim)
                var step = dt
                while (step > 1e-5f) {   // sub-steps keep the spring stable on a slow frame
                    val d = min(step, h)
                    vel[k] += (w * w * (g - ang[k]) - 2f * w * vel[k]) * d
                    ang[k] = (ang[k] + vel[k] * d).coerceIn(-lim * 1.15f, lim * 1.15f)
                    step -= d
                }
            }
        }
        compose()
    }

    private fun compose() {
        for (i in 0 until NB) {
            val rx = ang[i * 3]; val ry = ang[i * 3 + 1]; val rz = ang[i * 3 + 2]
            val cx = cos(rx); val sx = sin(rx); val cy = cos(ry); val sy = sin(ry); val cz = cos(rz); val sz = sin(rz)
            val r0 = cz * cy; val r1 = cz * sy * sx - sz * cx; val r2 = cz * sy * cx + sz * sx
            val r3 = sz * cy; val r4 = sz * sy * sx + cz * cx; val r5 = sz * sy * cx - cz * sx
            val r6 = -sy; val r7 = cy * sx; val r8 = cy * cx
            val hx = PIV[i * 3]; val hy = PIV[i * 3 + 1]; val hz = PIV[i * 3 + 2]
            lm[0] = r0; lm[1] = r1; lm[2] = r2; lm[3] = hx - (r0 * hx + r1 * hy + r2 * hz)
            lm[4] = r3; lm[5] = r4; lm[6] = r5; lm[7] = hy - (r3 * hx + r4 * hy + r5 * hz)
            lm[8] = r6; lm[9] = r7; lm[10] = r8; lm[11] = hz - (r6 * hx + r7 * hy + r8 * hz)
            val o = world[i]; val pa = PAR[i]
            if (pa < 0) System.arraycopy(lm, 0, o, 0, 12) else {
                val a = world[pa]
                for (r in 0 until 3) {
                    val a0 = a[r * 4]; val a1 = a[r * 4 + 1]; val a2 = a[r * 4 + 2]
                    o[r * 4] = a0 * lm[0] + a1 * lm[4] + a2 * lm[8]
                    o[r * 4 + 1] = a0 * lm[1] + a1 * lm[5] + a2 * lm[9]
                    o[r * 4 + 2] = a0 * lm[2] + a1 * lm[6] + a2 * lm[10]
                    o[r * 4 + 3] = a0 * lm[3] + a1 * lm[7] + a2 * lm[11] + a[r * 4 + 3]
                }
            }
            System.arraycopy(o, 0, mats, i * 12, 12)
        }
    }

    companion object {
        const val NB = 22
        private val PAR = intArrayOf(-1, 0, 1, 2, 3, 4, 3, 6, 7, 8, 3, 10, 11, 12, 0, 14, 15, 16, 0, 18, 19, 20)
        /** Joint pivots (model space, Blender axes: x = his left, y = his back, z = up, feet at 0). */
        private val PIV = floatArrayOf(
            0f, -.01f, .56f, 0f, -.01f, .6f, 0f, -.01f, .675f, 0f, 0f, .715f, 0f, 0f, .775f, 0f, -.005f, .79f,
            .045f, 0f, .73f, .127f, -.025f, .705f, .21f, -.03f, .712f, .306f, -.07f, .725f,
            -.045f, 0f, .73f, -.13f, -.025f, .705f, -.208f, -.03f, .712f, -.305f, -.07f, .725f,
            .07f, -.03f, .505f, .075f, -.045f, .267f, .087f, -.04f, .07f, .11f, -.1f, .03f,
            -.068f, -.03f, .505f, -.075f, -.045f, .267f, -.087f, -.04f, .07f, -.11f, -.1f, .03f,
        )
        /** Joint limits (radians) per bone and axis. */
        private val LIM = floatArrayOf(
            .6f, .6f, .6f, .35f, .3f, .35f, .35f, .3f, .35f, .3f, .3f, .3f, .3f, .25f, .45f, .4f, .3f, .6f,
            .2f, .25f, .25f, .5f, 1.15f, 1.1f, .4f, 1.35f, 1.25f, .4f, .5f, .5f,
            .2f, .25f, .25f, .5f, 1.15f, 1.1f, .4f, 1.35f, 1.25f, .4f, .5f, .5f,
            1.2f, .5f, .35f, 1.4f, .2f, .2f, .5f, .2f, .2f, .3f, .1f, .1f,
            1.2f, .5f, .35f, 1.4f, .2f, .2f, .5f, .2f, .2f, .3f, .1f, .1f,
        )
    }
}
