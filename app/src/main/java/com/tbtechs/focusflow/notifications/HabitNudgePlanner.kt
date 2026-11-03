package com.tbtechs.focusflow.notifications

import com.tbtechs.focusflow.data.local.entity.FindingEntity
import kotlin.math.abs

/** One heads-up to show every day at [minuteOfDay] (minutes after local midnight). */
data class HabitNudge(
    val minuteOfDay: Int,
    val title: String,
    val text: String,
)

/**
 * Turns the findings the person has not dismissed into at most two daily heads-ups, each placed
 * shortly before the moment the finding says things go wrong. Pure logic, no Android types.
 *
 * Only findings that carry a time are used, so a nudge is never an arbitrary reminder:
 *  - late-night use: shortly before bedtime
 *  - biggest time sink: 15 minutes before the hour the app is used most
 */
object HabitNudgePlanner {
    private const val MAX_NUDGES = 2
    private const val MIN_GAP_MINUTES = 30
    private const val DEFAULT_LATE_NIGHT_MINUTE = 22 * 60 + 30
    private val LIVE_STATES = setOf("detected", "seen", "aware")
    private val PEAK_HOUR = Regex("\"peak_hour\":(\\d+)")
    private val SUGGESTED_MINUTES = Regex("\"suggested_min\":(\\d+)")

    fun plan(findings: List<FindingEntity>, bedTimeMinutes: Int?): List<HabitNudge> {
        val live = findings.filter { it.state in LIVE_STATES }
        val nudges = mutableListOf<HabitNudge>()

        live.firstOrNull { it.detectionType == "LATE_NIGHT_USE" && !it.subjectAppName.isNullOrBlank() }
            ?.let { finding ->
                nudges += HabitNudge(
                    minuteOfDay = lateNightMinute(bedTimeMinutes),
                    title = "Wind down tonight",
                    text = "${finding.subjectAppName} has kept you up late recently. " +
                        "Block it before bed?",
                )
            }

        live.firstOrNull { it.detectionType == "HEAVY_DAILY_USE" && !it.subjectAppName.isNullOrBlank() }
            ?.let { finding ->
                val peak = PEAK_HOUR.find(finding.evidenceJson)?.groupValues?.get(1)?.toIntOrNull()
                val minute = peak?.takeIf { it in 0..23 }?.let { it * 60 - 15 }
                if (minute != null && minute >= 0) {
                    val limit = SUGGESTED_MINUTES.find(finding.evidenceJson)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    val name = finding.subjectAppName
                    nudges += HabitNudge(
                        minuteOfDay = minute,
                        title = "$name time",
                        text = "You usually open $name around ${hourText(peak)}. " +
                            (limit?.let { "Keep it to about $it min today?" } ?: "Set a limit for today?"),
                    )
                }
            }

        val spaced = mutableListOf<HabitNudge>()
        nudges.sortedBy { it.minuteOfDay }.forEach { nudge ->
            if (spaced.none { abs(it.minuteOfDay - nudge.minuteOfDay) < MIN_GAP_MINUTES }) {
                spaced += nudge
            }
        }
        return spaced.take(MAX_NUDGES)
    }

    /** Half an hour before bedtime, but always before the 23:00 start of the late-night window. */
    internal fun lateNightMinute(bedTimeMinutes: Int?): Int {
        if (bedTimeMinutes == null) return DEFAULT_LATE_NIGHT_MINUTE
        val target = ((bedTimeMinutes - 30) % 1440 + 1440) % 1440
        return if (target in (12 * 60)..DEFAULT_LATE_NIGHT_MINUTE) target else DEFAULT_LATE_NIGHT_MINUTE
    }

    internal fun hourText(hour: Int): String {
        val twelve = if (hour % 12 == 0) 12 else hour % 12
        return "$twelve ${if (hour < 12) "am" else "pm"}"
    }
}
