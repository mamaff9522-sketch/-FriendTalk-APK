package com.example.ui.social

import androidx.compose.foundation.lazy.itemsIndexed
import com.example.ui.components.tr
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.example.ui.components.ft.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.social.FeedComment
import com.example.social.FeedPost
import com.example.social.FeedRepo
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** State for the real post feed (paged, newest first). */
class RealFeedState(private val scope: CoroutineScope, val tag: String? = null, val author: String? = null) {
    val posts = mutableStateListOf<FeedPost>()
    var cursor by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var loaded by mutableStateOf(false)
    var error by mutableStateOf("")
    var commentsFor by mutableStateOf<FeedPost?>(null)
    var filter by mutableStateOf("all")
    fun visible(p: FeedPost) = when (filter) { "photos" -> p.imageUrls.isNotEmpty(); "places" -> p.placeLabel.isNotBlank(); "liked" -> p.likedByMe; else -> true }
    var openTag by mutableStateOf<String?>(null)
    val endReached get() = loaded && cursor == null && posts.isNotEmpty()

    suspend fun refresh() {
        loading = true
        val r = FeedRepo.feed(null, tag, author)
        if (r != null) { posts.clear(); posts.addAll(r.first); cursor = r.second; error = "" } else error = tr("โหลดฟีดไม่สำเร็จ")
        loading = false; loaded = true
        if (r != null && posts.isEmpty() && cursor != null) loadMore()
    }
    fun loadMore() {
        val c = cursor ?: return
        if (loading) return
        loading = true
        scope.launch {
            var next: String? = c
            // keep going through pages whose posts were all hidden by visibility, so scrolling always reaches the first post
            while (next != null) {
                val r = FeedRepo.feed(next, tag, author) ?: break
                val fresh = r.first.filter { np -> posts.none { it.id == np.id } }
                posts.addAll(fresh); cursor = r.second; next = if (fresh.isEmpty()) r.second else null
            }
            loading = false
        }
    }
    fun update(id: String, f: (FeedPost) -> FeedPost) { val i = posts.indexOfFirst { it.id == id }; if (i >= 0) posts[i] = f(posts[i]) }
    fun toggleLike(p: FeedPost) {
        val on = !p.likedByMe
        update(p.id) { it.copy(likedByMe = on, likeCount = (it.likeCount + if (on) 1 else -1).coerceAtLeast(0)) }
        scope.launch {
            val n = FeedRepo.setLike(p.id, on)
            if (n == null) update(p.id) { it.copy(likedByMe = p.likedByMe, likeCount = p.likeCount) } else update(p.id) { it.copy(likeCount = n) }
        }
    }
    fun setCommentsOff(p: FeedPost, off: Boolean) { scope.launch { if (FeedRepo.setCommentsOff(p.id, off)) update(p.id) { it.copy(commentsOff = off) } else error = tr("เปลี่ยนการตั้งค่าความคิดเห็นไม่สำเร็จ") } }
    fun setVisibility(p: FeedPost, v: String) { scope.launch { if (FeedRepo.setVisibility(p.id, v)) update(p.id) { it.copy(visibility = v) } else error = tr("เปลี่ยนการมองเห็นไม่สำเร็จ") } }
    fun delete(p: FeedPost) { scope.launch { if (FeedRepo.deletePost(p.id)) posts.removeAll { it.id == p.id } else error = tr("ลบไม่สำเร็จ") } }
}

@Composable
fun rememberRealFeedState(): RealFeedState {
    val scope = rememberCoroutineScope()
    val s = remember { RealFeedState(scope) }
    LaunchedEffect(Unit) { s.refresh() }
    return s
}

