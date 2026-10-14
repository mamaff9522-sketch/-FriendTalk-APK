package com.example.ui.discover

import com.example.ui.components.tr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.example.ui.components.ft.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.User
import com.example.social.NearbyRepo
import com.example.social.NearbyUser
import com.example.social.RealProfile
import com.example.social.SocialRepo
import com.example.ui.social.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 'หาเพื่อน' — every friend-finding function in one page: search (names + nearby), online friends, nearby row,
 * then a grid of all functions; a tile opens the function in-page with a back arrow to the grid.
 */
@Composable
fun DiscoverTab(
    users: List<User>,
    currentUser: User,
    onStartChat: (User) -> Unit,
    showCompanion: Boolean = false,
    onOpenCompanion: () -> Unit = {}
) {
    var fn by rememberSaveable { mutableStateOf<String?>(null) }
    val f = fn
    androidx.activity.compose.BackHandler(enabled = f != null) { fn = null }
    if (f != null) {
        Column(Modifier.fillMaxSize().background(AppThemeMode.pageBrush)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { fn = null }) { Text(tr("← หาเพื่อน"), color = White, fontSize = 15.sp) }
                Text(TILES.firstOrNull { it.first == f }?.second ?: "", color = Slate300, fontSize = 14.sp)
            }
            Box(Modifier.weight(1f)) {
                when (f) {
                    "random" -> com.example.ui.ai.RandomMatchView(users = users.filter { it.id != currentUser.id }, onStartChatWithUser = onStartChat)
                    "shake" -> RealShakeView()
                    "radar" -> RealRadarView()
                    "swipe" -> RealSwipeView()
                    "similar" -> PersonalityMatchView()
                    "requests" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { FriendRequestsCard(); FriendsList() }
                }
            }
        }
        return
    }
    FindFriendsHome(onTile = { key -> if (key == "companion") onOpenCompanion() else fn = key }, showCompanion = showCompanion)
}

private val TILES = listOf(
    "random" to tr("สุ่มหาเพื่อน"), "shake" to tr("เขย่าหาเพื่อน"), "radar" to tr("เรดาร์"), "swipe" to tr("ปัดหาเพื่อน"),
    "similar" to tr("คนที่ชอบคล้ายกัน"), "requests" to tr("คำขอเป็นเพื่อน"), "companion" to tr("เพื่อนคุย")
)
private val TILE_ART = mapOf("random" to com.example.R.drawable.art_dice, "shake" to com.example.R.drawable.art_phone, "radar" to com.example.R.drawable.art_radar, "swipe" to com.example.R.drawable.art_heart, "similar" to com.example.R.drawable.art_puzzle, "requests" to com.example.R.drawable.art_handshake, "companion" to com.example.R.drawable.art_chat)
private val TILE_TINT = mapOf("random" to Color(0xFFEC4899), "shake" to Color(0xFF6366F1), "radar" to Color(0xFF8B5CF6), "swipe" to Color(0xFFDB2777), "similar" to Color(0xFF10B981), "requests" to Color(0xFFF59E0B), "companion" to Color(0xFF7C3AED))
private val TILE_ICONS = mapOf("random" to "🎲", "shake" to "📱", "radar" to "📡", "swipe" to "💘", "similar" to "🧩", "requests" to "🤝", "companion" to "💗")

// ---- mockup palette (light lavender page, glassy cards, colourful tiles) ----
// colours follow the user's mode (โหมดปกติ = this lavender look, โหมดมืด = Home's dark look)
private val FPageBg: Brush get() = AppThemeMode.pageBrush
private val FInk: Color get() = AppThemeMode.text
private val FInkSoft: Color get() = AppThemeMode.muted
private val FViolet = Color(0xFF8B3DFF)
private val FPink = Color(0xFFE6399B)
private val FOnline = Color(0xFF22C55E)
private val FCard: Color get() = AppThemeMode.card
private val FCardBorder: Color get() = if (AppThemeMode.dark) Color(0xFF2A2357) else Color(0xCCFFFFFF)

