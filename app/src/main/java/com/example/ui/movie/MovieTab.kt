package com.example.ui.movie

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.model.ShortVideoItem
import com.example.ui.theme.*

@Composable
fun MovieTab(
    onOpenComments: (String) -> Unit = {}
) {
    val videos = remember {
        mutableStateListOf(
            ShortVideoItem("v1", "user_linlin", "หลินหลิน 🌸", "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&auto=format&fit=crop&q=80", "บรรยากาศคอนเสิร์ตเมื่อคืนสนุกมากกกก 🎤✨ มาร้องเพลงด้วยกันน้า!", "https://example.com/v1.mp4", "เสียงต้นฉบับ - Linlin Acoustic", 1250, 48, true),
            ShortVideoItem("v2", "user_arty", "อาร์ตี้ กีตาร์", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400&auto=format&fit=crop&q=80", "Solo กีตาร์แบบ acoustic สดๆ ฝึกมา 3 วันเต็ม ลองฟังกันดูครับ 🎸🔥", "https://example.com/v2.mp4", "Acoustic Vibes - Arty Solo", 890, 32, false),
            ShortVideoItem("v3", "user_fah", "ฟ้าใส (Fahsai)", "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&auto=format&fit=crop&q=80", "แวะมาคาเฟ่อารีย์ แสงบ่าย 3 โมงคือดีงามมากกก ☕🌿", "https://example.com/v3.mp4", "Chill Lo-Fi Cafe Beat", 620, 19, false)
        )
    }

    var isPaused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(videos) { index, video ->
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .background(Slate950)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isPaused = !isPaused
                        }
                ) {
                    val coverImages = listOf(
                        "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop&q=80",
                        "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=80",
                        "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=800&auto=format&fit=crop&q=80"
                    )

                    AsyncImage(
                        model = coverImages[index % coverImages.size],
                        contentDescription = "Video Feed",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Black.copy(alpha = 0.35f), Color.Transparent, Black.copy(alpha = 0.85f))
                                )
                            )
                    )

                    // Pause indicator overlay
                    AnimatedVisibility(
                        visible = isPaused,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(150)),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    // Right action side-panel
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 90.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Creator Avatar with Follow Plus button
                        Box(contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = video.creatorAvatar,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .align(Alignment.BottomCenter)
                                    .offset(y = 6.dp)
                                    .clip(CircleShape)
                                    .background(Pink500),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Follow", tint = White, modifier = Modifier.size(12.dp))
                            }
                        }

                        // Like Button
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    val isLiked = !video.isLiked
                                    val newCount = if (isLiked) video.likesCount + 1 else maxOf(0, video.likesCount - 1)
                                    videos[index] = video.copy(isLiked = isLiked, likesCount = newCount)
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Black.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    imageVector = if (video.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (video.isLiked) Rose500 else White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text("${video.likesCount}", fontSize = 11.sp, color = White, fontWeight = FontWeight.Bold)
                        }

                        // Comment Button
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { onOpenComments(video.id) },
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Black.copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Default.ChatBubble, contentDescription = "Comment", tint = White, modifier = Modifier.size(22.dp))
                            }
                            Text("${video.commentsCount}", fontSize = 11.sp, color = White, fontWeight = FontWeight.Bold)
                        }

                        // Share Button
                        IconButton(
                            onClick = {},
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = White, modifier = Modifier.size(22.dp))
                        }
                    }

                    // Bottom Video Information
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 90.dp, end = 80.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = video.creatorName,
                                fontWeight = FontWeight.Bold,
                                color = White,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Pink500,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "Creator",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = video.caption,
                            color = Slate100,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Black.copy(alpha = 0.4f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Pink400, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = video.musicTitle, color = Slate200, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
