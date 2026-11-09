package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Content of the Apps and Habits tabs (Overview is rendered by StatsScreen itself). */
@Composable
internal fun ExtraPhoneContent(
    tab: ExtraTab,
    phoneViewModel: PhoneStatsViewModel,
    window: String,
    onOpenUsageAccessSettings: () -> Unit,
    onBlockApp: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by phoneViewModel.report.collectAsState()
    val insights by phoneViewModel.insights.collectAsState()

    Column(modifier.fillMaxSize()) {
        when (val s = state) {
            is PhoneReportUiState.Ready -> when (tab) {
                ExtraTab.OVERVIEW -> Unit // rendered by StatsScreen itself
                ExtraTab.APPS -> ExtraAppsTab(s.report, onBlockApp)
                ExtraTab.HABITS -> ExtraHabitsTab(s.report, window, insights, onBlockApp)
            }
            else -> PhoneStateMessage(s, onOpenUsageAccessSettings, Modifier.padding(16.dp))
        }
    }
}
