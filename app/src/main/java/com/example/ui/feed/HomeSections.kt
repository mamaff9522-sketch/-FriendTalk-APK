package com.example.ui.feed

import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.draw.shadow
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
import com.example.ui.components.ft.Text
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
    // Mockup: navy card, thin neon magenta→violet border with a soft purple glow.
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xE60B1430), border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0xCCC026D3), Color(0xCC7C3AED), Color(0x993B82F6)))),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = Color(0xFF7C3AED), spotColor = Color(0xFFC026D3))) { Column(Modifier.padding(12.dp), content = content) }
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
    // Mockup chips: ทั้งหมด (feed filter) · ใกล้คุณ / ออนไลน์ (open Find Friends) · ผู้ใช้ทั้งหมด (user search)
    val chips: List<Pair<String, ImageVector>> = listOf("all" to Icons.Filled.Home, "near" to Icons.Filled.Place, "users" to Icons.Filled.Group, "online" to Icons.Filled.Favorite)
    val labels = mapOf("all" to tr("ทั้งหมด"), "near" to tr("ใกล้คุณ"), "users" to tr("ผู้ใช้ทั้งหมด"), "online" to tr("ออนไลน์"))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { (k, icon) ->
            val sel = selected == k
            Surface(onClick = { onSelect(k) }, shape = RoundedCornerShape(16.dp),
                color = if (sel) Color.Transparent else Color(0xFF0B1430), border = if (sel) null else BorderStroke(1.dp, Color(0xFF26305A))) {
                Row(Modifier.then(if (sel) Modifier.background(Brush.horizontalGradient(listOf(Pink500, Pink400))) else Modifier).padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = if (sel) PureWhite else if (k == "online") Pink400 else Slate300, modifier = Modifier.size(20.dp))
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


/** Mockup search bar: magenta neon outline, magnifier + filter icon; opens the real global search (users + #hashtag). */
@Composable
fun HomeSearchBar(onOpen: () -> Unit) {
    Surface(onClick = onOpen, shape = RoundedCornerShape(16.dp), color = Color(0xFF0B1430),
        border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFFEC4899), Color(0xFFC026D3), Color(0xFFA855F7)))),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = Color(0xFFEC4899), spotColor = Color(0xFFEC4899))) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = Color(0xFFEC4899), modifier = Modifier.size(24.dp)); Spacer(Modifier.width(10.dp))
            Text(tr("ค้นหาโพสต์, เพื่อน, คลับ หรือ สตรีมเมอร์..."), color = Color(0xFFB8C0D9), fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(22.dp).background(Color(0xFF3A4270))); Spacer(Modifier.width(10.dp))
            Icon(Icons.Filled.Tune, null, tint = Color(0xFFD1D5F0), modifier = Modifier.size(22.dp))
        }
    }
}

/** Original Home mockup palette (dark navy + magenta/purple). Applied on Home only. */
val HOME_MOCKUP_THEME = com.example.model.AppUiThemeConfig(primaryColorHex = "#EC4899", secondaryColorHex = "#A855F7", accentColorHex = "#8B5CF6",
    backgroundColorHex = "#060B1A", surfaceColorHex = "#0B1430", cardColorHex = "#101A3A", textPrimaryHex = "#FFFFFF", textSecondaryHex = "#B8C0D9")
