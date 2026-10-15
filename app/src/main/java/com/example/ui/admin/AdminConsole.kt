package com.example.ui.admin

import com.example.ui.components.dialogNavPadding

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.network.BrainApi
import com.example.ui.social.Avatar
import com.example.ui.social.UserProfileDialog
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private suspend fun api(method: String, path: String, body: JSONObject? = null): BrainApi.ApiResponse {
    val u = FirebaseAuth.getInstance().currentUser ?: return BrainApi.ApiResponse(0, null)
    return BrainApi.call(u, method, path, body)
}
private fun err(r: BrainApi.ApiResponse) = r.json?.optString("error")?.ifBlank { null } ?: "ไม่สำเร็จ (${r.code})"
private val dfmt = SimpleDateFormat("d MMM yy HH:mm", Locale("th", "TH"))
private fun ts(t: Long) = if (t > 0) dfmt.format(Date(t)) else "-"

/** Server-side permissions of the signed-in admin (from /admin/overview). Superadmin = everything. */
private data class Perms(val role: String, val p: Map<String, Boolean>) {
    val superadmin get() = role == "superadmin"
    fun has(k: String) = superadmin || p[k] == true
}

val ROLE_TH = linkedMapOf("all" to "ทั้งหมด", "superadmin" to "ซูเปอร์แอดมิน", "admin" to "แอดมิน", "moderator" to "ผู้ดูแล",
    "creator" to "ครีเอเตอร์", "user" to "ผู้ใช้", "banned" to "ถูกแบน")
private val PERM_TH = linkedMapOf("ban_users" to "แบน/ปลดแบนผู้ใช้", "manage_content" to "จัดการเนื้อหา/รายงาน/จำกัดสิทธิ์", "coins_confirm" to "เหรียญ (ยืนยัน/ให้เหรียญ)",
    "config_edit" to "ตั้งค่าระบบ/UI/บอท/ลิมิต", "age_approve" to "อนุมัติยืนยันอายุ", "app_lock" to "ล็อกแอปทั้งระบบ")

