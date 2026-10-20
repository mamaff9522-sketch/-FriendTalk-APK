package com.threejunks.sdk.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.threejunks.sdk.LibraryFilter
import com.threejunks.sdk.LiveChannel
import com.threejunks.sdk.LiveChannelListener
import com.threejunks.sdk.LiveException
import com.threejunks.sdk.LiveJoin
import com.threejunks.sdk.LiveSessionProvider
import com.threejunks.sdk.ThreeJunksLive
import com.threejunks.sdk.android.filter.LiveStudioFilterProcessor
import com.threejunks.sdk.android.filter.StudioFilterControl
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.DisconnectReason
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor

/** Live media events. Callbacks run on the main thread. */
interface LiveMediaListener {
    /** connecting, connected, reconnecting, ended, failed */
    fun onConnection(state: String) {}
    /** The host's (or a co-host's) video appeared / went away for a viewer. */
    fun onRemoteVideo(available: Boolean) {}
    fun onError(message: String) {}
}

/**
 * Three Junks live streaming for Android: one object per app (or per live screen).
 *
 * ```
 * val live = ThreeJunksLiveAndroid(context, "https://call.example.com", { myBackend.liveSession() }, listener)
 * // host
 * live.attachLocalVideo(localRenderer)
 * live.broadcast(room.id) { result -> ... }          // room from live.api.createRoom(...) (background thread)
 * // viewer
 * live.attachRemoteVideo(remoteRenderer)
 * live.watch(roomId) { result -> ... }
 * val chat = live.channel(roomId, chatListener)       // chat, hearts, viewer count, gifts
 * live.leave()  /  live.endLive(roomId)
 * ```
 * Media goes through the project's LiveKit SFU (Apache-2.0, self-hosted next to Three Junks). Nothing is recorded.
 * Views are `io.livekit.android.renderer.SurfaceViewRenderer`. Hosts need RECORD_AUDIO and CAMERA at runtime.
 */
