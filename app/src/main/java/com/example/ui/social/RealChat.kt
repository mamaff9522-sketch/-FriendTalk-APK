package com.example.ui.social

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
private fun rememberProfile(uid: String): RealProfile? {
    val p by produceState<RealProfile?>(null, uid) { value = if (uid.isBlank()) null else SocialRepo.profile(uid) }
    return p
}

/** Real chat list from RTDB userChats/{uid}. */
@Composable
fun RealChatListScreen() {
    val me = FirebaseAuth.getInstance().currentUser?.uid
    if (me == null) { Text("กรุณาเข้าสู่ระบบ", color = Slate400, modifier = Modifier.padding(16.dp)); return }
    val chats by remember(me) { SocialRepo.chatList(me) }.collectAsState(initial = null)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("แชท", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = White)
        Spacer(Modifier.height(8.dp))
        when {
            chats == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pink500) }
            chats!!.isEmpty() -> Text("ยังไม่มีแชท ลองปัดหาคู่หรือสุ่มคุยในแท็บค้นหา", color = Slate400, fontSize = 13.sp)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(chats!!, key = { it.chatId }) { c -> ChatRow(c) }
            }
        }
    }
}

@Composable
private fun ChatRow(c: ChatSummary) {
    val p = rememberProfile(c.otherUid)
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale("th", "TH")) }
    Surface(shape = RoundedCornerShape(14.dp), color = Slate800, modifier = Modifier.fillMaxWidth().clickable { SocialRepo.openChat.value = c.chatId }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p?.avatar.orEmpty(), p?.displayName ?: "?", 44)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p?.displayName ?: "…", color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(c.lastMessage.ifBlank { "เริ่มคุยกันได้เลย" }, color = Slate400, fontSize = 12.sp, maxLines = 1)
            }
            if (c.lastAt > 0) Text(fmt.format(Date(c.lastAt)), color = Slate400, fontSize = 10.sp)
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
    val messages by remember(chatId) { SocialRepo.messages(chatId) }.collectAsState(initial = emptyList())
    val otherRead by remember(chatId, otherUid) { if (otherUid.isBlank()) kotlinx.coroutines.flow.flowOf(0L) else SocialRepo.otherReadAt(chatId, otherUid) }.collectAsState(initial = 0L)
    val token by produceState<String?>(null) { value = idToken() }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) { listState.animateScrollToItem(messages.size - 1); SocialRepo.markRead(chatId) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val size = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: 0L
            if (size > 10L * 1024 * 1024) { status = "รูปใหญ่เกิน 10MB"; return@rememberLauncherForActivityResult }
            status = "กำลังส่งรูป…"
            scope.launch { status = if (SocialRepo.sendImage(chatId, uri, mime)) "" else "ส่งรูปไม่สำเร็จ" }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Slate950) {
            Column(Modifier.fillMaxSize()) {
                Surface(color = Slate900) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onClose) { Text("←", color = White, fontSize = 18.sp) }
                        Avatar(other?.avatar.orEmpty(), other?.displayName ?: "?", 36)
                        Spacer(Modifier.width(8.dp))
                        Text(other?.displayName ?: "…", color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    }
                }
                val shared by produceState<com.example.social.Similarity?>(null, otherUid) { if (otherUid.isNotBlank()) value = com.example.social.InterestsRepo.profile(otherUid)?.similarity }
                shared?.takeIf { it.shared.isNotEmpty() }?.let { Surface(color = Slate800, modifier = Modifier.fillMaxWidth()) { Box(Modifier.padding(10.dp)) { SharedInterests(it) } } }
                LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(messages, key = { it.id }) { m ->
                        val mine = m.senderUid == me
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                            Surface(shape = RoundedCornerShape(14.dp), color = if (mine) Pink600 else Slate800) {
                                if (m.imagePath != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(SocialRepo.imageUrl(chatId, m.imagePath))
                                            .addHeader("Authorization", "Bearer ${token.orEmpty()}").build(),
                                        contentDescription = "รูปภาพ", contentScale = ContentScale.Crop,
                                        modifier = Modifier.widthIn(max = 220.dp).heightIn(max = 280.dp).clip(RoundedCornerShape(14.dp))
                                    )
                                } else Text(m.text.orEmpty(), color = White, fontSize = 14.sp, modifier = Modifier.padding(10.dp))
                            }
                            if (mine && m == messages.lastOrNull { it.senderUid == me } && otherRead >= m.at) Text("อ่านแล้ว", color = Slate400, fontSize = 9.sp)
                        }
                    }
                }
                if (status.isNotBlank()) Text(status, color = Amber400, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp))
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("📷", fontSize = 20.sp) }
                    OutlinedTextField(value = input, onValueChange = { if (it.length <= 2000) input = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("พิมพ์ข้อความ…", color = Slate400) }, maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                    Spacer(Modifier.width(6.dp))
                    Button(enabled = input.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Pink500), onClick = {
                        val t = input; input = ""
                        scope.launch { if (!SocialRepo.sendText(chatId, t)) { status = "ส่งไม่สำเร็จ (ยืนยันอีเมลแล้วหรือยัง?)"; input = t } else status = "" }
                    }) { Text("ส่ง") }
                }
            }
        }
    }
}
