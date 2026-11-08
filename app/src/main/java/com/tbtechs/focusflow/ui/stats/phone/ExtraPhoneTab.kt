package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.analytics.phone.PhoneBuckets
import com.tbtechs.focusflow.analytics.phone.PhoneReport
import com.tbtechs.focusflow.analytics.phone.PhoneText
import com.tbtechs.focusflow.ui.stats.StatsPeriodPill

/** Extra · Phone: unlocks by hour, session lengths, what you open first, first unlock of the day. */
@Composable
internal fun ExtraPhoneTab(report: PhoneReport, modifier: Modifier = Modifier) {
    val b = report.behaviour
    var wakesChosen by rememberSaveable { mutableStateOf(false) }
    val showWakes = wakesChosen || !b.unlockSignalAvailable

    ExtraTabList(modifier) {
        // 1 · UNLOCKS BY HOUR
        item {
            ExtraCard(if (showWakes) "Wake-ups by hour" else "Unlocks by hour") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (b.unlockSignalAvailable) {
                        StatsPeriodPill("Unlocks", selected = !showWakes, onClick = { wakesChosen = false })
                    }
                    StatsPeriodPill("Wake-ups", selected = showWakes, onClick = { wakesChosen = true })
                }
                val byHour = if (showWakes) b.wakesByHour else b.unlocksByHour
                HourBars(
                    values = byHour.map { it.toFloat() },
                    contentDescription = "Bar chart of ${if (showWakes) "screen wake-ups" else "unlocks"} for each hour of the day",
                    highlightIndex = busiestHour(byHour),
                )
                PhoneMutedNote(unlockHourCaption(b, showWakes))
                if (!b.unlockSignalAvailable) {
                    PhoneMutedNote("This phone doesn't report unlocks (is a screen lock set?), so wake-ups are shown instead.")
                } else if (showWakes) {
                    PhoneMutedNote("Wake-ups include the screen turning on for notifications.")
                }
            }
        }

        // 2 · HOW LONG YOU USE IT AT ONCE
        item {
            ExtraCard("How long you use it at once") {
                PhoneHeroText(sessionHeadline(b))
                if (b.sessions > 0) {
                    BucketBars(
                        counts = b.sessionBucketCounts,
                        labels = PhoneBuckets.LABELS,
                        contentDescription = "Number of phone sessions by length",
                    )
                    PhoneMutedNote("Longest session ${PhoneText.duration(b.longestSessionMs)}")
                }
                PhoneMutedNote(sessionBasisNote(b))
            }
        }

        // 3 · WHAT YOU OPEN FIRST (needs the unlock signal)
        if (b.unlockSignalAvailable) {
            item {
                ExtraCard("What you open first") {
                    val noApp = noAppRow(b)
                    if (b.firstApps.isEmpty() && noApp == null) {
                        PhoneMutedNote("No unlocks recorded for this period.")
                    } else {
                        b.firstApps.forEach { row ->
                            ShareRow(
                                title = row.appName,
                                subtitle = firstAppSubtitle(row),
                                fraction = (row.shareOfUnlocksPercent / 100.0).toFloat().coerceIn(0f, 1f),
                                trailing = PhoneText.percent(Math.round(row.shareOfUnlocksPercent).toInt()),
                            )
                        }
                        noApp?.let { ShareRow(it.title, it.subtitle, it.fraction, it.trailing) }
                        PhoneMutedNote("The first app opened within 15 seconds of unlocking.")
                    }
                }
            }
        }

        // 4 · FIRST UNLOCK OF THE DAY
        b.firstUnlockMedianMinuteOfDay?.let { minute ->
            item {
                ExtraCard("First unlock of the day") {
                    PhoneHeroText("Usually around ${PhoneText.clock(minute)}")
                }
            }
        }
    }
}
