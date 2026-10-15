package com.example.ui.feed

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.User
import com.example.model.UserRole
import com.example.social.FeedPost
import com.example.social.FeedRepo
import com.example.social.FriendRequest
import com.example.social.NearbyRepo
import com.example.social.NearbyUser
import com.example.social.SocialRepo
import com.example.ui.components.ft.Text
import com.example.ui.components.tr
import com.example.ui.social.Avatar
import com.example.ui.social.RealFeedState
import com.example.ui.social.UserProfileDialog
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/* Home screen built to the owner's mockup (navy/violet neon). All data is real:
 * lives + users from RTDB, nearby from friendtalk-brain /radar/nearby, feed from /feed. */

// ---- mockup palette ----
// colours follow the user's mode (โหมดมืด / โหมดปกติ, set on the "ฉัน" page)
private val HBg: Color get() = AppThemeMode.bg
private val HCard: Color get() = AppThemeMode.card
private val HLine: Color get() = AppThemeMode.line
private val HText: Color get() = AppThemeMode.text
private val HNeon = Color(0xFFFF2FA8)
private val HViolet = Color(0xFFB04DFF)
private val HMuted: Color get() = AppThemeMode.muted
private val HSoft: Color get() = AppThemeMode.textSoft
private val HOnline = Color(0xFF2BD96B)
private val HRed = Color(0xFFFF2D55)
private val ringBrush = Brush.sweepGradient(listOf(HNeon, HViolet, Color(0xFF6D5BFF), HNeon))
private val neonGlow = TextStyle(shadow = Shadow(Color(0xC0FF2FA8), blurRadius = 18f))

/** Real data for the mockup Home (cached per process). */
object HomeRepo {
    data class UserInfo(val name: String, val avatar: String, val verified: Boolean)
    data class LiveItem(val id: String, val hostUid: String, val title: String, val name: String, val cover: String, val viewers: Int, val verified: Boolean)

    private val db by lazy { FirebaseDatabase.getInstance(SocialRepo.DB_URL) }
    private val users = java.util.concurrent.ConcurrentHashMap<String, UserInfo>()
    val unreadChats = MutableStateFlow(0)
    val notifCount = MutableStateFlow(0)
    /** Post created from the bottom-bar ＋ button; Home puts it at the top of the feed. */
    val newPost = MutableStateFlow<FeedPost?>(null)
    var reloadTick by mutableIntStateOf(0)
    private var nearbyTried = false

    private suspend fun get(path: String, q: (Query) -> Query = { it }): DataSnapshot? = suspendCancellableCoroutine { c ->
        q(db.getReference(path)).get().addOnCompleteListener { t -> if (c.isActive) c.resume(if (t.isSuccessful) t.result else null) }
    }
    private fun num(s: DataSnapshot, k: String): Long = (s.child(k).value as? Number)?.toLong() ?: 0L

    suspend fun user(uid: String): UserInfo {
        users[uid]?.let { return it }
        val s = get("users/$uid")
        val u = UserInfo(s?.child("displayName")?.value as? String ?: tr("ผู้ใช้"), s?.child("avatar")?.value as? String ?: "", s?.child("isCreator")?.value == true)
        if (s != null) users[uid] = u
        return u
    }

    /** lives/{id} with status == "live", most viewers first. */
    suspend fun lives(): List<LiveItem>? {
        val s = get("lives") { it.orderByKey().limitToLast(50) } ?: return null
        return s.children.filter { it.child("status").value == "live" }.map { l ->
            val host = l.child("hostUid").value as? String ?: ""
            val u = user(host)
            val cover = (l.child("coverUrl").value as? String)?.takeIf { it.isNotBlank() } ?: u.avatar
            LiveItem(l.key ?: "", host, l.child("title").value as? String ?: "", u.name, cover, num(l, "viewerCount").toInt(), u.verified)
        }.sortedByDescending { it.viewers }
    }

