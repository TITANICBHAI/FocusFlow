package com.tbtechs.focusflow.analytics.phone.insights

import kotlin.math.floor
import kotlin.math.sqrt

/** Small, pure statistics helpers for the insight engine. */
object InsightStats {
    /** Median; the average of the two middle values (integer division) for an even count; 0 for empty. */
    fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    fun mean(values: List<Long>): Double = if (values.isEmpty()) 0.0 else values.sum().toDouble() / values.size

    /** Population standard deviation; 0 for fewer than one value. */
    fun stdev(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.sum() / values.size
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    /** Whole-number rounding where .5 goes up (also for negatives: −12.5 → −12). */
    fun roundHalfUp(value: Double): Int = floor(value + 0.5).toInt()

    /** `(now − before) / before` as a whole percent, rounded half up; null when `before == 0.0`. */
    fun percentChange(now: Double, before: Double): Int? =
        if (before == 0.0) null else roundHalfUp((now - before) / before * 100.0)
}
