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
 * Applies a 3Jump Filter Studio filter to the call camera, between the camera and WebRTC, on the device GPU.
 *
 * Runs on the camera's SurfaceTextureHelper thread (its EGL context is current; shared with the encoder and views).
 * Per frame: the camera texture is drawn upright into an RGBA texture (row 0 = image top, the layout the Studio
 * engine expects); a small copy goes to the Studio's [FaceTracker] (MediaPipe, own thread, never waited for); then
 * the Studio's own [FilterRenderer] renders the filter with the latest landmarks into a new texture frame (rotation 0).
 * Same shaders and masks as the Studio, so a filter looks the same in the call as in the Studio's camera test.
 * No filter / non-texture frame / any GL error: the camera frame passes through unchanged. Nothing is recorded.
 */
internal class StudioFilterProcessor(
    context: Context,
    private val handler: Handler,
    private val control: StudioFilterControl,
) : VideoProcessor {
    private val app = context.applicationContext
    @Volatile private var sink: VideoSink? = null
    private var gl: Gl? = null
    private var failed = false

    // face tracking on its own thread (MediaPipe GPU delegate: create/use/close on one thread)
    private var trackerThread: HandlerThread? = null
    private var trackerHandler: Handler? = null
    private var tracker: FaceTracker? = null
    private val inFlight = AtomicBoolean(false)
    @Volatile private var points: FloatArray? = null
    @Volatile private var pointsAt = 0L

    override fun setSink(sink: VideoSink?) { this.sink = sink }
    override fun onCapturerStarted(success: Boolean) {}
    override fun onCapturerStopped() {}

    override fun onFrameCaptured(frame: VideoFrame) {
        val out = sink ?: return
        val cfg = control.config
        val buffer = frame.buffer
        if (cfg == null || failed || buffer !is VideoFrame.TextureBuffer || buffer.type != VideoFrame.TextureBuffer.Type.OES) {
            if (cfg == null && points != null) points = null
            out.onFrame(frame); return
        }
        ensureTracker()
        val filtered = try {
            (gl ?: Gl().also { gl = it }).render(frame, buffer, cfg)
        } catch (e: Throwable) {
            Log.w(TAG, "filter disabled after GL error", e)
            failed = true
            releaseGl()
            null
        }
        if (filtered == null) { if (failed) out.onFrame(frame); return } // null without failure = dropped (no free texture)
        out.onFrame(filtered)
        filtered.release()
    }

    /** Free GL objects and stop face tracking. Must run on [handler]'s thread while its EGL context is alive. */
    fun release() {
        releaseGl()
        val th = trackerThread ?: return
        trackerHandler?.post { runCatching { tracker?.close() }; tracker = null }
        th.quitSafely(); trackerThread = null; trackerHandler = null
    }

    private fun releaseGl() { gl?.release(); gl = null }

    private fun ensureTracker() {
        if (trackerThread != null) return
        val th = HandlerThread("tj-face").also { it.start() }
        val h = Handler(th.looper)
        trackerThread = th; trackerHandler = h
        h.post {
            tracker = try {
                FaceTracker(app, object : FaceTracker.Listener {
                    override fun onFace(frame: FaceFrame) {
                        points = frame.points; pointsAt = SystemClock.uptimeMillis(); inFlight.set(false)
                    }
                    override fun onTrackerError(message: String) { Log.w(TAG, "face tracker: $message"); inFlight.set(false) }
                    override fun onDelegateChanged(delegate: TrackerDelegate, reason: String?) { Log.i(TAG, "face tracker on $delegate ${reason ?: ""}") }
                })
            } catch (t: Throwable) { Log.w(TAG, "face tracker unavailable", t); null }
        }
    }

    /** Called on the GL thread with a top-first upright bitmap; the tracker owns it until its result arrives. */
    private fun offer(bitmap: Bitmap): Boolean {
        val h = trackerHandler ?: return false
        if (!inFlight.compareAndSet(false, true)) return false
        h.post {
            val t = tracker
            val ok = t != null && runCatching { t.detect(bitmap, 0, SystemClock.uptimeMillis()) }.getOrDefault(false)
            if (!ok) inFlight.set(false)
        }
        return true
    }

    private inner class Gl {
        private val oes = GlShader(VS, FS_OES)
        private val copy = GlShader(VS, FS_COPY)
        private val renderer = FilterRenderer().also { it.mirror = false; it.onSurfaceCreated(null, null) }
        private val yuv = YuvConverter()
        private val base = GlTextureFrameBuffer(GLES20.GL_RGBA)
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

        fun render(frame: VideoFrame, buffer: VideoFrame.TextureBuffer, cfg: FilterConfig): VideoFrame? {
            val rot = ((frame.rotation % 360) + 360) % 360
            val w = if (rot == 90 || rot == 270) buffer.height else buffer.width
            val h = if (rot == 90 || rot == 270) buffer.width else buffer.height
            val slot = (0 until RING).map { (next + it) % RING }.firstOrNull { ringBusy[it].compareAndSet(false, true) } ?: return null
            next = (slot + 1) % RING
            try {
                base.setSize(w, h)
                ring[slot].setSize(w, h)

                // 1) camera (OES, sensor orientation) -> upright RGBA, row 0 = image top (bitmap layout)
                cameraTexCoords(rot)
                drawQuad(oes, base.frameBufferId, w, h, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, buffer.textureId, tcCamera,
                    RendererCommon.convertMatrixFromAndroidGraphicsMatrix(buffer.transformMatrix))

                // 2) a small copy for the face tracker when it is free
                if (!inFlight.get()) detect(w, h)
                val pts = points?.takeIf { SystemClock.uptimeMillis() - pointsAt < FACE_STALE_MS }

                // 3) the Studio's own renderer: filter.json -> same shaders + masks as in the Studio
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, ring[slot].frameBufferId)
                renderer.config = cfg
                renderer.drawExternal(base.textureId, w, h, pts)
                renderer.lastError?.let { throw IllegalStateException(it) }

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glFinish() // the encoder and the views read this texture from other (shared) contexts
                GlUtil.checkNoGLES2Error("studio filter")
            } catch (e: Throwable) {
                ringBusy[slot].set(false)
                throw e
            }
            val tex = TextureBufferImpl(w, h, VideoFrame.TextureBuffer.Type.RGB, ring[slot].textureId, Matrix(), handler, yuv) {
                ringBusy[slot].set(false)
            }
            return VideoFrame(tex, 0, frame.timestampNs)
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

        private fun detect(w: Int, h: Int) {
            val dw = DETECT_WIDTH
            val dh = maxOf(1, Math.round(DETECT_WIDTH.toFloat() * h / w))
            small.setSize(dw, dh)
            // base is top-first; drawn with identity coordinates the small copy is top-first too, so the rows read
            // back by glReadPixels (row 0 first) are already in Bitmap order
            drawQuad(copy, small.frameBufferId, dw, dh, GLES20.GL_TEXTURE_2D, base.textureId, tcIdentity, identity)
            val i = nextBitmap; nextBitmap = (nextBitmap + 1) % bitmaps.size
            val bmp = bitmaps[i]?.takeIf { it.width == dw && it.height == dh }
                ?: Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888).also { bitmaps[i] = it }
            val px = pixels?.takeIf { it.capacity() == dw * dh * 4 }
                ?: ByteBuffer.allocateDirect(dw * dh * 4).order(ByteOrder.nativeOrder()).also { pixels = it }
            px.position(0)
            GLES20.glReadPixels(0, 0, dw, dh, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, px)
            px.position(0)
            bmp.copyPixelsFromBuffer(px) // ARGB_8888 is stored as RGBA bytes
            offer(bmp)
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
            oes.release(); copy.release(); yuv.release()
            base.release(); small.release(); ring.forEach { it.release() }
            // the Studio renderer's program/textures belong to this context and go with it; bitmaps may still be in
            // the tracker's hands, so they are left to the garbage collector
        }
    }

    companion object {
        private const val TAG = "ThreeJunksFilter"
        private const val RING = 4
        private const val DETECT_WIDTH = 256
        private const val FACE_STALE_MS = 500L

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
