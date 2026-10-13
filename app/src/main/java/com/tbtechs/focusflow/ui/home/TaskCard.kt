package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.theme.scaledSp
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.common.taskTimeDisplay
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Redesigned TaskCard for Screenshots 6c & 6f.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    task: Task,
    isActive: Boolean,
    onOpen: () -> Unit,
    onComplete: (String) -> Unit,
    onSkip: (String) -> Unit,
    onExtend: (Task) -> Unit,
) {
    val complete = task.status == "completed"
    val closed = complete || task.status == "skipped"
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val activeBlue = if (isLightTheme) Color(0xFF1D4ED8) else RefBlue
    val completeGreen = if (isLightTheme) Color(0xFF047857) else RefGreen
    val extendAmber = if (isLightTheme) Color(0xFFB45309) else RefAmber
    val accent = if (isActive && !closed) activeBlue else taskAccent(task.color)
    var nowMs by remember(task.id) { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(task.id, task.startTime, task.endTime, closed) {
        if (closed) return@LaunchedEffect
        while (true) {
            val currentMs = System.currentTimeMillis()
            nowMs = currentMs
            val refreshIntervalMs = runCatching {
                val startMs = Instant.parse(task.startTime).toEpochMilli()
                val endMs = Instant.parse(task.endTime).toEpochMilli()
                val remainingMs = endMs - currentMs
                if (startMs <= currentMs && remainingMs in -59_999L..59_999L) 1_000L else 30_000L
            }.getOrDefault(30_000L)
            delay(refreshIntervalMs)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .clickable(onClick = onOpen)
            .semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // Left color strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 0.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = task.title,
                        fontSize = 15.scaledSp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (closed) DarkTextMuted else DarkTextPrimary,
                        textDecoration = if (closed) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(priorityBadgeBg(task.priority))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = task.priority.replaceFirstChar(Char::titlecase),
                            fontSize = 11.scaledSp,
                            fontWeight = FontWeight.SemiBold,
                            color = priorityColor(task.priority),
                        )
                    }
                }

                Text(
                    text = "${task.startTime.asLocalTime()} – ${task.endTime.asLocalTime()} · ${task.durationMinutes.asDurationLabel()}",
                    fontSize = 13.scaledSp,
                    color = DarkTextSecondary,
                )

                if (task.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        task.tags.forEach { tag ->
                            TaskTagChip(tag = tag, fontSizeSp = 11)
                        }
                    }
                }

                if (closed) {
                    val skipped = task.status == "skipped"
                    val statusColor = when {
                        !skipped -> completeGreen
                        isLightTheme -> Color(0xFF92400E)
                        else -> Color(0xFFFBBF24)
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusColor.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = if (skipped) Icons.Outlined.Close else Icons.Outlined.Check,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = if (skipped) "Skipped" else "Completed",
                            fontSize = 12.scaledSp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                        )
                    }
                } else {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(task.progressAt(nowMs))
                                    .fillMaxHeight()
                                    .background(activeBlue),
                            )
                        }
                    }
                    Text(
                        text = if (isActive || task.hasStartedAt(nowMs)) {
                            task.timeRemainingLabel(nowMs)
                        } else {
                            task.timeUntilStartLabel(nowMs)
                        },
                        fontSize = 11.scaledSp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = DarkTextSecondary,
                    )
                }
            }

            if (!closed) {
                Column(
                    modifier = Modifier.padding(end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isActive) {
                        TaskCardAction(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Complete task",
                            tint = completeGreen,
                            onClick = { onComplete(task.id) },
                        )
                        TaskCardAction(
                            imageVector = Icons.Outlined.Alarm,
                            contentDescription = "Extend task",
                            tint = extendAmber,
                            onClick = { onExtend(task) },
                        )
                    } else {
                        TaskCardAction(
                            imageVector = Icons.Outlined.SkipNext,
                            contentDescription = "Skip task",
                            tint = DarkTextSecondary,
                            onClick = { onSkip(task.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCardAction(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(shape)
                .border(1.dp, tint.copy(alpha = 0.42f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun taskAccent(raw: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(raw)) }
        .getOrDefault(BrandPrimary)

@Composable
internal fun priorityColor(priority: String): Color = when (priority.lowercase()) {
    "critical" -> Color(0xFFF87171)
    "high" -> Color(0xFFFBBF24)
    "medium" -> RefBlue
    else -> Color(0xFFCBD5E1)
}

@Composable
internal fun priorityBadgeBg(priority: String): Color = when (priority.lowercase()) {
    "critical" -> Color(0xFFEF4444).copy(alpha = 0.15f)
    "high" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
    "medium" -> RefBlue.copy(alpha = 0.15f)
    else -> Color(0xFFCBD5E1).copy(alpha = 0.15f)
}

internal fun String.asLocalTime(): String = runCatching {
    Instant.parse(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
}.getOrDefault(this)

internal fun Int.asDurationLabel(): String = when {
    this < 60 -> "${this}m"
    this % 60 == 0 -> "${this / 60}h"
    else -> "${this / 60}h ${this % 60}m"
}

private fun Task.timeRemainingLabel(nowMs: Long): String = runCatching {
    val endMs = Instant.parse(endTime).toEpochMilli()
    val display = taskTimeDisplay(endMs, nowMs)
    when {
        display.justEnded -> "Just ended"
        display.overdueMinutes != null -> "Overdue by ${display.overdueMinutes}m"
        display.secondsRemaining != null -> "${display.secondsRemaining}s remaining"
        else -> "${(endMs - nowMs) / 60_000L}m remaining"
    }
}.getOrDefault("")

private fun Task.progressAt(nowMs: Long): Float = runCatching {
    val start = Instant.parse(startTime).toEpochMilli()
    val end = Instant.parse(endTime).toEpochMilli()
    if (end <= start) 0f else ((nowMs - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}.getOrDefault(0f)

private fun Task.timeUntilStartLabel(nowMs: Long): String = runCatching {
    val minutes = Duration.ofMillis(Instant.parse(startTime).toEpochMilli() - nowMs).toMinutes()
    when {
        minutes <= 0 -> "Starting now"
        minutes < 60 -> "Starts in ${minutes}m"
        else -> "Starts in ${minutes / 60}h ${minutes % 60}m"
    }
}.getOrDefault("")

private fun Task.hasStartedAt(nowMs: Long): Boolean = runCatching {
    Instant.parse(startTime).toEpochMilli() <= nowMs
}.getOrDefault(false)
