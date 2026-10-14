package com.example.ui.feed

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.network.BrainApi
import com.example.social.SocialRepo
import com.example.ui.components.ft.Text
import com.example.ui.components.tr
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Launch showcase: AI sample images the admin uploads (appConfig/showcase) for 'ไลฟ์สดตอนนี้' and 'คนใกล้คุณ'.
 * Shown only while the real list is empty and always labelled "ตัวอย่าง AI", so they hide by themselves once real users appear.
 */
object Showcase {
    data class Item(val url: String, val name: String, val res: Int = 0) { val model: Any get() = if (res != 0) res else url }
    /** Built-in AI sample faces (res/drawable-nodpi/showcase_XX.webp) used until the admin uploads their own set. */
    private val BUILT_IN = listOf(
        com.example.R.drawable.showcase_01, com.example.R.drawable.showcase_02, com.example.R.drawable.showcase_03, com.example.R.drawable.showcase_04, com.example.R.drawable.showcase_05,
        com.example.R.drawable.showcase_06, com.example.R.drawable.showcase_07, com.example.R.drawable.showcase_08, com.example.R.drawable.showcase_09, com.example.R.drawable.showcase_10,
    ).map { Item("", "", it) }
    var enabled by mutableStateOf(true)
    var live by mutableStateOf(BUILT_IN.take(5))
    var nearby by mutableStateOf(BUILT_IN.drop(5))
    /** Admin's uploaded lists only (empty = using the built-in faces). */
    var adminLive by mutableStateOf<List<Item>>(emptyList())
    var adminNearby by mutableStateOf<List<Item>>(emptyList())
    private var loaded = false

    private fun items(s: com.google.firebase.database.DataSnapshot) =
        s.children.mapNotNull { c -> c.child("url").getValue(String::class.java)?.takeIf { it.isNotBlank() }?.let { Item(it, c.child("name").getValue(String::class.java).orEmpty()) } }

    suspend fun load(force: Boolean = false) {
        if (loaded && !force) return
        runCatching {
            val s = FirebaseDatabase.getInstance(SocialRepo.DB_URL).getReference("appConfig/showcase").get().await()
            enabled = s.child("enabled").getValue(Boolean::class.java) != false
            adminLive = items(s.child("live")); adminNearby = items(s.child("nearby"))
            live = adminLive.ifEmpty { BUILT_IN.take(5) }; nearby = adminNearby.ifEmpty { BUILT_IN.drop(5) }; loaded = true
        }
    }
}

@Composable
private fun AiBadge(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xB3000000)).padding(horizontal = 4.dp, vertical = 1.dp)) {
        Text(tr("ตัวอย่าง AI"), color = PureWhite, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Live-card row of sample images (same size as the real LiveCard). Returns false when there is nothing to show. */
@Composable
fun ShowcaseLiveRow(cardW: Dp, onClick: () -> Unit): Boolean {
    LaunchedEffect(Unit) { Showcase.load() }
    val l = Showcase.live
    if (!Showcase.enabled || l.isEmpty()) return false
    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(l, key = { i, it -> "sl$i" + it.url + it.res }) { _, it ->
            Box(Modifier.width(cardW).aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF3A2C72), RoundedCornerShape(12.dp)).background(Color(0xFF16123A)).clickable(onClick = onClick)) {
                AsyncImage(model = it.model, contentDescription = it.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                AiBadge(Modifier.align(Alignment.TopStart).padding(3.dp))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.4f).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000)))))
                if (it.name.isNotBlank()) Text(it.name, color = PureWhite, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp))
            }
        }
    }
    return true
}

