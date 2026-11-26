package app.pulsify.engine

import android.graphics.Bitmap
import android.opengl.GLES20
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Debug only: renders the wallpaper frame by frame with a fixed time step and saves every frame as a JPEG,
 * so store and website videos come out perfectly smooth however slow the device (or emulator) is.
 * The frames are upside down (GL order); the encoder flips them. [script] plays tricks at given frames.
 */
object FrameCapture {
    @Volatile var on = false; private set
    var fps = 30; private set
    private var dir: File? = null
    private var total = 0
    private var n = 0
    private var script: Map<Int, String> = emptyMap()
    private var buf: ByteBuffer? = null
    private var bmp: Bitmap? = null

    /** [scriptSpec]: "frame:trick;frame:trick", e.g. "110:next;200:next". */
    fun start(out: File, frames: Int, fps: Int, scriptSpec: String?) {
        out.mkdirs(); out.listFiles()?.forEach { it.delete() }
        dir = out; total = frames; this.fps = fps.coerceIn(10, 60); n = 0
        script = scriptSpec.orEmpty().split(';').mapNotNull { p ->
            val i = p.indexOf(':'); if (i <= 0) null else p.substring(0, i).trim().toIntOrNull()?.let { it to p.substring(i + 1).trim() }
        }.toMap()
        on = true
    }

    /** The trick to play before drawing this frame, if any. */
    fun trickNow(): String? = if (on) script[n] else null

    /** Reads the finished frame (default framebuffer, before the swap) and saves it. */
    fun grab(w: Int, h: Int) {
        val d = dir ?: return
        if (!on) return
        val size = w * h * 4
        val b = buf?.takeIf { it.capacity() == size } ?: ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder()).also { buf = it }
        b.position(0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, b)
        val bm = bmp?.takeIf { it.width == w && it.height == h } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp = it }
        b.position(0); bm.copyPixelsFromBuffer(b)
        FileOutputStream(File(d, "f%05d.jpg".format(n))).use { bm.compress(Bitmap.CompressFormat.JPEG, 93, it) }
        n++
        if (n >= total) { on = false; File(d, "done").writeText("$n") }
    }
}
