package com.example.social

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.network.BrainApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/** The signed-in user's real profile (RTDB users/{uid}), shown on 'ฉัน' and edited there. Saves go through friendtalk-brain (validated + moderated). */
object MyProfile {
    data class Data(
        val displayName: String = "", val username: String = "", val avatar: String = "", val cover: String = "", val bio: String = "",
        val gender: String = "", val province: String = "", val occupation: String = "", val lookingFor: List<String> = emptyList(),
        val interests: List<String> = emptyList(), val age: Int? = null
    )
    /** key -> Thai label, same keys as the server's LOOKING_FOR list. */
    val LOOKING_FOR = linkedMapOf("friends" to "👋 หาเพื่อน", "chat" to "💬 หาคนคุย", "dating" to "💞 หาแฟน", "travel" to "✈️ เพื่อนเที่ยว",
        "gaming" to "🎮 เพื่อนเล่นเกม", "study" to "📚 เพื่อนติวหนังสือ", "work" to "💼 คอนเนกชันงาน")

    var me by mutableStateOf<Data?>(null)
        private set
    private var listening: String? = null
    private val storage by lazy { FirebaseStorage.getInstance("gs://friendtalk-4e623.firebasestorage.app") }

    fun start() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (listening == uid) return; listening = uid
        FirebaseDatabase.getInstance(SocialRepo.DB_URL).getReference("users/$uid").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                fun str(k: String) = s.child(k).getValue(String::class.java).orEmpty()
                me = Data(str("displayName"), str("username"), str("avatar"), str("cover"), str("bio"), str("gender"), str("province"), str("occupation"),
                    s.child("lookingFor").children.mapNotNull { it.getValue(String::class.java) }, s.child("interests").children.mapNotNull { it.getValue(String::class.java) },
                    s.child("age").getValue(Long::class.java)?.toInt())
            }
            override fun onCancelled(e: DatabaseError) { listening = null }
        })
    }

    /** null = saved; otherwise the error message to show. */
    suspend fun save(displayName: String, bio: String, gender: String, province: String, occupation: String, lookingFor: List<String>): String? {
        val u = FirebaseAuth.getInstance().currentUser ?: return "กรุณาเข้าสู่ระบบ"
        val r = BrainApi.call(u, "PUT", "/me/profile", JSONObject().put("displayName", displayName.trim()).put("bio", bio.trim()).put("gender", gender)
            .put("province", province.trim()).put("occupation", occupation.trim()).put("lookingFor", JSONArray(lookingFor)))
        return if (r.code == 200) null else r.json?.optString("error")?.ifBlank { null } ?: "บันทึกไม่สำเร็จ (${r.code}) — เซิร์ฟเวอร์อาจยังไม่อัปเดต"
    }

    /** Upload a new avatar / cover. null = done; otherwise the error message. */
    suspend fun setPhoto(ctx: Context, uri: Uri, kind: String): String? {
        val u = FirebaseAuth.getInstance().currentUser ?: return "กรุณาเข้าสู่ระบบ"
        val bytes = FeedRepo.compress(ctx, uri) ?: return "อ่านรูปไม่ได้"
        val path = "posts/${u.uid}/profile_${kind}_${System.currentTimeMillis()}.jpg"
        val ok = suspendCancellableCoroutine { c ->
            storage.reference.child(path).putBytes(bytes, StorageMetadata.Builder().setContentType("image/jpeg").build()).addOnCompleteListener { if (c.isActive) c.resume(it.isSuccessful) }
        }
        if (!ok) return "อัปโหลดรูปไม่สำเร็จ (ยืนยันอีเมลแล้วหรือยัง?)"
        val r = BrainApi.call(u, "PUT", "/me/photo", JSONObject().put("kind", kind).put("ref", path))
        return if (r.code == 200) null else r.json?.optString("error")?.ifBlank { null } ?: "บันทึกรูปไม่สำเร็จ (${r.code}) — เซิร์ฟเวอร์อาจยังไม่อัปเดต"
    }
}
