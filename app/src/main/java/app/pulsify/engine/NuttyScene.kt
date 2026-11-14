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

/** Nutty's fur colours. */
data class NuttyCoat(val name: String, val fur: Int, val light: Int, val dark: Int, val belly: Int, val tail: Int)

val NUTTY_COATS = listOf(
    NuttyCoat("Red", 0xFFD1692A.toInt(), 0xFFF2AC6A.toInt(), 0xFF5E2A10.toInt(), 0xFFFFE6C8.toInt(), 0xFFD9782F.toInt()),
    NuttyCoat("Grey", 0xFF8E8A96.toInt(), 0xFFC9C5CC.toInt(), 0xFF3A3640.toInt(), 0xFFF4F1F6.toInt(), 0xFF9C97A0.toInt()),
    NuttyCoat("Black", 0xFF3A3238.toInt(), 0xFF6E6470.toInt(), 0xFF120E12.toInt(), 0xFFD9C8B8.toInt(), 0xFF453B43.toInt()),
    NuttyCoat("Golden", 0xFFE6A13A.toInt(), 0xFFFFDC8A.toInt(), 0xFF8A5212.toInt(), 0xFFFFF1D6.toInt(), 0xFFF0B347.toInt()),
    NuttyCoat("Flying", 0xFFB08866.toInt(), 0xFFE6CBB0.toInt(), 0xFF4E3624.toInt(), 0xFFFFF6EA.toInt(), 0xFFC4A283.toInt()),
)

/**
 * Nutty, the red squirrel. Twitchy, nosy, always one second from panicking. A plush SDF
 * character: huge frosted plume of a tail, mitten paws, cheeks that swell with stashed nuts.
 * Tap: freezes, eyes huge, tail puffs. Drag: hops after your finger. Hold: an acorn appears;
 * it hops over, grabs it and nibbles. Character units: feet on the floor at y = -0.55.
 */
class NuttyScene(private val context: Context) : Creature {

    override var listener: SceneListener? = null

    private enum class Act { NONE, NIBBLE, STASH, LOOKOUT, FLICK, ZOOM, CATCH, SPIN, SCRATCH, FEED, DRUM, GREET, STARTLE }

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
    private var x = 0f
    private var hopping = false; private var hopFrom = 0f; private var hopTo = 0f; private var hopT = 0f; private var hopTime = 0.26f
    private var jumpY = 0f; private var vy = 0f
    private var sy = 1f; private var syV = 0f
    private var shear = 0f; private var shearT = 0f
    private var stand = 0f; private var standT = 0f

    // Head and face.
    private var lookX = 0f; private var lookY = 0f; private var lookTX = 0f; private var lookTY = 0f
    private var tilt = 0f; private var tiltT = 0f
    private var headY = 0f; private var headYT = 0f
    private var nextWander = 2f
    private var blink = 1f; private var nextBlink = 2.5f
    private var eyeMode = 0f; private var mouth = 0f
    private var chew = 0f; private var chewing = false
    private var cheeks = 0f; private var cheeksT = 0f
    private var earL = 0f; private var earR = 0f; private var earLV = 0f; private var earRV = 0f; private var twitchIn = 2f

    // Tail: swing (spring), sway, curl, fluff.
    private var tail = 0f; private var tailV = 0f; private var tailT = 0f
    private var swayPhase = 0f; private var sway = 0.06f; private var swayT = 0.06f
    private var curl = 0.7f; private var curlT = 0.7f
    private var fluff = 0f

    // Hands (character space), eased toward targets.
    private val hand = floatArrayOf(-0.045f, -0.15f, 0.045f, -0.15f)
    private val handT = FloatArray(4)
    private val handV = FloatArray(4)

    // Acorn: 0 none, 1 in hands, 2 at mouth, 3 loose in the world, 4 stashed in a cheek.
    private var acorn = 0
    private var acornX = 0f; private var acornY = 0f; private var acornVy = 0f; private var acornRot = 0f; private var acornVis = 0f

