package com.tbtechs.focusflow.analytics.rules

import com.tbtechs.focusflow.analytics.AnalyticsSnapshot
import com.tbtechs.focusflow.analytics.InsightCard
import com.tbtechs.focusflow.analytics.InsightRule
import com.tbtechs.focusflow.analytics.hourLabel
import com.tbtechs.focusflow.analytics.renderInsightVariant
import com.tbtechs.focusflow.analytics.taskResultLine

val yesterdayRules: List<InsightRule> = listOf(
    InsightRule(
        id = "YESTERDAY_PERFECT_DAY",
        category = "positive",
        condition = { snapshot ->
            snapshot.blocking.totalAttempts == 0 &&
                snapshot.tasks.total > 0 &&
                snapshot.tasks.completed == snapshot.tasks.total
        },
        priority = { 90 },
        render = { _, seed ->
            InsightCard(
                id = "YESTERDAY_PERFECT_DAY",
                category = "positive",
                priority = 90,
                headline = "Clean day",
                body = renderInsightVariant("YESTERDAY_PERFECT_DAY", seed),
                sentiment = "positive",
            )
        },
    ),
    InsightRule(
        id = "YESTERDAY_CLEAN_NO_BLOCKS",
        category = "positive",
        condition = { snapshot ->
            snapshot.blocking.totalAttempts == 0 &&
                snapshot.tasks.total > 0 &&
                snapshot.tasks.completed < snapshot.tasks.total
        },
        priority = { 60 },
        render = { _, seed ->
            InsightCard(
                id = "YESTERDAY_CLEAN_NO_BLOCKS",
                category = "positive",
                priority = 60,
                headline = "The distraction side was clean",
                body = renderInsightVariant("YESTERDAY_CLEAN_NO_BLOCKS", seed),
                sentiment = "positive",
            )
        },
    ),
    InsightRule(
        id = "YESTERDAY_PEAK_BLOCK_HOUR",
        category = "resistance",
        condition = { snapshot -> snapshot.blocking.totalAttempts > 0 },
        priority = { 75 },
        render = { snapshot, seed ->
            val peakHour = snapshot.blocking.peakHour
            InsightCard(
                id = "YESTERDAY_PEAK_BLOCK_HOUR",
                category = "resistance",
                priority = 75,
                headline = "${snapshot.blocking.totalAttempts} blocked-app attempts",
                body = renderInsightVariant(
                    "YESTERDAY_PEAK_BLOCK_HOUR",
                    seed,
                    mapOf(
                        "count" to snapshot.blocking.totalAttempts,
                        "peak_hour_label" to hourLabel(peakHour),
                        "peak_count" to if (peakHour == null) 0 else snapshot.blocking.byHour[peakHour] ?: 0,
                    ),
                ),
                sentiment = if (snapshot.blocking.totalAttempts >= 10) "warning" else "neutral",
            )
        },
    ),
    InsightRule(
        id = "YESTERDAY_SINGLE_HARD_SESSION",
        category = "session",
        condition = { snapshot -> (snapshot.sessions.hardestSession?.attempts ?: 0) > 5 },
        priority = { 80 },
        render = { snapshot, seed ->
            val hardest = snapshot.sessions.hardestSession
            InsightCard(
                id = "YESTERDAY_SINGLE_HARD_SESSION",
                category = "session",
                priority = 80,
                headline = "A hard session",
                body = renderInsightVariant(
                    "YESTERDAY_SINGLE_HARD_SESSION",
                    seed,
                    mapOf(
                        "session_time" to hourLabel(hardest?.hour),
                        "count" to (hardest?.attempts ?: 0),
                    ),
                ),
                sentiment = "neutral",
            )
        },
    ),
    InsightRule(
        id = "YESTERDAY_SECOND_SKIP_THIS_WEEK",
        category = "task",
        condition = { snapshot -> (snapshot.tasks.skippedThisWeek ?: snapshot.tasks.skipped) > 1 },
        priority = { 65 },
        render = { snapshot, seed ->
            val count = snapshot.tasks.skippedThisWeek ?: snapshot.tasks.skipped
            InsightCard(
                id = "YESTERDAY_SECOND_SKIP_THIS_WEEK",
                category = "task",
                priority = 65,
                headline = "A pattern worth noticing",
                body = renderInsightVariant(
                    "YESTERDAY_SECOND_SKIP_THIS_WEEK",
                    seed,
                    mapOf("count" to count),
                ),
                sentiment = "neutral",
            )
        },
    ),
    InsightRule(
        id = "YESTERDAY_TASK_RESULT",
        category = "task",
        condition = { true },
        priority = { 50 },
        render = { snapshot, _ ->
            val rows = snapshot.tasks.resultRows
            InsightCard(
                id = "YESTERDAY_TASK_RESULT",
                category = "task",
                priority = 50,
                headline = "Today's tasks",
                body = if (!rows.isNullOrEmpty()) {
                    rows.joinToString("\n") { row -> taskResultLine(row.title, row.status) }
                } else {
                    "No tasks were recorded yesterday."
                },
                sentiment = "neutral",
            )
        },
    ),
    InsightRule(
        id = "YESTERDAY_NOTHING_NOTABLE",
        category = "nothing_to_report",
        condition = { true },
        priority = { 10 },
        render = { _, seed ->
            InsightCard(
                id = "YESTERDAY_NOTHING_NOTABLE",
                category = "nothing_to_report",
                priority = 10,
                headline = "Nothing unusual",
                body = renderInsightVariant("YESTERDAY_NOTHING_NOTABLE", seed),
                sentiment = "neutral",
            )
        },
    ),
)
