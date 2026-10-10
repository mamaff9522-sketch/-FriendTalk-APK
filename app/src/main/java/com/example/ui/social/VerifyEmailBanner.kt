package com.example.ui.social

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth

/** Shown to email/password users until they verify; the backend and DB rules block their writes until then. */
@Composable
fun VerifyEmailBanner() {
    val user = FirebaseAuth.getInstance().currentUser ?: return
    val isPassword = user.providerData.any { it.providerId == "password" }
    var verified by remember { mutableStateOf(user.isEmailVerified) }
    var msg by remember { mutableStateOf("") }
    if (!isPassword || verified) return
    Surface(color = Amber500.copy(alpha = 0.2f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("กรุณายืนยันอีเมล ${user.email} ก่อน จึงจะแชท ปัดหาคู่ และแก้โปรไฟล์ได้", color = White, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    user.sendEmailVerification().addOnCompleteListener { msg = if (it.isSuccessful) "ส่งอีเมลยืนยันแล้ว" else "ส่งไม่สำเร็จ ลองใหม่ภายหลัง" }
                }) { Text("ส่งอีเมลยืนยันอีกครั้ง", color = Amber400, fontSize = 12.sp) }
                TextButton(onClick = {
                    user.reload().addOnCompleteListener {
                        val u = FirebaseAuth.getInstance().currentUser
                        if (u?.isEmailVerified == true) { u.getIdToken(true); verified = true } else msg = "ยังไม่ได้ยืนยัน"
                    }
                }) { Text("ยืนยันแล้ว", color = Emerald400, fontSize = 12.sp) }
            }
            if (msg.isNotBlank()) Text(msg, color = Slate300, fontSize = 11.sp)
        }
    }
}
