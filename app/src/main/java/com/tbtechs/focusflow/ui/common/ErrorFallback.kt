package com.tbtechs.focusflow.ui.common

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tbtechs.focusflow.ui.home.FocusFlowModalCard
import com.tbtechs.focusflow.ui.home.FocusFlowPrimaryButton
import com.tbtechs.focusflow.ui.home.FocusFlowSecondaryButton
import com.tbtechs.focusflow.ui.home.RefRed
import com.tbtechs.focusflow.ui.home.RefSecondary
import com.tbtechs.focusflow.ui.home.RefText
import com.tbtechs.focusflow.data.repository.StartupLogger
import com.tbtechs.focusflow.ui.support.DiagnosticLogEntry
import com.tbtechs.focusflow.ui.support.DiagnosticLogLevel
import com.tbtechs.focusflow.ui.support.DiagnosticsReport
import com.tbtechs.focusflow.ui.support.DiagnosticsReportType
import com.tbtechs.focusflow.ui.support.ReportIssueModal

@Composable
fun ErrorFallback(
    screenName: String,
    error: Throwable?,
    onRetry: () -> Unit,
    onReportIssue: () -> Unit,
    onClose: () -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var detailsVisible by remember { mutableStateOf(false) }
    val details = buildString {
        appendLine("Screen: $screenName")
        appendLine("Message: ${error?.message ?: "Unknown error"}")
        appendLine("Stack trace:")
        appendLine(error?.stackTraceToString() ?: "(unavailable)")
        val diagnostics = StartupLogger.formatForShare()
        if (diagnostics.isNotBlank()) {
            appendLine()
            appendLine("Persistent diagnostic logs:")
            appendLine(diagnostics)
        }
    }
    fun shareDetails() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "FocusFlow crash report")
            putExtra(Intent.EXTRA_TEXT, details)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Share crash report")) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = RefRed,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Something went wrong",
                    color = RefText,
                    fontSize = 28.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                "FocusFlow could not display this screen. Your local data was not intentionally deleted.",
                color = RefSecondary,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            )
            FocusFlowPrimaryButton(text = "Try again", onClick = onRetry)
            FocusFlowSecondaryButton(
                text = if (copied) "Copied logs" else "Copy logs",
                icon = Icons.Outlined.ContentCopy,
                onClick = {
                    clipboard.setText(AnnotatedString(details))
                    copied = true
                },
            )
            FocusFlowSecondaryButton(
                text = "View error details",
                icon = Icons.Outlined.Send,
                onClick = { detailsVisible = true },
            )
            FocusFlowSecondaryButton(
                text = "Share logs",
                icon = Icons.Outlined.Send,
                onClick = ::shareDetails,
            )
            FocusFlowSecondaryButton(text = "Report this issue", onClick = onReportIssue)
            FocusFlowSecondaryButton(text = "Close", onClick = onClose)
        }
    }
    if (detailsVisible) {
        Dialog(
            onDismissRequest = { detailsVisible = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            FocusFlowModalCard(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                radius = 16.dp,
                contentPadding = 16.dp,
            ) {
                Text("Error details", color = RefText, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(
                    details,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp),
                    color = RefSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FocusFlowSecondaryButton(
                        text = "Close",
                        onClick = { detailsVisible = false },
                        modifier = Modifier.weight(1f),
                    )
                    FocusFlowPrimaryButton(
                        text = "Copy details",
                        onClick = {
                            clipboard.setText(AnnotatedString(details))
                            copied = true
                            detailsVisible = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}