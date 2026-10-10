package com.example.social

import android.net.Uri
import com.example.network.BrainApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

data class RealProfile(
    val uid: String, val displayName: String, val username: String = "", val avatar: String = "",
    val bio: String = "", val age: Int? = null, val gender: String = "", val interests: List<String> = emptyList(),
    val lastActive: Long = 0,
    val similarity: Similarity? = null
)
data class ChatSummary(val chatId: String, val otherUid: String, val lastMessage: String, val lastAt: Long)
data class ChatMessage(val id: String, val senderUid: String, val text: String?, val imagePath: String?, val at: Long)

/** Real social data in Firebase Realtime Database + Storage (no mock data). */
object SocialRepo {
    const val DB_URL = "https://friendtalk-4e623-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val db by lazy { FirebaseDatabase.getInstance(DB_URL) }
    private val storage by lazy { FirebaseStorage.getInstance("gs://friendtalk-4e623.firebasestorage.app") }

    /** Chat to open full-screen (set from swipe match / random match / chat list). */
    val openChat = MutableStateFlow<String?>(null)

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitOk(): Boolean = suspendCancellableCoroutine { c ->
        addOnCompleteListener { c.resume(it.isSuccessful) }
    }
    private suspend fun get(path: String): DataSnapshot? = suspendCancellableCoroutine { c ->
        db.getReference(path).get().addOnCompleteListener { c.resume(if (it.isSuccessful) it.result else null) }
    }

    fun parseProfile(uid: String, s: DataSnapshot): RealProfile? {
        val name = s.child("displayName").getValue(String::class.java) ?: return null
        return RealProfile(
            uid = uid, displayName = name,
            username = s.child("username").getValue(String::class.java).orEmpty(),
            avatar = s.child("avatar").getValue(String::class.java).orEmpty(),
            bio = s.child("bio").getValue(String::class.java).orEmpty(),
            age = s.child("age").getValue(Long::class.java)?.toInt(),
            gender = s.child("gender").getValue(String::class.java).orEmpty(),
            interests = s.child("interests").children.mapNotNull { it.getValue(String::class.java) },
            lastActive = s.child("lastActive").getValue(Long::class.java) ?: 0
        )
    }

    /** On sign-in: create users/{uid} if missing, otherwise just bump lastActive. Errors are ignored (e.g. unverified email). */
    /** @return true if the profile was newly created (first sign-in). */
    suspend fun upsertOnSignIn(user: FirebaseUser): Boolean {
        val ref = db.getReference("users/${user.uid}")
        val snap = get("users/${user.uid}")
        val now = System.currentTimeMillis()
        if (snap != null && snap.exists() && snap.hasChild("displayName")) {
            ref.child("lastActive").setValue(now).awaitOk()
            return false
        } else {
            val name = (user.displayName?.ifBlank { null } ?: user.email?.substringBefore("@") ?: "ผู้ใช้ FriendTalk").take(50)
            val photo = user.photoUrl?.toString()?.takeIf { it.startsWith("https://") && it.length <= 500 } ?: ""
            val uname = (user.email?.substringBefore("@") ?: "user_${user.uid.take(6)}").lowercase().replace(Regex("[^a-z0-9_]"), "_").take(30)
            return ref.setValue(mapOf("displayName" to name, "username" to uname, "avatar" to photo, "bio" to "", "createdAt" to now, "lastActive" to now)).awaitOk()
        }
    }

