package com.tbtechs.focusflow.analytics.phone

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Milliseconds spent in one local clock hour of one local date. */
data class HourSlice(val date: LocalDate, val hour: Int, val ms: Long)

/**
 * Splits a time span into per-(local date, local hour) milliseconds.
 *
 * Boundaries are found on the zone's own timeline, so half-hour zones, midnight and DST
 * transitions are handled correctly. It never steps by a fixed 3 600 000 ms from [startMs].
 */
object HourSplitter {
    fun split(startMs: Long, endMs: Long, zone: ZoneId): List<HourSlice> {
        if (endMs <= startMs) return emptyList()
        val slices = ArrayList<HourSlice>()
        var cursor = startMs
        while (cursor < endMs) {
            val zoned = Instant.ofEpochMilli(cursor).atZone(zone)
            val nextHour = zoned.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
            val segmentEnd = minOf(endMs, nextHour)
            if (segmentEnd <= cursor) break
            slices += HourSlice(zoned.toLocalDate(), zoned.hour, segmentEnd - cursor)
            cursor = segmentEnd
        }
        return slices
    }
}
