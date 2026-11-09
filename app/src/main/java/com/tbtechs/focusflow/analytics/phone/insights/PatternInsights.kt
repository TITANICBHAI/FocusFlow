package com.tbtechs.focusflow.analytics.phone.insights

import com.tbtechs.focusflow.analytics.phone.PhonePeriod
import com.tbtechs.focusflow.analytics.phone.PhoneText
import com.tbtechs.focusflow.analytics.phone.TrendDirection
import java.time.Instant
import com.tbtechs.focusflow.analytics.phone.insights.InsightThresholds as T

/** Modules 7–10: first-app pattern, checking habit, time-of-day habit, consistency. */
internal object PatternInsights {

    fun firstApp(c: InsightContext): List<PhoneInsight> {
        val win = c.win(T.LAST14_FROM, T.LAST14_TO)
        val complete = c.complete(win)
        val total = complete.sumOf { it.unlockCount }
        if (total < T.FIRST_APP_MIN_UNLOCKS) return emptyList()

        val counts = HashMap<String, Int>()
        for (day in complete) for ((pkg, n) in day.firstApps) counts[pkg] = (counts[pkg] ?: 0) + n
        val ranked = counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        val top = ranked.firstOrNull() ?: return emptyList()
        if (top.value * 100 < T.FIRST_APP_MIN_SHARE_PERCENT * total) return emptyList()

        val name = c.name(top.key)
        var message = "After ${InsightStats.roundHalfUp(top.value * 100.0 / total)}% of unlocks, $name was the first app you opened (last 14 days)."
        val second = ranked.getOrNull(1)
        if (second != null && second.value * 100 >= T.FIRST_APP_SECOND_SHARE_PERCENT * total) {
            message += " ${c.name(second.key)} follows at ${InsightStats.roundHalfUp(second.value * 100.0 / total)}%."
        }
        return listOf(
            PhoneInsight(
                id = "firstapp:${top.key}", kind = InsightKind.FIRST_APP_PATTERN, group = InsightGroup.PATTERN,
                title = "You often reach for $name first", message = message,
                evidence = evidence(
                    "The first app opened after an unlock",
                    "Share of all unlocks over complete days",
                    win.label(),
                    "${PhoneText.count(total, "unlock")} on ${days(complete.size)}",
                ),
                direction = null,
                confidence = confidence(total, T.FIRST_APP_MIN_UNLOCKS),
                priority = T.FIRST_APP_PRIORITY,
            ),
        )
    }

    fun checkingHabit(c: InsightContext): List<PhoneInsight> {
        val win = c.win(T.LAST14_FROM, T.LAST14_TO)
        val signal = c.complete(win).filter { it.unlockSignal }
        val sessions = signal.sumOf { it.sessionCount }
        if (sessions < T.CHECKING_MIN_SESSIONS) return emptyList()
        val quick = signal.sumOf { day -> day.sessionBucketCounts.take(T.CHECKING_QUICK_BUCKETS).sum() }
        if (quick * 100 < T.CHECKING_MIN_QUICK_SHARE_PERCENT * sessions) return emptyList()
        if (quick < T.CHECKING_MIN_QUICK_PER_DAY * signal.size) return emptyList()

        val share = InsightStats.roundHalfUp(quick * 100.0 / sessions)
        val perDay = InsightStats.roundHalfUp(quick.toDouble() / signal.size)
        return listOf(
            PhoneInsight(
                id = "checking:${c.today.minusDays(1)}", kind = InsightKind.CHECKING_HABIT, group = InsightGroup.PATTERN,
                title = "Most unlocks are quick checks",
                message = "$share% of your phone sessions last under a minute — about $perDay quick checks a day.",
                evidence = evidence(
                    "How long each phone session lasts",
                    "Sessions under one minute as a share of all sessions",
                    win.label(),
                    "${PhoneText.count(sessions, "session")} on ${days(signal.size)}",
                ),
                direction = null,
                confidence = confidence(sessions, T.CHECKING_MIN_SESSIONS),
                priority = T.CHECKING_PRIORITY,
            ),
        )
    }

    private fun periodHours(period: PhonePeriod) = when (period) {
        PhonePeriod.MORNING -> "5 AM–noon"
        PhonePeriod.AFTERNOON -> "noon–5 PM"
        PhonePeriod.EVENING -> "5–10 PM"
        PhonePeriod.LATE_NIGHT -> "10 PM–5 AM"
    }

