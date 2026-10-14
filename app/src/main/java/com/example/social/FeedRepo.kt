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
    val hashtags: List<String> = emptyList(), val placeLabel: String = "", val visibility: String = "public", val commentsOff: Boolean = false,
    val videoUrl: String = "", val videoPosterUrl: String = "",
    /** ~1 km point; only returned to the author, or to viewers when the author chose to share it. */
    val placeLat: Double? = null, val placeLng: Double? = null,
    val feeling: PostFeeling? = null
)
/** Feeling ("รู้สึก 😊 มีความสุข") or activity ("🎬 กำลังดูหนัง") attached to a post. */
data class PostFeeling(val kind: String, val emoji: String, val label: String) {
    fun display() = if (kind == "feeling") "รู้สึก $emoji $label" else "$emoji $label"
}

/** Place chosen in the composer. lat/lng are sent only when [shareCoords] is on (user consent). */
data class PostPlace(val label: String, val lat: Double? = null, val lng: Double? = null, val shareCoords: Boolean = false)
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
            o.optJSONObject("place")?.optString("label").orEmpty(), o.optString("visibility", "public"), o.optBoolean("commentsOff"),
            o.optJSONObject("video")?.optString("url").orEmpty(), o.optJSONObject("video")?.optString("posterUrl").orEmpty(),
            o.optJSONObject("place")?.let { pl -> if (pl.has("lat") && pl.has("lng")) pl.optDouble("lat") else null },
            o.optJSONObject("place")?.let { pl -> if (pl.has("lat") && pl.has("lng")) pl.optDouble("lng") else null },
            o.optJSONObject("feeling")?.let { f -> f.optString("label").takeIf { it.isNotBlank() }?.let { PostFeeling(f.optString("kind", "feeling"), f.optString("emoji"), it) } })
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
    internal suspend fun compress(ctx: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
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

    const val VIDEO_MAX_BYTES = 50L * 1024 * 1024
    const val VIDEO_MAX_MS = 3 * 60 * 1000L

    /** Size (bytes) and duration (ms) of a picked video, or null when it cannot be read. */
    suspend fun videoInfo(ctx: Context, uri: Uri): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        runCatching {
            val size = ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            val mmr = android.media.MediaMetadataRetriever()
            val dur = try { mmr.setDataSource(ctx, uri); mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } finally { runCatching { mmr.release() } }
            size to dur
        }.getOrNull()
    }

    /** First frame of a video as JPEG (cover image, also moderated by the server). */
    private suspend fun videoPoster(ctx: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val mmr = android.media.MediaMetadataRetriever()
            try {
                mmr.setDataSource(ctx, uri)
                val bmp = mmr.getFrameAtTime(0) ?: return@runCatching null
                val scale = minOf(1f, 1280f / maxOf(bmp.width, bmp.height))
                val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
                ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
            } finally { runCatching { mmr.release() } }
        }.getOrNull()
    }

    private suspend fun uploadBytes(path: String, bytes: ByteArray, mime: String): Boolean = suspendCancellableCoroutine { c ->
        storage.reference.child(path).putBytes(bytes, StorageMetadata.Builder().setContentType(mime).build()).addOnCompleteListener { c.resume(it.isSuccessful) }
    }

    /**
     * Composer post: up to 4 images and/or 1 video, optional place, visibility (public / friends / only_me).
     * Files go to Storage posts/{uid}/ (same as before); friendtalk-brain POST /posts saves the post and enforces visibility.
     */
    suspend fun createPostFull(ctx: Context, text: String, images: List<Uri>, video: Uri?, place: PostPlace?, visibility: String,
                               onProgress: (String) -> Unit = {}, feeling: PostFeeling? = null): Result<FeedPost> {
        val u = user() ?: return Result.failure(Exception("กรุณาเข้าสู่ระบบ"))
        val t0 = System.currentTimeMillis()
        val refs = JSONArray()
        val list = images.take(4)
        for ((i, uri) in list.withIndex()) {
            onProgress("กำลังอัปโหลดรูป ${i + 1}/${list.size}…")
            val bytes = compress(ctx, uri) ?: return Result.failure(Exception("อ่านรูปไม่ได้"))
            if (bytes.size > 10 * 1024 * 1024) return Result.failure(Exception("รูปใหญ่เกิน 10MB"))
            val path = "posts/${u.uid}/${t0}_$i.jpg"
            if (!uploadBytes(path, bytes, "image/jpeg")) return Result.failure(Exception("อัปโหลดรูปไม่สำเร็จ (ยืนยันอีเมลแล้วหรือยัง?)"))
            refs.put(path)
        }
        var videoRef: String? = null
        var posterRef: String? = null
        if (video != null) {
            val info = videoInfo(ctx, video) ?: return Result.failure(Exception("อ่านไฟล์วิดีโอไม่ได้"))
            if (info.first > VIDEO_MAX_BYTES) return Result.failure(Exception("วิดีโอใหญ่เกิน 50MB"))
            if (info.second > VIDEO_MAX_MS) return Result.failure(Exception("วิดีโอยาวเกิน 3 นาที"))
            val mime = ctx.contentResolver.getType(video)?.takeIf { it.startsWith("video/") } ?: "video/mp4"
            val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "mp4"
            videoPoster(ctx, video)?.let { cover ->
                val pp = "posts/${u.uid}/${t0}_v_cover.jpg"
                if (uploadBytes(pp, cover, "image/jpeg")) posterRef = pp
            }
            onProgress("กำลังอัปโหลดวิดีโอ… 0%")
            val vp = "posts/${u.uid}/${t0}_v.$ext"
            val ok = suspendCancellableCoroutine<Boolean> { c ->
                storage.reference.child(vp).putFile(video, StorageMetadata.Builder().setContentType(mime).build())
                    .addOnProgressListener { s -> if (s.totalByteCount > 0) onProgress("กำลังอัปโหลดวิดีโอ… ${(s.bytesTransferred * 100 / s.totalByteCount)}%") }
                    .addOnCompleteListener { c.resume(it.isSuccessful) }
            }
            if (!ok) return Result.failure(Exception("อัปโหลดวิดีโอไม่สำเร็จ (ยืนยันอีเมลแล้วหรือยัง? หรือเซิร์ฟเวอร์ยังไม่เปิดรับวิดีโอ)"))
            videoRef = vp
        }
        onProgress("กำลังเผยแพร่…")
        val body = JSONObject().put("text", text.trim()).put("imageRefs", refs).put("visibility", visibility)
        if (videoRef != null) { body.put("videoRef", videoRef); posterRef?.let { body.put("posterRef", it) } }
        feeling?.let { body.put("feeling", JSONObject().put("kind", it.kind).put("emoji", it.emoji).put("label", it.label)) }
        if (place != null && place.label.isNotBlank()) {
            body.put("place", place.label.trim().take(80))
            // Coordinates leave the phone only with the user's consent; the server rounds them to ~1 km.
            if (place.shareCoords && place.lat != null && place.lng != null) body.put("lat", place.lat).put("lng", place.lng).put("shareCoords", true)
        }
        val r = BrainApi.call(u, "POST", "/posts", body)
        if (r.code != 200 || r.json == null) return Result.failure(Exception(r.json?.optString("error")?.ifBlank { null } ?: "โพสต์ไม่สำเร็จ (${r.code})"))
        val post = parsePost(r.json)
        // An older server ignores videoRef: remove that post instead of silently publishing it without the video.
        if (videoRef != null && post.videoUrl.isBlank()) {
            BrainApi.call(u, "DELETE", "/posts/${post.id}")
            return Result.failure(Exception("เซิร์ฟเวอร์ยังไม่รองรับโพสต์วิดีโอ (ต้องอัปเดต friendtalk-brain)"))
        }
        return Result.success(post)
    }

    suspend fun setCommentsOff(id: String, off: Boolean): Boolean = user()?.let { BrainApi.call(it, "PATCH", "/posts/$id", JSONObject().put("commentsOff", off)).code == 200 } ?: false
    var lastCommentError = ""
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
        lastCommentError = r.json?.optString("error").orEmpty()
        return if (r.code == 200) r.json?.optInt("commentCount") else null
    }

    suspend fun deleteComment(postId: String, cid: String): Int? {
        val u = user() ?: return null
        val r = BrainApi.call(u, "DELETE", "/posts/$postId/comments/$cid")
        return if (r.code == 200) r.json?.optInt("commentCount") else null
    }
}
