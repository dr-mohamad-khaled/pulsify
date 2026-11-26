package app.pulsify.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import app.pulsify.core.AudioFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/** One statue of the Pharaohs section: its files and the camera shots of each home-screen page. */
class StatueLook(val name: String, val file: String, val env: Float, val lock: Shot, val pages: List<Shot>, val spin: Float = 0f, val group: String = "Pharaohs",
                 /** Height of the head (model units) for the wake close-up; NaN = the section's default. */
                 val headY: Float = Float.NaN,
                 /** Flying rigs: downward shoulder angle and wing-beat size (see [StatueBird]). */
                 val wingBias: Float = 0f, val flapAmp: Float = 1f,
                 /** Animator: "fly" (StatueBird), "crow" or "perch" (StatueCrow; perch = a parrot). Empty for statues. */
                 val rig: String = "",
                 /** The same bird sitting (a perched twin): the Studio offers Perched / Flying / Shuffle. */
                 val perch: StatueLook? = null,
                 /** How far the camera pulls back in the middle of a swipe (1 = the statues' drone move). */
                 val swipePull: Float = 1f,
                 /** A scene drawn in code instead of a model file ("asteroids", "nebula": see [SpaceFx]). */
                 val proc: String = "")

/** A sitting twin for a flying bird: perched-bird shots and the perch animator. */
private fun perched(name: String, file: String, d: Float, headY: Float) = StatueLook(name, file, 0.55f, Shot(0f, 6f, d, 0.05f),
    // pages stay in front of the bird (within ±50 degrees) at a middle distance, so a swipe is a short, quick move
    listOf(Shot(-28f, 10f, d * 0.95f, 0.05f), Shot(22f, -4f, d * 0.88f, headY * 0.25f), Shot(48f, 7f, d * 0.95f, 0.05f), Shot(-50f, 12f, d, 0.05f), Shot(4f, 8f, d * 0.97f, 0.05f)),
    group = "Birds", headY = headY, rig = "perch", swipePull = 0.35f)

/** A camera shot around the statue: azimuth / elevation in degrees (0 = its face), distance, look-at height. */
data class Shot(val az: Float, val el: Float, val d: Float, val y: Float = 0f, val roll: Float = 0f)

/** Super wallpaper sections on the home screen, in order, with their one-line pitch. */
val STATUE_GROUPS = listOf(
    "Pharaohs" to "Gold, stone and gods in the dark. Unlock it, swipe your pages, play music.",
    "Birds" to "Birds in flight that never land. Swipe and they power up.",
    "Space" to "Moons, nebulae and asteroid storms. The camera flies through them as you swipe.",
)

val STATUE_LOOKS = listOf(
    StatueLook("Tutankhamun", "tut", 1.15f, Shot(0f, 4f, 5.6f),
        listOf(Shot(-28f, 6f, 4.5f), Shot(18f, -8f, 3.4f, 0.08f), Shot(72f, 4f, 4.3f), Shot(125f, 14f, 4.6f), Shot(180f, 6f, 4.7f))),
    StatueLook("Nefertiti", "nef", 0.8f, Shot(0f, 3f, 5.4f),
        listOf(Shot(-62f, 3f, 3.9f), Shot(-18f, -6f, 3.0f, 0.08f), Shot(40f, 6f, 3.8f), Shot(90f, 2f, 4.1f), Shot(150f, 12f, 4.4f))),
    StatueLook("Anubis", "anu", 1.0f, Shot(0f, 4f, 5.6f),
        listOf(Shot(-32f, -10f, 4.3f), Shot(14f, 2f, 3.2f, 0.08f), Shot(-88f, 4f, 4.0f), Shot(60f, 12f, 4.4f), Shot(175f, 10f, 4.7f))),
    StatueLook("Horus", "hor", 1.05f, Shot(0f, 4f, 5.6f),
        listOf(Shot(-30f, -8f, 4.3f), Shot(16f, 2f, 3.2f, 0.1f), Shot(-85f, 4f, 4.0f), Shot(58f, 12f, 4.4f), Shot(170f, 10f, 4.7f))),
    // a rigged bird that flies on the spot (STA2 file): the same drone camera circles it
    StatueLook("Turquoise Motmot", "mot", 0.7f, Shot(0f, 10f, 3.7f, 0.02f),
        listOf(Shot(-30f, 14f, 3.5f), Shot(22f, -6f, 2.9f, 0.03f), Shot(76f, 8f, 3.4f), Shot(-115f, 24f, 3.7f), Shot(160f, 16f, 3.8f)), group = "Birds"),
    StatueLook("Solar Queen", "sol", 1.0f, Shot(0f, 4f, 5.4f),
        listOf(Shot(-30f, -8f, 4.2f), Shot(16f, 2f, 3.1f, 0.12f), Shot(-85f, 4f, 3.9f), Shot(58f, 12f, 4.3f), Shot(170f, 10f, 4.6f))),
    StatueLook("Golden Bastet", "bas", 1.0f, Shot(0f, 4f, 5.0f),
        listOf(Shot(-35f, -6f, 4.0f), Shot(16f, 2f, 2.9f, 0.14f), Shot(-90f, 4f, 3.8f), Shot(60f, 12f, 4.1f), Shot(170f, 10f, 4.4f))),
    StatueLook("Temple Guardian", "ram", 1.0f, Shot(0f, 4f, 5.0f),
        listOf(Shot(-30f, -8f, 4.0f), Shot(16f, 2f, 3.0f, 0.12f), Shot(-85f, 4f, 3.8f), Shot(58f, 12f, 4.2f), Shot(170f, 10f, 4.5f))),
    // the moon turns slowly on its own while the drone circles it
    StatueLook("Cratered Moon", "moon", 0.5f, Shot(0f, 4f, 5.6f),
        listOf(Shot(-30f, -6f, 4.6f), Shot(18f, 4f, 3.6f), Shot(-80f, 10f, 4.4f), Shot(60f, 16f, 4.8f), Shot(170f, 8f, 5.0f)), spin = 5f, group = "Space"),
    // a living crow standing on the spot (STA2 --crow): it looks around and hops round to face each new camera
    StatueLook("Midnight Sentinel", "crow", 0.55f, Shot(0f, 6f, 3.7f, 0.05f),
        listOf(Shot(-38f, 10f, 3.4f, 0.05f), Shot(24f, -5f, 2.7f, 0.22f), Shot(82f, 7f, 3.3f, 0.05f), Shot(-112f, 16f, 3.6f, 0.05f), Shot(158f, 11f, 3.7f, 0.05f)),
        group = "Birds", headY = 0.6f, rig = "crow"),
    // the Indian ringneck in endless flight (STA2 --ringneck), the same drone camera circling it
    StatueLook("Emerald Ringneck", "ring", 0.6f, Shot(0f, 10f, 5.6f, 0.02f),
        listOf(Shot(-30f, 14f, 5.3f), Shot(22f, -6f, 4.7f, 0.04f), Shot(76f, 8f, 5.1f), Shot(-115f, 22f, 5.5f), Shot(160f, 16f, 5.6f)),
        group = "Birds", wingBias = 0.42f, flapAmp = 1.25f, rig = "fly"),
    // the cockatiel in endless flight (STA2 --cockatiel)
    StatueLook("Golden Cockatiel", "tiel", 0.6f, Shot(0f, 10f, 4.4f, 0.0f),
        listOf(Shot(-30f, 14f, 4.2f), Shot(22f, -6f, 3.8f, 0.03f), Shot(76f, 8f, 4.0f), Shot(-115f, 22f, 4.3f), Shot(160f, 16f, 4.4f)),
        group = "Birds", wingBias = 0.16f, flapAmp = 0.95f, rig = "fly"),
    // the budgie ("love bird"), sitting only
    perched("Budgie", "budgie", 4.4f, 0.6f),
    // code-drawn space scenes (SpaceFx): two asteroids smash into each other again and again; tap to make them clash
    StatueLook("Asteroid Clash", "proc_asteroids", 0f, Shot(0f, 8f, 6.2f),
        listOf(Shot(-34f, 12f, 5.6f), Shot(20f, -6f, 4.6f), Shot(-95f, 18f, 5.8f), Shot(70f, 24f, 6.2f), Shot(165f, 10f, 5.4f)),
        group = "Space", headY = 0f, proc = "asteroids"),
    // a glowing nebula around a cluster of young stars; the clouds slide past each other as the camera circles
    StatueLook("Nebula", "proc_nebula", 0f, Shot(0f, 6f, 5.4f),
        listOf(Shot(-40f, 10f, 4.6f), Shot(25f, -8f, 3.6f), Shot(-110f, 20f, 5.2f), Shot(80f, 28f, 5.6f), Shot(170f, 12f, 4.2f)),
        group = "Space", headY = 0f, proc = "nebula"),
)

