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

    // Clean floating bar: solid surface (dark / light), icons in an M3-style indicator pill, gradient pill on the active tab,
    // raised gradient ＋ ringed with the bar colour. Labels use the plain Material Text so UI-Builder text overrides can't restyle them.
    val dark = AppThemeMode.dark
    val barBg = if (dark) Color(0xFF161132) else Color(0xFFFFFFFF)
    val idle = if (dark) Color(0xFF9A90C2) else Color(0xFF8A7BA8)
    val activeLabel = if (dark) Color(0xFFFF6CC4) else Color(0xFFD12C8A)
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    // Docked edge-to-edge bar: fills the full width and continues under the system navigation bar.
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth()
                .shadow(14.dp, shape, ambientColor = Color(0x55B04DFF), spotColor = Color(0x55FF2FA8))
                .clip(shape)
                .background(barBg)
                .border(1.dp, if (dark) Color(0x26FFFFFF) else Color(0x1F6B2FB0), shape)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(58.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 5 equal columns: 2 tabs · ＋ · 2 tabs
                left.forEach { NavTab(it, activeTab == it.id, activeLabel, idle) { onTabSelected(it.id) } }
                Spacer(Modifier.weight(1f)) // room for the raised ＋ button
                right.forEach { NavTab(it, activeTab == it.id, activeLabel, idle) { onTabSelected(it.id) } }
            }
        }
        // raised ＋ : opens the real post composer
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-16).dp).size(52.dp)
                .clip(CircleShape).background(barBg).padding(3.dp)
                .shadow(10.dp, CircleShape, ambientColor = Color(0xFFFF2FA8), spotColor = Color(0xFFFF2FA8))
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFF4FB8), Color(0xFF9B3DFF))))
                .clickable { creating = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, tr("สร้างโพสต์"), tint = PureWhite, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun RowScope.NavTab(item: NavItem, isSelected: Boolean, activeLabel: Color, idle: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.weight(1f).fillMaxHeight().clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // indicator pill behind the icon
            Box(
                Modifier.size(width = 46.dp, height = 26.dp).clip(RoundedCornerShape(13.dp))
                    .then(if (isSelected) Modifier.background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFFF4FB8), Color(0xFF9B3DFF)))) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
                    contentDescription = item.label,
                    tint = if (isSelected) PureWhite else idle,
                    modifier = Modifier.size(19.dp)
                )
            }
            if (item.badgeCount > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                        .clip(CircleShape).background(Color(0xFFFF2D55)).border(1.5.dp, Color.White, CircleShape).padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(if (item.badgeCount > 99) "99+" else "${item.badgeCount}", color = PureWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 9.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        androidx.compose.material3.Text(
            text = item.label + if (com.example.ui.components.AppGates.partLocked(if (item.id == "movie") "video" else item.id)) " 🔒" else "",
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) activeLabel else idle,
            maxLines = 1
        )
    }
}
