package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tbtechs.focusflow.analytics.AnalyticsWindow
import com.tbtechs.focusflow.analytics.ANALYTICS_YESTERDAY

@Composable
fun EmptyStatsState(window: AnalyticsWindow) = Card {
    Column {
        Icon(Icons.Outlined.Analytics, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (window == ANALYTICS_YESTERDAY) "Nothing recorded yesterday" else "No activity recorded this week", style = MaterialTheme.typography.titleMedium)
        Text("Insights will appear here once FocusFlow has a task, session, or blocked-app attempt to read.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
