package com.tbtechs.focusflow.analytics.phone

import java.time.DayOfWeek

/** Builds [ScreenTimeSection] from stored/live phone days and per-app rows. Pure: no clock, no Android. */
object ScreenTimeBuilder {

    fun build(range: PhoneDateRange, phoneDays: List<PhoneDay>, appDays: List<AppDayRow>): ScreenTimeSection {
        val byDate = phoneDays.associateBy { it.date }
        val appMsByDate = HashMap<String, Long>()
        for (row in appDays) appMsByDate[row.date] = (appMsByDate[row.date] ?: 0L) + row.foregroundMs

        val points = range.dates().map { date ->
            val key = date.toString()
            val day = byDate[key]
            DayPoint(
                date = date,
                coverage = when (day?.status) {
                    null -> DayCoverage.MISSING
                    PhoneDayStatus.COMPLETE -> DayCoverage.COMPLETE
                    PhoneDayStatus.PARTIAL -> DayCoverage.PARTIAL
                    PhoneDayStatus.LIVE -> DayCoverage.LIVE
                },
                screenOnMs = day?.screenOnMs ?: 0L,
                appMs = appMsByDate[key] ?: 0L,
                unlocks = day?.unlockCount ?: 0,
                wakes = day?.wakeCount ?: 0,
            )
        }

        val totalScreenOn = points.filter { it.coverage != DayCoverage.MISSING }.sumOf { it.screenOnMs }
        val totalApp = points.sumOf { it.appMs }

        val complete = points.filter { it.coverage == DayCoverage.COMPLETE }
        val live = points.filter { it.coverage == DayCoverage.LIVE }
        val (basis, baseline) = when {
            complete.isNotEmpty() -> AverageBasis.COMPLETE_DAYS to complete
            live.isNotEmpty() -> AverageBasis.TODAY_SO_FAR to live
            else -> AverageBasis.NONE to emptyList()
        }
        val n = baseline.size
        val avgScreenOn = if (n == 0) null else baseline.sumOf { it.screenOnMs } / n
        val avgApp = if (n == 0 || baseline.all { it.appMs == 0L }) null else baseline.sumOf { it.appMs } / n

        val weekdayDays = complete.filter { it.date.dayOfWeek !in WEEKEND }
        val weekendDays = complete.filter { it.date.dayOfWeek in WEEKEND }
        val groupsOk = weekdayDays.size >= 2 && weekendDays.size >= 1
        fun group(days: List<DayPoint>) = DayGroupAverage(days.sumOf { it.screenOnMs } / days.size, days.size)

        val extremes = complete.size >= 3
        val highest = if (extremes) complete.maxWith(compareBy<DayPoint> { it.screenOnMs }.thenBy { it.date }) else null
        val lowest = if (extremes) complete.minWith(compareBy<DayPoint> { it.screenOnMs }.thenByDescending { it.date }) else null

        val baselineDays = baseline.mapNotNull { byDate[it.date.toString()] }
        val hourlyAvg = if (n == 0) List(24) { 0L }
        else BuilderUtils.sumLongs(baselineDays.map { it.screenOnHourlyMs }, 24).map { it / n }

        return ScreenTimeSection(
            totalScreenOnMs = totalScreenOn,
            totalAppMs = totalApp,
            averageBasis = basis,
            averageDays = n,
            avgScreenOnPerDayMs = avgScreenOn,
            avgAppPerDayMs = avgApp,
            weekday = if (groupsOk) group(weekdayDays) else null,
            weekend = if (groupsOk) group(weekendDays) else null,
            highest = highest,
            lowest = lowest,
            screenOnByHourAvgMs = hourlyAvg,
            peakHour = BuilderUtils.peakIndex(hourlyAvg),
            days = points,
        )
    }

    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
}
