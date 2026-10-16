package com.example.ui.social

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.social.NearbyRepo
import com.example.ui.components.dialogNavPadding
import com.example.ui.components.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val APink = Color(0xFFFF2FA8)
private val AViolet = Color(0xFF9B3DFF)

/**
 * Anonymous 60-second chat after a shake match. No real names/photos, no countdown on screen.
 *  - 0–20 s: no heart · 20 s: my half heart appears (the other person gets the other half) and can be tapped
 *  - last 20 s: the half heart beats gently + a hint · last 10 s: it shakes harder and faster
 *  - both tapped: the halves fly together into one heart, then both real profiles show and the normal chat opens
 *  - 60 s without a mutual heart: the chat ends (no extra time). The server owns the clock and the result.
 */
@Composable
fun AnonShakeChatDialog(room: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var st by remember { mutableStateOf<NearbyRepo.AnonState?>(null) }
    val msgs = remember { mutableStateListOf<NearbyRepo.AnonMsg>() }
    var syncedElapsed by remember { mutableLongStateOf(0L) }
    var syncedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var heartBusy by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var openChat by remember { mutableStateOf<String?>(null) }
    var revealDone by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    fun apply(s: NearbyRepo.AnonState) {
        st = s; syncedElapsed = s.elapsedMs; syncedAt = System.currentTimeMillis()
        s.messages.forEach { m -> if (msgs.none { it.at == m.at && it.text == m.text && it.mine == m.mine }) msgs.add(m) }
    }
    // server poll (1 s) + local clock (100 ms) between polls
    LaunchedEffect(room) {
        while (true) {
            val last = msgs.maxOfOrNull { it.at } ?: 0L
            NearbyRepo.anonGet(room, last)?.let { apply(it) }
            if (st?.state == "matched" || st?.state == "ended") break
            delay(1000)
        }
    }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(100) } }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1) }

    val s = st
    val duration = s?.durationMs ?: 60_000L
    val elapsed = if (s == null) 0L else (syncedElapsed + (now - syncedAt)).coerceAtMost(duration)
    val heartAt = s?.heartAfterMs ?: 20_000L
    val left = duration - elapsed
    val ended = s?.state == "ended" || (s?.state == "active" && left <= 0)
    val matched = s?.state == "matched"

    Dialog(onDismissRequest = { if (!matched && !ended) confirmLeave = true else onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF12082B), Color(0xFF2A0F55), Color(0xFF3B1060))))) {
            Column(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding().imePadding()) {
                // top bar: who I talk to (alias only) — no timer on purpose
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0x55FFFFFF), Color(0x22FFFFFF)))), contentAlignment = Alignment.Center) {
                        Text(s?.otherAlias?.takeLast(2)?.trim() ?: "❔", fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (matched) (s?.other?.displayName ?: "") else (s?.otherAlias ?: tr("กำลังเชื่อมต่อ…")), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(tr("แชทนิรนามจากการเขย่า · คุณคือ ") + (s?.myAlias ?: ""), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                    Text("✕", color = Color.White, fontSize = 18.sp, modifier = Modifier.clip(CircleShape).clickable { if (!matched && !ended) confirmLeave = true else onClose() }.padding(10.dp))
                }
                // heart stage
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    when {
                        s == null -> CircularProgressIndicator(color = APink)
                        matched -> HeartMerge(s) { revealDone = true }
                        ended -> Text(tr("หมดเวลาแล้ว"), color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        elapsed < heartAt -> Text(tr("คุยกันให้สนุก 😊\nตัวตนจริงจะถูกซ่อนไว้"), color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, textAlign = TextAlign.Center)
                        else -> {
                            val phase = when { left <= 10_000 -> 2; left <= 20_000 -> 1; else -> 0 }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                HalfHeartButton(pressed = s.myHeart, phase = phase, appearedAgo = elapsed - heartAt) {
                                    if (!s.myHeart && !heartBusy) { heartBusy = true; scope.launch { NearbyRepo.anonHeart(room)?.let { apply(it) }; heartBusy = false } }
                                }
                                Text(when {
                                    s.myHeart -> tr("ส่งใจแล้ว รออีกฝ่าย…")
                                    phase >= 1 -> tr("ใกล้หมดเวลาแล้ว! กดหัวใจถ้าอยากรู้จักกัน")
                                    else -> tr("แตะหัวใจครึ่งซีก ถ้าอยากรู้จักอีกฝ่าย")
                                }, color = if (phase >= 1 && !s.myHeart) Color(0xFFFFC94D) else Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                // messages
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(msgs) { m ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                            Text(m.text, color = Color.White, fontSize = 15.sp, modifier = Modifier.widthIn(max = 280.dp)
                                .clip(RoundedCornerShape(18.dp)).background(if (m.mine) Brush.linearGradient(listOf(APink, AViolet)) else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x26FFFFFF))))
                                .padding(horizontal = 14.dp, vertical = 9.dp))
                        }
                    }
                }
                // input / end state
                when {
                    ended -> Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr("ยังไม่ได้กดหัวใจตรงกัน แชทนี้จบแล้ว"), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(tr("เขย่าหาคนใหม่"), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(22.dp))
                            .background(Brush.horizontalGradient(listOf(APink, AViolet))).clickable(onClick = onClose).padding(horizontal = 28.dp, vertical = 12.dp))
                    }
                    matched -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        if (revealDone) Text(tr("เริ่มแชท"), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(22.dp))
                            .background(Brush.horizontalGradient(listOf(APink, AViolet))).clickable { openChat = s?.chatId }.padding(horizontal = 36.dp, vertical = 12.dp))
                    }
                    else -> Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(value = input, onValueChange = { input = it.take(300) }, singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(APink),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                val t = input.trim(); if (t.isNotEmpty() && !sending) { input = ""; sending = true
                                    scope.launch { if (!NearbyRepo.anonSend(room, t)) input = t; else NearbyRepo.anonGet(room, msgs.maxOfOrNull { it.at } ?: 0L)?.let { apply(it) }; sending = false } }
                            }),
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Color(0x22FFFFFF)).border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                            decorationBox = { inner -> Box { if (input.isEmpty()) Text(tr("พิมพ์ข้อความ…"), color = Color.White.copy(alpha = 0.45f), fontSize = 15.sp); inner() } })
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(APink, AViolet))).clickable(enabled = input.isNotBlank() && !sending) {
                            val t = input.trim(); input = ""; sending = true
                            scope.launch { if (!NearbyRepo.anonSend(room, t)) input = t; else NearbyRepo.anonGet(room, msgs.maxOfOrNull { it.at } ?: 0L)?.let { apply(it) }; sending = false }
                        }, contentAlignment = Alignment.Center) { Text("➤", color = Color.White, fontSize = 18.sp) }
                    }
                }
            }
        }
    }
    // a matched pair opens the normal chat right away (after the merge effect)
    LaunchedEffect(revealDone) { if (revealDone) { delay(1500); if (openChat == null) openChat = st?.chatId?.ifBlank { null } } }
    openChat?.let { RealChatRoomDialog(it) { openChat = null; onClose() } }
    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false },
        title = { Text(tr("ออกจากแชทนิรนาม?")) }, text = { Text(tr("แชทนี้จะจบทันที และจะไม่รู้ว่าอีกฝ่ายเป็นใคร")) },
        confirmButton = { TextButton(onClick = { confirmLeave = false; scope.launch { NearbyRepo.anonLeave(room) }; onClose() }) { Text(tr("ออก"), color = APink) } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(tr("คุยต่อ")) } })
}

