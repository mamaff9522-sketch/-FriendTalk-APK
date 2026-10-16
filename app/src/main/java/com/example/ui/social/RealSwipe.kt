package com.example.ui.social

import com.example.ui.components.tr
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.example.ui.components.ft.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.social.PayGift
import com.example.social.PayOpenRepo
import com.example.social.PayResult
import com.example.social.RealProfile
import com.example.social.SocialRepo
import com.example.ui.payopen.PayOpenEvents
import com.example.ui.payopen.WaitingBadge
import com.example.ui.random.RandomCard
import com.example.ui.random.RandomCardView
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ปัดหาเพื่อน (friendtalk-brain /swipe): only people online right now, each shown with their สุ่มหาเพื่อน intro card
 * (photo / video / voice / name frame / effect). Swipe left = ผ่าน, right = ไลก์; 💝 เปย์เปิดใจ = send a gift — the other person has
 * 10 seconds to accept (→ friends + chat) or the diamonds come back automatically. No mock users.
 */
@Composable
fun RealSwipeView() {
    val scope = rememberCoroutineScope()
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    var cards by remember { mutableStateOf<List<Pair<RealProfile, RandomCard?>>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var matched by remember { mutableStateOf<Pair<RealProfile, String?>?>(null) }
    var msg by remember { mutableStateOf("") }
    var payFor by remember { mutableStateOf<RealProfile?>(null) }
    var waiting by remember { mutableStateOf<Triple<String, String, Long>?>(null) } // id, name, local end time
    val offsetX = remember { Animatable(0f) }

    suspend fun reload() { val d = SocialRepo.swipeDeck(); failed = d == null; cards = d ?: emptyList() }
    LaunchedEffect(Unit) { reload() }

    fun next() { scope.launch { offsetX.snapTo(0f) }; cards = cards?.drop(1); if (cards?.isEmpty() == true) scope.launch { reload() } }
    fun act(p: RealProfile, action: String) {
        scope.launch {
            val r = SocialRepo.swipe(p.uid, action)
            com.example.ads.AdsManager.onSwipe(activity)
            offsetX.snapTo(0f)
            cards = cards?.drop(1)
            if (!r.ok) msg = tr("บันทึกไม่สำเร็จ ลองใหม่")
            else if (r.matched) matched = p to r.chatId
            if (cards?.isEmpty() == true) reload()
        }
    }

    // While my gift waits for an answer: ask the server too (works even before the realtime rule is deployed); the result plays once.
    LaunchedEffect(waiting?.first) {
        val w = waiting ?: return@LaunchedEffect
        while (true) {
            delay(2000)
            val s = PayOpenRepo.status(w.first)
            if (s != null && s.state != "pending" && s.state.isNotBlank()) {
                PayOpenEvents.deliver(PayResult(s.id, s.state, s.chatId, s.price, s.giftName, s.voiceGender, System.currentTimeMillis()))
                break
            }
            if (System.currentTimeMillis() > w.third + 60_000) break
        }
        waiting = null
    }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(waiting != null) { while (waiting != null) { delay(500); tick++ } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Emerald400))
            Spacer(Modifier.width(6.dp))
            Text(tr("เฉพาะคนที่ออนไลน์อยู่ตอนนี้") + (cards?.let { if (it.isNotEmpty()) " · ${it.size}+" else "" } ?: ""), color = Slate300, fontSize = 12.sp)
        }
        waiting?.let { w -> key(tick) { WaitingBadge(w.second, ((w.third - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0), Modifier.padding(top = 6.dp)) } }
        Spacer(Modifier.height(8.dp))
        val top = cards?.firstOrNull()
        when {
            cards == null -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pink500) }
            top == null -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("🌙", fontSize = 44.sp)
                Text(if (failed) tr("โหลดไม่สำเร็จ ลองใหม่อีกครั้ง") else tr("ตอนนี้ยังไม่มีคนออนไลน์ให้ปัด"), color = Slate300, fontSize = 14.sp)
                Text(tr("คนที่คุณกดผ่านจะกลับมาให้ปัดอีกครั้งหลัง 24 ชั่วโมง"), color = Slate400, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { cards = null; scope.launch { reload() } }) { Text(tr("โหลดใหม่"), color = White) }
            }
            else -> {
                val (p, card) = top
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    cards!!.getOrNull(1)?.let { (p2, c2) ->
                        RandomCardView(c2 ?: RandomCard(intro = p2.bio), p2.displayName, p2.age, p2.avatar, Modifier.fillMaxHeight().padding(10.dp)
                            .graphicsLayer { scaleX = 0.94f; scaleY = 0.94f; alpha = 0.7f }, autoplay = false)
                    }
                    key(p.uid) {
                        Box(Modifier.fillMaxHeight()
                            .graphicsLayer { translationX = offsetX.value; rotationZ = offsetX.value / 40f }
                            .pointerInput(p.uid) {
                                detectDragGestures(
                                    onDragEnd = {
                                        when {
                                            offsetX.value > 300f -> act(p, "like")
                                            offsetX.value < -300f -> act(p, "pass")
                                            else -> scope.launch { offsetX.animateTo(0f) }
                                        }
                                    },
                                    onDrag = { ch, amt -> ch.consume(); scope.launch { offsetX.snapTo(offsetX.value + amt.x) } }
                                )
                            }) {
                            RandomCardView(card ?: RandomCard(intro = p.bio), p.displayName, p.age, p.avatar, Modifier.fillMaxHeight())
                            Row(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x66000000)).padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(Emerald400)); Spacer(Modifier.width(4.dp))
                                Text(tr("ออนไลน์"), color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            // 🎁 shortcut kept from the old card: chat gift ("I'd like to talk with you", charged only when accepted)
                            Box(Modifier.align(Alignment.BottomEnd).padding(10.dp)) { GiftToUserButton(p.uid, compact = true) }
                        }
                    }
                    if (offsetX.value > 80f) Text("LIKE 💚", color = Emerald400, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).padding(24.dp))
                    if (offsetX.value < -80f) Text(tr("ผ่าน ✖"), color = Rose500, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp))
                }
                // info that was on the old card
                Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (p.distance.isNotBlank()) "📍 " + p.distance + " " + tr("จากคุณ") else "📍 " + tr("ไม่ทราบระยะ (เปิดเรดาร์เพื่อดูระยะ)"), color = Slate300, fontSize = 12.sp)
                    if (p.interests.isNotEmpty()) Text(p.interests.joinToString(" · "), color = Pink400, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    p.similarity?.let { SharedInterests(it, title = false) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { act(p, "pass") }, colors = ButtonDefaults.buttonColors(containerColor = Slate700)) { Text(tr("✖ ผ่าน")) }
                    Button(onClick = { payFor = p }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2FA8))) { Text(tr("💝 เปย์เปิดใจ"), fontWeight = FontWeight.Bold) }
                    Button(onClick = { act(p, "like") }, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text(tr("💚 ไลก์")) }
                }
            }
        }
        if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }

    payFor?.let { p ->
        PayOpenPicker(p, onClose = { payFor = null }) { id ->
            payFor = null
            waiting = Triple(id, p.displayName, System.currentTimeMillis() + 15_000)
            // counts as a super-like on the deck (they don't come back, and a like back still makes a match)
            scope.launch { runCatching { SocialRepo.swipe(p.uid, "superlike") } }
            if (cards?.firstOrNull()?.first?.uid == p.uid) next()
        }
    }

    matched?.let { (p, chatId) ->
        Dialog(onDismissRequest = { matched = null }) {
            Surface(shape = RoundedCornerShape(24.dp), color = Slate900) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("It's a match! 💞", color = Pink400, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Avatar(p.avatar, p.displayName, 96)
                    Text("คุณกับ ${p.displayName} ไลก์กันและกัน", color = White, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { matched = null; SocialRepo.openChat.value = chatId }, enabled = chatId != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text(tr("เริ่มแชท")) }
                    TextButton(onClick = { matched = null }) { Text(tr("ปัดต่อ"), color = Slate300) }
                }
            }
        }
    }
}

