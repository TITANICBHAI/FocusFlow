package com.tbtechs.focusflow.analytics.phone

import kotlin.math.roundToInt

/** Distraction section: apps that pull time or attention. Pure; thresholds live in [PhoneThresholds]. */
object DistractionBuilder {
    private const val MAX_APPS = 8
    private const val MAX_REFLEX = 5
    private const val MAX_RISING = 3
    private const val MANY_OPENS_PER_DAY = 5.0
    private const val RISING_MIN_ACTIVE_DAYS = 3
    private const val MIN_TREND_DAYS = 4
    private const val MIN_PREVIOUS_AVG_MS = 5L * 60 * 1000

    fun build(
        apps: AppsSection,
        appDays: List<AppDayRow>,
        blockedByPackage: Map<String, Int>,
        categoryOf: (String) -> UsageCategory,
        previous: PreviousAppPeriod?,
    ): DistractionSection {
        val totals = HashMap<String, Long>()
        val hourly = HashMap<String, LongArray>()
        for (row in appDays) {
            totals[row.packageName] = (totals[row.packageName] ?: 0L) + row.foregroundMs
            val h = hourly.getOrPut(row.packageName) { LongArray(24) }
            for (i in 0 until 24) h[i] += row.hourlyMs.getOrElse(i) { 0L }
        }

        fun isDistraction(pkg: String) = categoryOf(pkg).isDistraction || (blockedByPackage[pkg] ?: 0) > 0
        val set = totals.keys.filter { isDistraction(it) }
        val distractionMs = set.sumOf { totals.getValue(it) }
        val share = if (apps.totalMs == 0L) null else BuilderUtils.percent(distractionMs, apps.totalMs)

        val byHour = List(24) { i -> set.sumOf { hourly.getValue(it)[i] } }

        val distractionApps = apps.apps
            .filter { isDistraction(it.packageName) }
            .sortedWith(compareByDescending<AppUsageRow> { it.totalMs }.thenBy { it.appName })
            .take(MAX_APPS)
            .map { row ->
                val blocked = blockedByPackage[row.packageName] ?: 0
                val reasons = buildList {
                    if (row.category.isDistraction) add(row.category.label)
                    if (blocked > 0) add("Blocked ${blocked}×")
                    if (row.activeDays > 0 && row.opens.toDouble() / row.activeDays >= MANY_OPENS_PER_DAY) {
                        add("${(row.opens.toDouble() / row.activeDays).roundToInt()} opens a day")
                    }
                }
                DistractionApp(
                    packageName = row.packageName, appName = row.appName, category = row.category,
                    totalMs = row.totalMs, sharePercent = row.sharePercent,
                    blockedAttempts = blocked, opens = row.opens, reasons = reasons,
                )
            }

        val reflex = apps.apps
            .filter { row ->
                row.activeDays >= PhoneThresholds.REFLEX_MIN_ACTIVE_DAYS && row.opens > 0 &&
                    row.opens.toDouble() / row.activeDays >= PhoneThresholds.REFLEX_MIN_OPENS_PER_DAY &&
                    row.sessionBucketCounts[0].toDouble() / row.opens >= PhoneThresholds.REFLEX_MIN_SHORT_SHARE
            }
            .sortedWith(
                compareByDescending<AppUsageRow> { it.opens.toDouble() / it.activeDays }.thenBy { it.appName },
            )
            .take(MAX_REFLEX)
            .map {
                ReflexApp(
                    packageName = it.packageName, appName = it.appName,
                    opensPerActiveDay = it.opens.toDouble() / it.activeDays,
                    shortSessionSharePercent = BuilderUtils.percent(it.sessionBucketCounts[0].toLong(), it.opens.toLong()),
                )
            }

        val rising = apps.apps
            .filter { row ->
                val prev = row.previousAvgPerDayMs
                row.changePercent != null && prev != null &&
                    prev >= PhoneThresholds.RISING_MIN_PREVIOUS_MS &&
                    row.activeDays >= RISING_MIN_ACTIVE_DAYS &&
                    row.avgPerDayMs.toDouble() >= PhoneThresholds.RISING_MIN_RATIO * prev &&
                    row.avgPerDayMs - prev >= PhoneThresholds.RISING_MIN_DELTA_MS
            }
            .sortedWith(
                compareByDescending<AppUsageRow> { it.avgPerDayMs - (it.previousAvgPerDayMs ?: 0L) }.thenBy { it.appName },
            )
            .take(MAX_RISING)
            .map {
                RisingApp(it.packageName, it.appName, it.avgPerDayMs, it.previousAvgPerDayMs ?: 0L, it.changePercent ?: 0)
            }

        var categoryChange: Int? = null
        if (previous != null && previous.daysWithData >= MIN_TREND_DAYS && apps.appDataDays >= MIN_TREND_DAYS) {
            val cur = totals.filterKeys { categoryOf(it).isDistraction }.values.sum() / apps.appDataDays
            val prev = previous.appMsByPackage.filterKeys { categoryOf(it).isDistraction }.values.sum() / previous.daysWithData
            if (prev >= MIN_PREVIOUS_AVG_MS) categoryChange = ((cur - prev) * 100.0 / prev).roundToInt()
        }

        return DistractionSection(
            distractionMs = distractionMs,
            sharePercent = share,
            apps = distractionApps,
            byHourMs = byHour,
            peakHour = BuilderUtils.peakIndex(byHour),
            reflexApps = reflex,
            risingApps = rising,
            changePercent = categoryChange,
        )
    }
}
