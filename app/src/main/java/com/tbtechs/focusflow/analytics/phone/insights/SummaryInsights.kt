package com.tbtechs.focusflow.analytics.phone.insights

import com.tbtechs.focusflow.analytics.phone.PhoneDay
import com.tbtechs.focusflow.analytics.phone.PhoneText
import com.tbtechs.focusflow.analytics.phone.TrendDirection
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import com.tbtechs.focusflow.analytics.phone.insights.InsightThresholds as T

/** Modules 1–3: weekly summary, monthly summary, unusual day. */
internal object SummaryInsights {

    private fun dur(avg: Avg) = PhoneText.duration(avg.value.roundToLong())
    private fun percentParen(pct: Int?) = if (pct == null) "" else " (${PhoneText.signedPercent(pct)})"

    private fun direction(pct: Int?): TrendDirection = when {
        pct == null -> TrendDirection.FLAT
        pct >= T.SUMMARY_DIRECTION_PERCENT -> TrendDirection.UP
        pct <= -T.SUMMARY_DIRECTION_PERCENT -> TrendDirection.DOWN
        else -> TrendDirection.FLAT
    }

    private fun unlockAvg(days: List<PhoneDay>): Avg {
        val signal = days.filter { it.unlockSignal }
        return Avg(signal.sumOf { it.unlockCount.toLong() }, signal.size)
    }

    private fun topApp(c: InsightContext, dates: List<java.time.LocalDate>): Pair<String, Long>? =
        c.appTotals(dates).entries
            .filter { it.value > 0L }
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .firstOrNull()?.let { it.key to it.value }

    fun weekly(c: InsightContext): List<PhoneInsight> {
        val recentWin = c.win(T.RECENT_FROM, T.RECENT_TO)
        val beforeWin = c.win(T.WEEK_LAST_FROM, T.WEEK_LAST_TO)
        val r = c.complete(recentWin)
        val b = c.complete(beforeWin)
        if (r.size < T.WEEKLY_MIN_COMPLETE_DAYS || b.size < T.WEEKLY_MIN_COMPLETE_DAYS) return emptyList()

        val rScreen = Avg(r.sumOf { it.screenOnMs }, r.size)
        val bScreen = Avg(b.sumOf { it.screenOnMs }, b.size)
        val screenPct = InsightStats.percentChange(rScreen.value, bScreen.value)

        val lines = ArrayList<String>()
        lines += "Screen time: ${dur(rScreen)}/day" +
            (if (screenPct == null) "" else ", ${PhoneText.signedPercent(screenPct)} vs the week before (${dur(bScreen)}/day)") + "."

        val rUnlock = unlockAvg(r)
        val bUnlock = unlockAvg(b)
        if (rUnlock.n >= T.WEEKLY_MIN_UNLOCK_DAYS && bUnlock.n >= T.WEEKLY_MIN_UNLOCK_DAYS) {
            val pct = InsightStats.percentChange(rUnlock.value, bUnlock.value)
            lines += "Unlocks: ${rUnlock.value.roundToInt()}/day${percentParen(pct)}."
        }

        val top = topApp(c, c.completeDates(recentWin))
        if (top != null) {
            val old = topApp(c, c.completeDates(beforeWin))
            var line = "Most used: ${c.name(top.first)} (${PhoneText.duration(top.second / r.size)}/day)."
            if (old != null && old.first != top.first) line += " It was ${c.name(old.first)} the week before."
            lines += line
        }

        val rLate = Avg(r.sumOf { c.lateNightMs(it) }, r.size)
        val bLate = Avg(b.sumOf { c.lateNightMs(it) }, b.size)
        if (rLate.total > 0L || bLate.total > 0L) {
            val pct = InsightStats.percentChange(rLate.value, bLate.value)
            lines += "Late night (10 PM–5 AM): ${dur(rLate)}/day${percentParen(pct)}."
        }

        val focusNow = c.focusMs(recentWin)
        val focusBefore = c.focusMs(beforeWin)
        val attemptsNow = c.blockedAttempts(recentWin)
        val attemptsBefore = c.blockedAttempts(beforeWin)
        if (focusNow > 0L || focusBefore > 0L || attemptsNow > 0 || attemptsBefore > 0) {
            lines += "Focus: ${PhoneText.duration(focusNow)} in focus mode, ${PhoneText.count(attemptsNow, "blocked attempt")}."
        }

        val id = "weekly:${c.today.minusDays(1)}"
        return listOf(
            PhoneInsight(
                id = id, kind = InsightKind.WEEKLY_SUMMARY, group = InsightGroup.SUMMARY,
                title = "Last 7 days vs the 7 before", message = lines.joinToString("\n"),
                evidence = evidence(
                    "Screen time, unlocks, apps and focus over the last 7 days vs the 7 before",
                    "Daily averages over complete days only",
                    "${recentWin.label()} vs ${beforeWin.label()}",
                    days(r.size + b.size),
                ),
                direction = direction(screenPct),
                confidence = confidence(r.size + b.size, T.WEEKLY_MIN_COMPLETE_DAYS),
                priority = T.WEEKLY_PRIORITY,
            ),
        )
    }

