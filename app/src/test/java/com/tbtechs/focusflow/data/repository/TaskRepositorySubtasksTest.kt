package com.tbtechs.focusflow.data.repository

import com.tbtechs.focusflow.data.local.dao.TaskDao
import com.tbtechs.focusflow.data.local.entity.TaskEntity
import com.tbtechs.focusflow.data.images.ImageBounds
import com.tbtechs.focusflow.data.images.ImageCodec
import com.tbtechs.focusflow.data.images.SubtaskImageStore
import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.SubtaskLimits
import com.tbtechs.focusflow.data.model.SubtaskStep
import com.tbtechs.focusflow.data.model.Task
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.nio.file.Files

class TaskRepositorySubtasksTest {
    @Test
    fun decodesValidElementsWhenAnotherStoredSubtaskIsMalformed() = runTest {
        val dao = FakeTaskDao(taskEntity(subtasks = """
            [
              {"id":"valid","title":"Keep this"},
              {"id":"broken","title":"Skip this","steps":17}
            ]
        """.trimIndent()))
        val task = TaskRepository(dao.proxy).getTaskById("task-1")

        assertEquals(listOf("valid"), task?.subtasks?.map(Subtask::id))
    }

    @Test
    fun mutatesOnlySubtaskColumnAndPreservesOtherTaskDataAndUpdatedAt() = runTest {
        val original = taskEntity(subtasks = """[{"id":"lift","title":"Lift"}]""")
        val dao = FakeTaskDao(original)
        val repository = TaskRepository(dao.proxy)

        val changed = repository.mutateSubtasks("task-1") { current ->
            current.map { it.copy(done = true) }
        }

        assertTrue(changed)
        assertEquals(original.copy(subtasks = """[{"id":"lift","title":"Lift","done":true}]"""), dao.rows["task-1"])
    }

