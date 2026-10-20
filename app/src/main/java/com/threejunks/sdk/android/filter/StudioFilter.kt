package com.threejunks.sdk.android.filter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.threejump.filterstudio.facetracking.FaceFrame
import com.threejump.filterstudio.facetracking.FaceTracker
import com.threejump.filterstudio.facetracking.TrackerDelegate
import com.threejump.filterstudio.filterengine.FilterConfig
import com.threejump.filterstudio.filterengine.FilterRenderer
import org.webrtc.GlShader
import org.webrtc.GlTextureFrameBuffer
import org.webrtc.GlUtil
import org.webrtc.RendererCommon
import org.webrtc.TextureBufferImpl
import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink
import org.webrtc.YuvConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

/**
 * The filter a call's camera uses: a `filter.json` made with 3Jump Filter Studio (or null = no filter).
 * Shared by every call of a [com.threejunks.sdk.android.WebRtcPeerFactory]; can change at any time, also mid-call.
 */
class StudioFilterControl {
    @Volatile var config: FilterConfig? = null
        private set

    /** [json]: the content of filter.json exported by 3Jump Filter Studio; null = filter off. Throws on invalid JSON. */
    fun set(json: String?) { config = json?.let { FilterConfig.fromJson(it) } }

    fun set(cfg: FilterConfig?) { config = cfg }

    companion object {
        /** filter.json from the Studio's export: the .json itself or the export ZIP (filter.json inside). */
        @JvmStatic
        fun readFilterJson(bytes: ByteArray): String {
            if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                ZipInputStream(bytes.inputStream()).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        if (!e.isDirectory && e.name.substringAfterLast('/') == "filter.json") return z.readBytes().toString(Charsets.UTF_8)
                    }
                }
                throw IllegalArgumentException("filter.json not found in the ZIP")
            }
            return bytes.toString(Charsets.UTF_8)
        }
    }
}

/**
 * Renders a 3Jump Filter Studio filter on a camera texture, on the device GPU. Used by the call camera
 * ([StudioFilterProcessor], org.webrtc) and the live camera ([LiveStudioFilterProcessor], LiveKit's livekit.org.webrtc):
 * only the frame types differ, so this class works on plain texture ids.
 *
 * Runs on the camera's SurfaceTextureHelper thread (its EGL context is current; shared with the encoder and views).
 * Same as the Studio's camera: every frame is filtered with the landmarks found IN THAT FRAME, so thin, face-placed
 * effects (eyeliner, lashes) stay on the face while it moves. Per frame: the camera texture is drawn upright into an
 * RGBA texture (row 0 = image top, the layout the Studio engine expects). When the Studio's [FaceTracker] (MediaPipe,
 * own thread) is free, the frame is held, a 480-wide copy is sent to it, and when its landmarks come back the held
 * frame is rendered by the Studio's own [FilterRenderer] and emitted; frames arriving meanwhile are dropped (the
 * Studio's drop-when-busy), so the video runs at the tracker's rate. Tracker still loading, unavailable or stuck:
 * frames are rendered at once with the last landmarks (or none). Any GL error: [failed] turns true and the adapters
 * pass camera frames through unchanged. Nothing is recorded.
 */
internal class StudioFilterEngine(context: Context) {
    private val app = context.applicationContext
    private var gl: Gl? = null
    /** A GL error happened: the filter stays off for this camera session. */
    var failed = false; private set
    private var released = false

    // face tracking on its own thread (MediaPipe GPU delegate: create/use/close on one thread)
    private var trackerThread: HandlerThread? = null
    private var trackerHandler: Handler? = null
    private var tracker: FaceTracker? = null
    @Volatile private var trackerReady = false
    @Volatile private var points: FloatArray? = null

    // the frame waiting for its landmarks (GL thread only)
    private var glHandler: Handler? = null
    private var emitter: ((Output) -> Unit)? = null
    private var sentGen = 0
    private var inFlight = false
    private var sentAt = 0L
    private var held: Held? = null

    private class Held(val gen: Int, val w: Int, val h: Int, val timestampNs: Long, val cfg: FilterConfig)

    /** A filtered frame: an RGB 2D texture, upright (rotation 0). Call [release] when the encoder/views are done with it. */
    class Output(val textureId: Int, val width: Int, val height: Int, val timestampNs: Long, val release: () -> Unit)

