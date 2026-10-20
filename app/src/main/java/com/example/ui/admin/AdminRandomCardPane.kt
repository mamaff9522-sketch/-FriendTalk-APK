package com.example.ui.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.BrainApi
import com.example.ui.random.RandomCardRepo
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** One editable item of the สุ่มหาเพื่อน card catalog. [style] = colours "#RRGGBB,#RRGGBB" (frames) or emojis "💗,💖" (effects). */
private class RcRow(val id: String, name: String, price: String, enabled: Boolean, style: String, shape: String = "", imageUrl: String = "") {
    var name by mutableStateOf(name); var price by mutableStateOf(price); var enabled by mutableStateOf(enabled); var style by mutableStateOf(style)
    /** name frames: shape (pill, neon, …) · effects: movement (rise, fall, …) */
    var shape by mutableStateOf(shape)
    /** admin picture: saved URL, a new file waiting for บันทึก, or removal */
    var imageUrl by mutableStateOf(imageUrl)
    var newImg by mutableStateOf<Pair<ByteArray, String>?>(null)
    var newImgUri by mutableStateOf<android.net.Uri?>(null)
    var dropImage by mutableStateOf(false)
    val shownImage get() = newImgUri?.toString() ?: if (dropImage) "" else imageUrl
}
private val RC_SHAPES = listOf("pill" to "แคปซูล", "neon" to "นีออน", "ribbon" to "ริบบิ้น", "bubble" to "บับเบิล", "badge" to "เหรียญตรา", "glitter" to "วิบวับ",
    "outline" to "เส้นคู่", "royal" to "ราชา", "cloud" to "ก้อนเมฆ", "tag" to "ป้ายแท็ก")
private val RC_MOVES = listOf("rise" to "ลอยขึ้น", "fall" to "โปรยลง", "orbit" to "โคจร", "twinkle" to "กะพริบ", "burst" to "ระเบิด", "sway" to "พลิ้ว",
    "spiral" to "หมุนวน", "rain" to "ฝนตก", "zigzag" to "ซิกแซก", "shoot" to "พุ่ง")

/**
 * Admin → ของขวัญ & ตกแต่ง → 'การ์ดสุ่ม': split into tabs (ราคาทั่วไป / กรอบรูป / กรอบชื่อ / เอฟเฟกต์). Each item: picture (optional,
 * replaces the colours / emojis), name, price, on/off, shape or movement. Saved with one button at the bottom
 * (PUT /admin/randomcard-catalog, then pictures) — users see it right away, no new APK. Diamonds are always checked and taken by the server.
 */
