package com.example.ui.discover

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.User
import com.example.ui.theme.*

@Composable
fun RadarView(
    users: List<User>,
    currentUser: User,
    onSelectUser: (User) -> Unit
) {
    var radiusKm by remember { mutableStateOf(5f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "เรดาร์ค้นหาเพื่อนรอบตัว (รัศมี ${radiusKm.toInt()} กม.)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = White
        )
        Slider(
            value = radiusKm,
            onValueChange = { radiusKm = it },
            valueRange = 1f..50f,
            colors = SliderDefaults.colors(thumbColor = Pink500, activeTrackColor = Pink500)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(CircleShape)
                .background(Slate900),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(color = Slate700, radius = size.width * 0.45f, style = Stroke(1.5f))
                drawCircle(color = Slate700, radius = size.width * 0.30f, style = Stroke(1.5f))
                drawCircle(color = Slate700, radius = size.width * 0.15f, style = Stroke(1.5f))
                drawLine(color = Slate800, start = Offset(0f, center.y), end = Offset(size.width, center.y))
                drawLine(color = Slate800, start = Offset(center.x, 0f), end = Offset(center.x, size.height))
            }

            // Center User (Me)
            AsyncImage(
                model = currentUser.avatar,
                contentDescription = "Me",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(2.dp, Pink500, CircleShape)
            )

            // Other users around
            users.filter { it.id != currentUser.id }.take(4).forEachIndexed { index, user ->
                val angle = (index * 90) * (Math.PI / 180)
                val dist = 80.0
                val xOff = (Math.cos(angle) * dist).toInt().dp
                val yOff = (Math.sin(angle) * dist).toInt().dp

                AsyncImage(
                    model = user.avatar,
                    contentDescription = user.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(x = xOff, y = yOff)
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Cyan400, CircleShape)
                        .clickable { onSelectUser(user) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "แตะที่โปรไฟล์เพื่อเริ่มแชตหรือดูรายละเอียด ✨",
            fontSize = 12.sp,
            color = Slate400
        )
    }
}
