package com.example.rtc

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.threejunks.sdk.FilterLibrary
import com.threejunks.sdk.LibraryFilter

/**
 * 3Jump filters for my camera in calls and lives: the project's filter library (made in 3Jump Filter Studio, uploaded
 * in Three Junks Super Admin → คลังฟิลเตอร์ 3Jump). The chosen filter is remembered for the next call / live while it
 * is still in the library. Main thread only.
 */
object TjFilters {
    private val main = Handler(Looper.getMainLooper())

    /** Filters of the project; empty = no filter button. */
    var list by mutableStateOf<List<LibraryFilter>>(emptyList()); private set
    /** The filter on my camera. */
    var selectedId by mutableStateOf<String?>(null); private set

    private var apply: ((LibraryFilter?) -> Unit)? = null
    private var library: Pair<String, FilterLibrary>? = null
    private var generation = 0

    /** A camera (call or live host) started: [applyFn] puts a filter on it. Loads the library and re-applies the choice. */
    fun attach(applyFn: (LibraryFilter?) -> Unit) { apply = applyFn; refresh() }

    /** That camera stopped. */
    fun detach(applyFn: (LibraryFilter?) -> Unit) { if (apply === applyFn) { apply = null; generation++ } }

    fun select(f: LibraryFilter?) {
        val a = apply ?: return
        runCatching { a(f) }.onSuccess { selectedId = f?.id }.onFailure { runCatching { a(null) }; selectedId = null }
    }

    /** Fetch the library again (also after a `filters:updated` push). */
    fun refresh() {
        val base = ThreeJunksCalls.baseUrl
        if (base.isBlank() || apply == null) return
        val lib = library?.takeIf { it.first == base }?.second
            ?: FilterLibrary(base, { ThreeJunksCalls.sessionToken() }).also { library = base to it }
        val gen = ++generation
        Thread({
            val r = runCatching { lib.fetch() }
            main.post {
                if (gen != generation || apply == null) return@post
                val l = r.getOrNull() ?: return@post // offline: keep the last list
                list = l
                select(l.firstOrNull { it.id == selectedId })
            }
        }, "tj-filters").start()
    }
}
