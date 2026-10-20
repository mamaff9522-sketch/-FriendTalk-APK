/*
 * OpenGL ES 2.0 renderer applying a [FilterConfig] to camera frames using [FilterShaders].
 * Licensed under the Apache License, Version 2.0.
 */
package com.threejump.filterstudio.filterengine

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

/**
 * Usage: glSurfaceView.setEGLContextClientVersion(2); setRenderer(renderer); renderMode = WHEN_DIRTY.
 * For every camera frame call [submit] (upright, NOT mirrored bitmap + 478 normalized landmarks or null)
 * then glSurfaceView.requestRender(). Display is FILL_CENTER cropped and mirrored when [mirror] = true.
 */
class FilterRenderer : GLSurfaceView.Renderer {
    @Volatile var config: FilterConfig = FilterConfig("default", "default")
    @Volatile var bypass = false
    @Volatile var mirror = true
    /** Diagnostic view, see [DebugMode]. Not part of FilterConfig / export. */
    @Volatile var debug: DebugMode = DebugMode.NORMAL
    /** Called on the GL thread with the rendered frame (as displayed) after [requestCapture]. */
    @Volatile var onCaptured: ((Bitmap) -> Unit)? = null
    @Volatile private var captureRequested = false
    /** Last GL error / compile log, for diagnostics. */
    @Volatile var lastError: String? = null; private set
    @Volatile var drawMs = 0f; private set
    /** Diagnostic: centre pixel of the uploaded bitmap vs the same pixel read back from GL ("#RRGGBB vs #RRGGBB"). */
    @Volatile var probe: String? = null; private set
    private var frameCount = 0

    private class Frame(val bitmap: Bitmap, val points: FloatArray?)
    private val pending = AtomicReference<Frame?>()
    private var lastPoints: FloatArray? = null
    private var imgW = 0; private var imgH = 0
    private var viewW = 1; private var viewH = 1
    private var program = 0
    private var texImage = 0; private var texMask = 0; private var texMask2 = 0; private var texMask3 = 0
    private var maskW = 0; private var maskH = 0
    private val mask = FaceMaskBuilder()
    private val loc = HashMap<String, Int>()
    private var aPos = 0
    private val quad: FloatBuffer = QuadGeometry.newBuffer()

    // ---- camera source path ----
    /** BITMAP: upload the submitted bitmap (default). OES: sample a SurfaceTexture fed by CameraX Preview. */
    enum class CameraPath { BITMAP, OES }
    @Volatile var cameraPath = CameraPath.BITMAP
    /** AUTO: while on BITMAP, compare a hidden passthrough readback with the bitmap; switch to OES on mismatch. */
    @Volatile var autoPath = false
    /** Called (GL thread) when auto-check switches to OES. */
    @Volatile var onPathAutoSwitched: ((String) -> Unit)? = null
    @Volatile var pathInfo = "บิตแมป"; private set
    private var autoMismatch = 0; private var autoMatch = 0
    private var programOes = 0; private var aPosOes = 0
    private val locOes = HashMap<String, Int>()
    private var texOes = 0
    private var surfaceTexture: android.graphics.SurfaceTexture? = null
    @Volatile private var oesFrameAvailable = false
    private val stm = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
    /** CameraX TransformationInfo.rotationDegrees of the Preview stream (buffer -> upright; sets the image size). */
    @Volatile var externalRotation = 0
    /**
     * TransformationInfo.hasCameraTransform / isMirroring. With the camera transform the SurfaceTexture matrix
     * already rotates to upright (portrait-locked screens: no rotation left) and mirrors when isMirroring,
     * so the shader must not rotate again, only undo the mirroring.
     */
    @Volatile var externalCameraTransform = false
    @Volatile var externalMirroring = false
    private var extW = 0; private var extH = 0