/** Full-screen admin console: Overview · Members & roles · Reports · Economy · Content & UI · AI bots. Every action is enforced by the backend too. */
@Composable
fun AdminConsole(onDismiss: () -> Unit, onOpenUiBuilder: () -> Unit, onOpenApkDownload: () -> Unit) {
    var perms by remember { mutableStateOf<Perms?>(null) }
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var loadErr by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    var section by remember { mutableStateOf<String?>(null) } // null = menu
    LaunchedEffect(tick) {
        val r = api("GET", "/admin/overview")
        if (r.code == 200 && r.json != null) {
            val j = r.json; overview = j
            val pj = j.optJSONObject("yourPermissions"); val m = mutableMapOf<String, Boolean>()
            pj?.keys()?.forEach { m[it] = pj.optBoolean(it) }
            perms = Perms(j.optString("yourRole"), m)
        } else loadErr = err(r)
    }
    // Back inside a section returns to the menu; back on the menu closes the console.
    Dialog(onDismissRequest = { if (section != null) section = null else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Slate950) {
            Column(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding()) {
                Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("คอนโซลแอดมิน", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        perms?.let { Text(ROLE_TH[it.role] ?: it.role, color = if (it.superadmin) Amber400 else Purple400, fontSize = 12.sp) }
                    }
                    TextButton(onClick = onDismiss) { Text("ปิด ✕", color = Slate300) }
                }
                val p = perms
                if (p == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { if (loadErr.isNotBlank()) Text(loadErr, color = Amber400) else CircularProgressIndicator(color = Pink500) }; return@Column }
                val sections = adminSections(p)
                val sec = sections.firstOrNull { it.key == section }
                if (sec == null) {
                    AdminHome(sections, overview, p) { section = it }
                } else {
                    var sub by remember(sec.key) { mutableStateOf(sec.subs.first().first) }
                    // section header: back to the menu + title
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { section = null }) { Text("‹ เมนู", color = Pink400, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                        Text(sec.icon + " " + sec.title, color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (sec.subs.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sec.subs.forEach { (k2, l) ->
                            val on = sub == k2
                            Box(Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) Pink500 else Slate900).border(1.dp, if (on) Pink500 else Slate700, RoundedCornerShape(18.dp))
                                .clickable { sub = k2 }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Text(l, color = if (on) White else Slate300, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                    if (sec.note.isNotBlank()) Text(sec.note, color = Slate400, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    HorizontalDivider(color = Slate800, modifier = Modifier.padding(top = 6.dp))
                    Box(Modifier.fillMaxSize()) {
                        when (sub) {
                            "overview" -> OverviewPane(overview, p, onOpenApkDownload) { tick++ }
                            "members" -> MembersPane(p)
                            "reports" -> ReportsPane(p)
                            "applications" -> com.example.ui.social.AdminApplicationsPane()
                            "topup" -> com.example.ui.shop.AdminTopupPane()
                            "packs" -> com.example.ui.social.AdminShopPacksPane()
                            "rates" -> com.example.ui.social.AdminCompanionRatesPane()
                            "vipplans" -> VipPlansPane()
                            "grant" -> GrantPane()
                            "placements" -> AdPlacementsCard()
                            "limits" -> AdminLimitsTab()
                            "gifts" -> AdminGiftsTab()
                            "decor" -> AdminDecorOptionsPane()
                            "uibuilder" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("ปรับหน้าตาแอปทั้งหมดในที่เดียว: ลากวางหน้าแรก, ธีม & สี, จัด Feed, ชื่อแอป/คำโปรย, โหมดปิดปรับปรุง, ประวัติเวอร์ชัน", color = Slate300, fontSize = 13.sp)
                                Button(onClick = onOpenUiBuilder, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Purple600)) { Text("เปิด UI Builder") }
                            }
                            "showcase" -> com.example.ui.feed.AdminShowcasePane()
                            "comingsoon" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { ComingSoonList(p) }
                            "bots" -> com.example.ui.ai.AdminBotsTab()
                            "system" -> SystemPane()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color = White, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = Slate900, border = BorderStroke(1.dp, Slate800), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text(label, color = Slate400, fontSize = 12.sp, maxLines = 2)
        }
    }
}

@Composable
private fun OverviewPane(o: JSONObject?, p: Perms, onApk: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (o == null) { CircularProgressIndicator(color = Pink500); return@Column }
        val rows = listOf(
            listOf("ผู้ใช้ทั้งหมด" to o.optInt("users").toString(), "ใช้งาน 24 ชม." to o.optInt("active24h").toString()),
            listOf("สมัครใหม่วันนี้" to o.optInt("newToday").toString(), "โพสต์วันนี้" to "${o.optInt("postsToday")} / ${o.optInt("postsTotal")}"),
            listOf("รายงานที่รอตรวจ" to o.optInt("openReports").toString(), "ถูกแบนอยู่" to o.optInt("banned").toString()),
            listOf("เหรียญที่ออกแล้ว" to o.optLong("coinsIssued").toString(), "ทีมแอดมิน" to o.optInt("admins").toString()),
            listOf("คำขอซื้อเหรียญรอยืนยัน" to o.optInt("pendingCoinRequests").toString(), "คำขอยืนยันอายุรอตรวจ" to o.optInt("pendingAgeRequests").toString()))
        rows.forEach { r -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { r.forEach { (l, v) -> Stat(l, v, if (l.contains("รอ") && v != "0") Amber400 else White, Modifier.weight(1f)) } } }
        Text("เหรียญ: ซื้อจริง ${o.optLong("coinsPurchased")} · แอดมินให้ ${o.optLong("coinsGranted")}", color = Slate400, fontSize = 12.sp)
        Text("สิทธิ์ของคุณ", color = White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        PERM_TH.forEach { (k, l) -> Text((if (p.has(k)) "✅ " else "⛔ ") + l, color = if (p.has(k)) Slate200 else Slate500, fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = onRefresh) { Text("รีเฟรช", color = White) }
            OutlinedButton(onClick = onApk) { Text("ลิงก์ดาวน์โหลด APK", color = White) }
        }
    }
}

@Composable
private fun MembersPane(p: Perms) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("all") }
    var list by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var counts by remember { mutableStateOf<JSONObject?>(null) }
    var next by remember { mutableStateOf<Int?>(null) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    fun load(offset: Int) { busy = true; scope.launch {
        val r = api("GET", "/admin/users?q=" + android.net.Uri.encode(q.trim()) + "&role=$role&offset=$offset")
        if (r.code == 200 && r.json != null) {
            val a = r.json.optJSONArray("users"); val l = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }
            list = if (offset == 0) l else list + l; counts = r.json.optJSONObject("counts"); next = if (r.json.isNull("next")) null else r.json.optInt("next"); msg = ""
        } else msg = err(r)
        busy = false
    } }
    LaunchedEffect(role, reload) { load(0) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(value = q, onValueChange = { q = it.take(60) }, singleLine = true, placeholder = { Text("ค้นหาชื่อ, @username หรือ UID", color = Slate400) },
            trailingIcon = { TextButton(onClick = { load(0) }) { Text("ค้นหา", color = Pink400) } }, shape = RoundedCornerShape(14.dp),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { load(0) }), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White), modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ROLE_TH.forEach { (k, l) ->
                val n = counts?.optInt(k, 0) ?: 0
                if (k == "all" || n > 0 || role == k) FilterChip(selected = role == k, onClick = { role = k }, label = { Text("$l ($n)") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Purple600, selectedLabelColor = White, labelColor = Slate300))
            }
        }
        if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 13.sp)
        if (com.example.ui.components.AppGates.listenError.isNotBlank()) Text("อ่านสถานะจากฐานข้อมูลไม่ได้: " + com.example.ui.components.AppGates.listenError, color = Rose500, fontSize = 12.sp)
        if (busy && list.isEmpty()) LinearProgressIndicator(color = Pink500, modifier = Modifier.fillMaxWidth())
        if (!busy && list.isEmpty() && msg.isBlank()) Text("ไม่พบสมาชิก", color = Slate400, modifier = Modifier.padding(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { it.optString("uid") }) { u ->
                Surface(shape = RoundedCornerShape(12.dp), color = Slate900, modifier = Modifier.fillMaxWidth().clickable { open = u.optString("uid") }) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(u.optString("avatar"), u.optString("displayName"), 40); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.optString("displayName").ifBlank { "(ไม่มีชื่อ)" }, color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("ใช้งานล่าสุด ${ts(u.optLong("lastActive"))}", color = Slate400, fontSize = 11.sp)
                        }
                        val r = u.optString("role")
                        if (u.optBoolean("banned")) Badge("ถูกแบน", Rose500)
                        else if (r != "user") Badge(ROLE_TH[r] ?: r, if (r == "superadmin") Amber500 else Purple500)
                    }
                }
            }
            if (next != null) item { TextButton(onClick = { load(next!!) }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "กำลังโหลด…" else "โหลดเพิ่ม", color = Pink400) } }
        }
    }
    open?.let { MemberDetail(it, p) { changed -> open = null; if (changed) reload++ } }
}

