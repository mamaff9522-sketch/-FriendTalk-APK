package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.FEATURE_LABELS
import com.example.network.BrainApi
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Admin: per-tier daily limits, ad reward amounts, ad switches, posting switches. Values live in appConfig/limits. */
@Composable
fun AdminLimitsTab() {
    val scope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf<JSONObject?>(null) }
    val fields = remember { mutableStateMapOf<String, String>() } // "tiers.free.radarPerDay" -> "50"
    val flags = remember { mutableStateMapOf<String, Boolean>() }  // "ads.bannerEnabled" -> true
    var reason by remember { mutableStateOf("") }
    var chatReason by remember { mutableStateOf("") }
    var commentsReason by remember { mutableStateOf("") }
    var rUid by remember { mutableStateOf("") }
    var rReason by remember { mutableStateOf("") }
    var rState by remember { mutableStateOf<JSONObject?>(null) }
    var msg by remember { mutableStateOf("") }
    fun load(j: JSONObject) {
        cfg = j; fields.clear(); flags.clear()
        listOf("free", "vip").forEach { t -> j.optJSONObject("tiers")?.optJSONObject(t)?.let { o -> o.keys().forEach { k -> fields["tiers.$t.$k"] = o.opt(k).toString() } } }
        j.optJSONObject("adBonus")?.let { o -> o.keys().forEach { k -> fields["adBonus.$k"] = o.opt(k).toString() } }
        j.optJSONObject("global")?.let { o -> o.keys().forEach { k -> fields["global.$k"] = o.opt(k).toString() } }
        j.optJSONObject("ads")?.let { o -> o.keys().forEach { k -> val v = o.opt(k); if (v is Boolean) flags["ads.$k"] = v else fields["ads.$k"] = v.toString() } }
        j.optJSONObject("posting")?.let { o -> o.keys().forEach { k -> val v = o.opt(k); if (v is Boolean) flags["posting.$k"] = v }; reason = o.optString("reason") }
        j.optJSONObject("chat")?.let { o -> o.keys().forEach { k -> val v = o.opt(k); if (v is Boolean) flags["chat.$k"] = v }; chatReason = o.optString("reason") }
        j.optJSONObject("comments")?.let { o -> o.keys().forEach { k -> val v = o.opt(k); if (v is Boolean) flags["comments.$k"] = v }; commentsReason = o.optString("reason") }
    }
    LaunchedEffect(Unit) {
        val u = FirebaseAuth.getInstance().currentUser ?: return@LaunchedEffect
        val r = BrainApi.call(u, "GET", "/admin/limits")
        if (r.code == 200 && r.json != null) load(r.json) else msg = "โหลดไม่สำเร็จ (${r.code})"
    }
    var tab by remember { mutableStateOf("quota") }
    /** one number on its own line: what it means on the left, the box + unit on the right; 0 shows what it means */
    @Composable fun NumRow(key: String, label: String, unit: String = "ครั้ง", zero: String = "ไม่จำกัด") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, color = White, fontSize = 13.sp)
                if (fields[key] == "0" && zero.isNotBlank()) Text("0 = $zero", color = Emerald400, fontSize = 11.sp)
            }
            OutlinedTextField(value = fields[key].orEmpty(), onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() }) fields[key] = v },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), suffix = { Text(unit, fontSize = 11.sp) },
                modifier = Modifier.width(120.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
        }
    }
    @Composable fun Flag(key: String, label: String) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(label, color = White, fontSize = 13.sp, modifier = Modifier.weight(1f)); Switch(checked = flags[key] == true, onCheckedChange = { flags[key] = it }) }
    }
    @Composable fun Card(title: String, sub: String = "", content: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(Slate900).padding(12.dp)) {
            Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (sub.isNotBlank()) Text(sub, color = Slate400, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
            content()
        }
    }
    fun save() {
        val b = JSONObject()
        fields.forEach { (k, v) -> val parts = k.split("."); val n = v.toIntOrNull() ?: return@forEach
            var o = b; for (i in 0 until parts.size - 1) o = o.optJSONObject(parts[i]) ?: JSONObject().also { nn -> o.put(parts[i], nn) }; o.put(parts.last(), n) }
        flags.forEach { (k, v) -> val (a, c) = k.split("."); (b.optJSONObject(a) ?: JSONObject().also { b.put(a, it) }).put(c, v) }
        (b.optJSONObject("posting") ?: JSONObject().also { b.put("posting", it) }).put("reason", reason)
        (b.optJSONObject("chat") ?: JSONObject().also { b.put("chat", it) }).put("reason", chatReason)
        (b.optJSONObject("comments") ?: JSONObject().also { b.put("comments", it) }).put("reason", commentsReason)
        msg = "กำลังบันทึก…"
        scope.launch {
            val u = FirebaseAuth.getInstance().currentUser ?: return@launch
            val r = BrainApi.call(u, "PUT", "/admin/limits", b)
            if (r.code == 200 && r.json != null) { load(r.json); msg = "บันทึกแล้ว ✅ มีผลทันที" } else msg = r.json?.optString("error") ?: "บันทึกไม่สำเร็จ (${r.code})"
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            if (cfg == null) { Text(msg.ifBlank { "กำลังโหลด…" }, color = Slate400); return@Column }
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("quota" to "📊 โควตาต่อวัน", "ads" to "📺 โฆษณา", "switch" to "🔌 เปิด/ปิดระบบ", "offers" to "🎁 ข้อเสนอดูโฆษณา").forEach { (k, l) ->
                    FilterChip(selected = tab == k, onClick = { tab = k }, label = { Text(l) })
                }
            }
            when (tab) {
                "quota" -> {
                    Text("ผู้ใช้แต่ละคนใช้แต่ละฟีเจอร์ได้กี่ครั้งต่อวัน (รีเซ็ตเที่ยงคืน) · ใส่ 0 = ไม่จำกัด", color = Slate300, fontSize = 12.sp)
                    FEATURE_LABELS.keys.forEach { f ->
                        Card(FEATURE_LABELS[f] ?: f) {
                            NumRow("tiers.free.$f", "👤 สมาชิกฟรี ใช้ได้วันละ")
                            NumRow("tiers.vip.$f", "⭐ สมาชิก VIP ใช้ได้วันละ")
                            NumRow("adBonus.$f", "📺 ดูโฆษณา 1 ครั้ง ได้เพิ่ม", zero = "ดูโฆษณาแล้วไม่ได้เพิ่ม")
                        }
                    }
                }
                "ads" -> {
                    Card("เปิด/ปิดโฆษณา") {
                        Flag("ads.adsEnabled", "เปิดโฆษณาทั้งหมด"); Flag("ads.rewardEnabled", "ให้ดูโฆษณาเพื่อรับโควตาเพิ่ม"); Flag("ads.bannerEnabled", "แบนเนอร์ (หน้าแรก / ฟีด / แชท)")
                        Flag("ads.interstitialEnabled", "โฆษณาเต็มจอระหว่างปัดหาเพื่อน")
                        Flag("ads.ssvRequired", "ให้รางวัลเฉพาะเมื่อ Google ยืนยัน (SSV)")
                    }
                    Card("ดูโฆษณาได้กี่ครั้งต่อวัน") {
                        NumRow("tiers.free.maxAdsPerDay", "👤 สมาชิกฟรี", zero = "ไม่จำกัด")
                        NumRow("tiers.vip.maxAdsPerDay", "⭐ VIP (ที่ใช้เพชรฟรีซื้อ)", zero = "ไม่จำกัด")
                    }
                    Card("ความถี่") {
                        NumRow("ads.interstitialEverySwipes", "โฆษณาเต็มจอ ทุก ๆ กี่ครั้งที่ปัด", zero = "")
                        NumRow("global.adMinIntervalSec", "เว้นระหว่างโฆษณาแต่ละตัว", unit = "วินาที", zero = "ไม่ต้องเว้น")
                    }
                    Card("บอท AI ทั้งระบบ") {
                        NumRow("global.botDailyLimit", "ข้อความบอทรวมทั้งแอป ต่อวัน", unit = "ข้อความ")
                        NumRow("global.botHourlyPerUser", "ข้อความบอทต่อคน ต่อชั่วโมง", unit = "ข้อความ")
                    }
                }
                "switch" -> {
                    Card("📝 การโพสต์") {
                        Flag("posting.enabled", "เปิดให้โพสต์"); Flag("posting.freeEnabled", "สมาชิกฟรีโพสต์ได้"); Flag("posting.vipEnabled", "VIP โพสต์ได้")
                        OutlinedTextField(value = reason, onValueChange = { if (it.length <= 200) reason = it }, label = { Text("ข้อความที่ผู้ใช้เห็นเมื่อปิด") }, modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                    }
                    Card("💬 แชทระหว่างผู้ใช้") {
                        Flag("chat.enabled", "เปิดระบบแชท"); Flag("chat.freeEnabled", "สมาชิกฟรีแชทได้"); Flag("chat.vipEnabled", "VIP แชทได้")
                        OutlinedTextField(value = chatReason, onValueChange = { if (it.length <= 200) chatReason = it }, label = { Text("ข้อความที่ผู้ใช้เห็นเมื่อปิด") }, modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                    }
                    Card("🗨 ความคิดเห็น") {
                        Flag("comments.enabled", "เปิดให้แสดงความคิดเห็น")
                        OutlinedTextField(value = commentsReason, onValueChange = { if (it.length <= 200) commentsReason = it }, label = { Text("ข้อความที่ผู้ใช้เห็นเมื่อปิด") }, modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                    }
                    Text("จำกัดสิทธิ์รายคน (ห้ามโพสต์/ห้ามแชท) อยู่ที่ ผู้ใช้ & ทีมงาน → แตะผู้ใช้", color = Slate400, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                else -> AdminAdOffersSection()
            }
        }
        if (cfg != null && tab != "offers") Column(Modifier.fillMaxWidth().background(Slate950).padding(vertical = 8.dp)) {
            if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("💾 บันทึก (มีผลทันที)") }
        }
    }
}
