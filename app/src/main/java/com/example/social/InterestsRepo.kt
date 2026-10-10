package com.example.social

import com.example.network.BrainApi
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

data class InterestCategory(val key: String, val label: String, val options: List<String>)
data class Similarity(val percent: Int, val shared: Map<String, List<String>>, val isMatch: Boolean)
data class FullProfile(val profile: RealProfile, val interests: Map<String, List<String>>, val similarity: Similarity?)

/** Interest profile + similarity, all computed/validated by friendtalk-brain. */
object InterestsRepo {
    private fun user() = FirebaseAuth.getInstance().currentUser
    var categories: List<InterestCategory> = emptyList(); private set

    fun strMap(o: JSONObject?): Map<String, List<String>> = o?.keys()?.asSequence()?.associateWith { k ->
        o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
    } ?: emptyMap()
    fun parseSim(o: JSONObject?): Similarity? = o?.let { Similarity(it.optInt("percent"), strMap(it.optJSONObject("shared")), it.optBoolean("isMatch")) }

    suspend fun config(): List<InterestCategory> {
        if (categories.isNotEmpty()) return categories
        val u = user() ?: return emptyList()
        val c = BrainApi.call(u, "GET", "/interests/config").json?.optJSONObject("categories") ?: return emptyList()
        categories = c.keys().asSequence().map { k -> val o = c.getJSONObject(k)
            InterestCategory(k, o.optString("label", k), o.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()) }
            .sortedByDescending { listOf("lookingFor", "personality").indexOf(it.key).let { i -> if (i < 0) -1 else 10 - i } }.toList()
        return categories
    }
    fun label(cat: String) = categories.firstOrNull { it.key == cat }?.label ?: cat

    suspend fun save(map: Map<String, List<String>>): Boolean {
        val u = user() ?: return false
        val b = JSONObject(); map.forEach { (k, v) -> if (v.isNotEmpty()) b.put(k, JSONArray(v)) }
        return BrainApi.call(u, "PUT", "/me/interests", b).code == 200
    }
    suspend fun profile(uid: String): FullProfile? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "GET", "/users/$uid/profile"); val j = r.json?.takeIf { r.code == 200 } ?: return null
        return FullProfile(NearbyRepo.parseProfile(j), strMap(j.optJSONObject("interestProfile")), parseSim(j.optJSONObject("similarity")))
    }
    data class PersonalityResult(val users: List<FullProfile>, val needInterests: Boolean)
    suspend fun personalityMatches(): PersonalityResult? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "GET", "/match/personality")
        if (r.code == 409) return PersonalityResult(emptyList(), true)
        val arr = r.json?.takeIf { r.code == 200 }?.optJSONArray("users") ?: return null
        return PersonalityResult((0 until arr.length()).map { arr.getJSONObject(it) }.map {
            FullProfile(NearbyRepo.parseProfile(it), strMap(it.optJSONObject("interestProfile")), parseSim(it.optJSONObject("similarity")))
        }, false)
    }
    data class FriendResult(val status: String, val similarity: Similarity?)
    /** Sends immediately; brain returns similarity + shared interests in the same call. */
    suspend fun sendFriendRequest(toUid: String): FriendResult? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "POST", "/friends/request", JSONObject().put("toUid", toUid))
        return if (r.code == 200) FriendResult(r.json?.optString("status").orEmpty(), parseSim(r.json?.optJSONObject("similarity"))) else null
    }
}
