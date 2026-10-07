package com.example.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.User
import com.example.ui.theme.*

@Composable
fun ShakeView(
    users: List<User>,
    onFoundUser: (User) -> Unit
) {
    var isShaking by remember { mutableStateOf(false) }
    var matchedUser by remember { mutableStateOf<User?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (matchedUser == null) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Pink500.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Vibration,
                    contentDescription = null,
                    tint = Pink400,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "เขย่ามือถือเพื่อหาเพื่อนใหม่ 📱",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ระบบจะค้นหาผู้ใช้งานที่กำลังเขย่าหาเพื่อนในเวลาเดียวกัน",
                fontSize = 13.sp,
                color = Slate400
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    isShaking = true
                    matchedUser = users.randomOrNull()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pink500)
            ) {
                Text("จำลองการเขย่า (Shake Now)", fontWeight = FontWeight.Bold)
            }
        } else {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Slate800,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🎉 แมตช์สำเร็จแล้ว!", fontWeight = FontWeight.Bold, color = Amber400, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    AsyncImage(
                        model = matchedUser!!.avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = matchedUser!!.displayName, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = White)
                    Text(text = matchedUser!!.bio, fontSize = 12.sp, color = Slate300)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { matchedUser = null },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("เขย่าใหม่", color = Slate300)
                        }
                        Button(
                            onClick = { onFoundUser(matchedUser!!) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Pink500)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ทักทายเลย")
                        }
                    }
                }
            }
        }
    }
}
