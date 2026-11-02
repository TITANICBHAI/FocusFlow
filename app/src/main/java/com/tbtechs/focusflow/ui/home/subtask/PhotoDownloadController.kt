package com.tbtechs.focusflow.ui.home.subtask

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.tbtechs.focusflow.data.images.PhotoDownloadCoordinator
import com.tbtechs.focusflow.data.images.PhotoDownloadFailure
import com.tbtechs.focusflow.data.images.PhotoDownloader
import com.tbtechs.focusflow.data.images.SubtaskImageStore
import com.tbtechs.focusflow.data.model.DownloadedPhoto
import com.tbtechs.focusflow.data.model.PhotoLinkCheck
import com.tbtechs.focusflow.data.model.PhotoLinkPolicy
import com.tbtechs.focusflow.data.model.PhotoSlotRef
import com.tbtechs.focusflow.data.model.SubtaskPhotoSlots
import com.tbtechs.focusflow.data.model.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface PhotoDownloadUiState {
    data object Idle : PhotoDownloadUiState

    /** Waiting for the person to agree. Nothing has touched the network yet. */
    data class Confirm(
        val taskId: String,
        val slots: List<PhotoSlotRef>,
        val hosts: List<String>,
        val linkCount: Int,
    ) : PhotoDownloadUiState

    data class Running(val done: Int, val total: Int) : PhotoDownloadUiState

    data class Finished(
        val saved: Int,
        val failures: Map<PhotoDownloadFailure, Int>,
        val notAttempted: Int,
        val stopped: Boolean,
    ) : PhotoDownloadUiState
}

/**
 * Drives "Download pictures". It never downloads anything until [confirm] is called after the
 * person saw the consent dialog. Downloads are attached through [onAttach], which must apply them
 * to the subtasks stored right now (TaskViewModel.attachDownloadedPhotos does).
 */
internal class PhotoDownloadController(
    private val scope: CoroutineScope,
    private val coordinator: PhotoDownloadCoordinator?,
    private val onAttach: (taskId: String, downloads: List<DownloadedPhoto>) -> Unit,
) {
    var state: PhotoDownloadUiState by mutableStateOf(PhotoDownloadUiState.Idle)
        private set

    @Volatile
    private var stopRequested = false

    /** Step 1: collect the slots that have a link and ask for consent. */
    fun requestDownload(task: Task) {
        if (coordinator == null || state is PhotoDownloadUiState.Running) return
        val slots = SubtaskPhotoSlots.collect(task.subtasks).filter { it.slot.url != null }
        if (slots.isEmpty()) return
        val hosts = slots
            .mapNotNull { (PhotoLinkPolicy.check(it.slot.url) as? PhotoLinkCheck.Accepted)?.uri?.host }
            .distinct()
        state = PhotoDownloadUiState.Confirm(
            taskId = task.id,
            slots = slots,
            hosts = hosts,
            linkCount = slots.mapNotNull { it.slot.url }.distinct().size,
        )
    }

    /** Step 2: the person tapped Download in the consent dialog. */
    fun confirm() {
        val pending = state as? PhotoDownloadUiState.Confirm ?: return
        val active = coordinator ?: return
        stopRequested = false
        state = PhotoDownloadUiState.Running(
            done = 0,
            total = pending.slots.size.coerceAtMost(PhotoDownloadCoordinator.MAX_PER_BATCH),
        )
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                active.run(
                    slots = pending.slots,
                    isCancelled = { stopRequested || !isActive },
                    onProgress = { done, total ->
                        state = PhotoDownloadUiState.Running(done, total)
                    },
                )
            }
            if (outcome.downloads.isNotEmpty()) onAttach(pending.taskId, outcome.downloads)
            state = PhotoDownloadUiState.Finished(
                saved = outcome.succeeded,
                failures = outcome.failures,
                notAttempted = outcome.notAttempted,
                stopped = stopRequested,
            )
        }
    }

    /** The Stop button: the batch ends at the next check; pictures saved so far are kept. */
    fun stop() {
        stopRequested = true
    }

    fun dismiss() {
        if (state !is PhotoDownloadUiState.Running) state = PhotoDownloadUiState.Idle
    }
}

@Composable
internal fun rememberPhotoDownloadController(
    taskId: String,
    imageStore: SubtaskImageStore?,
    downloader: PhotoDownloader?,
    onAttach: (String, List<DownloadedPhoto>) -> Unit,
): PhotoDownloadController {
    val scope = rememberCoroutineScope()
    val latestAttach = rememberUpdatedState(onAttach)
    val coordinator = remember(imageStore, downloader) {
        if (imageStore != null && downloader != null) {
            PhotoDownloadCoordinator(downloader, imageStore)
        } else {
            null
        }
    }
    return remember(taskId, coordinator) {
        PhotoDownloadController(scope, coordinator) { id, downloads ->
            latestAttach.value(id, downloads)
        }
    }
}
