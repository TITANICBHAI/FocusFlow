package com.tbtechs.focusflow.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
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
    var copied by remember { mutableStateOf(false) }
    var detailsVisible by remember { mutableStateOf(false) }
    val details = buildString {
        appendLine("Screen: $screenName")
        appendLine("Message: ${error?.message ?: "Unknown error"}")
        appendLine("Stack trace:")
        appendLine(error?.stackTraceToString() ?: "(unavailable)")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "Something went wrong",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                Text(
                    "FocusFlow could not display this screen. Your local data was not intentionally deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Try again")
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(details))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Text(if (copied) "Copied logs" else "Copy logs", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = { detailsVisible = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Send, contentDescription = null)
                    Text("View error details", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = onReportIssue,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Report this issue")
                }
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
    if (detailsVisible) {
        AlertDialog(
            onDismissRequest = { detailsVisible = false },
            title = { Text("Error details") },
            text = { Text(details, modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(details))
                    copied = true
                    detailsVisible = false
                }) { Text("Copy details") }
            },
            dismissButton = { TextButton(onClick = { detailsVisible = false }) { Text("Close") } },
        )
    }
}