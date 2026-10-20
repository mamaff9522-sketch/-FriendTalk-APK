package com.threejunks.sdk

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Returns a Three Junks **live** session token. Called on a background thread; it may block (e.g. ask your backend). */
fun interface LiveSessionProvider {
    fun session(): String
}

class LiveException(val status: Int, message: String) : RuntimeException(message)

data class LiveUser(val sub: String, val name: String)

data class LiveRoom(
    val id: String,
    val title: String,
    val description: String,
    /** created, live, interrupted, ended */
    val status: String,
    val owner: LiveUser,
    val viewerCount: Int,
    val hostOnline: Boolean,
    val cohosts: List<LiveUser>,
    val durationSec: Int,
    val raw: JSONObject,
)

/** Where and how to connect to the SFU (LiveKit) for one room. */
data class LiveJoin(val sfuUrl: String, val sfuToken: String, val role: String, val room: LiveRoom?, val raw: JSONObject)

data class LiveChatMessage(val id: String, val at: Long, val from: LiveUser, val fromRole: String, val text: String, val raw: JSONObject)

/** Realtime events of one live room (chat, counts, co-host signalling, verified gifts). Callbacks run on the executor. */
interface LiveChannelListener {
    /** connected, reconnecting, closed */
    fun onState(state: String) {}
    fun onJoined(role: String, room: LiveRoom, chat: List<LiveChatMessage>) {}
    fun onChat(message: LiveChatMessage) {}
    fun onChatDeleted(id: String) {}
    fun onViewers(count: Int) {}
    fun onHearts(count: Int, total: Int) {}
    fun onRoom(room: LiveRoom) {}
    /** The live ended (host ended it, time limit, host gone, ...). */
    fun onEnded(reason: String) {}
    /** Co-host flow: cohost_request, cohost_queue, cohost_granted, cohost_denied, cohost_invite, cohost_declined, cohost_revoked. */
    fun onCohost(type: String, message: JSONObject) {}
    /** A verified event from the app backend (e.g. type "gift"). */
    fun onEvent(event: JSONObject) {}
    /** Every message, including the ones above (for anything not covered). */
    fun onMessage(message: JSONObject) {}
    fun onError(error: String) {}
}

/**
 * Three Junks Live: REST client for the live control plane (`/live/v1`) plus the realtime room channel.
 * Media (publishing / watching) goes to the SFU with the url + token in [LiveJoin]; on Android use ThreeJunksLiveAndroid.
 *
 * All REST methods block: call them off the main thread. Errors throw [LiveException] (status + server message).
 * Sessions: your backend mints one with `POST /live/v1/sessions` (project API key), or the app exchanges its own login's
 * ID token with [exchange]. They are short-lived: [LiveSessionProvider] is asked again when the cached one is about to
 * expire or the server says 401.
 */
