package app.pulsify.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** One member of the gang: colours, how many eyes, what it wears, how it smiles. */
internal class GloopTrait(val base: Int, val light: Int, val dark: Int, val rim: Int, val eyes: Int, val extra: String, val mouth: String,
                    val happy: Boolean = false, val big: Boolean = false, val freckles: Boolean = false, val name: String)

internal val GLOOP_TRAITS = listOf(
    GloopTrait(0xFF8EE03B.toInt(), 0xFFD8FF9A.toInt(), 0xFF4E9A12.toInt(), 0xFFC9FF7A.toInt(), 3, "sprout", "grin", name = "Sprout"),
    GloopTrait(0xFFF7C431.toInt(), 0xFFFFF0A0.toInt(), 0xFFB9820B.toInt(), 0xFFFFE27A.toInt(), 2, "bunny", "o", name = "Bun"),
    GloopTrait(0xFFF25FD0.toInt(), 0xFFFFB5EE.toInt(), 0xFFA3228A.toInt(), 0xFFFF9BEA.toInt(), 2, "cat", "smile", name = "Mew"),
    GloopTrait(0xFF4FE0B0.toInt(), 0xFFB8FFE6.toInt(), 0xFF1F9A73.toInt(), 0xFF8FFFD8.toInt(), 2, "antenna", "smile", happy = true, name = "Beep"),
    GloopTrait(0xFFE8355A.toInt(), 0xFFFF9AAE.toInt(), 0xFF7A0F2A.toInt(), 0xFFFF7A96.toInt(), 1, "", "fang", big = true, name = "Chomp"),
    GloopTrait(0xFF9D5CFF.toInt(), 0xFFD9C2FF.toInt(), 0xFF5A22B5.toInt(), 0xFFC7A6FF.toInt(), 2, "horns", "smirk", name = "Imp"),
    GloopTrait(0xFF3DA7FF.toInt(), 0xFFB5DEFF.toInt(), 0xFF1561B5.toInt(), 0xFF86C8FF.toInt(), 2, "crown", "smile", name = "King"),
    GloopTrait(0xFFFF8A3D.toInt(), 0xFFFFD0A6.toInt(), 0xFFB4500F.toInt(), 0xFFFFB47A.toInt(), 2, "star", "o", freckles = true, name = "Star"),
)

private val FRECKLES = floatArrayOf(-0.55f, 0.08f, -0.48f, 0.16f, -0.62f, 0.2f, 0.55f, 0.08f, 0.48f, 0.16f, 0.62f, 0.2f)

/** Gang sizes offered in the Studio (they use the "coat" slot). */
val GLOOP_SIZES = listOf(2, 3, 5, 8)

/**
 * The Gloop Gang: glossy jelly blobs that roam the whole screen, bump and squish into each
 * other and start their own games. Tap one to squish it, drag and fling it into the others,
 * hold the screen and they all come over.
 */
class GloopScene(context: Context) : CanvasCreature(context) {

    private enum class Game { ROAM, TAG, TOWER, CONGA, PEEKABOO, BOUNCE, WAVE, GATHER, MUSIC, SLEEP, POTATO }

    private class Blob(val tr: GloopTrait) {
        var r = 0f; var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var tx = 0f; var ty = 0f; var next = 0f
        var sq = 1f; var sqv = 0f; var ang = 0f; val seed = Random.nextFloat() * 10f; val blink = 3f + Random.nextFloat() * 3f
        var lx = 0f; var ly = 0f; var surprise = 0f; var hidden = 0f
    }

    private val blobs = ArrayList<Blob>()
    private var count = 5
    private var game = Game.ROAM; private var gameT = 0f; private var gameLen = 0f
    private var nextGame = 3f
    private val lastGames = ArrayDeque<Game>()
    private var itIdx = 0; private var tagT = -9f; private var tagMsgT = -9f; private var tagged: Blob? = null
    private var toppled = false
    private var grab: Blob? = null; private var grabX = 0f; private var grabY = 0f; private var lastGX = 0f; private var lastGY = 0f
    private var holdX = 0f; private var holdY = 0f
    private var lastBeat = -9f; private var kick = 0f
    private var msg = ""; private var msgX = 0f; private var msgY = 0f; private var msgT = 0f

    override val busy: Boolean get() = true

    override fun layout() { build() }

