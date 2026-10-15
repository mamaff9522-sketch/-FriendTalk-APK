package com.example.rtc

import android.content.Context
import android.media.RingtoneManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** 1:1 voice/video calls: ringing via RTDB callInbox/{uid}, call state via calls/{id}, media via Agora. */
object CallManager {
    data class Incoming(val callId: String, val fromUid: String, val fromName: String, val video: Boolean, val chatId: String)
    data class Active(val callId: String, val peerUid: String, val name: String, val video: Boolean, val outgoing: Boolean)

    var incoming by mutableStateOf<Incoming?>(null)
    var active by mutableStateOf<Active?>(null)
    var connectedAt by mutableLongStateOf(0L)
    var note by mutableStateOf("")
    private var inboxRef: DatabaseReference? = null
    private var inboxL: ValueEventListener? = null
    private var callRef: DatabaseReference? = null
    private var callL: ValueEventListener? = null
    private var listeningUid: String? = null

    fun listen(uid: String?) {
        if (uid == listeningUid) return
        inboxL?.let { inboxRef?.removeEventListener(it) }; inboxRef = null; inboxL = null; incoming = null
        listeningUid = uid
        if (uid == null) return
        val ref = RtcApi.db.getReference("callInbox/$uid")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val id = s.child("callId").getValue(String::class.java)
                val at = s.child("at").getValue(Long::class.java) ?: 0L
                incoming = if (id == null || active?.callId == id || System.currentTimeMillis() - at > 60_000) null
                else Incoming(id, s.child("fromUid").getValue(String::class.java).orEmpty(), s.child("fromName").getValue(String::class.java) ?: "เพื่อน",
                    s.child("video").getValue(Boolean::class.java) == true, s.child("chatId").getValue(String::class.java).orEmpty())
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(l); inboxRef = ref; inboxL = l
    }

    private fun watch(id: String) {
        unwatch()
        val ref = RtcApi.db.getReference("calls/$id/status")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                when (s.getValue(String::class.java)) {
                    "accepted" -> if (connectedAt == 0L) connectedAt = System.currentTimeMillis()
                    "declined" -> finish("ปลายสายไม่รับสาย")
                    "missed" -> finish(if (active?.outgoing == true) "ไม่มีผู้รับสาย" else "")
                    "ended" -> finish("วางสายแล้ว")
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(l); callRef = ref; callL = l
    }
    private fun unwatch() { callL?.let { callRef?.removeEventListener(it) }; callRef = null; callL = null }

    /** Returns an error message, or null when the call is ringing. */
    suspend fun start(ctx: Context, chatId: String, video: Boolean, peerUid: String, name: String): String? {
        if (active != null) return "กำลังอยู่ในสายอื่น"
        val r = RtcApi.post("/calls", JSONObject().put("chatId", chatId).put("video", video))
        if (r?.code != 200 || r.json == null) return RtcApi.error(r)
        val s = RtcApi.session(r.json)
        connectedAt = 0L; note = ""
        active = Active(s.id, peerUid, name, video, outgoing = true)
        if (!Rtc.join(ctx, s, live = false, publisher = true, video = video)) { hangup(); return Rtc.error ?: "เชื่อมต่อไม่สำเร็จ" }
        watch(s.id)
        return null
    }

    suspend fun answer(ctx: Context, accept: Boolean): String? {
        val inc = incoming ?: return null
        incoming = null
        val r = RtcApi.post("/calls/${inc.callId}/answer", JSONObject().put("accept", accept))
        if (!accept) return null
        if (r?.code == 409) return "สายนี้จบไปแล้ว"
        if (r?.code != 200 || r.json == null) return RtcApi.error(r)
        val s = RtcApi.session(r.json)
        connectedAt = System.currentTimeMillis(); note = ""
        active = Active(s.id, inc.fromUid, inc.fromName, inc.video, outgoing = false)
        if (!Rtc.join(ctx, s, live = false, publisher = true, video = inc.video)) { hangup(); return Rtc.error ?: "เชื่อมต่อไม่สำเร็จ" }
        watch(s.id)
        return null
    }

    fun hangup() {
        val a = active ?: return
        finish("")
        RtcScope.launch { RtcApi.post("/calls/${a.callId}/end") }
    }

    private fun finish(msg: String) {
        if (active == null) return
        unwatch(); Rtc.leave()
        active = null; connectedAt = 0L; note = msg
    }
}

/** Open live room for viewers (set from Home / Live tab), and the host's go-live screen. */
object LiveNav {
    var watch by mutableStateOf<String?>(null)
    var host by mutableStateOf(false)
}

/** Put once at the app root: incoming-call screen, in-call screen, live viewer and live host. */
@Composable
fun RtcHost() {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    LaunchedEffect(uid) { CallManager.listen(uid) }
    val inc = CallManager.incoming
    val act = CallManager.active
    when {
        act != null -> CallScreen(act)
        inc != null -> IncomingCallScreen(inc)
    }
    if (act == null) {
        LiveNav.watch?.let { id -> LiveViewerScreen(id) { LiveNav.watch = null } }
        if (LiveNav.host) LiveHostScreen { LiveNav.host = false }
    }
    val ctx = LocalContext.current
    val note = CallManager.note
    LaunchedEffect(note) { if (note.isNotBlank()) { android.widget.Toast.makeText(ctx, note, android.widget.Toast.LENGTH_SHORT).show(); CallManager.note = "" } }
}

private val CallBg = Brush.verticalGradient(listOf(Color(0xFF1A0B3D), Color(0xFF3B1466), Color(0xFF12082B)))

@Composable
private fun IncomingCallScreen(inc: CallManager.Incoming) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var err by remember { mutableStateOf("") }
    val askPerm = rememberRtcPermission { err = "ต้องอนุญาตไมโครโฟน" + (if (inc.video) "และกล้อง" else "") + "ก่อนรับสาย" }
    val avatar by produceState("", inc.fromUid) { value = runCatching { com.example.ui.feed.HomeRepo.user(inc.fromUid).avatar }.getOrDefault("") }
    DisposableEffect(inc.callId) {
        val tone = runCatching { RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)) }.getOrNull()
        runCatching { tone?.play() }
        onDispose { runCatching { tone?.stop() } }
    }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(CallBg).systemBarsPadding()) {
            Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PulseAvatar(avatar, inc.fromName, 120.dp)
                Spacer(Modifier.height(20.dp))
                Text(inc.fromName, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(if (inc.video) "วิดีโอคอลเข้า…" else "สายเข้า…", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                if (err.isNotBlank()) Text(err, color = Color(0xFFFFC94D), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
            Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 70.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundAction(Icons.Filled.CallEnd, "ปฏิเสธ", Color(0xFFFF3B5C), 70.dp) { RtcScope.launch { CallManager.answer(ctx, false) } }
                RoundAction(if (inc.video) Icons.Filled.Videocam else Icons.Filled.Call, "รับสาย", Color(0xFF22C55E), 70.dp) {
                    askPerm(inc.video) { RtcScope.launch { CallManager.answer(ctx, true)?.let { CallManager.note = it } } }
                }
            }
        }
    }
}

