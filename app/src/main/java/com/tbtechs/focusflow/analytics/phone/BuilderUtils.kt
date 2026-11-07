package com.tbtechs.focusflow.analytics.phone

import kotlin.math.roundToInt

/** Small pure helpers shared by the report builders. */
internal object BuilderUtils {
    /** Index of the largest value (ties → earliest index), or null when every value is <= 0. */
    fun peakIndex(values: List<Long>): Int? {
        var best = -1
        var bestValue = 0L
        for (i in values.indices) {
            if (values[i] > bestValue) {
                bestValue = values[i]
                best = i
            }
        }
        return if (best < 0) null else best
    }

    fun peakIndexInt(values: List<Int>): Int? = peakIndex(values.map { it.toLong() })

    /** Element-wise sum of equally sized lists; [size] zeros when [lists] is empty. */
    fun sumLongs(lists: List<List<Long>>, size: Int): List<Long> =
        List(size) { i -> lists.sumOf { it.getOrElse(i) { 0L } } }

    fun sumInts(lists: List<List<Int>>, size: Int): List<Int> =
        List(size) { i -> lists.sumOf { it.getOrElse(i) { 0 } } }

    /** round-half-up percentage of [part] in [whole] (whole must be > 0). */
    fun percent(part: Long, whole: Long): Int = (part * 100.0 / whole).roundToInt()
}
