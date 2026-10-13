package com.tbtechs.focusflow.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tbtechs.focusflow.analytics.ANALYTICS_TODAY
import com.tbtechs.focusflow.analytics.AnalyticsWindow
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.di.AppModule

/**
 * The visible Stats route uses the archived four-period report as its primary
 * experience. The newer behavioural-intelligence view is available as Extra.
 */
@Composable
fun StatsScreen(
    statsViewModel: StatsViewModel,
    onOpenUsageAccessSettings: () -> Unit = {},
    onOpenActiveBlocks: () -> Unit = {},
    onOpenQuickBlock: (String?) -> Unit = {},
    focusDayRating: Boolean = false,
    dayRatingRequestNonce: Int = 0,
    onDayRatingRequestHandled: (Int) -> Unit = {},
) {
    var showExtra by rememberSaveable { mutableStateOf(false) }
    var archivedWindow by rememberSaveable { mutableStateOf<AnalyticsWindow>(ANALYTICS_TODAY) }
    var handledDayRatingRequestNonce by rememberSaveable { mutableStateOf(0) }
    var pendingRatingFocusNonce by rememberSaveable { mutableStateOf(0) }
    val reflectionPromptsEnabled = remember {
        AppModule.settingsRepository.getBoolean(
            SettingsRepository.REFLECTION_PROMPTS_ENABLED_KEY,
            defaultValue = true,
        )
    }

    LaunchedEffect(focusDayRating, dayRatingRequestNonce, reflectionPromptsEnabled) {
        if (!focusDayRating) return@LaunchedEffect
        val plan = planDayRatingNotification(
            requestNonce = dayRatingRequestNonce,
            lastHandledRequestNonce = handledDayRatingRequestNonce,
            reflectionPromptsEnabled = reflectionPromptsEnabled,
        ) ?: return@LaunchedEffect

        handledDayRatingRequestNonce = plan.requestNonce
        statsViewModel.setWindow(ANALYTICS_TODAY)
        if (plan.openRatingExperience) {
            pendingRatingFocusNonce = plan.requestNonce
            showExtra = true
        } else {
            showExtra = false
            onDayRatingRequestHandled(plan.requestNonce)
        }
    }

    if (showExtra) {
        StatsInsightsExperience(
            statsViewModel = statsViewModel,
            onOpenUsageAccessSettings = onOpenUsageAccessSettings,
            onOpenActiveBlocks = onOpenActiveBlocks,
            onOpenQuickBlock = onOpenQuickBlock,
            dayRatingFocusRequestNonce = pendingRatingFocusNonce,
            onDayRatingFocusHandled = { requestNonce ->
                if (pendingRatingFocusNonce == requestNonce) {
                    pendingRatingFocusNonce = 0
                    onDayRatingRequestHandled(requestNonce)
                }
            },
            screenTitle = "Extra",
            onOpenArchived = {
                if (pendingRatingFocusNonce > 0) {
                    val requestNonce = pendingRatingFocusNonce
                    pendingRatingFocusNonce = 0
                    onDayRatingRequestHandled(requestNonce)
                }
                statsViewModel.setWindow(archivedWindow)
                showExtra = false
            },
        )
    } else {
        ArchivedStatsScreen(
            statsViewModel = statsViewModel,
            onOpenUsageAccessSettings = onOpenUsageAccessSettings,
            onOpenActiveBlocks = onOpenActiveBlocks,
            onOpenQuickBlock = onOpenQuickBlock,
            onOpenExtra = {
                archivedWindow = statsViewModel.activeWindow.value
                showExtra = true
            },
        )
    }
}

/** Preserved current analytics implementation, isolated from the RN-style route. */
@Composable
fun CurrentStatsScreen(
    statsViewModel: StatsViewModel,
    onOpenUsageAccessSettings: () -> Unit = {},
    onOpenActiveBlocks: () -> Unit = {},
    onOpenQuickBlock: (String?) -> Unit = {},
) = StatsInsightsExperience(
    statsViewModel = statsViewModel,
    onOpenUsageAccessSettings = onOpenUsageAccessSettings,
    onOpenActiveBlocks = onOpenActiveBlocks,
    onOpenQuickBlock = onOpenQuickBlock,
)