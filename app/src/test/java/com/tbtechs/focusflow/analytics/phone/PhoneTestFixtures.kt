package com.tbtechs.focusflow.analytics.phone

import java.time.LocalDate

/** Shared builders for report-builder tests. 2026-11-02 is a Monday; 2026-11-07/08 are the weekend. */
object PhoneTestFixtures {
    const val MIN = 60_000L

    val mon: LocalDate = LocalDate.of(2026, 11, 2)
    fun d(offsetFromMonday: Int): LocalDate = mon.plusDays(offsetFromMonday.toLong())
    fun week(): PhoneDateRange = PhoneDateRange(d(0), d(6))

    fun day(
        date: LocalDate,
        status: PhoneDayStatus = PhoneDayStatus.COMPLETE,
        screenOnMin: Int = 0,
        unlocks: Int = 0,
        wakes: Int = 0,
        screenHour: Int = 12,
        hourlyMs: List<Long>? = null,
        sessions: Int = 0,
        bucketCounts: List<Int>? = null,
        longestMs: Long = 0L,
        gapSumMs: Long = 0L,
        gapCount: Int = 0,
        firstUnlockAtMs: Long? = null,
        firstApps: Map<String, Int> = emptyMap(),
        unlocksWithoutApp: Int = 0,
        unlockHour: Int = 9,
        wakeHour: Int = 9,
    ): PhoneDay {
        val screenMs = screenOnMin * MIN
        val hourly = hourlyMs ?: List(24) { if (it == screenHour) screenMs else 0L }
        return PhoneDay.empty(date.toString(), status).copy(
            screenOnMs = screenMs,
            screenOnHourlyMs = hourly,
            wakeCount = wakes,
            wakeHourly = List(24) { if (it == wakeHour) wakes else 0 },
            unlockCount = unlocks,
            unlockHourly = List(24) { if (it == unlockHour) unlocks else 0 },
            unlockSignal = unlocks > 0,
            sessionBasis = if (unlocks > 0) SessionBasis.UNLOCK else SessionBasis.SCREEN,
            sessionCount = sessions,
            sessionBucketCounts = bucketCounts ?: List(PhoneBuckets.COUNT) { 0 },
            longestSessionMs = longestMs,
            unlockGapSumMs = gapSumMs,
            unlockGapCount = gapCount,
            firstUnlockAtMs = firstUnlockAtMs,
            firstApps = firstApps,
            unlocksWithoutApp = unlocksWithoutApp,
        )
    }

    fun app(
        date: LocalDate, pkg: String, ms: Long, name: String = pkg,
        hourly: List<Long>? = null, hour: Int = 12, sessions: Int = 0,
    ) = AppDayRow(
        date = date.toString(), packageName = pkg, appName = name, foregroundMs = ms,
        hourlyMs = hourly ?: List(24) { if (it == hour) ms else 0L }, sessionCount = sessions,
    )
}
