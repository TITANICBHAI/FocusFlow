package com.tbtechs.focusflow.ui.focus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tbtechs.focusflow.data.local.dao.RecentSessionSummaryRow
import com.tbtechs.focusflow.ui.home.FocusFlowModalCard
import com.tbtechs.focusflow.ui.home.FocusFlowPrimaryButton
import com.tbtechs.focusflow.ui.home.RefAmber
import com.tbtechs.focusflow.ui.home.RefGreen
import com.tbtechs.focusflow.ui.home.RefSecondary
import com.tbtechs.focusflow.ui.home.RefText
import com.tbtechs.focusflow.ui.theme.scaledSp
import java.time.Duration
import java.time.Instant

/** Shown after a completed focus session; caller controls visibility and retrieval. */
@Composable
fun SessionDebriefModal(
    session: RecentSessionSummaryRow?,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible || session == null) return

    val actualMinutes = runCatching {
        Duration.between(
            Instant.parse(session.startedAt),
            Instant.parse(session.endedAt),
        ).toMinutes().coerceAtLeast(0)
    }.getOrDefault(0)
    val plannedMinutes = session.plannedMinutes
    val delta = (plannedMinutes ?: actualMinutes.toInt()) - actualMinutes
    val timing = when {
        plannedMinutes == null -> ""
        delta >= 2 -> " You finished $delta minutes ahead of schedule."
        delta <= -2 -> " You ran ${-delta} minutes over the plan."
        else -> ""
    }
    val clean = session.overrideCount == 0
    val title = if (clean) "Clean session" else "Tough session"
    val insight = if (clean) {
        "Zero blocked-app attempts.$timing"
    } else {
        "${session.overrideCount} blocked-app attempt${
            if (session.overrideCount == 1) "" else "s"
        } during this session.$timing"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        FocusFlowModalCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            radius = 20.dp,
            contentPadding = 24.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (clean) Icons.Outlined.Security else Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = if (clean) RefGreen else RefAmber,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(40.dp),
                    )
                    Text(title, modifier = Modifier.weight(1f), color = RefText, fontSize = 18.scaledSp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Dismiss session debrief", tint = RefSecondary)
                    }
                }
                Text(
                    "SESSION DEBRIEF",
                    color = RefSecondary,
                    fontSize = 11.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.scaledSp,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    session.taskTitle ?: "Focus session",
                    color = RefText,
                    fontSize = 22.scaledSp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    insight,
                    color = RefSecondary,
                    fontSize = 13.scaledSp,
                    lineHeight = 20.scaledSp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FocusFlowPrimaryButton(
                    text = "Done",
                    onClick = onDismiss,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
    }
}