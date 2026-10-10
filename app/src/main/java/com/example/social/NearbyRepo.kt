package com.example.social

import android.content.Context
import com.example.network.BrainApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import org.json.JSONArray
import org.json.JSONObject

data class NearbyUser(val profile: RealProfile, val distance: String)
data class FriendRequest(val profile: RealProfile, val at: Long)

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
    suspend fun nearby(radiusKm: Double): List<NearbyUser>? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "GET", "/radar/nearby?radiusKm=${rStr(radiusKm)}")
        if (r.code != 200) return null
        return arr(r.json?.optJSONArray("users")).map { NearbyUser(parseProfile(it), it.optString("distance")) }
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
        return arr(BrainApi.call(u, "GET", "/friends/requests").json?.optJSONArray("requests")).map { FriendRequest(parseProfile(it), it.optLong("at")) }
    }
    suspend fun friends(): List<RealProfile> {
        val u = user() ?: return emptyList()
        return arr(BrainApi.call(u, "GET", "/friends").json?.optJSONArray("friends")).map { parseProfile(it) }
    }

    data class ShakeState(val status: String, val user: RealProfile?)
    suspend fun shake(lat: Double?, lng: Double?, radiusKm: Double): ShakeState? {
        val u = user() ?: return null
        val b = JSONObject().put("radiusKm", if (radiusKm < 1) 0.5 else radiusKm.toInt())
        if (lat != null && lng != null) b.put("lat", lat).put("lng", lng)
        val r = BrainApi.call(u, "POST", "/shake", b)
        val j = r.json ?: return null
        if (r.code != 200) return null
        return ShakeState(j.optString("status"), j.optJSONObject("user")?.let { parseProfile(it) })
    }
    suspend fun shakeResult(): ShakeState? {
        val u = user() ?: return null
        val j = BrainApi.call(u, "GET", "/shake/result").json ?: return null
        return ShakeState(j.optString("status"), j.optJSONObject("user")?.let { parseProfile(it) })
    }
}
