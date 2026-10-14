package com.example.ui.social

import com.example.ui.components.dialogNavPadding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.example.ui.components.ft.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.network.BrainApi
import com.example.social.Wallet
import com.example.ui.components.tr
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

private suspend fun api(m: String, p: String, b: JSONObject? = null): BrainApi.ApiResponse {
    val u = FirebaseAuth.getInstance().currentUser ?: return BrainApi.ApiResponse(401, null)
    return BrainApi.call(u, m, p, b)
}
private fun errOf(r: BrainApi.ApiResponse) = r.json?.optString("error")?.ifBlank { null } ?: "ไม่สำเร็จ (${r.code})"
private fun JSONArray?.strings() = this?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
private fun ts(t: Long) = if (t <= 0) "-" else java.text.SimpleDateFormat("d MMM yy HH:mm", java.util.Locale("th", "TH")).format(java.util.Date(t))
private val tfColors @Composable get() = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White)

@Composable
private fun FullDialog(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding().imePadding(), color = Slate950) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text(tr("ปิด") + " ✕") }
                }
                content()
            }
        }
    }
}

const val AD_REVENUE_NOTE = "รายได้จากโฆษณามีเฉพาะครีเอเตอร์ที่ได้รับอนุมัติและผ่านเกณฑ์เท่านั้น ผู้ใช้ทั่วไปไม่มีรายได้จากโฆษณา (เป็นผู้ชมโฆษณาเท่านั้น)"
private val CR_CAT_TH = mapOf("music" to "ดนตรี", "singing" to "ร้องเพลง", "talk" to "พูดคุย", "gaming" to "เกม", "dance" to "เต้น", "art" to "ศิลปะ", "cooking" to "ทำอาหาร",
    "fitness" to "ฟิตเนส", "education" to "ความรู้", "lifestyle" to "ไลฟ์สไตล์", "other" to "อื่น ๆ")

