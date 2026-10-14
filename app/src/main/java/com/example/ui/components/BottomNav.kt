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
import com.example.ui.components.ft.Text
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
) = UiPage("nav") {
    // Mockup nav: หน้าหลัก · หาเพื่อน · ข้อความ · วิดีโอ · โปรไฟล์ (admin toggles still hide a tab)
    val items = mutableListOf<NavItem>()
    if (uiConfig.bottomNav.showHomeTab) items.add(NavItem("home", tr("หน้าหลัก"), Icons.Filled.Home, Icons.Outlined.Home))
    if (uiConfig.bottomNav.showDiscoverTab) items.add(NavItem("discover", tr("หาเพื่อน"), Icons.Filled.Place, Icons.Outlined.Place))
    if (uiConfig.bottomNav.showChatTab) items.add(NavItem("chat", tr("ข้อความ"), Icons.Filled.Sms, Icons.Outlined.Sms, badgeCount = totalUnreadMessages))
    if (uiConfig.bottomNav.showMovieTab && uiConfig.isMovieFeatureEnabled) items.add(NavItem("movie", tr("วิดีโอ"), Icons.Filled.SmartDisplay, Icons.Outlined.SmartDisplay))
    items.add(NavItem("profile", tr("โปรไฟล์"), Icons.Filled.Person, Icons.Outlined.Person))

    val active = Color(0xFFC77DFF)
    val idle = Color(0xFFCFC9EE)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0C0A24),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xB32A2357)),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = activeTab == item.id
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onTabSelected(item.id) }.padding(vertical = 2.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // soft violet glow behind the selected icon
                        if (isSelected) Box(Modifier.size(34.dp).background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x80B04DFF), Color.Transparent)), CircleShape))
                        Icon(
                            imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
                            contentDescription = item.label,
                            tint = if (isSelected) active else idle,
                            modifier = Modifier.size(26.dp)
                        )
                        if (item.badgeCount > 0) {
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-6).dp).defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                    .clip(CircleShape).background(Color(0xFFFF2D6F)).padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (item.badgeCount > 99) "99+" else "${item.badgeCount}", color = PureWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.label + if (com.example.ui.components.AppGates.partLocked(if (item.id == "movie") "video" else item.id)) " 🔒" else "",
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) active else idle,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
