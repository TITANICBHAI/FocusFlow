package com.tbtechs.focusflow.ui.blocklist

import androidx.compose.runtime.Composable
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.ui.SettingsViewModel

/** Architecture-package entry point for the VPN block-list screen. */
@Composable
fun VpnBlockListScreen(
    settingsViewModel: SettingsViewModel,
    vpnRepository: VpnRepository,
    installedAppsRepository: InstalledAppsRepository,
    isFocusActive: Boolean = false,
    onBack: () -> Unit,
) = com.tbtechs.focusflow.ui.launcher.VpnBlockListScreen(
    settingsViewModel = settingsViewModel,
    vpnRepository = vpnRepository,
    installedAppsRepository = installedAppsRepository,
    isFocusActive = isFocusActive,
    onBack = onBack,
)