package com.threejunks.sdk.android

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.threejunks.sdk.Call
import com.threejunks.sdk.CallEnd
import com.threejunks.sdk.CallState
import com.threejunks.sdk.ServerConfig
import com.threejunks.sdk.ThreeJunksClient
import com.threejunks.sdk.ThreeJunksListener
import com.threejunks.sdk.TokenProvider
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

/**
 * Three Junks for Android: one object per app (e.g. in your Application or a singleton).
 *
 * ```
 * val tj = ThreeJunksAndroid(context, "https://call.example.com", { myBackend.threeJunksToken() }, myListener)
 * tj.client.connect()
 * tj.client.callExternal("friend-user-id", video = true)   // or accept() in onIncomingCall
 * tj.attachLocalVideo(localView); tj.attachRemoteVideo(remoteView)
 * ```
 * The app must hold RECORD_AUDIO (and CAMERA for video) at runtime before a call starts.
 * Listener callbacks run on the main thread.
 */
class ThreeJunksAndroid(
    context: Context,
    baseUrl: String,
    tokenProvider: TokenProvider,
    listener: ThreeJunksListener,
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    val media = WebRtcPeerFactory(app)
    private val audio = CallAudio(app)

    val client = ThreeJunksClient(baseUrl, tokenProvider, media, object : ThreeJunksListener {
        override fun onConnected(config: ServerConfig) = listener.onConnected(config)
        override fun onDisconnected(willReconnect: Boolean, takenOver: Boolean) = listener.onDisconnected(willReconnect, takenOver)
        override fun onIncomingCall(call: Call) = listener.onIncomingCall(call)
        override fun onCallStateChanged(call: Call) {
            if (call.state == CallState.CONNECTING) audio.start(speaker = call.video)
            listener.onCallStateChanged(call)
        }
        override fun onPeerVideoChanged(call: Call, video: Boolean) = listener.onPeerVideoChanged(call, video)
        override fun onPeerReconnecting(call: Call, reconnecting: Boolean) = listener.onPeerReconnecting(call, reconnecting)
        override fun onCallEnded(call: Call, end: CallEnd) { audio.stop(); listener.onCallEnded(call, end) }
        override fun onError(code: String, message: String) = listener.onError(code, message)
    }, callbackExecutor = { main.post(it) })

    /** Show the local camera in [view] (mirrored). Call on the main thread. */
    fun attachLocalVideo(view: SurfaceViewRenderer) {
        initRenderer(view, mirror = true)
        media.localVideo.target = view
    }

    /** Show the other side's video in [view]. Call on the main thread. */
    fun attachRemoteVideo(view: SurfaceViewRenderer) {
        initRenderer(view, mirror = false)
        media.remoteVideo.target = view
    }

    /** Stop drawing into the views (e.g. in onDestroyView). The views must then be released by the caller. */
    fun detachVideo() { media.localVideo.target = null; media.remoteVideo.target = null }

    fun switchCamera() = media.switchCamera()

    fun setSpeakerphone(on: Boolean) = audio.setSpeaker(on)

    /** Reconnect quickly after the app comes to the foreground or the network changes (Wi-Fi <-> mobile data). */
    fun onForegroundOrNetworkChanged() = client.onNetworkOrForegroundChanged()

    fun release() { client.shutdown(); detachVideo(); audio.stop(); media.release() }

    private fun initRenderer(view: SurfaceViewRenderer, mirror: Boolean) {
        try { view.init(media.eglBase.eglBaseContext, null) } catch (e: IllegalStateException) { /* already initialized */ }
        view.setMirror(mirror)
        view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        view.setEnableHardwareScaler(true)
    }
}

/** Call audio routing: communication mode, earpiece for voice calls, loudspeaker for video calls. */
internal class CallAudio(context: Context) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var active = false
    private var savedMode = AudioManager.MODE_NORMAL

    fun start(speaker: Boolean) {
        if (active) return
        active = true
        savedMode = am.mode
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeaker(speaker)
    }

    @Suppress("DEPRECATION")
    fun setSpeaker(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 31) {
            val type = if (on) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            am.availableCommunicationDevices.firstOrNull { it.type == type }?.let { am.setCommunicationDevice(it) }
        } else {
            am.isSpeakerphoneOn = on
        }
    }

    @Suppress("DEPRECATION")
    fun stop() {
        if (!active) return
        active = false
        if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice() else am.isSpeakerphoneOn = false
        am.mode = savedMode
    }
}
