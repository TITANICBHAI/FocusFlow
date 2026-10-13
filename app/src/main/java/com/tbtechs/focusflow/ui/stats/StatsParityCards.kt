package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot
import com.tbtechs.focusflow.analytics.LifetimeStats
import com.tbtechs.focusflow.analytics.ANALYTICS_ALL_TIME
import kotlin.math.roundToInt

@Composable
fun FocusTimeHero(snapshot: AnalyticsSnapshot) {
    val minutes = snapshot.sessions.totalFocusMinutes.roundToInt()
    StatsHeroCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(27.dp),
                    )
                }
                Column {
                    Text(
                        "FOCUS TIME",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        formatMinutes(minutes),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatValue("${snapshot.sessions.total}", "sessions", Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                StatValue("${snapshot.sessions.cleanCount}", "clean", Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                StatValue("${snapshot.sessions.avgDurationMinutes.roundToInt()}m", "average", Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
                StatValue("${snapshot.tasks.completed}/${snapshot.tasks.total}", "tasks done", Modifier.weight(1f), MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ProductivityHeatmap(snapshot: AnalyticsSnapshot) {
    val days = listOf("S", "M", "T", "W", "T", "F", "S")
    StatsCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        if (snapshot.window == ANALYTICS_ALL_TIME) "LIFETIME WEEKDAY PATTERN" else "WEEKLY ACTIVITY",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        if (snapshot.window == ANALYTICS_ALL_TIME) {
                            "Task completion across each weekday"
                        } else {
                            "${snapshot.tasks.completed} of ${snapshot.tasks.total} tasks completed"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                days.forEachIndexed { day, label ->
                    val bucket = snapshot.tasks.byDayOfWeek[day]
                    val rate = if ((bucket?.total ?: 0) == 0) 0.0
                    else (bucket?.completed ?: 0).toDouble() / (bucket?.total ?: 1)
                    val cellAccent = when {
                        bucket == null || bucket.total == 0 -> MaterialTheme.colorScheme.surfaceVariant
                        rate >= 0.8 -> MaterialTheme.colorScheme.secondary
                        rate >= 0.5 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                    val cellAlpha = when {
                        bucket == null || bucket.total == 0 -> 0.45f
                        rate >= 0.8 -> 0.95f
                        rate >= 0.5 -> 0.68f
                        else -> 0.42f
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(cellAccent.copy(alpha = cellAlpha)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${bucket?.completed ?: 0}/${bucket?.total ?: 0}",
                                color = if (bucket != null && bucket.total > 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Scheduled tasks by local day. Brighter cells mean a higher completion rate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TemptationStats(
    snapshot: AnalyticsSnapshot,
    onQuickBlock: (String?) -> Unit,
) {
    val blocking = snapshot.blocking
    StatsCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Block, null, tint = MaterialTheme.colorScheme.tertiary)
                }
                Column {
                    Text("DISTRACTION PATTERNS", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Blocked-app attempts in this period",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (blocking.totalAttempts == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f))
                        .padding(13.dp),
                ) {
                    Text(
                        "No blocked-app attempts were recorded. Your protection stayed quiet this period.",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f))
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            blocking.totalAttempts.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text("blocked attempts", style = MaterialTheme.typography.bodySmall)
                    }
                    blocking.peakHour?.let {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "PEAK WINDOW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                hourLabel(it),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
                blocking.byApp.entries
                    .sortedByDescending { it.value.count }
                    .take(4)
                    .forEach { (packageName, app) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(13.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
                                .clickable { onQuickBlock(packageName) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(app.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Tap to quick block",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${app.count}",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
            }
        }
    }
}

@Composable
fun AllTimeStats(
    snapshot: AnalyticsSnapshot,
    lifetime: LifetimeStats?,
    earnedAchievementCount: Int,
) {
    StatsCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text("ALL-TIME PROGRESS", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Your long-term focus record",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatValue(
                    formatMinutes((lifetime?.totalFocusMinutes ?: snapshot.sessions.totalFocusMinutes).roundToInt()),
                    "focus",
                    Modifier.weight(1f),
                    MaterialTheme.colorScheme.primary,
                )
                StatValue(
                    "${lifetime?.completedTasks ?: snapshot.tasks.completed}",
                    "tasks",
                    Modifier.weight(1f),
                    MaterialTheme.colorScheme.secondary,
                )
                StatValue(
                    "${lifetime?.totalSessions ?: snapshot.sessions.total}",
                    "sessions",
                    Modifier.weight(1f),
                    MaterialTheme.colorScheme.tertiary,
                )
                StatValue(
                    "${lifetime?.currentStreakDays ?: 0}d",
                    "streak",
                    Modifier.weight(1f),
                    MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "$earnedAchievementCount achievements earned",
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StatValue(
    value: String,
    label: String,
    modifier: Modifier,
    valueColor: Color,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f))
            .padding(horizontal = 3.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}