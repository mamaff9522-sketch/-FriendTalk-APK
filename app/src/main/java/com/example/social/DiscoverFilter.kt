package com.example.social

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 'ต้องการ' filter of หาเพื่อน: gender, age range and distance. One setting shared by the online grid, random match,
 * shake, radar and swipe — BrainApi adds it to those requests (see [appendTo]); the server applies it.
 */
object DiscoverFilter {
    /** "" = ทุกเพศ, else male / female / other (as on the server). */
    var gender by mutableStateOf("")
    var ageMin by mutableStateOf(18)
    var ageMax by mutableStateOf(60)
    /** 0 = ทุกที่ (no distance limit); else km, one of [DISTANCES]. */
    var km by mutableStateOf(0.0)
    private var loaded = false

    const val AGE_LO = 18
    const val AGE_HI = 60 // slider top = "60+" (sent as 99, no upper limit)
    val GENDERS = listOf("" to "ทุกเพศ", "female" to "หญิง", "male" to "ชาย", "other" to "LGBTQ+")
    val DISTANCES = listOf(0.0, 1.0, 5.0, 10.0, 25.0, 50.0)
    fun distanceLabel(k: Double) = if (k <= 0) "ทุกที่" else "${k.toInt()} กม."

    fun load(ctx: Context) {
        if (loaded) return; loaded = true
        val p = ctx.getSharedPreferences("ft_social", Context.MODE_PRIVATE)
        gender = p.getString("fGender", "") ?: ""
        ageMin = p.getInt("fAgeMin", AGE_LO).coerceIn(AGE_LO, AGE_HI); ageMax = p.getInt("fAgeMax", AGE_HI).coerceIn(ageMin, AGE_HI)
        km = p.getFloat("fKm", 0f).toDouble().takeIf { it in DISTANCES } ?: 0.0
    }
    fun save(ctx: Context, g: String, a: Int, b: Int, k: Double) {
        gender = g; ageMin = a; ageMax = b; km = k; loaded = true
        ctx.getSharedPreferences("ft_social", Context.MODE_PRIVATE).edit()
            .putString("fGender", g).putInt("fAgeMin", a).putInt("fAgeMax", b).putFloat("fKm", k.toFloat()).apply()
    }
    val isSet get() = gender.isNotBlank() || ageMin > AGE_LO || ageMax < AGE_HI || km > 0
    fun summary(): String = buildList {
        if (gender.isNotBlank()) add(GENDERS.firstOrNull { it.first == gender }?.second ?: "")
        if (ageMin > AGE_LO || ageMax < AGE_HI) add("$ageMin-${if (ageMax >= AGE_HI) "60+" else ageMax} ปี")
        if (km > 0) add(distanceLabel(km))
    }.joinToString(" · ")

    private val PATHS = listOf("/discover/online", "/match", "/shake", "/radar/nearby", "/swipe/candidates")
    /** Adds the filter as query params to the friend-finding endpoints (not to /shake/result and others). */
    fun appendTo(path: String): String {
        val base = path.substringBefore('?')
        if (base !in PATHS) return path
        val q = buildString {
            if (gender.isNotBlank()) append("&fGender=").append(gender)
            if (ageMin > AGE_LO || ageMax < AGE_HI) append("&fAgeMin=").append(ageMin).append("&fAgeMax=").append(if (ageMax >= AGE_HI) 99 else ageMax)
            if (km > 0) append("&fKm=").append(if (km < 1) "0.5" else km.toInt().toString())
        }
        if (q.isEmpty()) return path
        return if ('?' in path) path + q else path + "?" + q.drop(1)
    }
}
