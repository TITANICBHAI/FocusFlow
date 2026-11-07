package com.tbtechs.focusflow.analytics.phone.insights

import com.tbtechs.focusflow.analytics.phone.insights.InsightThresholds as T

/**
 * Turns up to 90 days of derived phone data into a short list of evidence-backed insights.
 * Deterministic and stateless: no clock (`input.today` is the clock), no storage, no Android.
 */
class PhoneInsightEngine {
    fun generate(input: PhoneInsightInput): List<PhoneInsight> {
        val c = InsightContext(input)
        val candidates = buildList {
            addAll(SummaryInsights.weekly(c))
            addAll(SummaryInsights.monthly(c))
            addAll(SummaryInsights.unusualDay(c))
            addAll(ChangeInsights.appSurgeAndDrop(c))
            addAll(ChangeInsights.unlocksChange(c))
            addAll(ChangeInsights.lateNightShift(c))
            addAll(PatternInsights.firstApp(c))
            addAll(PatternInsights.checkingHabit(c))
            addAll(PatternInsights.timeOfDayHabit(c))
            addAll(PatternInsights.consistency(c))
            addAll(RecordInsights.records(c))
            addAll(ChangeInsights.focusTrend(c))
            addAll(RecordInsights.notes(c))
        }
        val order = compareByDescending<PhoneInsight> { it.priority }.thenBy { it.id }
        val unique = candidates.sortedWith(order).distinctBy { it.id }
        val main = unique.filter { it.kind != InsightKind.DATA_NOTE }.take(T.MAX_INSIGHTS)
        val notes = unique.filter { it.kind == InsightKind.DATA_NOTE }.take(T.MAX_NOTES)
        return (main + notes).sortedWith(order)
    }
}
