package com.tbtechs.focusflow.analytics

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OnDemandDetectorUsageReaderTest {
    private val zone = ZoneId.of("UTC")
    private val dayOne = LocalDate.parse("2026-10-06")
    private val dayTwo = dayOne.plusDays(1)
    private val today = dayTwo.plusDays(1)
    private val nowMs = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun completeOnDemandDaysPreserveMidnightSessionIdentityAndClippedTime() = runBlocking {
        val startMs = dayOne.atTime(23, 50).atZone(zone).toInstant().toEpochMilli()
        val endMs = dayTwo.atTime(0, 20).atZone(zone).toInstant().toEpochMilli()
        val reader = reader(
            UsageEventRead.Available(
                events = listOf(
                    ForegroundUsageEvent(
                        ForegroundEventType.ACTIVITY_RESUMED,
                        startMs,
                        "com.example.app",
                        "MainActivity",
                    ),
                    ForegroundUsageEvent(
                        ForegroundEventType.ACTIVITY_PAUSED,
                        endMs,
                        "com.example.app",
                        "MainActivity",
                    ),
                ),
                earliestEventAtMs = dayOne.atStartOfDay(zone).toInstant().toEpochMilli() - 1,
            ),
        )

        val result = reader.read(dayOne.toString(), dayTwo.toString(), nowMs)

        assertNotNull(result)
        assertEquals(setOf(dayOne.toString(), dayTwo.toString()), result!!.completeDates)
        val rows = result.appDays.associateBy { it.date }
        assertEquals(10L * 60_000L, rows.getValue(dayOne.toString()).foregroundMs)
        assertEquals(20L * 60_000L, rows.getValue(dayTwo.toString()).foregroundMs)
        assertEquals(1, rows.getValue(dayOne.toString()).launchCount)
        assertEquals(0, rows.getValue(dayTwo.toString()).launchCount)
        assertEquals(1, result.sessions.size)
        assertEquals(dayOne.toString(), result.sessions.single().localDate)
        assertEquals(30L * 60_000L, result.sessions.single().durationMs)
    }

    @Test
    fun incompleteRetentionCoverageOmitsOnlyTruncatedDate() = runBlocking {
        val reader = reader(
            UsageEventRead.Available(
                events = emptyList(),
                earliestEventAtMs = dayOne.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            ),
        )

        val result = reader.read(dayOne.toString(), dayTwo.toString(), nowMs)

        assertNotNull(result)
        assertEquals(setOf(dayTwo.toString()), result!!.completeDates)
        assertEquals(emptyList<com.tbtechs.focusflow.data.local.dao.AppUsageRangeRow>(), result.appDays)
        assertEquals(emptyList<com.tbtechs.focusflow.data.local.entity.AppSessionEntity>(), result.sessions)
    }

    @Test
    fun unknownEventReadReturnsNoOnDemandHistory() = runBlocking {
        val result = reader(
            UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.ACCESS_REVOKED),
        ).read(dayOne.toString(), dayTwo.toString(), nowMs)

        assertNull(result)
    }

    private fun reader(read: UsageEventRead) = OnDemandDetectorUsageReader(
        eventSource = UsageEventsSource { _, _ -> read },
        packageName = "com.focusflow",
        resolveAppName = { it },
        resolveCategory = { "social" },
        zoneId = { zone },
    )
}