    private fun build() {
        if (W < 2f) return
        val n = count
        val R = unit * (if (n <= 2) 0.15f else if (n <= 3) 0.13f else if (n <= 5) 0.105f else 0.085f)
        val old = blobs.toList()
        blobs.clear()
        for (i in 0 until n) {
            val tr = GLOOP_TRAITS[i % GLOOP_TRAITS.size]
            val b = old.firstOrNull { it.tr === tr } ?: Blob(tr).also {
                it.x = W * (0.15f + 0.7f * Random.nextFloat()); it.y = H * (0.3f + 0.4f * Random.nextFloat()); it.tx = it.x; it.ty = it.y
            }
            b.r = R * (if (tr.big) 1.22f else 1f)
            blobs += b
        }
    }

    override fun update(dt: Float, input: SceneInput) {
        val n = GLOOP_SIZES[input.coat.coerceIn(0, GLOOP_SIZES.size - 1)]
        if (n != count) { count = n; build() }
        super.update(dt, input)
    }

    private fun blobAt(px: Float, py: Float): Blob? = blobs.filter { hypot(px - it.x, py - it.y) < it.r * 1.25f }.minByOrNull { hypot(px - it.x, py - it.y) }

    override fun touchDown(nx: Float, ny: Float): Boolean {
        val px = xp(nx); val py = yp(ny); sinceTouch = 0f
        val b = blobAt(px, py) ?: return false
        grab = b; grabX = px; grabY = py; lastGX = px; lastGY = py
        b.surprise = 1f; b.sqv += 4f
        return true
    }
    override fun touchMove(nx: Float, ny: Float): Boolean {
        sinceTouch = 0f
        if (grab == null) { holdX = xp(nx); holdY = yp(ny); return false }
        lastGX = grabX; lastGY = grabY; grabX = xp(nx); grabY = yp(ny)
        return true
    }
    override fun touchUp(): Boolean {
        val b = grab ?: return false
        // Fling with the finger's speed.
        b.vx = (grabX - lastGX) * 60f; b.vy = (grabY - lastGY) * 60f
        grab = null
        say("wheee!", b)
        return true
    }

    override fun tapAt(nx: Float, ny: Float): Boolean {
        val px = xp(nx); val py = yp(ny); sinceTouch = 0f
        val b = blobAt(px, py)
        if (b != null) {
            b.sqv -= 9f; b.surprise = 1f
            say(listOf("squish!", "hey!", "blep", "boing", "hehe").random(), b)
            listener?.onBondXp(1)
            return true
        }
        // Tap empty space: everyone looks and the nearest one hops over.
        blobs.minByOrNull { hypot(px - it.x, py - it.y) }?.let { it.tx = px; it.ty = min(py, floorY - it.r); it.next = time + 2f }
        blobs.forEach { it.surprise = 0.6f }
        return false
    }

    override fun longPressAt(nx: Float, ny: Float): Boolean {
        holdX = xp(nx); holdY = yp(ny)
        startGame(Game.GATHER)
        return true
    }

    override fun greet() { startGame(Game.BOUNCE) }

    override fun perform(trick: String): Boolean {
        val g = when (trick) {
            "tag" -> Game.TAG; "tower" -> Game.TOWER; "conga" -> Game.CONGA; "peekaboo" -> Game.PEEKABOO
            "bounce" -> Game.BOUNCE; "wave" -> Game.WAVE
            "squish" -> { blobs.forEach { it.sqv -= 8f; it.surprise = 0.8f }; return true }
            else -> return false
        }
        startGame(g); return true
    }

    private fun say(s: String, b: Blob) { msg = s; msgX = b.x; msgY = b.y - b.r * 1.3f; msgT = 1.2f }

    private fun startGame(g: Game) {
        game = g; gameT = 0f; toppled = false
        gameLen = when (g) {
            Game.TAG -> 10f; Game.TOWER -> 7f; Game.CONGA -> 9f; Game.PEEKABOO -> 7f; Game.BOUNCE -> 3.2f
            Game.WAVE -> 4f; Game.GATHER -> 4f; Game.POTATO -> 9f; else -> 0f
        }
        if (g == Game.TAG) { itIdx = Random.nextInt(blobs.size.coerceAtLeast(1)); tagT = time }
        if (g == Game.TOWER && blobs.isNotEmpty()) say("tower time!", blobs.maxByOrNull { it.r }!!)
        if (g == Game.CONGA && blobs.isNotEmpty()) say("conga!", blobs[0])
        if (g == Game.PEEKABOO) blobs.forEachIndexed { i, b -> b.hidden = 0f; b.tx = if (i % 2 == 0) -b.r * 0.4f else W + b.r * 0.4f; b.ty = H * (0.3f + 0.1f * i) }
    }

