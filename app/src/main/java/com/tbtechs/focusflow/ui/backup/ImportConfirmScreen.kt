package com.tbtechs.focusflow.ui.backup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.backup.BackupEnvelope
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.WarningBorder
import com.tbtechs.focusflow.ui.theme.WarningIcon
import com.tbtechs.focusflow.ui.theme.WarningSurface
import com.tbtechs.focusflow.ui.theme.scaledSp

private val ImportDangerRed = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportConfirmScreen(
    importState: ImportState,
    pendingEnvelope: BackupEnvelope?,
    onConfirm: (replaceTasks: Boolean) -> Unit,
    onCancelImport: () -> Unit,
    onResetImport: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val isRestoring = importState == ImportState.Restoring
    val canCancel = !isRestoring
    var replaceTasks by remember(pendingEnvelope) { mutableStateOf(false) }
    val shouldShowError = importState is ImportState.Error
    val shouldShowSuccess = importState is ImportState.Success

    fun cancelAndReturn() {
        if (canCancel) {
            onCancelImport()
            onCancel()
        }
    }

    BackHandler(
        enabled = canCancel && !shouldShowError && !shouldShowSuccess,
        onBack = ::cancelAndReturn,
    )

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Import backup",
                        color = DarkTextPrimary,
                        fontSize = 18.scaledSp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = ::cancelAndReturn,
                        enabled = canCancel,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cancel import",
                            tint = if (canCancel) DarkTextPrimary else DarkTextMuted,
                        )
                    }
                },
                actions = { Spacer(Modifier.size(48.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            pendingEnvelope?.let { envelope ->
                ImportHeroCard()
                ImportSectionLabel("THIS FILE CONTAINS")
                BackupSummaryCard(envelope = envelope)
                Text(
                    text = "Exported ${envelope.exportedAtHuman.ifBlank { "Unknown" }} · " +
                        "App version ${envelope.appVersion?.takeIf(String::isNotBlank) ?: "Unknown"}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    color = DarkTextMuted,
                    fontSize = 12.scaledSp,
                    lineHeight = 17.scaledSp,
                    textAlign = TextAlign.Center,
                )
                val previewWarnings =
                    (importState as? ImportState.PendingConfirm)?.warnings.orEmpty()
                if (previewWarnings.isNotEmpty()) {
                    ImportWarningCard(
                        warnings = previewWarnings,
                        backgroundColor = WarningSurface,
                        borderColor = WarningBorder,
                        iconColor = WarningIcon,
                    )
                }

                ImportSectionLabel("IMPORT BEHAVIOR")
                ImportBehaviorCard(
                    replaceTasks = replaceTasks,
                    enabled = importState is ImportState.PendingConfirm,
                    onReplaceTasksChange = { replaceTasks = it },
                )
                if (replaceTasks) {
                    DestructiveImportWarning()
                }
            }
            if (pendingEnvelope == null && importState != ImportState.Reading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        tint = DarkTextMuted,
                        modifier = Modifier.size(34.dp),
                    )
                    Text(
                        text = when (importState) {
                            ImportState.Reading -> "Reading backup…"
                            ImportState.Restoring -> "Restoring backup…"
                            else -> "No backup is ready to import."
                        },
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = DarkTextSecondary,
                        fontSize = 14.scaledSp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (importState == ImportState.Reading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(22.dp)
                            .semantics { stateDescription = "In progress" },
                        color = BrandPrimary,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Reading backup…",
                        color = DarkTextSecondary,
                        fontSize = 14.scaledSp,
                    )
                }
            }

            if (importState !is ImportState.Success && importState !is ImportState.Error) {
                Button(
                    onClick = { onConfirm(replaceTasks) },
                    enabled = importState is ImportState.PendingConfirm && pendingEnvelope != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (replaceTasks) ImportDangerRed else BrandPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = DarkSurfaceVariant,
                        disabledContentColor = DarkTextMuted,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(19.dp)
                                .semantics { stateDescription = "In progress" },
                            color = DarkTextPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(
                        text = when {
                            isRestoring -> "Restoring…"
                            replaceTasks -> "Replace & Import"
                            else -> "Merge & Import"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (canCancel) {
                    OutlinedButton(
                        onClick = ::cancelAndReturn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DarkTextPrimary,
                        ),
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    (importState as? ImportState.Error)?.let { error ->
        AlertDialog(
            onDismissRequest = onResetImport,
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Could not restore backup", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = error.message,
                    modifier = Modifier
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = onResetImport) {
                    Text("OK", color = BrandPrimary)
                }
            },
        )
    }

    (importState as? ImportState.Success)?.let { success ->
        AlertDialog(
            onDismissRequest = {
                onResetImport()
                onDone()
            },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Backup restored", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${success.tasksImported} tasks imported.")
                    Text("${success.tasksSkipped} tasks skipped.")
                    if (success.warnings.isNotEmpty()) {
                        Text(
                            text = if (success.warnings.size == 1) {
                                "1 note"
                            } else {
                                "${success.warnings.size} notes"
                            },
                            color = DarkTextSecondary,
                            fontSize = 13.scaledSp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            itemsIndexed(
                                items = success.warnings,
                                key = { index, _ -> index },
                            ) { _, warning ->
                                Text(warning, color = DarkTextSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetImport()
                        onDone()
                    },
                ) {
                    Text("Done", color = BrandPrimary)
                }
            },
        )
    }
}

@Composable
private fun ImportHeroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = BrandPrimary.copy(alpha = 0.12f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    tint = BrandPrimary,
                    modifier = Modifier
                        .padding(14.dp)
                        .size(28.dp),
                )
            }
            Text(
                text = "FocusFlow backup",
                color = DarkTextPrimary,
                fontSize = 22.scaledSp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Review what will be brought onto this device before anything changes.",
                color = DarkTextSecondary,
                fontSize = 13.scaledSp,
                lineHeight = 20.scaledSp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ImportSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 4.dp),
        color = DarkTextSecondary,
        fontSize = 13.scaledSp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.6.sp,
    )
}

@Composable
private fun ImportWarningCard(
    warnings: List<String>,
    backgroundColor: Color,
    borderColor: Color,
    iconColor: Color,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(19.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                warnings.forEach { warning ->
                    Text(
                        text = warning,
                        color = DarkTextPrimary,
                        fontSize = 13.scaledSp,
                        lineHeight = 19.scaledSp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportBehaviorCard(
    replaceTasks: Boolean,
    enabled: Boolean,
    onReplaceTasksChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = replaceTasks,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onReplaceTasksChange,
                )
                .semantics {
                    stateDescription = if (replaceTasks) {
                        "Replace all existing tasks"
                    } else {
                        "Merge with existing tasks"
                    }
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (replaceTasks) {
                        "Replace all existing tasks"
                    } else {
                        "Merge with existing tasks"
                    },
                    color = DarkTextPrimary,
                    fontSize = 15.scaledSp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (replaceTasks) {
                        "Current tasks will be deleted before backup tasks are restored. " +
                            "Portable settings are still merged."
                    } else {
                        "Existing task IDs are kept. New tasks are added, and portable settings " +
                            "are merged without clearing other current settings."
                    },
                    color = DarkTextSecondary,
                    fontSize = 13.scaledSp,
                    lineHeight = 19.scaledSp,
                )
            }
            Switch(
                checked = replaceTasks,
                onCheckedChange = null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ImportDangerRed,
                    checkedTrackColor = ImportDangerRed.copy(alpha = 0.45f),
                    uncheckedThumbColor = DarkTextSecondary,
                    uncheckedTrackColor = DarkBorder,
                ),
            )
        }
    }
}

