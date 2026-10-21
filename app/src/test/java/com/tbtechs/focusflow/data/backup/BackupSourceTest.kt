package com.tbtechs.focusflow.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSourceTest {
    @Test
    fun readsBundleManifestAndLazilyOpensReferencedAsset() {
        val cache = Files.createTempDirectory("backup-source").toFile()
        val image = byteArrayOf(1, 2, 3, 4)
        val manifest = bundleManifest("photo", "assets/photo.webp")
        val document = BackupSource.readStream(
            ByteArrayInputStream(
                zipBytes(
                    "manifest.json" to manifest.toByteArray(),
                    "assets/photo.webp" to image,
                ),
            ),
            cache,
        )

        try {
            assertTrue(document.isBundle)
            assertEquals(manifest, document.manifestJson)
            assertEquals(setOf("photo"), document.bundleAssetIds)
            assertArrayEquals(image, document.assetByteSource.open("photo"))
        } finally {
            document.close()
            cache.deleteRecursively()
        }
    }

    @Test
    fun jsonWithBomAndWhitespaceUsesTheJsonPath() {
        val cache = Files.createTempDirectory("backup-json-source").toFile()
        val json = "\uFEFF \n{\"kind\":\"FocusFlowBackupV1\"}"
        val document = BackupSource.readStream(ByteArrayInputStream(json.toByteArray()), cache)

        try {
            assertFalse(document.isBundle)
            assertEquals(json, document.manifestJson)
            assertTrue(document.bundleAssetIds.isEmpty())
        } finally {
            document.close()
            cache.deleteRecursively()
        }
    }

    @Test
    fun bundleTemporaryCopyIsRemovedWhenDocumentCloses() {
        val cache = Files.createTempDirectory("backup-cleanup").toFile()
        val document = BackupSource.readStream(
            ByteArrayInputStream(zipBytes("manifest.json" to bundleManifest().toByteArray())),
            cache,
        )
        val stagedFiles = File(cache, "import_bundle").listFiles().orEmpty()
        assertEquals(1, stagedFiles.size)
        assertTrue(stagedFiles.single().isFile)

        document.close()

        assertTrue(File(cache, "import_bundle").listFiles().orEmpty().isEmpty())
        cache.deleteRecursively()
    }

    @Test
    fun startupSweepRemovesOnlyBundleCopiesOlderThanOneHour() {
        val cache = Files.createTempDirectory("backup-startup-sweep").toFile()
        val directory = File(cache, "import_bundle").apply { mkdirs() }
        val now = 10_000_000L
        val stale = File(directory, "stale.zip").apply {
            writeBytes(byteArrayOf(1))
            setLastModified(now - BackupJsonLimits.MAX_BUNDLE_TEMP_AGE_MILLIS - 1)
        }
        val recent = File(directory, "recent.zip").apply {
            writeBytes(byteArrayOf(2))
            setLastModified(now - BackupJsonLimits.MAX_BUNDLE_TEMP_AGE_MILLIS + 1)
        }
        val unrelated = File(cache, "unrelated.zip").apply { writeBytes(byteArrayOf(3)) }

        val removed = BackupSource.sweepStaleBundleFiles(cache, now)

        assertEquals(1, removed)
        assertFalse(stale.exists())
        assertTrue(recent.exists())
        assertTrue(unrelated.exists())
        cache.deleteRecursively()
    }

    @Test
    fun missingManifestAndMalformedZipFailAsInvalidFocusFlowFiles() {
        val cache = Files.createTempDirectory("backup-invalid-zip").toFile()
        try {
            val missingManifest = runCatching {
                BackupSource.readStream(
                    ByteArrayInputStream(zipBytes("assets/unused.webp" to byteArrayOf(1))),
                    cache,
                )
            }.exceptionOrNull()
            assertTrue(missingManifest is BackupSourceException)
            assertTrue(missingManifest?.message.orEmpty().contains("manifest.json"))

            val malformed = runCatching {
                BackupSource.readStream(ByteArrayInputStream(byteArrayOf(0x50, 0x4b, 0x03, 0x04, 1)), cache)
            }.exceptionOrNull()
            assertTrue(malformed is BackupSourceException)
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun emptyZipNestedZipAndDuplicateManifestAreRejected() {
        val cache = Files.createTempDirectory("backup-invalid-containers").toFile()
        try {
            val empty = zipBytes()
            val nested = zipBytes(
                "nested.focusflow" to zipBytes(
                    "manifest.json" to bundleManifest().toByteArray(),
                ),
            )
            val duplicateManifest = renameZipEntry(
                zipBytes(
                    "manifest.json" to bundleManifest().toByteArray(),
                    "assets/a.webp" to byteArrayOf(1),
                ),
                "assets/a.webp",
                "manifest.json",
            )

            listOf(empty, nested, duplicateManifest).forEach { bytes ->
                val failure = runCatching {
                    BackupSource.readStream(ByteArrayInputStream(bytes), cache)
                }.exceptionOrNull()
                assertTrue(failure is BackupSourceException)
            }
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun bundleEntryCountIsBoundedBeforeManifestOrAssetsAreRead() {
        val cache = Files.createTempDirectory("backup-entry-count").toFile()
        val entries = buildList {
            add("manifest.json" to bundleManifest().toByteArray())
            repeat(BackupJsonLimits.MAX_BUNDLE_ENTRIES) { index ->
                add("extra-$index.txt" to byteArrayOf(index.toByte()))
            }
        }
        val failure = runCatching {
            BackupSource.readStream(ByteArrayInputStream(zipBytes(*entries.toTypedArray())), cache)
        }.exceptionOrNull()

        assertTrue(failure is BackupSourceException)
        assertTrue(failure?.message.orEmpty().contains("too many entries"))
        cache.deleteRecursively()
    }

    @Test
    fun unsafeAndUnsupportedEntriesAreIgnoredAndReported() {
        val cache = Files.createTempDirectory("backup-unsafe-zip").toFile()
        val document = BackupSource.readStream(
            ByteArrayInputStream(
                zipBytes(
                    "manifest.json" to bundleManifest("safe", "assets/safe.webp").toByteArray(),
                    "assets/safe.webp" to byteArrayOf(7),
                    "../outside.webp" to byteArrayOf(8),
                    "assets/../escape.webp" to byteArrayOf(9),
                    "notes/readme.txt" to byteArrayOf(10),
                    "/assets/absolute.webp" to byteArrayOf(11),
                    "assets\\backslash.webp" to byteArrayOf(12),
                ),
            ),
            cache,
        )
        try {
            assertArrayEquals(byteArrayOf(7), document.assetByteSource.open("safe"))
            assertEquals(null, document.assetByteSource.open("unsafe"))
            assertTrue(document.warnings.isNotEmpty())
        } finally {
            document.close()
            cache.deleteRecursively()
        }
    }

    @Test
    fun unsupportedCompressionEntryIsIgnoredAndReported() {
        val cache = Files.createTempDirectory("backup-unsupported-method").toFile()
        val manifest = bundleManifest("photo", "assets/photo.webp")
        val bytes = setCentralDirectoryMethod(
            zipBytes(
                "manifest.json" to manifest.toByteArray(),
                "assets/photo.webp" to byteArrayOf(1, 2, 3),
            ),
            entryName = "assets/photo.webp",
            method = 99,
        )
        val document = BackupSource.readStream(ByteArrayInputStream(bytes), cache)
        try {
            assertEquals(null, document.assetByteSource.open("photo"))
            assertTrue(document.warnings.any { it.contains("ignored") })
        } finally {
            document.close()
            cache.deleteRecursively()
        }
    }

    @Test
    fun encryptedManifestIsRejectedAndEncryptedAssetsAreSkipped() {
        val cache = Files.createTempDirectory("backup-encrypted-entry").toFile()
        try {
            val manifest = bundleManifest("photo", "assets/photo.webp")
            val encryptedManifest = setGeneralPurposeFlags(
                zipBytes("manifest.json" to manifest.toByteArray()),
                "manifest.json",
                0x1,
            )
            val manifestFailure = runCatching {
                BackupSource.readStream(ByteArrayInputStream(encryptedManifest), cache)
            }.exceptionOrNull()
            assertTrue(manifestFailure is BackupSourceException)
            assertTrue(manifestFailure?.message.orEmpty().contains("encrypted"))

            val encryptedAsset = setGeneralPurposeFlags(
                zipBytes(
                    "manifest.json" to manifest.toByteArray(),
                    "assets/photo.webp" to byteArrayOf(1, 2, 3),
                ),
                "assets/photo.webp",
                0x1,
            )
            val document = BackupSource.readStream(
                ByteArrayInputStream(encryptedAsset),
                cache,
            )
            try {
                assertFalse(document.assetByteSource.contains("photo"))
                assertTrue(document.warnings.isNotEmpty())
            } finally {
                document.close()
            }
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun lyingCentralDirectorySizeCannotCauseAnUnboundedAssetRead() {
        val cache = Files.createTempDirectory("backup-lying-size").toFile()
        val manifest = bundleManifest("photo", "assets/photo.webp")
        val expanded = ByteArray(BackupJsonLimits.MAX_BUNDLE_ASSET_ENTRY_BYTES + 1) { 7 }
        val bytes = lieAboutUncompressedSize(
            zipBytes(
                "manifest.json" to manifest.toByteArray(),
                "assets/photo.webp" to expanded,
            ),
            entryName = "assets/photo.webp",
            declaredSize = 1,
        )
        val document = BackupSource.readStream(ByteArrayInputStream(bytes), cache)
        try {
            assertEquals(null, document.assetByteSource.open("photo"))
        } finally {
            document.close()
            cache.deleteRecursively()
        }
    }

    @Test
    fun assetsOverThePerEntryLimitAreUnavailable() {
        val cache = Files.createTempDirectory("backup-large-entry").toFile()
        val manifest = bundleManifest("large", "assets/large.webp")
        val oversizedImage = ByteArray(BackupJsonLimits.MAX_BUNDLE_ASSET_ENTRY_BYTES + 1) { 3 }
        val document = BackupSource.readStream(
            ByteArrayInputStream(
                zipBytes(
                    "manifest.json" to manifest.toByteArray(),
                    "assets/large.webp" to oversizedImage,
                ),
            ),
            cache,
        )
        try {
            assertEquals(null, document.assetByteSource.open("large"))
            assertTrue(document.warnings.isNotEmpty())
        } finally {
            document.close()
            cache.deleteRecursively()
        }
    }

    @Test
    fun totalUncompressedAssetReadsAreBoundedAcrossEntries() {
        val cache = Files.createTempDirectory("backup-total-uncompressed").toFile()
        val imageBytes = ByteArray(BackupJsonLimits.MAX_BUNDLE_ASSET_ENTRY_BYTES)
        val assets = (0 until 41).map { index ->
            "image-$index" to "assets/image-$index.webp"
        }
        val manifestAssets = assets.joinToString(",") { (id, file) ->
            """"$id":{"file":"$file"}"""
        }
        val manifest =
            """{"kind":"FocusFlowBackupV1","version":2,"settings":{},"tasks":[],"assets":{$manifestAssets}}"""
        val archive = zipBytes(
            *(
                listOf("manifest.json" to manifest.toByteArray()) +
                    assets.map { (_, path) -> path to imageBytes }
                ).toTypedArray(),
        )
        val document = BackupSource.readStream(ByteArrayInputStream(archive), cache)
        try {
            var readable = 0
            assets.forEach { (id, _) ->
                val data = document.assetByteSource.open(id)
                if (data != null) {
                    readable++
                    data.fill(0)
                }
            }
            assertTrue(readable in 1 until assets.size)
        } finally {
            document.close()
            cache.deleteRecursively()
        }
    }

    @Test
    fun bundleAssetWithMismatchedSha256IsUnavailable() {
        val cache = Files.createTempDirectory("backup-bad-hash").toFile()
        val manifest =
            """{"kind":"FocusFlowBackupV1","version":2,"settings":{},"tasks":[],"assets":{"photo":{"file":"assets/photo.webp","sha256":"${"0".repeat(64)}"}}}"""
        val document = BackupSource.readStream(
            ByteArrayInputStream(
                zipBytes(
                    "manifest.json" to manifest.toByteArray(),
                    "assets/photo.webp" to byteArrayOf(1, 2, 3),
                ),
            ),
            cache,
        )
        try {
            assertEquals(null, document.assetByteSource.open("photo"))
        } finally {
            document.close()
            cache.deleteRecursively()
        }
    }

    @Test
    fun bundleCopyStopsAtArchiveLimitWithoutTrustingDeclaredInputSize() {
        val cache = Files.createTempDirectory("backup-copy-cap").toFile()
        val prefix = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
        val endless = object : InputStream() {
            private var index = 0
            override fun read(): Int = if (index++ < BackupJsonLimits.MAX_BUNDLE_BYTES + 1) 1 else -1
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (index > BackupJsonLimits.MAX_BUNDLE_BYTES) return -1
                val count = minOf(length, BackupJsonLimits.MAX_BUNDLE_BYTES + 1 - index)
                bytes.fill(1, offset, offset + count)
                index += count
                return count
            }
        }
        val input = ByteArrayInputStream(prefix + byteArrayOf()) // initialize the ZIP signature
        val failure = runCatching {
            BackupSource.readStream(ConcatenatedInputStream(input, endless), cache)
        }.exceptionOrNull()

        assertTrue(failure is BackupSourceException)
        assertTrue(failure?.message.orEmpty().contains("64 MiB"))
        cache.deleteRecursively()
    }

    private fun bundleManifest(
        assetId: String? = null,
        file: String? = null,
    ): String {
        val assets = if (assetId == null || file == null) {
            ""
        } else {
            ""","assets":{"$assetId":{"file":"$file","mime":"image/webp"}}"""
        }
        return """{"kind":"FocusFlowBackupV1","version":2,"settings":{},"tasks":[]$assets}"""
    }

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun renameZipEntry(bytes: ByteArray, oldName: String, newName: String): ByteArray {
        require(oldName.toByteArray().size == newName.toByteArray().size)
        val source = oldName.toByteArray()
        val replacement = newName.toByteArray()
        val result = bytes.copyOf()
        var index = 0
        while (index <= result.size - source.size) {
            if (source.indices.all { result[index + it] == source[it] }) {
                replacement.copyInto(result, index)
                index += source.size
            } else {
                index++
            }
        }
        return result
    }

    private fun setCentralDirectoryMethod(
        bytes: ByteArray,
        entryName: String,
        method: Int,
    ): ByteArray {
        val result = bytes.copyOf()
        val name = entryName.toByteArray()
        var index = 0
        while (index <= result.size - 4) {
            if (result[index] == 0x50.toByte() &&
                result[index + 1] == 0x4b.toByte() &&
                result[index + 2] == 0x01.toByte() &&
                result[index + 3] == 0x02.toByte()
            ) {
                val nameLength = unsignedShort(result, index + 28)
                if (nameLength == name.size &&
                    name.indices.all { result[index + 46 + it] == name[it] }
                ) {
                    result[index + 10] = method.toByte()
                    result[index + 11] = (method ushr 8).toByte()
                    return result
                }
            }
            index++
        }
        error("Central directory entry not found.")
    }

    private fun setGeneralPurposeFlags(
        bytes: ByteArray,
        entryName: String,
        flagsToAdd: Int,
    ): ByteArray {
        val result = bytes.copyOf()
        val name = entryName.toByteArray()
        var index = 0
        while (index <= result.size - 46) {
            if (result[index] == 0x50.toByte() &&
                result[index + 1] == 0x4b.toByte() &&
                result[index + 2] == 0x01.toByte() &&
                result[index + 3] == 0x02.toByte()
            ) {
                val nameLength = unsignedShort(result, index + 28)
                if (nameLength == name.size &&
                    name.indices.all { result[index + 46 + it] == name[it] }
                ) {
                    val localOffset = littleEndianInt(result, index + 42)
                    val centralFlags = unsignedShort(result, index + 8) or flagsToAdd
                    val localFlags = unsignedShort(result, localOffset + 6) or flagsToAdd
                    result[index + 8] = centralFlags.toByte()
                    result[index + 9] = (centralFlags ushr 8).toByte()
                    result[localOffset + 6] = localFlags.toByte()
                    result[localOffset + 7] = (localFlags ushr 8).toByte()
                    return result
                }
            }
            index++
        }
        error("Central directory entry not found.")
    }

    private fun lieAboutUncompressedSize(
        bytes: ByteArray,
        entryName: String,
        declaredSize: Int,
    ): ByteArray {
        val result = bytes.copyOf()
        val name = entryName.toByteArray()
        var index = 0
        while (index <= result.size - 46) {
            if (result[index] == 0x50.toByte() &&
                result[index + 1] == 0x4b.toByte() &&
                result[index + 2] == 0x01.toByte() &&
                result[index + 3] == 0x02.toByte()
            ) {
                val nameLength = unsignedShort(result, index + 28)
                if (nameLength == name.size &&
                    name.indices.all { result[index + 46 + it] == name[it] }
                ) {
                    val localOffset = littleEndianInt(result, index + 42)
                    setLittleEndianInt(result, index + 24, declaredSize)
                    setLittleEndianInt(result, localOffset + 22, declaredSize)
                    return result
                }
            }
            index++
        }
        error("Central directory entry not found.")
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun setLittleEndianInt(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { byteIndex ->
            bytes[offset + byteIndex] = (value ushr (byteIndex * 8)).toByte()
        }
    }

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private class ConcatenatedInputStream(
        private val first: InputStream,
        private val second: InputStream,
    ) : InputStream() {
        private var readingFirst = true

        override fun read(): Int {
            if (readingFirst) {
                val value = first.read()
                if (value >= 0) return value
                readingFirst = false
            }
            return second.read()
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (readingFirst) {
                val count = first.read(bytes, offset, length)
                if (count >= 0) return count
                readingFirst = false
            }
            return second.read(bytes, offset, length)
        }
    }
}