@Composable
private fun Badge(t: String, c: Color) = Surface(shape = RoundedCornerShape(8.dp), color = c.copy(alpha = 0.2f), border = BorderStroke(1.dp, c)) {
    Text(t, color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
private fun MemberDetail(uid: String, p: Perms, onClose: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var msg by remember { mutableStateOf("") }
    var changed by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var reason by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("0") }
    var amount by remember { mutableStateOf("") }
    var showProfile by remember { mutableStateOf(false) }
    var newRole by remember { mutableStateOf("user") }
    val rolePerms = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(tick) {
        val r = api("GET", "/admin/users/$uid")
        if (r.code == 200 && r.json != null) {
            d = r.json; newRole = r.json.optString("role")
            val pj = r.json.optJSONObject("permissions"); rolePerms.clear(); PERM_TH.keys.forEach { rolePerms[it] = pj?.optBoolean(it) == true }
        } else msg = err(r)
    }
    fun act(method: String, path: String, body: JSONObject?, ok: String) { scope.launch {
        val r = api(method, path, body); msg = if (r.code in 200..299) { changed = true; tick++; ok } else err(r)
    } }
    Dialog(onDismissRequest = { onClose(changed) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Slate950) {
            Column(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹ กลับ", color = Slate300, modifier = Modifier.clickable { onClose(changed) }.padding(end = 12.dp))
                    Text("จัดการสมาชิก", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                }
                val j = d ?: run { if (msg.isNotBlank()) Text(msg, color = Amber400) else CircularProgressIndicator(color = Pink500); return@Column }
                val pr = j.optJSONObject("profile") ?: JSONObject()
                val tRole = j.optString("role"); val targetSuper = tRole == "superadmin"
                val targetStaff = tRole in listOf("admin", "moderator", "superadmin")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(pr.optString("avatar"), pr.optString("displayName"), 64); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pr.optString("displayName").ifBlank { "(ไม่มีชื่อ)" }, color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (pr.optString("username").isNotBlank()) Text("@" + pr.optString("username"), color = Slate400, fontSize = 12.sp)
                        if (j.optString("email").isNotBlank()) Text(j.optString("email") + if (j.optBoolean("emailVerified")) " ✓" else " (ยังไม่ยืนยัน)", color = Slate400, fontSize = 12.sp)
                        Text("UID: $uid", color = Slate500, fontSize = 10.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(ROLE_TH[tRole] ?: tRole, if (targetSuper) Amber500 else Purple500)
                    Badge("🪙 ${j.optLong("coins")}", Amber400)
                    if (!j.isNull("vip")) Badge("VIP", Amber500)
                    if (!j.isNull("ban")) Badge("ถูกแบน", Rose500)
                }
                Text("สมัคร ${ts(pr.optLong("createdAt"))} · ใช้งานล่าสุด ${ts(pr.optLong("lastActive"))}", color = Slate400, fontSize = 12.sp)
                OutlinedButton(onClick = { showProfile = true }) { Text("ดูโปรไฟล์", color = White) }
                if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 13.sp)
                OutlinedTextField(value = reason, onValueChange = { reason = it.take(200) }, label = { Text("เหตุผล (ใช้กับแบน/เหรียญ/จำกัดสิทธิ์)") }, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))

                if (p.has("ban_users") && !targetSuper && (!targetStaff || p.superadmin)) Section("แบน") {
                    val ban = j.optJSONObject("ban")
                    if (ban != null) {
                        Text("ถูกแบน: ${ban.optString("reason")} · ถึง ${if (ban.isNull("until")) "ถาวร" else ts(ban.optLong("until"))}", color = Rose500, fontSize = 13.sp)
                        Button(onClick = { act("POST", "/admin/users/$uid/ban", JSONObject().put("banned", false), "ปลดแบนแล้ว") }, colors = ButtonDefaults.buttonColors(containerColor = Emerald500)) { Text("ปลดแบน") }
                    } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = days, onValueChange = { days = it.filter(Char::isDigit).take(4) }, label = { Text("วัน (0=ถาวร)") }, singleLine = true, modifier = Modifier.width(130.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                        Button(onClick = { act("POST", "/admin/users/$uid/ban", JSONObject().put("banned", true).put("reason", reason).put("days", days.toIntOrNull() ?: 0), "แบนแล้ว") },
                            colors = ButtonDefaults.buttonColors(containerColor = Rose500)) { Text("แบน") }
                    }
                }
                if (p.has("manage_content")) Section("จำกัดสิทธิ์") {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("ห้ามโพสต์", color = White, modifier = Modifier.weight(1f))
                        Switch(checked = !j.isNull("noPost"), onCheckedChange = { act("PUT", "/admin/restrict/$uid", JSONObject().put("noPost", it).put("reason", reason), if (it) "ห้ามโพสต์แล้ว" else "ปลดห้ามโพสต์แล้ว") }) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("ห้ามแชท", color = White, modifier = Modifier.weight(1f))
                        Switch(checked = !j.isNull("noChat"), onCheckedChange = { act("PUT", "/admin/restrict/$uid", JSONObject().put("noChat", it).put("reason", reason), if (it) "ห้ามแชทแล้ว" else "ปลดห้ามแชทแล้ว") }) }
                }
                if (p.has("coins_confirm")) Section("เหรียญ / เพชร (บันทึกในสมุดบัญชีพร้อมเหตุผล)") {
                    var cur by remember { mutableStateOf("coins") }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || (p.superadmin && c == '-') }.take(7) }, label = { Text(if (p.superadmin) "จำนวน (+/-)" else "จำนวน (+ เพิ่มเท่านั้น)") }, singleLine = true, modifier = Modifier.width(150.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                        Button(enabled = (amount.toIntOrNull() ?: 0) != 0 && reason.trim().length >= 3, onClick = {
                            act("POST", "/admin/users/$uid/coins", JSONObject().put("amount", amount.toInt()).put("reason", reason).put("currency", cur), if (cur == "diamonds") "ปรับเพชรแล้ว" else "ปรับเหรียญแล้ว"); amount = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = Amber500)) { Text("ยืนยัน") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = cur == "coins", onClick = { cur = "coins" }, label = { Text("🪙 เหรียญ") })
                        FilterChip(selected = cur == "diamonds", onClick = { cur = "diamonds" }, label = { Text("💎 เพชร (ใช้ส่งของขวัญ)") })
                    }
                    Text("ต้องใส่เหตุผลอย่างน้อย 3 ตัวอักษร", color = Slate500, fontSize = 11.sp)
                }
                if (p.superadmin && !targetSuper && uid != FirebaseAuth.getInstance().currentUser?.uid) Section("บทบาท & สิทธิ์ (เฉพาะซูเปอร์แอดมิน)") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("user", "moderator", "admin").forEach { k -> FilterChip(selected = newRole == k, onClick = { newRole = k }, label = { Text(ROLE_TH[k]!!) }) }
                    }
                    if (newRole != "user") PERM_TH.forEach { (k, l) ->
                        val locked = newRole == "moderator" && k in listOf("coins_confirm", "config_edit", "age_approve")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = !locked && rolePerms[k] == true, enabled = !locked, onCheckedChange = { rolePerms[k] = it })
                            Text(l, color = if (locked) Slate500 else White, fontSize = 13.sp)
                        }
                    }
                    Button(onClick = {
                        val pj = JSONObject(); rolePerms.forEach { (k, v) -> pj.put(k, v) }
                        act("PUT", "/superadmin/users/$uid/role", JSONObject().put("role", newRole).put("permissions", pj), "บันทึกบทบาทแล้ว")
                    }, colors = ButtonDefaults.buttonColors(containerColor = Purple600)) { Text("บันทึกบทบาท") }
                }
                val reps = j.optJSONArray("reports")
                Section("รายงานเกี่ยวกับผู้ใช้นี้ (${reps?.length() ?: 0})") {
                    if ((reps?.length() ?: 0) == 0) Text("ไม่มี", color = Slate400, fontSize = 13.sp)
                    for (i in 0 until (reps?.length() ?: 0)) { val r = reps!!.getJSONObject(i)
                        Text("• [${r.optString("status", "open")}] ${r.optString("reason")} ${r.optString("preview").take(60)}", color = Slate300, fontSize = 12.sp) }
                }
            }
        }
    }
    if (showProfile) UserProfileDialog(uid) { showProfile = false }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Slate900, border = BorderStroke(1.dp, Slate800), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, color = White, fontWeight = FontWeight.Bold); content() }
    }
}

