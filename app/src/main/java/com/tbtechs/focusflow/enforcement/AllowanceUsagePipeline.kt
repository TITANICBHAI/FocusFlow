package com.tbtechs.focusflow.enforcement

import com.tbtechs.focusflow.analytics.ForegroundSession
import java.time.Instant
import java.time.ZoneId

enum class AllowanceUsageFreshness {
    FRESH,
    STALE,
    UNAVAILABLE,
}

object AllowanceUsageFreshnessReducer {
    const val FRESH_FOR_MS = 60_000L
    const val NO_READ_GRACE_MS = 5 * 60_000L

    fun reduce(
        hasUsageAccess: Boolean,
        lastSuccessfulReadAtMs: Long,
        unlockedAtMs: Long,
        nowMs: Long,
    ): AllowanceUsageFreshness {
        if (!hasUsageAccess) return AllowanceUsageFreshness.UNAVAILABLE
        if (
            lastSuccessfulReadAtMs > 0L &&
            nowMs >= lastSuccessfulReadAtMs &&
            nowMs - lastSuccessfulReadAtMs <= FRESH_FOR_MS
        ) {
            return AllowanceUsageFreshness.FRESH
        }
        if (
            lastSuccessfulReadAtMs <= 0L &&
            unlockedAtMs > 0L &&
            nowMs - unlockedAtMs >= NO_READ_GRACE_MS
        ) {
            return AllowanceUsageFreshness.UNAVAILABLE
        }
        return AllowanceUsageFreshness.STALE
    }
}

data class AllowanceUsageTarget(
    val packageName: String,
    val mode: String,
    val windowStartMs: Long = 0L,
    val windowMs: Long = 0L,
)

data class AllowancePipelineMeasurement(
    val usedMs: Long = 0L,
    val count: Int = 0,
)

/**
 * Allowance values are derived from the same paired foreground sessions used
 * by Stats. System permission and chooser screens are a narrow exception for
 * open counting only; their time is never credited to the allowance app.
 */
object AllowanceUsagePipeline {
    const val DIALOG_BRIDGE_MAX_MS = 3_000L

    val dialogBridgePackages = setOf(
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.intentresolver",
        "com.google.android.intentresolver",
    )

    fun measure(
        target: AllowanceUsageTarget,
        sessions: List<ForegroundSession>,
        todayStartMs: Long,
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): AllowancePipelineMeasurement {
        val interval = target.mode == AllowanceLedger.MODE_INTERVAL
        val rangeStart = if (interval) target.windowStartMs else todayStartMs
        if (rangeStart < 0L || rangeStart >= nowMs) {
            return AllowancePipelineMeasurement()
        }
        val rangeEnd = if (interval && target.windowMs > 0L) {
            minOf(nowMs, target.windowStartMs + target.windowMs)
        } else {
            nowMs
        }
        val targetSessions = sessions
            .asSequence()
            .filter { it.packageName.equals(target.packageName, ignoreCase = true) }
            .sortedBy { it.startedAtMs }
            .toList()
        val usedMs = targetSessions.sumOf { session ->
            (minOf(session.endedAtMs, rangeEnd) - maxOf(session.startedAtMs, rangeStart))
                .coerceAtLeast(0L)
        }
        val count = targetSessions.count { session ->
            session.startedAtMs in todayStartMs until nowMs &&
                session.isNewOpen &&
                !isDialogBridgeReturn(
                    session = session,
                    targetSessions = targetSessions,
                    allSessions = sessions,
                )
        }
        // The date conversion intentionally happens on calendar time. It keeps
        // the current local-day boundary explicit for DST transitions.
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val startDate = Instant.ofEpochMilli(todayStartMs).atZone(zoneId).toLocalDate()
        return if (today == startDate) {
            AllowancePipelineMeasurement(usedMs = usedMs, count = count)
        } else {
            AllowancePipelineMeasurement(usedMs = usedMs, count = 0)
        }
    }

    private fun isDialogBridgeReturn(
        session: ForegroundSession,
        targetSessions: List<ForegroundSession>,
        allSessions: List<ForegroundSession>,
    ): Boolean {
        val previousTarget = targetSessions
            .lastOrNull { it.startedAtMs < session.startedAtMs }
            ?: return false
        if (session.startedAtMs - previousTarget.endedAtMs > DIALOG_BRIDGE_MAX_MS) return false
        val interruptions = allSessions.filter {
            it.startedAtMs >= previousTarget.endedAtMs &&
                it.endedAtMs <= session.startedAtMs &&
                !it.packageName.equals(session.packageName, ignoreCase = true)
        }
        return interruptions.isNotEmpty() && interruptions.all {
            it.packageName in dialogBridgePackages
        }
    }
}
