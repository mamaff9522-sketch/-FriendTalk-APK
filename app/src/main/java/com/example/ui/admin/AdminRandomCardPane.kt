package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.BrainApi
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** One editable item of the สุ่มหาเพื่อน card catalog. [style] = colours "#RRGGBB,#RRGGBB" (frames) or emojis "💗,💖" (effects). */
private class RcRow(val id: String, name: String, price: String, enabled: Boolean, style: String) {
    var name by mutableStateOf(name); var price by mutableStateOf(price); var enabled by mutableStateOf(enabled); var style by mutableStateOf(style)
}

/**
 * Admin → ของขวัญ & ตกแต่ง → 'การ์ดสุ่ม': diamond prices of frames, name frames, effects, intro video and voice greeting;
 * add / edit / switch off items. Saved to the server (PUT /admin/randomcard-catalog) — users see it right away, no new APK.
 * Diamonds are always checked and taken by the server.
 */
@Composable
fun AdminRandomCardPane() {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(true) }
    var videoPrice by remember { mutableStateOf("") }
    var audioPrice by remember { mutableStateOf("") }
    val rows = remember { mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<RcRow>>() }
    var status by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val kinds = listOf("frames" to "กรอบรูปโปรไฟล์", "nameFrames" to "กรอบป้ายชื่อ", "effects" to "เอฟเฟกต์")
    suspend fun load() {
        val u = FirebaseAuth.getInstance().currentUser ?: return
        val r = BrainApi.call(u, "GET", "/admin/randomcard-catalog"); val j = r.json
        if (r.code != 200 || j == null) { status = if (r.code == 404) "เซิร์ฟเวอร์ยังไม่อัปเดต" else "โหลดไม่สำเร็จ (${r.code})"; return }
        enabled = j.optBoolean("enabled", true); videoPrice = j.optInt("videoPrice").toString(); audioPrice = j.optInt("audioPrice").toString()
        kinds.forEach { (k, _) ->
            val o = j.optJSONObject(k) ?: JSONObject()
            rows[k] = mutableStateListOf<RcRow>().apply {
                o.keys().forEach { id -> val v = o.getJSONObject(id)
                    val st = (v.optJSONArray(if (k == "effects") "glyphs" else "colors") ?: JSONArray()).let { a -> (0 until a.length()).joinToString(",") { a.optString(it) } }
                    add(RcRow(id, v.optString("name", id), v.optInt("price").toString(), v.optBoolean("enabled", true), st)) }
            }
        }
        loaded = true
    }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("🐢 ของตกแต่งการ์ดสุ่ม (VIP)", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text("รูปและข้อความใช้ฟรี · รายการด้านล่างคิดเพชรทุกครั้งที่ผู้ใช้เปลี่ยน · ราคาใหม่มีผลทันทีโดยไม่ต้องอัปเดตแอป · การหักเพชรทำที่เซิร์ฟเวอร์และบันทึกประวัติทุกครั้ง",
            color = Slate400, fontSize = 11.sp)
        if (!loaded) { if (status.isNotBlank()) Text(status, color = Amber400) else CircularProgressIndicator(color = Pink500); return@Column }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("เปิดระบบตกแต่งการ์ด", color = White, modifier = Modifier.weight(1f)); Switch(enabled, { enabled = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(videoPrice, { videoPrice = it.filter(Char::isDigit).take(7) }, label = { Text("วิดีโอแนะนำตัว 💎") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
            OutlinedTextField(audioPrice, { audioPrice = it.filter(Char::isDigit).take(7) }, label = { Text("เสียงทักทาย 💎") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
        }
        kinds.forEach { (k, label) ->
            val list = rows.getOrPut(k) { mutableStateListOf() }
            Text(label, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            list.forEach { row ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Slate900).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.id, color = Slate400, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(if (row.enabled) "เปิด" else "ปิดใช้งาน", color = if (row.enabled) Emerald400 else Slate400, fontSize = 12.sp); Spacer(Modifier.width(6.dp))
                        Switch(row.enabled, { row.enabled = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(row.name, { row.name = it.take(30) }, label = { Text("ชื่อ") }, singleLine = true, modifier = Modifier.weight(1.4f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                        OutlinedTextField(row.price, { row.price = it.filter(Char::isDigit).take(7) }, label = { Text("💎") }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                    }
                    OutlinedTextField(row.style, { row.style = it.take(80) }, label = { Text(if (k == "effects") "อีโมจิ คั่นด้วย , เช่น 💗,💖" else "สี คั่นด้วย , เช่น #FFD700,#B8860B") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                }
            }
            var newId by remember(k) { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newId, { newId = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(24) }, label = { Text("รหัสรายการใหม่ (a-z, 0-9, _)") }, singleLine = true,
                    modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                TextButton(enabled = newId.isNotBlank() && newId != "none" && list.none { it.id == newId }, onClick = {
                    list.add(RcRow(newId, newId, "30", true, if (k == "effects") "✨" else "#FF2FA8,#9B3DFF")); newId = ""
                }) { Text("+ เพิ่ม", color = Pink400) }
            }
        }
        if (status.isNotBlank()) Text(status, color = Amber400, fontSize = 13.sp)
        Button(enabled = !saving, onClick = {
            saving = true; status = ""
            scope.launch {
                val body = JSONObject().put("enabled", enabled).put("videoPrice", videoPrice.toIntOrNull() ?: 0).put("audioPrice", audioPrice.toIntOrNull() ?: 0)
                kinds.forEach { (k, _) ->
                    val o = JSONObject()
                    rows[k].orEmpty().forEachIndexed { i, row ->
                        val parts = row.style.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        o.put(row.id, JSONObject().put("name", row.name).put("price", row.price.toIntOrNull() ?: 0).put("enabled", row.enabled).put("order", i)
                            .put(if (k == "effects") "glyphs" else "colors", JSONArray(parts)))
                    }
                    body.put(k, o)
                }
                val u = FirebaseAuth.getInstance().currentUser
                val r = if (u == null) null else BrainApi.call(u, "PUT", "/admin/randomcard-catalog", body)
                saving = false
                status = if (r?.code == 200) "บันทึกแล้ว ✅ ผู้ใช้เห็นราคาใหม่ทันที" else r?.json?.optString("error")?.ifBlank { null } ?: "บันทึกไม่สำเร็จ (${r?.code})"
            }
        }, colors = ButtonDefaults.buttonColors(containerColor = Pink500), modifier = Modifier.fillMaxWidth()) { Text(if (saving) "กำลังบันทึก…" else "บันทึกราคา") }
    }
}