/** Feed section content (composer + real posts). Used by the Home layout's "feed" section. */
fun LazyListScope.realFeedItems(state: RealFeedState) {
    if (state.tag == null && state.author == null) item(key = "real_composer") { com.example.ui.components.FeatureLockGate("posting") { PostComposer(onPosted = { state.posts.add(0, it) }) } }
    if (state.error.isNotBlank()) item(key = "real_err") { Text(state.error, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
    if (state.loaded && state.posts.isEmpty()) item(key = "real_empty") {
        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
            Text(tr("ยังไม่มีโพสต์ เป็นคนแรกที่โพสต์เลย!"), color = Slate400, fontSize = 14.sp)
        }
    }
    val shown = state.posts.filter { state.visible(it) }
    if (state.filter != "all" && state.loaded && shown.isEmpty()) item(key = "real_fempty") {
        Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr("ยังไม่มีโพสต์ที่ตรงกับตัวกรองนี้"), color = Slate400, fontSize = 14.sp)
            if (!state.endReached) TextButton(onClick = { state.loadMore() }) { Text(tr("โหลดโพสต์เพิ่ม"), color = Pink400) }
        }
    }
    itemsIndexed(shown, key = { _, it -> "rp_" + it.id }) { idx, p ->
        if (idx > 0 && idx % com.example.ads.AdPlacements.inFeedEvery == 0) com.example.ads.PlacementBanner("inFeed", Modifier.padding(vertical = 6.dp))
        RealPostCard(p, onLike = { state.toggleLike(p) }, onComments = { state.commentsFor = p }, onDelete = { state.delete(p) },
            onTag = { state.openTag = it }, onVisibility = { v -> when (v) { "#comments_on" -> state.setCommentsOff(p, false); "#comments_off" -> state.setCommentsOff(p, true); else -> state.setVisibility(p, v) } })
        if (p.id == shown.lastOrNull()?.id) LaunchedEffect(p.id, state.posts.size) { state.loadMore() }
    }
    if (state.endReached && !state.loading) item(key = "real_end") { Text(tr("— ดูครบทุกโพสต์แล้ว —"), color = Slate400, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    if (state.loading) item(key = "real_loading") { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pink500) } }
}

@Composable
fun PostComposer(onPosted: (FeedPost) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var showPlace by remember { mutableStateOf(false) }
    var attachLoc by remember { mutableStateOf(false) }
    var visibility by remember { mutableStateOf("public") }
    val limits by com.example.ads.AdRoom.limits.collectAsState()
    LaunchedEffect(Unit) { com.example.ads.AdRoom.refresh() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { images = it.take(4) }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res -> attachLoc = res.values.any { it } }
    val posting = limits?.posting
    if (posting != null && !posting.first) {
        Surface(shape = RoundedCornerShape(12.dp), color = Slate800, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("🔒 โพสต์ไม่ได้ตอนนี้: ${posting.second}  (ยังอ่านฟีด กดไลก์ และคอมเมนต์ได้ตามปกติ)", color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
        }
        return
    }
    val me = FirebaseAuth.getInstance().currentUser
    Surface(shape = RoundedCornerShape(18.dp), color = Slate900, border = androidx.compose.foundation.BorderStroke(1.dp, Slate700.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(me?.photoUrl?.toString().orEmpty(), me?.displayName ?: tr("ฉัน"), 44)
                Spacer(Modifier.width(10.dp))
                TextField(value = text, onValueChange = { if (it.length <= 2000) text = it }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    placeholder = { Text(tr("คุณกำลังคิดอะไรอยู่?"), color = Slate400) }, maxLines = 6,
                    colors = TextFieldDefaults.colors(focusedContainerColor = Slate800, unfocusedContainerColor = Slate800, focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, focusedTextColor = White, unfocusedTextColor = White))
            }
            if (showPlace) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                OutlinedTextField(value = place, onValueChange = { if (it.length <= 80) place = it }, modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text(tr("ชื่อสถานที่ เช่น สยามพารากอน"), color = Slate400) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                FilterChip(selected = attachLoc, onClick = {
                    if (attachLoc) attachLoc = false
                    else if (hasLocPerm(ctx)) attachLoc = true
                    else locPerm.launch(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION))
                }, label = { Text(tr("แนบพิกัดคร่าว ๆ (~1 กม.)"), fontSize = 10.sp) }, modifier = Modifier.padding(start = 4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                listOf("public" to tr("🌐 สาธารณะ"), "friends" to tr("👥 เพื่อน"), "only_me" to tr("🔒 เฉพาะฉัน")).forEach { (k, l) ->
                    val sel = visibility == k
                    Surface(onClick = { visibility = k }, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f), color = if (sel) Pink500.copy(alpha = 0.18f) else Slate800,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) Pink500 else Slate700)) {
                        Text(l, fontSize = 12.sp, color = White, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
                    }
                }
            }
            if (images.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                items(images) { u -> AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))) }
            }
            HorizontalDivider(color = Slate700.copy(alpha = 0.5f), modifier = Modifier.padding(top = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("🖼 รูป (${images.size}/4)", color = Slate200, fontSize = 11.sp, maxLines = 1) }
                    TextButton(onClick = { msg = tr("โพสต์คลิปยังไม่เปิดให้ใช้") }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(tr("🎬 คลิป (0/1)"), color = Slate400, fontSize = 11.sp, maxLines = 1) }
                    TextButton(onClick = { showPlace = !showPlace; if (!showPlace) { place = ""; attachLoc = false } }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(tr("📍 ตำแหน่ง"), color = Slate200, fontSize = 11.sp, maxLines = 1) }
                    if (images.isNotEmpty()) TextButton(onClick = { images = emptyList() }) { Text(tr("ล้างรูป"), color = Slate400) }
                }
                val can = !busy && (text.isNotBlank() || images.isNotEmpty())
                Box(Modifier.clip(RoundedCornerShape(22.dp)).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Pink500, Pink400))).alpha(if (can) 1f else 0.5f)
                    .clickable(enabled = can) {
                        busy = true; msg = ""
                        scope.launch {
                            val loc = if (attachLoc && place.isNotBlank()) currentLocation(ctx) else null
                            FeedRepo.createPost(ctx, text, images, place, loc?.latitude, loc?.longitude, visibility).onSuccess { onPosted(it); text = ""; images = emptyList(); place = ""; showPlace = false; attachLoc = false }.onFailure { msg = it.message ?: tr("โพสต์ไม่สำเร็จ") }
                            busy = false
                        }
                    }.padding(horizontal = 16.dp, vertical = 9.dp)) { Text(if (busy) tr("กำลังโพสต์…") else tr("➤ โพสต์"), color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
            if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp)
        }
    }
}

