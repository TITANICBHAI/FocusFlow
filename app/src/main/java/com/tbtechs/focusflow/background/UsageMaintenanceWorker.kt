package com.tbtechs.focusflow.background

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tbtechs.focusflow.analytics.UsageRollupPass
import com.tbtechs.focusflow.data.local.entity.UsagePipelineStateEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupDayEntity
import com.tbtechs.focusflow.di.AppModule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class UsageMaintenanceWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val clock = Clock.systemDefaultZone()
        val zoneId = ZoneId.systemDefault()
        val state = SharedPreferencesUsageMaintenanceState(
            applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        )
        val operations = object : UsageMaintenanceOperations {
            override suspend fun writeRecentRollups(nowMs: Long): MaintenanceRollupResult {
                val pass = AppModule.usageRollupWriter.writeRecentPastDays(nowMs)
                return when (pass) {
                    is UsageRollupPass.Written -> MaintenanceRollupResult.Written
                    is UsageRollupPass.Unknown -> MaintenanceRollupResult.Unknown(pass.reason)
                }
            }

            override suspend fun areRequiredDaysComplete(
                today: LocalDate,
                zoneId: ZoneId,
            ): Boolean {
                val firstDate = today.minusDays(UsageMaintenanceJob.REQUIRED_ROLLUP_DAYS)
                val lastDate = today.minusDays(1)
                val rows = AppModule.database.usageRollupDao()
                    .getDays(firstDate.toString(), lastDate.toString())
                    .associateBy { it.date }
                return (1L..UsageMaintenanceJob.REQUIRED_ROLLUP_DAYS).all { daysAgo ->
                    val row = rows[today.minusDays(daysAgo).toString()]
                    row?.status == UsageRollupDayEntity.COMPLETE &&
                        row.pipelineVersion == UsagePipelineStateEntity.CURRENT_PIPELINE_VERSION
                }
            }

            override suspend fun runDetection(today: LocalDate, zoneId: ZoneId) {
                AppModule.findingDetectionRunner.runAll()
            }

            override suspend fun prune(cutoffDate: LocalDate, cutoffInstant: Instant) {
                AppModule.database.dailyAppUsageDao().deleteOlderThan(cutoffDate.toString())
                AppModule.database.appSessionDao().deleteOlderThan(cutoffDate.toString())
                AppModule.database.findingDao().deleteOldResolved(cutoffInstant.toString())
                AppModule.database.clarifyingQuestionDao()
                    .deleteAnsweredBefore(cutoffInstant.toString())
            }
        }

        return try {
            when (
                UsageMaintenanceJob(
                    operations = operations,
                    state = state,
                    clock = clock,
                    zoneId = zoneId,
                ).run()
            ) {
                UsageMaintenanceRunResult.SUCCESS -> Result.success()
                UsageMaintenanceRunResult.RETRY -> Result.retry()
            }
        } catch (error: Exception) {
            Log.w(TAG, "Usage maintenance failed; WorkManager will retry", error)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "UsageMaintenance"
        private const val PREFERENCES_NAME = "focusday_prefs"

        fun enqueuePeriodic(context: Context) {
            UsageMaintenanceStartupScheduler { schedule ->
                val operation = WorkManager.getInstance(context.applicationContext)
                    .enqueueUniquePeriodicWork(
                        schedule.workName,
                        when (schedule.existingWorkPolicy) {
                            MaintenanceExistingWorkPolicy.KEEP -> ExistingPeriodicWorkPolicy.KEEP
                        },
                        PeriodicWorkRequestBuilder<UsageMaintenanceWorker>(
                            schedule.intervalMinutes,
                            TimeUnit.MINUTES,
                        ).build(),
                    )
                operation.result.addListener(
                    {
                        runCatching { operation.result.get() }
                            .onSuccess {
                                Log.i(
                                    TAG,
                                    "Periodic schedule observed: " +
                                        "${schedule.workName}, ${schedule.intervalMinutes}m, " +
                                        "${schedule.existingWorkPolicy}",
                                )
                            }
                            .onFailure {
                                Log.e(TAG, "Periodic schedule failed: ${schedule.workName}", it)
                            }
                    },
                    Executor { command -> command.run() },
                )
            }.schedule()
        }
    }
}

private class SharedPreferencesUsageMaintenanceState(
    private val preferences: android.content.SharedPreferences,
) : UsageMaintenanceState {
    override suspend fun lastDetectionRunDate(): LocalDate? =
        preferences.getString(LAST_DETECTION_RUN_DATE, null).toLocalDateOrNull()

    override suspend fun markDetectionRun(date: LocalDate) {
        preferences.edit().putString(LAST_DETECTION_RUN_DATE, date.toString()).apply()
    }

    override suspend fun lastPruneDate(): LocalDate? =
        preferences.getString(LAST_PRUNE_DATE, null).toLocalDateOrNull()

    override suspend fun markPruned(date: LocalDate) {
        preferences.edit().putString(LAST_PRUNE_DATE, date.toString()).apply()
    }

    private fun String?.toLocalDateOrNull(): LocalDate? =
        this?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }

    companion object {
        private const val LAST_DETECTION_RUN_DATE = "last_detection_run_date"
        private const val LAST_PRUNE_DATE = "last_prune_date"
    }
}