private val TILE_SUB = mapOf(
    "random" to "เจอเพื่อนใหม่\nแบบสุ่ม", "shake" to "เขย่าเพื่อเจอ\nเพื่อนรอบตัว", "radar" to "ค้นหาเพื่อน\nรอบตัวคุณ", "swipe" to "ปัดซ้ายขวา\nหาเพื่อนที่ใช่",
    "similar" to "หาเพื่อนจากความสนใจ\nที่เหมือนกัน", "requests" to "จัดการคำขอ\nและแนะนำเพื่อน", "companion" to "หาเพื่อนคุย\nเริ่มบทสนทนา"
)
private val TILE_GRAD = mapOf(
    "random" to listOf(Color(0xFFFF63B8), Color(0xFFB63BD8)), "shake" to listOf(Color(0xFF4F86FF), Color(0xFF7A4DFF)),
    "radar" to listOf(Color(0xFF8C6BFF), Color(0xFF5530D0)), "swipe" to listOf(Color(0xFFFF8A8A), Color(0xFFE0409F)),
    "similar" to listOf(Color(0xFFEC4FAE), Color(0xFF8E3BD9)), "requests" to listOf(Color(0xFF5B6CF5), Color(0xFF7A3FE0)),
    "companion" to listOf(Color(0xFF9550FF), Color(0xFFC94BE0))
)

