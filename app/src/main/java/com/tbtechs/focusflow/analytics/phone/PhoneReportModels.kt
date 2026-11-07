package com.tbtechs.focusflow.analytics.phone

import java.time.LocalDate

data class DayPoint(
    val date: LocalDate, val coverage: DayCoverage,
    val screenOnMs: Long, val appMs: Long, val unlocks: Int, val wakes: Int,
)
enum class AverageBasis { COMPLETE_DAYS, TODAY_SO_FAR, NONE }
data class DayGroupAverage(val avgMs: Long, val days: Int)

data class ScreenTimeSection(
    val totalScreenOnMs: Long,
    val totalAppMs: Long,
    val averageBasis: AverageBasis,
    val averageDays: Int,
    val avgScreenOnPerDayMs: Long?,
    val avgAppPerDayMs: Long?,
    val weekday: DayGroupAverage?,
    val weekend: DayGroupAverage?,
    val highest: DayPoint?,
    val lowest: DayPoint?,
    val screenOnByHourAvgMs: List<Long>,     // 24
    val peakHour: Int?,
    val days: List<DayPoint>,                // one per date in range, oldest first
)

data class AppUsageRow(
    val packageName: String, val appName: String, val category: UsageCategory,
    val totalMs: Long, val sharePercent: Double,
    val opens: Int, val avgSessionMs: Long, val longestSessionMs: Long,
    val sessionBucketCounts: List<Int>,      // 5
    val periodMs: List<Long>,                // 4, PhonePeriod.entries order
    val dailyMs: List<Long>,                 // aligned with ScreenTimeSection.days
    val activeDays: Int,
    val avgPerDayMs: Long,
    val previousAvgPerDayMs: Long?,
    val changePercent: Int?,
    val firstAfterUnlock: Int,
)
data class CategoryShare(val category: UsageCategory, val totalMs: Long, val sharePercent: Double)
data class AppShare(val packageName: String, val appName: String, val totalMs: Long)
data class PeriodShare(val period: PhonePeriod, val totalMs: Long, val sharePercent: Double, val topApps: List<AppShare>)
data class AppsSection(
    val totalMs: Long,
    val appDataDays: Int,
    val apps: List<AppUsageRow>,             // by totalMs desc, max 30
    val othersMs: Long,                      // time in apps beyond the 30 listed
    val categories: List<CategoryShare>,     // by totalMs desc
    val periods: List<PeriodShare>,          // 4 entries, PhonePeriod order
)

data class FirstAppRow(
    val packageName: String, val appName: String,
    val unlocks: Int, val shareOfUnlocksPercent: Double, val perDay: Double,
)
data class PhoneBehaviourSection(
    val daysWithData: Int,
    val unlockSignalAvailable: Boolean,
    val unlocks: Int, val unlocksPerDay: Double?,
    val wakes: Int, val wakesPerDay: Double?,
    val avgGapBetweenUnlocksMs: Long?,
    val sessionBasis: SessionBasis,
    val sessions: Int,
    val sessionBucketCounts: List<Int>, val sessionBucketMs: List<Long>,
    val quickChecks: Int, val quickCheckSharePercent: Int?,
    val longestSessionMs: Long,
    val unlocksByHour: List<Int>, val wakesByHour: List<Int>,    // sums over non-missing days
    val firstApps: List<FirstAppRow>, val unlocksWithoutApp: Int,
    val firstUnlockMedianMinuteOfDay: Int?,
)

data class DistractionApp(
    val packageName: String, val appName: String, val category: UsageCategory,
    val totalMs: Long, val sharePercent: Double,
    val blockedAttempts: Int, val opens: Int, val reasons: List<String>,
)
data class ReflexApp(val packageName: String, val appName: String, val opensPerActiveDay: Double, val shortSessionSharePercent: Int)
data class RisingApp(val packageName: String, val appName: String, val currentAvgMs: Long, val previousAvgMs: Long, val changePercent: Int)
data class DistractionSection(
    val distractionMs: Long, val sharePercent: Int?,
    val apps: List<DistractionApp>,          // max 8, by totalMs desc
    val byHourMs: List<Long>,                // 24
    val peakHour: Int?,
    val reflexApps: List<ReflexApp>,         // max 5
    val risingApps: List<RisingApp>,         // max 3
    val changePercent: Int?,                 // category-only distraction avg/day vs previous period
)

data class BlockedAppRow(val packageName: String, val appName: String, val attempts: Int, val sharePercent: Double)
data class FocusWeekPoint(val blockStart: LocalDate, val focusMs: Long, val endedSessions: Int, val heldSessions: Int, val attempts: Int)
data class FocusTrend(
    val direction: TrendDirection,
    val attemptsPerFocusHourNow: Double, val attemptsPerFocusHourBefore: Double, val changePercent: Int,
)
data class FocusBlockingSection(
    val focusMs: Long, val sessions: Int, val heldSessions: Int, val interruptedSessions: Int,
    val durationBucketCounts: List<Int>,     // 5: <15m, 15–30, 30–60, 1–2h, 2h+
    val blockedAttempts: Int, val blockedApps: List<BlockedAppRow>,   // top 8
    val blockedByHour: List<Int>, val blockedPeakHour: Int?,
    val attemptsPerFocusHour: Double?,
    val weeks: List<FocusWeekPoint>,         // up to 8, oldest first
    val trend: FocusTrend?,
)

data class PhoneCoverage(
    val completeDays: Int, val partialDays: Int, val liveDays: Int, val missingDays: Int,
    val phoneDataSince: LocalDate?, val appDataSince: LocalDate?,
)
data class PhoneReport(
    val window: String, val range: PhoneDateRange,
    val screenTime: ScreenTimeSection, val apps: AppsSection, val behaviour: PhoneBehaviourSection,
    val distraction: DistractionSection, val focus: FocusBlockingSection, val coverage: PhoneCoverage,
)
sealed interface PhoneReportResult {
    object PermissionNeeded : PhoneReportResult
    object NoData : PhoneReportResult
    data class Ready(val report: PhoneReport) : PhoneReportResult
    data class Failed(val message: String) : PhoneReportResult
}
