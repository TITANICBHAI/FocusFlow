package com.tbtechs.focusflow.data.images

import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.Task
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class SubtaskImageStore(
    private val directory: File,
    private val codec: ImageCodec,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    fun ingest(bytes: ByteArray): Result<String> = runCatching {
        require(bytes.isNotEmpty()) { "Image is empty." }
        require(hasSupportedMagic(bytes)) { "Only PNG, JPEG, and WebP images are supported." }
        require(bytes.size <= MAX_INPUT_BYTES) { "Image exceeds the supported size limit." }
        val orientation = codec.orientation(bytes).takeIf { it in 1..8 } ?: 1
        val bounds = codec.bounds(bytes)
            ?.rotatedForExif(orientation)
            ?: error("Image could not be decoded.")
        require(bounds.width > 0 && bounds.height > 0) { "Image dimensions are invalid." }
        require(bounds.width <= MAX_SOURCE_EDGE && bounds.height <= MAX_SOURCE_EDGE) {
            "Image dimensions exceed 4096 pixels."
        }
        require(bounds.width.toLong() * bounds.height <= MAX_SOURCE_PIXELS) {
            "Image dimensions exceed 16 megapixels."
        }
        val pngSource = isPng(bytes)
        val quality = if (pngSource) PNG_FALLBACK_QUALITY else STORED_QUALITY
        val encoded = codec.reencodeWithOrientation(
            bytes = bytes,
            maxLongEdge = MAX_STORED_EDGE,
            quality = quality,
            orientation = orientation,
            pngSource = pngSource,
        )
            ?: error("Image could not be re-encoded.")
        require(isWebp(encoded)) { "Image encoder did not produce WebP data." }
        require(encoded.isNotEmpty() && encoded.size.toLong() <= MAX_STORED_FILE_BYTES) {
            "Re-encoded image exceeds the stored size limit."
        }
        val assetId = sha256(encoded)
        writeAtomically(assetId, encoded)
        assetId
    }

    fun ingest(input: InputStream): Result<String> = runCatching {
        val bytes = input.readBounded(MAX_INPUT_BYTES)
        try {
            ingest(bytes).getOrThrow()
        } finally {
            bytes.fill(0)
        }
    }

    fun file(assetId: String): File? {
        if (!ASSET_ID.matches(assetId)) return null
        return File(directory, "$assetId.webp")
    }

    fun exists(assetId: String): Boolean = file(assetId)?.isFile == true

    fun openForExport(assetId: String): ByteArray? {
        val target = file(assetId)?.takeIf(File::isFile) ?: return null
        if (target.length() !in 1..MAX_STORED_FILE_BYTES) return null
        val bytes = target.readBytes()
        return bytes.takeIf { isWebp(it) && sha256(it) == assetId }
    }

    /** Delete unreferenced image files only after the import/write grace period. */
    fun sweep(referencedIds: Set<String>): Int {
        if (!directory.isDirectory) return 0
        val keep = referencedIds.filterTo(mutableSetOf()) { ASSET_ID.matches(it) }
        val cutoff = clockMillis() - GRACE_PERIOD_MILLIS
        var deleted = 0
        directory.listFiles().orEmpty().forEach { candidate ->
            val id = candidate.name.takeIf { it.endsWith(".webp") }
                ?.removeSuffix(".webp")
            val isManagedAsset = id != null && ASSET_ID.matches(id)
            val isStaleTemp = candidate.name.startsWith("ingest-") &&
                candidate.name.endsWith(".tmp")
            if ((isManagedAsset && id !in keep || isStaleTemp) &&
                candidate.lastModified() < cutoff && candidate.delete()
            ) {
                deleted++
            }
        }
        return deleted
    }

    private fun writeAtomically(assetId: String, bytes: ByteArray) {
        check(directory.exists() || directory.mkdirs()) { "Image storage is unavailable." }
        val destination = requireNotNull(file(assetId))
        if (destination.isFile) return
        val temporary = File.createTempFile("ingest-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                check(temporary.renameTo(destination)) { "Image could not be saved atomically." }
            } catch (_: java.nio.file.FileAlreadyExistsException) {
                // Another import of the same content won the race; the existing
                // content-addressed file is the canonical copy.
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            if (count == 0) {
                val value = read()
                if (value < 0) break
                require(total < maxBytes) { "Image exceeds the supported size limit." }
                output.write(value)
                total++
                continue
            }
            require(count <= maxBytes - total) { "Image exceeds the supported size limit." }
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_SOURCE_EDGE = 4_096
        const val MAX_SOURCE_PIXELS = 16L * 1024 * 1024
        const val MAX_STORED_EDGE = 1_280
        const val STORED_QUALITY = 85
        const val PNG_FALLBACK_QUALITY = 90
        const val GRACE_PERIOD_MILLIS = 10 * 60 * 1_000L
        private const val MAX_INPUT_BYTES = 6 * 1024 * 1024
        private const val MAX_STORED_FILE_BYTES = 6 * 1024 * 1024L
        private val ASSET_ID = Regex("^[a-f0-9]{64}$")

        fun referencedAssetIds(tasks: Iterable<Task>): Set<String> =
            referencedAssetIdsFromSubtasks(tasks.asSequence().flatMap { it.subtasks.asSequence() }.asIterable())

        fun referencedAssetIdsFromSubtasks(subtasks: Iterable<Subtask>): Set<String> =
            subtasks.asSequence().flatMap { it.imageReferences() }.toSet()

        private fun Subtask.imageReferences(): Sequence<String> = sequence {
            image?.let { yield(it.asset) }
            steps.asSequence().mapNotNull { it.image?.asset }.forEach { yield(it) }
        }

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

        fun hasSupportedMagic(bytes: ByteArray): Boolean =
            isPng(bytes) || isJpeg(bytes) || isWebp(bytes)

        fun isWebp(bytes: ByteArray): Boolean =
            bytes.size >= 12 &&
                bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x52, 0x49, 0x46, 0x46)) &&
                bytes.copyOfRange(8, 12).contentEquals(byteArrayOf(0x57, 0x45, 0x42, 0x50))

        private fun isPng(bytes: ByteArray): Boolean =
            bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
                byteArrayOf(
                    0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                ),
            )

        private fun isJpeg(bytes: ByteArray): Boolean =
            bytes.size >= 3 &&
                bytes[0] == 0xff.toByte() &&
                bytes[1] == 0xd8.toByte() &&
                bytes[2] == 0xff.toByte()
    }
}
