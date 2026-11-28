package app.pulsify.engine

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

const val DROP_ID = 15

/** The liquids Droppy can be made of (its looks). */
class Liquid(val name: String, val k: Float, val damp: Float, val rest: Float, val splash: Float, val crawl: Float, val g: Float, val wall: Float)
val LIQUIDS = listOf(
    Liquid("Water", 140f, 9f, .35f, 1f, .45f, 2.4f, .5f),
    Liquid("Honey", 60f, 15f, .08f, .45f, .16f, 1.9f, .92f),
    Liquid("Lava", 95f, 11f, .18f, .7f, .28f, 2.2f, .75f),
    Liquid("Slime", 48f, 4.5f, .62f, .55f, .34f, 2.2f, .85f),
    Liquid("Mercury", 230f, 10f, .26f, 1.35f, .75f, 3f, .2f),
    Liquid("Galaxy", 120f, 8f, .4f, 1f, .4f, 1.6f, .5f),
)

/**
 * Droppy, a living drop of liquid. Its size is the battery level. It follows your finger, and when it hits
 * an edge of the screen it splashes: part of it flies off as beads, which slide down, crawl back along the
 * floor and melt back in until it is whole again. While the phone charges, rain falls on it (and it shows
 * the percentage for a moment). Six liquids (water, honey, lava, slime, mercury, galaxy) and three faces.
 *
 * Drawing: the beads are summed as smooth bumps into a half-resolution field texture; one full-screen pass
 * draws the body from its 16 outline radii, melts it together with the beads (smooth minimum), shades the
 * liquid as a dome (highlights, rim, its own look per liquid) and draws the face on top.
 * The physics and shaders mirror tools/drop (the browser preview) line for line.
 */
class DropScene : Creature {
    override var listener: SceneListener? = null
    override var bottomInset = 0f
    override val busy: Boolean get() = held || drops.isNotEmpty() || abs(vx) + abs(vy) > .02f || charging || act != null || bass > .02f

    private class Bead(var x: Float, var y: Float, var vx: Float, var vy: Float, var m: Float, var rain: Boolean) { var age = 0f }

    var liq = 0; var face = 0
    private val L get() = LIQUIDS[liq.coerceIn(0, LIQUIDS.size - 1)]
    private var halfW = .5f; private var halfH = 1f; private var floor = -.5f; private var ground = Float.NaN; private var homeX = 0f
    private var x = 0f; private var y = .2f; private var vx = 0f; private var vy = 0f; private var ax = 0f; private var ay = 0f
    private val d = FloatArray(M); private val dv = FloatArray(M)
    private val drops = ArrayList<Bead>()
    private var battery = .6f; private var shown = .6f; private var charging = false; private var wasCharging = false; private var bonus = 0f; private var firstFrame = true
    private var t = 0f; private var held = false; private var fx = 0f; private var fy = 0f; private var downT = 0f; private var moved = 0f
    private var lookX = 0f; private var lookY = 0f; private var tlx = 0f; private var tly = 0f; private var lookT = 0f
    private var blink = 0f; private var nextBlink = 2f; private var expr = "normal"; private var exprT = 0f
    private var act: String? = null; private var actT = 0f; private var nextAct = 4f; private var rainT = 0f; private var pctT = -9f
    private var bass = 0f; private var sndT = 0f; private var squash = 0f
    // the battery icon over its head (uv), on a spring, and the tilt of the liquid inside
    private var ix = 0f; private var iy = .5f; private var ivx = 0f; private var ivy = 0f; private var tilt = 0f; private var iconSet = false; private var iconA = 0f
    private fun rnd() = Random.nextFloat()
    private fun clamp(v: Float, a: Float, b: Float) = if (v < a) a else if (v > b) b else v
    private fun sm(v: Float): Float { val c = clamp(v, 0f, 1f); return c * c * (3 - 2 * c) }

    /** All the liquid (area units), from the battery: 0% = a small bead, 100% = a big drop. */
    private val total: Float get() { val r = .1f + .27f * shown; return r * r }
    private val splashed: Float get() { var s = 0f; for (p in drops) if (!p.rain) s += p.m; return s }
    private val R: Float get() = sqrt(max(total * .15f, total - splashed + bonus))
    private val restY get() = R * .82f

    override fun setGround(y: Float) { ground = y }
    override fun setHomeX(x: Float) { homeX = x }

    // ------------------------------------------------------------------ touch (uv, y up)
    private fun ux(nx: Float) = (nx - .5f) * 2f * halfW
    private fun uy(ny: Float) = (.5f - ny) * 2f * halfH
    override fun touchDown(nx: Float, ny: Float): Boolean {
        held = true; fx = ux(nx); fy = uy(ny); downT = t; moved = 0f; expr = "surprised"; exprT = .6f; act = null; return true
    }
    override fun touchMove(nx: Float, ny: Float): Boolean {
        if (!held) return false
        val x1 = ux(nx); val y1 = uy(ny); moved += hypot(x1 - fx, y1 - fy); fx = x1; fy = y1; return true
    }
    override fun touchUp(): Boolean {
        if (!held) return false; held = false
        val quick = t - downT < .3f && moved < .03f
        if (quick && hypot(fx - x, fy - y) < R * 1.3f) tickle()
        return true
    }
    override fun tapAt(nx: Float, ny: Float): Boolean = true
    override fun longPressAt(nx: Float, ny: Float): Boolean { doTrick("wobble"); return true }
    override fun greet() { if (act == null && !held) doTrick("hop") }
    override fun swiped(dir: Float, fast: Boolean) { vx += -dir * 1.6f; vy += .6f; expr = "surprised"; exprT = .8f }
    override fun perform(trick: String): Boolean {
        when (trick) {
            "hop", "wobble", "split", "splat" -> doTrick(trick)
            "tickle" -> tickle()
            "rain" -> { pctT = t; repeat(14) { add(x + (rnd() - .5f) * R * 2.2f, halfH + .05f + rnd() * .6f, 0f, -1.6f, total * .006f, true) } }
            else -> return false
        }
        return true
    }
    private fun tickle() {
        expr = "happy"; exprT = 1.2f; for (i in 0 until M) dv[i] += (rnd() - .5f) * 3f; vy += 1.1f
        listener?.onDiscovery("droppy tickle")
    }
    private fun doTrick(n: String) {
        act = n; actT = 0f
        when (n) {
            "hop" -> { vy = 2f + rnd() * .4f; vx += (rnd() - .5f) * .8f; squash = .5f }
            "split" -> {   // spits out two little droplets that bounce away and come home
                val m = total * .14f
                for (s in intArrayOf(-1, 1)) add(x + s * R * .8f, y + R * .2f, s * (1.1f + rnd() * .4f), 1.6f + rnd() * .5f, m, false)
                expr = "happy"; exprT = 1.4f
            }
            "splat" -> { vy = 2.6f; vx = (if (x > 0) 1f else -1f) * 1.8f }
        }
    }
    private fun add(x: Float, y: Float, vx: Float, vy: Float, m: Float, rain: Boolean) { if (drops.size < MAX) drops += Bead(x, y, vx, vy, m, rain) }

