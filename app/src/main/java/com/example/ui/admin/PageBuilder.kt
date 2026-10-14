package com.example.ui.admin

import com.example.ui.components.dialogNavPadding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.network.BrainApi
import com.example.ui.components.PageSpec
import com.example.ui.components.SectionSpec
import com.example.ui.components.TextSpec
import com.example.ui.components.UiPages
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Every-page builder: page selector; sections (order/hide/size); every text seen on that page (content/size/colour/weight); draft/publish/rollback per page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageBuilderDialog(onClose: () -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser ?: return onClose()
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("home") }
    var spec by remember { mutableStateOf(PageSpec()) }
    var versions by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf("") }
    var colorFor by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        val r = BrainApi.call(user, "GET", "/admin/ui/pages/$page")
        if (r.code != 200) { msg = "โหลดไม่ได้ (${r.code}) ${r.json?.optString("error") ?: ""}"; return }
        spec = PageSpec.from(r.json?.optJSONObject("draft") ?: r.json?.optJSONObject("current"))
        val a = r.json?.optJSONArray("versions"); versions = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it).let { o -> o.optString("id") to o.optLong("savedAt") } }
        msg = ""
    }
    LaunchedEffect(page) { load() }
    LaunchedEffect(page, spec) { UiPages.draft = page to spec } // live preview in the app behind
    DisposableEffect(Unit) { onDispose { UiPages.draft = null } }
    fun save(publish: Boolean) = scope.launch {
        val r = BrainApi.call(user, "PUT", "/admin/ui/pages/$page", JSONObject().put("page", spec.toJson()).put("publish", publish))
        msg = if (r.code == 200) { if (publish) { UiPages.refresh(); load(); "เผยแพร่แล้ว" } else "บันทึกร่างแล้ว" } else "ไม่สำเร็จ: ${r.json?.optString("error") ?: r.code}"
    }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.systemBarsPadding().dialogNavPadding().imePadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📄 จัดหน้าแต่ละหน้า", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f)); TextButton(onClose) { Text("ปิด") }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(UiPages.PAGES.entries.toList()) { (k, l) -> FilterChip(page == k, { page = k }, { Text(l) }) }
                }
                Text("เปิดหน้าที่ต้องการในแอปก่อน 1 ครั้ง ข้อความ/ส่วนของหน้านั้นจะขึ้นในรายการ · การแก้ไขแสดงผลทันทีในแอปด้านหลัง (ร่าง) จนกว่าจะกดเผยแพร่", fontSize = 11.sp, color = Color.Gray)
                if (msg.isNotBlank()) Text(msg, color = Color(0xFFD32F2F))
                TabRow(tab) { Tab(tab == 0, { tab = 0 }) { Text("ส่วนของหน้า", Modifier.padding(8.dp)) }; Tab(tab == 1, { tab = 1 }) { Text("ข้อความ", Modifier.padding(8.dp)) }; Tab(tab == 2, { tab = 2 }) { Text("เวอร์ชัน", Modifier.padding(8.dp)) } }
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> {
                            val seen = UiPages.seenSections[page] ?: emptyMap()
                            val ids = (seen.keys + spec.sections.keys).distinct().sortedBy { spec.sections[it]?.order?.takeIf { o -> o > 0 } ?: 999 }
                            LazyColumn {
                                if (ids.isEmpty()) item { Text("ยังไม่พบส่วนของหน้านี้ — เปิดหน้านี้ในแอปก่อน", Modifier.padding(12.dp)) }
                                items(ids) { id ->
                                    val s = spec.sections[id] ?: SectionSpec()
                                    fun put(n: SectionSpec) { spec = spec.copy(sections = spec.sections + (id to n)) }
                                    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Column(Modifier.padding(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(seen[id] ?: id, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                            TextButton({ val i = ids.indexOf(id); if (i > 0) { val o = ids[i - 1]; spec = spec.copy(sections = spec.sections + ids.mapIndexed { j, x -> x to (spec.sections[x] ?: SectionSpec()).copy(order = (if (x == id) i else if (x == o) i + 1 else j + 1) * 10) }) } }) { Text("▲") }
                                            TextButton({ val i = ids.indexOf(id); if (i < ids.size - 1) { val o = ids[i + 1]; spec = spec.copy(sections = spec.sections + ids.mapIndexed { j, x -> x to (spec.sections[x] ?: SectionSpec()).copy(order = (if (x == id) i + 2 else if (x == o) i + 1 else j + 1) * 10) }) } }) { Text("▼") }
                                            Switch(!s.hidden, { put(s.copy(hidden = !it)) })
                                        }
                                        Text("ขนาด ${(s.scale * 100).toInt()}%", fontSize = 12.sp)
                                        Slider(s.scale, { put(s.copy(scale = (it * 20).toInt() / 20f)) }, valueRange = 0.5f..2f)
                                    } }
                                }
                            }
                        }
                        1 -> {
                            val seen = UiPages.seenTexts[page] ?: emptyMap()
                            val keys = (seen.keys + spec.texts.keys).distinct().filter { k -> filter.isBlank() || (seen[k] ?: spec.texts[k]?.src ?: "").contains(filter, true) }
                            Column {
                                OutlinedTextField(filter, { filter = it }, label = { Text("ค้นหาข้อความ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                LazyColumn {
                                    if (keys.isEmpty()) item { Text("ยังไม่พบข้อความ — เปิดหน้านี้ในแอปก่อน", Modifier.padding(12.dp)) }
                                    items(keys) { k ->
                                        val src = seen[k] ?: spec.texts[k]?.src ?: ""
                                        val t = spec.texts[k] ?: TextSpec(src)
                                        fun put(n: TextSpec?) { spec = spec.copy(texts = if (n == null) spec.texts - k else spec.texts + (k to n.copy(src = src))) }
                                        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Column(Modifier.padding(8.dp)) {
                                            Text("เดิม: $src", fontSize = 11.sp, color = Color.Gray, maxLines = 2)
                                            OutlinedTextField(t.t ?: src, { put(t.copy(t = it)) }, label = { Text("ข้อความ") }, modifier = Modifier.fillMaxWidth())
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("ขนาด ${t.s?.toInt() ?: "เดิม"}", fontSize = 12.sp, modifier = Modifier.width(70.dp))
                                                Slider(t.s ?: 14f, { put(t.copy(s = it.toInt().toFloat())) }, valueRange = 8f..40f, modifier = Modifier.weight(1f))
                                            }
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                listOf(null to "น้ำหนักเดิม", 300 to "บาง", 400 to "ปกติ", 600 to "กึ่งหนา", 800 to "หนา").forEach { (w, l) -> FilterChip(t.w == w, { put(t.copy(w = w)) }, { Text(l, fontSize = 11.sp) }) }
                                                AssistChip({ colorFor = k }, { Text(t.c ?: "สีเดิม", fontSize = 11.sp, color = t.c?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: Color.Unspecified) })
                                                if (t.c != null) AssistChip({ put(t.copy(c = null)) }, { Text("ล้างสี", fontSize = 11.sp) })
                                                AssistChip({ put(null) }, { Text("คืนค่าเดิม", fontSize = 11.sp) })
                                            }
                                        } }
                                    }
                                }
                            }
                        }
                        else -> LazyColumn {
                            if (versions.isEmpty()) item { Text("ยังไม่มีเวอร์ชันที่เผยแพร่", Modifier.padding(12.dp)) }
                            items(versions) { (id, at) ->
                                Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("th")).format(java.util.Date(at)), modifier = Modifier.weight(1f))
                                    OutlinedButton({ scope.launch { val r = BrainApi.call(user, "POST", "/admin/ui/pages/$page/revert", JSONObject().put("versionId", id)); msg = if (r.code == 200) "ย้อนกลับแล้ว" else "ไม่สำเร็จ ${r.code}"; UiPages.refresh(); load() } }) { Text("ย้อนกลับเป็นเวอร์ชันนี้") }
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ spec = PageSpec() }) { Text("ล้างทั้งหน้า") }
                    OutlinedButton({ save(false) }) { Text("บันทึกร่าง") }
                    Button({ save(true) }, Modifier.weight(1f)) { Text("เผยแพร่หน้านี้") }
                }
            }
        }
    }
    colorFor?.let { k -> ColorPickerDialog("สีข้อความ", spec.texts[k]?.c ?: "#FF222222", onPick = { hex -> val src = UiPages.seenTexts[page]?.get(k) ?: spec.texts[k]?.src ?: ""; spec = spec.copy(texts = spec.texts + (k to (spec.texts[k] ?: TextSpec(src)).copy(c = hex, src = src))); colorFor = null }, onDismiss = { colorFor = null }) }
}
