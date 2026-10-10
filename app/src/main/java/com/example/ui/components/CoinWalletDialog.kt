package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.User
import com.example.ui.theme.*

data class CoinPackage(val coins: Int, val priceBaht: Double, val bonusCoins: Int = 0)

@Composable
fun CoinWalletDialog(
    currentUser: User,
    onDismiss: () -> Unit,
    onBuyCoins: (coins: Int, baht: Double) -> String
) {
    val context = LocalContext.current
    val packages = listOf(
        CoinPackage(100, 35.0),
        CoinPackage(300, 99.0, 30),
        CoinPackage(500, 169.0, 60),
        CoinPackage(1000, 329.0, 150),
        CoinPackage(2500, 799.0, 500),
        CoinPackage(5000, 1499.0, 1200)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "กระเป๋าเหรียญ FriendTalk 🪙",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Slate800,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "เหรียญคงเหลือ", fontSize = 12.sp, color = Slate400)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "🪙 ${currentUser.coins}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Amber400
                            )
                        }
                        Divider(modifier = Modifier.height(36.dp).width(1.dp), color = Slate700)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "เพชรสะสม (รายได้)", fontSize = 12.sp, color = Slate400)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "💎 ${currentUser.diamonds}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Cyan400
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "เติมเหรียญ (สำหรับส่งของขวัญและใช้บริการเพื่อนคุย):",
                    fontSize = 13.sp,
                    color = Slate300,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    items(packages) { pkg ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Slate800,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    // ไม่เพิ่มเหรียญ: แสดงข้อความว่ายังไม่เปิดซื้อเหรียญ
                                    val message = onBuyCoins(pkg.coins + pkg.bonusCoins, pkg.priceBaht)
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🪙 ${pkg.coins}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Amber400
                                )
                                if (pkg.bonusCoins > 0) {
                                    Text(
                                        text = "+โบนัส ${pkg.bonusCoins} เหรียญ",
                                        fontSize = 10.sp,
                                        color = Emerald400,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = Pink500,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "฿${pkg.priceBaht.toInt()}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
