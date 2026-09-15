package com.tbtechs.focusflow.ui.focus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tbtechs.focusflow.data.local.dao.RecentSessionSummaryRow
import java.time.Duration
import java.time.Instant

/** Shown after a completed focus session; caller controls visibility and retrieval. */
@Composable
fun SessionDebriefModal(session: RecentSessionSummaryRow?, visible: Boolean, onDismiss: () -> Unit) {
    if (!visible || session == null) return
    val actualMinutes = runCatching { Duration.between(Instant.parse(session.startedAt), Instant.parse(session.endedAt)).toMinutes().coerceAtLeast(0) }.getOrDefault(0)
    val delta = (session.plannedMinutes ?: 0) - actualMinutes
    val timing = when {
        delta >= 2 -> " You finished $delta minutes ahead of schedule."
        delta <= -2 -> " You ran ${-delta} minutes over the plan."
        else -> ""
    }
    val clean = session.overrideCount == 0
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (clean) Icons.Outlined.Security else Icons.Outlined.Warning, null, tint = if (clean) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary) },
        title = { Row { Text(if (clean) "Clean session" else "Tough session"); IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Dismiss session debrief") } } },
        text = {
            Column {
                Text("SESSION DEBRIEF", style = MaterialTheme.typography.labelLarge)
                Text(session.taskTitle ?: "Focus session", style = MaterialTheme.typography.titleSmall)
                Text(if (clean) "Zero blocked-app attempts.$timing" else "${session.overrideCount} blocked-app attempt${if (session.overrideCount == 1) "" else "s"} during this session.$timing")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}
