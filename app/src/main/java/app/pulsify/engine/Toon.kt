package app.pulsify.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix as GMatrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.opengl.GLES20
import android.opengl.GLUtils
import app.pulsify.core.AudioFrame
import app.pulsify.core.Mood
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * The 2D cast (Kalboob, Snowy, Sunny, Acorn): vector creatures drawn with Canvas, ported from the
 * "New 2D cast" browser preview. Creature space: origin on the floor under the creature, x right,
 * y DOWN, 1 unit = U px. Only a box around the creature is drawn and uploaded each frame, so a
 * creature costs a small texture, not a full-screen shader (the old birds and squirrel did).
 */

// ------------------------------------------------------------------ drawing toolkit
internal class ToonInk {
    lateinit var cv: Canvas
    /** Bitmap pixels per creature unit (text is drawn at real pixel sizes). */
    var ups = 100f
    private val paths = ArrayList<Path>()
    private var used = 0
    val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val rf = RectF()
    private val mx = GMatrix()
    private val shaders = HashMap<Long, Shader>()

    fun begin(c: Canvas) { cv = c; used = 0; if (shaders.size > 600) shaders.clear() }
    fun p(): Path { if (used == paths.size) paths.add(Path()); return paths[used++].also { it.rewind() } }

    fun ell(cx: Float, cy: Float, rx: Float, ry: Float, rot: Float = 0f, into: Path? = null): Path {
        val p = into ?: p()
        rf.set(cx - abs(rx), cy - abs(ry), cx + abs(rx), cy + abs(ry))
        if (rot == 0f) p.addOval(rf, Path.Direction.CW)
        else { val q = p(); q.addOval(rf, Path.Direction.CW); mx.setRotate(rot * 57.29578f, cx, cy); q.transform(mx); p.addPath(q) }
        return p
    }

    /** Closed smooth shape through points (Catmull-Rom as cubic Beziers). */
    fun blob(pts: FloatArray, t: Float = 1f): Path {
        val p = p(); val n = pts.size / 2
        p.moveTo(pts[0], pts[1])
        for (i in 0 until n) {
            val a = (i - 1 + n) % n; val c = (i + 1) % n; val d = (i + 2) % n
            val bx = pts[i * 2]; val by = pts[i * 2 + 1]; val cx = pts[c * 2]; val cy = pts[c * 2 + 1]
            p.cubicTo(bx + (cx - pts[a * 2]) / 6 * t, by + (cy - pts[a * 2 + 1]) / 6 * t,
                cx - (pts[d * 2] - bx) / 6 * t, cy - (pts[d * 2 + 1] - by) / 6 * t, cx, cy)
        }
        p.close(); return p
    }

    private val tmp = FloatArray(128)
    /** An ellipse with a soft fur edge (little tufts). */
    fun furry(cx: Float, cy: Float, rx: Float, ry: Float, n: Int, amp: Float, rot: Float = 0f, seed: Float = 1f): Path {
        val m = n * 2; val cr = cos(rot); val sr = sin(rot)
        val pts = if (m * 2 <= tmp.size) tmp.copyOf(m * 2) else FloatArray(m * 2)
        for (i in 0 until m) {
            val a = i.toFloat() / m * TAU; val k = if (i % 2 == 1) 1f else 1f - amp * (0.7f + 0.3f * sin(i * seed * 1.7f))
            val x = cos(a) * rx * k; val y = sin(a) * ry * k
            pts[i * 2] = cx + x * cr - y * sr; pts[i * 2 + 1] = cy + x * sr + y * cr
        }
        return blob(pts, 0.9f)
    }

    private fun key(vararg f: Float): Long { var h = 1125899906842597L; for (v in f) h = 31 * h + v.toRawBits(); return h }

