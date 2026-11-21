package app.pulsify.engine

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/* The four creatures of the 2D cast, drawn exactly like the "New 2D cast" preview. y is DOWN. */

private fun fa(vararg v: Float) = v
private fun sh(c: Int, k: Float) = ToonInk.shade(c, k)
private const val TAU2 = (PI * 2).toFloat()

private fun ToonInk.furStrokes(cx: Float, cy: Float, rx: Float, ry: Float, n: Int, c: Int, a: Float, len: Float = .06f, seed: Float = 1f) {
    for (i in 0 until n) {
        val an = i.toFloat() / n * TAU2 + seed; val x = cx + cos(an) * rx; val y = cy + sin(an) * ry
        val p = p(); p.moveTo(x, y); p.quadTo(x + cos(an + .6f) * len * .6f, y + sin(an + .6f) * len * .6f, x + cos(an + .3f) * len, y + sin(an + .3f) * len)
        line(p, c, .012f, a)
    }
}

// ------------------------------------------------------------------ Kalboob, the pup
internal object PupKind : ToonKind(1.75f, listOf("Scratch", "Yawn", "Play bow", "Zoomies", "Bark", "Sniff", "Head tilt")) {
    override fun draw(ink: ToonInk, r: Rig, coat: Int) = with(ink) {
        val c = PUP_COATS[coat.coerceIn(0, PUP_COATS.size - 1)]
        val fur = c.fur; val light = c.light; val collar = c.collar; val dark = sh(fur, -.3f); val deep = sh(fur, -.45f); val lx = r.lx; val ly = r.ly
        // tail: a fluffy plume that wags
        cv.save(); cv.translate(.32f, -.24f); cv.rotate((-.75f + sin(r.wag) * .6f * r.wagAmp) * 57.3f)
        val tl = blob(fa(-.04f, 0f, .12f, -.08f, .24f, -.3f, .26f, -.56f, .18f, -.66f, .12f, -.5f, .06f, -.24f, -.06f, -.06f)); vol(tl, fur, .14f, -.32f, .36f)
        cv.save(); cv.clipPath(tl); furStrokes(.16f, -.36f, .1f, .24f, 9, sh(fur, .3f), .5f, .07f, 2f); cv.restore(); cv.restore()
        for (sd in intArrayOf(-1, 1)) { val s = sd.toFloat()
            vol(furry(s * .31f, -.22f, .26f, .24f, 7, .05f, s * -.2f, s + 3f), fur, s * .31f, -.24f, .28f)
            vol(ell(s * .38f, -.05f, .17f, .075f), light, s * .38f, -.06f, .17f, .25f, -.12f) }
        val body = blob(fa(-.33f, -.06f, -.42f, -.38f, -.33f, -.72f, 0f, -.86f, .33f, -.72f, .42f, -.38f, .33f, -.06f, 0f, -.01f)); vol(body, fur, -.02f, -.46f, .5f)
        val bib = blob(fa(-.24f, -.8f, -.27f, -.56f, -.16f, -.36f, -.08f, -.42f, 0f, -.33f, .08f, -.42f, .16f, -.36f, .27f, -.56f, .24f, -.8f, 0f, -.88f)); vol(bib, light, 0f, -.62f, .32f, .3f, -.08f)
        furStrokes(0f, -.6f, .22f, .2f, 10, sh(light, -.12f), .5f, .05f, 1f)
        for (sd in intArrayOf(-1, 1)) { val s = sd.toFloat(); val lift = if (sd > 0) r.pawLift else 0f
            cv.save(); cv.translate(s * .14f, -lift * .3f)
            val leg = blob(fa(-.08f, -.6f, .08f, -.6f, .09f, -.1f, 0f, -.06f, -.09f, -.1f)); vol(leg, fur, 0f, -.38f, .3f, .18f, -.3f)
            val sep = p(); sep.moveTo(s * -.085f, -.5f); sep.lineTo(s * -.09f, -.1f); line(sep, dark, .012f, .45f)
            vol(ell(0f, -.055f, .115f, .07f), light, 0f, -.07f, .12f, .3f, -.15f)
            for (k in intArrayOf(-1, 0, 1)) { val t = p(); t.moveTo(k * .04f, -.01f); t.lineTo(k * .035f, -.05f); line(t, sh(light, -.4f), .012f, .8f) }
            cv.restore() }
        val col = p(); col.moveTo(-.27f, -.84f); col.quadTo(0f, -.74f, .27f, -.84f); line(col, sh(collar, -.25f), .08f); line(col, collar, .06f)
        fill(ell(0f, -.74f, .055f, .055f), 0xE8B93A); fill(ell(-.015f, -.755f, .02f, .016f), 0xFFFFFF, .7f)
        // head
        cv.save(); cv.translate(r.headX, -1.2f + r.headY); cv.rotate(r.headTilt * 57.3f)
        vol(furry(0f, -.04f, .48f, .41f, 11, .045f, 0f, 2f), fur, -.02f, -.08f, .5f, .25f, -.25f)
        vol(furry(0f, .13f, .45f, .2f, 9, .08f, 0f, 3f), fur, 0f, .1f, .45f, .15f, -.25f)
        fill(ell(0f, -.24f, .09f, .13f), light, .55f)
        vol(blob(fa(-.07f, -.38f, -.02f, -.47f, .05f, -.5f, .1f, -.45f, .04f, -.43f, .06f, -.38f)), fur, 0f, -.43f, .08f)
        for (sd in intArrayOf(-1, 1)) { val s = sd.toFloat()
            fill(ell(s * .19f + lx * .02f, -.24f + ly * .015f, .075f, .03f, s * .2f), light, .9f)
            eye(s * .19f + lx * .05f, -.09f + ly * .03f, .105f, lx, ly, r.blink * (if (r.sleep) 0f else 1f), 0x5A3416, dark, r.happy) }
        val mx = lx * .04f; val my = ly * .02f
        vol(blob(fa(-.24f + mx, .1f + my, -.14f + mx, .01f + my, mx, -.02f + my, .14f + mx, .01f + my, .24f + mx, .1f + my, .15f + mx, .23f + my, mx, .26f + my, -.15f + mx, .23f + my)), light, mx, .08f + my, .26f, .3f, -.12f)
        val mo = max(r.mouth, r.nibble * .5f)
        if (mo > .05f) { val m = p(); m.moveTo(-.11f + mx, .17f + my); m.quadTo(mx, .2f + mo * .2f + my, .11f + mx, .17f + my); m.quadTo(mx, .14f + my, -.11f + mx, .17f + my); fill(m, 0x3A1410) }
        if (r.tongue > .05f) { val ty = .2f + my + mo * .08f
            vol(blob(fa(-.055f + mx, ty, .055f + mx, ty, .06f + mx, ty + .07f + r.tongue * .05f, mx, ty + .11f + r.tongue * .07f, -.06f + mx, ty + .07f + r.tongue * .05f)), 0xF27A8A, mx, ty + .05f, .08f, .25f, -.2f)
            val t = p(); t.moveTo(mx, ty + .01f); t.lineTo(mx, ty + .07f + r.tongue * .05f); line(t, 0xC24A5A, .01f) }
        if (mo <= .05f) { val m = p(); m.moveTo(-.08f + mx, .17f + my); m.quadTo(-.04f + mx, .21f + my, mx, .165f + my); m.quadTo(.04f + mx, .21f + my, .08f + mx, .17f + my); line(m, 0x3A2016, .017f) }
        val ph = p(); ph.moveTo(mx, .1f + my); ph.lineTo(mx, .165f + my); line(ph, 0x3A2016, .014f)
        vol(blob(fa(-.08f + mx, .04f + my, mx, .025f + my, .08f + mx, .04f + my, .055f + mx, .09f + my, mx, .105f + my, -.055f + mx, .09f + my)), 0x2A1A16, mx, .06f + my, .09f, .4f, -.2f)
        fill(ell(-.028f + mx, .05f + my, .025f, .012f), 0xFFFFFF, .75f)
        for (sd in intArrayOf(-1, 1)) { val s = sd.toFloat()
            cv.save(); cv.translate(s * .33f, -.3f); cv.rotate(s * (-.12f + r.ear * .7f + (if (sd > 0) r.earR else 0f) + sin(r.t * 1.3f + s) * .03f) * 57.3f)
            vol(blob(fa(s * -.05f, -.04f, s * .1f, -.06f, s * .2f, .12f, s * .2f, .38f, s * .1f, .5f, 0f, .42f, s * -.05f, .2f)), dark, s * .08f, .16f, .32f, .18f, -.3f)
            val fold = p(); fold.moveTo(s * .02f, 0f); fold.quadTo(s * .14f, .2f, s * .1f, .44f); line(fold, deep, .02f, .4f)
            cv.restore() }
        cv.restore()
    }
    override fun chargeProp(ink: ToonInk, t: Float) = with(ink) {
        val b = p(); b.moveTo(-.15f, 0f); b.lineTo(.15f, 0f); line(b, 0xF6EBD2, .07f)
        for (e in floatArrayOf(-.15f, .15f)) for (k in intArrayOf(-1, 1)) fill(ell(e, k * .035f, .045f, .045f), 0xF6EBD2)
    }
}

