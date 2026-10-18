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
    // a function screen (swipe cards, radar, shake…) is open → don't let horizontal drags change the tab
    DisposableEffect(f) { com.example.ui.components.TabSwipe.locked = f != null; onDispose { com.example.ui.components.TabSwipe.locked = false } }
    if (f != null) {
        Column(Modifier.fillMaxSize().background(AppThemeMode.pageBrush)) {
            // สุ่มหาเพื่อน has no header row: the phone's back button returns to the grid (BackHandler above)
            if (f != "random") Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    "friends" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { FriendsList() } // requests are in the bell now
                    "companion" -> com.example.ui.social.CompanionGridScreen() // approved companions as small profile cards
                    "rewards" -> com.example.ads.AdsPage()
                }
            }
        }
        return
    }
    FindFriendsHome(onTile = { key -> fn = key }, showCompanion = showCompanion) // เพื่อนคุย now opens inside หาเพื่อน too
}

private val TILES = listOf(
    "random" to tr("สุ่มหาเพื่อน"), "shake" to tr("เขย่าหาเพื่อน"), "radar" to tr("เรดาร์"), "swipe" to tr("ปัดหาเพื่อน"),
    "similar" to tr("คนที่ชอบคล้ายกัน"), "friends" to tr("เพื่อนของฉัน"), "companion" to tr("เพื่อนคุย"),
    // 8th tile fills the empty slot of the 4 + 3 grid: watch a short ad -> extra quota (existing Ad Room / หน้าโฆษณา)
    "rewards" to tr("รับสิทธิ์ฟรี")
)
private val TILE_ART = mapOf("random" to com.example.R.drawable.art_dice, "shake" to com.example.R.drawable.art_phone, "radar" to com.example.R.drawable.art_radar, "swipe" to com.example.R.drawable.art_heart, "similar" to com.example.R.drawable.art_puzzle, "requests" to com.example.R.drawable.art_handshake, "companion" to com.example.R.drawable.art_chat, "rewards" to com.example.R.drawable.art_gift)
private val TILE_TINT = mapOf("random" to Color(0xFFEC4899), "shake" to Color(0xFF6366F1), "radar" to Color(0xFF8B5CF6), "swipe" to Color(0xFFDB2777), "similar" to Color(0xFF10B981), "requests" to Color(0xFFF59E0B), "companion" to Color(0xFF7C3AED), "rewards" to Color(0xFFF59E0B))
private val TILE_ICONS = mapOf("random" to "🎲", "shake" to "📱", "radar" to "📡", "swipe" to "💘", "similar" to "🧩", "requests" to "🤝", "friends" to "👥", "companion" to "💗", "rewards" to "🎁")

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
    "similar" to "หาเพื่อนจากความสนใจ\nที่เหมือนกัน", "requests" to "จัดการคำขอ\nและแนะนำเพื่อน", "companion" to "หาเพื่อนคุย\nเริ่มบทสนทนา",
    "rewards" to "ดูโฆษณาสั้น ๆ\nแลกโควตาเพิ่ม"
)
private val TILE_GRAD = mapOf(
    "random" to listOf(Color(0xFFFF63B8), Color(0xFFB63BD8)), "shake" to listOf(Color(0xFF4F86FF), Color(0xFF7A4DFF)),
    "radar" to listOf(Color(0xFF8C6BFF), Color(0xFF5530D0)), "swipe" to listOf(Color(0xFFFF8A8A), Color(0xFFE0409F)),
    "similar" to listOf(Color(0xFFEC4FAE), Color(0xFF8E3BD9)), "requests" to listOf(Color(0xFF5B6CF5), Color(0xFF7A3FE0)),
    "companion" to listOf(Color(0xFF9550FF), Color(0xFFC94BE0)), "friends" to listOf(Color(0xFF3FB8FF), Color(0xFF5B6CF5)),
    "rewards" to listOf(Color(0xFFFFB547), Color(0xFFFF4F8B))
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
        Text(title, color = FInk, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(tr("ดูทั้งหมด ›"), color = FViolet, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSeeAll).padding(4.dp))
    }
}