    /**
     * GL thread only (use GLSurfaceView.queueEvent). Creates the OES texture + SurfaceTexture of the given
     * buffer size; [onFrame] is called from any thread when a new camera frame is available.
     */
    fun createExternalSurface(bufW: Int, bufH: Int, onFrame: () -> Unit): android.graphics.SurfaceTexture {
        releaseExternal()
        if (programOes == 0) {
            programOes = link(compile(GLES20.GL_VERTEX_SHADER, FilterShaders.VERTEX), compile(GLES20.GL_FRAGMENT_SHADER, FilterShaders.FRAGMENT_OES))
            aPosOes = GLES20.glGetAttribLocation(programOes, "aPos")
            for (u in FilterShaders.UNIFORMS + FilterShaders.OES_UNIFORMS) locOes[u] = GLES20.glGetUniformLocation(programOes, u)
        }
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0); texOes = t[0]
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texOes)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val st = android.graphics.SurfaceTexture(texOes)
        st.setDefaultBufferSize(bufW, bufH)
        st.setOnFrameAvailableListener { oesFrameAvailable = true; onFrame() }
        surfaceTexture = st; extW = bufW; extH = bufH
        return st
    }

    /** GL thread only. */
    fun releaseExternal() {
        surfaceTexture?.release(); surfaceTexture = null
        if (texOes != 0) { GLES20.glDeleteTextures(1, intArrayOf(texOes), 0); texOes = 0 }
    }

    fun submit(bitmap: Bitmap, points: FloatArray?) { pending.set(Frame(bitmap, points)) }

    // ---- 3Jump SDK addition (not in Filter Studio): offscreen rendering from an existing GL texture ----
    private var externalTex = 0
    /**
     * GL thread, after [onSurfaceCreated] on the same context. Renders [inputTex] (GL_TEXTURE_2D, w x h, row 0 =
     * image TOP, i.e. the same layout as an uploaded bitmap) with [config] into the currently bound framebuffer,
     * full size, not mirrored. [points]: 478 normalized (x,y,z) landmarks of that image, or null.
     */
    fun drawExternal(inputTex: Int, w: Int, h: Int, points: FloatArray?) {
        externalTex = inputTex; imgW = w; imgH = h; lastPoints = points
        viewW = w; viewH = h; GLES20.glViewport(0, 0, w, h)
        try { onDrawFrame(null) } finally { externalTex = 0 }
    }
    fun requestCapture() { captureRequested = true }

    override fun onSurfaceCreated(gl: GL10?, cfg: EGLConfig?) {
        program = link(compile(GLES20.GL_VERTEX_SHADER, FilterShaders.VERTEX), compile(GLES20.GL_FRAGMENT_SHADER, FilterShaders.FRAGMENT))
        aPos = GLES20.glGetAttribLocation(program, "aPos")   // queried after link
        programOes = 0; locOes.clear(); texOes = 0; surfaceTexture = null   // context (re)created: old GL objects are gone
        for (u in FilterShaders.UNIFORMS) loc[u] = GLES20.glGetUniformLocation(program, u)
        val t = IntArray(4); GLES20.glGenTextures(4, t, 0); texImage = t[0]; texMask = t[1]; texMask2 = t[2]; texMask3 = t[3]
        for (id in t) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
        imgW = 0; imgH = 0; maskW = 0; maskH = 0
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { viewW = max(1, w); viewH = max(1, h); GLES20.glViewport(0, 0, w, h) }

    override fun onDrawFrame(gl: GL10?) {
        val t0 = System.nanoTime()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val f = pending.getAndSet(null)
        var probeSrc = -1
        var striped = false
        val oesReady = cameraPath == CameraPath.OES && surfaceTexture != null && programOes != 0
        if (f != null && oesReady) { lastPoints = f.points; if (++frameCount % 30 == 0) probeSrc = f.bitmap.getPixel(f.bitmap.width / 2, f.bitmap.height / 2) }
        else if (f != null) {
            upload(texImage, f.bitmap, f.bitmap.width == imgW && f.bitmap.height == imgH)
            if (++frameCount % 30 == 0) {
                probeSrc = f.bitmap.getPixel(f.bitmap.width / 2, f.bitmap.height / 2)
                if (autoPath) striped = stripeCheck(f.bitmap)
            }
            imgW = f.bitmap.width; imgH = f.bitmap.height
            lastPoints = f.points
        }
        val oes = cameraPath == CameraPath.OES && surfaceTexture != null && programOes != 0
        if (oes) {
            if (oesFrameAvailable) { oesFrameAvailable = false; try { surfaceTexture!!.updateTexImage(); surfaceTexture!!.getTransformMatrix(stm) } catch (t: Throwable) { lastError = "OES: ${t.message}" } }
            val r = externalRotation; val swap = r % 180 != 0
            imgW = if (swap) extH else extW; imgH = if (swap) extW else extH
        }
        pathInfo = if (oes) "OES (SurfaceTexture)" else "บิตแมป"
        if (imgW == 0 || program == 0) return
        val dbg = debug
        val cfg = dbg.only?.let { g -> FilterGroup.values().filter { it.switchable }.fold(config) { c, x -> c.withEnabled(x, x == g) } } ?: config
        val m = mask.build(lastPoints, imgW, imgH, cfg)
        val same = m.width == maskW && m.height == maskH
        upload(texMask, m, same)
        upload(texMask2, mask.bitmap2!!, same)
        upload(texMask3, mask.bitmap3!!, same)
        maskW = m.width; maskH = m.height

        curOes = oes
        val prog = if (oes) programOes else program
        GLES20.glUseProgram(prog)
        if (oes) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE3); GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texOes)
            GLES20.glUniform1i(u("uTex"), 3)
            GLES20.glUniformMatrix4fv(locOes["uSTM"] ?: -1, 1, false, stm, 0)
            val camXform = externalCameraTransform
            GLES20.glUniform1f(locOes["uCamRot"] ?: -1, if (camXform) 0f else externalRotation.toFloat())
            GLES20.glUniform1f(locOes["uCamFlip"] ?: -1, if (camXform && externalMirroring) 1f else 0f)
        } else {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (externalTex != 0) externalTex else texImage)
            GLES20.glUniform1i(u("uTex"), 0)
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texMask)
        GLES20.glUniform1i(u("uMask"), 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texMask2)
        GLES20.glUniform1i(u("uMask2"), 2)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE4); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texMask3)
        GLES20.glUniform1i(u("uMask3"), 4)
        val s = max(viewW.toFloat() / imgW, viewH.toFloat() / imgH)
        GLES20.glUniform2f(u("uCrop"), viewW / (imgW * s), viewH / (imgH * s))
        GLES20.glUniform1f(u("uMirror"), if (mirror) 1f else 0f)
        GLES20.glUniform2f(u("uTexel"), 1f / imgW, 1f / imgH)
        GLES20.glUniform1f(u("uBypass"), if (bypass) 1f else 0f)
        GLES20.glUniform1f(u("uDebug"), dbg.shader)
        GLES20.glUniform1f(u("uIntensity"), cfg["intensity"])
        GLES20.glUniform1f(u("uSmoothOn"), on(cfg, FilterGroup.SMOOTH))
        GLES20.glUniform1f(u("uSmooth"), cfg["smoothness"])
        GLES20.glUniform1f(u("uRadius"), cfg["radius"])
        GLES20.glUniform1f(u("uEdge"), cfg["edgePreserve"])
        GLES20.glUniform1f(u("uBrightOn"), on(cfg, FilterGroup.BRIGHT))
        GLES20.glUniform1f(u("uBright"), cfg["brightness"])
        GLES20.glUniform1f(u("uContrast"), cfg["contrast"])
        GLES20.glUniform1f(u("uSat"), cfg["saturation"])
        GLES20.glUniform1f(u("uPinkOn"), on(cfg, FilterGroup.PINK))
        GLES20.glUniform1f(u("uPink"), cfg["pink"])
        GLES20.glUniform1f(u("uWarm"), cfg["warmth"])
        GLES20.glUniform1f(u("uTint"), cfg["tint"])
        GLES20.glUniform1f(u("uLipOn"), on(cfg, FilterGroup.LIPSTICK))
        GLES20.glUniform1f(u("uLipAmt"), cfg["lipstickAmount"])
        cfg.rgb(ColorSpecs.LIPSTICK).let { GLES20.glUniform3f(u("uLipColor"), it[0], it[1], it[2]) }
        GLES20.glUniform1f(u("uBrowOn"), on(cfg, FilterGroup.BROW))
        GLES20.glUniform1f(u("uBrowAmt"), cfg["browAmount"])
        cfg.rgb(ColorSpecs.BROW).let { GLES20.glUniform3f(u("uBrowColor"), it[0], it[1], it[2]) }
        val pts = lastPoints
        val hasCheeks = pts != null && pts.size > FaceContours.CHEEK_LEFT * 3 + 1
        GLES20.glUniform1f(u("uBlushOn"), if (hasCheeks) on(cfg, FilterGroup.BLUSH) else 0f)
        GLES20.glUniform1f(u("uBlushAmt"), cfg["blushAmount"])
        cfg.rgb(ColorSpecs.BLUSH).let { GLES20.glUniform3f(u("uBlushColor"), it[0], it[1], it[2]) }
        GLES20.glUniform2f(u("uImgSize"), imgW.toFloat(), imgH.toFloat())
        if (hasCheeks) {
            val p = pts!!
            val l = FaceContours.CHEEK_LEFT * 3; val r = FaceContours.CHEEK_RIGHT * 3
            GLES20.glUniform2f(u("uCheekL"), p[l], p[l + 1])
            GLES20.glUniform2f(u("uCheekR"), p[r], p[r + 1])
            // blush sigma in pixels ~ 9% of face width
            var minX = 1f; var maxX = 0f
            for (i in FaceContours.FACE_OVAL) { val x = p[i * 3]; if (x < minX) minX = x; if (x > maxX) maxX = x }
            GLES20.glUniform1f(u("uBlushSize"), (maxX - minX) * imgW * 0.09f * cfg["blushSize"])
        } else {
            GLES20.glUniform2f(u("uCheekL"), -1f, -1f); GLES20.glUniform2f(u("uCheekR"), -1f, -1f)
            GLES20.glUniform1f(u("uBlushSize"), 1f)
        }
        stageB(cfg, pts)
        drawQuad(if (oes) aPosOes else aPos)
        if (!oes && autoPath && probeSrc != -1) autoCheck(probeSrc, striped)
        drawMs = (System.nanoTime() - t0) / 1e6f
        if (probeSrc != -1) {
            // centre of the view = centre of the image (crop + mirror are symmetric about the centre)
            val px = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(viewW / 2, viewH / 2, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, px)
            val r = px.get(0).toInt() and 255; val g = px.get(1).toInt() and 255; val bl = px.get(2).toInt() and 255
            probe = "bitmap #%06X → GL #%02X%02X%02X [%s]%s".format(probeSrc and 0xFFFFFF, r, g, bl, dbg.thai, if (bypass) " (bypass)" else "")
        }
        if (captureRequested) { captureRequested = false; onCaptured?.invoke(readPixels()) }
    }

    /** Stage B uniforms: eye makeup colours/amounts (mask2) + analytic highlight/contour/under-eye centres. */
    private fun stageB(cfg: FilterConfig, pts: FloatArray?) {
        GLES20.glUniform1f(u("uShadowOn"), on(cfg, FilterGroup.EYESHADOW))
        GLES20.glUniform1f(u("uShadowAmt"), cfg["eyeshadowAmount"])
        cfg.rgb(ColorSpecs.EYESHADOW).let { GLES20.glUniform3f(u("uShadowColor"), it[0], it[1], it[2]) }
        GLES20.glUniform1f(u("uLinerOn"), on(cfg, FilterGroup.EYELINER))
        GLES20.glUniform1f(u("uLinerAmt"), cfg["eyelinerAmount"])
        cfg.rgb(ColorSpecs.EYELINER).let { GLES20.glUniform3f(u("uLinerColor"), it[0], it[1], it[2]) }
        GLES20.glUniform1f(u("uTeethOn"), on(cfg, FilterGroup.TEETH))
        GLES20.glUniform1f(u("uTeethAmt"), cfg["teethAmount"])
        GLES20.glUniform1f(u("uLashOn"), on(cfg, FilterGroup.EYELASH))
        GLES20.glUniform1f(u("uLashAmt"), cfg["eyelashAmount"])
        cfg.rgb(ColorSpecs.EYELASH).let { GLES20.glUniform3f(u("uLashColor"), it[0], it[1], it[2]) }
        GLES20.glUniform1f(u("uHiAmt"), cfg["highlightAmount"])
        cfg.rgb(ColorSpecs.HIGHLIGHT).let { GLES20.glUniform3f(u("uHiColor"), it[0], it[1], it[2]) }
        GLES20.glUniform1f(u("uConAmt"), cfg["contourAmount"])
        cfg.rgb(ColorSpecs.CONTOUR).let { GLES20.glUniform3f(u("uConColor"), it[0], it[1], it[2]) }
        GLES20.glUniform1f(u("uUnderAmt"), cfg["underEyeAmount"])
        val ok = pts != null && pts.size >= 468 * 3
        GLES20.glUniform1f(u("uHiOn"), if (ok) on(cfg, FilterGroup.HIGHLIGHT) else 0f)
        GLES20.glUniform1f(u("uConOn"), if (ok) on(cfg, FilterGroup.CONTOUR) else 0f)
        GLES20.glUniform1f(u("uUnderOn"), if (ok) on(cfg, FilterGroup.UNDEREYE) else 0f)
        if (!ok) { GLES20.glUniform1f(u("uHiSize"), 1f); GLES20.glUniform1f(u("uConSize"), 1f); GLES20.glUniform1f(u("uUnderSize"), 1f); return }
        val p = pts!!
        fun pt(loc: Int, i: Int) = GLES20.glUniform2f(loc, p[i * 3], p[i * 3 + 1])
        pt(u("uHiF"), FaceContours.HL_FOREHEAD); pt(u("uHiN"), FaceContours.HL_NOSE); pt(u("uHiC"), FaceContours.HL_CHIN)
        pt(u("uConL"), FaceContours.CONTOUR_LEFT); pt(u("uConR"), FaceContours.CONTOUR_RIGHT)
        pt(u("uUnderL"), FaceContours.UNDER_EYE_LEFT); pt(u("uUnderR"), FaceContours.UNDER_EYE_RIGHT)
        var minX = 1f; var maxX = 0f
        for (i in FaceContours.FACE_OVAL) { val x = p[i * 3]; if (x < minX) minX = x; if (x > maxX) maxX = x }
        val fw = (maxX - minX) * imgW
        GLES20.glUniform1f(u("uHiSize"), fw * 0.07f * cfg["highlightSize"])
        GLES20.glUniform1f(u("uConSize"), fw * 0.07f * cfg["contourSize"])
        GLES20.glUniform1f(u("uUnderSize"), fw * 0.045f * cfg["underEyeSize"])
    }

    private fun on(c: FilterConfig, g: FilterGroup) = if (c.isOn(g)) 1f else 0f
    /** Client-array quad: no VBO bound, attribute location queried after link, enabled every draw. */
    private fun drawQuad(attr: Int) {
        if (attr < 0) { lastError = "aPos not found"; return }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        quad.position(0)
        GLES20.glEnableVertexAttribArray(attr)
        GLES20.glVertexAttribPointer(attr, QuadGeometry.COMPONENTS, GLES20.GL_FLOAT, false, QuadGeometry.STRIDE_BYTES, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, QuadGeometry.VERTEX_COUNT)
        GLES20.glDisableVertexAttribArray(attr)
    }

    /**
     * Hidden passthrough check (BITMAP path, AUTO mode): draw camera-only, read the centre pixel, compare with
     * the bitmap centre, then redraw the normal frame over it (only the normal frame is ever shown).
     * A bitmap that is itself corrupted ([StripeCheck], channels shifted per pixel) also counts as a mismatch:
     * then GL matches the broken bitmap, so the colour comparison alone can't see it.
     * 3 mismatches (>24 on any channel, or striped bitmap) -> switch to OES. 3 matches -> stop checking.
     */
    private fun autoCheck(src: Int, striped: Boolean) {
        GLES20.glUniform1f(u("uDebug"), 1f); drawQuad(aPos)
        val px = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(viewW / 2, viewH / 2, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, px)
        val d = ProbeCompare.maxChannelDiff(src, px.get(0).toInt() and 255, px.get(1).toInt() and 255, px.get(2).toInt() and 255)
        GLES20.glUniform1f(u("uDebug"), debug.shader); drawQuad(aPos)
        if (d > ProbeCompare.THRESHOLD || striped) autoMismatch++ else autoMatch++
        if (autoMatch >= 3) autoPath = false
        else if (autoMismatch >= 3) {
            autoPath = false; cameraPath = CameraPath.OES
            onPathAutoSwitched?.invoke(if (striped) "ภาพบิตแมปเป็นลายสี" else "passthrough ต่างจากบิตแมป $d")
        }
    }

    private var stripePx = IntArray(0)
    /** GL thread, every 30th frame while auto-checking: is the camera bitmap itself striped? */
    private fun stripeCheck(b: Bitmap): Boolean {
        val n = b.width * b.height
        if (stripePx.size != n) stripePx = IntArray(n)
        b.getPixels(stripePx, 0, b.width, 0, 0, b.width, b.height)
        return StripeCheck.isStriped(stripePx, b.width, b.height)
    }

    private var curOes = false
    private fun u(n: String) = (if (curOes) locOes[n] else loc[n]) ?: -1

    /** Texture units are fixed: camera image = unit 0, mask = 1, mask2 = 2, OES camera = 3, mask3 = 4 (never shared). */
    private fun unitOf(tex: Int) = when (tex) { texImage -> 0; texMask -> 1; texMask3 -> 4; else -> 2 }

    /**
     * Upload with GLUtils.texImage2D (driver picks the matching RGBA/UNSIGNED_BYTE layout for an ARGB_8888
     * bitmap, no manual buffers, no width/stride assumptions). The texture's own unit is activated first, so
     * an upload can never land on another texture's binding.
     */
    private fun upload(tex: Int, src: Bitmap, sameSize: Boolean) {
        val b = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unitOf(tex))
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        if (sameSize) GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, b)
        else {
            // NPOT (480x640) in ES2 is only complete with no mipmaps + CLAMP_TO_EDGE: set before allocating
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
        }
        val err = GLES20.glGetError()
        if (err != GLES20.GL_NO_ERROR) lastError = "upload glError 0x" + Integer.toHexString(err) + " ${b.width}x${b.height}"
        if (b !== src) b.recycle()
    }

    private fun readPixels(): Bitmap {
        val buf = ByteBuffer.allocateDirect(viewW * viewH * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, viewW, viewH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        val raw = Bitmap.createBitmap(viewW, viewH, Bitmap.Config.ARGB_8888)
        buf.rewind(); raw.copyPixelsFromBuffer(buf)
        val flip = android.graphics.Matrix().apply { postScale(1f, -1f) }
        val out = Bitmap.createBitmap(raw, 0, 0, viewW, viewH, flip, false)
        raw.recycle()
        return out
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
        val ok = IntArray(1); GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) { lastError = GLES20.glGetShaderInfoLog(s); Log.e(TAG, "shader: $lastError"); GLES20.glDeleteShader(s); return 0 }
        return s
    }

    private fun link(v: Int, f: Int): Int {
        if (v == 0 || f == 0) return 0
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f); GLES20.glLinkProgram(p)
        val ok = IntArray(1); GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) { lastError = GLES20.glGetProgramInfoLog(p); Log.e(TAG, "link: $lastError"); return 0 }
        return p
    }

    companion object { private const val TAG = "FilterRenderer" }
}

