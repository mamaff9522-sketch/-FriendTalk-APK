package com.example.ui.social

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.example.ui.components.ft.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.social.GiftRepo
import com.example.social.GiftRepo.Decor
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Six VIP themes: name + gradient colors (used for room background and chat-list mini preview). */
val DECOR_THEMES = linkedMapOf(
    "royal_gold" to ("Royal Gold" to listOf(Color(0xFF3B2A06), Color(0xFF8A6A12), Color(0xFF1C1403))),
    "diamond_luxury" to ("Diamond Luxury" to listOf(Color(0xFF0E2A3B), Color(0xFF5CC8E8), Color(0xFF0A1622))),
    "galaxy" to ("Galaxy" to listOf(Color(0xFF0B0430), Color(0xFF4B1C8C), Color(0xFF02010F))),
    "romantic" to ("Romantic" to listOf(Color(0xFF4A0E2A), Color(0xFFD9467A), Color(0xFF2A0617))),
    "neon_vip" to ("Neon VIP" to listOf(Color(0xFF07011A), Color(0xFF00E5FF), Color(0xFFFF00C8))),
    "luxury_palace" to ("Luxury Palace" to listOf(Color(0xFF2B0F0A), Color(0xFFB8860B), Color(0xFF4A1C10)))
)
fun hexColor(h: String, fb: Color = Color.Transparent): Color = runCatching { Color(android.graphics.Color.parseColor(h)) }.getOrDefault(fb)
fun frameColors(f: String) = when (f) {
    "diamond" -> listOf(Color(0xFFB9F2FF), Color.White, Color(0xFF5CC8E8)); "gold" -> listOf(Color(0xFFFFD700), Color(0xFFB8860B), Color(0xFFFFF3B0))
    "sparkle" -> listOf(Color(0xFFFF6FD8), Color(0xFFFFD700), Color(0xFF7AF5FF)); else -> emptyList()
}
fun contrastRatio(a: Color, b: Color): Double { val x = a.luminance() + 0.05; val y = b.luminance() + 0.05; return (maxOf(x, y) / minOf(x, y)).toDouble() }

@Composable
fun rememberGifLoader(): ImageLoader {
    val ctx = LocalContext.current
    return remember { ImageLoader.Builder(ctx).components { if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory()) }.build() }
}

/** Chat list mini preview: theme gradient + frame, no GIF. */
@Composable
fun DecorMiniPreview(d: Decor) {
    val cols = DECOR_THEMES[d.theme]?.second ?: return
    val fc = frameColors(d.frame)
    Box(Modifier.size(width = 30.dp, height = 40.dp).clip(RoundedCornerShape(6.dp)).background(Brush.verticalGradient(cols))
        .then(if (fc.isNotEmpty()) Modifier.border(2.dp, Brush.linearGradient(fc), RoundedCornerShape(6.dp)) else Modifier)) {
        Box(Modifier.align(Alignment.Center).size(12.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(hexColor(d.bubbleColor, Pink500)))
    }
}

/** Full room background: theme, colour, pattern, image/GIF (lite mode = static), frame, effect, stickers. */
@Composable
fun DecorBackground(chatId: String, d: Decor, token: String?, stickers: Map<String, com.example.social.CatalogItem>, lite: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val cols = DECOR_THEMES[d.theme]?.second ?: listOf(Slate950, Slate950)
    val fc = frameColors(d.frame)
    val gif = rememberGifLoader()
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(cols))) {
        if (d.bgColor.isNotBlank()) Box(Modifier.fillMaxSize().background(hexColor(d.bgColor).copy(alpha = 0.55f)))
        if (d.pattern != "none") PatternLayer(d.pattern)
        if (d.imagePath.isNotBlank()) AsyncImage(
            model = ImageRequest.Builder(ctx).data(GiftRepo.decorImageUrl(chatId, d.imagePath)).addHeader("Authorization", "Bearer ${token.orEmpty()}")
                .apply { if (lite || !d.animated) decoderFactory(coil.decode.BitmapFactoryDecoder.Factory()) }.memoryCacheKey(d.imagePath + lite).build(),
            imageLoader = gif, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.85f)
        if (d.effect != "none" && !lite) EffectLayer(d.effect)
        StickerLayer(d.items, stickers)
        if (fc.isNotEmpty()) Box(Modifier.fillMaxSize().border(4.dp, Brush.linearGradient(fc), RoundedCornerShape(0.dp)))
    }
}