    /** Fill with a soft 3D look: lit from the upper left, darker toward the lower right. */
    fun vol(path: Path, c: Int, cx: Float, cy: Float, r: Float, light: Float = .28f, dark: Float = -.28f, alpha: Float = 1f) {
        val k = key(c.toFloat(), cx, cy, r, light, dark, 1f)
        val sh = shaders.getOrPut(k) {
            RadialGradient(cx - r * .35f, cy - r * .45f, max(1e-3f, r * 1.25f), intArrayOf(shade(c, light), c or 0xFF000000.toInt(), shade(c, dark)), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        }
        fp.shader = sh; fp.color = Color.BLACK; fp.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        cv.drawPath(path, fp); fp.shader = null
    }
    fun linFill(path: Path, x0: Float, y0: Float, x1: Float, y1: Float, cs: IntArray, pos: FloatArray?, alpha: Float = 1f) {
        var h = key(x0, y0, x1, y1, 2f); for (c in cs) h = h * 31 + c
        val sh = shaders.getOrPut(h) { LinearGradient(x0, y0, x1, y1, cs, pos, Shader.TileMode.CLAMP) }
        fp.shader = sh; fp.color = Color.BLACK; fp.alpha = (alpha * 255).roundToInt().coerceIn(0, 255); cv.drawPath(path, fp); fp.shader = null
    }
    fun fill(path: Path, c: Int, a: Float = 1f) { fp.shader = null; fp.color = c or 0xFF000000.toInt(); fp.alpha = (a * 255).roundToInt().coerceIn(0, 255); cv.drawPath(path, fp) }
    fun line(path: Path, c: Int, w: Float, a: Float = 1f) { sp.shader = null; sp.color = c or 0xFF000000.toInt(); sp.alpha = (a * 255).roundToInt().coerceIn(0, 255); sp.strokeWidth = w; cv.drawPath(path, sp) }
    fun linLine(path: Path, w: Float, x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int) {
        val h = key(x0, y0, x1, y1, 3f) * 31 + c0 * 7 + c1
        sp.shader = shaders.getOrPut(h) { LinearGradient(x0, y0, x1, y1, c0 or 0xFF000000.toInt(), c1 or 0xFF000000.toInt(), Shader.TileMode.CLAMP) }
        sp.color = Color.BLACK; sp.alpha = 255; sp.strokeWidth = w; cv.drawPath(path, sp); sp.shader = null
    }

    /** A big glossy cartoon eye: centre, radius, look -1..1, open 0..1, iris colour, lid colour. */
    fun eye(x: Float, y: Float, r: Float, lx: Float, ly: Float, open: Float, iris: Int, lid: Int, happy: Float = 0f) {
        cv.save(); cv.translate(x, y)
        if (happy > .5f) {
            val p = p(); p.moveTo(-r * .8f, r * .15f); p.quadTo(0f, -r * .9f, r * .8f, r * .15f); line(p, 0x2A1A12, r * .32f)
            cv.restore(); return
        }
        val oh = max(.06f, open)
        cv.save(); val clip = p(); rf.set(-r, -r * oh, r, r * oh); clip.addOval(rf, Path.Direction.CW); cv.clipPath(clip)
        fill(ell(0f, 0f, r, r), 0x1A0F0A)
        val ix = lx * r * .28f; val iy = ly * r * .22f
        val k = key(iris.toFloat(), r, 9f)
        fp.shader = shaders.getOrPut(k) { RadialGradient(0f, r * .2f, r * .85f, intArrayOf(shade(iris, .35f), iris or 0xFF000000.toInt(), shade(iris, -.5f)), floatArrayOf(0f, .7f, 1f), Shader.TileMode.CLAMP) }
        fp.color = Color.BLACK; fp.alpha = 255
        cv.save(); cv.translate(ix, iy); cv.drawPath(ell(0f, 0f, r * .8f, r * .8f), fp); cv.restore(); fp.shader = null
        fill(ell(ix, iy, r * .46f, r * .46f), 0x0A0605)
        fill(ell(ix - r * .32f, iy - r * .34f, r * .26f, r * .22f), 0xFFFFFF, .95f)
        fill(ell(ix + r * .3f, iy + r * .3f, r * .11f, r * .11f), 0xFFFFFF, .8f)
        cv.restore()
        if (oh < .98f) { val p = p(); rf.set(-r * 1.04f, -r * oh, r * 1.04f, r * oh); p.addArc(rf, 180f, 180f); line(p, lid, r * .16f) }
        cv.restore()
    }

    fun shadow(x: Float, y: Float, rx: Float, a: Float) {
        if (a <= 0.01f) return
        val k = key(rx, 77f)
        fp.shader = shaders.getOrPut(k) { RadialGradient(0f, 0f, rx, intArrayOf(Color.argb(107, 0, 0, 0), Color.argb(0, 0, 0, 0)), null, Shader.TileMode.CLAMP) }
        fp.color = Color.BLACK; fp.alpha = (a * 255).roundToInt().coerceIn(0, 255)
        cv.save(); cv.translate(x, y); cv.scale(1f, .22f); cv.drawCircle(0f, 0f, rx, fp); cv.restore(); fp.shader = null
    }

    companion object {
        const val TAU = (PI * 2).toFloat()
        /** k > 0: toward white; k < 0: toward black. */
        fun shade(c: Int, k: Float): Int {
            fun f(v: Int) = (if (k > 0) v + (255 - v) * k else v * (1 + k)).roundToInt().coerceIn(0, 255)
            return Color.rgb(f(Color.red(c)), f(Color.green(c)), f(Color.blue(c)))
        }
    }
}

internal fun clampf(x: Float, a: Float, b: Float) = if (x < a) a else if (x > b) b else x
internal fun mixf(a: Float, b: Float, t: Float) = a + (b - a) * t
internal fun smf(x: Float): Float { val c = clampf(x, 0f, 1f); return c * c * (3 - 2 * c) }
internal fun winf(t: Float, a: Float, b: Float, c: Float, d: Float) = smf((t - a) / max(1e-4f, b - a)) * (1 - smf((t - c) / max(1e-4f, d - c)))

// ------------------------------------------------------------------ the rig: everything a pose sets each frame
internal class Rig {
    var t = 0f
    var lx = 0f; var ly = 0f; var blink = 1f; var happy = 0f; var sleep = false
    var headX = 0f; var headY = 0f; var headTilt = 0f; var mouth = 0f; var tongue = 0f
    var ear = 0f; var earR = 0f; var wag = 0f; var wagAmp = .4f; var pawLift = 0f; var step = 0f
    var wing = 0f; var wingLift = 0f; var flapPh = 0f; var tailSw = 0f; var tailFlick = 0f; var nibble = 0f; var acorn = true
    // painted puppets
    var yaw = 0f; var pitch = 0f; var back = 0f; var wingKick = 0f; var wingS = 0f; var tuftV = 0f; var smile = 0f; var lean = 0f; var puff = 0f
    var airMix = 0f; var walking = false; var walkPh = 0f; var face = 1f; var flapHere = false; var flying = false; var carried = false; var oneLeg = 0f; var tiltSnap = 0f
    fun base() { smile = 0f; puff = 0f; flapHere = false; oneLeg = 0f; headX = 0f; headY = 0f; headTilt = 0f; mouth = 0f; tongue = 0f; happy = 0f; ear = 0f; earR = 0f; wagAmp = .4f; pawLift = 0f; step = 0f
        wing = 0f; wingLift = 0f; tailSw = 0f; tailFlick = 0f; nibble = 0f; acorn = true; sleep = false }
}

/** One kind of 2D creature: how tall it is, what it does when idle, and how it is drawn. */
internal abstract class ToonKind(val height: Float, val idle: List<String>, val perch: Boolean = false) {
    abstract fun draw(ink: ToonInk, r: Rig, coat: Int)
    open fun chargeProp(ink: ToonInk, t: Float) {}
}

private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, val kind: Int, val max: Float) { var life = 0f }

/**
 * Shared body physics + behaviour for every 2D creature: looks at your touch (and otherwise at you),
 * carried by the finger, thrown (birds fly off and land), falls over on a hard landing and gets up,
 * walks for real, dances only while music plays, idle tricks at least three times a minute.
 */
internal class ToonScene(private val kind: ToonKind, private val ctx: android.content.Context? = null, private val coatOf: (Int) -> Int) : Creature {
    private val puppet = kind as? PuppetKind
    private val art = puppet?.let { PuppetArt(it.dir) }
    private val pgl = PuppetGl()
    private val isBird get() = kind.perch
    override var listener: SceneListener? = null
    override var bottomInset = 0f
        set(v) { field = v.coerceIn(0f, .8f); layout() }

    private val r = Rig()
    private val ink = ToonInk()
    private var w = 1; private var h = 1
    private var time = 0f

    // placement (screen px)
    private var U = 100f; private var floorY = 800f; private var cx0 = 500f
    private var ground = Float.NaN

    // body state (creature units, y up from the floor)
    private var x = 0f; private var y = 0f; private var vx = 0f; private var vy = 0f
    private var carried = false; private var flying = false; private var knocked = 0f; private var landHard = false
    private var tilt = 0f; private var tiltV = 0f; private var sy = 1f; private var syV = 0f
    private var yawV = 0f; private var pitchV = 0f; private var backV = 0f; private var leanV = 0f; private var leanT = 0f; private var wingT = 0f; private var wingSV = 0f; private var flyT = 0f; private var battery = 1f
    private var ltx = 0f; private var lty = 0f; private var lookHold = 0f; private var nextBlink = 2f
    private var act: String? = null; private var actT = 0f; private var nextAct = 5f
    private var face = 1f; private var walkTo = Float.NaN; private var walkBack = false; private var walkPh = 0f
    private var musicFor = 0f; private var grabDY = 0f; private var fvx = 0f; private var fvy = 0f
    private val taps = ArrayDeque<Float>()
    private var sinceTouch = 99f
    private var lastTapAt = -9f

    // touch
    private var down = false; private var onBodyDown = false; private var moved = false; private var downAt = 0f
    private var fx = 0f; private var fy = 0f; private var dnx = 0f; private var dny = 0f; private var lastT = 0f; private var holdT = 0f

    private val parts = ArrayList<Particle>()
    private var say = ""; private var sayT = 0f
    var words = ""

    override val busy: Boolean get() = true

