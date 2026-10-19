package com.example.rtc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.threejunks.sdk.Call
import com.threejunks.sdk.CallEnd
import com.threejunks.sdk.CallState
import com.threejunks.sdk.ServerConfig
import com.threejunks.sdk.ThreeJunksListener
import com.threejunks.sdk.android.ThreeJunksAndroid
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.webrtc.SurfaceViewRenderer
import java.util.concurrent.TimeUnit

/**
 * 1:1 call media through Three Junks (our own servers) instead of Agora, when the admin switch `call.threeJunks` is on.
 *
 * Ringing, accept/decline, missed calls and the call screens stay exactly as before (friendtalk-brain + RTDB);
 * only the voice/video stream changes. Flow:
 *  - callee: on accept, connects to Three Junks and waits for the caller's Three Junks call, which it accepts by itself;
 *  - caller: when RTDB says `accepted`, places the Three Junks call to the callee's Firebase uid (retries for a few
 *    seconds while the callee is still connecting).
 * Sign-in: the Firebase ID token is exchanged at `POST /api/v1/auth/idp-exchange` (project `call.threeJunksProject`,
 * whose identity provider must be set to this Firebase project in Three Junks Super Admin → Projects).
 * Live streaming always stays on Agora.
 */
object ThreeJunksCalls {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    /** Read the switch when a call starts (both sides read the same remote value). */
    val enabled: Boolean get() = com.example.ui.components.Tweaks.on("call.threeJunks")
    private val baseUrl get() = com.example.ui.components.Tweaks.text("call.threeJunksUrl").trim().trimEnd('/')
    private val projectId get() = com.example.ui.components.Tweaks.text("call.threeJunksProject").trim()

    /** True while the current call's media goes through Three Junks. */
    var active = false; private set
    private var sdk: ThreeJunksAndroid? = null
    private var peerUid = ""
    private var video = false
    private var outgoing = false
    private var connected = false
    private var inviteWanted = false
    private var inviteDeadline = 0L
    private var attempt = 0

    /** Start Three Junks media for a FriendTalk call (main thread). */
    fun start(ctx: Context, peerUid: String, video: Boolean, outgoing: Boolean): Boolean {
        release()
        if (baseUrl.isBlank() || projectId.isBlank()) { Rtc.error = "ยังไม่ได้ตั้งค่าเซิร์ฟเวอร์ Three Junks"; return false }
        this.peerUid = peerUid; this.video = video; this.outgoing = outgoing
        connected = false; inviteWanted = false; attempt = 0
        val s = runCatching { ThreeJunksAndroid(ctx.applicationContext, baseUrl, { token() }, listener) }
            .getOrElse { Rtc.error = "เปิดระบบเสียง/วิดีโอไม่ได้: ${it.message}"; return false }
        sdk = s; active = true
        Rtc.tjStarted(video)
        s.client.connect()
        return true
    }

    /** Caller: the callee accepted in FriendTalk, place the Three Junks call now. */
    fun peerAccepted() {
        if (!active || !outgoing || inviteWanted) return
        inviteWanted = true
        inviteDeadline = System.currentTimeMillis() + INVITE_WINDOW_MS
        if (connected) invite()
    }

    private fun invite() {
        val s = sdk ?: return
        if (s.client.currentCall != null) return
        attempt++
        s.client.callExternal(peerUid, video)
    }

    /** The callee may still be connecting: try again shortly, within the invite window. */
    private fun retryInvite(): Boolean {
        if (!active || !outgoing || !inviteWanted || System.currentTimeMillis() > inviteDeadline) return false
        main.postDelayed({ if (active && connected) invite() }, 1000)
        return true
    }

    private val listener = object : ThreeJunksListener {
        override fun onConnected(config: ServerConfig) {
            connected = true
            if (outgoing && inviteWanted && sdk?.client?.currentCall == null) invite()
        }

        override fun onIncomingCall(call: Call) {
            val s = sdk ?: return
            if (!outgoing && call.peer?.externalUserId == peerUid) s.client.accept(video) else s.client.reject()
        }

        override fun onCallStateChanged(call: Call) {
            if (call.state == CallState.ACTIVE) Rtc.tjRemote(true)
        }

        override fun onPeerReconnecting(call: Call, reconnecting: Boolean) {
            Rtc.tjRemote(!reconnecting) // "กำลังเชื่อมต่อ…" until media is back
        }

        override fun onCallEnded(call: Call, end: CallEnd) {
            Rtc.tjRemote(false)
            if (!active) return
            // callee not connected yet (or busy for a moment): the caller retries while the window is open
            if (end.status == "missed" && end.reason == "offline" && retryInvite()) return
            if (end.status == "busy" && retryInvite()) return
            // media ended on the Three Junks side (other side hung up, network lost for too long, ...): end the FriendTalk call too
            val msg = if (end.reason == "media_failed") "สัญญาณขาดหาย สายถูกตัด" else ""
            CallManager.hangupFromMedia(msg)
        }

        override fun onError(code: String, message: String) {
            if (code == "not_registered" && retryInvite()) return
            if (!active) return
            Rtc.error = when (code) {
                "auth_failed", "unauthorized" -> "เข้าสู่ระบบ Three Junks ไม่ได้"
                "rate_limited" -> "โทรถี่เกินไป ลองใหม่อีกครั้ง"
                "not_registered" -> "ปลายสายยังเชื่อมต่อ Three Junks ไม่ได้"
                else -> "การเชื่อมต่อมีปัญหา ($code)"
            }
        }
    }

    /** Firebase ID token -> Three Junks session (background thread, asked again on every reconnect). */
    private fun token(): String {
        val user = FirebaseAuth.getInstance().currentUser ?: throw IllegalStateException("not signed in")
        val idToken = Tasks.await(user.getIdToken(false), 15, TimeUnit.SECONDS).token ?: throw IllegalStateException("no Firebase token")
        val body = JSONObject().put("projectId", projectId).put("idToken", idToken).toString()
        val req = Request.Builder().url("$baseUrl/api/v1/auth/idp-exchange")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IllegalStateException("idp-exchange ${r.code}: ${text.take(200)}")
            return JSONObject(text).getString("token")
        }
    }

    fun setMic(on: Boolean) { sdk?.client?.setMicrophoneMuted(!on) }
    fun setCamera(on: Boolean) { sdk?.client?.setCameraEnabled(on) }
    fun setSpeaker(on: Boolean) { sdk?.setSpeakerphone(on) }
    fun switchCamera() { sdk?.switchCamera() }

    fun localView(ctx: Context): View = SurfaceViewRenderer(ctx).also { v ->
        v.setZOrderMediaOverlay(true) // above the remote video
        sdk?.attachLocalVideo(v)
    }

    fun remoteView(ctx: Context): View = SurfaceViewRenderer(ctx).also { v -> sdk?.attachRemoteVideo(v) }

    /** A video view left the screen: stop drawing into it and free it. */
    fun releaseView(view: View) {
        val v = view as? SurfaceViewRenderer ?: return
        sdk?.media?.let { m ->
            if (m.localVideo.target === v) m.localVideo.target = null
            if (m.remoteVideo.target === v) m.remoteVideo.target = null
        }
        runCatching { v.release() }
    }

    /** End media and disconnect (FriendTalk call finished). */
    fun release() {
        main.removeCallbacksAndMessages(null)
        val s = sdk ?: return
        sdk = null; active = false; inviteWanted = false; connected = false
        runCatching { s.client.hangup() }
        runCatching { s.release() }
    }

    private const val INVITE_WINDOW_MS = 15_000L
}
