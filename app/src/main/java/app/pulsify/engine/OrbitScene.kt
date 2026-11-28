package app.pulsify.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/** One home-screen orbit creature: which model, which clips, which world. */
data class OrbitSpecies(
    val model: String, val orb: Int, val lookUp: FloatArray, val life: List<String>, val taps: List<String>, val tumble: String,
    val dances: List<String>, val env: Int,
)
val ORBIT_REAPER = OrbitSpecies("reaper2", 0, floatArrayOf(-.1f, -.16f), listOf("SpellCast", "Slash", "Kick"), listOf("SpellCast", "Slash", "Kick"), "Tumble",
    listOf("DanceHipHop", "DanceHipHop2", "DanceCardio", "DancePop", "DanceNight"), 0)
val ORBIT_IMP = OrbitSpecies("imp", 1, floatArrayOf(-.03f, -.05f), listOf("Scheme", "EvilLaugh", "Wave", "Point", "SpellCast", "Hop"), listOf("Boo", "EvilLaugh", "Point", "Hop", "Scheme"), "Tumble",
    listOf("DanceBounce", "DanceWave", "DanceShimmy", "DanceFloss"), 2)
val ORBIT_ENVS = listOf("Volcano", "Moonlit graveyard", "Crystal cavern", "Frozen soul lake", "Candy meadow")

/**
 * A 3D creature that lives behind the home-screen icons and is seen from a camera that orbits it 45°
 * per page swipe: eyes follow the camera at once, the body turns to face you about a second after the
 * swipe settles. A swipe startles it: it searches the wrong way first, finds you, then turns.
 * Notifications: it walks up to the glass, knocks and tells you. Charging: it conjures a glowing orb held
 * in both hands that grows with the battery. Dances only while music plays. The world is drawn in code.
 */
class OrbitScene(private val context: Context) : Creature {
    override var listener: SceneListener? = null
    override var bottomInset = 0f
    override val busy: Boolean get() = cur != null && cur!!.name != "Idle"

    var species: OrbitSpecies = ORBIT_REAPER
    private val pet = Pet3D(context)
    private var meta: JSONObject? = null
    private var loadedSp: OrbitSpecies? = null

    private var w = 1; private var h = 1; private var halfW = .5f; private var halfH = 1f
    private var time = 0f
    private var cur: PetClip? = null; private var curT = 0f; private var from: PetClip? = null; private var fromT = 0f; private var fade = 1f
    private val matA = FloatArray(Pet3D.MAX_BONES * 12); private val matB = FloatArray(Pet3D.MAX_BONES * 12)
    private val ctl = FloatArray(12); private val ctlB = FloatArray(12)

    // camera orbit: [off] in pages; the launcher's page position when the wallpaper knows it, else finger drags
    private var off = 0f; private var offV = 0f; private var page = 0f; private var lastSwipe = -9f; private var lastPagePos = Float.NaN
    private var drag = false; private var d0u = 0f; private var off0 = 0f; private var lastT = 0f; private var vel = 0f; private var moved = 0f
    private var camYaw = 0f; private var bodyYaw = 0f; private var bodyV = 0f; private var headYaw = 0f; private var headV = 0f; private var eyeX = 0f
    private var px = 0f; private var pz = 0f; private var camX = 0f; private var camZ = 0f; private var walkTo: FloatArray? = null
    private var seek = "look"; private var seekT = 0f; private var scanDir = 1f; private var hop = 0f; private var pupil = 1f
    private var nextLife = 6f; private val taps = ArrayList<Float>()
    private var near = 0f; private var shake = 0f
    private var env = 0
    /** The world picked in the Studio (index into [ORBIT_ENVS]), or -1: a plain colour or the user's photo is drawn behind instead. */
    @Volatile var worldIndex = 0
    private var seenWorld = -2
    private var jaw = 0f; private var jawT = -9f; private var snicker = 3f
    private var hold = 0f; private var ball = 0f; private var battery = .5f; private var charging = false
    private val ballPos = FloatArray(3); private var hasBall = false
    private var blinkT = 0f; private var nextBlink = 2f

    private class Knock(val t0: Float, val msg: String) { var phase = 0; var t1 = 0f; var t2 = 0f; var arm = 0f; var tap = 0f; val done = BooleanArray(5) }
    private var knock: Knock? = null
    private val rings = ArrayList<Float>()
    private val KNOCKS = floatArrayOf(.45f, .7f, .95f, 1.5f, 1.75f)

    // GL
    private var bgProg = 0; private var fireProg = 0; private var tri = 0; private var quad = 0
    private val bu = HashMap<String, Int>(); private val fu = HashMap<String, Int>()
    private var bubbleTex = 0; private var bubbleAspect = 4f; private var bubbleMsg = ""

