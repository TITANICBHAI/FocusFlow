package com.tbtechs.focusflow.analytics

import com.tbtechs.focusflow.data.local.dao.AppUsageRangeRow
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.UsagePipelineStateEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupAppDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageHistoryRepositoryOnDemandTest {
    private val today = "2026-10-08"
    private val dayOne = "2026-10-06"
    private val dayTwo = "2026-10-07"

    @Test
    fun detectorHistoryUsesCompleteRollupsAndFallsBackForMissingOrPartialDates() = runBlocking {
        val store = TestStore(
            cutoverDate = dayOne,
            rollupDays = listOf(
                rollupDay(dayOne, UsageRollupDayEntity.COMPLETE),
                rollupDay(dayTwo, UsageRollupDayEntity.PARTIAL),
            ),
            rollupAppDays = listOf(rollupAppDay(dayOne), rollupAppDay(dayTwo)),
            rollupSessions = listOf(rollupSession(dayOne), rollupSession(dayTwo)),
            onDemand = OnDemandDetectorUsage(
                completeDates = setOf(dayTwo),
                appDays = listOf(appDay(dayTwo, "event-app")),
                sessions = listOf(session(dayTwo, "event-app")),
            ),
        )

        val result = UsageHistoryRepository(store).detectorHistory(dayOne, dayTwo, today)

        assertEquals(0, store.legacyReadCount)
        assertEquals(1, store.onDemandReadCount)
        assertEquals(
            listOf(dayOne to "rollup-app", dayTwo to "event-app"),
            result!!.appDays.map { it.date to it.appName },
        )
        assertEquals(
            listOf(dayOne to "rollup-app", dayTwo to "event-app"),
            result.sessions.map { it.localDate to it.appName },
        )
    }

    @Test
    fun legacyDetectorReadClampsBothUsageTablesToCompletedDays() = runBlocking {
        val store = TestStore(
            cutoverDate = null,
            legacyAppDays = listOf(
                historyAppDay(dayTwo, "yesterday"),
                historyAppDay(today, "today"),
            ),
            legacySessions = listOf(session(dayTwo, "yesterday"), session(today, "today")),
        )

        val result = UsageHistoryRepository(store).detectorHistory(dayTwo, today, today)

        assertEquals(listOf(dayTwo), result!!.appDays.map { it.date })
        assertEquals(listOf(dayTwo), result.sessions.map { it.localDate })
    }

    private class TestStore(
        cutoverDate: String?,
        private val legacyAppDays: List<UsageHistoryAppDay> = emptyList(),
        private val legacySessions: List<AppSessionEntity> = emptyList(),
        private val rollupDays: List<UsageRollupDayEntity> = emptyList(),
        private val rollupAppDays: List<UsageRollupAppDayEntity> = emptyList(),
        private val rollupSessions: List<UsageRollupSessionEntity> = emptyList(),
        private val onDemand: OnDemandDetectorUsage? = null,
    ) : UsageHistoryStore {
        private val state = UsagePipelineStateEntity(
            cutoverDate = cutoverDate,
            pipelineVersion = UsagePipelineStateEntity.CURRENT_PIPELINE_VERSION,
            shadowStartedOn = "2026-09-01",
        )
        var legacyReadCount = 0
        var onDemandReadCount = 0

        override suspend fun legacyAppDays(
            startDate: String,
            endDate: String,
        ): List<UsageHistoryAppDay> {
            legacyReadCount++
            return legacyAppDays.filter { it.date in startDate..endDate }
        }

        override suspend fun legacySessions(
            startDate: String,
            endDate: String,
        ): List<AppSessionEntity> {
            legacyReadCount++
            return legacySessions.filter { it.localDate in startDate..endDate }
        }

        override suspend fun pipelineState(): UsagePipelineStateEntity = state

        override suspend fun rollupDays(
            startDate: String,
            endDate: String,
        ): List<UsageRollupDayEntity> = rollupDays.filter { it.date in startDate..endDate }

        override suspend fun rollupAppDays(
            startDate: String,
            endDate: String,
        ): List<UsageRollupAppDayEntity> =
            rollupAppDays.filter { it.date in startDate..endDate }

        override suspend fun rollupSessions(
            startDate: String,
            endDate: String,
        ): List<UsageRollupSessionEntity> =
            rollupSessions.filter { it.localDate in startDate..endDate }

        override suspend fun onDemandDetectorUsage(
            startDate: String,
            endDate: String,
        ): OnDemandDetectorUsage? {
            onDemandReadCount++
            return onDemand
        }
    }

    private fun rollupDay(date: String, status: String) = UsageRollupDayEntity(
        date = date,
        status = status,
        coverageStartMs = null,
        coverageEndMs = null,
        pipelineVersion = UsagePipelineStateEntity.CURRENT_PIPELINE_VERSION,
        computedAtMs = 1L,
        totalForegroundMs = 1L,
    )

    private fun rollupAppDay(date: String) = UsageRollupAppDayEntity(
        date = date,
        packageName = "com.example.$date",
        appName = "rollup-app",
        category = "social",
        foregroundMs = 60_000L,
        hourlyMs = "",
        launchCount = 1,
        sessionCount = 1,
        firstStartAtMs = 1L,
        lastUsedAtMs = 2L,
    )

    private fun rollupSession(date: String) = UsageRollupSessionEntity(
        packageName = "com.example.$date",
        startedAtMs = 1L,
        endedAtMs = 2L,
        durationMs = 1L,
        localDate = date,
    )

    private fun appDay(date: String, name: String) = AppUsageRangeRow(
        packageName = "com.example.$date",
        appName = name,
        category = "social",
        date = date,
        foregroundMs = 60_000L,
        hourlyMs = "",
        launchCount = 1,
        lastUsedAt = 2L,
    )

    private fun historyAppDay(date: String, name: String) = UsageHistoryAppDay(
        date = date,
        packageName = "com.example.$date",
        appName = name,
        category = "social",
        foregroundMs = 60_000L,
        hourlyMs = "",
        launchCount = 1,
        lastUsedAtMs = 2L,
    )

    private fun session(date: String, name: String) = AppSessionEntity(
        packageName = "com.example.$date",
        appName = name,
        startedAt = 1L,
        endedAt = 2L,
        durationMs = 1L,
        localDate = date,
    )
}
