package com.example.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
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
import com.example.model.User
import com.example.ui.theme.*

@Composable
fun SwipeMatchView(
    users: List<User>,
    onMatch: (User) -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }

    if (currentIndex >= users.size) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "✨", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "คุณดูโปรไฟล์ทั้งหมดครบแล้ว!", fontWeight = FontWeight.Bold, color = White, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { currentIndex = 0 }, shape = RoundedCornerShape(12.dp)) {
                    Text("เริ่มดูใหม่")
                }
            }
        }
    } else {
        val user = users[currentIndex]
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
            ) {
                AsyncImage(
                    model = user.avatar,
                    contentDescription = user.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Black.copy(alpha = 0.1f), Black.copy(alpha = 0.8f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Text(
                        text = "${user.displayName}, ${user.age}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = White
                    )
                    Text(
                        text = user.bio,
                        fontSize = 13.sp,
                        color = Slate200,
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { currentIndex++ },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Slate800)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Pass", tint = Rose500, modifier = Modifier.size(28.dp))
                }

                IconButton(
                    onClick = {
                        onMatch(user)
                        currentIndex++
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Pink500)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = "Like", tint = White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}