    /** Fills NearbyRepo.lastNearby once per app run (one radar call); later Home visits reuse the cache. */
    suspend fun nearbyOnce(ctx: android.content.Context) {
        if (nearbyTried || NearbyRepo.lastNearby.value != null) return
        nearbyTried = true
        NearbyRepo.nearby(NearbyRepo.loadRadius(ctx))
    }

    suspend fun postsByAuthors(uids: List<String>): List<FeedPost> = coroutineScope {
        uids.distinct().take(12).map { a -> async { FeedRepo.feed(null, null, a)?.first ?: emptyList() } }.awaitAll()
            .flatten().distinctBy { it.id }.sortedByDescending { it.createdAt }
    }

    /** Unread chats (last message from someone else, newer than my read receipt) + unseen notifications. */
    suspend fun refreshCounts() {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: run { unreadChats.value = 0; notifCount.value = 0; return }
        val uc = get("userChats/$me")
        val ids = uc?.children?.sortedByDescending { (it.value as? Number)?.toLong() ?: 0L }?.take(30)?.mapNotNull { it.key } ?: emptyList()
        val unread = coroutineScope {
            ids.map { id -> async {
                val c = get("chats/$id") ?: return@async false
                val lastAt = num(c, "lastAt"); val sender = c.child("lastSenderUid").value as? String
                val rr = (get("readReceipts/$id/$me")?.value as? Number)?.toLong() ?: 0L
                lastAt > 0 && sender != null && sender != me && lastAt > rr
            } }.awaitAll().count { it }
        }
        unreadChats.value = unread
        val reqs = runCatching { NearbyRepo.requests().size }.getOrDefault(0)
        val matches = get("matchNotifications/$me")?.children?.count { it.child("seen").value != true } ?: 0
        notifCount.value = reqs + matches
    }

    data class MatchNote(val id: String, val otherUid: String, val at: Long, val seen: Boolean)
    suspend fun matchNotes(): List<MatchNote> {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyList()
        return get("matchNotifications/$me")?.children?.map {
            MatchNote(it.key ?: "", it.child("otherUid").value as? String ?: "", num(it, "at"), it.child("seen").value == true)
        }?.sortedByDescending { it.at }?.take(20) ?: emptyList()
    }
    fun markMatchSeen(id: String) {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.getReference("matchNotifications/$me/$id/seen").setValue(true)
    }
}

/** "~1.2 km" / "~500 m" (brain) → "1.2 กม." / "500 ม." */
fun thaiDistance(d: String): String = d.trim().removePrefix("~").trim().let {
    when { it.endsWith("km") -> it.removeSuffix("km").trim() + " กม."; it.endsWith("m") -> it.removeSuffix("m").trim() + " ม."; else -> it }
}

fun thaiTimeAgo(t: Long): String {
    val s = ((System.currentTimeMillis() - t) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> tr("เมื่อสักครู่")
        s < 3600 -> "${s / 60} นาทีที่แล้ว"
        s < 86400 -> "${s / 3600} ชั่วโมงที่แล้ว"
        s < 86400L * 30 -> "${s / 86400} วันที่แล้ว"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale("th", "TH")).format(java.util.Date(t))
    }
}

@Composable
private fun rememberUserInfo(uid: String): HomeRepo.UserInfo? {
    val v by produceState<HomeRepo.UserInfo?>(null, uid) { value = if (uid.isBlank()) null else HomeRepo.user(uid) }
    return v
}

@Composable
private fun RingAvatar(url: String, name: String, size: Dp, ring: Dp = 2.dp, glow: Boolean = true) {
    Box(Modifier.size(size).then(if (glow) Modifier.shadow(8.dp, CircleShape, ambientColor = HViolet, spotColor = HViolet) else Modifier)
        .clip(CircleShape).background(ringBrush).padding(ring).clip(CircleShape).background(HBg).padding(1.5.dp), contentAlignment = Alignment.Center) {
        Avatar(url, name, (size - ring * 2 - 3.dp).value.toInt())
    }
}

