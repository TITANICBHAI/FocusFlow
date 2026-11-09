package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.foundation.clickable
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
                Text("Sort: ${sort.label}")
            }
        }

        if (section.apps.isEmpty()) {
            item { ExtraCard("Apps") { PhoneMutedNote("No app usage recorded for this period.") } }
        } else {
            item { PhoneSectionTitle("Apps") }
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
                    TextButton(onClick = { showAll = true }) { Text("Show more") }
                }
            }

            if (section.categories.isNotEmpty()) {
                item {
                    ExtraCard("Categories") {
                        section.categories.forEach { c ->
                            ShareRow(
                                title = c.category.label,
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
                    ExtraCard("Time of day") {
                        periods.forEach { p ->
                            ShareRow(
                                title = p.period.label,
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
    StatsCard {
        Column(
            Modifier
                .clickable(onClickLabel = if (isExpanded) "Collapse details" else "Show details", role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = if (isExpanded) "Expanded" else "Collapsed" }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(app.appName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(app.category.label, fontSize = 12.sp, color = DarkTextMuted)
            Text(appRowSummary(app), fontSize = 13.sp)
            trendText(app)?.let { Text(it, fontSize = 12.sp, color = DarkTextMuted) }

            if (isExpanded) {
                PhoneSectionTitle("Session length", Modifier.padding(top = 8.dp))
                BucketBars(
                    counts = app.sessionBucketCounts,
                    labels = PhoneBuckets.LABELS,
                    contentDescription = "Number of sessions of ${app.appName} by length",
                )
                PhoneSectionTitle("Time of day (minutes)", Modifier.padding(top = 8.dp))
                BucketBars(
                    counts = periodMinutes(app),
                    labels = periodBarLabels(),
                    contentDescription = "Minutes of ${app.appName} by time of day",
                )
                val daily = appDailyPoints(app, report.screenTime.days)
                if (daily.isNotEmpty()) {
                    PhoneSectionTitle("Last days", Modifier.padding(top = 8.dp))
                    DayBars(points = daily, contentDescription = "Daily time in ${app.appName} over the last ${daily.size} days")
                }
                TextButton(onClick = { onBlockApp(app.packageName) }) { Text("Block this app") }
            }
        }
    }
}
