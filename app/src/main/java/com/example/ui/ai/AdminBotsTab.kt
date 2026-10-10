package com.example.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.BrainApi
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/** Admin: global AI-bot switch + per-bot switches (server checks config_edit permission). */
@Composable
fun AdminBotsTab() {
    val scope = rememberCoroutineScope()
    var globalOn by remember { mutableStateOf(false) }
    var bots by remember { mutableStateOf<List<BrainApi.AiBot>>(emptyList()) }
    var msg by remember { mutableStateOf("กำลังโหลด…") }

    suspend fun reload() {
        val fu = FirebaseAuth.getInstance().currentUser ?: run { msg = "ยังไม่ได้เข้าสู่ระบบ"; return }
        val r = BrainApi.adminBots(fu)
        if (r == null) msg = "โหลดไม่ได้ (ต้องมีสิทธิ์ config_edit)" else { globalOn = r.first; bots = r.second; msg = "" }
    }
    LaunchedEffect(Unit) { reload() }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = Slate900, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("เปิด/ปิดบอท AI", fontWeight = FontWeight.Bold, color = White, fontSize = 14.sp)
                        Text("ปิดแล้ว ระบบสุ่มจะไม่เจอบอท และแชทกับบอทจะใช้ไม่ได้", color = Slate400, fontSize = 11.sp)
                    }
                    Switch(checked = globalOn, onCheckedChange = { v ->
                        val fu = FirebaseAuth.getInstance().currentUser ?: return@Switch
                        scope.launch { if (BrainApi.setBotsEnabled(fu, v)) globalOn = v else msg = "บันทึกไม่สำเร็จ" }
                    })
                }
            }
        }
        if (msg.isNotBlank()) item { Text(msg, color = Slate300, fontSize = 12.sp) }
        items(bots, key = { it.id }) { b ->
            Surface(shape = RoundedCornerShape(12.dp), color = Slate800, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(b.name, color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp)); AiBadge()
                        }
                        Text(b.bio, color = Slate400, fontSize = 11.sp)
                    }
                    Switch(checked = b.enabled, onCheckedChange = { v ->
                        val fu = FirebaseAuth.getInstance().currentUser ?: return@Switch
                        scope.launch {
                            if (BrainApi.setBotEnabled(fu, b.id, v)) bots = bots.map { if (it.id == b.id) it.copy(enabled = v) else it }
                            else msg = "บันทึกไม่สำเร็จ"
                        }
                    })
                }
            }
        }
    }
}
