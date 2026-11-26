package com.tbtechs.focusflow.ui.home.subtask

import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.model.SubtaskGoal
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.scaledSp

@Composable
internal fun SubtaskTimerPanel(
    goal: SubtaskGoal,
    state: SubtaskTimerState,
    restTimerEnabled: Boolean,
    onRestTimerEnabledChange: (Boolean) -> Unit,
    onStartDurationTimer: (Int) -> Unit,
    onStopTimer: () -> Unit,
) {
    val restSeconds = goal.restSeconds?.takeIf { goal.kind == "sets" && it > 0 }
    val durationSeconds = goal.seconds?.takeIf { it > 0 }
        ?.takeIf { goal.kind == "duration" || goal.kind == "sets" }
    if (restSeconds == null && durationSeconds == null && state.announcement == null) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (restSeconds != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_rest_timer), color = BrandPrimary, fontSize = 14.scaledSp, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.home_start_after_each_set, formatTimer(restSeconds)),
                        fontSize = 12.scaledSp,
                    )
                }
                val a11yDescription0 = stringResource(R.string.home_rest_timer)
                Switch(
                    checked = restTimerEnabled,
                    onCheckedChange = onRestTimerEnabledChange,
                    modifier = Modifier.semantics { contentDescription = a11yDescription0 },
                )
            }
        }

        if (durationSeconds != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.home_timed_goal, formatTimer(durationSeconds)), fontSize = 13.scaledSp)
                val durationButtonDescription = stringResource(
                    if (state.mode == SubtaskTimerMode.DURATION) R.string.home_restart_duration_timer
                    else R.string.home_start_duration_timer,
                )
                TextButton(
                    onClick = { onStartDurationTimer(durationSeconds) },
                    modifier = Modifier.semantics { contentDescription = durationButtonDescription },
                ) {
                    Text(
                        stringResource(if (state.mode == SubtaskTimerMode.DURATION) R.string.home_restart_timer else R.string.home_start_timer),
                        fontSize = 13.scaledSp,
                    )
                }
            }
        }

        state.mode?.let { mode ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val modeTitle = timerTitle(mode)
                    Text(
                        modeTitle,
                        color = BrandPrimary,
                        fontSize = 12.scaledSp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val remainingDescription = stringResource(
                        R.string.home_timer_remaining,
                        modeTitle,
                        formatSpokenTimer(state.remainingSeconds),
                    )
                    Text(
                        formatTimer(state.remainingSeconds),
                        color = BrandPrimary,
                        fontSize = 30.scaledSp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.semantics { contentDescription = remainingDescription },
                    )
                }
                TextButton(onClick = onStopTimer) { Text(stringResource(R.string.home_stop_timer), fontSize = 13.scaledSp) }
            }
        }

        state.announcement?.let { announcement ->
            Text(
                text = announcementText(announcement),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                fontSize = 12.scaledSp,
            )
        }
    }
}

internal fun formatTimer(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val remainder = safeSeconds % 60
    return if (minutes > 0) "$minutes:${remainder.toString().padStart(2, '0')}" else "${remainder}s"
}

@Composable
private fun timerTitle(mode: SubtaskTimerMode): String = stringResource(
    if (mode == SubtaskTimerMode.REST) R.string.home_rest_timer else R.string.home_duration_timer,
)

@Composable
private fun announcementText(announcement: SubtaskTimerAnnouncement): String = stringResource(
    when (announcement.mode) {
        SubtaskTimerMode.REST -> when (announcement.event) {
            SubtaskTimerEvent.STARTED -> R.string.home_rest_timer_started
            SubtaskTimerEvent.FINISHED -> R.string.home_rest_timer_finished
            SubtaskTimerEvent.STOPPED -> R.string.home_rest_timer_stopped
        }
        SubtaskTimerMode.DURATION -> when (announcement.event) {
            SubtaskTimerEvent.STARTED -> R.string.home_duration_timer_started
            SubtaskTimerEvent.FINISHED -> R.string.home_duration_timer_finished
            SubtaskTimerEvent.STOPPED -> R.string.home_duration_timer_stopped
        }
    },
)

@Composable
private fun formatSpokenTimer(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val remainder = safeSeconds % 60
    return when {
        minutes > 0 && remainder > 0 -> stringResource(R.string.home_spoken_minutes_seconds, minutes, remainder)
        minutes > 0 -> stringResource(R.string.home_spoken_minutes, minutes)
        else -> stringResource(R.string.home_spoken_seconds, remainder)
    }
}
