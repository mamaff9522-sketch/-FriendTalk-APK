package com.example.ui.payopen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.social.PayOpen
import com.example.social.PayOpenRepo
import com.example.social.PayResult
import com.example.social.SocialRepo
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Refund / accept results to play on the sender's phone (from the realtime listener or the swipe page's own polling). */
object PayOpenEvents {
    val result = MutableStateFlow<PayResult?>(null)
    fun deliver(r: PayResult) { if (PayOpenRepo.handled.add(r.id)) result.value = r }
}

private const val ACCEPT_SECONDS = 10
private const val EFFECT_MS = 3600L

/**
 * Lives at the root of the app (like the incoming-call screen) so a เปย์เปิดใจ gift shows on ANY screen:
 *  recipient → gift effect in the middle + cute 'สายเปย์มาแล้ว~' voice + sender's name, then ยอมรับ / ปฏิเสธ with a 10 s countdown;
 *  sender → refunded: diamonds fly back into the wallet with a random short voice line (opposite gender), no text; accepted: the chat opens.
 */
@Composable
fun PayOpenHost() {
    var uid by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.uid) }
    DisposableEffect(Unit) {
        val l = FirebaseAuth.AuthStateListener { uid = it.currentUser?.uid }
        FirebaseAuth.getInstance().addAuthStateListener(l)
        onDispose { FirebaseAuth.getInstance().removeAuthStateListener(l) }
    }
    val me = uid ?: return
    val since = remember(me) { System.currentTimeMillis() - 5_000 }
    val inbox by remember(me) { PayOpenRepo.inbox(me) }.collectAsState(initial = emptyList())
    LaunchedEffect(me) { PayOpenRepo.results(me, since).collect { PayOpenEvents.deliver(it) } }
    LaunchedEffect(me) { PayVoicePlayer.voices() } // warm the voice list

    // recipient: one gift at a time, oldest first; a gift whose answer window has passed is skipped (the server refunds it)
    val done = remember(me) { mutableStateListOf<String>() }
    val cur = inbox.filter { it.id !in done && it.state == "pending" && it.deadline - (System.currentTimeMillis() + it.skew) > 2_000 }.minByOrNull { it.deadline }
    cur?.let { g -> key(g.id) { ReceiveDialog(g) { done.add(g.id) } } }

    // sender
    val res by PayOpenEvents.result.collectAsState()
    res?.let { r ->
        key(r.id) {
            when (r.state) {
                "refunded" -> RefundFx(r) { PayOpenEvents.result.value = null }
                "accepted" -> LaunchedEffect(r.id) { if (r.chatId.isNotBlank()) SocialRepo.openChat.value = r.chatId; PayOpenEvents.result.value = null }
                else -> LaunchedEffect(r.id) { PayOpenEvents.result.value = null }
            }
        }
    }
}

@Composable
private fun ReceiveDialog(g: PayOpen, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableIntStateOf(0) } // 0 effect, 1 answer, 2 sending answer
    var left by remember { mutableIntStateOf(ACCEPT_SECONDS) }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(g.id) {
        PayVoicePlayer.playRandom(ctx, "arrival", if (g.fromGender == "male") "male" else "female", "สายเปย์มาแล้ว~")
        delay(EFFECT_MS); phase = 1
    }
    fun answer(accept: Boolean) {
        if (phase == 2) return
        phase = 2
        scope.launch {
            val r = PayOpenRepo.respond(g.id, accept)
            when {
                accept && r?.state == "accepted" && r.chatId.isNotBlank() -> { SocialRepo.openChat.value = r.chatId; onClose() }
                accept && r?.state == "refunded" -> { note = "หมดเวลาแล้ว"; delay(1500); onClose() }
                accept && r == null -> { note = "เชื่อมต่อไม่สำเร็จ ลองอีกครั้ง"; phase = 1 }
                else -> onClose()
            }
        }
    }
    // 10 s on screen once the buttons appear, never past the server's deadline
    LaunchedEffect(phase == 1) {
        if (phase != 1) return@LaunchedEffect
        val endAt = minOf(System.currentTimeMillis() + ACCEPT_SECONDS * 1000L, g.deadline - g.skew - 1_000)
        while (phase == 1) {
            val ms = endAt - System.currentTimeMillis()
            left = ((ms + 999) / 1000).toInt().coerceIn(0, ACCEPT_SECONDS)
            if (ms <= 0) { answer(false); break }
            delay(200)
        }
    }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Color(0x8C000000)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
                if (g.videoUrl.isNotBlank()) Box(Modifier.fillMaxWidth().heightIn(max = 340.dp)) { com.example.ui.components.GiftVideoFx(g.videoUrl, {}, dim = false, maxMs = 3_600L) }
                else PayGiftEffect(g.fx, g.imageUrl, g.effectUrl)
                Row(Modifier.clip(RoundedCornerShape(28.dp)).background(Brush.horizontalGradient(listOf(Color(0xE6FF2FA8), Color(0xCC9B3DFF))))
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.example.ui.social.Avatar(g.fromAvatar, g.fromName.ifBlank { "?" }, 38)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.widthIn(max = 220.dp)) {
                        Text("สายเปย์มาแล้ว~ 💝", color = Color(0xFFFFE6F4), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(g.fromName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("ส่ง ${g.giftName} เพื่อเปิดใจกับคุณ", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(18.dp))
                AnimatedVisibility(phase >= 1, enter = fadeIn() + slideInVertically { it / 2 }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Countdown(left)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Pill("ปฏิเสธ", listOf(Color(0xFF5B6475), Color(0xFF3A4150)), phase == 1) { answer(false) }
                            Pill("ยอมรับ", listOf(Color(0xFFFF2FA8), Color(0xFFFF7A59)), phase == 1) { answer(true) }
                        }
                        if (phase == 2 && note.isBlank()) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.padding(top = 12.dp).size(22.dp))
                        if (note.isNotBlank()) Text(note, color = Color(0xFFFFD45C), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun Countdown(left: Int) {
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.18f), style = Stroke(6.dp.toPx()))
            drawArc(if (left <= 3) Color(0xFFFF5F6D) else Color(0xFFFFD45C), -90f, 360f * left / ACCEPT_SECONDS, false, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        }
        Text("$left", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun Pill(label: String, colors: List<Color>, enabled: Boolean, onClick: () -> Unit) {
    Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
        modifier = Modifier.width(130.dp).clip(RoundedCornerShape(30.dp)).background(Brush.horizontalGradient(colors.map { if (enabled) it else it.copy(alpha = 0.5f) }))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 13.dp))
}

@Composable
private fun RefundFx(r: PayResult, onDone: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(r.id) {
        val g = r.voiceGender.ifBlank { listOf("male", "female").random() }
        PayVoicePlayer.playRandom(ctx, "refund", g, "เพชรกลับมาแล้วจ้า~")
    }
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDone)) { DiamondsBack(onDone) }
    }
}

/** Little round badge used on the swipe page while waiting for the answer. */
@Composable
fun WaitingBadge(name: String, secondsLeft: Int, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xCC2B1660)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF2FA8)))
        Spacer(Modifier.width(6.dp))
        Text("รอ $name ตอบรับ… $secondsLeft", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
