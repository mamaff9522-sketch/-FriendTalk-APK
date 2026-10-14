package com.example.ui.feed

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.social.FeedPost
import com.example.social.FeedRepo
import com.example.social.PostPlace
import com.example.ui.components.ft.Text
import com.example.ui.components.tr
import com.example.ui.social.Avatar
import com.example.ui.social.currentLocation
import com.example.ui.social.hasLocPerm
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/* Post composer: text, images, one video, privacy (public / friends / only_me) and an optional place.
 * Uploads use the existing Storage folder posts/{uid}/ and friendtalk-brain POST /posts (which enforces visibility). */

private val CBg = Color(0xFF0A0820)
private val CCard = Color(0xFF16123A)
private val CLine = Color(0xFF2A2357)
private val CNeon = Color(0xFFFF2FA8)
private val CViolet = Color(0xFFB04DFF)
private val CMuted = Color(0xFFA49CC8)
private val CSoft = Color(0xFFD9D3F7)

private data class Privacy(val key: String, val icon: String, val label: String, val desc: String)
private val PRIVACY = listOf(
    Privacy("public", "🌐", "สาธารณะ", "ทุกคนเห็นโพสต์นี้ได้"),
    Privacy("friends", "👥", "เฉพาะเพื่อน", "เฉพาะคนที่เป็นเพื่อนกับคุณ"),
    Privacy("only_me", "🔒", "เฉพาะฉัน", "มีแค่คุณที่เห็นโพสต์นี้")
)
fun privacyIcon(v: String) = PRIVACY.firstOrNull { it.key == v }?.icon ?: "🌐"

/** Opens the place in a map app. Exact coordinates exist only when the author agreed to share a ~1 km point. */
fun openPlaceOnMap(ctx: Context, label: String, lat: Double?, lng: Double?) {
    val q = Uri.encode(label)
    val geo = if (lat != null && lng != null) "geo:$lat,$lng?q=$lat,$lng($q)" else "geo:0,0?q=$q"
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geo))) }
    catch (_: ActivityNotFoundException) {
        val web = if (lat != null && lng != null) "https://www.google.com/maps/search/?api=1&query=$lat,$lng" else "https://www.google.com/maps/search/?api=1&query=$q"
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web))) }
    }
}

@Composable
private fun rememberVideoFrame(uri: Uri?): ImageBitmap? {
    val ctx = LocalContext.current
    val v by produceState<ImageBitmap?>(null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val m = android.media.MediaMetadataRetriever()
                try { m.setDataSource(ctx, uri); m.getFrameAtTime(0)?.asImageBitmap() } finally { runCatching { m.release() } }
            }.getOrNull()
        }
    }
    return v
}

