package app.pulsify.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** One kind of fish in the aquarium. Sprites are Flow-painted pictures, nose to the right. */
data class AquaSpecies(
    val key: String, val name: String, val sub: String,
    val len: Float,            // body length in dp at full size
    val speed: Float, val wave: Float, val school: Int = 0, val curious: Float = .5f,
    val upright: Boolean = false, val jelly: Boolean = false, val bottom: Boolean = false, val puff: Boolean = false,
)

val AQUA_SPECIES = listOf(
    AquaSpecies("clown", "Clownfish", "reef · playful", 62f, .5f, .12f, curious = .8f),
    AquaSpecies("betta", "Betta", "flowing veil", 70f, .32f, .15f, curious = 1f),
    AquaSpecies("angel", "Angelfish", "tall · graceful", 66f, .3f, .07f, curious = .6f),
    AquaSpecies("tetra", "Neon Tetra", "school of 8", 26f, .75f, .1f, school = 8, curious = .3f),
    AquaSpecies("gold", "Fancy Goldfish", "double tail", 64f, .3f, .14f, curious = .9f),
    AquaSpecies("guppy", "Guppy", "rainbow tail", 32f, .7f, .15f, curious = .4f),
    AquaSpecies("puffer", "Pufferfish", "tap it: it puffs", 54f, .25f, .08f, curious = .8f, puff = true),
    AquaSpecies("discus", "Discus", "king of the tank", 66f, .28f, .06f, curious = .5f),
    AquaSpecies("ytang", "Yellow Tang", "sunny · reef", 64f, .55f, .08f, curious = .4f),
    AquaSpecies("mandarin", "Mandarin", "psychedelic", 48f, .18f, .1f, curious = .4f),
    AquaSpecies("koi", "Koi", "big · calm", 92f, .3f, .1f, curious = .7f),
    AquaSpecies("seahorse", "Seahorse", "hovers upright", 36f, .12f, 0f, curious = .4f, upright = true),
    AquaSpecies("jelly", "Moon Jelly", "glows", 56f, .1f, 0f, curious = 0f, jelly = true),
    AquaSpecies("shrimp", "Cherry Shrimp", "walks the sand", 26f, .1f, 0f, curious = .2f, bottom = true),
)
val AQUA_TANKS = listOf("Planted", "Coral Reef", "Glow Night", "Blackwater", "Zen Garden", "Abyss")
val AQUA_PRESETS = listOf(
    "Community" to mapOf("tetra" to 8, "guppy" to 4, "angel" to 2, "shrimp" to 3, "puffer" to 1, "discus" to 1),
    "Reef" to mapOf("clown" to 2, "ytang" to 2, "mandarin" to 1, "seahorse" to 1, "shrimp" to 2),
    "Betta solo" to mapOf("betta" to 1, "shrimp" to 2),
    "Goldfish & koi" to mapOf("gold" to 3, "koi" to 2),
    "Night jellies" to mapOf("jelly" to 5),
)

/** What's in the tank. Saved in its own preferences; the scene rebuilds the fish when [version] changes. */
object AquariumConfig {
    @Volatile var version = 0; private set
    val counts = IntArray(AQUA_SPECIES.size)
    @Volatile var tank = 0; private set
    private var loaded = false
    fun load(ctx: Context) {
        if (loaded) return; loaded = true
        val p = ctx.getSharedPreferences("aquarium", Context.MODE_PRIVATE)
        val s = p.getString("fish", null)
        if (s == null) setPreset(ctx, 0, save = false)
        else for (part in s.split(",")) { val kv = part.split(":"); val i = AQUA_SPECIES.indexOfFirst { it.key == kv[0] }; if (i >= 0 && kv.size == 2) counts[i] = kv[1].toIntOrNull() ?: 0 }
        tank = p.getInt("tank", 0); version++
    }
    private fun save(ctx: Context) {
        ctx.getSharedPreferences("aquarium", Context.MODE_PRIVATE).edit()
            .putString("fish", AQUA_SPECIES.indices.joinToString(",") { "${AQUA_SPECIES[it].key}:${counts[it]}" }).putInt("tank", tank).apply()
        version++
    }
    fun change(ctx: Context, i: Int, delta: Int) {
        val sp = AQUA_SPECIES[i]; val step = if (sp.school > 0) 4 else 1
        val total = counts.sum()
        val n = if (delta > 0 && counts[i] == 0 && sp.school > 0) sp.school else counts[i] + delta * step
        if (delta > 0 && total + (n - counts[i]) > 40) return
        counts[i] = n.coerceIn(0, 16); save(ctx)
    }
    fun setPreset(ctx: Context, p: Int, save: Boolean = true) {
        counts.fill(0); for ((k, n) in AQUA_PRESETS[p].second) { val i = AQUA_SPECIES.indexOfFirst { it.key == k }; if (i >= 0) counts[i] = n }
        if (p == 1) tank = 1; if (p == 4) tank = 2
        if (save) save(ctx) else version++
    }
    fun setTank(ctx: Context, t: Int) { tank = t; save(ctx) }
}

