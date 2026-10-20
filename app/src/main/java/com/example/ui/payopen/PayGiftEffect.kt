package com.example.ui.payopen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Effect styles an admin can pick for a เปย์เปิดใจ gift (catalog field animationEffect). Each one is a different show. */
val PAY_FX = listOf(
    "HEARTS" to "หัวใจลอย", "STARS" to "ดาวระยิบ", "BALLOONS" to "ลูกโป่งลอย", "BOUNCE" to "เด้งดึ๋ง", "BUBBLES" to "ฟองสบู่",
    "PETALS" to "กลีบดอกไม้", "ORBIT" to "หัวใจโคจร", "RAINBOW" to "สายรุ้ง", "BUTTERFLY" to "ผีเสื้อกระพือ", "SPARKLE" to "ประกายเพชร",
    "ROYAL" to "รัศมีทอง", "FIREWORK" to "พลุ"
)

private enum class Motion { RISE, FALL, ORBIT, BURST, RING, SWAY }
private class Style(val glyphs: List<String>, val motion: Motion, val glow: Color, val count: Int)

private fun styleOf(fx: String): Style = when (fx.uppercase()) {
    "HEARTS" -> Style(listOf("💗", "💖", "💕", "❤️"), Motion.RISE, Color(0xFFFF4FA3), 18)
    "STARS" -> Style(listOf("⭐", "✨", "🌟"), Motion.RING, Color(0xFFFFD45C), 14)
    "BALLOONS" -> Style(listOf("🎈", "🎈", "💗"), Motion.RISE, Color(0xFFFF6F91), 10)
    "BOUNCE" -> Style(listOf("💫", "✨"), Motion.BURST, Color(0xFFFFB347), 12)
    "BUBBLES" -> Style(listOf("🫧", "○", "◦"), Motion.SWAY, Color(0xFF7AF5FF), 18)
    "PETALS" -> Style(listOf("🌸", "🌺", "🌷"), Motion.FALL, Color(0xFFFFB3C7), 16)
    "ORBIT" -> Style(listOf("💞", "💗", "💘"), Motion.ORBIT, Color(0xFFFF2FA8), 8)
    "RAINBOW" -> Style(listOf("🌈", "☁️", "✨"), Motion.SWAY, Color(0xFF8EC5FC), 8)
    "BUTTERFLY" -> Style(listOf("🦋", "✨"), Motion.ORBIT, Color(0xFF9B8CFF), 6)
    "SPARKLE" -> Style(listOf("💎", "✨", "✦"), Motion.BURST, Color(0xFFB9F2FF), 20)
    "ROYAL" -> Style(listOf("👑", "✨", "⭐"), Motion.RING, Color(0xFFFFD700), 12)
    "FIREWORK" -> Style(listOf("🎆", "🎇", "✨", "🎉"), Motion.BURST, Color(0xFFFF5F6D), 24)
    else -> styleOf(PAY_FX[abs(fx.hashCode()) % PAY_FX.size].first) // admin typed something else → still a stable, own style
}

/**
 * The gift in the middle of the screen (medium size): picture pops in, the style's own particles + light around it.
 * An admin-uploaded animated effect (GIF / animated WebP) plays as the main picture.
 */
