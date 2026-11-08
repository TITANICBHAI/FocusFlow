package com.tbtechs.focusflow.ui.stats.phone

import com.tbtechs.focusflow.analytics.ANALYTICS_ALL_TIME
import com.tbtechs.focusflow.analytics.ANALYTICS_THREE_MONTHS
import com.tbtechs.focusflow.analytics.ANALYTICS_TODAY
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import com.tbtechs.focusflow.analytics.ANALYTICS_YESTERDAY
import com.tbtechs.focusflow.analytics.phone.DayCoverage
import com.tbtechs.focusflow.analytics.phone.DayPoint
import com.tbtechs.focusflow.analytics.phone.PhonePeriod
import com.tbtechs.focusflow.analytics.phone.PhoneReport
import com.tbtechs.focusflow.analytics.phone.PhoneText
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Pure helpers for three small extras on the Stats screen: weekday pattern, part-of-day split, share summary. */

// ---------------------------------------------------------------------------------------------- weekday pattern

/** Fewest complete days before the weekday chart is worth showing (about two of each weekday). */
internal const val WEEKDAY_CHART_MIN_COMPLETE_DAYS = 14

/** Fewest complete days of one weekday before its bar is drawn at full strength. */
internal const val WEEKDAY_MIN_SAMPLES = 2

internal data class WeekdayAverage(val day: DayOfWeek, val avgMs: Long?, val days: Int)

/** Average screen time for each weekday, Monday first, from COMPLETE days only. */
internal fun weekdayAverages(days: List<DayPoint>): List<WeekdayAverage> {
    val complete = days.filter { it.coverage == DayCoverage.COMPLETE }
    return DayOfWeek.entries.map { dow ->
        val mine = complete.filter { it.date.dayOfWeek == dow }
        WeekdayAverage(dow, if (mine.isEmpty()) null else mine.sumOf { it.screenOnMs } / mine.size, mine.size)
    }
}

internal fun showWeekdayChart(days: List<DayPoint>): Boolean =
    days.count { it.coverage == DayCoverage.COMPLETE } >= WEEKDAY_CHART_MIN_COMPLETE_DAYS

/** The weekday with the highest average (the earliest in the week on a tie), or `null` when nothing is measured. */
internal fun highestWeekday(avgs: List<WeekdayAverage>): WeekdayAverage? =
    avgs.filter { it.avgMs != null }.maxWithOrNull(compareBy<WeekdayAverage> { it.avgMs }.thenByDescending { it.day.value })

internal fun weekdayBarPoints(avgs: List<WeekdayAverage>): List<DayBarPoint> {
    val top = highestWeekday(avgs)?.day
    return avgs.map { a ->
        DayBarPoint(
            label = a.day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
            value = (a.avgMs ?: 0L).toFloat(),
            dimmed = a.avgMs == null || a.days < WEEKDAY_MIN_SAMPLES,
            highlighted = a.day == top,
        )
    }
}

internal fun weekdayCaption(avgs: List<WeekdayAverage>): String? {
    val top = highestWeekday(avgs) ?: return null
    val name = top.day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    return "Highest on ${name}s · ${PhoneText.duration(top.avgMs ?: 0L)} on average"
}

// ---------------------------------------------------------------------------------------------- part of the day

private fun periodHours(p: PhonePeriod): String = when (p) {
    PhonePeriod.MORNING -> "5 AM – 12 PM"
    PhonePeriod.AFTERNOON -> "12 – 5 PM"
    PhonePeriod.EVENING -> "5 – 10 PM"
    PhonePeriod.LATE_NIGHT -> "10 PM – 5 AM"
}

/** Screen-on time split across morning / afternoon / evening / late night; empty when there is no screen time. */
internal fun partOfDayRows(hourlyMs: List<Long>, window: String): List<TopAppRow> {
    val perPeriod = LongArray(PhonePeriod.entries.size)
    hourlyMs.forEachIndexed { hour, ms -> if (hour in 0..23) perPeriod[PhonePeriod.of(hour).ordinal] += ms.coerceAtLeast(0L) }
    val total = perPeriod.sum()
    if (total <= 0L) return emptyList()
    val suffix = if (isSingleDay(window)) "" else " a day"
    return PhonePeriod.entries.map { p ->
        val ms = perPeriod[p.ordinal]
        val share = ms * 100.0 / total
        TopAppRow(
            title = p.label + " · " + periodHours(p),
            subtitle = PhoneText.duration(ms) + suffix,
            fraction = (share / 100.0).toFloat().coerceIn(0f, 1f),
            trailing = PhoneText.percent(share.roundToInt()),
        )
    }
}

// ---------------------------------------------------------------------------------------------- share summary

internal fun windowPhrase(window: String): String = when (window) {
    ANALYTICS_TODAY -> "today"
    ANALYTICS_YESTERDAY -> "yesterday"
    ANALYTICS_WEEK -> "this week"
    ANALYTICS_THREE_MONTHS -> "over 3 months"
    ANALYTICS_ALL_TIME -> "all time"
    else -> ""
}

/** A short plain-text summary the person can send to someone. Only lines with real data are included. */
internal fun shareSummaryText(report: PhoneReport, window: String): String {
    val start = PhoneText.weekdayDate(report.range.start)
    val end = PhoneText.weekdayDate(report.range.end)
    val range = if (report.range.start == report.range.end) start else "$start – $end"
    val phrase = windowPhrase(window)
    val lines = mutableListOf<String>()
    lines += "My phone" + (if (phrase.isEmpty()) "" else " $phrase") + " ($range)"

    val hero = screenTimeHero(report.screenTime, window)
    if (hero.value != "—") lines += "Screen time: ${hero.value} ${hero.caption}"

    unlocksHero(report.behaviour, window)?.let { lines += "Phone: ${it.value} ${it.caption}" }

    val top = report.apps.apps.filter { it.totalMs > 0L }.take(3)
    if (top.isNotEmpty()) lines += "Most used: " + top.joinToString(", ") { "${it.appName} (${PhoneText.duration(it.totalMs)})" }

    busiestCaption(report.screenTime.peakHour, window)?.let { lines += it }
    lines += "Shared from FocusFlow"
    return lines.joinToString("\n")
}
