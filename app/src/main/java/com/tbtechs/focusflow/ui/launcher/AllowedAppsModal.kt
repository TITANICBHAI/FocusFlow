package com.tbtechs.focusflow.ui.launcher

import androidx.compose.runtime.Composable
import com.tbtechs.focusflow.data.model.AllowedAppPreset
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository

/**
 * Focus-mode wrapper around the shared picker.
 *
 * In this context an empty saved list means "block all", so it is passed
 * through unchanged instead of being expanded to the installed-app list.
 */
@Composable
fun AllowedAppsModal(
    visible: Boolean,
    initialSelected: List<String>,
    presets: List<AllowedAppPreset>,
    installedAppsRepository: InstalledAppsRepository,
    onSave: (List<String>) -> Unit,
    onSavePreset: (AllowedAppPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onClose: () -> Unit,
) {
    AppPickerSheet(
        visible = visible,
        title = "Allowed During Focus",
        initialSelected = initialSelected,
        noneWhenEmpty = true,
        presets = presets,
        installedAppsRepository = installedAppsRepository,
        onSave = onSave,
        onSavePreset = onSavePreset,
        onDeletePreset = onDeletePreset,
        onClose = onClose,
    )
}