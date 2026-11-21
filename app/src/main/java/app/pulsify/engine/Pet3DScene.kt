package app.pulsify.engine

import android.content.Context
import app.pulsify.core.AudioFrame
import app.pulsify.core.Mood
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** What one Meshy pet can do: its model and which baked clips play for which moment. */
class PetSpecies(
    val model: String,
    val walk: String,
    val walkSpeed: Float,                  // body heights per second
    val life: List<Pair<String, Float>>,   // background clips with weights
    val greet: String,
    val charge: String,
    val dance: String,
    val tapThrice: String,
    val tricks: Map<String, String>,       // Studio trick id -> clip
    val dances: List<String> = emptyList(), // music: a random one of these (else [dance])
    val walks: List<String> = emptyList(),  // walk variants (else [walk]); "CarryBread" ends in a peck
    val run: String? = null,               // long trips
    val runSpeed: Float = 1f,
    val ownFace: Boolean = false,          // clips carry their own blinks, glances and moods (PET2)
    val angry: String? = null,             // too many pokes in a row
    val alias: Map<String, String> = emptyMap(),   // scene clip name -> this pet's clip (birds fly instead of jump)
    val big: Float = 1f,                   // on-screen size factor
    val angryPokes: Int = 7,               // pokes within 8 s that trigger [angry]
    val glide: Boolean = false,            // birds flutter down slowly instead of dropping
)

val PET_KITTEN = PetSpecies(
    "kitten", "Hop", 0.55f,
    listOf("LookAround" to 3f, "HeadTilt" to 2f, "EarTwitch" to 2f, "TailWag" to 2f, "PawLift" to 2f, "Stretch" to 1.5f, "Happy" to 1f,
        "Spin" to 0.7f, "DrinkMilk" to 1.5f, "EatFish" to 1.5f, "PlayYarn" to 1.8f, "ChaseButterfly" to 1.5f, "Shake" to 0.8f,
        "Sneeze" to 0.6f, "Sniff" to 1f, "Knead" to 1.2f),
    "HeadTilt", "Happy", "Happy", "Spin",
    mapOf("pounce" to "Jump", "zoomies" to "Spin", "purr" to "Pet", "groom" to "PawLift", "loaf" to "Sleep", "box" to "Sleep",
        "trill" to "HeadTilt", "stretch" to "Stretch", "knock" to "Poke", "cucumber" to "Poke"),
)
/** Blizzard the husky (Kalboob's 3D look): Meshy quadruped rig + Meshy motions + hand-made acting, layered eyes. */
val PET_PUPPY = PetSpecies(
    "husky", "Walk", 0.5f,
    listOf("LookAround" to 2.5f, "HeadTilt" to 2f, "Wag" to 2.5f, "Bark" to 1.6f, "Howl" to 1f, "PlayBow" to 1.5f, "Shake" to 1.2f,
        "Dig" to 1.2f, "Fetch" to 1.5f, "Eat" to 1.3f, "ChewBone" to 1.3f, "ChaseButterfly" to 1.3f, "Spin" to 0.8f),
    "Wag", "Eat", "Dance", "Howl",
    mapOf("fetch" to "Fetch", "paw" to "PlayBow", "belly" to "Pet", "dig" to "Dig", "howl" to "Howl", "butterfly" to "ChaseButterfly",
        "zoomies" to "Spin", "yawn" to "Shake"),
    dances = listOf("Dance", "Dance2"), run = "Run", runSpeed = 1.2f, ownFace = true, angry = "Bark",
)
/** Gromble's 3D look 'Reaper': a tiny skeleton reaper who tries so hard to be evil. */
val PET_REAPER = PetSpecies(
    "reaper", "Walk", 0.42f,
    listOf("LookAround" to 2f, "EvilLaugh" to 2.2f, "SpellCast" to 1.4f, "Boo" to 1.4f, "CapeSwish" to 1.3f, "HeadTilt" to 1.6f,
        "TryToFly" to 1.2f, "ChaseButterfly" to 1.2f, "Shake" to 0.8f, "Spin" to 0.8f, "Scared" to 0.8f),
    "EvilLaugh", "Plugged", "Dance", "Tumble",
    mapOf("laugh" to "EvilLaugh", "plan" to "HeadTilt", "fire" to "SpellCast", "fly" to "TryToFly", "boo" to "Boo",
        "cape" to "CapeSwish", "attack" to "Attack", "pet" to "Pet"),
    dances = listOf("Dance"), run = "Run", runSpeed = 1.3f, ownFace = true, angry = "AngryKick",
)
/** Snowy v2: Meshy auto-rig + Meshy motions + hand-made acting (face moods, beak, props). */
val PET_DUCK = PetSpecies(
    "snowy", "Walk", 0.45f,
    listOf("Quack" to 2.5f, "HeadTilt" to 2f, "LookAround" to 2f, "Notice" to 1.5f, "TryToFly" to 1.4f, "Preen" to 1.4f,
        "Peck" to 1.4f, "Splash" to 1.3f, "RubberDuck" to 1.3f, "ChaseButterfly" to 1.2f, "Shake" to 1f, "Spin" to 0.7f),
    "Quack", "Plugged", "DancePop", "Tumble",
    mapOf("quack" to "Quack", "flap" to "TryToFly", "fly" to "TryToFly", "preen" to "Preen", "crumbs" to "Peck",
        "splash" to "Splash", "toy" to "RubberDuck", "butterfly" to "ChaseButterfly", "sleep" to "Sleep", "dance" to "DanceHipHop",
        "spin" to "Spin", "shake" to "Shake", "kick" to "AngryKick", "tumble" to "Tumble"),
    dances = listOf("DancePop", "DanceHipHop", "DanceHipHop2", "DanceBubble", "DanceCardio", "DanceJoy", "DanceNight"),
    walks = listOf("Walk", "Walk", "Walk", "CarryBread"),
    run = "Run", runSpeed = 1.15f, ownFace = true, angry = "AngryKick",
)
val PET_BOT = PetSpecies(
    "bot", "Hop", 0.5f,
    listOf("LookAround" to 2.5f, "HeadTilt" to 1.5f, "Wave" to 2f, "Dance" to 1.2f, "Juggle" to 1.6f, "OilDrink" to 1.5f,
        "Scan" to 1.8f, "PowerUp" to 0.8f, "Glitch" to 1f, "GiveHeart" to 1.4f, "ChaseButterfly" to 1.2f, "Spin" to 0.6f, "Happy" to 1f),
    "Wave", "PowerUp", "Dance", "Glitch",
    mapOf("wave" to "Wave", "dance" to "Dance", "glitch" to "Glitch", "scan" to "Scan", "radar" to "Scan", "jump" to "Jump",
        "faces" to "GiveHeart", "polish" to "OilDrink", "tune" to "Juggle", "clock" to "LookAround"),
)

