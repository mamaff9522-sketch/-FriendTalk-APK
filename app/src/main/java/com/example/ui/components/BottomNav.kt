package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppUiConfig
import com.example.ui.theme.*

data class NavItem(
    val id: String,
    val label: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
    val badgeCount: Int = 0,
    val isSpecial: Boolean = false
)

@Composable
fun BottomNavBar(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    totalUnreadMessages: Int,
    activeLiveCount: Int,
    currentUserAge: Int,
    uiConfig: AppUiConfig
) {
    val items = mutableListOf<NavItem>()

    if (uiConfig.bottomNav.showHomeTab) {
        items.add(NavItem("home", "หน้าแรก", Icons.Filled.Home, Icons.Outlined.Home))
    }
    if (uiConfig.bottomNav.showDiscoverTab) {
        items.add(NavItem("discover", "ค้นหา", Icons.Filled.Explore, Icons.Outlined.Explore))
    }
    if (uiConfig.bottomNav.showLiveTab && uiConfig.isLiveStreamingEnabled) {
        items.add(NavItem("live", "ไลฟ์สด", Icons.Filled.LiveTv, Icons.Outlined.LiveTv, badgeCount = activeLiveCount))
    }
    if (uiConfig.bottomNav.showMovieTab && uiConfig.isMovieFeatureEnabled) {
        items.add(NavItem("movie", "วิดีโอ", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircleOutline))
    }
    if (uiConfig.bottomNav.showChatTab) {
        items.add(NavItem("chat", "แชต", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, badgeCount = totalUnreadMessages))
    }
    if (uiConfig.bottomNav.showCompanionTab && uiConfig.isCompanionFeatureEnabled && currentUserAge >= 18) {
        items.add(NavItem("companion", "เพื่อนคุย", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, isSpecial = true))
    }
    if (uiConfig.bottomNav.showProfileTab) {
        items.add(NavItem("profile", "โปรไฟล์", Icons.Filled.Person, Icons.Outlined.PersonOutline))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = activeTab == item.id

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(item.id) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(width = 44.dp, height = 28.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (item.isSpecial) Rose500.copy(alpha = 0.25f)
                                        else Pink500.copy(alpha = 0.25f)
                                    )
                            )
                        }

                        Icon(
                            imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
                            contentDescription = item.label,
                            tint = when {
                                item.isSpecial && isSelected -> Rose500
                                isSelected -> Pink500
                                item.isSpecial -> Rose500.copy(alpha = 0.7f)
                                else -> Slate400
                            },
                            modifier = Modifier.size(22.dp)
                        )

                        if (item.badgeCount > 0) {
                            Badge(
                                containerColor = if (item.id == "live") Rose500 else Pink500,
                                contentColor = White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-4).dp)
                            ) {
                                Text(
                                    text = if (item.badgeCount > 9) "9+" else "${item.badgeCount}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            item.isSpecial && isSelected -> Rose500
                            isSelected -> Pink400
                            else -> Slate400
                        }
                    )
                }
            }
        }
    }
}