private val fmt = SimpleDateFormat("d MMM HH:mm", Locale("th", "TH"))

@Composable
fun RealPostCard(p: FeedPost, onLike: () -> Unit, onComments: () -> Unit, onDelete: () -> Unit, onTag: (String) -> Unit = {}, onVisibility: (String) -> Unit = {}) {
    val me = FirebaseAuth.getInstance().currentUser?.uid
    var confirm by remember { mutableStateOf(false) }
    var showAuthor by remember { mutableStateOf(false) }
    if (showAuthor) UserProfileDialog(p.authorId) { showAuthor = false }
    val fs = FeedStyle.cfg
    Surface(shape = RoundedCornerShape(fs.cardCornerRadiusDp.coerceIn(0, 40).dp), color = Slate900, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = (fs.postSpacingDp.coerceIn(0, 48) / 2).dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable { showAuthor = true }) { Avatar(p.authorAvatar, p.authorName, 36) }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.authorName, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(fmt.format(Date(p.createdAt)) + "  " + when (p.visibility) { "friends" -> "👥"; "only_me" -> "🔒"; else -> "🌐" }, color = Slate400, fontSize = 10.sp)
                    if (p.placeLabel.isNotBlank()) Text("📍 ${p.placeLabel}", color = Pink400, fontSize = 11.sp)
                }
                if (p.authorId == me) {
                    var visMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { visMenu = true }) { Text("👁", fontSize = 12.sp) }
                        DropdownMenu(expanded = visMenu, onDismissRequest = { visMenu = false }) {
                            listOf("public" to tr("🌐 สาธารณะ"), "friends" to tr("👥 เพื่อน"), "only_me" to tr("🔒 เฉพาะฉัน")).forEach { (k, l) ->
                                DropdownMenuItem(text = { Text(l) }, onClick = { visMenu = false; onVisibility(k) })
                            }
                            DropdownMenuItem(text = { Text(if (p.commentsOff) tr("💬 เปิดความคิดเห็น") else tr("🚫 ปิดความคิดเห็น")) }, onClick = { visMenu = false; onVisibility(if (p.commentsOff) "#comments_on" else "#comments_off") })
                        }
                    }
                    TextButton(onClick = { confirm = true }) { Text(tr("ลบ"), color = Rose500, fontSize = 12.sp) }
                } else {
                    var rep by remember { mutableStateOf(false) }
                    TextButton(onClick = { rep = true }) { Text("⋯", color = Slate400, fontSize = 16.sp) }
                    if (rep) ReportDialog("post", p.id, p.authorId) { rep = false }
                }
            }
            if (p.text.isNotBlank()) Text(p.text, color = White, fontSize = (14 * fs.textSizeScale.coerceIn(0.7f, 1.6f)).sp, modifier = Modifier.padding(vertical = 6.dp))
            if (p.hashtags.isNotEmpty()) Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                p.hashtags.forEach { t -> Text("#$t", color = Cyan400, fontSize = 13.sp, modifier = Modifier.clickable { onTag(t) }.padding(end = 8.dp, bottom = 4.dp)) }
            }
            p.imageUrls.forEach { url ->
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLike) { Text((if (p.likedByMe) "❤️ " else "🤍 ") + p.likeCount, color = White) }
                TextButton(onClick = onComments) { Text("💬 ${p.commentCount}", color = White) }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(tr("ลบโพสต์นี้?")) },
        confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text(tr("ลบ"), color = Rose500) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("ยกเลิก")) } })
}

