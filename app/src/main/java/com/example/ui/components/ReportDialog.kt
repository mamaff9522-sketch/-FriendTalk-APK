package com.example.ui.components

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
import com.example.model.Report
import com.example.ui.theme.*

@Composable
fun ReportDialog(
    targetType: String,
    targetId: String,
    targetName: String,
    onDismiss: () -> Unit,
    onSubmitReport: (Report) -> Unit
) {
    var reason by remember { mutableStateOf("เนื้อหาไม่เหมาะสม") }
    var details by remember { mutableStateOf("") }

    val reasons = listOf("เนื้อหาไม่เหมาะสม", "สแปมหรือหลอกลวง", "คุกคามหรือก่อกวน", "การแอบอ้างบุคคลอื่น", "อื่นๆ")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
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
                    Text(
                        text = "รายงานความไม่ปลอดภัย 🛡️",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "รายงาน: $targetName ($targetType)",
                    fontSize = 13.sp,
                    color = Slate300,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(12.dp))

                reasons.forEach { r ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = reason == r,
                            onClick = { reason = r },
                            colors = RadioButtonDefaults.colors(selectedColor = Pink500)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = r, fontSize = 13.sp, color = Slate200)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("รายละเอียดเพิ่มเติม (ถ้ามี)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val report = Report(
                            id = "rep_${System.currentTimeMillis()}",
                            reporterId = "current",
                            reporterName = "ผู้ใช้งาน",
                            targetType = targetType,
                            targetId = targetId,
                            targetName = targetName,
                            reason = reason,
                            details = details,
                            timestamp = "วันนี้"
                        )
                        onSubmitReport(report)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                ) {
                    Text("ส่งรายงานความไม่ปลอดภัย", color = White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
