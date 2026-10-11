package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.BgSpec
import com.example.model.parseHexColor

/** Page background: admin image (cover/contain/tile, blur, dim, colour overlay) or the theme's soft default gradient (never flat black). */
@Composable
fun AppBackground(spec: BgSpec?, modifier: Modifier = Modifier) {
    val bg = AppPalette.background
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(lerp(bg, AppPalette.primary, 0.06f), bg, lerp(bg, Purple500, 0.05f))))) {
        if (spec != null && spec.imageUrl.isNotBlank()) {
            val blur = spec.blur.coerceIn(0, 25).dp
            when (spec.fit) {
                "tile" -> TiledImage(spec.imageUrl, Modifier.fillMaxSize().blur(blur))
                else -> AsyncImage(model = spec.imageUrl, contentDescription = null,
                    contentScale = if (spec.fit == "contain") ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(blur))
            }
            if (spec.dim > 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = spec.dim.coerceIn(0, 90) / 100f)))
            if (spec.overlayHex.isNotBlank() && spec.overlayAlpha > 0)
                Box(Modifier.fillMaxSize().background(parseHexColor(spec.overlayHex, Color.Transparent).copy(alpha = spec.overlayAlpha.coerceIn(0, 100) / 100f)))
        }
    }
}

@Composable
private fun TiledImage(url: String, modifier: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val img = androidx.compose.runtime.produceState<ImageBitmap?>(null, url) {
        val req = coil.request.ImageRequest.Builder(ctx).data(url).allowHardware(false).size(512).build()
        value = (coil.Coil.imageLoader(ctx).execute(req).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.asImageBitmap()
    }.value
    if (img != null) Box(modifier.background(ShaderBrush(ImageShader(img, TileMode.Repeated, TileMode.Repeated))))
}
