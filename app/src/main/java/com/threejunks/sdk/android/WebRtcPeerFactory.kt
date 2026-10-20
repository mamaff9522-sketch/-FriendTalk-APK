package com.threejunks.sdk.android

import android.content.Context
import com.threejunks.sdk.IceCandidate
import com.threejunks.sdk.MediaStats
import com.threejunks.sdk.Peer
import com.threejunks.sdk.PeerEvents
import com.threejunks.sdk.PeerFactory
import com.threejunks.sdk.RtcConfig
import com.threejunks.sdk.SessionDescription
import com.threejunks.sdk.android.filter.StudioFilterControl
import com.threejunks.sdk.android.filter.StudioFilterProcessor
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SurfaceTextureHelper
import org.webrtc.ThreadUtils
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.math.BigInteger
import org.webrtc.IceCandidate as RtcIceCandidate
import org.webrtc.SessionDescription as RtcSessionDescription

/** Forwards frames to whichever view is attached (views come and go with the UI; the track stays). */
class ProxyVideoSink : VideoSink {
    @Volatile var target: VideoSink? = null
    override fun onFrame(frame: VideoFrame) { target?.onFrame(frame) }
}

/**
 * [PeerFactory] on Google's WebRTC for Android. One factory per app: create it once and reuse it for every call.
 * Same media layout as the web client: one audio track, plus (when the project allows video) one sendrecv video
 * transceiver from the start, so the camera can be turned on/off mid-call by swapping the sender's track without
 * renegotiation.
 */