    override fun step(dt: Float) {
        gameT += dt; msgT -= dt
        val floor = floorY
        val a = audio
        if (musicOn && a.beat > 0.95f && time - lastBeat > 0.22f) { lastBeat = time; kick = 1f; blobs.forEach { it.sqv -= 3f + a.bass * 4f } }
        kick = max(0f, kick - dt * 3.5f)

        // What the gang is up to.
        val base = when {
            musicOn -> Game.MUSIC
            night && sinceTouch > 8f -> Game.SLEEP
            charging -> Game.POTATO
            else -> Game.ROAM
        }
        if (game == Game.MUSIC || game == Game.SLEEP || (game == Game.POTATO && !charging)) { if (game != base) { game = base; gameT = 0f } }
        if (game != Game.ROAM && gameLen > 0f && gameT > gameLen) { game = base; gameT = 0f }
        if (game == Game.ROAM && base != Game.ROAM) { game = base; gameT = 0f; gameLen = 0f }
        if ((game == Game.ROAM || game == Game.POTATO) && sinceTouch > 3f && grab == null) {
            nextGame -= dt
            if (nextGame <= 0f && blobs.size >= 2) {
                val pool = mutableListOf(Game.TAG, Game.TOWER, Game.CONGA, Game.PEEKABOO, Game.BOUNCE, Game.WAVE)
                if (blobs.size < 3) pool.remove(Game.TOWER)
                pool.removeAll(lastGames)
                val g = pool.random()
                lastGames.addLast(g); if (lastGames.size > 3) lastGames.removeFirst()
                startGame(g)
                nextGame = TRICK_GAP_MIN + Random.nextFloat() * TRICK_GAP_RANGE
            }
        }

        val n = blobs.size
        // Targets.
        blobs.forEachIndexed { i, b ->
            if (b === grab) return@forEachIndexed
            when (game) {
                Game.ROAM -> if (time > b.next) {
                    b.tx = b.r + Random.nextFloat() * (W - 2 * b.r); b.ty = H * 0.3f + Random.nextFloat() * (floor - H * 0.3f - b.r)
                    b.next = time + 2f + Random.nextFloat() * 4f
                }
                Game.MUSIC -> { val col = (i + 0.5f) / n; b.tx = W * (0.1f + 0.8f * col); b.ty = floor - b.r - abs(sin((time * 2f + i * 0.25f) * PI.toFloat())) * b.r * (1.2f + a.bass) }
                Game.SLEEP -> { b.tx = W * 0.15f + (i % 4) * b.r * 1.7f; b.ty = floor - b.r - (i / 4) * b.r * 1.5f }
                Game.POTATO -> { b.tx = W * (0.12f + 0.76f * (i + 0.5f) / n); b.ty = floor - b.r }
                Game.GATHER -> { val ang = i * 2f * PI.toFloat() / n + time * 0.5f; b.tx = holdX + cos(ang) * b.r * 2.2f; b.ty = min(holdY + sin(ang) * b.r * 1.6f, floor - b.r) }
                Game.BOUNCE -> { b.ty = floor - b.r - abs(sin(gameT * 4.5f)) * b.r * 2.5f }
                Game.WAVE -> { val k = (gameT * 2.2f - i * 0.35f); b.tx = W * (0.1f + 0.8f * (i + 0.5f) / n); b.ty = floor - b.r - (if (k in 0f..1f) sin(k * PI.toFloat()) * b.r * 2f else 0f) }
                Game.CONGA -> {
                    if (i == 0) { b.tx = W * 0.5f + cos(gameT * 0.9f) * W * 0.32f; b.ty = floor - b.r - abs(sin(gameT * 3f)) * b.r * 0.4f }
                    else { val lead = blobs[i - 1]; b.tx = lead.x - (if (lead.vx >= 0) 1f else -1f) * (lead.r + b.r) * 1.05f; b.ty = floor - b.r - abs(sin(gameT * 3f - i * 0.6f)) * b.r * 0.4f }
                }
                Game.PEEKABOO -> {
                    // Hide off the edges, then pop out together.
                    if (gameT > 2.6f && gameT < 2.7f) { blobs.forEach { it.sqv -= 6f }; say("BOO!", blobs[0]) }
                    if (gameT > 2.6f) { b.tx = W * (0.15f + 0.7f * (i + 0.5f) / n); b.ty = H * 0.55f }
                }
                Game.TOWER -> {
                    val order = blobs.sortedByDescending { it.r }
                    val k = order.indexOf(b)
                    var y = floor
                    for (j in 0 until k) y -= order[j].r * 1.75f
                    val sway = sin(gameT * 2f) * k * k * b.r * 0.04f * (gameT / 5f)
                    b.tx = W * 0.5f + sway; b.ty = y - b.r
                    if (gameT > 5.2f && !toppled) {
                        toppled = true
                        blobs.forEach { q -> q.vx += (Random.nextFloat() - 0.4f) * W * 1.4f; q.vy -= Random.nextFloat() * H * 0.9f; q.sqv += 6f }
                        say("nooo", order.last())
                    }
                    if (toppled) { b.tx = b.x; b.ty = floor - b.r }
                }
                Game.TAG -> {
                    val itb = blobs.getOrNull(itIdx % n) ?: return@forEachIndexed
                    if (b === itb) {
                        val target = blobs.filter { q -> q !== itb }.minByOrNull { q -> hypot(q.x - itb.x, q.y - itb.y) }
                        if (target != null) { b.tx = target.x; b.ty = target.y }
                    } else {
                        val dx = b.x - itb.x; val dy = b.y - itb.y; val d = max(1f, hypot(dx, dy))
                        b.tx = (b.x + dx / d * W * 0.3f).coerceIn(b.r, W - b.r); b.ty = (b.y + dy / d * H * 0.3f).coerceIn(H * 0.2f, floor - b.r)
                    }
                }
            }
        }
        // Integrate.
        val speed = when (game) { Game.TAG -> 2.6f; Game.MUSIC -> 7f; Game.TOWER -> 4f; Game.BOUNCE, Game.WAVE -> 9f; Game.CONGA -> 5f; Game.GATHER -> 4f; else -> 1.3f }
        val damp = if (game == Game.MUSIC || game == Game.BOUNCE || game == Game.WAVE) 4f else 1.9f
        val gravity = if (toppled) H * 2.4f else 0f
        val melt = if (lowBattery) 0.8f else 1f
        for (b in blobs) {
            if (b === grab) {
                b.vx = (grabX - b.x) / max(dt, 0.001f); b.vy = (grabY - b.y) / max(dt, 0.001f); b.x = grabX; b.y = grabY; continue
            }
            val isIt = game == Game.TAG && b === blobs.getOrNull(itIdx % max(1, n))
            val k = speed * (if (isIt) 1.3f else 1f)
            b.vx += ((b.tx - b.x) * k - b.vx * damp) * dt
            b.vy += ((b.ty - b.y) * k - b.vy * damp) * dt + gravity * dt
            b.x += b.vx * dt; b.y += b.vy * dt
            val left = if (game == Game.PEEKABOO && gameT < 2.6f) -b.r * 0.7f else b.r
            val right = if (game == Game.PEEKABOO && gameT < 2.6f) W + b.r * 0.7f else W - b.r
            if (b.x < left) { b.x = left; b.vx = abs(b.vx) * 0.6f; b.sqv += 3f }
            if (b.x > right) { b.x = right; b.vx = -abs(b.vx) * 0.6f; b.sqv += 3f }
            if (b.y < b.r * 1.7f) { b.y = b.r * 1.7f; b.vy = abs(b.vy) * 0.6f; b.sqv += 3f }
            if (b.y > floor - b.r * 0.9f * melt) { b.y = floor - b.r * 0.9f * melt; b.vy = -abs(b.vy) * 0.5f; b.sqv -= min(6f, abs(b.vy) / H * 10f) }
            b.ang = clampF(b.vx / W * 0.6f, -0.4f, 0.4f)
        }
        // Jelly collisions.
        for (i in 0 until n) for (j in i + 1 until n) {
            val p = blobs[i]; val q = blobs[j]
            val dx = q.x - p.x; val dy = q.y - p.y; val d = max(0.001f, hypot(dx, dy)); val mn = p.r + q.r
            if (d < mn) {
                val push = (mn - d) / 2f; val nx = dx / d; val ny = dy / d
                if (p !== grab) { p.x -= nx * push; p.y -= ny * push }
                if (q !== grab) { q.x += nx * push; q.y += ny * push }
                val rv = (q.vx - p.vx) * nx + (q.vy - p.vy) * ny
                if (rv < 0f) {
                    p.vx += rv * nx * 0.8f; p.vy += rv * ny * 0.8f; q.vx -= rv * nx * 0.8f; q.vy -= rv * ny * 0.8f
                    val hit = min(4f, -rv / W * 6f); p.sqv += hit; q.sqv += hit
                }
                if (game == Game.TAG && time - tagT > 1.2f) {
                    val itb = blobs.getOrNull(itIdx % n)
                    if (p === itb || q === itb) {
                        val other = if (p === itb) q else p
                        itIdx = blobs.indexOf(other); tagT = time; tagged = other; tagMsgT = time; other.sqv += 5f
                    }
                }
            }
        }
        // Wobble, gaze.
        for (b in blobs) {
            b.sqv += ((1f - b.sq) * 120f - b.sqv * 7f) * dt
            b.sq = clampF(b.sq + b.sqv * dt, 0.72f, 1.3f)
            if (game == Game.MUSIC) b.sq = clampF(b.sq, 0.85f, 1.15f)
            b.surprise = max(0f, b.surprise - dt * 0.8f)
            var lx = 0f; var ly = 0f
            val g = grab
            if (g != null && g !== b) { val dx = g.x - b.x; val dy = g.y - b.y; val d = max(1f, hypot(dx, dy)); lx = dx / d; ly = dy / d }
            else if (sinceTouch < 2f) { val dx = holdX - b.x; val dy = holdY - b.y; val d = max(1f, hypot(dx, dy)); lx = dx / d; ly = dy / d }
            else {
                var best: Blob? = null; var bd = 1e9f
                for (o in blobs) if (o !== b) { val d = hypot(o.x - b.x, o.y - b.y); if (d < bd) { bd = d; best = o } }
                if (best != null) { lx = (best.x - b.x) / bd; ly = (best.y - b.y) / bd }
            }
            b.lx += (lx - b.lx) * min(1f, dt * 6f); b.ly += (ly - b.ly) * min(1f, dt * 6f)
        }
    }

