package com.tbtechs.focusflow.ui.home.subtask

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.images.PhotoDownloadFailure
import com.tbtechs.focusflow.data.model.SubtaskPhotoSlots
import com.tbtechs.focusflow.ui.theme.scaledSp

/**
 * Shown on a task while some of its items or steps still say "Photo needed".
 *
 * It is a collapsible notice: a one line header that always says what is going on, and a body
 * with the shortcuts. It starts open only when a download can still help, so a picture that can
 * never be downloaded no longer keeps a big card on screen. "Stop asking" removes the empty
 * placeholders for good. It never starts a download by itself.
 */
@Composable
internal fun PhotoSlotsBanner(
    taskId: String,
    summary: SubtaskPhotoSlots.Summary,
    lastFailures: Map<PhotoDownloadFailure, Int>,
    canDownload: Boolean,
    canAddInOrder: Boolean,
    onDownload: () -> Unit,
    onAddInOrder: () -> Unit,
    onStopAsking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(taskId) {
        mutableStateOf(summary.downloadableLinks > 0 && lastFailures.isEmpty())
    }
    var confirmStop by remember { mutableStateOf(false) }
    val failedCount = lastFailures.values.sum()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (summary.total == 1) "1 picture missing" else "${summary.total} pictures missing",
                        fontSize = 15.scaledSp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = headerLine(summary, failedCount),
                        fontSize = 12.scaledSp,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "Hide details" else "Show details",
                )
            }
            if (expanded) {
                if (failedCount > 0) {
                    lastFailures.forEach { (reason, count) ->
                        Text("$count × ${reason.userMessage}", fontSize = 13.scaledSp)
                    }
                }
                if (summary.brokenLinkSlots > 0) {
                    Text(
                        text = "${summary.brokenLinkSlots} " +
                            (if (summary.brokenLinkSlots == 1) "link is" else "links are") +
                            " not a plain https picture link and cannot be downloaded.",
                        fontSize = 13.scaledSp,
                    )
                }
                if (summary.noLinkSlots > 0) {
                    Text(
                        text = "${summary.noLinkSlots} " +
                            (if (summary.noLinkSlots == 1) "picture has" else "pictures have") +
                            " no link. Add " + (if (summary.noLinkSlots == 1) "it" else "them") +
                            " yourself from the item's photo menu.",
                        fontSize = 13.scaledSp,
                    )
                }
                if (summary.downloadableLinks > 0 && canDownload) {
                    OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        val label = when {
                            failedCount > 0 -> "Try the links again"
                            summary.downloadableLinks == 1 -> "Download 1 picture from its link"
                            else -> "Download ${summary.downloadableLinks} pictures from their links"
                        }
                        Text(text = label, fontSize = 13.scaledSp)
                    }
                }
                if (summary.total >= 2 && canAddInOrder) {
                    OutlinedButton(onClick = onAddInOrder, modifier = Modifier.fillMaxWidth()) {
                        Text("Add pictures from gallery", fontSize = 13.scaledSp)
                    }
                }
                TextButton(onClick = { confirmStop = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop asking for these pictures", fontSize = 13.scaledSp)
                }
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop asking for pictures?", fontSize = 20.scaledSp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "This removes the empty picture placeholders from this task. Item text, " +
                        "steps and pictures you already added stay as they are. You can still add " +
                        "a picture to any item later.",
                    fontSize = 14.scaledSp,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmStop = false; onStopAsking() }) {
                    Text("Remove", fontSize = 14.scaledSp)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text("Cancel", fontSize = 14.scaledSp) }
            },
        )
    }
}

private fun headerLine(summary: SubtaskPhotoSlots.Summary, failedCount: Int): String {
    val parts = mutableListOf<String>()
    if (failedCount > 0) parts += "$failedCount failed to download"
    if (summary.downloadableLinks > 0 && failedCount == 0) {
        parts += "${summary.downloadableLinks} can be downloaded"
    }
    if (summary.brokenLinkSlots > 0) parts += "${summary.brokenLinkSlots} with a broken link"
    if (summary.noLinkSlots > 0) parts += "${summary.noLinkSlots} with no link"
    return if (parts.isEmpty()) "Tap for options" else parts.joinToString(" · ")
}
