package com.tbtechs.focusflow.analytics.rules

import com.tbtechs.focusflow.analytics.AnalyticsSnapshot
import com.tbtechs.focusflow.analytics.InsightCard
import com.tbtechs.focusflow.analytics.InsightRule
import com.tbtechs.focusflow.analytics.hourLabel
import com.tbtechs.focusflow.analytics.renderInsightVariant
import kotlin.math.max
import kotlin.math.round

private fun dayName(day: Int): String =
    listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        .getOrElse(day) { "That day" }

private data class WorstScheduledDay(
    val day: Int,
    val total: Int,
    val completed: Int,
    val rate: Double,
)

private fun worstScheduledDay(snapshot: AnalyticsSnapshot): WorstScheduledDay? =
    snapshot.tasks.byDayOfWeek
        .map { (day, bucket) ->
            WorstScheduledDay(
                day = day,
                total = bucket.total,
                completed = bucket.completed,
                rate = if (bucket.total > 0) bucket.completed.toDouble() / bucket.total else 1.0,
            )
        }
        .filter { it.total > 5 && it.rate < 0.4 }
        .sortedWith(compareBy<WorstScheduledDay> { it.rate }.thenByDescending { it.total }.thenBy { it.day })
        .firstOrNull()

private fun hasEnoughTrendData(snapshot: AnalyticsSnapshot, count: Int): Boolean =
    (snapshot.trends?.weeksWithData ?: 0) >= count

private fun lastTrendWeeks(
    snapshot: AnalyticsSnapshot,
    count: Int,
): List<AnalyticsSnapshot.WeekTrend> {
    val weeks = snapshot.trends?.weekByWeek ?: emptyList()
    return if (weeks.size >= count) weeks.takeLast(count) else emptyList()
}

private fun average(values: List<Double>): Double = if (values.isEmpty()) 0.0 else values.average()

private data class NightPattern(val multiplier: Double)

private fun nightPattern(snapshot: AnalyticsSnapshot): NightPattern? {
    val byHour = snapshot.phoneUsage?.byHour ?: return null
    val daytimeAverage = (6..20).sumOf { hour -> byHour[hour] ?: 0.0 } / 15.0
    val nightAverage = listOf(21, 22, 23).sumOf { hour -> byHour[hour] ?: 0.0 } / 3.0
    if (daytimeAverage <= 0.0 || nightAverage <= daytimeAverage * 2.0) return null
    return NightPattern(multiplier = round((nightAverage / daytimeAverage) * 10.0) / 10.0)
}

