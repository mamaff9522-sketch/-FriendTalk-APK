package com.example.ui.social

import com.example.ui.components.dialogNavPadding

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
    "luxury_palace" to ("Luxury Palace" to listOf(Color(0xFF2B0F0A), Color(0xFFB8860B), Color(0xFF4A1C10))),
    "sakura" to ("Sakura" to listOf(Color(0xFF3A1027), Color(0xFFF49AC1), Color(0xFF2A0B1C))),
    "ocean" to ("Ocean" to listOf(Color(0xFF021B33), Color(0xFF1C7ED6), Color(0xFF01101F))),
    "sunset" to ("Sunset" to listOf(Color(0xFF2B0B2E), Color(0xFFFF7A45), Color(0xFF3A0F1F))),
    "midnight" to ("Midnight" to listOf(Color(0xFF05060F), Color(0xFF1E2A5A), Color(0xFF020308))),
    "mint_garden" to ("Mint Garden" to listOf(Color(0xFF052A22), Color(0xFF2FBF9A), Color(0xFF02150F))),
    "rose_gold" to ("Rose Gold" to listOf(Color(0xFF2E1418), Color(0xFFE8A598), Color(0xFF1E0B0E)))
)

/** Built-in decor stickers: the free chat emoji (e_*) + decor-only emoji (d_*). The server accepts these ids without an admin catalog. */
val DECOR_BUILTIN_STICKERS: List<com.example.social.CatalogItem> = com.example.social.ChatSticker.BUILT_IN + listOf(
    "crown" to "👑", "rose" to "🌹", "diamond" to "💎", "star" to "🌟", "balloon" to "🎈", "ribbon" to "🎀",
    "moon" to "🌙", "butterfly" to "🦋", "sparkles" to "✨", "cake" to "🎂", "teddy" to "🧸", "rainbow" to "🌈"
).mapIndexed { i, (id, e) -> com.example.social.CatalogItem("d_$id", id, 0, "", e, "ตกแต่ง", true, 100 + i) }
fun hexColor(h: String, fb: Color = Color.Transparent): Color = runCatching { Color(android.graphics.Color.parseColor(h)) }.getOrDefault(fb)
fun frameColors(f: String) = when (f) {
    "diamond" -> listOf(Color(0xFFB9F2FF), Color.White, Color(0xFF5CC8E8)); "gold" -> listOf(Color(0xFFFFD700), Color(0xFFB8860B), Color(0xFFFFF3B0))
    "sparkle" -> listOf(Color(0xFFFF6FD8), Color(0xFFFFD700), Color(0xFF7AF5FF))
    "neon" -> listOf(Color(0xFF00E5FF), Color(0xFFFF00C8), Color(0xFF00E5FF)); "rose" -> listOf(Color(0xFFFFB3C7), Color(0xFFE8A598), Color(0xFFFF6F91)); else -> emptyList()
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
        StickerLayer(d.items, DECOR_BUILTIN_STICKERS.associateBy { it.id } + stickers)
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
    val glyphs = when (effect) { "hearts" -> listOf("💗"); "stars" -> listOf("⭐"); "snow" -> listOf("❄"); "bubbles" -> listOf("○"); "petals" -> listOf("🌸", "🌺")
        "confetti" -> listOf("🎉", "🎊", "✦"); "fireflies" -> listOf("•"); else -> listOf("✨") }
    val tint = if (effect == "fireflies") Color(0xFFFFF59D) else Color.White
    val t by rememberInfiniteTransition(label = "fx").animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "t")
    val seeds = remember { List(12) { Random.nextFloat() to Random.nextFloat() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        seeds.forEachIndexed { i, (sx, sy) ->
            val y = ((sy + t) % 1f)
            Text(glyphs[i % glyphs.size], fontSize = 14.sp, color = tint.copy(alpha = 0.65f), modifier = Modifier.offset(x = w * sx, y = h * y))
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

/** VIP-only room decoration editor: live preview on top, tabbed panel below (ธีม · พื้นหลัง · บับเบิล · เอฟเฟกต์ · สติกเกอร์). */
@Composable
fun RoomDecorEditor(chatId: String, initial: Decor?, token: String?, onClose: (saved: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var d by remember { mutableStateOf(initial ?: Decor()) }
    var msg by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(-1) }
    var tab by remember { mutableStateOf(0) }
    val catalog by remember { GiftRepo.catalog("stickers") }.collectAsState(initial = emptyList())
    val stickers = catalog + DECOR_BUILTIN_STICKERS
    val stickerMap = stickers.associateBy { it.id }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
        if (mime !in listOf("image/jpeg", "image/png", "image/webp", "image/gif")) { msg = "รองรับ jpeg/png/webp/gif เท่านั้น"; return@rememberLauncherForActivityResult }
        val bytes = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return@rememberLauncherForActivityResult
        val cap = if (mime == "image/gif") 3 else 5
        if (bytes.size > cap * 1024 * 1024) { msg = "ไฟล์ใหญ่เกิน ${cap}MB"; return@rememberLauncherForActivityResult }
        msg = "กำลังอัปโหลดรูป…"
        scope.launch { val r = GiftRepo.uploadDecorImage(chatId, bytes, mime); if (r != null) { d = d.copy(imagePath = r.first, animated = r.second); msg = "" } else msg = "อัปโหลดไม่สำเร็จ (VIP เท่านั้น)" }
    }
    val gold = Color(0xFFFFC94D)
    Dialog(onDismissRequest = { onClose(false) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0B0824)) {
            Column(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding()) {
                // header
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onClose(false) }) { Icon(Icons.Filled.Close, "ปิด", tint = PureWhite) }
                    Column(Modifier.weight(1f)) {
                        Text("ตกแต่งห้องแชท", color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFFFE08A), Color(0xFFE0A100)))).padding(horizontal = 6.dp, vertical = 1.dp)) {
                                Text("VIP", color = Color(0xFF3B2A06), fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.width(6.dp)); Text("ทั้งสองคนในห้องจะเห็นเหมือนกัน", color = Slate400, fontSize = 11.sp)
                        }
                    }
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFFF2FA8), Color(0xFF9B3DFF))))
                        .clickable(enabled = !saving) {
                            if (contrastRatio(hexColor(d.bubbleColor, Pink500), hexColor(d.textColor, White)) < 4.5) { msg = "สีตัวอักษรกับบับเบิลตัดกันไม่พอ อ่านยาก — เปลี่ยนสีในแท็บบับเบิล"; tab = 2; return@clickable }
                            saving = true
                            scope.launch { val r = GiftRepo.saveDecor(chatId, d); saving = false; msg = r.msg; if (r.ok) onClose(true) }
                        }.padding(horizontal = 18.dp, vertical = 8.dp)) {
                        if (saving) CircularProgressIndicator(Modifier.size(16.dp), color = PureWhite, strokeWidth = 2.dp)
                        else Text("บันทึก", color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                // live preview (drag / pinch / rotate the selected sticker)
                var size by remember { mutableStateOf(IntSize(1, 1)) }
                Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp, vertical = 4.dp).clip(RoundedCornerShape(22.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(22.dp)).onSizeChanged { size = it }
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
                    Column(Modifier.fillMaxWidth().align(Alignment.Center).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) { DecorBubble(d, "สวัสดี ห้องนี้สวยมากเลย ✨", false) }
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { DecorBubble(d, "ขอบคุณนะ ตกแต่งเองเลย 💖", true) }
                    }
                    if (selected in d.items.indices) Text("ลากเพื่อย้าย · สองนิ้วเพื่อย่อ/ขยาย/หมุน", color = PureWhite, fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                // panel
                Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color(0xFF15102F))) {
                    val tabs = listOf("ธีม", "พื้นหลัง", "บับเบิล", "เอฟเฟกต์", "สติกเกอร์")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tabs.forEachIndexed { i, t ->
                            val on = tab == i
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .background(if (on) Brush.horizontalGradient(listOf(Color(0xFFFF2FA8), Color(0xFF9B3DFF))) else Brush.horizontalGradient(listOf(Color(0x14FFFFFF), Color(0x14FFFFFF))))
                                .clickable { tab = i }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(t, color = if (on) PureWhite else Slate400, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth().height(230.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 12.dp)) {
                        when (tab) {
                            0 -> {
                                Section("ธีมห้อง")
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DECOR_THEMES.forEach { (k, v) ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp).clickable { d = d.copy(theme = k) }) {
                                            Box(Modifier.size(width = 64.dp, height = 88.dp).clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(v.second))
                                                .border(if (d.theme == k) 2.5.dp else 1.dp, if (d.theme == k) gold else Color(0x33FFFFFF), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                                if (d.theme == k) Box(Modifier.size(22.dp).clip(CircleShape).background(gold), contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Filled.Check, null, tint = Color(0xFF3B2A06), modifier = Modifier.size(14.dp)) }
                                            }
                                            Spacer(Modifier.height(4.dp)); Text(v.first, color = PureWhite, fontSize = 10.5.sp, maxLines = 1)
                                        }
                                    }
                                }
                                Section("กรอบห้อง")
                                OptionRow(listOf("none" to "ไม่มี", "gold" to "ทอง", "diamond" to "เพชร", "sparkle" to "วิบวับ", "neon" to "นีออน", "rose" to "โรส"), d.frame,
                                    swatch = { k -> frameColors(k).ifEmpty { listOf(Color(0x33FFFFFF), Color(0x33FFFFFF)) } }) { d = d.copy(frame = it) }
                            }
                            1 -> {
                                Section("สีทับพื้นหลัง")
                                ColorRow(listOf("", "#000000", "#FFFFFF", "#7C3AED", "#DB2777", "#0EA5E9", "#10B981", "#F59E0B", "#EF4444"), d.bgColor) { d = d.copy(bgColor = it) }
                                Section("ลวดลาย")
                                OptionRow(listOf("none" to "ไม่มี", "dots" to "• จุด", "stripes" to "╱ ริ้ว", "hearts" to "♥ หัวใจ", "stars" to "★ ดาว", "diamonds" to "◆ เพชร"), d.pattern) { d = d.copy(pattern = it) }
                                Section("รูปหรือ GIF ของคุณ")
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(14.dp))
                                        .clickable { bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                        Text(if (d.imagePath.isBlank()) "เลือกรูปพื้นหลัง" else "เปลี่ยนรูป", color = PureWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                    if (d.imagePath.isNotBlank()) TextButton(onClick = { d = d.copy(imagePath = "", animated = false) }) { Text("ลบรูป", color = Pink400) }
                                }
                                Text("รูปไม่เกิน 5MB · GIF ไม่เกิน 3MB", color = Slate400, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                            2 -> {
                                Section("รูปแบบบับเบิล")
                                OptionRow(listOf("default" to "ปกติ", "rounded" to "มน", "gradient" to "ไล่สี", "glass" to "กระจก", "outline" to "เส้นขอบ", "gold" to "ขอบทอง", "neon" to "นีออน"), d.bubbleStyle) { d = d.copy(bubbleStyle = it) }
                                Section("สีบับเบิล")
                                ColorRow(listOf("#7C3AED", "#DB2777", "#B8860B", "#0EA5E9", "#10B981", "#EF4444", "#111827", "#F9FAFB"), d.bubbleColor) { d = d.copy(bubbleColor = it) }
                                Section("สีตัวอักษร")
                                ColorRow(listOf("#FFFFFF", "#111827", "#FDE68A", "#FBCFE8"), d.textColor) { d = d.copy(textColor = it) }
                                val cr = contrastRatio(hexColor(d.bubbleColor, Pink500), hexColor(d.textColor, White))
                                Text(if (cr >= 4.5) "✓ อ่านง่าย" else "⚠ สีตัวอักษรกับบับเบิลใกล้กันเกินไป อ่านยาก", color = if (cr >= 4.5) Emerald400 else Amber400, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                            }
                            3 -> {
                                Section("เอฟเฟกต์เคลื่อนไหว")
                                OptionRow(listOf("none" to "ไม่มี", "sparkle" to "✨ วิบวับ", "hearts" to "💗 หัวใจ", "stars" to "⭐ ดาว", "snow" to "❄ หิมะ", "bubbles" to "○ ฟอง",
                                    "petals" to "🌸 กลีบซากุระ", "confetti" to "🎉 ปาร์ตี้", "fireflies" to "• หิ่งห้อย"), d.effect) { d = d.copy(effect = it) }
                                Text("ผู้รับเลือก 'โหมดประหยัด' เพื่อหยุดเอฟเฟกต์ได้", color = Slate400, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                            }
                            else -> {
                                Section("แตะเพื่อวางสติกเกอร์ (${d.items.size}/30)")
                                val rows = stickers.chunked(6)
                                rows.forEach { row ->
                                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        row.forEach { s -> Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF))
                                            .clickable(enabled = d.items.size < 30) { d = d.copy(items = d.items + GiftRepo.DecorItem(s.id, 0.5f, 0.5f, 1f, 0f)); selected = d.items.size - 1 }.padding(6.dp)) { StickerImage(s) } }
                                        repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                                if (d.items.isNotEmpty()) {
                                    Section("สติกเกอร์ในห้อง — แตะเพื่อเลือกแล้วลากบนภาพตัวอย่าง")
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        d.items.forEachIndexed { i, it2 -> val s = stickerMap[it2.stickerId]
                                            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x14FFFFFF)).border(if (i == selected) 2.dp else 0.dp, gold, CircleShape).clickable { selected = i }.padding(5.dp)) { s?.let { StickerImage(it) } } }
                                    }
                                    if (selected in d.items.indices) TextButton(onClick = { d = d.copy(items = d.items.filterIndexed { i, _ -> i != selected }); selected = -1 }) { Text("ลบสติกเกอร์ที่เลือก", color = Pink400) }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { scope.launch { val r = GiftRepo.resetDecor(chatId); msg = r.msg; if (r.ok) onClose(true) } }) { Text("รีเซ็ตห้องกลับเป็นค่าเดิม", color = Slate400, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable private fun Section(t: String) = Text(t, color = Slate300, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))

@Composable
private fun OptionRow(opts: List<Pair<String, String>>, sel: String, swatch: ((String) -> List<Color>)? = null, on: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opts.forEach { (k, l) ->
            val active = sel == k
            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(if (active) Color(0x33FF2FA8) else Color(0x14FFFFFF))
                .border(1.dp, if (active) Color(0xFFFF4FB8) else Color(0x22FFFFFF), RoundedCornerShape(14.dp)).clickable { on(k) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                swatch?.let { f -> Box(Modifier.size(14.dp).clip(CircleShape).background(Brush.linearGradient(f(k)))); Spacer(Modifier.width(6.dp)) }
                Text(l, color = if (active) PureWhite else Slate300, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ColorRow(colors: List<String>, sel: String, on: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { c ->
            val active = sel.equals(c, ignoreCase = true)
            Box(Modifier.size(34.dp).clip(CircleShape).border(if (active) 2.5.dp else 1.dp, if (active) Color(0xFFFFC94D) else Color(0x44FFFFFF), CircleShape).padding(3.dp).clip(CircleShape)
                .background(if (c.isBlank()) Brush.linearGradient(listOf(Color(0xFF3A2C72), Color(0xFF16123A))) else Brush.linearGradient(listOf(hexColor(c), hexColor(c))))
                .clickable { on(c) }, contentAlignment = Alignment.Center) {
                if (c.isBlank()) Text("ธีม", color = PureWhite, fontSize = 9.sp)
            }
        }
    }
}

/** Bubble styled by the room decor (same tail shape as the normal chat bubbles). */
@Composable
fun DecorBubble(d: Decor?, text: String, mine: Boolean, content: (@Composable () -> Unit)? = null) {
    val base = if (d == null) (if (mine) Pink600 else Slate800) else hexColor(d.bubbleColor, Pink600).let { if (mine) it else it.copy(alpha = 0.78f) }
    val r = if (d?.bubbleStyle == "default") 14.dp else 20.dp
    val shape = if (mine) RoundedCornerShape(r, r, 6.dp, r) else RoundedCornerShape(r, r, r, 6.dp)
    val textColor = d?.let { hexColor(it.textColor, White) } ?: White
    val m = when (d?.bubbleStyle) {
        "glass" -> Modifier.background(base.copy(alpha = 0.42f)).border(1.dp, Color.White.copy(alpha = 0.35f), shape)
        "gold" -> Modifier.background(base).border(1.5.dp, Brush.linearGradient(listOf(Color(0xFFFFF3B0), Color(0xFFFFD700), Color(0xFFB8860B))), shape)
        "neon" -> Modifier.background(base).border(1.5.dp, Color(0xFF00E5FF), shape)
        "gradient" -> Modifier.background(Brush.linearGradient(listOf(base, lerpColor(base, Color.White, 0.35f))))
        "outline" -> Modifier.background(Color.Black.copy(alpha = 0.25f)).border(1.5.dp, base.copy(alpha = 1f), shape)
        else -> Modifier.background(base)
    }
    Box(Modifier.clip(shape).then(m)) {
        content?.invoke() ?: Text(text, color = textColor, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha)
