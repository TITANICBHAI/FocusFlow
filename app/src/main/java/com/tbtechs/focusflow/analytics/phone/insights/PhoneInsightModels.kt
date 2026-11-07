package com.tbtechs.focusflow.analytics.phone.insights

import com.tbtechs.focusflow.analytics.phone.*
import java.time.LocalDate
import java.time.ZoneId

enum class InsightKind {
    WEEKLY_SUMMARY, MONTHLY_SUMMARY, UNUSUAL_DAY, APP_SURGE, APP_DROP, UNLOCKS_CHANGE, LATE_NIGHT_SHIFT,
    FIRST_APP_PATTERN, CHECKING_HABIT, TIME_OF_DAY_HABIT, CONSISTENCY, RECORD, FOCUS_TREND, DATA_NOTE,
}
enum class InsightGroup(val title: String) {
    SUMMARY("Summary"), CHANGE("Unusual changes"), PATTERN("Patterns"), RECORD("Personal records"), NOTE("About this data"),
}
enum class InsightConfidence { MEDIUM, HIGH }

data class PhoneInsight(
    val id: String,                       // stable, e.g. "weekly:2026-11-05", "surge:com.instagram.android"
    val kind: InsightKind, val group: InsightGroup,
    val title: String, val message: String,
    val evidence: List<String>,           // one fact per line: "What: …", "How: …", "Period: …", "Evidence: …"
    val direction: TrendDirection?,
    val confidence: InsightConfidence,
    val priority: Int,
    val isNew: Boolean = false,
)

data class PhoneInsightInput(
    val today: LocalDate,
    val days: List<PhoneDay>,             // up to 90 days, any order, LIVE today included
    val appDays: List<AppDayRow>,
    val appSessions: List<AppSessionRow>,
    val blockedDays: List<BlockedDayRow>,
    val focusSessions: List<FocusSessionRow>,
    val appNames: Map<String, String>,
    val categoryOf: (String) -> UsageCategory,
    val zone: ZoneId,
)

/** What the repository returns for the Insights screen (the engine has not run yet). */
sealed interface PhoneInsightInputResult {
    object PermissionNeeded : PhoneInsightInputResult
    data class Building(val completeDays: Int) : PhoneInsightInputResult
    data class Ready(val input: PhoneInsightInput) : PhoneInsightInputResult
    data class Failed(val message: String) : PhoneInsightInputResult
}

/** What the ViewModel exposes to the Insights screen (the engine has run). */
sealed interface PhoneInsightsResult {
    object PermissionNeeded : PhoneInsightsResult
    data class Building(val completeDays: Int) : PhoneInsightsResult
    data class Ready(val insights: List<PhoneInsight>, val completeDays: Int) : PhoneInsightsResult
    data class Failed(val message: String) : PhoneInsightsResult
}
