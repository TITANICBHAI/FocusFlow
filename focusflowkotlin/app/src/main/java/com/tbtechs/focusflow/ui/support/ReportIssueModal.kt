package com.tbtechs.focusflow.ui.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Dialog
import androidx.compose.material3.DialogProperties
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun ReportIssueModal(
    visible: Boolean,
    logs: List<DiagnosticLogEntry> = emptyList(),
    error: Throwable? = null,
    onClose: () -> Unit,
    onOpenDraft: ((DiagnosticsReport) -> Boolean)? = null,
) {
    if (!visible) return

    val context = LocalContext.current
    var description by remember { mutableStateOf("") }
    var reportType by remember { mutableStateOf(DiagnosticsReportType.BUG) }
    var includeLogs by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(visible) {
        description = ""
        reportType = DiagnosticsReportType.BUG
        includeLogs = true
        busy = false
        status = null
    }

    Dialog(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Report this issue",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )
                    IconButton(onClick = onClose, enabled = !busy) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close report form")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "This opens your email app with a draft addressed to tbtechsdev@gmail.com. Review it and tap Send yourself — nothing is sent automatically.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text("What would you like to share?", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DiagnosticsReportType.entries.forEach { type ->
                        FilterChip(
                            selected = reportType == type,
                            onClick = { reportType = type },
                            label = { Text(type.label) },
                        )
                    }
                }

                Text(
                    if (reportType == DiagnosticsReportType.BUG) "What happened?" else "What would you like to share?",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (reportType == DiagnosticsReportType.BUG) {
                        "Tell us what you were doing and how the error appeared. This is optional, but it helps reproduce the problem."
                    } else {
                        "Your message is optional, but it helps us understand your experience and improve FocusFlow."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 2_000) description = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 8,
                    placeholder = { Text("For example: I opened Settings and the app showed an error.") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includeLogs, onCheckedChange = { includeLogs = it })
                    Column {
                        Text("Include diagnostic logs", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Add recent sanitized logs when available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "Personal files, contacts, installed-app lists, and location are not included.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                status?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Button(
                    onClick = {
                        busy = true
                        val errorDetails = error?.let {
                            "Error screen message: ${it.message}\nStack trace:\n${it.stackTraceToString()}\n\n"
                        }.orEmpty()
                        val report = DiagnosticsReport(
                            description = description,
                            logs = if (includeLogs) errorDetails + formatDiagnosticLogs(logs) else "",
                            type = reportType,
                        )
                        val opened = onOpenDraft?.invoke(report)
                            ?: openDiagnosticsEmailDraft(context, report)
                        busy = false
                        status = if (opened) {
                            "Email draft opened. Nothing was sent automatically."
                        } else {
                            "No email app is available. Please email tbtechsdev@gmail.com manually."
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        CircularProgressIndicator()
                    } else {
                        Icon(Icons.Outlined.Send, contentDescription = null)
                        Text("Open email draft", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedButton(
                    onClick = onClose,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}