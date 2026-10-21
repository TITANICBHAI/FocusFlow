package com.tbtechs.focusflow.data.backup

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.Collections
import java.util.Enumeration
import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal class BackupSourceException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/**
 * A staged backup source stays alive through review and restore. Bundle bytes
 * remain on disk in cache; only the manifest and one requested asset are read
 * into memory at a time.
 */
internal class BackupSourceDocument(
    val manifestJson: String,
    val assetByteSource: AssetByteSource,
    val isBundle: Boolean,
    val bundleAssetIds: Set<String>,
    val warnings: List<String>,
    private val ownedTempFile: File? = null,
) : Closeable {
    override fun close() {
        runCatching { assetByteSource.close() }
        ownedTempFile?.delete()
    }
}

internal object BackupSource {
    private val json = Json { ignoreUnknownKeys = true }
    private val assetPathPattern = Regex("^assets/[A-Za-z0-9._-]{1,100}$")

    fun readUri(
        contentResolver: ContentResolver,
        cacheDir: File,
        uri: Uri,
    ): BackupSourceDocument {
        val input = contentResolver.openInputStream(uri)
            ?: throw BackupSourceException("The selected backup could not be opened.")
        return readStream(input, cacheDir)
    }

    internal fun readStream(input: InputStream, cacheDir: File): BackupSourceDocument {
        val source = input.buffered()
        val signature = ByteArray(4)
        var signatureCount = 0
        while (signatureCount < signature.size) {
            val read = source.read(signature, signatureCount, signature.size - signatureCount)
            if (read < 0) break
            if (read == 0) {
                val next = source.read()
                if (next < 0) break
                signature[signatureCount++] = next.toByte()
            } else {
                signatureCount += read
            }
        }
        val isZip = signatureCount == ZIP_SIGNATURE.size &&
            signature.contentEquals(ZIP_SIGNATURE)
        val prefixed = PrefixInputStream(signature.copyOf(signatureCount), source)
        return if (isZip) {
            readBundle(prefixed, cacheDir)
        } else {
            val text = try {
                BackupJsonPreflight.readUtf8Bounded(prefixed)
            } catch (exception: BackupJsonFormatException) {
                throw BackupSourceException(exception.message ?: "Backup JSON is invalid.", exception)
            } catch (exception: Exception) {
                throw BackupSourceException("Backup could not be read.", exception)
            } finally {
                runCatching { prefixed.close() }
            }
            BackupSourceDocument(
                manifestJson = text,
                assetByteSource = EmptyAssetByteSource,
                isBundle = false,
                bundleAssetIds = emptySet(),
                warnings = emptyList(),
            )
        }
    }

    fun sweepStaleBundleFiles(cacheDir: File, nowMillis: Long = System.currentTimeMillis()): Int {
        val directory = File(cacheDir, BUNDLE_CACHE_DIRECTORY)
        if (!directory.isDirectory) return 0
        val cutoff = nowMillis - BackupJsonLimits.MAX_BUNDLE_TEMP_AGE_MILLIS
        var removed = 0
        directory.listFiles().orEmpty().forEach { file ->
            if (file.isFile && file.lastModified() < cutoff && file.delete()) removed++
        }
        return removed
    }

