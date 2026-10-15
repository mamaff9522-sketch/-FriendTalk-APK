package com.example.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ads.AdPlacements
import com.example.ads.AdRoom
import com.example.ui.components.tr
import com.example.ui.theme.PureWhite
import kotlinx.coroutines.launch
import androidx.compose.material3.CircularProgressIndicator

private val VGold = Color(0xFFFFC94D)
private val VPink = Color(0xFFFF2FA8)
private val VViolet = Color(0xFF9B3DFF)

/**
 * 'ปลดล็อก VIP' card under คนใกล้คุณ on Home.
 * Shown only when the admin switch appConfig/adPlacements/vipBanner is on AND the server says this account is on the free tier
 * (/me/limits tier); VIP members never see it, even with the switch on.
 */
@Composable
fun HomeVipBanner() {
    LaunchedEffect(Unit) { AdPlacements.start(); if (AdRoom.limits.value == null) AdRoom.refresh() }
    val lim by AdRoom.limits.collectAsState()
    val l = lim ?: return                       // tier unknown yet → show nothing rather than flash it to a VIP
    if (!AdPlacements.on("vipBanner") || l.tier == "vip") return
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(14.dp, shape, ambientColor = VViolet, spotColor = VPink)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF2A0F55), Color(0xFF4A1470), Color(0xFF6A1478))))
            .border(1.dp, Brush.linearGradient(listOf(Color(0x99FF4FB8), Color(0x66B04DFF))), shape)
            .clickable { open = true }
    ) {
        // soft glow behind the crown
        Box(Modifier.align(Alignment.CenterEnd).offset(x = 30.dp).size(150.dp).background(Brush.radialGradient(listOf(Color(0x66FF4FB8), Color.Transparent))))
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0x33FFC94D), Color(0x33FF4FB8)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.WorkspacePremium, null, tint = VGold, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("ปลดล็อก "), color = PureWhite, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text("VIP", color = VGold, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                    Text(tr("เพิ่มโอกาสเจอเพื่อนใหม่แบบพิเศษ"), color = PureWhite.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(VPink, Color(0xFFD13CF0)))).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tr("ดูรายละเอียด"), color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Filled.ChevronRight, null, tint = PureWhite, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                listOf(tr("ไม่มีโฆษณา"), tr("โควตาเพิ่มขึ้น"), tr("ตกแต่งห้องแชท")).forEach { CheckLine(it) }
            }
        }
    }
    if (open) VipDetailsDialog { open = false }
}

@Composable
private fun CheckLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Check, null, tint = Color(0xFFFF9F43), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = PureWhite.copy(alpha = 0.9f), fontSize = 11.5.sp, maxLines = 1)
    }
}