@Composable
private fun PlusDot() {
    Box(Modifier.size(15.dp).clip(CircleShape).background(Brush.linearGradient(listOf(HNeon, HViolet))), contentAlignment = Alignment.Center) {
        Text("+", color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp)
    }
}

@Composable
private fun WalletChip(onClick: () -> Unit, icon: @Composable () -> Unit, value: Int, color: Color) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = Color(0xFF1A1440), border = BorderStroke(1.dp, Color(0xFF5B3A8F))) {
        Row(Modifier.padding(start = 3.dp, end = 3.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            icon(); Spacer(Modifier.width(3.dp))
            Text(String.format(java.util.Locale.US, "%,d", value), color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.width(3.dp)); PlusDot()
        }
    }
}

/** Header from the mockup: avatar + FriendTalk♡ + name, coin and diamond pills, bell with red dot. */
@Composable
fun HomeHeader(currentUser: User, onAvatar: () -> Unit, onCoins: () -> Unit, onDiamonds: () -> Unit, onBell: () -> Unit, onAdmin: () -> Unit) {
    val notif by HomeRepo.notifCount.collectAsState()
    val signedIn = FirebaseAuth.getInstance().currentUser != null
    // Live profile (users/{uid}) so a new photo / name shows here right away; the sign-in copy is only the fallback.
    LaunchedEffect(signedIn) { if (signedIn) com.example.social.MyProfile.start() }
    val me = if (signedIn) com.example.social.MyProfile.me else null
    val headerAvatar = me?.avatar?.ifBlank { null } ?: currentUser.avatar
    val headerName = me?.displayName?.ifBlank { null } ?: currentUser.displayName
    Box(Modifier.fillMaxWidth().background(AppThemeMode.headerBrush)) {
        Column(Modifier.statusBarsPadding()) {
            // compact app bar: 34dp avatar, 18sp wordmark, small wallet chips, 22dp bell
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(CircleShape).clickable(onClick = onAvatar)) {
                    RingAvatar(headerAvatar, headerName.ifBlank { "F" }, 34.dp)
                    Box(Modifier.align(Alignment.BottomEnd).size(10.dp).clip(CircleShape).background(HBg).padding(2.dp).clip(CircleShape).background(HOnline))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Friend", color = PureWhite, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, maxLines = 1,
                            style = TextStyle(shadow = Shadow(Color(0xA0D6A0FF), blurRadius = 14f)))
                        Text("Talk", color = HNeon, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, maxLines = 1, style = neonGlow)
                        Icon(Icons.Outlined.FavoriteBorder, null, tint = Color(0xFFFF4FC0), modifier = Modifier.padding(start = 2.dp).size(14.dp))
                    }
                    Text(if (signedIn) headerName else tr("เข้าสู่ระบบ"), color = Color(0xFFD9D3F7), fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    WalletChip(onCoins, {
                        Box(Modifier.size(15.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFFFFD84A), Color(0xFFF2A20C)))), contentAlignment = Alignment.Center) {
                            Text("$", color = Color(0xFF8A5200), fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 9.sp)
                        }
                    }, com.example.social.Wallet.coins, Color(0xFFFFE08A))
                    WalletChip(onDiamonds, { Text("💎", fontSize = 11.sp, lineHeight = 11.sp) }, com.example.social.Wallet.diamonds, Color(0xFFBFE8FF))
                    if (currentUser.role == UserRole.SUPERADMIN || currentUser.role == UserRole.ADMIN || currentUser.role == UserRole.MODERATOR)
                        Icon(Icons.Filled.AdminPanelSettings, tr("แอดมิน"), tint = Amber400, modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onAdmin))
                    Box(Modifier.clip(CircleShape).clickable(onClick = onBell).padding(2.dp)) {
                        Icon(Icons.Outlined.Notifications, tr("การแจ้งเตือน"), tint = Color(0xFFE9E4FF), modifier = Modifier.size(22.dp))
                        if (notif > 0) Box(Modifier.align(Alignment.TopEnd).size(8.dp).shadow(4.dp, CircleShape, ambientColor = HRed, spotColor = HRed).clip(CircleShape).background(HRed))
                    }
                }
            }
            HorizontalDivider(color = AppThemeMode.divider.copy(alpha = 0.7f), thickness = 0.8.dp)
        }
    }
}

