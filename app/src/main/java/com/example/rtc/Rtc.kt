package com.example.rtc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.ContextCompat
import com.example.network.BrainApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import org.json.JSONObject

/** App-lifetime scope for "fire and forget" calls that must survive a screen closing (end live, leave live, hang up). */
val RtcScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

/** A call or live as friendtalk-brain returns it (its Agora fields are no longer used: media goes through Three Junks). */
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

/**
 * Voice/video state for the call and live screens. Media goes through Three Junks (our own servers): 1:1 calls via
 * [ThreeJunksCalls], live streaming via [ThreeJunksLiveMedia]. Values are Compose state, updated on the main thread.
 */
object Rtc {
    val remoteUids: SnapshotStateList<Int> = mutableStateListOf()
    var joined by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var micOn by mutableStateOf(true); private set
    var camOn by mutableStateOf(true); private set
    var speakerOn by mutableStateOf(true); private set

    /** Live: host (publisher) goes live in FriendTalk live [liveId]; a viewer watches the room of [hostUid]. */
    suspend fun joinLive(ctx: Context, liveId: String, hostUid: String, publisher: Boolean, title: String = ""): Boolean {
        leave()
        error = null; remoteUids.clear(); joined = false
        micOn = true; camOn = publisher; speakerOn = true
        return ThreeJunksLiveMedia.start(ctx, liveId, hostUid, publisher, title)
    }

    fun leave() {
        if (ThreeJunksCalls.active) ThreeJunksCalls.release()
        if (ThreeJunksLiveMedia.active) ThreeJunksLiveMedia.stop()
        remoteUids.clear(); joined = false
    }

    fun toggleMic() {
        micOn = !micOn
        if (ThreeJunksCalls.active) ThreeJunksCalls.setMic(micOn) else ThreeJunksLiveMedia.setMic(micOn)
    }
    fun toggleCam() {
        camOn = !camOn
        if (ThreeJunksCalls.active) ThreeJunksCalls.setCamera(camOn) else ThreeJunksLiveMedia.setCamera(camOn)
    }
    fun toggleSpeaker() {
        speakerOn = !speakerOn
        if (ThreeJunksCalls.active) ThreeJunksCalls.setSpeaker(speakerOn)
    }
    fun switchCamera() { if (ThreeJunksCalls.active) ThreeJunksCalls.switchCamera() else ThreeJunksLiveMedia.switchCamera() }

    /** A 1:1 call's media started (see ThreeJunksCalls). */
    internal fun tjStarted(video: Boolean) {
        error = null; remoteUids.clear(); joined = false
        micOn = true; camOn = video; speakerOn = video
    }
    /** Media from the other side flowing (true) or not (false); the screens show the remote video for "uid" 1. */
    internal fun tjRemote(on: Boolean) {
        joined = on
        if (on) { if (TJ_REMOTE !in remoteUids) remoteUids.add(TJ_REMOTE) } else remoteUids.clear()
    }
    const val TJ_REMOTE = 1

    /** Free a video view created by [localView] / [remoteView] when it leaves the screen. */
    fun releaseView(view: android.view.View) { ThreeJunksCalls.releaseView(view); ThreeJunksLiveMedia.releaseView(view) }

    @Suppress("UNUSED_PARAMETER")
    fun localView(ctx: Context, overlay: Boolean): android.view.View =
        if (ThreeJunksLiveMedia.active) ThreeJunksLiveMedia.localView(ctx) else ThreeJunksCalls.localView(ctx)
    @Suppress("UNUSED_PARAMETER")
    fun remoteView(ctx: Context, uid: Int): android.view.View =
        if (ThreeJunksLiveMedia.active) ThreeJunksLiveMedia.remoteView(ctx) else ThreeJunksCalls.remoteView(ctx)
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
