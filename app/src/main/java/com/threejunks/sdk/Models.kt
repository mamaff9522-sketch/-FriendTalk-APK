package com.threejunks.sdk

/** A user as Three Junks shows it. [externalUserId] is your app's own user id (the `sub` of your login). */
data class TjUser(
    val id: Long,
    val displayName: String,
    val username: String,
    val externalUserId: String?,
)

enum class CallDirection { OUTGOING, INCOMING }

enum class CallState {
    /** Outgoing: invite sent, waiting for the server to ring the callee. */
    DIALING,
    /** Ringing on the callee's device (both sides). */
    RINGING,
    /** Answered; WebRTC media is being set up (or re-established after a network change). */
    CONNECTING,
    /** Media is flowing. */
    ACTIVE,
    ENDED,
}

/** How a call ended, as reported by the server (`status` + `reason` of `call:ended`). */
data class CallEnd(val status: String, val reason: String, val durationSec: Int)

class Call internal constructor(
    val direction: CallDirection,
    peer: TjUser?,
    val video: Boolean,
    internal val ref: String?,
) {
    /** Server call id; null until the server confirmed an outgoing call. */
    @Volatile var id: String? = null
        internal set
    @Volatile var peer: TjUser? = peer
        internal set
    @Volatile var state: CallState = CallState.DIALING
        internal set
    /** The other side has its camera on. */
    @Volatile var peerVideo: Boolean = false
        internal set
    @Volatile var end: CallEnd? = null
        internal set

    override fun toString() = "Call(id=$id, $direction, $state, peer=${peer?.externalUserId ?: peer?.id}, video=$video)"
}

data class IceServer(val urls: List<String>, val username: String? = null, val credential: String? = null)

data class RtcConfig(val iceServers: List<IceServer>, val iceTransportPolicy: String = "all")

/** Values from `auth:ok`. */
data class ServerConfig(
    val user: TjUser,
    val serviceEnabled: Boolean,
    val voice: Boolean,
    val video: Boolean,
    val reconnectGraceSec: Int,
    val maxCallDurationSec: Int,
    val ringTimeoutSec: Int,
    val rtcConfig: RtcConfig,
    /** 3Jump filters are enabled for this project (Super Admin: feature "ฟิลเตอร์ 3Jump"). */
    val filters: Boolean = false,
)

/**
 * A filter from the project's 3Jump filter library (made in 3Jump Filter Studio, uploaded in Super Admin).
 * [filterJson] is the Studio's filter.json; give it to `ThreeJunksAndroid.setFilter`. [thumbUrl]: full URL of its
 * preview image (null = none), cacheable forever. [category]: the picker tab it belongs to.
 */
data class LibraryFilter(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val filterJson: String,
    val category: String = "",
    val thumbUrl: String? = null,
)

data class SessionDescription(val type: String, val sdp: String)

data class IceCandidate(val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int?)

/** Media progress reported to the server every 10 s (it flags/ends calls whose media stopped). */
data class MediaStats(
    val bytesReceived: Long,
    val packetsLost: Long,
    val jitter: Double,
    val rttSec: Double,
    val route: String,
)
