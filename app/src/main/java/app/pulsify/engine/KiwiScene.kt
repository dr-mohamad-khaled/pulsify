package app.pulsify.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
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

/** A parrot species: its own colours and markings on the shared bird rig. */
data class KiwiSpecies(
    val name: String,
    val body: Int, val belly: Int, val head: Int, val face: Int,
    val wing: Int, val edge: Int, val tail: Int, val beak: Int, val mark: Int,
)

val KIWI_SPECIES = listOf(
    KiwiSpecies("Ringneck", 0xFF66C233.toInt(), 0xFF9ED942.toInt(), 0xFF70CC3D.toInt(), 0xFF70CC3D.toInt(), 0xFF4DAB33.toInt(), 0xFF337A29.toInt(), 0xFF21859E.toInt(), 0xFFED3826.toInt(), 0xFFFF80A6.toInt()),
    KiwiSpecies("Budgie", 0xFF73D147.toInt(), 0xFF8CE052.toInt(), 0xFFFAEB59.toInt(), 0xFFFAEB59.toInt(), 0xFFD9D959.toInt(), 0xFF0D0D0D.toInt(), 0xFF264D99.toInt(), 0xFFD9C78C.toInt(), 0xFF7359D9.toInt()),
    KiwiSpecies("Cockatiel", 0xFF9E9EA6.toInt(), 0xFFB3B3B8.toInt(), 0xFFF7E073.toInt(), 0xFFFAE673.toInt(), 0xFF8C8C94.toInt(), 0xFF73737A.toInt(), 0xFF737380.toInt(), 0xFF999499.toInt(), 0xFFFF8C26.toInt()),
    KiwiSpecies("Lovebird", 0xFF6BC74D.toInt(), 0xFF8CDB61.toInt(), 0xFFF28040.toInt(), 0xFFF2402E.toInt(), 0xFF52A642.toInt(), 0xFF387A33.toInt(), 0xFF4D9959.toInt(), 0xFFF23326.toInt(), 0xFF4073F2.toInt()),
)

/**
 * Kiwi, the parrot. Perches on a real branch, sidesteps toward your finger, squawks, talks in
 * speech bubbles (your own words too), whistles, preens, hangs upside-down, flies a lap, copies the
 * rhythm you tap, and headbangs to the music.
 */
class KiwiScene(private val context: Context) : Creature {

    override var listener: SceneListener? = null

    private enum class Act { NONE, SQUAWK, SCRATCH, SEED, TALK, WHISTLE, PREEN, BOB, ONELEG, STRETCH, HANG, FLY, COPY, DANCE, NIBBLE }

    private var program = 0
    private var bubbleProg = 0
    private var quadVbo = 0
    private var textTex = 0
    private val uni = HashMap<String, Int>()
    private val bu = HashMap<String, Int>()
    private val fxBuf: FloatBuffer = ByteBuffer.allocateDirect(24 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val fx = FloatArray(24)
    private val fxSpeed = FloatArray(6)
    private val fxVel = FloatArray(12)

    private var w = 1; private var h = 1
    private var time = 0f
    private var size = 0.6f
    private var homeX = 0f
    private var branchBase = -0.3f
    private var branchR = 0.04f
    private var branchSway = 0f; private var branchSwayV = 0f
    override var bottomInset = 0f
        set(v) { field = v.coerceIn(0f, 0.8f); layout() }

    // Rig state.
    private var x = 0f; private var vx = 0f; private var targetX = 0f
    private var flyX = 0f; private var flyY = 0f
    private var face = 1f; private var faceT = 1f
    private var lean = 0.12f
    private var headX = 0f; private var headY = 0f; private var nod = 0f; private var headFlip = 1f
    private var turn = 0f; private var hang = 0f; private var fluff = 0f; private var crest = 0.2f
    private var wingOpen = 0f; private var wingSpread = 0f
    private var beak = 0f
    private var eyeOpen = 1f; private var eyeMode = 0f; private var eyeLook = 0f
    private var legNear = 0f; private var legFar = 0f
    private var tailSway = 0f
    private var sq = 1f; private var sqV = 0f
    private var blink = 1f; private var nextBlink = 3f
    private var stepPhase = 0f
    private var footN = 0f; private var footF = 0f; private var footNT = 0f; private var footFT = 0f
    // Sideways swipes (or home-screen page scrolls) grab a wing and pull it open with the finger.
    private var pullL = 0f; private var pullR = 0f; private var pullLT = 0f; private var pullRT = 0f
    private var swipeX0 = 0f; private var swipeY0 = 0f; private var swiping = false
    private var flutterT = 0f; private var lastPar = 0f

    // Behaviour.
    private var act = Act.NONE
    private var actT = 0f
    private var nextIdle = 3f
    private val lastActs = ArrayDeque<Act>()
    private var greetedMorning = false
    private var mood = Mood.DAY
    var sinceTouch = 99f; private set

    // Touch.
    private var touchDown = false
    private var downAt = 0f
    private var downOnBody = false
    private var tX = 0f; private var tY = 0f
    private var moved = false
    private var longFired = false
    private var following = false
    private var followX = 0f
    private val bodyTaps = FloatArray(3)
    private var bodyTapN = 0

    // Rhythm copy: taps on the background, then Kiwi plays them back.
    private val taps = FloatArray(12)
    private var tapN = 0
    private val play = FloatArray(12)
    private var playN = 0
    private var playIdx = 0
    private var bobPulse = 0f

    // Seed.
    private var seedX = 0f; private var seedY = 0f; private var seedVis = 0f; private var seedCracked = 0f
    private var seedHeld = false

    // Music.
    private var beatPhase = 0f
    private var lastBeat = 0f
    private var danceUntil = 0f

    // Charging snack.
    private var millet = 0f

    // Speech bubble.
    private val words = ArrayList<String>()
    private var wordsSrc = ""
    private var bubbleText = ""
    private var bubbleDirty = false
    private var bubbleT = 99f
    private var bubbleDur = 0f
    private var bubbleW = 1f      // text width / height in the texture
    private var nextChat = 12f
    private val tw = 512; private val th = 128
    private var bmp: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 70f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.LEFT
    }

    // Colours.
    private val cBody = FloatArray(3); private val cBelly = FloatArray(3); private val cHead = FloatArray(3); private val cFace = FloatArray(3)
    private val cWing = FloatArray(3); private val cEdge = FloatArray(3); private val cTail = FloatArray(3); private val cBeak = FloatArray(3); private val cMark = FloatArray(3)
    private val rim = FloatArray(3); private val glow = floatArrayOf(0.35f, 1f, 0.45f)
    private val amb = floatArrayOf(1f, 1f, 1f)
    private var colorsInit = false
    private var species = 0

