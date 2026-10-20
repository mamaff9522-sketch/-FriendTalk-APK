/*
 * 3Jump Filter Studio - filter engine (portable; Android SDK only, no MediaPipe dependency).
 * Licensed under the Apache License, Version 2.0.
 */
package com.threejump.filterstudio.filterengine

import kotlin.math.roundToLong

/** Effect groups. SMOOTH/BRIGHT/PINK can be switched on/off; MASK and GLOBAL always apply. */
enum class FilterGroup(val key: String, val thai: String, val switchable: Boolean, val defaultOn: Boolean = true) {
    SMOOTH("smooth", "หน้าเนียน", true),
    BRIGHT("bright", "หน้าใส", true),
    PINK("pink", "ผิวอมชมพู", true),
    LIPSTICK("lipstick", "ลิปสติก", true, false),
    BROW("brow", "เติมคิ้ว", true, false),
    BLUSH("blush", "บลัชออน", true, false),
    EYESHADOW("eyeshadow", "อายแชโดว์", true, false),
    EYELINER("eyeliner", "อายไลเนอร์", true, false),
    EYELASH("eyelash", "ขนตา", true, false),
    HIGHLIGHT("highlight", "ไฮไลท์", true, false),
    CONTOUR("contour", "คอนทัวร์ (เฉดดิ้ง)", true, false),
    UNDEREYE("undereye", "ใต้ตาสว่าง", true, false),
    TEETH("teeth", "ฟันขาว", true, false),
    MASK("mask", "หน้ากากใบหน้า", false),
    GLOBAL("global", "ภาพรวม", false),
}

/** One adjustable parameter. Every parameter in [FilterParams.ALL] gets its own control in the editor. */
data class ParamSpec(
    val key: String,
    val thai: String,
    val english: String,
    val min: Float,
    val max: Float,
    val step: Float,
    val default: Float,
    val group: FilterGroup,
    val unit: String = "",
)

