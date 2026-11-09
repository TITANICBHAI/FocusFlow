package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.analytics.phone.PhoneReport
import com.tbtechs.focusflow.analytics.phone.PhoneText
import com.tbtechs.focusflow.analytics.phone.insights.InsightKind
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsight
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightsResult
import com.tbtechs.focusflow.ui.stats.StatsBlue
import com.tbtechs.focusflow.ui.stats.StatsCard
import com.tbtechs.focusflow.ui.stats.StatsGreen
import com.tbtechs.focusflow.ui.stats.StatsRed
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.SunAmber

private const val INSIGHTS_PREVIEW = 3

/**
 * Extra · Habits: the nudge switch, what FocusFlow noticed, then one visual card each for phone use,
 * time of day, distraction and focus. Numbers first, no explanations — those live in Settings.
 */
@Composable
internal fun ExtraHabitsTab(
    report: PhoneReport,
    window: String,
    insightsState: PhoneInsightsUiState,
    onBlockApp: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtraTabList(modifier) {
        item { NudgeCard() }
        item { NoticedCard(insightsState) }
        item { PhoneUseCard(report, window) }
        item { TimeOfDayCard(report, window) }
        item { DistractionCard(report, window, onBlockApp) }
        if (report.focus.sessions > 0 || report.focus.blockedAttempts > 0) {
            item { FocusCard(report) }
        }
    }
}

