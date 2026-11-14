package app.pulsify.engine

import android.content.Context
import android.opengl.GLES20
import app.pulsify.core.AudioFrame
import app.pulsify.core.BuddyPoke
import app.pulsify.core.Mood
import app.pulsify.core.BuddySlot
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Where the widget creatures live and what is happening around them.
 * The live wallpaper feeds real widgets; the app's playground feeds pretend ones.
 */
abstract class BuddyFeed {
    /** Home-screen page under the screen (0 = first page; fractional mid-swipe). */
    @Volatile var pagePos = 0f
    /** False when the launcher doesn't report its pages (everything counts as one page). */
    @Volatile var pagesKnown = false
    /** Lock screen showing: widgets aren't there, so neither are the creatures. */
    @Volatile var hidden = false
    /** Bumped when the phone is unlocked (everyone says hello). */
    @Volatile var greetSeq = 0
    abstract fun slots(): List<BuddySlot>
    abstract fun pollPoke(): BuddyPoke?
    /** A tap told us where a widget is. */
    open fun placed(widgetId: Int, page: Int, l: Float, t: Float, r: Float, b: Float) {}
}

/** Framing of each creature inside its widget (k: virtual screen width per widget short side). */
private class Frame(val k: Float, val aspect: Float = 2f, val ground: Float = -0.6f, val feet: Float = Float.NaN, val head: Float = 1.15f, val tall: Float = 0f)

private val FRAMES = mapOf(
    1 to Frame(1.22f),                              // Mochi
    2 to Frame(1.12f, head = 1.0f),                 // Kalboob
    3 to Frame(1.2f),                               // Kitty
    4 to Frame(1.12f, feet = -0.30f, head = 1.2f),  // Kiwi sits on her branch
    5 to Frame(1.3f, aspect = 1.55f, ground = -0.62f, tall = 1.15f), // Glowy swims
    6 to Frame(1.2f),                               // Gromble
    7 to Frame(1.0f, head = 1.05f),                 // Nutty
    8 to Frame(1.0f, head = 1.15f),                 // Bolt
    9 to Frame(1.05f, head = 0.75f),                // Gloop gang
)

private val VOICE = mapOf(
    1 to listOf("mochi!", "eep!", "boing!"), 2 to listOf("woof!", "arf?!", "wuff"), 3 to listOf("mrrp?", "mew!", "hiss!"),
    4 to listOf("squawk!", "kiwi!", "chirp?"), 5 to listOf("blub!", "glub?", "bloop"), 6 to listOf("MWAHA… ow", "grr!", "hmph!"),
    7 to listOf("nuts?!", "chk-chk!", "eek!"), 8 to listOf("beep?!", "bzzt!", "boop"), 9 to listOf("blorp!", "squish", "bloop!"),
)

private val THROWS = mapOf(1 to Fx.OBJ_BALL, 2 to Fx.OBJ_BONE, 3 to Fx.OBJ_YARN, 4 to Fx.OBJ_SEED, 5 to Fx.OBJ_BUBBLE,
    6 to Fx.OBJ_ROCK, 7 to Fx.OBJ_ACORN, 8 to Fx.OBJ_GEAR, 9 to Fx.OBJ_BLOB)

fun makeCreature(id: Int, context: Context): Creature? = when (id) {
    1 -> MochiScene(context); 2 -> KalboobScene(context); 3 -> KittyScene(context); 4 -> KiwiScene(context)
    5 -> GlowyScene(context); 6 -> GrombleScene(context); 7 -> NuttyScene(context); 8 -> BoltScene(context)
    9 -> GloopScene(context); else -> null
}

/** A short animation added on top of a buddy's pose. */
private class Move(val kind: Int, val start: Float, val dur: Float, val a: Float = 1f)

/** Everything a buddy's sprite does this frame (reset, then every motion adds to it). */
private class Pose {
    var sx = 1f; var sy = 1f; var rot = 0f; var offX = 0f; var offY = 0f; var skew = 0f
    var swirl = 0f; var wave = 0f; var sick = 0f; var blush = 0f; var flash = 0f; var alpha = 1f; var clip = false
    fun reset() { sx = 1f; sy = 1f; rot = 0f; offX = 0f; offY = 0f; skew = 0f; swirl = 0f; wave = 0f; sick = 0f; blush = 0f; flash = 0f; alpha = 1f; clip = false }
}

class Buddy internal constructor(val slot: BuddySlot, val creature: Creature?, internal val frame: Frame0) {
    internal var gl = 0             // 0 not started, 1 ok, -1 failed
    internal var fbo = 0; internal var tex = 0; internal var fw = 0; internal var fh = 0
    internal var vw = 0; internal var vh = 0; internal var winTop = 0; internal var winBot = 0; internal var feetV = 0f
    // Screen geometry (render px), for the buddy's own page.
    internal var cx = 0f; internal var feetY = 0f; internal var w = 0f; internal var h = 0f; internal var m = 0f
    var s = 0f; internal set              // nominal creature size in px
    // Page spring.
    internal var x = Float.NaN; internal var vx = 0f; internal var idealX = 0f
    internal val pose = Pose()
    internal val moves = ArrayList<Move>()
    internal var busyUntil = 0f
    internal var bag = ArrayList<Int>()
    internal val input = SceneInput()
    internal var renderTick = 0
    // Swipe bookkeeping.
    internal var wasHome = true; internal var travel = 0f; internal var maxV = 0f; internal var swipeDir = 0f
    internal var byeSaid = false
    internal var pokes = 0; internal var lastPoke = -9f
    internal var visible = false
    internal var lookX = 0f
    val name: String get() = creatureInfo(slot.creature)?.name ?: "Friend"

    // Where things are on screen right now (with the pose).
    val footX: Float get() = x + pose.offX
    val footY: Float get() = feetY + pose.offY
    val headX: Float get() = footX + sin(pose.rot) * s * frame.head * pose.sy
    val headY: Float get() = footY - s * frame.head * pose.sy * cos(pose.rot)
    val midX: Float get() = footX + sin(pose.rot) * s * 0.5f
    val midY: Float get() = footY - s * 0.5f * pose.sy
}

/** Public copy of the framing (the table above is private to this file). */
class Frame0(val k: Float, val aspect: Float, val ground: Float, val feet: Float, val head: Float, val tall: Float)

class BuddyLayer(private val context: Context) {
    var feed: BuddyFeed? = null
    private val buddies = LinkedHashMap<Int, Buddy>()
    private val fx = Fx(context)
    private var time = 0f
    private var rw = 0; private var rh = 0
    private var prog = 0; private var shadowProg = 0; private var vbo = 0
    private val u = HashMap<String, Int>()
    private val su = HashMap<String, Int>()
    private var glReady = false
    private var lastSlots: List<BuddySlot>? = null
    private var lastPage = 0f
    private var pageVel = 0f
    private var still = 0f
    private var greetSeen = -1
    private var hiddenFade = 0f
    private var nextPlay = 6f
    private var script: Script? = null
    private val mail = ArrayList<IntArray>()   // [fromId, toId]
    private val rnd = Random(System.nanoTime())

    /** Something is moving: the loop should run at full rate. */
    val busy: Boolean
        get() = buddies.values.any { it.visible } && (abs(pageVel) > 0.01f || fx.alive || script != null ||
            buddies.values.any { it.visible && (it.moves.isNotEmpty() || abs(it.vx) > 4f) })
    val active: Boolean get() = feed != null && buddies.isNotEmpty()