/** My half (left) of the heart. phase 0 = still, 1 = gentle beat (last 20 s), 2 = strong fast shake (last 10 s). */
@Composable
private fun HalfHeartButton(pressed: Boolean, phase: Int, appearedAgo: Long, onTap: () -> Unit) {
    val appear = remember { Animatable(if (appearedAgo > 600) 1f else 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.45f)) }
    val inf = rememberInfiniteTransition(label = "half")
    val beat by inf.animateFloat(1f, if (phase == 2) 1.16f else 1.08f, infiniteRepeatable(tween(if (phase == 2) 260 else 700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "beat")
    val shake by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(90, easing = LinearEasing), RepeatMode.Reverse), label = "shake")
    val scale = appear.value * if (phase >= 1) beat else 1f
    Canvas(Modifier.size(width = 110.dp, height = 96.dp).graphicsLayer {
        scaleX = scale; scaleY = scale
        if (phase == 2) { rotationZ = shake * 9f; translationX = shake * 6f }
    }.clip(RoundedCornerShape(12.dp)).clickable(onClick = onTap)) {
        val c = if (pressed) listOf(Color(0xFFFF5CA8), APink) else listOf(Color(0xFFFF9ACB), Color(0xFFE0569E))
        drawHalfHeart(left = true, brush = Brush.linearGradient(c), glow = pressed)
        // dashed outline of the other half — it belongs to the other person
        drawHalfHeart(left = false, brush = SolidColor(Color(0x33FFFFFF)), glow = false, outline = true)
    }
}

