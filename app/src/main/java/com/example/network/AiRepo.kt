package com.example.network

import com.google.firebase.auth.FirebaseUser
import org.json.JSONArray
import org.json.JSONObject

/** All AI-character (bot) backend calls in one place. Uses BrainApi.call for auth/HTTP. */
object AiRepo {
    data class AiBot(val id: String, val name: String, val avatarUrl: String, val bio: String, val interests: List<String>, val enabled: Boolean)
    data class MatchResult(val type: String, val partnerUid: String? = null, val bot: AiBot? = null, val chatId: String? = null)
    internal fun parseBot(o: JSONObject): AiBot {
        val arr = o.optJSONArray("interests") ?: JSONArray()
        return AiBot(
            id = o.optString("id"), name = o.optString("name"), avatarUrl = o.optString("avatarUrl"),
            bio = o.optString("bio"), interests = (0 until arr.length()).map { arr.optString(it) },
            enabled = o.optBoolean("enabled", false)
        )
    }

    suspend fun match(user: FirebaseUser): MatchResult? {
        val r = BrainApi.call(user, "POST", "/match")
        val j = r.json ?: return null
        if (r.code != 200) return null
        return when (j.optString("type")) {
            "bot" -> MatchResult("bot", bot = j.optJSONObject("bot")?.let { parseBot(it) })
            "user" -> MatchResult("user", partnerUid = j.optString("uid"), chatId = j.optString("chatId").ifBlank { null })
            else -> MatchResult("waiting")
        }
    }

    /** Returns reply text, or an error message prefixed with "!" */
    suspend fun botChat(user: FirebaseUser, botId: String, message: String): String {
        val r = BrainApi.call(user, "POST", "/bot/chat", JSONObject().put("botId", botId).put("message", message))
        return when (r.code) {
            200 -> r.json?.optString("reply").orEmpty()
            403 -> "!บอท AI ถูกปิดใช้งานอยู่"
            429 -> if (r.json?.optString("code") == "quota") "!QUOTA" else "!ส่งข้อความถี่เกินไป ลองใหม่ภายหลัง"
            0 -> "!เชื่อมต่อเซิร์ฟเวอร์ไม่ได้"
            else -> "!เกิดข้อผิดพลาด (${r.code})"
        }
    }

    suspend fun endBotChat(user: FirebaseUser, botId: String) { BrainApi.call(user, "DELETE", "/bot/chat/$botId") }

    suspend fun adminBots(user: FirebaseUser): Pair<Boolean, List<AiBot>>? {
        val r = BrainApi.call(user, "GET", "/admin/bots")
        if (r.code != 200) return null
        val j = r.json ?: return null
        val arr = j.optJSONArray("bots") ?: JSONArray()
        return j.optBoolean("enabled", false) to (0 until arr.length()).map { parseBot(arr.getJSONObject(it)) }
    }

    suspend fun setBotsEnabled(user: FirebaseUser, enabled: Boolean): Boolean =
        BrainApi.call(user, "PUT", "/admin/bots/enabled", JSONObject().put("enabled", enabled)).code == 200

    suspend fun setBotEnabled(user: FirebaseUser, botId: String, enabled: Boolean): Boolean =
        BrainApi.call(user, "PUT", "/admin/bots/$botId", JSONObject().put("enabled", enabled)).code == 200
}