    override val busy: Boolean
        get() = touchDown || sinceTouch < 2f || act != Act.NONE || abs(vx) > 0.01f || bubbleT < bubbleDur + 0.3f || seedVis > 0.01f || time < danceUntil

    override fun onSurfaceCreated() {
        val vert = context.assets.open("shaders/mochi.vert").bufferedReader().use { it.readText() }
        val frag = context.assets.open("shaders/kiwi.frag").bufferedReader().use { it.readText() }
        val bfrag = context.assets.open("shaders/bubble.frag").bufferedReader().use { it.readText() }
        program = Gl.program(vert, frag)
        for (n in listOf("uQuad", "uRes", "uTime", "uPos", "uSize", "uPx", "uFace", "uLean", "uHead", "uTurn", "uHang", "uFluff",
            "uCrest", "uWing", "uWingPull", "uBeak", "uEyeS", "uLegs", "uFeet", "uTailSway", "uSquash", "uSpecies", "uBody", "uBelly", "uHeadC", "uFaceC",
            "uWingC", "uEdgeC", "uTailC", "uBeakC", "uMark", "uAmb", "uRim", "uGlow", "uBranch", "uSeed", "uMillet", "uFx", "uDim")) {
            uni[n] = GLES20.glGetUniformLocation(program, n)
        }
        bubbleProg = Gl.program(vert, bfrag)
        for (n in listOf("uQuad", "uRes", "uTex", "uBox", "uTip", "uPop", "uPx", "uTexW", "uRim", "uAlpha")) {
            bu[n] = GLES20.glGetUniformLocation(bubbleProg, n)
        }
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0); quadVbo = ids[0]
        val q = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        val fb = ByteBuffer.allocateDirect(q.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(q)
        fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, q.size * 4, fb, GLES20.GL_STATIC_DRAW)
        GLES20.glGenTextures(1, ids, 0); textTex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textTex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        if (bubbleText.isNotEmpty()) bubbleDirty = true
    }

    override fun onSurfaceChanged(width: Int, height: Int) { w = max(1, width); h = max(1, height); layout() }

    private val halfW get() = 0.5f * w / min(w, h)
    private val halfH get() = 0.5f * h / min(w, h)

    private fun layout() {
        size = min(0.6f, halfW * 2f * 0.6f)
        branchR = 0.042f * min(1f, size / 0.5f + 0.2f)
        var top = -halfH * 0.32f
        if (h.toFloat() / w < 1.5f) { size = min(0.42f, halfW * 0.8f); top = -halfH + 0.55f }
        if (bottomInset > 0f) {
            val floor = -halfH + bottomInset * 2f * halfH + 0.08f
            size = min(size, (halfH - 0.12f - floor) / 1.15f)
            top = max(top, floor)
        }
        branchBase = top - branchR
    }

    // Branch curve: identical to the shader.
    private fun branchY(bx: Float) = branchBase + 0.022f * sin(bx * 2.3f + 0.6f) + 0.035f * bx + branchSway
    private fun branchRad(bx: Float) = branchR * (1f - 0.18f * (bx / max(halfW, 0.1f)).coerceIn(-1f, 1f))
    private fun branchTop(bx: Float) = branchY(bx) + branchRad(bx)

    private val birdX get() = homeX + x + flyX
    private val birdY get() = branchTop(homeX + x) + 0.5f * size + flyY
    private val reach get() = (halfW - 0.32f * size).coerceAtLeast(0f)

    private fun toUv(nx: Float, ny: Float): Pair<Float, Float> {
        val m = min(w, h).toFloat()
        return Pair((nx - 0.5f) * w / m, (0.5f - ny) * h / m)
    }

    private fun onBody(ux: Float, uy: Float): Boolean {
        val cx = (ux - birdX) / size; val cy = (uy - birdY) / size
        if (hang > 0.5f) return hypot(cx, cy + 1.0f) < 0.38f
        return (cx / 0.28f) * (cx / 0.28f) + ((cy + 0.05f) / 0.45f) * ((cy + 0.05f) / 0.45f) < 1f
    }

    // ---- Touch ----

    override fun touchDown(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        tX = ux; tY = uy; moved = false; longFired = false; downAt = time; touchDown = true
        downOnBody = onBody(ux, uy)
        swipeX0 = ux; swipeY0 = uy; swiping = false
        if (!downOnBody && act != Act.FLY && act != Act.SEED) { following = true; followX = ux }
        return true
    }

    override fun touchMove(nx: Float, ny: Float): Boolean {
        if (!touchDown) return false
        val (ux, uy) = toUv(nx, ny)
        if (hypot(ux - tX, uy - tY) > 0.03f) moved = true
        if (!downOnBody) { followX = ux; tY = uy; tX = ux }
        // A clear sideways swipe: grab the wing on the finger's side and pull it open.
        if (!downOnBody && act != Act.FLY && !swiping && abs(ux - swipeX0) > 0.1f && abs(ux - swipeX0) > abs(uy - swipeY0) * 1.3f) {
            swiping = true; following = false
        }
        if (swiping) {
            val d = ux - birdX
            val amt = (abs(d) / (size * 1.6f)).coerceIn(0.15f, 1f)
            if (d < 0f) { pullLT = amt; pullRT = 0f } else { pullRT = amt; pullLT = 0f }
        }
        sinceTouch = 0f
        return true
    }

    override fun touchUp(): Boolean {
        if (!touchDown) return false
        touchDown = false; following = false; sinceTouch = 0f
        if (swiping) {
            // Let go: the wing snaps back with a quick flutter and a squawk.
            swiping = false; pullLT = 0f; pullRT = 0f; flutterT = 0.6f
            if (bubbleT > bubbleDur) say(listOf("hey!", "my wing!", "rawk!", "wheee").random(), 0f)
            return true
        }
        val tap = !moved && time - downAt < 0.35f
        if (act == Act.SCRATCH) { act = Act.NONE; spawnFx(1f, 0.05f, 0.45f, 0.9f) }
        if (tap) { if (downOnBody) tapBody() else tapBackground(tX, tY) }
        return true
    }

    override fun tapAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        if (onBody(ux, uy)) tapBody() else tapBackground(ux, uy)
        return true
    }

    override fun longPressAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        if (onBody(ux, uy)) startAct(Act.SCRATCH) else offerSeed(ux, uy)
        return true
    }

    private fun tapBody() {
        if (isAsleep()) { startAct(Act.SQUAWK); say(listOf("5 more minutes...", "zzz... huh?", "Shh. Sleeping.").random(), 2f); return }
        // Three quick taps: it takes off for a lap.
        bodyTaps[bodyTapN % 3] = time; bodyTapN++
        if (bodyTapN >= 3 && time - bodyTaps[(bodyTapN - 3) % 3] < 1.2f && act != Act.FLY) { bodyTapN = 0; startAct(Act.FLY); return }
        startAct(Act.SQUAWK)
    }

    private fun tapBackground(ux: Float, uy: Float) {
        // Record the rhythm: a run of taps with gaps under 1.1 s.
        if (tapN > 0 && time - taps[tapN - 1] > 1.1f) tapN = 0
        if (tapN < taps.size) taps[tapN++] = time
        faceT = if (ux > birdX) 1f else -1f
        eyeLook = ((uy - birdY) / size).coerceIn(-1f, 1f)
        crest = min(1f, crest + 0.3f)
        if (act == Act.NONE && !isAsleep()) targetX = ((ux - homeX) * 0.5f).coerceIn(-reach, reach)
    }

    private fun offerSeed(ux: Float, uy: Float) {
        if (act == Act.FLY) return
        seedX = ux; seedY = uy; seedVis = 1f; seedCracked = 0f; seedHeld = false
        startAct(Act.SEED)
    }

    override fun greet() {
        if (act == Act.FLY) return
        startAct(Act.TALK)
        say(listOf("Welcome back!", "Hiii!", "There you are!", "Missed you!").random(), 1.8f)
        wingOpen = 0.6f
    }

    override fun perform(trick: String): Boolean {
        sinceTouch = 0f
        if (trick.startsWith("say:")) { startAct(Act.TALK); say(trick.removePrefix("say:").take(40), 2.2f); return true }
        when (trick) {
            "talk" -> { startAct(Act.TALK); say(pickPhrase(null), 0f) }
            "whistle" -> startAct(Act.WHISTLE)
            "dance" -> { danceUntil = time + 7f; startAct(Act.DANCE) }
            "fly" -> startAct(Act.FLY)
            "hang" -> startAct(Act.HANG)
            "seed" -> offerSeed(birdX + face * 0.5f * size, birdY + 0.1f * size)
            "preen" -> startAct(Act.PREEN)
            "stretch" -> startAct(Act.STRETCH)
            "squawk" -> startAct(Act.SQUAWK)
            "oneleg" -> startAct(Act.ONELEG)
            else -> return false
        }
        return true
    }

    private fun isAsleep() = mood == Mood.NIGHT && act == Act.NONE && sinceTouch > 6f && time > danceUntil

    private fun startAct(a: Act) {
        act = a; actT = 0f
        when (a) {
            Act.SQUAWK -> {
                crest = 1f; sqV -= 3f; turn = max(turn, 0.3f)
                spawnFx(6f, 0.15f * face, 0.55f, 0.9f)
                if (Random.nextFloat() < 0.5f) spawnFx(7f, -0.1f * face, 0.1f, 0.35f)
                if (Random.nextFloat() < 0.55f) say(listOf("SQUAWK!", "Hello!", "Pretty bird!", "Hey!", "Boop!", "Tickles!").random(), 1.3f)
            }
            Act.FLY -> { sqV -= 4f; say(listOf("Wheee!", "Fly time!", "Watch this!").random(), 1.2f) }
            Act.WHISTLE -> spawnFx(3f, 0.25f * face, 0.25f, 0.6f)
            Act.STRETCH -> {}
            Act.COPY -> { turn = 0.8f }
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
        fxVel[i * 2] = (Random.nextFloat() - 0.5f) * 0.12f + (if (kind == 3f) 0.12f * face else 0f)
        fxVel[i * 2 + 1] = when (kind) { 7f -> -0.18f; 4f -> 0f; else -> 0.28f }
    }

    // ---- Speech ----

    private fun say(text: String, dur: Float) {
        bubbleText = text
        bubbleDirty = true
        bubbleT = 0f
        bubbleDur = if (dur > 0f) dur else (1.0f + 0.07f * text.length).coerceIn(1.4f, 3.2f)
    }

    private fun pickPhrase(input: SceneInput?): String {
        if (words.isNotEmpty() && Random.nextFloat() < 0.5f) return words.random()
        val pool = ArrayList<String>(16)
        pool += listOf("Hello!", "Pretty bird!", "Who's a good bird?", "Kiwi wants a seed!", "Peekaboo!", "I love you!", "Tickle me!", "Squawk squawk!", "Give me a scratch?")
        val now = java.time.LocalTime.now()
        val hh = if (now.hour % 12 == 0) 12 else now.hour % 12
        pool += "It's $hh:${now.minute.toString().padStart(2, '0')}!"
        if (input != null) {
            pool += "Battery ${(input.battery * 100).toInt()}%!"
            when (input.mood) {
                Mood.MORNING -> pool += listOf("Good morning!", "Rise and shine!", "Breakfast?")
                Mood.EVENING -> pool += listOf("Good evening!", "Sunset vibes")
                Mood.NIGHT -> pool += listOf("Sleepy...", "Shh, night time")
                else -> pool += listOf("Play with me!", "Bored. Poke me.")
            }
            if (input.audio.source != AudioFrame.Source.NONE) pool += listOf("This song slaps!", "Turn it up!", "Headbang!")
            if (input.charging) pool += listOf("Mmm, electricity", "Snack time!")
            if (input.lowBattery) pool += listOf("Battery low! SQUAWK!", "Feed the phone!")
        }
        return pool.random()
    }

    private fun loadWords(src: String) {
        if (src == wordsSrc) return
        wordsSrc = src
        words.clear()
        src.split(',', '\n').map { it.trim().take(40) }.filter { it.isNotEmpty() }.take(12).forEach { words += it }
    }

    private fun uploadText() {
        val b = bmp ?: return
        bubbleDirty = false
        b.eraseColor(Color.TRANSPARENT)
        var ts = 70f
        paint.textSize = ts
        var tw0 = paint.measureText(bubbleText)
        if (tw0 > tw - 24f) { ts *= (tw - 24f) / tw0; paint.textSize = ts; tw0 = paint.measureText(bubbleText) }
        val cv = Canvas(b)
        val fm = paint.fontMetrics
        val baseY = th * 0.5f - (fm.ascent + fm.descent) * 0.5f
        cv.drawText(bubbleText, 12f, baseY, paint)
        bubbleW = (tw0 + 24f) / th
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textTex)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
    }

    // ---- Frame ----

    override fun update(dt: Float, input: SceneInput) {
        time += dt; sinceTouch += dt; actT += dt; bubbleT += dt
        mood = input.mood
        loadWords(input.kiwiWords)
        val a = input.audio
        val musicOn = a.source != AudioFrame.Source.NONE
        val charging = input.charging
        val low = input.lowBattery && !charging
        val night = mood == Mood.NIGHT

        species = input.coat.coerceIn(0, KIWI_SPECIES.size - 1)
        val sp = KIWI_SPECIES[species]
        val k = if (colorsInit) dt * 4f else 1f
        ease(cBody, sp.body, k); ease(cBelly, sp.belly, k); ease(cHead, sp.head, k); ease(cFace, sp.face, k)
        ease(cWing, sp.wing, k); ease(cEdge, sp.edge, k); ease(cTail, sp.tail, k); ease(cBeak, sp.beak, k); ease(cMark, sp.mark, k)
        ease(rim, if (input.accent != 0) input.accent else 0xFFD2C6FF.toInt(), k)
        ease(glow, if (musicOn) Color.HSVToColor(floatArrayOf((140f + time * 40f) % 360f, 0.7f, 1f)) else Color.rgb(90, 255, 115), dt * 3f)
        ambientFor(input, if (colorsInit) dt * 2f else 1f)
        colorsInit = true

        if (mood == Mood.MORNING && !greetedMorning && act == Act.NONE) {
            greetedMorning = true; startAct(Act.STRETCH); say(listOf("Good morning!", "Rise and shine!").random(), 2f)
        }
        if (mood != Mood.MORNING) greetedMorning = false

        // Long press while the finger stays still.
        if (touchDown && !moved && !longFired && time - downAt > 0.6f) {
            longFired = true
            if (downOnBody) startAct(Act.SCRATCH) else { following = false; offerSeed(tX, tY) }
        }

        // Rhythm playback once the taps stop.
        if (tapN >= 3 && time - taps[tapN - 1] > 1.0f && act != Act.FLY && act != Act.SEED) {
            playN = tapN; for (i in 0 until tapN) play[i] = taps[i] - taps[0]
            tapN = 0; playIdx = 0
            startAct(Act.COPY)
        }

        // Music: track the beat phase.
        val dancing = (musicOn && !charging && !night) || time < danceUntil
        val bpm = if (musicOn && a.bpm > 40f) a.bpm.coerceIn(60f, 180f) else 118f
        beatPhase += dt * bpm / 60f
        if (musicOn && a.beat >= 0.99f && time - lastBeat > 0.25f) { lastBeat = time; beatPhase = 0f }
        if (!musicOn && time < danceUntil) { /* the demo beat keeps its own phase */ }
        if (beatPhase > 1f) beatPhase -= beatPhase.toInt().toFloat()

        // Idle choice.
        if (act == Act.NONE && !touchDown && sinceTouch > 3f && !dancing) {
            nextIdle -= dt
            if (nextIdle <= 0f) {
                val pick = when {
                    charging -> Act.NIBBLE
                    low -> Act.TALK
                    night -> listOf(Act.PREEN, Act.ONELEG, Act.STRETCH).random()
                    else -> {
                        val pool = mutableListOf(Act.TALK, Act.WHISTLE, Act.PREEN, Act.BOB, Act.ONELEG, Act.STRETCH, Act.HANG, Act.TALK)
                        if (Random.nextFloat() < 0.18f) pool += Act.FLY
                        pool.removeAll(lastActs)
                        pool.random()
                    }
                }
                if (pick != Act.NONE) {
                    lastActs.addLast(pick); if (lastActs.size > 3) lastActs.removeFirst()
                    startAct(pick)
                    if (pick == Act.TALK) say(pickPhrase(input), 0f)
                }
                nextIdle = TRICK_GAP_MIN + Random.nextFloat() * TRICK_GAP_RANGE
            }
        }

        // ---- Pose targets ----
        var leanT = 0.12f; var hxT = 0f; var hyT = 0f; var nodT = 0f; var flipT = 1f
        var turnT = 0f; var hangT = 0f; var fluffT = 0f; var crestT = 0.2f
        var wingT = 0f; var spreadT = 0f; var beakT = 0f
        var openT = 1f; var eT = 0f; var lookT = eyeLook
        var legNT = 0f; var legFT = 0f; var swayT = 0.04f * sin(time * 1.3f)
        var milletT = 0f
        var flyXT = 0f; var flyYT = 0f
        var flying = false

        if (charging) milletT = 1f
        if (low) { fluffT = 0.5f; leanT = -0.05f; openT = 0.55f; crestT = 0f; hyT = -0.02f }

        // Sleeping at night: fluffed up on one leg, head tucked into the back.
        if (isAsleep()) {
            fluffT = 1f; legNT = 1f; flipT = -1f; hxT = -0.06f; hyT = -0.07f; nodT = 0.35f; eT = 2f; crestT = 0f
            if (fx[23] == 0f || fx[22] >= 1f) spawnFx(5f, -0.15f, 0.35f, 0.3f, slot = 5)
        }

        // Following a finger: sidestep along the branch, face it, look at it.
        if (following && act != Act.FLY && act != Act.SEED && act != Act.HANG) {
            targetX = ((followX - homeX) * 0.7f).coerceIn(-reach, reach)
            faceT = if (followX > birdX + 0.02f) 1f else if (followX < birdX - 0.02f) -1f else faceT
            lookT = ((tY - birdY) / size).coerceIn(-1f, 1f)
            crestT = 0.5f
        }

        // Dance: the famous parrot headbang, side steps on every other beat.
        if (dancing && (act == Act.NONE || act == Act.DANCE)) {
            val hb = 0.5f - 0.5f * cos(beatPhase * 2f * PI.toFloat())
            val hard = if (musicOn) (0.6f + 0.6f * a.bass).coerceAtMost(1.2f) else 1f
            nodT = 0.55f * hb * hard; hyT = -0.06f * hb * hard; hxT = 0.04f * hb; leanT = 0.12f + 0.3f * hb * hard
            crestT = 0.4f + 0.6f * (1f - hb)
            turnT = 0.25f
            wingT = if (musicOn) a.bass * 0.25f else 0.08f
            beakT = if (musicOn) a.mid * 0.5f else 0.15f
            eT = if (hb > 0.6f) 1f else 0f
            val bar = (time * bpm / 60f).toInt()
            if (act == Act.NONE || act == Act.DANCE) targetX = (if (bar % 4 < 2) -0.05f else 0.05f) * size * 2f
            if (bar % 2 == 0) legFT = 0.35f * hb
            if (beatPhase < dt * bpm / 60f * 1.5f && Random.nextFloat() < 0.35f) spawnFx(2f, (Random.nextFloat() - 0.5f) * 0.4f, 0.3f + Random.nextFloat() * 0.3f, 1.1f)
            swayT = 0.15f * sin(beatPhase * 2f * PI.toFloat())
            if (act == Act.DANCE && time > danceUntil && !musicOn) act = Act.NONE
        }

        when (act) {
            Act.SQUAWK -> {
                crestT = 1f; beakT = if (actT < 0.45f) 0.9f else 0f; wingT = if (actT < 0.3f) 0.35f else 0f
                turnT = 0.4f; fluffT = max(fluffT, 0.3f); eT = 3f
                if (actT > 0.9f) act = Act.NONE
            }
            Act.SCRATCH -> {
                // Head down for a scratch, feathers puff up, eyes closed with bliss.
                nodT = 0.7f; hyT = -0.04f; hxT = 0.03f; fluffT = 1f; eT = 1f; crestT = 0.9f; turnT = 0.6f
                if ((actT * 1.4f).toInt() != ((actT - dt) * 1.4f).toInt()) spawnFx(1f, (Random.nextFloat() - 0.3f) * 0.3f, 0.5f, 0.8f)
                if (!touchDown && actT > 2.5f) act = Act.NONE
            }
            Act.TALK -> {
                turnT = 0.85f; crestT = 0.6f
                val talking = bubbleT < bubbleDur * 0.75f
                beakT = if (talking) 0.25f + 0.35f * (0.5f + 0.5f * sin(time * 24f)) else 0f
                nodT = if (talking) 0.06f * sin(time * 9f) else 0f
                if (actT > bubbleDur + 0.4f) act = Act.NONE
            }
            Act.WHISTLE -> {
                // A little tune: beak barely open, head tilting with each note.
                turnT = 0.35f; crestT = 0.7f; beakT = 0.18f; eT = 1f
                nodT = -0.15f + 0.12f * sin(actT * 6f)
                if ((actT * 2.2f).toInt() != ((actT - dt) * 2.2f).toInt()) spawnFx(3f, 0.2f * face, 0.25f, 0.55f)
                if (actT > 3.2f) act = Act.NONE
            }
            Act.PREEN -> {
                // Turns its head back and nibbles the wing; a loose feather drifts down.
                flipT = -1f; hxT = -0.05f; hyT = -0.06f; nodT = 0.5f + 0.15f * sin(actT * 14f)
                beakT = 0.2f + 0.2f * sin(actT * 20f); fluffT = 0.4f; eT = if (actT > 1.5f) 1f else 0f
                if (actT > 1.0f && actT - dt <= 1.0f) spawnFx(7f, -0.15f, -0.05f, 0.3f)
                if (actT > 3.4f) act = Act.NONE
            }
            Act.BOB -> {
                // Excited head bobbing, the way parrots do when they want attention.
                val bb = 0.5f - 0.5f * cos(actT * 14f)
                hyT = -0.05f * bb; nodT = 0.25f * bb; turnT = 0.6f; crestT = 0.8f; eT = 3f
                if (actT > 2.4f) act = Act.NONE
            }
            Act.ONELEG -> {
                legNT = 1f; fluffT = 0.45f; eT = if (actT > 1.2f) 1f else 0f; crestT = 0.1f
                if (actT > 6f) act = Act.NONE
            }
            Act.STRETCH -> {
                // One wing stretched down-back with the matching leg, then both wings up.
                val s1 = smooth(actT, 0.2f, 0.7f, 1.6f)
                val s2 = smooth(actT, 2.0f, 2.4f, 3.2f)
                wingT = max(0.55f * s1, s2); spreadT = max(s1, s2)
                legFT = 0.6f * s1; leanT = 0.12f - 0.15f * s2; beakT = if (s2 > 0.5f) 0.7f else 0f
                eT = if (s2 > 0.5f) 1f else 0f; crestT = 0.3f + 0.7f * s2
                if (actT > 3.8f) act = Act.NONE
            }
            Act.HANG -> {
                val s = smooth(actT, 0.1f, 0.9f, 4.5f)
                hangT = s
                if (s > 0.9f) { beakT = 0.4f; eT = 1f; swayT = 0.3f * sin(actT * 3f); crestT = 1f }
                if (actT > 1.2f && actT - dt <= 1.2f) say(listOf("Look at me!", "Upside-down!", "Wheee!").random(), 1.5f)
                if (actT > 5.3f) { act = Act.NONE; hang = 0f }
            }
            Act.FLY -> {
                // Take off, a big loop around the screen, land back on the branch.
                flying = true
                val dur = 3.6f
                val t = ((actT - 0.25f) / dur).coerceIn(0f, 1f)
                if (actT < 0.25f) { sqV -= 0f; legNT = 0f; wingT = 0.7f; spreadT = 1f }
                else {
                    val ang = t * 2f * PI.toFloat()
                    val ax = (halfW - 0.35f * size).coerceAtLeast(0.1f) * 0.85f
                    val ay = (halfH * 0.75f).coerceAtMost(1.2f)
                    flyXT = sin(ang) * ax * (if (homeX + x > 0f) -1f else 1f)
                    flyYT = (1f - cos(ang)) * 0.5f * ay
                    val dx = cos(ang) * (if (homeX + x > 0f) -1f else 1f)
                    if (abs(dx) > 0.1f) faceT = sign(dx)
                    leanT = 0.9f
                    wingT = 0.55f + 0.45f * sin(time * 26f); spreadT = 1f
                    legNT = 1f; legFT = 1f; crestT = 0.1f; beakT = 0.3f
                    swayT = 0.2f * sin(time * 26f)
                    if (t > 0.88f) { wingT = 1f; leanT = 0.0f; legNT = 0f; legFT = 0f }
                }
                if (actT > dur + 0.25f) {
                    act = Act.NONE; flyX = 0f; flyY = 0f; sqV -= 3f
                    branchSwayV -= 0.25f
                    spawnFx(7f, 0.1f, 0.0f, 0.35f)
                }
            }
            Act.COPY -> {
                // Plays back your rhythm: a head bob, a chirp and a note on every tap.
                turnT = 0.7f; crestT = 0.6f
                val t = actT - 0.5f
                if (playIdx < playN && t >= play[playIdx]) {
                    playIdx++
                    bobPulse = 1f; crest = 1f
                    spawnFx(3f, (0.1f + 0.05f * (playIdx % 3)) * face, 0.3f, 0.7f)
                }
                beakT = 0.6f * bobPulse
                hyT = -0.05f * bobPulse; nodT = 0.3f * bobPulse
                if (playIdx >= playN && t > play[max(0, playN - 1)] + 0.6f) {
                    act = Act.NONE
                    say(listOf("Again!", "Nailed it!", "Encore!", "Ta-da!").random(), 1.4f)
                    spawnFx(1f, 0f, 0.5f, 0.8f)
                }
            }
            Act.SEED -> {
                if (!seedHeld) {
                    // The seed drifts down into reach, Kiwi walks over, leans in and takes it.
                    val goalY = branchTop(seedX) + 0.62f * size
                    seedY += (goalY - seedY) * min(1f, dt * 4f)
                    seedX = seedX.coerceIn(homeX - reach - 0.25f * size, homeX + reach + 0.25f * size)
                    faceT = if (seedX > birdX) 1f else -1f
                    targetX = (seedX - homeX - faceT * 0.3f * size).coerceIn(-reach, reach)
                    crestT = 0.9f; turnT = 0f; lookT = -0.2f
                    val gap = abs(seedX - (birdX + face * 0.3f * size))
                    if (gap < 0.06f * size + 0.02f && abs(vx) < 0.15f) { leanT = 0.4f; nodT = 0.4f; beakT = 0.6f }
                    if (gap < 0.05f * size + 0.02f && actT > 0.6f && abs(face - faceT) < 0.1f) { seedHeld = true; actT = 0f }
                    if (actT > 8f) { act = Act.NONE; seedVis = 0f }
                } else {
                    // Crunch crunch crunch.
                    seedX = birdX + face * 0.24f * size; seedY = birdY + 0.12f * size
                    turnT = 0.2f; eT = 1f; crestT = 0.8f
                    beakT = 0.15f + 0.25f * (0.5f + 0.5f * sin(actT * 18f))
                    if (actT > 0.5f && actT - dt <= 0.5f) { seedCracked = 1f; spawnFx(4f, 0.22f * face, 0.12f, 0.9f) }
                    if (actT > 1.0f && actT - dt <= 1.0f) spawnFx(4f, 0.22f * face, 0.12f, 0.9f)
                    if (actT > 1.6f) seedVis = max(0f, seedVis - dt * 4f)
                    if (actT > 1.8f && actT - dt <= 1.8f) { spawnFx(1f, 0f, 0.5f, 0.8f); say(listOf("Yum!", "Crunchy!", "More please!").random(), 1.4f) }
                    if (actT > 2.6f) { act = Act.NONE; seedVis = 0f; seedHeld = false }
                }
            }
            Act.NIBBLE -> {
                // Charging: nibbles the glowing millet spray hanging next to it.
                faceT = 1f; targetX = 0f
                val ph = actT % 3f
                if (ph < 1.6f) { leanT = 0.35f; nodT = 0.55f; hxT = 0.04f; hyT = -0.05f; beakT = 0.2f + 0.3f * (0.5f + 0.5f * sin(actT * 20f)); eT = 1f }
                if (ph > 1.5f && ph - dt <= 1.5f) spawnFx(4f, 0.35f, -0.05f, 0.9f)
                if (actT > 1.0f && actT - dt <= 1.0f && Random.nextFloat() < 0.5f) say(listOf("Mmm, electricity", "Snack time!", "Crunchy volts").random(), 1.6f)
                if (actT > 6f || !charging) act = Act.NONE
            }
            Act.DANCE, Act.NONE -> {}
        }

        // Random chatter while music plays or just now and then.
        nextChat -= dt
        if (nextChat <= 0f && act == Act.NONE && !isAsleep() && bubbleT > bubbleDur + 2f && sinceTouch > 4f) {
            nextChat = 14f + Random.nextFloat() * 14f
            if (dancing || Random.nextFloat() < 0.4f) { startAct(Act.TALK); say(pickPhrase(input), 0f) }
        }

        // ---- Integrate ----
        // Sidestepping like a real parrot on a perch: one foot lifts with curled toes, reaches
        // sideways in the direction of travel and grips again while the other slides under the
        // body; then they swap. The body bobs and sways a little on every step.
        val dxT = targetX - x
        vx += (dxT * 10f - vx * 6f) * dt
        x += vx * dt
        val speed = abs(vx) / max(size, 0.05f)
        if (speed > 0.06f && !flying) {
            stepPhase += dt * (5f + min(speed, 1.5f) * 7f)
            if (stepPhase > 1f) stepPhase -= 1f
            val dirChar = (if (vx > 0f) 1f else -1f) * (if (face < 0f) -1f else 1f)
            val stride = 0.045f
            // The leading foot (on the side we move to) goes first.
            val leadNear = dirChar > 0f
            for (fIdx in 0..1) {
                val ph = (stepPhase + (if ((fIdx == 0) == leadNear) 0f else 0.5f)) % 1f
                val raise: Float; val off: Float
                if (ph < 0.5f) { val k = ph / 0.5f; raise = sin(k * PI.toFloat()); off = (-stride + 2f * stride * easeF(k)) * dirChar }
                else { val k = (ph - 0.5f) / 0.5f; raise = 0f; off = (stride - 2f * stride * k) * dirChar }
                if (fIdx == 0) { legNT = max(legNT, raise * 0.75f); footNT = off } else { legFT = max(legFT, raise * 0.75f); footFT = off }
            }
            hyT += 0.01f * abs(sin(stepPhase * 2f * PI.toFloat()))
            leanT += 0.06f * sin(stepPhase * 2f * PI.toFloat())
            swayT += 0.12f * sin(stepPhase * 2f * PI.toFloat())
        } else { footNT = 0f; footFT = 0f }
        // Home-screen page scrolls pull the wings too.
        val parV = (input.parallax - lastPar) / max(dt, 1e-3f)
        lastPar = input.parallax
        if (!touchDown && abs(parV) > 0.6f) {
            val amt = (abs(parV) * 0.35f).coerceIn(0f, 1f)
            if (parV < 0f) pullLT = max(pullLT, amt) else pullRT = max(pullRT, amt)
        } else if (!touchDown) { pullLT *= max(0f, 1f - dt * 6f); pullRT *= max(0f, 1f - dt * 6f) }
        pullL += (pullLT - pullL) * min(1f, dt * 14f)
        pullR += (pullRT - pullR) * min(1f, dt * 14f)
        if (pullL + pullR > 0.1f) {
            leanT += 0.1f * (pullR - pullL); crestT = max(crestT, 0.8f); lookT = 0.2f
            hxT += 0.03f * (pullR - pullL)
        }
        if (flutterT > 0f) { flutterT -= dt; wingT = max(wingT, 0.35f * abs(sin(time * 34f)) * (flutterT / 0.6f)); spreadT = max(spreadT, 0.4f) }
        footN += (footNT - footN) * min(1f, dt * 18f)
        footF += (footFT - footF) * min(1f, dt * 18f)
        if (act == Act.NONE && !following && !dancing && sinceTouch > 4f) targetX *= max(0f, 1f - dt * 0.15f)

        val kf = min(1f, dt * (if (flying) 3.5f else 8f))
        flyX += (flyXT - flyX) * kf; flyY += (flyYT - flyY) * kf
        face += (faceT - face) * min(1f, dt * 9f)
        lean += (leanT - lean) * min(1f, dt * (if (dancing) 14f else 7f))
        headX += (hxT - headX) * min(1f, dt * 12f)
        headY += (hyT - headY) * min(1f, dt * (if (dancing) 18f else 12f))
        nod += (nodT - nod) * min(1f, dt * (if (dancing) 18f else 10f))
        headFlip += (flipT - headFlip) * min(1f, dt * 8f)
        turn += (turnT - turn) * min(1f, dt * 5f)
        hang += (hangT - hang) * min(1f, dt * 4f)
        fluff += (fluffT - fluff) * min(1f, dt * 3f)
        crest += (crestT - crest) * min(1f, dt * (if (crestT > crest) 12f else 3f))
        wingOpen += (wingT - wingOpen) * min(1f, dt * (if (flying) 30f else 9f))
        wingSpread += (spreadT - wingSpread) * min(1f, dt * 8f)
        beak += (beakT - beak) * min(1f, dt * 22f)
        eyeLook += (lookT - eyeLook) * min(1f, dt * 6f)
        legNear += (legNT - legNear) * min(1f, dt * 12f)
        legFar += (legFT - legFar) * min(1f, dt * 12f)
        tailSway += (swayT - tailSway) * min(1f, dt * 6f)
        millet += (milletT - millet) * min(1f, dt * 3f)
        bobPulse = max(0f, bobPulse - dt * 5f)
        if (seedVis > 0f && act != Act.SEED) seedVis = max(0f, seedVis - dt * 3f)

        nextBlink -= dt
        if (nextBlink <= 0f) { blink = 0f; nextBlink = 2.5f + Random.nextFloat() * 4f }
        blink = min(1f, blink + dt * 10f)
        eyeOpen += (min(openT, blink) - eyeOpen) * min(1f, dt * 20f)
        eyeMode = eT

        // Springs, sub-stepped so a long frame can never blow them up.
        val bassKick = if (musicOn) a.beat * 0.004f else 0f
        val n = (dt / 0.012f).toInt() + 1; val hs = dt / n
        branchSwayV -= bassKick
        for (i in 0 until n) {
            sqV += ((1f - sq) * 180f - sqV * 10f) * hs; sq = (sq + sqV * hs).coerceIn(0.82f, 1.15f)
            // The branch bounces a little with the bird and the bass.
            branchSwayV += (-branchSway * 120f - branchSwayV * 6f) * hs
            branchSway = (branchSway + branchSwayV * hs).coerceIn(-0.05f, 0.05f)
        }

        for (i in 0 until 6) {
            if (fx[i * 4 + 3] == 0f) continue
            fx[i * 4 + 2] += fxSpeed[i] * dt
            fx[i * 4] += fxVel[i * 2] * dt; fx[i * 4 + 1] += fxVel[i * 2 + 1] * dt
            if (fx[i * 4 + 2] >= 1f) fx[i * 4 + 3] = 0f
        }
    }

    private fun ambientFor(input: SceneInput, k: Float) {
        val hr = input.hour
        fun ss(a: Float, b: Float, v: Float): Float { val t = ((v - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }
        var day = ss(7f, 9f, hr) * (1f - ss(16.5f, 18.5f, hr))
        var dawn = ss(4.5f, 6.5f, hr) * (1f - ss(7.5f, 9f, hr))
        var dusk = ss(16.5f, 18f, hr) * (1f - ss(19f, 20.5f, hr))
        var nt = (1f - day - dawn - dusk).coerceIn(0f, 1f)
        val s = day + dawn + dusk + nt; day /= s; dawn /= s; dusk /= s; nt /= s
        // Birds stay readable at night: a soft moonlit tint, never pitch dark.
        val r = 0.55f * nt + 0.95f * dawn + 1f * day + 0.98f * dusk
        val g = 0.6f * nt + 0.85f * dawn + 1f * day + 0.8f * dusk
        val b = 0.85f * nt + 0.82f * dawn + 1f * day + 0.72f * dusk
        val kk = min(1f, k)
        amb[0] += (r - amb[0]) * kk; amb[1] += (g - amb[1]) * kk; amb[2] += (b - amb[2]) * kk
    }

    private fun smooth(t: Float, a: Float, b: Float, c: Float): Float = when {
        t < a -> 0f
        t < b -> ((t - a) / (b - a)).let { it * it * (3 - 2 * it) }
        t < c -> 1f
        t < c + 0.4f -> 1f - ((t - c) / 0.4f).let { it * it * (3 - 2 * it) }
        else -> 0f
    }

    override fun draw(input: SceneInput) {
        if (program == 0) return
        if (bubbleDirty) uploadText()
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)

        val bx = birdX; val by = birdY
        val bTop = branchBase + 0.3f
        val minY = min(branchBase - 0.32f, by - (if (hang > 0.05f) 1.55f else 1.45f) * size)
        val maxY = max(bTop + 0.12f, by + 0.85f * size)
        GLES20.glUniform4f(u("uQuad"), -halfW, minY, halfW, maxY)
        GLES20.glUniform2f(u("uRes"), w.toFloat(), h.toFloat())
        GLES20.glUniform1f(u("uTime"), time % 600f)
        GLES20.glUniform2f(u("uPos"), bx, by)
        GLES20.glUniform1f(u("uSize"), size)
        GLES20.glUniform1f(u("uPx"), 1f / (size * min(w, h)))
        GLES20.glUniform1f(u("uFace"), face)
        GLES20.glUniform1f(u("uLean"), lean)
        GLES20.glUniform4f(u("uHead"), headX, headY, nod, if (headFlip >= 0f) max(headFlip, 0.15f) else min(headFlip, -0.15f))
        GLES20.glUniform1f(u("uTurn"), turn.coerceIn(0f, 1f))
        GLES20.glUniform1f(u("uHang"), hang)
        GLES20.glUniform1f(u("uFluff"), fluff)
        GLES20.glUniform1f(u("uCrest"), crest.coerceIn(0f, 1f))
        GLES20.glUniform2f(u("uWing"), wingOpen.coerceIn(0f, 1.1f), wingSpread)
        GLES20.glUniform1f(u("uBeak"), beak.coerceIn(0f, 1f))
        GLES20.glUniform3f(u("uEyeS"), eyeOpen, eyeMode, eyeLook)
        GLES20.glUniform2f(u("uLegs"), legNear, legFar)
        GLES20.glUniform2f(u("uFeet"), footN, footF)
        // Screen left/right to the bird's own left/right (it can face either way).
        if (face >= 0f) GLES20.glUniform2f(u("uWingPull"), pullL, pullR) else GLES20.glUniform2f(u("uWingPull"), pullR, pullL)
        GLES20.glUniform1f(u("uTailSway"), tailSway)
        GLES20.glUniform1f(u("uSquash"), sq)
        GLES20.glUniform1f(u("uSpecies"), species.toFloat())
        GLES20.glUniform3f(u("uBody"), cBody[0], cBody[1], cBody[2])
        GLES20.glUniform3f(u("uBelly"), cBelly[0], cBelly[1], cBelly[2])
        GLES20.glUniform3f(u("uHeadC"), cHead[0], cHead[1], cHead[2])
        GLES20.glUniform3f(u("uFaceC"), cFace[0], cFace[1], cFace[2])
        GLES20.glUniform3f(u("uWingC"), cWing[0], cWing[1], cWing[2])
        GLES20.glUniform3f(u("uEdgeC"), cEdge[0], cEdge[1], cEdge[2])
        GLES20.glUniform3f(u("uTailC"), cTail[0], cTail[1], cTail[2])
        GLES20.glUniform3f(u("uBeakC"), cBeak[0], cBeak[1], cBeak[2])
        GLES20.glUniform4f(u("uMark"), cMark[0], cMark[1], cMark[2], 1f)
        GLES20.glUniform3f(u("uAmb"), amb[0], amb[1], amb[2])
        GLES20.glUniform3f(u("uRim"), rim[0], rim[1], rim[2])
        GLES20.glUniform3f(u("uGlow"), glow[0], glow[1], glow[2])
        GLES20.glUniform4f(u("uBranch"), branchBase, branchR, branchSway, halfW)
        GLES20.glUniform4f(u("uSeed"), (seedX - bx) / size, (seedY - by) / size, seedVis, seedCracked)
        GLES20.glUniform4f(u("uMillet"), millet, input.battery, 0.06f * sin(time * 1.5f) + branchSway * 2f, 0f)
        fxBuf.position(0); fxBuf.put(fx); fxBuf.position(0)
        GLES20.glUniform4fv(u("uFx"), 6, fxBuf)
        GLES20.glUniform1f(u("uDim"), if (input.lowBattery) 0.3f else 0f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        drawBubble()
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun drawBubble() {
        if (bubbleProg == 0 || bubbleText.isEmpty() || bubbleT > bubbleDur + 0.35f) return
        val t = bubbleT
        // Pop in with a little overshoot, shrink away at the end.
        val pop = if (t < 0.3f) { val k = t / 0.3f; 1f + 1.6f * (k - 1f) * (k - 1f) * (k - 1f) + 0.6f * (k - 1f) * (k - 1f) } else if (t > bubbleDur) max(0f, 1f - (t - bubbleDur) / 0.35f) else 1f
        if (pop <= 0.01f) return
        val bh = 0.085f * min(1f, size / 0.5f + 0.25f)
        val bw = bh * bubbleW * 0.5f + bh * 0.25f
        // Above the head, toward the side the beak points.
        val hx = birdX + face * (0.12f + headX) * size
        val hy = birdY + (0.3f + headY) * size + (if (hang > 0.5f) -1.2f * size else 0f)
        var cx = hx + face * bw * 0.45f
        cx = cx.coerceIn(-halfW + bw + 0.03f, halfW - bw - 0.03f)
        val cy = min(hy + bh * 1.4f, halfH - bh - 0.05f)
        val tipX = birdX + face * (0.15f + headX) * size; val tipY = hy - 0.02f
        GLES20.glUseProgram(bubbleProg)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textTex)
        GLES20.glUniform1i(b("uTex"), 0)
        GLES20.glUniform4f(b("uQuad"), min(cx - bw, tipX) - 0.04f, min(cy - bh, tipY) - 0.04f, max(cx + bw, tipX) + 0.04f, cy + bh + 0.04f)
        GLES20.glUniform2f(b("uRes"), w.toFloat(), h.toFloat())
        GLES20.glUniform4f(b("uBox"), cx, cy, bw, bh * 0.5f)
        GLES20.glUniform2f(b("uTip"), tipX, tipY)
        GLES20.glUniform1f(b("uPop"), pop)
        GLES20.glUniform1f(b("uPx"), 1f / min(w, h))
        GLES20.glUniform1f(b("uTexW"), bubbleW * th / tw)
        GLES20.glUniform3f(b("uRim"), rim[0], rim[1], rim[2])
        GLES20.glUniform1f(b("uAlpha"), 1f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        if (program != 0) GLES20.glDeleteProgram(program)
        if (bubbleProg != 0) GLES20.glDeleteProgram(bubbleProg)
        if (quadVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
        if (textTex != 0) GLES20.glDeleteTextures(1, intArrayOf(textTex), 0)
        program = 0; bubbleProg = 0; quadVbo = 0; textTex = 0
        bmp?.recycle(); bmp = null
    }

    private fun u(n: String) = uni[n] ?: -1
    private fun b(n: String) = bu[n] ?: -1

    private fun ease(dst: FloatArray, argb: Int, k: Float) {
        val kk = min(1f, k)
        dst[0] += (Color.red(argb) / 255f - dst[0]) * kk
        dst[1] += (Color.green(argb) / 255f - dst[1]) * kk
        dst[2] += (Color.blue(argb) / 255f - dst[2]) * kk
    }

}
