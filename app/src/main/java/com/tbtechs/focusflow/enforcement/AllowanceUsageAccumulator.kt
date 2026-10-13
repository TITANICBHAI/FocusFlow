package com.tbtechs.focusflow.enforcement

import java.time.ZoneId

/** Tracks one allowance app's estimated foreground time between successful reads. */
internal class AllowanceUsageAccumulator(
    private val ledger: AllowanceLedger,
    private val zoneId: ZoneId,
) {
    private var segmentCheckpointAtMs = 0L
    private var segmentStartedAtMs = 0L

    fun start(atMs: Long, confirmedAtMs: Long = 0L) {
        segmentStartedAtMs = atMs
        segmentCheckpointAtMs = maxOf(atMs, confirmedAtMs)
    }

    fun updateSessionStart(startedAtMs: Long) {
        if (startedAtMs > 0L) segmentStartedAtMs = startedAtMs
    }

    fun segmentStartedAtMs(): Long = segmentStartedAtMs

    fun successfulRead(atMs: Long) {
        segmentCheckpointAtMs = atMs
    }

    fun checkpoint(target: AllowanceUsageTarget, atMs: Long) {
        addTimeEstimate(target, atMs)
        segmentCheckpointAtMs = atMs
    }

    fun finish(target: AllowanceUsageTarget, atMs: Long) {
        addTimeEstimate(target, atMs)
        segmentCheckpointAtMs = atMs
    }

    fun clear() {
        segmentCheckpointAtMs = 0L
        segmentStartedAtMs = 0L
    }

    private fun addTimeEstimate(target: AllowanceUsageTarget, requestedEndMs: Long) {
        if (target.mode != AllowanceLedger.MODE_TIME_BUDGET &&
            target.mode != AllowanceLedger.MODE_INTERVAL
        ) return

        val record = ledger.usage(target.packageName)
        val startMs = maxOf(
            segmentCheckpointAtMs,
            segmentStartedAtMs,
            record.confirmedAtMs,
            if (target.mode == AllowanceLedger.MODE_INTERVAL) target.windowStartMs else 0L,
        )
        val segmentStart = segmentStartedAtMs.takeIf { it > 0L } ?: startMs
        var endMs = AllowanceUsageTimeAccounting.cappedEnd(
            segmentStartedAtMs = segmentStart,
            requestedEndMs = requestedEndMs,
        )
        if (target.mode == AllowanceLedger.MODE_INTERVAL && target.windowStartMs > 0L) {
            endMs = minOf(endMs, target.windowStartMs + target.windowMs)
        }

        AllowanceUsageTimeAccounting.splitAtLocalMidnight(startMs, endMs, zoneId)
            .forEach { slice ->
                ledger.addEstimatedTimeUsage(
                    packageName = target.packageName,
                    mode = target.mode,
                    today = slice.localDate,
                    windowStartMs = target.windowStartMs,
                    deltaMs = slice.durationMs,
                )
            }
    }
}