/**
 * A home aquarium: many kinds of 2D fish (Flow-painted sprites) in a Flow-painted tank.
 * Each fish is drawn as a strip mesh bent by a travelling wave, so the body and tail really swim.
 *  - Touch the water: every fish heads for your finger on its own wobbly path and mills around it.
 *  - Tap the water: food sinks, they race for it.  Tap a fish: it darts off (the puffer puffs up).
 *  - Music: they swim faster, bubbles rise faster.
 */
class AquariumScene(private val context: Context) : Creature {
    override var listener: SceneListener? = null
    override var bottomInset = 0f
    override val busy: Boolean get() = true

    private class Fish(val sp: AquaSpecies, val si: Int) {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var z = 1f; var face = 1f; var faceS = 1f; var ph = 0f
        var tx = 0f; var ty = 0f; var nextT = 0f; var dart = 0f; var puff = 0f; var puffT = 0f; var fed = 0f; var size = 1f
        var offX = 0f; var offY = 0f; var offT = 0f; var delay = 0f; var lead: Fish? = null
    }
    private val fish = ArrayList<Fish>()
    private class Dot(var x: Float, var y: Float, var vy: Float, var r: Float, var life: Float, val kind: Int)
    private val dots = ArrayList<Dot>()
    private var w = 1; private var h = 1; private var dp = 1f
    /** Which tank to paint (index into [AQUA_TANKS]), or -1 when the background comes from elsewhere. */
    @Volatile var tankWorld = 0
    private var time = 0f; private var bass = 0f; private var built = -1; private var tankLoaded = -1
    private var touching = false; private var tX = 0f; private var tY = 0f; private var t0 = 0f; private var tvx = 0f; private var lastTx = 0f

