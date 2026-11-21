package app.pulsify.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
import kotlin.math.max
import kotlin.math.min

/** A speech bubble: the words are drawn on the CPU only when they change, then shown by bubble.frag. */
class SpeechBubble(private val context: Context) {
    private var prog = 0
    private var tex = 0
    private val u = HashMap<String, Int>()
    private val tw = 512; private val th = 128
    private var bmp: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.LEFT
    }
    private var text = ""
    private var dirty = false
    private var t = 99f
    private var dur = 0f
    private var ratio = 1f

    val showing: Boolean get() = text.isNotEmpty() && t < dur + 0.35f
    val age: Float get() = t
    val length: Float get() = dur

    fun create() {
        val vert = context.assets.open("shaders/mochi.vert").bufferedReader().use { it.readText() }
        val frag = context.assets.open("shaders/bubble.frag").bufferedReader().use { it.readText() }
        prog = Gl.program(vert, frag)
        for (n in listOf("uQuad", "uRes", "uTex", "uBox", "uTip", "uPop", "uPx", "uTexW", "uRim", "uAlpha")) u[n] = GLES20.glGetUniformLocation(prog, n)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0); tex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        if (text.isNotEmpty()) dirty = true
    }

    fun say(words: String, seconds: Float = 0f) {
        text = words.take(40); dirty = true; t = 0f
        dur = if (seconds > 0f) seconds else (1.0f + 0.07f * text.length).coerceIn(1.4f, 3.2f)
    }

    fun update(dt: Float) { t += dt }

    private fun upload() {
        val b = bmp ?: return
        dirty = false
        b.eraseColor(Color.TRANSPARENT)
        paint.textSize = 70f
        var w0 = paint.measureText(text)
        if (w0 > tw - 24f) { paint.textSize = 70f * (tw - 24f) / w0; w0 = paint.measureText(text) }
        val fm = paint.fontMetrics
        Canvas(b).drawText(text, 12f, th * 0.5f - (fm.ascent + fm.descent) * 0.5f, paint)
        ratio = (w0 + 24f) / th
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
    }

    /** tipX/tipY: where the tail points (uv). side: which way the bubble leans. */
    fun draw(w: Int, h: Int, quadVbo: Int, tipX: Float, tipY: Float, side: Float, scale: Float, rim: FloatArray) {
        if (prog == 0 || !showing) return
        if (dirty) upload()
        val pop = if (t < 0.3f) { val k = t / 0.3f; 1f + 1.6f * (k - 1f) * (k - 1f) * (k - 1f) + 0.6f * (k - 1f) * (k - 1f) } else if (t > dur) max(0f, 1f - (t - dur) / 0.35f) else 1f
        if (pop <= 0.01f) return
        val m = min(w, h).toFloat()
        val halfW = 0.5f * w / m; val halfH = 0.5f * h / m
        val bh = 0.085f * scale
        val bw = bh * ratio * 0.5f + bh * 0.25f
        val cx = (tipX + side * bw * 0.45f).coerceIn(-halfW + bw + 0.03f, halfW - bw - 0.03f)
        val cy = min(tipY + bh * 1.5f, halfH - bh - 0.05f)
        GLES20.glUseProgram(prog)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(u["uTex"] ?: -1, 0)
        GLES20.glUniform4f(u["uQuad"] ?: -1, min(cx - bw, tipX) - 0.04f, min(cy - bh, tipY) - 0.04f, max(cx + bw, tipX) + 0.04f, cy + bh + 0.04f)
        GLES20.glUniform2f(u["uRes"] ?: -1, w.toFloat(), h.toFloat())
        GLES20.glUniform4f(u["uBox"] ?: -1, cx, cy, bw, bh * 0.5f)
        GLES20.glUniform2f(u["uTip"] ?: -1, tipX, tipY)
        GLES20.glUniform1f(u["uPop"] ?: -1, pop)
        GLES20.glUniform1f(u["uPx"] ?: -1, 1f / m)
        GLES20.glUniform1f(u["uTexW"] ?: -1, ratio * th / tw)
        GLES20.glUniform3f(u["uRim"] ?: -1, rim[0], rim[1], rim[2])
        GLES20.glUniform1f(u["uAlpha"] ?: -1, 1f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun release() {
        if (prog != 0) GLES20.glDeleteProgram(prog)
        if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
        prog = 0; tex = 0
        bmp?.recycle(); bmp = null
    }
}
