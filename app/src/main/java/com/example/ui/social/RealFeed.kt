package com.example.ui.social

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
class RealFeedState(private val scope: CoroutineScope) {
    val posts = mutableStateListOf<FeedPost>()
    var cursor by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var loaded by mutableStateOf(false)
    var error by mutableStateOf("")
    var commentsFor by mutableStateOf<FeedPost?>(null)

    suspend fun refresh() {
        loading = true
        val r = FeedRepo.feed(null)
        if (r != null) { posts.clear(); posts.addAll(r.first); cursor = r.second; error = "" } else error = "โหลดฟีดไม่สำเร็จ"
        loading = false; loaded = true
    }
    fun loadMore() {
        val c = cursor ?: return
        if (loading) return
        loading = true
        scope.launch {
            FeedRepo.feed(c)?.let { (p, n) -> posts.addAll(p.filter { np -> posts.none { it.id == np.id } }); cursor = n }
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
    item(key = "real_composer") { PostComposer(onPosted = { state.posts.add(0, it) }) }
    if (state.error.isNotBlank()) item(key = "real_err") { Text(state.error, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
    if (state.loaded && state.posts.isEmpty()) item(key = "real_empty") {
        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
            Text("ยังไม่มีโพสต์ เป็นคนแรกที่โพสต์เลย!", color = Slate400, fontSize = 14.sp)
        }
    }
    items(state.posts, key = { "rp_" + it.id }) { p ->
        RealPostCard(p, onLike = { state.toggleLike(p) }, onComments = { state.commentsFor = p }, onDelete = { state.delete(p) })
        if (p.id == state.posts.lastOrNull()?.id) LaunchedEffect(p.id) { state.loadMore() }
    }
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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { images = it.take(4) }
    Surface(shape = RoundedCornerShape(16.dp), color = Slate800, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(value = text, onValueChange = { if (it.length <= 2000) text = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("คุณกำลังคิดอะไรอยู่?", color = Slate400) }, maxLines = 6,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
            if (images.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                items(images) { u -> AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("🖼️ รูป (${images.size}/4)", color = Slate300) }
                if (images.isNotEmpty()) TextButton(onClick = { images = emptyList() }) { Text("ล้างรูป", color = Slate400) }
                Spacer(Modifier.weight(1f))
                Button(enabled = !busy && (text.isNotBlank() || images.isNotEmpty()), colors = ButtonDefaults.buttonColors(containerColor = Pink500), onClick = {
                    busy = true; msg = ""
                    scope.launch {
                        FeedRepo.createPost(ctx, text, images).onSuccess { onPosted(it); text = ""; images = emptyList() }.onFailure { msg = it.message ?: "โพสต์ไม่สำเร็จ" }
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
fun RealPostCard(p: FeedPost, onLike: () -> Unit, onComments: () -> Unit, onDelete: () -> Unit) {
    val me = FirebaseAuth.getInstance().currentUser?.uid
    var confirm by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = Slate900, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.authorAvatar, p.authorName, 36)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.authorName, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(fmt.format(Date(p.createdAt)), color = Slate400, fontSize = 10.sp)
                }
                if (p.authorId == me) TextButton(onClick = { confirm = true }) { Text("ลบ", color = Rose500, fontSize = 12.sp) }
            }
            if (p.text.isNotBlank()) Text(p.text, color = White, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = input, onValueChange = { if (it.length <= 1000) input = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("เขียนความคิดเห็น…", color = Slate400) }, maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                    Spacer(Modifier.width(6.dp))
                    Button(enabled = input.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Pink500), onClick = {
                        val t = input; val parent = replyTo?.id; input = ""; replyTo = null
                        scope.launch {
                            val n = FeedRepo.addComment(post.id, t, parent)
                            if (n == null) { msg = "ส่งไม่สำเร็จ"; input = t } else { msg = ""; state.update(post.id) { it.copy(commentCount = n) }; reload() }
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