    // GL
    private var dotAttr = 1; private var spriteProg = 0; private var bgProg = 0; private var dotProg = 0
    private var stripVbo = 0; private var quadVbo = 0; private var dotVbo = 0
    private var bgTex = 0; private var bgW = 1; private var bgH = 1
    private val tex = IntArray(AQUA_SPECIES.size); private val texAspect = FloatArray(AQUA_SPECIES.size) { 1f }
    private val su = HashMap<String, Int>(); private val bu = HashMap<String, Int>(); private val du = HashMap<String, Int>()
    private val dotData = FloatArray(MAX_DOTS * 6 * 5); private val dotBuf: FloatBuffer = ByteBuffer.allocateDirect(MAX_DOTS * 6 * 5 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    override fun onSurfaceCreated() {
        AquariumConfig.load(context)
        spriteProg = Gl.program(SPRITE_VS, SPRITE_FS); bgProg = Gl.program(BG_VS, BG_FS); dotProg = Gl.program(DOT_VS, DOT_FS)
        for (n in listOf("uRes", "uC", "uS", "uFace", "uTilt", "uPh", "uAmp", "uTex", "uDim", "uWater", "uAlpha", "uJelly")) su[n] = GLES20.glGetUniformLocation(spriteProg, n)
        for (n in listOf("uTex", "uCover", "uTime", "uBass")) bu[n] = GLES20.glGetUniformLocation(bgProg, n)
        for (n in listOf("uRes")) du[n] = GLES20.glGetUniformLocation(dotProg, n)
        dotAttr = GLES20.glGetAttribLocation(dotProg, "aDot")
        val ids = IntArray(3); GLES20.glGenBuffers(3, ids, 0); stripVbo = ids[0]; quadVbo = ids[1]; dotVbo = ids[2]
        val strip = FloatArray((STRIPS + 1) * 4); for (i in 0..STRIPS) { val u = i / STRIPS.toFloat(); strip[i * 4] = u; strip[i * 4 + 1] = 0f; strip[i * 4 + 2] = u; strip[i * 4 + 3] = 1f }
        upload(stripVbo, strip); upload(quadVbo, floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f))
        tex.fill(0); tankLoaded = -1; built = -1
    }
    private fun upload(vbo: Int, a: FloatArray) {
        val fb = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(a); fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo); GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, a.size * 4, fb, GLES20.GL_STATIC_DRAW)
    }
    private fun loadTex(path: String, mip: Boolean, aspectOut: ((Float) -> Unit)? = null): Int {
        val bmp = try { context.assets.open(path).use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null } ?: return 0
        aspectOut?.invoke(bmp.width / bmp.height.toFloat())
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, if (mip) GLES20.GL_LINEAR_MIPMAP_LINEAR else GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0); if (mip) GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        bmp.recycle(); return t[0]
    }
    override fun onSurfaceChanged(width: Int, height: Int) { w = max(1, width); h = max(1, height); dp = min(w, h) / 390f; built = -1 }
    override fun release() {
        for (p in listOf(spriteProg, bgProg, dotProg)) if (p != 0) GLES20.glDeleteProgram(p)
        GLES20.glDeleteBuffers(3, intArrayOf(stripVbo, quadVbo, dotVbo), 0)
        val all = tex.filter { it != 0 }.toMutableList(); if (bgTex != 0) all += bgTex
        if (all.isNotEmpty()) GLES20.glDeleteTextures(all.size, all.toIntArray(), 0)
        tex.fill(0); bgTex = 0; spriteProg = 0; bgProg = 0; dotProg = 0; tankLoaded = -1; built = -1
    }

    // ---------------------------------------------------------------- tank contents
    private val x0 get() = 20f * dp; private val x1 get() = w - 20f * dp
    private val y0 get() = h * .12f; private val y1 get() = h * .82f
    private fun rr(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
    private fun scale(f: Fish) = (.55f + .5f * f.z) * dp * 1.25f
    private fun build() {
        built = AquariumConfig.version; fish.clear()
        for ((i, sp) in AQUA_SPECIES.withIndex()) {
            val n = AquariumConfig.counts[i]; var lead: Fish? = null; val zLead = rr(.2f, 1f)
            for (k in 0 until n) {
                val f = Fish(sp, i); f.z = if (sp.school > 0) (zLead + rr(-.12f, .12f)).coerceIn(.1f, 1f) else rr(.15f, 1f)
                f.x = rr(x0 + 40 * dp, x1 - 40 * dp); f.y = if (sp.bottom) y1 + 10 * dp else rr(y0 + 30 * dp, y1 - 40 * dp)
                f.vx = rr(-20f, 20f) * dp; f.face = if (f.vx < 0) -1f else 1f; f.faceS = f.face; f.ph = rr(0f, 6.28f); f.size = rr(.8f, 1.15f)
                if (sp.school > 0) { if (lead == null) lead = f else f.lead = lead }
                newTarget(f); fish += f
            }
        }
        fish.sortBy { it.z }
    }
    private fun newTarget(f: Fish) {
        val sp = f.sp; f.tx = rr(x0 + 30 * dp, x1 - 30 * dp)
        f.ty = when { sp.bottom -> y1 + 10 * dp; sp.jelly -> rr(y0, y1 - 60 * dp); else -> rr(y0 + 20 * dp, y1 - 30 * dp) }
        f.nextT = time + rr(3f, 8f) / max(.3f, sp.speed * 2f)
    }

    // ---------------------------------------------------------------- touch (normalised 0..1, y down)
    override fun touchDown(nx: Float, ny: Float): Boolean { touching = true; tX = nx * w; tY = ny * h; t0 = time; lastTx = tX; tvx = 0f; return true }
    override fun touchMove(nx: Float, ny: Float): Boolean { val x = nx * w; tvx = tvx * .6f + (x - lastTx) * 60f * .4f; lastTx = x; tX = x; tY = ny * h; return true }
    override fun touchUp(): Boolean { touching = false; return true }
    override fun tapAt(nx: Float, ny: Float): Boolean {
        val x = nx * w; val y = ny * h
        val f = fish.lastOrNull { hypot(it.x - x, it.y - y) < it.sp.len * scale(it) * it.size * .5f }
        if (f != null) {
            if (f.sp.puff) f.puffT = time + 2.5f
            val s = sign(f.x - x).let { if (it == 0f) 1f else it }
            f.dart = .6f; f.vx = s * 220f * dp; f.tx = (f.x + s * 160f * dp).coerceIn(x0 + 30 * dp, x1 - 30 * dp); f.ty = (f.y + rr(-60f, 60f) * dp).coerceIn(y0, y1); f.nextT = time + 2f
            listener?.onDiscovery("aquarium poke")
        } else {
            for (i in 0 until 5) dots += Dot(x + rr(-14f, 14f) * dp, min(y, h * .5f) + rr(-8f, 8f) * dp, rr(5f, 20f) * dp, 2.6f * dp, 8f, 1)
            dots += Dot(x, y, 0f, 4f * dp, .8f, 2); listener?.onDiscovery("aquarium feed")
        }
        return true
    }
    override fun longPressAt(nx: Float, ny: Float): Boolean = true
    override fun greet() {}
    override fun perform(trick: String): Boolean {
        when (trick) {
            "feed" -> { tapAt(rr(.2f, .8f), .3f); return true }
            "bubbles" -> { for (i in 0 until 20) dots += Dot(rr(0f, w.toFloat()), y1 + rr(0f, 30f) * dp, rr(40f, 80f) * dp, rr(2f, 5f) * dp, 99f, 0); return true }
            "puff" -> { fish.filter { it.sp.puff }.forEach { it.puffT = time + 2.5f }; return true }
        }
        return false
    }

    // ---------------------------------------------------------------- update
    override fun update(dt: Float, input: SceneInput) {
        time += dt
        if (built != AquariumConfig.version) build()
        bass += ((if (input.audio.active) input.audio.bass else 0f) - bass) * min(1f, dt * 12f)
        val nf = fish.size
        for ((k, f) in fish.withIndex()) {
            val sp = f.sp; val s = scale(f); val maxV = (40f + 90f * sp.speed) * s * (1f + bass * .8f) * (if (f.dart > 0f) 3f else 1f)
            f.dart = max(0f, f.dart - dt)
            f.puff += ((if (f.puffT > time) 1f else 0f) - f.puff) * min(1f, dt * (if (f.puffT > time) 8f else 1.5f))
            var tx = f.tx; var ty = f.ty; var want = maxV * .55f
            if (time > f.nextT) newTarget(f)
            f.lead?.let { L -> tx = L.x - L.face * (18f + (k % 4) * 12f) * s; ty = L.y + ((k % 3) - 1) * 14f * s + sin(time + k) * 6f * dp; want = maxV * .9f }
            // food
            var near: Dot? = null; var nd = 1e9f
            if (!sp.jelly && !sp.upright && f.fed < time) for (d in dots) if (d.kind == 1) { val dd = hypot(d.x - f.x, d.y - f.y); if (dd < nd && dd < 260f * dp) { nd = dd; near = d } }
            near?.let { tx = it.x; ty = it.y; want = maxV; if (nd < 10f * s + 4f) { it.life = -1f; f.fed = time + rr(.8f, 2f); dots += Dot(it.x, it.y, 0f, 3f * dp, .6f, 2) } }
            // the finger: everyone comes, each to its own spot around it, on its own wobbly path
            if (touching && !sp.jelly && !sp.bottom) {
                if (time > f.offT) { val a = rr(0f, 6.283f); val r = rr(18f, 70f) * s * (if (sp.school > 0) .7f else 1f); f.offX = cos(a) * r; f.offY = sin(a) * r * .7f; f.offT = time + rr(.5f, 1.4f); if (f.delay == 0f) f.delay = rr(.01f, .5f) }
                if (time - t0 > f.delay) { tx = tX + f.offX + sin(time * 2.3f + f.ph) * 12f * s; ty = tY + f.offY + cos(time * 1.9f + f.ph) * 9f * s; want = maxV * rr(.75f, 1.05f) }
                if (abs(tvx) > 900f * dp && hypot(tX - f.x, tY - f.y) < 90f * dp) { tx = f.x * 2 - tX; ty = f.y * 2 - tY; want = maxV * 1.6f }
            } else f.delay = 0f
            if (sp.bottom) ty = y1 + 10f * dp
            val dx = tx - f.x; val dy = ty - f.y; val d = hypot(dx, dy) + 1e-3f; val sl = (d / (60f * dp)).coerceIn(0f, 1f)
            val dvx = dx / d * want * sl; val dvy = dy / d * want * sl * (if (sp.upright) 1f else .6f); val kk = min(1f, dt * (if (f.dart > 0f) 6f else 1.6f))
            f.vx += (dvx - f.vx) * kk; f.vy += (dvy - f.vy) * kk
            if (sp.jelly) { val pulse = max(0f, sin(time * 1.6f + f.ph)) * (1f + bass * 2f); f.vy += (-18f * pulse * s + 6f * s - f.vy) * min(1f, dt * 2f) }
            if (sp.upright) f.vy += sin(time * 1.4f + f.ph) * 6f * dp * dt
            f.x += f.vx * dt; f.y += f.vy * dt
            val m = 20f * s
            if (f.x < x0 + m) { f.x = x0 + m; f.vx = abs(f.vx) }; if (f.x > x1 - m) { f.x = x1 - m; f.vx = -abs(f.vx) }
            val yb = y1 + (if (sp.bottom) 14f * dp else 0f)
            if (f.y < y0) { f.y = y0; f.vy = abs(f.vy) }; if (f.y > yb) { f.y = yb; f.vy = -abs(f.vy) * .3f }
            if (abs(f.vx) > 6f * dp && !sp.jelly) f.face = sign(f.vx); f.faceS += (f.face - f.faceS) * min(1f, dt * 7f)
            f.ph += dt * (2f + hypot(f.vx, f.vy) / (12f * s)) * (1f + bass)
        }
        // food sinks, bubbles rise, ripples fade
        for (d in dots) when (d.kind) {
            1 -> { d.vy += (22f * dp - d.vy) * dt; if (d.y < h * .84f) d.y += d.vy * dt else d.life -= dt; d.x += sin(time * 2f + d.r) * 6f * dp * dt }
            0 -> { d.y -= d.vy * dt * (1f + bass); d.x += sin(time * 3f + d.r * 3f) * 10f * dp * dt; if (d.y < h * .08f) d.life = -1f }
            else -> d.life -= dt
        }
        dots.removeAll { it.life < 0f }
        if (Random.nextFloat() < dt * 3f) dots += Dot(w * .12f + rr(-6f, 6f) * dp, h * .84f, rr(40f, 70f) * dp, rr(2f, 5f) * dp, 99f, 0)
        if (nf > 0 && Random.nextFloat() < dt * .08f * nf) { val f = fish[Random.nextInt(nf)]; if (!f.sp.jelly) dots += Dot(f.x + f.face * 10f * dp, f.y, rr(25f, 45f) * dp, rr(1.5f, 3f) * dp, 99f, 0) }
        while (dots.size > MAX_DOTS) dots.removeAt(0)
    }

    // ---------------------------------------------------------------- draw
    override fun draw(input: SceneInput) {
        // the tank is the World picked in the Studio; a plain colour or the user's photo is already drawn behind
        val own = tankWorld >= 0
        val tank = if (own) tankWorld.coerceIn(0, AQUA_TANKS.size - 1) else 0
        if (own && tankLoaded != tank) { if (bgTex != 0) GLES20.glDeleteTextures(1, intArrayOf(bgTex), 0); bgTex = loadTex("aquarium/bg_$tank.jpg", false) { a -> bgW = (a * 1000).toInt(); bgH = 1000 }; tankLoaded = tank }
        val vp = IntArray(4); GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0); GLES20.glViewport(0, 0, w, h)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        // tank picture (cover fit) with a soft moving shimmer
        if (own) {
        GLES20.glUseProgram(bgProg); GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTex); GLES20.glUniform1i(bu["uTex"]!!, 0)
        val sa = w / h.toFloat(); val ia = bgW / bgH.toFloat(); if (sa < ia) GLES20.glUniform4f(bu["uCover"]!!, sa / ia, 1f, (1f - sa / ia) / 2f, 0f) else GLES20.glUniform4f(bu["uCover"]!!, 1f, ia / sa, 0f, (1f - ia / sa) / 2f)
        GLES20.glUniform1f(bu["uTime"]!!, time % 1000f); GLES20.glUniform1f(bu["uBass"]!!, bass)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo); GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        }
        // fish, far ones first
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(spriteProg); GLES20.glUniform2f(su["uRes"]!!, w.toFloat(), h.toFloat()); GLES20.glUniform1i(su["uTex"]!!, 0)
        val water = if (own) WATER[tank.coerceIn(0, WATER.size - 1)] else NO_WATER; GLES20.glUniform3f(su["uWater"]!!, water[0], water[1], water[2])
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, stripVbo); GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        for (f in fish) {
            val i = f.si
            if (tex[i] == 0) tex[i] = loadTex("aquarium/fish_${f.sp.key}.png", true) { a -> texAspect[i] = a }.let { if (it == 0) -1 else it }
            if (tex[i] <= 0) continue
            val sp = f.sp; val s = scale(f); var len = sp.len * s * f.size; val ht = len / texAspect[i]
            val pf = if (sp.puff) 1f + .5f * f.puff else 1f
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[i])
            GLES20.glUniform2f(su["uC"]!!, f.x, f.y); GLES20.glUniform2f(su["uS"]!!, len * pf, ht * pf * (if (sp.puff) 1f + .3f * f.puff else 1f))
            val fs = f.faceS; GLES20.glUniform1f(su["uFace"]!!, sign(fs).let { if (it == 0f) 1f else it } * max(.12f, abs(fs)))
            val tilt = if (sp.upright || sp.jelly) sin(time * 1.2f + f.ph) * .08f else (atan2(f.vy, abs(f.vx) + 20f * dp) * .7f).coerceIn(-.5f, .5f)
            GLES20.glUniform1f(su["uTilt"]!!, tilt)
            GLES20.glUniform1f(su["uPh"]!!, f.ph * 2.2f)
            val amp = if (sp.wave > 0f) sp.wave * ht * 1.4f * (1f + min(1.5f, hypot(f.vx, f.vy) / (60f * s))) else 0f
            GLES20.glUniform1f(su["uAmp"]!!, amp)
            GLES20.glUniform1f(su["uJelly"]!!, if (sp.jelly) max(0f, sin(time * 1.6f + f.ph)) else -1f)
            GLES20.glUniform1f(su["uDim"]!!, (1f - f.z) * .45f); GLES20.glUniform1f(su["uAlpha"]!!, 1f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, (STRIPS + 1) * 2)
        }
        // food, bubbles and ripples in one batch
        var n = 0
        for (d in dots) { if (n >= MAX_DOTS) break
            val k = when (d.kind) { 1 -> 1f; 0 -> 0f; else -> 2f + (1f - (d.life / .8f).coerceIn(0f, 1f)) }
            val r = if (d.kind == 2) d.r * (1f + 5f * (1f - d.life / .8f)) else d.r
            val c = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f)
            for (v in 0 until 6) { val o = (n * 6 + v) * 5; dotData[o] = d.x + c[v * 2] * r * 1.4f; dotData[o + 1] = d.y + c[v * 2 + 1] * r * 1.4f; dotData[o + 2] = c[v * 2] * 1.4f; dotData[o + 3] = c[v * 2 + 1] * 1.4f; dotData[o + 4] = k }
            n++ }
        if (n > 0) {
            GLES20.glUseProgram(dotProg); GLES20.glUniform2f(du["uRes"]!!, w.toFloat(), h.toFloat())
            dotBuf.position(0); dotBuf.put(dotData, 0, n * 30); dotBuf.position(0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dotVbo); GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, n * 30 * 4, dotBuf, GLES20.GL_DYNAMIC_DRAW)
            GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 20, 0)
            GLES20.glEnableVertexAttribArray(dotAttr); GLES20.glVertexAttribPointer(dotAttr, 3, GLES20.GL_FLOAT, false, 20, 8)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, n * 6); GLES20.glDisableVertexAttribArray(dotAttr)
        }
        GLES20.glDisable(GLES20.GL_BLEND); GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glViewport(vp[0], vp[1], vp[2], vp[3])
    }

    companion object {
        private const val STRIPS = 20
        private const val MAX_DOTS = 120
        private val WATER = arrayOf(floatArrayOf(.03f, .2f, .18f), floatArrayOf(.02f, .12f, .28f), floatArrayOf(0f, 0f, .02f), floatArrayOf(.2f, .12f, .03f), floatArrayOf(.25f, .32f, .3f), floatArrayOf(0f, .01f, .04f))
        /** No tank picture behind (plain colour or the user's photo): no water tint on the fish. */
        private val NO_WATER = floatArrayOf(0f, 0f, 0f)
        private const val BG_VS = """
attribute vec2 aPos; varying vec2 vUv; uniform vec4 uCover;
void main(){ vec2 uv = aPos * 0.5 + 0.5; vUv = vec2(uCover.z + uv.x * uCover.x, 1.0 - (uCover.w + uv.y * uCover.y)); gl_Position = vec4(aPos, 0.0, 1.0); }"""
        private const val BG_FS = """
precision mediump float; varying vec2 vUv; uniform sampler2D uTex; uniform float uTime; uniform float uBass;
void main(){
  vec2 uv = vUv + vec2(sin(vUv.y * 24.0 + uTime * 1.3), cos(vUv.x * 20.0 + uTime * 1.1)) * 0.0012;
  vec3 c = texture2D(uTex, uv).rgb;
  float sh = sin(vUv.x * 38.0 + uTime * 0.9 + sin(vUv.y * 17.0 + uTime * 0.6) * 2.0) * sin(vUv.y * 29.0 - uTime * 0.7);
  c += c * (0.05 * sh + 0.25 * uBass * (1.0 - vUv.y));
  gl_FragColor = vec4(c, 1.0); }"""
        // strip mesh: x = u along the sprite (0 tail .. 1 nose), y = v; bent by a travelling wave that grows toward the tail
        private const val SPRITE_VS = """
attribute vec2 aPos; uniform vec2 uRes; uniform vec2 uC; uniform vec2 uS; uniform float uFace; uniform float uTilt; uniform float uPh; uniform float uAmp; uniform float uJelly;
varying vec2 vUv;
void main(){
  float xn = aPos.x * 2.0 - 1.0;
  vec2 p = vec2(xn * 0.5 * uS.x, (aPos.y - 0.5) * uS.y);
  float wgt = clamp((0.6 - xn) / 1.6, 0.0, 1.0); wgt *= wgt;
  p.y += uAmp * wgt * sin(uPh - xn * 3.0) * 0.35;
  if (uJelly >= 0.0) { float top = aPos.y; p.x *= 1.0 + 0.12 * (1.0 - uJelly) * top; p.y *= 1.0 - 0.08 * (1.0 - uJelly); p.x += sin(uPh + (1.0 - top) * 6.0) * uS.x * 0.04 * (1.0 - top); }
  p.x *= uFace;
  float c = cos(uTilt * sign(uFace)), s = sin(uTilt * sign(uFace)); p = vec2(c * p.x - s * p.y, s * p.x + c * p.y);
  vec2 sp = uC + vec2(p.x, -p.y);
  vUv = vec2(aPos.x, 1.0 - aPos.y);
  gl_Position = vec4(sp.x / uRes.x * 2.0 - 1.0, 1.0 - sp.y / uRes.y * 2.0, 0.0, 1.0); }"""
        private const val SPRITE_FS = """
precision mediump float; varying vec2 vUv; uniform sampler2D uTex; uniform float uDim; uniform vec3 uWater; uniform float uAlpha;
void main(){ vec4 t = texture2D(uTex, vUv); t.rgb = mix(t.rgb, uWater * t.a, uDim); gl_FragColor = t * uAlpha; }"""
        // dots: 0 bubble ring, 1 food pellet, 2.. ripple ring (fraction = age)
        private const val DOT_VS = """
attribute vec2 aPos; attribute vec3 aDot; uniform vec2 uRes; varying vec3 vD;
void main(){ vD = aDot; gl_Position = vec4(aPos.x / uRes.x * 2.0 - 1.0, 1.0 - aPos.y / uRes.y * 2.0, 0.0, 1.0); }"""
        private const val DOT_FS = """
precision mediump float; varying vec3 vD;
void main(){ float r = length(vD.xy); vec4 c;
  if (vD.z < 0.5) { float ring = smoothstep(0.75, 0.9, r) * (1.0 - smoothstep(0.95, 1.05, r)); float hl = smoothstep(0.25, 0.1, length(vD.xy - vec2(-0.35, 0.35))); c = vec4(vec3(0.85, 1.0, 1.0) * (ring * 0.6 + hl * 0.5), ring * 0.6 + hl * 0.5); }
  else if (vD.z < 1.5) { float a = 1.0 - smoothstep(0.8, 1.0, r); c = vec4(vec3(0.78, 0.45, 0.16) * a, a); }
  else { float age = vD.z - 2.0; float ring = smoothstep(0.7, 0.85, r) * (1.0 - smoothstep(0.9, 1.0, r)) * (1.0 - age) * 0.6; c = vec4(vec3(ring), ring); }
  gl_FragColor = c; }"""
    }
}