/** Circle avatars: green ring + green dot (online) or pink ring + distance pill (nearby). */
@Composable
private fun CircleRow(people: List<Pair<RealProfile, String>>, online: Boolean, onOpen: (String) -> Unit) {
    val w = ((LocalConfiguration.current.screenWidthDp - 48) / 6f).dp.coerceAtLeast(60.dp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
        people.forEach { (p, dist) ->
            Column(Modifier.width(w).clip(RoundedCornerShape(10.dp)).clickable { onOpen(p.uid) }, horizontalAlignment = Alignment.CenterHorizontally) {
                val ring = if (online) Brush.linearGradient(listOf(Color(0xFF34D399), FOnline)) else Brush.linearGradient(listOf(Color(0xFFFF7AD0), Color(0xFFB45CFF)))
                Box(Modifier.size(56.dp)) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(ring).padding(2.5.dp).clip(CircleShape).background(AppThemeMode.bg).padding(1.5.dp)) {
                        Avatar(p.avatar, p.displayName, 48)
                    }
                    if (online) Box(Modifier.align(Alignment.BottomEnd).padding(end = 1.dp, bottom = 1.dp).size(12.dp).clip(CircleShape).background(Color.White).padding(2.dp).clip(CircleShape).background(FOnline))
                    else if (dist.isNotBlank()) Row(Modifier.align(Alignment.BottomCenter).offset(y = 4.dp).clip(RoundedCornerShape(10.dp)).background(AppThemeMode.pill).padding(horizontal = 5.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(androidx.compose.material.icons.Icons.Filled.LocationOn, null, tint = FPink, modifier = Modifier.size(11.dp))
                        Text(com.example.ui.feed.thaiDistance(dist), color = FInk, fontSize = 9.5.sp, maxLines = 1)
                    }
                }
                if (online) Text(p.displayName, color = FInk, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                else Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun FeatureTile(k: String, label: String, height: androidx.compose.ui.unit.Dp, modifier: Modifier, titleSize: androidx.compose.ui.unit.TextUnit, onTile: (String) -> Unit) {
    val grad = TILE_GRAD[k] ?: listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
    com.example.ui.components.ComingSoonGate("tile_$k", modifier.height(height).clip(RoundedCornerShape(20.dp)), compact = true) { com.example.ui.components.FeatureLockGate(k, Modifier.fillMaxSize(), compact = true) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(grad))
            .border(1.5.dp, Brush.verticalGradient(listOf(Color(0xCCFFFFFF), Color(0x33FFFFFF))), RoundedCornerShape(20.dp)).clickable { onTile(k) }) {
            // glossy top light
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.45f).background(Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color.Transparent))))
            // same art size, title line and 2-line subtitle in every tile, so both rows line up
            Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    val art = TILE_ART[k]
                    if (art != null) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(art), null, modifier = Modifier.fillMaxSize())
                    else Text(TILE_ICONS[k] ?: "", fontSize = 40.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(label, color = PureWhite, fontSize = titleSize, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false, letterSpacing = (-0.3).sp, textAlign = TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x66000000), blurRadius = 6f)))
                Text(tr(TILE_SUB[k] ?: ""), color = Color(0xE6FFFFFF), fontSize = 9.5.sp, lineHeight = 12.sp, textAlign = TextAlign.Center, maxLines = 2, minLines = 2,
                    modifier = Modifier.padding(top = 3.dp))
            }
        }
    } }
}

/**
 * หาเพื่อน home: search box + small 'ต้องการ' capsule (gender / age / distance, shared with random, shake, radar, swipe
 * and this grid), a sideways row of small function capsules (each opens its own screen), then everyone online now
 * as a 4-column grid of rounded photos in random order — the main area of the page.
 */