class ThreeJunksLive(
    baseUrl: String,
    private val sessionProvider: LiveSessionProvider,
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build(),
) {
    private val base = baseUrl.trimEnd('/')
    @Volatile private var cached: String? = null

    /** Current session token (cached until a minute before it expires). */
    fun sessionToken(forceNew: Boolean = false): String {
        val c = cached
        if (!forceNew && c != null && expiresInSec(c) > 60) return c
        return sessionProvider.session().also { cached = it }
    }

    // ---------------------------------------------------------------- rooms

    fun config(): JSONObject = api("GET", "/config")
    fun createRoom(title: String, description: String = ""): LiveRoom =
        room(api("POST", "/rooms", JSONObject().put("title", title).put("description", description)).getJSONObject("room"))
    /** Open rooms of this project (created, live or interrupted). */
    fun rooms(): List<LiveRoom> = api("GET", "/rooms").optJSONArray("rooms").objects().map(::room)
    fun room(id: String): LiveRoom = room(api("GET", "/rooms/${enc(id)}").getJSONObject("room"))
    fun updateRoom(id: String, patch: JSONObject): LiveRoom = room(api("PATCH", "/rooms/${enc(id)}", patch).getJSONObject("room"))
    /** Host: go live (or resume after an interruption). */
    fun start(id: String): LiveJoin = join(api("POST", "/rooms/${enc(id)}/start"), "host")
    /** Viewer: subscribe-only access. */
    fun watch(id: String): LiveJoin = join(api("POST", "/rooms/${enc(id)}/watch"), "viewer")
    /** A fresh SFU token for my current role (after a long network loss, or after a role change). */
    fun token(id: String): LiveJoin = join(api("POST", "/rooms/${enc(id)}/token"), "viewer")
    fun end(id: String): LiveRoom = room(api("POST", "/rooms/${enc(id)}/end").getJSONObject("room"))
    fun viewers(id: String): JSONArray = api("GET", "/rooms/${enc(id)}/viewers").optJSONArray("viewers") ?: JSONArray()

    // ---------------------------------------------------------------- chat / reactions / moderation

    fun sendChat(id: String, text: String, replyTo: String? = null): JSONObject =
        api("POST", "/rooms/${enc(id)}/messages", JSONObject().put("text", text).apply { if (replyTo != null) put("replyTo", replyTo) })
    fun hearts(id: String, count: Int = 1): JSONObject = api("POST", "/rooms/${enc(id)}/hearts", JSONObject().put("count", count))
    fun deleteMessage(id: String, messageId: String): JSONObject = api("DELETE", "/rooms/${enc(id)}/messages/${enc(messageId)}")
    /** action: mute, unmute, kick, ban, unban (host / co-host only). */
    fun moderate(id: String, sub: String, action: String, durationSec: Int? = null): JSONObject =
        api("POST", "/rooms/${enc(id)}/moderation", JSONObject().put("sub", sub).put("action", action).apply { if (durationSec != null) put("durationSec", durationSec) })

    // ---------------------------------------------------------------- co-hosts

    fun requestCohost(id: String): JSONObject = api("POST", "/rooms/${enc(id)}/cohost/request")
    fun cancelCohostRequest(id: String): JSONObject = api("DELETE", "/rooms/${enc(id)}/cohost/request")
    fun approveCohost(id: String, sub: String): JSONObject = api("POST", "/rooms/${enc(id)}/cohost/approve", JSONObject().put("sub", sub))
    fun denyCohost(id: String, sub: String): JSONObject = api("POST", "/rooms/${enc(id)}/cohost/deny", JSONObject().put("sub", sub))
    fun inviteCohost(id: String, sub: String): JSONObject = api("POST", "/rooms/${enc(id)}/cohost/invite", JSONObject().put("sub", sub))
    fun acceptCohostInvite(id: String): JSONObject = api("POST", "/rooms/${enc(id)}/cohost/accept")
    fun declineCohostInvite(id: String): JSONObject = api("POST", "/rooms/${enc(id)}/cohost/decline")
    fun removeCohost(id: String, sub: String): JSONObject = api("POST", "/rooms/${enc(id)}/cohost/remove", JSONObject().put("sub", sub))
    fun leaveStage(id: String): JSONObject = api("POST", "/rooms/${enc(id)}/cohost/leave")

    /** Open the realtime channel of a room (reconnects by itself until [LiveChannel.close]). */
    fun channel(roomId: String, listener: LiveChannelListener, callbackExecutor: Executor = Executor { it.run() }): LiveChannel =
        LiveChannel(this, base, roomId, listener, callbackExecutor, http).also { it.open() }

    // ---------------------------------------------------------------- http

    internal fun api(method: String, path: String, body: JSONObject? = null): JSONObject {
        var r = request(method, path, body, sessionToken())
        if (r.first == 401) r = request(method, path, body, sessionToken(forceNew = true))
        val (code, json) = r
        if (code !in 200..299) throw LiveException(code, json.optString("error").ifEmpty { "HTTP $code" })
        return json
    }

    private fun request(method: String, path: String, body: JSONObject?, token: String): Pair<Int, JSONObject> {
        val rb = when {
            body != null -> body.toString().toRequestBody(JSON)
            method == "POST" || method == "PATCH" -> "{}".toRequestBody(JSON)
            else -> null
        }
        val req = Request.Builder().url("$base/live/v1$path").header("Authorization", "Bearer $token").method(method, rb).build()
        http.newCall(req).execute().use { res: Response ->
            val text = res.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            return res.code to json
        }
    }

    private fun join(j: JSONObject, defaultRole: String): LiveJoin {
        val sfu = j.getJSONObject("sfu")
        return LiveJoin(sfu.getString("url"), sfu.getString("token"), j.optString("role").ifEmpty { sfu.optString("role").ifEmpty { defaultRole } },
            j.optJSONObject("room")?.let(::room), j)
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        /**
         * Exchange the ID token of your app's own login (e.g. Firebase) for a live session.
         * The project's identity provider must be configured in Three Junks (Super Admin → Projects) and live enabled.
         */
        @JvmStatic
        fun exchange(baseUrl: String, projectId: String, idToken: String, http: OkHttpClient = OkHttpClient()): String {
            val body = JSONObject().put("projectId", projectId).put("idToken", idToken).toString().toRequestBody(JSON)
            val req = Request.Builder().url(baseUrl.trimEnd('/') + "/live/v1/sessions/exchange").post(body).build()
            http.newCall(req).execute().use { r ->
                val j = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrElse { JSONObject() }
                if (!r.isSuccessful) throw LiveException(r.code, j.optString("error").ifEmpty { "HTTP ${r.code}" })
                return j.getString("token")
            }
        }

        internal fun room(o: JSONObject): LiveRoom {
            val owner = o.optJSONObject("owner") ?: JSONObject()
            return LiveRoom(
                id = o.optString("id"), title = o.optString("title"), description = o.optString("description"),
                status = o.optString("status"), owner = LiveUser(owner.optString("sub"), owner.optString("name")),
                viewerCount = o.optInt("viewerCount"), hostOnline = o.optBoolean("hostOnline"),
                cohosts = o.optJSONArray("cohosts").objects().map { LiveUser(it.optString("sub"), it.optString("name")) },
                durationSec = o.optInt("durationSec"), raw = o,
            )
        }

        internal fun chat(o: JSONObject): LiveChatMessage {
            val f = o.optJSONObject("from") ?: JSONObject()
            return LiveChatMessage(o.optString("id"), o.optLong("at"), LiveUser(f.optString("sub"), f.optString("name")), f.optString("role"), o.optString("text"), o)
        }

        internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

        private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        /** Seconds until the JWT expires (0 when unreadable). */
        internal fun expiresInSec(jwt: String): Long = runCatching {
            val payload = okio.ByteString.Companion.run { jwt.split('.')[1].decodeBase64() }!!.utf8() // url-safe alphabet accepted
            JSONObject(payload).getLong("exp") - System.currentTimeMillis() / 1000
        }.getOrDefault(0L)
    }
}

