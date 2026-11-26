package app.pulsify.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The code-drawn space super wallpapers of the Space shelf, flown by the same drone camera as the statues.
 *  - "asteroids": two asteroids on a collision course smash into each other in a flash, a shock ring and a
 *    storm of glowing fragments, then the next pair drifts in. A ring of slow rocks circles far behind. Tap: clash now.
 *  - "nebula": a glowing nebula all around the camera, gas clouds in 3D that slide past each other as the camera
 *    circles a cluster of young stars. Music swells the clouds; tap: a wave of light runs out of the core.
 * Soft things (the painted sky, gas, flares) are drawn at half resolution and laid under the full-resolution
 * rocks and stars, so the scene stays cheap on the GPU.
 */
class SpaceFx(private val context: Context) {
    private val rnd = Random(7)

    // ---------------------------------------------------------------- state shared by both looks
    private var time = 0f
    private var kind = ""
    private var bass = 0f
    private var gain = 1f          // tap surge for the nebula
    private var ripple = -1f       // seconds since the last tap ripple (nebula), -1 = none

    // ---------------------------------------------------------------- asteroid clash
    private class Rock {
        var mesh = 0; var scale = 1f
        val p = FloatArray(3); val v = FloatArray(3); val axis = FloatArray(3)
        var ang = 0f; var spin = 0f; var heat = 0f; var alive = false
    }
    private class Spark { val p = FloatArray(3); val v = FloatArray(3); var life = 0f; var age = 0f; var size = 1f }
    private class Cloud { val p = FloatArray(3); var age = 0f; var life = 3f; var size0 = .4f; var size1 = 2f; var atlas = 0; var rot = 0f; var warm = 1f }

    private val bigA = Rock(); private val bigB = Rock()
    private val pieces = Array(36) { Rock() }
    private val belt = Array(64) { Rock() }
    private val beltR = FloatArray(64); private val beltTh = FloatArray(64); private val beltW = FloatArray(64); private val beltH = FloatArray(64)
    private val sparks = Array(MAX_SPARKS) { Spark() }
    private val clouds = Array(10) { Cloud() }
    private var phase = 0f
    private var impacted = false
    private var flash = 0f
    private var shockAge = -1f
    private val dirA = FloatArray(3); private val perpU = FloatArray(3); private val perpV = FloatArray(3)
    private var startD = 3.2f

    // ---------------------------------------------------------------- nebula
    private class Gas { val p = FloatArray(3); var size = 1f; var r = 1f; var g = 1f; var b = 1f; var a = .15f; var atlas = 0; var rot = 0f; var spin = 0f }
    private val gas = Array(46) { Gas() }
    private class Star { val p = FloatArray(3); var size = .4f; var ph = 0f }
    private val core = Array(7) { Star() }

    init { seedBelt(); seedNebula(); newCycle(first = true) }

    // ---------------------------------------------------------------- events
    fun tap() {
        when (kind) {
            "asteroids" -> if (!impacted && phase < T_IMPACT - 0.35f) phase = T_IMPACT - 0.35f
            "nebula" -> { ripple = 0f; gain = 1.9f }
        }
    }

    fun update(dt: Float, look: String, bassIn: Float) {
        if (look != kind) { kind = look; if (look == "asteroids") newCycle(first = true) }
        time += dt
        bass += (bassIn - bass) * min(1f, dt * 10f)
        gain += (1f - gain) * min(1f, dt * 1.4f)
        if (ripple >= 0f) { ripple += dt; if (ripple > 2.2f) ripple = -1f }
        if (kind == "asteroids") stepClash(dt)
    }

    private fun stepClash(dt: Float) {
        phase += dt
        if (phase >= T_END) newCycle(first = false)
        if (!impacted && phase >= T_IMPACT) impact()
        flash = max(0f, flash - dt * 2.6f)
        if (shockAge >= 0f) { shockAge += dt; if (shockAge > 2.4f) shockAge = -1f }
        if (!impacted) {
            // the pair falls towards each other faster and faster (no slowing down before the hit)
            val u = (phase / T_IMPACT).coerceIn(0f, 1f)
            val k = 1f - u.pow(1.8f)
            place(bigA, dirA, bigA.scale * 0.92f + startD * k)
            val back = FloatArray(3) { -dirA[it] + perpU[it] * 0.16f }
            norm(back)
            place(bigB, back, bigB.scale * 0.92f + startD * k)
        }
        for (r in pieces) if (r.alive) {
            for (i in 0..2) { r.p[i] += r.v[i] * dt; r.v[i] *= (1f - 0.04f * dt) }
            r.heat *= exp(-dt / 1.9f)
        }
        for (r in arrayOf(bigA, bigB)) if (r.alive) r.ang += r.spin * dt
        for (r in pieces) if (r.alive) r.ang += r.spin * dt
        for (s in sparks) if (s.life > 0f && s.age < s.life) {
            s.age += dt
            val drag = 1f - 1.2f * dt
            for (i in 0..2) { s.p[i] += s.v[i] * dt; s.v[i] *= drag }
        }
        for (c in clouds) if (c.age < c.life) c.age += dt
    }

    private fun place(r: Rock, d: FloatArray, dist: Float) { for (i in 0..2) r.p[i] = d[i] * dist }

    private fun newCycle(first: Boolean) {
        phase = if (first) 1.2f else 0f
        impacted = false; flash = 0f; shockAge = -1f
        // a new collision axis: mostly sideways on screen, a little up or down, a new heading each time
        val th = rnd.nextFloat() * 2f * PI.toFloat(); val ph = (rnd.nextFloat() - .5f) * 0.6f
        dirA[0] = cos(th) * cos(ph); dirA[1] = sin(ph); dirA[2] = sin(th) * cos(ph)
        // two axes across it: the plane the debris fans out in
        val up = floatArrayOf(0f, 1f, 0f)
        cross(dirA, up, perpU); norm(perpU); cross(dirA, perpU, perpV); norm(perpV)
        startD = 3.0f + rnd.nextFloat() * 0.6f
        for ((i, r) in arrayOf(bigA, bigB).withIndex()) {
            r.alive = true; r.mesh = rnd.nextInt(MESHES); r.scale = if (i == 0) 0.62f + rnd.nextFloat() * .1f else 0.5f + rnd.nextFloat() * .1f
            randAxis(r.axis); r.ang = rnd.nextFloat() * 6.28f; r.spin = 0.25f + rnd.nextFloat() * 0.35f; r.heat = 0f
        }
        for (r in pieces) r.alive = false
        for (s in sparks) s.life = 0f
        for (c in clouds) c.age = c.life
    }

