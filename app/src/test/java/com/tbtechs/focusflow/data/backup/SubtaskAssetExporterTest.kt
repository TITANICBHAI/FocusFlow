package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.images.ImageBounds
import com.tbtechs.focusflow.data.images.ImageCodec
import com.tbtechs.focusflow.data.images.SubtaskImageStore
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.SubtaskImage
import com.tbtechs.focusflow.data.model.SubtaskStep
import com.tbtechs.focusflow.data.model.Task
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskAssetExporterTest {
    @Test
    fun exportThenImportPreservesSubtaskContentAndReferencedImage() {
        val directory = Files.createTempDirectory("subtask-round-trip").toFile()
        try {
            val store = SubtaskImageStore(directory, LargeWebpCodec())
            val assetId = store.ingest(png(7)).getOrThrow()
            val subtasks = listOf(
                Subtask(
                    id = "movement",
                    title = "Movement",
                    subtitle = "Upper body",
                    image = SubtaskImage(assetId, alt = "Overview"),
                    steps = listOf(
                        SubtaskStep(
                            title = "Start",
                            text = "Set your position.",
                            image = SubtaskImage(assetId, alt = "Starting position"),
                        ),
                    ),
                    tips = listOf("Move with control"),
                    avoid = listOf("Do not rush"),
                ),
            )
            val exporter = SubtaskAssetExporter(store)

            val exported = exporter.export(listOf(task(7).copy(subtasks = subtasks))) {
                exportTasks, assets ->
                BackupSerializer.buildEnvelope(
                    settings = AppSettings(),
                    tasks = exportTasks,
                    appVersion = "test",
                    assets = assets,
                )
            }

            val parsed = BackupSerializer.parseAndValidate(exported.json)
            assertTrue(parsed is BackupParseResult.Success)
            val success = parsed as BackupParseResult.Success
            val imported = success.envelope
            assertEquals(subtasks, imported.tasks.single().subtasks)
            assertEquals(setOf(assetId), SubtaskImageStore.referencedAssetIds(imported.tasks))
            assertEquals(setOf(assetId), imported.assets.keys)
            assertTrue(success.warnings.isEmpty())
            assertTrue(exported.warnings.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun reencodesThenDropsImagesWhenTheImporterSizeLimitWouldBeExceeded() {
        val directory = Files.createTempDirectory("subtask-export").toFile()
        try {
            val codec = LargeWebpCodec()
            val store = SubtaskImageStore(directory, codec)
            val tasks = (0 until 21).map { index ->
                val assetId = store.ingest(png(index)).getOrThrow()
                task(index).copy(
                    subtasks = listOf(
                        Subtask(
                            id = "item",
                            title = "Item $index",
                            image = SubtaskImage(assetId),
                        ),
                    ),
                )
            }
            val exporter = SubtaskAssetExporter(store)

            val result = exporter.export(tasks) { exportTasks, assets ->
                BackupSerializer.buildEnvelope(
                    settings = AppSettings(),
                    tasks = exportTasks,
                    appVersion = "test",
                    assets = assets,
                )
            }

            assertTrue(codec.reducedExportCalls > 0)
            assertTrue(result.json.toByteArray(Charsets.UTF_8).size <= BackupJsonLimits.MAX_FILE_BYTES)
            assertEquals(20, result.imageCount)
            assertEquals(1, result.warnings.size)
            assertTrue(result.warnings.any { it.contains("left out to keep the file under 8 MiB") })
            assertTrue(BackupSerializer.parseAndValidate(result.json) is BackupParseResult.Success)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun png(index: Int) = byteArrayOf(
        0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, index.toByte(),
    )

    private fun task(index: Int) = Task(
        id = "export-$index",
        title = "Session $index",
        startTime = "2026-10-21T10:00:00Z",
        endTime = "2026-10-21T10:30:00Z",
        durationMinutes = 30,
        status = "completed",
        priority = "medium",
        color = "#22c55e",
        focusMode = false,
        createdAt = "2026-10-20T10:00:00Z",
        updatedAt = "2026-10-20T10:30:00Z",
    )

    private class LargeWebpCodec : ImageCodec {
        var reducedExportCalls = 0

        override fun bounds(bytes: ByteArray) = ImageBounds(32, 32)

        override fun reencode(bytes: ByteArray, maxLongEdge: Int, quality: Int): ByteArray {
            if (quality == 60) reducedExportCalls++
            val image = ByteArray(299_400)
            image[0] = 0x52
            image[1] = 0x49
            image[2] = 0x46
            image[3] = 0x46
            image[8] = 0x57
            image[9] = 0x45
            image[10] = 0x42
            image[11] = 0x50
            image[12] = bytes.last()
            return image
        }
    }
}
