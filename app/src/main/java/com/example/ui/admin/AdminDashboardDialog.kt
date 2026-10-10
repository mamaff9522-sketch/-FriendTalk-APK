package com.example.ui.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.theme.*

@Composable
fun AdminDashboardDialog(
    currentUser: User,
    users: List<User>,
    reports: List<Report>,
    withdrawals: List<WithdrawalRequest>,
    adminLogs: List<AdminLog>,
    onUpdateUserRole: (String, UserRole) -> Boolean,
    onBanUser: (String, String) -> Boolean,
    onUnbanUser: (String) -> Boolean,
    onAdjustWallet: (String, Int, Int) -> Boolean,
    onUpdateReportStatus: (String, ReportStatus) -> Boolean,
    onApproveWithdrawal: (String) -> Boolean,
    onRejectWithdrawal: (String, String) -> Boolean,
    onDismiss: () -> Unit,
    onOpenUiBuilder: () -> Unit,
    onOpenApkDownload: () -> Unit,
    onOpenCompanionAdmin: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Users, 1: Reports, 2: Withdrawals, 3: Logs, 4: Tools
    var userSearchQuery by remember { mutableStateOf("") }
    var userRoleFilter by remember { mutableStateOf("ALL") }

    // Dialog state for Role Change
    var targetRoleUser by remember { mutableStateOf<User?>(null) }
    // Dialog state for Ban
    var targetBanUser by remember { mutableStateOf<User?>(null) }
    var banReasonInput by remember { mutableStateOf("") }
    // Dialog state for Wallet Adjustment
    var targetWalletUser by remember { mutableStateOf<User?>(null) }
    var coinAdjustmentInput by remember { mutableStateOf("") }
    var diamondAdjustmentInput by remember { mutableStateOf("") }

    val isSuperAdmin = currentUser.role == UserRole.SUPERADMIN
    val isAppOwner = currentUser.email.equals("mama.ff9522@gmail.com", ignoreCase = true) ||
            currentUser.username.equals("mama", ignoreCase = true) || isSuperAdmin

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "แผงควบคุมระบบ (Admin Console)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isAppOwner) "👑" else "🛡️", fontSize = 16.sp)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "ผู้ดูแล: ${currentUser.displayName}",
                                fontSize = 11.sp,
                                color = Slate300
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSuperAdmin) Amber400.copy(alpha = 0.2f) else Purple500.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = if (isSuperAdmin) "SUPER ADMIN" else currentUser.role.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSuperAdmin) Amber400 else Purple400,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Tabs
                val tabTitles = listOf(
                    "👥 สมาชิก (${users.size})",
                    "🚨 รายงาน (${reports.count { it.status == ReportStatus.PENDING }})",
                    "💰 ถอนเงิน (${withdrawals.count { it.status == "PENDING" }})",
                    "📜 บันทึก",
                    "⚙️ เครื่องมือ",
                    "🤖 บอท AI",
                    "📊 ตั้งค่าลิมิต"
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tabTitles.size) { index ->
                        val selected = selectedTab == index
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) Pink600 else Slate800,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedTab = index }
                        ) {
                            Text(
                                text = tabTitles[index],
                                color = if (selected) White else Slate300,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Content
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> UsersTab(
                            users = users,
                            currentUser = currentUser,
                            searchQuery = userSearchQuery,
                            onSearchChange = { userSearchQuery = it },
                            roleFilter = userRoleFilter,
                            onRoleFilterChange = { userRoleFilter = it },
                            onChangeRoleClick = { targetRoleUser = it },
                            onBanClick = {
                                targetBanUser = it
                                banReasonInput = ""
                            },
                            onUnbanClick = { onUnbanUser(it.id) },
                            onWalletClick = {
                                targetWalletUser = it
                                coinAdjustmentInput = ""
                                diamondAdjustmentInput = ""
                            }
                        )
                        1 -> ReportsTab(
                            reports = reports,
                            onUpdateStatus = onUpdateReportStatus,
                            onQuickBan = { userId, reason -> onBanUser(userId, reason) }
                        )
                        2 -> WithdrawalsTab(
                            withdrawals = withdrawals,
                            onApprove = onApproveWithdrawal,
                            onReject = onRejectWithdrawal
                        )
                        3 -> LogsTab(logs = adminLogs)
                        4 -> ToolsTab(
                            onOpenUiBuilder = { onDismiss(); onOpenUiBuilder() },
                            onOpenCompanionAdmin = { onDismiss(); onOpenCompanionAdmin() },
                            onOpenApkDownload = { onDismiss(); onOpenApkDownload() }
                        )
                        5 -> com.example.ui.ai.AdminBotsTab()
                        6 -> AdminLimitsTab()
                    }
                }
            }
        }
    }

    // Role Selection Dialog
    if (targetRoleUser != null) {
        val target = targetRoleUser!!
        Dialog(onDismissRequest = { targetRoleUser = null }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Slate900,
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "เปลี่ยนระดับสิทธิ์: ${target.displayName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ระดับปัจจุบัน: ${target.role.name}",
                        fontSize = 12.sp,
                        color = Slate400
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    UserRole.values().forEach { role ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (target.role == role) Purple600.copy(alpha = 0.3f) else Slate800,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onUpdateUserRole(target.id, role)
                                    targetRoleUser = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (role) {
                                        UserRole.SUPERADMIN -> "👑 SUPERADMIN (เจ้าของระบบ)"
                                        UserRole.ADMIN -> "🛡️ ADMIN (ผู้ดูแลระบบ)"
                                        UserRole.MODERATOR -> "⚖️ MODERATOR (ผู้ตรวจสอบ)"
                                        UserRole.CREATOR -> "⭐ CREATOR (ครีเอเตอร์)"
                                        UserRole.USER -> "👤 USER (ผู้ใช้ทั่วไป)"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = White
                                )
                                if (target.role == role) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Emerald400, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { targetRoleUser = null },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ยกเลิก", color = Slate300)
                    }
                }
            }
        }
    }

    // Ban User Dialog
    if (targetBanUser != null) {
        val target = targetBanUser!!
        Dialog(onDismissRequest = { targetBanUser = null }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Slate900,
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ระงับการใช้งานบัญชี 🚫",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Rose500
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ระงับบัญชี ${target.displayName} (@${target.username})",
                        fontSize = 13.sp,
                        color = Slate300
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = banReasonInput,
                        onValueChange = { banReasonInput = it },
                        label = { Text("ระบุเหตุผลในการระงับ", color = Slate400) },
                        placeholder = { Text("เช่น โพสต์ภาพไม่เหมาะสม, ส่งข้อความก่อกวน", color = Slate500) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Rose500,
                            unfocusedBorderColor = Slate700
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { targetBanUser = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ยกเลิก", color = Slate300)
                        }
                        Button(
                            onClick = {
                                onBanUser(target.id, banReasonInput)
                                targetBanUser = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ยืนยันระงับ", color = White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Wallet Adjustment Dialog
    if (targetWalletUser != null) {
        val target = targetWalletUser!!
        Dialog(onDismissRequest = { targetWalletUser = null }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Slate900,
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ปรับยอดเงินกระเป๋า: ${target.displayName} 🪙",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Amber400
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ยอดปัจจุบัน: ${target.coins} เหรียญ | ${target.diamonds} เพชร",
                        fontSize = 12.sp,
                        color = Slate300
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = coinAdjustmentInput,
                        onValueChange = { coinAdjustmentInput = it },
                        label = { Text("ปรับเหรียญ (+ หรือ -)", color = Slate400) },
                        placeholder = { Text("เช่น +500 หรือ -200", color = Slate500) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Amber400,
                            unfocusedBorderColor = Slate700
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = diamondAdjustmentInput,
                        onValueChange = { diamondAdjustmentInput = it },
                        label = { Text("ปรับเพชร (+ หรือ -)", color = Slate400) },
                        placeholder = { Text("เช่น +100 หรือ -50", color = Slate500) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Cyan400,
                            unfocusedBorderColor = Slate700
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { targetWalletUser = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ยกเลิก", color = Slate300)
                        }
                        Button(
                            onClick = {
                                val dCoins = coinAdjustmentInput.trim().toIntOrNull() ?: 0
                                val dDiamonds = diamondAdjustmentInput.trim().toIntOrNull() ?: 0
                                onAdjustWallet(target.id, dCoins, dDiamonds)
                                targetWalletUser = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Amber500),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("บันทึก", color = White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersTab(
    users: List<User>,
    currentUser: User,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    roleFilter: String,
    onRoleFilterChange: (String) -> Unit,
    onChangeRoleClick: (User) -> Unit,
    onBanClick: (User) -> Unit,
    onUnbanClick: (User) -> Unit,
    onWalletClick: (User) -> Unit
) {
    val isSuperAdmin = currentUser.role == UserRole.SUPERADMIN

    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Filter
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("ค้นหาชื่อ, @username, อีเมล, UID...", color = Slate500, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = White,
                unfocusedTextColor = White,
                focusedBorderColor = Pink500,
                unfocusedBorderColor = Slate800,
                focusedContainerColor = Slate800,
                unfocusedContainerColor = Slate800
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Role filter chips
        val filterOptions = listOf("ALL", "SUPERADMIN", "ADMIN", "MODERATOR", "CREATOR", "USER", "BANNED")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filterOptions) { opt ->
                val isSelected = roleFilter == opt
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Purple600 else Slate800,
                    modifier = Modifier.clickable { onRoleFilterChange(opt) }
                ) {
                    Text(
                        text = when (opt) {
                            "ALL" -> "ทั้งหมด"
                            "BANNED" -> "🚫 ถูกระงับ"
                            else -> opt
                        },
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) White else Slate300,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filtered Users
        val filteredUsers = users.filter { u ->
            val matchesQuery = searchQuery.isBlank() ||
                    u.displayName.contains(searchQuery, ignoreCase = true) ||
                    u.username.contains(searchQuery, ignoreCase = true) ||
                    u.email.contains(searchQuery, ignoreCase = true) ||
                    u.id.contains(searchQuery, ignoreCase = true)

            val matchesRole = when (roleFilter) {
                "ALL" -> true
                "BANNED" -> u.isBanned
                else -> u.role.name == roleFilter
            }
            matchesQuery && matchesRole
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredUsers) { u ->
                val isTargetSuperAdmin = u.role == UserRole.SUPERADMIN ||
                        u.email.equals("mama.ff9522@gmail.com", ignoreCase = true) ||
                        u.username.equals("mama", ignoreCase = true)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Slate800,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AsyncImage(
                                model = u.avatar,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, if (isTargetSuperAdmin) Amber400 else Slate700, CircleShape)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = u.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    if (isTargetSuperAdmin) {
                                        Text("👑", fontSize = 12.sp)
                                    }
                                }
                                Text(
                                    text = "@${u.username} • UID: ${u.id.take(8)}...",
                                    fontSize = 11.sp,
                                    color = Slate400
                                )
                                if (u.email.isNotBlank()) {
                                    Text(
                                        text = u.email,
                                        fontSize = 10.sp,
                                        color = Slate300
                                    )
                                }
                            }

                            // Role & Ban Badges
                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when (u.role) {
                                        UserRole.SUPERADMIN -> Amber400.copy(alpha = 0.2f)
                                        UserRole.ADMIN -> Color(0xFF3B82F6).copy(alpha = 0.2f)
                                        UserRole.MODERATOR -> Amber500.copy(alpha = 0.15f)
                                        UserRole.CREATOR -> Pink500.copy(alpha = 0.15f)
                                        UserRole.USER -> Slate700
                                    }
                                ) {
                                    Text(
                                        text = u.role.name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (u.role) {
                                            UserRole.SUPERADMIN -> Amber400
                                            UserRole.ADMIN -> Color(0xFF60A5FA)
                                            UserRole.MODERATOR -> Amber400
                                            UserRole.CREATOR -> Pink400
                                            UserRole.USER -> Slate300
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                if (u.isBanned) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Rose500.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "🚫 ถูกระงับ",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Rose500,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (u.isBanned && u.banReason.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "เหตุผล: ${u.banReason}",
                                fontSize = 10.sp,
                                color = Rose500
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Stats & Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🪙 ${u.coins} • 💎 ${u.diamonds}",
                                fontSize = 11.sp,
                                color = Amber400
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // SuperAdmin action: Change role
                                if (isSuperAdmin && !isTargetSuperAdmin) {
                                    Button(
                                        onClick = { onChangeRoleClick(u) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Purple600),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("สิทธิ์", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // SuperAdmin action: Adjust wallet
                                if (isSuperAdmin) {
                                    Button(
                                        onClick = { onWalletClick(u) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Amber500),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("เงิน", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Black)
                                    }
                                }

                                // Ban / Unban button
                                if (!isTargetSuperAdmin) {
                                    if (u.isBanned) {
                                        Button(
                                            onClick = { onUnbanClick(u) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("ปลดแบน", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = { onBanClick(u) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("แบน", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportsTab(
    reports: List<Report>,
    onUpdateStatus: (String, ReportStatus) -> Boolean,
    onQuickBan: (String, String) -> Boolean
) {
    var statusFilter by remember { mutableStateOf<ReportStatus?>(ReportStatus.PENDING) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ReportStatus.values().forEach { st ->
                val selected = statusFilter == st
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) Pink600 else Slate800,
                    modifier = Modifier.clickable { statusFilter = if (selected) null else st }
                ) {
                    Text(
                        text = when (st) {
                            ReportStatus.PENDING -> "รอดำเนินการ"
                            ReportStatus.REVIEWED -> "ตรวจสอบแล้ว"
                            ReportStatus.RESOLVED -> "แก้ไขแล้ว"
                            ReportStatus.DISMISSED -> "ปฏิเสธ"
                        },
                        fontSize = 11.sp,
                        color = if (selected) White else Slate300,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val filteredReports = reports.filter { statusFilter == null || it.status == statusFilter }

        if (filteredReports.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("ไม่มีรายงานในหมวดนี้ 🎉", color = Slate400, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredReports) { rep ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Slate800,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "[${rep.targetType}] ${rep.targetName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = White
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when (rep.status) {
                                        ReportStatus.PENDING -> Rose500.copy(alpha = 0.2f)
                                        ReportStatus.REVIEWED -> Amber400.copy(alpha = 0.2f)
                                        ReportStatus.RESOLVED -> Emerald400.copy(alpha = 0.2f)
                                        ReportStatus.DISMISSED -> Slate700
                                    }
                                ) {
                                    Text(
                                        text = rep.status.name,
                                        fontSize = 10.sp,
                                        color = when (rep.status) {
                                            ReportStatus.PENDING -> Rose500
                                            ReportStatus.REVIEWED -> Amber400
                                            ReportStatus.RESOLVED -> Emerald400
                                            ReportStatus.DISMISSED -> Slate400
                                        },
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "เหตุผล: ${rep.reason}", fontSize = 12.sp, color = Amber300)
                            if (rep.details.isNotBlank()) {
                                Text(text = "รายละเอียด: ${rep.details}", fontSize = 11.sp, color = Slate300)
                            }
                            Text(text = "ผู้แจ้ง: ${rep.reporterName} • ${rep.timestamp}", fontSize = 10.sp, color = Slate400)

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (rep.status == ReportStatus.PENDING) {
                                    Button(
                                        onClick = { onUpdateStatus(rep.id, ReportStatus.REVIEWED) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Purple600),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("ตรวจแล้ว", fontSize = 10.sp)
                                    }
                                }
                                if (rep.status != ReportStatus.RESOLVED) {
                                    Button(
                                        onClick = { onUpdateStatus(rep.id, ReportStatus.RESOLVED) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("จัดการแล้ว", fontSize = 10.sp)
                                    }
                                }
                                if (rep.status != ReportStatus.DISMISSED) {
                                    OutlinedButton(
                                        onClick = { onUpdateStatus(rep.id, ReportStatus.DISMISSED) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("ยกเลิก", fontSize = 10.sp, color = Slate300)
                                    }
                                }
                                if (rep.targetType == "USER") {
                                    Button(
                                        onClick = { onQuickBan(rep.targetId, "ละเมิดกฎจากรายงาน: ${rep.reason}") },
                                        colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("แบนทันที", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WithdrawalsTab(
    withdrawals: List<WithdrawalRequest>,
    onApprove: (String) -> Boolean,
    onReject: (String, String) -> Boolean
) {
    if (withdrawals.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("ไม่มีคำขอถอนเงิน", color = Slate400, fontSize = 13.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(withdrawals) { wd ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Slate800,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${wd.userName} (${wd.amountBaht} บาท)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = White
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (wd.status) {
                                    "PENDING" -> Amber400.copy(alpha = 0.2f)
                                    "APPROVED" -> Emerald400.copy(alpha = 0.2f)
                                    else -> Rose500.copy(alpha = 0.2f)
                                }
                            ) {
                                Text(
                                    text = wd.status,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (wd.status) {
                                        "PENDING" -> Amber400
                                        "APPROVED" -> Emerald400
                                        else -> Rose500
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "💎 ${wd.amountDiamonds} เพชร • ${wd.bankName} เลขบัญชี: ${wd.accountNumber}",
                            fontSize = 12.sp,
                            color = Slate300
                        )
                        Text(
                            text = "ชื่อบัญชี: ${wd.accountHolder} • ยื่นเมื่อ: ${wd.requestedAt}",
                            fontSize = 11.sp,
                            color = Slate400
                        )

                        if (wd.status == "PENDING") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onApprove(wd.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("อนุมัติโอนเงิน", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { onReject(wd.id, "ข้อมูลบัญชีไม่ถูกต้อง") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("ปฏิเสธ", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogsTab(logs: List<AdminLog>) {
    if (logs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("ยังไม่มีบันทึกกิจกรรม", color = Slate400, fontSize = 13.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs) { log ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate800,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = log.action,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Amber400
                            )
                            Text(
                                text = log.timestamp,
                                fontSize = 10.sp,
                                color = Slate400
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = log.details,
                            fontSize = 11.sp,
                            color = Slate200
                        )
                        Text(
                            text = "โดย: ${log.adminName}",
                            fontSize = 10.sp,
                            color = Slate400
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsTab(
    onOpenUiBuilder: () -> Unit,
    onOpenCompanionAdmin: () -> Unit,
    onOpenApkDownload: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onOpenUiBuilder,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple600)
        ) {
            Icon(Icons.Default.DesignServices, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Admin UI Builder (ปรับแต่งหน้าตาและ Feed)", fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onOpenCompanionAdmin,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Pink600)
        ) {
            Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("จัดการระบบเพื่อนคุย (Companion 18+)", fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onOpenApkDownload,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Slate800)
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("ประวัติเวอร์ชันและดาวน์โหลด APK", fontWeight = FontWeight.Bold, color = White)
        }
    }
}
