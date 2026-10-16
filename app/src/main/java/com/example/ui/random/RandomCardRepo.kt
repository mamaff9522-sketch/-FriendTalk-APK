package com.example.ui.random

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.example.network.BrainApi
import com.example.social.NearbyRepo
import com.example.social.RealProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/** Position of one element on the card: centre as a fraction of the card (0..1) + scale. */
data class Place(val x: Float, val y: Float, val s: Float = 1f)
data class CardLayout(val avatar: Place = Place(0.5f, 0.36f), val name: Place = Place(0.5f, 0.62f), val intro: Place = Place(0.5f, 0.78f))

/** A decorated 'สุ่มหาเพื่อน' card as other people see it. */
data class RandomCard(
    val avatarUrl: String = "", val bgUrl: String = "", val intro: String = "", val videoUrl: String = "", val audioUrl: String = "",
    val frame: String = "none", val frameColors: List<Color> = emptyList(), val nameFrame: String = "none", val nameFrameColors: List<Color> = emptyList(),
    val effect: String = "none", val effectGlyphs: List<String> = emptyList(), val layout: CardLayout = CardLayout()
)

data class CardItem(val id: String, val name: String, val price: Int, val colors: List<Color>, val glyphs: List<String>)
data class CardCatalog(val enabled: Boolean, val videoPrice: Int, val audioPrice: Int, val frames: List<CardItem>, val nameFrames: List<CardItem>,
                       val effects: List<CardItem>, val vip: Boolean, val diamonds: Int)

object RandomCardRepo {
    private fun user() = FirebaseAuth.getInstance().currentUser
    fun hex(s: String): Color? = runCatching { Color(android.graphics.Color.parseColor(s)) }.getOrNull()
    private fun colors(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).mapNotNull { hex(a.optString(it)) }
    private fun strs(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
    private fun place(o: JSONObject?, d: Place) = if (o == null) d else Place(o.optDouble("x", d.x.toDouble()).toFloat(), o.optDouble("y", d.y.toDouble()).toFloat(), o.optDouble("s", 1.0).toFloat())

    fun parseCard(o: JSONObject?): RandomCard? {
        o ?: return null
        val l = o.optJSONObject("layout"); val d = CardLayout()
        return RandomCard(o.optString("avatarUrl"), o.optString("bgUrl"), o.optString("intro"), o.optString("videoUrl"), o.optString("audioUrl"),
            o.optString("frame", "none"), colors(o.optJSONArray("frameColors")), o.optString("nameFrame", "none"), colors(o.optJSONArray("nameFrameColors")),
            o.optString("effect", "none"), strs(o.optJSONArray("effectGlyphs")),
            CardLayout(place(l?.optJSONObject("avatar"), d.avatar), place(l?.optJSONObject("name"), d.name), place(l?.optJSONObject("intro"), d.intro)))
    }

    sealed class Next {
        data class Person(val profile: RealProfile, val online: Boolean, val card: RandomCard?) : Next()
        data class Bot(val bot: com.example.network.AiRepo.AiBot) : Next()
        data class Nobody(val msg: String) : Next()
    }
    /** One random person (online first, 'ต้องการ' filter applied by the server). [skip] = people passed with 💔 this session. */
    suspend fun next(skip: List<String>): Next {
        val u = user() ?: return Next.Nobody("กรุณาเข้าสู่ระบบก่อน")
        val r = BrainApi.call(u, "POST", "/random/next", JSONObject().put("skip", JSONArray(skip.takeLast(100))))
        val j = r.json
        if (r.code == 404) return Next.Nobody("เซิร์ฟเวอร์ยังไม่อัปเดต")
        if (r.code == 429) return Next.Nobody("สุ่มครบโควตาวันนี้แล้ว")
        if (r.code != 200 || j == null) return Next.Nobody(j?.optString("error")?.ifBlank { null } ?: "สุ่มไม่สำเร็จ ลองใหม่อีกครั้ง")
        return when (j.optString("type")) {
            "user" -> j.optJSONObject("user")?.let { Next.Person(NearbyRepo.parseProfile(it), it.optBoolean("online"), parseCard(j.optJSONObject("card"))) } ?: Next.Nobody("สุ่มไม่สำเร็จ")
            "bot" -> j.optJSONObject("bot")?.let { b -> Next.Bot(com.example.network.AiRepo.parseBot(b)) } ?: Next.Nobody("สุ่มไม่สำเร็จ")
            else -> Next.Nobody("ยังไม่เจอใครที่ตรงกับที่คุณต้องการ ลองใหม่หรือปรับ 'ต้องการ'")
        }
    }

    suspend fun catalog(): CardCatalog? {
        val u = user() ?: return null
        val j = BrainApi.call(u, "GET", "/randomcard/catalog").json ?: return null
        fun list(k: String) = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map {
            CardItem(it.optString("id"), it.optString("name"), it.optInt("price"), colors(it.optJSONArray("colors")), strs(it.optJSONArray("glyphs"))) } } ?: emptyList()
        return CardCatalog(j.optBoolean("enabled", true), j.optInt("videoPrice"), j.optInt("audioPrice"), list("frames"), list("nameFrames"), list("effects"), j.optBoolean("vip"), j.optInt("diamonds"))
    }
    data class Mine(val card: RandomCard?, val videoRef: String, val audioRef: String)
    suspend fun mine(): Mine {
        val u = user() ?: return Mine(null, "", "")
        val j = BrainApi.call(u, "GET", "/randomcard/me").json
        return Mine(parseCard(j?.optJSONObject("card")), j?.optString("videoRef").orEmpty(), j?.optString("audioRef").orEmpty())
    }

