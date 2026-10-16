package com.example.social

import com.example.network.BrainApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** One 'เปย์เปิดใจ' gift in the catalog (admin-managed, prices live from the server). */
data class PayGift(val id: String, val name: String, val price: Int, val imageUrl: String, val effectUrl: String, val icon: String, val fx: String)

/** A gift sent to me that is waiting for ยอมรับ / ปฏิเสธ (or the sender's view of one). */
data class PayOpen(
    val id: String, val state: String, val from: String, val to: String, val giftName: String, val imageUrl: String, val effectUrl: String, val fx: String,
    val price: Int, val deadline: Long, val chatId: String, val fromName: String, val fromAvatar: String, val fromGender: String, val voiceGender: String,
    /** server clock − my clock (ms), so the countdown uses the server's deadline correctly */
    val skew: Long = 0
)

data class PayVoice(val id: String, val text: String, val gender: String, val kind: String, val audioUrl: String, val order: Int)

/** Result the sender gets once the recipient answered (or the time ran out). */
data class PayResult(val id: String, val state: String, val chatId: String, val price: Int, val giftName: String, val voiceGender: String, val at: Long)

/**
 * เปย์เปิดใจ: the server takes the diamonds when the gift is sent, then either pays the recipient (ยอมรับ within 10 s → friends + chat)
 * or refunds the sender in full (ปฏิเสธ / no answer). The app never computes or writes balances — it only shows what the server decided.
 */
object PayOpenRepo {
    private fun user() = FirebaseAuth.getInstance().currentUser
    private val db by lazy { FirebaseDatabase.getInstance(SocialRepo.DB_URL) }
    private fun err(r: BrainApi.ApiResponse, fallback: String) = r.json?.optString("error")?.takeIf { it.isNotBlank() } ?: "$fallback (${r.code})"

    fun parse(o: JSONObject, skew: Long = 0) = PayOpen(o.optString("id"), o.optString("state"), o.optString("from"), o.optString("to"), o.optString("giftName"),
        o.optString("imageUrl"), o.optString("effectUrl"), o.optString("fx"), o.optInt("price"), o.optLong("deadline"), o.optString("chatId"),
        o.optString("fromName"), o.optString("fromAvatar"), o.optString("fromGender"), o.optString("voiceGender"),
        if (o.has("serverNow")) o.optLong("serverNow") - System.currentTimeMillis() else skew)

    private fun snap(s: DataSnapshot): PayOpen? {
        val v = s.value as? Map<*, *> ?: return null
        return runCatching { parse(JSONObject(v), 0) }.getOrNull()?.copy(id = s.key.orEmpty(), skew = serverSkew)
    }

