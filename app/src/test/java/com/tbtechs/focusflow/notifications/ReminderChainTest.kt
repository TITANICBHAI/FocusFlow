package com.tbtechs.focusflow.notifications

import com.tbtechs.focusflow.data.model.Reminder
import com.tbtechs.focusflow.data.model.Task
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderChainTest {
    private val startMs = Instant.parse("2026-01-01T10:00:00Z").toEpochMilli()
    private val beforeStartMs = startMs - 60 * 60_000L

    @Test
    fun plannerUsesTaskTimesAndIgnoresPersistedReminderArray() {
        val base = task("focus-1", startMs, startMs + 60 * 60_000L)
        val withLegacyReminder = base.copy(
            reminders = listOf(
                Reminder(
                    id = "legacy",
                    taskId = base.id,
                    offsetMinutes = 123,
                    type = "post-start",
                ),
            ),
        )

        val planned = ReminderPlanner.plan(
            tasks = listOf(base),
            nowMs = beforeStartMs,
            zoneId = ZoneOffset.UTC,
        )
        val plannedWithLegacyData = ReminderPlanner.plan(
            tasks = listOf(withLegacyReminder),
            nowMs = beforeStartMs,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(planned, plannedWithLegacyData)
        assertEquals(
            listOf(
                "focus-1-pre-600000",
                "focus-1-pre-300000",
                "focus-1-pre-60000",
                "focus-1-live-start",
                "focus-1-pre0",
                "focus-1-mid900000",
                "focus-1-mid1800000",
                "focus-1-almost",
            ),
            planned.map(ReminderSlot::id),
        )
        assertFalse(planned.any { it.id.endsWith("-end") })
    }

    @Test
    fun plannerExcludesIneligibleTasksAndExpiredSlots() {
        val futureEndMs = startMs + 60 * 60_000L
        val excludedStatuses = listOf("completed", "skipped", "overdue")
        val terminalTasks = excludedStatuses.mapIndexed { index, status ->
            task("terminal-$index", startMs, futureEndMs, status = status)
        }
        val expiredTask = task(
            id = "expired",
            startMs = startMs - 60 * 60_000L,
            endMs = startMs - 1_000L,
        )

        assertTrue(
            ReminderPlanner.plan(
                terminalTasks + expiredTask,
                nowMs = startMs,
                zoneId = ZoneOffset.UTC,
            ).isEmpty(),
        )

        val midTask = task(
            id = "mid-threshold",
            startMs = startMs,
            endMs = startMs + 20 * 60_000L,
        )
        val midPlan = ReminderPlanner.plan(
            tasks = listOf(midTask),
            nowMs = beforeStartMs,
            zoneId = ZoneOffset.UTC,
        )
        assertFalse(midPlan.any { it.kind == ReminderKind.MID_SESSION })

        val longTask = task("active", startMs, futureEndMs)
        val afterStart = startMs + 16 * 60_000L
        val futureOnly = ReminderPlanner.plan(
            tasks = listOf(longTask),
            nowMs = afterStart,
            zoneId = ZoneOffset.UTC,
        )
        assertTrue(futureOnly.isNotEmpty())
        assertTrue(futureOnly.all {
            it.triggerMs >= afterStart + ReminderPlanner.MIN_SCHEDULE_LEAD_MS
        })
        assertFalse(futureOnly.any { it.id.endsWith("-pre0") })
    }

    @Test
    fun plannerEnforcesEarliestFirst450SlotBudget() {
        val tasks = (0 until 65).map { index ->
            val taskStart = startMs + (index + 1) * 24 * 60 * 60_000L
            task("task-$index", taskStart, taskStart + 60 * 60_000L)
        }

        val slots = ReminderPlanner.plan(
            tasks = tasks,
            nowMs = startMs,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(ReminderPlanner.MAX_SLOTS, slots.size)
        assertEquals(
            slots.sortedWith(compareBy<ReminderSlot> { it.triggerMs }.thenBy { it.id }),
            slots,
        )
        assertEquals(
            listOf("task-56-pre-600000", "task-56-pre-300000"),
            slots.filter { it.taskId == "task-56" }.map(ReminderSlot::id),
        )
    }

    @Test
    fun disabledReminderPreferenceStillSchedulesLiveStatusStart() {
        val task = task("disabled", startMs, startMs + 60 * 60_000L)

        val slots = ReminderPlanner.plan(
            tasks = listOf(task),
            nowMs = beforeStartMs,
            zoneId = ZoneOffset.UTC,
            remindersEnabled = false,
        )

        assertEquals(listOf("disabled-live-start"), slots.map(ReminderSlot::id))
        assertEquals(ReminderKind.LIVE_STATUS_START, slots.single().kind)
    }

    @Test
    fun reminderCategoriesCanBeDisabledIndependentlyWithoutRemovingLiveStatus() {
        val task = task("categories", startMs, startMs + 60 * 60_000L)

        val withoutPreStart = ReminderPlanner.plan(
            tasks = listOf(task),
            nowMs = beforeStartMs,
            zoneId = ZoneOffset.UTC,
            preStartRemindersEnabled = false,
        )
        assertFalse(withoutPreStart.any { it.kind == ReminderKind.PRE_START })
        assertTrue(withoutPreStart.any { it.kind == ReminderKind.AT_START })

        val withoutAtStart = ReminderPlanner.plan(
            tasks = listOf(task),
            nowMs = beforeStartMs,
            zoneId = ZoneOffset.UTC,
            atStartReminderEnabled = false,
        )
        assertFalse(withoutAtStart.any { it.kind == ReminderKind.AT_START })
        assertTrue(withoutAtStart.any { it.kind == ReminderKind.PRE_START })

        val withoutMidSession = ReminderPlanner.plan(
            tasks = listOf(task),
            nowMs = beforeStartMs,
            zoneId = ZoneOffset.UTC,
            midSessionRemindersEnabled = false,
        )
        assertFalse(withoutMidSession.any { it.kind == ReminderKind.MID_SESSION })

        val withoutWrapUp = ReminderPlanner.plan(
            tasks = listOf(task),
            nowMs = beforeStartMs,
            zoneId = ZoneOffset.UTC,
            wrapUpReminderEnabled = false,
        )
        assertFalse(withoutWrapUp.any { it.kind == ReminderKind.ALMOST_DONE })

        val remindersOff = ReminderPlanner.plan(
            tasks = listOf(task),
            nowMs = beforeStartMs,
            zoneId = ZoneOffset.UTC,
            remindersEnabled = false,
            preStartRemindersEnabled = false,
            atStartReminderEnabled = false,
            midSessionRemindersEnabled = false,
            wrapUpReminderEnabled = false,
        )
        assertEquals(listOf(ReminderKind.LIVE_STATUS_START), remindersOff.map(ReminderSlot::kind))
    }

    @Test
    fun chainReschedulesToNextFutureSlotAndCancelsWhenNoSlotRemains() {
        val driver = RecordingAlarmDriver()
        val scheduler = ReminderChainScheduler(driver)
        val first = slot("first", 2_000L)
        val second = slot("second", 4_000L)
        val expired = slot("expired", 900L)
        val imminent = slot("imminent", 1_999L)

        assertEquals(
            first,
            scheduler.rearm(listOf(second, first, expired, imminent), nowMs = 1_000L),
        )
        assertEquals(2_000L, driver.scheduledAtMs)
        assertEquals(1, driver.scheduleCalls)

        assertEquals(second, scheduler.rearm(listOf(first, second), nowMs = 2_000L))
        assertEquals(4_000L, driver.scheduledAtMs)
        assertEquals(2, driver.scheduleCalls)

        assertNull(scheduler.rearm(listOf(first, second), nowMs = 4_000L))
        assertNull(driver.scheduledAtMs)
        assertEquals(1, driver.cancelCalls)
    }

    @Test
    fun chainDeliveryUsesSlotIdentityAndSuppressesDuplicatePosts() {
        val store = InMemoryLedgerStore()
        val ledger = ReminderChainLedger(store)
        val posted = mutableListOf<String>()
        val due = slot("due", 1_000L)
        val withinTolerance = slot("within-tolerance", 2_000L)
        val future = slot("future", 2_001L)
        val nowMs = 1_000L

        assertEquals(
            listOf(due, withinTolerance),
            ReminderDelivery.dueSlots(listOf(future, withinTolerance, due), nowMs),
        )
        val firstDelivery = ledger.deliverDue(
            slots = listOf(future, withinTolerance, due),
            nowMs = nowMs,
            post = { posted += it.id },
        )
        val duplicateDelivery = ledger.deliverDue(
            slots = listOf(due, withinTolerance),
            nowMs = nowMs,
            post = { posted += it.id },
        )

        assertEquals(listOf(due, withinTolerance), firstDelivery)
        assertTrue(duplicateDelivery.isEmpty())
        assertEquals(listOf("due", "within-tolerance"), posted)

        val rescheduled = due.copy(triggerMs = 2_001L)
        assertEquals(
            listOf(rescheduled),
            ledger.deliverDue(
                slots = listOf(rescheduled),
                nowMs = 1_500L,
                post = { posted += it.id },
            ),
        )
    }

    @Test
    fun ledgerPrunesEntriesOlderThan48Hours() {
        val nowMs = 1_000_000_000L
        val expiredAt = nowMs - 48L * 60L * 60L * 1_000L - 1L
        val retainedAt = nowMs - 48L * 60L * 60L * 1_000L
        val store = InMemoryLedgerStore(mapOf("expired" to expiredAt, "retained" to retainedAt))
        val ledger = ReminderChainLedger(store)

        ledger.deliverDue(slots = emptyList(), nowMs = nowMs, post = {})

        assertEquals(mapOf("retained" to retainedAt), store.entries)
        assertFalse("expired" in store.entries)
    }

    @Test
    fun ledgerNeverExceeds450Entries() {
        val nowMs = 1_000_000_000L
        val store = InMemoryLedgerStore(
            entries = buildMap {
                repeat(ReminderPlanner.MAX_SLOTS) { index ->
                    put("recent-$index", nowMs - 2_000L - index)
                }
            },
        )
        val ledger = ReminderChainLedger(store)

        ledger.deliverDue(
            slots = listOf(slot("new", nowMs + 500L)),
            nowMs = nowMs,
            post = {},
        )

        assertEquals(ReminderPlanner.MAX_SLOTS, store.entries.size)
        assertTrue("new" in store.entries)
        assertFalse("recent-${ReminderPlanner.MAX_SLOTS - 1}" in store.entries)
    }

    @Test
    fun reconciliationCancelsNotificationsForDeletedOrIneligibleTasksOnly() {
        val store = InMemoryLedgerStore(
            entries = mapOf(
                "deleted-pre0" to startMs,
                "completed-pre0" to startMs,
                "completed-live-start" to startMs,
                "task-1-pre0" to startMs,
                "task-1-live-start" to startMs,
                "task-1-almost" to startMs,
                "task-10-pre0" to startMs,
            ),
        )
        val ledger = ReminderChainLedger(store)
        val cancelled = mutableListOf<String>()

        ledger.cancelNotificationsForIneligibleTasks(
            tasks = listOf(
                task("completed", startMs, startMs + 60 * 60_000L, status = "completed"),
                task("task-1", startMs, startMs + 60 * 60_000L, status = "active"),
            ),
            cancel = cancelled::add,
        )

        assertEquals(
            listOf(
                "completed-live-start",
                "completed-pre0",
                "deleted-pre0",
                "task-10-pre0",
            ),
            cancelled.sorted(),
        )
    }

    private fun task(
        id: String,
        startMs: Long,
        endMs: Long,
        status: String = "scheduled",
    ) = Task(
        id = id,
        title = "Focus task $id",
        startTime = Instant.ofEpochMilli(startMs).toString(),
        endTime = Instant.ofEpochMilli(endMs).toString(),
        durationMinutes = ((endMs - startMs) / 60_000L).toInt(),
        status = status,
        priority = "medium",
        color = "#6366f1",
        focusMode = false,
        createdAt = Instant.ofEpochMilli(startMs - 60_000L).toString(),
        updatedAt = Instant.ofEpochMilli(startMs - 60_000L).toString(),
    )

    private fun slot(id: String, triggerMs: Long) = ReminderSlot(
        id = id,
        taskId = "task",
        kind = ReminderKind.PRE_START,
        triggerMs = triggerMs,
        title = "Task reminder",
        text = "Reminder text",
    )

    private class RecordingAlarmDriver : ReminderChainAlarmDriver {
        var scheduledAtMs: Long? = null
            private set
        var scheduleCalls = 0
            private set
        var cancelCalls = 0
            private set

        override fun schedule(triggerAtMs: Long) {
            scheduledAtMs = triggerAtMs
            scheduleCalls++
        }

        override fun cancel() {
            scheduledAtMs = null
            cancelCalls++
        }
    }

    private class InMemoryLedgerStore(
        var entries: Map<String, Long> = emptyMap(),
    ) : ReminderLedgerStore {
        override fun read(): Map<String, Long> = entries.toMap()

        override fun write(entries: Map<String, Long>): Boolean {
            this.entries = entries.toMap()
            return true
        }
    }
}