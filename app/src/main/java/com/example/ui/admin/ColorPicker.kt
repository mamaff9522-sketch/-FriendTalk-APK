package com.example.ui.admin

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.drawLayer
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
import com.example.model.parseHexColor
import com.example.ui.theme.*
import kotlinx.coroutines.launch

fun Color.toHex(withAlpha: Boolean = alpha < 0.999f): String {
    val a = (alpha * 255).toInt(); val r = (red * 255).toInt(); val g = (green * 255).toInt(); val b = (blue * 255).toInt()
    return if (withAlpha) String.format("#%02X%02X%02X%02X", a, r, g, b) else String.format("#%02X%02X%02X", r, g, b)
}
private val HEX_RE = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
private fun recent(ctx: Context) = ctx.getSharedPreferences("color_picker", 0).getString("recent", "")!!.split(",").filter { HEX_RE.matches(it) }
private fun pushRecent(ctx: Context, hex: String) {
    val l = (listOf(hex) + recent(ctx).filter { !it.equals(hex, true) }).take(12)
    ctx.getSharedPreferences("color_picker", 0).edit().putString("recent", l.joinToString(",")).apply()
}
private val SWATCHES = listOf("#FFFFFF", "#F7F4FA", "#FDF2F8", "#F5F3FF", "#EFF6FF", "#ECFDF5", "#FFFBEB", "#F1F5F9",
    "#EC4899", "#DB2777", "#A855F7", "#8B5CF6", "#6366F1", "#3B82F6", "#06B6D4", "#10B981", "#84CC16", "#F59E0B", "#F97316", "#EF4444",
    "#1F1B2E", "#334155", "#5B5670", "#94A3B8", "#0F172A", "#090D16", "#1E1B4B", "#3B0764", "#000000")

/** Full colour picker: SV square + hue + alpha, hex input with validation, recents, swatches, eyedropper (image / draft preview). */
@Composable
fun ColorPickerDialog(title: String, initialHex: String, previewContent: (@Composable () -> Unit)? = null, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val init = parseHexColor(initialHex, Color(0xFFEC4899))
    val hsv0 = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(init.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(hsv0[0]) }
    var sat by remember { mutableFloatStateOf(hsv0[1]) }
    var value by remember { mutableFloatStateOf(hsv0[2]) }
    var alpha by remember { mutableFloatStateOf(init.alpha) }
    val color = Color.hsv(hue, sat, value, alpha)
    var hexText by remember { mutableStateOf(init.toHex()) }
    var hexErr by remember { mutableStateOf(false) }
    var dropper by remember { mutableStateOf<ImageBitmap?>(null) }
    var pickPreview by remember { mutableStateOf(false) }
    fun setColor(c: Color) {
        val h = FloatArray(3); android.graphics.Color.colorToHSV(c.copy(alpha = 1f).toArgb(), h)
        hue = h[0]; sat = h[1]; value = h[2]; alpha = c.alpha; hexText = c.toHex(); hexErr = false
    }
    LaunchedEffect(hue, sat, value, alpha) { if (!hexErr) hexText = color.toHex() }
    val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ctx.contentResolver.openInputStream(uri)?.use { s ->
            val o = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeStream(s, null, o)?.let { dropper = it.asImageBitmap() }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(20.dp), color = Slate900, modifier = Modifier.fillMaxWidth(0.94f)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.size(width = 56.dp, height = 32.dp).clip(RoundedCornerShape(8.dp)).background(color).border(1.dp, Slate700, RoundedCornerShape(8.dp)))
                }
                // Saturation/Value square
                var sq by remember { mutableStateOf(IntSize(1, 1)) }
                fun svAt(o: Offset) { sat = (o.x / sq.width).coerceIn(0f, 1f); value = 1f - (o.y / sq.height).coerceIn(0f, 1f); hexErr = false }
                Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).onSizeChanged { sq = it }
                    .pointerInput(Unit) { detectTapGestures { svAt(it) } }.pointerInput(Unit) { detectDragGestures { ch, _ -> ch.consume(); svAt(ch.position) } }) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        drawCircle(Color.White, 10.dp.toPx(), Offset(sat * size.width, (1 - value) * size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                    }
                }
                // Hue slider
                var hw by remember { mutableIntStateOf(1) }
                Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(13.dp)).onSizeChanged { hw = it.width }
                    .pointerInput(Unit) { detectTapGestures { hue = (it.x / hw * 360f).coerceIn(0f, 359.9f) } }
                    .pointerInput(Unit) { detectDragGestures { ch, _ -> ch.consume(); hue = (ch.position.x / hw * 360f).coerceIn(0f, 359.9f) } }) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }))
                        drawCircle(Color.White, size.height / 2.4f, Offset(hue / 360f * size.width, size.height / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ความทึบ ${(alpha * 100).toInt()}%", color = Slate300, fontSize = 12.sp, modifier = Modifier.width(96.dp))
                    Slider(value = alpha, onValueChange = { alpha = it; hexErr = false }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = hexText, onValueChange = { t ->
                    hexText = t.trim().uppercase().let { if (it.startsWith("#")) it else "#$it" }.take(9)
                    hexErr = !HEX_RE.matches(hexText); if (!hexErr) setColor(parseHexColor(hexText, color))
                }, label = { Text("HEX (#RRGGBB หรือ #AARRGGBB)") }, isError = hexErr, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { if (hexErr) Text("รูปแบบไม่ถูกต้อง") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                Text("ตัวดูดสี", color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { imgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("🖼 จากรูปภาพ", color = White, fontSize = 12.sp) }
                    if (previewContent != null) OutlinedButton(onClick = { pickPreview = true }) { Text("📱 จากหน้าตัวอย่าง", color = White, fontSize = 12.sp) }
                }
                val rec = remember { recent(ctx) }
                if (rec.isNotEmpty()) { Text("ล่าสุด", color = Slate400, fontSize = 12.sp); SwatchRow(rec) { setColor(parseHexColor(it, color)) } }
                Text("ชุดสี", color = Slate400, fontSize = 12.sp)
                SWATCHES.chunked(10).forEach { SwatchRow(it) { h -> setColor(parseHexColor(h, color)) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("ยกเลิก") }
                    Button(enabled = !hexErr, onClick = { val h = color.toHex(); pushRecent(ctx, h); onPick(h) }, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("ใช้สีนี้", color = PureWhite) }
                }
            }
        }
    }
    dropper?.let { bmp -> EyedropperDialog(bmp, onPick = { setColor(it); dropper = null }, onDismiss = { dropper = null }) }
    if (pickPreview && previewContent != null) PreviewCapture(previewContent, onCaptured = { dropper = it; pickPreview = false }, onDismiss = { pickPreview = false })
}

