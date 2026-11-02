package com.tbtechs.focusflow.data.images

import com.tbtechs.focusflow.data.model.DownloadedPhoto
import com.tbtechs.focusflow.data.model.PhotoSlotRef

/**
 * Downloads the pictures behind a batch of photo slots, one after another, and stores each one
 * with [SubtaskImageStore.ingest] (which decodes and re-encodes it as WebP). It does not change any
 * task: the caller attaches [Outcome.downloads] afterwards with `SubtaskPhotoSlots.applyDownloads`.
 */
class PhotoDownloadCoordinator(
    private val downloader: PhotoDownloader,
    private val store: SubtaskImageStore,
) {
    data class Outcome(
        val downloads: List<DownloadedPhoto>,
        val failures: Map<PhotoDownloadFailure, Int>,
        val notAttempted: Int,
    ) {
        val succeeded: Int get() = downloads.size
        val failed: Int get() = failures.values.sum()
    }

    /** Blocking. Call it from a background thread. */
    fun run(
        slots: List<PhotoSlotRef>,
        isCancelled: () -> Boolean = { false },
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Outcome {
        val withLinks = slots.filter { it.slot.url != null }
        val batch = withLinks.take(MAX_PER_BATCH)
        val assetByUrl = mutableMapOf<String, String>()
        val failureByUrl = mutableMapOf<String, PhotoDownloadFailure>()
        val downloads = mutableListOf<DownloadedPhoto>()
        val failures = linkedMapOf<PhotoDownloadFailure, Int>()
        var attempted = 0
        for (ref in batch) {
            if (isCancelled()) break
            val url = ref.slot.url ?: continue
            attempted++
            if (url !in assetByUrl && url !in failureByUrl) {
                when (val result = downloader.download(url, isCancelled)) {
                    is PhotoDownloadResult.Failure -> {
                        if (result.reason == PhotoDownloadFailure.CANCELLED) {
                            attempted--
                            break
                        }
                        failureByUrl[url] = result.reason
                    }
                    is PhotoDownloadResult.Success -> {
                        val stored = try {
                            store.ingest(result.bytes)
                        } finally {
                            result.bytes.fill(0)
                        }
                        stored.onSuccess { assetByUrl[url] = it }
                            .onFailure { failureByUrl[url] = PhotoDownloadFailure.UNREADABLE }
                    }
                }
            }
            val assetId = assetByUrl[url]
            if (assetId != null) {
                downloads += DownloadedPhoto(ref.subtaskId, ref.stepIndex, url, assetId)
            } else {
                val reason = failureByUrl[url] ?: PhotoDownloadFailure.NETWORK_ERROR
                failures[reason] = (failures[reason] ?: 0) + 1
            }
            onProgress(attempted, batch.size)
        }
        return Outcome(
            downloads = downloads,
            failures = failures,
            notAttempted = withLinks.size - attempted,
        )
    }

    companion object {
        /** Keeps one tap bounded. The user can tap again for the rest. */
        const val MAX_PER_BATCH = 30
    }
}