/**
 * Pharaohs: a 3D statue as a Xiaomi-style super wallpaper, always centred on the screen.
 *  - Screen on while locked: the statue punches in fast from far away to the lock-screen framing.
 *  - Unlock: a drone flight (lift, bank, dive) to the angle of the current home page.
 *  - Every home page has its own camera shot; changing page flies there with a lift and a bank.
 *  - When it first enters the view: a spiral fly-in past the face, then out to the whole statue.
 * The statue never reacts to music; gold pharaonic line icons around it do (behind it, no glow).
 * Drawn into its own depth-buffered texture, composited over true black.
 */
class StatueScene(private val context: Context) : Creature {
    override var listener: SceneListener? = null
    override var bottomInset = 0f

    /** The look picked in the Studio, and for birds with a sitting twin: 0 perched, 1 flying, 2 shuffle (a new pick each time the screen wakes). */
    @Volatile var baseLook: StatueLook = STATUE_LOOKS[0]
    @Volatile var pose = 1
    private var shuffleSit = false
    private var shuffledFor: StatueLook? = null
    /** What is on screen now. */
    val look: StatueLook get() {
        val b = baseLook; val p = b.perch ?: return b
        return when (pose) { 0 -> p; 2 -> if (shuffleSit) p else b; else -> b }
    }
    private val flyer = StatueBird()
    /** The code-drawn space looks. */
    private val space = SpaceFx(context)
    private val crow = StatueCrow()
    /** The animator for rigged looks: the crow stands and lives, the others fly. */
    private val bird: StatueRig get() = look.let { l ->
        // perched birds stay put at their closest shot and walk up to you when the camera settles further away
        if (l.rig == "crow" || l.rig == "perch") crow.also { it.parrot = l.rig == "perch"; it.walkNear = l.pages.minOf { s -> s.d } } else flyer.also { it.wingBias = l.wingBias; it.flapAmp = l.flapAmp }
    }

    private var w = 1; private var h = 1
    private var time = 0f

    // ---- camera rig: the "drone" ----
    private var az = 0f; private var el = 0f; private var dist = 15f; private var ty = 0f; private var roll = 0f
    private var flight: Flight? = null
    private var glintE = 0f
    private var page = 0
    private var locked = false
    private var entered = ""
    private var tiltX = 0f; private var tiltY = 0f; private var tiltTX = 0f; private var tiltTY = 0f
    private var lightK = 0f; private var dim = 1f

    private class Flight(val keys: List<Shot>, val dur: Float, val ease: (Float) -> Float, val glint: Float) { var t = 0f }

    // Between keyframed flights the camera is a critically damped spring towards the page's shot. Each
    // swipe retargets it and adds its own lift and bank, so quick swipes stack instead of replacing each other.
    private var tAz = 0f; private var tEl = 4f; private var tD = 5.6f; private var tY = 0f
    private var vAz = 0f; private var vEl = 0f; private var vD = 0f; private var vY = 0f; private var vR = 0f
    private var pAz = 0f; private var pEl = 0f; private var pD = 15f; private var pY = 0f; private var pR = 0f
    private var lastDt = 1f / 60f

    // A super wallpaper: always smooth (60 fps while it is on screen), the bird never stops flying.
    override val busy: Boolean get() = true

