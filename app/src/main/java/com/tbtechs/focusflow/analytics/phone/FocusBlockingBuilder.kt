package com.tbtechs.focusflow.analytics.phone

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Focus sessions and blocked-app attempts. Pure: "now" and the zone are arguments. */
object FocusBlockingBuilder {
    private const val HOUR_MS = 3_600_000L
    private const val MIN_FOCUS_FOR_RATE_MS = 30L * 60 * 1000
    private const val MIN_FOCUS_FOR_TREND_MS = HOUR_MS
    private const val MAX_BLOCKED_APPS = 8
    private const val WEEKS = 8
    private const val TREND_CHANGE_PERCENT = 20
    /** Exclusive upper bounds (ms) of duration buckets 0..3; bucket 4 is open-ended. */
    private val DURATION_BOUNDS_MS = longArrayOf(15L * 60_000, 30L * 60_000, 60L * 60_000, 120L * 60_000)

    fun build(
        range: PhoneDateRange, today: LocalDate, nowMs: Long, zone: ZoneId,
        sessions: List<FocusSessionRow>, blocked: List<BlockedDayRow>,
        trendSessions: List<FocusSessionRow>, trendBlocked: List<BlockedDayRow>,
    ): FocusBlockingSection {
        val rangeStart = startMs(range.start, zone)
        val rangeEnd = minOf(startMs(range.end.plusDays(1), zone), nowMs)

        fun overlap(s: FocusSessionRow, from: Long, to: Long): Long =
            maxOf(0L, minOf(s.endedAtMs ?: nowMs, to) - maxOf(s.startedAtMs, from))

        val inRange = sessions.filter { overlap(it, rangeStart, rangeEnd) > 0L }
        val focusMs = sessions.sumOf { overlap(it, rangeStart, rangeEnd) }
        val ended = inRange.filter { it.endedAtMs != null }
        val buckets = IntArray(5)
        for (s in ended) buckets[durationBucket(s.endedAtMs!! - s.startedAtMs)]++

        val blockedTotal = blocked.sumOf { it.attempts }
        val perPackage = LinkedHashMap<String, Pair<String, Int>>()
        for (row in blocked) {
            val old = perPackage[row.packageName]
            val name = row.appName.ifBlank { old?.first ?: row.packageName }
            perPackage[row.packageName] = name to ((old?.second ?: 0) + row.attempts)
        }
        val blockedApps = perPackage.map { (pkg, v) ->
            BlockedAppRow(pkg, v.first, v.second, if (blockedTotal == 0) 0.0 else v.second * 100.0 / blockedTotal)
        }.sortedWith(compareByDescending<BlockedAppRow> { it.attempts }.thenBy { it.appName }).take(MAX_BLOCKED_APPS)
        val byHour = BuilderUtils.sumInts(blocked.map { it.hourly }, 24)

        val rate = if (focusMs >= MIN_FOCUS_FOR_RATE_MS) blockedTotal / (focusMs / HOUR_MS.toDouble()) else null

        val weeks = (WEEKS - 1 downTo 0).map { k ->
            val last = today.minusDays(7L * k)
            val first = last.minusDays(6)
            val from = startMs(first, zone)
            val to = startMs(last.plusDays(1), zone)
            val startedInBlock = trendSessions.filter {
                val d = Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate()
                !d.isBefore(first) && !d.isAfter(last)
            }
            val endedInBlock = startedInBlock.filter { it.endedAtMs != null }
            FocusWeekPoint(
                blockStart = first,
                focusMs = trendSessions.sumOf { overlap(it, from, to) },
                endedSessions = endedInBlock.size,
                heldSessions = endedInBlock.count { it.overrideCount == 0 },
                attempts = trendBlocked.filter { it.date >= first.toString() && it.date <= last.toString() }.sumOf { it.attempts },
            )
        }

        return FocusBlockingSection(
            focusMs = focusMs,
            sessions = inRange.size,
            heldSessions = ended.count { it.overrideCount == 0 },
            interruptedSessions = ended.count { it.overrideCount > 0 },
            durationBucketCounts = buckets.toList(),
            blockedAttempts = blockedTotal,
            blockedApps = blockedApps,
            blockedByHour = byHour,
            blockedPeakHour = BuilderUtils.peakIndexInt(byHour),
            attemptsPerFocusHour = rate,
            weeks = weeks,
            trend = trend(weeks),
        )
    }

    /** [weeks] is oldest first, so the last two are "now" (blocks 0+1) and the two before them are "before" (blocks 2+3). */
    private fun trend(weeks: List<FocusWeekPoint>): FocusTrend? {
        val now = weeks.takeLast(2)
        val before = weeks.takeLast(4).take(2)
        val focusNow = now.sumOf { it.focusMs }
        val focusBefore = before.sumOf { it.focusMs }
        if (focusNow < MIN_FOCUS_FOR_TREND_MS || focusBefore < MIN_FOCUS_FOR_TREND_MS) return null
        val rateNow = now.sumOf { it.attempts } / (focusNow / HOUR_MS.toDouble())
        val rateBefore = before.sumOf { it.attempts } / (focusBefore / HOUR_MS.toDouble())
        if (rateBefore == 0.0) {
            return if (rateNow == 0.0) FocusTrend(TrendDirection.FLAT, 0.0, 0.0, 0) else null
        }
        val change = ((rateNow - rateBefore) * 100.0 / rateBefore).roundToInt()
        val direction = when {
            change >= TREND_CHANGE_PERCENT -> TrendDirection.UP
            change <= -TREND_CHANGE_PERCENT -> TrendDirection.DOWN
            else -> TrendDirection.FLAT
        }
        return FocusTrend(direction, rateNow, rateBefore, change)
    }

    private fun durationBucket(durationMs: Long): Int {
        for (i in DURATION_BOUNDS_MS.indices) if (durationMs < DURATION_BOUNDS_MS[i]) return i
        return 4
    }

    private fun startMs(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
}
