package com.example.ui.admin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.social.CatalogItem
import com.example.social.GiftRepo
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Admin: gift catalogs (chat / live / VIP) + room stickers; split %, switches and the gift ledger. */
@Composable
fun AdminGiftsTab() {
    val scope = rememberCoroutineScope()
    var ch by remember { mutableStateOf("chat") }
    var page by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0) }
    var edit by remember { mutableStateOf<CatalogItem?>(null) }
    var msg by remember { mutableStateOf("") }
    var view by remember { mutableStateOf("items") }
    val size = 30
    LaunchedEffect(ch, page, tick) { val (l, t) = GiftRepo.adminList(ch, page, size); items = l; total = t }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GiftRepo.CHANNELS.forEach { (k, l) -> FilterChip(selected = view == "items" && ch == k, onClick = { view = "items"; ch = k; page = 0 }, label = { Text(l) }) }
            FilterChip(selected = view == "config", onClick = { view = "config" }, label = { Text("⚙ ตั้งค่า") })
            FilterChip(selected = view == "tx", onClick = { view = "tx" }, label = { Text("📒 ประวัติ") })
        }
        if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp)
        when (view) {
            "config" -> GiftConfigSection()
            "tx" -> GiftLedgerSection()
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ทั้งหมด $total รายการ", color = Slate300, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Button(onClick = { edit = CatalogItem("item_" + System.currentTimeMillis().toString(36), "", if (ch == "stickers") 0 else 10, "", "", ch, true, -1) },
                        colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("+ เพิ่ม") }
                }
                items.forEachIndexed { i, it ->
                    Surface(shape = RoundedCornerShape(12.dp), color = Slate800, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp)) { com.example.ui.social.StickerImage(it) }
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(it.name, color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                Text("🪙 ${it.price} · ${it.category}" + if (!it.enabled) " · ปิดอยู่" else "", color = if (it.enabled) Slate400 else Amber400, fontSize = 11.sp)
                            }
                            TextButton(enabled = page * size + i > 0, onClick = { scope.launch { move(ch, page, size, items, i, -1); tick++ } }) { Text("↑") }
                            TextButton(enabled = page * size + i < total - 1, onClick = { scope.launch { move(ch, page, size, items, i, 1); tick++ } }) { Text("↓") }
                            TextButton(onClick = { edit = it }) { Text("แก้ไข") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = page > 0, onClick = { page-- }) { Text("‹ ก่อนหน้า", color = White) }
                    Text("หน้า ${page + 1}/${maxOf(1, (total + size - 1) / size)}", color = Slate300, modifier = Modifier.align(Alignment.CenterVertically))
                    OutlinedButton(enabled = (page + 1) * size < total, onClick = { page++ }) { Text("ถัดไป ›", color = White) }
                }
            }
        }
    }
    edit?.let { e -> CatalogItemEditor(ch, e, onClose = { changed -> edit = null; if (changed) tick++ }, onMsg = { msg = it }) }
}

/** Reorder by swapping with neighbour; needs the whole order list, so fetch all ids once. */
private suspend fun move(ch: String, page: Int, size: Int, pageItems: List<CatalogItem>, i: Int, dir: Int) {
    val all = mutableListOf<CatalogItem>(); var p = 0
    while (true) { val (l, t) = GiftRepo.adminList(ch, p, 100); all += l; p++; if (all.size >= t || l.isEmpty()) break }
    val idx = page * size + i; val j = idx + dir; if (j !in all.indices) return
    java.util.Collections.swap(all, idx, j); GiftRepo.adminOrder(ch, all.map { it.id })
}

