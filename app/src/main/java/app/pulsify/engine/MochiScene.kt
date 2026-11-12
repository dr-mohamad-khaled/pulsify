package app.pulsify.engine

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import app.pulsify.core.AudioFrame
import app.pulsify.core.Mood
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Mochi's coats. Each one is only a body colour and a rim tint; the shape never changes. */
data class MochiCoat(val name: String, val base: Int, val rim: Int)

val MOCHI_COATS = listOf(
    MochiCoat("Pearl", 0xFFFBF6EA.toInt(), 0xFFDDB8FF.toInt()),
    MochiCoat("Strawberry", 0xFFFFE1E6.toInt(), 0xFFFF9EC4.toInt()),
    MochiCoat("Matcha", 0xFFE6F3D8.toInt(), 0xFFA8F0C0.toInt()),
    MochiCoat("Taro", 0xFFEAE0FA.toInt(), 0xFFB9A2FF.toInt()),
    MochiCoat("Honey", 0xFFFFEFCF.toInt(), 0xFFFFC27A.toInt()),
)

/**
 * Mochi, the battery-juice bean. All motion lives here on the CPU (springs, timers, a small
 * behaviour state machine); the look lives in mochi.frag. Character units: body ~1.1 tall,
 * feet at y = -0.55. Screen "uv" space is aspect-correct with the short side spanning 1.
 */
class MochiScene(private val context: Context) : Creature {

    override var listener: SceneListener? = null

    private enum class Act { NONE, BUBBLE, STAR, BIRD, PRESS, WOBBLE, STRETCH, GREET, GIGGLE, TICKLE, CELEBRATE, PET }
    private enum class Jar { HIDDEN, HELD, DRAG, LOOSE }

