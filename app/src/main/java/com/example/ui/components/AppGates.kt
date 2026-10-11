package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.network.BrainApi
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class ComingSoonCfg(val enabled: Boolean, val text: String, val sticker: String, val imageUrl: String)
data class LockCfg(val locked: Boolean, val message: String, val imageUrl: String)

/** Live app gates from RTDB appConfig: whole-app lock + per-target 'coming soon' placeholders. */
object AppGates {
    var isSuper by mutableStateOf(false)
    var isStaff by mutableStateOf(false)
    var comingSoon by mutableStateOf<Map<String, ComingSoonCfg>>(emptyMap())
    var lock by mutableStateOf(LockCfg(false, "", ""))
    private var started = false
    fun start() {
        if (started || FirebaseAuth.getInstance().currentUser == null) return; started = true
        val db = FirebaseDatabase.getInstance("https://friendtalk-4e623-default-rtdb.asia-southeast1.firebasedatabase.app")
        db.getReference("appConfig/comingSoon").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                comingSoon = s.children.associate { c -> (c.key ?: "") to ComingSoonCfg(c.child("enabled").getValue(Boolean::class.java) != false,
                    c.child("text").getValue(String::class.java) ?: tr("เร็ว ๆ นี้ รอติดตาม"), c.child("sticker").getValue(String::class.java) ?: "", c.child("imageUrl").getValue(String::class.java) ?: "") }
            }
            override fun onCancelled(e: DatabaseError) {}
        })
        db.getReference("appConfig/lock").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                lock = LockCfg(s.child("locked").getValue(Boolean::class.java) == true, s.child("message").getValue(String::class.java) ?: "", s.child("imageUrl").getValue(String::class.java) ?: "")
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }
}

val COMING_SOON_TARGETS = linkedMapOf(
    "tab_home" to "แท็บ หน้าแรก", "tab_discover" to "แท็บ หาเพื่อน", "tab_live" to "แท็บ ไลฟ์สด", "tab_movie" to "แท็บ วิดีโอ", "tab_chat" to "แท็บ แชท", "tab_ads" to "แท็บ โฆษณา",
    "home_live" to "หน้าแรก: การ์ด LIVE ตอนนี้", "home_nearby" to "หน้าแรก: คนใกล้คุณ/เรื่องราว",
    "tile_random" to "ไทล์ สุ่มหาเพื่อน", "tile_shake" to "ไทล์ เขย่าหาเพื่อน", "tile_radar" to "ไทล์ เรดาร์", "tile_swipe" to "ไทล์ ปัดหาเพื่อน",
    "tile_similar" to "ไทล์ คนที่ชอบคล้ายกัน", "tile_requests" to "ไทล์ คำขอเป็นเพื่อน", "tile_companion" to "ไทล์ เพื่อนคุย")

/** If a placeholder is enabled for [target], everyone except Super Admin sees only the overlay (clicks blocked). Super Admin taps -> edit or enter. */
@Composable
fun ComingSoonGate(target: String, modifier: Modifier = Modifier, compact: Boolean = false, content: @Composable () -> Unit) {
    val cfg = AppGates.comingSoon[target]?.takeIf { it.enabled }
    var entered by remember(target) { mutableStateOf(false) }
    var editor by remember { mutableStateOf(false) }
    var choose by remember { mutableStateOf(false) }
    if (cfg == null || (AppGates.isSuper && entered)) {
        Box(modifier) {
            content()
            if (cfg != null) Surface(shape = RoundedCornerShape(8.dp), color = Amber500.copy(alpha = 0.9f), modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clickable { entered = false }) {
                Text("Super Admin bypass · ซ่อน", color = Color.Black, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    } else {
        Box(modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (AppGates.isSuper) choose = true }) {
            Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(Purple600.copy(alpha = 0.55f), Pink600.copy(alpha = 0.45f), Slate950))))
            Column(Modifier.align(Alignment.Center).padding(if (compact) 6.dp else 24.dp).then(if (compact) Modifier else Modifier.fillMaxWidth().defaultMinSize(minHeight = 260.dp)),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (cfg.imageUrl.isNotBlank()) AsyncImage(cfg.imageUrl, null, contentScale = ContentScale.Fit, modifier = Modifier.size(if (compact) 56.dp else 180.dp).clip(RoundedCornerShape(16.dp)))
                if (cfg.sticker.isNotBlank()) Text(cfg.sticker, fontSize = if (compact) 28.sp else 64.sp)
                if (cfg.imageUrl.isBlank() && cfg.sticker.isBlank()) Text("⏳", fontSize = if (compact) 28.sp else 56.sp)
                Text(cfg.text, color = PureWhite, fontWeight = FontWeight.Bold, fontSize = if (compact) 11.sp else 18.sp, textAlign = TextAlign.Center, maxLines = if (compact) 2 else 4)
            }
        }
    }
    if (choose) AlertDialog(onDismissRequest = { choose = false }, title = { Text("Placeholder: ${COMING_SOON_TARGETS[target] ?: target}") },
        text = { Text(tr("คุณเป็น Super Admin — ผู้ใช้และแอดมินคนอื่นเห็นเฉพาะหน้านี้")) },
        confirmButton = { TextButton(onClick = { choose = false; editor = true }) { Text(tr("แก้ไข placeholder")) } },
        dismissButton = { TextButton(onClick = { choose = false; entered = true }) { Text(tr("เข้าไปดูส่วนนี้")) } })
    if (editor) ComingSoonEditor(target) { editor = false }
}

