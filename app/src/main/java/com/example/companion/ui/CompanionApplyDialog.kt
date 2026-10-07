package com.example.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.companion.model.CompanionLevel
import com.example.companion.model.CompanionProfile
import com.example.companion.model.CompanionStatus
import com.example.model.User
import com.example.ui.theme.*

@Composable
fun CompanionApplyDialog(
    currentUser: User,
    onDismiss: () -> Unit,
    onSubmitApplication: (CompanionProfile) -> Unit
) {
    var bio by remember { mutableStateOf("พร้อมรับฟังและให้กำลังใจ เป็นที่ปรึกษาและเพื่อนคุยที่ดีค่ะ ✨") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "สมัครเป็นเพื่อนคุย (Companion) 💖", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = White)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "ผู้สมัคร: ${currentUser.displayName} (อายุ ${currentUser.age} ปี)",
                    fontSize = 13.sp,
                    color = Slate300
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("ข้อความแนะนำตัวและสไตล์การคุย") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val profile = CompanionProfile(
                            id = "comp_${currentUser.id}",
                            userId = currentUser.id,
                            displayName = currentUser.displayName,
                            avatar = currentUser.avatar,
                            age = currentUser.age,
                            bio = bio,
                            level = CompanionLevel.BRONZE,
                            status = CompanionStatus.PENDING_REVIEW
                        )
                        onSubmitApplication(profile)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pink500)
                ) {
                    Text("ส่งใบสมัครเพื่อรอแอดมินตรวจสอบ", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
