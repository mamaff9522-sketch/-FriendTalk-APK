package com.example.rtc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * TikTok-style gift effect in the middle of the live screen. Every gift sent in the room (by anyone, including me) plays once,
 * one after another. Size of the show follows the gift: small = pop, medium = glow + sparkle burst, big (500+ 💎 or
 * ROYAL/BLAST in the catalog) = dimmed screen, rotating light rays, confetti and a bigger picture.
 * Draws only — never takes touches, so chat and buttons underneath keep working.
 */
@Composable
fun GiftBurstLayer(msgs: List<LiveMsg>, modifier: Modifier = Modifier.fillMaxSize()) {
    val catalog by remember { com.example.social.GiftRepo.catalog("live") }.collectAsState(initial = emptyList())
    val openedAt = remember { System.currentTimeMillis() }
    val seen = remember { HashSet<String>() }
    val queue = remember { mutableStateListOf<LiveMsg>() }
    LaunchedEffect(msgs.size, msgs.lastOrNull()?.id) { // list is capped, so watch the newest id too
        msgs.forEach { m ->
            if (m.type != "gift" || !seen.add(m.id)) return@forEach
            if (m.at >= openedAt - 3_000) queue.add(m) // only gifts sent while I'm watching, not history
        }
        while (queue.size > 8) queue.removeAt(1) // keep the one playing, drop the oldest waiting
    }
    val cur = queue.firstOrNull() ?: return
    val item = catalog.firstOrNull { it.id == cur.giftId }
    val price = if (cur.price > 0) cur.price else item?.price ?: 0
    val fx = cur.fx.ifBlank { item?.animationEffect ?: "" }
    val tier = when { fx == "BLAST" || fx == "ROYAL" || price >= 500 -> 2; fx == "FLOAT" || price >= 100 -> 1; else -> 0 }
    val image = cur.imageUrl.ifBlank { item?.imageUrl.orEmpty() }
    key(cur.id) {
        GiftBurst(cur, image, tier, modifier) { if (queue.firstOrNull()?.id == cur.id) queue.removeAt(0) }
    }
}

private val Pink = Color(0xFFFF2FA8)
private val Gold = Color(0xFFFFD45C)
private val Purple = Color(0xFF9B3DFF)
private val Cyan = Color(0xFF7AF5FF)

private class Particle(val angle: Float, val speed: Float, val size: Float, val color: Color, val star: Boolean, val spin: Float)

