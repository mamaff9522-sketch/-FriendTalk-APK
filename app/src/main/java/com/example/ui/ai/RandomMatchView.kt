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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import coil.compose.AsyncImage
import com.example.model.User
import com.example.network.AiRepo
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch


/** Random match: real user first (server queue); otherwise an AI character (clearly labeled). */
@Composable
fun RandomMatchView(users: List<User>, onStartChatWithUser: (User) -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var bot by remember { mutableStateOf<AiRepo.AiBot?>(null) }
    var chatting by remember { mutableStateOf(false) }

    fun doMatch() {
        val fu = FirebaseAuth.getInstance().currentUser ?: run { status = "กรุณาเข้าสู่ระบบก่อน"; return }
        loading = true; status = ""; bot = null
        scope.launch {
            val res = AiRepo.match(fu)
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
            FirebaseAuth.getInstance().currentUser?.let { fu -> scope.launch { AiRepo.endBotChat(fu, b.id) } }
            chatting = false; bot = null
        }, onSkip = { chatting = false; doMatch() })
        return
    }

    var isAdmin by remember { mutableStateOf(false) }
    var manageBots by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { FirebaseAuth.getInstance().currentUser?.let { isAdmin = AiRepo.adminBots(it) != null } }

    // AI hub: one scrollable screen, grouped into sections (chat / my quota / admin)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🤖 ศูนย์ AI & สุ่มเพื่อน", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = White)
        HubCard("🎲 สุ่มหาเพื่อนคุย / คุยกับ AI") {
            Text("ระบบจะหาผู้ใช้จริงที่ว่างก่อน ถ้าไม่มี อาจเจอตัวละคร AI (มีป้าย AI กำกับเสมอ) ฟรี ไม่ใช้เหรียญ", fontSize = 12.sp, color = Slate400)
            if (b != null) {
                Column(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(model = b.avatarUrl, contentDescription = b.name, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp).clip(CircleShape))
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(6.dp)); AiBadge()
                    }
                    Text(b.bio, fontSize = 12.sp, color = Slate300, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    if (b.interests.isNotEmpty()) Text(b.interests.joinToString(" · "), fontSize = 11.sp, color = Slate400, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("นี่คือตัวละคร AI ไม่ใช่คนจริง", fontSize = 11.sp, color = Amber400)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { doMatch() }, modifier = Modifier.weight(1f)) { Text("ข้าม", color = White) }
                        Button(onClick = { chatting = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("เริ่มคุย") }
                    }
                }
            }
            if (status.isNotBlank()) Text(status, fontSize = 12.sp, color = Slate300, modifier = Modifier.padding(top = 6.dp))
            if (b == null) Button(onClick = { doMatch() }, enabled = !loading, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text(if (loading) "กำลังสุ่ม…" else "สุ่มเลย") }
        }
        // Ads live on their own page; only a link here.
        TextButton(onClick = { com.example.ads.AdRoom.open.value = "aiBotMessagesPerDay" }) { Text("🎁 โควตาหมด? ไปหน้าโฆษณา →", color = Pink400) }
        if (isAdmin) HubCard("🛠 จัดการบอท (แอดมิน)") {
            Text("เปิด/ปิดบอททั้งหมด และรายตัว", color = Slate400, fontSize = 12.sp)
            OutlinedButton(onClick = { manageBots = true }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("จัดการบอท AI", color = White) }
        }
    }
    if (manageBots) androidx.compose.ui.window.Dialog(onDismissRequest = { manageBots = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding(), color = Slate950) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤖 จัดการบอท AI", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { manageBots = false }) { Text("ปิด", color = Slate300) }
                }
                AdminBotsTab()
            }
        }
    }
}

@Composable
private fun HubCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Slate800, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}
