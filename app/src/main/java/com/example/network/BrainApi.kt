package com.example.network

import com.example.model.UserRole
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
}
