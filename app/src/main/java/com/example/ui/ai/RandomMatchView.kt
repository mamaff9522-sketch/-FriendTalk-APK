package com.example.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.example.ui.components.ft.Text
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


/**
 * สุ่มหาเพื่อน: a big turtle in the middle — tap it, it spins while searching, then the person's intro card shows
 * (photo/video, name, age, intro, voice greeting, VIP frame / name frame / effect). ❤️ = like → chat, 💔 = next one.
 * No timer. 'แต่งหน้าสุ่ม' (VIP) opens the card editor. If nobody fits, a labelled AI character may show (as before).
 */
@Composable
fun RandomMatchView(users: List<User>, onStartChatWithUser: (User) -> Unit) {
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<com.example.ui.random.RandomCardRepo.Next?>(null) }
    var chatBot by remember { mutableStateOf<AiRepo.AiBot?>(null) }
    var editor by remember { mutableStateOf(false) }
    var vipInfo by remember { mutableStateOf(false) }
    var checkingVip by remember { mutableStateOf(false) }
    val skipped = remember { mutableStateListOf<String>() }

    fun roll() {
        if (searching) return
        searching = true; status = ""; result = null
        scope.launch {
            val t0 = System.currentTimeMillis()
            val r = com.example.ui.random.RandomCardRepo.next(skipped)
            val wait = 1200 - (System.currentTimeMillis() - t0) // let the turtle spin a little
            if (wait > 0) kotlinx.coroutines.delay(wait)
            searching = false
            if (r is com.example.ui.random.RandomCardRepo.Next.Nobody) status = r.msg else result = r
        }
    }

    chatBot?.let { b ->
        BotChatScreen(bot = b, onEnd = {
            FirebaseAuth.getInstance().currentUser?.let { fu -> scope.launch { AiRepo.endBotChat(fu, b.id) } }
            chatBot = null; result = null
        }, onSkip = { chatBot = null; roll() })
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // 'แต่งหน้าสุ่ม' capsule, top right
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Row(Modifier.clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFFFC94D), androidx.compose.ui.graphics.Color(0xFFFF2FA8))))
                .clickable(enabled = !checkingVip) {
                    checkingVip = true
                    scope.launch {
                        val c = com.example.ui.random.RandomCardRepo.catalog()
                        checkingVip = false
                        when { c == null -> status = "โหลดไม่สำเร็จ (เซิร์ฟเวอร์ยังไม่อัปเดต?)"; c.vip -> editor = true; else -> vipInfo = true }
                    }
                }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🖌️", fontSize = 13.sp); Spacer(Modifier.width(4.dp))
                Text(if (checkingVip) "กำลังเปิด…" else "แต่งหน้าสุ่ม", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        val r = result
        when (r) {
            is com.example.ui.random.RandomCardRepo.Next.Person -> {
                Spacer(Modifier.height(8.dp))
                com.example.ui.random.RandomCardView(r.card ?: com.example.ui.random.RandomCard(intro = r.profile.bio), r.profile.displayName, r.profile.age, r.profile.avatar,
                    Modifier.fillMaxWidth(0.92f))
                if (r.online) Text("🟢 ออนไลน์อยู่", color = Emerald400, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                LikeRow(
                    onPass = { skipped.add(r.profile.uid); roll() },
                    onLike = {
                        scope.launch {
                            val cid = com.example.social.SocialRepo.openChatWith(r.profile.uid)
                            if (cid != null) com.example.social.SocialRepo.openChat.value = cid
                            else status = com.example.social.SocialRepo.lastOpenError.ifBlank { "เปิดแชทไม่สำเร็จ" }
                        }
                    })
            }
            is com.example.ui.random.RandomCardRepo.Next.Bot -> {
                val b = r.bot
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth(0.92f).clip(RoundedCornerShape(24.dp)).background(Slate800).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(model = b.avatarUrl, contentDescription = b.name, contentScale = ContentScale.Crop, modifier = Modifier.size(110.dp).clip(CircleShape))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(b.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = White); Spacer(Modifier.width(6.dp)); AiBadge()
                    }
                    Text(b.bio, fontSize = 13.sp, color = Slate300, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text("นี่คือตัวละคร AI ไม่ใช่คนจริง", fontSize = 11.sp, color = Amber400, modifier = Modifier.padding(top = 4.dp))
                }
                LikeRow(onPass = { roll() }, onLike = { chatBot = b })
            }
            else -> {
                Spacer(Modifier.height(36.dp))
                Turtle(searching) { roll() }
            }
        }
        if (searching && r == null) Text("กำลังหาเพื่อนให้คุณ…", color = Slate300, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        if (status.isNotBlank()) Text(status, color = Amber400, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        if (r != null) Text("แตะ 🐢 ด้านล่างเพื่อกลับไปหน้าสุ่ม", color = Slate400, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp).clickable { result = null })
    }
    if (editor) com.example.ui.random.RandomCardEditor(onClose = { editor = false })
    if (vipInfo) com.example.ui.feed.VipDetailsDialog { vipInfo = false }
}

/** Big turtle; spins while searching. */
@Composable
private fun Turtle(spinning: Boolean, onTap: () -> Unit) {
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "turtle")
    val spin by inf.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.LinearEasing)), label = "spin")
    val bob by inf.animateFloat(-6f, 6f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400), androidx.compose.animation.core.RepeatMode.Reverse), label = "bob")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(230.dp).clip(CircleShape)
            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(androidx.compose.ui.graphics.Color(0x6634D399), androidx.compose.ui.graphics.Color(0x2210B981), androidx.compose.ui.graphics.Color.Transparent)))
            .clickable(enabled = !spinning, onClick = onTap), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Text("🐢", fontSize = 140.sp, modifier = Modifier.graphicsLayer {
                if (spinning) rotationZ = spin else translationY = bob
            })
        }
        Text(if (spinning) "กำลังสุ่ม…" else "กดสุ่ม", color = White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
    }
}

/** 💔 = not interested, find another · ❤️ = like → chat. */
@Composable
private fun LikeRow(onPass: () -> Unit, onLike: () -> Unit) {
    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(70.dp).clip(CircleShape).background(Slate800).clickable(onClick = onPass), contentAlignment = Alignment.Center) { androidx.compose.material3.Text("💔", fontSize = 34.sp) }
            Text("สุ่มใหม่", color = Slate400, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFFFF2FA8), androidx.compose.ui.graphics.Color(0xFF9B3DFF))))
                .clickable(onClick = onLike), contentAlignment = Alignment.Center) { androidx.compose.material3.Text("❤️", fontSize = 40.sp) }
            Text("ถูกใจ · แชทเลย", color = Pink400, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
internal fun HubCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Slate800, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}