    /**
     * Filter one camera frame: [oesTexture] (external OES) with its [transform] matrix, buffer size [bufW] x [bufH],
     * frame [rotation] and [timestampNs]. Filtered frames go to [emit] on this GL thread ([handler]): at once or when
     * the frame's landmarks arrive; frames may be dropped. False = not handled (GL error): pass the camera frame on.
     */
    fun process(
        oesTexture: Int, transform: Matrix, bufW: Int, bufH: Int, rotation: Int, timestampNs: Long, cfg: FilterConfig,
        handler: Handler, emit: (Output) -> Unit,
    ): Boolean {
        if (failed || released) return false
        glHandler = handler; emitter = emit
        ensureTracker()
        try {
            val g = gl ?: Gl().also { gl = it }
            val (w, h) = g.upright(oesTexture, transform, bufW, bufH, rotation)
            val now = SystemClock.uptimeMillis()
            if (inFlight) {
                if (now - sentAt < STUCK_MS) return true // the held frame is shown when its landmarks arrive
                cancelHeld() // tracker stuck: carry on without waiting
            }
            if (!trackerReady) { g.renderBase(w, h, timestampNs, cfg, points)?.let(emit); return true }
            val gen = ++sentGen
            g.hold()
            held = Held(gen, w, h, timestampNs, cfg)
            inFlight = true; sentAt = now
            if (!offer(g.detectBitmap(w, h), gen)) { inFlight = false; held = null; g.renderHeld(w, h, timestampNs, cfg, points)?.let(emit) }
            return true
        } catch (e: Throwable) {
            fail(e); return false
        }
    }

    /** No filter (or no texture frame): drop the frame waiting for landmarks so frames stay in order. */
    fun idle() { cancelHeld(); if (points != null) points = null }

    private fun cancelHeld() { held = null; inFlight = false; sentGen++ }

    /** GL thread: the landmarks of frame [gen] (null = no face / tracker error). */
    private fun onLandmarks(gen: Int, pts: FloatArray?) {
        val f = held
        if (released || failed || f == null || f.gen != gen || gen != sentGen) return
        held = null; inFlight = false
        try {
            gl?.renderHeld(f.w, f.h, f.timestampNs, f.cfg, pts)?.let { out -> emitter?.invoke(out) ?: out.release() }
        } catch (e: Throwable) { fail(e) }
    }

    private fun fail(e: Throwable) {
        Log.w(TAG, "filter disabled after GL error", e)
        failed = true; held = null; inFlight = false
        releaseGl()
    }

    /** Free GL objects and stop face tracking. Must run on the camera GL thread while its EGL context is alive. */
    fun release() {
        released = true; held = null; emitter = null
        releaseGl()
        val th = trackerThread ?: return
        trackerHandler?.post { runCatching { tracker?.close() }; tracker = null }
        th.quitSafely(); trackerThread = null; trackerHandler = null
    }

    private fun releaseGl() { gl?.release(); gl = null }

    @Volatile private var pendingGen = 0

    private fun ensureTracker() {
        if (trackerThread != null) return
        val th = HandlerThread("tj-face").also { it.start() }
        val h = Handler(th.looper)
        trackerThread = th; trackerHandler = h
        h.post {
            tracker = try {
                FaceTracker(app, object : FaceTracker.Listener {
                    override fun onFace(frame: FaceFrame) {
                        points = frame.points
                        val gen = pendingGen
                        glHandler?.post { onLandmarks(gen, frame.points) }
                    }
                    override fun onTrackerError(message: String) {
                        Log.w(TAG, "face tracker: $message")
                        val gen = pendingGen
                        glHandler?.post { onLandmarks(gen, points) }
                    }
                    override fun onDelegateChanged(delegate: TrackerDelegate, reason: String?) { Log.i(TAG, "face tracker on $delegate ${reason ?: ""}") }
                })
            } catch (t: Throwable) { Log.w(TAG, "face tracker unavailable", t); null }
            trackerReady = tracker != null
        }
    }

    /** GL thread: send the held frame's top-first upright bitmap to the tracker (it owns it until its result). */
    private fun offer(bitmap: Bitmap, gen: Int): Boolean {
        val h = trackerHandler ?: return false
        h.post {
            val t = tracker
            pendingGen = gen
            val ok = t != null && runCatching { t.detect(bitmap, 0, SystemClock.uptimeMillis()) }.getOrDefault(false)
            if (!ok) glHandler?.post { onLandmarks(gen, points) }
        }
        return true
    }

