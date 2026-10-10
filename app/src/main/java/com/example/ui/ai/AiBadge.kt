package com.example.ui.ai

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small visible label so users always know they are talking to an AI character. */
@Composable
fun AiBadge(modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF0EA5E9), modifier = modifier) {
        Text("AI", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