/**
 * Diagnostic render modes (debug selector in the camera view). [shader]: value of uDebug.
 * [only]: render normally but with every switchable group off except this one (forced on).
 */
enum class DebugMode(val thai: String, val shader: Float, val only: FilterGroup? = null) {
    NORMAL("ปกติ", 0f),
    PASSTHROUGH("ส่งตรง (ภาพกล้องล้วน)", 1f),
    MASK1("ดูมาสก์ 1 (ผิว/ปาก/คิ้ว)", 2f),
    MASK2("ดูมาสก์ 2 (ตา/ไลเนอร์/ฟัน)", 3f),
    MASK3("ดูมาสก์ 3 (ขนตา)", 4f),
    SMOOTH_ONLY("เฉพาะ หน้าเนียน", 0f, FilterGroup.SMOOTH),
    BRIGHT_ONLY("เฉพาะ หน้าใส", 0f, FilterGroup.BRIGHT),
    PINK_ONLY("เฉพาะ ผิวอมชมพู", 0f, FilterGroup.PINK),
    LIP_ONLY("เฉพาะ ลิปสติก", 0f, FilterGroup.LIPSTICK),
    BLUSH_ONLY("เฉพาะ บลัชออน", 0f, FilterGroup.BLUSH),
    EYE_ONLY("เฉพาะ อายแชโดว์", 0f, FilterGroup.EYESHADOW),
    LASH_ONLY("เฉพาะ ขนตา", 0f, FilterGroup.EYELASH),
}

/** Pure helper for the passthrough auto-check (unit-tested). */
object ProbeCompare {
    const val THRESHOLD = 24
    /** [argb] Android colour int vs r,g,b bytes read back from GL. */
    fun maxChannelDiff(argb: Int, r: Int, g: Int, b: Int): Int =
        maxOf(kotlin.math.abs(((argb shr 16) and 255) - r), kotlin.math.abs(((argb shr 8) and 255) - g), kotlin.math.abs((argb and 255) - b))
}
