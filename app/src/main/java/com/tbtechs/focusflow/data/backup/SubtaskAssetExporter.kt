package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.images.SubtaskImageStore
import com.tbtechs.focusflow.data.model.Task
import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class SubtaskAssetExportResult(
    val json: String,
    val warnings: List<String>,
    val imageCount: Int,
)

internal class SubtaskAssetExporter(
    private val imageStore: SubtaskImageStore,
) {
    fun export(
        tasks: List<Task>,
        buildEnvelope: (List<Task>, Map<String, BackupAsset>) -> BackupEnvelope,
    ): SubtaskAssetExportResult {
        var candidate = collect(tasks, reencode = false)
        var envelope = buildEnvelope(candidate.tasks, candidate.assets)
        var serialized = BackupSerializer.serializeToJson(envelope)
        var omittedForSize = candidate.omittedForSize

        if (serialized.utf8Size() > BackupJsonLimits.EXPORT_REENCODE_THRESHOLD_BYTES) {
            candidate = collect(tasks, reencode = true)
            omittedForSize = candidate.omittedForSize
            envelope = buildEnvelope(candidate.tasks, candidate.assets)
            serialized = BackupSerializer.serializeToJson(envelope)
        }

        if (serialized.utf8Size() > BackupJsonLimits.MAX_FILE_BYTES) {
            val remaining = candidate.assets.entries
                .sortedByDescending { it.value.data.length }
                .map { it.key }
            var tasksWithoutImages = candidate.tasks
            val assetsWithoutLargeImages = candidate.assets.toMutableMap()
            for (assetId in remaining) {
                assetsWithoutLargeImages.remove(assetId)
                tasksWithoutImages = withoutAsset(tasksWithoutImages, assetId)
                omittedForSize++
                envelope = buildEnvelope(tasksWithoutImages, assetsWithoutLargeImages)
                serialized = BackupSerializer.serializeToJson(envelope)
                if (serialized.utf8Size() <= BackupJsonLimits.MAX_FILE_BYTES) break
            }
        }
        require(serialized.utf8Size() <= BackupJsonLimits.MAX_FILE_BYTES) {
            "Backup exceeds the 8 MiB file limit even after omitting images."
        }

        val warnings = buildList {
            if (candidate.missingCount > 0) {
                add("${candidate.missingCount} images could not be found and were omitted from the backup.")
            }
            if (omittedForSize > 0) {
                add("$omittedForSize images were left out to keep the file under 8 MiB.")
            }
        }
        return SubtaskAssetExportResult(
            json = serialized,
            warnings = warnings,
            imageCount = envelope.assets.size,
        )
    }

    private fun collect(tasks: List<Task>, reencode: Boolean): Candidate {
        val assets = linkedMapOf<String, BackupAsset>()
        val validIds = mutableSetOf<String>()
        var missingCount = 0
        var omittedForSize = 0
        var totalBytes = 0
        val referenced = SubtaskImageStore.referencedAssetIds(tasks).sorted()
        for (assetId in referenced) {
            if (assets.size >= BackupJsonLimits.MAX_ASSETS) {
                omittedForSize++
                continue
            }
            var bytes = if (reencode) {
                imageStore.reencodeForExport(assetId, EXPORT_MAX_EDGE, EXPORT_QUALITY)
            } else {
                imageStore.openForExport(assetId)
            }
            if (bytes == null) {
                missingCount++
                continue
            }
            var data = Base64.getEncoder().withoutPadding().encodeToString(bytes)
            if (data.length > BackupJsonLimits.MAX_ASSET_DATA_CHARS && !reencode) {
                bytes = imageStore.reencodeForExport(assetId, EXPORT_MAX_EDGE, EXPORT_QUALITY)
                data = bytes?.let { Base64.getEncoder().withoutPadding().encodeToString(it) }.orEmpty()
            }
            if (bytes == null || data.isEmpty() ||
                data.length > BackupJsonLimits.MAX_ASSET_DATA_CHARS ||
                bytes.size > BackupJsonLimits.MAX_ASSET_TOTAL_BYTES - totalBytes
            ) {
                omittedForSize++
                continue
            }
            totalBytes += bytes.size
            assets[assetId] = BackupAsset(mime = "image/webp", data = data)
            validIds += assetId
        }
        return Candidate(
            tasks = keepOnlyAssets(tasks, validIds),
            assets = assets,
            missingCount = missingCount,
            omittedForSize = omittedForSize,
        )
    }

    private fun keepOnlyAssets(tasks: List<Task>, ids: Set<String>): List<Task> =
        tasks.map { task ->
            task.copy(
                subtasks = task.subtasks.map { subtask ->
                    subtask.copy(
                        image = subtask.image?.takeIf { it.asset in ids },
                        steps = subtask.steps.map { step ->
                            step.copy(image = step.image?.takeIf { it.asset in ids })
                        },
                    )
                },
            )
        }

    private fun withoutAsset(tasks: List<Task>, assetId: String): List<Task> =
        tasks.map { task ->
            task.copy(
                subtasks = task.subtasks.map { subtask ->
                    subtask.copy(
                        image = subtask.image?.takeUnless { it.asset == assetId },
                        steps = subtask.steps.map { step ->
                            step.copy(image = step.image?.takeUnless { it.asset == assetId })
                        },
                    )
                },
            )
        }

    private fun String.utf8Size(): Int = toByteArray(StandardCharsets.UTF_8).size

    private data class Candidate(
        val tasks: List<Task>,
        val assets: Map<String, BackupAsset>,
        val missingCount: Int,
        val omittedForSize: Int,
    )

    private companion object {
        const val EXPORT_MAX_EDGE = 960
        const val EXPORT_QUALITY = 60
    }
}
