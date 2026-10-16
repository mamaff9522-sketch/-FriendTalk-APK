package com.example.ui.random

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlin.random.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Which element of the card the editor is moving. (AVATAR kept for old saved layouts; the photo now fills the card.) */
enum class CardPart { AVATAR, NAME, INTRO }

/**
 * The card: profile photo (or looping intro video) fills the whole card, the chosen frame runs around the card's edge,
 * the effect plays over it, and the name tag (its own frame shape + the owner's decoration) and intro text sit where the
 * owner placed them ([CardLayout]). [videoUri]/[audioUri] override the URLs (editor preview of local files).
 * [partModifier] lets the editor attach drag / pinch to each element. Voice greeting plays once automatically.
 */
@Composable
fun RandomCardView(
    card: RandomCard, name: String, age: Int?, fallbackAvatar: String, modifier: Modifier = Modifier,
    videoUri: Uri? = null, audioUri: Uri? = null, autoplay: Boolean = true, selected: CardPart? = null,
    partModifier: (CardPart) -> Modifier = { Modifier }
) {
    val shape = RoundedCornerShape(24.dp)
    BoxWithConstraints(modifier.aspectRatio(0.75f).clip(shape)
        .background(Brush.linearGradient(listOf(Color(0xFF2B1660), Color(0xFF5A1A6E), Color(0xFFB0306E))))) {
        val w = maxWidth; val h = maxHeight
        // full-card photo / video
        val vid = videoUri?.toString() ?: card.videoUrl
        val photo = card.avatarUrl.ifBlank { fallbackAvatar }
        when {
            vid.isNotBlank() && autoplay -> LoopVideo(vid, Modifier.fillMaxSize())
            photo.isNotBlank() -> AsyncImage(model = photo, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else -> Text(name.take(1).ifBlank { "?" }, color = Color.White.copy(alpha = 0.85f), fontSize = 96.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xAA000000))))
        val gif = com.example.ui.social.rememberGifLoader()
        if (card.effect == "custom") { if (card.effectImageUrl.isNotBlank()) AsyncImage(model = card.effectImageUrl, imageLoader = gif, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        else if (card.effect != "none") CardEffect(card.effectGlyphs.ifEmpty { listOf("✨") }, card.effectMotion)
        val L = card.layout
        // name tag
        Placed(L.name, w, h, null, partModifier(CardPart.NAME), selected == CardPart.NAME) {
            NameTag(name + (age?.let { ", $it" } ?: ""), card.nameFrame, card.nameFrameColors, L.name.s, card.nameFrameStyle, card.nameDeco, card.nameColor, card.nameFrameImageUrl)
        }
        // intro text
        if (card.intro.isNotBlank()) Placed(L.intro, w, h, null, partModifier(CardPart.INTRO), selected == CardPart.INTRO) {
            Text(card.intro, color = Color.White, fontSize = (13 * L.intro.s).sp, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = w * 0.86f).clip(RoundedCornerShape(12.dp)).background(Color(0x55000000)).padding(horizontal = 10.dp, vertical = 6.dp))
        }
        // frame around the whole card
        if (card.frame == "custom") { if (card.frameImageUrl.isNotBlank()) AsyncImage(model = card.frameImageUrl, imageLoader = gif, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
        else if (card.frame != "none" && card.frameColors.size >= 2) CardFrame(card.frameColors)
        val aud = audioUri?.toString() ?: card.audioUrl
        if (aud.isNotBlank() && autoplay) VoiceChip(aud, Modifier.align(Alignment.TopEnd).padding(12.dp))
    }
}

/** Glowing frame along the card's edge; its colours slowly travel around. */
@Composable
fun CardFrame(colors: List<Color>, width: Dp = 6.dp, corner: Dp = 24.dp) {
    val spin by rememberInfiniteTransition(label = "cframe").animateFloat(0f, 360f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "a")
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val a = Math.toRadians(spin.toDouble()); val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2); val r = size.maxDimension / 2
        val d = androidx.compose.ui.geometry.Offset((kotlin.math.cos(a) * r).toFloat(), (kotlin.math.sin(a) * r).toFloat())
        val brush = Brush.linearGradient(colors + colors.first(), start = c - d, end = c + d)
        val sw = width.toPx(); val cr = corner.toPx()
        drawRoundRect(brush, topLeft = androidx.compose.ui.geometry.Offset(sw / 2, sw / 2), size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr - sw / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
        drawRoundRect(Color.White.copy(alpha = 0.35f), topLeft = androidx.compose.ui.geometry.Offset(sw * 1.4f, sw * 1.4f), size = androidx.compose.ui.geometry.Size(size.width - sw * 2.8f, size.height - sw * 2.8f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr - sw * 1.4f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
    }
}

/** Puts [content] with its centre at (x, y) of the card. */
@Composable
private fun Placed(p: Place, w: Dp, h: Dp, size: Dp?, extra: Modifier, selected: Boolean, content: @Composable () -> Unit) {
    var cw by remember { mutableIntStateOf(0) }; var ch by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val sw = size ?: with(density) { cw.toDp() }; val sh = size ?: with(density) { ch.toDp() }
    Box(Modifier.offset(x = w * p.x - sw / 2, y = h * p.y - sh / 2)
        .then(if (size != null) Modifier.size(size) else Modifier)
        .onSizeChanged { cw = it.width; ch = it.height }
        .then(if (selected) Modifier.border(1.5.dp, Color(0xCCFFFFFF), RoundedCornerShape(10.dp)) else Modifier)
        .then(extra), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun FramedCircle(frame: String, colors: List<Color>, content: @Composable () -> Unit) {
    val spin by rememberInfiniteTransition(label = "frame").animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "spin")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (frame != "none" && colors.size >= 2) Box(Modifier.fillMaxSize().rotate(spin).clip(CircleShape).background(Brush.sweepGradient(colors + colors.first())))
        else Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0x55FFFFFF)))
        Box(Modifier.fillMaxSize().padding(if (frame != "none") 5.dp else 3.dp).clip(CircleShape).background(Color(0xFF1A1036))) { content() }
    }
}

