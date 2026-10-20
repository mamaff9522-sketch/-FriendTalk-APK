package com.example.ui.components

import android.app.Activity
import android.content.ContextWrapper
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * On some phones (3-button navigation, Android 15+) a full-screen dialog window is drawn under the navigation bar
 * while Compose reports a 0 navigation-bar inset inside the dialog, so systemBarsPadding() does nothing and the
 * bottom row (comment box, chat input, composer buttons) ends up under the phone's buttons.
 * This measures how far the dialog window really overlaps the activity's navigation bar and pads that much
 * (minus whatever the system insets already pad, so nothing is padded twice).
 */
@Composable
fun rememberDialogNavOverlap(): Dp {
    val view = LocalView.current
    val ctx = LocalContext.current
    val density = LocalDensity.current
    var overlapPx by remember { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val act = generateSequence(ctx) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        fun measure() {
            val decor = act?.window?.decorView ?: return
            val nav = ViewCompat.getRootWindowInsets(decor)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            if (nav <= 0) { overlapPx = 0; return }
            val d = IntArray(2); decor.getLocationOnScreen(d)
            val navTop = d[1] + decor.height - nav // where the navigation bar starts on screen
            val root = view.rootView
            val r = IntArray(2); root.getLocationOnScreen(r)
            overlapPx = (r[1] + root.height - navTop).coerceIn(0, nav)
        }
        val l = ViewTreeObserver.OnGlobalLayoutListener { measure() }
        view.viewTreeObserver.addOnGlobalLayoutListener(l)
        view.post { measure() }
        onDispose { runCatching { view.viewTreeObserver.removeOnGlobalLayoutListener(l) } }
    }
    val alreadyPadded = WindowInsets.navigationBars.getBottom(density)
    return with(density) { (overlapPx - alreadyPadded).coerceAtLeast(0).toDp() }
}

/** Bottom padding that keeps a full-screen dialog's last row above the phone's navigation buttons. */
fun Modifier.dialogNavPadding(): Modifier = composed { padding(bottom = rememberDialogNavOverlap().coerceAtLeast(0.dp)) }
