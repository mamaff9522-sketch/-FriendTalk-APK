package com.example.ui.social

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.social.RealProfile
import com.example.social.SocialRepo
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/** Real swipe matching (friendtalk-brain /swipe). No mock users. */
@Composable
fun RealSwipeView() {
    val scope = rememberCoroutineScope()
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    var cards by remember { mutableStateOf<List<RealProfile>?>(null) }
    var matched by remember { mutableStateOf<Pair<RealProfile, String?>?>(null) }
    var msg by remember { mutableStateOf("") }
    val offsetX = remember { Animatable(0f) }

    suspend fun reload() { cards = SocialRepo.candidates() }
    LaunchedEffect(Unit) { reload() }

    fun act(p: RealProfile, action: String) {
        scope.launch {
            val r = SocialRepo.swipe(p.uid, action)
            com.example.ads.AdsManager.onSwipe(activity)
            offsetX.snapTo(0f)
            cards = cards?.drop(1)
            if (!r.ok) msg = "บันทึกไม่สำเร็จ ลองใหม่"
            else if (r.matched) matched = p to r.chatId
            if (cards?.isEmpty() == true) reload()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val top = cards?.firstOrNull()
        when {
            cards == null -> CircularProgressIndicator(color = Pink500)
            top == null -> {
                Text("ยังไม่มีผู้ใช้ใหม่ให้ปัด", color = Slate300, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { cards = null; scope.launch { reload() } }) { Text("โหลดใหม่", color = White) }
            }
            else -> {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    cards!!.getOrNull(1)?.let { SwipeCard(it, Modifier.fillMaxSize().padding(8.dp).graphicsLayer { scaleX = 0.95f; scaleY = 0.95f }) }
                    SwipeCard(top, Modifier.fillMaxSize()
                        .graphicsLayer { translationX = offsetX.value; rotationZ = offsetX.value / 40f }
                        .pointerInput(top.uid) {
                            detectDragGestures(
                                onDragEnd = {
                                    when {
                                        offsetX.value > 300f -> act(top, "like")
                                        offsetX.value < -300f -> act(top, "pass")
                                        else -> scope.launch { offsetX.animateTo(0f) }
                                    }
                                },
                                onDrag = { ch, amt -> ch.consume(); scope.launch { offsetX.snapTo(offsetX.value + amt.x) } }
                            )
                        })
                    if (offsetX.value > 80f) Text("LIKE 💚", color = Emerald400, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).padding(24.dp))
                    if (offsetX.value < -80f) Text("ผ่าน ✖", color = Rose500, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { act(top, "pass") }, colors = ButtonDefaults.buttonColors(containerColor = Slate700)) { Text("✖ ผ่าน") }
                    Button(onClick = { act(top, "superlike") }, colors = ButtonDefaults.buttonColors(containerColor = Cyan400)) { Text("⭐ ซูเปอร์ไลก์", color = Color.Black) }
                    Button(onClick = { act(top, "like") }, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("💚 ไลก์") }
                }
            }
        }
        if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp)
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
                        colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("เริ่มแชท") }
                    TextButton(onClick = { matched = null }) { Text("ปัดต่อ", color = Slate300) }
                }
            }
        }
    }
}

@Composable
private fun SwipeCard(p: RealProfile, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Slate800)) {
        if (p.avatar.isNotBlank()) AsyncImage(model = p.avatar, contentDescription = p.displayName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Avatar("", p.displayName, 120) }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))).padding(16.dp)) {
            Text(p.displayName + (p.age?.let { ", $it" } ?: ""), color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (p.bio.isNotBlank()) Text(p.bio, color = Slate300, fontSize = 13.sp, maxLines = 3)
            if (p.interests.isNotEmpty()) Text(p.interests.joinToString(" · "), color = Pink400, fontSize = 12.sp)
            p.similarity?.let { SharedInterests(it) }
        }
    }
}
