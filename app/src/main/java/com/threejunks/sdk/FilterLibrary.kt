package com.threejunks.sdk

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * The project's 3Jump filter library (`GET /api/v1/filters`, ETag-cached), usable without a call connection
 * (e.g. on a live screen). [tokenProvider] returns a Three Junks session token of the project.
 * Blocking network calls: use a background thread.
 */
class FilterLibrary(
    baseUrl: String,
    private val tokenProvider: TokenProvider,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val base = baseUrl.trimEnd('/')
    @Volatile private var cache: Pair<String, List<LibraryFilter>>? = null

    /** The enabled filters; empty when filters are disabled for the project (HTTP 403). [token] is tried first when given. */
    fun fetch(token: String? = null): List<LibraryFilter> {
        // a cached token may have expired: retry once with a fresh one
        for (tok in sequenceOf({ token }, { tokenProvider.token() }).mapNotNull { it() }) {
            val rb = Request.Builder().url("$base/api/v1/filters").header("Authorization", "Bearer $tok")
            cache?.let { rb.header("If-None-Match", it.first) }
            http.newCall(rb.build()).execute().use { r ->
                if (r.code == 401) return@use
                if (r.code == 304) cache?.let { return it.second }
                if (r.code == 403) return emptyList()
                if (!r.isSuccessful) throw IOException("filters: HTTP ${r.code}")
                val list = parse(JSONObject(r.body!!.string()))
                r.header("ETag")?.let { cache = it to list }
                return list
            }
        }
        throw IOException("filters: session token refused")
    }

    companion object {
        internal fun parse(o: JSONObject): List<LibraryFilter> {
            val a = o.optJSONArray("filters") ?: return emptyList()
            return (0 until a.length()).map { i ->
                val f = a.getJSONObject(i)
                LibraryFilter(f.getString("id"), f.optString("name"), f.optLong("updatedAt"), f.getJSONObject("filter").toString())
            }
        }
    }
}
