package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.ui.stats.phone.ExtraPhoneContent
import com.tbtechs.focusflow.ui.stats.phone.ExtraTab
import com.tbtechs.focusflow.ui.stats.phone.ExtraTabRow
import com.tbtechs.focusflow.ui.stats.phone.PhoneInsightsScreen
import com.tbtechs.focusflow.ui.stats.phone.PhoneStatsSection
import com.tbtechs.focusflow.ui.stats.phone.PhoneStatsViewModel
import com.tbtechs.focusflow.ui.stats.phone.StatsDestination
import com.tbtechs.focusflow.ui.stats.phone.StatsDestinationTabs
import com.tbtechs.focusflow.ui.theme.DarkBackground
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
    var destination by rememberSaveable { mutableStateOf(StatsDestination.STATS) }
    var extraTab by rememberSaveable { mutableStateOf(ExtraTab.OVERVIEW) }
    var archivedWindow by rememberSaveable { mutableStateOf<AnalyticsWindow>(ANALYTICS_TODAY) }
    var handledDayRatingRequestNonce by rememberSaveable { mutableStateOf(0) }
    var pendingRatingFocusNonce by rememberSaveable { mutableStateOf(0) }
    val reflectionPromptsEnabled = remember {
        AppModule.settingsRepository.getBoolean(
            SettingsRepository.REFLECTION_PROMPTS_ENABLED_KEY,
            defaultValue = true,
        )
    }
    val phoneViewModel: PhoneStatsViewModel = viewModel(factory = PhoneStatsViewModel.Factory)
    val window by statsViewModel.activeWindow.collectAsState()
    val weekMode by statsViewModel.weekViewMode.collectAsState()
    val weekStartDay by statsViewModel.weekStartDay.collectAsState()

    fun selectDestination(target: StatsDestination) {
        if (target == destination) return
        if (destination == StatsDestination.EXTRA && pendingRatingFocusNonce > 0) {
            val requestNonce = pendingRatingFocusNonce
            pendingRatingFocusNonce = 0
            onDayRatingRequestHandled(requestNonce)
        }
        if (destination == StatsDestination.STATS) archivedWindow = statsViewModel.activeWindow.value
        if (target == StatsDestination.STATS) statsViewModel.setWindow(archivedWindow)
        destination = target
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
            extraTab = ExtraTab.OVERVIEW
            destination = StatsDestination.EXTRA
        } else {
            destination = StatsDestination.STATS
            onDayRatingRequestHandled(plan.requestNonce)
        }
    }

    // Load phone-habit data for the screens that show it.
    LaunchedEffect(window, weekMode, weekStartDay, destination, extraTab) {
        val needsReport = destination == StatsDestination.STATS ||
            (destination == StatsDestination.EXTRA && extraTab != ExtraTab.OVERVIEW)
        if (needsReport) phoneViewModel.loadReport(window, weekMode, weekStartDay)
    }
    LaunchedEffect(destination) {
        if (destination == StatsDestination.INSIGHTS) phoneViewModel.loadInsights()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) phoneViewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        StatsDestinationTabs(
            selected = destination,
            onSelect = { selectDestination(it) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (destination) {
                StatsDestination.STATS -> ArchivedStatsScreen(
                    statsViewModel = statsViewModel,
                    onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                    onOpenActiveBlocks = onOpenActiveBlocks,
                    onOpenQuickBlock = onOpenQuickBlock,
                    onOpenExtra = { selectDestination(StatsDestination.EXTRA) },
                    phoneSection = {
                        PhoneStatsSection(
                            phoneViewModel = phoneViewModel,
                            window = window,
                            onOpenExtra = { selectDestination(StatsDestination.EXTRA) },
                            onOpenInsights = { selectDestination(StatsDestination.INSIGHTS) },
                        )
                    },
                )
                StatsDestination.EXTRA -> Column(modifier = Modifier.fillMaxSize()) {
                    ExtraTabRow(
                        selected = extraTab,
                        onSelect = { extraTab = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (extraTab == ExtraTab.OVERVIEW) {
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
                                onOpenArchived = { selectDestination(StatsDestination.STATS) },
                            )
                        } else {
                            ExtraPhoneContent(
                                tab = extraTab,
                                phoneViewModel = phoneViewModel,
                                statsViewModel = statsViewModel,
                                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                            )
                        }
                    }
                }
                StatsDestination.INSIGHTS -> PhoneInsightsScreen(
                    phoneViewModel = phoneViewModel,
                    onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                )
            }
        }
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