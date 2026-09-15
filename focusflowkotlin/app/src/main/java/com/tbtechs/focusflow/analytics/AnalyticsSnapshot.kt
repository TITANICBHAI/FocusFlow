package com.tbtechs.focusflow.analytics

/**
 * The subset of the analytics snapshot required by AchievementEngine.
 *
 * The remaining analytics fields can be added by the AnalyticsProcessor port
 * without changing achievement condition signatures.
 */
data class AnalyticsSnapshot(
    val generatedAt: String,
    val window: String,
    val tasks: TaskMetrics,
    val sessions: SessionMetrics,
    val blocking: BlockingMetrics,
    val trends: TrendMetrics? = null,
    val sourceHealth: SourceHealth? = null,
    val phoneUsage: PhoneUsage? = null,
) {
    data class TaskMetrics(
        val completed: Int,
        val byDayOfWeek: Map<Int, DayBucket>,
        val estimationErrorMinutes: List<Double>,
        val total: Int = completed,
        val skipped: Int = 0,
        val skippedThisWeek: Int? = null,
        val missed: Int = 0,
        val resultRows: List<TaskResultRow>? = null,
        val byHour: Map<Int, HourBucket> = emptyMap(),
        val firstTaskHour: Int? = null,
    )

    data class SessionMetrics(
        val cleanCount: Int,
        val total: Int = 0,
        val totalFocusMinutes: Double = 0.0,
        val byHour: Map<Int, Int> = emptyMap(),
        val byDayOfWeek: Map<Int, Int> = emptyMap(),
        val avgDurationMinutes: Double = 0.0,
        val fastestWindowHour: Int? = null,
        val fastestWindowSampleSize: Int = 0,
        val fastestWindowImprovementPercent: Int? = null,
        val hardestSession: HardestSession? = null,
    )

    data class BlockingMetrics(
        val totalAttempts: Int,
        val topAppShare: Double?,
        val byHour: Map<Int, Int> = emptyMap(),
        val byApp: Map<String, AppCount> = emptyMap(),
        val peakHour: Int? = null,
        val topApp: AppCount? = null,
    )

    data class DayBucket(
        val total: Int,
        val completed: Int = 0,
    )

    data class HourBucket(
        val total: Int,
        val completed: Int = 0,
    )

    data class TaskResultRow(
        val title: String,
        val status: String,
    )

    data class HardestSession(
        val hour: Int,
        val attempts: Int,
    )

    data class AppCount(
        val appName: String,
        val count: Int,
    )

    data class TrendMetrics(
        val completionRatePrev: Double?,
        val completionRateCurr: Double,
        val blockingAttemptsPrev: Int?,
        val blockingAttemptsCurr: Int,
        val weekByWeek: List<WeekTrend> = emptyList(),
        val weeksWithData: Int = 0,
    )

    data class WeekTrend(
        val weekStart: String,
        val completionRate: Double,
        val hasData: Boolean,
    )

    data class SourceHealth(
        val temptations: String = "loaded",
    )

    data class PhoneUsage(
        val byHour: Map<Int, Double>,
        val peakHour: Int?,
        val peakPeriod: String?,
        val heaviestApp: HeaviestApp? = null,
    )

    data class HeaviestApp(
        val appName: String,
        val minutes: Double,
    )
}
