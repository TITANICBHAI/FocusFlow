package com.tbtechs.focusflow.data.images

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperPathPolicyTest {
    private fun withDirs(block: (filesDir: File, outside: File) -> Unit) {
        val base = Files.createTempDirectory("wallpaper-policy").toFile()
        try {
            val filesDir = File(base, "files").apply { mkdirs() }
            val outside = File(base, "outside").apply { mkdirs() }
            block(filesDir, outside)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun acceptsFilesInsideFilesDirIncludingLegacyFileScheme() = withDirs { filesDir, _ ->
        val inside = File(filesDir, "launcher_wallpaper/a.webp").apply {
            parentFile!!.mkdirs(); writeBytes(byteArrayOf(1))
        }
        assertEquals(inside.canonicalFile, WallpaperPathPolicy.resolve(filesDir, inside.path))
        assertNotNull(WallpaperPathPolicy.resolve(filesDir, "file://${inside.path}"))
    }

    @Test
    fun rejectsContentUrisOtherSchemesAndBlankValues() = withDirs { filesDir, _ ->
        assertNull(WallpaperPathPolicy.resolve(filesDir, "content://media/external/images/1"))
        assertNull(WallpaperPathPolicy.resolve(filesDir, "http://example.com/a.png"))
        assertNull(WallpaperPathPolicy.resolve(filesDir, ""))
        assertNull(WallpaperPathPolicy.resolve(filesDir, null))
        assertNull(WallpaperPathPolicy.resolve(filesDir, "relative/path.webp"))
        assertNull(WallpaperPathPolicy.resolve(filesDir, "file://"))
    }

    @Test
    fun rejectsPathsOutsideFilesDirTraversalAndSiblingPrefixes() = withDirs { filesDir, outside ->
        val foreign = File(outside, "x.png").apply { writeBytes(byteArrayOf(1)) }
        assertNull(WallpaperPathPolicy.resolve(filesDir, foreign.path))
        assertNull(WallpaperPathPolicy.resolve(filesDir, "${filesDir.path}/../outside/x.png"))
        // "files-evil" shares a string prefix with "files" but is a different directory.
        val sibling = File(filesDir.parentFile, "files-evil").apply { mkdirs() }
        val sneaky = File(sibling, "x.png").apply { writeBytes(byteArrayOf(1)) }
        assertNull(WallpaperPathPolicy.resolve(filesDir, sneaky.path))
    }

    @Test
    fun rejectsSymlinksThatEscapeFilesDir() = withDirs { filesDir, outside ->
        val target = File(outside, "secret.png").apply { writeBytes(byteArrayOf(1)) }
        val link = File(filesDir, "link.png")
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } catch (_: Exception) {
            return@withDirs // symlinks unavailable on this platform
        }
        assertNull(WallpaperPathPolicy.resolve(filesDir, link.path))
    }

    @Test
    fun requireFileControlsWhetherMissingFilesAreAccepted() = withDirs { filesDir, _ ->
        val missing = File(filesDir, "missing.webp")
        assertNull(WallpaperPathPolicy.resolve(filesDir, missing.path))
        assertNotNull(WallpaperPathPolicy.resolve(filesDir, missing.path, requireFile = false))
    }

    @Test
    fun isStorableAcceptsOnlyPlainInsidePaths() = withDirs { filesDir, outside ->
        val inside = File(filesDir, "w.webp")
        assertTrue(WallpaperPathPolicy.isStorable(filesDir, inside.path))
        assertFalse(WallpaperPathPolicy.isStorable(filesDir, "file://${inside.path}"))
        assertFalse(WallpaperPathPolicy.isStorable(filesDir, "content://x/1"))
        assertFalse(WallpaperPathPolicy.isStorable(filesDir, File(outside, "w.webp").path))
    }

    @Test
    fun sampleSizeKeepsBothDimensionsAtOrAboveTheScreen() {
        assertEquals(1, WallpaperLoader.sampleSizeFor(1080, 2400, 1080, 2400))
        assertEquals(1, WallpaperLoader.sampleSizeFor(1500, 3000, 1080, 2400))
        assertEquals(2, WallpaperLoader.sampleSizeFor(4096, 4096, 1080, 2000))
        // 12000x9000: at 1/4 the height would be 2250 px, below the 2400 px screen height, so only 1/2 is allowed.
        assertEquals(2, WallpaperLoader.sampleSizeFor(12000, 9000, 1080, 2400))
        assertEquals(4, WallpaperLoader.sampleSizeFor(12000, 12000, 1080, 2400))
        assertEquals(1, WallpaperLoader.sampleSizeFor(0, 100, 1080, 2400))
        assertEquals(1, WallpaperLoader.sampleSizeFor(4096, 4096, 0, 0))
    }
}
