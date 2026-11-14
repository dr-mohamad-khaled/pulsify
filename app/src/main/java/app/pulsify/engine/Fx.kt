package app.pulsify.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Everything drawn around the widget creatures: speech bubbles, dizzy stars, hearts, thrown
 * toys, paper planes, laser scans, glass cracks. Painted with a hardware Canvas into one
 * screen-sized texture, composited on top in GL. Nothing is painted while nothing is happening.
 */
class Fx(private val context: Context) {
    var now = 0f
    private var W = 1f; private var H = 1f; private var q = 1f
    private val items = ArrayList<Item>()
    private val adding = ArrayList<Item>()
    private val rnd = Random(7)
    val alive: Boolean get() = items.isNotEmpty() || adding.isNotEmpty()

    // Paint kit.
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()
    private val r = RectF()
    private val ink = Ink()
    private val face: Typeface by lazy {
        val base = try { androidx.core.content.res.ResourcesCompat.getFont(context, app.pulsify.R.font.bricolage) } catch (_: Throwable) { null } ?: Typeface.DEFAULT
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, 760, false) else Typeface.create(base, Typeface.BOLD)
    }

    // GL.
    private var prog = 0; private var vbo = 0; private var tex = 0
    private var uTex = -1; private var uMtx = -1; private var uA = -1
    private var stx: SurfaceTexture? = null
    private var surface: Surface? = null
    @Volatile private var fresh = false
    private var hasFrame = false
    private val mtx = FloatArray(16)
    private var software = false

    // ------------------------------------------------------------------ lifecycle

    fun init() {
        val vs = "attribute vec2 aPos;\nvarying vec2 vUv;\nvoid main(){ vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }\n"
        val fs = """#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vUv;
uniform samplerExternalOES uTex;
uniform mat4 uMtx;
uniform float uA;
void main(){ gl_FragColor = texture2D(uTex, (uMtx * vec4(vUv, 0.0, 1.0)).xy) * uA; }
"""
        prog = Gl.program(vs, fs)
        uTex = GLES20.glGetUniformLocation(prog, "uTex"); uMtx = GLES20.glGetUniformLocation(prog, "uMtx"); uA = GLES20.glGetUniformLocation(prog, "uA")
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0); vbo = ids[0]
        val tri = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
        val fb = ByteBuffer.allocateDirect(tri.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(tri); fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, tri.size * 4, fb, GLES20.GL_STATIC_DRAW)
        GLES20.glGenTextures(1, ids, 0); tex = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val s = SurfaceTexture(tex)
        s.setOnFrameAvailableListener { fresh = true }
        stx = s; surface = Surface(s); hasFrame = false
        if (W > 1f) stx?.setDefaultBufferSize(max(1, (W * q).toInt()), max(1, (H * q).toInt()))
    }

    fun size(w: Int, h: Int) {
        W = max(1, w).toFloat(); H = max(1, h).toFloat()
        q = if (max(w, h) > 1500) 0.75f else 1f
        ink.maxX = W
        stx?.setDefaultBufferSize(max(1, (W * q).toInt()), max(1, (H * q).toInt()))
    }

    fun clearAll() { items.clear(); adding.clear() }

    fun release() {
        surface?.release(); surface = null
        stx?.release(); stx = null
        if (prog != 0) GLES20.glDeleteProgram(prog)
        if (vbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
        if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
        prog = 0; vbo = 0; tex = 0; hasFrame = false
        clearAll()
    }

    fun step(dt: Float, time: Float) {
        now = time
        if (adding.isNotEmpty()) { items += adding; adding.clear() }
        val it = items.iterator()
        while (it.hasNext()) {
            val i = it.next()
            if (now < i.start) continue
            i.step(dt)
            if (i.done) it.remove()
        }
        if (adding.isNotEmpty()) { items += adding; adding.clear() }
    }

    fun draw(alpha: Float) {
        if (items.isEmpty()) return
        val s = surface ?: return
        val c: Canvas? = try { if (software) s.lockCanvas(null) else s.lockHardwareCanvas() } catch (t: Throwable) {
            if (!software) { software = true; app.pulsify.core.CrashLog.note("fx: software canvas") }
            null
        }
        if (c != null) {
            try {
                c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                c.save(); c.scale(q, q)
                for (i in items) if (now >= i.start) { val k = ((now - i.start) / i.life).coerceIn(0f, 1f); i.paint(c, k) }
                c.restore()
            } finally { s.unlockCanvasAndPost(c) }
        }
        if (fresh) {
            fresh = false
            try { stx?.updateTexImage(); stx?.getTransformMatrix(mtx); hasFrame = true } catch (_: Throwable) {}
        }
        if (!hasFrame) return
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(prog)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniformMatrix4fv(uMtx, 1, false, mtx, 0)
        GLES20.glUniform1f(uA, alpha)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun add(i: Item) { adding += i }

    // ------------------------------------------------------------------ items

    private abstract inner class Item(val start: Float, val life: Float) {
        open val done: Boolean get() = now > start + life
        open fun step(dt: Float) {}
        abstract fun paint(c: Canvas, k: Float)
    }

    /** Time-based alpha: quick fade in, fade out over the last [out] of the life. */
    private fun fade(k: Float, inn: Float = 0.08f, out: Float = 0.25f): Float =
        min(1f, k / inn) * min(1f, (1f - k) / out)

    private fun backOut(t: Float): Float { val k = t.coerceIn(0f, 1f) - 1f; val s = 2.2f; return 1f + k * k * ((s + 1f) * k + s) }

    // Speech bubbles ---------------------------------------------------------

    private inner class Bubble(val b: Buddy, val text: String, val style: Int, start: Float, life: Float) : Item(start, life) {
        var killed = false
        override val done: Boolean get() = killed || super.done
        override fun paint(c: Canvas, k: Float) {
            val age = (now - start)
            val pop = backOut(age / 0.24f)
            val out = min(1f, (1f - k) / 0.18f)
            val a = min(1f, age / 0.06f) * out
            val sz = clampF(b.s * (if (style == WHISPER) 0.15f else 0.18f), 15f, 44f)
            tp.typeface = face; tp.textSize = sz
            val tw = tp.measureText(text)
            val bw = tw + sz * 1.3f; val bh = sz * 1.85f
            var x = b.headX + b.s * 0.18f
            val y = b.headY - b.s * 0.16f - age * sz * 0.18f
            if (W > bw + 12f) x = x.coerceIn(bw / 2f + 6f, W - bw / 2f - 6f)
            val sc = pop * (0.92f + 0.08f * out)
            c.save(); c.translate(x, y - bh / 2f); c.scale(sc, sc)
            if (style == SHOUT) c.rotate(-5f + sin(age * 30f) * 2f * max(0f, 1f - age * 3f))
            when (style) {
                SHOUT -> {
                    val rx = bw * 0.62f; val ry = bh * 0.78f
                    path.reset()
                    val n = 14
                    for (i in 0..n * 2) {
                        val ang = i * PI.toFloat() / n
                        val rr = if (i % 2 == 0) 1f else 0.8f
                        val px = cos(ang) * rx * rr; val py = sin(ang) * ry * rr
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    ink.glow(c, 0f, 0f, rx * 1.3f, ry * 1.3f, 0xFFFFB36B.toInt(), 0.35f * a)
                    p.shader = LinearGradient(0f, -ry, 0f, ry, 0xFFFFE6A8.toInt(), 0xFFFFB85C.toInt(), Shader.TileMode.CLAMP); p.alpha = (a * 255).toInt()
                    c.drawPath(path, p); p.shader = null
                    st.color = 0xFF3A2410.toInt(); st.alpha = (a * 220).toInt(); st.strokeWidth = sz * 0.1f
                    c.drawPath(path, st)
                    tp.color = 0xFF2A1607.toInt()
                }
                THINK -> {
                    val col = 0xFFF1EAFF.toInt()
                    p.shader = null; p.color = col; p.alpha = (a * 245).toInt()
                    r.set(-bw / 2f, -bh / 2f, bw / 2f, bh / 2f); c.drawRoundRect(r, bh / 2f, bh / 2f, p)
                    val bumps = max(3, (bw / (bh * 0.55f)).toInt())
                    for (i in 0 until bumps) {
                        val bx = -bw / 2f + bh * 0.45f + i * (bw - bh * 0.9f) / max(1, bumps - 1)
                        c.drawCircle(bx, -bh * 0.38f, bh * 0.36f, p); c.drawCircle(bx + bh * 0.1f, bh * 0.38f, bh * 0.3f, p)
                    }
                    // little thought dots toward the head
                    val dx = b.headX - x; val dy = (b.headY - (y - bh / 2f)) + bh * 0.2f
                    c.drawCircle(dx * 0.45f / sc, bh * 0.5f + dy * 0.25f / sc, bh * 0.13f, p)
                    c.drawCircle(dx * 0.75f / sc, bh * 0.5f + dy * 0.6f / sc, bh * 0.08f, p)
                    tp.color = 0xFF2B1F45.toInt()
                }
                WHISPER -> {
                    p.shader = null; p.color = 0xFF1E192B.toInt(); p.alpha = (a * 225).toInt()
                    r.set(-bw / 2f, -bh / 2f, bw / 2f, bh / 2f); c.drawRoundRect(r, bh / 2f, bh / 2f, p)
                    st.color = 0xFFCDB4FF.toInt(); st.alpha = (a * 120).toInt(); st.strokeWidth = 1.5f
                    c.drawRoundRect(r, bh / 2f, bh / 2f, st)
                    tp.color = 0xFFE4D8FF.toInt()
                }
                else -> {
                    ink.glow(c, 0f, bh * 0.1f, bw * 0.7f, bh, 0x66000000, a)
                    p.shader = null; p.color = Color.WHITE; p.alpha = (a * 250).toInt()
                    r.set(-bw / 2f, -bh / 2f, bw / 2f, bh / 2f); c.drawRoundRect(r, bh / 2f, bh / 2f, p)
                    val tx = ((b.headX - x) / sc).coerceIn(-bw * 0.3f, bw * 0.3f)
                    path.reset(); path.moveTo(tx - sz * 0.32f, bh / 2f - 1f); path.lineTo(tx - sz * 0.1f, bh / 2f + sz * 0.5f); path.lineTo(tx + sz * 0.3f, bh / 2f - 1f); path.close()
                    c.drawPath(path, p)
                    tp.color = 0xFF1A1325.toInt()
                }
            }
            tp.alpha = (a * 255).toInt()
            c.drawText(text, 0f, sz * 0.35f, tp)
            c.restore()
        }
    }

    fun bubble(b: Buddy, text: String, style: Int, life: Float, at: Float = now) {
        // One bubble per creature at a time: the new one replaces any it is still showing.
        (items + adding).forEach { if (it is Bubble && it.b === b && it.start <= at && it.start + it.life > at) it.killed = true }
        add(Bubble(b, text, style, at, life))
    }

    // Dizzy stars --------------------------------------------------------------

    private fun star(c: Canvas, x: Float, y: Float, rad: Float, rot: Float, a: Float, col: Int = 0xFFFFD86B.toInt()) {
        ink.glow(c, x, y, rad * 2.2f, rad * 2.2f, col, 0.35f * a)
        path.reset()
        for (i in 0 until 10) {
            val ang = rot + i * PI.toFloat() / 5f - PI.toFloat() / 2f
            val rr = if (i % 2 == 0) rad else rad * 0.45f
            if (i == 0) path.moveTo(x + cos(ang) * rr, y + sin(ang) * rr) else path.lineTo(x + cos(ang) * rr, y + sin(ang) * rr)
        }
        path.close()
        p.shader = RadialGradient(x - rad * 0.25f, y - rad * 0.3f, rad * 1.3f, intArrayOf(0xFFFFFBE6.toInt(), col, Ink.darken(col, 0.25f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        p.alpha = (a * 255).toInt(); c.drawPath(path, p); p.shader = null
    }

    fun stars(b: Buddy, at: Float, life: Float) = add(object : Item(at, life) {
        override fun paint(c: Canvas, k: Float) {
            val a = fade(k, 0.1f, 0.2f)
            val rx = b.s * 0.42f; val ry = b.s * 0.11f
            val cx = b.headX; val cy = b.headY - b.s * 0.02f
            st.color = 0xFFFFE9A8.toInt(); st.alpha = (a * 70).toInt(); st.strokeWidth = 2f
            r.set(cx - rx, cy - ry, cx + rx, cy + ry); c.drawOval(r, st)
            for (i in 0 until 3) {
                val ang = (now - start) * 5.5f + i * 2.094f
                val depth = sin(ang)
                star(c, cx + cos(ang) * rx, cy + depth * ry, b.s * 0.07f * (0.85f + 0.3f * depth), ang * 1.5f, a * (0.6f + 0.4f * depth))
            }
        }
    })

    // "!" -------------------------------------------------------------------------

    fun exclaim(b: Buddy, at: Float) = add(object : Item(at, 1.1f) {
        override fun paint(c: Canvas, k: Float) {
            val age = now - start
            val a = fade(k, 0.04f, 0.3f)
            val sc = backOut(age / 0.2f)
            val x = b.headX + b.s * 0.42f; val y = b.headY - b.s * 0.15f
            // shock lines
            val e = min(1f, age / 0.25f)
            for (i in -1..1) {
                val ang = -PI.toFloat() / 2f + i * 0.55f
                val r0 = b.s * (0.18f + 0.1f * e); val r1 = b.s * (0.3f + 0.16f * e)
                ink.line(c, b.headX + cos(ang) * r0, b.headY - b.s * 0.05f + sin(ang) * r0, b.headX + cos(ang) * r1, b.headY - b.s * 0.05f + sin(ang) * r1,
                    0xFFFFE3A3.toInt(), b.s * 0.035f, a * (1f - e * 0.5f))
            }
            val sz = b.s * 0.42f
            c.save(); c.translate(x, y); c.scale(sc, sc); c.rotate(10f + sin(age * 40f) * 6f * max(0f, 1f - age * 2.5f))
            tp.typeface = face; tp.textSize = sz
            ink.glow(c, 0f, -sz * 0.3f, sz * 0.8f, sz * 0.9f, 0xFFFFB36B.toInt(), 0.45f * a)
            st.color = 0xFF2A1607.toInt(); st.alpha = (a * 255).toInt(); st.strokeWidth = sz * 0.14f
            tp.style = Paint.Style.STROKE; tp.strokeWidth = sz * 0.14f; tp.strokeJoin = Paint.Join.ROUND; tp.color = 0xFF2A1607.toInt(); tp.alpha = (a * 255).toInt()
            c.drawText("!", 0f, 0f, tp)
            tp.style = Paint.Style.FILL; tp.color = 0xFFFFC970.toInt(); tp.alpha = (a * 255).toInt()
            c.drawText("!", 0f, 0f, tp)
            c.restore()
        }
    })

    fun sweat(b: Buddy, at: Float) = add(object : Item(at, 1.2f) {
        override fun paint(c: Canvas, k: Float) {
            val a = fade(k, 0.1f, 0.3f)
            val x = b.headX - b.s * 0.38f; val y = b.headY + b.s * 0.1f + easeF(k) * b.s * 0.22f
            val rr = b.s * 0.065f
            path.reset(); path.moveTo(x, y - rr * 2.2f)
            path.cubicTo(x + rr * 1.3f, y - rr * 0.6f, x + rr * 1.1f, y + rr, x, y + rr)
            path.cubicTo(x - rr * 1.1f, y + rr, x - rr * 1.3f, y - rr * 0.6f, x, y - rr * 2.2f)
            p.shader = LinearGradient(x - rr, y - rr * 2f, x + rr, y + rr, 0xFFE6F7FF.toInt(), 0xFF6CC6FF.toInt(), Shader.TileMode.CLAMP); p.alpha = (a * 240).toInt()
            c.drawPath(path, p); p.shader = null
            ink.ell(c, x - rr * 0.3f, y - rr * 0.2f, rr * 0.25f, rr * 0.4f, Color.WHITE, a * 0.9f)
        }
    })

    fun anger(b: Buddy, at: Float, life: Float) = add(object : Item(at, life) {
        override fun paint(c: Canvas, k: Float) {
            val a = fade(k, 0.08f, 0.2f)
            val pulse = 1f + 0.18f * sin((now - start) * 14f)
            val x = b.headX + b.s * 0.33f; val y = b.headY + b.s * 0.02f; val rr = b.s * 0.1f * pulse
            ink.glow(c, x, y, rr * 2.2f, rr * 2.2f, 0xFFFF5A6E.toInt(), 0.3f * a)
            for (i in 0 until 4) {
                val ang = i * PI.toFloat() / 2f + PI.toFloat() / 4f
                val cx0 = x + cos(ang) * rr * 0.9f; val cy0 = y + sin(ang) * rr * 0.9f
                ink.arc(c, cx0, cy0, rr * 0.55f, ang + PI.toFloat() * 0.75f, ang + PI.toFloat() * 1.25f, 0xFFFF4D63.toInt(), rr * 0.3f, a)
            }
        }
    })

    // Particles ---------------------------------------------------------------------

    private inner class Particle(val kind: Int, var x: Float, var y: Float, var vx: Float, var vy: Float, val g: Float, val size: Float,
                                 val col: Int, start: Float, life: Float, var rot: Float = 0f, val vr: Float = 0f) : Item(start, life) {
        override fun step(dt: Float) { vy += g * dt; x += vx * dt; y += vy * dt; rot += vr * dt; vx *= 0.985f }
        override fun paint(c: Canvas, k: Float) {
            val a = fade(k, 0.08f, 0.35f)
            when (kind) {
                P_HEART -> heart(c, x, y, size * (0.7f + 0.3f * backOut(k * 4f)), rot, a, col)
                P_SPARK -> sparkle(c, x, y, size * (1f - k * 0.5f), rot, a, col)
                P_NOTE -> { ink.glow(c, x, y, size * 1.4f, size * 1.4f, col, 0.3f * a); ink.note(c, x, y, size, col, a) }
                P_DOT -> { ink.glow(c, x, y, size * 1.6f, size * 1.6f, col, 0.25f * a); ink.ell(c, x, y, size * 0.5f, size * 0.5f, col, a * 0.85f); ink.shine(c, x - size * 0.15f, y - size * 0.15f, size * 0.15f, size * 0.12f, 0.8f * a) }
                P_ZED -> { tp.typeface = face; tp.textSize = size; tp.color = col; tp.alpha = (a * 230).toInt(); c.drawText("z", x, y, tp) }
                P_PUFF -> {
                    val rr = size * (0.6f + 0.8f * easeF(k))
                    ink.glow(c, x, y, rr * 1.3f, rr * 1.3f, col, 0.4f * a)
                    ink.ell(c, x, y, rr, rr, col, a * 0.8f)
                }
                P_OBJ -> obj(c, col, x, y, size, rot, a)
            }
        }
    }

    private fun heart(c: Canvas, x: Float, y: Float, s: Float, rot: Float, a: Float, col: Int) {
        if (a <= 0.02f) return
        c.save(); c.translate(x, y); c.rotate(rot * 57.3f)
        ink.glow(c, 0f, 0f, s * 1.8f, s * 1.8f, col, 0.3f * a)
        path.reset()
        path.moveTo(0f, s * 0.85f)
        path.cubicTo(-s * 1.25f, -s * 0.05f, -s * 0.65f, -s * 1.05f, 0f, -s * 0.38f)
        path.cubicTo(s * 0.65f, -s * 1.05f, s * 1.25f, -s * 0.05f, 0f, s * 0.85f)
        p.shader = LinearGradient(0f, -s, 0f, s, Ink.lighten(col, 0.35f), col, Shader.TileMode.CLAMP); p.alpha = (a * 255).toInt()
        c.drawPath(path, p); p.shader = null
        ink.ell(c, -s * 0.38f, -s * 0.38f, s * 0.18f, s * 0.12f, Color.WHITE, 0.75f * a, -0.6f)
        c.restore()
    }

    private fun sparkle(c: Canvas, x: Float, y: Float, s: Float, rot: Float, a: Float, col: Int) {
        if (a <= 0.02f) return
        ink.glow(c, x, y, s * 2f, s * 2f, col, 0.4f * a)
        c.save(); c.translate(x, y); c.rotate(rot * 57.3f)
        path.reset(); path.moveTo(0f, -s)
        path.quadTo(s * 0.14f, -s * 0.14f, s, 0f); path.quadTo(s * 0.14f, s * 0.14f, 0f, s)
        path.quadTo(-s * 0.14f, s * 0.14f, -s, 0f); path.quadTo(-s * 0.14f, -s * 0.14f, 0f, -s); path.close()
        p.shader = null; p.color = Ink.lighten(col, 0.6f); p.alpha = (a * 255).toInt(); c.drawPath(path, p)
        c.restore()
    }

    fun hearts(b: Buddy, at: Float, n: Int) {
        for (i in 0 until n) add(Particle(P_HEART, b.headX + (rnd.nextFloat() - 0.5f) * b.s * 0.5f, b.headY, (rnd.nextFloat() - 0.5f) * b.s * 0.9f,
            -b.s * (0.9f + rnd.nextFloat() * 0.6f), b.s * 0.4f, b.s * (0.07f + rnd.nextFloat() * 0.04f),
            listOf(0xFFFF6FA3.toInt(), 0xFFFF8FB8.toInt(), 0xFFCDB4FF.toInt()).random(rnd), at + i * 0.09f, 1.3f, (rnd.nextFloat() - 0.5f) * 0.6f, (rnd.nextFloat() - 0.5f) * 1.5f))
    }

    fun sparkles(b: Buddy, at: Float, n: Int) = burstAt(b.midX, b.midY - b.s * 0.1f, b.s, n, at, 0xFFFFE3A3.toInt())

    private fun burstAt(x: Float, y: Float, s: Float, n: Int, at: Float, col: Int) {
        for (i in 0 until n) {
            val ang = i * 2f * PI.toFloat() / n + rnd.nextFloat() * 0.5f
            val sp = s * (1.2f + rnd.nextFloat() * 1.1f)
            add(Particle(P_SPARK, x + cos(ang) * s * 0.25f, y + sin(ang) * s * 0.25f, cos(ang) * sp, sin(ang) * sp, s * 0.6f,
                s * (0.05f + rnd.nextFloat() * 0.05f), if (i % 3 == 0) 0xFFCDB4FF.toInt() else col, at, 0.75f + rnd.nextFloat() * 0.3f, rnd.nextFloat() * 3f, 4f))
        }
    }

    fun burst(x: Float, y: Float, s: Float, col: Int) {
        burstAt(x, y, s, 10, now, col)
        add(Ring(x, y, now, 0.5f, s * 0.1f, s * 0.8f, col, s * 0.05f))
        add(Ring(x, y, now + 0.06f, 0.55f, s * 0.1f, s * 1.1f, 0xFFCDB4FF.toInt(), s * 0.03f))
    }

    fun note(b: Buddy) {
        val d = if (rnd.nextBoolean()) 1f else -1f
        add(Particle(P_NOTE, b.headX + d * b.s * 0.3f, b.headY, d * b.s * 0.4f, -b.s * 0.8f, 0f, b.s * 0.13f,
            listOf(0xFFCDB4FF.toInt(), 0xFFFFB36B.toInt(), 0xFF9D4EDD.toInt(), 0xFF7FE3D4.toInt()).random(rnd), now, 1.4f, 0f, 0f))
    }

    fun sickBubbles(b: Buddy, at: Float, life: Float) = add(object : Item(at, life) {
        var acc = 0f
        override fun step(dt: Float) {
            acc += dt
            while (acc > 0.18f) {
                acc -= 0.18f
                add(Particle(P_DOT, b.headX + (rnd.nextFloat() - 0.3f) * b.s * 0.4f, b.headY + b.s * 0.15f, (rnd.nextFloat() - 0.5f) * b.s * 0.3f,
                    -b.s * (0.5f + rnd.nextFloat() * 0.4f), 0f, b.s * (0.05f + rnd.nextFloat() * 0.05f), 0xFF9BE37A.toInt(), now, 1.0f))
            }
        }
        override fun paint(c: Canvas, k: Float) {}
    })

    fun zzz(b: Buddy, at: Float, life: Float) = add(object : Item(at, life) {
        var acc = 0.4f
        override fun step(dt: Float) {
            acc += dt
            if (acc > 0.55f) {
                acc = 0f
                add(Particle(P_ZED, b.headX + b.s * 0.25f, b.headY, b.s * 0.25f, -b.s * 0.45f, 0f, b.s * (0.14f + rnd.nextFloat() * 0.08f),
                    0xFFCDB4FF.toInt(), now, 1.4f))
            }
        }
        override fun paint(c: Canvas, k: Float) {}
    })

    fun heartStream(a: Buddy, b: Buddy, at: Float, n: Int) {
        for (i in 0 until n) add(object : Item(at + i * 0.22f, 1.25f) {
            val wob = rnd.nextFloat() * 6f
            override fun paint(c: Canvas, k: Float) {
                val e = easeF(k)
                val x0 = a.headX; val y0 = a.headY + a.s * 0.1f; val x1 = b.headX; val y1 = b.headY + b.s * 0.15f
                val mx = (x0 + x1) / 2f; val my = min(y0, y1) - abs(x1 - x0) * 0.35f - a.s * 0.3f
                val x = (1 - e) * (1 - e) * x0 + 2 * (1 - e) * e * mx + e * e * x1 + sin(k * 9f + wob) * a.s * 0.05f
                val y = (1 - e) * (1 - e) * y0 + 2 * (1 - e) * e * my + e * e * y1
                heart(c, x, y, a.s * (0.07f + 0.03f * sin(k * PI.toFloat())), sin(k * 6f + wob) * 0.3f, fade(k, 0.1f, 0.12f), 0xFFFF6FA3.toInt())
            }
        })
    }

    // Lines, rings, cracks -------------------------------------------------------------

    private inner class Ring(val x: Float, val y: Float, start: Float, life: Float, val r0: Float, val r1: Float, val col: Int, val w: Float) : Item(start, life) {
        override fun paint(c: Canvas, k: Float) {
            val e = 1f - (1f - k) * (1f - k)
            st.shader = null; st.color = col; st.alpha = ((1f - k) * 220).toInt(); st.strokeWidth = w * (1f - k * 0.6f)
            c.drawCircle(x, y, r0 + (r1 - r0) * e, st)
        }
    }

    fun ripple(x: Float, y: Float, s: Float) {
        add(Ring(x, y, now, 0.55f, s * 0.1f, s * 0.75f, 0xFFCDB4FF.toInt(), s * 0.04f))
        add(Ring(x, y, now + 0.08f, 0.6f, s * 0.08f, s * 0.55f, 0xFFFFB36B.toInt(), s * 0.025f))
    }

    fun speed(b: Buddy, at: Float, life: Float, dir: Float) = add(object : Item(at, life) {
        val ys = FloatArray(5) { 0.15f + it * 0.17f + rnd.nextFloat() * 0.06f }
        override fun paint(c: Canvas, k: Float) {
            val a = fade(k, 0.1f, 0.4f)
            for (i in ys.indices) {
                val y = b.footY - ys[i] * b.s * 1.1f
                val x0 = b.footX - dir * b.s * (0.5f + 0.1f * i % 2)
                val len = b.s * (0.5f + 0.35f * ((i * 37) % 5) / 5f) * (1f - k * 0.5f)
                ink.line(c, x0, y, x0 - dir * len, y, 0xFFF1EAFF.toInt(), b.s * 0.025f, a * 0.7f)
            }
        }
    })

    fun impact(b: Buddy, at: Float, dir: Float) = add(object : Item(at, 1.4f) {
        var x = 0f; var y = 0f; var set = false
        val cracks = FloatArray(6) { rnd.nextFloat() }
        override fun step(dt: Float) { if (!set) { set = true; x = b.footX + dir * b.s * 0.42f; y = b.midY - b.s * 0.1f } }
        override fun paint(c: Canvas, k: Float) {
            if (!set) return
            val a = fade(k, 0.02f, 0.45f)
            val flash = max(0f, 1f - k * 6f)
            ink.glow(c, x, y, b.s * 0.8f, b.s * 0.8f, Color.WHITE, 0.6f * flash)
            // Little glass cracks where it hit the screen.
            for (i in cracks.indices) {
                val ang = i * PI.toFloat() / 3f + cracks[i] * 0.6f
                val len = b.s * (0.25f + cracks[i] * 0.3f) * min(1f, k * 8f)
                val mx = x + cos(ang) * len * 0.5f + sin(ang) * len * 0.12f; val my = y + sin(ang) * len * 0.5f - cos(ang) * len * 0.12f
                path.reset(); path.moveTo(x, y); path.lineTo(mx, my); path.lineTo(x + cos(ang) * len, y + sin(ang) * len)
                st.color = Color.WHITE; st.alpha = (a * 200).toInt(); st.strokeWidth = b.s * 0.012f
                c.drawPath(path, st)
            }
            st.alpha = (a * 120).toInt(); c.drawCircle(x, y, b.s * 0.12f * min(1f, k * 8f), st)
        }
    })

    fun pop(b: Buddy, text: String, at: Float, dir: Float) = add(object : Item(at, 1.0f) {
        override fun paint(c: Canvas, k: Float) { comic(c, text, b.headX + dir * b.s * 0.55f, b.headY - b.s * 0.1f - k * b.s * 0.2f, b.s, k, now - start, dir) }
    })

    fun popAt(text: String, x: Float, y: Float, s: Float) = add(object : Item(now, 1.0f) {
        override fun paint(c: Canvas, k: Float) { comic(c, text, x, y - k * s * 0.25f, s, k, now - start, 0f) }
    })

    private fun comic(c: Canvas, text: String, x: Float, y: Float, s: Float, k: Float, age: Float, dir: Float) {
        val a = fade(k, 0.03f, 0.3f)
        val sz = clampF(s * 0.26f, 18f, 60f)
        c.save(); c.translate(x, y); c.scale(backOut(age / 0.2f), backOut(age / 0.2f)); c.rotate(if (dir < 0f) 8f else -8f)
        tp.typeface = face; tp.textSize = sz
        ink.glow(c, 0f, -sz * 0.3f, sz * 1.8f, sz * 1.1f, 0xFFFFB36B.toInt(), 0.4f * a)
        tp.style = Paint.Style.STROKE; tp.strokeWidth = sz * 0.2f; tp.strokeJoin = Paint.Join.ROUND; tp.color = 0xFF231433.toInt(); tp.alpha = (a * 255).toInt()
        c.drawText(text, 0f, 0f, tp)
        tp.style = Paint.Style.FILL
        tp.shader = LinearGradient(0f, -sz, 0f, 0f, 0xFFFFF6DC.toInt(), 0xFFFFC266.toInt(), Shader.TileMode.CLAMP); tp.alpha = (a * 255).toInt()
        c.drawText(text, 0f, 0f, tp); tp.shader = null
        c.restore()
    }

    fun poof(x: Float, y: Float, s: Float) {
        for (i in 0 until 9) {
            val ang = i * 2f * PI.toFloat() / 9f
            add(Particle(P_PUFF, x + cos(ang) * s * 0.15f, y + sin(ang) * s * 0.15f, cos(ang) * s * 0.9f, sin(ang) * s * 0.6f - s * 0.2f, 0f,
                s * (0.16f + rnd.nextFloat() * 0.08f), if (i % 2 == 0) 0xFFF4EEFF.toInt() else 0xFFD9CCF5.toInt(), now, 0.7f))
        }
        burstAt(x, y, s * 0.8f, 6, now + 0.1f, 0xFFFFE3A3.toInt())
    }

    // Toys that fly between friends ---------------------------------------------------------

    fun toss(kind: Int, from: Buddy, to: Buddy, at: Float, dur: Float, miss: Boolean, onLand: () -> Unit) = add(object : Item(at, dur) {
        var x0 = 0f; var y0 = 0f; var set = false; var landed = false
        val trail = FloatArray(16); var tn = 0
        override fun step(dt: Float) {
            if (!set) { set = true; val d = if (to.footX > from.footX) 1f else -1f; x0 = from.headX + d * from.s * 0.3f; y0 = from.midY - from.s * 0.15f }
            val k = (now - start) / life
            if (k >= 1f && !landed) {
                landed = true; onLand()
                if (miss) {
                    val d = if (to.footX > from.footX) 1f else -1f
                    add(Particle(P_OBJ, to.headX, to.headY - to.s * 0.05f, d * to.s * 0.9f, -to.s * 1.4f, to.s * 4.2f, to.s * 0.11f, kind, now, 0.9f, 0f, d * 9f))
                } else add(Ring(to.headX, to.headY + to.s * 0.1f, now, 0.35f, to.s * 0.05f, to.s * 0.35f, 0xFFFFE3A3.toInt(), to.s * 0.03f))
            }
        }
        override fun paint(c: Canvas, k: Float) {
            if (!set || landed) return
            val x1 = to.headX; val y1 = if (miss) to.headY - to.s * 0.02f else to.headY + to.s * 0.12f
            val dist = abs(x1 - x0)
            val x = x0 + (x1 - x0) * k
            val y = y0 + (y1 - y0) * k - (dist * 0.35f + from.s * 0.55f) * 4f * k * (1f - k)
            if (tn < 8) tn++
            for (i in tn - 1 downTo 1) { trail[i * 2] = trail[(i - 1) * 2]; trail[i * 2 + 1] = trail[(i - 1) * 2 + 1] }
            trail[0] = x; trail[1] = y
            for (i in 1 until tn) ink.ell(c, trail[i * 2], trail[i * 2 + 1], from.s * 0.03f * (1f - i / 8f), from.s * 0.03f * (1f - i / 8f), 0xFFF1EAFF.toInt(), 0.5f * (1f - i / 8f))
            obj(c, kind, x, y, from.s * 0.11f, k * 4f * PI.toFloat(), 1f)
        }
    })

    private fun obj(c: Canvas, kind: Int, x: Float, y: Float, s: Float, rot: Float, a: Float) {
        c.save(); c.translate(x, y); c.rotate(rot * 57.3f)
        when (kind) {
            OBJ_BALL -> {
                ink.glossy(c, 0f, 0f, s, s, 0xFFFF7FB2.toInt(), 0xFFFFD6E8.toInt(), 0xFFB23A74.toInt(), a = a)
                ink.arc(c, 0f, 0f, s * 0.7f, -0.6f, 1.6f, 0xFFFFF2B8.toInt(), s * 0.22f, a)
            }
            OBJ_BONE -> {
                val col = 0xFFF5ECD7.toInt(); val lt = 0xFFFFFFFF.toInt(); val dk = 0xFFC9B48C.toInt()
                ink.glossy(c, 0f, 0f, s * 1.1f, s * 0.32f, col, lt, dk, a = a)
                for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) ink.glossy(c, sx * s * 1.05f, sy * s * 0.28f, s * 0.36f, s * 0.36f, col, lt, dk, a = a)
            }
            OBJ_YARN -> {
                ink.glossy(c, 0f, 0f, s, s, 0xFFE05AA8.toInt(), 0xFFFFB0DD.toInt(), 0xFF8A2563.toInt(), a = a)
                for (i in 0 until 4) ink.arc(c, s * 0.2f * (i - 1.5f), -s * 0.1f, s * 0.8f, 0.5f + i * 0.3f, 2.2f + i * 0.3f, 0xFFFFC7E8.toInt(), s * 0.08f, 0.8f * a)
            }
            OBJ_SEED -> {
                ink.glossy(c, 0f, 0f, s * 0.55f, s, 0xFF3A3530.toInt(), 0xFF8C8478.toInt(), 0xFF141210.toInt(), a = a)
                for (i in -1..1) ink.line(c, i * s * 0.2f, -s * 0.75f, i * s * 0.2f, s * 0.75f, 0xFFE9E1D0.toInt(), s * 0.08f, 0.8f * a)
            }
            OBJ_BUBBLE -> {
                ink.glow(c, 0f, 0f, s * 1.4f, s * 1.4f, 0xFF80F2FF.toInt(), 0.35f * a)
                ink.arc(c, 0f, 0f, s, 0f, 6.3f, 0xFFBDF7FF.toInt(), s * 0.08f, 0.9f * a)
                ink.shine(c, -s * 0.35f, -s * 0.4f, s * 0.28f, s * 0.16f, 0.9f * a)
            }
            OBJ_ROCK -> {
                path.reset(); path.moveTo(-s, -s * 0.2f); path.lineTo(-s * 0.4f, -s * 0.9f); path.lineTo(s * 0.6f, -s * 0.7f)
                path.lineTo(s, s * 0.1f); path.lineTo(s * 0.4f, s * 0.85f); path.lineTo(-s * 0.7f, s * 0.7f); path.close()
                p.shader = RadialGradient(-s * 0.3f, -s * 0.4f, s * 1.6f, intArrayOf(0xFFA59BB5.toInt(), 0xFF5B5268.toInt(), 0xFF2B2533.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                p.alpha = (a * 255).toInt(); c.drawPath(path, p); p.shader = null
                ink.ell(c, -s * 0.2f, -s * 0.15f, s * 0.12f, s * 0.12f, 0xFFFF5A6E.toInt(), a); ink.ell(c, s * 0.25f, -s * 0.15f, s * 0.12f, s * 0.12f, 0xFFFF5A6E.toInt(), a)
            }
            OBJ_ACORN -> {
                ink.glossy(c, 0f, s * 0.25f, s * 0.75f, s * 0.85f, 0xFFC27A3A.toInt(), 0xFFF2BE85.toInt(), 0xFF6E3A14.toInt(), a = a)
                ink.glossy(c, 0f, -s * 0.45f, s * 0.92f, s * 0.42f, 0xFF7A4A24.toInt(), 0xFFB98758.toInt(), 0xFF3B2210.toInt(), a = a)
                for (i in -2..2) ink.line(c, i * s * 0.3f, -s * 0.75f, i * s * 0.3f + s * 0.2f, -s * 0.15f, 0xFF5A3418.toInt(), s * 0.05f, 0.6f * a)
                ink.line(c, 0f, -s * 0.85f, s * 0.15f, -s * 1.2f, 0xFF4A2A12.toInt(), s * 0.14f, a)
            }
            OBJ_GEAR -> {
                path.reset()
                for (i in 0 until 16) {
                    val ang = i * PI.toFloat() / 8f; val rr = if (i % 2 == 0) s else s * 0.75f
                    val a0 = ang - 0.18f; val a1 = ang + 0.18f
                    if (i == 0) path.moveTo(cos(a0) * rr, sin(a0) * rr) else path.lineTo(cos(a0) * rr, sin(a0) * rr)
                    path.lineTo(cos(a1) * rr, sin(a1) * rr)
                }
                path.close()
                p.shader = RadialGradient(-s * 0.3f, -s * 0.4f, s * 1.5f, intArrayOf(0xFFE2FBFF.toInt(), 0xFF64C7D9.toInt(), 0xFF1C5A68.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                p.alpha = (a * 255).toInt(); c.drawPath(path, p); p.shader = null
                ink.ell(c, 0f, 0f, s * 0.3f, s * 0.3f, 0xFF15343C.toInt(), a)
            }
            OBJ_BLOB -> {
                ink.glossy(c, 0f, 0f, s, s * 0.85f, 0xFF7BE07A.toInt(), 0xFFD8FFD0.toInt(), 0xFF2F8A3A.toInt(), a = a)
                c.rotate(-rot * 57.3f)
                ink.eye(c, -s * 0.3f, -s * 0.1f, s * 0.2f); ink.eye(c, s * 0.3f, -s * 0.1f, s * 0.2f)
            }
            else -> ink.glossy(c, 0f, 0f, s, s, 0xFFCDB4FF.toInt(), Color.WHITE, 0xFF6B4FB0.toInt(), a = a)
        }
        c.restore()
    }

    fun plane(x0: Float, y0: Float, to: Buddy?, at: Float, dur: Float, exitDir: Float = 0f, onLand: () -> Unit) = add(object : Item(at, dur) {
        var landed = false
        var px = x0; var py = y0; var ang = 0f
        val trail = FloatArray(40); var tn = 0
        override fun step(dt: Float) {
            val k = ((now - start) / life).coerceIn(0f, 1f)
            val x1: Float; val y1: Float
            if (to != null) { x1 = to.headX; y1 = to.headY - to.s * 0.05f } else { x1 = if (exitDir > 0f) W + 120f else -120f; y1 = y0 - H * 0.12f }
            val e = easeF(k)
            var x = x0 + (x1 - x0) * e
            var y = y0 + (y1 - y0) * e - sin(k * PI.toFloat()) * H * 0.06f
            // a loop-the-loop in the middle
            if (k in 0.35f..0.65f) {
                val l = (k - 0.35f) / 0.3f * 2f * PI.toFloat()
                val R = min(W, H) * 0.07f
                val d = if (x1 >= x0) 1f else -1f
                x += sin(l) * R * d; y += -(1f - cos(l)) * R
            }
            val nx = x - px; val ny = y - py
            if (abs(nx) + abs(ny) > 0.5f) ang = atan2(ny, nx)
            px = x; py = y
            if (tn < 20) tn++
            for (i in tn - 1 downTo 1) { trail[i * 2] = trail[(i - 1) * 2]; trail[i * 2 + 1] = trail[(i - 1) * 2 + 1] }
            trail[0] = x; trail[1] = y
            if (k >= 1f && !landed) { landed = true; onLand() }
        }
        override fun paint(c: Canvas, k: Float) {
            if (landed) return
            val s = min(W, H) * 0.045f
            for (i in 2 until tn step 2) ink.ell(c, trail[i * 2], trail[i * 2 + 1], s * 0.06f, s * 0.06f, 0xFFF1EAFF.toInt(), 0.55f * (1f - i / tn.toFloat()))
            c.save(); c.translate(px, py); c.rotate(ang * 57.3f)
            ink.glow(c, 0f, 0f, s * 1.6f, s * 1.2f, 0xFFCDB4FF.toInt(), 0.3f)
            path.reset(); path.moveTo(s, 0f); path.lineTo(-s, -s * 0.62f); path.lineTo(-s * 0.55f, 0f); path.close()
            p.shader = null; p.color = 0xFFFFFFFF.toInt(); c.drawPath(path, p)
            path.reset(); path.moveTo(s, 0f); path.lineTo(-s, s * 0.5f); path.lineTo(-s * 0.55f, 0f); path.close()
            p.color = 0xFFD8CFEA.toInt(); c.drawPath(path, p)
            ink.line(c, s, 0f, -s * 0.55f, 0f, 0xFFB7A9D6.toInt(), s * 0.06f, 0.9f)
            c.restore()
        }
    })

    fun beam(a: Buddy, b: Buddy, at: Float, life: Float) = add(object : Item(at, life) {
        override fun paint(c: Canvas, k: Float) {
            val al = fade(k, 0.12f, 0.2f)
            val x0 = a.headX; val y0 = a.headY + a.s * 0.38f
            val scan = 0.5f + 0.5f * sin((now - start) * 9f)
            val x1 = b.midX + (scan - 0.5f) * b.s * 0.4f; val y1 = b.headY + (b.footY - b.headY) * (0.15f + 0.7f * scan)
            st.shader = null
            st.color = 0xFFFF3B5C.toInt(); st.alpha = (al * 90).toInt(); st.strokeWidth = a.s * 0.09f; c.drawLine(x0, y0, x1, y1, st)
            st.color = 0xFFFF7A90.toInt(); st.alpha = (al * 200).toInt(); st.strokeWidth = a.s * 0.035f; c.drawLine(x0, y0, x1, y1, st)
            st.color = Color.WHITE; st.alpha = (al * 255).toInt(); st.strokeWidth = a.s * 0.012f; c.drawLine(x0, y0, x1, y1, st)
            ink.glow(c, x1, y1, a.s * 0.25f, a.s * 0.25f, 0xFFFF3B5C.toInt(), 0.7f * al)
            ink.glow(c, x0, y0, a.s * 0.15f, a.s * 0.15f, 0xFFFF7A90.toInt(), 0.8f * al)
            // scan line across the target
            st.color = 0xFFFF7A90.toInt(); st.alpha = (al * 150).toInt(); st.strokeWidth = 2f
            c.drawLine(b.midX - b.s * 0.45f, y1, b.midX + b.s * 0.45f, y1, st)
        }
    })

    companion object {
        const val SPEECH = 0; const val SHOUT = 1; const val THINK = 2; const val WHISPER = 3
        const val OBJ_BALL = 1; const val OBJ_BONE = 2; const val OBJ_YARN = 3; const val OBJ_SEED = 4; const val OBJ_BUBBLE = 5
        const val OBJ_ROCK = 6; const val OBJ_ACORN = 7; const val OBJ_GEAR = 8; const val OBJ_BLOB = 9
        private const val P_HEART = 1; private const val P_SPARK = 2; private const val P_NOTE = 3; private const val P_DOT = 4
        private const val P_ZED = 5; private const val P_PUFF = 6; private const val P_OBJ = 7
    }
}
