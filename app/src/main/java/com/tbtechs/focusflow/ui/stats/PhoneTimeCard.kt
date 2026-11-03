package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot
import kotlin.math.roundToInt

/** "1h 20m" / "45m". */
internal fun minutesLabel(minutes: Double): String {
    val total = minutes.roundToInt().coerceAtLeast(0)
    val hours = total / 60
    val rest = total % 60
    return when {
        hours == 0 -> "${rest}m"
        rest == 0 -> "${hours}h"
        else -> "${hours}h ${rest}m"
    }
}

/**
 * The usage-first part of Stats: where the phone time actually went in this period, with a
 * one-tap block for each app. It needs only Usage Access, no tasks and no focus sessions.
 */
@Composable
internal fun PhoneTimeCard(
    snapshot: AnalyticsSnapshot,
    onBlockApp: (String?) -> Unit,
) {
    val usage = snapshot.phoneUsage ?: return
    if (usage.apps.isEmpty()) return
    val total = usage.byHour.values.sum()

    StatsCard {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("WHERE YOUR PHONE TIME WENT", style = MaterialTheme.typography.labelLarge)
            Text(
                buildString {
                    append(minutesLabel(total))
                    append(" in total")
                    if (usage.peakHour != null) append(" · busiest around ${hourLabel(usage.peakHour)}")
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            usage.apps.take(5).forEach { app ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            minutesLabel(app.minutes),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val packageName = app.packageName
                    if (packageName != null) {
                        TextButton(onClick = { onBlockApp(packageName) }) {
                            Text("Block", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