/** The 3D birds (Kiwi's 3D look): one shared hand-made library of 50 motions, wings that really fold and fly. */
private val BIRD_LIFE = listOf("LookAround" to 2.5f, "HeadTilt" to 2f, "HeadBob" to 1.6f, "Preen" to 1.6f, "PreenTail" to 1f,
    "Scratch" to 1.2f, "Fluff" to 1.4f, "Shake" to 0.8f, "Stretch" to 1.2f, "WingStretch" to 1.2f, "Yawn" to 0.8f, "Peck" to 1.4f,
    "Eat" to 1.3f, "Chirp" to 1.6f, "Talk" to 1.5f, "Notice" to 1f, "Hop" to 1f, "Mirror" to 0.8f, "Swing" to 0.8f,
    "Upside" to 0.6f, "ChaseButterfly" to 1f)
private val BIRD_TRICKS = mapOf("kiss" to "Mirror", "talk" to "Talk", "whistle" to "Chirp", "dance" to "DanceHeadbang",
    "fly" to "Jump", "hang" to "Upside", "seed" to "Eat", "squawk" to "Notice", "preen" to "Preen", "oneleg" to "Scratch",
    "stretch" to "WingStretch")
private val BIRD_ALIAS = mapOf("JumpStart" to "TakeOff", "JumpAir" to "Fly", "JumpLand" to "Land", "Spin" to "DanceSpin")
private fun bird(model: String) = PetSpecies(
    model, "Walk", 0.45f, BIRD_LIFE, "Chirp", "Plugged", "DanceBob", "Fluff", BIRD_TRICKS,
    dances = listOf("DanceBob", "DanceWings", "DanceSide", "DanceSpin", "DanceHeadbang"), run = "Run", runSpeed = 1.2f,
    ownFace = true, angry = "Angry", alias = BIRD_ALIAS, big = 0.92f, glide = true,
)
/** Leo the lion cub: a tiny lion who is SURE he is the king. Roars come out as squeaks; he loves belly rubs. */
val PET_LION = PetSpecies(
    "lion", "Walk", 0.5f,
    listOf("LookAround" to 2.5f, "HeadTilt" to 2f, "Yawn" to 1f, "Sneeze" to 0.6f, "Stretch" to 1.4f, "Groom" to 1.6f,
        "RollOver" to 1.2f, "ChaseTail" to 0.9f, "Pounce" to 1.4f, "TinyRoar" to 1.6f, "Beg" to 0.8f, "PlayBall" to 1.6f,
        "ChaseButterfly" to 1.2f, "Sit" to 1f),
    "TinyRoar", "Plugged", "DanceStomp", "BigRoar",
    mapOf("roar" to "TinyRoar", "bigroar" to "BigRoar", "pounce" to "Pounce", "ball" to "PlayBall", "belly" to "RollOver",
        "groom" to "Groom", "beg" to "Beg", "stalk" to "Pounce", "butterfly" to "ChaseButterfly", "sleep" to "Sleep"),
    dances = listOf("DanceBob", "DanceStomp", "DanceRoar", "DanceSpin"), run = "Run", runSpeed = 1.3f, ownFace = true, angry = "Angry",
    alias = mapOf("Spin" to "DanceSpin", "Shake" to "Sneeze"),
)
/** Luna the fluffy kitten (Kitty's 3D look): Meshy cat motions + Leo's hand-made library adapted for a cat. */
val PET_LUNA = PetSpecies(
    "luna", "Walk", 0.5f,
    listOf("LookAround" to 2.5f, "HeadTilt" to 2f, "Yawn" to 1f, "Sneeze" to 0.6f, "Stretch" to 1.4f, "Lounge" to 1f, "Knead" to 1.2f,
        "Groom" to 1.6f, "RollOver" to 1f, "ChaseTail" to 0.8f, "Pounce" to 1.4f, "Meow" to 1.6f, "Beg" to 0.7f, "PlayBall" to 1.6f,
        "ChaseButterfly" to 1.2f, "Sit" to 1.2f),
    "Meow", "Plugged", "DanceBob", "Hiss",
    mapOf("pounce" to "Pounce", "zoomies" to "ChaseTail", "purr" to "Pet", "groom" to "Groom", "loaf" to "Sleep", "box" to "Sleep",
        "trill" to "Meow", "stretch" to "Stretch", "knock" to "PlayBall", "cucumber" to "Hiss"),
    dances = listOf("DanceBob", "DanceStomp", "DanceRoar", "DanceSpin"), run = "Run", runSpeed = 1.3f, ownFace = true, angry = "Hiss",
    alias = mapOf("Spin" to "DanceSpin", "Shake" to "Sneeze"),
)
/** Nutmeg (Nutty's 3D look): a twitchy red squirrel. Keep tapping him and he gets knocked out cold. */
val PET_NUTMEG = PetSpecies(
    "nutmeg", "Walk", 0.6f,
    listOf("LookAround" to 2.5f, "Lookout" to 1.6f, "Nibble" to 2f, "Stash" to 1.4f, "TailFlick" to 2f, "Wash" to 1.4f,
        "Scratch" to 1.2f, "Sneeze" to 0.6f, "Zoomies" to 0.8f, "Drum" to 1f, "HeadTilt" to 1.6f, "ChaseButterfly" to 1f),
    "TailFlick", "Plugged", "DanceTail", "Spin",
    mapOf("nibble" to "Nibble", "stash" to "Stash", "lookout" to "Lookout", "flick" to "TailFlick", "zoom" to "Zoomies",
        "catch" to "Catch", "spin" to "Spin", "scratch" to "Scratch", "feed" to "Catch", "drum" to "Drum"),
    dances = listOf("DanceTail", "DanceDrum", "DanceHop"), run = "Run", runSpeed = 1.5f, ownFace = true, angry = "Knockdown",
    angryPokes = 5, big = 0.9f,
)
val PET_BUDGIE = bird("budgie")
val PET_COCKATIEL = bird("cockatiel")
val PET_RINGNECK = bird("ringneck")

