package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tbtechs.focusflow.data.model.Task

/**
 * Home timeline alternative. The reference checkout has no TimelineView.tsx to port;
 * this is the requested Compose destination, based on the scheduling intent in the
 * migration architecture.
 */
@Composable
fun TimelineView(tasks: List<Task>, onTaskClick: (Task) -> Unit, modifier: Modifier = Modifier) {
    if (tasks.isEmpty()) {
        Column(modifier = modifier.fillMaxWidth()) {
            Text("Nothing on today’s timeline", style = MaterialTheme.typography.titleMedium)
            Text("Add a task to see it here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(tasks.sortedBy(Task::startTime), key = Task::id) { task ->
                Card(onClick = { onTaskClick(task) }, modifier = Modifier.fillMaxWidth()) {
                    Text(task.startTime.asLocalTime(), style = MaterialTheme.typography.labelLarge, color = priorityColor(task.priority))
                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                    Text("${task.durationMinutes.asDurationLabel()} · ${task.endTime.asLocalTime()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