object FilterParams {
    val ALL: List<ParamSpec> = listOf(
        ParamSpec("smoothness", "ความเนียน", "Smoothness", 0f, 1f, 0.01f, 0.6f, FilterGroup.SMOOTH),
        ParamSpec("radius", "รัศมีการเบลอ", "Smoothing radius", 1f, 12f, 0.5f, 5f, FilterGroup.SMOOTH, "px"),
        ParamSpec("edgePreserve", "รักษาขอบ", "Edge preserve", 0f, 1f, 0.01f, 0.7f, FilterGroup.SMOOTH),
        ParamSpec("brightness", "ความสว่าง", "Brightness", -0.3f, 0.3f, 0.005f, 0.06f, FilterGroup.BRIGHT),
        ParamSpec("contrast", "คอนทราสต์", "Contrast", 0.5f, 1.5f, 0.01f, 1.0f, FilterGroup.BRIGHT),
        ParamSpec("saturation", "ความอิ่มสี", "Saturation", 0f, 2f, 0.01f, 1.0f, FilterGroup.BRIGHT),
        ParamSpec("warmth", "โทนอุ่น/เย็น", "Warmth", -1f, 1f, 0.01f, 0f, FilterGroup.PINK),
        ParamSpec("tint", "โทนสีผิว (เขียว↔ชมพูม่วง)", "Skin tint", -1f, 1f, 0.01f, 0f, FilterGroup.PINK),
        ParamSpec("pink", "ความอมชมพู", "Pink amount", 0f, 1f, 0.01f, 0.35f, FilterGroup.PINK),
        ParamSpec("lipstickAmount", "ความเข้มลิปสติก", "Lipstick amount", 0f, 1f, 0.01f, 0.5f, FilterGroup.LIPSTICK),
        ParamSpec("browAmount", "ความเข้มการเติมคิ้ว", "Brow fill amount", 0f, 1f, 0.01f, 0.4f, FilterGroup.BROW),
        ParamSpec("blushAmount", "ความเข้มบลัช", "Blush amount", 0f, 1f, 0.01f, 0.35f, FilterGroup.BLUSH),
        ParamSpec("blushSize", "ขนาดบลัช", "Blush size", 0.5f, 2f, 0.01f, 1f, FilterGroup.BLUSH),
        ParamSpec("eyeshadowAmount", "ความเข้มอายแชโดว์", "Eyeshadow amount", 0f, 1f, 0.01f, 0.4f, FilterGroup.EYESHADOW),
        ParamSpec("eyeshadowSize", "ความสูงอายแชโดว์", "Eyeshadow height", 0.5f, 2f, 0.01f, 1f, FilterGroup.EYESHADOW),
        ParamSpec("eyelinerAmount", "ความเข้มอายไลเนอร์", "Eyeliner amount", 0f, 1f, 0.01f, 0.6f, FilterGroup.EYELINER),
        ParamSpec("eyelinerWidth", "ความหนาอายไลเนอร์", "Eyeliner width", 0.5f, 2.5f, 0.01f, 1f, FilterGroup.EYELINER),
        ParamSpec("eyelinerWing", "ความยาวหางตา", "Eyeliner wing length", 0f, 3f, 0.01f, 1f, FilterGroup.EYELINER),
        ParamSpec("eyelinerWingAngle", "มุมหางตา (องศา)", "Eyeliner wing angle", -10f, 70f, 1f, 30f, FilterGroup.EYELINER, "°"),
        ParamSpec("eyelashAmount", "ความเข้มขนตา", "Eyelash amount", 0f, 1f, 0.01f, 0.7f, FilterGroup.EYELASH),
        ParamSpec("eyelashLength", "ความยาวขนตา", "Eyelash length", 0.5f, 2f, 0.01f, 1f, FilterGroup.EYELASH),
        ParamSpec("eyelashLower", "ขนตาล่าง", "Lower lashes", 0f, 1f, 0.01f, 0.3f, FilterGroup.EYELASH),
        ParamSpec("highlightAmount", "ความเข้มไฮไลท์", "Highlight amount", 0f, 1f, 0.01f, 0.3f, FilterGroup.HIGHLIGHT),
        ParamSpec("highlightSize", "ขนาดไฮไลท์", "Highlight size", 0.5f, 2f, 0.01f, 1f, FilterGroup.HIGHLIGHT),
        ParamSpec("contourAmount", "ความเข้มคอนทัวร์", "Contour amount", 0f, 1f, 0.01f, 0.3f, FilterGroup.CONTOUR),
        ParamSpec("contourSize", "ขนาดคอนทัวร์", "Contour size", 0.5f, 2f, 0.01f, 1f, FilterGroup.CONTOUR),
        ParamSpec("underEyeAmount", "ความสว่างใต้ตา", "Under-eye brightening", 0f, 1f, 0.01f, 0.4f, FilterGroup.UNDEREYE),
        ParamSpec("underEyeSize", "ขนาดบริเวณใต้ตา", "Under-eye area size", 0.5f, 2f, 0.01f, 1f, FilterGroup.UNDEREYE),
        ParamSpec("teethAmount", "ความขาวของฟัน", "Teeth whitening", 0f, 1f, 0.01f, 0.5f, FilterGroup.TEETH),
        ParamSpec("feather", "ความนุ่มขอบหน้ากาก", "Mask feather", 0f, 1f, 0.01f, 0.4f, FilterGroup.MASK),
        ParamSpec("expand", "ขยาย/หดหน้ากาก", "Mask expand/shrink", -0.2f, 0.2f, 0.005f, 0f, FilterGroup.MASK),
        ParamSpec("eyeProtect", "ป้องกันตา/คิ้ว", "Eye protect", 0f, 1f, 0.01f, 0.9f, FilterGroup.MASK),
        ParamSpec("lipProtect", "ป้องกันริมฝีปาก", "Lip protect", 0f, 1f, 0.01f, 0.8f, FilterGroup.MASK),
        ParamSpec("intensity", "ความแรงรวม", "Overall intensity", 0f, 1f, 0.01f, 1f, FilterGroup.GLOBAL),
    ) + ColorSpecs.ALL.flatMap { it.params() }
    private val byKey = ALL.associateBy { it.key }
    fun spec(key: String): ParamSpec = byKey[key] ?: error("unknown param $key")
    fun quantize(spec: ParamSpec, v: Float): Float {
        val c = v.coerceIn(spec.min, spec.max)
        val steps = ((c - spec.min) / spec.step).roundToLong()
        return (spec.min + steps * spec.step).coerceIn(spec.min, spec.max)
    }
}

