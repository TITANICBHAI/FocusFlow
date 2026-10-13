package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.theme.scaledSp
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
            Text("Nothing on today’s timeline", fontSize = 15.scaledSp, color = RefText)
            Text("Add a task to see it here.", fontSize = 11.scaledSp, color = RefSecondary)
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(tasks.sortedBy(Task::startTime), key = Task::id) { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(RefCard)
                        .border(1.dp, RefBorder, RoundedCornerShape(10.dp))
                        .clickable { onTaskClick(task) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(
                        modifier = Modifier
                            .size(width = 4.dp, height = 32.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(priorityColor(task.priority)),
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(task.title, fontSize = 13.scaledSp, color = RefText)
                        Text(
                            "${task.startTime.asLocalTime()} · ${task.durationMinutes.asDurationLabel()} · ${task.endTime.asLocalTime()}",
                            fontSize = 11.scaledSp,
                            color = RefSecondary,
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = "Open task",
                        tint = RefMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