    private var program = 0
    private var quadVbo = 0
    private val uni = HashMap<String, Int>()
    private val fxBuf: FloatBuffer = ByteBuffer.allocateDirect(24 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val fx = FloatArray(24)            // 6 × (x, y, life 0..1, kind)
    private val fxSpeed = FloatArray(6)        // life per second
    private val fxVel = FloatArray(12)

    private var w = 1; private var h = 1
    private var time = 0f

    // Placement.
    private var size = 0.6f
    private var homeX = 0f; private var homeY = -0.4f
    private var x = 0f                         // body x offset from home (uv)
    private var vx = 0f
    private var jumpY = 0f; private var vy = 0f

    // Springs.
    private var sy = 1f; private var syV = 0f
    private var shear = 0f; private var shearV = 0f
    private var press = 0f

    // Face.
    private var lookX = 0f; private var lookY = 0f
    private var lookTX = 0f; private var lookTY = 0f
    private var blink = 1f
    private var nextBlink = 2.5f
    private var nextLookWander = 3f
    private var eyeMode = 0f
    private var mouth = 0f
    private var tears = 0f
    private var blush = 0.5f

    // Arms: current and target (x, y, angle, length) × 2.
    private val arm = floatArrayOf(-0.4f, -0.12f, -0.3f, 1f, 0.4f, -0.12f, 0.3f, 1f)
    private val armT = FloatArray(8)

    // Jar.
    private var jar = Jar.HIDDEN
    private var jarX = 0f; private var jarY = -0.26f   // char space when HELD, uv when loose/dragged
    private var jarVy = 0f
    private var jarVis = 0f
    private var sip = 0f

    // Behaviour.
    private var act = Act.NONE
    private var actT = 0f
    private var nextIdleAct = 7f
    private val lastActs = ArrayDeque<Act>()
    private var wasCharging = false
    private var greetedMorning = false
    private var awakeFor = 0f                  // night: stays awake this long after a touch
    private var birdX = 0f; private var birdY = 0f; private var birdVis = 0f
    private val tapTimes = FloatArray(4); private var tapIdx = 0

    // Touch.
    private var touchDown = false
    private var downAt = 0f
    private var downOnBody = false
    private var tX = 0f; private var tY = 0f
    private var lastTX = 0f; private var lastTY = 0f; private var lastTT = 0f
    private var moved = false
    private var longFired = false

    // Colours.
    private val glow = floatArrayOf(0.35f, 1f, 0.45f)
    private val base = FloatArray(3)
    private val rim = FloatArray(3)
    private var colorsInit = false

    var sinceTouch = 99f; private set

    override val busy: Boolean
        get() = touchDown || sinceTouch < 2f || act != Act.NONE || abs(vy) > 0.01f || abs(vx) > 0.01f ||
            jar == Jar.DRAG || jar == Jar.LOOSE || abs(syV) > 0.05f

    override fun onSurfaceCreated() {
        val vert = context.assets.open("shaders/mochi.vert").bufferedReader().use { it.readText() }
        val frag = context.assets.open("shaders/mochi.frag").bufferedReader().use { it.readText() }
        program = Gl.program(vert, frag)
        for (n in listOf("uQuad", "uRes", "uTime", "uPos", "uSize", "uPx", "uSquash", "uShear", "uLook", "uEyeOpen",
            "uEyeMode", "uMouth", "uTears", "uBlush", "uArmL", "uArmR", "uJar", "uJarHeld", "uSip", "uBase", "uGlow",
            "uRim", "uBeat", "uPress", "uFx", "uBird", "uBattIcon", "uDim")) {
            uni[n] = GLES20.glGetUniformLocation(program, n)
        }
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        quadVbo = ids[0]
        val q = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        val fb = ByteBuffer.allocateDirect(q.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(q)
        fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, q.size * 4, fb, GLES20.GL_STATIC_DRAW)
    }

    /** Fraction of the screen height covered by UI at the bottom (the Studio dock). Mochi sits above it. */
    override var bottomInset = 0f
        set(v) { field = v.coerceIn(0f, 0.8f); layout() }

    override fun onSurfaceChanged(width: Int, height: Int) {
        w = max(1, width); h = max(1, height)
        layout()
    }

    private var ground = Float.NaN
    override fun setGround(y: Float) {
        if (y == ground || (y.isNaN() && ground.isNaN())) return
        ground = y; layout()
    }

    private fun layout() {
        val halfH = 0.5f * h / min(w, h)
        val halfW = 0.5f * w / min(w, h)
        // Same size on every screen in short-side units; never wider than the screen allows.
        size = min(0.62f, halfW * 2f * 0.62f)
        homeX = 0f
        homeY = (if (ground.isNaN()) -halfH + 0.32f else ground) + 0.55f * size
        // Short previews (the home-screen card) keep Mochi smaller and up top, clear of the text.
        if (h.toFloat() / w < 1.5f) {
            size = min(0.42f, halfW * 0.8f)
            homeY = halfH - 0.24f - 0.5f * size
        }
        if (bottomInset > 0f) {
            val floor = -halfH + bottomInset * 2f * halfH + 0.06f
            val room = halfH - 0.08f - floor
            size = min(size, room / 1.25f)
            homeY = max(homeY, floor + 0.55f * size)
        }
    }

    private fun toUv(nx: Float, ny: Float): Pair<Float, Float> {
        val m = min(w, h).toFloat()
        return Pair((nx - 0.5f) * w / m, (0.5f - ny) * h / m)
    }

    // Body position in uv.
    private val bodyX get() = homeX + x
    private val bodyY get() = homeY + jumpY

    private fun onBody(ux: Float, uy: Float): Boolean {
        val cx = (ux - bodyX) / size; val cy = (uy - bodyY) / size
        return (cx / 0.5f) * (cx / 0.5f) + ((cy + 0.05f) / 0.62f) * ((cy + 0.05f) / 0.62f) < 1f
    }

    private fun jarUv(): Pair<Float, Float> =
        if (jar == Jar.HELD) Pair(bodyX + jarX * size, bodyY + jarY * size) else Pair(jarX, jarY)

    private fun onJar(ux: Float, uy: Float): Boolean {
        if (jarVis < 0.5f) return false
        val (jx, jy) = jarUv()
        return abs(ux - jx) < 0.2f * size && abs(uy - jy) < 0.32f * size
    }

    // ---- Touch (normalised 0..1, y down). Returns true when Mochi used the touch. ----

    override fun touchDown(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        tX = ux; tY = uy; lastTX = ux; lastTY = uy; lastTT = time
        moved = false; longFired = false
        downAt = time
        touchDown = true
        lookAtUv(ux, uy)
        if (onJar(ux, uy) && (jar == Jar.HELD || jar == Jar.LOOSE)) {
            jar = Jar.DRAG
            jarX = ux; jarY = uy; jarVy = 0f
            act = Act.NONE
            discover("jar_thief")
            return true
        }
        downOnBody = onBody(ux, uy)
        wake()
        return downOnBody
    }

    override fun touchMove(nx: Float, ny: Float): Boolean {
        if (!touchDown) return false
        val (ux, uy) = toUv(nx, ny)
        val dt = max(1e-3f, time - lastTT)
        val vxs = (ux - lastTX) / dt
        if (hypot(ux - tX, uy - tY) > 0.03f) moved = true
        if (jar == Jar.DRAG) {
            jarX = ux; jarY = uy
        } else {
            lookAtUv(ux, uy)
            // A fast swipe across Mochi sends a jelly wobble.
            if (abs(vxs) > 3f && abs((uy - bodyY) / size) < 0.6f) {
                shearV += (vxs / 3f).coerceIn(-3f, 3f) * 0.6f
            }
        }
        lastTX = ux; lastTY = uy; lastTT = time
        sinceTouch = 0f
        return jar == Jar.DRAG || downOnBody
    }

    override fun touchUp(): Boolean {
        if (!touchDown) return false
        touchDown = false
        sinceTouch = 0f
        val wasDrag = jar == Jar.DRAG
        if (wasDrag) {
            jar = Jar.LOOSE
            return true
        }
        val tap = !moved && time - downAt < 0.35f
        if (tap && downOnBody) {
            tapBody()
            return true
        }
        if (tap) {
            // Tapping nearby: a little startled hop and a look.
            lookAtUv(tX, tY)
            if (!isAsleep()) { syV -= 2.5f; blink = 0f }
        }
        return downOnBody
    }

    override fun tapAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        lookAtUv(ux, uy)
        wake()
        if (onBody(ux, uy)) { tapBody(); return true }
        return false
    }