/** A colour item edited with the colour picker; stored as three params: <key>Hue, <key>Sat, <key>Val. */
data class ColorSpec(val key: String, val thai: String, val english: String, val group: FilterGroup, val h: Float, val sv: Float, val v: Float) {
    val hueKey get() = key + "Hue"; val satKey get() = key + "Sat"; val valKey get() = key + "Val"
    fun params() = listOf(
        ParamSpec(hueKey, "$thai: เฉดสี", "$english hue", 0f, 360f, 0.5f, h, group, "°"),
        ParamSpec(satKey, "$thai: ความสดของสี", "$english saturation", 0f, 1f, 0.005f, sv, group),
        ParamSpec(valKey, "$thai: ความสว่างของสี", "$english value", 0f, 1f, 0.005f, v, group),
    )
    val defaultHex get() = ColorMath.toHex(ColorMath.hsvToRgb(h, sv, v))
}

object ColorSpecs {
    val LIPSTICK = ColorSpec("lipstickColor", "สีลิปสติก", "Lipstick colour", FilterGroup.LIPSTICK, 350f, 0.75f, 0.83f)
    val BROW = ColorSpec("browColor", "สีคิ้ว", "Brow colour", FilterGroup.BROW, 25f, 0.55f, 0.24f)
    val BLUSH = ColorSpec("blushColor", "สีบลัช", "Blush colour", FilterGroup.BLUSH, 350f, 0.55f, 1f)
    val EYESHADOW = ColorSpec("eyeshadowColor", "สีอายแชโดว์", "Eyeshadow colour", FilterGroup.EYESHADOW, 20f, 0.45f, 0.62f)
    val EYELINER = ColorSpec("eyelinerColor", "สีอายไลเนอร์", "Eyeliner colour", FilterGroup.EYELINER, 20f, 0.3f, 0.08f)
    val EYELASH = ColorSpec("eyelashColor", "สีขนตา", "Eyelash colour", FilterGroup.EYELASH, 20f, 0.3f, 0.06f)
    val HIGHLIGHT = ColorSpec("highlightColor", "สีไฮไลท์", "Highlight colour", FilterGroup.HIGHLIGHT, 40f, 0.1f, 1f)
    val CONTOUR = ColorSpec("contourColor", "สีคอนทัวร์", "Contour colour", FilterGroup.CONTOUR, 22f, 0.45f, 0.4f)
    val ALL = listOf(LIPSTICK, BROW, BLUSH, EYESHADOW, EYELINER, EYELASH, HIGHLIGHT, CONTOUR)
    /** Quick swatches per group (hex). */
    val SWATCHES: Map<FilterGroup, List<String>> = mapOf(
        FilterGroup.LIPSTICK to listOf("#C2185B", "#E53950", "#D46A6A", "#B5525C", "#E88E8E", "#9C2A3B", "#F06292", "#A0522D"),
        FilterGroup.BROW to listOf("#3E2A1E", "#5B3A29", "#2B1D14", "#6D4C41", "#4A3B32", "#1F1A17"),
        FilterGroup.BLUSH to listOf("#FF8FA3", "#FFA07A", "#F48FB1", "#FFB6C1", "#E57373", "#FFAB91"),
        FilterGroup.EYESHADOW to listOf("#9E7B5B", "#B5651D", "#C08081", "#8E6C8A", "#D4A373", "#6B4F3A", "#A1887F", "#B07D62"),
        FilterGroup.EYELINER to listOf("#141010", "#2B1D14", "#3E2A1E", "#1A237E", "#2E2E2E", "#4E342E"),
        FilterGroup.EYELASH to listOf("#100C0A", "#2B1D14", "#3E2A1E", "#1A1A2E", "#4E342E", "#000000"),
        FilterGroup.HIGHLIGHT to listOf("#FFF5E6", "#FFE9C7", "#FFF0F5", "#FFFFFF", "#FCE4C4", "#F5E6FF"),
        FilterGroup.CONTOUR to listOf("#66472E", "#7A5230", "#5D4037", "#8D6E63", "#4E342E", "#795548"),
    )
}

