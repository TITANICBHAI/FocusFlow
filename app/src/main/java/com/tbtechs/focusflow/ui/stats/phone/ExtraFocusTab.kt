package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.analytics.phone.PhoneReport
import com.tbtechs.focusflow.analytics.phone.PhoneText

/** Extra · Focus: focus time, how long sessions run, blocked attempts and how blocking trends. */
@Composable
internal fun ExtraFocusTab(report: PhoneReport, modifier: Modifier = Modifier) {
    val f = report.focus

    ExtraTabList(modifier) {
        // 1 · FOCUS
        item {
            ExtraCard("Focus", Modifier.semantics { contentDescription = "Focus: " + focusSummaryLine(f) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PhoneStatTile(PhoneText.duration(f.focusMs), "Focus time", Modifier.weight(1f))
                    PhoneStatTile(f.sessions.toString(), "Sessions", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PhoneStatTile(f.heldSessions.toString(), "Held", Modifier.weight(1f))
                    PhoneStatTile(f.interruptedSessions.toString(), "Interrupted", Modifier.weight(1f))
                }
                heldRatePercent(f)?.let { PhoneMutedNote("Held ${PhoneText.percent(it)} of focus sessions") }
                PhoneMutedNote("Held = a focus session with no overrides.")
            }
        }

        // 2 · SESSION LENGTH
        item {
            ExtraCard("Session length") {
                if (f.durationBucketCounts.all { it == 0 }) {
                    PhoneMutedNote("No finished focus sessions in this period.")
                } else {
                    BucketBars(
                        counts = f.durationBucketCounts,
                        labels = focusDurationLabels(),
                        contentDescription = "Number of focus sessions by length",
                    )
                }
            }
        }

        // 3 · BLOCKED ATTEMPTS
        item {
            ExtraCard("Blocked attempts") {
                if (f.blockedAttempts <= 0) {
                    PhoneMutedNote("No blocked attempts in this period.")
                } else {
                    PhoneHeroText(f.blockedAttempts.toString())
                    f.attemptsPerFocusHour?.let { PhoneMutedNote("${oneDecimal(it)} per focus hour") }
                    blockedAppRows(f).forEach { ShareRow(it.title, it.subtitle, it.fraction, it.trailing) }
                }
            }
        }

        // 4 · WHEN YOU TRY
        item {
            ExtraCard("When you try") {
                HourBars(
                    values = f.blockedByHour.map { it.toFloat() },
                    contentDescription = "Bar chart of blocked attempts for each hour of the day",
                    highlightIndex = f.blockedPeakHour,
                )
                f.blockedPeakHour?.let { PhoneMutedNote("Most attempts around ${PhoneText.hour(it)}") }
            }
        }

        // 5 · BLOCKING OVER TIME (always the last 8 weeks, whatever the window)
        item {
            ExtraCard("Blocking over time") {
                DayBars(
                    points = weekBarPoints(f),
                    contentDescription = "Bar chart of blocked attempts for each of the last 8 weeks",
                )
                PhoneMutedNote(focusTrendText(f))
            }
        }
    }
}
