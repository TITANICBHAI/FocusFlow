package com.tbtechs.focusflow.enforcement.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.tbtechs.focusflow.enforcement.ForegroundTaskService
import com.tbtechs.focusflow.data.repository.AlarmRuntimeDiagnostics
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.data.repository.TaskEndAlarmValidation
import com.tbtechs.focusflow.ui.common.AppErrorEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * TaskEndAlarmReceiver
 *
 * Fired by AlarmManager.setAlarmClock() at a task's end time. This is the
 * *primary* alarm trigger — independent of the foreground service's in-process
 * Handler polling, which the OS aggressively throttles in Doze mode and which
 * stops entirely if the service is killed.
 *
 * On receive:
 *   1. Acquire a partial wakelock so we have CPU time to post the notification
 *      before the device returns to Doze.
 *   2. Read taskId / taskName / endMs from the alarm intent extras.
 *   3. Post the same heads-up + full-screen-intent notification that the
 *      foreground service used to post via its Handler poll. The notification's
 *      full-screen intent launches [TaskAlarmActivity], waking the device,
 *      playing the alarm ringtone, and showing Done / Extend / Skip.
 *   4. Broadcast ACTION_TASK_ENDED so the foreground service (if alive) can
 *      clean up its in-memory session state and switch its persistent
 *      notification back to idle.
 *
 * Wire-up:
 *   - JS schedules via TaskAlarmModule.scheduleAlarm(taskId, taskName, endMs).
 *   - The receiver is declared (exported=false) by withFocusDayAndroid plugin.
 *   - The receiver intent uses an explicit class target so it works without an
 *     intent-filter, but we keep ACTION_FIRE for log-grep convenience.
 */
class TaskEndAlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "TaskEndAlarmReceiver"
        private const val RECEIVER_BUDGET_MS = 8_000L
        private const val ROOM_READ_BUDGET_MS = 3_000L

        /**
         * Action used by the alarm intent. Receiver is targeted by explicit
         * component, so this string is informational only — it shows up in
         * `adb shell dumpsys alarm` and in our own logging, making it easy to
         * confirm a pending alarm is scheduled for the right task.
         */
        const val ACTION_FIRE     = "com.tbtechs.focusflow.alarm.FIRE_TASK_END"

        const val EXTRA_TASK_ID   = "taskId"
        const val EXTRA_TASK_NAME = "taskName"
        const val EXTRA_END_MS    = "endTimeMs"

        /** Tag attached to the wakelock — visible in `dumpsys power` for triage. */
        private const val WAKELOCK_TAG = "FocusFlow:TaskEndAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        AlarmRuntimeDiagnostics.record("receiver.onReceive")
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        if (taskId.isBlank()) {
            Log.w(TAG, "Ignoring task-end alarm without a task ID.")
            return
        }
        val taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: ""
        val endMs = intent.getLongExtra(EXTRA_END_MS, Long.MIN_VALUE)
        if (endMs == Long.MIN_VALUE) {
            Log.w(TAG, "Ignoring task-end alarm without an end time.")
            return
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wl = try {
            pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)?.also {
                it.setReferenceCounted(false)
                it.acquire(RECEIVER_BUDGET_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wakelock acquire failed: ${e.message}")
            null
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(RECEIVER_BUDGET_MS) {
                    AppModule.restoreGate.write("TaskEndAlarmReceiver") {
                        runCatching {
                            AppModule.alarmRepository.recordFireCapabilitySnapshot(taskId)
                        }.onFailure {
                            Log.w(TAG, "Could not record fire-time capabilities.")
                        }
                        val lookup = withTimeoutOrNull(ROOM_READ_BUDGET_MS) {
                            try {
                                TaskLookup.FoundOrMissing(AppModule.taskRepository.getTaskById(taskId))
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Log.w(TAG, "Task lookup failed; delivering alarm fail-open.", error)
                                TaskLookup.Unavailable
                            }
                        } ?: TaskLookup.Unavailable

                        val nowMs = System.currentTimeMillis()
                        when (lookup) {
                            TaskLookup.Unavailable -> {
                                AppErrorEvents.report(
                                    tag = "Task alarms",
                                    message = "A task-end alert was delivered without database validation.",
                                )
                            }
                            is TaskLookup.FoundOrMissing -> {
                                val task = lookup.task
                                when (TaskEndAlarmValidation.evaluate(
                                    taskStatus = task?.status,
                                    endMs = endMs,
                                    registeredEndMs = AppModule.alarmRepository
                                        .triggerAtMillis(taskId),
                                    nowMs = nowMs,
                                )) {
                                    TaskEndAlarmValidation.Decision.SUPPRESS_STALE_TRIGGER ->
                                        return@write
                                    TaskEndAlarmValidation.Decision.SUPPRESS_MISSING_OR_TERMINAL -> {
                                        // A rescheduled alarm for the same ID must
                                        // survive an already-delivered stale intent.
                                        if (
                                            AppModule.alarmRepository.triggerAtMillis(taskId) == endMs
                                        ) {
                                            AppModule.alarmRepository.cancelAlarm(taskId)
                                        }
                                        return@write
                                    }
                                    TaskEndAlarmValidation.Decision.SUPPRESS_EARLY -> {
                                        if (
                                            AppModule.alarmRepository.triggerAtMillis(taskId) == endMs
                                        ) {
                                            AppModule.alarmRepository.cancelAlarm(taskId)
                                        }
                                        AppModule.taskAlarmReconciler.reconcile("early_fire")
                                        return@write
                                    }
                                    TaskEndAlarmValidation.Decision.DELIVER -> Unit
                                }
                            }
                        }

                        val validatedTask =
                            (lookup as? TaskLookup.FoundOrMissing)?.task
                        val displayName = validatedTask?.title ?: taskName
                        ForegroundTaskService.postTaskEndAlarmNotification(
                            context.applicationContext,
                            taskId,
                            displayName,
                            endMs,
                        )

                        try {
                            context.sendBroadcast(
                                Intent(ForegroundTaskService.ACTION_TASK_ENDED).apply {
                                    `package` = context.packageName
                                    putExtra(ForegroundTaskService.EXTRA_TASK_ID, taskId)
                                },
                            )
                        } catch (error: Exception) {
                            Log.w(TAG, "ACTION_TASK_ENDED broadcast failed.", error)
                        }

                        AppModule.taskAlarmReconciler.reconcile("fired")
                    }
                }
            } catch (error: TimeoutCancellationException) {
                Log.w(TAG, "Task-end receiver exceeded its 8-second budget.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Task-end alarm handling failed.", error)
            } finally {
                try {
                    if (wl?.isHeld == true) wl.release()
                } catch (_: Exception) {
                }
                pendingResult.finish()
            }
        }
    }

    private sealed interface TaskLookup {
        data class FoundOrMissing(val task: com.tbtechs.focusflow.data.model.Task?) : TaskLookup
        data object Unavailable : TaskLookup
    }
}
