package com.threejunks.sdk.android.filter

import android.content.Context
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import livekit.org.webrtc.TextureBufferImpl
import livekit.org.webrtc.ThreadUtils
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoProcessor
import livekit.org.webrtc.VideoSink
import livekit.org.webrtc.YuvConverter

/**
 * The live camera's filter: same [StudioFilterEngine] as calls, with LiveKit's WebRTC frame types (livekit.org.webrtc).
 * LiveKit creates the camera's SurfaceTextureHelper itself, so the GL thread is taken from the first frame.
 * No filter / non-texture frame / GL error: the camera frame passes through unchanged.
 */
internal class LiveStudioFilterProcessor(
    context: Context,
    private val control: StudioFilterControl,
) : VideoProcessor {
    private val engine = StudioFilterEngine(context)
    private var yuv: YuvConverter? = null
    @Volatile private var glHandler: Handler? = null
    @Volatile private var sink: VideoSink? = null
    @Volatile private var released = false

    override fun setSink(sink: VideoSink?) { this.sink = sink }
    override fun onCapturerStarted(success: Boolean) {}
    override fun onCapturerStopped() {}

    override fun onFrameCaptured(frame: VideoFrame) {
        val out = sink ?: return
        val cfg = control.config
        val buffer = frame.buffer
        if (cfg == null || released || engine.failed || buffer !is VideoFrame.TextureBuffer || buffer.type != VideoFrame.TextureBuffer.Type.OES) {
            if (cfg == null) engine.idle()
            out.onFrame(frame); return
        }
        val h = glHandler ?: Handler(Looper.myLooper() ?: run { out.onFrame(frame); return }).also { glHandler = it }
        val f = engine.render(buffer.textureId, buffer.transformMatrix, buffer.width, buffer.height, frame.rotation, cfg)
        if (f == null) { if (engine.failed) out.onFrame(frame); return } // null without failure = dropped (no free texture)
        val conv = yuv ?: YuvConverter().also { yuv = it }
        val tex = TextureBufferImpl(f.width, f.height, VideoFrame.TextureBuffer.Type.RGB, f.textureId, Matrix(), h, conv) { f.release() }
        val filtered = VideoFrame(tex, 0, frame.timestampNs)
        out.onFrame(filtered)
        filtered.release()
    }

    /** Free GL objects and stop face tracking, on the camera GL thread (before LiveKit disposes the track). */
    fun release() {
        released = true
        val h = glHandler
        if (h == null) { engine.release(); return } // no frame was filtered: no GL objects yet
        runCatching { ThreadUtils.invokeAtFrontUninterruptibly(h) { engine.release(); yuv?.release(); yuv = null } }
    }
}
