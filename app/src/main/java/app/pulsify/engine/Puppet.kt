package app.pulsify.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Painted puppets: creatures painted in Flow, cut into parts, and moved here part by part.
 * Each part is a textured quad under a 2D transform stack (in "rig px", the pixel space of the
 * cut-out art); the head is a grid mesh whose points slide with their depth, so the face turns
 * like a round head (2.5D). Eyes and lids are clipped in the fragment shader (an ellipse, an
 * optional rectangle and an optional lid curve), so lids are made of the creature's own fur.
 */

internal class PTex(val id: Int, val w: Int, val h: Int, val bmp: Bitmap? = null)
internal class PPart(val x: Float, val y: Float, val w: Float, val h: Float, var tex: PTex? = null)

/** One puppet's art: rig.json + its part pictures, under assets/puppets/<dir>/. */
internal class PuppetArt(private val dir: String) {
    var mesh: PMesh? = null
    var rig: JSONObject? = null; private set
    val parts = HashMap<String, PPart>()
    var loaded = false; private set
    private var failed = false
    /** The code-made bark that tiles along the perch (birds only). */
    var bark: PTex? = null

    /** Tests: the pictures as bitmaps (no GL), for the Canvas twin of [PuppetGl]. */
    fun loadBitmaps(ctx: Context) {
        meta(ctx); if (rig == null) return
        for ((name, p) in parts) { val b = ctx.assets.open("puppets/$dir/$name.png").use { BitmapFactory.decodeStream(it) } ?: continue; p.tex = PTex(0, b.width, b.height, b) }
        if (parts.containsKey("branch")) bark = Bark.bitmap().let { PTex(0, it.width, it.height, it) }
        loaded = true
    }
    fun meta(ctx: Context) {
        if (rig != null || failed) return
        try {
            val j = JSONObject(ctx.assets.open("puppets/$dir/rig.json").bufferedReader().readText())
            rig = j
            val ps = j.getJSONObject("parts")
            for (k in ps.keys()) { val p = ps.getJSONObject(k); parts[k] = PPart(p.getDouble("x").toFloat(), p.getDouble("y").toFloat(), p.getDouble("w").toFloat(), p.getDouble("h").toFloat()) }
            if (dir == "parrot") {   // the open-beak head shares the head's place; the flying pose has its own frame
                parts["head"]?.let { parts["headOpen"] = PPart(it.x, it.y, it.w, it.h) }
                val fp = j.getJSONObject("fly").getJSONObject("parts")
                for (k in fp.keys()) { val p = fp.getJSONObject(k); parts[k] = PPart(p.getDouble("x").toFloat(), p.getDouble("y").toFloat(), p.getDouble("w").toFloat(), p.getDouble("h").toFloat()) }
            }
        } catch (t: Throwable) { failed = true; android.util.Log.e("Pulsify", "puppet $dir", t) }
    }

    /** Upload the pictures (GL thread). [density]: screen px per rig px, so a part is never stored much bigger than it is drawn. */
    fun load(ctx: Context, density: Float) {
        if (loaded) return
        meta(ctx); if (rig == null) return
        val f = (density * 1.35f).coerceIn(.3f, 1f)
        for ((name, p) in parts) {
            if (name == "flyBody") continue
            try {
                val src = ctx.assets.open("puppets/$dir/$name.png").use { BitmapFactory.decodeStream(it) } ?: continue
                val k = if (name.startsWith("fly")) f * 1.7f else f            // the flying parts are stored at 0.6 of their frame
                val tw = max(4, (src.width * k.coerceAtMost(1f)).roundToInt()); val th = max(4, (src.height * k.coerceAtMost(1f)).roundToInt())
                val b = if (tw < src.width) Bitmap.createScaledBitmap(src, tw, th, true).also { src.recycle() } else src
                val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
                p.tex = PTex(ids[0], b.width, b.height); b.recycle()
            } catch (_: Throwable) {}
        }
        if (parts.containsKey("branch")) try {
            val b = Bark.bitmap()
            val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0); GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            bark = PTex(ids[0], b.width, b.height); b.recycle()
        } catch (_: Throwable) {}
        loaded = true
    }

    /** The GL context was lost: ids are gone, upload again next time. */
    fun forget() { parts.values.forEach { it.tex = null }; bark = null; loaded = false }
    fun release() {
        val ids = (parts.values.mapNotNull { it.tex?.id } + listOfNotNull(bark?.id)).toIntArray()
        bark = null
        if (ids.isNotEmpty()) GLES20.glDeleteTextures(ids.size, ids, 0)
        parts.values.forEach { it.tex = null }; loaded = false
    }

    fun f(key: String) = rig!!.getDouble(key).toFloat()
    fun arr(key: String): FloatArray { val a = rig!!.getJSONArray(key); return FloatArray(a.length()) { a.getDouble(it).toFloat() } }
    fun arr2(key: String): Array<FloatArray> { val a = rig!!.getJSONArray(key); return Array(a.length()) { i -> val b = a.getJSONArray(i); FloatArray(b.length()) { b.getDouble(it).toFloat() } } }
    fun pivot(name: String): FloatArray { val a = rig!!.getJSONObject("pivots").getJSONArray(name); return floatArrayOf(a.getDouble(0).toFloat(), a.getDouble(1).toFloat()) }
}