/** Name-tag decorations the owner can add for free, and text colours. */
val NAME_DECOS = listOf("" to "ไม่มี", "♡" to "หัวใจ", "✦" to "ประกาย", "👑" to "มงกุฎ", "🌸" to "ดอกไม้", "⭐" to "ดาว", "🎀" to "โบว์", "🔥" to "ไฟ", "💎" to "เพชร", "🦋" to "ผีเสื้อ", "🐱" to "แมว", "🌙" to "พระจันทร์")
val NAME_COLORS = listOf("", "#FFD45C", "#FF6FB5", "#7AF5FF", "#47E891", "#C9A2FF", "#FF7A59", "#1E1B3A")

private val RibbonShape = GenericShape { s, _ ->
    val n = s.height * 0.32f
    moveTo(0f, 0f); lineTo(s.width, 0f); lineTo(s.width - n, s.height / 2); lineTo(s.width, s.height); lineTo(0f, s.height); lineTo(n, s.height / 2); close()
}
private val TagShape = GenericShape { s, _ ->
    val n = s.height * 0.42f
    moveTo(n, 0f); lineTo(s.width, 0f); lineTo(s.width, s.height); lineTo(n, s.height); lineTo(0f, s.height / 2); close()
}

/**
 * Name tag in the chosen frame [style]: pill, neon, ribbon, bubble, badge, glitter, outline, royal, cloud or tag
 * (frame "none" = plain dark pill). [deco] = sticker at both ends, [textColor] = #RRGGBB (blank = default).
 */
