package app.pulsify.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * Peachy: a squishy pudding-shaped peach bear drawn in 2D (Android Canvas into a small bitmap that
 * follows him, uploaded as a GL texture each frame). Port of the 2D browser preview.
 * Jelly springs on ears/arms/squash; drinks a battery juice box while charging; a notification or any
 * new sound makes him startle and look up; sound still going after 4 s = music, he dances.
 */
class PeachyScene : Creature {
    override var listener: SceneListener? = null
    override var bottomInset = 0f
    override val busy: Boolean get() = cur != "Idle"

    // ---------------------------------------------------------------- pose + clips
    class Pose {
        var x = 0f; var y = 0f; var sx = 1f; var sy = 1f; var rot = 0f; var bend = 0f; var lookX = 0f; var lookY = 0f
        var earL = 0f; var earR = 0f; var armL = .35f; var armR = .35f; var footL = 0f; var footR = 0f
        var blink = 0f; var happy = 0f; var squint = 0f; var mouth = .25f; var blush = .55f; var pupil = 1f
        fun arr() = floatArrayOf(x, y, sx, sy, rot, bend, lookX, lookY, earL, earR, armL, armR, footL, footR, blink, happy, squint, mouth, blush, pupil)
        fun set(a: FloatArray) { x = a[0]; y = a[1]; sx = a[2]; sy = a[3]; rot = a[4]; bend = a[5]; lookX = a[6]; lookY = a[7]; earL = a[8]; earR = a[9]
            armL = a[10]; armR = a[11]; footL = a[12]; footR = a[13]; blink = a[14]; happy = a[15]; squint = a[16]; mouth = a[17]; blush = a[18]; pupil = a[19] }
        fun breathe(t: Float, a: Float = 1f): Pose { val b = S(t, .32f); sy += .016f * a * b; sx += -.012f * a * b; return this }
        fun twitch(t: Float): Pose { earL += .35f * max(0f, S(t, .21f)).pow(10); earR += -.35f * max(0f, S(t, .17f, .4f)).pow(10); return this }
    }
    private class Clip(val dur: Float, val loop: Boolean = false, val dance: Boolean = false, val f: (Float) -> Pose)

