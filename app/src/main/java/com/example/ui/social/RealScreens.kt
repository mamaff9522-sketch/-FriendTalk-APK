package com.example.ui.social

import com.example.ui.components.tr
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
import com.example.ui.components.ft.Text
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

/** Live tab: there is no live-streaming backend yet, so no fake streamers — a polished "coming soon" page with labelled AI samples. */
@Composable
fun RealLiveScreen() {
    var info by remember { mutableStateOf(false) }
    var cat by remember { mutableStateOf("all") }
    LaunchedEffect(Unit) { com.example.ui.feed.Showcase.load() }
    val dark = AppThemeMode.dark
    val ink = AppThemeMode.text
    val muted = AppThemeMode.muted
    val neon = Color(0xFFFF2FA8); val violet = Color(0xFF9B3DFF)
    val grad = Brush.horizontalGradient(listOf(neon, violet))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
        // header: title + LIVE pill · start-live button
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("ไลฟ์สด"), color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                    Spacer(Modifier.width(8.dp))
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF2D55)).padding(horizontal = 7.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(PureWhite)); Spacer(Modifier.width(4.dp))
                        Text("LIVE", color = PureWhite, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
                Text(tr("ดูไลฟ์ ส่งของขวัญ และคุยกับสตรีมเมอร์แบบเรียลไทม์"), color = muted, fontSize = 13.sp)
            }
            Row(Modifier.clip(RoundedCornerShape(20.dp)).background(grad).clickable { info = true }.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🎥", fontSize = 14.sp); Spacer(Modifier.width(6.dp))
                Text(tr("เริ่มไลฟ์"), color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        // categories
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to tr("ทั้งหมด"), "music" to tr("🎵 ดนตรี"), "sing" to tr("🎤 ร้องเพลง"), "talk" to tr("💬 พูดคุย"), "game" to tr("🎮 เกม"), "friend" to tr("👥 มิตรภาพ")).forEach { (k, l) ->
                val on = cat == k
                Box(Modifier.clip(RoundedCornerShape(18.dp)).then(if (on) Modifier.background(grad) else Modifier.background(AppThemeMode.cardSolid).border(1.dp, AppThemeMode.divider, RoundedCornerShape(18.dp)))
                    .clickable { cat = k }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(l, color = if (on) PureWhite else ink, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        // hero: coming soon
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(1.7f).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1A1145), Color(0xFF3B1466), Color(0xFF7A1E6E))))) {
            Box(Modifier.align(Alignment.TopEnd).offset(40.dp, (-40).dp).size(200.dp).background(Brush.radialGradient(listOf(Color(0x66FF2FA8), Color.Transparent))))
            Box(Modifier.align(Alignment.BottomStart).offset((-50).dp, 50.dp).size(220.dp).background(Brush.radialGradient(listOf(Color(0x5533C8FF), Color.Transparent))))
            Column(Modifier.align(Alignment.CenterStart).padding(20.dp)) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0x33FFFFFF)).border(1.dp, Color(0x66FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(grad), contentAlignment = Alignment.Center) { Text("📡", fontSize = 18.sp) }
                }
                Spacer(Modifier.height(12.dp))
                Text(tr("ไลฟ์สดกำลังจะมาเร็ว ๆ นี้"), color = PureWhite, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(tr("ตอนนี้ยังไม่มีใครไลฟ์ — เตรียมพบกับห้องไลฟ์ ของขวัญ และแชทสด"), color = PureWhite.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        // sample rooms (AI, clearly labelled)
        val samples = com.example.ui.feed.Showcase.live
        if (com.example.ui.feed.Showcase.enabled && samples.isNotEmpty()) {
            SectionTitle(tr("ตัวอย่างห้องไลฟ์"), tr("ภาพตัวอย่างจาก AI"), ink, muted)
            samples.take(6).chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { item ->
                        Box(Modifier.weight(1f).aspectRatio(0.78f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF16123A)).clickable { info = true }) {
                            AsyncImage(model = item.model, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color.Transparent, Color(0xCC000000)))))
                            Text(tr("ตัวอย่าง AI"), color = PureWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp))
                            Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                                Text(item.name.ifBlank { tr("ห้องตัวอย่าง") }, color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(tr("เร็ว ๆ นี้"), color = PureWhite.copy(alpha = 0.75f), fontSize = 11.sp)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        // what live will offer
        SectionTitle(tr("สิ่งที่จะมีในไลฟ์สด"), null, ink, muted)
        listOf(Triple("🎁", tr("ส่งของขวัญให้สตรีมเมอร์"), tr("ของขวัญแสดงบนจอแบบเคลื่อนไหว")),
            Triple("💬", tr("แชทสดระหว่างไลฟ์"), tr("คุยกับสตรีมเมอร์และผู้ชมคนอื่นแบบเรียลไทม์")),
            Triple("💎", tr("สตรีมเมอร์มีรายได้"), tr("รับเพชรจากของขวัญ แลกเป็นรายได้ได้"))).forEach { (ic, t, d) ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppThemeMode.cardSolid)
                .border(1.dp, AppThemeMode.divider, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(neon.copy(alpha = 0.18f), violet.copy(alpha = 0.18f)))), contentAlignment = Alignment.Center) { Text(ic, fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
                Column { Text(t, color = ink, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(d, color = muted, fontSize = 12.sp) }
            }
        }
    }
    if (info) AlertDialog(onDismissRequest = { info = false }, confirmButton = { TextButton(onClick = { info = false }) { Text(tr("ตกลง")) } },
        title = { Text(tr("ไลฟ์สดยังไม่เปิด")) }, text = { Text(tr("ระบบไลฟ์สดยังไม่เปิดให้ใช้ในเวอร์ชันนี้ — ภาพในหน้านี้เป็นตัวอย่างจาก AI")) })
}

@Composable
private fun SectionTitle(title: String, sub: String?, ink: Color, muted: Color) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        if (sub != null) Text(sub, color = muted, fontSize = 12.sp)
    }
}

/** Video tab: vertical full-screen pager over real image posts (no video upload yet), right action rail. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RealVerticalFeed() {
    val state = rememberRealFeedState()
    LaunchedEffect(Unit) { if (state.posts.isEmpty()) state.refresh() }
    val media = state.posts.filter { it.imageUrls.isNotEmpty() }
    Box(Modifier.fillMaxSize()) { RealVerticalFeedBody(state, media) ; com.example.ads.PlacementBanner("video", Modifier.align(Alignment.BottomCenter)) }
}

@Composable
private fun RealVerticalFeedBody(state: RealFeedState, media: List<com.example.social.FeedPost>) {
    if (media.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("▶", color = Pink400, fontSize = 48.sp)
                Text(if (state.loading) tr("กำลังโหลด…") else tr("ยังไม่มีคลิป/รูปภาพให้ดู"), color = Slate300, fontSize = 15.sp)
                Text(tr("วิดีโอยังไม่เปิดให้อัปโหลด ตอนนี้แสดงโพสต์รูปจริงแบบเต็มจอ"), color = Slate400, fontSize = 12.sp)
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
                    Spacer(Modifier.width(8.dp)); Text(p.authorName, color = PureWhite, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (p.text.isNotBlank()) Text(p.text, color = PureWhite, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun RailButton(icon: String, count: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xCC111827)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(icon, fontSize = 26.sp) }
        Text(count, color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