// ------------------------------------------------------------------ Snowy, the duckling
data class DuckCoat(val name: String, val fluff: Int, val beak: Int)
val DUCK_COATS = listOf(DuckCoat("Snow white", 0xF5F2EA, 0xFF9A3C), DuckCoat("Sunny", 0xFFE36E, 0xFF8A2A), DuckCoat("Mallard", 0xB79A6E, 0xE6A23A))

internal object DuckKind : ToonKind(1.4f, listOf("Quack", "Flap", "Preen", "Waddle", "Bread crumbs", "Head tilt")) {
    override fun draw(ink: ToonInk, r: Rig, coat: Int) = with(ink) {
        val c = DUCK_COATS[coat.coerceIn(0, DUCK_COATS.size - 1)]; val fluff = c.fluff; val beak = c.beak; val lx = r.lx; val ly = r.ly; val dk = sh(fluff, -.2f)
        for (sd in intArrayOf(-1, 1)) { val st = if (sd > 0) r.step else -r.step
            cv.save(); cv.translate(sd * .17f + st * .07f, -.02f - max(0f, st) * .06f)
            vol(blob(fa(-.14f, .02f, -.1f, -.04f, -.04f, -.07f, 0f, -.08f, .04f, -.07f, .1f, -.04f, .14f, .02f, .08f, 0f, .04f, .03f, 0f, 0f, -.04f, .03f, -.08f, 0f)), beak, 0f, -.03f, .15f, .25f, -.2f)
            for (k in intArrayOf(-1, 1)) { val w = p(); w.moveTo(0f, -.06f); w.lineTo(k * .07f, 0f); line(w, sh(beak, -.25f), .01f, .6f) }
            cv.restore() }
        vol(furry(0f, -.42f, .47f, .42f, 16, .055f, 0f, 1.3f), fluff, -.04f, -.48f, .52f, .25f, -.2f)
        for (i in 0 until 7) { val x = sin(i * 1.7f) * .26f; val y = -.22f - cos(i * 2.3f) * .14f; val f = p(); f.moveTo(x, y); f.quadTo(x + .03f, y + .03f, x + .01f, y + .06f); line(f, dk, .01f, .22f) }
        for (sd in intArrayOf(-1, 1)) { val s = sd.toFloat()
            cv.save(); cv.translate(s * .4f, -.52f); cv.rotate(s * (-.15f - r.wing * 1.2f - abs(sin(r.t * 30f)) * .35f * r.wing) * 57.3f)
            vol(blob(fa(0f, -.08f, s * .1f, -.06f, s * .15f, .06f, s * .12f, .2f, s * .04f, .22f, s * -.03f, .12f)), sh(fluff, -.08f), s * .06f, .06f, .18f, .3f, -.2f)
            for (k in floatArrayOf(.06f, .13f)) { val f = p(); f.moveTo(s * .02f, k); f.quadTo(s * .08f, k + .04f, s * .12f, k + .03f); line(f, dk, .01f, .45f) }
            cv.restore() }
        cv.save(); cv.translate(r.headX, -.98f + r.headY); cv.rotate(r.headTilt * 57.3f)
        vol(furry(0f, 0f, .36f, .34f, 13, .05f, 0f, 2.1f), fluff, -.04f, -.05f, .4f, .25f, -.18f)
        for (k in intArrayOf(-1, 0, 1)) vol(blob(fa(k * .05f - .03f, -.3f, k * .06f, -.42f, k * .08f + .06f, -.46f, k * .09f + .05f, -.4f, k * .05f + .03f, -.3f)), fluff, k * .06f, -.38f, .06f, .3f, -.25f)
        for (sd in intArrayOf(-1, 1)) fill(ell(sd * .21f, .07f, .07f, .045f), 0xFF9FB0, .4f)
        for (sd in intArrayOf(-1, 1)) eye(sd * .14f + lx * .04f, -.04f + ly * .03f, .08f, lx, ly, r.blink * (if (r.sleep) 0f else 1f), 0x2B1E18, dk, r.happy)
        val bx = lx * .06f; val by = .1f + ly * .02f; val mo = r.mouth
        vol(blob(fa(-.13f + bx, by + .01f, .13f + bx, by + .01f, .1f + bx, by + .05f + mo * .1f, bx, by + .07f + mo * .12f, -.1f + bx, by + .05f + mo * .1f)), sh(beak, -.15f), bx, by + .05f, .13f, .2f, -.2f)
        if (mo > .05f) fill(ell(bx, by + .035f + mo * .05f, .08f, .02f + mo * .045f), 0x8A2A14)
        vol(blob(fa(-.17f + bx, by + .02f, -.13f + bx, by - .06f, bx, by - .085f, .13f + bx, by - .06f, .17f + bx, by + .02f, .08f + bx, by + .04f - mo * .01f, bx, by + .035f - mo * .01f, -.08f + bx, by + .04f - mo * .01f)), beak, bx, by - .035f, .17f, .35f, -.15f)
        fill(ell(-.035f + bx, by - .05f, .016f, .008f), sh(beak, -.45f), .7f); fill(ell(.035f + bx, by - .05f, .016f, .008f), sh(beak, -.45f), .7f)
        fill(ell(-.06f + bx, by - .06f, .05f, .012f, -.2f), 0xFFFFFF, .35f)
        cv.restore()
    }
    override fun chargeProp(ink: ToonInk, t: Float) = with(ink) { for (i in 0 until 5) fill(ell((i - 2) * .05f, sin(i.toFloat()) * .02f, .02f, .016f), 0xE6C27A) }
}