@Composable
private fun ReportsPane(p: Perms) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("open") }
    var list by remember { mutableStateOf<List<JSONObject>?>(null) }
    var msg by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    var openUid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(status, tick) {
        list = null
        val r = api("GET", "/admin/reports?status=$status")
        if (r.code == 200) { val a = r.json?.optJSONArray("reports"); list = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) } } else { list = emptyList(); msg = err(r) }
    }
    fun resolve(id: String, action: String) { scope.launch {
        val r = api("POST", "/admin/reports/$id/resolve", JSONObject().put("action", action).put("days", 0))
        msg = if (r.code == 200) { tick++; "เรียบร้อย" } else err(r)
    } }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("open" to "รอตรวจ", "actioned" to "ดำเนินการแล้ว", "dismissed" to "ยกเลิก", "all" to "ทั้งหมด").forEach { (k, l) ->
                FilterChip(selected = status == k, onClick = { status = k }, label = { Text(l) }) }
        }
        if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 13.sp)
        val l = list
        if (l == null) LinearProgressIndicator(color = Pink500, modifier = Modifier.fillMaxWidth())
        else if (l.isEmpty()) Text("ไม่มีรายงาน", color = Slate400, modifier = Modifier.padding(16.dp))
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(l, key = { it.optString("id") }) { r ->
                Section("${if (r.optString("targetType") == "post") "📝 โพสต์" else if (r.optString("targetType") == "comment") "💬 คอมเมนต์" else "👤 ผู้ใช้"} · ${ts(r.optLong("createdAt"))}") {
                    Text("เหตุผล: ${r.optString("reason")}", color = White, fontSize = 13.sp)
                    if (r.optString("preview").isNotBlank()) Text("“${r.optString("preview")}”", color = Slate300, fontSize = 12.sp, maxLines = 3)
                    Text("ผู้ถูกรายงาน: ${r.optString("targetName").ifBlank { r.optString("targetUid") }} · โดย ${r.optString("reporterName").ifBlank { "-" }}", color = Slate400, fontSize = 11.sp,
                        modifier = Modifier.clickable { r.optString("targetUid").ifBlank { null }?.let { openUid = it } })
                    if (r.optString("status") == "open") Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { resolve(r.optString("id"), "dismiss") }) { Text("ยกเลิกรายงาน", color = White) }
                        if (r.optString("targetType") == "post") Button(onClick = { resolve(r.optString("id"), "remove_post") }, colors = ButtonDefaults.buttonColors(containerColor = Amber500)) { Text("ลบโพสต์") }
                        if (p.has("ban_users") && r.optString("targetUid").isNotBlank()) Button(onClick = { resolve(r.optString("id"), "ban") }, colors = ButtonDefaults.buttonColors(containerColor = Rose500)) { Text("แบนถาวร") }
                    } else Text("สถานะ: ${r.optString("status")}", color = Slate500, fontSize = 11.sp)
                }
            }
        }
    }
    openUid?.let { UserProfileDialog(it) { openUid = null } }
}

/** One menu entry of the console: a group of related tools (sub-tabs), shown only when the role has at least one of them. */
private data class AdminSection(val key: String, val icon: String, val title: String, val desc: String, val accent: Color, val subs: List<Pair<String, String>>, val note: String = "", val badge: String = "")

