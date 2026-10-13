package com.tbtechs.focusflow.data.repository

import android.util.Log
import com.tbtechs.focusflow.data.model.CanonicalTimestamp
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.data.restore.RestoreGate
import com.tbtechs.focusflow.notifications.ReminderChainScheduler
import com.tbtechs.focusflow.notifications.ReminderChainLedger
import com.tbtechs.focusflow.notifications.ReminderNotificationPublisher
import com.tbtechs.focusflow.notifications.ReminderPlanner
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DesiredTaskEndAlarm(
    val taskId: String,
    val taskName: String,
    val endTimeMillis: Long,
)

object TaskAlarmReconcilePlan {
    const val MAX_ARMED_ALARMS = 100

    fun desired(
        tasks: List<Task>,
        nowMs: Long,
        limit: Int = MAX_ARMED_ALARMS,
    ): List<DesiredTaskEndAlarm> =
        tasks.asSequence()
            .filter { it.status == "scheduled" || it.status == "active" }
            .map { task ->
                val endMs = runCatching { Instant.parse(task.endTime).toEpochMilli() }
                    .getOrElse {
                        throw IllegalStateException(
                            "A task has an invalid end time and cannot be reconciled.",
                        )
                    }
                DesiredTaskEndAlarm(task.id, task.title, endMs)
            }
            .filter { it.endTimeMillis > nowMs }
            .sortedBy(DesiredTaskEndAlarm::endTimeMillis)
            .take(limit)
            .toList()
}

/**
 * Rebuilds task-end alarms and the reminder chain from Room, then synchronizes
 * the scheduled-task status card.
 */
class TaskAlarmReconciler(
    private val taskRepository: TaskRepository,
    private val alarmRepository: AlarmRepository,
    private val restoreGate: RestoreGate,
    private val settingsRepository: SettingsRepository,
    private val reminderChainScheduler: ReminderChainScheduler,
    private val reminderChainLedger: ReminderChainLedger,
    private val cancelReminderNotification: (String) -> Unit,
    private val syncLiveTaskStatus: (List<Task>, Long) -> Unit = { _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val reconcileMutex = Mutex()

    suspend fun reconcile(reason: String) =
        restoreGate.write("TaskAlarmReconciler:$reason") {
            reconcileMutex.withLock { reconcileLocked(reason) }
        }

    private suspend fun reconcileLocked(reason: String) {
        var passes = 0
        while (passes++ <= TaskAlarmReconcilePlan.MAX_ARMED_ALARMS) {
            val nowMs = clock()
            val canonicalNow = CanonicalTimestamp.format(Instant.ofEpochMilli(nowMs))
            taskRepository.markOverdue(canonicalNow)

            val tasks = taskRepository.getAllTasks()
            val desired = TaskAlarmReconcilePlan.desired(tasks, clock())
            val desiredIds = desired.mapTo(mutableSetOf(), DesiredTaskEndAlarm::taskId)
            val registeredIds = alarmRepository.registeredTaskIds()

            (registeredIds - desiredIds).forEach { taskId ->
                check(alarmRepository.cancelTaskEndAlarmWithinReconciliation(taskId)) {
                    "A task-end alarm no longer desired by Room could not be cancelled."
                }
            }

            alarmRepository.prepareDesiredTaskEndAlarms(
                desired.associate { it.taskId to it.endTimeMillis },
            )

            var foundPastTrigger = false
            for (alarm in desired) {
                when (
                    val result = alarmRepository.scheduleTaskEndAlarmWithinReconciliation(
                        alarm.taskId,
                        alarm.taskName,
                        alarm.endTimeMillis,
                    )
                ) {
                    AlarmScheduleResult.Scheduled,
                    AlarmScheduleResult.DeferredExactUnavailable -> Unit
                    AlarmScheduleResult.PastTrigger -> {
                        // Time can advance while the horizon is being armed.
                        // Cancel this identity, sweep the now-overdue row, and
                        // recompute so the next future task enters the horizon.
                        check(
                            alarmRepository.cancelTaskEndAlarmWithinReconciliation(alarm.taskId),
                        ) { "A past task-end alarm could not be cancelled." }
                        foundPastTrigger = true
                        break
                    }
                    is AlarmScheduleResult.Failed -> throw IllegalStateException(
                        "Could not schedule a task-end alarm.",
                        result.cause,
                    )
                }
            }

            if (!foundPastTrigger) {
                val reminderNowMs = clock()
                reminderChainLedger.cancelNotificationsForIneligibleTasks(
                    tasks = tasks,
                    cancel = cancelReminderNotification,
                )
                val settings = settingsRepository.readAppSettings()
                val reminderPlan = ReminderPlanner.plan(
                    tasks = tasks,
                    nowMs = reminderNowMs,
                    remindersEnabled = settings.taskRemindersEnabled,
                    preStartRemindersEnabled = settings.taskPreStartRemindersEnabled,
                    atStartReminderEnabled = settings.taskAtStartReminderEnabled,
                    midSessionRemindersEnabled = settings.taskMidSessionRemindersEnabled,
                    wrapUpReminderEnabled = settings.taskWrapUpReminderEnabled,
                )
                reminderChainScheduler.rearm(reminderPlan, reminderNowMs)
                syncLiveTaskStatus(tasks, reminderNowMs)
                Log.i(TAG, "Task-end alarms reconciled reason=$reason count=${desired.size}")
                return
            }
        }
        throw IllegalStateException("Task-end alarm reconciliation did not converge.")
    }

    companion object {
        private const val TAG = "TaskAlarmReconciler"
    }
}