/** Header of the 'หาเพื่อน' page (mockup): FriendTalk♡ 🦋, bell and chat with real counts. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
fun FriendsHeader(onBell: () -> Unit, onChat: () -> Unit) {
    val notif by com.example.ui.feed.HomeRepo.notifCount.collectAsState()
    val unread by com.example.ui.feed.HomeRepo.unreadChats.collectAsState()
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF6B2FB0), Color(0xFF9B4FD0), Color(0xFFD9A8EE))))) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text("FriendTalk", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                style = androidx.compose.ui.text.TextStyle(brush = Brush.horizontalGradient(listOf(Color.White, Color(0xFFFFD6F2), Color(0xFFFF8FD0))),
                    shadow = androidx.compose.ui.graphics.Shadow(Color(0x99FF4FC0), blurRadius = 16f)))
            Text("💗", fontSize = 16.sp, modifier = Modifier.padding(start = 2.dp, bottom = 18.dp))
            Text("🦋", fontSize = 40.sp, modifier = Modifier.padding(start = 10.dp))
            Spacer(Modifier.weight(1f))
            HeaderIconBadge(androidx.compose.material.icons.Icons.Filled.Notifications, tr("การแจ้งเตือน"), notif, onBell)
            Spacer(Modifier.width(10.dp))
            HeaderIconBadge(androidx.compose.material.icons.Icons.Filled.Sms, tr("แชท"), unread, onChat)
        }
    }
}

@Composable
private fun HeaderIconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, count: Int, onClick: () -> Unit) {
    Box(Modifier.size(46.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Color(0xFFFFE3F6), modifier = Modifier.size(32.dp))
        if (count > 0) Box(Modifier.align(Alignment.TopEnd).defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(CircleShape).background(Color(0xFFFF2D55))
            .border(1.5.dp, Color.White, CircleShape).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text(if (count > 99) "99+" else "$count", color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = FCard, border = androidx.compose.foundation.BorderStroke(1.dp, FCardBorder),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) { Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp), content = content) }
}

@Composable
private fun PanelTitle(icon: @Composable () -> Unit, title: String, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        icon(); Spacer(Modifier.width(8.dp))
        Text(title, color = FInk, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Text(tr("ดูทั้งหมด ›"), color = FViolet, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSeeAll).padding(4.dp))
    }
}

/** Circle avatars: green ring + green dot (online) or pink ring + distance pill (nearby). */
@Composable
private fun CircleRow(people: List<Pair<RealProfile, String>>, online: Boolean, onOpen: (String) -> Unit) {
    val w = ((LocalConfiguration.current.screenWidthDp - 48) / 6f).dp
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
        people.forEach { (p, dist) ->
            Column(Modifier.width(w).clip(RoundedCornerShape(10.dp)).clickable { onOpen(p.uid) }, horizontalAlignment = Alignment.CenterHorizontally) {
                val ring = if (online) Brush.linearGradient(listOf(Color(0xFF34D399), FOnline)) else Brush.linearGradient(listOf(Color(0xFFFF7AD0), Color(0xFFB45CFF)))
                Box(Modifier.size(w - 4.dp)) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(ring).padding(2.5.dp).clip(CircleShape).background(AppThemeMode.bg).padding(1.5.dp)) {
                        Avatar(p.avatar, p.displayName, (w - 12.dp).value.toInt())
                    }
                    if (online) Box(Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 2.dp).size(13.dp).clip(CircleShape).background(Color.White).padding(2.dp).clip(CircleShape).background(FOnline))
                    else if (dist.isNotBlank()) Row(Modifier.align(Alignment.BottomCenter).offset(y = 4.dp).clip(RoundedCornerShape(10.dp)).background(AppThemeMode.pill).padding(horizontal = 5.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(androidx.compose.material.icons.Icons.Filled.LocationOn, null, tint = FPink, modifier = Modifier.size(11.dp))
                        Text(com.example.ui.feed.thaiDistance(dist), color = FInk, fontSize = 10.sp, maxLines = 1)
                    }
                }
                if (online) Text(p.displayName, color = FInk, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                else Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun FeatureTile(k: String, label: String, height: androidx.compose.ui.unit.Dp, modifier: Modifier, onTile: (String) -> Unit) {
    val grad = TILE_GRAD[k] ?: listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
    com.example.ui.components.ComingSoonGate("tile_$k", modifier.height(height).clip(RoundedCornerShape(22.dp)), compact = true) { com.example.ui.components.FeatureLockGate(k, Modifier.fillMaxSize(), compact = true) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(grad))
            .border(1.5.dp, Brush.verticalGradient(listOf(Color(0xCCFFFFFF), Color(0x33FFFFFF))), RoundedCornerShape(22.dp)).clickable { onTile(k) }) {
            // glossy top light
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.45f).background(Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color.Transparent))))
            Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val art = TILE_ART[k]
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (art != null) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(art), null, modifier = Modifier.fillMaxHeight(0.92f).aspectRatio(1f))
                    else Text(TILE_ICONS[k] ?: "", fontSize = 44.sp)
                }
                Text(label, color = PureWhite, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.3).sp,
                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x66000000), blurRadius = 6f)))
                Text(tr(TILE_SUB[k] ?: ""), color = Color(0xE6FFFFFF), fontSize = 10.5.sp, lineHeight = 13.sp, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
            }
        }
    } }
}

