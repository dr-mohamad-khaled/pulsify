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

/** Kitty's coats: fur, white, stripe colour, eye colour, siamese points. */
data class KittyCoat(val name: String, val fur: Int, val white: Int, val stripe: Int, val eye: Int, val points: Float)

val KITTY_COATS = listOf(
    KittyCoat("Silver Tabby", 0xFF9EA3AD.toInt(), 0xFFF7F7FA.toInt(), 0xFF4D4F59.toInt(), 0xFF5AA6FF.toInt(), 0f),
    KittyCoat("Ginger", 0xFFF29A4C.toInt(), 0xFFFFF4E6.toInt(), 0xFFBF6126.toInt(), 0xFFF2C040.toInt(), 0f),
    KittyCoat("Tuxedo", 0xFF1F1F24.toInt(), 0xFFF5F5F5.toInt(), 0xFF0D0D10.toInt(), 0xFF7FE36A.toInt(), 0f),
    KittyCoat("Siamese", 0xFFF2E6D2.toInt(), 0xFFFFF8EE.toInt(), 0xFF5A4034.toInt(), 0xFF64B5FF.toInt(), 1f),
)

/**
 * Kitty, the grey-and-white kitten: curious, aloof, a little dramatic. Its own slim rig: tail chain,
 * swivelling ears, slit pupils, crouch-and-pounce, loaf. Hunts a laser dot that follows your finger.
 */
class KittyScene(private val context: Context) : Creature {

    override var listener: SceneListener? = null

    private enum class Act { NONE, BAT, PURR, STALK, POUNCE, GROOM, KNEAD, LOAF, KNOCK, ZOOM, CUCUMBER, BOX, STRETCH, TRILL }

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

    private var x = 0f; private var vx = 0f
    private var jumpY = 0f; private var vy = 0f
    private var sy = 1f; private var syV = 0f
    private var shear = 0f
    private var loaf = 0f; private var crouch = 0f; private var puff = 0f

    private var lookX = 0f; private var lookY = 0f; private var lookTX = 0f; private var lookTY = 0f
    private var tilt = 0f; private var tiltT = 0f
    private var headY = 0f
    private var nextWander = 3f
    private var earL = 0f; private var earR = 0f; private var earsDown = 0f
    private var tailPhase = 0f; private var tailBase = 0.55f; private var tailSway = 0.15f

    private var blink = 1f; private var nextBlink = 3f
    private var eyeOpen = 1f; private var eyeMode = 0f; private var mouth = 0f
    private var pupil = 0.3f
    private val paws = floatArrayOf(0f, 1f, 0f, 1f)
    private val pawsT = FloatArray(4)

    // Props.
    private var dotX = 0f; private var dotY = 0f; private var dotVis = 0f; private var dotStill = 0f
    private var cupX = 0f; private var cupY = 0f; private var cupRot = 0f; private var cupVy = 0f; private var cupVis = 0f; private var cupFalling = false
    private var cukeVis = 0f
    private var boxVis = 0f
    private var padVis = 0f
    private var eyeGlow = 0f

    private var act = Act.NONE
    private var actT = 0f
    private var nextIdle = 6f
    private val lastActs = ArrayDeque<Act>()
    private var greetedMorning = false
    private var pounceX = 0f

    private var touchDown = false
    private var downAt = 0f
    private var downOnBody = false
    private var tX = 0f; private var tY = 0f
    private var moved = false
    private var longFired = false

    private val fur = FloatArray(3); private val white = FloatArray(3); private val stripe = FloatArray(3); private val eye = FloatArray(3)
    private var points = 0f
    private val rim = FloatArray(3); private val glow = floatArrayOf(0.35f, 1f, 0.45f)
    private var colorsInit = false
    private var mood = Mood.DAY
    var sinceTouch = 99f; private set

    override val busy: Boolean
        get() = touchDown || sinceTouch < 2f || act != Act.NONE || abs(vx) > 0.01f || abs(vy) > 0.01f || dotVis > 0.01f || cupFalling

