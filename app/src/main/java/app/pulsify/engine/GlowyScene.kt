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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** A Glowy variant: body, belly, fins, lure glow, and how see-through it is. */
data class GlowyCoat(val name: String, val body: Int, val belly: Int, val fin: Int, val glow: Int, val ghost: Float, val kind: Int = 0)

val GLOWY_COATS = listOf(
    GlowyCoat("Amber", 0xFFF28C33.toInt(), 0xFFFFCC73.toInt(), 0xFFE6663F.toInt(), 0xFFFFD966.toInt(), 0f),
    GlowyCoat("Ghost", 0xFF59BFF2.toInt(), 0xFFCCF2FF.toInt(), 0xFF66B3FF.toInt(), 0xFF80F2FF.toInt(), 1f),
    GlowyCoat("Orchid", 0xFF9959E6.toInt(), 0xFFF2B3FF.toInt(), 0xFFB366FF.toInt(), 0xFF66FF80.toInt(), 0f),
    GlowyCoat("Coral", 0xFFFF6680.toInt(), 0xFFFFC2B3.toInt(), 0xFFFF8C66.toInt(), 0xFF66E6FF.toInt(), 0.35f),
    // Other fish: same personality and tricks, different bodies.
    GlowyCoat("Clownfish", 0xFFFF7417.toInt(), 0xFFFFA24D.toInt(), 0xFFFF8A2E.toInt(), 0xFFFFE0B3.toInt(), 0f, kind = 1),
    GlowyCoat("Betta", 0xFF2E4BFF.toInt(), 0xFF9D3CFF.toInt(), 0xFFFF2E6E.toInt(), 0xFF8CB8FF.toInt(), 0.15f, kind = 2),
    GlowyCoat("Pufferfish", 0xFFE3C35E.toInt(), 0xFFFFF4DB.toInt(), 0xFFC99A3E.toInt(), 0xFFFFE680.toInt(), 0f, kind = 3),
    GlowyCoat("Koi", 0xFFFF5A1F.toInt(), 0xFFFFFBF5.toInt(), 0xFFFFE8DC.toInt(), 0xFFFFB38C.toInt(), 0.1f, kind = 4),
    GlowyCoat("Neon Betta", 0xFFFF2E9A.toInt(), 0xFF35E0FF.toInt(), 0xFF8A2EFF.toInt(), 0xFF7DFFF2.toInt(), 0.25f, kind = 2),
)

/**
 * Glowy, the anglerfish. Swims the whole screen. Its lure follows your finger on a springy stalk,
 * little fish gather where you touch and it gulps them, a tap flashes the lure, and the lure pulses
 * with the bass.
 */
class GlowyScene(private val context: Context) : Creature {

    override var listener: SceneListener? = null

    private enum class Act { NONE, GULP, PUFF, YAWN, BUBBLES, LOOP, STARE, NIBBLE }