@Composable
private fun FindFriendsHome(onTile: (String) -> Unit, showCompanion: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { com.example.social.DiscoverFilter.load(ctx) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<NearbyUser>?>(null) }
    var searchMsg by remember { mutableStateOf("") }
    var granted by remember { mutableStateOf(hasLocPerm(ctx)) }
    var online by remember { mutableStateOf<List<NearbyUser>?>(null) }
    var onlineMsg by remember { mutableStateOf("") }
    var hasLoc by remember { mutableStateOf(true) }
    var open by remember { mutableStateOf<String?>(null) }
    var wantDialog by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    fun search() {
        val q = query.trim(); if (q.isEmpty()) return
        focus.clearFocus(); searchMsg = tr("กำลังค้นหา…")
        scope.launch {
            val r = NearbyRepo.search(q, false, 0.5)
            results = r?.users ?: emptyList()
            searchMsg = when { r == null -> tr("ค้นหาไม่สำเร็จ"); r.code == 429 -> tr("โควตาค้นหาวันนี้หมดแล้ว"); r.code != 200 -> "ค้นหาไม่สำเร็จ (${r.code})"; else -> "" }
        }
    }
    // online grid: reload on open, when the filter is saved, and every minute while the page is shown
    LaunchedEffect(reload, granted) {
        if (granted) currentLocation(ctx)?.let { NearbyRepo.shareLocation(it.latitude, it.longitude) }
        while (true) {
            val r = NearbyRepo.onlineUsers()
            if (r.code == 200) { online = r.users; hasLoc = r.hasLocation; onlineMsg = "" }
            else if (online.isNullOrEmpty()) { online = emptyList(); onlineMsg = if (r.code == 404) tr("เซิร์ฟเวอร์ยังไม่อัปเดต") else tr("โหลดไม่สำเร็จ กำลังลองใหม่…") + if (r.code > 0) " (${r.code})" else "" }
            // a failed load (e.g. the server waking up) is retried soon instead of waiting a minute
            kotlinx.coroutines.delay(if (r.code == 200) (com.example.ui.components.Tweaks.num("discover.refreshSec") * 1000).toLong() else 6_000)
        }
    }

    Column(Modifier.fillMaxSize().background(FPageBg)) {
        // 1) search + 'ต้องการ'
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp)).background(AppThemeMode.field)
                .border(1.dp, if (AppThemeMode.dark) Color(0x80BE5AFF) else Color(0xE6FFFFFF), RoundedCornerShape(21.dp)).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Filled.Search, null, tint = if (AppThemeMode.dark) Color(0xFFC48CFF) else Color(0xFF6B2FB0), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicTextField(value = query, onValueChange = { query = it.take(50); if (it.isBlank()) { results = null; searchMsg = "" } },
                    singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = FInk, fontSize = 14.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(FPink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> Box { if (query.isEmpty()) Text(tr("ค้นหาเพื่อน"), color = AppThemeMode.fieldHint, fontSize = 14.sp); inner() } })
                if (query.isNotBlank()) Text("✕", color = FInkSoft, fontSize = 14.sp, modifier = Modifier.clip(CircleShape).clickable { query = ""; results = null; searchMsg = "" }.padding(8.dp))
            }
            Spacer(Modifier.width(8.dp))
            val set = com.example.social.DiscoverFilter.isSet
            Row(Modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
                .background(if (set) Brush.horizontalGradient(listOf(FPink, FViolet)) else Brush.horizontalGradient(listOf(AppThemeMode.pill, AppThemeMode.pill)))
                .border(1.dp, if (set) Color.Transparent else AppThemeMode.divider, RoundedCornerShape(17.dp))
                .clickable { wantDialog = true }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Filled.Tune, null, tint = if (set) PureWhite else FViolet, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr("ต้องการ"), color = if (set) PureWhite else FInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (com.example.social.DiscoverFilter.isSet) Text("🎯 " + com.example.social.DiscoverFilter.summary(), color = FInkSoft, fontSize = 11.sp, modifier = Modifier.padding(start = 18.dp, bottom = 2.dp))
        // 2) function capsules: all on one screen, 3 per row (no sideways scrolling)
        val tiles = TILES.filter { it.first != "companion" || showCompanion }
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val perRow = com.example.ui.components.Tweaks.int("discover.capsuleColumns")
            tiles.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (k, label) -> FunctionCapsule(k, label, Modifier.weight(1f)) { onTile(k) } }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (searchMsg.isNotBlank()) Text(searchMsg, color = Color(0xFFB45309), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
        // 3) main area: search results, else everyone online
        val res = results
        if (res != null) androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item { Text(tr("ผลการค้นหา"), color = FInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            if (res.isEmpty() && searchMsg.isBlank()) item { Text(tr("ไม่พบผู้ใช้ชื่อนี้"), color = FInkSoft, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
            items(res.size) { i -> val n = res[i]; PersonRow(n.profile, n.distance, dark = false) { open = n.profile.uid } }
        } else {
            val on = online
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(FOnline)); Spacer(Modifier.width(6.dp))
                Text(if (on == null) tr("กำลังโหลด…") else "${tr("ออนไลน์ตอนนี้")} ${on.size} ${tr("คน")}", color = FInkSoft, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("↻", color = FViolet, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).clickable { reload++ }.padding(horizontal = 10.dp, vertical = 2.dp))
            }
            when {
                on == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FPink) }
                on.isEmpty() -> Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(onlineMsg.ifBlank { if (com.example.social.DiscoverFilter.isSet) tr("ไม่มีคนออนไลน์ที่ตรงกับที่คุณต้องการ") else tr("ยังไม่มีใครออนไลน์ตอนนี้") }, color = FInkSoft, fontSize = 14.sp, textAlign = TextAlign.Center)
                    if (com.example.social.DiscoverFilter.isSet) TextButton(onClick = { wantDialog = true }) { Text(tr("ปรับ 'ต้องการ'"), color = FViolet) }
                }
                else -> androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Fixed(com.example.ui.components.Tweaks.int("discover.onlineColumns")),
                    Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(on.size, key = { on[it].profile.uid }) { i -> OnlineCard(on[i]) { open = on[i].profile.uid } }
                }
            }
            if (!granted && on != null) Box(Modifier.padding(horizontal = 10.dp)) { LocationPermissionCard { granted = true } }
            else if (granted && !hasLoc && com.example.social.DiscoverFilter.km > 0) Text(tr("ระยะทางใช้ได้เมื่อหาตำแหน่งได้แล้ว"), color = FInkSoft, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }
    open?.let { UserProfileDialog(it) { open = null } }
    if (wantDialog) WantDialog(onDismiss = { wantDialog = false }) { wantDialog = false; online = null; reload++ }
}

