package com.tbtechs.focusflow.data.repository

import com.tbtechs.focusflow.data.model.Task
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskEndAlarmContractTest {

    @Test
    fun pendingIntentAndNotificationIdentityAreUniquePerTask() {
        val first = TaskEndAlarmIdentity.dataUriString("task/alpha")
        val second = TaskEndAlarmIdentity.dataUriString("task?alpha")

        assertNotEquals(first, second)
        assertTrue(first.endsWith("task%2Falpha"))
        assertEquals("task-end:task/alpha", TaskEndAlarmIdentity.notificationTag("task/alpha"))
    }

    @Test
    fun reconcilePlanKeepsOnlyEarliestHundredFutureUnresolvedTasks() {
        val now = 1_800_000_000_000L
        val future = (1..105).map { offset ->
            task("task-$offset", now + offset * 1_000L)
        } + task("completed", now + 500L, status = "completed") +
            task("past", now - 1L)

        val desired = TaskAlarmReconcilePlan.desired(future, now)

        assertEquals(100, desired.size)
        assertEquals("task-1", desired.first().taskId)
        assertEquals("task-100", desired.last().taskId)
        assertTrue(desired.all { it.endTimeMillis > now })
    }

    @Test
    fun fireValidationRejectsTerminalAndEarlyTasksAtExactBoundary() {
        val endMs = 1_800_000_000_000L

        assertEquals(
            TaskEndAlarmValidation.Decision.DELIVER,
            TaskEndAlarmValidation.evaluate("scheduled", endMs, endMs, endMs - 5_000L),
        )
        assertEquals(
            TaskEndAlarmValidation.Decision.SUPPRESS_EARLY,
            TaskEndAlarmValidation.evaluate("active", endMs, endMs, endMs - 5_001L),
        )
        assertEquals(
            TaskEndAlarmValidation.Decision.SUPPRESS_MISSING_OR_TERMINAL,
            TaskEndAlarmValidation.evaluate("completed", endMs, endMs, endMs),
        )
        assertEquals(
            TaskEndAlarmValidation.Decision.SUPPRESS_MISSING_OR_TERMINAL,
            TaskEndAlarmValidation.evaluate(null, endMs, endMs, endMs),
        )
        assertEquals(
            TaskEndAlarmValidation.Decision.SUPPRESS_STALE_TRIGGER,
            TaskEndAlarmValidation.evaluate("active", endMs, endMs + 60_000L, endMs),
        )
    }

    private fun task(
        id: String,
        endMs: Long,
        status: String = "scheduled",
    ) = Task(
        id = id,
        title = id,
        startTime = Instant.ofEpochMilli(endMs - 60_000L).toString(),
        endTime = Instant.ofEpochMilli(endMs).toString(),
        durationMinutes = 1,
        status = status,
        priority = "medium",
        color = "#000000",
        focusMode = false,
        createdAt = Instant.EPOCH.toString(),
        updatedAt = Instant.EPOCH.toString(),
    )
}