@Composable
fun PayGiftEffect(fx: String, imageUrl: String, effectUrl: String, icon: String = "", size: Dp = 260.dp) {
    val st = remember(fx) { styleOf(fx) }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)) }
    val inf = rememberInfiniteTransition(label = "payfx")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "t")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "spin")
    val pulse by inf.animateFloat(0.8f, 1.12f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse")
    val flap by inf.animateFloat(0.55f, 1f, infiniteRepeatable(tween(220), RepeatMode.Reverse), label = "flap")
    val bounce by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "bounce")
    val seeds = remember(fx) { List(st.count) { Triple(Random.nextFloat(), Random.nextFloat(), 14 + Random.nextInt(14)) } }
    val loader = com.example.ui.social.rememberGifLoader()
    val upper = fx.uppercase()

    BoxWithConstraints(Modifier.size(size), contentAlignment = Alignment.Center) {
        val w = maxWidth; val h = maxHeight
        // light behind the gift
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2, this.size.height / 2); val r = this.size.minDimension / 2
            when (upper) {
                "ROYAL" -> rotate(spin, c) {
                    for (i in 0 until 12) { val a0 = i * 30.0 * PI / 180; val a1 = a0 + 0.22
                        drawPath(Path().apply { moveTo(c.x, c.y); lineTo(c.x + cos(a0).toFloat() * r, c.y + sin(a0).toFloat() * r); lineTo(c.x + cos(a1).toFloat() * r, c.y + sin(a1).toFloat() * r); close() },
                            Brush.radialGradient(listOf(st.glow.copy(alpha = 0.55f), Color.Transparent), c, r)) }
                }
                "RAINBOW" -> listOf(0xFFFF5F6D, 0xFFFFC371, 0xFF47E891, 0xFF4FACFE, 0xFFB06AB3).forEachIndexed { i, col ->
                    val rr = r * (0.95f - i * 0.1f)
                    drawArc(Color(col).copy(alpha = 0.75f), 180f, 180f * pop.value, false, Offset(c.x - rr, c.y - rr + r * 0.15f), Size(rr * 2, rr * 2), style = Stroke(r * 0.07f))
                }
                "BOUNCE" -> for (k in 0..2) { val p = (t + k / 3f) % 1f; drawCircle(st.glow.copy(alpha = (1f - p) * 0.6f), r * (0.35f + p * 0.65f), c, style = Stroke(6f)) }
                "SPARKLE" -> { drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f * pulse), st.glow.copy(alpha = 0.3f), Color.Transparent), c, r), r, c) }
                else -> drawCircle(Brush.radialGradient(listOf(st.glow.copy(alpha = 0.5f * pulse), Color.Transparent), c, r * pulse), r * pulse, c)
            }
        }
        // particles
        seeds.forEachIndexed { i, (sx, sy, sz) ->
            val g = st.glyphs[i % st.glyphs.size]
            val ph = (t + sy) % 1f
            val (x, y, a) = when (st.motion) {
                Motion.RISE -> Triple(sx, 1f - ph, (1f - abs(ph - 0.5f) * 1.8f))
                Motion.FALL -> Triple((sx + sin(ph * 6f + i) * 0.06f).coerceIn(0f, 1f), ph, (1f - abs(ph - 0.5f) * 1.8f))
                Motion.SWAY -> Triple((sx + sin(ph * 9f + i) * 0.08f).coerceIn(0f, 1f), 1f - ph, 0.9f - ph * 0.7f)
                Motion.ORBIT -> { val ang = (spin / 360f + i.toFloat() / st.count) * 2 * PI; Triple(0.5f + cos(ang).toFloat() * 0.42f, 0.5f + sin(ang).toFloat() * 0.42f, 0.95f) }
                Motion.RING -> { val ang = i.toFloat() / st.count * 2 * PI; val rr = 0.36f + 0.06f * sin(ph * 2 * PI).toFloat(); Triple(0.5f + cos(ang).toFloat() * rr, 0.5f + sin(ang).toFloat() * rr, 0.4f + 0.6f * abs(sin((ph + i * 0.3f) * PI)).toFloat()) }
                Motion.BURST -> { val ang = sx * 2 * PI; val d = 0.08f + ph * 0.45f; Triple(0.5f + cos(ang).toFloat() * d, 0.5f + sin(ang).toFloat() * d, 1f - ph) }
            }
            Text(g, fontSize = sz.sp, color = Color.White, modifier = Modifier.align(Alignment.TopStart)
                .offset(x = w * x - (sz / 2).dp, y = h * y - (sz / 2).dp).graphicsLayer { alpha = a.coerceIn(0f, 1f) * pop.value })
        }
        // the gift itself
        val pic = size * 0.52f
        Box(Modifier.size(pic).graphicsLayer {
            scaleX = pop.value * (if (upper == "BUTTERFLY") flap else 1f) * (if (upper == "BOUNCE") 1f + 0.08f * bounce else 1f)
            scaleY = pop.value * (if (upper == "BOUNCE") 1f - 0.08f * bounce else 1f)
            translationY = if (upper == "BOUNCE") -bounce * 26f else if (upper == "BALLOONS") sin(t * 2 * PI).toFloat() * 10f else 0f
            rotationZ = if (upper == "PETALS" || upper == "STARS") sin(t * 2 * PI).toFloat() * 8f else 0f
        }, contentAlignment = Alignment.Center) {
            when {
                effectUrl.isNotBlank() -> AsyncImage(model = effectUrl, imageLoader = loader, contentDescription = null, modifier = Modifier.fillMaxSize())
                imageUrl.isNotBlank() -> AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                else -> Text(icon.ifBlank { "🎁" }, fontSize = (pic.value * 0.6f).sp)
            }
        }
    }
}

/** Diamonds flying back to the sender's wallet (no text — the refund just lands in the wallet). */
@Composable
fun DiamondsBack(onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(1900, easing = LinearEasing)); onDone() }
    val seeds = remember { List(16) { Triple(Random.nextFloat(), Random.nextFloat(), 18 + Random.nextInt(14)) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        val wallet = Offset(0.5f, 0.78f)
        val v = p.value
        seeds.forEachIndexed { i, (sx, sy, sz) ->
            val start = Offset(0.15f + sx * 0.7f, 0.12f + sy * 0.35f)
            val k = ((v - i * 0.025f) / 0.75f).coerceIn(0f, 1f)
            val e = k * k * (3 - 2 * k)
            val x = start.x + (wallet.x - start.x) * e + sin(k * PI).toFloat() * 0.08f * (if (i % 2 == 0) 1 else -1)
            val y = start.y + (wallet.y - start.y) * e
            Text("💎", fontSize = (sz * (1f - 0.45f * e)).sp, modifier = Modifier.offset(x = w * x, y = h * y)
                .graphicsLayer { alpha = if (k >= 1f) 0f else (0.3f + k).coerceAtMost(1f); rotationZ = k * 360f })
        }
        val bump = if (v > 0.6f) 1f + 0.25f * sin(((v - 0.6f) / 0.4f) * PI).toFloat() else 1f
        Text("👛", fontSize = 54.sp, modifier = Modifier.offset(x = w * wallet.x - 27.dp, y = h * wallet.y - 10.dp)
            .graphicsLayer { scaleX = bump; scaleY = bump; alpha = (if (v < 0.1f) v * 10f else if (v > 0.9f) (1f - v) * 10f else 1f).coerceIn(0f, 1f) })
    }
}
