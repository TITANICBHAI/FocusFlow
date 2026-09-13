package com.tbtechs.focusflow.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

/**
 * Pure Kotlin port of schedulerEngine.ts.
 *
 * This file intentionally has no Android, Room, or notification dependency. The
 * caller owns persistence; this class only returns the task changes that should
 * be persisted.
 */
class SchedulerEngine(
    private val clock: Clock = Clock.systemUTC(),
) {

    private val isoFormatter: DateTimeFormatter =
        DateTimeFormatterBuilder().appendInstant(3).toFormatter()

    fun detectConflicts(
        newTask: Task,
        existingTasks: List<Task>,
    ): ConflictResult {
        val conflicts = mutableListOf<TaskConflict>()
        val newStart = parseInstant(newTask.startTime)
        val newEnd = parseInstant(newTask.endTime)

        for (task in existingTasks) {
            if (task.id == newTask.id) continue
            if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.SKIPPED) continue

            val taskStart = parseInstant(task.startTime)
            val taskEnd = parseInstant(task.endTime)
            val overlapStart = maxOf(newStart, taskStart)
            val overlapEnd = minOf(newEnd, taskEnd)
            val overlapMinutes = Duration.between(overlapStart, overlapEnd).toMinutes()

            if (overlapMinutes > 0) {
                conflicts += TaskConflict(task = task, overlapMinutes = overlapMinutes.toInt())
            }
        }

        return ConflictResult(
            hasConflict = conflicts.isNotEmpty(),
            conflicts = conflicts,
        )
    }

    fun findNextAvailableSlot(
        durationMinutes: Int,
        afterTime: String,
        tasks: List<Task>,
        bufferMinutes: Int = 5,
    ): String {
        var candidate = parseInstant(afterTime)
        val activeTasks = tasks
            .filter { it.status != TaskStatus.COMPLETED && it.status != TaskStatus.SKIPPED }
            .sortedBy { parseInstant(it.startTime).epochSecond }

        repeat(50) {
            var conflict = false

            for (task in activeTasks) {
                val taskStart = parseInstant(task.startTime)
                val taskEnd = parseInstant(task.endTime)
                val slotEnd = candidate.plusSeconds(durationMinutes * 60L)
                val overlaps = candidate < taskEnd && slotEnd > taskStart

                if (overlaps) {
                    candidate = taskEnd.plusSeconds(bufferMinutes * 60L)
                    conflict = true
                    break
                }
            }

            if (!conflict) return formatInstant(candidate)
        }

        return formatInstant(candidate)
    }

    fun rebalanceAfterOverrun(
        overrunTask: Task,
        overrunMinutes: Int,
        allTasks: List<Task>,
        options: RebalanceOptions = RebalanceOptions(),
    ): OverrunResult {
        val updatedSchedule = mutableListOf<Task>()
        val skipped = mutableListOf<Task>()
        val shifted = mutableListOf<Task>()
        val needsUserConfirm = mutableListOf<Task>()

        val overrunStart = parseInstant(overrunTask.startTime)
        val subsequent = allTasks
            .filter { task ->
                task.id != overrunTask.id &&
                    task.status != TaskStatus.COMPLETED &&
                    task.status != TaskStatus.SKIPPED &&
                    parseInstant(task.startTime) > overrunStart
            }
            .sortedBy { parseInstant(it.startTime).epochSecond }

        var cumulativeShift = overrunMinutes

        for (task in subsequent) {
            val priority = task.priority.rank

            if (cumulativeShift <= 0) {
                updatedSchedule += task
                continue
            }

            if (priority == TaskPriority.CRITICAL.rank) {
                needsUserConfirm += task
                updatedSchedule += task
                cumulativeShift = 0
                continue
            }

            if (cumulativeShift > options.maxAutoShiftMinutes &&
                priority <= TaskPriority.MEDIUM.rank
            ) {
                val skippedTask = task.copy(
                    status = TaskStatus.SKIPPED,
                    updatedAt = nowIso(),
                )
                skipped += skippedTask
                updatedSchedule += skippedTask
                cumulativeShift -= task.durationMinutes
                continue
            }

            val shiftSeconds = cumulativeShift * 60L
            val shiftedTask = task.copy(
                startTime = formatInstant(parseInstant(task.startTime).plusSeconds(shiftSeconds)),
                endTime = formatInstant(parseInstant(task.endTime).plusSeconds(shiftSeconds)),
                updatedAt = nowIso(),
            )
            shifted += shiftedTask
            updatedSchedule += shiftedTask
        }

        return OverrunResult(
            updatedSchedule = updatedSchedule,
            skipped = skipped,
            shifted = shifted,
            needsUserConfirm = needsUserConfirm,
        )
    }

    fun insertTaskSafe(
        newTask: Task,
        existingTasks: List<Task>,
    ): TaskInsertionResult {
        if (!detectConflicts(newTask, existingTasks).hasConflict) {
            return TaskInsertionResult(task = newTask, shifted = emptyList())
        }

        val shifted = mutableListOf<Task>()
        var occupiedUntil = parseInstant(newTask.endTime)
        val newStart = parseInstant(newTask.startTime)
        val activeTasks = existingTasks
            .filter { it.status != TaskStatus.COMPLETED && it.status != TaskStatus.SKIPPED }
            .sortedBy { parseInstant(it.startTime).toEpochMilli() }

        for (task in activeTasks) {
            if (task.id == newTask.id) continue

            val taskStart = parseInstant(task.startTime)
            val taskEnd = parseInstant(task.endTime)
            if (taskEnd <= newStart) continue

            if (taskStart >= occupiedUntil) {
                if (taskEnd > occupiedUntil) occupiedUntil = taskEnd
                continue
            }

            if (task.priority.rank < newTask.priority.rank) {
                val shiftedStart = occupiedUntil.plusSeconds(5 * 60L)
                val durationMinutes = Duration.between(taskStart, taskEnd).toMinutes()
                val shiftedTask = task.copy(
                    startTime = formatInstant(shiftedStart),
                    endTime = formatInstant(
                        shiftedStart.plusSeconds(durationMinutes * 60L),
                    ),
                    updatedAt = nowIso(),
                )
                shifted += shiftedTask
                occupiedUntil = parseInstant(shiftedTask.endTime)
                continue
            }

            if (taskEnd > occupiedUntil) occupiedUntil = taskEnd
        }

        return TaskInsertionResult(task = newTask, shifted = shifted)
    }

    fun compressSchedule(
        completedTask: Task,
        completedAt: String,
        allTasks: List<Task>,
    ): List<Task> {
        val actualEnd = parseInstant(completedAt)
        val plannedEnd = parseInstant(completedTask.endTime)
        if (actualEnd >= plannedEnd) return allTasks

        val savedMinutes = Duration.between(actualEnd, plannedEnd).toMinutes()
        return allTasks.map { task ->
            if (
                task.id == completedTask.id ||
                task.status == TaskStatus.COMPLETED ||
                task.status == TaskStatus.SKIPPED ||
                parseInstant(task.startTime) < plannedEnd
            ) {
                task
            } else {
                task.copy(
                    startTime = formatInstant(
                        parseInstant(task.startTime).minusSeconds(savedMinutes * 60L),
                    ),
                    endTime = formatInstant(
                        parseInstant(task.endTime).minusSeconds(savedMinutes * 60L),
                    ),
                    updatedAt = nowIso(),
                )
            }
        }
    }

    fun compressDeletedTaskGap(
        deletedTask: Task,
        allTasks: List<Task>,
    ): List<Task> {
        val deletedStart = parseInstant(deletedTask.startTime)
        val deletedEnd = parseInstant(deletedTask.endTime)
        val savedMinutes = Duration.between(deletedStart, deletedEnd).toMinutes()
        if (savedMinutes <= 0) return allTasks

        return allTasks.map { task ->
            if (
                task.id == deletedTask.id ||
                task.status != TaskStatus.SCHEDULED ||
                parseInstant(task.startTime) <= deletedEnd
            ) {
                task
            } else {
                task.copy(
                    startTime = formatInstant(
                        parseInstant(task.startTime).minusSeconds(savedMinutes * 60L),
                    ),
                    endTime = formatInstant(
                        parseInstant(task.endTime).minusSeconds(savedMinutes * 60L),
                    ),
                    updatedAt = nowIso(),
                )
            }
        }
    }

    fun getUnfinishedOverdueTasks(tasks: List<Task>): List<Task> {
        val now = clock.instant()
        return tasks.filter {
            it.status == TaskStatus.SCHEDULED && parseInstant(it.endTime) < now
        }
    }

    fun analyzeScheduleHealth(tasks: List<Task>): ScheduleHealth {
        val sorted = tasks
            .filter { it.status != TaskStatus.SKIPPED }
            .sortedBy { parseInstant(it.startTime).epochSecond }

        val overlaps = mutableListOf<TaskOverlap>()
        val gaps = mutableListOf<ScheduleGap>()

        for (index in 0 until (sorted.size - 1).coerceAtLeast(0)) {
            val a = sorted[index]
            val b = sorted[index + 1]
            val aEnd = parseInstant(a.endTime)
            val bStart = parseInstant(b.startTime)

            if (bStart < aEnd) {
                overlaps += TaskOverlap(a = a, b = b)
            } else {
                val gapMinutes = Duration.between(aEnd, bStart).toMinutes()
                if (gapMinutes > 15) {
                    gaps += ScheduleGap(afterTask = a, gapMinutes = gapMinutes.toInt())
                }
            }
        }

        val totalScheduledMinutes = tasks.sumOf { it.durationMinutes }
        val hourLoad = mutableMapOf<Int, Int>()

        for (task in tasks) {
            val taskStart = localDateTime(task.startTime)
            val taskEnd = localDateTime(task.endTime)
            val dayBase = taskStart.toLocalDate().atStartOfDay()
            val startHour = taskStart.hour
            val endHour = taskEnd.hour

            // This intentionally mirrors the source loop. In particular, an
            // overnight task whose end hour is numerically before its start
            // hour contributes no hour buckets, matching the existing JS.
            for (hour in startHour..endHour) {
                val hourStart = dayBase.plusHours(hour.toLong())
                val hourEnd = hourStart
                    .plusMinutes(59)
                    .plusSeconds(59)
                val slotStart = maxOf(taskStart, hourStart)
                val slotEnd = minOf(taskEnd, hourEnd)
                val minutes = Duration.between(slotStart, slotEnd).toMinutes()
                    .coerceAtLeast(0)
                hourLoad[hour] = (hourLoad[hour] ?: 0) + minutes.toInt()
            }
        }

        val overloadedHours = hourLoad
            .filterValues { it > 60 }
            .keys
            .sorted()
            .map { "$it:00" }

        return ScheduleHealth(
            overlaps = overlaps,
            gaps = gaps,
            totalScheduledMinutes = totalScheduledMinutes,
            overloadedHours = overloadedHours,
        )
    }

    private fun nowIso(): String = formatInstant(clock.instant())

    private fun parseInstant(value: String): Instant {
        return runCatching { Instant.parse(value) }.getOrElse {
            runCatching { OffsetDateTime.parse(value).toInstant() }.getOrElse {
                LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant()
            }
        }
    }

    private fun localDateTime(value: String) =
        parseInstant(value).atZone(ZoneId.systemDefault()).toLocalDateTime()

    private fun formatInstant(value: Instant): String = isoFormatter.format(value)
}