    private fun txt(p: String) = context.assets.open(p).bufferedReader().use { it.readText() }
    private fun size0() = min(.7f * halfH, halfW * 1.25f)
    private fun size() = size0() * (1f + .85f * sm(near))
    private fun floorY() = -halfH * (.52f + .2f * sm(near))
    private fun sm(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3 - 2 * c) }
    private fun rnd() = Random.nextFloat()

    override fun onSurfaceCreated() {
        pet.create()
        bgProg = Gl.program(FULL_VS, txt("shaders/orbit.frag"))
        for (n in listOf("uRes", "uYaw", "uTime", "uTanF", "uPitch", "uCam", "uTarget", "uBass", "uEnv", "uFire")) bu[n] = GLES20.glGetUniformLocation(bgProg, n)
        fireProg = Gl.program(RECT_VS, txt("shaders/orbitfire.frag"))
        for (n in listOf("uRect", "uTime", "uHeat", "uOrb")) fu[n] = GLES20.glGetUniformLocation(fireProg, n)
        blitProg = Gl.program(BLIT_VS, BLIT_FS); blU = GLES20.glGetUniformLocation(blitProg, "uTex"); bgFbo = 0; bgTex = 0; bgW = 0; bgH = 0
        val ids = IntArray(2); GLES20.glGenBuffers(2, ids, 0); tri = ids[0]; quad = ids[1]
        upload(tri, floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)); upload(quad, floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        loadedSp = null; bubbleTex = 0; bubbleMsg = ""
    }
    private fun upload(vbo: Int, a: FloatArray) {
        val fb = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(a); fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo); GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, a.size * 4, fb, GLES20.GL_STATIC_DRAW)
    }
    private fun ensureModel() {
        if (loadedSp === species && pet.loaded == species.model) return
        pet.load(species.model)
        meta = try { JSONObject(context.assets.open("pets/${species.model}.json").bufferedReader().use { it.readText() }) } catch (e: Exception) { null }
        meta?.optJSONObject("mouth")?.let { } ; meta?.optJSONArray("mouth")?.let { a -> for (i in 0 until min(4, a.length())) pet.mouthCurve[i] = a.getDouble(i).toFloat() }
        intCache.clear(); vecCache.clear()
        loadedSp = species; env = if (worldIndex >= 0) worldIndex.coerceIn(0, ORBIT_ENVS.size - 1) else species.env; cur = null; play("Idle")
    }
    override fun onSurfaceChanged(width: Int, height: Int) {
        w = max(1, width); h = max(1, height); val m = min(w, h).toFloat(); halfW = .5f * w / m; halfH = .5f * h / m
    }
    override fun release() {
        pet.release()
        for (p in listOf(bgProg, fireProg, blitProg)) if (p != 0) GLES20.glDeleteProgram(p)
        if (bgTex != 0) { GLES20.glDeleteTextures(1, intArrayOf(bgTex), 0); GLES20.glDeleteFramebuffers(1, intArrayOf(bgFbo), 0) }
        bgTex = 0; bgFbo = 0; blitProg = 0
        GLES20.glDeleteBuffers(2, intArrayOf(tri, quad), 0)
        if (bubbleTex != 0) GLES20.glDeleteTextures(1, intArrayOf(bubbleTex), 0)
        bgProg = 0; fireProg = 0; bubbleTex = 0; loadedSp = null
    }

    private fun play(n: String, restart: Boolean = true) {
        val c = pet.clips[n] ?: return
        if (!restart && c === cur) return
        from = cur; fromT = curT; fade = if (cur != null) 0f else 1f; cur = c; curT = 0f
    }
    private fun isIdle() = cur?.name == "Idle"

    // ---------------------------------------------------------------- input (normalised 0..1, y down)
    private fun uOf(nx: Float) = (nx - .5f) * 2f * halfW
    private fun vOf(ny: Float) = (.5f - ny) * 2f * halfH
    // touch: finger on him = grab (hold ~0.3 s or drag = picked up); elsewhere = look at the finger / orbit drag in-app
    private var grab = false; private var held = false; private var falling = false; private var grabT = 0f; private var grabMoved = 0f
    private var gU = 0f; private var gV = 0f; private var g0U = 0f; private var g0V = 0f; private var grabDy = 0f
    private var holdU = 0f; private var air = 0f; private var fallV = 0f; private var holdVx = 0f; private var dangle = 0f; private var dangleV = 0f
    private var land = 0f; private var dropT = -9f; private var fingerOn = false; private var fU = 0f; private var fV = 0f
    private var lastSx = 0f; private var lastFeet = -1f; private var lastS = .5f
    private fun onBody(u: Float, v: Float) = abs(u - lastSx) < lastS * .32f && v > lastFeet - lastS * .05f && v < lastFeet + lastS * .95f
    override fun touchDown(nx: Float, ny: Float): Boolean {
        val u = uOf(nx); val v = vOf(ny); fingerOn = true; fU = u; fV = v
        if (onBody(u, v) && knock == null) { grab = true; grabT = time; grabMoved = 0f; gU = u; gV = v; g0U = u; g0V = v; return true }
        drag = true; d0u = u; off0 = off; lastT = time; vel = 0f; moved = 0f; return true
    }
    override fun touchMove(nx: Float, ny: Float): Boolean {
        val u = uOf(nx); val v = vOf(ny); fU = u; fV = v
        if (grab) { gU = u; gV = v; grabMoved = max(grabMoved, hypot(u - g0U, v - g0V)); if (!held && grabMoved > .03f) startHold(); return true }
        if (!drag || pagesKnown) return true
        val du = u - d0u; moved = max(moved, abs(du))
        if (moved > .03f && (seek == "look" || seek == "found") && knock == null) startle()
        val nOff = (off0 - du / (halfW * 2)).coerceIn(-.35f, PAGES - .65f); val dt = max(1e-3f, time - lastT)
        vel = vel * .6f + ((nOff - off) / dt) * .4f; off = nOff; lastT = time; lastSwipe = time
        return true
    }
    override fun touchUp(): Boolean {
        fingerOn = false
        if (grab) { grab = false; if (held) drop(); return true }
        if (!drag) return true; drag = false
        if (!pagesKnown && moved >= .03f) {
            var tgt = Math.round(off + (vel * .25f).coerceIn(-.6f, .6f)).toFloat(); if (abs(vel) > .8f && tgt == page) tgt += sign(vel)
            page = tgt.coerceIn(0f, PAGES - 1f); lastSwipe = time
        }
        return true
    }
    private fun startHold() {
        held = true; falling = false; walkTo = null; seek = "look"; holdU = lastSx; air = max(0f, lastFeet - floorY())
        grabDy = gV - lastFeet; holdVx = 0f; dangle = 0f; dangleV = 0f
        if (cur?.name != "Idle") play("Idle"); listener?.onDiscovery("orbit picked up")
    }
    private fun drop() { held = false; falling = true; fallV = 0f; dropT = time }
    override fun tapAt(nx: Float, ny: Float): Boolean {
        if (held || falling || time - dropT < .4f) return true
        val u = uOf(nx); val v = vOf(ny); val onIt = onBody(u, v)
        taps.removeAll { time - it > .6f }; taps += time
        if (!onIt) { wander(); return true }
        if (taps.size >= 3) { taps.clear(); play(species.tumble); listener?.onDiscovery("orbit tumble"); return true }
        walkTo = null; seek = "found"; seekT = time; play(species.taps[Random.nextInt(species.taps.size)]); return true
    }
    override fun longPressAt(nx: Float, ny: Float): Boolean { if (grab || held) return true; notifyKnock(); return true }
    override fun greet() { if (knock == null && seek == "look") { seek = "found"; seekT = time } }
    override fun swiped(dir: Float, fast: Boolean) { if (knock == null) startle() }
    override fun perform(trick: String): Boolean {
        when (trick) {
            "notify" -> notifyKnock()
            "walk" -> wander()
            "world" -> env = (env + 1) % ORBIT_ENVS.size
            "tumble" -> play(species.tumble)
            else -> {
                val alias = mapOf("laugh" to "EvilLaugh", "plan" to "Scheme", "fire" to "SpellCast", "boo" to "Boo", "attack" to "Slash", "fly" to "Hop", "cape" to "Spin", "pet" to "Wave")
                val n = alias[trick] ?: trick.replaceFirstChar { it.uppercase() }
                val pick = if (pet.clips[n] != null) n else species.taps[Random.nextInt(species.taps.size)]
                walkTo = null; play(pick)
            }
        }
        return true
    }
    private fun startle() { seek = "startled"; seekT = time; walkTo = null; if (!isIdle() && cur?.name?.startsWith("Dance") != true) play("Idle") }
    /** A notification came in: walk up to the glass, knock, say so, hop back. */
    fun notifyKnock(msg: String? = null) {
        walkTo = null; seek = "look"
        knock = Knock(time, msg ?: listOf("Knock knock! New message for you", "Hey! Someone texted you", "Psst… you have a notification")[Random.nextInt(3)])
    }
    private fun wander() {
        val a = rnd() * 6.283f; val r = .6f + rnd() * 1.2f; var x = px + sin(a) * r; var z = pz + cos(a) * r
        val R = hypot(x, z); if (R > 2.2f) { x *= 2.2f / R; z *= 2.2f / R }; walkTo = floatArrayOf(x, z)
    }

    // ---------------------------------------------------------------- update
    private var pagesKnown = false
    override fun update(dt: Float, input: SceneInput) {
        if (worldIndex != seenWorld) { seenWorld = worldIndex; if (worldIndex >= 0) env = worldIndex.coerceIn(0, ORBIT_ENVS.size - 1); bgDirty = true }
        ensureModel(); val c0 = cur ?: return
        time += dt; val t = time
        charging = input.charging; battery = input.battery.coerceIn(0f, 1f)
        val music = input.audio.active && input.audio.level > .02f
        val bass = if (input.audio.active) input.audio.bass else 0f
        // the launcher's page position drives the camera when we have it
        if (input.pagesKnown) {
            pagesKnown = true
            val pp = input.pagePos
            if (lastPagePos.isNaN()) lastPagePos = pp
            if (abs(pp - lastPagePos) > .002f) { lastSwipe = t; if (seek == "look" || seek == "found") startle() }
            lastPagePos = pp; off = pp; page = pp
        } else if (!drag) {
            val k = 90f; val d = 2 * sqrt(k) * .95f; offV += (k * (page - off) - d * offV) * dt; off += offV * dt
            if (abs(off - page) > .004f) lastSwipe = t
        } else offV = 0f
        camYaw = (off - (if (pagesKnown) 0f else (PAGES - 1) / 2f)) * PI.toFloat() / 4f
        val settled = t - lastSwipe > 1f
        var bodyGoal = camYaw; var bodyK = 22f
        val kn = knock
        if (kn != null && kn.phase == 0) bodyK = 60f
        walkTo?.let { wt ->
            val dx = wt[0] - px; val dz = wt[1] - pz; val d = hypot(dx, dz); val hd = atan2(dx, dz)
            bodyGoal = bodyYaw + atan2(sin(hd - bodyYaw), cos(hd - bodyYaw)); bodyK = 40f
            val v = (meta?.optJSONObject("speed")?.optDouble("Walk", .44)?.toFloat() ?: .44f) * max(0f, cos(bodyGoal - bodyYaw))
            if (cur?.name != "Walk") play("Walk", false)
            if (d < .05f) { walkTo = null; play("Idle"); lastSwipe = max(lastSwipe, t - .4f) }
            else { val st = min(d, v * dt); px += dx / d * st; pz += dz / d * st }
        }
        val rel = bodyGoal - bodyYaw
        val mayTurn = walkTo != null || knock != null || (settled && (seek == "look" || (seek == "found" && t - seekT > .55f)))
        if (mayTurn && abs(rel) > .01f) { val dd = 2 * sqrt(bodyK); bodyV += (bodyK * rel - dd * bodyV) * dt } else bodyV *= exp(-dt * 10f)
        bodyYaw += bodyV * dt
        if (!held && !falling) { val kc = if (t - dropT < 2.5f) .8f else 6f; camX += (px - camX) * min(1f, dt * kc); camZ += (pz - camZ) * min(1f, dt * kc) }
        // startled -> searches the wrong way first -> finds you -> then turns the body
        val relC = atan2(sin(camYaw - bodyYaw), cos(camYaw - bodyYaw)); val st = t - seekT
        if (seek == "startled" && st > .45f) { seek = "search"; seekT = t; scanDir = if (relC > 0) -1f else 1f }
        if (seek == "search" && settled && t - seekT > 1.4f && t - lastSwipe > .9f) { seek = "found"; seekT = t; listener?.onDiscovery("orbit found you") }
        if (seek == "found" && st > 1.4f) seek = "look"
        var want = relC; var kh = 120f
        when (seek) {
            "startled" -> { want = headYaw; kh = 40f; hop = sin(PI.toFloat() * (st / .3f).coerceIn(0f, 1f)) }
            "search" -> { want = scanDir * (.9f * sin((t - seekT) * 2.2f) + .25f); kh = 30f }
            else -> if (knock == null) hop = 0f
        }
        if (seek == "found") kh = 220f
        // the finger is on the glass (not on him): head and eyes follow it
        if (fingerOn && !grab && knock == null && seek != "startled") { want = ((fU - lastSx) / max(lastS, .05f) * 1.4f).coerceIn(-1.2f, 1.2f); kh = 140f }
        if (held) { want = ((gU - lastSx) / max(lastS, .05f)).coerceIn(-.6f, .6f); kh = 60f }
        want = want.coerceIn(-1.25f, 1.25f); val dh = 2 * sqrt(kh) * .8f
        headV += (kh * (want - headYaw) - dh * headV) * dt; headYaw += headV * dt
        var eyeT = if (seek == "search") ((want - headYaw) * 2 + scanDir * .6f * sin((t - seekT) * 2.2f + .6f)).coerceIn(-1f, 1f) else ((relC - headYaw) * 1.6f).coerceIn(-1f, 1f)
        if (seek == "startled") eyeT = eyeX
        eyeX += (eyeT - eyeX) * min(1f, dt * (if (seek == "found") 30f else 12f))
        pupil = if (seek == "startled") .55f else if (seek == "found" && st < .6f) 1.3f else 1f
        // notification: walk up to the glass, knock, tell you, hop back
        knock?.let { k ->
            when (k.phase) {
                0 -> { near = min(1f, near + dt / 1.3f); if (cur?.name != "Walk") play("Walk", false); if (near >= 1f) { k.phase = 1; k.t1 = t; play("Idle") } }
                1 -> { val u = t - k.t1; k.arm = sm(u / .3f) * (1 - sm((u - 2.4f) / .3f))
                    for (i in KNOCKS.indices) if (u >= KNOCKS[i] && !k.done[i]) { k.done[i] = true; rings += t; shake = 1f }
                    var tp = 0f; for (kk in KNOCKS) if (u > kk - .08f) tp = max(tp, exp(-abs(u - kk) * 22f)); k.tap = tp
                    if (u > 3f) { k.phase = 2; k.t2 = t } }
                else -> { val u = t - k.t2; near = max(0f, 1 - u / .5f); hop = sin(PI.toFloat() * (u / .5f).coerceIn(0f, 1f)) * .8f; if (u > .6f) { knock = null; hop = 0f } }
            }
        }
        shake *= exp(-dt * 18f); rings.removeAll { t - it > .7f }
        // picked up: hangs from the finger, swings with the drag; let go: falls, lands with a squash
        if (grab && !held && t - grabT > .3f) startHold()
        if (held) {
            val tu = gU; val tv = gV - grabDy; val ou = holdU
            holdU += (tu - holdU) * min(1f, dt * 16f); air += (max(0f, tv - floorY()) - air) * min(1f, dt * 16f)
            holdVx = (holdU - ou) / max(dt, 1e-3f)
            dangleV += (-holdVx * 2.2f - dangle * 34f - dangleV * 4.5f) * dt; dangle = (dangle + dangleV * dt).coerceIn(-.7f, .7f)
            walkTo = null
        } else if (falling) {
            fallV -= 5.5f * dt; air += fallV * dt; holdU += holdVx * dt; holdVx *= exp(-dt * 2f)
            dangle *= exp(-dt * 6f)
            holdU = holdU.coerceIn(-halfW * .85f, halfW * .85f)
            if (air <= 0f) {
                val hard = -fallV; air = 0f; falling = false; land = min(1f, hard * .8f); shake = min(1f, hard * .9f); dangle = 0f
                // he now stands where he was dropped: put him there in the world
                val lat = holdU / size0(); px = camX + lat * cos(camYaw); pz = camZ - lat * sin(camYaw)
                val R = hypot(px, pz); if (R > 2.2f) { px *= 2.2f / R; pz *= 2.2f / R }
                if (hard > 1.1f) play(species.tumble) else { startle() }
            }
        }
        land *= exp(-dt * 7f)
        // clips: dances only while music plays
        val c = cur!!; curT += dt
        if (c.loop) curT %= max(.01f, c.duration) else if (curT >= c.duration) play(if (walkTo != null) "Walk" else "Idle")
        val isDance = cur!!.name.startsWith("Dance")
        if (music && walkTo == null && knock == null && !charging && (isIdle() || (isDance && curT > cur!!.duration - .05f))) play(species.dances[Random.nextInt(species.dances.size)])
        if (!music && isDance) play("Idle")
        nextLife -= dt
        if (nextLife <= 0 && isIdle() && settled && walkTo == null && knock == null && !charging && seek == "look") {
            nextLife = 7 + rnd() * 6; if (rnd() < .45f) wander() else play(species.life[Random.nextInt(species.life.size)])
        }
        val P = pet; val n = P.nBones * 12; val cc = cur!!
        P.sample(cc, if (cc.loop) curT % cc.duration else min(curT, cc.duration), matA, ctl)
        val f = from
        if (f != null && fade < 1f) {
            fade = min(1f, fade + dt / .3f); fromT += dt
            P.sample(f, if (f.loop) fromT % f.duration else min(fromT, f.duration), matB, ctlB)
            val a = fade * fade * (3 - 2 * fade); for (i in 0 until n) P.pose[i] = matB[i] + (matA[i] - matB[i]) * a
        } else System.arraycopy(matA, 0, P.pose, 0, n)
        val m = meta ?: return; val M = P.pose
        // look-at: neck 40 %, head 60 % about the vertical axis; then a little up at you
        turnBones(M, ints(m, "neck"), pivot2(M, m.optInt("neckBone"), vec(m, "neckPivot")), headYaw * .4f)
        turnBones(M, ints(m, "head"), pivot2(M, m.optInt("headBone"), vec(m, "headPivot")), headYaw * .6f)
        rot3(M, ints(m, "neck"), piv3(M, m.optInt("neckBone"), vec(m, "neckPivot")), 1f, 0f, 0f, species.lookUp[0])
        rot3(M, ints(m, "head"), piv3(M, m.optInt("headBone"), vec(m, "headPivot")), 1f, 0f, 0f, species.lookUp[1])
        // charging: conjures an orb held in both hands; it grows with the battery
        hold += ((if (charging) 1f else 0f) - hold) * min(1f, dt * 4f)
        ball += ((if (charging && hold > .6f) 1f else 0f) - ball) * min(1f, dt * (if (charging) 2.5f else 6f))
        hasBall = false
        if (hold > .01f && m.has("armR") && m.has("armL")) {
            val a = hold; val br = sin(t * 2.2f) * .04f
            arm(M, m, "armR", 1f, 0f, 0f, (-1f - .25f * battery + br) * a); arm(M, m, "armR", 0f, 0f, 1f, .55f * a)
            arm(M, m, "foreR", 1f, 0f, 0f, -.5f * a); arm(M, m, "foreR", 0f, 0f, 1f, (.35f - .3f * battery) * a)
            arm(M, m, "armL", 1f, 0f, 0f, (-1f - .25f * battery + br) * a); arm(M, m, "armL", 0f, 0f, 1f, -.55f * a)
            arm(M, m, "foreL", 1f, 0f, 0f, -.5f * a); arm(M, m, "foreL", 0f, 0f, 1f, (-.35f + .3f * battery) * a)
            if (m.has("handL") && m.has("handR")) {
                val hl = piv3(M, m.optInt("handL"), vec(m, "handLPivot")); val hr = piv3(M, m.optInt("handR"), vec(m, "handRPivot"))
                ballPos[0] = (hl[0] + hr[0]) / 2; ballPos[1] = (hl[1] + hr[1]) / 2 - .05f; ballPos[2] = (hl[2] + hr[2]) / 2 - .05f; hasBall = true
            }
        }
        // mouth: jaw / lips open to gasp, talk, laugh and sing along
        var open = 0f; val st2 = t - seekT; val nm = cur!!.name
        open = when {
            held || falling -> .55f + .25f * abs(sin(t * 9f))
            seek == "startled" -> .9f
            seek == "found" && st2 < 1f -> .45f * abs(sin(st2 * 14f))
            knock?.phase == 1 -> { val u = t - knock!!.t1; if (u > .3f) .2f + .5f * abs(sin(u * 11f) * sin(u * 3.1f)) else 0f }
            nm.startsWith("Dance") -> (bass * .9f).coerceIn(0f, .8f)
            nm == "SpellCast" || nm == "Slash" || nm == "Kick" -> .55f
            charging && ball > .5f -> .12f + .1f * sin(t * 1.3f)
            else -> { snicker -= dt; if (snicker < 0) { snicker = 5 + rnd() * 6; jawT = t }; val u = t - jawT; if (u < 1.1f) .35f * abs(sin(u * 13f)) * (1 - u / 1.1f) else 0f }
        }
        jaw += (open - jaw) * min(1f, dt * 18f)
        if (m.has("jaw")) rot3(M, ints(m, "jaw"), piv3(M, m.optInt("jawBone"), vec(m, "jawPivot")), 1f, 0f, 0f, -.44f * (1 - jaw))
        knock?.let { k -> if (k.arm > 0f && m.has("armR")) {
            val a = k.arm
            arm(M, m, "armR", 1f, 0f, 0f, -1.45f * a); arm(M, m, "armR", 0f, 0f, 1f, .35f * a); arm(M, m, "foreR", 1f, 0f, 0f, (-.35f - .55f * k.tap) * a)
        } }
        if ((held || falling) && m.has("armR") && m.has("armL")) {
            val f1 = sin(t * 11f) * .35f; val f2 = sin(t * 11f + 1.7f) * .35f
            arm(M, m, "armR", 1f, 0f, 0f, -1.25f + f1); arm(M, m, "armR", 0f, 0f, 1f, .5f)
            arm(M, m, "armL", 1f, 0f, 0f, -1.25f + f2); arm(M, m, "armL", 0f, 0f, 1f, -.5f)
        }
        // eyes
        nextBlink -= dt; if (nextBlink < 0) { blinkT = 1f; nextBlink = 2 + rnd() * 3.5f; if (rnd() < .25f) nextBlink = .28f }; blinkT = max(0f, blinkT - dt * 7f)
        val bl = if (held || falling) 0f else max(ctl[0], sin(blinkT * PI.toFloat()))
        val lookY = if (held || falling) -.6f else if (fingerOn && !grab) ((fV - lastFeet) / max(lastS, .05f) - .7f).coerceIn(-.8f, .8f) else if (knock != null) .1f else if (charging && ball > .5f) -.55f else .25f
        val ex = if (charging && ball > .5f) 0f else eyeX
        P.eyeL[0] = max(bl, ctl[0]); P.eyeL[1] = ex; P.eyeL[2] = lookY; P.eyeL[3] = ctl[8]
        P.eyeR[0] = max(bl, ctl[1]); P.eyeR[1] = ex; P.eyeR[2] = lookY; P.eyeR[3] = ctl[8]
        // lips (imp): 0.75 = as painted, below closes, above opens wide; corners pull into a grin
        val mo = if (seek == "startled") 1.45f else (.62f + jaw * .95f).coerceIn(.05f, 1.5f)
        if (m.has("mouthJaw")) {
            val mp = piv3(M, m.optInt("mouthBone"), vec(m, "mouthJawPivot")); val up = piv3(M, m.optInt("mouthUpBone"), vec(m, "mouthUpPivot")); val cp = piv3(M, m.optInt("mouthCLBone"), vec(m, "mouthCPivot"))
            val grin = if (seek == "found" || nm == "EvilLaugh" || nm == "Scheme" || nm == "Point") .22f else .06f * sin(t * .7f)
            rot3(M, ints(m, "mouthJaw"), mp, 1f, 0f, 0f, (mo - .75f) * .55f); rot3(M, ints(m, "mouthUp"), up, 1f, 0f, 0f, -(mo - .75f) * .16f)
            rot3(M, ints(m, "mouthCL"), cp, 0f, 1f, 0f, -grin); rot3(M, ints(m, "mouthCR"), cp, 0f, 1f, 0f, grin)
        }
        P.mouthOpen = (mo / .45f).coerceIn(.04f, 1f); P.mood[0] = if (seek == "found") .35f else ctl[9]; P.mood[1] = if (held || falling) .6f else pupil
        P.yaw = bodyYaw - camYaw; P.lean = 0f; P.pivot = 0f; P.bend[0] = 0f; P.bend[1] = 0f; P.bend[2] = 0f; P.bend[3] = -1f; P.squash = max(ctl[11], land * .6f)
        P.glow = .6f + .4f * sin(t * 2.3f) + 1.6f * bass + (if (seek == "found") .8f else 0f) + ball * .8f
        lastBass = bass
    }
    private var lastBass = 0f

    private fun arm(M: FloatArray, m: JSONObject, key: String, ax: Float, ay: Float, az: Float, a: Float) =
        rot3(M, ints(m, key), piv3(M, m.optInt(key + "Bone"), vec(m, key + "Pivot")), ax, ay, az, a)
    private val intCache = HashMap<String, IntArray>(); private val vecCache = HashMap<String, FloatArray>()
    private fun ints(m: JSONObject, k: String): IntArray = intCache.getOrPut(k) { val a = m.optJSONArray(k); if (a == null) IntArray(0) else IntArray(a.length()) { a.getInt(it) } }
    private fun vec(m: JSONObject, k: String): FloatArray = vecCache.getOrPut(k) { val a = m.optJSONArray(k); if (a == null) FloatArray(3) else FloatArray(3) { a.getDouble(it).toFloat() } }
    private val p2 = FloatArray(2); private val p3a = FloatArray(3)
    private fun pivot2(M: FloatArray, b: Int, r: FloatArray): FloatArray { val o = b * 12
        p2[0] = M[o] * r[0] + M[o + 1] * r[1] + M[o + 2] * r[2] + M[o + 3]; p2[1] = M[o + 4] * r[0] + M[o + 5] * r[1] + M[o + 6] * r[2] + M[o + 7]; return p2 }
    private val pivRing = Array(4) { FloatArray(3) }; private var pivI = 0
    private fun piv3(M: FloatArray, b: Int, r: FloatArray): FloatArray { val o = b * 12; val out = pivRing[pivI]; pivI = (pivI + 1) and 3
        for (i in 0 until 3) out[i] = M[o + i * 4] * r[0] + M[o + i * 4 + 1] * r[1] + M[o + i * 4 + 2] * r[2] + M[o + i * 4 + 3]; return out }
    /** Rotate bones about the vertical (model z) axis through [piv]. */
    private fun turnBones(M: FloatArray, list: IntArray, piv: FloatArray, a: Float) {
        if (abs(a) < 1e-4f) return; val c = cos(a); val s = sin(a); val px = piv[0]; val py = piv[1]
        for (b in list) { val o = b * 12
            for (col in 0 until 4) { val x = M[o + col]; val y = M[o + 4 + col]; M[o + col] = c * x - s * y; M[o + 4 + col] = s * x + c * y }
            M[o + 3] += px - (c * px - s * py); M[o + 7] += py - (s * px + c * py) }
    }
    /** Rotate bones about an arbitrary model-space axis through point [p]. */
    private val rotM = FloatArray(9)
    private fun rot3(M: FloatArray, list: IntArray, p: FloatArray, x: Float, y: Float, z: Float, a: Float) {
        if (abs(a) < 1e-4f) return; val c = cos(a); val s = sin(a); val t = 1 - c
        val R = rotM; R[0] = t * x * x + c; R[1] = t * x * y - s * z; R[2] = t * x * z + s * y; R[3] = t * x * y + s * z; R[4] = t * y * y + c; R[5] = t * y * z - s * x; R[6] = t * x * z - s * y; R[7] = t * y * z + s * x; R[8] = t * z * z + c
        for (b in list) { val o = b * 12
            for (col in 0 until 4) { val v0 = M[o + col]; val v1 = M[o + 4 + col]; val v2 = M[o + 8 + col]
                M[o + col] = R[0] * v0 + R[1] * v1 + R[2] * v2; M[o + 4 + col] = R[3] * v0 + R[4] * v1 + R[5] * v2; M[o + 8 + col] = R[6] * v0 + R[7] * v1 + R[8] * v2 }
            for (i in 0 until 3) M[o + i * 4 + 3] += p[i] - (R[i * 3] * p[0] + R[i * 3 + 1] * p[1] + R[i * 3 + 2] * p[2]) }
    }

    // ---------------------------------------------------------------- draw
    private val pr = FloatArray(2); private val vp = IntArray(4)
    private var bgFbo = 0; private var bgTex = 0; private var bgW = 0; private var bgH = 0; private var bgDirty = true
    private var bgT = -9f; private var bgYaw = 0f; private var bgCx = 0f; private var bgCz = 0f; private var bgEnv = -1
    private var blitProg = 0; private var blU = 0
    private fun ensureBg(w: Int, h: Int) {
        if (bgFbo != 0 && w == bgW && h == bgH) return
        if (bgTex == 0) { val t = IntArray(1); GLES20.glGenTextures(1, t, 0); bgTex = t[0]; val f = IntArray(1); GLES20.glGenFramebuffers(1, f, 0); bgFbo = f[0] }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bgFbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, bgTex, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target)
        bgW = w; bgH = h; bgDirty = true
    }
    override fun draw(input: SceneInput) {
        if (cur == null) return
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0)
        // the world, drawn in code at reduced resolution into its own buffer; re-rendered ~24x a second
        // (or at once when the camera moves), then stretched to the screen with linear filtering
        val bw = max(1, (vp[2] * BG_SCALE).toInt()); val bh = max(1, (vp[3] * BG_SCALE).toInt())
        if (worldIndex >= 0) {
        ensureBg(bw, bh)
        val moving = abs(camYaw - bgYaw) > .002f || abs(camX - bgCx) > .002f || abs(camZ - bgCz) > .002f || env != bgEnv
        if (bgDirty || moving || time - bgT > 1f / 20f) {
            bgDirty = false; bgT = time; bgYaw = camYaw; bgCx = camX; bgCz = camZ; bgEnv = env
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bgFbo); GLES20.glViewport(0, 0, bw, bh)
            GLES20.glUseProgram(bgProg)
            val D = 6f; val tf = (1f / D) / size0(); val feetNdc = -halfH * .52f; val pitch = atan(.12f)
            val camH = D * tan(pitch - atan(feetNdc * tf))
            GLES20.glUniform2f(bu["uRes"]!!, bw.toFloat(), bh.toFloat()); GLES20.glUniform1f(bu["uYaw"]!!, camYaw); GLES20.glUniform1f(bu["uTime"]!!, time % 1000f)
            GLES20.glUniform1f(bu["uTanF"]!!, tf); GLES20.glUniform1f(bu["uPitch"]!!, pitch)
            GLES20.glUniform3f(bu["uCam"]!!, camX + sin(camYaw) * D, camH, camZ + cos(camYaw) * D); GLES20.glUniform2f(bu["uTarget"]!!, camX, camZ)
            GLES20.glUniform1f(bu["uBass"]!!, lastBass); GLES20.glUniform1f(bu["uEnv"]!!, env.toFloat())
            val fr = ball * (.06f + .2f * battery); GLES20.glUniform4f(bu["uFire"]!!, px, .55f, pz - .25f, if (ball > .01f) fr else 0f)
            GLES20.glDisable(GLES20.GL_BLEND); GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tri); GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target); GLES20.glViewport(vp[0], vp[1], vp[2], vp[3])
        }
        GLES20.glUseProgram(blitProg); GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTex)
        GLES20.glUniform1i(blU, 0); GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tri); GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        }
        // the creature
        val P = pet; val s = size(); val k = s / P.height; val boxHalf = P.boxR * k
        val m = min(vp[2], vp[3])
        P.ensureSprite(min(1024, (boxHalf * 2f * m * 1.1f).toInt()))
        P.render()
        val ox = px - camX; val oz = pz - camZ; val lat = ox * cos(camYaw) - oz * sin(camYaw)
        val shx = shake * .012f * sin(time * 90f) * 2f * halfW; val hopY = hop * s * .12f
        val sx = (if (held || falling) holdU else lat * size0()) + shx; val sy = floorY() + air + hopY + P.boxCz * k
        lastSx = sx; lastFeet = floorY() + air + hopY; lastS = s
        P.beginFx()
        val sh = 1f / (1f + air / max(s, .05f) * 2f)
        P.quad(1f, sx, floorY(), s * .32f * sh, s * .05f * sh, 0f, 0f, 0f, 0f, .9f * sh)
        P.sprite(sx, sy, boxHalf, dangle, 1f)
        // knuckle rings on the glass
        for (r in rings) { val u = (time - r) / .7f; P.quad(10f, sx - s * .16f, sy + s * .05f, (.02f + .12f * u) * s, (.02f + .12f * u) * s, 0f, 1f, .89f, .72f, (1 - u) * .8f) }
        // speech bubble while it knocks
        knock?.let { kn -> if (kn.phase >= 1) { val u = time - kn.t1; val a = sm(u / .25f) * (1 - sm((u - 3.2f) / .3f))
            if (a > 0f) { ensureBubble(kn.msg); val bw = min(halfW * .92f, s * .9f); val bh = bw / bubbleAspect
                val bx = sx.coerceIn(-halfW + bw + .02f, halfW - bw - .02f); P.texQuad(bubbleTex, bx, sy + s * .55f + bh, bw, bh, a) } } }
        P.endFx()
        // the orb in its hands
        if (ball > .01f && hasBall) {
            P.projectBox(ballPos, pr); val fx = sx + pr[0] * boxHalf; val fy = sy + pr[1] * boxHalf; val r = ball * (.07f + .13f * battery) * s
            val sxN = 2f * m / vp[2]; val syN = 2f * m / vp[3]
            GLES20.glUseProgram(fireProg)
            GLES20.glUniform4f(fu["uRect"]!!, (fx - r) * sxN, (fy - r) * syN, (fx + r) * sxN, (fy + r) * syN)
            GLES20.glUniform1f(fu["uTime"]!!, time % 1000f); GLES20.glUniform1f(fu["uHeat"]!!, battery); GLES20.glUniform1f(fu["uOrb"]!!, species.orb.toFloat())
            GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quad); GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4); GLES20.glDisable(GLES20.GL_BLEND)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
    private fun ensureBubble(msg: String) {
        if (bubbleTex != 0 && bubbleMsg == msg) return
        if (bubbleTex != 0) GLES20.glDeleteTextures(1, intArrayOf(bubbleTex), 0)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 44f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = 0xFF0D0A14.toInt() }
        val tw = paint.measureText(msg); val W = (tw + 80).toInt(); val H = 150
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFCDB4FF.toInt() }
        c.drawRoundRect(RectF(0f, 0f, W.toFloat(), 118f), 50f, 50f, bg)
        val tail = Path().apply { moveTo(W / 2f - 22f, 116f); lineTo(W / 2f, 148f); lineTo(W / 2f + 22f, 116f); close() }; c.drawPath(tail, bg)
        c.drawText(msg, 40f, 74f, paint)
        val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0); bubbleTex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bubbleTex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0); bmp.recycle()
        bubbleAspect = W / H.toFloat(); bubbleMsg = msg
    }

    companion object {
        private const val PAGES = 5
        /** The code-drawn world is the expensive part: render it at this fraction of the screen. */
        private const val BG_SCALE = .56f
        private const val BLIT_VS = "attribute vec2 aPos; varying vec2 vUv; void main(){ vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }"
        private const val BLIT_FS = "precision mediump float; varying vec2 vUv; uniform sampler2D uTex; void main(){ gl_FragColor = vec4(texture2D(uTex, vUv).rgb, 1.0); }"
        private const val FULL_VS = "attribute vec2 aPos;\nvoid main(){ gl_Position = vec4(aPos, 0.0, 1.0); }\n"
        private const val RECT_VS = "attribute vec2 aPos; uniform vec4 uRect; varying vec2 vUv; void main(){ vUv = aPos; gl_Position = vec4(mix(uRect.xy, uRect.zw, aPos * 0.5 + 0.5), 0.0, 1.0); }"
    }
}
