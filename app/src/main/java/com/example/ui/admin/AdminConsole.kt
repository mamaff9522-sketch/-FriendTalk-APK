package com.example.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
    LaunchedEffect(tick) {
        val r = api("GET", "/admin/overview")
        if (r.code == 200 && r.json != null) {
            val j = r.json; overview = j
            val pj = j.optJSONObject("yourPermissions"); val m = mutableMapOf<String, Boolean>()
            pj?.keys()?.forEach { m[it] = pj.optBoolean(it) }
            perms = Perms(j.optString("yourRole"), m)
        } else loadErr = err(r)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Slate950) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("คอนโซลแอดมิน", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        perms?.let { Text(ROLE_TH[it.role] ?: it.role, color = if (it.superadmin) Amber400 else Purple400, fontSize = 12.sp) }
                    }
                    TextButton(onClick = onDismiss) { Text("ปิด ✕", color = Slate300) }
                }
                val p = perms
                if (p == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { if (loadErr.isNotBlank()) Text(loadErr, color = Amber400) else CircularProgressIndicator(color = Pink500) }; return@Column }
                val tabs = buildList {
                    if (p.has("app_lock")) add("system" to "⚙️ ระบบ (ล็อกแอป)")
                    add("overview" to "📊 ภาพรวม"); add("members" to "👥 สมาชิก & สิทธิ์")
                    if (p.has("manage_content")) { add("reports" to "🚨 รายงาน"); add("applications" to "📝 ใบสมัคร") }
                    if (p.has("config_edit") || p.has("manage_content")) add("economy" to "💰 เศรษฐกิจ")
                    if (p.has("config_edit")) { add("content" to "🎨 เนื้อหา & UI"); add("bots" to "🤖 บอท AI") }
                }
                var tab by remember { mutableStateOf(if (p.has("app_lock")) "system" else "overview") }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEach { (k, l) -> FilterChip(selected = tab == k, onClick = { tab = k }, label = { Text(l) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Pink500, selectedLabelColor = White, labelColor = Slate300)) }
                }
                HorizontalDivider(color = Slate800, modifier = Modifier.padding(top = 6.dp))
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        "overview" -> OverviewPane(overview, p, onOpenApkDownload) { tick++ }
                        "members" -> MembersPane(p)
                        "reports" -> ReportsPane(p)
                        "applications" -> com.example.ui.social.AdminApplicationsPane()
                        "economy" -> EconomyPane(p)
                        "content" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("ปรับหน้าตาแอปทั้งหมดในที่เดียว: ลากวางหน้าแรก, ธีม & สี, จัด Feed, ชื่อแอป/คำโปรย, โหมดปิดปรับปรุง, ประวัติเวอร์ชัน", color = Slate300, fontSize = 13.sp)
                            Button(onClick = onOpenUiBuilder, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Purple600)) { Text("เปิด UI Builder") }
                            ComingSoonList(p)
                        }
                        "bots" -> com.example.ui.ai.AdminBotsTab()
                        "system" -> SystemPane()
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
    Dialog(onDismissRequest = { onClose(changed) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Slate950) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

@Composable
private fun EconomyPane(p: Perms) {
    val subs = buildList { if (p.has("config_edit")) { add("limits" to "ลิมิต & โฆษณา"); add("placements" to "ตำแหน่งโฆษณา"); add("rates" to "อัตราค่าเพื่อนคุย"); add("packs" to "ราคาแพ็กเพชร/เหรียญ") }; if (p.has("manage_content")) add("gifts" to "ของขวัญ & สติกเกอร์") }
    var sub by remember { mutableStateOf(subs.firstOrNull()?.first ?: "") }
    Column(Modifier.fillMaxSize()) {
        Text(com.example.ui.social.AD_REVENUE_NOTE, color = Slate400, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { subs.forEach { (k, l) -> FilterChip(selected = sub == k, onClick = { sub = k }, label = { Text(l) }) } }
        Box(Modifier.fillMaxSize()) { when (sub) { "limits" -> AdminLimitsTab(); "placements" -> AdPlacementsCard(); "rates" -> com.example.ui.social.AdminCompanionRatesPane(); "packs" -> com.example.ui.social.AdminShopPacksPane(); "gifts" -> AdminGiftsTab() } }
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
        "inFeed" to "ในฟีด คั่นระหว่างโพสต์", "chatRoom" to "แชท 1:1 (แบนเนอร์เล็กด้านบน)")
    fun save(m: Map<String, Boolean>) { scope.launch {
        val j = JSONObject(); m.forEach { (k, v) -> j.put(k, v) }; j.put("inFeedEvery", every.toIntOrNull() ?: 5)
        val r = api("PUT", "/admin/ad-placements", j); status = if (r.code == 200) "บันทึกแล้ว — มีผลทันที" else err(r)
    } }
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
