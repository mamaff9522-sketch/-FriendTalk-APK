package com.example.ui.admin

import com.example.ui.components.dialogNavPadding

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    var showDraftPreview by remember { mutableStateOf(false) }
    if (showDraftPreview) DraftPreviewDialog(draftConfig.theme) { showDraftPreview = false }
    var selectedBuilderTab by remember { mutableStateOf("visual_dnd") } // visual_dnd (default), feed, theme, remote, preview, history
    var releaseNotes by remember { mutableStateOf("ปรับแต่งหน้า Feed และระบบ UI") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .systemBarsPadding().dialogNavPadding()
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.95f),
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
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "visual_dnd" to "ลากวาง UI 🎨",
                        "feed" to "จัด Feed 📰",
                        "theme" to "ธีม & สี 🌈",
                        "remote" to "Config ⚙️",
                        "history" to "ประวัติ ⏳"
                    ).forEach { (key, label) ->
                        val isSel = selectedBuilderTab == key
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) Pink500 else Slate800,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedBuilderTab = key }
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) White else Slate300,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
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
                        "history" -> VersionHistoryView(
                            history = versionHistory,
                            onRollback = onRollback
                        )
                    }
                }

                var showPages by remember { mutableStateOf(false) }
                if (showPages) PageBuilderDialog { showPages = false }
                Button(onClick = { showPages = true }, modifier = Modifier.fillMaxWidth()) { Text("📄 แก้ทุกหน้า: เลือกหน้า · ส่วน · ข้อความ · เผยแพร่/ย้อนกลับ") }
                if (selectedBuilderTab != "visual_dnd") {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Slate800)
                Spacer(modifier = Modifier.height(8.dp))

                // Action Bar: Release notes + Publish Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { showDraftPreview = true }, shape = RoundedCornerShape(12.dp)) {
                        Text("👁 ดูตัวอย่าง", fontSize = 11.sp, color = White)
                    }
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
    var picking by remember { mutableStateOf<Pair<String, String>?>(null) } // field key -> label
    val fields = listOf(
        "backgroundColorHex" to "พื้นหลัง", "surfaceColorHex" to "พื้นผิว / แถบ", "cardColorHex" to "การ์ด",
        "primaryColorHex" to "สีเน้น (Accent)", "secondaryColorHex" to "สีเน้น 2 / ไล่สี", "accentColorHex" to "สีเสริม",
        "textPrimaryHex" to "ตัวอักษรหลัก", "textSecondaryHex" to "ตัวอักษรรอง")
    fun get(k: String) = when (k) { "backgroundColorHex" -> themeConfig.backgroundColorHex; "surfaceColorHex" -> themeConfig.surfaceColorHex; "cardColorHex" -> themeConfig.cardColorHex
        "primaryColorHex" -> themeConfig.primaryColorHex; "secondaryColorHex" -> themeConfig.secondaryColorHex; "accentColorHex" -> themeConfig.accentColorHex
        "textPrimaryHex" -> themeConfig.textPrimaryHex; else -> themeConfig.textSecondaryHex }
    fun set(k: String, v: String) = onUpdateThemeConfig(when (k) { "backgroundColorHex" -> themeConfig.copy(backgroundColorHex = v); "surfaceColorHex" -> themeConfig.copy(surfaceColorHex = v)
        "cardColorHex" -> themeConfig.copy(cardColorHex = v); "primaryColorHex" -> themeConfig.copy(primaryColorHex = v); "secondaryColorHex" -> themeConfig.copy(secondaryColorHex = v)
        "accentColorHex" -> themeConfig.copy(accentColorHex = v); "textPrimaryHex" -> themeConfig.copy(textPrimaryHex = v); else -> themeConfig.copy(textSecondaryHex = v) })
    picking?.let { (k, label) ->
        ColorPickerDialog(label, get(k), previewContent = { DraftHomePreview(themeConfig, compact = true) }, onPick = { set(k, it); picking = null }, onDismiss = { picking = null })
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("ชุดสีสำเร็จรูป (แตะเพื่อเลือก):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Pink400)
            Spacer(Modifier.height(8.dp))
            UI_PALETTES.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { pal ->
                        val sel = themeConfig.primaryColorHex.equals(pal.primary, true) && themeConfig.backgroundColorHex.equals(pal.background, true)
                        Surface(
                            onClick = { onUpdateThemeConfig(themeConfig.copy(primaryColorHex = pal.primary, secondaryColorHex = pal.secondary, accentColorHex = pal.accent,
                                backgroundColorHex = pal.background, surfaceColorHex = pal.surface, cardColorHex = pal.card, textPrimaryHex = pal.text, textSecondaryHex = pal.text2)) },
                            shape = RoundedCornerShape(10.dp), color = parseHexColor(pal.background, Slate950),
                            border = androidx.compose.foundation.BorderStroke(if (sel) 3.dp else 1.dp, if (sel) Pink500 else Slate700),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(pal.primary, pal.secondary, pal.accent, pal.card).forEach { Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(parseHexColor(it, Pink500))) }
                                }
                                Text((if (sel) "✓ " else "") + pal.name, fontSize = 12.sp, color = parseHexColor(pal.text, White), fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item {
            Text("ปรับสีเอง (แตะแถวเพื่อเปิดตัวเลือกสี):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Pink400)
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = Slate900, modifier = Modifier.fillMaxWidth()) {
                Column {
                    fields.forEach { (k, label) ->
                        Row(Modifier.fillMaxWidth().clickable { picking = k to label }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(parseHexColor(get(k), Slate500)).border(1.dp, Slate700, RoundedCornerShape(8.dp)))
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(get(k).uppercase(), color = Slate400, fontSize = 12.sp)
                            Text("  ›", color = Slate400)
                        }
                    }
                }
            }
        }
        item { BackgroundEditor(themeConfig, onUpdateThemeConfig) }
        item {
            Text("ตัวอย่างสด (ยังไม่เผยแพร่):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Pink400)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Slate700, RoundedCornerShape(14.dp))) { DraftHomePreview(themeConfig, compact = true) }
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


private data class UiPalette(val name: String, val primary: String, val secondary: String, val accent: String, val background: String, val surface: String, val card: String, val text: String, val text2: String)
private val UI_PALETTES = listOf(
    UiPalette("Pearl White (ค่าเริ่มต้น)", "#EC4899", "#A855F7", "#8B5CF6", "#F7F4FA", "#FFFFFF", "#F1ECF7", "#1F1B2E", "#5B5670"),
    UiPalette("Pink & Purple (มืด)", "#EC4899", "#A855F7", "#22D3EE", "#090D16", "#0F172A", "#1E293B", "#FFFFFF", "#CBD5E1"),
    UiPalette("Indigo Neon (มืด)", "#6366F1", "#EC4899", "#FBBF24", "#0B0B1E", "#14142E", "#1F1F45", "#FFFFFF", "#C7D2FE"),
    UiPalette("Sakura", "#F472B6", "#FB7185", "#C084FC", "#FFF5F8", "#FFFFFF", "#FCE7F3", "#3B1D2E", "#7A5468"),
    UiPalette("Mint Fresh", "#10B981", "#06B6D4", "#84CC16", "#F2FBF7", "#FFFFFF", "#DCF5EA", "#12322A", "#4E6B62"),
    UiPalette("Ocean Sky", "#3B82F6", "#06B6D4", "#6366F1", "#F2F7FE", "#FFFFFF", "#E0ECFD", "#122139", "#4D6283"),
    UiPalette("Sunset", "#F97316", "#EC4899", "#F59E0B", "#FFF8F1", "#FFFFFF", "#FFEBD9", "#3A2213", "#7A5A45"),
    UiPalette("Midnight Gold (มืด)", "#F59E0B", "#D97706", "#FCD34D", "#0C0A09", "#1C1917", "#292524", "#FAFAF9", "#D6D3D1")
)
