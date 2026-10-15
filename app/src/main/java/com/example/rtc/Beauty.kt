package com.example.rtc

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.dialogNavPadding
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.BeautyOptions
import io.agora.rtc2.video.ColorEnhanceOptions
import io.agora.rtc2.video.LowLightEnhanceOptions

/**
 * Face beauty for the camera (Agora clear-vision extension): smooth skin, brighten, rosy cheeks, sharpen,
 * vivid colour and low-light boost. Applied to the local camera, so viewers see the edited picture.
 * Values are remembered on the phone and re-applied every time the camera starts.
 */
object Beauty {
    var on by mutableStateOf(true)
    var smooth by mutableFloatStateOf(0.5f)
    var lighten by mutableFloatStateOf(0.4f)
    var redness by mutableFloatStateOf(0.2f)
    var sharpen by mutableFloatStateOf(0.3f)
    var vivid by mutableStateOf(false)
    var lowLight by mutableStateOf(false)
    private var loaded = false

    val PRESETS = listOf(
        Triple("ธรรมชาติ", "🌿", floatArrayOf(0.35f, 0.25f, 0.1f, 0.3f)),
        Triple("ผิวใส", "✨", floatArrayOf(0.6f, 0.55f, 0.15f, 0.3f)),
        Triple("หวานละมุน", "🌸", floatArrayOf(0.7f, 0.45f, 0.45f, 0.2f)),
        Triple("คมชัด", "💎", floatArrayOf(0.3f, 0.3f, 0.1f, 0.7f)),
    )

    fun load(ctx: Context) {
        if (loaded) return; loaded = true
        val p = ctx.getSharedPreferences("friendtalk", 0)
        on = p.getBoolean("bty_on", true); smooth = p.getFloat("bty_s", smooth); lighten = p.getFloat("bty_l", lighten)
        redness = p.getFloat("bty_r", redness); sharpen = p.getFloat("bty_h", sharpen); vivid = p.getBoolean("bty_v", false); lowLight = p.getBoolean("bty_ll", false)
    }
    fun save(ctx: Context) {
        ctx.getSharedPreferences("friendtalk", 0).edit().putBoolean("bty_on", on).putFloat("bty_s", smooth).putFloat("bty_l", lighten)
            .putFloat("bty_r", redness).putFloat("bty_h", sharpen).putBoolean("bty_v", vivid).putBoolean("bty_ll", lowLight).apply()
    }

    /** Push the current settings to the engine (no-op without an engine). */
    fun apply(e: RtcEngine? = Rtc.engine) {
        val engine = e ?: return
        runCatching { engine.enableExtension("agora_video_filters_clear_vision", "clear_vision", true) }
        runCatching { engine.setBeautyEffectOptions(on, BeautyOptions(BeautyOptions.LIGHTENING_CONTRAST_NORMAL, lighten, smooth, redness, sharpen)) }
        runCatching { engine.setColorEnhanceOptions(on && vivid, ColorEnhanceOptions(0.5f, 1f)) }
        runCatching { engine.setLowlightEnhanceOptions(on && lowLight, LowLightEnhanceOptions(LowLightEnhanceOptions.LOW_LIGHT_ENHANCE_AUTO, LowLightEnhanceOptions.LOW_LIGHT_ENHANCE_LEVEL_FAST)) }
    }
}

private val BNeon = Color(0xFFFF2FA8)
private val BViolet = Color(0xFF9B3DFF)

/** Bottom sheet over the live preview: presets + sliders, changes show on the camera immediately. */
@Composable
fun BeautySheet(onClose: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Beauty.load(ctx) }
    fun changed() { Beauty.apply(); Beauty.save(ctx) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onClose)) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xE6120A2A)).clickable(enabled = false) {}.dialogNavPadding().navigationBarsPadding().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✨ แต่งหน้า", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(if (Beauty.on) "เปิด" else "ปิด", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Switch(checked = Beauty.on, onCheckedChange = { Beauty.on = it; changed() })
                }
                Text("ผู้ชมเห็นภาพที่แต่งแล้ว · ปรับแล้วเห็นผลทันที", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Beauty.PRESETS.forEach { (name, icon, v) ->
                        val active = Beauty.on && Beauty.smooth == v[0] && Beauty.lighten == v[1] && Beauty.redness == v[2] && Beauty.sharpen == v[3]
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                            Beauty.on = true; Beauty.smooth = v[0]; Beauty.lighten = v[1]; Beauty.redness = v[2]; Beauty.sharpen = v[3]; changed()
                        }) {
                            Box(Modifier.size(54.dp).clip(CircleShape)
                                .background(if (active) Brush.linearGradient(listOf(BNeon, BViolet)) else Brush.linearGradient(listOf(Color(0x22FFFFFF), Color(0x22FFFFFF))))
                                .border(if (active) 2.dp else 1.dp, if (active) Color.White else Color(0x33FFFFFF), CircleShape), contentAlignment = Alignment.Center) { Text(icon, fontSize = 24.sp) }
                            Spacer(Modifier.height(4.dp)); Text(name, color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                BeautySlider("ผิวเนียน", Beauty.smooth) { Beauty.smooth = it; changed() }
                BeautySlider("ผิวขาวกระจ่างใส", Beauty.lighten) { Beauty.lighten = it; changed() }
                BeautySlider("แก้มอมชมพู", Beauty.redness) { Beauty.redness = it; changed() }
                BeautySlider("ความคมชัด", Beauty.sharpen) { Beauty.sharpen = it; changed() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎨 สีสดขึ้น", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = Beauty.vivid, onCheckedChange = { Beauty.vivid = it; changed() })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🌙 ปรับแสงเมื่อที่มืด", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = Beauty.lowLight, onCheckedChange = { Beauty.lowLight = it; changed() })
                }
            }
        }
    }
}

@Composable
private fun BeautySlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(118.dp))
        Slider(value = value, onValueChange = onChange, enabled = Beauty.on, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = BNeon, inactiveTrackColor = Color(0x33FFFFFF)))
        Text("${(value * 100).toInt()}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.width(30.dp))
    }
}
