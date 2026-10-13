package com.tbtechs.focusflow.analytics.rules

import com.tbtechs.focusflow.analytics.AnalyticsSnapshot
import com.tbtechs.focusflow.analytics.InsightCard
import com.tbtechs.focusflow.analytics.InsightRule
import com.tbtechs.focusflow.analytics.hourLabel
import com.tbtechs.focusflow.analytics.renderInsightVariant
import com.tbtechs.focusflow.analytics.twoHourWindowLabel
import kotlin.math.abs
import kotlin.math.round

private fun averageEstimationError(snapshot: AnalyticsSnapshot): Double? {
    val errors = snapshot.tasks.estimationErrorMinutes
    if (errors.isEmpty()) return null
    return errors.average()
}

private fun bestCleanWindow(snapshot: AnalyticsSnapshot): Int? {
    val temptationHealth = snapshot.sourceHealth?.temptations
    if (!temptationHealth.isNullOrEmpty() && temptationHealth != "loaded") {
        return null
    }
    if (
        snapshot.tasks.total == 0 &&
        snapshot.sessions.total == 0 &&
        snapshot.blocking.totalAttempts == 0
    ) {
        return null
    }
    for (hour in 0..23) {
        val nextHour = (hour + 1) % 24
        if ((snapshot.blocking.byHour[hour] ?: 0) == 0 &&
            (snapshot.blocking.byHour[nextHour] ?: 0) == 0
        ) {
            return hour
        }
    }
    return null
}

private fun daysShownUp(snapshot: AnalyticsSnapshot): Int {
    val present = mutableSetOf<Int>()
    snapshot.tasks.byDayOfWeek.forEach { (day, bucket) ->
        if (bucket.total > 0) present.add(day)
    }
    snapshot.sessions.byDayOfWeek.forEach { (day, count) ->
        if (count > 0) present.add(day)
    }
    return present.size
}

