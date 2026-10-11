package com.example.ui.admin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AppUiThemeConfig
import com.example.model.BgSpec
import com.example.model.parseHexColor
import com.example.network.BrainApi
import com.example.social.FeedPost
import com.example.social.FeedRepo
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private object DraftData { var posts: List<FeedPost>? = null }

/** Home screen rendered with explicit DRAFT colours (does not touch the live palette), filled with real posts / nearby cache. */
@Composable
fun DraftHomePreview(t: AppUiThemeConfig, compact: Boolean, appName: String = "FriendTalk") {
    val bg = parseHexColor(t.backgroundColorHex, Color.White); val surf = parseHexColor(t.surfaceColorHex, Color.White)
    val card = parseHexColor(t.cardColorHex, Color.White); val acc = parseHexColor(t.primaryColorHex, Color.Magenta)
    val acc2 = parseHexColor(t.secondaryColorHex, Color.Magenta); val tx = parseHexColor(t.textPrimaryHex, Color.Black); val tx2 = parseHexColor(t.textSecondaryHex, Color.Gray)
    var posts by remember { mutableStateOf(DraftData.posts) }
    LaunchedEffect(Unit) { if (posts == null) { DraftData.posts = FeedRepo.feed(null)?.first?.take(10) ?: emptyList(); posts = DraftData.posts } }
    val near by com.example.social.NearbyRepo.lastNearby.collectAsState()
    Box(Modifier.fillMaxSize()) {
        val spec = t.backgrounds["tab_home"] ?: t.backgrounds["app"]
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(lerp(bg, acc, 0.06f), bg, lerp(bg, acc2, 0.05f)))))
        if (spec != null && spec.imageUrl.isNotBlank()) AppBackground(spec)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(Modifier.fillMaxWidth().background(surf.copy(alpha = 0.92f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(acc, acc2))), contentAlignment = Alignment.Center) { Text("FT", color = PureWhite, fontWeight = FontWeight.Black, fontSize = 13.sp) }
                    Spacer(Modifier.width(8.dp)); Text(appName, color = tx, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ทั้งหมด", "รูปภาพ", "มีตำแหน่ง", "ที่ฉันถูกใจ").forEachIndexed { i, c ->
                        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (i == 0) Brush.horizontalGradient(listOf(acc, acc2)) else Brush.linearGradient(listOf(card, card))).padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(c, color = if (i == 0) PureWhite else tx, fontSize = 12.sp)
                        }
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = surf, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("🔴 LIVE ตอนนี้", color = tx, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("ยังไม่มีใครไลฟ์ตอนนี้", color = tx2, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("📍 คนใกล้คุณ", color = tx, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        val n = near
                        if (n.isNullOrEmpty()) Text("ยังไม่มีข้อมูลเรดาร์", color = tx2, fontSize = 12.sp)
                        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { n.take(6).forEach { u -> com.example.ui.social.Avatar(u.profile.avatar, u.profile.displayName, 36) } }
                    }
                }
            }
            val ps = posts
            if (ps == null) item { Text("กำลังโหลดโพสต์จริง…", color = tx2, modifier = Modifier.padding(16.dp)) }
            else if (ps.isEmpty()) item { Text("ยังไม่มีโพสต์", color = tx2, modifier = Modifier.padding(16.dp)) }
            else items(if (compact) ps.take(3) else ps) { p ->
                Surface(shape = RoundedCornerShape(t.cornerRadiusDp.coerceIn(0, 40).dp), color = card, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.example.ui.social.Avatar(p.authorAvatar, p.authorName, 32); Spacer(Modifier.width(8.dp))
                            Text(p.authorName, color = tx, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        if (p.text.isNotBlank()) Text(p.text, color = tx, fontSize = 13.sp, maxLines = 4, modifier = Modifier.padding(top = 6.dp))
                        if (p.imageUrls.isNotEmpty()) coil.compose.AsyncImage(model = p.imageUrls.first(), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.padding(top = 6.dp).fillMaxWidth().height(if (compact) 120.dp else 220.dp).clip(RoundedCornerShape(12.dp)))
                        Text("❤ ${p.likeCount}   💬 ${p.commentCount}", color = tx2, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

/** The single full-screen preview of the unsaved draft. Closing returns to the editor with edits intact. */
@Composable
fun DraftPreviewDialog(t: AppUiThemeConfig, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Color(0xFFF59E0B)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("👁 PREVIEW / ยังไม่เผยแพร่", color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("ปิด ✕", color = Color.Black) }
            }
            Box(Modifier.weight(1f)) { DraftHomePreview(t, compact = false) }
        }
    }
}

private val BG_TARGETS = listOf("app" to "ทุกหน้า", "tab_home" to "หน้าแรก", "tab_discover" to "หาเพื่อน", "tab_live" to "ไลฟ์สด",
    "tab_movie" to "วิดีโอ", "tab_chat" to "แชท", "tab_ads" to "โฆษณา", "tab_profile" to "โปรไฟล์")

/** Admin background-image editor: per page, upload (compressed), fit, blur, dim, colour overlay, remove. */
@Composable
fun BackgroundEditor(t: AppUiThemeConfig, onUpdate: (AppUiThemeConfig) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf("app") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var pickOverlay by remember { mutableStateOf(false) }
    val spec = t.backgrounds[target] ?: BgSpec()
    fun put(s: BgSpec?) = onUpdate(t.copy(backgrounds = if (s == null) t.backgrounds - target else t.backgrounds + (target to s)))
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val u = FirebaseAuth.getInstance().currentUser ?: return@rememberLauncherForActivityResult
        busy = true; msg = "กำลังอัปโหลด…"
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.let { b ->
                    val sc = minOf(1f, 1440f / maxOf(b.width, b.height))
                    val r = Bitmap.createScaledBitmap(b, (b.width * sc).toInt().coerceAtLeast(1), (b.height * sc).toInt().coerceAtLeast(1), true)
                    ByteArrayOutputStream().also { r.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
                }
            }
            if (bytes == null) { msg = "อ่านรูปไม่ได้"; busy = false; return@launch }
            val r = BrainApi.callRaw(u, "PUT", "/admin/ui/bg/$target", bytes, "image/jpeg")
            val url = r.json?.optString("url").orEmpty()
            if (r.code == 200 && url.isNotBlank()) { put(spec.copy(imageUrl = url)); msg = "อัปโหลดแล้ว — กดเผยแพร่เพื่อใช้กับทุกคน" } else msg = "อัปโหลดไม่สำเร็จ (${r.code})"
            busy = false
        }
    }
    if (pickOverlay) ColorPickerDialog("สีทับ (overlay)", spec.overlayHex.ifBlank { "#000000" }, onPick = { put(spec.copy(overlayHex = it, overlayAlpha = if (spec.overlayAlpha == 0) 30 else spec.overlayAlpha)); pickOverlay = false }, onDismiss = { pickOverlay = false })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("รูปพื้นหลัง:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Pink400)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BG_TARGETS.forEach { (k, l) ->
                FilterChip(selected = target == k, onClick = { target = k }, label = { Text(l + if (t.backgrounds[k]?.imageUrl?.isNotBlank() == true) " •" else "", fontSize = 12.sp) })
            }
        }
        Surface(shape = RoundedCornerShape(14.dp), color = Slate900, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Slate700, RoundedCornerShape(10.dp))) {
                    AppBackground(spec.takeIf { it.imageUrl.isNotBlank() })
                    if (spec.imageUrl.isBlank()) Text("ไม่มีรูป — ใช้ไล่สีอ่อนของธีม", color = Slate400, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) {
                        Text(if (spec.imageUrl.isBlank()) "📤 อัปโหลดรูป" else "🔁 เปลี่ยนรูป", color = PureWhite, fontSize = 12.sp)
                    }
                    if (spec.imageUrl.isNotBlank()) OutlinedButton(onClick = { put(null) }) { Text("🗑 ลบ", color = Rose500, fontSize = 12.sp) }
                }
                if (msg.isNotBlank()) Text(msg, color = Slate400, fontSize = 11.sp)
                if (spec.imageUrl.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("cover" to "เต็มจอ", "contain" to "พอดี", "tile" to "เรียงต่อ").forEach { (k, l) -> FilterChip(selected = spec.fit == k, onClick = { put(spec.copy(fit = k)) }, label = { Text(l, fontSize = 12.sp) }) }
                    }
                    Text("เบลอ ${spec.blur}", color = Slate300, fontSize = 12.sp)
                    Slider(value = spec.blur.toFloat(), onValueChange = { put(spec.copy(blur = it.toInt())) }, valueRange = 0f..25f)
                    Text("ทำให้มืด ${spec.dim}%", color = Slate300, fontSize = 12.sp)
                    Slider(value = spec.dim.toFloat(), onValueChange = { put(spec.copy(dim = it.toInt())) }, valueRange = 0f..90f)
                    Row(Modifier.fillMaxWidth().clickable { pickOverlay = true }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(parseHexColor(spec.overlayHex, Color.Transparent)).border(1.dp, Slate700, RoundedCornerShape(6.dp)))
                        Text("  สีทับ ${spec.overlayHex.ifBlank { "(ไม่มี)" }} · ${spec.overlayAlpha}%", color = White, fontSize = 12.sp)
                    }
                    Slider(value = spec.overlayAlpha.toFloat(), onValueChange = { put(spec.copy(overlayAlpha = it.toInt())) }, valueRange = 0f..100f)
                }
            }
        }
    }
}