/** Creator entry: approved creators -> Studio; others -> application form / status. */
@Composable
fun CreatorEntryDialog(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var st by remember { mutableStateOf<JSONObject?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { st = api("GET", "/creator/status").json ?: JSONObject() }
    val s = st
    if (s == null) { FullDialog("Creator", onClose) { CircularProgressIndicator(color = Pink500) }; return }
    if (s.optBoolean("isCreator")) {
        FullDialog("Creator Studio 🎨", onClose) {
            Surface(shape = RoundedCornerShape(14.dp), color = Slate900) { Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("💎 ${Wallet.diamonds} เพชร", color = Cyan400, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("ยอดเพชรจริงจากเซิร์ฟเวอร์ (รวมส่วนแบ่ง 80% จากของขวัญ)", color = Slate400, fontSize = 12.sp)
            } }
            Text("✅ คุณเป็นครีเอเตอร์ที่ได้รับอนุมัติ", color = Emerald400, fontWeight = FontWeight.Bold)
            Text(AD_REVENUE_NOTE, color = Slate400, fontSize = 12.sp)
            Text("ยังไม่มีระบบนับยอดวิว/ผู้ติดตาม/รายได้โฆษณาของครีเอเตอร์ และยังไม่มีการโพสต์คลิป — จะเพิ่มในเวอร์ชันถัดไป", color = Amber400, fontSize = 12.sp)
        }
        return
    }
    val app = s.optJSONObject("application")
    val rulesObj = s.optJSONObject("rules"); val rules = rulesObj?.optJSONArray("rules").strings(); val rv = rulesObj?.optInt("version") ?: 1
    val cats = s.optJSONArray("categories").strings().ifEmpty { CR_CAT_TH.keys.toList() }
    FullDialog("สมัครเป็นครีเอเตอร์", onClose) {
        if (app != null && app.optString("status") == "pending") {
            Text("⏳ ใบสมัครกำลังรอตรวจสอบ (ส่งเมื่อ ${ts(app.optLong("submittedAt"))})", color = Amber400, fontWeight = FontWeight.Bold); return@FullDialog
        }
        if (app != null && app.optString("status") == "rejected") Text("❌ ใบสมัครก่อนหน้าไม่ผ่าน: ${app.optString("reason")}\nแก้ไขแล้วส่งใหม่ได้", color = Rose500)
        var name by remember { mutableStateOf(app?.optString("displayName").orEmpty()) }
        var cat by remember { mutableStateOf(app?.optString("category")?.ifBlank { null } ?: "talk") }
        var bio by remember { mutableStateOf(app?.optString("bio").orEmpty()) }
        var links by remember { mutableStateOf(app?.optJSONArray("links").strings().joinToString("\n")) }
        var sample by remember { mutableStateOf(app?.optString("sampleUrl").orEmpty()) }
        val agreed = remember { mutableStateMapOf<Int, Boolean>() }
        var msg by remember { mutableStateOf("") }
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("ชื่อที่แสดง") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = tfColors)
        Text("หมวดหมู่", color = White, fontWeight = FontWeight.Bold)
        FlowRowCompat { cats.forEach { k -> FilterChip(selected = cat == k, onClick = { cat = k }, label = { Text(CR_CAT_TH[k] ?: k) }) } }
        OutlinedTextField(bio, { bio = it.take(300) }, label = { Text("แนะนำตัวสั้น ๆ (${bio.length}/300)") }, modifier = Modifier.fillMaxWidth(), colors = tfColors)
        OutlinedTextField(links, { links = it.take(800) }, label = { Text("ลิงก์โซเชียล (บรรทัดละ 1 ลิงก์ https://)") }, modifier = Modifier.fillMaxWidth(), colors = tfColors)
        OutlinedTextField(sample, { sample = it.take(300) }, label = { Text("ลิงก์ตัวอย่างรูป/คลิป (https://)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = tfColors)
        Text("กติกา (ต้องติ๊กยอมรับทุกข้อ)", color = White, fontWeight = FontWeight.Bold)
        Text(AD_REVENUE_NOTE, color = Slate400, fontSize = 12.sp)
        rules.forEachIndexed { i, r ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { agreed[i] = agreed[i] != true }) {
                Checkbox(checked = agreed[i] == true, onCheckedChange = { agreed[i] = it }); Text("${i + 1}. $r", color = White, fontSize = 13.sp)
            }
        }
        val all = rules.indices.all { agreed[it] == true }
        Button(enabled = all && name.isNotBlank() && bio.length >= 10, onClick = { scope.launch {
            val r = api("POST", "/creator/apply", JSONObject().put("displayName", name).put("category", cat).put("bio", bio)
                .put("links", JSONArray(links.lines().map { it.trim() }.filter { it.isNotBlank() })).put("sampleUrl", sample.trim())
                .put("adult", true).put("agree", true).put("rulesVersion", rv).put("agreedRules", JSONArray(rules.indices.toList())))
            if (r.code == 200) tick++ else msg = errOf(r)
        } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("ส่งใบสมัคร", color = PureWhite) }
        if (msg.isNotBlank()) Text(msg, color = Amber400)
    }
}

@Composable
private fun FlowRowCompat(content: @Composable () -> Unit) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

// ===================== Chat safety rules =====================
object ChatRulesState { var accepted by mutableStateOf<Boolean?>(null) }

/** Gate: shows the rules sheet with required 'ยอมรับ' before chat / เพื่อนคุย are usable. */
@Composable
fun ChatRulesGate(content: @Composable () -> Unit) {
    LaunchedEffect(Unit) { if (ChatRulesState.accepted != true) ChatRulesState.accepted = api("GET", "/chat-rules").json?.optBoolean("accepted") ?: true }
    when (ChatRulesState.accepted) {
        true -> content()
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pink500) }
        false -> ChatRulesSheet(requireAccept = true) { if (it) ChatRulesState.accepted = true }
    }
}

@Composable
fun ChatRulesSheet(requireAccept: Boolean, onDone: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var j by remember { mutableStateOf<JSONObject?>(null) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { j = api("GET", "/chat-rules").json }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🛡 " + tr("กติกาการแชทและเพื่อนคุย"), color = White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        j?.optJSONArray("rules").strings().forEachIndexed { i, r -> Text("${i + 1}. $r", color = White, fontSize = 14.sp) }
        if (requireAccept) Button(enabled = j != null, onClick = { scope.launch {
            val r = api("POST", "/chat-rules/accept", JSONObject().put("version", j?.optInt("version") ?: 1))
            if (r.code == 200) onDone(true) else msg = errOf(r)
        } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text(tr("ยอมรับ"), color = PureWhite, fontWeight = FontWeight.Bold) }
        else Button(onClick = { onDone(false) }) { Text(tr("ปิด")) }
        if (msg.isNotBlank()) Text(msg, color = Amber400)
    }
}

@Composable
fun ChatRulesDialog(onClose: () -> Unit) { Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { Surface(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding().imePadding(), color = Slate950) { ChatRulesSheet(false) { onClose() } } } }

// ===================== เพื่อนคุย =====================
data class Partner(val uid: String, val name: String, val occupation: String, val age: Int, val slots: List<String>, val hours: Map<String, String>,
                   val talkStyle: String, val personality: String, val modes: List<String>, val avg: Double, val count: Int, val photo: String = "")
private val SLOT_TH = linkedMapOf("morning" to "เช้า", "afternoon" to "บ่าย", "evening" to "เย็น", "night" to "ดึก")
private val DAY_TH = linkedMapOf("mon" to "จ.", "tue" to "อ.", "wed" to "พ.", "thu" to "พฤ.", "fri" to "ศ.", "sat" to "ส.", "sun" to "อา.")
private val MODE_TH = linkedMapOf("text" to "💬 ข้อความ", "voice" to "📞 โทรเสียง", "video" to "🎥 วิดีโอคอล")

fun stars(avg: Double): String { val full = avg.toInt(); val half = avg - full >= 0.5; return "★".repeat(full) + (if (half) "⯨" else "") + "☆".repeat(5 - full - if (half) 1 else 0) }

/** Real เพื่อนคุย list (approved partners from RTDB companions/), signup link, mode selection, paid chat. */
@Composable
fun RealCompanionScreen() {
    ChatRulesGate {
        val scope = rememberCoroutineScope()
        var list by remember { mutableStateOf<List<Partner>?>(null) }
        var signup by remember { mutableStateOf(false) }
        var pick by remember { mutableStateOf<Partner?>(null) }
        var openChat by remember { mutableStateOf<Pair<String, String>?>(null) }
        LaunchedEffect(Unit) { list = loadPartners() }
        if (signup) CompanionSignupDialog { signup = false }
        pick?.let { p -> ModeSelectDialog(p, onClose = { pick = null }) { chatId -> pick = null; openChat = chatId to p.uid } }
        openChat?.let { (cid, puid) -> com.example.ui.components.UiPage("chatroom") { RealChatRoomDialog(cid) { openChat = null; CompanionRating.maybeAsk(puid) } } }
        CompanionRating.Host()
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💗 " + tr("เพื่อนคุย"), color = White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, modifier = Modifier.weight(1f))
                    // sign-up moved to the "ฉัน" page (owner request)
                }
                Text("18+ เท่านั้น · คิดค่าบริการเป็นเพชรตามอัตราที่แอดมินกำหนด", color = Slate400, fontSize = 12.sp)
            }
            val l = list
            if (l == null) item { CircularProgressIndicator(color = Pink500) }
            else if (l.isEmpty()) item { Text("ยังไม่มีเพื่อนคุยที่ได้รับอนุมัติ", color = Slate400) }
            else items(l, key = { it.uid }) { p -> PartnerCard(p) { pick = p } }
        }
    }
}