/** Small capsule for one friend-finding function (respects the admin's feature lock / 'เร็ว ๆ นี้'). */
@Composable
private fun FunctionCapsule(k: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val locked = com.example.ui.components.AppGates.partLocked(k) && !com.example.ui.components.AppGates.isStaff
    val soon = com.example.ui.components.AppGates.comingSoon["tile_$k"]?.enabled == true && !com.example.ui.components.AppGates.isSuper
    val grad = TILE_GRAD[k] ?: listOf(FPink, FViolet)
    Row(modifier.height(com.example.ui.components.Tweaks.num("discover.capsuleHeight").dp).clip(RoundedCornerShape(19.dp))
        .background(if (locked || soon) Brush.horizontalGradient(listOf(Color(0x66888899), Color(0x66888899))) else Brush.horizontalGradient(grad))
        .clickable {
            when {
                locked -> android.widget.Toast.makeText(ctx, tr("ปิดใช้งานชั่วคราว"), android.widget.Toast.LENGTH_SHORT).show()
                soon -> android.widget.Toast.makeText(ctx, tr("เร็ว ๆ นี้"), android.widget.Toast.LENGTH_SHORT).show()
                else -> onClick()
            }
        }.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(if (locked) "🔒" else TILE_ICONS[k] ?: "", fontSize = 14.sp)
        Spacer(Modifier.width(3.dp))
        Text(label, color = PureWhite, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** One online person: big 3:4 photo (3 per row), green dot, name and 'age · distance' over a dark fade at the bottom. */
@Composable
private fun OnlineCard(n: NearbyUser, onClick: () -> Unit) {
    val p = n.profile
    Box(Modifier.fillMaxWidth().aspectRatio(100f / com.example.ui.components.Tweaks.num("discover.onlineCardHeight")).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
        .background(Brush.linearGradient(listOf(Color(0xFFFF7AD0), Color(0xFF9B5CFF))))) {
        Text(p.displayName.take(1).ifBlank { "?" }, color = PureWhite, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
        if (p.avatar.isNotBlank()) coil.compose.AsyncImage(model = p.avatar, contentDescription = p.displayName, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(13.dp).clip(CircleShape).background(Color.White).padding(2.dp).clip(CircleShape).background(FOnline))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            .padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 7.dp)) {
            Text(p.displayName.ifBlank { tr("ผู้ใช้") }, color = PureWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(p.age?.let { "$it ${tr("ปี")}" }, n.distance.takeIf { it.isNotBlank() }?.let { com.example.ui.feed.thaiDistance(it) }).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = PureWhite.copy(alpha = 0.85f), fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 'ต้องการ' window: gender, age range, distance (+ hide my online status). Save applies to every friend-finding function. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WantDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val F = com.example.social.DiscoverFilter
    var g by remember { mutableStateOf(F.gender) }
    var ages by remember { mutableStateOf(F.ageMin.toFloat()..F.ageMax.toFloat()) }
    var km by remember { mutableStateOf(F.km) }
    var hidden by remember { mutableStateOf(SocialRepo.isOnlineHidden(ctx)) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = AppThemeMode.dialog,
        title = { Text(tr("ต้องการ"), color = FInk, fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(tr("ใช้กับคนออนไลน์ สุ่ม เขย่า เรดาร์ และปัดหาเพื่อน"), color = FInkSoft, fontSize = 11.sp)
            Text(tr("เพศ"), color = FInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                F.GENDERS.forEach { (k, l) -> FilterChip(selected = g == k, onClick = { g = k }, label = { Text(tr(l), fontSize = 12.sp) }) }
            }
            val top = ages.endInclusive.toInt()
            Text("${tr("อายุ")}  ${ages.start.toInt()} – ${if (top >= F.AGE_HI) "60+" else top} ${tr("ปี")}", color = FInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
            RangeSlider(value = ages, onValueChange = { ages = it }, valueRange = F.AGE_LO.toFloat()..F.AGE_HI.toFloat(), steps = F.AGE_HI - F.AGE_LO - 1,
                colors = SliderDefaults.colors(thumbColor = FPink, activeTrackColor = FPink))
            Text(tr("ระยะทาง"), color = FInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                F.DISTANCES.forEach { d -> FilterChip(selected = km == d, onClick = { km = d }, label = { Text(tr(F.distanceLabel(d)), fontSize = 12.sp) }) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("ซ่อนสถานะของฉัน"), color = FInk, fontSize = 14.sp)
                    Text(tr("คนอื่นจะไม่เห็นว่าคุณออนไลน์"), color = FInkSoft, fontSize = 11.sp)
                }
                Switch(checked = hidden, onCheckedChange = { hidden = it })
            }
        } },
        confirmButton = { TextButton(onClick = {
            F.save(ctx, g, ages.start.toInt(), ages.endInclusive.toInt(), km)
            if (hidden != SocialRepo.isOnlineHidden(ctx)) SocialRepo.setOnlineHidden(ctx, hidden)
            onSaved()
        }) { Text(tr("บันทึก"), color = FPink, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก"), color = FInkSoft) } })
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
