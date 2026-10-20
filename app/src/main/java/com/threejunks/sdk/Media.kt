package com.threejunks.sdk

/**
 * The media side of a call, kept behind an interface so the signaling/state machine in [ThreeJunksClient] is plain
 * Kotlin (testable on the JVM against the real server). The Android module implements it with org.webrtc.
 *
 * All callbacks may be invoked on any thread; the client re-posts them onto its own thread.
 */
interface PeerFactory {
    /**
     * @param isCaller the caller is the offerer (it also performs ICE restarts).
     * @param videoCapable add/accept a video transceiver so the camera can be turned on mid-call without renegotiation.
     * @param cameraOn start with the camera on.
     */
    fun create(config: RtcConfig, isCaller: Boolean, videoCapable: Boolean, cameraOn: Boolean, events: PeerEvents): Peer
}

interface PeerEvents {
    /** A local ICE candidate, or null at the end of gathering. */
    fun onIceCandidate(candidate: IceCandidate?)
    /** RTCPeerConnectionState in lower case: new, connecting, connected, disconnected, failed, closed. */
    fun onConnectionState(state: String)
}

interface Peer {
    /** Create an offer (ICE restart if asked), set it as local description, return it. */
    fun createOffer(iceRestart: Boolean, done: (Result<SessionDescription>) -> Unit)
    /** Set the remote offer, create + set the answer, return it. */
    fun answer(offer: SessionDescription, done: (Result<SessionDescription>) -> Unit)
    /** Set the remote answer. Ignore a stale answer (signaling state not have-local-offer). */
    fun setRemoteAnswer(answer: SessionDescription, done: (Result<Unit>) -> Unit)
    fun addIceCandidate(candidate: IceCandidate)
    fun hasRemoteDescription(): Boolean
    /** New TURN credentials before an ICE restart. */
    fun updateConfig(config: RtcConfig)
    fun setMicrophoneMuted(muted: Boolean)
    fun setCameraEnabled(enabled: Boolean)
    fun stats(done: (MediaStats?) -> Unit)
    fun close()
}
