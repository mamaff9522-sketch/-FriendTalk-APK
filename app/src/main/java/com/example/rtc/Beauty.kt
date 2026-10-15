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
import io.agora.rtc2.video.FaceShapeAreaOptions
import io.agora.rtc2.video.FaceShapeBeautyOptions
import io.agora.rtc2.video.FilterEffectOptions
import io.agora.rtc2.video.LowLightEnhanceOptions

/**
 * Face beauty for the camera (Agora clear-vision extension), TikTok-style:
 *  - ฟิลเตอร์: colour looks (our own .cube LUTs in assets/filters) with a strength slider
 *  - ใบหน้า: face-shape style (female / male / natural) + per-area tweaks (slim face, big eyes, small nose, chin, …)
 *  - ผิว: smooth skin, brighten, rosy cheeks, sharpen, vivid colour, low-light boost
 * Applied to my camera, so viewers see the edited picture. Remembered on the phone and re-applied when the camera starts.
 */
object Beauty {
    var on by mutableStateOf(true)
    var smooth by mutableFloatStateOf(0.5f)
    var lighten by mutableFloatStateOf(0.4f)
    var redness by mutableFloatStateOf(0.2f)
    var sharpen by mutableFloatStateOf(0.3f)
    var vivid by mutableStateOf(false)
    var lowLight by mutableStateOf(false)
    var filter by mutableStateOf("none")
    var filterStrength by mutableFloatStateOf(0.7f)
    var shapeStyle by mutableIntStateOf(-1) // -1 off, else FaceShapeBeautyOptions.FACE_SHAPE_BEAUTY_STYLE_*
    var shapeIntensity by mutableFloatStateOf(0.5f)
    /** face area -> -100..100 (0 = unchanged) */
    val areas = mutableStateMapOf<Int, Int>()
    private var loaded = false
    private var appCtx: Context? = null

    val PRESETS = listOf(
        Triple("ธรรมชาติ", "🌿", floatArrayOf(0.35f, 0.25f, 0.1f, 0.3f)),
        Triple("ผิวใส", "✨", floatArrayOf(0.6f, 0.55f, 0.15f, 0.3f)),
        Triple("หวานละมุน", "🌸", floatArrayOf(0.7f, 0.45f, 0.45f, 0.2f)),
        Triple("คมชัด", "💎", floatArrayOf(0.3f, 0.3f, 0.1f, 0.7f)),
    )
    /** id, label, two swatch colours */
    val FILTERS = listOf(
        Triple("none", "ไม่มี", listOf(Color(0xFF3A3550), Color(0xFF3A3550))),
        Triple("clear", "ผิวใส", listOf(Color(0xFFFFF1F4), Color(0xFFFFD7E2))),
        Triple("pink", "ชมพูหวาน", listOf(Color(0xFFFFB6D1), Color(0xFFFF6FA8))),
        Triple("warm", "อบอุ่น", listOf(Color(0xFFFFD39A), Color(0xFFFF9A5A))),
        Triple("cool", "เกาหลี", listOf(Color(0xFFD6ECFF), Color(0xFF8EC5FF))),
        Triple("vivid", "สดใส", listOf(Color(0xFF6CFFB0), Color(0xFFFF4FD8))),
        Triple("vintage", "วินเทจ", listOf(Color(0xFFD9C3A0), Color(0xFF9C7F63))),
        Triple("cinema", "ซีเนม่า", listOf(Color(0xFF1F8A9C), Color(0xFFFF9E57))),
        Triple("mono", "ขาวดำ", listOf(Color(0xFFFFFFFF), Color(0xFF222222))),
    )
    val STYLES = listOf(-1 to "ปิด", FaceShapeBeautyOptions.FACE_SHAPE_BEAUTY_STYLE_FEMALE to "หวาน",
        FaceShapeBeautyOptions.FACE_SHAPE_BEAUTY_STYLE_MALE to "หล่อ", FaceShapeBeautyOptions.FACE_SHAPE_BEAUTY_STYLE_NATURAL to "ธรรมชาติ")
    /** area, label, icon */
    val AREAS = listOf(
        Triple(FaceShapeAreaOptions.FACE_SHAPE_AREA_FACECONTOUR, "หน้าเรียว", "🙂"),
        Triple(FaceShapeAreaOptions.FACE_SHAPE_AREA_FACEWIDTH, "หน้าแคบ", "↔️"),
        Triple(FaceShapeAreaOptions.FACE_SHAPE_AREA_CHEEKBONE, "โหนกแก้ม", "💫"),
        Triple(FaceShapeAreaOptions.FACE_SHAPE_AREA_CHIN, "คาง", "🔻"),
        Triple(FaceShapeAreaOptions.FACE_SHAPE_AREA_EYESCALE, "ตาโต", "👁️"),
        Triple(FaceShapeAreaOptions.FACE_SHAPE_AREA_NOSEWIDTH, "จมูก", "👃"),
        Triple(FaceShapeAreaOptions.FACE_SHAPE_AREA_MOUTHSCALE, "ปาก", "👄"),
        Triple(FaceShapeAreaOptions.FACE_SHAPE_AREA_HEADSCALE, "หัวเล็ก", "🫧"),
    )

