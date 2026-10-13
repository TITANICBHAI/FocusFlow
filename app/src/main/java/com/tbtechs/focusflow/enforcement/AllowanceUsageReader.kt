package com.tbtechs.focusflow.enforcement

import android.content.Context
import com.tbtechs.focusflow.analytics.ForegroundSpanTracker
import com.tbtechs.focusflow.analytics.UsageEventRead
import com.tbtechs.focusflow.data.repository.UsageStatsRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private fun createPlatformEventSource(
    context: Context,
): suspend (Long, Long) -> UsageEventRead {
    val source = UsageStatsRepository(context.applicationContext)
    return { startMs, endMs -> source.readForegroundEvents(startMs, endMs) }
}

internal data class AllowanceUsageReadOutcome(
    val read: UsageEventRead,
    val activeSessionStartedAtMs: Long? = null,
    val completedAtMs: Long,
)

/** Performs the UsageEvents read and reconciles every configured allowance. */
internal class AllowanceUsageReader(
    private val readEvents: suspend (Long, Long) -> UsageEventRead,
    packageName: String,
    private val ledger: AllowanceLedger,
    private val zoneId: ZoneId,
    private val onReconciled: (String) -> Unit,
    private val readTimeoutMs: Long = READ_TIMEOUT_MS,
    private val clockNowMs: () -> Long = { System.currentTimeMillis() },
) {
    constructor(
        context: Context,
        ledger: AllowanceLedger,
        zoneId: ZoneId,
        onReconciled: (String) -> Unit,
    ) : this(
        readEvents = createPlatformEventSource(context),
        packageName = context.applicationContext.packageName,
        ledger = ledger,
        zoneId = zoneId,
        onReconciled = onReconciled,
    )

    private val tracker = ForegroundSpanTracker(
        excludedPackages = ForegroundSpanTracker.DEFAULT_EXCLUDED_PACKAGES + packageName,
    )

    suspend fun readAndReconcile(
        targets: List<AllowanceUsageTarget>,
        queryStartMs: Long,
        nowMs: Long,
        activePackage: String?,
    ): AllowanceUsageReadOutcome {
        val read = try {
            withTimeout(readTimeoutMs) {
                readEvents(queryStartMs, nowMs)
            }
        } catch (_: TimeoutCancellationException) {
            UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.EVENTS_UNAVAILABLE)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.ACCESS_REVOKED)
        } catch (_: Exception) {
            UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.EVENTS_UNAVAILABLE)
        }
        val completedAtMs = clockNowMs()

        if (read !is UsageEventRead.Available) {
            return AllowanceUsageReadOutcome(read = read, completedAtMs = completedAtMs)
        }

        val sessions = tracker.sessions(
            events = read.events,
            windowStartMs = queryStartMs,
            windowEndMs = nowMs,
            nowMs = nowMs,
        )
        val currentSessionStart = activePackage?.let { pkg ->
            sessions.lastOrNull {
                it.packageName.equals(pkg, ignoreCase = true) &&
                    it.isInferredTail &&
                    it.startedAtMs <= nowMs
            }?.startedAtMs
        }
        val todayDate = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val todayStartMs = todayDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val today = todayDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

        targets.forEach { target ->
            val measurement = AllowanceUsagePipeline.measure(
                target = target,
                sessions = sessions,
                todayStartMs = todayStartMs,
                nowMs = nowMs,
                zoneId = zoneId,
            )
            when (target.mode) {
                AllowanceLedger.MODE_COUNT -> ledger.reconcileCountUsage(
                    target.packageName,
                    today,
                    measurement.count,
                    completedAtMs,
                )
                AllowanceLedger.MODE_TIME_BUDGET,
                AllowanceLedger.MODE_INTERVAL -> ledger.reconcileTimeUsage(
                    packageName = target.packageName,
                    mode = target.mode,
                    today = today,
                    windowStartMs = target.windowStartMs,
                    usedMs = measurement.usedMs,
                    atMs = completedAtMs,
                )
            }
            onReconciled(target.packageName)
        }
        return AllowanceUsageReadOutcome(
            read = read,
            activeSessionStartedAtMs = currentSessionStart,
            completedAtMs = completedAtMs,
        )
    }

    private companion object {
        const val READ_TIMEOUT_MS = 5_000L
    }
}