// ------------------------------------------------------------------ Sunny, the birds (Kiwi's 2D look)
internal object BirdKind : ToonKind(1.35f, listOf("Whistle", "Preen", "Wing stretch", "Head bob", "Fly a lap", "Head tilt"), perch = true) {
    fun drawPerch(ink: ToonInk) = with(ink) {
        val p = p(); p.moveTo(-1.6f, .06f); p.quadTo(0f, -.02f, 1.6f, .08f); linLine(p, .1f, -1.5f, 0f, 1.5f, 0f, 0x4A3020, 0x6E4A2E)
        val tw = p(); tw.moveTo(.9f, .05f); tw.quadTo(1.1f, -.1f, 1.25f, -.18f); line(tw, 0x5A3C26, .04f)
        fill(ell(1.28f, -.2f, .08f, .04f, -.5f), 0x4E8A3A)
    }
    override fun draw(ink: ToonInk, r: Rig, coat: Int) = with(ink) {
        val c = KIWI_SPECIES[coat.coerceIn(0, KIWI_SPECIES.size - 1)]
        val lx = r.lx; val ly = r.ly; val fl = r.wing; val flap = sin(r.flapPh)
        cv.save(); cv.rotate(r.tailSw * .15f * 57.3f)
        linFill(blob(fa(-.08f, -.25f, .08f, -.25f, .06f, .25f, 0f, .55f, -.06f, .25f)), 0f, -.2f, 0f, .55f, intArrayOf(c.tail or 0xFF000000.toInt(), sh(c.tail, -.35f)), null)
        val t2 = p(); t2.moveTo(0f, -.2f); t2.lineTo(0f, .5f); line(t2, sh(c.tail, -.4f), .01f, .5f); cv.restore()
        if (fl > .02f) for (sd in intArrayOf(-1, 1)) { val s = sd.toFloat()
            cv.save(); cv.translate(s * .18f, -.62f); cv.rotate((s * (-.3f - flap * .9f * fl) + s * (1 - fl) * 1.2f) * 57.3f); cv.scale(s, 1f)
            val wg = blob(fa(0f, -.05f, .25f, -.18f, .55f, -.16f, .78f, -.06f, .62f, .02f, .66f, .08f, .45f, .1f, .48f, .16f, .25f, .14f, 0f, .1f))
            linFill(wg, 0f, 0f, .8f, 0f, intArrayOf(c.wing or 0xFF000000.toInt(), c.wing or 0xFF000000.toInt(), c.edge or 0xFF000000.toInt()), floatArrayOf(0f, .6f, 1f), min(1f, fl * 1.5f))
            for (k in floatArrayOf(.35f, .5f, .62f)) { val f = p(); f.moveTo(k, -.12f); f.lineTo(k + .04f, .1f); line(f, c.edge, .012f, .6f * min(1f, fl * 1.5f)) }
            cv.restore() }
        vol(blob(fa(-.26f, -.35f, -.3f, -.62f, -.2f, -.86f, 0f, -.92f, .2f, -.86f, .3f, -.62f, .26f, -.35f, .12f, -.2f, -.12f, -.2f)), c.body, 0f, -.6f, .4f)
        vol(blob(fa(-.17f, -.4f, -.19f, -.62f, 0f, -.74f, .19f, -.62f, .17f, -.4f, 0f, -.26f)), c.belly, 0f, -.52f, .25f, .25f, -.1f)
        for (row in 0 until 3) for (k in -1..1) { val f = p(); val cx = k * .08f + (row % 2) * .04f; val cy = -.62f + row * .08f
            f.addArc(cx - .045f, cy - .045f, cx + .045f, cy + .045f, 11.5f, 157f); line(f, sh(c.belly, -.15f), .008f, .5f) }
        if (fl < .98f) for (sd in intArrayOf(-1, 1)) { val s = sd.toFloat()
            cv.save(); cv.translate(s * .22f, -.62f); cv.rotate(s * (.12f + r.wingLift * .6f) * 57.3f)
            val wf = blob(fa(0f, -.18f, s * .1f, -.12f, s * .11f, .12f, s * .04f, .4f, s * -.04f, .18f, s * -.05f, -.08f))
            linFill(wf, 0f, -.18f, 0f, .4f, intArrayOf(c.wing or 0xFF000000.toInt(), c.wing or 0xFF000000.toInt(), c.edge or 0xFF000000.toInt()), floatArrayOf(0f, .7f, 1f), 1 - fl)
            val budgie = c.name == "Budgie"
            for (k in floatArrayOf(-.1f, -.02f, .06f, .14f, .22f, .3f)) { val f = p(); f.moveTo(s * -.04f, k); f.quadTo(s * .05f, k + .04f, s * .1f, k + .015f); line(f, c.edge, if (budgie) .025f else .01f, (if (budgie) .85f else .5f) * (1 - fl)) }
            cv.restore() }
        for (sd in intArrayOf(-1, 1)) { val s = sd.toFloat(); val up = if (sd > 0) r.step * .12f else 0f
            val ft = p(); ft.moveTo(s * .08f, -.24f); ft.lineTo(s * .09f, -.04f - up); line(ft, 0x8A8A90, .035f)
            for (k in intArrayOf(-1, 1)) { val tw = p(); tw.moveTo(s * .09f, -.04f - up); tw.quadTo(s * .09f + k * .05f, -.02f - up, s * .09f + k * .06f, .03f - up); line(tw, 0x8A8A90, .025f) } }
        cv.save(); cv.translate(r.headX, -1.06f + r.headY); cv.rotate(r.headTilt * 57.3f)
        vol(ell(0f, 0f, .24f, .23f), c.head, 0f, -.02f, .26f)
        if (c.face != c.head) fill(ell(0f, .05f, .19f, .14f), c.face, .85f)
        when (c.name) {
            "Ringneck" -> { val rg = p(); rg.addArc(-.22f, .04f, .22f, .2f, 11.5f, 157f); line(rg, 0x111111, .025f); line(rg, c.mark, .012f, .9f) }
            "Cockatiel" -> { for (k in 0..2) { val cr = p(); cr.moveTo(-.02f + k * .03f, -.2f); cr.quadTo(-.05f + k * .05f, -.45f, .06f + k * .05f, -.52f + k * .04f); line(cr, c.head, .03f) }
                for (sd in intArrayOf(-1, 1)) fill(ell(sd * .15f, .04f, .06f, .05f), c.mark, .9f) }
            "Budgie" -> { for (sd in intArrayOf(-1, 1)) fill(ell(sd * .13f, .12f, .035f, .035f), c.mark)
                for (k in 0 until 4) { val b = p(); val bx = -.12f + k * .08f; b.addArc(bx - .04f, -.16f, bx + .04f, -.08f, 0f, 180f); line(b, 0x222222, .01f, .6f) } }
            "Lovebird" -> fill(ell(0f, .04f, .2f, .13f), c.mark, .35f)
        }
        for (sd in intArrayOf(-1, 1)) { val s = sd.toFloat()
            fill(ell(s * .11f + lx * .03f, -.03f + ly * .02f, .075f, .075f), 0xFFFFFF, .9f)
            eye(s * .11f + lx * .04f, -.03f + ly * .02f, .058f, lx, ly, r.blink * (if (r.sleep) 0f else 1f), 0x2A1A10, c.head, r.happy) }
        val bx = lx * .05f; val by = .07f + ly * .02f; val mo = r.mouth
        vol(blob(fa(-.05f + bx, by + .02f, .05f + bx, by + .02f, bx, by + .08f + mo * .08f)), sh(c.beak, -.25f), bx, by + .04f, .06f)
        vol(blob(fa(-.07f + bx, by - .04f, bx, by - .07f, .07f + bx, by - .04f, .04f + bx, by + .06f, bx, by + .12f - mo * .03f, -.04f + bx, by + .06f)), c.beak, bx, by, .09f, .35f, -.2f)
        fill(ell(-.025f + bx, by - .03f, .02f, .012f), 0xFFFFFF, .45f)
        cv.restore()
    }
    override fun chargeProp(ink: ToonInk, t: Float) = with(ink) {
        val st = p(); st.moveTo(0f, 0f); st.quadTo(.05f, -.2f, .02f, -.35f); line(st, 0xB89A5A, .015f)
        for (i in 0 until 8) fill(ell(.02f + (if (i % 2 == 0) -.03f else .05f), -.08f - i * .035f, .022f, .016f), 0xE6C27A)
    }
}

