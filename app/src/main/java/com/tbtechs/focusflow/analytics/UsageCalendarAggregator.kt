package com.tbtechs.focusflow.analytics

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Derives local-day totals and clock-hour buckets without splitting session identity. */
object UsageCalendarAggregator {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Keeps every per-app row when callers need to select data by calendar date.
     * A plain associateBy(date) would silently discard all but one app per day.
     */
    fun aggregateByDate(
        sessions: List<ForegroundSession>,
        rangeStartMs: Long,
        rangeEndMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Map<String, List<AppUsageDay>> =
        aggregate(sessions, rangeStartMs, rangeEndMs, zoneId).groupBy { it.date }

    fun aggregate(
        sessions: List<ForegroundSession>,
        rangeStartMs: Long,
        rangeEndMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<AppUsageDay> {
        if (rangeStartMs >= rangeEndMs) return emptyList()

        data class MutableDay(
            val date: String,
            val packageName: String,
            val hourlyMs: LongArray = LongArray(24),
            var foregroundMs: Long = 0L,
            var launchCount: Int = 0,
            var sessionCount: Int = 0,
            var firstStartAtMs: Long? = null,
            var lastUsedAtMs: Long = 0L,
        )

        val days = linkedMapOf<Pair<String, String>, MutableDay>()
        fun day(date: String, packageName: String) =
            days.getOrPut(date to packageName) { MutableDay(date, packageName) }

        sessions.forEach { session ->
            if (session.endedAtMs <= session.startedAtMs) return@forEach

            val startLocalDate = Instant.ofEpochMilli(session.startedAtMs)
                .atZone(zoneId).toLocalDate().format(dateFormatter)
            if (session.startedAtMs in rangeStartMs until rangeEndMs) {
                val startDay = day(startLocalDate, session.packageName)
                startDay.sessionCount += 1
                if (session.isNewOpen) startDay.launchCount += 1
                startDay.firstStartAtMs = minOf(
                    startDay.firstStartAtMs ?: Long.MAX_VALUE,
                    session.startedAtMs,
                )
            }

            var cursor = maxOf(session.startedAtMs, rangeStartMs)
            val clippedEnd = minOf(session.endedAtMs, rangeEndMs)
            while (cursor < clippedEnd) {
                val zonedCursor = Instant.ofEpochMilli(cursor).atZone(zoneId)
                val nextHour = zonedCursor.withMinute(0).withSecond(0).withNano(0)
                    .plusHours(1).toInstant().toEpochMilli()
                val segmentEnd = minOf(clippedEnd, nextHour)
                if (segmentEnd <= cursor) break

                val date = zonedCursor.toLocalDate().format(dateFormatter)
                val row = day(date, session.packageName)
                val duration = segmentEnd - cursor
                row.hourlyMs[zonedCursor.hour] += duration
                row.foregroundMs += duration
                row.lastUsedAtMs = maxOf(row.lastUsedAtMs, segmentEnd)
                cursor = segmentEnd
            }
        }

        return days.values
            .sortedWith(compareBy<MutableDay> { it.date }.thenByDescending { it.foregroundMs })
            .map { row ->
                AppUsageDay(
                    date = row.date,
                    packageName = row.packageName,
                    foregroundMs = row.foregroundMs,
                    hourlyMs = row.hourlyMs.toList(),
                    launchCount = row.launchCount,
                    sessionCount = row.sessionCount,
                    firstStartAtMs = row.firstStartAtMs?.takeUnless { it == Long.MAX_VALUE },
                    lastUsedAtMs = row.lastUsedAtMs,
                )
            }
    }
}
