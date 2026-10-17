package com.example.push

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.mutableStateMapOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * While the app is open: watches my chats (chats/{id} lastAt/lastSenderUid), plays a message sound + short buzz when a new
 * message arrives from someone else (not for the chat that is open on screen), and keeps an unread count per chat
 * (messages from the other member newer than my read receipt) for the chat list badges and the bottom-bar total.
 */
object MessageWatcher {
    /** chatId -> unread messages from the other member. */
    val unread = mutableStateMapOf<String, Int>()
    @Volatile var foreground = false
    private const val DB_URL = "https://friendtalk-4e623-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val db get() = FirebaseDatabase.getInstance(DB_URL)
    private var uid: String? = null
    private var listRef: DatabaseReference? = null
    private var listL: ValueEventListener? = null
    private val chatLs = HashMap<String, Pair<DatabaseReference, ValueEventListener>>()
    private val lastSeenAt = HashMap<String, Long>()
    private var lastSound = 0L
    private var appCtx: Context? = null

    fun start(ctx: Context) {
        appCtx = ctx.applicationContext
        val me = FirebaseAuth.getInstance().currentUser?.uid
        if (me == uid) return
        stop()
        uid = me ?: return
        val ref = db.getReference("userChats/$me")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val ids = s.children.mapNotNull { it.key }.toSet()
                (chatLs.keys - ids).forEach { id -> chatLs.remove(id)?.let { (r, x) -> r.removeEventListener(x) }; unread.remove(id) }
                (ids - chatLs.keys).forEach { id -> watchChat(me, id) }
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(l); listRef = ref; listL = l
    }

    fun stop() {
        listL?.let { listRef?.removeEventListener(it) }; listRef = null; listL = null
        chatLs.values.forEach { (r, x) -> r.removeEventListener(x) }; chatLs.clear()
        lastSeenAt.clear(); unread.clear(); uid = null
    }

    private fun watchChat(me: String, id: String) {
        val ref = db.getReference("chats/$id")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val lastAt = (s.child("lastAt").value as? Number)?.toLong() ?: 0L
                val sender = s.child("lastSenderUid").getValue(String::class.java)
                val prev = lastSeenAt[id]
                lastSeenAt[id] = lastAt
                if (prev != null && lastAt > prev && sender != null && sender != me) {
                    val open = com.example.social.SocialRepo.openChat.value == id
                    if (!open) ping()
                }
                recount(me, id)
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(l); chatLs[id] = ref to l
    }

    /** Unread = messages from the other member after my read receipt (max 99). */
    fun recount(me: String, id: String) {
        db.getReference("readReceipts/$id/$me").get().addOnCompleteListener { rt ->
            val read = (rt.result?.value as? Number)?.toLong() ?: 0L
            // last 99 by push key (chronological, no extra index needed), then filter by time
            db.getReference("messages/$id").limitToLast(99).get().addOnCompleteListener { mt ->
                val n = mt.result?.children?.count { m ->
                    val at = (m.child("at").value as? Number)?.toLong() ?: 0L
                    val s = m.child("senderUid").getValue(String::class.java)
                    at > read && s != null && s != me
                } ?: 0
                if (n > 0) unread[id] = n else unread.remove(id)
                com.example.ui.feed.HomeRepo.unreadChats.value = unread.size
            }
        }
    }

    /** Called when I open/read a chat. */
    fun markRead(id: String) { unread.remove(id); com.example.ui.feed.HomeRepo.unreadChats.value = unread.size }

    private fun ping() {
        val ctx = appCtx ?: return
        val now = System.currentTimeMillis()
        if (now - lastSound < 1500) return // a burst of messages → one sound
        lastSound = now
        if (!ctx.getSharedPreferences("friendtalk", 0).getBoolean("msgSound", true)) return
        runCatching {
            val r = RingtoneManager.getRingtone(ctx, PushRepo.messageSound(ctx)) // FriendTalk 'Soft Pop
            if (Build.VERSION.SDK_INT >= 28) r.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT).build()
            r.play()
        }
        runCatching {
            @Suppress("DEPRECATION") val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= 26) v?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v?.vibrate(60)
        }
    }
}
