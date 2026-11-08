package com.tbtechs.focusflow.ui.stats.phone

import com.tbtechs.focusflow.analytics.phone.AppUsageRow
import com.tbtechs.focusflow.analytics.phone.AppsSection
import com.tbtechs.focusflow.analytics.phone.DayCoverage
import com.tbtechs.focusflow.analytics.phone.DayPoint
import com.tbtechs.focusflow.analytics.phone.PhonePeriod
import com.tbtechs.focusflow.analytics.phone.PhoneText
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure wording and ordering for the Extra · Apps tab. */
enum class AppSort(val label: String) { TIME("Time"), OPENS("Opens"), AVG_SESSION("Avg session"), TREND("Trend") }

/** How many apps the list shows before "Show more", and the most it ever shows. */
internal const val APPS_PAGE_SIZE = 15
internal const val APPS_MAX_SHOWN = 30

/** Most days in an app's "Last days" bars. */
internal const val APP_DAILY_MAX_DAYS = 14

private val NAME_ORDER: Comparator<AppUsageRow> = Comparator { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.appName, b.appName) }

internal fun sortApps(rows: List<AppUsageRow>, sort: AppSort): List<AppUsageRow> {
    val primary: Comparator<AppUsageRow> = when (sort) {
        AppSort.TIME -> compareByDescending { it.totalMs }
        AppSort.OPENS -> compareByDescending { it.opens }
        AppSort.AVG_SESSION -> compareByDescending { it.avgSessionMs }
        // null last: a missing change sorts below every real one.
        AppSort.TREND -> Comparator { a, b ->
            val x = a.changePercent
            val y = b.changePercent
            when {
                x == null && y == null -> 0
                x == null -> 1
                y == null -> -1
                else -> y.compareTo(x)
            }
        }
    }
    return rows.sortedWith(primary.then(NAME_ORDER))
}

internal fun appRowSummary(row: AppUsageRow): String =
    PhoneText.duration(row.totalMs) + " · " + PhoneText.percent(row.sharePercent.roundToInt()) + " · " +
        PhoneText.count(row.opens, "open") + " · avg " + PhoneText.duration(row.avgSessionMs)

internal fun trendText(row: AppUsageRow): String? {
    val change = row.changePercent ?: return null
    return when {
        change > 0 -> "▲ ${PhoneText.percent(change)} vs previous period"
        change < 0 -> "▼ ${PhoneText.percent(abs(change))} vs previous period"
        else -> "● no change vs previous period"
    }
}

internal fun periodBarLabels(): List<String> = PhonePeriod.entries.map { it.label }

/** Minutes of use in each time-of-day period, rounded to the nearest minute, for the "Time of day" bars. */
internal fun periodMinutes(row: AppUsageRow): List<Int> = row.periodMs.map { ((it.coerceAtLeast(0L) + 30_000L) / 60_000L).toInt() }

internal fun appsCoverageNote(section: AppsSection): String =
    "Based on ${PhoneText.count(section.appDataDays, "day")} of app data."

/** "Active 4 of 5 days" and, when known, "First app after 12 unlocks". */
internal fun appDetailLines(row: AppUsageRow, appDataDays: Int): List<String> = buildList {
    add("Active ${row.activeDays} of $appDataDays days")
    if (row.firstAfterUnlock > 0) add("First app after ${PhoneText.count(row.firstAfterUnlock, "unlock")}")
}

/** The app's last [APP_DAILY_MAX_DAYS] days as bars; [dailyMs] is aligned with [days] (oldest first). */
internal fun appDailyPoints(row: AppUsageRow, days: List<DayPoint>): List<DayBarPoint> {
    val n = minOf(row.dailyMs.size, days.size)
    if (n == 0) return emptyList()
    val take = minOf(n, APP_DAILY_MAX_DAYS)
    val firstDay = days.size - take // days is the full list; use its tail so dates match the values' tail
    val firstValue = row.dailyMs.size - take
    return (0 until take).map { i ->
        val day = days[firstDay + i]
        DayBarPoint(
            label = dayBarLabel(day.date, take, i),
            value = row.dailyMs[firstValue + i].toFloat(),
            dimmed = day.coverage == DayCoverage.MISSING || day.coverage == DayCoverage.PARTIAL,
            highlighted = false,
        )
    }
}

/** How many rows the list shows. */
internal fun visibleAppCount(total: Int, showAll: Boolean): Int = minOf(total, if (showAll) APPS_MAX_SHOWN else APPS_PAGE_SIZE)
