package com.example.ui.social

import com.example.ui.components.dialogNavPadding

import com.example.ui.components.tr
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.Brush
import com.example.ui.components.ft.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.social.ChatSummary
import com.example.social.RealProfile
import com.example.social.SocialRepo
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

@Composable
fun Avatar(url: String, name: String, size: Int) {
    if (url.isNotBlank()) AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp).clip(CircleShape))
    else Box(Modifier.size(size.dp).clip(CircleShape).background(Purple600), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = White, fontWeight = FontWeight.Bold, fontSize = (size / 2.4).sp)
    }
}

@Composable
internal fun rememberProfile(uid: String): RealProfile? {
    val p by produceState<RealProfile?>(null, uid) { value = if (uid.isBlank()) null else SocialRepo.profile(uid) }
    return p
}

/** Real chat list from RTDB userChats/{uid}. */
@Composable
fun RealChatListScreen() { ChatRulesGate { RealChatListInner() } }

@Composable
private fun RealChatListInner() {
    val me = FirebaseAuth.getInstance().currentUser?.uid
    if (me == null) { Text(tr("กรุณาเข้าสู่ระบบ"), color = Slate400, modifier = Modifier.padding(16.dp)); return }
    val chats by remember(me) { SocialRepo.chatList(me) }.collectAsState(initial = null)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        var aiHub by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("แชท"), fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = White, modifier = Modifier.weight(1f))
            com.example.ui.components.FeatureLockGate("ai", compact = true) { OutlinedButton(onClick = { aiHub = true }, shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Purple500, Pink500)))) { Text(tr("🤖 คุยกับ AI"), color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold) } }
        }
        if (aiHub) com.example.ui.components.UiPage("ai") { com.example.ui.ai.AiHubDialog { aiHub = false } }
        com.example.ads.PlacementBanner("chatList")
        Spacer(Modifier.height(8.dp))
        val previews by produceState(emptyMap<String, com.example.social.GiftRepo.Decor>(), chats?.size) { chats?.let { value = com.example.social.GiftRepo.previews(it.map { c -> c.chatId }) } }
        when {
            chats == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pink500) }
            chats!!.isEmpty() -> Text(tr("ยังไม่มีแชท ไปที่หน้า หาเพื่อน เพื่อปัด สุ่ม หรือเขย่าหาเพื่อน"), color = Slate400, fontSize = 13.sp)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(chats!!, key = { it.chatId }) { c -> ChatRow(c, previews[c.chatId]) }
            }
        }
    }
}

@Composable
private fun ChatRow(c: ChatSummary, decor: com.example.social.GiftRepo.Decor? = null) {
    val p = rememberProfile(c.otherUid)
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale("th", "TH")) }
    Surface(shape = RoundedCornerShape(16.dp), color = Slate900, border = androidx.compose.foundation.BorderStroke(1.dp, Slate700.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().clickable { SocialRepo.openChat.value = c.chatId }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            var showP by remember { mutableStateOf(false) }
            if (showP) UserProfileDialog(c.otherUid) { showP = false }
            Box(Modifier.clip(CircleShape).border(2.5.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Amber400, Pink500, Purple500)), CircleShape).padding(4.dp).clip(CircleShape).clickable { showP = true }) { Avatar(p?.avatar.orEmpty(), p?.displayName ?: "?", 56) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p?.displayName ?: "…", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                Text(c.lastMessage.ifBlank { tr("เริ่มคุยกันได้เลย") }, color = Slate300, fontSize = 14.sp, maxLines = 1)
            }
            decor?.let { Box(Modifier.padding(end = 6.dp)) { DecorMiniPreview(it) } }
            if (c.lastAt > 0) Text(fmt.format(Date(c.lastAt)), color = Slate300, fontSize = 12.sp, modifier = Modifier.align(Alignment.Top))
        }
    }
}

private suspend fun idToken(): String? = suspendCancellableCoroutine { c ->
    val u = FirebaseAuth.getInstance().currentUser
    if (u == null) c.resume(null) else u.getIdToken(false).addOnCompleteListener { c.resume(it.result?.token) }
}