private fun fmtDuration(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

/**
 * Full-screen "สร้างโพสต์". [startWith] = "image" / "video" opens that picker right away.
 * [onPosted] receives the post saved by the server (shown at the top of the feed).
 */
@Composable
fun CreatePostDialog(startWith: String? = null, onDismiss: () -> Unit, onPosted: (FeedPost) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = FirebaseAuth.getInstance().currentUser
    val myInfo by produceState<HomeRepo.UserInfo?>(null, me?.uid) { value = me?.uid?.let { HomeRepo.user(it) } }
    var text by remember { mutableStateOf("") }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var video by remember { mutableStateOf<Uri?>(null) }
    var videoMs by remember { mutableStateOf(0L) }
    var visibility by remember { mutableStateOf("public") }
    var place by remember { mutableStateOf<PostPlace?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var privacyMenu by remember { mutableStateOf(false) }
    var placePicker by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val limits by com.example.ads.AdRoom.limits.collectAsState()
    LaunchedEffect(Unit) { com.example.ads.AdRoom.refresh() }
    val posting = limits?.posting
    val locked = posting != null && !posting.first

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { picked ->
        if (picked.isNotEmpty()) { images = (images + picked).distinct().take(4); if (images.size < (images + picked).distinct().size) msg = tr("แนบรูปได้สูงสุด 4 รูป") }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val info = FeedRepo.videoInfo(ctx, uri)
            when {
                info == null -> msg = tr("อ่านไฟล์วิดีโอไม่ได้")
                info.first > FeedRepo.VIDEO_MAX_BYTES -> msg = tr("วิดีโอใหญ่เกิน 50MB")
                info.second > FeedRepo.VIDEO_MAX_MS -> msg = tr("วิดีโอยาวเกิน 3 นาที")
                else -> { video = uri; videoMs = info.second; msg = "" }
            }
        }
    }
    fun pickImages() = imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun pickVideo() = videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    LaunchedEffect(startWith) { when (startWith) { "image" -> pickImages(); "video" -> pickVideo(); else -> {} } }

    val hasContent = text.isNotBlank() || images.isNotEmpty() || video != null
    val canPost = hasContent && !busy && !locked
    fun close() { if (busy) return; if (hasContent || place != null) confirmDiscard = true else onDismiss() }
    fun publish() {
        if (!canPost) return
        busy = true; msg = ""; progress = tr("กำลังเตรียมโพสต์…")
        scope.launch {
            FeedRepo.createPostFull(ctx, text, images, video, place, visibility) { progress = it }
                .onSuccess { onPosted(it); onDismiss() }
                .onFailure { msg = it.message ?: tr("โพสต์ไม่สำเร็จ") }
            busy = false; progress = ""
        }
    }

    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = CBg) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                // top bar: ✕  สร้างโพสต์  [โพสต์]
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { close() }) { Icon(Icons.Filled.Close, tr("ปิด"), tint = CSoft) }
                    Text(tr("สร้างโพสต์"), color = PureWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (canPost) Brush.horizontalGradient(listOf(CNeon, CViolet)) else SolidColor(Color(0xFF2A2357)))
                        .clickable(enabled = canPost) { publish() }.padding(horizontal = 18.dp, vertical = 8.dp)) {
                        Text(if (busy) tr("กำลังโพสต์…") else tr("โพสต์"), color = if (canPost || busy) PureWhite else CMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                HorizontalDivider(color = CLine, thickness = 0.8.dp)

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    // avatar + name + privacy under the name
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(myInfo?.avatar ?: me?.photoUrl?.toString().orEmpty(), myInfo?.name ?: me?.displayName ?: tr("ฉัน"), 48)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(myInfo?.name ?: me?.displayName ?: tr("ฉัน"), color = PureWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            val cur = PRIVACY.first { it.key == visibility }
                            Box {
                                Surface(onClick = { privacyMenu = true }, enabled = !busy, shape = RoundedCornerShape(8.dp), color = CCard, border = BorderStroke(1.dp, Color(0xFF4A3590))) {
                                    Text("${cur.icon} ${tr(cur.label)} ▾", color = CSoft, fontSize = 12.5.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                }
                                DropdownMenu(expanded = privacyMenu, onDismissRequest = { privacyMenu = false }) {
                                    PRIVACY.forEach { pv ->
                                        DropdownMenuItem(onClick = { visibility = pv.key; privacyMenu = false }, text = {
                                            Column {
                                                Text("${pv.icon} ${tr(pv.label)}" + if (pv.key == visibility) "  ✓" else "", fontWeight = FontWeight.Medium)
                                                Text(tr(pv.desc), fontSize = 12.sp, color = Slate400)
                                            }
                                        })
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    BasicTextField(value = text, onValueChange = { if (it.length <= 2000) text = it }, enabled = !busy,
                        textStyle = TextStyle(color = PureWhite, fontSize = 18.sp, lineHeight = 26.sp), cursorBrush = SolidColor(CNeon),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        decorationBox = { inner -> Box { if (text.isEmpty()) Text(tr("คุณกำลังคิดอะไรอยู่?"), color = Color(0xFF8E86B8), fontSize = 18.sp); inner() } })
                    if (text.length > 1800) Text("${text.length}/2000", color = CMuted, fontSize = 11.sp)

                    // place chip: change / remove, and consent for showing a ~1 km point
                    place?.let { pl ->
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CCard).border(1.dp, Color(0xFF4A3590), RoundedCornerShape(12.dp)).padding(start = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocationOn, null, tint = CNeon, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(pl.label, color = PureWhite, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            TextButton(onClick = { placePicker = true }, enabled = !busy) { Text(tr("เปลี่ยน"), color = CNeon, fontSize = 13.sp) }
                            IconButton(onClick = { place = null }, enabled = !busy) { Icon(Icons.Filled.Close, tr("ลบสถานที่"), tint = CMuted, modifier = Modifier.size(18.dp)) }
                        }
                        if (pl.lat != null && pl.lng != null) Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("ให้คนอื่นเปิดดูจุดบนแผนที่ได้"), color = CSoft, fontSize = 13.sp)
                                Text(tr("แสดงแค่ตำแหน่งโดยประมาณ (~1 กม.) ถ้าไม่เปิด จะแสดงเฉพาะชื่อสถานที่"), color = CMuted, fontSize = 11.sp)
                            }
                            Switch(checked = pl.shareCoords, onCheckedChange = { place = pl.copy(shareCoords = it) }, enabled = !busy,
                                colors = SwitchDefaults.colors(checkedTrackColor = CNeon))
                        }
                    }

                    // previews with remove buttons
                    if (images.isNotEmpty() || video != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            video?.let { v ->
                                val frame = rememberVideoFrame(v)
                                Box(Modifier.size(130.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
                                    frame?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                    Box(Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(Color(0x80000000)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.PlayArrow, null, tint = PureWhite)
                                    }
                                    Text(fmtDuration(videoMs), color = PureWhite, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                                        .clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 4.dp))
                                    RemoveDot(Modifier.align(Alignment.TopEnd), !busy) { video = null; videoMs = 0 }
                                }
                            }
                            images.forEach { u ->
                                Box(Modifier.size(130.dp).clip(RoundedCornerShape(12.dp))) {
                                    AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    RemoveDot(Modifier.align(Alignment.TopEnd), !busy) { images = images - u }
                                }
                            }
                        }
                    }
                    if (locked) Text("🔒 " + tr("โพสต์ไม่ได้ตอนนี้") + ": ${posting?.second}", color = Amber400, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                    if (progress.isNotBlank()) Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = CNeon, strokeWidth = 2.dp, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                        Text(progress, color = CSoft, fontSize = 13.sp)
                    }
                    if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                }

                HorizontalDivider(color = CLine, thickness = 0.8.dp)
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComposerAction(Modifier.weight(1f), { Icon(Icons.Outlined.Image, null, tint = Color(0xFF4ADE80), modifier = Modifier.size(20.dp)) },
                        tr("รูปภาพ") + if (images.isNotEmpty()) " ${images.size}/4" else "", !busy && images.size < 4) { pickImages() }
                    ComposerAction(Modifier.weight(1f), { Icon(Icons.Outlined.Videocam, null, tint = Color(0xFFFF6B8F), modifier = Modifier.size(20.dp)) },
                        tr("วิดีโอ"), !busy && video == null) { pickVideo() }
                    ComposerAction(Modifier.weight(1.3f), { Icon(Icons.Filled.LocationOn, null, tint = CNeon, modifier = Modifier.size(20.dp)) },
                        tr("เพิ่มสถานที่ 📍"), !busy) { placePicker = true }
                }
            }
        }
    }
    if (placePicker) PlacePickerDialog(current = place, onDismiss = { placePicker = false }, onPick = { place = it; placePicker = false })
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text(tr("ทิ้งโพสต์นี้?")) },
        text = { Text(tr("ข้อความและไฟล์ที่เลือกไว้จะหายไป")) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text(tr("ทิ้ง"), color = Rose500) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(tr("แก้ไขต่อ")) } })
}

