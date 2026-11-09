package com.tbtechs.focusflow.notifications

import com.tbtechs.focusflow.data.local.entity.FindingEntity
import kotlin.math.abs

/**
 * Turns the findings the person has not dismissed into at most two daily heads-ups, each placed
 * shortly before the moment the finding says things go wrong. Pure logic, no Android types.
 *
 * Only findings that carry a time are used, so a nudge is never an arbitrary reminder:
 *  - late-night use: shortly before bedtime
 *  - hardest hour (blocked-app attempts): 15 minutes before the hour they cluster
 *  - biggest time sink: 15 minutes before the hour the app is used most
 *  - first app after picking up the phone: 15 minutes before the hour it is reached for most
 */
object HabitNudgePlanner {
    private const val MAX_NUDGES = 2
    private const val MIN_GAP_MINUTES = 30
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

        live.firstOrNull { it.detectionType == "TEMPTATION_PEAK" }?.let { finding ->
            val peak = PEAK_HOUR.find(finding.evidenceJson)?.groupValues?.get(1)?.toIntOrNull()
            val minute = peak?.takeIf { it in 0..23 }?.let { it * 60 - 15 }
            if (peak != null && minute != null && minute >= 0) {
                val name = finding.subjectAppName?.takeIf { it.isNotBlank() } ?: "a blocked app"
                nudges += HabitNudge(
                    minuteOfDay = minute,
                    title = "Your hardest hour is coming",
                    text = "You often try to open $name around ${hourText(peak)}. Start a block now?",
                )
            }
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

        live.firstOrNull {
            it.detectionType == "ENTRY_APP" &&
                it.evidenceJson.contains("\"blockable\":true") &&
                !it.subjectAppName.isNullOrBlank()
        }?.let { finding ->
            val peak = PEAK_HOUR.find(finding.evidenceJson)?.groupValues?.get(1)?.toIntOrNull()
            val minute = peak?.takeIf { it in 0..23 }?.let { it * 60 - 15 }
            if (peak != null && minute != null && minute >= 0) {
                val name = finding.subjectAppName
                nudges += HabitNudge(
                    minuteOfDay = minute,
                    title = "Before you reach for $name",
                    text = "Around ${hourText(peak)} you usually open $name first. " +
                        "Start a short block now?",
                )
            }
        }

        // Kept in priority order (the order they were added), then shown in clock order.
        val chosen = mutableListOf<HabitNudge>()
        nudges.forEach { nudge ->
            if (chosen.size < MAX_NUDGES &&
                chosen.none { abs(it.minuteOfDay - nudge.minuteOfDay) < MIN_GAP_MINUTES }
            ) {
                chosen += nudge
            }
        }
        return chosen.sortedBy { it.minuteOfDay }
    }

    internal fun lateNightMinute(bedTimeMinutes: Int?): Int = NudgeSchedule.lateNightMinute(bedTimeMinutes)

    internal fun hourText(hour: Int): String = NudgeSchedule.hourText(hour)
}
