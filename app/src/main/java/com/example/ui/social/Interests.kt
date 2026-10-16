package com.example.ui.social

import com.example.ui.components.dialogNavPadding

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.example.ui.components.ft.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.social.FullProfile
import com.example.social.InterestsRepo
import com.example.social.Similarity
import com.example.social.SocialRepo
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipsFlow(items: List<String>, highlight: Set<String> = emptySet()) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { t ->
            val hit = highlight.contains(t.lowercase())
            Surface(shape = RoundedCornerShape(12.dp), color = if (hit) Pink600 else Slate700) {
                Text((if (hit) "✓ " else "") + t, color = White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

/** "ชอบอาหารญี่ปุ่นเหมือนกัน" style shared chips grouped by category. */
@Composable
fun SharedInterests(sim: Similarity?, title: Boolean = true) {
    if (sim == null) return
    LaunchedEffect(Unit) { InterestsRepo.config() }
    Column {
        if (title) Text("เข้ากันได้ ${sim.percent}%", color = Pink400, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (sim.shared.isEmpty()) Text("ยังไม่มีความสนใจที่ตรงกัน", color = Slate400, fontSize = 11.sp)
        sim.shared.forEach { (cat, items) ->
            Text("${InterestsRepo.label(cat)}: " + items.joinToString(", ") + " เหมือนกัน", color = Slate300, fontSize = 11.sp)
        }
    }
}

/** Optional interests editor (skippable): profile and after sign-up. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestsEditorDialog(onDismiss: () -> Unit, skippable: Boolean = false) {
    val scope = rememberCoroutineScope()
    var cats by remember { mutableStateOf(InterestsRepo.categories) }
    val sel = remember { mutableStateMapOf<String, List<String>>() }
    val custom = remember { mutableStateMapOf<String, String>() }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        cats = InterestsRepo.config()
        FirebaseAuth.getInstance().currentUser?.uid?.let { InterestsRepo.profile(it)?.interests?.forEach { (k, v) -> sel[k] = v } }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding().imePadding(), color = Slate950) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ความสนใจของฉัน", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(if (skippable) "ข้าม" else "ปิด", color = Slate300) }
                }
                Text("เลือกได้หมวดละไม่เกิน 10 ใช้จับคู่คนที่คล้ายกับคุณ (ไม่บังคับ)", color = Slate400, fontSize = 12.sp)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (cats.isEmpty()) CircularProgressIndicator(color = Pink500)
                    cats.forEach { c ->
                        val cur = sel[c.key].orEmpty()
                        Text(c.label, color = White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (c.options + cur.filter { it !in c.options }).forEach { o ->
                                FilterChip(selected = o in cur, onClick = {
                                    sel[c.key] = if (o in cur) cur - o else if (cur.size < 10) cur + o else cur
                                }, label = { Text(o, fontSize = 11.sp) })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = custom[c.key].orEmpty(), onValueChange = { if (it.length <= 30) custom[c.key] = it },
                                singleLine = true, modifier = Modifier.weight(1f), placeholder = { Text("เพิ่มเอง…", color = Slate400, fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                            TextButton(onClick = {
                                val t = custom[c.key].orEmpty().trim()
                                if (t.isNotBlank() && t !in cur && cur.size < 10) sel[c.key] = cur + t
                                custom[c.key] = ""
                            }) { Text("เพิ่ม", color = Pink400) }
                        }
                    }
                }
                if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp)
                Button(onClick = { scope.launch { if (InterestsRepo.save(sel.toMap())) onDismiss() else msg = "บันทึกไม่สำเร็จ (ยืนยันอีเมลแล้วหรือยัง?)" } },
                    modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("บันทึก") }
            }
        }
    }
}

/** Another user's profile: banner + ringed photo, compatibility gauge, bio, interests by category (shared ones highlighted), actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserProfileDialog(uid: String, distance: String? = null, onDismiss: () -> Unit) {
    // Own avatar anywhere -> own Profile page (logout, my posts) instead of the public card.
    if (uid == com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid) { LaunchedEffect(uid) { ProfileNav.openOwn(); onDismiss() }; return }
    val scope = rememberCoroutineScope()
    var fp by remember { mutableStateOf<FullProfile?>(null) }
    var sent by remember { mutableStateOf<InterestsRepo.FriendResult?>(null) }
    var msg by remember { mutableStateOf("") }
    var showPosts by remember { mutableStateOf(false) }
    var repU by remember { mutableStateOf(false) }
    LaunchedEffect(uid) { InterestsRepo.config(); fp = InterestsRepo.profile(uid) }
    val pink = Color(0xFFFF2FA8); val violet = Color(0xFF8B3DFF); val indigo = Color(0xFF4F46E5)
    val ink = com.example.ui.theme.AppThemeMode.text; val soft = com.example.ui.theme.AppThemeMode.muted
    val dark = com.example.ui.theme.AppThemeMode.dark
    val glass = if (dark) Color(0xFF1C1645) else Color(0xFFF4EAFF)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = com.example.ui.theme.AppThemeMode.dialog, shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.86f)) {
            val f = fp
            if (f == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = pink) }; return@Surface }
            val p = f.profile
            val sim = f.similarity
            val shared = sim?.shared.orEmpty()
            val sharedCount = shared.values.sumOf { it.size }
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    // banner + photo
                    Box(Modifier.fillMaxWidth().height(178.dp)) {
                        Box(Modifier.fillMaxWidth().height(124.dp).background(Brush.linearGradient(listOf(pink, violet, indigo))))
                        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(124.dp)) {
                            drawCircle(Color.White.copy(alpha = 0.12f), size.width * 0.28f, androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.1f))
                            drawCircle(Color.White.copy(alpha = 0.10f), size.width * 0.18f, androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.95f))
                            drawCircle(Color.White.copy(alpha = 0.18f), 5f, androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height * 0.3f))
                            drawCircle(Color.White.copy(alpha = 0.25f), 3f, androidx.compose.ui.geometry.Offset(size.width * 0.7f, size.height * 0.55f))
                        }
                        Text("✦ ✧ ✦", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(16.dp))
                        Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                            .clip(CircleShape).background(Color(0x33000000)).clickable(onClick = onDismiss).padding(horizontal = 11.dp, vertical = 6.dp))
                        Box(Modifier.align(Alignment.BottomCenter).size(112.dp).clip(CircleShape).background(Brush.sweepGradient(listOf(pink, Color(0xFFFFC94D), violet, pink))).padding(4.dp)
                            .clip(CircleShape).background(com.example.ui.theme.AppThemeMode.dialog).padding(3.dp), contentAlignment = Alignment.Center) {
                            Avatar(p.avatar, p.displayName, 98)
                        }
                    }
                    Text(p.displayName + (p.age?.let { ", $it" } ?: ""), color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp, start = 20.dp, end = 20.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (p.username.isNotBlank()) Text("@${p.username}", color = soft, fontSize = 12.sp)
                    distance?.takeIf { it.isNotBlank() }?.let {
                        Text("📍 ห่างประมาณ $it", color = pink, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)
                            .clip(RoundedCornerShape(12.dp)).background(pink.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                    // compatibility
                    sim?.let { s ->
                        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp).clip(RoundedCornerShape(20.dp)).background(glass).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            val pct = s.percent.coerceIn(0, 100)
                            Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
                                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                    val st = androidx.compose.ui.graphics.drawscope.Stroke(7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                    drawArc(soft.copy(alpha = 0.2f), 0f, 360f, false, style = st)
                                    drawArc(Brush.sweepGradient(listOf(pink, violet, pink)), -90f, 360f * pct / 100f, false, style = st)
                                }
                                Text("$pct%", color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("เข้ากันได้", color = soft, fontSize = 12.sp)
                                Text(when { pct >= 80 -> "คู่ซี้ในอนาคต 💞"; pct >= 50 -> "คุยกันถูกคอแน่ ✨"; pct >= 25 -> "มีเรื่องคุยกันได้ 😊"; else -> "ลองทักไปทำความรู้จัก 👋" },
                                    color = ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(if (sharedCount > 0) "ชอบเหมือนกัน $sharedCount อย่าง" else "ยังไม่มีความสนใจที่ตรงกัน", color = pink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    // bio
                    if (p.bio.isNotBlank()) Box(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp)) {
                        Text("“", color = pink.copy(alpha = 0.35f), fontSize = 44.sp, fontWeight = FontWeight.Black, modifier = Modifier.offset(x = (-2).dp, y = (-14).dp))
                        Text(p.bio, color = ink, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 22.dp, top = 4.dp))
                    }
                    // interests
                    if (f.interests.isNotEmpty()) Text("ความสนใจ", color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
                    f.interests.forEach { (cat, items) ->
                        val label = InterestsRepo.label(cat)
                        val hit = shared[cat].orEmpty().map { it.lowercase() }.toSet()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(glass).padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(catEmoji(label), fontSize = 16.sp); Spacer(Modifier.width(6.dp))
                                Text(label, color = ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (hit.isNotEmpty()) Text("ตรงกัน ${hit.size}", color = pink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items.forEach { t ->
                                    val on = t.lowercase() in hit
                                    Text((if (on) "✓ " else "") + t, color = if (on) Color.White else ink, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                                            .then(if (on) Modifier.background(Brush.horizontalGradient(listOf(pink, violet)))
                                                  else Modifier.background(com.example.ui.theme.AppThemeMode.dialog).border(1.dp, com.example.ui.theme.AppThemeMode.divider, RoundedCornerShape(16.dp)))
                                            .padding(horizontal = 12.dp, vertical = 6.dp))
                                }
                            }
                        }
                    }
                    if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, start = 18.dp, end = 18.dp))
                    // small actions
                    Row(Modifier.padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SmallAction("📝", "โพสต์", ink, glass) { showPosts = true }
                        GiftToUserButton(uid, compact = true)
                        SmallAction("🚩", "รายงาน", soft, glass) { repU = true }
                    }
                }
                // main actions pinned at the bottom
                Row(Modifier.fillMaxWidth().background(com.example.ui.theme.AppThemeMode.dialog).padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FriendButton(uid, onSent = { sent = it }, onError = { msg = it }) // เพิ่มเพื่อน → ส่งคำขอแล้ว → เพื่อน
                    Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp)).background(Brush.horizontalGradient(listOf(pink, violet)))
                        .clickable { scope.launch {
                            val cid = SocialRepo.openChatWith(uid)
                            if (cid != null) { onDismiss(); SocialRepo.openChat.value = cid } else msg = SocialRepo.lastOpenError.ifBlank { "เปิดแชทไม่สำเร็จ" }
                        } }, contentAlignment = Alignment.Center) {
                        Text("💬 เริ่มแชท", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
    if (repU) ReportDialog("user", uid, uid) { repU = false }
    if (showPosts) AuthorFeedDialog(uid, fp?.profile?.displayName ?: "") { showPosts = false }
    sent?.let { r -> FriendSentDialog(r) { sent = null } }
}

@Composable
private fun SmallAction(icon: String, label: String, color: Color, bg: Color, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 13.sp); Spacer(Modifier.width(4.dp)); Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun catEmoji(label: String): String = when {
    "เวลา" in label -> "⏰"; "อดิเรก" in label -> "🎨"; "ภาษา" in label -> "🗣️"; "ไลฟ์" in label -> "🌿"; "มองหา" in label -> "🔎"
    "นิสัย" in label -> "💫"; "อาหาร" in label -> "🍜"; "เพลง" in label || "ดนตรี" in label -> "🎵"; "กีฬา" in label -> "⚽"; "เที่ยว" in label -> "✈️"
    "หนัง" in label -> "🎬"; "เกม" in label -> "🎮"; "สัตว์" in label -> "🐾"; "งาน" in label || "อาชีพ" in label -> "💼"; else -> "✨"
}

@Composable
fun FriendSentDialog(r: InterestsRepo.FriendResult, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(when (r.status) { "friends" -> "เป็นเพื่อนกันแล้ว! 🎉"; "already_friends" -> "เป็นเพื่อนกันอยู่แล้ว"; else -> "ส่งคำขอเป็นเพื่อนแล้ว ✓" }) },
        text = { SharedInterests(r.similarity) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("ตกลง") } })
}

/** Discover tab: people similar to you (personality match, quota-limited by the server). */
@Composable
fun PersonalityMatchView() {
    var res by remember { mutableStateOf<InterestsRepo.PersonalityResult?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun load() { InterestsRepo.config(); res = InterestsRepo.personalityMatches(); loaded = true }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("คนที่คล้ายคุณ", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { edit = true }) { Text("แก้ความสนใจ", color = Pink400) }
        }
        val r = res
        when {
            !loaded -> CircularProgressIndicator(color = Pink500)
            r == null -> Text("โหลดไม่สำเร็จ (โควตาอาจหมด)", color = Amber400, fontSize = 12.sp)
            r.needInterests -> Button(onClick = { edit = true }, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("ตั้งค่าความสนใจก่อน") }
            r.users.isEmpty() -> Text("ยังไม่พบคนที่ความสนใจตรงกัน", color = Slate400, fontSize = 13.sp)
            else -> r.users.forEach { u ->
                Surface(shape = RoundedCornerShape(14.dp), color = Slate800, onClick = { open = u.profile.uid }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(u.profile.avatar, u.profile.displayName, 42); Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.profile.displayName, color = White, fontWeight = FontWeight.Bold)
                            SharedInterests(u.similarity, title = false)
                        }
                        Text("${u.similarity?.percent ?: 0}%", color = Emerald400, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (edit) InterestsEditorDialog(onDismiss = { edit = false; scope.launch { load() } })
    open?.let { UserProfileDialog(it) { open = null } }
}

/** Optional interests step shown once after the profile is first created (sign-up). */
object InterestsPrompt { val show = kotlinx.coroutines.flow.MutableStateFlow(false) }

/** Set by MainActivity: navigates to the user's own Profile page. */
object ProfileNav { var openOwn: () -> Unit = {} }