    // ---------------------------------------------------------------- shots
    private fun current() = Shot(az, el, dist, ty, roll)
    private fun near(from: Float, to: Float): Float { var a = to; while (a - from > 180f) a -= 360f; while (a - from < -180f) a += 360f; return a }
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private val outExpo: (Float) -> Float = { t -> if (t >= 1f) 1f else 1f - 2f.pow(-10f * t) }
    private val inOutCubic: (Float) -> Float = { t -> if (t < .5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f }
    private val outCubic: (Float) -> Float = { t -> 1f - (1f - t).pow(3) }

    private fun fly(keys: List<Shot>, dur: Float, ease: (Float) -> Float, glint: Float = 0f) { flight = Flight(keys, dur, ease, glint) }

    private fun pageShot(i: Int) = look.pages[((i % look.pages.size) + look.pages.size) % look.pages.size]

    /** Screen on while locked: the drone starts tight on a detail (the face, the bird's head, the
     *  moon's craters) out of black, then pulls back and swings round until the subject sits centred,
     *  facing the screen, in the lock framing. */
    private fun shotWake() {
        val L = look.lock; val tz = L.az
        val headY = if (!look.headY.isNaN()) look.headY else when (look.group) { "Birds" -> 0.06f; "Space" -> 0.1f; else -> 0.48f }
        val side = if ((wakeN++ and 1) == 0) 1f else -1f
        az = tz + side * 62f; el = L.el + 24f; dist = if (look.proc.isNotEmpty()) 2.2f else 1.05f; ty = headY; roll = side * 16f
        fly(listOf(current(),
            Shot(tz + side * 40f, L.el + 15f, 1.6f, headY * 0.85f, side * 10f),
            Shot(tz + side * 16f, L.el + 6f, L.d * 0.8f, L.y + headY * 0.25f, side * 3f),
            L.copy(az = tz)), 2.3f, outQuart, 1.3f)
        fadeIn = 0f
    }
    private var wakeN = 0
    /** 0..1 light ramp after the screen turns on, so the close-up rises out of black. */
    private var fadeIn = 1f
    private val outQuart: (Float) -> Float = { t -> 1f - (1f - t).pow(2.6f) }

    /** Unlock: lift off the lock framing, bank around and dive to the page's angle. */
    private fun shotUnlock() {
        val P = pageShot(page); val s = current(); val tz = near(s.az, P.az); val dir = if (tz - s.az >= 0f) 1f else -1f
        fly(listOf(s,
            Shot(s.az + dir * 10f, s.el + 16f, s.d * 1.12f, s.y + 0.15f, -dir * 4f),
            Shot(lerp(s.az, tz, .6f) + dir * 18f, P.el + 10f, (s.d + P.d) / 2f, (s.y + P.y) / 2f, -dir * 7f),
            P.copy(az = tz + dir * 6f, d = P.d * 0.93f, roll = -dir * 2f),
            P.copy(az = tz)), 1.5f, inOutCubic, 1.4f)
    }

    /** The statue enters the view: a spiral fly-in with a close pass by the face, then out to the whole statue. */
    private fun shotEntrance() {
        val T = if (locked) look.lock else pageShot(page); val b = T.az
        fly(listOf(Shot(b + 235f, 38f, 10f, 1.1f, 10f), Shot(b + 150f, 22f, 5.2f, 0.8f, 12f),
            Shot(b + 70f, 8f, 2.4f, 0.45f, 6f), Shot(b + 22f, T.el + 4f, T.d * 0.82f, T.y + .05f, 2f), T.copy(az = b)),
            3.0f, outCubic, 1.6f)
    }

    // Catmull-Rom through the keys.
    private fun cr(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t; val t3 = t2 * t
        return .5f * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
    }
    private fun sample(keys: List<Shot>, u: Float) {
        val n = keys.size - 1; val f = min(u * n, n - 1e-4f); val i = f.toInt(); val t = f - i
        fun k(j: Int) = keys[j.coerceIn(0, n)]
        az = cr(k(i - 1).az, k(i).az, k(i + 1).az, k(i + 2).az, t)
        el = cr(k(i - 1).el, k(i).el, k(i + 1).el, k(i + 2).el, t)
        dist = cr(k(i - 1).d, k(i).d, k(i + 1).d, k(i + 2).d, t)
        ty = cr(k(i - 1).y, k(i).y, k(i + 1).y, k(i + 2).y, t)
        roll = cr(k(i - 1).roll, k(i).roll, k(i + 1).roll, k(i + 2).roll, t)
    }

    // ---------------------------------------------------------------- events
    /** The phone's screen came on ([locked]: over the lock screen). */
    override fun screenOn(locked: Boolean) {
        // shuffle: sitting or flying, picked again every time the screen wakes
        if (pose == 2 && baseLook.perch != null) shuffleSit = if (shuffledFor !== baseLook) java.util.Random().nextBoolean().also { shuffledFor = baseLook } else !shuffleSit
        entered = look.file   // the wake shot shows the new pick (no entrance flight)
        this.locked = locked
        if (locked) shotWake()
    }

    /** Unlocked. */
    override fun greet() {
        if (locked) { locked = false; shotUnlock() }
    }

    // every swipe flies the drone to the next angle, even two quick swipes in a row
    override val swipeCooldownMs: Long get() = 120L

    override fun swiped(dir: Float, fast: Boolean) {
        if (locked) return
        page = (page + (if (dir > 0f) 1 else -1)).let { ((it % 5) + 5) % 5 }
        pageMove(if (dir > 0f) 1f else -1f)
    }

    /** Swipe to [page]: take over any flight with its current motion, retarget the spring, kick it. */
    private fun pageMove(dir: Float) {
        if (flight != null) {
            flight = null
            val k = 1f / max(lastDt, 1f / 120f)
            vAz = (az - pAz) * k; vEl = (el - pEl) * k; vD = (dist - pD) * k; vY = (ty - pY) * k; vR = (roll - pR) * k
            tAz = az; tEl = el; tD = dist; tY = ty
        }
        val P = pageShot(page)
        tAz = near(tAz, P.az); tEl = P.el; tD = P.d; tY = P.y
        val lift = min(1f, abs(tAz - az) / 60f) * 0.6f + 0.4f
        vR += -dir * 110f; vEl += 120f * lift * (0.5f + 0.5f * look.swipePull); vD += 7f * lift * look.swipePull
        bird.kick(dir)
    }

    private fun stepSpring(dt: Float) {
        val n = max(1, kotlin.math.ceil(dt * 240f).toInt()); val h = dt / n
        val w = 6.2f; val wr = 5.2f
        repeat(n) {
            vAz += (w * w * (tAz - az) - 2f * w * vAz) * h; az += vAz * h
            vEl += (w * w * (tEl - el) - 2f * w * vEl) * h; el += vEl * h
            vD += (w * w * (tD - dist) - 2f * w * vD) * h; dist += vD * h
            vY += (w * w * (tY - ty) - 2f * w * vY) * h; ty += vY * h
            vR += (wr * wr * (0f - roll) - 2f * wr * vR) * h; roll += vR * h
        }
        glintE = min(1f, abs(vAz) / 140f) * 1.1f
    }

    override fun perform(trick: String): Boolean {
        when (trick) {
            "wake" -> { locked = true; shotWake() }
            "unlock" -> { if (!locked) { locked = true; az = look.lock.az; el = look.lock.el; dist = look.lock.d; ty = 0f }; greet() }
            "next" -> { if (locked) greet(); page = (page + 1) % 5; pageMove(1f) }
            "prev" -> { if (locked) greet(); page = (page + 4) % 5; pageMove(-1f) }
            "fly" -> shotEntrance()
            else -> return false
        }
        return true
    }

    override fun touchDown(nx: Float, ny: Float): Boolean { tiltTX = nx - .5f; tiltTY = ny - .5f; bird.touch(tiltTX, tiltTY, true); return false }
    override fun touchMove(nx: Float, ny: Float): Boolean { tiltTX = nx - .5f; tiltTY = ny - .5f; bird.touch(tiltTX, tiltTY, true); return false }
    override fun touchUp(): Boolean { tiltTX = 0f; tiltTY = 0f; bird.touch(0f, 0f, false); return false }
    override fun tapAt(nx: Float, ny: Float): Boolean { if (look.proc.isEmpty()) return false; space.tap(); return true }
    override fun longPressAt(nx: Float, ny: Float): Boolean = false

    private var pagesKnown = false

    // ---------------------------------------------------------------- update
    override fun update(dt: Float, input: SceneInput) {
        time += dt; lastDt = dt
        fadeIn = min(1f, fadeIn + dt / 0.45f)
        if (entered != look.file) {
            if (entered.isNotEmpty() || !locked) shotEntrance()
            entered = look.file
        }
        pagesKnown = input.pagesKnown
        pAz = az; pEl = el; pD = dist; pY = ty; pR = roll
        flight?.let { f ->
            f.t += dt
            val u = min(1f, f.t / f.dur)
            sample(f.keys, f.ease(u))
            glintE = sin(PI.toFloat() * u) * f.glint
            if (u >= 1f) {
                flight = null
                tAz = az; tEl = el; tD = dist; tY = ty; vAz = 0f; vEl = 0f; vD = 0f; vY = 0f; vR = 0f
            }
        } ?: stepSpring(dt)
        val music0 = input.audio.source != AudioFrame.Source.NONE
        bird.update(dt, time, if (music0) input.audio.bass else 0f)
        if (look.proc.isNotEmpty()) space.update(dt, look.proc, if (music0) input.audio.bass else 0f)
        // where the camera will settle: the end of the flight, or the spring's target
        bird.rest((flight?.keys?.last()?.az ?: tAz) * D2R, if (locked) 0f else (flight?.keys?.last()?.d ?: tD))   // on the lock screen it stays in the middle
        tiltX += (tiltTX - tiltX) * min(1f, dt * 5f); tiltY += (tiltTY - tiltY) * min(1f, dt * 5f)
        // lights come up after the far "screen off" look
        val farK = ((dist - 8f) / 7f).coerceIn(0f, 1f)
        lightK = (1f - farK * 0.9f) * (fadeIn * fadeIn * (3f - 2f * fadeIn))
        dim = if (input.mood == app.pulsify.core.Mood.NIGHT) 0.75f else 1f

        // glyphs: hidden in silence, fade in with music, each listens to its own band
        val a = input.audio
        val music = a.source != AudioFrame.Source.NONE
        val energy = a.bass * .5f + a.mid * .3f + a.treble * .2f
        musicOnFor = if (music && energy > .04f) musicOnFor + dt else max(0f, musicOnFor - dt * 2f)
        val want = if (musicOnFor > .3f) 1f else 0f
        glyphVis += (want - glyphVis) * min(1f, dt * (if (want > 0f) 1.6f else 0.9f))
        for (g in GLYPHS) {
            val v = when (g.band) { 0 -> a.bass; 1 -> a.mid; else -> a.treble } * (if (music) 1f else 0f)
            g.lit += (v - g.lit) * min(1f, dt * (if (v > g.lit) 18f else 5f))
        }
    }

    // ---------------------------------------------------------------- GL
    private var prog = 0; private var compProg = 0; private var quadVbo = 0
    private var aN = 1; private var aT = 2; private var aU = 3; private var aJ = 4; private var aW = 5
    private var stride = 20; private var skinned = false
    private var vbo = 0; private var ibo = 0; private var icount = 0; private var idxType = GLES20.GL_UNSIGNED_INT
    private var texC = 0; private var texN = 0; private var texMR = 0
    private var loaded = ""
    private val scaleXYZ = FloatArray(3)
    private var fbo = 0; private var fboTex = 0; private var depthRb = 0; private var fbW = 0; private var fbH = 0
    private val uni = HashMap<String, Int>()
    private val cuni = HashMap<String, Int>()

    override fun onSurfaceCreated() {
        prog = Gl.program(VS, FS)
        aN = GLES20.glGetAttribLocation(prog, "aNrm"); aT = GLES20.glGetAttribLocation(prog, "aTan"); aU = GLES20.glGetAttribLocation(prog, "aUv")
        aJ = GLES20.glGetAttribLocation(prog, "aJ"); aW = GLES20.glGetAttribLocation(prog, "aW")
        for (n in listOf("uMVP", "uScale", "uSpin", "uSkin", "uB", "uCam", "uC", "uNm", "uMR", "uKeyDir", "uKeyCol", "uRimADir", "uRimACol", "uRimVDir", "uRimVCol",
            "uGlintDir", "uGlint", "uEnv", "uLight")) uni[n] = GLES20.glGetUniformLocation(prog, n)
        compProg = Gl.program(CVS, CFS)
        for (n in listOf("uTex", "uMode", "uAlpha")) cuni[n] = GLES20.glGetUniformLocation(compProg, n)
        val ids = IntArray(1); GLES20.glGenBuffers(1, ids, 0); quadVbo = ids[0]
        val q = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val fb = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().put(q); fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo); GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 32, fb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        loaded = ""; fbW = 0; glyphTex = 0
        space.onSurfaceCreated()
    }

