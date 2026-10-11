package com.example.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
    if (f != null) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { fn = null }) { Text("← หาเพื่อน", color = White, fontSize = 15.sp) }
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
    "random" to "สุ่มหาเพื่อน", "shake" to "เขย่าหาเพื่อน", "radar" to "เรดาร์", "swipe" to "ปัดหาเพื่อน",
    "similar" to "คนที่ชอบคล้ายกัน", "requests" to "คำขอเป็นเพื่อน", "companion" to "เพื่อนคุย"
)
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
        nearbyMsg = when { r == null -> "โหลดไม่สำเร็จ"; r.code == 429 -> "โควตาเรดาร์วันนี้หมดแล้ว"; r.nearbyStatus == "no_location" -> "ยังหาตำแหน่งไม่ได้ ลองใหม่"; r.nearby.isEmpty() -> "ยังไม่มีใครใกล้คุณในระยะ ${NearbyRepo.radiusLabel(radius)}"; else -> "" }
    }
    fun search() {
        val q = query.trim(); if (q.isEmpty()) return
        focus.clearFocus(); searchMsg = "กำลังค้นหา…"
        scope.launch {
            if (granted) currentLocation(ctx)?.let { NearbyRepo.shareLocation(it.latitude, it.longitude) }
            val r = NearbyRepo.search(q, granted, radius)
            results = r?.users ?: emptyList()
            searchMsg = when { r == null -> "ค้นหาไม่สำเร็จ"; r.code == 429 -> "โควตาค้นหาวันนี้หมดแล้ว"; r.code != 200 -> "ค้นหาไม่สำเร็จ (${r.code})"; else -> "" }
        }
    }
    LaunchedEffect(Unit) { online = NearbyRepo.friendsWithOnline().filter { it.second }.map { it.first } }
    LaunchedEffect(granted, radius) { loadNearby() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        OutlinedTextField(value = query, onValueChange = { query = it.take(50); if (it.isBlank()) { results = null; searchMsg = "" } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, shape = RoundedCornerShape(24.dp),
            placeholder = { Text("🔍 ค้นหาเพื่อนจากชื่อ", color = Slate400) },
            trailingIcon = { if (query.isNotBlank()) TextButton(onClick = { query = ""; results = null; searchMsg = "" }) { Text("ล้าง", color = Slate300) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
        if (searchMsg.isNotBlank()) Text(searchMsg, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        val res = results
        if (res != null) {
            if (res.isEmpty() && searchMsg.isBlank()) Text("ไม่พบผู้ใช้ชื่อนี้", color = Slate400, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            res.forEach { n -> PersonRow(n.profile, n.distance) { open = n.profile.uid } }
        } else {
            Section("🟢 เพื่อนที่ออนไลน์") {
                Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("ซ่อนสถานะของฉัน", color = Slate400, fontSize = 11.sp)
                    Switch(checked = hidden, onCheckedChange = { hidden = it; SocialRepo.setOnlineHidden(ctx, it) }, modifier = Modifier.padding(start = 4.dp))
                }
            }
            val on = online
            when {
                on == null -> Text("กำลังโหลด…", color = Slate400, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
                on.isEmpty() -> Text("ยังไม่มีเพื่อนออนไลน์ตอนนี้", color = Slate400, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
                else -> AvatarRow(on.map { it to "" }) { open = it }
            }
            Section("📍 คนใกล้คุณ (${NearbyRepo.radiusLabel(radius)})") {}
            if (!granted) Box(Modifier.padding(horizontal = 16.dp)) { LocationPermissionCard { granted = true } }
            else {
                Text("ใช้ระยะเดียวกับเรดาร์ · ปรับได้ในเรดาร์", color = Slate400, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
                val nb = nearby
                if (nb == null) Text("กำลังหา…", color = Slate400, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
                else if (nb.isNotEmpty()) AvatarRow(nb.map { it.profile to it.distance }) { open = it }
                if (nearbyMsg.isNotBlank()) Text(nearbyMsg, color = Slate400, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            Section("✨ ฟังก์ชันหาเพื่อน") {}
            val tiles = TILES.filter { it.first != "companion" || showCompanion }
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (k, label) ->
                            Surface(shape = RoundedCornerShape(16.dp), color = Slate800, onClick = { onTile(k) }, modifier = Modifier.weight(1f).height(96.dp)) {
                                Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(TILE_ICONS[k] ?: "", fontSize = 28.sp)
                                    Text(label, color = White, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
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
            Text(distance.replace(" m", " ม.").replace(" km", " กม."), color = Emerald400, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
}

@Composable
private fun FriendsList() {
    var list by remember { mutableStateOf<List<Pair<RealProfile, Boolean>>?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { list = NearbyRepo.friendsWithOnline() }
    Text("เพื่อนของฉัน", color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
    val l = list
    when {
        l == null -> Text("กำลังโหลด…", color = Slate400, fontSize = 12.sp)
        l.isEmpty() -> Text("ยังไม่มีเพื่อน", color = Slate400, fontSize = 12.sp)
        else -> l.forEach { (p, on) -> PersonRow(p, if (on) "🟢 ออนไลน์" else "") { open = p.uid } }
    }
    open?.let { UserProfileDialog(it) { open = null } }
}