    private inner class Gl {
        private val oes = GlShader(VS, FS_OES)
        private val copy = GlShader(VS, FS_COPY)
        private val renderer = FilterRenderer().also { it.mirror = false; it.onSurfaceCreated(null, null) }
        private var base = GlTextureFrameBuffer(GLES20.GL_RGBA)
        private var heldBuf = GlTextureFrameBuffer(GLES20.GL_RGBA)
        private val small = GlTextureFrameBuffer(GLES20.GL_RGBA)
        private val ring = Array(RING) { GlTextureFrameBuffer(GLES20.GL_RGBA) }
        private val ringBusy = Array(RING) { AtomicBoolean(false) }
        private var next = 0
        private val quad = GlUtil.createFloatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        private val tcIdentity = GlUtil.createFloatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
        private val tcCamera = GlUtil.createFloatBuffer(FloatArray(8))
        private val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        private val bitmaps = arrayOfNulls<Bitmap>(2)
        private var nextBitmap = 0
        private var pixels: ByteBuffer? = null

        /** Camera (OES, sensor orientation) -> upright RGBA in [base], row 0 = image top (bitmap layout). Returns w, h. */
        fun upright(oesTexture: Int, transform: Matrix, bufW: Int, bufH: Int, rotation: Int): Pair<Int, Int> {
            val rot = ((rotation % 360) + 360) % 360
            val w = if (rot == 90 || rot == 270) bufH else bufW
            val h = if (rot == 90 || rot == 270) bufW else bufH
            base.setSize(w, h)
            cameraTexCoords(rot)
            drawQuad(oes, base.frameBufferId, w, h, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture, tcCamera,
                RendererCommon.convertMatrixFromAndroidGraphicsMatrix(transform))
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            return w to h
        }

        /** Keep the current upright frame (it waits for its landmarks); the next frame draws into the other buffer. */
        fun hold() { val t = heldBuf; heldBuf = base; base = t }

        fun renderBase(w: Int, h: Int, ts: Long, cfg: FilterConfig, pts: FloatArray?) = render(base, w, h, ts, cfg, pts)
        fun renderHeld(w: Int, h: Int, ts: Long, cfg: FilterConfig, pts: FloatArray?) = render(heldBuf, w, h, ts, cfg, pts)

        /** The Studio's own renderer: filter.json -> same shaders + masks as in the Studio. Null = no free output texture. */
        private fun render(src: GlTextureFrameBuffer, w: Int, h: Int, ts: Long, cfg: FilterConfig, pts: FloatArray?): Output? {
            val slot = (0 until RING).map { (next + it) % RING }.firstOrNull { ringBusy[it].compareAndSet(false, true) } ?: return null
            next = (slot + 1) % RING
            try {
                ring[slot].setSize(w, h)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, ring[slot].frameBufferId)
                renderer.config = cfg
                renderer.drawExternal(src.textureId, w, h, pts)
                renderer.lastError?.let { throw IllegalStateException(it) }
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glFinish() // the encoder and the views read this texture from other (shared) contexts
                GlUtil.checkNoGLES2Error("studio filter")
            } catch (e: Throwable) {
                ringBusy[slot].set(false)
                throw e
            }
            return Output(ring[slot].textureId, w, h, ts) { ringBusy[slot].set(false) }
        }