/** Search pill in the Home header style; opens the existing global search. */
@Composable
fun HomeSearchPill(onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(AppThemeMode.field)
        .border(1.5.dp, Color(0xBFBE5AFF), RoundedCornerShape(16.dp)).clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(androidx.compose.material.icons.Icons.Filled.Search, null, tint = Color(0xFFC48CFF), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(tr("ค้นหาเพื่อน หรือ #แฮชแท็ก…"), color = AppThemeMode.fieldHint, fontSize = 14.sp)
    }
}

@Composable
private fun HomeSectionTitle(icon: @Composable () -> Unit, title: String, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        icon(); Spacer(Modifier.width(6.dp))
        Text(title, color = HText, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(tr("ดูทั้งหมด ›"), color = HNeon, fontSize = 12.5.sp, modifier = Modifier.clickable(onClick = onSeeAll).padding(4.dp))
    }
}

@Composable
private fun EmptyRow(text: String, onClick: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, HLine, RoundedCornerShape(14.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
        Text(text, color = HMuted, fontSize = 13.sp)
    }
}

/** 'ไลฟ์สดตอนนี้' — real lives/{id} with status=live. */
@Composable
fun HomeLiveSection(onSeeAll: () -> Unit) {
    val tick = HomeRepo.reloadTick
    var lives by remember { mutableStateOf<List<HomeRepo.LiveItem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    // A database read can wait forever when the connection is slow; give up after 6 s so the section never stays on placeholders.
    LaunchedEffect(tick) { val r = kotlinx.coroutines.withTimeoutOrNull(6000) { HomeRepo.lives() }; failed = r == null; lives = r ?: emptyList() }
    val cardW = ((LocalConfiguration.current.screenWidthDp - 28) / 2.75f).dp
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp)) {
        HomeSectionTitle({ Icon(Icons.Filled.Videocam, null, tint = HNeon, modifier = Modifier.size(22.dp)) }, tr("ไลฟ์สดตอนนี้"), onSeeAll)
        Spacer(Modifier.height(10.dp))
        val l = lives
        when {
            l == null -> Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { Box(Modifier.width(cardW).aspectRatio(0.72f).clip(RoundedCornerShape(18.dp)).background(AppThemeMode.skeleton)) }
            }
            l.isEmpty() -> if (!ShowcaseLiveRow(cardW, onSeeAll)) EmptyRow(if (failed) tr("โหลดไลฟ์ไม่สำเร็จ แตะเพื่อลองใหม่") else tr("ยังไม่มีใครไลฟ์ตอนนี้"), if (failed) ({ HomeRepo.reloadTick++ }) else null)
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(l.take(10), key = { _, it -> it.id }) { i, it -> LiveCard(it, cardW, i == 0, onSeeAll) }
            }
        }
    }
    HorizontalDivider(color = HLine.copy(alpha = 0.6f), thickness = 0.8.dp)
}

@Composable
private fun LiveCard(l: HomeRepo.LiveItem, w: Dp, highlight: Boolean, onClick: () -> Unit) {
    LiveTile(l.cover.ifBlank { null }, l.name, l.title, w, highlight,
        topLeft = { TilePill("LIVE", HRed) },
        topRight = { TilePill(com.example.ui.components.compactNum(l.viewers), Color(0x80000000)) { Icon(Icons.Outlined.Visibility, null, tint = PureWhite, modifier = Modifier.size(11.dp)) } },
        verified = l.verified, onClick = onClick)
}