/**
 * A Meshy pet with baked clips: background life (with props), touch reactions (pet, poke, tickle,
 * carry), hops across the floor, and jumps on top of the widgets on the home screen.
 */
class Pet3DScene(private val context: Context) : Creature {
    override var listener: SceneListener? = null
    @Volatile var species: PetSpecies = PET_LUNA
        set(v) { if (v !== field) { field = v; laidOut = false } }
    private var laidOut = false
    var viewLag = 0f

    private val pet = Pet3D(context)
    private var w = 1; private var h = 1
    private var time = 0f
    override var bottomInset = 0f
        set(v) { field = v.coerceIn(0f, 0.8f); layout() }
    private var ground = Float.NaN
    private var size = 0.42f
    private var floorY = -0.5f
    private val halfW get() = 0.5f * w / min(w, h)
    private val halfH get() = 0.5f * h / min(w, h)

    private enum class Mode { FLOOR, WALK, LAUNCH, AIR, PERCHED, CARRY, SLEEP }
    private var mode = Mode.FLOOR
    private var x = 0f; private var y = -0.5f; private var vx = 0f; private var vy = 0f
    private var grounded = true
    private var onLedge = -1; private var ledgeDx = 0f
    private var targetX = 0f
    private var facing = 0f; private var yawNow = 0f
    private var launchTo = floatArrayOf(0f, 0f, 0f)       // x, y, apex
    private var landOnLedge = -1
    private var afterWalk: String? = null

    // clip player
    private var cur: PetClip? = null; private var curT = 0f
    private var fadeFrom: PetClip? = null; private var fadeFromT = 0f; private var fade = 1f
    private val matA = FloatArray(Pet3D.MAX_BONES * 12); private val matB = FloatArray(Pet3D.MAX_BONES * 12)
    private val ctlA = FloatArray(Pet3D.MAX_CTL); private val ctlB = FloatArray(Pet3D.MAX_CTL)
    private var walkClip = ""; private var walkSpd = 0.4f
    private val pokeTimes = ArrayList<Float>()
    private var nextLife = 4f
    private var lastLife = ""
    private var sleepLeft = 0f

    // eyes
    private var blink = 0f; private var nextBlink = 2f
    private var lookX = 0f; private var lookY = 0f; private var lookTX = 0f; private var lookTY = 0f

    // touch
    private var touching = false; private var downAt = 0f; private var downOnPet = false
    // 5.9 soft body: lateral bend and squash springs driven by body acceleration
    private var bendX = 0f; private var bendV = 0f; private var sq = 0f; private var sqV = 0f
    private var bodyVx = 0f; private var bodyVy = 0f; private var lastX = Float.NaN; private var lastY = Float.NaN
    private var swing = 0f; private var swingV = 0f; private var carryVx = 0f; private var fallTop = 0f
    private var tX = 0f; private var tY = 0f; private var t0X = 0f; private var t0Y = 0f
    private var path = 0f; private var lastMX = 0f; private var lastMY = 0f
    private var held = false
    private var taps = 0; private var lastTap = -9f
    private var sinceTouch = 99f

    private val ledges = ArrayList<Ledge>()
    private var wasCharging = false; private var chargeKnown = false
    private var musicOn = false; private var lastBeat = 0f
    private var mood = Mood.DAY

    private class P(var kind: Float, var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, var max: Float,
                    var s: Float, var r: Float, var g: Float, var b: Float, var rot: Float)
    private val parts = ArrayList<P>()

    override val busy: Boolean
        get() = touching || sinceTouch < 2f || mode != Mode.FLOOR || (!isIdle() && curName != "Sleep") || parts.isNotEmpty()

    // ------------------------------------------------------------ lifecycle
    override fun onSurfaceCreated() { pet.create(); pet.load(species.model); cur = null }
    override fun onSurfaceChanged(width: Int, height: Int) { w = max(1, width); h = max(1, height); layout() }
    override fun setGround(y: Float) {
        if (y == ground || (y.isNaN() && ground.isNaN())) return
        ground = y; layout()
    }
    private fun layout() {
        laidOut = true
        size = min(0.4f * halfH, halfW * 0.8f) * species.big      // about 30 % of the screen
        floorY = if (ground.isNaN()) -halfH + 0.3f else ground
        if (h.toFloat() / w < 1.5f) { size = min(0.32f, halfW * 0.55f); floorY = -halfH + 0.12f }
        if (bottomInset > 0f) {
            val floor = -halfH + bottomInset * 2f * halfH + 0.05f
            size = min(size, (halfH - 0.1f - floor) / 1.3f)
            floorY = max(floorY, floor)
        }
        if (grounded && onLedge < 0) y = floorY
        x = x.coerceIn(-halfW + size * 0.45f, halfW - size * 0.45f)
    }
    override fun release() { pet.release() }