private fun adminSections(p: Perms): List<AdminSection> {
    val content = p.has("manage_content"); val cfg = p.has("config_edit")
    fun subs(vararg x: Pair<Pair<String, String>, Boolean>) = x.filter { it.second }.map { it.first }
    return listOf(
        AdminSection("overview", "📊", "ภาพรวม", "สถิติผู้ใช้ งานที่รอ และสิทธิ์ของคุณ", Color(0xFF3D8BFF), subs(("overview" to "ภาพรวม") to true)),
        AdminSection("people", "👥", "ผู้ใช้ & ทีมงาน", "ค้นหาผู้ใช้ แบน ให้ VIP/เหรียญ ตั้งสิทธิ์แอดมิน", Color(0xFF9B3DFF), subs(("members" to "สมาชิก & สิทธิ์") to true)),
        AdminSection("safety", "🛡", "ตรวจสอบ & ใบสมัคร", "รายงานจากผู้ใช้ และใบสมัครเพื่อนคุย/ครีเอเตอร์", Color(0xFFFF4D6D),
            subs(("reports" to "รายงาน") to content, ("applications" to "ใบสมัคร") to content), badge = "openReports"),
        AdminSection("money", "💳", "การเงิน & ราคา", "เติมเพชร/เหรียญให้ผู้ใช้ ยืนยันการโอน ตั้งราคาแพ็ก VIP และค่าเพื่อนคุย", Color(0xFF10B981),
            subs(("grant" to "เติมให้ผู้ใช้") to p.has("coins_confirm"), ("topup" to "เติมเงิน (โอน)") to p.has("coins_confirm"), ("packs" to "ราคาแพ็กเพชร/เหรียญ") to cfg, ("rates" to "ค่าเพื่อนคุย") to cfg, ("vipplans" to "แพ็กเกจ VIP") to cfg),
            note = com.example.ui.social.AD_REVENUE_NOTE, badge = "pendingCoinRequests"),
        AdminSection("ads", "📢", "โฆษณา & โควตา", "จุดวางแบนเนอร์ การ์ด VIP โควตาฟรี/VIP และโฆษณารับรางวัล", Color(0xFFFF9F43),
            subs(("placements" to "ตำแหน่งโฆษณา & การ์ด VIP") to cfg, ("limits" to "โควตา & โฆษณา") to cfg)),
        AdminSection("gifts", "🎁", "ของขวัญ & ตกแต่ง", "ของขวัญ สติกเกอร์ และธีม/กรอบ/เอฟเฟกต์ห้อง VIP", Color(0xFFFF2FA8),
            subs(("gifts" to "ของขวัญ & สติกเกอร์") to content, ("decor" to "ตกแต่งห้อง VIP") to cfg)),
        AdminSection("look", "🎨", "หน้าตาแอป", "UI Builder รูปตัวอย่าง AI และป้าย 'เร็ว ๆ นี้'", Color(0xFF6D5BFF),
            subs(("uibuilder" to "UI Builder") to cfg, ("showcase" to "รูปตัวอย่าง AI") to cfg, ("comingsoon" to "ป้าย 'เร็ว ๆ นี้'") to cfg)),
        AdminSection("bots", "🤖", "บอท AI", "ตัวละคร AI สำหรับคุยและเติมคนใกล้คุณ", Color(0xFF00B8D9), subs(("bots" to "บอท AI") to cfg)),
        AdminSection("system", "⚙️", "ระบบ", "ล็อกแอปทั้งหมด หรือปิดบางฟีเจอร์ชั่วคราว", Color(0xFF94A3B8), subs(("system" to "ล็อกแอป & ฟีเจอร์") to p.has("app_lock")))
    ).filter { it.subs.isNotEmpty() }
}

