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

/** Kalboob's coats: main fur, light fur (muzzle/chest), collar. */
data class PupCoat(val name: String, val fur: Int, val light: Int, val collar: Int)

val PUP_COATS = listOf(
    PupCoat("Golden", 0xFFEDB56B.toInt(), 0xFFFFEDC7.toInt(), 0xFFD9263A.toInt()),
    PupCoat("Cream", 0xFFF3DDB5.toInt(), 0xFFFFF7E8.toInt(), 0xFF2F80ED.toInt()),
    PupCoat("Red", 0xFFC9783E.toInt(), 0xFFF3C79A.toInt(), 0xFF27AE60.toInt()),
    PupCoat("Chocolate", 0xFF7A4A2C.toInt(), 0xFFC9946A.toInt(), 0xFFF2C94C.toInt()),
)

/**
 * Kalboob, the golden retriever puppy. Eager and loyal. Its body plan, rig and every behaviour
 * are its own: tail and ear springs, head tilt, sitting/lying blend, paws, fetch, belly rubs.
 * Character units: paws on the floor at y = -0.55, sitting height about 1.15.
 */
class KalboobScene(private val context: Context) : Creature {

    override var listener: SceneListener? = null

    private enum class Act { NONE, WIGGLE, RUB, FETCH, SNIFF, DIG, BUTTERFLY, YAWN, PAW, HOWL, STRETCH, GREET, ZOOM }

