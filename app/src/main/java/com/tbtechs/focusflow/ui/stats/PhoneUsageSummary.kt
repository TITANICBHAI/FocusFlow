package com.tbtechs.focusflow.ui.stats

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
        Text("ANDROID USAGESTATS", style = MaterialTheme.typography.labelLarge)
        Text("ON DEVICE", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
        val usage = snapshot.phoneUsage
        if (usage == null) {
            val usageUnavailable = snapshot.sourceHealth?.usageSummary?.let { it != SOURCE_LOADED } == true
            Text(
                if (usageUnavailable) {
                    "Usage data unavailable. Grant Usage Access or try again."
                } else {
                    "Android returned no phone-use history for this period."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else {
            val minutes = usage.byHour.values.sum().roundToInt()
            Text(formatUsageMinutes(minutes), style = MaterialTheme.typography.headlineSmall, color = Color(0xFF63A7FF))
            Text(
                "Android app foreground time — FocusFlow home-screen time excluded",
                style = MaterialTheme.typography.bodySmall,
                color = DarkTextMuted,
            )
            Text(
                buildString {
                    append(if (usage.peakHour == null) "No hourly peak was recorded." else "Heaviest use is around ${hourLabel(usage.peakHour)}.")
                    usage.heaviestApp?.let { append(" ${it.appName} was the most-used app.") }
                },
            )
            UsageComparison(
                observedMinutes = minutes,
                focusMinutes = snapshot.sessions.totalFocusMinutes.roundToInt(),
                blockedAttempts = snapshot.blocking.totalAttempts,
            )
            HourlyUsageBars(usage.byHour)
            if (usage.apps.isNotEmpty()) {
                Text(
                    "Top apps by observed time",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    "minutes",
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
        UsageMetric(formatUsageMinutes(focusMinutes), "Focus time", BrandPrimary)
        UsageMetric(ratio?.let { "$it%" } ?: "—", "Focus / observed", if (ratio != null) StatusReady else DarkTextMuted)
        UsageMetric(blockedAttempts.toString(), "Blocked attempts", if (blockedAttempts > 0) SunAmber else StatusReady)
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
        Text("Observed time by hour", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = DarkTextPrimary)
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
        Text("${app.minutes.roundToInt()}m", fontSize = 12.sp, color = DarkTextPrimary)
    }
}

private fun formatUsageMinutes(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

/** Plain analytics formatter: 14 becomes "2pm". */
fun hourLabel(hour: Int?): String {
    if (hour == null || hour !in 0..23) return "that hour"
    val normalized = hour % 12
    return "${if (normalized == 0) 12 else normalized}${if (hour < 12) "am" else "pm"}"
}
