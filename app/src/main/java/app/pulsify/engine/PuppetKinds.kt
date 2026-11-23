package app.pulsify.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A 2D creature painted in Flow and rigged part by part (drawn with [PuppetGl], not Canvas). */
internal abstract class PuppetKind(height: Float, idle: List<String>, perch: Boolean, val dir: String, val below: Float) : ToonKind(height, idle, perch) {
    override fun draw(ink: ToonInk, r: Rig, coat: Int) {}
    /** Rig px -> creature units, and the rig point that sits on the creature's origin (feet / branch). */
    abstract val k: Float
    abstract val ox: Float
    abstract val oy: Float
    /** Each scene owns its [PuppetArt] (textures belong to one GL context). */
    abstract fun drawGl(g: PuppetGl, r: Rig, art: PuppetArt)
    /** Rig px per creature unit (to size the textures). */
    val rigPerUnit get() = 1f / k
}

// ------------------------------------------------------------------ Snowy (painted duckling)
internal object DuckArtKind : PuppetKind(1.4f, listOf("Quack", "Flap", "Waddle", "Bread crumbs", "Head tilt", "Splash"), false, "duck", .45f) {
    private const val FEET = 1118f
    override val k = 1.4f / (FEET - 75f)
    override val ox = 448f
    override val oy = FEET
    private const val HX = 448f; private const val HY = 425f; private const val HRX = 318f; private const val HRY = 276f
    private const val BX = 448f; private const val BY = 548f
    private fun depth(x: Float, y: Float): Float {
        val hx = (x - HX) / HRX; val hy = (y - HY) / HRY; val r2 = hx * hx + hy * hy
        val z = if (r2 < 1f) sqrt(1 - r2) else 0f
        val bx = (x - BX) / 150f; val by = (y - BY) / 95f; return z + .5f * exp(-(bx * bx + by * by))
    }
    private fun dx(x: Float, y: Float, yaw: Float) = sin(yaw) * depth(x, y) * HRX * .42f
    private fun dy(x: Float, y: Float, pitch: Float) = sin(pitch) * depth(x, y) * HRY * .34f

