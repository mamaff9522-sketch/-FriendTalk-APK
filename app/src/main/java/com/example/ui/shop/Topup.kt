package com.example.ui.shop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.BrainApi
import com.example.ui.components.ft.Text
import com.example.ui.components.tr
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/* Coin / diamond top-up by bank transfer (works while Google Play billing is not available):
 * user picks a package, transfers, enters the reference → admin checks the bank and confirms → wallet credited.
 * Server: friendtalk-brain /coins/topup, /coins/topup/mine, /admin/coins/topup|pending|confirm|reject. */

private suspend fun api(method: String, path: String, body: JSONObject? = null): BrainApi.ApiResponse {
    val u = FirebaseAuth.getInstance().currentUser ?: return BrainApi.ApiResponse(0, null)
    return BrainApi.call(u, method, path, body)
}
private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
private fun fmtThb(v: Double) = if (v % 1.0 == 0.0) "฿${v.toLong()}" else "฿%.2f".format(v)
private fun when_(t: Long) = if (t <= 0) "" else java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("th", "TH")).format(java.util.Date(t))
private fun statusTh(s: String) = when (s) { "pending" -> "⏳ รอแอดมินตรวจ"; "paid" -> "✅ เติมแล้ว"; "rejected" -> "❌ ไม่ผ่าน"; else -> s }