@Composable
private fun CatalogItemEditor(ch: String, init: CatalogItem, onClose: (Boolean) -> Unit, onMsg: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(init.name) }
    var price by remember { mutableStateOf(init.price.toString()) }
    var category by remember { mutableStateOf(init.category) }
    var icon by remember { mutableStateOf(init.icon) }
    var enabled by remember { mutableStateOf(init.enabled) }
    var src by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var rot by remember { mutableStateOf(0f) }
    var ox by remember { mutableStateOf(0f) }
    var oy by remember { mutableStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var confirmDel by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) src = runCatching { ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }
    val out = remember(src, scale, rot, ox, oy) { src?.let { render(it, scale, rot, ox, oy) } }
    Dialog(onDismissRequest = { onClose(false) }) {
        Surface(shape = RoundedCornerShape(16.dp), color = Slate900) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (init.name.isBlank()) "เพิ่มรายการ" else "แก้ไข ${init.name}", color = White, fontWeight = FontWeight.Bold)
                Box(Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)).background(Slate800).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                    when { out != null -> Image(out.asImageBitmap(), null, Modifier.fillMaxSize())
                        init.imageUrl.isNotBlank() -> AsyncImage(model = init.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                        else -> Text(icon.ifBlank { "🎁" }, fontSize = 56.sp) }
                }
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("🖼 เลือกรูปจากเครื่อง", color = White) }
                if (src != null) {
                    Text("ย่อ/ขยาย ${"%.2f".format(scale)}", color = Slate300, fontSize = 11.sp); Slider(scale, { scale = it }, valueRange = 0.3f..3f)
                    Text("หมุน ${rot.toInt()}°", color = Slate300, fontSize = 11.sp); Slider(rot, { rot = it }, valueRange = -180f..180f)
                    Text("เลื่อนซ้าย/ขวา", color = Slate300, fontSize = 11.sp); Slider(ox, { ox = it }, valueRange = -0.5f..0.5f)
                    Text("เลื่อนขึ้น/ลง", color = Slate300, fontSize = 11.sp); Slider(oy, { oy = it }, valueRange = -0.5f..0.5f)
                }
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("ชื่อ") }, singleLine = true)
                OutlinedTextField(price, { price = it.filter(Char::isDigit).take(7) }, label = { Text("ราคา (เหรียญ)" + if (ch == "stickers") " 0 = ฟรี" else "") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(category, { category = it.take(30) }, label = { Text("หมวดหมู่") }, singleLine = true)
                OutlinedTextField(icon, { icon = it.take(4) }, label = { Text("อีโมจิ (ถ้าไม่มีรูป)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("เปิดใช้งาน", color = White, modifier = Modifier.weight(1f)); Switch(enabled, { enabled = it }) }
                if (err.isNotBlank()) Text(err, color = Amber400, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (init.name.isNotBlank()) TextButton(onClick = { confirmDel = true }) { Text("ลบ", color = Pink400) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onClose(false) }) { Text("ยกเลิก", color = Slate300) }
                    Button(enabled = !busy && name.isNotBlank(), onClick = {
                        busy = true; err = ""
                        scope.launch {
                            val r = GiftRepo.adminSave(ch, init.copy(name = name.trim(), price = price.toIntOrNull() ?: 0, category = category.ifBlank { ch }, icon = icon, enabled = enabled, order = init.order))
                            if (!r.ok) { err = r.msg; busy = false; return@launch }
                            out?.let { bmp ->
                                val bytes = withContext(Dispatchers.Default) { compressWebp(bmp) }
                                if (bytes == null) { err = "บีบอัดรูปให้ไม่เกิน 512KB ไม่ได้"; busy = false; return@launch }
                                val u = GiftRepo.adminUploadImage(ch, init.id, bytes); if (!u.ok) { err = u.msg; busy = false; return@launch }
                            }
                            onMsg("บันทึก ${name} แล้ว"); onClose(true)
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text(if (busy) "กำลังบันทึก…" else "บันทึก") }
                }
            }
        }
    }
    if (confirmDel) AlertDialog(onDismissRequest = { confirmDel = false }, title = { Text("ลบ ${init.name}?") },
        confirmButton = { TextButton(onClick = { scope.launch { GiftRepo.adminDelete(ch, init.id); confirmDel = false; onClose(true) } }) { Text("ลบ") } },
        dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("ยกเลิก") } })
}

