package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.BrainApi
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONObject

/** On-screen WYSIWYG editor state (Super Admin). Reuses uiPages backend via UiPages.drafts. */
object UiEdit {
    data class Sel(val page: String, val key: String, val src: String) // key "s:<id>" = section, else text key
    var active by mutableStateOf(false)
    var preview by mutableStateOf(false)
    var selected by mutableStateOf<Sel?>(null)
    val undo = ArrayDeque<Map<String, PageSpec>>()
    fun snapshot() { undo.addLast(UiPages.drafts.toMap()); if (undo.size > 50) undo.removeFirst() }
    fun update(page: String, f: (PageSpec) -> PageSpec) { snapshot(); UiPages.drafts[page] = f(UiPages.spec(page)) }
}

/** Outline + tap-to-select that wins over the parent's click (Initial pass), so tapping a button's text edits it instead of navigating. */
fun Modifier.uiEditOutline(selected: Boolean, color: Color = Color(0x88FF4081), onSelect: () -> Unit): Modifier =
    this.border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFFFF4081) else color, RoundedCornerShape(3.dp))
        .pointerInput(onSelect) {
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial); down.consume()
                do { val e = awaitPointerEvent(PointerEventPass.Initial); e.changes.forEach { it.consume() } } while (e.changes.any { it.pressed })
                onSelect()
            }
        }

@Composable
fun UiEditFab(isSuper: Boolean, modifier: Modifier = Modifier) {
    if (!isSuper || UiEdit.active) return
    ExtendedFloatingActionButton(onClick = { UiEdit.active = true; UiEdit.preview = false; UiEdit.selected = null }, modifier = modifier) { Text("✏️ แก้ไข") }
}

