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

/** One baked animation of a PET1/PET2 model: skin matrices (3x4 per bone) and eye/face controls per frame. */
class PetClip(val name: String, val frames: Int, val loop: Boolean, val mats: FloatArray, val ctl: FloatArray, val fps: Float, val nctl: Int = 8) {
    val duration: Float get() = (frames - 1) / fps
}

/**
 * A rigged Meshy creature (PET1, written by tools/petexport.py): one skinned mesh with its colour
 * texture plus the props it plays with, and every clip baked to skin matrices. Draws into its own
 * square sprite with a depth buffer; the scene stamps the sprite where the creature stands.
 */
class Pet3D(private val context: Context) {
    private var prog = 0
    private var fxProg = 0
    private var quad = 0
    private var vbo = 0; private var ibo = 0; private var icount = 0
    private var tex = 0; private var eyeTex = 0; private var nrmTex = 0; private var idxType = GLES20.GL_UNSIGNED_SHORT
    private val uni = HashMap<String, Int>()
    private val fxUni = HashMap<String, Int>()
    var loaded = ""; private set
    var nBones = 0; private set
    var height = 1f; private set
    var eyeMode = 0; private set
    private val fur = FloatArray(3); private val lash = FloatArray(3); private val iris = FloatArray(4)
    val clips = HashMap<String, PetClip>()

    // offscreen sprite
    private var fbo = 0; private var colTex = 0; private var depthRb = 0
    var spriteSize = 0; private set
    /** Half the side of the square box (model units) the sprite covers, centred [boxCz] above the floor. */
    val boxR = 0.9f
    val boxCz = 0.62f

    // current pose
    val pose = FloatArray(MAX_BONES * 12)
    val eyeL = FloatArray(4); val eyeR = FloatArray(4)
    /** happy (^^), pupil scale — PET2 eyes only. */
    val mood = floatArrayOf(0f, 1f)
    var nCtl = 8; private set
    var face = 0f; var facePhase = 0f
    var yaw = 0f; var lean = 0f
    /** Height (model units) the yaw/lean turn around: 0 = the feet, boxCz = the middle of the sprite. */
    var pivot = 0f
    var dim = 0f; var glow = 0f
    /** x,y = bend amount, z,w = scale (5.9) */
    val bend = floatArrayOf(0f, 0f, 1f, 1f)
    var squash = 0f
    /** Dual-quaternion skinning (models exported with flag 2): up to 60 bones, joints keep their volume. */
    var dq = false; private set
    private val dqBuf = FloatArray(MAX_BONES * 8)
    /** Eye mode 4 (glow face): grin centre line / half height, and how open the mouth is (1 = as painted). */
    val mouthCurve = floatArrayOf(0f, 0f, 1f, 0f)
    var mouthOpen = 1f

    private val vp = FloatArray(16); private val model = FloatArray(16)
    private val tmp = FloatArray(16); private val tmp2 = FloatArray(16)

