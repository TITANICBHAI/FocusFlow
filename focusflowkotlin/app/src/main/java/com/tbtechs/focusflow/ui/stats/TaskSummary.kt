package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot

@Composable
fun TaskSummary(snapshot: AnalyticsSnapshot) = Card {
    Column {
        Text("TASKS", style = MaterialTheme.typography.labelLarge)
        Row {
            Metric("Done", snapshot.tasks.completed, MaterialTheme.colorScheme.secondary)
            Metric("Skipped", snapshot.tasks.skipped, MaterialTheme.colorScheme.tertiary)
            Metric("Missed", snapshot.tasks.missed, MaterialTheme.colorScheme.error)
        }
        if (snapshot.tasks.total == 0) Text("No tasks were recorded in this window.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Metric(label: String, value: Int, color: androidx.compose.ui.graphics.Color) = Column {
    Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = color)
    Text(label, style = MaterialTheme.typography.labelMedium)
}