/** HSV / RGB / hex helpers (pure Kotlin). RGB components 0..1. */
object ColorMath {
    fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray {
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val i = hh.toInt(); val f = hh - i
        val p = v * (1 - s); val q = v * (1 - s * f); val t = v * (1 - s * (1 - f))
        return when (i) { 0 -> floatArrayOf(v, t, p); 1 -> floatArrayOf(q, v, p); 2 -> floatArrayOf(p, v, t)
            3 -> floatArrayOf(p, q, v); 4 -> floatArrayOf(t, p, v); else -> floatArrayOf(v, p, q) }
    }
    fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return floatArrayOf(h, if (mx == 0f) 0f else d / mx, mx)
    }
    fun toHex(rgb: FloatArray): String = "#%02X%02X%02X".format(
        (rgb[0] * 255f).roundToLong().coerceIn(0, 255), (rgb[1] * 255f).roundToLong().coerceIn(0, 255), (rgb[2] * 255f).roundToLong().coerceIn(0, 255))
    /** Parses #RRGGBB or RRGGBB; null if invalid. */
    fun parseHex(s: String): FloatArray? {
        val t = s.trim().removePrefix("#")
        if (t.length != 6 || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        val v = t.toInt(16)
        return floatArrayOf(((v shr 16) and 255) / 255f, ((v shr 8) and 255) / 255f, (v and 255) / 255f)
    }
}

/** A complete filter: name, on/off per switchable group, value for every parameter. Immutable. */
data class FilterConfig(
    val id: String,
    val name: String,
    val enabled: Map<String, Boolean> = FilterGroup.values().filter { it.switchable }.associate { it.key to it.defaultOn },
    val params: Map<String, Float> = emptyMap(),
) {
    operator fun get(key: String): Float = params[key] ?: FilterParams.spec(key).default
    fun isOn(g: FilterGroup): Boolean = !g.switchable || (enabled[g.key] ?: g.defaultOn)
    fun with(key: String, v: Float) = copy(params = params + (key to FilterParams.quantize(FilterParams.spec(key), v)))
    fun withEnabled(g: FilterGroup, on: Boolean) = copy(enabled = enabled + (g.key to on))
    fun resetParam(key: String) = copy(params = params - key)
    fun resetAll() = FilterConfig(id, name)
    fun rgb(c: ColorSpec): FloatArray = ColorMath.hsvToRgb(get(c.hueKey), get(c.satKey), get(c.valKey))
    fun hex(c: ColorSpec): String = ColorMath.toHex(rgb(c))
    fun withHex(c: ColorSpec, hex: String): FilterConfig {
        val rgb = ColorMath.parseHex(hex) ?: return this
        val hsv = ColorMath.rgbToHsv(rgb[0], rgb[1], rgb[2])
        return with(c.hueKey, hsv[0]).with(c.satKey, hsv[1]).with(c.valKey, hsv[2])
    }
    fun resetColor(c: ColorSpec) = resetParam(c.hueKey).resetParam(c.satKey).resetParam(c.valKey)
    /** All values resolved (defaults filled in). */
    fun resolved(): Map<String, Float> = FilterParams.ALL.associate { it.key to get(it.key) }

    fun toJson(): String {
        val sb = StringBuilder()
        sb.append("{\n  \"schema\": ").append(SCHEMA).append(",\n")
        sb.append("  \"id\": ").append(MiniJson.quote(id)).append(",\n")
        sb.append("  \"name\": ").append(MiniJson.quote(name)).append(",\n")
        sb.append("  \"enabled\": {")
        sb.append(FilterGroup.values().filter { it.switchable }.joinToString(", ") { "\"${it.key}\": ${isOn(it)}" })
        sb.append("},\n  \"colors\": {")
        sb.append(ColorSpecs.ALL.joinToString(", ") { "\"${it.key}\": \"${hex(it)}\"" })
        sb.append("},\n  \"params\": {\n")
        sb.append(FilterParams.ALL.joinToString(",\n") { "    \"${it.key}\": ${MiniJson.num(get(it.key))}" })
        sb.append("\n  }\n}\n")
        return sb.toString()
    }

    companion object {
        const val SCHEMA = 1
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: String): FilterConfig {
            val root = MiniJson.parse(json) as? Map<String, Any?> ?: error("filter.json: not an object")
            val en = (root["enabled"] as? Map<String, Any?>).orEmpty()
            val ps = (root["params"] as? Map<String, Any?>).orEmpty()
            var cfg = FilterConfig(root["id"] as? String ?: "imported", root["name"] as? String ?: "ฟิลเตอร์")
            for (g in FilterGroup.values()) if (g.switchable) (en[g.key] as? Boolean)?.let { cfg = cfg.withEnabled(g, it) }
            for (s in FilterParams.ALL) (ps[s.key] as? Number)?.let { cfg = cfg.with(s.key, it.toFloat()) }
            return cfg
        }
    }
}