/** 'คนใกล้คุณ' — real radar result (one /radar/nearby call per app run, then cached). */
@Composable
fun HomeNearbySection(onSeeAll: () -> Unit) {
    val ctx = LocalContext.current
    val list by NearbyRepo.lastNearby.collectAsState()
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { HomeRepo.nearbyOnce(ctx); done = true }
    var open by remember { mutableStateOf<NearbyUser?>(null) }
    val itemW = ((LocalConfiguration.current.screenWidthDp - 16) / 5.2f).dp
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp)) {
        HomeSectionTitle({ Icon(Icons.Filled.LocationOn, null, tint = HNeon, modifier = Modifier.size(22.dp)) }, tr("คนใกล้คุณ"), onSeeAll)
        Spacer(Modifier.height(10.dp))
        val l = list
        when {
            l == null && !done -> Row(Modifier.padding(horizontal = 8.dp)) { repeat(5) { Box(Modifier.width(itemW), contentAlignment = Alignment.Center) { Box(Modifier.size(66.dp).clip(CircleShape).background(AppThemeMode.skeleton)) } } }
            l == null -> { if (!ShowcaseNearbyRow(itemW, onSeeAll)) EmptyRow(tr("เปิดเรดาร์เพื่อดูคนใกล้คุณ ›"), onSeeAll) }
            l.isEmpty() -> { if (!ShowcaseNearbyRow(itemW, onSeeAll)) EmptyRow(tr("ยังไม่พบคนใกล้คุณ"), onSeeAll) }
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                items(l.take(12), key = { it.profile.uid }) { n ->
                    NearbyTile(itemW, n.profile.displayName, thaiDistance(n.distance), online = true, onClick = { open = n }) { d ->
                        Avatar(n.profile.avatar, n.profile.displayName, d.value.toInt())
                    }
                }
            }
        }
    }
    open?.let { n -> UserProfileDialog(n.profile.uid, thaiDistance(n.distance)) { open = null } }
}

/** Mockup composer bar: ✎ "โพสต์..." [image]. Tapping opens the full "สร้างโพสต์" screen (CreatePostDialog). */
@Composable
fun HomeComposerBar(onPosted: (FeedPost) -> Unit) {
    var open by remember { mutableStateOf<String?>(null) } // null = closed, "" = text, "image" = open with photo picker
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = HViolet, spotColor = HViolet).clip(RoundedCornerShape(16.dp))
            .background(AppThemeMode.field).border(1.5.dp, Color(0xBFBE5AFF), RoundedCornerShape(16.dp)).clickable { open = "" }.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Edit, null, tint = Color(0xFFC48CFF), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr("โพสต์..."), color = AppThemeMode.fieldHint, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.Image, tr("แนบรูป"), tint = Color(0xFFC48CFF), modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).clickable { open = "image" })
        }
    }
    open?.let { start -> CreatePostDialog(startWith = start.ifBlank { null }, onDismiss = { open = null }, onPosted = onPosted) }
}

/** Posts for 'ใกล้คุณ' (radar people) and 'กำลังติดตาม' (friends). 'ทั้งหมด' uses the paged RealFeedState. */
class HomeTabFeed(private val scope: CoroutineScope) {
    val posts = mutableStateListOf<FeedPost>()
    var loading by mutableStateOf(false)
    var loadedTab by mutableStateOf("")
    fun load(tab: String) {
        loading = true
        scope.launch {
            val uids = if (tab == "near") (NearbyRepo.lastNearby.value ?: emptyList()).map { it.profile.uid } else NearbyRepo.friends().map { it.uid }
            val r = HomeRepo.postsByAuthors(uids)
            posts.clear(); posts.addAll(r); loadedTab = tab; loading = false
        }
    }
    fun update(id: String, f: (FeedPost) -> FeedPost) { val i = posts.indexOfFirst { it.id == id }; if (i >= 0) posts[i] = f(posts[i]) }
    fun toggleLike(p: FeedPost) {
        val on = !p.likedByMe
        update(p.id) { it.copy(likedByMe = on, likeCount = (it.likeCount + if (on) 1 else -1).coerceAtLeast(0)) }
        scope.launch { val n = FeedRepo.setLike(p.id, on); if (n == null) update(p.id) { p } else update(p.id) { it.copy(likeCount = n) } }
    }
    fun delete(p: FeedPost) { scope.launch { if (FeedRepo.deletePost(p.id)) posts.removeAll { it.id == p.id } } }
}

