package com.tbtechs.focusflow.ui.launchersetup

import androidx.compose.runtime.Composable
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.LauncherController
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.ui.SettingsViewModel

/** Architecture-package entry point; implementation remains shared with the launcher activity UI. */
@Composable
fun LauncherSetupScreen(
    settingsViewModel: SettingsViewModel,
    settingsRepository: SettingsRepository,
    installedAppsRepository: InstalledAppsRepository,
    launcherController: LauncherController,
    onBack: () -> Unit,
) = com.tbtechs.focusflow.ui.launcher.LauncherSetupScreen(
    settingsViewModel = settingsViewModel,
    settingsRepository = settingsRepository,
    installedAppsRepository = installedAppsRepository,
    launcherController = launcherController,
    onBack = onBack,
)