    private fun impact() {
        impacted = true; flash = 1f; shockAge = 0f
        bigA.alive = false; bigB.alive = false
        var n = 0
        fun spawn(from: FloatArray, scale: Float, speed: Float, heat: Float, spread: Float, push: Float) {
            if (n >= pieces.size) return
            val r = pieces[n++]
            r.alive = true; r.mesh = rnd.nextInt(MESHES); r.scale = scale; r.heat = heat
            randAxis(r.axis); r.ang = rnd.nextFloat() * 6.28f; r.spin = 0.6f + rnd.nextFloat() * 2.2f * (0.3f / max(scale, 0.1f)).coerceAtMost(3f)
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val g = (rnd.nextGaussian() * spread).toFloat()
            for (i in 0..2) {
                val d = perpU[i] * cos(a) + perpV[i] * sin(a) + dirA[i] * g
                r.p[i] = from[i] + d * 0.12f
                r.v[i] = d * speed + dirA[i] * push
            }
        }
        // each big rock breaks into two halves that tumble away across the hit, still glowing
        val pa = bigA.p.copyOf(); val pb = bigB.p.copyOf()
        repeat(2) { spawn(pa, bigA.scale * (0.55f + rnd.nextFloat() * .12f), 0.32f + rnd.nextFloat() * .2f, 0.75f, 0.25f, 0.12f) }
        repeat(2) { spawn(pb, bigB.scale * (0.55f + rnd.nextFloat() * .12f), 0.32f + rnd.nextFloat() * .2f, 0.75f, 0.25f, -0.12f) }
        // and a storm of small burning fragments out of the middle
        val c = floatArrayOf(0f, 0f, 0f)
        while (n < pieces.size) spawn(c, 0.035f + rnd.nextFloat() * rnd.nextFloat() * 0.13f, 0.5f + rnd.nextFloat() * 1.5f, 1f, 0.45f, 0f)
        for (s in sparks) {
            val a = rnd.nextFloat() * 2f * PI.toFloat(); val g = (rnd.nextGaussian() * 0.6).toFloat()
            val sp = 1.0f + rnd.nextFloat() * rnd.nextFloat() * 4.5f
            for (i in 0..2) {
                val d = perpU[i] * cos(a) + perpV[i] * sin(a) + dirA[i] * g
                s.p[i] = d * 0.08f; s.v[i] = d * sp
            }
            s.age = 0f; s.life = 0.5f + rnd.nextFloat() * 1.6f; s.size = 0.6f + rnd.nextFloat() * 1.2f
        }
        for (cl in clouds) {
            for (i in 0..2) cl.p[i] = (rnd.nextFloat() - .5f) * 0.5f
            cl.age = 0f; cl.life = 2.6f + rnd.nextFloat() * 1.8f; cl.size0 = 0.3f + rnd.nextFloat() * .3f; cl.size1 = 1.3f + rnd.nextFloat() * 1.3f
            cl.atlas = rnd.nextInt(4); cl.rot = rnd.nextFloat() * 6.28f; cl.warm = rnd.nextFloat()
        }
    }

    private fun seedBelt() {
        for (i in belt.indices) {
            val r = belt[i]
            r.alive = true; r.mesh = rnd.nextInt(MESHES); r.scale = 0.06f + rnd.nextFloat() * rnd.nextFloat() * 0.28f
            randAxis(r.axis); r.ang = rnd.nextFloat() * 6.28f; r.spin = 0.1f + rnd.nextFloat() * 0.5f
            beltR[i] = 6.2f + rnd.nextFloat() * 3.2f; beltTh[i] = rnd.nextFloat() * 6.28f
            beltW[i] = 0.012f + rnd.nextFloat() * 0.02f; beltH[i] = (rnd.nextGaussian() * 0.55).toFloat()
        }
    }

    private fun seedNebula() {
        // clouds fill a flattened ball around the core, denser in the middle
        val pal = arrayOf(floatArrayOf(1f, .25f, .7f), floatArrayOf(.45f, .3f, 1f), floatArrayOf(.15f, .85f, .95f), floatArrayOf(1f, .55f, .25f), floatArrayOf(.9f, .4f, 1f))
        for (g in gas) {
            val rr = 0.4f + rnd.nextFloat().pow(0.8f) * 3.4f; val th = rnd.nextFloat() * 6.28f
            g.p[0] = cos(th) * rr; g.p[2] = sin(th) * rr; g.p[1] = (rnd.nextGaussian() * 0.55).toFloat()
            g.size = 1.0f + rnd.nextFloat() * 2.2f
            val c = pal[rnd.nextInt(pal.size)]; g.r = c[0]; g.g = c[1]; g.b = c[2]
            g.a = 0.09f + rnd.nextFloat() * 0.14f; g.atlas = rnd.nextInt(4); g.rot = rnd.nextFloat() * 6.28f; g.spin = (rnd.nextFloat() - .5f) * 0.08f
        }
        for ((i, s) in core.withIndex()) {
            if (i == 0) { s.size = 1.5f } else {
                val th = rnd.nextFloat() * 6.28f; val rr = 0.25f + rnd.nextFloat() * 0.6f
                s.p[0] = cos(th) * rr; s.p[2] = sin(th) * rr; s.p[1] = (rnd.nextFloat() - .5f) * 0.4f
                s.size = 0.25f + rnd.nextFloat() * 0.45f
            }
            s.ph = rnd.nextFloat() * 6.28f
        }
    }

    // ---------------------------------------------------------------- GL resources
    private var rockProg = 0; private var spriteProg = 0; private var pointProg = 0; private var skyProg = 0; private var paintProg = 0; private var copyProg = 0
    private val ur = HashMap<String, Int>(); private val us = HashMap<String, Int>(); private val up = HashMap<String, Int>()
    private val uk = HashMap<String, Int>(); private val upn = HashMap<String, Int>(); private val uc = HashMap<String, Int>()
    private var quadVbo = 0
    private val meshVbo = IntArray(MESHES); private val meshIbo = IntArray(MESHES); private var meshIdx = 0
    private var starVbo = 0; private var starCount = 0
    private var sparkVbo = 0; private var sparkBuf: FloatBuffer? = null
    private var rockTex = 0; private var atlasTex = 0; private var cubeTex = 0; private var cubeKind = ""
    private var glowFbo = 0; private var glowTex = 0; private var glowW = 0; private var glowH = 0
    private var aNrmLoc = 1; private var aColLoc = 1

