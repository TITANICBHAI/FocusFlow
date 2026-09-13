package com.tbtechs.focusflow.data.repository

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.tbtechs.focusflow.enforcement.ForegroundTaskService
import com.tbtechs.focusflow.enforcement.TaskAlarmActivity
import com.tbtechs.focusflow.enforcement.receivers.TaskEndAlarmReceiver

/**
 * AlarmRepository
 *
 * Converted from TaskAlarmModule.
 * Interacts with the device's native AlarmManager so task end-time alarms
 * fire reliably even when the app is in Doze or the process has been killed.
 *
 * Responsibilities:
 *   1. scheduleAlarm — Fallback ladder: setAlarmClock -> setExactAndAllowWhileIdle -> setAndAllowWhileIdle
 *   2. cancelAlarm — cancels AlarmManager registration for a given taskId
 *   3. dismissAlarm — finishes TaskAlarmActivity and clears notification
 *   4. canScheduleExactAlarms / requestExactAlarmPermission — probes and opens settings on Android 12+
 */
class AlarmRepository(private val context: Context) {

    companion object {
        private const val TAG = "AlarmRepository"

        /**
         * Stable hash -> request code so the same taskId always maps to the same PendingIntent.
         */
        fun requestCodeFor(taskId: String): Int {
            val h = taskId.hashCode()
            return if (h == Int.MIN_VALUE) 0 else Math.abs(h)
        }

        /** Build the canonical alarm PendingIntent for a given taskId. */
        fun buildAlarmPendingIntent(
            ctx: Context,
            taskId: String,
            taskName: String,
            endMs: Long,
            flags: Int,
        ): PendingIntent {
            val intent = Intent(ctx.applicationContext, TaskEndAlarmReceiver::class.java).apply {
                action = TaskEndAlarmReceiver.ACTION_FIRE
                `package` = ctx.packageName
                putExtra(TaskEndAlarmReceiver.EXTRA_TASK_ID,   taskId)
                putExtra(TaskEndAlarmReceiver.EXTRA_TASK_NAME, taskName)
                putExtra(TaskEndAlarmReceiver.EXTRA_END_MS,    endMs)
            }
            return PendingIntent.getBroadcast(
                ctx.applicationContext,
                requestCodeFor(taskId),
                intent,
                flags,
            )
        }
    }

    /**
     * Schedules a wake-up alarm at [endMs] that posts the full-screen task-end alarm.
     * Replaces any earlier registration for the same taskId.
     *
     * Named Risk Preserved:
     * Fallback ladder setAlarmClock -> setExactAndAllowWhileIdle -> setAndAllowWhileIdle,
     * tried in that order, gated by canScheduleExactAlarms() on API 31+.
     */
    suspend fun scheduleAlarm(taskId: String?, taskName: String?, endMs: Long): Boolean {
        return try {
            val id = taskId ?: ""
            val name = taskName ?: ""
            val triggerAt = endMs

            if (id.isEmpty()) {
                Log.w(TAG, "scheduleAlarm: empty taskId — refusing to schedule")
                return false
            }
            if (triggerAt <= System.currentTimeMillis()) {
                // Caller is rescheduling something that has already ended —
                // fire immediately so the user still gets the alarm UI and
                // the task moves to awaiting-decision state.
                Log.i(TAG, "scheduleAlarm: triggerAt is in the past — posting alarm now")
                ForegroundTaskService.postTaskEndAlarmNotification(
                    context.applicationContext, id, name, triggerAt,
                )
                return true
            }

            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (am == null) {
                Log.e(TAG, "scheduleAlarm: AlarmManager unavailable")
                return false
            }

            val pi = buildAlarmPendingIntent(
                context, id, name, triggerAt,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            // Strategy ladder, strictest first:
            //   1. setAlarmClock — Doze-immune, shown in lockscreen alarm row.
            //      Requires SCHEDULE_EXACT_ALARM (auto-granted with USE_EXACT_ALARM
            //      on API 33+) or USE_EXACT_ALARM on API 31-32.
            //   2. setExactAndAllowWhileIdle — fires within ~10s of trigger
            //      even in Doze. Used when 1 fails (no exact-alarm permission
            //      or OEM rejects setAlarmClock).
            //   3. setAndAllowWhileIdle — coarse fallback for OS versions or
            //      OEM ROMs that reject the exact APIs entirely. May be off
            //      by minutes but at least the alarm eventually fires.
            val showIntent = Intent(context, TaskAlarmActivity::class.java)
            val showPi = PendingIntent.getActivity(
                context.applicationContext,
                requestCodeFor(id) xor 0x55AA55AA,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            var scheduled = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPi), pi)
                        scheduled = true
                    }
                } else {
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPi), pi)
                    scheduled = true
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "setAlarmClock denied: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "setAlarmClock failed: ${e.message}")
            }

            if (!scheduled) {
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    scheduled = true
                } catch (e: SecurityException) {
                    Log.w(TAG, "setExactAndAllowWhileIdle denied: ${e.message}")
                } catch (e: Exception) {
                    Log.w(TAG, "setExactAndAllowWhileIdle failed: ${e.message}")
                }
            }

            if (!scheduled) {
                try {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    scheduled = true
                } catch (e: Exception) {
                    Log.e(TAG, "setAndAllowWhileIdle failed — alarm will NOT fire: ${e.message}")
                }
            }

            Log.i(TAG, "scheduleAlarm taskId=$id name='$name' endMs=$triggerAt scheduled=$scheduled")
            scheduled
        } catch (e: Exception) {
            Log.e(TAG, "scheduleAlarm crashed: ${e.message}", e)
            false
        }
    }

    /** Overload for Double millisecond timestamp compatibility */
    suspend fun scheduleAlarm(taskId: String?, taskName: String?, endMs: Double): Boolean =
        scheduleAlarm(taskId, taskName, endMs.toLong())

    /**
     * Cancels any previously-scheduled alarm for this taskId. Safe to call
     * even if no alarm exists — PendingIntent.cancel() is a no-op in that case.
     */
    suspend fun cancelAlarm(taskId: String?): Boolean {
        return try {
            val id = taskId ?: ""
            if (id.isEmpty()) return true

            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val existing = buildAlarmPendingIntent(
                context, id, "", 0L,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (existing != null) {
                am?.cancel(existing)
                existing.cancel()
            }
            Log.i(TAG, "cancelAlarm taskId=$id existed=${existing != null}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "cancelAlarm failed: ${e.message}")
            false
        }
    }

    /**
     * Finishes visible TaskAlarmActivity and cancels the alarm notification.
     */
    suspend fun dismissAlarm(taskId: String?): Boolean {
        return try {
            val intent = Intent(TaskAlarmActivity.ACTION_DISMISS_ALARM).apply {
                `package` = context.packageName
                if (!taskId.isNullOrEmpty()) {
                    putExtra(TaskAlarmActivity.EXTRA_TASK_ID, taskId)
                }
            }
            context.sendBroadcast(intent)

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(ForegroundTaskService.TASK_ALARM_NOTIF_ID)

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
}
