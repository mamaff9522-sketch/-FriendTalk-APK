package com.example.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.example.model.Gender
import com.example.model.User
import com.example.ui.theme.*

@Composable
fun ProfileTab(
    currentUser: User,
    onEditProfile: () -> Unit,
    onOpenCreatorStudio: () -> Unit,
    onOpenCoins: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenCompanionApply: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Cover Photo & Avatar
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                AsyncImage(
                    model = currentUser.coverPhoto,
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
                AsyncImage(
                    model = currentUser.avatar,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(88.dp)
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp)
                        .clip(CircleShape)
                        .background(Slate950)
                )
            }
        }

        // Profile Info
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentUser.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = White
                            )
                            if (currentUser.isVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = Cyan400, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(text = "@${currentUser.username}", fontSize = 12.sp, color = Slate400)
                    }

                    OutlinedButton(
                        onClick = onEditProfile,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("แก้ไขโปรไฟล์", fontSize = 12.sp, color = Slate200)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(text = currentUser.bio, fontSize = 13.sp, color = Slate200)

                Spacer(modifier = Modifier.height(14.dp))

                // Stats row
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Slate800,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${currentUser.followingCount}", fontWeight = FontWeight.Bold, color = White, fontSize = 15.sp)
                            Text("กำลังติดตาม", fontSize = 11.sp, color = Slate400)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${currentUser.followersCount}", fontWeight = FontWeight.Bold, color = White, fontSize = 15.sp)
                            Text("ผู้ติดตาม", fontSize = 11.sp, color = Slate400)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${currentUser.likesCount}", fontWeight = FontWeight.Bold, color = White, fontSize = 15.sp)
                            Text("ถูกใจทั้งหมด", fontSize = 11.sp, color = Slate400)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Action Cards
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Slate800,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenCoins() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🪙", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("กระเป๋าเหรียญ & เพชรสะสม", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = White)
                                Text("${currentUser.coins} เหรียญ • ${currentUser.diamonds} เพชร", fontSize = 12.sp, color = Amber400)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Slate400)
                        }
                    }

                    if (currentUser.age >= 18) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Slate800,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpenCompanionApply() }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "💖", fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("สมัครเป็นผู้ให้บริการเพื่อนคุย (18+)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Rose500)
                                    Text("สร้างรายได้จากการคุยสายเสียง/วิดีโอคอล", fontSize = 12.sp, color = Slate400)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Slate400)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Slate800,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenCreatorStudio() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🎨", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Creator Studio (สตูดิโอครีเอเตอร์)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = White)
                                Text("แดชบอร์ดสตรีมเมอร์และสถิติรายได้", fontSize = 12.sp, color = Slate400)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Slate400)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Slate800,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenPermissions() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "⚙️", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ตั้งค่าความเป็นส่วนตัวและสิทธิ์", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = White)
                                Text("จัดการการเข้าถึงกล้อง ไมโครโฟน และตำแหน่ง", fontSize = 12.sp, color = Slate400)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Slate400)
                        }
                    }
                }
            }
        }
    }
}