/** Gift picker for เปย์เปิดใจ: pictures + diamond prices (live from the admin catalog), then a confirm step before anything is charged. */
@Composable
private fun PayOpenPicker(to: RealProfile, onClose: () -> Unit, onSent: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<Pair<List<PayGift>, Int>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<PayGift?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    suspend fun load() { val d = PayOpenRepo.catalog(); loadFailed = d == null; data = d }
    LaunchedEffect(Unit) { load() }
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF1C1033), modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.78f)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💝 " + tr("เปย์เปิดใจ"), color = PureWhite, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    data?.let { Text("💎 ${it.second}", color = Color(0xFF7AF5FF), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
                Text(tr("ส่งของขวัญให้") + " ${to.displayName} · " + tr("ถ้าไม่รับภายใน 10 วินาที เพชรคืนเต็มจำนวนอัตโนมัติ"), color = Slate300, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                val list = data?.first
                when {
                    data == null && !loadFailed -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pink500) }
                    list.isNullOrEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(if (loadFailed) tr("โหลดของขวัญไม่สำเร็จ (เซิร์ฟเวอร์อาจยังไม่อัปเดต)") else tr("ยังไม่มีของขวัญ"), color = Slate300, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    else -> LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list!!, key = { it.id }) { g ->
                            val sel = pick?.id == g.id
                            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(if (sel) Color(0x55FF2FA8) else Color(0x22FFFFFF))
                                .border(if (sel) 2.dp else 0.dp, if (sel) Color(0xFFFF2FA8) else Color.Transparent, RoundedCornerShape(16.dp))
                                .clickable { pick = g; err = "" }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                                    if (g.imageUrl.isNotBlank()) AsyncImage(model = g.imageUrl, contentDescription = g.name, modifier = Modifier.fillMaxSize())
                                    else Text(g.icon.ifBlank { "🎁" }, fontSize = 38.sp)
                                }
                                Text(g.name, color = PureWhite, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("💎 ${g.price}", color = Color(0xFF7AF5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (err.isNotBlank()) Text(err, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onClose, enabled = !busy, modifier = Modifier.weight(1f)) { Text(tr("ยกเลิก"), color = White) }
                    Button(onClick = { confirm = true }, enabled = pick != null && !busy, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2FA8))) { Text(pick?.let { tr("ส่ง") + " 💎${it.price}" } ?: tr("เลือกของขวัญ")) }
                }
            }
        }
    }
    val g = pick
    if (confirm && g != null) AlertDialog(
        onDismissRequest = { if (!busy) confirm = false },
        title = { Text(tr("ยืนยันการส่ง")) },
        text = { Text(tr("ส่ง") + " ${g.name} " + tr("ให้") + " ${to.displayName}\n" + tr("ระบบจะหัก") + " 💎${g.price} " + tr("ทันที ถ้าอีกฝ่ายไม่ยอมรับภายใน 10 วินาที เพชรจะคืนให้เต็มจำนวนอัตโนมัติ")) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val r = PayOpenRepo.send(to.uid, g)
                    busy = false; confirm = false
                    if (r.ok) onSent(r.id)
                    else { err = r.msg; if (r.code == "price_changed") { pick = null; load() } }
                }
            }) { Text(if (busy) tr("กำลังส่ง…") else tr("ยืนยัน"), color = Pink400, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirm = false }) { Text(tr("ยกเลิก")) } }
    )
}
