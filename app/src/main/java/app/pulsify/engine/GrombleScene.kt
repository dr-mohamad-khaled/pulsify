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
import kotlin.math.sin
import kotlin.random.Random

data class GrombleCoat(val name: String, val fur: Int, val belly: Int, val horn: Int, val eye: Int, val rim: Int, val rainbow: Boolean)

val GROMBLE_COATS = listOf(
    GrombleCoat("Shadow", 0xFF211A33.toInt(), 0xFF4D3D6B.toInt(), 0xFFE62633.toInt(), 0xFFFFD933.toInt(), 0xFFB380FF.toInt(), false),
    GrombleCoat("Rainbow", 0xFF7333B3.toInt(), 0xFFBF80F2.toInt(), 0xFFFF80CC.toInt(), 0xFFFF99E6.toInt(), 0xFFFF99E6.toInt(), true),
    GrombleCoat("Ember", 0xFF801F1A.toInt(), 0xFFCC6640.toInt(), 0xFFFF991A.toInt(), 0xFFFFE666.toInt(), 0xFFFF994D.toInt(), false),
    GrombleCoat("Frost", 0xFF1A2E59.toInt(), 0xFF5980BF.toInt(), 0xFF66E6FF.toInt(), 0xFFCCF2FF.toInt(), 0xFF80D9FF.toInt(), false),
)

/**
 * Gromble: tries very hard to be evil and fails every time. Evil laugh into a coughing fit, an
 * EVIL PLAN that falls apart, a fireball that fizzles, a "BOO!" he is embarrassed about. He growls at
 * your finger from a distance and panics when it comes close. Secretly loves being petted.
 */
class GrombleScene(private val context: Context) : Creature {

    override var listener: SceneListener? = null

    private enum class Act { NONE, LAUGH, PLAN, FIRE, FLY, BOO, CAPE, ATTACK, POKE, PET, ZAP }

    private var program = 0
    private var quadVbo = 0
    private val uni = HashMap<String, Int>()
    private val bubble = SpeechBubble(context)
    private val fxBuf: FloatBuffer = ByteBuffer.allocateDirect(24 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val fx = FloatArray(24)
    private val fxSpeed = FloatArray(6)
    private val fxVel = FloatArray(12)

    private var w = 1; private var h = 1
    private var time = 0f
    private var size = 0.6f
    private var homeY = 0f
    override var bottomInset = 0f
        set(v) { field = v.coerceIn(0f, 0.8f); layout() }

    private var x = 0f; private var vx = 0f; private var targetX = 0f
    private var sq = 1f; private var sqV = 0f
    private var lean = 0f; private var lift = 0f
    private var lookX = 0f; private var lookY = 0f
    private var eyeOpen = 1f; private var eyeMode = 0f; private var brow = -0.6f; private var blush = 0f
    private var mouth = 0f
    private var armL = 0f; private var armR = 0f; private var cover = 0f; private var cross = 0f
    private var wing = 0f; private var tailPh = 0f; private var tailAmt = 0.25f
    private var puff = 0f
    private var capeVis = 0f; private var capeSwish = 0f; private var capeTangle = 0f
    private var propKind = 0f; private var propA = 0f; private var propB = 0f; private var propVis = 0f
    private var hornGlow = 0f
    private var blink = 1f; private var nextBlink = 3f
    private var beatPhase = 0f; private var lastBeat = 0f

    private var act = Act.NONE
    private var actT = 0f
    private var nextIdle = 3f
    private var lastAct = Act.NONE
    private var pokes = 0; private var lastPoke = -9f
    private var mood = Mood.DAY
    var sinceTouch = 99f; private set

    private var touchDown = false
    private var downAt = 0f
    private var downOnBody = false
    private var fX = 0f; private var fY = 0f
    private var moved = false
    private var longFired = false
    private var grumbled = -9f

    private val cFur = FloatArray(3); private val cBelly = FloatArray(3); private val cHorn = FloatArray(3); private val cEye = FloatArray(3)
    private val rim = FloatArray(3); private val glow = floatArrayOf(0.35f, 1f, 0.45f)
    private var rainbow = 0f
    private var colorsInit = false

    override val busy: Boolean
        get() = touchDown || sinceTouch < 2f || act != Act.NONE || abs(vx) > 0.01f || bubble.showing

    override fun onSurfaceCreated() {
        val vert = context.assets.open("shaders/mochi.vert").bufferedReader().use { it.readText() }
        val frag = context.assets.open("shaders/gromble.frag").bufferedReader().use { it.readText() }
        program = Gl.program(vert, frag)
        for (n in listOf("uQuad", "uRes", "uTime", "uPos", "uSize", "uPx", "uSquash", "uLean", "uLift", "uLook", "uEye", "uMouth", "uArms",
            "uWing", "uTail", "uPuff", "uCape", "uProp", "uFur", "uBelly", "uHorn", "uEyeC", "uRainbow", "uHornGlow", "uRim", "uGlow", "uBatt", "uFx", "uDim")) {
            uni[n] = GLES20.glGetUniformLocation(program, n)
        }
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0); quadVbo = ids[0]
        val q = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        val fb = ByteBuffer.allocateDirect(q.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(q)
        fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, q.size * 4, fb, GLES20.GL_STATIC_DRAW)
        bubble.create()
    }

