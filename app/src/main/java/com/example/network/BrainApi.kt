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
    const val BASE_URL = "https://friendtalk-brain-123224091480.asia-southeast1.run.app"

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
                        "moderator" -> UserRole.MODERATOR
                        else -> UserRole.USER
                    }
                    MeResult(role, raw)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /** code = HTTP status (0 = network error); body = parsed JSON or null */
    data class ApiResponse(val code: Int, val json: JSONObject?)

    /** Raw upload (image bytes) — used for gift assets and room-decor images. */
    suspend fun callRaw(user: FirebaseUser, method: String, path: String, bytes: ByteArray, mime: String): ApiResponse {
        val token = idToken(user) ?: return ApiResponse(0, null)
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url("$BASE_URL$path").header("Authorization", "Bearer $token")
                    .method(method, bytes.toRequestBody(mime.toMediaType())).build()
                client.newCall(req).execute().use { resp -> ApiResponse(resp.code, runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrNull()) }
            } catch (e: Exception) { ApiResponse(0, null) }
        }
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
                    val j = runCatching { JSONObject(text) }.getOrNull()
                    if (resp.code == 429 && j?.optString("code") == "quota") com.example.ads.AdRoom.quotaHit.value = j.optString("feature")
                    ApiResponse(resp.code, j)
                }
            } catch (e: Exception) { ApiResponse(0, null) }
        }
    }

}
