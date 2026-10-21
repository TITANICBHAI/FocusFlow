package com.tbtechs.focusflow.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp

@Composable
internal fun BackupExportNoticeDialog(
    state: ExportState,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onExportWithoutImages: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            onDismiss()
            onReset()
        },
        containerColor = DarkCard,
        titleContentColor = DarkTextPrimary,
        textContentColor = DarkTextSecondary,
        title = {
            Text(
                when (state) {
                    is ExportState.Success -> "Backup exported"
                    is ExportState.TooLarge -> "Bundle too large"
                    else -> "Export failed"
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            when (state) {
                is ExportState.Success ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Saved as a ${state.containerDescription}.")
                        state.warnings.forEach { warning ->
                            Text(
                                text = warning,
                                color = DarkTextSecondary,
                                fontSize = 13.scaledSp,
                                lineHeight = 19.scaledSp,
                            )
                        }
                    }
                is ExportState.Error ->
                    Text(
                        text = state.message,
                        modifier = Modifier
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                is ExportState.TooLarge ->
                    Text(
                        "The ZIP bundle would exceed the 64 MiB limit. You can export the " +
                            "tasks without images instead.",
                    )
                else -> Unit
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (state is ExportState.TooLarge) {
                        onDismiss()
                        onExportWithoutImages(state.uri)
                    } else {
                        onDismiss()
                        onReset()
                    }
                },
            ) {
                Text(
                    if (state is ExportState.TooLarge) "Export without images" else "OK",
                    color = BrandPrimary,
                )
            }
        },
        dismissButton = if (state is ExportState.TooLarge) {
            {
                TextButton(
                    onClick = {
                        onDismiss()
                        onReset()
                    },
                ) { Text("Cancel", color = DarkTextSecondary) }
            }
        } else {
            null
        },
    )
}
