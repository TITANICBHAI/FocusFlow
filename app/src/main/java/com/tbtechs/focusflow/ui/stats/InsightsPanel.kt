package com.tbtechs.focusflow.ui.stats

import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
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
            Text(stringResource(R.string.stats_at_a_glance), style = MaterialTheme.typography.titleMedium)
            val notEnoughData = stringResource(R.string.stats_insight_not_enough_data)
            InsightPanelRow(stringResource(R.string.stats_insight_sharpest_hour), statsHourLabel(bestHour) ?: notEnoughData)
            InsightPanelRow(
                stringResource(R.string.stats_insight_best_day),
                strongestDay?.dayOfWeek?.getDisplayName(java.time.format.TextStyle.FULL, statsLocale()) ?: notEnoughData,
            )
            InsightPanelRow(
                stringResource(R.string.stats_insight_completion_trend),
                if (previousTasks.isEmpty()) {
                    stringResource(R.string.stats_insight_no_prior_week)
                } else {
                    stringResource(
                        R.string.stats_insight_vs_last_week,
                        if (trendPoints > 0) "↑" else if (trendPoints < 0) "↓" else "→",
                        kotlin.math.abs(trendPoints),
                    )
                },
            )
        }
    }
}

@Composable
private fun InsightPanelRow(label: String, value: String) = Row {
    Text(label, modifier = androidx.compose.ui.Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyMedium)
}

private fun completionRate(tasks: List<Task>) = if (tasks.isEmpty()) 0.0 else tasks.count { it.status == "completed" }.toDouble() / tasks.size
