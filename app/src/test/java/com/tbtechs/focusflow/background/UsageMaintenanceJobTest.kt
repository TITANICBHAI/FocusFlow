package com.tbtechs.focusflow.background

import com.tbtechs.focusflow.analytics.UsageEventRead
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageMaintenanceJobTest {

    @Test
    fun runsRollupThenCompletedDayCheckThenDetectionThenPrune() = runBlocking {
        val operations = FakeOperations()
        val state = FakeState()

        val result = job(operations, state).run()

        assertEquals(UsageMaintenanceRunResult.SUCCESS, result)
        assertEquals(
            listOf("rollup", "completed-day-check", "detection", "prune"),
            operations.events,
        )
    }

    @Test
    fun unknownUsageReadRetriesWithoutDetectionAndStillPrunes() = runBlocking {
        val operations = FakeOperations(
            rollupResult = MaintenanceRollupResult.Unknown(
                UsageEventRead.Unknown.Reason.EVENTS_UNAVAILABLE,
            ),
        )
        val state = FakeState()

        val result = job(operations, state).run()

        assertEquals(UsageMaintenanceRunResult.RETRY, result)
        assertEquals(listOf("rollup", "prune"), operations.events)
        assertNull(state.detectionDate)
    }

    @Test
    fun permissionDeniedAllowsOneDetectionAndAdvancesMarker() = runBlocking {
        val operations = FakeOperations(
            rollupResult = MaintenanceRollupResult.Unknown(
                UsageEventRead.Unknown.Reason.PERMISSION_MISSING,
            ),
        )
        val state = FakeState()

        val result = job(operations, state).run()

        assertEquals(UsageMaintenanceRunResult.SUCCESS, result)
        assertEquals(listOf("rollup", "detection", "prune"), operations.events)
        assertEquals(LocalDate.parse("2026-10-11"), state.detectionDate)
    }

    @Test
    fun incompleteRequiredDaysRetryWithoutAdvancingDetectionMarker() = runBlocking {
        val operations = FakeOperations(requiredDaysComplete = false)
        val state = FakeState()

        val result = job(operations, state).run()

        assertEquals(UsageMaintenanceRunResult.RETRY, result)
        assertEquals(listOf("rollup", "completed-day-check", "prune"), operations.events)
        assertNull(state.detectionDate)
    }

    @Test
    fun detectionRunsOnlyOncePerLocalCalendarDay() = runBlocking {
        val operations = FakeOperations()
        val state = FakeState()
        val maintenance = job(operations, state)

        assertEquals(UsageMaintenanceRunResult.SUCCESS, maintenance.run())
        assertEquals(UsageMaintenanceRunResult.SUCCESS, maintenance.run())

        assertEquals(1, operations.events.count { it == "detection" })
        assertEquals(1, operations.events.count { it == "prune" })
    }

    @Test
    fun pruneHorizonUsesInjectedInstantAndLocalDate() = runBlocking {
        val operations = FakeOperations()
        val clock = Clock.fixed(
            Instant.parse("2026-10-10T20:00:00Z"),
            ZoneOffset.UTC,
        )
        val zone = ZoneId.of("Asia/Kolkata")

        job(operations, FakeState(), clock, zone).run()

        assertEquals(LocalDate.parse("2026-10-11").minusDays(90), operations.pruneDate)
        assertEquals(
            clock.instant().minus(90, ChronoUnit.DAYS),
            operations.pruneInstant,
        )
    }

    @Test
    fun startupScheduleUsesUniqueFifteenMinuteKeepRequest() {
        var enqueued: UsageMaintenanceSchedule? = null
        UsageMaintenanceStartupScheduler { enqueued = it }.schedule()

        assertEquals(
            UsageMaintenanceSchedule(
                workName = "FOCUSDAY_USAGE_MAINTENANCE",
                intervalMinutes = 15L,
                existingWorkPolicy = MaintenanceExistingWorkPolicy.KEEP,
            ),
            enqueued,
        )
    }

    private fun job(
        operations: FakeOperations,
        state: FakeState,
        clock: Clock = Clock.fixed(
            Instant.parse("2026-10-10T20:00:00Z"),
            ZoneOffset.UTC,
        ),
        zoneId: ZoneId = ZoneId.of("Asia/Kolkata"),
    ) = UsageMaintenanceJob(operations, state, clock, zoneId)

    private class FakeOperations(
        var rollupResult: MaintenanceRollupResult = MaintenanceRollupResult.Written,
        var requiredDaysComplete: Boolean = true,
    ) : UsageMaintenanceOperations {
        val events = mutableListOf<String>()
        var pruneDate: LocalDate? = null
        var pruneInstant: Instant? = null

        override suspend fun writeRecentRollups(nowMs: Long): MaintenanceRollupResult {
            events += "rollup"
            return rollupResult
        }

        override suspend fun areRequiredDaysComplete(
            today: LocalDate,
            zoneId: ZoneId,
        ): Boolean {
            events += "completed-day-check"
            return requiredDaysComplete
        }

        override suspend fun runDetection(today: LocalDate, zoneId: ZoneId) {
            events += "detection"
        }

        override suspend fun prune(cutoffDate: LocalDate, cutoffInstant: Instant) {
            events += "prune"
            pruneDate = cutoffDate
            pruneInstant = cutoffInstant
        }
    }

    private class FakeState : UsageMaintenanceState {
        var detectionDate: LocalDate? = null
        var pruneDate: LocalDate? = null

        override suspend fun lastDetectionRunDate(): LocalDate? = detectionDate

        override suspend fun markDetectionRun(date: LocalDate) {
            detectionDate = date
        }

        override suspend fun lastPruneDate(): LocalDate? = pruneDate

        override suspend fun markPruned(date: LocalDate) {
            pruneDate = date
        }
    }
}
