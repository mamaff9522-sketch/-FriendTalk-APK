package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import com.example.network.BrainApi
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.security.MessageDigest

data class SectionSpec(val hidden: Boolean = false, val order: Int = 0, val scale: Float = 1f)
data class TextSpec(val src: String = "", val t: String? = null, val s: Float? = null, val c: String? = null, val w: Int? = null)
data class PageSpec(val sections: Map<String, SectionSpec> = emptyMap(), val texts: Map<String, TextSpec> = emptyMap()) {
    fun toJson(): JSONObject {
        val sj = JSONObject(); sections.forEach { (k, v) -> sj.put(k, JSONObject().put("hidden", v.hidden).put("order", v.order).put("scale", v.scale.toDouble())) }
        val tj = JSONObject(); texts.forEach { (k, v) -> val o = JSONObject().put("src", v.src); v.t?.let { o.put("t", it) }; v.s?.let { o.put("s", it.toDouble()) }; v.c?.let { o.put("c", it) }; v.w?.let { o.put("w", it) }; tj.put(k, o) }
        return JSONObject().put("sections", sj).put("texts", tj)
    }
    companion object {
        fun from(j: JSONObject?): PageSpec {
            if (j == null) return PageSpec()
            val s = j.optJSONObject("sections"); val t = j.optJSONObject("texts")
            return PageSpec(
                s?.keys()?.asSequence()?.associate { k -> val o = s.getJSONObject(k); k to SectionSpec(o.optBoolean("hidden"), o.optInt("order"), o.optDouble("scale", 1.0).toFloat()) } ?: emptyMap(),
                t?.keys()?.asSequence()?.associate { k -> val o = t.getJSONObject(k)
                    k to TextSpec(o.optString("src"), if (o.has("t")) o.optString("t") else null, if (o.has("s")) o.optDouble("s").toFloat() else null,
                        if (o.has("c")) o.optString("c") else null, if (o.has("w")) o.optInt("w") else null) } ?: emptyMap())
        }
    }
}

/** Server-driven per-page UI (uiPages/{page}). Pages: see PAGES. Texts keyed by sha1 of their default text, page-scoped then 'global'. */
object UiPages {
    val PAGES = linkedMapOf("global" to "ทุกหน้า (ข้อความร่วม)", "header" to "แถบบน", "nav" to "แถบล่าง", "home" to "หน้าแรก", "discover" to "หาเพื่อน", "live" to "ไลฟ์",
        "movie" to "วิดีโอ", "chat" to "รายการแชท", "chatroom" to "ห้องแชท", "companion" to "เพื่อนคุย", "ads" to "โฆษณา", "profile" to "โปรไฟล์",
        "shop" to "ร้านค้า", "ai" to "AI บอท", "feed" to "ฟีด/โพสต์", "wallet" to "กระเป๋า/ถอนเงิน")
    var pages by mutableStateOf<Map<String, PageSpec>>(emptyMap())
    /** Builder preview: while editing, the draft of one page overrides the published one. */
    var draft by mutableStateOf<Pair<String, PageSpec>?>(null)
    /** WYSIWYG edit mode drafts (page -> spec), applied live while editing. */
    val drafts = mutableStateMapOf<String, PageSpec>()
    // Plain (non-snapshot) registries: written during composition, read by the builder when opened.
    val seenTexts = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()   // page -> key -> default text
    val seenSections = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>() // page -> id -> label
    val orderOf = java.util.concurrent.ConcurrentHashMap<String, List<String>>() // page -> reorderable section ids in render order
    fun spec(page: String): PageSpec = drafts[page] ?: draft?.takeIf { it.first == page }?.second ?: pages[page] ?: PageSpec()
    private val keyCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    fun key(text: String): String = keyCache.getOrPut(text) { MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(16) }
    suspend fun refresh() {
        val u = FirebaseAuth.getInstance().currentUser ?: return
        val r = BrainApi.call(u, "GET", "/ui/pages"); val p = r.json?.optJSONObject("pages") ?: return
        pages = p.keys().asSequence().associateWith { PageSpec.from(p.optJSONObject(it)) }
    }
    fun textFor(page: String, text: String): TextSpec? {
        if (text.isBlank() || text.length > 300) return null
        val k = key(text)
        val m = seenTexts[page]; if (m == null || !m.containsKey(k)) seenTexts[page] = (m ?: emptyMap()) + (k to text)
        return spec(page).texts[k] ?: spec("global").texts[k]
    }
}

val LocalUiPage = staticCompositionLocalOf { "global" }

@Composable
fun UiPage(page: String, content: @Composable () -> Unit) = CompositionLocalProvider(LocalUiPage provides page, content = content)

/** A named section of the current page: admin can hide it or resize it (scales density + font). */
@Composable
fun UiSection(id: String, label: String, content: @Composable () -> Unit) {
    val page = LocalUiPage.current
    run { val m = UiPages.seenSections[page]; if (m == null || m[id] != label) UiPages.seenSections[page] = (m ?: emptyMap()) + (id to label) }
    val s = UiPages.spec(page).sections[id] ?: SectionSpec()
    if (UiEdit.active && !UiEdit.preview) {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.border(1.dp, androidx.compose.ui.graphics.Color(0x8800AAFF)).then(if (s.hidden) androidx.compose.ui.Modifier.alpha(0.3f) else androidx.compose.ui.Modifier)) {
            scaled(s.scale) { content() }
            SectionHandle(page, id, label, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopEnd))
        }
        return
    }
    if (s.hidden) return
    scaled(s.scale) { content() }
}

