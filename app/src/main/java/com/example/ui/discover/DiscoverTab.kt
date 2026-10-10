package com.example.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.User
import com.example.ui.theme.*

@Composable
fun DiscoverTab(
    users: List<User>,
    currentUser: User,
    onStartChat: (User) -> Unit
) {
    var subTab by remember { mutableStateOf("radar") } // radar, nearby, swipe, shake

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Sub tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "radar" to "เรดาร์ 📡",
                "nearby" to "ใกล้เคียง 📍",
                "swipe" to "ปัดหาคู่ 💘",
                "shake" to "เขย่าเจอ 📱",
                "random" to "สุ่มคุย 🎲"
            ).forEach { (key, label) ->
                val isSel = subTab == key
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSel) Pink500 else Slate800,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { subTab = key }
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSel) White else Slate300,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        when (subTab) {
            "radar" -> RadarView(users = users, currentUser = currentUser, onSelectUser = onStartChat)
            "nearby" -> NearbyListView(users = users, currentUser = currentUser, onStartChat = onStartChat)
            "swipe" -> com.example.ui.social.RealSwipeView()
            "shake" -> ShakeView(users = users.filter { it.id != currentUser.id }, onFoundUser = onStartChat)
            "random" -> com.example.ui.ai.RandomMatchView(users = users.filter { it.id != currentUser.id }, onStartChatWithUser = onStartChat)
        }
    }
}