    // ------------------------------------------------------------------ frame

    fun frame(dt: Float, main: SceneInput, target: Int, width: Int, height: Int) {
        val f = feed ?: return
        if (width != rw || height != rh) { rw = width; rh = height; buddies.values.forEach { geometry(it) }; fx.size(width, height) }
        sync(f)
        if (buddies.isEmpty()) { fx.clearAll(); return }
        if (!glReady) initGl()
        time += dt
        fx.now = time
        hiddenFade = if (f.hidden) max(0f, hiddenFade - dt * 6f) else min(1f, hiddenFade + dt * 3f)
        if (f.greetSeq != greetSeen) {
            if (greetSeen >= 0) buddies.values.forEach { if (it.slot.page == homePage(f)) react(it, if (main.mood == Mood.NIGHT || main.mood == Mood.MORNING) R_WAKE else R_HELLO, delay = rnd.nextFloat() * 0.4f) }
            greetSeen = f.greetSeq
        }

        // Page motion.
        val page = if (f.pagesKnown) f.pagePos else 0f
        val pv = (page - lastPage) / max(dt, 1e-3f)
        pageVel = pageVel * 0.6f + pv * 0.4f
        lastPage = page
        val moving = abs(pageVel) > 0.08f
        still = if (moving) 0f else still + dt

        while (true) {
            val p = f.pollPoke() ?: break
            handlePoke(f, p, page)
        }

        for (b in buddies.values) stepBuddy(b, dt, page, moving, main)
        director(dt, main)
        script?.let { if (!it.step(time)) script = null }
        fx.step(dt, time)

        if (hiddenFade <= 0.001f) return
        // Render each visible buddy into its own little buffer, then place it on screen.
        for (b in buddies.values) if (b.visible) renderBuddy(b, dt, main)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, target)
        Gl.target = target
        GLES20.glViewport(0, 0, rw, rh)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        for (b in buddies.values) if (b.visible && b.gl == 1) drawShadow(b)
        for (b in buddies.values) if (b.visible && b.gl == 1) drawSprite(b)
        GLES20.glDisable(GLES20.GL_BLEND)
        fx.draw(hiddenFade)
    }

    private fun homePage(f: BuddyFeed): Int = if (f.pagesKnown) f.pagePos.roundToInt() else 0

    private fun sync(f: BuddyFeed) {
        val slots = f.slots()
        if (slots === lastSlots) return
        lastSlots = slots
        val live = slots.filter { it.placed }.take(app.pulsify.core.Buddies.MAX_LIVE)
        val ids = live.map { it.widgetId }.toSet()
        buddies.entries.removeAll { (id, b) ->
            val s = live.firstOrNull { it.widgetId == id }
            val gone = s == null || s.creature != b.slot.creature || s.coat != b.slot.coat
            if (gone) freeBuddy(b)
            gone
        }
        for (s in live) {
            val old = buddies[s.widgetId]
            if (old != null) {
                if (old.slot != s) {
                    // Same creature, new place: keep it alive and let it travel there.
                    val nb = Buddy(s, old.creature, old.frame).also { copyState(old, it) }
                    buddies[s.widgetId] = nb; geometry(nb)
                }
                continue
            }
            val fr = FRAMES[s.creature] ?: FRAMES[1]!!
            val b = Buddy(s, makeCreature(s.creature, context), Frame0(fr.k, fr.aspect, fr.ground, fr.feet, fr.head, fr.tall))
            b.creature?.listener = null
            buddies[s.widgetId] = b
            geometry(b)
        }
        if (ids.isEmpty()) script = null
    }

    private fun copyState(a: Buddy, b: Buddy) {
        b.gl = a.gl; b.fbo = a.fbo; b.tex = a.tex; b.fw = a.fw; b.fh = a.fh
        b.vw = a.vw; b.vh = a.vh; b.winTop = a.winTop; b.winBot = a.winBot; b.feetV = a.feetV
        b.x = a.x; b.vx = a.vx; b.bag = a.bag
    }

    private fun geometry(b: Buddy) {
        if (rw <= 0) return
        val s = b.slot
        val x0 = s.l * rw; val y0 = s.t * rh; val w = (s.r - s.l) * rw; val h = (s.b - s.t) * rh
        b.w = w; b.h = h; b.m = min(w, h)
        b.cx = x0 + w / 2f
        b.feetY = y0 + h - 0.07f * h
        b.s = 0.78f * b.m
        val fr = b.frame
        val vw = max(32, (b.m * fr.k).roundToInt())
        val vh = (vw * fr.aspect).roundToInt()
        val ground = fr.ground
        val feetUv = if (fr.feet.isNaN()) ground else fr.feet
        val feetV = vh / 2f - feetUv * vw
        val top: Int; val bot: Int
        if (fr.tall > 0f) {
            val hh = (vw * fr.tall).roundToInt()
            bot = min(vh, (feetV + 0.12f * vw).roundToInt()); top = max(0, bot - hh)
        } else {
            top = max(0, (feetV - (h + 0.55f * b.m)).roundToInt())
            bot = min(vh, (feetV + 0.12f * b.m).roundToInt())
        }
        val changed = vw != b.vw || vh != b.vh
        b.vw = vw; b.vh = vh; b.winTop = top; b.winBot = bot; b.feetV = feetV
        if (b.gl == 1 && (changed || b.fw != vw || b.fh != bot - top)) {
            makeTarget(b)
            if (changed) b.creature?.onSurfaceChanged(vw, vh)
        }
    }

    // ------------------------------------------------------------------ per buddy

    private fun stepBuddy(b: Buddy, dt: Float, page: Float, moving: Boolean, main: SceneInput) {
        val rel = b.slot.page - page
        b.idealX = b.cx + rel * rw
        if (b.x.isNaN() || abs(b.x - b.idealX) > rw * 2.5f) { b.x = b.idealX; b.vx = 0f }
        // A soft, slightly bouncy spring: the creature is carried by the page, lags, overshoots.
        var left = dt
        while (left > 0f) {
            val h = min(left, 1f / 120f); left -= h
            val ax = 230f * (b.idealX - b.x) - 17f * b.vx
            b.vx += ax * h; b.x += b.vx * h
        }
        val lagMax = 0.65f * b.w + 0.2f * b.m
        if (b.x - b.idealX > lagMax) { b.x = b.idealX + lagMax; b.vx = min(b.vx, 0f) }
        if (b.idealX - b.x > lagMax) { b.x = b.idealX - lagMax; b.vx = max(b.vx, 0f) }

        val margin = b.vw * 0.8f
        b.visible = hiddenFade > 0f && b.x + margin > 0f && b.x - margin < rw

        // Swipe reactions.
        val home = abs(rel) < 0.02f
        if (moving) {
            if (abs(rel) < 1.6f) { b.travel += abs(pageVel) * dt; b.maxV = max(b.maxV, abs(pageVel)); b.swipeDir = sign(pageVel) }
            if (b.wasHome && !b.byeSaid && abs(rel) in 0.08f..0.5f && rnd.nextFloat() < 0.4f && time > b.busyUntil) {
                b.byeSaid = true
                fx.bubble(b, listOf("bye!", "noo—", "wait!", "hey hey hey", "see ya!", "aaa!").random(rnd), Fx.SPEECH, 1.1f)
            }
        } else if (still > 0.16f) {
            if (b.travel > 0.35f && home) {
                if (time > b.busyUntil) arrive(b, main)
                b.travel = 0f; b.maxV = 0f
            } else if (!home) b.travel = 0f
            b.wasHome = home; b.byeSaid = false
        }

        // Pose: spring stretch, then every running move, then the music.
        val p = b.pose
        p.reset()
        val v = (b.x - b.idealX)
        p.offX = 0f
        p.skew = clampF(-b.vx / 2600f, -0.42f, 0.42f)
        val st = min(abs(b.vx) / 9000f, 0.22f)
        p.sx = 1f + st; p.sy = 1f - st * 0.8f
        p.rot = clampF(v / max(b.w, 1f) * 0.18f, -0.25f, 0.25f)
        val it = b.moves.iterator()
        while (it.hasNext()) {
            val mv = it.next()
            val uu = (time - mv.start) / mv.dur
            if (uu < 0f) continue
            if (uu >= 1f) { it.remove(); continue }
            applyMove(b, mv, uu)
        }
        if (main.audio.source != AudioFrame.Source.NONE) {
            val beat = main.audio.beat
            p.sy += beat * 0.07f; p.sx -= beat * 0.04f; p.offY -= beat * 0.05f * b.s
            p.rot += sin(time * 3.2f + b.slot.widgetId) * 0.05f * main.audio.level
            if (beat > 0.9f && rnd.nextFloat() < 0.18f && b.visible) fx.note(b)
        }
        // Lean a little toward whoever it is watching (for a while).
        if (time > b.busyUntil + 2.5f) b.lookX = 0f
        if (b.lookX != 0f) p.rot += clampF((b.lookX - b.footX) / rw, -1f, 1f) * 0.08f
    }

    private fun arrive(b: Buddy, main: SceneInput) {
        val fast = b.maxV > 3.2f || b.travel > 1.6f
        val night = main.mood == Mood.NIGHT
        val pool = ArrayList<Int>()
        if (b.wasHome) {
            pool += listOf(R_MISSED, R_ANNOYED, R_HELLO, R_SHY, R_DELIGHT)
        } else {
            pool += listOf(R_SURPRISED, R_WHEE, R_PEEK, R_HELLO, R_MELT, R_DELIGHT, R_BONK, R_SHY)
            if (fast) pool += listOf(R_DIZZY, R_DIZZY, R_SEASICK, R_BONK, R_WHEE)
        }
        if (night) pool += listOf(R_SLEEPY, R_SLEEPY)
        if (b.slot.creature == 9 || b.slot.creature == 1) pool += R_MELT
        // A shuffle bag per buddy: never the same reaction twice in a row.
        if (b.bag.isEmpty() || b.bag.none { it in pool }) b.bag = ArrayList(pool.distinct().shuffled(rnd))
        var pick = b.bag.firstOrNull { it in pool } ?: pool.random(rnd)
        if (fast && rnd.nextFloat() < 0.45f) pick = listOf(R_DIZZY, R_SEASICK, R_BONK).random(rnd)
        b.bag.remove(pick)
        react(b, pick, delay = rnd.nextFloat() * 0.15f)
        // Friends on the same page notice.
        buddies.values.filter { it !== b && it.visible && it.slot.page == b.slot.page && time > it.busyUntil }.forEach { o ->
            if (rnd.nextFloat() < 0.5f) { o.lookX = b.footX; add(o, M_WIGGLE, time + 0.5f, 0.6f, 0.08f) }
        }
    }

    // ------------------------------------------------------------------ reactions

    private fun react(b: Buddy, r: Int, delay: Float = 0f) {
        val t = time + delay
        val voice = VOICE[b.slot.creature] ?: listOf("!")
        val dur: Float
        when (r) {
            R_DIZZY -> {
                add(b, M_DIZZY, t, 2.6f); fx.stars(b, t, 2.5f)
                if (rnd.nextBoolean()) fx.bubble(b, listOf("whoa…", "@_@", "the room's spinning", "wobble").random(rnd), Fx.THINK, 1.5f, t + 0.5f)
                add(b, M_SHAKE, t + 2.3f, 0.45f, 1f); dur = 2.8f
            }
            R_SURPRISED -> {
                add(b, M_SQUASH, t, 0.14f, 0.28f); add(b, M_HOP, t + 0.12f, 0.62f, 0.55f)
                fx.exclaim(b, t + 0.1f); fx.sweat(b, t + 0.55f)
                fx.bubble(b, voice.random(rnd), Fx.SHOUT, 1.1f, t + 0.25f); dur = 1.5f
            }
            R_WHEE -> {
                val d = if (b.swipeDir == 0f) 1f else b.swipeDir
                add(b, M_LEAN, t, 0.9f, d * 0.25f); fx.speed(b, t, 0.85f, d)
                fx.bubble(b, listOf("wheee!", "again!", "yippee!", "zoom!").random(rnd), Fx.SPEECH, 1.4f, t + 0.05f)
                add(b, M_HOP, t + 0.85f, 0.5f, 0.35f); add(b, M_HOP, t + 1.4f, 0.4f, 0.22f)
                fx.hearts(b, t + 1.0f, 3); dur = 1.9f
            }
            R_SEASICK -> {
                add(b, M_SICK, t, 2.6f); fx.sickBubbles(b, t, 2.4f)
                fx.bubble(b, listOf("urp…", "too fast…", "blegh", "i need a minute").random(rnd), Fx.THINK, 1.5f, t + 0.6f); dur = 2.7f
            }
            R_BONK -> {
                val d = if (b.swipeDir == 0f) 1f else -b.swipeDir
                add(b, M_BONK, t, 1.5f, d)
                fx.impact(b, t + 0.17f, d); fx.stars(b, t + 0.4f, 1.1f)
                fx.pop(b, "BONK", t + 0.17f, d); dur = 1.6f
            }
            R_PEEK -> {
                add(b, M_PEEK, t, 2.1f)
                fx.bubble(b, listOf("boo!", "peekaboo!", "found you!").random(rnd), Fx.SHOUT, 1.0f, t + 1.2f)
                fx.sparkles(b, t + 1.2f, 7); dur = 2.2f
            }
            R_ANNOYED -> {
                add(b, M_HOP, t, 0.28f, 0.12f); add(b, M_HOP, t + 0.32f, 0.28f, 0.12f)
                fx.anger(b, t, 1.7f)
                fx.bubble(b, listOf("hey!", "rude.", "i was napping", "where were you?!").random(rnd), Fx.SHOUT, 1.4f, t + 0.1f)
                add(b, M_SHAKE, t + 0.7f, 0.5f, 0.7f); dur = 1.8f
            }
            R_DELIGHT -> {
                add(b, M_FLIP, t, 0.8f, 1f); fx.sparkles(b, t + 0.3f, 9)
                fx.bubble(b, listOf("again!", "ta-da!", "yay!", "✦ hi ✦").random(rnd), Fx.SPEECH, 1.2f, t + 0.7f)
                add(b, M_HOP, t + 0.85f, 0.4f, 0.2f); dur = 1.6f
            }
            R_SLEEPY -> {
                add(b, M_STRETCH, t, 1.5f); fx.bubble(b, listOf("*yawn*", "5 more minutes…", "shh, sleeping").random(rnd), Fx.THINK, 1.6f, t + 0.2f)
                fx.zzz(b, t + 0.8f, 2.4f); dur = 2.6f
            }
            R_HELLO -> {
                add(b, M_WIGGLE, t, 1.2f, 0.16f); add(b, M_HOP, t + 0.05f, 0.4f, 0.18f)
                fx.bubble(b, listOf("hi!", "hello!", "oh, it's you!", "hiii").random(rnd), Fx.SPEECH, 1.4f, t + 0.1f)
                fx.hearts(b, t + 0.4f, 2); dur = 1.5f
            }
            R_MELT -> {
                add(b, M_MELT, t, 1.9f); fx.bubble(b, listOf("blorp", "melting…", "i'm a puddle now").random(rnd), Fx.THINK, 1.3f, t + 0.4f)
                fx.sparkles(b, t + 1.55f, 5); dur = 2.0f
            }
            R_SHY -> {
                add(b, M_BLUSH, t, 1.8f); add(b, M_LEAN, t, 1.4f, -0.12f)
                fx.bubble(b, listOf("oh!", "…hi", "you're looking at me", "eep").random(rnd), Fx.WHISPER, 1.5f, t + 0.15f)
                fx.hearts(b, t + 0.6f, 2); dur = 1.9f
            }
            R_MISSED -> {
                add(b, M_HOP, t, 0.5f, 0.42f); add(b, M_SQUASH, t + 0.5f, 0.2f, 0.2f)
                fx.bubble(b, listOf("missed you!", "you came back!", "again! again!", "that was fun").random(rnd), Fx.SPEECH, 1.5f, t + 0.1f)
                fx.hearts(b, t + 0.2f, 4); dur = 1.7f
            }
            R_WAKE -> {
                add(b, M_STRETCH, t, 1.4f); add(b, M_HOP, t + 1.3f, 0.45f, 0.3f)
                fx.bubble(b, listOf("you're back!", "good morning!", "*stretch*", "hi hi hi").random(rnd), Fx.SPEECH, 1.6f, t + 0.3f)
                fx.sparkles(b, t + 1.4f, 6); dur = 2.0f
            }
            R_GIGGLE -> {
                add(b, M_WIGGLE, t, 0.9f, 0.2f); add(b, M_SQUASH, t, 0.25f, 0.18f)
                fx.bubble(b, listOf("hehe", "that tickles!", "hihi", "ah-ha!").random(rnd), Fx.SPEECH, 1.1f, t + 0.05f); dur = 1.0f
            }
            R_LOVE -> {
                add(b, M_BLUSH, t, 1.3f); add(b, M_HOP, t, 0.45f, 0.25f)
                fx.hearts(b, t, 6); dur = 1.3f
            }
            R_BOING -> {
                add(b, M_SQUASH, t, 0.18f, 0.35f); add(b, M_HOP, t + 0.16f, 0.7f, 0.7f); add(b, M_SPIN, t + 0.2f, 0.6f, 1f)
                fx.pop(b, voice.random(rnd), t + 0.2f, 0f); dur = 1.0f
            }
            else -> dur = 0f
        }
        b.busyUntil = max(b.busyUntil, t + dur)
    }

    private fun add(b: Buddy, kind: Int, start: Float, dur: Float, a: Float = 1f) { b.moves += Move(kind, start, dur, a) }

    private fun applyMove(b: Buddy, mv: Move, uu: Float) {
        val p = b.pose; val s = b.s; val a = mv.a
        when (mv.kind) {
            M_HOP -> {
                val pre = 0.17f; val post = 0.17f
                when {
                    uu < pre -> { val q = sin(uu / pre * PI.toFloat()) * 0.17f; p.sy -= q; p.sx += q * 0.7f }
                    uu < 1f - post -> {
                        val v = (uu - pre) / (1f - pre - post)
                        p.offY -= a * s * 4f * v * (1f - v)
                        val st = (1f - abs(2f * v - 1f)) * 0.1f; p.sy += st; p.sx -= st * 0.6f
                    }
                    else -> { val q = sin((uu - 1f + post) / post * PI.toFloat()) * 0.2f; p.sy -= q; p.sx += q * 0.75f }
                }
            }
            M_SPIN -> { p.rot += a * 2f * PI.toFloat() * easeF(uu); p.offY -= 0.18f * s * sin(uu * PI.toFloat()) }
            M_FLIP -> { p.sx *= cos(2f * PI.toFloat() * a * easeF(uu)); p.offY -= 0.22f * s * sin(uu * PI.toFloat()) }
            M_SQUASH -> { val k = sin(uu * PI.toFloat()) * a; p.sy -= k; p.sx += k * 0.8f }
            M_WIGGLE -> p.rot += sin(uu * 6f * PI.toFloat()) * a * (1f - uu)
            M_SHAKE -> p.offX += sin(uu * 52f) * a * s * 0.07f * (1f - uu)
            M_LEAN -> { val e = sin(uu * PI.toFloat()); p.offX += -a * s * 1.2f * e; p.rot += a * e; p.skew += a * 0.6f * e }
            M_SLIDE -> { val e = easeF(min(1f, uu * 1.4f)); p.offX += a * e * (1f - easeF(max(0f, uu - 0.7f) / 0.3f)) }
            M_DIZZY -> {
                val k = (1f - uu)
                p.rot += sin(uu * 17f) * 0.21f * sqrt(k)
                p.offX += sin(uu * 8.5f) * 0.08f * s * k
                p.swirl += 2.1f * k * k
                p.sy -= 0.05f * sin(uu * 11f) * k
            }
            M_SICK -> {
                val pl = easeF(uu / 0.15f) * (1f - easeF((uu - 0.75f) / 0.25f))
                p.sick += pl * 0.85f; p.wave += pl
                p.rot += sin(uu * 9f) * 0.08f * pl; p.offY += sin(uu * 7f) * 0.03f * s * pl
            }
            M_BLUSH -> p.blush += sin(uu * PI.toFloat())
            M_FLASH -> p.flash += (1f - uu) * (1f - uu) * a
            M_STRETCH -> {
                val e = sin(uu * PI.toFloat()); p.sy += 0.16f * e; p.sx -= 0.08f * e
                p.rot += sin(uu * 3f * PI.toFloat()) * 0.05f
            }
            M_MELT -> {
                val mlt = when {
                    uu < 0.3f -> easeF(uu / 0.3f)
                    uu < 0.6f -> 1f + sin(uu * 30f) * 0.03f
                    else -> { val v = (uu - 0.6f) / 0.4f; (1f - v) * cos(v * 9f) * (1f - v) - 0.15f * sin(v * PI.toFloat()) }
                }
                p.sy -= 0.55f * mlt; p.sx += 0.6f * mlt
            }
            M_PEEK -> {
                val hid = when {
                    uu < 0.08f -> 1f
                    uu < 0.3f -> lerpF(1f, 0.62f, easeF((uu - 0.08f) / 0.22f))
                    uu < 0.55f -> 0.62f + sin(uu * 40f) * 0.012f
                    uu < 0.72f -> { val v = (uu - 0.55f) / 0.17f; lerpF(0.62f, -0.35f, 1f - (1f - v) * (1f - v)) }
                    uu < 0.88f -> { val v = (uu - 0.72f) / 0.16f; lerpF(-0.35f, 0f, v * v) }
                    else -> 0f
                }
                p.offY += hid * s * 1.3f; p.clip = true
                if (uu in 0.3f..0.55f) p.rot += sin(uu * 28f) * 0.06f
                if (uu > 0.88f) { val q = sin((uu - 0.88f) / 0.12f * PI.toFloat()) * 0.2f; p.sy -= q; p.sx += q * 0.7f }
            }
            M_BONK -> {
                val d = a
                when {
                    uu < 0.12f -> p.offX += d * s * 0.9f * easeF(uu / 0.12f)
                    uu < 0.4f -> { p.offX += d * s * 0.9f; val v = (uu - 0.12f) / 0.28f; p.sx -= 0.38f * sin(min(1f, v * 3f) * PI.toFloat() * 0.5f) * (1f - v * 0.6f); p.sy += 0.12f * (1f - v) }
                    else -> {
                        val v = (uu - 0.4f) / 0.6f
                        p.offX += d * s * 0.9f * (1f - easeF(v)); p.rot += -d * sin(v * 10f) * 0.18f * (1f - v)
                        p.offY -= 0.25f * s * sin(v * PI.toFloat())
                    }
                }
            }
            M_ARC -> {
                // Jump to a point a (px, horizontal) and stay there until the move ends.
                val e = easeF(min(1f, uu / 0.25f)); val back = easeF(max(0f, (uu - 0.75f) / 0.25f))
                p.offX += a * (e - back)
                p.offY -= s * 0.9f * (sin(min(1f, uu / 0.25f) * PI.toFloat()) + sin(max(0f, (uu - 0.75f) / 0.25f) * PI.toFloat()))
            }
            M_TREMBLE -> p.offX += sin(uu * 95f) * 0.018f * s * (1f - uu)
        }
    }

    // ------------------------------------------------------------------ taps

    private fun handlePoke(f: BuddyFeed, p: BuddyPoke, page: Float) {
        val slot = f.slots().firstOrNull { it.widgetId == p.widgetId } ?: return
        if (p.l >= 0f) {
            val pg = if (f.pagesKnown) page.roundToInt() else 0
            val moved = !slot.placed || slot.page != pg || abs(slot.l - p.l) > 0.01f || abs(slot.t - p.t) > 0.01f
            if (moved) {
                buddies[p.widgetId]?.let { old -> fx.poof(old.footX, old.footY - old.s * 0.5f, old.s) }
                f.placed(p.widgetId, pg, p.l, p.t, p.r, p.b)
                pendingHello = p.widgetId
                return
            }
        }
        val b = buddies[p.widgetId] ?: return
        poke(b)
    }

    private var pendingHello = -1

    /** Someone tapped the widget. */
    fun poke(b: Buddy) {
        fx.ripple(b.footX, b.footY - b.s * 0.45f, b.s)
        b.pokes = if (time - b.lastPoke < 1.4f) b.pokes + 1 else 1
        b.lastPoke = time
        b.creature?.let { c -> if (b.gl == 1) c.tapAt(0.5f, (b.feetV - b.s * 0.5f) / b.vh) }
        if (b.pokes >= 3) { b.moves.clear(); react(b, R_DIZZY); b.pokes = 0; return }
        if (time < b.busyUntil - 0.3f) { add(b, M_SQUASH, time, 0.2f, 0.18f); return }
        react(b, listOf(R_GIGGLE, R_LOVE, R_BOING, R_DELIGHT, R_SURPRISED, R_SHY).random(rnd))
        buddies.values.filter { it !== b && it.visible && time > it.busyUntil }.forEach { o ->
            o.lookX = b.footX
            if (rnd.nextFloat() < 0.45f) fx.bubble(o, listOf("?", "me too!", "hehe", "hey, me!", "!!").random(rnd), Fx.SPEECH, 1.0f, time + 0.4f)
        }
    }

    // ------------------------------------------------------------------ friends playing

    private fun director(dt: Float, main: SceneInput) {
        // Say hello after being placed (or found) with a tap.
        if (pendingHello >= 0) buddies[pendingHello]?.let { b ->
            if (b.visible) { fx.poof(b.footX, b.footY - b.s * 0.5f, b.s); react(b, R_HELLO, 0.15f); pendingHello = -1 }
        }
        if (script != null || abs(pageVel) > 0.05f || still < 1.2f) return
        nextPlay -= dt
        val here = buddies.values.filter { it.visible && abs(it.x - it.idealX) < it.w * 0.3f && it.footX in 0f..rw.toFloat() }
        // Letters to a friend on another page arrive when you get there.
        mail.firstOrNull { m -> here.any { it.slot.widgetId == m[1] } }?.let { m ->
            mail.remove(m)
            val to = buddies[m[1]]; val from = buddies[m[0]]
            if (to != null) {
                val fromLeft = from != null && from.slot.page < to.slot.page
                val sx = if (fromLeft) -40f else rw + 40f
                fx.plane(sx, to.headY - to.s * 1.2f, to, time, 1.6f) {
                    add(to, M_SQUASH, time, 0.2f, 0.2f)
                    fx.bubble(to, listOf("a letter!", "from ${from?.name ?: "a friend"}!", "♥ mail ♥").random(rnd), Fx.SPEECH, 1.6f, time + 0.1f)
                    fx.hearts(to, time + 0.3f, 4); to.busyUntil = time + 1.8f
                }
                nextPlay = 3f
            }
            return
        }
        if (nextPlay > 0f) return
        nextPlay = 5f + rnd.nextFloat() * 5f
        val free = here.filter { time > it.busyUntil + 0.3f }
        if (free.size >= 2) {
            val a = free.random(rnd)
            val b = free.filter { it !== a }.minByOrNull { abs(it.footX - a.footX) + rnd.nextFloat() * 120f }!!
            val music = main.audio.source != AudioFrame.Source.NONE
            val pool = ArrayList(listOf(P_CATCH, P_CATCH, P_CHAT, P_FIVE, P_HEARTS, P_PLANE, P_COPY, P_SWAP))
            if (main.mood == Mood.NIGHT) pool += listOf(P_YAWN, P_YAWN)
            if (free.size >= 3) pool += P_WAVE
            if (music) pool += listOf(P_DANCE, P_DANCE)
            if (a.slot.creature == 8 || b.slot.creature == 8) pool += P_ZAP
            play(pool.random(rnd), a, b, free)
        } else if (here.size == 1 && free.size == 1) {
            val a = free[0]
            val far = buddies.values.filter { it.slot.page != a.slot.page }
            if (far.isNotEmpty() && rnd.nextFloat() < 0.5f && mail.size < 3) {
                val to = far.random(rnd)
                val dir = if (to.slot.page > a.slot.page) 1f else -1f
                add(a, M_HOP, time, 0.45f, 0.25f)
                fx.bubble(a, listOf("sending ${to.name} a note", "psst, ${to.name}!", "mail time").random(rnd), Fx.WHISPER, 1.5f, time)
                fx.plane(a.headX, a.headY, null, time + 0.4f, 1.3f, exitDir = dir) {}
                mail += intArrayOf(a.slot.widgetId, to.slot.widgetId)
                a.busyUntil = time + 1.8f
            } else {
                react(a, listOf(R_GIGGLE, R_DELIGHT, R_SHY, R_MELT, R_SLEEPY).random(rnd))
            }
        }
    }

    private fun play(kind: Int, a: Buddy, b: Buddy, all: List<Buddy>) {
        val t0 = time
        a.lookX = b.footX; b.lookX = a.footX
        val sc = Script()
        when (kind) {
            P_CATCH -> {
                var thrower = a; var catcher = b
                val volleys = 2 + rnd.nextInt(3)
                var t = 0.2f
                repeat(volleys) { i ->
                    val th = thrower; val ca = catcher
                    val last = i == volleys - 1
                    val miss = last && rnd.nextFloat() < 0.35f
                    sc.at(t0 + t) {
                        add(th, M_SQUASH, time, 0.18f, 0.2f); add(th, M_LEAN, time, 0.5f, if (ca.footX > th.footX) -0.1f else 0.1f)
                        fx.toss(THROWS[th.slot.creature] ?: Fx.OBJ_BALL, th, ca, time + 0.12f, 0.85f, miss) {
                            if (miss) {
                                add(ca, M_SQUASH, time, 0.25f, 0.3f); fx.stars(ca, time, 1.2f)
                                fx.bubble(ca, listOf("ow!", "my head!", "hey!!").random(rnd), Fx.SHOUT, 1.1f, time + 0.15f)
                                fx.bubble(th, listOf("oops", "sorry!!", "hehe…").random(rnd), Fx.WHISPER, 1.1f, time + 0.6f)
                            } else {
                                add(ca, M_SQUASH, time, 0.2f, 0.22f); fx.sparkles(ca, time, 5)
                                if (rnd.nextFloat() < 0.35f) fx.bubble(ca, listOf("got it!", "nice!", "mine!").random(rnd), Fx.SPEECH, 0.9f, time + 0.1f)
                            }
                        }
                    }
                    t += 1.25f
                    thrower = ca; catcher = th
                }
                sc.end = t0 + t + 1.2f
            }
            P_CHAT -> {
                val open = listOf("psst", "hey!", "guess what", "hi!", "did you see that?", "i'm hungry")
                val reply = listOf("what?", "?", "hmm", "!!", "hehe", "nope", "ok!", "tell me!", "same", "♥")
                val end = listOf("hehe", "lol", "♥", "deal!", "ok bye", "…")
                var t = 0.1f
                val lines = listOf(a to open.random(rnd), b to reply.random(rnd), a to reply.random(rnd), b to end.random(rnd))
                lines.take(3 + rnd.nextInt(2)).forEach { (who, txt) ->
                    sc.at(t0 + t) { fx.bubble(who, txt, Fx.SPEECH, 1.15f, time); add(who, M_WIGGLE, time, 0.6f, 0.07f); add(who, M_SQUASH, time, 0.16f, 0.08f) }
                    t += 1.0f
                }
                sc.at(t0 + t) { add(a, M_HOP, time, 0.4f, 0.2f); add(b, M_HOP, time + 0.1f, 0.4f, 0.2f) }
                sc.end = t0 + t + 0.6f
            }
            P_FIVE -> {
                val d = b.footX - a.footX
                val reach = clampF(abs(d) * 0.32f, 0f, a.s * 0.9f) * sign(d)
                add(a, M_SLIDE, t0, 0.95f, reach); add(b, M_SLIDE, t0, 0.95f, -reach)
                add(a, M_HOP, t0 + 0.05f, 0.45f, 0.25f); add(b, M_HOP, t0 + 0.05f, 0.45f, 0.25f)
                sc.at(t0 + 0.42f) {
                    val mx = (a.headX + b.headX) / 2f; val my = (a.headY + b.headY) / 2f + a.s * 0.3f
                    fx.burst(mx, my, a.s, 0xFFFFD27A.toInt()); fx.popAt("five!", mx, my - a.s * 0.4f, a.s)
                    add(a, M_FLASH, time, 0.3f, 0.5f); add(b, M_FLASH, time, 0.3f, 0.5f)
                }
                sc.at(t0 + 1.1f) { fx.hearts(a, time, 2); fx.hearts(b, time, 2) }
                sc.end = t0 + 1.8f
            }
            P_HEARTS -> {
                add(a, M_BLUSH, t0, 2.2f); add(a, M_LEAN, t0, 1.6f, if (b.footX > a.footX) -0.08f else 0.08f)
                fx.bubble(a, listOf("i like you", "♥", "bestie!", "you're my favourite").random(rnd), Fx.WHISPER, 1.6f, t0)
                fx.heartStream(a, b, t0 + 0.3f, 5)
                sc.at(t0 + 1.7f) { add(b, M_BLUSH, time, 1.6f); add(b, M_FLIP, time, 0.8f, 1f); fx.bubble(b, listOf("♥!", "aww", "me too!").random(rnd), Fx.SPEECH, 1.3f, time + 0.2f) }
                sc.end = t0 + 3.2f
            }
            P_PLANE -> {
                add(a, M_SQUASH, t0, 0.2f, 0.18f)
                fx.bubble(a, listOf("incoming!", "catch!", "air mail!").random(rnd), Fx.SPEECH, 1.0f, t0)
                fx.plane(a.headX, a.headY, b, t0 + 0.3f, 1.7f) {
                    add(b, M_HOP, time, 0.45f, 0.3f); fx.bubble(b, listOf("!!", "a note!", "♥").random(rnd), Fx.SPEECH, 1.2f, time + 0.1f)
                    fx.sparkles(b, time, 5)
                }
                sc.end = t0 + 3.4f
            }
            P_COPY -> {
                val mv = listOf(M_FLIP, M_SPIN, M_MELT).random(rnd)
                val d = when (mv) { M_MELT -> 1.6f; else -> 0.8f }
                add(a, mv, t0, d, 1f)
                sc.at(t0 + d + 0.2f) { fx.bubble(b, listOf("me too!", "watch this", "copycat!").random(rnd), Fx.SPEECH, 1.0f, time); add(b, mv, time + 0.3f, d, 1f) }
                sc.at(t0 + 2f * d + 0.7f) { fx.bubble(a, listOf("copycat!", "hehe", "show-off").random(rnd), Fx.WHISPER, 1.0f, time) }
                sc.end = t0 + 2f * d + 1.6f
            }
            P_SWAP -> {
                val d = b.footX - a.footX
                fx.bubble(a, listOf("swap!", "trade places?", "switcheroo").random(rnd), Fx.SPEECH, 1.0f, t0)
                add(a, M_ARC, t0 + 0.5f, 3.4f, d); add(b, M_ARC, t0 + 0.5f, 3.4f, -d)
                sc.at(t0 + 1.5f) { fx.bubble(b, listOf("?", "this feels wrong", "hmm").random(rnd), Fx.THINK, 1.2f, time) }
                sc.at(t0 + 2.6f) { fx.bubble(a, listOf("nope", "going home", "ok back!").random(rnd), Fx.SPEECH, 0.9f, time) }
                sc.end = t0 + 4.2f
            }
            P_YAWN -> {
                var t = 0f
                all.shuffled(rnd).forEach { who ->
                    sc.at(t0 + t) { add(who, M_STRETCH, time, 1.4f); fx.bubble(who, "*yawn*", Fx.THINK, 1.3f, time + 0.2f) }
                    t += 1.0f
                }
                sc.at(t0 + t + 0.4f) { all.forEach { fx.zzz(it, time, 2.5f) } }
                sc.end = t0 + t + 3f
            }
            P_WAVE -> {
                val row = all.sortedBy { it.footX }
                row.forEachIndexed { i, who -> sc.at(t0 + i * 0.22f) { add(who, M_HOP, time, 0.5f, 0.35f) } }
                row.forEachIndexed { i, who -> sc.at(t0 + 1.4f + i * 0.22f) { add(who, M_HOP, time, 0.5f, 0.35f) } }
                sc.at(t0 + 0.1f) { fx.bubble(row.first(), "wave!", Fx.SHOUT, 1.0f, time) }
                sc.end = t0 + 1.6f + row.size * 0.22f + 0.8f
            }
            P_DANCE -> {
                fx.bubble(a, listOf("this is my song!", "dance!", "♪♪♪").random(rnd), Fx.SPEECH, 1.2f, t0)
                for (i in 0 until 6) sc.at(t0 + 0.3f + i * 0.5f) {
                    all.forEach { who -> add(who, if (i % 2 == 0) M_HOP else M_WIGGLE, time, 0.45f, if (i % 2 == 0) 0.22f else 0.12f); if (i % 2 == 0) fx.note(who) }
                }
                sc.at(t0 + 2.8f) { all.forEach { add(it, M_FLIP, time, 0.7f, 1f) } }
                sc.end = t0 + 3.8f
            }
            P_ZAP -> {
                val bolt = if (a.slot.creature == 8) a else b; val other = if (bolt === a) b else a
                fx.bubble(bolt, listOf("scanning…", "beep boop", "target found").random(rnd), Fx.SPEECH, 1.2f, t0)
                fx.beam(bolt, other, t0 + 0.4f, 1.0f)
                sc.at(t0 + 0.6f) { add(other, M_TREMBLE, time, 0.8f); fx.exclaim(other, time); add(other, M_HOP, time + 0.5f, 0.5f, 0.4f) }
                sc.at(t0 + 1.6f) { fx.bubble(other, listOf("hey!", "that tickles", "rude!").random(rnd), Fx.SHOUT, 1.1f, time) }
                sc.at(t0 + 2.3f) { fx.bubble(bolt, listOf("hehe", "100% friend", "scan complete ♥").random(rnd), Fx.SPEECH, 1.1f, time) }
                sc.end = t0 + 3.4f
            }
        }
        val end = sc.end
        a.busyUntil = max(a.busyUntil, end); b.busyUntil = max(b.busyUntil, end)
        if (kind == P_YAWN || kind == P_WAVE || kind == P_DANCE) all.forEach { it.busyUntil = max(it.busyUntil, end) }
        script = sc
    }

    private class Script {
        private val steps = ArrayList<Pair<Float, () -> Unit>>()
        var end = 0f
        fun at(t: Float, block: () -> Unit) { steps += t to block }
        fun step(now: Float): Boolean {
            val due = steps.filter { it.first <= now }
            steps.removeAll(due)
            due.forEach { it.second() }
            return now < end || steps.isNotEmpty()
        }
    }

    // ------------------------------------------------------------------ GL

    private fun initGl() {
        val vs = """
attribute vec2 aPos;
uniform vec4 uWin; uniform vec2 uFeet; uniform vec2 uScale; uniform float uRot; uniform float uLift;
uniform vec2 uOff; uniform float uSkew; uniform vec2 uRes;
varying vec2 vUv; varying float vY;
void main(){
  vUv = vec2(aPos.x, 1.0 - aPos.y);
  vec2 p = uWin.xy + aPos * uWin.zw - uFeet;
  p.x += -p.y * uSkew;
  p *= uScale;
  p.y += uLift;
  float c = cos(uRot), s = sin(uRot);
  p = vec2(c * p.x - s * p.y, s * p.x + c * p.y);
  p.y -= uLift;
  p += uFeet + uOff;
  vY = p.y;
  gl_Position = vec4(p.x / uRes.x * 2.0 - 1.0, 1.0 - p.y / uRes.y * 2.0, 0.0, 1.0);
}
"""
        val fs = """
precision mediump float;
varying vec2 vUv; varying float vY;
uniform sampler2D uTex; uniform vec2 uPivot; uniform float uAsp;
uniform float uSwirl, uWave, uTime, uAlpha, uClipY, uSick, uBlush, uFlash;
void main(){
  vec2 uv = vUv;
  vec2 d = uv - uPivot; d.y *= uAsp;
  float r = length(d);
  float a = uSwirl * exp(-r * r * 9.0);
  float c = cos(a), s = sin(a);
  d = vec2(c * d.x - s * d.y, s * d.x + c * d.y);
  d.y /= uAsp;
  uv = uPivot + d;
  uv.x += sin(uv.y * 22.0 + uTime * 7.0) * uWave * 0.012;
  vec4 col = texture2D(uTex, uv);
  float lum = dot(col.rgb, vec3(0.3, 0.59, 0.11));
  col.rgb = mix(col.rgb, vec3(lum * 0.62, lum * 1.05 + 0.07 * col.a, lum * 0.5), uSick);
  col.rgb += vec3(1.0, 0.42, 0.58) * uBlush * col.a * 0.28;
  col.rgb += vec3(uFlash) * col.a;
  if (vY > uClipY) col *= 0.0;
  gl_FragColor = col * uAlpha;
}
"""
        prog = Gl.program(vs, fs)
        for (n in listOf("uWin", "uFeet", "uScale", "uRot", "uLift", "uOff", "uSkew", "uRes", "uTex", "uPivot", "uAsp",
            "uSwirl", "uWave", "uTime", "uAlpha", "uClipY", "uSick", "uBlush", "uFlash")) u[n] = GLES20.glGetUniformLocation(prog, n)
        val svs = """
attribute vec2 aPos; uniform vec4 uRect; uniform vec2 uRes; varying vec2 vP;
void main(){ vP = aPos * 2.0 - 1.0; vec2 p = uRect.xy + aPos * uRect.zw; gl_Position = vec4(p.x / uRes.x * 2.0 - 1.0, 1.0 - p.y / uRes.y * 2.0, 0.0, 1.0); }
"""
        val sfs = """
precision mediump float; varying vec2 vP; uniform float uA;
void main(){ float r = dot(vP, vP); float k = exp(-r * 3.2) * (1.0 - smoothstep(0.75, 1.0, r)); gl_FragColor = vec4(0.0, 0.0, 0.0, k * uA); }
"""
        shadowProg = Gl.program(svs, sfs)
        for (n in listOf("uRect", "uRes", "uA")) su[n] = GLES20.glGetUniformLocation(shadowProg, n)
        val ids = IntArray(1); GLES20.glGenBuffers(1, ids, 0); vbo = ids[0]
        val q = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        val fb = ByteBuffer.allocateDirect(q.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(q); fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, q.size * 4, fb, GLES20.GL_STATIC_DRAW)
        fx.init()
        glReady = true
    }

    private fun makeTarget(b: Buddy) {
        freeTarget(b)
        val w = max(1, b.vw); val h = max(1, b.winBot - b.winTop)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0); b.tex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, b.tex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glGenFramebuffers(1, ids, 0); b.fbo = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, b.fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, b.tex, 0)
        val ok = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target)
        if (!ok) { freeTarget(b); b.gl = -1; return }
        b.fw = w; b.fh = h
    }

    private fun freeTarget(b: Buddy) {
        if (b.fbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(b.fbo), 0)
        if (b.tex != 0) GLES20.glDeleteTextures(1, intArrayOf(b.tex), 0)
        b.fbo = 0; b.tex = 0; b.fw = 0; b.fh = 0
    }

    private fun renderBuddy(b: Buddy, dt: Float, main: SceneInput) {
        val c = b.creature ?: return
        if (b.gl == -1) return
        val outer = Gl.target
        if (b.gl == 0) {
            b.gl = try {
                c.onSurfaceCreated(); c.onSurfaceChanged(b.vw, b.vh); 1
            } catch (t: Throwable) {
                app.pulsify.core.CrashLog.note("buddy ${b.slot.creature} failed: ${t.message}"); -1
            }
            if (b.gl == 1) makeTarget(b)
            if (b.gl != 1) return
        }
        // Copy the world's state (time, music, battery), with the widget's own coat.
        val i = b.input
        i.hour = main.hour; i.mood = main.mood; i.charging = main.charging; i.battery = main.battery; i.lowBattery = main.lowBattery
        i.accent = main.accent; i.brightness = main.brightness; i.kiwiWords = main.kiwiWords
        i.character = b.slot.creature; i.coat = b.slot.coat; i.world = -1; i.video = -1; i.parallax = 0f
        val a = main.audio; val ia = i.audio
        ia.bass = a.bass; ia.mid = a.mid; ia.treble = a.treble; ia.level = a.level; ia.beat = a.beat; ia.bpm = a.bpm; ia.active = a.active; ia.source = a.source
        c.setGround(b.frame.ground)
        c.update(dt, i)
        // Calm buddies repaint every other frame; anything lively repaints every frame.
        b.renderTick++
        val lively = b.moves.isNotEmpty() || c.busy || abs(b.vx) > 4f || a.source != AudioFrame.Source.NONE
        if (!lively && b.renderTick % 2 == 1 && b.fw > 0) return
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, b.fbo)
        Gl.target = b.fbo
        GLES20.glViewport(0, -(b.vh - b.winBot), b.vw, b.vh)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        try { c.draw(i) } catch (t: Throwable) { app.pulsify.core.CrashLog.note("buddy draw: ${t.message}"); b.gl = -1 }
        Gl.target = outer
    }

    private fun drawShadow(b: Buddy) {
        val lift = clampF(-b.pose.offY / b.s, 0f, 1.5f)
        val w = b.s * 0.75f * (1f - lift * 0.3f) * b.pose.sx; val h = b.s * 0.13f
        GLES20.glUseProgram(shadowProg)
        GLES20.glUniform4f(su["uRect"]!!, b.footX - w / 2f, b.feetY - h / 2f + b.s * 0.02f, w, h)
        GLES20.glUniform2f(su["uRes"]!!, rw.toFloat(), rh.toFloat())
        GLES20.glUniform1f(su["uA"]!!, 0.28f * (1f - lift * 0.5f) * b.pose.alpha * hiddenFade)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawSprite(b: Buddy) {
        if (b.fw <= 0) return
        val p = b.pose
        val wl = b.x - b.vw / 2f
        val wt = b.feetY - (b.feetV - b.winTop)
        GLES20.glUseProgram(prog)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, b.tex)
        GLES20.glUniform1i(u["uTex"]!!, 0)
        GLES20.glUniform4f(u["uWin"]!!, wl, wt, b.vw.toFloat(), (b.winBot - b.winTop).toFloat())
        GLES20.glUniform2f(u["uFeet"]!!, b.x, b.feetY)
        GLES20.glUniform2f(u["uScale"]!!, p.sx, p.sy)
        GLES20.glUniform1f(u["uRot"]!!, p.rot)
        GLES20.glUniform1f(u["uLift"]!!, b.s * 0.5f)
        GLES20.glUniform2f(u["uOff"]!!, p.offX, p.offY)
        GLES20.glUniform1f(u["uSkew"]!!, p.skew)
        GLES20.glUniform2f(u["uRes"]!!, rw.toFloat(), rh.toFloat())
        val fh = (b.winBot - b.winTop).toFloat()
        GLES20.glUniform2f(u["uPivot"]!!, 0.5f, (b.winBot - (b.feetV - b.s * 0.55f)) / fh)
        GLES20.glUniform1f(u["uAsp"]!!, fh / b.vw)
        GLES20.glUniform1f(u["uSwirl"]!!, p.swirl)
        GLES20.glUniform1f(u["uWave"]!!, p.wave)
        GLES20.glUniform1f(u["uTime"]!!, time)
        GLES20.glUniform1f(u["uAlpha"]!!, p.alpha * hiddenFade)
        GLES20.glUniform1f(u["uClipY"]!!, if (p.clip) b.feetY + b.s * 0.04f else 1e6f)
        GLES20.glUniform1f(u["uSick"]!!, p.sick)
        GLES20.glUniform1f(u["uBlush"]!!, p.blush)
        GLES20.glUniform1f(u["uFlash"]!!, p.flash)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun freeBuddy(b: Buddy) {
        if (b.gl == 1) try { b.creature?.release() } catch (_: Throwable) {}
        freeTarget(b)
        b.gl = 0
    }

    fun release() {
        buddies.values.forEach { freeBuddy(it) }
        buddies.clear(); lastSlots = null
        if (prog != 0) GLES20.glDeleteProgram(prog)
        if (shadowProg != 0) GLES20.glDeleteProgram(shadowProg)
        if (vbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
        prog = 0; shadowProg = 0; vbo = 0
        fx.release()
        glReady = false
    }

    companion object {
        const val R_DIZZY = 1; const val R_SURPRISED = 2; const val R_WHEE = 3; const val R_SEASICK = 4; const val R_BONK = 5
        const val R_PEEK = 6; const val R_ANNOYED = 7; const val R_DELIGHT = 8; const val R_SLEEPY = 9; const val R_HELLO = 10
        const val R_MELT = 11; const val R_SHY = 12; const val R_MISSED = 13; const val R_WAKE = 14; const val R_GIGGLE = 15
        const val R_LOVE = 16; const val R_BOING = 17

        private const val M_HOP = 1; private const val M_SPIN = 2; private const val M_FLIP = 3; private const val M_SQUASH = 4
        private const val M_WIGGLE = 5; private const val M_SHAKE = 6; private const val M_LEAN = 7; private const val M_DIZZY = 8
        private const val M_SICK = 9; private const val M_BLUSH = 10; private const val M_FLASH = 11; private const val M_STRETCH = 12
        private const val M_MELT = 13; private const val M_PEEK = 14; private const val M_BONK = 15; private const val M_ARC = 16
        private const val M_TREMBLE = 17; private const val M_SLIDE = 18

        private const val P_CATCH = 1; private const val P_CHAT = 2; private const val P_FIVE = 3; private const val P_HEARTS = 4
        private const val P_PLANE = 5; private const val P_COPY = 6; private const val P_SWAP = 7; private const val P_YAWN = 8
        private const val P_WAVE = 9; private const val P_DANCE = 10; private const val P_ZAP = 11
    }
}
