package com.tbtechs.focusflow.ui.stats.phone

import com.tbtechs.focusflow.analytics.phone.PhoneReport
import com.tbtechs.focusflow.analytics.phone.PhoneReportResult
import com.tbtechs.focusflow.analytics.phone.PhoneReportSource
import com.tbtechs.focusflow.analytics.phone.PhoneDayStatus
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsight
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightInput
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightInputResult
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightsResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PhoneReportUiState {
    object Loading : PhoneReportUiState
    object PermissionNeeded : PhoneReportUiState
    object NoData : PhoneReportUiState
    data class Ready(val report: PhoneReport) : PhoneReportUiState
    data class Failed(val message: String) : PhoneReportUiState
}

sealed interface PhoneInsightsUiState {
    object Loading : PhoneInsightsUiState
    data class Loaded(val result: PhoneInsightsResult) : PhoneInsightsUiState
}

/**
 * All the loading logic of [PhoneStatsViewModel], kept free of Android so it can be unit-tested with plain
 * coroutines. The ViewModel owns the scope (`viewModelScope`) and nothing else.
 *
 * - a request that differs from the last *loaded* one shows `Loading`; repeating the same request keeps the
 *   current state (no flicker on refresh);
 * - starting a new load cancels the previous one, and a cancelled load never writes state.
 */
class PhoneStatsStateHolder(
    private val scope: CoroutineScope,
    private val source: PhoneReportSource,
    private val generateInsights: (PhoneInsightInput) -> List<PhoneInsight>,
    private val workDispatcher: CoroutineDispatcher,
) {
    private data class ReportRequest(val window: String, val weekMode: String, val weekStartDay: Int)

    private val _report = MutableStateFlow<PhoneReportUiState>(PhoneReportUiState.Loading)
    val report: StateFlow<PhoneReportUiState> = _report.asStateFlow()

    private val _insights = MutableStateFlow<PhoneInsightsUiState>(PhoneInsightsUiState.Loading)
    val insights: StateFlow<PhoneInsightsUiState> = _insights.asStateFlow()

    private var lastRequest: ReportRequest? = null
    private var lastLoaded: ReportRequest? = null
    private var reportJob: Job? = null
    private var insightsRequested = false
    private var insightsJob: Job? = null

    fun loadReport(window: String, weekMode: String, weekStartDay: Int) {
        val request = ReportRequest(window, weekMode, weekStartDay)
        lastRequest = request
        reportJob?.cancel()
        if (request != lastLoaded) _report.value = PhoneReportUiState.Loading
        reportJob = scope.launch {
            val state = try {
                source.loadReport(window, weekMode, weekStartDay).toUiState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PhoneReportUiState.Failed(e.message ?: "Could not load phone usage")
            }
            ensureActive()
            _report.value = state
            lastLoaded = request
        }
    }

    fun loadInsights() {
        insightsRequested = true
        insightsJob?.cancel()
        insightsJob = scope.launch {
            val result = try {
                when (val input = source.loadInsightInput()) {
                    PhoneInsightInputResult.PermissionNeeded -> PhoneInsightsResult.PermissionNeeded
                    is PhoneInsightInputResult.Building -> PhoneInsightsResult.Building(input.completeDays)
                    is PhoneInsightInputResult.Failed -> PhoneInsightsResult.Failed(input.message)
                    is PhoneInsightInputResult.Ready -> {
                        val data = input.input
                        val list = withContext(workDispatcher) { generateInsights(data) }
                        PhoneInsightsResult.Ready(list, data.days.count { it.status == PhoneDayStatus.COMPLETE })
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PhoneInsightsResult.Failed(e.message ?: "Could not build insights")
            }
            ensureActive()
            _insights.value = PhoneInsightsUiState.Loaded(result)
        }
    }

    /** Re-runs the last report request and, if insights were requested before, the insights. */
    fun refresh() {
        lastRequest?.let { loadReport(it.window, it.weekMode, it.weekStartDay) }
        if (insightsRequested) loadInsights()
    }

    private fun PhoneReportResult.toUiState(): PhoneReportUiState = when (this) {
        PhoneReportResult.PermissionNeeded -> PhoneReportUiState.PermissionNeeded
        PhoneReportResult.NoData -> PhoneReportUiState.NoData
        is PhoneReportResult.Ready -> PhoneReportUiState.Ready(report)
        is PhoneReportResult.Failed -> PhoneReportUiState.Failed(message)
    }
}