val weeklyRules: List<InsightRule> = listOf(
    InsightRule(
        id = "WEEKLY_VULNERABLE_WINDOW",
        category = "resistance",
        condition = { snapshot ->
            snapshot.blocking.totalAttempts > 0 && snapshot.blocking.peakHour != null
        },
        priority = { 85 },
        render = { snapshot, seed ->
            val hour = snapshot.blocking.peakHour
            val count = if (hour == null) 0 else snapshot.blocking.byHour[hour] ?: 0
            InsightCard(
                id = "WEEKLY_VULNERABLE_WINDOW",
                category = "resistance",
                priority = 85,
                headline = "${hourLabel(hour)} is your weakest hour",
                body = renderInsightVariant(
                    "WEEKLY_VULNERABLE_WINDOW",
                    seed,
                    mapOf(
                        "peak_hour_label" to hourLabel(hour),
                        "peak_hour_start" to hourLabel(hour),
                        "peak_hour_end" to hourLabel(if (hour == null) null else (hour + 1) % 24),
                        "peak_count" to count,
                        "total" to snapshot.blocking.totalAttempts,
                    ),
                ),
                sentiment = if (count > 8) "warning" else "neutral",
            )
        },
    ),
    InsightRule(
        id = "WEEKLY_ONE_APP",
        category = "pattern",
        condition = { snapshot -> (snapshot.blocking.topAppShare ?: 0.0) > 0.5 },
        priority = { 88 },
        render = { snapshot, seed ->
            val app = snapshot.blocking.topApp?.appName ?: "One app"
            InsightCard(
                id = "WEEKLY_ONE_APP",
                category = "pattern",
                priority = 88,
                headline = "$app is the habit",
                body = renderInsightVariant(
                    "WEEKLY_ONE_APP",
                    seed,
                    mapOf(
                        "app_name" to (snapshot.blocking.topApp?.appName ?: "one app"),
                        "share" to round((snapshot.blocking.topAppShare ?: 0.0) * 100.0).toInt(),
                    ),
                ),
                sentiment = "warning",
            )
        },
    ),
    InsightRule(
        id = "WEEKLY_TOP_APP_MODERATE",
        category = "pattern",
        condition = { snapshot ->
            snapshot.blocking.topApp != null &&
                (snapshot.blocking.topAppShare ?: 0.0) <= 0.5
        },
        priority = { 60 },
        render = { snapshot, seed ->
            InsightCard(
                id = "WEEKLY_TOP_APP_MODERATE",
                category = "pattern",
                priority = 60,
                headline = "${snapshot.blocking.topApp?.appName ?: "An app"} was most common",
                body = renderInsightVariant(
                    "WEEKLY_TOP_APP_MODERATE",
                    seed,
                    mapOf(
                        "app_name" to (snapshot.blocking.topApp?.appName ?: "one app"),
                        "count" to (snapshot.blocking.topApp?.count ?: 0),
                    ),
                ),
                sentiment = "neutral",
            )
        },
    ),
    InsightRule(
        id = "WEEKLY_CLEAN_WINDOW",
        category = "positive",
        condition = { snapshot -> bestCleanWindow(snapshot) != null },
        priority = { 55 },
        render = { snapshot, seed ->
            InsightCard(
                id = "WEEKLY_CLEAN_WINDOW",
                category = "positive",
                priority = 55,
                headline = "A quiet window",
                body = renderInsightVariant(
                    "WEEKLY_CLEAN_WINDOW",
                    seed,
                    mapOf("clean_window" to twoHourWindowLabel(bestCleanWindow(snapshot))),
                ),
                sentiment = "positive",
            )
        },
    ),
    InsightRule(
        id = "WEEKLY_ESTIMATION_IMPROVING",
        category = "task",
        condition = { snapshot ->
            val average = averageEstimationError(snapshot)
            average != null && average >= -5.0 && average <= 10.0
        },
        priority = { 65 },
        render = { _, seed ->
            InsightCard(
                id = "WEEKLY_ESTIMATION_IMPROVING",
                category = "task",
                priority = 65,
                headline = "Your estimates were solid",
                body = renderInsightVariant("WEEKLY_ESTIMATION_IMPROVING", seed),
                sentiment = "positive",
            )
        },
    ),
    InsightRule(
        id = "WEEKLY_UNDERESTIMATING",
        category = "task",
        condition = { snapshot -> (averageEstimationError(snapshot) ?: 0.0) > 20.0 },
        priority = { 72 },
        render = { snapshot, seed ->
            val average = averageEstimationError(snapshot) ?: 0.0
            InsightCard(
                id = "WEEKLY_UNDERESTIMATING",
                category = "task",
                priority = 72,
                headline = "You are underestimating",
                body = renderInsightVariant(
                    "WEEKLY_UNDERESTIMATING",
                    seed,
                    mapOf("avg_error" to round(average).toInt()),
                ),
                sentiment = "warning",
            )
        },
    ),
    InsightRule(
        id = "WEEKLY_OVERESTIMATING",
        category = "task",
        condition = { snapshot -> (averageEstimationError(snapshot) ?: 0.0) < -15.0 },
        priority = { 62 },
        render = { snapshot, seed ->
            InsightCard(
                id = "WEEKLY_OVERESTIMATING",
                category = "task",
                priority = 62,
                headline = "Your buffers may be generous",
                body = renderInsightVariant(
                    "WEEKLY_OVERESTIMATING",
                    seed,
                    mapOf("abs_error" to round(abs(averageEstimationError(snapshot) ?: 0.0)).toInt()),
                ),
                sentiment = "neutral",
            )
        },
    ),
    InsightRule(
        id = "WEEKLY_SHOWED_UP",
        category = "positive",
        condition = { true },
        priority = { 40 },
        render = { snapshot, seed ->
            val days = daysShownUp(snapshot)
            val variantSeed = if (days == 7) 1 else if (days < 3) 2 else seed
            InsightCard(
                id = "WEEKLY_SHOWED_UP",
                category = "positive",
                priority = 40,
                headline = "You showed up $days of 7 days",
                body = renderInsightVariant(
                    "WEEKLY_SHOWED_UP",
                    variantSeed,
                    mapOf("count" to days),
                ),
                sentiment = if (days >= 5) "positive" else if (days < 3) "warning" else "neutral",
            )
        },
    ),
    InsightRule(
        id = "WEEKLY_BETTER_THAN_LAST",
        category = "trend",
        condition = { snapshot ->
            val trend = snapshot.trends
            trend != null &&
                trend.completionRatePrev != null &&
                trend.completionRateCurr > trend.completionRatePrev + 0.05
        },
        priority = { 78 },
        render = { snapshot, seed ->
            val trend = requireNotNull(snapshot.trends)
            val previous = trend.blockingAttemptsPrev ?: trend.blockingAttemptsCurr
            val direction = when {
                trend.blockingAttemptsCurr < previous -> "down"
                trend.blockingAttemptsCurr > previous -> "up"
                else -> "holding steady"
            }
            InsightCard(
                id = "WEEKLY_BETTER_THAN_LAST",
                category = "trend",
                priority = 78,
                headline = "A better week",
                body = renderInsightVariant(
                    "WEEKLY_BETTER_THAN_LAST",
                    seed,
                    mapOf("direction" to direction),
                ),
                sentiment = "positive",
            )
        },
    ),
    InsightRule(
        id = "WEEKLY_WORSE_THAN_LAST",
        category = "trend",
        condition = { snapshot ->
            val trend = snapshot.trends
            trend != null &&
                trend.completionRatePrev != null &&
                trend.completionRateCurr < trend.completionRatePrev - 0.1
        },
        priority = { 82 },
        render = { _, seed ->
            InsightCard(
                id = "WEEKLY_WORSE_THAN_LAST",
                category = "trend",
                priority = 82,
                headline = "A harder week",
                body = renderInsightVariant("WEEKLY_WORSE_THAN_LAST", seed),
                sentiment = "warning",
            )
        },
    ),
    InsightRule(
        id = "WEEKLY_FLAT",
        category = "nothing_to_report",
        condition = { snapshot ->
            val trend = snapshot.trends
            trend != null &&
                trend.completionRatePrev != null &&
                abs(trend.completionRateCurr - trend.completionRatePrev) < 0.05 &&
                trend.completionRateCurr >= trend.completionRatePrev - 0.1
        },
        priority = { 35 },
        render = { _, seed ->
            InsightCard(
                id = "WEEKLY_FLAT",
                category = "nothing_to_report",
                priority = 35,
                headline = "Consistent with last week",
                body = renderInsightVariant("WEEKLY_FLAT", seed),
                sentiment = "neutral",
            )
        },
    ),
)
