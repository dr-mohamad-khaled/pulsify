package app.pulsify.engine

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Cosmo's deep-space background, cheap: the Milky Way and dust are painted ONCE into a half-resolution
 * texture, and the ~1,400 stars are GL point sprites drawn in one call. The old version searched 36 star
 * cells and ran 3 fbm noises for every pixel, every frame, at native resolution.
 */
class StarField {
    private var neb = 0; private var blit = 0; private var pts = 0; private var tri = 0; private var vbo = 0
    private var tex = 0; private var fbo = 0; private var n = 0; private var bw = 0; private var bh = 0
    private val bu = HashMap<String, Int>(); private val pu = HashMap<String, Int>(); private var aStar = 1; private var aLook = 2

    fun create() {
        neb = Gl.program(VS, NEB_FS); blit = Gl.program(VS, BLIT_FS); pts = Gl.program(STAR_VS, STAR_FS)
        for (k in listOf("uTex", "uRes", "uShake")) bu[k] = GLES20.glGetUniformLocation(blit, k)
        for (k in listOf("uRes", "uFall", "uTime", "uS", "uPar")) pu[k] = GLES20.glGetUniformLocation(pts, k)
        aStar = GLES20.glGetAttribLocation(pts, "aStar"); aLook = GLES20.glGetAttribLocation(pts, "aLook")
        val ids = IntArray(2); GLES20.glGenBuffers(2, ids, 0); tri = ids[0]; vbo = ids[1]
        put(tri, floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)); bw = 0; bh = 0
    }
    private fun put(b: Int, a: FloatArray) {
        val fb = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(a); fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, b); GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, a.size * 4, fb, GLES20.GL_STATIC_DRAW)
    }
    private fun drawTri() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tri); GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0); GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
    }
    private fun build(W: Int, H: Int) {
        // same statistics as the per-pixel cells: 3 parallax layers + fine stars inside the Milky Way band
        val rnd = Random(20261125); val s = min(W, H) / 1080f; val area = W.toFloat() * H; val out = ArrayList<Float>(16000)
        fun layer(cell: Float, dens: Float, rad: Float, k: Float, tw: Float, gain: Float, bandOnly: Boolean) {
            val cnt = (area / ((cell * s) * (cell * s)) * dens * (if (bandOnly) 1.6f else 1f)).roundToInt()
            for (i in 0 until cnt) {
                val x = rnd.nextFloat(); val y = rnd.nextFloat()
                if (bandOnly) { val qx = (x - .5f) * W / H; val qy = y - .5f; val t = (qx * .6f + qy * .9f + .05f) * 3.2f; if (rnd.nextFloat() > exp(-t * t) * .35f / .56f) continue }
                out.add(x); out.add(y); out.add(k); out.add(rad); out.add((.6f + 2 * rnd.nextFloat()) * tw); out.add(rnd.nextFloat() * 40f)
                out.add(rnd.nextFloat()); out.add(rnd.nextFloat()); out.add(gain); out.add(0f)
            }
        }
        layer(46f, .55f, .75f, .25f, .7f, .7f, false); layer(90f, .45f, 1.05f, .5f, 1f, 1f, false)
        layer(190f, .35f, 1.5f, 1f, 1.3f, 1.15f, false); layer(22f, .35f, .55f, .2f, .5f, .6f, true)
        n = out.size / 10; put(vbo, out.toFloatArray())
        // nebula: painted once at half resolution
        val w = max(1, W shr 1); val h = max(1, H shr 1)
        if (tex == 0) { val t = IntArray(1); GLES20.glGenTextures(1, t, 0); tex = t[0]; val f = IntArray(1); GLES20.glGenFramebuffers(1, f, 0); fbo = f[0] }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        for ((p, v) in listOf(GLES20.GL_TEXTURE_MIN_FILTER to GLES20.GL_LINEAR, GLES20.GL_TEXTURE_MAG_FILTER to GLES20.GL_LINEAR,
                GLES20.GL_TEXTURE_WRAP_S to GLES20.GL_CLAMP_TO_EDGE, GLES20.GL_TEXTURE_WRAP_T to GLES20.GL_CLAMP_TO_EDGE)) GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, p, v)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex, 0)
        GLES20.glViewport(0, 0, w, h); GLES20.glUseProgram(neb); GLES20.glUniform2f(GLES20.glGetUniformLocation(neb, "uRes"), w.toFloat(), h.toFloat()); drawTri()
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target)
        bw = W; bh = H
    }
    /** Draws into the current target and viewport. */
    fun draw(fall: Float, time: Float, shake: Float, par: Float) {
        val vp = IntArray(4); GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0); val W = vp[2]; val H = vp[3]
        if (bw != W || bh != H) { build(W, H); GLES20.glViewport(vp[0], vp[1], vp[2], vp[3]) }
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(blit); GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(bu["uTex"]!!, 0); GLES20.glUniform2f(bu["uRes"]!!, W.toFloat(), H.toFloat()); GLES20.glUniform1f(bu["uShake"]!!, shake)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(blit, "uOrg"), vp[0].toFloat(), vp[1].toFloat()); drawTri()
        GLES20.glUseProgram(pts)
        GLES20.glUniform2f(pu["uRes"]!!, W.toFloat(), H.toFloat()); GLES20.glUniform1f(pu["uFall"]!!, fall); GLES20.glUniform1f(pu["uTime"]!!, time)
        GLES20.glUniform1f(pu["uS"]!!, min(W, H) / 1080f); GLES20.glUniform1f(pu["uPar"]!!, par)
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 40, 0)
        GLES20.glEnableVertexAttribArray(aStar); GLES20.glVertexAttribPointer(aStar, 4, GLES20.GL_FLOAT, false, 40, 8)
        GLES20.glEnableVertexAttribArray(aLook); GLES20.glVertexAttribPointer(aLook, 4, GLES20.GL_FLOAT, false, 40, 24)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, n)
        GLES20.glDisableVertexAttribArray(aStar); GLES20.glDisableVertexAttribArray(aLook); GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
    fun release() {
        for (p in listOf(neb, blit, pts)) if (p != 0) GLES20.glDeleteProgram(p)
        if (tri != 0) GLES20.glDeleteBuffers(2, intArrayOf(tri, vbo), 0)
        if (tex != 0) { GLES20.glDeleteTextures(1, intArrayOf(tex), 0); GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0) }
        neb = 0; blit = 0; pts = 0; tri = 0; vbo = 0; tex = 0; fbo = 0; bw = 0; bh = 0
    }

    companion object {
        private const val VS = "attribute vec2 aPos;\nvoid main(){ gl_Position = vec4(aPos, 0.0, 1.0); }\n"
        private const val NEB_FS = """precision highp float;
uniform vec2 uRes;
float h21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float noise(vec2 p){ vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
  return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y); }
float fbm(vec2 p){ float n = 0.0, a = 0.5; for (int i = 0; i < 5; i++) { n += a * noise(p); p = p * 2.03 + vec2(1.7, 9.2); a *= 0.5; } return n; }
void main(){
  vec2 px = gl_FragCoord.xy; vec3 col = vec3(0.004, 0.004, 0.012); vec2 q = (px - 0.5 * uRes) / uRes.y;
  float band = exp(-pow((q.x * 0.6 + q.y * 0.9 + 0.05) * 3.2, 2.0)); float n = fbm(q * 3.0 + 3.0), dust = fbm(q * 7.0 + 11.0);
  col += band * (vec3(0.05, 0.045, 0.08) * smoothstep(0.35, 0.85, n) - vec3(0.03, 0.025, 0.04) * smoothstep(0.55, 0.8, dust) * 0.6);
  col += vec3(0.03, 0.012, 0.05) * smoothstep(0.55, 0.95, fbm(q * 1.6 + 30.0)) * 0.8;
  gl_FragColor = vec4(col, 1.0); }"""
        private const val BLIT_FS = """precision mediump float; uniform sampler2D uTex; uniform vec2 uRes; uniform vec2 uOrg; uniform float uShake;
void main(){ gl_FragColor = vec4(texture2D(uTex, (gl_FragCoord.xy - uOrg) / uRes).rgb + vec3(0.25, 0.35, 0.6) * uShake * 0.04, 1.0); }"""
        private const val STAR_VS = """attribute vec2 aPos; attribute vec4 aStar; attribute vec4 aLook;
uniform vec2 uRes; uniform float uFall; uniform float uTime; uniform float uS; uniform float uPar;
varying float vR; varying float vSize; varying vec3 vCol;
void main(){
  vec2 p = aPos * uRes - vec2(uPar * 0.15 * uRes.x, uFall * 6.0) * aStar.x;
  p = mod(p, uRes);
  float m = aLook.y; float rr = aStar.y * uS * (0.6 + 0.9 * m * m);
  float t = 0.75 + 0.25 * sin(uTime * aStar.z + aStar.w);
  vec3 tint = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.86, 0.7), aLook.x);
  vCol = tint * t * (0.35 + 0.65 * m) * aLook.z; vR = rr; vSize = rr * 5.0 + 2.0;
  gl_PointSize = vSize; gl_Position = vec4(p / uRes * 2.0 - 1.0, 0.0, 1.0); }"""
        private const val STAR_FS = """precision mediump float; varying float vR; varying float vSize; varying vec3 vCol;
void main(){ float d = length(gl_PointCoord - 0.5) * vSize;
  float a = clamp(vR + 0.5 - d, 0.0, 1.0) + exp(-d * d / (vR * vR * 6.0)) * 0.25;
  gl_FragColor = vec4(vCol * a, 1.0); }"""
    }
}