@Composable
private fun RemoveDot(modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(modifier.padding(5.dp).size(24.dp).clip(CircleShape).background(Color(0xCC000000)).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Close, tr("ลบไฟล์"), tint = PureWhite, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun ComposerAction(modifier: Modifier, icon: @Composable () -> Unit, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp), color = CCard, border = BorderStroke(1.dp, CLine), modifier = modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            icon(); Spacer(Modifier.width(5.dp))
            Text(label, color = if (enabled) CSoft else CMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------- place picker ----------------

val THAI_PROVINCES = listOf(
    "กรุงเทพมหานคร", "กระบี่", "กาญจนบุรี", "กาฬสินธุ์", "กำแพงเพชร", "ขอนแก่น", "จันทบุรี", "ฉะเชิงเทรา", "ชลบุรี", "ชัยนาท",
    "ชัยภูมิ", "ชุมพร", "เชียงราย", "เชียงใหม่", "ตรัง", "ตราด", "ตาก", "นครนายก", "นครปฐม", "นครพนม",
    "นครราชสีมา", "นครศรีธรรมราช", "นครสวรรค์", "นนทบุรี", "นราธิวาส", "น่าน", "บึงกาฬ", "บุรีรัมย์", "ปทุมธานี", "ประจวบคีรีขันธ์",
    "ปราจีนบุรี", "ปัตตานี", "พระนครศรีอยุธยา", "พะเยา", "พังงา", "พัทลุง", "พิจิตร", "พิษณุโลก", "เพชรบุรี", "เพชรบูรณ์",
    "แพร่", "ภูเก็ต", "มหาสารคาม", "มุกดาหาร", "แม่ฮ่องสอน", "ยโสธร", "ยะลา", "ร้อยเอ็ด", "ระนอง", "ระยอง",
    "ราชบุรี", "ลพบุรี", "ลำปาง", "ลำพูน", "เลย", "ศรีสะเกษ", "สกลนคร", "สงขลา", "สตูล", "สมุทรปราการ",
    "สมุทรสงคราม", "สมุทรสาคร", "สระแก้ว", "สระบุรี", "สิงห์บุรี", "สุโขทัย", "สุพรรณบุรี", "สุราษฎร์ธานี", "สุรินทร์", "หนองคาย",
    "หนองบัวลำภู", "อ่างทอง", "อำนาจเจริญ", "อุดรธานี", "อุตรดิตถ์", "อุทัยธานี", "อุบลราชธานี"
)

private data class PlaceHit(val label: String, val lat: Double?, val lng: Double?)

/** Short readable name from a geocoder result (name, district, province). */
private fun addressLabel(a: android.location.Address): String {
    val parts = listOfNotNull(
        a.featureName?.takeIf { f -> f.isNotBlank() && f.any { !it.isDigit() } && f != a.subLocality && f != a.locality && f != a.adminArea },
        a.subLocality, a.locality ?: a.subAdminArea, a.adminArea
    ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
    return parts.joinToString(", ").ifBlank { a.getAddressLine(0).orEmpty() }.take(80)
}

@Suppress("DEPRECATION")
private suspend fun geocodeName(ctx: Context, q: String): List<PlaceHit>? = withContext(Dispatchers.IO) {
    if (!android.location.Geocoder.isPresent()) return@withContext null
    runCatching { android.location.Geocoder(ctx, Locale("th", "TH")).getFromLocationName(q, 8).orEmpty().map { PlaceHit(addressLabel(it), it.latitude, it.longitude) } }.getOrNull()
}

@Suppress("DEPRECATION")
private suspend fun reverseGeocode(ctx: Context, lat: Double, lng: Double): List<String> = withContext(Dispatchers.IO) {
    if (!android.location.Geocoder.isPresent()) return@withContext emptyList()
    runCatching {
        android.location.Geocoder(ctx, Locale("th", "TH")).getFromLocation(lat, lng, 3).orEmpty().flatMap { a ->
            listOfNotNull(addressLabel(a), listOfNotNull(a.subLocality, a.adminArea).joinToString(", ").ifBlank { null },
                listOfNotNull(a.locality ?: a.subAdminArea, a.adminArea).joinToString(", ").ifBlank { null })
        }.filter { it.isNotBlank() }.distinct()
    }.getOrDefault(emptyList())
}

/**
 * "เพิ่มสถานที่ 📍": current location (asks permission first), search by name, pick on a map, province / district, or none.
 * Nothing here turns on GPS or shares a location by itself.
 */
@Composable
fun PlacePickerDialog(current: PostPlace?, onDismiss: () -> Unit, onPick: (PostPlace?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("menu") }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("") }
    // results of GPS / map: a point plus suggested names; the user can also type their own name
    var point by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var customName by remember { mutableStateOf("") }

    fun usePoint(lat: Double, lng: Double) {
        point = lat to lng; busy = true; info = tr("กำลังหาชื่อสถานที่…"); mode = "name"
        scope.launch {
            names = reverseGeocode(ctx, lat, lng)
            customName = names.firstOrNull() ?: ""
            info = if (names.isEmpty()) tr("ไม่พบชื่อสถานที่ของจุดนี้ พิมพ์ชื่อเองได้") else ""
            busy = false
        }
    }
    fun locateNow() {
        busy = true; info = tr("กำลังหาตำแหน่งปัจจุบัน…")
        scope.launch {
            val loc = currentLocation(ctx)
            if (loc == null) { busy = false; info = tr("หาตำแหน่งไม่ได้ กรุณาเปิดตำแหน่ง (Location) ในเครื่องแล้วลองใหม่") }
            else usePoint(loc.latitude, loc.longitude)
        }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) locateNow() else { mode = "menu"; info = tr("ไม่ได้รับอนุญาตให้ใช้ตำแหน่ง เลือกสถานที่วิธีอื่นได้") }
    }

    if (mode == "map") { MapPickDialog(start = point ?: current?.let { c -> if (c.lat != null && c.lng != null) c.lat to c.lng else null }, onDismiss = { mode = "menu" }, onPick = { la, lo -> usePoint(la, lo) }); return }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = 640.dp), shape = RoundedCornerShape(22.dp), color = Color(0xFF120E2E), border = BorderStroke(1.dp, Color(0xFF4A3590))) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mode != "menu") TextButton(onClick = { mode = "menu"; info = "" }, enabled = !busy) { Text("‹ " + tr("กลับ"), color = CSoft) }
                    Text(tr("เพิ่มสถานที่ 📍"), color = PureWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, tr("ปิด"), tint = CMuted) }
                }
                if (info.isNotBlank()) Text(info, color = if (busy) CSoft else Amber400, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                if (busy) LinearProgressIndicator(color = CNeon, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                when (mode) {
                    "menu" -> Column {
                        PickRow({ Icon(Icons.Outlined.MyLocation, null, tint = CNeon) }, tr("ใช้ตำแหน่งปัจจุบัน (GPS)"), tr("จะขออนุญาตก่อนทุกครั้ง")) {
                            mode = "gps"
                            if (hasLocPerm(ctx)) locateNow() else permLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                        }
                        PickRow({ Icon(Icons.Outlined.Search, null, tint = CNeon) }, tr("ค้นหาชื่อสถานที่"), tr("เช่น ร้านกาแฟ ห้าง หรือสถานที่ท่องเที่ยว")) { mode = "search"; info = "" }
                        PickRow({ Icon(Icons.Outlined.Map, null, tint = CNeon) }, tr("เลือกตำแหน่งจากแผนที่"), tr("เลื่อนแผนที่ให้หมุดอยู่ตรงจุดที่ต้องการ")) { mode = "map"; info = "" }
                        PickRow({ Text("🏙", fontSize = 20.sp) }, tr("เลือกจังหวัด / อำเภอ"), tr("ไม่ใช้พิกัด แสดงเฉพาะชื่อพื้นที่")) { mode = "province"; info = "" }
                        PickRow({ Text("🚫", fontSize = 20.sp) }, tr("ไม่ระบุสถานที่"), if (current != null) tr("ลบ") + " \"${current.label}\"" else tr("โพสต์โดยไม่มีสถานที่")) { onPick(null) }
                    }
                    "gps" -> if (!busy) TextButton(onClick = { if (hasLocPerm(ctx)) locateNow() else permLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }) {
                        Text(tr("ลองอีกครั้ง"), color = CNeon)
                    }
                    "name" -> Column {
                        Text(tr("เลือกชื่อสถานที่"), color = CSoft, fontSize = 13.sp)
                        LazyColumn(Modifier.heightIn(max = 220.dp)) {
                            items(names) { n -> PickRow({ Icon(Icons.Filled.LocationOn, null, tint = CNeon) }, n, null) { customName = n } }
                        }
                        PlaceTextField(customName, { customName = it }, tr("หรือพิมพ์ชื่อสถานที่เอง"))
                        Button(onClick = { point?.let { (la, lo) -> onPick(PostPlace(customName.trim().take(80), la, lo)) } }, enabled = customName.isNotBlank() && point != null,
                            colors = ButtonDefaults.buttonColors(containerColor = CNeon), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(tr("ใช้สถานที่นี้")) }
                    }
                    "search" -> PlaceSearch(onPick = { onPick(it) })
                    "province" -> ProvincePicker(onPick = { onPick(it) })
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun PickRow(icon: @Composable () -> Unit, title: String, sub: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = PureWhite, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, color = CMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlaceTextField(value: String, onChange: (String) -> Unit, hint: String, onDone: (() -> Unit)? = null) {
    OutlinedTextField(value = value, onValueChange = { if (it.length <= 80) onChange(it) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        placeholder = { Text(hint, color = CMuted) },
        keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Search else ImeAction.Done),
        keyboardActions = KeyboardActions(onSearch = { onDone?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = PureWhite, unfocusedTextColor = PureWhite, focusedBorderColor = CNeon, unfocusedBorderColor = Color(0xFF4A3590)))
}

@Composable
private fun PlaceSearch(onPick: (PostPlace) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<PlaceHit>?>(null) }
    var note by remember { mutableStateOf("") }
    fun search() {
        if (q.isBlank() || busy) return
        busy = true; note = ""
        scope.launch {
            val r = geocodeName(ctx, q.trim())
            hits = r ?: emptyList()
            if (r == null) note = tr("เครื่องนี้ค้นหาสถานที่ไม่ได้ ใช้ชื่อที่พิมพ์ได้เลย") else if (r.isEmpty()) note = tr("ไม่พบสถานที่ ลองคำอื่น หรือใช้ชื่อที่พิมพ์")
            busy = false
        }
    }
    Column {
        PlaceTextField(q, { q = it }, tr("พิมพ์ชื่อสถานที่"), onDone = { search() })
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { search() }, enabled = q.isNotBlank() && !busy, colors = ButtonDefaults.buttonColors(containerColor = CNeon)) { Text(tr("ค้นหา")) }
            OutlinedButton(onClick = { onPick(PostPlace(q.trim().take(80))) }, enabled = q.isNotBlank()) { Text(tr("ใช้ชื่อนี้"), color = CSoft) }
        }
        if (busy) LinearProgressIndicator(color = CNeon, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        if (note.isNotBlank()) Text(note, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        LazyColumn(Modifier.heightIn(max = 320.dp)) {
            items(hits.orEmpty()) { h -> PickRow({ Icon(Icons.Filled.LocationOn, null, tint = CNeon) }, h.label, null) { onPick(PostPlace(h.label, h.lat, h.lng)) } }
        }
    }
}

@Composable
private fun ProvincePicker(onPick: (PostPlace) -> Unit) {
    var filter by remember { mutableStateOf("") }
    var province by remember { mutableStateOf<String?>(null) }
    var district by remember { mutableStateOf("") }
    val p = province
    if (p == null) Column {
        PlaceTextField(filter, { filter = it }, tr("ค้นหาจังหวัด"))
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            items(THAI_PROVINCES.filter { filter.isBlank() || it.contains(filter.trim()) }) { name ->
                Text(name, color = PureWhite, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { province = name }.padding(horizontal = 8.dp, vertical = 11.dp))
            }
        }
    } else Column {
        val bkk = p == "กรุงเทพมหานคร"
        Text(tr("จังหวัด") + ": $p", color = PureWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        TextButton(onClick = { province = null; district = "" }) { Text(tr("เปลี่ยนจังหวัด"), color = CNeon, fontSize = 13.sp) }
        PlaceTextField(district, { district = it }, if (bkk) tr("เขต (ไม่บังคับ)") else tr("อำเภอ (ไม่บังคับ)"))
        Button(onClick = {
            val d = district.trim().removePrefix("อำเภอ").removePrefix("อ.").removePrefix("เขต").trim()
            val label = when { d.isBlank() -> p; bkk -> "เขต$d, $p"; else -> "อ.$d, จ.$p" }
            onPick(PostPlace(label.take(80)))
        }, colors = ButtonDefaults.buttonColors(containerColor = CNeon), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(tr("ใช้พื้นที่นี้")) }
    }
}

/** OpenStreetMap picker (no API key): pan the map under the centre pin, then confirm. Starts at Bangkok, never at GPS. */
@Composable
fun MapPickDialog(start: Pair<Double, Double>?, onDismiss: () -> Unit, onPick: (Double, Double) -> Unit) {
    val ctx = LocalContext.current
    val mapView = remember {
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = java.io.File(ctx.cacheDir, "osmdroid")
            osmdroidTileCache = java.io.File(ctx.cacheDir, "osmdroid/tiles")
        }
        org.osmdroid.views.MapView(ctx).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(if (start != null) 15.0 else 11.0)
            controller.setCenter(org.osmdroid.util.GeoPoint(start?.first ?: 13.7563, start?.second ?: 100.5018))
        }
    }
    DisposableEffect(Unit) { mapView.onResume(); onDispose { mapView.onPause(); mapView.onDetach() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = CBg) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, tr("ปิด"), tint = CSoft) }
                    Text(tr("เลือกตำแหน่งจากแผนที่"), color = PureWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                    // centre pin (its tip marks the chosen point)
                    Icon(Icons.Filled.LocationOn, null, tint = CNeon, modifier = Modifier.align(Alignment.Center).padding(bottom = 40.dp).size(40.dp))
                    Text("© OpenStreetMap", color = Color.DarkGray, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomEnd).background(Color(0xCCFFFFFF)).padding(horizontal = 4.dp))
                }
                Button(onClick = { val c = mapView.mapCenter; onPick(c.latitude, c.longitude) }, colors = ButtonDefaults.buttonColors(containerColor = CNeon),
                    modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text(tr("เลือกตำแหน่งนี้"), fontSize = 16.sp) }
            }
        }
    }
}