@Composable
fun AdminRandomCardPane() {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(true) }
    var videoPrice by remember { mutableStateOf("") }
    var audioPrice by remember { mutableStateOf("") }
    // prices for the user's own uploads (0 = free)
    val customPrices = remember { mutableStateMapOf("customFramePrice" to "0", "customNameFramePrice" to "0", "customEffectPrice" to "0") }
    val rows = remember { mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<RcRow>>() }
    var status by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("prices") }
    val kinds = listOf("frames" to "กรอบรูป", "nameFrames" to "กรอบชื่อ", "effects" to "เอฟเฟกต์")
    var imgRow by remember { mutableStateOf<RcRow?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val row = imgRow; imgRow = null
        if (uri == null || row == null) return@rememberLauncherForActivityResult
        val b = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        when {
            b == null -> status = "อ่านรูปไม่ได้"
            b.size > 2 * 1024 * 1024 -> status = "รูปต้องไม่เกิน 2MB (ตอนนี้ ${b.size / 1024}KB)"
            else -> { row.newImg = b to (ctx.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/png"); row.newImgUri = uri; row.dropImage = false; status = "กด 'บันทึก' ด้านล่างเพื่อใช้รูปนี้" }
        }
    }
    suspend fun load() {
        val u = FirebaseAuth.getInstance().currentUser ?: return
        val r = BrainApi.call(u, "GET", "/admin/randomcard-catalog"); val j = r.json
        if (r.code != 200 || j == null) { status = if (r.code == 404) "เซิร์ฟเวอร์ยังไม่อัปเดต" else "โหลดไม่สำเร็จ (${r.code})"; return }
        enabled = j.optBoolean("enabled", true); videoPrice = j.optInt("videoPrice").toString(); audioPrice = j.optInt("audioPrice").toString()
        customPrices.keys.toList().forEach { k -> customPrices[k] = j.optInt(k).toString() }
        kinds.forEach { (k, _) ->
            val o = j.optJSONObject(k) ?: JSONObject()
            rows[k] = mutableStateListOf<RcRow>().apply {
                o.keys().asSequence().map { it to o.getJSONObject(it) }.sortedBy { it.second.optInt("order") }.forEach { (id, v) ->
                    val st = (v.optJSONArray(if (k == "effects") "glyphs" else "colors") ?: JSONArray()).let { a -> (0 until a.length()).joinToString(",") { a.optString(it) } }
                    add(RcRow(id, v.optString("name", id), v.optInt("price").toString(), v.optBoolean("enabled", true), st,
                        when (k) { "effects" -> v.optString("motion", "rise"); "nameFrames" -> v.optString("style", "pill"); else -> "" }, v.optString("imageUrl"))) }
            }
        }
        loaded = true
    }
    LaunchedEffect(Unit) { load() }
    fun save() {
        saving = true; status = "กำลังบันทึก…"
        scope.launch {
            val body = JSONObject().put("enabled", enabled).put("videoPrice", videoPrice.toIntOrNull() ?: 0).put("audioPrice", audioPrice.toIntOrNull() ?: 0)
                .apply { customPrices.forEach { (k, v) -> put(k, v.toIntOrNull() ?: 0) } }
            kinds.forEach { (k, _) ->
                val o = JSONObject()
                rows[k].orEmpty().forEachIndexed { i, row ->
                    val parts = row.style.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    o.put(row.id, JSONObject().put("name", row.name).put("price", row.price.toIntOrNull() ?: 0).put("enabled", row.enabled).put("order", i)
                        .put(if (k == "effects") "glyphs" else "colors", JSONArray(parts))
                        .put("pendingImage", row.newImg != null).put("dropImage", row.dropImage && row.newImg == null)
                        .apply { if (k == "effects") put("motion", row.shape.ifBlank { "rise" }); if (k == "nameFrames") put("style", row.shape.ifBlank { "pill" }) })
                }
                body.put(k, o)
            }
            val u = FirebaseAuth.getInstance().currentUser
            val r = if (u == null) null else BrainApi.call(u, "PUT", "/admin/randomcard-catalog", body)
            if (r?.code != 200) { saving = false; status = r?.json?.optString("error")?.ifBlank { null } ?: "บันทึกไม่สำเร็จ (${r?.code})"; return@launch }
            // pictures after the items exist on the server
            var failed = ""
            kinds.forEach { (k, _) -> rows[k].orEmpty().forEach { row ->
                val img = row.newImg ?: return@forEach
                val up = BrainApi.callRaw(u!!, "PUT", "/admin/randomcard-catalog/$k/${row.id}/image", img.first, img.second)
                if (up.code != 200) failed = "${row.name}: " + (up.json?.optString("error")?.ifBlank { null } ?: "อัปโหลดรูปไม่สำเร็จ (${up.code})")
            } }
            saving = false
            load()
            status = if (failed.isBlank()) "บันทึกแล้ว ✅ ผู้ใช้เห็นทันที" else "บันทึกรายการแล้ว แต่ $failed"
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🎲 ของตกแต่งการ์ดสุ่ม (VIP)", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text("ตั้งราคาและเพิ่มของตกแต่งได้เอง · ใส่รูปแทนสี/อีโมจิได้ · มีผลทันทีไม่ต้องอัปเดตแอป · หักเพชรที่เซิร์ฟเวอร์", color = Slate400, fontSize = 11.sp)
            if (!loaded) { if (status.isNotBlank()) Text(status, color = Amber400) else CircularProgressIndicator(color = Pink500); return@Column }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("prices" to "💎 ราคาทั่วไป") + kinds.map { (k, l) -> k to "${when (k) { "frames" -> "🖼"; "nameFrames" -> "🏷"; else -> "✨" }} $l (${rows[k]?.size ?: 0})" }).forEach { (k, l) ->
                    FilterChip(selected = tab == k, onClick = { tab = k }, label = { Text(l) })
                }
            }
            if (tab == "prices") {
                Section("เปิด/ปิดระบบ") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (enabled) "เปิดให้ VIP ตกแต่งการ์ด" else "ปิดอยู่ (ผู้ใช้แก้การ์ดไม่ได้)", color = White, modifier = Modifier.weight(1f)); Switch(enabled, { enabled = it })
                    }
                }
                Section("สื่อบนการ์ด (💎 ต่อครั้งที่เปลี่ยน)") {
                    PriceField("🎬 วิดีโอแนะนำตัว", videoPrice) { videoPrice = it }
                    PriceField("🎤 เสียงทักทาย", audioPrice) { audioPrice = it }
                }
                Section("ผู้ใช้อัปโหลดรูปเอง (0 = ฟรี)") {
                    PriceField("🖼 กรอบการ์ด", customPrices["customFramePrice"].orEmpty()) { customPrices["customFramePrice"] = it }
                    PriceField("🏷 กรอบชื่อ", customPrices["customNameFramePrice"].orEmpty()) { customPrices["customNameFramePrice"] = it }
                    PriceField("✨ เอฟเฟกต์", customPrices["customEffectPrice"].orEmpty()) { customPrices["customEffectPrice"] = it }
                }
            } else {
                val k = tab
                val list = rows.getOrPut(k) { mutableStateListOf() }
                Text(when (k) { "frames" -> "รูปกรอบ: PNG พื้นใสตรงกลาง ยืดเต็มการ์ด"; "nameFrames" -> "รูปกรอบชื่อ: ยืดอยู่หลังชื่อ PNG พื้นใสสวยที่สุด"; else -> "รูปเอฟเฟกต์: GIF / WebP เคลื่อนไหว พื้นใส เล่นทับทั้งการ์ด" } + " · ไม่เกิน 2MB",
                    color = Slate300, fontSize = 12.sp)
                list.forEach { row -> ItemCard(k, row, onPickImage = { imgRow = row; picker.launch("image/*") }, onRemove = { list.remove(row) }) }
                OutlinedButton(onClick = {
                    val id = (when (k) { "frames" -> "f_"; "nameFrames" -> "n_"; else -> "e_" }) + System.currentTimeMillis().toString(36)
                    list.add(RcRow(id, "รายการใหม่", "30", true, if (k == "effects") "✨" else "#FF2FA8,#9B3DFF", when (k) { "effects" -> "rise"; "nameFrames" -> "pill"; else -> "" }))
                }, modifier = Modifier.fillMaxWidth()) { Text("+ เพิ่ม" + kinds.first { it.first == k }.second, color = Pink400) }
            }
        }
        // one save button, always visible
        Column(Modifier.fillMaxWidth().background(Slate950).padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (status.isNotBlank()) Text(status, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            Button(enabled = loaded && !saving, onClick = { save() }, colors = ButtonDefaults.buttonColors(containerColor = Pink500), modifier = Modifier.fillMaxWidth()) {
                Text(if (saving) "กำลังบันทึก…" else "💾 บันทึกทั้งหมด")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Slate900).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        content()
    }
}