/** Comments sheet with one level of replies. */
@Composable
fun RealCommentsDialog(state: RealFeedState) {
    val post = state.commentsFor ?: return
    val me = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()
    var comments by remember(post.id) { mutableStateOf<List<FeedComment>?>(null) }
    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<FeedComment?>(null) }
    var msg by remember { mutableStateOf("") }
    suspend fun reload() { comments = FeedRepo.comments(post.id) ?: emptyList() }
    LaunchedEffect(post.id) { reload() }
    Dialog(onDismissRequest = { state.commentsFor = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.85f), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = Slate950) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("ความคิดเห็น"), color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { state.commentsFor = null }) { Text(tr("ปิด"), color = Slate300) }
                }
                val list = comments
                LazyColumn(Modifier.weight(1f)) {
                    if (list == null) item { CircularProgressIndicator(color = Pink500) }
                    else if (list.isEmpty()) item { Text(tr("ยังไม่มีความคิดเห็น"), color = Slate400, fontSize = 13.sp) }
                    else {
                        val top = list.filter { it.parentId == null }
                        top.forEach { c ->
                            item(key = c.id) { CommentRow(c, false, c.authorId == me, onReply = { replyTo = c }, onDelete = {
                                scope.launch { FeedRepo.deleteComment(post.id, c.id)?.let { n -> state.update(post.id) { it.copy(commentCount = n) }; reload() } }
                            }) }
                            list.filter { it.parentId == c.id }.forEach { rp ->
                                item(key = rp.id) { CommentRow(rp, true, rp.authorId == me, onReply = { replyTo = c }, onDelete = {
                                    scope.launch { FeedRepo.deleteComment(post.id, rp.id)?.let { n -> state.update(post.id) { it.copy(commentCount = n) }; reload() } }
                                }) }
                            }
                        }
                    }
                }
                replyTo?.let { Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ตอบกลับ ${it.authorName}", color = Pink400, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { replyTo = null }) { Text(tr("ยกเลิก"), color = Slate400, fontSize = 12.sp) }
                } }
                if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp)
                val off = state.posts.firstOrNull { it.id == post.id }?.commentsOff ?: post.commentsOff
                if (off) Text(tr("🚫 เจ้าของโพสต์ปิดความคิดเห็น"), color = Slate400, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = input, onValueChange = { if (it.length <= 1000) input = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text(tr("เขียนความคิดเห็น…"), color = Slate400) }, maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                    Spacer(Modifier.width(6.dp))
                    Button(enabled = input.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Pink500), onClick = {
                        val t = input; val parent = replyTo?.id; input = ""; replyTo = null
                        scope.launch {
                            val n = FeedRepo.addComment(post.id, t, parent)
                            if (n == null) { msg = FeedRepo.lastCommentError.ifBlank { tr("ส่งไม่สำเร็จ") }; input = t } else { msg = ""; state.update(post.id) { it.copy(commentCount = n) }; reload() }
                        }
                    }) { Text(tr("ส่ง")) }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(c: FeedComment, isReply: Boolean, mine: Boolean, onReply: () -> Unit, onDelete: () -> Unit) {
    var showAuthor by remember { mutableStateOf(false) }
    if (showAuthor) UserProfileDialog(c.authorId) { showAuthor = false }
    Row(Modifier.fillMaxWidth().padding(start = if (isReply) 40.dp else 0.dp, top = 6.dp, bottom = 2.dp)) {
        Box(Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable { showAuthor = true }) { Avatar(c.authorAvatar, c.authorName, if (isReply) 26 else 32) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(c.authorName, color = White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(c.text, color = Slate300, fontSize = 13.sp)
            Row {
                Text(tr("ตอบกลับ"), color = Slate400, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onReply).padding(end = 12.dp, top = 2.dp))
                if (mine) Text(tr("ลบ"), color = Rose500, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onDelete).padding(top = 2.dp))
            }
        }
    }
}

