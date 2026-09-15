package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot

@Composable
fun TaskResultList(snapshot: AnalyticsSnapshot) = Card {
    Column {
        Text("YESTERDAY'S TASKS", style = MaterialTheme.typography.labelLarge)
        val rows = snapshot.tasks.resultRows.orEmpty()
        if (rows.isEmpty()) Text("No tasks were recorded yesterday.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEach { row ->
            val label = when (row.status) {
                "completed" -> "Done"
                "skipped" -> "Skipped"
                "overdue" -> "Missed"
                else -> "Not completed"
            }
            Text("${row.title} · $label")
        }
    }
}
