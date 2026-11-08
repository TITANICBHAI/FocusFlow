package com.tbtechs.focusflow.ui.stats.phone

import com.tbtechs.focusflow.analytics.ANALYTICS_TODAY
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import com.tbtechs.focusflow.analytics.WEEK_MODE_DYNAMIC
import com.tbtechs.focusflow.analytics.WEEK_MODE_FIXED
import com.tbtechs.focusflow.analytics.phone.PhoneDay
import com.tbtechs.focusflow.analytics.phone.PhoneDayStatus
import com.tbtechs.focusflow.analytics.phone.PhoneReport
import com.tbtechs.focusflow.analytics.phone.PhoneReportResult
import com.tbtechs.focusflow.analytics.phone.PhoneReportSource
import com.tbtechs.focusflow.analytics.phone.UsageCategory
import com.tbtechs.focusflow.analytics.phone.insights.InsightConfidence
import com.tbtechs.focusflow.analytics.phone.insights.InsightGroup
import com.tbtechs.focusflow.analytics.phone.insights.InsightKind
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsight
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightInput
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightInputResult
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightsResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext

/**
 * Tests the loading logic behind [PhoneStatsViewModel] with plain coroutines: every coroutine runs on a
 * dispatcher the test advances by hand, and the fake source completes calls only when told to.
 */
