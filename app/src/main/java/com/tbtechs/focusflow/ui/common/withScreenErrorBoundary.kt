package com.tbtechs.focusflow.ui.common

import androidx.compose.runtime.Composable

/**
 * Kotlin equivalent of the React HOC. Callers can wrap any destination body
 * without changing the destination's own state or navigation callbacks.
 */
@Composable
fun WithScreenErrorBoundary(
    screenName: String,
    content: @Composable () -> Unit,
) {
    ErrorBoundary(screenName = screenName, content = content)
}

@Composable
fun withScreenErrorBoundary(
    screenName: String,
    content: @Composable () -> Unit,
) {
    WithScreenErrorBoundary(screenName, content)
}