    fun timeOfDayHabit(c: InsightContext): List<PhoneInsight> {
        val win = c.win(T.LAST14_FROM, T.LAST14_TO)
        val dates = c.completeDates(win)
        if (dates.isEmpty()) return emptyList()
        val totals = c.appTotals(dates)
        val top = totals.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(T.TOD_TOP_APPS)
            .filter { it.value >= T.TOD_MIN_APP_MS }

        val out = ArrayList<PhoneInsight>()
        for ((pkg, total) in top) {
            val perPeriod = LongArray(PhonePeriod.values().size)
            for (date in dates) for (row in c.appRows(date)) {
                if (row.packageName != pkg) continue
                for (hour in 0 until 24) perPeriod[PhonePeriod.of(hour).ordinal] += row.hourlyMs.getOrElse(hour) { 0L }
            }
            val periodTotal = perPeriod.sum()
            if (periodTotal <= 0L) continue
            val bestIndex = perPeriod.indices.maxByOrNull { perPeriod[it] }!! // first maximum wins
            if (perPeriod[bestIndex] * 100 < T.TOD_MIN_SHARE_PERCENT * periodTotal) continue
            val period = PhonePeriod.values()[bestIndex]
            val name = c.name(pkg)
            val label = period.label.lowercase()
            out += PhoneInsight(
                id = "todhabit:$pkg", kind = InsightKind.TIME_OF_DAY_HABIT, group = InsightGroup.PATTERN,
                title = "$name is mostly a $label app",
                message = "${InsightStats.roundHalfUp(perPeriod[bestIndex] * 100.0 / periodTotal)}% of your $name time is in the $label (${periodHours(period)}).",
                evidence = evidence(
                    "When in the day you use $name",
                    "Share of its time in each part of the day",
                    win.label(),
                    "${PhoneText.duration(total)} over ${days(dates.size)}",
                ),
                direction = null,
                confidence = confidence((total / T.MINUTE).toInt(), (T.TOD_MIN_APP_MS / T.MINUTE).toInt()),
                priority = T.TOD_PRIORITY,
                subject = pkg, subjectName = name, peakHour = c.peakHour(pkg, dates),
            )
            if (out.size >= T.TOD_MAX) break
        }
        return out
    }

    private fun cv(values: List<Long>): Double? {
        val mean = InsightStats.mean(values)
        if (mean <= 0.0) return null
        return InsightStats.stdev(values.map { it.toDouble() }) / mean
    }

    fun consistency(c: InsightContext): List<PhoneInsight> {
        val win = c.win(T.LAST14_FROM, T.LAST14_TO)
        val prior = c.win(T.PRIOR14_FROM, T.PRIOR14_TO)
        val last = c.complete(win)
        if (last.size < T.CONSISTENCY_MIN_DAYS) return emptyList()
        val values = last.map { it.screenOnMs }
        val cvNow = cv(values) ?: return emptyList()
        val label = when {
            cvNow < T.CONSISTENCY_STEADY_BELOW -> "steady"
            cvNow < T.CONSISTENCY_VARIABLE_BELOW -> "variable"
            else -> "irregular"
        }
        var message = "Daily screen time varies by about ${InsightStats.roundHalfUp(cvNow * 100.0)}% around your " +
            "${PhoneText.duration(InsightStats.mean(values).toLong())} average."

        var direction: TrendDirection? = null
        var sample = last.size
        val before = c.complete(prior)
        if (before.size >= T.CONSISTENCY_MIN_DAYS) {
            sample += before.size
            val cvBefore = cv(before.map { it.screenOnMs })
            if (cvBefore != null && Math.abs(cvNow - cvBefore) >= T.CONSISTENCY_TREND_MIN_DIFF - 1e-9) {
                val lessSteady = cvNow > cvBefore
                message += " It's ${if (lessSteady) "less steady" else "steadier"} than the 14 days before."
                direction = if (lessSteady) TrendDirection.UP else TrendDirection.DOWN
            }
        }

        val firstUnlocks = last.mapNotNull { it.firstUnlockAtMs }
        if (firstUnlocks.size >= T.CONSISTENCY_FIRST_UNLOCK_MIN_DAYS) {
            val minutes = firstUnlocks.map { ms ->
                val t = Instant.ofEpochMilli(ms).atZone(c.input.zone).toLocalTime()
                (t.hour * 60 + t.minute).toLong()
            }
            val sd = InsightStats.stdev(minutes.map { it.toDouble() })
            when {
                sd <= T.CONSISTENCY_REGULAR_MAX_STDEV_MIN ->
                    message += " Your first unlock is usually around ${PhoneText.clock(InsightStats.median(minutes).toInt())} (±${InsightStats.roundHalfUp(sd)} min)."
                sd >= T.CONSISTENCY_VARIES_MIN_STDEV_MIN ->
                    message += " Your first unlock time varies a lot from day to day."
            }
        }
        return listOf(
            PhoneInsight(
                id = "consistency:${c.today.minusDays(1)}", kind = InsightKind.CONSISTENCY, group = InsightGroup.PATTERN,
                title = "Your phone use is $label", message = message,
                evidence = evidence(
                    "How much your daily screen time varies",
                    "Standard deviation divided by the average, over complete days",
                    win.label(),
                    days(last.size),
                ),
                direction = direction,
                confidence = confidence(sample, T.CONSISTENCY_MIN_DAYS),
                priority = T.CONSISTENCY_PRIORITY,
            ),
        )
    }
}
