package com.tbtechs.focusflow.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Temporary package-entry fallback for the nested picker used by the reference UI.
 * NEEDS: InstalledAppsRepository-backed AppPickerSheet, including selection presets.
 */
@Composable
internal fun AllowedAppsDialog(value: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var packages by remember(value) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allowed apps") },
        text = {
            HomeTextField(
                value = packages,
                onValueChange = { packages = it },
                label = "Package names (comma separated)",
                singleLine = false,
            )
        },
        confirmButton = { Button(onClick = { onSave(packages) }) { Text("Save") } },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
    )
}