    fun load(ctx: Context) {
        appCtx = ctx.applicationContext
        if (loaded) return; loaded = true
        val p = ctx.getSharedPreferences("friendtalk", 0)
        on = p.getBoolean("bty_on", true); smooth = p.getFloat("bty_s", smooth); lighten = p.getFloat("bty_l", lighten)
        redness = p.getFloat("bty_r", redness); sharpen = p.getFloat("bty_h", sharpen); vivid = p.getBoolean("bty_v", false); lowLight = p.getBoolean("bty_ll", false)
        filter = p.getString("bty_f", "none") ?: "none"; filterStrength = p.getFloat("bty_fs", 0.7f)
        shapeStyle = p.getInt("bty_ss", -1); shapeIntensity = p.getFloat("bty_si", 0.5f)
        AREAS.forEach { (a, _, _) -> p.getInt("bty_a$a", 0).let { if (it != 0) areas[a] = it } }
    }
    fun save(ctx: Context) {
        ctx.getSharedPreferences("friendtalk", 0).edit().putBoolean("bty_on", on).putFloat("bty_s", smooth).putFloat("bty_l", lighten)
            .putFloat("bty_r", redness).putFloat("bty_h", sharpen).putBoolean("bty_v", vivid).putBoolean("bty_ll", lowLight)
            .putString("bty_f", filter).putFloat("bty_fs", filterStrength).putInt("bty_ss", shapeStyle).putFloat("bty_si", shapeIntensity)
            .apply { AREAS.forEach { (a, _, _) -> putInt("bty_a$a", areas[a] ?: 0) } }.apply()
    }

    /** LUT file from assets copied once to app storage (the SDK needs a real file path). */
    private fun filterPath(id: String): String? {
        val ctx = appCtx ?: return null
        val f = java.io.File(ctx.filesDir, "filters/$id.cube")
        if (!f.exists()) runCatching { f.parentFile?.mkdirs(); ctx.assets.open("filters/$id.cube").use { i -> f.outputStream().use { i.copyTo(it) } } }
        return f.takeIf { it.exists() }?.absolutePath
    }

    /** Push the current settings to the engine (no-op without an engine). */
    fun apply(e: RtcEngine? = Rtc.engine) {
        val engine = e ?: return
        runCatching { engine.enableExtension("agora_video_filters_clear_vision", "clear_vision", true) }
        runCatching { engine.setBeautyEffectOptions(on, BeautyOptions(BeautyOptions.LIGHTENING_CONTRAST_NORMAL, lighten, smooth, redness, sharpen)) }
        runCatching { engine.setColorEnhanceOptions(on && vivid, ColorEnhanceOptions(0.5f, 1f)) }
        runCatching { engine.setLowlightEnhanceOptions(on && lowLight, LowLightEnhanceOptions(LowLightEnhanceOptions.LOW_LIGHT_ENHANCE_AUTO, LowLightEnhanceOptions.LOW_LIGHT_ENHANCE_LEVEL_FAST)) }
        val shape = on && (shapeStyle >= 0 || areas.values.any { it != 0 })
        runCatching { engine.setFaceShapeBeautyOptions(shape, FaceShapeBeautyOptions(shapeStyle.coerceAtLeast(0), if (shapeStyle >= 0) (shapeIntensity * 100).toInt() else 0)) }
        if (shape) AREAS.forEach { (a, _, _) -> runCatching { engine.setFaceShapeAreaOptions(FaceShapeAreaOptions(a, areas[a] ?: 0)) } }
        val path = if (on && filter != "none") filterPath(filter) else null
        runCatching { engine.setFilterEffectOptions(path != null, FilterEffectOptions(path ?: "", filterStrength)) }
    }
}

private val BNeon = Color(0xFFFF2FA8)
private val BViolet = Color(0xFF9B3DFF)

