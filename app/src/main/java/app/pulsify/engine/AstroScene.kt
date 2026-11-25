package app.pulsify.engine

import android.content.Context
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cosmo, an astronaut who has lost control and drifts through deep space. The universe streams past him
 * (we fly forward through a warp of stars and nebula), he tumbles slowly and floats around the screen,
 * bumping off its edges. Scrolling your home pages pushes him along: the screen edge knocks him back in.
 *  - Tap him: he gets knocked away from your finger, spins, flails; the stars rush faster for a moment.
 *  - Tap space: a little push away from that spot.  Swipe: flings him across.
 *  - Left alone he waves, curls up and somersaults, tries to swim, thumbs-up, or just relaxes and drifts.
 */
class AstroScene(private val context: Context) : Creature {
    override var listener: SceneListener? = null
    override var bottomInset = 0f
    override val busy: Boolean get() = true

    private val pet = Pet3D(context)
    private var prog = 0; private var vbo = 0
    private val su = HashMap<String, Int>()
    private var w = 1; private var h = 1
    private val halfW get() = 0.5f * w / min(w, h)
    private val halfH get() = 0.5f * h / min(w, h)

    // where he is: screen uv (0 = centre), drifting with momentum, bouncing off the edges
    private var x = 0f; private var vx = 0.05f
    private var y = 0f; private var vy = 0.03f
    private var pagePos = 0f; private var lastPage = Float.NaN; private var bumpT = -9f
    private var speed = 1f; private var fall = 0f
    private var spin = 0f; private var spinRate = 0.35f
    private var yawPh = 0f; private var shake = 0f
    private var time = 0f
    private var cur: PetClip? = null; private var curT = 0f
    private var from: PetClip? = null; private var fromT = 0f; private var fade = 1f
    private val matA = FloatArray(Pet3D.MAX_BONES * 12); private val matB = FloatArray(Pet3D.MAX_BONES * 12)
    private val ctl = FloatArray(Pet3D.MAX_CTL)
    private var nextLife = 4f
    private var down = false; private var dx = 0f; private var dy = 0f; private var lx = 0f; private var ly = 0f; private var downT = 0f
    private var flung = false
    private var lastMoveT = 0f; private var mvx = 0f; private var mvy = 0f

