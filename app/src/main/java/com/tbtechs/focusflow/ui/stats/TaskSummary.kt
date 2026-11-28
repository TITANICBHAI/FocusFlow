package com.tbtechs.focusflow.ui.stats

import com.tbtechs.focusflow.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot

@Composable
fun TaskSummary(snapshot: AnalyticsSnapshot) = StatsCard {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                stringResource(R.string.stats_task_outcomes),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp,
            )
            Text(
                stringResource(R.string.stats_task_scheduled_in_period, snapshot.tasks.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Metric(stringResource(R.string.stats_task_done), snapshot.tasks.completed, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            Metric(stringResource(R.string.stats_task_skipped), snapshot.tasks.skipped, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            Metric(stringResource(R.string.stats_task_missed), snapshot.tasks.missed, MaterialTheme.colorScheme.error, Modifier.weight(1f))
        }
        if (snapshot.tasks.total == 0) {
            Text(
                stringResource(R.string.stats_task_none_in_period),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Metric(
    label: String,
    value: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
) = Column(
    modifier = modifier
        .clip(RoundedCornerShape(14.dp))
        .background(color.copy(alpha = 0.12f))
        .padding(vertical = 10.dp, horizontal = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
) {
    Text(value.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
    Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