    override fun longPressAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        wake()
        if (onBody(ux, uy)) { startAct(Act.PET); discover("pet"); return true }
        // Long press on the sky drops a star for Mochi to catch.
        startAct(Act.STAR)
        return false
    }

    private fun tapBody() {
        tapTimes[tapIdx % 4] = time; tapIdx++
        listener?.onBondXp(1)
        syV -= 5f
        spawnFx(1f, (Random.nextFloat() - 0.5f) * 0.5f, 0.45f, 0.9f)
        val quick = tapIdx >= 3 && time - tapTimes[(tapIdx - 3) % 4] < 1.2f
        if (quick) { startAct(Act.TICKLE); discover("tickle") } else if (act == Act.NONE || act == Act.GIGGLE) startAct(Act.GIGGLE)
    }

    private fun lookAtUv(ux: Float, uy: Float) {
        val cx = (ux - bodyX) / size; val cy = (uy - bodyY - 0.15f * size) / size
        lookTX = (cx / 0.8f).coerceIn(-1f, 1f)
        lookTY = (cy / 0.8f).coerceIn(-1f, 1f)
        nextLookWander = 2.5f + Random.nextFloat() * 2f
    }

    override fun greet() {
        wake()
        if (jar != Jar.DRAG) startAct(Act.GREET)
    }

    private fun wake() { awakeFor = 10f }

    private var mood = Mood.DAY
    private fun isAsleep() = mood == Mood.NIGHT && awakeFor <= 0f && act == Act.NONE && jar != Jar.DRAG && jar != Jar.LOOSE

    override fun perform(trick: String): Boolean {
        wake()
        val a = when (trick) {
            "bubble" -> Act.BUBBLE; "star" -> Act.STAR; "bird" -> Act.BIRD; "party" -> Act.CELEBRATE
            "wobble" -> Act.WOBBLE; "stretch" -> Act.STRETCH; "giggle" -> Act.GIGGLE; "hello" -> Act.GREET
            else -> return false
        }
        startAct(a); return true
    }

    private fun startAct(a: Act) {
        act = a; actT = 0f
        when (a) {
            Act.BUBBLE -> spawnFx(3f, 0f, 0.07f, 0.4f, slot = 5)
            Act.STAR -> { spawnFx(4f, 0.55f, 1.7f, 0.45f, slot = 4); fxVel[8] = -0.5f; fxVel[9] = -1.35f }
            Act.BIRD -> { birdX = 1.4f; birdY = 1.1f; birdVis = 1f; discover("bird") }
            Act.CELEBRATE -> { vy = 1.1f * size; syV -= 4f; repeat(3) { spawnFx(if (it == 1) 2f else 1f, (it - 1) * 0.3f, 0.5f, 0.8f) } }
            Act.GREET -> { vy = 0.6f * size; syV -= 3f }
            Act.WOBBLE -> shearV += if (Random.nextBoolean()) 2.4f else -2.4f
            else -> {}
        }
    }

    private fun spawnFx(kind: Float, cx: Float, cy: Float, speed: Float, slot: Int = -1) {
        var i = slot
        if (i < 0) {
            i = 0
            var best = -1f
            for (k in 0 until 4) { val life = if (fx[k * 4 + 3] == 0f) 2f else fx[k * 4 + 2]; if (life > best) { best = life; i = k } }
        }
        fx[i * 4] = cx; fx[i * 4 + 1] = cy; fx[i * 4 + 2] = 0f; fx[i * 4 + 3] = kind
        fxSpeed[i] = speed
        fxVel[i * 2] = (Random.nextFloat() - 0.5f) * 0.15f
        fxVel[i * 2 + 1] = if (kind == 1f || kind == 2f || kind == 5f) 0.28f else 0f
    }

    private fun discover(id: String) { if (reported.add(id)) listener?.onDiscovery(id) }
    private val reported = HashSet<String>()

    // ---- Frame ----

    override fun update(dt: Float, input: SceneInput) {
        time += dt
        sinceTouch += dt
        mood = input.mood
        awakeFor -= dt
        val a = input.audio
        val musicOn = a.source != AudioFrame.Source.NONE
        val charging = input.charging
        val low = input.lowBattery && !charging

        // Morning stretch once per morning.
        if (mood == Mood.MORNING && !greetedMorning && act == Act.NONE) { greetedMorning = true; startAct(Act.STRETCH) }
        if (mood != Mood.MORNING) greetedMorning = false

        // Jar follows the charger.
        if (charging && !wasCharging && jar == Jar.HIDDEN) { jar = Jar.HELD; jarX = 0f; jarY = -0.26f; startAct(Act.CELEBRATE) }
        if (!charging && wasCharging && jar != Jar.DRAG) jar = Jar.HIDDEN
        if (low && jar == Jar.HIDDEN) { jar = Jar.HELD; jarX = 0f; jarY = -0.26f }
        if (!low && !charging && jar == Jar.HELD) jar = Jar.HIDDEN
        wasCharging = charging
        jarVis += ((if (jar == Jar.HIDDEN) 0f else 1f) - jarVis) * min(1f, dt * 6f)

        // Loose jar falls to the floor.
        val floorY = homeY - 0.55f * size + 0.25f * size
        if (jar == Jar.LOOSE) {
            jarVy -= 3.2f * dt
            jarY += jarVy * dt
            if (jarY < floorY) { jarY = floorY; jarVy = -jarVy * 0.3f; if (abs(jarVy) < 0.1f) jarVy = 0f }
        }

        // Chase: when the jar is away, walk to it; jump if it is above.
        val chasing = jar == Jar.DRAG || jar == Jar.LOOSE
        if (chasing) {
            val (jx, jy) = jarUv()
            val dx = jx - bodyX
            val halfW = 0.5f * w / min(w, h) - 0.58f * size
            val target = (jx - homeX).coerceIn(-halfW, halfW)
            vx += ((target - x) * 6f - vx * 3.5f) * dt
            if (jumpY <= 0.001f && jy > bodyY + 0.55f * size && abs(dx) < 0.45f * size && Random.nextFloat() < dt * 2.5f) {
                vy = min(1.6f, (jy - bodyY) * 2.2f + 0.6f) * size; syV -= 3f
            }
            // Grab it back.
            if (abs(dx) < 0.22f * size && abs(jy - (bodyY - 0.26f * size)) < 0.45f * size && jar == Jar.LOOSE) {
                jar = Jar.HELD; jarX = 0f; jarY = -0.26f
                startAct(Act.CELEBRATE)
                discover("rescue")
                listener?.onBondXp(5)
            }
            lookAtUv(jx, jy)
            awakeFor = 4f
        } else {
            vx += ((-x) * 4f - vx * 3f) * dt
        }
        x += vx * dt

        // Gravity.
        if (jumpY > 0f || vy > 0f) {
            vy -= 4.2f * size * dt
            jumpY += vy * dt
            if (jumpY <= 0f) { jumpY = 0f; if (vy < -0.3f * size) syV -= abs(vy) * 4f / size; vy = 0f }
        }

        // Music: bounce on the beat, glow colours cycle.
        if (musicOn && !isAsleep()) {
            if (a.beat >= 0.99f) syV -= 2.2f + a.bass * 3f
            shearV += (sin(time * a.bpm.coerceAtLeast(60f) / 60f * PI.toFloat()) * a.mid * 0.6f) * dt * 6f
        }
        val hueCycle = if (musicOn) (time * 40f) % 360f else -1f
        val glowTarget = when {
            low && jar == Jar.HELD -> Color.rgb(255, 77, 64)
            hueCycle >= 0f -> Color.HSVToColor(floatArrayOf((130f + hueCycle) % 360f, 0.7f, 1f))
            else -> Color.rgb(90, 255, 115)
        }
        ease(glow, glowTarget, dt * 3f)
        val coat = MOCHI_COATS[input.coat.coerceIn(0, MOCHI_COATS.size - 1)]
        ease(base, coat.base, if (colorsInit) dt * 4f else 1f)
        val rimC = if (input.accent != 0) input.accent else coat.rim
        ease(rim, rimC, if (colorsInit) dt * 3f else 1f)
        colorsInit = true

        // Long press from the raw touch stream (the wallpaper has no gesture detector).
        if (touchDown && !moved && !longFired && jar != Jar.DRAG && time - downAt > 0.6f) {
            longFired = true
            if (downOnBody) { startAct(Act.PET); discover("pet") } else startAct(Act.STAR)
        }

        // Idle actions.
        actT += dt
        val asleep = isAsleep()
        if (act == Act.NONE && !asleep && !chasing && !charging && !low && !touchDown && sinceTouch > 3f) {
            nextIdleAct -= dt
            if (nextIdleAct <= 0f) {
                val pool = mutableListOf(Act.BUBBLE, Act.STAR, Act.BIRD, Act.PRESS, Act.WOBBLE)
                pool.removeAll(lastActs)
                val pick = pool.random()
                lastActs.addLast(pick); if (lastActs.size > 2) lastActs.removeFirst()
                startAct(pick)
                nextIdleAct = 7f + Random.nextFloat() * 7f
            }
        }

        // Defaults for this frame.
        var eTarget = 0f
        var mTarget = 0f
        var openTarget = 1f
        var tearT = 0f
        var blushT = 0.5f
        var pressT = 0f
        var breathe = 0.018f
        var breatheRate = 1.6f
        // Arm targets: rest at the sides.
        setArms(-0.4f, -0.13f, -0.25f, 0.4f, -0.13f, 0.25f)

        when {
            jar == Jar.HELD && (charging || low) && act != Act.CELEBRATE -> {
                setArms(-0.24f, -0.2f, -0.45f, 0.24f, -0.2f, 0.45f)
                if (charging) { mTarget = 2f; blushT = 0.75f; sip += (1f - sip) * min(1f, dt * 3f); breatheRate = 2.2f } else sip *= 0.9f
                if (low) {
                    eTarget = 4f; breathe = 0.01f; breatheRate = 0.9f
                    if (fx[23] == 0f || fx[22] >= 1f) spawnFx(6f, 0.22f, 0.32f, 0.35f, slot = 5)
                }
            }
            chasing -> {
                sip = 0f
                tearT = 1f; mTarget = 1f
                val (jx, jy) = jarUv()
                val dirX = ((jx - bodyX) / size).coerceIn(-1f, 1f)
                val up = ((jy - bodyY) / size).coerceIn(-0.5f, 1f)
                setArms(-0.33f + dirX * 0.06f, 0.02f + up * 0.1f, -2.3f + dirX * 0.4f, 0.33f + dirX * 0.06f, 0.05f + up * 0.1f, 2.3f + dirX * 0.4f)
            }
            asleep -> {
                eTarget = 3f; openTarget = 0f; breathe = 0.035f; breatheRate = 0.7f
                if (fx[23] == 0f || fx[22] >= 1f) spawnFx(5f, 0.3f, 0.5f, 0.3f, slot = 5)
            }
            else -> sip *= 0.85f
        }
        if (mood == Mood.NIGHT && !asleep && act == Act.NONE && !chasing && jar != Jar.HELD) { eTarget = 4f } // groggy

        when (act) {
            Act.GIGGLE -> { eTarget = 1f; mTarget = 3f; blushT = 1f; if (actT > 0.9f) act = Act.NONE }
            Act.TICKLE -> {
                eTarget = 1f; mTarget = 3f; blushT = 1f
                shear = sin(actT * 28f) * 0.06f
                setArms(-0.36f, 0.0f, -1.6f + sin(actT * 20f) * 0.4f, 0.36f, 0.0f, 1.6f - sin(actT * 20f) * 0.4f)
                if (actT > 1.6f) act = Act.NONE
            }
            Act.PET -> {
                eTarget = 2f; blushT = 1f; mTarget = 0f
                if (actT < 0.05f) spawnFx(1f, 0f, 0.6f, 0.6f)
                if (actT > 2.2f) act = Act.NONE
            }
            Act.CELEBRATE -> {
                eTarget = 1f; mTarget = 3f; blushT = 1f
                setArms(-0.4f, 0.1f, -2.6f, 0.4f, 0.1f, 2.6f)
                if (actT > 1.6f) act = Act.NONE
            }
            Act.GREET -> {
                eTarget = 1f; mTarget = 3f
                setArms(-0.4f, -0.13f, -0.25f, 0.42f, 0.12f, 2.5f + sin(actT * 14f) * 0.35f)
                if (actT > 1.8f) act = Act.NONE
            }
            Act.STRETCH -> {
                val k = smoothPulse(actT, 0.3f, 1.8f, 2.4f)
                sy += (1f + 0.1f * k - sy) * min(1f, dt * 8f)
                eTarget = if (k > 0.5f) 3f else 0f; openTarget = 1f - k
                mTarget = if (k > 0.5f) 1f else 0f
                setArms(-0.36f, -0.13f + 0.3f * k, -0.25f - 2.5f * k, 0.36f, -0.13f + 0.3f * k, 0.25f + 2.5f * k)
                if (actT > 2.6f) act = Act.NONE
            }
            Act.BUBBLE -> {
                mTarget = 2f; lookTX = 0f; lookTY = 0.3f
                val life = fx[22]
                if (life >= 0.92f && life < 0.95f) { blink = 0f; syV -= 2f }
                if (life >= 0.92f) { eTarget = 1f; mTarget = 3f }
                if (actT > 3.2f) act = Act.NONE
            }
            Act.STAR -> {
                // The star falls toward the mouth; Mochi watches, opens wide, then munches.
                val sx = fx[16]; val syy = fx[17]
                lookTX = (sx / 0.8f).coerceIn(-1f, 1f); lookTY = ((syy - 0.15f) / 0.8f).coerceIn(-1f, 1f)
                fxVel[8] += (-sx * 4f - fxVel[8]) * dt * 2f
                fx[16] += fxVel[8] * dt; fx[17] += fxVel[9] * dt
                if (syy > 0.1f) mTarget = 1f
                if (syy <= 0.1f && fx[19] == 4f) {
                    fx[19] = 0f
                    syV -= 3f; spawnFx(2f, 0f, 0.15f, 1.2f)
                    discover("star_snack")
                    listener?.onBondXp(3)
                }
                if (fx[19] != 4f) { eTarget = 1f; mTarget = if (sin(actT * 18f) > 0f) 0f else 2f; blushT = 0.9f }
                if (actT > 3.2f) { act = Act.NONE; fx[19] = 0f }
            }
            Act.BIRD -> {
                val land = smoothPulse(actT, 0f, 1.6f, 4.6f)
                val tx = 0.05f; val ty = 0.56f
                if (actT < 1.6f) { birdX += (tx - birdX) * min(1f, dt * 3f); birdY += (ty - birdY) * min(1f, dt * 3f) }
                else if (actT > 4.6f) { birdX += dt * 1.4f; birdY += dt * 1.0f }
                else { birdX = tx; birdY = ty + (sy - 1f) * 0.5f }
                lookTX = 0f; lookTY = 1f
                if (land > 0.5f) { eTarget = 2f; blushT = 1f }
                if (actT > 6.2f) { act = Act.NONE; birdVis = 0f }
            }
            Act.PRESS -> {
                pressT = smoothPulse(actT, 0.2f, 0.8f, 2.4f)
                lookTX = 0f; lookTY = 0f
                if (pressT > 0.5f) blushT = 0.9f
                if (actT > 3f) act = Act.NONE
            }
            Act.WOBBLE -> { eTarget = 1f; if (actT > 1.2f) act = Act.NONE }
            Act.NONE -> {}
        }
        if (act != Act.BIRD) birdVis = max(0f, birdVis - dt * 2f)

        // Gaze: wander when idle.
        nextLookWander -= dt
        if (nextLookWander <= 0f && !touchDown && act == Act.NONE && !chasing) {
            lookTX = (Random.nextFloat() - 0.5f) * 1.2f; lookTY = (Random.nextFloat() - 0.4f) * 0.8f
            nextLookWander = 2f + Random.nextFloat() * 3f
        }
        lookX += (lookTX - lookX) * min(1f, dt * 7f)
        lookY += (lookTY - lookY) * min(1f, dt * 7f)

        // Blink.
        nextBlink -= dt
        if (nextBlink <= 0f) { blink = 0f; nextBlink = 2.5f + Random.nextFloat() * 3.5f }
        blink = min(1f, blink + dt * 9f)

        // Squash spring (target includes breathing) and wobble spring.
        val breath = 1f + breathe * sin(time * breatheRate * 2f * PI.toFloat() / 2f)
        val syTarget = when {
            asleep -> 0.92f * breath
            low -> 0.95f * breath
            else -> breath
        }
        syV += ((syTarget - sy) * 180f - syV * 9f) * dt
        sy += syV * dt
        sy = sy.coerceIn(0.72f, 1.3f)
        if (act != Act.TICKLE) {
            shearV += (-shear * 160f - shearV * 5f) * dt
            shear += shearV * dt
            shear = shear.coerceIn(-0.25f, 0.25f)
        }

        eyeMode = eTarget
        mouth = mTarget
        tears += (tearT - tears) * min(1f, dt * 4f)
        blush += (blushT - blush) * min(1f, dt * 3f)
        press += (pressT - press) * min(1f, dt * 8f)
        val openNow = min(openTarget, blink)
        eyeOpen += (openNow - eyeOpen) * min(1f, dt * 20f)

        for (i in 0 until 8) arm[i] += (armT[i] - arm[i]) * min(1f, dt * 10f)

        // Effects age.
        for (i in 0 until 6) {
            if (fx[i * 4 + 3] == 0f) continue
            fx[i * 4 + 2] += fxSpeed[i] * dt
            if (i != 4) { fx[i * 4] += fxVel[i * 2] * dt; fx[i * 4 + 1] += fxVel[i * 2 + 1] * dt }
            if (fx[i * 4 + 2] >= 1f) fx[i * 4 + 3] = 0f
        }
    }

    private var eyeOpen = 1f

    private fun setArms(lx: Float, ly: Float, la: Float, rx: Float, ry: Float, ra: Float) {
        armT[0] = lx; armT[1] = ly; armT[2] = la; armT[3] = 1f
        armT[4] = rx; armT[5] = ry; armT[6] = ra; armT[7] = 1f
    }

    private fun smoothPulse(t: Float, a: Float, b: Float, c: Float): Float = when {
        t < a -> 0f
        t < b -> ((t - a) / (b - a)).let { it * it * (3 - 2 * it) }
        t < c -> 1f
        t < c + 0.4f -> 1f - ((t - c) / 0.4f).let { it * it * (3 - 2 * it) }
        else -> 0f
    }

    override fun draw(input: SceneInput) {
        if (program == 0) return
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)

        val bx = bodyX; val by = bodyY
        // Bounding quad: Mochi, its effects above, and the jar wherever it is.
        var minX = bx - 0.62f * size; var maxX = bx + 0.62f * size
        var minY = by - 0.72f * size; var maxY = by + 1.0f * size
        if (act == Act.STAR || act == Act.BIRD) { maxY = by + 1.9f * size; maxX = bx + 1.5f * size }
        if (jar == Jar.DRAG || jar == Jar.LOOSE) {
            minX = min(minX, jarX - 0.3f * size); maxX = max(maxX, jarX + 0.3f * size)
            minY = min(minY, jarY - 0.35f * size); maxY = max(maxY, jarY + 0.4f * size)
        }
        GLES20.glUniform4f(u("uQuad"), minX, minY, maxX, maxY)
        GLES20.glUniform2f(u("uRes"), w.toFloat(), h.toFloat())
        GLES20.glUniform1f(u("uTime"), time % 600f)
        GLES20.glUniform2f(u("uPos"), bx, by)
        GLES20.glUniform1f(u("uSize"), size)
        GLES20.glUniform1f(u("uPx"), 1f / (size * min(w, h)))
        GLES20.glUniform2f(u("uSquash"), 1f / sy, sy)
        GLES20.glUniform1f(u("uShear"), shear)
        GLES20.glUniform2f(u("uLook"), lookX, lookY)
        GLES20.glUniform1f(u("uEyeOpen"), eyeOpen)
        GLES20.glUniform1f(u("uEyeMode"), eyeMode)
        GLES20.glUniform1f(u("uMouth"), mouth)
        GLES20.glUniform1f(u("uTears"), tears)
        GLES20.glUniform1f(u("uBlush"), blush)
        GLES20.glUniform4f(u("uArmL"), arm[0], arm[1], arm[2], arm[3])
        GLES20.glUniform4f(u("uArmR"), arm[4], arm[5], arm[6], arm[7])
        val held = jar == Jar.HELD
        val jx = if (held) jarX else (jarX - bx) / size
        val jy = if (held) jarY else (jarY - by) / size
        val level = if (input.lowBattery && !input.charging) input.battery.coerceAtMost(0.12f) else input.battery
        GLES20.glUniform4f(u("uJar"), jx, jy, jarVis, level)
        GLES20.glUniform1f(u("uJarHeld"), if (held) 1f else 0f)
        GLES20.glUniform1f(u("uSip"), sip)
        GLES20.glUniform3f(u("uBase"), base[0], base[1], base[2])
        GLES20.glUniform3f(u("uGlow"), glow[0], glow[1], glow[2])
        GLES20.glUniform3f(u("uRim"), rim[0], rim[1], rim[2])
        GLES20.glUniform1f(u("uBeat"), input.audio.beat)
        GLES20.glUniform1f(u("uPress"), press)
        fxBuf.position(0); fxBuf.put(fx); fxBuf.position(0)
        GLES20.glUniform4fv(u("uFx"), 6, fxBuf)
        GLES20.glUniform4f(u("uBird"), birdX, birdY, time * 22f, birdVis)
        GLES20.glUniform1f(u("uBattIcon"), if (input.charging && held) jarVis else 0f)
        val dim = when { input.mood == Mood.NIGHT -> 0.35f; input.lowBattery -> 0.3f; else -> 0f }
        GLES20.glUniform1f(u("uDim"), dim)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    override fun release() {
        if (program != 0) GLES20.glDeleteProgram(program)
        if (quadVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
        program = 0; quadVbo = 0
    }

    private fun u(n: String) = uni[n] ?: -1

    private fun ease(dst: FloatArray, argb: Int, k: Float) {
        val kk = min(1f, k)
        dst[0] += (Color.red(argb) / 255f - dst[0]) * kk
        dst[1] += (Color.green(argb) / 255f - dst[1]) * kk
        dst[2] += (Color.blue(argb) / 255f - dst[2]) * kk
    }
}
