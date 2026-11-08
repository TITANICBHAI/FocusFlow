package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tbtechs.focusflow.analytics.phone.PhoneReport
import com.tbtechs.focusflow.analytics.phone.PhoneText

/** Extra · Distraction: how much time goes to distracting apps, which, when, and what is opened reflexively. */
@Composable
internal fun ExtraDistractionTab(report: PhoneReport, modifier: Modifier = Modifier) {
    val d = report.distraction

    ExtraTabList(modifier) {
        // 1 · DISTRACTION TIME
        item {
            ExtraCard("Distraction time") {
                if (d.distractionMs <= 0L) {
                    PhoneMutedNote("No distraction time recorded for this period.")
                } else {
                    PhoneHeroText(distractionHeadline(d))
                    distractionChangeText(d)?.let { PhoneMutedNote(it) }
                }
                PhoneMutedNote("Counts social media, entertainment and games, plus apps you tried to open while blocked.")
            }
        }

        // 2 · MOST DISTRACTING APPS
        item {
            ExtraCard("Most distracting apps") {
                if (d.apps.isEmpty()) {
                    PhoneMutedNote("No distracting apps in this period.")
                } else {
                    d.apps.forEach { app ->
                        ShareRow(
                            title = app.appName,
                            subtitle = distractionSubtitle(app).ifEmpty { null },
                            fraction = (app.sharePercent / 100.0).toFloat().coerceIn(0f, 1f),
                            trailing = PhoneText.duration(app.totalMs),
                        )
                    }
                }
            }
        }

        // 3 · WHEN IT HAPPENS
        item {
            ExtraCard("When it happens") {
                HourBars(
                    values = d.byHourMs.map { it.toFloat() },
                    contentDescription = "Bar chart of distraction time for each hour of the day",
                    highlightIndex = d.peakHour,
                )
                d.peakHour?.let { PhoneMutedNote("Most distraction time around ${PhoneText.hour(it)}") }
            }
        }

        // 4 · OPENED AGAIN AND AGAIN
        item {
            ExtraCard("Opened again and again") {
                if (d.reflexApps.isEmpty()) {
                    PhoneMutedNote("No apps are opened that often.")
                } else {
                    d.reflexApps.forEach { PhoneListRow(it.appName, reflexSubtitle(it)) }
                }
            }
        }

        // 5 · TAKING MORE TIME THAN USUAL
        if (d.risingApps.isNotEmpty()) {
            item {
                ExtraCard("Taking more time than usual") {
                    d.risingApps.forEach { PhoneListRow(it.appName, risingSubtitle(it)) }
                }
            }
        }
    }
}
