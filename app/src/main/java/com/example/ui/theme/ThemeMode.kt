package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * User's display mode (หน้า "ฉัน"):
 *  - dark  = โหมดมืด: every page uses the Home background (navy / violet neon)
 *  - light = โหมดปกติ: every page uses the 'หาเพื่อน' background (lavender, glassy white cards)
 * Saved on the device; default is dark (the look the app had before).
 */
object AppThemeMode {
    private const val PREFS = "friendtalk"
    private const val KEY = "darkMode"
    var dark by mutableStateOf(true)
        private set

    fun load(ctx: android.content.Context) { dark = ctx.getSharedPreferences(PREFS, 0).getBoolean(KEY, true) }
    fun set(ctx: android.content.Context, isDark: Boolean) {
        dark = isDark
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(KEY, isDark).apply()
    }

    /** App-wide colours (drive White / Slate* through AppPalette in FriendTalkTheme). */
    val DARK_THEME = com.example.model.AppUiThemeConfig(primaryColorHex = "#FF2FA8", secondaryColorHex = "#B04DFF", accentColorHex = "#8B5CF6",
        backgroundColorHex = "#0A0820", surfaceColorHex = "#120F2C", cardColorHex = "#120E2E", textPrimaryHex = "#FFFFFF", textSecondaryHex = "#A49CC8")
    val LIGHT_THEME = com.example.model.AppUiThemeConfig(primaryColorHex = "#E6399B", secondaryColorHex = "#8B3DFF", accentColorHex = "#8B5CF6",
        backgroundColorHex = "#F1E2FA", surfaceColorHex = "#FBF5FF", cardColorHex = "#FFFFFF", textPrimaryHex = "#3B1C5A", textSecondaryHex = "#7A5B98")
    fun themeConfig() = if (dark) DARK_THEME else LIGHT_THEME

    private val lightPage = Brush.verticalGradient(listOf(Color(0xFFE9D9FF), Color(0xFFF3DDF5), Color(0xFFE6CFF4), Color(0xFFD9B8EE)))
    private val darkPage = Brush.verticalGradient(listOf(Color(0xFF0A0820), Color(0xFF0A0820)))
    val pageBrush: Brush get() = if (dark) darkPage else lightPage

    // Shared colours for screens that draw their own cards (Home, หาเพื่อน, composer, post view)
    val bg: Color get() = if (dark) Color(0xFF0A0820) else Color(0xFFF1E2FA)
    val card: Color get() = if (dark) Color(0xFF120E2E) else Color(0x8CFFFFFF)
    val cardSolid: Color get() = if (dark) Color(0xFF16123A) else Color(0xFFFFFFFF)
    val dialog: Color get() = if (dark) Color(0xFF120E2E) else Color(0xFFFBF5FF)
    val line: Color get() = if (dark) Color(0xFF2A2357) else Color(0xCCFFFFFF)
    val divider: Color get() = if (dark) Color(0xFF2A2357) else Color(0xFFE2CFF5)
    val text: Color get() = if (dark) Color.White else Color(0xFF3B1C5A)
    val textSoft: Color get() = if (dark) Color(0xFFD9D3F7) else Color(0xFF5B3F7A)
    val muted: Color get() = if (dark) Color(0xFFA49CC8) else Color(0xFF7A5B98)
    val field: Color get() = if (dark) Color(0xFF130F30) else Color(0xB3FFFFFF)
    val fieldHint: Color get() = if (dark) Color(0xFF8E86B8) else Color(0xFF9B83B5)
    val skeleton: Color get() = if (dark) Color(0xFF1B1640) else Color(0x66FFFFFF)
    val pill: Color get() = if (dark) Color(0xFF151033) else Color(0xE6FFFFFF)
    val headerBrush: Brush get() = if (dark) Brush.verticalGradient(listOf(Color(0xFF0A0820), Color(0xFF0A0820)))
        else Brush.verticalGradient(listOf(Color(0xFF6B2FB0), Color(0xFF8E45C8)))
}

/** Page background for the current mode (drawn behind every tab). */
@Composable
fun ModeBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(AppThemeMode.pageBrush))
}
