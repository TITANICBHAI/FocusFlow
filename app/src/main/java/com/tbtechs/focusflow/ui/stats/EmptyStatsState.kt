package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.analytics.AnalyticsWindow
import com.tbtechs.focusflow.analytics.ANALYTICS_ALL_TIME
import com.tbtechs.focusflow.analytics.ANALYTICS_THREE_MONTHS
import com.tbtechs.focusflow.analytics.ANALYTICS_TODAY
import com.tbtechs.focusflow.analytics.ANALYTICS_YESTERDAY

@Composable
fun EmptyStatsState(window: AnalyticsWindow) = StatsCard {
    Column(modifier = androidx.compose.ui.Modifier.padding(12.dp)) {
        Icon(Icons.Outlined.Analytics, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        val title = when (window) {
            ANALYTICS_YESTERDAY -> "Nothing recorded yesterday"
            ANALYTICS_TODAY -> "Nothing recorded today"
            ANALYTICS_ALL_TIME -> "No activity recorded yet"
            ANALYTICS_THREE_MONTHS -> "No activity recorded in the last 3 months"
            else -> "No activity recorded this week"
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text("Insights will appear here once FocusFlow has a task, session, or blocked-app attempt to read.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