    // ------------------------------------------------------------------ the splash when it hits an edge
    private fun splash(nx: Float, ny: Float, vn: Float) {
        val r = R; val hard = clamp((vn - 1.05f) / 2.2f, 0f, 1f)
        if (hard <= 0f) { squash = max(squash, clamp(vn * .25f, 0f, .4f)); return }
        val avail = total - splashed
        val lose = avail * clamp(hard * .42f * L.splash, 0f, .42f)
        val n = max(2, ((3f + hard * 13f) * L.splash).roundToInt())
        val px = x - nx * r * .9f; val py = y - ny * r * .9f; val tx = -ny; val ty = nx
        var left = lose
        for (i in 0 until n) {
            if (drops.size >= MAX) break
            val m = if (i == n - 1) left else lose / n * (.4f + rnd() * 1.2f); left -= m; if (m <= 1e-6f) continue
            val along = (rnd() - .5f) * 2f; val up = .25f + rnd() * .9f; val sp = vn * (.35f + rnd() * .55f)
            add(px + tx * along * r * .6f, py + ty * along * r * .6f, (nx * up + tx * along * 1.1f) * sp, (ny * up + ty * along * 1.1f) * sp, m, false)
        }
        squash = .9f; expr = "squish"; exprT = .9f + hard
        for (i in 0 until M) dv[i] += (rnd() - .5f) * 4f * hard
        if (hard > .5f) listener?.onDiscovery("droppy splash")
    }

    // ------------------------------------------------------------------ update
    override fun update(dt: Float, input: SceneInput) {
        liq = input.style.coerceIn(0, LIQUIDS.size - 1); face = input.coat.coerceIn(0, 2)
        floor = if (ground.isNaN()) -halfH * .5f else ground
        floor = max(floor, -halfH + bottomInset * 2f * halfH + .02f)
        // two half steps keep the springs stable
        val h = min(dt, .05f) / 2f
        step(h, input); step(h, input)
    }

