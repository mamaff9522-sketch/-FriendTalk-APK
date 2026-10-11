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
        items.add(NavItem("home", tr("หน้าแรก"), Icons.Filled.Home, Icons.Outlined.Home))
    }
    if (uiConfig.bottomNav.showDiscoverTab) {
        items.add(NavItem("discover", tr("หาเพื่อน"), Icons.Filled.People, Icons.Outlined.People))
    }
    if (uiConfig.bottomNav.showLiveTab && uiConfig.isLiveStreamingEnabled) {
        items.add(NavItem("live", tr("ไลฟ์สด"), Icons.Filled.LiveTv, Icons.Outlined.LiveTv, badgeCount = activeLiveCount))
    }
    if (uiConfig.bottomNav.showMovieTab && uiConfig.isMovieFeatureEnabled) {
        items.add(NavItem("movie", tr("วิดีโอ"), Icons.Filled.PlayCircle, Icons.Outlined.PlayCircleOutline))
    }
    if (uiConfig.bottomNav.showChatTab) {
        items.add(NavItem("chat", tr("แชท"), Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, badgeCount = totalUnreadMessages))
    }
    // 'เพื่อนคุย' (companion, 18+) moved to a tile on the 'หาเพื่อน' page.
    // Profile moved to the header avatar; this slot is the standalone ads page.
    items.add(NavItem("ads", tr("โฆษณา"), Icons.Filled.Star, Icons.Outlined.StarBorder))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Slate950,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Slate700.copy(alpha = 0.5f)),
        tonalElevation = 0.dp
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
                                    .size(width = 60.dp, height = 34.dp)
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Pink500.copy(alpha = 0.35f), Purple500.copy(alpha = 0.30f))))
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
                            modifier = Modifier.size(25.dp)
                        )

                        if (item.badgeCount > 0) {
                            Badge(
                                containerColor = if (item.id == "live") Rose500 else Pink500,
                                contentColor = PureWhite,
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
                        fontSize = 12.sp,
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