/** Console home: things waiting for action first, then every tool group as a card. */
@Composable
private fun AdminHome(sections: List<AdminSection>, o: JSONObject?, p: Perms, open: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val todo = listOfNotNull(
            o?.optInt("openReports")?.takeIf { it > 0 && p.has("manage_content") }?.let { Triple("🚨 รายงานรอตรวจ", it, "safety") },
            o?.optInt("pendingCoinRequests")?.takeIf { it > 0 && p.has("coins_confirm") }?.let { Triple("💸 การโอนรอยืนยัน", it, "money") },
            o?.optInt("pendingAgeRequests")?.takeIf { it > 0 }?.let { Triple("🪪 คำขอยืนยันอายุ", it, "people") })
        Text(if (todo.isEmpty()) "ไม่มีงานค้าง 🎉" else "งานที่รอคุณ", color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        todo.forEach { (l, n, k) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Amber400.copy(alpha = 0.12f)).border(1.dp, Amber400.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .clickable { open(k) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l, color = White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("$n", color = Amber400, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp); Text("  ›", color = Slate400)
            }
        }
        Text("เครื่องมือ", color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        sections.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { s ->
                    val n = if (s.badge.isNotBlank()) o?.optInt(s.badge) ?: 0 else 0
                    Column(Modifier.weight(1f).heightIn(min = 120.dp).clip(RoundedCornerShape(18.dp)).background(Slate900).border(1.dp, Slate800, RoundedCornerShape(18.dp))
                        .clickable { open(s.key) }.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(s.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Text(s.icon, fontSize = 20.sp) }
                            Spacer(Modifier.weight(1f))
                            if (n > 0) Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Rose500).padding(horizontal = 7.dp, vertical = 2.dp)) { Text("$n", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(s.title, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.desc, color = Slate400, fontSize = 11.sp, maxLines = 3, lineHeight = 15.sp)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SystemPane() {
    val scope = rememberCoroutineScope()
    val l = com.example.ui.components.AppGates.lock
    LaunchedEffect(Unit) { com.example.ui.components.AppGates.start() }
    var message by remember(l.message) { mutableStateOf(l.message) }
    var image by remember(l.imageUrl) { mutableStateOf(l.imageUrl) }
    var msg by remember { mutableStateOf("") }
    var log by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { com.example.ui.components.AppGates.start(); val r = api("GET", "/admin/lock/log"); if (r.code != 200) msg = "โหลดประวัติไม่ได้: " + err(r); val a = r.json?.optJSONArray("log"); log = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) } }
    fun set(locked: Boolean) { scope.launch {
        val r = api("PUT", "/admin/lock", JSONObject().put("locked", locked).put("message", message).put("imageUrl", image.trim()))
        msg = if (r.code == 200) { tick++; r.json?.let { j -> com.example.ui.components.AppGates.lock = com.example.ui.components.AppGates.lock.copy(locked = j.optBoolean("locked"), message = j.optString("message"), imageUrl = j.optString("imageUrl")) }; if (locked) "ล็อกแอปแล้ว — ผู้ใช้ทั่วไปเข้าไม่ได้" else "ปลดล็อกแล้ว" } else err(r)
    } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(18.dp), color = if (l.locked) Rose500.copy(alpha = 0.18f) else Slate900, border = BorderStroke(2.dp, if (l.locked) Rose500 else Slate700)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (l.locked) "🔒" else "🔓", fontSize = 36.sp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("ล็อกแอป / Lock app", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text(if (l.locked) "กำลังล็อก: ผู้ใช้ทั่วไปเห็นหน้าล็อก (แอดมินเข้าได้)" else "แอปเปิดใช้งานตามปกติ", color = Slate300, fontSize = 12.sp)
                }
                Switch(checked = l.locked, onCheckedChange = { set(it) })
            }
        }
        OutlinedTextField(value = message, onValueChange = { message = it.take(300) }, label = { Text("ข้อความบนหน้าล็อก") }, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
        OutlinedTextField(value = image, onValueChange = { image = it.take(500) }, label = { Text("รูปบนหน้าล็อก (ลิงก์ https, ไม่บังคับ)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
        if (l.locked) Button(onClick = { set(true) }) { Text("อัปเดตข้อความ/รูป") }
        if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 13.sp)
        Text("ระบบบังคับฝั่งเซิร์ฟเวอร์: API ปฏิเสธผู้ใช้ทั่วไป (423) และกฎฐานข้อมูลห้ามอ่าน/เขียนข้อมูลโซเชียลขณะล็อก", color = Slate400, fontSize = 11.sp)
        FeatureLocksCard { tick++ }
        Text("ประวัติการล็อก/ปลดล็อก", color = White, fontWeight = FontWeight.Bold)
        if (log.isEmpty()) Text("ยังไม่มี", color = Slate400, fontSize = 12.sp)
        log.forEach { x -> Text("${when (x.optString("action")) { "app_lock" -> "🔒 ล็อก"; "app_feature_locks" -> "🧩 ฟีเจอร์ที่ปิด: " + x.optString("message"); else -> "🔓 ปลดล็อก" }} · ${ts(x.optLong("at"))} · โดย ${x.optString("by").take(10)}…", color = Slate300, fontSize = 12.sp) }
    }
}

@Composable
private fun ComingSoonList(p: Perms) {
    var edit by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { com.example.ui.components.AppGates.start() }
    Section("ป้าย 'เร็ว ๆ นี้' (Coming soon)") {
        if (!p.superadmin) { Text("เฉพาะซูเปอร์แอดมินเท่านั้นที่ตั้งค่าได้", color = Slate400, fontSize = 12.sp); return@Section }
        Text("เลือกหน้า/ส่วน แล้วตั้งรูป/สติกเกอร์/ข้อความ ผู้ใช้และแอดมินอื่นจะกดเข้าไม่ได้", color = Slate400, fontSize = 12.sp)
        com.example.ui.components.COMING_SOON_TARGETS.forEach { (k, l) ->
            val c = com.example.ui.components.AppGates.comingSoon[k]
            Row(Modifier.fillMaxWidth().clickable { edit = k }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l, color = White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(when { c == null -> "—"; c.enabled -> "เปิดอยู่ ${c.sticker}"; else -> "ปิด" }, color = if (c?.enabled == true) Amber400 else Slate500, fontSize = 12.sp)
                Text("  ›", color = Slate400)
            }
        }
    }
    edit?.let { com.example.ui.components.ComingSoonEditor(it) { edit = null } }
}


/** Per-feature on/off. OFF = feature still visible (greyed + 🔒) but unusable for non-staff; brain returns 423 for its endpoints. */
@Composable
private fun FeatureLocksCard(onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val locked = com.example.ui.components.AppGates.lockedParts
    var fmsg by remember(com.example.ui.components.AppGates.featureLockMsg) { mutableStateOf(com.example.ui.components.AppGates.featureLockMsg) }
    var status by remember { mutableStateOf("") }
    fun save(parts: Set<String>) { scope.launch {
        val pj = JSONObject(); parts.forEach { pj.put(it, true) }
        val r = api("PUT", "/admin/feature-locks", JSONObject().put("parts", pj).put("message", fmsg.trim()))
        status = if (r.code == 200) { com.example.ui.components.AppGates.lockedParts = parts; onChanged(); "บันทึกแล้ว — มีผลกับผู้ใช้ทันที" } else err(r)
    } }
    Surface(shape = RoundedCornerShape(18.dp), color = Slate900, border = BorderStroke(1.dp, Slate700)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("🧩 เปิด/ปิดฟีเจอร์ (ล็อกเฉพาะส่วน)", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text("ปิด = ผู้ใช้ยังเห็นเมนู/ไทล์ (สีจาง + 🔒) แต่ใช้ไม่ได้ แตะแล้วขึ้นแจ้งเตือน · เซิร์ฟเวอร์ปฏิเสธ API ของส่วนนั้น · แอดมินใช้ได้ปกติ · แยกจาก 'เร็ว ๆ นี้' (ภาพทับ)", color = Slate400, fontSize = 11.sp)
            com.example.ui.components.FEATURE_PARTS.forEach { (k, label) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text((if (k in locked) "🔒 " else "✅ ") + label, color = if (k in locked) Rose500 else White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(checked = k !in locked, onCheckedChange = { on -> save(if (on) locked - k else locked + k) })
                }
            }
            OutlinedTextField(value = fmsg, onValueChange = { fmsg = it.take(300) }, label = { Text("ข้อความแจ้งเมื่อแตะฟีเจอร์ที่ปิด (ไม่บังคับ)") }, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
            Button(onClick = { save(locked) }) { Text("บันทึกข้อความ") }
            if (status.isNotBlank()) Text(status, color = Amber400, fontSize = 12.sp)
        }
    }
}


@Composable
private fun AdPlacementsCard() {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { com.example.ads.AdPlacements.start() }
    val cur = com.example.ads.AdPlacements.cfg
    var every by remember(com.example.ads.AdPlacements.inFeedEvery) { mutableStateOf(com.example.ads.AdPlacements.inFeedEvery.toString()) }
    var status by remember { mutableStateOf("") }
    val labels = linkedMapOf("home" to "หน้าแรก (แบนเนอร์ล่าง)", "discover" to "หาเพื่อน", "chatList" to "รายการแชท", "video" to "วิดีโอ",
        "inFeed" to "ในฟีด คั่นระหว่างโพสต์", "chatRoom" to "แชท 1:1 (แบนเนอร์เล็กด้านบน)", "vipBanner" to "การ์ด 'ปลดล็อก VIP' หน้าแรก (ใต้คนใกล้คุณ)")
    fun save(m: Map<String, Boolean>) {
        // Flip the switch right away (the RTDB listener may be slow or not attached yet), then confirm with the server's saved values.
        val before = com.example.ads.AdPlacements.cfg
        val full = com.example.ads.AdPlacements.DEFAULTS.mapValues { (k, d) -> m[k] ?: before[k] ?: d }
        com.example.ads.AdPlacements.cfg = full; status = "กำลังบันทึก…"
        scope.launch {
            val j = JSONObject(); full.forEach { (k, v) -> j.put(k, v) }; j.put("inFeedEvery", every.toIntOrNull() ?: 5)
            val r = api("PUT", "/admin/ad-placements", j)
            if (r.code == 200) {
                r.json?.let { o -> com.example.ads.AdPlacements.cfg = com.example.ads.AdPlacements.DEFAULTS.mapValues { (k, d) -> if (o.has(k)) o.optBoolean(k) else full[k] ?: d }
                    com.example.ads.AdPlacements.inFeedEvery = o.optInt("inFeedEvery", 5).coerceIn(2, 50) }
                status = "บันทึกแล้ว — มีผลทันที"
            } else { com.example.ads.AdPlacements.cfg = before; status = err(r) }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("📍 ตำแหน่งแบนเนอร์โฆษณา", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text("ซ่อนสำหรับ VIP · บัญชีแอดมินเห็นโฆษณาทดสอบเหมือนเดิม · ต้องเปิด 'แบนเนอร์' ในหน้าลิมิตด้วย · Interstitial ตั้งค่าที่หน้าลิมิต (ค่าเริ่มต้นปิด)", color = Slate400, fontSize = 11.sp)
        labels.forEach { (k, l) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(l, color = White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(checked = cur[k] == true, onCheckedChange = { save(cur + (k to it)) })
            }
        }
        OutlinedTextField(value = every, onValueChange = { every = it.filter(Char::isDigit).take(2) }, label = { Text("ในฟีด: แสดงทุก ๆ N โพสต์ (2–50)") }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
        Button(onClick = { save(cur) }) { Text("บันทึก") }
        if (status.isNotBlank()) Text(status, color = Amber400, fontSize = 12.sp)
    }
}

/** VIP packages users buy with diamonds (appConfig/vipPlans via PUT /admin/vip-plans). */
@Composable
private fun VipPlansPane() {
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(true) }
    val rows = remember { mutableStateListOf<Triple<String, String, String>>() } // id, days, price
    var status by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val r = api("GET", "/vip/plans")
        if (r.code == 200 && r.json != null) {
            enabled = r.json.optBoolean("enabled", true)
            val a = r.json.optJSONArray("plans"); rows.clear()
            for (i in 0 until (a?.length() ?: 0)) { val o = a!!.getJSONObject(i); rows.add(Triple(o.optString("id"), o.optInt("days").toString(), o.optInt("price").toString())) }
        } else status = if (r.code == 404) "เซิร์ฟเวอร์ยังไม่อัปเดต (404) — deploy friendtalk-brain ก่อน" else err(r)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("👑 แพ็กเกจ VIP (จ่ายด้วยเพชร)", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text("ผู้ใช้กด 'ปลดล็อก VIP' → เลือกแพ็กเกจ → หักเพชรแล้วเป็น VIP ทันที (ถ้าเป็นอยู่แล้วจะต่อวันเพิ่ม) · เพชรเข้ากระเป๋าระบบ · สูงสุด 6 แพ็กเกจ", color = Slate400, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("เปิดให้สมัครในแอป", color = White, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        rows.forEachIndexed { i, (id, days, price) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(days, { v -> rows[i] = Triple(id, v.filter(Char::isDigit).take(3), price) }, label = { Text("วัน") }, singleLine = true, modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                OutlinedTextField(price, { v -> rows[i] = Triple(id, days, v.filter(Char::isDigit).take(7)) }, label = { Text("ราคา 💎") }, singleLine = true, modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                TextButton(onClick = { rows.removeAt(i) }) { Text("ลบ", color = Rose500) }
            }
        }
        if (rows.size < 6) OutlinedButton(onClick = { rows.add(Triple("p" + System.currentTimeMillis().toString(36).takeLast(5), "30", "990")) }) { Text("+ เพิ่มแพ็กเกจ", color = White) }
        Button(onClick = {
            status = "กำลังบันทึก…"
            scope.launch {
                val a = org.json.JSONArray(); rows.forEach { (id, d, p) -> a.put(JSONObject().put("id", id).put("days", d.toIntOrNull() ?: 0).put("price", p.toIntOrNull() ?: 0)) }
                val r = api("PUT", "/admin/vip-plans", JSONObject().put("enabled", enabled).put("plans", a))
                status = if (r.code == 200) "บันทึกแล้ว — ผู้ใช้เห็นราคาใหม่ทันที" else err(r)
            }
        }, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("บันทึก") }
        if (status.isNotBlank()) Text(status, color = Amber400, fontSize = 12.sp)
    }
}

/** Admin: give diamonds / coins to any user (search → pick → amount → confirm). Logged in adminCoinLedger, user gets a push. */
@Composable
private fun GrantPane() {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var picked by remember { mutableStateOf<JSONObject?>(null) }
    var cur by remember { mutableStateOf("diamonds") }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("แอดมินเติมให้") }
    var confirm by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var ledger by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { val r = api("GET", "/admin/coin-ledger"); val a = r.json?.optJSONArray("items"); ledger = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) } }
    fun search() { scope.launch {
        val r = api("GET", "/admin/users?q=" + android.net.Uri.encode(q.trim()) + "&role=all&offset=0")
        val a = r.json?.optJSONArray("users"); results = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }.take(8)
        status = if (r.code != 200) err(r) else if (results.isEmpty()) "ไม่พบผู้ใช้" else ""
    } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("🎁 เติมเพชร / เหรียญให้ผู้ใช้", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text("ใช้ตอนผู้ใช้จ่ายนอกแอป แจกรางวัล หรือชดเชย · ทุกครั้งถูกบันทึกพร้อมชื่อแอดมินและเหตุผล · ผู้ใช้ได้รับแจ้งเตือน", color = Slate400, fontSize = 11.sp)
        val u = picked
        if (u == null) {
            OutlinedTextField(q, { q = it.take(60) }, singleLine = true, placeholder = { Text("ชื่อ, @username หรือ UID", color = Slate400) },
                trailingIcon = { TextButton(onClick = { search() }) { Text("ค้นหา", color = Pink400) } }, shape = RoundedCornerShape(14.dp),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { search() }), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White), modifier = Modifier.fillMaxWidth())
            results.forEach { x ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Slate900).clickable { picked = x; status = "" }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(x.optString("avatar"), x.optString("displayName").ifBlank { "?" }, 36); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(x.optString("displayName").ifBlank { "(ไม่มีชื่อ)" }, color = White, fontWeight = FontWeight.Bold)
                        Text((x.optString("username").takeIf { it.isNotBlank() }?.let { "@$it · " } ?: "") + x.optString("uid").take(10) + "…", color = Slate400, fontSize = 11.sp)
                    }
                    Text("เลือก ›", color = Pink400, fontSize = 13.sp)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Slate900).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(u.optString("avatar"), u.optString("displayName").ifBlank { "?" }, 42); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(u.optString("displayName").ifBlank { "(ไม่มีชื่อ)" }, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(u.optString("uid").take(14) + "…", color = Slate400, fontSize = 11.sp)
                }
                TextButton(onClick = { picked = null; amount = "" }) { Text("เปลี่ยน", color = Pink400) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = cur == "diamonds", onClick = { cur = "diamonds" }, label = { Text("💎 เพชร") })
                FilterChip(selected = cur == "coins", onClick = { cur = "coins" }, label = { Text("🪙 เหรียญ") })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(100, 300, 500, 1000, 5000).forEach { n -> FilterChip(selected = amount == n.toString(), onClick = { amount = n.toString() }, label = { Text("+$n") }) }
            }
            OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '-' }.take(7) }, label = { Text("จำนวน (ติดลบ = หักออก เฉพาะซูเปอร์แอดมิน)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
            OutlinedTextField(reason, { reason = it.take(200) }, label = { Text("เหตุผล (บันทึกในสมุดบัญชี)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
            Button(onClick = { if ((amount.toIntOrNull() ?: 0) == 0) status = "ใส่จำนวนก่อน" else if (reason.trim().length < 3) status = "ใส่เหตุผลอย่างน้อย 3 ตัวอักษร" else confirm = true },
                modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Pink500)) {
                Text((if ((amount.toIntOrNull() ?: 0) < 0) "หัก " else "เติม ") + (if (cur == "diamonds") "💎 " else "🪙 ") + amount.removePrefix("-").ifBlank { "0" }, fontWeight = FontWeight.Bold)
            }
            if (confirm) AlertDialog(onDismissRequest = { confirm = false },
                title = { Text("ยืนยันการ" + if ((amount.toIntOrNull() ?: 0) < 0) "หัก" else "เติม") },
                text = { Text("${u.optString("displayName")} จะ" + (if ((amount.toIntOrNull() ?: 0) < 0) "ถูกหัก " else "ได้รับ ") + (if (cur == "diamonds") "💎 " else "🪙 ") + amount.removePrefix("-") + "\nเหตุผล: " + reason) },
                confirmButton = { TextButton(onClick = {
                    confirm = false; status = "กำลังบันทึก…"
                    scope.launch {
                        val r = api("POST", "/admin/users/${u.optString("uid")}/coins", JSONObject().put("amount", amount.toInt()).put("reason", reason.trim()).put("currency", cur))
                        status = if (r.code == 200) "✅ สำเร็จ — ยอดใหม่ " + (if (cur == "diamonds") "💎 " else "🪙 ") + (r.json?.optLong(cur) ?: 0) else err(r)
                        if (r.code == 200) { amount = ""; tick++ }
                    }
                }) { Text("ยืนยัน") } },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("ยกเลิก") } })
        }
        if (status.isNotBlank()) Text(status, color = Amber400, fontSize = 13.sp)
        HorizontalDivider(color = Slate800, modifier = Modifier.padding(vertical = 4.dp))
        Text("ประวัติการเติมล่าสุด", color = White, fontWeight = FontWeight.Bold)
        if (ledger.isEmpty()) Text("ยังไม่มี", color = Slate400, fontSize = 12.sp)
        ledger.forEach { x ->
            val n = x.optInt("amount"); val c = if (x.optString("currency") == "diamonds") "💎" else "🪙"
            Text("${if (n > 0) "+" else ""}$n $c · ${x.optString("name")} · ${x.optString("reason")} · ${ts(x.optLong("at"))}", color = if (n > 0) Emerald400 else Rose500, fontSize = 12.sp)
        }
    }
}
