package com.tbtechs.focusflow.analytics.phone

import java.time.LocalDate

enum class PhoneDayStatus { COMPLETE, PARTIAL, LIVE }
enum class DayCoverage { COMPLETE, PARTIAL, LIVE, MISSING }
enum class SessionBasis { UNLOCK, SCREEN }
enum class TrendDirection { UP, DOWN, FLAT }

/** One local day of phone behaviour. All hourly lists have exactly 24 entries; bucket lists exactly 5. */
data class PhoneDay(
    val date: String,
    val status: PhoneDayStatus,
    val screenOnMs: Long,
    val screenOnHourlyMs: List<Long>,
    val wakeCount: Int,
    val wakeHourly: List<Int>,
    val unlockCount: Int,
    val unlockHourly: List<Int>,
    val unlockSignal: Boolean,
    val sessionBasis: SessionBasis,
    val sessionCount: Int,
    val sessionBucketCounts: List<Int>,
    val sessionBucketMs: List<Long>,
    val longestSessionMs: Long,
    val firstUnlockAtMs: Long?,
    val unlockGapSumMs: Long,
    val unlockGapCount: Int,
    val firstApps: Map<String, Int>,
    val unlocksWithoutApp: Int,
) {
    companion object {
        fun empty(date: String, status: PhoneDayStatus) = PhoneDay(
            date = date, status = status, screenOnMs = 0L,
            screenOnHourlyMs = List(24) { 0L }, wakeCount = 0, wakeHourly = List(24) { 0 },
            unlockCount = 0, unlockHourly = List(24) { 0 }, unlockSignal = false,
            sessionBasis = SessionBasis.SCREEN, sessionCount = 0,
            sessionBucketCounts = List(PhoneBuckets.COUNT) { 0 },
            sessionBucketMs = List(PhoneBuckets.COUNT) { 0L },
            longestSessionMs = 0L, firstUnlockAtMs = null, unlockGapSumMs = 0L, unlockGapCount = 0,
            firstApps = emptyMap(), unlocksWithoutApp = 0,
        )
    }
}

/** Inclusive date range. */
data class PhoneDateRange(val start: LocalDate, val end: LocalDate) {
    init { require(!end.isBefore(start)) { "end before start" } }
    val dayCount: Int get() = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
    fun dates(): List<LocalDate> = (0 until dayCount).map { start.plusDays(it.toLong()) }
}

data class AppDayRow(
    val date: String, val packageName: String, val appName: String,
    val foregroundMs: Long, val hourlyMs: List<Long>, val sessionCount: Int,
)
data class AppSessionRow(val packageName: String, val date: String, val startedAtMs: Long, val durationMs: Long)
data class FocusSessionRow(val date: String, val startedAtMs: Long, val endedAtMs: Long?, val overrideCount: Int)
data class BlockedDayRow(
    val date: String, val packageName: String, val appName: String,
    val attempts: Int, val hourly: List<Int>,
)