@Composable
fun NameTag(text: String, frame: String, colors: List<Color>, scale: Float = 1f, style: String = "pill", deco: String = "", textColor: String = "", imageUrl: String = "") {
    val has = frame != "none" && colors.size >= 2
    val st = when { frame == "custom" -> "image"; has -> style; else -> "plain" }
    val inf = rememberInfiniteTransition(label = "tag")
    val pulse by inf.animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "p")
    val shine by inf.animateFloat(-1f, 2f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "s")
    val shape: Shape = when (st) {
        "ribbon" -> RibbonShape; "tag" -> TagShape; "badge" -> CutCornerShape(30)
        "bubble" -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
        "neon", "outline" -> RoundedCornerShape(14.dp)
        else -> RoundedCornerShape(50)
    }
    val defaultText = if (st == "cloud") Color(0xFF2B2160) else Color.White
    val tc = RandomCardRepo.hex(textColor) ?: if (st == "neon") colors.firstOrNull() ?: Color.White else defaultText
    val bg: Modifier = when (st) {
        "plain" -> Modifier.background(Color(0x66000000), shape)
        "image" -> Modifier
        "neon" -> Modifier.background(Color(0xCC0A0820), shape).border(2.dp, colors.first().copy(alpha = pulse), shape)
            .border(5.dp, colors.last().copy(alpha = 0.25f * pulse), shape)
        "outline" -> Modifier.border(2.dp, colors.first(), shape).padding(3.dp).border(1.dp, colors.last(), shape)
        "cloud" -> Modifier.background(Brush.verticalGradient(colors), shape).border(2.dp, Color.White, shape)
        "royal" -> Modifier.background(Brush.horizontalGradient(colors + colors.first()), shape).border(1.5.dp, Color(0xFFFFF3B0), shape)
        else -> Modifier.background(Brush.horizontalGradient(colors), shape).border(1.5.dp, Color(0xCCFFFFFF), shape)
    }
    val padH = when (st) { "ribbon" -> 26; "tag" -> 22; "image" -> 26; else -> 14 }
    Box(contentAlignment = Alignment.Center) {
        // own uploaded name frame: the picture is stretched behind the name
        if (st == "image" && imageUrl.isNotBlank()) AsyncImage(model = imageUrl, imageLoader = com.example.ui.social.rememberGifLoader(), contentDescription = null,
            contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize())
        Row(Modifier.padding(top = if (st == "royal") (14 * scale).dp else 0.dp).then(bg).clip(shape)
            .then(if (st == "glitter") Modifier.drawWithContent {
                drawContent()
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                    start = androidx.compose.ui.geometry.Offset(size.width * shine, 0f), end = androidx.compose.ui.geometry.Offset(size.width * shine + size.height * 1.5f, size.height)))
            } else Modifier)
            .padding(start = (padH * scale).dp, end = (padH * scale).dp, top = ((if (st == "image") 12 else 6) * scale).dp, bottom = ((if (st == "image") 12 else 6) * scale).dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (deco.isNotBlank()) Text(deco, color = tc, fontSize = (15 * scale).sp, modifier = Modifier.padding(end = 5.dp))
            if (st == "glitter") Text("✦", color = Color.White.copy(alpha = pulse), fontSize = (10 * scale).sp, modifier = Modifier.padding(end = 4.dp))
            Text(text, color = tc, fontSize = (17 * scale).sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(shadow = if (st == "cloud") null else androidx.compose.ui.graphics.Shadow(if (st == "neon") tc.copy(alpha = pulse) else Color(0x99000000), blurRadius = if (st == "neon") 14f else 6f)))
            if (st == "glitter") Text("✦", color = Color.White.copy(alpha = 1.5f - pulse), fontSize = (10 * scale).sp, modifier = Modifier.padding(start = 4.dp))
            if (deco.isNotBlank()) Text(deco, color = tc, fontSize = (15 * scale).sp, modifier = Modifier.padding(start = 5.dp))
        }
        if (st == "royal") Text("👑", fontSize = (18 * scale).sp, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-4).dp))
    }
}

