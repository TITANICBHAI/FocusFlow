package com.tbtechs.focusflow.ui.stats

import com.tbtechs.focusflow.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.analytics.ACHIEVEMENTS
import com.tbtechs.focusflow.analytics.AchievementDefinition
import com.tbtechs.focusflow.analytics.AchievementState
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot
import com.tbtechs.focusflow.analytics.LifetimeStats
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AchievementRow(
    state: AchievementState,
    snapshot: AnalyticsSnapshot,
    lifetime: LifetimeStats?,
) {
    val catalog = ACHIEVEMENTS
    val earnedIds = state.earnedIds.toSet()
    val earnedCount = earnedIds.size.coerceAtMost(catalog.size)
    StatsCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.stats_ach_milestones).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                    )
                    Text(
                        stringResource(R.string.stats_ach_unlocked_count, earnedCount, catalog.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "$earnedCount/${catalog.size}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            ProgressTrack(
                fraction = if (catalog.isEmpty()) 0f else earnedCount.toFloat() / catalog.size,
                accent = MaterialTheme.colorScheme.primary,
            )
            catalog.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { definition ->
                        val earned = definition.id in earnedIds
                        AchievementTile(
                            definition = definition,
                            earned = earned,
                            isNew = definition.id in state.newlyEarnedIds,
                            snapshot = snapshot,
                            lifetime = lifetime,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AchievementTile(
    definition: AchievementDefinition,
    earned: Boolean,
    isNew: Boolean,
    snapshot: AnalyticsSnapshot,
    lifetime: LifetimeStats?,
    modifier: Modifier,
) {
    val accent = categoryAccent(definition.category)
    val hiddenLocked = definition.hidden && !earned
    val meter = if (earned || hiddenLocked) null else progressFor(definition.id, snapshot, lifetime)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (earned) accent.copy(alpha = 0.11f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
            )
            .border(
                width = 1.dp,
                color = if (earned) accent.copy(alpha = 0.38f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                shape = shape,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accent.copy(alpha = if (earned) 0.22f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (hiddenLocked) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(achievementEmoji(definition.icon), fontSize = 20.sp)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (hiddenLocked) stringResource(R.string.stats_ach_hidden_milestone) else achievementTitle(definition),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (hiddenLocked) stringResource(R.string.stats_ach_secret) else categoryLabel(definition.category),
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            when {
                hiddenLocked -> stringResource(R.string.stats_ach_hidden_hint)
                else -> achievementDescription(definition)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        when {
            earned -> Text(
                stringResource(if (isNew) R.string.stats_ach_newly_unlocked else R.string.stats_ach_unlocked).uppercase(),
                color = accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
            )
            meter != null -> {
                ProgressTrack(fraction = meter.fraction, accent = accent)
                Text(
                    meter.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProgressTrack(fraction: Float, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(CircleShape)
                .background(accent),
        )
    }
}

private data class AchievementMeter(
    val fraction: Float,
    val label: String,
)

@Composable
private fun progressFor(
    id: String,
    snapshot: AnalyticsSnapshot,
    lifetime: LifetimeStats?,
): AchievementMeter {
    val errors = snapshot.tasks.estimationErrorMinutes
    val cleanSessions = lifetime?.cleanSessions ?: 0
    val focusMinutes = lifetime?.totalFocusMinutes ?: 0.0
    val currentStreak = lifetime?.currentStreakDays ?: 0

    return when (id) {
        "RESISTANCE_10_CLEAN_SESSIONS" -> AchievementMeter(
            (cleanSessions / 10f).coerceIn(0f, 1f),
            stringResource(R.string.stats_ach_meter_clean_sessions, cleanSessions),
        )
        "HONEST_ESTIMATOR" -> {
            val averageError = errors.map { abs(it) }.average().takeIf { errors.isNotEmpty() }
            val sampleProgress = (errors.size / 5.0).coerceAtMost(1.0)
            val accuracyProgress = averageError?.let {
                if (it <= 15.0) 1.0 else (15.0 / it).coerceIn(0.0, 1.0)
            } ?: 0.0
            AchievementMeter(
                minOf(sampleProgress, accuracyProgress).toFloat(),
                if (averageError != null) stringResource(R.string.stats_ach_meter_estimates, errors.size, averageError.roundToInt())
                else stringResource(R.string.stats_ach_meter_estimates_none, errors.size),
            )
        }
        "PRESENCE_7_DAYS" -> {
            val days = snapshot.tasks.byDayOfWeek.values.count { it.total > 0 }
            if (snapshot.window == ANALYTICS_WEEK) {
                AchievementMeter((days / 7f).coerceIn(0f, 1f), stringResource(R.string.stats_ach_meter_days_scheduled, days))
            } else {
                AchievementMeter(0f, stringResource(R.string.stats_ach_meter_open_week))
            }
        }
        "PATTERN_BREAKER" -> {
            val attempts = snapshot.blocking.totalAttempts
            val share = snapshot.blocking.topAppShare
            val countProgress = (attempts / 10f).coerceIn(0f, 1f)
            val balanceProgress = share?.let {
                if (it < 0.5) 1.0 else ((1.0 - it) / 0.5).coerceIn(0.0, 1.0)
            } ?: 0.0
            AchievementMeter(
                minOf(countProgress, balanceProgress.toFloat()),
                stringResource(R.string.stats_ach_meter_attempts, attempts, share?.let { "${(it * 100).roundToInt()}%" } ?: "—"),
            )
        }
        "QUIET_WIN" -> AchievementMeter(
            (focusMinutes / 60.0).toFloat().coerceIn(0f, 1f),
            stringResource(R.string.stats_ach_meter_lifetime_min, focusMinutes.roundToInt()),
        )
        "IRON_SESSION" -> AchievementMeter(
            if (snapshot.sessions.cleanCount > 0) 1f else 0f,
            stringResource(R.string.stats_ach_meter_clean_period, snapshot.sessions.cleanCount),
        )
        "THREE_WEEKS" -> AchievementMeter(
            (currentStreak / 21f).coerceIn(0f, 1f),
            stringResource(R.string.stats_ach_meter_streak, currentStreak),
        )
        "LONG_GAME" -> {
            val earlyFinishes = errors.count { it <= -30.0 }
            AchievementMeter(
                (earlyFinishes / 3f).coerceIn(0f, 1f),
                stringResource(R.string.stats_ach_meter_early, earlyFinishes),
            )
        }
        "BACK_AGAIN" -> {
            val gapDays = sessionGapDays(lifetime, snapshot)
            AchievementMeter(
                ((gapDays ?: 0.0) / 6.0).toFloat().coerceIn(0f, 1f),
                gapDays?.let { stringResource(R.string.stats_ach_meter_away, it.roundToInt()) }
                    ?: stringResource(R.string.stats_ach_meter_start_tracking),
            )
        }
        "RESET" -> {
            val gapDays = sessionGapDays(lifetime, snapshot)
            val daysProgress = ((gapDays ?: 0.0) / 6.0).coerceIn(0.0, 1.0)
            val taskProgress = if (snapshot.tasks.completed > 0) 1.0 else 0.0
            AchievementMeter(
                ((daysProgress + taskProgress) / 2.0).toFloat(),
                stringResource(R.string.stats_ach_meter_reset, gapDays?.roundToInt() ?: 0, if (taskProgress > 0) 1 else 0),
            )
        }
        else -> AchievementMeter(0f, stringResource(R.string.stats_ach_meter_keep_going))
    }
}

private fun sessionGapDays(lifetime: LifetimeStats?, snapshot: AnalyticsSnapshot): Double? =
    lifetime?.lastSessionAt?.let { lastSession ->
        runCatching {
            Duration.between(Instant.parse(lastSession), Instant.parse(snapshot.generatedAt))
                .toMillis() / (24.0 * 60.0 * 60.0 * 1000.0)
        }.getOrNull()?.coerceAtLeast(0.0)
    }

private fun categoryAccent(category: String): Color = when (category) {
    "resistance" -> Color(0xFF9B8CFF)
    "honesty" -> Color(0xFF42D6C5)
    "presence" -> Color(0xFF65A8FF)
    "pattern_breaking" -> Color(0xFFFFB454)
    else -> Color(0xFFDB8BFF)
}

@Composable
private fun categoryLabel(category: String): String = when (category) {
    "resistance" -> stringResource(R.string.stats_ach_cat_resistance)
    "honesty" -> stringResource(R.string.stats_ach_cat_honesty)
    "presence" -> stringResource(R.string.stats_ach_cat_presence)
    "pattern_breaking" -> stringResource(R.string.stats_ach_cat_pattern_breaking)
    "hidden" -> stringResource(R.string.stats_ach_cat_hidden)
    else -> category.replaceFirstChar { it.uppercase() }
}

private fun achievementEmoji(icon: String): String = when {
    icon.contains("shield") -> "🛡️"
    icon.contains("resize") -> "🎯"
    icon.contains("calendar") -> "🗓️"
    icon.contains("compare") -> "⚖️"
    icon.contains("eye-off") -> "🌙"
    icon.contains("flash") -> "⚡"
    icon.contains("trophy") -> "🏆"
    icon.contains("hourglass") -> "⏳"
    icon.contains("return") -> "↩️"
    icon.contains("refresh") -> "🔄"
    else -> "✨"
}
