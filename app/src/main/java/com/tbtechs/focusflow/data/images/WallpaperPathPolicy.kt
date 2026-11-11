package com.tbtechs.focusflow.data.images

import java.io.File

/**
 * Decides whether a stored wallpaper reference may be opened.
 *
 * Wallpaper references used to be free-form strings (`content://`, `file://`,
 * or any absolute path) that could arrive from a backup file. They are now
 * only honoured when they resolve, canonically, to a location inside the app's
 * private `filesDir`. Pure JVM so it can be unit tested.
 */
object WallpaperPathPolicy {
    /**
     * Returns the canonical file for [rawPath] when it lives inside [filesDir],
     * otherwise null. A leading `file://` is accepted for values written by older
     * versions; any other URI scheme (for example `content://`) is rejected.
     */
    fun resolve(filesDir: File, rawPath: String?, requireFile: Boolean = true): File? {
        val trimmed = rawPath?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.indexOf('\u0000') >= 0) return null
        val path = when {
            trimmed.startsWith("file://") -> trimmed.removePrefix("file://")
            trimmed.contains("://") -> return null
            else -> trimmed
        }
        if (path.isEmpty()) return null
        return try {
            val candidate = File(path)
            if (!candidate.isAbsolute) return null
            val root = filesDir.canonicalFile
            val canonical = candidate.canonicalFile
            val inside = canonical.path.startsWith(root.path + File.separator)
            if (!inside) return null
            if (requireFile && !canonical.isFile) return null
            canonical
        } catch (_: Exception) {
            null
        }
    }

    /** True when [rawPath] is an acceptable value to persist as a wallpaper reference. */
    fun isStorable(filesDir: File, rawPath: String): Boolean =
        !rawPath.startsWith("file://") &&
            resolve(filesDir, rawPath, requireFile = false) != null
}
