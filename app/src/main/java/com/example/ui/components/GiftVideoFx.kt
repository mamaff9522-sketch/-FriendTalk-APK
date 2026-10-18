package com.example.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/**
 * Admin-uploaded gift video effect (MP4 / WebM): plays once, full width in the middle of a dimmed screen, with sound,
 * then calls [onDone]. Tap anywhere to skip. If the video can't load it ends quietly (the normal gift effect is not lost
 * because callers only use this when a video exists). Hard stop after [maxMs].
 */
@Composable
fun GiftVideoFx(url: String, onDone: () -> Unit, dim: Boolean = true, maxMs: Long = (Tweaks.num("gift.videoMaxSec") * 1000).toLong()) {
    var aspect by remember(url) { mutableFloatStateOf(0f) }
    var done by remember(url) { mutableStateOf(false) }
    val finish = rememberUpdatedState { if (!done) { done = true; onDone() } }
    LaunchedEffect(url) { delay(maxMs); finish.value() }
    val player = remember(url) { MediaPlayer() }
    DisposableEffect(url) { onDispose { runCatching { player.stop() }; runCatching { player.release() } } }
    Box(Modifier.fillMaxSize().then(if (dim) Modifier.background(Color(0x99000000)) else Modifier)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish.value() },
        contentAlignment = Alignment.Center) {
        AndroidView(factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        runCatching {
                            player.setSurface(Surface(st))
                            player.setDataSource(ctx, android.net.Uri.parse(url))
                            player.setOnVideoSizeChangedListener { _, vw, vh -> if (vw > 0 && vh > 0) aspect = vw.toFloat() / vh }
                            player.setOnPreparedListener { it.start() }
                            player.setOnCompletionListener { finish.value() }
                            player.setOnErrorListener { _, _, _ -> finish.value(); true }
                            player.prepareAsync()
                        }.onFailure { finish.value() }
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        }, modifier = Modifier.fillMaxWidth().then(if (aspect > 0f) Modifier.aspectRatio(aspect) else Modifier.height(1.dp)))
    }
}
