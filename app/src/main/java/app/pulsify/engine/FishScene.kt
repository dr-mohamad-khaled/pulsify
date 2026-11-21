package app.pulsify.engine

import android.content.Context
import app.pulsify.core.AudioFrame
import app.pulsify.core.Mood
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

/**
 * Crimson, a real 3D betta (Glowy's 'Real Betta' look). Swims around its water, turns with a real body bend, flares
 * its fins at your finger, eats the pellets you drop, blows bubbles and builds a bubble nest at the top.
 *  - Tap the water: pellets sink, it hunts them down and snaps them up.
 *  - Tap it: startle dart away into open water (keep poking: an angry flare at you).
 *  - Touch and drag in the water: it turns to your finger and follows it, nose first.
 *  - Hold it: it nuzzles your finger. Rub it: wiggles. Drag it: it wriggles along with you.
 *  - Hold the water: it flares at your finger.  Music: dances.  Charger: celebrates, rests, gets tired.
 */
class FishScene(private val context: Context) : Creature {
    override var listener: SceneListener? = null
    override var bottomInset = 0f
        set(v) { field = v; layout() }
    override val busy: Boolean get() = true

    private val pet = Pet3D(context)
    private var w = 1; private var h = 1
    private val halfW get() = 0.5f * w / min(w, h)
    private val halfH get() = 0.5f * h / min(w, h)
    private var size = 0.4f
    private var floorY = -0.6f; private var topY = 0.6f
    private var time = 0f

    private var x = 0f; private var y = 0f; private var vx = 0f; private var vy = 0f
    private var tx = 0f; private var ty = 0f; private var maxSpd = 0.25f
    private var facing = 1f; private var yawNow = 1.5708f; private var leanNow = 0f
    private var faceCam = 0f                 // 1 = turn towards the viewer (flaring at you, being petted)
    private var nextWander = 2f; private var nextLife = 5f; private var lastLife = ""

    private var cur: PetClip? = null; private var curT = 0f
    private var from: PetClip? = null; private var fromT = 0f; private var fade = 1f
    private val matA = FloatArray(Pet3D.MAX_BONES * 12); private val matB = FloatArray(Pet3D.MAX_BONES * 12)
    private val ctlA = FloatArray(Pet3D.MAX_CTL); private val ctlB = FloatArray(Pet3D.MAX_CTL)

    private enum class Mode { SWIM, CARRY, SLEEP }
    private var mode = Mode.SWIM
    private var touching = false; private var downOn = false; private var downAt = 0f; private var path = 0f
    private var fX = 0f; private var fY = 0f; private var f0X = 0f; private var f0Y = 0f; private var lX = 0f; private var lY = 0f
    private var held = false; private var sinceTouch = 99f
    private var taps = 0; private var lastTap = -9f
    private val pokes = ArrayList<Float>()
    private var sleepLeft = 0f
    private var charging = false; private var low = false; private var chargeKnown = false; private var fullDone = false
    private var lastBeat = 0f; private var musicOn = false; private var mood = Mood.DAY
    private val reported = HashSet<String>()

    private class P(var kind: Float, var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, var max: Float,
                    var s: Float, var r: Float, var g: Float, var b: Float, var ph: Float)
    private val parts = ArrayList<P>()

    // ------------------------------------------------------------ lifecycle
    /** Which 3D fish: "betta" (Crimson) or "clown" (the clownfish). Same clips, same behaviour. */
    var model = "betta"
    override fun onSurfaceCreated() { pet.create(); pet.load(model); cur = null }
    override fun onSurfaceChanged(width: Int, height: Int) { w = max(1, width); h = max(1, height); layout() }
    override fun setGround(y: Float) {}
    private fun layout() {
        // the clownfish model is chunkier than the betta: it is drawn at about half the size
        size = min(0.4f * halfH, halfW * 0.8f) * (if (model == "clown") 0.45f else 1f)
        floorY = -halfH + bottomInset * 2f * halfH + size * 0.45f
        topY = halfH - size * 0.7f
        x = x.coerceIn(-halfW + size * 0.5f, halfW - size * 0.5f); y = y.coerceIn(floorY, topY)
    }
    override fun release() { pet.release() }

