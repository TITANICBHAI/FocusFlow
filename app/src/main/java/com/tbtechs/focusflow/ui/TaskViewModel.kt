package com.tbtechs.focusflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.data.repository.AlarmRepository
import com.tbtechs.focusflow.data.repository.TaskAlarmReconciler
import com.tbtechs.focusflow.data.repository.FocusSessionRepository
import com.tbtechs.focusflow.data.repository.ForegroundServiceController
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.TaskRepository
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.domain.SchedulerEngine
import com.tbtechs.focusflow.ui.common.AppErrorEvents
import java.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manages the task list and exposes CRUD operations backed by Room.
 *
 * ## State
 * - [tasks]: reactive [StateFlow] fed by [TaskRepository.observeAllTasks].
 *   Emits a new list whenever any task is inserted, updated, or deleted.
 *
 * ## Mutating methods
 * All mutating calls launch a coroutine on [viewModelScope] and delegate to
 * [TaskRepository]. Status changes (completing, skipping) update the task in
 * place and stamp [Task.updatedAt].
 *
 * ## PIN enforcement on deletion
 * [deleteTask] and [clearAllTasks] accept a caller-supplied [pinHash]. When the
 * user has configured a session PIN (via Settings), the deletion must not
 * proceed unless the PIN has been verified. The PIN check is delegated to
 * [beforeTaskDelete] and [beforeClearTasks] lambdas passed at construction time
 * (typically wired to [SettingsViewModel.verifySessionPin]).
 *
 * ## Separation of concerns
 * This ViewModel only owns task records. Stopping a running focus session when a
 * task is completed/skipped is [FocusSessionViewModel]'s responsibility.
 */
