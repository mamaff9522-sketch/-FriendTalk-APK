package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
@OptIn(ExperimentalFoundationApi::class)
fun AppHeader(
    currentUser: User,
    appName: String = "FriendTalk",
    showSearchBar: Boolean = true,
    unreadNotificationsCount: Int,
    onOpenCoins: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenProfile: () -> Unit = onOpenAuth,
    onOpenSearch: () -> Unit = {},
    uiConfig: AppUiConfig,
    onRefreshConfig: () -> Unit,
    isRefreshing: Boolean,
    onOpenAdminDashboard: () -> Unit,
    onOpenUiBuilder: () -> Unit,
    onOpenApkDownload: () -> Unit
) {
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
                    val signedIn = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Pink500, Purple600)))
                            .clickable { if (signedIn) onOpenProfile() else onOpenAuth() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (signedIn && currentUser.avatar.isNotBlank()) {
                            AsyncImage(
                                model = currentUser.avatar,
                                contentDescription = tr("เปิดโปรไฟล์ของฉัน"),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(42.dp).clip(CircleShape)
                            )
                        } else if (signedIn) {
                            Text(currentUser.displayName.take(1).ifBlank { "?" }, fontWeight = FontWeight.Black, fontSize = 19.sp, color = PureWhite)
                        } else {
                            Text(text = "FT", fontWeight = FontWeight.Black, fontSize = 19.sp, color = PureWhite)
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = appName.ifBlank { "FriendTalk" },
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
                            text = uiConfig.appSubtitle,
                            fontSize = 12.sp,
                            color = Slate300,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Header Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Notification Button with badge
                    Box {
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(1.dp, Slate700.copy(alpha = 0.6f), CircleShape).background(Slate900)
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
                                contentColor = PureWhite,
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
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF3A2A06),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Amber500.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onOpenCoins() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFFE680), Color(0xFFE0A800)))), contentAlignment = Alignment.Center) {
                                Text("1", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF8A5A00))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${currentUser.coins}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFFD54A)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(Modifier.size(22.dp).clip(CircleShape).background(Pink500), contentAlignment = Alignment.Center) {
                                Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                            }
                        }
                    }

                    // Admin Quick Tools (If Admin/SuperAdmin/Moderator)
                    if (currentUser.role == UserRole.SUPERADMIN || currentUser.role == UserRole.ADMIN || currentUser.role == UserRole.MODERATOR) {
                        val isSuperAdmin = currentUser.role == UserRole.SUPERADMIN
                        IconButton(
                            onClick = onOpenAdminDashboard,
                            modifier = Modifier
                                .size(44.dp)
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

                }
            }

            // Search Bar Expanding Row
            if (showSearchBar) {
                Surface(
                    onClick = onOpenSearch,
                    shape = RoundedCornerShape(18.dp),
                    color = Slate900.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Pink500, Purple500))),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = tr("ค้นหา"), tint = Pink400, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(tr("ค้นหาเพื่อน หรือ #แฮชแท็ก…"), fontSize = 14.sp, color = Slate400, modifier = Modifier.weight(1f))
                        Box(Modifier.width(1.dp).height(22.dp).background(Slate700))
                        Icon(Icons.Default.Tune, contentDescription = null, tint = Slate300, modifier = Modifier.padding(start = 10.dp).size(22.dp))
                    }
                }
            }
        }
    }
}
