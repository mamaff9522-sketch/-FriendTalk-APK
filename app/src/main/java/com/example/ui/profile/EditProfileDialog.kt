package com.example.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import com.example.ui.components.ft.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.Gender
import com.example.model.User
import com.example.social.MyProfile
import com.example.ui.components.dialogNavPadding
import com.example.ui.components.tr
import com.example.ui.theme.*
import kotlinx.coroutines.launch

private val PNeon = Color(0xFFFF2FA8)
private val PViolet = Color(0xFF9B3DFF)

/**
 * Full-screen "แก้ไขโปรไฟล์": cover + avatar (upload now), name, bio, gender, province, occupation, what I'm looking for, interests.
 * Saved to the server (PUT /me/profile) — [onSave] only refreshes the local screen state.
 */
@Composable
fun EditProfileDialog(
    currentUser: User,
    onDismiss: () -> Unit,
    onSave: (displayName: String, bio: String, age: Int, gender: Gender, province: String, interests: List<String>) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { MyProfile.start() }
    val me = MyProfile.me
    val autoBio = currentUser.bio.startsWith("สมาชิก FriendTalk (UID")
    var displayName by remember(me != null) { mutableStateOf(me?.displayName?.ifBlank { null } ?: currentUser.displayName) }
    var bio by remember(me != null) { mutableStateOf(me?.bio ?: if (autoBio) "" else currentUser.bio) }
    var gender by remember(me != null) { mutableStateOf(me?.gender ?: "") }
    var province by remember(me != null) { mutableStateOf(me?.province?.ifBlank { null } ?: currentUser.location.province) }
    var occupation by remember(me != null) { mutableStateOf(me?.occupation ?: "") }
    val looking = remember(me != null) { mutableStateListOf<String>().apply { addAll(me?.lookingFor ?: emptyList()) } }
    var busy by remember { mutableStateOf(false) }
    var photoBusy by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("") }
    var provinceMenu by remember { mutableStateOf(false) }
    var editInterests by remember { mutableStateOf(false) }
    var photoKind by remember { mutableStateOf("avatar") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { photoBusy = photoKind; scope.launch { msg = MyProfile.setPhoto(ctx, uri, photoKind) ?: ""; photoBusy = null } }
    }
    fun pick(kind: String) { photoKind = kind; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    if (editInterests) com.example.ui.social.InterestsEditorDialog(onDismiss = { editInterests = false })

    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = AppThemeMode.bg) {
            Column(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding().imePadding()) {
                // top bar: ✕  แก้ไขโปรไฟล์  [บันทึก]
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (!busy) onDismiss() }) { Icon(Icons.Filled.Close, tr("ปิด"), tint = AppThemeMode.text) }
                    Text(tr("แก้ไขโปรไฟล์"), color = AppThemeMode.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    val canSave = displayName.isNotBlank() && !busy
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (canSave) Brush.horizontalGradient(listOf(PNeon, PViolet)) else Brush.horizontalGradient(listOf(AppThemeMode.skeleton, AppThemeMode.skeleton)))
                        .clickable(enabled = canSave) {
                            busy = true; msg = ""
                            scope.launch {
                                val err = MyProfile.save(displayName, bio, gender, province, occupation, looking.toList())
                                busy = false
                                if (err == null) {
                                    onSave(displayName.trim(), bio.trim(), currentUser.age, when (gender) { "male" -> Gender.MALE; "female" -> Gender.FEMALE; else -> Gender.OTHER }, province.trim(), me?.interests ?: currentUser.interests)
                                    android.widget.Toast.makeText(ctx, MyProfile.lastNote ?: tr("บันทึกโปรไฟล์แล้ว"), android.widget.Toast.LENGTH_LONG).show()
                                    onDismiss()
                                } else msg = err
                            }
                        }.padding(horizontal = 18.dp, vertical = 8.dp)) {
                        Text(if (busy) tr("กำลังบันทึก…") else tr("บันทึก"), color = if (canSave || busy) PureWhite else AppThemeMode.muted, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                HorizontalDivider(color = AppThemeMode.divider)

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    // cover + avatar, both tappable
                    Box(Modifier.fillMaxWidth().height(210.dp)) {
                        Box(Modifier.fillMaxWidth().height(160.dp).background(Brush.linearGradient(listOf(Color(0xFF5B23A0), PNeon))).clickable(enabled = photoBusy == null) { pick("cover") }) {
                            val cover = me?.cover.orEmpty()
                            if (cover.isNotBlank()) AsyncImage(model = cover, contentDescription = tr("รูปปก"), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            PhotoBadge(tr("เปลี่ยนรูปปก"), photoBusy == "cover", Modifier.align(Alignment.BottomEnd).padding(10.dp))
                        }
                        Box(Modifier.align(Alignment.BottomStart).padding(start = 16.dp).size(104.dp).clip(CircleShape).background(AppThemeMode.bg).padding(4.dp)
                            .clip(CircleShape).clickable(enabled = photoBusy == null) { pick("avatar") }) {
                            com.example.ui.social.Avatar(me?.avatar?.ifBlank { null } ?: currentUser.avatar, displayName, 96)
                            Box(Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(PNeon), contentAlignment = Alignment.Center) {
                                if (photoBusy == "avatar") CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                else Icon(Icons.Filled.CameraAlt, tr("เปลี่ยนรูปโปรไฟล์"), tint = PureWhite, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

                    Section(tr("ข้อมูลพื้นฐาน")) {
                        Field(tr("ชื่อที่แสดง"), displayName, { displayName = it.take(50) }, counter = "${displayName.length}/50")
                        Field(tr("แนะนำตัว"), bio, { bio = it.take(300) }, hint = tr("เล่าเรื่องตัวเองสั้น ๆ ให้เพื่อนใหม่รู้จัก"), minLines = 3, counter = "${bio.length}/300")
                    }
                    Section(tr("เกี่ยวกับฉัน")) {
                        Label(tr("เพศ"))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("male" to tr("ชาย"), "female" to tr("หญิง"), "other" to tr("อื่น ๆ"), "" to tr("ไม่ระบุ")).forEach { (k, l) -> Pill(l, gender == k) { gender = k } }
                        }
                        Spacer(Modifier.height(12.dp))
                        Label(tr("จังหวัด"))
                        Box {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppThemeMode.field).border(1.dp, AppThemeMode.divider, RoundedCornerShape(14.dp))
                                .clickable { provinceMenu = true }.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("📍 " + province.ifBlank { tr("เลือกจังหวัด") }, color = if (province.isBlank()) AppThemeMode.fieldHint else AppThemeMode.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Text("▾", color = AppThemeMode.muted)
                            }
                            DropdownMenu(expanded = provinceMenu, onDismissRequest = { provinceMenu = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                                DropdownMenuItem(text = { Text(tr("ไม่ระบุ")) }, onClick = { province = ""; provinceMenu = false })
                                com.example.ui.feed.THAI_PROVINCES.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { province = p; provinceMenu = false }) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Field(tr("อาชีพ / การศึกษา"), occupation, { occupation = it.take(60) }, hint = tr("เช่น นักศึกษา, พนักงานออฟฟิศ, ฟรีแลนซ์"))
                    }
                    Section(tr("มองหา")) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MyProfile.LOOKING_FOR.forEach { (k, l) -> Pill(tr(l), k in looking) { if (k in looking) looking.remove(k) else looking.add(k) } }
                        }
                    }
                    Section(tr("ความสนใจ")) {
                        val ints = me?.interests ?: currentUser.interests
                        if (ints.isEmpty()) Text(tr("ยังไม่ได้เลือกความสนใจ"), color = AppThemeMode.muted, fontSize = 13.sp)
                        else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { ints.forEach { Pill(it, true) {} } }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { editInterests = true }, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, PNeon)) { Text(tr("🧩 แก้ไขความสนใจ"), color = PNeon) }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun PhotoBadge(label: String, busy: Boolean, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (busy) CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        else Icon(Icons.Filled.CameraAlt, null, tint = PureWhite, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp)); Text(label, color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).clip(RoundedCornerShape(18.dp)).background(AppThemeMode.cardSolid)
        .border(1.dp, AppThemeMode.divider, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Text(title, color = AppThemeMode.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Label(t: String) { Text(t, color = AppThemeMode.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)) }

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, hint: String = "", minLines: Int = 1, counter: String? = null) {
    Label(label)
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), minLines = minLines, maxLines = maxOf(minLines, 6),
        placeholder = { if (hint.isNotBlank()) Text(hint, color = AppThemeMode.fieldHint) },
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AppThemeMode.text, unfocusedTextColor = AppThemeMode.text, focusedBorderColor = PNeon,
            unfocusedBorderColor = AppThemeMode.divider, focusedContainerColor = AppThemeMode.field, unfocusedContainerColor = AppThemeMode.field, cursorColor = PNeon))
    if (counter != null) Text(counter, color = AppThemeMode.muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 2.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) Brush.horizontalGradient(listOf(PNeon, PViolet)) else Brush.horizontalGradient(listOf(AppThemeMode.field, AppThemeMode.field)))
        .border(1.dp, if (selected) Color.Transparent else AppThemeMode.divider, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, color = if (selected) PureWhite else AppThemeMode.text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