/** Built-in one-tap presets. */
object FilterPresets {
    private fun only(vararg on: FilterGroup) = FilterGroup.values().filter { it.switchable }.associate { it.key to (it in on) }
    val ALL: List<FilterConfig> = listOf(
        FilterConfig("preset-smooth", "หน้าเนียน", only(FilterGroup.SMOOTH)),
        FilterConfig("preset-bright", "หน้าใส", only(FilterGroup.BRIGHT), mapOf("brightness" to 0.1f, "contrast" to 1.03f, "saturation" to 1.05f)),
        FilterConfig("preset-pink", "ผิวอมชมพู", only(FilterGroup.PINK), mapOf("pink" to 0.5f, "warmth" to 0.1f, "tint" to 0.15f)),
        FilterConfig("preset-natural", "ธรรมชาติ (รวม 3 แบบ)", only(FilterGroup.SMOOTH, FilterGroup.BRIGHT, FilterGroup.PINK),
            mapOf("smoothness" to 0.45f, "brightness" to 0.05f, "pink" to 0.25f, "intensity" to 0.85f)),
        FilterConfig("preset-makeup", "แต่งหน้าธรรมชาติ", only(FilterGroup.SMOOTH, FilterGroup.BRIGHT, FilterGroup.LIPSTICK, FilterGroup.BROW, FilterGroup.BLUSH),
            mapOf("smoothness" to 0.45f, "brightness" to 0.04f, "lipstickAmount" to 0.45f, "lipstickColorHue" to 352f,
                "browAmount" to 0.35f, "blushAmount" to 0.3f, "intensity" to 0.9f)),
        FilterConfig("preset-eyes", "ตาสวยละมุน", only(FilterGroup.SMOOTH, FilterGroup.EYESHADOW, FilterGroup.EYELINER, FilterGroup.EYELASH, FilterGroup.UNDEREYE, FilterGroup.BROW),
            mapOf("smoothness" to 0.4f, "eyeshadowAmount" to 0.35f, "eyelinerAmount" to 0.55f, "underEyeAmount" to 0.4f, "browAmount" to 0.3f)),
        FilterConfig("preset-glow", "หน้าโกลว์มีมิติ", only(FilterGroup.SMOOTH, FilterGroup.BRIGHT, FilterGroup.HIGHLIGHT, FilterGroup.CONTOUR, FilterGroup.TEETH, FilterGroup.LIPSTICK),
            mapOf("smoothness" to 0.45f, "highlightAmount" to 0.35f, "contourAmount" to 0.3f, "teethAmount" to 0.45f, "lipstickAmount" to 0.35f)),
    )
}

/** Tiny JSON reader/writer (objects, arrays, strings, numbers, booleans, null). No dependencies. */
object MiniJson {
    fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }

    fun num(v: Float): String {
        val r = (v * 100000f).roundToLong() / 100000.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() + ".0" else r.toString()
    }

    fun parse(s: String): Any? { val p = P(s); val v = p.value(); p.ws(); require(p.i == s.length) { "trailing data at ${p.i}" }; return v }

    private class P(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws(); require(i < s.length) { "unexpected end" }
            return when (val c = s[i]) {
                '{' -> obj(); '[' -> arr(); '"' -> str()
                't' -> lit("true", true); 'f' -> lit("false", false); 'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) number() else error("bad char '$c' at $i")
            }
        }
        fun lit(w: String, v: Any?): Any? { require(s.startsWith(w, i)) { "bad literal at $i" }; i += w.length; return v }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>(); i++; ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws(); val k = str(); ws(); require(s[i] == ':') { "expected : at $i" }; i++
                m[k] = value(); ws()
                when (s[i]) { ',' -> i++; '}' -> { i++; return m }; else -> error("expected , or } at $i") }
            }
        }
        fun arr(): List<Any?> {
            val l = ArrayList<Any?>(); i++; ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value(); ws()
                when (s[i]) { ',' -> i++; ']' -> { i++; return l }; else -> error("expected , or ] at $i") }
            }
        }
        fun str(): String {
            require(s[i] == '"') { "expected string at $i" }; i++
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = s[i++]) {
                        'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun number(): Double {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(st, i).toDouble()
        }
    }
}
