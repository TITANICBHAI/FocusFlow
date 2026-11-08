package com.tbtechs.focusflow.ui.stats.phone

import com.tbtechs.focusflow.analytics.phone.DayCoverage
import com.tbtechs.focusflow.analytics.phone.DayPoint
import java.time.LocalDate

/** One bar of the by-day chart. `value` is screen-on milliseconds. */
internal data class DayBarPoint(val label: String, val value: Float, val dimmed: Boolean, val highlighted: Boolean)

/**
 * 0 or negative → 0f; positive values are scaled by the maximum and never drop below [minVisible];
 * all-zero input → all 0f; empty input → empty list.
 */
internal fun barFractions(values: List<Number>, minVisible: Float = 0.04f): List<Float> {
    val cleaned = values.map { n -> n.toDouble().takeIf { it.isFinite() && it > 0.0 } ?: 0.0 }
    val max = cleaned.maxOrNull() ?: return emptyList()
    if (max <= 0.0) return cleaned.map { 0f }
    return cleaned.map { v -> if (v <= 0.0) 0f else (v / max).toFloat().coerceIn(minVisible, 1f) }
}

/**
 * ≤ 7 bars → weekday initial ("M","T","W","T","F","S","S"); more bars → day-of-month on every 7th bar
 * (index 0, 7, 14, …) and on the last bar, "" otherwise.
 */
internal fun dayBarLabel(date: LocalDate, total: Int, index: Int): String =
    if (total <= 7) {
        WEEKDAY_INITIALS[date.dayOfWeek.value - 1].toString()
    } else if (index % 7 == 0 || index == total - 1) {
        date.dayOfMonth.toString()
    } else {
        ""
    }

private const val WEEKDAY_INITIALS = "MTWTFSS"

/** value = screenOnMs; dimmed = coverage MISSING or PARTIAL; highlighted = date == highestDate. */
internal fun toDayBarPoints(days: List<DayPoint>, highestDate: LocalDate?): List<DayBarPoint> =
    days.mapIndexed { index, day ->
        DayBarPoint(
            label = dayBarLabel(day.date, days.size, index),
            value = day.screenOnMs.toFloat(),
            dimmed = day.coverage == DayCoverage.MISSING || day.coverage == DayCoverage.PARTIAL,
            highlighted = highestDate != null && day.date == highestDate,
        )
    }

/** Width of a share bar: clamped to 0..1, and a positive share is never thinner than 2 %. */
internal fun shareBarFraction(fraction: Float): Float = when {
    fraction.isNaN() || fraction <= 0f -> 0f
    else -> fraction.coerceIn(0.02f, 1f)
}
