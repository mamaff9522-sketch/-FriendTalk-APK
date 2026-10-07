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
import com.example.companion.model.KycStatus
import com.example.companion.model.KycVerificationInfo
import com.example.model.User
import com.example.ui.theme.*

@Composable
fun CompanionKycDialog(
    currentUser: User,
    onDismiss: () -> Unit,
    onSubmitKyc: (KycVerificationInfo) -> Unit
) {
    var fullName by remember { mutableStateOf(currentUser.displayName) }
    var idCardNumber by remember { mutableStateOf("1-1002-00345-67-8") }

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
                    Text(text = "ยืนยันตัวตน (KYC 18+) 🪪", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = White)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("ชื่อ-นามสกุลตามบัตรประชาชน") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = idCardNumber,
                    onValueChange = { idCardNumber = it },
                    label = { Text("เลขประจำตัวประชาชน 13 หลัก") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val kyc = KycVerificationInfo(
                            userId = currentUser.id,
                            fullName = fullName,
                            idCardNumber = idCardNumber,
                            birthDate = "01/01/2000",
                            calculatedAge = currentUser.age,
                            status = KycStatus.VERIFIED
                        )
                        onSubmitKyc(kyc)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald400)
                ) {
                    Text("บันทึกข้อมูลและยืนยันตัวตน", color = Slate950, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
