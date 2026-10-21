package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.images.ImageBounds
import com.tbtechs.focusflow.data.images.ImageCodec
import com.tbtechs.focusflow.data.images.SubtaskImageStore
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.SubtaskImage
import com.tbtechs.focusflow.data.model.Task
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupBundleWriterTest {
    @Test
    fun writesDeterministicBundleWithStoredImageBytesUnchanged() {
        val directory = Files.createTempDirectory("bundle-writer").toFile()
        try {
            val store = SubtaskImageStore(directory, FixedWebpCodec())
            val assetId = store.ingest(png()).getOrThrow()
            val imageBytes = store.openForExport(assetId)!!
            val task = task().copy(
                subtasks = listOf(
                    Subtask(
                        id = "part",
                        title = "Part",
                        image = SubtaskImage(assetId),
                    ),
                ),
            )
            val manifest = BackupSerializer.serializeToJson(
                BackupSerializer.buildEnvelope(
                    settings = AppSettings(),
                    tasks = listOf(task),
                    appVersion = "test",
                    assets = mapOf(
                        assetId to BackupAsset(
                            mime = "image/webp",
                            file = "assets/$assetId.webp",
                            sha256 = assetId,
                        ),
                    ),
                ),
            )
            val writer = BackupBundleWriter(store)
            val prepared = writer.prepare(manifest, setOf(assetId)) as BundleExportPreparation.Ready

            val first = ByteArrayOutputStream()
            val second = ByteArrayOutputStream()
            writer.write(prepared.plan, first)
            writer.write(prepared.plan, second)

            assertArrayEquals(first.toByteArray(), second.toByteArray())
            val entries = readZip(first.toByteArray())
            assertEquals(listOf("manifest.json", "assets/$assetId.webp"), entries.keys.toList())
            assertArrayEquals(imageBytes, entries.getValue("assets/$assetId.webp"))
            assertTrue(entries.getValue("manifest.json").toString(Charsets.UTF_8).contains("\"version\": 2"))

            val cache = Files.createTempDirectory("bundle-round-trip").toFile()
            val source = BackupSource.readStream(ByteArrayInputStream(first.toByteArray()), cache)
            try {
                val parsed = BackupSerializer.parseAndValidate(
                    jsonText = source.manifestJson,
                    isBundle = source.isBundle,
                    bundleAssetIds = source.bundleAssetIds,
                    sourceWarnings = source.warnings,
                ) as BackupParseResult.Success
                assertTrue(parsed.warnings.isEmpty())
                assertArrayEquals(imageBytes, source.assetByteSource.open(assetId))
                assertEquals(assetId, parsed.envelope.assets.getValue(assetId).sha256)
            } finally {
                source.close()
                cache.deleteRecursively()
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun oversizedStoredImageReturnsTooLargeWithoutReencodingOrOmitting() {
        val directory = Files.createTempDirectory("bundle-too-large").toFile()
        try {
            val store = SubtaskImageStore(directory, FixedWebpCodec(large = true))
            val assetId = store.ingest(png()).getOrThrow()
            val writer = BackupBundleWriter(store)

            val result = writer.prepare(
                manifestJson = "{}",
                assetIds = setOf(assetId),
            )

            assertTrue(result is BundleExportPreparation.TooLarge)
            assertTrue(
                store.file(assetId)!!.length() >
                    BackupJsonLimits.MAX_BUNDLE_ASSET_ENTRY_BYTES,
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun readZip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes()
            }
        }
        return result
    }

    private fun png() = byteArrayOf(
        0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1,
    )

    private fun task() = Task(
        id = "bundle-task",
        title = "Bundle task",
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

    private class FixedWebpCodec(
        private val large: Boolean = false,
    ) : ImageCodec {
        override fun bounds(bytes: ByteArray) = ImageBounds(8, 8)

        override fun reencode(bytes: ByteArray, maxLongEdge: Int, quality: Int): ByteArray {
            val size = if (large) BackupJsonLimits.MAX_BUNDLE_ASSET_ENTRY_BYTES + 1 else 32
            return ByteArray(size).also { output ->
                output[0] = 0x52
                output[1] = 0x49
                output[2] = 0x46
                output[3] = 0x46
                output[8] = 0x57
                output[9] = 0x45
                output[10] = 0x42
                output[11] = 0x50
                output[12] = bytes.last()
            }
        }
    }
}