    private val stars = StarField()
    /** "astro" = Cosmo drifting; "astro2" = Free Fall, the falling astronaut whose clips are falls and impacts. */
    private var model = "astro"
    private val faller get() = model == "astro2"
    override fun onSurfaceCreated() {
        pet.create(); pet.load(model); cur = null
        prog = Gl.program(VERT, FRAG)
        stars.create()
        for (n in listOf("uRes", "uHalf", "uFall", "uSpeed", "uTime", "uPar", "uShake")) su[n] = GLES20.glGetUniformLocation(prog, n)
        val ids = IntArray(1); GLES20.glGenBuffers(1, ids, 0); vbo = ids[0]
        val tri = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
        val fb = ByteBuffer.allocateDirect(tri.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(tri); fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, tri.size * 4, fb, GLES20.GL_STATIC_DRAW)
        play("Idle")
    }
    override fun onSurfaceChanged(width: Int, height: Int) { w = max(1, width); h = max(1, height) }
    override fun release() {
        pet.release(); stars.release()
        if (prog != 0) GLES20.glDeleteProgram(prog)
        if (vbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
        prog = 0; vbo = 0
    }

    private fun play(name: String) {
        val c = pet.clips[name] ?: return
        if (c === cur) return
        from = cur; fromT = curT; fade = if (cur == null) 1f else 0f
        cur = c; curT = 0f
    }

    private val size get() = min(0.4f * halfH, halfW * 0.8f)
    private fun screenX(): Float = x
    private fun hit(nx: Float, ny: Float): Boolean {
        val ux = (nx - 0.5f) * 2f * halfW; val uy = (0.5f - ny) * 2f * halfH
        return hypot(ux - screenX(), uy - y) < size * 0.6f
    }

    // ---------------------------------------------------------------- touch
    override fun touchDown(nx: Float, ny: Float): Boolean {
        down = true; dx = nx; dy = ny; lx = nx; ly = ny; downT = time; mvx = 0f; mvy = 0f; lastMoveT = time
        return hit(nx, ny)
    }
    override fun touchMove(nx: Float, ny: Float): Boolean {
        val dt = max(0.008f, time - lastMoveT)
        mvx = mvx * 0.5f + (nx - lx) / dt * 0.5f; mvy = mvy * 0.5f + (ny - ly) / dt * 0.5f
        lx = nx; ly = ny; lastMoveT = time
        return false
    }
    override fun touchUp(): Boolean {
        if (!down) return false
        down = false
        val dist = hypot(lx - dx, ly - dy)
        if (dist > 0.12f && time - downT < 0.6f && abs(mvx) > 0.6f) {
            // a swipe: fling him off sideways toward the next page
            vx = (mvx * 2f * halfW * 0.35f).coerceIn(-2.4f, 2.4f)
            vy = (-mvy * 2f * halfH * 0.35f).coerceIn(-2.4f, 2.4f)
            spinRate += sign(mvx) * 3f
            play("Flail"); boost(1.5f)
            if (!flung) { flung = true; listener?.onDiscovery("cosmo fling") }
            return true
        }
        return false
    }
    override fun tapAt(nx: Float, ny: Float): Boolean {
        val onHim = hit(nx, ny)
        boost(if (onHim) 3.2f else 1.2f)
        play(if (onHim) "Poke" else "Flail")
        // pushed away from the finger, harder the closer it was
        val ux = (nx - 0.5f) * 2f * halfW; val uy = (0.5f - ny) * 2f * halfH
        val dx = x - ux; val dy = y - uy; val d = max(0.05f, hypot(dx, dy))
        val k = (if (onHim) 1.1f else 0.5f) / (0.4f + d)
        vx += dx / d * k; vy += dy / d * k
        return true
    }
    override fun longPressAt(nx: Float, ny: Float): Boolean { boost(4f); play("Flail"); return true }
    private fun boost(k: Float) {
        speed = min(9f, speed + k); spinRate += (if (Random.nextBoolean()) 1f else -1f) * k * 0.9f; shake = min(1f, shake + 0.25f * k)
    }

    override fun greet() { play("Wave") }
    override fun swiped(dir: Float, fast: Boolean) { if (fast) boost(1f) }
    override fun perform(trick: String): Boolean {
        if (faller) { when (trick) { "faster" -> { boost(5f); play("Flail") }; "wave", "thumbs" -> play("Poke"); else -> play("Impact") }; return true }
        when (trick) {
            "faster" -> { boost(5f); play("Flail") }
            "wave" -> play("Wave"); "look", "curl" -> play("Stretch"); "reach", "swim" -> play("Reach")
            "thumbs" -> play("ThumbsUp"); "stretch", "relax" -> play("Stretch")
            else -> return false
        }
        return true
    }

    // ---------------------------------------------------------------- update
    override fun update(dt: Float, input: SceneInput) {
        time += dt
        val want = if (input.style == 1) "astro2" else "astro"
        if (want != model) { model = want; pet.load(want); cur = null; from = null; play("Idle") }
        // the warp: calm drift speed 1, kicked up by touches and the bass, settling back
        speed += (1f - speed) * min(1f, dt * 0.6f)
        if (input.audio.active) speed = max(speed, 1f + input.audio.bass * 2.5f)
        fall += speed * dt * 0.5f
        shake *= exp(-dt * 3f)
        // tumble: always turning slowly, faster after a knock
        spinRate += ((if (spinRate >= 0f) 0.22f else -0.22f) - spinRate) * min(1f, dt * 0.4f)
        spin += spinRate * dt
        yawPh += dt * (0.22f + speed * 0.04f)
        // page scrolling moves the world under him: the screen edge comes along and knocks him back in
        val pp = input.pagePos
        val dpx = if (lastPage.isNaN()) 0f else (pp - lastPage) * 2f * halfW
        lastPage = pp; pagePos = pp
        x -= dpx * 0.85f
        // free drift: weightless, barely any drag, a faint wander so he never stops
        vx += sin(time * 0.31f + 1.3f) * 0.02f * dt; vy += cos(time * 0.27f) * 0.02f * dt
        vx *= exp(-dt * 0.12f); vy *= exp(-dt * 0.12f)
        val spd = hypot(vx, vy)
        if (spd < 0.06f) { vx += vx / max(spd, 1e-3f) * 0.02f * dt; vy += vy / max(spd, 1e-3f) * 0.02f * dt }
        if (spd > 2.5f) { vx *= 2.5f / spd; vy *= 2.5f / spd }
        x += vx * dt; y += vy * dt
        // bounce off the screen edges
        val r = size * 0.42f
        val lx = -halfW + r; val rx = halfW - r; val by = -halfH + r + bottomInset * 2f * halfH; val ty = halfH - r * 1.1f
        var bumped = 0f
        val scroll = (dpx / max(dt, 0.008f)).coerceIn(-6f, 6f)     // how fast the edge is sweeping him
        if (x < lx) { val push = max(0f, scroll) * 0.35f; bumped = max(bumped, abs(vx) + push); x = lx; vx = abs(vx) * 0.75f + push }
        if (x > rx) { val push = max(0f, -scroll) * 0.35f; bumped = max(bumped, abs(vx) + push); x = rx; vx = -abs(vx) * 0.75f - push }
        if (y < by) { bumped = max(bumped, abs(vy)); y = by; vy = abs(vy) * 0.75f }
        if (y > ty) { bumped = max(bumped, abs(vy)); y = ty; vy = -abs(vy) * 0.75f }
        if (bumped > 0.25f && time - bumpT > 0.6f) {
            bumpT = time; spinRate += (if (Random.nextBoolean()) 1f else -1f) * min(3f, bumped * 2f); shake = min(1f, shake + bumped * 0.4f)
            if (faller) { if (cur?.name != "Impact" && cur?.name != "Poke") play(if (bumped > 0.7f) "Impact" else "Poke") }
            else if (cur?.name != "Poke") play(if (bumped > 0.7f) "Poke" else "Flail")
            if (bumped > 0.7f) listener?.onDiscovery("cosmo bonk")
        }
        // what he's doing
        val c = cur
        if (c != null) {
            curT += dt
            val done = !c.loop && curT > c.duration
            if (c.name == "Flail" && speed < 1.6f && hypot(vx, vy) < 0.4f) play("Idle")
            else if (done) play("Idle")
        }
        nextLife -= dt
        if (nextLife <= 0f && cur?.name == "Idle") {
            nextLife = 6f + Random.nextFloat() * 6f
            play(if (faller) listOf("Poke", "Impact").random() else listOf("Wave", "Reach", "ThumbsUp", "Stretch").random())   // no LookAround: it warps the suit
        }
        advance(dt)
    }

    private fun advance(dt: Float) {
        val c = cur ?: return
        pet.sample(c, if (c.loop) curT % max(0.01f, c.duration) else min(curT, c.duration), matA, ctl)
        val nb = pet.nBones * 12
        val f = from
        if (f != null && fade < 1f) {
            fade = min(1f, fade + dt / 0.3f); fromT += dt
            pet.sample(f, if (f.loop) fromT % max(0.01f, f.duration) else min(fromT, f.duration), matB, ctl)
            val a = fade * fade * (3f - 2f * fade)
            for (i in 0 until nb) pet.pose[i] = matB[i] + (matA[i] - matB[i]) * a
        } else System.arraycopy(matA, 0, pet.pose, 0, nb)
        pet.pivot = pet.boxCz
        pet.yaw = sin(yawPh) * 1.1f + time * 0.15f * sign(spinRate)
        pet.lean = sin(yawPh * 0.7f + 1f) * 0.5f
    }

    // ---------------------------------------------------------------- draw
    override fun draw(input: SceneInput) {
        // stars: one point-sprite draw over a nebula painted once (was a heavy per-pixel shader)
        stars.draw(fall % 1000f, time % 1000f, shake, pagePos)
        GLES20.glDisableVertexAttribArray(0)
        val m = min(w, h)
        val k = size / pet.height
        val boxHalf = pet.boxR * k
                pet.ensureSprite((boxHalf * 2f * m * 1.6f).toInt())
        pet.render()
        pet.beginFx()
        val sx = screenX() + (Random.nextFloat() - 0.5f) * shake * 0.02f
        pet.sprite(sx, y + (Random.nextFloat() - 0.5f) * shake * 0.02f, boxHalf, spin, 1f)
        pet.endFx()
    }

    companion object {
        private const val VERT = "attribute vec2 aPos;\nvoid main(){ gl_Position = vec4(aPos, 0.0, 1.0); }\n"
        // Deep space rushing upward: three layers of stars that stretch into streaks with speed,
        // drifting dust, a faint nebula, and now and then a planet sliding past far behind.
        private const val FRAG = """
precision highp float;
uniform vec2 uRes; uniform vec2 uHalf; uniform float uFall; uniform float uSpeed; uniform float uTime; uniform float uPar; uniform float uShake;
float h21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float noise(vec2 p){ vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
  return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y); }
float fbm(vec2 p){ float n = 0.0, a = 0.5; for (int i = 0; i < 5; i++) { n += a * noise(p); p = p * 2.03 + vec2(1.7, 9.2); a *= 0.5; } return n; }
// one layer of stars in pixel space: cell = grid size in px, dens = share of cells with a star
vec3 stars(vec2 px, float cell, float dens, float rad, float seed, float tw){
  vec2 g = px / cell; vec2 id = floor(g);
  vec3 c = vec3(0.0);
  for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
    vec2 cid = id + vec2(float(i), float(j));
    float r = h21(cid + seed);
    if (r > dens) continue;
    vec2 sp = (cid + vec2(h21(cid + seed + 3.1), h21(cid + seed + 7.7))) * cell;
    float d = length(px - sp);
    float m = h21(cid + seed + 11.3);
    float rr = rad * (0.6 + 0.9 * m * m);
    float a = clamp(rr + 0.5 - d, 0.0, 1.0);                   // 1 px anti-aliased disc
    float glow = exp(-d * d / (rr * rr * 6.0)) * 0.25 * m;     // the brightest ones get a soft halo
    float t = 0.75 + 0.25 * sin(uTime * (0.6 + 2.0 * h21(cid + seed + 5.0)) * tw + r * 40.0);
    vec3 tint = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.86, 0.7), h21(cid + seed + 9.0));
    c += tint * (a + glow) * t * (0.35 + 0.65 * m);
  }
  return c;
}
void main(){
  vec2 px = gl_FragCoord.xy;
  float s = min(uRes.x, uRes.y) / 1080.0;                     // keep the look the same on every screen
  vec2 drift = vec2(uPar * 0.15 * uRes.x, uFall * 6.0) ;
  vec3 col = vec3(0.004, 0.004, 0.012);
  // Milky Way: a faint diagonal band of light and dark dust lanes
  vec2 q = (px - 0.5 * uRes) / uRes.y + drift.xy * vec2(0.00002, 0.00002);
  float band = exp(-pow((q.x * 0.6 + q.y * 0.9 + 0.05) * 3.2, 2.0));
  float n = fbm(q * 3.0 + 3.0), dust = fbm(q * 7.0 + 11.0);
  col += band * (vec3(0.05, 0.045, 0.08) * smoothstep(0.35, 0.85, n) - vec3(0.03, 0.025, 0.04) * smoothstep(0.55, 0.8, dust) * 0.6);
  col += vec3(0.03, 0.012, 0.05) * smoothstep(0.55, 0.95, fbm(q * 1.6 + 30.0)) * 0.8;
  // three layers of distant stars, the nearer ones drifting a little faster
  col += stars(px + drift * 0.25, 46.0 * s, 0.55, 0.75 * s, 1.0, 0.7) * 0.7;
  col += stars(px + drift * 0.5, 90.0 * s, 0.45, 1.05 * s, 17.0, 1.0);
  col += stars(px + drift * 1.0, 190.0 * s, 0.35, 1.5 * s, 41.0, 1.3) * 1.15;
  // a few tiny stars inside the Milky Way band
  col += stars(px + drift * 0.2, 22.0 * s, 0.35 * band, 0.55 * s, 71.0, 0.5) * 0.6 * band;
  col += vec3(0.25, 0.35, 0.6) * uShake * 0.04;
  gl_FragColor = vec4(col, 1.0);
}
"""
    }
}
