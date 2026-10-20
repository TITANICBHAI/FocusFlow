package com.tbtechs.focusflow.data.images

import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.SubtaskImage
import com.tbtechs.focusflow.data.model.SubtaskStep
import com.tbtechs.focusflow.data.model.Task
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskImageStoreTest {
    @Test
    fun ingestUsesContentAddressedWebpAndDeduplicates() = withStore { store, _, _ ->
        val first = store.ingest(fakePng())
        val second = store.ingest(fakePng())
        val expectedId = SubtaskImageStore.sha256(fakeWebp())

        assertTrue(first.isSuccess)
        assertEquals(expectedId, first.getOrThrow())
        assertEquals(64, expectedId.length)
        assertEquals(first.getOrThrow(), second.getOrThrow())
        assertTrue(store.exists(expectedId))
        assertTrue(SubtaskImageStore.isWebp(store.openForExport(expectedId)!!))
        assertTrue(store.file(expectedId)!!.parentFile!!.listFiles().orEmpty()
            .none { it.name.startsWith("ingest-") })
    }

    @Test
    fun rejectsUnsupportedMagicAndOversizedDimensionsBeforeDecode() = withStore { store, codec, _ ->
        assertTrue(store.ingest(byteArrayOf(1, 2, 3)).isFailure)
        codec.imageBounds = ImageBounds(4_097, 1)
        assertTrue(store.ingest(fakePng()).isFailure)
        assertEquals(0, codec.reencodeCalls)

        codec.imageBounds = ImageBounds(64, 64)
        codec.encodedBytes = fakeWebp().copyOf(6 * 1024 * 1024 + 1)
        assertTrue(store.ingest(fakePng()).isFailure)
    }

    @Test
    fun appliesTenMinuteGracePeriodAndPreservesReferencedFiles() = withStore { store, _, clock ->
        val id = store.ingest(fakePng()).getOrThrow()
        clock.value = store.file(id)!!.lastModified()
        clock.advance(SubtaskImageStore.GRACE_PERIOD_MILLIS - 1)
        assertEquals(0, store.sweep(emptySet()))
        assertTrue(store.exists(id))

        clock.advance(2)
        assertEquals(0, store.sweep(setOf(id)))
        assertEquals(1, store.sweep(emptySet()))
        assertFalse(store.exists(id))
    }

    @Test
    fun hostileIdsCannotBecomePathsAndReferencesIncludeStepImages() {
        withStore { store, _, _ ->
            assertNull(store.file("../../outside"))
            assertNull(store.file("x".repeat(64)))
            val task = task().copy(
                subtasks = listOf(
                    Subtask(
                        id = "a",
                        title = "A",
                        image = SubtaskImage("overview"),
                        steps = listOf(SubtaskStep(text = "step", image = SubtaskImage("step-image"))),
                    ),
                ),
            )
            assertEquals(
                setOf("overview", "step-image"),
                SubtaskImageStore.referencedAssetIds(listOf(task)),
            )
        }
    }

    private fun withStore(block: (SubtaskImageStore, FakeCodec, FakeClock) -> Unit) {
        val directory = Files.createTempDirectory("subtask-images").toFile()
        val clock = FakeClock(System.currentTimeMillis())
        val codec = FakeCodec()
        try {
            block(SubtaskImageStore(directory, codec, clock::now), codec, clock)
        } finally {
            directory.deleteRecursively()
        }
    }

    private class FakeClock(var value: Long) {
        fun now(): Long = value
        fun advance(milliseconds: Long) {
            value += milliseconds
        }
    }

    private class FakeCodec : ImageCodec {
        var imageBounds = ImageBounds(64, 64)
        var reencodeCalls = 0
        var encodedBytes = fakeWebp()

        override fun bounds(bytes: ByteArray): ImageBounds = imageBounds

        override fun reencode(bytes: ByteArray, maxLongEdge: Int, quality: Int): ByteArray {
            reencodeCalls++
            return encodedBytes
        }
    }

    companion object {
        fun fakePng(): ByteArray =
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

        fun fakeWebp(): ByteArray =
            byteArrayOf(
                0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0,
                0x57, 0x45, 0x42, 0x50,
            )

        fun task() = Task(
            id = "task",
            title = "Task",
            startTime = "2026-10-21T10:00:00Z",
            endTime = "2026-10-21T10:30:00Z",
            durationMinutes = 30,
            status = "scheduled",
            priority = "medium",
            color = "#22c55e",
            focusMode = false,
            createdAt = "2026-10-20T10:00:00Z",
            updatedAt = "2026-10-20T10:00:00Z",
        )
    }
}
