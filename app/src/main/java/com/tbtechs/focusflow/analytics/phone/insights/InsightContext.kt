package com.tbtechs.focusflow.analytics.phone.insights

import com.tbtechs.focusflow.analytics.phone.AppDayRow
import com.tbtechs.focusflow.analytics.phone.FocusSessionRow
import com.tbtechs.focusflow.analytics.phone.PhoneDay
import com.tbtechs.focusflow.analytics.phone.PhoneDayStatus
import com.tbtechs.focusflow.analytics.phone.PhoneText
import java.time.LocalDate

/** Inclusive date window. */
internal class Win(val start: LocalDate, val end: LocalDate) {
    val dates: List<LocalDate> by lazy {
        generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    }
    fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
    fun label(): String = "${PhoneText.weekdayDate(start)} – ${PhoneText.weekdayDate(end)}"
}

/** A total over `n` days; comparisons are done by cross-multiplication so ratio boundaries are exact. */
internal class Avg(val total: Long, val n: Int) {
    val value: Double get() = if (n == 0) 0.0 else total.toDouble() / n
    /** this ≥ (num/den) · other */
    fun atLeast(other: Avg, num: Int, den: Int): Boolean = total * other.n * den >= other.total * n * num
    /** this ≤ (num/den) · other */
    fun atMost(other: Avg, num: Int, den: Int): Boolean = total * other.n * den <= other.total * n * num
    /** this − other, per day */
    fun minus(other: Avg): Double = value - other.value
}

/**
 * Read-only view of the engine input. Only COMPLETE phone days count; a date with no app row counts as 0 minutes
 * only when that date is a COMPLETE day (otherwise the date is ignored).
 */
internal class InsightContext(val input: PhoneInsightInput) {
    val today: LocalDate = input.today

    val completeByDate: Map<LocalDate, PhoneDay> = input.days
        .filter { it.status == PhoneDayStatus.COMPLETE }
        .mapNotNull { day -> runCatching { LocalDate.parse(day.date) }.getOrNull()?.let { it to day } }
        .toMap()

    private val appRowsByDate: Map<LocalDate, List<AppDayRow>> = input.appDays
        .mapNotNull { row -> runCatching { LocalDate.parse(row.date) }.getOrNull()?.let { it to row } }
        .groupBy({ it.first }, { it.second })

    fun win(fromBack: Int, toBack: Int) = Win(today.minusDays(fromBack.toLong()), today.minusDays(toBack.toLong()))

    fun complete(window: Win): List<PhoneDay> = window.dates.mapNotNull { completeByDate[it] }
    fun completeDates(window: Win): List<LocalDate> = window.dates.filter { it in completeByDate }

    fun name(pkg: String): String = input.appNames[pkg]?.takeIf { it.isNotBlank() } ?: pkg

    /** Foreground ms per package summed over the given dates (callers pass COMPLETE dates only). */
    fun appTotals(dates: List<LocalDate>): Map<String, Long> {
        val totals = LinkedHashMap<String, Long>()
        for (date in dates) for (row in appRowsByDate[date].orEmpty()) {
            totals[row.packageName] = (totals[row.packageName] ?: 0L) + row.foregroundMs
        }
        return totals
    }

    /** Foreground ms of one package on one date (0 when there is no row). */
    fun appMsOn(pkg: String, date: LocalDate): Long = appRowsByDate[date].orEmpty().filter { it.packageName == pkg }.sumOf { it.foregroundMs }

    /** The hour of day (0–23) in which [pkg] has the most foreground time over [dates]; `null` when it has none. */
    fun peakHour(pkg: String, dates: List<LocalDate>): Int? {
        val perHour = LongArray(24)
        for (date in dates) for (row in appRowsByDate[date].orEmpty()) {
            if (row.packageName != pkg) continue
            for (hour in 0 until 24) perHour[hour] += row.hourlyMs.getOrElse(hour) { 0L }
        }
        val best = perHour.indices.maxByOrNull { perHour[it] } ?: return null // first maximum wins
        return best.takeIf { perHour[it] > 0L }
    }

    fun appRows(date: LocalDate): List<AppDayRow> = appRowsByDate[date].orEmpty()
    val appRowDates: Set<LocalDate> get() = appRowsByDate.keys

    private fun startMs(date: LocalDate): Long = date.atStartOfDay(input.zone).toInstant().toEpochMilli()

    private fun overlap(session: FocusSessionRow, window: Win): Long {
        val wStart = startMs(window.start)
        val wEnd = startMs(window.end.plusDays(1))
        val end = session.endedAtMs ?: wEnd // a still-running session is counted to the end of the window
        return (minOf(end, wEnd) - maxOf(session.startedAtMs, wStart)).coerceAtLeast(0L)
    }

    fun focusMs(window: Win): Long = input.focusSessions.sumOf { overlap(it, window) }

    fun blockedAttempts(window: Win): Int = input.blockedDays.sumOf { row ->
        val date = runCatching { LocalDate.parse(row.date) }.getOrNull()
        if (date != null && window.contains(date)) row.attempts else 0
    }

    /** Ended sessions that overlap the window. */
    fun endedSessions(window: Win): List<FocusSessionRow> =
        input.focusSessions.filter { it.endedAtMs != null && overlap(it, window) > 0L }

    fun lateNightMs(day: PhoneDay): Long = InsightThresholds.LATE_NIGHT_HOURS.sumOf { day.screenOnHourlyMs.getOrElse(it) { 0L } }
}

internal fun confidence(sample: Int, min: Int): InsightConfidence =
    if (sample >= InsightThresholds.HIGH_CONFIDENCE_FACTOR * min) InsightConfidence.HIGH else InsightConfidence.MEDIUM

internal fun evidence(what: String, how: String, period: String, evidence: String): List<String> =
    listOf("What: $what", "How: $how", "Period: $period", "Evidence: $evidence")

internal fun days(n: Int): String = PhoneText.count(n, "complete day")