class TaskViewModel(
    private val taskRepository: TaskRepository,
    private val alarmRepository: AlarmRepository,
    private val taskAlarmReconciler: TaskAlarmReconciler,
    private val focusSessionRepository: FocusSessionRepository,
    private val foregroundServiceController: ForegroundServiceController,
    private val settingsRepository: SettingsRepository,
    private val schedulerEngine: SchedulerEngine,
    private val beforeTaskDelete: suspend (taskId: String, pinHash: String?) -> Unit = { _, _ -> },
    private val beforeClearTasks: suspend (pinHash: String?) -> Unit = { _ -> },
) : ViewModel() {

    // ─── State ────────────────────────────────────────────────────────────────

    /**
     * Reactive stream of all tasks ordered by start time.
     * Room re-emits on every write; UI collectors see updates without explicit refresh.
     *
     * Backing call: [TaskRepository.observeAllTasks] → [TaskDao.observeAllTasks]
     */
    val tasks: StateFlow<List<Task>> = taskRepository.observeAllTasks()
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue   = emptyList(),
        )

    /**
     * Loads tasks whose local-calendar dates fall within the inclusive range.
     * The repository converts the ISO timestamps to the device's local dates
     * before querying Room.
     */
    suspend fun getTasksInDateRange(startISO: String, endISO: String): List<Task> =
        taskRepository.getTasksInDateRange(startISO, endISO)

    // ─── Mutating methods ─────────────────────────────────────────────────────
    //
    /**
     * Inserts [task] into the database. Duplicate IDs are silently ignored
     * (INSERT OR IGNORE semantics from [TaskRepository.insertTask]).
     *
     * Backing call: [TaskRepository.insertTask] → [TaskDao.insertTask]
     */
    fun addTask(task: Task) {
        viewModelScope.launch {
            taskRepository.withTaskOperationLock {
                if (!hasValidTimeRange(task)) {
                    reportInvalidTimeRange()
                    return@withTaskOperationLock
                }
                taskRepository.insertTask(task)
                reconcileAlarms("task_created")
            }
        }
    }

    /**
     * Updates [task] by primary key.
     *
     * Backing call: [TaskRepository.updateTask] → [TaskDao.updateTask]
     */
    fun updateTask(task: Task) {
        viewModelScope.launch {
            taskRepository.withTaskOperationLock {
                if (!hasValidTimeRange(task)) {
                    reportInvalidTimeRange()
                    return@withTaskOperationLock
                }
                taskRepository.updateTask(task)
                reconcileAlarms("task_updated")
            }
        }
    }

    /**
     * Deletes the task with [taskId].
     *
     * Calls [beforeTaskDelete] before mutating Room. If [beforeTaskDelete]
     * throws (e.g. invalid PIN), the deletion does not proceed.
     *
     * Backing call: [TaskRepository.deleteTask] → [TaskDao.deleteTask]
     */
    fun deleteTask(taskId: String, pinHash: String? = null) {
        viewModelScope.launch {
            taskRepository.withTaskOperationLock {
                beforeTaskDelete(taskId, pinHash)
                val task = taskRepository.getTaskById(taskId)
                    ?: return@withTaskOperationLock
                alarmRepository.dismissAlarm(taskId)
                taskRepository.deleteTask(taskId)
                if (shouldAutoReschedule() && task.status !in COMPLETED_STATUSES) {
                    val rescheduled = schedulerEngine.compressDeletedTaskGap(
                        deletedTask = task,
                        allTasks = taskRepository.getAllTasks(),
                    )
                    persistRescheduledTasks(rescheduled, excludedTaskId = task.id)
                }
                reconcileAlarms("task_deleted")
            }
        }
    }

    /**
     * Deletes all tasks from the database.
     *
     * Calls [beforeClearTasks] before mutating Room. If [beforeClearTasks]
     * throws, the clear does not proceed.
     *
     * Backing call: [TaskRepository.clearAllTasks] → [TaskDao.clearAllTasks]
     */
    fun clearAllTasks(pinHash: String? = null) {
        viewModelScope.launch {
            taskRepository.withTaskOperationLock {
                beforeClearTasks(pinHash)
                val allTasks = taskRepository.getAllTasks()
                allTasks.forEach {
                    alarmRepository.dismissAlarm(it.id)
                }
                taskRepository.deleteAllTasks()
                reconcileAlarms("tasks_cleared")
            }
        }
    }

    /**
     * Deletes all tasks except the one with [excludedTaskId].
     * Used by Settings when an active focus session must be preserved.
     */
    fun clearAllTasksExcept(excludedTaskId: String, pinHash: String? = null) {
        viewModelScope.launch {
            taskRepository.withTaskOperationLock {
                val activeSession = focusSessionRepository.getActiveFocusSession()
                if (activeSession?.taskId != excludedTaskId) {
                    beforeClearTasks(pinHash)
                }
                val allTasks = taskRepository.getAllTasks()
                allTasks.filter { it.id != excludedTaskId }.forEach {
                    alarmRepository.dismissAlarm(it.id)
                }
                taskRepository.deleteAllTasksExcept(excludedTaskId)
                reconcileAlarms("tasks_cleared_except_active")
            }
        }
    }

    /**
     * Marks the task with [taskId] as "completed" and stamps [updatedAt].
     * No-op if the task is already completed or skipped.
     *
     * Backing call: [TaskRepository.updateTask] (status mutation happens in ViewModel)
     */
    fun completeTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.withTaskOperationLock {
                val task = taskRepository.getTaskById(taskId) ?: return@withTaskOperationLock
                if (task.status == "completed" || task.status == "skipped") return@withTaskOperationLock
                val completedAt = Instant.now()
                taskRepository.updateTask(
                    task.copy(status = "completed", updatedAt = completedAt.toString()),
                )
                alarmRepository.dismissAlarm(taskId)
                if (shouldAutoReschedule()) {
                    val rescheduled = schedulerEngine.compressSchedule(
                        completedTask = task,
                        completedAt = completedAt.toString(),
                        allTasks = taskRepository.getAllTasks(),
                    )
                    persistRescheduledTasks(rescheduled, excludedTaskId = task.id)
                }
                reconcileAlarms("task_completed")
            }
        }
    }

    /**
     * Marks the task with [taskId] as "skipped" and stamps [updatedAt].
     * No-op if the task is not found in [tasks].
     *
     * Backing call: [TaskRepository.updateTask] (status mutation happens in ViewModel)
     */
    fun skipTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.withTaskOperationLock {
                val task = taskRepository.getTaskById(taskId) ?: return@withTaskOperationLock
                if (task.status == "completed" || task.status == "skipped") return@withTaskOperationLock
                taskRepository.updateTask(
                    task.copy(status = "skipped", updatedAt = Instant.now().toString()),
                )
                alarmRepository.dismissAlarm(taskId)
                if (shouldAutoReschedule()) {
                    // Skipping frees the task's full scheduled slot. Treat it
                    // like deletion so later scheduled tasks move forward.
                    val rescheduled = schedulerEngine.compressDeletedTaskGap(
                        deletedTask = task,
                        allTasks = taskRepository.getAllTasks(),
                    )
                    persistRescheduledTasks(rescheduled, excludedTaskId = task.id)
                }
                reconcileAlarms("task_skipped")
            }
        }
    }

    /**
     * Extends the end time and duration of the task with [taskId] by [minutes].
     * No-op if the task is not found in [tasks].
     *
     * Backing call: [TaskRepository.updateTask] (time mutation happens in ViewModel)
     */
    fun extendTaskTime(taskId: String, minutes: Int) {
        viewModelScope.launch {
            taskRepository.withTaskOperationLock {
                val task = taskRepository.getTaskById(taskId) ?: return@withTaskOperationLock
                if (task.status == "completed" || task.status == "skipped") {
                    return@withTaskOperationLock
                }
                val newEnd = Instant.parse(task.endTime).plusMillis(minutes * 60_000L)
                taskRepository.updateTask(
                    task.copy(
                        endTime = newEnd.toString(),
                        durationMinutes = task.durationMinutes + minutes,
                        updatedAt = Instant.now().toString(),
                    ),
                )
                val newEndMs = newEnd.toEpochMilli()
                reconcileAlarms("task_extended")
                if (focusSessionRepository.getActiveFocusSession()?.taskId == taskId) {
                    foregroundServiceController.updateNotification(
                        taskId = taskId,
                        taskName = task.title,
                        endTimeMs = newEndMs,
                        nextName = null,
                    )
                }
            }
        }
    }

    private fun hasValidTimeRange(task: Task): Boolean = runCatching {
        Instant.parse(task.endTime).isAfter(Instant.parse(task.startTime))
    }.getOrDefault(false)

    private fun reportInvalidTimeRange() {
        AppErrorEvents.report(
            tag = "Tasks",
            message = "A task's end time must be after its start time.",
        )
    }

    private suspend fun shouldAutoReschedule(): Boolean =
        settingsRepository.readAppSettings().autoRescheduleEnabled

    /**
     * Persists only schedule rows whose time window changed and keeps their
     * AlarmManager entries synchronized with the new end times.
     *
     * Callers already hold [TaskRepository.withTaskOperationLock], so the
     * Room writes and alarm changes cannot interleave with another task edit.
     */
    private suspend fun persistRescheduledTasks(
        transformedTasks: List<Task>,
        excludedTaskId: String,
    ) {
        val currentTasks = taskRepository.getAllTasks().associateBy(Task::id)
        val changedTasks = transformedTasks
            .filter { it.id != excludedTaskId }
            .filter { currentTasks[it.id] != it }
        if (changedTasks.isEmpty()) return

        taskRepository.updateTasksBatch(changedTasks)
    }

    private suspend fun reconcileAlarms(reason: String) {
        try {
            taskAlarmReconciler.reconcile(reason)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            AppErrorEvents.report(
                tag = "Task alarms",
                message = "Task changes were saved, but task-end alarms need reconciliation.",
                throwable = error,
            )
        }
    }

    companion object {
        private val COMPLETED_STATUSES = setOf("completed", "skipped")

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TaskViewModel(
                    taskRepository = AppModule.taskRepository,
                    alarmRepository = AppModule.alarmRepository,
                    taskAlarmReconciler = AppModule.taskAlarmReconciler,
                    focusSessionRepository = AppModule.focusSessionRepository,
                    foregroundServiceController = AppModule.foregroundServiceController,
                    settingsRepository = AppModule.settingsRepository,
                    schedulerEngine = AppModule.schedulerEngine,
                ) as T
            }

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return TaskViewModel(
                    taskRepository = AppModule.taskRepository,
                    alarmRepository = AppModule.alarmRepository,
                    taskAlarmReconciler = AppModule.taskAlarmReconciler,
                    focusSessionRepository = AppModule.focusSessionRepository,
                    foregroundServiceController = AppModule.foregroundServiceController,
                    settingsRepository = AppModule.settingsRepository,
                    schedulerEngine = AppModule.schedulerEngine,
                ) as T
            }
        }
    }
}