    private fun readBundle(input: InputStream, cacheDir: File): BackupSourceDocument {
        val directory = File(cacheDir, BUNDLE_CACHE_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            input.close()
            throw BackupSourceException("Temporary backup storage is unavailable.")
        }
        val stagedFile = File.createTempFile("bundle-", ".zip", directory)
        try {
            FileOutputStream(stagedFile).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) {
                        val next = input.read()
                        if (next < 0) break
                        if (total == BackupJsonLimits.MAX_BUNDLE_BYTES) {
                            throw BackupSourceException("Backup bundle exceeds the 64 MiB file limit.")
                        }
                        output.write(next)
                        total++
                        continue
                    }
                    if (count > BackupJsonLimits.MAX_BUNDLE_BYTES - total) {
                        throw BackupSourceException("Backup bundle exceeds the 64 MiB file limit.")
                    }
                    output.write(buffer, 0, count)
                    total += count
                }
                output.fd.sync()
            }
            input.close()
            return openBundle(stagedFile)
        } catch (exception: BackupSourceException) {
            stagedFile.delete()
            throw exception
        } catch (exception: Exception) {
            stagedFile.delete()
            throw BackupSourceException("This is not a valid FocusFlow file.", exception)
        }
    }

    private fun openBundle(stagedFile: File): BackupSourceDocument {
        val centralEntries = readCentralDirectoryEntries(stagedFile)
        val manifestEntries = centralEntries.filter { it.name == MANIFEST_ENTRY }
        if (manifestEntries.size > 1) {
            throw BackupSourceException("Backup bundle has more than one manifest.json.")
        }
        val manifestCentralEntry = manifestEntries.singleOrNull()
            ?: throw BackupSourceException("Backup bundle is missing manifest.json.")
        if (manifestCentralEntry.flags and ENCRYPTION_FLAGS_MASK != 0) {
            throw BackupSourceException("Backup bundle manifest is encrypted.")
        }
        if (manifestCentralEntry.method !in SUPPORTED_ZIP_METHODS) {
            throw BackupSourceException("Backup bundle manifest is invalid.")
        }
        val encryptedNames = centralEntries
            .filter { it.flags and ENCRYPTION_FLAGS_MASK != 0 }
            .mapTo(mutableSetOf(), BundleCentralDirectoryEntry::name)
        val unsupportedCompressionNames = centralEntries
            .filter { it.name != MANIFEST_ENTRY && it.method !in SUPPORTED_ZIP_METHODS }
            .mapTo(mutableSetOf(), BundleCentralDirectoryEntry::name)
        sanitizeSkippedCentralEntries(stagedFile, centralEntries)
        val zip = try {
            ZipFile(stagedFile)
        } catch (exception: Exception) {
            throw BackupSourceException("This is not a valid FocusFlow file.", exception)
        }
        try {
            val entries = zip.entries().toListBounded()
            if (entries.size > BackupJsonLimits.MAX_BUNDLE_ENTRIES) {
                throw BackupSourceException("Backup bundle contains too many entries.")
            }
            val seenNames = mutableSetOf<String>()
            val safeEntries = linkedMapOf<String, ZipEntry>()
            val warnings = mutableListOf<String>()
            var ignoredEntries = 0
            var manifestCount = 0
            entries.forEach { entry ->
                val name = entry.name
                if (name == MANIFEST_ENTRY) {
                    manifestCount++
                    if (manifestCount > 1) {
                        throw BackupSourceException("Backup bundle has more than one manifest.json.")
                    }
                    if (name in encryptedNames) {
                        throw BackupSourceException("Backup bundle manifest is encrypted.")
                    }
                    if (entry.isDirectory || entry.method !in SUPPORTED_ZIP_METHODS) {
                        throw BackupSourceException("Backup bundle manifest is invalid.")
                    }
                    safeEntries[name] = entry
                    return@forEach
                }
                if (!seenNames.add(name)) {
                    ignoredEntries++
                    return@forEach
                }
                if (name in encryptedNames || name in unsupportedCompressionNames ||
                    !isSafeEntryName(name) || entry.isDirectory ||
                    entry.method !in SUPPORTED_ZIP_METHODS
                ) {
                    ignoredEntries++
                    return@forEach
                }
                if (name.startsWith(ASSET_DIRECTORY_PREFIX)) {
                    if (!isSupportedAssetPath(name) ||
                        safeEntries.keys.count { it.startsWith(ASSET_DIRECTORY_PREFIX) } >=
                        BackupJsonLimits.MAX_BUNDLE_ASSETS
                    ) {
                        ignoredEntries++
                    } else {
                        safeEntries[name] = entry
                    }
                } else {
                    ignoredEntries++
                }
            }
            if (manifestCount != 1) {
                throw BackupSourceException("Backup bundle is missing manifest.json.")
            }
            if (ignoredEntries > 0) {
                warnings += "$ignoredEntries unsupported or extra bundle entries were ignored."
            }

            val manifestEntry = safeEntries[MANIFEST_ENTRY]
                ?: throw BackupSourceException("Backup bundle is missing a readable manifest.json.")
            if (manifestEntry.size > BackupJsonLimits.MAX_BUNDLE_MANIFEST_BYTES) {
                throw BackupSourceException("Backup bundle manifest exceeds the 8 MiB limit.")
            }
            val manifestBytes = readEntryBounded(
                zip = zip,
                entry = manifestEntry,
                cap = BackupJsonLimits.MAX_BUNDLE_MANIFEST_BYTES,
            )
            if (manifestBytes == null) {
                throw BackupSourceException("Backup bundle manifest is invalid or too large.")
            }
            val manifestText = try {
                val decoded = BackupJsonPreflight.readUtf8Bounded(manifestBytes.inputStream())
                BackupJsonPreflight.validateAndStripBom(decoded)
            } catch (exception: Exception) {
                throw BackupSourceException("Backup bundle manifest is invalid.", exception)
            }
            val assetReferences = parseBundleAssetPaths(manifestText, safeEntries, warnings)
            val source = ZipAssetSource(
                zip = zip,
                entries = safeEntries,
                assetReferences = assetReferences,
                initialUncompressedBytes = manifestBytes.size,
            )
            return BackupSourceDocument(
                manifestJson = manifestText,
                assetByteSource = source,
                isBundle = true,
                bundleAssetIds = assetReferences.keys,
                warnings = warnings,
                ownedTempFile = stagedFile,
            )
        } catch (exception: BackupSourceException) {
            zip.close()
            stagedFile.delete()
            throw exception
        } catch (exception: Exception) {
            zip.close()
            stagedFile.delete()
            throw BackupSourceException("This is not a valid FocusFlow file.", exception)
        }
    }

    private fun parseBundleAssetPaths(
        manifest: String,
        entries: Map<String, ZipEntry>,
        warnings: MutableList<String>,
    ): Map<String, BundleAssetReference> {
        val root = try {
            json.parseToJsonElement(manifest) as? JsonObject
        } catch (exception: Exception) {
            throw BackupSourceException("Backup bundle manifest is invalid.", exception)
        } ?: throw BackupSourceException("Backup bundle manifest must be a JSON object.")
        val assetObject = root["assets"] as? JsonObject ?: return emptyMap()
        val result = linkedMapOf<String, BundleAssetReference>()
        var missingCount = 0
        assetObject.forEach { (id, value) ->
            val file = (value as? JsonObject)
                ?.get("file")
                ?.let { it as? JsonPrimitive }
                ?.takeIf(JsonPrimitive::isString)
                ?.contentOrNull
            val sha256 = (value as? JsonObject)
                ?.get("sha256")
                ?.let { it as? JsonPrimitive }
                ?.takeIf(JsonPrimitive::isString)
                ?.contentOrNull
            if (file == null || !ASSET_ID_PATTERN.matches(id) || !isSupportedAssetPath(file)) {
                if (file != null) missingCount++
                return@forEach
            }
            val entry = entries[file]
            if (entry == null || entry.size > BackupJsonLimits.MAX_BUNDLE_ASSET_ENTRY_BYTES) {
                missingCount++
            } else {
                result[id] = BundleAssetReference(file = file, sha256 = sha256)
            }
        }
        if (missingCount > 0) {
            warnings += "$missingCount bundle images are missing or invalid."
        }
        return result
    }

    private fun readEntryBounded(zip: ZipFile, entry: ZipEntry, cap: Int): ByteArray? {
        if (entry.size > cap) return null
        return try {
            zip.getInputStream(entry).use { input ->
                val output = ByteArrayOutputStream(minOf(cap, entry.size.coerceAtLeast(0).toInt()))
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) {
                        val next = input.read()
                        if (next < 0) break
                        if (total == cap) return null
                        output.write(next)
                        total++
                        continue
                    }
                    if (count > cap - total) return null
                    output.write(buffer, 0, count)
                    total += count
                }
                output.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * ZipEntry does not expose general-purpose encryption flags. Read those
     * flags from the central directory before opening entries so encrypted
     * assets can be skipped individually and an encrypted manifest is rejected.
     */
    private fun readCentralDirectoryEntries(file: File): List<BundleCentralDirectoryEntry> =
        try {
            RandomAccessFile(file, "r").use { archive ->
                val fileLength = archive.length()
                val tailLength = minOf(fileLength, MAX_EOCD_SEARCH_BYTES.toLong()).toInt()
                if (tailLength < EOCD_MIN_BYTES) {
                    throw BackupSourceException("This is not a valid FocusFlow file.")
                }
                val tail = ByteArray(tailLength)
                archive.seek(fileLength - tailLength)
                archive.readFully(tail)
                val eocdOffset = findEndOfCentralDirectory(tail)
                    ?: throw BackupSourceException("This is not a valid FocusFlow file.")

                val diskNumber = unsignedShort(tail, eocdOffset + 4)
                val centralDirectoryDisk = unsignedShort(tail, eocdOffset + 6)
                val entriesOnDisk = unsignedShort(tail, eocdOffset + 8)
                val entryCount = unsignedShort(tail, eocdOffset + 10)
                val centralDirectorySize = unsignedInt(tail, eocdOffset + 12)
                val centralDirectoryOffset = unsignedInt(tail, eocdOffset + 16)
                if (diskNumber != 0 || centralDirectoryDisk != 0 ||
                    entriesOnDisk != entryCount ||
                    entryCount == ZIP64_SENTINEL ||
                    centralDirectorySize == ZIP64_SENTINEL_LONG ||
                    centralDirectoryOffset == ZIP64_SENTINEL_LONG
                ) {
                    throw BackupSourceException("This is not a supported FocusFlow bundle.")
                }
                if (entryCount > BackupJsonLimits.MAX_BUNDLE_ENTRIES) {
                    throw BackupSourceException("Backup bundle contains too many entries.")
                }
                val directoryEnd = centralDirectoryOffset + centralDirectorySize
                if (directoryEnd > fileLength || centralDirectoryOffset < 0) {
                    throw BackupSourceException("This is not a valid FocusFlow file.")
                }

                archive.seek(centralDirectoryOffset)
                val entries = ArrayList<BundleCentralDirectoryEntry>(entryCount)
                repeat(entryCount) {
                    val headerOffset = archive.filePointer
                    val header = ByteArray(CENTRAL_DIRECTORY_HEADER_BYTES)
                    archive.readFully(header)
                    if (littleEndianInt(header, 0) != CENTRAL_DIRECTORY_SIGNATURE) {
                        throw BackupSourceException("This is not a valid FocusFlow file.")
                    }
                    val flags = unsignedShort(header, 8)
                    val method = unsignedShort(header, 10)
                    val nameLength = unsignedShort(header, 28)
                    val extraLength = unsignedShort(header, 30)
                    val commentLength = unsignedShort(header, 32)
                    if (nameLength == 0) {
                        throw BackupSourceException("This is not a valid FocusFlow file.")
                    }
                    val nameBytes = ByteArray(nameLength)
                    archive.readFully(nameBytes)
                    val name = nameBytes.toString(Charsets.UTF_8)
                    entries += BundleCentralDirectoryEntry(
                        name = name,
                        flags = flags,
                        method = method,
                        headerOffset = headerOffset,
                    )
                    val trailingLength = extraLength.toLong() + commentLength
                    if (archive.filePointer + trailingLength > directoryEnd) {
                        throw BackupSourceException("This is not a valid FocusFlow file.")
                    }
                    archive.seek(archive.filePointer + trailingLength)
                }
                if (archive.filePointer > directoryEnd) {
                    throw BackupSourceException("This is not a valid FocusFlow file.")
                }
                entries
            }
        } catch (exception: BackupSourceException) {
            throw exception
        } catch (exception: Exception) {
            throw BackupSourceException("This is not a valid FocusFlow file.", exception)
        }

    /**
     * ZipFile rejects some central-directory methods and encryption flags before
     * callers can skip those entries. Preserve their original metadata above,
     * then normalize only the staged cache copy so ZipFile can enumerate the
     * archive; entries in these sets are never added to the readable source map.
     */
    private fun sanitizeSkippedCentralEntries(
        file: File,
        entries: List<BundleCentralDirectoryEntry>,
    ) {
        val skipped = entries.filter {
            it.name != MANIFEST_ENTRY &&
                (it.flags and ENCRYPTION_FLAGS_MASK != 0 || it.method !in SUPPORTED_ZIP_METHODS)
        }
        if (skipped.isEmpty()) return
        RandomAccessFile(file, "rw").use { archive ->
            skipped.forEach { entry ->
                archive.seek(entry.headerOffset + CENTRAL_FLAGS_OFFSET)
                writeLittleEndianShort(
                    archive,
                    entry.flags and ENCRYPTION_FLAGS_MASK.inv(),
                )
                if (entry.method !in SUPPORTED_ZIP_METHODS) {
                    archive.seek(entry.headerOffset + CENTRAL_METHOD_OFFSET)
                    writeLittleEndianShort(archive, ZipEntry.DEFLATED)
                }
            }
            archive.fd.sync()
        }
    }

    private fun writeLittleEndianShort(archive: RandomAccessFile, value: Int) {
        archive.writeByte(value and 0xff)
        archive.writeByte((value ushr 8) and 0xff)
    }

    private fun findEndOfCentralDirectory(bytes: ByteArray): Int? {
        for (offset in bytes.size - EOCD_MIN_BYTES downTo 0) {
            if (littleEndianInt(bytes, offset) != EOCD_SIGNATURE) continue
            val commentLength = unsignedShort(bytes, offset + 20)
            if (offset + EOCD_MIN_BYTES + commentLength == bytes.size) return offset
        }
        return null
    }

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun unsignedInt(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, Int.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int

    private fun isSafeEntryName(name: String): Boolean =
        name.isNotEmpty() &&
            !name.contains("..") &&
            !name.startsWith('/') &&
            '\\' !in name &&
            '\u0000' !in name &&
            !DRIVE_PREFIX.containsMatchIn(name)

    private fun isSupportedAssetPath(name: String): Boolean =
        assetPathPattern.matches(name) &&
            name.substringAfterLast('.').lowercase() in SUPPORTED_ASSET_EXTENSIONS

    private fun Enumeration<out ZipEntry>.toListBounded(): List<ZipEntry> {
        val result = mutableListOf<ZipEntry>()
        while (hasMoreElements()) {
            if (result.size >= BackupJsonLimits.MAX_BUNDLE_ENTRIES + 1) {
                return result
            }
            result += nextElement()
        }
        return result
    }

    private class PrefixInputStream(
        private val prefix: ByteArray,
        private val remainder: InputStream,
    ) : InputStream() {
        private var index = 0

        override fun read(): Int =
            if (index < prefix.size) prefix[index++].toInt() and 0xff else remainder.read()

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (index < prefix.size) {
                val count = minOf(length, prefix.size - index)
                prefix.copyInto(bytes, offset, index, index + count)
                index += count
                return count
            }
            return remainder.read(bytes, offset, length)
        }

        override fun close() = remainder.close()
    }

    private const val BUNDLE_CACHE_DIRECTORY = "import_bundle"
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val ASSET_DIRECTORY_PREFIX = "assets/"
    private const val CENTRAL_DIRECTORY_HEADER_BYTES = 46
    private const val CENTRAL_FLAGS_OFFSET = 8
    private const val CENTRAL_METHOD_OFFSET = 10
    private const val EOCD_MIN_BYTES = 22
    private const val MAX_EOCD_SEARCH_BYTES = EOCD_MIN_BYTES + 65_535
    private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50
    private const val EOCD_SIGNATURE = 0x06054b50
    private const val ENCRYPTION_FLAGS_MASK = 0x41
    private const val ZIP64_SENTINEL = 0xffff
    private const val ZIP64_SENTINEL_LONG = 0xffff_ffffL
    private val SUPPORTED_ZIP_METHODS = setOf(ZipEntry.STORED, ZipEntry.DEFLATED)
    private val SUPPORTED_ASSET_EXTENSIONS = setOf("webp", "png", "jpg", "jpeg")
    private val DRIVE_PREFIX = Regex("^[A-Za-z]:")
    private val ASSET_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}$")
    private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4b, 0x03, 0x04)

    internal fun jsonDocument(text: String): BackupSourceDocument =
        BackupSourceDocument(
            manifestJson = text,
            assetByteSource = EmptyAssetByteSource,
            isBundle = false,
            bundleAssetIds = emptySet(),
            warnings = emptyList(),
        )
}

