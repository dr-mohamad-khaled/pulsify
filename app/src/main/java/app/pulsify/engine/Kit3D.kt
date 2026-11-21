package app.pulsify.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min

/** Bone ids of a KIT1 model (the same order as the exporter). */
object KitBone { const val BODY = 0; const val HEAD = 1; const val EYE_L = 2; const val EYE_R = 3; const val EAR_L = 4; const val EAR_R = 5; const val LEG_L = 6; const val LEG_R = 7; const val TAIL = 8 }

/** Everything that moves on the 3D kitty, in radians and plain factors. */
class KitPose {
    var yaw = 0f; var pitch = 0f            // whole body: turn, lean forward (+)
    var squashY = 1f; var squashX = 1f      // squash and stretch about the feet
    var headYaw = 0f; var headPitch = 0f; var headTilt = 0f
    var blinkL = 1f; var blinkR = 1f; var eyeScale = 1f
    var earFlickL = 0f; var earFlickR = 0f; var earDroop = 0f
    var legSwingL = 0f; var legSwingR = 0f; var legSpreadL = 0f; var legSpreadR = 0f
    var tailCurl = 0f; var tailWag = 0f; var tailPhase = 0f; var tailDroop = 0f
    var glow = 0f; var dim = 0f

    private val snap = FloatArray(26)
    private fun pack(o: FloatArray) {
        o[0] = yaw; o[1] = pitch; o[2] = squashY; o[3] = squashX; o[4] = headYaw; o[5] = headPitch; o[6] = headTilt
        o[7] = blinkL; o[8] = blinkR; o[9] = eyeScale; o[10] = earFlickL; o[11] = earFlickR; o[12] = earDroop
        o[13] = legSwingL; o[14] = legSwingR; o[15] = legSpreadL; o[16] = legSpreadR; o[17] = tailCurl; o[18] = tailWag
        o[19] = tailPhase; o[20] = tailDroop; o[21] = glow; o[22] = dim
    }
    private val cur = FloatArray(26)
    /** True when the pose moved enough since the last [markDrawn] to be worth redrawing. */
    fun changed(): Boolean {
        pack(cur)
        for (i in cur.indices) {
            val eps = if (i == 19) 0.04f else 0.0025f
            if (kotlin.math.abs(cur[i] - snap[i]) > eps) return true
        }
        return false
    }
    fun markDrawn() { pack(snap) }
}

/**
 * Loads a KIT1 kitty (mesh + coat mask) and draws it, posed, into its own small texture with a
 * depth buffer. The texture is only re-rendered when the pose changes; every frame just stamps it.
 */
class Kit3D(private val context: Context) {
    private var prog = 0
    private var fxProg = 0
    private var quad = 0
    private var vbo = 0; private var ibo = 0; private var icount = 0
    private var maskTex = 0
    private val uni = HashMap<String, Int>()
    private val fxUni = HashMap<String, Int>()
    val pivots = Array(9) { FloatArray(3) }
    var floorZ = 0f; private set
    var height = 1.5f; private set
    private var loaded = -1

    // Offscreen sprite.
    private var fbo = 0; private var colTex = 0; private var depthRb = 0
    var spriteSize = 0; private set
    private var dirty = true