@Composable
private fun CallScreen(a: CallManager.Active) {
    val scope = rememberCoroutineScope()
    val avatar by produceState("", a.peerUid) { value = runCatching { com.example.ui.feed.HomeRepo.user(a.peerUid).avatar }.getOrDefault("") }
    val remote = Rtc.remoteUids.firstOrNull()
    LaunchedEffect(remote) { if (remote != null && CallManager.connectedAt == 0L) CallManager.connectedAt = System.currentTimeMillis() }
    // ringing timeout for the caller
    LaunchedEffect(a.callId) { delay(45_000); if (CallManager.connectedAt == 0L && CallManager.active?.callId == a.callId) CallManager.hangup() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val since = CallManager.connectedAt
    val status = when {
        Rtc.error != null -> Rtc.error!!
        since == 0L -> if (a.outgoing) "กำลังโทร…" else "กำลังเชื่อมต่อ…"
        remote == null -> "กำลังเชื่อมต่อ…"
        else -> ((now - since) / 1000).let { "%02d:%02d".format(it / 60, it % 60) }
    }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        val ctx = LocalContext.current
        Box(Modifier.fillMaxSize().background(CallBg)) {
            if (a.video && remote != null) key(remote) { AndroidView(factory = { Rtc.remoteView(it, remote) }, modifier = Modifier.fillMaxSize()) }
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                if (!a.video || remote == null) {
                    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PulseAvatar(avatar, a.name, 120.dp)
                        Spacer(Modifier.height(20.dp))
                        Text(a.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(status, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x66000000)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(a.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(status, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().padding(bottom = 36.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    RoundAction(if (Rtc.micOn) Icons.Filled.Mic else Icons.Filled.MicOff, if (Rtc.micOn) "ปิดไมค์" else "เปิดไมค์", if (Rtc.micOn) Color(0x33FFFFFF) else Color.White, 56.dp, dark = !Rtc.micOn) { Rtc.toggleMic() }
                    if (a.video) RoundAction(if (Rtc.camOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, "กล้อง", if (Rtc.camOn) Color(0x33FFFFFF) else Color.White, 56.dp, dark = !Rtc.camOn) { Rtc.toggleCam() }
                    else RoundAction(Icons.Filled.VolumeUp, "ลำโพง", if (Rtc.speakerOn) Color.White else Color(0x33FFFFFF), 56.dp, dark = Rtc.speakerOn) { Rtc.toggleSpeaker() }
                    if (a.video) RoundAction(Icons.Filled.Cameraswitch, "สลับกล้อง", Color(0x33FFFFFF), 56.dp) { Rtc.switchCamera() }
                    RoundAction(Icons.Filled.CallEnd, "วางสาย", Color(0xFFFF3B5C), 64.dp) { CallManager.hangup() }
                }
            }
            if (a.video && Rtc.camOn) Box(Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(top = 70.dp, end = 14.dp).size(width = 110.dp, height = 160.dp)
                .clip(RoundedCornerShape(16.dp)).border(2.dp, Color(0x88FFFFFF), RoundedCornerShape(16.dp))) {
                AndroidView(factory = { Rtc.localView(ctx, overlay = true) }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
internal fun PulseAvatar(url: String, name: String, size: Dp) {
    Box(Modifier.size(size + 24.dp).clip(CircleShape).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).clip(CircleShape).border(3.dp, Brush.linearGradient(listOf(Color(0xFFFF2FA8), Color(0xFF9B3DFF))), CircleShape), contentAlignment = Alignment.Center) {
            com.example.ui.social.Avatar(url, name, (size.value - 8).toInt())
        }
    }
}

@Composable
internal fun RoundAction(icon: ImageVector, label: String, bg: Color, size: Dp, dark: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (dark) Color(0xFF1A0B3D) else Color.White, modifier = Modifier.size(size * 0.45f))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
    }
}
