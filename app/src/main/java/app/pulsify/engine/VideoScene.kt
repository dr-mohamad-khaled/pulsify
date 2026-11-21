package app.pulsify.engine

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A looping video wallpaper. Files live in assets/loops/<file>. */
data class VideoLoop(val id: Int, val name: String, val file: String, val line: String, val c1: Int, val c2: Int)

val VIDEO_LOOPS = listOf(
    VideoLoop(0, "Star Whale", "whale.mp4", "A whale of stardust rising through the dark.", 0xFF2DD4BF.toInt(), 0xFF5B8CFF.toInt()),
    VideoLoop(1, "Golden Eagle", "eagle.mp4", "Perched on black rock, watching you.", 0xFFFFB36B.toInt(), 0xFF8C5A2B.toInt()),
    VideoLoop(2, "Jade Turtle", "turtle.mp4", "Glowing shell, drifting through the void.", 0xFF4ADE80.toInt(), 0xFF2DD4BF.toInt()),
    VideoLoop(3, "Pharaohs", "pharaohs.mp4", "Golden eyes light up under the pyramids.", 0xFFFFD166.toInt(), 0xFF7A5A1E.toInt()),
    VideoLoop(4, "Neon Jelly", "jelly.mp4", "Pink and blue, pulsing upward forever.", 0xFFFF5FA2.toInt(), 0xFF5B8CFF.toInt()),
    VideoLoop(5, "Violet Panther", "panther.mp4", "Neon-veined, stalking across black water.", 0xFF9D4EDD.toInt(), 0xFF4ADE80.toInt()),
    VideoLoop(6, "Royal Betta", "fish.mp4", "Silk fins unfurling in the void.", 0xFFFF4F86.toInt(), 0xFF6B7BFF.toInt()),
    VideoLoop(7, "Phoenix", "bird.mp4", "Feathers of fire on a branch of glass.", 0xFFFF7A2E.toInt(), 0xFFFFC266.toInt()),
    VideoLoop(8, "Lion", "lion.mp4", "Slow steps out of the dark, straight at you.", 0xFFD9A55B.toInt(), 0xFF7A5A2B.toInt()),
    VideoLoop(9, "Astronaut", "astronaut.mp4", "Drifting free, tether trailing behind.", 0xFFE8EEF7.toInt(), 0xFF5B8CFF.toInt()),
    VideoLoop(10, "Ember Dragon", "dragon.mp4", "Coiled, breathing, glowing from inside.", 0xFFFF5A2E.toInt(), 0xFF6B4FB0.toInt()),
    VideoLoop(11, "Gold Tiger", "tiger.mp4", "Golden stripes walking out of the dark.", 0xFFFFC14D.toInt(), 0xFF3A2A12.toInt()),
)

fun videoLoop(id: Int): VideoLoop? = VIDEO_LOOPS.firstOrNull { it.id == id }

/**
 * Plays a seamless loop into an external texture and draws it cover-fitted.
 * The decoder runs only while the loop is shown and the wallpaper is visible; switching away
 * releases the player and its buffers. All calls happen on the GL thread (which has a Looper).
 */
@OptIn(UnstableApi::class)
class VideoScene(private val context: Context) {
    private var prog = 0
    private var vbo = 0
    private var tex = 0
    private var st: SurfaceTexture? = null
    private var surface: Surface? = null
    private var player: ExoPlayer? = null
    private var current = -1
    private var vw = 1080; private var vh = 1920
    private var w = 1; private var h = 1
    private var playing = false
    private val mtx = FloatArray(16)
    private var uTex = -1; private var uMtx = -1; private var uMap = -1; private var uRes = -1
    @Volatile var frameReady = false; private set
    private var hasFrame = false

    fun onSurfaceChanged(width: Int, height: Int) { w = width; h = height }

