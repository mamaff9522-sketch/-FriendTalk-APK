package com.example.model

import androidx.compose.ui.graphics.Color

data class AppUiThemeConfig(
    val primaryColorHex: String = "#EC4899",
    val secondaryColorHex: String = "#A855F7",
    val accentColorHex: String = "#22D3EE",
    val backgroundColorHex: String = "#090D16",
    val surfaceColorHex: String = "#0F172A",
    val cardColorHex: String = "#1E293B",
    val cornerRadiusDp: Int = 16,
    val cardElevationDp: Int = 2
)

enum class PostCardStyle {
    MODERN_CARD,
    COMPACT,
    BORDERLESS
}

data class FeedSectionConfig(
    val showHeader: Boolean = true,
    val showCategoryTabs: Boolean = true,
    val showStoryRow: Boolean = true,
    val showQuickActions: Boolean = true,
    val showClubsSnippet: Boolean = true,
    val availableTabs: List<String> = listOf("ทั้งหมด", "คนใกล้เคียง", "ออนไลน์", "หาเพื่อน"),
    val postCardStyle: PostCardStyle = PostCardStyle.MODERN_CARD,
    val cardCornerRadiusDp: Int = 18,
    val postSpacingDp: Int = 12,
    val textSizeScale: Float = 1.0f,
    val showVerifiedBadges: Boolean = true,
    val showLiveIndicatorOnCard: Boolean = true,
    val sectionOrder: List<String> = listOf("HEADER", "TABS", "STORY", "FEED")
)

data class BottomNavConfig(
    val showHomeTab: Boolean = true,
    val showDiscoverTab: Boolean = true,
    val showLiveTab: Boolean = true,
    val showChatTab: Boolean = true,
    val showMovieTab: Boolean = true,
    val showProfileTab: Boolean = true,
    val showCompanionTab: Boolean = true
)

data class AppUiConfig(
    val configVersion: String = "v1.2.0",
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val updatedBy: String = "Admin",
    val versionNotes: String = "Enhanced Feed Layout & Customization",
    val theme: AppUiThemeConfig = AppUiThemeConfig(),
    val feed: FeedSectionConfig = FeedSectionConfig(),
    val bottomNav: BottomNavConfig = BottomNavConfig(),
    val isCompanionFeatureEnabled: Boolean = true,
    val isMovieFeatureEnabled: Boolean = true,
    val isLiveStreamingEnabled: Boolean = true,
    val appName: String = "FriendTalk",
    val apiEndpoint: String = "",
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "ระบบกำลังปิดปรับปรุงชั่วคราว กรุณากลับมาใหม่ภายหลัง",
    val banners: List<UiBannerItem> = emptyList(),
    val serverDrivenComponents: List<ServerDrivenComponent> = emptyList()
)

data class UiBannerItem(
    val id: String = "banner_main",
    val title: String = "",
    val subtitle: String = "",
    val backgroundColorHex: String = "#DB2777",
    val isVisible: Boolean = false
)

data class ServerDrivenComponent(
    val id: String = "",
    val title: String = "",
    val text: String = "",
    val backgroundColorHex: String = "#1E293B",
    val textColorHex: String = "#FFFFFF",
    val cornerRadiusDp: Int = 16,
    val marginDp: Int = 16,
    val paddingDp: Int = 14,
    val order: Int = 0,
    val isVisible: Boolean = true
)

data class ConfigVersionRecord(
    val version: String,
    val publishedAt: String,
    val publishedBy: String,
    val notes: String,
    val config: AppUiConfig,
    val isActive: Boolean = false
)

data class ApkBuildRecord(
    val buildId: String,
    val versionName: String,
    val versionCode: Int,
    val buildDate: String,
    val apkSizeMb: String,
    val status: String,
    val apkFileName: String,
    val downloadPath: String,
    val notes: String
)

fun parseHexColor(hexString: String, defaultColor: Color): Color {
    return try {
        val clean = hexString.removePrefix("#").trim()
        val colorInt = when (clean.length) {
            6 -> (0xFF000000 or clean.toLong(16)).toInt()
            8 -> clean.toLong(16).toInt()
            else -> return defaultColor
        }
        Color(colorInt)
    } catch (e: Exception) {
        defaultColor
    }
}