    fun monthly(c: InsightContext): List<PhoneInsight> {
        val monthWin = c.win(T.MONTH_FROM, T.MONTH_TO)
        val beforeWin = c.win(T.MONTH_BEFORE_FROM, T.MONTH_BEFORE_TO)
        val m = c.complete(monthWin)
        val b = c.complete(beforeWin)
        if (m.size < T.MONTHLY_MIN_COMPLETE_DAYS || b.size < T.MONTHLY_BEFORE_MIN_COMPLETE_DAYS) return emptyList()

        val mScreen = Avg(m.sumOf { it.screenOnMs }, m.size)
        val bScreen = Avg(b.sumOf { it.screenOnMs }, b.size)
        val screenPct = InsightStats.percentChange(mScreen.value, bScreen.value)

        val lines = ArrayList<String>()
        lines += "Screen time: ${dur(mScreen)}/day" +
            (if (screenPct == null) "" else ", ${PhoneText.signedPercent(screenPct)} vs the month before") + "."

        val mUnlock = unlockAvg(m)
        val bUnlock = unlockAvg(b)
        if (mUnlock.n >= T.MONTHLY_MIN_UNLOCK_DAYS && bUnlock.n >= T.MONTHLY_MIN_UNLOCK_DAYS) {
            val pct = InsightStats.percentChange(mUnlock.value, bUnlock.value)
            lines += "Unlocks: ${mUnlock.value.roundToInt()}/day${percentParen(pct)}."
        }

        val top = c.appTotals(c.completeDates(monthWin)).entries
            .filter { it.value > 0L }
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(T.MONTHLY_TOP_APPS)
        if (top.isNotEmpty()) lines += "Top apps: ${top.joinToString(", ") { c.name(it.key) }}."

        return listOf(
            PhoneInsight(
                id = "monthly:${c.today.minusDays(1)}", kind = InsightKind.MONTHLY_SUMMARY, group = InsightGroup.SUMMARY,
                title = "Last 30 days vs the 30 before", message = lines.joinToString("\n"),
                evidence = evidence(
                    "Screen time, unlocks and top apps over the last 30 days vs the 30 before",
                    "Daily averages over complete days only",
                    "${monthWin.label()} vs ${beforeWin.label()}",
                    days(m.size + b.size),
                ),
                direction = direction(screenPct),
                confidence = confidence(m.size + b.size, T.MONTHLY_MIN_COMPLETE_DAYS),
                priority = T.MONTHLY_PRIORITY,
            ),
        )
    }

    fun unusualDay(c: InsightContext): List<PhoneInsight> {
        val yesterday = c.today.minusDays(1)
        val y = c.completeByDate[yesterday] ?: return emptyList()
        val baselineWin = c.win(T.UNUSUAL_BASELINE_FROM, T.UNUSUAL_BASELINE_TO)
        val baseline = c.complete(baselineWin)
        if (baseline.size < T.UNUSUAL_MIN_BASELINE_DAYS) return emptyList()

        val median = InsightStats.median(baseline.map { it.screenOnMs })
        if (median <= 0L) return emptyList()
        val value = y.screenOnMs
        val up = value * T.UNUSUAL_UP_DEN >= median * T.UNUSUAL_UP_NUM && value - median >= T.UNUSUAL_MIN_DELTA_MS
        val down = value * T.UNUSUAL_DOWN_DEN <= median * T.UNUSUAL_DOWN_NUM && median - value >= T.UNUSUAL_MIN_DELTA_MS
        if (!up && !down) return emptyList()

        val percent = abs(InsightStats.percentChange(value.toDouble(), median.toDouble()) ?: 0)
        val message = "Screen time was ${PhoneText.duration(value)}, $percent% ${if (up) "above" else "below"} " +
            "your usual ${PhoneText.duration(median)} (typical of the last ${baseline.size} days)."
        return listOf(
            PhoneInsight(
                id = "unusual:$yesterday", kind = InsightKind.UNUSUAL_DAY, group = InsightGroup.CHANGE,
                title = if (up) "Yesterday was heavier than usual" else "Yesterday was lighter than usual",
                message = message,
                evidence = evidence(
                    "Screen time yesterday vs your usual day",
                    "Compared with the median of your previous ${baseline.size} complete days",
                    baselineWin.label(),
                    days(baseline.size),
                ),
                direction = if (up) TrendDirection.UP else TrendDirection.DOWN,
                confidence = confidence(baseline.size + 1, T.UNUSUAL_MIN_BASELINE_DAYS),
                priority = if (up) T.UNUSUAL_UP_PRIORITY else T.UNUSUAL_DOWN_PRIORITY,
            ),
        )
    }
}