    // Behaviour.
    private var act = Act.NONE; private var actT = 0f; private var actLen = 0f
    private var nextIdle = 3f
    private val lastActs = ArrayDeque<Act>()
    private var awakeFor = 0f
    private var charging = false; private var lowBattery = false; private var night = false; private var musicOn = false
    private var lastBeat = -9f
    private var taps = 0; private var lastTap = -9f

    // Touch.
    private var touchDown = false; private var downOnBody = false
    private var tX = 0f; private var tY = 0f

    private val coat = FloatArray(3); private val cream = FloatArray(3); private val dark = FloatArray(3); private val rim = FloatArray(3)
    private val glow = floatArrayOf(0.35f, 1f, 0.5f)
    private var colorsInit = false
    var sinceTouch = 99f; private set

    override val busy: Boolean
        get() = touchDown || sinceTouch < 2f || act != Act.NONE || hopping || abs(vy) > 0.01f || acorn == 3

    override fun onSurfaceCreated() {
        val vert = context.assets.open("shaders/mochi.vert").bufferedReader().use { it.readText() }
        val frag = context.assets.open("shaders/nutty.frag").bufferedReader().use { it.readText() }
        program = Gl.program(vert, frag)
        for (n in listOf("uQuad", "uRes", "uTime", "uPos", "uSize", "uPx", "uSquash", "uShear", "uLook", "uTilt", "uHeadY",
            "uStand", "uEyeOpen", "uEyeMode", "uMouth", "uChew", "uCheeks", "uEars", "uTail", "uFluff", "uHandL", "uHandR",
            "uAcorn", "uCoat", "uCoat2", "uCoat3", "uRim", "uGlow", "uFx", "uBattIcon", "uBatt", "uDim")) {
            uni[n] = GLES20.glGetUniformLocation(program, n)
        }
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0); quadVbo = ids[0]
        val q = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        val fb = ByteBuffer.allocateDirect(q.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(q); fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, q.size * 4, fb, GLES20.GL_STATIC_DRAW)
    }

    override fun onSurfaceChanged(width: Int, height: Int) { w = max(1, width); h = max(1, height); layout() }

    private var ground = Float.NaN
    override fun setGround(y: Float) {
        if (y == ground || (y.isNaN() && ground.isNaN())) return
        ground = y; layout()
    }

    private val halfW get() = 0.5f * w / min(w, h)

    private fun layout() {
        val halfH = 0.5f * h / min(w, h)
        size = min(0.6f, halfW * 2f * 0.58f)
        homeX = -0.1f * size
        homeY = (if (ground.isNaN()) -halfH + 0.3f else ground) + 0.55f * size
        if (h.toFloat() / w < 1.5f) { size = min(0.4f, halfW * 0.75f); homeY = halfH - 0.3f - 0.75f * size }
        if (bottomInset > 0f) {
            val floor = -halfH + bottomInset * 2f * halfH + 0.06f
            size = min(size, (halfH - 0.08f - floor) / 1.4f)
            homeY = max(homeY, floor + 0.55f * size)
        }
    }

    private val bodyX get() = homeX + x
    private val bodyY get() = homeY + jumpY
    private val floorUv get() = homeY - 0.55f * size
    private val reach get() = (halfW - 0.5f * size).coerceAtLeast(0f)

    private fun toUv(nx: Float, ny: Float): Pair<Float, Float> {
        val m = min(w, h).toFloat()
        return Pair((nx - 0.5f) * w / m, (0.5f - ny) * h / m)
    }

    private fun onBody(ux: Float, uy: Float): Boolean {
        val cx = (ux - bodyX) / size; val cy = (uy - bodyY) / size
        return (cx / 0.42f) * (cx / 0.42f) + ((cy + 0.05f) / 0.62f) * ((cy + 0.05f) / 0.62f) < 1f
    }

    // ---- Touch ----

    override fun touchDown(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f; awakeFor = 12f
        tX = ux; tY = uy; touchDown = true
        downOnBody = onBody(ux, uy)
        lookAt(ux, uy)
        return downOnBody
    }

    override fun touchMove(nx: Float, ny: Float): Boolean {
        if (!touchDown) return false
        val (ux, uy) = toUv(nx, ny)
        tX = ux; tY = uy; sinceTouch = 0f
        lookAt(ux, uy)
        // Hops after the finger when it wanders off to the side.
        if (!downOnBody && act == Act.NONE && abs(ux - bodyX) > 0.45f * size) hopToward(ux - homeX)
        return downOnBody
    }

    override fun touchUp(): Boolean { val r = touchDown && downOnBody; touchDown = false; sinceTouch = 0f; return r }

    override fun tapAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f; awakeFor = 12f
        lookAt(ux, uy)
        if (onBody(ux, uy)) {
            taps = if (time - lastTap < 0.7f) taps + 1 else 1
            lastTap = time
            listener?.onBondXp(1)
            if (taps >= 3) { taps = 0; start(Act.ZOOM) } else start(Act.STARTLE)
            return true
        }
        // Tap the ground: it hops over to check what you dropped.
        hopToward(ux - homeX)
        return false
    }

    override fun longPressAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f; awakeFor = 12f
        dropAcorn(ux, uy)
        listener?.onBondXp(2)
        return true
    }

    private fun dropAcorn(ux: Float, uy: Float) {
        acorn = 3; acornX = ux.coerceIn(homeX - reach, homeX + reach); acornY = max(uy, floorUv + 0.1f * size); acornVy = 0f; acornVis = 1f
        start(Act.FEED)
    }

    private fun lookAt(ux: Float, uy: Float) {
        lookTX = ((ux - bodyX) / size / 0.8f).coerceIn(-1f, 1f)
        lookTY = ((uy - bodyY - 0.15f * size) / size / 0.8f).coerceIn(-1f, 1f)
        val d = hypot(ux - bodyX, uy - (bodyY + 0.15f * size)) / size
        tiltT = if (d in 0.3f..1.0f) 0.25f * (if (ux > bodyX) 1f else -1f) else 0f
        nextWander = 2.5f + Random.nextFloat() * 2f
    }

    private fun hopToward(targetX: Float) {
        if (hopping || jumpY > 0f) return
        val t = targetX.coerceIn(-reach - homeX, reach - homeX)
        if (abs(t - x) < 0.04f * size) return
        hopFrom = x; hopTo = x + (t - x).coerceIn(-0.35f * size, 0.35f * size); hopT = 0f; hopping = true
        vy = 4.6f * size * hopTime * 0.5f
    }

    override fun swiped(dir: Float, fast: Boolean) { if (fast) start(Act.STARTLE) else start(listOf(Act.LOOKOUT, Act.FLICK, Act.GREET).random()) }

    override fun greet() { awakeFor = 12f; start(Act.GREET) }

    override fun perform(trick: String): Boolean {
        awakeFor = 15f; sinceTouch = 0f
        val a = when (trick) {
            "nibble" -> Act.NIBBLE; "stash" -> Act.STASH; "lookout" -> Act.LOOKOUT; "flick" -> Act.FLICK
            "zoom" -> Act.ZOOM; "catch" -> Act.CATCH; "spin" -> Act.SPIN; "scratch" -> Act.SCRATCH
            "feed" -> { dropAcorn(bodyX + 0.6f * size * (if (Random.nextBoolean()) 1f else -1f), bodyY + 1.0f * size); return true }
            "drum" -> Act.DRUM
            else -> return false
        }
        start(a); return true
    }

    private fun start(a: Act) {
        act = a; actT = 0f
        actLen = when (a) {
            Act.NIBBLE -> 3.4f; Act.STASH -> 5.4f; Act.LOOKOUT -> 3.6f; Act.FLICK -> 1.5f; Act.ZOOM -> 3.0f
            Act.CATCH -> 3.0f; Act.SPIN -> 1.3f; Act.SCRATCH -> 2.2f; Act.FEED -> 6f; Act.DRUM -> 3.2f; Act.GREET -> 1.8f
            Act.STARTLE -> 1.4f; Act.NONE -> 0f
        }
        if (a == Act.NIBBLE || a == Act.STASH) { if (acorn == 0) { acorn = 1; acornVis = 0f } }
        if (a == Act.CATCH) { acorn = 3; acornX = bodyX + 0.02f * size; acornY = bodyY + 1.6f * size; acornVy = 0f; acornVis = 1f }
        if (a == Act.STARTLE) { syV += 4f; spawn(6f, 0.25f, 0.62f, 0.8f) }
        if (a == Act.GREET) spawn(1f, -0.2f, 0.55f, 0.9f)
    }

    // ---- Simulation ----

    override fun update(dt: Float, input: SceneInput) {
        time += dt; sinceTouch += dt; awakeFor = max(0f, awakeFor - dt)
        charging = input.charging; lowBattery = input.lowBattery; night = input.mood == Mood.NIGHT
        musicOn = input.audio.source != AudioFrame.Source.NONE
        updateColors(input, dt)
        val asleep = night && awakeFor <= 0f && act == Act.NONE && !touchDown

        // Pose targets: a calm sitting squirrel, acorn hugged at the chest.
        var lx = -0.05f; var ly = -0.15f; var rx = 0.05f; var ry = -0.15f
        standT = 0f; headYT = 0f; eyeMode = 0f; mouth = 0f; cheeksT = 0f; chewing = false
        tailT = 0f; swayT = 0.06f; curlT = 0.7f
        var fluffT = 0f
        shearT = 0f

        if (asleep) {
            eyeMode = 3f; headYT = -0.05f; tiltT = 0.25f; curlT = 1.3f; tailT = -0.15f; swayT = 0.02f
            if (Random.nextFloat() < dt * 0.6f) spawn(5f, 0.2f, 0.45f, 0.4f)
        }
        if (lowBattery && !asleep) { eyeMode = 2f; headYT = -0.04f; tailT = 0.35f; curlT = 0.2f; swayT = 0.01f }
        if (charging && !asleep) {
            // Cosy: hugs a glowing acorn and nibbles it now and then.
            if (acorn == 0) { acorn = 1; acornVis = 0f }
            if (sin(time * 0.7f) > 0.6f) { chewing = true; mouth = 1f; lx = -0.035f; ly = 0.0f; rx = 0.035f; ry = 0.0f }
            eyeMode = if (sin(time * 0.31f) > 0.2f) 1f else 0f
        }
        if (musicOn && !asleep) {
            val a = input.audio
            if (a.beat > 0.9f && time - lastBeat > 0.25f) { lastBeat = time; syV -= 2.2f + a.bass * 2f; tailV += 3f * (if (Random.nextBoolean()) 1f else -1f) }
            headYT += 0.02f * a.bass; swayT = 0.12f + 0.12f * a.mid
            if (Random.nextFloat() < dt * 0.8f) spawn(4f, 0.3f, 0.6f, 0.5f)
        }

        actT += dt
        val k = if (actLen > 0f) actT / actLen else 0f
        when (act) {
            Act.NIBBLE -> {
                acorn = if (acorn == 0) 1 else acorn
                lx = -0.035f; ly = 0.0f; rx = 0.035f; ry = 0.0f; chewing = true; mouth = 1f; eyeMode = if (sin(actT * 3f) > 0.4f) 1f else 0f
                if (Random.nextFloat() < dt * 5f) spawn(3f, (Random.nextFloat() - 0.5f) * 0.1f, 0.03f, 0.9f)
            }
            Act.STASH -> {
                // Stuff the nut in a cheek, hop aside, dig, bury, hop back.
                if (actT < 0.8f) { lx = -0.03f; ly = 0.02f; rx = 0.03f; ry = 0.02f; mouth = 2f }
                else { if (acorn != 0) { acorn = 4; acornVis = 0f }; cheeksT = 1f }
                if (actT in 0.8f..0.9f) hopToward(0.5f * size * (if (x > 0f) -1f else 1f))
                if (actT in 1.4f..3.6f) {
                    standT = -0.2f; headYT = -0.06f
                    val dig = sin(actT * 22f)
                    lx = -0.09f; ly = -0.45f + 0.04f * dig; rx = 0.09f; ry = -0.45f - 0.04f * dig
                    if (Random.nextFloat() < dt * 10f) spawn(3f, (Random.nextFloat() - 0.5f) * 0.3f, -0.5f, 1.4f)
                    if (actT > 2.4f) cheeksT = 0f
                }
                if (actT in 3.6f..3.7f) { hopToward(0f); acorn = 0 }
                if (actT > 4.2f) { eyeMode = 1f; mouth = 3f }
            }
            Act.LOOKOUT -> {
                standT = 1f; lx = -0.04f; ly = 0.0f; rx = 0.04f; ry = 0.0f
                lookTX = sin(actT * 1.8f) * 0.9f; lookTY = 0.3f
                if (Random.nextFloat() < dt * 2f) { earLV += 8f; earRV -= 8f }
                tailT = 0.15f; curlT = 1.0f
            }
            Act.FLICK -> {
                tailV += sin(actT * 18f) * 40f * dt * (1f - k)
                mouth = 2f * (if (sin(actT * 20f) > 0f) 1f else 0f); fluffT = 0.4f
            }
            Act.ZOOM -> {
                if (!hopping && jumpY == 0f) hopToward(if (sin(actT * 2.4f) > 0f) reach - homeX else -reach - homeX)
                hopTime = 0.18f; eyeMode = 4f; mouth = 2f; fluffT = 0.6f
                if (k > 0.85f) hopToward(0f)
            }
            Act.CATCH -> {
                lookTY = 1f; lookTX = 0f
                lx = -0.06f; ly = 0.05f; rx = 0.06f; ry = 0.05f
                if (acorn == 1) { eyeMode = 1f; mouth = 3f; lx = -0.045f; ly = -0.12f; rx = 0.045f; ry = -0.12f }
                else mouth = 2f
            }
            Act.SPIN -> {
                shearT = sin(k * PI.toFloat() * 4f) * 0.3f; tailT = sin(k * PI.toFloat() * 2f) * 0.8f
                if (actT < 0.05f) vy = 0.9f * size
                eyeMode = 1f
            }
            Act.SCRATCH -> {
                rx = 0.16f; ry = 0.2f + 0.03f * sin(actT * 30f); tiltT = 0.25f; eyeMode = 1f; lx = -0.05f; ly = -0.15f
            }
            Act.FEED -> {
                when (acorn) {
                    3 -> {
                        lookAt(acornX, acornY)
                        if (acornY <= floorUv + 0.06f * size + 1e-3f && abs(acornX - bodyX) > 0.12f * size) hopToward(acornX - homeX)
                        if (abs(acornX - bodyX) <= 0.14f * size && acornY <= floorUv + 0.07f * size) { acorn = 1; acornVis = 1f; spawn(1f, 0f, 0.6f, 0.9f) }
                    }
                    else -> { lx = -0.035f; ly = 0.0f; rx = 0.035f; ry = 0.0f; chewing = true; mouth = 1f; eyeMode = 1f
                        if (Random.nextFloat() < dt * 5f) spawn(3f, (Random.nextFloat() - 0.5f) * 0.1f, 0.03f, 0.9f) }
                }
                if (actT > 5.5f && acorn == 3) acornVis = max(0f, acornVis - dt)
            }
            Act.DRUM -> {
                val beat = sin(actT * 16f)
                lx = -0.08f; ly = -0.3f + 0.05f * beat; rx = 0.08f; ry = -0.3f - 0.05f * beat
                eyeMode = 1f; mouth = 3f; headYT = 0.02f * beat
                if (Random.nextFloat() < dt * 3f) spawn(4f, (Random.nextFloat() - 0.5f) * 0.5f, 0.3f, 0.6f)
            }
            Act.GREET -> {
                lx = -0.2f + 0.04f * sin(actT * 14f); ly = 0.12f; eyeMode = 1f; mouth = 3f; tiltT = 0.15f
            }
            Act.STARTLE -> {
                eyeMode = 4f; mouth = 2f; fluffT = 1f; standT = 0.6f; tailT = 0.1f; curlT = 1.1f
                lx = -0.08f; ly = -0.02f; rx = 0.08f; ry = -0.02f
                if (actT > 0.9f) { fluffT = 0.3f }
            }
            Act.NONE -> {}
        }
        if (act != Act.ZOOM) hopTime = 0.26f
        if (act != Act.NONE && actT > actLen) {
            if (act == Act.FEED || act == Act.NIBBLE) { if (acorn == 1 || acorn == 2) acorn = 0 }
            if (act == Act.CATCH) acorn = 0
            act = Act.NONE; nextIdle = TRICK_GAP_MIN + Random.nextFloat() * TRICK_GAP_RANGE
        }
        // Idle tricks: at least three a minute.
        if (act == Act.NONE && !asleep && !touchDown && !charging) {
            nextIdle -= dt
            if (nextIdle <= 0f) {
                val pool = mutableListOf(Act.NIBBLE, Act.STASH, Act.LOOKOUT, Act.FLICK, Act.SCRATCH, Act.CATCH, Act.SPIN, Act.DRUM, Act.GREET)
                if (musicOn) pool += listOf(Act.DRUM, Act.DRUM)
                pool.removeAll(lastActs)
                val a = pool.random()
                lastActs.addLast(a); if (lastActs.size > 3) lastActs.removeFirst()
                start(a)
            }
        }
        if (acorn == 1 && acornVis < 1f) acornVis = min(1f, acornVis + dt * 3f)

        // Acorn physics when it's loose.
        if (acorn == 3) {
            acornVy -= 3.6f * dt; acornY += acornVy * dt; acornRot += dt * 4f
            val fl = floorUv + 0.06f * size
            if (act == Act.CATCH && acornY < bodyY + 0.05f * size && abs(acornX - bodyX) < 0.3f * size) {
                acorn = 1; acornVis = 1f; syV -= 3f; spawn(2f, 0f, 0.0f, 0.8f); spawn(1f, 0.1f, 0.6f, 0.9f)
            } else if (acornY < fl) { acornY = fl; acornVy = -acornVy * 0.4f; if (abs(acornVy) < 0.1f) acornVy = 0f }
        }

        // Hops.
        if (hopping) {
            hopT = min(1f, hopT + dt / hopTime)
            val e = hopT * hopT * (3f - 2f * hopT)
            x = hopFrom + (hopTo - hopFrom) * e
            shearT += (hopTo - hopFrom).let { if (it > 0f) -0.12f else 0.12f }
            if (hopT >= 1f) { hopping = false; x = hopTo }
        }
        if (jumpY > 0f || vy > 0f) {
            vy -= 4.6f * size * dt; jumpY += vy * dt
            if (jumpY <= 0f) { jumpY = 0f; if (vy < -0.3f * size) { syV -= 3.5f; tailV += 3f }; vy = 0f }
        }

        // Springs and easing.
        syV += ((1f - sy) * 160f - syV * 9f) * dt; sy += syV * dt; sy = sy.coerceIn(0.75f, 1.25f)
        shear += (shearT - shear) * min(1f, dt * 10f)
        stand += (standT - stand) * min(1f, dt * 6f)
        headY += (headYT - headY) * min(1f, dt * 8f)
        cheeks += (cheeksT - cheeks) * min(1f, dt * 6f)
        fluff += (fluffT - fluff) * min(1f, dt * (if (fluffT > fluff) 14f else 2.5f))
        curl += (curlT - curl) * min(1f, dt * 3f)
        sway += (swayT - sway) * min(1f, dt * 3f)
        swayPhase += dt * (2.2f + (if (musicOn) 4f else 0f))
        tailV += ((tailT - tail) * 40f - tailV * 5f) * dt; tail += tailV * dt
        // The plume leans against the direction of a hop.
        if (hopping) tail += (hopTo - hopFrom) / size * 0.6f * dt
        if (chewing) chew = (chew + dt * 4.5f) % 1f
        handT[0] = lx; handT[1] = ly; handT[2] = rx; handT[3] = ry
        for (i in 0 until 4) { handV[i] += ((handT[i] - hand[i]) * 220f - handV[i] * 18f) * dt; hand[i] += handV[i] * dt }

        // Ears twitch now and then; blink; gaze wanders.
        twitchIn -= dt
        if (twitchIn <= 0f) { if (Random.nextBoolean()) earLV += 9f else earRV -= 9f; twitchIn = 1.5f + Random.nextFloat() * 3f }
        earLV += (-earL * 120f - earLV * 8f) * dt; earL += earLV * dt
        earRV += (-earR * 120f - earRV * 8f) * dt; earR += earRV * dt
        nextBlink -= dt
        if (nextBlink <= 0f) { blink = 0f; nextBlink = 2f + Random.nextFloat() * 3f }
        blink = min(1f, blink + dt * 9f)
        nextWander -= dt
        if (nextWander <= 0f && !touchDown && act == Act.NONE) {
            lookTX = (Random.nextFloat() - 0.5f) * 1.4f; lookTY = (Random.nextFloat() - 0.3f) * 0.8f
            tiltT = if (Random.nextFloat() < 0.35f) (Random.nextFloat() - 0.5f) * 0.4f else 0f
            nextWander = 1.6f + Random.nextFloat() * 2.5f
        }
        lookX += (lookTX - lookX) * min(1f, dt * 9f); lookY += (lookTY - lookY) * min(1f, dt * 9f)
        tilt += (tiltT - tilt) * min(1f, dt * 7f)

        // Effects.
        for (i in 0 until 6) {
            if (fx[i * 4 + 3] < 0.5f) continue
            fx[i * 4 + 2] += dt * fxSpeed[i]
            fx[i * 4] += fxVel[i * 2] * dt; fx[i * 4 + 1] += fxVel[i * 2 + 1] * dt
            if (fx[i * 4 + 3] == 3f) fxVel[i * 2 + 1] -= 2.2f * dt
            if (fx[i * 4 + 2] >= 1f) fx[i * 4 + 3] = 0f
        }
    }

    private fun spawn(kind: Float, cx: Float, cy: Float, speed: Float) {
        var slot = (0 until 6).firstOrNull { fx[it * 4 + 3] < 0.5f } ?: (0 until 6).maxByOrNull { fx[it * 4 + 2] } ?: 0
        fx[slot * 4] = cx; fx[slot * 4 + 1] = cy; fx[slot * 4 + 2] = 0f; fx[slot * 4 + 3] = kind
        fxSpeed[slot] = speed
        fxVel[slot * 2] = (Random.nextFloat() - 0.5f) * 0.2f
        fxVel[slot * 2 + 1] = if (kind == 3f) 0.5f + Random.nextFloat() * 0.3f else 0.25f
    }

    private fun updateColors(input: SceneInput, dt: Float) {
        val c = NUTTY_COATS[input.coat.coerceIn(0, NUTTY_COATS.size - 1)]
        ease(coat, c.fur, dt); ease(cream, c.belly, dt); ease(dark, c.dark, dt)
        val r = when (input.mood) { Mood.NIGHT -> 0xFF8C9EFF.toInt(); Mood.EVENING -> 0xFFFF9C6B.toInt(); else -> 0xFFFFC890.toInt() }
        ease(rim, r, dt)
        colorsInit = true
    }

    private fun ease(dst: FloatArray, argb: Int, dt: Float) {
        val r = Color.red(argb) / 255f; val g = Color.green(argb) / 255f; val b = Color.blue(argb) / 255f
        if (!colorsInit) { dst[0] = r; dst[1] = g; dst[2] = b; return }
        val k = min(1f, dt * 4f)
        dst[0] += (r - dst[0]) * k; dst[1] += (g - dst[1]) * k; dst[2] += (b - dst[2]) * k
    }

    private fun u(n: String) = uni[n] ?: -1

    override fun draw(input: SceneInput) {
        if (program == 0) return
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)

        val bx = bodyX; val by = bodyY
        var minX = bx - 0.5f * size; var maxX = bx + 0.75f * size
        var minY = by - 0.62f * size; var maxY = by + 1.1f * size
        if (acorn == 3) {
            minX = min(minX, acornX - 0.1f * size); maxX = max(maxX, acornX + 0.1f * size)
            minY = min(minY, acornY - 0.1f * size); maxY = max(maxY, acornY + 0.1f * size)
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
        GLES20.glUniform1f(u("uTilt"), tilt)
        GLES20.glUniform1f(u("uHeadY"), headY)
        GLES20.glUniform1f(u("uStand"), stand)
        GLES20.glUniform1f(u("uEyeOpen"), if (eyeMode == 0f || eyeMode == 4f) blink else 1f)
        GLES20.glUniform1f(u("uEyeMode"), eyeMode)
        GLES20.glUniform1f(u("uMouth"), mouth)
        GLES20.glUniform1f(u("uChew"), chew)
        GLES20.glUniform1f(u("uCheeks"), cheeks)
        GLES20.glUniform2f(u("uEars"), earL, earR)
        GLES20.glUniform4f(u("uTail"), tail, curl, swayPhase, sway)
        GLES20.glUniform1f(u("uFluff"), fluff)
        GLES20.glUniform2f(u("uHandL"), hand[0], hand[1] + 0.1f * stand)
        GLES20.glUniform2f(u("uHandR"), hand[2], hand[3] + 0.1f * stand)
        // The acorn: between the paws, at the mouth, or loose in the world.
        when (acorn) {
            1 -> GLES20.glUniform4f(u("uAcorn"), (hand[0] + hand[2]) / 2f, (hand[1] + hand[3]) / 2f + 0.035f + 0.1f * stand, acornVis, 0.15f * sin(time * 2f))
            3 -> GLES20.glUniform4f(u("uAcorn"), (acornX - bx) / size, (acornY - by) / size, acornVis, acornRot)
            else -> GLES20.glUniform4f(u("uAcorn"), 0f, 0f, 0f, 0f)
        }
        GLES20.glUniform3f(u("uCoat"), coat[0], coat[1], coat[2])
        GLES20.glUniform3f(u("uCoat2"), cream[0], cream[1], cream[2])
        GLES20.glUniform3f(u("uCoat3"), dark[0], dark[1], dark[2])
        GLES20.glUniform3f(u("uRim"), rim[0], rim[1], rim[2])
        GLES20.glUniform3f(u("uGlow"), glow[0], glow[1], glow[2])
        fxBuf.position(0); fxBuf.put(fx); fxBuf.position(0)
        GLES20.glUniform4fv(u("uFx"), 6, fxBuf)
        GLES20.glUniform1f(u("uBattIcon"), if (input.charging) 1f else 0f)
        GLES20.glUniform1f(u("uBatt"), input.battery)
        GLES20.glUniform1f(u("uDim"), when { input.mood == Mood.NIGHT -> 0.3f; input.lowBattery -> 0.25f; else -> 0f })
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    override fun release() {
        if (program != 0) GLES20.glDeleteProgram(program)
        if (quadVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
        program = 0; quadVbo = 0
    }
}
