package com.tbtechs.focusflow.analytics.phone.insights

import com.tbtechs.focusflow.analytics.phone.PhoneDay
import com.tbtechs.focusflow.analytics.phone.PhoneText
import java.time.LocalDate
import com.tbtechs.focusflow.analytics.phone.insights.InsightThresholds as T

/** Modules 11 and 13: personal records and data notes. */
internal object RecordInsights {

    private fun record(
        c: InsightContext, key: String, title: String, message: String, date: LocalDate?,
        what: String, how: String, period: String, evidence: String, sample: Int, min: Int,
    ): PhoneInsight {
        val isNew = date != null && !date.isBefore(c.today.minusDays(T.RECORD_NEW_WITHIN_DAYS.toLong())) && !date.isAfter(c.today)
        return PhoneInsight(
            id = "record:$key", kind = InsightKind.RECORD, group = InsightGroup.RECORD,
            title = title, message = message,
            evidence = evidence(what, how, period, evidence),
            direction = null, confidence = confidence(sample, min),
            priority = T.RECORD_PRIORITY + if (isNew) T.RECORD_NEW_BONUS else 0,
            isNew = isNew,
        )
    }

    fun records(c: InsightContext): List<PhoneInsight> {
        val out = ArrayList<PhoneInsight>()
        val complete = c.completeByDate.entries.toList()
        val completeCount = complete.size
        val allPeriod = if (complete.isEmpty()) "" else "${PhoneText.weekdayDate(complete.minOf { it.key })} – ${PhoneText.weekdayDate(complete.maxOf { it.key })}"

        if (completeCount >= T.RECORD_MIN_DAYS) {
            val highest = complete.sortedWith(compareByDescending<Map.Entry<LocalDate, PhoneDay>> { it.value.screenOnMs }.thenByDescending { it.key }).first()
            out += record(
                c, "highest_day", "Highest screen-time day",
                "${PhoneText.duration(highest.value.screenOnMs)} on ${PhoneText.weekdayDate(highest.key)}.", highest.key,
                "Your highest daily screen time", "Largest screen-on total over complete days", allPeriod, days(completeCount),
                completeCount, T.RECORD_MIN_DAYS,
            )
            val lowest = complete.sortedWith(compareBy<Map.Entry<LocalDate, PhoneDay>> { it.value.screenOnMs }.thenByDescending { it.key }).first()
            out += record(
                c, "lowest_day", "Lowest screen-time day",
                "${PhoneText.duration(lowest.value.screenOnMs)} on ${PhoneText.weekdayDate(lowest.key)}.", lowest.key,
                "Your lowest daily screen time", "Smallest screen-on total over complete days", allPeriod, days(completeCount),
                completeCount, T.RECORD_MIN_DAYS,
            )
        }

        val signal = complete.filter { it.value.unlockSignal }
        if (signal.size >= T.RECORD_MIN_DAYS) {
            val most = signal.sortedWith(compareByDescending<Map.Entry<LocalDate, PhoneDay>> { it.value.unlockCount }.thenByDescending { it.key }).first()
            if (most.value.unlockCount > 0) {
                out += record(
                    c, "most_unlocks_day", "Most unlocks in a day",
                    "${PhoneText.count(most.value.unlockCount, "unlock")} on ${PhoneText.weekdayDate(most.key)}.", most.key,
                    "Your most unlocks in one day", "Largest unlock count over complete days that report unlocks", allPeriod,
                    days(signal.size), signal.size, T.RECORD_MIN_DAYS,
                )
            }
        }

        if (completeCount >= T.RECORD_PHONE_SESSION_MIN_DAYS) {
            val longest = complete.sortedWith(compareByDescending<Map.Entry<LocalDate, PhoneDay>> { it.value.longestSessionMs }.thenByDescending { it.key }).first()
            if (longest.value.longestSessionMs > 0L) {
                out += record(
                    c, "longest_phone_session", "Longest phone session",
                    "${PhoneText.duration(longest.value.longestSessionMs)} on ${PhoneText.weekdayDate(longest.key)}.", longest.key,
                    "Your longest single phone session", "Longest session over complete days", allPeriod, days(completeCount),
                    completeCount, T.RECORD_PHONE_SESSION_MIN_DAYS,
                )
            }
        }

        val bestSession = c.input.appSessions
            .filter { it.durationMs > 0L && runCatching { LocalDate.parse(it.date) }.isSuccess }
            .sortedWith(compareByDescending<com.tbtechs.focusflow.analytics.phone.AppSessionRow> { it.durationMs }.thenByDescending { it.startedAtMs })
            .firstOrNull()
        if (bestSession != null) {
            val date = LocalDate.parse(bestSession.date)
            out += record(
                c, "longest_app_session", "Longest app session",
                "${c.name(bestSession.packageName)} for ${PhoneText.duration(bestSession.durationMs)} on ${PhoneText.weekdayDate(date)}.", date,
                "Your longest single app session", "Longest app session on record", PhoneText.weekdayDate(date),
                PhoneText.count(c.input.appSessions.size, "app session"), c.input.appSessions.size, 1,
            )
        }

        val totals = c.input.appDays.groupBy { it.packageName }.mapValues { (_, rows) -> rows.sumOf { it.foregroundMs } }
        val topApp = totals.entries.filter { it.value > 0L }
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key }).firstOrNull()
        if (topApp != null) {
            val dayCount = c.input.appDays.map { it.date }.distinct().size
            out += record(
                c, "most_used_app", "Most-used app",
                "${c.name(topApp.key)}: ${PhoneText.duration(topApp.value)} over $dayCount days.", null,
                "The app you've used most", "Total time in each app over all days with app data", "All days with app data",
                "$dayCount days of app data", dayCount, 1,
            )
        }

        if (completeCount >= T.RECORD_BUSIEST_HOUR_MIN_DAYS) {
            val hourTotals = LongArray(24)
            for ((_, day) in complete) for (h in 0 until 24) hourTotals[h] += day.screenOnHourlyMs.getOrElse(h) { 0L }
            val best = (0 until 24).maxByOrNull { hourTotals[it] }!!
            if (hourTotals[best] > 0L) {
                val minutes = InsightStats.roundHalfUp(hourTotals[best].toDouble() / completeCount / T.MINUTE)
                out += record(
                    c, "busiest_hour", "Busiest hour",
                    "Around ${PhoneText.hour(best)}, your screen is on $minutes minutes of the hour on average.", null,
                    "The hour of the day your screen is on most", "Mean screen-on time per hour over complete days", allPeriod,
                    days(completeCount), completeCount, T.RECORD_BUSIEST_HOUR_MIN_DAYS,
                )
            }
        }
        return out.take(T.RECORD_MAX)
    }

    fun notes(c: InsightContext): List<PhoneInsight> {
        val out = ArrayList<PhoneInsight>()
        val completeCount = c.completeByDate.size
        if (completeCount < T.NOTE_HISTORY_TARGET_DAYS) {
            out += PhoneInsight(
                id = "note:history", kind = InsightKind.DATA_NOTE, group = InsightGroup.NOTE,
                title = "Still building your history",
                message = "FocusFlow has ${PhoneText.count(completeCount, "full day")} of phone data. Insights get sharper after ${T.NOTE_HISTORY_TARGET_DAYS}.",
                evidence = evidence("How much history the insights can use", "Counted complete days only", "All stored days", days(completeCount)),
                direction = null, confidence = InsightConfidence.HIGH, priority = T.NOTE_HISTORY_PRIORITY,
            )
        }
        val win = c.win(T.LAST14_FROM, T.LAST14_TO)
        val last = c.complete(win)
        if (last.isNotEmpty() && last.none { it.unlockSignal } &&
            last.sumOf { it.wakeCount } >= T.NOTE_UNLOCKS_MIN_WAKES_PER_DAY * last.size
        ) {
            out += PhoneInsight(
                id = "note:unlocks", kind = InsightKind.DATA_NOTE, group = InsightGroup.NOTE,
                title = "Unlock counts aren't available",
                message = "This phone doesn't report unlocks (is a screen lock set?). Screen wake-ups are shown instead.",
                evidence = evidence("Whether this phone reports unlocks", "No unlock events on any complete day while the screen woke up regularly", win.label(), days(last.size)),
                direction = null, confidence = InsightConfidence.HIGH, priority = T.NOTE_UNLOCKS_PRIORITY,
            )
        }
        return out
    }
}