    /** Starts the loop with this id (or stops everything for -1). */
    fun ensure(id: Int) {
        if (id == current) return
        stop()
        current = id
        val loop = videoLoop(id) ?: return
        try {
            if (prog == 0) createGl()
            // A fresh surface per video: a decoder that was just released can still be
            // attached to the old one, and the next video would then stay black.
            newSurface()
            val looper = Looper.myLooper() ?: return
            val p = ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
                .setLooper(looper)
                .build()
            p.volume = 0f
            p.repeatMode = Player.REPEAT_MODE_ONE
            p.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            p.setVideoSurface(surface)
            p.setMediaItem(MediaItem.fromUri("asset:///loops/${loop.file}"))
            p.prepare()
            p.playWhenReady = playing
            player = p
        } catch (t: Throwable) {
            Log.w("Pulsify", "video $id failed", t)
            app.pulsify.core.CrashLog.note("video failed: ${t.message}")
        }
    }

    fun setPlaying(on: Boolean) {
        playing = on
        player?.playWhenReady = on
    }

    private fun createGl() {
        val vs = "attribute vec2 aPos;\nvarying vec2 vUv;\nvoid main(){ vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }\n"
        val fs = """#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vUv;
uniform samplerExternalOES uTex;
uniform mat4 uMtx;
uniform vec4 uMap;
void main(){
  vec2 iv = vUv * uMap.xy + uMap.zw;
  vec2 tc = (uMtx * vec4(iv, 0.0, 1.0)).xy;
  gl_FragColor = vec4(texture2D(uTex, tc).rgb, 1.0);
}
"""
        prog = Gl.program(vs, fs)
        uTex = GLES20.glGetUniformLocation(prog, "uTex"); uMtx = GLES20.glGetUniformLocation(prog, "uMtx")
        uMap = GLES20.glGetUniformLocation(prog, "uMap")
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0); vbo = ids[0]
        val q = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
        val fb = ByteBuffer.allocateDirect(q.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(q)
        fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, q.size * 4, fb, GLES20.GL_STATIC_DRAW)
    }

    private fun newSurface() {
        surface?.release(); surface = null
        st?.release(); st = null
        if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0); tex = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val s = SurfaceTexture(tex)
        s.setOnFrameAvailableListener { frameReady = true }
        st = s
        surface = Surface(s)
        hasFrame = false; frameReady = false
    }

    /** True when a new decoded frame is waiting (the loop renders exactly at the video's rate). */
    val wantsFrame: Boolean get() = frameReady || !hasFrame

    fun draw() {
        val s = st
        if (s == null || prog == 0) { GLES20.glClearColor(0f, 0f, 0f, 1f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT); return }
        if (frameReady) {
            frameReady = false
            try { s.updateTexImage(); s.getTransformMatrix(mtx); hasFrame = true } catch (_: Throwable) {}
        }
        if (!hasFrame) { GLES20.glClearColor(0f, 0f, 0f, 1f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT); return }
        player?.videoSize?.let { if (it.width > 0 && it.height > 0) { vw = it.width; vh = it.height } }
        // Cover fit: crop the long side, keep the centre.
        val va = vw.toFloat() / vh; val sa = w.toFloat() / h
        val sx: Float; val sy: Float
        if (sa < va) { sx = sa / va; sy = 1f } else { sx = 1f; sy = va / sa }
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(prog)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniformMatrix4fv(uMtx, 1, false, mtx, 0)
        GLES20.glUniform4f(uMap, sx, sy, (1f - sx) * 0.5f, (1f - sy) * 0.5f)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
    }

    private fun stop() {
        try { player?.clearVideoSurface(); player?.release() } catch (_: Throwable) {}
        player = null
        hasFrame = false; frameReady = false
        current = -1
    }

    fun release() {
        stop()
        surface?.release(); surface = null
        st?.release(); st = null
        if (prog != 0) GLES20.glDeleteProgram(prog)
        if (vbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
        if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
        prog = 0; vbo = 0; tex = 0
    }
}
