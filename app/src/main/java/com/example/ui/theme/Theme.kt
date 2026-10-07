package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.example.model.AppUiThemeConfig
import com.example.model.parseHexColor

private val DarkColorScheme = darkColorScheme(
    primary = Pink500,
    onPrimary = White,
    primaryContainer = Pink600,
    onPrimaryContainer = White,
    secondary = Purple500,
    onSecondary = White,
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

    val colorScheme = DarkColorScheme.copy(
        primary = primaryColor,
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
