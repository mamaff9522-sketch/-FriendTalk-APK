package com.example.social

import android.content.Context
import com.example.network.BrainApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import org.json.JSONArray
import org.json.JSONObject

data class NearbyUser(val profile: RealProfile, val distance: String)
data class FriendRequest(val profile: RealProfile, val at: Long, val similarity: Similarity? = null)

/** Friends, radar and shake via friendtalk-brain. Exact coordinates never leave the server. */
object NearbyRepo {
    val RADII = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 25.0, 50.0)
    private fun user() = FirebaseAuth.getInstance().currentUser

    fun radiusLabel(r: Double) = if (r < 1) "${(r * 1000).toInt()} ม." else "${r.toInt()} กม."
    private fun rStr(r: Double) = if (r < 1) "0.5" else r.toInt().toString()

    fun loadRadius(ctx: Context): Double =
        ctx.getSharedPreferences("ft_social", Context.MODE_PRIVATE).getFloat("radiusKm", 0.5f).toDouble().takeIf { it in RADII } ?: 0.5

    fun saveRadius(ctx: Context, r: Double) {
        ctx.getSharedPreferences("ft_social", Context.MODE_PRIVATE).edit().putFloat("radiusKm", r.toFloat()).apply()
        val uid = user()?.uid ?: return
        val v: Any = if (r < 1) 0.5 else r.toInt()
        FirebaseDatabase.getInstance(SocialRepo.DB_URL).getReference("users/$uid/settings/radiusKm").setValue(v)
    }

    fun parseProfile(o: JSONObject): RealProfile {
        val ints = o.optJSONArray("interests")
        return RealProfile(o.optString("uid"), o.optString("displayName"), o.optString("username"), o.optString("avatar"),
            o.optString("bio"), if (o.isNull("age")) null else o.optInt("age"), o.optString("gender"),
            if (ints == null) emptyList() else (0 until ints.length()).map { ints.optString(it) })
    }
    private fun arr(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it) }

    suspend fun shareLocation(lat: Double, lng: Double): Boolean =
        user()?.let { BrainApi.call(it, "POST", "/radar/location", JSONObject().put("lat", lat).put("lng", lng)).code == 200 } ?: false
    suspend fun hide(): Boolean = user()?.let { BrainApi.call(it, "DELETE", "/radar/location").code == 200 } ?: false
    /** Last radar result (radar screen / หาเพื่อน card). Home reuses it so it never spends radar quota. */
    val lastNearby = kotlinx.coroutines.flow.MutableStateFlow<List<NearbyUser>?>(null)
    suspend fun nearby(radiusKm: Double): List<NearbyUser>? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "GET", "/radar/nearby?radiusKm=${rStr(radiusKm)}")
        if (r.code != 200) return null
        return arr(r.json?.optJSONArray("users")).map { NearbyUser(parseProfile(it), it.optString("distance")) }.also { lastNearby.value = it }
    }

    /** Returns status: requested / friends / already_friends, or null on failure. */
    suspend fun requestFriend(toUid: String): String? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "POST", "/friends/request", JSONObject().put("toUid", toUid))
        return if (r.code == 200) r.json?.optString("status") else null
    }
    suspend fun respond(fromUid: String, accept: Boolean): String? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "POST", "/friends/respond", JSONObject().put("fromUid", fromUid).put("accept", accept))
        return if (r.code == 200) (r.json?.optString("chatId")?.ifBlank { null } ?: "") else null
    }
    suspend fun requests(): List<FriendRequest> {
        val u = user() ?: return emptyList()
        return arr(BrainApi.call(u, "GET", "/friends/requests").json?.optJSONArray("requests")).map { FriendRequest(parseProfile(it), it.optLong("at"),
            if (it.isNull("similarity")) null else Similarity(it.optInt("similarity"), InterestsRepo.strMap(it.optJSONObject("shared")), true)) }
    }
    data class SearchResult(val users: List<NearbyUser>, val nearby: List<NearbyUser>, val nearbyStatus: String, val code: Int)
    /** Name search and/or nearby people (near = radar). Nearby name matches come first with a distance. */
    suspend fun search(q: String, near: Boolean, radiusKm: Double): SearchResult? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "GET", "/search/users?q=" + android.net.Uri.encode(q) + (if (near) "&near=1&radiusKm=${rStr(radiusKm)}" else ""))
        val j = r.json ?: return SearchResult(emptyList(), emptyList(), "error", r.code)
        if (r.code != 200) return SearchResult(emptyList(), emptyList(), j.optString("code", "error"), r.code)
        val nb = arr(j.optJSONArray("nearby")).map { NearbyUser(parseProfile(it), it.optString("distance")) }
        if (near && j.optString("nearbyStatus") == "ok") lastNearby.value = nb
        return SearchResult(arr(j.optJSONArray("users")).map { NearbyUser(parseProfile(it), it.optString("distance")) }, nb, j.optString("nearbyStatus"), 200)
    }
    /** Friends with live online flag (presence; hidden users never show online). */
    suspend fun friendsWithOnline(): List<Pair<RealProfile, Boolean>> {
        val u = user() ?: return emptyList()
        return arr(BrainApi.call(u, "GET", "/friends").json?.optJSONArray("friends")).map { parseProfile(it) to it.optBoolean("online") }
    }
    suspend fun friends(): List<RealProfile> {
        val u = user() ?: return emptyList()
        return arr(BrainApi.call(u, "GET", "/friends").json?.optJSONArray("friends")).map { parseProfile(it) }
    }

    /** Everyone online now (presence, not hidden), random order, filtered by [DiscoverFilter] on the server. */
    data class OnlineList(val users: List<NearbyUser>, val hasLocation: Boolean, val code: Int)
    suspend fun onlineUsers(): OnlineList {
        val u = user() ?: return OnlineList(emptyList(), false, 0)
        val r = BrainApi.call(u, "GET", "/discover/online")
        return OnlineList(arr(r.json?.optJSONArray("users")).map { NearbyUser(parseProfile(it), it.optString("distance")) }, r.json?.optBoolean("hasLocation") == true, r.code)
    }

    /** status: waiting / matched / timeout. A match now opens an anonymous room ([room]); [user] is only set by old servers. */
    data class ShakeState(val status: String, val user: RealProfile?, val room: String = "")
    suspend fun shake(lat: Double?, lng: Double?, radiusKm: Double): ShakeState? {
        val u = user() ?: return null
        val b = JSONObject().put("radiusKm", if (radiusKm < 1) 0.5 else radiusKm.toInt())
        if (lat != null && lng != null) b.put("lat", lat).put("lng", lng)
        val r = BrainApi.call(u, "POST", "/shake", b)
        val j = r.json ?: return null
        if (r.code != 200) return null
        return ShakeState(j.optString("status"), j.optJSONObject("user")?.let { parseProfile(it) }, j.optString("room"))
    }
    suspend fun shakeResult(): ShakeState? {
        val u = user() ?: return null
        val j = BrainApi.call(u, "GET", "/shake/result").json ?: return null
        return ShakeState(j.optString("status"), j.optJSONObject("user")?.let { parseProfile(it) }, j.optString("room"))
    }

    // ---- anonymous shake room (server-timed 60 s) ----
    data class AnonMsg(val mine: Boolean, val text: String, val at: Long)
    data class AnonState(val state: String, val elapsedMs: Long, val heartAfterMs: Long, val durationMs: Long, val myAlias: String, val otherAlias: String,
                         val myHeart: Boolean, val messages: List<AnonMsg>, val chatId: String = "", val me: RealProfile? = null, val other: RealProfile? = null)
    private fun anonState(j: JSONObject) = AnonState(j.optString("state"), j.optLong("elapsedMs"), j.optLong("heartAfterMs", 20_000), j.optLong("durationMs", 60_000),
        j.optString("myAlias"), j.optString("otherAlias"), j.optBoolean("myHeart"),
        arr(j.optJSONArray("messages")).map { AnonMsg(it.optBoolean("mine"), it.optString("text"), it.optLong("at")) },
        j.optString("chatId"), j.optJSONObject("me")?.let { parseProfile(it) }, j.optJSONObject("other")?.let { parseProfile(it) })
    suspend fun anonGet(room: String, after: Long = 0): AnonState? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "GET", "/anon/$room?after=$after")
        return if (r.code == 200) r.json?.let { anonState(it) } else null
    }
    suspend fun anonSend(room: String, text: String): Boolean =
        user()?.let { BrainApi.call(it, "POST", "/anon/$room/msg", JSONObject().put("text", text)).code == 200 } ?: false
    suspend fun anonHeart(room: String): AnonState? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "POST", "/anon/$room/heart")
        return if (r.code == 200) r.json?.let { anonState(it) } else null
    }
    suspend fun anonLeave(room: String) { user()?.let { BrainApi.call(it, "POST", "/anon/$room/leave") } }

    // ---- bell: one list of all notifications ----
    data class Notif(val type: String, val uid: String, val name: String, val avatar: String, val at: Long, val unseen: Boolean,
                     val postId: String = "", val snippet: String = "", val chatId: String = "", val id: String = "")
    data class NotifList(val items: List<Notif>, val unseen: Int, val code: Int)
    suspend fun notifications(): NotifList {
        val u = user() ?: return NotifList(emptyList(), 0, 0)
        val r = BrainApi.call(u, "GET", "/notifications")
        return NotifList(arr(r.json?.optJSONArray("items")).map { Notif(it.optString("type"), it.optString("uid"), it.optString("name"), it.optString("avatar"),
            it.optLong("at"), it.optBoolean("unseen"), it.optString("postId"), it.optString("snippet"), it.optString("chatId"), it.optString("id")) },
            r.json?.optInt("unseen") ?: 0, r.code)
    }
    suspend fun markNotificationsSeen() { user()?.let { BrainApi.call(it, "POST", "/notifications/seen") } }
}
