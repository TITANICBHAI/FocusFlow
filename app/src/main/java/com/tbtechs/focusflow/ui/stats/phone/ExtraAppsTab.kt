package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
import androidx.compose.foundation.clickable
import com.tbtechs.focusflow.ui.theme.scaledSp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.analytics.phone.AppUsageRow
import com.tbtechs.focusflow.analytics.phone.PhoneBuckets
import com.tbtechs.focusflow.analytics.phone.PhoneReport
import com.tbtechs.focusflow.analytics.phone.PhoneText
import com.tbtechs.focusflow.ui.stats.StatsCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted

/** Extra · Apps: sortable app list with a one-at-a-time drill-down, categories and time-of-day habits. */
@Composable
internal fun ExtraAppsTab(report: PhoneReport, onBlockApp: (String?) -> Unit, modifier: Modifier = Modifier) {
    var sort by rememberSaveable { mutableStateOf(AppSort.TIME) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }

    val section = report.apps
    val sorted = remember(section.apps, sort) { sortApps(section.apps, sort) }
    val visible = sorted.take(visibleAppCount(sorted.size, showAll))

    ExtraTabList(modifier) {
        item {
            TextButton(onClick = { sort = AppSort.entries[(sort.ordinal + 1) % AppSort.entries.size] }) {
                Text(stringResource(R.string.stats_sort, appSortLabel(sort)))
            }
        }

        if (section.apps.isEmpty()) {
            item { ExtraCard(stringResource(R.string.stats_apps)) { PhoneMutedNote(stringResource(R.string.stats_no_app_usage_recorded)) } }
        } else {
            item { PhoneSectionTitle(stringResource(R.string.stats_apps)) }
            items(visible, key = { it.packageName }) { app ->
                AppCard(
                    app = app,
                    isExpanded = expanded == app.packageName,
                    report = report,
                    onBlockApp = onBlockApp,
                    onToggle = { expanded = if (expanded == app.packageName) null else app.packageName },
                )
            }
            if (sorted.size > visible.size) {
                item {
                    TextButton(onClick = { showAll = true }) { Text(stringResource(R.string.stats_show_more)) }
                }
            }

            if (section.categories.isNotEmpty()) {
                item {
                    ExtraCard(stringResource(R.string.stats_categories)) {
                        section.categories.forEach { c ->
                            ShareRow(
                                title = usageCategoryLabel(c.category),
                                subtitle = PhoneText.duration(c.totalMs),
                                fraction = (c.sharePercent / 100.0).toFloat().coerceIn(0f, 1f),
                                trailing = PhoneText.percent(Math.round(c.sharePercent).toInt()),
                            )
                        }
                    }
                }
            }

            val periods = section.periods.filter { it.totalMs > 0L }
            if (periods.isNotEmpty()) {
                item {
                    ExtraCard(stringResource(R.string.stats_time_of_day)) {
                        periods.forEach { p ->
                            ShareRow(
                                title = phonePeriodLabel(p.period),
                                subtitle = p.topApps.firstOrNull()?.appName,
                                fraction = (p.sharePercent / 100.0).toFloat().coerceIn(0f, 1f),
                                trailing = PhoneText.percent(Math.round(p.sharePercent).toInt()),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCard(app: AppUsageRow, isExpanded: Boolean, report: PhoneReport, onBlockApp: (String?) -> Unit, onToggle: () -> Unit) {
    val expandedStateText = stringResource(if (isExpanded) R.string.settings_expanded else R.string.settings_collapsed)
    StatsCard {
        Column(
            Modifier
                .clickable(onClickLabel = stringResource(if (isExpanded) R.string.stats_collapse_details else R.string.stats_show_details), role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = expandedStateText }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(app.appName, fontSize = 16.scaledSp, fontWeight = FontWeight.SemiBold)
            Text(usageCategoryLabel(app.category), fontSize = 12.scaledSp, color = DarkTextMuted)
            Text(appRowSummary(app), fontSize = 13.scaledSp)
            trendText(app)?.let { Text(it, fontSize = 12.scaledSp, color = DarkTextMuted) }

            if (isExpanded) {
                PhoneSectionTitle(stringResource(R.string.stats_session_length), Modifier.padding(top = 8.dp))
                BucketBars(
                    counts = app.sessionBucketCounts,
                    labels = PhoneBuckets.LABELS,
                    contentDescription = stringResource(R.string.stats_number_of_sessions_of_by, app.appName),
                )
                PhoneSectionTitle(stringResource(R.string.stats_time_of_day_minutes), Modifier.padding(top = 8.dp))
                BucketBars(
                    counts = periodMinutes(app),
                    labels = phonePeriodLabels(),
                    contentDescription = stringResource(R.string.stats_minutes_of_by_time_of, app.appName),
                )
                val daily = appDailyPoints(app, report.screenTime.days)
                if (daily.isNotEmpty()) {
                    PhoneSectionTitle(stringResource(R.string.stats_last_days), Modifier.padding(top = 8.dp))
                    DayBars(points = daily, contentDescription = stringResource(R.string.stats_daily_time_in_over_the, app.appName, daily.size))
                }
                TextButton(onClick = { onBlockApp(app.packageName) }) { Text(stringResource(R.string.stats_block_this_app)) }
            }
        }
    }
}