/** Posts with one hashtag (paged, visibility-filtered by the server). */
@Composable
fun TagFeedDialog(tag: String, onClose: () -> Unit) = FilteredFeedDialog("#$tag", tag, null, onClose)

/** Full post timeline of one user (newest first, infinite scroll to the first post; server applies visibility). */
@Composable
fun AuthorFeedDialog(uid: String, name: String, onClose: () -> Unit) = FilteredFeedDialog("โพสต์ของ $name", null, uid, onClose)

@Composable
fun FilteredFeedDialog(title: String, tag: String?, author: String?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val st = remember(tag, author) { RealFeedState(scope, tag, author) }
    LaunchedEffect(tag, author) { st.refresh() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding(), color = Slate950) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text(tr("ปิด"), color = Slate300) }
                }
                LazyColumn(Modifier.weight(1f)) { realFeedItems(st) }
            }
        }
    }
    RealCommentsDialog(st)
    st.openTag?.let { if (it != tag) TagFeedDialog(it) { st.openTag = null } else st.openTag = null }
}

/** Feed style from the published Remote UI config (admin 'จัด Feed': corner radius, spacing, text size). */
object FeedStyle { var cfg by androidx.compose.runtime.mutableStateOf(com.example.model.FeedSectionConfig()) }

/** Report a post/user to the admins (brain POST /reports). */
@Composable
fun ReportDialog(type: String, targetId: String, targetUid: String?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf(tr("สแปม")) }
    var msg by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text(if (type == "post") tr("รายงานโพสต์") else tr("รายงานผู้ใช้")) },
        text = { Column {
            listOf(tr("สแปม"), tr("เนื้อหาไม่เหมาะสม"), "เนื้อหาลามก/ทางเพศ", tr("คุกคาม/กลั่นแกล้ง"), "ข่มขู่/ความรุนแรง", "ถ้อยคำแสดงความเกลียดชัง", "เปิดเผยข้อมูลส่วนตัวผู้อื่น", tr("หลอกลวง"), "ขอเงิน/ฉ้อโกง", tr("บัญชีปลอม"), "แอบอ้างเป็นผู้อื่น", "ผู้เยาว์ / ชักชวนผู้เยาว์", "ยาเสพติด/ผิดกฎหมาย").forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { reason = r }) { RadioButton(selected = reason == r, onClick = { reason = r }); Text(r) }
            }
            if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = { scope.launch {
            val u = FirebaseAuth.getInstance().currentUser ?: return@launch
            val r = com.example.network.BrainApi.call(u, "POST", "/reports", org.json.JSONObject().put("targetType", type).put("targetId", targetId).put("targetUid", targetUid ?: "").put("reason", reason))
            if (r.code == 201) onClose() else msg = r.json?.optString("error") ?: tr("ส่งไม่สำเร็จ")
        } }) { Text(tr("ส่งรายงาน")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("ยกเลิก")) } })
}
