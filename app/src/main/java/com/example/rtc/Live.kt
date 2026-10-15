package com.example.rtc

import com.example.ui.components.dialogNavPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

data class LiveMsg(val id: String, val type: String, val uid: String, val name: String, val avatar: String, val text: String,
                   val giftId: String = "", val giftName: String = "", val icon: String = "", val count: Int = 1, val at: Long = 0,
                   val imageUrl: String = "", val price: Int = 0, val fx: String = "")

@Composable
private fun rememberLiveChat(liveId: String): List<LiveMsg> {
    val msgs = remember(liveId) { mutableStateListOf<LiveMsg>() }
    DisposableEffect(liveId) {
        val q = RtcApi.db.getReference("liveChat/$liveId").limitToLast(50)
        val l = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, p: String?) {
                fun str(k: String) = s.child(k).getValue(String::class.java).orEmpty()
                msgs.add(LiveMsg(s.key ?: "", str("type").ifBlank { "chat" }, str("uid"), str("name"), str("avatar"), str("text"), str("giftId"), str("giftName"), str("icon"),
                    (s.child("count").value as? Number)?.toInt() ?: 1, (s.child("at").value as? Number)?.toLong() ?: 0L,
                    str("imageUrl"), (s.child("price").value as? Number)?.toInt() ?: 0, str("fx")))
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

private val QUICK_EMOJI = listOf("❤️", "😍", "😂", "👏", "🔥", "🥰", "😮", "🙏", "💯", "🎉", "😘", "👍")

/** Newest gift slides in over the video for 4 s: avatar, name, gift, xN. */
@Composable
private fun GiftBanner(msgs: List<LiveMsg>, modifier: Modifier) {
    val gifts by remember { com.example.social.GiftRepo.catalog("live") }.collectAsState(initial = emptyList())
    val lastGift = msgs.lastOrNull { it.type == "gift" }
    var bannerFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lastGift?.id) { if (lastGift != null && System.currentTimeMillis() - lastGift.at < 15_000) { bannerFor = lastGift.id; delay(4000); if (bannerFor == lastGift.id) bannerFor = null } }
    androidx.compose.animation.AnimatedVisibility(visible = bannerFor != null && lastGift != null, modifier = modifier,
        enter = androidx.compose.animation.slideInHorizontally { -it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut()) {
        val g = lastGift ?: return@AnimatedVisibility
        val item = gifts.firstOrNull { it.id == g.giftId }
        Row(Modifier.clip(RoundedCornerShape(28.dp)).background(Brush.horizontalGradient(listOf(Color(0xE6FF2FA8), Color(0x999B3DFF))))
            .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            com.example.ui.social.Avatar(g.avatar, g.name.ifBlank { "?" }, 36)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.widthIn(max = 130.dp)) {
                Text(g.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("ส่ง ${g.giftName}", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (item != null) com.example.ui.social.StickerImage(item) else Text(g.icon.ifBlank { "🎁" }, fontSize = 30.sp)
            }
            Text("x${g.count}", color = Color(0xFFFFE08A), fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** Bottom half of the live room: comments (avatars, joins, gifts) + emoji + input + caller-supplied buttons. */
@Composable
private fun CommentsPanel(liveId: String, msgs: List<LiveMsg>, modifier: Modifier, extra: @Composable RowScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf(false) }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1) }
    fun send() {
        val t = text.trim(); if (t.isEmpty()) return
        text = ""; emoji = false
        scope.launch { val r = RtcApi.post("/lives/$liveId/chat", JSONObject().put("text", t)); err = if (r?.code == 200) "" else RtcApi.error(r) }
    }
    // floats over the full-screen video: transparent background, comments in dark pills, newest at the bottom
    Column(modifier) {
        if (msgs.isNotEmpty()) LazyColumn(state = list, modifier = Modifier.fillMaxWidth(0.82f).heightIn(max = 240.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(msgs, key = { it.id }) { m ->
                when (m.type) {
                    "join" -> Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x40000000)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("👋  ", fontSize = 12.sp)
                        Text(m.name, color = Color(0xFF9FD8FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(" เข้าร่วมแล้ว", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    else -> Row(Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0x59000000)).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 5.dp), verticalAlignment = Alignment.Top) {
                        com.example.ui.social.Avatar(m.avatar, m.name.ifBlank { "?" }, 28)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(m.name, color = Color(0xFFFFD98A), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            if (m.type == "gift") Row(Modifier.padding(top = 2.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x33FFC94D)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text("🎁 ส่ง ${m.giftName} ${m.icon} x${m.count}", color = Color(0xFFFFE08A), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            else Text(m.text, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        if (err.isNotBlank()) Text(err, color = Color(0xFFFFC94D), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
        if (emoji) Row(Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x99000000)).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            QUICK_EMOJI.forEach { e -> Text(e, fontSize = 24.sp, modifier = Modifier.clip(CircleShape).clickable { text = (text + e).take(200) }.padding(5.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Color(0x66000000)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(22.dp)).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    if (text.isEmpty()) Text("แสดงความคิดเห็น…", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                    BasicTextField(text, { text = it.take(200) }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 14.sp), cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { send() }))
                }
                Box(Modifier.size(34.dp).clip(CircleShape).clickable { emoji = !emoji }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.EmojiEmotions, "อีโมจิ", tint = if (emoji) Color(0xFFFFC94D) else Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp)) }
                if (text.isNotBlank()) Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Neon, Violet))).clickable { send() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Send, "ส่ง", tint = Color.White, modifier = Modifier.size(17.dp)) }
            }
            extra()
        }
    }
}

/** Last 3 viewers' faces + count (TikTok-style, top right). */
@Composable
private fun ViewerFaces(liveId: String, viewers: Int) {
    val snap = rememberDbValue("liveViewers/$liveId")
    val uids = snap?.children?.sortedByDescending { (it.value as? Number)?.toLong() ?: 0L }?.mapNotNull { it.key }?.take(3).orEmpty()
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x66000000)).padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            uids.forEachIndexed { i, u ->
                val av by produceState("", u) { value = runCatching { com.example.ui.feed.HomeRepo.user(u).avatar }.getOrDefault("") }
                Box(Modifier.padding(start = (i * 18).dp).size(28.dp).clip(CircleShape).border(1.5.dp, Color.White, CircleShape)) { com.example.ui.social.Avatar(av, "?", 28) }
            }
        }
        if (uids.isNotEmpty()) Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(3.dp))
        Text("$viewers", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FollowButton(hostUid: String) {
    val scope = rememberCoroutineScope()
    val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    var following by remember(hostUid) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(hostUid) {
        if (me == null || hostUid.isBlank() || hostUid == me.uid) return@LaunchedEffect
        following = runCatching { com.example.network.BrainApi.call(me, "GET", "/follow/$hostUid").json?.optBoolean("following") }.getOrNull() ?: false
    }
    if (following != false) return
    Text("+ ติดตาม", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(16.dp)).background(LiveRed).clickable {
            following = true
            scope.launch { val r = RtcApi.post("/follow/$hostUid", JSONObject().put("follow", true)); if (r?.code != 200) following = false }
        }.padding(horizontal = 12.dp, vertical = 6.dp))
}

private fun shareLive(ctx: android.content.Context, host: String, title: String) {
    val text = "มาดูไลฟ์ของ $host ใน FriendTalk กัน! ${if (title.isNotBlank()) "\"$title\" " else ""}\nโหลดแอป: " + if (com.example.BuildConfig.PLAY_STORE) "https://play.google.com/store/apps/details?id=com.friendtalk.app" else "https://github.com/mamaff9522-sketch/-FriendTalk-APK/releases/download/friendtalk-test/FriendTalk.apk"
    val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
    runCatching { ctx.startActivity(android.content.Intent.createChooser(i, "แชร์ไลฟ์").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Live gift picker: real catalog/live items, paid with diamonds right away (host gets the recipient share). */
@Composable
private fun LiveGiftSheet(liveId: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val items by remember { com.example.social.GiftRepo.catalog("live") }.collectAsState(initial = null)
    var wallet by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { wallet = com.example.social.GiftRepo.wallet() }
    var sel by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(items) { if (sel == null) sel = items?.let { l -> (l.firstOrNull { it.id == "gift_flower" } ?: l.minByOrNull { it.price })?.id } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss)) {
            // sheet never taller than 72% of the screen: gift grid scrolls, the send button is always visible at the bottom
            val maxH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.72f).dp
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxH).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color(0xFF15102F))
                .clickable(enabled = false) {}.dialogNavPadding().navigationBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ส่งของขวัญ", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("💎 ${wallet?.third ?: "…"}", color = Color(0xFF7AF5FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Text("หักเพชรทันทีเมื่อส่ง สตรีมเมอร์ได้รับส่วนแบ่งเป็นเพชร", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                val l = items
                when {
                    l == null -> CircularProgressIndicator(color = Neon)
                    l.isEmpty() -> Text("ยังไม่มีของขวัญไลฟ์ (แอดมินเพิ่มได้)", color = Color.White.copy(alpha = 0.6f))
                    // many gifts → scroll inside the sheet (send button stays visible below)
                    else -> androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                        Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(l.size) { i -> val g = l[i]
                            val on = sel == g.id
                            Column(Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Color(0x33FF2FA8) else Color(0x14FFFFFF))
                                .border(1.5.dp, if (on) Neon else Color.Transparent, RoundedCornerShape(14.dp)).clickable { sel = g.id }.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(44.dp)) { com.example.ui.social.StickerImage(g) }
                                Text(g.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("💎 ${g.price}", color = Color(0xFF7AF5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (msg.isNotBlank()) Text(msg, color = Color(0xFFFFC94D), fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(48.dp).clip(RoundedCornerShape(24.dp))
                    .background(if (sel != null) Brush.horizontalGradient(listOf(Neon, Violet)) else Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x33FFFFFF))))
                    .clickable(enabled = sel != null && !busy) {
                        val id = sel ?: return@clickable
                        busy = true; msg = ""
                        scope.launch {
                            val r = RtcApi.post("/lives/$liveId/gift", JSONObject().put("giftId", id).put("count", 1))
                            busy = false
                            if (r?.code == 200) { tick++; onDismiss() } else msg = RtcApi.error(r) + if (r?.code == 402) " — เติมเพชรได้ที่หน้า 'ฉัน' → เหรียญ & เพชร" else ""
                        }
                    }, contentAlignment = Alignment.Center) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(items?.firstOrNull { it.id == sel }?.let { "ส่ง ${it.name}  💎${it.price}" } ?: "เลือกของขวัญ", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
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
    val myName by produceState("") { value = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { runCatching { com.example.ui.feed.HomeRepo.user(it).name }.getOrNull() }.orEmpty() }
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("talk") }
    var liveId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var viewers by remember { mutableIntStateOf(0) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var confirmEnd by remember { mutableStateOf(false) }
    var beautySheet by remember { mutableStateOf(false) }
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
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
                val msgs = rememberLiveChat(id)
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (Rtc.camOn) AndroidView(factory = { Rtc.localView(it, overlay = false) }, modifier = Modifier.fillMaxSize())
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ปิดกล้องอยู่", color = Color.White.copy(alpha = 0.7f)) }
                    Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))))
                    Box(Modifier.fillMaxWidth().fillMaxHeight(0.45f).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))))
                    Column(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding().imePadding()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(LiveRed).padding(horizontal = 8.dp, vertical = 3.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(((now - startedAt) / 1000).let { "%02d:%02d".format(it / 60, it % 60) }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            ViewerFaces(id, viewers)
                            Spacer(Modifier.width(8.dp))
                            Text("จบไลฟ์", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(LiveRed).clickable { confirmEnd = true }.padding(horizontal = 14.dp, vertical = 7.dp))
                        }
                        if (err.isNotBlank() || Rtc.error != null) Text(Rtc.error ?: err, color = Color(0xFFFFC94D), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp))
                        Spacer(Modifier.weight(1f))
                        GiftBanner(msgs, Modifier.padding(start = 12.dp, bottom = 8.dp))
                        CommentsPanel(id, msgs, Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                            SmallRound(Icons.Filled.AutoAwesome, "แต่งหน้า", active = Beauty.on) { beautySheet = true }
                            SmallRound(if (Rtc.micOn) Icons.Filled.Mic else Icons.Filled.MicOff, "ไมค์", active = !Rtc.micOn) { Rtc.toggleMic() }
                            SmallRound(Icons.Filled.Cameraswitch, "สลับกล้อง") { Rtc.switchCamera() }
                            SmallRound(Icons.Filled.Share, "แชร์") { shareLive(ctx, myName, title) }
                        }
                    }
                    GiftBurstLayer(msgs) // big gift effect in the middle of the screen
                }
                if (beautySheet) BeautySheet { beautySheet = false }
                if (confirmEnd) androidx.compose.material3.AlertDialog(onDismissRequest = { confirmEnd = false },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmEnd = false; end() }) { Text("จบไลฟ์") } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmEnd = false }) { Text("ไลฟ์ต่อ") } },
                    title = { Text("จบไลฟ์ตอนนี้?") }, text = { Text("ผู้ชมจะออกจากห้องทั้งหมด") })
            } else {
                // pre-live setup
                Column(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding().imePadding().padding(20.dp)) {
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
    val hostUid = info?.child("hostUid")?.getValue(String::class.java).orEmpty()
    var giftSheet by remember { mutableStateOf(false) }
    LaunchedEffect(liveId) {
        val r = RtcApi.post("/lives/$liveId/join")
        if (r?.code == 200 && r.json != null) {
            val s = RtcApi.session(r.json)
            if (Rtc.join(ctx, s, live = true, publisher = false, video = true)) session = s else err = Rtc.error ?: "เข้าดูไลฟ์ไม่สำเร็จ"
        } else err = RtcApi.error(r)
    }
    DisposableEffect(liveId) { onDispose { Rtc.leave(); RtcScope.launch { RtcApi.post("/lives/$liveId/leave") } } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val msgs = rememberLiveChat(liveId)
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0824))) {
            val s = session
            val hostOn = s != null && s.peerUid in Rtc.remoteUids
            if (hostOn) key(s!!.peerUid) { AndroidView(factory = { Rtc.remoteView(it, s.peerUid) }, modifier = Modifier.fillMaxSize()) }
            else {
                if (avatar.isNotBlank()) AsyncImage(model = avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.35f)
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        status == "ended" -> { Text("ไลฟ์นี้จบแล้ว", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("ขอบคุณที่มาร่วมดู 💖", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                            Spacer(Modifier.height(14.dp))
                            Text("ปิดหน้านี้", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(Neon, Violet))).clickable(onClick = onClose).padding(horizontal = 24.dp, vertical = 10.dp)) }
                        err.isNotBlank() -> Text(err, color = Color(0xFFFFC94D), fontSize = 14.sp)
                        else -> { CircularProgressIndicator(color = Color.White); Spacer(Modifier.height(10.dp)); Text("กำลังเชื่อมต่อไลฟ์…", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp) }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))))
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.45f).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))))
            Column(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f, fill = false).clip(RoundedCornerShape(24.dp)).background(Color(0x66000000)).padding(4.dp).padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.example.ui.social.Avatar(avatar, name.ifBlank { "?" }, 34)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.widthIn(max = 110.dp)) {
                            Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (title.isNotBlank()) Text(title, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FollowButton(hostUid)
                    }
                    Spacer(Modifier.width(8.dp))
                    ViewerFaces(liveId, viewers)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, "ออก", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
                Spacer(Modifier.weight(1f))
                GiftBanner(msgs, Modifier.padding(start = 12.dp, bottom = 8.dp))
                if (status != "ended") CommentsPanel(liveId, msgs, Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Neon, Violet))).clickable { giftSheet = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CardGiftcard, "ของขวัญ", tint = Color.White, modifier = Modifier.size(21.dp)) }
                    SmallRound(Icons.Filled.Share, "แชร์") { shareLive(ctx, name, title) }
                }
            }
            GiftBurstLayer(msgs) // big gift effect in the middle of the screen
        }
        if (giftSheet) LiveGiftSheet(liveId) { giftSheet = false }
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
