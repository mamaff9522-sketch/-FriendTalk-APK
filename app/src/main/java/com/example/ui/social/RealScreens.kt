package com.example.ui.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.*

/** Live tab: there is no live-streaming backend yet, so no fake streamers — an honest, styled empty state. */
@Composable
fun RealLiveScreen() {
    var info by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("ห้องไลฟ์สดที่กำลังออกอากาศ 🔴", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.padding(end = 72.dp))
            Text("ดูไลฟ์สุดฮิต พบสตรีมเมอร์ที่คุณชอบได้ที่นี่", color = Slate300, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = Slate900, border = BorderStroke(1.dp, Purple500.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
                Column {
                    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(Color(0xFFFF8A5B), Color(0xFF8E3B8E), Color(0xFF1D1B4A)))), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📺", fontSize = 64.sp)
                            Text("ยังไม่มีใครไลฟ์ตอนนี้", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("ระบบไลฟ์สดกำลังจะเปิดเร็ว ๆ นี้", color = Slate200, fontSize = 13.sp)
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("🎵 ดนตรี", "🎤 ร้องเพลง", "💗 พูดคุย", "👥 มิตรภาพ").forEach {
                            Surface(shape = RoundedCornerShape(18.dp), color = Slate800, border = BorderStroke(1.dp, Slate700)) { Text(it, color = White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(22.dp).background(Pink500)); Spacer(Modifier.width(8.dp))
                Text("ไลฟ์สดแนะนำสำหรับคุณ", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Text("ยังไม่มีไลฟ์แนะนำ", color = Slate400, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp).size(width = 64.dp, height = 140.dp).clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(Pink500, Pink600))).clickable { info = true }, contentAlignment = Alignment.Center) { Text("+", color = White, fontSize = 40.sp) }
    }
    if (info) AlertDialog(onDismissRequest = { info = false }, confirmButton = { TextButton(onClick = { info = false }) { Text("ตกลง") } },
        title = { Text("เริ่มไลฟ์") }, text = { Text("การไลฟ์สดยังไม่เปิดให้ใช้ในเวอร์ชันนี้") })
}

/** Video tab: vertical full-screen pager over real image posts (no video upload yet), right action rail. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RealVerticalFeed() {
    val state = rememberRealFeedState()
    LaunchedEffect(Unit) { if (state.posts.isEmpty()) state.refresh() }
    val media = state.posts.filter { it.imageUrls.isNotEmpty() }
    if (media.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("▶", color = Pink400, fontSize = 48.sp)
                Text(if (state.loading) "กำลังโหลด…" else "ยังไม่มีคลิป/รูปภาพให้ดู", color = Slate300, fontSize = 15.sp)
                Text("วิดีโอยังไม่เปิดให้อัปโหลด ตอนนี้แสดงโพสต์รูปจริงแบบเต็มจอ", color = Slate400, fontSize = 12.sp)
            }
        }
        return
    }
    val pager = rememberPagerState { media.size }
    LaunchedEffect(pager.currentPage) { if (pager.currentPage >= media.size - 2) state.loadMore() }
    RealCommentsDialog(state)
    VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
        val p = media[i]
        var showAuthor by remember { mutableStateOf(false) }
        if (showAuthor) UserProfileDialog(p.authorId) { showAuthor = false }
        Box(Modifier.fillMaxSize()) {
            AsyncImage(model = p.imageUrls.first(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xCC000000)))))
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(64.dp).clip(CircleShape).border(3.dp, Pink500, CircleShape).padding(3.dp).clickable { showAuthor = true }) { Avatar(p.authorAvatar, p.authorName, 58) }
                RailButton(if (p.likedByMe) "❤️" else "🤍", "${p.likeCount}") { state.toggleLike(p) }
                RailButton("💬", "${p.commentCount}") { state.commentsFor = p }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 96.dp, bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showAuthor = true }) {
                    Box(Modifier.size(48.dp).clip(CircleShape).border(2.dp, Pink500, CircleShape).padding(2.dp)) { Avatar(p.authorAvatar, p.authorName, 44) }
                    Spacer(Modifier.width(8.dp)); Text(p.authorName, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (p.text.isNotBlank()) Text(p.text, color = White, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun RailButton(icon: String, count: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xCC111827)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(icon, fontSize = 26.sp) }
        Text(count, color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