@Composable
private fun GiftBurst(m: LiveMsg, image: String, tier: Int, modifier: Modifier, onDone: () -> Unit) {
    val total = when (tier) { 2 -> 3800L; 1 -> 2600L; else -> 1900L }
    val pop = remember { Animatable(0f) }      // picture scale
    val fade = remember { Animatable(0f) }     // whole layer alpha
    val burst = remember { Animatable(0f) }    // particles flying out 0..1
    val fall = remember { Animatable(0f) }     // confetti falling 0..1 (big gifts)
    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, tween(180)) }
        launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
        launch { burst.animateTo(1f, tween(if (tier == 2) 1600 else 1100, easing = FastOutSlowInEasing)) }
        if (tier == 2) launch { fall.animateTo(1f, tween(total.toInt(), easing = LinearEasing)) }
        delay(total - 350)
        fade.animateTo(0f, tween(350))
        onDone()
    }
    val inf = rememberInfiniteTransition(label = "gift")
    val spinDeg by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "rays")
    val glow by inf.animateFloat(0.75f, 1.1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow")
    val bob by inf.animateFloat(-6f, 6f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob")
    val particles = remember {
        val n = when (tier) { 2 -> 46; 1 -> 28; else -> 14 }
        val colors = listOf(Pink, Gold, Purple, Cyan, Color.White)
        List(n) { Particle(Random.nextFloat() * 2f * PI.toFloat(), 0.45f + Random.nextFloat() * 0.6f, 4f + Random.nextFloat() * if (tier == 2) 10f else 7f,
            colors[it % colors.size], Random.nextBoolean(), Random.nextFloat() * 360f) }
    }
    val confetti = remember {
        List(if (tier == 2) 60 else 0) { Particle(Random.nextFloat(), 0.6f + Random.nextFloat() * 0.8f, 6f + Random.nextFloat() * 8f,
            listOf(Pink, Gold, Purple, Cyan)[it % 4], it % 3 == 0, Random.nextFloat() * 360f) }
    }
    val picSize = when (tier) { 2 -> 230.dp; 1 -> 180.dp; else -> 140.dp }

    Box(modifier.graphicsLayer { alpha = fade.value }, contentAlignment = Alignment.Center) {
        if (tier == 2) Box(Modifier.fillMaxSize().background(Color(0x73000000)))
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.42f)
            val reach = size.minDimension * if (tier == 2) 0.62f else 0.45f
            if (tier == 2) rotate(spinDeg, c) { drawRays(c, size.maxDimension, 14) }
            if (tier >= 1) drawCircle(Brush.radialGradient(listOf((if (tier == 2) Gold else Pink).copy(alpha = 0.55f * glow), Color.Transparent), c, reach * 0.75f * glow), reach * 0.75f * glow, c)
            val p = burst.value
            particles.forEach { pt ->
                val d = reach * pt.speed * p
                val pos = Offset(c.x + cos(pt.angle) * d, c.y + sin(pt.angle) * d + 40f * p * p)
                val a = (1f - p).coerceIn(0f, 1f)
                if (pt.star) drawSparkle(pos, pt.size * 1.6f, pt.color.copy(alpha = a), pt.spin + p * 180f)
                else drawCircle(pt.color.copy(alpha = a), pt.size, pos)
            }
            val f = fall.value
            confetti.forEach { cf ->
                val y = -40f + (size.height + 80f) * ((f * cf.speed * 1.6f + cf.angle * 0.35f) % 1f)
                val x = size.width * cf.angle + sin((f * 8f + cf.spin) .toDouble()).toFloat() * 24f
                rotate(cf.spin + f * 720f, Offset(x, y)) {
                    if (cf.star) drawSparkle(Offset(x, y), cf.size * 1.4f, cf.color, 0f)
                    else drawRect(cf.color, Offset(x - cf.size / 2, y - cf.size), androidx.compose.ui.geometry.Size(cf.size, cf.size * 1.8f))
                }
            }
        }
        Column(Modifier.fillMaxSize().padding(bottom = 80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(picSize).graphicsLayer {
                scaleX = pop.value; scaleY = pop.value; translationY = if (tier >= 1) bob else 0f
            }, contentAlignment = Alignment.Center) {
                if (image.isNotBlank()) AsyncImage(model = image, contentDescription = m.giftName, modifier = Modifier.fillMaxSize())
                else Text(m.icon.ifBlank { "🎁" }, fontSize = (picSize.value * 0.6f).sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.graphicsLayer { alpha = pop.value.coerceIn(0f, 1f); translationY = (1f - pop.value.coerceIn(0f, 1f)) * 40f }
                .clip(RoundedCornerShape(28.dp)).background(Brush.horizontalGradient(listOf(Color(0xE6FF2FA8), Color(0xCC9B3DFF))))
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                com.example.ui.social.Avatar(m.avatar, m.name.ifBlank { "?" }, 34)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.widthIn(max = 170.dp)) {
                    Text(m.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("ส่ง ${m.giftName}", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (m.count > 1) Text(" x${m.count}", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            }
        }
    }
}

private fun DrawScope.drawRays(c: Offset, len: Float, n: Int) {
    val step = 360f / n
    for (i in 0 until n) {
        val a0 = Math.toRadians((i * step).toDouble()); val a1 = Math.toRadians((i * step + step * 0.45f).toDouble())
        val path = Path().apply {
            moveTo(c.x, c.y)
            lineTo(c.x + cos(a0).toFloat() * len, c.y + sin(a0).toFloat() * len)
            lineTo(c.x + cos(a1).toFloat() * len, c.y + sin(a1).toFloat() * len)
            close()
        }
        drawPath(path, Brush.radialGradient(listOf(Gold.copy(alpha = 0.35f), Color.Transparent), c, len * 0.7f))
    }
}

/** 4-point twinkle star (thin diamond points). */
private fun DrawScope.drawSparkle(c: Offset, r: Float, color: Color, deg: Float) {
    rotate(deg, c) {
        val i = r * 0.28f
        val path = Path().apply {
            moveTo(c.x, c.y - r); lineTo(c.x + i, c.y - i); lineTo(c.x + r, c.y); lineTo(c.x + i, c.y + i)
            lineTo(c.x, c.y + r); lineTo(c.x - i, c.y + i); lineTo(c.x - r, c.y); lineTo(c.x - i, c.y - i); close()
        }
        drawPath(path, color)
    }
}
