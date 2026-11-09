package com.tbtechs.focusflow.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.analytics.ANALYTICS_THREE_MONTHS
import com.tbtechs.focusflow.analytics.ANALYTICS_TODAY
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.ui.stats.phone.ExtraHeader
import com.tbtechs.focusflow.ui.stats.phone.ExtraPhoneContent
import com.tbtechs.focusflow.ui.stats.phone.ExtraTab
import com.tbtechs.focusflow.ui.stats.phone.PhoneStatsSection
import com.tbtechs.focusflow.ui.stats.phone.PhoneStatsViewModel
import com.tbtechs.focusflow.ui.theme.DarkBackground

/**
 * Stats keeps its original shape: the period tabs, the Observed Device Time card, focus numbers and one
 * button into Extra. Extra (Overview / Apps / Habits) is a second level with its own back button.
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
    var extraTab by rememberSaveable { mutableStateOf(ExtraTab.OVERVIEW) }
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

    fun closeExtra() {
        if (pendingRatingFocusNonce > 0) {
            val requestNonce = pendingRatingFocusNonce
            pendingRatingFocusNonce = 0
            onDayRatingRequestHandled(requestNonce)
        }
        // Stats has no 3-month tab; fall back to Week so one is always selected.
        if (window == ANALYTICS_THREE_MONTHS) statsViewModel.setWindow(ANALYTICS_WEEK)
        showExtra = false
    }

    BackHandler(enabled = showExtra) { closeExtra() }

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
            showExtra = true
        } else {
            showExtra = false
            onDayRatingRequestHandled(plan.requestNonce)
        }
    }

    // Load phone-habit data for the screens that show it.
    LaunchedEffect(window, weekMode, weekStartDay, showExtra, extraTab) {
        if (!showExtra || extraTab != ExtraTab.OVERVIEW) phoneViewModel.loadReport(window, weekMode, weekStartDay)
    }
    LaunchedEffect(showExtra, extraTab) {
        if (showExtra && extraTab == ExtraTab.HABITS) phoneViewModel.loadInsights()
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
        if (!showExtra) {
            ArchivedStatsScreen(
                statsViewModel = statsViewModel,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenActiveBlocks = onOpenActiveBlocks,
                onOpenQuickBlock = onOpenQuickBlock,
                onOpenExtra = { showExtra = true },
                phoneSection = {
                    PhoneStatsSection(
                        phoneViewModel = phoneViewModel,
                        window = window,
                        onOpenHabits = {
                            extraTab = ExtraTab.HABITS
                            showExtra = true
                        },
                    )
                },
            )
        } else {
            ExtraHeader(
                window = window,
                onSelectWindow = statsViewModel::setWindow,
                onBack = { closeExtra() },
                selectedTab = extraTab,
                onSelectTab = { extraTab = it },
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
                        embedded = true,
                    )
                } else {
                    ExtraPhoneContent(
                        tab = extraTab,
                        phoneViewModel = phoneViewModel,
                        window = window,
                        onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                        onBlockApp = onOpenQuickBlock,
                    )
                }
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