@Composable
fun rememberHomeTabFeed(): HomeTabFeed { val s = rememberCoroutineScope(); return remember { HomeTabFeed(s) } }

/** Composer + tabs + 2-column real feed, as LazyColumn items. */
fun LazyListScope.homeFeedItems(all: RealFeedState, tabFeed: HomeTabFeed, tab: String, onTab: (String) -> Unit, nearby: List<NearbyUser>?) {
    item(key = "home_composer") { com.example.ui.components.FeatureLockGate("posting") { HomeComposerBar(onPosted = { onTab("all"); all.posts.add(0, it) }) } }
    item(key = "home_tabs") { HomeFeedTabs(tab, onTab) }
    val isAll = tab == "all"
    val posts: List<FeedPost> = if (isAll) all.posts.filter { all.visible(it) } else tabFeed.posts
    val loading = if (isAll) all.loading else tabFeed.loading || tabFeed.loadedTab != tab
    if (isAll && all.error.isNotBlank()) item(key = "home_err") { Text(all.error, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
    if (!loading && posts.isEmpty() && (!isAll || all.loaded)) item(key = "home_empty") {
        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
            Text(when (tab) { "near" -> if (nearby == null) tr("เปิดเรดาร์เพื่อดูโพสต์จากคนใกล้คุณ") else tr("ยังไม่มีโพสต์จากคนใกล้คุณ"); "following" -> tr("ยังไม่มีโพสต์จากเพื่อนที่คุณติดตาม"); else -> tr("ยังไม่มีโพสต์ เป็นคนแรกที่โพสต์เลย!") },
                color = HMuted, fontSize = 13.sp)
        }
    }
    val rows = posts.chunked(2)
    val adEvery = (com.example.ads.AdPlacements.inFeedEvery / 2).coerceAtLeast(1)
    val distances = nearby?.associate { it.profile.uid to thaiDistance(it.distance) } ?: emptyMap()
    itemsIndexed(rows, key = { _, r -> "home_row_" + r[0].id }) { idx, row ->
        if (idx > 0 && idx % adEvery == 0) com.example.ads.PlacementBanner("inFeed", Modifier.padding(vertical = 6.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            row.forEach { p ->
                HomePostCard(p, distances[p.authorId] ?: "", Modifier.weight(1f),
                    onLike = { if (isAll) all.toggleLike(p) else tabFeed.toggleLike(p) },
                    onComments = { all.commentsFor = p },
                    onDelete = { if (isAll) all.delete(p) else tabFeed.delete(p) },
                    onCommentCount = { n -> if (isAll) all.update(p.id) { it.copy(commentCount = n) } else tabFeed.update(p.id) { it.copy(commentCount = n) } })
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        if (isAll && idx == rows.lastIndex) LaunchedEffect(row[0].id, all.posts.size) { all.loadMore() }
    }
    if (loading) item(key = "home_loading") { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HNeon) } }
}

@Composable
fun HomeFeedTabs(selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.padding(horizontal = 6.dp)) {
            listOf("all" to tr("ทั้งหมด"), "near" to tr("ใกล้คุณ"), "following" to tr("กำลังติดตาม")).forEach { (k, label) ->
                val on = selected == k
                Column(Modifier.width(IntrinsicSize.Max).clickable { if (!on) onSelect(k) }.padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = if (on) HNeon else HMuted, fontSize = 14.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                        style = if (on) neonGlow else TextStyle.Default, modifier = Modifier.padding(vertical = 6.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (on) Brush.horizontalGradient(listOf(HNeon, HViolet)) else SolidColor(Color.Transparent)))
                }
            }
        }
        HorizontalDivider(color = HLine.copy(alpha = 0.6f), thickness = 0.8.dp)
    }
}

