package com.tbtechs.focusflow.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Dialog
import androidx.compose.material3.DialogProperties
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun DiagnosticsModal(
    visible: Boolean,
    logs: List<DiagnosticLogEntry> = emptyList(),
    loading: Boolean = false,
    onClose: () -> Unit,
    onRefresh: () -> Unit = {},
    onClearLogs: () -> Unit = {},
    onOpenReport: ((DiagnosticsReport) -> Boolean)? = null,
) {
    if (!visible) return

    val clipboard = LocalClipboardManager.current
    var displayedLogs by remember { mutableStateOf(logs) }
    var clearConfirmationVisible by remember { mutableStateOf(false) }
    var reportVisible by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(logs) {
        displayedLogs = logs
    }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1_500)
            copied = false
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(formatDiagnosticLogs(displayedLogs)))
                            copied = true
                        },
                        enabled = displayedLogs.isNotEmpty(),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy all logs")
                    }
                    IconButton(
                        onClick = { clearConfirmationVisible = true },
                        enabled = displayedLogs.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "Clear logs",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DiagnosticLegend(DiagnosticLogLevel.DEBUG, Color(0xFF0288D1))
                    DiagnosticLegend(DiagnosticLogLevel.INFO, Color(0xFF2E7D32))
                    DiagnosticLegend(DiagnosticLogLevel.WARN, Color(0xFFEF6C00))
                    DiagnosticLegend(DiagnosticLogLevel.ERROR, MaterialTheme.colorScheme.error)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${displayedLogs.size} entries",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Send, contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Help us fix this", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Send these logs only when you choose to report an issue.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(onClick = { reportVisible = true }) {
                            Text("Report")
                        }
                    }
                }

                when {
                    loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    displayedLogs.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No logs yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
                    ) {
                        itemsIndexed(displayedLogs.asReversed()) { _, entry ->
                            DiagnosticLogRow(entry)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OutlinedButton(onClick = onRefresh, enabled = !loading) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (copied) "Copied" else "Refresh")
                    }
                }
            }
        }
    }

    if (clearConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { clearConfirmationVisible = false },
            title = { Text("Clear logs?") },
            text = { Text("Delete all diagnostic logs from this device?") },
            confirmButton = {
                Button(
                    onClick = {
                        clearConfirmationVisible = false
                        displayedLogs = emptyList()
                        onClearLogs()
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirmationVisible = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    ReportIssueModal(
        visible = reportVisible,
        logs = displayedLogs,
        onClose = { reportVisible = false },
        onOpenDraft = onOpenReport,
    )
}

@Composable
private fun DiagnosticLegend(level: DiagnosticLogLevel, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(50)),
        )
        Text(level.name, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DiagnosticLogRow(entry: DiagnosticLogEntry) {
    val color = when (entry.level) {
        DiagnosticLogLevel.DEBUG -> Color(0xFF0288D1)
        DiagnosticLogLevel.INFO -> Color(0xFF2E7D32)
        DiagnosticLogLevel.WARN -> Color(0xFFEF6C00)
        DiagnosticLogLevel.ERROR -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            entry.level.name,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.tag, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                Text(
                    formatDiagnosticTime(entry.timestamp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                entry.message,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}