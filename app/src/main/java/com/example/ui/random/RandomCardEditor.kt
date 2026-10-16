package com.example.ui.random

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.ui.components.dialogNavPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private val EPink = Color(0xFFFF2FA8)
private val EViolet = Color(0xFF9B3DFF)
private val EGold = Color(0xFFFFC94D)

/**
 * VIP editor for the สุ่มหาเพื่อน card. Everything is a draft with a live preview until 'ยืนยันและใช้การ์ดนี้':
 * then media is uploaded, the SERVER prices the changes, the user sees the list + total and confirms, and only then
 * diamonds are taken (server-side) and the card goes public. Closing / cancelling before that costs nothing.
 * Free: card photo (fills the card), intro text, positions, name-tag decoration. Paid: frame, name frame, effect, video, voice — every change.
 */
@Composable
fun RandomCardEditor(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = com.example.social.MyProfile.me
    LaunchedEffect(Unit) { com.example.social.MyProfile.start() }
    var cat by remember { mutableStateOf<CardCatalog?>(null) }
    var saved by remember { mutableStateOf<RandomCardRepo.Mine?>(null) }
    var loadErr by remember { mutableStateOf("") }
    // draft
    var card by remember { mutableStateOf(RandomCard()) }
    var newAvatar by remember { mutableStateOf<Uri?>(null) }; var clearAvatar by remember { mutableStateOf(false) }
    var newBg by remember { mutableStateOf<Uri?>(null) }; var clearBg by remember { mutableStateOf(false) }
    var newVideo by remember { mutableStateOf<Uri?>(null) }; var clearVideo by remember { mutableStateOf(false) }
    var newAudio by remember { mutableStateOf<java.io.File?>(null) }; var clearAudio by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf(CardPart.NAME) }
    var tab by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }
    var quote by remember { mutableStateOf<Pair<RandomCardRepo.Quote, JSONObject>?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val c = RandomCardRepo.catalog(); val m = RandomCardRepo.mine()
        if (c == null) { loadErr = "โหลดไม่สำเร็จ (เซิร์ฟเวอร์ยังไม่อัปเดต?)"; return@LaunchedEffect }
        cat = c; saved = m
        card = m.card ?: RandomCard(intro = me?.bio.orEmpty().take(200))
    }
    val c = cat
    val base = saved?.card ?: RandomCard()

    // live estimate (the server's number is the one that counts)
    fun priceOf(list: List<CardItem>?, id: String) = list?.firstOrNull { it.id == id }?.price ?: 0
    val estimate = if (c == null) 0 else
        (if (card.frame != "none" && card.frame != base.frame) priceOf(c.frames, card.frame) else 0) +
        (if (card.nameFrame != "none" && card.nameFrame != base.nameFrame) priceOf(c.nameFrames, card.nameFrame) else 0) +
        (if (card.effect != "none" && card.effect != base.effect) priceOf(c.effects, card.effect) else 0) +
        (if (newVideo != null) c.videoPrice else 0) + (if (newAudio != null) c.audioPrice else 0)

    var imageTarget by remember { mutableStateOf("avatar") }
    val pickImage2 = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { if (imageTarget == "avatar") { newAvatar = uri; clearAvatar = false } else { newBg = uri; clearBg = false }; dirty = true }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val (size, dur) = runCatching {
            val sz = ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            val mr = android.media.MediaMetadataRetriever(); mr.setDataSource(ctx, uri)
            val d = mr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L; mr.release(); sz to d
        }.getOrDefault(-1L to 0L)
        msg = when {
            size > 50L * 1024 * 1024 -> "วิดีโอใหญ่เกิน 50MB"
            dur > 31_000 -> "วิดีโอยาวเกิน 30 วินาที"
            else -> { newVideo = uri; clearVideo = false; dirty = true; "" }
        }
    }
    // the preview uses local files for anything new
    val preview = card.copy(
        avatarUrl = if (clearAvatar) "" else newAvatar?.toString() ?: card.avatarUrl,
        bgUrl = if (clearBg) "" else newBg?.toString() ?: card.bgUrl,
        videoUrl = if (clearVideo) "" else card.videoUrl, audioUrl = if (clearAudio) "" else card.audioUrl)

    fun draftJson(refs: Map<String, String?>): JSONObject {
        val L = card.layout
        fun p(x: Place) = JSONObject().put("x", x.x.toDouble()).put("y", x.y.toDouble()).put("s", x.s.toDouble())
        return JSONObject().put("intro", card.intro.take(200)).put("frame", card.frame).put("nameFrame", card.nameFrame).put("effect", card.effect)
            .put("nameDeco", card.nameDeco).put("nameColor", card.nameColor)
            .put("layout", JSONObject().put("avatar", p(L.avatar)).put("name", p(L.name)).put("intro", p(L.intro)))
            .apply { refs.forEach { (k, v) -> if (v != null) put(k, v) } }
    }
    /** Upload new media (no charge yet), then ask the server for the price. */
    fun prepare() {
        busy = "upload"; msg = ""
        scope.launch {
            val refs = mutableMapOf<String, String?>()
            if (clearAvatar) refs["avatarRef"] = "" else newAvatar?.let { u -> refs["avatarRef"] = RandomCardRepo.uploadImage(ctx, u, "avatar") ?: run { msg = "อัปโหลดรูปโปรไฟล์ไม่สำเร็จ"; busy = ""; return@launch } }
            if (clearBg) refs["bgRef"] = "" else newBg?.let { u -> refs["bgRef"] = RandomCardRepo.uploadImage(ctx, u, "bg") ?: run { msg = "อัปโหลดพื้นหลังไม่สำเร็จ"; busy = ""; return@launch } }
            if (clearVideo) refs["videoRef"] = "" else newVideo?.let { u -> refs["videoRef"] = RandomCardRepo.uploadVideo(ctx, u) ?: run { msg = "อัปโหลดวิดีโอไม่สำเร็จ"; busy = ""; return@launch } }
            if (clearAudio) refs["audioRef"] = "" else newAudio?.let { f -> refs["audioRef"] = RandomCardRepo.uploadAudio(f.readBytes()) ?: run { msg = "อัปโหลดเสียงไม่สำเร็จ"; busy = ""; return@launch } }
            val d = draftJson(refs)
            val q = RandomCardRepo.quote(d)
            busy = ""
            when {
                q == null -> msg = "คำนวณราคาไม่สำเร็จ"
                q.error.isNotBlank() -> msg = q.error
                else -> quote = q to d
            }
        }
    }

    Dialog(onDismissRequest = { if (dirty) confirmExit = true else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF12082B), Color(0xFF221046))))) {
            Column(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🖌️ แต่งหน้าสุ่ม", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    c?.let { Text("💎 ${it.diamonds}", color = Color(0xFF7AF5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    Text("✕", color = Color.White, fontSize = 18.sp, modifier = Modifier.clip(CircleShape).clickable { if (dirty) confirmExit = true else onClose() }.padding(10.dp))
                }
                if (c == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { if (loadErr.isNotBlank()) Text(loadErr, color = EGold) else CircularProgressIndicator(color = EPink) }
                    return@Column
                }
                // preview: tap an element to select it, drag to move, pinch to resize
                Text("แตะเลือกส่วนบนการ์ด · ลากเพื่อย้าย · ใช้สองนิ้วย่อ/ขยาย", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
                var cardSize by remember { mutableStateOf(IntSize.Zero) }
                Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                    RandomCardView(preview, me?.displayName ?: "ฉัน", me?.age, me?.avatar.orEmpty(),
                        Modifier.fillMaxHeight().onSizeChanged { cardSize = it }, videoUri = if (clearVideo) null else newVideo,
                        audioUri = if (clearAudio) null else newAudio?.let { Uri.fromFile(it) }, selected = sel,
                        partModifier = { part -> Modifier.pointerInput(part, cardSize) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                sel = part; dirty = true
                                val L = card.layout
                                val cur = when (part) { CardPart.AVATAR -> L.avatar; CardPart.NAME -> L.name; CardPart.INTRO -> L.intro }
                                val w = cardSize.width.coerceAtLeast(1); val h = cardSize.height.coerceAtLeast(1)
                                val nx = (cur.x + pan.x / w).coerceIn(0.05f, 0.95f); val ny = (cur.y + pan.y / h).coerceIn(0.05f, 0.95f)
                                val np = Place(nx, ny, (cur.s * zoom).coerceIn(0.5f, 2.2f))
                                card = card.copy(layout = when (part) { CardPart.AVATAR -> L.copy(avatar = np); CardPart.NAME -> L.copy(name = np); CardPart.INTRO -> L.copy(intro = np) })
                            }
                        }.clickable { sel = part } })
                }
                // tabs
                val tabs = listOf("รูป", "ข้อความ", "กรอบรูป", "ป้ายชื่อ", "เอฟเฟกต์", "วิดีโอ", "เสียง")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tabs.forEachIndexed { i, t ->
                        Text(t, color = if (tab == i) Color.White else Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (tab == i) Brush.horizontalGradient(listOf(EPink, EViolet)) else Brush.horizontalGradient(listOf(Color(0x22FFFFFF), Color(0x22FFFFFF))))
                                .clickable { tab = i }.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
                Column(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 190.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    when (tab) {
                        0 -> {
                            FreeNote()
                            Text("รูปจะแสดงเต็มการ์ด", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill("📷 เลือกรูปการ์ด") { imageTarget = "avatar"; pickImage2.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                if (preview.avatarUrl.isNotBlank()) Pill("ใช้รูปโปรไฟล์ปกติ", ghost = true) { newAvatar = null; clearAvatar = true; dirty = true }
                            }
                        }
                        1 -> {
                            FreeNote()
                            OutlinedTextField(card.intro, { card = card.copy(intro = it.take(200)); dirty = true }, label = { Text("ข้อความแนะนำตัว / ต้อนรับ") }, minLines = 2, maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
                            Text("${card.intro.length}/200", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                        2 -> Options(c.frames, card.frame, base.frame) { id, item -> card = card.copy(frame = id, frameColors = item?.colors ?: emptyList()); dirty = true }
                        3 -> {
                            NameFrameOptions(c.nameFrames, card.nameFrame, base.nameFrame, card.nameDeco, card.nameColor) { id, item ->
                                card = card.copy(nameFrame = id, nameFrameColors = item?.colors ?: emptyList(), nameFrameStyle = item?.style ?: "pill"); dirty = true }
                            FreeNote()
                            Text("ตกแต่งกรอบป้ายชื่อ", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                NAME_DECOS.forEach { (g, label) ->
                                    val on = card.nameDeco == g
                                    Text(if (g.isBlank()) label else "$g $label", color = Color.White, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                        .background(if (on) EPink else Color(0x22FFFFFF)).clickable { card = card.copy(nameDeco = g); dirty = true }.padding(horizontal = 10.dp, vertical = 6.dp))
                                }
                            }
                            Text("สีตัวอักษร", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NAME_COLORS.forEach { hx ->
                                    val col = RandomCardRepo.hex(hx)
                                    Box(Modifier.size(30.dp).clip(CircleShape).background(col ?: Color.White)
                                        .border(if (card.nameColor == hx) 3.dp else 1.dp, if (card.nameColor == hx) EPink else Color(0x55FFFFFF), CircleShape)
                                        .clickable { card = card.copy(nameColor = hx); dirty = true }, contentAlignment = Alignment.Center) {
                                        if (hx.isBlank()) Text("A", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        4 -> Options(c.effects, card.effect, base.effect) { id, item -> card = card.copy(effect = id, effectGlyphs = item?.glyphs ?: emptyList(), effectMotion = item?.motion ?: "rise"); dirty = true }
                        5 -> {
                            PaidNote(c.videoPrice, "วิดีโอ (ไม่เกิน 30 วินาที) เล่นอัตโนมัติแทนรูปโปรไฟล์")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill("🎬 เลือกวิดีโอ  💎${c.videoPrice}") { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
                                if (newVideo != null || (preview.videoUrl.isNotBlank())) Pill("ไม่ใช้วิดีโอ", ghost = true) { newVideo = null; clearVideo = true; dirty = true }
                            }
                            if (newVideo != null) Text("เลือกวิดีโอใหม่แล้ว (ยังไม่หักเพชร)", color = EGold, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                        6 -> VoiceRecorder(c.audioPrice, hasVoice = newAudio != null || preview.audioUrl.isNotBlank(),
                            onRecorded = { f -> newAudio = f; clearAudio = false; dirty = true }, onRemove = { newAudio = null; clearAudio = true; dirty = true })
                    }
                }
                if (msg.isNotBlank()) Text(msg, color = EGold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
                // total + confirm
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ราคารวมโดยประมาณ", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Text(if (estimate == 0) "ฟรี" else "💎 $estimate", color = if (estimate == 0) Color(0xFF34D399) else Color(0xFF7AF5FF), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Box(Modifier.clip(RoundedCornerShape(24.dp)).background(Brush.horizontalGradient(listOf(EPink, EViolet))).clickable(enabled = busy.isEmpty()) { prepare() }
                        .padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                        if (busy.isNotEmpty()) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("ยืนยันและใช้การ์ดนี้", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    // price list from the server → confirm → charge + publish
    quote?.let { (q, d) ->
        var paying by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { if (!paying) quote = null },
            title = { Text("ยืนยันการตกแต่ง") },
            text = { Column {
                if (q.items.isEmpty()) Text("ไม่มีรายการที่คิดเพชร (รูป ข้อความ และตำแหน่งใช้ฟรี)")
                q.items.forEach { Row { Text(it.name, modifier = Modifier.weight(1f)); Text("💎 ${it.price}", fontWeight = FontWeight.Bold) } }
                Spacer(Modifier.height(8.dp))
                Row { Text("รวม", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(if (q.total == 0) "ฟรี" else "💎 ${q.total}", fontWeight = FontWeight.ExtraBold) }
                Text("เพชรคงเหลือ 💎 ${c?.diamonds ?: 0}", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                if (q.total > (c?.diamonds ?: 0)) Text("เพชรไม่พอ — เติมเพชรได้ที่หน้า 'ฉัน'", color = Color(0xFFE11D48), fontSize = 12.sp)
            } },
            confirmButton = { TextButton(enabled = !paying && q.total <= (c?.diamonds ?: 0), onClick = {
                paying = true
                scope.launch {
                    val (err, _) = RandomCardRepo.save(d.put("expectedTotal", q.total))
                    paying = false; quote = null
                    if (err == null) { msg = ""; dirty = false; onClose() } else msg = err
                }
            }) { Text(if (paying) "กำลังบันทึก…" else if (q.total == 0) "ยืนยันและใช้การ์ดนี้" else "จ่าย 💎${q.total} และใช้การ์ดนี้", color = EPink, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(enabled = !paying, onClick = { quote = null }) { Text("ยกเลิก") } })
    }
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false }, title = { Text("ออกโดยไม่บันทึก?") },
        text = { Text("การตกแต่งที่ยังไม่ยืนยันจะหายไป และไม่มีการหักเพชร") },
        confirmButton = { TextButton(onClick = { confirmExit = false; onClose() }) { Text("ออก", color = EPink) } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("แต่งต่อ") } })
}

@Composable private fun FreeNote() = Text("ใช้ฟรี", color = Color(0xFF34D399), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
@Composable private fun PaidNote(price: Int, text: String) = Text("$text · 💎$price ทุกครั้งที่เปลี่ยน", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))

@Composable
private fun Pill(text: String, ghost: Boolean = false, onClick: () -> Unit) {
    Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(18.dp))
        .then(if (ghost) Modifier.border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(18.dp)) else Modifier.background(Brush.horizontalGradient(listOf(EPink, EViolet))))
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

/** Paid choices: 'ไม่ใช้' + catalog items with price; the saved one is marked 'ใช้อยู่' (keeping it costs nothing). */
@Composable
private fun Options(items: List<CardItem>, cur: String, savedId: String, onPick: (String, CardItem?) -> Unit) {
    Text("คิดเพชรทุกครั้งที่เปลี่ยน (เปลี่ยนกลับมาอันเดิมก็คิดใหม่)", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OptionTile("ไม่ใช้", "ฟรี", null, emptyList(), cur == "none") { onPick("none", null) }
        items.forEach { item ->
            OptionTile(item.name + (if (item.glyphs.isNotEmpty()) MOTION_TH[item.motion]?.let { " · $it" } ?: "" else ""), if (item.id == savedId) "ใช้อยู่" else "💎${item.price}",
                item.colors.takeIf { c -> c.size >= 2 }, item.glyphs, cur == item.id) { onPick(item.id, item) }
        }
    }
}

private val MOTION_TH = mapOf("rise" to "ลอยขึ้น", "fall" to "โปรยลง", "orbit" to "โคจร", "twinkle" to "กะพริบ", "burst" to "ระเบิด", "sway" to "พลิ้ว",
    "spiral" to "หมุนวน", "rain" to "ฝนตก", "zigzag" to "บินซิกแซก", "shoot" to "พุ่ง")

/** Name frames shown as real mini name tags, so each shape can be seen before choosing. */
@Composable
private fun NameFrameOptions(items: List<CardItem>, cur: String, savedId: String, deco: String, color: String, onPick: (String, CardItem?) -> Unit) {
    Text("กรอบป้ายชื่อ · คิดเพชรทุกครั้งที่เปลี่ยน", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        (listOf<CardItem?>(null) + items).forEach { item ->
            val id = item?.id ?: "none"; val on = cur == id
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) Color(0x33FF2FA8) else Color.Transparent)
                .border(if (on) 2.dp else 0.dp, if (on) EPink else Color.Transparent, RoundedCornerShape(12.dp)).clickable { onPick(id, item) }.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                NameTag("ชื่อ", id, item?.colors ?: emptyList(), 0.7f, item?.style ?: "pill", deco, color)
                Text(item?.name ?: "ไม่ใช้", color = if (on) EPink else Color.White, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                Text(when { item == null -> "ฟรี"; item.id == savedId -> "ใช้อยู่"; else -> "💎${item.price}" }, color = Color(0xFF7AF5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OptionTile(name: String, price: String, colors: List<Color>?, glyphs: List<String>, on: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(92.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(58.dp).clip(CircleShape)
            .background(if (colors != null) Brush.sweepGradient(colors + colors.first()) else Brush.linearGradient(listOf(Color(0x22FFFFFF), Color(0x22FFFFFF))))
            .border(if (on) 3.dp else 1.dp, if (on) Color.White else Color(0x33FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
            if (glyphs.isNotEmpty()) Text(glyphs.first(), fontSize = 24.sp) else if (colors == null) Text("🚫", fontSize = 20.sp)
        }
        Text(name, color = if (on) EPink else Color.White, fontSize = 11.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        Text(price, color = Color(0xFF7AF5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Record a voice greeting (≤ 20 s, AAC). Needs the microphone permission. */
@Composable
private fun VoiceRecorder(price: Int, hasVoice: Boolean, onRecorded: (java.io.File) -> Unit, onRemove: () -> Unit) {
    val ctx = LocalContext.current
    var rec by remember { mutableStateOf<MediaRecorder?>(null) }
    var file by remember { mutableStateOf<java.io.File?>(null) }
    var secs by remember { mutableIntStateOf(0) }
    var err by remember { mutableStateOf("") }
    fun stop() {
        val r = rec ?: return; rec = null
        val ok = runCatching { r.stop() }.isSuccess; runCatching { r.release() }
        val f = file
        if (ok && f != null && f.length() > 500) onRecorded(f) else err = "อัดเสียงไม่สำเร็จ ลองใหม่"
    }
    fun start() {
        err = ""
        val f = java.io.File(ctx.cacheDir, "rc_voice_${System.currentTimeMillis()}.m4a"); file = f
        val r = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC); r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); r.setAudioEncodingBitRate(64_000); r.setAudioSamplingRate(44_100)
            r.setMaxDuration(20_000); r.setOutputFile(f.absolutePath)
            r.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stop() }
            r.prepare(); r.start(); rec = r; secs = 0
        }.onFailure { runCatching { r.release() }; err = "เปิดไมโครโฟนไม่ได้" }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) start() else err = "ต้องอนุญาตไมโครโฟนก่อน" }
    LaunchedEffect(rec) { while (rec != null) { delay(1000); secs++ } }
    DisposableEffect(Unit) { onDispose { runCatching { rec?.release() } } }
    PaidNote(price, "เสียงทักทาย (ไม่เกิน 20 วินาที) เล่นอัตโนมัติเมื่อมีคนเห็นการ์ด")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (rec == null) Pill("🎙️ อัดเสียง  💎$price") {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start() else perm.launch(Manifest.permission.RECORD_AUDIO)
        } else Pill("⏹ หยุด (${secs}s)") { stop() }
        if (hasVoice && rec == null) Pill("ไม่ใช้เสียง", ghost = true) { onRemove() }
    }
    if (err.isNotBlank()) Text(err, color = EGold, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
}
