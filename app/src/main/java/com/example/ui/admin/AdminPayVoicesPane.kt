package com.example.ui.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.social.PayOpenRepo
import com.example.social.PayVoice
import com.example.ui.payopen.PayVoicePlayer
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * จัดการเสียงเปย์เปิดใจ: every line, split into female / male, each with ▶ play, edit text, upload a new audio file and delete.
 * Lines without a file are spoken by the phone. Changes are live for every user (no APK update). No per-line on/off switch.
 */
@Composable
fun AdminPayVoicesPane() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<PayVoice>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<PayVoice?>(null) }
    var del by remember { mutableStateOf<PayVoice?>(null) }
    var uploadFor by remember { mutableStateOf<PayVoice?>(null) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(tick) { list = PayOpenRepo.adminVoices() }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val v = uploadFor; uploadFor = null
        if (uri == null || v == null) return@rememberLauncherForActivityResult
        val mime = ctx.contentResolver.getType(uri)?.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"
        val b = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        when {
            b == null -> msg = "อ่านไฟล์เสียงไม่ได้"
            b.size > 2 * 1024 * 1024 -> msg = "ไฟล์เสียงต้องไม่เกิน 2MB (ตอนนี้ ${b.size / 1024}KB)"
            else -> scope.launch { msg = "กำลังอัปโหลด…"; msg = PayOpenRepo.adminUploadVoice(v.id, b, mime).msg; PayVoicePlayer.voices(force = true); tick++ }
        }
    }

    Text("🔊 จัดการเสียงเปย์เปิดใจ", color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    Text("เสียงตอนของขวัญมาถึง (สายเปย์มาแล้ว~) และเสียงตอนคืนเพชร (สุ่มเล่น · ผู้ส่งชายได้ยินเสียงหญิง · ผู้ส่งหญิงได้ยินเสียงชาย). " +
        "ถ้ายังไม่อัปโหลดไฟล์ เครื่องจะอ่านข้อความให้เอง · แก้แล้วมีผลทันที ไม่ต้องอัปเดตแอป", color = Slate400, fontSize = 11.sp)
    if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { edit = PayVoice("v" + System.currentTimeMillis().toString(36), "", "female", "refund", "", -1) }, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("+ เพิ่มเสียงหญิง") }
        Button(onClick = { edit = PayVoice("v" + System.currentTimeMillis().toString(36), "", "male", "refund", "", -1) }, colors = ButtonDefaults.buttonColors(containerColor = Cyan400)) { Text("+ เพิ่มเสียงชาย", color = androidx.compose.ui.graphics.Color.Black) }
    }
    val l = list
    if (l == null) CircularProgressIndicator(color = Pink500)
    else if (l.isEmpty()) Text("ยังไม่มีเสียง (หรือเซิร์ฟเวอร์ยังไม่อัปเดต)", color = Slate400, fontSize = 12.sp)
    else for ((g, label) in listOf("female" to "👩 เสียงผู้หญิง", "male" to "👨 เสียงผู้ชาย")) {
        val mine = l.filter { it.gender == g }
        Text("$label (${mine.size})", color = if (g == "female") Pink400 else Cyan400, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        mine.sortedWith(compareBy({ if (it.kind == "arrival") 0 else 1 }, { it.order })).forEach { v ->
            Surface(shape = RoundedCornerShape(12.dp), color = Slate800, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { PayVoicePlayer.play(ctx, v) }) { Text("▶", color = Emerald400, fontSize = 18.sp) }
                    Column(Modifier.weight(1f)) {
                        Text(v.text, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text((if (v.kind == "arrival") "ตอนของขวัญมาถึง" else "ตอนคืนเพชร") + " · " + if (v.audioUrl.isNotBlank()) "ไฟล์เสียงที่อัปโหลด" else "เครื่องอ่านข้อความ",
                            color = Slate400, fontSize = 11.sp)
                    }
                    TextButton(onClick = { edit = v }) { Text("แก้", fontSize = 12.sp) }
                    TextButton(onClick = { uploadFor = v; audioPicker.launch("audio/*") }) { Text("อัปโหลด", fontSize = 12.sp) }
                    TextButton(onClick = { del = v }) { Text("ลบ", color = Pink400, fontSize = 12.sp) }
                }
            }
        }
    }

    edit?.let { v ->
        var text by remember(v.id) { mutableStateOf(v.text) }
        var gender by remember(v.id) { mutableStateOf(v.gender) }
        var kind by remember(v.id) { mutableStateOf(v.kind) }
        var busy by remember(v.id) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { if (!busy) edit = null },
            title = { Text(if (v.text.isBlank()) "เพิ่มเสียง" else "แก้ไขข้อความ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { text = it.take(60) }, label = { Text("ข้อความ") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = gender == "female", onClick = { gender = "female" }, label = { Text("หญิง") })
                        FilterChip(selected = gender == "male", onClick = { gender = "male" }, label = { Text("ชาย") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = kind == "refund", onClick = { kind = "refund" }, label = { Text("ตอนคืนเพชร") })
                        FilterChip(selected = kind == "arrival", onClick = { kind = "arrival" }, label = { Text("ตอนของขวัญมาถึง") })
                    }
                    TextButton(onClick = { PayVoicePlayer.speak(ctx, text, gender) }, enabled = text.isNotBlank()) { Text("▶ ลองฟังเสียงเครื่องอ่าน") }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && text.isNotBlank(), onClick = {
                    busy = true
                    scope.launch {
                        val r = PayOpenRepo.adminSaveVoice(v.copy(text = text.trim(), gender = gender, kind = kind))
                        busy = false; msg = r.msg
                        if (r.ok) { edit = null; PayVoicePlayer.voices(force = true); tick++ }
                    }
                }) { Text(if (busy) "กำลังบันทึก…" else "บันทึก") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { edit = null }) { Text("ยกเลิก") } })
    }
    del?.let { v ->
        AlertDialog(onDismissRequest = { del = null }, title = { Text("ลบเสียง \"${v.text}\"?") },
            confirmButton = { TextButton(onClick = { scope.launch { msg = if (PayOpenRepo.adminDeleteVoice(v.id)) "ลบแล้ว" else "ลบไม่สำเร็จ"; del = null; PayVoicePlayer.voices(force = true); tick++ } }) { Text("ลบ", color = Pink400) } },
            dismissButton = { TextButton(onClick = { del = null }) { Text("ยกเลิก") } })
    }
}