@Composable
private fun PriceField(label: String, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        OutlinedTextField(value, { onChange(it.filter(Char::isDigit).take(7)) }, suffix = { Text("💎") }, singleLine = true, modifier = Modifier.width(130.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
    }
}

/** One catalog item: preview + picture buttons on top, name / price / switch, then shape or movement and the fallback colours / emojis. */
@Composable
private fun ItemCard(k: String, row: RcRow, onPickImage: () -> Unit, onRemove: () -> Unit) {
    val img = row.shownImage
    val cols = row.style.split(',').mapNotNull { RandomCardRepo.hex(it.trim()) }
    val glyphs = row.style.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    var confirmDel by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Slate900).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // preview
            Box(Modifier.size(width = 66.dp, height = 88.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2B1660), Color(0xFFB0306E)))), contentAlignment = Alignment.Center) {
                when {
                    k == "nameFrames" -> com.example.ui.random.NameTag("ชื่อ", row.id, cols, 0.55f, row.shape.ifBlank { "pill" }, imageUrl = img)
                    img.isNotBlank() -> coil.compose.AsyncImage(model = img, imageLoader = com.example.ui.social.rememberGifLoader(), contentDescription = null,
                        contentScale = if (k == "frames") ContentScale.FillBounds else ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    k == "frames" && cols.size >= 2 -> Box(Modifier.fillMaxSize().border(4.dp, Brush.linearGradient(cols), RoundedCornerShape(12.dp)))
                    else -> Text(glyphs.joinToString(" ").ifBlank { "✨" }, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (row.enabled) "เปิดใช้งาน" else "ปิดอยู่", color = if (row.enabled) Emerald400 else Slate400, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Switch(row.enabled, { row.enabled = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (img.isBlank()) "🖼 ใส่รูป" else "🖼 เปลี่ยนรูป", color = Pink400, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onPickImage))
                    if (img.isNotBlank()) Text("เอารูปออก", color = Slate400, fontSize = 13.sp, modifier = Modifier.clickable { row.newImg = null; row.newImgUri = null; row.dropImage = row.imageUrl.isNotBlank() })
                    Text("ลบ", color = Rose500, fontSize = 13.sp, modifier = Modifier.clickable { confirmDel = true })
                }
                if (row.newImg != null) Text("รูปใหม่ · รอกดบันทึก", color = Amber400, fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(row.name, { row.name = it.take(30) }, label = { Text("ชื่อ") }, singleLine = true, modifier = Modifier.weight(1.5f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
            OutlinedTextField(row.price, { row.price = it.filter(Char::isDigit).take(7) }, label = { Text("ราคา") }, suffix = { Text("💎") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
        }
        if (k != "frames") {
            Text(if (k == "effects") "การเคลื่อนไหว" else "รูปทรงป้ายชื่อ", color = Slate400, fontSize = 11.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (if (k == "effects") RC_MOVES else RC_SHAPES).forEach { (sk, sl) -> FilterChip(selected = row.shape == sk, onClick = { row.shape = sk }, label = { Text(sl, fontSize = 11.sp) }) }
            }
        }
        OutlinedTextField(row.style, { row.style = it.take(80) }, label = { Text((if (k == "effects") "อีโมจิ คั่นด้วย , เช่น 💗,💖" else "สี คั่นด้วย , เช่น #FFD700,#B8860B") + if (img.isNotBlank()) " (ไม่บังคับเมื่อมีรูป)" else "") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
        if (k != "effects") Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("#FF2FA8", "#9B3DFF", "#00E5FF", "#FFD700", "#B8860B", "#10B981", "#EF4444", "#FFFFFF", "#000000").forEach { c ->
                val on = c in row.style
                Box(Modifier.size(26.dp).clip(CircleShape).background(RandomCardRepo.hex(c) ?: Color.White).border(if (on) 3.dp else 1.dp, if (on) Amber400 else Slate700, CircleShape)
                    .clickable { val cur = row.style.split(',').map { it.trim() }.filter { it.isNotEmpty() }; row.style = (if (on) cur - c else (cur + c).takeLast(3)).joinToString(",") })
            }
        }
    }
    if (confirmDel) AlertDialog(onDismissRequest = { confirmDel = false }, title = { Text("ลบ ${row.name}?") }, text = { Text("จะหายไปเมื่อกดบันทึก · การ์ดที่ใช้อยู่แล้วยังแสดงเหมือนเดิม") },
        confirmButton = { TextButton(onClick = { confirmDel = false; onRemove() }) { Text("ลบ", color = Rose500) } },
        dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("ยกเลิก") } })
}
