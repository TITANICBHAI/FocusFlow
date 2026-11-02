package com.tbtechs.focusflow.widget

import com.tbtechs.focusflow.data.model.Task
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetSnapshotBuilderTest {
    private val now = Instant.parse("2026-11-03T10:00:00Z").toEpochMilli()
    private val minute = 60_000L

    private fun task(id: String, startMs: Long, endMs: Long, status: String = "scheduled") = Task(
        id = id,
        title = "Task $id",
        startTime = Instant.ofEpochMilli(startMs).toString(),
        endTime = Instant.ofEpochMilli(endMs).toString(),
        durationMinutes = ((endMs - startMs) / minute).toInt(),
        status = status,
        priority = "medium",
        color = "#112233",
        focusMode = false,
        createdAt = Instant.ofEpochMilli(startMs - minute).toString(),
        updatedAt = Instant.ofEpochMilli(startMs - minute).toString(),
    )

    private fun build(tasks: List<Task>, session: WidgetSession? = null) =
        WidgetSnapshotBuilder.build(tasks, now, session, ZoneOffset.UTC)

    @Test
    fun runningTaskIsActiveAndNamesTheFollowingOne() {
        val snap = build(
            listOf(
                task("a", now - 10 * minute, now + 20 * minute),
                task("b", now + 30 * minute, now + 60 * minute),
            ),
        )
        assertEquals(WidgetMode.ACTIVE, snap.mode)
        assertEquals("Task a", snap.title)
        assertEquals("Task b", snap.followingTitle)
        assertEquals("#112233", snap.colorHex)
    }

    @Test
    fun finishedTasksAreNeverShownAsActiveOrNext() {
        val snap = build(
            listOf(
                task("done", now - 10 * minute, now + 20 * minute, status = "completed"),
                task("skipped", now + 5 * minute, now + 25 * minute, status = "skipped"),
            ),
        )
        assertEquals(WidgetMode.EMPTY, snap.mode)
        assertEquals(1, snap.doneToday)
        assertEquals(2, snap.totalToday)
    }

    @Test
    fun nothingRunningShowsTheNextTaskToday() {
        val snap = build(listOf(task("n", now + 45 * minute, now + 90 * minute)))
        assertEquals(WidgetMode.NEXT, snap.mode)
        assertEquals("Task n", snap.title)
        assertEquals(true, snap.startsToday)
        assertNull(snap.followingTitle)
    }

    @Test
    fun taskTomorrowIsNextButNotToday() {
        val start = now + 20 * 60 * minute
        val snap = build(listOf(task("t", start, start + 30 * minute)))
        assertEquals(WidgetMode.NEXT, snap.mode)
        assertEquals(false, snap.startsToday)
    }

    @Test
    fun runningFocusSessionIsShownEvenWithoutAMatchingTask() {
        val snap = build(emptyList(), WidgetSession("Deep work", now - minute, now + 25 * minute, "#ff0000"))
        assertEquals(WidgetMode.ACTIVE, snap.mode)
        assertEquals("Deep work", snap.title)
    }

    @Test
    fun emptyDayIsEmpty() {
        assertEquals(WidgetMode.EMPTY, build(emptyList()).mode)
    }
}
