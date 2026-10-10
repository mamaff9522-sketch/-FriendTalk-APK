package com.example.ui.layout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.UiBlock
import com.example.model.parseHexColor

/** True when the admin changed size/padding/radius/background of a built-in section. */
fun UiBlock.hasSectionStyle() = widthFraction < 0.999f || heightDp > 0 || paddingDp > 0 || cornerRadiusDp > 0 || bgColorHex.isNotBlank()

/** Wraps a built-in section's content with the block's size/padding/radius/background. */
@Composable
fun SectionStyleBox(style: UiBlock, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = (style.paddingDp / 2).dp), contentAlignment = Alignment.Center) {
        var m = Modifier.fillMaxWidth(style.widthFraction)
        if (style.heightDp > 0) m = m.heightIn(max = style.heightDp.dp)
        m = m.clip(RoundedCornerShape(style.cornerRadiusDp.dp))
        if (style.bgColorHex.isNotBlank()) m = m.background(parseHexColor(style.bgColorHex, Color.Transparent))
        Box(m.padding(horizontal = (style.paddingDp / 2).dp)) { content() }
    }
}

/** LazyListScope that applies a section style to every item emitted by an existing section. */
@OptIn(ExperimentalFoundationApi::class)
class StyledLazyScope(private val base: LazyListScope, private val style: UiBlock) : LazyListScope {
    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        base.item(key, contentType) { SectionStyleBox(style) { content() } }
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit
    ) {
        base.items(count, key, contentType) { i -> SectionStyleBox(style) { itemContent(i) } }
    }

    @ExperimentalFoundationApi
    override fun stickyHeader(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        base.stickyHeader(key, contentType) { SectionStyleBox(style) { content() } }
    }
}
