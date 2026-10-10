package com.example.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Server-driven screen layout made in the admin UI Builder.
 * Blocks are stacked top-to-bottom in list order (order = position).
 * type: image | frame | text | button | banner | spacer | section
 * section (when type == "section"): tabs | banners | stories | clubs | sdc | feed  (existing built-in parts)
 */
data class UiBlock(
    val id: String,
    val type: String,
    val section: String = "",
    val visible: Boolean = true,
    val widthFraction: Float = 1f,   // 0.2 .. 1.0 of screen width
    val heightDp: Int = 0,           // 0 = wrap content
    val cornerRadiusDp: Int = 12,
    val bgColorHex: String = "",
    val textColorHex: String = "#FFFFFF",
    val text: String = "",
    val fontSizeSp: Int = 14,
    val paddingDp: Int = 12,
    val imageUrl: String = "",
    val linkUrl: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("type", type).put("section", section).put("visible", visible)
        .put("widthFraction", widthFraction.toDouble()).put("heightDp", heightDp).put("cornerRadiusDp", cornerRadiusDp)
        .put("bgColorHex", bgColorHex).put("textColorHex", textColorHex).put("text", text)
        .put("fontSizeSp", fontSizeSp).put("paddingDp", paddingDp).put("imageUrl", imageUrl).put("linkUrl", linkUrl)

    companion object {
        val TYPES = setOf("image", "frame", "text", "button", "banner", "spacer", "section")
        /** Built-in section with neutral styling (looks exactly like the built-in Home). */
        fun sectionBlock(section: String) = UiBlock(id = "sec_$section", type = "section", section = section, paddingDp = 0, cornerRadiusDp = 0)
        val SECTIONS = listOf("tabs", "banners", "stories", "clubs", "sdc", "feed")

        fun fromJson(o: JSONObject): UiBlock? {
            val type = o.optString("type")
            if (type !in TYPES) return null
            val section = o.optString("section")
            if (type == "section" && section !in SECTIONS) return null
            val img = o.optString("imageUrl")
            return UiBlock(
                id = o.optString("id").ifBlank { return null }.take(40),
                type = type, section = section,
                visible = o.optBoolean("visible", true),
                widthFraction = o.optDouble("widthFraction", 1.0).toFloat().coerceIn(0.2f, 1f),
                heightDp = o.optInt("heightDp", 0).coerceIn(0, 800),
                cornerRadiusDp = o.optInt("cornerRadiusDp", 12).coerceIn(0, 64),
                bgColorHex = o.optString("bgColorHex").take(9),
                textColorHex = o.optString("textColorHex", "#FFFFFF").take(9),
                text = o.optString("text").take(500),
                fontSizeSp = o.optInt("fontSizeSp", 14).coerceIn(8, 48),
                paddingDp = o.optInt("paddingDp", 12).coerceIn(0, 48),
                imageUrl = if (img.startsWith("https://")) img else "",
                linkUrl = o.optString("linkUrl").let { if (it.startsWith("https://")) it else "" }
            )
        }
    }
}

data class UiLayout(val screen: String = "home", val blocks: List<UiBlock> = emptyList()) {
    fun toJson(): JSONObject = JSONObject().put("screen", screen)
        .put("blocks", JSONArray().apply { blocks.forEach { put(it.toJson()) } })

    companion object {
        /** Returns null when missing/invalid so the app falls back to the built-in layout. */
        fun fromJson(o: JSONObject?): UiLayout? {
            if (o == null) return null
            val arr = o.optJSONArray("blocks") ?: return null
            val blocks = (0 until minOf(arr.length(), 100)).mapNotNull { arr.optJSONObject(it)?.let(UiBlock::fromJson) }
            if (blocks.isEmpty()) return null
            return UiLayout(o.optString("screen", "home"), blocks)
        }

        /** Same order as the built-in Home screen; starting point for the editor. */
        fun defaultHome() = UiLayout("home", UiBlock.SECTIONS.map { sectionBlock(it) })
    }
}