class ThreeJunksLiveAndroid(
    context: Context,
    baseUrl: String,
    sessionProvider: LiveSessionProvider,
    private val listener: LiveMediaListener = object : LiveMediaListener {},
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainExecutor = Executor { main.post(it) }

    /** REST API of the live control plane (blocking: call off the main thread). */
    val api = ThreeJunksLive(baseUrl, sessionProvider)

    private var room: Room? = null
    private var events: Job? = null
    private var roomId: String? = null
    /** host, cohost or viewer while connected. */
    var role: String? = null; private set
    private var localView: SurfaceViewRenderer? = null
    private var remoteView: SurfaceViewRenderer? = null
    private var remoteTrack: VideoTrack? = null
    private var localTrack: LocalVideoTrack? = null
    private var filterProcessor: LiveStudioFilterProcessor? = null
    private var front = true
    private var micOn = true
    private var camOn = true
    private var leaving = false

    /** Show my camera (host / co-host). Call on the main thread, before or after [broadcast]. */
    fun attachLocalVideo(view: SurfaceViewRenderer) {
        localView = view
        room?.let { initView(it, view); localTrack?.addRenderer(view) }
        view.setMirror(front)
    }

    /** Show the host's video (viewer). Call on the main thread, before or after [watch]. */
    fun attachRemoteVideo(view: SurfaceViewRenderer) {
        remoteView = view
        room?.let { initView(it, view); remoteTrack?.addRenderer(view) }
    }

    /** Stop drawing into the views (e.g. the screen closes). The views must then be released by the caller. */
    fun detachVideo() {
        localView?.let { localTrack?.removeRenderer(it) }
        remoteView?.let { remoteTrack?.removeRenderer(it) }
        localView = null; remoteView = null
    }

    /** Host: go live in [roomId] (created with `api.createRoom`), publishing camera + microphone. */
    fun broadcast(roomId: String, done: (Result<LiveJoin>) -> Unit = {}) = join(roomId, done) { api.start(roomId) }

    /** Viewer: watch [roomId] (subscribe only, no camera / microphone needed). */
    fun watch(roomId: String, done: (Result<LiveJoin>) -> Unit = {}) = join(roomId, done) { api.watch(roomId) }

    /** The live camera's filter (shared by every room of this object). */
    val filter = StudioFilterControl()

    /**
     * Filter on my live camera: the content of `filter.json` exported by 3Jump Filter Studio (null = off). Rendered on
     * this phone's GPU with the Studio's engine before the video is sent, so viewers see it. Can change at any time.
     * Throws IllegalArgumentException for an invalid filter.json.
     */
    fun setFilter(filterJson: String?) = filter.set(filterJson)

    /** Apply a filter from the project's 3Jump filter library (null = off). */
    fun setLibraryFilter(f: LibraryFilter?) = setFilter(f?.filterJson)

    /** Realtime channel of the room (chat, hearts, viewer count, co-host requests, gifts); callbacks on the main thread. */
    fun channel(roomId: String, l: LiveChannelListener): LiveChannel = api.channel(roomId, l, mainExecutor)

    fun setMicrophoneEnabled(on: Boolean) { micOn = on; room?.let { r -> scope.launch { runCatching { r.localParticipant.setMicrophoneEnabled(on) } } } }

    fun setCameraEnabled(on: Boolean) {
        camOn = on
        val r = room ?: return
        scope.launch {
            runCatching {
                if (on && r.localParticipant.getTrackPublication(Track.Source.CAMERA) == null) publishCamera(r)
                else r.localParticipant.setCameraEnabled(on)
            }
            if (on) bindLocalTrack(r)
        }
    }

    /** Front <-> back camera without re-publishing (viewers keep the stream). */
    fun switchCamera() {
        front = !front
        localTrack?.switchCamera(position = if (front) CameraPosition.FRONT else CameraPosition.BACK)
        localView?.setMirror(front)
    }

    /** Leave the room (viewer, or host who will come back: the live stays open for the reconnect grace period). */
    fun leave() {
        leaving = true
        disconnect()
        roomId = null; role = null
    }

    /** Host: end the live for everyone, then leave. */
    fun endLive(roomId: String, done: (Result<Unit>) -> Unit = {}) {
        leave()
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { api.end(roomId) }; Unit }
            done(r)
        }
    }

    fun release() { leave(); detachVideo(); scope.cancel() }

    // ------------------------------------------------------------------ internals

    private fun join(id: String, done: (Result<LiveJoin>) -> Unit, fetch: () -> LiveJoin) {
        disconnect()
        leaving = false; roomId = id
        listener.onConnection("connecting")
        scope.launch {
            val r = runCatching {
                val j = withContext(Dispatchers.IO) { fetch() }
                connect(j)
                j
            }
            r.exceptionOrNull()?.let { e -> if (roomId == id) { listener.onConnection("failed"); listener.onError(e.message ?: e.toString()) } }
            done(r)
        }
    }

    private suspend fun connect(j: LiveJoin) {
        val r = LiveKit.create(app, RoomOptions(adaptiveStream = true, dynacast = true))
        room = r; role = j.role
        localView?.let { initView(r, it) }
        remoteView?.let { initView(r, it) }
        events = scope.launch { r.events.collect { e -> onEvent(r, e) } }
        r.connect(j.sfuUrl, j.sfuToken)
        if (j.role != "viewer") {
            r.localParticipant.setMicrophoneEnabled(micOn)
            if (camOn) publishCamera(r)
            bindLocalTrack(r)
        }
        // tracks that were already published before we subscribed
        for (p in r.remoteParticipants.values) for ((_, t) in p.videoTrackPublications) (t as? VideoTrack)?.let { showRemote(it) }
        listener.onConnection("connected")
    }

    /**
     * Camera with the 3Jump filter processor in front of the encoder (LiveKit's own camera track has none). Published
     * as the CAMERA source, so setCameraEnabled(false/true) afterwards mutes / resumes this same track.
     */
    private suspend fun publishCamera(r: Room) {
        val fx = LiveStudioFilterProcessor(app, filter)
        val options = r.localParticipant.videoTrackCaptureDefaults.copy(position = if (front) CameraPosition.FRONT else CameraPosition.BACK)
        val t = r.localParticipant.createVideoTrack(name = "camera", options = options, videoProcessor = fx)
        filterProcessor = fx
        t.startCapture()
        if (!r.localParticipant.publishVideoTrack(t)) {
            t.stopCapture(); fx.release(); filterProcessor = null; t.dispose()
            throw IllegalStateException("camera could not be published")
        }
    }

    private fun bindLocalTrack(r: Room) {
        val t = r.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack ?: return
        if (t === localTrack) return
        localView?.let { v -> localTrack?.removeRenderer(v); t.addRenderer(v) }
        localTrack = t
    }

    private fun showRemote(t: VideoTrack) {
        if (t === remoteTrack) return
        remoteView?.let { v -> remoteTrack?.removeRenderer(v); t.addRenderer(v) }
        remoteTrack = t
        listener.onRemoteVideo(true)
    }

    private fun onEvent(r: Room, e: RoomEvent) {
        if (r !== room) return
        when (e) {
            is RoomEvent.TrackSubscribed -> (e.track as? VideoTrack)?.let { showRemote(it) }
            is RoomEvent.TrackUnsubscribed -> if (e.track === remoteTrack) {
                remoteView?.let { remoteTrack?.removeRenderer(it) }
                remoteTrack = null
                listener.onRemoteVideo(false)
                // another publisher (e.g. a co-host) may still be on stage
                r.remoteParticipants.values.firstNotNullOfOrNull { p -> p.videoTrackPublications.firstNotNullOfOrNull { it.second as? VideoTrack } }?.let { showRemote(it) }
            }
            is RoomEvent.Reconnecting -> listener.onConnection("reconnecting")
            is RoomEvent.Reconnected -> listener.onConnection("connected")
            is RoomEvent.Disconnected -> onDisconnected(e.reason)
            else -> {}
        }
    }

    /** Lost for longer than LiveKit's own resume: get a fresh token for my role and rejoin (same as the web SDK). */
    private fun onDisconnected(reason: DisconnectReason?) {
        val id = roomId
        disconnect()
        if (leaving || id == null) return
        if (reason == DisconnectReason.CLIENT_INITIATED || reason == DisconnectReason.ROOM_DELETED || reason == DisconnectReason.DUPLICATE_IDENTITY ||
            reason == DisconnectReason.PARTICIPANT_REMOVED || reason == DisconnectReason.ROOM_CLOSED) {
            listener.onConnection("ended"); return
        }
        listener.onConnection("reconnecting")
        scope.launch {
            for (i in 0 until 40) {
                delay(minOf(10_000L, 1000L shl minOf(i, 3)))
                if (leaving || roomId != id) return@launch
                try {
                    val j = withContext(Dispatchers.IO) { api.token(id) }
                    connect(j)
                    return@launch
                } catch (e: LiveException) {
                    if (e.status == 403 || e.status == 404 || e.status == 409) { listener.onConnection("ended"); return@launch }
                } catch (e: Exception) { disconnect() }
            }
            listener.onConnection("failed")
        }
    }

    private fun disconnect() {
        events?.cancel(); events = null
        localView?.let { localTrack?.removeRenderer(it) }
        remoteView?.let { remoteTrack?.removeRenderer(it) }
        localTrack = null; remoteTrack = null
        // free the filter's GL objects on the camera thread while LiveKit's EGL context is still alive
        filterProcessor?.let { fx -> filterProcessor = null; runCatching { fx.release() } }
        val r = room ?: return
        room = null
        runCatching { r.disconnect() }
        runCatching { r.release() }
    }

    /** A renderer belongs to one Room's EGL context: (re)initialise it for [r]. */
    private fun initView(r: Room, v: SurfaceViewRenderer) {
        runCatching { v.release() }
        r.initVideoRenderer(v)
    }
}
