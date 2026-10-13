package com.tbtechs.focusflow.analytics

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageCalendarAggregatorTest {
    @Test
    fun aggregateByDateKeepsAllAppsAndTheirUsageForTheDay() {
        val date = LocalDate.of(2026, 10, 9)
        val zone = ZoneId.of("Asia/Kolkata")
        val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val minuteMs = 60_000L
        val sessions = listOf(
            session("app.instagram", dayStartMs + 1 * 60 * minuteMs, 40 * minuteMs),
            session("app.whatsapp", dayStartMs + 2 * 60 * minuteMs, 15 * minuteMs),
            session("app.maps", dayStartMs + 3 * 60 * minuteMs, 3 * minuteMs),
            session("app.settings", dayStartMs + 4 * 60 * minuteMs, 400L),
        )

        val appDaysByDate = UsageCalendarAggregator.aggregateByDate(
            sessions = sessions,
            rangeStartMs = dayStartMs,
            rangeEndMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            zoneId = zone,
        )
        val appDays = appDaysByDate.getValue(date.toString())

        assertEquals(
            listOf("app.instagram", "app.whatsapp", "app.maps", "app.settings"),
            appDays.map { it.packageName },
        )
        assertEquals(40 * minuteMs + 15 * minuteMs + 3 * minuteMs + 400L, appDays.sumOf { it.foregroundMs })
        assertEquals(58 * minuteMs, appDays.filter { it.foregroundMs >= 500L }.sumOf { it.foregroundMs })
    }

    private fun session(packageName: String, startMs: Long, durationMs: Long) = ForegroundSession(
        packageName = packageName,
        startedAtMs = startMs,
        endedAtMs = startMs + durationMs,
        activityClassName = null,
        isNewOpen = true,
    )
}
