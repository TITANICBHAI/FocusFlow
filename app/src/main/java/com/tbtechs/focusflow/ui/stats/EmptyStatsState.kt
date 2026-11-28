package com.tbtechs.focusflow.ui.stats

import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
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
            ANALYTICS_YESTERDAY -> stringResource(R.string.stats_empty_yesterday)
            ANALYTICS_TODAY -> stringResource(R.string.stats_empty_today)
            ANALYTICS_ALL_TIME -> stringResource(R.string.stats_empty_all_time)
            ANALYTICS_THREE_MONTHS -> stringResource(R.string.stats_empty_three_months)
            else -> stringResource(R.string.stats_empty_week)
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.stats_insights_will_appear_here_once), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
