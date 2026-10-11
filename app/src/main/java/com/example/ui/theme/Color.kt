package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Live app palette from the published Remote UI theme (admin 'ธีม & สี'); the named colours below read from it. */
object AppPalette {
    var primary by mutableStateOf(Color(0xFFEC4899))
    var background by mutableStateOf(Color(0xFFF7F4FA))
    var surface by mutableStateOf(Color(0xFFFFFFFF))
    var card by mutableStateOf(Color(0xFFF1ECF7))
    var text by mutableStateOf(Color(0xFF1F1B2E))
    var text2 by mutableStateOf(Color(0xFF5B5670))
    val muted: Color get() = lerp(text2, background, 0.30f)
    val muted2: Color get() = lerp(text2, background, 0.45f)
    val line: Color get() = lerp(text2, background, 0.72f)
    val primaryDark: Color get() = lerp(primary, Color.Black, 0.12f)
    val primaryLight: Color get() = lerp(primary, Color.White, 0.25f)
}

val Pink500: Color get() = AppPalette.primary
val Pink600: Color get() = AppPalette.primaryDark
val Pink400: Color get() = AppPalette.primaryLight
val Purple500 = Color(0xFFA855F7)
val Purple600 = Color(0xFF9333EA)
val Purple400 = Color(0xFFC084FC)
val Indigo500 = Color(0xFF6366F1)
val Cyan400 = Color(0xFF22D3EE)
val Amber400 = Color(0xFFFBBF24)
val Amber500 = Color(0xFFF59E0B)
val Amber300 = Color(0xFFFCD34D)
val Emerald400 = Color(0xFF34D399)
val Emerald500 = Color(0xFF10B981)
val Blue500 = Color(0xFF3B82F6)
val Blue400 = Color(0xFF60A5FA)
val Rose500 = Color(0xFFF43F5E)

// Dark Palette
val Slate950: Color get() = AppPalette.background
val Slate900: Color get() = AppPalette.surface
val Slate850: Color get() = AppPalette.card
val Slate800: Color get() = AppPalette.card
val Slate700: Color get() = AppPalette.line
val Slate600: Color get() = AppPalette.line
val Slate500: Color get() = AppPalette.muted2
val Slate400: Color get() = AppPalette.muted
val Slate300: Color get() = AppPalette.text2
val Slate200: Color get() = AppPalette.text
val Slate100: Color get() = AppPalette.text
/** Primary text colour of the active theme (dark on Pearl White, white on dark presets). Use PureWhite for text on accent/photos. */
val White: Color get() = AppPalette.text
val PureWhite = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)
