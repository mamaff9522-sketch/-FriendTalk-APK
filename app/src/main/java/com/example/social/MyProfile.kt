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
    /** Extra note for the last successful save (e.g. some fields wait for the server update). */
    var lastNote: String? = null
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
        lastNote = null
        val r = BrainApi.call(u, "PUT", "/me/profile", JSONObject().put("displayName", displayName.trim()).put("bio", bio.trim()).put("gender", gender)
            .put("province", province.trim()).put("occupation", occupation.trim()).put("lookingFor", JSONArray(lookingFor)))
        if (r.code == 200) return null
        if (r.code == 404) {
            // Server not updated yet (no /me/profile): save what the existing database rules already allow, straight to users/{uid}.
            val ok = SocialRepo.saveProfile(displayName, bio, -1, gender, province, me?.interests ?: emptyList())
            if (!ok) return "บันทึกไม่สำเร็จ (ยืนยันอีเมลแล้วหรือยัง?)"
            if (occupation.isNotBlank() || lookingFor.isNotEmpty()) lastNote = "บันทึกแล้ว · อาชีพและสิ่งที่มองหาจะบันทึกได้หลังอัปเดตเซิร์ฟเวอร์"
            return null
        }
        return r.json?.optString("error")?.ifBlank { null } ?: "บันทึกไม่สำเร็จ (${r.code})"
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
        if (r.code == 200) return null
        if (r.code == 404) {
            // Server not updated yet (no /me/photo).
            if (kind != "avatar") { runCatching { storage.reference.child(path).delete() }; return "รูปปกจะเปลี่ยนได้หลังอัปเดตเซิร์ฟเวอร์ (ตอนนี้เปลี่ยนได้เฉพาะรูปโปรไฟล์)" }
            // The existing database rules already allow users/{uid}/avatar (an https link), so save the uploaded picture's link directly.
            val url = suspendCancellableCoroutine<String?> { c ->
                storage.reference.child(path).downloadUrl.addOnCompleteListener { if (c.isActive) c.resume(if (it.isSuccessful) it.result?.toString() else null) }
            }
            if (url == null || url.length > 500) return "บันทึกรูปไม่สำเร็จ"
            val ok = suspendCancellableCoroutine<Boolean> { c ->
                FirebaseDatabase.getInstance(SocialRepo.DB_URL).getReference("users/${u.uid}/avatar").setValue(url).addOnCompleteListener { if (c.isActive) c.resume(it.isSuccessful) }
            }
            return if (ok) null else "บันทึกรูปไม่สำเร็จ (ยืนยันอีเมลแล้วหรือยัง?)"
        }
        return r.json?.optString("error")?.ifBlank { null } ?: "บันทึกรูปไม่สำเร็จ (${r.code})"
    }
}