/** Full-screen realtime 1:1 chat room. */
@Composable
fun RealChatRoomDialog(chatId: String, onClose: () -> Unit) {
    val me = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val otherUid by produceState("", chatId) { value = SocialRepo.chatOther(chatId, me).orEmpty() }
    val other = rememberProfile(otherUid)
    val paid by produceState<Pair<Boolean, String>?>(null, chatId) { value = PaidChat.info(chatId) }
    var reportU by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) ChatRulesDialog { showRules = false }
    var blocked by remember { mutableStateOf(false) }
    LaunchedEffect(otherUid) { if (otherUid.isNotBlank()) { val u = FirebaseAuth.getInstance().currentUser; if (u != null) blocked = com.example.network.BrainApi.call(u, "GET", "/blocks").json?.optJSONArray("blocked")?.let { a -> (0 until a.length()).any { a.optString(it) == otherUid } } == true } }
    if (reportU && otherUid.isNotBlank()) ReportDialog("user", otherUid, otherUid) { reportU = false }
    val messages by remember(chatId) { SocialRepo.messages(chatId) }.collectAsState(initial = emptyList())
    val otherRead by remember(chatId, otherUid) { if (otherUid.isBlank()) kotlinx.coroutines.flow.flowOf(0L) else SocialRepo.otherReadAt(chatId, otherUid) }.collectAsState(initial = 0L)
    val token by produceState<String?>(null) { value = idToken() }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var cs by remember { mutableStateOf<SocialRepo.ChatStatus?>(null) }
    var csTick by remember { mutableStateOf(0) }
    LaunchedEffect(chatId, csTick) { cs = SocialRepo.chatStatus(chatId) }
    val decorRev by remember(chatId) { com.example.social.GiftRepo.decorRev(chatId) }.collectAsState(initial = 0L)
    var decorTick by remember { mutableStateOf(0) }
    val decorState by produceState<com.example.social.GiftRepo.DecorState?>(null, chatId, decorRev, decorTick) { value = com.example.social.GiftRepo.decor(chatId) }
    val decor = decorState?.decor
    val stickerList by remember { com.example.social.GiftRepo.catalog("stickers") }.collectAsState(initial = emptyList())
    val prefs = remember { context.getSharedPreferences("friendtalk", 0) }
    var lite by remember { mutableStateOf(prefs.getBoolean("decorLite", false)) }
    var editDecor by remember { mutableStateOf(false) }
    var vipInfo by remember { mutableStateOf(false) }
    var giftSheet by remember { mutableStateOf(false) }
    var stickerSheet by remember { mutableStateOf(false) }
    // free chat stickers uploaded by admins (catalog/chatStickers), for drawing sticker messages
    val chatStickers by remember { com.example.social.GiftRepo.catalog("chatStickers", enabledOnly = false) }.collectAsState(initial = emptyList())
    val chatStickerMap = remember(chatStickers) { com.example.social.ChatSticker.BUILT_IN.associateBy { it.id } + chatStickers.associateBy { it.id } }
    var isFriend by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(otherUid, csTick) { if (otherUid.isNotBlank()) isFriend = com.example.social.NearbyRepo.friendsWithOnline().any { it.first.uid == otherUid } }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) { listState.animateScrollToItem(messages.size - 1); SocialRepo.markRead(chatId) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val size = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: 0L
            if (size > 10L * 1024 * 1024) { status = tr("รูปใหญ่เกิน 10MB"); return@rememberLauncherForActivityResult }
            status = tr("กำลังส่งรูป…")
            scope.launch { status = if (SocialRepo.sendImage(chatId, uri, mime)) "" else tr("ส่งรูปไม่สำเร็จ"); csTick++ }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Slate950) {
            // Insets: the input row was drawn behind the navigation bar (edge-to-edge dialog window) → pad for system bars + keyboard.
            Column(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding().imePadding()) {
                var callInfo by remember { mutableStateOf<String?>(null) }
                Box(Modifier.fillMaxWidth().background(chatHeaderBrush())) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("กลับ"), tint = PureWhite) }
                        var showOther by remember { mutableStateOf(false) }
                        if (showOther && otherUid.isNotBlank()) UserProfileDialog(otherUid) { showOther = false }
                        Box(Modifier.clip(CircleShape).border(2.dp, Brush.linearGradient(listOf(Amber400, Color(0xFFFF2FA8), Color(0xFFB04DFF))), CircleShape).padding(3.dp).clip(CircleShape).clickable { showOther = true }) { Avatar(other?.avatar.orEmpty(), other?.displayName ?: "?", 38) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(other?.displayName ?: "…", color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            when (isFriend) {
                                true -> Text(tr("เพื่อน"), color = Color(0xFFFFD98A), fontSize = 11.sp, maxLines = 1)
                                false -> Text(tr("ยังไม่ได้เป็นเพื่อน"), color = PureWhite.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1)
                                null -> {}
                            }
                        }
                        IconButton(onClick = { callInfo = "voice" }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Call, tr("โทรเสียง"), tint = PureWhite) }
                        IconButton(onClick = { callInfo = "video" }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Videocam, tr("วิดีโอคอล"), tint = PureWhite) }
                        IconButton(onClick = { giftSheet = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.CardGiftcard, tr("ส่งของขวัญ"), tint = Color(0xFFFFC94D)) }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.MoreVert, tr("เมนู"), tint = PureWhite) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                val c = cs
                                val ds = decorState
                                // Room decor (VIP): first item so it is easy to find; free users get the VIP details instead of a dead end.
                                if (ds?.canEdit == true) DropdownMenuItem(text = { Text(tr("🎨 ตกแต่งห้องแชท"), fontWeight = FontWeight.Bold) }, onClick = { menu = false; editDecor = true })
                                else if (ds != null && ds.ownerUid.isNotBlank() && ds.ownerUid != me) DropdownMenuItem(text = { Text(tr("🎨 ห้องนี้ตกแต่งโดยอีกฝ่าย"), color = Slate400) }, onClick = { menu = false; status = tr("ห้องนี้ถูกตกแต่งโดยอีกฝ่าย ต้องให้เจ้าของรีเซ็ตก่อนจึงจะตกแต่งเองได้") })
                                else DropdownMenuItem(text = { Text(tr("🎨 ตกแต่งห้องแชท 👑 VIP")) }, onClick = { menu = false; vipInfo = true })
                                DropdownMenuItem(text = { Text(tr("🚩 รายงานผู้ใช้")) }, onClick = { menu = false; reportU = true })
                                DropdownMenuItem(text = { Text(if (blocked) tr("✅ เลิกบล็อก") else tr("⛔ บล็อกผู้ใช้")) }, onClick = { menu = false; scope.launch {
                                    val u = FirebaseAuth.getInstance().currentUser ?: return@launch
                                    val r = com.example.network.BrainApi.call(u, "POST", "/blocks/$otherUid", org.json.JSONObject().put("block", !blocked))
                                    if (r.code == 200) { blocked = !blocked; status = if (blocked) tr("บล็อกแล้ว — อีกฝ่ายส่งข้อความ/ของขวัญหาคุณไม่ได้") else tr("เลิกบล็อกแล้ว") } else status = tr("ไม่สำเร็จ") } })
                                DropdownMenuItem(text = { Text(tr("🛡 กติกาการแชท")) }, onClick = { menu = false; showRules = true })
                                if (c?.closed == true && c.closedByMe) DropdownMenuItem(text = { Text(tr("🔓 เปิดแชทอีกครั้ง")) }, onClick = { menu = false; scope.launch { status = SocialRepo.setClosed(chatId, false).orEmpty(); csTick++ } })
                                else if (c?.closed != true) DropdownMenuItem(text = { Text(tr("🔒 ปิดแชทนี้")) }, onClick = { menu = false; scope.launch { status = SocialRepo.setClosed(chatId, true).orEmpty(); csTick++ } })
                                if (decor != null) DropdownMenuItem(text = { Text(if (lite) tr("🎞 เปิดภาพเคลื่อนไหว") else tr("🪶 โหมดประหยัด (หยุด GIF)")) }, onClick = { menu = false; lite = !lite; prefs.edit().putBoolean("decorLite", lite).apply() })
                                if (decor != null && ds?.ownerUid != me) DropdownMenuItem(text = { Text(tr("🚩 รายงานพื้นหลังไม่เหมาะสม")) }, onClick = { menu = false; scope.launch { status = if (com.example.social.GiftRepo.reportDecor(chatId, "inappropriate")) tr("รายงานแล้ว ขอบคุณ") else tr("รายงานไม่สำเร็จ") } })
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xCCFFC94D), Color.Transparent))))
                // Slim banner ad right under the header — free users only (the server sets showAds=false for VIP/premium; admin can switch the placement off).
                Box(Modifier.fillMaxWidth().background(AppThemeMode.dialog), contentAlignment = Alignment.Center) { com.example.ads.PlacementBanner("chatRoom") }
                if (isFriend == false) Row(Modifier.fillMaxWidth().background(AppThemeMode.card).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("คุณยังไม่ได้เป็นเพื่อนกัน"), color = AppThemeMode.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFFFF2FA8), Color(0xFF9B3DFF))))
                        .clickable { scope.launch { status = if (com.example.social.NearbyRepo.requestFriend(otherUid) != null) tr("ส่งคำขอเป็นเพื่อนแล้ว") else tr("ส่งคำขอไม่สำเร็จ"); csTick++ } }
                        .padding(horizontal = 12.dp, vertical = 5.dp)) { Text(tr("➕ เพิ่มเพื่อน"), color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
                callInfo?.let { kind ->
                    // No call service is connected yet (needs WebRTC/TURN or a provider) — say so instead of showing a fake call screen.
                    AlertDialog(onDismissRequest = { callInfo = null }, confirmButton = { TextButton(onClick = { callInfo = null }) { Text(tr("ตกลง")) } },
                        title = { Text(if (kind == "video") tr("🎥 วิดีโอคอล") else tr("📞 โทรเสียง")) },
                        text = { Text(tr("ระบบโทรผ่านแอปกำลังพัฒนา ยังใช้งานไม่ได้ — ตอนนี้คุยกันผ่านข้อความ รูป สติกเกอร์ และของขวัญได้")) })
                }
                decorState?.hiddenReason?.takeIf { it.isNotBlank() }?.let { Text("✨ $it", color = Slate400, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp)) }
                Box(Modifier.weight(1f).background(chatPageBrush())) {
                if (decor != null) DecorBackground(chatId, decor, token, stickerList.associateBy { it.id }, lite)
                val dayStarts = remember(messages) { messages.filterIndexed { i, m -> i == 0 || dayKey(m.at) != dayKey(messages[i - 1].at) }.map { it.id }.toSet() }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(messages, key = { it.id }) { m ->
                        if (m.id in dayStarts) DayChip(m.at)
                        val mine = m.senderUid == me
                        var showSender by remember { mutableStateOf(false) }
                        if (showSender) UserProfileDialog(m.senderUid) { showSender = false }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                        if (!mine) { Box(Modifier.clip(CircleShape).clickable { showSender = true }) { Avatar(other?.avatar.orEmpty(), other?.displayName ?: "?", 28) }; Spacer(Modifier.width(6.dp)) }
                        Column(Modifier.weight(1f, fill = false), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                            val stickerId = if (m.imagePath == null && m.gift == null) com.example.social.ChatSticker.decode(m.text) else null
                            if (m.gift != null) GiftBubble(m.gift, mine) { scope.launch { status = com.example.social.GiftRepo.accept(m.gift.txId).msg; csTick++ } }
                            else if (stickerId != null) {
                                // sticker message: the picture itself, no bubble (falls back to text if the admin removed it)
                                val st = chatStickerMap[stickerId]
                                if (st != null) Box(Modifier.size(120.dp)) { StickerImage(st) }
                                else Text(com.example.social.ChatSticker.PREVIEW, color = Slate400, fontSize = 13.sp, modifier = Modifier.padding(6.dp))
                            }
                            else if (decor != null && m.imagePath == null) DecorBubble(decor, m.text.orEmpty(), mine)
                            else Box(Modifier.clip(bubbleShape(mine)).then(if (mine) Modifier.background(Brush.linearGradient(listOf(Color(0xFFFF2FA8), Color(0xFF9B3DFF))))
                                else Modifier.background(AppThemeMode.cardSolid).border(1.dp, AppThemeMode.divider, bubbleShape(mine)))) {
                                if (m.imagePath != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(SocialRepo.imageUrl(chatId, m.imagePath))
                                            .addHeader("Authorization", "Bearer ${token.orEmpty()}").build(),
                                        contentDescription = tr("รูปภาพ"), contentScale = ContentScale.Crop,
                                        modifier = Modifier.widthIn(max = 220.dp).heightIn(max = 280.dp).clip(RoundedCornerShape(14.dp))
                                    )
                                } else Text(m.text.orEmpty(), color = if (mine) PureWhite else AppThemeMode.text, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                            }
                            Text(hhmm.format(Date(m.at)) + if (mine && m == messages.lastOrNull { it.senderUid == me } && otherRead >= m.at) " · " + tr("อ่านแล้ว") else "",
                                color = AppThemeMode.muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                        }
                    }
                }
                }
                if (editDecor) RoomDecorEditor(chatId, decor, token) { saved -> editDecor = false; if (saved) decorTick++ }
                if (vipInfo) com.example.ui.feed.VipDetailsDialog { vipInfo = false }
                if (giftSheet) ChatGiftSheet(onDismiss = { giftSheet = false }) { item -> giftSheet = false; scope.launch { status = com.example.social.GiftRepo.sendGift(chatId, item.id).msg } }
                if (stickerSheet) ChatStickerSheet(onDismiss = { stickerSheet = false }) { item ->
                    stickerSheet = false
                    scope.launch {
                        val pc = paid
                        if (pc != null && pc.first && pc.second != me) { PaidChat.send(chatId, com.example.social.ChatSticker.encode(item.id))?.let { e -> status = e }; return@launch }
                        if (!SocialRepo.sendSticker(chatId, item.id)) { cs = SocialRepo.chatStatus(chatId); status = if (cs?.canSend == false) "" else tr("ส่งสติกเกอร์ไม่สำเร็จ") } else status = ""
                    }
                }
                if (status.isNotBlank()) Text(status, color = Amber400, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp))
                cs?.takeIf { !it.canSend }?.let { Surface(color = Slate800, modifier = Modifier.fillMaxWidth()) { Text("🚫 " + it.reason, color = Amber400, fontSize = 13.sp, modifier = Modifier.padding(12.dp)) } }
                fun sendNow() {
                    val t = input; if (t.isBlank()) return; input = ""
                    scope.launch {
                        val pc = paid
                        if (pc != null && pc.first && pc.second != me) { PaidChat.send(chatId, t)?.let { e -> input = t; status = e }; return@launch }
                        if (!SocialRepo.sendText(chatId, t)) { input = t; cs = SocialRepo.chatStatus(chatId); status = if (cs?.canSend == false) "" else tr("ส่งไม่สำเร็จ (ยืนยันอีเมลแล้วหรือยัง?)") } else status = "" }
                }
                if (cs?.canSend != false) Row(Modifier.fillMaxWidth().background(AppThemeMode.dialog).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                    val fieldShape = RoundedCornerShape(26.dp)
                    Row(Modifier.weight(1f).heightIn(min = 52.dp).clip(fieldShape).background(AppThemeMode.field)
                        .border(1.2.dp, Brush.linearGradient(listOf(Color(0xFFFF2FA8), Color(0xFF9B3DFF), Color(0xFFFFC94D))), fieldShape).padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { stickerSheet = true }, modifier = Modifier.size(42.dp)) { Icon(Icons.Filled.EmojiEmotions, tr("สติกเกอร์"), tint = Color(0xFFC48CFF)) }
                        TextField(value = input, onValueChange = { if (it.length <= 2000) input = it }, modifier = Modifier.weight(1f),
                            placeholder = { Text(tr("พิมพ์ข้อความ…"), color = AppThemeMode.fieldHint, fontSize = 15.sp) }, maxLines = 5,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                                focusedTextColor = AppThemeMode.text, unfocusedTextColor = AppThemeMode.text, cursorColor = Color(0xFFFF2FA8)))
                        IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.size(42.dp)) { Icon(Icons.Filled.PhotoCamera, tr("ส่งรูป"), tint = Color(0xFFC48CFF)) }
                    }
                    Spacer(Modifier.width(6.dp))
                    val canSend = input.isNotBlank()
                    Box(Modifier.size(52.dp).clip(CircleShape)
                        .background(if (canSend) Brush.linearGradient(listOf(Color(0xFFFF2FA8), Color(0xFF9B3DFF))) else Brush.linearGradient(listOf(AppThemeMode.skeleton, AppThemeMode.skeleton)))
                        .clickable(enabled = canSend) { sendNow() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Send, tr("ส่ง"), tint = if (canSend) PureWhite else AppThemeMode.muted, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

/** Gift message: recipient sees 'รับของขวัญ' until accepted (coins move only on accept). */
@Composable
private fun GiftBubble(g: com.example.social.GiftMsg, mine: Boolean, onAccept: () -> Unit) {
    var busy by remember(g.txId, g.status) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF3B1D4A), border = androidx.compose.foundation.BorderStroke(1.dp, Amber400)) {
        Column(Modifier.padding(12.dp).widthIn(max = 220.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (g.imageUrl.isNotBlank()) AsyncImage(model = g.imageUrl, contentDescription = g.name, modifier = Modifier.size(72.dp)) else Text(g.icon.ifBlank { "🎁" }, fontSize = 44.sp)
            Text(g.name, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("🪙 ${g.price}", color = Amber400, fontSize = 12.sp)
            when {
                g.status == "accepted" -> Text(tr("✓ รับแล้ว"), color = Emerald400, fontSize = 12.sp)
                mine -> Text(tr("รออีกฝ่ายกดรับ"), color = Slate300, fontSize = 11.sp)
                else -> Button(enabled = !busy, onClick = { busy = true; onAccept() }, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text(if (busy) tr("กำลังรับ…") else tr("รับของขวัญ")) }
            }
        }
    }
}

/** 🎁 button usable anywhere a person is shown: opens (or creates) the 1:1 chat, then sends the same paid gift (charged on accept). */
@Composable
fun GiftToUserButton(otherUid: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (otherUid == FirebaseAuth.getInstance().currentUser?.uid) return
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var open by remember { mutableStateOf(false) }
    com.example.ui.components.FeatureLockGate("gifts", modifier, compact = true) {
        if (compact) Surface(shape = RoundedCornerShape(14.dp), color = Pink500.copy(alpha = 0.15f), onClick = { open = true }) { Text("🎁", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
        else OutlinedButton(onClick = { open = true }, shape = RoundedCornerShape(20.dp)) { Text(tr("🎁 ส่งของขวัญ"), color = Pink500, fontWeight = FontWeight.Bold) }
    }
    if (open) ChatGiftSheet(onDismiss = { open = false }) { item ->
        open = false
        scope.launch {
            val chatId = SocialRepo.openChatWith(otherUid)
            val msg = if (chatId == null) SocialRepo.lastOpenError.ifBlank { tr("เปิดแชทไม่สำเร็จ") } else com.example.social.GiftRepo.sendGift(chatId, item.id).msg
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

/** Realtime list of enabled chat gifts (catalog/chat). */
/** Sticker picker: free stickers the admins uploaded (catalog/chatStickers), grouped by category. Tap = send. */
@Composable
internal fun ChatStickerSheet(onDismiss: () -> Unit, onPick: (com.example.social.CatalogItem) -> Unit) {
    val items by remember { com.example.social.GiftRepo.catalog("chatStickers") }.collectAsState(initial = null)
    var cat by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(18.dp), color = Slate900) {
            Column(Modifier.padding(16.dp).heightIn(max = 520.dp)) {
                Text(tr("😊 สติกเกอร์"), color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(tr("แตะเพื่อส่ง · ส่งฟรี"), color = Slate400, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                // admin catalog first; while it is empty, the built-in emoji stickers are offered
                val l = items?.ifEmpty { com.example.social.ChatSticker.BUILT_IN }
                when {
                    l == null -> CircularProgressIndicator(color = Pink500)
                    l.isEmpty() -> Text(tr("ยังไม่มีสติกเกอร์ (แอดมินกำลังเพิ่ม)"), color = Slate400, modifier = Modifier.padding(vertical = 16.dp))
                    else -> {
                        val cats = l.map { it.category }.filter { it.isNotBlank() && it != "chatStickers" }.distinct()
                        if (cats.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = cat == null, onClick = { cat = null }, label = { Text(tr("ทั้งหมด"), fontSize = 12.sp) })
                            cats.forEach { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c, fontSize = 12.sp) }) }
                        }
                        val shown = if (cat == null) l else l.filter { it.category == cat }
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(shown.size) { i -> val it = shown[i]
                                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Slate800).clickable { onPick(it) }.padding(6.dp)) { StickerImage(it) }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(tr("ปิด"), color = Slate300) }
            }
        }
    }
}

@Composable
internal fun ChatGiftSheet(onDismiss: () -> Unit, onPick: (com.example.social.CatalogItem) -> Unit) {
    val items by remember { com.example.social.GiftRepo.catalog("chat") }.collectAsState(initial = null)
    val wallet by produceState<Triple<Int, Int, Int>?>(null) { value = com.example.social.GiftRepo.wallet() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(18.dp), color = Slate900) {
            Column(Modifier.padding(16.dp).heightIn(max = 520.dp)) {
                Text(tr("🎁 ส่งของขวัญ"), color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                wallet?.let { Text("💎 เพชร ${it.first} · จองไว้ ${it.second} · ใช้ได้ ${it.third}", color = Slate300, fontSize = 12.sp) }
                Text("ของขวัญใช้เพชร 💎 · ยังไม่หักตอนส่ง จะหักเมื่ออีกฝ่ายกดรับ (ผู้รับได้ 80%)", color = Slate400, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                val l = items
                when {
                    l == null -> CircularProgressIndicator(color = Pink500)
                    l.isEmpty() -> Text(tr("ยังไม่มีของขวัญในแชท"), color = Slate400)
                    else -> androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(l.size) { i -> val it = l[i]
                            Surface(shape = RoundedCornerShape(12.dp), color = Slate800, onClick = { onPick(it) }) {
                                Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(48.dp)) { StickerImage(it) }
                                    Text(it.name, color = White, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text("💎 ${it.price}" + if (wallet != null && wallet!!.third < it.price) " · ไม่พอ" else "", color = if (wallet != null && wallet!!.third < it.price) Rose500 else Cyan400, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Luxury header: deep violet → plum (dark) / royal purple → magenta (light), white text in both. */
private fun chatHeaderBrush(): Brush = if (AppThemeMode.dark) Brush.horizontalGradient(listOf(Color(0xFF140F35), Color(0xFF2B1660), Color(0xFF3A1A5E)))
    else Brush.horizontalGradient(listOf(Color(0xFF5B23A0), Color(0xFF8E45C8), Color(0xFFB0459F)))

private fun chatPageBrush(): Brush = if (AppThemeMode.dark) Brush.verticalGradient(listOf(Color(0xFF0B0824), Color(0xFF130C33), Color(0xFF0B0824)))
    else Brush.verticalGradient(listOf(Color(0xFFF6EEFF), Color(0xFFF1E2FA), Color(0xFFEBD8F7)))

/** Chat bubble with a small "tail" corner on the sender's side. */
private fun bubbleShape(mine: Boolean) = if (mine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)

private val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault())
private fun dayKey(at: Long) = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(at))

/** Centered date pill between days: วันนี้ / เมื่อวาน / d MMM yyyy. */
@Composable
private fun DayChip(at: Long) {
    val now = System.currentTimeMillis()
    val label = when (dayKey(at)) { dayKey(now) -> tr("วันนี้"); dayKey(now - 86_400_000L) -> tr("เมื่อวาน"); else -> SimpleDateFormat("d MMM yyyy", Locale("th", "TH")).format(Date(at)) }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(label, color = AppThemeMode.muted, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(AppThemeMode.pill).padding(horizontal = 12.dp, vertical = 3.dp))
    }
}
