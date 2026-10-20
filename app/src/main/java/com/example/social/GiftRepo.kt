package com.example.social

import com.example.network.BrainApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject

data class CatalogItem(val id: String, val name: String, val price: Int, val imageUrl: String, val icon: String,
                       val category: String, val enabled: Boolean, val order: Int, val animationEffect: String = "POP",
                       /** animated effect (GIF / WebP) — เปย์เปิดใจ gifts */ val effectUrl: String = "",
                       /** admin-uploaded video effect (MP4 / WebM), any channel — plays full screen when sent */ val videoUrl: String = "")

/** Gift catalog (live / vip / chat / stickers), chat gifts and VIP room decor — all via brain + RTDB catalog/. */
object GiftRepo {
    val CHANNELS = listOf("chat" to "แชท", "live" to "ไลฟ์", "vip" to "VIP", "stickers" to "สติกเกอร์ห้อง", "chatStickers" to "สติกเกอร์แชท", "payopen" to "เปย์เปิดใจ")
    private val db by lazy { FirebaseDatabase.getInstance(SocialRepo.DB_URL) }
    private fun user() = FirebaseAuth.getInstance().currentUser

    fun parse(id: String, o: DataSnapshot) = CatalogItem(id, o.child("name").getValue(String::class.java).orEmpty(),
        (o.child("price").getValue(Long::class.java) ?: 0L).toInt(), o.child("imageUrl").getValue(String::class.java).orEmpty(),
        o.child("icon").getValue(String::class.java).orEmpty(), o.child("category").getValue(String::class.java).orEmpty(),
        o.child("enabled").getValue(Boolean::class.java) != false, (o.child("order").getValue(Long::class.java) ?: 0L).toInt(),
        o.child("animationEffect").getValue(String::class.java) ?: "POP", o.child("effectUrl").getValue(String::class.java).orEmpty(),
        o.child("videoUrl").getValue(String::class.java).orEmpty())

