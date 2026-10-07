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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "feed" to "จัดหน้า Feed 📰",
                        "theme" to "ธีม & สี 🎨",
                        "history" to "ประวัติเวอร์ชัน ⏳"
                    ).forEach { (key, label) ->
                        val isSel = selectedBuilderTab == key
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) Pink500 else Slate800,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedBuilderTab = key }
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) White else Slate300,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Content area
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedBuilderTab) {
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
