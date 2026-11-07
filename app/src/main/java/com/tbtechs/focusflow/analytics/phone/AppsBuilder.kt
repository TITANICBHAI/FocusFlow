package com.tbtechs.focusflow.analytics.phone

import kotlin.math.roundToInt

/** Previous comparison window for app trends: how many days had app data, and the time per package. */
data class PreviousAppPeriod(val daysWithData: Int, val appMsByPackage: Map<String, Long>)

data class AppsInput(
    val range: PhoneDateRange,
    val appDays: List<AppDayRow>,
    val appSessions: List<AppSessionRow>,
    val firstAppUnlocks: Map<String, Int>,        // package -> unlocks in the range
    val previous: PreviousAppPeriod?,
    val categoryOf: (String) -> UsageCategory,
)

/** Per-app analysis, categories and time-of-day habits. Pure: categories arrive through [AppsInput.categoryOf]. */
object AppsBuilder {
    private const val MAX_APPS = 30
    private const val TOP_APPS_PER_PERIOD = 3
    private const val MIN_TREND_DAYS = 4
    private const val MIN_PREVIOUS_AVG_MS = 5L * 60 * 1000

    private class Acc(size: Int) {
        var name: String = ""
        var totalMs = 0L
        val daily = LongArray(size)
        val period = LongArray(PhonePeriod.entries.size)
        var rowSessions = 0
    }

    fun build(input: AppsInput): AppsSection {
        val dates = input.range.dates()
        val dateIndex = dates.mapIndexed { i, d -> d.toString() to i }.toMap()

        val accs = LinkedHashMap<String, Acc>()
        val dataDates = HashSet<String>()
        // Oldest first so that "latest non-blank name" really is the latest.
        for (row in input.appDays.sortedBy { it.date }) {
            val idx = dateIndex[row.date] ?: continue
            val acc = accs.getOrPut(row.packageName) { Acc(dates.size) }
            if (row.appName.isNotBlank()) acc.name = row.appName
            acc.totalMs += row.foregroundMs
            acc.daily[idx] += row.foregroundMs
            acc.rowSessions += row.sessionCount
            for (h in 0 until 24) acc.period[PhonePeriod.of(h).ordinal] += row.hourlyMs.getOrElse(h) { 0L }
            if (row.foregroundMs > 0) dataDates += row.date
        }
        accs.values.removeAll { it.totalMs <= 0L }

        val sessionsByPkg = input.appSessions
            .filter { it.date in dateIndex && it.durationMs >= PhoneThresholds.MIN_OPEN_MS }
            .groupBy { it.packageName }

        val appDataDays = dataDates.size
        val totalAll = accs.values.sumOf { it.totalMs }

        val prev = input.previous
        val trendOk = prev != null && prev.daysWithData >= MIN_TREND_DAYS && appDataDays >= MIN_TREND_DAYS

        val rows = accs.map { (pkg, acc) ->
            val sessions = sessionsByPkg[pkg].orEmpty()
            val opens: Int
            val buckets = IntArray(PhoneBuckets.COUNT)
            val longest: Long
            val avgSession: Long
            if (sessions.isNotEmpty()) {
                opens = sessions.size
                for (s in sessions) buckets[PhoneBuckets.indexFor(s.durationMs)]++
                longest = sessions.maxOf { it.durationMs }
                avgSession = sessions.sumOf { it.durationMs } / opens
            } else if (acc.rowSessions > 0) {
                opens = acc.rowSessions
                longest = 0L
                avgSession = acc.totalMs / opens
            } else {
                opens = 0
                longest = 0L
                avgSession = 0L
            }
            val avgPerDay = acc.totalMs / maxOf(1, appDataDays)
            val previousAvg = if (trendOk) (prev!!.appMsByPackage[pkg] ?: 0L) / prev.daysWithData else null
            val change = if (previousAvg != null && previousAvg >= MIN_PREVIOUS_AVG_MS) {
                ((avgPerDay - previousAvg) * 100.0 / previousAvg).roundToInt()
            } else null
            AppUsageRow(
                packageName = pkg,
                appName = acc.name.ifBlank { pkg },
                category = input.categoryOf(pkg),
                totalMs = acc.totalMs,
                sharePercent = if (totalAll == 0L) 0.0 else acc.totalMs * 100.0 / totalAll,
                opens = opens,
                avgSessionMs = avgSession,
                longestSessionMs = longest,
                sessionBucketCounts = buckets.toList(),
                periodMs = acc.period.toList(),
                dailyMs = acc.daily.toList(),
                activeDays = acc.daily.count { it > 0L },
                avgPerDayMs = avgPerDay,
                previousAvgPerDayMs = previousAvg,
                changePercent = change,
                firstAfterUnlock = input.firstAppUnlocks[pkg] ?: 0,
            )
        }.sortedWith(compareByDescending<AppUsageRow> { it.totalMs }.thenBy { it.appName })

        val listed = rows.take(MAX_APPS)
        val others = rows.drop(MAX_APPS).sumOf { it.totalMs }

        val categories = rows.groupBy { it.category }
            .map { (cat, list) ->
                val ms = list.sumOf { it.totalMs }
                CategoryShare(cat, ms, if (totalAll == 0L) 0.0 else ms * 100.0 / totalAll)
            }
            .filter { it.totalMs > 0L }
            .sortedWith(compareByDescending<CategoryShare> { it.totalMs }.thenBy { it.category.ordinal })

        val periodTotals = PhonePeriod.entries.map { p -> rows.sumOf { it.periodMs[p.ordinal] } }
        val periodSum = periodTotals.sum()
        val periods = PhonePeriod.entries.map { p ->
            val top = rows
                .filter { it.periodMs[p.ordinal] > 0L }
                .sortedWith(compareByDescending<AppUsageRow> { it.periodMs[p.ordinal] }.thenBy { it.appName })
                .take(TOP_APPS_PER_PERIOD)
                .map { AppShare(it.packageName, it.appName, it.periodMs[p.ordinal]) }
            PeriodShare(
                period = p,
                totalMs = periodTotals[p.ordinal],
                sharePercent = if (periodSum == 0L) 0.0 else periodTotals[p.ordinal] * 100.0 / periodSum,
                topApps = top,
            )
        }

        return AppsSection(
            totalMs = totalAll,
            appDataDays = appDataDays,
            apps = listed,
            othersMs = others,
            categories = categories,
            periods = periods,
        )
    }
}