data class Task(
    val id: String,
    val title: String,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Int,
    val status: TaskStatus,
    val priority: TaskPriority,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val color: String = "#6366f1",
    val focusMode: Boolean = false,
    val focusAllowedPackages: List<String>? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

data class Reminder(
    val id: String,
    val taskId: String,
    val offsetMinutes: Int,
    val type: ReminderType,
    val notifId: String? = null,
)

enum class ReminderType {
    PRE_START,
    AT_START,
    POST_START,
}

enum class TaskStatus {
    SCHEDULED,
    ACTIVE,
    COMPLETED,
    SKIPPED,
    OVERDUE,
}

enum class TaskPriority(val rank: Int) {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4),
}

data class ConflictResult(
    val hasConflict: Boolean,
    val conflicts: List<TaskConflict>,
)

data class TaskConflict(
    val task: Task,
    val overlapMinutes: Int,
)

data class RebalanceOptions(
    val maxAutoShiftMinutes: Int = 60,
)

data class OverrunResult(
    val updatedSchedule: List<Task>,
    val skipped: List<Task>,
    val shifted: List<Task>,
    val needsUserConfirm: List<Task>,
)

typealias RebalanceResult = OverrunResult

data class TaskInsertionResult(
    val task: Task,
    val shifted: List<Task>,
)

data class TaskOverlap(
    val a: Task,
    val b: Task,
)

data class ScheduleGap(
    val afterTask: Task,
    val gapMinutes: Int,
)

data class ScheduleHealth(
    val overlaps: List<TaskOverlap>,
    val gaps: List<ScheduleGap>,
    val totalScheduledMinutes: Int,
    val overloadedHours: List<String>,
)