package com.example.ui.admin

import androidx.compose.foundation.layout.*
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
    var msg by remember { mutableStateOf("") }
    fun load(j: JSONObject) {
        cfg = j; fields.clear(); flags.clear()
        listOf("free", "vip").forEach { t -> j.optJSONObject("tiers")?.optJSONObject(t)?.let { o -> o.keys().forEach { k -> fields["tiers.$t.$k"] = o.opt(k).toString() } } }
        j.optJSONObject("adBonus")?.let { o -> o.keys().forEach { k -> fields["adBonus.$k"] = o.opt(k).toString() } }
        j.optJSONObject("global")?.let { o -> o.keys().forEach { k -> fields["global.$k"] = o.opt(k).toString() } }
        j.optJSONObject("ads")?.let { o -> o.keys().forEach { k -> val v = o.opt(k); if (v is Boolean) flags["ads.$k"] = v else fields["ads.$k"] = v.toString() } }
        j.optJSONObject("posting")?.let { o -> o.keys().forEach { k -> val v = o.opt(k); if (v is Boolean) flags["posting.$k"] = v }; reason = o.optString("reason") }
    }
    LaunchedEffect(Unit) {
        val u = FirebaseAuth.getInstance().currentUser ?: return@LaunchedEffect
        val r = BrainApi.call(u, "GET", "/admin/limits")
        if (r.code == 200 && r.json != null) load(r.json) else msg = "โหลดไม่สำเร็จ (${r.code})"
    }
    @Composable fun NumField(key: String, label: String) {
        OutlinedTextField(value = fields[key].orEmpty(), onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() }) fields[key] = v },
            label = { Text(label, fontSize = 10.sp) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(110.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
    }
    @Composable fun Flag(key: String, label: String) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(label, color = White, fontSize = 13.sp, modifier = Modifier.weight(1f)); Switch(checked = flags[key] == true, onCheckedChange = { flags[key] = it }) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        if (cfg == null) { Text(msg.ifBlank { "กำลังโหลด…" }, color = Slate400); return@Column }
        Text("ลิมิตต่อวัน (0 = ไม่จำกัด)", color = White, fontWeight = FontWeight.Bold)
        Row { Text("ฟีเจอร์", color = Slate400, fontSize = 11.sp, modifier = Modifier.weight(1f)); Text("ฟรี / VIP / +ต่อโฆษณา", color = Slate400, fontSize = 11.sp) }
        (FEATURE_LABELS.keys + "maxAdsPerDay").forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(FEATURE_LABELS[f] ?: "ดูโฆษณาได้/วัน", color = White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                NumField("tiers.free.$f", "ฟรี"); Spacer(Modifier.width(4.dp)); NumField("tiers.vip.$f", "VIP")
                if (f != "maxAdsPerDay") { Spacer(Modifier.width(4.dp)); NumField("adBonus.$f", "+/โฆษณา") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("ทั้งระบบ", color = White, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumField("global.botDailyLimit", "บอท/วัน รวม"); NumField("global.botHourlyPerUser", "บอท/ชม./คน"); NumField("global.adMinIntervalSec", "เว้นโฆษณา(วิ)")
        }
        Spacer(Modifier.height(8.dp))
        Text("โฆษณา (ทดสอบ AdMob)", color = White, fontWeight = FontWeight.Bold)
        Flag("ads.adsEnabled", "เปิดโฆษณาทั้งหมด"); Flag("ads.rewardEnabled", "โฆษณารับโควตา"); Flag("ads.bannerEnabled", "แบนเนอร์ในฟีด")
        Flag("ads.interstitialEnabled", "โฆษณาเต็มจอระหว่างปัด"); NumField("ads.interstitialEverySwipes", "ทุก N ครั้งที่ปัด")
        Flag("ads.ssvRequired", "ให้รางวัลเฉพาะผ่าน SSV ของ Google")
        Spacer(Modifier.height(8.dp))
        Text("การโพสต์", color = White, fontWeight = FontWeight.Bold)
        Flag("posting.enabled", "เปิดให้โพสต์"); Flag("posting.freeEnabled", "สมาชิกฟรีโพสต์ได้"); Flag("posting.vipEnabled", "VIP โพสต์ได้")
        OutlinedTextField(value = reason, onValueChange = { if (it.length <= 200) reason = it }, label = { Text("ข้อความเหตุผลเมื่อปิด") }, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
        if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp)
        Button(onClick = {
            val b = JSONObject()
            fields.forEach { (k, v) -> val parts = k.split("."); val n = v.toIntOrNull() ?: return@forEach
                var o = b; for (i in 0 until parts.size - 1) o = o.optJSONObject(parts[i]) ?: JSONObject().also { nn -> o.put(parts[i], nn) }; o.put(parts.last(), n) }
            flags.forEach { (k, v) -> val (a, c) = k.split("."); (b.optJSONObject(a) ?: JSONObject().also { b.put(a, it) }).put(c, v) }
            (b.optJSONObject("posting") ?: JSONObject().also { b.put("posting", it) }).put("reason", reason)
            scope.launch {
                val u = FirebaseAuth.getInstance().currentUser ?: return@launch
                val r = BrainApi.call(u, "PUT", "/admin/limits", b)
                if (r.code == 200 && r.json != null) { load(r.json); msg = "บันทึกแล้ว มีผลทันที" } else msg = r.json?.optString("error") ?: "บันทึกไม่สำเร็จ (${r.code})"
            }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("บันทึก") }
    }
}