/** Floating bar + bottom panel; place at the root over the app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UiEditOverlay() {
    if (!UiEdit.active) return
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var pickColor by remember { mutableStateOf(false) }
    fun saveAll(publish: Boolean) = scope.launch {
        val u = FirebaseAuth.getInstance().currentUser ?: return@launch
        if (UiPages.drafts.isEmpty()) { msg = "ยังไม่มีการแก้ไข"; return@launch }
        val res = UiPages.drafts.map { (p, sp) -> p to BrainApi.call(u, "PUT", "/admin/ui/pages/$p", JSONObject().put("page", sp.toJson()).put("publish", publish)) }
        val bad = res.filter { it.second.code != 200 }
        msg = if (bad.isEmpty()) { if (publish) { UiPages.refresh(); UiPages.drafts.clear(); UiEdit.undo.clear(); "เผยแพร่แล้ว ${res.size} หน้า" } else "บันทึกร่างแล้ว ${res.size} หน้า" }
              else "ไม่สำเร็จ: " + bad.joinToString { "${it.first} (${it.second.json?.optString("error") ?: it.second.code})" }
    }
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        // floating bar
        Surface(Modifier.align(Alignment.TopCenter).padding(top = 4.dp), shape = RoundedCornerShape(20.dp), color = Color(0xEE222222), shadowElevation = 6.dp) {
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val c = Color.White
                TextButton({ UiEdit.undo.removeLastOrNull()?.let { UiPages.drafts.clear(); UiPages.drafts.putAll(it) } }) { Text("↶", color = c) }
                TextButton({ UiEdit.preview = !UiEdit.preview; UiEdit.selected = null }) { Text(if (UiEdit.preview) "✏️ แก้" else "👁 ดู/ใช้งาน", color = c, fontSize = 12.sp) }
                TextButton({ saveAll(false) }) { Text("ร่าง", color = c, fontSize = 12.sp) }
                TextButton({ saveAll(true) }) { Text("เผยแพร่", color = Color(0xFFFF80AB), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                TextButton({ UiEdit.active = false; UiEdit.selected = null; UiPages.drafts.clear(); UiEdit.undo.clear() }) { Text("ออก", color = c, fontSize = 12.sp) }
            }
        }
        if (msg.isNotBlank()) Surface(Modifier.align(Alignment.TopCenter).padding(top = 52.dp), color = Color(0xEE333333), shape = RoundedCornerShape(10.dp)) {
            Text(msg, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
        if (UiEdit.preview && UiEdit.selected == null) Text("โหมดดู/ใช้งาน: กดปุ่มนำทางได้ตามปกติ · กด ✏️ ที่แถบเพื่อกลับไปแก้", fontSize = 11.sp, color = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).background(Color(0xAA000000)).padding(6.dp))
        val sel = UiEdit.selected ?: return@Box
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding(), shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), shadowElevation = 12.dp) {
            Column(Modifier.padding(12.dp)) {
                val page = sel.page; val spec = UiPages.spec(page)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${UiPages.PAGES[page] ?: page} · " + if (sel.key.startsWith("s:")) "ส่วน: ${sel.src}" else "ข้อความ", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton({ UiEdit.selected = null }) { Text("✕") }
                }
                if (sel.key.startsWith("s:")) {
                    val id = sel.key.removePrefix("s:"); val s = spec.sections[id] ?: SectionSpec()
                    fun put(n: SectionSpec) = UiEdit.update(page) { it.copy(sections = it.sections + (id to n)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("แสดง", modifier = Modifier.weight(1f)); Switch(!s.hidden, { put(s.copy(hidden = !it)) })
                    }
                    Text("ขนาด ${(s.scale * 100).toInt()}%", fontSize = 12.sp)
                    Slider(s.scale, { put(s.copy(scale = (it * 20).toInt() / 20f)) }, valueRange = 0.5f..2f)
                    Row { OutlinedButton({ put(s.copy(order = (s.order.takeIf { it > 0 } ?: 500) - 15)) }) { Text("▲ เลื่อนขึ้น") }; Spacer(Modifier.width(8.dp))
                        OutlinedButton({ put(s.copy(order = (s.order.takeIf { it > 0 } ?: 500) + 15)) }) { Text("▼ เลื่อนลง") }; Spacer(Modifier.width(8.dp))
                        TextButton({ UiEdit.update(page) { it.copy(sections = it.sections - id) } }) { Text("คืนค่า") } }
                } else {
                    val k = sel.key; val t = spec.texts[k] ?: TextSpec(sel.src)
                    fun put(n: TextSpec?) = UiEdit.update(page) { it.copy(texts = if (n == null) it.texts - k else it.texts + (k to n.copy(src = sel.src))) }
                    OutlinedTextField(t.t ?: sel.src, { put(t.copy(t = it)) }, label = { Text("ข้อความ") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ขนาด ${t.s?.toInt() ?: "เดิม"}", fontSize = 12.sp, modifier = Modifier.width(64.dp))
                        Slider(t.s ?: 14f, { put(t.copy(s = it.toInt().toFloat())) }, valueRange = 8f..40f, modifier = Modifier.weight(1f))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(null to "เดิม", 300 to "บาง", 400 to "ปกติ", 600 to "กึ่งหนา", 800 to "หนา").forEach { (w, l) -> FilterChip(t.w == w, { put(t.copy(w = w)) }, { Text(l, fontSize = 11.sp) }) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("#FF111111", "#FFFFFFFF", "#FFEC4899", "#FF6366F1", "#FF10B981", "#FFF59E0B", "#FFEF4444").forEach { h ->
                            Box(Modifier.size(26.dp).background(Color(android.graphics.Color.parseColor(h)), RoundedCornerShape(13.dp)).border(1.dp, Color.Gray, RoundedCornerShape(13.dp))
                                .uiEditOutline(t.c == h, Color.Transparent) { put(t.copy(c = h)) }) }
                        AssistChip({ pickColor = true }, { Text("🎨 สีอื่น", fontSize = 11.sp) })
                        AssistChip({ put(t.copy(c = null)) }, { Text("สีเดิม", fontSize = 11.sp) })
                        AssistChip({ put(t.copy(t = "")) }, { Text("ซ่อน", fontSize = 11.sp) })
                        AssistChip({ put(null) }, { Text("คืนค่าเดิม", fontSize = 11.sp) })
                    }
                    if (pickColor) com.example.ui.admin.ColorPickerDialog("สีข้อความ", t.c ?: "#FF222222", onPick = { put(t.copy(c = it)); pickColor = false }, onDismiss = { pickColor = false })
                }
            }
        }
    }
}