/** TikTok-style bottom sheet over the live preview: ฟิลเตอร์ · ใบหน้า · ผิว. Every change shows on the camera immediately. */
@Composable
fun BeautySheet(onClose: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Beauty.load(ctx) }
    fun changed() { Beauty.apply(); Beauty.save(ctx) }
    var tab by remember { mutableIntStateOf(0) }
    var area by remember { mutableIntStateOf(Beauty.AREAS.first().first) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onClose)) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xE6120A2A)).clickable(enabled = false) {}.dialogNavPadding().navigationBarsPadding().padding(16.dp)) {
                // slider for whatever is selected (like TikTok: on top of the list)
                when (tab) {
                    0 -> if (Beauty.filter != "none") BeautySlider("ความเข้ม", Beauty.filterStrength) { Beauty.filterStrength = it; changed() }
                    1 -> if (Beauty.shapeStyle >= 0) BeautySlider("ปรับรูปหน้า", Beauty.shapeIntensity) { Beauty.shapeIntensity = it; changed() }
                    else -> {}
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf("ฟิลเตอร์", "ใบหน้า", "ผิว").forEachIndexed { i, t ->
                        Column(Modifier.clickable { tab = i }.padding(end = 18.dp, top = 6.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t, color = if (tab == i) Color.White else Color.White.copy(alpha = 0.5f), fontSize = 16.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal)
                            Box(Modifier.padding(top = 4.dp).width(28.dp).height(2.dp).background(if (tab == i) Color.White else Color.Transparent))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("↺ รีเซ็ต", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.clickable {
                        when (tab) {
                            0 -> { Beauty.filter = "none"; Beauty.filterStrength = 0.7f }
                            1 -> { Beauty.shapeStyle = -1; Beauty.shapeIntensity = 0.5f; Beauty.areas.clear() }
                            else -> { Beauty.smooth = 0.5f; Beauty.lighten = 0.4f; Beauty.redness = 0.2f; Beauty.sharpen = 0.3f; Beauty.vivid = false; Beauty.lowLight = false }
                        }
                        Beauty.on = true; changed()
                    }.padding(6.dp))
                    Switch(checked = Beauty.on, onCheckedChange = { Beauty.on = it; changed() })
                }
                Text("ผู้ชมเห็นภาพที่แต่งแล้ว · ปรับแล้วเห็นผลทันที", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                when (tab) {
                    0 -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Beauty.FILTERS.forEach { (id, name, cols) ->
                            BeautyChip(name, Beauty.on && Beauty.filter == id, Brush.linearGradient(cols)) {
                                Beauty.on = true; Beauty.filter = id; changed()
                            }
                        }
                    }
                    1 -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Beauty.STYLES.forEach { (st, name) ->
                                val active = Beauty.shapeStyle == st
                                Text(name, color = Color.White, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (active) Brush.horizontalGradient(listOf(BNeon, BViolet)) else Brush.horizontalGradient(listOf(Color(0x22FFFFFF), Color(0x22FFFFFF))))
                                        .clickable { Beauty.on = true; Beauty.shapeStyle = st; changed() }.padding(horizontal = 14.dp, vertical = 7.dp))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Beauty.AREAS.forEach { (a, name, icon) ->
                                val v = Beauty.areas[a] ?: 0
                                BeautyChip(if (v != 0) "$name ${if (v > 0) "+" else ""}$v" else name, area == a, null, icon) { area = a }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val cur = Beauty.areas[area] ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Beauty.AREAS.first { it.first == area }.second, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(80.dp))
                            Slider(value = cur.toFloat(), onValueChange = { Beauty.on = true; if (it.toInt() == 0) Beauty.areas.remove(area) else Beauty.areas[area] = it.toInt(); changed() },
                                valueRange = -100f..100f, modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = BNeon, inactiveTrackColor = Color(0x33FFFFFF)))
                            Text("$cur", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.width(34.dp))
                        }
                    }
                    else -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Beauty.PRESETS.forEach { (name, icon, v) ->
                                val active = Beauty.on && Beauty.smooth == v[0] && Beauty.lighten == v[1] && Beauty.redness == v[2] && Beauty.sharpen == v[3]
                                BeautyChip(name, active, null, icon) {
                                    Beauty.on = true; Beauty.smooth = v[0]; Beauty.lighten = v[1]; Beauty.redness = v[2]; Beauty.sharpen = v[3]; changed()
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
    }
}

/** Round item with label (TikTok filter row). [swatch] = colour preview, else [icon]. */
@Composable
private fun BeautyChip(name: String, active: Boolean, swatch: Brush?, icon: String = "", onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(56.dp).clip(CircleShape)
            .background(swatch ?: if (active) Brush.linearGradient(listOf(BNeon, BViolet)) else Brush.linearGradient(listOf(Color(0x22FFFFFF), Color(0x22FFFFFF))))
            .border(if (active) 2.5.dp else 1.dp, if (active) BNeon else Color(0x33FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
            if (icon.isNotBlank()) Text(icon, fontSize = 24.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(name, color = if (active) BNeon else Color.White, fontSize = 11.sp, maxLines = 1, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
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