    override fun onSurfaceChanged(width: Int, height: Int) { w = max(1, width); h = max(1, height) }

    private fun ensureModel() {
        if (loaded == look.file) return
        freeModel()
        if (look.proc.isNotEmpty()) { pivX = 0f; pivY = 0f; pivZ = 0f; loaded = look.file; return }
        val bytes = context.assets.open("statues/${look.file}.sta").use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(bytes[0] == 'S'.code.toByte() && (bytes[3] == '1'.code.toByte() || bytes[3] == '2'.code.toByte())) { "bad statue file" }
        skinned = bytes[3] == '2'.code.toByte()
        bb.position(4)
        val nv = bb.int; val ni = bb.int
        bb.float; bb.float; bb.float
        scaleXYZ[0] = bb.float; scaleXYZ[1] = bb.float; scaleXYZ[2] = bb.float
        var vOff = 4 + 8 + 24
        if (skinned) {
            // the rig: parent and pivot (model units) of each bone
            val nb = bb.int
            check(nb in 1..StatueBird.MAX_BONES) { "rig has $nb bones" }
            val par = IntArray(nb); val heads = FloatArray(nb * 3)
            for (i in 0 until nb) { par[i] = bb.int; heads[i * 3] = bb.float; heads[i * 3 + 1] = bb.float; heads[i * 3 + 2] = bb.float }
            bird.setRig(par, heads)
            // flying birds: the camera circles the body, not the middle of the wingspan, so the bird stays centred
            if (look.rig == "fly") { pivX = heads[0]; pivY = heads[1]; pivZ = heads[2] } else { pivX = 0f; pivY = 0f; pivZ = 0f }
            vOff += 4 + nb * 16
        } else { bird.clear(); pivX = 0f; pivY = 0f; pivZ = 0f }
        stride = if (skinned) 28 else 20
        val vb = ByteBuffer.allocateDirect(nv * stride).order(ByteOrder.nativeOrder()); vb.put(bytes, vOff, nv * stride); vb.position(0)
        val iOff = vOff + nv * stride
        val ids = IntArray(2); GLES20.glGenBuffers(2, ids, 0); vbo = ids[0]; ibo = ids[1]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo); GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, nv * stride, vb, GLES20.GL_STATIC_DRAW)
        val ext = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
        if (nv <= 65535 || !ext.contains("OES_element_index_uint")) {
            // 16-bit indices: always fine below 65 536 vertices (and the only choice without the extension)
            val ib = ByteBuffer.allocateDirect(ni * 2).order(ByteOrder.nativeOrder())
            val src = ByteBuffer.wrap(bytes, iOff, ni * 4).order(ByteOrder.LITTLE_ENDIAN)
            var n = 0
            for (i in 0 until ni) { val v = src.int; if (v < 65536) { ib.putShort(v.toShort()); n++ } }
            ib.position(0); icount = n - n % 3; idxType = GLES20.GL_UNSIGNED_SHORT
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo); GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, n * 2, ib, GLES20.GL_STATIC_DRAW)
        } else {
            val ib = ByteBuffer.allocateDirect(ni * 4).order(ByteOrder.nativeOrder())
            val src = ByteBuffer.wrap(bytes, iOff, ni * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until ni) ib.putInt(src.int)
            ib.position(0); icount = ni; idxType = GLES20.GL_UNSIGNED_INT
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo); GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, ni * 4, ib, GLES20.GL_STATIC_DRAW)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0); GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        texC = texture("statues/${look.file}_c.webp"); texN = texture("statues/${look.file}_n.webp"); texMR = texture("statues/${look.file}_mr.webp")
        loaded = look.file
    }

    private var aniso = -1f
    private fun texture(path: String): Int {
        val opts = BitmapFactory.Options().apply { inPremultiplied = false; inScaled = false }
        val bmp = context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }!!
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        // anisotropic filtering keeps feathers and fur sharp where the surface turns away from the camera
        if (aniso < 0f) aniso = if (GLES20.glGetString(GLES20.GL_EXTENSIONS)?.contains("GL_EXT_texture_filter_anisotropic") == true)
            FloatArray(1).also { GLES20.glGetFloatv(0x84FF, it, 0) }[0].coerceAtMost(8f) else 0f
        if (aniso > 1f) GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, 0x84FE, aniso)
        bmp.recycle()
        return t[0]
    }

    private fun freeModel() {
        if (vbo != 0) GLES20.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0)
        val ts = intArrayOf(texC, texN, texMR).filter { it != 0 }.toIntArray()
        if (ts.isNotEmpty()) GLES20.glDeleteTextures(ts.size, ts, 0)
        vbo = 0; ibo = 0; texC = 0; texN = 0; texMR = 0; loaded = ""; skinned = false; bird.clear()
    }

    private fun ensureFbo(vw: Int, vh: Int) {
        if (fbo != 0 && vw == fbW && vh == fbH) return
        freeFbo()
        fbW = vw; fbH = vh
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0); fboTex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, vw, vh, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glGenRenderbuffers(1, ids, 0); depthRb = ids[0]
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthRb)
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, vw, vh)
        GLES20.glGenFramebuffers(1, ids, 0); fbo = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex, 0)
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, depthRb)
        val st = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target)
        check(st == GLES20.GL_FRAMEBUFFER_COMPLETE) { "statue fbo $st" }
    }

    private fun freeFbo() {
        if (fbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (fboTex != 0) GLES20.glDeleteTextures(1, intArrayOf(fboTex), 0)
        if (depthRb != 0) GLES20.glDeleteRenderbuffers(1, intArrayOf(depthRb), 0)
        fbo = 0; fboTex = 0; depthRb = 0; fbW = 0; fbH = 0
    }

    private val proj = FloatArray(16); private val view = FloatArray(16); private val mvp = FloatArray(16)
    private val vpBuf = IntArray(4)
    private var pivX = 0f; private var pivY = 0f; private var pivZ = 0f

    override fun draw(input: SceneInput) {
        if (prog == 0) return
        ensureModel()
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vpBuf, 0)
        val vw = max(1, vpBuf[2]); val vh = max(1, vpBuf[3])
        // supersampling: the model is drawn at twice the size and filtered down, so edges, feathers and fur
        // stay crisp (no jaggies), capped so the buffer never gets bigger than a 1440p phone at 2x
        val ss = if (look.proc.isEmpty() && vw * vh * 4 <= 1440 * 3200 * 4) 2 else 1
        ensureFbo(vw * ss, vh * ss)

        // ---- camera ----
        val aspect = vw.toFloat() / vh
        val hf = 24f * PI.toFloat() / 180f
        val fovY = (2f * atan(tan(hf / 2f) / aspect) * 180f / PI.toFloat()).coerceIn(30f, 48f)
        Matrix.perspectiveM(proj, 0, fovY, aspect, 0.05f, 60f)
        val live = dist < 10f
        val hovAz = if (live) sin(time * .31f) * 0.9f + sin(time * .13f) * 0.6f + tiltX * 7f else 0f
        val hovEl = if (live) sin(time * .23f + 1f) * 0.6f - tiltY * 5f else 0f
        if (!live && flight == null) az += 0.07f
        val a = (az + hovAz) * D2R; val e = (el + hovEl) * D2R
        val d = dist * (1f + (if (live) sin(time * .19f) * 0.025f else 0f))
        val cx = pivX + sin(a) * cos(e) * d; val cy = pivY + ty + sin(e) * d; val cz = pivZ + cos(a) * cos(e) * d
        // roll: tilt the up vector about the view axis
        val r = roll * D2R
        val upx = sin(r) * cos(a); val upy = cos(r); val upz = -sin(r) * sin(a)
        Matrix.setLookAtM(view, 0, cx, cy, cz, pivX, pivY + ty, pivZ, upx, upy, upz)
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)

        if (look.proc.isNotEmpty()) {
            // code-drawn space scene into the same texture, then the usual composite below
            space.draw(look.proc, view, proj, cx, cy, cz, fovY, aspect, fbo, fbW, fbH, lightK * dim)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target)
            GLES20.glViewport(vpBuf[0], vpBuf[1], vpBuf[2], vpBuf[3])
            composite(vw, vh)
            return
        }

        // ---- statue into its own depth-buffered texture ----
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, fbW, fbH)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glDepthFunc(GLES20.GL_LEQUAL); GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(prog)
        GLES20.glUniformMatrix4fv(u("uMVP"), 1, false, mvp, 0)
        GLES20.glUniform3f(u("uScale"), scaleXYZ[0], scaleXYZ[1], scaleXYZ[2])
        GLES20.glUniform1f(u("uSpin"), (look.spin * time * D2R) % (2f * PI.toFloat()))
        if (skinned && bird.bones > 0) {
            bird.pose(a)
            GLES20.glUniform1f(u("uSkin"), 1f)
            GLES20.glUniform4fv(u("uB"), bird.bones * 3, bird.mats, 0)
        } else GLES20.glUniform1f(u("uSkin"), 0f)
        GLES20.glUniform3f(u("uCam"), cx, cy, cz)
        // key light rides with the camera (up and to the left of it), rims from behind, glint sweeps on flights
        val ka = a - 0.55f
        dir3("uKeyDir", sin(ka) * 0.8f, 0.75f, cos(ka) * 0.8f)
        val L = lightK * dim
        GLES20.glUniform3f(u("uKeyCol"), 2.6f * L, 2.45f * L, 2.2f * L)
        dir3("uRimADir", -3f, 1.5f, -3f); GLES20.glUniform3f(u("uRimACol"), 2.4f * (0.4f + 0.6f * L), 1.6f * (0.4f + 0.6f * L), 0.9f * (0.4f + 0.6f * L))
        dir3("uRimVDir", 3f, 1.2f, -3f); GLES20.glUniform3f(u("uRimVCol"), 1.2f * L, 0.6f * L, 2.2f * L)
        val ga = a + ((flight?.let { it.t / it.dur } ?: 1f) * PI.toFloat() * 1.4f - 0.7f)
        dir3("uGlintDir", sin(ga) * 4f, 2.5f, cos(ga) * 4f)
        GLES20.glUniform1f(u("uGlint"), glintE * 3.2f)
        GLES20.glUniform1f(u("uEnv"), look.env * (0.12f + 0.88f * L))
        GLES20.glUniform1f(u("uLight"), L)
        tex(0, texC, "uC"); tex(1, texN, "uNm"); tex(2, texMR, "uMR")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        val st = stride
        GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 3, GLES20.GL_SHORT, true, st, 0)
        GLES20.glEnableVertexAttribArray(aN); GLES20.glVertexAttribPointer(aN, 4, GLES20.GL_BYTE, true, st, 8)
        GLES20.glEnableVertexAttribArray(aT); GLES20.glVertexAttribPointer(aT, 4, GLES20.GL_BYTE, true, st, 12)
        GLES20.glEnableVertexAttribArray(aU); GLES20.glVertexAttribPointer(aU, 2, GLES20.GL_UNSIGNED_SHORT, true, st, 16)
        val skinAttrs = skinned && aJ >= 0 && aW >= 0
        if (skinAttrs) {
            GLES20.glEnableVertexAttribArray(aJ); GLES20.glVertexAttribPointer(aJ, 4, GLES20.GL_UNSIGNED_BYTE, false, st, 20)
            GLES20.glEnableVertexAttribArray(aW); GLES20.glVertexAttribPointer(aW, 4, GLES20.GL_UNSIGNED_BYTE, true, st, 24)
        } else {
            if (aJ >= 0) GLES20.glVertexAttrib4f(aJ, 0f, 0f, 0f, 0f)
            if (aW >= 0) GLES20.glVertexAttrib4f(aW, 1f, 0f, 0f, 0f)
        }
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, icount, idxType, 0)
        for (i in intArrayOf(0, aN, aT, aU)) GLES20.glDisableVertexAttribArray(i)
        if (skinAttrs) { GLES20.glDisableVertexAttribArray(aJ); GLES20.glDisableVertexAttribArray(aW) }
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0); GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target)
        GLES20.glViewport(vpBuf[0], vpBuf[1], vpBuf[2], vpBuf[3])

        composite(vw, vh)
    }

    /** Glyphs behind, then the statue (or space scene) texture over them, onto the screen. */
    private fun composite(vw: Int, vh: Int) {
        GLES20.glUseProgram(compProg)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        if (glyphVis > 0.01f && look.proc.isEmpty()) {
            drawGlyphs(vw, vh)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glyphTex)
            GLES20.glUniform1i(cu("uTex"), 0); GLES20.glUniform1f(cu("uMode"), 1f); GLES20.glUniform1f(cu("uAlpha"), 1f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex)
        GLES20.glUniform1i(cu("uTex"), 0); GLES20.glUniform1f(cu("uMode"), 0f); GLES20.glUniform1f(cu("uAlpha"), 1f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(0); GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun dir3(n: String, x: Float, y: Float, z: Float) { val l = kotlin.math.sqrt(x * x + y * y + z * z); GLES20.glUniform3f(u(n), x / l, y / l, z / l) }
    private fun tex(unit: Int, t: Int, n: String) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t); GLES20.glUniform1i(u(n), unit)
    }
    private fun u(n: String) = uni[n] ?: -1
    private fun cu(n: String) = cuni[n] ?: -1

    // ---------------------------------------------------------------- 2D pharaonic glyphs
    private class Glyph(val path: Path, val x: Float, val y: Float, val s: Float, val band: Int, val ph: Float) { var lit = 0f }
    private val GLYPHS: List<Glyph> by lazy {
        fun p(d: String) = svgPath(d)
        listOf(
            Glyph(p(SUN), .5f, .27f, .16f, 1, 0f),
            Glyph(p(ANKH), .12f, .36f, .13f, 0, 1.7f), Glyph(p(EYE), .88f, .36f, .15f, 1, 3.4f),
            Glyph(p(SCARAB), .10f, .55f, .13f, 2, 5.1f), Glyph(p(DJED), .90f, .55f, .12f, 0, 6.8f),
            Glyph(p(LOTUS), .13f, .74f, .13f, 1, 8.5f), Glyph(p(PYRAMID), .87f, .74f, .14f, 2, 10.2f),
            Glyph(p(WAS), .5f, .86f, .10f, 0, 11.9f),
        )
    }
    private var glyphVis = 0f; private var musicOnFor = 0f
    private var glyphBmp: Bitmap? = null; private var glyphCv: Canvas? = null; private var glyphTex = 0
    private val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }

    private fun drawGlyphs(vw: Int, vh: Int) {
        // half the screen resolution is plenty for thin gold lines, and keeps the upload cheap
        val k = min(1f, 540f / min(vw, vh)); val bw = max(8, (vw * k).roundToInt()); val bh = max(8, (vh * k).roundToInt())
        var b = glyphBmp
        if (b == null || b.width != bw || b.height != bh) {
            b?.recycle(); b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888); glyphBmp = b; glyphCv = Canvas(b)
            if (glyphTex != 0) GLES20.glDeleteTextures(1, intArrayOf(glyphTex), 0); glyphTex = 0
        }
        val cv = glyphCv!!
        cv.drawColor(Color.TRANSPARENT, PorterDuff.Mode.SRC)
        val W = bw.toFloat(); val H = bh.toFloat()
        for (g in GLYPHS) {
            val size = g.s * W; val sc = size / 100f * (1f + g.lit * .07f)
            val jx = sin(time * 23f + g.ph) * g.lit * 1.6f * k * 2f; val jy = cos(time * 19f + g.ph * 2f) * g.lit * 1.6f * k * 2f
            val rot = (sin(time * .7f + g.ph) * .03f + sin(time * 17f + g.ph) * g.lit * .035f) * 57.3f
            val float = sin(time * .6f + g.ph) * H * .004f
            val al = (glyphVis * (.22f + g.lit * .95f)).coerceIn(0f, 1f)
            gp.color = Color.argb((al * 255).roundToInt(), 255, (190 + g.lit * 40).roundToInt().coerceAtMost(255), (110 + g.lit * 90).roundToInt().coerceAtMost(255))
            gp.strokeWidth = 2.6f * k * 2f / sc * (1f + g.lit * .4f)
            cv.save(); cv.translate(g.x * W + jx, g.y * H + jy + float); cv.rotate(rot); cv.scale(sc, sc); cv.translate(-50f, -50f)
            cv.drawPath(g.path, gp); cv.restore()
        }
        if (glyphTex == 0) {
            val t = IntArray(1); GLES20.glGenTextures(1, t, 0); glyphTex = t[0]; GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glyphTex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
        } else { GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glyphTex); GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, b) }
    }

    override fun release() {
        freeModel(); freeFbo(); space.release()
        if (prog != 0) GLES20.glDeleteProgram(prog); if (compProg != 0) GLES20.glDeleteProgram(compProg)
        if (quadVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
        if (glyphTex != 0) GLES20.glDeleteTextures(1, intArrayOf(glyphTex), 0)
        glyphBmp?.recycle(); glyphBmp = null; glyphCv = null
        prog = 0; compProg = 0; quadVbo = 0; glyphTex = 0
        entered = ""
    }

    companion object {
        private const val D2R = (PI / 180.0).toFloat()

        // Gold line icons in a 100 × 100 box.
        private const val ANKH = "M50 8 C34 8 30 22 30 30 C30 42 42 50 50 54 C58 50 70 42 70 30 C70 22 66 8 50 8 Z M50 18 C59 18 60 26 60 30 C60 37 54 42 50 45 C46 42 40 37 40 30 C40 26 41 18 50 18 Z M22 56 L78 56 L78 64 L22 64 Z M45 64 L55 64 L57 94 L43 94 Z"
        private const val EYE = "M12 42 C30 24 70 24 88 40 M10 34 C34 16 70 16 92 30 M20 44 C34 58 66 58 82 42 O50 41 9 9 M40 54 L36 82 M60 54 C66 66 74 74 84 70 C90 66 86 58 80 62"
        private const val SCARAB = "O50 13 9 9 O50 34 14 10 M50 44 C34 44 30 62 32 74 C34 88 44 94 50 94 C56 94 66 88 68 74 C70 62 66 44 50 44 Z M50 46 L50 92 M32 56 L8 46 M68 56 L92 46 M32 70 L10 74 M68 70 L90 74 M38 86 L22 96 M62 86 L78 96"
        private const val DJED = "M38 94 L62 94 L58 30 L42 30 Z M30 30 L70 30 M28 22 L72 22 M30 14 L70 14 M32 6 L68 6 M42 52 L58 52 M42 62 L58 62"
        private const val PYRAMID = "M50 10 L90 86 L10 86 Z M50 10 L44 30 L56 30 Z M50 10 L62 86 M24 60 L76 60 M36 38 L64 38"
        private const val LOTUS = "M50 88 L50 60 M50 60 C38 52 36 32 50 12 C64 32 62 52 50 60 Z M50 60 C34 60 22 46 20 30 C34 34 44 46 50 60 M50 60 C66 60 78 46 80 30 C66 34 56 46 50 60 M30 88 L70 88"
        private const val WAS = "M44 96 L50 84 L56 96 M50 84 L50 22 M50 22 L40 10 L62 6 L56 16 Z"
        private const val SUN = "O50 40 12 12 M38 44 C26 34 16 36 4 30 C14 44 24 48 38 50 M62 44 C74 34 84 36 96 30 C86 44 76 48 62 50 M10 40 L30 46 M90 40 L70 46"

        // uSkin 1: a rigged model (the bird), 4 bones per vertex, 3 rows of vec4 per bone.
        private const val VS = """
attribute vec3 aPos; attribute vec4 aNrm; attribute vec4 aTan; attribute vec2 aUv; attribute vec4 aJ; attribute vec4 aW;
uniform mat4 uMVP; uniform vec3 uScale; uniform float uSpin; uniform float uSkin; uniform vec4 uB[72];
varying vec3 vP; varying vec3 vN; varying vec4 vT; varying vec2 vUv;
vec3 xf(int i, vec4 p) { return vec3(dot(uB[i * 3], p), dot(uB[i * 3 + 1], p), dot(uB[i * 3 + 2], p)); }
void main() {
  vec3 p = aPos * uScale; vec3 n = aNrm.xyz; vec3 t = aTan.xyz;
  if (uSkin > 0.5) {
    vec4 P = vec4(p, 1.0); vec4 N = vec4(n, 0.0); vec4 T = vec4(t, 0.0);
    int j0 = int(aJ.x); int j1 = int(aJ.y); int j2 = int(aJ.z); int j3 = int(aJ.w);
    p = aW.x * xf(j0, P) + aW.y * xf(j1, P) + aW.z * xf(j2, P) + aW.w * xf(j3, P);
    n = aW.x * xf(j0, N) + aW.y * xf(j1, N) + aW.z * xf(j2, N) + aW.w * xf(j3, N);
    t = aW.x * xf(j0, T) + aW.y * xf(j1, T) + aW.z * xf(j2, T) + aW.w * xf(j3, T);
  }
  float cs = cos(uSpin); float sn = sin(uSpin); mat2 sp = mat2(cs, -sn, sn, cs);
  p.xz = sp * p.xz; n.xz = sp * n.xz; t.xz = sp * t.xz;
  vP = p; vN = n; vT = vec4(t, aTan.w < 0.0 ? -1.0 : 1.0); vUv = aUv;
  gl_Position = uMVP * vec4(p, 1.0);
}"""

        private const val FS = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D uC; uniform sampler2D uNm; uniform sampler2D uMR;
uniform vec3 uCam; uniform vec3 uKeyDir; uniform vec3 uKeyCol; uniform vec3 uRimADir; uniform vec3 uRimACol;
uniform vec3 uRimVDir; uniform vec3 uRimVCol; uniform vec3 uGlintDir; uniform float uGlint; uniform float uEnv; uniform float uLight;
varying vec3 vP; varying vec3 vN; varying vec4 vT; varying vec2 vUv;
const float PI = 3.14159265;
// A studio around the statue, only for reflections: a big soft light overhead, two strips at the sides, a dark floor.
vec3 env(vec3 r, float rough) {
  float up = r.y;
  float w = rough * 0.6;
  float top = smoothstep(0.55 - w, 0.95, up);
  float sl = smoothstep(0.78 - w, 0.98, dot(r, normalize(vec3(-0.9, 0.25, 0.35))));
  float sr = smoothstep(0.78 - w, 0.98, dot(r, normalize(vec3(0.9, 0.2, -0.3))));
  float base = 0.06 + 0.12 * smoothstep(-0.3, 0.6, up);
  vec3 c = vec3(base) * vec3(0.9, 0.85, 0.8) + top * vec3(1.6, 1.5, 1.4) + sl * vec3(1.3, 1.1, 0.8) + sr * vec3(0.8, 0.7, 1.2);
  return c * mix(1.0, 0.55, rough);
}
vec3 light(vec3 N, vec3 V, vec3 L, vec3 col, vec3 diff, vec3 F0, float a2) {
  float NdL = max(dot(N, L), 0.0); if (NdL <= 0.0) return vec3(0.0);
  vec3 H = normalize(L + V);
  float NdH = max(dot(N, H), 0.0); float NdV = max(dot(N, V), 1e-3);
  float dd = NdH * NdH * (a2 - 1.0) + 1.0; float D = a2 / (PI * dd * dd);
  float k = (sqrt(a2) + 1.0); k = k * k / 8.0;
  float G = (NdV / (NdV * (1.0 - k) + k)) * (NdL / (NdL * (1.0 - k) + k));
  vec3 F = F0 + (1.0 - F0) * pow(1.0 - max(dot(H, V), 0.0), 5.0);
  vec3 spec = D * G * F / (4.0 * NdV * NdL + 1e-4);
  return (diff * (1.0 - F) / PI + spec) * col * NdL;
}
void main() {
  vec3 base = pow(texture2D(uC, vUv).rgb, vec3(2.2));
  vec3 mr = texture2D(uMR, vUv).rgb;
  float rough = clamp(mr.g, 0.06, 1.0); float metal = mr.b;
  vec3 Nv = normalize(vN); if (!gl_FrontFacing) Nv = -Nv;
  vec3 T = normalize(vT.xyz - Nv * dot(Nv, vT.xyz)); vec3 B = cross(Nv, T) * vT.w;
  vec3 nm = texture2D(uNm, vUv).xyz * 2.0 - 1.0;
  vec3 N = normalize(T * nm.x + B * nm.y + Nv * nm.z);
  vec3 V = normalize(uCam - vP);
  vec3 F0 = mix(vec3(0.04), base, metal); vec3 diff = base * (1.0 - metal);
  float a2 = rough * rough; a2 = a2 * a2;
  vec3 c = light(N, V, uKeyDir, uKeyCol, diff, F0, a2)
         + light(N, V, uRimADir, uRimACol, diff, F0, a2)
         + light(N, V, uRimVDir, uRimVCol, diff, F0, a2)
         + light(N, V, uGlintDir, vec3(uGlint), diff, F0, a2);
  float NdV = max(dot(N, V), 0.0);
  vec3 Fe = F0 + (max(vec3(1.0 - rough), F0) - F0) * pow(1.0 - NdV, 5.0);
  c += env(reflect(-V, N), rough) * Fe * uEnv + diff * env(N, 1.0) * 0.35 * uEnv;
  c *= 1.0;
  // ACES fit, then gamma
  c = clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
  gl_FragColor = vec4(pow(c, vec3(1.0 / 2.2)), 1.0);
}"""

        private const val CVS = "attribute vec2 aPos; varying vec2 vUv; void main(){ vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }"
        // uMode 0: the statue texture (FBO, y up); 1: the glyph bitmap (y down). Near-black pixels go to true black.
        private const val CFS = """precision mediump float; varying vec2 vUv; uniform sampler2D uTex; uniform float uMode; uniform float uAlpha;
void main(){ vec2 uv = uMode > 0.5 ? vec2(vUv.x, 1.0 - vUv.y) : vUv; vec4 c = texture2D(uTex, uv);
  float m = max(c.r, max(c.g, c.b)); float k = smoothstep(0.012, 0.03, m);
  // the statue stays opaque where it is dark (true black, not a hole); the glyphs fade out completely
  gl_FragColor = uMode > 0.5 ? c * k * uAlpha : vec4(c.rgb * k, c.a) * uAlpha; }"""
    }
}

/** A tiny SVG-path reader for the glyphs: M, L, C, Z (absolute) and O cx cy rx ry (an oval). */
internal fun svgPath(d: String): Path {
    val p = Path()
    val t = Regex("[MLCZO]|-?[0-9.]+").findAll(d).map { it.value }.toList()
    var i = 0; var cmd = 'M'
    fun n() = t[i++].toFloat()
    while (i < t.size) {
        val tok = t[i]
        if (tok[0].isLetter()) { cmd = tok[0]; i++; if (cmd == 'Z') { p.close(); continue } }
        when (cmd) {
            'M' -> { p.moveTo(n(), n()); cmd = 'L' }
            'L' -> p.lineTo(n(), n())
            'C' -> p.cubicTo(n(), n(), n(), n(), n(), n())
            'O' -> { val cx = n(); val cy = n(); val rx = n(); val ry = n(); p.addOval(cx - rx, cy - ry, cx + rx, cy + ry, Path.Direction.CW) }
            else -> i++
        }
    }
    return p
}
