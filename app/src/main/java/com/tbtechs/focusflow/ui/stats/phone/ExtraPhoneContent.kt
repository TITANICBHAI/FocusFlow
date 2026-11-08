package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.stats.StatsViewModel

/**
 * Content of the Extra tabs other than Overview (which the caller renders itself).
 * The window picker stays visible in every state so changing the window never removes it.
 */
@Composable
internal fun ExtraPhoneContent(
    tab: ExtraTab,
    phoneViewModel: PhoneStatsViewModel,
    statsViewModel: StatsViewModel,
    onOpenUsageAccessSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by phoneViewModel.report.collectAsState()
    val window by statsViewModel.activeWindow.collectAsState()

    Column(modifier.fillMaxSize()) {
        PhoneWindowTabs(
            window = window,
            onSelect = statsViewModel::setWindow,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
        when (val s = state) {
            is PhoneReportUiState.Ready -> when (tab) {
                ExtraTab.OVERVIEW -> Unit // rendered by StatsScreen itself
                ExtraTab.APPS -> ExtraAppsTab(s.report)
                ExtraTab.PHONE -> ExtraPhoneTab(s.report)
                ExtraTab.DISTRACTION -> ExtraDistractionTab(s.report)
                ExtraTab.FOCUS -> ExtraFocusTab(s.report)
            }
            else -> PhoneStateMessage(s, onOpenUsageAccessSettings, Modifier.padding(16.dp))
        }
    }
}