    override fun paint(c: Canvas) {
        val floor = floorY
        // Disco beams when the music plays.
        if (musicOn) {
            val bx = W * 0.5f; val by = H * 0.08f
            val cols = intArrayOf(0xFF8CFF96.toInt(), 0xFFCD78FF.toInt(), 0xFFFFB45A.toInt(), 0xFF5AC8FF.toInt())
            for (i in 0 until 4) {
                val ang = PI.toFloat() / 2f + sin(time * (0.5f + i * 0.13f) + i * 1.7f) * 0.75f
                val L = H * 1.2f; val sp = 0.12f
                ink.path.reset(); ink.path.moveTo(bx, by)
                ink.path.lineTo(bx + cos(ang - sp) * L, by + sin(ang - sp) * L); ink.path.lineTo(bx + cos(ang + sp) * L, by + sin(ang + sp) * L); ink.path.close()
                c.drawPath(ink.path, ink.color(cols[i], 0.12f + kick * 0.12f))
            }
        }
        // Shadows.
        for (b in blobs) {
            val d = clampF((floor - b.y) / H, 0f, 1f)
            ink.ell(c, b.x, floor + 4f, b.r * (0.9f - d * 0.5f), b.r * 0.15f * (1f - d * 0.5f), Color.BLACK, 0.35f * (1f - d))
        }
        // Back to front.
        val order = blobs.sortedBy { it.y }
        val itb = if (game == Game.TAG) blobs.getOrNull(itIdx % max(1, blobs.size)) else null
        for (b in order) {
            val blink = if ((time + b.seed) % b.blink < 0.12f) 1f else 0f
            val near = itb != null && b !== itb && hypot(b.x - itb.x, b.y - itb.y) < b.r * 4f
            val sing = if (musicOn) 0.3f + 0.7f * abs(sin(time * 7f + b.seed)) * (0.4f + audio.mid) else 0f
            paintBlob(c, b, blink, game == Game.SLEEP, near || b.surprise > 0.3f || b === grab, sing)
            if (b === itb) ink.text(c, "IT", b.x, b.y - b.r * 1.4f, b.r * 0.34f, 0xFFFFB36B.toInt())
        }
        // Charging: they pass a glowing battery like a hot potato.
        if (game == Game.POTATO && blobs.isNotEmpty()) {
            val n = blobs.size
            val k = (time * 0.8f) % n
            val i0 = k.toInt(); val f = k - i0
            val p0 = blobs[i0 % n]; val p1 = blobs[(i0 + 1) % n]
            val bx = lerpF(p0.x, p1.x, easeF(f)); val by = lerpF(p0.y, p1.y, easeF(f)) - p0.r * 1.3f - sin(f * PI.toFloat()) * p0.r * 1.2f
            val bw = p0.r * 0.9f
            ink.glow(c, bx, by, bw * 1.2f, bw * 0.9f, 0xFF7CFFB0.toInt(), 0.45f)
            ink.r.set(bx - bw / 2, by - bw / 4, bx + bw / 2, by + bw / 4)
            c.drawRoundRect(ink.r, bw * 0.1f, bw * 0.1f, ink.stroke(0xFF7CFFB0.toInt(), bw * 0.07f))
            c.drawRect(bx - bw / 2 + bw * 0.08f, by - bw / 4 + bw * 0.08f, bx - bw / 2 + bw * 0.08f + (bw - bw * 0.16f) * battery, by + bw / 4 - bw * 0.08f, ink.color(0xFF7CFFB0.toInt(), 0.9f))
        }
        if (tagged != null && time - tagMsgT < 1f) {
            val b = tagged!!
            ink.text(c, "tag!", b.x + b.r * 0.6f, b.y - b.r * 1.1f - (time - tagMsgT) * b.r, b.r * 0.45f, 0xFFFFE45C.toInt(), 1f - (time - tagMsgT))
        }
        if (game == Game.SLEEP) blobs.forEachIndexed { i, b -> if (i % 2 == 0) ink.zzz(c, b.x + b.r * 0.7f, b.y - b.r * 0.8f, time + i, b.r * 0.35f) }
        if (musicOn) for (i in 0 until 4) {
            val k = (time * 0.35f + i / 4f) % 1f
            ink.note(c, W * (0.15f + i * 0.23f), floor - k * H * 0.5f, unit * 0.03f, intArrayOf(0xFFCDB4FF.toInt(), 0xFFFFB36B.toInt(), 0xFF7FE3FF.toInt(), 0xFF9BE15D.toInt())[i], sin(k * PI.toFloat()))
        }
        if (msgT > 0f) ink.bubble(c, msgX, msgY, msg, unit * 0.035f, min(1f, msgT * 4f))
    }

