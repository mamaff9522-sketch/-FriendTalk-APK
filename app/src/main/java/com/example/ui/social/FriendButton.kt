package com.example.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.social.InterestsRepo
import com.example.social.NearbyRepo
import com.example.ui.components.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 'เพิ่มเพื่อน' button that always shows the real state:
 * ➕ เพิ่มเพื่อน → ⏳ ส่งคำขอแล้ว (re-checked every few seconds) → ✓ เพื่อน once the other person accepts.
 * If they already asked me it shows 'ตอบรับคำขอ'. [onSent] gets the server reply (shared interests) after sending.
 */
@Composable
fun FriendButton(uid: String, small: Boolean = false, onSent: (InterestsRepo.FriendResult) -> Unit = {}, onError: (String) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var status by remember(uid) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(uid) {
        status = NearbyRepo.friendStatus(uid) ?: "none"
        // waiting for the other person → keep checking while this button is on screen
        while (true) { delay(5000); if (status == "requested" || status == "incoming") NearbyRepo.friendStatus(uid)?.let { status = it } }
    }
    val st = status
    if (st == "self") return
    val (label, active) = when (st) {
        null -> tr("…") to false
        "friends" -> "✓ " + tr("เพื่อน") to false
        "requested" -> "⏳ " + tr("ส่งคำขอแล้ว") to false
        "incoming" -> tr("ตอบรับคำขอ") to true
        else -> "➕ " + tr("เพิ่มเพื่อน") to true
    }
    val shape = RoundedCornerShape(if (small) 14.dp else 20.dp)
    Text(label, color = if (active) Color.White else if (st == "friends") Color(0xFF34D399) else Color(0xFFCBC3E3),
        fontSize = if (small) 12.sp else 14.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(shape)
            .then(if (active) Modifier.background(Brush.linearGradient(listOf(Color(0xFFFF2FA8), Color(0xFF9B3DFF))))
                  else Modifier.background(Color(0x22FFFFFF)).border(1.dp, if (st == "friends") Color(0xFF34D399) else Color(0x44FFFFFF), shape))
            .clickable(enabled = active && !busy) {
                busy = true
                scope.launch {
                    if (st == "incoming") {
                        if (NearbyRepo.respond(uid, true) != null) status = "friends" else onError(tr("ตอบรับไม่สำเร็จ"))
                    } else {
                        val r = InterestsRepo.sendFriendRequest(uid)
                        if (r == null) onError(tr("ส่งคำขอไม่สำเร็จ"))
                        else { status = if (r.status == "friends" || r.status == "already_friends") "friends" else "requested"; onSent(r) }
                    }
                    busy = false
                }
            }
            .padding(horizontal = if (small) 12.dp else 18.dp, vertical = if (small) 5.dp else 10.dp))
}
