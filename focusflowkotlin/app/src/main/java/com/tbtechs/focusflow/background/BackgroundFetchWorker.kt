package com.tbtechs.focusflow.background

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tbtechs.focusflow.domain.Task
import com.tbtechs.focusflow.domain.TaskStatus
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.math.floor

/**
 * The pure-Kotlin replacement for the BACKGROUND_FETCH task in backgroundTasks.ts.
 *
 * The database and notification repositories are a later migration layer, so the
 * worker talks through [BackgroundFetchGateway]. A missing gateway is an explicit
 * configuration failure, never a silent no-op.
 */
class BackgroundFetchWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val gateway = BackgroundFetchDependencies.create(applicationContext)
            ?: return Result.failure(
                workDataOf("error" to "BackgroundFetchGateway is not configured"),
            )

        return try {
            val now = dependenciesClock.instant()
            val today = now.atZone(ZoneOffset.UTC).toLocalDate().toString()
            val tasks = gateway.getTasksForDate(today)
            val tasksToRearm = mutableListOf<Task>()

            for (task in tasks) {
                if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.SKIPPED) {
                    continue
                }

                val start = parseInstant(task.startTime)
                val end = parseInstant(task.endTime)
                val minutesLate = floor(
                    Duration.between(start, now).toMillis() / 60_000.0,
                ).toInt()

                if (
                    task.status == TaskStatus.SCHEDULED &&
                    minutesLate in 3..15
                ) {
                    gateway.fireLateStartWarning(task, minutesLate)
                    continue
                }

                if (end < now) continue
                tasksToRearm += task
            }

            gateway.scheduleTaskRemindersBatch(tasksToRearm)

            val currentHour = now.atZone(ZoneId.systemDefault()).hour
            if (currentHour >= 20) {
                val wakeUpTime = gateway.getWakeUpTime()
                if (!wakeUpTime.isNullOrBlank()) {
                    gateway.scheduleMorningDigest(wakeUpTime, tasks)
                }
            }

            Result.success(
                workDataOf("rearmedCount" to tasksToRearm.size),
            )
        } catch (error: Exception) {
            Log.w(TAG, "BACKGROUND_FETCH failed", error)
            Result.failure(
                workDataOf("error" to (error.message ?: "BACKGROUND_FETCH failed")),
            )
        }
    }

    private val dependenciesClock: Clock
        get() = BackgroundFetchDependencies.clock

    private fun parseInstant(value: String) =
        runCatching { java.time.Instant.parse(value) }.getOrElse {
            java.time.OffsetDateTime.parse(value).toInstant()
        }

    companion object {
        private const val TAG = "BackgroundFetchWorker"
        const val WORK_NAME = "FOCUSDAY_BACKGROUND_FETCH"
        const val INTERVAL_MINUTES = 15L

        /**
         * Enqueues the one surviving periodic background job.
         *
         * WorkManager persists this unique periodic request across process death
         * and device reboot. The OS may run it less often than 15 minutes.
         */
        fun enqueuePeriodic(context: Context) =
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BackgroundFetchWorker>(
                    INTERVAL_MINUTES,
                    TimeUnit.MINUTES,
                ).build(),
            )
    }
}

/**
 * Adapter boundary for the future Room TaskRepository and notification layer.
 */
interface BackgroundFetchGateway {
    suspend fun getTasksForDate(isoDate: String): List<Task>
    suspend fun fireLateStartWarning(task: Task, minutesLate: Int)
    suspend fun scheduleTaskRemindersBatch(tasks: List<Task>)
    suspend fun getWakeUpTime(): String?
    suspend fun scheduleMorningDigest(wakeUpTime: String, tasks: List<Task>)
}

/**
 * WorkManager constructs workers reflectively, so the repository/notification
 * adapter is installed by application startup rather than passed to the worker
 * constructor. Tests can install an in-memory gateway and a fixed Clock here.
 */
object BackgroundFetchDependencies {
    @Volatile
    private var factory: ((Context) -> BackgroundFetchGateway)? = null

    @Volatile
    var clock: Clock = Clock.systemUTC()

    fun install(factory: (Context) -> BackgroundFetchGateway) {
        this.factory = factory
    }

    fun create(context: Context): BackgroundFetchGateway? =
        factory?.invoke(context.applicationContext)
}