package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.BrainApi
import com.example.ui.social.DecorOptions
import com.example.ui.social.hexColor
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val PALETTE = listOf("#FF2FA8", "#9B3DFF", "#6D5BFF", "#1C7ED6", "#00E5FF", "#2FBF9A", "#10B981", "#FFD700",
    "#F59E0B", "#FF7A45", "#EF4444", "#E8A598", "#F49AC1", "#FFFFFF", "#1E2A5A", "#0B0824", "#3B2A06", "#000000")

/**
 * Admin: add room-decor themes, frames and effects without an app update (appConfig/decorOptions via PUT /admin/decor-options).
 * Built-in options stay in the app; these are appended for VIP users. Turning one off hides it from the editor (rooms already using it keep rendering).
 */
@Composable
fun AdminDecorOptionsPane() {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var status by remember { mutableStateOf("") }
    // picture for the new option (sent before the option itself) / for an existing option
    var newImg by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }
    var newImgUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var imgFor by remember { mutableStateOf<String?>(null) }
    fun readImg(uri: android.net.Uri): Pair<ByteArray, String>? {
        val b = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: run { status = "อ่านรูปไม่ได้"; return null }
        if (b.size > 2 * 1024 * 1024) { status = "รูปต้องไม่เกิน 2MB (ตอนนี้ ${b.size / 1024}KB)"; return null }
        return b to (ctx.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/png")
    }
    suspend fun uploadImg(k: String, id: String, img: Pair<ByteArray, String>): Boolean {
        val u = FirebaseAuth.getInstance().currentUser ?: return false
        val r = BrainApi.callRaw(u, "PUT", "/admin/decor-options/$k/$id/image", img.first, img.second)
        if (r.code != 200) status = r.json?.optString("error")?.ifBlank { null } ?: "อัปโหลดรูปไม่สำเร็จ (${r.code})"
        return r.code == 200
    }
    val newPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) readImg(uri)?.let { newImg = it; newImgUri = uri; status = "" }
    }
    LaunchedEffect(Unit) { DecorOptions.start() }
    var kind by remember { mutableStateOf("themes") }
    var name by remember { mutableStateOf("") }
    var colors by remember { mutableStateOf(listOf<String>()) }
    var glyphs by remember { mutableStateOf("") }
    val rowPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        val id = imgFor; imgFor = null
        if (uri == null || id == null) return@rememberLauncherForActivityResult
        val img = readImg(uri) ?: return@rememberLauncherForActivityResult
        status = "กำลังอัปโหลดรูป…"; val k = kind
        scope.launch { if (uploadImg(k, id, img)) status = "ใส่รูปแล้ว ✅ ผู้ใช้เห็นทันที" }
    }

    fun optsOf(k: String) = when (k) { "themes" -> DecorOptions.themes; "frames" -> DecorOptions.frames; else -> DecorOptions.effects }
    fun save(next: Map<String, List<DecorOptions.Opt>>) {
        status = "กำลังบันทึก…"
        scope.launch {
            val body = JSONObject()
            for (k in listOf("themes", "frames", "effects")) {
                val o = JSONObject()
                (next[k] ?: optsOf(k)).forEach { opt ->
                    val j = JSONObject().put("name", opt.name).put("enabled", opt.enabled)
                    if (k == "effects") j.put("glyphs", JSONArray(opt.glyphs)) else j.put("colors", JSONArray(opt.hex))
                    o.put(opt.id, j)
                }
                body.put(k, o)
            }
            val u = FirebaseAuth.getInstance().currentUser
            val r = if (u == null) null else BrainApi.call(u, "PUT", "/admin/decor-options", body)
            status = when {
                r == null -> "ยังไม่ได้เข้าสู่ระบบ"
                r.code == 200 -> "บันทึกแล้ว — VIP เห็นตัวเลือกใหม่ทันที"
                r.code == 404 -> "เซิร์ฟเวอร์ยังไม่อัปเดต (404) — deploy friendtalk-brain ก่อน"
                else -> r.json?.optString("error")?.ifBlank { null } ?: "ไม่สำเร็จ (${r.code})"
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🎨 ตัวเลือกตกแต่งห้อง VIP", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text("เพิ่มธีม กรอบ และเอฟเฟกต์ใหม่ได้เองโดยไม่ต้องอัปเดตแอป · สติกเกอร์เพิ่มได้ที่แท็บ 'ของขวัญ & สติกเกอร์' หมวดสติกเกอร์ห้อง", color = Slate400, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("themes" to "ธีม", "frames" to "กรอบ", "effects" to "เอฟเฟกต์").forEach { (k, l) ->
                FilterChip(selected = kind == k, onClick = { kind = k; colors = emptyList(); glyphs = ""; name = ""; newImg = null; newImgUri = null }, label = { Text(l) })
            }
        }

        // existing
        val cur = optsOf(kind)
        if (cur.isEmpty()) Text("ยังไม่มีรายการที่เพิ่มเอง", color = Slate400, fontSize = 12.sp)
        cur.forEach { o ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Slate900).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Preview(kind, o.colors, o.glyphs, o.imageUrl)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(o.name, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (o.imageUrl.isBlank()) "🖼 ใส่รูป" else "🖼 เปลี่ยนรูป", color = Pink400, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { imgFor = o.id; rowPicker.launch("image/*") })
                        if (o.imageUrl.isNotBlank()) Text("เอารูปออก", color = Slate400, fontSize = 12.sp, modifier = Modifier.clickable {
                            if (o.colors.size < 2 && kind != "effects" || kind == "effects" && o.glyphs.isEmpty()) status = "รายการนี้ไม่มีสี/อีโมจิสำรอง ใส่รูปใหม่แทน หรือลบรายการ"
                            else scope.launch { FirebaseAuth.getInstance().currentUser?.let { u -> BrainApi.call(u, "DELETE", "/admin/decor-options/$kind/${o.id}/image") }; status = "เอารูปออกแล้ว" }
                        })
                    }
                }
                Switch(checked = o.enabled, onCheckedChange = { on -> save(mapOf(kind to cur.map { if (it.id == o.id) it.copy(enabled = on) else it })) })
                IconButton(onClick = { save(mapOf(kind to cur.filter { it.id != o.id })) }) { Icon(Icons.Filled.Delete, "ลบ", tint = Rose500) }
            }
        }

        // add new
        HorizontalDivider(color = Slate800, modifier = Modifier.padding(vertical = 6.dp))
        Text("เพิ่ม" + when (kind) { "themes" -> "ธีม"; "frames" -> "กรอบ"; else -> "เอฟเฟกต์" } + "ใหม่", color = White, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it.take(30) }, label = { Text("ชื่อที่ผู้ใช้เห็น") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
        Text(when (kind) { "themes" -> "รูปพื้นหลังห้อง"; "frames" -> "รูปกรอบ (PNG โปร่งใสตรงกลาง)"; else -> "รูปเอฟเฟกต์ (GIF / WebP เคลื่อนไหวได้)" } + " · ไม่เกิน 2MB · ใส่รูปแล้วไม่ต้องเลือกสี/อีโมจิ",
            color = Slate400, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { newPicker.launch("image/*") }) { Text(if (newImg == null) "🖼 อัปโหลดรูป" else "🖼 เปลี่ยนรูป", color = White) }
            if (newImg != null) TextButton(onClick = { newImg = null; newImgUri = null }) { Text("เอาออก", color = Pink400) }
        }
        if (kind == "effects") {
            OutlinedTextField(glyphs, { glyphs = it.take(20) }, label = { Text("อีโมจิที่ลอย (1–3 ตัว เว้นวรรค) เช่น 🍀 🌼") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
        } else {
            Text("แตะสีเพื่อเลือก 2–3 สี (เรียงจากบนลงล่าง) · แตะสีที่เลือกแล้วเพื่อเอาออก", color = Slate400, fontSize = 11.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PALETTE.forEach { c ->
                    val on = c in colors
                    Box(Modifier.size(32.dp).clip(CircleShape).border(if (on) 3.dp else 1.dp, if (on) Amber400 else Slate700, CircleShape).padding(3.dp).clip(CircleShape)
                        .background(hexColor(c)).clickable { colors = if (on) colors - c else if (colors.size < 3) colors + c else colors })
                }
            }
        }
        val gl = glyphs.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(3)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ตัวอย่าง: ", color = Slate400, fontSize = 12.sp)
            Preview(kind, colors.map { hexColor(it) }, gl, newImgUri?.toString().orEmpty())
        }
        Button(onClick = {
            val n = name.trim()
            when {
                n.isBlank() -> status = "ใส่ชื่อก่อน"
                newImg == null && kind != "effects" && colors.size < 2 -> status = "เลือกอย่างน้อย 2 สี หรืออัปโหลดรูป"
                newImg == null && kind == "effects" && gl.isEmpty() -> status = "ใส่อีโมจิอย่างน้อย 1 ตัว หรืออัปโหลดรูป"
                else -> {
                    val id = "c_" + System.currentTimeMillis().toString(36)
                    val opt = DecorOptions.Opt(id, n, glyphs = gl, hex = colors)
                    val img = newImg; val k = kind
                    if (img != null) { status = "กำลังอัปโหลดรูป…"; scope.launch { if (uploadImg(k, id, img)) save(mapOf(k to optsOf(k) + opt)) } } // picture first, then the option
                    else save(mapOf(kind to optsOf(kind) + opt))
                    name = ""; colors = emptyList(); glyphs = ""; newImg = null; newImgUri = null
                }
            }
        }, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("เพิ่มและบันทึก") }
        if (status.isNotBlank()) Text(status, color = Amber400, fontSize = 12.sp)
    }
}