    override fun onSurfaceChanged(width: Int, height: Int) { w = max(1, width); h = max(1, height); layout() }

    private val halfW get() = 0.5f * w / min(w, h)
    private val halfH get() = 0.5f * h / min(w, h)

    private var ground = Float.NaN
    override fun setGround(y: Float) {
        if (y == ground || (y.isNaN() && ground.isNaN())) return
        ground = y; layout()
    }

    private fun layout() {
        size = min(0.6f, halfW * 2f * 0.62f)
        var floor = if (ground.isNaN()) -halfH + 0.3f else ground
        if (h.toFloat() / w < 1.5f) { size = min(0.42f, halfH * 0.9f); floor = -halfH + 0.12f }
        if (bottomInset > 0f) {
            floor = -halfH + bottomInset * 2f * halfH + 0.04f
            size = min(size, (halfH - 0.1f - floor) / 1.25f)
        }
        homeY = floor + 0.55f * size
    }

    private val reach get() = (halfW - 0.42f * size).coerceAtLeast(0f)
    private fun toUv(nx: Float, ny: Float): Pair<Float, Float> {
        val m = min(w, h).toFloat()
        return Pair((nx - 0.5f) * w / m, (0.5f - ny) * h / m)
    }
    private fun onBody(ux: Float, uy: Float): Boolean {
        val cx = (ux - x) / size; val cy = (uy - homeY) / size + 0.2f
        return (cx / 0.36f) * (cx / 0.36f) + (cy / 0.42f) * (cy / 0.42f) < 1f
    }

    // ---- Touch ----

    override fun touchDown(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        fX = ux; fY = uy; moved = false; longFired = false; downAt = time; touchDown = true
        downOnBody = onBody(ux, uy)
        return true
    }

    override fun touchMove(nx: Float, ny: Float): Boolean {
        if (!touchDown) return false
        val (ux, uy) = toUv(nx, ny)
        if (hypot(ux - fX, uy - fY) > 0.03f) moved = true
        fX = ux; fY = uy; sinceTouch = 0f
        return true
    }

    override fun touchUp(): Boolean {
        if (!touchDown) return false
        touchDown = false; sinceTouch = 0f
        if (act == Act.PET) { actT = max(actT, 9f) }
        if (!moved && time - downAt < 0.35f && downOnBody) poke()
        return true
    }

