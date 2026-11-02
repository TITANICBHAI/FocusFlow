package com.tbtechs.focusflow.data.images

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoDownloaderTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3)
    private val webp = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50)

    private class FakeReply(
        override val status: Int,
        private val headers: Map<String, String> = emptyMap(),
        private val bodyBytes: ByteArray = ByteArray(0),
        private val bodyStream: (() -> InputStream)? = null,
    ) : HttpReply {
        var closed = false
        var bodyOpened = false
        override fun header(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        override fun body(): InputStream {
            bodyOpened = true
            return bodyStream?.invoke() ?: ByteArrayInputStream(bodyBytes)
        }
        override fun close() {
            closed = true
        }
    }

    private class FakeWeb(private val routes: Map<String, () -> HttpReply>) : HttpFetch {
        val requested = mutableListOf<String>()
        val replies = mutableListOf<FakeReply>()
        var connectTimeout = 0
        var readTimeout = 0
        override fun get(uri: URI, connectTimeoutMillis: Int, readTimeoutMillis: Int): HttpReply {
            requested += uri.toString()
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            val reply = (routes[uri.toString()] ?: error("Unexpected request to $uri"))() as FakeReply
            replies += reply
            return reply
        }
    }

    private fun address(text: String): InetAddress =
        InetAddress.getByAddress(text.split('.').map { it.toInt().toByte() }.toByteArray())

    /** Every host is public unless it is listed. */
    private fun resolver(privateHosts: Set<String> = emptySet()) = HostResolver { host ->
        if (host in privateHosts) listOf(address("10.0.0.7")) else listOf(address("93.184.216.34"))
    }

    private fun image(type: String? = "image/png", body: ByteArray = png) = {
        FakeReply(200, if (type == null) emptyMap() else mapOf("Content-Type" to type), body)
    }

    private fun redirect(status: Int, to: String?) = {
        FakeReply(status, if (to == null) emptyMap() else mapOf("Location" to to))
    }

    private fun failure(result: PhotoDownloadResult): PhotoDownloadFailure =
        (result as PhotoDownloadResult.Failure).reason

    @Test
    fun downloadsAnImageAndClosesTheReply() {
        val web = FakeWeb(mapOf("https://example.com/a.png" to image()))
        val result = PhotoDownloader(resolver(), web).download("https://example.com/a.png")

        assertArrayEquals(png, (result as PhotoDownloadResult.Success).bytes)
        assertTrue(web.replies.single().closed)
        assertEquals(PhotoDownloader.CONNECT_TIMEOUT_MILLIS, web.connectTimeout)
        assertEquals(PhotoDownloader.READ_TIMEOUT_MILLIS, web.readTimeout)
    }

    @Test
    fun acceptsParametersMissingTypeAndOctetStream() {
        listOf(
            image("image/png; charset=binary"),
            image(null),
            image("application/octet-stream", webp),
            image("IMAGE/WEBP", webp),
        ).forEach { route ->
            val web = FakeWeb(mapOf("https://example.com/a" to route))
            assertTrue(PhotoDownloader(resolver(), web).download("https://example.com/a") is PhotoDownloadResult.Success)
        }
    }

    @Test
    fun invalidLinksNeverReachTheNetwork() {
        val web = FakeWeb(emptyMap())
        var lookups = 0
        val counting = HostResolver { lookups++; listOf(address("93.184.216.34")) }
        listOf(
            "http://example.com/a.png",
            "https://127.0.0.1/a.png",
            "https://user:pw@example.com/a.png",
            "https://example.com:8443/a.png",
            "javascript:alert(1)",
            "",
        ).forEach {
            assertEquals(it, PhotoDownloadFailure.INVALID_LINK, failure(PhotoDownloader(counting, web).download(it)))
        }
        assertEquals(0, lookups)
        assertTrue(web.requested.isEmpty())
    }

    @Test
    fun refusesHostsThatResolveToPrivateAddressesBeforeConnecting() {
        val web = FakeWeb(emptyMap())
        val result = PhotoDownloader(resolver(setOf("evil.example")), web).download("https://evil.example/a.png")

        assertEquals(PhotoDownloadFailure.UNSAFE_ADDRESS, failure(result))
        assertTrue(web.requested.isEmpty())
    }

    @Test
    fun refusesWhenAnyResolvedAddressIsPrivate() {
        val mixed = HostResolver { listOf(address("93.184.216.34"), address("192.168.1.10")) }
        val web = FakeWeb(emptyMap())

        assertEquals(
            PhotoDownloadFailure.UNSAFE_ADDRESS,
            failure(PhotoDownloader(mixed, web).download("https://example.com/a.png")),
        )
        assertTrue(web.requested.isEmpty())
    }

    @Test
    fun reportsLookupFailures() {
        val web = FakeWeb(emptyMap())
        val unknown = HostResolver { throw UnknownHostException("nope") }
        val empty = HostResolver { emptyList() }

        assertEquals(PhotoDownloadFailure.DNS_FAILED, failure(PhotoDownloader(unknown, web).download("https://example.com/a.png")))
        assertEquals(PhotoDownloadFailure.DNS_FAILED, failure(PhotoDownloader(empty, web).download("https://example.com/a.png")))
    }

    @Test
    fun followsUpToThreeRedirectsIncludingRelativeOnes() {
        val web = FakeWeb(
            mapOf(
                "https://a.example/1.png" to redirect(302, "https://b.example/2.png"),
                "https://b.example/2.png" to redirect(301, "/3.png"),
                "https://b.example/3.png" to redirect(308, "https://c.example/4.png"),
                "https://c.example/4.png" to image(),
            ),
        )
        val result = PhotoDownloader(resolver(), web).download("https://a.example/1.png")

        assertTrue(result is PhotoDownloadResult.Success)
        assertEquals(
            listOf(
                "https://a.example/1.png",
                "https://b.example/2.png",
                "https://b.example/3.png",
                "https://c.example/4.png",
            ),
            web.requested,
        )
        assertTrue(web.replies.all { it.closed })
        assertFalse(web.replies.first().bodyOpened)
    }

    @Test
    fun stopsAfterThreeRedirects() {
        val web = FakeWeb(
            mapOf(
                "https://a.example/1.png" to redirect(302, "https://a.example/2.png"),
                "https://a.example/2.png" to redirect(302, "https://a.example/3.png"),
                "https://a.example/3.png" to redirect(302, "https://a.example/4.png"),
                "https://a.example/4.png" to redirect(302, "https://a.example/5.png"),
            ),
        )

        assertEquals(
            PhotoDownloadFailure.TOO_MANY_REDIRECTS,
            failure(PhotoDownloader(resolver(), web).download("https://a.example/1.png")),
        )
        assertEquals(4, web.requested.size)
    }

    @Test
    fun refusesUnsafeRedirectTargets() {
        listOf(
            "http://b.example/a.png",
            "https://169.254.169.254/latest/meta-data",
            "https://user:pw@b.example/a.png",
            "https://b.example:8443/a.png",
            "file:///sdcard/a.png",
            "javascript:alert(1)",
            "https://b.example/a b.png",
        ).forEach { target ->
            val web = FakeWeb(mapOf("https://a.example/1.png" to redirect(302, target)))
            assertEquals(
                target,
                PhotoDownloadFailure.BAD_REDIRECT,
                failure(PhotoDownloader(resolver(), web).download("https://a.example/1.png")),
            )
            assertEquals(1, web.requested.size)
        }
    }

    @Test
    fun refusesARedirectToAHostThatResolvesToAPrivateAddress() {
        val web = FakeWeb(mapOf("https://a.example/1.png" to redirect(302, "https://internal-looking.example/a.png")))
        val result = PhotoDownloader(resolver(setOf("internal-looking.example")), web)
            .download("https://a.example/1.png")

        assertEquals(PhotoDownloadFailure.UNSAFE_ADDRESS, failure(result))
        assertEquals(listOf("https://a.example/1.png"), web.requested)
    }

    @Test
    fun aRedirectWithoutALocationFails() {
        val web = FakeWeb(mapOf("https://a.example/1.png" to redirect(302, null)))
        assertEquals(
            PhotoDownloadFailure.BAD_REDIRECT,
            failure(PhotoDownloader(resolver(), web).download("https://a.example/1.png")),
        )
    }

    @Test
    fun nonSuccessStatusCodesFail() {
        listOf(204, 304, 401, 403, 404, 429, 500, 503).forEach { status ->
            val web = FakeWeb(mapOf("https://a.example/1.png" to { FakeReply(status) }))
            assertEquals(
                status.toString(),
                PhotoDownloadFailure.HTTP_ERROR,
                failure(PhotoDownloader(resolver(), web).download("https://a.example/1.png")),
            )
            assertTrue(web.replies.single().closed)
        }
    }

    @Test
    fun webPagesAndOtherTypesAreRejectedWithoutReadingTheBody() {
        listOf("text/html; charset=utf-8", "image/svg+xml", "image/gif", "application/json", "text/plain").forEach { type ->
            val reply = FakeReply(200, mapOf("Content-Type" to type), png)
            val web = FakeWeb(mapOf("https://a.example/1.png" to { reply }))
            assertEquals(
                type,
                PhotoDownloadFailure.NOT_AN_IMAGE,
                failure(PhotoDownloader(resolver(), web).download("https://a.example/1.png")),
            )
            assertFalse(type, reply.bodyOpened)
        }
    }

    @Test
    fun theFirstBytesMustBeAPictureEvenWhenTheTypeSaysImage() {
        val html = "<html><script>alert(1)</script></html>".toByteArray()
        val svg = "<svg xmlns='http://www.w3.org/2000/svg'/>".toByteArray()
        listOf(html, svg, ByteArray(0), byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)).forEach { body ->
            val web = FakeWeb(mapOf("https://a.example/1.png" to image("image/png", body)))
            assertEquals(
                PhotoDownloadFailure.NOT_AN_IMAGE,
                failure(PhotoDownloader(resolver(), web).download("https://a.example/1.png")),
            )
        }
    }

    @Test
    fun rejectsADeclaredLengthOverTheLimitWithoutReading() {
        val reply = FakeReply(
            200,
            mapOf("Content-Type" to "image/png", "Content-Length" to (PhotoDownloader.MAX_BYTES + 1L).toString()),
            png,
        )
        val web = FakeWeb(mapOf("https://a.example/1.png" to { reply }))

        assertEquals(
            PhotoDownloadFailure.TOO_LARGE,
            failure(PhotoDownloader(resolver(), web).download("https://a.example/1.png")),
        )
        assertFalse(reply.bodyOpened)
    }

    @Test
    fun aNegativeOrJunkContentLengthIsIgnoredSafely() {
        listOf("-5", "abc", "").forEach { length ->
            val reply = FakeReply(200, mapOf("Content-Type" to "image/png", "Content-Length" to length), png)
            val web = FakeWeb(mapOf("https://a.example/1.png" to { reply }))
            assertTrue(length, PhotoDownloader(resolver(), web).download("https://a.example/1.png") is PhotoDownloadResult.Success)
        }
    }

    @Test
    fun stopsReadingAServerThatNeverStopsSending() {
        var delivered = 0L
        val endless = object : InputStream() {
            override fun read(): Int = png[0].toInt() and 0xFF
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                for (index in 0 until length) buffer[offset + index] = png[index % png.size]
                delivered += length
                return length
            }
        }
        val reply = FakeReply(200, mapOf("Content-Type" to "image/png"), bodyStream = { endless })
        val web = FakeWeb(mapOf("https://a.example/1.png" to { reply }))

        val result = PhotoDownloader(resolver(), web).download("https://a.example/1.png")

        assertEquals(PhotoDownloadFailure.TOO_LARGE, failure(result))
        assertTrue(delivered <= PhotoDownloader.MAX_BYTES + 8 * 1024)
        assertTrue(reply.closed)
    }

    @Test
    fun mapsTimeoutsAndNetworkErrors() {
        val timeout = HttpFetch { _, _, _ -> throw SocketTimeoutException("slow") }
        val broken = HttpFetch { _, _, _ -> throw IOException("reset") }
        val surprise = HttpFetch { _, _, _ -> throw IllegalStateException("bug") }
        assertEquals(PhotoDownloadFailure.TIMED_OUT, failure(PhotoDownloader(resolver(), timeout).download("https://a.example/1.png")))
        assertEquals(PhotoDownloadFailure.NETWORK_ERROR, failure(PhotoDownloader(resolver(), broken).download("https://a.example/1.png")))
        assertEquals(PhotoDownloadFailure.NETWORK_ERROR, failure(PhotoDownloader(resolver(), surprise).download("https://a.example/1.png")))

        val slowBody = FakeReply(200, mapOf("Content-Type" to "image/png"), bodyStream = {
            object : InputStream() {
                override fun read(): Int = throw SocketTimeoutException("slow body")
            }
        })
        val web = FakeWeb(mapOf("https://a.example/1.png" to { slowBody }))
        assertEquals(PhotoDownloadFailure.TIMED_OUT, failure(PhotoDownloader(resolver(), web).download("https://a.example/1.png")))
        assertTrue(slowBody.closed)
    }

    @Test
    fun givesUpWhenTheWholeDownloadTakesTooLong() {
        var now = 0L
        val reply = FakeReply(200, mapOf("Content-Type" to "image/png"), bodyStream = {
            object : InputStream() {
                override fun read(): Int = 1
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    now += PhotoDownloader.TOTAL_TIMEOUT_NANOS + 1
                    buffer[offset] = 1
                    return 1
                }
            }
        })
        val web = FakeWeb(mapOf("https://a.example/1.png" to { reply }))

        val result = PhotoDownloader(resolver(), web, nanoTime = { now }).download("https://a.example/1.png")

        assertEquals(PhotoDownloadFailure.TIMED_OUT, failure(result))
    }

    @Test
    fun cancellationStopsBeforeAndDuringTheTransfer() {
        val web = FakeWeb(mapOf("https://a.example/1.png" to image()))
        assertEquals(
            PhotoDownloadFailure.CANCELLED,
            failure(PhotoDownloader(resolver(), web).download("https://a.example/1.png") { true }),
        )
        assertTrue(web.requested.isEmpty())

        var checks = 0
        val reply = FakeReply(200, mapOf("Content-Type" to "image/png"), png)
        val second = FakeWeb(mapOf("https://a.example/1.png" to { reply }))
        val result = PhotoDownloader(resolver(), second).download("https://a.example/1.png") { ++checks > 1 }
        assertEquals(PhotoDownloadFailure.CANCELLED, failure(result))
        assertTrue(reply.closed)
    }
}
