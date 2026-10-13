package com.tbtechs.focusflow.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions

/**
 * Redesigned Report Issue modal sheet matching screenshots 12a and 12b.
 * Includes report type selection, message field, sanitized diagnostics checkbox,
 * and email draft launcher.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportIssueModal(
    visible: Boolean,
    logs: List<DiagnosticLogEntry> = emptyList(),
    error: Throwable? = null,
    onClose: () -> Unit,
    onOpenDraft: ((DiagnosticsReport) -> Boolean)? = null,
) {
    if (!visible) return

    val dimensions = LocalFocusFlowDimensions.current
    val context = LocalContext.current
    var description by remember { mutableStateOf("") }
    var reportType by remember { mutableStateOf(DiagnosticsReportType.BUG) }
    var includeLogs by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusSuccess by remember { mutableStateOf(true) }

    LaunchedEffect(visible) {
        description = ""
        reportType = DiagnosticsReportType.BUG
        includeLogs = true
        busy = false
        statusMessage = null
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onClose() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = DarkCard,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = dimensions.modalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BrandPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Email,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        "Report an Issue",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                }

                IconButton(onClick = onClose, enabled = !busy) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = DarkTextSecondary,
                    )
                }
            }

            // Info notice callout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "This opens your email app with a pre-filled draft addressed to tbtechsdev@gmail.com. Review everything and tap Send yourself — nothing is transmitted automatically.",
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        color = DarkTextSecondary,
                    )
                }
            }

            // 1. Report Type Selection (12a)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "WHAT WOULD YOU LIKE TO SHARE?",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandPrimary,
                    letterSpacing = 0.5.sp,
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DiagnosticsReportType.entries.forEach { type ->
                        val isSelected = reportType == type
                        val bg = if (isSelected) BrandPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant
                        val border = if (isSelected) BrandPrimary else DarkBorder
                        val textColor = if (isSelected) Color.White else DarkTextSecondary

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .border(1.dp, border, RoundedCornerShape(12.dp))
                                .clickable { reportType = type }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                                Text(
                                    text = type.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = textColor,
                                )
                            }
                        }
                    }
                }
            }

            // 2. Message / What happened field (12a)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (reportType == DiagnosticsReportType.BUG) "WHAT HAPPENED?" else "YOUR MESSAGE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandPrimary,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    if (reportType == DiagnosticsReportType.BUG) {
                        "Describe what you were doing, what went wrong, and any error message you noticed."
                    } else {
                        "Your feedback helps us refine FocusFlow. Share your suggestions or thoughts."
                    },
                    fontSize = 12.sp,
                    color = DarkTextSecondary,
                    lineHeight = 16.sp,
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 2_000) description = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 7,
                    placeholder = {
                        Text(
                            "e.g. When I turned on Focus Mode, blocked apps opened for a second before closing...",
                            color = DarkTextMuted,
                            fontSize = 13.sp,
                        )
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = DarkTextPrimary,
                        unfocusedTextColor = DarkTextPrimary,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                    ),
                )
            }

            // 3. Diagnostics Checkbox (12b)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    .clickable { includeLogs = !includeLogs }
                    .padding(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = includeLogs,
                        onCheckedChange = { includeLogs = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = BrandPrimary,
                            uncheckedColor = DarkTextMuted,
                            checkmarkColor = Color.White,
                        ),
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Include diagnostic logs",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkTextPrimary,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Includes recent app events and device Android version to help troubleshoot.",
                            fontSize = 12.sp,
                            color = DarkTextSecondary,
                            lineHeight = 16.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Personal files, contacts, installed-app lists, and location are never collected or included.",
                            fontSize = 11.5.sp,
                            color = DarkTextMuted,
                            lineHeight = 15.sp,
                        )
                    }
                }
            }

            // Feedback / Status message if attempted
            statusMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (statusSuccess) BrandPrimary.copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f))
                        .padding(12.dp),
                ) {
                    Text(
                        msg,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (statusSuccess) BrandPrimary else Color(0xFFF87171),
                    )
                }
            }

            // Actions: Open Email Draft & Cancel
            Button(
                onClick = {
                    busy = true
                    val errorDetails = error?.let {
                        "Error message: ${it.message}\nStack trace:\n${it.stackTraceToString()}\n\n"
                    }.orEmpty()
                    val report = DiagnosticsReport(
                        description = description,
                        logs = if (includeLogs) errorDetails + formatDiagnosticLogs(logs) else "",
                        type = reportType,
                    )
                    val opened = onOpenDraft?.invoke(report)
                        ?: openDiagnosticsEmailDraft(context, report)
                    busy = false
                    statusSuccess = opened
                    statusMessage = if (opened) {
                        "Email draft opened in your email app. Tap Send when ready."
                    } else {
                        "No email app found. Please email tbtechsdev@gmail.com manually."
                    }
                },
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            "Open Email Draft",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onClose,
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextSecondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp),
            ) {
                Text("Cancel", fontSize = 14.sp)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