    // ------------------------------------------------------------ layout
    override fun onSurfaceChanged(width: Int, height: Int) { w = max(1, width); h = max(1, height); layout() }
    override fun setGround(y: Float) { if (y == ground || (y.isNaN() && ground.isNaN())) return; ground = y; layout() }
    private fun layout() {
        val m = min(w, h).toFloat()
        U = m * 0.3f * (if (kind.perch) 1f else 1f)
        val halfH = 0.5f * h / m
        var gUv = if (ground.isNaN()) -halfH + 0.30f else ground
        if (h.toFloat() / w < 1.5f) { U = m * 0.2f; gUv = -halfH + 0.18f }
        if (bottomInset > 0f) gUv = max(gUv, -halfH + bottomInset * 2f * halfH + 0.05f)
        floorY = h * 0.5f - gUv * m
        cx0 = w * 0.5f
        perchDirty = true
    }
    private val halfW get() = (w * 0.5f / U) - 0.6f

    // ------------------------------------------------------------ touch
    private fun toX(nx: Float) = (nx * w - cx0) / U
    private fun toY(ny: Float) = (floorY - ny * h) / U
    private fun onBody(nx: Float, ny: Float): Boolean { val cx = toX(nx); val cy = toY(ny); return abs(cx - x) < .55f && cy > y - .1f && cy < y + kind.height + .15f }
    private fun lookAt(nx: Float, ny: Float) { ltx = clampf((toX(nx) - x) / 1.4f, -1f, 1f); lty = clampf(-(toY(ny) - (y + kind.height * .8f)) / 1.6f, -.7f, .7f); lookHold = 4f }

    override fun touchDown(nx: Float, ny: Float): Boolean {
        down = true; moved = false; downAt = time; fx = nx; fy = ny; dnx = nx; dny = ny; lastT = time; sinceTouch = 0f; holdT = 0f
        onBodyDown = onBody(nx, ny); lookAt(nx, ny)
        return onBodyDown
    }
    override fun touchMove(nx: Float, ny: Float): Boolean {
        if (!down) return false
        val dt = max(1e-3f, time - lastT)
        val vxs = (nx - fx) * w / U / dt; val vys = -(ny - fy) * h / U / dt
        fvx += (vxs - fvx) * .5f; fvy += (vys - fvy) * .5f
        fx = nx; fy = ny; lastT = time; sinceTouch = 0f
        if (hypot((nx - dnx) * w, (ny - dny) * h) > 0.03f * min(w, h)) moved = true
        if (onBodyDown && moved && !carried && knocked <= 0f) {
            carried = true; flying = false; act = null; walkTo = Float.NaN; grabDY = max(0f, toY(ny) - y - .4f); syV += 3f
            listener?.onDiscovery("carried")
        }
        if (!carried) lookAt(nx, ny)
        return onBodyDown
    }
    override fun touchUp(): Boolean {
        if (!down) return false
        down = false; sinceTouch = 0f
        if (carried) {
            carried = false
            val s = hypot(fvx, fvy); val k = if (s > 14f) 14f / s else 1f
            vx = fvx * k; vy = fvy * k; flying = true; tiltV += -vx * .8f
            if (s > 4f) listener?.onDiscovery("thrown")
            return true
        }
        val tap = !moved && time - downAt < .35f
        if (tap) { lastTapAt = time; tapped(onBodyDown) }
        return onBodyDown
    }
    private fun tapped(body: Boolean) {
        if (body) {
            if (knocked > 0f) { knocked = min(knocked, .15f); return }
            taps.addLast(time); while (taps.isNotEmpty() && time - taps.first() > 1.2f) taps.removeFirst()
            syV -= 5f; listener?.onBondXp(1)
            if (kind is SquirrelKind && taps.size >= 4) {   // repeated pokes knock Nutty over
                taps.clear(); knocked = 1.8f; tiltV += if (Random.nextBoolean()) 8f else -8f; puff(x, 1f, STAR, 4); listener?.onDiscovery("knocked_over")
            } else startAct("Giggle")
        } else { syV -= 2f; r.blink = 0f }
    }
    override fun tapAt(nx: Float, ny: Float): Boolean {
        lookAt(nx, ny); sinceTouch = 0f
        val body = onBody(nx, ny)
        if (time - lastTapAt > .25f) { lastTapAt = time; tapped(body) }
        return body
    }
    override fun longPressAt(nx: Float, ny: Float): Boolean { lookAt(nx, ny); sinceTouch = 0f; return if (onBody(nx, ny)) { startAct("Pet"); true } else false }
    override fun greet() { if (!carried && knocked <= 0f) startAct(if (isBird) "Squawk" else "Giggle") }
    override fun swiped(dir: Float, fast: Boolean) {
        if (carried || knocked > 0f) return
        if (isBird) { act = "Fly a lap"; actT = 0f; flying = true; flyT = 0f; vx = dir * 4.5f; vy = 6f; return }
        ltx = dir; lty = 0f; lookHold = 1.5f
        if (y <= 0f) { vy = if (fast) 3.6f else 2.4f; syV -= 4f }
        if (fast) startAct("Giggle")
    }

    override fun perform(trick: String): Boolean {
        sinceTouch = 0f
        if (trick.startsWith("say:")) { speak(trick.removePrefix("say:").take(40)); return true }
        val a = (if (isBird && trick == "fly") "Fly a lap" else TRICKS[trick]) ?: return false
        if (a == "Talk") { if (kind is ParrotArtKind) startAct("Talk") else { speak(pickWord()); startAct("Squawk") }; return true }
        startAct(a); return true
    }
    private fun pickWord(): String { val ws = words.split(',').map { it.trim() }.filter { it.isNotEmpty() }; return if (ws.isNotEmpty() && Random.nextFloat() < .7f) ws.random() else listOf("hello!", "pretty bird", "hehe", "I love you", "seed?").random() }
    private fun speak(t: String) { say = t; sayT = 2.4f }

    private fun startAct(a: String) {
        act = a; actT = 0f
        if (a == "Zoomies" || a == "Waddle") { walkTo = (if (Random.nextBoolean()) -1f else 1f) * (halfW - .1f); walkBack = false }
        if (a == "Side step") { walkTo = (if (Random.nextBoolean()) -1f else 1f) * .3f; walkBack = false }
    }
    private fun puff(px: Float, py: Float, k: Int, n: Int = 1) { repeat(n) { if (parts.size < 40) parts.add(Particle(px, py, (Random.nextFloat() - .5f) * .6f, .4f + Random.nextFloat() * .6f, k, 1f + Random.nextFloat() * .6f)) } }