    private fun paintBlob(c: Canvas, b: Blob, blink: Float, sleep: Boolean, surprise: Boolean, sing: Float) {
        val tr = b.tr; val r = b.r
        c.save(); c.translate(b.x, b.y); c.rotate(b.ang * 57.29578f)
        val melt = if (lowBattery) 0.86f else 1f
        c.scale(1f / sqrt(b.sq * melt), b.sq * melt)
        // Traits behind the head.
        if (tr.extra == "bunny") for (s in intArrayOf(-1, 1)) {
            c.save(); c.translate(s * r * 0.4f, -r * 0.8f); c.rotate((s * 0.15f + sin(time * 3f + b.seed) * 0.08f) * 57.29578f)
            ink.glossy(c, 0f, -r * 0.5f, r * 0.2f, r * 0.6f, tr.base, tr.light, tr.dark)
            ink.ell(c, 0f, -r * 0.45f, r * 0.08f, r * 0.4f, Color.WHITE, 0.35f)
            c.restore()
        }
        if (tr.extra == "cat") for (s in intArrayOf(-1, 1)) {
            ink.path.reset(); ink.path.moveTo(s * r * 0.75f, -r * 0.45f); ink.path.lineTo(s * r * 0.55f, -r * 1.25f); ink.path.lineTo(s * r * 0.15f, -r * 0.85f); ink.path.close()
            c.drawPath(ink.path, ink.color(tr.dark))
            ink.path.reset(); ink.path.moveTo(s * r * 0.62f, -r * 0.6f); ink.path.lineTo(s * r * 0.53f, -r * 1.05f); ink.path.lineTo(s * r * 0.3f, -r * 0.82f); ink.path.close()
            c.drawPath(ink.path, ink.color(tr.light))
        }
        if (tr.extra == "horns") for (s in intArrayOf(-1, 1)) {
            ink.path.reset(); ink.path.moveTo(s * r * 0.55f, -r * 0.7f)
            ink.path.quadTo(s * r * 0.95f, -r * 1.2f, s * r * 0.7f, -r * 1.35f); ink.path.quadTo(s * r * 0.6f, -r * 1.05f, s * r * 0.25f, -r * 0.88f); ink.path.close()
            c.drawPath(ink.path, ink.color(0xFF2A1640.toInt()))
        }
        // Body: glossy jelly with a rim and an inner glow.
        ink.glow(c, 0f, 0f, r * 1.35f, r * 1.35f, tr.rim, 0.18f)
        ink.glossy(c, 0f, 0f, r, r, tr.base, tr.light, tr.dark)
        ink.r.set(-r + r * 0.02f, -r + r * 0.02f, r - r * 0.02f, r - r * 0.02f)
        c.drawOval(ink.r, ink.stroke(tr.rim, max(1.5f, r * 0.035f), 0.9f))
        ink.glow(c, 0f, r * 0.5f, r * 0.6f, r * 0.4f, Color.WHITE, 0.18f)
        ink.shine(c, -r * 0.38f, -r * 0.52f, r * 0.3f, r * 0.15f, 0.5f)
        ink.ell(c, -r * 0.05f, -r * 0.62f, r * 0.05f, r * 0.05f, Color.WHITE, 0.8f)
        // Traits on top.
        when (tr.extra) {
            "sprout" -> {
                ink.line(c, 0f, -r * 0.95f, 0f, -r * 1.25f, 0xFF3F8A22.toInt(), r * 0.05f)
                ink.ell(c, -r * 0.15f, -r * 1.3f, r * 0.18f, r * 0.08f, 0xFF6FD16A.toInt(), 1f, 0.4f + sin(time * 2f + b.seed) * 0.1f)
                ink.ell(c, r * 0.15f, -r * 1.3f, r * 0.18f, r * 0.08f, 0xFF6FD16A.toInt(), 1f, -0.4f)
                ink.ell(c, 0f, -r * 1.27f, r * 0.06f, r * 0.06f, 0xFFFF8FCF.toInt())
            }
            "antenna" -> {
                val sw = sin(time * 2.5f + b.seed) * r * 0.15f
                ink.path.reset(); ink.path.moveTo(0f, -r * 0.95f); ink.path.quadTo(sw, -r * 1.2f, sw * 1.5f, -r * 1.45f)
                c.drawPath(ink.path, ink.stroke(tr.dark, r * 0.04f))
                ink.glow(c, sw * 1.5f, -r * 1.5f, r * 0.35f, r * 0.35f, tr.rim, 0.6f)
                ink.ell(c, sw * 1.5f, -r * 1.5f, r * 0.1f, r * 0.1f, 0xFFD6FFF0.toInt())
            }
            "crown" -> {
                ink.path.reset(); ink.path.moveTo(-r * 0.4f, -r * 0.82f); ink.path.lineTo(-r * 0.45f, -r * 1.2f); ink.path.lineTo(-r * 0.2f, -r * 1.0f)
                ink.path.lineTo(0f, -r * 1.3f); ink.path.lineTo(r * 0.2f, -r * 1.0f); ink.path.lineTo(r * 0.45f, -r * 1.2f); ink.path.lineTo(r * 0.4f, -r * 0.82f); ink.path.close()
                c.drawPath(ink.path, ink.color(0xFFFFD23F.toInt()))
                ink.ell(c, 0f, -r * 1.0f, r * 0.06f, r * 0.06f, 0xFFFF5C9A.toInt())
            }
            "star" -> {
                c.save(); c.translate(r * 0.3f, -r * 1.05f); c.rotate(time * 0.8f * 57.29578f)
                ink.path.reset()
                for (i in 0 until 10) {
                    val an = i * PI.toFloat() / 5f - PI.toFloat() / 2f; val rr = if (i % 2 == 1) r * 0.09f else r * 0.22f
                    if (i == 0) ink.path.moveTo(cos(an) * rr, sin(an) * rr) else ink.path.lineTo(cos(an) * rr, sin(an) * rr)
                }
                ink.path.close(); c.drawPath(ink.path, ink.color(0xFFFFE45C.toInt()))
                c.restore()
            }
        }
        // Face.
        val eyeAt = when (tr.eyes) { 3 -> floatArrayOf(-0.38f, -0.08f, 0f, -0.32f, 0.38f, -0.08f); 1 -> floatArrayOf(0f, -0.15f); else -> floatArrayOf(-0.3f, -0.12f, 0.3f, -0.12f) }
        val er = when (tr.eyes) { 1 -> 0.24f; 3 -> 0.15f; else -> 0.2f }
        val dark = 0xFF1A0F20.toInt()
        var k = 0
        while (k < eyeAt.size) {
            val ex = eyeAt[k] * r; val ey = eyeAt[k + 1] * r; val rr = er * r
            k += 2
            if (sleep || (tr.happy && !surprise)) {
                if (sleep) ink.closedEye(c, ex, ey, rr, dark) else ink.happyEye(c, ex, ey, rr, dark)
                continue
            }
            if (tr.extra == "" && tr.eyes == 1) {
                // The red one: a single sleepy-menacing eye with two little fangs.
                ink.ell(c, ex, ey, rr * 1.1f, rr * 0.7f * (1f - blink) + 0.5f, dark)
                ink.ell(c, ex + b.lx * rr * 0.3f, ey, rr * 0.3f, rr * 0.3f * (1f - blink), 0xFFFF4D6D.toInt(), 0.9f)
                for (s in intArrayOf(-1, 1)) {
                    ink.path.reset(); ink.path.moveTo(ex + s * rr * 0.6f, ey + rr * 0.35f); ink.path.lineTo(ex + s * rr * 0.45f, ey + rr * 0.7f); ink.path.lineTo(ex + s * rr * 0.3f, ey + rr * 0.35f); ink.path.close()
                    c.drawPath(ink.path, ink.color(Color.WHITE))
                }
                continue
            }
            ink.ell(c, ex, ey, rr, rr * (1f - blink * 0.92f), Color.WHITE)
            ink.r.set(ex - rr, ey - rr * (1f - blink * 0.92f), ex + rr, ey + rr * (1f - blink * 0.92f))
            c.drawOval(ink.r, ink.stroke(dark, r * 0.04f))
            if (blink < 0.5f) {
                val s = if (surprise) 0.55f else 0.48f
                val px = ex + b.lx * rr * 0.4f; val py = ey + b.ly * rr * 0.4f
                ink.ell(c, px, py, rr * s, rr * s, dark)
                ink.ell(c, px - rr * 0.18f, py - rr * 0.18f, rr * 0.14f, rr * 0.14f, Color.WHITE)
            }
        }
        // Blush and freckles.
        ink.ell(c, -r * 0.6f, r * 0.18f, r * 0.14f, r * 0.08f, 0xFFFF7896.toInt(), 0.35f)
        ink.ell(c, r * 0.6f, r * 0.18f, r * 0.14f, r * 0.08f, 0xFFFF7896.toInt(), 0.35f)
        if (tr.freckles) { var j = 0; while (j < FRECKLES.size) { ink.ell(c, FRECKLES[j] * r, FRECKLES[j + 1] * r, r * 0.025f, r * 0.025f, 0xFF8C3C0A.toInt(), 0.5f); j += 2 } }
        // Mouth.
        val my = if (tr.eyes == 1) r * 0.35f else r * 0.32f
        when {
            sleep -> ink.ell(c, 0f, my, r * 0.05f, r * 0.035f, dark)
            surprise || tr.mouth == "o" || sing > 0.3f -> {
                ink.ell(c, 0f, my, r * (0.1f + sing * 0.04f), r * (0.1f + sing * 0.08f), dark)
                ink.ell(c, 0f, my + r * 0.05f, r * 0.06f, r * 0.04f, 0xFFFF7D9A.toInt())
            }
            tr.mouth == "grin" -> {
                ink.path.reset(); ink.path.moveTo(-r * 0.2f, my - r * 0.03f); ink.path.quadTo(0f, my + r * 0.2f, r * 0.2f, my - r * 0.03f); ink.path.close()
                c.drawPath(ink.path, ink.color(dark))
                c.drawRect(-r * 0.15f, my - r * 0.03f, r * 0.15f, my + r * 0.02f, ink.color(Color.WHITE))
            }
            tr.mouth == "fang" -> {
                ink.arc(c, -r * 0.1f, my - r * 0.02f, r * 0.1f, 0.2f, PI.toFloat() - 0.2f, dark, r * 0.055f)
                ink.arc(c, r * 0.1f, my - r * 0.02f, r * 0.1f, 0.2f, PI.toFloat() - 0.2f, dark, r * 0.055f)
            }
            tr.mouth == "smirk" -> {
                ink.path.reset(); ink.path.moveTo(-r * 0.15f, my); ink.path.quadTo(r * 0.05f, my + r * 0.1f, r * 0.2f, my - r * 0.06f)
                c.drawPath(ink.path, ink.stroke(dark, r * 0.055f))
            }
            else -> ink.arc(c, 0f, my - r * 0.06f, r * 0.14f, 0.35f, PI.toFloat() - 0.35f, dark, r * 0.055f)
        }
        // Low battery: little drips.
        if (lowBattery) for (i in 0 until 2) {
            val kk = (time * 0.4f + i * 0.5f + b.seed) % 1f
            ink.ell(c, (i - 0.5f) * r * 0.8f, r * 0.9f + kk * r * 0.4f, r * 0.06f, r * 0.09f, tr.base, 1f - kk)
        }
        c.restore()
    }
}
