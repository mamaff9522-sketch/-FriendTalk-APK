package com.example.rtc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private val Neon = Color(0xFFFF2FA8)
private val Violet = Color(0xFF9B3DFF)
private val LiveRed = Color(0xFFFF2D55)
val LIVE_CATEGORIES = listOf("talk" to "💬 พูดคุย", "music" to "🎵 ดนตรี", "sing" to "🎤 ร้องเพลง", "game" to "🎮 เกม", "friend" to "👥 มิตรภาพ")

data class LiveRoom(val id: String, val hostUid: String, val name: String, val avatar: String, val title: String, val category: String, val viewers: Int, val startedAt: Long)

/** Live rooms that are on air (status live + host heartbeat within 90 s), most viewers first. */
@Composable
fun rememberLiveRooms(): List<LiveRoom>? {
    var snap by remember { mutableStateOf<DataSnapshot?>(null) }
    DisposableEffect(Unit) {
        val q = RtcApi.db.getReference("lives").orderByChild("status").equalTo("live")
        val l = object : com.google.firebase.database.ValueEventListener { override fun onDataChange(d: DataSnapshot) { snap = d }; override fun onCancelled(e: DatabaseError) { snap = null } }
        q.addValueEventListener(l)
        onDispose { q.removeEventListener(l) }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val s = snap ?: return null
    return s.children.mapNotNull { l ->
        if (l.child("status").getValue(String::class.java) != "live") return@mapNotNull null
        val ping = (l.child("lastPing").value as? Number)?.toLong() ?: 0L
        if (now - ping > 90_000) return@mapNotNull null
        LiveRoom(l.key ?: return@mapNotNull null, l.child("hostUid").getValue(String::class.java).orEmpty(), l.child("name").getValue(String::class.java) ?: "ผู้ใช้",
            l.child("avatar").getValue(String::class.java).orEmpty(), l.child("title").getValue(String::class.java).orEmpty(), l.child("category").getValue(String::class.java) ?: "talk",
            ((l.child("viewers").value as? Number)?.toInt()) ?: 0, (l.child("startedAt").value as? Number)?.toLong() ?: 0L)
    }.sortedByDescending { it.viewers }
}

@Composable
private fun rememberLiveChat(liveId: String): List<Triple<String, String, String>> {
    val msgs = remember(liveId) { mutableStateListOf<Triple<String, String, String>>() }
    DisposableEffect(liveId) {
        val q = RtcApi.db.getReference("liveChat/$liveId").limitToLast(50)
        val l = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, p: String?) {
                msgs.add(Triple(s.key ?: "", s.child("name").getValue(String::class.java) ?: "", s.child("text").getValue(String::class.java).orEmpty()))
                if (msgs.size > 80) msgs.removeAt(0)
            }
            override fun onChildChanged(s: DataSnapshot, p: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            override fun onCancelled(e: DatabaseError) {}
        }
        q.addChildEventListener(l)
        onDispose { q.removeEventListener(l) }
    }
    return msgs
}

/** Chat overlay (bottom-left) + input row used by both host and viewers. */
@Composable
private fun LiveChatOverlay(liveId: String, modifier: Modifier, extra: @Composable RowScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val msgs = rememberLiveChat(liveId)
    val list = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1) }
    Column(modifier) {
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth(0.78f).heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(msgs, key = { it.first }) { (_, name, t) ->
                Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x66000000)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("$name  ", color = Color(0xFFFFC94D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(t, color = Color.White, fontSize = 12.sp)
                }
            }
        }
        if (err.isNotBlank()) Text(err, color = Color(0xFFFFC94D), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Color(0x55000000)).border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(22.dp)).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    if (text.isEmpty()) Text("พูดอะไรสักหน่อย…", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                    BasicTextField(text, { text = it.take(200) }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 14.sp), cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth())
                }
                Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Neon, Violet))).clickable {
                    val t = text.trim(); if (t.isEmpty()) return@clickable
                    text = ""
                    scope.launch { val r = RtcApi.post("/lives/$liveId/chat", JSONObject().put("text", t)); err = if (r?.code == 200) "" else RtcApi.error(r) }
                }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Send, "ส่ง", tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            extra()
        }
    }
}