class PhoneStatsStateHolderTest {
    private class ManualDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.addLast(block) }
        fun runAll() { while (queue.isNotEmpty()) queue.removeFirst().run() }
    }

    private class FakeSource : PhoneReportSource {
        class Call(val window: String, val mode: String, val weekStart: Int, val result: CompletableDeferred<PhoneReportResult>)
        val reportCalls = mutableListOf<Call>()
        val insightCalls = mutableListOf<CompletableDeferred<PhoneInsightInputResult>>()
        override suspend fun loadReport(window: String, weekMode: String, weekStartDay: Int): PhoneReportResult {
            val call = Call(window, weekMode, weekStartDay, CompletableDeferred())
            reportCalls += call
            return call.result.await()
        }
        override suspend fun loadInsightInput(): PhoneInsightInputResult {
            val d = CompletableDeferred<PhoneInsightInputResult>()
            insightCalls += d
            return d.await()
        }
    }

    private class Setup(generate: ((PhoneInsightInput) -> List<PhoneInsight>)? = null) {
        val dispatcher = ManualDispatcher()
        val source = FakeSource()
        val generated = mutableListOf<PhoneInsightInput>()
        val holder = PhoneStatsStateHolder(
            scope = CoroutineScope(SupervisorJob() + dispatcher),
            source = source,
            generateInsights = generate ?: { input -> generated += input; listOf(insight("a")) },
            workDispatcher = dispatcher,
        )
        fun settle() = dispatcher.runAll()
    }

    companion object {
        private val today = LocalDate.of(2026, 11, 6)
        fun insight(id: String) = PhoneInsight(
            id = id, kind = InsightKind.RECORD, group = InsightGroup.RECORD, title = "t", message = "m",
            evidence = emptyList(), direction = null, confidence = InsightConfidence.MEDIUM, priority = 1,
        )
        private fun day(date: LocalDate, status: PhoneDayStatus) = PhoneDay.empty(date.toString(), status)
        fun input(complete: Int, other: Int = 0) = PhoneInsightInput(
            today = today,
            days = (1..complete).map { day(today.minusDays(it.toLong()), PhoneDayStatus.COMPLETE) } +
                (1..other).map { day(today.minusDays((100 + it).toLong()), PhoneDayStatus.PARTIAL) } +
                day(today, PhoneDayStatus.LIVE),
            appDays = emptyList(), appSessions = emptyList(), blockedDays = emptyList(), focusSessions = emptyList(),
            appNames = emptyMap(), categoryOf = { UsageCategory.OTHER }, zone = ZoneId.of("UTC"),
        )
    }

    private val ready: PhoneReportResult.Ready by lazy {
        // Any real report will do; build one through the repository-free path used by other tests.
        PhoneReportResult.Ready(com.tbtechs.focusflow.analytics.phone.PhoneReportFixtures.emptyReport())
    }

    // 1
    @Test
    fun bothStatesStartAsLoading() {
        val s = Setup()
        assertSame(PhoneReportUiState.Loading, s.holder.report.value)
        assertSame(PhoneInsightsUiState.Loading, s.holder.insights.value)
    }

    // 2
    @Test
    fun resultsMapToUiStates() {
        fun stateFor(result: PhoneReportResult): PhoneReportUiState {
            val s = Setup()
            s.holder.loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1)
            s.settle()
            s.source.reportCalls.single().result.complete(result)
            s.settle()
            return s.holder.report.value
        }
        assertEquals(PhoneReportUiState.Ready(ready.report), stateFor(ready))
        assertSame(PhoneReportUiState.NoData, stateFor(PhoneReportResult.NoData))
        assertSame(PhoneReportUiState.PermissionNeeded, stateFor(PhoneReportResult.PermissionNeeded))
        assertEquals(PhoneReportUiState.Failed("boom"), stateFor(PhoneReportResult.Failed("boom")))
    }

    @Test
    fun theRequestIsPassedToTheSource() {
        val s = Setup()
        s.holder.loadReport(ANALYTICS_TODAY, WEEK_MODE_FIXED, 0)
        s.settle()
        val call = s.source.reportCalls.single()
        assertEquals(Triple(ANALYTICS_TODAY, WEEK_MODE_FIXED, 0), Triple(call.window, call.mode, call.weekStart))
    }

    @Test
    fun aSourceThatThrowsBecomesFailed() {
        val s = Setup()
        val throwing = object : PhoneReportSource {
            override suspend fun loadReport(window: String, weekMode: String, weekStartDay: Int): PhoneReportResult = error("kaput")
            override suspend fun loadInsightInput(): PhoneInsightInputResult = error("nope")
        }
        val holder = PhoneStatsStateHolder(CoroutineScope(SupervisorJob() + s.dispatcher), throwing, { emptyList() }, s.dispatcher)
        holder.loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1)
        holder.loadInsights()
        s.settle()
        assertEquals(PhoneReportUiState.Failed("kaput"), holder.report.value)
        assertEquals(PhoneInsightsUiState.Loaded(PhoneInsightsResult.Failed("nope")), holder.insights.value)
    }

    // 3
    @Test
    fun repeatingTheSameRequestKeepsTheCurrentStateWhileLoading() {
        val s = Setup()
        s.holder.loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1)
        s.settle()
        s.source.reportCalls[0].result.complete(ready)
        s.settle()
        val seen = mutableListOf<PhoneReportUiState>()
        s.holder.loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1)
        seen += s.holder.report.value
        s.settle()
        seen += s.holder.report.value
        assertTrue(seen.all { it is PhoneReportUiState.Ready })
        s.source.reportCalls[1].result.complete(PhoneReportResult.NoData)
        s.settle()
        assertSame(PhoneReportUiState.NoData, s.holder.report.value)
    }

    @Test
    fun aDifferentRequestShowsLoadingFirst() {
        val s = Setup()
        s.holder.loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1)
        s.settle()
        s.source.reportCalls[0].result.complete(ready)
        s.settle()
        s.holder.loadReport(ANALYTICS_TODAY, WEEK_MODE_DYNAMIC, 1)
        assertSame(PhoneReportUiState.Loading, s.holder.report.value)
        s.settle()
        assertSame(PhoneReportUiState.Loading, s.holder.report.value)
        s.source.reportCalls[1].result.complete(PhoneReportResult.NoData)
        s.settle()
        assertSame(PhoneReportUiState.NoData, s.holder.report.value)
    }

    @Test
    fun aChangedWeekModeOrStartDayCountsAsADifferentRequest() {
        val s = Setup()
        s.holder.loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1)
        s.settle(); s.source.reportCalls[0].result.complete(ready); s.settle()
        s.holder.loadReport(ANALYTICS_WEEK, WEEK_MODE_FIXED, 1)
        assertSame(PhoneReportUiState.Loading, s.holder.report.value)
        s.settle(); s.source.reportCalls[1].result.complete(ready); s.settle()
        s.holder.loadReport(ANALYTICS_WEEK, WEEK_MODE_FIXED, 0)
        assertSame(PhoneReportUiState.Loading, s.holder.report.value)
    }

    @Test
    fun anUnfinishedFirstLoadIsStillTreatedAsNew() {
        val s = Setup()
        s.holder.loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1)
        s.settle()
        s.holder.loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1) // nothing has loaded yet
        assertSame(PhoneReportUiState.Loading, s.holder.report.value)
    }

    @Test
    fun afterAFailureTheSameRequestIsNotShownAsLoadingAgain() {
        val s = Setup()
        s.holder.loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1)
        s.settle(); s.source.reportCalls[0].result.complete(PhoneReportResult.Failed("x")); s.settle()
        s.holder.loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1)
        assertEquals(PhoneReportUiState.Failed("x"), s.holder.report.value)
    }

    // 4
    @Test
    fun aSlowEarlierRequestNeverOverwritesALaterOne() {
        val s = Setup()
        s.holder.loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1) // A: slow
        s.settle()
        s.holder.loadReport(ANALYTICS_TODAY, WEEK_MODE_DYNAMIC, 1) // B: finishes first
        s.settle()
        val (a, b) = s.source.reportCalls
        b.result.complete(PhoneReportResult.NoData)
        s.settle()
        assertSame(PhoneReportUiState.NoData, s.holder.report.value)
        a.result.complete(PhoneReportResult.PermissionNeeded)
        s.settle()
        assertSame(PhoneReportUiState.NoData, s.holder.report.value)
    }

    // 5
    @Test
    fun insightsBuildingPermissionAndFailedMapThrough() {
        fun stateFor(result: PhoneInsightInputResult): PhoneInsightsUiState {
            val s = Setup()
            s.holder.loadInsights(); s.settle()
            s.source.insightCalls.single().complete(result); s.settle()
            return s.holder.insights.value
        }
        assertEquals(PhoneInsightsUiState.Loaded(PhoneInsightsResult.Building(2)), stateFor(PhoneInsightInputResult.Building(2)))
        assertEquals(PhoneInsightsUiState.Loaded(PhoneInsightsResult.PermissionNeeded), stateFor(PhoneInsightInputResult.PermissionNeeded))
        assertEquals(PhoneInsightsUiState.Loaded(PhoneInsightsResult.Failed("bad")), stateFor(PhoneInsightInputResult.Failed("bad")))
    }

    @Test
    fun readyInputRunsTheEngineAndCountsCompleteDays() {
        val s = Setup()
        val data = input(complete = 5, other = 2)
        s.holder.loadInsights(); s.settle()
        s.source.insightCalls.single().complete(PhoneInsightInputResult.Ready(data)); s.settle()
        assertEquals(listOf(data), s.generated)
        assertEquals(
            PhoneInsightsUiState.Loaded(PhoneInsightsResult.Ready(listOf(insight("a")), completeDays = 5)),
            s.holder.insights.value,
        )
    }

    @Test
    fun anExceptionInTheEngineBecomesFailed() {
        val s = Setup(generate = { error("engine broke") })
        s.holder.loadInsights(); s.settle()
        s.source.insightCalls.single().complete(PhoneInsightInputResult.Ready(input(5))); s.settle()
        assertEquals(PhoneInsightsUiState.Loaded(PhoneInsightsResult.Failed("engine broke")), s.holder.insights.value)
    }

    @Test
    fun aCancellationInsideTheEngineIsNotTurnedIntoAFailure() {
        val s = Setup(generate = { throw CancellationException("cancelled") })
        s.holder.loadInsights(); s.settle()
        s.source.insightCalls.single().complete(PhoneInsightInputResult.Ready(input(5))); s.settle()
        assertSame(PhoneInsightsUiState.Loading, s.holder.insights.value)
    }

    @Test
    fun aSlowEarlierInsightsLoadNeverOverwritesALaterOne() {
        val s = Setup()
        s.holder.loadInsights(); s.settle()
        s.holder.loadInsights(); s.settle()
        val (first, second) = s.source.insightCalls
        second.complete(PhoneInsightInputResult.Building(1)); s.settle()
        first.complete(PhoneInsightInputResult.Building(9)); s.settle()
        assertEquals(PhoneInsightsUiState.Loaded(PhoneInsightsResult.Building(1)), s.holder.insights.value)
    }

    @Test
    fun reloadingInsightsKeepsTheLoadedResultMeanwhile() {
        val s = Setup()
        s.holder.loadInsights(); s.settle()
        s.source.insightCalls[0].complete(PhoneInsightInputResult.Building(1)); s.settle()
        s.holder.loadInsights(); s.settle()
        assertEquals(PhoneInsightsUiState.Loaded(PhoneInsightsResult.Building(1)), s.holder.insights.value)
    }

    // 6
    @Test
    fun refreshReissuesTheLastReportRequest() {
        val s = Setup()
        s.holder.loadReport(ANALYTICS_TODAY, WEEK_MODE_FIXED, 0); s.settle()
        s.source.reportCalls[0].result.complete(PhoneReportResult.NoData); s.settle()
        s.holder.refresh(); s.settle()
        assertEquals(2, s.source.reportCalls.size)
        val second = s.source.reportCalls[1]
        assertEquals(Triple(ANALYTICS_TODAY, WEEK_MODE_FIXED, 0), Triple(second.window, second.mode, second.weekStart))
        assertSame(PhoneReportUiState.NoData, s.holder.report.value) // no flicker
        assertEquals(0, s.source.insightCalls.size)               // insights were never requested
    }

    @Test
    fun refreshAlsoReloadsInsightsOnceTheyWereRequested() {
        val s = Setup()
        s.holder.loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1); s.holder.loadInsights(); s.settle()
        s.holder.refresh(); s.settle()
        assertEquals(2, s.source.reportCalls.size)
        assertEquals(2, s.source.insightCalls.size)
    }

    @Test
    fun refreshBeforeAnyRequestDoesNothing() {
        val s = Setup()
        s.holder.refresh(); s.settle()
        assertEquals(0, s.source.reportCalls.size)
        assertEquals(0, s.source.insightCalls.size)
    }

    @Test
    fun refreshInsightsOnlyWhenNoReportWasRequested() {
        val s = Setup()
        s.holder.loadInsights(); s.settle()
        s.holder.refresh(); s.settle()
        assertEquals(0, s.source.reportCalls.size)
        assertEquals(2, s.source.insightCalls.size)
    }
}
