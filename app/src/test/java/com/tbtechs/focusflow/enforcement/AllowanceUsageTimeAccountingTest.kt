package com.tbtechs.focusflow.enforcement

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowanceUsageTimeAccountingTest {
    @Test
    fun splitAcrossMidnightKeepsEachDaySliceAndTotal() {
        val zone = ZoneId.of("UTC")
        val start = LocalDate.parse("2026-10-08").atTime(23, 50)
            .atZone(zone).toInstant().toEpochMilli()
        val end = LocalDate.parse("2026-10-09").atTime(0, 20)
            .atZone(zone).toInstant().toEpochMilli()

        val slices = AllowanceUsageTimeAccounting.splitAtLocalMidnight(start, end, zone)

        assertEquals(listOf("2026-10-08", "2026-10-09"), slices.map { it.localDate })
        assertEquals(listOf(10 * 60_000L, 20 * 60_000L), slices.map { it.durationMs })
        assertEquals(30 * 60_000L, slices.sumOf { it.durationMs })
    }

    @Test
    fun localDaySlicesRespectSpringAndAutumnDstLengths() {
        val zone = ZoneId.of("America/New_York")
        val springStart = LocalDate.parse("2026-03-08").atStartOfDay(zone)
            .toInstant().toEpochMilli()
        val springEnd = LocalDate.parse("2026-03-09").atStartOfDay(zone)
            .toInstant().toEpochMilli()
        val autumnStart = LocalDate.parse("2026-11-01").atStartOfDay(zone)
            .toInstant().toEpochMilli()
        val autumnEnd = LocalDate.parse("2026-11-02").atStartOfDay(zone)
            .toInstant().toEpochMilli()

        val spring = AllowanceUsageTimeAccounting.splitAtLocalMidnight(
            springStart,
            springEnd,
            zone,
        )
        val autumn = AllowanceUsageTimeAccounting.splitAtLocalMidnight(
            autumnStart,
            autumnEnd,
            zone,
        )

        assertEquals(23 * 60 * 60_000L, spring.single().durationMs)
        assertEquals(25 * 60 * 60_000L, autumn.single().durationMs)
    }

    @Test
    fun estimateEndIsCappedFromTheContinuousSessionStart() {
        val startedAt = 1_000_000L
        val fourHours = AllowanceLedger.MAX_ESTIMATED_SEGMENT_MS

        assertEquals(
            startedAt + fourHours,
            AllowanceUsageTimeAccounting.cappedEnd(startedAt, startedAt + 6 * 60 * 60_000L),
        )
        assertEquals(
            startedAt + 2 * 60 * 60_000L,
            AllowanceUsageTimeAccounting.cappedEnd(startedAt, startedAt + 2 * 60 * 60_000L),
        )
    }

    @Test
    fun restartRecoveryNeverExtendsPastTheCheckpointLimit() {
        val checkpointAt = 1_000_000L
        val maxRecovery = AllowanceUsageCoordinator.CHECKPOINT_INTERVAL_MS * 2

        assertEquals(
            checkpointAt + maxRecovery,
            AllowanceUsageTimeAccounting.recoverableEnd(
                checkpointAt,
                checkpointAt + 60_000L,
                maxRecovery,
            ),
        )
        assertEquals(
            checkpointAt + 10_000L,
            AllowanceUsageTimeAccounting.recoverableEnd(
                checkpointAt,
                checkpointAt + 10_000L,
                maxRecovery,
            ),
        )
        assertTrue(
            AllowanceUsageTimeAccounting.splitAtLocalMidnight(
                checkpointAt,
                AllowanceUsageTimeAccounting.recoverableEnd(
                    checkpointAt,
                    checkpointAt - 1,
                    maxRecovery,
                ),
                ZoneId.of("UTC"),
            ).isEmpty(),
        )
    }

    @Test
    fun absentOrInvalidCheckpointCannotInventRecoveryTime() {
        assertEquals(
            0L,
            AllowanceUsageTimeAccounting.recoverableEnd(
                checkpointAtMs = 0L,
                nowMs = 100_000L,
                maximumRecoveryMs = AllowanceUsageCoordinator.MAX_RECOVERABLE_CHECKPOINT_GAP_MS,
            ),
        )
        assertEquals(
            1_000L,
            AllowanceUsageTimeAccounting.recoverableEnd(
                checkpointAtMs = 1_000L,
                nowMs = 900L,
                maximumRecoveryMs = AllowanceUsageCoordinator.MAX_RECOVERABLE_CHECKPOINT_GAP_MS,
            ),
        )
    }
}
