package com.example.ui.ai

import androidx.compose.ui.text.style.TextOverflow

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
import com.example.network.AiRepo
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch


private data class BotMsg(val fromBot: Boolean, val text: String, val isError: Boolean = false)

/** AI character chat (clearly labeled AI). */
@Composable
fun BotChatScreen(bot: AiRepo.AiBot, onEnd: () -> Unit, onSkip: () -> Unit) {
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
                        Text(bot.name, fontWeight = FontWeight.Bold, color = White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(6.dp)); AiBadge()
                    }
                    Text("ตัวละคร AI · ฟรี", fontSize = 10.sp, color = Slate400)
                }
                com.example.ads.QuotaChip("aiBotMessagesPerDay")
                // skip / end grouped in one menu so the header fits on narrow screens
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) { Text("⋮", color = White, fontSize = 20.sp) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("⏭ ข้าม หาคนใหม่") }, onClick = { menu = false; onSkip() })
                        DropdownMenuItem(text = { Text("🛑 จบแชท", color = Rose500) }, onClick = { menu = false; onEnd() })
                    }
                }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(messages) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromBot) Arrangement.Start else Arrangement.End) {
                    Surface(shape = RoundedCornerShape(14.dp),
                        color = when { m.isError -> Slate700; m.fromBot -> Slate800; else -> Pink600 }) {
                        Text(m.text, color = White, fontSize = 13.sp, modifier = Modifier.widthIn(max = 280.dp).padding(10.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = input, onValueChange = { if (it.length <= 500) input = it },
                modifier = Modifier.weight(1f), placeholder = { Text("พิมพ์ข้อความ…", color = Slate400) }, maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
            Spacer(Modifier.width(6.dp))
            Button(enabled = !sending && input.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Pink500), onClick = {
                val text = input.trim(); input = ""
                val fu = FirebaseAuth.getInstance().currentUser ?: return@Button
                messages.add(BotMsg(false, text)); sending = true
                scope.launch {
                    val reply = AiRepo.botChat(fu, bot.id, text)
                    sending = false
                    if (reply == "!QUOTA") {
                        // Quota used up: put the unsent text back so the user can tap send after topping up
                        messages.removeAt(messages.lastIndex); input = text
                    } else if (reply.startsWith("!")) messages.add(BotMsg(true, reply.drop(1), isError = true))
                    else messages.add(BotMsg(true, reply))
                }
            }) { Text(if (sending) "…" else "ส่ง") }
        }
    }
}
