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
import android.util.Log
import android.view.Surface
import app.pulsify.core.AudioFrame
import app.pulsify.core.Mood
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A creature painted with the Android Canvas API on the GPU (Surface.lockHardwareCanvas into a
 * SurfaceTexture), then composited over the world in the GL frame. This lets characters use
 * real vector art — gradients, curves, text — at hardware speed.
 *
 * Subclasses work in render pixels with y pointing down: [W] x [H], ground at [floorY].
 */
abstract class CanvasCreature(protected val context: Context) : Creature {
    override var listener: SceneListener? = null
    override var bottomInset = 0f
        set(v) { field = v.coerceIn(0f, 0.8f) }

    protected var W = 1f; protected var H = 1f
    protected var time = 0f
    protected var sinceTouch = 99f
    protected val ink = Ink()
    private var groundUv = Float.NaN

    // State shared by every canvas creature, refreshed each frame.
    protected var musicOn = false
    protected var night = false
    protected var charging = false
    protected var lowBattery = false
    protected var battery = 1f
    protected var audio: AudioFrame = AudioFrame()

    private var prog = 0; private var vbo = 0; private var tex = 0
    private var uTex = -1; private var uMtx = -1; private var uDim = -1
    private var st: SurfaceTexture? = null
    private var surface: Surface? = null
    @Volatile private var fresh = false
    private var hasFrame = false
    private val mtx = FloatArray(16)
    private var q = 1f
    private var software = false

    /** Where the feet go (px from the top), above the Studio dock when it is open. */
    protected val floorY: Float
        get() {
            val g = if (groundUv.isNaN()) H * 0.8f else H * 0.5f - groundUv * min(W, H)
            return min(g, H * (1f - bottomInset) - min(W, H) * 0.04f)
        }
    protected val unit: Float get() = min(W, H)

    /** Called when the size, ground or dock changes. */
    protected open fun layout() {}
    /** Advances the simulation. */
    protected abstract fun step(dt: Float)
    /** Paints one frame (canvas is cleared to transparent, in render pixels). */
    protected abstract fun paint(c: Canvas)

    override fun onSurfaceCreated() {
        val vs = "attribute vec2 aPos;\nvarying vec2 vUv;\nvoid main(){ vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }\n"
        val fs = """#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vUv;
uniform samplerExternalOES uTex;
uniform mat4 uMtx;
uniform float uDim;
void main(){ vec4 c = texture2D(uTex, (uMtx * vec4(vUv, 0.0, 1.0)).xy); gl_FragColor = vec4(c.rgb * (1.0 - uDim), c.a); }
"""
        prog = Gl.program(vs, fs)
        uTex = GLES20.glGetUniformLocation(prog, "uTex"); uMtx = GLES20.glGetUniformLocation(prog, "uMtx")
        uDim = GLES20.glGetUniformLocation(prog, "uDim")
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0); vbo = ids[0]
        val tri = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
        val fb = ByteBuffer.allocateDirect(tri.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(tri)
        fb.position(0)
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
        st = s
        surface = Surface(s)
        hasFrame = false
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        W = max(1, width).toFloat(); H = max(1, height).toFloat()
        // The art is smooth vector shapes: 3/4 resolution on big screens looks the same and
        // saves memory and fill.
        q = if (max(width, height) > 1500) 0.75f else 1f
        st?.setDefaultBufferSize(max(1, (W * q).toInt()), max(1, (H * q).toInt()))
        layout()
    }

    override fun setGround(y: Float) {
        if (y == groundUv || (y.isNaN() && groundUv.isNaN())) return
        groundUv = y; layout()
    }

    override fun update(dt: Float, input: SceneInput) {
        time += dt; sinceTouch += dt
        audio = input.audio
        musicOn = input.audio.source != AudioFrame.Source.NONE
        night = input.mood == Mood.NIGHT
        charging = input.charging
        lowBattery = input.lowBattery
        battery = input.battery
        step(min(dt, 1f / 20f))
    }