    @Volatile var serverSkew = 0L
    private fun watchSkew() = runCatching {
        db.getReference(".info/serverTimeOffset").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { serverSkew = (s.getValue(Double::class.java) ?: 0.0).toLong() }
            override fun onCancelled(e: DatabaseError) {}
        })
    }
    private var skewOn = false

    suspend fun catalog(): Pair<List<PayGift>, Int>? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "GET", "/payopen/catalog"); val j = r.json ?: return null
        if (r.code != 200) return null
        val a = j.optJSONArray("items") ?: return emptyList<PayGift>() to j.optInt("diamonds")
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            PayGift(it.optString("id"), it.optString("name"), it.optInt("price"), it.optString("imageUrl"), it.optString("effectUrl"), it.optString("icon"), it.optString("fx"))
        } to j.optInt("diamonds")
    }

    data class Sent(val ok: Boolean, val id: String, val msg: String, val code: String = "")
    suspend fun send(toUid: String, gift: PayGift): Sent {
        val u = user() ?: return Sent(false, "", "กรุณาเข้าสู่ระบบก่อน")
        val r = BrainApi.call(u, "POST", "/payopen/send", JSONObject().put("toUid", toUid).put("giftId", gift.id).put("expectedPrice", gift.price))
        return if (r.code == 200) Sent(true, r.json?.optString("id").orEmpty(), "")
        else Sent(false, "", err(r, "ส่งไม่สำเร็จ"), r.json?.optString("code").orEmpty())
    }
    suspend fun respond(id: String, accept: Boolean): PayOpen? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "POST", "/payopen/$id/respond", JSONObject().put("accept", accept))
        return if (r.code == 200 && r.json != null) parse(r.json) else null
    }
    suspend fun status(id: String): PayOpen? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "GET", "/payopen/$id")
        return if (r.code == 200 && r.json != null) parse(r.json) else null
    }
    suspend fun voices(): List<PayVoice> {
        val u = user() ?: return emptyList()
        return parseVoices(BrainApi.call(u, "GET", "/payopen/voices").json)
    }
    private fun parseVoices(j: JSONObject?): List<PayVoice> {
        val a = j?.optJSONArray("voices") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.map { PayVoice(it.optString("id"), it.optString("text"), it.optString("gender"), it.optString("kind"), it.optString("audioUrl"), it.optInt("order")) }
    }

    /**
     * Gifts waiting for me: realtime from payOpenInbox/{me}; if the database rule is not deployed yet (permission denied),
     * falls back to asking the server every few seconds.
     */
    fun inbox(uid: String): Flow<List<PayOpen>> = callbackFlow {
        if (!skewOn) { skewOn = true; watchSkew() }
        val items = LinkedHashMap<String, PayOpen>()
        var polling: kotlinx.coroutines.Job? = null
        fun push() { trySend(items.values.toList()) }
        val ref = db.getReference("payOpenInbox/$uid")
        val l = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, p: String?) { snap(s)?.let { items[it.id] = it; push() } }
            override fun onChildChanged(s: DataSnapshot, p: String?) { snap(s)?.let { items[it.id] = it; push() } }
            override fun onChildRemoved(s: DataSnapshot) { items.remove(s.key); push() }
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            override fun onCancelled(e: DatabaseError) {
                polling = launch {
                    while (true) {
                        val u = user()
                        val a = u?.let { BrainApi.call(it, "GET", "/payopen/inbox").json?.optJSONArray("items") }
                        if (a != null) { items.clear(); (0 until a.length()).forEach { val x = parse(a.getJSONObject(it)); items[x.id] = x }; push() }
                        delay(4000)
                    }
                }
            }
        }
        ref.addChildEventListener(l)
        awaitClose { ref.removeEventListener(l); polling?.cancel() }
    }

    /** Answers to gifts I sent (accepted → chat, refunded → diamonds fly back). Only results newer than [since]. */
    fun results(uid: String, since: Long): Flow<PayResult> = callbackFlow {
        val ref = db.getReference("payOpenResults/$uid")
        val q = ref.orderByChild("at").startAt(since.toDouble())
        val l = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, p: String?) {
                val at = s.child("at").getValue(Long::class.java) ?: 0L
                if (at < since) return
                trySend(PayResult(s.key.orEmpty(), s.child("state").getValue(String::class.java).orEmpty(), s.child("chatId").getValue(String::class.java).orEmpty(),
                    (s.child("price").getValue(Long::class.java) ?: 0L).toInt(), s.child("giftName").getValue(String::class.java).orEmpty(),
                    s.child("voiceGender").getValue(String::class.java).orEmpty(), at))
            }
            override fun onChildChanged(s: DataSnapshot, p: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            override fun onCancelled(e: DatabaseError) {} // rule not deployed yet → the sender still polls status() while waiting
        }
        q.addChildEventListener(l)
        awaitClose { q.removeEventListener(l) }
    }

    /** Results the sender already reacted to (shared between the swipe page's polling and the realtime listener → each plays once). */
    val handled = java.util.Collections.synchronizedSet(HashSet<String>())

    // ---- admin: voices
    suspend fun adminVoices(): List<PayVoice> = user()?.let { parseVoices(BrainApi.call(it, "GET", "/admin/payopen-voices").json) } ?: emptyList()
    suspend fun adminSaveVoice(v: PayVoice): GiftRepo.Res {
        val u = user() ?: return GiftRepo.Res(false, "no user")
        val r = BrainApi.call(u, "PUT", "/admin/payopen-voices/${v.id}", JSONObject().put("text", v.text).put("gender", v.gender).put("kind", v.kind).apply { if (v.order >= 0) put("order", v.order) })
        return if (r.code == 200) GiftRepo.Res(true, "บันทึกแล้ว") else GiftRepo.Res(false, err(r, "บันทึกไม่สำเร็จ"))
    }
    suspend fun adminUploadVoice(id: String, bytes: ByteArray, mime: String): GiftRepo.Res {
        val u = user() ?: return GiftRepo.Res(false, "no user")
        val r = BrainApi.callRaw(u, "PUT", "/admin/payopen-voices/$id/audio", bytes, mime)
        return if (r.code == 200) GiftRepo.Res(true, "อัปโหลดเสียงแล้ว (${bytes.size / 1024}KB)") else GiftRepo.Res(false, err(r, "อัปโหลดไม่สำเร็จ"))
    }
    suspend fun adminDeleteVoice(id: String) = user()?.let { BrainApi.call(it, "DELETE", "/admin/payopen-voices/$id").code == 200 } ?: false

    // ---- admin: animated effect of a gift
    suspend fun adminUploadEffect(ch: String, id: String, bytes: ByteArray, mime: String): GiftRepo.Res {
        val u = user() ?: return GiftRepo.Res(false, "no user")
        val r = BrainApi.callRaw(u, "PUT", "/admin/catalog/$ch/$id/effect", bytes, mime)
        return if (r.code == 200) GiftRepo.Res(true, "อัปโหลดเอฟเฟกต์แล้ว (${bytes.size / 1024}KB)") else GiftRepo.Res(false, err(r, "อัปโหลดไม่สำเร็จ"))
    }
    suspend fun adminDeleteEffect(ch: String, id: String) = user()?.let { BrainApi.call(it, "DELETE", "/admin/catalog/$ch/$id/effect").code == 200 } ?: false
}