    // ------------------------------------------------------------ update
    override fun update(dt: Float, input: SceneInput) {
        time += dt; sinceTouch += dt; r.t = time
        words = input.kiwiWords
        coat = coatOf(input.coat)
        charging = input.charging; battery = input.battery
        r.base()
        val a = input.audio; val musicOn = a.source != AudioFrame.Source.NONE
        musicFor = if (musicOn) musicFor + dt else 0f
        val dancing = musicOn && musicFor > 4f && !carried && !flying && knocked <= 0f
        // look at the touch, otherwise at you (small drift)
        lookHold -= dt
        if (lookHold <= 0f) { ltx = sin(time * .37f) * .25f; lty = sin(time * .29f) * .15f }
        if (musicOn && musicFor < 3f) { ltx = 0f; lty = -1f }
        r.lx += (ltx - r.lx) * min(1f, dt * 9f); r.ly += (lty - r.ly) * min(1f, dt * 9f)
        nextBlink -= dt; if (nextBlink <= 0f) { r.blink = 0f; nextBlink = 2f + Random.nextFloat() * 3.5f }; r.blink = min(1f, r.blink + dt * 8f)
        // ---- carried / thrown / knocked
        val hw = halfW; val top = floorY / U - kind.height - .2f
        if (carried) {
            val tx = (fx * w - cx0) / U; val ty = max(0f, toY(fy) - grabDY)
            val nx = x + (tx - x) * min(1f, dt * 18f); val ny = y + (ty - y) * min(1f, dt * 18f)
            vx = (nx - x) / max(dt, 1e-3f); vy = (ny - y) / max(dt, 1e-3f); x = nx; y = ny
            tiltV += (clampf(-fvx * .06f, -.7f, .7f) - tilt) * 40f * dt - tiltV * 6f * dt
        } else if (flying && isBird) {
            flyT += dt; val back = flyT > 1.4f; val near = abs(x) < .35f && abs(vx) < 1.6f
            val ty = if (back) (if (near) 0f else 1f) else max(1.3f, min(top, y))
            vx += ((if (back) (0f - x) * 3.2f else 0f) - vx * (if (back) 2.4f else .5f)) * dt
            vy += ((ty - y) * (if (back && near) 2.6f else 3.4f) - vy * (if (back && near) 2.6f else 2f)) * dt
            if (back && near && y < .6f) vy = max(vy, -1.1f)
            r.flapPh += dt * (if (back && near && y < .5f) 13f else 24f)
            x = clampf(x + vx * dt, -hw, hw); y = clampf(y + vy * dt, 0f, top)
            tiltV += (clampf(-vx * .08f, -.5f, .5f) - tilt) * 30f * dt - tiltV * 6f * dt
            if (back && y < .035f && abs(x) < .07f) { flying = false; flyT = 0f; x = 0f; y = 0f; syV -= 3.5f; vx = 0f; vy = 0f; r.wingKick = .8f }
        } else {
            if (flying) {
                if (x + vx * dt > hw || x + vx * dt < -hw) { vx *= -.6f; tiltV += vx * 3f; syV -= 3f }
                if (y > top && vy > 0f) vy *= -.4f
                tiltV += -vx * .5f * dt
            }
            if (y > 0f || vy > 0f) {
                vy -= 12f * dt; y += vy * dt; x += vx * dt
                if (y <= 0f) {
                    val imp = -vy; y = 0f
                    if (flying && imp > 5.5f && !landHard) {
                        landHard = true; knocked = 1.7f + Random.nextFloat() * .6f; tiltV += (if (vx > 0f) -1f else 1f) * 7f; vx *= .3f; vy = 0f
                        puff(x, 1f, STAR, 4); syV -= 6f; listener?.onDiscovery("knocked_over")
                    } else if (flying && imp > 2.5f) { vy = imp * .3f; vx *= .6f; syV -= 4f }
                    else { vy = 0f; if (flying) { flying = false; landHard = false; vx *= .3f }; syV -= min(6f, imp * .8f) }
                }
            } else if (!flying && knocked <= 0f) {
                if (!walkTo.isNaN()) {
                    val d = walkTo - x; val v = clampf(d * 3f, -2.6f, 2.6f) * (if (act == "Zoomies") 1.6f else if (act == "Side step") .32f else .7f)
                    x += v * dt; if (d != 0f) face = sign(d); walkPh += abs(v) * dt * 9f
                    if (abs(d) < .03f) { if (act == "Zoomies" || act == "Waddle" || act == "Side step") { if (walkBack) { walkTo = Float.NaN; walkBack = false; act = null } else { walkTo = 0f; walkBack = true } } else walkTo = Float.NaN }
                } else if (abs(x) > .02f) x += (0f - x) * min(1f, dt * .8f)
            }
        }
        if (!flying) flyT = 0f
        if (knocked > 0f) {
            flying = false; vx *= max(0f, 1f - dt * 6f); val side = if (tilt >= 0f) 1f else -1f; knocked -= dt
            if (kind is DuckArtKind) { tiltV += (-tilt * 90f - tiltV * 9f) * dt; if (knocked <= 0f) { landHard = false; vy = 2.4f; syV -= 3f; startAct("Shake") } }
            else if (knocked > 0f) tiltV += ((side * 1.45f - tilt) * 70f - tiltV * 10f) * dt
            else { landHard = false; vy = 3.2f; syV -= 3f; tiltV -= side * 4f; startAct("Shake") }
        } else if (!carried && !(flying && isBird) && act != "Upside down" && act != "Spin" && act != "Belly rub") tiltV += (-tilt * 90f - tiltV * 9f) * dt
        tilt = clampf(tilt + tiltV * dt, -1.7f, 1.7f)
        run { val tb = if (kind is DuckArtKind && knocked > 0f) 1f else 0f
            backV += ((tb - r.back) * (if (tb > 0f) 55f else 38f) - backV * (if (tb > 0f) 6f else 7f)) * dt; r.back = clampf(r.back + backV * dt, -.15f, 1.15f) }
        val breath = 1f + .015f * sin(time * 2.4f)
        syV += ((breath - sy) * 180f - syV * 9f) * dt; sy = clampf(sy + syV * dt, .75f, 1.25f)
        // ---- idle acts (at least 3 a minute); night nap
        actT += dt
        val night = input.mood == Mood.NIGHT && sinceTouch > 12f && !musicOn
        if (act == null && !carried && !flying && knocked <= 0f && !dancing && !charging && sinceTouch > 3f) {
            nextAct -= dt
            if (nextAct <= 0f) { startAct(if (night) "Nap" else kind.idle.random()); nextAct = TRICK_GAP_MIN + Random.nextFloat() * TRICK_GAP_RANGE }
        }
        if (down && onBodyDown && !carried && !moved) { holdT += dt; if (holdT > .6f && act != "Pet") startAct("Pet") }
        pose(dt, dancing, musicOn, a)
        if (!walkTo.isNaN()) { r.step = sin(walkPh); r.headY += abs(sin(walkPh)) * -.03f; if (kind is PupKind) r.wagAmp = 1f }
        // painted puppets: the head turns toward what it looks at, wings breathe and flick, lean / hang
        r.walking = !walkTo.isNaN(); r.walkPh = walkPh; r.face = face; r.flying = flying; r.carried = carried; r.tiltSnap = tilt
        if (puppet != null) {
            val ta = if (isBird && flying && flyT > 1.4f && abs(x) < .35f && y < .45f) clampf((y - .05f) / .4f, 0f, 1f) else if (flying || carried || y > .06f) 1f else 0f
            r.airMix += (ta - r.airMix) * min(1f, dt * (if (ta > 0f) 12f else 7f))
            wingT -= dt; if (wingT <= 0f) { r.wingKick = if (Random.nextBoolean()) .4f else .7f; wingT = 1.3f + Random.nextFloat() * 2.5f }
            r.wingKick *= exp(-dt * 5f)
            wingSV += ((-vx * .05f - r.wingS) * 40f - wingSV * 5f) * dt; r.wingS += wingSV * dt
            r.tuftV = r.tuftV * exp(-dt * 4f) - tiltV * .004f
            if (kind is ParrotArtKind && act == null) r.tailSw = sin(time * 1.1f) * .15f
            val wy = if (r.walking && kind is DuckArtKind) face * .55f else 0f
            val tyaw = clampf(r.lx * .55f + wy, -.6f, .6f); val tp = clampf(r.ly * .35f, -.35f, .35f)
            yawV += ((tyaw - r.yaw) * 60f - yawV * 11f) * dt; r.yaw += yawV * dt
            pitchV += ((tp - r.pitch) * 60f - pitchV * 11f) * dt; r.pitch += pitchV * dt
            val big = abs(leanT) > 1f
            leanV += ((leanT - r.lean) * (if (big) 22f else 45f) - leanV * (if (big) 6f else 8f)) * dt; r.lean += leanV * dt
            if (isBird) { r.wing = max(r.wing, if (carried || flying) 1f else 0f) } else if (carried || flying) r.wing = 1f
        }
        // particles
        val it = parts.iterator()
        while (it.hasNext()) { val p = it.next(); p.life += dt; p.x += p.vx * dt; p.y += p.vy * dt; if (p.kind == FEATHER) { p.vy -= .9f * dt; p.vx *= 1 - dt * 2f } else if (p.kind >= DIRT) p.vy -= 6f * dt; if (p.life > p.max) it.remove() }
        if (sayT > 0f) sayT -= dt
    }
    private var coat = 0
    private var tiltSide = 1f
    private var charging = false