@Composable
fun HomePostCard(p: FeedPost, distance: String, modifier: Modifier, onLike: () -> Unit, onComments: () -> Unit, onDelete: () -> Unit, onCommentCount: (Int) -> Unit = {}) {
    val ctx = LocalContext.current
    val me = FirebaseAuth.getInstance().currentUser?.uid
    val info = rememberUserInfo(p.authorId)
    var confirm by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var author by remember { mutableStateOf(false) }
    if (author) UserProfileDialog(p.authorId) { author = false }
    if (report) com.example.ui.social.ReportDialog("post", p.id, p.authorId) { report = false }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(tr("ลบโพสต์นี้?")) },
        confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text(tr("ลบ"), color = Rose500) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("ยกเลิก")) } })
    val more: @Composable () -> Unit = { PostMoreButton(p.authorId == me, onDelete = { confirm = true }, onReport = { report = true }) }
    // tap the card → full post (text, all photos, video, place, comments)
    var detail by remember { mutableStateOf(false) }
    if (detail) PostDetailDialog(p, onDismiss = { detail = false }, onLike = onLike, onDelete = onDelete, onCommentCount = onCommentCount)
    Surface(onClick = { detail = true }, shape = RoundedCornerShape(16.dp), color = HCard, border = BorderStroke(1.dp, HLine), modifier = modifier) {
        Column {
            Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.clip(CircleShape).clickable { author = true }) { RingAvatar(p.authorAvatar, p.authorName, 34.dp, ring = 1.5.dp, glow = false) }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.authorName, color = HText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (info?.verified == true) Icon(Icons.Filled.Verified, null, tint = HNeon, modifier = Modifier.padding(start = 3.dp).size(14.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(thaiTimeAgo(p.createdAt) + " · " + privacyIcon(p.visibility), color = HMuted, fontSize = 9.5.sp, maxLines = 1)
                        if (distance.isNotBlank()) {
                            Text(" • ", color = HMuted, fontSize = 9.5.sp)
                            Icon(Icons.Outlined.Place, null, tint = HMuted, modifier = Modifier.size(10.dp))
                            Text(distance, color = HMuted, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                more()
            }
            p.feeling?.let { f -> Text(tr(f.display()), color = HMuted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 2.dp)) }
            if (p.placeLabel.isNotBlank()) Row(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp).clip(RoundedCornerShape(6.dp))
                .clickable { openPlaceOnMap(ctx, p.placeLabel, p.placeLat, p.placeLng) }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, tr("ดูบนแผนที่"), tint = HNeon, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(2.dp))
                Text(p.placeLabel, color = Color(0xFFFF8BCB), fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (p.text.isNotBlank()) Text(p.text, color = HText, fontSize = 12.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp))
            if (p.videoUrl.isNotBlank()) PostVideoThumb(p, Modifier.padding(horizontal = 6.dp).fillMaxWidth().aspectRatio(1f / 1.08f))
            else p.imageUrls.firstOrNull()?.let { url ->
                val video = Regex("\\.(mp4|webm|mov|m4v)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(Uri.decode(url))
                Box(Modifier.padding(horizontal = 6.dp).fillMaxWidth().aspectRatio(1f / 1.08f).clip(RoundedCornerShape(10.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                    if (!video) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0x73000000)).border(2.dp, Color(0xE6FFFFFF), CircleShape)
                        .clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setDataAndType(Uri.parse(url), "video/*")) } },
                        contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, tr("เล่นวิดีโอ"), tint = PureWhite, modifier = Modifier.size(26.dp)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onLike), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (p.likedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, tr("ถูกใจ"), tint = if (p.likedByMe) Color(0xFFFF2D6F) else HSoft, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(3.dp)); Text(com.example.ui.components.compactNum(p.likeCount), color = HSoft, fontSize = 11.5.sp)
                }
                Spacer(Modifier.width(10.dp))
                Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onComments), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ChatBubbleOutline, tr("ความคิดเห็น"), tint = HSoft, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(3.dp)); Text(com.example.ui.components.compactNum(p.commentCount), color = HSoft, fontSize = 11.5.sp)
                }
                Spacer(Modifier.width(10.dp))
                Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, (p.authorName + ": " + p.text).trim() + "\n— FriendTalk")
                    runCatching { ctx.startActivity(Intent.createChooser(send, tr("แชร์โพสต์"))) }
                }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.Reply, tr("แชร์"), tint = HSoft, modifier = Modifier.size(17.dp).graphicsLayer(scaleX = -1f))
                    Spacer(Modifier.width(3.dp)); Text("0", color = HSoft, fontSize = 11.5.sp)
                }
            }
        }
    }
}

