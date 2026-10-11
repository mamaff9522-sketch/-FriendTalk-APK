package com.example.ui.feed

import com.example.ui.components.tr
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
        if (onSeeAll != null) Text(tr("ดูทั้งหมด ›"), color = Pink400, fontSize = 13.sp, modifier = Modifier.clickable { onSeeAll() })
    }
}

/** Home chips = real filters over the real feed (applied to loaded posts; paging continues underneath). */
@Composable
fun HomeChips(selected: String, onSelect: (String) -> Unit) {
    val chips: List<Pair<String, ImageVector>> = listOf("all" to Icons.Filled.Home, "photos" to Icons.Filled.Image, "places" to Icons.Filled.Place, "liked" to Icons.Filled.Favorite)
    val labels = mapOf("all" to tr("ทั้งหมด"), "photos" to tr("รูปภาพ"), "places" to tr("มีตำแหน่ง"), "liked" to tr("ที่ฉันถูกใจ"))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { (k, icon) ->
            val sel = selected == k
            Surface(onClick = { onSelect(k) }, shape = RoundedCornerShape(16.dp),
                color = if (sel) Color.Transparent else Slate900, border = if (sel) null else BorderStroke(1.dp, Slate700.copy(alpha = 0.7f))) {
                Row(Modifier.then(if (sel) Modifier.background(Brush.horizontalGradient(listOf(Pink500, Pink400))) else Modifier).padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = if (sel) PureWhite else if (k == "liked") Pink400 else Slate300, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text(labels[k]!!, color = if (sel) PureWhite else White, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

/** 'LIVE ตอนนี้' — no live backend yet, so an honest empty state (no fake streamers). */
@Composable
fun LiveNowSection(onSeeAll: () -> Unit) {
    GlassCard {
        SectionHeader("🔥", tr("LIVE ตอนนี้"), onSeeAll)
        Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Purple600.copy(alpha = 0.35f), Pink600.copy(alpha = 0.25f)))),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📺", fontSize = 26.sp)
                Text(tr("ยังไม่มีใครไลฟ์ตอนนี้"), color = Slate200, fontSize = 13.sp)
            }
        }
    }
}

/** 'คนใกล้คุณ' circles: shows the cached last radar result only — never calls the radar (no quota spent). */
@Composable
fun NearbyStoriesSection(onSeeAll: () -> Unit) {
    val ctx = LocalContext.current
    val list by NearbyRepo.lastNearby.collectAsState()
    var open by remember { mutableStateOf<String?>(null) }
    var storyMsg by remember { mutableStateOf(false) }
    val radius = remember { NearbyRepo.loadRadius(ctx) }
    GlassCard {
        SectionHeader("📍", tr("คนใกล้คุณ"), onSeeAll)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp).clickable { storyMsg = true }) {
                Box(Modifier.size(68.dp).clip(CircleShape).border(3.dp, Brush.linearGradient(listOf(Purple500, Pink500)), CircleShape).background(Purple600.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Text("+", color = White, fontSize = 30.sp, fontWeight = FontWeight.Light)
                }
                Text(tr("เพิ่มเรื่องราว"), color = White, fontSize = 11.sp, maxLines = 1)
            }
            val l = list
            when {
                l == null -> Surface(onClick = onSeeAll, shape = RoundedCornerShape(14.dp), color = Slate800, modifier = Modifier.align(Alignment.CenterVertically)) {
                    Text(tr("📡 เปิดเรดาร์เพื่อดูคนใกล้คุณ ›"), color = Slate200, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
                l.isEmpty() -> Text("ยังไม่มีใครใกล้คุณในระยะ ${NearbyRepo.radiusLabel(radius)}", color = Slate400, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
                else -> l.forEach { n ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp).clickable { open = n.profile.uid }) {
                        Box(Modifier.size(68.dp).clip(CircleShape).border(3.dp, Brush.linearGradient(listOf(Pink500, Purple500)), CircleShape).padding(4.dp)) { Avatar(n.profile.avatar, n.profile.displayName, 60) }
                        Text(n.profile.displayName, color = White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(n.distance.replace(" m", tr(" ม.")).replace(" km", tr(" กม.")), color = Slate400, fontSize = 10.sp)
                    }
                }
            }
        }
    }
    open?.let { UserProfileDialog(it) { open = null } }
    if (storyMsg) AlertDialog(onDismissRequest = { storyMsg = false }, confirmButton = { TextButton(onClick = { storyMsg = false }) { Text(tr("ตกลง")) } },
        title = { Text(tr("เรื่องราว")) }, text = { Text(tr("ฟีเจอร์เรื่องราว (Stories) ยังไม่เปิดให้ใช้ — ตอนนี้โพสต์ในฟีดได้เลย")) })
}
