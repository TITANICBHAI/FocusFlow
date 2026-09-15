package com.tbtechs.focusflow.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tbtechs.focusflow.analytics.AchievementEngine
import com.tbtechs.focusflow.analytics.AchievementState
import com.tbtechs.focusflow.analytics.AnalyticsProcessor
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot
import com.tbtechs.focusflow.analytics.AnalyticsWindow
import com.tbtechs.focusflow.analytics.ANALYTICS_THREE_MONTHS
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import com.tbtechs.focusflow.analytics.InsightCard
import com.tbtechs.focusflow.analytics.InsightEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class StatsLoadState {
    data object Loading : StatsLoadState()
    data object Ready : StatsLoadState()
    data object PermissionNeeded : StatsLoadState()
    data object Unavailable : StatsLoadState()
    data class Error(val cause: Throwable) : StatsLoadState()
}

class StatsViewModel(
    private val analyticsProcessor: AnalyticsProcessor,
    private val insightEngine: InsightEngine,
    private val achievementEngine: AchievementEngine,
) : ViewModel() {
    private val _analyticsSnapshot = MutableStateFlow<AnalyticsSnapshot?>(null)
    private val _insightCards = MutableStateFlow<List<InsightCard>>(emptyList())
    private val _weeklyStandout = MutableStateFlow<InsightCard?>(null)
    private val _achievementState = MutableStateFlow<AchievementState?>(null)
    private val _loadState = MutableStateFlow<StatsLoadState>(StatsLoadState.Loading)
    private val _activeWindow = MutableStateFlow<AnalyticsWindow>(ANALYTICS_WEEK)

    val analyticsSnapshot: StateFlow<AnalyticsSnapshot?> = _analyticsSnapshot.asStateFlow()
    val insightCards: StateFlow<List<InsightCard>> = _insightCards.asStateFlow()
    val weeklyStandout: StateFlow<InsightCard?> = _weeklyStandout.asStateFlow()
    val achievementState: StateFlow<AchievementState?> = _achievementState.asStateFlow()
    val loadState: StateFlow<StatsLoadState> = _loadState.asStateFlow()
    val activeWindow: StateFlow<AnalyticsWindow> = _activeWindow.asStateFlow()

    private var loadJob: Job? = null

    init {
        reload()
    }

    fun setWindow(window: AnalyticsWindow) {
        if (window !in setOf("yesterday", ANALYTICS_WEEK, ANALYTICS_THREE_MONTHS)) return
        _activeWindow.value = window
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        val window = _activeWindow.value
        loadJob = viewModelScope.launch {
            _loadState.value = StatsLoadState.Loading
            _weeklyStandout.value = null
            try {
                val usagePermission = if (window == ANALYTICS_THREE_MONTHS) {
                    analyticsProcessor.hasUsageStatsPermission()
                } else {
                    null
                }
                if (window == ANALYTICS_THREE_MONTHS && usagePermission != true) {
                    _analyticsSnapshot.value = null
                    _insightCards.value = emptyList()
                    _achievementState.value = null
                    _loadState.value = StatsLoadState.PermissionNeeded
                    return@launch
                }

                val snapshot = analyticsProcessor.buildAnalyticsSnapshot(
                    window = window,
                    options = com.tbtechs.focusflow.analytics.AnalyticsBuildOptions(
                        usageStatsPermission = usagePermission,
                    ),
                )
                _analyticsSnapshot.value = snapshot
                _insightCards.value = insightEngine.buildInsights(snapshot)
                // Keep achievement persistence independent from weekly
                // standout recording. A transient weekly-ledger failure
                // should not prevent newly earned achievements from syncing.
                _achievementState.value = achievementEngine.syncAchievements(snapshot)
                if (window == ANALYTICS_WEEK) {
                    _weeklyStandout.value = insightEngine.syncWeeklyStandout(snapshot)
                }
                _loadState.value = if (hasUsableStats(snapshot)) {
                    StatsLoadState.Ready
                } else {
                    StatsLoadState.Unavailable
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _loadState.value = StatsLoadState.Error(error)
            }
        }
    }

    private fun hasUsableStats(snapshot: AnalyticsSnapshot): Boolean {
        val hasRecords = snapshot.tasks.total > 0 ||
            snapshot.sessions.total > 0 ||
            snapshot.blocking.totalAttempts > 0 ||
            (snapshot.trends?.weeksWithData ?: 0) > 0 ||
            snapshot.phoneUsage?.byHour?.values?.any { it > 0.0 } == true
        return hasRecords
    }
}