    private var program = 0
    private var quadVbo = 0
    private val uni = HashMap<String, Int>()
    private val fxBuf: FloatBuffer = ByteBuffer.allocateDirect(24 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val preyBuf: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val fx = FloatArray(24)          // world x, y, life, kind
    private val fxSpeed = FloatArray(6)
    private val fxVel = FloatArray(12)
    private val fxOut = FloatArray(24)
    private val prey = FloatArray(16)        // world x, y, dir, visible
    private val preyT = FloatArray(8)        // target offsets
    private val preyOut = FloatArray(16)
    private var preyLife = 0f

    private var w = 1; private var h = 1
    private var time = 0f
    private var size = 0.5f
    override var bottomInset = 0f
        set(v) { field = v.coerceIn(0f, 0.8f) }

    private var x = 0f; private var y = 0f; private var vx = 0f; private var vy = 0f
    private var tx = 0f; private var ty = 0f; private var nextWander = 0f
    private var face = 1f; private var faceT = 1f
    private var pitch = 0f; private var loop = 0f
    private var sq = 1f; private var sqV = 0f
    private var puff = 0f; private var mouth = 0f
    private var eyeOpen = 1f; private var eyeMode = 0f; private var lookX = 0f; private var lookY = 0f
    private var finPh = 0f; private var finAmp = 0.35f; private var tailPh = 0f; private var tailAmp = 0.25f
    private var lx = 0f; private var ly = 0.3f; private var lvx = 0f; private var lvy = 0f
    private var lureB = 1f; private var flash = 0f
    private var charge = 0f; private var spots = 1f
    private var blink = 1f; private var nextBlink = 3f

    private var kind = 0
    private var act = Act.NONE
    private var actT = 0f
    private var nextIdle = 3f
    private var lastAct = Act.NONE
    private var mood = Mood.DAY
    var sinceTouch = 99f; private set

    private var touchDown = false
    private var downAt = 0f
    private var downOnBody = false
    private var fX = 0f; private var fY = 0f
    private var moved = false
    private var longFired = false
    private var following = false

    private val cBody = FloatArray(3); private val cBelly = FloatArray(3); private val cFin = FloatArray(3); private val cGlow = FloatArray(3)
    private var ghost = 0f
    private var colorsInit = false

    override val busy: Boolean
        get() = touchDown || sinceTouch < 2f || act != Act.NONE || preyLife > 0f || flash > 0.02f

    override fun onSurfaceCreated() {
        val vert = context.assets.open("shaders/mochi.vert").bufferedReader().use { it.readText() }
        val frag = context.assets.open("shaders/glowy.frag").bufferedReader().use { it.readText() }
        program = Gl.program(vert, frag)
        for (n in listOf("uQuad", "uRes", "uTime", "uPos", "uSize", "uPx", "uFace", "uPitch", "uSquash", "uPuff", "uMouth", "uEye",
            "uFin", "uTail", "uLure", "uCharge", "uBody", "uBelly", "uFinC", "uGlow", "uSpots", "uGhost", "uPrey", "uFx", "uDim", "uKind")) {
            uni[n] = GLES20.glGetUniformLocation(program, n)
        }
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0); quadVbo = ids[0]
        val q = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        val fb = ByteBuffer.allocateDirect(q.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(q)
        fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, q.size * 4, fb, GLES20.GL_STATIC_DRAW)
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        w = max(1, width); h = max(1, height)
        size = min(0.5f, halfW * 2f * 0.5f)
        if (h.toFloat() / w < 1.5f) size = min(0.36f, halfH * 0.7f)
    }

    private val halfW get() = 0.5f * w / min(w, h)
    private val halfH get() = 0.5f * h / min(w, h)
    private val minX get() = -halfW + 0.42f * size
    private val maxX get() = halfW - 0.42f * size
    private val minY get() = -halfH + bottomInset * 2f * halfH + 0.45f * size
    private val maxY get() = halfH - 0.75f * size

    private fun toUv(nx: Float, ny: Float): Pair<Float, Float> {
        val m = min(w, h).toFloat()
        return Pair((nx - 0.5f) * w / m, (0.5f - ny) * h / m)
    }
    private fun onBody(ux: Float, uy: Float) = hypot(ux - x, uy - y) < 0.36f * size

    // ---- Touch ----

    override fun touchDown(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        fX = ux; fY = uy; moved = false; longFired = false; downAt = time; touchDown = true
        downOnBody = onBody(ux, uy)
        if (!downOnBody) { following = true; if (preyLife <= 0f && act == Act.NONE) spawnPrey(ux, uy) }
        return true
    }

    override fun touchMove(nx: Float, ny: Float): Boolean {
        if (!touchDown) return false
        val (ux, uy) = toUv(nx, ny)
        if (hypot(ux - fX, uy - fY) > 0.03f) moved = true
        fX = ux; fY = uy
        sinceTouch = 0f
        return true
    }

    override fun touchUp(): Boolean {
        if (!touchDown) return false
        touchDown = false; following = false; sinceTouch = 0f
        if (act == Act.PUFF && actT > 0.4f) actT = max(actT, 1.6f)
        if (!moved && time - downAt < 0.35f) { if (downOnBody) tapBody() else flashLure() }
        return true
    }

