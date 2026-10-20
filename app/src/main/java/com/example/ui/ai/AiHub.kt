package com.example.ui.ai

import com.example.ui.components.dialogNavPadding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.example.ui.components.ft.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.network.AiRepo
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/** AI hub: chat with AI characters (always labeled AI), link to the ads page, admin bot management. */
@Composable
fun AiHubDialog(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var bots by remember { mutableStateOf<List<AiRepo.AiBot>?>(null) }
    var chatWith by remember { mutableStateOf<AiRepo.AiBot?>(null) }
    var isAdmin by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        FirebaseAuth.getInstance().currentUser?.let { u -> bots = AiRepo.bots(u); isAdmin = AiRepo.adminBots(u) != null }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding(), color = Slate950) {
            val c = chatWith
            if (c != null) {
                BotChatScreen(bot = c, onEnd = {
                    FirebaseAuth.getInstance().currentUser?.let { u -> scope.launch { AiRepo.endBotChat(u, c.id) } }
                    chatWith = null
                }, onSkip = { chatWith = null })
                return@Surface
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤖 คุยกับ AI", color = White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("ปิด", color = Slate300) }
                }
                Text("ตัวละคร AI ไม่ใช่คนจริง ฟรี ไม่ใช้เหรียญ · หาเพื่อนจริงได้ที่หน้า 'หาเพื่อน'", color = Slate400, fontSize = 12.sp)
                val list = bots
                when {
                    list == null -> CircularProgressIndicator(color = Pink500)
                    list.isEmpty() -> Text("ตอนนี้ยังไม่เปิดให้คุยกับ AI", color = Slate400, fontSize = 13.sp)
                    else -> list.forEach { b ->
                        Surface(shape = RoundedCornerShape(14.dp), color = Slate800, onClick = { chatWith = b }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(model = b.avatarUrl, contentDescription = b.name, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(CircleShape))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(b.name, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        Spacer(Modifier.width(6.dp)); AiBadge()
                                    }
                                    Text(b.bio, color = Slate400, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Text("คุย ›", color = Pink400, fontSize = 13.sp)
                            }
                        }
                    }
                }
                if (isAdmin) HubCard("🛠 จัดการบอท (แอดมิน)") {
                    OutlinedButton(onClick = { manage = true }, modifier = Modifier.fillMaxWidth()) { Text("เปิด/ปิดบอท AI", color = White) }
                }
            }
        }
    }
    if (manage) Dialog(onDismissRequest = { manage = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding(), color = Slate950) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤖 จัดการบอท AI", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { manage = false }) { Text("ปิด", color = Slate300) }
                }
                AdminBotsTab()
            }
        }
    }
}