    private fun pose(dt: Float, dancing: Boolean, musicOn: Boolean, au: AudioFrame) {
        val A = act; val T = actT
        leanT = 0f
        r.wag += dt * (6f + (if (dancing) au.beat * 4f else 0f) + (if (A == "Pet") 8f else 0f))
        if (isBird) { r.wing = if (carried || flying) 1f else 0f; if (carried) r.flapPh += dt * 28f }
        if (carried) { r.mouth = .6f; ltx = 0f; lty = -.3f }
        if (knocked > 0f) { r.happy = 0f; r.blink = min(r.blink, .35f); ltx = sin(time * 7f) * .8f; lty = cos(time * 7f) * .5f; if (Random.nextFloat() < dt * 3f) puff(x + (Random.nextFloat() - .5f) * .4f, kind.height * .9f, STAR) }
        if (dancing) {
            val b = sin(time * PI.toFloat() * 2f * max(1f, au.bpm / 60f).coerceAtMost(2.5f))
            // dancing: eyes open and smiling (never shut)
            r.headTilt += b * .12f; r.headY += abs(b) * -.04f; syV -= au.beat * 1.2f; r.happy = 0f; r.smile = 1f
            if (isBird) { r.headY += b * .06f; r.wingLift = .3f + .3f * b; r.tailSw = b * .5f }
            if (kind is ParrotArtKind) r.mouth = max(r.mouth, .8f * au.beat * au.beat)
            when (kind) { is PupKind -> { r.wagAmp = 1f; r.mouth = max(r.mouth, .35f); r.tongue = .5f }; is SquirrelKind -> r.tailFlick = b; is DuckKind, is DuckArtKind -> r.wing = .3f + .3f * abs(b); else -> {} }
        }
        if (musicOn && musicFor in 0.001f..3f) { r.headY -= .03f; r.mouth = .3f }
        fun done(at: Float) { if (T > at) act = null }
        when (A) {
            "Pet" -> { r.happy = 1f; r.headTilt = sin(time * 3f) * .12f; r.wagAmp = 1f; if (!down && T > .4f) act = null }
            "Giggle" -> { r.happy = 1f; r.mouth = .5f; r.headTilt = sin(T * 20f) * .08f; done(.9f) }
            "Head tilt" -> { r.headTilt = winf(T, 0f, .3f, 1.4f, 1.8f) * .35f; done(1.9f) }
            "Shake" -> { r.headTilt = sin(T * 40f) * .2f * (1 - T / .9f); tilt += sin(T * 40f) * .02f; if (T > .9f) { act = "Giggle"; actT = 0f } }
            "Nap" -> { r.blink = 0f; r.sleep = true; r.headY = .05f; if (Random.nextFloat() < dt * 1.5f) puff(x + .25f, kind.height * .9f, ZZ); if (T > 6f || sinceTouch < 1f) act = null }
            // pup
            "Bark" -> { val k = if (T % .45f < .2f) 1f else 0f; r.mouth = k; r.headY -= k * .04f; if (floor(T / .45f) != floor((T - dt) / .45f)) puff(x + .3f, 1.6f, BANG); done(1.4f) }
            "Yawn" -> { r.mouth = winf(T, .2f, .8f, 1.6f, 2f); r.tongue = r.mouth * .8f; r.blink = min(r.blink, 1 - r.mouth * .9f); r.headTilt = -r.mouth * .08f; done(2.1f) }
            "Scratch" -> { val k = winf(T, 0f, .2f, 1.6f, 1.8f); r.headTilt = .25f * k; r.blink = min(r.blink, .4f); r.earR = sin(T * 30f) * .25f * k; done(1.9f) }
            "Play bow" -> { r.headY = winf(T, 0f, .3f, 1.3f, 1.6f) * .25f; r.wagAmp = 1.2f; r.happy = 1f; done(1.7f) }
            "Sniff" -> { r.headY = winf(T, 0f, .3f, 1.5f, 1.8f) * .12f; r.headX = sin(T * 6f) * .05f; ltx = 0f; lty = .8f; done(1.9f) }
            "Give paw" -> { r.pawLift = winf(T, 0f, .3f, 1.5f, 1.8f); r.tongue = .6f; r.mouth = .4f; done(1.9f) }
            "Belly rub" -> { r.happy = 1f; r.tongue = 1f; r.mouth = .7f; r.wagAmp = 1.2f; if (T > 2.6f) act = null else { tilt = mixf(tilt, 1.35f, .12f); tiltV = 0f } }
            "Howl" -> { val k = winf(T, 0f, .4f, 2f, 2.4f); r.headTilt = -.2f * k; r.headY = -.06f * k; r.mouth = winf(T, .4f, .6f, 1.9f, 2.1f) * .6f; ltx = 0f; lty = -1f; done(2.5f) }
            "Dig" -> { val k = winf(T, 0f, .3f, 1.8f, 2.1f); r.headY = .18f * k; r.pawLift = abs(sin(T * 14f)) * k; if (Random.nextFloat() < dt * 10f) puff(x + .15f, 0f, DIRT); done(2.2f) }
            "Fetch" -> { r.happy = 1f; r.wagAmp = 1.2f; r.tongue = .8f; r.mouth = .5f; if (T < dt * 1.5f) { vy = 3.5f; syV -= 4f }; done(1.2f) }
            "Zoomies", "Waddle" -> { r.happy = if (A == "Zoomies") 1f else 0f; r.tongue = if (kind is PupKind) .8f else 0f; if (walkTo.isNaN()) act = null }
            // duck
            "Quack" -> { val k = if (T % .38f < .16f) 1f else 0f; r.mouth = k; r.headY -= k * .03f; if (floor(T / .38f) != floor((T - dt) / .38f)) puff(x + .3f, 1.35f, NOTE); done(1.2f) }
            "Flap" -> { r.wing = abs(sin(T * 22f)) * winf(T, 0f, .1f, 1f, 1.2f); done(1.3f) }
            "Try to fly" -> { r.wing = abs(sin(T * 26f)); if (T < dt * 1.5f) { vy = 4.4f; syV -= 4f }; if (T > 1.4f) { act = "Giggle"; actT = 0f } }
            "Preen" -> { val k = winf(T, 0f, .3f, 1.6f, 1.9f); r.headX = .12f * k; r.headY = .1f * k; r.headTilt = .5f * k; r.blink = min(r.blink, .3f); r.wingLift = .4f * k; done(2f) }
            "Bread crumbs" -> { r.headY = .12f * abs(sin(T * 8f)) * winf(T, 0f, .2f, 1.8f, 2f); lty = .9f; ltx = 0f; if (Random.nextFloat() < dt * 4f) puff(x + .1f, .05f, CRUMB); done(2.1f) }
            "Splash" -> { r.wing = abs(sin(T * 18f)) * winf(T, 0f, .1f, 1.4f, 1.6f); if (Random.nextFloat() < dt * 14f) puff(x + (Random.nextFloat() - .5f) * .6f, 0f, DROP); r.happy = 1f; done(1.7f) }
            // bird
            "Whistle" -> { r.mouth = .25f + .15f * sin(T * 18f); r.headTilt = sin(T * 3f) * .15f; if (Random.nextFloat() < dt * 5f) puff(x + .2f, 1.3f, NOTE); done(2f) }
            "Squawk" -> { val k = winf(T, 0f, .1f, .6f, .8f); r.mouth = k; r.wingLift = .5f * k; r.headY = -.05f * k; if (T < dt * 1.5f) puff(x + .25f, 1.4f, BANG); done(.9f) }
            "Fly a lap" -> { if (T < dt * 1.5f && !flying) { flying = true; vx = (if (Random.nextBoolean()) -1f else 1f) * 4.5f; vy = 6f }; if (!flying && T > .3f) act = null }
            "Head bob" -> { r.headY = abs(sin(T * 9f)) * -.07f; done(1.6f) }
            "Wing stretch" -> { r.wing = .75f * winf(T, 0f, .4f, 1.4f, 1.8f); r.flapPh = -1.2f; done(1.9f) }
            "One leg" -> { r.blink = min(r.blink, .5f); r.step = .6f; r.oneLeg = winf(T, 0f, .3f, 2.2f, 2.5f); done(2.5f) }
            // painted parrot
            "Talk" -> { val ph = T % 1.6f; val syl = if (ph < 1f && (ph * 6f) % 1f < .55f) 1f else 0f; r.mouth = syl; r.headY = -.02f * syl; r.headTilt = sin(T * 2.5f) * .18f
                if (T < dt * 1.5f) speak(pickWord()); done(2.4f) }
            "Hang upside down" -> { leanT = PI.toFloat() * winf(T, 0f, .7f, 2.8f, 3.5f); r.happy = if (winf(T, .8f, 1f, 2.4f, 2.6f) > .5f) 1f else 0f; ltx = 0f; lty = 0f; done(3.6f) }
            "Side step" -> { leanT = sin(walkPh) * .06f; if (walkTo.isNaN() && T > .3f) act = null; done(6f) }
            "Flap workout" -> { r.wing = winf(T, 0f, .25f, 1.8f, 2.2f); r.flapHere = true; r.flapPh += dt * 20f; r.headY = sin(r.flapPh) * .012f; r.mouth = .15f; done(2.3f) }
            "Shake off" -> { val e = winf(T, .15f, .3f, .9f, 1.1f); r.puff = max(r.puff, winf(T, 0f, .15f, 1.1f, 1.4f) * .9f); leanT = sin(T * 42f) * .07f * e; r.headTilt = sin(T * 42f + 1f) * .3f * e; r.blink = min(r.blink, 1 - e * .8f)
                if (e > .5f && Random.nextFloat() < dt * 10f) puff(x + (Random.nextFloat() - .5f) * .5f, .6f + Random.nextFloat() * .6f, FEATHER); done(1.5f) }
            "Curious tilt" -> { if (T < dt * 1.5f) tiltSide = if (Random.nextBoolean()) -1f else 1f; val e = winf(T, 0f, .35f, 1.7f, 2.1f); r.headTilt = tiltSide * 1.25f * e; ltx = 0f; lty = 0f; if (T > .9f && T < 1f) r.blink = 0f; done(2.2f) }
            "Puff up" -> { r.puff = winf(T, 0f, .25f, 1.3f, 1.7f); r.happy = if (r.puff > .5f) 1f else 0f; r.headY = r.puff * .02f; done(1.8f) }
            "Upside down" -> { if (kind is ParrotArtKind) { act = "Hang upside down"; actT = 0f; return }; tilt = PI.toFloat() * winf(T, 0f, .6f, 2.4f, 3f); tiltV = 0f; r.happy = winf(T, .6f, .8f, 2.2f, 2.4f); if (T > 3.1f) { act = null; tilt = 0f } }
            // squirrel
            "Nibble" -> { r.nibble = abs(sin(T * 24f)) * winf(T, 0f, .1f, 1.8f, 2f); r.mouth = r.nibble * .3f; r.blink = min(r.blink, .7f); if (Random.nextFloat() < dt * 6f) puff(x, .45f, CRUMB); done(2.1f) }
            "Tail flick" -> { r.tailFlick = sin(T * 16f) * winf(T, 0f, .1f, 1f, 1.2f); done(1.3f) }
            "Lookout" -> { r.headY = -.12f * winf(T, 0f, .3f, 2f, 2.3f); r.headX = sin(T * 2.4f) * .06f; r.ear = .4f; ltx = sin(T * 2.4f) * .9f; lty = -.2f; done(2.4f) }
            "Bury a nut" -> { r.acorn = T < 1f; r.headY = .15f * winf(T, .2f, .5f, 1.6f, 1.9f); if (T > .8f && T < 1.6f && Random.nextFloat() < dt * 10f) puff(x + .2f, 0f, DIRT); done(2f) }
            "Catch!" -> { if (T < dt * 1.5f) { vy = 3.8f; syV -= 4f }; r.happy = if (T > .5f) 1f else 0f; done(1.2f) }
            "Spin" -> { tilt = if (T < 1f) ToonInk.TAU * smf(T) else 0f; tiltV = 0f; if (T > 1.05f) { tilt = 0f; act = null } }
            "Drum solo" -> { r.nibble = abs(sin(T * 20f)); r.headY = sin(T * 20f) * .02f; if (Random.nextFloat() < dt * 6f) puff(x + .2f, .9f, NOTE); done(2f) }
        }
        if (A != null && act == A && T > 9f) act = null
    }