    override fun onSurfaceCreated() {
        val vert = context.assets.open("shaders/mochi.vert").bufferedReader().use { it.readText() }
        val frag = context.assets.open("shaders/kitty.frag").bufferedReader().use { it.readText() }
        program = Gl.program(vert, frag)
        for (n in listOf("uQuad", "uRes", "uTime", "uPos", "uSize", "uPx", "uSquash", "uShear", "uLook", "uTilt", "uHeadY",
            "uLoaf", "uCrouch", "uEyeOpen", "uEyeMode", "uPupil", "uMouth", "uEars", "uEarsDown", "uTail", "uPuff",
            "uPawL", "uPawR", "uFur", "uWhite", "uStripe", "uEye", "uPoints", "uRim", "uGlow", "uEyeGlow", "uPad",
            "uBox", "uCup", "uCuke", "uDot", "uFx", "uBattIcon", "uDim")) {
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
        if (h.toFloat() / w < 1.5f) { size = min(0.42f, halfW * 0.8f); homeY = halfH - 0.24f - 0.55f * size }
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
        return (cx / 0.36f) * (cx / 0.36f) + ((cy + 0.12f) / 0.5f) * ((cy + 0.12f) / 0.5f) < 1f
    }

    private fun discover(id: String) { if (reported.add(id)) listener?.onDiscovery(id) }
    private val reported = HashSet<String>()

    // ---- Touch: a dragged finger is a laser dot ----

    override fun touchDown(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        tX = ux; tY = uy; moved = false; longFired = false; downAt = time; touchDown = true
        downOnBody = onBody(ux, uy)
        lookAt(ux, uy)
        if (!downOnBody) { dotX = ux; dotY = uy; dotStill = 0f }
        return true // Kitty uses every touch: the dot, or Kitty herself
    }

    override fun touchMove(nx: Float, ny: Float): Boolean {
        if (!touchDown) return false
        val (ux, uy) = toUv(nx, ny)
        if (hypot(ux - tX, uy - tY) > 0.03f) {
            if (!moved && !downOnBody) discover("laser")
            moved = true
        }
        if (!downOnBody && moved) {
            dotVis = 1f
            if (hypot(ux - dotX, uy - dotY) > 0.004f) dotStill = 0f
            dotX = ux; dotY = uy
            if (act == Act.NONE || act == Act.LOAF || act == Act.GROOM || act == Act.KNEAD) startAct(Act.STALK)
        }
        lookAt(ux, uy)
        sinceTouch = 0f
        return true
    }

    override fun touchUp(): Boolean {
        if (!touchDown) return false
        touchDown = false; sinceTouch = 0f
        val tap = !moved && time - downAt < 0.35f
        if (act == Act.PURR) act = Act.NONE
        if (tap) {
            if (downOnBody) { tapBody() } else batAt(tX, tY)
        }
        return true
    }

    override fun tapAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f; lookAt(ux, uy)
        if (onBody(ux, uy)) tapBody() else batAt(ux, uy)
        return true
    }