    fun create() {
        prog = Gl.program(asset("shaders/pet.vert"), asset("shaders/pet.frag"))
        uni["uB"] = GLES20.glGetUniformLocation(prog, "uB[0]").let { if (it >= 0) it else GLES20.glGetUniformLocation(prog, "uB") }
        for (n in listOf("uVP", "uModel", "uTex", "uEyes", "uEyeMode", "uEyeL", "uEyeR", "uMood", "uIris", "uFace", "uFur", "uLash",
            "uLightDir", "uDim", "uGlow", "uScreenHalf", "uNrm", "uHasNrm", "uBend", "uSquash", "uDQ", "uShell", "uMouth", "uMouthOpen", "uDither")) uni[n] = GLES20.glGetUniformLocation(prog, n)
        for (n in listOf("aNrm", "aUv", "aEye", "aBone", "aWeight", "aCol", "aEye2")) uni[n] = GLES20.glGetAttribLocation(prog, n)
        fxProg = Gl.program(asset("shaders/kitfx.vert"), asset("shaders/kitfx.frag"))
        for (n in listOf("uRect", "uRot", "uAspect", "uTex", "uMode", "uColor", "uCable")) fxUni[n] = GLES20.glGetUniformLocation(fxProg, n)
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0); quad = ids[0]
        val q = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        val fb = ByteBuffer.allocateDirect(q.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(q); fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quad)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, q.size * 4, fb, GLES20.GL_STATIC_DRAW)
        // a fresh GL context: the old ids are gone, reload into new ones
        vbo = 0; ibo = 0; tex = 0; eyeTex = 0; nrmTex = 0; fbo = 0; colTex = 0; depthRb = 0; spriteSize = 0
        val keep = loaded; loaded = ""
        if (keep.isNotEmpty()) load(keep)
    }

    private fun asset(p: String) = context.assets.open(p).bufferedReader().use { it.readText() }

    /** Loads assets/pets/<name>.pet (+ textures). Cheap when it is already loaded. */
    fun load(name: String) {
        if (name == loaded) return
        freeModel()
        val bytes = context.assets.open("pets/$name.pet").use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bb.get().toInt() == 'P'.code && bb.get().toInt() == 'E'.code && bb.get().toInt() == 'T'.code) { "not a PET model" }
        val ver = bb.get().toInt() - '0'.code
        require(ver in 1..3) { "unknown PET version $ver" }
        val nv = bb.int; val ni = bb.int; nBones = bb.int; val nc = bb.int
        
        height = bb.float; val fps = bb.float; eyeMode = bb.int
        nCtl = if (ver >= 2) bb.int else 8
        val flags = if (ver >= 3) bb.int else 0
        val ib = if (flags and 1 != 0) 4 else 2
        dq = flags and 2 != 0
        require(nBones <= (if (dq) MAX_BONES else 40)) { "too many bones: $nBones" }
        for (i in 0 until 3) fur[i] = bb.float
        for (i in 0 until 3) lash[i] = bb.float
        if (eyeMode == 3) for (i in 0 until 4) iris[i] = bb.float
        val vOff = bb.position()
        val vbuf = ByteBuffer.allocateDirect(nv * 40).order(ByteOrder.nativeOrder()); vbuf.put(bytes, vOff, nv * 40); vbuf.position(0)
        val iOff = vOff + nv * 40
        val ibuf = ByteBuffer.allocateDirect(ni * ib).order(ByteOrder.nativeOrder()); ibuf.put(bytes, iOff, ni * ib); ibuf.position(0)
        val ids = IntArray(2)
        GLES20.glGenBuffers(2, ids, 0); vbo = ids[0]; ibo = ids[1]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, nv * 40, vbuf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, ni * ib, ibuf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        icount = ni
        idxType = if (ib == 4) GLES20.GL_UNSIGNED_INT else GLES20.GL_UNSIGNED_SHORT
        bb.position(iOff + ni * ib)
        clips.clear()
        repeat(nc) {
            val len = bb.get().toInt() and 0xFF
            val nm = String(bytes, bb.position(), len); bb.position(bb.position() + len)
            val frames = bb.short.toInt() and 0xFFFF; val loop = bb.get().toInt() != 0
            val mats = FloatArray(frames * nBones * 12)
            for (i in mats.indices) mats[i] = half(bb.short)
            val ctl = FloatArray(frames * nCtl)
            for (i in ctl.indices) ctl[i] = half(bb.short)
            clips[nm] = PetClip(nm, frames, loop, mats, ctl, fps, nCtl)
        }
        tex = texture("pets/$name.jpg")
        eyeTex = if (eyeMode == 1 || eyeMode == 3) texture("pets/${name}_eyes.png", straightAlpha = eyeMode == 3) else 0
        nrmTex = if (hasAsset("pets/${name}_n.jpg")) texture("pets/${name}_n.jpg", true, maxSide = 1024) else 0
        loaded = name
        // rest pose until the scene poses it
        for (b in 0 until MAX_BONES) { val o = b * 12; java.util.Arrays.fill(pose, o, o + 12, 0f); pose[o] = 1f; pose[o + 5] = 1f; pose[o + 10] = 1f }
    }

    private fun hasAsset(p: String) = try { context.assets.open(p).close(); true } catch (e: Exception) { false }

    /** [maxSide]: power-of-two cap; normal maps read fine at half size and save ~16 MB each. */
    private fun texture(path: String, straightAlpha: Boolean = false, maxSide: Int = 4096): Int {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, b) }
        var ss = 1; while (max(b.outWidth, b.outHeight) / ss > maxSide) ss *= 2
        val opts = BitmapFactory.Options().apply { inPremultiplied = !straightAlpha; inSampleSize = ss }
        val bmp = context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }!!
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        bmp.recycle()
        return t[0]
    }

    /** Writes [clip] at time [t] (seconds) into [out]/[ctl] (up to MAX_CTL controls; missing ones are 0). */
    fun sample(clip: PetClip, t: Float, out: FloatArray, ctl: FloatArray) {
        val f = (t * clip.fps).coerceIn(0f, (clip.frames - 1).toFloat())
        val f0 = f.toInt().coerceAtMost(clip.frames - 1); val f1 = min(f0 + 1, clip.frames - 1); val a = f - f0
        val n = nBones * 12
        val o0 = f0 * n; val o1 = f1 * n
        for (i in 0 until n) out[i] = clip.mats[o0 + i] + (clip.mats[o1 + i] - clip.mats[o0 + i]) * a
        val k = clip.nctl
        for (i in 0 until min(k, ctl.size)) ctl[i] = clip.ctl[f0 * k + i] + (clip.ctl[f1 * k + i] - clip.ctl[f0 * k + i]) * a
        for (i in k until ctl.size) ctl[i] = 0f
    }

    fun ensureSprite(px: Int) {
        // power-of-two so it can be mip-mapped: the sprite is drawn smaller than it is rendered, and
        // trilinear filtering turns the supersampling into real anti-aliasing instead of sparkle
        var n = 256
        while (n < px && n < 2048) n *= 2
        if (fbo != 0 && n == spriteSize) return
        freeSprite()
        spriteSize = n
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0); colTex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colTex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, n, n, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
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
        check(st == GLES20.GL_FRAMEBUFFER_COMPLETE) { "pet sprite fbo $st" }
    }

    private fun deg(r: Float) = r * 57.29578f
    private val saveVp = IntArray(4)

    /** Renders the current [pose] into the sprite. */
    fun render() {
        if (loaded.isEmpty() || fbo == 0) return
        // model: Blender (x, y, z) -> GL (x, z, -y), turned by [yaw] about the feet, leaned by [lean]
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, 0f, pivot, 0f)
        Matrix.rotateM(model, 0, deg(yaw), 0f, 1f, 0f)
        Matrix.rotateM(model, 0, deg(lean), 1f, 0f, 0f)
        Matrix.translateM(model, 0, 0f, -pivot, 0f)
        val conv = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, -1f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f)
        Matrix.multiplyMM(tmp, 0, model, 0, conv, 0)
        System.arraycopy(tmp, 0, model, 0, 16)
        // camera: long lens from slightly above, like the 3D kitty
        val dist = 9f
        Matrix.setLookAtM(tmp, 0, 0f, boxCz + dist * 0.12f, dist, 0f, boxCz, 0f, 0f, 1f, 0f)
        val fov = deg(2f * atan(boxR / dist))
        Matrix.perspectiveM(tmp2, 0, fov, 1f, dist - boxR * 3f, dist + boxR * 3f)
        Matrix.multiplyMM(vp, 0, tmp2, 0, tmp, 0)

        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, saveVp, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, spriteSize, spriteSize)
        GLES20.glClearColor(0f, 0f, 0f, 0f); GLES20.glClearDepthf(1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glDepthFunc(GLES20.GL_LEQUAL); GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND); GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glUseProgram(prog)
        GLES20.glUniformMatrix4fv(uni["uVP"]!!, 1, false, vp, 0)
        GLES20.glUniformMatrix4fv(uni["uModel"]!!, 1, false, model, 0)
        if (dq) { toDQ(pose, nBones, dqBuf); GLES20.glUniform4fv(uni["uB"]!!, nBones * 2, dqBuf, 0) }
        else GLES20.glUniform4fv(uni["uB"]!!, nBones * 3, pose, 0)
        uni["uDQ"]?.let { if (it >= 0) GLES20.glUniform1f(it, if (dq) 1f else 0f) }
        uni["uShell"]?.let { if (it >= 0) GLES20.glUniform3f(it, 0f, 0f, 0f) }
        uni["uDither"]?.let { if (it >= 0) GLES20.glUniform2f(it, 0f, 0f) }
        uni["uMouth"]?.let { if (it >= 0) GLES20.glUniform4fv(it, 1, mouthCurve, 0) }
        uni["uMouthOpen"]?.let { if (it >= 0) GLES20.glUniform1f(it, mouthOpen) }
        GLES20.glUniform1f(uni["uEyeMode"]!!, eyeMode.toFloat())
        GLES20.glUniform4fv(uni["uEyeL"]!!, 1, eyeL, 0); GLES20.glUniform4fv(uni["uEyeR"]!!, 1, eyeR, 0)
        GLES20.glUniform2f(uni["uMood"]!!, mood[0], mood[1])
        GLES20.glUniform2f(uni["uFace"]!!, face, facePhase)
        GLES20.glUniform4fv(uni["uIris"]!!, 1, iris, 0)
        GLES20.glUniform3fv(uni["uFur"]!!, 1, fur, 0); GLES20.glUniform3fv(uni["uLash"]!!, 1, lash, 0)
        GLES20.glUniform3f(uni["uLightDir"]!!, 0.45f, 0.65f, 0.62f)
        GLES20.glUniform1f(uni["uDim"]!!, dim); GLES20.glUniform1f(uni["uGlow"]!!, glow)
        GLES20.glUniform3f(uni["uScreenHalf"]!!, 0.244f, 0.142f, 0f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(uni["uTex"]!!, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (eyeTex != 0) eyeTex else tex)
        GLES20.glUniform1i(uni["uEyes"]!!, 1)
        uni["uBend"]?.let { if (it >= 0) GLES20.glUniform4fv(it, 1, bend, 0) }
        uni["uSquash"]?.let { if (it >= 0) GLES20.glUniform1f(it, squash) }
        uni["uHasNrm"]?.let { if (it >= 0) GLES20.glUniform1f(it, if (nrmTex != 0) 1f else 0f) }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (nrmTex != 0) nrmTex else tex)
        uni["uNrm"]?.let { if (it >= 0) GLES20.glUniform1i(it, 2) }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        val aN = uni["aNrm"]!!; val aU = uni["aUv"]!!; val aE = uni["aEye"]!!; val aB = uni["aBone"]!!
        val aW = uni["aWeight"]!!; val aC = uni["aCol"]!!; val aI = uni["aEye2"]!!
        GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 40, 0)
        attr(aN, 4, GLES20.GL_BYTE, true, 12)
        attr(aU, 2, GLES20.GL_UNSIGNED_SHORT, true, 16)
        attr(aE, 2, GLES20.GL_SHORT, false, 20)
        attr(aB, 4, GLES20.GL_UNSIGNED_BYTE, false, 24)
        attr(aW, 4, GLES20.GL_UNSIGNED_BYTE, true, 28)
        attr(aC, 4, GLES20.GL_UNSIGNED_BYTE, true, 32)
        attr(aI, 2, GLES20.GL_SHORT, false, 36)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, icount, idxType, 0)
        for (a in listOf(aN, aU, aE, aB, aW, aC, aI)) if (a >= 0) GLES20.glDisableVertexAttribArray(a)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, Gl.target)
        GLES20.glViewport(saveVp[0], saveVp[1], saveVp[2], saveVp[3])
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colTex); GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
    }

    private fun attr(loc: Int, n: Int, type: Int, norm: Boolean, off: Int) {
        if (loc < 0) return
        GLES20.glEnableVertexAttribArray(loc); GLES20.glVertexAttribPointer(loc, n, type, norm, 40, off)
    }

    // ---- compositing (same quad shader as the 3D kitty) ----
    private var vpW = 1; private var vpH = 1
    private val vpBuf = IntArray(4)
    fun beginFx() {
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vpBuf, 0)
        vpW = max(1, vpBuf[2]); vpH = max(1, vpBuf[3])
        GLES20.glUseProgram(fxProg)
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quad)
        GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glUniform1f(fxUni["uAspect"]!!, vpW.toFloat() / vpH)
    }
    private fun cx(u: Float) = u * 2f * min(vpW, vpH) / vpW
    private fun cy(v: Float) = v * 2f * min(vpW, vpH) / vpH
    fun quad(mode: Float, x: Float, y: Float, hw: Float, hh: Float, rot: Float, r: Float, g: Float, b: Float, a: Float) {
        GLES20.glUniform4f(fxUni["uRect"]!!, cx(x - hw), cy(y - hh), cx(x + hw), cy(y + hh))
        GLES20.glUniform1f(fxUni["uRot"]!!, rot)
        GLES20.glUniform1f(fxUni["uMode"]!!, mode)
        GLES20.glUniform4f(fxUni["uColor"]!!, r, g, b, a)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }
    fun sprite(x: Float, y: Float, boxHalfUv: Float, rot: Float, alpha: Float) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colTex)
        GLES20.glUniform1i(fxUni["uTex"]!!, 0)
        quad(0f, x, y, boxHalfUv, boxHalfUv, rot, 1f, 1f, 1f, alpha)
    }
    fun endFx() { GLES20.glDisableVertexAttribArray(0) }

    /** Any texture (premultiplied) drawn as a quad, e.g. a speech bubble. Call between beginFx / endFx. */
    fun texQuad(t: Int, x: Float, y: Float, hw: Float, hh: Float, alpha: Float) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t)
        GLES20.glUniform1i(fxUni["uTex"]!!, 0)
        quad(0f, x, y, hw, hh, 0f, 1f, 1f, 1f, alpha)
    }
    private val pv = FloatArray(4); private val pw = FloatArray(4)
    /** A model-space point (Blender units, z up) after the last [render] -> position inside the sprite, -1..1 each way. */
    fun projectBox(p: FloatArray, out: FloatArray) {
        pv[0] = p[0]; pv[1] = p[1]; pv[2] = p[2]; pv[3] = 1f
        Matrix.multiplyMV(pw, 0, model, 0, pv, 0); Matrix.multiplyMV(pv, 0, vp, 0, pw, 0)
        out[0] = pv[0] / pv[3]; out[1] = pv[1] / pv[3]
    }

    private fun freeModel() {
        if (vbo != 0) GLES20.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0)
        if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
        if (eyeTex != 0) GLES20.glDeleteTextures(1, intArrayOf(eyeTex), 0)
        if (nrmTex != 0) GLES20.glDeleteTextures(1, intArrayOf(nrmTex), 0)
        nrmTex = 0
        vbo = 0; ibo = 0; tex = 0; eyeTex = 0; loaded = ""; clips.clear()
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

    companion object {
        const val MAX_BONES = 60
        /** Rigid 3x4 bone matrices -> unit dual quaternions (real xyzw, dual xyzw) for the DQ shader. */
        fun toDQ(m: FloatArray, nb: Int, out: FloatArray) {
            for (b in 0 until nb) {
                val o = b * 12
                val a00 = m[o]; val a01 = m[o + 1]; val a02 = m[o + 2]; val a10 = m[o + 4]; val a11 = m[o + 5]; val a12 = m[o + 6]; val a20 = m[o + 8]; val a21 = m[o + 9]; val a22 = m[o + 10]
                val tx = m[o + 3]; val ty = m[o + 7]; val tz = m[o + 11]
                var x: Float; var y: Float; var z: Float; var w: Float; val tr = a00 + a11 + a22
                if (tr > 0) { val s = kotlin.math.sqrt(tr + 1) * 2; w = .25f * s; x = (a21 - a12) / s; y = (a02 - a20) / s; z = (a10 - a01) / s }
                else if (a00 > a11 && a00 > a22) { val s = kotlin.math.sqrt(1 + a00 - a11 - a22) * 2; w = (a21 - a12) / s; x = .25f * s; y = (a01 + a10) / s; z = (a02 + a20) / s }
                else if (a11 > a22) { val s = kotlin.math.sqrt(1 + a11 - a00 - a22) * 2; w = (a02 - a20) / s; x = (a01 + a10) / s; y = .25f * s; z = (a12 + a21) / s }
                else { val s = kotlin.math.sqrt(1 + a22 - a00 - a11) * 2; w = (a10 - a01) / s; x = (a02 + a20) / s; y = (a12 + a21) / s; z = .25f * s }
                val l = kotlin.math.sqrt(x * x + y * y + z * z + w * w).let { if (it == 0f) 1f else it }; x /= l; y /= l; z /= l; w /= l
                val q = b * 8
                out[q] = x; out[q + 1] = y; out[q + 2] = z; out[q + 3] = w
                out[q + 4] = .5f * (tx * w + ty * z - tz * y); out[q + 5] = .5f * (-tx * z + ty * w + tz * x); out[q + 6] = .5f * (tx * y - ty * x + tz * w); out[q + 7] = -.5f * (tx * x + ty * y + tz * z)
            }
        }
        const val MAX_CTL = 12
        private val HALF: FloatArray by lazy { FloatArray(65536) { halfSlow(it.toShort()) } }
        fun half(s: Short): Float = HALF[s.toInt() and 0xFFFF]
        /** IEEE half -> float. */
        private fun halfSlow(s: Short): Float {
            val h = s.toInt() and 0xFFFF
            val sign = if (h and 0x8000 != 0) -1f else 1f
            val e = (h shr 10) and 0x1F; val m = h and 0x3FF
            return when (e) {
                0 -> sign * m / 1024f * 6.1035156e-5f
                31 -> if (m == 0) sign * Float.POSITIVE_INFINITY else Float.NaN
                else -> sign * (1f + m / 1024f) * Math.pow(2.0, (e - 15).toDouble()).toFloat()
            }
        }
    }
}
