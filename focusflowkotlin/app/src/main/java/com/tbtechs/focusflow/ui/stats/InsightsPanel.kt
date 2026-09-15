package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tbtechs.focusflow.data.model.Task
import java.time.Instant
import java.time.ZoneId

/** Legacy report-side "At a glance" panel; retained because ReportScreen uses it. */
@Composable
fun InsightsPanel(tasks: List<Task>, previousTasks: List<Task> = emptyList()) {
    val completed = tasks.filter { it.status == "completed" }
    val bestHour = completed.mapNotNull { runCatching { Instant.parse(it.startTime).atZone(ZoneId.systemDefault()).hour }.getOrNull() }
        .groupingBy { it }.eachCount().maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenByDescending { -it.key })?.key
    val strongestDay = tasks.groupBy { runCatching { Instant.parse(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull() }
        .filterKeys { it != null }
        .maxWithOrNull(compareBy<Map.Entry<java.time.LocalDate?, List<Task>>> { entry -> entry.value.count { it.status == "completed" }.toDouble() / entry.value.size }.thenBy { it.value.size })?.key
    val trendPoints = ((completionRate(tasks) - completionRate(previousTasks)) * 100).toInt()
    Card {
        Column {
            Text("At a glance", style = MaterialTheme.typography.titleMedium)
            InsightPanelRow("Sharpest hour", bestHour?.let(::hourLabel) ?: "Not enough data")
            InsightPanelRow("Best day", strongestDay?.dayOfWeek?.name?.lowercase()?.replaceFirstChar(Char::titlecase) ?: "Not enough data")
            InsightPanelRow("Completion trend", if (previousTasks.isEmpty()) "No prior week" else "${if (trendPoints > 0) "↑" else if (trendPoints < 0) "↓" else "→"} ${kotlin.math.abs(trendPoints)}% vs last week")
        }
    }
}

@Composable
private fun InsightPanelRow(label: String, value: String) = Row {
    Text(label, modifier = androidx.compose.ui.Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyMedium)
}

private fun completionRate(tasks: List<Task>) = if (tasks.isEmpty()) 0.0 else tasks.count { it.status == "completed" }.toDouble() / tasks.size