    override fun longPressAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        if (onBody(ux, uy)) { startAct(Act.PURR); return true }
        dotX = ux; dotY = uy; dotVis = 1f; dotStill = 0f
        startAct(Act.STALK)
        return true
    }

    private fun tapBody() {
        listener?.onBondXp(1)
        // Cats are cats: sometimes a happy squint, sometimes an offended ear flick.
        if (Random.nextFloat() < 0.7f) { startAct(Act.TRILL); spawnFx(1f, 0f, 0.45f, 0.9f) }
        else { earsDown = 0.6f; spawnFx(7f, 0.25f, 0.45f, 0.9f) }
    }

    private fun batAt(ux: Float, uy: Float) {
        pounceX = ux
        startAct(Act.BAT)
    }

    private fun lookAt(ux: Float, uy: Float) {
        lookTX = ((ux - bodyX) / size / 0.8f).coerceIn(-1f, 1f)
        lookTY = ((uy - bodyY - 0.06f * size) / size / 0.8f).coerceIn(-1f, 1f)
        nextWander = 3f + Random.nextFloat() * 2f
    }

    override fun greet() { startAct(Act.TRILL) }

    override fun perform(trick: String): Boolean {
        sinceTouch = 0f
        val a = when (trick) {
            "pounce" -> Act.POUNCE; "knock" -> Act.KNOCK; "cucumber" -> Act.CUCUMBER; "box" -> Act.BOX
            "zoomies" -> Act.ZOOM; "groom" -> Act.GROOM; "loaf" -> Act.LOAF; "trill" -> Act.TRILL
            "stretch" -> Act.STRETCH; "purr" -> Act.PURR
            else -> return false
        }
        startAct(a); return true
    }

    private fun startAct(a: Act) {
        act = a; actT = 0f
        when (a) {
            Act.PURR -> discover("purr")
            Act.POUNCE -> { vy = 1.0f * size; syV -= 3f; discover("pounce") }
            Act.KNOCK -> { cupVis = 1f; cupX = 0.48f; cupY = -0.47f; cupRot = 0f; cupFalling = false }
            Act.CUCUMBER -> cukeVis = 1f
            Act.BOX -> discover("box")
            Act.TRILL -> spawnFx(4f, 0.2f, 0.5f, 0.8f)
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
        fxVel[i * 2] = (Random.nextFloat() - 0.5f) * 0.1f
        fxVel[i * 2 + 1] = if (kind == 3f) 0f else 0.25f
    }

    // ---- Frame ----

    override fun update(dt: Float, input: SceneInput) {
        time += dt; sinceTouch += dt; actT += dt
        mood = input.mood
        val a = input.audio
        val musicOn = a.source != AudioFrame.Source.NONE
        val charging = input.charging
        val low = input.lowBattery && !charging

        val c = KITTY_COATS[input.coat.coerceIn(0, KITTY_COATS.size - 1)]
        val k = if (colorsInit) dt * 4f else 1f
        ease(fur, c.fur, k); ease(white, c.white, k); ease(stripe, c.stripe, k); ease(eye, c.eye, k)
        points += (c.points - points) * min(1f, k)
        ease(rim, if (input.accent != 0) input.accent else 0xFFCFDAFF.toInt(), k)
        ease(glow, if (musicOn) Color.HSVToColor(floatArrayOf((140f + time * 40f) % 360f, 0.7f, 1f)) else Color.rgb(90, 255, 115), dt * 3f)
        colorsInit = true

        if (mood == Mood.MORNING && !greetedMorning && act == Act.NONE) { greetedMorning = true; startAct(Act.STRETCH) }
        if (mood != Mood.MORNING) greetedMorning = false

        if (touchDown && !moved && !longFired && time - downAt > 0.6f) {
            longFired = true
            if (downOnBody) startAct(Act.PURR) else { dotX = tX; dotY = tY; dotVis = 1f; startAct(Act.STALK) }
        }

        if (act == Act.NONE && !charging && !low && !touchDown && sinceTouch > 3f) {
            nextIdle -= dt
            if (nextIdle <= 0f) {
                val pool = mutableListOf(Act.GROOM, Act.KNEAD, Act.LOAF, Act.KNOCK, Act.ZOOM, Act.CUCUMBER, Act.BOX)
                pool.removeAll(lastActs)
                val pick = pool.random()
                lastActs.addLast(pick); if (lastActs.size > 3) lastActs.removeFirst()
                startAct(pick)
                nextIdle = 7f + Random.nextFloat() * 7f
            }
        }

        // ---- Pose targets ----
        var loafT = 0f; var crouchT = 0f; var puffT = 0f
        var eT = 0f; var mT = 0f; var openT = 1f
        var headYT = 0f; var earsDownT = 0f; var shearT = 0f
        var pupilT = if (mood == Mood.NIGHT) 0.85f else 0.3f
        var tailB = 0.55f; var tailS = 0.15f; var tailRate = 1.2f
        var targetX = 0f
        var padT = 0f; var boxT = 0f
        setPaws(0f, 1f, 0f, 1f)

        if (charging && act == Act.NONE) {
            // Sipping glowing milk through a straw: gulp, gulp, gulp, then a happy "ahh".
            padT = 1f; tailB = 0.35f; tailS = 0.22f; tailRate = 2.2f
            val ph = time % 5f
            if (ph < 3.8f) {
                val gulp = sin(time * 7f)
                mT = 4f; eT = 1f; headYT = -0.035f + 0.012f * gulp; tiltT = -0.1f
                lookTX = -0.55f; lookTY = -0.5f
                if (gulp > 0.95f) { earL = 0.18f; earR = -0.12f }
            } else {
                mT = 3f; eT = 0f; pupilT = 0.95f; headYT = 0f; tiltT = 0.08f; lookTX = 0f; lookTY = 0f
                if (ph - dt <= 3.8f) spawnFx(1f, 0.2f, 0.42f, 0.8f)
            }
        } else if (charging) {
            padT = 1f
        } else if (low) {
            loafT = 1f; eT = 2f; earsDownT = 0.4f; tailB = 1.7f; tailS = 0.02f; headYT = -0.04f
        }

        // Night: wide-eyed, watching, eyes shining.
        val glowT = if (mood == Mood.NIGHT && !charging) 0.7f else 0f
        eyeGlow += (glowT - eyeGlow) * min(1f, dt * 2f)

        // Music: ears swivel on beats, tail sways with the tempo, head bobs on the hi-hats.
        if (musicOn && !charging) {
            if (a.beat >= 0.99f) { earL = if (Random.nextBoolean()) 0.35f else -0.25f; earR = -earL * 0.8f }
            tailRate = a.bpm.coerceAtLeast(60f) / 60f * PI.toFloat()
            tailS = 0.28f
            headYT += a.treble * 0.02f * sin(time * 20f)
        }

        when (act) {
            Act.BAT -> {
                val side = if (pounceX > bodyX) 1f else -1f
                val r = smooth(actT, 0.0f, 0.12f, 0.3f)
                if (side > 0) setPaws(0f, 1f, 1.9f * r, 1.1f) else setPaws(1.9f * r, 1.1f, 0f, 1f)
                pupilT = 1f; eT = 0f
                if (actT > 0.7f) act = Act.NONE
            }
            Act.TRILL -> {
                eT = 1f; mT = if (actT < 0.4f) 1f else 0f; tiltT = 0.2f; tailB = 0.05f; tailS = 0.06f
                if (actT > 1.2f) act = Act.NONE
            }
            Act.PURR -> {
                eT = 2f; openT = 0.35f; tiltT = 0.15f; tailS = 0.05f
                shearT = 0.004f * sin(time * 90f) // rumble
                if ((actT * 1.6f).toInt() != ((actT - dt) * 1.6f).toInt()) spawnFx(3f, 0f, -0.15f, 0.9f)
                if (!touchDown && actT > 2.5f) act = Act.NONE
            }
            Act.STALK -> {
                // Eyes locked on the dot, crouch low, butt wiggle, then pounce when the dot rests.
                lookAt(dotX, dotY)
                crouchT = 1f; pupilT = 1f; earsDownT = 0f; tailB = 1.35f; tailS = 0.05f
                dotStill += dt
                shearT = if (dotStill > 0.35f) 0.05f * sin(time * 28f) else 0f
                val reach = (halfW - 0.4f * size).coerceAtLeast(0f)
                targetX = ((dotX - homeX) * 0.4f).coerceIn(-reach, reach)
                if (dotStill > 1.1f && dotVis > 0.5f) { pounceX = dotX; startAct(Act.POUNCE) }
                if (!touchDown && actT > 6f) act = Act.NONE
            }
            Act.POUNCE -> {
                val reach = (halfW - 0.4f * size).coerceAtLeast(0f)
                targetX = (pounceX - homeX).coerceIn(-reach, reach)
                setPaws(1.2f, 1.1f, 1.2f, 1.1f)
                pupilT = 1f; eT = 0f; tailB = 0.9f
                if (actT > 0.45f && jumpY == 0f) {
                    // Landed: the dot "escapes".
                    dotVis = 0f
                    spawnFx(2f, 0f, 0.1f, 1.4f)
                    act = Act.NONE
                    listener?.onBondXp(2)
                    if (touchDown) startAct(Act.STALK)
                }
            }
            Act.GROOM -> {
                val ph = sin(actT * 9f)
                setPaws(0f, 1f, 2.4f, 0.75f)
                tiltT = 0.18f; eT = if (ph > 0f) 1f else 3f; openT = 0.5f
                mT = if (ph > 0f) 3f else 0f
                headYT = -0.01f * ph
                if (actT > 3.2f) act = Act.NONE
            }
            Act.KNEAD -> {
                val ph = sin(actT * 7f)
                setPaws(0.12f + 0.12f * ph, 0.85f, 0.12f - 0.12f * ph, 0.85f)
                eT = 2f; openT = 0.35f; tailS = 0.05f
                if ((actT * 1.2f).toInt() != ((actT - dt) * 1.2f).toInt()) spawnFx(3f, 0f, -0.2f, 0.9f)
                if (actT > 3.5f) act = Act.NONE
            }
            Act.LOAF -> {
                loafT = 1f; eT = 2f; openT = 0.4f; tailB = 1.6f; tailS = 0.03f
                if (actT > 6f) act = Act.NONE
            }
            Act.KNOCK -> {
                // Looks you in the eye... then pushes the cup off the ledge.
                if (actT < 1.4f) { lookTX = 0f; lookTY = 0f; eT = 0f; pupilT = 0.2f }
                else if (actT < 2.2f) {
                    lookTX = 1f; lookTY = -0.4f
                    val r = smooth(actT, 1.4f, 1.8f, 2.2f)
                    setPaws(0f, 1f, 1.5f * r, 1.25f)
                    if (actT > 1.75f && !cupFalling) { cupFalling = true; cupVy = 0.4f; discover("knock") }
                } else { lookTX = 0.6f; lookTY = -0.8f; eT = 1f }
                if (actT > 4.2f) { act = Act.NONE }
            }
            Act.ZOOM -> {
                val reach = (halfW - 0.4f * size).coerceAtLeast(0f)
                targetX = (if (actT < 0.6f) 1f else if (actT < 1.2f) -1f else if (actT < 1.7f) 0.6f else 0f) * reach
                pupilT = 1f; earsDownT = 0.4f; tailB = 0.2f; puffT = 0.3f
                if (actT > 2.4f) act = Act.NONE
            }
            Act.CUCUMBER -> {
                // A cucumber appears. Kitty notices. KITTY LEAVES THE GROUND.
                if (actT < 1.2f) { lookTX = -0.3f; lookTY = -0.6f }
                else if (actT < 1.25f) { vy = 1.2f * size; syV -= 4f; spawnFx(6f, 0f, 0.6f, 0.9f); discover("cucumber") }
                if (actT > 1.2f && actT < 3.2f) { eT = 4f; pupilT = 1f; puffT = 1f; earsDownT = 0.8f; mT = 1f; tailB = 0.1f; tailS = 0.02f }
                if (actT > 3.2f) cukeVis = max(0f, cukeVis - dt * 2f)
                if (actT > 4f) { act = Act.NONE; cukeVis = 0f }
            }
            Act.BOX -> {
                boxT = 1f
                headYT = -0.12f + 0.03f * sin(actT * 2f)
                lookTX = sin(actT * 1.3f) * 0.7f; lookTY = 0f
                pupilT = 1f
                if (actT > 5.5f) act = Act.NONE
            }
            Act.STRETCH -> {
                val s = smooth(actT, 0.3f, 1.0f, 2.2f)
                crouchT = 0.8f * s; setPaws(0.7f * s, 1f + 0.3f * s, 0.7f * s, 1f + 0.3f * s)
                mT = if (s > 0.5f) 2f else 0f; eT = if (s > 0.5f) 3f else 0f; openT = 1f - s
                tailB = 0.3f
                if (actT > 2.8f) act = Act.NONE
            }
            Act.NONE -> {}
        }

        // Props.
        if (dotVis > 0f && !touchDown && act != Act.STALK && act != Act.POUNCE) dotVis = max(0f, dotVis - dt * 2f)
        if (cupFalling) {
            cupVy -= 3.5f * dt; cupY += cupVy * dt; cupX += 0.25f * dt; cupRot += dt * 6f
            if (cupY < -2.5f) { cupFalling = false; cupVis = 0f }
        } else if (act != Act.KNOCK) cupVis = max(0f, cupVis - dt * 2f)
        boxVis += (boxT - boxVis) * min(1f, dt * 6f)
        padVis += (padT - padVis) * min(1f, dt * 4f)

        // Locomotion.
        vx += ((targetX - x) * 9f - vx * 4.5f) * dt
        x += vx * dt
        if (jumpY > 0f || vy > 0f) {
            vy -= 4.5f * size * dt; jumpY += vy * dt
            if (jumpY <= 0f) { jumpY = 0f; if (vy < -0.3f * size) syV -= 2.5f; vy = 0f }
        }

        nextWander -= dt
        if (nextWander <= 0f && !touchDown && act == Act.NONE) {
            lookTX = (Random.nextFloat() - 0.5f) * 1.3f; lookTY = (Random.nextFloat() - 0.4f) * 0.8f
            if (Random.nextFloat() < 0.4f) { earL = (Random.nextFloat() - 0.5f) * 0.6f; earR = (Random.nextFloat() - 0.5f) * 0.6f }
            tiltT = if (Random.nextFloat() < 0.25f) (Random.nextFloat() - 0.5f) * 0.4f else 0f
            nextWander = 2.5f + Random.nextFloat() * 3f
        }
        lookX += (lookTX - lookX) * min(1f, dt * 9f); lookY += (lookTY - lookY) * min(1f, dt * 9f)
        tilt += (tiltT - tilt) * min(1f, dt * 6f)
        headY += (headYT - headY) * min(1f, dt * 10f)
        loaf += (loafT - loaf) * min(1f, dt * 4f)
        crouch += (crouchT - crouch) * min(1f, dt * 8f)
        puff += (puffT - puff) * min(1f, dt * (if (puffT > puff) 14f else 2f))
        earsDown += (earsDownT - earsDown) * min(1f, dt * 6f)
        earL *= max(0f, 1f - dt * 1.5f); earR *= max(0f, 1f - dt * 1.5f)
        shear += (shearT - shear) * min(1f, dt * 20f)
        pupil += (pupilT - pupil) * min(1f, dt * 5f)
        tailBase += (tailB - tailBase) * min(1f, dt * 4f)
        tailSway += (tailS - tailSway) * min(1f, dt * 4f)
        tailPhase += dt * tailRate

        nextBlink -= dt
        if (nextBlink <= 0f) { blink = 0f; nextBlink = 3f + Random.nextFloat() * 4f }
        blink = min(1f, blink + dt * 9f)
        eyeOpen += (min(openT, blink) - eyeOpen) * min(1f, dt * 18f)
        eyeMode = eT; mouth = mT
        val syTarget = 1f + 0.01f * sin(time * 1.4f * 2f * PI.toFloat())
        syV += ((syTarget - sy) * 170f - syV * 9f) * dt; sy = (sy + syV * dt).coerceIn(0.8f, 1.2f)
        for (i in 0 until 4) paws[i] += (pawsT[i] - paws[i]) * min(1f, dt * 14f)

        for (i in 0 until 6) {
            if (fx[i * 4 + 3] == 0f) continue
            fx[i * 4 + 2] += fxSpeed[i] * dt
            fx[i * 4] += fxVel[i * 2] * dt; fx[i * 4 + 1] += fxVel[i * 2 + 1] * dt
            if (fx[i * 4 + 2] >= 1f) fx[i * 4 + 3] = 0f
        }
    }

    private fun setPaws(lr: Float, ll: Float, rr: Float, rl: Float) { pawsT[0] = lr; pawsT[1] = ll; pawsT[2] = rr; pawsT[3] = rl }
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
        var minX = bx - 0.6f * size; var maxX = bx + 0.62f * size
        var minY = by - 0.65f * size; var maxY = by + 0.85f * size
        if (dotVis > 0f) {
            minX = min(minX, dotX - 0.05f); maxX = max(maxX, dotX + 0.05f)
            minY = min(minY, dotY - 0.05f); maxY = max(maxY, dotY + 0.05f)
        }
        if (cupVis > 0f) { maxX = max(maxX, bx + (cupX + 0.15f) * size); minY = min(minY, by + (cupY - 0.12f) * size) }
        if (cukeVis > 0f) minX = min(minX, bx - 0.75f * size)
        if (boxVis > 0f || padVis > 0f) { minX = min(minX, bx - 0.55f * size); maxX = max(maxX, bx + 0.55f * size) }
        if (padVis > 0f) minX = min(minX, bx - 0.72f * size)
        GLES20.glUniform4f(u("uQuad"), minX, minY, maxX, maxY)
        GLES20.glUniform2f(u("uRes"), w.toFloat(), h.toFloat())
        GLES20.glUniform1f(u("uTime"), time % 600f)
        GLES20.glUniform2f(u("uPos"), bx, by)
        GLES20.glUniform1f(u("uSize"), size)
        GLES20.glUniform1f(u("uPx"), 1f / (size * min(w, h)))
        GLES20.glUniform2f(u("uSquash"), 1f / sy, sy)
        GLES20.glUniform1f(u("uShear"), shear + (vx * 0.08f).coerceIn(-0.05f, 0.05f))
        GLES20.glUniform2f(u("uLook"), lookX, lookY)
        GLES20.glUniform1f(u("uTilt"), tilt)
        GLES20.glUniform1f(u("uHeadY"), headY)
        GLES20.glUniform1f(u("uLoaf"), loaf)
        GLES20.glUniform1f(u("uCrouch"), crouch)
        GLES20.glUniform1f(u("uEyeOpen"), eyeOpen)
        GLES20.glUniform1f(u("uEyeMode"), eyeMode)
        GLES20.glUniform1f(u("uPupil"), pupil)
        GLES20.glUniform1f(u("uMouth"), mouth)
        GLES20.glUniform2f(u("uEars"), earL, earR)
        GLES20.glUniform1f(u("uEarsDown"), earsDown)
        GLES20.glUniform4f(u("uTail"), tailBase, -0.18f + 0.1f * loaf, tailPhase, tailSway)
        GLES20.glUniform1f(u("uPuff"), puff)
        GLES20.glUniform2f(u("uPawL"), paws[0], paws[1])
        GLES20.glUniform2f(u("uPawR"), paws[2], paws[3])
        GLES20.glUniform3f(u("uFur"), fur[0], fur[1], fur[2])
        GLES20.glUniform3f(u("uWhite"), white[0], white[1], white[2])
        GLES20.glUniform3f(u("uStripe"), stripe[0], stripe[1], stripe[2])
        GLES20.glUniform3f(u("uEye"), eye[0], eye[1], eye[2])
        GLES20.glUniform1f(u("uPoints"), points)
        GLES20.glUniform3f(u("uRim"), rim[0], rim[1], rim[2])
        GLES20.glUniform3f(u("uGlow"), glow[0], glow[1], glow[2])
        GLES20.glUniform1f(u("uEyeGlow"), eyeGlow)
        GLES20.glUniform4f(u("uPad"), padVis, input.battery, 0f, 0f)
        GLES20.glUniform4f(u("uBox"), boxVis, 0f, 0f, 0f)
        GLES20.glUniform4f(u("uCup"), cupX, cupY, cupRot, cupVis)
        GLES20.glUniform4f(u("uCuke"), -0.55f, -0.52f, 0f, cukeVis)
        GLES20.glUniform4f(u("uDot"), (dotX - bx) / size, (dotY - by) / size, 0f, dotVis)
        fxBuf.position(0); fxBuf.put(fx); fxBuf.position(0)
        GLES20.glUniform4fv(u("uFx"), 6, fxBuf)
        GLES20.glUniform1f(u("uBattIcon"), if (input.charging) padVis else 0f)
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
