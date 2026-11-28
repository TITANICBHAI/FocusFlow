package com.tbtechs.focusflow.ui.stats

import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot
import com.tbtechs.focusflow.analytics.SOURCE_LOADED
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.StatusReady
import com.tbtechs.focusflow.ui.theme.SunAmber
import kotlin.math.roundToInt

@Composable
fun PhoneUsageSummary(
    snapshot: AnalyticsSnapshot,
    onQuickBlock: (String?) -> Unit = {},
) = StatsCard {
    Column(modifier = androidx.compose.ui.Modifier.padding(12.dp)) {
        Text(stringResource(R.string.stats_android_usagestats), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.stats_on_device), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
        val usage = snapshot.phoneUsage
        if (usage == null) {
            val usageUnavailable = snapshot.sourceHealth?.usageSummary?.let { it != SOURCE_LOADED } == true
            Text(
                if (usageUnavailable) {
                    stringResource(R.string.stats_phone_unavailable)
                } else {
                    stringResource(R.string.stats_phone_no_history)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else {
            val minutes = usage.byHour.values.sum().roundToInt()
            Text(statsDuration(minutes), style = MaterialTheme.typography.headlineSmall, color = Color(0xFF63A7FF))
            Text(
                stringResource(R.string.stats_phone_foreground_note),
                style = MaterialTheme.typography.bodySmall,
                color = DarkTextMuted,
            )
            val peakLabel = statsHourLabel(usage.peakHour)
            val peakText = if (peakLabel == null) stringResource(R.string.stats_phone_no_peak) else stringResource(R.string.stats_phone_peak, peakLabel)
            val heaviestText = usage.heaviestApp?.let { stringResource(R.string.stats_phone_most_used, it.appName) }
            Text(listOfNotNull(peakText, heaviestText).joinToString(" "))
            UsageComparison(
                observedMinutes = minutes,
                focusMinutes = snapshot.sessions.totalFocusMinutes.roundToInt(),
                blockedAttempts = snapshot.blocking.totalAttempts,
            )
            HourlyUsageBars(usage.byHour)
            if (usage.apps.isNotEmpty()) {
                Text(
                    stringResource(R.string.stats_phone_top_apps),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    stringResource(R.string.stats_phone_minutes_axis),
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkTextMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
                val maxMinutes = usage.apps.firstOrNull()?.minutes?.coerceAtLeast(1.0) ?: 1.0
                usage.apps.take(5).forEachIndexed { index, app ->
                    UsageAppRow(
                        app = app,
                        rank = index + 1,
                        maxMinutes = maxMinutes,
                        onQuickBlock = onQuickBlock,
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageComparison(
    observedMinutes: Int,
    focusMinutes: Int,
    blockedAttempts: Int,
) {
    val ratio = if (observedMinutes > 0) (focusMinutes * 100 / observedMinutes).coerceAtMost(999) else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.55f))
            .padding(vertical = 10.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
    ) {
        UsageMetric(statsDuration(focusMinutes), stringResource(R.string.stats_phone_focus_time), BrandPrimary)
        UsageMetric(ratio?.let { "$it%" } ?: "—", stringResource(R.string.stats_phone_focus_vs_observed), if (ratio != null) StatusReady else DarkTextMuted)
        UsageMetric(blockedAttempts.toString(), stringResource(R.string.stats_phone_blocked_attempts), if (blockedAttempts > 0) SunAmber else StatusReady)
    }
}

@Composable
private fun UsageMetric(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = DarkTextMuted)
    }
}

@Composable
private fun HourlyUsageBars(byHour: Map<Int, Double>) {
    val maxMinutes = byHour.values.maxOrNull()?.coerceAtLeast(0.0) ?: 0.0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
    ) {
        Text(stringResource(R.string.stats_observed_time_by_hour), fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = DarkTextPrimary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(78.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            (0..23).forEach { hour ->
                val minutes = byHour[hour] ?: 0.0
                val fraction = if (maxMinutes > 0.0) (minutes / maxMinutes).toFloat().coerceIn(0f, 1f) else 0f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((4f + 56f * fraction).dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(if (minutes > 0.0) Color(0xFF63A7FF) else DarkBorder.copy(alpha = 0.7f)),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            listOf("12a", "4a", "8a", "12p", "4p", "8p").forEach { label ->
                Text(label, fontSize = 10.sp, color = DarkTextMuted)
            }
        }
    }
}

@Composable
private fun UsageAppRow(
    app: AnalyticsSnapshot.HeaviestApp,
    rank: Int,
    maxMinutes: Double,
    onQuickBlock: (String?) -> Unit,
) {
    val enabled = app.packageName != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { app.packageName?.let(onQuickBlock) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (rank == 1) BrandPrimary.copy(alpha = 0.12f) else DarkSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("#$rank", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = if (rank == 1) BrandPrimary else DarkTextMuted)
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.appName, maxLines = 1, color = DarkTextPrimary)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(DarkBorder),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((app.minutes / maxMinutes).toFloat().coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(Color(0xFF63A7FF)),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(statsDuration(app.minutes.roundToInt()), fontSize = 12.sp, color = DarkTextPrimary)
    }
}


