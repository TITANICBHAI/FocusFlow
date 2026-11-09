package com.tbtechs.focusflow.notifications

import com.tbtechs.focusflow.analytics.phone.TrendDirection
import com.tbtechs.focusflow.analytics.phone.insights.InsightKind
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsight

/**
 * Turns the phone-habit insights into at most two daily heads-ups. Pure logic, no Android types.
 *
 * A nudge is only made when an insight carries a time and the app is a distraction, so it is never an arbitrary reminder:
 *  - late-night screen time is going up: shortly before bedtime
 *  - a distracting app is taking more time: 15 minutes before the hour it is used most
 *  - a distracting app has a clear time of day: 15 minutes before its busiest hour
 */
object PhoneNudgePlanner {
    const val MAX = 2
    const val LEAD_MINUTES = 15

    fun plan(
        insights: List<PhoneInsight>,
        isDistraction: (String) -> Boolean,
        bedTimeMinutes: Int?,
    ): List<HabitNudge> {
        val ordered = insights
            .filter { it.kind != InsightKind.DATA_NOTE }
            .sortedWith(compareByDescending<PhoneInsight> { it.priority }.thenBy { it.id })
        val nudges = ArrayList<HabitNudge>()

        ordered.firstOrNull { it.kind == InsightKind.LATE_NIGHT_SHIFT && it.direction == TrendDirection.UP }?.let {
            nudges += HabitNudge(
                minuteOfDay = NudgeSchedule.lateNightMinute(bedTimeMinutes),
                title = "Wind down tonight",
                text = "Late-night screen time is creeping up. Put the phone down a little earlier?",
            )
        }

        for (insight in ordered) {
            val pkg = insight.subject ?: continue
            val hour = insight.peakHour?.takeIf { it in 1..23 } ?: continue
            if (!isDistraction(pkg)) continue
            val name = insight.subjectName?.takeIf { it.isNotBlank() } ?: continue
            val minute = hour * 60 - LEAD_MINUTES
            when (insight.kind) {
                InsightKind.APP_SURGE -> nudges += HabitNudge(
                    minuteOfDay = minute,
                    title = "$name is taking more of your day",
                    text = "You use it most around ${NudgeSchedule.hourText(hour)}. Start a short block first?",
                )
                InsightKind.TIME_OF_DAY_HABIT -> nudges += HabitNudge(
                    minuteOfDay = minute,
                    title = "Your $name hour is coming",
                    text = "You usually open it around ${NudgeSchedule.hourText(hour)}. Take a break first?",
                )
                else -> Unit
            }
        }
        return NudgeSchedule.merge(nudges, emptyList(), MAX)
    }
}
