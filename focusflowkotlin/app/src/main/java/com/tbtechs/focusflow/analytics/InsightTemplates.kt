package com.tbtechs.focusflow.analytics

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.abs

typealias TemplateValue = Any

data class InsightCard(
    val id: String,
    val category: String,
    val priority: Int,
    val headline: String,
    val body: String,
    val sentiment: String,
)

data class InsightRule(
    val id: String,
    val category: String,
    val condition: (AnalyticsSnapshot) -> Boolean,
    val priority: (AnalyticsSnapshot) -> Int,
    val render: (AnalyticsSnapshot, Int) -> InsightCard,
)

val INSIGHT_VARIANTS: Map<String, List<String>> = mapOf(
    "YESTERDAY_PERFECT_DAY" to listOf(
        "Clean day. You finished everything and never reached for a blocked app.",
        "Nothing to flag today. Every task done, zero blocked-app attempts.",
    ),
    "YESTERDAY_CLEAN_NO_BLOCKS" to listOf(
        "You never reached for a blocked app today. That part was clean.",
    ),
    "YESTERDAY_PEAK_BLOCK_HOUR" to listOf(
        "You resisted {count} blocked-app attempts. {peak_hour_label} was your hardest hour, with {peak_count} of them.",
        "{count} times you reached for something blocked. Most of those — {peak_count} — happened {peak_hour_label}.",
    ),
    "YESTERDAY_SECOND_SKIP_THIS_WEEK" to listOf(
        "You've skipped {count} tasks this week. Not a problem if it's deliberate.",
        "{count} skips this week. If the schedule isn't working, it's worth adjusting rather than skipping.",
    ),
    "YESTERDAY_SINGLE_HARD_SESSION" to listOf(
        "Your {session_time} session had {count} blocked-app attempts. That one was hard.",
    ),
    "YESTERDAY_NOTHING_NOTABLE" to listOf(
        "Ordinary day. You showed up, you worked, nothing unusual happened.",
    ),
    "WEEKLY_VULNERABLE_WINDOW" to listOf(
        "{peak_hour_label} is your weakest hour this week. {peak_count} of your {total} blocked-app attempts happened then.",
        "Between {peak_hour_start} and {peak_hour_end}, you reached for a blocked app {peak_count} times — more than any other hour.",
    ),
    "WEEKLY_CLEAN_WINDOW" to listOf(
        "{clean_window} was consistently quiet this week. No blocked-app attempts during that window.",
    ),
    "WEEKLY_ONE_APP" to listOf(
        "{app_name} made up {share}% of your blocked-app attempts this week. It's not a habit — it's the habit.",
        "More than half your blocked-app attempts were {app_name}. {share}% to be exact.",
    ),
    "WEEKLY_TOP_APP_MODERATE" to listOf(
        "{app_name} was your most blocked app this week — {count} attempts.",
    ),
    "WEEKLY_ESTIMATION_IMPROVING" to listOf(
        "Your task estimates were solid this week. You were off by less than 10 minutes on average.",
    ),
    "WEEKLY_UNDERESTIMATING" to listOf(
        "Your tasks are consistently taking longer than you schedule. {avg_error} minutes over on average.",
        "You're underestimating. Tasks ran {avg_error} minutes over their scheduled time on average this week.",
    ),
    "WEEKLY_OVERESTIMATING" to listOf(
        "You're finishing tasks faster than you schedule them — {abs_error} minutes ahead on average. Your buffers might be too generous.",
    ),
    "WEEKLY_SHOWED_UP" to listOf(
        "You showed up {count} of 7 days.",
        "Every day this week. That's the whole job.",
        "You showed up {count} of 7 days this week.",
    ),
    "WEEKLY_BETTER_THAN_LAST" to listOf(
        "Better week than last. Completion rate up, blocking attempts {direction}.",
        "This week was cleaner than last. You're moving in the right direction.",
    ),
    "WEEKLY_WORSE_THAN_LAST" to listOf(
        "Harder week than last. Completion rate dropped, more blocking attempts.",
        "This week was messier than last. Worth paying attention to.",
    ),
    "WEEKLY_FLAT" to listOf(
        "Consistent with last week. Not better, not worse.",
    ),
    "WEEKLY_NOTHING_UNUSUAL" to listOf(
        "Nothing unusual this week. You showed up, you finished things, nothing spiked. Sometimes the analysis is: you did well.",
    ),
    "THREE_MONTH_PHONE_PEAK" to listOf(
        "Your heaviest phone use is consistently between {peak_start} and {peak_end}. Every week, without exception.",
        "{peak_period_label} is when you use your phone most. That's been true across all 12 weeks.",
    ),
    "THREE_MONTH_REAL_FOCUS_WINDOW" to listOf(
        "Tasks you start around {hour_label} consistently finish faster than your estimates. That's your sharpest window.",
        "Your best work happens {hour_label}. Tasks started then run {pct}% closer to your planned time than any other slot.",
    ),
    "THREE_MONTH_SCHEDULING_HONESTY" to listOf(
        "You've scheduled tasks on {day_name} for 12 weeks. Your completion rate that day is {rate}%. Consider whether {day_name} is actually available to you.",
        "{day_name} is your most scheduled day. It's also your worst completion day, at {rate}%. Something doesn't add up.",
    ),
    "THREE_MONTH_REAL_PROBLEM_APP" to listOf(
        "In 12 weeks, {app_name} has accounted for {share}% of every blocked-app attempt. That's not a coincidence. That's the thing.",
        "{app_name} is responsible for {share}% of your blocked attempts over 3 months. One app.",
    ),
    "THREE_MONTH_IMPROVING" to listOf(
        "Your task completion rate has improved over the last 3 months. You're actually getting better at this.",
        "Slow improvement across 12 weeks. Completion rate is up {delta}pt from where you started.",
    ),
    "THREE_MONTH_PLATEAU" to listOf(
        "You've been at around {rate}% completion for 6 weeks. You've hit a ceiling at your current setup.",
        "Flat for 6 weeks. Not getting worse, but not improving. Something about the routine isn't working.",
    ),
    "THREE_MONTH_NIGHT_PATTERN" to listOf(
        "After 9pm, your phone use is consistently {mult}x higher than during the day. Every week.",
        "Your phone use triples after 9pm. That pattern has held across all 12 weeks.",
    ),
    "THREE_MONTH_INSUFFICIENT_DATA" to listOf(
        "Not enough data yet for 3-month patterns. Come back after {weeks_remaining} more weeks.",
    ),
)

