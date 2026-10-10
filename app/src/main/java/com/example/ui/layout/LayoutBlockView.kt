package com.example.ui.layout

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.UiBlock
import com.example.model.parseHexColor

/** Renders one custom (non-section) block of a server-driven layout. */
@Composable
fun LayoutBlockView(b: UiBlock, modifier: Modifier = Modifier, interactive: Boolean = true) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(b.cornerRadiusDp.dp)
    val defaultBg = when (b.type) { "button" -> Color(0xFFEC4899); "banner" -> Color(0xFFDB2777); "frame" -> Color(0xFF1E293B); else -> Color.Transparent }
    val bg = if (b.bgColorHex.isBlank()) defaultBg else parseHexColor(b.bgColorHex, defaultBg)
    val fg = parseHexColor(b.textColorHex, Color.White)
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        var m = Modifier.fillMaxWidth(b.widthFraction)
        if (b.heightDp > 0) m = m.height(b.heightDp.dp)
        m = m.clip(shape).background(bg)
        if (interactive && b.linkUrl.isNotBlank()) m = m.clickable {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(b.linkUrl))) }
        }
        when (b.type) {
            "spacer" -> Spacer(Modifier.fillMaxWidth().height((if (b.heightDp > 0) b.heightDp else 16).dp))
            "image" -> Box(m.then(if (b.heightDp == 0) Modifier.height(160.dp) else Modifier)) {
                if (b.imageUrl.isNotBlank()) AsyncImage(model = b.imageUrl, contentDescription = b.text, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text("ไม่มีรูป", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            }
            else -> Box(m.padding(b.paddingDp.dp), contentAlignment = if (b.type == "button") Alignment.Center else Alignment.CenterStart) {
                if (b.type == "banner" && b.imageUrl.isNotBlank()) AsyncImage(model = b.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                if (b.text.isNotBlank()) Text(
                    b.text, color = fg, fontSize = b.fontSizeSp.sp,
                    fontWeight = if (b.type == "text") FontWeight.Normal else FontWeight.Bold,
                    textAlign = if (b.type == "button") TextAlign.Center else TextAlign.Start
                )
            }
        }
    }
}
