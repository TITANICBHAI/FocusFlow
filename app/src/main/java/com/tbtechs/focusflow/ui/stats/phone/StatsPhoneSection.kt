package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.analytics.phone.PhoneReport
import com.tbtechs.focusflow.analytics.phone.PhoneText
import com.tbtechs.focusflow.ui.stats.StatsCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted

/** Phone-habit cards shown on the Stats screen, under the existing device-usage card. */
@Composable
internal fun PhoneStatsSection(
    phoneViewModel: PhoneStatsViewModel,
    window: String,
    onOpenExtra: () -> Unit,
    onOpenInsights: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by phoneViewModel.report.collectAsState()
    when (val s = state) {
        PhoneReportUiState.Loading -> PhoneStateMessage(s, {}, modifier)
        PhoneReportUiState.PermissionNeeded -> Unit // the existing "Usage access" card already explains this
        PhoneReportUiState.NoData -> PhoneStateMessage(s, {}, modifier)
        is PhoneReportUiState.Failed -> PhoneStateMessage(s, {}, modifier)
        is PhoneReportUiState.Ready -> PhoneStatsCards(s.report, window, onOpenExtra, onOpenInsights, modifier)
    }
}

@Composable
private fun PhoneStatsCards(
    report: PhoneReport,
    window: String,
    onOpenExtra: () -> Unit,
    onOpenInsights: () -> Unit,
    modifier: Modifier,
) {
    val screen = report.screenTime
    val behaviour = report.behaviour
    val single = isSingleDay(window)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1 · SCREEN TIME
        val hero = screenTimeHero(screen, window)
        StatsCard {
            Column(
                Modifier.padding(16.dp).semantics { contentDescription = "Screen time: ${hero.value} ${hero.caption}" },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PhoneSectionTitle("Screen time")
                PhoneHero(hero)
                if (!single) PhoneMutedNote("Total ${PhoneText.duration(screen.totalScreenOnMs)}")
                inAppsText(screen, window)?.let { line ->
                    Text(line, fontSize = 14.sp)
                    PhoneMutedNote("Screen on includes the lock screen and home screen.")
                }
                val highest = screen.highest
                val lowest = screen.lowest
                if (highest != null || lowest != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (highest != null) {
                            PhoneStatTile(PhoneText.duration(highest.screenOnMs), "Highest · " + PhoneText.weekdayDate(highest.date), Modifier.weight(1f))
                        }
                        if (lowest != null) {
                            PhoneStatTile(PhoneText.duration(lowest.screenOnMs), "Lowest · " + PhoneText.weekdayDate(lowest.date), Modifier.weight(1f))
                        }
                    }
                }
                weekdayWeekendNote(screen)?.let { PhoneMutedNote(it) }
                coverageNote(report.coverage, window)?.let { PhoneMutedNote(it) }
            }
        }

        // 2 · UNLOCKS & CHECKS
        val unlocks = unlocksHero(behaviour, window)
        StatsCard {
            Column(
                Modifier.padding(16.dp).semantics {
                    contentDescription = if (unlocks == null) "Unlocks and checks: no phone activity recorded" else "Unlocks and checks: ${unlocks.value} ${unlocks.caption}"
                },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PhoneSectionTitle("Unlocks & checks")
                if (unlocks == null) {
                    PhoneMutedNote("No phone activity recorded for this period.")
                } else {
                    PhoneHero(unlocks)
                    if (behaviour.unlockSignalAvailable) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            PhoneStatTile(wakeUpsStat(behaviour, window), "Screen wake-ups", Modifier.weight(1f))
                            PhoneStatTile(gapStat(behaviour), "Between unlocks", Modifier.weight(1f))
                            PhoneStatTile(quickChecksStat(behaviour), "Quick checks · under 1 minute", Modifier.weight(1f))
                        }
                        PhoneMutedNote("Wake-ups include the screen turning on for notifications.")
                    } else {
                        PhoneMutedNote("This phone doesn't report unlocks (is a screen lock set?), so unlock counts aren't shown.")
                    }
                }
            }
        }

        // 3 · SCREEN TIME BY DAY
        if (screen.days.size >= 2) {
            val shown = screen.days.takeLast(30)
            StatsCard {
                Column(
                    Modifier.padding(16.dp).semantics { contentDescription = "Screen time by day over ${shown.size} days" },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PhoneSectionTitle("Screen time by day")
                    DayBars(
                        points = toDayBarPoints(shown, screen.highest?.date),
                        contentDescription = "Bar chart of screen time for each of the last ${shown.size} days",
                    )
                    byDayCaption(screen.days.size)?.let { PhoneMutedNote(it) }
                    screen.avgScreenOnPerDayMs?.let { PhoneMutedNote("Average ${PhoneText.duration(it)}") }
                }
            }
        }

        // 4 · WHEN YOUR SCREEN IS ON
        StatsCard {
            Column(
                Modifier.padding(16.dp).semantics { contentDescription = "When your screen is on, by hour of the day" },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PhoneSectionTitle("When your screen is on")
                HourBars(
                    values = screen.screenOnByHourAvgMs.map { it.toFloat() },
                    contentDescription = "Bar chart of screen-on time for each hour of the day",
                    highlightIndex = screen.peakHour,
                )
                busiestCaption(screen.peakHour, window)?.let { PhoneMutedNote(it) }
            }
        }

        // 5 · WHERE THE TIME WENT
        StatsCard {
            Column(
                Modifier.padding(16.dp).semantics { contentDescription = "Where the time went, by app" },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PhoneSectionTitle("Where the time went")
                if (report.apps.totalMs == 0L) {
                    PhoneMutedNote("No app usage recorded for this period.")
                } else {
                    topAppRows(report.apps).forEach { row ->
                        ShareRow(row.title, row.subtitle, row.fraction, row.trailing)
                    }
                    PhoneMutedNote(appsFootnote(report.apps))
                }
            }
        }

        // 6 · links
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenExtra, modifier = Modifier.weight(1f)) { Text("More detail") }
            OutlinedButton(onClick = onOpenInsights, modifier = Modifier.weight(1f)) { Text("Patterns & records") }
        }
    }
}

@Composable
private fun PhoneHero(hero: HeroText) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(hero.value, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(hero.caption, fontSize = 13.sp, color = DarkTextMuted)
    }
}
