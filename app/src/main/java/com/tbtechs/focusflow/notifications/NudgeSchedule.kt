package com.tbtechs.focusflow.notifications

import kotlin.math.abs

/** Shared, Android-free rules for placing heads-ups on the clock. */
object NudgeSchedule {
    /** The most heads-ups a person ever gets in a day, whichever analysis they come from. */
    const val MAX_NUDGES = 3
    const val MIN_GAP_MINUTES = 30
    private const val DEFAULT_LATE_NIGHT_MINUTE = 22 * 60 + 30

    /**
     * [primary] nudges win over [secondary] ones; a nudge too close to an earlier pick is dropped.
     * The result is in clock order and never longer than [max].
     */
    fun merge(primary: List<HabitNudge>, secondary: List<HabitNudge>, max: Int = MAX_NUDGES): List<HabitNudge> {
        val chosen = mutableListOf<HabitNudge>()
        for (nudge in primary + secondary) {
            if (chosen.size >= max) break
            if (chosen.none { abs(it.minuteOfDay - nudge.minuteOfDay) < MIN_GAP_MINUTES }) chosen += nudge
        }
        return chosen.sortedBy { it.minuteOfDay }
    }

    /** Half an hour before bedtime, but always before the 23:00 start of the late-night window. */
    fun lateNightMinute(bedTimeMinutes: Int?): Int {
        if (bedTimeMinutes == null) return DEFAULT_LATE_NIGHT_MINUTE
        val target = ((bedTimeMinutes - 30) % 1440 + 1440) % 1440
        return if (target in (12 * 60)..DEFAULT_LATE_NIGHT_MINUTE) target else DEFAULT_LATE_NIGHT_MINUTE
    }

    fun hourText(hour: Int): String {
        val twelve = if (hour % 12 == 0) 12 else hour % 12
        return "$twelve ${if (hour < 12) "am" else "pm"}"
    }
}
