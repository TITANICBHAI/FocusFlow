package com.tbtechs.focusflow.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Call inside a full-screen Dialog. Makes that dialog's status and navigation bars see-through so
 * its own background runs to the screen edges, with no light strip behind three-button
 * navigation (Android adds one on its own unless contrast enforcement is switched off).
 */
@Composable
fun TransparentDialogSystemBars() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        // Let the dialog's own content run under the bars; its insets then reach Compose, which
        // is why the screens below pad themselves with WindowInsets.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
    }
}