    @Test
    fun missingTaskMutationReturnsFalseAndOversizeReplacementDoesNotWrite() = runTest {
        val original = taskEntity()
        val dao = FakeTaskDao(original)
        val repository = TaskRepository(dao.proxy)

        assertFalse(repository.mutateSubtasks("missing") { it })
        val oversized = List(3) { index ->
            Subtask(
                id = "large-$index",
                title = "Large",
                explanation = "x".repeat(SubtaskLimits.MAX_EXPLANATION_CHARS),
                steps = List(SubtaskLimits.MAX_STEPS) {
                    SubtaskStep(text = "y".repeat(SubtaskLimits.MAX_STEP_TEXT_CHARS))
                },
            )
        }
        val error = runCatching { repository.replaceSubtasks("task-1", oversized) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(original, dao.rows["task-1"])
    }

    @Test
    fun subtaskJsonCacheIsBoundedAndEmptyTaskExportOmitsNewField() {
        val cache = SubtaskJsonCache(Json)
        repeat(SubtaskJsonCache.MAX_ENTRIES + 30) { index ->
            cache.decode("""[{"id":"s$index","title":"Task"}]""")
        }
        assertEquals(SubtaskJsonCache.MAX_ENTRIES, cache.size())

        val json = Json { encodeDefaults = true; explicitNulls = false }
        val encodedEmptyTask = json.encodeToString(taskDomain())
        assertFalse(encodedEmptyTask.contains("subtasks"))
        val encodedWithSubtasks = json.encodeToString(taskDomain().copy(subtasks = listOf(Subtask("s1", "Read"))))
        assertTrue(encodedWithSubtasks.contains("subtasks"))
    }

    @Test
    fun orphanSweepKeepsReferencedImagesAndFailsClosedOnMalformedRows() = runTest {
        val directory = Files.createTempDirectory("repository-image-sweep").toFile()
        try {
            var now = System.currentTimeMillis()
            val store = SubtaskImageStore(
                directory = directory,
                codec = StoredWebpCodec(),
                clockMillis = { now },
            )
            val referencedId = store.ingest(png()).getOrThrow()
            now += SubtaskImageStore.GRACE_PERIOD_MILLIS + 1

            val validDao = FakeTaskDao(
                taskEntity(
                    subtasks = """[{"id":"sub","title":"Keep image","image":{"asset":"$referencedId"}}]""",
                ),
            )
            val validRepository = TaskRepository(validDao.proxy, imageStore = store)
            assertEquals(0, validRepository.sweepOrphanedSubtaskImages())
            assertTrue(store.exists(referencedId))

            val malformedDao = FakeTaskDao(taskEntity(subtasks = "not-json"))
            val malformedRepository = TaskRepository(malformedDao.proxy, imageStore = store)
            assertEquals(0, malformedRepository.sweepOrphanedSubtaskImages())
            assertTrue(store.exists(referencedId))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun logsDecodeTimeForThreeHundredTasksWithEightSubtasksWithoutTimingAssertion() = runTest {
        val subtaskFixture = (1..8).joinToString(prefix = "[", postfix = "]") { index ->
            """{"id":"item-$index","title":"Item $index"}"""
        }
        val entities = (1..300).map { index ->
            taskEntity(subtasks = subtaskFixture).copy(id = "task-$index")
        }
        val repository = TaskRepository(FakeTaskDao(*entities.toTypedArray()).proxy)

        val coldStart = System.nanoTime()
        val coldTasks = repository.observeAllTasks().first()
        val coldMillis = (System.nanoTime() - coldStart) / 1_000_000.0
        val warmStart = System.nanoTime()
        val warmTasks = repository.observeAllTasks().first()
        val warmMillis = (System.nanoTime() - warmStart) / 1_000_000.0

        assertEquals(300, coldTasks.size)
        assertEquals(8, coldTasks.first().subtasks.size)
        assertEquals(300, warmTasks.size)
        println("Subtask decode fixture: 300 tasks × 8 subtasks; cold=${coldMillis}ms warm=${warmMillis}ms")
    }

    private fun taskEntity(subtasks: String = "[]") = TaskEntity(
        id = "task-1",
        title = "Preserved title",
        description = "description",
        startTime = "2026-10-20T08:00:00Z",
        endTime = "2026-10-20T09:00:00Z",
        durationMinutes = 60,
        status = "scheduled",
        priority = "high",
        tags = """["tag"]""",
        reminders = "[]",
        color = "#123456",
        focusMode = true,
        focusAllowedPackages = """["pkg.allowed"]""",
        createdAt = "2026-10-19T08:00:00Z",
        updatedAt = "2026-10-19T09:00:00Z",
        subtasks = subtasks,
    )

    private fun taskDomain() = Task(
        id = "task-1",
        title = "Task",
        startTime = "2026-10-20T08:00:00Z",
        endTime = "2026-10-20T09:00:00Z",
        durationMinutes = 60,
        status = "scheduled",
        priority = "medium",
        color = "#6366f1",
        focusMode = false,
        createdAt = "2026-10-19T08:00:00Z",
        updatedAt = "2026-10-19T09:00:00Z",
    )

    private fun png() = byteArrayOf(
        0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
    )

    private class StoredWebpCodec : ImageCodec {
        override fun bounds(bytes: ByteArray) = ImageBounds(16, 16)

        override fun reencode(bytes: ByteArray, maxLongEdge: Int, quality: Int) =
            byteArrayOf(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50)
    }

    private class FakeTaskDao(vararg initial: TaskEntity) : InvocationHandler {
        val rows = initial.associateBy(TaskEntity::id).toMutableMap()
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
                    val taskId = values[0] as String
                    val raw = values[1] as String
                    val previous = rows[taskId]
                    if (previous == null) {
                        0
                    } else {
                        rows[taskId] = previous.copy(subtasks = raw)
                        1
                    }
                }
                "getAllSubtaskJson" -> rows.values.map {
                    com.tbtechs.focusflow.data.local.dao.TaskSubtasksRow(it.id, it.subtasks)
                }
                "observeAllTasks" -> flowOf(rows.values.toList())
                "toString" -> "FakeTaskDao"
                else -> error("Unexpected TaskDao call: ${method.name}")
            }
        }
    }
}