    private val clips: Map<String, Clip> = mapOf(
        "Idle" to Clip(8f, true) { t -> val sw = S(t, .125f); Pose().breathe(t).twitch(t).apply { rot += .025f * sw; armL += .06f * S(t, .32f); armR += .06f * S(t, .32f, .5f); earL += .05f * S(t, .3f); earR += -.05f * S(t, .3f, .3f) } },
        "Walk" to Clip(.6f, true) { t -> val u = t / .6f; val sw = sin(TAU * u); val bo = abs(sin(TAU * u))
            Pose().apply { rot = .13f * sw; y = .03f * bo; sy = 1 - .04f * (1 - bo); sx = 1 + .03f * (1 - bo); footL = .06f * max(0f, sin(TAU * u)); footR = .06f * max(0f, -sin(TAU * u))
                armL = .5f + .35f * sw; armR = .5f - .35f * sw; earL = -.15f * sw; earR = -.15f * sw; mouth = .3f } },
        "Wave" to Clip(2.8f) { t -> val a = win(t, .1f, .4f, 2.3f, 2.7f); val w = S(t, 2.4f) * win(t, .4f, .55f, 2.1f, 2.3f)
            Pose().apply { armR = mix(.35f, 2.55f, a) + .4f * w; rot = -.07f * a; happy = a; mouth = .25f + .25f * a; earR = -.2f * a; lookY = .2f * a }.breathe(t) },
        "Hop" to Clip(1.25f) { t -> val crouch = win(t, 0f, .22f, .25f, .32f); val air = sin(PI.toFloat() * clamp((t - .3f) / .42f, 0f, 1f)); val land = win(t, .7f, .76f, .8f, 1.05f)
            Pose().apply { y = .42f * air; sy = 1 - .16f * crouch + .14f * air * (1 - air * .6f) - .2f * land; sx = 1 + .14f * crouch - .08f * air + .18f * land
                armL = .35f + 1.6f * air; armR = .35f + 1.6f * air; earL = .5f * crouch - .6f * air + .5f * land; earR = -.5f * crouch + .6f * air - .5f * land
                footL = .05f * air; footR = .05f * air; happy = win(t, .3f, .4f, 1f, 1.2f); mouth = .25f + .4f * air } },
        "HappyBounce" to Clip(2.4f) { t -> val a = win(t, 0f, .2f, 2.1f, 2.4f); val b = abs(S(t, 1.25f)); val land = (1 - b).pow(6)
            Pose().apply { y = .2f * b * a; sy = 1 + (.08f * b - .14f * land) * a; sx = 1 + (-.05f * b + .12f * land) * a; armL = mix(.35f, 2.4f + .3f * b, a); armR = armL
                earL = (.4f * land - .3f * b) * a; earR = (-.4f * land + .3f * b) * a; rot = .05f * S(t, .625f) * a; happy = a; mouth = .3f + .45f * a * b; blush = .55f + .35f * a } },
        "Shy" to Clip(3.2f) { t -> val a = win(t, .1f, .5f, 2.6f, 3.1f); val s = S(t, .7f)
            Pose().apply { armL = mix(.35f, -2f, a); armR = armL; rot = .09f * s * a; lookX = -.4f * a; lookY = -.35f * a; blush = .55f + .45f * a; happy = a * win(t, 1.2f, 1.4f, 2.3f, 2.5f)
                earL = .3f * a; earR = -.3f * a; mouth = .12f; sy = 1 - .03f * a }.breathe(t) },
        "Love" to Clip(3.4f) { t -> val a = win(t, .2f, .6f, 2.8f, 3.3f); val s = S(t, .6f)
            Pose().apply { armL = mix(.35f, -1.35f, a); armR = armL; rot = .12f * s * a; happy = a; blush = .55f + .45f * a; mouth = .3f; earL = -.15f * s * a; earR = -.15f * s * a
                sy = 1 + .02f * abs(S(t, 1.2f)) * a }.breathe(t) },
        "Giggle" to Clip(2.4f) { t -> val a = win(t, .05f, .25f, 2f, 2.4f); val sh = S(t, 7f)
            Pose().apply { armL = mix(.35f, -.9f, a); armR = armL; y = .012f * abs(sh) * a; sy = 1 - .04f * abs(sh) * a; sx = 1 + .03f * abs(sh) * a; rot = .05f * sh * a
                happy = a; mouth = .3f + .5f * abs(sh) * a; earL = .3f * sh * a; earR = .3f * sh * a; blush = .55f + .35f * a } },
        "HeadTilt" to Clip(3f) { t -> val a = win(t, .05f, .35f, 1.3f, 1.55f); val b = win(t, 1.55f, 1.8f, 2.6f, 3f)
            Pose().apply { rot = .2f * a - .2f * b; earL = .5f * b - .1f * a; earR = -.5f * a + .1f * b; lookX = .25f * a - .25f * b; lookY = .15f * (a + b); mouth = .15f; pupil = 1 + .15f * (a + b) }.breathe(t) },
        "EarWiggle" to Clip(2.2f) { t -> val a = win(t, 0f, .2f, 1.8f, 2.2f); val w = S(t, 3.5f)
            Pose().apply { earL = .55f * w * a; earR = .55f * w * a; rot = .03f * w * a; happy = .9f * a; mouth = .35f }.breathe(t) },
        "Poke" to Clip(1.1f) { t -> val hit = win(t, 0f, .05f, .1f, .35f); val wob = exp(-t * 4) * sin(t * 22)
            Pose().apply { sy = 1 - .2f * hit + .06f * wob; sx = 1 + .17f * hit - .05f * wob; rot = .05f * wob; squint = win(t, 0f, .05f, .4f, .55f); mouth = .25f + .5f * win(t, .02f, .06f, .3f, .45f)
                earL = .7f * hit - .2f * wob; earR = -.7f * hit + .2f * wob; armL = .35f + .6f * hit; armR = armL } },
        "Yawn" to Clip(2.8f) { t -> val a = win(t, .2f, .7f, 1.7f, 2.2f)
            Pose().apply { armL = mix(.35f, 2.85f, a); armR = armL; sy = 1 + .1f * a; sx = 1 - .07f * a; rot = .03f * S(t, .5f); mouth = .2f + .95f * win(t, .4f, .8f, 1.5f, 1.9f); blink = .95f * win(t, .5f, .75f, 1.6f, 1.9f)
                earL = -.35f * a; earR = .35f * a; lookY = .3f * a }.breathe(t) },
        "Sleepy" to Clip(3f) { t -> val a = win(t, .1f, 1.3f, 2.05f, 2.15f); val wake = win(t, 2.1f, 2.2f, 2.4f, 2.9f)
            Pose().apply { rot = .09f * a; sy = 1 - .06f * a + .06f * wake; sx = 1 + .04f * a; blink = a * .97f; earL = .45f * a - .5f * wake; earR = -.45f * a + .5f * wake; armL = .2f; armR = .2f
                pupil = 1 - .3f * wake; mouth = .12f + .2f * wake; y = .06f * wake } },
        "LookAround" to Clip(3.2f) { t -> val l = win(t, .1f, .45f, 1.3f, 1.7f); val r = win(t, 1.7f, 2.1f, 2.8f, 3.2f)
            Pose().apply { lookX = -.85f * l + .85f * r; rot = -.04f * l + .04f * r; earL = .2f * l; earR = -.2f * r; mouth = .15f }.breathe(t) },
        "Tumble" to Clip(2.8f) { t -> val fall = sm(clamp(t / .45f, 0f, 1f)); val back = win(t, .45f, .5f, 1.6f, 1.95f); val up = sin(PI.toFloat() * clamp((t - 1.85f) / .35f, 0f, 1f))
            val keep = 1 - sm((t - 1.6f) / .35f)
            Pose().apply { rot = -1.45f * fall * keep + .1f * sin(t * 9) * back; x = -.12f * fall * keep; y = .25f * up
                sy = 1 - .1f * back + .12f * up - .15f * win(t, 2.2f, 2.25f, 2.3f, 2.6f); sx = 1 + .08f * back + .12f * win(t, 2.2f, 2.25f, 2.3f, 2.6f)
                armL = .35f + 2 * back; armR = armL; footL = .12f * back; footR = footL; squint = win(t, .3f, .4f, .9f, 1.05f); happy = win(t, 2.1f, 2.3f, 2.6f, 2.8f); mouth = .3f + .5f * win(t, .2f, .3f, .6f, .8f)
                earL = .5f * back; earR = .5f * back } },
        "DanceBounce" to Clip(4f, true, true) { t -> val b = t * 2; val d = abs(S(b, .5f))
            Pose().apply { y = .07f * d; sy = 1 + .05f * d - .08f * (1 - d).pow(4); sx = 1 - .03f * d + .07f * (1 - d).pow(4); rot = .1f * S(b, .25f); armL = 1.6f + .8f * S(b, .25f); armR = 1.6f - .8f * S(b, .25f)
                earL = .3f * S(b, .5f); earR = .3f * S(b, .5f, .5f); happy = 1f; mouth = .45f + .2f * d } },
        "DanceSway" to Clip(4f, true, true) { t -> val b = t * 2; val s = S(b, .25f)
            Pose().apply { x = .05f * s; rot = .16f * s; bend = .12f * s; armL = 2.5f + .35f * s; armR = 2.5f - .35f * s; sy = 1 - .03f * abs(S(b, .5f)); earL = -.3f * s; earR = -.3f * s; happy = 1f; mouth = .35f; lookX = .3f * s } },
        "DanceWiggle" to Clip(4f, true, true) { t -> val b = t * 2; val w = S(b, 1f)
            Pose().apply { bend = -.14f * w; rot = .05f * w; x = .02f * w; sy = .96f; sx = 1.03f; armL = -.6f + .5f * w; armR = -.6f - .5f * w; footL = .03f * max(0f, w); footR = .03f * max(0f, -w)
                earL = .35f * w; earR = .35f * w; happy = 1f; mouth = .4f; lookX = -.3f * w } },
        "DanceRoof" to Clip(4f, true, true) { t -> val b = t * 2; val pu = abs(S(b, .5f))
            Pose().apply { armL = 2.6f + .45f * pu; armR = armL; y = .04f * pu; sy = 1 + .04f * pu - .07f * (1 - pu).pow(4); rot = .07f * S(b, .25f); earL = -.35f * pu; earR = .35f * pu; happy = 1f; mouth = .25f + .55f * pu; blush = .8f } },
    )
    private val LIFE = listOf("Wave", "HeadTilt", "EarWiggle", "LookAround", "Yawn", "Shy", "Love", "HappyBounce", "Hop", "Sleepy")
    private val TAPS = listOf("Poke", "Giggle", "Hop", "HappyBounce", "Shy", "Love", "Poke")
    private val DANCES = listOf("DanceBounce", "DanceSway", "DanceWiggle", "DanceRoof")

