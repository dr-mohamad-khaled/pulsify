package app.pulsify.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.robolectric.RuntimeEnvironment
import app.pulsify.core.AudioFrame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.FileOutputStream

/** The Canvas twin of [PuppetGl]: same transform stack, same calls, drawn with Canvas (for tests). */
internal class CanvasPuppet(private val cv: Canvas) : PuppetGl() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var clipM: FloatArray? = null
    private var clipPath: Path? = null
    override fun init() {}
    override val ready get() = true
    override fun begin(w: Int, h: Int) { sw = w.toFloat(); sh = h.toFloat(); creatureBase.copyInto(M); alpha = 1f; clipM = null }
    override fun end() {}
    private fun mat(m: FloatArray) = Matrix().apply { setValues(floatArrayOf(m[0], m[2], m[4], m[1], m[3], m[5], 0f, 0f, 1f)) }
    private fun enter() {
        cv.save()
        val cm = clipM; val cp = clipPath
        if (cm != null && cp != null) { cv.setMatrix(mat(cm)); cv.clipPath(cp); cv.setMatrix(Matrix()) }
        cv.concat(mat(M))
        p.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
    }
    override fun clip(cx: Float, cy: Float, rx: Float, ry: Float, lid: Int, ly: Float, bulge: Float, lx: Float) {
        clipM = M.copyOf()
        val e = Path(); e.addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW)
        if (lid != 0) {
            val c = Path(); val big = 1e4f
            c.moveTo(cx - lx * 2, if (lid == 1) ly - big else ly + big)
            for (i in 0..40) { val u = -2f + 4f * i / 40; val y = if (lid == 1) ly + bulge * (1 - u * u) else ly - bulge * (1 - u * u); c.lineTo(cx + u * lx, y) }
            c.lineTo(cx + lx * 2, if (lid == 1) ly - big else ly + big); c.close()
            e.op(c, Path.Op.INTERSECT)
        }
        clipPath = e
    }
    override fun clipRect(x0: Float, y0: Float, x1: Float, y1: Float) { clipM = M.copyOf(); clipPath = Path().apply { addRect(x0, y0, x1, y1, Path.Direction.CW) } }
    override fun noClip() { clipM = null; clipPath = null }
    override fun quadRepeat(t: PTex?, x: Float, y: Float, w: Float, h: Float, u0: Float, u1: Float) {
        val b = t?.bmp ?: return; enter()
        val tw = w / (u1 - u0); var u = kotlin.math.floor(u0); val x0 = x - (u0 - u) * tw
        var xx = x0
        cv.save(); cv.clipRect(x, y, x + w, y + h)
        while (xx < x + w) { cv.drawBitmap(b, null, RectF(xx, y, xx + tw, y + h), p); xx += tw; u += 1f }
        cv.restore(); cv.restore()
    }
    override fun quad(t: PTex?, x: Float, y: Float, w: Float, h: Float) {
        val b = t?.bmp ?: return; enter(); cv.drawBitmap(b, null, RectF(x, y, x + w, y + h), p); cv.restore()
    }
    override fun mesh(m: PMesh, a: PTex?, b: PTex?, mix: Float, ox: Float, oy: Float) {
        val bm = (if (b != null && mix > .5f) b else a)?.bmp ?: return
        val v = FloatArray(m.n * 2)
        for (i in 0 until m.n) { v[i * 2] = (m.pos[i * 2] + ox - m.p.x) / m.p.w * bm.width; v[i * 2 + 1] = (m.pos[i * 2 + 1] + oy - m.p.y) / m.p.h * bm.height }
        enter(); cv.translate(m.p.x, m.p.y); cv.scale(m.p.w / bm.width, m.p.h / bm.height)
        cv.drawBitmapMesh(bm, m.gx, m.gy, v, 0, null, 0, p); cv.restore()
    }
    override fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        enter(); val q = Paint(Paint.ANTI_ALIAS_FLAG); q.color = color or 0xFF000000.toInt(); q.alpha = (alpha * 255).toInt(); cv.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), q); cv.restore()
    }
    override fun curve(x0: Float, y0: Float, qx: Float, qy: Float, x1: Float, y1: Float, width: Float, color: Int) {
        val sv = clipM; clipM = null; enter(); clipM = sv
        val q = Paint(Paint.ANTI_ALIAS_FLAG); q.style = Paint.Style.STROKE; q.strokeCap = Paint.Cap.ROUND; q.strokeWidth = width; q.color = color or 0xFF000000.toInt(); q.alpha = (alpha * 255).toInt()
        cv.drawPath(Path().apply { moveTo(x0, y0); quadTo(qx, qy, x1, y1) }, q); cv.restore()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PuppetTest {
    private val ctx: android.content.Context get() = RuntimeEnvironment.getApplication()
    private fun run(s: ToonScene, inp: SceneInput, secs: Float) { var t = 0f; while (t < secs) { s.update(1 / 60f, inp); t += 1 / 60f } }
    private fun snap(s: ToonScene, name: String) {
        val W = 540; val H = 1170
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); val cv = Canvas(b); cv.drawColor(Color.rgb(16, 20, 32))
        // overlay (shadow, particles, badge) the way the scene places its box, then the puppet on top
        s.debugOverlay(cv)
        s.debugPuppet(CanvasPuppet(cv), W, H)
        FileOutputStream("/tmp/pp_$name.png").use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun puppets() {
        println("ASSETS " + ctx.assets.list("puppets")?.toList() + " " + (ctx.assets.list("puppets/duck")?.size))
        val a0 = PuppetArt("duck"); a0.loadBitmaps(ctx); println("LOADED " + a0.loaded + " " + a0.parts.keys + " " + a0.parts["body"]?.tex?.bmp?.width)
        for ((name, kind) in listOf("duck" to DuckArtKind, "parrot" to ParrotArtKind)) {
            val s = ToonScene(kind, ctx) { it }; s.onSurfaceChanged(540, 1170)
            val inp = SceneInput()
            run(s, inp, 1.2f); snap(s, "${name}_idle")
            s.touchDown(.95f, .3f); run(s, inp, .6f); snap(s, "${name}_look"); s.touchUp()
            // carry and throw
            s.touchDown(.5f, .78f); run(s, inp, .05f)
            var y = .78f; var x = .5f
            for (i in 0 until 12) { x += .02f; y -= .03f; s.touchMove(x, y); run(s, inp, 1 / 60f) }
            snap(s, "${name}_carried")
            s.touchUp(); run(s, inp, .3f); snap(s, "${name}_air")
            run(s, inp, 1.2f); snap(s, "${name}_land"); println("$name: ${s.debugState}")
            run(s, inp, 4f); snap(s, "${name}_back")
            for (t in kind.idle) { s.perform(ToonScene.TRICKS.entries.firstOrNull { it.value == t }?.key ?: continue); run(s, inp, .7f); snap(s, "${name}_${t.replace(' ', '_')}"); run(s, inp, 3f) }
            inp.audio.source = AudioFrame.Source.MUSIC; inp.audio.beat = 1f
            run(s, inp, 6f); snap(s, "${name}_dance")
            inp.audio.source = AudioFrame.Source.NONE; inp.charging = true; inp.battery = .76f; run(s, inp, 2f); snap(s, "${name}_charge")
        }
    }
}
