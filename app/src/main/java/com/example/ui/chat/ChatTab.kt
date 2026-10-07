package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Conversation
import com.example.model.User
import com.example.ui.theme.*

@Composable
fun ChatTab(
    conversations: List<Conversation>,
    users: List<User>,
    currentUser: User,
    onSelectConversation: (String) -> Unit,
    onCreateGroup: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "กล่องข้อความแชต 💬",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = White
                )
                IconButton(
                    onClick = onCreateGroup,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Pink500.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Default.Group, contentDescription = "Create Group", tint = Pink400, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "ยังไม่มีบทสนทนา เลือกเพื่อนเพื่อเริ่มแชตเลย ✨", fontSize = 13.sp, color = Slate400)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(conversations) { conv ->
                        val otherUserId = conv.participantIds.firstOrNull { it != currentUser.id }
                        val otherUser = users.find { it.id == otherUserId }
                        val avatar = conv.groupAvatar ?: otherUser?.avatar ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&auto=format&fit=crop&q=80"
                        val title = conv.groupName ?: otherUser?.displayName ?: "ผู้ใช้งาน FriendTalk"
                        val unread = conv.unreadCounts[currentUser.id] ?: 0

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Slate800,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSelectConversation(conv.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = avatar,
                                    contentDescription = title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = conv.lastMessageTimestamp,
                                            fontSize = 10.sp,
                                            color = Slate400
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = conv.lastMessage,
                                        fontSize = 12.sp,
                                        color = if (unread > 0) White else Slate400,
                                        fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (unread > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Badge(containerColor = Pink500, contentColor = White) {
                                        Text("$unread", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
