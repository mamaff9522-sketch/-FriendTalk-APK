package com.example.ui.feed

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.social.FeedComment
import com.example.social.FeedPost
import com.example.social.FeedRepo
import com.example.ui.components.ft.Text
import com.example.ui.components.tr
import com.example.ui.social.Avatar
import com.example.ui.social.UserProfileDialog
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/* "ดูโพสต์": full post opened from a feed card — full text, every photo, video, place, likes, share and comments.
 * Uses the post already returned by /feed (the server only returns posts the viewer may see); comments use the
 * existing /posts/{id}/comments endpoints, which check visibility again. */

// colours follow the user's mode (โหมดมืด / โหมดปกติ)
private val DBg: Color get() = AppThemeMode.bg
private val DCard: Color get() = AppThemeMode.cardSolid
private val DLine: Color get() = AppThemeMode.divider
private val DText: Color get() = AppThemeMode.text
private val DNeon = Color(0xFFFF2FA8)
private val DViolet = Color(0xFFB04DFF)
private val DMuted: Color get() = AppThemeMode.muted
private val DSoft: Color get() = AppThemeMode.textSoft

fun sharePost(ctx: android.content.Context, p: FeedPost) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, (p.authorName + ": " + p.text).trim() + "\n— FriendTalk")
    runCatching { ctx.startActivity(Intent.createChooser(send, tr("แชร์โพสต์"))) }
}

/**
 * [post] is read again on every recomposition, so likes / comment counts made here or in the feed stay in sync.
 * [onCommentCount] receives the new count from the server after adding / deleting a comment.
 */
