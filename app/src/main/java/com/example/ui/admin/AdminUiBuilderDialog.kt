package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.*
import com.example.ui.theme.*

@Composable
fun AdminUiBuilderDialog(
    currentConfig: AppUiConfig,
    versionHistory: List<ConfigVersionRecord>,
    onDismiss: () -> Unit,
    onPublish: (AppUiConfig, String) -> Unit,
    onRollback: (String) -> Unit,
    onRestoreDefault: () -> Unit
) {
    var draftConfig by remember { mutableStateOf(currentConfig.copy()) }
    var selectedBuilderTab by remember { mutableStateOf("feed") } // feed, theme, history
    var releaseNotes by remember { mutableStateOf("ปรับแต่งหน้า Feed และระบบ UI") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = Slate950,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Admin UI Builder 🎨",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = White
                        )
                        Text(
                            text = "Active Version: ${currentConfig.configVersion}",
                            fontSize = 11.sp,
                            color = Pink400
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Builder Category Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "visual_dnd" to "ลากวาง UI 🎨",
                        "feed" to "จัด Feed 📰",
                        "theme" to "ธีม & สี 🌈",
                        "remote" to "Config ⚙️",
                        "preview" to "ตัวอย่าง 📱",
                        "history" to "ประวัติ ⏳"
                    ).forEach { (key, label) ->
                        val isSel = selectedBuilderTab == key
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) Pink500 else Slate800,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedBuilderTab = key }
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) White else Slate300,
                                modifier = Modifier
                                    .padding(vertical = 6.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Content area
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedBuilderTab) {
                        "visual_dnd" -> com.example.ui.layout.HomeLayoutEditor()
                        "feed" -> FeedLayoutBuilder(
                            feedConfig = draftConfig.feed,
                            onUpdateFeedConfig = { updatedFeed ->
                                draftConfig = draftConfig.copy(feed = updatedFeed)
                            }
                        )
                        "theme" -> ThemeBuilder(
                            themeConfig = draftConfig.theme,
                            onUpdateThemeConfig = { updatedTheme ->
                                draftConfig = draftConfig.copy(theme = updatedTheme)
                            }
                        )
                        "remote" -> RemoteConfigBuilder(
                            draftConfig = draftConfig,
                            onUpdateConfig = { updated ->
                                draftConfig = updated
                            }
                        )
                        "preview" -> DeviceLivePreview(
                            draftConfig = draftConfig
                        )
                        "history" -> VersionHistoryView(
                            history = versionHistory,
                            onRollback = onRollback
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Slate800)
                Spacer(modifier = Modifier.height(8.dp))

                // Action Bar: Release notes + Publish Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onRestoreDefault,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("รีเซ็ตเริ่มต้น", fontSize = 11.sp, color = Slate300)
                    }

                    Button(
                        onClick = {
                            onPublish(draftConfig, releaseNotes)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Pink500)
                    ) {
                        Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("เผยแพร่ UI เวอร์ชันใหม่", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FeedLayoutBuilder(
    feedConfig: FeedSectionConfig,
    onUpdateFeedConfig: (FeedSectionConfig) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Toggle Sections
        item {
            Text(text = "1. เปิด/ปิด Section ในหน้าแรก (Feed Layout Toggles):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Pink400)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = Slate900, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    SettingToggleRow(
                        title = "แสดง Header ด้านบน",
                        checked = feedConfig.showHeader,
                        onCheckedChange = { onUpdateFeedConfig(feedConfig.copy(showHeader = it)) }
                    )
                    SettingToggleRow(
                        title = "แสดง Category Tabs ใต้ Header",
                        checked = feedConfig.showCategoryTabs,
                        onCheckedChange = { onUpdateFeedConfig(feedConfig.copy(showCategoryTabs = it)) }
                    )
                    SettingToggleRow(
                        title = "แสดง Story / Quick Access Row",
                        checked = feedConfig.showStoryRow,
                        onCheckedChange = { onUpdateFeedConfig(feedConfig.copy(showStoryRow = it)) }
                    )
                    SettingToggleRow(
                        title = "แสดง คลับและคอมมูนิตี้ยอดนิยม (Clubs Snippet)",
                        checked = feedConfig.showClubsSnippet,
                        onCheckedChange = { onUpdateFeedConfig(feedConfig.copy(showClubsSnippet = it)) }
                    )
                }
            }
        }

        // 2. Post Card Style
        item {
            Text(text = "2. รูปแบบการแสดงผล Post Card (Card Style):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Pink400)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PostCardStyle.MODERN_CARD to "Modern Card",
                    PostCardStyle.COMPACT to "Compact",
                    PostCardStyle.BORDERLESS to "Borderless"
                ).forEach { (style, label) ->
                    val isSel = feedConfig.postCardStyle == style
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSel) Pink500 else Slate800,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onUpdateFeedConfig(feedConfig.copy(postCardStyle = style)) }
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) White else Slate300,
                            modifier = Modifier.padding(vertical = 10.dp).wrapContentWidth(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }

        // 3. Spacing & Corner Radius & Text Scale
        item {
            Text(text = "3. ปรับแต่งระยะห่าง มุมโค้ง และขนาดตัวอักษร:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Pink400)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = Slate900, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "มุมโค้ง Card: ${feedConfig.cardCornerRadiusDp} dp", fontSize = 12.sp, color = Slate300)
                    Slider(
                        value = feedConfig.cardCornerRadiusDp.toFloat(),
                        onValueChange = { onUpdateFeedConfig(feedConfig.copy(cardCornerRadiusDp = it.toInt())) },
                        valueRange = 4f..32f,
                        colors = SliderDefaults.colors(thumbColor = Pink500, activeTrackColor = Pink500)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "ระยะห่างระหว่างโพสต์: ${feedConfig.postSpacingDp} dp", fontSize = 12.sp, color = Slate300)
                    Slider(
                        value = feedConfig.postSpacingDp.toFloat(),
                        onValueChange = { onUpdateFeedConfig(feedConfig.copy(postSpacingDp = it.toInt())) },
                        valueRange = 4f..24f,
                        colors = SliderDefaults.colors(thumbColor = Pink500, activeTrackColor = Pink500)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "ขนาดข้อความ: ${String.format("%.1f", feedConfig.textSizeScale)}x", fontSize = 12.sp, color = Slate300)
                    Slider(
                        value = feedConfig.textSizeScale,
                        onValueChange = { onUpdateFeedConfig(feedConfig.copy(textSizeScale = it)) },
                        valueRange = 0.8f..1.4f,
                        colors = SliderDefaults.colors(thumbColor = Pink500, activeTrackColor = Pink500)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 13.sp, color = White)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = Pink500)
        )
    }
}