@Composable
private fun scaled(scale: Float, content: @Composable () -> Unit) {
    if (scale == 1f) { content(); return }
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density * scale, d.fontScale)) { content() }
}

/** Ordered list of sections: admin reorders/hides/resizes. */
@Composable
fun ColumnScope.UiSections(items: List<Triple<String, String, @Composable () -> Unit>>) {
    val page = LocalUiPage.current; val sp = UiPages.spec(page).sections
    val sorted = items.withIndex().sortedBy { (i, it) -> sp[it.first]?.order?.takeIf { o -> o > 0 } ?: (i + 1) * 10 }.map { it.value }
    UiPages.orderOf[page] = sorted.map { it.first }
    sorted.forEach { UiSection(it.first, it.second, it.third) }
}

/** LazyColumn item that is an editable section (hide/resize in edit mode). */
fun androidx.compose.foundation.lazy.LazyListScope.uiItem(id: String, label: String, content: @Composable androidx.compose.foundation.lazy.LazyItemScope.() -> Unit) =
    item(key = "ui_$id") { val sc = this; UiSection(id, label) { sc.content() } }

/** Small handle on a section (edit mode): tap = select, long-press + drag = reorder. Content stays tappable for text editing. */
@Composable
fun SectionHandle(page: String, id: String, label: String, modifier: androidx.compose.ui.Modifier) {
    val sel = UiEdit.Sel(page, "s:" + id, label)
    var dy by remember { mutableStateOf(0f) }
    val stepPx = with(LocalDensity.current) { 80.dp.toPx() }
    androidx.compose.material3.Surface(modifier.padding2().pointerInput(id) {
        detectTapGestures(onTap = { UiEdit.selected = sel }) }
        .pointerInput(id + "d") { detectDragGesturesAfterLongPress(onDragStart = { dy = 0f; UiEdit.selected = sel }, onDrag = { ch, d -> ch.consume(); dy += d.y },
            onDragEnd = { val steps = kotlin.math.round(dy / stepPx).toInt(); if (steps != 0) UiEdit.move(page, id, steps); dy = 0f }) }
        .graphicsLayer { translationY = dy },
        color = if (UiEdit.selected == sel) androidx.compose.ui.graphics.Color(0xFFFF4081) else androidx.compose.ui.graphics.Color(0xCC0088FF),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
        androidx.compose.material3.Text("⠿ " + label.take(14), color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp,
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
    }
}
private fun androidx.compose.ui.Modifier.padding2() = this.padding(2.dp)

/** Column whose direct children become reorderable/hideable/resizable sections automatically (ids prefix_0..n by code order). */
@Composable
fun UiAutoColumn(prefix: String, label: String, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier, spacing: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    val page = LocalUiPage.current
    val editing = UiEdit.active && !UiEdit.preview
    val sp = UiPages.spec(page).sections
    androidx.compose.ui.layout.SubcomposeLayout(modifier) { c ->
        val ms = subcompose("content", content)
        val ids = ms.indices.map { "${prefix}_$it" }
        val order = ids.withIndex().sortedBy { (i, x) -> sp[x]?.order?.takeIf { o -> o > 0 } ?: (i + 1) * 10 }.map { it.index }
        UiPages.orderOf[page] = order.map { ids[it] }
        UiPages.seenSections[page] = (UiPages.seenSections[page] ?: emptyMap()) + ids.mapIndexed { i, x -> x to "$label ${i + 1}" }
        val cc = c.copy(minHeight = 0) // keep maxHeight: lazy lists inside need bounded height
        val gap = spacing.roundToPx()
        data class P(val i: Int, val pl: androidx.compose.ui.layout.Placeable, val sc: Float, val h: Int, val hidden: Boolean)
        val ps = order.mapNotNull { i -> val s = sp[ids[i]] ?: SectionSpec(); if (s.hidden && !editing) null else { val pl = ms[i].measure(cc); P(i, pl, s.scale, (pl.height * s.scale).toInt(), s.hidden) } }
        val handles = if (editing) ps.map { p -> subcompose("h_" + ids[p.i]) { SectionHandle(page, ids[p.i], "$label ${p.i + 1}", androidx.compose.ui.Modifier) }.map { it.measure(androidx.compose.ui.unit.Constraints()) } } else emptyList()
        val total = ps.sumOf { it.h } + gap * (ps.size - 1).coerceAtLeast(0)
        val w = if (c.hasBoundedWidth) c.maxWidth else (ps.maxOfOrNull { it.pl.width } ?: 0)
        layout(w, total.coerceAtLeast(c.minHeight)) {
            var y = 0
            ps.forEachIndexed { k, p ->
                p.pl.placeWithLayer(0, y) { scaleX = p.sc; scaleY = p.sc; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f); alpha = if (p.hidden) 0.3f else 1f }
                if (editing) handles[k].forEach { it.place(w - it.width, y) }
                y += p.h + gap
            }
        }
    }
}
