package com.tbtechs.focusflow.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
import com.tbtechs.focusflow.analytics.AchievementDefinition

/** String-resource ids for one achievement's title and description. */
internal data class AchievementTextRes(val title: Int, val description: Int)

/**
 * Display text for each achievement, keyed by [AchievementDefinition.id].
 * The ids and the English text in the catalog are stored data; only what the person reads is translated.
 * AchievementTextTest fails if a catalog id has no entry here.
 */
internal val ACHIEVEMENT_TEXT: Map<String, AchievementTextRes> = mapOf(
    "RESISTANCE_10_CLEAN_SESSIONS" to AchievementTextRes(R.string.stats_ach_resistance_clean_title, R.string.stats_ach_resistance_clean_desc),
    "HONEST_ESTIMATOR" to AchievementTextRes(R.string.stats_ach_honest_estimator_title, R.string.stats_ach_honest_estimator_desc),
    "PRESENCE_7_DAYS" to AchievementTextRes(R.string.stats_ach_presence_7_title, R.string.stats_ach_presence_7_desc),
    "PATTERN_BREAKER" to AchievementTextRes(R.string.stats_ach_pattern_breaker_title, R.string.stats_ach_pattern_breaker_desc),
    "QUIET_WIN" to AchievementTextRes(R.string.stats_ach_quiet_win_title, R.string.stats_ach_quiet_win_desc),
    "IRON_SESSION" to AchievementTextRes(R.string.stats_ach_iron_session_title, R.string.stats_ach_iron_session_desc),
    "THREE_WEEKS" to AchievementTextRes(R.string.stats_ach_three_weeks_title, R.string.stats_ach_three_weeks_desc),
    "LONG_GAME" to AchievementTextRes(R.string.stats_ach_long_game_title, R.string.stats_ach_long_game_desc),
    "BACK_AGAIN" to AchievementTextRes(R.string.stats_ach_back_again_title, R.string.stats_ach_back_again_desc),
    "RESET" to AchievementTextRes(R.string.stats_ach_reset_title, R.string.stats_ach_reset_desc),
)

@Composable
internal fun achievementTitle(definition: AchievementDefinition): String =
    ACHIEVEMENT_TEXT[definition.id]?.let { stringResource(it.title) } ?: definition.title

@Composable
internal fun achievementDescription(definition: AchievementDefinition): String =
    ACHIEVEMENT_TEXT[definition.id]?.let { stringResource(it.description) } ?: definition.description
