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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.R
import com.tbtechs.focusflow.data.images.PhotoDownloadCoordinator
import com.tbtechs.focusflow.ui.theme.scaledSp

/** The three dialogs of "Download pictures": consent, progress and result. */
@Composable
internal fun PhotoDownloadDialogs(controller: PhotoDownloadController) {
    when (val state = controller.state) {
        PhotoDownloadUiState.Idle -> Unit
        is PhotoDownloadUiState.Confirm -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text(stringResource(R.string.photo_dl_title), fontSize = 20.scaledSp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val one = state.linkCount == 1
                    if (state.hosts.isEmpty()) {
                        Text(
                            text = if (one) stringResource(R.string.photo_dl_connect_file_one)
                            else stringResource(R.string.photo_dl_connect_file_many, state.linkCount),
                            fontSize = 14.scaledSp,
                        )
                    } else {
                        val oneHost = state.hosts.size == 1
                        Text(
                            text = when {
                                oneHost && one -> stringResource(R.string.photo_dl_connect_host_one_one)
                                oneHost -> stringResource(R.string.photo_dl_connect_host_one_many, state.linkCount)
                                one -> stringResource(R.string.photo_dl_connect_hosts_many_one, state.hosts.size)
                                else -> stringResource(R.string.photo_dl_connect_hosts_many_many, state.hosts.size, state.linkCount)
                            },
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
                        text = stringResource(R.string.photo_dl_privacy),
                        fontSize = 13.scaledSp,
                    )
                    if (state.linkCount > PhotoDownloadCoordinator.MAX_PER_BATCH) {
                        Text(
                            text = stringResource(R.string.photo_dl_batch_limit, PhotoDownloadCoordinator.MAX_PER_BATCH),
                            fontSize = 13.scaledSp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = controller::confirm) { Text(stringResource(R.string.photo_dl_confirm), fontSize = 14.scaledSp) }
            },
            dismissButton = {
                TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.photo_dl_cancel), fontSize = 14.scaledSp) }
            },
        )
        is PhotoDownloadUiState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.photo_dl_running), fontSize = 20.scaledSp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.photo_dl_progress, state.done, state.total), fontSize = 13.scaledSp)
                }
            },
            confirmButton = {
                TextButton(onClick = controller::stop) { Text(stringResource(R.string.photo_dl_stop), fontSize = 14.scaledSp) }
            },
        )
        is PhotoDownloadUiState.Finished -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = {
                Text(
                    text = stringResource(if (state.stopped) R.string.photo_dl_stopped else R.string.photo_dl_finished),
                    fontSize = 20.scaledSp,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (state.saved == 1) stringResource(R.string.photo_dl_saved_one)
                        else stringResource(R.string.photo_dl_saved_many, state.saved),
                        fontSize = 14.scaledSp,
                    )
                    state.failures.forEach { (reason, count) ->
                        Text(stringResource(R.string.photo_fail_count, count, stringResource(reason.messageRes)), fontSize = 13.scaledSp)
                    }
                    if (state.notAttempted > 0) {
                        Text(
                            text = if (state.notAttempted == 1) stringResource(R.string.photo_dl_not_tried_one)
                            else stringResource(R.string.photo_dl_not_tried_many, state.notAttempted),
                            fontSize = 13.scaledSp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.home_ok), fontSize = 14.scaledSp) }
            },
        )
    }
}
