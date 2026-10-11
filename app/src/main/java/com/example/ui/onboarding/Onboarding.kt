package com.example.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.example.ui.components.ft.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.BrainApi
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray?.strs(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }

/** Server-driven first-login wizard. Gates the app until /onboarding reports complete. */
@Composable
fun OnboardingGate(user: FirebaseUser?, content: @Composable () -> Unit) {
    if (user == null) { content(); return }
    var state by remember(user.uid) { mutableStateOf<JSONObject?>(null) }
    var failed by remember(user.uid) { mutableStateOf(false) }
    LaunchedEffect(user.uid) {
        val r = BrainApi.call(user, "GET", "/onboarding")
        if (r.code == 200) state = r.json else failed = true
    }
    val s = state
    if (failed || (s != null && s.optBoolean("complete"))) { content(); return }
    Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding(), color = MaterialTheme.colorScheme.background) {
        if (s == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (s.optBoolean("minor")) Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("FriendTalk สำหรับผู้มีอายุ 18 ปีขึ้นไปเท่านั้น", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        } else Wizard(user, s) { state = it }
    }
}

@Composable
private fun Wizard(user: FirebaseUser, st: JSONObject, onState: (JSONObject) -> Unit) {
    val steps = st.optJSONArray("steps").strs(); val next = st.optString("next")
    val idx = steps.indexOf(next).coerceAtLeast(0)
    val scope = rememberCoroutineScope()
    var err by remember(next) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var fields by remember { mutableStateOf<JSONObject?>(null) }
    var terms by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) {
        fields = BrainApi.call(user, "GET", "/onboarding/fields").json
        terms = BrainApi.call(user, "GET", "/onboarding/terms").json
    }
    fun submit(body: JSONObject) { scope.launch {
        busy = true; err = ""
        val r = BrainApi.call(user, "POST", "/onboarding/$next", body)
        busy = false
        if (r.code == 200 && r.json != null) onState(r.json!!)
        else if (r.json?.optString("code") == "minor") onState(JSONObject(st.toString()).put("minor", true))
        else err = r.json?.optString("error")?.ifBlank { null } ?: "ผิดพลาด (${r.code})"
    } }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ตั้งค่าบัญชี ${idx + 1}/${steps.size}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        LinearProgressIndicator(progress = { (idx + 1f) / steps.size.coerceAtLeast(1) }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
        if (err.isNotBlank()) Text(err, color = Color(0xFFD32F2F))
        Box(Modifier.weight(1f)) {
            val f = fields
            when (next) {
                "signin" -> Button(onClick = { submit(JSONObject()) }) { Text("ดำเนินการต่อ") }
                "terms" -> terms?.let { TermsStep(it, busy) { submit(JSONObject().put("accept", true).put("scrolledToEnd", true).put("version", it.optInt("version"))) } }
                "profile" -> f?.let { ProfileStep(it.optJSONArray("profile")!!, busy, ::submit) }
                "interests" -> f?.let { PickStep(listOf(it.optJSONObject("interests")!!), busy, ::submit) }
                "looking_for" -> f?.let { PickStep(listOf(it.optJSONObject("lookingFor")!!), busy, ::submit) }
                "details" -> f?.let { val a = it.optJSONArray("details")!!; PickStep((0 until a.length()).map { i -> a.getJSONObject(i) }, busy, ::submit) }
                else -> Button(onClick = { submit(JSONObject()) }) { Text("ดำเนินการต่อ") }
            }
        }
    }
}

