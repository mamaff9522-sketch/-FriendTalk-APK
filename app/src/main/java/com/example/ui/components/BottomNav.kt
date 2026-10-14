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

/** Turns off swiping between tabs while a screen needs horizontal drags itself (e.g. ปัดหาเพื่อน cards). */
object TabSwipe {
    var locked by mutableStateOf(false)
    /** Owner: hide the วิดีโอสั้น tab for now (the page and its code stay; set true to bring it back). */
    const val SHOW_SHORT_VIDEO_TAB = false
}

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
    if (TabSwipe.SHOW_SHORT_VIDEO_TAB && uiConfig.bottomNav.showMovieTab && uiConfig.isMovieFeatureEnabled) right.add(NavItem("movie", tr("วิดีโอสั้น"), Icons.Filled.Videocam, Icons.Outlined.Videocam))
    if (uiConfig.bottomNav.showChatTab) right.add(NavItem("chat", tr("แชท"), Icons.Filled.Sms, Icons.Outlined.Sms, badgeCount = totalUnreadMessages))
    right.add(NavItem("profile", tr("ฉัน"), Icons.Filled.Person, Icons.Outlined.Person))

    var creating by remember { mutableStateOf(false) }
    if (creating) com.example.ui.feed.CreatePostDialog(onDismiss = { creating = false }, onPosted = { com.example.ui.feed.HomeRepo.newPost.value = it; onTabSelected("home") })

    // Same colours as the top header (dark navy in โหมดมืด, purple gradient in โหมดปกติ), flat with a thin line on top.
    val active = PureWhite
    val idle = if (AppThemeMode.dark) Color(0xFFA79BC8) else Color(0xCCFFFFFF)
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(AppThemeMode.headerBrush)) {
            HorizontalDivider(color = AppThemeMode.divider.copy(alpha = 0.7f), thickness = 0.8.dp)
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 6.dp, end = 6.dp, top = 5.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 5 equal columns: 2 tabs · ＋ · 2 tabs
                left.forEach { NavTab(it, activeTab == it.id, active, idle) { onTabSelected(it.id) } }
                Spacer(Modifier.weight(1f)) // room for the raised ＋ button
                right.forEach { NavTab(it, activeTab == it.id, active, idle) { onTabSelected(it.id) } }
            }
        }
        // raised ＋ : opens the real post composer
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-16).dp).size(50.dp)
                .shadow(10.dp, CircleShape, ambientColor = Color(0xFFE040C8), spotColor = Color(0xFFE040C8))
                .clip(CircleShape).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFF4FB8), Color(0xFFB04DFF))))
                .border(2.5.dp, Color(0x80FFFFFF), CircleShape).clickable { creating = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, tr("สร้างโพสต์"), tint = PureWhite, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun RowScope.NavTab(item: NavItem, isSelected: Boolean, active: Color, idle: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 1.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isSelected) Box(Modifier.size(28.dp).background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x99D05CFF), Color.Transparent)), CircleShape))
            Icon(
                imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
                contentDescription = item.label,
                tint = if (isSelected) active else idle,
                modifier = Modifier.size(21.dp)
            )
            if (item.badgeCount > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-5).dp).defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                        .clip(CircleShape).background(Color(0xFFFF2D55)).border(1.5.dp, Color.White, CircleShape).padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (item.badgeCount > 99) "99+" else "${item.badgeCount}", color = PureWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 9.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = item.label + if (com.example.ui.components.AppGates.partLocked(if (item.id == "movie") "video" else item.id)) " 🔒" else "",
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) active else idle,
            maxLines = 1
        )
        Box(Modifier.padding(top = 2.dp).width(16.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp))
            .background(if (isSelected) Color(0xFFE040C8) else Color.Transparent))
    }
}