@Composable
private fun FindFriendsHome(onTile: (String) -> Unit, showCompanion: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var radius by rememberRadius()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<NearbyUser>?>(null) }
    var searchMsg by remember { mutableStateOf("") }
    var nearby by remember { mutableStateOf<List<NearbyUser>?>(null) }
    var nearbyMsg by remember { mutableStateOf("") }
    var granted by remember { mutableStateOf(hasLocPerm(ctx)) }
    var online by remember { mutableStateOf<List<RealProfile>?>(null) }
    var hidden by remember { mutableStateOf(SocialRepo.isOnlineHidden(ctx)) }
    var open by remember { mutableStateOf<String?>(null) }
    var filters by remember { mutableStateOf(false) }

    suspend fun loadNearby() {
        if (!granted) { nearby = emptyList(); nearbyMsg = ""; return }
        currentLocation(ctx)?.let { NearbyRepo.shareLocation(it.latitude, it.longitude) }
        val r = NearbyRepo.search("", true, radius)
        nearby = r?.nearby ?: emptyList()
        nearbyMsg = when { r == null -> tr("โหลดไม่สำเร็จ"); r.code == 429 -> tr("โควตาเรดาร์วันนี้หมดแล้ว"); r.nearbyStatus == "no_location" -> tr("ยังหาตำแหน่งไม่ได้ ลองใหม่"); r.nearby.isEmpty() -> "ยังไม่มีใครใกล้คุณในระยะ ${NearbyRepo.radiusLabel(radius)}"; else -> "" }
    }
    fun search() {
        val q = query.trim(); if (q.isEmpty()) return
        focus.clearFocus(); searchMsg = tr("กำลังค้นหา…")
        scope.launch {
            if (granted) currentLocation(ctx)?.let { NearbyRepo.shareLocation(it.latitude, it.longitude) }
            val r = NearbyRepo.search(q, granted, radius)
            results = r?.users ?: emptyList()
            searchMsg = when { r == null -> tr("ค้นหาไม่สำเร็จ"); r.code == 429 -> tr("โควตาค้นหาวันนี้หมดแล้ว"); r.code != 200 -> "ค้นหาไม่สำเร็จ (${r.code})"; else -> "" }
        }
    }
    LaunchedEffect(Unit) { online = NearbyRepo.friendsWithOnline().filter { it.second }.map { it.first } }
    LaunchedEffect(granted, radius) { loadNearby() }

    Box(Modifier.fillMaxSize().background(FPageBg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 16.dp)) {
            // search pill: 🔍 ค้นหาเพื่อน … [filters]
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(28.dp)).background(AppThemeMode.field)
                .border(1.5.dp, if (AppThemeMode.dark) Color(0xBFBE5AFF) else Color(0xE6FFFFFF), RoundedCornerShape(28.dp)).padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Filled.Search, null, tint = if (AppThemeMode.dark) Color(0xFFC48CFF) else Color(0xFF6B2FB0), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                androidx.compose.foundation.text.BasicTextField(value = query, onValueChange = { query = it.take(50); if (it.isBlank()) { results = null; searchMsg = "" } },
                    singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = FInk, fontSize = 16.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    decorationBox = { inner -> Box { if (query.isEmpty()) Text(tr("ค้นหาเพื่อน"), color = AppThemeMode.fieldHint, fontSize = 16.sp); inner() } })
                if (query.isNotBlank()) TextButton(onClick = { query = ""; results = null; searchMsg = "" }) { Text(tr("ล้าง"), color = FInkSoft, fontSize = 13.sp) }
                Box(Modifier.size(44.dp).clip(CircleShape).background(AppThemeMode.pill).border(1.dp, AppThemeMode.divider, CircleShape).clickable { filters = true }, contentAlignment = Alignment.Center) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Tune, tr("ตัวกรอง"), tint = if (AppThemeMode.dark) Color(0xFFC48CFF) else Color(0xFF6B2FB0), modifier = Modifier.size(22.dp))
                }
            }
            if (searchMsg.isNotBlank()) Text(searchMsg, color = Color(0xFFB45309), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            Spacer(Modifier.height(8.dp))
            val res = results
            if (res != null) GlassPanel {
                Text(tr("ผลการค้นหา"), color = FInk, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                if (res.isEmpty() && searchMsg.isBlank()) Text(tr("ไม่พบผู้ใช้ชื่อนี้"), color = FInkSoft, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
                res.forEach { n -> PersonRow(n.profile, n.distance, dark = false) { open = n.profile.uid } }
            } else {
                GlassPanel {
                    PanelTitle({ Box(Modifier.size(18.dp).clip(CircleShape).background(FOnline)) }, tr("เพื่อนที่ออนไลน์"), onSeeAll = { onTile("requests") })
                    val on = online
                    when {
                        on == null -> Text(tr("กำลังโหลด…"), color = FInkSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                        on.isEmpty() -> Text(tr("ยังไม่มีเพื่อนออนไลน์ตอนนี้"), color = FInkSoft, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                        else -> CircleRow(on.map { it to "" }, online = true) { open = it }
                    }
                    if (hidden) Text(tr("คุณซ่อนสถานะออนไลน์อยู่ (เปลี่ยนได้ที่ปุ่มตัวกรอง)"), color = FInkSoft, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                }
                GlassPanel {
                    PanelTitle({ Icon(androidx.compose.material.icons.Icons.Filled.LocationOn, null, tint = FPink, modifier = Modifier.size(24.dp)) }, tr("คนใกล้คุณ"), onSeeAll = { onTile("radar") })
                    if (!granted) Box(Modifier.padding(top = 8.dp)) { LocationPermissionCard { granted = true } }
                    else {
                        val nb = nearby
                        if (nb == null) Text(tr("กำลังหา…"), color = FInkSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                        else if (nb.isNotEmpty()) CircleRow(nb.map { it.profile to it.distance }, online = false) { open = it }
                        if (nearbyMsg.isNotBlank()) Text(nearbyMsg, color = FInkSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                val tiles = TILES.filter { it.first != "companion" || showCompanion }
                val row1 = tiles.take(4); val row2 = tiles.drop(4)
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row1.forEach { (k, label) -> FeatureTile(k, label, 172.dp, Modifier.weight(1f), onTile) }
                }
                if (row2.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(start = if (row2.size < 3) 10.dp else 22.dp, end = if (row2.size < 3) 10.dp else 22.dp, top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row2.forEach { (k, label) -> FeatureTile(k, label, 160.dp, Modifier.weight(1f), onTile) }
                }
            }
        }
    }
    open?.let { UserProfileDialog(it) { open = null } }
    if (filters) AlertDialog(onDismissRequest = { filters = false }, containerColor = Slate900,
        title = { Text(tr("ตัวกรองการหาเพื่อน"), color = White, fontWeight = FontWeight.Bold) },
        text = { Column {
            RadiusPicker(radius) { radius = it; NearbyRepo.saveRadius(ctx, it) }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("ซ่อนสถานะของฉัน"), color = White, fontSize = 14.sp)
                    Text(tr("เพื่อนจะไม่เห็นว่าคุณออนไลน์"), color = Slate400, fontSize = 11.sp)
                }
                Switch(checked = hidden, onCheckedChange = { hidden = it; SocialRepo.setOnlineHidden(ctx, it) })
            }
        } },
        confirmButton = { TextButton(onClick = { filters = false }) { Text(tr("เสร็จ"), color = Pink400) } })
}

@Composable
private fun Section(title: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun AvatarRow(people: List<Pair<RealProfile, String>>, onOpen: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        people.forEach { (p, dist) ->
            Column(Modifier.width(68.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpen(p.uid) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(p.avatar, p.displayName, 52)
                Text(p.displayName, color = White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (dist.isNotBlank()) Text(dist, color = Emerald400, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun PersonRow(p: RealProfile, distance: String, dark: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(p.avatar, p.displayName, 40)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.displayName, color = if (dark) White else FInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.bio.isNotBlank()) Text(p.bio, color = if (dark) Slate400 else FInkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (distance.isNotBlank()) Surface(shape = RoundedCornerShape(10.dp), color = Color(0x3310B981)) {
            Text(distance.replace(" m", tr(" ม.")).replace(" km", tr(" กม.")), color = Emerald400, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Spacer(Modifier.width(6.dp)); com.example.ui.social.GiftToUserButton(p.uid, compact = true)
    }
}

@Composable
private fun FriendsList() {
    var list by remember { mutableStateOf<List<Pair<RealProfile, Boolean>>?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { list = NearbyRepo.friendsWithOnline() }
    Text(tr("เพื่อนของฉัน"), color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
    val l = list
    when {
        l == null -> Text(tr("กำลังโหลด…"), color = Slate400, fontSize = 12.sp)
        l.isEmpty() -> Text(tr("ยังไม่มีเพื่อน"), color = Slate400, fontSize = 12.sp)
        else -> l.forEach { (p, on) -> PersonRow(p, if (on) tr("🟢 ออนไลน์") else "") { open = p.uid } }
    }
    open?.let { UserProfileDialog(it) { open = null } }
}