    /** Realtime catalog of a channel (sorted by order). enabledOnly for users. */
    fun catalog(ch: String, enabledOnly: Boolean = true): Flow<List<CatalogItem>> = callbackFlow {
        val ref = db.getReference("catalog/$ch")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.map { parse(it.key.orEmpty(), it) }.filter { !enabledOnly || it.enabled }.sortedBy { it.order }) }
            override fun onCancelled(e: DatabaseError) { trySend(emptyList()) }
        }
        ref.addValueEventListener(l); awaitClose { ref.removeEventListener(l) }
    }

    data class Res(val ok: Boolean, val msg: String, val json: JSONObject? = null)
    private fun err(r: BrainApi.ApiResponse, fallback: String) = r.json?.optString("error")?.takeIf { it.isNotBlank() } ?: "$fallback (${r.code})"

    suspend fun sendGift(chatId: String, giftId: String): Res {
        val u = user() ?: return Res(false, "ยังไม่ได้เข้าสู่ระบบ")
        val r = BrainApi.call(u, "POST", "/chats/$chatId/gift", JSONObject().put("giftId", giftId))
        return if (r.code == 200) Res(true, "ส่งของขวัญแล้ว รออีกฝ่ายกดรับ (ยังไม่หักเหรียญจนกว่าจะรับ)") else Res(false, err(r, "ส่งไม่สำเร็จ"))
    }
    suspend fun accept(txId: String): Res {
        val u = user() ?: return Res(false, "ยังไม่ได้เข้าสู่ระบบ")
        val r = BrainApi.call(u, "POST", "/gifts/$txId/accept")
        return if (r.code == 200) Res(true, "รับของขวัญแล้ว +${r.json?.optInt("recipientShare")} เหรียญ" + if (r.json?.optBoolean("friended") == true) " · เป็นเพื่อนกันแล้ว 🎉" else "", r.json)
        else Res(false, err(r, "รับไม่สำเร็จ"))
    }
    suspend fun wallet(): Triple<Int, Int, Int>? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "GET", "/gifts/wallet"); val j = r.json ?: return null
        return if (r.code == 200) Triple(j.optInt("diamonds"), j.optInt("pending"), j.optInt("available")) else null
    }

    // ---- admin
    suspend fun adminList(ch: String, page: Int, size: Int = 30): Pair<List<CatalogItem>, Int> {
        val u = user() ?: return emptyList<CatalogItem>() to 0
        val r = BrainApi.call(u, "GET", "/admin/catalog/$ch?page=$page&size=$size"); val a = r.json?.optJSONArray("items") ?: return emptyList<CatalogItem>() to 0
        return (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            CatalogItem(o.optString("id"), o.optString("name"), o.optInt("price"), o.optString("imageUrl"), o.optString("icon"), o.optString("category"), o.optBoolean("enabled", true), o.optInt("order"), o.optString("animationEffect", "POP"), o.optString("effectUrl"), o.optString("videoUrl"))
        } to (r.json?.optInt("total") ?: 0)
    }
    suspend fun adminSave(ch: String, it: CatalogItem): Res {
        val u = user() ?: return Res(false, "no user")
        val r = BrainApi.call(u, "PUT", "/admin/catalog/$ch/${it.id}", JSONObject().put("name", it.name).put("price", it.price).put("category", it.category)
            .put("enabled", it.enabled).put("icon", it.icon).put("animationEffect", it.animationEffect).apply { if (it.order >= 0) put("order", it.order) })
        return if (r.code == 200) Res(true, "บันทึกแล้ว") else Res(false, err(r, "บันทึกไม่สำเร็จ"))
    }
    suspend fun adminUploadImage(ch: String, id: String, webp: ByteArray): Res {
        val u = user() ?: return Res(false, "no user")
        val r = BrainApi.callRaw(u, "PUT", "/admin/catalog/$ch/$id/image", webp, "image/webp")
        return if (r.code == 200) Res(true, "อัปโหลดรูปแล้ว (${webp.size / 1024}KB)") else Res(false, err(r, "อัปโหลดไม่สำเร็จ"))
    }
    suspend fun adminDelete(ch: String, id: String) = user()?.let { BrainApi.call(it, "DELETE", "/admin/catalog/$ch/$id").code == 200 } ?: false
    suspend fun adminUploadVideo(ch: String, id: String, bytes: ByteArray, mime: String): Res {
        val u = user() ?: return Res(false, "no user")
        val r = BrainApi.callRaw(u, "PUT", "/admin/catalog/$ch/$id/video", bytes, mime)
        return if (r.code == 200) Res(true, "อัปโหลดวิดีโอแล้ว (${bytes.size / 1024}KB)") else Res(false, err(r, "อัปโหลดวิดีโอไม่สำเร็จ"))
    }
    suspend fun adminDeleteVideo(ch: String, id: String) = user()?.let { BrainApi.call(it, "DELETE", "/admin/catalog/$ch/$id/video").code == 200 } ?: false
    suspend fun adminOrder(ch: String, ids: List<String>) = user()?.let { BrainApi.call(it, "POST", "/admin/catalog/$ch/order", JSONObject().put("ids", JSONArray(ids))).code == 200 } ?: false
    suspend fun adminConfig(): JSONObject? = user()?.let { BrainApi.call(it, "GET", "/admin/gifts/config").json }
    suspend fun adminSetConfig(c: JSONObject): Res {
        val u = user() ?: return Res(false, "no user"); val r = BrainApi.call(u, "PUT", "/admin/gifts/config", c)
        return if (r.code == 200) Res(true, "บันทึกแล้ว") else Res(false, err(r, "บันทึกไม่สำเร็จ"))
    }
    suspend fun adminTx(cursor: String?): Pair<List<JSONObject>, String?> {
        val u = user() ?: return emptyList<JSONObject>() to null
        val r = BrainApi.call(u, "GET", "/admin/gifts/tx" + (cursor?.let { "?cursor=$it" } ?: "")); val a = r.json?.optJSONArray("tx") ?: return emptyList<JSONObject>() to null
        return (0 until a.length()).map { a.getJSONObject(it) } to r.json?.optString("nextCursor")?.takeIf { it.isNotBlank() && it != "null" }
    }

    // ---- VIP room decor
    data class DecorItem(val stickerId: String, val x: Float, val y: Float, val scale: Float, val rotation: Float)
    data class Decor(val theme: String = "royal_gold", val bgColor: String = "", val pattern: String = "none", val imagePath: String = "", val animated: Boolean = false,
                     val frame: String = "gold", val effect: String = "sparkle", val bubbleStyle: String = "rounded", val bubbleColor: String = "#7C3AED",
                     val textColor: String = "#FFFFFF", val items: List<DecorItem> = emptyList())
    data class DecorState(val decor: Decor?, val canEdit: Boolean, val ownerUid: String, val hiddenReason: String)
    private fun parseDecor(d: JSONObject) = Decor(d.optString("theme"), d.optJSONObject("bg")?.optString("color").orEmpty(), d.optJSONObject("bg")?.optString("pattern") ?: "none",
        d.optJSONObject("bg")?.optString("imagePath").orEmpty(), d.optJSONObject("bg")?.optBoolean("animated") == true, d.optString("frame", "none"), d.optString("effect", "none"),
        d.optJSONObject("bubble")?.optString("style") ?: "default", d.optJSONObject("bubble")?.optString("color") ?: "#7C3AED", d.optJSONObject("bubble")?.optString("textColor") ?: "#FFFFFF",
        d.optJSONArray("items")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { DecorItem(it.optString("stickerId"), it.optDouble("x").toFloat(), it.optDouble("y").toFloat(), it.optDouble("scale", 1.0).toFloat(), it.optDouble("rotation").toFloat()) } } ?: emptyList())
    fun decorJson(d: Decor) = JSONObject().put("theme", d.theme).put("bg", JSONObject().put("color", d.bgColor).put("pattern", d.pattern).put("imagePath", d.imagePath).put("animated", d.animated))
        .put("frame", d.frame).put("effect", d.effect).put("bubble", JSONObject().put("style", d.bubbleStyle).put("color", d.bubbleColor).put("textColor", d.textColor))
        .put("items", JSONArray(d.items.map { JSONObject().put("stickerId", it.stickerId).put("x", it.x.toDouble()).put("y", it.y.toDouble()).put("scale", it.scale.toDouble()).put("rotation", it.rotation.toDouble()) }))
    suspend fun decor(chatId: String): DecorState? {
        val u = user() ?: return null; val r = BrainApi.call(u, "GET", "/rooms/$chatId/decor"); val j = r.json ?: return null
        if (r.code != 200) return null
        return DecorState(j.optJSONObject("decor")?.let { parseDecor(it) }, j.optBoolean("canEdit"), j.optString("ownerUid"), if (j.optBoolean("hidden")) j.optString("reason") else "")
    }
    suspend fun saveDecor(chatId: String, d: Decor): Res {
        val u = user() ?: return Res(false, "no user"); val r = BrainApi.call(u, "PUT", "/rooms/$chatId/decor", JSONObject().put("decor", decorJson(d)))
        return if (r.code == 200) Res(true, "บันทึกการตกแต่งแล้ว") else Res(false, err(r, "บันทึกไม่สำเร็จ"))
    }
    suspend fun resetDecor(chatId: String): Res {
        val u = user() ?: return Res(false, "no user"); val r = BrainApi.call(u, "DELETE", "/rooms/$chatId/decor")
        return if (r.code == 200) Res(true, "รีเซ็ตแล้ว") else Res(false, err(r, "รีเซ็ตไม่สำเร็จ"))
    }
    suspend fun uploadDecorImage(chatId: String, bytes: ByteArray, mime: String): Pair<String, Boolean>? {
        val u = user() ?: return null; val r = BrainApi.callRaw(u, "POST", "/rooms/$chatId/decor/image", bytes, mime)
        return if (r.code == 200) r.json!!.optString("imagePath") to r.json.optBoolean("animated") else null
    }
    suspend fun reportDecor(chatId: String, reason: String) = user()?.let { BrainApi.call(it, "POST", "/rooms/$chatId/decor/report", JSONObject().put("reason", reason)).code == 200 } ?: false
    fun decorImageUrl(chatId: String, path: String) = "${BrainApi.BASE_URL}/rooms/$chatId/decor/image?path=" + android.net.Uri.encode(path)
    /** Realtime 'rev' so both members refetch when decor changes. */
    fun decorRev(chatId: String): Flow<Long> = callbackFlow {
        val ref = db.getReference("roomDecor/$chatId/rev")
        val l = object : ValueEventListener { override fun onDataChange(s: DataSnapshot) { trySend(s.getValue(Long::class.java) ?: 0L) }; override fun onCancelled(e: DatabaseError) {} }
        ref.addValueEventListener(l); awaitClose { ref.removeEventListener(l) }
    }
    suspend fun previews(chatIds: List<String>): Map<String, Decor> {
        val u = user() ?: return emptyMap(); if (chatIds.isEmpty()) return emptyMap()
        val j = BrainApi.call(u, "POST", "/rooms/decor/previews", JSONObject().put("chatIds", JSONArray(chatIds))).json?.optJSONObject("previews") ?: return emptyMap()
        return j.keys().asSequence().associateWith { k -> val o = j.getJSONObject(k); Decor(theme = o.optString("theme"), frame = o.optString("frame"), bgColor = o.optString("bgColor"), bubbleColor = o.optString("bubbleColor")) }
    }
}
