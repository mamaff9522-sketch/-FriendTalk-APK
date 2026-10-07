package com.example.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.User
import com.example.ui.theme.*

@Composable
fun CreatorStudioDialog(
    currentUser: User,
    onDismiss: () -> Unit
) {
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
                    Text(text = "Creator Studio 🎨", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = White)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Slate800,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("เพชรรายได้ทั้งหมดจากไลฟ์และของขวัญ:", fontSize = 12.sp, color = Slate400)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("💎 ${currentUser.diamonds} เพชร (ประมาณ ฿${currentUser.diamonds / 5})", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Cyan400)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("ของขวัญที่ได้รับทั้งหมด: ${currentUser.giftsReceivedTotal} ชิ้น 🎁", fontSize = 12.sp, color = Slate300)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pink500)
                ) {
                    Text("ตกลง", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