/** Round-avatar row of sample images for 'คนใกล้คุณ'. Returns false when there is nothing to show. */
@Composable
fun ShowcaseNearbyRow(itemW: Dp, onClick: () -> Unit): Boolean {
    LaunchedEffect(Unit) { Showcase.load() }
    val l = Showcase.nearby
    if (!Showcase.enabled || l.isEmpty()) return false
    LazyRow(contentPadding = PaddingValues(horizontal = 6.dp)) {
        itemsIndexed(l, key = { i, it -> "sn$i" + it.url + it.res }) { _, it ->
            Column(Modifier.width(itemW).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(64.dp), contentAlignment = Alignment.TopCenter) {
                    AsyncImage(model = it.model, contentDescription = it.name, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(CircleShape).border(2.dp, Color(0xFFBE5AFF), CircleShape))
                    AiBadge(Modifier.align(Alignment.BottomCenter))
                }
                Spacer(Modifier.height(3.dp))
                Text(it.name.ifBlank { tr("ตัวอย่าง") }, color = AppThemeMode.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    return true
}

/** Admin: upload/remove the AI sample images and switch the showcase on/off. */
@Composable
fun AdminShowcasePane() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(true) }
    val live = remember { mutableStateListOf<Showcase.Item>() }
    val nearby = remember { mutableStateListOf<Showcase.Item>() }
    var target by remember { mutableStateOf("live") }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { Showcase.load(force = true); enabled = Showcase.enabled; live.clear(); live.addAll(Showcase.adminLive); nearby.clear(); nearby.addAll(Showcase.adminNearby) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val u = FirebaseAuth.getInstance().currentUser
        if (uri == null || u == null) return@rememberLauncherForActivityResult
        val list = if (target == "live") live else nearby
        if (list.size >= 12) { msg = "ใส่ได้สูงสุด 12 รูปต่อหมวด"; return@rememberLauncherForActivityResult }
        busy = true; msg = "กำลังอัปโหลด…"
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()?.let { b ->
                    val sc = minOf(1f, 720f / maxOf(b.width, b.height))
                    val r = Bitmap.createScaledBitmap(b, (b.width * sc).toInt().coerceAtLeast(1), (b.height * sc).toInt().coerceAtLeast(1), true)
                    ByteArrayOutputStream().also { r.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                }
            }
            if (bytes == null) { msg = "อ่านรูปไม่ได้"; busy = false; return@launch }
            val r = BrainApi.callRaw(u, "PUT", "/admin/ui/bg/sec_showcase_$target", bytes, "image/jpeg")
            val url = r.json?.optString("url").orEmpty()
            if (r.code == 200 && url.isNotBlank()) { list.add(Showcase.Item(url, name.trim())); name = ""; msg = "อัปโหลดแล้ว — กดบันทึกเพื่อใช้กับทุกคน" } else msg = "อัปโหลดไม่สำเร็จ (${r.code})"
            busy = false
        }
    }
    fun arr(l: List<Showcase.Item>) = JSONArray().also { a -> l.forEach { a.put(JSONObject().put("url", it.url).put("name", it.name)) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("รูปตัวอย่าง AI ช่วงเปิดระบบใหม่", color = PureWhite, fontWeight = FontWeight.Bold)
        Text("แสดงในช่อง 'ไลฟ์สดตอนนี้' และ 'คนใกล้คุณ' เฉพาะตอนที่ยังไม่มีผู้ใช้จริง และมีป้าย 'ตัวอย่าง AI' กำกับเสมอ — เมื่อมีคนไลฟ์จริงหรือมีคนใกล้จริง รูปตัวอย่างจะซ่อนเองอัตโนมัติ", color = Slate400, fontSize = 12.sp)
        Text("ถ้าหมวดไหนไม่มีรูปที่อัปโหลด แอปจะใช้รูปตัวอย่างที่ติดมากับแอป (10 รูป)", color = Slate400, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("เปิดใช้รูปตัวอย่าง", color = PureWhite, modifier = Modifier.weight(1f)); Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = target == "live", onClick = { target = "live" }, label = { Text("ไลฟ์สด (${live.size})") })
            FilterChip(selected = target == "nearby", onClick = { target = "nearby" }, label = { Text("คนใกล้คุณ (${nearby.size})") })
        }
        OutlinedTextField(name, { name = it.take(30) }, label = { Text("ชื่อที่แสดง (ไม่ใส่ก็ได้)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(enabled = !busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("🖼 เพิ่มรูปจากเครื่อง", color = PureWhite) }
        val list = if (target == "live") live else nearby
        list.forEachIndexed { i, it ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Slate900).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = it.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(10.dp))
                Text(it.name.ifBlank { "(ไม่มีชื่อ)" }, color = PureWhite, modifier = Modifier.weight(1f))
                TextButton(onClick = { list.removeAt(i) }) { Text("ลบ", color = Rose500) }
            }
        }
        Button(enabled = !busy, onClick = { scope.launch {
            val u = FirebaseAuth.getInstance().currentUser ?: return@launch
            busy = true
            val r = BrainApi.call(u, "PUT", "/admin/showcase", JSONObject().put("enabled", enabled).put("live", arr(live)).put("nearby", arr(nearby)))
            if (r.code == 200) { Showcase.load(force = true); msg = "บันทึกแล้ว" } else msg = "บันทึกไม่สำเร็จ (${r.code})"
            busy = false
        } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("บันทึก", color = PureWhite) }
        if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp)
    }
}
