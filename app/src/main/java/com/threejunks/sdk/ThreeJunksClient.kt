package com.threejunks.sdk

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Returns a Three Junks session token. Called on a background thread; it may block (e.g. ask your backend). */
fun interface TokenProvider {
    fun token(): String
}

/**
 * Three Junks client: signaling over WebSocket (`/ws`) and the 1:1 call state machine. Same protocol and behaviour as
 * the web client (public/app.js): the caller is the offerer and performs ICE restarts, the callee asks for one with
 * `rtc:restart`; after a signaling reconnect the call is reconciled with the server (`auth:ok.activeCall`) and rebuilt
 * with `call:rebuild` if the peer connection is gone; if media does not come back within the grace period the call is
 * ended with `media_failed`.
 *
 * Threading: all state lives on one internal thread. Listener callbacks run on [callbackExecutor].
 *
 * Tokens: your backend mints one with `POST /api/v1/auth/server-token` (project API key), or your app exchanges the ID
 * token of its own login with `POST /api/v1/auth/idp-exchange`. Tokens are short-lived; [TokenProvider] is asked for a
 * fresh one on every (re)connect.
 */
class ThreeJunksClient(
    baseUrl: String,
    private val tokenProvider: TokenProvider,
    private val peerFactory: PeerFactory,
    private val listener: ThreeJunksListener,
    private val callbackExecutor: Executor = Executor { it.run() },
    private val http: OkHttpClient = OkHttpClient.Builder().pingInterval(0, TimeUnit.SECONDS).build(),
) {
    private val base = baseUrl.trimEnd('/')
    private val loop = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "threejunks").apply { isDaemon = true } }

    private var ws: WebSocket? = null
    @Volatile private var token: String? = null
    private var wanted = false
    private var ready = false
    private var reconnectMs = 1000L
    private var authFailures = 0
    private var pongTimer: ScheduledFuture<*>? = null
    private var heartbeat: ScheduledFuture<*>? = null
    private var reconnectTimer: ScheduledFuture<*>? = null

    @Volatile var config: ServerConfig? = null
        private set

    private var call: CallCtx? = null
    /** The current call, if any. */
    val currentCall: Call? get() = call?.call

    private class CallCtx(val call: Call) {
        var peer: Peer? = null
        var isCaller = call.direction == CallDirection.OUTGOING
        var cameraOn = call.video
        var micMuted = false
        val pendingCandidates = ArrayList<IceCandidate>()
        var lastRestartMs = Long.MIN_VALUE / 2
        var discTimer: ScheduledFuture<*>? = null
        var retryTimer: ScheduledFuture<*>? = null
        var graceTimer: ScheduledFuture<*>? = null
        var statsTimer: ScheduledFuture<*>? = null
        var connected = false
    }

    // ------------------------------------------------------------------ public API

    /** Open the signaling connection (and keep it open: it reconnects by itself until [disconnect]). */
    fun connect() = post { wanted = true; openSocket() }

    /** Close the connection; an active call is hung up. */
    fun disconnect() = post {
        wanted = false
        call?.let { c -> c.call.id?.let { send(json("call:hangup", "callId" to it)) }; finish(c, CallEnd("completed", "disconnected", 0)) }
        reconnectTimer?.cancel(false); heartbeat?.cancel(false); pongTimer?.cancel(false)
        ws?.close(1000, "bye"); ws = null; ready = false
    }

    /** Call one of your app's users by their id in your app (the `sub` they signed in with). */
    fun callExternal(externalUserId: String, video: Boolean = false): Call = startCall(null, externalUserId, video)

    /** Call a user by Three Junks user id. */
    fun call(userId: Long, video: Boolean = false): Call = startCall(userId, null, video)

    fun accept(video: Boolean = false) = post {
        val c = call ?: return@post
        if (c.call.direction != CallDirection.INCOMING || c.call.state != CallState.RINGING) return@post
        c.cameraOn = video
        send(json("call:accept", "callId" to c.call.id))
    }

    fun reject() = post {
        val c = call ?: return@post
        c.call.id?.let { send(json("call:reject", "callId" to it)) }
    }

    fun hangup() = post {
        val c = call ?: return@post
        val id = c.call.id
        val local = CallEnd(if (c.call.state == CallState.ACTIVE || c.call.state == CallState.CONNECTING) "completed" else "cancelled", "local_hangup", 0)
        if (id == null || !ready) { if (id != null) send(json("call:hangup", "callId" to id)); finish(c, local); return@post }
        send(json("call:hangup", "callId" to id))
        c.peer?.close(); c.peer = null
        // the server answers with call:ended (status, reason, duration); end locally if it does not
        loop.schedule({ post { finish(c, local) } }, 3, TimeUnit.SECONDS)
    }

    fun setMicrophoneMuted(muted: Boolean) = post { call?.let { it.micMuted = muted; it.peer?.setMicrophoneMuted(muted) } }

    fun setCameraEnabled(enabled: Boolean) = post {
        val c = call ?: return@post
        c.cameraOn = enabled
        c.peer?.setCameraEnabled(enabled)
        if (c.call.id != null && c.call.state != CallState.RINGING && c.call.state != CallState.DIALING) send(json("call:media", "callId" to c.call.id, "video" to enabled))
    }

    /** Test hook: drop the socket as a network loss would (no close handshake). */
    private val filterLibrary = FilterLibrary(baseUrl, tokenProvider, http)

    /**
     * The project's 3Jump filter library (`GET /api/v1/filters`, ETag-cached). Blocking network call: run it on a
     * background thread. Returns an empty list when filters are disabled for the project (HTTP 403).
     * [ThreeJunksListener.onFiltersUpdated] tells you when to call it again.
     */
    fun fetchFilters(): List<LibraryFilter> = filterLibrary.fetch(token)

    internal fun debugDropConnection() = post { ws?.cancel() }

    /** Stop the internal thread. The client cannot be used afterwards. */
    fun shutdown() { disconnect(); loop.shutdown() }

    // ------------------------------------------------------------------ calls

    private fun startCall(userId: Long?, externalUserId: String?, video: Boolean): Call {
        val ref = UUID.randomUUID().toString().substring(0, 12)
        val call = Call(CallDirection.OUTGOING, null, video, ref)
        post {
            if (this.call != null) { emit { listener.onError("already_in_call", "a call is already in progress") }; call.state = CallState.ENDED; return@post }
            if (!ready) { emit { listener.onError("not_connected", "not connected to Three Junks") }; call.state = CallState.ENDED; return@post }
            this.call = CallCtx(call)
            val m = json("call:invite", "ref" to ref, "media" to if (video) "video" else "audio")
            if (externalUserId != null) m.put("toExternal", externalUserId) else m.put("to", userId)
            send(m)
            emit { listener.onCallStateChanged(call) }
        }
        return call
    }

    private fun finish(c: CallCtx, end: CallEnd) {
        if (call !== c) return
        call = null
        c.discTimer?.cancel(false); c.retryTimer?.cancel(false); c.graceTimer?.cancel(false); c.statsTimer?.cancel(false)
        c.peer?.close(); c.peer = null
        c.call.state = CallState.ENDED; c.call.end = end
        emit { listener.onCallEnded(c.call, end) }
    }

    private fun setState(c: CallCtx, s: CallState) {
        if (c.call.state == s) return
        c.call.state = s
        emit { listener.onCallStateChanged(c.call) }
    }

    private fun startPeer(c: CallCtx, isCaller: Boolean) {
        c.peer?.close()
        c.isCaller = isCaller; c.connected = false; c.pendingCandidates.clear()
        val cfg = fetchIceConfig() ?: config?.rtcConfig ?: RtcConfig(emptyList())
        if (call !== c) return
        val videoCapable = config?.video ?: false
        val peer = peerFactory.create(cfg, isCaller, videoCapable, c.cameraOn && videoCapable, object : PeerEvents {
            override fun onIceCandidate(candidate: IceCandidate?) = post {
                if (call !== c) return@post
                val m = json("rtc:ice", "callId" to c.call.id)
                m.put("candidate", candidate?.let { JSONObject().put("candidate", it.candidate).put("sdpMid", it.sdpMid ?: JSONObject.NULL).put("sdpMLineIndex", it.sdpMLineIndex ?: JSONObject.NULL) } ?: JSONObject.NULL)
                send(m)
            }
            override fun onConnectionState(state: String) = post { if (call === c) onPeerState(c, state) }
        })
        c.peer = peer
        if (c.micMuted) peer.setMicrophoneMuted(true)
        c.statsTimer?.cancel(false)
        c.statsTimer = loop.scheduleWithFixedDelay({ reportStats(c) }, 10, 10, TimeUnit.SECONDS)
        if (isCaller) sendOffer(c, iceRestart = false)
    }

    private fun sendOffer(c: CallCtx, iceRestart: Boolean) {
        val peer = c.peer ?: return
        peer.createOffer(iceRestart) { r -> post {
            if (call !== c || c.peer !== peer) return@post
            r.onSuccess { send(json("rtc:offer", "callId" to c.call.id, "sdp" to sdpJson(it))) }
                .onFailure { emit { listener.onError("offer_failed", it.message ?: "offer failed") } }
        } }
    }

    private fun onPeerState(c: CallCtx, st: String) {
        when (st) {
            "connected" -> {
                c.discTimer?.cancel(false); c.retryTimer?.cancel(false); c.graceTimer?.cancel(false)
                c.discTimer = null; c.retryTimer = null; c.graceTimer = null
                c.connected = true
                setState(c, CallState.ACTIVE)
                // after an ICE restart / resume make sure the peer knows our camera state
                if (c.cameraOn) send(json("call:media", "callId" to c.call.id, "video" to true))
            }
            "disconnected", "failed" -> {
                c.connected = false
                setState(c, CallState.CONNECTING)
                startMediaGrace(c)
                if (st == "failed") requestRestart(c)
                else if (c.discTimer == null) c.discTimer = loop.schedule({ post { c.discTimer = null; if (call === c && !c.connected) requestRestart(c) } }, 2500, TimeUnit.MILLISECONDS)
                if (c.retryTimer == null) c.retryTimer = loop.scheduleWithFixedDelay({ post { if (call === c && !c.connected) requestRestart(c) } }, 5, 5, TimeUnit.SECONDS)
            }
        }
    }

    private fun requestRestart(c: CallCtx) {
        if (c.peer == null || !ready) return
        if (c.call.state == CallState.RINGING || c.call.state == CallState.DIALING) return
        val now = System.nanoTime() / 1_000_000
        if (now - c.lastRestartMs < 4000) return
        c.lastRestartMs = now
        if (c.isCaller) {
            fetchIceConfig()?.let { c.peer?.updateConfig(it) }
            sendOffer(c, iceRestart = true)
        } else send(json("rtc:restart", "callId" to c.call.id))
    }

    /** If media cannot be restored within the grace period, end the call instead of hanging forever. */
    private fun startMediaGrace(c: CallCtx) {
        if (c.graceTimer != null) return
        val sec = (config?.reconnectGraceSec ?: 20) + 5L
        c.graceTimer = loop.schedule({ post {
            c.graceTimer = null
            if (call !== c || c.connected) return@post
            send(json("call:hangup", "callId" to c.call.id, "reason" to "media_failed"))
            finish(c, CallEnd("failed", "media_failed", 0))
        } }, sec, TimeUnit.SECONDS)
    }

    private fun reportStats(c: CallCtx) {
        val peer = c.peer ?: return
        peer.stats { s -> post {
            if (call !== c || s == null) return@post
            send(json("call:stats", "callId" to c.call.id, "connectionState" to if (c.connected) "connected" else "connecting",
                "bytesReceived" to s.bytesReceived, "packetsLost" to s.packetsLost, "jitter" to s.jitter, "rtt" to s.rttSec, "route" to s.route))
        } }
    }

    private fun flushCandidates(c: CallCtx) {
        val peer = c.peer ?: return
        for (cand in c.pendingCandidates) peer.addIceCandidate(cand)
        c.pendingCandidates.clear()
    }

    // ------------------------------------------------------------------ signaling messages

    private fun onSignal(m: JSONObject) {
        val type = m.optString("type")
        val c = call
        val callId = m.optString("callId", "")
        val mine = c != null && c.call.id != null && c.call.id == callId
        when (type) {
            "pong" -> { pongTimer?.cancel(false); pongTimer = null }
            "auth:ok" -> onAuthOk(m)
            "call:ringing" -> if (c != null && c.call.ref != null && c.call.ref == m.optString("ref")) {
                c.call.id = callId
                m.optJSONObject("to")?.let { c.call.peer = user(it) }
                setState(c, CallState.RINGING)
            }
            "call:incoming" -> {
                if (mine) return // re-delivered after reconnect
                if (c != null) { send(json("call:reject", "callId" to callId)); return }
                val call = Call(CallDirection.INCOMING, m.optJSONObject("from")?.let { user(it) }, m.optString("media") == "video", null)
                call.id = callId; call.state = CallState.RINGING
                this.call = CallCtx(call).also { it.cameraOn = false }
                emit { listener.onIncomingCall(call) }
            }
            "call:accepted" -> if (mine) {
                setState(c!!, CallState.CONNECTING)
                if (c.call.direction == CallDirection.OUTGOING) startPeer(c, isCaller = true)
            }
            "rtc:offer" -> if (mine) {
                val cc = c!!
                if (cc.peer == null) startPeer(cc, isCaller = false)
                val peer = cc.peer ?: return
                peer.answer(sdp(m.getJSONObject("sdp"))) { r -> post {
                    if (call !== cc || cc.peer !== peer) return@post
                    r.onSuccess { send(json("rtc:answer", "callId" to cc.call.id, "sdp" to sdpJson(it))); flushCandidates(cc) }
                        .onFailure { emit { listener.onError("answer_failed", it.message ?: "answer failed") } }
                } }
            }
            "rtc:answer" -> if (mine) {
                val cc = c!!; val peer = cc.peer ?: return
                peer.setRemoteAnswer(sdp(m.getJSONObject("sdp"))) { post { if (call === cc && cc.peer === peer) flushCandidates(cc) } }
            }
            "rtc:ice" -> if (mine) {
                val o = m.optJSONObject("candidate") ?: return // null = remote end of candidates
                val cand = IceCandidate(o.getString("candidate"), if (o.isNull("sdpMid")) null else o.optString("sdpMid"),
                    if (o.isNull("sdpMLineIndex")) null else o.optInt("sdpMLineIndex"))
                val peer = c!!.peer
                if (peer != null && peer.hasRemoteDescription()) peer.addIceCandidate(cand) else c.pendingCandidates.add(cand)
            }
            "rtc:restart" -> if (mine && c!!.isCaller) { c.lastRestartMs = Long.MIN_VALUE / 2; requestRestart(c) }
            "call:rebuild" -> if (mine) {
                // the other side (or we, after a reload) lost its peer connection: both build a fresh one
                c!!.peer?.close(); c.peer = null; c.connected = false
                setState(c, CallState.CONNECTING)
                if (c.isCaller) startPeer(c, isCaller = true)
            }
            "call:media" -> if (mine) { c!!.call.peerVideo = m.optBoolean("video"); emit { listener.onPeerVideoChanged(c.call, c.call.peerVideo) } }
            "call:peer-reconnecting" -> if (mine) emit { listener.onPeerReconnecting(c!!.call, true) }
            "call:peer-reconnected" -> if (mine) {
                emit { listener.onPeerReconnecting(c!!.call, false) }
                if (!c!!.connected) requestRestart(c)
            }
            "call:ended" -> if (c != null && (mine || (c.call.ref != null && c.call.ref == m.optString("ref")))) {
                if (c.call.id == null) c.call.id = callId
                finish(c, CallEnd(m.optString("status", "completed"), m.optString("reason", ""), m.optInt("durationSec", 0)))
            }
            "call:error" -> {
                val code = m.optString("code"); val msg = m.optString("message")
                emit { listener.onError(code, msg) }
                if (c != null && c.call.state == CallState.DIALING && c.call.ref != null && c.call.ref == m.optString("ref")) finish(c, CallEnd("failed", code, 0))
            }
            "filters:updated" -> emit { listener.onFiltersUpdated() }
            "ice:config" -> m.optJSONObject("rtcConfig")?.let { rc -> config = config?.copy(rtcConfig = rtcConfig(rc)) }
        }
    }

    private fun onAuthOk(m: JSONObject) {
        ready = true; reconnectMs = 1000; authFailures = 0
        val f = m.optJSONObject("features")
        val cfg = ServerConfig(
            user = user(m.getJSONObject("user")),
            serviceEnabled = m.optBoolean("serviceEnabled", true),
            voice = f?.optBoolean("voice", true) ?: true,
            video = f?.optBoolean("video", false) ?: false,
            reconnectGraceSec = m.optInt("reconnectGraceSec", 20),
            maxCallDurationSec = m.optInt("maxCallDurationSec", 0),
            ringTimeoutSec = m.optInt("ringTimeoutSec", 30),
            rtcConfig = m.optJSONObject("rtcConfig")?.let { rtcConfig(it) } ?: RtcConfig(emptyList()),
            filters = f?.optBoolean("beauty", false) ?: false,
        )
        config = cfg
        heartbeat?.cancel(false)
        heartbeat = loop.scheduleWithFixedDelay({ checkSocket(10_000) }, 15, 15, TimeUnit.SECONDS)
        emit { listener.onConnected(cfg) }
        resumeAfterReconnect(m.optJSONObject("activeCall"))
    }

    /** After the signaling socket (re)connects, reconcile the local call with the server. */
    private fun resumeAfterReconnect(ac: JSONObject?) {
        val c = call
        if (c != null) {
            if (ac == null || (c.call.id != null && ac.optString("id") != c.call.id)) {
                if (c.call.state != CallState.DIALING) finish(c, CallEnd("completed", "disconnected", 0))
                return
            }
            if (ac.optString("state") == "active") {
                when {
                    c.peer != null && c.connected -> {}
                    c.peer != null -> requestRestart(c)
                    else -> send(json("call:rebuild", "callId" to c.call.id))
                }
            }
            return
        }
        if (ac == null) return
        // no local state (process was restarted) but the server still has our call
        val dir = if (ac.optString("direction") == "outgoing") CallDirection.OUTGOING else CallDirection.INCOMING
        val call = Call(dir, ac.optJSONObject("peer")?.let { user(it) }, ac.optString("media") == "video", null)
        call.id = ac.optString("id")
        val ctx = CallCtx(call).also { it.cameraOn = false }
        this.call = ctx
        if (ac.optString("state") == "ringing") {
            call.state = CallState.RINGING
            emit { if (dir == CallDirection.INCOMING) listener.onIncomingCall(call) else listener.onCallStateChanged(call) }
        } else {
            call.state = CallState.CONNECTING
            emit { listener.onCallStateChanged(call) }
            send(json("call:rebuild", "callId" to call.id))
        }
    }

    // ------------------------------------------------------------------ socket

    private fun openSocket() {
        if (!wanted || ws != null) return
        reconnectTimer?.cancel(false); reconnectTimer = null
        val tok = try { tokenProvider.token().also { token = it } } catch (e: Exception) {
            emit { listener.onError("token_failed", e.message ?: "could not get a session token") }
            scheduleReconnect(); return
        }
        val url = base.replaceFirst(Regex("^http"), "ws") + "/ws"
        val socket = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject().put("type", "auth").put("token", tok).put("caps", JSONArray().put("resume").put("video")).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) = post {
                if (ws !== webSocket) return@post
                val m = try { JSONObject(text) } catch (e: Exception) { return@post }
                if (m.optString("type") == "auth:error") authFailures++
                onSignal(m)
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = post { onSocketGone(webSocket, code) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = post { onSocketGone(webSocket, 1006) }
        })
        ws = socket
    }

    private fun onSocketGone(socket: WebSocket, code: Int) {
        if (ws !== socket) return
        ws = null; ready = false
        heartbeat?.cancel(false); pongTimer?.cancel(false); pongTimer = null
        val takenOver = code == 4001
        // 4401: token refused (retry once with a fresh token), 4003: account disabled, 4001: replaced by another device
        val fatal = takenOver || code == 4003 || (code == 4401 && authFailures >= 2)
        val c = call
        if (fatal) {
            wanted = false
            if (c != null) finish(c, CallEnd("completed", "disconnected", 0))
        }
        emit { listener.onDisconnected(!fatal && wanted, takenOver) }
        if (!fatal && wanted) scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (!wanted || reconnectTimer != null) return
        val active = call != null
        val delay = if (active) 500L else reconnectMs
        if (!active) reconnectMs = minOf(reconnectMs * 2, 15_000)
        reconnectTimer = loop.schedule({ post { reconnectTimer = null; openSocket() } }, delay, TimeUnit.MILLISECONDS)
    }

    /** Application-level heartbeat: detects half-open sockets (phone slept, network switched) much faster than TCP. */
    private fun checkSocket(timeoutMs: Long) = post {
        val socket = ws ?: return@post
        if (!ready || pongTimer != null) return@post
        send(json("ping", "t" to System.currentTimeMillis()))
        pongTimer = loop.schedule({ post {
            pongTimer = null
            if (ws === socket) { socket.cancel(); onSocketGone(socket, 4000) }
        } }, timeoutMs, TimeUnit.MILLISECONDS)
    }

    /** Call when the app returns to the foreground or the network changes: re-validates the connection now. */
    fun onNetworkOrForegroundChanged() = post {
        if (!wanted) return@post
        if (ws == null) { reconnectTimer?.cancel(false); reconnectTimer = null; openSocket(); return@post }
        pongTimer?.cancel(false); pongTimer = null
        checkSocket(5000)
        call?.let { if (it.peer != null && !it.connected) requestRestart(it) }
    }

    // ------------------------------------------------------------------ helpers

    private fun send(m: JSONObject) { if (ready || m.optString("type") == "auth") ws?.send(m.toString()) }

    private fun fetchIceConfig(): RtcConfig? {
        val tok = token ?: return null
        return try {
            http.newCall(Request.Builder().url("$base/api/v1/ice").header("Authorization", "Bearer $tok").build()).execute().use { r ->
                if (!r.isSuccessful) null else rtcConfig(JSONObject(r.body!!.string()))
            }
        } catch (e: Exception) { null }
    }

    private fun post(block: () -> Unit) {
        if (loop.isShutdown) return
        loop.execute { try { block() } catch (e: Exception) { emit { listener.onError("internal", e.toString()) } } }
    }

    private fun emit(block: () -> Unit) = callbackExecutor.execute(block)

    companion object {
        internal fun json(type: String, vararg kv: Pair<String, Any?>): JSONObject {
            val o = JSONObject().put("type", type)
            for ((k, v) in kv) o.put(k, v ?: JSONObject.NULL)
            return o
        }
        internal fun sdpJson(s: SessionDescription) = JSONObject().put("type", s.type).put("sdp", s.sdp)
        internal fun sdp(o: JSONObject) = SessionDescription(o.getString("type"), o.getString("sdp"))
        internal fun user(o: JSONObject) = TjUser(o.optLong("id"), o.optString("displayName"), o.optString("username"),
            if (o.isNull("externalUserId")) null else o.optString("externalUserId").ifEmpty { null })
        internal fun rtcConfig(o: JSONObject): RtcConfig {
            val list = ArrayList<IceServer>()
            val arr = o.optJSONArray("iceServers") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val urls = s.opt("urls")
                val u = when (urls) { is JSONArray -> (0 until urls.length()).map { urls.getString(it) }; is String -> listOf(urls); else -> emptyList() }
                list.add(IceServer(u, s.optString("username").ifEmpty { null }, s.optString("credential").ifEmpty { null }))
            }
            return RtcConfig(list, o.optString("iceTransportPolicy", "all").ifEmpty { "all" })
        }
    }
}