private data class BundleCentralDirectoryEntry(
    val name: String,
    val flags: Int,
    val method: Int,
    val headerOffset: Long,
)

internal data class BundleAssetReference(
    val file: String,
    val sha256: String?,
)

internal class ZipAssetSource(
    private val zip: ZipFile,
    private val entries: Map<String, ZipEntry>,
    private val assetReferences: Map<String, BundleAssetReference>,
    initialUncompressedBytes: Int,
) : AssetByteSource {
    private var uncompressedBytes = initialUncompressedBytes.toLong()

    @Synchronized
    override fun open(assetId: String): ByteArray? {
        val reference = assetReferences[assetId] ?: return null
        val entry = entries[reference.file] ?: return null
        if (entry.size > BackupJsonLimits.MAX_BUNDLE_ASSET_ENTRY_BYTES) return null
        return try {
            zip.getInputStream(entry).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) {
                        val next = input.read()
                        if (next < 0) break
                        if (total == BackupJsonLimits.MAX_BUNDLE_ASSET_ENTRY_BYTES) return null
                        if (uncompressedBytes >= BackupJsonLimits.MAX_BUNDLE_UNCOMPRESSED_BYTES) return null
                        output.write(next)
                        total++
                        uncompressedBytes++
                        continue
                    }
                    if (count > BackupJsonLimits.MAX_BUNDLE_ASSET_ENTRY_BYTES - total ||
                        count.toLong() > BackupJsonLimits.MAX_BUNDLE_UNCOMPRESSED_BYTES - uncompressedBytes
                    ) {
                        return null
                    }
                    output.write(buffer, 0, count)
                    total += count
                    uncompressedBytes += count
                }
                val bytes = output.toByteArray()
                if (reference.sha256 != null &&
                    !reference.sha256.equals(sha256(bytes), ignoreCase = true)
                ) {
                    bytes.fill(0)
                    null
                } else {
                    bytes
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun contains(assetId: String): Boolean = assetId in assetReferences

    override fun close() {
        zip.close()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
