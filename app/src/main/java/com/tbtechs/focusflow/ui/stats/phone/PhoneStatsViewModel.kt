package com.tbtechs.focusflow.ui.stats.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.tbtechs.focusflow.analytics.phone.PhoneReportSource
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsight
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightEngine
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightInput
import com.tbtechs.focusflow.di.AppModule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

/** Thin Android wrapper: the logic lives in [PhoneStatsStateHolder]. */
class PhoneStatsViewModel(
    source: PhoneReportSource,
    generateInsights: (PhoneInsightInput) -> List<PhoneInsight> = PhoneInsightEngine()::generate,
    workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val holder = PhoneStatsStateHolder(viewModelScope, source, generateInsights, workDispatcher)

    /** Starts as [PhoneReportUiState.Loading]. */
    val report: StateFlow<PhoneReportUiState> get() = holder.report

    /** Starts as [PhoneInsightsUiState.Loading]. */
    val insights: StateFlow<PhoneInsightsUiState> get() = holder.insights

    fun loadReport(window: String, weekMode: String, weekStartDay: Int) = holder.loadReport(window, weekMode, weekStartDay)

    fun loadInsights() = holder.loadInsights()

    fun refresh() = holder.refresh()

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PhoneStatsViewModel(AppModule.phoneBehaviourRepository) as T

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                PhoneStatsViewModel(AppModule.phoneBehaviourRepository) as T
        }
    }
}
