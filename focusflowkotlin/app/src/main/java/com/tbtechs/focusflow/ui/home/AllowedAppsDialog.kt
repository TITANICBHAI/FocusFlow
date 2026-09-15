package com.tbtechs.focusflow.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.ui.launcher.AppPickerSheet

/**
 * Installed-app picker used by the nested focus-task editor.
 */
@Composable
internal fun AllowedAppsDialog(value: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val context = LocalContext.current
    val repository = remember { InstalledAppsRepository(context) }
    AppPickerSheet(
        visible = true,
        title = "Allowed apps",
        initialSelected = value.split(",").map(String::trim).filter(String::isNotBlank),
        presets = emptyList(),
        installedAppsRepository = repository,
        onSave = { onSave(it.joinToString(", ")); onDismiss() },
        onSavePreset = {},
        onDeletePreset = {},
        onClose = onDismiss,
    )
}
