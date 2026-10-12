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
import androidx.compose.ui.graphics.Color
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
        Column(Modifier.fillMaxSize()) {
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

@Composable
private fun FindFriendsHome(onTile: (String) -> Unit, showCompanion: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val radius by rememberRadius()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<NearbyUser>?>(null) }
    var searchMsg by remember { mutableStateOf("") }
    var nearby by remember { mutableStateOf<List<NearbyUser>?>(null) }
    var nearbyMsg by remember { mutableStateOf("") }
    var granted by remember { mutableStateOf(hasLocPerm(ctx)) }
    var online by remember { mutableStateOf<List<RealProfile>?>(null) }
    var hidden by remember { mutableStateOf(SocialRepo.isOnlineHidden(ctx)) }
    var open by remember { mutableStateOf<String?>(null) }

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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        OutlinedTextField(value = query, onValueChange = { query = it.take(50); if (it.isBlank()) { results = null; searchMsg = "" } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, shape = RoundedCornerShape(18.dp),
            leadingIcon = { Text("🔍", fontSize = 18.sp) },
            placeholder = { Text(tr("ค้นหาเพื่อนจากชื่อ"), color = Slate400) },
            trailingIcon = { if (query.isNotBlank()) TextButton(onClick = { query = ""; results = null; searchMsg = "" }) { Text(tr("ล้าง"), color = Slate300) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = Pink500, unfocusedBorderColor = Pink500.copy(alpha = 0.7f), focusedContainerColor = Slate900, unfocusedContainerColor = Slate900))
        if (searchMsg.isNotBlank()) Text(searchMsg, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        val res = results
        if (res != null) {
            if (res.isEmpty() && searchMsg.isBlank()) Text(tr("ไม่พบผู้ใช้ชื่อนี้"), color = Slate400, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            res.forEach { n -> PersonRow(n.profile, n.distance) { open = n.profile.uid } }
        } else {
            com.example.ui.feed.GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Emerald400)); Spacer(Modifier.width(10.dp))
                    Text(tr("เพื่อนที่ออนไลน์"), color = White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, modifier = Modifier.weight(1f))
                    Text(tr("ซ่อนสถานะของฉัน"), color = Slate300, fontSize = 12.sp)
                    Switch(checked = hidden, onCheckedChange = { hidden = it; SocialRepo.setOnlineHidden(ctx, it) }, modifier = Modifier.padding(start = 6.dp))
                }
                val on = online
                Spacer(Modifier.height(6.dp))
                when {
                    on == null -> Text(tr("กำลังโหลด…"), color = Slate400, fontSize = 13.sp)
                    on.isEmpty() -> Text(tr("ยังไม่มีเพื่อนออนไลน์ตอนนี้"), color = Slate300, fontSize = 14.sp)
                    else -> AvatarRow(on.map { it to "" }) { open = it }
                }
            }
            com.example.ui.feed.GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", fontSize = 22.sp); Spacer(Modifier.width(8.dp))
                    Text("คนใกล้คุณ (${NearbyRepo.radiusLabel(radius)})", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                }
                Spacer(Modifier.height(6.dp))
                if (!granted) LocationPermissionCard { granted = true }
                else {
                    val nb = nearby
                    if (nb == null) Text(tr("กำลังหา…"), color = Slate400, fontSize = 13.sp)
                    else if (nb.isNotEmpty()) AvatarRow(nb.map { it.profile to it.distance }) { open = it }
                    if (nearbyMsg.isNotBlank()) Text(nearbyMsg, color = Slate300, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(tr("ปรับระยะได้ในเรดาร์"), color = Slate400, fontSize = 11.sp)
                }
            }
            Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✨", fontSize = 22.sp); Spacer(Modifier.width(8.dp)); Text(tr("ฟังก์ชันหาเพื่อน"), color = White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
            val tiles = TILES.filter { it.first != "companion" || showCompanion }
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (k, label) ->
                            val tint = TILE_TINT[k] ?: Color(0xFF8B5CF6)
                            com.example.ui.components.ComingSoonGate("tile_$k", Modifier.weight(1f).height(150.dp).clip(RoundedCornerShape(16.dp)), compact = true) { com.example.ui.components.FeatureLockGate(k, Modifier.fillMaxSize(), compact = true) {
                            Surface(shape = RoundedCornerShape(16.dp), color = Slate900, onClick = { onTile(k) },
                                border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.55f)), modifier = Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(tint.copy(alpha = 0.35f), Color.Transparent)))) {
                                    val art = TILE_ART[k]
                                    if (art != null) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(art), null, modifier = Modifier.align(Alignment.Center).padding(bottom = 30.dp).size(88.dp))
                                    else Text(TILE_ICONS[k] ?: "", fontSize = 54.sp, modifier = Modifier.align(Alignment.Center).padding(bottom = 26.dp))
                                    Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.2).sp, modifier = Modifier.weight(1f))
                                        Text("›", color = Slate300, fontSize = 15.sp)
                                    }
                                }
                            }
                            } }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    open?.let { UserProfileDialog(it) { open = null } }
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
private fun PersonRow(p: RealProfile, distance: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(p.avatar, p.displayName, 40)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.displayName, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.bio.isNotBlank()) Text(p.bio, color = Slate400, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
