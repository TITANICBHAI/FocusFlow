package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import com.tbtechs.focusflow.data.model.Task
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TaskCard(
    task: Task,
    isActive: Boolean,
    onOpen: () -> Unit,
    onComplete: (String) -> Unit,
    onSkip: (String) -> Unit,
    onExtend: (Task) -> Unit,
    onStartFocus: (String) -> Unit,
) {
    val complete = task.status == "completed"
    val closed = complete || task.status == "skipped"
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row {
            Column(modifier = Modifier.weight(1f)) {
                Row {
                    if (task.focusMode) Icon(Icons.Outlined.Shield, contentDescription = "Focus mode", tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (complete) TextDecoration.LineThrough else null,
                        color = if (closed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    AssistChip(onClick = onOpen, label = { Text(task.priority.replaceFirstChar(Char::titlecase)) })
                }
                Text(
                    text = "${task.startTime.asLocalTime()} – ${task.endTime.asLocalTime()} · ${task.durationMinutes.asDurationLabel()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (task.tags.isNotEmpty()) Text(
                    task.tags.joinToString(separator = " ") { "#$it" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    isActive -> Text(
                        task.timeRemainingLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    task.status == "scheduled" -> Text(
                        task.timeUntilStartLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    closed -> Text(
                        if (complete) "Completed" else "Skipped",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isActive) {
                Column {
                    IconButton(onClick = { onComplete(task.id) }) { Icon(Icons.Outlined.Check, "Complete task", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = { onExtend(task) }) { Icon(Icons.Outlined.Alarm, "Extend task", tint = MaterialTheme.colorScheme.tertiary) }
                    if (task.focusMode) IconButton(onClick = { onStartFocus(task.id) }) { Icon(Icons.Outlined.Shield, "Start focus", tint = MaterialTheme.colorScheme.secondary) }
                }
            } else if (task.status == "scheduled") {
                IconButton(onClick = { onSkip(task.id) }) { Icon(Icons.Outlined.SkipNext, "Skip task", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
internal fun priorityColor(priority: String): androidx.compose.ui.graphics.Color = when (priority) {
    "critical" -> MaterialTheme.colorScheme.error
    "high" -> MaterialTheme.colorScheme.tertiary
    "medium" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.secondary
}

internal fun String.asLocalTime(): String = runCatching {
    Instant.parse(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
}.getOrDefault(this)

internal fun Int.asDurationLabel(): String = when {
    this < 60 -> "${this}m"
    this % 60 == 0 -> "${this / 60}h"
    else -> "${this / 60}h ${this % 60}m"
}

private fun Task.timeRemainingLabel(): String = runCatching {
    val minutes = Duration.between(Instant.now(), Instant.parse(endTime)).toMinutes()
    if (minutes < 0) "Overdue by ${-minutes}m" else "${minutes}m remaining"
}.getOrDefault("")

private fun Task.timeUntilStartLabel(): String = runCatching {
    val minutes = Duration.between(Instant.now(), Instant.parse(startTime)).toMinutes()
    when {
        minutes <= 0 -> "Starting now"
        minutes < 60 -> "Starts in ${minutes}m"
        else -> "Starts in ${minutes / 60}h ${minutes % 60}m"
    }
}.getOrDefault("")