/** A grid over a part whose points can be displaced (the 2.5D head). */
internal class PMesh(val p: PPart, val gx: Int, val gy: Int) {
    val n = (gx + 1) * (gy + 1)
    val pos = FloatArray(n * 2)
    val uv = FloatArray(n * 2).also { for (j in 0..gy) for (i in 0..gx) { val k = (j * (gx + 1) + i) * 2; it[k] = i.toFloat() / gx; it[k + 1] = j.toFloat() / gy } }
    val idx: ShortBuffer = run {
        val s = ShortArray(gx * gy * 6); var o = 0
        for (j in 0 until gy) for (i in 0 until gx) { val a = j * (gx + 1) + i; val b = a + 1; val c = a + gx + 1; val d = c + 1
            s[o++] = a.toShort(); s[o++] = b.toShort(); s[o++] = c.toShort(); s[o++] = b.toShort(); s[o++] = d.toShort(); s[o++] = c.toShort() }
        ByteBuffer.allocateDirect(s.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().put(s).also { it.position(0) }
    }
    val count = gx * gy * 6
    /** Rebuild the points: [disp] returns the shift of rest point (x, y) into out[0..1]. */
    inline fun build(disp: (Float, Float, FloatArray) -> Unit) {
        val d = FloatArray(2)
        for (j in 0..gy) for (i in 0..gx) { val k = (j * (gx + 1) + i) * 2
            val x = p.x + i.toFloat() / gx * p.w; val y = p.y + j.toFloat() / gy * p.h
            d[0] = 0f; d[1] = 0f; disp(x, y, d); pos[k] = x + d[0]; pos[k + 1] = y + d[1] }
    }
}

/** Draws puppet parts straight to the screen with a 2D transform stack and shader clipping. */
internal open class PuppetGl {
    private var prog = 0
    private var aP = 0; private var aT = 0; private var aC = 0
    private var uA = -1; private var uB = -1; private var uMix = -1; private var uAlpha = -1; private var uE = -1; private var uL = -1; private var uR = -1; private var uSolid = -1; private var uAA = -1
    private var buf: FloatBuffer = ByteBuffer.allocateDirect(4 * 6 * 2048).order(ByteOrder.nativeOrder()).asFloatBuffer()
    internal var sw = 1f; internal var sh = 1f

    // affine a b c d e f: x' = a x + c y + e, y' = b x + d y + f
    internal var M = FloatArray(6)
    private val stack = ArrayList<FloatArray>()
    private val alphaStack = ArrayList<Float>()
    var alpha = 1f
    /** Base (rig px -> screen px) of the creature, and of the world (for things that stay put, like a branch). */
    val creatureBase = FloatArray(6); val worldBase = FloatArray(6)

    // clip (in the frame current when it was set)
    private var clipInv: FloatArray? = null
    private val ce = FloatArray(4); private val cl = FloatArray(4); private val cr = FloatArray(4)
    private var clipScale = 1f

    open fun init() {
        prog = Gl.program(VS, FS)
        aP = GLES20.glGetAttribLocation(prog, "aP"); aT = GLES20.glGetAttribLocation(prog, "aT"); aC = GLES20.glGetAttribLocation(prog, "aC")
        uA = GLES20.glGetUniformLocation(prog, "uA"); uB = GLES20.glGetUniformLocation(prog, "uB"); uMix = GLES20.glGetUniformLocation(prog, "uMix")
        uAlpha = GLES20.glGetUniformLocation(prog, "uAlpha"); uE = GLES20.glGetUniformLocation(prog, "uE"); uL = GLES20.glGetUniformLocation(prog, "uL")
        uR = GLES20.glGetUniformLocation(prog, "uR"); uSolid = GLES20.glGetUniformLocation(prog, "uSolid"); uAA = GLES20.glGetUniformLocation(prog, "uAA")
    }
    open val ready get() = prog != 0
    fun release() { if (prog != 0) GLES20.glDeleteProgram(prog); prog = 0 }
    fun forget() { prog = 0 }

