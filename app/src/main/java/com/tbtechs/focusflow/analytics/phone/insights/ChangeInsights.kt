package com.tbtechs.focusflow.analytics.phone.insights

import com.tbtechs.focusflow.analytics.phone.PhoneText
import com.tbtechs.focusflow.analytics.phone.TrendDirection
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import com.tbtechs.focusflow.analytics.phone.insights.InsightThresholds as T

/** Modules 4–6 and 12: app surge / drop, unlocks change, late-night shift, focus trend. */
internal object ChangeInsights {

    private fun dur(avg: Avg) = PhoneText.duration(avg.value.roundToLong())

    fun appSurgeAndDrop(c: InsightContext): List<PhoneInsight> {
        val recentWin = c.win(T.RECENT_FROM, T.RECENT_TO)
        val priorWin = c.win(T.PRIOR_FROM, T.PRIOR_TO)
        val recentDates = c.completeDates(recentWin)
        val priorDates = c.completeDates(priorWin)
        if (recentDates.size < T.APP_MIN_RECENT_DAYS || priorDates.size < T.APP_MIN_PRIOR_DAYS) return emptyList()

        val recentTotals = c.appTotals(recentDates)
        val priorTotals = c.appTotals(priorDates)
        val packages = (recentTotals.keys + priorTotals.keys).toSortedSet()
        val evidenceLines = evidence(
            "Time in each app over the last 7 days vs the 21 days before",
            "Daily averages over complete days only",
            "${recentWin.label()} vs ${priorWin.label()}",
            days(recentDates.size + priorDates.size),
        )
        val conf = confidence(recentDates.size + priorDates.size, T.APP_MIN_PRIOR_DAYS)

        data class Row(val pkg: String, val recent: Avg, val prior: Avg, val active: Int)
        val rows = packages.map { pkg ->
            Row(
                pkg,
                Avg(recentTotals[pkg] ?: 0L, recentDates.size),
                Avg(priorTotals[pkg] ?: 0L, priorDates.size),
                recentDates.count { c.appMsOn(pkg, it) > 0L },
            )
        }

        val surges = rows.filter { row ->
            if (row.active < T.SURGE_MIN_ACTIVE_DAYS) return@filter false
            if (row.prior.total == 0L) {
                row.recent.value >= T.SURGE_NEW_MIN_RECENT_MS
            } else {
                row.recent.atLeast(row.prior, T.SURGE_RATIO_NUM, T.SURGE_RATIO_DEN) &&
                    row.recent.minus(row.prior) >= T.SURGE_MIN_DELTA_MS
            }
        }.sortedWith(compareByDescending<Row> { it.recent.minus(it.prior) }.thenBy { it.pkg }).take(T.SURGE_MAX)

        val drops = rows.filter { row ->
            row.prior.value >= T.DROP_MIN_PRIOR_MS &&
                row.recent.atMost(row.prior, T.DROP_RATIO_NUM, T.DROP_RATIO_DEN) &&
                row.prior.minus(row.recent) >= T.DROP_MIN_DELTA_MS
        }.sortedWith(compareByDescending<Row> { it.prior.minus(it.recent) }.thenBy { it.pkg }).take(T.DROP_MAX)

        val out = ArrayList<PhoneInsight>()
        for (row in surges) {
            val name = c.name(row.pkg)
            val isNew = row.prior.total == 0L
            out += PhoneInsight(
                id = "surge:${row.pkg}", kind = InsightKind.APP_SURGE, group = InsightGroup.CHANGE,
                title = if (isNew) "$name is new in your routine" else "$name is taking more time",
                message = if (isNew) {
                    "${dur(row.recent)}/day over the last 7 days; it wasn't used much before."
                } else {
                    val pct = InsightStats.percentChange(row.recent.value, row.prior.value)
                    "${dur(row.recent)}/day over the last 7 days, up from ${dur(row.prior)}/day (${PhoneText.signedPercent(pct ?: 0)})."
                },
                evidence = evidenceLines, direction = TrendDirection.UP, confidence = conf, priority = T.SURGE_PRIORITY,
            )
        }
        for (row in drops) {
            val name = c.name(row.pkg)
            val pct = InsightStats.percentChange(row.recent.value, row.prior.value)
            out += PhoneInsight(
                id = "drop:${row.pkg}", kind = InsightKind.APP_DROP, group = InsightGroup.CHANGE,
                title = "$name is down",
                message = "${dur(row.recent)}/day over the last 7 days, down from ${dur(row.prior)}/day (${PhoneText.signedPercent(pct ?: 0)}).",
                evidence = evidenceLines, direction = TrendDirection.DOWN, confidence = conf, priority = T.DROP_PRIORITY,
            )
        }
        return out
    }

