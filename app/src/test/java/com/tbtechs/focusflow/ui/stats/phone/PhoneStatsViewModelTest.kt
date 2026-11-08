package com.tbtechs.focusflow.ui.stats.phone

import androidx.lifecycle.ViewModelStore
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import com.tbtechs.focusflow.analytics.WEEK_MODE_DYNAMIC
import com.tbtechs.focusflow.analytics.phone.PhoneReportResult
import com.tbtechs.focusflow.analytics.phone.PhoneReportSource
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightInputResult
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Thin wiring tests for the ViewModel. The loading rules themselves are covered, with plain coroutines,
 * by `PhoneStatsStateHolderTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneStatsViewModelTest {
    private var store: ViewModelStore? = null

    @After
    fun tearDown() {
        store?.clear()
        store = null
        Dispatchers.resetMain()
    }

    private class FakeSource : PhoneReportSource {
        var reportCalls = 0
        var insightCalls = 0
        override suspend fun loadReport(window: String, weekMode: String, weekStartDay: Int): PhoneReportResult {
            reportCalls++
            return PhoneReportResult.NoData
        }
        override suspend fun loadInsightInput(): PhoneInsightInputResult {
            insightCalls++
            return PhoneInsightInputResult.Building(2)
        }
    }

    private fun createViewModel(scheduler: TestCoroutineScheduler, source: FakeSource): PhoneStatsViewModel {
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        val viewModel = PhoneStatsViewModel(source, { emptyList() }, dispatcher)
        store = ViewModelStore().apply { put("phone", viewModel) }
        return viewModel
    }

    @Test
    fun startsLoadingAndPublishesTheReport() = runTest {
        val source = FakeSource()
        val viewModel = createViewModel(testScheduler, source)
        assertSame(PhoneReportUiState.Loading, viewModel.report.value)
        assertSame(PhoneInsightsUiState.Loading, viewModel.insights.value)

        viewModel.loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1)
        runCurrent()

        assertSame(PhoneReportUiState.NoData, viewModel.report.value)
        assertEquals(1, source.reportCalls)
    }

    @Test
    fun insightsAndRefreshGoThroughTheSource() = runTest {
        val source = FakeSource()
        val viewModel = createViewModel(testScheduler, source)

        viewModel.loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1)
        viewModel.loadInsights()
        runCurrent()
        assertEquals(PhoneInsightsUiState.Loaded(PhoneInsightsResult.Building(2)), viewModel.insights.value)

        viewModel.refresh()
        runCurrent()
        assertEquals(2, source.reportCalls)
        assertEquals(2, source.insightCalls)
    }
}
