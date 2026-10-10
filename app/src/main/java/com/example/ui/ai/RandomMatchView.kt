package com.example.ui.ai

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.User
import com.example.network.BrainApi
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

private data class BotMsg(val fromBot: Boolean, val text: String, val isError: Boolean = false)

/** Random match: real user first (server queue); otherwise an AI character (clearly labeled). */
@Composable
fun RandomMatchView(users: List<User>, onStartChatWithUser: (User) -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var bot by remember { mutableStateOf<BrainApi.AiBot?>(null) }
    var chatting by remember { mutableStateOf(false) }

    fun doMatch() {
        val fu = FirebaseAuth.getInstance().currentUser ?: run { status = "กรุณาเข้าสู่ระบบก่อน"; return }
        loading = true; status = ""; bot = null
        scope.launch {
            val res = BrainApi.match(fu)
            loading = false
            when (res?.type) {
                "bot" -> bot = res.bot
                "user" -> {
                    // Real user matched: open the real chat created by the backend
                    val cid = res.chatId ?: res.partnerUid?.let { com.example.social.SocialRepo.openChatWith(it) }
                    if (cid != null) { status = "เจอผู้ใช้จริงแล้ว!"; com.example.social.SocialRepo.openChat.value = cid }
                    else status = "เจอผู้ใช้จริงแล้ว แต่เปิดแชทไม่สำเร็จ"
                }
                "waiting" -> status = "ยังไม่มีคนว่าง กำลังรอคู่… ลองกดอีกครั้งในไม่กี่วินาที"
                else -> status = "สุ่มไม่สำเร็จ ลองใหม่อีกครั้ง"
            }
        }
    }

    val b = bot
    if (chatting && b != null) {
        BotChatScreen(bot = b, onEnd = {
            FirebaseAuth.getInstance().currentUser?.let { fu -> scope.launch { BrainApi.endBotChat(fu, b.id) } }
            chatting = false; bot = null
        }, onSkip = { chatting = false; doMatch() })
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("สุ่มหาเพื่อนคุย 🎲", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = White)
        Text("ระบบจะหาผู้ใช้จริงที่ว่างก่อน ถ้าไม่มี อาจเจอตัวละคร AI (มีป้าย AI กำกับเสมอ) ฟรี ไม่ใช้เหรียญ",
            fontSize = 12.sp, color = Slate400)
        if (b != null) {
            Surface(shape = RoundedCornerShape(18.dp), color = Slate800, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(model = b.avatarUrl, contentDescription = b.name, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(CircleShape))
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = White)
                        Spacer(Modifier.width(6.dp)); AiBadge()
                    }
                    Text(b.bio, fontSize = 12.sp, color = Slate300)
                    if (b.interests.isNotEmpty()) Text(b.interests.joinToString(" · "), fontSize = 11.sp, color = Slate400)
                    Text("นี่คือตัวละคร AI ไม่ใช่คนจริง", fontSize = 11.sp, color = Amber400)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { doMatch() }) { Text("ข้าม", color = White) }
                        Button(onClick = { chatting = true }, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("เริ่มคุย") }
                    }
                }
            }
        }
        if (status.isNotBlank()) Text(status, fontSize = 12.sp, color = Slate300)
        Button(onClick = { doMatch() }, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) {
            Text(if (loading) "กำลังสุ่ม…" else "สุ่มเลย")
        }
    }
}

@Composable
fun BotChatScreen(bot: BrainApi.AiBot, onEnd: () -> Unit, onSkip: () -> Unit) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<BotMsg>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Column(Modifier.fillMaxSize()) {
        Surface(color = Slate900, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = bot.avatarUrl, contentDescription = bot.name, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(36.dp).clip(CircleShape))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bot.name, fontWeight = FontWeight.Bold, color = White, fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp)); AiBadge()
                    }
                    Text("ตัวละคร AI · ฟรี", fontSize = 10.sp, color = Slate400)
                }
                TextButton(onClick = onSkip) { Text("ข้าม", color = Slate300) }
                TextButton(onClick = onEnd) { Text("จบแชท", color = Rose500) }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(messages) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromBot) Arrangement.Start else Arrangement.End) {
                    Surface(shape = RoundedCornerShape(14.dp),
                        color = when { m.isError -> Slate700; m.fromBot -> Slate800; else -> Pink600 }) {
                        Text(m.text, color = White, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = input, onValueChange = { if (it.length <= 500) input = it },
                modifier = Modifier.weight(1f), placeholder = { Text("พิมพ์ข้อความ…", color = Slate400) }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
            Spacer(Modifier.width(6.dp))
            Button(enabled = !sending && input.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Pink500), onClick = {
                val text = input.trim(); input = ""
                val fu = FirebaseAuth.getInstance().currentUser ?: return@Button
                messages.add(BotMsg(false, text)); sending = true
                scope.launch {
                    val reply = BrainApi.botChat(fu, bot.id, text)
                    sending = false
                    if (reply.startsWith("!")) messages.add(BotMsg(true, reply.drop(1), isError = true))
                    else messages.add(BotMsg(true, reply))
                }
            }) { Text(if (sending) "…" else "ส่ง") }
        }
    }
}