    data class QuoteItem(val name: String, val price: Int)
    data class Quote(val items: List<QuoteItem>, val total: Int, val error: String)
    suspend fun quote(draft: JSONObject): Quote? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "POST", "/randomcard/quote", draft)
        val j = r.json ?: return null
        if (r.code != 200) return Quote(emptyList(), 0, j.optString("error").ifBlank { "คำนวณราคาไม่สำเร็จ (${r.code})" })
        val a = j.optJSONArray("items")
        return Quote(if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it) }.map { QuoteItem(it.optString("name"), it.optInt("price")) }, j.optInt("total"), j.optString("error"))
    }
    /** Returns null on success, else the message to show. */
    suspend fun save(draft: JSONObject): Pair<String?, RandomCard?> {
        val u = user() ?: return "กรุณาเข้าสู่ระบบก่อน" to null
        val r = BrainApi.call(u, "POST", "/randomcard/save", draft)
        if (r.code == 200) return null to parseCard(r.json?.optJSONObject("card"))
        return (r.json?.optString("error")?.ifBlank { null } ?: "บันทึกไม่สำเร็จ (${r.code})") to null
    }

    private suspend fun putStorage(path: String, put: (com.google.firebase.storage.StorageReference) -> com.google.firebase.storage.UploadTask): Boolean =
        suspendCancellableCoroutine { c -> put(FirebaseStorage.getInstance().reference.child(path)).addOnCompleteListener { c.resume(it.isSuccessful) } }
    /** Picture for the card (compressed) → Storage posts/{uid}/… ; returns the ref or null. */
    suspend fun uploadImage(ctx: Context, uri: Uri, tag: String): String? {
        val u = user() ?: return null
        val bytes = com.example.social.FeedRepo.compress(ctx, uri) ?: return null
        val path = "posts/${u.uid}/rc_${tag}_${System.currentTimeMillis()}.jpg"
        return if (putStorage(path) { it.putBytes(bytes, StorageMetadata.Builder().setContentType("image/jpeg").build()) }) path else null
    }
    suspend fun uploadVideo(ctx: Context, uri: Uri): String? {
        val u = user() ?: return null
        val mime = ctx.contentResolver.getType(uri)?.takeIf { it.startsWith("video/") } ?: "video/mp4"
        val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "mp4"
        val path = "posts/${u.uid}/rc_video_${System.currentTimeMillis()}.$ext"
        return if (putStorage(path) { it.putFile(uri, StorageMetadata.Builder().setContentType(mime).build()) }) path else null
    }
    suspend fun uploadAudio(bytes: ByteArray): String? {
        val u = user() ?: return null
        val r = BrainApi.callRaw(u, "PUT", "/randomcard/audio", bytes, "audio/mp4")
        return if (r.code == 200) r.json?.optString("ref")?.ifBlank { null } else null
    }
}
