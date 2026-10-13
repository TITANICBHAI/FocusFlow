package com.tbtechs.focusflow.analytics

import com.tbtechs.focusflow.analytics.detection.UsageDetectorWindow
import com.tbtechs.focusflow.analytics.detection.UsageDetectorWindows
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.UsagePipelineStateEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupAppDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupSessionEntity
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import com.tbtechs.focusflow.analytics.detection.detectSubstitution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class UsageDetectorSeamTest {
    private val today = LocalDate.parse("2026-10-08")

    @Test
    fun everyUsageDetectorUsesLegacyForAWindowEntirelyBeforeCutover() = runBlocking {
        UsageDetectorWindow.entries.forEach { detector ->
            val range = UsageDetectorWindows.range(detector, today)
            val store = TestHistoryStore(cutoverDate = range.end.plusDays(1).toString())

            assertTrue(
                "Expected legacy history for $detector",
                UsageHistoryRepository(store).detectorHistory(
                    range.start.toString(),
                    range.end.toString(),
                    today.toString(),
                ) != null,
            )
            assertEquals(2, store.legacyReads)
            assertEquals(0, store.rollupReads)
        }
    }

    @Test
    fun substitutionOutputIsUnchangedWhenItsWindowIsEntirelyBeforeCutover() = runBlocking {
        val range = UsageDetectorWindows.range(UsageDetectorWindow.SUBSTITUTION, today)
        val legacyRows = generateSequence(range.start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(range.end) }
            .flatMap { date ->
                val daysAgo = ChronoUnit.DAYS.between(date, today)
                val downMinutes = if (daysAgo < 7) 15 else 60
                listOf(
                    UsageHistoryAppDay(
                        date = date.toString(),
                        packageName = "down",
                        appName = "Down",
                        category = "social",
                        foregroundMs = downMinutes * 60_000L,
                        hourlyMs = "",
                        launchCount = 1,
                        lastUsedAtMs = 0L,
                    ),
                    UsageHistoryAppDay(
                        date = date.toString(),
                        packageName = "up",
                        appName = "Up",
                        category = "entertainment",
                        foregroundMs = (120 - downMinutes) * 60_000L,
                        hourlyMs = "",
                        launchCount = 1,
                        lastUsedAtMs = 0L,
                    ),
                )
            }
            .toList()
        val store = TestHistoryStore(
            cutoverDate = range.end.plusDays(1).toString(),
            legacyAppDayRows = legacyRows,
        )
        val history = UsageHistoryRepository(store).detectorHistory(
            range.start.toString(),
            range.end.toString(),
            today.toString(),
        ) ?: error("Expected the complete pre-cutover legacy window")
        val directLegacyInput = legacyRows.map { row ->
            com.tbtechs.focusflow.data.local.dao.AppUsageRangeRow(
                packageName = row.packageName,
                appName = row.appName,
                category = row.category,
                date = row.date,
                foregroundMs = row.foregroundMs,
                hourlyMs = row.hourlyMs,
                launchCount = row.launchCount,
                lastUsedAt = row.lastUsedAtMs,
            )
        }

        val expected = detectSubstitution(directLegacyInput, today)
        val actual = detectSubstitution(history.appDays, today)
        assertNotNull(expected)
        assertNotNull(actual)
        assertEquals(expected?.detectionType, actual?.detectionType)
        assertEquals(expected?.subjectPackage, actual?.subjectPackage)
        assertEquals(expected?.evidenceJson, actual?.evidenceJson)
    }

    @Test
    fun everyUsageDetectorUsesCompleteRollupsAfterCutover() = runBlocking {
        UsageDetectorWindow.entries.forEach { detector ->
            val range = UsageDetectorWindows.range(detector, today)
            val store = TestHistoryStore(
                cutoverDate = range.start.toString(),
                completeRollups = true,
            )

            assertTrue(
                "Expected complete rollup history for $detector",
                UsageHistoryRepository(store).detectorHistory(
                    range.start.toString(),
                    range.end.toString(),
                    today.toString(),
                ) != null,
            )
            assertEquals(0, store.legacyReads)
            assertEquals(1, store.rollupReads)
        }
    }

    @Test
    fun everyUsageDetectorRefusesWindowsCrossingTheCutoverSeam() = runBlocking {
        UsageDetectorWindow.entries.forEach { detector ->
            val range = UsageDetectorWindows.range(detector, today)
            val store = TestHistoryStore(
                cutoverDate = range.start.plusDays(1).toString(),
                completeRollups = true,
            )

            assertNull(
                "Expected seam-crossing history to be refused for $detector",
                UsageHistoryRepository(store).detectorHistory(
                    range.start.toString(),
                    range.end.toString(),
                    today.toString(),
                ),
            )
            assertEquals(0, store.legacyReads)
            assertEquals(0, store.rollupReads)
        }
    }

    @Test
    fun everyUsageDetectorWindowEndsYesterdayAndRepositoryClampsToday() = runBlocking {
        UsageDetectorWindow.entries.forEach { detector ->
            val range = UsageDetectorWindows.range(detector, today)
            assertEquals(today.minusDays(1), range.end)
            assertEquals(
                detector.days,
                ChronoUnit.DAYS.between(range.start, range.end) + 1,
            )
            assertFalse(range.end == today)

            val store = TestHistoryStore(
                cutoverDate = range.start.toString(),
                completeRollups = true,
            )
            val result = UsageHistoryRepository(store).detectorHistory(
                range.start.toString(),
                today.toString(),
                today.toString(),
            )
            assertTrue(result != null)
            assertEquals(today.minusDays(1).toString(), store.lastRollupEnd)
        }
    }

    @Test
    fun everyUsageDetectorCountsMidnightSessionOnceOnItsStartDate() = runBlocking {
        UsageDetectorWindow.entries.forEach { detector ->
            val range = UsageDetectorWindows.range(detector, today)
            val startDate = range.end.minusDays(1)
            val nextDate = startDate.plusDays(1)
            val startMs = startDate.atTime(23, 50).toInstant(ZoneOffset.UTC).toEpochMilli()
            val durationMs = 30L * 60L * 1_000L
            val store = TestHistoryStore(
                cutoverDate = range.start.toString(),
                completeRollups = true,
                rollupAppDayRows = listOf(
                    UsageRollupAppDayEntity(
                        date = startDate.toString(),
                        packageName = "com.example.target",
                        appName = "Target",
                        category = "social",
                        foregroundMs = 10L * 60L * 1_000L,
                        hourlyMs = List(24) { if (it == 23) 10L * 60L * 1_000L else 0L }
                            .joinToString(","),
                        launchCount = 1,
                        sessionCount = 1,
                        firstStartAtMs = startMs,
                        lastUsedAtMs = startMs + 10L * 60L * 1_000L,
                    ),
                    UsageRollupAppDayEntity(
                        date = nextDate.toString(),
                        packageName = "com.example.target",
                        appName = "Target",
                        category = "social",
                        foregroundMs = 20L * 60L * 1_000L,
                        hourlyMs = List(24) { if (it == 0) 20L * 60L * 1_000L else 0L }
                            .joinToString(","),
                        launchCount = 0,
                        sessionCount = 0,
                        firstStartAtMs = null,
                        lastUsedAtMs = startMs + durationMs,
                    ),
                ),
                rollupSessionRows = listOf(
                    UsageRollupSessionEntity(
                        packageName = "com.example.target",
                        startedAtMs = startMs,
                        endedAtMs = startMs + durationMs,
                        durationMs = durationMs,
                        localDate = startDate.toString(),
                    ),
                ),
            )

            val history = UsageHistoryRepository(store).detectorHistory(
                range.start.toString(),
                range.end.toString(),
                today.toString(),
            ) ?: error("Expected complete detector history for $detector")

            assertEquals("Expected one session for $detector", 1, history.sessions.size)
            assertEquals(startDate.toString(), history.sessions.single().localDate)
            assertEquals(durationMs, history.sessions.single().durationMs)
            val appDays = history.appDays.associateBy { it.date }
            assertEquals(10L * 60L * 1_000L, appDays.getValue(startDate.toString()).foregroundMs)
            assertEquals(20L * 60L * 1_000L, appDays.getValue(nextDate.toString()).foregroundMs)
            assertEquals(1, appDays.getValue(startDate.toString()).launchCount)
            assertEquals(0, appDays.getValue(nextDate.toString()).launchCount)
        }
    }

    private class TestHistoryStore(
        cutoverDate: String,
        private val completeRollups: Boolean = false,
        private val legacyAppDayRows: List<UsageHistoryAppDay> = emptyList(),
        private val rollupAppDayRows: List<UsageRollupAppDayEntity> = emptyList(),
        private val rollupSessionRows: List<UsageRollupSessionEntity> = emptyList(),
    ) : UsageHistoryStore {
        private val state = UsagePipelineStateEntity(
            cutoverDate = cutoverDate,
            pipelineVersion = UsagePipelineStateEntity.CURRENT_PIPELINE_VERSION,
            shadowStartedOn = "2026-09-01",
        )
        var legacyReads = 0
        var rollupReads = 0
        var lastRollupEnd: String? = null

        override suspend fun legacyAppDays(
            startDate: String,
            endDate: String,
        ): List<UsageHistoryAppDay> {
            legacyReads++
            return legacyAppDayRows.filter { it.date in startDate..endDate }
        }

        override suspend fun legacySessions(
            startDate: String,
            endDate: String,
        ): List<AppSessionEntity> {
            legacyReads++
            return emptyList()
        }

        override suspend fun pipelineState(): UsagePipelineStateEntity = state

        override suspend fun rollupDays(
            startDate: String,
            endDate: String,
        ): List<UsageRollupDayEntity> {
            rollupReads++
            lastRollupEnd = endDate
            if (!completeRollups) return emptyList()
            val first = LocalDate.parse(startDate)
            val end = LocalDate.parse(endDate)
            return generateSequence(first) { it.plusDays(1) }
                .takeWhile { !it.isAfter(end) }
                .map { date ->
                    UsageRollupDayEntity(
                        date = date.toString(),
                        status = UsageRollupDayEntity.COMPLETE,
                        coverageStartMs = null,
                        coverageEndMs = null,
                        pipelineVersion = UsagePipelineStateEntity.CURRENT_PIPELINE_VERSION,
                        computedAtMs = 1L,
                        totalForegroundMs = 0L,
                    )
                }
                .toList()
        }

        override suspend fun rollupAppDays(date: String): List<UsageRollupAppDayEntity> =
            rollupAppDayRows.filter { it.date == date }

        override suspend fun rollupAppDays(
            startDate: String,
            endDate: String,
        ): List<UsageRollupAppDayEntity> =
            rollupAppDayRows.filter { it.date in startDate..endDate }

        override suspend fun rollupSessions(
            startDate: String,
            endDate: String,
        ): List<UsageRollupSessionEntity> =
            rollupSessionRows.filter { it.localDate in startDate..endDate }
    }
}