fun pickDeterministicVariant(
    variants: List<String>,
    seed: Int,
): String {
    if (variants.isEmpty()) return ""
    val index = abs(seed.toLong()) % variants.size
    return variants[index.toInt()]
}

fun renderInsightVariant(
    id: String,
    seed: Int,
    values: Map<String, TemplateValue> = emptyMap(),
): String {
    val template = pickDeterministicVariant(INSIGHT_VARIANTS[id] ?: emptyList(), seed)
    return Regex("\\{([a-z_]+)\\}").replace(template) { match ->
        if (values.containsKey(match.groupValues[1])) {
            templateValueString(values.getValue(match.groupValues[1]))
        } else {
            match.value
        }
    }
}

private fun templateValueString(value: TemplateValue): String = when (value) {
    is Double -> if (value.isFinite() && value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
    is Float -> if (value.isFinite() && value == value.toLong().toFloat()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
    else -> value.toString()
}

fun hourLabel(hour: Int?): String {
    if (hour == null || hour !in 0..23) return "that hour"
    val normalized = hour % 12
    return "${if (normalized == 0) 12 else normalized}${if (hour < 12) "am" else "pm"}"
}

fun hourWindowLabel(hour: Int?): String {
    if (hour == null || hour !in 0..23) return "that window"
    return "${hourLabel(hour)}–${hourLabel((hour + 1) % 24)}"
}

fun twoHourWindowLabel(hour: Int?): String {
    if (hour == null || hour !in 0..23) return "that window"
    return "${hourLabel(hour)}–${hourLabel((hour + 2) % 24)}"
}

fun taskResultLine(title: String, status: String): String {
    val symbol = when (status) {
        "completed" -> "✓"
        "skipped" -> "—"
        "overdue" -> "✗"
        else -> "·"
    }
    return "$symbol $title"
}

fun weekSeed(generatedAt: String): Int {
    val timestamp = runCatching { Instant.parse(generatedAt).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(generatedAt).toInstant().toEpochMilli() }
        .recoverCatching { LocalDate.parse(generatedAt).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        .getOrNull()
        ?: return 0
    return Math.floorDiv(timestamp, 7L * 24L * 60L * 60L * 1000L).toInt()
}
