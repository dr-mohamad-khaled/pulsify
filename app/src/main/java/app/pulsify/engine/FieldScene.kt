package app.pulsify.engine

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Full-screen worlds that live in a GPU texture between frames:
 *  - LIQUID (Liquid Obsidian): a real wave simulation; every touch and every home-screen swipe
 *    makes water move, music beats drop into it, and coloured light glints off the black liquid.
 *  - NEON (Neon Light Play): a field of light; your finger paints bright neon that blooms, drifts
 *    and slowly fades; swipes sweep light across the screen and beats flash rings.
 * Both run at a low simulation resolution and are cheap enough to stay at 60 fps.
 */
class FieldScene(val kind: Int) {
    private var simProg = 0; private var drawProg = 0; private var vbo = 0
    private val tex = IntArray(2); private val fbo = IntArray(2)
    private var cur = 0
    private var sw = 0; private var sh = 0
    private var w = 1; private var h = 1
    private var time = 0f
    private val su = HashMap<String, Int>(); private val du = HashMap<String, Int>()
    // Pending splashes / strokes: x0, y0, x1, y1 (uv 0..1, y up), radius, strength, hue, unused.
    private val queue = ArrayDeque<FloatArray>()
    private val buf: FloatBuffer = ByteBuffer.allocateDirect(MAXQ * 8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var down = false; private var lx = 0f; private var ly = 0f; private var downT = 0f; private var sx = 0f; private var sy = 0f
    private var hue = Random.nextFloat()
    private var rain = 0.5f
    private var lastPar = Float.NaN
    private var sinceTouch = 99f
    private var lastBeat = 0f; private var beatN = 0

    val busy: Boolean get() = true

    fun create() {
        val sim = if (kind == LIQUID) LIQUID_SIM else NEON_SIM
        val drw = if (kind == LIQUID) LIQUID_DRAW else NEON_DRAW
        simProg = Gl.program(VERT, sim)
        drawProg = Gl.program(VERT, drw)
        for (n in listOf("uState", "uTexel", "uQ", "uN", "uAspect", "uDamp", "uDecay", "uTime")) su[n] = GLES20.glGetUniformLocation(simProg, n)
        for (n in listOf("uState", "uTexel", "uAspect", "uA1", "uA2", "uA3", "uTime", "uBass", "uTreble", "uBright", "uRes")) du[n] = GLES20.glGetUniformLocation(drawProg, n)
        val ids = IntArray(1); GLES20.glGenBuffers(1, ids, 0); vbo = ids[0]
        val tri = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
        val fb = ByteBuffer.allocateDirect(tri.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(tri); fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, tri.size * 4, fb, GLES20.GL_STATIC_DRAW)
    }

    fun size(width: Int, height: Int) {
        w = max(1, width); h = max(1, height)
        val nw = if (kind == LIQUID) 260 else 220
        val nh = (nw * h.toFloat() / w).toInt().coerceIn(nw, nw * 3)
        if (nw == sw && nh == sh) return
        freeTargets()
        sw = nw; sh = nh
        GLES20.glGenTextures(2, tex, 0); GLES20.glGenFramebuffers(2, fbo, 0)
        for (i in 0..1) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[i])
            val filter = if (kind == LIQUID) GLES20.GL_NEAREST else GLES20.GL_LINEAR
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, sw, sh, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[i])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex[i], 0)
            // Liquid starts flat (encoded 0 = 0.5 grey); neon starts dark.
            if (kind == LIQUID) GLES20.glClearColor(0.5f, 0f, 0.5f, 0f) else GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target)
    }

    // ---- input (normalised screen 0..1, y down) ----

    private fun add(x0: Float, y0: Float, x1: Float, y1: Float, r: Float, s: Float) {
        queue.addLast(floatArrayOf(x0, 1f - y0, x1, 1f - y1, r, s, hue, 0f))
        while (queue.size > 64) queue.removeFirst()
    }

    fun touchDown(nx: Float, ny: Float) {
        down = true; lx = nx; ly = ny; sx = nx; sy = ny; downT = time; sinceTouch = 0f
        if (kind == LIQUID) add(nx, ny, nx, ny, 0.05f, 0.9f) else add(nx, ny, nx, ny, 0.02f, 1.8f)
    }

    fun touchMove(nx: Float, ny: Float) {
        sinceTouch = 0f
        val d = hypot(nx - lx, ny - ly)
        if (d < 0.004f) return
        if (kind == LIQUID) {
            val n = min(4, (d / 0.02f).toInt() + 1)
            for (i in 1..n) { val t = i / n.toFloat(); val x = lx + (nx - lx) * t; val y = ly + (ny - ly) * t; add(x, y, x, y, 0.032f, 0.26f) }
        } else {
            hue = (hue + d * 0.6f) % 1f
            add(lx, ly, nx, ny, 0.0065f + min(0.008f, d * 0.05f), 1.7f)
        }
        lx = nx; ly = ny
    }

    fun touchUp() {
        down = false
        if (time - downT < 0.3f && hypot(lx - sx, ly - sy) < 0.02f) {
            if (kind == LIQUID) add(lx, ly, lx, ly, 0.09f, -1.6f) else ring(lx, ly)
        }
    }

    private fun ring(x: Float, y: Float) {
        // A halo of light: a closed ring drawn as short arcs, each a slightly different hue.
        val r = 0.06f + Random.nextFloat() * 0.05f
        val n = 14
        for (i in 0 until n) {
            val a0 = i * 6.2832f / n; val a1 = (i + 1) * 6.2832f / n
            hue = (hue + 0.035f) % 1f
            add(x + kotlin.math.cos(a0) * r, y + kotlin.math.sin(a0) * r * w / h, x + kotlin.math.cos(a1) * r, y + kotlin.math.sin(a1) * r * w / h, 0.006f, 1.6f)
        }
    }

    fun update(dt: Float, input: SceneInput) {
        time += dt; sinceTouch += dt
        val a = input.audio
        // Home-screen swipes: a wave front (liquid) or a sweep of light (neon) across the screen.
        val par = input.parallax
        if (lastPar.isNaN()) lastPar = par
        val dv = par - lastPar
        lastPar = par
        if (abs(dv) > 0.002f) {
            val y = 0.15f + Random.nextFloat() * 0.7f
            val dir = if (dv > 0f) -1f else 1f
            if (kind == LIQUID) {
                val x = if (dir > 0f) 0.02f else 0.98f
                for (k in 0 until 3) add(x, y + (k - 1) * 0.12f, x, y + (k - 1) * 0.12f, 0.06f, min(1.2f, abs(dv) * 30f))
                add(0.5f + dv * 8f, 0.5f, 0.5f + dv * 8f, 0.5f, 0.04f, min(0.8f, abs(dv) * 20f))
            } else {
                hue = (hue + 0.03f) % 1f
                val x0 = 0.5f - dv * 18f; val x1 = 0.5f + dv * 18f
                add(x0, y, x1, y, 0.01f, min(1.6f, abs(dv) * 40f))
            }
        }
        // Music.
        if (a.active && a.beat > 0.9f && time - lastBeat > 0.2f) {
            lastBeat = time; beatN++
            if (kind == LIQUID) {
                val bx = 0.3f + Random.nextFloat() * 0.4f; val by = 0.35f + Random.nextFloat() * 0.3f
                add(bx, by, bx, by, 0.1f + a.bass * 0.06f, 1.2f + a.bass)
                if (beatN % 4 == 0) add(0.5f, 0.5f, 0.5f, 0.5f, 0.2f, -1.8f)
            } else ring(0.2f + Random.nextFloat() * 0.6f, 0.25f + Random.nextFloat() * 0.5f)
        }
        // Quiet rain on the liquid; slow drifting sparks in the neon.
        rain -= dt
        if (rain <= 0f) {
            if (kind == LIQUID) {
                rain = if (a.active) 0.2f + Random.nextFloat() * 0.4f else 0.6f + Random.nextFloat() * 1.2f
                val x = Random.nextFloat(); val y = Random.nextFloat()
                add(x, y, x, y, 0.015f + Random.nextFloat() * 0.02f, 0.3f + Random.nextFloat() * 0.3f)
            } else {
                rain = if (sinceTouch < 3f) 0.6f else 1.6f + Random.nextFloat() * 1.5f
                val x = 0.1f + Random.nextFloat() * 0.8f; val y = 0.15f + Random.nextFloat() * 0.7f
                hue = (hue + 0.21f) % 1f
                add(x, y, x + (Random.nextFloat() - 0.5f) * 0.25f, y + (Random.nextFloat() - 0.5f) * 0.25f, 0.006f, 0.9f)
            }
        }
    }

    fun draw(input: SceneInput) {
        if (sw == 0) size(w, h)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        // Simulation steps (two per frame for the water; one for light).
        val steps = if (kind == LIQUID) 2 else 1
        GLES20.glViewport(0, 0, sw, sh)
        GLES20.glUseProgram(simProg)
        for (s in 0 until steps) {
            val nq = min(MAXQ, queue.size)
            buf.position(0)
            for (i in 0 until MAXQ) {
                val q = if (i < nq) queue.removeFirst() else null
                if (q != null) buf.put(q) else for (k in 0 until 8) buf.put(0f)
            }
            buf.position(0)
            GLES20.glUniform4fv(su["uQ"]!!, MAXQ * 2, buf)
            GLES20.glUniform1f(su["uN"]!!, nq.toFloat())
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[1 - cur])
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[cur])
            GLES20.glUniform1i(su["uState"]!!, 0)
            GLES20.glUniform2f(su["uTexel"]!!, 1f / sw, 1f / sh)
            GLES20.glUniform1f(su["uAspect"]!!, w.toFloat() / h)
            GLES20.glUniform1f(su["uDamp"]!!, 0.992f)
            GLES20.glUniform1f(su["uDecay"]!!, if (input.audio.active) 0.965f else 0.978f)
            GLES20.glUniform1f(su["uTime"]!!, time)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
            cur = 1 - cur
        }
        // Show it.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target)
        GLES20.glViewport(0, 0, w, h)
        GLES20.glUseProgram(drawProg)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[cur])
        GLES20.glUniform1i(du["uState"]!!, 0)
        GLES20.glUniform2f(du["uTexel"]!!, 1f / sw, 1f / sh)
        GLES20.glUniform2f(du["uRes"]!!, w.toFloat(), h.toFloat())
        GLES20.glUniform1f(du["uAspect"]!!, w.toFloat() / h)
        val pal = if (input.accent != 0) intArrayOf(input.accent, SkyScene.shiftHue(input.accent, 40f), SkyScene.shiftHue(input.accent, -60f))
            else if (kind == LIQUID) intArrayOf(0xFFCDB4FF.toInt(), 0xFFFFB36B.toInt(), 0xFF9D4EDD.toInt())
            else intArrayOf(0xFFFF2BD6.toInt(), 0xFF00E5FF.toInt(), 0xFF7CFF4F.toInt())
        for ((i, n) in listOf("uA1", "uA2", "uA3").withIndex()) {
            val c = pal[i]
            GLES20.glUniform3f(du[n]!!, ((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)
        }
        GLES20.glUniform1f(du["uTime"]!!, time % 3600f)
        GLES20.glUniform1f(du["uBass"]!!, if (input.audio.active) input.audio.bass else 0f)
        GLES20.glUniform1f(du["uTreble"]!!, if (input.audio.active) input.audio.treble else 0f)
        val dim = when { input.lowBattery -> 0.6f; input.mood == app.pulsify.core.Mood.NIGHT -> 0.75f; else -> 1f }
        GLES20.glUniform1f(du["uBright"]!!, input.brightness * dim)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
    }

    private fun freeTargets() {
        if (sw != 0) {
            GLES20.glDeleteFramebuffers(2, fbo, 0); GLES20.glDeleteTextures(2, tex, 0)
        }
        sw = 0; sh = 0
    }

    fun release() {
        freeTargets()
        if (simProg != 0) GLES20.glDeleteProgram(simProg)
        if (drawProg != 0) GLES20.glDeleteProgram(drawProg)
        if (vbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
        simProg = 0; drawProg = 0; vbo = 0
    }

    companion object {
        const val LIQUID = 3
        const val NEON = 4
        private const val MAXQ = 8
        private const val VERT = "attribute vec2 aPos;\nvarying vec2 vUv;\nvoid main(){ vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }\n"

        // Height and velocity packed as 8.8 fixed point in RG / BA (works on every GPU).
        private val PACK = """
float dec(vec2 v){ return (v.x * 255.0 + v.y) / 255.0 * 2.0 - 1.0; }
vec2 enc(float f){ float x = clamp(f * 0.5 + 0.5, 0.0, 0.99998) * 255.0; return vec2(floor(x) / 255.0, fract(x)); }
"""

        val LIQUID_SIM = """
precision highp float;
varying vec2 vUv;
uniform sampler2D uState; uniform vec2 uTexel; uniform vec4 uQ[16]; uniform float uN; uniform float uAspect; uniform float uDamp;
$PACK
void main(){
  vec4 c = texture2D(uState, vUv);
  float hgt = dec(c.rg), vel = dec(c.ba);
  float avg = (dec(texture2D(uState, vUv + vec2(uTexel.x, 0.0)).rg) + dec(texture2D(uState, vUv - vec2(uTexel.x, 0.0)).rg)
             + dec(texture2D(uState, vUv + vec2(0.0, uTexel.y)).rg) + dec(texture2D(uState, vUv - vec2(0.0, uTexel.y)).rg)) * 0.25;
  vel += (avg - hgt) * 2.0;
  vel *= uDamp;
  hgt += vel;
  hgt *= 0.9993;
  for (int i = 0; i < 8; i++) {
    if (float(i) < uN) {
      vec4 a = uQ[i * 2]; vec4 b = uQ[i * 2 + 1];
      vec2 q = vUv - a.xy; q.x *= uAspect;
      float k = max(0.0, 1.0 - length(q) / b.x);
      k = 0.5 - cos(k * 3.14159) * 0.5;
      hgt += k * b.y * 0.5;
    }
  }
  gl_FragColor = vec4(enc(clamp(hgt, -0.99, 0.99)), enc(clamp(vel, -0.99, 0.99)));
}
"""

        val LIQUID_DRAW = """
precision highp float;
varying vec2 vUv;
uniform sampler2D uState; uniform vec2 uTexel; uniform vec2 uRes; uniform float uAspect;
uniform vec3 uA1, uA2, uA3; uniform float uTime, uBass, uTreble, uBright;
$PACK
float H(vec2 uv){
  // manual bilinear on the packed heights so the surface is silky, not blocky
  vec2 st = uv / uTexel - 0.5; vec2 i = floor(st); vec2 f = fract(st);
  vec2 b = (i + 0.5) * uTexel;
  float a00 = dec(texture2D(uState, b).rg), a10 = dec(texture2D(uState, b + vec2(uTexel.x, 0.0)).rg);
  float a01 = dec(texture2D(uState, b + vec2(0.0, uTexel.y)).rg), a11 = dec(texture2D(uState, b + uTexel).rg);
  return mix(mix(a00, a10, f.x), mix(a01, a11, f.x), f.y);
}
float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
void main(){
  vec2 e = uTexel * 0.75;
  float hx = H(vUv + vec2(e.x, 0.0)) - H(vUv - vec2(e.x, 0.0));
  float hy = H(vUv + vec2(0.0, e.y)) - H(vUv - vec2(0.0, e.y));
  float h0 = H(vUv);
  vec3 n = normalize(vec3(-hx * 14.0, -hy * 14.0, 1.0));
  float slope = length(n.xy);
  vec3 r = reflect(vec3(0.0, 0.0, -1.0), n);
  float l1 = pow(max(dot(r, normalize(vec3(-0.55, 0.62, 0.55))), 0.0), 26.0);
  float l2 = pow(max(dot(r, normalize(vec3(0.6, -0.4, 0.68))), 0.0), 20.0);
  float l3 = pow(max(dot(r, normalize(vec3(0.15, 0.85, 0.5))), 0.0), 70.0);
  vec3 col = uA1 * l1 * 1.7 + uA2 * l2 * 1.4 + mix(uA3, vec3(1.0), 0.6) * l3 * 0.9;
  col += mix(uA2, uA1, smoothstep(-0.02, 0.02, h0)) * smoothstep(0.05, 0.7, slope) * 0.5;
  vec2 sp = (vUv + n.xy * 0.04) * vec2(uAspect, 1.0) * 70.0;
  vec2 id = floor(sp); float s = hash(id);
  float st = step(0.982, s) * smoothstep(0.32, 0.0, length(fract(sp) - 0.5)) * (0.45 + 0.35 * sin(uTime * (1.0 + s * 3.0) + s * 40.0) + 0.5 * uTreble);
  col += vec3(0.85, 0.88, 1.0) * st * 0.32;
  col += uA3 * 0.03 * (1.0 - length(vUv - vec2(0.5, 0.35))) * (1.0 + uBass);
  col *= uBright * (1.0 + uBass * 0.25);
  gl_FragColor = vec4(max(col - 0.004, 0.0), 1.0);
}
"""

        val NEON_SIM = """
precision highp float;
varying vec2 vUv;
uniform sampler2D uState; uniform vec2 uTexel; uniform vec4 uQ[16]; uniform float uN; uniform float uAspect; uniform float uDecay; uniform float uTime;
vec3 hue(float h){ return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0); }
float sdSeg(vec2 p, vec2 a, vec2 b){ vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0); return length(pa - ba * h); }
void main(){
  // light drifts upward and swirls a little as it fades, like smoke made of neon
  vec2 drift = vec2(sin(vUv.y * 9.0 + uTime * 0.7) * 0.6, 1.0) * uTexel * 0.6;
  vec2 uv = vUv - drift;
  vec3 c = texture2D(uState, uv).rgb * 0.5
         + (texture2D(uState, uv + vec2(uTexel.x, 0.0)).rgb + texture2D(uState, uv - vec2(uTexel.x, 0.0)).rgb
         +  texture2D(uState, uv + vec2(0.0, uTexel.y)).rgb + texture2D(uState, uv - vec2(0.0, uTexel.y)).rgb) * 0.125;
  c *= uDecay;
  vec2 p = vUv * vec2(uAspect, 1.0);
  for (int i = 0; i < 8; i++) {
    if (float(i) < uN) {
      vec4 a = uQ[i * 2]; vec4 b = uQ[i * 2 + 1];
      float d = sdSeg(p, a.xy * vec2(uAspect, 1.0), a.zw * vec2(uAspect, 1.0));
      float k = exp(-d * d / (b.x * b.x)) * b.y;
      vec3 col = mix(hue(b.z), vec3(1.0), 0.25);
      c += col * k * 0.55;
    }
  }
  gl_FragColor = vec4(min(c, vec3(1.0)), 1.0);
}
"""

        val NEON_DRAW = """
precision highp float;
varying vec2 vUv;
uniform sampler2D uState; uniform vec2 uTexel; uniform vec2 uRes; uniform float uAspect;
uniform vec3 uA1, uA2, uA3; uniform float uTime, uBass, uTreble, uBright;
void main(){
  vec3 c = texture2D(uState, vUv).rgb;
  // bloom: wide soft samples of the light field
  vec3 b = vec3(0.0);
  for (int i = 0; i < 8; i++) {
    float a = float(i) * 0.785;
    vec2 o = vec2(cos(a), sin(a)) * uTexel * 3.5;
    b += texture2D(uState, vUv + o).rgb + texture2D(uState, vUv + o * 2.4).rgb * 0.6;
  }
  b /= 12.8;
  vec3 col = c * 1.9 + b * 0.9 + pow(c, vec3(2.0)) * 2.6;
  // a faint reflective floor line so the light has somewhere to live
  float floorY = 0.12;
  float fl = exp(-abs(vUv.y - floorY) * 120.0) * 0.05;
  col += mix(uA1, uA2, vUv.x) * fl * (1.0 + uBass * 2.0);
  vec2 rv = vec2(vUv.x, 2.0 * floorY - vUv.y);
  if (vUv.y < floorY) col += texture2D(uState, rv).rgb * 0.35 * (vUv.y / floorY);
  col *= uBright * (1.0 + uBass * 0.3);
  col = 1.0 - exp(-col * 1.4);
  gl_FragColor = vec4(max(col - 0.003, 0.0), 1.0);
}
"""
    }
}
