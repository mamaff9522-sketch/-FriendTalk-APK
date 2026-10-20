package com.threejunks.sdk

/** Events from [ThreeJunksClient]. Called on the client's callback executor (the main thread on Android). */
interface ThreeJunksListener {
    fun onConnected(config: ServerConfig) {}
    /**
     * The signaling connection closed. [willReconnect] is false when it will not come back by itself:
     * the session token was refused, the account was disabled, or the same account connected elsewhere ([takenOver]).
     */
    fun onDisconnected(willReconnect: Boolean, takenOver: Boolean) {}
    fun onIncomingCall(call: Call) {}
    fun onCallStateChanged(call: Call) {}
    fun onPeerVideoChanged(call: Call, video: Boolean) {}
    /** The other side lost its connection; the server keeps the call for a grace period. */
    fun onPeerReconnecting(call: Call, reconnecting: Boolean) {}
    fun onCallEnded(call: Call, end: CallEnd) {}
    /** A call-level error from the server, e.g. `not_registered`, `rate_limited`, `video_disabled`. */
    fun onError(code: String, message: String) {}
    /** The project's 3Jump filter library changed (filter added, renamed, disabled or deleted): call [ThreeJunksClient.fetchFilters] again. */
    fun onFiltersUpdated() {}
}