/** User side, shown in the shop for [currency] = "coins" or "diamonds". */
@Composable
fun TopupSection(currency: String) {
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    var cfg by remember { mutableStateOf<JSONObject?>(null) }
    var mine by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var chosen by remember(currency) { mutableStateOf<JSONObject?>(null) }
    var ref by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(tick) {
        cfg = api("GET", "/coins/topup").json ?: JSONObject()
        mine = api("GET", "/coins/topup/mine").json?.optJSONArray("items").objs()
    }
    val c = cfg
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (c == null) { CircularProgressIndicator(color = Pink500); return@Column }
        if (!c.optBoolean("enabled")) {
            Surface(shape = RoundedCornerShape(14.dp), color = Slate900) {
                Text(tr("ยังไม่เปิดขาย — แอดมินกำลังเตรียมช่องทางชำระเงิน"), color = Slate300, modifier = Modifier.padding(16.dp))
            }
        } else {
            val packs = c.optJSONArray("packages").objs().filter { if (currency == "diamonds") it.optInt("diamonds") > 0 else it.optInt("coins") > 0 }
            Text(tr("เติมด้วยการโอนเงิน"), color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(tr("1) เลือกแพ็กเกจ  2) โอนตามยอด  3) กรอกเลขอ้างอิง แอดมินตรวจแล้วเติมให้"), color = Slate400, fontSize = 12.sp)
            if (packs.isEmpty()) Text(tr("ยังไม่มีแพ็กเกจ"), color = Slate400)
            packs.forEach { p ->
                val sel = chosen?.optString("id") == p.optString("id")
                Surface(onClick = { chosen = p; msg = "" }, shape = RoundedCornerShape(14.dp), color = Slate900,
                    border = BorderStroke(if (sel) 2.dp else 1.dp, if (sel) Pink500 else Slate700), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(listOfNotNull(p.optInt("coins").takeIf { it > 0 }?.let { "🪙 $it" }, p.optInt("diamonds").takeIf { it > 0 }?.let { "💎 $it" }).joinToString("  +  "),
                                color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (p.optString("label").isNotBlank()) Text(p.optString("label"), color = Slate400, fontSize = 12.sp)
                        }
                        Text(fmtThb(p.optDouble("priceThb")), color = Amber400, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
            chosen?.let { p ->
                Surface(shape = RoundedCornerShape(14.dp), color = Slate800, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("โอน ${fmtThb(p.optDouble("priceThb"))} ไปที่", color = White, fontWeight = FontWeight.Bold)
                        listOf("ธนาคาร" to c.optString("bankName"), "ชื่อบัญชี" to c.optString("accountName"), "เลขบัญชี" to c.optString("accountNo"), "พร้อมเพย์" to c.optString("promptPay"))
                            .filter { it.second.isNotBlank() }.forEach { (k, v) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("$k: ", color = Slate400, fontSize = 13.sp); Text(v, color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    if (k == "เลขบัญชี" || k == "พร้อมเพย์") TextButton(onClick = { clip.setText(AnnotatedString(v)); msg = tr("คัดลอกแล้ว") }) { Text(tr("คัดลอก"), fontSize = 12.sp) }
                                }
                            }
                        if (c.optString("note").isNotBlank()) Text(c.optString("note"), color = Slate300, fontSize = 12.sp)
                        OutlinedTextField(ref, { ref = it.take(100) }, label = { Text(tr("เลขอ้างอิง / เวลาที่โอน")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                        Button(enabled = !busy && ref.trim().length >= 3, onClick = {
                            busy = true; msg = ""
                            scope.launch {
                                val r = api("POST", "/coins/topup", JSONObject().put("packageId", p.optString("id")).put("paymentRef", ref.trim()))
                                if (r.code == 202) { msg = tr("ส่งคำขอแล้ว รอแอดมินตรวจสอบ (จะมีแจ้งเตือนเมื่อเติมเสร็จ)"); ref = ""; chosen = null; tick++ }
                                else msg = r.json?.optString("error")?.ifBlank { null } ?: tr("ส่งไม่สำเร็จ")
                                busy = false
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Pink500), modifier = Modifier.fillMaxWidth()) { Text(if (busy) tr("กำลังส่ง…") else tr("แจ้งโอนแล้ว")) }
                    }
                }
            }
        }
        if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 13.sp)
        if (mine.isNotEmpty()) {
            Text(tr("คำขอของฉัน"), color = White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            mine.take(10).forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(listOfNotNull(t.optInt("coins").takeIf { it > 0 }?.let { "🪙 $it" }, t.optInt("diamonds").takeIf { it > 0 }?.let { "💎 $it" }).joinToString(" + ") +
                            (if (t.has("priceThb")) "  ·  " + fmtThb(t.optDouble("priceThb")) else ""), color = White, fontSize = 13.sp)
                        Text(when_(t.optLong("createdAt")) + if (t.optString("reason").isNotBlank()) " · ${t.optString("reason")}" else "", color = Slate400, fontSize = 11.sp)
                    }
                    Text(statusTh(t.optString("status")), color = if (t.optString("status") == "paid") Emerald400 else Slate300, fontSize = 12.sp)
                }
            }
        }
    }
}

/** Admin → เศรษฐกิจ → เติมเงิน (โอน): pending requests to confirm / reject, and the pay info + packages. */
@Composable
fun AdminTopupPane() {
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf("pending") }
    var items by remember { mutableStateOf<List<JSONObject>?>(null) }
    var msg by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(view, tick) { if (view == "pending") items = api("GET", "/admin/coins/pending").json?.optJSONArray("items").objs() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = view == "pending", onClick = { view = "pending" }, label = { Text("⏳ รอยืนยัน") })
            FilterChip(selected = view == "settings", onClick = { view = "settings" }, label = { Text("⚙ บัญชีรับเงิน & แพ็กเกจ") })
        }
        if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp)
        if (view == "settings") { TopupSettings { msg = it }; return@Column }
        val l = items
        when {
            l == null -> CircularProgressIndicator(color = Pink500)
            l.isEmpty() -> Text("ไม่มีคำขอรอยืนยัน", color = Slate400)
            else -> l.forEach { t ->
                var reason by remember(t.optString("id")) { mutableStateOf("") }
                Surface(shape = RoundedCornerShape(12.dp), color = Slate800, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${t.optString("name")} · " + listOfNotNull(t.optInt("coins").takeIf { it > 0 }?.let { "🪙 $it" }, t.optInt("diamonds").takeIf { it > 0 }?.let { "💎 $it" }).joinToString(" + "),
                            color = White, fontWeight = FontWeight.Bold)
                        Text("ยอด ${if (t.has("priceThb")) fmtThb(t.optDouble("priceThb")) else "-"} · อ้างอิง: ${t.optString("paymentRef")}", color = Slate300, fontSize = 13.sp)
                        Text(when_(t.optLong("createdAt")) + " · uid ${t.optString("uid").take(8)}…", color = Slate400, fontSize = 11.sp)
                        OutlinedTextField(reason, { reason = it.take(200) }, label = { Text("เหตุผล (ถ้าปฏิเสธ)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { scope.launch {
                                val r = api("POST", "/admin/coins/confirm", JSONObject().put("uid", t.optString("uid")).put("txId", t.optString("id")))
                                msg = if (r.code == 200) "เติมให้ ${t.optString("name")} แล้ว" else r.json?.optString("error") ?: "ไม่สำเร็จ (${r.code})"; tick++
                            } }, colors = ButtonDefaults.buttonColors(containerColor = Emerald500)) { Text("✓ ยืนยันได้รับเงิน") }
                            OutlinedButton(onClick = { scope.launch {
                                val r = api("POST", "/admin/coins/reject", JSONObject().put("uid", t.optString("uid")).put("txId", t.optString("id")).put("reason", reason))
                                msg = if (r.code == 200) "ปฏิเสธแล้ว" else r.json?.optString("error") ?: "ไม่สำเร็จ (${r.code})"; tick++
                            } }) { Text("✕ ปฏิเสธ", color = Rose500) }
                        }
                    }
                }
            }
        }
    }
}

