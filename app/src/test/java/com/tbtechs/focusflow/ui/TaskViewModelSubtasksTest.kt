package com.tbtechs.focusflow.ui

import com.tbtechs.focusflow.data.local.dao.TaskDao
import com.tbtechs.focusflow.data.local.entity.TaskEntity
import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.repository.AlarmRepository
import com.tbtechs.focusflow.data.repository.FocusSessionRepository
import com.tbtechs.focusflow.data.repository.ForegroundServiceController
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.TaskAlarmReconciler
import com.tbtechs.focusflow.data.repository.TaskRepository
import com.tbtechs.focusflow.domain.SchedulerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class TaskViewModelSubtasksTest {
    @Test
    fun subtaskActionsUseColumnWritesAndDoNotReconcileWhileTaskEditsPreserveProgress() {
        try {
            runTest {
                Dispatchers.setMain(StandardTestDispatcher(testScheduler))
                val original = taskEntity()
                val dao = FakeTaskDao(original)
                val repository = TaskRepository(dao.proxy)
                var alarmReconciliations = 0
                val viewModel = TaskViewModel(
                    taskRepository = repository,
                    alarmRepository = uninitialized(),
                    taskAlarmReconciler = uninitialized(),
                    focusSessionRepository = uninitialized(),
                    foregroundServiceController = uninitialized(),
                    settingsRepository = uninitialized(),
                    schedulerEngine = uninitialized(),
                    reconcileTaskAlarms = { alarmReconciliations++ },
                )

                viewModel.setSubtaskSetsDone("task-1", "sets", setsDone = 99)
                runCurrent()
                val completedSets = repository.getTaskById("task-1")!!.subtasks.single { it.id == "sets" }
                assertEquals(3, completedSets.setsDone)
                assertTrue(completedSets.done)

                viewModel.setSubtaskDone("task-1", "read", done = true)
                runCurrent()
                viewModel.resetSubtasks("task-1")
                runCurrent()

                val progressBeforeEdit = repository.getTaskById("task-1")!!.subtasks
                assertEquals(listOf(false, false), progressBeforeEdit.map(Subtask::done))
                assertEquals(listOf(0, 0), progressBeforeEdit.map(Subtask::setsDone))
                assertEquals(0, alarmReconciliations)
                assertEquals(0, dao.fullTaskWrites)
                assertTrue(dao.subtaskWrites >= 3)

                val edited = repository.getTaskById("task-1")!!.copy(
                    title = "Edited title",
                    subtasks = emptyList(),
                )
                viewModel.updateTask(edited)
                runCurrent()

                val afterEdit = repository.getTaskById("task-1")!!
                assertEquals("Edited title", afterEdit.title)
                assertEquals(progressBeforeEdit, afterEdit.subtasks)
                assertEquals(1, dao.fullTaskWrites)
                assertEquals(1, alarmReconciliations)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun taskEntity() = TaskEntity(
        id = "task-1",
        title = "Workout",
        description = null,
        startTime = "2026-10-20T08:00:00Z",
        endTime = "2026-10-20T09:00:00Z",
        durationMinutes = 60,
        status = "scheduled",
        priority = "medium",
        tags = "[]",
        reminders = "[]",
        color = "#6366f1",
        focusMode = false,
        focusAllowedPackages = null,
        createdAt = "2026-10-19T08:00:00Z",
        updatedAt = "2026-10-19T09:00:00Z",
        subtasks = """
            [
              {"id":"sets","title":"Sets","goal":{"kind":"sets","sets":3,"reps":8}},
              {"id":"read","title":"Read"}
            ]
        """.trimIndent(),
    )

    // Test-only placeholders for services that subtask mutations must not call.
    private inline fun <reified T : Any> uninitialized(): T =
        uninitialized(T::class.java)

    private fun <T : Any> uninitialized(type: Class<T>): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null)
        @Suppress("UNCHECKED_CAST")
        return unsafeClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, type) as T
    }

    private class FakeTaskDao(initial: TaskEntity) : InvocationHandler {
        private val rows = mutableMapOf(initial.id to initial)
        var subtaskWrites = 0
            private set
        var fullTaskWrites = 0
            private set
        val proxy: TaskDao = Proxy.newProxyInstance(
            TaskDao::class.java.classLoader,
            arrayOf(TaskDao::class.java),
            this,
        ) as TaskDao

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            val values = args.orEmpty()
            return when (method.name) {
                "getTaskById" -> rows[values[0] as String]
                "updateSubtasks" -> {
                    subtaskWrites++
                    val taskId = values[0] as String
                    val current = rows[taskId] ?: return 0
                    rows[taskId] = current.copy(subtasks = values[1] as String)
                    1
                }
                "updateTask" -> {
                    fullTaskWrites++
                    val task = values[0] as TaskEntity
                    rows[task.id] = task
                    Unit
                }
                "observeAllTasks" -> flowOf(rows.values.toList())
                "toString" -> "FakeTaskDao"
                else -> error("Unexpected TaskDao call: ${method.name}")
            }
        }
    }
}