/** Both halves fly together into one heart, burst, then both real profiles appear. */
@Composable
private fun HeartMerge(s: NearbyRepo.AnonState, onDone: () -> Unit) {
    val join = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        join.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        pop.animateTo(1.35f, tween(220)); pop.animateTo(1f, spring(dampingRatio = 0.4f))
        reveal.animateTo(1f, tween(500))
        onDone()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RevealFace(s.me, reveal.value)
        Canvas(Modifier.size(width = 120.dp, height = 104.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
            val gap = (1f - join.value) * size.width * 0.55f
            translateX(-gap) { drawHalfHeart(true, Brush.linearGradient(listOf(Color(0xFFFF5CA8), APink)), glow = join.value > 0.95f) }
            translateX(gap) { drawHalfHeart(false, Brush.linearGradient(listOf(APink, AViolet)), glow = join.value > 0.95f) }
            if (join.value >= 1f) for (k in 0 until 10) { // sparkles
                val a = k / 10f * 6.283f; val rr = size.minDimension * (0.55f + (pop.value - 1f))
                drawCircle(Color(0xFFFFE08A).copy(alpha = 0.8f), 3.dp.toPx(), Offset(size.width / 2 + kotlin.math.cos(a) * rr, size.height / 2 + kotlin.math.sin(a) * rr))
            }
        }
        RevealFace(s.other, reveal.value)
    }
}

@Composable
private fun RevealFace(p: com.example.social.RealProfile?, a: Float) {
    Column(Modifier.width(76.dp).graphicsLayer { alpha = a; scaleX = 0.7f + 0.3f * a; scaleY = 0.7f + 0.3f * a }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clip(CircleShape).background(Brush.linearGradient(listOf(APink, AViolet))).padding(2.dp)) { Avatar(p?.avatar ?: "", p?.displayName ?: "?", 56) }
        Text(p?.displayName ?: "", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

private inline fun DrawScope.translateX(dx: Float, block: DrawScope.() -> Unit) { drawContext.transform.translate(dx, 0f); block(); drawContext.transform.translate(-dx, 0f) }

private fun DrawScope.heartPath(): Path {
    val w = size.width; val h = size.height
    return Path().apply {
        moveTo(w / 2, h * 0.95f)
        cubicTo(w * 0.08f, h * 0.62f, -w * 0.05f, h * 0.22f, w * 0.26f, h * 0.08f)
        cubicTo(w * 0.40f, h * 0.02f, w * 0.48f, h * 0.12f, w / 2, h * 0.24f)
        cubicTo(w * 0.52f, h * 0.12f, w * 0.60f, h * 0.02f, w * 0.74f, h * 0.08f)
        cubicTo(w * 1.05f, h * 0.22f, w * 0.92f, h * 0.62f, w / 2, h * 0.95f)
        close()
    }
}

private fun DrawScope.drawHalfHeart(left: Boolean, brush: Brush, glow: Boolean, outline: Boolean = false) {
    val path = heartPath()
    clipRect(left = if (left) 0f else size.width / 2, right = if (left) size.width / 2 else size.width) {
        if (glow) drawPath(path, Color(0x66FF2FA8), style = Stroke(10.dp.toPx()))
        if (outline) drawPath(path, brush, style = Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        else drawPath(path, brush)
    }
    if (!outline) drawLine(Color(0x55FFFFFF), Offset(size.width / 2, size.height * 0.24f), Offset(size.width / 2, size.height * 0.95f), 1.5.dp.toPx())
}

