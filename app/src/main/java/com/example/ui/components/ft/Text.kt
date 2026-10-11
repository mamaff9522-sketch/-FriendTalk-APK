package com.example.ui.components.ft

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.ui.components.LocalUiPage
import com.example.ui.components.UiPages

/** Drop-in for material3 Text: applies admin overrides (content, size, colour, weight) from the UI Builder. */
@Composable
fun Text(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null, fontWeight: FontWeight? = null, fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null, textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE, minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null, style: TextStyle = androidx.compose.material3.LocalTextStyle.current
) {
    val o = UiPages.textFor(LocalUiPage.current, text)
    val c = o?.c?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: color
    androidx.compose.material3.Text(text = o?.t ?: text, modifier = modifier, color = c, fontSize = o?.s?.sp ?: fontSize, fontStyle = fontStyle,
        fontWeight = o?.w?.let { FontWeight(it) } ?: fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = if (o?.s != null) TextUnit.Unspecified else lineHeight, overflow = overflow, softWrap = softWrap,
        maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout ?: {}, style = style)
}

@Composable
fun Text(
    text: AnnotatedString, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null, fontWeight: FontWeight? = null, fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null, textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE, minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {}, style: TextStyle = androidx.compose.material3.LocalTextStyle.current
) = androidx.compose.material3.Text(text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
    fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight,
    overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout ?: {}, style = style)