    // ---------------------------------------------------------------- state
    private var time = 0f; private var beatClock = 0f
    private var palX = 0f; private var walkTo = Float.NaN
    private var cur = "Idle"; private var curT = 0f; private var from: String? = null; private var fromT = 0f; private var fade = 1f
    private var nextLife = 5f; private var blinkT = 0f; private var nextBlink = 2f
    private val look = FloatArray(2); private val lookT = FloatArray(2); private var lastTouch = -9f
    private val taps = ArrayList<Float>()
    private var held = false; private var hy = 0f; private var hvy = 0f; private var dangle = 0f; private var dangleV = 0f
    private var box = 0f; private var sip = 0f; private var battery = .5f; private var charging = false
    private var notifT0 = -99f; private var notifBanner = false; private var notifOn = false
    private var sndStart = -9f; private var sndLast = -9f; private var sndReacted = false; private var musicOn = false; private var bass = 0f
    private val pose = Pose()
    private val sprK = arrayOf(floatArrayOf(5.5f, .22f), floatArrayOf(5.5f, .22f), floatArrayOf(7f, .35f), floatArrayOf(7f, .35f), floatArrayOf(6f, .2f), floatArrayOf(6f, .2f), floatArrayOf(5f, .35f), floatArrayOf(4f, .25f))
    private val sprX = floatArrayOf(0f, 0f, .35f, .35f, 1f, 1f, 0f, 0f); private val sprV = FloatArray(8)

    private class Part(val kind: Int, var x: Float, var y: Float, var vx: Float, val vy: Float, val max: Float, val s: Float, val r: Float) { var life = 0f }
    private val parts = ArrayList<Part>()
    private fun emit(kind: Int, x: Float, y: Float, n: Int = 1) { repeat(n) {
        parts += Part(kind, x + (rnd() - .5f) * .15f, y + (rnd() - .5f) * .05f, (rnd() - .5f) * .25f, .25f + rnd() * .25f, 1.4f + rnd() * .8f, .045f + rnd() * .03f, (rnd() - .5f) * .6f) } }

    // layout (uv: centred, short side = 1, y up)
    private var w = 1; private var h = 1; private var halfW = .5f; private var halfH = 1f; private var ground = Float.NaN
    private val U get() = min(halfW * 2 * .62f, halfH * 2 * .3f)
    private val gy get() = if (ground.isNaN()) -halfH * .28f else ground

    private fun play(n: String) { val c = clips[n] ?: return; from = cur; fromT = curT; fade = 0f; cur = n; curT = if (c.dance) beatClock % c.dur else 0f }
    private fun sample(n: String, t: Float): Pose { val c = clips[n]!!; return c.f(if (c.loop) t % c.dur else min(t, c.dur)) }
    private fun lookUp(withBanner: Boolean) {
        notifT0 = time; notifBanner = withBanner; notifOn = true; walkTo = Float.NaN
        if (cur != "Idle" && clips[cur]?.dance != true) play("Idle")
        emit(BANG, palX + .05f, 1.12f, 1)
    }

    // ---------------------------------------------------------------- input
    private var ptrOn = false; private var px0 = 0f; private var py0 = 0f; private var pxu = 0f; private var pyu = 0f; private var ptrT0 = 0f; private var onPal = false; private var moved = 0f
    private fun bodyOf(nx: Float, ny: Float): FloatArray { val u = (nx - .5f) * 2 * halfW; val v = (.5f - ny) * 2 * halfH; return floatArrayOf((u - palX * U) / U, (v - gy) / U) }
    override fun touchDown(nx: Float, ny: Float): Boolean {
        val b = bodyOf(nx, ny); ptrOn = true; px0 = nx; py0 = ny; pxu = nx; pyu = ny; ptrT0 = time; moved = 0f; lastTouch = time
        onPal = abs(b[0]) < .5f && b[1] > -.05f && b[1] < 1.05f; return onPal
    }
    override fun touchMove(nx: Float, ny: Float): Boolean {
        if (!ptrOn) return false; pxu = nx; pyu = ny; lastTouch = time
        moved = max(moved, kotlin.math.hypot((nx - px0) * 2 * halfW, (ny - py0) * 2 * halfH))
        if (onPal && moved > .02f && !held) { held = true; walkTo = Float.NaN; hy = 0f; hvy = 0f; emit(SPARK, palX, .9f, 3) }
        return held
    }
    override fun touchUp(): Boolean {
        if (!ptrOn) return false; ptrOn = false
        if (held) { held = false; hvy = 0f; play("Poke"); return true }
        if (onPal && moved < .02f && time - ptrT0 > .5f) { play("Love"); emit(HEART, palX, .95f, 4); return true }
        return false
    }
    override fun tapAt(nx: Float, ny: Float): Boolean {
        val b = bodyOf(nx, ny)
        if (abs(b[0]) < .5f && b[1] > -.05f && b[1] < 1.05f) { tapPal(); return true }
        val lim = halfW / U - .55f; walkTo = clamp(palX + b[0], -lim, lim); return true
    }
    override fun longPressAt(nx: Float, ny: Float): Boolean { val b = bodyOf(nx, ny); if (abs(b[0]) < .5f && b[1] > -.05f && b[1] < 1.05f) { play("Love"); emit(HEART, palX, .95f, 4); return true }; return false }
    override fun greet() { if (cur == "Idle") play("Wave") }
    override fun swiped(dir: Float, fast: Boolean) { if (cur == "Idle") play(if (fast) "Poke" else "LookAround") }
    private fun tapPal() {
        taps.removeAll { time - it > .7f }; taps += time; walkTo = Float.NaN
        if (taps.size >= 3) { taps.clear(); play("Tumble"); emit(STAR, palX, .9f, 5); listener?.onDiscovery("peachy tumble"); return }
        val n = TAPS[Random.nextInt(TAPS.size)]; play(n)
        if (n == "Love" || n == "Shy") emit(HEART, palX, .95f, 3) else emit(SPARK, palX, .85f, 3)
        listener?.onBondXp(1)
    }
    override fun setGround(y: Float) { ground = y }
    override fun perform(trick: String): Boolean {
        val n = when (trick) {
            "notify" -> { lookUp(true); return true }
            "hello" -> "Wave"; "giggle" -> "Giggle"; "wobble" -> "Poke"; "stretch" -> "Yawn"; "party" -> "HappyBounce"; "star" -> "Hop"; "bubble" -> "EarWiggle"; "bird" -> "LookAround"
            else -> trick.replaceFirstChar { it.uppercase() }
        }
        if (clips[n] == null) return false
        walkTo = Float.NaN; play(n)
        if (n == "Love" || n == "Shy") emit(HEART, palX, .95f, 3); if (n == "Sleepy") emit(ZZZ, palX + .3f, 1.05f, 1)
        return true
    }

