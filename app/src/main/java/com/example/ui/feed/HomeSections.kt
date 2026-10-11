package com.example.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.social.NearbyRepo
import com.example.social.NearbyUser
import com.example.ui.social.Avatar
import com.example.ui.social.UserProfileDialog
import com.example.ui.theme.*

/** Glassy rounded section card used across the redesigned screens. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Slate900.copy(alpha = 0.9f), border = BorderStroke(1.dp, Purple500.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) { Column(Modifier.padding(12.dp), content = content) }
}

@Composable
fun SectionHeader(icon: String, title: String, onSeeAll: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 18.sp); Spacer(Modifier.width(6.dp))
        Text(title, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (onSeeAll != null) Text("ดูทั้งหมด ›", color = Pink400, fontSize = 13.sp, modifier = Modifier.clickable { onSeeAll() })
    }
}

/** Home filter chips with icons (ทั้งหมด = feed; people chips open the หาเพื่อน page). */
@Composable
fun HomeChips(selected: String, onAll: () -> Unit, onPeople: () -> Unit) {
    val chips: List<Triple<String, ImageVector, Boolean>> = listOf(
        Triple("ทั้งหมด", Icons.Filled.Home, true), Triple("ใกล้คุณ", Icons.Filled.Place, false),
        Triple("ผู้ใช้ทั้งหมด", Icons.Filled.People, false), Triple("ออนไลน์", Icons.Filled.Favorite, false))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { (label, icon, isAll) ->
            val sel = isAll && (selected == "ทั้งหมด" || selected.isBlank())
            Surface(onClick = { if (isAll) onAll() else onPeople() }, shape = RoundedCornerShape(16.dp),
                color = if (sel) Color.Transparent else Slate900, border = if (sel) null else BorderStroke(1.dp, Slate700.copy(alpha = 0.7f))) {
                Row(Modifier.then(if (sel) Modifier.background(Brush.horizontalGradient(listOf(Pink500, Pink400))) else Modifier).padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = if (sel) White else if (label == "ออนไลน์") Pink400 else Slate300, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text(label, color = White, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

/** 'LIVE ตอนนี้' — no live backend yet, so an honest empty state (no fake streamers). */
@Composable
fun LiveNowSection(onSeeAll: () -> Unit) {
    GlassCard {
        SectionHeader("🔥", "LIVE ตอนนี้", onSeeAll)
        Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Purple600.copy(alpha = 0.35f), Pink600.copy(alpha = 0.25f)))),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📺", fontSize = 26.sp)
                Text("ยังไม่มีใครไลฟ์ตอนนี้", color = Slate200, fontSize = 13.sp)
            }
        }
    }
}

/** 'คนใกล้คุณ' story circles from the real radar (respects the radar distance and permission). */
@Composable
fun NearbyStoriesSection(onSeeAll: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(com.example.ui.social.hasLocPerm(ctx)) }
    var list by remember { mutableStateOf<List<NearbyUser>?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    var storyMsg by remember { mutableStateOf(false) }
    val radius = remember { NearbyRepo.loadRadius(ctx) }
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        com.example.ui.social.currentLocation(ctx)?.let { NearbyRepo.shareLocation(it.latitude, it.longitude) }
        list = NearbyRepo.search("", true, radius)?.nearby ?: emptyList()
    }
    GlassCard {
        SectionHeader("📍", "คนใกล้คุณ", onSeeAll)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp).clickable { storyMsg = true }) {
                Box(Modifier.size(68.dp).clip(CircleShape).border(3.dp, Brush.linearGradient(listOf(Purple500, Pink500)), CircleShape).background(Purple600.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Text("+", color = White, fontSize = 30.sp, fontWeight = FontWeight.Light)
                }
                Text("เพิ่มเรื่องราว", color = White, fontSize = 11.sp, maxLines = 1)
            }
            val l = list
            when {
                !granted -> Text("เปิดตำแหน่งในเรดาร์เพื่อดูคนใกล้คุณ", color = Slate400, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically).clickable { onSeeAll() })
                l == null -> Text("กำลังหา…", color = Slate400, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
                l.isEmpty() -> Text("ยังไม่มีใครใกล้คุณในระยะ ${NearbyRepo.radiusLabel(radius)}", color = Slate400, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
                else -> l.forEach { n ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp).clickable { open = n.profile.uid }) {
                        Box(Modifier.size(68.dp).clip(CircleShape).border(3.dp, Brush.linearGradient(listOf(Pink500, Purple500)), CircleShape).padding(4.dp)) { Avatar(n.profile.avatar, n.profile.displayName, 60) }
                        Text(n.profile.displayName, color = White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(n.distance.replace(" m", " ม.").replace(" km", " กม."), color = Slate400, fontSize = 10.sp)
                    }
                }
            }
        }
    }
    open?.let { UserProfileDialog(it) { open = null } }
    if (storyMsg) AlertDialog(onDismissRequest = { storyMsg = false }, confirmButton = { TextButton(onClick = { storyMsg = false }) { Text("ตกลง") } },
        title = { Text("เรื่องราว") }, text = { Text("ฟีเจอร์เรื่องราว (Stories) ยังไม่เปิดให้ใช้ — ตอนนี้โพสต์ในฟีดได้เลย") })
}

/** House promo ('ได้รับการสนับสนุน') pointing to the หาเพื่อน page; not a third-party ad. */
@Composable
fun FindFriendsPromo(onGo: () -> Unit) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Pink500, Purple600))), contentAlignment = Alignment.Center) { Text("FT", color = White, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("FriendTalk", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp); Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = Pink500) { Text("ได้รับการสนับสนุน", color = White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                }
                Text("ค้นพบเพื่อนใหม่ ใกล้คุณมากขึ้น 🌐", color = Slate300, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("พบผู้คนที่ใช่", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text("ใกล้กว่าที่คิด", color = Pink400, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text("📍 เริ่มต้นบทสนทนาใหม่วันนี้ บน FriendTalk", color = Slate300, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onGo, border = BorderStroke(1.5.dp, Pink500), shape = RoundedCornerShape(22.dp)) { Text("ค้นหาเลย →", color = White, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(Color(0xFFFF8A5B), Color(0xFF8E3B8E), Color(0xFF1D1B4A)))),
            contentAlignment = Alignment.BottomStart) {
            Text("🏰", fontSize = 64.sp, modifier = Modifier.align(Alignment.Center))
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0x99000000), modifier = Modifier.padding(8.dp)) { Text("📍 คนใกล้คุณกำลังรออยู่…", color = White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
        }
    }
}
