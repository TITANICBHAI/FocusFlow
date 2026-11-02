package com.tbtechs.focusflow.widget

import com.tbtechs.focusflow.data.model.Task
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

internal enum class WidgetMode { ACTIVE, NEXT, EMPTY }

/** A running focus session described by the enforcement layer's saved state. */
internal data class WidgetSession(
    val name: String,
    val startMs: Long,
    val endMs: Long,
    val colorHex: String?,
)

/** Everything the widget needs to draw itself. Contains no Android types, so it is unit tested. */
internal data class WidgetSnapshot(
    val mode: WidgetMode,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val colorHex: String?,
    val doneToday: Int,
    val totalToday: Int,
    /** While ACTIVE: the task that follows. */
    val followingTitle: String?,
    /** For NEXT: whether it starts today. */
    val startsToday: Boolean,
)

internal object WidgetSnapshotBuilder {
    private val terminalStatuses = setOf("completed", "skipped", "overdue")
    private const val LOOKAHEAD_MS = 48L * 60 * 60 * 1000

    private data class Parsed(val task: Task, val startMs: Long, val endMs: Long)

    fun build(
        tasks: List<Task>,
        nowMs: Long,
        session: WidgetSession?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): WidgetSnapshot {
        val parsed = tasks.mapNotNull { task ->
            val start = parseMillis(task.startTime) ?: return@mapNotNull null
            val end = parseMillis(task.endTime) ?: return@mapNotNull null
            if (end <= start) null else Parsed(task, start, end)
        }
        val open = parsed.filter { it.task.status !in terminalStatuses }
        val running = open
            .filter { it.startMs <= nowMs && it.endMs > nowMs }
            .maxWithOrNull(compareBy<Parsed> { it.startMs }.thenBy { it.task.id })
        val upcoming = open
            .filter { it.startMs > nowMs && it.startMs - nowMs <= LOOKAHEAD_MS }
            .minWithOrNull(compareBy<Parsed> { it.startMs }.thenBy { it.task.id })

        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val todays = parsed.filter {
            Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() == today
        }
        val done = todays.count { it.task.status == "completed" }
        val total = todays.size

        if (running != null) {
            return WidgetSnapshot(
                mode = WidgetMode.ACTIVE,
                title = running.task.title,
                startMs = running.startMs,
                endMs = running.endMs,
                colorHex = running.task.color,
                doneToday = done,
                totalToday = total,
                followingTitle = upcoming?.task?.title,
                startsToday = false,
            )
        }
        if (session != null && session.name.isNotBlank() && session.endMs > nowMs) {
            return WidgetSnapshot(
                mode = WidgetMode.ACTIVE,
                title = session.name,
                startMs = session.startMs,
                endMs = session.endMs,
                colorHex = session.colorHex,
                doneToday = done,
                totalToday = total,
                followingTitle = upcoming?.task?.title,
                startsToday = false,
            )
        }
        if (upcoming != null) {
            return WidgetSnapshot(
                mode = WidgetMode.NEXT,
                title = upcoming.task.title,
                startMs = upcoming.startMs,
                endMs = upcoming.endMs,
                colorHex = upcoming.task.color,
                doneToday = done,
                totalToday = total,
                followingTitle = null,
                startsToday = Instant.ofEpochMilli(upcoming.startMs).atZone(zone).toLocalDate() == today,
            )
        }
        return WidgetSnapshot(
            mode = WidgetMode.EMPTY,
            title = "",
            startMs = 0L,
            endMs = 0L,
            colorHex = null,
            doneToday = done,
            totalToday = total,
            followingTitle = null,
            startsToday = false,
        )
    }

    private fun parseMillis(value: String): Long? =
        runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrNull()
}