    // ---------------------------------------------------------------- update
    override fun update(dt0: Float, input: SceneInput) {
        val dt = min(dt0, .05f); time += dt; beatClock += dt
        if (input.charging && !charging) { emit(SPARK, palX, .5f, 5); play("Hop") }
        charging = input.charging; battery = input.battery.coerceIn(0f, 1f)
        val a = input.audio; val lvl = if (a.active) a.level else 0f
        bass += ((if (a.active) a.bass else 0f) - bass) * min(1f, dt * 20)
        // sound timeline: first 3 s of any new sound = "what was that?" look-up; still going after 4 s = music, dance
        if (lvl > .02f) { if (time - sndLast > .8f) { sndStart = time; sndReacted = false }; sndLast = time }
        val sndDur = if (time - sndLast < .8f) time - sndStart else 0f
        if (sndDur > 0 && !sndReacted && !held) { sndReacted = true; if (!notifOn || time - notifT0 > 3) lookUp(false) }
        musicOn = sndDur > 4f
        if (!walkTo.isNaN() && !held) {
            val d = walkTo - palX; if (cur != "Walk") play("Walk")
            val st = min(abs(d), .55f * dt); palX += sign(d) * st; if (abs(d) < .01f) { walkTo = Float.NaN; play(if (rnd() < .5f) "Wave" else "HappyBounce") }
        }
        curT += dt
        val c = clips[cur]!!
        if (!c.loop && curT >= c.dur) play(if (!walkTo.isNaN()) "Walk" else "Idle")
        val isDance = clips[cur]!!.dance
        if (musicOn && !charging && !held && walkTo.isNaN() && (cur == "Idle" || (isDance && curT % 4 > 3.95f && rnd() < .5f))) { val ds = DANCES.filter { it != cur }; play(ds[Random.nextInt(ds.size)]) }
        if (!musicOn && isDance) play("Idle")
        nextLife -= dt
        if (nextLife < 0 && cur == "Idle" && !held && time - lastTouch > 2) { nextLife = 5 + rnd() * 5; val n = LIFE[Random.nextInt(LIFE.size)]; play(n)
            if (n == "Love") emit(HEART, palX, .95f, 3); if (n == "Sleepy") emit(ZZZ, palX + .25f, 1f, 1) }
        // blend
        val p = sample(cur, curT)
        val f = from
        if (f != null && fade < 1f) { fade = min(1f, fade + dt / .25f); fromT += dt; val fa = sample(f, fromT).arr(); val pa = p.arr(); val k = sm(fade); for (i in pa.indices) pa[i] = fa[i] + (pa[i] - fa[i]) * k; p.set(pa) }
        // held: the finger lifts him; he stretches, legs and ears dangle
        if (held && ptrOn) {
            val b = bodyOf(pxu, pyu); val tx = palX + b[0]; val ty = b[1] - .85f; val ox = palX
            palX += (tx - palX) * min(1f, dt * 14); hy += (max(0f, ty) - hy) * min(1f, dt * 14)
            val vx = (palX - ox) / max(dt, 1e-3f); dangleV += (-vx * .06f - dangle * 30 - dangleV * 4) * dt; dangle += dangleV * dt
            p.y = hy; p.sy = 1.1f; p.sx = .93f; p.rot = clamp(dangle, -.5f, .5f); p.bend = clamp(-vx * .05f, -.25f, .25f); p.armL = 1.9f + .4f * sin(time * 9); p.armR = 1.9f + .4f * sin(time * 9 + 1)
            p.footL = -.03f; p.footR = -.03f; p.earL = .4f + dangle * .8f; p.earR = -.4f + dangle * .8f; p.mouth = .55f; p.happy = 0f; p.pupil = 1.2f
        } else if (hy > 0) {
            hvy -= 6 * dt; hy += hvy * dt; if (hy <= 0) { hy = 0f; hvy = 0f; sprV[4] -= 3; sprV[5] += 2.5f; emit(SPARK, palX, .1f, 4) }
            p.y += hy; p.sy *= 1.06f; p.armL = 2f; p.armR = 2f
        }
        val lim = halfW / U - .45f; palX = palX.coerceIn(-lim, lim)
        // notification / new sound: startle, look up at the top of the screen, point at it, grin
        if (notifOn && !held) { val u = time - notifT0; val D = if (notifBanner) 3.4f else 2.6f
            if (u > D) notifOn = false else {
                val aa = win(u, 0f, .12f, D - .5f, D); val st = win(u, 0f, .06f, .12f, .35f); val pt = if (notifBanner) win(u, .7f, 1f, 2.4f, 2.8f) else 0f
                p.sy += -.12f * st + .04f * aa; p.sx += .1f * st; p.y += .05f * win(u, .05f, .15f, .2f, .4f)
                p.lookY = mix(p.lookY, 1.15f, aa); p.lookX = mix(p.lookX, .15f, aa); p.pupil = mix(p.pupil, 1.35f, aa); p.blink = 0f; p.happy *= 1 - aa; p.squint *= 1 - aa
                p.mouth = mix(p.mouth, .55f * (1 - pt) + .45f * pt, aa); p.earL += .5f * st - .25f * aa; p.earR -= .5f * st - .25f * aa; p.rot += -.06f * aa
                if (pt > 0) { p.armR = mix(p.armR, 2.75f + .1f * S(u, 3f), pt); p.happy = max(p.happy, pt * win(u, 1.4f, 1.6f, 2.3f, 2.5f)) } } }
        // charging: holds a battery juice box and sips it
        box += ((if (charging && !held) 1f else 0f) - box) * min(1f, dt * 5)
        if (box > .02f && !held) { val b = box; val u = time % 2.6f; val sp = win(u, .2f, .4f, 1.3f, 1.5f); val ahh = win(u, 1.55f, 1.75f, 2.2f, 2.5f); val full = battery >= .995f
            p.armL = mix(p.armL, -1.15f + .05f * S(time, .7f), b); p.armR = mix(p.armR, -1.15f + .05f * S(time, .7f, .5f), b)
            if (!full && !notifOn) { p.mouth = mix(p.mouth, .05f + .4f * ahh, b); p.blink = max(p.blink, .45f * sp * b); p.happy = max(p.happy, ahh * b); p.blush += .35f * sp * b
                p.sy += (.02f * sp - .025f * ahh) * b; p.earL += .25f * ahh * S(time, 4f) * b; p.earR += .25f * ahh * S(time, 4f) * b; p.lookY = mix(p.lookY, -.35f, b * sp) }
            sip = if (full) 0f else sp * b
        } else sip = 0f
        if (!charging && input.lowBattery && !notifOn && !held) { p.blink = max(p.blink, .5f); p.sy -= .04f; p.sx += .03f; p.earL += .45f; p.earR -= .45f; p.mouth = min(p.mouth, .1f); p.armL -= .2f; p.armR -= .2f }
        p.sy += .05f * bass; p.sx -= .03f * bass
        // gaze
        if (ptrOn && !held) { val b = bodyOf(pxu, pyu); lookT[0] = clamp(b[0] * 1.3f, -1f, 1f); lookT[1] = clamp((b[1] - .6f) * 1.2f, -1f, 1f) }
        else if (time - lastTouch > 2.5f && rnd() < dt * .4f) { lookT[0] = (rnd() - .5f) * .8f; lookT[1] = (rnd() - .5f) * .4f }
        look[0] += (lookT[0] + p.lookX - look[0]) * min(1f, dt * 10); look[1] += (lookT[1] + p.lookY - look[1]) * min(1f, dt * 10)
        nextBlink -= dt; if (nextBlink < 0) { blinkT = 1f; nextBlink = 2 + rnd() * 3.5f; if (rnd() < .2f) nextBlink = .25f }
        blinkT = max(0f, blinkT - dt * 7); p.blink = max(p.blink, sin(blinkT * PI.toFloat()))
        // jelly springs: ears, arms, squash, tilt
        val tg = floatArrayOf(p.earL, p.earR, p.armL, p.armR, p.sy, p.sx, p.rot, p.bend)
        for (k in 0 until 8) { val fq = sprK[k][0]; val z = sprK[k][1]; val wv = TAU * fq
            repeat(3) { val acc = wv * wv * (tg[k] - sprX[k]) - 2 * z * wv * sprV[k]; sprV[k] += acc * dt / 3; sprX[k] += sprV[k] * dt / 3 } }
        p.earL = sprX[0]; p.earR = sprX[1]; p.armL = sprX[2]; p.armR = sprX[3]; p.sy = sprX[4]; p.sx = sprX[5]; p.rot = sprX[6]; p.bend = sprX[7]
        pose.set(p.arr())
        for (q in parts) { q.life += dt; q.x += q.vx * dt; q.y += q.vy * dt; q.vx *= exp(-dt) }
        parts.removeAll { it.life > it.max }
        if (cur == "Sleepy" && rnd() < dt * .8f) emit(ZZZ, palX + .3f, 1.05f, 1)
        if (clips[cur]!!.dance && bass > .55f && rnd() < dt * 3) emit(NOTE, palX + (rnd() - .5f) * .9f, 1f, 1)
        if (cur == "Love" && rnd() < dt * 2) emit(HEART, palX, 1f, 1)
    }

