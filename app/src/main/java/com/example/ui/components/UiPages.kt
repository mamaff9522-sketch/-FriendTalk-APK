package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
    // Plain (non-snapshot) registries: written during composition, read by the builder when opened.
    val seenTexts = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()   // page -> key -> default text
    val seenSections = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>() // page -> id -> label
    fun spec(page: String): PageSpec = draft?.takeIf { it.first == page }?.second ?: pages[page] ?: PageSpec()
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
    if (s.hidden) return
    if (s.scale == 1f) { content(); return }
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density * s.scale, d.fontScale)) { content() }
}

/** Ordered list of sections: admin reorders/hides/resizes. */
@Composable
fun ColumnScope.UiSections(items: List<Triple<String, String, @Composable () -> Unit>>) {
    val page = LocalUiPage.current; val sp = UiPages.spec(page).sections
    items.withIndex().sortedBy { (i, it) -> sp[it.first]?.order?.takeIf { o -> o > 0 } ?: (i + 1) * 10 }.forEach { (_, it) -> UiSection(it.first, it.second, it.third) }
}
