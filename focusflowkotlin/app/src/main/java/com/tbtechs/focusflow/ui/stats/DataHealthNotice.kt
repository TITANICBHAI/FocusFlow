package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot
import com.tbtechs.focusflow.analytics.SOURCE_LOADED
import java.time.Duration
import java.time.Instant

@Composable
fun DataHealthNotice(snapshot: AnalyticsSnapshot) {
    val degraded = snapshot.sourceHealth?.let { health ->
        listOf(health.tasks, health.sessions, health.estimationErrors, health.tasksByHour, health.weeklyRates, health.temptations, health.usageSummary, health.usageHourly)
            .filterNotNull().any { it != SOURCE_LOADED }
    } == true
    val stale = runCatching { Duration.between(Instant.parse(snapshot.generatedAt), Instant.now()).toMinutes() > 5 }.getOrDefault(false)
    if (!degraded && !stale) return
    Card {
        Row {
            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.tertiary)
            Text(
                when {
                    stale -> "This view is stale. Return to it to refresh your local history."
                    snapshot.sourceHealth?.let { listOf(it.tasks, it.sessions, it.estimationErrors, it.tasksByHour, it.weeklyRates, it.temptations, it.usageSummary, it.usageHourly).filterNotNull().any { source -> source == "unavailable" } } == true -> "Some local data sources are unavailable, so related insights are omitted."
                    else -> "Some local data could not be read, so related insights are omitted."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
