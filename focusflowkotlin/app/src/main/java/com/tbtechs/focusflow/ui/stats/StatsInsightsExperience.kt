package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.analytics.ANALYTICS_THREE_MONTHS
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import com.tbtechs.focusflow.analytics.ANALYTICS_YESTERDAY
import com.tbtechs.focusflow.analytics.AnalyticsWindow

/** The superseding Stats experience; do not substitute UsageInsights or WeeklyReport. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsInsightsExperience(
    statsViewModel: StatsViewModel = viewModel(),
    onOpenUsageAccessSettings: () -> Unit = {},
) {
    val snapshot by statsViewModel.analyticsSnapshot.collectAsState()
    val insights by statsViewModel.insightCards.collectAsState()
    val weeklyStandout by statsViewModel.weeklyStandout.collectAsState()
    val achievements by statsViewModel.achievementState.collectAsState()
    val state by statsViewModel.loadState.collectAsState()
    val window by statsViewModel.activeWindow.collectAsState()
    var localNoticeDismissed by remember { mutableStateOf(false) }

    Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Stats")
                    Text(windowSubtitle(window), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            actions = { Icon(Icons.Outlined.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        )
        AnalyticsWindowTabs(activeWindow = window, onSelect = statsViewModel::setWindow)
        if (window == ANALYTICS_THREE_MONTHS && !localNoticeDismissed) {
            LocalOnlyNotice(onDismiss = {
                // NEEDS: persist this dismissal in AppSettings; no privacy-notice field is exposed yet.
                localNoticeDismissed = true
            })
        }
        when (state) {
            StatsLoadState.Loading -> LoadingStats()
            StatsLoadState.PermissionNeeded -> PermissionGate(onOpenUsageAccessSettings)
            StatsLoadState.Unavailable -> UnavailableGate()
            is StatsLoadState.Error -> ErrorStats(onRetry = statsViewModel::reload)
            StatsLoadState.Ready -> snapshot?.let { loaded ->
                LazyColumn(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                    item {
                        if (window == ANALYTICS_WEEK) weeklyStandout?.let(::InsightCardView)
                        DataHealthNotice(loaded)
                        if (loaded.tasks.total == 0 && loaded.sessions.total == 0 && loaded.blocking.totalAttempts == 0 && window != ANALYTICS_THREE_MONTHS) {
                            EmptyStatsState(window)
                        }
                        insights.filter { it.id != weeklyStandout?.id }.forEach(::InsightCardView)
                        when (window) {
                            ANALYTICS_WEEK -> {
                                PresenceStrip(loaded)
                                TaskSummary(loaded)
                            }
                            ANALYTICS_YESTERDAY -> TaskResultList(loaded)
                            ANALYTICS_THREE_MONTHS -> {
                                PhoneUsageSummary(loaded)
                                TrendChart(loaded)
                            }
                        }
                        if (window != ANALYTICS_THREE_MONTHS) achievements?.let(::AchievementRow)
                    }
                }
            } ?: LoadingStats()
        }
    }
}

@Composable
private fun AnalyticsWindowTabs(activeWindow: AnalyticsWindow, onSelect: (AnalyticsWindow) -> Unit) =
    SingleChoiceSegmentedButtonRow(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
        listOf(ANALYTICS_YESTERDAY to "Yesterday", ANALYTICS_WEEK to "This Week", ANALYTICS_THREE_MONTHS to "3 Months").forEachIndexed { index, (window, label) ->
            SegmentedButton(
                selected = activeWindow == window,
                onClick = { onSelect(window) },
                shape = SegmentedButtonDefaults.itemShape(index, 3),
            ) { Text(label) }
        }
    }

@Composable
private fun LoadingStats() = Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
    CircularProgressIndicator()
    Text("Reading your local history…", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ErrorStats(onRetry: () -> Unit) = Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
    Text("Stats unavailable", style = MaterialTheme.typography.headlineSmall)
    Text("FocusFlow could not read this period. Return to the view to try again.")
    androidx.compose.material3.Button(onClick = onRetry) { Text("Try again") }
}

private fun windowSubtitle(window: AnalyticsWindow): String = when (window) {
    ANALYTICS_YESTERDAY -> "The day that just ended"
    ANALYTICS_THREE_MONTHS -> "What your routine has held over time"
    else -> "The pattern forming this week"
}
