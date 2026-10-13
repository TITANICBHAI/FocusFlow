package com.tbtechs.focusflow.background

import com.tbtechs.focusflow.analytics.UsageEventRead
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

sealed interface MaintenanceRollupResult {
    data object Written : MaintenanceRollupResult
    data class Unknown(
        val reason: UsageEventRead.Unknown.Reason,
    ) : MaintenanceRollupResult
}

interface UsageMaintenanceOperations {
    suspend fun writeRecentRollups(nowMs: Long): MaintenanceRollupResult
    suspend fun areRequiredDaysComplete(today: LocalDate, zoneId: ZoneId): Boolean
    suspend fun runDetection(today: LocalDate, zoneId: ZoneId)
    suspend fun prune(cutoffDate: LocalDate, cutoffInstant: Instant)
}

interface UsageMaintenanceState {
    suspend fun lastDetectionRunDate(): LocalDate?
    suspend fun markDetectionRun(date: LocalDate)
    suspend fun lastPruneDate(): LocalDate?
    suspend fun markPruned(date: LocalDate)
}

enum class UsageMaintenanceRunResult {
    SUCCESS,
    RETRY,
}

enum class MaintenanceExistingWorkPolicy {
    KEEP,
}

data class UsageMaintenanceSchedule(
    val workName: String,
    val intervalMinutes: Long,
    val existingWorkPolicy: MaintenanceExistingWorkPolicy,
)

fun interface UsageMaintenanceScheduleEnqueuer {
    fun enqueue(schedule: UsageMaintenanceSchedule)
}

class UsageMaintenanceStartupScheduler(
    private val enqueuer: UsageMaintenanceScheduleEnqueuer,
) {
    fun schedule() {
        enqueuer.enqueue(STARTUP_SCHEDULE)
    }

    companion object {
        val STARTUP_SCHEDULE = UsageMaintenanceSchedule(
            workName = "FOCUSDAY_USAGE_MAINTENANCE",
            intervalMinutes = 15L,
            existingWorkPolicy = MaintenanceExistingWorkPolicy.KEEP,
        )
    }
}

/**
 * Coordinates the daily maintenance pass without Android or WorkManager
 * dependencies, so ordering, retries, and date boundaries remain testable.
 */
class UsageMaintenanceJob(
    private val operations: UsageMaintenanceOperations,
    private val state: UsageMaintenanceState,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    suspend fun run(): UsageMaintenanceRunResult {
        val now = clock.instant()
        val today = now.atZone(zoneId).toLocalDate()
        val rollupResult = operations.writeRecentRollups(now.toEpochMilli())
        var retry = false

        if (state.lastDetectionRunDate() != today) {
            when (rollupResult) {
                MaintenanceRollupResult.Written -> {
                    if (operations.areRequiredDaysComplete(today, zoneId)) {
                        operations.runDetection(today, zoneId)
                        state.markDetectionRun(today)
                    } else {
                        retry = true
                    }
                }

                is MaintenanceRollupResult.Unknown -> {
                    if (rollupResult.reason.isPermissionDenied()) {
                        operations.runDetection(today, zoneId)
                        state.markDetectionRun(today)
                    } else {
                        retry = true
                    }
                }
            }
        } else if (
            rollupResult is MaintenanceRollupResult.Unknown &&
            !rollupResult.reason.isPermissionDenied()
        ) {
            retry = true
        }

        if (state.lastPruneDate() != today) {
            operations.prune(
                cutoffDate = today.minusDays(PRUNE_RETENTION_DAYS),
                cutoffInstant = now.minus(PRUNE_RETENTION_DAYS, ChronoUnit.DAYS),
            )
            state.markPruned(today)
        }

        return if (retry) UsageMaintenanceRunResult.RETRY else UsageMaintenanceRunResult.SUCCESS
    }

    private fun UsageEventRead.Unknown.Reason.isPermissionDenied(): Boolean =
        this == UsageEventRead.Unknown.Reason.PERMISSION_MISSING ||
            this == UsageEventRead.Unknown.Reason.ACCESS_REVOKED

    companion object {
        const val REQUIRED_ROLLUP_DAYS = 3L
        const val PRUNE_RETENTION_DAYS = 90L
    }
}