    fun onSurfaceCreated() {
        rockProg = Gl.program(ROCK_VS, ROCK_FS); aNrmLoc = GLES20.glGetAttribLocation(rockProg, "aNrm")
        for (n in listOf("uVP", "uM", "uTex", "uCam", "uKey", "uKeyCol", "uRimDir", "uRimCol", "uHit", "uHitCol", "uHeat", "uDim", "uTexS")) ur[n] = GLES20.glGetUniformLocation(rockProg, n)
        spriteProg = Gl.program(SPRITE_VS, SPRITE_FS)
        for (n in listOf("uVP", "uCenter", "uRight", "uUp", "uSize", "uRot", "uTex", "uCol", "uMode", "uAtlas")) us[n] = GLES20.glGetUniformLocation(spriteProg, n)
        pointProg = Gl.program(POINT_VS, POINT_FS); aColLoc = GLES20.glGetAttribLocation(pointProg, "aCol")
        for (n in listOf("uVP", "uTime", "uPx", "uTw")) up[n] = GLES20.glGetUniformLocation(pointProg, n)
        skyProg = Gl.program(QUAD_VS, SKY_FS)
        for (n in listOf("uCube", "uRight", "uUp", "uFwd", "uTan", "uGain")) uk[n] = GLES20.glGetUniformLocation(skyProg, n)
        paintProg = Gl.program(QUAD_VS, PAINT_FS)
        for (n in listOf("uFace", "uKind", "uSize", "uMode")) upn[n] = GLES20.glGetUniformLocation(paintProg, n)
        copyProg = Gl.program(QUAD_VS, COPY_FS)
        uc["uTex"] = GLES20.glGetUniformLocation(copyProg, "uTex")

        val ids = IntArray(1); GLES20.glGenBuffers(1, ids, 0); quadVbo = ids[0]
        val q = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo); GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 32, fbuf(q), GLES20.GL_STATIC_DRAW)
        buildMeshes(); buildStars()
        GLES20.glGenBuffers(1, ids, 0); sparkVbo = ids[0]
        sparkBuf = ByteBuffer.allocateDirect(MAX_SPARKS * 8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        rockTex = loadTexture("statues/moon_c.webp")
        cubeKind = ""; atlasTex = 0; glowW = 0
    }

    private fun fbuf(a: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(a).also { it.position(0) }

    /** Lumpy rocks: an icosphere pushed in and out by noise, with a few craters pressed in. */
    private fun buildMeshes() {
        val (verts, tris) = icosphere(3)
        meshIdx = tris.size
        val ib = ByteBuffer.allocateDirect(tris.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        for (t in tris) ib.put(t.toShort()); ib.position(0)
        val nv = verts.size / 3
        for (m in 0 until MESHES) {
            val r = Random(100L + m * 31L)
            val off = FloatArray(3) { r.nextFloat() * 50f }
            val stretch = floatArrayOf(1f + r.nextFloat() * .35f, 0.75f + r.nextFloat() * .2f, 0.85f + r.nextFloat() * .25f)
            val craters = Array(5) { val d = FloatArray(3); randUnit(r, d); d }
            val crR = FloatArray(5) { 0.25f + r.nextFloat() * 0.3f }
            val pos = FloatArray(nv * 3)
            for (i in 0 until nv) {
                val x = verts[i * 3]; val y = verts[i * 3 + 1]; val z = verts[i * 3 + 2]
                var h = 1f + 0.32f * (fbm(x * 1.3f + off[0], y * 1.3f + off[1], z * 1.3f + off[2]) - 0.5f) * 2f +
                    0.06f * (fbm(x * 4f + off[1], y * 4f + off[2], z * 4f + off[0]) - 0.5f) * 2f
                for (c in craters.indices) {
                    val d = 1f - (x * craters[c][0] + y * craters[c][1] + z * craters[c][2])
                    val t = d / (crR[c] * crR[c] * 0.5f)
                    if (t < 1.4f) h += if (t < 1f) -0.08f * (1f - t * t) else 0.03f * (1f - abs(t - 1.2f) / 0.2f).coerceAtLeast(0f)
                }
                pos[i * 3] = x * h * stretch[0]; pos[i * 3 + 1] = y * h * stretch[1]; pos[i * 3 + 2] = z * h * stretch[2]
            }
            val nrm = FloatArray(nv * 3)
            var t = 0
            while (t < tris.size) {
                val a = tris[t]; val b = tris[t + 1]; val c = tris[t + 2]
                val ux = pos[b * 3] - pos[a * 3]; val uy = pos[b * 3 + 1] - pos[a * 3 + 1]; val uz = pos[b * 3 + 2] - pos[a * 3 + 2]
                val vx = pos[c * 3] - pos[a * 3]; val vy = pos[c * 3 + 1] - pos[a * 3 + 1]; val vz = pos[c * 3 + 2] - pos[a * 3 + 2]
                val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
                for (k in intArrayOf(a, b, c)) { nrm[k * 3] += nx; nrm[k * 3 + 1] += ny; nrm[k * 3 + 2] += nz }
                t += 3
            }
            val data = FloatArray(nv * 6)
            for (i in 0 until nv) {
                var nx = nrm[i * 3]; var ny = nrm[i * 3 + 1]; var nz = nrm[i * 3 + 2]
                val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f); nx /= l; ny /= l; nz /= l
                // keep the normals pointing out (the icosphere winding is not guaranteed)
                if (nx * pos[i * 3] + ny * pos[i * 3 + 1] + nz * pos[i * 3 + 2] < 0f) { nx = -nx; ny = -ny; nz = -nz }
                data[i * 6] = pos[i * 3]; data[i * 6 + 1] = pos[i * 3 + 1]; data[i * 6 + 2] = pos[i * 3 + 2]
                data[i * 6 + 3] = nx; data[i * 6 + 4] = ny; data[i * 6 + 5] = nz
            }
            val ids = IntArray(2); GLES20.glGenBuffers(2, ids, 0); meshVbo[m] = ids[0]; meshIbo[m] = ids[1]
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, meshVbo[m]); GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size * 4, fbuf(data), GLES20.GL_STATIC_DRAW)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, meshIbo[m]); GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, tris.size * 2, ib, GLES20.GL_STATIC_DRAW)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0); GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    /** Far stars on a big sphere and nearer ones inside it (they slide against the far ones as the camera moves). */
    private fun buildStars() {
        val r = Random(11)
        val far = 1500; val near = 420
        starCount = far + near
        val a = FloatArray(starCount * 8)
        val d = FloatArray(3)
        for (i in 0 until starCount) {
            randUnit(r, d)
            val dist = if (i < far) 26f else 3.5f + r.nextFloat() * 12f
            val b = r.nextFloat().pow(3f)
            val tint = r.nextFloat()
            a[i * 8] = d[0] * dist; a[i * 8 + 1] = d[1] * dist; a[i * 8 + 2] = d[2] * dist
            a[i * 8 + 3] = 0.6f + b * 2.4f
            val br = 0.25f + b * 0.9f
            a[i * 8 + 4] = br * (if (tint < .2f) 1f else if (tint < .35f) .75f else .85f)
            a[i * 8 + 5] = br * (if (tint < .2f) .8f else .9f)
            a[i * 8 + 6] = br * (if (tint < .2f) .6f else if (tint < .35f) 1f else 1f)
            a[i * 8 + 7] = r.nextFloat() * 6.28f
        }
        val ids = IntArray(1); GLES20.glGenBuffers(1, ids, 0); starVbo = ids[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, starVbo); GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, a.size * 4, fbuf(a), GLES20.GL_STATIC_DRAW)
    }

    private fun loadTexture(path: String): Int {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bmp = context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }!!
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        bmp.recycle()
        return t[0]
    }

    /** Paints a texture once with the paint shader (the gas atlas, or one face of the sky cube). */
    private fun paint(target: Int, face: Int, size: Int, mode: Int) {
        val ids = IntArray(1); GLES20.glGenFramebuffers(1, ids, 0); val fb = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb)
        if (mode == 0) GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, target, 0)
        else GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, target, 0)
        GLES20.glViewport(0, 0, size, size)
        GLES20.glDisable(GLES20.GL_BLEND); GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(paintProg)
        GLES20.glUniform1f(upn["uFace"]!!, face.toFloat()); GLES20.glUniform1f(upn["uKind"]!!, if (cubeKind == "nebula") 0f else 1f)
        GLES20.glUniform1f(upn["uSize"]!!, size.toFloat()); GLES20.glUniform1f(upn["uMode"]!!, mode.toFloat())
        quad()
        GLES20.glDeleteFramebuffers(1, ids, 0)
    }

    private fun ensurePainted(want: String) {
        if (atlasTex == 0) {
            val t = IntArray(1); GLES20.glGenTextures(1, t, 0); atlasTex = t[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTex)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, ATLAS, ATLAS, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            paint(atlasTex, 0, ATLAS, 1)
        }
        if (cubeKind == want && cubeTex != 0) return
        cubeKind = want
        if (cubeTex == 0) {
            val t = IntArray(1); GLES20.glGenTextures(1, t, 0); cubeTex = t[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_CUBE_MAP, cubeTex)
            for (f in 0 until 6) GLES20.glTexImage2D(GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + f, 0, GLES20.GL_RGBA, CUBE, CUBE, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
        for (f in 0 until 6) paint(cubeTex, f, CUBE, 0)
    }

    private fun ensureGlow(w: Int, h: Int) {
        if (glowFbo != 0 && w == glowW && h == glowH) return
        freeGlow()
        glowW = w; glowH = h
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0); glowTex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glowTex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glGenFramebuffers(1, ids, 0); glowFbo = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, glowFbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, glowTex, 0)
    }

    private fun freeGlow() {
        if (glowFbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(glowFbo), 0)
        if (glowTex != 0) GLES20.glDeleteTextures(1, intArrayOf(glowTex), 0)
        glowFbo = 0; glowTex = 0; glowW = 0; glowH = 0
    }

    private fun quad() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(0)
    }

    // ---------------------------------------------------------------- draw
    private val vp = FloatArray(16); private val model = FloatArray(16)
    private val right = FloatArray(3); private val upv = FloatArray(3); private val fwd = FloatArray(3)

    /**
     * Draws the look into [targetFbo] ([w] × [h], with a depth buffer), replacing its contents.
     * [view]/[proj] and the camera position come from the statue drone; [light] is its 0..1 light ramp.
     */
    fun draw(look: String, view: FloatArray, proj: FloatArray, cx: Float, cy: Float, cz: Float, fovY: Float, aspect: Float,
             targetFbo: Int, w: Int, h: Int, light: Float) {
        if (rockProg == 0) return
        ensurePainted(look)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        right[0] = view[0]; right[1] = view[4]; right[2] = view[8]
        upv[0] = view[1]; upv[1] = view[5]; upv[2] = view[9]
        fwd[0] = -view[2]; fwd[1] = -view[6]; fwd[2] = -view[10]
        val tanY = tan(fovY * PI.toFloat() / 360f)

        // ---- soft layer at half resolution: sky, gas, flares, dust, shock ring ----
        val gw = max(16, w / 2); val gh = max(16, h / 2)
        ensureGlow(gw, gh)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, glowFbo)
        GLES20.glViewport(0, 0, gw, gh)
        GLES20.glClearColor(0f, 0f, 0f, 1f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(skyProg)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_CUBE_MAP, cubeTex)
        GLES20.glUniform1i(uk["uCube"]!!, 0)
        GLES20.glUniform3f(uk["uRight"]!!, right[0], right[1], right[2]); GLES20.glUniform3f(uk["uUp"]!!, upv[0], upv[1], upv[2])
        GLES20.glUniform3f(uk["uFwd"]!!, fwd[0], fwd[1], fwd[2]); GLES20.glUniform2f(uk["uTan"]!!, tanY * aspect, tanY)
        val skyGain = (if (look == "nebula") 0.85f + 0.35f * bass + 0.25f * (gain - 1f) else 0.9f) * light
        GLES20.glUniform1f(uk["uGain"]!!, skyGain)
        quad()

        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)
        GLES20.glUseProgram(spriteProg)
        GLES20.glUniformMatrix4fv(us["uVP"]!!, 1, false, vp, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTex)
        GLES20.glUniform1i(us["uTex"]!!, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        if (look == "nebula") drawNebulaSoft(light) else drawClashSoft(light)
        GLES20.glDisableVertexAttribArray(0)

        // ---- full resolution: the soft layer, then rocks (depth), stars and sparks ----
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFbo)
        GLES20.glViewport(0, 0, w, h)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_BLEND); GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(copyProg)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glowTex)
        GLES20.glUniform1i(uc["uTex"]!!, 0)
        quad()

        if (look == "asteroids") {
            GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glDepthFunc(GLES20.GL_LEQUAL); GLES20.glDepthMask(true)
            drawRocks(cx, cy, cz, light)
        }
        // stars and sparks: additive, hidden behind rocks, never write depth
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)
        GLES20.glDepthMask(false)
        drawStars(h, light)
        if (look == "asteroids") drawSparks(h)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0); GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun sprite(x: Float, y: Float, z: Float, size: Float, rot: Float, r: Float, g: Float, b: Float, a: Float, mode: Int, atlas: Int = 0,
                       ax: FloatArray = right, ay: FloatArray = upv) {
        if (a <= 0.002f) return
        GLES20.glUniform3f(us["uCenter"]!!, x, y, z)
        GLES20.glUniform3f(us["uRight"]!!, ax[0], ax[1], ax[2]); GLES20.glUniform3f(us["uUp"]!!, ay[0], ay[1], ay[2])
        GLES20.glUniform1f(us["uSize"]!!, size); GLES20.glUniform1f(us["uRot"]!!, rot)
        GLES20.glUniform4f(us["uCol"]!!, r, g, b, a); GLES20.glUniform1f(us["uMode"]!!, mode.toFloat())
        GLES20.glUniform2f(us["uAtlas"]!!, (atlas % 2) * 0.5f, (atlas / 2) * 0.5f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawNebulaSoft(light: Float) {
        val turn = time * 0.035f; val c = cos(turn); val s = sin(turn)
        val g0 = (0.8f + bass * 0.6f) * gain * light
        for (g in gas) {
            val x = g.p[0] * c - g.p[2] * s; val z = g.p[0] * s + g.p[2] * c
            val breathe = 1f + 0.06f * sin(time * 0.3f + g.rot * 3f)
            sprite(x, g.p[1], z, g.size * breathe * (1f + bass * 0.12f), g.rot + g.spin * time, g.r, g.g, g.b, g.a * g0, 0, g.atlas)
        }
        // the young stars at the heart: a big flare and a few smaller ones, twinkling
        for ((i, st) in core.withIndex()) {
            val x = st.p[0] * c - st.p[2] * s; val z = st.p[0] * s + st.p[2] * c
            val tw = 0.85f + 0.15f * sin(time * (1.3f + i * .37f) + st.ph)
            val k = (if (i == 0) 1f + bass * 0.35f + (gain - 1f) * 0.4f else 1f) * tw * light
            val cool = if (i % 3 == 1) floatArrayOf(1f, .8f, .65f) else floatArrayOf(.75f, .85f, 1f)
            sprite(x, st.p[1], z, st.size * k, 0.3f + i * 0.4f, cool[0], cool[1], cool[2], (if (i == 0) 1.1f else 0.85f), 1)
        }
        if (ripple >= 0f) {
            val u = ripple / 2.2f
            sprite(0f, 0f, 0f, 0.3f + u * 7f, 0f, .7f, .6f, 1f, (1f - u).pow(1.5f) * 0.9f * light, 2)
        }
    }

    private fun drawClashSoft(light: Float) {
        // dust from the hit, warm at first, then grey and fading
        for (cl in clouds) if (cl.age < cl.life) {
            val u = cl.age / cl.life
            val size = cl.size0 + (cl.size1 - cl.size0) * (1f - (1f - u).pow(2.2f))
            val hot = exp(-cl.age * 1.6f)
            val r = 0.45f + 0.55f * hot; val g = 0.38f + 0.3f * hot * cl.warm; val b = 0.42f - 0.2f * hot
            sprite(cl.p[0], cl.p[1], cl.p[2], size, cl.rot + cl.age * 0.2f, r, g, b, (1f - u) * (0.22f + 0.5f * hot) * light, 0, cl.atlas)
        }
        // the shock ring spreads in the plane across the hit
        if (shockAge >= 0f) {
            val u = shockAge / 2.4f
            sprite(0f, 0f, 0f, 0.2f + (1f - (1f - u).pow(3f)) * 4.2f, 0f, 1f, .62f, .35f, (1f - u).pow(1.6f) * 1.2f * light, 2, ax = perpU, ay = perpV)
            sprite(0f, 0f, 0f, 0.2f + (1f - (1f - u).pow(3f)) * 3.2f, 0f, .55f, .5f, 1f, (1f - u).pow(2f) * 0.6f * light, 2, ax = perpU, ay = perpV)
        }
        if (flash > 0f) {
            val f = flash * flash
            sprite(0f, 0f, 0f, 0.6f + (1f - flash) * 2.4f, 0.4f, 1f, .9f, .75f, f * 1.6f, 1)
            sprite(0f, 0f, 0f, 3.5f, 0f, 1f, .55f, .3f, f * 0.35f, 0, 1)
        }
    }

    private fun drawRocks(cx: Float, cy: Float, cz: Float, light: Float) {
        GLES20.glUseProgram(rockProg)
        GLES20.glUniformMatrix4fv(ur["uVP"]!!, 1, false, vp, 0)
        GLES20.glUniform3f(ur["uCam"]!!, cx, cy, cz)
        val kd = floatArrayOf(-0.62f, 0.45f, 0.64f); norm(kd)
        GLES20.glUniform3f(ur["uKey"]!!, kd[0], kd[1], kd[2])
        GLES20.glUniform3f(ur["uKeyCol"]!!, 2.4f * light, 2.25f * light, 2.05f * light)
        val rd = floatArrayOf(0.7f, 0.2f, -0.7f); norm(rd)
        GLES20.glUniform3f(ur["uRimDir"]!!, rd[0], rd[1], rd[2]); GLES20.glUniform3f(ur["uRimCol"]!!, 0.35f * light, 0.25f * light, 0.75f * light)
        val f = flash.pow(1.5f) * 9f + (if (impacted && phase - T_IMPACT < 3f) exp(-(phase - T_IMPACT) * 1.4f) * 1.5f else 0f)
        GLES20.glUniform3f(ur["uHit"]!!, 0f, 0f, 0f); GLES20.glUniform3f(ur["uHitCol"]!!, 1.6f * f, 0.8f * f, 0.35f * f)
        GLES20.glUniform1f(ur["uTexS"]!!, 0.38f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, rockTex); GLES20.glUniform1i(ur["uTex"]!!, 0)
        GLES20.glEnableVertexAttribArray(0); if (aNrmLoc >= 0) GLES20.glEnableVertexAttribArray(aNrmLoc)
        var bound = -1
        fun one(r: Rock, x: Float, y: Float, z: Float, dim: Float) {
            if (dim <= 0.01f) return
            if (bound != r.mesh) {
                bound = r.mesh
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, meshVbo[r.mesh]); GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, meshIbo[r.mesh])
                GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 24, 0)
                if (aNrmLoc >= 0) GLES20.glVertexAttribPointer(aNrmLoc, 3, GLES20.GL_FLOAT, false, 24, 12)
            }
            Matrix.setIdentityM(model, 0); Matrix.translateM(model, 0, x, y, z)
            Matrix.rotateM(model, 0, r.ang * 57.29578f, r.axis[0], r.axis[1], r.axis[2])
            Matrix.scaleM(model, 0, r.scale, r.scale, r.scale)
            GLES20.glUniformMatrix4fv(ur["uM"]!!, 1, false, model, 0)
            GLES20.glUniform1f(ur["uHeat"]!!, (r.heat * (1f + bass * 0.4f)).coerceAtMost(1.3f)); GLES20.glUniform1f(ur["uDim"]!!, dim)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, meshIdx, GLES20.GL_UNSIGNED_SHORT, 0)
        }
        // the far ring of rocks, slowly orbiting on a tilted plane
        val tilt = 0.32f; val ct = cos(tilt); val stl = sin(tilt)
        for (i in belt.indices) {
            val r = belt[i]; r.ang = time * r.spin + i
            val th = beltTh[i] + time * beltW[i]
            val x = cos(th) * beltR[i]; val z0 = sin(th) * beltR[i]; val y0 = beltH[i]
            one(r, x, y0 * ct - z0 * stl, y0 * stl + z0 * ct, 0.75f)
        }
        // fade the whole clash in at the start of a cycle and out at the end
        val fadeIn = (phase / 0.9f).coerceIn(0f, 1f); val fadeOut = ((T_END - phase) / 1.5f).coerceIn(0f, 1f)
        for (r in arrayOf(bigA, bigB)) if (r.alive) one(r, r.p[0], r.p[1], r.p[2], fadeIn)
        for (r in pieces) if (r.alive) one(r, r.p[0], r.p[1], r.p[2], fadeOut)
        GLES20.glDisableVertexAttribArray(0); if (aNrmLoc >= 0) GLES20.glDisableVertexAttribArray(aNrmLoc)
    }

    private fun drawStars(h: Int, light: Float) {
        GLES20.glUseProgram(pointProg)
        GLES20.glUniformMatrix4fv(up["uVP"]!!, 1, false, vp, 0)
        GLES20.glUniform1f(up["uTime"]!!, time); GLES20.glUniform1f(up["uPx"]!!, h / 1100f * (0.6f + 0.4f * light)); GLES20.glUniform1f(up["uTw"]!!, light)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, starVbo)
        GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 4, GLES20.GL_FLOAT, false, 32, 0)
        if (aColLoc >= 0) { GLES20.glEnableVertexAttribArray(aColLoc); GLES20.glVertexAttribPointer(aColLoc, 4, GLES20.GL_FLOAT, false, 32, 16) }
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, starCount)
        GLES20.glDisableVertexAttribArray(0); if (aColLoc >= 0) GLES20.glDisableVertexAttribArray(aColLoc)
    }

    private fun drawSparks(h: Int) {
        val buf = sparkBuf ?: return
        buf.clear(); var n = 0
        for (s in sparks) if (s.life > 0f && s.age < s.life) {
            val u = s.age / s.life; val k = (1f - u)
            buf.put(s.p[0]).put(s.p[1]).put(s.p[2]).put(s.size * (0.7f + 1.1f * k))
            // white hot, then orange, then deep red as it cools
            buf.put(1.6f * k).put((0.4f + 1.1f * k * k) * k).put((0.1f + 0.9f * k * k * k) * k).put(-1f)
            n++
        }
        if (n == 0) return
        buf.position(0)
        GLES20.glUniform1f(up["uTw"]!!, 0f)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sparkVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, n * 32, buf, GLES20.GL_STREAM_DRAW)
        GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 4, GLES20.GL_FLOAT, false, 32, 0)
        if (aColLoc >= 0) { GLES20.glEnableVertexAttribArray(aColLoc); GLES20.glVertexAttribPointer(aColLoc, 4, GLES20.GL_FLOAT, false, 32, 16) }
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, n)
        GLES20.glDisableVertexAttribArray(0); if (aColLoc >= 0) GLES20.glDisableVertexAttribArray(aColLoc)
    }

    fun release() {
        freeGlow()
        for (p in intArrayOf(rockProg, spriteProg, pointProg, skyProg, paintProg, copyProg)) if (p != 0) GLES20.glDeleteProgram(p)
        rockProg = 0; spriteProg = 0; pointProg = 0; skyProg = 0; paintProg = 0; copyProg = 0
        val bufs = (intArrayOf(quadVbo, starVbo, sparkVbo) + meshVbo + meshIbo).filter { it != 0 }.toIntArray()
        if (bufs.isNotEmpty()) GLES20.glDeleteBuffers(bufs.size, bufs, 0)
        quadVbo = 0; starVbo = 0; sparkVbo = 0; meshVbo.fill(0); meshIbo.fill(0)
        val tex = intArrayOf(rockTex, atlasTex, cubeTex).filter { it != 0 }.toIntArray()
        if (tex.isNotEmpty()) GLES20.glDeleteTextures(tex.size, tex, 0)
        rockTex = 0; atlasTex = 0; cubeTex = 0; cubeKind = ""
    }

    // ---------------------------------------------------------------- helpers
    private fun randAxis(a: FloatArray) = randUnit(rnd, a)
    private fun randUnit(r: Random, a: FloatArray) {
        val z = r.nextFloat() * 2f - 1f; val t = r.nextFloat() * 2f * PI.toFloat(); val s = sqrt(1f - z * z)
        a[0] = cos(t) * s; a[1] = sin(t) * s; a[2] = z
    }
    private fun cross(a: FloatArray, b: FloatArray, o: FloatArray) {
        o[0] = a[1] * b[2] - a[2] * b[1]; o[1] = a[2] * b[0] - a[0] * b[2]; o[2] = a[0] * b[1] - a[1] * b[0]
    }
    private fun norm(a: FloatArray) { val l = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]).coerceAtLeast(1e-6f); a[0] /= l; a[1] /= l; a[2] /= l }

    private fun hash(x: Int, y: Int, z: Int): Float {
        var h = x * 374761393 + y * 668265263 + z * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0x7fffffff) / 2147483647f
    }
    private fun vnoise(x: Float, y: Float, z: Float): Float {
        val xi = kotlin.math.floor(x).toInt(); val yi = kotlin.math.floor(y).toInt(); val zi = kotlin.math.floor(z).toInt()
        var fx = x - xi; var fy = y - yi; var fz = z - zi
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz)
        fun l(a: Float, b: Float, t: Float) = a + (b - a) * t
        return l(l(l(hash(xi, yi, zi), hash(xi + 1, yi, zi), fx), l(hash(xi, yi + 1, zi), hash(xi + 1, yi + 1, zi), fx), fy),
            l(l(hash(xi, yi, zi + 1), hash(xi + 1, yi, zi + 1), fx), l(hash(xi, yi + 1, zi + 1), hash(xi + 1, yi + 1, zi + 1), fx), fy), fz)
    }
    private fun fbm(x: Float, y: Float, z: Float): Float {
        var a = 0.5f; var s = 0f; var px = x; var py = y; var pz = z
        repeat(4) { s += a * vnoise(px, py, pz); px = px * 2.03f + 1.7f; py = py * 2.03f + 9.2f; pz = pz * 2.03f + 3.1f; a *= 0.5f }
        return s / 0.9375f
    }

    /** Unit icosphere: vertices (x, y, z, ...) and triangle indices. */
    private fun icosphere(levels: Int): Pair<FloatArray, IntArray> {
        val t = ((1.0 + sqrt(5.0)) / 2.0).toFloat()
        val v = ArrayList<FloatArray>()
        fun add(x: Float, y: Float, z: Float): Int { val l = sqrt(x * x + y * y + z * z); v.add(floatArrayOf(x / l, y / l, z / l)); return v.size - 1 }
        listOf(-1f to t, 1f to t, -1f to -t, 1f to -t).forEach { (a, b) -> add(a, b, 0f) }
        listOf(-1f to t, 1f to t, -1f to -t, 1f to -t).forEach { (a, b) -> add(0f, a, b) }
        listOf(-1f to t, 1f to t, -1f to -t, 1f to -t).forEach { (a, b) -> add(b, 0f, a) }
        var f = intArrayOf(0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
            3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1)
        repeat(levels) {
            val mid = HashMap<Long, Int>()
            fun m(a: Int, b: Int): Int {
                val key = if (a < b) a.toLong() * 100000 + b else b.toLong() * 100000 + a
                return mid.getOrPut(key) { val p = v[a]; val q = v[b]; add((p[0] + q[0]) / 2, (p[1] + q[1]) / 2, (p[2] + q[2]) / 2) }
            }
            val nf = IntArray(f.size * 4); var k = 0
            var i = 0
            while (i < f.size) {
                val a = f[i]; val b = f[i + 1]; val c = f[i + 2]
                val ab = m(a, b); val bc = m(b, c); val ca = m(c, a)
                for (x in intArrayOf(a, ab, ca, b, bc, ab, c, ca, bc, ab, bc, ca)) nf[k++] = x
                i += 3
            }
            f = nf
        }
        val out = FloatArray(v.size * 3); for (i in v.indices) { out[i * 3] = v[i][0]; out[i * 3 + 1] = v[i][1]; out[i * 3 + 2] = v[i][2] }
        return out to f
    }

    companion object {
        const val MESHES = 6
        const val MAX_SPARKS = 260
        const val T_IMPACT = 4.6f
        const val T_END = 11.5f
        const val CUBE = 768
        const val ATLAS = 512

        private const val PREC = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
"""
        private const val NOISE = """
float hash3(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float vnoise(vec3 x) {
  vec3 i = floor(x); vec3 f = fract(x); f = f * f * (3.0 - 2.0 * f);
  return mix(mix(mix(hash3(i), hash3(i + vec3(1.0, 0.0, 0.0)), f.x), mix(hash3(i + vec3(0.0, 1.0, 0.0)), hash3(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
             mix(mix(hash3(i + vec3(0.0, 0.0, 1.0)), hash3(i + vec3(1.0, 0.0, 1.0)), f.x), mix(hash3(i + vec3(0.0, 1.0, 1.0)), hash3(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}
float fbm(vec3 p) { float a = 0.5; float s = 0.0; for (int i = 0; i < 6; i++) { s += a * vnoise(p); p = p * 2.03 + vec3(1.7, 9.2, 3.1); a *= 0.5; } return s; }
"""

        const val QUAD_VS = "attribute vec2 aPos; varying vec2 vUv; void main(){ vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }"

        const val COPY_FS = "precision mediump float; varying vec2 vUv; uniform sampler2D uTex; void main(){ gl_FragColor = vec4(texture2D(uTex, vUv).rgb, 1.0); }"

        // uMode 0: one face of the sky cube for uKind (0 nebula, 1 the darker sky behind the asteroids).
        // uMode 1: the 2 x 2 atlas of soft gas puffs (white, alpha in every channel).
        const val PAINT_FS = PREC + NOISE + """
uniform float uFace; uniform float uKind; uniform float uSize; uniform float uMode;
vec3 faceDir(float f, vec2 c) {
  if (f < 0.5) return vec3(1.0, -c.y, -c.x);
  if (f < 1.5) return vec3(-1.0, -c.y, c.x);
  if (f < 2.5) return vec3(c.x, 1.0, c.y);
  if (f < 3.5) return vec3(c.x, -1.0, -c.y);
  if (f < 4.5) return vec3(c.x, -c.y, 1.0);
  return vec3(-c.x, -c.y, -1.0);
}
void main() {
  vec2 st = gl_FragCoord.xy / uSize;
  if (uMode > 0.5) {
    vec2 cell = floor(st * 2.0); vec2 uv = fract(st * 2.0) * 2.0 - 1.0;
    float seed = cell.x + cell.y * 2.0;
    float r = length(uv);
    float base = smoothstep(1.0, 0.1, r); base *= base;
    float d = fbm(vec3(uv * 2.2, seed * 7.3));
    float a = clamp(base * (d * 1.9 - 0.35), 0.0, 1.0);
    gl_FragColor = vec4(a);
    return;
  }
  vec3 d = normalize(faceDir(uFace, st * 2.0 - 1.0));
  vec3 q = d * 1.7;
  vec3 w = vec3(fbm(q + vec3(0.0, 1.3, 2.1)), fbm(q + vec3(5.2, 1.7, 9.1)), fbm(q + vec3(2.4, 8.1, 3.3)));
  float n = fbm(q * 1.3 + w * 2.4);
  float n2 = fbm(q * 3.2 - w * 1.6 + 7.0);
  float band = exp(-pow(dot(d, normalize(vec3(0.35, 1.0, 0.2))) * 2.0, 2.0));
  float dens = smoothstep(0.32, 0.82, n) * (0.3 + 0.7 * band);
  float lanes = smoothstep(0.48, 0.78, n2);
  vec3 magenta = vec3(0.95, 0.18, 0.6); vec3 blue = vec3(0.25, 0.3, 1.0); vec3 teal = vec3(0.1, 0.85, 0.85); vec3 amber = vec3(1.0, 0.55, 0.22);
  vec3 col = mix(blue, magenta, smoothstep(0.35, 0.75, w.x));
  col = mix(col, teal, smoothstep(0.55, 0.85, w.y) * 0.65);
  col = mix(col, amber, smoothstep(0.62, 0.9, n2) * 0.45);
  vec3 c = col * dens * 1.25 * (1.0 - 0.8 * lanes);
  c += vec3(1.0, 0.86, 0.95) * pow(dens, 4.0) * 0.55;
  if (uKind > 0.5) {
    // behind the asteroids: a dim, colder sky so the rocks and the fire stand out
    c = mix(vec3(dot(c, vec3(0.3, 0.5, 0.2))), c, 0.55) * vec3(0.55, 0.62, 0.9) * 0.42;
  }
  gl_FragColor = vec4(min(c, vec3(1.0)), 1.0);
}"""

        const val SKY_FS = """precision mediump float; varying vec2 vUv;
uniform samplerCube uCube; uniform vec3 uRight; uniform vec3 uUp; uniform vec3 uFwd; uniform vec2 uTan; uniform float uGain;
void main() {
  vec2 c = vUv * 2.0 - 1.0;
  vec3 d = normalize(uFwd + uRight * c.x * uTan.x + uUp * c.y * uTan.y);
  gl_FragColor = vec4(textureCube(uCube, d).rgb * uGain, 1.0);
}"""

        const val SPRITE_VS = """attribute vec2 aPos;
uniform mat4 uVP; uniform vec3 uCenter; uniform vec3 uRight; uniform vec3 uUp; uniform float uSize; uniform float uRot;
varying vec2 vUv;
void main() {
  float c = cos(uRot); float s = sin(uRot);
  vec2 p = vec2(c * aPos.x - s * aPos.y, s * aPos.x + c * aPos.y);
  vUv = aPos * 0.5 + 0.5;
  gl_Position = uVP * vec4(uCenter + (uRight * p.x + uUp * p.y) * uSize, 1.0);
}"""

        // uMode 0: a gas puff from the atlas; 1: a star flare with four spikes; 2: a thin glowing ring.
        const val SPRITE_FS = """precision mediump float; varying vec2 vUv;
uniform sampler2D uTex; uniform vec4 uCol; uniform float uMode; uniform vec2 uAtlas;
void main() {
  vec2 q = vUv * 2.0 - 1.0; float r = length(q); float a;
  if (uMode < 0.5) a = texture2D(uTex, uAtlas + vUv * 0.5).a;
  else if (uMode < 1.5) {
    a = exp(-r * 9.0) * 1.6 + exp(-r * 3.2) * 0.35;
    a += (exp(-abs(q.x) * 70.0) * exp(-abs(q.y) * 3.0) + exp(-abs(q.y) * 70.0) * exp(-abs(q.x) * 3.0)) * 0.7;
    a *= smoothstep(1.0, 0.75, r);
  } else {
    a = exp(-pow((r - 0.86) * 11.0, 2.0)) + exp(-pow((r - 0.86) * 4.0, 2.0)) * 0.25;
    a *= smoothstep(1.0, 0.94, r);
  }
  gl_FragColor = vec4(uCol.rgb * a * uCol.a, 1.0);
}"""

        // aPos: xyz + size; aCol: rgb + twinkle phase (negative: no twinkle, the sparks)
        const val POINT_VS = """attribute vec4 aPos; attribute vec4 aCol;
uniform mat4 uVP; uniform float uTime; uniform float uPx; uniform float uTw;
varying vec3 vC;
void main() {
  gl_Position = uVP * vec4(aPos.xyz, 1.0);
  float tw = aCol.w < 0.0 ? 1.0 : 0.7 + 0.3 * sin(uTime * (0.8 + fract(aCol.w) * 2.5) + aCol.w * 5.0);
  vC = aCol.rgb * mix(1.0, tw, uTw);
  gl_PointSize = max(1.0, aPos.w * uPx * 2.2);
}"""
        const val POINT_FS = """precision mediump float; varying vec3 vC;
void main() { vec2 q = gl_PointCoord * 2.0 - 1.0; float a = exp(-dot(q, q) * 3.5) * smoothstep(1.0, 0.6, length(q)); gl_FragColor = vec4(vC * a, 1.0); }"""

        const val ROCK_VS = """attribute vec3 aPos; attribute vec3 aNrm;
uniform mat4 uVP; uniform mat4 uM;
varying vec3 vW; varying vec3 vN; varying vec3 vO; varying vec3 vON;
void main() {
  vec4 w = uM * vec4(aPos, 1.0);
  vW = w.xyz; vN = (uM * vec4(aNrm, 0.0)).xyz; vO = aPos; vON = aNrm;
  gl_Position = uVP * w;
}"""
        const val ROCK_FS = PREC + """
uniform sampler2D uTex; uniform vec3 uCam; uniform vec3 uKey; uniform vec3 uKeyCol; uniform vec3 uRimDir; uniform vec3 uRimCol;
uniform vec3 uHit; uniform vec3 uHitCol; uniform float uHeat; uniform float uDim; uniform float uTexS;
varying vec3 vW; varying vec3 vN; varying vec3 vO; varying vec3 vON;
vec3 tri(vec3 p, vec3 n) {
  vec3 b = pow(abs(n), vec3(4.0)); b /= (b.x + b.y + b.z);
  return texture2D(uTex, p.yz * uTexS).rgb * b.x + texture2D(uTex, p.xz * uTexS + 0.37).rgb * b.y + texture2D(uTex, p.xy * uTexS + 0.71).rgb * b.z;
}
void main() {
  vec3 N = normalize(vN); vec3 V = normalize(uCam - vW);
  vec3 tex = tri(vO, normalize(vON));
  vec3 alb = pow(tex, vec3(2.2)) * vec3(0.92, 0.84, 0.76) * 1.25;
  float k = max(dot(N, uKey), 0.0);
  vec3 c = alb * uKeyCol * k + alb * vec3(0.025, 0.028, 0.05);
  vec3 H = normalize(uKey + V); c += uKeyCol * 0.08 * pow(max(dot(N, H), 0.0), 20.0) * k;
  float rim = pow(1.0 - max(dot(N, V), 0.0), 3.0); c += uRimCol * rim * (0.35 + 0.65 * max(dot(N, uRimDir), 0.0));
  vec3 Lh = uHit - vW; float dh = max(length(Lh), 1e-3); c += alb * uHitCol * max(dot(N, Lh / dh), 0.0) / (1.0 + dh * dh * 1.2);
  // heat: the dark cracks glow first, the whole surface when it is very hot
  float lum = dot(tex, vec3(0.33));
  float cracks = smoothstep(0.42, 0.18, lum);
  vec3 hot = mix(vec3(0.9, 0.18, 0.02), vec3(1.0, 0.75, 0.35), clamp(uHeat, 0.0, 1.0));
  c += hot * uHeat * (0.15 + cracks * 2.2) * 1.4;
  c *= uDim;
  c = clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
  gl_FragColor = vec4(pow(c, vec3(1.0 / 2.2)), 1.0);
}"""
    }
}