@Composable
private fun NoticedCard(state: PhoneInsightsUiState) {
    var showAll by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<String?>(null) }
    ExtraCard("What we noticed") {
        val result = (state as? PhoneInsightsUiState.Loaded)?.result
        when (result) {
            null -> PhoneMutedNote("Looking at your habits…")
            is PhoneInsightsResult.Building -> PhoneMutedNote(buildingMessage(result.completeDays))
            is PhoneInsightsResult.Failed -> PhoneMutedNote(INSIGHTS_FAILED)
            PhoneInsightsResult.PermissionNeeded -> PhoneMutedNote("Allow usage access to see this.")
            is PhoneInsightsResult.Ready -> {
                val items = result.insights.filter { it.kind != InsightKind.DATA_NOTE }
                if (items.isEmpty()) {
                    PhoneMutedNote(INSIGHTS_EMPTY)
                } else {
                    val shown = if (showAll) items else items.take(INSIGHTS_PREVIEW)
                    shown.forEach { insight ->
                        InsightLine(insight, expanded = openId == insight.id) {
                            openId = if (openId == insight.id) null else insight.id
                        }
                    }
                    if (items.size > INSIGHTS_PREVIEW) {
                        TextButton(onClick = { showAll = !showAll }) {
                            Text(if (showAll) "Show less" else "Show all ${items.size}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightLine(insight: PhoneInsight, expanded: Boolean, onToggle: () -> Unit) {
    val accent = when (insight.direction) {
        com.tbtechs.focusflow.analytics.phone.TrendDirection.UP -> StatsRed
        com.tbtechs.focusflow.analytics.phone.TrendDirection.DOWN -> StatsGreen
        else -> StatsBlue
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "Collapse" else "Expand", role = Role.Button, onClick = onToggle)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(directionGlyph(insight.direction).ifEmpty { "●" }, color = accent, fontSize = 14.sp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(insight.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (expanded) Text(insight.message, fontSize = 13.sp, color = DarkTextMuted)
        }
    }
}

@Composable
private fun PhoneUseCard(report: PhoneReport, window: String) {
    val b = report.behaviour
    val single = isSingleDay(window)
    val unlockHero = unlocksHero(b, window)
    ExtraCard("Phone use") {
        if (b.daysWithData <= 0 && b.unlocks <= 0 && b.wakes <= 0) {
            PhoneMutedNote("No phone activity for this period yet.")
            return@ExtraCard
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (unlockHero != null) {
                PhoneHabitTile(unlockHero.value, if (b.unlockSignalAvailable) "Unlocks" else "Wake-ups", StatsBlue, Modifier.weight(1f))
            }
            b.quickCheckSharePercent?.let {
                PhoneHabitTile(PhoneText.percent(it), "Quick checks", SunAmber, Modifier.weight(1f))
            }
            if (b.longestSessionMs > 0L) {
                PhoneHabitTile(PhoneText.duration(b.longestSessionMs), "Longest session", StatsGreen, Modifier.weight(1f))
            }
        }
        val byHour = if (b.unlockSignalAvailable) b.unlocksByHour else b.wakesByHour
        HourBars(
            values = byHour.map { it.toFloat() },
            contentDescription = "Bar chart of ${if (b.unlockSignalAvailable) "unlocks" else "wake-ups"} for each hour of the day",
            highlightIndex = busiestHour(byHour),
            height = 64.dp,
        )
        val firstApps = topFirstAppRows(report)
        if (firstApps.isNotEmpty()) {
            PhoneSectionTitle("First app you open", Modifier.padding(top = 4.dp))
            firstApps.forEach { ShareRow(it.title, it.subtitle, it.fraction, it.trailing) }
        }
        if (!single && b.avgGapBetweenUnlocksMs != null) {
            PhoneMutedNote("About ${PhoneText.duration(b.avgGapBetweenUnlocksMs)} between unlocks")
        }
    }
}

private fun topFirstAppRows(report: PhoneReport): List<TopAppRow> =
    report.behaviour.firstApps.take(3).map { row ->
        TopAppRow(
            title = row.appName,
            subtitle = null,
            fraction = (row.shareOfUnlocksPercent / 100.0).toFloat().coerceIn(0f, 1f),
            trailing = PhoneText.percent(Math.round(row.shareOfUnlocksPercent).toInt()),
        )
    }

@Composable
private fun TimeOfDayCard(report: PhoneReport, window: String) {
    val rows = partOfDayRows(report.screenTime.screenOnByHourAvgMs, window)
    val days = report.screenTime.days
    val weekday = showWeekdayChart(days)
    if (rows.isEmpty() && !weekday) return
    ExtraCard("Time of day") {
        rows.forEach { ShareRow(it.title, it.subtitle, it.fraction, it.trailing) }
        if (weekday) {
            val avgs = weekdayAverages(days)
            PhoneSectionTitle("By weekday", Modifier.padding(top = 4.dp))
            DayBars(points = weekdayBarPoints(avgs), contentDescription = "Average screen time for each day of the week", height = 72.dp)
            weekdayCaption(avgs)?.let { PhoneMutedNote(it) }
        }
    }
}

@Composable
private fun DistractionCard(report: PhoneReport, window: String, onBlockApp: (String?) -> Unit) {
    val d = report.distraction
    if (d.distractionMs <= 0L && d.apps.isEmpty()) return
    ExtraCard("Distraction") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhoneHabitTile(PhoneText.duration(d.distractionMs), if (isSingleDay(window)) "In distracting apps" else "In distracting apps", StatsRed, Modifier.weight(1f))
            d.sharePercent?.let { PhoneHabitTile(PhoneText.percent(it), "Of your app time", SunAmber, Modifier.weight(1f)) }
            d.peakHour?.let { PhoneHabitTile(PhoneText.hour(it), "Peak hour", StatsBlue, Modifier.weight(1f)) }
        }
        distractionChangeText(d)?.let { PhoneMutedNote(it) }
        d.apps.take(3).forEach { app ->
            ShareRow(
                title = app.appName,
                subtitle = PhoneText.duration(app.totalMs),
                fraction = (app.sharePercent / 100.0).toFloat().coerceIn(0f, 1f),
                trailing = PhoneText.percent(Math.round(app.sharePercent).toInt()),
            )
        }
        HourBars(
            values = d.byHourMs.map { it.toFloat() },
            contentDescription = "Bar chart of time in distracting apps for each hour of the day",
            highlightIndex = d.peakHour,
            height = 56.dp,
        )
        if (d.reflexApps.isNotEmpty()) {
            PhoneSectionTitle("Opened on reflex", Modifier.padding(top = 4.dp))
            d.reflexApps.take(3).forEach { app ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PhoneListRow(app.appName, reflexSubtitle(app), Modifier.weight(1f))
                    TextButton(onClick = { onBlockApp(app.packageName) }) { Text("Block") }
                }
            }
        }
    }
}

@Composable
private fun FocusCard(report: PhoneReport) {
    val f = report.focus
    ExtraCard("Focus") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhoneHabitTile(PhoneText.duration(f.focusMs), "Focus time", StatsGreen, Modifier.weight(1f))
            heldRatePercent(f)?.let { PhoneHabitTile(PhoneText.percent(it), "Sessions held", StatsBlue, Modifier.weight(1f)) }
            PhoneHabitTile(f.blockedAttempts.toString(), "Blocked attempts", SunAmber, Modifier.weight(1f))
        }
        if (f.blockedByHour.any { it > 0 }) {
            HourBars(
                values = f.blockedByHour.map { it.toFloat() },
                contentDescription = "Bar chart of blocked attempts for each hour of the day",
                highlightIndex = f.blockedPeakHour,
                height = 56.dp,
            )
        }
        blockedAppRows(f).take(3).forEach { ShareRow(it.title, it.subtitle, it.fraction, it.trailing) }
    }
}