    override fun draw(input: SceneInput) {
        val s = surface ?: return
        val c: Canvas? = try {
            if (software) s.lockCanvas(null) else s.lockHardwareCanvas()
        } catch (t: Throwable) {
            if (!software) { software = true; Log.w("Pulsify", "hardware canvas unavailable", t); app.pulsify.core.CrashLog.note("canvas: software fallback") }
            null
        }
        if (c != null) {
            try {
                c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                ink.maxX = W
                c.save(); c.scale(q, q)
                paint(c)
                c.restore()
            } finally {
                s.unlockCanvasAndPost(c)
            }
        }
        if (fresh) {
            fresh = false
            try { st?.updateTexImage(); st?.getTransformMatrix(mtx); hasFrame = true } catch (_: Throwable) {}
        }
        if (!hasFrame) return
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(prog)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniformMatrix4fv(uMtx, 1, false, mtx, 0)
        GLES20.glUniform1f(uDim, if (lowBattery) 0.25f else 0f)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    override fun release() {
        surface?.release(); surface = null
        st?.release(); st = null
        if (prog != 0) GLES20.glDeleteProgram(prog)
        if (vbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
        if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
        prog = 0; vbo = 0; tex = 0; hasFrame = false
    }

    protected fun xp(nx: Float) = nx * W
    protected fun yp(ny: Float) = ny * H
}

/** Paints, cached gradients and the little drawing vocabulary the characters share. */
class Ink {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    val r = RectF()
    val path = Path()
    /** Right edge for keeping speech bubbles on screen (set by the creature). */
    var maxX = 1e9f
    private val glossCache = HashMap<Long, Shader>()
    private val glowCache = HashMap<Int, Shader>()

    fun color(col: Int, a: Float = 1f): Paint {
        p.shader = null; p.color = col; p.alpha = (a.coerceIn(0f, 1f) * Color.alpha(col)).toInt(); return p
    }

    fun ell(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, col: Int, a: Float = 1f, rot: Float = 0f) {
        color(col, a)
        if (rot != 0f) { c.save(); c.translate(x, y); c.rotate(rot * 57.29578f); r.set(-rx, -ry, rx, ry); c.drawOval(r, p); c.restore() }
        else { r.set(x - rx, y - ry, x + rx, y + ry); c.drawOval(r, p) }
    }

    /** A glossy 3D-looking ellipse: light spot upper-left, base, dark rim. */
    fun glossy(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, base: Int, light: Int, dark: Int, rot: Float = 0f, a: Float = 1f) {
        val key = (base.toLong() and 0xFFFFFFL) or ((light.toLong() and 0xFFFFFFL) shl 24) xor ((dark.toLong() and 0xFFFFL) shl 48) xor (dark.toLong() ushr 16 shl 40)
        val sh = glossCache.getOrPut(key) {
            RadialGradient(-0.32f, -0.42f, 1.32f, intArrayOf(light, base, dark), floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
        }
        p.shader = sh; p.color = Color.WHITE; p.alpha = (a * 255).toInt()
        c.save(); c.translate(x, y); if (rot != 0f) c.rotate(rot * 57.29578f); c.scale(max(rx, 0.1f), max(ry, 0.1f))
        r.set(-1f, -1f, 1f, 1f); c.drawOval(r, p)
        c.restore(); p.shader = null
    }

    /** Soft round glow (colour fading to transparent). */
    fun glow(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, col: Int, a: Float = 1f) {
        if (a <= 0.01f) return
        val sh = glowCache.getOrPut(col) { RadialGradient(0f, 0f, 1f, intArrayOf(col, col and 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP) }
        p.shader = sh; p.color = Color.WHITE; p.alpha = (a.coerceIn(0f, 1f) * 255).toInt()
        c.save(); c.translate(x, y); c.scale(max(rx, 0.1f), max(ry, 0.1f)); r.set(-1f, -1f, 1f, 1f); c.drawOval(r, p); c.restore()
        p.shader = null
    }

    fun shine(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, a: Float = 0.55f) = ell(c, x, y, rx, ry, Color.WHITE, a, -0.5f)

    fun stroke(col: Int, w: Float, a: Float = 1f): Paint { s.shader = null; s.color = col; s.alpha = (a.coerceIn(0f, 1f) * Color.alpha(col)).toInt(); s.strokeWidth = w; return s }

    fun line(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, col: Int, w: Float, a: Float = 1f) = c.drawLine(x0, y0, x1, y1, stroke(col, w, a))

    fun arc(c: Canvas, x: Float, y: Float, rad: Float, start: Float, end: Float, col: Int, w: Float, a: Float = 1f) {
        r.set(x - rad, y - rad, x + rad, y + rad)
        c.drawArc(r, start * 57.29578f, (end - start) * 57.29578f, false, stroke(col, w, a))
    }

    /** Big glossy cartoon eye. blink 0 open .. 1 shut; look -1..1. */
    fun eye(c: Canvas, x: Float, y: Float, rad: Float, blink: Float = 0f, lx: Float = 0f, ly: Float = 0f, iris: Int = 0xFF3A2A4A.toInt(), ink: Int = 0xFF1A1020.toInt()) {
        val sy = 1f - blink * 0.92f
        if (sy < 0.15f) { arc(c, x, y - rad * 0.2f, rad * 0.7f, (PI * 0.15).toFloat(), (PI * 0.85).toFloat(), ink, rad * 0.28f); return }
        c.save(); c.translate(x, y); c.scale(1f, sy)
        glossy(c, 0f, 0f, rad, rad * 1.08f, iris, lighten(iris, 0.25f), 0xFF0B0710.toInt())
        val ox = lx * rad * 0.25f; val oy = ly * rad * 0.25f
        ell(c, ox - rad * 0.32f, oy - rad * 0.38f, rad * 0.3f, rad * 0.3f, Color.WHITE)
        ell(c, ox + rad * 0.32f, oy + rad * 0.35f, rad * 0.13f, rad * 0.13f, Color.WHITE, 0.85f)
        c.restore()
    }

    fun happyEye(c: Canvas, x: Float, y: Float, rad: Float, ink: Int = 0xFF1A1020.toInt()) =
        arc(c, x, y + rad * 0.35f, rad * 0.75f, (PI * 1.15).toFloat(), (PI * 1.85).toFloat(), ink, rad * 0.32f)

    fun closedEye(c: Canvas, x: Float, y: Float, rad: Float, ink: Int = 0xFF1A1020.toInt()) =
        arc(c, x, y - rad * 0.2f, rad * 0.7f, (PI * 0.15).toFloat(), (PI * 0.85).toFloat(), ink, rad * 0.28f)

    fun blush(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, a: Float = 0.55f) = glow(c, x, y, rx, ry, 0xFFFF7896.toInt(), a)

    fun note(c: Canvas, x: Float, y: Float, sz: Float, col: Int, a: Float) {
        if (a <= 0.02f) return
        ell(c, x, y, sz * 0.42f, sz * 0.32f, col, a, -0.4f)
        path.reset(); path.moveTo(x + sz * 0.38f, y - sz * 0.05f); path.lineTo(x + sz * 0.38f, y - sz * 1.3f)
        path.quadTo(x + sz * 0.9f, y - sz * 1.1f, x + sz * 0.85f, y - sz * 0.7f)
        c.drawPath(path, stroke(col, sz * 0.16f, a))
    }

    /** Speech bubble with a tail pointing down at (x, y). */
    fun bubble(c: Canvas, x: Float, y: Float, text: String, sz: Float, a: Float = 1f, col: Int = 0xFFF4F0FF.toInt(), ink: Int = 0xFF140F1E.toInt()) {
        if (a <= 0.02f) return
        t.textSize = sz
        val w = t.measureText(text) + sz * 1.2f; val h = sz * 1.9f
        val bx = if (maxX > w + 8f) x.coerceIn(w / 2 + 4f, maxX - w / 2 - 4f) else x
        color(col, a)
        r.set(bx - w / 2, y - h, bx + w / 2, y); c.drawRoundRect(r, h / 2, h / 2, p)
        path.reset(); path.moveTo(x - sz * 0.3f, y - 1); path.lineTo(x, y + sz * 0.5f); path.lineTo(x + sz * 0.3f, y - 1); path.close(); c.drawPath(path, p)
        t.color = ink; t.alpha = (a * 255).toInt()
        c.drawText(text, bx, y - h / 2 + sz * 0.36f, t)
    }

    fun zzz(c: Canvas, x: Float, y: Float, time: Float, sz: Float, col: Int = 0xFFCDB4FF.toInt()) {
        t.textSize = sz; t.color = col
        for (i in 0 until 3) {
            val k = ((time * 0.5f + i / 3f) % 1f)
            t.alpha = (sin(k * PI.toFloat()) * 230).toInt().coerceIn(0, 255)
            c.drawText("z", x + k * sz * 1.4f + sin(k * 6f) * sz * 0.2f, y - k * sz * 2.4f, t)
        }
    }

    fun boltShape(c: Canvas, x: Float, y: Float, sz: Float, col: Int, a: Float = 1f) {
        path.reset()
        path.moveTo(x + sz * 0.15f, y - sz); path.lineTo(x - sz * 0.45f, y + sz * 0.1f); path.lineTo(x - sz * 0.02f, y + sz * 0.1f)
        path.lineTo(x - sz * 0.2f, y + sz); path.lineTo(x + sz * 0.45f, y - sz * 0.15f); path.lineTo(x + sz * 0.03f, y - sz * 0.15f); path.close()
        glow(c, x, y, sz * 1.6f, sz * 1.6f, col, 0.35f * a)
        c.drawPath(path, color(col, a))
    }

    fun text(c: Canvas, s: String, x: Float, y: Float, sz: Float, col: Int, a: Float = 1f) {
        t.textSize = sz; t.color = col; t.alpha = (a.coerceIn(0f, 1f) * 255).toInt(); c.drawText(s, x, y, t)
    }

    fun vGrad(top: Float, bottom: Float, c0: Int, c1: Int): Paint {
        p.shader = LinearGradient(0f, top, 0f, bottom, c0, c1, Shader.TileMode.CLAMP); p.color = Color.WHITE; p.alpha = 255; return p
    }

    companion object {
        fun lighten(c: Int, k: Float): Int = Color.rgb(
            (Color.red(c) + (255 - Color.red(c)) * k).toInt(), (Color.green(c) + (255 - Color.green(c)) * k).toInt(), (Color.blue(c) + (255 - Color.blue(c)) * k).toInt())
        fun darken(c: Int, k: Float): Int = Color.rgb((Color.red(c) * (1 - k)).toInt(), (Color.green(c) * (1 - k)).toInt(), (Color.blue(c) * (1 - k)).toInt())
    }
}

internal fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
internal fun easeF(t: Float): Float { val k = t.coerceIn(0f, 1f); return k * k * (3f - 2f * k) }
internal fun clampF(v: Float, a: Float, b: Float) = if (v < a) a else if (v > b) b else v
internal fun absF(v: Float) = abs(v)
