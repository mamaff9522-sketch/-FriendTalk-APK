package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate950

@Composable
fun MobileDevicePreviewContainer(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
            .padding(8.dp)
            .clip(RoundedCornerShape(32.dp))
            .border(4.dp, Slate800, RoundedCornerShape(32.dp))
    ) {
        content()
    }
}
