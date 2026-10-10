package com.example.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.network.BrainApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

data class FeedPost(
    val id: String, val authorId: String, val authorName: String, val authorAvatar: String,
    val text: String, val imageUrls: List<String>, val createdAt: Long,
    val likeCount: Int, val commentCount: Int, val likedByMe: Boolean,
    val hashtags: List<String> = emptyList(), val placeLabel: String = "", val visibility: String = "public"
)
data class FeedComment(val id: String, val authorId: String, val authorName: String, val authorAvatar: String,
                       val text: String, val parentId: String?, val createdAt: Long)

/** Real feed via friendtalk-brain (/feed, /posts). */
object FeedRepo {
    private val storage by lazy { FirebaseStorage.getInstance("gs://friendtalk-4e623.firebasestorage.app") }
    private fun user() = FirebaseAuth.getInstance().currentUser

    private fun parsePost(o: JSONObject): FeedPost {
        val imgs = o.optJSONArray("images") ?: JSONArray()
        return FeedPost(o.optString("id"), o.optString("authorId"), o.optString("authorName"), o.optString("authorAvatar"),
            o.optString("text"), (0 until imgs.length()).mapNotNull { imgs.optJSONObject(it)?.optString("url") },
            o.optLong("createdAt"), o.optInt("likeCount"), o.optInt("commentCount"), o.optBoolean("likedByMe"),
            o.optJSONArray("hashtags")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
            o.optJSONObject("place")?.optString("label").orEmpty(), o.optString("visibility", "public"))
    }

    suspend fun feed(cursor: String?, tag: String? = null, author: String? = null): Pair<List<FeedPost>, String?>? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "GET", "/feed?limit=20" + (cursor?.let { "&cursor=$it" } ?: "") + (tag?.let { "&tag=" + android.net.Uri.encode(it) } ?: "") + (author?.let { "&author=$it" } ?: ""))
        val j = r.json ?: return null
        if (r.code != 200) return null
        val arr = j.optJSONArray("posts") ?: JSONArray()
        return (0 until arr.length()).map { parsePost(arr.getJSONObject(it)) } to j.optString("nextCursor").takeIf { !j.isNull("nextCursor") && it.isNotBlank() }
    }

    /** Downscale to max 1600px JPEG ~85% before upload. */
    private suspend fun compress(ctx: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1; while (maxOf(o.outWidth, o.outHeight) / sample > 3200) sample *= 2
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@runCatching null
            val scale = minOf(1f, 1600f / maxOf(bmp.width, bmp.height))
            val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        }.getOrNull()
    }

    suspend fun createPost(ctx: Context, text: String, images: List<Uri>, place: String = "", lat: Double? = null, lng: Double? = null, visibility: String = "public"): Result<FeedPost> {
        val u = user() ?: return Result.failure(Exception("กรุณาเข้าสู่ระบบ"))
        val refs = JSONArray()
        for ((i, uri) in images.take(4).withIndex()) {
            val bytes = compress(ctx, uri) ?: return Result.failure(Exception("อ่านรูปไม่ได้"))
            if (bytes.size > 10 * 1024 * 1024) return Result.failure(Exception("รูปใหญ่เกิน 10MB"))
            val path = "posts/${u.uid}/${System.currentTimeMillis()}_$i.jpg"
            val ok = suspendCancellableCoroutine { c ->
                storage.reference.child(path).putBytes(bytes, StorageMetadata.Builder().setContentType("image/jpeg").build())
                    .addOnCompleteListener { c.resume(it.isSuccessful) }
            }
            if (!ok) return Result.failure(Exception("อัปโหลดรูปไม่สำเร็จ (ยืนยันอีเมลแล้วหรือยัง?)"))
            refs.put(path)
        }
        val body = JSONObject().put("text", text.trim()).put("imageRefs", refs).put("visibility", visibility)
        if (place.isNotBlank()) { body.put("place", place.trim().take(80)); if (lat != null && lng != null) body.put("lat", lat).put("lng", lng) }
        val r = BrainApi.call(u, "POST", "/posts", body)
        return if (r.code == 200 && r.json != null) Result.success(parsePost(r.json))
        else Result.failure(Exception(r.json?.optString("error")?.ifBlank { null } ?: "โพสต์ไม่สำเร็จ (${r.code})"))
    }

    suspend fun setVisibility(id: String, v: String): Boolean = user()?.let { BrainApi.call(it, "PATCH", "/posts/$id", JSONObject().put("visibility", v)).code == 200 } ?: false

    suspend fun deletePost(id: String): Boolean = user()?.let { BrainApi.call(it, "DELETE", "/posts/$id").code == 200 } ?: false

    /** Returns new likeCount or null on failure. */
    suspend fun setLike(id: String, on: Boolean): Int? {
        val u = user() ?: return null
        val r = BrainApi.call(u, if (on) "POST" else "DELETE", "/posts/$id/like")
        return if (r.code == 200) r.json?.optInt("likeCount") else null
    }

    suspend fun comments(postId: String): List<FeedComment>? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "GET", "/posts/$postId/comments?limit=100")
        val arr = r.json?.optJSONArray("comments") ?: return if (r.code == 200) emptyList() else null
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            FeedComment(it.optString("id"), it.optString("authorId"), it.optString("authorName"), it.optString("authorAvatar"),
                it.optString("text"), if (it.isNull("parentId")) null else it.optString("parentId").ifBlank { null }, it.optLong("createdAt"))
        }
    }

    /** Returns new commentCount or null. */
    suspend fun addComment(postId: String, text: String, parentId: String?): Int? {
        val u = user() ?: return null
        val b = JSONObject().put("text", text.trim()); if (parentId != null) b.put("parentId", parentId)
        val r = BrainApi.call(u, "POST", "/posts/$postId/comments", b)
        return if (r.code == 200) r.json?.optInt("commentCount") else null
    }

    suspend fun deleteComment(postId: String, cid: String): Int? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "DELETE", "/posts/$postId/comments/$cid")
        return if (r.code == 200) r.json?.optInt("commentCount") else null
    }
}
