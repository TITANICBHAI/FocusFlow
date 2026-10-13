package com.tbtechs.focusflow.ui.focus

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.TaskViewModel
import com.tbtechs.focusflow.ui.active.ActiveScreen

/**
 * Backward-compatible delegator to canonical [ActiveScreen].
 */
@Composable
fun ActiveBlockScreen(
    taskViewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory),
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    focusSessionViewModel: FocusSessionViewModel = viewModel(factory = FocusSessionViewModel.Factory),
    onBack: () -> Unit = {},
    onOpenFocus: () -> Unit = {},
    onOpenAlwaysOn: () -> Unit = {},
    onOpenDefense: () -> Unit = {},
    onOpenKeywordBlocker: () -> Unit = {},
    onOpenVpnBlockList: () -> Unit = {},
) {
    ActiveScreen(
        taskViewModel = taskViewModel,
        settingsViewModel = settingsViewModel,
        focusSessionViewModel = focusSessionViewModel,
        onBack = onBack,
        onOpenFocus = onOpenFocus,
        onOpenAlwaysOn = onOpenAlwaysOn,
        onOpenDefense = onOpenDefense,
        onOpenKeywordBlocker = onOpenKeywordBlocker,
        onOpenVpnBlockList = onOpenVpnBlockList,
    )
}
