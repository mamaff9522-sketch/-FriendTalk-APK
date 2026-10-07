package com.example.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.companion.model.*
import com.example.model.GiftItem
import com.example.model.User
import com.example.model.UserRole
import com.example.ui.theme.*

@Composable
fun CompanionMainScreen(
    currentUser: User,
    profiles: List<CompanionProfile>,
    wallets: Map<String, CompanionWallet>,
    pricingConfig: CompanionPricingConfig,
    onOpenApply: () -> Unit,
    onOpenKyc: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenWallet: (String) -> Unit,
    onStartSession: (CompanionProfile, CompanionServiceType, Int) -> Unit,
    onSendGiftRequest: (String, GiftItem, String) -> Unit,
    onReportSafety: (String, String) -> Unit
) {
    val myCompanionProfile = profiles.find { it.userId == currentUser.id }
    val approvedProfiles = profiles.filter { it.status == CompanionStatus.APPROVED && it.isReadyNow }
    val isAdmin = currentUser.role == UserRole.SUPERADMIN || currentUser.role == UserRole.MODERATOR

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 18+ Warning Banner
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Rose500.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🔞", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ระบบเพื่อนคุย (FriendTalk Companion 18+)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Rose500
                        )
                        Text(
                            text = "สงวนสิทธิ์เฉพาะผู้ใช้อายุ 18 ปีขึ้นไป มีการยืนยันตัวตน (KYC) และระบบควบคุมความปลอดภัยอย่างเข้มงวด",
                            fontSize = 11.sp,
                            color = Slate300
                        )
                    }
                }
            }
        }

        // Companion Dashboard / Apply Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (myCompanionProfile == null) {
                    Button(
                        onClick = onOpenApply,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Pink500)
                    ) {
                        Text("สมัครเป็นเพื่อนคุย 💖", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { onOpenWallet(myCompanionProfile.id) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400)
                    ) {
                        Text("กระเป๋าเงินเพื่อนคุย 💰", fontWeight = FontWeight.Bold, color = Slate950)
                    }
                }

                if (isAdmin) {
                    IconButton(
                        onClick = onOpenAdmin,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Purple600)
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin", tint = White)
                    }
                }
            }
        }

        // List of Available Companions
        items(approvedProfiles) { comp ->
            val rate = pricingConfig.rates[comp.level] ?: CompanionLevelRate(comp.level, 15, 45, 90)

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Slate800,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = comp.avatar,
                            contentDescription = comp.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = comp.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Amber400.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "${comp.level}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Amber400,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "⭐ ${comp.ratingScore} (${comp.ratingsCount} รีวิว) • คุยแล้ว ${comp.totalHoursTalked.toInt()} ชม.",
                                fontSize = 12.sp,
                                color = Slate300
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = comp.bio, fontSize = 13.sp, color = Slate200)

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = Slate700)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🪙 ${rate.voiceCallCoinsPerMin} เหรียญ/นาที",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Amber400
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onStartSession(comp, CompanionServiceType.TEXT_CHAT, rate.textChatCoinsPerMin) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("แชต", fontSize = 12.sp, color = Slate200)
                            }
                            Button(
                                onClick = { onStartSession(comp, CompanionServiceType.VOICE_CALL, rate.voiceCallCoinsPerMin) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Pink500)
                            ) {
                                Text("โทรคุย 📞", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
