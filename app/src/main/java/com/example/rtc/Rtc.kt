package com.example.rtc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.ContextCompat
import com.example.network.BrainApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import org.json.JSONObject

/** App-lifetime scope for "fire and forget" calls that must survive a screen closing (end live, leave live, hang up). */
val RtcScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

/** One joined Agora channel (a call or a live room). Token/appId always come from friendtalk-brain. */
data class RtcSession(val id: String, val appId: String, val channel: String, val uid: Int, val peerUid: Int, val token: String, val video: Boolean)

object RtcApi {
    suspend fun post(path: String, body: JSONObject = JSONObject()): BrainApi.ApiResponse? =
        FirebaseAuth.getInstance().currentUser?.let { runCatching { BrainApi.call(it, "POST", path, body) }.getOrNull() }
    fun session(j: JSONObject) = RtcSession(j.optString("callId").ifBlank { j.optString("liveId") }, j.optString("appId"), j.optString("channel"),
        j.optInt("uid"), if (j.has("peerUid")) j.optInt("peerUid") else j.optInt("hostUid"), j.optString("token"), j.optBoolean("video", true))
    fun error(r: BrainApi.ApiResponse?): String = r?.json?.optString("error")?.ifBlank { null } ?: when (r?.code) {
        null -> "เชื่อมต่อเซิร์ฟเวอร์ไม่ได้"; 404 -> "เซิร์ฟเวอร์ยังไม่อัปเดต (404)"; else -> "ไม่สำเร็จ (${r.code})"
    }
    const val DB_URL = "https://friendtalk-4e623-default-rtdb.asia-southeast1.firebasedatabase.app"
    val db: FirebaseDatabase get() = FirebaseDatabase.getInstance(DB_URL)
}

/** The single Agora engine. Callbacks arrive on an SDK thread and are posted to the main thread as Compose state. */
object Rtc {
    var engine: RtcEngine? = null; private set
    val remoteUids: SnapshotStateList<Int> = mutableStateListOf()
    var joined by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var micOn by mutableStateOf(true); private set
    var camOn by mutableStateOf(true); private set
    var speakerOn by mutableStateOf(true); private set
    private val main = Handler(Looper.getMainLooper())
    private val handler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) { main.post { joined = true } }
        override fun onUserJoined(uid: Int, elapsed: Int) { main.post { if (uid !in remoteUids) remoteUids.add(uid) } }
        override fun onUserOffline(uid: Int, reason: Int) { main.post { remoteUids.remove(uid) } }
        override fun onError(err: Int) { main.post { error = "การเชื่อมต่อมีปัญหา (รหัส $err)" } }
    }

    /** live=false → 1:1 call; live=true → broadcaster (publisher) or audience. */
    fun join(ctx: Context, s: RtcSession, live: Boolean, publisher: Boolean, video: Boolean): Boolean {
        leave()
        error = null
        val e = runCatching {
            RtcEngine.create(RtcEngineConfig().apply {
                mContext = ctx.applicationContext; mAppId = s.appId; mEventHandler = handler
                mChannelProfile = if (live) Constants.CHANNEL_PROFILE_LIVE_BROADCASTING else Constants.CHANNEL_PROFILE_COMMUNICATION
            })
        }.getOrElse { error = "เปิดระบบเสียง/วิดีโอไม่ได้: ${it.message}"; return false }
        engine = e
        if (video) e.enableVideo()
        val o = ChannelMediaOptions().apply {
            channelProfile = if (live) Constants.CHANNEL_PROFILE_LIVE_BROADCASTING else Constants.CHANNEL_PROFILE_COMMUNICATION
            clientRoleType = if (publisher) Constants.CLIENT_ROLE_BROADCASTER else Constants.CLIENT_ROLE_AUDIENCE
            publishMicrophoneTrack = publisher
            publishCameraTrack = publisher && video
            autoSubscribeAudio = true
            autoSubscribeVideo = video
            if (!publisher) audienceLatencyLevel = Constants.AUDIENCE_LATENCY_LEVEL_LOW_LATENCY
        }
        if (publisher && video) { e.startPreview(); Beauty.load(ctx); Beauty.apply(e) } // face beauty on my camera
        micOn = true; camOn = video; speakerOn = video || live
        e.setDefaultAudioRoutetoSpeakerphone(speakerOn)
        val rc = e.joinChannel(s.token, s.channel, s.uid, o)
        if (rc != 0) { error = "เข้าห้องไม่สำเร็จ (รหัส $rc)"; leave(); return false }
        return true
    }

    fun leave() {
        val e = engine ?: return
        engine = null
        runCatching { e.stopPreview(); e.leaveChannel() }
        runCatching { RtcEngine.destroy() }
        remoteUids.clear(); joined = false
    }

    fun toggleMic() { val e = engine ?: return; micOn = !micOn; e.muteLocalAudioStream(!micOn) }
    fun toggleCam() { val e = engine ?: return; camOn = !camOn; e.muteLocalVideoStream(!camOn); e.enableLocalVideo(camOn) }
    fun toggleSpeaker() { val e = engine ?: return; speakerOn = !speakerOn; e.setEnableSpeakerphone(speakerOn) }
    fun switchCamera() { engine?.switchCamera() }

    // TextureView (not SurfaceView) so rounded corners / overlays from Compose apply to the video too.
    @Suppress("UNUSED_PARAMETER")
    fun localView(ctx: Context, overlay: Boolean): android.view.TextureView = android.view.TextureView(ctx).also {
        engine?.setupLocalVideo(VideoCanvas(it, VideoCanvas.RENDER_MODE_HIDDEN, 0))
    }
    fun remoteView(ctx: Context, uid: Int): android.view.TextureView = android.view.TextureView(ctx).also {
        engine?.setupRemoteVideo(VideoCanvas(it, VideoCanvas.RENDER_MODE_HIDDEN, uid))
    }
}

/** Asks for mic (+camera for video) and runs [then] only when granted. */
@Composable
fun rememberRtcPermission(onDenied: () -> Unit = {}): (video: Boolean, then: () -> Unit) -> Unit {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val ok = res.values.all { it }
        val p = pending; pending = null
        if (ok) p?.invoke() else onDenied()
    }
    return { video, then ->
        val need = listOfNotNull(Manifest.permission.RECORD_AUDIO, if (video) Manifest.permission.CAMERA else null)
            .filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isEmpty()) then() else { pending = then; launcher.launch(need.toTypedArray()) }
    }
}

/** Value listener on an RTDB path as Compose state; detached when the composable leaves. */
@Composable
fun rememberDbValue(path: String?): DataSnapshot? {
    var v by remember(path) { mutableStateOf<DataSnapshot?>(null) }
    DisposableEffect(path) {
        if (path == null) return@DisposableEffect onDispose { }
        val ref = RtcApi.db.getReference(path)
        val l = object : ValueEventListener { override fun onDataChange(s: DataSnapshot) { v = s }; override fun onCancelled(e: DatabaseError) {} }
        ref.addValueEventListener(l)
        onDispose { ref.removeEventListener(l) }
    }
    return v
}