/** Effect over the whole card: the glyphs move in the item's own way ([motion]) + a soft light. */
@Composable
fun CardEffect(glyphs: List<String>, motion: String = "rise") {
    val t by rememberInfiniteTransition(label = "fx").animateFloat(0f, 1f, infiniteRepeatable(tween(if (motion == "rain" || motion == "shoot") 3200 else 7000, easing = LinearEasing)), label = "t")
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(0.12f, 0.35f, infiniteRepeatable(tween(1600), androidx.compose.animation.core.RepeatMode.Reverse), label = "g")
    val n = when (motion) { "rain" -> 22; "orbit", "spiral" -> 10; "shoot" -> 6; else -> 16 }
    val seeds = remember(motion) { List(n) { Triple(Random.nextFloat(), Random.nextFloat(), 12 + Random.nextInt(10)) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFFFF6FD8).copy(alpha = glow), Color.Transparent))))
        val w = maxWidth; val h = maxHeight
        seeds.forEachIndexed { i, (sx, sy, sz) ->
            val ph = (sy + t) % 1f
            val tw = 2f * PI.toFloat()
            var scale = 1f
            val (x, y, a) = when (motion) {
                "fall" -> Triple(sx + sin(ph * 6f + i) * 0.05f, ph, 1f - abs(ph - 0.5f) * 1.6f)
                "rain" -> Triple(sx, ph * 1.1f - 0.05f, 0.9f)
                "sway" -> Triple(sx + sin(ph * 10f + i) * 0.1f, 1f - ph, 1f - abs(ph - 0.5f) * 1.6f)
                "twinkle" -> { val k = abs(sin((ph * 2f + i * 0.37f) * tw / 2)); scale = 0.6f + 0.6f * k; Triple(sx, sy, k) }
                "burst" -> { val ang = sx * tw; val d = ph * 0.55f; Triple(0.5f + cos(ang) * d, 0.42f + sin(ang) * d * 0.75f, 1f - ph) }
                "orbit" -> { val ang = (t + i.toFloat() / n) * tw; Triple(0.5f + cos(ang) * 0.38f, 0.42f + sin(ang) * 0.28f, 0.95f) }
                "spiral" -> { val ang = (ph * 2f + i.toFloat() / n) * tw; val r = 0.05f + ph * 0.42f; Triple(0.5f + cos(ang) * r, 0.45f + sin(ang) * r * 0.8f, 1f - ph) }
                "zigzag" -> Triple(ph, sy * 0.8f + 0.1f + (if (((ph * 8f).toInt()) % 2 == 0) (ph * 8f % 1f) else 1f - (ph * 8f % 1f)) * 0.06f, 1f - abs(ph - 0.5f) * 1.6f)
                "shoot" -> Triple(sx * 0.6f - 0.2f + ph * 0.9f, sy * 0.5f - 0.1f + ph * 0.7f, (1f - ph) * (if (ph < 0.08f) ph * 12f else 1f))
                else -> Triple(sx, 1f - ph, 1f - abs(ph - 0.5f) * 1.6f) // rise
            }
            Text(glyphs[i % glyphs.size], fontSize = sz.sp, color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.offset(x = w * x, y = h * y).graphicsLayer { alpha = a.coerceIn(0f, 1f); scaleX = scale; scaleY = scale; rotationZ = if (motion == "spiral" || motion == "fall") ph * 360f else 0f })
        }
    }
}

/** Looping, auto-playing video (TextureView + MediaPlayer, same approach as post videos). */
@Composable
fun LoopVideo(url: String, modifier: Modifier) {
    var player by remember(url) { mutableStateOf<android.media.MediaPlayer?>(null) }
    DisposableEffect(url) { onDispose { runCatching { player?.release() }; player = null } }
    androidx.compose.runtime.key(url) {
        AndroidView(factory = { c ->
            android.view.TextureView(c).apply {
                surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                        val mp = android.media.MediaPlayer(); player = mp
                        runCatching {
                            mp.setSurface(android.view.Surface(st)); mp.isLooping = true
                            mp.setDataSource(c, Uri.parse(url)); mp.setOnPreparedListener { it.start() }; mp.setOnErrorListener { _, _, _ -> true }; mp.prepareAsync()
                        }
                    }
                    override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean { runCatching { player?.release() }; player = null; return true }
                    override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
                }
            }
        }, modifier = modifier)
    }
}

/** Voice greeting: plays once when the card appears; tap to play again / stop. */
@Composable
fun VoiceChip(url: String, modifier: Modifier) {
    val ctx = LocalContext.current
    var playing by remember(url) { mutableStateOf(false) }
    val mp = remember(url) { android.media.MediaPlayer() }
    fun play() = runCatching { mp.reset(); mp.setDataSource(ctx, Uri.parse(url)); mp.setOnPreparedListener { it.start(); playing = true }; mp.setOnCompletionListener { playing = false }; mp.prepareAsync() }
    LaunchedEffect(url) { play() }
    DisposableEffect(url) { onDispose { runCatching { mp.release() } } }
    Text(if (playing) "🔊 " + "กำลังเล่นเสียง" else "🔈 " + "ฟังเสียงทักทาย", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x66000000)).clickable {
            if (playing) { runCatching { mp.stop() }; playing = false } else play()
        }.padding(horizontal = 10.dp, vertical = 5.dp))
}