val threeMonthRules: List<InsightRule> = listOf(
    InsightRule(
        id = "THREE_MONTH_INSUFFICIENT_DATA",
        category = "nothing_to_report",
        condition = { snapshot -> (snapshot.trends?.weeksWithData ?: 0) < 4 },
        priority = { 100 },
        render = { snapshot, seed ->
            InsightCard(
                id = "THREE_MONTH_INSUFFICIENT_DATA",
                category = "nothing_to_report",
                priority = 100,
                headline = "Not enough history yet",
                body = renderInsightVariant(
                    "THREE_MONTH_INSUFFICIENT_DATA",
                    seed,
                    mapOf(
                        "weeks_remaining" to max(0, 4 - (snapshot.trends?.weeksWithData ?: 0)),
                    ),
                ),
                sentiment = "neutral",
            )
        },
    ),
    InsightRule(
        id = "THREE_MONTH_SCHEDULING_HONESTY",
        category = "pattern",
        condition = { snapshot -> worstScheduledDay(snapshot) != null },
        priority = { 95 },
        render = { snapshot, seed ->
            val worst = requireNotNull(worstScheduledDay(snapshot))
            InsightCard(
                id = "THREE_MONTH_SCHEDULING_HONESTY",
                category = "pattern",
                priority = 95,
                headline = "${dayName(worst.day)} is not matching the schedule",
                body = renderInsightVariant(
                    "THREE_MONTH_SCHEDULING_HONESTY",
                    seed,
                    mapOf(
                        "day_name" to dayName(worst.day),
                        "rate" to round(worst.rate * 100.0).toInt(),
                    ),
                ),
                sentiment = "warning",
            )
        },
    ),
    InsightRule(
        id = "THREE_MONTH_PHONE_PEAK",
        category = "pattern",
        condition = { snapshot ->
            snapshot.phoneUsage?.byHour != null && snapshot.phoneUsage.peakHour != null
        },
        priority = { 92 },
        render = { snapshot, seed ->
            val phone = requireNotNull(snapshot.phoneUsage)
            val peakHour = phone.peakHour
            val period = phone.peakPeriod
            val headline = if (period != null) {
                period.replaceFirstChar { it.uppercase() } + " is your peak"
            } else {
                "Phone use is your peak"
            }
            InsightCard(
                id = "THREE_MONTH_PHONE_PEAK",
                category = "pattern",
                priority = 92,
                headline = headline,
                body = renderInsightVariant(
                    "THREE_MONTH_PHONE_PEAK",
                    seed,
                    mapOf(
                        "peak_start" to hourLabel(peakHour),
                        "peak_end" to hourLabel(if (peakHour == null) null else (peakHour + 1) % 24),
                        "peak_period_label" to (period ?: "That period"),
                    ),
                ),
                sentiment = "neutral",
            )
        },
    ),
    InsightRule(
        id = "THREE_MONTH_REAL_FOCUS_WINDOW",
        category = "positive",
        condition = { snapshot ->
            snapshot.sessions.fastestWindowHour != null &&
                snapshot.sessions.fastestWindowSampleSize > 20
        },
        priority = { 90 },
        render = { snapshot, seed ->
            InsightCard(
                id = "THREE_MONTH_REAL_FOCUS_WINDOW",
                category = "positive",
                priority = 90,
                headline = "Your sharpest window",
                body = renderInsightVariant(
                    "THREE_MONTH_REAL_FOCUS_WINDOW",
                    seed,
                    mapOf(
                        "hour_label" to hourLabel(snapshot.sessions.fastestWindowHour),
                        "pct" to (snapshot.sessions.fastestWindowImprovementPercent ?: 0),
                    ),
                ),
                sentiment = "positive",
            )
        },
    ),
    InsightRule(
        id = "THREE_MONTH_REAL_PROBLEM_APP",
        category = "resistance",
        condition = { snapshot -> (snapshot.blocking.topAppShare ?: 0.0) > 0.4 },
        priority = { 88 },
        render = { snapshot, seed ->
            InsightCard(
                id = "THREE_MONTH_REAL_PROBLEM_APP",
                category = "resistance",
                priority = 88,
                headline = "${snapshot.blocking.topApp?.appName ?: "One app"} is the problem",
                body = renderInsightVariant(
                    "THREE_MONTH_REAL_PROBLEM_APP",
                    seed,
                    mapOf(
                        "app_name" to (snapshot.blocking.topApp?.appName ?: "One app"),
                        "share" to round((snapshot.blocking.topAppShare ?: 0.0) * 100.0).toInt(),
                    ),
                ),
                sentiment = "warning",
            )
        },
    ),
    InsightRule(
        id = "THREE_MONTH_IMPROVING",
        category = "trend",
        condition = { snapshot ->
            if (!hasEnoughTrendData(snapshot, 4)) {
                false
            } else {
                val weeks = snapshot.trends?.weekByWeek ?: emptyList()
                val firstFour = weeks.take(4)
                val lastFour = lastTrendWeeks(snapshot, 4)
                if (
                    firstFour.size != 4 ||
                    lastFour.size != 4 ||
                    firstFour.any { !it.hasData } ||
                    lastFour.any { !it.hasData }
                ) {
                    false
                } else {
                    val monotonic = lastFour.withIndex().all { (index, week) ->
                        index == 0 || week.completionRate >= lastFour[index - 1].completionRate
                    }
                    val netImprovement =
                        average(lastFour.map { it.completionRate }) -
                            average(firstFour.map { it.completionRate }) >= 0.1
                    monotonic || netImprovement
                }
            }
        },
        priority = { 85 },
        render = { snapshot, seed ->
            val rates = snapshot.trends?.weekByWeek?.map { it.completionRate } ?: emptyList()
            InsightCard(
                id = "THREE_MONTH_IMPROVING",
                category = "trend",
                priority = 85,
                headline = "You are getting better at this",
                body = renderInsightVariant(
                    "THREE_MONTH_IMPROVING",
                    seed,
                    mapOf(
                        "delta" to round(((rates.lastOrNull() ?: 0.0) - (rates.firstOrNull() ?: 0.0)) * 100.0).toInt(),
                    ),
                ),
                sentiment = "positive",
            )
        },
    ),
    InsightRule(
        id = "THREE_MONTH_PLATEAU",
        category = "trend",
        condition = { snapshot ->
            if (!hasEnoughTrendData(snapshot, 6)) {
                false
            } else {
                val lastSix = lastTrendWeeks(snapshot, 6)
                if (lastSix.size != 6 || lastSix.any { !it.hasData }) {
                    false
                } else {
                    val rates = lastSix.map { it.completionRate }
                    (rates.maxOrNull() ?: 0.0) - (rates.minOrNull() ?: 0.0) < 0.05
                }
            }
        },
        priority = { 75 },
        render = { snapshot, seed ->
            val rates = lastTrendWeeks(snapshot, 6).map { it.completionRate }
            val average = if (rates.isNotEmpty()) rates.average() else 0.0
            InsightCard(
                id = "THREE_MONTH_PLATEAU",
                category = "trend",
                priority = 75,
                headline = "A steady plateau",
                body = renderInsightVariant(
                    "THREE_MONTH_PLATEAU",
                    seed,
                    mapOf("rate" to round(average * 100.0).toInt()),
                ),
                sentiment = "neutral",
            )
        },
    ),
    InsightRule(
        id = "THREE_MONTH_NIGHT_PATTERN",
        category = "pattern",
        condition = { snapshot -> nightPattern(snapshot) != null },
        priority = { 80 },
        render = { snapshot, seed ->
            InsightCard(
                id = "THREE_MONTH_NIGHT_PATTERN",
                category = "pattern",
                priority = 80,
                headline = "A late-night pattern",
                body = renderInsightVariant(
                    "THREE_MONTH_NIGHT_PATTERN",
                    seed,
                    mapOf("mult" to (nightPattern(snapshot)?.multiplier ?: 0.0)),
                ),
                sentiment = "neutral",
            )
        },
    ),
)