@Composable
private fun PostMoreButton(mine: Boolean, onDelete: () -> Unit, onReport: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Icon(Icons.Filled.MoreVert, tr("เพิ่มเติม"), tint = HMuted, modifier = Modifier.size(18.dp).clip(CircleShape).clickable { menu = true })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (mine) DropdownMenuItem(text = { Text(tr("ลบโพสต์"), color = Rose500) }, onClick = { menu = false; onDelete() })
            else DropdownMenuItem(text = { Text(tr("รายงานโพสต์")) }, onClick = { menu = false; onReport() })
        }
    }
}

/** Real notifications: friend requests (accept / decline) and new matches. */
@Composable
fun HomeNotificationsDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var reqs by remember { mutableStateOf<List<FriendRequest>?>(null) }
    var matches by remember { mutableStateOf<List<HomeRepo.MatchNote>>(emptyList()) }
    LaunchedEffect(Unit) {
        reqs = runCatching { NearbyRepo.requests() }.getOrDefault(emptyList())
        matches = HomeRepo.matchNotes()
        matches.filter { !it.seen }.forEach { HomeRepo.markMatchSeen(it.id) }
        HomeRepo.refreshCounts()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.7f), shape = RoundedCornerShape(22.dp), color = AppThemeMode.dialog, border = BorderStroke(1.dp, Color(0xFF4A3590))) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("การแจ้งเตือน"), color = HText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(tr("ปิด"), color = HSoft) }
                }
                val r = reqs
                if (r == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HNeon) }
                else if (r.isEmpty() && matches.isEmpty()) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(tr("ยังไม่มีการแจ้งเตือน"), color = HMuted) }
                else androidx.compose.foundation.lazy.LazyColumn {
                    items(r, key = { "req_" + it.profile.uid }) { q ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(q.profile.avatar, q.profile.displayName, 42); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${q.profile.displayName} ขอเป็นเพื่อน", color = HText, fontSize = 14.sp, maxLines = 2)
                                Text(thaiTimeAgo(q.at), color = HMuted, fontSize = 11.sp)
                            }
                            TextButton(onClick = { scope.launch { NearbyRepo.respond(q.profile.uid, true); reqs = reqs?.filter { it !== q }; HomeRepo.refreshCounts() } }) { Text(tr("ตอบรับ"), color = HNeon) }
                            TextButton(onClick = { scope.launch { NearbyRepo.respond(q.profile.uid, false); reqs = reqs?.filter { it !== q }; HomeRepo.refreshCounts() } }) { Text(tr("ลบ"), color = HMuted) }
                        }
                    }
                    items(matches, key = { "m_" + it.id }) { m ->
                        val u = rememberUserInfo(m.otherUid)
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(u?.avatar ?: "", u?.name ?: "?", 42); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("คุณกับ ${u?.name ?: tr("ผู้ใช้")} แมตช์กันแล้ว 💞", color = HText, fontSize = 14.sp, maxLines = 2)
                                Text(thaiTimeAgo(m.at), color = HMuted, fontSize = 11.sp)
                            }
                            if (!m.seen) Box(Modifier.size(8.dp).clip(CircleShape).background(HNeon))
                        }
                    }
                }
            }
        }
    }
}
