package com.example.network

import com.example.model.UserRole
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Reads the signed-in user's role from friendtalk-brain GET /me (role lives in RTDB roles/{uid}). */
object BrainApi {
    private const val BASE_URL = "https://friendtalk-brain-123224091480.asia-southeast1.run.app"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    data class MeResult(val role: UserRole, val rawRole: String)

    private suspend fun idToken(user: FirebaseUser): String? = suspendCancellableCoroutine { cont ->
        user.getIdToken(false).addOnCompleteListener { task ->
            cont.resume(if (task.isSuccessful) task.result?.token else null)
        }
    }

    /** Returns null on any error so the caller keeps the current (USER) role. */
    suspend fun fetchMe(user: FirebaseUser): MeResult? {
        val token = idToken(user) ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$BASE_URL/me")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val raw = JSONObject(resp.body?.string().orEmpty()).optString("role", "user")
                    val role = when (raw.lowercase()) {
                        "superadmin" -> UserRole.SUPERADMIN
                        "admin" -> UserRole.ADMIN
                        else -> UserRole.USER
                    }
                    MeResult(role, raw)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    // ===== AI characters =====
    data class AiBot(val id: String, val name: String, val avatarUrl: String, val bio: String, val interests: List<String>, val enabled: Boolean)
    data class MatchResult(val type: String, val partnerUid: String? = null, val bot: AiBot? = null)
    /** code = HTTP status (0 = network error); body = parsed JSON or null */
    data class ApiResponse(val code: Int, val json: JSONObject?)

    private fun parseBot(o: JSONObject): AiBot {
        val arr = o.optJSONArray("interests") ?: JSONArray()
        return AiBot(
            id = o.optString("id"), name = o.optString("name"), avatarUrl = o.optString("avatarUrl"),
            bio = o.optString("bio"), interests = (0 until arr.length()).map { arr.optString(it) },
            enabled = o.optBoolean("enabled", false)
        )
    }

    suspend fun call(user: FirebaseUser, method: String, path: String, body: JSONObject? = null): ApiResponse {
        val token = idToken(user) ?: return ApiResponse(0, null)
        return withContext(Dispatchers.IO) {
            try {
                val rb = body?.toString()?.toRequestBody("application/json".toMediaType())
                    ?: if (method == "GET" || method == "DELETE") null else "{}".toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url("$BASE_URL$path")
                    .header("Authorization", "Bearer $token").header("Accept", "application/json")
                    .method(method, rb).build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    ApiResponse(resp.code, runCatching { JSONObject(text) }.getOrNull())
                }
            } catch (e: Exception) { ApiResponse(0, null) }
        }
    }

    suspend fun match(user: FirebaseUser): MatchResult? {
        val r = call(user, "POST", "/match")
        val j = r.json ?: return null
        if (r.code != 200) return null
        return when (j.optString("type")) {
            "bot" -> MatchResult("bot", bot = j.optJSONObject("bot")?.let { parseBot(it) })
            "user" -> MatchResult("user", partnerUid = j.optString("uid"))
            else -> MatchResult("waiting")
        }
    }

    /** Returns reply text, or an error message prefixed with "!" */
    suspend fun botChat(user: FirebaseUser, botId: String, message: String): String {
        val r = call(user, "POST", "/bot/chat", JSONObject().put("botId", botId).put("message", message))
        return when (r.code) {
            200 -> r.json?.optString("reply").orEmpty()
            403 -> "!บอท AI ถูกปิดใช้งานอยู่"
            429 -> "!ส่งข้อความถี่เกินไป ลองใหม่ภายหลัง"
            0 -> "!เชื่อมต่อเซิร์ฟเวอร์ไม่ได้"
            else -> "!เกิดข้อผิดพลาด (${r.code})"
        }
    }

    suspend fun endBotChat(user: FirebaseUser, botId: String) { call(user, "DELETE", "/bot/chat/$botId") }

    suspend fun adminBots(user: FirebaseUser): Pair<Boolean, List<AiBot>>? {
        val r = call(user, "GET", "/admin/bots")
        if (r.code != 200) return null
        val j = r.json ?: return null
        val arr = j.optJSONArray("bots") ?: JSONArray()
        return j.optBoolean("enabled", false) to (0 until arr.length()).map { parseBot(arr.getJSONObject(it)) }
    }

    suspend fun setBotsEnabled(user: FirebaseUser, enabled: Boolean): Boolean =
        call(user, "PUT", "/admin/bots/enabled", JSONObject().put("enabled", enabled)).code == 200

    suspend fun setBotEnabled(user: FirebaseUser, botId: String, enabled: Boolean): Boolean =
        call(user, "PUT", "/admin/bots/$botId", JSONObject().put("enabled", enabled)).code == 200
}
