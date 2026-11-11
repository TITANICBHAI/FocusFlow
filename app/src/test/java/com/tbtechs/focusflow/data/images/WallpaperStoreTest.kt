package com.tbtechs.focusflow.data.images

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperStoreTest {
    private class FakeCodec : ImageCodec {
        var imageBounds = ImageBounds(4000, 3000)
        var encoded = webp()
        var reencodeCalls = 0
        var lastMaxEdge = 0

        override fun bounds(bytes: ByteArray): ImageBounds = imageBounds

        override fun reencode(bytes: ByteArray, maxLongEdge: Int, quality: Int): ByteArray = encoded

        override fun reencodeWithOrientation(
            bytes: ByteArray,
            maxLongEdge: Int,
            quality: Int,
            orientation: Int,
            pngSource: Boolean,
        ): ByteArray {
            reencodeCalls++
            lastMaxEdge = maxLongEdge
            return encoded
        }
    }

    private fun withStore(block: (WallpaperStore, FakeCodec, File) -> Unit) {
        val dir = Files.createTempDirectory("wallpaper-store").toFile()
        try {
            val codec = FakeCodec()
            block(WallpaperStore(File(dir, "launcher_wallpaper"), codec), codec, dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun storesAReencodedWebpAndReturnsAnInsidePath() = withStore { store, codec, dir ->
        val path = store.ingest(ByteArrayInputStream(jpeg())).getOrThrow()
        val file = File(path)
        assertTrue(file.isFile)
        assertTrue(SubtaskImageStore.isWebp(file.readBytes()))
        assertTrue(file.canonicalPath.startsWith(dir.canonicalPath + File.separator))
        assertEquals(WallpaperStore.MAX_STORED_EDGE, codec.lastMaxEdge)
    }

    @Test
    fun acceptsLargePhoneSizedSourcesThatTaskImagesWouldReject() = withStore { store, codec, _ ->
        codec.imageBounds = ImageBounds(8000, 6000)
        assertTrue(store.ingest(jpeg()).isSuccess)
    }

    @Test
    fun rejectsBadMagicOversizedInputAndAbsurdDimensionsBeforeReencoding() = withStore { store, codec, _ ->
        assertTrue(store.ingest(byteArrayOf(1, 2, 3)).isFailure)
        assertTrue(store.ingest(ByteArrayInputStream(ByteArray(WallpaperStore.MAX_INPUT_BYTES + 1))).isFailure)
        codec.imageBounds = ImageBounds(WallpaperStore.MAX_SOURCE_EDGE + 1, 10)
        assertTrue(store.ingest(jpeg()).isFailure)
        codec.imageBounds = ImageBounds(19_000, 19_000)
        assertTrue(store.ingest(jpeg()).isFailure)
        assertEquals(0, codec.reencodeCalls)
    }

    @Test
    fun failsClosedWhenTheEncoderDoesNotProduceWebp() = withStore { store, codec, _ ->
        codec.encoded = byteArrayOf(1, 2, 3, 4)
        assertTrue(store.ingest(jpeg()).isFailure)
    }

    @Test
    fun keepsOnlyTheLatestWallpaperAndClearRemovesIt() = withStore { store, codec, dir ->
        val first = store.ingest(jpeg()).getOrThrow()
        codec.encoded = webp(marker = 7)
        val second = store.ingest(jpeg()).getOrThrow()
        assertFalse(File(first).exists())
        assertTrue(File(second).exists())
        assertTrue(File(dir, "launcher_wallpaper").listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        store.clear()
        assertFalse(File(second).exists())
    }

    private companion object {
        fun jpeg() = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())

        fun webp(marker: Byte = 0): ByteArray = byteArrayOf(
            0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0,
            0x57, 0x45, 0x42, 0x50, marker,
        )
    }
}
