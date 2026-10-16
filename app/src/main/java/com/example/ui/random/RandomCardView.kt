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

/** Which element of the card the editor is moving. */
enum class CardPart { AVATAR, NAME, INTRO }

/**
 * The card: background, effect, profile photo (or looping intro video) in its frame, name tag in its name frame,
 * intro text — each placed by [CardLayout]. [videoUri]/[audioUri] override the URLs (editor preview of local files).
 * [partModifier] lets the editor attach drag / pinch to each element. Voice greeting plays once automatically.
 */
@Composable
fun RandomCardView(
    card: RandomCard, name: String, age: Int?, fallbackAvatar: String, modifier: Modifier = Modifier,
    videoUri: Uri? = null, audioUri: Uri? = null, autoplay: Boolean = true, selected: CardPart? = null,
    partModifier: (CardPart) -> Modifier = { Modifier }
) {
    BoxWithConstraints(modifier.aspectRatio(0.75f).clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF2B1660), Color(0xFF5A1A6E), Color(0xFFB0306E))))) {
        val w = maxWidth; val h = maxHeight
        if (card.bgUrl.isNotBlank()) AsyncImage(model = card.bgUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))))
        if (card.effect != "none") CardEffect(card.effectGlyphs.ifEmpty { listOf("✨") })
        val L = card.layout
        // profile photo / intro video in the frame
        val av = 0.42f * w.value
        Placed(L.avatar, w, h, (av * L.avatar.s).dp, partModifier(CardPart.AVATAR), selected == CardPart.AVATAR) {
            FramedCircle(card.frame, card.frameColors) {
                val vid = videoUri?.toString() ?: card.videoUrl
                if (vid.isNotBlank() && autoplay) LoopVideo(vid, Modifier.fillMaxSize())
                else AsyncImage(model = card.avatarUrl.ifBlank { fallbackAvatar }, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        // name tag
        Placed(L.name, w, h, null, partModifier(CardPart.NAME), selected == CardPart.NAME) {
            NameTag(name + (age?.let { ", $it" } ?: ""), card.nameFrame, card.nameFrameColors, L.name.s)
        }
        // intro text
        if (card.intro.isNotBlank()) Placed(L.intro, w, h, null, partModifier(CardPart.INTRO), selected == CardPart.INTRO) {
            Text(card.intro, color = Color.White, fontSize = (13 * L.intro.s).sp, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = w * 0.86f).clip(RoundedCornerShape(12.dp)).background(Color(0x55000000)).padding(horizontal = 10.dp, vertical = 6.dp))
        }
        val aud = audioUri?.toString() ?: card.audioUrl
        if (aud.isNotBlank() && autoplay) VoiceChip(aud, Modifier.align(Alignment.TopEnd).padding(10.dp))
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

@Composable
fun NameTag(text: String, frame: String, colors: List<Color>, scale: Float = 1f) {
    val bg = if (frame != "none" && colors.size >= 2) Brush.horizontalGradient(colors) else Brush.horizontalGradient(listOf(Color(0x66000000), Color(0x66000000)))
    Text(text, color = Color.White, fontSize = (17 * scale).sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), blurRadius = 6f)),
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bg)
            .then(if (frame != "none") Modifier.border(1.5.dp, Color(0xCCFFFFFF), RoundedCornerShape(20.dp)) else Modifier)
            .padding(horizontal = (14 * scale).dp, vertical = (6 * scale).dp))
}

/** Floating glyphs + soft light over the whole card. */
@Composable
fun CardEffect(glyphs: List<String>) {
    val t by rememberInfiniteTransition(label = "fx").animateFloat(0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "t")
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(0.15f, 0.4f, infiniteRepeatable(tween(1600), androidx.compose.animation.core.RepeatMode.Reverse), label = "g")
    val seeds = remember { List(16) { Triple(Random.nextFloat(), Random.nextFloat(), 12 + Random.nextInt(10)) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFFFF6FD8).copy(alpha = glow), Color.Transparent))))
        val w = maxWidth; val h = maxHeight
        seeds.forEachIndexed { i, (sx, sy, sz) ->
            val y = 1f - ((sy + t) % 1f)
            Text(glyphs[i % glyphs.size], fontSize = sz.sp, color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.offset(x = w * sx, y = h * y).graphicsLayer { alpha = (1f - kotlin.math.abs(y - 0.5f) * 1.6f).coerceIn(0f, 1f) })
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