    override fun drawGl(g: PuppetGl, r: Rig, art: PuppetArt) {
        val a = art; val P = a.parts; if (!a.loaded) return
        val T = r.t; val br = sin(T * 2.4f)
        g.translate(-ox, -oy)
        val W = if (r.walking) 1f else 0f; val ph = r.walkPh
        fun lift(f: Float) = max(0f, sin(ph + f))
        val rockA = W * sin(ph) * .075f; val bob = W * abs(sin(ph)) * -10f
        fun foot(n: String, f: Float, cx: Float) { val L = lift(f) * W; val dir = r.face
            g.save(); g.translate(cx + L * dir * 14f, 1100f - L * 34f); g.rotate(-dir * L * .35f); g.translate(-cx, -1100f); g.part(P[n]); g.restore() }
        val bk = max(0f, r.back)
        // Knocked over: a painted picture of the duck lying on its back takes over from the rig
        // (crossfade while it tips), rocking a little and breathing, feet in the air.
        val fallen = P["fallen"]
        val fv = if (fallen?.tex != null) ((bk - .3f) / .4f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) } else 0f
        if (fv > 0f) {
            val fp = fallen!!
            g.save(); g.alpha *= fv
            val cx = 448f; val by = fp.y + fp.h - 20f
            g.translate(cx, by); g.rotate(sin(T * 3.1f) * .035f * bk); g.scale(1f + br * .01f, 1f - br * .015f + (1f - bk) * .25f); g.translate(-cx, -by)
            g.part(fp); g.restore()
        }
        if (fv >= .999f) return
        val sv0 = g.alpha; g.alpha *= 1f - fv
        if (bk < .02f) { foot("footL", 0f, 335f); foot("footR", PI.toFloat(), 560f) }
        g.save(); g.translate(448f, FEET - 30f); g.rotate(rockA); g.translate(0f, bob)
        if (bk > 0f) g.scale(1 + bk * .08f, 1 - bk * .42f)
        g.translate(-448f, -(FEET - 30f))
        g.save(); g.translate(448f, FEET - 20f); g.scale(1 - br * .006f, 1 + br * .012f); g.translate(-448f, -(FEET - 20f)); g.part(P["body"])
        val fl = max(r.wing, bk * .8f); val flap = abs(sin(T * (if (bk > .3f) 22f else 30f))) * fl; val sw = -rockA * 1.6f + r.wingS
        val idleW = .06f * sin(T * 2.4f) + .025f * sin(T * 9.3f) + r.wingKick * abs(sin(T * 24f))
        val wl = .1f + idleW + flap * .95f + r.wingLift * .5f; val wr = .1f + idleW * .9f + flap * .95f + r.wingLift * .5f + .02f * sin(T * 7.7f)
        val pL = a.pivot("wingL"); val pR = a.pivot("wingR")
        g.rot(pL[0], pL[1], wl + sw) { g.part(P["wingL"]) }
        g.rot(pR[0], pR[1], -wr + sw) { g.part(P["wingR"]) }
        g.restore()
        // head
        val hp = a.pivot("head"); val yaw = r.yaw; val pitch = r.pitch - bk * .35f
        g.save(); g.translate(hp[0] + r.headX * 300f, hp[1] + r.headY * 300f - br * 3f + bk * 55f)
        g.rotate(r.headTilt - rockA * .8f + sin(T * 3f) * .08f * bk); g.scale(1 - bk * .06f, 1 - bk * .02f); g.translate(-hp[0], -hp[1])
        val tp = a.pivot("tuft"); val tdx = dx(tp[0], tp[1] + 40f, yaw); val tdy = dy(tp[0], tp[1] + 40f, pitch)
        g.rot(tp[0] + tdx, tp[1] + 8f + tdy, sin(T * 1.7f) * .06f - r.headTilt * .6f + r.tuftV - yaw * .25f) { g.part(P["tuft"], tdx, tdy) }
        val hm = a.mesh ?: PMesh(P["head"]!!, 34, 30).also { a.mesh = it }
        hm.build { x, y, d -> d[0] = dx(x, y, yaw); d[1] = dy(x, y, pitch) }
        val ht = P["head"]!!.tex
        g.mesh(hm, ht, null, 0f)
        // lower beak drops to open, riding the turn with the upper beak
        val mY = a.f("mouthY"); val mo = r.mouth
        g.save(); g.translate(dx(BX, BY + 20f, yaw), dy(BX, BY + 20f, pitch)); g.translate(448f, mY); g.scale(1 - abs(sin(yaw)) * .12f, 1 + mo * .12f); g.translate(0f, mo * 22f); g.translate(-448f, -mY); g.part(P["beakLow"]); g.restore()
        // eyes
        val pupils = a.arr2("pupils")
        for (i in 0..1) {
            val (cx0, cy0, rr) = Triple(pupils[i][0], pupils[i][1], pupils[i][2])
            val ex = dx(cx0, cy0, yaw); val ey = dy(cx0, cy0, pitch); val cx = cx0 + ex; val cy = cy0 + ey
            val sx = 1 + (dx(cx0 + 8f, cy0, yaw) - dx(cx0 - 8f, cy0, yaw)) / 16f
            val oxl = r.lx * rr * .12f; val oyl = r.ly * rr * .1f
            g.save(); g.translate(cx, cy); g.scale(sx, 1f); g.translate(-cx, -cy)
            g.clip(cx, cy, rr * 1.28f, rr * 1.28f); g.part(P[if (i == 0) "pupilL" else "pupilR"], ex + oxl, ey + oyl); g.noClip()
            val close = if (r.happy > .5f) 1f else (1 - r.blink * (if (r.sleep) 0f else 1f))
            if (r.smile > .02f) smileLid(g, hm, ht, cx, cy, rr * 1.55f, rr * 1.5f, .28f * r.smile, 0x786464)
            if (close > .02f) {
                val R2 = rr * 1.55f; val ly = cy - R2 + 2 * R2 * close
                g.clip(cx, cy, R2, R2 * .95f, 1, ly, rr * .35f * close * .5f, R2)
                g.mesh(hm, ht, null, 0f, 0f, 2 * R2 * close * 1.02f); g.noClip()
                g.save(); g.alpha *= .35f * close; g.curve(cx - R2 * .9f, ly, cx, ly + rr * .35f * close, cx + R2 * .9f, ly, rr * .08f, 0x786464); g.restore()
            }
            if (r.happy > .5f) { g.save(); g.alpha *= .9f; g.curve(cx - rr * .85f, cy + rr * .15f, cx, cy - rr * .75f, cx + rr * .85f, cy + rr * .15f, rr * .22f, 0x281E1E); g.restore() }
            else if (close > .6f) { g.save(); g.alpha *= .55f; g.curve(cx - rr * .95f, cy + rr * .1f, cx, cy + rr * .55f, cx + rr * .95f, cy + rr * .1f, rr * .12f, 0x3C3232); g.restore() }
            g.restore()
        }
        g.restore(); g.restore()
        if (bk > .02f) for ((n, cx, ph0) in listOf(Triple("footL", 335f, 0f), Triple("footR", 560f, 2f))) {
            val kick = sin(T * 16f + ph0) * .5f; val sd = if (cx < 448f) -1f else 1f
            g.save(); g.translate(cx, 1100f); g.translate(sd * bk * 34f, -bk * (150f + 30f * kick)); g.rotate(-sd * bk * (2.25f + .25f * kick)); g.translate(-cx, -1100f); g.part(P[n]); g.restore()
        }
        g.alpha = sv0
    }
}