    // ------------------------------------------------------------ clips
    private fun play(name: String, restart: Boolean = true): Boolean {
        val c = pet.clips[species.alias[name] ?: name] ?: return false
        if (!restart && cur === c) return true
        fadeFrom = cur; fadeFromT = curT; fade = if (cur == null) 1f else 0f
        cur = c; curT = 0f
        return true
    }
    private val curName get() = cur?.name ?: ""
    private var chargingNow = false; private var lowNow = false; private var fullDone = false
    /** The resting loop right now: content on the charger, droopy on a low battery, else the normal idle. */
    private val idleName: String get() = when {
        chargingNow && pet.clips.containsKey("ChargeRest") -> "ChargeRest"
        lowNow && !chargingNow && pet.clips.containsKey("Tired") -> "Tired"
        else -> "Idle"
    }
    private fun isIdle() = curName == "Idle" || curName == "ChargeRest" || curName == "Tired"
    private fun clipDone() = cur?.let { !it.loop && curT >= it.duration } ?: true

    private fun pickLife(input: SceneInput) {
        val opts = species.life.filter { it.first != lastLife && pet.clips.containsKey(it.first) }
        if (opts.isEmpty()) return
        var r = Random.nextFloat() * opts.sumOf { it.second.toDouble() }.toFloat()
        for ((n, wgt) in opts) { r -= wgt; if (r <= 0f) { lastLife = n; play(n); return } }
        lastLife = opts.last().first; play(lastLife)
    }

    // ------------------------------------------------------------ helpers
    private fun toUv(nx: Float, ny: Float): Pair<Float, Float> {
        val m = min(w, h).toFloat()
        return Pair((nx - 0.5f) * w / m, (0.5f - ny) * h / m)
    }
    private fun onPet(ux: Float, uy: Float): Boolean {
        val cx = (ux - x) / size; val cy = (uy - y) / size
        return cx * cx / (0.5f * 0.5f) + (cy - 0.5f) * (cy - 0.5f) / (0.62f * 0.62f) < 1f
    }
    private fun lookAt(ux: Float, uy: Float) {
        lookTX = ((ux - x) / size / 1.4f).coerceIn(-0.45f, 0.45f)
        lookTY = ((uy - (y + size * 0.7f)) / size / 1.4f).coerceIn(-0.35f, 0.35f)
    }
    private val reported = HashSet<String>()
    private fun discover(id: String) { if (reported.add(id)) listener?.onDiscovery(id) }
    private fun burst(kind: Float, n: Int, cx: Float, cy: Float, r: Float, g: Float, b: Float, s: Float = 0.035f) {
        for (i in 0 until n) parts += P(kind, cx + (Random.nextFloat() - 0.5f) * size * 0.3f, cy, (Random.nextFloat() - 0.5f) * 0.12f,
            0.12f + Random.nextFloat() * 0.12f, 0f, 1.2f + Random.nextFloat() * 0.5f, s * (0.7f + Random.nextFloat() * 0.6f), r, g, b,
            (Random.nextFloat() - 0.5f) * 0.6f)
        if (parts.size > 50) parts.subList(0, parts.size - 50).clear()
    }
    private val headY get() = y + size * 0.85f

    private fun busyActing() = mode == Mode.CARRY || mode == Mode.LAUNCH || mode == Mode.AIR

