package com.tbtechs.focusflow.data.images

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Imports a user-picked wallpaper into app-private storage.
 *
 * The picked image is validated (magic bytes, size, dimensions) and re-encoded
 * to WebP, exactly like [SubtaskImageStore] does for task images, so the stored
 * file is a clean image that no longer depends on the picker's temporary URI
 * grant. Only one wallpaper is kept at a time, in its own directory (so the
 * task-image sweeper never touches it).
 *
 * Limits are deliberately more generous than for task images: a wallpaper is
 * usually a full-resolution phone photo, and the codec sub-samples before
 * decoding, so large sources stay memory-safe.
 */
class WallpaperStore(
    private val directory: File,
    private val codec: ImageCodec,
) {
    /** Returns the canonical absolute path of the stored wallpaper. */
    fun ingest(input: InputStream): Result<String> = runCatching {
        val bytes = input.readBounded(MAX_INPUT_BYTES)
        try {
            ingest(bytes).getOrThrow()
        } finally {
            bytes.fill(0)
        }
    }

    fun ingest(bytes: ByteArray): Result<String> = runCatching {
        require(bytes.isNotEmpty()) { "Image is empty." }
        require(SubtaskImageStore.hasSupportedMagic(bytes)) {
            "Only PNG, JPEG, and WebP images are supported."
        }
        require(bytes.size <= MAX_INPUT_BYTES) { "Image exceeds the supported size limit." }
        val orientation = codec.orientation(bytes).takeIf { it in 1..8 } ?: 1
        val bounds = codec.bounds(bytes)
            ?.rotatedForExif(orientation)
            ?: error("Image could not be decoded.")
        require(bounds.width > 0 && bounds.height > 0) { "Image dimensions are invalid." }
        require(bounds.width <= MAX_SOURCE_EDGE && bounds.height <= MAX_SOURCE_EDGE) {
            "Image dimensions are too large."
        }
        require(bounds.width.toLong() * bounds.height <= MAX_SOURCE_PIXELS) {
            "Image dimensions are too large."
        }
        val encoded = codec.reencodeWithOrientation(
            bytes = bytes,
            maxLongEdge = MAX_STORED_EDGE,
            quality = STORED_QUALITY,
            orientation = orientation,
            pngSource = false,
        ) ?: error("Image could not be re-encoded.")
        require(SubtaskImageStore.isWebp(encoded)) { "Image encoder did not produce WebP data." }
        require(encoded.isNotEmpty() && encoded.size.toLong() <= MAX_STORED_FILE_BYTES) {
            "Re-encoded image exceeds the stored size limit."
        }
        val name = SubtaskImageStore.sha256(encoded) + ".webp"
        check(directory.isDirectory || directory.mkdirs()) { "Wallpaper storage is unavailable." }
        val target = File(directory, name)
        if (!target.isFile) writeAtomically(target, encoded)
        removeOthers(keep = name)
        target.canonicalPath
    }

    /** Removes every stored wallpaper. */
    fun clear() {
        directory.listFiles().orEmpty().forEach { runCatching { it.delete() } }
    }

    private fun removeOthers(keep: String) {
        directory.listFiles().orEmpty()
            .filter { it.name != keep }
            .forEach { runCatching { it.delete() } }
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temporary = File(directory, "ingest-${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(64 * 1024)
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
        const val MAX_SOURCE_EDGE = 20_000
        const val MAX_SOURCE_PIXELS = 200L * 1024 * 1024
        const val MAX_STORED_EDGE = 2_560
        const val STORED_QUALITY = 85
        const val MAX_INPUT_BYTES = 20 * 1024 * 1024
        private const val MAX_STORED_FILE_BYTES = 8L * 1024 * 1024
    }
}
