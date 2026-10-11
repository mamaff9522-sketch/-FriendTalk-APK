package com.example.ui.social

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import com.example.ui.components.ft.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Strong haptics + rattle sound for the shake screen. Sound only in normal ringer mode. */
class ShakeFeedback(private val ctx: Context) {
    private val vib: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    private val pool = SoundPool.Builder().setMaxStreams(2)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val soundId = pool.load(ctx, com.example.R.raw.shake_rattle, 1)
    private var lastPulse = 0L

    private fun vibrate(timings: LongArray, amps: IntArray) {
        val v = vib ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                if (v.hasAmplitudeControl()) v.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
                else v.vibrate(VibrationEffect.createWaveform(timings, -1)) // on/off fallback, still full strength
            } else @Suppress("DEPRECATION") v.vibrate(timings, -1)
        } catch (_: Exception) {}
    }
    /** Shake detected: strong double buzz + rattle. */
    fun trigger(volume: Float) {
        vibrate(longArrayOf(0, 120, 60, 180), intArrayOf(0, 255, 0, 255))
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am?.ringerMode == AudioManager.RINGER_MODE_NORMAL) { val vol = volume.coerceIn(0.4f, 1f); pool.play(soundId, vol, vol, 1, 0, 1f) }
    }
    /** While the phone is moving hard: short strong pulses, at most every 180 ms. */
    fun pulse(level: Float) {
        val now = System.currentTimeMillis()
        if (level < 0.35f || now - lastPulse < 180) return
        lastPulse = now
        vibrate(longArrayOf(0, 40), intArrayOf(0, (150 + 105 * level).toInt().coerceAtMost(255)))
    }
    fun release() { pool.release() }
}

/**
 * Central shake icon: wiggles (rotation + horizontal offset) with live motion [level] 0..1,
 * a [burst] 0..1 decaying after a detected shake (ripple rings + sparkles), and an idle hint.
 */
@Composable
fun ShakeIcon(level: Float, burst: Float, burstStrength: Float, idleHint: Boolean, icon: String = "📱") {
    val inf = rememberInfiniteTransition(label = "wiggle")
    val phase by inf.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(140, easing = LinearEasing)), label = "phase")
    val hint = remember { Animatable(0f) }
    LaunchedEffect(idleHint) {
        while (idleHint) {
            kotlinx.coroutines.delay(3200)
            hint.animateTo(0.45f, tween(150)); hint.animateTo(0f, tween(550))
        }
    }
    val amp = maxOf(level, burst, hint.value).coerceIn(0f, 1f)
    val sparkle = maxOf(burst * (0.6f + 0.6f * burstStrength), if (level > 0.3f) level * 0.5f else 0f)
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val maxR = size.minDimension / 2
            if (burst > 0f) {
                val p = 1f - burst
                for (k in 0 until 3) {
                    val q = (p - k * 0.18f).coerceIn(0f, 1f)
                    if (q <= 0f) continue
                    drawCircle(Color(0xFFEC4899).copy(alpha = (1f - q) * burst), radius = maxR * (0.35f + 0.65f * q),
                        center = c, style = Stroke(width = (6f - 4f * q) * density * (0.6f + burstStrength)))
                }
            }
            if (sparkle > 0.02f) {
                val n = 16
                val dist = maxR * (0.45f + 0.5f * (1f - burst).coerceIn(0f, 1f))
                for (i in 0 until n) {
                    val a = i * (2 * Math.PI / n) + phase * 0.15
                    val r = dist * (0.85f + 0.15f * sin(phase + i))
                    val pos = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
                    val col = if (i % 2 == 0) Color(0xFFFBBF24) else Color(0xFFF9A8D4)
                    drawCircle(col.copy(alpha = sparkle.coerceIn(0f, 1f)), radius = (2.5f + 3f * sparkle) * density, center = pos)
                }
            }
        }
        Text(icon, fontSize = 88.sp, modifier = Modifier.graphicsLayer {
            rotationZ = sin(phase) * 28f * amp
            translationX = cos(phase) * 14f * amp * density
            val s = 1f + 0.12f * burst; scaleX = s; scaleY = s
        })
    }
}