    // Matrices.
    private val vp = FloatArray(16)
    private val bones = FloatArray(16 * 9)
    private val tmp = FloatArray(16); private val tmp2 = FloatArray(16)
    private val conv = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, -1f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f)
    /** Half the side of the square box (model units) the sprite covers, centred at [boxCz]. */
    var boxR = 1f; private set
    var boxCz = 0.5f; private set

    fun create() {
        val v = context.assets.open("shaders/kit.vert").bufferedReader().use { it.readText() }
        val f = context.assets.open("shaders/kit.frag").bufferedReader().use { it.readText() }
        prog = Gl.program(v, f)
        for (n in listOf("uVP", "uBone", "uPush", "uTailPiv", "uTail", "uOutline", "uPoints", "uMask", "uFur", "uStripe", "uCream",
            "uPoint", "uIris", "uInk", "uLight", "uDim", "uGlow")) uni[n] = GLES20.glGetUniformLocation(prog, n)
        uni["aNrm"] = GLES20.glGetAttribLocation(prog, "aNrm"); uni["aUv"] = GLES20.glGetAttribLocation(prog, "aUv")
        uni["aInfo"] = GLES20.glGetAttribLocation(prog, "aInfo")
        val fv = context.assets.open("shaders/kitfx.vert").bufferedReader().use { it.readText() }
        val ff = context.assets.open("shaders/kitfx.frag").bufferedReader().use { it.readText() }
        fxProg = Gl.program(fv, ff)
        for (n in listOf("uRect", "uRot", "uAspect", "uTex", "uMode", "uColor", "uCable")) fxUni[n] = GLES20.glGetUniformLocation(fxProg, n)
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0); quad = ids[0]
        val q = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        val fb = ByteBuffer.allocateDirect(q.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(q); fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quad)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, q.size * 4, fb, GLES20.GL_STATIC_DRAW)
        loaded = -1
    }

    /** Loads design 0, 1, 2 (kitty_a/b/c). Cheap when it is already loaded. */
    fun load(design: Int) {
        if (design == loaded) return
        freeModel()
        val name = "kitty_" + ('a' + design.coerceIn(0, 2))
        val bytes = context.assets.open("models/$name.kit").use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bb.get().toInt() == 'K'.code && bb.get().toInt() == 'I'.code && bb.get().toInt() == 'T'.code && bb.get().toInt() == '1'.code) { "not a KIT1 model" }
        val vc = bb.int; val ic = bb.int; floorZ = bb.float
        for (i in 0 until 9) { pivots[i][0] = bb.float; pivots[i][1] = bb.float; pivots[i][2] = bb.float }
        val off = 16 + 108
        var top = 0f
        for (i in 0 until vc) top = max(top, bb.getFloat(off + i * 24 + 8))
        height = top - floorZ
        val vbuf = ByteBuffer.allocateDirect(vc * 24).order(ByteOrder.nativeOrder())
        vbuf.put(bytes, off, vc * 24); vbuf.position(0)
        val ibuf = ByteBuffer.allocateDirect(ic * 2).order(ByteOrder.nativeOrder())
        ibuf.put(bytes, off + vc * 24, ic * 2); ibuf.position(0)
        val ids = IntArray(2)
        GLES20.glGenBuffers(2, ids, 0); vbo = ids[0]; ibo = ids[1]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vc * 24, vbuf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, ic * 2, ibuf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        icount = ic
        val bmp = context.assets.open("models/${name}_mask.png").use { BitmapFactory.decodeStream(it) }
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0); maskTex = t[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        // A square box around the cat roomy enough for raised arms, a lifted tail and a turn.
        boxR = height * 0.78f
        boxCz = floorZ + height * 0.5f
        loaded = design
        dirty = true
    }

    /** Sprite resolution follows the cat's size on screen (capped, the GPU rarely needs more). */
    fun ensureSprite(px: Int) {
        val n = px.coerceIn(128, 900)
        if (fbo != 0 && kotlin.math.abs(n - spriteSize) < 24) return
        freeSprite()
        spriteSize = n
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0); colTex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colTex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, n, n, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glGenRenderbuffers(1, ids, 0); depthRb = ids[0]
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthRb)
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, n, n)
        GLES20.glGenFramebuffers(1, ids, 0); fbo = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, colTex, 0)
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, depthRb)
        val st = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target)
        check(st == GLES20.GL_FRAMEBUFFER_COMPLETE) { "kitty sprite fbo $st" }
        dirty = true
    }

    fun invalidate() { dirty = true }

    // ---- Posing ----

    private fun around(out: FloatArray, oi: Int, parent: FloatArray, pi: Int, piv: FloatArray, block: (FloatArray) -> Unit) {
        // out = parent * T(piv) * R * T(-piv)
        Matrix.setIdentityM(tmp, 0)
        Matrix.translateM(tmp, 0, piv[0], piv[1], piv[2])
        block(tmp)
        Matrix.translateM(tmp, 0, -piv[0], -piv[1], -piv[2])
        Matrix.multiplyMM(out, oi, parent, pi, tmp, 0)
    }

    private fun deg(r: Float) = r * 57.29578f

    private fun pose(p: KitPose) {
        val feet = floatArrayOf(0f, 0f, floorZ)
        // body: conv * T(feet) * Rz(yaw) * Rx(lean) * S * T(-feet)
        around(bones, 0, conv, 0, feet) { m ->
            Matrix.rotateM(m, 0, deg(p.yaw), 0f, 0f, 1f)
            Matrix.rotateM(m, 0, deg(-p.pitch), 1f, 0f, 0f)
            Matrix.scaleM(m, 0, p.squashX, p.squashX, p.squashY)
        }
        around(bones, 16, bones, 0, pivots[KitBone.HEAD]) { m ->
            Matrix.rotateM(m, 0, deg(p.headYaw), 0f, 0f, 1f)
            Matrix.rotateM(m, 0, deg(-p.headPitch), 1f, 0f, 0f)
            Matrix.rotateM(m, 0, deg(p.headTilt), 0f, 1f, 0f)
        }
        around(bones, 32, bones, 16, pivots[KitBone.EYE_L]) { m -> Matrix.scaleM(m, 0, p.eyeScale, p.eyeScale, p.eyeScale * max(0.08f, p.blinkL)) }
        around(bones, 48, bones, 16, pivots[KitBone.EYE_R]) { m -> Matrix.scaleM(m, 0, p.eyeScale, p.eyeScale, p.eyeScale * max(0.08f, p.blinkR)) }
        around(bones, 64, bones, 16, pivots[KitBone.EAR_L]) { m ->
            Matrix.rotateM(m, 0, deg(p.earFlickL), 1f, 0f, 0f); Matrix.rotateM(m, 0, deg(-p.earDroop), 0f, 1f, 0f)
        }
        around(bones, 80, bones, 16, pivots[KitBone.EAR_R]) { m ->
            Matrix.rotateM(m, 0, deg(p.earFlickR), 1f, 0f, 0f); Matrix.rotateM(m, 0, deg(p.earDroop), 0f, 1f, 0f)
        }
        around(bones, 96, bones, 0, pivots[KitBone.LEG_L]) { m ->
            Matrix.rotateM(m, 0, deg(p.legSpreadL), 0f, 1f, 0f); Matrix.rotateM(m, 0, deg(-p.legSwingL), 1f, 0f, 0f)
        }
        around(bones, 112, bones, 0, pivots[KitBone.LEG_R]) { m ->
            Matrix.rotateM(m, 0, deg(-p.legSpreadR), 0f, 1f, 0f); Matrix.rotateM(m, 0, deg(-p.legSwingR), 1f, 0f, 0f)
        }
        System.arraycopy(bones, 0, bones, 128, 16)
    }

    private fun camera() {
        // A long lens from a little above: almost flat, like a storybook, but the turns still read as 3D.
        val dist = height * 9f
        val up = dist * 0.1f
        val cz = boxCz
        Matrix.setLookAtM(tmp, 0, 0f, cz + up, dist, 0f, cz, 0f, 0f, 1f, 0f)
        val fov = deg(2f * atan(boxR / dist))
        Matrix.perspectiveM(tmp2, 0, fov, 1f, dist - boxR * 2f, dist + boxR * 2f)
        Matrix.multiplyMM(vp, 0, tmp2, 0, tmp, 0)
    }

    class Palette {
        val fur = FloatArray(3); val stripe = FloatArray(3); val cream = FloatArray(3); val point = FloatArray(3); val iris = FloatArray(3)
        var points = 0f
    }

    private val saveVp = IntArray(4)

    /** Renders the posed cat into the sprite texture if anything changed. */
    fun render(p: KitPose, pal: Palette, force: Boolean = false) {
        if (loaded < 0 || fbo == 0) return
        if (!dirty && !force && !p.changed()) return
        pose(p); camera()
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, saveVp, 0)       // the caller's viewport, restored below
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, spriteSize, spriteSize)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClearDepthf(1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glDepthFunc(GLES20.GL_LEQUAL); GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND); GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glUseProgram(prog)
        GLES20.glUniformMatrix4fv(uni["uVP"]!!, 1, false, vp, 0)
        GLES20.glUniformMatrix4fv(uni["uBone"]!!, 9, false, bones, 0)
        val tp = pivots[KitBone.TAIL]
        GLES20.glUniform3f(uni["uTailPiv"]!!, tp[0], tp[1], tp[2])
        GLES20.glUniform4f(uni["uTail"]!!, p.tailCurl, p.tailWag, p.tailPhase, p.tailDroop)
        GLES20.glUniform1f(uni["uPoints"]!!, pal.points)
        GLES20.glUniform3fv(uni["uFur"]!!, 1, pal.fur, 0); GLES20.glUniform3fv(uni["uStripe"]!!, 1, pal.stripe, 0)
        GLES20.glUniform3fv(uni["uCream"]!!, 1, pal.cream, 0); GLES20.glUniform3fv(uni["uPoint"]!!, 1, pal.point, 0)
        GLES20.glUniform3fv(uni["uIris"]!!, 1, pal.iris, 0)
        GLES20.glUniform3f(uni["uInk"]!!, 0.23f, 0.11f, 0.07f)
        GLES20.glUniform3f(uni["uLight"]!!, -0.52f, 0.62f, 0.59f)
        GLES20.glUniform1f(uni["uDim"]!!, p.dim); GLES20.glUniform1f(uni["uGlow"]!!, p.glow)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTex)
        GLES20.glUniform1i(uni["uMask"]!!, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        val aN = uni["aNrm"]!!; val aU = uni["aUv"]!!; val aI = uni["aInfo"]!!
        GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 24, 0)
        GLES20.glEnableVertexAttribArray(aN); GLES20.glVertexAttribPointer(aN, 3, GLES20.GL_BYTE, true, 24, 12)
        GLES20.glEnableVertexAttribArray(aU); GLES20.glVertexAttribPointer(aU, 2, GLES20.GL_UNSIGNED_SHORT, true, 24, 16)
        GLES20.glEnableVertexAttribArray(aI); GLES20.glVertexAttribPointer(aI, 4, GLES20.GL_UNSIGNED_BYTE, false, 24, 20)
        // Ink outline first (an inflated shell pushed slightly behind), then the colours on top.
        GLES20.glUniform1f(uni["uOutline"]!!, height * 0.0085f)
        GLES20.glUniform1f(uni["uPush"]!!, height * 0.025f * 2f / (4f * boxR))
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, icount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glUniform1f(uni["uOutline"]!!, 0f)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, icount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glDisableVertexAttribArray(aN); GLES20.glDisableVertexAttribArray(aU); GLES20.glDisableVertexAttribArray(aI)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target)
        GLES20.glViewport(saveVp[0], saveVp[1], saveVp[2], saveVp[3])
        p.markDrawn(); dirty = false
    }

    // ---- Compositing on the screen ----

    private var vpW = 1; private var vpH = 1
    private val vpBuf = IntArray(4)

    /** Starts a compositing batch into the current target and viewport. */
    fun beginFx() {
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vpBuf, 0)
        vpW = max(1, vpBuf[2]); vpH = max(1, vpBuf[3])
        GLES20.glUseProgram(fxProg)
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quad)
        GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glUniform1f(fxUni["uAspect"]!!, vpW.toFloat() / vpH)
    }

    /** uv (centred, short side = 1, y up) → clip. */
    private fun cx(u: Float) = u * 2f * min(vpW, vpH) / vpW
    private fun cy(v: Float) = v * 2f * min(vpW, vpH) / vpH

    /** A quad centred at (x, y) uv with half sizes (hw, hh) uv. */
    fun quad(mode: Float, x: Float, y: Float, hw: Float, hh: Float, rot: Float, r: Float, g: Float, b: Float, a: Float) {
        GLES20.glUniform4f(fxUni["uRect"]!!, cx(x - hw), cy(y - hh), cx(x + hw), cy(y + hh))
        GLES20.glUniform1f(fxUni["uRot"]!!, rot)
        GLES20.glUniform1f(fxUni["uMode"]!!, mode)
        GLES20.glUniform4f(fxUni["uColor"]!!, r, g, b, a)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** Stamps the sprite so that the box centre lands on (x, y) uv, scaled to [boxHalfUv]. */
    fun sprite(x: Float, y: Float, boxHalfUv: Float, rot: Float, alpha: Float) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colTex)
        GLES20.glUniform1i(fxUni["uTex"]!!, 0)
        quad(0f, x, y, boxHalfUv, boxHalfUv, rot, 1f, 1f, 1f, alpha)
    }

    private val cableBuf = FloatArray(24)
    /** The charging cable through 12 points (uv), drawn in one quad around them. */
    fun cable(pts: FloatArray, thick: Float) {
        var x0 = 1e9f; var y0 = 1e9f; var x1 = -1e9f; var y1 = -1e9f
        for (i in 0 until 12) { x0 = min(x0, pts[i * 2]); x1 = max(x1, pts[i * 2]); y0 = min(y0, pts[i * 2 + 1]); y1 = max(y1, pts[i * 2 + 1]) }
        val pad = thick * 4f
        x0 -= pad; y0 -= pad; x1 += pad; y1 += pad
        val w = x1 - x0; val h = y1 - y0
        // the quad's uv is (0..1) across its box; express points and thickness in that space
        for (i in 0 until 12) { cableBuf[i * 2] = (pts[i * 2] - x0) / w; cableBuf[i * 2 + 1] = (pts[i * 2 + 1] - y0) / h }
        GLES20.glUniform4fv(fxUni["uCable"]!!, 6, cableBuf, 0)
        quad(7f, (x0 + x1) / 2f, (y0 + y1) / 2f, w / 2f, h / 2f, 0f, 1f, 1f, 1f, thick / max(w, h))
    }

    fun endFx() {
        GLES20.glDisableVertexAttribArray(0)
    }

    private fun freeModel() {
        if (vbo != 0) GLES20.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0)
        if (maskTex != 0) GLES20.glDeleteTextures(1, intArrayOf(maskTex), 0)
        vbo = 0; ibo = 0; maskTex = 0; loaded = -1
    }

    private fun freeSprite() {
        if (fbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (colTex != 0) GLES20.glDeleteTextures(1, intArrayOf(colTex), 0)
        if (depthRb != 0) GLES20.glDeleteRenderbuffers(1, intArrayOf(depthRb), 0)
        fbo = 0; colTex = 0; depthRb = 0; spriteSize = 0
    }

    fun release() {
        freeModel(); freeSprite()
        if (prog != 0) GLES20.glDeleteProgram(prog)
        if (fxProg != 0) GLES20.glDeleteProgram(fxProg)
        if (quad != 0) GLES20.glDeleteBuffers(1, intArrayOf(quad), 0)
        prog = 0; fxProg = 0; quad = 0
    }
}