@Composable
fun PostDetailDialog(post: FeedPost, onDismiss: () -> Unit, onLike: () -> Unit, onDelete: () -> Unit, onCommentCount: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = FirebaseAuth.getInstance().currentUser?.uid
    val mine = post.authorId == me
    val info by produceState<HomeRepo.UserInfo?>(null, post.authorId) { value = HomeRepo.user(post.authorId) }
    var comments by remember(post.id) { mutableStateOf<List<FeedComment>?>(null) }
    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<FeedComment?>(null) }
    var sending by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var author by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableStateOf<String?>(null) }
    suspend fun reload() { comments = FeedRepo.comments(post.id) ?: emptyList() }
    LaunchedEffect(post.id) { reload() }
    fun send() {
        val t = input.trim(); if (t.isEmpty() || sending) return
        sending = true; msg = ""
        val parent = replyTo?.id
        scope.launch {
            val n = FeedRepo.addComment(post.id, t, parent)
            if (n == null) msg = FeedRepo.lastCommentError.ifBlank { tr("ส่งไม่สำเร็จ") }
            else { input = ""; replyTo = null; onCommentCount(n); reload() }
            sending = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = DBg) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                // top bar
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("กลับ"), tint = DSoft) }
                    Text("โพสต์ของ ${post.authorName.ifBlank { tr("ผู้ใช้") }}", color = DText, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, tr("เพิ่มเติม"), tint = DSoft) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (mine) DropdownMenuItem(text = { Text(tr("ลบโพสต์"), color = Rose500) }, onClick = { menu = false; confirmDelete = true })
                            else DropdownMenuItem(text = { Text(tr("รายงานโพสต์")) }, onClick = { menu = false; report = true })
                        }
                    }
                }
                HorizontalDivider(color = DLine, thickness = 0.8.dp)

                LazyColumn(Modifier.weight(1f)) {
                    item(key = "head") {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.clip(CircleShape).clickable { author = post.authorId }) { Avatar(post.authorAvatar, post.authorName, 46) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(post.authorName, color = DText, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.clickable { author = post.authorId })
                                        if (info?.verified == true) Icon(Icons.Filled.Verified, null, tint = DNeon, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                                    }
                                    Text(thaiTimeAgo(post.createdAt) + " · " + privacyIcon(post.visibility), color = DMuted, fontSize = 12.sp)
                                }
                            }
                            if (post.placeLabel.isNotBlank()) Row(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp))
                                .clickable { openPlaceOnMap(ctx, post.placeLabel, post.placeLat, post.placeLng) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LocationOn, tr("ดูบนแผนที่"), tint = DNeon, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                                Text(post.placeLabel, color = Color(0xFFFF8BCB), fontSize = 13.sp)
                                Text("  · " + tr("ดูแผนที่ ›"), color = DMuted, fontSize = 12.sp)
                            }
                            if (post.text.isNotBlank()) Text(post.text, color = DText, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 10.dp))
                            if (post.hashtags.isNotEmpty()) Text(post.hashtags.joinToString("  ") { "#$it" }, color = Cyan400, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    if (post.videoUrl.isNotBlank()) item(key = "video") {
                        PostVideoThumb(post, Modifier.padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth().aspectRatio(16f / 10f))
                    }
                    post.imageUrls.forEachIndexed { i, url -> item(key = "img_$i") {
                        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.FillWidth,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { zoom = url })
                    } }
                    item(key = "actions") {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text("❤️ ${com.example.ui.components.compactNum(post.likeCount)}   ·   💬 ${com.example.ui.components.compactNum(post.commentCount)} " + tr("ความคิดเห็น"), color = DMuted, fontSize = 13.sp)
                            HorizontalDivider(color = DLine, thickness = 0.8.dp, modifier = Modifier.padding(vertical = 8.dp))
                            Row(Modifier.fillMaxWidth()) {
                                DetailAction(Modifier.weight(1f), { Icon(if (post.likedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null, tint = if (post.likedByMe) Color(0xFFFF2D6F) else DSoft, modifier = Modifier.size(20.dp)) },
                                    if (post.likedByMe) tr("ถูกใจแล้ว") else tr("ถูกใจ"), onLike)
                                DetailAction(Modifier.weight(1f), { Icon(Icons.Outlined.ChatBubbleOutline, null, tint = DSoft, modifier = Modifier.size(19.dp)) }, tr("ความคิดเห็น")) { replyTo = null }
                                DetailAction(Modifier.weight(1f), { Icon(Icons.AutoMirrored.Outlined.Reply, null, tint = DSoft, modifier = Modifier.size(19.dp).graphicsLayer(scaleX = -1f)) }, tr("แชร์")) { sharePost(ctx, post) }
                            }
                            HorizontalDivider(color = DLine, thickness = 0.8.dp, modifier = Modifier.padding(top = 8.dp))
                            Text(tr("ความคิดเห็น"), color = DText, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                    val list = comments
                    when {
                        list == null -> item(key = "c_loading") { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DNeon) } }
                        list.isEmpty() -> item(key = "c_empty") { Text(tr("ยังไม่มีความคิดเห็น เป็นคนแรกเลย!"), color = DMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) }
                        else -> {
                            val top = list.filter { it.parentId == null || list.none { p -> p.id == it.parentId } }
                            top.forEach { c ->
                                item(key = "c_" + c.id) { CommentItem(c, false, c.authorId == me || mine, onAuthor = { author = c.authorId }, onReply = { replyTo = c }, onDelete = {
                                    scope.launch { FeedRepo.deleteComment(post.id, c.id)?.let { n -> onCommentCount(n); reload() } }
                                }) }
                                list.filter { it.parentId == c.id }.forEach { r ->
                                    item(key = "c_" + r.id) { CommentItem(r, true, r.authorId == me || mine, onAuthor = { author = r.authorId }, onReply = { replyTo = c }, onDelete = {
                                        scope.launch { FeedRepo.deleteComment(post.id, r.id)?.let { n -> onCommentCount(n); reload() } }
                                    }) }
                                }
                            }
                        }
                    }
                    item(key = "pad") { Spacer(Modifier.height(12.dp)) }
                }

                // comment box
                HorizontalDivider(color = DLine, thickness = 0.8.dp)
                if (post.commentsOff) Text(tr("🚫 เจ้าของโพสต์ปิดความคิดเห็น"), color = DMuted, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                else Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    replyTo?.let { r -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                        Text("ตอบกลับ ${r.authorName}", color = DNeon, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Close, tr("ยกเลิก"), tint = DMuted, modifier = Modifier.size(16.dp).clickable { replyTo = null })
                    } }
                    if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(value = input, onValueChange = { if (it.length <= 1000) input = it }, enabled = !sending, maxLines = 4,
                            textStyle = TextStyle(color = DText, fontSize = 15.sp), cursorBrush = SolidColor(DNeon),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }),
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(DCard).padding(horizontal = 14.dp, vertical = 11.dp),
                            decorationBox = { inner -> Box { if (input.isEmpty()) Text(tr("เขียนความคิดเห็น…"), color = DMuted, fontSize = 15.sp); inner() } })
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(40.dp).clip(CircleShape).background(if (input.isNotBlank() && !sending) Brush.linearGradient(listOf(DNeon, DViolet)) else SolidColor(DLine))
                            .clickable(enabled = input.isNotBlank() && !sending) { send() }, contentAlignment = Alignment.Center) {
                            if (sending) CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            else Text("➤", color = PureWhite, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
    author?.let { UserProfileDialog(it) { author = null } }
    zoom?.let { ImageZoomDialog(it) { zoom = null } }
    if (report) com.example.ui.social.ReportDialog("post", post.id, post.authorId) { report = false }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(tr("ลบโพสต์นี้?")) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(); onDismiss() }) { Text(tr("ลบ"), color = Rose500) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("ยกเลิก")) } })
}

@Composable
private fun DetailAction(modifier: Modifier, icon: @Composable () -> Unit, label: String, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        icon(); Spacer(Modifier.width(6.dp)); Text(label, color = DSoft, fontSize = 14.sp)
    }
}

@Composable
private fun CommentItem(c: FeedComment, isReply: Boolean, canDelete: Boolean, onAuthor: () -> Unit, onReply: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = if (isReply) 54.dp else 14.dp, end = 14.dp, top = 6.dp, bottom = 2.dp)) {
        Box(Modifier.clip(CircleShape).clickable(onClick = onAuthor)) { Avatar(c.authorAvatar, c.authorName, if (isReply) 28 else 34) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Surface(shape = RoundedCornerShape(16.dp), color = DCard, border = BorderStroke(0.5.dp, DLine)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text(c.authorName, color = DText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(c.text, color = DSoft, fontSize = 14.sp)
                }
            }
            Row(Modifier.padding(start = 10.dp, top = 2.dp)) {
                Text(thaiTimeAgo(c.createdAt), color = DMuted, fontSize = 11.sp)
                Text(tr("ตอบกลับ"), color = DMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp).clickable(onClick = onReply))
                if (canDelete) Text(tr("ลบ"), color = Rose500, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp).clickable(onClick = onDelete))
            }
        }
    }
}

/** Full-screen photo. */
@Composable
fun ImageZoomDialog(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss)) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(8.dp)) { Icon(Icons.Filled.Close, tr("ปิด"), tint = PureWhite) }
        }
    }
}