// ---------------- video in the feed ----------------

/** Video cover with a play button; plays inside the app. */
@Composable
fun PostVideoThumb(p: FeedPost, modifier: Modifier) {
    var play by remember { mutableStateOf(false) }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black).clickable { play = true }, contentAlignment = Alignment.Center) {
        if (p.videoPosterUrl.isNotBlank()) AsyncImage(model = p.videoPosterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0x73000000)).border(2.dp, Color(0xE6FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, tr("เล่นวิดีโอ"), tint = PureWhite, modifier = Modifier.size(26.dp))
        }
    }
    if (play) VideoPlayerDialog(p.videoUrl) { play = false }
}

@Composable
fun VideoPlayerDialog(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            var view by remember { mutableStateOf<VideoView?>(null) }
            AndroidView(factory = { c ->
                VideoView(c).apply {
                    setMediaController(MediaController(c).also { it.setAnchorView(this) })
                    setVideoURI(Uri.parse(url))
                    setOnPreparedListener { start() }
                    view = this
                }
            }, modifier = Modifier.align(Alignment.Center).fillMaxWidth())
            DisposableEffect(Unit) { onDispose { view?.stopPlayback() } }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(8.dp)) {
                Icon(Icons.Filled.Close, tr("ปิด"), tint = PureWhite)
            }
        }
    }
}
