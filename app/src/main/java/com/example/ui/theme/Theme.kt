package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.example.model.AppUiThemeConfig
import com.example.model.parseHexColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp

private val DarkColorScheme = darkColorScheme(
    primary = Pink500,
    onPrimary = PureWhite,
    primaryContainer = Pink600,
    onPrimaryContainer = PureWhite,
    secondary = Purple500,
    onSecondary = PureWhite,
    secondaryContainer = Slate800,
    onSecondaryContainer = Slate100,
    tertiary = Cyan400,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300,
    outline = Slate700
)

@Composable
fun FriendTalkTheme(
    themeConfig: AppUiThemeConfig = AppUiThemeConfig(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val primaryColor = parseHexColor(themeConfig.primaryColorHex, Pink500)
    val secondaryColor = parseHexColor(themeConfig.secondaryColorHex, Purple500)
    val accentColor = parseHexColor(themeConfig.accentColorHex, Cyan400)
    val bgColor = parseHexColor(themeConfig.backgroundColorHex, Slate950)
    val surfaceColor = parseHexColor(themeConfig.surfaceColorHex, Slate900)
    val cardColor = parseHexColor(themeConfig.cardColorHex, Slate800)
    val light = bgColor.luminance() > 0.5f
    val textColor = parseHexColor(themeConfig.textPrimaryHex, if (light) androidx.compose.ui.graphics.Color(0xFF1F1B2E) else PureWhite)
    val text2Color = parseHexColor(themeConfig.textSecondaryHex, if (light) androidx.compose.ui.graphics.Color(0xFF5B5670) else androidx.compose.ui.graphics.Color(0xFFCBD5E1))

    androidx.compose.runtime.SideEffect {
        AppPalette.primary = primaryColor; AppPalette.background = bgColor; AppPalette.surface = surfaceColor; AppPalette.card = cardColor; AppPalette.text = textColor; AppPalette.text2 = text2Color
    }
    val base = if (light) androidx.compose.material3.lightColorScheme() else DarkColorScheme
    val colorScheme = base.copy(
        primary = primaryColor, onPrimary = PureWhite, onBackground = textColor, onSurface = textColor, onSurfaceVariant = text2Color,
        outline = lerp(text2Color, bgColor, 0.6f), surfaceContainerHigh = surfaceColor, surfaceContainer = surfaceColor,
        secondary = secondaryColor,
        tertiary = accentColor,
        background = bgColor,
        surface = surfaceColor,
        surfaceVariant = cardColor
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