    override fun tapAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        if (onBody(ux, uy)) tapBody() else { flashLure(); if (preyLife <= 0f) spawnPrey(ux, uy) }
        return true
    }

    override fun longPressAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        if (onBody(ux, uy)) startAct(Act.PUFF) else spawnPrey(ux, uy)
        return true
    }

    private fun tapBody() {
        flashLure()
        sqV -= 3f; puff = max(puff, 0.5f)
        spawnFx(5f, x + face * 0.1f * size, y + 0.5f * size, 0.9f)
        for (i in 0 until 2) spawnFx(1f, x + face * 0.3f * size, y - 0.05f * size, 0.5f)
    }

    private fun flashLure() { flash = 1f; lvy += 0.6f * size }

    private fun spawnPrey(ux: Float, uy: Float) {
        preyLife = 10f
        for (i in 0 until 4) {
            val left = Random.nextBoolean()
            prey[i * 4] = if (left) -halfW - 0.1f else halfW + 0.1f
            prey[i * 4 + 1] = uy + (Random.nextFloat() - 0.5f) * 0.6f
            prey[i * 4 + 2] = if (left) 1f else -1f
            prey[i * 4 + 3] = if (i < 3 || Random.nextBoolean()) 1f else 0f
            preyT[i * 2] = (Random.nextFloat() - 0.5f) * 0.22f; preyT[i * 2 + 1] = (Random.nextFloat() - 0.5f) * 0.16f
        }
        fX = ux; fY = uy
    }

    override fun swiped(dir: Float, fast: Boolean) { if (!fast || !perform("puff")) greet() }

    override fun greet() { flashLure(); startAct(Act.LOOP) }

    override fun perform(trick: String): Boolean {
        sinceTouch = 0f
        when (trick) {
            "flash" -> flashLure()
            "puff" -> startAct(Act.PUFF)
            "yawn" -> startAct(Act.YAWN)
            "bubbles" -> startAct(Act.BUBBLES)
            "loop" -> startAct(Act.LOOP)
            "feed" -> spawnPrey(x + face * 0.7f * size, (y - 0.2f * size).coerceIn(minY, maxY))
            "stare" -> startAct(Act.STARE)
            else -> return false
        }
        return true
    }

    private fun isAsleep() = mood == Mood.NIGHT && act == Act.NONE && sinceTouch > 6f && preyLife <= 0f

    private fun startAct(a: Act) { act = a; actT = 0f; lastAct = a }

    private fun spawnFx(kind: Float, wx: Float, wy: Float, speed: Float) {
        var i = 0; var best = -1f
        for (k in 0 until 6) { val life = if (fx[k * 4 + 3] == 0f) 2f else fx[k * 4 + 2]; if (life > best) { best = life; i = k } }
        fx[i * 4] = wx; fx[i * 4 + 1] = wy; fx[i * 4 + 2] = 0f; fx[i * 4 + 3] = kind
        fxSpeed[i] = speed
        fxVel[i * 2] = (Random.nextFloat() - 0.5f) * 0.06f
        fxVel[i * 2 + 1] = if (kind == 1f) 0.16f + Random.nextFloat() * 0.1f else 0.12f
    }

    // ---- Frame ----

    override fun update(dt: Float, input: SceneInput) {
        time += dt; sinceTouch += dt; actT += dt
        mood = input.mood
        val a = input.audio
        val musicOn = a.source != AudioFrame.Source.NONE
        val charging = input.charging
        val low = input.lowBattery && !charging
        val asleep = isAsleep()

        val coat = GLOWY_COATS[input.coat.coerceIn(0, GLOWY_COATS.size - 1)]
        val k = if (colorsInit) dt * 4f else 1f
        ease(cBody, coat.body, k); ease(cBelly, coat.belly, k); ease(cFin, coat.fin, k)
        ease(cGlow, if (musicOn) Color.HSVToColor(floatArrayOf((time * 50f) % 360f, 0.6f, 1f)) else if (charging) Color.rgb(90, 255, 115) else coat.glow, if (colorsInit) dt * 3f else 1f)
        ghost += (coat.ghost - ghost) * min(1f, k)
        kind = coat.kind
        colorsInit = true

        if (touchDown && !moved && !longFired && time - downAt > 0.6f) {
            longFired = true
            if (downOnBody) startAct(Act.PUFF)
        }

        // Idle tricks.
        if (act == Act.NONE && !touchDown && sinceTouch > 3f && preyLife <= 0f && !asleep) {
            nextIdle -= dt
            if (nextIdle <= 0f) {
                val pick = when {
                    charging -> Act.NIBBLE
                    low -> Act.YAWN
                    else -> listOf(Act.YAWN, Act.BUBBLES, Act.LOOP, Act.PUFF, Act.STARE).filter { it != lastAct }.random()
                }
                startAct(pick)
                nextIdle = TRICK_GAP_MIN + Random.nextFloat() * TRICK_GAP_RANGE
            }
        }

        // ---- Where to swim ----
        var speed = 0.22f
        var puffT = 0f; var mouthT = 0f; var eT = 0f; var openT = 1f
        var lkx = 0.2f; var lky = 0.1f
        var lureRX = 0.36f; var lureRY = 0.44f
        var brightT = 1f; var chargeT = 0f; var spotsT = 0.8f
        var loopT = 0f
        var preyGathered = false

        if (preyLife > 0f) {
            // Chase the little fish.
            var cx = 0f; var cy = 0f; var n = 0
            for (i in 0 until 4) if (prey[i * 4 + 3] > 0.5f) { cx += prey[i * 4]; cy += prey[i * 4 + 1]; n++ }
            if (n > 0) {
                cx /= n; cy /= n
                faceT = if (cx > x) 1f else -1f
                tx = cx - faceT * 0.34f * size; ty = cy + 0.06f * size
                speed = 0.5f
                lkx = 0.9f; lky = -0.3f; eT = 3f; mouthT = 0.15f
                val mx = x + face * 0.3f * size; val my = y - 0.06f * size
                if (hypot(cx - mx, cy - my) < 0.2f * size && act != Act.GULP && abs(face - faceT) < 0.3f) { startAct(Act.GULP) }
                preyGathered = true
            } else preyLife = 0f
        } else if (following && !downOnBody) {
            // Curious about your finger: hover so the lure can reach it.
            faceT = if (fX > x + 0.03f) 1f else if (fX < x - 0.03f) -1f else faceT
            tx = fX - faceT * 0.3f * size; ty = fY - 0.42f * size
            speed = 0.32f
            lkx = ((fX - x) / size * face).coerceIn(-1f, 1f); lky = ((fY - y) / size).coerceIn(-1f, 1f)
        } else {
            nextWander -= dt
            if (nextWander <= 0f || hypot(tx - x, ty - y) < 0.05f) {
                nextWander = 4f + Random.nextFloat() * 5f
                tx = minX + Random.nextFloat() * (maxX - minX)
                ty = minY + Random.nextFloat() * (maxY - minY)
            }
            if (asleep || low) { ty = minY + 0.05f; speed = 0.05f }
            if (charging) { tx = 0f; ty = (minY + maxY) * 0.5f; speed = 0.12f }
            if (abs(tx - x) > 0.05f) faceT = sign(tx - x)
        }

        if (asleep) { eT = 2f; brightT = 0.35f + 0.15f * sin(time * 1.2f); spotsT = 0.3f
            if ((time * 0.35f).toInt() != ((time - dt) * 0.35f).toInt()) spawnFx(4f, x + face * 0.2f * size, y + 0.35f * size, 0.3f) }
        if (low) { openT = 0.5f; brightT = if (sin(time * 13f) + sin(time * 7.3f) > 0.6f) 0.9f else 0.25f; spotsT = 0.2f }
        if (charging) {
            chargeT = 1f; lureRX = 0.52f; lureRY = 0.16f; lkx = 1f; lky = 0.2f; eT = if (act == Act.NIBBLE) 1f else 3f
            brightT = 0.9f + 0.3f * sin(time * 4f)
        }
        if (musicOn && !asleep) {
            brightT = 0.5f + 1.3f * a.bass
            spotsT = 0.4f + 1.2f * a.treble
            finAmp += (0.3f + 0.7f * a.mid - finAmp) * min(1f, dt * 10f)
            if (a.beat >= 0.99f) sqV -= 1.6f
        } else finAmp += (0.35f - finAmp) * min(1f, dt * 4f)

        when (act) {
            Act.GULP -> {
                mouthT = if (actT < 0.35f) 1f else 0f
                speed = 0.9f; eT = if (actT < 0.35f) 3f else 1f
                // Suck the fish in.
                val mx = x + face * 0.22f * size; val my = y - 0.06f * size
                for (i in 0 until 4) if (prey[i * 4 + 3] > 0f) {
                    prey[i * 4] += (mx - prey[i * 4]) * min(1f, dt * 12f); prey[i * 4 + 1] += (my - prey[i * 4 + 1]) * min(1f, dt * 12f)
                    if (actT > 0.25f) prey[i * 4 + 3] = max(0f, prey[i * 4 + 3] - dt * 8f)
                }
                if (actT > 0.4f && actT - dt <= 0.4f) { sqV -= 3.5f; preyLife = 0f; spawnFx(1f, mx, my, 0.5f); spawnFx(3f, x, y + 0.45f * size, 0.8f) }
                if (actT > 0.9f && actT - dt <= 0.9f) spawnFx(1f, mx, my, 0.45f)
                if (actT > 1.3f) act = Act.NONE
            }
            Act.PUFF -> {
                val hold = touchDown && downOnBody
                puffT = if (actT < 1.6f || hold) 1f else 0f
                eT = 3f; mouthT = if (puffT > 0.5f) 0.12f else 0.3f
                if (puffT < 0.5f && (actT * 6f).toInt() != ((actT - dt) * 6f).toInt()) spawnFx(1f, x + face * 0.3f * size, y - 0.05f * size, 0.55f)
                if (!hold && actT > 2.4f) act = Act.NONE
            }
            Act.YAWN -> {
                val s = tri(actT, 0.5f, 1.5f, 2.1f)
                mouthT = s; eT = if (s > 0.3f) 2f else 0f
                if (actT > 1.6f && actT - dt <= 1.6f) spawnFx(1f, x + face * 0.3f * size, y, 0.4f)
                if (actT > 2.4f) act = Act.NONE
            }
            Act.BUBBLES -> {
                mouthT = 0.22f + 0.1f * sin(actT * 14f); eT = 1f
                if ((actT * 4f).toInt() != ((actT - dt) * 4f).toInt()) spawnFx(1f, x + face * 0.32f * size, y - 0.05f * size, 0.4f + Random.nextFloat() * 0.2f)
                if (actT > 2.4f) act = Act.NONE
            }
            Act.LOOP -> {
                val t = (actT / 1.1f).coerceIn(0f, 1f)
                loopT = t * t * (3f - 2f * t) * 2f * PI.toFloat()
                speed = 0.7f; eT = 1f; mouthT = 0.4f
                if (actT > 1.15f) { act = Act.NONE; loop = 0f; loopT = 0f; spawnFx(2f, x, y + 0.4f * size, 1f) }
            }
            Act.STARE -> {
                // Goes cross-eyed at its own lure, snaps at it, misses.
                lureRX = 0.5f; lureRY = 0.12f + 0.04f * sin(actT * 5f); lkx = 1f; lky = 0.1f; eT = 3f; speed = 0.03f
                if (actT > 1.6f && actT < 1.85f) { mouthT = 1f }
                if (actT > 1.85f && actT - dt <= 1.85f) { lvy += 2.2f * size; lvx -= face * 0.8f * size; sqV -= 2f; spawnFx(5f, x + face * 0.1f * size, y + 0.5f * size, 0.9f) }
                if (actT > 1.85f) { lureRX = 0.36f; lureRY = 0.44f; eT = 0f }
                if (actT > 2.8f) act = Act.NONE
            }
            Act.NIBBLE -> {
                // Charging: kisses the glowing bulb and lights up from the inside.
                val ph = actT % 1.4f
                mouthT = if (ph < 0.3f) 0.5f else 0f; eT = 1f
                lureRX = 0.44f; lureRY = 0.1f
                if (ph < dt) { flash = 0.6f; spotsT = 2f; spawnFx(2f, x + face * 0.45f * size, y + 0.1f * size, 1f) }
                if (actT > 4.2f || !charging) act = Act.NONE
            }
            Act.NONE -> {}
        }

        // ---- Integrate ----
        val dx = tx - x; val dy = ty - y
        val dist = hypot(dx, dy)
        val want = min(speed, dist * 2.5f)
        val ax = if (dist > 0.001f) dx / dist * want else 0f
        val ay = if (dist > 0.001f) dy / dist * want else 0f
        vx += (ax - vx) * min(1f, dt * 2.2f); vy += (ay - vy) * min(1f, dt * 2.2f)
        x = (x + vx * dt).coerceIn(-halfW + 0.3f * size, halfW - 0.3f * size)
        y = (y + (vy + 0.015f * sin(time * 1.4f)) * dt).coerceIn(minY - 0.1f, maxY + 0.1f)
        val sp = hypot(vx, vy)
        face += (faceT - face) * min(1f, dt * 6f)
        val pitchT = (vy * 1.6f * (if (face >= 0f) 1f else -1f)).coerceIn(-0.45f, 0.45f)
        pitch += (pitchT - pitch) * min(1f, dt * 4f)
        loop = loopT
        tailPh += dt * (3.5f + sp * 26f); tailAmp += ((0.18f + sp * 1.4f).coerceAtMost(0.7f) - tailAmp) * min(1f, dt * 5f)
        finPh += dt * (5f + (if (musicOn) a.mid * 14f else 0f) + sp * 8f)
        puff += (puffT - puff) * min(1f, dt * (if (puffT > puff) 9f else 3f))
        mouth += (mouthT - mouth) * min(1f, dt * 14f)
        lookX += (lkx - lookX) * min(1f, dt * 6f); lookY += (lky - lookY) * min(1f, dt * 6f)
        lureB += (brightT - lureB) * min(1f, dt * 10f)
        charge += (chargeT - charge) * min(1f, dt * 3f)
        spots += (spotsT - spots) * min(1f, dt * 8f)
        flash = max(0f, flash - dt * 2.2f)
        nextBlink -= dt
        if (nextBlink <= 0f) { blink = 0f; nextBlink = 2.5f + Random.nextFloat() * 4f }
        blink = min(1f, blink + dt * 9f)
        eyeOpen += (min(openT, blink) - eyeOpen) * min(1f, dt * 20f)
        eyeMode = eT

        // Lure on its springy stalk (sub-stepped).
        val fs = if (face >= 0f) 1f else -1f
        var ltx = x + fs * lureRX * size + 0.03f * size * sin(time * 1.7f)
        var lty = y + lureRY * size + 0.03f * size * sin(time * 2.3f)
        if (following && !downOnBody && preyLife <= 0f) {
            val axx = x + fs * 0.1f * size; val ayy = y + 0.26f * size
            var ddx = fX - axx; var ddy = fY - ayy
            val d = hypot(ddx, ddy); val maxL = 0.62f * size
            if (d > maxL) { ddx *= maxL / d; ddy *= maxL / d }
            ltx = axx + ddx; lty = ayy + ddy
        }
        val n = (dt / 0.012f).toInt() + 1; val hs = dt / n
        for (i in 0 until n) {
            lvx += ((ltx - lx) * 70f - lvx * 7f) * hs; lvy += ((lty - ly) * 70f - lvy * 7f) * hs
            lx += lvx * hs; ly += lvy * hs
            sqV += ((1f - sq) * 170f - sqV * 9f) * hs; sq = (sq + sqV * hs).coerceIn(0.8f, 1.2f)
        }

        // Little fish gather around the touch point.
        if (preyLife > 0f) {
            preyLife -= dt
            for (i in 0 until 4) {
                if (prey[i * 4 + 3] <= 0f) continue
                val gx = fX + preyT[i * 2] + 0.03f * sin(time * 2f + i); val gy = fY + preyT[i * 2 + 1] + 0.02f * cos(time * 2.6f + i * 2f)
                if (act != Act.GULP) {
                    val ddx = gx - prey[i * 4]; val ddy = gy - prey[i * 4 + 1]
                    prey[i * 4] += ddx * min(1f, dt * 2.5f); prey[i * 4 + 1] += ddy * min(1f, dt * 2.5f)
                    if (abs(ddx) > 0.02f) prey[i * 4 + 2] = sign(ddx)
                }
                if (preyLife <= 0f) prey[i * 4 + 3] = 0f
            }
            if (!preyGathered) preyLife = min(preyLife, 0f)
        }

        for (i in 0 until 6) {
            if (fx[i * 4 + 3] == 0f) continue
            fx[i * 4 + 2] += fxSpeed[i] * dt
            fx[i * 4] += (fxVel[i * 2] + 0.02f * sin(time * 5f + i)) * dt; fx[i * 4 + 1] += fxVel[i * 2 + 1] * dt
            if (fx[i * 4 + 2] >= 1f) fx[i * 4 + 3] = 0f
        }
    }

    private fun tri(t: Float, a: Float, b: Float, c: Float): Float = when {
        t < a -> t / a
        t < b -> 1f
        t < c -> 1f - (t - b) / (c - b)
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

        var qx0 = x - 1.3f * size; var qx1 = x + 1.3f * size; var qy0 = y - 1.1f * size; var qy1 = y + 1.3f * size
        for (i in 0 until 4) {
            preyOut[i * 4] = (prey[i * 4] - x) / size; preyOut[i * 4 + 1] = (prey[i * 4 + 1] - y) / size
            preyOut[i * 4 + 2] = prey[i * 4 + 2]; preyOut[i * 4 + 3] = if (preyLife > 0f || act == Act.GULP) prey[i * 4 + 3] else 0f
            if (preyOut[i * 4 + 3] > 0f) {
                qx0 = min(qx0, prey[i * 4] - 0.1f); qx1 = max(qx1, prey[i * 4] + 0.1f)
                qy0 = min(qy0, prey[i * 4 + 1] - 0.1f); qy1 = max(qy1, prey[i * 4 + 1] + 0.1f)
            }
        }
        for (i in 0 until 6) {
            fxOut[i * 4] = (fx[i * 4] - x) / size; fxOut[i * 4 + 1] = (fx[i * 4 + 1] - y) / size
            fxOut[i * 4 + 2] = fx[i * 4 + 2]; fxOut[i * 4 + 3] = fx[i * 4 + 3]
            if (fx[i * 4 + 3] != 0f) {
                qx0 = min(qx0, fx[i * 4] - 0.1f); qx1 = max(qx1, fx[i * 4] + 0.1f)
                qy0 = min(qy0, fx[i * 4 + 1] - 0.1f); qy1 = max(qy1, fx[i * 4 + 1] + 0.1f)
            }
        }
        GLES20.glUniform4f(u("uQuad"), max(qx0, -halfW), max(qy0, -halfH), min(qx1, halfW), min(qy1, halfH))
        GLES20.glUniform2f(u("uRes"), w.toFloat(), h.toFloat())
        GLES20.glUniform1f(u("uTime"), time % 600f)
        GLES20.glUniform2f(u("uPos"), x, y)
        GLES20.glUniform1f(u("uSize"), size)
        GLES20.glUniform1f(u("uPx"), 1f / (size * min(w, h)))
        GLES20.glUniform1f(u("uFace"), face)
        val pr = pitch + loop
        GLES20.glUniform1f(u("uPitch"), pr)
        val sx = 1f / kotlin.math.sqrt(sq)
        GLES20.glUniform2f(u("uSquash"), sx, sq)
        GLES20.glUniform1f(u("uPuff"), puff)
        GLES20.glUniform1f(u("uMouth"), mouth.coerceIn(0f, 1f))
        GLES20.glUniform4f(u("uEye"), eyeOpen, eyeMode, lookX, lookY)
        GLES20.glUniform2f(u("uFin"), finPh, finAmp)
        GLES20.glUniform2f(u("uTail"), tailPh, tailAmp)
        // The lure in the fish's own (flipped, rotated, squashed) space.
        val fc = if (abs(face) < 0.15f) (if (face < 0f) -0.15f else 0.15f) else face
        val ldx = (lx - x) / size / fc; val ldy = (ly - y) / size
        val c = cos(-pr); val s = sin(-pr)
        GLES20.glUniform4f(u("uLure"), (c * ldx - s * ldy) / sx, (s * ldx + c * ldy) / sq, lureB.coerceIn(0f, 2.2f), flash)
        GLES20.glUniform4f(u("uCharge"), charge, input.battery, 0f, 0f)
        GLES20.glUniform3f(u("uBody"), cBody[0], cBody[1], cBody[2])
        GLES20.glUniform3f(u("uBelly"), cBelly[0], cBelly[1], cBelly[2])
        GLES20.glUniform3f(u("uFinC"), cFin[0], cFin[1], cFin[2])
        GLES20.glUniform3f(u("uGlow"), cGlow[0], cGlow[1], cGlow[2])
        GLES20.glUniform1f(u("uSpots"), spots.coerceIn(0f, 2f))
        GLES20.glUniform1f(u("uGhost"), ghost)
        GLES20.glUniform1f(u("uKind"), kind.toFloat())
        preyBuf.position(0); preyBuf.put(preyOut); preyBuf.position(0)
        GLES20.glUniform4fv(u("uPrey"), 4, preyBuf)
        fxBuf.position(0); fxBuf.put(fxOut); fxBuf.position(0)
        GLES20.glUniform4fv(u("uFx"), 6, fxBuf)
        GLES20.glUniform1f(u("uDim"), if (input.lowBattery) 0.3f else 0f)
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
