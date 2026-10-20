/*
 * One-tap eye makeup looks (eyeshadow + eyeliner + eyelashes). Pure Kotlin.
 * Licensed under the Apache License, Version 2.0.
 */
package com.threejump.filterstudio.filterengine

/**
 * An eye look: which of the eye groups are on, their params and colours (hex). [apply] switches the
 * eye groups ([GROUPS]) to exactly [on] and sets the values; every other group and value is kept.
 */
data class EyeStyle(
    val id: String,
    val thai: String,
    val on: Set<FilterGroup>,
    val params: Map<String, Float> = emptyMap(),
    val colors: Map<ColorSpec, String> = emptyMap(),
) {
    fun apply(cfg: FilterConfig): FilterConfig {
        var c = cfg
        for (g in EyeStyles.GROUPS) c = c.withEnabled(g, g in on)
        for (g in on) for (p in FilterParams.ALL) if (p.group == g) c = c.resetParam(p.key)
        for ((k, v) in params) c = c.with(k, v)
        for ((spec, hex) in colors) c = c.withHex(spec, hex)
        return c
    }
}

object EyeStyles {
    val GROUPS = listOf(FilterGroup.EYESHADOW, FilterGroup.EYELINER, FilterGroup.EYELASH)
    private val S = FilterGroup.EYESHADOW; private val L = FilterGroup.EYELINER; private val A = FilterGroup.EYELASH

    val ALL: List<EyeStyle> = listOf(
        EyeStyle("natural", "ธรรมชาติ", setOf(S, A),
            mapOf("eyeshadowAmount" to 0.25f, "eyelashAmount" to 0.5f, "eyelashLength" to 0.9f, "eyelashLower" to 0.2f),
            mapOf(ColorSpecs.EYESHADOW to "#A1887F", ColorSpecs.EYELASH to "#2B1D14")),
        EyeStyle("sweet", "ตาหวาน", setOf(S, L, A),
            mapOf("eyeshadowAmount" to 0.3f, "eyelinerAmount" to 0.45f, "eyelinerWidth" to 0.7f, "eyelinerWing" to 0.3f,
                "eyelinerWingAngle" to 5f, "eyelashAmount" to 0.8f, "eyelashLength" to 1.3f, "eyelashLower" to 0.5f),
            mapOf(ColorSpecs.EYESHADOW to "#C08081", ColorSpecs.EYELINER to "#3E2A1E", ColorSpecs.EYELASH to "#100C0A")),
        EyeStyle("cat", "ตาแมว", setOf(L, A),
            mapOf("eyelinerAmount" to 0.85f, "eyelinerWidth" to 1.3f, "eyelinerWing" to 2.0f, "eyelinerWingAngle" to 40f,
                "eyelashAmount" to 0.7f, "eyelashLength" to 1.1f, "eyelashLower" to 0.1f),
            mapOf(ColorSpecs.EYELINER to "#141010", ColorSpecs.EYELASH to "#100C0A")),
        EyeStyle("winged", "ปีกยาว", setOf(L, A),
            mapOf("eyelinerAmount" to 0.8f, "eyelinerWidth" to 1.1f, "eyelinerWing" to 2.6f, "eyelinerWingAngle" to 22f,
                "eyelashAmount" to 0.6f, "eyelashLower" to 0.15f),
            mapOf(ColorSpecs.EYELINER to "#141010", ColorSpecs.EYELASH to "#100C0A")),
        EyeStyle("smoky", "สโมกกี้", setOf(S, L, A),
            mapOf("eyeshadowAmount" to 0.65f, "eyeshadowSize" to 1.4f, "eyelinerAmount" to 0.8f, "eyelinerWidth" to 1.6f,
                "eyelinerWing" to 0.6f, "eyelinerWingAngle" to 20f, "eyelashAmount" to 0.9f, "eyelashLower" to 0.6f),
            mapOf(ColorSpecs.EYESHADOW to "#4A3B3B", ColorSpecs.EYELINER to "#141010", ColorSpecs.EYELASH to "#000000")),
        EyeStyle("doll", "ตาโตตุ๊กตา", setOf(S, L, A),
            mapOf("eyeshadowAmount" to 0.25f, "eyelinerAmount" to 0.5f, "eyelinerWidth" to 0.8f, "eyelinerWing" to 0f,
                "eyelashAmount" to 1f, "eyelashLength" to 1.8f, "eyelashLower" to 0.8f),
            mapOf(ColorSpecs.EYESHADOW to "#D4A373", ColorSpecs.EYELINER to "#2B1D14", ColorSpecs.EYELASH to "#000000")),
        EyeStyle("blue", "ไลเนอร์น้ำเงิน", setOf(L, A),
            mapOf("eyelinerAmount" to 0.8f, "eyelinerWidth" to 1.2f, "eyelinerWing" to 1.4f, "eyelinerWingAngle" to 30f,
                "eyelashAmount" to 0.6f, "eyelashLower" to 0.2f),
            mapOf(ColorSpecs.EYELINER to "#1A237E", ColorSpecs.EYELASH to "#1A1A2E")),
        EyeStyle("warm", "ส้มอิฐ", setOf(S, L, A),
            mapOf("eyeshadowAmount" to 0.45f, "eyelinerAmount" to 0.5f, "eyelinerWing" to 1f, "eyelashAmount" to 0.6f),
            mapOf(ColorSpecs.EYESHADOW to "#B5651D", ColorSpecs.EYELINER to "#3E2A1E", ColorSpecs.EYELASH to "#2B1D14")),
        EyeStyle("none", "ไม่แต่งตา", emptySet()),
    )
}