    override fun tapAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        if (onBody(ux, uy)) poke() else { fX = ux; fY = uy; grumble() }
        return true
    }

    override fun longPressAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        if (onBody(ux, uy)) startAct(Act.PET)
        return true
    }

    private fun poke() {
        if (act == Act.ATTACK) return
        if (time - lastPoke > 3f) pokes = 0
        lastPoke = time; pokes++
        if (pokes >= 3) { pokes = 0; startAct(Act.ATTACK) } else startAct(Act.POKE)
    }

    private fun grumble() {
        if (time - grumbled < 2.5f || bubble.showing) return
        grumbled = time
        bubble.say(listOf("Grrr!", "Back off!", "I bite!", "Fear me!", "Hssss!").random(), 1.2f)
    }

    override fun greet() { startAct(Act.BOO) }

    override fun perform(trick: String): Boolean {
        sinceTouch = 0f
        val a = when (trick) {
            "laugh" -> Act.LAUGH; "plan" -> Act.PLAN; "fire" -> Act.FIRE; "fly" -> Act.FLY
            "boo" -> Act.BOO; "cape" -> Act.CAPE; "attack" -> Act.ATTACK; "pet" -> Act.PET
            else -> return false
        }
        startAct(a); return true
    }

    private fun isAsleep() = mood == Mood.NIGHT && act == Act.NONE && sinceTouch > 6f

    private fun startAct(a: Act) {
        act = a; actT = 0f; lastAct = a
        when (a) {
            Act.LAUGH -> bubble.say("MWAHAHAHA!", 1.5f)
            Act.PLAN -> bubble.say("My EVIL PLAN!", 1.6f)
            Act.FIRE -> bubble.say("Behold... FIRE!", 1.5f)
            Act.FLY -> bubble.say("I shall FLY!", 1.3f)
            Act.CAPE -> bubble.say("Dramatic swish!", 1.3f)
            Act.ATTACK -> bubble.say("RAAAWR!", 1.0f)
            Act.POKE -> { sqV -= 3f; bubble.say(listOf("Hey!", "Stop that!", "I'm SCARY!", "No poking!").random(), 1.1f); spawnFx(4f, 0.1f, 0.35f, 0.9f) }
            else -> {}
        }
    }

    private fun spawnFx(kind: Float, cx: Float, cy: Float, speed: Float) {
        var i = 0; var best = -1f
        for (k in 0 until 6) { val life = if (fx[k * 4 + 3] == 0f) 2f else fx[k * 4 + 2]; if (life > best) { best = life; i = k } }
        fx[i * 4] = cx; fx[i * 4 + 1] = cy; fx[i * 4 + 2] = 0f; fx[i * 4 + 3] = kind
        fxSpeed[i] = speed
        fxVel[i * 2] = (Random.nextFloat() - 0.5f) * 0.14f
        fxVel[i * 2 + 1] = if (kind == 3f) -0.2f else 0.25f
    }

    // ---- Frame ----

    override fun update(dt: Float, input: SceneInput) {
        time += dt; sinceTouch += dt; actT += dt
        bubble.update(dt)
        mood = input.mood
        val a = input.audio
        val musicOn = a.source != AudioFrame.Source.NONE
        val charging = input.charging
        val low = input.lowBattery && !charging
        val asleep = isAsleep()

        val coat = GROMBLE_COATS[input.coat.coerceIn(0, GROMBLE_COATS.size - 1)]
        val k = if (colorsInit) dt * 4f else 1f
        ease(cFur, coat.fur, k); ease(cBelly, coat.belly, k); ease(cHorn, coat.horn, k); ease(cEye, coat.eye, k); ease(rim, coat.rim, k)
        rainbow += ((if (coat.rainbow) 1f else 0f) - rainbow) * min(1f, k)
        colorsInit = true

        if (touchDown && !moved && !longFired && time - downAt > 0.6f && downOnBody) { longFired = true; startAct(Act.PET) }

        val dancing = musicOn && !charging && !asleep
        val bpm = if (musicOn && a.bpm > 40f) a.bpm.coerceIn(60f, 180f) else 120f
        beatPhase += dt * bpm / 60f
        if (musicOn && a.beat >= 0.99f && time - lastBeat > 0.25f) { lastBeat = time; beatPhase = 0f }
        if (beatPhase > 1f) beatPhase -= beatPhase.toInt().toFloat()

        if (act == Act.NONE && !touchDown && sinceTouch > 3f && !dancing && !asleep && !low) {
            nextIdle -= dt
            if (nextIdle <= 0f) {
                val pick = if (charging) Act.ZAP else listOf(Act.LAUGH, Act.PLAN, Act.FIRE, Act.FLY, Act.BOO, Act.CAPE).filter { it != lastAct }.random()
                startAct(pick)
                nextIdle = TRICK_GAP_MIN + Random.nextFloat() * TRICK_GAP_RANGE
            }
        }

        // ---- Pose targets: by default, a scowl with a swishing tail ----
        var sqT = 1f; var leanT = 0f; var liftT = 0f
        var lkx = 0f; var lky = 0f
        var eT = 0f; var openT = 1f; var browT = -0.6f; var blushT = 0f; var mT = 0f
        var aL = 0f; var aR = 0f; var coverT = 0f; var crossT = 0.6f
        var wingT = 0f; var tailT = 0.25f; var tailRate = 2f; var puffT = 0f
        var capeT = 0f; var swishT = 0f; var tangleT = 0f
        var pk = propKind; var pa = propA; var pb = propB; var pv = 0f
        var glowT = 0f
        var tx = targetX

        // Your finger: brave from far away, a coward up close.
        if (touchDown && !downOnBody && act == Act.NONE) {
            val d = hypot(fX - x, fY - (homeY - 0.2f * size)) / size
            lkx = ((fX - x) / size).coerceIn(-1f, 1f); lky = ((fY - homeY) / size + 0.2f).coerceIn(-1f, 1f)
            crossT = 0f
            if (d > 0.75f) {
                browT = -1f; mT = 5f; aL = 0.6f; aR = 0.6f; wingT = 0.6f; tailRate = 6f; tailT = 0.5f
                tx = (x + (fX - x) * 0.15f).coerceIn(-reach, reach)
                grumble()
            } else {
                eT = 3f; browT = 1f; mT = 3f; coverT = if (d < 0.5f) 1f else 0f; blushT = 0.4f
                sqT = 0.93f + 0.02f * sin(time * 40f)
                tx = (x - (if (fX > x) 1f else -1f) * 0.25f * size).coerceIn(-reach, reach)
                if ((time * 2f).toInt() != ((time - dt) * 2f).toInt()) spawnFx(3f, 0.25f * (if (fX > x) -1f else 1f), 0.2f, 1.0f)
            }
        }

        if (asleep) {
            // Asleep hugging the teddy he pretends he doesn't own.
            eT = 2f; browT = 0.3f; mT = 3f; crossT = 1f; pk = 3f; pv = 1f; tailT = 0.05f; sqT = 0.96f + 0.015f * sin(time * 1.6f)
            if ((time * 0.35f).toInt() != ((time - dt) * 0.35f).toInt()) spawnFx(8f, 0.25f, 0.3f, 0.3f)
        } else if (low && act == Act.NONE) {
            leanT = 1.45f; eT = 2f; browT = 1f; mT = 3f; crossT = 0f; tailT = 0f
            if ((time * 0.12f).toInt() != ((time - dt) * 0.12f).toInt()) bubble.say(listOf("Avenge me...", "So... weak...", "Feed... the phone").random(), 2.2f)
        } else if (charging && act == Act.NONE) {
            // Hugging a jar of lightning.
            pk = 4f; pv = 1f; pa = input.battery; pb = 0f; crossT = 1f; browT = -0.9f; mT = 0f; lky = -0.5f; glowT = 0.5f
        }

        if (dancing && act == Act.NONE && !touchDown) {
            // Headbang like a metal fan. Horns flash on the drops.
            val hb = 0.5f - 0.5f * cos(beatPhase * 2f * PI.toFloat())
            val hard = (0.6f + 0.7f * a.bass).coerceAtMost(1.3f)
            leanT = 0.28f * hb * hard * (if ((time * bpm / 60f).toInt() % 8 < 4) 1f else -1f)
            sqT = 1f - 0.12f * hb * hard
            eT = if (hb > 0.5f) 1f else 0f; browT = -1f; mT = if (a.mid > 0.5f) 5f else 1f
            aR = 1.1f; aL = 0.2f + 0.5f * hb; crossT = 0f; wingT = a.bass * 0.8f; tailRate = 8f; tailT = 0.5f
            glowT = (a.bass * 1.4f).coerceAtMost(1.5f)
            if (a.beat >= 0.99f && Random.nextFloat() < 0.5f) spawnFx(6f, (Random.nextFloat() - 0.5f) * 0.6f, 0.3f + Random.nextFloat() * 0.3f, 1.2f)
        }

        when (act) {
            Act.LAUGH -> {
                crossT = 0f
                if (actT < 1.6f) {
                    mT = 1f; eT = 1f; browT = -1f; aL = 1.1f; aR = 1.1f; sqT = 1f + 0.05f * sin(actT * 26f); wingT = 0.5f; glowT = 0.6f
                } else if (actT < 3.0f) {
                    // ...the coughing fit.
                    mT = 4f; eT = 1f; browT = 0.8f; coverT = 0.0f; aR = 0.9f; sqT = 0.92f + 0.07f * abs(sin(actT * 14f)); leanT = 0.18f
                    if ((actT * 4f).toInt() != ((actT - dt) * 4f).toInt()) spawnFx(1f, 0.08f, -0.2f, 0.9f)
                    if (actT - dt < 1.6f) bubble.say("*cough* *hack*", 1.3f)
                } else { mT = 3f; browT = 0.7f; blushT = 1f; lkx = -0.8f; lky = -0.3f }
                if (actT > 4.2f) act = Act.NONE
            }
            Act.PLAN -> {
                crossT = 0f; pk = 1f; pv = 1f; pb = 0f
                if (actT < 1.8f) { pa = 0f; browT = -1f; mT = 0f; aL = 0.25f; aR = 0.25f; lky = -0.4f; glowT = 0.3f }
                else {
                    pa = ((actT - 1.8f) / 0.7f).coerceIn(0f, 1f)
                    eT = 3f; browT = 1f; mT = 2f; lky = -0.8f
                    if (actT > 2.6f) { pv = 0f; mT = 3f; eT = 0f; blushT = 0.8f; lky = -0.6f }
                    if (actT - dt < 2.6f && actT >= 2.6f) bubble.say("...I have copies.", 1.5f)
                }
                if (actT > 4.4f) act = Act.NONE
            }
            Act.FIRE -> {
                crossT = 0f; pk = 2f; pv = 1f; aR = 1.25f; lkx = 0.7f; lky = 0.6f
                if (actT < 1.7f) { pa = (actT / 1.5f).coerceIn(0f, 1f); pb = 0f; browT = -1f; mT = 1f; glowT = pa }
                else {
                    pa = 1f; pb = ((actT - 1.7f) / 0.5f).coerceIn(0f, 1f)
                    eT = 3f; browT = 1f; mT = 2f
                    if (actT - dt < 1.75f) { spawnFx(1f, 0.34f, 0.2f, 0.7f); spawnFx(1f, 0.28f, 0.3f, 0.6f) }
                    if (actT > 2.4f) { pv = 0f; mT = 3f; eT = 0f; blushT = 1f; aR = 0f }
                    if (actT - dt < 2.5f && actT >= 2.5f) bubble.say("It was... a warm-up.", 1.6f)
                }
                if (actT > 4.4f) act = Act.NONE
            }
            Act.FLY -> {
                crossT = 0f
                if (actT < 2.2f) {
                    wingT = 0.5f + 0.5f * sin(actT * 34f); liftT = (actT / 2.2f) * 0.8f; browT = -0.3f; mT = 3f; eT = 0f
                    aL = 0.5f; aR = 0.5f; sqT = 1.04f
                    if ((actT * 2.5f).toInt() != ((actT - dt) * 2.5f).toInt()) spawnFx(3f, -0.25f, 0.2f, 1.0f)
                } else if (actT < 2.5f) { wingT = 0f; liftT = 0.8f; eT = 3f; browT = 1f; mT = 2f }
                else {
                    liftT = 0f
                    if (actT - dt < 2.75f && actT >= 2.75f) { sqV -= 5f; spawnFx(2f, -0.2f, 0.3f, 0.6f); spawnFx(2f, 0.2f, 0.35f, 0.5f); spawnFx(1f, 0f, -0.45f, 0.9f) }
                    eT = 4f; mT = 3f; browT = 0.5f
                    if (actT > 3.9f) { eT = 0f; blushT = 0.8f }
                }
                if (actT > 4.6f) act = Act.NONE
            }
            Act.BOO -> {
                crossT = 0f
                if (actT < 0.7f) { sqT = 0.72f; browT = -1f; mT = 0f; coverT = 0f; lky = -0.5f }
                else if (actT < 1.8f) {
                    if (actT - dt < 0.7f) { sqV += 5f; bubble.say("BOO!", 1.0f) }
                    aL = 1.3f; aR = 1.3f; mT = 5f; browT = -1f; wingT = 1f; glowT = 1f; puffT = 0.4f
                } else { coverT = 1f; blushT = 1f; browT = 1f; mT = 3f; eT = 1f
                    if (actT - dt < 1.9f && actT >= 1.9f) bubble.say("...was that scary?", 1.6f) }
                if (actT > 3.9f) act = Act.NONE
            }
            Act.CAPE -> {
                crossT = 0f; capeT = 1f
                if (actT < 1.6f) { swishT = 0.5f * sin(actT * 7f); aR = 0.9f; browT = -1f; mT = 0f; lkx = 0.6f; lky = 0.4f; glowT = 0.4f }
                else if (actT < 3.6f) {
                    tangleT = 1f; aL = 0.7f + 0.4f * sin(actT * 15f); aR = 0.7f - 0.4f * sin(actT * 15f)
                    tx = (x + 0.12f * size * sin(actT * 5f)).coerceIn(-reach, reach)
                    leanT = 0.18f * sin(actT * 9f)
                    if (actT - dt < 1.7f && actT >= 1.7f) bubble.say("Mmph! Who turned off the lights?!", 1.8f)
                } else if (actT < 4.6f) { tangleT = 1f; leanT = 1.45f; if (actT - dt < 3.75f && actT >= 3.75f) { sqV -= 3f; spawnFx(2f, 0.3f, 0.1f, 0.6f) } }
                else { capeT = 0f; mT = 3f; blushT = 1f; browT = 0.6f }
                if (actT > 5.6f) act = Act.NONE
            }
            Act.ATTACK -> {
                // Charges, trips, face-plants.
                crossT = 0f
                if (actT < 0.7f) { browT = -1f; mT = 5f; aL = 1.2f; aR = 1.2f; wingT = 1f; glowT = 1f; tx = (x + (if (x > 0f) -1f else 1f) * 0.5f * size).coerceIn(-reach, reach); leanT = 0.3f * (if (tx > x) 1f else -1f) }
                else if (actT < 2.4f) {
                    leanT = if (lean >= 0f) 1.5f else -1.5f
                    eT = 4f; mT = 3f; browT = 0.6f
                    if (actT - dt < 0.75f && actT >= 0.75f) { sqV -= 3f; spawnFx(2f, 0.3f, 0f, 0.6f); spawnFx(2f, -0.1f, 0.1f, 0.5f); spawnFx(1f, 0.2f, -0.4f, 0.8f) }
                } else { eT = 0f; mT = 3f; blushT = 1f; browT = 0.7f; lkx = -0.7f
                    if (actT - dt < 2.5f && actT >= 2.5f) bubble.say("I meant to do that.", 1.6f) }
                if (actT > 4.2f) act = Act.NONE
            }
            Act.POKE -> {
                browT = -1f; mT = 5f; aL = 0.9f; aR = 0.9f; crossT = 0f; wingT = 0.7f; puffT = 0.5f
                if (actT > 0.9f) act = Act.NONE
            }
            Act.PET -> {
                // Resists for a second, then melts.
                crossT = if (actT < 1.1f) 1f else 0f
                if (actT < 1.1f) { browT = -1f; mT = 3f; lkx = 0.8f; blushT = 0.5f }
                else {
                    eT = 1f; browT = 0.8f; mT = 1f; blushT = 1f; tailRate = 9f; tailT = 0.6f; sqT = 0.95f + 0.02f * sin(time * 9f)
                    if ((actT * 1.5f).toInt() != ((actT - dt) * 1.5f).toInt()) spawnFx(7f, (Random.nextFloat() - 0.5f) * 0.4f, 0.4f, 0.8f)
                    if (actT - dt < 2.2f && actT >= 2.2f) bubble.say("...don't tell anyone.", 1.8f)
                }
                if (!touchDown && actT > 3.2f) act = Act.NONE
            }
            Act.ZAP -> {
                // Charging: squeezes the jar, gets zapped, loves it.
                pk = 4f; pv = 1f; pa = input.battery; crossT = 1f
                if (actT < 0.9f) { pb = 0f; browT = -1f; mT = 0f; sqT = 0.94f }
                else if (actT < 1.7f) {
                    pb = 1f; puffT = 1f; eT = 3f; browT = 1f; mT = 2f; glowT = 1.4f; sqT = 1f + 0.04f * sin(actT * 60f)
                    if (actT - dt < 0.95f) { spawnFx(6f, 0.3f, 0.2f, 1.2f); spawnFx(6f, -0.3f, 0.1f, 1.2f); bubble.say(listOf("UNLIMITED POWER!", "Bzzzt!", "Yesss, VOLTS!").random(), 1.4f) }
                } else { pb = 0f; puffT = 0.4f; eT = 1f; mT = 1f; browT = -0.4f; glowT = 0.6f }
                if (actT > 3.0f || !charging) act = Act.NONE
            }
            Act.NONE -> {}
        }
        targetX = tx

        // ---- Integrate ----
        vx += ((targetX - x) * 12f - vx * 6f) * dt
        x += vx * dt
        if (act == Act.NONE && !touchDown && sinceTouch > 3f) targetX *= max(0f, 1f - dt * 0.3f)
        lean += (leanT - lean) * min(1f, dt * (if (abs(leanT) > 1f) 10f else 9f))
        lift += (liftT - lift) * min(1f, dt * (if (liftT < lift) 14f else 3f))
        lookX += (lkx - lookX) * min(1f, dt * 7f); lookY += (lky - lookY) * min(1f, dt * 7f)
        brow += (browT - brow) * min(1f, dt * 12f); blush += (blushT - blush) * min(1f, dt * 5f)
        mouth = mT
        armL += (aL - armL) * min(1f, dt * 12f); armR += (aR - armR) * min(1f, dt * 12f)
        cover += (coverT - cover) * min(1f, dt * 10f); cross += (crossT - cross) * min(1f, dt * 8f)
        wing += (wingT - wing) * min(1f, dt * 20f)
        tailPh += dt * tailRate; tailAmt += (tailT - tailAmt) * min(1f, dt * 5f)
        puff += (puffT - puff) * min(1f, dt * 12f)
        capeVis += (capeT - capeVis) * min(1f, dt * 8f); capeSwish += (swishT - capeSwish) * min(1f, dt * 10f); capeTangle += (tangleT - capeTangle) * min(1f, dt * 8f)
        propKind = pk; propA = pa; propB = pb; propVis += (pv - propVis) * min(1f, dt * 10f)
        hornGlow += (glowT - hornGlow) * min(1f, dt * 12f)
        nextBlink -= dt
        if (nextBlink <= 0f) { blink = 0f; nextBlink = 2.5f + Random.nextFloat() * 4f }
        blink = min(1f, blink + dt * 9f)
        eyeOpen += (min(openT, blink) - eyeOpen) * min(1f, dt * 20f)
        eyeMode = eT
        val n = (dt / 0.012f).toInt() + 1; val hs = dt / n
        for (i in 0 until n) { sqV += ((sqT - sq) * 190f - sqV * 11f) * hs; sq = (sq + sqV * hs).coerceIn(0.6f, 1.3f) }

        for (i in 0 until 6) {
            if (fx[i * 4 + 3] == 0f) continue
            fx[i * 4 + 2] += fxSpeed[i] * dt
            fx[i * 4] += fxVel[i * 2] * dt; fx[i * 4 + 1] += fxVel[i * 2 + 1] * dt
            if (fx[i * 4 + 2] >= 1f) fx[i * 4 + 3] = 0f
        }
        ease(glow, if (musicOn) Color.HSVToColor(floatArrayOf((time * 60f) % 360f, 0.7f, 1f)) else Color.rgb(90, 255, 115), dt * 3f)
    }

    override fun draw(input: SceneInput) {
        if (program == 0) return
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glUniform4f(u("uQuad"), x - 1.0f * size, homeY - 0.68f * size, x + 1.0f * size, homeY + 1.0f * size)
        GLES20.glUniform2f(u("uRes"), w.toFloat(), h.toFloat())
        GLES20.glUniform1f(u("uTime"), time % 600f)
        GLES20.glUniform2f(u("uPos"), x, homeY)
        GLES20.glUniform1f(u("uSize"), size)
        GLES20.glUniform1f(u("uPx"), 1f / (size * min(w, h)))
        GLES20.glUniform2f(u("uSquash"), 1f / kotlin.math.sqrt(sq), sq)
        GLES20.glUniform1f(u("uLean"), lean)
        GLES20.glUniform1f(u("uLift"), lift)
        GLES20.glUniform2f(u("uLook"), lookX, lookY)
        GLES20.glUniform4f(u("uEye"), eyeOpen, eyeMode, brow, blush)
        GLES20.glUniform1f(u("uMouth"), mouth)
        GLES20.glUniform4f(u("uArms"), armL, armR, cover, cross)
        GLES20.glUniform1f(u("uWing"), wing)
        GLES20.glUniform2f(u("uTail"), tailPh, tailAmt)
        GLES20.glUniform1f(u("uPuff"), puff)
        GLES20.glUniform4f(u("uCape"), capeVis, capeSwish, capeTangle, 0f)
        GLES20.glUniform4f(u("uProp"), propKind, propA, propB, propVis)
        GLES20.glUniform3f(u("uFur"), cFur[0], cFur[1], cFur[2])
        GLES20.glUniform3f(u("uBelly"), cBelly[0], cBelly[1], cBelly[2])
        GLES20.glUniform3f(u("uHorn"), cHorn[0], cHorn[1], cHorn[2])
        GLES20.glUniform3f(u("uEyeC"), cEye[0], cEye[1], cEye[2])
        GLES20.glUniform1f(u("uRainbow"), rainbow)
        GLES20.glUniform1f(u("uHornGlow"), hornGlow)
        GLES20.glUniform3f(u("uRim"), rim[0], rim[1], rim[2])
        GLES20.glUniform3f(u("uGlow"), glow[0], glow[1], glow[2])
        GLES20.glUniform1f(u("uBatt"), if (input.charging) 1f else 0f)
        fxBuf.position(0); fxBuf.put(fx); fxBuf.position(0)
        GLES20.glUniform4fv(u("uFx"), 6, fxBuf)
        GLES20.glUniform1f(u("uDim"), if (input.lowBattery) 0.3f else 0f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        bubble.draw(w, h, quadVbo, x + 0.12f * size, homeY + (0.42f + lift * 0.3f) * size, 1f, min(1f, size / 0.5f + 0.25f), rim)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    override fun release() {
        if (program != 0) GLES20.glDeleteProgram(program)
        if (quadVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
        program = 0; quadVbo = 0
        bubble.release()
    }

    private fun u(n: String) = uni[n] ?: -1

    private fun ease(dst: FloatArray, argb: Int, k: Float) {
        val kk = min(1f, k)
        dst[0] += (Color.red(argb) / 255f - dst[0]) * kk
        dst[1] += (Color.green(argb) / 255f - dst[1]) * kk
        dst[2] += (Color.blue(argb) / 255f - dst[2]) * kk
    }
}
