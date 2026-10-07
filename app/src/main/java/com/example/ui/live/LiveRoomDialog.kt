package com.example.ui.live

import androidx.compose.foundation.background
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.GiftItem
import com.example.model.LiveRoom
import com.example.model.User
import com.example.ui.theme.*

@Composable
fun LiveRoomDialog(
    room: LiveRoom,
    currentUser: User,
    onDismiss: () -> Unit,
    onSendComment: (String) -> Unit,
    onSendGift: (GiftItem) -> Unit,
    onOpenTopGifters: () -> Unit
) {
    var commentText by remember { mutableStateOf("") }
    var isGiftSheetOpen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
        ) {
            // Live Video / Cover Background
            AsyncImage(
                model = room.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient Overlays
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Black.copy(alpha = 0.6f), Color.Transparent, Black.copy(alpha = 0.85f))
                        )
                    )
            )

            // Top Header: Host info + viewers + Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        AsyncImage(
                            model = room.hostAvatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = room.hostName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = White)
                            Text(text = "💎 ${room.diamondsEarned}", fontSize = 10.sp, color = Cyan400)
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onOpenTopGifters,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Amber400.copy(alpha = 0.3f))
                    ) {
                        Text(text = "👑", fontSize = 16.sp)
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Black.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // PK Battle Bar (If Active)
            if (room.isPkMode && room.pkState.isPkActive) {
                Surface(
                    color = Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 70.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${room.hostName}: ${room.pkState.myScore}", color = Rose500, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(text = "⚔️ PK 0${room.pkState.timeLeftSeconds / 60}:${room.pkState.timeLeftSeconds % 60}", color = Amber400, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        Text(text = "${room.pkState.opponentName}: ${room.pkState.opponentScore}", color = Cyan400, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Bottom Overlay: Live comments + action controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                // Comments Box
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(room.recentComments) { comment ->
                        Surface(
                            color = Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${comment.userName}: ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (comment.isGift) Amber400 else Cyan400
                                )
                                Text(
                                    text = comment.text,
                                    fontSize = 12.sp,
                                    color = White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Input Row & Gift Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text("ส่งข้อความในห้องไลฟ์...", fontSize = 12.sp, color = Slate400) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Black.copy(alpha = 0.6f),
                            unfocusedContainerColor = Black.copy(alpha = 0.6f),
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedIndicatorColor = Pink500,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                onSendComment(commentText)
                                commentText = ""
                            }
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Pink500)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { isGiftSheetOpen = true },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(Amber400, Rose500))
                            )
                    ) {
                        Text(text = "🎁", fontSize = 20.sp)
                    }
                }
            }
        }

        if (isGiftSheetOpen) {
            GiftSheetDialog(
                currentUser = currentUser,
                onDismiss = { isGiftSheetOpen = false },
                onSelectGift = { gift ->
                    onSendGift(gift)
                    isGiftSheetOpen = false
                }
            )
        }
    }
}