    // ---------------------------------------------------------------- GL plumbing
    private var prog = 0; private var vbo = 0; private var tex = 0; private var bmp: Bitmap? = null; private var cv: Canvas? = null
    private var uRect = 0; private var uTex = 0
    override fun onSurfaceCreated() {
        prog = Gl.program(VS, FS); uRect = GLES20.glGetUniformLocation(prog, "uRect"); uTex = GLES20.glGetUniformLocation(prog, "uTex")
        val ids = IntArray(1); GLES20.glGenBuffers(1, ids, 0); vbo = ids[0]
        val q = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        val fb = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().put(q); fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo); GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 32, fb, GLES20.GL_STATIC_DRAW)
        tex = 0
    }
    override fun onSurfaceChanged(width: Int, height: Int) {
        w = max(1, width); h = max(1, height); val m = min(w, h).toFloat(); halfW = .5f * w / m; halfH = .5f * h / m
    }
    override fun release() {
        if (prog != 0) GLES20.glDeleteProgram(prog); if (vbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
        if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
        prog = 0; vbo = 0; tex = 0; bmp?.recycle(); bmp = null; cv = null
    }

    // box in body units that follows him
    private val BX = 1.25f; private val BY0 = -.35f; private val BY1 = 1.95f
    override fun draw(input: SceneInput) {
        val m = min(w, h); val u = U
        val cx = palX + pose.x; val cyb = pose.y
        val bwU = 2 * BX; val bhU = BY1 - BY0
        val pxPerUnit = min(u * m, 560f / bwU)     // render resolution: at most ~560 px wide
        val bw = (bwU * pxPerUnit).roundToInt().coerceAtLeast(8); val bh = (bhU * pxPerUnit).roundToInt().coerceAtLeast(8)
        var b = bmp
        if (b == null || b.width != bw || b.height != bh) {
            b?.recycle(); b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888); bmp = b; cv = Canvas(b)
            if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0); tex = 0
        }
        val c = cv!!
        c.drawColor(0, PorterDuff.Mode.CLEAR)
        c.save()
        // map body units -> bitmap px: x right, y down
        val S = pxPerUnit; val x0 = cx - BX; val y1 = cyb + BY1
        c.translate(-x0 * S, y1 * S)       // now (bodyX * S, -bodyY * S)
        drawGlow(c, S, cx)
        drawPal(c, S)
        for (q in parts) drawPart(c, q, S)
        c.restore()
        // upload
        if (tex == 0) { val t = IntArray(1); GLES20.glGenTextures(1, t, 0); tex = t[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
        } else { GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex); GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, b) }
        // quad in uv -> NDC
        val vp = IntArray(4); GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0); val mm = min(vp[2], vp[3]).toFloat()
        val sxN = 2f * mm / vp[2]; val syN = 2f * mm / vp[3]
        val ux0 = x0 * u; val ux1 = (cx + BX) * u; val vy0 = gy + (cyb + BY0) * u; val vy1 = gy + y1 * u
        GLES20.glUseProgram(prog); GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex); GLES20.glUniform1i(uTex, 0)
        GLES20.glUniform4f(uRect, ux0 * sxN, vy0 * syN, ux1 * sxN, vy1 * syN)
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo); GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(0); GLES20.glDisable(GLES20.GL_BLEND); GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    // ---------------------------------------------------------------- drawing (body units, y up inside drawPal)
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG); private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path(); private val rect = RectF()
    private fun fillSolid(col: Int, alpha: Float = 1f): Paint { fp.shader = null; fp.color = col; fp.alpha = (Color.alpha(col) * alpha.coerceIn(0f, 1f)).roundToInt(); return fp }
    private fun stroke(col: Int, width: Float): Paint { sp.shader = null; sp.color = col; sp.strokeWidth = width; sp.strokeCap = Paint.Cap.ROUND; sp.strokeJoin = Paint.Join.ROUND; sp.pathEffect = null; return sp }
    private fun oval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, p: Paint) { rect.set(x - rx, y - ry, x + rx, y + ry); c.drawOval(rect, p) }
    private fun radial(x: Float, y: Float, r: Float, cols: IntArray, stops: FloatArray? = null) = RadialGradient(x, y, max(r, 1e-4f), cols, stops, Shader.TileMode.CLAMP)
    private fun radial2(fx: Float, fy: Float, r0: Float, x: Float, y: Float, r1: Float, cols: IntArray, stops: FloatArray? = null): Shader {
        // canvas has no two-circle gradient: approximate with a gradient centred between the two circles
        val k = .5f; return RadialGradient(mix(fx, x, k), mix(fy, y, k), max(r1, 1e-4f), cols, stops ?: FloatArray(cols.size) { i -> if (cols.size == 1) 0f else (r0 / r1) + (1 - r0 / r1) * i / (cols.size - 1) }, Shader.TileMode.CLAMP)
    }

    private fun drawGlow(c: Canvas, S: Float, cx: Float) {
        // a soft warm/violet glow behind him on the AMOLED black
        c.save(); c.scale(S, -S)
        fp.shader = radial(cx, .5f, 1.6f, intArrayOf(Color.argb(((.1f + .12f * bass) * 255).roundToInt().coerceIn(0, 255), 157, 78, 221), Color.argb(10, 255, 179, 107), 0), floatArrayOf(0f, .5f, 1f))
        fp.alpha = 255; c.drawCircle(cx, .5f, 1.6f, fp); fp.shader = null
        c.restore()
    }

    private fun bodyPath(p: Pose) {
        path.reset(); val n = 72
        for (i in 0 until n) { val th = i.toFloat() / n * TAU; val co = cos(th); val s = sin(th)
            var x = .45f * co * (1 - .07f * s * s * (if (s > 0) 1f else 0f)) * (1 + .05f * max(0f, -s)); var y = .5f + .5f * s * (if (s < 0) .95f else 1f)
            y = max(.02f, y); x += p.bend * y * y
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        path.close()
    }
    private fun capsule(len: Float, wd: Float) {
        path.reset(); path.moveTo(-wd / 2, 0f); path.lineTo(-wd / 2, -len + wd / 2)
        rect.set(-wd / 2, -len, wd / 2, -len + wd); path.arcTo(rect, 180f, 180f)   // y-up space: arc over the far end
        path.lineTo(wd / 2, 0f); rect.set(-wd / 2, -wd / 2, wd / 2, wd / 2); path.arcTo(rect, 0f, 180f); path.close()
    }
    private fun drawArm(c: Canvas, side: Float, a: Float, p: Pose) {
        c.save(); c.translate(side * .38f + p.bend * .23f, .47f); c.rotate(deg(side * a))
        val len = .24f; val wd = .13f; capsule(len, wd)
        fp.shader = LinearGradient(-wd / 2, 0f, wd / 2, 0f, if (side < 0) SKIN_HI else SKIN, if (side < 0) SKIN else SKIN_LO, Shader.TileMode.CLAMP); fp.alpha = 255
        c.drawPath(path, fp); fp.shader = null; c.drawPath(path, stroke(Color.argb(89, 150, 70, 60), .006f)); c.restore()
    }
    private fun drawEar(c: Canvas, side: Float, a: Float, p: Pose) {
        c.save(); c.translate(side * .27f + p.bend * .9f, .9f); c.rotate(deg(side * -.25f - a))
        fp.shader = radial2(-.03f, .1f, .01f, 0f, .07f, .11f, intArrayOf(SKIN_HI, EAR)); fp.alpha = 255; oval(c, 0f, .075f, .095f, .088f, fp); fp.shader = null
        oval(c, .005f, .07f, .052f, .048f, fillSolid(EAR_IN, .7f)); c.restore()
    }
    private fun drawEye(c: Canvas, cx: Float, cy: Float, p: Pose, side: Float) {
        val r = .1f; val lx = look[0]; val ly = look[1]
        c.save(); c.translate(cx, cy)
        if (p.happy > .5f || p.squint > .5f) {
            path.reset()
            if (p.squint > .5f) { path.moveTo(-side * .06f, .05f); path.lineTo(side * .05f, 0f); path.lineTo(-side * .06f, -.05f) }
            else { path.moveTo(-.07f, -.02f); path.quadTo(0f, .09f, .07f, -.02f) }
            c.drawPath(path, stroke(LINE, .028f)); c.restore(); return
        }
        path.reset(); rect.set(-r, -r * 1.04f, r, r * 1.04f); path.addOval(rect, Path.Direction.CW)
        c.save(); c.clipPath(path)
        c.drawPath(path, fillSolid(0xFFFBF6F2.toInt()))
        val ix = .012f + lx * .03f; val iy = .01f + ly * .025f; val ir = r * .86f
        fp.shader = radial2(ix, iy - ir * .45f, ir * .05f, ix, iy, ir, intArrayOf(0xFFD7C25C.toInt(), 0xFF6E6A27.toInt(), 0xFF2B2A0E.toInt(), 0xFF121206.toInt()), floatArrayOf(0f, .35f, .75f, 1f)); fp.alpha = 255
        c.drawCircle(ix, iy, ir, fp); fp.shader = null
        c.drawCircle(ix, iy, ir * .48f * p.pupil, fillSolid(0xFF050505.toInt()))
        val wht = fillSolid(Color.WHITE); c.drawCircle(ix - ir * .34f, iy + ir * .36f, ir * .23f, wht); c.drawCircle(ix + ir * .33f, iy - ir * .32f, ir * .09f, wht)
        c.drawCircle(ix + ir * .05f, iy + ir * .55f, ir * .05f, fillSolid(Color.WHITE, .7f))
        val bl = clamp(p.blink, 0f, 1f)
        if (bl > .02f) { val yl = r * 1.05f - bl * r * 2.15f
            path.reset(); path.moveTo(-r * 1.2f, r * 1.2f); path.lineTo(r * 1.2f, r * 1.2f); path.lineTo(r * 1.2f, yl); path.quadTo(0f, yl - r * .25f * (1 - bl), -r * 1.2f, yl); path.close()
            c.drawPath(path, fillSolid(SKIN))
            if (bl > .25f) { path.reset(); path.moveTo(-r * 1.1f, yl); path.quadTo(0f, yl - r * .25f * (1 - bl), r * 1.1f, yl); c.drawPath(path, stroke(LINE, .016f)) } }
        c.restore()
        rect.set(-r, -r * 1.04f, r, r * 1.04f); c.drawOval(rect, stroke(Color.argb(115, 90, 42, 40), .008f))
        c.restore()
    }
    private fun drawMouth(c: Canvas, p: Pose) {
        val m = clamp(p.mouth, 0f, 1.2f)
        c.save(); c.translate(look[0] * .035f, .455f + look[1] * .02f)
        if (m < .08f) { path.reset(); path.moveTo(-.035f, .01f); path.quadTo(-.017f, -.015f, 0f, .005f); path.quadTo(.017f, -.015f, .035f, .01f); c.drawPath(path, stroke(LINE, .012f)); c.restore(); return }
        val wd = .035f + .012f * m; val ht = .012f + .055f * m
        path.reset(); path.moveTo(-wd, .006f); path.quadTo(-wd * .5f, -.01f, 0f, .002f); path.quadTo(wd * .5f, -.01f, wd, .006f); path.quadTo(wd * .9f, -ht, 0f, -ht); path.quadTo(-wd * .9f, -ht, -wd, .006f); path.close()
        c.drawPath(path, fillSolid(0xFF6E1F2B.toInt())); c.save(); c.clipPath(path)
        oval(c, 0f, -ht * .95f, wd * .7f, ht * .55f, fillSolid(0xFFFF8DA1.toInt())); c.restore()
        c.drawPath(path, stroke(LINE, .009f)); c.restore()
    }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    /** Text drawn at a sane font size: [size] in local units. */
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Int, alpha: Float = 1f, flipY: Boolean = true) {
        c.save(); c.translate(x, y); val k = size / 100f; c.scale(k, if (flipY) -k else k)
        textP.color = col; textP.alpha = (255 * alpha.coerceIn(0f, 1f)).roundToInt(); textP.textSize = 100f; c.drawText(s, 0f, 0f, textP); c.restore()
    }
    private fun drawBox(c: Canvas) {
        val b = sm(box); val lv = battery; val full = lv >= .995f
        c.save(); c.translate(look[0] * .03f, .2f); c.scale(b, b)
        val wd = .2f; val ht = .25f
        val mx = look[0] * .015f; val my = .255f + look[1] * .02f
        path.reset(); path.moveTo(.045f, ht - .02f); path.lineTo(.045f, ht + .07f); path.quadTo(.045f, my, mx, my)
        c.drawPath(path, stroke(Color.WHITE, .028f))
        val st = stroke(0xFFFF6FA0.toInt(), .012f); st.pathEffect = DashPathEffect(floatArrayOf(.018f, .018f), 0f); c.drawPath(path, st); st.pathEffect = null
        if (sip > .1f) for (i in 0 until 3) { val k = (time * 1.6f + i / 3f) % 1f; val yy = ht + k * (my - ht); c.drawCircle(.045f - .03f * max(0f, k - .7f) / .3f, yy, .009f, fillSolid(0xFF7CFFB0.toInt(), sip)) }
        fp.shader = LinearGradient(-wd / 2, 0f, wd / 2, 0f, 0xFF3DDC97.toInt(), 0xFF1FA971.toInt(), Shader.TileMode.CLAMP); fp.alpha = 255
        rect.set(-wd / 2, 0f, wd / 2, ht); c.drawRoundRect(rect, .025f, .025f, fp); fp.shader = null
        path.reset(); path.moveTo(-wd / 2, ht - .035f); path.lineTo(0f, ht + .005f); path.lineTo(wd / 2, ht - .035f); path.lineTo(wd / 2, ht - .01f); path.lineTo(-wd / 2, ht - .01f); path.close()
        c.drawPath(path, fillSolid(Color.argb(64, 255, 255, 255)))
        val bx = -.06f; val by = .06f; val bw = .12f; val bh = .1f
        val dk = fillSolid(0xFF0D2B20.toInt()); rect.set(bx, by, bx + bw, by + bh); c.drawRoundRect(rect, .012f, .012f, dk); c.drawRect(bx + bw * .35f, by + bh, bx + bw * .65f, by + bh + .014f, dk)
        val fc = if (lv < .2f) 0xFFFF6B6B.toInt() else if (full) 0xFFFFE08A.toInt() else 0xFF7CFFB0.toInt()
        c.drawRect(bx + .008f, by + .008f, bx + .008f + (bw - .016f), by + .008f + (bh - .016f) * clamp(lv, 0f, 1f), fillSolid(fc, .9f + .1f * sin(time * 6)))
        text(c, if (full) "FULL" else "${(lv * 100).roundToInt()}%", 0f, .025f, .042f, Color.WHITE)
        rect.set(-wd / 2, 0f, wd / 2, ht); c.drawRoundRect(rect, .025f, .025f, stroke(Color.argb(128, 0, 60, 40), .006f))
        c.restore()
    }
    private fun drawPal(c: Canvas, S: Float) {
        val p = pose
        // shadow (shrinks while he is in the air); feet line is body y = 0 at hy... the box follows pose.y, so ground is at -pose.y
        val air = clamp(p.y, 0f, 1f)
        c.save(); c.scale(S, -S)
        c.save(); c.translate(palX + p.x, 0f); c.scale(1f, .22f)
        fp.shader = radial(0f, 0f, .45f, intArrayOf(Color.argb(((.55f - .3f * air) * 255).roundToInt(), 0, 0, 0), 0)); fp.alpha = 255
        c.drawCircle(0f, 0f, .42f * (1 - .4f * air), fp); fp.shader = null; c.restore()
        // body space: the box is anchored so body y = 0 at its feet already offset by pose.y in draw()
        c.translate(palX + p.x, p.y)
        val pv = if (abs(p.rot) > .6f) .35f else 0f
        c.translate(0f, pv); c.rotate(deg(-p.rot)); c.translate(0f, -pv)
        c.scale(p.sx, p.sy)
        for (sd in floatArrayOf(-1f, 1f)) { val lift = if (sd < 0) p.footL else p.footR
            fp.shader = radial2(sd * .17f, .06f + lift, .01f, sd * .19f, .03f + lift, .11f, intArrayOf(SKIN_HI, FOOT)); fp.alpha = 255; oval(c, sd * .19f, .03f + lift, .1f, .065f, fp); fp.shader = null }
        drawEar(c, -1f, -p.earL, p); drawEar(c, 1f, p.earR, p)
        val frontL = p.armL < -.3f; val frontR = p.armR < -.3f
        if (!frontL) drawArm(c, -1f, p.armL, p); if (!frontR) drawArm(c, 1f, p.armR, p)
        bodyPath(p)
        fp.shader = radial2(-.14f + p.bend * .3f, .72f, .05f, 0f, .5f, .72f, intArrayOf(SKIN_HI, SKIN, SKIN_LO), floatArrayOf(0f, .5f, 1f)); fp.alpha = 255
        c.drawPath(path, fp); fp.shader = null
        c.save(); c.clipPath(path)
        fp.shader = radial(0f, .27f, .3f, intArrayOf(Color.argb(217, 255, 224, 212), Color.argb(0, 255, 224, 212))); fp.alpha = 255; oval(c, p.bend * .05f, .27f, .3f, .23f, fp)
        fp.shader = LinearGradient(.3f, 0f, .55f, 0f, Color.argb(0, 255, 179, 107), Color.argb(((.25f + .3f * bass) * 255).roundToInt().coerceIn(0, 255), 255, 190, 140), Shader.TileMode.CLAMP); c.drawRect(.3f, 0f, .6f, 1f, fp)
        fp.shader = LinearGradient(-.3f, 0f, -.55f, 0f, Color.argb(0, 157, 78, 221), Color.argb(((.18f + .3f * bass) * 255).roundToInt().coerceIn(0, 255), 190, 140, 255), Shader.TileMode.CLAMP); c.drawRect(-.6f, 0f, -.3f, 1f, fp)
        fp.shader = null; c.restore()
        c.save(); c.translate(look[0] * .045f + p.bend * .4f, look[1] * .025f)
        for (sd in floatArrayOf(-1f, 1f)) { fp.shader = radial(sd * .3f, .47f, .09f, intArrayOf(Color.argb((.55f * p.blush * 255).roundToInt().coerceIn(0, 255), 255, 110, 120), Color.argb(0, 255, 110, 120))); fp.alpha = 255
            oval(c, sd * .3f, .47f, .085f, .05f, fp); fp.shader = null }
        drawEye(c, -.19f, .585f, p, -1f); drawEye(c, .19f, .585f, p, 1f); drawMouth(c, p)
        c.restore()
        if (box > .02f) drawBox(c)
        if (frontL) drawArm(c, -1f, p.armL, p); if (frontR) drawArm(c, 1f, p.armR, p)
        c.restore()
    }
    private fun drawPart(c: Canvas, q: Part, S: Float) {
        val a = sm(q.life / .2f) * (1 - sm((q.life - q.max + .4f) / .4f))
        // particles live in ground-relative body units; the bitmap's y origin follows pose.y
        val x = q.x * S; val y = -q.y * S; val s = q.s * S
        c.save(); c.translate(x, y); c.rotate(deg(q.r + sin(time * 2 + q.max * 9) * .2f))
        when (q.kind) {
            HEART -> { path.reset(); path.moveTo(0f, s * .35f); path.cubicTo(-s, -s * .3f, -s * .45f, -s * .95f, 0f, -s * .45f); path.cubicTo(s * .45f, -s * .95f, s, -s * .3f, 0f, s * .35f); c.drawPath(path, fillSolid(0xFFFF7BA8.toInt(), a)) }
            BANG -> text(c, "!", 0f, 0f, s * 2.2f, 0xFFFFB36B.toInt(), a, false)
            ZZZ -> text(c, "z", 0f, 0f, s * 1.3f, 0xFFCDB4FF.toInt(), a, false)
            NOTE -> text(c, if (q.max > 1.8f) "♪" else "♫", 0f, 0f, s * 1.4f, if (q.max > 1.8f) 0xFFFFB36B.toInt() else 0xFFCDB4FF.toInt(), a, false)
            else -> { path.reset(); for (i in 0 until 8) { val rr = if (i % 2 == 1) s * .25f else s * .7f; val an = i / 8f * TAU; if (i == 0) path.moveTo(cos(an) * rr, sin(an) * rr) else path.lineTo(cos(an) * rr, sin(an) * rr) }; path.close()
                c.drawPath(path, fillSolid(if (q.kind == STAR) 0xFFFFE08A.toInt() else Color.WHITE, a)) }
        }
        c.restore()
    }

    companion object {
        private const val TAU = (PI * 2).toFloat()
        private const val HEART = 0; private const val BANG = 1; private const val ZZZ = 2; private const val NOTE = 3; private const val SPARK = 4; private const val STAR = 5
        private const val SKIN = 0xFFF4A393.toInt(); private const val SKIN_HI = 0xFFFFCDBC.toInt(); private const val SKIN_LO = 0xFFD87C6D.toInt()
        private const val EAR = 0xFFE98D7E.toInt(); private const val EAR_IN = 0xFFF7B3A3.toInt(); private const val FOOT = 0xFFE08B7C.toInt(); private const val LINE = 0xFF5A2A28.toInt()
        private fun clamp(x: Float, a: Float, b: Float) = if (x < a) a else if (x > b) b else x
        private fun sm(x: Float): Float { val c = clamp(x, 0f, 1f); return c * c * (3 - 2 * c) }
        private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
        private fun win(t: Float, a: Float, b: Float, c: Float, d: Float) = sm((t - a) / max(1e-4f, b - a)) * (1 - sm((t - c) / max(1e-4f, d - c)))
        private fun S(t: Float, f: Float, ph: Float = 0f) = sin(TAU * (t * f + ph))
        private fun rnd() = Random.nextFloat()
        private fun deg(r: Float) = r * 57.29578f
        private const val VS = "attribute vec2 aPos; uniform vec4 uRect; varying vec2 vUv; void main(){ vUv = vec2(aPos.x, 1.0 - aPos.y); gl_Position = vec4(mix(uRect.xy, uRect.zw, aPos), 0.0, 1.0); }"
        private const val FS = "precision mediump float; varying vec2 vUv; uniform sampler2D uTex; void main(){ gl_FragColor = texture2D(uTex, vUv); }"
    }
}