    suspend fun saveProfile(displayName: String, bio: String, age: Int, gender: String, province: String, interests: List<String>): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val m = mutableMapOf<String, Any>(
            "displayName" to displayName.trim().take(50).ifBlank { "ผู้ใช้ FriendTalk" },
            "bio" to bio.take(300), "gender" to gender, "province" to province.take(60),
            "interests" to interests.map { it.take(40) }.take(20),
            "lastActive" to System.currentTimeMillis()
        )
        if (age in 18..99) m["age"] = age
        return db.getReference("users/$uid").updateChildren(m).awaitOk()
    }

    suspend fun profile(uid: String): RealProfile? = get("users/$uid")?.let { parseProfile(uid, it) }

    /** Live list of my chats (userChats/{uid} -> chats/{id}). */
    fun chatList(myUid: String): Flow<List<ChatSummary>> = callbackFlow {
        val ref = db.getReference("userChats/$myUid")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val ids = s.children.mapNotNull { it.key }
                if (ids.isEmpty()) { trySend(emptyList()); return }
                val out = mutableListOf<ChatSummary>(); var left = ids.size
                ids.forEach { id ->
                    db.getReference("chatMembers/$id").get().addOnCompleteListener { mt ->
                        val other = mt.result?.children?.mapNotNull { it.key }?.firstOrNull { it != myUid }.orEmpty()
                        db.getReference("chats/$id").get().addOnCompleteListener { ct ->
                            val c = ct.result
                            out.add(ChatSummary(id, other, c?.child("lastMessage")?.getValue(String::class.java).orEmpty(),
                                c?.child("lastAt")?.getValue(Long::class.java) ?: 0))
                            left--; if (left == 0) trySend(out.sortedByDescending { it.lastAt })
                        }
                    }
                }
            }
            override fun onCancelled(e: DatabaseError) { trySend(emptyList()) }
        }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    /** Realtime message stream (last 200). */
    fun messages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val q = db.getReference("messages/$chatId").orderByKey().limitToLast(200)
        val list = linkedMapOf<String, ChatMessage>()
        val l = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, p: String?) {
                val id = s.key ?: return
                list[id] = ChatMessage(id, s.child("senderUid").getValue(String::class.java).orEmpty(),
                    s.child("text").getValue(String::class.java), s.child("imagePath").getValue(String::class.java),
                    s.child("at").getValue(Long::class.java) ?: 0)
                trySend(list.values.toList())
            }
            override fun onChildChanged(s: DataSnapshot, p: String?) {}
            override fun onChildRemoved(s: DataSnapshot) { s.key?.let { list.remove(it); trySend(list.values.toList()) } }
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            override fun onCancelled(e: DatabaseError) {}
        }
        q.addChildEventListener(l)
        awaitClose { q.removeEventListener(l) }
    }

    /** Other member's read time (read receipts). */
    fun otherReadAt(chatId: String, otherUid: String): Flow<Long> = callbackFlow {
        val ref = db.getReference("readReceipts/$chatId/$otherUid")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.getValue(Long::class.java) ?: 0) }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(l); awaitClose { ref.removeEventListener(l) }
    }

    fun markRead(chatId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.getReference("readReceipts/$chatId/$uid").setValue(System.currentTimeMillis())
    }

    suspend fun chatOther(chatId: String, myUid: String): String? =
        get("chatMembers/$chatId")?.children?.mapNotNull { it.key }?.firstOrNull { it != myUid }

    private suspend fun pushMessage(chatId: String, fields: Map<String, Any>, preview: String): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val now = System.currentTimeMillis()
        val key = db.getReference("messages/$chatId").push().key ?: return false
        // daily counter written in the same atomic update; RTDB rules check it against appConfig/limits (chatMessagesPerDay)
        val day = (now + 7 * 3600_000L) / 86_400_000L
        val cc = get("chatCount/$uid")
        val prevDay = cc?.child("day")?.getValue(Long::class.java); val prevN = cc?.child("n")?.getValue(Long::class.java) ?: 0L
        val n = if (prevDay == day) prevN + 1 else 1L
        val ok = db.reference.updateChildren(mapOf(
            "messages/$chatId/$key" to (fields + mapOf("senderUid" to uid, "at" to now)),
            "chatCount/$uid" to mapOf("day" to day, "n" to n, "last" to key))).awaitOk()
        if (ok) db.getReference("chats/$chatId").updateChildren(mapOf("lastMessage" to preview.take(200), "lastAt" to now, "lastSenderUid" to uid))
        return ok
    }

    suspend fun sendText(chatId: String, text: String) = pushMessage(chatId, mapOf("text" to text.trim().take(2000)), text.trim())

    suspend fun sendImage(chatId: String, uri: Uri, mime: String): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val path = "chatImages/$chatId/$uid/${System.currentTimeMillis()}.${if (mime.contains("png")) "png" else "jpg"}"
        val meta = StorageMetadata.Builder().setContentType(if (mime.startsWith("image/")) mime else "image/jpeg").build()
        val up = storage.reference.child(path).putFile(uri, meta).awaitOk()
        if (!up) return false
        return pushMessage(chatId, mapOf("imagePath" to path), "📷 รูปภาพ")
    }

    fun imageUrl(chatId: String, path: String) = "https://friendtalk-brain-123224091480.asia-southeast1.run.app/chats/$chatId/image?path=" + Uri.encode(path)

    data class ChatStatus(val canSend: Boolean, val reason: String, val closedByMe: Boolean, val closed: Boolean)
    suspend fun chatStatus(chatId: String): ChatStatus? {
        val u = FirebaseAuth.getInstance().currentUser ?: return null
        val j = BrainApi.call(u, "GET", "/chats/$chatId/status").json ?: return null
        val c = j.optJSONObject("closed")
        return ChatStatus(j.optBoolean("canSend", true), j.optString("reason"), c?.optBoolean("byMe") == true, c != null)
    }
    suspend fun setClosed(chatId: String, closed: Boolean): String? {
        val u = FirebaseAuth.getInstance().currentUser ?: return "ไม่ได้เข้าสู่ระบบ"
        val r = BrainApi.call(u, "POST", "/chats/$chatId/close", JSONObject().put("closed", closed))
        return if (r.code == 200) null else (r.json?.optString("error") ?: "ไม่สำเร็จ (${r.code})")
    }
    var lastOpenError = ""
    suspend fun openChatWith(otherUid: String): String? {
        val u = FirebaseAuth.getInstance().currentUser ?: return null
        val r = BrainApi.call(u, "POST", "/chats/open", JSONObject().put("otherUid", otherUid))
        lastOpenError = r.json?.optString("error").orEmpty()
        return if (r.code == 200) r.json?.optString("chatId") else null
    }

    suspend fun candidates(): List<RealProfile> {
        val u = FirebaseAuth.getInstance().currentUser ?: return emptyList()
        val r = BrainApi.call(u, "GET", "/swipe/candidates?limit=20")
        val arr = r.json?.optJSONArray("candidates") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ints = o.optJSONArray("interests")
            RealProfile(o.optString("uid"), o.optString("displayName"), o.optString("username"), o.optString("avatar"),
                o.optString("bio"), if (o.isNull("age")) null else o.optInt("age"), o.optString("gender"),
                if (ints == null) emptyList() else (0 until ints.length()).map { ints.optString(it) },
                similarity = InterestsRepo.parseSim(o.optJSONObject("similarity")))
        }
    }

    data class SwipeResult(val ok: Boolean, val matched: Boolean, val chatId: String?)
    suspend fun swipe(targetUid: String, action: String): SwipeResult {
        val u = FirebaseAuth.getInstance().currentUser ?: return SwipeResult(false, false, null)
        val r = BrainApi.call(u, "POST", "/swipe", JSONObject().put("targetUid", targetUid).put("action", action))
        val j = r.json
        return SwipeResult(r.code == 200, j?.optBoolean("matched") == true, j?.optString("chatId")?.ifBlank { null })
    }
}