    open fun begin(w: Int, h: Int) {
        sw = w.toFloat(); sh = h.toFloat(); stack.clear(); alphaStack.clear(); alpha = 1f; clipInv = null
        creatureBase.copyInto(M)
        GLES20.glUseProgram(prog)
        GLES20.glDisable(GLES20.GL_CULL_FACE); GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUniform1i(uA, 0); GLES20.glUniform1i(uB, 1)
    }
    open fun end() {
        GLES20.glDisableVertexAttribArray(aP); GLES20.glDisableVertexAttribArray(aT); GLES20.glDisableVertexAttribArray(aC)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    // ---- transform stack
    fun save() { stack.add(M.copyOf()); alphaStack.add(alpha) }
    fun restore() { if (stack.isNotEmpty()) { M = stack.removeAt(stack.size - 1); alpha = alphaStack.removeAt(alphaStack.size - 1) } }
    fun useWorld() { worldBase.copyInto(M) }
    fun identity() { M[0] = 1f; M[1] = 0f; M[2] = 0f; M[3] = 1f; M[4] = 0f; M[5] = 0f }
    fun snapshot(into: FloatArray) { M.copyInto(into) }
    private fun mul(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
        val m = M
        val na = m[0] * a + m[2] * b; val nb = m[1] * a + m[3] * b
        val nc = m[0] * c + m[2] * d; val nd = m[1] * c + m[3] * d
        val ne = m[0] * e + m[2] * f + m[4]; val nf = m[1] * e + m[3] * f + m[5]
        m[0] = na; m[1] = nb; m[2] = nc; m[3] = nd; m[4] = ne; m[5] = nf
    }
    fun translate(x: Float, y: Float) = mul(1f, 0f, 0f, 1f, x, y)
    fun scale(x: Float, y: Float) = mul(x, 0f, 0f, y, 0f, 0f)
    fun rotate(a: Float) { val c = cos(a); val s = sin(a); mul(c, s, -s, c, 0f, 0f) }
    inline fun rot(px: Float, py: Float, a: Float, fn: () -> Unit) { save(); translate(px, py); rotate(a); translate(-px, -py); fn(); restore() }
    inline fun around(px: Float, py: Float, fn: () -> Unit) { translate(px, py); fn(); translate(-px, -py) }

    // ---- clipping (an ellipse, a rectangle and/or a lid curve, all in the current frame)
    /** [lid]: 0 none, 1 keep above the curve, 2 keep below it. The curve is y = ly - bulge*(1-u^2) for mode 2 and ly + bulge*(1-u^2) for mode 1, u = (x-cx)/lx. */
    open fun clip(cx: Float, cy: Float, rx: Float, ry: Float, lid: Int = 0, ly: Float = 0f, bulge: Float = 0f, lx: Float = 1f) {
        clipInv = inv(M); ce[0] = cx; ce[1] = cy; ce[2] = rx; ce[3] = ry; cl[0] = lid.toFloat(); cl[1] = ly; cl[2] = bulge; cl[3] = lx; cr[0] = 0f; cr[1] = 0f; cr[2] = 0f; cr[3] = 0f
        clipScale = sqrt(abs(M[0] * M[3] - M[1] * M[2])).coerceAtLeast(1e-4f)
    }
    open fun clipRect(x0: Float, y0: Float, x1: Float, y1: Float) { clipInv = inv(M); ce[2] = 0f; cl[0] = 0f; cr[0] = x0; cr[1] = y0; cr[2] = x1; cr[3] = y1; clipScale = sqrt(abs(M[0] * M[3] - M[1] * M[2])).coerceAtLeast(1e-4f) }
    open fun noClip() { clipInv = null }
    private fun inv(m: FloatArray): FloatArray {
        val det = m[0] * m[3] - m[1] * m[2]; val id = if (abs(det) < 1e-12f) 0f else 1f / det
        val a = m[3] * id; val b = -m[1] * id; val c = -m[2] * id; val d = m[0] * id
        return floatArrayOf(a, b, c, d, -(a * m[4] + c * m[5]), -(b * m[4] + d * m[5]))
    }

    // ---- drawing
    private fun put(x: Float, y: Float, u: Float, v: Float) {
        val m = M; val X = m[0] * x + m[2] * y + m[4]; val Y = m[1] * x + m[3] * y + m[5]
        buf.put(X / sw * 2f - 1f); buf.put(1f - Y / sh * 2f); buf.put(u); buf.put(v)
        val ci = clipInv
        if (ci != null) { buf.put(ci[0] * X + ci[2] * Y + ci[4]); buf.put(ci[1] * X + ci[3] * Y + ci[5]) } else { buf.put(0f); buf.put(0f) }
    }
    private fun uniforms(a: PTex?, b: PTex?, mix: Float, solid: Int, solidA: Float) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, a?.id ?: 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, (b ?: a)?.id ?: 0)
        GLES20.glUniform1f(uMix, if (b == null) 0f else mix.coerceIn(0f, 1f))
        GLES20.glUniform1f(uAlpha, alpha.coerceIn(0f, 1f))
        if (clipInv != null) {
            GLES20.glUniform4f(uE, ce[0], ce[1], ce[2], ce[3]); GLES20.glUniform4f(uL, cl[0], cl[1], cl[2], cl[3]); GLES20.glUniform4f(uR, cr[0], cr[1], cr[2], cr[3])
            // ~1.2 screen px of soft edge, in clip units
            val m = M; val s = sqrt(abs(m[0] * m[3] - m[1] * m[2])); GLES20.glUniform1f(uAA, 1.2f / max(1e-4f, clipScale))
        } else { GLES20.glUniform4f(uE, 0f, 0f, 0f, 0f); GLES20.glUniform4f(uL, 0f, 0f, 0f, 1f); GLES20.glUniform4f(uR, 0f, 0f, 0f, 0f); GLES20.glUniform1f(uAA, 1f) }
        if (solid != 0 || solidA > 0f) GLES20.glUniform4f(uSolid, ((solid shr 16) and 255) / 255f, ((solid shr 8) and 255) / 255f, (solid and 255) / 255f, 1f)
        else GLES20.glUniform4f(uSolid, 0f, 0f, 0f, 0f)
    }
    private fun attribs() {
        buf.position(0); GLES20.glEnableVertexAttribArray(aP); GLES20.glVertexAttribPointer(aP, 2, GLES20.GL_FLOAT, false, 24, buf)
        buf.position(2); GLES20.glEnableVertexAttribArray(aT); GLES20.glVertexAttribPointer(aT, 2, GLES20.GL_FLOAT, false, 24, buf)
        buf.position(4); GLES20.glEnableVertexAttribArray(aC); GLES20.glVertexAttribPointer(aC, 2, GLES20.GL_FLOAT, false, 24, buf)
    }
    /** A part at its place, offset by (ox, oy); or at an explicit rect. */
    fun part(p: PPart?, ox: Float = 0f, oy: Float = 0f) { if (p != null) quad(p.tex, p.x + ox, p.y + oy, p.w, p.h) }
    /** A quad whose picture repeats sideways from u0 to u1 (the texture must wrap). */
    open fun quadRepeat(t: PTex?, x: Float, y: Float, w: Float, h: Float, u0: Float, u1: Float) {
        if (t == null || alpha <= .003f) return
        buf.position(0); put(x, y, u0, 0f); put(x + w, y, u1, 0f); put(x, y + h, u0, 1f); put(x + w, y + h, u1, 1f)
        uniforms(t, null, 0f, 0, 0f); attribs(); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }
    open fun quad(t: PTex?, x: Float, y: Float, w: Float, h: Float) {
        if (t == null || alpha <= .003f) return
        buf.position(0); put(x, y, 0f, 0f); put(x + w, y, 1f, 0f); put(x, y + h, 0f, 1f); put(x + w, y + h, 1f, 1f)
        uniforms(t, null, 0f, 0, 0f); attribs(); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }
    open fun mesh(m: PMesh, a: PTex?, b: PTex?, mix: Float, ox: Float = 0f, oy: Float = 0f) {
        if (a == null || alpha <= .003f) return
        if (buf.capacity() < m.n * 6) buf = ByteBuffer.allocateDirect(4 * 6 * m.n).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.position(0)
        for (i in 0 until m.n) put(m.pos[i * 2] + ox, m.pos[i * 2 + 1] + oy, m.uv[i * 2], m.uv[i * 2 + 1])
        uniforms(a, b, mix, 0, 0f); attribs(); m.idx.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, m.count, GLES20.GL_UNSIGNED_SHORT, m.idx)
    }
    /** A filled ellipse in a flat colour (catch-lights). */
    open fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        if (alpha <= .003f) return
        val savedInv = clipInv; val sce = ce.copyOf(); val scl = cl.copyOf(); val scr = cr.copyOf(); val ss = clipScale
        // the ellipse itself is the clip; an outer clip still applies through the alpha of what's behind (good enough here)
        clip(cx, cy, rx, ry)
        buf.position(0); put(cx - rx, cy - ry, 0f, 0f); put(cx + rx, cy - ry, 1f, 0f); put(cx - rx, cy + ry, 0f, 1f); put(cx + rx, cy + ry, 1f, 1f)
        uniforms(null, null, 0f, color or 0x1000000, 1f); attribs(); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        clipInv = savedInv; sce.copyInto(ce); scl.copyInto(cl); scr.copyInto(cr); clipScale = ss
    }
    /** A round-capped stroke along a quadratic curve (lid creases, happy-eye arcs). */
    open fun curve(x0: Float, y0: Float, qx: Float, qy: Float, x1: Float, y1: Float, width: Float, color: Int) {
        if (alpha <= .003f) return
        val n = 14; buf.position(0)
        for (i in 0..n) {
            val t = i.toFloat() / n; val it = 1 - t
            val x = it * it * x0 + 2 * it * t * qx + t * t * x1; val y = it * it * y0 + 2 * it * t * qy + t * t * y1
            val dx = 2 * it * (qx - x0) + 2 * t * (x1 - qx); val dy = 2 * it * (qy - y0) + 2 * t * (y1 - qy)
            val l = max(1e-4f, sqrt(dx * dx + dy * dy)); val w2 = width * .5f * (if (i == 0 || i == n) .6f else 1f)
            val nx = -dy / l * w2; val ny = dx / l * w2
            put(x + nx, y + ny, 0f, 0f); put(x - nx, y - ny, 0f, 0f)
        }
        val sv = clipInv; clipInv = null
        uniforms(null, null, 0f, color or 0x1000000, 1f); attribs(); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, (n + 1) * 2)
        clipInv = sv
    }

    companion object {
        private const val VS = "attribute vec2 aP; attribute vec2 aT; attribute vec2 aC; varying vec2 vT; varying vec2 vC;" +
            "void main(){ vT = aT; vC = aC; gl_Position = vec4(aP, 0.0, 1.0); }"
        private const val FS = "precision mediump float; varying vec2 vT; varying vec2 vC;" +
            "uniform sampler2D uA; uniform sampler2D uB; uniform float uMix; uniform float uAlpha;" +
            "uniform vec4 uE; uniform vec4 uL; uniform vec4 uR; uniform vec4 uSolid; uniform float uAA;" +
            "void main(){" +
            " vec4 c = uSolid.a > 0.5 ? vec4(uSolid.rgb, 1.0) : mix(texture2D(uA, vT), texture2D(uB, vT), uMix);" +
            " float k = 1.0;" +
            " if (uE.z > 0.0) { vec2 d = (vC - uE.xy) / uE.zw; float r = length(d); float e = uAA / min(uE.z, uE.w); k *= 1.0 - smoothstep(1.0 - e, 1.0 + e, r); }" +
            " if (uL.x > 0.5) { float u = (vC.x - uE.x) / uL.w; float b = uL.z * (1.0 - u * u);" +
            "   if (uL.x < 1.5) k *= 1.0 - smoothstep(-uAA, uAA, vC.y - (uL.y + b)); else k *= smoothstep(-uAA, uAA, vC.y - (uL.y - b)); }" +
            " if (uR.z > uR.x) { k *= step(uR.x, vC.x) * step(vC.x, uR.z) * step(uR.y, vC.y) * step(vC.y, uR.w); }" +
            " gl_FragColor = (uSolid.a > 0.5 ? vec4(c.rgb, 1.0) : c) * (k * uAlpha);" +
            " if (uSolid.a > 0.5) gl_FragColor.rgb *= 1.0;" +
            "}"
    }
}
