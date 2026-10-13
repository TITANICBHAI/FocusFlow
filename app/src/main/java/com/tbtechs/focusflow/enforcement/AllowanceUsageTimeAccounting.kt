package com.tbtechs.focusflow.enforcement

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class AllowanceUsageTimeSlice(
    val localDate: String,
    val durationMs: Long,
)

/**
 * Calendar-based helpers for bounded allowance estimates. Unlike a fixed
 * 24-hour duration, these slices respect local midnight across DST changes.
 */
internal object AllowanceUsageTimeAccounting {
    fun localDateKey(atMs: Long, zoneId: ZoneId): String =
        Instant.ofEpochMilli(atMs).atZone(zoneId).toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun localMidnight(atMs: Long, zoneId: ZoneId): Long =
        Instant.ofEpochMilli(atMs).atZone(zoneId).toLocalDate().atStartOfDay(zoneId)
            .toInstant().toEpochMilli()

    fun cappedEnd(
        segmentStartedAtMs: Long,
        requestedEndMs: Long,
        maximumSegmentMs: Long = AllowanceLedger.MAX_ESTIMATED_SEGMENT_MS,
    ): Long {
        if (segmentStartedAtMs <= 0L || maximumSegmentMs <= 0L) return segmentStartedAtMs
        val capAt = if (Long.MAX_VALUE - segmentStartedAtMs < maximumSegmentMs) {
            Long.MAX_VALUE
        } else {
            segmentStartedAtMs + maximumSegmentMs
        }
        return minOf(requestedEndMs, capAt)
    }

    fun splitAtLocalMidnight(
        startMs: Long,
        endMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<AllowanceUsageTimeSlice> {
        if (endMs <= startMs) return emptyList()

        val slices = mutableListOf<AllowanceUsageTimeSlice>()
        var cursorMs = startMs
        while (cursorMs < endMs) {
            val cursor = Instant.ofEpochMilli(cursorMs).atZone(zoneId)
            val nextMidnightMs = cursor.toLocalDate().plusDays(1)
                .atStartOfDay(zoneId).toInstant().toEpochMilli()
            val boundaryMs = minOf(endMs, nextMidnightMs)
            if (boundaryMs <= cursorMs) break
            slices += AllowanceUsageTimeSlice(
                localDate = cursor.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                durationMs = boundaryMs - cursorMs,
            )
            cursorMs = boundaryMs
        }
        return slices
    }

    fun recoverableEnd(
        checkpointAtMs: Long,
        nowMs: Long,
        maximumRecoveryMs: Long,
    ): Long {
        if (checkpointAtMs <= 0L || maximumRecoveryMs <= 0L || nowMs <= checkpointAtMs) {
            return checkpointAtMs
        }
        val recoverableMs = minOf(nowMs - checkpointAtMs, maximumRecoveryMs)
        return if (Long.MAX_VALUE - checkpointAtMs < recoverableMs) {
            Long.MAX_VALUE
        } else {
            checkpointAtMs + recoverableMs
        }
    }
}
