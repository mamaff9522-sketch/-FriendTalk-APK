package com.example.rtc

import android.content.Context
import android.view.View
import com.threejunks.sdk.LiveException
import com.threejunks.sdk.LiveRoom
import com.threejunks.sdk.ThreeJunksLive
import com.threejunks.sdk.android.LiveMediaListener
import com.threejunks.sdk.android.ThreeJunksLiveAndroid
import io.livekit.android.renderer.SurfaceViewRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Live streaming media through Three Junks Live (LiveKit SFU on our own servers; Agora is no longer used).
 *
 * The live itself stays a FriendTalk live: friendtalk-brain decides who may go live (POST /lives) and keeps the room
 * list, chat, gifts and viewer counts in RTDB. Only the camera/microphone stream goes through Three Junks:
 *  - host: after brain accepted the live, opens a Three Junks room (owner = the host's Firebase uid) and publishes;
 *  - viewer: finds the open Three Junks room whose owner is the live's hostUid and subscribes.
 * Sign-in: Firebase ID token exchanged at `POST /live/v1/sessions/exchange` (same project as calls, live enabled).
 */
object ThreeJunksLiveMedia {
    var active = false; private set
    private var sdk: ThreeJunksLiveAndroid? = null
    private var roomId: String? = null
    private var host = false
    private val views = mutableSetOf<SurfaceViewRenderer>()
    private val applyFilter: (com.threejunks.sdk.LibraryFilter?) -> Unit = { f -> sdk?.setLibraryFilter(f) }

    private val listener = object : LiveMediaListener {
        override fun onConnection(state: String) {
            when (state) {
                "connected" -> if (host) Rtc.tjRemote(false)
                "reconnecting" -> if (!host) Rtc.tjRemote(false)
                "ended" -> if (!host) Rtc.tjRemote(false)
                "failed" -> Rtc.error = "การเชื่อมต่อไลฟ์มีปัญหา"
            }
        }
        override fun onRemoteVideo(available: Boolean) { if (!host) Rtc.tjRemote(available) }
        override fun onError(message: String) { Rtc.error = "ไลฟ์มีปัญหา: $message" }
    }

    /** Main thread. Returns false (and sets Rtc.error) when the live media could not start. */
    suspend fun start(ctx: Context, liveId: String, hostUid: String, publisher: Boolean, title: String): Boolean {
        stop()
        val base = ThreeJunksCalls.baseUrl; val project = ThreeJunksCalls.projectId
        if (base.isBlank() || project.isBlank()) { Rtc.error = "ยังไม่ได้ตั้งค่าเซิร์ฟเวอร์ Three Junks"; return false }
        val s = ThreeJunksLiveAndroid(ctx.applicationContext, base, { ThreeJunksLive.exchange(base, project, ThreeJunksCalls.firebaseIdToken()) }, listener)
        sdk = s; active = true; host = publisher
        return try {
            val room: LiveRoom = withContext(Dispatchers.IO) {
                if (publisher) s.api.createRoom(title.ifBlank { "FriendTalk live" }, liveId) else findHostRoom(s, hostUid)
            } ?: run { Rtc.error = "ยังไม่พบภาพจากคนไลฟ์ ลองใหม่อีกครั้ง"; stop(); return false }
            if (sdk !== s) return false // closed meanwhile
            roomId = room.id
            val r = suspendCancellableCoroutine { cont -> if (publisher) s.broadcast(room.id) { cont.resume(it) } else s.watch(room.id) { cont.resume(it) } }
            r.exceptionOrNull()?.let { e -> Rtc.error = "เชื่อมต่อไลฟ์ไม่สำเร็จ: ${e.message}"; stop(); return false }
            if (publisher) TjFilters.attach(applyFilter) // viewers see the host's filtered video
            true
        } catch (e: LiveException) {
            Rtc.error = if (e.status == 403) "บัญชีนี้ยังไลฟ์ผ่านระบบใหม่ไม่ได้ (${e.message})" else "เชื่อมต่อไลฟ์ไม่สำเร็จ (${e.status})"
            stop(); false
        } catch (e: Exception) {
            Rtc.error = "เชื่อมต่อไลฟ์ไม่สำเร็จ: ${e.message}"
            stop(); false
        }
    }

    /** The host's Three Junks room: it may take a few seconds to appear after the host pressed "go live". */
    private suspend fun findHostRoom(s: ThreeJunksLiveAndroid, hostUid: String): LiveRoom? {
        repeat(15) {
            val r = s.api.rooms().filter { it.owner.sub == hostUid && (it.status == "live" || it.status == "interrupted") }
                .maxByOrNull { it.raw.optLong("startedAt") }
            if (r != null) return r
            delay(2000)
        }
        return null
    }

    /** Leave (viewer) or end the Three Junks room (host). The FriendTalk live itself is ended through brain. */
    fun stop() {
        val s = sdk ?: return
        TjFilters.detach(applyFilter)
        sdk = null; active = false
        val id = roomId; roomId = null
        if (host && id != null) s.endLive(id) { s.release() } else s.release()
        views.forEach { runCatching { it.release() } }; views.clear()
    }

    fun setMic(on: Boolean) { sdk?.setMicrophoneEnabled(on) }
    fun setCamera(on: Boolean) { sdk?.setCameraEnabled(on) }
    fun switchCamera() { sdk?.switchCamera() }

    fun localView(ctx: Context): View = SurfaceViewRenderer(ctx).also { v -> views += v; sdk?.attachLocalVideo(v) }
    fun remoteView(ctx: Context): View = SurfaceViewRenderer(ctx).also { v -> views += v; sdk?.attachRemoteVideo(v) }

    fun releaseView(view: View) {
        val v = view as? SurfaceViewRenderer ?: return
        if (!views.remove(v)) return
        sdk?.detachVideo()
        runCatching { v.release() }
    }
}