@Composable
private fun PatternLayer(p: String) {
    val glyph = when (p) { "dots" -> "•"; "stripes" -> "╱"; "hearts" -> "♥"; "stars" -> "★"; else -> "◆" }
    Column(Modifier.fillMaxSize().graphicsLayer { alpha = 0.12f }, verticalArrangement = Arrangement.SpaceEvenly) {
        repeat(14) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { repeat(8) { Text(glyph, color = Color.White, fontSize = 16.sp) } } }
    }
}

/** Light particle effect; stops when the composable leaves the screen (Compose disposes the transition). */
@Composable
private fun EffectLayer(effect: String) {
    val glyph = when (effect) { "hearts" -> "💗"; "stars" -> "⭐"; "snow" -> "❄"; "bubbles" -> "○"; else -> "✨" }
    val t by rememberInfiniteTransition(label = "fx").animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "t")
    val seeds = remember { List(12) { Random.nextFloat() to Random.nextFloat() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        seeds.forEach { (sx, sy) ->
            val y = ((sy + t) % 1f)
            Text(glyph, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.offset(x = w * sx, y = h * y))
        }
    }
}

@Composable
private fun StickerLayer(items: List<GiftRepo.DecorItem>, stickers: Map<String, com.example.social.CatalogItem>) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        items.forEach { it2 ->
            val s = stickers[it2.stickerId] ?: return@forEach
            Box(Modifier.offset(x = w * it2.x - 28.dp, y = h * it2.y - 28.dp).size(56.dp).graphicsLayer { scaleX = it2.scale; scaleY = it2.scale; rotationZ = it2.rotation }) { StickerImage(s) }
        }
    }
}

@Composable
fun StickerImage(s: com.example.social.CatalogItem, modifier: Modifier = Modifier.fillMaxSize()) {
    if (s.imageUrl.isNotBlank()) AsyncImage(model = s.imageUrl, contentDescription = s.name, modifier = modifier)
    else Box(modifier, contentAlignment = Alignment.Center) { Text(s.icon.ifBlank { "🎁" }, fontSize = 32.sp) }
}

