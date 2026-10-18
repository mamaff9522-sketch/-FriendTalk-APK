package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.BrainApi
import com.example.ui.components.Tweaks
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Admin → หน้าตาแอป → 🔧 ปรับละเอียด: every value in [Tweaks.DEFS], grouped, as a slider + number box, a switch or a text box.
 * Changed values are marked; 'ค่าเดิม' puts one back to the built-in default. One save button; users see it within seconds.
 */
@Composable
fun AdminTweaksPane() {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Tweaks.start() }
    // working copy: key -> value (only values that differ from the default are sent)
    val edit = remember { mutableStateMapOf<String, Any>() }
    var loadedFrom by remember { mutableStateOf<Map<String, Any>?>(null) }
    LaunchedEffect(Tweaks.values) { if (loadedFrom == null) { edit.clear(); edit.putAll(Tweaks.values); loadedFrom = Tweaks.values } }
    var group by remember { mutableStateOf(Tweaks.DEFS.first().group) }
    var msg by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val groups = Tweaks.DEFS.map { it.group }.distinct()
    fun cur(d: Tweaks.Def): Any = edit[d.key] ?: d.def
    fun changed(d: Tweaks.Def) = edit.containsKey(d.key) && edit[d.key] != d.def && !(d.type == "num" && (edit[d.key] as? Number)?.toFloat() == (d.def as Number).toFloat())

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🔧 ปรับละเอียด", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text("ปรับขนาด ระยะ จำนวนต่อแถว และเปิด/ปิดลูกเล่นเล็ก ๆ · กดบันทึกแล้วผู้ใช้เห็นภายในไม่กี่วินาที ไม่ต้องอัปเดตแอป · ข้อความบนจอแก้ได้ที่ UI Builder → แก้ข้อความทุกหน้า",
                color = Slate400, fontSize = 11.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEach { g ->
                    val n = Tweaks.DEFS.count { it.group == g && changed(it) }
                    FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g + if (n > 0) " ($n)" else "") })
                }
            }
            Tweaks.DEFS.filter { it.group == group }.forEach { d ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Slate900).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.label, color = White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (changed(d)) Text("ค่าเดิม", color = Pink400, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { edit.remove(d.key) }.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    when (d.type) {
                        "bool" -> Row(verticalAlignment = Alignment.CenterVertically) {
                            val on = cur(d) as? Boolean ?: true
                            Text(if (on) "เปิด" else "ปิด", color = if (on) Emerald400 else Slate400, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(on, { edit[d.key] = it })
                        }
                        "text" -> OutlinedTextField((cur(d) as? String).orEmpty(), { edit[d.key] = it.take(300) }, singleLine = false, modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                        else -> {
                            val v = (cur(d) as? Number)?.toFloat() ?: 0f
                            var box by remember(d.key, v) { mutableStateOf(if (v % 1f == 0f) v.toInt().toString() else v.toString()) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Slider(value = v.coerceIn(d.min, d.max), onValueChange = { edit[d.key] = Math.round(it).toFloat() }, valueRange = d.min..d.max, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                OutlinedTextField(box, { t -> box = t.filter { it.isDigit() || it == '.' }.take(7); box.toFloatOrNull()?.let { edit[d.key] = it.coerceIn(d.min, d.max) } },
                                    singleLine = true, suffix = { Text(d.unit, fontSize = 10.sp) }, modifier = Modifier.width(118.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                            }
                            Text("ค่าเริ่มต้น ${(d.def as Number).toFloat().let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }} ${d.unit} · ตั้งได้ ${d.min.toInt()}–${d.max.toInt()}",
                                color = Slate400, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Slate950).padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            Button(enabled = !saving, onClick = {
                saving = true; msg = "กำลังบันทึก…"
                scope.launch {
                    val vals = JSONObject()
                    Tweaks.DEFS.forEach { d -> if (changed(d)) vals.put(d.key, edit[d.key]) }
                    val u = FirebaseAuth.getInstance().currentUser
                    val r = if (u == null) null else BrainApi.call(u, "PUT", "/admin/tweaks", JSONObject().put("values", vals))
                    saving = false
                    msg = when { r?.code == 200 -> "บันทึกแล้ว ✅ ผู้ใช้เห็นภายในไม่กี่วินาที"; r?.code == 404 -> "เซิร์ฟเวอร์ยังไม่อัปเดต (deploy ก่อน)"
                        else -> r?.json?.optString("error")?.ifBlank { null } ?: "บันทึกไม่สำเร็จ (${r?.code})" }
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Pink500), modifier = Modifier.fillMaxWidth()) { Text(if (saving) "กำลังบันทึก…" else "💾 บันทึก") }
        }
    }
}