    // ------------------------------------------------------------ clips
    private val idleName: String get() = when {
        charging && pet.clips.containsKey("ChargeRest") -> "ChargeRest"
        low && !charging -> "Tired"
        else -> "Idle"
    }
    private fun isIdle() = curName == "Idle" || curName == "ChargeRest" || curName == "Tired"
    private fun isMove() = curName == "Swim" || curName == "Dash" || curName == "Glide"
    private val curName get() = cur?.name ?: ""
    private fun play(name: String, restart: Boolean = true): Boolean {
        val c = pet.clips[name] ?: return false
        if (!restart && cur === c) return true
        from = cur; fromT = curT; fade = if (cur == null) 1f else 0f; cur = c; curT = 0f
        return true
    }
    private fun done() = cur?.let { !it.loop && curT >= it.duration } ?: true
    private fun discover(id: String) { if (reported.add(id)) listener?.onDiscovery(id) }

    // ------------------------------------------------------------ touch
    private fun toUv(nx: Float, ny: Float) = Pair((nx - 0.5f) * 2f * halfW, (0.5f - ny) * 2f * halfH)
    private fun onFish(ux: Float, uy: Float): Boolean {
        val side = abs(sin(yawNow)); val hw = size * (0.25f + 0.25f * side)
        val dx = (ux - x) / hw; val dy = (uy - y) / (size * 0.38f)
        return dx * dx + dy * dy < 1f
    }
    override fun touchDown(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        touching = true; sinceTouch = 0f; downAt = time; path = 0f; held = false
        fX = ux; fY = uy; f0X = ux; f0Y = uy; lX = ux; lY = uy
        downOn = onFish(ux, uy)
        if (mode == Mode.SLEEP && downOn) wake()
        follow = !downOn && mode == Mode.SWIM
        if (follow) discover("follow")
        return true
    }
    override fun touchMove(nx: Float, ny: Float): Boolean {
        if (!touching) return false
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f; path += hypot(ux - lX, uy - lY); lX = ux; lY = uy; fX = ux; fY = uy
        if (downOn && mode == Mode.SWIM) {
            if (hypot(ux - f0X, uy - f0Y) > size * 0.4f) { mode = Mode.CARRY; play("Dangle"); discover("scoop") }
            else if (path > size * 0.9f && curName != "Tickle") { play("Tickle"); listener?.onBondXp(1); discover("tickle") }
        }
        return true
    }
    override fun touchUp(): Boolean {
        if (!touching) return false
        touching = false; sinceTouch = 0f; faceCam = 0f
        val wasFollow = follow; follow = false
        if (wasFollow && path > size * 0.08f) { pause = 0.6f; held = false; return true }
        if (mode == Mode.CARRY) { mode = Mode.SWIM; play("Swim"); vx = (lX - f0X) * 0.5f; vy = 0f; pickTarget(); maxSpd = 0.35f; return true }
        if (path < size * 0.08f && time - downAt < 0.35f) { if (downOn) poke() else feed(fX, fY) }
        held = false
        return true
    }
    override fun tapAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny); sinceTouch = 0f
        if (onFish(ux, uy)) poke() else feed(ux, uy)
        return true
    }
    override fun longPressAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny); sinceTouch = 0f
        if (onFish(ux, uy)) pat() else flareAt(ux, uy)
        return true
    }
    override fun greet() { if (mode == Mode.SWIM) play("Wiggle") }
    override fun swiped(dir: Float, fast: Boolean) {
        if (mode != Mode.SWIM) return
        tx = (x - dir * halfW * 0.8f).coerceIn(-innerX, innerX); ty = y.coerceIn(innerBot, innerTop); maxSpd = if (fast) 0.9f else 0.45f
        play(if (fast) "Dash" else "Swim")
    }
    override fun perform(trick: String): Boolean {
        sinceTouch = 0f
        if (mode == Mode.SLEEP) wake()
        when (trick) {
            "feed" -> { feed((Random.nextFloat() - 0.5f) * halfW, topY); return true }
            "bubbles" -> { goTop(); return true }
            "flash", "puff" -> { play("Flare"); faceCam = 1f; return true }
            "loop" -> { pickTarget(halfW); maxSpd = 0.9f; play("Dash"); return true }
            "yawn" -> return play("Yawn")
            "stare" -> { play("LookAround"); return true }
            "sleep" -> { goSleep(12f); return true }
        }
        val name = trick.replaceFirstChar { it.uppercase() }
        if (name == "Spin" || name == "Loop") return false
        return play(name)
    }

    private fun poke() {
        listener?.onBondXp(1)
        if (mode == Mode.SLEEP) { wake(); return }
        if (time - lastTap < 0.6f) taps++ else taps = 1
        lastTap = time
        pokes += time; pokes.removeAll { time - it > 8f }
        if (pokes.size >= 7) { pokes.clear(); taps = 0; play("Angry"); faceCam = 1f; discover("angry betta"); return }
        if (taps >= 3) { taps = 0; play("Wiggle"); faceCam = 0.6f; discover("wiggle"); return }
        play("Poke")
        // dart away from the finger; if a wall is that way, flee along the other axis instead
        var ax = x - fX; var ay = y - fY; val al = max(0.001f, hypot(ax, ay)); ax /= al; ay /= al
        var nx = x + ax * size * 1.6f; var ny = y + ay * size * 1.6f
        if (abs(nx) > innerX) { nx = x - ax * size * 0.4f; ny = y + (if (y < (innerBot + innerTop) * 0.5f) 1f else -1f) * size * 1.4f }
        tx = nx.coerceIn(-innerX, innerX); ty = ny.coerceIn(innerBot, innerTop)
        vx = ax * size * 2.4f; vy = ay * size * 1.2f; maxSpd = 0.95f; pause = 0f
        burst(10f, 3, x, y + size * 0.1f, 0.85f, 0.95f, 1f, 0.03f)
    }
    private fun pat() {
        if (mode == Mode.SLEEP) wake()
        play("Pet"); held = true; faceCam = 1f; listener?.onBondXp(2); discover("pet")
    }
    private fun flareAt(ux: Float, uy: Float) {
        if (mode == Mode.SLEEP) wake()
        facing = if (ux > x) 1f else -1f; play("Flare"); discover("flare")
    }
    private fun feed(ux: Float, uy: Float) {
        if (mode == Mode.SLEEP) wake()
        for (i in 0 until 3) parts += P(11f, ux + (Random.nextFloat() - 0.5f) * size * 0.3f, max(uy, y - size), 0f, -0.04f - Random.nextFloat() * 0.03f,
            0f, 30f, 0.014f, 0.62f, 0.36f, 0.18f, Random.nextFloat() * 6f)
        discover("feed")
    }
    private fun goTop() { ty = topY; tx = x; maxSpd = 0.3f; afterArrive = "Nest" }
    private var afterArrive: String? = null
    private fun wake() { mode = Mode.SWIM; play("WakeUp"); sleepLeft = 0f }
    private fun goSleep(secs: Float) { mode = Mode.SLEEP; sleepLeft = secs; play("FallAsleep"); ty = floorY + size * 0.1f; tx = x }

    private fun burst(kind: Float, n: Int, cx: Float, cy: Float, r: Float, g: Float, b: Float, s: Float) {
        for (i in 0 until n) parts += P(kind, cx + (Random.nextFloat() - 0.5f) * size * 0.1f, cy, (Random.nextFloat() - 0.5f) * 0.03f,
            0.06f + Random.nextFloat() * 0.08f, 0f, 2.2f + Random.nextFloat(), s * (0.6f + Random.nextFloat() * 0.8f), r, g, b, Random.nextFloat() * 6f)
        if (parts.size > 60) parts.subList(0, parts.size - 60).clear()
    }
    // Open water: targets stay well inside the screen so it never parks against an edge.
    private val innerX get() = max(0.05f, halfW - size * 0.85f)
    private val innerBot get() = floorY + size * 0.25f
    private val innerTop get() = max(innerBot + 0.05f, topY - size * 0.25f)
    private fun pickTarget(minDist: Float = size * 0.8f) {
        for (i in 0 until 8) {
            val nx = (Random.nextFloat() * 2f - 1f) * innerX
            val ny = innerBot + Random.nextFloat() * (innerTop - innerBot)
            if (hypot(nx - x, ny - y) > minDist || i == 7) { tx = nx; ty = ny; return }
        }
    }
    private var follow = false            // finger down in the water: it comes over to look
    private var pause = 0f                // short hover after arriving, then on to the next spot
    private val snoutX get() = x + sin(yawNow) * size * 0.42f
    private val snoutY get() = y + size * 0.04f

    // ------------------------------------------------------------ update
    override fun update(dt: Float, input: SceneInput) {
        if (pet.loaded != model) { pet.load(model); cur = null; from = null; layout() }
        time += dt; sinceTouch += dt
        if (cur == null) play(idleName)
        mood = input.mood
        musicOn = input.audio.source != AudioFrame.Source.NONE && input.audio.level > 0.02f
        charger(input)
        if (touching && downOn && !held && path < size * 0.08f && time - downAt > 0.5f && mode == Mode.SWIM) pat()
        behave(dt, input)
        move(dt)
        advance(dt)
        particles(dt)
        pet.dim += ((if (low && !charging) 0.25f else 0f) + (if (mode == Mode.SLEEP) 0.12f else 0f) - pet.dim) * min(1f, dt * 2f)
    }

    private fun charger(input: SceneInput) {
        val ch = input.charging; low = input.lowBattery
        if (!chargeKnown) { chargeKnown = true; charging = ch; fullDone = input.battery >= 0.995f; return }
        if (ch && !charging) {
            if (mode == Mode.SLEEP) wake()
            play("Plugged"); discover("charger"); burst(10f, 6, snoutX, snoutY, 1f, 0.9f, 0.5f, 0.03f)
        }
        if (!ch && charging) { if (input.battery < 0.995f && mode == Mode.SWIM) play("Unplugged"); fullDone = false }
        if (ch && input.battery >= 0.995f && !fullDone) { fullDone = true; if (mode == Mode.SLEEP) wake(); play("FullCharge"); discover("full charge") }
        charging = ch
    }

    private fun behave(dt: Float, input: SceneInput) {
        when (mode) {
            Mode.CARRY -> {
                if (!touching) { mode = Mode.SWIM; return }
                tx = fX; ty = fY - size * 0.05f; maxSpd = 3f
                if (curName != "Dangle") play("Dangle")
                return
            }
            Mode.SLEEP -> {
                if (done() && curName == "FallAsleep") play("Sleep")
                sleepLeft -= dt; tx = x; ty = floorY + size * 0.1f; maxSpd = 0.08f
                if (sleepLeft <= 0f && curName == "Sleep") wake()
                return
            }
            Mode.SWIM -> {}
        }
        if (held && touching) { faceCam = 1f; tx = x; ty = y; if (done()) play("Pet"); return }
        if (follow && touching) {
            // swim up to the finger and hover nose-first in front of it
            val side = if (fX > x) 1f else -1f
            tx = (fX - side * size * 0.45f).coerceIn(-halfW + size * 0.5f, halfW - size * 0.5f)
            ty = (fY - size * 0.04f).coerceIn(floorY, topY)
            maxSpd = 0.7f
            if (hypot(tx - x, ty - y) < size * 0.15f) { if (isMove()) play(idleName, false); faceCam = 0.25f }
            else { faceCam = 0f; if (isIdle()) play(if (hypot(tx - x, ty - y) > size) "Dash" else "Swim", false) }
            if (time - downAt > 1.6f && curName == idleName && Random.nextFloat() < dt * 0.5f) play("Flare")
            return
        }
        // pellets: hunt the nearest one
        val pellet = parts.filter { it.kind == 11f }.minByOrNull { hypot(it.x - x, it.y - y) }
        if (pellet != null && (isIdle() || isMove())) {
            tx = pellet.x - sign(pellet.x - x + 0.0001f) * size * 0.4f; ty = pellet.y; maxSpd = 0.55f
            if (hypot(snoutX - pellet.x, snoutY - pellet.y) < size * 0.22f) {
                pellet.life = pellet.max; play("Eat"); listener?.onBondXp(1); discover("eat")
                burst(10f, 2, snoutX, snoutY, 0.9f, 0.95f, 1f, 0.02f)
            }
        }
        val moving = hypot(tx - x, ty - y) > size * 0.12f
        if (done()) {
            held = false; faceCam = 0f
            if (curName == "Eat" || curName == "Turn") play(if (moving) "Swim" else idleName)
            else play(if (moving) (if (maxSpd > 0.6f) "Dash" else "Swim") else idleName, false)
        }
        if (isMove() && !moving) {
            play(idleName, false); afterArrive?.let { play(it); afterArrive = null }
            if (pause <= 0f) pause = if (Random.nextFloat() < 0.6f) 0.3f + Random.nextFloat() * 0.8f else 1.5f + Random.nextFloat() * 1.5f
        }
        if (isIdle() && moving) play(if (maxSpd > 0.6f) "Dash" else "Swim", false)
        if (!isIdle() && !isMove()) return
        if (isIdle() && curName != idleName) { play(idleName, false); return }
        if (musicOn && input.audio.beat > 0.8f && time - lastBeat > 2.5f) {
            lastBeat = time; play(listOf("DanceWave", "DanceFlare").random()); faceCam = 0.5f; return
        }
        // charging: rest low; tired: sink and slow
        if (charging || low) { ty = floorY + size * (if (charging) 0.25f else 0.12f); maxSpd = 0.12f }
        nextWander -= dt
        if (pause > 0f && !moving) pause -= dt
        val arrivedAndRested = !moving && pause <= 0f
        if ((nextWander <= 0f || arrivedAndRested) && pellet == null) {
            nextWander = 2.5f + Random.nextFloat() * 3f
            val keepY = ty
            pickTarget()
            if (charging || low) ty = keepY
            maxSpd = if (low) 0.1f else 0.22f + Random.nextFloat() * 0.2f
            pause = 0f
        }
        if (!isIdle()) return
        nextLife -= dt
        if (nextLife > 0f) return
        nextLife = 7f + Random.nextFloat() * 6f
        val night = mood == Mood.NIGHT || (low && !charging)
        if (night && Random.nextFloat() < 0.35f) { goSleep(10f + Random.nextFloat() * 10f); return }
        val life = listOf("LookAround" to 2f, "Flare" to 1.2f, "Bubble" to 1.5f, "Yawn" to 0.7f, "Wiggle" to 1.2f,
            "Glide" to 2.5f, "Nest" to 0.8f).filter { it.first != lastLife }
        var r = Random.nextFloat() * life.sumOf { it.second.toDouble() }.toFloat()
        for ((n, wgt) in life) {
            r -= wgt
            if (r <= 0f) {
                lastLife = n
                when (n) {
                    "Nest" -> goTop()
                    "Glide" -> { play("Glide"); pickTarget(size * 1.2f); maxSpd = 0.18f }
                    else -> play(n)
                }
                return
            }
        }
    }

    private fun move(dt: Float) {
        val dx = tx - x; val dy = ty - y
        val d = hypot(dx, dy)
        val acting = !isMove() && !isIdle() && curName != "Dangle" && curName != "Eat" && curName != "Turn"
        val spd = if (acting) maxSpd * 0.4f else maxSpd
        val want = if (d > 0.001f) min(spd, d * 2.5f) else 0f
        val wx = if (d > 0.001f) dx / d * want else 0f; val wy = if (d > 0.001f) dy / d * want else 0f
        val k = min(1f, dt * (if (mode == Mode.CARRY) 12f else 2.2f))
        vx += (wx - vx) * k; vy += (wy - vy) * k
        // soft walls: the closer it drifts to an edge the harder the water nudges it back to open water
        if (mode == Mode.SWIM && !(follow && touching)) {
            val ex = abs(x) - innerX
            if (ex > 0f) vx -= sign(x) * ex * 6f * dt
            if (y < innerBot) vy += (innerBot - y) * 4f * dt
            if (y > innerTop && afterArrive == null && curName != "Nest") vy -= (y - innerTop) * 4f * dt
        }
        x += vx * dt; y += vy * dt
        x = x.coerceIn(-halfW + size * 0.5f, halfW - size * 0.5f); y = y.coerceIn(floorY, topY + size * 0.2f)
        // never sit still for long: a slow hover bob while idling
        if (isIdle() && mode == Mode.SWIM) y += sin(time * 1.3f) * size * 0.02f * dt
        // facing: turn with a C-bend when the swim direction flips
        val chase = follow && touching
        if (mode != Mode.CARRY && ((chase && abs(fX - x) > size * 0.12f) || (!chase && abs(vx) > 0.03f))) {
            val nf = if (chase) sign(fX - x) else sign(vx)
            if (nf != facing) { facing = nf; if (isMove() || isIdle()) play("Turn") }
        }
        val yawT = facing * 1.5708f * (1f - 0.75f * faceCam)
        yawNow += (yawT - yawNow) * min(1f, dt * 4f)
        val leanT = (-vy / max(0.05f, maxSpd) * 0.35f).coerceIn(-0.45f, 0.45f) * (if (isMove()) 1f else 0.4f)
        leanNow += (leanT - leanNow) * min(1f, dt * 3f)
        // ambient bubbles from the snout while blowing them
        if ((curName == "Bubble" || curName == "Nest") && Random.nextFloat() < dt * 6f) burst(10f, 1, snoutX, snoutY + size * 0.05f, 0.85f, 0.95f, 1f, 0.025f)
        if (Random.nextFloat() < dt * 0.4f) burst(10f, 1, snoutX, snoutY, 0.85f, 0.95f, 1f, 0.015f)
    }

    private fun advance(dt: Float) {
        val c = cur ?: return
        curT += dt
        if (c.loop && curT > c.duration) curT %= max(0.01f, c.duration)
        val nb = pet.nBones * 12
        pet.sample(c, min(curT, c.duration), matA, ctlA)
        val f = from
        if (f != null && fade < 1f) {
            fade = min(1f, fade + dt / 0.25f); fromT += dt
            pet.sample(f, if (f.loop) fromT % max(0.01f, f.duration) else min(fromT, f.duration), matB, ctlB)
            val a = fade * fade * (3f - 2f * fade)
            for (i in 0 until nb) pet.pose[i] = matB[i] + (matA[i] - matB[i]) * a
        } else System.arraycopy(matA, 0, pet.pose, 0, nb)
        pet.pivot = pet.boxCz
        pet.yaw = yawNow; pet.lean = leanNow
    }

    private fun particles(dt: Float) {
        val it = parts.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life += dt
            when (p.kind) {
                10f -> { p.vy += dt * 0.05f; p.x += (p.vx + sin(time * 3f + p.ph) * 0.02f) * dt; p.y += p.vy * dt; if (p.y > halfH) p.life = p.max }
                11f -> { if (p.y > floorY - size * 0.4f) { p.y += p.vy * dt; p.x += sin(time * 2f + p.ph) * 0.01f * dt } }
                else -> { p.y += p.vy * dt; p.x += p.vx * dt }
            }
            if (p.life >= p.max) it.remove()
        }
    }

    // ------------------------------------------------------------ draw
    override fun draw(input: SceneInput) {
        val m = min(w, h)
        val k = size / pet.height
        val boxHalf = pet.boxR * k
        pet.ensureSprite((boxHalf * 2f * m * 1.4f).toInt())
        pet.render()
        pet.beginFx()
        // soft shadow on the bottom when low
        val near = 1f - ((y - floorY) / (topY - floorY)).coerceIn(0f, 1f)
        pet.quad(1f, x, floorY - size * 0.38f, size * 0.4f, size * 0.06f, 0f, 0f, 0f, 0f, 0.15f + 0.35f * near)
        for (p in parts) if (p.kind == 11f) pet.quad(11f, p.x, p.y, p.s, p.s, 0f, p.r, p.g, p.b, 1f)
        pet.sprite(x, y, boxHalf, 0f, 1f)
        for (p in parts) {
            if (p.kind == 11f) continue
            val a = (min(1f, p.life * 5f) * min(1f, (p.max - p.life) * 2f)).coerceIn(0f, 1f)
            pet.quad(p.kind, p.x, p.y, p.s, p.s, 0f, p.r, p.g, p.b, a * (if (p.kind == 10f) 0.8f else 1f))
        }
        pet.endFx()
    }
}
