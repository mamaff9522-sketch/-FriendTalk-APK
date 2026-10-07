package com.example.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.companion.model.CompanionProfile
import com.example.companion.model.CompanionStatus
import com.example.ui.theme.*

@Composable
fun CompanionAdminDialog(
    profiles: List<CompanionProfile>,
    onDismiss: () -> Unit,
    onApprove: (String, String) -> Unit,
    onReject: (String, String) -> Unit,
    onSuspend: (String, String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "จัดการผู้ให้บริการเพื่อนคุย (Admin) 🛡️", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = White)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(profiles) { prof ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Slate800,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = prof.avatar,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(38.dp).clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = prof.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = White)
                                        Text(text = "สถานะ: ${prof.status.name} • Level: ${prof.level.name}", fontSize = 11.sp, color = Slate400)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    if (prof.status == CompanionStatus.PENDING_REVIEW) {
                                        Button(
                                            onClick = { onApprove(prof.id, "อนุมัติเรียบร้อย") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("อนุมัติ", color = Slate950, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = { onReject(prof.id, "ข้อมูลไม่ผ่านเกณฑ์") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("ปฏิเสธ", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else if (prof.status == CompanionStatus.APPROVED) {
                                        Button(
                                            onClick = { onSuspend(prof.id, "ระงับชั่วคราวเพื่อตรวจสอบ") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("ระงับ (Suspend)", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
