package com.tbtechs.focusflow.analytics

import com.tbtechs.focusflow.data.local.dao.WeeklyInsightDao
import com.tbtechs.focusflow.data.local.entity.WeeklyInsightEntity
import com.tbtechs.focusflow.analytics.rules.threeMonthRules
import com.tbtechs.focusflow.analytics.rules.weeklyRules
import com.tbtechs.focusflow.analytics.rules.yesterdayRules
import java.time.Instant
import kotlin.math.max

typealias InsightCategory = String

private val rulesByWindow: Map<AnalyticsWindow, List<InsightRule>> = mapOf(
    ANALYTICS_YESTERDAY to yesterdayRules,
    ANALYTICS_WEEK to weeklyRules,
    ANALYTICS_THREE_MONTHS to threeMonthRules,
)

fun buildInsights(
    snapshot: AnalyticsSnapshot,
    limit: Int = if (snapshot.window == ANALYTICS_THREE_MONTHS) 5 else 4,
): List<InsightCard> {
    val seed = weekSeed(snapshot.generatedAt)
    val candidates = (rulesByWindow[snapshot.window] ?: emptyList())
        .mapIndexedNotNull { index, rule ->
            if (!rule.condition(snapshot)) return@mapIndexedNotNull null
            Triple(rule.render(snapshot, seed), rule.priority(snapshot), index)
        }
        .sortedWith(compareByDescending<Triple<InsightCard, Int, Int>> { it.second }.thenBy { it.third })
    return candidates.take(max(1, limit)).map { it.first }
}

fun selectWeeklyStandout(
    snapshot: AnalyticsSnapshot,
    previousInsightIds: List<String>,
): InsightCard {
    val previous = previousInsightIds.toSet()
    val candidate = buildInsights(snapshot, 8)
        .firstOrNull { it.category != "nothing_to_report" && it.id !in previous }
    return candidate ?: InsightCard(
        id = "WEEKLY_NOTHING_UNUSUAL",
        category = "nothing_to_report",
        priority = 1,
        headline = "Nothing unusual this week",
        body = renderInsightVariant(
            "WEEKLY_NOTHING_UNUSUAL",
            weekSeed(snapshot.generatedAt),
        ),
        sentiment = "neutral",
    )
}

suspend fun syncWeeklyStandout(
    snapshot: AnalyticsSnapshot,
    weeklyInsightDao: WeeklyInsightDao,
): InsightCard {
    require(snapshot.window == ANALYTICS_WEEK) {
        "Weekly standout requires the week analytics window"
    }
    val previousIds = weeklyInsightDao.getRecentInsightIds(limit = 8)
    val standout = selectWeeklyStandout(snapshot, previousIds)
    weeklyInsightDao.record(
        WeeklyInsightEntity(
            weekStart = snapshot.range.startISO.take(10),
            insightId = standout.id,
            selectedAt = Instant.now().toString(),
        ),
    )
    return standout
}

class InsightEngine(
    private val weeklyInsightDao: WeeklyInsightDao,
) {
    fun buildInsights(
        snapshot: AnalyticsSnapshot,
        limit: Int = if (snapshot.window == ANALYTICS_THREE_MONTHS) 5 else 4,
    ): List<InsightCard> = com.tbtechs.focusflow.analytics.buildInsights(snapshot, limit)

    fun selectWeeklyStandout(
        snapshot: AnalyticsSnapshot,
        previousInsightIds: List<String>,
    ): InsightCard = com.tbtechs.focusflow.analytics.selectWeeklyStandout(snapshot, previousInsightIds)

    suspend fun syncWeeklyStandout(snapshot: AnalyticsSnapshot): InsightCard =
        com.tbtechs.focusflow.analytics.syncWeeklyStandout(snapshot, weeklyInsightDao)
}