    private fun step(dt: Float, input: SceneInput) {
        t += dt; val L = L
        battery = input.battery.coerceIn(0f, 1f); charging = input.charging
        if (firstFrame) { shown = battery; firstFrame = false; x = homeX * 2f * halfW; y = floor + .3f }
        shown += (battery - shown) * min(1f, dt * (if (charging) .8f else .3f))
        if (charging && !wasCharging) { pctT = t; expr = "happy"; exprT = 2f }
        wasCharging = charging
        bonus *= exp(-dt * .7f)
        val music = input.audio.active
        bass += ((if (music) input.audio.bass else 0f) - bass) * min(1f, dt * 12f)
        // a sound: look up for the first 3 s, dance after 4 s
        if (music) sndT += dt else sndT = 0f
        val dancing = sndT > 4f

        val r = R; val fl = floor; val hw = halfW; val hh = halfH
        val ox = vx; val oy = vy
        if (held) {
            // it follows your finger like a drop on glass: a soft spring, so it stretches behind
            vx += ((fx - x) * 55f - vx * 11f) * dt; vy += ((fy - y) * 55f - vy * 11f) * dt
        } else {
            vy -= L.g * dt
            if (dancing && y - r * .85f <= fl + .002f && bass > .45f && vy <= 0f) { vy = .9f + bass * .8f; squash = .6f }
        }
        x += vx * dt; y += vy * dt
        val rest = r * .82f
        if (y - rest < fl) { val vn = -vy; y = fl + rest; if (vn > 0f) { if (!held) splash(0f, 1f, vn); vy = if (vn > .25f) vn * L.rest else 0f; vx *= .02f.pow(dt) } }
        if (y + rest > hh) { val vn = vy; y = hh - rest; if (vn > 0f) { splash(0f, -1f, vn); vy = -vn * L.rest } }
        if (x - rest < -hw) { val vn = -vx; x = -hw + rest; if (vn > 0f) { splash(1f, 0f, vn); vx = vn * L.rest } }
        if (x + rest > hw) { val vn = vx; x = hw - rest; if (vn > 0f) { splash(-1f, 0f, vn); vx = -vn * L.rest } }
        ax = (vx - ox) / max(dt, 1e-3f); ay = (vy - oy) / max(dt, 1e-3f)

        // outline: springs, surface tension between neighbours, inertia; the edges flatten it
        val sq = squash; squash *= exp(-dt * 5f)
        val wob = if (act == "wobble") sin(actT * 18f) * .25f * (1f - actT / 1.6f) else 0f
        val dance = if (dancing) bass * .18f else 0f
        for (i in 0 until M) {
            val a = i.toFloat() / M * TAU; val cx = cos(a); val cy = sin(a)
            val lap = d[(i + 1) % M] + d[(i + M - 1) % M] - 2f * d[i]
            val inert = -(ax * cx + ay * cy) * .007f
            // moving liquid is never a perfect circle: it stretches along its motion and trails a tail behind
            val sp = hypot(vx, vy); val ux = if (sp > 1e-3f) vx / sp else 0f; val uy = if (sp > 1e-3f) vy / sp else 0f
            val al = cx * ux + cy * uy; val st = clamp(sp * .09f, 0f, .32f)
            val flow = st * (al * al - .5f) + st * .9f * max(0f, -al).pow(3) - st * .35f * max(0f, al).pow(3) +
                .05f * min(1f, sp) * sin(3f * a + t * 9f) + .035f * min(1f, sp) * sin(5f * a - t * 13f)
            val tgt = sq * .35f * (cx * cx - cy * cy) + wob * cos(2f * a + t * 3f) + dance * cy * cy + flow
            dv[i] += (L.k * (tgt - d[i]) - L.damp * dv[i] + L.k * .6f * lap + inert * L.k) * dt
        }
        var mean = 0f
        for (i in 0 until M) { d[i] = clamp(d[i] + dv[i] * dt, -.55f, .85f); mean += d[i] }
        mean /= M; for (i in 0 until M) d[i] -= mean     // keep its area: squashed here, it bulges there
        for (i in 0 until M) {
            val a = i.toFloat() / M * TAU; val cx = cos(a); val cy = sin(a); val rr = r * (1f + d[i])
            var lim = 9f
            if (cy < -.05f) lim = min(lim, (y - fl) / -cy)
            if (cy > .05f) lim = min(lim, (hh - y) / cy)
            if (cx < -.05f) lim = min(lim, (x + hw) / -cx)
            if (cx > .05f) lim = min(lim, (hw - x) / cx)
            if (rr > lim) { d[i] = lim / r - 1f; if (dv[i] > 0f) dv[i] = 0f }
        }

        // the splashed beads: fly, slide down the walls, crawl back along the floor, merge home
        var i = drops.size - 1
        while (i >= 0) {
            val p = drops[i]; p.age += dt; val pr = sqrt(p.m)
            p.vy -= L.g * dt * (if (p.rain) .4f else 1f)
            val home = p.age > .7f
            if (home) {
                val dx = x - p.x; val dy = y - p.y; val dd = hypot(dx, dy) + 1e-4f
                val pull = sm((p.age - .7f) / 1.8f) * (if (held || y - rest > fl + .05f) 3.2f else .9f)
                p.vx += dx / dd * pull * dt; p.vy += dy / dd * pull * dt * (if (held) 1f else .3f)
            }
            p.x += p.vx * dt; p.y += p.vy * dt
            if (p.y - pr * .6f <= fl) {
                p.y = fl + pr * .6f; if (p.vy < 0f) p.vy = if (p.vy < -1f) -p.vy * .15f else 0f
                if (home) { val dir = sign(x - p.x); p.vx += (dir * L.crawl * (1f + sm((p.age - .7f) / 2f)) - p.vx) * min(1f, dt * 4f) } else p.vx *= .05f.pow(dt)
            }
            if (p.x - pr * .6f < -hw) { p.x = -hw + pr * .6f; p.vx = abs(p.vx) * .1f; p.vy *= (1f - L.wall).pow(dt * 6f) }
            if (p.x + pr * .6f > hw) { p.x = hw - pr * .6f; p.vx = -abs(p.vx) * .1f; p.vy *= (1f - L.wall).pow(dt * 6f) }
            if (p.y + pr * .6f > hh && !p.rain) { p.y = hh - pr * .6f; p.vy = -abs(p.vy) * .2f }
            val dd = hypot(p.x - x, p.y - y)
            if ((p.age > .35f || p.rain) && dd < R * .95f + pr * .45f) {
                if (p.rain) bonus += p.m * .6f
                drops.removeAt(i)
                val a = atan2(p.y - y, p.x - x); val k = (((a / TAU) % 1f + 1f) % 1f * M).roundToInt() % M
                dv[k] += if (p.rain) -.8f else -1.6f; dv[(k + 1) % M] -= .5f; dv[(k + M - 1) % M] -= .5f
            }
            i--
        }
        // beads that touch become one
        var a = 0
        while (a < drops.size) {
            var b = drops.size - 1
            while (b > a) {
                val p = drops[a]; val q = drops[b]
                if (p.age > .25f && q.age > .25f && hypot(p.x - q.x, p.y - q.y) < (sqrt(p.m) + sqrt(q.m)) * .5f) {
                    val m = p.m + q.m
                    p.x = (p.x * p.m + q.x * q.m) / m; p.y = (p.y * p.m + q.y * q.m) / m
                    p.vx = (p.vx * p.m + q.vx * q.m) / m; p.vy = (p.vy * p.m + q.vy * q.m) / m
                    p.m = m; p.rain = p.rain && q.rain; p.age = max(p.age, q.age); drops.removeAt(b)
                }
                b--
            }
            a++
        }
        // rain while charging
        if (charging) {
            rainT -= dt
            if (rainT <= 0f) { rainT = .1f + rnd() * .12f; add(x + (rnd() - .5f) * R * 2.2f, hh + .05f, (rnd() - .5f) * .05f, -1.4f - rnd() * .6f, total * (.004f + rnd() * .006f), true) }
        }

        // the face: eyes on the finger, up at the rain, around now and then; blinking; expressions
        lookT -= dt
        if (held) { tlx = fx - x; tly = fy - y }
        else if (charging) { tlx = (rnd() - .5f) * .02f; tly = 1f }
        else if (sndT > 0f && sndT < 3f) { tlx = 0f; tly = 1f }
        else if (lookT <= 0f) { lookT = 1f + rnd() * 2.5f; val r0 = rnd(); tlx = if (r0 < .5f) 0f else (rnd() - .5f) * 2f; tly = if (r0 < .5f) 0f else rnd() - .3f }
        val ll = hypot(tlx, tly).let { if (it < 1e-5f) 1f else it }; val lk = min(1f, ll / .15f)
        lookX += (tlx / ll * lk - lookX) * min(1f, dt * 10f); lookY += (tly / ll * lk - lookY) * min(1f, dt * 10f)
        nextBlink -= dt; if (nextBlink <= 0f) { blink = 1f; nextBlink = 2f + rnd() * 3f }
        blink = max(0f, blink - dt * 7f)
        exprT -= dt
        if (exprT <= 0f) expr = if (dancing) "happy" else if (battery < .15f && !charging) "sleepy" else if (hypot(vx, vy) > 1.6f) "surprised" else "normal"

        // the battery icon floats above its head on a soft spring; the liquid inside tilts as it swings
        run {
            val tx = clamp(x - .03f, -hw + .16f, hw - .3f); val ty = min(y + R * 1.2f + .14f, hh - .14f)
            if (!iconSet) { ix = tx; iy = ty; iconSet = true }
            val ax2 = (tx - ix) * 40f - ivx * 9f; val ay2 = (ty - iy) * 40f - ivy * 9f
            ivx += ax2 * dt; ivy += ay2 * dt; ix += ivx * dt; iy += ivy * dt
            tilt += (clamp(-ax2 * .03f, -1f, 1f) - tilt) * min(1f, dt * 6f)
            // only shown while charging: it fades in on the plug, out when unplugged
            iconA += ((if (charging) 1f else 0f) - iconA) * min(1f, dt * 4f)
        }
        // tricks (at least three a minute)
        if (act != null) { actT += dt; if (actT > 1.6f) act = null }
        nextAct -= dt
        if (nextAct <= 0f && !held && act == null && !dancing) {
            nextAct = TRICK_GAP_MIN + rnd() * TRICK_GAP_RANGE
            doTrick(listOf("hop", "wobble", "split", "hop", "wobble", "look")[Random.nextInt(6)])
        }
    }