    // ------------------------------------------------------------ input
    override fun touchDown(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f; touching = true; held = false; downAt = time; path = 0f
        tX = ux; tY = uy; t0X = ux; t0Y = uy; lastMX = ux; lastMY = uy
        downOnPet = onPet(ux, uy)
        lookAt(ux, uy)
        if (mode == Mode.SLEEP && downOnPet) wake()
        return true
    }
    override fun touchMove(nx: Float, ny: Float): Boolean {
        if (!touching) return false
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        path += hypot(ux - lastMX, uy - lastMY); lastMX = ux; lastMY = uy
        tX = ux; tY = uy; lookAt(ux, uy)
        if (downOnPet && mode != Mode.CARRY && mode != Mode.SLEEP) {
            val far = hypot(ux - t0X, uy - t0Y)
            if (far > size * 0.35f) {                           // picked up
                mode = Mode.CARRY; grounded = false; onLedge = -1; play("Dangle"); discover("carry")
            } else if (path > size * 0.9f && curName != "Tickle") {   // rubbing back and forth
                play("Tickle"); burst(3f, 3, x, headY, 1f, 0.9f, 0.4f); discover("tickle"); listener?.onBondXp(1)
            }
        }
        return true
    }
    override fun touchUp(): Boolean {
        if (!touching) return false
        touching = false; sinceTouch = 0f
        val tap = path < size * 0.08f && time - downAt < 0.35f
        if (mode == Mode.CARRY) { mode = Mode.AIR; vx = carryVx * 0.4f; vy = 0f; landOnLedge = -2; fallTop = y; play("JumpAir") }
        else if (tap) { if (downOnPet) poke() else goTo(tX, tY) }
        return true
    }
    override fun tapAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f; lookAt(ux, uy)
        if (onPet(ux, uy)) poke() else goTo(ux, uy)
        return true
    }
    override fun longPressAt(nx: Float, ny: Float): Boolean {
        val (ux, uy) = toUv(nx, ny)
        sinceTouch = 0f
        if (onPet(ux, uy)) pat() else if (!busyActing()) { play("ChaseButterfly"); discover("butterfly") }
        return true
    }
    override fun greet() { if (!busyActing() && mode != Mode.SLEEP) play(species.greet) }
    override fun swiped(dir: Float, fast: Boolean) {
        if (busyActing() || mode == Mode.SLEEP || onLedge >= 0) return
        if (fast) { play(if (Random.nextBoolean()) "Shake" else "Spin"); vx = -dir * 0.5f * size }
        else { play("Poke"); vx = -dir * 0.3f * size }
    }
    override fun perform(trick: String): Boolean {
        sinceTouch = 0f
        val clip = species.tricks[trick] ?: pet.clips.keys.firstOrNull { it.equals(trick, true) } ?: return false
        if (clip == "Sleep") { goSleep(10f); return true }
        if (clip == "Jump") { hopInPlace(); return true }
        if (mode == Mode.SLEEP) wake()
        return play(clip)
    }

    private fun poke() {
        listener?.onBondXp(1)
        if (mode == Mode.SLEEP) { wake(); return }
        if (time - lastTap < 0.6f) taps++ else taps = 1
        lastTap = time
        pokeTimes += time; pokeTimes.removeAll { time - it > 8f }
        val mad = species.angry
        if (mad != null && pokeTimes.size >= species.angryPokes) {
            pokeTimes.clear(); taps = 0; play(mad); discover("grumpy")
            if (mad == "Knockdown") { discover("knocked out"); for (i in 0 until 4) burst(8f, 1, x, headY - size * 0.5f, 1f, 0.9f, 0.3f, 0.05f) }
            return
        }
        if (taps >= 3) { taps = 0; play(species.tapThrice); discover("triple tap"); return }
        play("Poke"); burst(9f, 3, x, headY, 1f, 0.95f, 0.6f, 0.025f)
    }
    private fun pat() {
        if (mode == Mode.SLEEP) wake()
        play("Pet"); held = true; discover("pet"); listener?.onBondXp(2)
        burst(2f, 3, x, headY + size * 0.1f, 1f, 0.45f, 0.62f)
    }
    private fun wake() { mode = Mode.FLOOR; play("WakeUp"); sleepLeft = 0f }
    private fun goSleep(secs: Float) {
        if (busyActing() || onLedge >= 0) return
        mode = Mode.SLEEP; sleepLeft = secs; play("FallAsleep")
    }

    /** Tap somewhere: hop over there (or up onto the widget that was tapped). */
    private fun goTo(ux: Float, uy: Float) {
        if (busyActing()) return
        if (mode == Mode.SLEEP) { wake(); return }
        val L = ledges.firstOrNull { visible(it) && ux > it.l && ux < it.r && uy < it.t + size * 0.6f && uy > it.b - 0.02f && reachable(it) }
        if (L != null) { jumpTo(ux.coerceIn(L.l + size * 0.3f, L.r - size * 0.3f), L.t, L.id); return }
        if (onLedge >= 0) { jumpTo(ux.coerceIn(-halfW + size * 0.45f, halfW - size * 0.45f), floorY, -1); return }
        targetX = ux.coerceIn(-halfW + size * 0.45f, halfW - size * 0.45f)
        if (abs(targetX - x) > size * 0.15f) startWalk(if (Random.nextBoolean()) "HeadTilt" else "LookAround")
        else play("HeadTilt")
    }

    private fun startWalk(after: String?) {
        mode = Mode.WALK
        val far = abs(targetX - x) > halfW * 0.9f
        val run = species.run
        val pick = if (far && run != null) run else species.walks.randomOrNull() ?: species.walk
        walkClip = pick
        walkSpd = if (pick == run) species.walkSpeed * species.runSpeed * 2f else species.walkSpeed
        afterWalk = if (pick == "CarryBread") "Peck" else after
        play(pick, false)
    }

    private fun hopInPlace() { if (!busyActing()) jumpTo(x, y, onLedge, apex = size * 0.5f) }

    private fun jumpTo(tx: Float, ty: Float, ledge: Int, apex: Float = size * 0.35f) {
        launchTo[0] = tx; launchTo[1] = ty; launchTo[2] = apex; landOnLedge = ledge
        mode = Mode.LAUNCH; play("JumpStart")
        if (abs(tx - x) > 0.02f) facing = sign(tx - x)
    }
    private fun launch() {
        val g = GRAV * size
        val tx = launchTo[0]; val ty = launchTo[1]
        val top = max(y, ty) + launchTo[2]
        val up = sqrt(2f * g * max(0.001f, top - y))
        val tDown = sqrt(2f * max(0.001f, top - ty) / g)
        vy = up; vx = (tx - x) / (up / g + tDown)
        grounded = false; onLedge = -1
        mode = Mode.AIR; play("JumpAir")
    }

    // ------------------------------------------------------------ ledges
    private fun refreshLedges(input: SceneInput) {
        val n = input.ledgeCount
        while (ledges.size < n) ledges += Ledge()
        while (ledges.size > n) ledges.removeAt(ledges.size - 1)
        val m = min(w, h).toFloat()
        val lagUv = viewLag * w / m
        for (i in 0 until n) {
            val o = i * 6
            val dx = input.ledges[o] - input.pagePos
            val L = ledges[i]
            L.l = (input.ledges[o + 1] + dx - 0.5f) * w / m - lagUv; L.r = (input.ledges[o + 3] + dx - 0.5f) * w / m - lagUv
            L.t = (0.5f - input.ledges[o + 2]) * h / m; L.b = (0.5f - input.ledges[o + 4]) * h / m
            L.id = input.ledges[o + 5].toInt()
        }
    }
    private fun ledgeById(id: Int) = ledges.firstOrNull { it.id == id }
    private fun visible(L: Ledge) = L.r > -halfW + 0.05f && L.l < halfW - 0.05f
    private fun reachable(L: Ledge) = L.t - floorY < size * 3.2f && L.t < halfH - size * 1.1f && L.r - L.l > size * 0.6f

    // ------------------------------------------------------------ update
    override fun update(dt: Float, input: SceneInput) {
        time += dt; sinceTouch += dt
        if (pet.loaded != species.model) { pet.load(species.model); cur = null; fadeFrom = null }
        if (!laidOut) layout()
        if (cur == null) play(idleName)
        mood = input.mood
        musicOn = input.audio.source != AudioFrame.Source.NONE && input.audio.level > 0.02f
        refreshLedges(input)
        charger(input)
        if (touching && downOnPet && !held && path < size * 0.08f && time - downAt > 0.5f && mode != Mode.CARRY) pat()
        behave(dt, input)
        physics(dt)
        softBody(dt)
        advance(dt)
        eyes(dt)
        particles(dt)
        pet.dim += (((if (input.lowBattery && !input.charging) 0.3f else 0f) + (if (mode == Mode.SLEEP) 0.12f else 0f)) - pet.dim) * min(1f, dt * 2f)
    }

    private fun charger(input: SceneInput) {
        val ch = input.charging
        chargingNow = ch; lowNow = input.lowBattery
        if (!chargeKnown) { chargeKnown = true; wasCharging = ch; fullDone = input.battery >= 0.995f; return }
        if (!ch && wasCharging) {                          // cable pulled
            if (input.battery < 0.995f && !busyActing() && mode != Mode.SLEEP) play("Unplugged")
            fullDone = false
        }
        if (ch && input.battery >= 0.995f && !fullDone) {   // just reached 100 %
            fullDone = true
            if (!busyActing()) { if (mode == Mode.SLEEP) wake(); play("FullCharge"); discover("full charge") }
            burst(2f, 5, x, headY + size * 0.2f, 1f, 0.85f, 0.3f)
        }
        if (ch && !wasCharging) {
            discover("charger")
            if (mode == Mode.SLEEP) wake()
            if (!busyActing()) play(species.charge)
            burst(6f, 4, x, headY + size * 0.2f, 1f, 0.85f, 0.25f, 0.04f)
        }
        wasCharging = ch
    }

    private fun behave(dt: Float, input: SceneInput) {
        when (mode) {
            Mode.FLOOR, Mode.PERCHED -> {
                if (mode == Mode.PERCHED) {
                    val L = ledgeById(onLedge)
                    if (L == null || !visible(L)) { fallOff(); return }
                }
                if (held && touching) { if (clipDone()) play("Pet"); return }
                if (clipDone()) { held = false; play(idleName, false) }
                if (!isIdle()) return
                if (curName != idleName) { play(idleName, false); return }
                if (musicOn && input.audio.beat > 0.8f && time - lastBeat > 2.5f) { lastBeat = time; play(species.dances.randomOrNull() ?: species.dance); return }
                nextLife -= dt
                if (nextLife > 0f) return
                nextLife = TRICK_GAP_MIN + Random.nextFloat() * TRICK_GAP_RANGE
                val night = mood == Mood.NIGHT || (input.lowBattery && !input.charging)
                val roll = Random.nextFloat()
                val vis = ledges.filter { visible(it) && reachable(it) && it.t > floorY + size * 0.4f }
                when {
                    mode == Mode.PERCHED && roll < 0.35f -> {
                        val next = ledges.filter { it.id != onLedge && visible(it) && reachable(it) && abs(it.l - x) < size * 3f }
                        if (next.isNotEmpty() && Random.nextBoolean()) { val N = next.random(); jumpTo(((N.l + N.r) / 2f), N.t, N.id); discover("widget hop") }
                        else jumpTo((x + (if (x > 0f) -1 else 1) * size * 0.5f).coerceIn(-halfW + size * 0.45f, halfW - size * 0.45f), floorY, -1)
                    }
                    night && roll < 0.3f && mode == Mode.FLOOR -> goSleep(10f + Random.nextFloat() * 8f)
                    mode == Mode.FLOOR && vis.isNotEmpty() && roll < 0.3f -> {
                        val L = vis.random()
                        jumpTo(x.coerceIn(L.l + size * 0.3f, L.r - size * 0.3f), L.t, L.id)
                    }
                    mode == Mode.FLOOR && roll < 0.48f -> {
                        targetX = (Random.nextFloat() - 0.5f) * 2f * (halfW - size * 0.5f)
                        if (abs(targetX - x) > size * 0.3f) startWalk(null) else pickLife(input)
                    }
                    else -> pickLife(input)
                }
            }
            Mode.WALK -> {
                val d = targetX - x
                facing = sign(d)
                if (abs(d) < 0.01f) {
                    x = targetX; mode = Mode.FLOOR; facing = 0f
                    val n = afterWalk; afterWalk = null
                    if (n != null) play(n) else play(idleName)
                } else {
                    x += sign(d) * min(abs(d), walkSpd * size * dt)
                    if (curName != walkClip) play(walkClip.ifEmpty { species.walk }, false)
                }
            }
            Mode.LAUNCH -> if (curT > 0.36f || clipDone()) launch()
            Mode.AIR -> {}
            Mode.CARRY -> {
                if (!touching) { mode = Mode.AIR; vx = 0f; vy = 0f; landOnLedge = -2; return }
                val tx = tX; val ty = tY - size * 1.05f
                val ox = x
                x += (tx - x) * min(1f, dt * 14f); y += (ty - y) * min(1f, dt * 14f)
                facing = 0f
                // hanging from the finger: a pendulum driven by how hard the finger accelerates
                val nvx = (x - ox) / max(dt, 0.008f); val ax = (nvx - carryVx) / max(dt, 0.008f); carryVx = nvx
                swingV += (-28f * sin(swing) - 3.2f * swingV - ax / size * 0.09f) * dt
                swing = (swing + swingV * dt).coerceIn(-1.1f, 1.1f)
                fallTop = y
            }
            Mode.SLEEP -> {
                if (clipDone() && curName == "FallAsleep") play("Sleep")
                sleepLeft -= dt
                if (sleepLeft <= 0f && curName == "Sleep") wake()
            }
        }
        if (mode == Mode.FLOOR && curName == "WakeUp" && clipDone()) play(idleName)
    }

    private fun fallOff() {
        onLedge = -1; grounded = false; vy = size * 0.2f; vx = 0f; mode = Mode.AIR; play("JumpAir")
        x = x.coerceIn(-halfW + size * 0.45f, halfW - size * 0.45f); discover("whoops")
    }

    private fun physics(dt: Float) {
        if (mode == Mode.CARRY) return
        if (onLedge >= 0) {
            val L = ledgeById(onLedge)
            if (L == null) fallOff() else { x = L.l + ledgeDx; y = L.t }
            return
        }
        if (!grounded) {
            vy -= GRAV * size * dt * (if (species.glide) 0.3f else 1f)
            if (species.glide) vy = max(vy, -size * 1.6f)
            fallTop = max(fallTop, y)
            x += vx * dt; y += vy * dt
            if (vy < 0f) for (L in ledges) {
                if (x > L.l + size * 0.1f && x < L.r - size * 0.1f && y <= L.t && y - vy * dt >= L.t - 0.002f && visible(L)
                    && (landOnLedge == L.id || landOnLedge == -2)) {
                    y = L.t; land(); onLedge = L.id; ledgeDx = x - L.l; mode = Mode.PERCHED; discover("perch"); return
                }
            }
            if (y <= floorY) { y = floorY; land(); mode = Mode.FLOOR }
            x = x.coerceIn(-halfW + size * 0.42f, halfW - size * 0.42f)
        } else {
            if (abs(vx) > 0f && mode != Mode.WALK) { x += vx * dt; vx *= exp(-6f * dt); if (abs(vx) < 0.005f) vx = 0f }
            x = x.coerceIn(-halfW + size * 0.42f, halfW - size * 0.42f)
            if (mode == Mode.FLOOR && abs(y - floorY) > 0.001f) { grounded = false; vy = 0f; mode = Mode.AIR; landOnLedge = -2 }
        }
    }
    private fun land() {
        grounded = true; vy = 0f; vx *= 0.2f; landOnLedge = -1
        val drop = (fallTop - y) / size; fallTop = y
        sqV += min(3.2f, drop * 0.9f + 0.9f)
        facing = 0f
        // the higher the fall, the harder the landing: a soft hop, a thud, or a full tumble
        if (drop > 1.6f && !species.glide && pet.clips.containsKey(species.alias["Tumble"] ?: "Tumble")) {
            play("Tumble"); discover("big fall"); burst(9f, 8, x, y + size * 0.04f, 1f, 1f, 1f, 0.035f)
        } else {
            play("JumpLand"); burst(9f, if (drop > 0.6f) 5 else 3, x, y + size * 0.04f, 1f, 1f, 1f, if (drop > 0.6f) 0.03f else 0.02f)
        }
    }

    private fun advance(dt: Float) {
        val c = cur ?: return
        curT += dt
        if (c.loop && curT > c.duration) curT %= max(0.01f, c.duration)
        val nb = pet.nBones * 12
        pet.sample(c, min(curT, c.duration), matA, ctlA)
        val ff = fadeFrom
        if (ff != null && fade < 1f) {
            fade = min(1f, fade + dt / 0.2f)
            fadeFromT += dt
            pet.sample(ff, if (ff.loop) fadeFromT % max(0.01f, ff.duration) else min(fadeFromT, ff.duration), matB, ctlB)
            val a = fade * fade * (3f - 2f * fade)
            for (i in 0 until nb) pet.pose[i] = matB[i] + (matA[i] - matB[i]) * a
            for (i in ctlA.indices) ctlA[i] = ctlB[i] + (ctlA[i] - ctlB[i]) * a
            if (a > 0.5f) { pet.face = ctlA[6] } else pet.face = ctlB[6]
        } else {
            System.arraycopy(matA, 0, pet.pose, 0, nb); pet.face = ctlA[6]
        }
        pet.facePhase = ctlA[7]
        // turn toward where we go, back toward you when we stop
        val yawT = facing * 0.95f + (if (facing == 0f) lookX * 0.25f else 0f) + (if (mode == Mode.CARRY) bendX * 1.2f else 0f)
        yawNow += (yawT - yawNow) * min(1f, dt * 6f)
        pet.yaw = yawNow
    }

    private fun eyes(dt: Float) {
        if (species.ownFace) {
            // the clip already blinks, glances and emotes; we only add where the finger is
            if (!touching && sinceTouch > 1.5f) { lookTX = 0f; lookTY = 0f }
            lookX += (lookTX - lookX) * min(1f, dt * 8f); lookY += (lookTY - lookY) * min(1f, dt * 8f)
            val lx = if (touching || sinceTouch < 1.5f) lookX else 0f; val ly = if (touching || sinceTouch < 1.5f) lookY else 0f
            pet.eyeL[0] = ctlA[0]; pet.eyeR[0] = ctlA[1]
            pet.eyeL[1] = (ctlA[2] + lx).coerceIn(-0.5f, 0.5f); pet.eyeL[2] = (ctlA[3] + ly).coerceIn(-0.4f, 0.4f)
            pet.eyeR[1] = (ctlA[4] + lx).coerceIn(-0.5f, 0.5f); pet.eyeR[2] = (ctlA[5] + ly).coerceIn(-0.4f, 0.4f)
            pet.eyeL[3] = ctlA[8]; pet.eyeR[3] = ctlA[8]
            pet.mood[0] = ctlA[9]; pet.mood[1] = if (ctlA[10] > 0.05f) ctlA[10] else 1f
            return
        }
        pet.eyeL[3] = 0f; pet.eyeR[3] = 0f; pet.mood[0] = 0f; pet.mood[1] = 1f
        nextBlink -= dt
        if (nextBlink <= 0f) { blink = 1f; nextBlink = 2.2f + Random.nextFloat() * 3.5f; if (Random.nextFloat() < 0.2f) nextBlink = 0.2f }
        blink = max(0f, blink - dt * 7f)
        val b = if (blink > 0.5f) 1f - (blink - 0.5f) * 2f else blink * 2f
        if (!touching && sinceTouch > 1.5f && Random.nextFloat() < dt * 0.4f) {
            lookTX = (Random.nextFloat() - 0.5f) * 0.6f; lookTY = (Random.nextFloat() - 0.5f) * 0.3f
            if (Random.nextFloat() < 0.4f) { lookTX = 0f; lookTY = 0f }
        }
        lookX += (lookTX - lookX) * min(1f, dt * 8f); lookY += (lookTY - lookY) * min(1f, dt * 8f)
        val blinkOk = mode != Mode.SLEEP
        pet.eyeL[0] = max(ctlA[0], if (blinkOk) b else 0f); pet.eyeR[0] = max(ctlA[1], if (blinkOk) b else 0f)
        pet.eyeL[1] = ctlA[2] + lookX; pet.eyeL[2] = ctlA[3] + lookY
        pet.eyeR[1] = ctlA[4] + lookX; pet.eyeR[2] = ctlA[5] + lookY
    }

    private fun particles(dt: Float) {
        val it = parts.iterator()
        while (it.hasNext()) {
            val p = it.next(); p.life += dt
            if (p.life > p.max) { it.remove(); continue }
            p.x += p.vx * dt; p.y += p.vy * dt; p.vy -= (if (p.kind == 9f) 0.6f else 0.02f) * dt; p.vx *= exp(-1.5f * dt)
        }
    }

    // ------------------------------------------------------------ draw
    override fun draw(input: SceneInput) {
        val m = min(w, h)
        val k = size / pet.height
        val boxHalf = pet.boxR * k
        pet.ensureSprite((boxHalf * 2f * m * 1.6f).toInt())   // supersampled: crisp edges and fur
        val hh = pet.height
        if (mode == Mode.CARRY || (onLedge < 0 && !grounded && abs(bendX) > 0.01f)) {
            pet.bend[0] = bendX * hh; pet.bend[1] = -abs(bendX) * hh * 0.25f; pet.bend[2] = 1.05f * hh; pet.bend[3] = hh
        } else {
            pet.bend[0] = bendX * hh * 0.6f; pet.bend[1] = 0f; pet.bend[2] = 0f; pet.bend[3] = -hh
        }
        pet.squash = sq
        pet.render()
        pet.beginFx()
        val air = if (onLedge >= 0) 0f else ((y - floorY) / size).coerceIn(0f, 2f)
        if (onLedge < 0) pet.quad(1f, x, floorY + 0.004f, size * 0.45f * (1f - air * 0.3f), size * 0.075f, 0f, 0f, 0f, 0f, 1f - min(0.8f, air * 0.6f))
        else ledgeById(onLedge)?.let { pet.quad(1f, x, it.t + 0.004f, size * 0.4f, size * 0.065f, 0f, 0f, 0f, 0f, 0.8f) }
        if (mode != Mode.CARRY) { swing *= 0.85f; swingV = 0f }
        val cy = y + pet.boxCz * k
        if (abs(swing) > 0.002f) {
            // rotate the hanging body around the finger
            val px = tX; val py = tY; val dx = x - px; val dy = cy - py
            val cs = cos(swing); val sn = sin(swing)
            pet.sprite(px + dx * cs - dy * sn, py + dx * sn + dy * cs, boxHalf, swing, 1f)
        } else pet.sprite(x, cy, boxHalf, 0f, 1f)
        for (p in parts) {
            val a = (min(1f, p.life * 6f) * min(1f, (p.max - p.life) * 2.5f)).coerceIn(0f, 1f)
            val s = p.s * (0.6f + 0.4f * min(1f, p.life * 4f))
            pet.quad(p.kind, p.x, p.y, s, s, p.rot, p.r, p.g, p.b, a)
        }
        pet.endFx()
    }

    companion object { const val GRAV = 9f }
    private fun softBody(dt: Float) {
        if (dt <= 0f) return
        if (lastX.isNaN()) { lastX = x; lastY = y }
        val vxNow = (x - lastX) / dt / size; val vyNow = (y - lastY) / dt / size
        lastX = x; lastY = y
        val ax = (vxNow - bodyVx) / dt
        bodyVx += (vxNow - bodyVx) * min(1f, dt * 20f); bodyVy += (vyNow - bodyVy) * min(1f, dt * 20f)
        val carry = mode == Mode.CARRY
        val drag = if (carry) -bodyVx * 0.22f else 0f
        val kick = (if (carry) -0.01f else -0.006f) * ax.coerceIn(-60f, 60f)
        val k = if (carry) 70f else 110f; val d = if (carry) 7f else 10f
        bendV += ((drag - bendX) * k - bendV * d + kick * k * 0.12f) * dt
        bendX = (bendX + bendV * dt).coerceIn(-0.55f, 0.55f)
        val stretch = when {
            carry -> (-bodyVy * 0.05f).coerceIn(-0.12f, 0.08f) - 0.04f
            mode == Mode.AIR && vy < 0f -> (vy / size * 0.03f).coerceIn(-0.12f, 0f)
            else -> 0f
        }
        sqV += ((stretch - sq) * 90f - sqV * 9f) * dt
        sq = (sq + sqV * dt).coerceIn(-0.2f, 0.32f)
    }

}

/** Which Meshy pet (if any) a character id + chosen look maps to. Snowy (10) only exists in 3D. */
fun petSpeciesFor(character: Int, style: Int, coat: Int = 0): PetSpecies? = when {
    style <= 0 -> null
    character == 4 -> when (coat) { 0 -> PET_RINGNECK; 2 -> PET_COCKATIEL; else -> PET_BUDGIE }
    character == 7 -> PET_NUTMEG
    character == 6 -> PET_REAPER
    else -> null
}
const val SNOWY_ID = 10
