package com.tbtechs.focusflow.notifications

/** One heads-up to show every day at [minuteOfDay] (minutes after local midnight). */
data class HabitNudge(
    val minuteOfDay: Int,
    val title: String,
    val text: String,
)
