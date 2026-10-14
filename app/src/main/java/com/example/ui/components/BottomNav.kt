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
import androidx.compose.runtime.*
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
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
    // Mockup nav (all pages): หน้าแรก · หาเพื่อน · [＋ create post] · วิดีโอสั้น · แชท · ฉัน  (admin toggles still hide a tab)
    val left = mutableListOf<NavItem>()
    val right = mutableListOf<NavItem>()
    if (uiConfig.bottomNav.showHomeTab) left.add(NavItem("home", tr("หน้าแรก"), Icons.Filled.Home, Icons.Outlined.Home))
    if (uiConfig.bottomNav.showDiscoverTab) left.add(NavItem("discover", tr("หาเพื่อน"), Icons.Filled.People, Icons.Outlined.People))
    if (uiConfig.bottomNav.showMovieTab && uiConfig.isMovieFeatureEnabled) right.add(NavItem("movie", tr("วิดีโอสั้น"), Icons.Filled.Videocam, Icons.Outlined.Videocam))
    if (uiConfig.bottomNav.showChatTab) right.add(NavItem("chat", tr("แชท"), Icons.Filled.Sms, Icons.Outlined.Sms, badgeCount = totalUnreadMessages))
    right.add(NavItem("profile", tr("ฉัน"), Icons.Filled.Person, Icons.Outlined.Person))

    var creating by remember { mutableStateOf(false) }
    if (creating) com.example.ui.feed.CreatePostDialog(onDismiss = { creating = false }, onPosted = { com.example.ui.feed.HomeRepo.newPost.value = it; onTabSelected("home") })

    val active = Color(0xFFF2D4FF)
    val idle = Color(0xFFA79BC8)
    Box(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = Color(0xFF1B1530),
            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0x55B04DFF)),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                left.forEach { NavTab(it, activeTab == it.id, active, idle) { onTabSelected(it.id) } }
                Spacer(Modifier.weight(1.1f)) // room for the raised ＋ button
                right.forEach { NavTab(it, activeTab == it.id, active, idle) { onTabSelected(it.id) } }
            }
        }
        // raised ＋ : opens the real post composer
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-26).dp).size(66.dp)
                .shadow(14.dp, CircleShape, ambientColor = Color(0xFFE040C8), spotColor = Color(0xFFE040C8))
                .clip(CircleShape).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFF4FB8), Color(0xFFB04DFF))))
                .border(3.dp, Color(0x66FFFFFF), CircleShape).clickable { creating = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, tr("สร้างโพสต์"), tint = PureWhite, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun RowScope.NavTab(item: NavItem, isSelected: Boolean, active: Color, idle: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 2.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isSelected) Box(Modifier.size(38.dp).background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x99D05CFF), Color.Transparent)), CircleShape))
            Icon(
                imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
                contentDescription = item.label,
                tint = if (isSelected) Color(0xFFE7B5FF) else idle,
                modifier = Modifier.size(27.dp)
            )
            if (item.badgeCount > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-7).dp).defaultMinSize(minWidth = 19.dp, minHeight = 19.dp)
                        .clip(CircleShape).background(Color(0xFFFF2D55)).border(1.5.dp, Color(0xFF1B1530), CircleShape).padding(horizontal = 4.dp),
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
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) active else idle,
            maxLines = 1
        )
        Box(Modifier.padding(top = 3.dp).width(34.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
            .background(if (isSelected) Color(0xFFE040C8) else Color.Transparent))
    }
}
