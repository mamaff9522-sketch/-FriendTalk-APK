package com.example.service

import com.example.model.UiLayout
import com.example.network.BrainApi
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the published Home layout from friendtalk-brain (GET /ui/layout/home). */
object UiLayoutRepository {
    private val _home = MutableStateFlow<UiLayout?>(null)
    val home: StateFlow<UiLayout?> = _home.asStateFlow()

    /** On error keeps the last good layout (or null = built-in layout). */
    suspend fun refreshHome() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val r = BrainApi.call(user, "GET", "/ui/layout/home")
        if (r.code == 200) _home.value = UiLayout.fromJson(r.json?.optJSONObject("layout"))
        else if (r.code == 404) _home.value = null
    }

    fun setLocal(layout: UiLayout?) { _home.value = layout }
}