/** VIP-only room decoration editor: theme, colour/pattern/image, frame, effect, bubble + text colour (contrast checked), stickers (drag/pinch/rotate). */
@Composable
fun RoomDecorEditor(chatId: String, initial: Decor?, token: String?, onClose: (saved: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var d by remember { mutableStateOf(initial ?: Decor()) }
    var preview by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(-1) }
    val stickers by remember { GiftRepo.catalog("stickers") }.collectAsState(initial = emptyList())
    val stickerMap = stickers.associateBy { it.id }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
        if (mime !in listOf("image/jpeg", "image/png", "image/webp", "image/gif")) { msg = "รองรับ jpeg/png/webp/gif เท่านั้น"; return@rememberLauncherForActivityResult }
        val bytes = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return@rememberLauncherForActivityResult
        val cap = if (mime == "image/gif") 3 else 5
        if (bytes.size > cap * 1024 * 1024) { msg = "ไฟล์ใหญ่เกิน ${cap}MB"; return@rememberLauncherForActivityResult }
        msg = "กำลังอัปโหลด…"
        scope.launch { val r = GiftRepo.uploadDecorImage(chatId, bytes, mime); if (r != null) { d = d.copy(imagePath = r.first, animated = r.second); msg = "" } else msg = "อัปโหลดไม่สำเร็จ (VIP เท่านั้น)" }
    }
    Dialog(onDismissRequest = { onClose(false) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding(), color = Slate950) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onClose(false) }) { Text("ยกเลิก", color = Slate300) }
                    Text("✨ ตกแต่งห้อง VIP", color = Amber400, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { preview = !preview }) { Text(if (preview) "แก้ไข" else "พรีวิว", color = White) }
                    Button(onClick = {
                        if (contrastRatio(hexColor(d.bubbleColor, Pink500), hexColor(d.textColor, White)) < 4.5) { msg = "สีตัวอักษรกับบับเบิลตัดกันไม่พอ อ่านยาก"; return@Button }
                        scope.launch { val r = GiftRepo.saveDecor(chatId, d); msg = r.msg; if (r.ok) onClose(true) }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("บันทึก") }
                }
                if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
                // canvas: drag / pinch / rotate the selected sticker
                var size by remember { mutableStateOf(IntSize(1, 1)) }
                Box(Modifier.fillMaxWidth().weight(1f).padding(8.dp).clip(RoundedCornerShape(12.dp)).onSizeChanged { size = it }
                    .pointerInput(selected) {
                        detectTransformGestures { _, pan, zoom, rot ->
                            val i = selected; if (i !in d.items.indices) return@detectTransformGestures
                            val it0 = d.items[i]
                            val n = it0.copy(x = (it0.x + pan.x / size.width).coerceIn(0f, 1f), y = (it0.y + pan.y / size.height).coerceIn(0f, 1f),
                                scale = (it0.scale * zoom).coerceIn(0.2f, 4f), rotation = ((it0.rotation + rot + 540f) % 360f) - 180f)
                            d = d.copy(items = d.items.toMutableList().also { l -> l[i] = n })
                        }
                    }) {
                    DecorBackground(chatId, d, token, stickerMap, lite = false)
                    Column(Modifier.align(Alignment.Center).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DecorBubble(d, "สวัสดี! ห้องนี้สวยมาก ✨", true); DecorBubble(d, "ขอบคุณนะ 💖", false)
                    }
                    if (!preview && selected in d.items.indices) Text("ลากเพื่อย้าย · ใช้สองนิ้วเพื่อย่อ/ขยาย/หมุน", color = White, fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).background(Color(0x99000000)).padding(4.dp))
                }
                if (!preview) Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                    Label("ธีม")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DECOR_THEMES.entries.toList()) { (k, v) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { d = d.copy(theme = k) }) {
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(v.second))
                                    .border(if (d.theme == k) 2.dp else 0.dp, Amber400, RoundedCornerShape(8.dp)))
                                Text(v.first, color = White, fontSize = 10.sp)
                            }
                        }
                    }
                    Label("พื้นหลัง")
                    ChipRow(listOf("" to "ตามธีม", "#000000" to "ดำ", "#FFFFFF" to "ขาว", "#7C3AED" to "ม่วง", "#DB2777" to "ชมพู", "#0EA5E9" to "ฟ้า"), d.bgColor) { d = d.copy(bgColor = it) }
                    ChipRow(listOf("none" to "ไม่มีลาย", "dots" to "จุด", "stripes" to "ริ้ว", "hearts" to "หัวใจ", "stars" to "ดาว", "diamonds" to "เพชร"), d.pattern) { d = d.copy(pattern = it) }
                    Row {
                        OutlinedButton(onClick = { bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("🖼 รูป/GIF พื้นหลัง", color = White, fontSize = 12.sp) }
                        if (d.imagePath.isNotBlank()) TextButton(onClick = { d = d.copy(imagePath = "", animated = false) }) { Text("ลบรูป", color = Slate300) }
                    }
                    Label("กรอบ"); ChipRow(listOf("none" to "ไม่มี", "diamond" to "💎 เพชร", "gold" to "🥇 ทอง", "sparkle" to "✨ วิบวับ"), d.frame) { d = d.copy(frame = it) }
                    Label("เอฟเฟกต์"); ChipRow(listOf("none" to "ไม่มี", "sparkle" to "✨", "hearts" to "💗", "stars" to "⭐", "snow" to "❄", "bubbles" to "○"), d.effect) { d = d.copy(effect = it) }
                    Label("บับเบิลแชท"); ChipRow(listOf("default" to "ปกติ", "rounded" to "มน", "glass" to "กระจก", "gold" to "ทอง", "neon" to "นีออน"), d.bubbleStyle) { d = d.copy(bubbleStyle = it) }
                    ChipRow(listOf("#7C3AED" to "ม่วง", "#DB2777" to "ชมพู", "#B8860B" to "ทอง", "#0EA5E9" to "ฟ้า", "#111827" to "ดำ", "#F9FAFB" to "ขาว"), d.bubbleColor) { d = d.copy(bubbleColor = it) }
                    ChipRow(listOf("#FFFFFF" to "อักษรขาว", "#111827" to "อักษรดำ", "#FDE68A" to "อักษรทอง"), d.textColor) { d = d.copy(textColor = it) }
                    val cr = contrastRatio(hexColor(d.bubbleColor, Pink500), hexColor(d.textColor, White))
                    Text(if (cr >= 4.5) "✓ อ่านง่าย (contrast ${"%.1f".format(cr)})" else "⚠ อ่านยาก (contrast ${"%.1f".format(cr)}) — เปลี่ยนสี", color = if (cr >= 4.5) Emerald400 else Amber400, fontSize = 11.sp)
                    Label("สติกเกอร์ (${d.items.size}/30) — แตะเพื่อเพิ่ม, แตะในแถวด้านล่างเพื่อเลือกแก้")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(stickers) { s -> Box(Modifier.size(44.dp).clickable(enabled = d.items.size < 30) {
                            d = d.copy(items = d.items + GiftRepo.DecorItem(s.id, 0.5f, 0.5f, 1f, 0f)); selected = d.items.size - 1 }) { StickerImage(s) } }
                    }
                    if (stickers.isEmpty()) Text("ยังไม่มีสติกเกอร์ (แอดมินเพิ่มได้)", color = Slate400, fontSize = 11.sp)
                    if (d.items.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        items(d.items.indices.toList()) { i -> val s = stickerMap[d.items[i].stickerId]
                            Box(Modifier.size(36.dp).border(if (i == selected) 2.dp else 0.dp, Amber400, CircleShape).clickable { selected = i }) { s?.let { StickerImage(it) } } }
                    }
                    if (selected in d.items.indices) TextButton(onClick = { d = d.copy(items = d.items.filterIndexed { i, _ -> i != selected }); selected = -1 }) { Text("ลบสติกเกอร์ที่เลือก", color = Pink400) }
                    TextButton(onClick = { scope.launch { val r = GiftRepo.resetDecor(chatId); msg = r.msg; if (r.ok) onClose(true) } }) { Text("รีเซ็ตห้อง (ลบการตกแต่ง)", color = Slate300) }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable private fun Label(t: String) = Text(t, color = Slate300, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
@Composable private fun ChipRow(opts: List<Pair<String, String>>, sel: String, on: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opts.forEach { (k, l) -> FilterChip(selected = sel == k, onClick = { on(k) }, label = { Text(l, fontSize = 11.sp) }) }
    }
}

/** Bubble styled by the room decor. */
@Composable
fun DecorBubble(d: Decor?, text: String, mine: Boolean, content: (@Composable () -> Unit)? = null) {
    val base = if (d == null) (if (mine) Pink600 else Slate800) else hexColor(d.bubbleColor, Pink600).let { if (mine) it else it.copy(alpha = 0.75f) }
    val shape = RoundedCornerShape(if (d?.bubbleStyle == "rounded") 22.dp else 14.dp)
    val border = when (d?.bubbleStyle) { "gold" -> BorderStroke(1.5.dp, Color(0xFFFFD700)); "neon" -> BorderStroke(1.5.dp, Color(0xFF00E5FF)); else -> null }
    Surface(shape = shape, color = if (d?.bubbleStyle == "glass") base.copy(alpha = 0.45f) else base, border = border) {
        content?.invoke() ?: Text(text, color = d?.let { hexColor(it.textColor, White) } ?: White, fontSize = 14.sp, modifier = Modifier.padding(10.dp))
    }
}
