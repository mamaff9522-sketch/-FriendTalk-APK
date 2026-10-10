package com.example.ui.social

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
    var openTag by mutableStateOf<String?>(null)
    val endReached get() = loaded && cursor == null && posts.isNotEmpty()

    suspend fun refresh() {
        loading = true
        val r = FeedRepo.feed(null, tag, author)
        if (r != null) { posts.clear(); posts.addAll(r.first); cursor = r.second; error = "" } else error = "โหลดฟีดไม่สำเร็จ"
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
    fun setCommentsOff(p: FeedPost, off: Boolean) { scope.launch { if (FeedRepo.setCommentsOff(p.id, off)) update(p.id) { it.copy(commentsOff = off) } else error = "เปลี่ยนการตั้งค่าความคิดเห็นไม่สำเร็จ" } }
    fun setVisibility(p: FeedPost, v: String) { scope.launch { if (FeedRepo.setVisibility(p.id, v)) update(p.id) { it.copy(visibility = v) } else error = "เปลี่ยนการมองเห็นไม่สำเร็จ" } }
    fun delete(p: FeedPost) { scope.launch { if (FeedRepo.deletePost(p.id)) posts.removeAll { it.id == p.id } else error = "ลบไม่สำเร็จ" } }
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
    if (state.tag == null && state.author == null) item(key = "real_composer") { PostComposer(onPosted = { state.posts.add(0, it) }) }
    if (state.error.isNotBlank()) item(key = "real_err") { Text(state.error, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
    if (state.loaded && state.posts.isEmpty()) item(key = "real_empty") {
        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
            Text("ยังไม่มีโพสต์ เป็นคนแรกที่โพสต์เลย!", color = Slate400, fontSize = 14.sp)
        }
    }
    items(state.posts, key = { "rp_" + it.id }) { p ->
        RealPostCard(p, onLike = { state.toggleLike(p) }, onComments = { state.commentsFor = p }, onDelete = { state.delete(p) },
            onTag = { state.openTag = it }, onVisibility = { v -> when (v) { "#comments_on" -> state.setCommentsOff(p, false); "#comments_off" -> state.setCommentsOff(p, true); else -> state.setVisibility(p, v) } })
        if (p.id == state.posts.lastOrNull()?.id) LaunchedEffect(p.id) { state.loadMore() }
    }
    if (state.endReached && !state.loading) item(key = "real_end") { Text("— ดูครบทุกโพสต์แล้ว —", color = Slate400, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
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
    Surface(shape = RoundedCornerShape(16.dp), color = Slate800, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(value = text, onValueChange = { if (it.length <= 2000) text = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("คุณกำลังคิดอะไรอยู่?", color = Slate400) }, maxLines = 6,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
            if (showPlace) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                OutlinedTextField(value = place, onValueChange = { if (it.length <= 80) place = it }, modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("ชื่อสถานที่ เช่น สยามพารากอน", color = Slate400) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                FilterChip(selected = attachLoc, onClick = {
                    if (attachLoc) attachLoc = false
                    else if (hasLocPerm(ctx)) attachLoc = true
                    else locPerm.launch(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION))
                }, label = { Text("แนบพิกัดคร่าว ๆ (~1 กม.)", fontSize = 10.sp) }, modifier = Modifier.padding(start = 4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                listOf("public" to "🌐 สาธารณะ", "friends" to "👥 เพื่อน", "only_me" to "🔒 เฉพาะฉัน").forEach { (k, l) ->
                    FilterChip(selected = visibility == k, onClick = { visibility = k }, label = { Text(l, fontSize = 11.sp) })
                }
            }
            if (images.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                items(images) { u -> AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("🖼️ รูป (${images.size}/4)", color = Slate300) }
                TextButton(onClick = { showPlace = !showPlace; if (!showPlace) { place = ""; attachLoc = false } }) { Text("📍", color = Slate300) }
                if (images.isNotEmpty()) TextButton(onClick = { images = emptyList() }) { Text("ล้างรูป", color = Slate400) }
                Spacer(Modifier.weight(1f))
                Button(enabled = !busy && (text.isNotBlank() || images.isNotEmpty()), colors = ButtonDefaults.buttonColors(containerColor = Pink500), onClick = {
                    busy = true; msg = ""
                    scope.launch {
                        val loc = if (attachLoc && place.isNotBlank()) currentLocation(ctx) else null
                        FeedRepo.createPost(ctx, text, images, place, loc?.latitude, loc?.longitude, visibility).onSuccess { onPosted(it); text = ""; images = emptyList(); place = ""; showPlace = false; attachLoc = false }.onFailure { msg = it.message ?: "โพสต์ไม่สำเร็จ" }
                        busy = false
                    }
                }) { Text(if (busy) "กำลังโพสต์…" else "โพสต์") }
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
    Surface(shape = RoundedCornerShape(16.dp), color = Slate900, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.authorAvatar, p.authorName, 36)
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
                            listOf("public" to "🌐 สาธารณะ", "friends" to "👥 เพื่อน", "only_me" to "🔒 เฉพาะฉัน").forEach { (k, l) ->
                                DropdownMenuItem(text = { Text(l) }, onClick = { visMenu = false; onVisibility(k) })
                            }
                            DropdownMenuItem(text = { Text(if (p.commentsOff) "💬 เปิดความคิดเห็น" else "🚫 ปิดความคิดเห็น") }, onClick = { visMenu = false; onVisibility(if (p.commentsOff) "#comments_on" else "#comments_off") })
                        }
                    }
                    TextButton(onClick = { confirm = true }) { Text("ลบ", color = Rose500, fontSize = 12.sp) }
                }
            }
            if (p.text.isNotBlank()) Text(p.text, color = White, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
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
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("ลบโพสต์นี้?") },
        confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("ลบ", color = Rose500) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("ยกเลิก") } })
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
                    Text("ความคิดเห็น", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { state.commentsFor = null }) { Text("ปิด", color = Slate300) }
                }
                val list = comments
                LazyColumn(Modifier.weight(1f)) {
                    if (list == null) item { CircularProgressIndicator(color = Pink500) }
                    else if (list.isEmpty()) item { Text("ยังไม่มีความคิดเห็น", color = Slate400, fontSize = 13.sp) }
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
                    TextButton(onClick = { replyTo = null }) { Text("ยกเลิก", color = Slate400, fontSize = 12.sp) }
                } }
                if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp)
                val off = state.posts.firstOrNull { it.id == post.id }?.commentsOff ?: post.commentsOff
                if (off) Text("🚫 เจ้าของโพสต์ปิดความคิดเห็น", color = Slate400, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = input, onValueChange = { if (it.length <= 1000) input = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("เขียนความคิดเห็น…", color = Slate400) }, maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                    Spacer(Modifier.width(6.dp))
                    Button(enabled = input.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Pink500), onClick = {
                        val t = input; val parent = replyTo?.id; input = ""; replyTo = null
                        scope.launch {
                            val n = FeedRepo.addComment(post.id, t, parent)
                            if (n == null) { msg = FeedRepo.lastCommentError.ifBlank { "ส่งไม่สำเร็จ" }; input = t } else { msg = ""; state.update(post.id) { it.copy(commentCount = n) }; reload() }
                        }
                    }) { Text("ส่ง") }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(c: FeedComment, isReply: Boolean, mine: Boolean, onReply: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = if (isReply) 40.dp else 0.dp, top = 6.dp, bottom = 2.dp)) {
        Avatar(c.authorAvatar, c.authorName, if (isReply) 26 else 32)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(c.authorName, color = White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(c.text, color = Slate300, fontSize = 13.sp)
            Row {
                Text("ตอบกลับ", color = Slate400, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onReply).padding(end = 12.dp, top = 2.dp))
                if (mine) Text("ลบ", color = Rose500, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onDelete).padding(top = 2.dp))
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
        Surface(Modifier.fillMaxSize(), color = Slate950) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("ปิด", color = Slate300) }
                }
                LazyColumn(Modifier.weight(1f)) { realFeedItems(st) }
            }
        }
    }
    RealCommentsDialog(st)
    st.openTag?.let { if (it != tag) TagFeedDialog(it) { st.openTag = null } else st.openTag = null }
}