/** Approved companions (companions/ in RTDB), best rated first. */
internal suspend fun loadPartners(): List<Partner> {
    val s = runCatching { FirebaseDatabase.getInstance("https://friendtalk-4e623-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("companions").get().await() }.getOrNull()
    return s?.children?.map { c -> Partner(c.key.orEmpty(), c.child("displayName").getValue(String::class.java).orEmpty(), c.child("occupation").getValue(String::class.java).orEmpty(),
        (c.child("age").getValue(Long::class.java) ?: 0L).toInt(), c.child("slots").children.mapNotNull { it.getValue(String::class.java) },
        c.child("hours").children.associate { (it.key ?: "") to (it.getValue(String::class.java) ?: "") },
        c.child("talkStyle").getValue(String::class.java).orEmpty(), c.child("personality").getValue(String::class.java).orEmpty(),
        c.child("modes").children.mapNotNull { it.getValue(String::class.java) }.ifEmpty { listOf("text") },
        c.child("rating/avg").getValue(Double::class.java) ?: 0.0, (c.child("rating/count").getValue(Long::class.java) ?: 0L).toInt(),
        c.child("photos").children.firstOrNull()?.getValue(String::class.java).orEmpty()) }
        ?.sortedWith(compareByDescending<Partner> { it.avg }.thenByDescending { it.count }) ?: emptyList()
}

/**
 * 'เพื่อนคุย' inside หาเพื่อน: small profile cards of approved companions in a grid.
 * Tap → the same mode picker → paid chat as before (rates set by admins, 18+ gate stays at the tile).
 */
@Composable
fun CompanionGridScreen() {
    ChatRulesGate {
        var list by remember { mutableStateOf<List<Partner>?>(null) }
        var pick by remember { mutableStateOf<Partner?>(null) }
        var openChat by remember { mutableStateOf<Pair<String, String>?>(null) }
        LaunchedEffect(Unit) { list = loadPartners() }
        pick?.let { p -> ModeSelectDialog(p, onClose = { pick = null }) { chatId -> pick = null; openChat = chatId to p.uid } }
        openChat?.let { (cid, puid) -> com.example.ui.components.UiPage("chatroom") { RealChatRoomDialog(cid) { openChat = null; CompanionRating.maybeAsk(puid) } } }
        CompanionRating.Host()
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Text("18+ เท่านั้น · คิดค่าบริการเป็นเพชรตามอัตราที่แอดมินกำหนด · สมัครเป็นเพื่อนคุยได้ที่หน้า \"ฉัน\"", color = Slate400, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
            val l = list
            when {
                l == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pink500) }
                l.isEmpty() -> Text(tr("ยังไม่มีเพื่อนคุยที่พร้อมให้บริการ"), color = Slate400, modifier = Modifier.padding(vertical = 24.dp))
                else -> androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(l.size, key = { l[it].uid }) { i -> PartnerTile(l[i]) { pick = l[i] } }
                }
            }
        }
    }
}

@Composable
private fun PartnerTile(p: Partner, onClick: () -> Unit) {
    val prof = rememberProfile(p.uid)
    val photo = p.photo.ifBlank { prof?.avatar.orEmpty() }
    Box(Modifier.fillMaxWidth().aspectRatio(0.78f).clip(RoundedCornerShape(16.dp)).background(Slate800).clickable(onClick = onClick)) {
        if (photo.isNotBlank()) coil.compose.AsyncImage(model = photo, contentDescription = p.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(p.name.take(1), color = White, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.5f)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color(0xD9000000)))))
        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            Text("${p.name}${if (p.age > 0) " · ${p.age}" else ""}", color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Text(if (p.count > 0) "★ ${"%.1f".format(p.avg)} (${p.count})" else tr("ใหม่"), color = Amber400, fontSize = 11.sp, maxLines = 1)
            Text(p.modes.joinToString(" ") { MODE_TH[it] ?: it }, color = PureWhite.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun PartnerCard(p: Partner, onClick: () -> Unit) {
    val prof = rememberProfile(p.uid)
    Surface(shape = RoundedCornerShape(16.dp), color = Slate900, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                Avatar(prof?.avatar.orEmpty(), p.name, 56)
                if (p.count > 0) { Text(stars(p.avg), color = Amber400, fontSize = 11.sp); Text("${p.avg} (${p.count})", color = Slate400, fontSize = 10.sp) }
                else Text("ยังไม่มีคะแนน", color = Slate400, fontSize = 10.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${p.name} · ${p.age}", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(p.occupation, color = Slate300, fontSize = 12.sp)
                Text(p.modes.joinToString("  ") { MODE_TH[it] ?: it }, color = Pink500, fontSize = 12.sp)
                Text("สะดวก: " + p.slots.joinToString(" ") { SLOT_TH[it] ?: it } + if (p.hours.isNotEmpty()) " · " + p.hours.entries.joinToString(" ") { "${DAY_TH[it.key]} ${it.value}" } else "", color = Slate400, fontSize = 11.sp)
                if (p.talkStyle.isNotBlank()) Text("ชอบคุย: ${p.talkStyle}", color = Slate300, fontSize = 12.sp, maxLines = 2)
                if (p.personality.isNotBlank()) Text("นิสัย: ${p.personality}", color = Slate300, fontSize = 12.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun ModeSelectDialog(p: Partner, onClose: () -> Unit, onTextChat: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var rates by remember { mutableStateOf<JSONObject?>(null) }
    var msg by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { rates = api("GET", "/companion/rates").json }
    val r = rates
    val curIcon = if (r?.optString("currency") == "coins") "🪙" else "💎"
    val bal = if (r?.optString("currency") == "coins") Wallet.coins else Wallet.diamonds
    val textCost = r?.optInt("textAmount") ?: 0
    val canStart = r != null && bal > 0 && bal >= textCost
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = Slate900) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("อยากคุยแบบไหน?", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text("กับ ${p.name} · ยอดของคุณ $curIcon $bal", color = Slate300)
                if (r != null && !canStart) {
                    Text(if (curIcon == "💎") "เติมเพชรก่อนเริ่มคุย" else "เติมเหรียญก่อนเริ่มคุย", color = Rose500, fontWeight = FontWeight.Bold)
                    Button(onClick = { com.example.ui.shop.Shop.open.value = if (curIcon == "💎") "diamonds" else "coins" }) { Text("เติม $curIcon") }
                }
                val textLabel = if (r?.optString("textMode") == "per_session") "$curIcon $textCost / รอบ ${r.optInt("sessionMinutes")} นาที" else "$curIcon $textCost / ข้อความ"
                ModeButton("💬 เริ่มคุยข้อความ", textLabel, enabled = canStart && "text" in p.modes) { confirm = true }
                ModeButton("📞 เริ่มคุยโทรเสียง", "$curIcon ${r?.optInt("voicePerMin") ?: 0} / นาที · เร็ว ๆ นี้", enabled = false) {}
                ModeButton("🎥 เริ่มคุยวิดีโอคอล", "$curIcon ${r?.optInt("videoPerMin") ?: 0} / นาที · เร็ว ๆ นี้", enabled = false) {}
                if ("voice" !in p.modes || "video" !in p.modes) Text("โหมดที่เพื่อนคุยคนนี้ไม่รับจะกดไม่ได้", color = Slate400, fontSize = 11.sp)
                if (msg.isNotBlank()) Text(msg, color = Amber400)
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text(tr("ยกเลิก")) }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("ยืนยันเริ่มคุยข้อความ") },
        text = { Text("อัตรา: ${textLabelOf(r)}\nระบบจะหัก${if (curIcon == "💎") "เพชร" else "เหรียญ"}อัตโนมัติ ผู้ให้บริการได้รับ ${r?.optInt("partnerPercent") ?: 80}%") },
        confirmButton = { Button(onClick = { confirm = false; scope.launch {
            val o = api("POST", "/companion/${p.uid}/open")
            val cid = o.json?.optString("chatId").orEmpty()
            if (o.code != 200 || cid.isBlank()) { msg = errOf(o); return@launch }
            if (r?.optString("textMode") == "per_session") { val s = api("POST", "/companion/chats/$cid/session"); if (s.code != 200) { msg = errOf(s); return@launch } }
            onTextChat(cid)
        } }) { Text("เริ่มคุย") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("ยกเลิก")) } })
}
private fun textLabelOf(r: JSONObject?): String { val c = if (r?.optString("currency") == "coins") "🪙" else "💎"
    return if (r?.optString("textMode") == "per_session") "$c ${r.optInt("textAmount")} ต่อรอบ ${r.optInt("sessionMinutes")} นาที" else "$c ${r?.optInt("textAmount") ?: 0} ต่อข้อความ" }

@Composable
private fun ModeButton(title: String, sub: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = if (enabled) Pink500 else Slate800, modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = if (enabled) PureWhite else Slate400, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(sub, color = if (enabled) PureWhite.copy(alpha = 0.85f) else Slate500, fontSize = 12.sp)
        }
    }
}

