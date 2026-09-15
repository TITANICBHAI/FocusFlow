package com.tbtechs.focusflow.ui.stats

import androidx.compose.runtime.Composable
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.ui.TaskViewModel

/** Canonical architecture name for the saved daily/weekly report experience. */
@Composable
fun ReportsScreen(
    taskViewModel: TaskViewModel,
    settingsRepository: SettingsRepository,
    reportType: ReportType = ReportType.Day,
    onBack: () -> Unit = {},
) = ReportScreen(
    reportType = reportType,
    taskViewModel = taskViewModel,
    settingsRepository = settingsRepository,
    onBack = onBack,
)