@Composable
private fun DestructiveImportWarning() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = ImportDangerRed.copy(alpha = 0.10f),
        ),
        border = BorderStroke(1.dp, ImportDangerRed.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = ImportDangerRed,
                modifier = Modifier.size(19.dp),
            )
            Text(
                modifier = Modifier.weight(1f),
                text = "This permanently deletes all current tasks. Replacement is blocked " +
                    "while a Focus Session is active.",
                color = DarkTextPrimary,
                fontSize = 13.scaledSp,
                lineHeight = 19.scaledSp,
            )
        }
    }
}

@Composable
private fun BackupSummaryCard(envelope: BackupEnvelope) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            BackupSummaryRow(
                icon = Icons.Outlined.Description,
                label = "Tasks",
                value = envelope.summary.taskCount.toString(),
            )
            HorizontalDivider(color = DarkBorder.copy(alpha = 0.6f))
            BackupSummaryRow(
                icon = Icons.Outlined.Settings,
                label = "Settings fields",
                value = envelope.settings.size.toString(),
            )
            HorizontalDivider(color = DarkBorder.copy(alpha = 0.6f))
            BackupSummaryRow(
                icon = Icons.Outlined.Block,
                label = "Blocked words",
                value = envelope.summary.blockedWordCount.toString(),
            )
            HorizontalDivider(color = DarkBorder.copy(alpha = 0.6f))
            BackupSummaryRow(
                icon = Icons.Outlined.AccessTime,
                label = "Schedule windows",
                value = envelope.summary.greyoutWindowCount.toString(),
            )
            HorizontalDivider(color = DarkBorder.copy(alpha = 0.6f))
            BackupSummaryRow(
                icon = Icons.Outlined.Timer,
                label = "Daily allowances",
                value = envelope.summary.dailyAllowanceCount.toString(),
            )
        }
    }
}

@Composable
private fun BackupSummaryRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BrandPrimary,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = DarkTextPrimary,
            fontSize = 15.scaledSp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            modifier = Modifier.padding(start = 8.dp),
            color = DarkTextPrimary,
            fontSize = 15.scaledSp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