    // ------------------------------------------------------------ GL: the creature's box, and the bird's perch
    private var prog = 0; private var vbo = 0; private var tex = 0; private var uTex = -1; private var uRect = -1
    private var bmp: Bitmap? = null; private var bcv: Canvas? = null
    private var perchTex = 0; private var perchDirty = true; private var perchBmp: Bitmap? = null
    private val q = FloatArray(4)

    override fun onSurfaceCreated() {
        prog = Gl.program(VS, FS); uTex = GLES20.glGetUniformLocation(prog, "uTex"); uRect = GLES20.glGetUniformLocation(prog, "uRect")
        val ids = IntArray(1); GLES20.glGenBuffers(1, ids, 0); vbo = ids[0]
        val qd = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        val fb = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().put(qd); fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo); GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 32, fb, GLES20.GL_STATIC_DRAW); GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        tex = 0; perchTex = 0; perchDirty = true
        pgl.forget(); art?.forget()
    }

    override fun draw(input: SceneInput) {
        if (prog == 0) return
        // box around the creature, in creature units (wider for the perch + speech bubble)
        val bx0 = x - 1.45f; val bx1 = x + 1.45f
        val by0 = -(y + kind.height + (if (isBird) 1.0f else .6f)); val by1 = -(y - (puppet?.below ?: .4f))
        val wPx = (bx1 - bx0) * U; val hPx = (by1 - by0) * U
        val k = min(1f, 620f / max(wPx, hPx))
        val bw = max(8, (wPx * k).roundToInt()); val bh = max(8, (hPx * k).roundToInt())
        var b = bmp
        if (b == null || b.width != bw || b.height != bh) { b?.recycle(); b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888); bmp = b; bcv = Canvas(b); if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0); tex = 0 }
        val cv = bcv!!
        cv.drawColor(Color.TRANSPARENT, PorterDuff.Mode.SRC)
        cv.save(); cv.scale(bw / (bx1 - bx0), bh / (by1 - by0)); cv.translate(-bx0, -by0)
        ink.begin(cv); ink.ups = bw / (bx1 - bx0)
        drawCreature()
        cv.restore()
        tex = upload(b, tex)
        // screen rect of the box (pixels, y down) -> GL
        q[0] = cx0 + bx0 * U; q[1] = floorY + by0 * U; q[2] = cx0 + bx1 * U; q[3] = floorY + by1 * U
        GLES20.glUseProgram(prog)
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo); GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glUniform1i(uTex, 0)
        if (kind.perch && puppet == null) { ensurePerch(); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, perchTex); rect(cx0 - 1.75f * U, floorY - .35f * U, cx0 + 1.75f * U, floorY + .2f * U) }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex); rect(q[0], q[1], q[2], q[3])
        GLES20.glDisableVertexAttribArray(0); GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0); GLES20.glDisable(GLES20.GL_BLEND)
        if (puppet != null && art != null && ctx != null) drawPuppet(puppet, art, ctx)
    }
    /** The painted puppet, straight to the screen: same place, tilt and squash as the Canvas creatures. */
    private fun drawPuppet(pk: PuppetKind, a: PuppetArt, c: android.content.Context) {
        if (!pgl.ready) pgl.init()
        if (!a.loaded) a.load(c, U * pk.k)
        if (!a.loaded) return
        val lift = .4f * abs(sin(tilt)) * (if (carried) 0f else 1f) * (if (isBird && knocked <= 0f) 0f else 1f)
        pgl.identity(); pgl.translate(cx0, floorY); pgl.scale(U, U); pgl.scale(pk.k, pk.k); pgl.snapshot(pgl.worldBase)
        pgl.identity(); pgl.translate(cx0, floorY); pgl.scale(U, U); pgl.translate(x, -y - lift); pgl.rotate(tilt)
        pgl.scale(1f / sqrt(sy), sy); pgl.scale(pk.k, pk.k); pgl.snapshot(pgl.creatureBase)
        pgl.begin(w, h); pk.drawGl(pgl, r, a); pgl.end()
    }
    private fun rect(l: Float, t: Float, rr: Float, bt: Float) {
        GLES20.glUniform4f(uRect, l / w * 2f - 1f, 1f - t / h * 2f, rr / w * 2f - 1f, 1f - bt / h * 2f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }
    private fun upload(b: Bitmap, t0: Int): Int {
        var t = t0
        if (t == 0) {
            val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0); t = ids[0]; GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
        } else { GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t); GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, b) }
        return t
    }
    private fun ensurePerch() {
        if (!perchDirty && perchTex != 0) return
        perchDirty = false
        val pw = min(1024, max(64, (3.5f * U).roundToInt())); val ph = max(16, (pw * .55f / 3.5f).roundToInt())
        val pb = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888); val pc = Canvas(pb)
        pc.scale(pw / 3.5f, ph / .55f); pc.translate(1.75f, .35f)
        ink.begin(pc); BirdKind.drawPerch(ink)
        if (perchTex != 0) GLES20.glDeleteTextures(1, intArrayOf(perchTex), 0)
        perchTex = upload(pb, 0); pb.recycle()
    }

    private fun drawCreature() {
        val cv = ink.cv
        ink.shadow(x, .02f, .55f, clampf(1 - y / 3f, 0f, 1f))
        cv.save(); cv.translate(x, -y)
        val lift = .4f * abs(sin(tilt)) * (if (carried) 0f else 1f) * (if (kind is BirdKind && knocked <= 0f) 0f else 1f)
        cv.translate(0f, -lift); cv.rotate(tilt * 57.29578f)
        cv.scale(1f / sqrt(sy) * (if (face < 0f && !walkTo.isNaN() && puppet == null) -1f else 1f), sy)
        kind.draw(ink, r, coat)
        cv.restore()
        if (charging) chargeBadge()
        for (p in parts) {
            val al = max(0f, 1 - p.life / p.max); cv.save(); cv.translate(p.x, -p.y)
            when (p.kind) {
                STAR -> { cv.rotate(p.life * 340f); val st = ink.p(); for (i in 0 until 10) { val rr = if (i % 2 == 1) .03f else .075f; val an = i / 10f * ToonInk.TAU; if (i == 0) st.moveTo(rr, 0f) else st.lineTo(cos(an) * rr, sin(an) * rr) }; st.close(); ink.fill(st, 0xFFD45A, al) }
                NOTE, BANG, ZZ -> { cv.scale(1f / ink.ups, 1f / ink.ups); ink.tp.textSize = .22f * ink.ups; ink.tp.color = if (p.kind == ZZ) 0xFFCDB4FF.toInt() else 0xFFFFB36B.toInt(); ink.tp.alpha = (al * 255).toInt(); cv.drawText(if (p.kind == NOTE) "♪" else if (p.kind == BANG) "!" else "z", 0f, 0f, ink.tp) }
                DIRT -> ink.fill(ink.ell(0f, 0f, .03f, .025f), 0x6E4A2E, al)
                DROP -> ink.fill(ink.ell(0f, 0f, .025f, .035f), 0x8FD3FF, al)
                FEATHER -> { cv.rotate(sin(p.life * 7f) * 34f); ink.fill(ink.ell(0f, 0f, .035f, .012f), 0x8FD14F, al) }
                else -> ink.fill(ink.ell(0f, 0f, .015f, .012f), 0xD9A066, al)
            }
            cv.restore()
        }
        if (sayT > 0f && say.isNotEmpty()) bubble()
    }
    /** Charging: a battery badge above the creature with the charge level. */
    private fun chargeBadge() {
        val cv = ink.cv; val bob = sin(time * 2f) * .02f
        cv.save(); cv.translate(x, -(y + kind.height + .38f) + bob)
        val bw = .62f; val bh = .24f
        val body = ink.p(); body.addRoundRect(RectF(-bw / 2, -bh / 2, bw / 2, bh / 2), .07f, .07f, Path.Direction.CW)
        ink.fill(body, 0x1A2E2B, .92f); ink.line(body, 0x4ADE80, .018f)
        val cap = ink.p(); cap.addRoundRect(RectF(bw / 2 + .012f, -.045f, bw / 2 + .047f, .045f), .012f, .012f, Path.Direction.CW); ink.fill(cap, 0x4ADE80)
        val lvl = battery.coerceIn(0f, 1f)
        val fillP = ink.p(); fillP.addRoundRect(RectF(-bw / 2 + .03f, -bh / 2 + .03f, -bw / 2 + .03f + (bw - .06f) * lvl, bh / 2 - .03f), .045f, .045f, Path.Direction.CW)
        ink.fill(fillP, 0x4ADE80, .32f + .1f * sin(time * 3f))
        val bolt = ink.p(); cv.save(); cv.translate(-bw / 2 + .1f, 0f)
        bolt.moveTo(.014f, -.075f); bolt.lineTo(-.045f, .012f); bolt.lineTo(-.003f, .012f); bolt.lineTo(-.016f, .075f); bolt.lineTo(.046f, -.014f); bolt.lineTo(.004f, -.014f); bolt.close()
        ink.fill(bolt, 0xEFFFF4); cv.restore()
        ink.tp.textSize = .135f * ink.ups; ink.tp.color = 0xFFFFFFFF.toInt(); ink.tp.alpha = 255
        cv.save(); cv.scale(1f / ink.ups, 1f / ink.ups); cv.drawText("${(lvl * 100).roundToInt()}%", .04f * ink.ups, .05f * ink.ups, ink.tp); cv.restore()
        cv.restore()
    }
    private fun bubble() {
        val cv = ink.cv; val a = clampf(sayT / .3f, 0f, 1f)
        ink.tp.textSize = .17f * ink.ups; val tw = ink.tp.measureText(say) / ink.ups
        val bx = x + .35f; val by = -(y + kind.height + .45f)
        val bp = ink.p(); bp.addRoundRect(RectF(bx - tw / 2 - .1f, by - .16f, bx + tw / 2 + .1f, by + .1f), .12f, .12f, Path.Direction.CW)
        bp.moveTo(bx - .12f, by + .08f); bp.lineTo(bx - .2f, by + .22f); bp.lineTo(bx - .02f, by + .08f)
        ink.fill(bp, 0xFFFFFF, .95f * a)
        ink.tp.color = 0xFF1E192B.toInt(); ink.tp.alpha = (a * 255).toInt()
        cv.save(); cv.scale(1f / ink.ups, 1f / ink.ups); cv.drawText(say, bx * ink.ups, (by + .02f) * ink.ups, ink.tp); cv.restore()
    }

    /** Tests: the Canvas layer (shadow, particles, bubble, badge) at its place on a full-screen canvas. */
    internal fun debugOverlay(c: Canvas) { c.save(); c.translate(cx0, floorY); c.scale(U, U); ink.begin(c); ink.ups = U; drawCreature(); c.restore() }
    /** Tests: the painted puppet through any [PuppetGl] (the Canvas twin), with bitmaps instead of textures. */
    internal fun debugPuppet(g: PuppetGl, width: Int, height: Int) {
        val pk = puppet ?: return; val a = art ?: return
        if (!a.loaded) a.loadBitmaps(ctx!!)
        val lift = .4f * abs(sin(tilt)) * (if (carried) 0f else 1f) * (if (isBird && knocked <= 0f) 0f else 1f)
        g.identity(); g.translate(cx0, floorY); g.scale(U, U); g.scale(pk.k, pk.k); g.snapshot(g.worldBase)
        g.identity(); g.translate(cx0, floorY); g.scale(U, U); g.translate(x, -y - lift); g.rotate(tilt)
        g.scale(1f / sqrt(sy), sy); g.scale(pk.k, pk.k); g.snapshot(g.creatureBase)
        g.begin(width, height); pk.drawGl(g, r, a); g.end()
    }
    /** Tests: draws the creature (no GL) into [c], already scaled to creature units with the floor at 0. */
    internal fun debugDraw(c: android.graphics.Canvas, pxPerUnit: Float) { ink.begin(c); ink.ups = pxPerUnit; drawCreature() }
    internal val debugState get() = "x=%.2f y=%.2f tilt=%.2f carried=%b flying=%b knocked=%.2f act=%s".format(x, y, tilt, carried, flying, knocked, act)

    override fun release() {
        if (prog != 0) GLES20.glDeleteProgram(prog); if (vbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
        val ts = intArrayOf(tex, perchTex).filter { it != 0 }.toIntArray(); if (ts.isNotEmpty()) GLES20.glDeleteTextures(ts.size, ts, 0)
        bmp?.recycle(); bmp = null; bcv = null; prog = 0; vbo = 0; tex = 0; perchTex = 0
        pgl.release(); art?.release()
    }

    companion object {
        const val STAR = 0; const val NOTE = 1; const val BANG = 2; const val ZZ = 3; const val DIRT = 4; const val DROP = 5; const val CRUMB = 6; const val FEATHER = 7
        /** Studio trick ids -> act names. */
        val TRICKS = mapOf(
            "fetch" to "Fetch", "paw" to "Give paw", "belly" to "Belly rub", "dig" to "Dig", "howl" to "Howl", "zoomies" to "Zoomies", "yawn" to "Yawn",
            "scratch" to "Scratch", "bow" to "Play bow", "bark" to "Bark", "sniff" to "Sniff",
            "quack" to "Quack", "flap" to "Flap", "fly" to "Try to fly", "preen" to "Preen", "crumbs" to "Bread crumbs", "splash" to "Splash", "waddle" to "Waddle", "sleep" to "Nap",
            "whistle" to "Whistle", "squawk" to "Squawk", "lap" to "Fly a lap", "dance" to "Head bob", "stretch" to "Wing stretch", "oneleg" to "One leg", "hang" to "Hang upside down", "talk" to "Talk",
            "sidestep" to "Side step", "workout" to "Flap workout", "shakeoff" to "Shake off", "curious" to "Curious tilt", "puffup" to "Puff up",
            "nibble" to "Nibble", "stash" to "Bury a nut", "lookout" to "Lookout", "flick" to "Tail flick", "catch" to "Catch!", "spin" to "Spin", "drum" to "Drum solo", "feed" to "Nibble", "zoom" to "Zoomies",
        )
        private const val VS = "attribute vec2 aPos; uniform vec4 uRect; varying vec2 vUv; void main(){ vUv = aPos; gl_Position = vec4(mix(uRect.xy, uRect.zw, aPos), 0.0, 1.0); }"
        private const val FS = "precision mediump float; varying vec2 vUv; uniform sampler2D uTex; void main(){ gl_FragColor = texture2D(uTex, vUv); }"
    }
}