@Composable
private fun Preview(kind: String, colors: List<Color>, glyphs: List<String>, imageUrl: String = "") {
    val shape = RoundedCornerShape(10.dp)
    if (imageUrl.isNotBlank()) {
        Box(Modifier.size(width = 40.dp, height = 56.dp).clip(shape).background(Slate950)) {
            coil.compose.AsyncImage(model = imageUrl, imageLoader = com.example.ui.social.rememberGifLoader(), contentDescription = null,
                contentScale = if (kind == "frames") androidx.compose.ui.layout.ContentScale.FillBounds else androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        return
    }
    when (kind) {
        "themes" -> Box(Modifier.size(width = 40.dp, height = 56.dp).clip(shape)
            .background(if (colors.size >= 2) Brush.verticalGradient(colors) else Brush.verticalGradient(listOf(Slate800, Slate900))))
        "frames" -> Box(Modifier.size(width = 40.dp, height = 56.dp).clip(shape).background(Slate950)
            .border(3.dp, if (colors.size >= 2) Brush.linearGradient(colors) else Brush.linearGradient(listOf(Slate700, Slate700)), shape))
        else -> Box(Modifier.size(width = 40.dp, height = 56.dp).clip(shape).background(Slate950), contentAlignment = Alignment.Center) {
            Text(glyphs.joinToString(" ").ifBlank { "✨" }, fontSize = 14.sp)
        }
    }
}
