package com.tbtechs.focusflow.analytics

import com.tbtechs.focusflow.data.repository.AppUsageInfo
import com.tbtechs.focusflow.data.repository.HourlyUsageSummary
import com.tbtechs.focusflow.data.repository.UsageSummary
import java.time.LocalDate
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalyticsProcessorPhoneUsageTest {
    private val now = ZonedDateTime.parse("2026-10-08T12:00:00Z")

    @Test
    fun phoneUsageCardRemainsAvailableWhenHourlyDataIsMissing() {
        val snapshot = createAnalyticsSnapshot(
            window = ANALYTICS_WEEK,
            range = AnalyticsRange(start = now.minusDays(6), end = now),
            source = source(
                usageSummary = UsageSummary(totalMinutes = 25, apps = emptyList()),
                usageHourly = null,
            ),
            generatedAt = now.toInstant().toString(),
        )

        val phoneUsage = snapshot.phoneUsage ?: error("Expected phone usage from summary data")
        assertEquals(25, phoneUsage.totalMinutes)
        assertEquals(0.0, phoneUsage.byHour.values.sum(), 0.0)
    }

    @Test
    fun phoneUsageCardRemainsAvailableWhenSummaryDataIsMissing() {
        val hourly = HourlyUsageSummary(
            foregroundMillisecondsByHour = List(24) { hour ->
                if (hour == 9) 20L * 60L * 1_000L else 0L
            },
            totalForegroundMilliseconds = 20L * 60L * 1_000L,
        )
        val snapshot = createAnalyticsSnapshot(
            window = ANALYTICS_WEEK,
            range = AnalyticsRange(start = now.minusDays(6), end = now),
            source = source(usageSummary = null, usageHourly = hourly),
            generatedAt = now.toInstant().toString(),
        )

        val phoneUsage = snapshot.phoneUsage ?: error("Expected phone usage from hourly data")
        assertEquals(20, phoneUsage.totalMinutes)
        assertEquals(20.0, phoneUsage.byHour.getValue(9), 0.0)
    }

    @Test
    fun unavailableDeviceUsageDoesNotBecomeAZeroPhoneUsageCard() {
        val snapshot = createAnalyticsSnapshot(
            window = ANALYTICS_TODAY,
            range = AnalyticsRange(start = now.toLocalDate().atStartOfDay(now.zone), end = now),
            source = source(usageSummary = null, usageHourly = null).copy(
                health = AnalyticsSnapshot.SourceHealth(
                    usageSummary = SOURCE_UNAVAILABLE,
                    usageHourly = SOURCE_UNAVAILABLE,
                ),
            ),
            generatedAt = now.toInstant().toString(),
        )

        assertNull(snapshot.phoneUsage)
        assertEquals(SOURCE_UNAVAILABLE, snapshot.sourceHealth?.usageSummary)
    }

    @Test
    fun dynamicWeekObservationSeriesKeepsCalendarDateKeysAcrossMonthBoundary() {
        val weekNow = ZonedDateTime.of(
            LocalDate.of(2026, 10, 3),
            java.time.LocalTime.NOON,
            java.time.ZoneId.of("Asia/Kolkata"),
        )
        val weekRange = getAnalyticsRange(
            window = ANALYTICS_WEEK,
            now = weekNow,
            weekMode = WEEK_MODE_DYNAMIC,
        )
        val sunday = weekRange.start.toLocalDate()
        val saturday = weekNow.toLocalDate()
        val snapshot = createAnalyticsSnapshot(
            window = ANALYTICS_WEEK,
            range = weekRange,
            source = source(
                usageSummary = UsageSummary(totalMinutes = 35, apps = emptyList()),
                usageHourly = null,
                usageDaily = listOf(
                    UsageDaySummary(sunday.toString(), sunday.dayOfWeek.value % 7, 15),
                    UsageDaySummary(saturday.toString(), saturday.dayOfWeek.value % 7, 20),
                ),
            ),
            generatedAt = weekNow.toInstant().toString(),
        )

        val usage = snapshot.phoneUsage ?: error("Expected phone usage")
        assertEquals(LocalDate.of(2026, 9, 27), sunday)
        assertEquals(15.0, usage.observedMinutesByDate.getValue(sunday.toString()), 0.0)
        assertEquals(20.0, usage.observedMinutesByDate.getValue(saturday.toString()), 0.0)
    }

    private fun source(
        usageSummary: UsageSummary?,
        usageHourly: HourlyUsageSummary?,
        usageDaily: List<UsageDaySummary> = emptyList(),
    ) = AnalyticsSourceData(
        tasks = emptyList(),
        sessions = emptyList(),
        estimationErrors = emptyList(),
        tasksByHour = emptyList(),
        weeklyRates = emptyList(),
        temptations = emptyList(),
        usageSummary = usageSummary,
        usageHourly = usageHourly,
        usageDaily = usageDaily,
    )
}
