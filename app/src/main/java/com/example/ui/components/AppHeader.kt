package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.AppUiConfig
import com.example.model.User
import com.example.model.UserRole
import com.example.ui.theme.*

@Composable
fun AppHeader(
    currentUser: User,
    unreadNotificationsCount: Int,
    searchQuery: String = "",
    isSearchActive: Boolean = false,
    onSearchQueryChange: (String) -> Unit = {},
    onToggleSearch: (Boolean) -> Unit = {},
    onOpenCoins: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenPermissions: () -> Unit,
    onToggleLocationSharing: () -> Unit,
    uiConfig: AppUiConfig,
    onRefreshConfig: () -> Unit,
    isRefreshing: Boolean,
    onOpenAdminDashboard: () -> Unit,
    onOpenUiBuilder: () -> Unit,
    onOpenApkDownload: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Logo & Brand Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Pink500, Purple600)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "FT",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = White
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "FriendTalk",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = White,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Pink500)
                            )
                        }
                        Text(
                            text = currentUser.displayName,
                            fontSize = 11.sp,
                            color = Slate400,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Header Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Search toggle button
                    IconButton(
                        onClick = { onToggleSearch(!isSearchActive) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isSearchActive) Pink500.copy(alpha = 0.2f) else Slate900)
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = if (isSearchActive) Pink400 else Slate200,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Notification Button with badge
                    Box {
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Slate900)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                tint = Slate200,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (unreadNotificationsCount > 0) {
                            Badge(
                                containerColor = Pink500,
                                contentColor = White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-2).dp, y = 2.dp)
                            ) {
                                Text(
                                    text = if (unreadNotificationsCount > 9) "9+" else "$unreadNotificationsCount",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Coins Button
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Amber400.copy(alpha = 0.15f),
                        border = null,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onOpenCoins() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(text = "🪙", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${currentUser.coins}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Amber400
                            )
                        }
                    }

                    // Admin Quick Tools (If Admin/SuperAdmin/Moderator)
                    if (currentUser.role == UserRole.SUPERADMIN || currentUser.role == UserRole.ADMIN || currentUser.role == UserRole.MODERATOR) {
                        val isSuperAdmin = currentUser.role == UserRole.SUPERADMIN
                        IconButton(
                            onClick = onOpenAdminDashboard,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isSuperAdmin) Amber500.copy(alpha = 0.25f) else Purple500.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = if (isSuperAdmin) Icons.Default.Shield else Icons.Default.AdminPanelSettings,
                                contentDescription = if (isSuperAdmin) "Super Admin Console" else "Admin",
                                tint = if (isSuperAdmin) Amber400 else Purple400,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // User Avatar / Account Details
                    AsyncImage(
                        model = currentUser.avatar,
                        contentDescription = "Account Details",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onOpenAuth() }
                    )
                }
            }

            // Search Bar Expanding Row
            AnimatedVisibility(visible = isSearchActive) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Slate900,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Pink400,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = {
                                Text(
                                    "ค้นหาโพสต์, เพื่อน, คลับ หรือ สตรีมเมอร์...",
                                    fontSize = 13.sp,
                                    color = Slate400
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = White,
                                unfocusedTextColor = White
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Slate400,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