class WebRtcPeerFactory(context: Context) : PeerFactory {
    private val app = context.applicationContext
    val eglBase: EglBase = EglBase.create()
    val localVideo = ProxyVideoSink()
    val remoteVideo = ProxyVideoSink()
    /** 3Jump Filter Studio filter on the local camera (off until a filter is set). */
    val filter = StudioFilterControl()
    @Volatile private var current: WebRtcPeer? = null

    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions())
        val adm = JavaAudioDeviceModule.builder(app)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    override fun create(config: RtcConfig, isCaller: Boolean, videoCapable: Boolean, cameraOn: Boolean, events: PeerEvents): Peer =
        WebRtcPeer(config, isCaller, videoCapable, cameraOn, events).also { current = it }

    /** Front <-> back camera on the current call. */
    fun switchCamera() { current?.switchCamera() }

    fun release() { current?.close(); eglBase.release() }

    private fun rtcConfiguration(config: RtcConfig) = PeerConnection.RTCConfiguration(
        config.iceServers.map { s ->
            PeerConnection.IceServer.builder(s.urls).apply {
                s.username?.let { setUsername(it) }; s.credential?.let { setPassword(it) }
            }.createIceServer()
        },
    ).apply {
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        iceTransportsType = if (config.iceTransportPolicy == "relay") PeerConnection.IceTransportsType.RELAY else PeerConnection.IceTransportsType.ALL
        continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
    }

    private inner class WebRtcPeer(
        config: RtcConfig,
        isCaller: Boolean,
        private val videoCapable: Boolean,
        private var cameraWanted: Boolean,
        private val events: PeerEvents,
    ) : Peer {
        private val pc: PeerConnection
        private val audioSource: AudioSource
        private val audioTrack: AudioTrack
        private var videoTransceiver: RtpTransceiver? = null
        private var capturer: CameraVideoCapturer? = null
        private var textureHelper: SurfaceTextureHelper? = null
        private var videoSource: VideoSource? = null
        private var processor: StudioFilterProcessor? = null
        private var videoTrack: VideoTrack? = null
        @Volatile private var closed = false

        init {
            pc = factory.createPeerConnection(rtcConfiguration(config), object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                    if (state == PeerConnection.IceGatheringState.COMPLETE && !closed) events.onIceCandidate(null)
                }
                override fun onIceCandidate(c: RtcIceCandidate) { if (!closed) events.onIceCandidate(IceCandidate(c.sdp, c.sdpMid, c.sdpMLineIndex)) }
                override fun onIceCandidatesRemoved(c: Array<out RtcIceCandidate>) {}
                override fun onAddStream(stream: MediaStream) {}
                override fun onRemoveStream(stream: MediaStream) {}
                override fun onDataChannel(dc: DataChannel) {}
                override fun onRenegotiationNeeded() {}
                override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                    if (!closed) events.onConnectionState(state.name.lowercase())
                }
                override fun onTrack(transceiver: RtpTransceiver) {
                    (transceiver.receiver.track() as? VideoTrack)?.addSink(remoteVideo)
                }
            }) ?: throw IllegalStateException("could not create PeerConnection")
            audioSource = factory.createAudioSource(MediaConstraints())
            audioTrack = factory.createAudioTrack("tj-audio", audioSource)
            pc.addTrack(audioTrack, listOf("tj"))
            if (videoCapable && isCaller) {
                videoTransceiver = pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV, listOf("tj")))
                if (cameraWanted) startCamera()
            }
        }

        private fun sdpObserver(onCreate: (RtcSessionDescription) -> Unit = {}, onSet: () -> Unit = {}, onError: (String) -> Unit) = object : SdpObserver {
            override fun onCreateSuccess(sdp: RtcSessionDescription) = onCreate(sdp)
            override fun onSetSuccess() = onSet()
            override fun onCreateFailure(error: String) = onError(error)
            override fun onSetFailure(error: String) = onError(error)
        }

        private fun toOurs(d: RtcSessionDescription) = SessionDescription(d.type.canonicalForm(), d.description)
        private fun toRtc(d: SessionDescription) = RtcSessionDescription(RtcSessionDescription.Type.fromCanonicalForm(d.type), d.sdp)

        override fun createOffer(iceRestart: Boolean, done: (Result<SessionDescription>) -> Unit) {
            val mc = MediaConstraints()
            if (iceRestart) mc.mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            pc.createOffer(sdpObserver(onCreate = { offer ->
                pc.setLocalDescription(sdpObserver(onSet = { done(Result.success(toOurs(pc.localDescription))) },
                    onError = { done(Result.failure(IllegalStateException(it))) }), offer)
            }, onError = { done(Result.failure(IllegalStateException(it))) }), mc)
        }

        override fun answer(offer: SessionDescription, done: (Result<SessionDescription>) -> Unit) {
            pc.setRemoteDescription(sdpObserver(onSet = {
                // callee: use the caller's video transceiver for our camera too (sendrecv), like the web client
                if (videoCapable && videoTransceiver == null) {
                    videoTransceiver = pc.transceivers.firstOrNull { it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO }
                    videoTransceiver?.setDirection(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
                    if (cameraWanted) startCamera()
                }
                pc.createAnswer(sdpObserver(onCreate = { answer ->
                    pc.setLocalDescription(sdpObserver(onSet = { done(Result.success(toOurs(pc.localDescription))) },
                        onError = { done(Result.failure(IllegalStateException(it))) }), answer)
                }, onError = { done(Result.failure(IllegalStateException(it))) }), MediaConstraints())
            }, onError = { done(Result.failure(IllegalStateException(it))) }), toRtc(offer))
        }

        override fun setRemoteAnswer(answer: SessionDescription, done: (Result<Unit>) -> Unit) {
            if (pc.signalingState() != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) { done(Result.success(Unit)); return } // stale
            pc.setRemoteDescription(sdpObserver(onSet = { done(Result.success(Unit)) }, onError = { done(Result.failure(IllegalStateException(it))) }), toRtc(answer))
        }

        override fun addIceCandidate(candidate: IceCandidate) {
            pc.addIceCandidate(RtcIceCandidate(candidate.sdpMid ?: "0", candidate.sdpMLineIndex ?: 0, candidate.candidate))
        }

        override fun hasRemoteDescription() = pc.remoteDescription != null

        override fun updateConfig(config: RtcConfig) { pc.setConfiguration(rtcConfiguration(config)) }

        override fun setMicrophoneMuted(muted: Boolean) { audioTrack.setEnabled(!muted) }

        override fun setCameraEnabled(enabled: Boolean) {
            cameraWanted = enabled
            if (enabled) startCamera() else stopCamera()
        }

        private fun startCamera() {
            val tr = videoTransceiver ?: return
            if (videoTrack != null || closed) return
            val cams = Camera2Enumerator(app)
            val name = cams.deviceNames.firstOrNull { cams.isFrontFacing(it) } ?: cams.deviceNames.firstOrNull() ?: return
            val cap = cams.createCapturer(name, null) ?: return
            val helper = SurfaceTextureHelper.create("tj-capture", eglBase.eglBaseContext)
            val source = factory.createVideoSource(false)
            val fx = StudioFilterProcessor(app, helper.handler, filter)
            source.setVideoProcessor(fx)
            cap.initialize(helper, app, source.capturerObserver)
            cap.startCapture(1280, 720, 30)
            val track = factory.createVideoTrack("tj-video", source)
            track.addSink(localVideo)
            tr.sender.setTrack(track, false)
            capturer = cap; textureHelper = helper; videoSource = source; videoTrack = track; processor = fx
        }

        private fun stopCamera() {
            videoTransceiver?.sender?.setTrack(null, false)
            videoTrack?.removeSink(localVideo)
            try { capturer?.stopCapture() } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
            val fx = processor; val helper = textureHelper
            if (fx != null && helper != null) ThreadUtils.invokeAtFrontUninterruptibly(helper.handler, Runnable { fx.release() })
            capturer?.dispose(); videoTrack?.dispose(); videoSource?.dispose(); textureHelper?.dispose()
            capturer = null; videoTrack = null; videoSource = null; textureHelper = null; processor = null
        }

        fun switchCamera() { capturer?.switchCamera(null) }

        override fun stats(done: (MediaStats?) -> Unit) {
            pc.getStats { report ->
                var bytes = 0L; var lost = 0L; var jitter = 0.0; var rtt = 0.0; var route = ""
                val map = report.statsMap
                for (s in map.values) {
                    val m = s.members
                    if (s.type == "inbound-rtp" && m["kind"] == "audio") {
                        bytes = (m["bytesReceived"] as? BigInteger)?.toLong() ?: (m["bytesReceived"] as? Number)?.toLong() ?: 0L
                        lost = (m["packetsLost"] as? Number)?.toLong() ?: 0L
                        jitter = (m["jitter"] as? Number)?.toDouble() ?: 0.0
                    }
                    if (s.type == "transport") {
                        val pair = map[m["selectedCandidatePairId"] as? String]
                        if (pair != null) {
                            rtt = (pair.members["currentRoundTripTime"] as? Number)?.toDouble() ?: 0.0
                            route = map[pair.members["localCandidateId"] as? String]?.members?.get("candidateType") as? String ?: ""
                        }
                    }
                }
                done(MediaStats(bytes, lost, jitter, rtt, route))
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            stopCamera()
            pc.dispose()
            audioTrack.dispose()
            audioSource.dispose()
            if (current === this) current = null
        }
    }
}
