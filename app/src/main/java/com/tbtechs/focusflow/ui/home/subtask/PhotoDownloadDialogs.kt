package com.tbtechs.focusflow.ui.home.subtask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.images.PhotoDownloadCoordinator
import com.tbtechs.focusflow.ui.theme.scaledSp

/** The three dialogs of "Download pictures": consent, progress and result. */
@Composable
internal fun PhotoDownloadDialogs(controller: PhotoDownloadController) {
    when (val state = controller.state) {
        PhotoDownloadUiState.Idle -> Unit
        is PhotoDownloadUiState.Confirm -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text("Download pictures?", fontSize = 20.scaledSp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val pictures = "${state.linkCount} ${if (state.linkCount == 1) "picture" else "pictures"}"
                    if (state.hosts.isEmpty()) {
                        Text(
                            text = "FocusFlow will connect to the websites in the file to download $pictures.",
                            fontSize = 14.scaledSp,
                        )
                    } else {
                        Text(
                            text = "FocusFlow will connect to " +
                                (if (state.hosts.size == 1) "this website" else "these ${state.hosts.size} websites") +
                                " to download $pictures:",
                            fontSize = 14.scaledSp,
                        )
                        // Every distinct host is listed (never truncated), so the person can see
                        // exactly where their network address will be sent.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 132.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            state.hosts.forEach { host ->
                                Text(text = "• $host", fontSize = 13.scaledSp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Text(
                        text = "Links are never opened in a browser. Each download must be a JPEG, PNG or " +
                            "WebP picture of at most 6 MiB. FocusFlow stores its own copy privately on " +
                            "this device. The websites can see your device's network address.",
                        fontSize = 13.scaledSp,
                    )
                    if (state.linkCount > PhotoDownloadCoordinator.MAX_PER_BATCH) {
                        Text(
                            text = "Only the first ${PhotoDownloadCoordinator.MAX_PER_BATCH} are downloaded now. " +
                                "Tap Download again for the rest.",
                            fontSize = 13.scaledSp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = controller::confirm) { Text("Download", fontSize = 14.scaledSp) }
            },
            dismissButton = {
                TextButton(onClick = controller::dismiss) { Text("Cancel", fontSize = 14.scaledSp) }
            },
        )
        is PhotoDownloadUiState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading…", fontSize = 20.scaledSp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${state.done} of ${state.total}", fontSize = 13.scaledSp)
                }
            },
            confirmButton = {
                TextButton(onClick = controller::stop) { Text("Stop", fontSize = 14.scaledSp) }
            },
        )
        is PhotoDownloadUiState.Finished -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = {
                Text(
                    text = if (state.stopped) "Stopped" else "Download finished",
                    fontSize = 20.scaledSp,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${state.saved} ${if (state.saved == 1) "picture" else "pictures"} saved.",
                        fontSize = 14.scaledSp,
                    )
                    state.failures.forEach { (reason, count) ->
                        Text("$count × ${reason.userMessage}", fontSize = 13.scaledSp)
                    }
                    if (state.notAttempted > 0) {
                        Text(
                            text = "${state.notAttempted} more links were not tried. Tap Download again.",
                            fontSize = 13.scaledSp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = controller::dismiss) { Text("OK", fontSize = 14.scaledSp) }
            },
        )
    }
}
