package com.example.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.data.MockData
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UiBlock
import com.example.model.UiLayout
import com.example.network.BrainApi
import com.example.service.UiLayoutRepository
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class VersionInfo(val id: String, val savedAt: Long, val savedBy: String, val blockCount: Int)

private val SECTION_LABELS = mapOf(
    "tabs" to "แท็บหมวดหมู่", "banners" to "แบนเนอร์ประกาศ", "stories" to "สตอรี่", "clubs" to "คลับ",
    "sdc" to "การ์ดจากเซิร์ฟเวอร์", "feed" to "ฟีดโพสต์"
)

private fun newBlock(type: String): UiBlock {
    val id = "b" + System.currentTimeMillis().toString(36)
    return when (type) {
        "image" -> UiBlock(id, "image", heightDp = 160, imageUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=800&auto=format&fit=crop&q=80")
        "frame" -> UiBlock(id, "frame", heightDp = 80, bgColorHex = "#1E293B", text = "กรอบข้อความ")
        "text" -> UiBlock(id, "text", text = "ข้อความใหม่", fontSizeSp = 16)
        "button" -> UiBlock(id, "button", text = "ปุ่ม", heightDp = 48, bgColorHex = "#EC4899", cornerRadiusDp = 24)
        "banner" -> UiBlock(id, "banner", text = "ประกาศใหม่ 🎉", heightDp = 90, bgColorHex = "#DB2777", fontSizeSp = 18)
        else -> UiBlock(id, "spacer", heightDp = 16)
    }
}

/** Admin drag-and-drop editor for the Home screen layout; saved/published on friendtalk-brain. */
@Composable
fun HomeLayoutEditor() {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var layout by remember { mutableStateOf(UiLayout.defaultHome()) }
    val undo = remember { mutableStateListOf<UiLayout>() }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf("edit") } // edit | preview | history
    var versions by remember { mutableStateOf<List<VersionInfo>>(emptyList()) }
    var msg by remember { mutableStateOf("กำลังโหลด…") }
    var busy by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var canvasWidthPx by remember { mutableStateOf(1) }
    var canvasTop by remember { mutableStateOf(0f) }
    var canvasBottom by remember { mutableStateOf(0f) }
    val canvasScroll = rememberScrollState()

    fun commit(newLayout: UiLayout, recordUndo: Boolean = true) {
        if (recordUndo) { undo.add(layout); if (undo.size > 30) undo.removeAt(0) }
        layout = newLayout
    }
    fun updateBlock(id: String, recordUndo: Boolean = true, f: (UiBlock) -> UiBlock) =
        commit(layout.copy(blocks = layout.blocks.map { if (it.id == id) f(it) else it }), recordUndo)
    fun move(from: Int, to: Int) {
        if (from !in layout.blocks.indices || to !in layout.blocks.indices) return
        val l = layout.blocks.toMutableList(); val b = l.removeAt(from); l.add(to, b)
        layout = layout.copy(blocks = l)
    }

    suspend fun load() {
        val fu = FirebaseAuth.getInstance().currentUser ?: run { msg = "ยังไม่ได้เข้าสู่ระบบ"; return }
        val r = BrainApi.call(fu, "GET", "/admin/ui/layout/home")
        if (r.code != 200) { msg = "โหลดไม่ได้ (${r.code}) ต้องมีสิทธิ์ config_edit"; return }
        val j = r.json
        layout = UiLayout.fromJson(j?.optJSONObject("draft")) ?: UiLayout.fromJson(j?.optJSONObject("current")) ?: UiLayout.defaultHome()
        val arr = j?.optJSONArray("versions")
        versions = if (arr == null) emptyList() else (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            VersionInfo(o.optString("id"), o.optLong("savedAt"), o.optString("savedBy"), o.optInt("blockCount"))
        }
        undo.clear(); msg = ""
    }
    LaunchedEffect(Unit) { load() }

    fun save(publish: Boolean) {
        val fu = FirebaseAuth.getInstance().currentUser ?: return
        busy = true
        scope.launch {
            val body = JSONObject().put("layout", layout.toJson()).put("publish", publish)
            val r = BrainApi.call(fu, "PUT", "/admin/ui/layout/home", body)
            busy = false
            msg = when {
                r.code == 200 && publish -> "เผยแพร่แล้ว ผู้ใช้จะเห็นเมื่อเปิดแอปหรือดึงหน้าเพื่อรีเฟรช"
                r.code == 200 -> "บันทึกฉบับร่างแล้ว (ยังไม่เผยแพร่)"
                else -> "บันทึกไม่สำเร็จ: ${r.json?.optString("error") ?: r.code}"
            }
            if (r.code == 200) {
                if (publish) UiLayoutRepository.setLocal(layout)
                load(); if (r.code == 200) msg = if (publish) "เผยแพร่แล้ว ผู้ใช้จะเห็นเมื่อเปิดแอปหรือดึงหน้าเพื่อรีเฟรช" else "บันทึกฉบับร่างแล้ว (ยังไม่เผยแพร่)"
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Toolbar
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                SmallBtn("+ เพิ่ม") { addMenu = true }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    listOf("image" to "รูปภาพ", "frame" to "กรอบ", "text" to "ข้อความ", "button" to "ปุ่ม", "banner" to "แบนเนอร์", "spacer" to "ช่องว่าง").forEach { (t, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            addMenu = false
                            val b = newBlock(t); commit(layout.copy(blocks = listOf(b) + layout.blocks)); selectedId = b.id
                        })
                    }
                    UiBlock.SECTIONS.filter { s -> layout.blocks.none { it.type == "section" && it.section == s } }.forEach { s ->
                        DropdownMenuItem(text = { Text("ส่วนเดิม: ${SECTION_LABELS[s]}") }, onClick = {
                            addMenu = false
                            commit(layout.copy(blocks = layout.blocks + UiBlock.sectionBlock(s)))
                        })
                    }
                }
            }
            SmallBtn("↶ ย้อน", enabled = undo.isNotEmpty()) { layout = undo.removeAt(undo.size - 1) }
            SmallBtn(if (mode == "preview") "แก้ไข" else "ดูตัวอย่าง") { mode = if (mode == "preview") "edit" else "preview"; selectedId = null }
            SmallBtn("ประวัติ") { mode = if (mode == "history") "edit" else "history" }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallBtn("บันทึกร่าง", enabled = !busy) { save(false) }
            SmallBtn("เผยแพร่", enabled = !busy, primary = true) { save(true) }
            SmallBtn("เริ่มใหม่") { commit(UiLayout.defaultHome()); selectedId = null }
        }
        if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
        Text(
            if (mode == "edit") "ลากที่ ≡ เพื่อย้าย · แตะบล็อกเพื่อเลือกและแก้คุณสมบัติ · ลาก ⤡ เพื่อปรับขนาด" else "",
            color = Slate400, fontSize = 10.sp
        )

        if (mode == "history") {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (versions.isEmpty()) Text("ยังไม่มีเวอร์ชันที่เผยแพร่", color = Slate400, fontSize = 12.sp)
                val fmt = SimpleDateFormat("d MMM yyyy HH:mm", Locale("th", "TH"))
                versions.forEach { v ->
                    Surface(shape = RoundedCornerShape(10.dp), color = Slate800, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(fmt.format(Date(v.savedAt)), color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("${v.blockCount} บล็อก · โดย ${v.savedBy.take(8)}", color = Slate400, fontSize = 10.sp)
                            }
                            SmallBtn("ย้อนกลับเป็นเวอร์ชันนี้", enabled = !busy) {
                                val fu = FirebaseAuth.getInstance().currentUser ?: return@SmallBtn
                                busy = true
                                scope.launch {
                                    val r = BrainApi.call(fu, "POST", "/admin/ui/layout/home/revert", JSONObject().put("versionId", v.id))
                                    busy = false
                                    if (r.code == 200) { load(); UiLayoutRepository.setLocal(layout); msg = "ย้อนกลับและเผยแพร่แล้ว"; mode = "edit" }
                                    else msg = "ย้อนกลับไม่สำเร็จ (${r.code})"
                                }
                            }
                        }
                    }
                }
            }
            return@Column
        }

        // Phone canvas
        Surface(
            shape = RoundedCornerShape(18.dp), color = Slate950,
            modifier = Modifier.fillMaxWidth().weight(1f).border(2.dp, Slate700, RoundedCornerShape(18.dp))
        ) {
            Column(
                Modifier.fillMaxSize().onSizeChanged { canvasWidthPx = maxOf(1, it.width) }
                    .onGloballyPositioned { val y = it.positionInWindow().y; canvasTop = y; canvasBottom = y + it.size.height }
                    .verticalScroll(canvasScroll).padding(vertical = 8.dp)
            ) {
                layout.blocks.forEach { b ->
                    key(b.id) {
                        if (mode == "preview") {
                            if (b.visible) {
                                if (b.type == "section") SectionPlaceholder(b, editing = false) else LayoutBlockView(b, interactive = false)
                            }
                        } else {
                            EditableBlock(
                                block = b,
                                selected = selectedId == b.id,
                                canvasTop = canvasTop,
                                canvasBottom = canvasBottom,
                                onAutoScroll = { d -> canvasScroll.dispatchRawDelta(d) },
                                onSelect = { selectedId = if (selectedId == b.id) null else b.id },
                                onDragStart = { undo.add(layout); if (undo.size > 30) undo.removeAt(0) },
                                onDragSwap = { dir ->
                                    val i = layout.blocks.indexOfFirst { it.id == b.id }
                                    move(i, i + dir)
                                },
                                onStep = { dir ->
                                    val i = layout.blocks.indexOfFirst { it.id == b.id }
                                    if (i + dir in layout.blocks.indices) { undo.add(layout); move(i, i + dir) }
                                },
                                onResize = { dx, dy ->
                                    updateBlock(b.id, recordUndo = false) {
                                        val w = (it.widthFraction + dx / canvasWidthPx).coerceIn(0.2f, 1f)
                                        val baseH = if (it.heightDp > 0) it.heightDp else 48
                                        val h = (baseH + with(density) { dy.toDp().value }).toInt().coerceIn(8, 800)
                                        it.copy(widthFraction = w, heightDp = h)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Properties panel
        val sel = layout.blocks.firstOrNull { it.id == selectedId }
        if (mode == "edit" && sel != null) {
            PropertiesPanel(
                block = sel,
                onChange = { f -> updateBlock(sel.id, f = f) },
                onDelete = { commit(layout.copy(blocks = layout.blocks.filter { it.id != sel.id })); selectedId = null },
                onMove = { dir ->
                    val i = layout.blocks.indexOfFirst { it.id == sel.id }
                    if (i + dir in layout.blocks.indices) { undo.add(layout); move(i, i + dir) }
                }
            )
        }
    }
}

@Composable
private fun SmallBtn(label: String, enabled: Boolean = true, primary: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (!enabled) Slate800.copy(alpha = 0.5f) else if (primary) Pink500 else Slate800,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(label, color = if (enabled) White else Slate400, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
    }
}

@Composable
private fun SectionPlaceholder(b: UiBlock, editing: Boolean) {
    // Miniature of the real built-in section, with the block's size/padding/radius/background applied
    Box(Modifier.alpha(if (b.visible) 1f else 0.4f)) {
        SectionStyleBox(b) { SectionMiniPreview(b.section) }
        if (editing && !b.visible) Text("ซ่อนอยู่", color = Amber400, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
}

@Composable
private fun SectionMiniPreview(section: String) {
    val title = SECTION_LABELS[section] ?: section
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(title, color = Slate400, fontSize = 9.sp)
        Spacer(Modifier.height(3.dp))
        when (section) {
            "tabs" -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ทั้งหมด", "คนใกล้เคียง", "ออนไลน์", "หาเพื่อน").forEachIndexed { i, t ->
                    Surface(shape = RoundedCornerShape(14.dp), color = if (i == 0) Pink500 else Slate800) {
                        Text(t, color = White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
            }
            "banners" -> Surface(shape = RoundedCornerShape(12.dp), color = Pink600, modifier = Modifier.fillMaxWidth()) {
                Text("แบนเนอร์ประกาศ (จากหน้า Config)", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp))
            }
            "stories" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MockData.initialStories.take(5).forEach { st ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(model = st.userAvatar, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(CircleShape).border(2.dp, Pink500, CircleShape))
                        Text(st.userName.take(6), color = Slate300, fontSize = 8.sp)
                    }
                }
            }
            "clubs" -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MockData.initialClubs.take(3).forEach { c ->
                    Surface(shape = RoundedCornerShape(10.dp), color = Slate800) {
                        Text("${c.icon} ${c.name.take(10)}", color = White, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
                    }
                }
            }
            "sdc" -> Surface(shape = RoundedCornerShape(12.dp), color = Slate800, modifier = Modifier.fillMaxWidth()) {
                Text("การ์ดจากเซิร์ฟเวอร์ (ถ้ามี)", color = Slate300, fontSize = 11.sp, modifier = Modifier.padding(10.dp))
            }
            "feed" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MockData.initialPosts.take(2).forEach { p ->
                    Surface(shape = RoundedCornerShape(12.dp), color = Slate800, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = p.authorAvatar, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(28.dp).clip(CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(p.authorName, color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(p.content.take(60), color = Slate300, fontSize = 9.sp, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableBlock(
    block: UiBlock,
    selected: Boolean,
    canvasTop: Float,
    canvasBottom: Float,
    onAutoScroll: (Float) -> Unit,
    onSelect: () -> Unit,
    onDragStart: () -> Unit,
    onDragSwap: (Int) -> Unit,
    onStep: (Int) -> Unit,
    onResize: (Float, Float) -> Unit
) {
    var dragY by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var heightPx by remember { mutableStateOf(1) }
    var winY by remember { mutableStateOf(0f) }
    val currentOnSwap by rememberUpdatedState(onDragSwap)
    val currentOnResize by rememberUpdatedState(onResize)
    val currentAutoScroll by rememberUpdatedState(onAutoScroll)
    val currentTop by rememberUpdatedState(canvasTop)
    val currentBottom by rememberUpdatedState(canvasBottom)

    fun dragBy(dy: Float) {
        dragY += dy
        val threshold = heightPx * 0.55f
        if (dragY > threshold) { currentOnSwap(1); dragY -= heightPx }
        else if (dragY < -threshold) { currentOnSwap(-1); dragY += heightPx }
        val y = winY + dragY
        if (y < currentTop + 80f) currentAutoScroll(-14f)
        else if (y + heightPx > currentBottom - 80f) currentAutoScroll(14f)
    }

    Box(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .onSizeChanged { heightPx = maxOf(1, it.height) }
            .onGloballyPositioned { winY = it.positionInWindow().y - dragY }
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragY; shadowElevation = if (dragging) 16f else 0f }
            .border(
                width = if (selected || dragging) 2.dp else 1.dp,
                color = if (selected || dragging) Cyan400 else Slate700.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
            .background(if (dragging) Slate900 else Color.Transparent, RoundedCornerShape(8.dp))
            .pointerInput(block.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = true; onDragStart() },
                    onDragEnd = { dragging = false; dragY = 0f },
                    onDragCancel = { dragging = false; dragY = 0f },
                    onDrag = { change, amount -> change.consume(); dragBy(amount.y) }
                )
            }
            .clickable { onSelect() }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Drag handle: drag starts immediately here
            Box(
                Modifier.width(36.dp).heightIn(min = 44.dp)
                    .background(if (dragging) Cyan400.copy(alpha = 0.3f) else Slate800, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .pointerInput(block.id + "_handle") {
                        detectDragGestures(
                            onDragStart = { dragging = true; onDragStart() },
                            onDragEnd = { dragging = false; dragY = 0f },
                            onDragCancel = { dragging = false; dragY = 0f },
                            onDrag = { change, amount -> change.consume(); dragBy(amount.y) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) { Text("≡", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            Box(Modifier.weight(1f)) {
                if (block.type == "section") SectionPlaceholder(block, editing = true)
                else Box(Modifier.alpha(if (block.visible) 1f else 0.4f)) { LayoutBlockView(block, interactive = false) }
            }
            Column {
                Text("▲", color = Slate300, fontSize = 14.sp, modifier = Modifier.clickable { onStep(-1) }.padding(horizontal = 8.dp, vertical = 4.dp))
                Text("▼", color = Slate300, fontSize = 14.sp, modifier = Modifier.clickable { onStep(1) }.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        if (selected) {
            // Corner resize handle (width + height)
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 34.dp, bottom = 2.dp).size(28.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Cyan400)
                    .pointerInput(block.id + "_resize") {
                        detectDragGestures { change, amount -> change.consume(); currentOnResize(amount.x, amount.y) }
                    }
            ) { Text("⤡", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center)) }
            Text(block.type.let { if (it == "section") "ส่วนเดิม" else it }, color = Color.Black, fontSize = 9.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 34.dp, top = 2.dp).background(Cyan400, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun PropertiesPanel(block: UiBlock, onChange: ((UiBlock) -> UiBlock) -> Unit, onDelete: () -> Unit, onMove: (Int) -> Unit) {
    Surface(color = Slate900, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(top = 6.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (block.type == "section") "ส่วนเดิม: ${SECTION_LABELS[block.section]}" else "บล็อก: ${block.type}",
                    color = White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("แสดง", color = Slate300, fontSize = 11.sp)
                Switch(checked = block.visible, onCheckedChange = { v -> onChange { it.copy(visible = v) } })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallBtn("▲ ขึ้น") { onMove(-1) }
                SmallBtn("▼ ลง") { onMove(1) }
                SmallBtn("🗑 ลบ") { onDelete() }
            }
            if (block.type == "section") {
                Text("ปรับขนาด ระยะขอบ มุมโค้ง และสีพื้นของส่วนเดิมได้", color = Slate400, fontSize = 10.sp)
                PropField("สีพื้น (#RRGGBB ว่าง = ไม่มี)", block.bgColorHex) { v -> onChange { it.copy(bgColorHex = v.trim().take(9)) } }
                PropSlider("ความกว้าง ${(block.widthFraction * 100).toInt()}%", block.widthFraction, 0.2f..1f) { v -> onChange { it.copy(widthFraction = v) } }
                PropSlider("ความสูงสูงสุด ${if (block.heightDp == 0) "อัตโนมัติ" else "${block.heightDp}dp"}", block.heightDp.toFloat(), 0f..400f) { v -> onChange { it.copy(heightDp = v.toInt()) } }
                PropSlider("มุมโค้ง ${block.cornerRadiusDp}dp", block.cornerRadiusDp.toFloat(), 0f..40f) { v -> onChange { it.copy(cornerRadiusDp = v.toInt()) } }
                PropSlider("ระยะขอบ ${block.paddingDp}dp", block.paddingDp.toFloat(), 0f..40f) { v -> onChange { it.copy(paddingDp = v.toInt()) } }
            }
            if (block.type != "section") {
                if (block.type in setOf("text", "button", "banner", "frame", "image")) PropField("ข้อความ", block.text) { v -> onChange { it.copy(text = v.take(500)) } }
                if (block.type in setOf("image", "banner")) PropField("ลิงก์รูป (https://)", block.imageUrl) { v -> onChange { it.copy(imageUrl = v.trim()) } }
                if (block.type in setOf("button", "banner", "image")) PropField("ลิงก์เมื่อกด (https://)", block.linkUrl) { v -> onChange { it.copy(linkUrl = v.trim()) } }
                PropField("สีพื้น (#RRGGBB)", block.bgColorHex) { v -> onChange { it.copy(bgColorHex = v.trim().take(9)) } }
                PropField("สีตัวอักษร (#RRGGBB)", block.textColorHex) { v -> onChange { it.copy(textColorHex = v.trim().take(9)) } }
                PropSlider("ความกว้าง ${(block.widthFraction * 100).toInt()}%", block.widthFraction, 0.2f..1f) { v -> onChange { it.copy(widthFraction = v) } }
                PropSlider("ความสูง ${if (block.heightDp == 0) "อัตโนมัติ" else "${block.heightDp}dp"}", block.heightDp.toFloat(), 0f..400f) { v -> onChange { it.copy(heightDp = v.toInt()) } }
                PropSlider("มุมโค้ง ${block.cornerRadiusDp}dp", block.cornerRadiusDp.toFloat(), 0f..40f) { v -> onChange { it.copy(cornerRadiusDp = v.toInt()) } }
                PropSlider("ขนาดตัวอักษร ${block.fontSizeSp}sp", block.fontSizeSp.toFloat(), 8f..40f) { v -> onChange { it.copy(fontSizeSp = v.toInt()) } }
                PropSlider("ระยะขอบใน ${block.paddingDp}dp", block.paddingDp.toFloat(), 0f..40f) { v -> onChange { it.copy(paddingDp = v.toInt()) } }
            }
        }
    }
}

@Composable
private fun PropField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValue, label = { Text(label, fontSize = 10.sp) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White)
    )
}

@Composable
private fun PropSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Text(label, color = Slate300, fontSize = 10.sp)
    Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValue, valueRange = range)
}