@Composable
private fun LivePill(viewers: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(LiveRed).padding(horizontal = 8.dp, vertical = 3.dp))
        Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x66000000)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp))
            Text("$viewers", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SmallRound(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, active: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(if (active) Color.White else Color(0x55000000)).border(1.dp, Color(0x44FFFFFF), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, desc, tint = if (active) Color(0xFF1A0B3D) else Color.White, modifier = Modifier.size(20.dp)) }
}

/** Host: set a title + category, go live with the front camera, see viewers and chat, end the live. */
@Composable
fun LiveHostScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("talk") }
    var liveId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var viewers by remember { mutableIntStateOf(0) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var confirmEnd by remember { mutableStateOf(false) }
    val askPerm = rememberRtcPermission { err = "ต้องอนุญาตกล้องและไมโครโฟนก่อนไลฟ์" }
    fun stop() { val id = liveId; liveId = null; Rtc.leave(); if (id != null) RtcScope.launch { RtcApi.post("/lives/$id/end") } }
    fun end() { stop(); onClose() }
    // heartbeat so the room stays listed; also refreshes the viewer count
    LaunchedEffect(liveId) {
        val id = liveId ?: return@LaunchedEffect
        while (liveId == id) {
            val r = RtcApi.post("/lives/$id/ping")
            if (r?.code == 200) viewers = r.json?.optInt("viewers") ?: viewers
            else if (r?.code == 409) { err = "ไลฟ์ถูกปิดแล้ว"; Rtc.leave(); liveId = null }
            delay(30_000)
        }
    }
    DisposableEffect(Unit) { onDispose { if (liveId != null) stop() } }
    Dialog(onDismissRequest = { if (liveId != null) confirmEnd = true else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF12082B), Color(0xFF2A0F55))))) {
            val id = liveId
            if (id != null) {
                if (Rtc.camOn) AndroidView(factory = { Rtc.localView(it, overlay = false) }, modifier = Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ปิดกล้องอยู่", color = Color.White.copy(alpha = 0.7f)) }
                Box(Modifier.fillMaxWidth().height(160.dp).background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))))
                Box(Modifier.fillMaxWidth().height(320.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))))
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
                Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LivePill(viewers)
                        Spacer(Modifier.width(8.dp))
                        Text(((now - startedAt) / 1000).let { "%02d:%02d".format(it / 60, it % 60) }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("จบไลฟ์", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(LiveRed).clickable { confirmEnd = true }.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                    if (err.isNotBlank() || Rtc.error != null) Text(Rtc.error ?: err, color = Color(0xFFFFC94D), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.weight(1f))
                    LiveChatOverlay(id, Modifier.fillMaxWidth()) {
                        SmallRound(if (Rtc.micOn) Icons.Filled.Mic else Icons.Filled.MicOff, "ไมค์", active = !Rtc.micOn) { Rtc.toggleMic() }
                        SmallRound(Icons.Filled.Cameraswitch, "สลับกล้อง") { Rtc.switchCamera() }
                    }
                }
                if (confirmEnd) androidx.compose.material3.AlertDialog(onDismissRequest = { confirmEnd = false },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmEnd = false; end() }) { Text("จบไลฟ์") } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmEnd = false }) { Text("ไลฟ์ต่อ") } },
                    title = { Text("จบไลฟ์ตอนนี้?") }, text = { Text("ผู้ชมจะออกจากห้องทั้งหมด") })
            } else {
                // pre-live setup
                Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "ปิด", tint = Color.White) }
                        Spacer(Modifier.width(12.dp))
                        Text("เริ่มไลฟ์สด", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(28.dp))
                    Text("หัวข้อไลฟ์", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x22FFFFFF)).border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(16.dp)).padding(14.dp)) {
                        if (title.isEmpty()) Text("เช่น มาคุยกันก่อนนอน 🌙", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                        BasicTextField(title, { title = it.take(60) }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("หมวดหมู่", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LIVE_CATEGORIES.forEach { (k, l) ->
                            val on = cat == k
                            Text(l, color = Color.White, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) Brush.horizontalGradient(listOf(Neon, Violet)) else Brush.horizontalGradient(listOf(Color(0x22FFFFFF), Color(0x22FFFFFF))))
                                    .clickable { cat = k }.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("• ไลฟ์ใช้กล้องหน้าและไมโครโฟน\n• ห้ามเนื้อหาโป๊ เปลือย ความรุนแรง หรือผิดกฎหมาย ผู้ดูแลปิดไลฟ์ได้ทันที\n• ผู้ชมส่งข้อความหาคุณได้ระหว่างไลฟ์",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 19.sp)
                    if (err.isNotBlank()) Text(err, color = Color(0xFFFFC94D), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp)).background(Brush.horizontalGradient(listOf(Neon, Violet)))
                        .clickable(enabled = !busy) {
                            askPerm(true) {
                                busy = true; err = ""
                                scope.launch {
                                    val r = RtcApi.post("/lives", JSONObject().put("title", title.trim()).put("category", cat))
                                    if (r?.code == 200 && r.json != null) {
                                        val s = RtcApi.session(r.json)
                                        if (Rtc.join(ctx, s, live = true, publisher = true, video = true)) { liveId = s.id; startedAt = System.currentTimeMillis() }
                                        else { err = Rtc.error ?: "เริ่มไลฟ์ไม่สำเร็จ"; RtcApi.post("/lives/${s.id}/end") }
                                    } else err = RtcApi.error(r)
                                    busy = false
                                }
                            }
                        }, contentAlignment = Alignment.Center) {
                        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("🔴  เริ่มไลฟ์เลย", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Viewer: host video full screen, live info, chat. */
@Composable
fun LiveViewerScreen(liveId: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<RtcSession?>(null) }
    var err by remember { mutableStateOf("") }
    val info = rememberDbValue("lives/$liveId")
    val status = info?.child("status")?.getValue(String::class.java)
    val name = info?.child("name")?.getValue(String::class.java) ?: ""
    val avatar = info?.child("avatar")?.getValue(String::class.java).orEmpty()
    val title = info?.child("title")?.getValue(String::class.java).orEmpty()
    val viewers = (info?.child("viewers")?.value as? Number)?.toInt() ?: 0
    LaunchedEffect(liveId) {
        val r = RtcApi.post("/lives/$liveId/join")
        if (r?.code == 200 && r.json != null) {
            val s = RtcApi.session(r.json)
            if (Rtc.join(ctx, s, live = true, publisher = false, video = true)) session = s else err = Rtc.error ?: "เข้าดูไลฟ์ไม่สำเร็จ"
        } else err = RtcApi.error(r)
    }
    DisposableEffect(liveId) { onDispose { Rtc.leave(); RtcScope.launch { RtcApi.post("/lives/$liveId/leave") } } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0824))) {
            val s = session
            val hostOn = s != null && s.peerUid in Rtc.remoteUids
            if (hostOn) key(s!!.peerUid) { AndroidView(factory = { Rtc.remoteView(it, s.peerUid) }, modifier = Modifier.fillMaxSize()) }
            else {
                if (avatar.isNotBlank()) AsyncImage(model = avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.35f)
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        status == "ended" -> { Text("ไลฟ์นี้จบแล้ว", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("ขอบคุณที่มาร่วมดู 💖", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) }
                        err.isNotBlank() -> Text(err, color = Color(0xFFFFC94D), fontSize = 14.sp)
                        else -> { CircularProgressIndicator(color = Color.White); Spacer(Modifier.height(10.dp)); Text("กำลังเชื่อมต่อไลฟ์…", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp) }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(160.dp).background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))))
            Box(Modifier.fillMaxWidth().height(320.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))))
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0x66000000)).padding(4.dp).padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.example.ui.social.Avatar(avatar, name.ifBlank { "?" }, 34)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (title.isNotBlank()) Text(title, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    LivePill(viewers)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, "ออก", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
                Spacer(Modifier.weight(1f))
                if (status != "ended") LiveChatOverlay(liveId, Modifier.fillMaxWidth()) {
                    SmallRound(if (Rtc.speakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, "เสียง", active = !Rtc.speakerOn) { Rtc.toggleSpeaker() }
                }
            }
        }
    }
}

/** Card grid of on-air rooms for the Live tab. */
@Composable
fun LiveRoomCard(r: LiveRoom, modifier: Modifier) {
    Box(modifier.aspectRatio(0.78f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF16123A)).clickable { LiveNav.watch = r.id }) {
        if (r.avatar.isNotBlank()) AsyncImage(model = r.avatar, contentDescription = r.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color.Transparent, Color(0xCC000000)))))
        Row(Modifier.padding(8.dp)) { LivePill(r.viewers) }
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(r.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.title.ifBlank { LIVE_CATEGORIES.firstOrNull { it.first == r.category }?.second ?: "" }, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