    fun unlocksChange(c: InsightContext): List<PhoneInsight> {
        val recentWin = c.win(T.RECENT_FROM, T.RECENT_TO)
        val priorWin = c.win(T.PRIOR_FROM, T.PRIOR_TO)
        val r = c.complete(recentWin).filter { it.unlockSignal }
        val p = c.complete(priorWin).filter { it.unlockSignal }
        if (r.size < T.UNLOCKS_MIN_RECENT_DAYS || p.size < T.UNLOCKS_MIN_PRIOR_DAYS) return emptyList()

        val recent = Avg(r.sumOf { it.unlockCount.toLong() }, r.size)
        val prior = Avg(p.sumOf { it.unlockCount.toLong() }, p.size)
        val diff = recent.minus(prior)
        val up = recent.atLeast(prior, T.UNLOCKS_UP_NUM, T.UNLOCKS_UP_DEN) && diff >= T.UNLOCKS_MIN_DELTA_PER_DAY
        val down = recent.atMost(prior, T.UNLOCKS_DOWN_NUM, T.UNLOCKS_DOWN_DEN) && -diff >= T.UNLOCKS_MIN_DELTA_PER_DAY
        if (!up && !down) return emptyList()

        val pct = InsightStats.percentChange(recent.value, prior.value) ?: 0
        return listOf(
            PhoneInsight(
                id = "unlocks:${c.today.minusDays(1)}", kind = InsightKind.UNLOCKS_CHANGE, group = InsightGroup.CHANGE,
                title = if (up) "You're unlocking more often" else "You're unlocking less often",
                message = "${recent.value.roundToInt()} unlocks a day this week, ${if (up) "up" else "down"} from " +
                    "${prior.value.roundToInt()} (${PhoneText.signedPercent(pct)}).",
                evidence = evidence(
                    "Unlocks per day over the last 7 days vs the 21 days before",
                    "Daily averages over complete days that report unlocks",
                    "${recentWin.label()} vs ${priorWin.label()}",
                    days(r.size + p.size),
                ),
                direction = if (up) TrendDirection.UP else TrendDirection.DOWN,
                confidence = confidence(r.size + p.size, T.UNLOCKS_MIN_PRIOR_DAYS),
                priority = T.UNLOCKS_PRIORITY,
            ),
        )
    }

    fun lateNightShift(c: InsightContext): List<PhoneInsight> {
        val recentWin = c.win(T.RECENT_FROM, T.RECENT_TO)
        val priorWin = c.win(T.PRIOR_FROM, T.PRIOR_TO)
        val r = c.complete(recentWin)
        val p = c.complete(priorWin)
        if (r.size < T.LATE_MIN_RECENT_DAYS || p.size < T.LATE_MIN_PRIOR_DAYS) return emptyList()

        val recent = Avg(r.sumOf { c.lateNightMs(it) }, r.size)
        val prior = Avg(p.sumOf { c.lateNightMs(it) }, p.size)
        val increase = recent.minus(prior)
        val up = increase >= T.LATE_MIN_DELTA_MS && (
            recent.atLeast(prior, T.LATE_UP_RATIO_NUM, T.LATE_UP_RATIO_DEN) ||
                (prior.value < T.LATE_UP_SMALL_PRIOR_MS && recent.value >= T.LATE_UP_SMALL_PRIOR_MIN_RECENT_MS)
            )
        val down = -increase >= T.LATE_MIN_DELTA_MS && recent.atMost(prior, T.LATE_DOWN_RATIO_NUM, T.LATE_DOWN_RATIO_DEN)
        if (!up && !down) return emptyList()

        return listOf(
            PhoneInsight(
                id = "latenight:${c.today.minusDays(1)}", kind = InsightKind.LATE_NIGHT_SHIFT, group = InsightGroup.CHANGE,
                title = if (up) "More late-night screen time" else "Less late-night screen time",
                message = "${dur(recent)}/day between 10 PM and 5 AM, ${if (up) "up" else "down"} from ${dur(prior)}/day.",
                evidence = evidence(
                    "Screen-on time between 10 PM and 5 AM",
                    "Daily averages over complete days only",
                    "${recentWin.label()} vs ${priorWin.label()}",
                    days(r.size + p.size),
                ),
                direction = if (up) TrendDirection.UP else TrendDirection.DOWN,
                confidence = confidence(r.size + p.size, T.LATE_MIN_PRIOR_DAYS),
                priority = if (up) T.LATE_UP_PRIORITY else T.LATE_DOWN_PRIORITY,
            ),
        )
    }

