package com.tbtechs.focusflow.ui.stats.phone

import android.app.Activity
import com.tbtechs.focusflow.ui.theme.scaledSp
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.analytics.phone.PhoneReport
import com.tbtechs.focusflow.analytics.phone.PhoneText
import com.tbtechs.focusflow.ui.stats.StatsBlue
import com.tbtechs.focusflow.ui.stats.StatsCard
import com.tbtechs.focusflow.ui.stats.StatsGreen
import com.tbtechs.focusflow.ui.theme.SunAmber

/**
 * What the Stats screen shows about phone habits: one compact card (three numbers and one chart), plus the
 * nudge invitation while nudges are off. Everything else lives in Extra · Habits.
 */
@Composable
internal fun PhoneStatsSection(
    phoneViewModel: PhoneStatsViewModel,
    window: String,
    onOpenHabits: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by phoneViewModel.report.collectAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val s = state) {
            PhoneReportUiState.PermissionNeeded -> Unit // the "Usage access" card already explains this
            is PhoneReportUiState.Ready -> PhoneHabitsCard(s.report, window, onOpenHabits)
            else -> PhoneStateMessage(s, {})
        }
        NudgeCard(promoOnly = true)
    }
}

@Composable
private fun PhoneHabitsCard(report: PhoneReport, window: String, onOpenHabits: () -> Unit) {
    val screen = report.screenTime
    val single = isSingleDay(window)
    val hero = screenTimeHero(screen, window)
    val unlocks = unlocksHero(report.behaviour, window)
    val context = LocalContext.current

    StatsCard {
        Column(
            Modifier.padding(16.dp).semantics { contentDescription = "Phone habits: screen time ${hero.value}" },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Phone habits", modifier = Modifier.weight(1f), fontSize = 16.scaledSp, fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = { shareText(context, shareSummaryText(report, window)) }) { Text("Share") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhoneHabitTile(hero.value, if (single) "Screen on" else "Screen on a day", StatsBlue, Modifier.weight(1f))
                if (unlocks != null) {
                    PhoneHabitTile(unlocks.value, shortUnlockLabel(unlocks, single), StatsGreen, Modifier.weight(1f))
                }
                screen.peakHour?.let {
                    PhoneHabitTile(PhoneText.hour(it), "Busiest hour", SunAmber, Modifier.weight(1f))
                }
            }
            if (single) {
                HourBars(
                    values = screen.screenOnByHourAvgMs.map { it.toFloat() },
                    contentDescription = "Bar chart of screen-on time for each hour of the day",
                    highlightIndex = screen.peakHour,
                    height = 56.dp,
                )
            } else if (screen.days.size >= 2) {
                val shown = screen.days.takeLast(30)
                DayBars(
                    points = toDayBarPoints(shown, screen.highest?.date),
                    contentDescription = "Bar chart of screen time for each of the last ${shown.size} days",
                    height = 56.dp,
                )
            }
            TextButton(onClick = onOpenHabits, modifier = Modifier.fillMaxWidth()) { Text("See your habits  →") }
        }
    }
}

/** "unlocks" / "unlocks a day" / "wake-ups" → a label short enough for a third of the card. */
private fun shortUnlockLabel(hero: HeroText, single: Boolean): String = when {
    hero.caption.contains("wake") -> if (single) "Wake-ups" else "Wake-ups a day"
    else -> if (single) "Unlocks" else "Unlocks a day"
}

/** Opens the system share sheet with a plain-text summary. Nothing is sent until the person picks an app. */
private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, "Share summary")
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        // No app can take plain text; nothing to do.
    }
}
