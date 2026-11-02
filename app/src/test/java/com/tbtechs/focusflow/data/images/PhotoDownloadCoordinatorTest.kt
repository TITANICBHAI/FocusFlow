package com.tbtechs.focusflow.data.images

import com.tbtechs.focusflow.data.model.PhotoSlot
import com.tbtechs.focusflow.data.model.PhotoSlotRef
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.URI
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoDownloadCoordinatorTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3)
    private val webp = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50)

    private class FakeCodec(var encoded: ByteArray?) : ImageCodec {
        override fun bounds(bytes: ByteArray) = ImageBounds(64, 64)
        override fun reencode(bytes: ByteArray, maxLongEdge: Int, quality: Int): ByteArray? = encoded
    }

    private class Reply(
        override val status: Int,
        private val type: String?,
        private val body: ByteArray,
    ) : HttpReply {
        override fun header(name: String): String? = if (name.equals("Content-Type", true)) type else null
        override fun body() = ByteArrayInputStream(body)
        override fun close() = Unit
    }

    private fun ref(id: String, url: String?, stepIndex: Int? = null) = PhotoSlotRef(
        subtaskId = id,
        subtaskTitle = "Item $id",
        stepIndex = stepIndex,
        stepTitle = null,
        slot = PhotoSlot(brief = "Picture for $id", url = url),
    )

    private fun withCoordinator(
        encoded: ByteArray? = webp,
        routes: Map<String, () -> HttpReply>,
        block: (PhotoDownloadCoordinator, MutableList<String>) -> Unit,
    ) {
        val directory = Files.createTempDirectory("photo-coordinator").toFile()
        try {
            val requested = mutableListOf<String>()
            val resolver = HostResolver { listOf(InetAddress.getByAddress(byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34))) }
            val fetch = HttpFetch { uri: URI, _, _ ->
                requested += uri.toString()
                (routes[uri.toString()] ?: error("Unexpected request to $uri"))()
            }
            val store = SubtaskImageStore(directory, FakeCodec(encoded))
            block(PhotoDownloadCoordinator(PhotoDownloader(resolver, fetch), store), requested)
        } finally {
            directory.deleteRecursively()
        }
    }

    private val okRoute = { Reply(200, "image/png", png) as HttpReply }

    @Test
    fun downloadsOncePerLinkAndStoresTheReEncodedPicture() = withCoordinator(
        routes = mapOf("https://a.example/p.png" to okRoute),
    ) { coordinator, requested ->
        val outcome = coordinator.run(
            listOf(
                ref("one", "https://a.example/p.png"),
                ref("two", "https://a.example/p.png"),
                ref("two", "https://a.example/p.png", stepIndex = 1),
            ),
        )

        assertEquals(1, requested.size)
        assertEquals(3, outcome.succeeded)
        assertEquals(0, outcome.failed)
        assertEquals(SubtaskImageStore.sha256(webp), outcome.downloads.first().assetId)
        assertEquals(listOf("one", "two", "two"), outcome.downloads.map { it.subtaskId })
        assertEquals(listOf(null, null, 1), outcome.downloads.map { it.stepIndex })
    }

    @Test
    fun skipsSlotsWithoutALinkAndCountsFailuresByReason() = withCoordinator(
        routes = mapOf(
            "https://a.example/p.png" to okRoute,
            "https://a.example/missing.png" to { Reply(404, null, ByteArray(0)) },
            "https://a.example/page" to { Reply(200, "text/html", png) },
        ),
    ) { coordinator, _ ->
        val outcome = coordinator.run(
            listOf(
                ref("no-link", null),
                ref("ok", "https://a.example/p.png"),
                ref("gone", "https://a.example/missing.png"),
                ref("gone-too", "https://a.example/missing.png"),
                ref("page", "https://a.example/page"),
                ref("bad", "http://a.example/insecure.png"),
            ),
        )

        assertEquals(1, outcome.succeeded)
        assertEquals(4, outcome.failed)
        assertEquals(2, outcome.failures[PhotoDownloadFailure.HTTP_ERROR])
        assertEquals(1, outcome.failures[PhotoDownloadFailure.NOT_AN_IMAGE])
        assertEquals(1, outcome.failures[PhotoDownloadFailure.INVALID_LINK])
        assertEquals(0, outcome.notAttempted)
    }

    @Test
    fun reportsAPictureTheDecoderRejects() = withCoordinator(
        encoded = null,
        routes = mapOf("https://a.example/p.png" to okRoute),
    ) { coordinator, _ ->
        val outcome = coordinator.run(listOf(ref("one", "https://a.example/p.png")))

        assertEquals(0, outcome.succeeded)
        assertEquals(1, outcome.failures[PhotoDownloadFailure.UNREADABLE])
    }

    @Test
    fun limitsOneBatchAndReportsTheRest() = withCoordinator(
        routes = (1..40).associate { "https://a.example/$it.png" to okRoute },
    ) { coordinator, requested ->
        val slots = (1..PhotoDownloadCoordinator.MAX_PER_BATCH + 1).map { ref("s$it", "https://a.example/$it.png") }
        val progress = mutableListOf<Pair<Int, Int>>()

        val outcome = coordinator.run(slots, onProgress = { done, total -> progress += done to total })

        assertEquals(PhotoDownloadCoordinator.MAX_PER_BATCH, requested.size)
        assertEquals(PhotoDownloadCoordinator.MAX_PER_BATCH, outcome.succeeded)
        assertEquals(1, outcome.notAttempted)
        assertEquals(PhotoDownloadCoordinator.MAX_PER_BATCH to PhotoDownloadCoordinator.MAX_PER_BATCH, progress.last())
    }

    @Test
    fun stopsBetweenDownloadsWhenCancelled() = withCoordinator(
        routes = mapOf(
            "https://a.example/1.png" to okRoute,
            "https://a.example/2.png" to okRoute,
        ),
    ) { coordinator, requested ->
        var cancelled = false
        val outcome = coordinator.run(
            listOf(ref("a", "https://a.example/1.png"), ref("b", "https://a.example/2.png")),
            isCancelled = { cancelled },
            onProgress = { done, _ -> if (done == 1) cancelled = true },
        )

        assertEquals(listOf("https://a.example/1.png"), requested)
        assertEquals(1, outcome.succeeded)
        assertEquals(1, outcome.notAttempted)
    }

    @Test
    fun aCancelSeenInsideTheDownloadIsNotReportedAsAFailure() = withCoordinator(
        routes = mapOf("https://a.example/1.png" to okRoute),
    ) { coordinator, requested ->
        var calls = 0
        val outcome = coordinator.run(
            listOf(ref("a", "https://a.example/1.png")),
            isCancelled = { calls++ >= 1 },
        )

        assertTrue(requested.isEmpty())
        assertEquals(0, outcome.succeeded)
        assertEquals(0, outcome.failed)
        assertEquals(1, outcome.notAttempted)
    }
}