    private var program = 0
    private var quadVbo = 0
    private val uni = HashMap<String, Int>()
    private val fxBuf: FloatBuffer = ByteBuffer.allocateDirect(24 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val fx = FloatArray(24)
    private val fxSpeed = FloatArray(6)
    private val fxVel = FloatArray(12)

    private var w = 1; private var h = 1
    private var time = 0f
    private var size = 0.6f
    private var homeX = 0f; private var homeY = -0.4f
    override var bottomInset = 0f
        set(v) { field = v.coerceIn(0f, 0.8f); layout() }

    // Body.
    private var x = 0f; private var vx = 0f
    private var jumpY = 0f; private var vy = 0f
    private var sy = 1f; private var syV = 0f
    private var shear = 0f
    private var lie = 0f
    private var facing = 0f            // lean while running

    // Head.
    private var lookX = 0f; private var lookY = 0f; private var lookTX = 0f; private var lookTY = 0f
    private var tilt = 0f; private var tiltT = 0f
    private var headY = 0f
    private var nextWander = 3f

    // Springs for ears and tail.
    private var earL = 0f; private var earLV = 0f
    private var earR = 0f; private var earRV = 0f
    private var tail = 0f
    private var wagPhase = 0f
    private var happy = 0.4f

    // Face.
    private var blink = 1f; private var nextBlink = 3f
    private var eyeOpen = 1f
    private var eyeMode = 0f
    private var mouth = 0f

    // Legs.
    private val paws = floatArrayOf(0f, 1f, 0f, 1f)    // L raise, L reach, R raise, R reach
    private val pawsT = FloatArray(4)
    private var kick = 0f

    // Props.
    private var boneVis = 0f
    private var ballX = 0f; private var ballY = 0f; private var ballVy = 0f; private var ballVis = 0f
    private var ballState = 0                           // 0 none, 1 falling/rolling, 2 in mouth, 3 dropped
    private var ballSpin = 0f
    private var flyX = 0f; private var flyY = 0f; private var flyVis = 0f

    // Behaviour.
    private var act = Act.NONE
    private var actT = 0f
    private var nextIdle = 6f
    private val lastActs = ArrayDeque<Act>()
    private var greetedMorning = false
    private var awakeFor = 0f
    private var sustain = 0f
    private var twitchIn = 3f

    // Touch.
    private var touchDown = false
    private var downAt = 0f
    private var downOnBody = false
    private var tX = 0f; private var tY = 0f
    private var lastTX = 0f; private var lastTT = 0f
    private var moved = false
    private var longFired = false

    private val coat = FloatArray(3); private val light = FloatArray(3); private val collar = FloatArray(3)
    private val rim = FloatArray(3); private val glow = floatArrayOf(0.35f, 1f, 0.45f)
    private var colorsInit = false
    private var mood = Mood.DAY
    var sinceTouch = 99f; private set

    override val busy: Boolean
        get() = touchDown || sinceTouch < 2f || act != Act.NONE || abs(vx) > 0.01f || abs(vy) > 0.01f || ballState in 1..2

    override fun onSurfaceCreated() {
        val vert = context.assets.open("shaders/mochi.vert").bufferedReader().use { it.readText() }
        val frag = context.assets.open("shaders/kalboob.frag").bufferedReader().use { it.readText() }
        program = Gl.program(vert, frag)
        for (n in listOf("uQuad", "uRes", "uTime", "uPos", "uSize", "uPx", "uSquash", "uShear", "uLook", "uTilt", "uHeadY",
            "uLie", "uEyeOpen", "uEyeMode", "uMouth", "uEars", "uTail", "uPawL", "uPawR", "uKick", "uCoat", "uCoat2",
            "uCollar", "uRim", "uGlow", "uBone", "uBall", "uFx", "uFly", "uBattIcon", "uDim")) {
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

    override fun onSurfaceChanged(width: Int, height: Int) { w = max(1, width); h = max(1, height); layout() }

    private var ground = Float.NaN
    override fun setGround(y: Float) {
        if (y == ground || (y.isNaN() && ground.isNaN())) return
        ground = y; layout()
    }

    private fun layout() {
        val halfH = 0.5f * h / min(w, h)
        val halfW = 0.5f * w / min(w, h)
        size = min(0.62f, halfW * 2f * 0.62f)
        homeY = (if (ground.isNaN()) -halfH + 0.3f else ground) + 0.55f * size
        if (h.toFloat() / w < 1.5f) { size = min(0.42f, halfW * 0.8f); homeY = halfH - 0.26f - 0.6f * size }
        if (bottomInset > 0f) {
            val floor = -halfH + bottomInset * 2f * halfH + 0.06f
            size = min(size, (halfH - 0.08f - floor) / 1.3f)
            homeY = max(homeY, floor + 0.55f * size)
        }
    }

    private val bodyX get() = homeX + x
    private val bodyY get() = homeY + jumpY
    private val floorUv get() = homeY - 0.55f * size
    private val halfW get() = 0.5f * w / min(w, h)

    private fun toUv(nx: Float, ny: Float): Pair<Float, Float> {
        val m = min(w, h).toFloat()
        return Pair((nx - 0.5f) * w / m, (0.5f - ny) * h / m)
    }

    private fun onBody(ux: Float, uy: Float): Boolean {
        val cx = (ux - bodyX) / size; val cy = (uy - bodyY) / size
        return (cx / 0.42f) * (cx / 0.42f) + ((cy + 0.05f) / 0.6f) * ((cy + 0.05f) / 0.6f) < 1f
    }

    private fun discover(id: String) { if (reported.add(id)) listener?.onDiscovery(id) }
    private val reported = HashSet<String>()

    // ---- Touch ----

    override fun touchDown(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        tX = ux; tY = uy; lastTX = ux; lastTT = time
        moved = false; longFired = false; downAt = time; touchDown = true
        downOnBody = onBody(ux, uy)
        awakeFor = 10f
        lookAt(ux, uy)
        return downOnBody
    }

    override fun touchMove(nx: Float, ny: Float): Boolean {
        if (!touchDown) return false
        val (ux, uy) = toUv(nx, ny)
        val dt = max(1e-3f, time - lastTT)
        val v = (ux - lastTX) / dt
        if (hypot(ux - tX, uy - tY) > 0.03f) moved = true
        lookAt(ux, uy)
        if (abs(v) > 4f && act == Act.NONE && !isAsleep()) { startAct(Act.ZOOM); discover("zoomies") }
        if (act == Act.RUB && moved) { /* keep rubbing */ }
        lastTX = ux; lastTT = time; sinceTouch = 0f
        return downOnBody
    }

    override fun touchUp(): Boolean {
        if (!touchDown) return false
        touchDown = false; sinceTouch = 0f
        if (act == Act.RUB) actT = max(actT, 100f) // ends the rub
        val tap = !moved && time - downAt < 0.35f
        if (tap && downOnBody) { tapBody(); return true }
        return downOnBody
    }

    override fun tapAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f; awakeFor = 10f
        lookAt(ux, uy)
        if (onBody(ux, uy)) { tapBody(); return true }
        return false
    }

    override fun longPressAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f; awakeFor = 10f
        if (onBody(ux, uy)) { startAct(Act.RUB); return true }
        dropBall(ux, uy)
        return false
    }

    private fun tapBody() {
        listener?.onBondXp(1)
        happy = min(1f, happy + 0.3f)
        if (isAsleep()) { awakeFor = 8f; syV -= 2f; return }
        startAct(Act.WIGGLE)
        spawnFx(1f, (Random.nextFloat() - 0.5f) * 0.4f, 0.55f, 0.9f)
    }

    private fun dropBall(ux: Float, uy: Float) {
        if (ballState == 1 || ballState == 2) return
        // Keep the ball where Kalboob can reach it.
        val reach = (halfW - 0.45f * size).coerceAtLeast(0f)
        ballX = ux.coerceIn(homeX - reach, homeX + reach); ballY = max(uy, floorUv + 0.1f * size); ballVy = 0f; ballVis = 1f; ballState = 1
        startAct(Act.FETCH)
    }

    private fun lookAt(ux: Float, uy: Float) {
        lookTX = ((ux - bodyX) / size / 0.8f).coerceIn(-1f, 1f)
        lookTY = ((uy - bodyY - 0.2f * size) / size / 0.8f).coerceIn(-1f, 1f)
        // Curious head tilt when the finger is close to the face but not on it.
        val d = hypot(ux - bodyX, uy - (bodyY + 0.2f * size)) / size
        tiltT = if (d in 0.35f..1.1f) 0.3f * (if (ux > bodyX) 1f else -1f) else 0f
        nextWander = 3f + Random.nextFloat() * 2f
    }

    override fun greet() { awakeFor = 10f; startAct(Act.GREET) }

    private fun isAsleep() = mood == Mood.NIGHT && awakeFor <= 0f && act == Act.NONE && ballState == 0

    override fun perform(trick: String): Boolean {
        awakeFor = 12f; sinceTouch = 0f
        when (trick) {
            "fetch" -> { ballState = 0; dropBall(homeX + (if (Random.nextBoolean()) 0.6f else -0.6f) * size, homeY + 1.2f * size) }
            "dig" -> startAct(Act.DIG); "howl" -> startAct(Act.HOWL); "paw" -> startAct(Act.PAW)
            "belly" -> startAct(Act.RUB); "butterfly" -> startAct(Act.BUTTERFLY); "zoomies" -> startAct(Act.ZOOM)
            "yawn" -> startAct(Act.YAWN); "hello" -> startAct(Act.GREET)
            else -> return false
        }
        return true
    }

    private fun startAct(a: Act) {
        act = a; actT = 0f
        when (a) {
            Act.GREET -> { vy = 0.7f * size; spawnFx(6f, 0.25f, 0.55f, 0.9f); happy = 1f }
            Act.BUTTERFLY -> { flyX = -1.3f; flyY = 0.9f; flyVis = 1f }
            Act.RUB -> discover("belly_rub")
            Act.PAW -> discover("give_paw")
            Act.DIG -> discover("dig")
            Act.HOWL -> discover("howl")
            Act.ZOOM -> happy = 1f
            else -> {}
        }
    }

    private fun spawnFx(kind: Float, cx: Float, cy: Float, speed: Float, slot: Int = -1) {
        var i = slot
        if (i < 0) {
            i = 0; var best = -1f
            for (k in 0 until 5) { val life = if (fx[k * 4 + 3] == 0f) 2f else fx[k * 4 + 2]; if (life > best) { best = life; i = k } }
        }
        fx[i * 4] = cx; fx[i * 4 + 1] = cy; fx[i * 4 + 2] = 0f; fx[i * 4 + 3] = kind
        fxSpeed[i] = speed
        fxVel[i * 2] = (Random.nextFloat() - 0.5f) * 0.12f
        fxVel[i * 2 + 1] = if (kind == 3f || kind == 7f) 0f else 0.25f
    }

    // ---- Frame ----

    override fun update(dt: Float, input: SceneInput) {
        time += dt; sinceTouch += dt; awakeFor -= dt; actT += dt
        mood = input.mood
        val a = input.audio
        val musicOn = a.source != AudioFrame.Source.NONE
        val charging = input.charging
        val low = input.lowBattery && !charging
        val asleep = isAsleep()

        if (mood == Mood.MORNING && !greetedMorning && act == Act.NONE) { greetedMorning = true; startAct(Act.STRETCH) }
        if (mood != Mood.MORNING) greetedMorning = false

        // Colours.
        val c = PUP_COATS[input.coat.coerceIn(0, PUP_COATS.size - 1)]
        val k = if (colorsInit) dt * 4f else 1f
        ease(coat, c.fur, k); ease(light, c.light, k); ease(collar, c.collar, k)
        ease(rim, if (input.accent != 0) input.accent else 0xFFFFD9A8.toInt(), k)
        ease(glow, if (musicOn) Color.HSVToColor(floatArrayOf((120f + time * 40f) % 360f, 0.7f, 1f)) else Color.rgb(90, 255, 115), dt * 3f)
        colorsInit = true

        // Long press from the raw touch stream.
        if (touchDown && !moved && !longFired && time - downAt > 0.6f) {
            longFired = true
            if (downOnBody) startAct(Act.RUB) else dropBall(tX, tY)
        }

        // Music: sustained mids make Kalboob howl.
        if (musicOn && !asleep) {
            sustain = if (a.mid > 0.55f) sustain + dt else max(0f, sustain - dt * 2f)
            if (sustain > 1.2f && act == Act.NONE) { startAct(Act.HOWL); sustain = 0f }
            if (a.beat >= 0.99f) { earLV += 3f + a.bass * 4f; earRV += 3f + a.bass * 4f; syV -= 1.2f }
        } else sustain = 0f

        // Idle actions.
        if (act == Act.NONE && !asleep && !charging && !low && !touchDown && sinceTouch > 3f && ballState == 0) {
            nextIdle -= dt
            if (nextIdle <= 0f) {
                val pool = mutableListOf(Act.SNIFF, Act.DIG, Act.BUTTERFLY, Act.YAWN, Act.PAW)
                pool.removeAll(lastActs)
                val pick = pool.random()
                lastActs.addLast(pick); if (lastActs.size > 2) lastActs.removeFirst()
                startAct(pick)
                nextIdle = 7f + Random.nextFloat() * 7f
            }
        }

        // ---- Pose targets ----
        var lieT = 0f
        var eT = 0f
        var mT = if (happy > 0.6f) 1f else 0f
        var openT = 1f
        var headYT = 0f
        var earT = 0f
        var wagRate = 3f + happy * 9f
        var wagAmp = 0.15f + happy * 0.45f
        var tailBase = 0.25f
        var kickT = 0f
        var boneT = 0f
        var targetX = 0f
        setPaws(0f, 1f, 0f, 1f)

        if (charging) {
            lieT = 0.35f; boneT = 1f; setPaws(0.5f, 0.9f, 0.5f, 0.9f)
            val chew = sin(time * 9f)
            mT = if (chew > 0f) 5f else 0f
            headYT = -0.03f + chew * 0.012f
            eT = if (sin(time * 0.7f) > 0.3f) 4f else 0f
            lookTY = -0.6f; lookTX = 0f
            wagAmp = 0.3f; wagRate = 5f
        } else if (low) {
            lieT = 1f; eT = 2f; earT = 0.5f; tailBase = -0.6f; wagAmp = 0f; mT = 0f; headYT = -0.03f
        } else if (asleep) {
            lieT = 1f; eT = 3f; openT = 0f; mT = 0f; wagAmp = 0f; tailBase = -0.5f; earT = 0.35f
            if (fx[23] == 0f || fx[22] >= 1f) spawnFx(5f, 0.3f, 0.45f, 0.3f, slot = 5)
            // Dreaming: a paw paddles, an ear flicks, the tail thumps.
            twitchIn -= dt
            if (twitchIn <= 0f) { twitchIn = 2f + Random.nextFloat() * 4f; earLV += 6f; tail += 0.4f }
            if (twitchIn > 0.2f && twitchIn < 0.6f) setPaws(0.25f * sin(time * 30f), 1f, 0f, 1f)
        }

        // Head follows gaze; curiosity tilt.
        if (musicOn && !asleep && act == Act.NONE) { headYT += 0.015f * sin(time * a.bpm.coerceAtLeast(60f) / 60f * 2f * PI.toFloat()) }

        when (act) {
            Act.WIGGLE -> {
                shear = sin(actT * 30f) * 0.07f * (1f - actT / 0.9f).coerceAtLeast(0f)
                eT = 1f; mT = 1f; wagRate = 16f; wagAmp = 0.7f
                if (actT > 0.9f) { act = Act.NONE; shear = 0f }
            }
            Act.RUB -> {
                lieT = 1f; eT = 4f; mT = 1f; tiltT = -0.25f
                kickT = 0.5f + 0.5f * sin(actT * 18f)
                wagRate = 12f; wagAmp = 0.5f
                if (actT > 0.5f && fx[3] == 0f) spawnFx(1f, 0.05f, 0.5f, 0.7f, slot = 0)
                if (actT > 100.8f || (!touchDown && actT > 3f && actT < 100f)) { act = Act.NONE; happy = 1f; listener?.onBondXp(3) }
            }
            Act.FETCH -> {
                if (ballState == 3) {
                    lookAt(ballX, ballY); eT = 1f; mT = 1f; wagRate = 16f; wagAmp = 0.7f
                } else if (ballState == 1) {
                    targetX = (ballX - homeX).coerceIn(-halfW + 0.45f * size, halfW - 0.45f * size)
                    lookAt(ballX, ballY)
                    mT = 1f; wagRate = 14f; wagAmp = 0.6f
                    if (abs(bodyX - ballX) < 0.2f * size && ballY < floorUv + 0.2f * size && ballState == 1) {
                        ballState = 2; syV -= 2f
                    }
                    if (actT > 8f) { ballState = 3; actT = 0f } // gives up; never gets stuck
                } else if (ballState == 2) {
                    targetX = 0f
                    mT = 5f; wagRate = 14f; wagAmp = 0.6f
                    if (abs(x) < 0.03f) {
                        ballState = 3; ballX = bodyX + 0.0f; ballY = floorUv + 0.07f * size; ballVy = 0.6f
                        spawnFx(1f, 0f, 0.6f, 0.8f); happy = 1f
                        discover("fetch"); listener?.onBondXp(5)
                        actT = 0f
                    }
                }
                if (ballState == 3 && actT > 2.5f) { act = Act.NONE }
            }
            Act.SNIFF -> {
                headYT = -0.07f; lookTY = -0.9f; lookTX = 0.3f * sin(actT * 1.5f)
                headYT += 0.006f * sin(actT * 40f)
                if ((actT * 3f).toInt() != ((actT - dt) * 3f).toInt()) spawnFx(7f, lookTX * 0.05f, 0.02f, 1.6f)
                if (actT > 3f) act = Act.NONE
            }
            Act.DIG -> {
                lieT = 0.3f; headYT = -0.05f; lookTY = -0.8f
                val ph = sin(actT * 16f)
                setPaws(0.35f + 0.35f * ph, 0.85f, 0.35f - 0.35f * ph, 0.85f)
                wagRate = 10f; wagAmp = 0.5f
                if ((actT * 4f).toInt() != ((actT - dt) * 4f).toInt()) spawnFx(3f, 0f, -0.5f, 1.4f)
                if (actT > 2.8f) act = Act.NONE
            }
            Act.BUTTERFLY -> {
                // Flutters in on a wavy path, circles the head, lands on the nose, then leaves.
                val tx: Float; val ty: Float
                when {
                    actT < 2.2f -> { tx = -0.4f + 0.4f * sin(actT * 2f); ty = 0.75f + 0.12f * sin(actT * 5f) }
                    actT < 4.5f -> { tx = 0.3f * sin(actT * 2.6f); ty = 0.55f + 0.15f * sin(actT * 3.3f) }
                    actT < 6.3f -> { tx = 0f; ty = 0.12f }
                    else -> { tx = 1.4f; ty = 1.2f }
                }
                flyX += (tx - flyX) * min(1f, dt * 2.5f); flyY += (ty - flyY) * min(1f, dt * 2.5f)
                lookTX = (flyX / 0.8f).coerceIn(-1f, 1f); lookTY = ((flyY - 0.2f) / 0.8f).coerceIn(-1f, 1f)
                tiltT = 0.25f * sin(actT * 1.7f)
                wagRate = 12f; wagAmp = 0.5f
                if (actT > 3f && actT - dt <= 3f && jumpY == 0f) vy = 0.55f * size
                if (actT > 4.5f && actT < 6.3f) { eT = 0f; lookTX = 0f; lookTY = -0.2f; mT = 0f; discover("butterfly") }
                if (actT > 8f) { act = Act.NONE; flyVis = 0f }
            }
            Act.YAWN -> {
                val y = smooth(actT, 0.2f, 0.9f, 1.8f)
                mT = if (y > 0.3f) 4f else mT; eT = if (y > 0.5f) 3f else 0f; openT = 1f - y
                earT = 0.4f * y; headYT = 0.03f * y; lookTY = 0.5f * y
                if (actT > 2.4f) act = Act.NONE
            }
            Act.PAW -> {
                val r = smooth(actT, 0.2f, 0.5f, 2.2f)
                setPaws(0f, 1f, 1.35f * r, 1f + 0.05f * r)
                eT = if (r > 0.5f) 1f else 0f; mT = 1f; tiltT = 0.2f * r
                wagRate = 12f; wagAmp = 0.5f
                if (actT > 2.7f) act = Act.NONE
            }
            Act.HOWL -> {
                headYT = 0.05f; lookTY = 1f; lookTX = 0f; tiltT = 0f
                mT = 3f; eT = 3f; openT = 0f; earT = 0.45f
                if ((actT * 2.5f).toInt() != ((actT - dt) * 2.5f).toInt()) spawnFx(4f, 0.12f, 0.55f, 0.6f)
                if (actT > 2.6f) act = Act.NONE
            }
            Act.STRETCH -> {
                val s = smooth(actT, 0.3f, 1.0f, 2.2f)
                lieT = 0.55f * s; setPaws(0.6f * s, 1f + 0.2f * s, 0.6f * s, 1f + 0.2f * s)
                mT = if (s > 0.5f) 4f else 0f; eT = if (s > 0.5f) 3f else 0f; openT = 1f - s
                tailBase = 0.25f + 0.4f * s; wagRate = 8f
                if (actT > 2.8f) act = Act.NONE
            }
            Act.GREET -> {
                mT = if (sin(actT * 14f) > 0f) 2f else 1f; eT = 1f
                wagRate = 18f; wagAmp = 0.7f
                if (actT > 1.6f) act = Act.NONE
            }
            Act.ZOOM -> {
                // A quick dash to one side and back.
                targetX = (if (actT < 0.8f) 0.55f else if (actT < 1.6f) -0.55f else 0f) * (halfW - 0.45f * size).coerceAtLeast(0f) / 0.55f
                mT = 1f; eT = 1f; wagRate = 18f; wagAmp = 0.7f
                if (actT > 2.4f) act = Act.NONE
            }
            Act.NONE -> {}
        }

        // Ball physics (in uv).
        if (ballState == 1 || ballState == 3) {
            ballVy -= 3.4f * dt
            ballY += ballVy * dt
            val fl = floorUv + 0.07f * size
            if (ballY < fl) { ballY = fl; ballVy = -ballVy * 0.5f; if (abs(ballVy) < 0.15f) ballVy = 0f }
            ballSpin += dt * 3f
        }
        if (ballState == 2) {
            // In the mouth.
            ballX = bodyX + lookX * 0.04f * size; ballY = bodyY + (mix(0.2f, -0.08f, lie) + headY - 0.15f) * size
        }
        if (ballState == 3 && act != Act.FETCH) { ballVis = max(0f, ballVis - dt * 0.8f); if (ballVis == 0f) ballState = 0 }

        // Locomotion.
        vx += ((targetX - x) * 7f - vx * 4f) * dt
        x += vx * dt
        facing += ((vx * 1.5f).coerceIn(-0.3f, 0.3f) - facing) * min(1f, dt * 8f)
        if (jumpY > 0f || vy > 0f) {
            vy -= 4.2f * size * dt; jumpY += vy * dt
            if (jumpY <= 0f) { jumpY = 0f; if (vy < -0.3f * size) syV -= 3f; vy = 0f }
            earLV -= vy * dt * 60f; earRV -= vy * dt * 60f
        }
        // Running bounce.
        if (abs(vx) > 0.15f && jumpY == 0f && sin(time * 18f) > 0.9f) vy = 0.12f * size

        happy = max(0.35f, happy - dt * 0.04f)

        // Gaze wander.
        nextWander -= dt
        if (nextWander <= 0f && !touchDown && act == Act.NONE) {
            lookTX = (Random.nextFloat() - 0.5f) * 1.2f; lookTY = (Random.nextFloat() - 0.4f) * 0.8f
            tiltT = if (Random.nextFloat() < 0.3f) (Random.nextFloat() - 0.5f) * 0.5f else 0f
            nextWander = 2.5f + Random.nextFloat() * 3f
        }
        lookX += (lookTX - lookX) * min(1f, dt * 6f); lookY += (lookTY - lookY) * min(1f, dt * 6f)
        tilt += (tiltT + facing * 0.5f - tilt) * min(1f, dt * 6f)
        headY += (headYT - headY) * min(1f, dt * 10f)
        lie += (lieT - lie) * min(1f, dt * 5f)
        kick += (kickT - kick) * min(1f, dt * 20f)
        boneVis += (boneT - boneVis) * min(1f, dt * 5f)

        // Ear springs: flop with motion, tilt and the target droop.
        val earTL = earT - tilt * 0.6f
        val earTR = earT + tilt * 0.6f
        earLV += ((earTL - earL) * 90f - earLV * 7f) * dt; earL += earLV * dt
        earRV += ((earTR - earR) * 90f - earRV * 7f) * dt; earR += earRV * dt
        earL = earL.coerceIn(-0.6f, 0.9f); earR = earR.coerceIn(-0.6f, 0.9f)

        // Tail: wag, or locked to the music's beat.
        if (musicOn && !asleep && wagAmp > 0f) {
            val beatPhase = time * a.bpm.coerceAtLeast(60f) / 60f
            tail += (tailBase + sin(beatPhase * PI.toFloat()) * 0.6f - tail) * min(1f, dt * 14f)
        } else {
            wagPhase += dt * wagRate
            tail += (tailBase + sin(wagPhase) * wagAmp - tail) * min(1f, dt * 14f)
        }

        // Blink and breathing.
        nextBlink -= dt
        if (nextBlink <= 0f) { blink = 0f; nextBlink = 2.5f + Random.nextFloat() * 4f }
        blink = min(1f, blink + dt * 9f)
        eyeOpen += (min(openT, blink) - eyeOpen) * min(1f, dt * 20f)
        eyeMode = eT; mouth = mT
        val breathRate = if (asleep) 0.5f else if (mT == 1f) 3.2f else 1.3f
        val syTarget = 1f + (if (mT == 1f) 0.012f else 0.01f) * sin(time * breathRate * 2f * PI.toFloat())
        syV += ((syTarget - sy) * 170f - syV * 9f) * dt; sy = (sy + syV * dt).coerceIn(0.8f, 1.2f)

        for (i in 0 until 4) paws[i] += (pawsT[i] - paws[i]) * min(1f, dt * 12f)

        // Butterfly fades out when not in its act.
        if (act != Act.BUTTERFLY) flyVis = max(0f, flyVis - dt * 2f)

        for (i in 0 until 6) {
            if (fx[i * 4 + 3] == 0f) continue
            fx[i * 4 + 2] += fxSpeed[i] * dt
            fx[i * 4] += fxVel[i * 2] * dt; fx[i * 4 + 1] += fxVel[i * 2 + 1] * dt
            if (fx[i * 4 + 2] >= 1f) fx[i * 4 + 3] = 0f
        }
    }

    private fun setPaws(lr: Float, ll: Float, rr: Float, rl: Float) { pawsT[0] = lr; pawsT[1] = ll; pawsT[2] = rr; pawsT[3] = rl }
    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun smooth(t: Float, a: Float, b: Float, c: Float): Float = when {
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
        var minX = bx - 0.62f * size; var maxX = bx + 0.62f * size
        var minY = by - 0.65f * size; var maxY = by + 1.0f * size
        if (act == Act.BUTTERFLY) { minX = bx - 1.6f * size; maxX = bx + 1.6f * size; maxY = by + 1.5f * size }
        if (ballVis > 0f) {
            minX = min(minX, ballX - 0.1f * size); maxX = max(maxX, ballX + 0.1f * size)
            minY = min(minY, ballY - 0.1f * size); maxY = max(maxY, ballY + 0.1f * size)
        }
        GLES20.glUniform4f(u("uQuad"), minX, minY, maxX, maxY)
        GLES20.glUniform2f(u("uRes"), w.toFloat(), h.toFloat())
        GLES20.glUniform1f(u("uTime"), time % 600f)
        GLES20.glUniform2f(u("uPos"), bx, by)
        GLES20.glUniform1f(u("uSize"), size)
        GLES20.glUniform1f(u("uPx"), 1f / (size * min(w, h)))
        GLES20.glUniform2f(u("uSquash"), 1f / sy, sy)
        GLES20.glUniform1f(u("uShear"), shear + facing * 0.15f)
        GLES20.glUniform2f(u("uLook"), lookX, lookY)
        GLES20.glUniform1f(u("uTilt"), tilt)
        GLES20.glUniform1f(u("uHeadY"), headY)
        GLES20.glUniform1f(u("uLie"), lie)
        GLES20.glUniform1f(u("uEyeOpen"), eyeOpen)
        GLES20.glUniform1f(u("uEyeMode"), eyeMode)
        GLES20.glUniform1f(u("uMouth"), mouth)
        GLES20.glUniform2f(u("uEars"), earL, earR)
        GLES20.glUniform1f(u("uTail"), tail)
        GLES20.glUniform2f(u("uPawL"), paws[0], paws[1])
        GLES20.glUniform2f(u("uPawR"), paws[2], paws[3])
        GLES20.glUniform1f(u("uKick"), kick)
        GLES20.glUniform3f(u("uCoat"), coat[0], coat[1], coat[2])
        GLES20.glUniform3f(u("uCoat2"), light[0], light[1], light[2])
        GLES20.glUniform3f(u("uCollar"), collar[0], collar[1], collar[2])
        GLES20.glUniform3f(u("uRim"), rim[0], rim[1], rim[2])
        GLES20.glUniform3f(u("uGlow"), glow[0], glow[1], glow[2])
        GLES20.glUniform4f(u("uBone"), 0f, mix(-0.34f, -0.36f, lie), boneVis, input.battery)
        GLES20.glUniform4f(u("uBall"), (ballX - bx) / size, (ballY - by) / size, ballVis, ballSpin)
        fxBuf.position(0); fxBuf.put(fx); fxBuf.position(0)
        GLES20.glUniform4fv(u("uFx"), 6, fxBuf)
        GLES20.glUniform4f(u("uFly"), flyX, flyY, time * 18f, flyVis)
        GLES20.glUniform1f(u("uBattIcon"), if (input.charging) boneVis else 0f)
        GLES20.glUniform1f(u("uDim"), when { input.mood == Mood.NIGHT -> 0.35f; input.lowBattery -> 0.3f; else -> 0f })
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