@Composable
fun ComingSoonEditor(target: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cur = AppGates.comingSoon[target]
    var enabled by remember { mutableStateOf(cur?.enabled ?: true) }
    var text by remember { mutableStateOf(cur?.text ?: tr("เร็ว ๆ นี้ รอติดตาม")) }
    var sticker by remember { mutableStateOf(cur?.sticker ?: "") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    suspend fun put(body: JSONObject): Boolean {
        val u = FirebaseAuth.getInstance().currentUser ?: return false
        val r = BrainApi.call(u, "PUT", "/superadmin/coming-soon/$target", body)
        msg = if (r.code == 200) tr("บันทึกแล้ว (ผู้ใช้เห็นทันที)") else r.json?.optString("error") ?: "ไม่สำเร็จ (${r.code})"; return r.code == 200
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val u = FirebaseAuth.getInstance().currentUser
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.let { bm ->
                    val sc = minOf(1f, 800f / maxOf(bm.width, bm.height)); val s = Bitmap.createScaledBitmap(bm, (bm.width * sc).toInt().coerceAtLeast(1), (bm.height * sc).toInt().coerceAtLeast(1), true)
                    ByteArrayOutputStream().also { o -> @Suppress("DEPRECATION") s.compress(Bitmap.CompressFormat.WEBP, 85, o) }.toByteArray()
                }
            }
            if (u != null && bytes != null) {
                if (cur == null) put(JSONObject().put("enabled", enabled).put("text", text).put("sticker", sticker))
                val r = BrainApi.callRaw(u, "PUT", "/superadmin/coming-soon/$target/image", bytes, "image/webp")
                msg = if (r.code == 200) tr("อัปโหลดรูปแล้ว") else r.json?.optString("error") ?: "อัปโหลดไม่สำเร็จ (${r.code})"
            } else msg = tr("อ่านรูปไม่ได้")
            busy = false
        }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(COMING_SOON_TARGETS[target] ?: target) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("เปิดใช้ placeholder"), modifier = Modifier.weight(1f)); Switch(checked = enabled, onCheckedChange = { enabled = it }) }
            OutlinedTextField(value = text, onValueChange = { text = it.take(120) }, label = { Text(tr("ข้อความ")) })
            OutlinedTextField(value = sticker, onValueChange = { sticker = it.take(16) }, label = { Text(tr("สติกเกอร์ (อีโมจิ)")) }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("🚧", "⏳", "🎉", "🔒", "✨", "🛠️").forEach { e -> Text(e, fontSize = 24.sp, modifier = Modifier.clickable { sticker = e }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled = !busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (cur?.imageUrl.isNullOrBlank()) tr("อัปโหลดรูป") else tr("เปลี่ยนรูป")) }
                if (!cur?.imageUrl.isNullOrBlank()) OutlinedButton(onClick = { scope.launch { put(JSONObject().put("enabled", enabled).put("text", text).put("sticker", sticker).put("removeImage", true)) } }) { Text(tr("ลบรูป")) }
            }
            if (busy) LinearProgressIndicator()
            if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp)
            if (cur != null) TextButton(onClick = { scope.launch {
                val u = FirebaseAuth.getInstance().currentUser ?: return@launch
                if (BrainApi.call(u, "DELETE", "/superadmin/coming-soon/$target").code == 200) onClose() else msg = "ลบไม่สำเร็จ"
            } }) { Text(tr("ลบ placeholder นี้"), color = Rose500) }
        }
    }, confirmButton = { TextButton(onClick = { scope.launch { if (put(JSONObject().put("enabled", enabled).put("text", text).put("sticker", sticker))) onClose() } }) { Text(tr("บันทึก")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("ปิด")) } })
}

@Composable
fun AppLockScreen(appName: String) {
    val l = AppGates.lock
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Purple600.copy(alpha = 0.5f), Slate950, Slate950))), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Pink500, Purple600))), contentAlignment = Alignment.Center) { Text("FT", color = PureWhite, fontWeight = FontWeight.Black, fontSize = 32.sp) }
            Text(appName.ifBlank { "FriendTalk" }, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Text("🔒", fontSize = 56.sp)
            if (l.imageUrl.isNotBlank()) AsyncImage(l.imageUrl, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(16.dp)))
            Surface(shape = RoundedCornerShape(16.dp), color = Slate900, border = BorderStroke(1.dp, Pink500.copy(alpha = 0.5f))) {
                Text(l.message.ifBlank { tr("แอปปิดปรับปรุงชั่วคราว ขออภัยในความไม่สะดวก") }, color = White, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(18.dp))
            }
        }
    }
}
