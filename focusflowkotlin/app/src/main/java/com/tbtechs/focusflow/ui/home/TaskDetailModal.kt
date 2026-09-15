package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tbtechs.focusflow.data.model.Task
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailModal(
    task: Task,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onExtend: () -> Unit,
    onStartFocus: () -> Unit,
    onEdit: () -> Unit,
) {
    val canAct = task.status !in setOf("completed", "skipped")
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(task.title, style = MaterialTheme.typography.headlineSmall)
            task.description?.let {
                Text("Notes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            Text("Schedule", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${task.startTime.asLocalTime()} – ${task.endTime.asLocalTime()}")
            Text(task.startTime.asLocalDate())
            Text("Priority", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(task.priority.replaceFirstChar(Char::titlecase), color = priorityColor(task.priority))
            if (task.tags.isNotEmpty()) {
                Text("Tags", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(task.tags.joinToString(" ") { "#$it" })
            }
            Text("Status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(task.status.replaceFirstChar(Char::titlecase))
            Row(modifier = Modifier.fillMaxWidth()) {
                DetailAction("Edit", Icons.Outlined.Edit, onEdit)
                if (canAct) {
                    DetailAction("Complete", Icons.Outlined.Check, onComplete)
                    DetailAction("Skip", Icons.Outlined.SkipNext, onSkip)
                    DetailAction("Extend", Icons.Outlined.Schedule, onExtend)
                    if (task.focusMode) DetailAction("Focus", Icons.Outlined.Shield, onStartFocus)
                }
            }
            Button(onClick = onDismiss) { Text("Close") }
        }
    }
}

@Composable
private fun DetailAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Icon(icon, contentDescription = label)
        Text(label)
    }
}

private fun String.asLocalDate(): String = runCatching {
    Instant.parse(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
}.getOrDefault(this)