// ------------------------------------------------------------------ Acorn, Nutty the squirrel (short neck: the head sits on the shoulders)
internal object SquirrelKind : ToonKind(1.45f, listOf("Nibble", "Tail flick", "Lookout", "Zoomies", "Scratch", "Head tilt")) {
    private val seg = arrayOf(floatArrayOf(0f, 0f, .42f, -.08f, .5f, -.55f, .3f, -.86f), floatArrayOf(.3f, -.86f, .14f, -1.1f, .24f, -1.42f, .5f, -1.36f))
    private val spX = FloatArray(29); private val spY = FloatArray(29); private val outline = FloatArray(29 * 4)
    init {
        for (k in 0..28) { val t = k / 28f; val s = if (t < .5f) seg[0] else seg[1]; val u = if (t < .5f) t * 2 else (t - .5f) * 2; val v = 1 - u
            spX[k] = v * v * v * s[0] + 3 * v * v * u * s[2] + 3 * v * u * u * s[4] + u * u * u * s[6]
            spY[k] = v * v * v * s[1] + 3 * v * v * u * s[3] + 3 * v * u * u * s[5] + u * u * u * s[7] }
        val n = 29
        for (k in 0 until n) { val a = max(0, k - 1); val b = min(n - 1, k + 1); var nx = -(spY[b] - spY[a]); var ny = spX[b] - spX[a]; val l = hypot(nx, ny).let { if (it == 0f) 1f else it }; nx /= l; ny /= l
            val t = k / (n - 1f); val wdt = .07f + .25f * sin(min(1f, t * 1.25f) * PI.toFloat() * .8f) + (if (t > .8f) .05f * (t - .8f) / .2f else 0f)
            val tl = if (k % 2 == 1) 1f else .88f; val tr = if (k % 2 == 1) .88f else 1f
            outline[k * 2] = spX[k] + nx * wdt * tl; outline[k * 2 + 1] = spY[k] + ny * wdt * tl
            val j = 2 * n - 1 - k; outline[j * 2] = spX[k] - nx * wdt * tr; outline[j * 2 + 1] = spY[k] - ny * wdt * tr }
    }
    override fun draw(ink: ToonInk, r: Rig, coat: Int) = with(ink) {
        val c = NUTTY_COATS[coat.coerceIn(0, NUTTY_COATS.size - 1)]; val fur = c.fur; val dark = c.dark; val bellyC = c.belly; val tailC = c.tail; val lx = r.lx; val ly = r.ly
        cv.save(); cv.translate(.2f, -.1f); cv.rotate(r.tailFlick * .22f * 57.3f)
        val tailP = blob(outline, .9f)
        linFill(tailP, .2f, 0f, .4f, -1.4f, intArrayOf(sh(tailC, -.1f), tailC or 0xFF000000.toInt(), sh(tailC, .18f)), floatArrayOf(0f, .5f, 1f))
        cv.save(); cv.clipPath(tailP)
        val s2 = p(); s2.moveTo(spX[0], spY[0]); for (k in 1 until 29) s2.lineTo(spX[k] - .04f, spY[k]); line(s2, sh(tailC, .45f), .16f, .35f)
        for (k in 2 until 29) { val a = k * 2.1f; for (sd in intArrayOf(-1, 1)) { val f = p(); f.moveTo(spX[k], spY[k]); f.lineTo(spX[k] + sd * cos(a) * .2f, spY[k] + sin(a) * .08f - .05f); line(f, sh(tailC, .28f), .014f, .4f) } }
        cv.restore(); line(tailP, sh(tailC, -.3f), .012f, .5f); cv.restore()
        for (sd in intArrayOf(-1, 1)) { val s = sd.toFloat(); vol(ell(s * .2f, -.2f, .17f, .2f, s * -.3f), fur, s * .2f, -.2f, .2f); vol(ell(s * .24f, -.03f, .15f, .05f), dark, s * .24f, -.03f, .15f, .2f, -.2f) }
        vol(blob(fa(-.24f, -.06f, -.3f, -.36f, -.22f, -.66f, 0f, -.74f, .22f, -.66f, .3f, -.36f, .24f, -.06f, 0f, -.02f)), fur, 0f, -.38f, .4f)
        vol(blob(fa(-.15f, -.1f, -.18f, -.38f, -.1f, -.62f, 0f, -.66f, .1f, -.62f, .18f, -.38f, .15f, -.1f, 0f, -.06f)), bellyC, 0f, -.38f, .3f, .2f, -.08f)
        val hy = -.5f + r.nibble * .04f
        if (r.acorn) { vol(ell(0f, hy - .02f, .08f, .09f), 0xB07A3A, 0f, hy, .1f, .3f, -.3f)
            vol(blob(fa(-.09f, hy - .06f, .09f, hy - .06f, .07f, hy - .13f, 0f, hy - .15f, -.07f, hy - .13f)), 0x6E4A2A, 0f, hy - .1f, .1f, .2f, -.3f)
            val st = p(); st.moveTo(0f, hy - .15f); st.lineTo(.02f, hy - .19f); line(st, 0x4A3020, .015f) }
        for (sd in intArrayOf(-1, 1)) { val s = sd.toFloat()
            vol(blob(fa(s * .2f, -.62f, s * .12f, -.66f, s * .05f, hy - .02f, s * .07f, hy + .05f, s * .15f, hy + .02f)), fur, s * .13f, -.56f, .15f, .2f, -.25f)
            vol(ell(s * .065f, hy + .01f, .045f, .04f), sh(fur, -.15f), s * .065f, hy, .05f) }
        cv.save(); cv.translate(r.headX, -.9f + r.headY); cv.rotate(r.headTilt * 57.3f)
        for (sd in intArrayOf(-1, 1)) { val s = sd.toFloat()
            cv.save(); cv.translate(s * .16f, -.17f); cv.rotate(s * (.28f + r.ear * .3f) * 57.3f)
            vol(blob(fa(-.07f, .04f, -.04f, -.1f, 0f, -.2f, .04f, -.1f, .07f, .04f)), fur, 0f, -.06f, .12f)
            fill(blob(fa(-.035f, .02f, 0f, -.12f, .035f, .02f)), 0xF2A08A, .65f)
            vol(blob(fa(-.035f, -.15f, 0f, -.27f, s * .05f, -.34f, s * .04f, -.24f, .035f, -.15f)), sh(fur, -.2f), 0f, -.24f, .08f, .2f, -.3f)
            cv.restore() }
        vol(blob(fa(-.25f, -.02f, -.22f, -.18f, 0f, -.24f, .22f, -.18f, .25f, -.02f, .17f, .12f, 0f, .17f, -.17f, .12f)), fur, 0f, -.05f, .28f)
        vol(blob(fa(-.14f, .04f, 0f, -.02f, .14f, .04f, .1f, .14f, 0f, .16f, -.1f, .14f)), bellyC, lx * .02f, .07f, .16f, .2f, -.1f)
        for (sd in intArrayOf(-1, 1)) { val s = sd.toFloat()
            fill(ell(s * .12f + lx * .03f, -.06f + ly * .02f, .075f, .07f), sh(fur, .35f), .6f)
            eye(s * .12f + lx * .04f, -.06f + ly * .02f, .06f, lx, ly, r.blink * (if (r.sleep) 0f else 1f), 0x1A0E08, dark, r.happy) }
        fill(blob(fa(-.025f + lx * .04f, .05f, .025f + lx * .04f, .05f, lx * .04f, .08f)), 0x3A1A14)
        val m = p(); m.moveTo(-.035f + lx * .04f, .11f); m.quadTo(lx * .04f, .13f + r.mouth * .04f, .035f + lx * .04f, .11f); line(m, 0x3A1A14, .012f)
        if (r.nibble > .1f || r.mouth > .2f) fill(ell(lx * .04f, .125f, .012f, .012f + r.mouth * .01f), 0xFFFFFF)
        cv.restore()
    }
    override fun chargeProp(ink: ToonInk, t: Float) = with(ink) { vol(ell(0f, 0f, .08f, .09f), 0xB07A3A, 0f, 0f, .1f); vol(ell(0f, -.07f, .09f, .04f), 0x6E4A2A, 0f, -.07f, .09f) }
}
