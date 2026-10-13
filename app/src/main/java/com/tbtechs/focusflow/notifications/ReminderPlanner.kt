package com.tbtechs.focusflow.notifications

import com.tbtechs.focusflow.data.model.Task
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ReminderKind(
    val notificationType: String,
    val categoryIdentifier: String,
) {
    LIVE_STATUS_START("live-task-status", "task-active"),
    PRE_START("reminder", "task-reminder"),
    AT_START("task-start", "task-active"),
    MID_SESSION("checkin", "task-active"),
    ALMOST_DONE("almost-done", "task-active"),
}

data class ReminderSlot(
    val id: String,
    val taskId: String,
    val kind: ReminderKind,
    val triggerMs: Long,
    val title: String,
    val text: String,
)

/**
 * Builds task reminders and live-status start triggers from task IDs and
 * task timestamps.
 *
 * The normal plan drops expired and imminent slots so they are never armed.
 * The receiver opts into [includeDueSlots] to recover a slot whose alarm was
 * delivered late; that path still excludes tasks whose end time has passed.
 */
object ReminderPlanner {
    const val MAX_SLOTS = 450
    const val MIN_SCHEDULE_LEAD_MS = 1_000L
    const val DELIVERY_TOLERANCE_MS = 1_000L

    private const val MINUTES_MS = 60_000L
    private const val MID_SESSION_MIN_REMAINING_MS = 10 * MINUTES_MS
    private val excludedStatuses = setOf("completed", "skipped", "overdue")
    private val slotIdSuffixes = listOf(
        "-live-start",
        "-mid1800000",
        "-mid900000",
        "-pre-600000",
        "-pre-300000",
        "-pre-60000",
        "-pre0",
        "-almost",
    )
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    internal fun taskIdFromSlotId(slotId: String): String? =
        slotIdSuffixes.firstNotNullOfOrNull { suffix ->
            slotId.takeIf { it.endsWith(suffix) }
                ?.dropLast(suffix.length)
                ?.takeIf(String::isNotBlank)
        }

    fun plan(
        tasks: List<Task>,
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        remindersEnabled: Boolean = true,
        preStartRemindersEnabled: Boolean = true,
        atStartReminderEnabled: Boolean = true,
        midSessionRemindersEnabled: Boolean = true,
        wrapUpReminderEnabled: Boolean = true,
        includeDueSlots: Boolean = false,
    ): List<ReminderSlot> {
        return tasks.asSequence()
            .filter { task ->
                task.id.isNotBlank() && task.status !in excludedStatuses
            }
            .mapNotNull { task ->
                val startMs = parseEpochMillis(task.startTime) ?: return@mapNotNull null
                val endMs = parseEpochMillis(task.endTime) ?: return@mapNotNull null
                if (endMs <= startMs || endMs <= nowMs) return@mapNotNull null
                task to (startMs to endMs)
            }
            .flatMap { (task, times) ->
                slotsFor(
                    task = task,
                    startMs = times.first,
                    endMs = times.second,
                    zoneId = zoneId,
                    remindersEnabled = remindersEnabled,
                    preStartRemindersEnabled = preStartRemindersEnabled,
                    atStartReminderEnabled = atStartReminderEnabled,
                    midSessionRemindersEnabled = midSessionRemindersEnabled,
                    wrapUpReminderEnabled = wrapUpReminderEnabled,
                ).asSequence()
            }
            .filter { slot ->
                includeDueSlots || slot.triggerMs >= nowMs + MIN_SCHEDULE_LEAD_MS
            }
            .sortedWith(compareBy<ReminderSlot> { it.triggerMs }.thenBy { it.id })
            .take(MAX_SLOTS)
            .toList()
    }

    private fun slotsFor(
        task: Task,
        startMs: Long,
        endMs: Long,
        zoneId: ZoneId,
        remindersEnabled: Boolean,
        preStartRemindersEnabled: Boolean,
        atStartReminderEnabled: Boolean,
        midSessionRemindersEnabled: Boolean,
        wrapUpReminderEnabled: Boolean,
    ): List<ReminderSlot> {
        val endLabel = Instant.ofEpochMilli(endMs)
            .atZone(zoneId)
            .format(timeFormatter)
        val durationLabel = formatDuration(task.durationMinutes)
        val slots = mutableListOf<ReminderSlot>()

        // Keep a start-time event in the alarm chain even when one-shot task
        // reminders are disabled. The receiver uses it to show the ongoing
        // status card without posting a reminder notification.
        slots += ReminderSlot(
            id = "${task.id}-live-start",
            taskId = task.id,
            kind = ReminderKind.LIVE_STATUS_START,
            triggerMs = startMs,
            title = task.title,
            text = "Task in progress · ends at $endLabel",
        )

        if (!remindersEnabled) return slots

        if (preStartRemindersEnabled) {
            listOf(
                -10 * MINUTES_MS to "Starting in 10 min · ends at $endLabel · $durationLabel total",
                -5 * MINUTES_MS to "Starting in 5 min · ends at $endLabel",
                -MINUTES_MS to "Starting in 1 min — get ready! Ends at $endLabel",
            ).forEach { (offsetMs, text) ->
                val triggerMs = safeAdd(startMs, offsetMs) ?: return@forEach
                slots += ReminderSlot(
                    id = "${task.id}-pre$offsetMs",
                    taskId = task.id,
                    kind = ReminderKind.PRE_START,
                    triggerMs = triggerMs,
                    title = "🎯 ${task.title}",
                    text = text,
                )
            }
        }
        if (atStartReminderEnabled) {
            slots += ReminderSlot(
                id = "${task.id}-pre0",
                taskId = task.id,
                kind = ReminderKind.AT_START,
                triggerMs = startMs,
                title = "🎯 ${task.title}",
                text = "$durationLabel session · ends at $endLabel — tap to open",
            )
        }

        if (midSessionRemindersEnabled) {
            listOf(
                15 * MINUTES_MS to "15 minutes in — how's it going?",
                30 * MINUTES_MS to "Half hour in — keep going!",
            ).forEach { (offsetMs, text) ->
                val triggerMs = safeAdd(startMs, offsetMs) ?: return@forEach
                if (triggerMs >= endMs || endMs - triggerMs < MID_SESSION_MIN_REMAINING_MS) {
                    return@forEach
                }
                slots += ReminderSlot(
                    id = "${task.id}-mid$offsetMs",
                    taskId = task.id,
                    kind = ReminderKind.MID_SESSION,
                    triggerMs = triggerMs,
                    title = "🟢 ${task.title}",
                    text = text,
                )
            }
        }

        if (wrapUpReminderEnabled) {
            val almostDoneMs = endMs - MINUTES_MS
            slots += ReminderSlot(
                id = "${task.id}-almost",
                taskId = task.id,
                kind = ReminderKind.ALMOST_DONE,
                triggerMs = almostDoneMs,
                title = "⏳ ${task.title} — 1 minute left",
                text = "Start wrapping up!",
            )
        }
        return slots
    }

    private fun parseEpochMillis(value: String): Long? =
        runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrNull()

    private fun safeAdd(value: Long, offset: Long): Long? =
        runCatching { Math.addExact(value, offset) }.getOrNull()

    private fun formatDuration(minutes: Int): String {
        val hours = minutes / 60
        val remainder = minutes % 60
        return when {
            hours > 0 && remainder > 0 -> "${hours}h ${remainder}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
}