/** What VIP really gives (all enforced by the server), and how to get it today. */
@Composable
fun VipDetailsDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1C0B3D), Color(0xFF34105A))))
                .border(1.dp, Color(0x55FF4FB8), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0x55FFC94D), Color.Transparent))), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.WorkspacePremium, null, tint = VGold, modifier = Modifier.size(46.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("สมาชิก "), color = PureWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("VIP", color = VGold, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            Text(tr("สิทธิพิเศษที่ได้รับทันทีเมื่อเป็น VIP"), color = PureWhite.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Benefit(Icons.Filled.Block, tr("ไม่มีโฆษณา"), tr("ไม่มีแบนเนอร์และโฆษณาคั่นทั้งแอป"))
            Benefit(Icons.Filled.Bolt, tr("โควตาสูงสุด"), tr("เรดาร์ เขย่า สุ่มแชท ค้นหา และคุยกับ AI ได้มากขึ้นหลายเท่าต่อวัน"))
            Benefit(Icons.Filled.AutoAwesome, tr("ตกแต่งห้องแชท VIP"), tr("12 ธีม กรอบ เอฟเฟกต์ บับเบิล สติกเกอร์ และรูปพื้นหลังของคุณเอง"))
            Spacer(Modifier.height(16.dp))
            // real plans from the server (appConfig/vipPlans), paid with diamonds
            val scope = rememberCoroutineScope()
            var info by remember { mutableStateOf<org.json.JSONObject?>(null) }
            var loadErr by remember { mutableStateOf("") }
            var wallet by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
            var sel by remember { mutableStateOf<String?>(null) }
            var busy by remember { mutableStateOf(false) }
            var msg by remember { mutableStateOf("") }
            var tick by remember { mutableIntStateOf(0) }
            LaunchedEffect(tick) {
                val u = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val r = u?.let { runCatching { com.example.network.BrainApi.call(it, "GET", "/vip/plans") }.getOrNull() }
                if (r?.code == 200) info = r.json else loadErr = if (r?.code == 404) tr("เซิร์ฟเวอร์ยังไม่อัปเดต (404)") else tr("โหลดแพ็กเกจไม่สำเร็จ")
                wallet = com.example.social.GiftRepo.wallet()
            }
            val df = remember { java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("th", "TH")) }
            val until = info?.optLong("vipUntil") ?: 0L
            if (info?.optString("tier") == "vip" && until > 0) Text("👑 " + tr("คุณเป็น VIP ถึง") + " " + df.format(java.util.Date(until)) + " · " + tr("ซื้อเพิ่มเพื่อต่ออายุได้"),
                color = VGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("เลือกแพ็กเกจ"), color = PureWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("💎 ${wallet?.third ?: "…"}", color = Color(0xFF7AF5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            val plans = info?.optJSONArray("plans")
            when {
                info == null && loadErr.isNotBlank() -> Text(loadErr, color = Color(0xFFFFC94D), fontSize = 12.sp)
                info == null -> CircularProgressIndicator(color = VPink, modifier = Modifier.size(24.dp))
                info?.optBoolean("enabled", true) == false || plans == null || plans.length() == 0 -> Text(tr("ยังไม่เปิดให้สมัคร VIP ในแอป"), color = PureWhite.copy(alpha = 0.7f), fontSize = 12.sp)
                else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val best = (0 until plans.length()).map { plans.getJSONObject(it) }.minByOrNull { it.optDouble("price") / maxOf(1, it.optInt("days")) }?.optString("id")
                    for (i in 0 until plans.length()) {
                        val p = plans.getJSONObject(i); val id = p.optString("id"); val on = sel == id
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (on) Color(0x33FFC94D) else Color(0x14FFFFFF))
                            .border(if (on) 2.dp else 1.dp, if (on) VGold else Color(0x33FFFFFF), RoundedCornerShape(16.dp)).clickable { sel = id; msg = "" }.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            if (id == best && plans.length() > 1) Text(tr("คุ้มสุด"), color = Color(0xFF3B2A06), fontSize = 9.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(VGold).padding(horizontal = 6.dp, vertical = 1.dp))
                            Text("${p.optInt("days")}", color = PureWhite, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Text(tr("วัน"), color = PureWhite.copy(alpha = 0.7f), fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("💎 ${p.optInt("price")}", color = Color(0xFF7AF5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (msg.isNotBlank()) Text(msg, color = if (msg.startsWith("✅")) Color(0xFF6EE7B7) else Color(0xFFFFC94D), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp))
                    .background(if (sel != null) Brush.horizontalGradient(listOf(Color(0xFFFFD86B), Color(0xFFE0A100))) else Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x33FFFFFF))))
                    .clickable(enabled = sel != null && !busy) {
                        val id = sel ?: return@clickable
                        busy = true; msg = ""
                        scope.launch {
                            val u = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                            val r = u?.let { runCatching { com.example.network.BrainApi.call(it, "POST", "/vip/buy", org.json.JSONObject().put("planId", id)) }.getOrNull() }
                            busy = false
                            if (r?.code == 200) {
                                msg = "✅ " + tr("สมัคร VIP สำเร็จ! ใช้สิทธิ์ได้ทันทีถึง") + " " + df.format(java.util.Date(r.json?.optLong("until") ?: 0L))
                                sel = null; tick++
                                runCatching { com.example.ads.AdRoom.refresh() }
                            } else msg = (r?.json?.optString("error")?.ifBlank { null } ?: tr("สมัครไม่สำเร็จ")) +
                                if (r?.code == 402) " — " + tr("เติมเพชรได้ที่หน้า 'ฉัน' → เหรียญ & เพชร") else ""
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color(0xFF3B2A06), strokeWidth = 2.dp)
                else Text(if (sel != null) "👑 " + tr("สมัคร VIP ด้วยเพชร") else tr("เลือกแพ็กเกจด้านบน"), color = if (sel != null) Color(0xFF3B2A06) else PureWhite.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Text(tr("ปิด"), color = PureWhite.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp).clickable(onClick = onClose).padding(8.dp))
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x26FFC94D)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = VGold, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = PureWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = PureWhite.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}
