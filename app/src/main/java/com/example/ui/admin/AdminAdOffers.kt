package com.example.ui.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.json.JSONArray
import org.json.JSONObject

private data class OfferEdit(val id: String, val title: String, val adFormat: String, val label: String, val description: String,
                             val validityHours: String, val feature: String, val amount: String, val enabled: Boolean)

/** Admin editor for appConfig/adOffers (list of ad offers users pick from on the ads page). */
@Composable
fun AdminAdOffersSection() {
    val scope = rememberCoroutineScope()
    val offers = remember { mutableStateListOf<OfferEdit>() }
    var msg by remember { mutableStateOf("") }
    fun load(j: JSONObject?) {
        val arr = j?.optJSONArray("offers") ?: return
        offers.clear()
        for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val r = o.optJSONObject("reward") ?: JSONObject()
            offers.add(OfferEdit(o.optString("id"), o.optString("title"), o.optString("adFormat", "rewarded"), o.optString("estDurationLabel"),
                o.optString("description"), o.optInt("validityHours").toString(), r.optString("feature", "aiBotMessagesPerDay"), r.optInt("amount", 1).toString(), o.optBoolean("enabled", true))) }
    }
    LaunchedEffect(Unit) { FirebaseAuth.getInstance().currentUser?.let { load(BrainApi.call(it, "GET", "/admin/ad-offers").json) } }
    val tf = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White)
    Column {
        Spacer(Modifier.height(16.dp))
        Text("รายการโฆษณาให้ผู้ใช้เลือก", color = White, fontWeight = FontWeight.Bold)
        Text("ระยะเวลาเป็นค่าประมาณ (AdMob ไม่บอกความยาวจริงก่อนเล่น) · อายุรางวัล 0 = ถึงเที่ยงคืนวันนี้", color = Slate400, fontSize = 11.sp)
        offers.forEachIndexed { i, o ->
            fun upd(f: (OfferEdit) -> OfferEdit) { offers[i] = f(offers[i]) }
            Surface(shape = RoundedCornerShape(12.dp), color = Slate800, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(o.id, { v -> upd { it.copy(id = v.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }.take(32)) } }, label = { Text("id") }, singleLine = true, modifier = Modifier.weight(1f), colors = tf)
                        Switch(o.enabled, { v -> upd { it.copy(enabled = v) } })
                        TextButton(onClick = { offers.removeAt(i) }) { Text("ลบ", color = Rose500) }
                    }
                    OutlinedTextField(o.title, { v -> upd { it.copy(title = v.take(60)) } }, label = { Text("ชื่อ") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = tf)
                    OutlinedTextField(o.label, { v -> upd { it.copy(label = v.take(40)) } }, label = { Text("ระยะเวลาโดยประมาณ เช่น ~15–30 วินาที") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = tf)
                    OutlinedTextField(o.description, { v -> upd { it.copy(description = v.take(200)) } }, label = { Text("ใช้ทำอะไรได้") }, modifier = Modifier.fillMaxWidth(), colors = tf)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(o.adFormat == "rewarded", { upd { it.copy(adFormat = "rewarded") } }, label = { Text("Rewarded", fontSize = 11.sp) })
                        FilterChip(o.adFormat == "rewarded_interstitial", { upd { it.copy(adFormat = "rewarded_interstitial") } }, label = { Text("Rewarded interstitial", fontSize = 11.sp) })
                    }
                    Text("รางวัล: ${if (o.feature == "coins") "🪙 เหรียญ" else FEATURE_LABELS[o.feature] ?: o.feature}", color = Slate300, fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                        (listOf("coins" to "🪙 เหรียญ") + FEATURE_LABELS.toList()).forEach { (k, l) -> FilterChip(o.feature == k, { upd { it.copy(feature = k) } }, label = { Text(l, fontSize = 10.sp) }, modifier = Modifier.padding(end = 4.dp)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(o.amount, { v -> if (v.length <= 4 && v.all(Char::isDigit)) upd { it.copy(amount = v) } }, label = { Text("จำนวน") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), colors = tf)
                        OutlinedTextField(o.validityHours, { v -> if (v.length <= 2 && v.all(Char::isDigit)) upd { it.copy(validityHours = v) } }, label = { Text("อายุ (ชม.)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), colors = tf)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { offers.add(OfferEdit("offer${offers.size + 1}", "โฆษณาใหม่", "rewarded", "~15–30 วินาที", "", "0", "aiBotMessagesPerDay", "5", true)) }) { Text("+ เพิ่มโฆษณา", color = White) }
            Button(colors = ButtonDefaults.buttonColors(containerColor = Pink500), onClick = {
                val arr = JSONArray()
                offers.forEachIndexed { i, o -> arr.put(JSONObject().put("id", o.id).put("title", o.title).put("adFormat", o.adFormat).put("estDurationLabel", o.label)
                    .put("description", o.description).put("validityHours", o.validityHours.toIntOrNull() ?: 0).put("enabled", o.enabled).put("order", i)
                    .put("reward", JSONObject().put("feature", o.feature).put("amount", o.amount.toIntOrNull() ?: 0))) }
                scope.launch {
                    val u = FirebaseAuth.getInstance().currentUser ?: return@launch
                    val r = BrainApi.call(u, "PUT", "/admin/ad-offers", JSONObject().put("offers", arr))
                    if (r.code == 200) { load(r.json); msg = "บันทึกรายการโฆษณาแล้ว" } else msg = r.json?.optString("error") ?: "บันทึกไม่สำเร็จ (${r.code})"
                }
            }) { Text("บันทึกรายการโฆษณา") }
        }
        if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp)
    }
}
