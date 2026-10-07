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
import com.example.companion.model.CompanionGiftRequest
import com.example.ui.theme.*

@Composable
fun CompanionGiftRequestDialog(
    request: CompanionGiftRequest,
    onDismiss: () -> Unit,
    onRespond: (Boolean) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "คำขอส่งของขวัญ 🎁", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Amber400)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "${request.senderName} ต้องการส่ง ${request.gift.name} (${request.gift.icon}) ให้คุณ", fontSize = 13.sp, color = White)
                if (request.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "\"${request.note}\"", fontSize = 12.sp, color = Slate300)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onRespond(false); onDismiss() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("ปฏิเสธ", color = Slate300)
                    }
                    Button(
                        onClick = { onRespond(true); onDismiss() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Pink500)
                    ) {
                        Text("รับของขวัญ", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
