package com.example.ui.profile

import com.example.ui.components.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import com.example.ui.components.ft.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Gender
import com.example.model.User
import com.example.ui.theme.*

@Composable
fun ProfileTab(
    currentUser: User,
    onEditProfile: () -> Unit,
    onOpenCreatorStudio: () -> Unit,
    onOpenCoins: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAccount: () -> Unit = {},
    onOpenCompanionApply: () -> Unit,
    onLogout: () -> Unit = {}
) {
    var myPosts by remember { mutableStateOf(false) }
    if (myPosts) com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.let { u -> com.example.ui.social.AuthorFeedDialog(u.uid, tr("ฉัน")) { myPosts = false } }
    // Real profile from users/{uid}; tapping the cover / avatar uploads a new photo right here.
    LaunchedEffect(Unit) { com.example.social.MyProfile.start() }
    val me = com.example.social.MyProfile.me
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var photoKind by remember { mutableStateOf("avatar") }
    var photoBusy by remember { mutableStateOf<String?>(null) }
    var photoMsg by remember { mutableStateOf("") }
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { photoBusy = photoKind; photoMsg = ""; scope.launch { photoMsg = com.example.social.MyProfile.setPhoto(ctx, uri, photoKind) ?: ""; photoBusy = null } }
    }
    fun pickPhoto(kind: String) { if (photoBusy != null) return; photoKind = kind
        photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val shownName = me?.displayName?.ifBlank { null } ?: currentUser.displayName
    val shownAvatar = me?.avatar?.ifBlank { null } ?: currentUser.avatar
    val shownBio = me?.bio ?: currentUser.bio.takeUnless { it.startsWith("สมาชิก FriendTalk (UID") }.orEmpty()
    var confirmLogout by remember { mutableStateOf(false) }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text(tr("ออกจากระบบ?")) },
        text = { Text(tr("คุณต้องการออกจากบัญชีนี้ใช่ไหม")) },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text(tr("ออกจากระบบ"), color = Rose500) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(tr("ยกเลิก")) } })
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Cover Photo & Avatar
        item {
            Box(Modifier.fillMaxWidth().height(210.dp)) {
                Box(Modifier.fillMaxWidth().height(160.dp).background(Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF5B23A0), Pink500))).clickable { pickPhoto("cover") }) {
                    val cover = me?.cover.orEmpty()
                    if (cover.isNotBlank()) AsyncImage(model = cover, contentDescription = tr("รูปปก"), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Row(Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color(0x99000000)).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (photoBusy == "cover") CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        else Icon(Icons.Filled.CameraAlt, null, tint = PureWhite, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp)); Text(tr("แก้ไขรูปปก"), color = PureWhite, fontSize = 12.sp)
                    }
                }
                Box(Modifier.align(Alignment.BottomStart).padding(start = 16.dp).size(104.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Amber400, Pink500, Purple500))).padding(3.dp)
                    .clip(CircleShape).background(Slate950).clickable { pickPhoto("avatar") }) {
                    com.example.ui.social.Avatar(shownAvatar, shownName, 98)
                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(28.dp).clip(CircleShape).background(Pink500), contentAlignment = Alignment.Center) {
                        if (photoBusy == "avatar") CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        else Icon(Icons.Filled.CameraAlt, tr("เปลี่ยนรูปโปรไฟล์"), tint = PureWhite, modifier = Modifier.size(15.dp))
                    }
                }
            }
            if (photoMsg.isNotBlank()) Text(photoMsg, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }

        // Profile Info
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = shownName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = White
                            )
                            if (currentUser.isVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = Cyan400, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(text = "@${currentUser.username}", fontSize = 12.sp, color = Slate400)
                    }

                    OutlinedButton(
                        onClick = onEditProfile,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(tr("แก้ไขโปรไฟล์"), fontSize = 12.sp, color = Slate200)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(text = shownBio.ifBlank { tr("ยังไม่มีคำแนะนำตัว — แตะ 'แก้ไขโปรไฟล์' เพื่อเพิ่ม") }, fontSize = 13.sp, color = if (shownBio.isBlank()) Slate400 else Slate200)
                // 📍 province · 💼 occupation · looking for
                val chips = listOfNotNull(me?.province?.ifBlank { null }?.let { "📍 $it" }, me?.occupation?.ifBlank { null }?.let { "💼 $it" }) +
                    (me?.lookingFor ?: emptyList()).mapNotNull { com.example.social.MyProfile.LOOKING_FOR[it] }
                if (chips.isNotEmpty()) Row(Modifier.padding(top = 8.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { c -> Text(tr(c), color = Slate200, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Slate800).padding(horizontal = 10.dp, vertical = 4.dp)) }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats row
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Slate800,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${currentUser.followingCount}", fontWeight = FontWeight.Bold, color = White, fontSize = 15.sp)
                            Text(tr("กำลังติดตาม"), fontSize = 11.sp, color = Slate400)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${currentUser.followersCount}", fontWeight = FontWeight.Bold, color = White, fontSize = 15.sp)
                            Text(tr("ผู้ติดตาม"), fontSize = 11.sp, color = Slate400)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${currentUser.likesCount}", fontWeight = FontWeight.Bold, color = White, fontSize = 15.sp)
                            Text(tr("ถูกใจทั้งหมด"), fontSize = 11.sp, color = Slate400)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Action Cards
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Slate800,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🪙", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tr("ยอดเหรียญ & เพชร"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = White)
                                Text("${com.example.social.Wallet.coins} เหรียญ • ${com.example.social.Wallet.diamonds} เพชร", fontSize = 12.sp, color = Amber400)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Slate400)
                        }
                    }

                    // 🎁 watch a short ad -> extra quota (opens the existing Ad Room in browse mode)
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFFFB547), androidx.compose.ui.graphics.Color(0xFFFF4F8B), androidx.compose.ui.graphics.Color(0xFF9B3DFF))))
                        .clickable { com.example.ads.AdRoom.open.value = "" }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.example.R.drawable.art_gift), null, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tr("รับสิทธิ์ฟรี — ดูโฆษณาสั้น ๆ"), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = PureWhite)
                                Text(tr("แลกโควตาเพิ่ม เช่น แชท ปัดหาเพื่อน เรดาร์"), fontSize = 12.sp, color = PureWhite.copy(alpha = 0.9f))
                            }
                            Text(tr("ดูเลย ›"), color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color(0x33FFFFFF)).padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }

                    if (currentUser.age >= 18) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Slate800,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpenCompanionApply() }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "💖", fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tr("สมัครเป็นผู้ให้บริการเพื่อนคุย (18+)"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Rose500)
                                    Text(tr("สร้างรายได้จากการแชทกับผู้ใช้ (สมัครผ่านระบบจริง)"), fontSize = 12.sp, color = Slate400)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Slate400)
                            }
                        }
                    }

                    var showWithdraw by remember { mutableStateOf(false) }
                    if (showWithdraw) com.example.ui.components.UiPage("wallet") { com.example.ui.wallet.WithdrawDialog(isStaff = currentUser.role != com.example.model.UserRole.USER) { showWithdraw = false } }
                    OutlinedButton(onClick = { showWithdraw = true }, modifier = Modifier.fillMaxWidth()) { Text("💸 ถอนเงิน") }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Slate800,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenCreatorStudio() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🎨", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tr("Creator Studio (สตูดิโอครีเอเตอร์)"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = White)
                                Text(tr("แดชบอร์ดสตรีมเมอร์และสถิติรายได้"), fontSize = 12.sp, color = Slate400)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Slate400)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Slate800,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpenAccount() }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🔗", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tr("บัญชี & การเชื่อมต่อ"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = White)
                                Text(tr("อีเมล, Google และการเชื่อมบัญชี"), fontSize = 12.sp, color = Slate400)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Slate400)
                        }
                    Spacer(modifier = Modifier.height(10.dp))
                    run {
                        var rules by remember { mutableStateOf(false) }
                        if (rules) com.example.ui.social.ChatRulesDialog { rules = false }
                        Surface(shape = RoundedCornerShape(14.dp), color = Slate900, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { rules = true }) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🛡", fontSize = 22.sp); Spacer(Modifier.width(12.dp))
                                Text(tr("กติกาการแชทและเพื่อนคุย"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = White, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Slate400)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    run {
                        val act = androidx.compose.ui.platform.LocalContext.current
                        Surface(shape = RoundedCornerShape(14.dp), color = Slate900, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🌐", fontSize = 22.sp); Spacer(Modifier.width(12.dp))
                                Text("ภาษา / Language", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = White, modifier = Modifier.weight(1f))
                                listOf("th" to "ไทย", "en" to "English").forEach { (k, l) ->
                                    FilterChip(selected = com.example.ui.components.I18n.lang == k, onClick = {
                                        if (com.example.ui.components.I18n.lang != k) { com.example.ui.components.I18n.set(act, k); (act as? android.app.Activity)?.recreate() }
                                    }, label = { Text(l, fontSize = 12.sp) }, modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    ThemeModeRow()
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Slate800,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenPermissions() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "⚙️", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tr("ตั้งค่าความเป็นส่วนตัวและสิทธิ์"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = White)
                                Text(tr("จัดการการเข้าถึงกล้อง ไมโครโฟน และตำแหน่ง"), fontSize = 12.sp, color = Slate400)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Slate400)
                        }
                    }
                    Surface(shape = RoundedCornerShape(14.dp), color = Slate800, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { myPosts = true }) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📝", fontSize = 22.sp); Spacer(Modifier.width(12.dp))
                            Text(tr("โพสต์ของฉัน"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = White, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Slate400)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // ออกจากระบบ — always the last item on the page
                    OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Rose500)) {
                        Text(tr("🚪 ออกจากระบบ"), color = Rose500, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


/** Settings row: โหมดมืด on/off (on = Home look on every page, off = โหมดปกติ, the 'หาเพื่อน' look). Saved on this device. */
@Composable
private fun ThemeModeRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dark = com.example.ui.theme.AppThemeMode.dark
    Surface(shape = RoundedCornerShape(14.dp), color = Slate900, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .clickable { com.example.ui.theme.AppThemeMode.set(ctx, !dark) }) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (dark) "🌙" else "☀️", fontSize = 22.sp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("โหมดมืด"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = White)
                Text(if (dark) tr("เปิดอยู่ · ทุกหน้าใช้พื้นหลังสีเข้ม") else tr("ปิดอยู่ · ใช้โหมดปกติ (พื้นหลังสีอ่อน)"), fontSize = 11.sp, color = Slate400)
            }
            Switch(checked = dark, onCheckedChange = { com.example.ui.theme.AppThemeMode.set(ctx, it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Pink500, checkedThumbColor = PureWhite))
        }
    }
}
