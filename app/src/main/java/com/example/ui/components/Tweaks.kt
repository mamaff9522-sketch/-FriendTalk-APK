package com.example.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Admin 'ปรับละเอียด': small layout numbers, switches and texts the admin changes live from the admin console
 * (appConfig/tweaks via PUT /admin/tweaks) — no new APK, no Play review. Every value has a built-in default, so the app
 * looks the same until the admin changes something; out-of-range values are clamped to the def's min / max.
 */
object Tweaks {
    /** One adjustable value. type: "num" (with min / max), "bool" or "text". */
    data class Def(val key: String, val group: String, val label: String, val type: String, val def: Any, val min: Float = 0f, val max: Float = 1000f, val unit: String = "")

    val DEFS: List<Def> = listOf(
        // ห้องแชท
        Def("chat.inputLift", "💬 ห้องแชท", "ยกช่องพิมพ์ขึ้นเพิ่มตอนคีย์บอร์ดเปิด", "num", 44f, 0f, 160f, "dp"),
        Def("chat.textSize", "💬 ห้องแชท", "ขนาดตัวหนังสือในข้อความแชท", "num", 15f, 11f, 24f, "sp"),
        Def("chat.giftVideo", "💬 ห้องแชท", "เล่นวิดีโอเอฟเฟกต์ของขวัญในแชท", "bool", true),
        Def("chat.sound", "💬 ห้องแชท", "เสียงแจ้งเตือนเมื่อมีข้อความเข้า (ตอนเปิดแอป)", "bool", true),
        Def("chat.vibrate", "💬 ห้องแชท", "สั่นเมื่อมีข้อความเข้า (ตอนเปิดแอป)", "bool", true),
        // หาเพื่อน
        Def("discover.onlineColumns", "👥 หาเพื่อน", "คนออนไลน์: จำนวนการ์ดต่อแถว", "num", 3f, 1f, 5f, "ใบ"),
        Def("discover.onlineCardHeight", "👥 หาเพื่อน", "คนออนไลน์: ความสูงการ์ด (% ของความกว้าง)", "num", 133f, 70f, 200f, "%"),
        Def("discover.capsuleColumns", "👥 หาเพื่อน", "ปุ่มฟังก์ชัน: จำนวนต่อแถว", "num", 3f, 2f, 4f, "ปุ่ม"),
        Def("discover.capsuleHeight", "👥 หาเพื่อน", "ปุ่มฟังก์ชัน: ความสูง", "num", 38f, 28f, 72f, "dp"),
        Def("discover.refreshSec", "👥 หาเพื่อน", "โหลดรายชื่อคนออนไลน์ใหม่ทุก", "num", 60f, 15f, 600f, "วินาที"),
        // สุ่มหาเพื่อน
        Def("random.cardWidth", "🎲 สุ่มหาเพื่อน", "ความกว้างการ์ด (% ของจอ)", "num", 92f, 60f, 100f, "%"),
        Def("random.diceSize", "🎲 สุ่มหาเพื่อน", "ขนาดลูกเต๋า", "num", 132f, 80f, 200f, "dp"),
        Def("random.rollMs", "🎲 สุ่มหาเพื่อน", "เวลาลูกเต๋าหมุนก่อนขึ้นผล", "num", 1200f, 300f, 4000f, "มิลลิวินาที"),
        // ฟีด
        Def("feed.columns", "📰 ฟีดหน้าแรก", "จำนวนโพสต์ต่อแถว", "num", 2f, 1f, 3f, "โพสต์"),
        Def("feed.mediaHeight", "📰 ฟีดหน้าแรก", "ความสูงรูป/วิดีโอในโพสต์ (% ของความกว้าง)", "num", 125f, 60f, 200f, "%"),
        Def("feed.captionLines", "📰 ฟีดหน้าแรก", "จำนวนบรรทัดข้อความใต้รูป", "num", 2f, 1f, 6f, "บรรทัด"),
        // โทร
        Def("call.keepScreenOn", "📞 โทร & วิดีโอคอล", "หน้าจอไม่ดับระหว่างโทร", "bool", true),
        Def("call.pipWidth", "📞 โทร & วิดีโอคอล", "รูปเล็กของเราตอนวิดีโอคอล: กว้าง", "num", 110f, 70f, 200f, "dp"),
        Def("call.pipHeight", "📞 โทร & วิดีโอคอล", "รูปเล็กของเราตอนวิดีโอคอล: สูง", "num", 160f, 90f, 300f, "dp"),
        Def("call.ringTimeoutSec", "📞 โทร & วิดีโอคอล", "โทรออกไม่มีคนรับ วางสายเองหลัง", "num", 45f, 15f, 120f, "วินาที"),
        Def("call.threeJunksUrl", "📞 โทร & วิดีโอคอล", "ที่อยู่เซิร์ฟเวอร์ Three Junks", "text", "https://threejunk.duckdns.org"),
        Def("call.threeJunksProject", "📞 โทร & วิดีโอคอล", "รหัสโปรเจกต์ใน Three Junks", "text", "friendtalk"),
        // ของขวัญ
        Def("gift.videoMaxSec", "🎁 ของขวัญ", "วิดีโอเอฟเฟกต์เล่นนานสุด", "num", 15f, 3f, 60f, "วินาที"),
        Def("gift.videoDim", "🎁 ของขวัญ", "ทำพื้นหลังมืดตอนเล่นวิดีโอเอฟเฟกต์ (แชท)", "bool", true),
    )
    private val byKey = DEFS.associateBy { it.key }

    var values by mutableStateOf<Map<String, Any>>(emptyMap())
        private set
    private var started = false
    fun start() {
        if (started) return; started = true
        runCatching {
            FirebaseDatabase.getInstance("https://friendtalk-4e623-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("appConfig/tweaks")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) { values = s.children.mapNotNull { c -> c.key?.let { k -> c.value?.let { k.replace('~', '.') to it } } }.toMap() }
                    override fun onCancelled(e: DatabaseError) { started = false }
                })
        }.onFailure { started = false }
    }
    fun num(key: String): Float {
        val d = byKey[key]; val dv = (d?.def as? Number)?.toFloat() ?: 0f
        val v = (values[key] as? Number)?.toFloat() ?: return dv
        return if (d == null) v else v.coerceIn(d.min, d.max)
    }
    fun int(key: String): Int = Math.round(num(key))
    fun on(key: String): Boolean = values[key] as? Boolean ?: (byKey[key]?.def as? Boolean ?: true)
    fun text(key: String): String = (values[key] as? String)?.takeIf { it.isNotBlank() } ?: (byKey[key]?.def as? String).orEmpty()
}
