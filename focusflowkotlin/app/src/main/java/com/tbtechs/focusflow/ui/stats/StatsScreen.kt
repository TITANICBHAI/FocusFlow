package com.tbtechs.focusflow.ui.stats

import androidx.compose.runtime.Composable

/** Canonical architecture name for the current analytics experience. */
@Composable
fun StatsScreen(
    statsViewModel: StatsViewModel,
    onOpenUsageAccessSettings: () -> Unit = {},
) = StatsInsightsExperience(
    statsViewModel = statsViewModel,
    onOpenUsageAccessSettings = onOpenUsageAccessSettings,
)