    private fun oneDecimal(value: Double) = String.format(Locale.ENGLISH, "%.1f", value)

    fun focusTrend(c: InsightContext): List<PhoneInsight> {
        val nowWin = c.win(T.LAST14_FROM, T.LAST14_TO)
        val beforeWin = c.win(T.PRIOR14_FROM, T.PRIOR14_TO)
        val focusNow = c.focusMs(nowWin)
        val focusBefore = c.focusMs(beforeWin)
        if (focusNow < T.FOCUS_MIN_FOCUS_MS || focusBefore < T.FOCUS_MIN_FOCUS_MS) return emptyList()

        val rateNow = c.blockedAttempts(nowWin) / (focusNow.toDouble() / T.HOUR)
        val rateBefore = c.blockedAttempts(beforeWin) / (focusBefore.toDouble() / T.HOUR)
        val pct = InsightStats.percentChange(rateNow, rateBefore) ?: return emptyList()
        if (abs(pct) < T.FOCUS_MIN_CHANGE_PERCENT) return emptyList()
        val down = pct < 0

        var message = "${oneDecimal(rateNow)} attempts per focus hour over the last 14 days, ${if (down) "down" else "up"} " +
            "from ${oneDecimal(rateBefore)} (${PhoneText.signedPercent(pct)})."
        val endedNow = c.endedSessions(nowWin)
        val endedBefore = c.endedSessions(beforeWin)
        if (endedNow.size >= T.FOCUS_MIN_ENDED_SESSIONS && endedBefore.size >= T.FOCUS_MIN_ENDED_SESSIONS) {
            val heldNow = InsightStats.roundHalfUp(endedNow.count { it.overrideCount == 0 } * 100.0 / endedNow.size)
            val heldBefore = InsightStats.roundHalfUp(endedBefore.count { it.overrideCount == 0 } * 100.0 / endedBefore.size)
            if (abs(heldNow - heldBefore) >= T.FOCUS_MIN_HELD_DIFF_POINTS) {
                message += " Sessions without overrides: $heldNow% (was $heldBefore%)."
            }
        }
        return listOf(
            PhoneInsight(
                id = "focus:${c.today.minusDays(1)}", kind = InsightKind.FOCUS_TREND, group = InsightGroup.CHANGE,
                title = if (down) "Fewer blocked attempts per focus hour" else "More blocked attempts per focus hour",
                message = message,
                evidence = evidence(
                    "Blocked attempts per hour of focus mode",
                    "Attempts divided by focus time, last 14 days vs the 14 before",
                    "${nowWin.label()} vs ${beforeWin.label()}",
                    "${PhoneText.duration(focusNow)} and ${PhoneText.duration(focusBefore)} of focus time",
                ),
                direction = if (down) TrendDirection.DOWN else TrendDirection.UP,
                confidence = confidence(((focusNow + focusBefore) / T.HOUR).toInt(), (T.FOCUS_MIN_FOCUS_MS / T.HOUR).toInt()),
                priority = T.FOCUS_PRIORITY,
            ),
        )
    }
}
