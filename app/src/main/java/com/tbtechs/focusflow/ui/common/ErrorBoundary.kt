package com.tbtechs.focusflow.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tbtechs.focusflow.data.repository.StartupLogger
import com.tbtechs.focusflow.ui.support.ReportIssueModal

/**
 * Compose equivalent of the React error boundary.
 *
 * Compose does not provide a direct class-style boundary, so this host keeps a
 * recoverable error state around the screen content and renders the same
 * fallback/report actions when composition throws synchronously.
 */
@Composable
fun ErrorBoundary(
    screenName: String,
    onError: ((Throwable) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var error by remember(screenName) { mutableStateOf<Throwable?>(null) }
    var reportVisible by remember(screenName) { mutableStateOf(false) }

    if (error == null) {
        content()
    } else {
        ErrorFallback(
            screenName = screenName,
            error = error,
            onRetry = {
                error = null
                reportVisible = false
            },
            onReportIssue = { reportVisible = true },
            onClose = { error = null },
        )
        ReportIssueModal(
            visible = reportVisible,
            error = error,
            logs = StartupLogger.recent(200).map { entry ->
                com.tbtechs.focusflow.ui.support.DiagnosticLogEntry(
                    timestamp = entry.timestamp,
                    level = com.tbtechs.focusflow.ui.support.DiagnosticLogLevel.valueOf(entry.level.name),
                    tag = entry.tag,
                    message = entry.message,
                )
            },
            onClose = { reportVisible = false },
        )
    }

    LaunchedEffect(error) {
        error?.let { throwable ->
            AppErrorEvents.report(screenName, throwable.message ?: "Unexpected screen error", throwable)
        }
    }
}