package com.example.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.companion.model.CompanionBankAccount
import com.example.companion.model.CompanionWallet
import com.example.ui.theme.*

@Composable
fun CompanionWalletSheet(
    wallet: CompanionWallet,
    onDismiss: () -> Unit,
    onRequestWithdrawal: (amountCoins: Int, bankAccount: CompanionBankAccount) -> Boolean
) {
    var withdrawCoinsText by remember { mutableStateOf("1000") }
    var bankName by remember { mutableStateOf(wallet.bankAccount?.bankName ?: "ธนาคารกสิกรไทย") }
    var accountNumber by remember { mutableStateOf(wallet.bankAccount?.accountNumber ?: "123-4-56789-0") }
    var accountHolder by remember { mutableStateOf(wallet.bankAccount?.accountHolder ?: "บัญชีของคุณ") }

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
                    Text(text = "กระเป๋าเงินผู้ให้บริการเพื่อนคุย 💰", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = White)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Slate800,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("เหรียญพร้อมถอน (Available):", fontSize = 12.sp, color = Slate400)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("🪙 ${wallet.availableCoins} เหรียญ (≈ ฿${wallet.availableCoins * 0.2})", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Amber400)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("ถอนสะสมแล้วทั้งหมด: ฿${wallet.totalWithdrawnBaht.toInt()}", fontSize = 11.sp, color = Slate300)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "แบบฟอร์มขอถอนเงินเข้าบัญชี:", fontSize = 13.sp, color = Slate200, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = withdrawCoinsText,
                    onValueChange = { withdrawCoinsText = it },
                    label = { Text("จำนวนเหรียญที่ต้องการถอน") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        val coins = withdrawCoinsText.toIntOrNull() ?: 0
                        val bank = CompanionBankAccount(bankName, accountNumber, accountHolder)
                        onRequestWithdrawal(coins, bank)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                    enabled = (withdrawCoinsText.toIntOrNull() ?: 0) > 0 && (withdrawCoinsText.toIntOrNull() ?: 0) <= wallet.availableCoins
                ) {
                    Text("ยืนยันขอถอนเงิน", color = Slate950, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