private data class PackRow(val id: String, val label: String, val coins: String, val diamonds: String, val price: String, val active: Boolean)

@Composable
private fun TopupSettings(onMsg: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var bank by remember { mutableStateOf("") }
    var accName by remember { mutableStateOf("") }
    var accNo by remember { mutableStateOf("") }
    var pp by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val packs = remember { mutableStateListOf<PackRow>() }
    LaunchedEffect(Unit) {
        val c = api("GET", "/coins/topup").json ?: JSONObject()
        enabled = c.optBoolean("enabled"); bank = c.optString("bankName"); accName = c.optString("accountName"); accNo = c.optString("accountNo"); pp = c.optString("promptPay"); note = c.optString("note")
        packs.clear(); c.optJSONArray("packages").objs().forEach { p ->
            packs.add(PackRow(p.optString("id"), p.optString("label"), p.optInt("coins").toString(), p.optInt("diamonds").toString(), p.optDouble("priceThb").let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }, p.optBoolean("active", true)))
        }
        loaded = true
    }
    if (!loaded) { CircularProgressIndicator(color = Pink500); return }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("เปิดให้ผู้ใช้เติมเงินแบบโอน", color = White, modifier = Modifier.weight(1f)); Switch(enabled, { enabled = it }) }
        OutlinedTextField(bank, { bank = it.take(60) }, label = { Text("ธนาคาร") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(accName, { accName = it.take(80) }, label = { Text("ชื่อบัญชี") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(accNo, { accNo = it.take(40) }, label = { Text("เลขบัญชี") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pp, { pp = it.take(40) }, label = { Text("พร้อมเพย์ (ถ้ามี)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it.take(300) }, label = { Text("หมายเหตุถึงผู้ใช้") }, modifier = Modifier.fillMaxWidth())
        Text("แพ็กเกจ (ใส่เหรียญหรือเพชร อย่างใดอย่างหนึ่งหรือทั้งคู่)", color = Slate300, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        packs.forEachIndexed { i, p ->
            Surface(shape = RoundedCornerShape(10.dp), color = Slate800) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(p.coins, { packs[i] = p.copy(coins = it.filter(Char::isDigit).take(7)) }, label = { Text("🪙") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(p.diamonds, { packs[i] = p.copy(diamonds = it.filter(Char::isDigit).take(7)) }, label = { Text("💎") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(p.price, { packs[i] = p.copy(price = it.filter { c -> c.isDigit() || c == '.' }.take(9)) }, label = { Text("฿") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(p.label, { packs[i] = p.copy(label = it.take(40)) }, label = { Text("ป้าย เช่น ขายดี") }, singleLine = true, modifier = Modifier.weight(1f))
                        Switch(p.active, { packs[i] = p.copy(active = it) }, modifier = Modifier.padding(horizontal = 6.dp))
                        TextButton(onClick = { packs.removeAt(i) }) { Text("ลบ", color = Rose500) }
                    }
                }
            }
        }
        OutlinedButton(onClick = { packs.add(PackRow("p" + System.currentTimeMillis().toString(36), "", "0", "0", "", true)) }) { Text("+ เพิ่มแพ็กเกจ") }
        Button(onClick = { scope.launch {
            val arr = JSONArray(); packs.forEach { p -> arr.put(JSONObject().put("id", p.id).put("label", p.label).put("coins", p.coins.toIntOrNull() ?: 0).put("diamonds", p.diamonds.toIntOrNull() ?: 0).put("priceThb", p.price.toDoubleOrNull() ?: 0.0).put("active", p.active)) }
            val r = api("PUT", "/admin/coins/topup", JSONObject().put("enabled", enabled).put("bankName", bank).put("accountName", accName).put("accountNo", accNo).put("promptPay", pp).put("note", note).put("packages", arr))
            onMsg(if (r.code == 200) "บันทึกแล้ว" else r.json?.optString("error") ?: "บันทึกไม่สำเร็จ (${r.code})")
        } }, colors = ButtonDefaults.buttonColors(containerColor = Pink500), modifier = Modifier.fillMaxWidth()) { Text("บันทึก") }
    }
}