/** Realtime channel of one live room (`/live/v1/ws`). Reconnects with backoff; ends on [close] or when the server closes it for good. */
class LiveChannel internal constructor(
    private val live: ThreeJunksLive,
    base: String,
    val roomId: String,
    private val listener: LiveChannelListener,
    private val callbackExecutor: Executor,
    private val http: OkHttpClient,
) {
    private val url = base.replaceFirst(Regex("^http"), "ws") + "/live/v1/ws"
    private val loop = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "threejunks-live").apply { isDaemon = true } }
    private var ws: WebSocket? = null
    @Volatile private var closed = false
    private var attempt = 0
    private var authFailures = 0
    private var refN = 0

    internal fun open() { loop.execute { connect() } }

    /** Send a chat message (it comes back to everyone, including me, as onChat). */
    fun sendChat(text: String, replyTo: String? = null) = send(JSONObject().put("type", "chat").put("text", text).put("ref", "c${++refN}").apply { if (replyTo != null) put("replyTo", replyTo) })
    fun heart(count: Int = 1) = send(JSONObject().put("type", "heart").put("count", count))

    fun close() {
        if (closed) return
        closed = true
        runCatching { loop.execute { ws?.close(1000, "bye"); ws = null; loop.shutdown() } }
    }

    private fun send(m: JSONObject) { runCatching { loop.execute { ws?.send(m.toString()) } } }

    private fun emit(f: LiveChannelListener.() -> Unit) { if (!closed) callbackExecutor.execute { runCatching { listener.f() } } }

    private fun connect() {
        if (closed) return
        val token = try { live.sessionToken(forceNew = authFailures > 0) } catch (e: Exception) { retry(); return }
        val req = okhttp3.Request.Builder().url(url).build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject().put("type", "auth").put("token", token).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { loop.execute { if (ws === webSocket) handle(webSocket, text) } }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                runCatching { loop.execute { if (ws === webSocket) dropped(code) } }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { runCatching { loop.execute { if (ws === webSocket) dropped(code) } } }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { runCatching { loop.execute { if (ws === webSocket) dropped(-1) } } }
        })
    }

    private fun handle(socket: WebSocket, text: String) {
        val m = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (val type = m.optString("type")) {
            "auth_ok" -> socket.send(JSONObject().put("type", "join").put("roomId", roomId).put("ref", "join").toString())
            "joined" -> {
                attempt = 0; authFailures = 0
                val room = ThreeJunksLive.room(m.optJSONObject("room") ?: JSONObject())
                val chat = with(ThreeJunksLive) { m.optJSONArray("chat").objects() }.map(ThreeJunksLive::chat)
                emit { onState("connected") }
                emit { onJoined(m.optString("role"), room, chat) }
            }
            "chat" -> m.optJSONObject("message")?.let { val c = ThreeJunksLive.chat(it); emit { onChat(c) } }
            "chat_deleted" -> emit { onChatDeleted(m.optString("id")) }
            "viewers" -> emit { onViewers(m.optInt("count")) }
            "hearts" -> emit { onHearts(m.optInt("count"), m.optInt("total")) }
            "room" -> m.optJSONObject("room")?.let { val r = ThreeJunksLive.room(it); emit { onRoom(r) } }
            "ended" -> emit { onEnded(m.optString("reason")) }
            "error" -> emit { onError(m.optString("error")) }
            else -> when {
                type.startsWith("cohost_") -> emit { onCohost(type, m) }
                type == "gift" || m.has("eventId") -> emit { onEvent(m) }
            }
        }
        emit { onMessage(m) }
    }

    private fun dropped(code: Int) {
        ws = null
        if (closed) return
        // 4000 = replaced / room gone, 4003 = banned: final. 4001 = auth: retry with a fresh session (a few times).
        if (code == 4000 || code == 4003) { emit { onState("closed") }; return }
        if (code == 4001 && ++authFailures > 3) { emit { onState("closed") }; return }
        emit { onState("reconnecting") }
        retry()
    }

    private fun retry() {
        if (closed) return
        val d = minOf(15_000L, 500L shl minOf(attempt++, 5)) + (Math.random() * 300).toLong()
        runCatching { loop.schedule({ connect() }, d, TimeUnit.MILLISECONDS) }
    }
}
