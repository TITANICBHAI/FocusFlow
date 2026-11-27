package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
import com.tbtechs.focusflow.analytics.ANALYTICS_ALL_TIME
import com.tbtechs.focusflow.analytics.ANALYTICS_TODAY
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import com.tbtechs.focusflow.analytics.ANALYTICS_YESTERDAY
import com.tbtechs.focusflow.analytics.phone.PhonePeriod
import com.tbtechs.focusflow.analytics.phone.UsageCategory

/**
 * Display names for codes. The codes (enum members, window ids) stay English data; only these
 * composable functions turn them into text the person reads, so they follow the app language.
 */
@Composable
internal fun appSortLabel(sort: AppSort): String = stringResource(
    when (sort) {
        AppSort.TIME -> R.string.stats_sort_time
        AppSort.OPENS -> R.string.stats_sort_opens
        AppSort.AVG_SESSION -> R.string.stats_sort_avg_session
        AppSort.TREND -> R.string.stats_sort_trend
    },
)

@Composable
internal fun extraTabLabel(tab: ExtraTab): String = stringResource(
    when (tab) {
        ExtraTab.OVERVIEW -> R.string.stats_tab_overview
        ExtraTab.APPS -> R.string.stats_apps
        ExtraTab.HABITS -> R.string.stats_tab_habits
    },
)

@Composable
internal fun phoneWindowLabel(window: String): String = stringResource(
    when (window) {
        ANALYTICS_YESTERDAY -> R.string.stats_window_yesterday
        ANALYTICS_WEEK -> R.string.stats_window_week
        ANALYTICS_ALL_TIME -> R.string.stats_window_all_time
        else -> R.string.stats_window_today
    },
)

@Composable
internal fun phonePeriodLabel(period: PhonePeriod): String = stringResource(
    when (period) {
        PhonePeriod.MORNING -> R.string.stats_period_morning
        PhonePeriod.AFTERNOON -> R.string.stats_period_afternoon
        PhonePeriod.EVENING -> R.string.stats_period_evening
        PhonePeriod.LATE_NIGHT -> R.string.stats_period_late_night
    },
)

@Composable
internal fun phonePeriodLabels(): List<String> = PhonePeriod.entries.map { phonePeriodLabel(it) }

@Composable
internal fun usageCategoryLabel(category: UsageCategory): String = stringResource(
    when (category) {
        UsageCategory.SOCIAL -> R.string.stats_cat_social
        UsageCategory.COMMUNICATION -> R.string.stats_cat_messaging
        UsageCategory.ENTERTAINMENT -> R.string.stats_cat_entertainment
        UsageCategory.GAMES -> R.string.stats_cat_games
        UsageCategory.EDUCATION -> R.string.stats_cat_education
        UsageCategory.PRODUCTIVITY -> R.string.stats_cat_productivity
        UsageCategory.NEWS_READING -> R.string.stats_cat_news_reading
        UsageCategory.TOOLS -> R.string.stats_cat_tools
        UsageCategory.OTHER -> R.string.stats_cat_other
    },
)
