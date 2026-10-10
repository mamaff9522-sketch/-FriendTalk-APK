package com.example.ui.social

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Slate950) {
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

/** Another user's profile: interests by category, shared ones highlighted, similarity %, add friend / chat. */
@Composable
fun UserProfileDialog(uid: String, distance: String? = null, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var fp by remember { mutableStateOf<FullProfile?>(null) }
    var sent by remember { mutableStateOf<InterestsRepo.FriendResult?>(null) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(uid) { InterestsRepo.config(); fp = InterestsRepo.profile(uid) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Slate900, modifier = Modifier.heightIn(max = 640.dp)) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                val f = fp
                if (f == null) { CircularProgressIndicator(color = Pink500); TextButton(onClick = onDismiss) { Text("ปิด") }; return@Column }
                val p = f.profile
                Avatar(p.avatar, p.displayName, 84)
                Text(p.displayName + (p.age?.let { ", $it" } ?: ""), color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                distance?.let { Text("ห่างประมาณ $it", color = Pink400, fontSize = 12.sp) }
                f.similarity?.let { Text("เข้ากันได้ ${it.percent}%", color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                if (p.bio.isNotBlank()) Text(p.bio, color = Slate300, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                val shared = f.similarity?.shared.orEmpty()
                f.interests.forEach { (cat, items) ->
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(InterestsRepo.label(cat), color = Slate400, fontSize = 11.sp)
                        ChipsFlow(items, shared[cat].orEmpty().map { it.lowercase() }.toSet())
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (uid != FirebaseAuth.getInstance().currentUser?.uid) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch {
                        val r = InterestsRepo.sendFriendRequest(uid)
                        if (r == null) msg = "ส่งคำขอไม่สำเร็จ" else sent = r
                    } }, colors = ButtonDefaults.buttonColors(containerColor = Purple600)) { Text("เพิ่มเพื่อน") }
                    Button(onClick = { scope.launch {
                        val cid = SocialRepo.openChatWith(uid)
                        if (cid != null) { onDismiss(); SocialRepo.openChat.value = cid } else msg = "เปิดแชทไม่สำเร็จ"
                    } }, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("เริ่มแชท") }
                }
                if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                TextButton(onClick = onDismiss) { Text("ปิด", color = Slate400) }
            }
        }
    }
    sent?.let { r -> FriendSentDialog(r) { sent = null } }
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
