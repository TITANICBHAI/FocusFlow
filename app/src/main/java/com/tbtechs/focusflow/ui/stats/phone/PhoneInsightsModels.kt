package com.tbtechs.focusflow.ui.stats.phone

import com.tbtechs.focusflow.analytics.phone.TrendDirection
import com.tbtechs.focusflow.analytics.phone.insights.InsightGroup
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsight

/** Pure grouping and wording for the Insights screen. */
internal data class InsightSection(val group: InsightGroup, val insights: List<PhoneInsight>)

/** Groups in [InsightGroup] order (summary, change, pattern, record, note); empty groups are omitted; order inside a group is kept. */
internal fun groupInsights(insights: List<PhoneInsight>): List<InsightSection> =
    InsightGroup.entries.mapNotNull { group ->
        insights.filter { it.group == group }.takeIf { it.isNotEmpty() }?.let { InsightSection(group, it) }
    }

/** The evidence lines exactly as the engine produced them. */
internal fun evidenceLines(insight: PhoneInsight): List<String> = insight.evidence

internal fun directionGlyph(direction: TrendDirection?): String = when (direction) {
    TrendDirection.UP -> "▲"
    TrendDirection.DOWN -> "▼"
    TrendDirection.FLAT -> "●"
    null -> ""
}

internal fun buildingMessage(completeDays: Int): String =
    "Insights start once FocusFlow has 3 full days of phone data. Days recorded so far: $completeDays."

internal const val INSIGHTS_SUBTITLE = "Patterns, changes and records from your own phone data."
internal const val INSIGHTS_EMPTY = "Nothing unusual to report right now. Check back after a few more days."
internal const val INSIGHTS_FAILED = "Couldn't analyse your phone habits right now."
internal const val INSIGHTS_FOOTER = "Calculated on this phone only. These are observations about your habits, not advice or a diagnosis."

/** Card title: the trend glyph (when there is one) in front of the insight's own title. */
internal fun insightCardTitle(insight: PhoneInsight): String {
    val glyph = directionGlyph(insight.direction)
    return if (glyph.isEmpty()) insight.title else "$glyph ${insight.title}"
}