/** Happy eyes: the fur under the eye lifts into an arch; the eye stays open. */
internal fun smileLid(g: PuppetGl, hm: PMesh, ht: PTex?, cx: Float, cy: Float, LX: Float, LY: Float, a: Float, ink: Int) {
    val yb = cy + LY - 2 * LY * a; val base = yb + LY * a * .6f
    g.clip(cx, cy, LX, LY, 2, base, LY * a * .85f, LX)
    g.mesh(hm, ht, null, 0f, 0f, -2 * LY * a * 1.05f); g.noClip()
    g.save(); g.alpha *= .4f; g.curve(cx - LX * .8f, yb + LY * a * .45f, cx, yb - LY * a * 1.05f, cx + LX * .8f, yb + LY * a * .45f, LX * .07f, ink); g.restore()
}

// ------------------------------------------------------------------ Kiwi (painted ringneck)
internal object ParrotArtKind : PuppetKind(1.5f, listOf("Talk", "Whistle", "Side step", "Curious tilt", "Shake off", "Wing stretch", "Puff up", "Fly a lap", "Head bob", "Flap workout", "Hang upside down"), true, "parrot", .95f) {
    private const val CX = 448f
    private const val BY = 777.5f
    override val k = 1.5f / (BY - 75f)
    override val ox = CX
    override val oy = BY
    private fun depth(hc: FloatArray, x: Float, y: Float): Float {
        val hx = (x - hc[0]) / hc[2]; val hy = (y - hc[1]) / hc[3]; val r2 = hx * hx + hy * hy
        val z = if (r2 < 1f) sqrt(1 - r2) else 0f
        val bx = (x - CX) / 62f; val by = (y - 292f) / 70f; return z + .75f * exp(-(bx * bx + by * by))
    }


    /** The branch stays in the world when the bird flies off. It is made in code (see [Bark]) and runs
     *  edge to edge, far past both sides of the screen. */
    fun drawBranch(g: PuppetGl, art: PuppetArt) {
        if (!art.loaded) return
        val b = art.bark ?: run { g.save(); g.useWorld(); g.translate(-ox, -oy); g.part(art.parts["branch"]); g.restore(); return }
        val h = 112f; val y = BY - 7f - h * (Bark.TOP / Bark.H)          // wood top just under the toes
        val tile = Bark.W * h / Bark.H; val half = 9000f
        g.save(); g.useWorld(); g.translate(-ox, -oy)
        g.quadRepeat(b, CX - half, y, half * 2f, h, -half / tile + 0.37f, half / tile + 0.37f)
        g.restore()
    }

