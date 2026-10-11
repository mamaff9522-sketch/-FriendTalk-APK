package com.example.ui.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.network.BrainApi
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONObject

/** ถอนเงิน: earned diamonds only. Staff see the payout queue (superadmin approves). */
@Composable
fun WithdrawDialog(isStaff: Boolean, onDismiss: () -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser ?: return onDismiss()
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var msg by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) { data = BrainApi.call(user, "GET", "/withdraw").json }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Row { Text("💸 ถอนเงิน", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f)); TextButton(onDismiss) { Text("ปิด") } }
                if (isStaff) TabRow(tab) { Tab(tab == 0, { tab = 0 }) { Text("ของฉัน", Modifier.padding(8.dp)) }; Tab(tab == 1, { tab = 1 }) { Text("คำขอถอน (แอดมิน)", Modifier.padding(8.dp)) } }
                if (msg.isNotBlank()) Text(msg, color = Color(0xFFD32F2F))
                if (tab == 1) { AdminQueue { msg = it }; return@Column }
                val d = data ?: run { CircularProgressIndicator(); return@Column }
                val c = d.optJSONObject("config") ?: JSONObject()
                Text("เพชรที่ได้รับ (ถอนได้): 💎 ${d.optInt("earned")}   กันไว้: ${d.optInt("held")}")
                Text("อัตรา 1 💎 = ${c.optDouble("rateTHB")} บาท • ขั้นต่ำ ${c.optInt("minDiamonds")} 💎 • ถอนได้วันที่ ${c.optInt("windowStartDay")}-${c.optInt("windowEndDay")} ของเดือน (ยอดไม่ถึงสะสมยกไปเดือนถัดไป)", fontSize = 12.sp)
                Text("ถอนได้เฉพาะเพชรที่ได้รับจากของขวัญ/เพื่อนคุย (หลังหักส่วนแบ่งแพลตฟอร์ม 20%) ไม่รวมเพชรที่ซื้อ", fontSize = 12.sp)
                if (!d.optBoolean("windowOpen")) Text("ยังไม่อยู่ในช่วงเวลาถอน", color = Color(0xFFD32F2F))
                var amount by remember { mutableStateOf("") }; var bank by remember { mutableStateOf("") }
                var method by remember { mutableStateOf("promptpay") }; var acct by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }
                Row { FilterChip(method == "promptpay", { method = "promptpay" }, { Text("พร้อมเพย์") }); Spacer(Modifier.width(8.dp)); FilterChip(method == "bank", { method = "bank" }, { Text("บัญชีธนาคาร") }) }
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("จำนวนเพชร") }, modifier = Modifier.fillMaxWidth())
                if (method == "bank") OutlinedTextField(bank, { bank = it }, label = { Text("ธนาคาร") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(acct, { acct = it }, label = { Text(if (method == "bank") "เลขบัญชี" else "เบอร์/เลขบัตรพร้อมเพย์") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text("ชื่อบัญชี") }, modifier = Modifier.fillMaxWidth())
                Text("ข้อมูลบัญชีเก็บเป็นส่วนตัว เห็นเฉพาะคุณและแอดมิน", fontSize = 11.sp)
                Button(onClick = { scope.launch {
                    val r = BrainApi.call(user, "POST", "/withdraw", JSONObject().put("amount", amount.toIntOrNull() ?: 0).put("method", method).put("account", acct).put("accountName", name).put("bank", bank))
                    msg = if (r.code == 200) "ส่งคำขอแล้ว (${r.json?.optString("status")}) ≈ ${r.json?.optDouble("thb")} บาท" else r.json?.optString("error") ?: "ผิดพลาด ${r.code}"
                    reload++
                } }, modifier = Modifier.fillMaxWidth()) { Text("ขอถอน") }
                Text("ประวัติ", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                val reqs = d.optJSONArray("requests")
                for (i in 0 until (reqs?.length() ?: 0)) { val x = reqs!!.getJSONObject(i)
                    Text("💎 ${x.optInt("amount")} ≈ ${x.optDouble("thb")} ฿ • ${x.optString("status")} ${x.optString("reason")}", fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun AdminQueue(onMsg: (String) -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser ?: return
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<org.json.JSONArray?>(null) }; var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) { val r = BrainApi.call(user, "GET", "/admin/withdrawals?status=pending"); items = r.json?.optJSONArray("items"); if (r.code != 200) onMsg(r.json?.optString("error") ?: "ไม่มีสิทธิ์") }
    val a = items ?: return
    if (a.length() == 0) Text("ไม่มีคำขอค้าง")
    for (i in 0 until a.length()) { val x = a.getJSONObject(i); val p = x.optJSONObject("payout") ?: JSONObject()
        var ref by remember(x.optString("id")) { mutableStateOf("") }
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Column(Modifier.padding(10.dp)) {
            Text("${x.optString("name")} • 💎 ${x.optInt("amount")} ≈ ${x.optDouble("thb")} ฿ • ${x.optString("status")} • risk ${x.optInt("risk")}", fontWeight = FontWeight.SemiBold)
            Text("${p.optString("method")} ${p.optString("bank")} ${p.optString("account")} (${p.optString("accountName")})", fontSize = 12.sp)
            OutlinedTextField(ref, { ref = it }, label = { Text("เลขอ้างอิงสลิป / เหตุผลปฏิเสธ") }, modifier = Modifier.fillMaxWidth())
            Row {
                Button(onClick = { scope.launch { val r = BrainApi.call(user, "POST", "/admin/withdrawals/${x.optString("id")}/decide", JSONObject().put("paid", true).put("ref", ref)); onMsg(r.json?.optString("error") ?: r.json?.optString("status") ?: ""); reload++ } }) { Text("โอนแล้ว (superadmin)") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { scope.launch { val r = BrainApi.call(user, "POST", "/admin/withdrawals/${x.optString("id")}/decide", JSONObject().put("paid", false).put("reason", ref)); onMsg(r.json?.optString("error") ?: r.json?.optString("status") ?: ""); reload++ } }) { Text("ปฏิเสธ") }
            }
        } }
    }
}