/** Square 512px render with scale/rotate/offset (simple crop). */
private fun render(src: Bitmap, scale: Float, rot: Float, ox: Float, oy: Float): Bitmap {
    val n = 512; val out = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    val base = n.toFloat() / minOf(src.width, src.height)
    val m = Matrix().apply {
        postTranslate(-src.width / 2f, -src.height / 2f); postScale(base * scale, base * scale); postRotate(rot)
        postTranslate(n / 2f + ox * n, n / 2f + oy * n)
    }
    Canvas(out).drawBitmap(src, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)); return out
}
private fun compressWebp(b: Bitmap): ByteArray? {
    @Suppress("DEPRECATION") val fmt = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
    var q = 90; var bmp = b
    repeat(8) {
        val bo = ByteArrayOutputStream(); bmp.compress(fmt, q, bo)
        if (bo.size() <= 512 * 1024) return bo.toByteArray()
        if (q > 50) q -= 15 else bmp = Bitmap.createScaledBitmap(bmp, bmp.width * 3 / 4, bmp.height * 3 / 4, true)
    }
    return null
}

@Composable
private fun GiftConfigSection() {
    val scope = rememberCoroutineScope()
    var en by remember { mutableStateOf(true) }; var af by remember { mutableStateOf(true) }
    var ap by remember { mutableStateOf("20") }; var rp by remember { mutableStateOf("80") }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { GiftRepo.adminConfig()?.let { en = it.optBoolean("enabled", true); af = it.optBoolean("autoFriend", true); ap = it.optInt("adminPercent", 20).toString(); rp = it.optInt("recipientPercent", 80).toString() } }
    Row(verticalAlignment = Alignment.CenterVertically) { Text("เปิดระบบของขวัญแชท", color = White, modifier = Modifier.weight(1f)); Switch(en, { en = it }) }
    Row(verticalAlignment = Alignment.CenterVertically) { Text("รับของขวัญแล้วเป็นเพื่อนอัตโนมัติ", color = White, modifier = Modifier.weight(1f)); Switch(af, { af = it }) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(ap, { ap = it.filter(Char::isDigit).take(3) }, label = { Text("แอดมิน %") }, modifier = Modifier.weight(1f), singleLine = true)
        OutlinedTextField(rp, { rp = it.filter(Char::isDigit).take(3) }, label = { Text("ผู้รับ %") }, modifier = Modifier.weight(1f), singleLine = true)
    }
    val sum = (ap.toIntOrNull() ?: 0) + (rp.toIntOrNull() ?: 0)
    if (sum != 100) Text("รวมต้องเท่ากับ 100 (ตอนนี้ $sum)", color = Amber400, fontSize = 12.sp)
    Button(enabled = sum == 100, onClick = { scope.launch { msg = GiftRepo.adminSetConfig(org.json.JSONObject().put("enabled", en).put("autoFriend", af).put("adminPercent", ap.toInt()).put("recipientPercent", rp.toInt())).msg } }) { Text("บันทึก") }
    if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp)
}

@Composable
private fun GiftLedgerSection() {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { val (l, c) = GiftRepo.adminTx(null); rows = l; cursor = c; loaded = true }
    if (loaded && rows.isEmpty()) Text("ยังไม่มีรายการ", color = Slate400)
    rows.forEach { t ->
        val sp = t.optJSONObject("split")
        Surface(shape = RoundedCornerShape(10.dp), color = Slate800, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                Text("${t.optString("name")} · 🪙${t.optInt("price")} · ${t.optString("status")}", color = White, fontSize = 13.sp)
                Text("จาก ${t.optString("from").take(8)} → ${t.optString("to").take(8)} · " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("th")).format(java.util.Date(t.optLong("createdAt"))), color = Slate400, fontSize = 11.sp)
                sp?.let { Text("ผู้รับ ${it.optInt("recipientShare")} · แอดมิน ${it.optInt("adminShare")}" + if (t.optBoolean("friended")) " · เพิ่มเพื่อนแล้ว" else "", color = Emerald400, fontSize = 11.sp) }
            }
        }
    }
    cursor?.let { c -> OutlinedButton(onClick = { scope.launch { val (l, n) = GiftRepo.adminTx(c); rows = rows + l; cursor = n } }) { Text("โหลดเพิ่ม", color = White) } }
}