        /**
         * Per corner (strip order BL, BR, TL, TR of the output): where to sample the camera buffer so that the
         * output's row 0 is the upright image's TOP (the layout of an uploaded bitmap, which the Studio expects).
         */
        private fun cameraTexCoords(rot: Int) {
            val upright = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f) // (u, v) of the upright image, top-left origin
            val tc = FloatArray(8)
            for (i in 0 until 4) {
                val u = upright[i * 2]; val v = upright[i * 2 + 1]
                // the buffer must be turned `rot` degrees clockwise to be upright; (x, y) = buffer point, top-left origin
                val x: Float; val y: Float
                when (rot) {
                    90 -> { x = v; y = 1f - u }
                    180 -> { x = 1f - u; y = 1f - v }
                    270 -> { x = 1f - v; y = u }
                    else -> { x = u; y = v }
                }
                tc[i * 2] = x; tc[i * 2 + 1] = 1f - y // WebRTC texture coordinates: origin bottom-left
            }
            tcCamera.clear(); tcCamera.put(tc); tcCamera.position(0)
        }

        /** A [DETECT_WIDTH]-wide top-first copy of the held frame for the face tracker. */
        fun detectBitmap(w: Int, h: Int): Bitmap {
            val dw = DETECT_WIDTH
            val dh = maxOf(1, Math.round(DETECT_WIDTH.toFloat() * h / w))
            small.setSize(dw, dh)
            // base is top-first; drawn with identity coordinates the small copy is top-first too, so the rows read
            // back by glReadPixels (row 0 first) are already in Bitmap order
            drawQuad(copy, small.frameBufferId, dw, dh, GLES20.GL_TEXTURE_2D, heldBuf.textureId, tcIdentity, identity)
            val i = nextBitmap; nextBitmap = (nextBitmap + 1) % bitmaps.size
            val bmp = bitmaps[i]?.takeIf { it.width == dw && it.height == dh }
                ?: Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888).also { bitmaps[i] = it }
            val px = pixels?.takeIf { it.capacity() == dw * dh * 4 }
                ?: ByteBuffer.allocateDirect(dw * dh * 4).order(ByteOrder.nativeOrder()).also { pixels = it }
            px.position(0)
            GLES20.glReadPixels(0, 0, dw, dh, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, px)
            px.position(0)
            bmp.copyPixelsFromBuffer(px) // ARGB_8888 is stored as RGBA bytes
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            return bmp
        }

        private fun drawQuad(s: GlShader, fbo: Int, w: Int, h: Int, target: Int, tex: Int, tc: FloatBuffer, texMatrix: FloatArray) {
            s.useProgram()
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            GLES20.glViewport(0, 0, w, h)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(target, tex)
            GLES20.glUniform1i(s.getUniformLocation("uTex"), 0)
            GLES20.glUniformMatrix4fv(s.getUniformLocation("uTexMat"), 1, false, texMatrix, 0)
            s.setVertexAttribArray("aPos", 2, quad)
            s.setVertexAttribArray("aTc", 2, tc)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glBindTexture(target, 0)
        }

        fun release() {
            oes.release(); copy.release()
            base.release(); heldBuf.release(); small.release(); ring.forEach { it.release() }
            // the Studio renderer's program/textures belong to this context and go with it; bitmaps may still be in
            // the tracker's hands, so they are left to the garbage collector
        }
    }

    companion object {
        internal const val TAG = "ThreeJunksFilter"
        private const val RING = 4
        private const val DETECT_WIDTH = 480 // the Studio's camera analysis frames are 480 x 640
        private const val STUCK_MS = 400L

        private const val VS = """
            attribute vec2 aPos;
            attribute vec2 aTc;
            uniform mat4 uTexMat;
            varying vec2 vTc;
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
                vTc = (uTexMat * vec4(aTc, 0.0, 1.0)).xy;
            }
        """

        private const val FS_OES = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTc;
            uniform samplerExternalOES uTex;
            void main() { gl_FragColor = vec4(texture2D(uTex, vTc).rgb, 1.0); }
        """

        private const val FS_COPY = """
            precision mediump float;
            varying vec2 vTc;
            uniform sampler2D uTex;
            void main() { gl_FragColor = texture2D(uTex, vTc); }
        """
    }
}

/**
 * The call camera's filter (org.webrtc): between the camera and WebRTC, on the camera's SurfaceTextureHelper thread
 * ([handler]). No filter / non-texture frame / GL error: the camera frame passes through unchanged.
 */
internal class StudioFilterProcessor(
    context: Context,
    private val handler: Handler,
    private val control: StudioFilterControl,
) : VideoProcessor {
    private val engine = StudioFilterEngine(context)
    private val yuv = YuvConverter()
    @Volatile private var sink: VideoSink? = null

    override fun setSink(sink: VideoSink?) { this.sink = sink }
    override fun onCapturerStarted(success: Boolean) {}
    override fun onCapturerStopped() {}

    override fun onFrameCaptured(frame: VideoFrame) {
        val out = sink ?: return
        val cfg = control.config
        val buffer = frame.buffer
        if (cfg == null || engine.failed || buffer !is VideoFrame.TextureBuffer || buffer.type != VideoFrame.TextureBuffer.Type.OES) {
            engine.idle()
            out.onFrame(frame); return
        }
        if (!engine.process(buffer.textureId, buffer.transformMatrix, buffer.width, buffer.height, frame.rotation, frame.timestampNs, cfg, handler, ::emit))
            out.onFrame(frame)
    }

    private fun emit(f: StudioFilterEngine.Output) {
        val out = sink ?: run { f.release(); return }
        val tex = TextureBufferImpl(f.width, f.height, VideoFrame.TextureBuffer.Type.RGB, f.textureId, Matrix(), handler, yuv) { f.release() }
        val filtered = VideoFrame(tex, 0, f.timestampNs)
        out.onFrame(filtered)
        filtered.release()
    }

    /** Free GL objects and stop face tracking. Must run on [handler]'s thread while its EGL context is alive. */
    fun release() { engine.release(); yuv.release() }
}