    // ------------------------------------------------------------------ GL
    private var pField = 0; private var pDrop = 0; private var tri = 0; private var qVbo = 0
    private var fTex = 0; private var fFbo = 0; private var fw = 0; private var fh = 0
    private val uf = HashMap<String, Int>(); private val ud = HashMap<String, Int>()
    private val qBuf: FloatBuffer = ByteBuffer.allocateDirect(MAX * 24 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val qArr = FloatArray(MAX * 24)
    private val rad = FloatArray(M)
    private val vp = IntArray(4)

    override fun onSurfaceCreated() {
        pField = Gl.program(FIELD_VS, FIELD_FS); pDrop = Gl.program(VS, FS)
        uf["uHalf"] = GLES20.glGetUniformLocation(pField, "uHalf")
        for (n in listOf("uField", "uTexel", "uHalf", "uRes", "uBody", "uRad", "uLiq", "uFaceKind", "uTime", "uEye", "uExpr", "uPct", "uFloor", "uBass", "uIcon", "uSlosh"))
            ud[n] = GLES20.glGetUniformLocation(pDrop, if (n == "uRad") "uRad[0]" else n)
        val ids = IntArray(2); GLES20.glGenBuffers(2, ids, 0); tri = ids[0]; qVbo = ids[1]
        val tb = ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asFloatBuffer().put(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)); tb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tri); GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 24, tb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        fw = 0; fh = 0
    }
    override fun onSurfaceChanged(width: Int, height: Int) {
        val m = min(width, height).toFloat().coerceAtLeast(1f); halfW = .5f * width / m; halfH = .5f * height / m
    }
    private fun ensureField(w: Int, h: Int) {
        if (fFbo != 0 && w == fw && h == fh) return
        if (fTex == 0) { val t1 = IntArray(1); GLES20.glGenTextures(1, t1, 0); fTex = t1[0]; val f1 = IntArray(1); GLES20.glGenFramebuffers(1, f1, 0); fFbo = f1[0] }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fTex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fFbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fTex, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target)
        fw = w; fh = h
    }

    override fun draw(input: SceneInput) {
        if (pDrop == 0) return
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0)
        val w = max(8, vp[2] / 2); val h = max(8, vp[3] / 2)
        ensureField(w, h)
        // field: every bead as a quad whose 0.5 level sits on its visible edge
        var n = 0
        for (p in drops) {
            if (n >= MAX) break
            val r = sqrt(p.m) / .454f; val o = n * 24
            for (k in 0 until 6) {
                val sx = QX[k]; val sy = QY[k]
                qArr[o + k * 4] = p.x + sx * r; qArr[o + k * 4 + 1] = p.y + sy * r; qArr[o + k * 4 + 2] = sx; qArr[o + k * 4 + 3] = sy
            }
            n++
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fFbo); GLES20.glViewport(0, 0, w, h)
        GLES20.glClearColor(0f, 0f, 0f, 0f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (n > 0) {
            GLES20.glUseProgram(pField); GLES20.glUniform2f(uf["uHalf"]!!, halfW, halfH)
            GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)
            qBuf.clear(); qBuf.put(qArr, 0, n * 24); qBuf.position(0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, qVbo); GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, n * 24 * 4, qBuf, GLES20.GL_STREAM_DRAW)
            GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 4, GLES20.GL_FLOAT, false, 0, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, n * 6)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target); GLES20.glViewport(vp[0], vp[1], vp[2], vp[3])
        // the liquid, over the world
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(pDrop)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fTex); GLES20.glUniform1i(ud["uField"]!!, 0)
        GLES20.glUniform2f(ud["uTexel"]!!, 1f / w, 1f / h); GLES20.glUniform2f(ud["uHalf"]!!, halfW, halfH); GLES20.glUniform2f(ud["uRes"]!!, vp[2].toFloat(), vp[3].toFloat())
        val r = R; for (i in 0 until M) rad[i] = r * (1f + d[i])
        GLES20.glUniform3f(ud["uBody"]!!, x, y, r); GLES20.glUniform1fv(ud["uRad"]!!, M, rad, 0)
        GLES20.glUniform1f(ud["uLiq"]!!, liq.toFloat()); GLES20.glUniform1f(ud["uFaceKind"]!!, face.toFloat()); GLES20.glUniform1f(ud["uTime"]!!, t % 1000f)
        val op = 1f - blink
        val open = if (expr == "sleepy") .35f * op else op
        val big = if (expr == "surprised") 1.25f else 1f
        GLES20.glUniform4f(ud["uEye"]!!, lookX, lookY, open, big)
        val smile = when (expr) { "happy" -> 1f; "sleepy" -> -.2f; "squish" -> -.4f; else -> .55f }
        GLES20.glUniform4f(ud["uExpr"]!!, if (expr == "happy") 1f else 0f, if (expr == "squish") 1f else 0f, smile, if (expr == "surprised") 1f else 0f)
        val pu = t - pctT; val pa = if (pu < 3.5f) min(1f, pu / .3f) * min(1f, (3.5f - pu) / .4f) else 0f
        GLES20.glUniform2f(ud["uPct"]!!, (battery * 100f).roundToInt().toFloat(), pa)
        GLES20.glUniform4f(ud["uIcon"]!!, ix, iy, .1f, iconA); GLES20.glUniform2f(ud["uSlosh"]!!, tilt, min(1f, hypot(ivx, ivy) * 2f))
        GLES20.glUniform1f(ud["uFloor"]!!, floor); GLES20.glUniform1f(ud["uBass"]!!, bass)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tri); GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        GLES20.glDisableVertexAttribArray(0); GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0); GLES20.glDisable(GLES20.GL_BLEND)
    }

    override fun release() {
        for (p in intArrayOf(pField, pDrop)) if (p != 0) GLES20.glDeleteProgram(p)
        if (tri != 0) GLES20.glDeleteBuffers(2, intArrayOf(tri, qVbo), 0)
        if (fTex != 0) { GLES20.glDeleteTextures(1, intArrayOf(fTex), 0); GLES20.glDeleteFramebuffers(1, intArrayOf(fFbo), 0) }
        pField = 0; pDrop = 0; tri = 0; qVbo = 0; fTex = 0; fFbo = 0; fw = 0; fh = 0
    }

    companion object {
        private const val M = 16
        private const val MAX = 64
        private const val TAU = (Math.PI * 2).toFloat()
        private val QX = floatArrayOf(-1f, 1f, -1f, -1f, 1f, 1f)
        private val QY = floatArrayOf(-1f, -1f, 1f, 1f, -1f, 1f)
        private const val FIELD_VS = """attribute vec4 aPos; uniform vec2 uHalf; varying vec2 vQ;
void main(){ vQ = aPos.zw; gl_Position = vec4(aPos.xy / uHalf, 0.0, 1.0); }"""
        private const val FIELD_FS = """precision mediump float; varying vec2 vQ;
void main(){ float d = dot(vQ, vQ); float f = d < 1.0 ? (1.0 - d) * (1.0 - d) * (1.0 - d) : 0.0; gl_FragColor = vec4(f * 0.25, 0.0, 0.0, 1.0); }"""
        private const val VS = """attribute vec2 aPos; varying vec2 vUv; void main(){ vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }"""
        private const val FS = """precision highp float;
varying vec2 vUv;
uniform sampler2D uField; uniform vec2 uTexel; uniform vec2 uHalf; uniform vec2 uRes;
uniform vec3 uBody;           // centre x, y, base radius
uniform float uRad[16];       // outline radii
uniform float uLiq; uniform float uFaceKind; uniform float uTime;
uniform vec4 uEye;            // look x, y, open, big
uniform vec4 uExpr;           // happy, squish, smile, mouth O
uniform vec2 uPct;            // percent, alpha
uniform float uFloor; uniform float uBass;
uniform vec4 uIcon;          // battery icon: centre x, y, size, alpha
uniform vec2 uSlosh;         // its liquid tilt and wave
float h21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float noise(vec2 p){ vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
  return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y); }
float fbm(vec2 p){ float n = 0.0, a = 0.5; for (int i = 0; i < 4; i++) { n += a * noise(p); p = p * 2.03 + 1.7; a *= 0.5; } return n; }
float bodyR(float a){
  float s = 0.0, w = 0.0;
  for (int i = 0; i < 16; i++) { float d = a - float(i) * 0.3926991; d = d - 6.2831853 * floor(d / 6.2831853 + 0.5); float k = exp(-d * d * 9.0); s += uRad[i] * k; w += k; }
  return s / w;
}
float smin(float a, float b, float k){ float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }
// a seven-segment digit (0..9) in a 1 x 2 box centred at 0
float seg(vec2 p, vec2 a, vec2 b){ vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float digit(vec2 p, float n){
  float d = 9.0; float s0, s1, s2, s3, s4, s5, s6;
  // segments: a top, b top-right, c bottom-right, d bottom, e bottom-left, f top-left, g middle
  s0 = (n == 1.0 || n == 4.0) ? 0.0 : 1.0; s1 = (n == 5.0 || n == 6.0) ? 0.0 : 1.0; s2 = (n == 2.0) ? 0.0 : 1.0;
  s3 = (n == 1.0 || n == 4.0 || n == 7.0) ? 0.0 : 1.0; s4 = (n == 0.0 || n == 2.0 || n == 6.0 || n == 8.0) ? 1.0 : 0.0;
  s5 = (n == 1.0 || n == 2.0 || n == 3.0 || n == 7.0) ? 0.0 : 1.0; s6 = (n == 0.0 || n == 1.0 || n == 7.0) ? 0.0 : 1.0;
  if (s0 > 0.5) d = min(d, seg(p, vec2(-0.4, 0.9), vec2(0.4, 0.9)));
  if (s1 > 0.5) d = min(d, seg(p, vec2(0.5, 0.8), vec2(0.5, 0.1)));
  if (s2 > 0.5) d = min(d, seg(p, vec2(0.5, -0.1), vec2(0.5, -0.8)));
  if (s3 > 0.5) d = min(d, seg(p, vec2(-0.4, -0.9), vec2(0.4, -0.9)));
  if (s4 > 0.5) d = min(d, seg(p, vec2(-0.5, -0.1), vec2(-0.5, -0.8)));
  if (s5 > 0.5) d = min(d, seg(p, vec2(-0.5, 0.8), vec2(-0.5, 0.1)));
  if (s6 > 0.5) d = min(d, seg(p, vec2(-0.4, 0.0), vec2(0.4, 0.0)));
  return d;
}
vec4 liquid(vec2 p, vec2 uv, float mirror){
  float px = 2.0 * uHalf.y / uRes.y;                          // one screen pixel in uv units
  float F = texture2D(uField, uv).r * 4.0;
  vec2 c = uBody.xy; float R = uBody.z; vec2 rel = p - c; float rl = length(rel);
  // the floor shadow under the body
  float shadow = 0.0;
  { float hgt = max(0.0, c.y - R * 0.82 - uFloor); vec2 q = (p - vec2(c.x, uFloor)) / vec2(R * (1.1 + hgt * 1.5), R * 0.16);
    shadow = exp(-dot(q, q) * 2.0) * 0.45 / (1.0 + hgt * 6.0) * step(p.y, uFloor + R * 0.1); }
  if (F < 0.02 && rl > R * 2.0) return vec4(0.0, 0.0, 0.0, shadow * 0.85 * (1.0 - mirror));
  // distance to the liquid: the body outline, melted together with the beads
  float dB = rl > R * 2.2 ? rl - R : rl - bodyR(atan(rel.y, rel.x));
  float fx = (texture2D(uField, uv + vec2(uTexel.x, 0.0)).r - texture2D(uField, uv - vec2(uTexel.x, 0.0)).r) * 4.0;
  float fy = (texture2D(uField, uv + vec2(0.0, uTexel.y)).r - texture2D(uField, uv - vec2(0.0, uTexel.y)).r) * 4.0;
  vec2 gF = vec2(fx, fy) / (2.0 * uTexel * 2.0 * uHalf);      // field gradient per uv unit
  float dF = (0.5 - F) / max(length(gF), 1e-3);
  float k = R * 0.35;
  float h = clamp(0.5 + 0.5 * (dF - dB) / k, 0.0, 1.0);
  float D = mix(dF, dB, h) - k * h * (1.0 - h);
  vec2 g = normalize(mix(-gF, rel / max(rl, 1e-4), h) + 1e-5);
  float a = clamp(0.5 - D / px, 0.0, 1.0);
  int L = int(uLiq + 0.5);
  float glow = 0.0;
  if (L == 2 || L == 5) glow = exp(-max(D, 0.0) / (R * 0.18)) * (L == 2 ? 0.55 + 0.35 * uBass : 0.35);
  if (a <= 0.0 && glow < 0.004) return vec4(0.0, 0.0, 0.0, shadow * 0.85 * (1.0 - mirror));
  // a dome: steep at the rim, flat on top
  float thick = mix(clamp(-dF / 0.05, 0.0, 1.0), clamp(-D / (R * 0.75), 0.0, 1.0), h);
  float s = sqrt(1.0 - (1.0 - thick) * (1.0 - thick));
  vec3 N = normalize(vec3(g * (1.0 - s) * 1.6, 0.35 + s));
  vec3 Ld = normalize(vec3(-0.45, 0.65, 0.6)); vec3 V = vec3(0.0, 0.0, 1.0);
  float diff = max(dot(N, Ld), 0.0);
  float spec = pow(max(dot(reflect(-Ld, N), V), 0.0), 70.0);
  float spec2 = pow(max(dot(reflect(-normalize(vec3(0.5, -0.3, 0.8)), N), V), 0.0), 30.0) * 0.25;
  float fres = pow(1.0 - N.z, 2.5);
  vec2 lq = rel / R;                                             // body space
  vec3 col; float alpha;
  if (L == 0) {          // water: clear, blue at the rim, bright glints, a caustic on the floor side
    col = mix(vec3(0.16, 0.56, 1.0), vec3(0.7, 0.95, 1.0), fres) * (0.75 + 0.35 * fres) + vec3(0.5, 0.85, 1.0) * pow(max(0.0, 1.0 - length(lq - vec2(0.12, -0.5)) * 1.9), 2.0) * 0.5 * h;
    col += vec3(0.2, 0.55, 1.0) * (1.0 - s) * 0.25;
    alpha = 0.62 + 0.35 * fres;
  } else if (L == 1) {   // honey: deep amber, glowing inside
    col = mix(vec3(0.75, 0.35, 0.02), vec3(1.0, 0.72, 0.2), s * 0.8 + 0.2 * diff) + vec3(1.0, 0.85, 0.4) * pow(s, 3.0) * 0.25;
    col *= 0.85 + 0.15 * noise(lq * 6.0 + uTime * 0.2); alpha = 0.92;
  } else if (L == 2) {   // lava: a dark crust that cracks over glowing rock
    float n = fbm(lq * 3.2 + vec2(uTime * 0.15, -uTime * 0.1)); float crust = smoothstep(0.42, 0.62, n);
    vec3 hot = mix(vec3(1.0, 0.25, 0.02), vec3(1.0, 0.75, 0.2), smoothstep(0.2, 0.0, abs(n - 0.4)));
    col = mix(hot * (1.2 + 0.4 * uBass), vec3(0.09, 0.03, 0.02) * (0.6 + diff), crust * s);
    alpha = 1.0;
  } else if (L == 3) {   // slime: green goo with bubbles floating up inside
    col = mix(vec3(0.15, 0.55, 0.08), vec3(0.55, 1.0, 0.35), s * 0.6 + diff * 0.4);
    for (int i = 0; i < 6; i++) { float fi = float(i); vec2 bp = vec2(sin(fi * 2.4) * 0.45, mod(uTime * (0.15 + 0.05 * fi) + fi * 0.37, 1.6) - 0.8);
      float br = 0.06 + 0.04 * fract(fi * 0.7); float bd = length(lq - bp); col += vec3(0.8, 1.0, 0.6) * smoothstep(br, br - 0.02, bd) * smoothstep(br - 0.05, br - 0.02, bd) * 0.6 * h; }
    alpha = 0.82 + 0.15 * fres;
  } else if (L == 4) {   // mercury: liquid chrome mirroring a soft studio sky
    vec3 r = reflect(-V, N); float sky = r.y * 0.5 + 0.5;
    col = mix(vec3(0.08, 0.09, 0.11), vec3(0.95, 0.97, 1.0), smoothstep(0.35, 0.75, sky)) + vec3(0.25, 0.3, 0.4) * smoothstep(0.75, 0.3, abs(r.x));
    col *= 0.7 + 0.3 * diff; alpha = 1.0;
  } else {               // galaxy: a little universe inside the drop
    vec2 sp = lq * 9.0 + vec2(uTime * 0.05, 0.0); vec2 si = floor(sp);
    float st = h21(si); float star = st > 0.9 ? smoothstep(0.12, 0.0, length(fract(sp) - 0.5 - (vec2(h21(si + 3.0), h21(si + 7.0)) - 0.5) * 0.6)) * (0.5 + 0.5 * sin(uTime * 3.0 + st * 40.0)) : 0.0;
    float neb = fbm(lq * 2.0 + uTime * 0.03);
    col = vec3(0.04, 0.01, 0.1) + vec3(0.55, 0.15, 0.85) * smoothstep(0.4, 0.8, neb) * 0.6 + vec3(0.1, 0.6, 0.9) * smoothstep(0.55, 0.85, fbm(lq * 3.0 - 4.0)) * 0.35 + vec3(1.0) * star;
    col += vec3(0.6, 0.4, 1.0) * fres * 0.6; alpha = 0.95;
  }
  col += vec3(1.0) * (spec * (L == 4 ? 0.6 : 1.1) + spec2);
  // reflection of the room: a bright window up and to the left, a soft horizon band; stronger at the rim
  { vec3 rv = reflect(-V, N);
    float win = smoothstep(0.1, 0.0, abs(rv.x + 0.35) - 0.16) * smoothstep(0.1, 0.0, abs(rv.y - 0.45) - 0.12);
    float bar = smoothstep(0.02, 0.0, abs(rv.x + 0.35)) * step(abs(rv.y - 0.45), 0.12);
    float hor = smoothstep(0.25, 0.0, abs(rv.y + 0.05)) * 0.35;
    float refl = (win * (1.0 - bar) * 0.55 + hor * 0.25) * (0.35 + 0.65 * fres) * h;
    col += (L == 2 ? vec3(1.0, 0.8, 0.6) : vec3(0.9, 0.95, 1.0)) * refl * (L == 4 ? 1.4 : 1.0); }
  // the face (body only)
  if (uFaceKind < 2.5 && h > 0.5) {
    float big = uEye.w; vec2 look = uEye.xy * 0.08;
    vec2 fq = lq - vec2(0.0, 0.08) - look * 0.6;
    float er = 0.15 * big; float ink = 0.0; float white = 0.0;
    for (int i = 0; i < 2; i++) {
      float sx = i == 0 ? -1.0 : 1.0; vec2 e = fq - vec2(sx * 0.33, 0.12);
      if (uExpr.x > 0.5) {          // happy: ^ ^
        float d = abs(length(e - vec2(0.0, -0.06)) - 0.11) ; ink = max(ink, smoothstep(0.035, 0.02, d) * step(-0.03, e.y));
      } else if (uExpr.y > 0.5) {   // squished: > <
        vec2 m = vec2(e.x * sx, e.y); float d = min(seg(m, vec2(-0.1, 0.08), vec2(0.08, 0.0)), seg(m, vec2(-0.1, -0.08), vec2(0.08, 0.0)));
        ink = max(ink, smoothstep(0.035, 0.02, d));
      } else {
        float open = max(0.05, uEye.z);
        vec2 ee = e / vec2(1.0, open);
        float w = smoothstep(er, er - 0.02, length(ee));
        white = max(white, w);
        vec2 pp = e - look * 0.5 - uEye.xy * 0.05; float pr = er * 0.62;
        float pup = smoothstep(pr, pr - 0.02, length(pp / vec2(1.0, open))) * w;
        ink = max(ink, pup);
        float hl = smoothstep(0.045, 0.025, length(pp - vec2(-0.04, 0.045))) * pup;
        white = max(white, hl); ink *= 1.0 - hl;
      }
    }
    // mouth
    vec2 mq = fq - vec2(0.0, -0.13);
    float md;
    if (uExpr.w > 0.5) md = abs(length(mq / vec2(1.0, 1.25)) - 0.055) - 0.012;      // "O"
    else { float cv = uExpr.z; float y = cv * (mq.x * mq.x * 6.0 - 0.04); md = max(abs(mq.y - y) - 0.016, abs(mq.x) - 0.11); }
    ink = max(ink, smoothstep(0.012, 0.0, md));
    // blush
    float bl = (smoothstep(0.1, 0.0, length(fq - vec2(-0.5, -0.04))) + smoothstep(0.1, 0.0, length(fq - vec2(0.5, -0.04)))) * (uFaceKind < 0.5 ? 0.35 : 0.0);
    vec3 inkC = L == 4 ? vec3(0.02) : L == 2 ? vec3(0.08, 0.02, 0.0) : vec3(0.02, 0.04, 0.1);
    if (uFaceKind > 0.5 && uFaceKind < 1.5) {   // cool: sunglasses instead of eyes
      vec2 g1 = fq - vec2(-0.3, 0.12), g2 = fq - vec2(0.3, 0.12);
      float gl = min(length(g1 / vec2(1.25, 0.85)) - 0.17, length(g2 / vec2(1.25, 0.85)) - 0.17);
      gl = min(gl, seg(fq, vec2(-0.12, 0.16), vec2(0.12, 0.16)) - 0.02);
      float gm = smoothstep(0.012, 0.0, gl); white *= 0.0; ink = max(ink * step(0.0, gl), gm);
      col = mix(col, vec3(0.9), smoothstep(0.03, 0.0, length(g1 - vec2(-0.06, 0.06))) * 0.0);
      col += vec3(1.0) * smoothstep(0.02, 0.0, abs(g1.x + g1.y * 0.6 + 0.04)) * gm * 0.25;
    }
    col = mix(col, vec3(1.0, 0.98, 0.95), white * 0.95); alpha = max(alpha, white);
    col = mix(col, vec3(1.0, 0.45, 0.55), bl * a);
    col = mix(col, inkC, ink); alpha = max(alpha, ink);
  }
  // the battery percentage, shown when it starts charging
  if (uPct.y > 0.01 && h > 0.5) {
    vec2 q = (lq - vec2(0.0, -0.42)) / 0.13; float n = uPct.x;
    float d1 = digit((q - vec2(-1.3, 0.0)) * vec2(1.0, 1.0), floor(n / 10.0));
    float d2 = digit(q - vec2(0.0, 0.0), mod(n, 10.0));
    float dd = n >= 100.0 ? min(min(digit(q - vec2(-2.6, 0.0), 1.0), digit(q - vec2(-1.3, 0.0), 0.0)), digit(q, 0.0)) : n < 10.0 ? d2 : min(d1, d2);
    float pc = min(min(length(q - vec2(1.0, 0.6)) - 0.15, length(q - vec2(1.8, -0.6)) - 0.15), seg(q, vec2(1.0, -0.8), vec2(1.8, 0.8)) - 0.05);
    float gl2 = smoothstep(0.16, 0.06, min(dd, pc + 0.1));
    col = mix(col, vec3(0.5, 1.0, 0.75), gl2 * uPct.y); alpha = max(alpha, gl2 * uPct.y);
  }
  vec3 gc = L == 2 ? vec3(1.0, 0.35, 0.05) : vec3(0.55, 0.3, 1.0);
  float A = alpha * a;
  vec3 outc = col * A + gc * glow * (1.0 - a);
  return vec4(outc, max(A, shadow * 0.85 * (1.0 - a) * (1.0 - mirror)));
}
// the battery as a little glass of the same liquid, the level = the charge, with the percentage beside it
vec4 batteryIcon(vec2 p){
  if (uIcon.w < 0.01) return vec4(0.0);
  vec2 q = (p - uIcon.xy) / uIcon.z;
  if (abs(q.x - 0.4) > 2.4 || abs(q.y) > 0.8) return vec4(0.0);
  float px = 2.0 * uHalf.y / uRes.y / uIcon.z;
  vec2 b = abs(q - vec2(-0.75, 0.0)) - vec2(0.48, 0.25);
  float box = length(max(b, 0.0)) + min(max(b.x, b.y), 0.0) - 0.07;
  vec2 cb = abs(q - vec2(-0.17, 0.0)) - vec2(0.04, 0.1); float cap = length(max(cb, 0.0)) + min(max(cb.x, cb.y), 0.0) - 0.03;
  float shell = min(abs(box) - 0.035, cap);
  float inside = smoothstep(px, -px, box + 0.06);
  float lvl = uPct.x / 100.0;
  float x01 = (q.x + 1.23) / 0.96;
  float wave = 0.025 * sin(x01 * 9.0 + uTime * 4.0) * (0.4 + uSlosh.y) + uSlosh.x * (x01 - 0.5) * 0.25;
  float fillY = -0.27 + lvl * 0.54 + wave;
  float fill = inside * smoothstep(px, -px, q.y - fillY);
  int L = int(uLiq + 0.5);
  vec3 lc = L == 0 ? vec3(0.25, 0.68, 1.0) : L == 1 ? vec3(1.0, 0.68, 0.15) : L == 2 ? mix(vec3(1.0, 0.3, 0.03), vec3(1.0, 0.75, 0.2), noise(q * 6.0 + uTime)) :
            L == 3 ? vec3(0.45, 1.0, 0.3) : L == 4 ? mix(vec3(0.35, 0.37, 0.42), vec3(0.95), smoothstep(-0.25, 0.2, q.y)) : vec3(0.45, 0.18, 0.85);
  if (L == 5) lc += vec3(1.0) * step(0.93, h21(floor(q * 18.0))) * 0.8;
  lc *= 0.75 + 0.35 * smoothstep(fillY - 0.2, fillY, q.y);
  vec4 c = vec4(lc, 1.0) * fill * (lvl < 0.15 ? 0.7 + 0.3 * sin(uTime * 6.0) : 1.0);
  float sh = smoothstep(px, -px, shell);
  c = vec4(0.92, 0.95, 1.0, 1.0) * sh * 0.85 + c * (1.0 - sh * 0.85);
  c += vec4(1.0) * smoothstep(0.03, 0.0, abs(q.y - 0.16)) * step(abs(q.x + 0.85), 0.25) * inside * 0.35;
  vec2 dq = (q - vec2(0.55, 0.0)) / 0.22; float n = uPct.x;
  float dd = n >= 100.0 ? min(min(digit(dq - vec2(-1.3, 0.0), 1.0), digit(dq, 0.0)), digit(dq - vec2(1.3, 0.0), 0.0))
           : n < 10.0 ? digit(dq + vec2(0.65, 0.0), mod(n, 10.0)) : min(digit(dq, floor(n / 10.0)), digit(dq - vec2(1.3, 0.0), mod(n, 10.0)));
  vec2 pq = dq - vec2(n >= 100.0 ? 2.9 : n < 10.0 ? 1.05 : 2.7, 0.0);
  float pc = min(min(length(pq - vec2(-0.3, 0.55)) - 0.16, length(pq - vec2(0.3, -0.55)) - 0.16), seg(pq, vec2(-0.35, -0.8), vec2(0.35, 0.8)) - 0.02);
  dd = min(dd, pc + 0.12);
  float num = smoothstep(0.17 + px / 0.22, 0.17 - px / 0.22, dd);
  c = vec4(mix(lc, vec3(1.0), 0.45), 1.0) * num + c * (1.0 - num);
  return c * uIcon.w;
}

void main(){
  vec2 p = (vUv - 0.5) * 2.0 * uHalf;                         // uv units, y up
  vec4 c = liquid(p, vUv, 0.0);
  // a glossy floor: under the floor line the drop is mirrored, faint and fading with depth (one more pass of the
  // same function, only for the pixels below the floor that the drop does not cover)
  if (p.y < uFloor && c.a < 0.99) {
    vec2 m = vec2(p.x, 2.0 * uFloor - p.y);
    vec4 r = liquid(m, m / (2.0 * uHalf) + 0.5, 1.0);
    float k = 0.32 * exp(-(uFloor - p.y) / (uBody.z * 0.9));
    c += r * k * (1.0 - c.a);
  }
  vec4 ic = batteryIcon(p);
  c = ic + c * (1.0 - ic.a);
  gl_FragColor = c;
}"""
    }
}
