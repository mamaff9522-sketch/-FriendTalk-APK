package com.example.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.theme.*

@Composable
fun PostCard(
    post: Post,
    currentUserId: String,
    feedConfig: FeedSectionConfig = FeedSectionConfig(),
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onReportClick: () -> Unit,
    onUserClick: (String) -> Unit,
    onLiveClick: (String) -> Unit = {},
    onImageClick: (String) -> Unit = {}
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    val cornerRadius = feedConfig.cardCornerRadiusDp.dp
    val scale = feedConfig.textSizeScale
    val isOwner = post.authorId == currentUserId

    val cardModifier = when (feedConfig.postCardStyle) {
        PostCardStyle.BORDERLESS -> Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = (feedConfig.postSpacingDp / 2).dp)
        PostCardStyle.COMPACT -> Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = (feedConfig.postSpacingDp / 2).dp)
        PostCardStyle.MODERN_CARD -> Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = (feedConfig.postSpacingDp / 2).dp)
    }

    Surface(
        modifier = cardModifier,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        border = if (post.type == PostType.LIVE) androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.5f)) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Avatar + Name + Verified Badge + Timestamp + Location + Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = post.authorAvatar,
                    contentDescription = post.authorName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable { onUserClick(post.authorId) }
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.authorName,
                            fontWeight = FontWeight.Bold,
                            fontSize = (14 * scale).sp,
                            color = White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (post.authorIsVerified && feedConfig.showVerifiedBadges) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = Cyan400,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        if (post.authorBadges.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Pink500.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = post.authorBadges.first(),
                                    fontSize = (9 * scale).sp,
                                    color = Pink400,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = post.timestamp,
                            fontSize = (11 * scale).sp,
                            color = Slate400
                        )
                        if (post.location != null) {
                            Text(text = "•", fontSize = (11 * scale).sp, color = Slate600)
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Pink400,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = post.location,
                                fontSize = (11 * scale).sp,
                                color = Pink400,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Three-dot Options Menu
                Box {
                    IconButton(
                        onClick = { isMenuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Post Options",
                            tint = Slate400,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (post.isBookmarked) "ยกเลิกบันทึก" else "บันทึกโพสต์") },
                            leadingIcon = {
                                Icon(
                                    if (post.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                onBookmarkClick()
                                isMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("รายงานโพสต์") },
                            leadingIcon = { Icon(Icons.Outlined.Report, contentDescription = null) },
                            onClick = {
                                onReportClick()
                                isMenuExpanded = false
                            }
                        )
                        if (isOwner) {
                            DropdownMenuItem(
                                text = { Text("ลบโพสต์", color = Rose500) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Rose500) },
                                onClick = {
                                    onDeleteClick()
                                    isMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Post Text Content
            if (post.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = post.content,
                    fontSize = (14 * scale).sp,
                    lineHeight = (20 * scale).sp,
                    color = Slate100
                )
            }

            // Post Media / Special Content
            when (post.type) {
                PostType.IMAGE -> {
                    if (post.images.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        AsyncImage(
                            model = post.images.first(),
                            contentDescription = "Post Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 340.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onImageClick(post.images.first()) }
                        )
                    }
                }

                PostType.MULTI_IMAGE -> {
                    if (post.images.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        // Main big image + sub previews
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AsyncImage(
                                model = post.images[0],
                                contentDescription = "Main Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onImageClick(post.images[0]) }
                            )

                            if (post.images.size > 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    post.images.drop(1).take(3).forEachIndexed { idx, imgUrl ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(80.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onImageClick(imgUrl) }
                                        ) {
                                            AsyncImage(
                                                model = imgUrl,
                                                contentDescription = "Sub Image $idx",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            if (idx == 2 && post.images.size > 4) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Black.copy(alpha = 0.6f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "+${post.images.size - 4}",
                                                        fontWeight = FontWeight.Bold,
                                                        color = White,
                                                        fontSize = 14.sp
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

                PostType.VIDEO, PostType.SHORT_VIDEO -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate950),
                        contentAlignment = Alignment.Center
                    ) {
                        if (post.images.isNotEmpty()) {
                            AsyncImage(
                                model = post.images.first(),
                                contentDescription = "Video Thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Black.copy(alpha = 0.35f))
                        )
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Pink500.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Video",
                                tint = White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        if (post.videoDuration != null) {
                            Surface(
                                color = Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = post.videoDuration,
                                    fontSize = 10.sp,
                                    color = White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                PostType.LIVE -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                post.liveRoomId?.let { onLiveClick(it) }
                            }
                    ) {
                        if (post.images.isNotEmpty()) {
                            AsyncImage(
                                model = post.images.first(),
                                contentDescription = "Live Cover",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Black.copy(alpha = 0.3f), Black.copy(alpha = 0.7f))
                                    )
                                )
                        )

                        // Top LIVE tag & Viewers count
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Rose500,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(White)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "LIVE",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        color = White
                                    )
                                }
                            }

                            Surface(
                                color = Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = Slate200,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${post.liveViewerCount} คน",
                                        fontSize = 11.sp,
                                        color = White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Bottom Action CTA
                        Button(
                            onClick = { post.liveRoomId?.let { onLiveClick(it) } },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(12.dp)
                                .fillMaxWidth(0.85f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                        ) {
                            Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("เข้าชมไลฟ์สดนี้", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                PostType.SHARED_POST -> {
                    if (post.sharedPost != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Slate900,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = post.sharedPost.originalAuthorAvatar,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = post.sharedPost.originalAuthorName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Slate200
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = post.sharedPost.originalContent,
                                    fontSize = 12.sp,
                                    color = Slate300
                                )
                                if (post.sharedPost.originalImageUrl != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    AsyncImage(
                                        model = post.sharedPost.originalImageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                        }
                    }
                }

                PostType.TEXT -> {
                    // No media
                }
            }

            // Tags
            if (post.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    post.tags.take(3).forEach { tag ->
                        Text(
                            text = "#$tag",
                            fontSize = 11.sp,
                            color = Cyan400,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Slate800, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(6.dp))

            // Action Buttons Row: Like, Comment, Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLikeClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (post.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (post.isLiked) Rose500 else Slate400,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (post.likesCount > 0) "${post.likesCount}" else "ถูกใจ",
                        fontSize = 12.sp,
                        fontWeight = if (post.isLiked) FontWeight.Bold else FontWeight.Normal,
                        color = if (post.isLiked) Rose500 else Slate300
                    )
                }

                // Comment Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCommentClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comment",
                        tint = Slate400,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (post.commentsCount > 0) "${post.commentsCount}" else "ความคิดเห็น",
                        fontSize = 12.sp,
                        color = Slate300
                    )
                }

                // Share Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onShareClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = Slate400,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (post.sharesCount > 0) "${post.sharesCount}" else "แชร์",
                        fontSize = 12.sp,
                        color = Slate300
                    )
                }
            }
        }
    }
}
