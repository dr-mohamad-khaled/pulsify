package app.pulsify.engine

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A wooden perch made in code: a bark texture that tiles end to end, so the branch the bird sits on
 * can run the whole width of the screen. Round shading (dark underside, a lit band on top), a wavy
 * outline, long grain, cracks and two knots per tile. Every pattern repeats exactly every [W] pixels.
 */
object Bark {
    const val W = 1024
    const val H = 128
    /** Rows of the picture covered by wood (the rest is clear, for the wavy outline). */
    const val TOP = 14f
    const val BOT = 114f

    private const val TAU = (2 * PI).toFloat()

    /** Smooth periodic noise: a few sine waves whose periods divide [W]. */
    private fun pn(x: Float, seed: Int, oct: Int = 4, base: Int = 2): Float {
        var s = 0f; var a = 1f; var tot = 0f
        for (o in 0 until oct) {
            val f = base * (1 shl o) + (seed * 7 + o * 3) % 3
            val ph = ((seed * 131 + o * 57) % 997) / 997f * TAU
            s += a * sin(TAU * f * x / W + ph); tot += a; a *= 0.55f
        }
        return s / tot
    }

    fun bitmap(): Bitmap {
        val px = IntArray(W * H)
        // knots: (x, y, radius x, radius y)
        val knots = arrayOf(floatArrayOf(210f, 52f, 26f, 15f), floatArrayOf(700f, 76f, 19f, 12f))
        val cracks = arrayOf(floatArrayOf(90f, 40f, 160f), floatArrayOf(430f, 88f, 120f), floatArrayOf(560f, 35f, 140f), floatArrayOf(880f, 70f, 110f))
        for (x in 0 until W) {
            val fx = x.toFloat()
            val top = TOP + 3.5f * pn(fx, 1, 3, 3) + 1.2f * pn(fx, 9, 2, 17)
            val bot = BOT + 3.0f * pn(fx, 2, 3, 3) + 1.0f * pn(fx, 8, 2, 19)
            for (y in 0 until H) {
                val fy = y + 0.5f
                // coverage with a soft 1.2 px edge
                val cov = min(((fy - top) / 1.2f).coerceIn(0f, 1f), ((bot - fy) / 1.2f).coerceIn(0f, 1f))
                if (cov <= 0f) { px[y * W + x] = 0; continue }
                val v = ((fy - top) / (bot - top)).coerceIn(0f, 1f)          // 0 top .. 1 underside
                // round log shading: lit from above, darker below, a little bounce at the very bottom
                val n = cos((v - 0.32f) * 1.9f).coerceAtLeast(0f)
                var shade = 0.42f + 0.62f * n.pow(1.4f) - 0.25f * smooth(0.75f, 1f, v) + 0.06f * smooth(0.93f, 1f, v)
                // long grain: streaks along the branch whose rows wander a little; each streak fades in
                // and out along its length, like real bark fibres
                val gy = fy + 5f * pn(fx, 3, 3, 2) + 1.5f * pn(fx, 4, 2, 11)
                val band = gy * 0.5f
                val bi = kotlin.math.floor(band).toInt(); val bf = band - bi
                val st = hash(bi) + (hash(bi + 1) - hash(bi)) * (bf * bf * (3f - 2f * bf))
                val fp = 2 + (hash(bi * 7 + 3) * 4f).toInt()
                val fade = 0.5f + 0.5f * sin(TAU * fp * fx / W + hash(bi * 13 + 5) * TAU)
                val fine = hash2(bi * 3 + 1, (fx / 9f).toInt()) * 0.5f + 0.5f
                shade *= 0.74f + 0.36f * st * (0.55f + 0.45f * fade) + 0.05f * fine
                // cracks: thin dark gaps along the grain
                for (c in cracks) {
                    var dx = abs(fx - c[0]); dx = min(dx, W - dx)
                    if (dx < c[2] / 2) {
                        val along = 1f - (dx / (c[2] / 2)).pow(2f)
                        val cy = c[1] + 2.5f * sin(fx * 0.05f + c[0])
                        val d = abs(fy - cy)
                        shade *= 1f - 0.55f * along * exp(-d * d / 1.6f)
                    }
                }
                var r = 0.47f; var g = 0.32f; var b = 0.21f                 // warm brown
                // knots: rings, darker core, the grain flows round them
                for (k in knots) {
                    var dx = fx - k[0]; if (dx > W / 2) dx -= W; if (dx < -W / 2) dx += W
                    val ex = dx / k[2]; val ey = (fy - k[1]) / k[3]
                    val rr = sqrt(ex * ex + ey * ey)
                    if (rr < 2.2f) {
                        val ring = 0.5f + 0.5f * cos(rr * 9f)
                        val core = exp(-rr * rr * 2.2f)
                        shade *= 1f - 0.35f * core - 0.12f * ring * exp(-rr * 1.2f)
                        val hl = exp(-((rr - 1.05f) * (rr - 1.05f)) * 30f)              // a raised lip
                        shade += 0.12f * hl
                    }
                }
                // a touch of grey-green on the top surface, weathered wood
                val wthr = smooth(0.18f, 0f, v) * (0.5f + 0.5f * pn(fx, 10, 3, 4))
                r = r * (1f - 0.25f * wthr) + 0.42f * 0.25f * wthr
                g = g * (1f - 0.25f * wthr) + 0.40f * 0.25f * wthr
                b = b * (1f - 0.25f * wthr) + 0.30f * 0.25f * wthr
                shade = shade.coerceIn(0f, 1.25f)
                val a = (cov * 255f).toInt()
                // straight colour; Android premultiplies on setPixels
                fun ch(cv: Float) = (min(1f, cv * shade) * 255f).toInt().coerceIn(0, 255)
                px[y * W + x] = (a shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
            }
        }
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, W, 0, 0, W, H)
        return bmp
    }

    private fun hash(i: Int): Float { var h = i * 374761393 + 668265263; h = (h xor (h ushr 13)) * 1274126177; return ((h xor (h ushr 16)) and 0xFFFF) / 65535f }
    private fun hash2(i: Int, j: Int): Float = hash(i * 9176 + Math.floorMod(j, W / 9 + 1) * 131)
    private fun smooth(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
}
