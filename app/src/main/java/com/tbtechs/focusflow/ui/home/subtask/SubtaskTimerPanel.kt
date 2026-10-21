package com.tbtechs.focusflow.ui.home.subtask

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
    if (restSeconds == null && durationSeconds == null && state.announcement.isBlank()) return

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
                    Text("Rest timer", color = BrandPrimary, fontSize = 14.scaledSp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Start after each set · ${formatTimer(restSeconds)}",
                        fontSize = 12.scaledSp,
                    )
                }
                Switch(
                    checked = restTimerEnabled,
                    onCheckedChange = onRestTimerEnabledChange,
                    modifier = Modifier.semantics { contentDescription = "Rest timer" },
                )
            }
        }

        if (durationSeconds != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Timed goal · ${formatTimer(durationSeconds)}", fontSize = 13.scaledSp)
                TextButton(
                    onClick = { onStartDurationTimer(durationSeconds) },
                    modifier = Modifier.semantics {
                        contentDescription = if (state.mode == SubtaskTimerMode.DURATION) {
                            "Restart duration timer"
                        } else {
                            "Start duration timer"
                        }
                    },
                ) {
                    Text(
                        if (state.mode == SubtaskTimerMode.DURATION) "Restart timer" else "Start timer",
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
                    Text(
                        "${mode.label} timer",
                        color = BrandPrimary,
                        fontSize = 12.scaledSp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        formatTimer(state.remainingSeconds),
                        color = BrandPrimary,
                        fontSize = 30.scaledSp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.semantics {
                            contentDescription =
                                "${mode.label} timer, ${formatSpokenTimer(state.remainingSeconds)} remaining"
                        },
                    )
                }
                TextButton(onClick = onStopTimer) { Text("Stop timer", fontSize = 13.scaledSp) }
            }
        }

        if (state.announcement.isNotBlank()) {
            Text(
                text = state.announcement,
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

private fun formatSpokenTimer(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val remainder = safeSeconds % 60
    return when {
        minutes > 0 && remainder > 0 -> "$minutes minutes and $remainder seconds"
        minutes > 0 -> "$minutes minutes"
        else -> "$remainder seconds"
    }
}