/** Paid-chat helper used by the chat room: text in a paid เพื่อนคุย chat goes through the brain (server charges). */
object PaidChat {
    suspend fun info(chatId: String): Pair<Boolean, String>? = runCatching {
        val s = FirebaseDatabase.getInstance("https://friendtalk-4e623-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("chats/$chatId").get().await()
        (s.child("paid").getValue(Boolean::class.java) == true) to (s.child("companion").getValue(String::class.java) ?: "")
    }.getOrNull()
    /** null = sent; else error message */
    suspend fun send(chatId: String, text: String): String? { val r = api("POST", "/companion/chats/$chatId/send", JSONObject().put("text", text)); return if (r.code == 200) null else errOf(r) }
}

object CompanionRating {
    private var ask by mutableStateOf<String?>(null)
    fun maybeAsk(partner: String) { ask = partner }
    @Composable fun Host() {
        val p = ask ?: return
        val scope = rememberCoroutineScope()
        var can by remember(p) { mutableStateOf<Boolean?>(null) }
        var starsN by remember(p) { mutableIntStateOf(5) }
        var comment by remember(p) { mutableStateOf("") }
        LaunchedEffect(p) { val j = api("GET", "/companion/$p/my-rating").json; can = j?.optBoolean("canRate") == true
            j?.optJSONObject("rating")?.let { starsN = it.optInt("stars", 5); comment = it.optString("comment") } }
        if (can == false) { ask = null; return }
        if (can == null) return
        AlertDialog(onDismissRequest = { ask = null }, title = { Text("ให้คะแนนเพื่อนคุย") }, text = {
            Column { Row { (1..5).forEach { i -> Text(if (i <= starsN) "★" else "☆", fontSize = 32.sp, color = Amber400, modifier = Modifier.clickable { starsN = i }.padding(2.dp)) } }
                OutlinedTextField(comment, { comment = it.take(200) }, label = { Text("ความเห็น (ไม่บังคับ)") }) }
        }, confirmButton = { Button(onClick = { scope.launch { api("POST", "/companion/$p/rate", JSONObject().put("stars", starsN).put("comment", comment)); ask = null } }) { Text("ส่งคะแนน") } },
            dismissButton = { TextButton(onClick = { ask = null }) { Text("ข้าม") } })
    }
}

@Composable
fun CompanionSignupDialog(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var j by remember { mutableStateOf<JSONObject?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { j = api("GET", "/companion-apply").json ?: JSONObject() }
    FullDialog("สมัครเป็นเพื่อนคุย", onClose) {
        val cur = j ?: run { CircularProgressIndicator(color = Pink500); return@FullDialog }
        val app = cur.optJSONObject("application"); val pv = cur.optJSONObject("private")
        when (app?.optString("status")) {
            "pending" -> { Text("⏳ รอแอดมินตรวจสอบ", color = Amber400, fontWeight = FontWeight.Bold); return@FullDialog }
            "approved" -> { Text("✅ คุณเป็นเพื่อนคุยที่ได้รับอนุมัติแล้ว", color = Emerald400, fontWeight = FontWeight.Bold); return@FullDialog }
            "rejected" -> Text("❌ ไม่ผ่าน: ${app.optString("reason")} — แก้ไขแล้วส่งใหม่ได้", color = Rose500)
        }
        var realName by remember { mutableStateOf(pv?.optString("realName").orEmpty()) }
        var phone by remember { mutableStateOf(pv?.optString("phone").orEmpty()) }
        var occ by remember { mutableStateOf(app?.optString("occupation").orEmpty()) }
        var age by remember { mutableStateOf(app?.optInt("age")?.takeIf { it > 0 }?.toString().orEmpty()) }
        var dn by remember { mutableStateOf(app?.optString("displayName").orEmpty()) }
        val slots = remember { mutableStateListOf<String>().also { l -> app?.optJSONArray("slots").strings().forEach { l.add(it) } } }
        val modes = remember { mutableStateListOf<String>().also { l -> app?.optJSONArray("modes").strings().forEach { l.add(it) } } }
        val hours = remember { mutableStateMapOf<String, String>() }
        var talk by remember { mutableStateOf(app?.optString("talkStyle").orEmpty()) }
        var pers by remember { mutableStateOf(app?.optString("personality").orEmpty()) }
        var msg by remember { mutableStateOf("") }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val photos = remember { mutableStateListOf<android.net.Uri>() }
        var busy by remember { mutableStateOf(false) }
        val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(4)) { picked ->
            picked.forEach { if (photos.size < 4 && it !in photos) photos.add(it) }
        }
        // photos of the applicant (required, 1–4): shown to admins for review and on the approved companion card
        Text("รูปของคุณ (1–4 รูป) *", color = White, fontWeight = FontWeight.Bold)
        Text("ใช้รูปตัวเองที่เห็นหน้าชัด ห้ามใช้รูปคนอื่น — แอดมินจะตรวจก่อนอนุมัติ", color = Slate400, fontSize = 12.sp)
        Row(Modifier.horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            photos.forEach { u ->
                Box(Modifier.size(84.dp).clip(RoundedCornerShape(14.dp))) {
                    coil.compose.AsyncImage(model = u, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color(0xCC000000))
                        .clickable(enabled = !busy) { photos.remove(u) }, contentAlignment = Alignment.Center) { Text("✕", color = PureWhite, fontSize = 11.sp) }
                }
            }
            if (photos.size < 4) Box(Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(Slate800).clickable(enabled = !busy) {
                photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("📷", fontSize = 22.sp); Text("เพิ่มรูป", color = Slate300, fontSize = 11.sp) }
            }
        }
        Text("🔒 ชื่อจริงและเบอร์โทรเป็นข้อมูลส่วนตัว เห็นได้เฉพาะคุณและแอดมินที่มีสิทธิ์ ไม่แสดงต่อผู้ใช้อื่น", color = Slate400, fontSize = 12.sp)
        OutlinedTextField(realName, { realName = it.take(80) }, label = { Text("ชื่อจริง (ส่วนตัว)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = tfColors)
        OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' }.take(13) }, label = { Text("เบอร์โทร (ส่วนตัว) เช่น 0812345678") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = tfColors)
        OutlinedTextField(dn, { dn = it.take(40) }, label = { Text("ชื่อที่แสดงในแอป") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = tfColors)
        OutlinedTextField(occ, { occ = it.take(60) }, label = { Text("อาชีพ") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = tfColors)
        OutlinedTextField(age, { age = it.filter(Char::isDigit).take(2) }, label = { Text("อายุ (18+)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = tfColors)
        Text("รูปแบบการคุยที่รับ (เลือกอย่างน้อย 1)", color = White, fontWeight = FontWeight.Bold)
        FlowRowCompat { MODE_TH.forEach { (k, l) -> FilterChip(selected = k in modes, onClick = { if (k in modes) modes.remove(k) else modes.add(k) }, label = { Text(l) }) } }
        Text("ช่วงเวลาที่สะดวก", color = White, fontWeight = FontWeight.Bold)
        FlowRowCompat { SLOT_TH.forEach { (k, l) -> FilterChip(selected = k in slots, onClick = { if (k in slots) slots.remove(k) else slots.add(k) }, label = { Text(l) }) } }
        Text("(ไม่จำเป็นต้องมาตามเวลาที่เลือก)", color = Slate400, fontSize = 12.sp)
        Text("ช่วงเวลารายวัน (ไม่บังคับ เช่น 18:00-22:00)", color = Slate300, fontSize = 12.sp)
        DAY_TH.forEach { (k, l) -> OutlinedTextField(hours[k] ?: "", { hours[k] = it.take(11) }, label = { Text(l) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = tfColors) }
        OutlinedTextField(talk, { talk = it.take(300) }, label = { Text("ชอบคุยแบบไหน (${talk.length}/300)") }, modifier = Modifier.fillMaxWidth(), colors = tfColors)
        OutlinedTextField(pers, { pers = it.take(300) }, label = { Text("ลักษณะนิสัยตัวเองเป็นยังไง (${pers.length}/300)") }, modifier = Modifier.fillMaxWidth(), colors = tfColors)
        val phoneOk = Regex("^(0[689]\\d{8}|0[2-7]\\d{7}|\\+66[689]\\d{8})$").matches(phone)
        if (phone.isNotBlank() && !phoneOk) Text("เบอร์โทรไม่ถูกต้อง", color = Rose500, fontSize = 12.sp)
        if ((age.toIntOrNull() ?: 99) < 18) Text("ต้องมีอายุ 18 ปีขึ้นไป", color = Rose500, fontSize = 12.sp)
        if (photos.isEmpty()) Text("แนบรูปของคุณอย่างน้อย 1 รูป", color = Rose500, fontSize = 12.sp)
        Button(enabled = !busy && photos.isNotEmpty() && phoneOk && (age.toIntOrNull() ?: 0) >= 18 && slots.isNotEmpty() && modes.isNotEmpty(), onClick = { scope.launch {
            busy = true; msg = "กำลังอัปโหลดรูป…"
            val refs = JSONArray()
            for (u in photos) { val p = com.example.social.FeedRepo.uploadImage(ctx, u, "companion") ?: run { msg = "อัปโหลดรูปไม่สำเร็จ (ยืนยันอีเมลแล้วหรือยัง?)"; busy = false; return@launch }; refs.put(p) }
            msg = "กำลังส่งใบสมัคร…"
            val h = JSONObject(); hours.forEach { (k, v) -> if (v.isNotBlank()) h.put(k, v.trim()) }
            val r = api("POST", "/companion-apply", JSONObject().put("realName", realName).put("phone", phone).put("displayName", dn).put("occupation", occ).put("age", age.toInt())
                .put("slots", JSONArray(slots.toList())).put("modes", JSONArray(modes.toList())).put("hours", h).put("talkStyle", talk).put("personality", pers).put("photoRefs", refs))
            busy = false
            if (r.code == 200) { msg = ""; tick++ } else msg = errOf(r)
        } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text(if (busy) "กำลังส่ง…" else "ส่งใบสมัคร", color = PureWhite) }
        if (msg.isNotBlank()) Text(msg, color = Amber400)
    }
}

// ===================== Admin panes =====================
@Composable
fun AdminApplicationsPane() {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("creator") }
    var status by remember { mutableStateOf("pending") }
    var list by remember { mutableStateOf<List<JSONObject>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(kind, status, tick) { list = null
        val r = api("GET", "/admin/${if (kind == "creator") "creator" else "companion"}-applications?status=$status")
        list = r.json?.optJSONArray("applications")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = kind == "creator", onClick = { kind = "creator" }, label = { Text("🎨 ครีเอเตอร์") })
            FilterChip(selected = kind == "companion", onClick = { kind = "companion" }, label = { Text("💗 เพื่อนคุย") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("pending" to "รอตรวจ", "approved" to "อนุมัติ", "rejected" to "ปฏิเสธ").forEach { (k, l) -> FilterChip(selected = status == k, onClick = { status = k }, label = { Text(l) }) } }
        if (kind == "creator") {
            Text(AD_REVENUE_NOTE, color = Slate400, fontSize = 11.sp)
            Text("หมายเหตุ: ยังไม่มีระบบนับยอดวิว/ผู้ชมไม่ซ้ำ/ผู้ติดตาม/การลงคลิป และยังไม่กำหนดสัดส่วนรายได้โฆษณาและวิธีจ่ายเงิน — กติกาเป็นข้อตกลงที่ผู้สมัครยอมรับเท่านั้น", color = Amber400, fontSize = 11.sp)
            CreatorRulesEditor()
        } else CompanionRatesNote()
        if (msg.isNotBlank()) Text(msg, color = Amber400)
        val l = list
        if (l == null) CircularProgressIndicator(color = Pink500) else if (l.isEmpty()) Text("ไม่มีรายการ", color = Slate400)
        l?.forEach { a ->
            var reason by remember(a.optString("uid")) { mutableStateOf("") }
            Surface(shape = RoundedCornerShape(14.dp), color = Slate900, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(a.optString("displayName") + "  (" + a.optString("uid").take(8) + "…)", color = White, fontWeight = FontWeight.Bold)
                    if (kind == "creator") {
                        Text("หมวด: ${CR_CAT_TH[a.optString("category")] ?: a.optString("category")} · ส่ง ${ts(a.optLong("submittedAt"))}", color = Slate300, fontSize = 12.sp)
                        Text(a.optString("bio"), color = Slate300, fontSize = 12.sp)
                        a.optJSONArray("links").strings().forEach { Text("🔗 $it", color = Blue500, fontSize = 12.sp) }
                        if (a.optString("sampleUrl").isNotBlank()) Text("🎬 ${a.optString("sampleUrl")}", color = Blue500, fontSize = 12.sp)
                        Text("กติกาที่ยอมรับ (v${a.optInt("rulesVersion")}, ${ts(a.optLong("agreedAt"))}):", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        a.optJSONArray("rulesAgreed").strings().forEachIndexed { i, r -> Text("✓ ${i + 1}. $r", color = Slate300, fontSize = 11.sp) }
                    } else {
                        val pv = a.optJSONObject("private")
                        val ph = a.optJSONArray("photos").strings()
                        if (ph.isNotEmpty()) Row(Modifier.horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ph.forEach { url -> coil.compose.AsyncImage(model = url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))) }
                        } else Text("(ไม่มีรูปแนบ)", color = Slate400, fontSize = 12.sp)
                        Text("🔒 ชื่อจริง: ${pv?.optString("realName")} · โทร: ${pv?.optString("phone")}", color = Amber400, fontSize = 12.sp)
                        Text("อาชีพ ${a.optString("occupation")} · อายุ ${a.optInt("age")} · โหมด ${a.optJSONArray("modes").strings().joinToString { MODE_TH[it] ?: it }}", color = Slate300, fontSize = 12.sp)
                        Text("สะดวก: ${a.optJSONArray("slots").strings().joinToString(" ") { SLOT_TH[it] ?: it }}", color = Slate300, fontSize = 12.sp)
                        if (a.optString("talkStyle").isNotBlank()) Text("ชอบคุย: ${a.optString("talkStyle")}", color = Slate300, fontSize = 12.sp)
                        if (a.optString("personality").isNotBlank()) Text("นิสัย: ${a.optString("personality")}", color = Slate300, fontSize = 12.sp)
                        if (status == "approved") CompanionRatingsAdmin(a.optString("uid"))
                    }
                    if (a.optString("reason").isNotBlank()) Text("เหตุผล: ${a.optString("reason")}", color = Rose500, fontSize = 12.sp)
                    if (status == "pending" || status == "approved") {
                        OutlinedTextField(reason, { reason = it.take(300) }, label = { Text("เหตุผล (จำเป็นเมื่อปฏิเสธ)") }, modifier = Modifier.fillMaxWidth(), colors = tfColors)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (status == "pending") Button(onClick = { scope.launch { val r = api("POST", "/admin/${kind}-applications/${a.optString("uid")}/decide", JSONObject().put("approve", true)); msg = if (r.code == 200) "อนุมัติแล้ว" else errOf(r); tick++ } },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald500)) { Text("อนุมัติ", color = PureWhite) }
                            OutlinedButton(enabled = reason.trim().length >= 3, onClick = { scope.launch { val r = api("POST", "/admin/${kind}-applications/${a.optString("uid")}/decide", JSONObject().put("approve", false).put("reason", reason)); msg = if (r.code == 200) "ปฏิเสธแล้ว" else errOf(r); tick++ } }) {
                                Text(if (status == "approved") "ถอนสิทธิ์" else "ปฏิเสธ", color = Rose500) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanionRatingsAdmin(uid: String) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { list = api("GET", "/admin/companion-ratings/$uid").json?.optJSONArray("ratings")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList() }
    if (list.isEmpty()) Text("ยังไม่มีคะแนน", color = Slate400, fontSize = 11.sp)
    list.forEach { r -> Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${"★".repeat(r.optInt("stars"))} ${r.optString("comment")}", color = Slate300, fontSize = 11.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { scope.launch { api("DELETE", "/admin/companion-ratings/$uid/${r.optString("uid")}"); tick++ } }) { Text("ลบ", color = Rose500, fontSize = 11.sp) }
    } }
}

@Composable
private fun CreatorRulesEditor() {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var cur by remember { mutableStateOf("เหรียญ") }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(open) { if (open) api("GET", "/creator/status").json?.optJSONObject("rules")?.let { j ->
        cur = j.optString("payoutCurrency", "เหรียญ"); text = (j.optJSONArray("rawRules") ?: j.optJSONArray("rules")).strings().joinToString("\n") } }
    TextButton(onClick = { open = !open }) { Text(if (open) "▲ ซ่อนการแก้กติกาครีเอเตอร์" else "✏️ แก้ไขกติกาครีเอเตอร์") }
    if (open) {
        OutlinedTextField(text, { text = it }, label = { Text("กติกา (บรรทัดละ 1 ข้อ, {currency} = หน่วยเงิน)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), colors = tfColors)
        OutlinedTextField(cur, { cur = it.take(20) }, label = { Text("หน่วยเงินที่จ่าย (เช่น เหรียญ / เพชร / บาท)") }, singleLine = true, colors = tfColors)
        Button(onClick = { scope.launch { val r = api("PUT", "/admin/creator-rules", JSONObject().put("rules", JSONArray(text.lines().map { it.trim() }.filter { it.isNotBlank() })).put("payoutCurrency", cur))
            msg = if (r.code == 200) "บันทึกแล้ว (เวอร์ชัน ${r.json?.optInt("version")})" else errOf(r) } }) { Text("บันทึกกติกา") }
        if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp)
    }
}

@Composable
private fun CompanionRatesNote() { Text("อัตราค่าเพื่อนคุยตั้งค่าที่ เศรษฐกิจ → อัตราค่าเพื่อนคุย", color = Slate400, fontSize = 11.sp) }

/** Economy → อัตราค่าเพื่อนคุย (config_edit; server-enforced). */
@Composable
fun AdminCompanionRatesPane() {
    val scope = rememberCoroutineScope()
    var j by remember { mutableStateOf<JSONObject?>(null) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { j = api("GET", "/companion/rates").json ?: JSONObject() }
    val r = j ?: run { CircularProgressIndicator(color = Pink500); return }
    var mode by remember { mutableStateOf(r.optString("textMode", "per_message")) }
    var amt by remember { mutableStateOf(r.optInt("textAmount", 1).toString()) }
    var mins by remember { mutableStateOf(r.optInt("sessionMinutes", 60).toString()) }
    var voice by remember { mutableStateOf(r.optInt("voicePerMin").toString()) }
    var video by remember { mutableStateOf(r.optInt("videoPerMin").toString()) }
    var cur by remember { mutableStateOf(r.optString("currency", "diamonds")) }
    var pct by remember { mutableStateOf(r.optInt("partnerPercent", 80).toString()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("💗 อัตราค่าเพื่อนคุย", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text("ผู้ใช้/เพื่อนคุยตั้งราคาเองไม่ได้ · คิดเงินข้อความฝั่งเซิร์ฟเวอร์ (ยอดไม่พอ = ส่งไม่ได้) · โทรเสียง/วิดีโอ เก็บอัตราไว้ จะคิดเงินเมื่อมีระบบโทร", color = Slate400, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = mode == "per_message", onClick = { mode = "per_message" }, label = { Text("ต่อข้อความ") })
            FilterChip(selected = mode == "per_session", onClick = { mode = "per_session" }, label = { Text("ต่อรอบ") })
        }
        OutlinedTextField(amt, { amt = it.filter(Char::isDigit).take(6) }, label = { Text(if (mode == "per_session") "ราคาต่อรอบ" else "ราคาต่อข้อความ") }, singleLine = true, colors = tfColors)
        if (mode == "per_session") OutlinedTextField(mins, { mins = it.filter(Char::isDigit).take(4) }, label = { Text("ความยาวรอบ (นาที)") }, singleLine = true, colors = tfColors)
        OutlinedTextField(voice, { voice = it.filter(Char::isDigit).take(6) }, label = { Text("โทรเสียง ต่อนาที") }, singleLine = true, colors = tfColors)
        OutlinedTextField(video, { video = it.filter(Char::isDigit).take(6) }, label = { Text("วิดีโอคอล ต่อนาที") }, singleLine = true, colors = tfColors)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = cur == "diamonds", onClick = { cur = "diamonds" }, label = { Text("💎 เพชร") })
            FilterChip(selected = cur == "coins", onClick = { cur = "coins" }, label = { Text("🪙 เหรียญ") })
        }
        OutlinedTextField(pct, { pct = it.filter(Char::isDigit).take(3) }, label = { Text("ส่วนแบ่งเพื่อนคุย % (แพลตฟอร์มได้ส่วนที่เหลือ)") }, singleLine = true, colors = tfColors)
        Button(onClick = { scope.launch { val x = api("PUT", "/admin/companion-rates", JSONObject().put("textMode", mode).put("textAmount", amt.toIntOrNull() ?: 0).put("sessionMinutes", mins.toIntOrNull() ?: 60)
            .put("voicePerMin", voice.toIntOrNull() ?: 0).put("videoPerMin", video.toIntOrNull() ?: 0).put("currency", cur).put("partnerPercent", pct.toIntOrNull() ?: 80))
            msg = if (x.code == 200) "บันทึกแล้ว" else errOf(x) } }) { Text("บันทึก") }
        if (msg.isNotBlank()) Text(msg, color = Amber400)
    }
}

/** Economy → ราคาแพ็กเพชร/เหรียญ (config_edit). Charged price lives in Play Console; 'sync' pushes THB prices via Android Publisher API. */
@Composable
fun AdminShopPacksPane() {
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var pkg by remember { mutableStateOf("") }
    val rows = remember { mutableStateListOf<MutableMap<String, String>>() }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val j = api("GET", "/shop/products").json ?: return@LaunchedEffect
        enabled = j.optBoolean("enabled"); pkg = j.optString("packageName")
        listOf("diamonds", "coins").forEach { c -> j.optJSONArray(c)?.let { a -> (0 until a.length()).forEach { i -> val o = a.getJSONObject(i)
            rows.add(mutableStateMapOf("cur" to c, "productId" to o.optString("productId"), "amount" to o.optInt("amount").toString(), "bonus" to o.optInt("bonus").toString(),
                "priceTHB" to o.optDouble("priceTHB", 0.0).toString(), "enabled" to o.optBoolean("enabled", true).toString(), "order" to o.optDouble("order", i.toDouble()).toInt().toString(), "label" to o.optString("label"))) } } }
    }
    fun body(sync: Boolean): JSONObject {
        fun arr(c: String) = JSONArray(rows.filter { it["cur"] == c }.map { m -> JSONObject().put("productId", m["productId"]).put("amount", m["amount"]?.toIntOrNull() ?: 0).put("bonus", m["bonus"]?.toIntOrNull() ?: 0)
            .put("priceTHB", m["priceTHB"]?.toDoubleOrNull() ?: 0.0).put("enabled", m["enabled"] == "true").put("order", m["order"]?.toIntOrNull() ?: 0).put("label", m["label"]) })
        return JSONObject().put("enabled", enabled).put("packageName", pkg).put("diamonds", arr("diamonds")).put("coins", arr("coins")).put("syncPlay", sync)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🛍 ราคาแพ็กเพชร/เหรียญ", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text("ราคาที่เก็บเงินจริงอยู่ใน Play Console — ร้านในแอปแสดงราคาจาก Google Play · ปุ่ม 'ซิงก์ราคาไป Play' ใช้ได้เมื่อแอปและสินค้าอยู่บน Play แล้วและบัญชีบริการมีสิทธิ์", color = Slate400, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) { Text("เปิดร้านค้า", color = White, modifier = Modifier.weight(1f)); Switch(checked = enabled, onCheckedChange = { enabled = it }) }
        OutlinedTextField(pkg, { pkg = it.trim() }, label = { Text("Package name บน Play") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = tfColors)
        rows.forEachIndexed { i, m ->
            Surface(shape = RoundedCornerShape(12.dp), color = Slate900) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (m["cur"] == "diamonds") "💎" else "🪙", fontSize = 18.sp)
                    Switch(checked = m["enabled"] == "true", onCheckedChange = { m["enabled"] = it.toString() })
                    Spacer(Modifier.weight(1f)); TextButton(onClick = { rows.removeAt(i) }) { Text("ลบ", color = Rose500) }
                }
                listOf("productId" to "Product ID (Play)", "amount" to "จำนวน", "bonus" to "โบนัส", "priceTHB" to "ราคา ฿", "order" to "ลำดับ", "label" to "ป้าย").forEach { (k, l) ->
                    OutlinedTextField(m[k].orEmpty(), { m[k] = it.take(60) }, label = { Text(l) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = tfColors) }
            } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { rows.add(mutableStateMapOf("cur" to "diamonds", "enabled" to "true", "order" to rows.size.toString())) }) { Text("+ แพ็กเพชร") }
            OutlinedButton(onClick = { rows.add(mutableStateMapOf("cur" to "coins", "enabled" to "true", "order" to rows.size.toString())) }) { Text("+ แพ็กเหรียญ") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { val r = api("PUT", "/admin/shop-products", body(false)); msg = if (r.code == 200) "บันทึกแล้ว" else errOf(r) } }) { Text("บันทึก") }
            Button(onClick = { scope.launch { val r = api("PUT", "/admin/shop-products", body(true))
                msg = if (r.code == 200) "บันทึกแล้ว · Play: " + (r.json?.optJSONArray("playSync")?.let { a -> (0 until a.length()).joinToString { val o = a.getJSONObject(it); "${o.optString("productId")}=${o.optInt("status")}" } } ?: "-") else errOf(r) } }) { Text("บันทึก + ซิงก์ราคาไป Play") }
        }
        if (msg.isNotBlank()) Text(msg, color = Amber400)
    }
}

/** Horizontal scroll shortcut used by the companion photo rows. */
@Composable
private fun Modifier.horizontalScrollCompat(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))