@Composable
private fun TermsStep(t: JSONObject, busy: Boolean, onAccept: () -> Unit) {
    val sc = rememberScrollState(); var checked by remember { mutableStateOf(false) }
    val atEnd = sc.maxValue == 0 || sc.value >= sc.maxValue - 4
    var reached by remember { mutableStateOf(false) }; if (atEnd) reached = true
    Column(Modifier.fillMaxSize()) {
        Text("กฎและข้อตกลงการใช้งาน (เวอร์ชัน ${t.optInt("version")})", fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(sc).padding(vertical = 8.dp)) {
            t.optJSONArray("rules").strs().forEachIndexed { i, r -> Text("${i + 1}. $r", Modifier.padding(vertical = 6.dp)) }
            Text("— จบ —", Modifier.padding(16.dp))
        }
        if (!reached) Text("เลื่อนอ่านจนจบก่อน", color = Color(0xFFD32F2F), fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked, { checked = it }, enabled = reached); Text("ฉันอ่านและยอมรับกฎทั้งหมด")
        }
        Button(onClick = onAccept, enabled = reached && checked && !busy, modifier = Modifier.fillMaxWidth()) { Text("ยอมรับกฎ") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldPicker(f: JSONObject, sel: List<String>, onSel: (List<String>) -> Unit) {
    val multi = f.optString("type") == "multi"
    Text(f.optString("label") + " *" + if (multi) " (เลือกได้หลายข้อ)" else "", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        f.optJSONArray("options").strs().forEach { o ->
            FilterChip(selected = o in sel, onClick = {
                onSel(if (multi) (if (o in sel) sel - o else sel + o) else listOf(o))
            }, label = { Text(o) })
        }
    }
    if (sel.isEmpty()) Text("จำเป็นต้องเลือก", color = Color(0xFFD32F2F), fontSize = 11.sp)
}

@Composable
private fun PickStep(defs: List<JSONObject>, busy: Boolean, submit: (JSONObject) -> Unit) {
    val sel = remember(defs) { mutableStateMapOf<String, List<String>>() }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            defs.forEach { f -> val k = f.optString("key"); FieldPicker(f, sel[k] ?: emptyList()) { sel[k] = it } }
        }
        val ok = defs.all { !sel[it.optString("key")].isNullOrEmpty() }
        Button(onClick = {
            val b = JSONObject(); defs.forEach { f -> val k = f.optString("key"); val v = sel[k]!!
                if (f.optString("type") == "multi") b.put(k, JSONArray(v)) else b.put(k, v.first()) }
            submit(b)
        }, enabled = ok && !busy, modifier = Modifier.fillMaxWidth()) { Text("ถัดไป") }
    }
}

@Composable
private fun ProfileStep(defsA: JSONArray, busy: Boolean, submit: (JSONObject) -> Unit) {
    val defs = (0 until defsA.length()).map { defsA.getJSONObject(it) }
    var real by remember { mutableStateOf("") }; var nick by remember { mutableStateOf("") }; var age by remember { mutableStateOf("") }
    val sel = remember { mutableStateMapOf<String, List<String>>() }
    val a = age.toIntOrNull()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            OutlinedTextField(real, { real = it.take(80) }, label = { Text("ชื่อจริง * (ไม่แสดงต่อผู้อื่น)") }, isError = real.trim().length < 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(nick, { nick = it.take(30) }, label = { Text("ชื่อเล่น (ไม่ซ้ำ) *") }, isError = nick.trim().length < 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(age, { age = it.filter(Char::isDigit).take(3) }, label = { Text("อายุ * (18+)") }, isError = a == null || a < 18, modifier = Modifier.fillMaxWidth())
            if (a != null && a < 18) Text("ต้องมีอายุ 18 ปีขึ้นไป", color = Color(0xFFD32F2F), fontSize = 11.sp)
            defs.forEach { f -> val k = f.optString("key"); FieldPicker(f, sel[k] ?: emptyList()) { sel[k] = it } }
        }
        val ok = real.trim().length >= 2 && nick.trim().length >= 2 && a != null && defs.all { !sel[it.optString("key")].isNullOrEmpty() }
        Button(onClick = {
            val b = JSONObject().put("realName", real.trim()).put("displayName", nick.trim()).put("age", a)
            defs.forEach { f -> b.put(f.optString("key"), sel[f.optString("key")]!!.first()) }
            submit(b)
        }, enabled = ok && !busy, modifier = Modifier.fillMaxWidth()) { Text("ถัดไป") }
    }
}