@Composable
private fun SwatchRow(hexes: List<String>, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        hexes.forEach { h -> Box(Modifier.size(26.dp).clip(CircleShape).background(parseHexColor(h, Color.Gray)).border(1.dp, Slate700, CircleShape).clickable { onPick(h) }) }
    }
}

/** Renders the draft preview into a GraphicsLayer and hands back a bitmap for sampling. */
@Composable
private fun PreviewCapture(content: @Composable () -> Unit, onCaptured: (ImageBitmap) -> Unit, onDismiss: () -> Unit) {
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("กด 'จับภาพ' แล้วแตะจุดที่ต้องการดูดสี", color = PureWhite, modifier = Modifier.weight(1f))
                Button(onClick = { scope.launch { onCaptured(layer.toImageBitmap()) } }) { Text("จับภาพ") }
            }
            Box(Modifier.weight(1f).fillMaxWidth().drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) { content() }
        }
    }
}

/** Tap/drag a loupe over the image to sample the exact pixel. */
@Composable
private fun EyedropperDialog(bmp: ImageBitmap, onPick: (Color) -> Unit, onDismiss: () -> Unit) {
    val pixels = remember(bmp) { bmp.asAndroidBitmap() }
    var box by remember { mutableStateOf(IntSize(1, 1)) }
    var pos by remember { mutableStateOf<Offset?>(null) }
    var picked by remember { mutableStateOf<Color?>(null) }
    fun sample(o: Offset) {
        pos = o
        // image is drawn with ContentScale.Fit: map box coords -> bitmap coords
        val sc = minOf(box.width.toFloat() / pixels.width, box.height.toFloat() / pixels.height)
        val ox = (box.width - pixels.width * sc) / 2; val oy = (box.height - pixels.height * sc) / 2
        val x = ((o.x - ox) / sc).toInt(); val y = ((o.y - oy) / sc).toInt()
        if (x in 0 until pixels.width && y in 0 until pixels.height) picked = Color(pixels.getPixel(x, y))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(picked ?: Color.Transparent).border(2.dp, PureWhite, CircleShape))
                Text(picked?.toHex() ?: "แตะหรือลากบนรูป", color = PureWhite, modifier = Modifier.padding(start = 10.dp).weight(1f))
                TextButton(onClick = onDismiss) { Text("ยกเลิก", color = PureWhite) }
                Button(enabled = picked != null, onClick = { picked?.let(onPick) }) { Text("ใช้สีนี้") }
            }
            Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { box = it }
                .pointerInput(Unit) { detectTapGestures { sample(it) } }
                .pointerInput(Unit) { detectDragGestures { ch, _ -> ch.consume(); sample(ch.position) } }) {
                Image(bmp, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                val p = pos; val c = picked
                if (p != null && c != null) Canvas(Modifier.fillMaxSize()) {
                    val loupe = Offset(p.x, (p.y - 70.dp.toPx()).coerceAtLeast(40.dp.toPx()))
                    drawCircle(c, 34.dp.toPx(), loupe)
                    drawCircle(Color.White, 34.dp.toPx(), loupe, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                    drawCircle(Color.White, 4.dp.toPx(), p, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
            }
        }
    }
}
