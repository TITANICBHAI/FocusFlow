package com.tbtechs.focusflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.data.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * TaskViewModel
 *
 * Single source of truth for task state in the UI layer.
 *
 * Backed by: [TaskRepository] (Track A Room output).
 *
 * All methods are backed by real [TaskRepository] calls — no plausible stubs.
 *
 * Notes for GPT Terra:
 *   - [tasks] is a hot StateFlow; collect it with `collectAsStateWithLifecycle()`.
 *   - [completeTask], [skipTask], [extendTaskTime] find the target task from the
 *     current StateFlow value and call [TaskRepository.updateTask]. They are no-ops
 *     if the taskId is not found in the current snapshot.
 *   - No FocusSessionRepository dependency here — stopping a focus session when a
 *     task is completed/skipped is [FocusSessionViewModel]'s responsibility.
 */
class TaskViewModel(
    private val taskRepository: TaskRepository,
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

    // ─── Mutating methods ─────────────────────────────────────────────────────

    /**
     * Inserts [task] into the database. Duplicate IDs are silently ignored
     * (INSERT OR IGNORE semantics from [TaskRepository.insertTask]).
     *
     * Backing call: [TaskRepository.insertTask] → [TaskDao.insertTask]
     */
    fun addTask(task: Task) {
        viewModelScope.launch { taskRepository.insertTask(task) }
    }

    /**
     * Updates [task] by primary key.
     *
     * Backing call: [TaskRepository.updateTask] → [TaskDao.updateTask]
     */
    fun updateTask(task: Task) {
        viewModelScope.launch { taskRepository.updateTask(task) }
    }

    /**
     * Deletes the task with [taskId].
     *
     * Backing call: [TaskRepository.deleteTask] → [TaskDao.deleteTask]
     */
    fun deleteTask(taskId: String) {
        viewModelScope.launch { taskRepository.deleteTask(taskId) }
    }

    /** Bulk deletion used by Settings; the screen gates this while Focus is active. */
    fun clearAllTasks() {
        viewModelScope.launch { taskRepository.deleteAllTasks() }
    }

    /**
     * Marks the task with [taskId] as "completed" and stamps [updatedAt].
     * No-op if the task is not found in [tasks].
     *
     * Backing call: [TaskRepository.updateTask] (status mutation happens in ViewModel)
     */
    fun completeTask(taskId: String) {
        viewModelScope.launch {
            val task = tasks.value.firstOrNull { it.id == taskId } ?: return@launch
            taskRepository.updateTask(
                task.copy(
                    status    = "completed",
                    updatedAt = Instant.now().toString(),
                )
            )
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
            val task = tasks.value.firstOrNull { it.id == taskId } ?: return@launch
            taskRepository.updateTask(
                task.copy(
                    status    = "skipped",
                    updatedAt = Instant.now().toString(),
                )
            )
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
            val task    = tasks.value.firstOrNull { it.id == taskId } ?: return@launch
            val newEnd  = Instant.parse(task.endTime).plusMillis(minutes * 60_000L)
            taskRepository.updateTask(
                task.copy(
                    endTime         = newEnd.toString(),
                    durationMinutes = task.durationMinutes + minutes,
                    updatedAt       = Instant.now().toString(),
                )
            )
        }
    }
}
