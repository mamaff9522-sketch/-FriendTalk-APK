package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*

@Composable
fun PermissionDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Slate900,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "การอนุญาตเข้าถึงอุปกรณ์ 📱",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "FriendTalk ต้องการสิทธิ์เพื่อใช้งานฟังก์ชัน:",
                    fontSize = 13.sp,
                    color = Slate300
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("📷 กล้อง: สำหรับไลฟ์สดและถ่ายรูปโปรไฟล์", fontSize = 12.sp, color = Slate400)
                Text("🎙️ ไมโครโฟน: สำหรับคุยสายเสียง/วิดีโอคอล", fontSize = 12.sp, color = Slate400)
                Text("📍 ตำแหน่ง: สำหรับฟังก์ชันเรดาร์และคนใกล้เคียง", fontSize = 12.sp, color = Slate400)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pink500)
                ) {
                    Text("ตกลง / อนุญาตแล้ว", color = White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
