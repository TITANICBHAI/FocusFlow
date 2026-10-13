package com.tbtechs.focusflow.data.repository

import android.app.AlarmManager
import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.tbtechs.focusflow.data.restore.RestoreGate
import com.tbtechs.focusflow.enforcement.TaskAlarmActivity
import com.tbtechs.focusflow.enforcement.receivers.TaskEndAlarmReceiver
import com.tbtechs.focusflow.ui.common.AppErrorEvents

/**
 * AlarmRepository
 *
 * Converted from TaskAlarmModule.
 * Interacts with the device's native AlarmManager so task end-time alarms
 * fire reliably even when the app is in Doze or the process has been killed.
 *
 * Responsibilities:
 *   1. scheduleAlarm — exact-only ladder: setAlarmClock -> setExactAndAllowWhileIdle
 *   2. cancelAlarm — cancels AlarmManager registration for a given taskId
 *   3. dismissAlarm — finishes TaskAlarmActivity and clears notification
 *   4. canScheduleExactAlarms / requestExactAlarmPermission — probes and opens settings on Android 12+
 */
class AlarmRepository(
    private val context: Context,
    private val restoreGate: RestoreGate,
) {
    private val registry = TaskAlarmRegistry(context)

    companion object {
        private const val TAG = "AlarmRepository"

        private fun alarmIntent(
            ctx: Context,
            taskId: String,
            taskName: String,
            endMs: Long,
        ): Intent =
            Intent(ctx.applicationContext, TaskEndAlarmReceiver::class.java).apply {
                action = TaskEndAlarmReceiver.ACTION_FIRE
                data = TaskEndAlarmIdentity.dataUri(taskId)
                `package` = ctx.packageName
                putExtra(TaskEndAlarmReceiver.EXTRA_TASK_ID, taskId)
                putExtra(TaskEndAlarmReceiver.EXTRA_TASK_NAME, taskName)
                putExtra(TaskEndAlarmReceiver.EXTRA_END_MS, endMs)
            }

        private fun showIntent(
            ctx: Context,
            taskId: String,
            taskName: String,
            endMs: Long,
            ringAlarm: Boolean = true,
        ): Intent =
            Intent(ctx.applicationContext, TaskAlarmActivity::class.java).apply {
                action = TaskAlarmActivity.ACTION_SHOW_ALARM
                data = TaskEndAlarmIdentity.dataUri(taskId)
                `package` = ctx.packageName
                putExtra(TaskAlarmActivity.EXTRA_TASK_ID, taskId)
                putExtra(TaskAlarmActivity.EXTRA_TASK_NAME, taskName)
                putExtra(TaskAlarmActivity.EXTRA_END_MS, endMs)
                putExtra(TaskAlarmActivity.EXTRA_RING_ALARM, ringAlarm)
            }

        /** Build the canonical alarm PendingIntent for a given taskId. */
        fun buildAlarmPendingIntent(
            ctx: Context,
            taskId: String,
            taskName: String,
            endMs: Long,
            flags: Int,
        ): PendingIntent {
            return PendingIntent.getBroadcast(
                ctx.applicationContext,
                TaskEndAlarmIdentity.REQUEST_CODE,
                alarmIntent(ctx, taskId, taskName, endMs),
                flags,
            )
        }

        /** Find an existing task-end alarm without creating one. */
        internal fun findAlarmPendingIntent(
            ctx: Context,
            taskId: String,
        ): PendingIntent? =
            PendingIntent.getBroadcast(
                ctx.applicationContext,
                TaskEndAlarmIdentity.REQUEST_CODE,
                alarmIntent(ctx, taskId, "", 0L),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )

        /** Build the task-specific show/full-screen Activity PendingIntent. */
        fun buildShowPendingIntent(
            ctx: Context,
            taskId: String,
            taskName: String,
            endMs: Long,
            flags: Int,
            ringAlarm: Boolean = true,
        ): PendingIntent {
            val intent = showIntent(ctx, taskId, taskName, endMs, ringAlarm)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                val options = ActivityOptions.makeBasic().apply {
                    setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                }
                PendingIntent.getActivity(
                    ctx.applicationContext,
                    TaskEndAlarmIdentity.REQUEST_CODE,
                    intent,
                    flags,
                    options.toBundle(),
                )
            } else {
                PendingIntent.getActivity(
                    ctx.applicationContext,
                    TaskEndAlarmIdentity.REQUEST_CODE,
                    intent,
                    flags,
                )
            }
        }

        /** Find an existing task-end Activity PendingIntent without creating one. */
        internal fun findShowPendingIntent(
            ctx: Context,
            taskId: String,
        ): PendingIntent? {
            val intent = showIntent(ctx, taskId, "", 0L)
            val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                val options = ActivityOptions.makeBasic().apply {
                    setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                }
                PendingIntent.getActivity(
                    ctx.applicationContext,
                    TaskEndAlarmIdentity.REQUEST_CODE,
                    intent,
                    flags,
                    options.toBundle(),
                )
            } else {
                PendingIntent.getActivity(
                    ctx.applicationContext,
                    TaskEndAlarmIdentity.REQUEST_CODE,
                    intent,
                    flags,
                )
            }
        }
    }

    /**
     * Schedules a wake-up alarm at [endMs] that posts the full-screen task-end alarm.
     * Replaces any earlier registration for the same taskId.
     */
    suspend fun scheduleTaskEndAlarm(
        taskId: String?,
        taskName: String?,
        endMs: Long,
    ): AlarmScheduleResult = restoreGate.write("AlarmRepository.scheduleTaskEndAlarm") {
        scheduleTaskEndAlarmWithinReconciliation(taskId, taskName, endMs)
    }

    /**
     * Used only by TaskAlarmReconciler while it owns the restore gate (or is
     * running as the restore coordinator with the gate deliberately closed).
     */
    internal fun scheduleTaskEndAlarmWithinReconciliation(
        taskId: String?,
        taskName: String?,
        endMs: Long,
    ): AlarmScheduleResult {
        val id = taskId?.takeIf(String::isNotBlank)
            ?: return AlarmScheduleResult.Failed(IllegalArgumentException("Task ID is required."))
        if (endMs <= System.currentTimeMillis()) return AlarmScheduleResult.PastTrigger

        return try {
            registry.prepareDesired(mapOf(id to endMs))
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return AlarmScheduleResult.Failed(
                    IllegalStateException("AlarmManager is unavailable."),
                ).also {
                    AlarmCapabilitySnapshot.record(context, "schedule", "FAILED")
                }

            val exactAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (!exactAccess) return deferred(id)

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val alarmPi = buildAlarmPendingIntent(
                context,
                id,
                taskName.orEmpty(),
                endMs,
                flags,
            )
            val showPi = buildShowPendingIntent(
                context,
                id,
                taskName.orEmpty(),
                endMs,
                flags,
                ringAlarm = SettingsRepository.taskEndAlarmRings(context),
            )

            try {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(endMs, showPi), alarmPi)
                registry.markScheduled(id, AlarmTier.ALARM_CLOCK)
                AlarmCapabilitySnapshot.record(context, "schedule", AlarmTier.ALARM_CLOCK.name)
                Log.i(TAG, "Scheduled task-end alarm with alarm-clock tier.")
                return AlarmScheduleResult.Scheduled
            } catch (error: Exception) {
                Log.w(TAG, "setAlarmClock failed; trying exact allow-while-idle.", error)
            }

            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMs, alarmPi)
                registry.markScheduled(id, AlarmTier.EXACT_ALLOW_IDLE)
                AlarmCapabilitySnapshot.record(context, "schedule", AlarmTier.EXACT_ALLOW_IDLE.name)
                Log.i(TAG, "Scheduled task-end alarm with exact allow-while-idle tier.")
                AlarmScheduleResult.Scheduled
            } catch (error: Exception) {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !alarmManager.canScheduleExactAlarms()
                ) {
                    deferred(id)
                } else {
                    registry.markFailed(id)
                    AlarmCapabilitySnapshot.record(context, "schedule", "FAILED")
                    AlarmScheduleResult.Failed(error)
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Task-end alarm scheduling failed.", error)
            AlarmScheduleResult.Failed(error)
        }
    }

    private fun deferred(taskId: String): AlarmScheduleResult {
        if (registry.markDeferred(taskId)) {
            AppErrorEvents.report(
                tag = "Task alarms",
                message = "Exact alarm access is off. Task-end alerts are deferred; enable Exact Alarms in Permissions to restore them.",
            )
        }
        AlarmCapabilitySnapshot.record(
            context,
            "schedule",
            "DEFERRED_EXACT_UNAVAILABLE",
        )
        return AlarmScheduleResult.DeferredExactUnavailable
    }

    /** Compatibility wrapper used by the currently dormant notification adapter. */
    suspend fun scheduleAlarm(taskId: String?, taskName: String?, endMs: Long): Boolean =
        when (scheduleTaskEndAlarm(taskId, taskName, endMs)) {
            AlarmScheduleResult.Scheduled,
            AlarmScheduleResult.PastTrigger,
            AlarmScheduleResult.DeferredExactUnavailable -> true
            is AlarmScheduleResult.Failed -> false
        }

    suspend fun scheduleAlarm(taskId: String?, taskName: String?, endMs: Double): Boolean =
        scheduleAlarm(taskId, taskName, endMs.toLong())

    /**
     * Cancels any previously-scheduled alarm for this taskId. Safe to call
     * even if no alarm exists — PendingIntent.cancel() is a no-op in that case.
     */
    suspend fun cancelAlarm(taskId: String?): Boolean =
        restoreGate.write("AlarmRepository.cancelAlarm") {
            cancelTaskEndAlarmWithinReconciliation(taskId)
        }

    internal fun registeredTaskIds(): Set<String> = registry.registeredTaskIds()

    internal fun triggerAtMillis(taskId: String): Long? = registry.triggerAtMillis(taskId)

    internal fun recordFireCapabilitySnapshot(taskId: String) {
        val tier = registry.tierFor(taskId)?.name ?: "UNKNOWN"
        AlarmCapabilitySnapshot.record(context, "fire", tier)
    }

    internal fun prepareDesiredTaskEndAlarms(alarms: Map<String, Long>) =
        registry.prepareDesired(alarms)

    internal fun cancelTaskEndAlarmWithinReconciliation(taskId: String?): Boolean {
        val id = taskId?.takeIf(String::isNotBlank) ?: return true
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return false
            val alarmPi = findAlarmPendingIntent(context, id)
            val showPi = findShowPendingIntent(context, id)
            if (alarmPi != null) {
                alarmManager.cancel(alarmPi)
                alarmPi.cancel()
            }
            if (showPi != null) showPi.cancel()
            registry.remove(id)
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(
                TaskEndAlarmIdentity.notificationTag(id),
                TaskEndAlarmIdentity.NOTIFICATION_ID,
            )
            true
        } catch (error: Exception) {
            Log.w(TAG, "Could not cancel task-end alarm.", error)
            false
        }
    }

    /**
     * Finishes visible TaskAlarmActivity and cancels the alarm notification.
     */
    suspend fun dismissAlarm(taskId: String?): Boolean =
        restoreGate.write("AlarmRepository.dismissAlarm") {
            dismissAlarmWithinGate(taskId)
        }

    private fun dismissAlarmWithinGate(taskId: String?): Boolean {
        return try {
            val intent = Intent(TaskAlarmActivity.ACTION_DISMISS_ALARM).apply {
                `package` = context.packageName
                if (!taskId.isNullOrEmpty()) {
                    putExtra(TaskAlarmActivity.EXTRA_TASK_ID, taskId)
                }
            }
            context.sendBroadcast(intent)

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (!taskId.isNullOrEmpty()) {
                nm?.cancel(
                    TaskEndAlarmIdentity.notificationTag(taskId),
                    TaskEndAlarmIdentity.NOTIFICATION_ID,
                )
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns whether the OS will honour exact alarm scheduling. On API < 31
     * this is always true. On API 31+ the user must grant "Alarms & reminders"
     * in app settings (or the app must hold USE_EXACT_ALARM).
     */
    suspend fun canScheduleExactAlarms(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return true
            }
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.canScheduleExactAlarms() ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.canUseFullScreenIntent() == true
        } catch (_: Exception) {
            false
        }
    }

    fun markFullScreenIntentPromptShownOnce(): Boolean =
        registry.markFullScreenPromptShownOnce()

    fun capabilitySnapshots(): List<AlarmCapabilitySnapshotRecord> =
        registry.capabilitySnapshots()

    fun markNotificationPostedOnce(
        taskId: String,
        endMs: Long,
        post: () -> Unit,
    ): Boolean = registry.postOnce(taskId, endMs, System.currentTimeMillis(), post)

    /**
     * Opens the system "Alarms & reminders" settings screen for this app.
     * Resolves true if the settings activity could be launched.
     */
    suspend fun requestExactAlarmPermission(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return true
            }
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "requestExactAlarmPermission failed: ${e.message}")
            false
        }
    }

    suspend fun requestFullScreenIntentPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (error: Exception) {
            Log.w(TAG, "Could not open full-screen intent settings.", error)
            false
        }
    }
}
