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
                ),
            ),
            cache,
        )
        try {
            assertEquals(byteArrayOf(7), document.assetByteSource.open("safe"))
            assertEquals(null, document.assetByteSource.open("unsafe"))
            assertTrue(document.warnings.isNotEmpty())
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