@Composable
fun ThemeBuilder(
    themeConfig: AppUiThemeConfig,
    onUpdateThemeConfig: (AppUiThemeConfig) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(text = "ชุดสี Accent & พื้นหลัง:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Pink400)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = Slate900, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("เลือก Palette สำเร็จรูป:", fontSize = 12.sp, color = Slate400)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onUpdateThemeConfig(
                                    themeConfig.copy(
                                        primaryColorHex = "#EC4899",
                                        secondaryColorHex = "#A855F7",
                                        accentColorHex = "#22D3EE"
                                    )
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Pink600)
                        ) {
                            Text("Pink & Purple", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                onUpdateThemeConfig(
                                    themeConfig.copy(
                                        primaryColorHex = "#6366F1",
                                        secondaryColorHex = "#EC4899",
                                        accentColorHex = "#FBBF24"
                                    )
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo500)
                        ) {
                            Text("Indigo Neon", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VersionHistoryView(
    history: List<ConfigVersionRecord>,
    onRollback: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(history) { record ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (record.isActive) Pink500.copy(alpha = 0.15f) else Slate900,
                border = if (record.isActive) androidx.compose.foundation.BorderStroke(1.dp, Pink500) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = record.version, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = White)
                        if (record.isActive) {
                            Surface(color = Pink500, shape = RoundedCornerShape(4.dp)) {
                                Text("ACTIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onRollback(record.version) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("ย้อนกลับ (Rollback)", fontSize = 10.sp, color = Cyan400)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = record.notes, fontSize = 12.sp, color = Slate300)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "เผยแพร่โดย: ${record.publishedBy} • ${record.publishedAt}", fontSize = 10.sp, color = Slate500)
                }
            }
        }
    }
}

@Composable
fun RemoteConfigBuilder(
    draftConfig: AppUiConfig,
    onUpdateConfig: (AppUiConfig) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(text = "1. ชื่อแอปพลิเคชัน & API กลาง (Branding & Core API):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Pink400)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = Slate900, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draftConfig.appName,
                        onValueChange = { onUpdateConfig(draftConfig.copy(appName = it)) },
                        label = { Text("ชื่อแอป (App Name)", color = Slate400, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Pink500,
                            unfocusedBorderColor = Slate700
                        )
                    )
                    OutlinedTextField(
                        value = draftConfig.apiEndpoint,
                        onValueChange = { onUpdateConfig(draftConfig.copy(apiEndpoint = it)) },
                        label = { Text("API Gateway / Remote Endpoint", color = Slate400, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Pink500,
                            unfocusedBorderColor = Slate700
                        )
                    )
                }
            }
        }

        item {
            Text(text = "2. โหมดปิดปรับปรุงเซิร์ฟเวอร์ (Maintenance Mode):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Pink400)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = Slate900, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingToggleRow(
                        title = "เปิดใช้งานโหมดปิดปรับปรุง (Maintenance)",
                        checked = draftConfig.maintenanceMode,
                        onCheckedChange = { onUpdateConfig(draftConfig.copy(maintenanceMode = it)) }
                    )
                    if (draftConfig.maintenanceMode) {
                        OutlinedTextField(
                            value = draftConfig.maintenanceMessage,
                            onValueChange = { onUpdateConfig(draftConfig.copy(maintenanceMessage = it)) },
                            label = { Text("ข้อความแจ้งเตือนผู้ใช้", color = Slate400, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = White,
                                unfocusedTextColor = White,
                                focusedBorderColor = Amber400,
                                unfocusedBorderColor = Slate700
                            )
                        )
                    }
                }
            }
        }

        item {
            Text(text = "3. แบนเนอร์ประกาศและกิจกรรม (Announcements & Banners):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Pink400)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = Slate900, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val activeBanner = draftConfig.banners.firstOrNull() ?: UiBannerItem()
                    SettingToggleRow(
                        title = "แสดงแบนเนอร์ด้านบนของ Feed",
                        checked = activeBanner.isVisible,
                        onCheckedChange = { isVis ->
                            val updatedBanners = listOf(activeBanner.copy(isVisible = isVis))
                            onUpdateConfig(draftConfig.copy(banners = updatedBanners))
                        }
                    )
                    OutlinedTextField(
                        value = activeBanner.title,
                        onValueChange = { newTitle ->
                            val updatedBanners = listOf(activeBanner.copy(title = newTitle))
                            onUpdateConfig(draftConfig.copy(banners = updatedBanners))
                        },
                        label = { Text("หัวข้อแบนเนอร์", color = Slate400, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Pink500,
                            unfocusedBorderColor = Slate700
                        )
                    )
                    OutlinedTextField(
                        value = activeBanner.subtitle,
                        onValueChange = { newSub ->
                            val updatedBanners = listOf(activeBanner.copy(subtitle = newSub))
                            onUpdateConfig(draftConfig.copy(banners = updatedBanners))
                        },
                        label = { Text("คำอธิบายรอง", color = Slate400, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Pink500,
                            unfocusedBorderColor = Slate700
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceLivePreview(
    draftConfig: AppUiConfig
) {
    val bgCol = parseHexColor(draftConfig.theme.backgroundColorHex, Slate950)
    val cardCol = parseHexColor(draftConfig.theme.cardColorHex, Slate900)
    val primaryCol = parseHexColor(draftConfig.theme.primaryColorHex, Pink500)

    MobileDevicePreviewContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgCol)
                .padding(12.dp)
        ) {
            // Simulated Mini Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(primaryCol),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("FT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = draftConfig.appName.ifBlank { "FriendTalk" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = White
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = primaryCol.copy(alpha = 0.2f),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🔴", fontSize = 8.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (draftConfig.maintenanceMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(shape = RoundedCornerShape(16.dp), color = cardCol, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🛠️", fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("ระบบปิดปรับปรุง", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = White)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = draftConfig.maintenanceMessage,
                                fontSize = 11.sp,
                                color = Slate400,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Category Tabs Mock
                if (draftConfig.feed.showCategoryTabs) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ทั้งหมด", "คนใกล้เคียง", "ออนไลน์").forEachIndexed { idx, tab ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (idx == 0) primaryCol else cardCol,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = tab,
                                    fontSize = 10.sp,
                                    color = if (idx == 0) White else Slate400,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Banner Mock
                val banner = draftConfig.banners.firstOrNull { it.isVisible }
                if (banner != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = parseHexColor(banner.backgroundColorHex, primaryCol),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(banner.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = White)
                            if (banner.subtitle.isNotBlank()) {
                                Text(banner.subtitle, fontSize = 10.sp, color = White.copy(alpha = 0.8f))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Story Row Mock
                if (draftConfig.feed.showStoryRow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(cardCol),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("👤", fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Post Card Mock
                Surface(
                    shape = RoundedCornerShape(draftConfig.feed.cardCornerRadiusDp.dp),
                    color = cardCol,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(primaryCol)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("LinLin Live 🎉", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "สวัสดีเพื่อนๆ FriendTalk ทุกคน วันนี้มีไลฟ์แจกของขวัญและ PK สดนะคะ 💖",
                            fontSize = 11.sp,
                            color = Slate300
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("❤️ 128", fontSize = 10.sp, color = Pink400)
                            Text("💬 34", fontSize = 10.sp, color = Slate400)
                            Text("🎁 12", fontSize = 10.sp, color = Amber400)
                        }
                    }
                }
            }
        }
    }
}