    override fun drawGl(g: PuppetGl, r: Rig, art: PuppetArt) {
        val a = art; val P = a.parts; if (!a.loaded) return
        val hc = a.arr("headC")
        fun dx(x: Float, y: Float, yaw: Float) = sin(yaw) * depth(hc, x, y) * hc[2] * .4f
        fun dy(x: Float, y: Float, pitch: Float) = sin(pitch) * depth(hc, x, y) * hc[3] * .3f
        val T = r.t; val br = sin(T * 2.6f)
        g.translate(-ox, -oy)
        val air = r.airMix
        val spread = smf((r.wing - .3f) / .35f)
        val pL = a.pivot("wingL"); val pR = a.pivot("wingR")
        val idle = .025f * sin(T * 2.6f) + .012f * sin(T * 8.1f) + r.wingKick * .09f * abs(sin(T * 26f))
        g.save(); g.translate(CX, BY - 20f); g.rotate(r.lean); g.translate(-CX, -(BY - 20f))
        // tail
        val tp = a.pivot("tail")
        g.rot(tp[0], tp[1], r.tailSw * .25f + sin(T * 1.3f) * .025f - r.tiltSnap * .5f * (1 - air) + air * sin(T * 9f) * .04f) { g.part(P["tail"]) }
        // spread (flight) wings, behind the body
        if (spread > 0f) {
            val fly = a.rig!!.getJSONObject("fly"); val fk = fly.getDouble("k").toFloat() * .5f * .82f
            val eyes = a.arr2("eyes"); val ex = (eyes[0][0] + eyes[1][0]) / 2; val ey = (eyes[0][1] + eyes[1][1]) / 2
            val ec = fly.getJSONArray("eyeC"); val ecx = ec.getDouble(0).toFloat(); val ecy = ec.getDouble(1).toFloat()
            fun mx(x: Float) = ex + (x - ecx) * fk
            fun my(y: Float) = ey + (y - ecy) * fk + 28f
            val flapping = if (r.flying || r.carried || r.flapHere) 1f else 0f
            val beat = if (flapping > 0f) sin(r.flapPh) else .15f * sin(T * 3f)
            val piv = fly.getJSONObject("pivots")
            for ((n, sg) in listOf("flyWingL" to 1f, "flyWingR" to -1f)) {
                val p = P[n] ?: continue; val pa = piv.getJSONArray(if (sg > 0) "L" else "R")
                val pvx = mx(pa.getDouble(0).toFloat()); val pvy = my(pa.getDouble(1).toFloat())
                g.save(); g.alpha *= spread; g.translate(pvx, pvy)
                g.rotate(sg * (-(1 - spread) * 1.25f + beat * .62f - r.wingLift * .2f))
                g.scale(1 - .18f * max(0f, beat) * flapping, 1 - .1f * abs(beat) * flapping)
                g.translate(-pvx, -pvy); g.quad(p.tex, mx(p.x), my(p.y), p.w * fk, p.h * fk); g.restore()
            }
        }
        // body breathes about the feet, puffs up
        g.save(); g.translate(CX, BY); g.scale(1 - br * .006f + r.puff * .03f, 1 + br * .013f + r.puff * .05f); g.translate(-CX, -BY)
        g.save(); g.translate(CX, BY - 120f); g.scale(1 + r.puff * .13f, 1f); g.translate(-CX, -(BY - 120f)); g.part(P["body"]); g.restore()
        g.restore()
        drawBranch(g, a)
        // folded wings
        if (spread < 1f) {
            g.save(); g.alpha *= 1 - spread; val lift = r.wingLift * .45f + r.wing * .7f
            g.rot(pL[0], pL[1], idle + lift) { g.part(P["wingL"]) }
            g.rot(pR[0], pR[1], -idle * .9f - lift - .01f * sin(T * 6.7f)) { g.part(P["wingR"]) }
            g.restore()
        }
        // head
        val hp = a.pivot("head"); val yaw = r.yaw; val pitch = r.pitch
        g.save(); g.translate(hp[0] + r.headX * 160f, hp[1] + r.headY * 260f - br * 2f); g.rotate(r.headTilt * .55f); g.scale(1 + r.puff * .05f, 1 + r.puff * .04f); g.translate(-hp[0], -hp[1])
        val hm = a.mesh ?: PMesh(P["head"]!!, 30, 30).also { a.mesh = it }
        hm.build { x, y, d -> d[0] = dx(x, y, yaw); d[1] = dy(x, y, pitch) }
        val ht = P["head"]!!.tex; val hopen = P["headOpen"]?.tex
        val mo = clampf(r.mouth, 0f, 1f)
        g.mesh(hm, ht, hopen, mo)
        val eyes = a.arr2("eyes")
        for (i in 0..1) {
            val cx0 = eyes[i][0]; val cy0 = eyes[i][1]; val rx = eyes[i][2]; val ry = eyes[i][3]
            val ex = dx(cx0, cy0, yaw); val ey = dy(cx0, cy0, pitch); val cx = cx0 + ex; val cy = cy0 + ey
            val sx = 1 + (dx(cx0 + 6f, cy0, yaw) - dx(cx0 - 6f, cy0, yaw)) / 12f
            val oxl = r.lx * rx * .15f; val oyl = r.ly * ry * .1f
            g.save(); g.translate(cx, cy); g.scale(sx, 1f); g.translate(-cx, -cy)
            g.clip(cx, cy, rx * 1.06f, ry * 1.04f); g.part(P[if (i == 0) "eyeL" else "eyeR"], ex + oxl, ey + oyl); g.noClip()
            g.save(); g.alpha *= .85f; g.ellipse(cx - rx * .28f, cy - ry * .42f, rx * .2f, ry * .13f, 0xFFFFFF); g.restore()
            if (r.smile > .02f) smileLid(g, hm, ht, cx, cy, rx * 1.32f, ry * 1.22f, .36f * r.smile, 0x283C14)
            val close = if (r.happy > .5f) .55f else (1 - r.blink * (if (r.sleep) 0f else 1f))
            if (close > .02f) {
                val LX = rx * 1.32f; val LY = ry * 1.22f; val ly = cy - LY + 2 * LY * close
                g.clip(cx, cy, LX, LY, 1, ly, ry * .4f * close * .5f, LX)
                g.mesh(hm, ht, hopen, mo, 0f, 2 * LY * close * 1.02f); g.noClip()
                g.save(); g.alpha *= .45f * close; g.curve(cx - LX * .85f, ly, cx, ly + ry * .4f * close, cx + LX * .85f, ly, rx * .1f, 0x283C14); g.restore()
            }
            if (r.happy > .5f) { g.save(); g.alpha *= .9f; g.curve(cx - rx * .9f, cy + ry * .1f, cx, cy - ry * .55f, cx + rx * .9f, cy + ry * .1f, rx * .2f, 0x1E1914); g.restore() }
            g.restore()
        }
        g.restore()
        g.restore()   // lean
        // feet tucked up in the air (from the flying painting)
        val ff = P["flyFeet"]
        if (air > .02f && ff != null) {
            val fly = a.rig!!.getJSONObject("fly"); val fk = fly.getDouble("k").toFloat() * .5f
            val eyes2 = a.arr2("eyes"); val ex = (eyes2[0][0] + eyes2[1][0]) / 2; val ec = fly.getJSONArray("eyeC"); val ecx = ec.getDouble(0).toFloat()
            val swv = sin(r.flapPh - .8f) * (if (r.flying || r.carried) 1f else .3f)
            g.save(); g.alpha *= air; g.translate(CX, BY - 38f); g.rotate(swv * .06f); g.translate(0f, swv * 4f - (1 - air) * 20f); g.translate(-CX, -(BY - 38f))
            g.quad(ff.tex, ex + (ff.x - ecx) * fk, BY - 38f + (ff.y - 1247f) * fk, ff.w * fk, ff.h * fk); g.restore()
        }
        // toes grip the branch; they step when it sidesteps, one lifts into the feathers on One leg
        if (air < .98f) {
            val tp2 = P["toes"] ?: return
            g.save(); g.alpha *= 1 - air; g.translate(0f, -air * 40f)
            val one = r.oneLeg; val wk = if (r.walking) 1f else 0f
            val lifts = floatArrayOf(wk * max(0f, sin(r.walkPh)) * 26f, wk * max(0f, sin(r.walkPh + PI.toFloat())) * 26f + one * 60f)
            if (lifts[0] + lifts[1] > .1f) for (i in 0..1) {
                g.save(); g.clipRect(tp2.x + i * tp2.w / 2, tp2.y - 80f, tp2.x + (i + 1) * tp2.w / 2, tp2.y + tp2.h)
                if (i == 1 && one > 0f) g.alpha *= 1 - smf(one * 1.4f)
                g.translate(0f, -lifts[i]); g.part(tp2); g.noClip(); g.restore()
            } else g.part(tp2)
            g.restore()
        }
    }
}
