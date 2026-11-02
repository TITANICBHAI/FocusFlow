package com.tbtechs.focusflow.data.images

import com.tbtechs.focusflow.data.model.PhotoLinkCheck
import com.tbtechs.focusflow.data.model.PhotoLinkPolicy
import com.tbtechs.focusflow.data.model.SubtaskLimits
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.Locale

/** Looks up the addresses of a host name. Tests replace it with a fake. */
fun interface HostResolver {
    @Throws(IOException::class)
    fun resolve(host: String): List<InetAddress>
}

/** Sends one plain GET request and returns the reply. Tests replace it with a fake. */
fun interface HttpFetch {
    @Throws(IOException::class)
    fun get(uri: URI, connectTimeoutMillis: Int, readTimeoutMillis: Int): HttpReply
}

interface HttpReply : Closeable {
    val status: Int
    fun header(name: String): String?
    fun body(): InputStream
}

enum class PhotoDownloadFailure(val userMessage: String) {
    INVALID_LINK("This is not a plain https link to a public website."),
    UNSAFE_ADDRESS("This link points to a private or local network address, so FocusFlow will not open it."),
    DNS_FAILED("The website could not be found. Check your connection."),
    TOO_MANY_REDIRECTS("The link redirected too many times."),
    BAD_REDIRECT("The link redirected somewhere that is not allowed."),
    HTTP_ERROR("The website did not return a picture."),
    NOT_AN_IMAGE("This is not a direct picture link. It must lead straight to a JPEG, PNG or WebP file."),
    TOO_LARGE("The picture is larger than 6 MiB."),
    TIMED_OUT("The website took too long to answer."),
    NETWORK_ERROR("The connection failed."),
    CANCELLED("Cancelled."),
    UNREADABLE("FocusFlow could not read that picture."),
}

sealed interface PhotoDownloadResult {
    class Success(val bytes: ByteArray) : PhotoDownloadResult
    data class Failure(val reason: PhotoDownloadFailure) : PhotoDownloadResult
}

/**
 * Downloads the bytes behind one photo link, and nothing else. The bytes are only ever handed to
 * [SubtaskImageStore.ingest], which decodes and re-encodes them. Nothing is executed, opened in a
 * browser or shown from the network.
 *
 * Safety rules, all enforced here:
 *  - the link and every redirect must pass [PhotoLinkPolicy] (https, no credentials, port 443);
 *  - every address the host resolves to must pass [PublicAddressPolicy];
 *  - at most [MAX_REDIRECTS] redirects, each followed by hand;
 *  - at most [MAX_BYTES] are read, no matter what the server says;
 *  - the first bytes must be a JPEG, PNG or WebP signature;
 *  - the whole download is limited to [TOTAL_TIMEOUT_NANOS].
 *
 * Known limit: the resolver and the HTTP stack each look the name up once, so a hostile DNS server
 * could answer differently the second time. The reply is still only decoded as a picture.
 */
class PhotoDownloader(
    private val resolver: HostResolver,
    private val fetch: HttpFetch,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    /** Blocking. Call it from a background thread. [isCancelled] is checked between reads. */
    fun download(link: String, isCancelled: () -> Boolean = { false }): PhotoDownloadResult {
        val deadline = nanoTime() + TOTAL_TIMEOUT_NANOS
        var uri = (PhotoLinkPolicy.check(link) as? PhotoLinkCheck.Accepted)?.uri
            ?: return failure(PhotoDownloadFailure.INVALID_LINK)
        var redirects = 0
        while (true) {
            if (isCancelled()) return failure(PhotoDownloadFailure.CANCELLED)
            if (nanoTime() > deadline) return failure(PhotoDownloadFailure.TIMED_OUT)
            checkAddresses(uri.host)?.let { return failure(it) }
            when (val step = fetchOnce(uri, deadline, isCancelled)) {
                is Step.Done -> return step.result
                is Step.Redirect -> {
                    redirects++
                    if (redirects > MAX_REDIRECTS) {
                        return failure(PhotoDownloadFailure.TOO_MANY_REDIRECTS)
                    }
                    uri = resolveRedirect(uri, step.location)
                        ?: return failure(PhotoDownloadFailure.BAD_REDIRECT)
                }
            }
        }
    }

    private sealed interface Step {
        data class Done(val result: PhotoDownloadResult) : Step
        data class Redirect(val location: String) : Step
    }

    private fun checkAddresses(host: String): PhotoDownloadFailure? {
        val addresses = try {
            resolver.resolve(host)
        } catch (_: UnknownHostException) {
            return PhotoDownloadFailure.DNS_FAILED
        } catch (_: IOException) {
            return PhotoDownloadFailure.DNS_FAILED
        } catch (_: RuntimeException) {
            return PhotoDownloadFailure.NETWORK_ERROR
        }
        if (addresses.isEmpty()) return PhotoDownloadFailure.DNS_FAILED
        if (addresses.any { !PublicAddressPolicy.isPublic(it) }) return PhotoDownloadFailure.UNSAFE_ADDRESS
        return null
    }

    private fun fetchOnce(uri: URI, deadline: Long, isCancelled: () -> Boolean): Step {
        val reply = try {
            fetch.get(uri, CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS)
        } catch (_: SocketTimeoutException) {
            return Step.Done(failure(PhotoDownloadFailure.TIMED_OUT))
        } catch (_: IOException) {
            return Step.Done(failure(PhotoDownloadFailure.NETWORK_ERROR))
        } catch (_: RuntimeException) {
            return Step.Done(failure(PhotoDownloadFailure.NETWORK_ERROR))
        }
        return reply.use { current ->
            val status = current.status
            when {
                status in REDIRECT_STATUSES -> {
                    val location = current.header("Location")?.trim()
                    if (location.isNullOrEmpty()) {
                        Step.Done(failure(PhotoDownloadFailure.BAD_REDIRECT))
                    } else {
                        Step.Redirect(location)
                    }
                }
                status == 200 -> Step.Done(readBody(current, deadline, isCancelled))
                else -> Step.Done(failure(PhotoDownloadFailure.HTTP_ERROR))
            }
        }
    }

    private fun resolveRedirect(base: URI, location: String): URI? {
        if (location.length > SubtaskLimits.MAX_URL_CHARS) return null
        val resolved = try {
            base.resolve(location)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return (PhotoLinkPolicy.check(resolved.toString()) as? PhotoLinkCheck.Accepted)?.uri
    }

    private fun readBody(
        reply: HttpReply,
        deadline: Long,
        isCancelled: () -> Boolean,
    ): PhotoDownloadResult {
        val declaredLength = reply.header("Content-Length")?.trim()?.toLongOrNull()
        if (declaredLength != null && declaredLength > MAX_BYTES) {
            return failure(PhotoDownloadFailure.TOO_LARGE)
        }
        val contentType = reply.header("Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
        if (contentType != null && contentType !in ACCEPTED_CONTENT_TYPES) {
            return failure(PhotoDownloadFailure.NOT_AN_IMAGE)
        }
        val initialSize = (declaredLength ?: 0L).coerceIn(1_024L, 256L * 1024).toInt()
        val output = ByteArrayOutputStream(initialSize)
        val buffer = ByteArray(8 * 1024)
        try {
            reply.body().use { input ->
                while (true) {
                    if (isCancelled()) return failure(PhotoDownloadFailure.CANCELLED)
                    if (nanoTime() > deadline) return failure(PhotoDownloadFailure.TIMED_OUT)
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (output.size() + count > MAX_BYTES) return failure(PhotoDownloadFailure.TOO_LARGE)
                    output.write(buffer, 0, count)
                }
            }
        } catch (_: SocketTimeoutException) {
            return failure(PhotoDownloadFailure.TIMED_OUT)
        } catch (_: IOException) {
            return failure(PhotoDownloadFailure.NETWORK_ERROR)
        }
        val bytes = output.toByteArray()
        if (!SubtaskImageStore.hasSupportedMagic(bytes)) return failure(PhotoDownloadFailure.NOT_AN_IMAGE)
        return PhotoDownloadResult.Success(bytes)
    }

    private fun failure(reason: PhotoDownloadFailure): PhotoDownloadResult =
        PhotoDownloadResult.Failure(reason)

    companion object {
        const val MAX_BYTES = 6 * 1024 * 1024
        const val MAX_REDIRECTS = 3
        const val CONNECT_TIMEOUT_MILLIS = 8_000
        const val READ_TIMEOUT_MILLIS = 15_000
        const val TOTAL_TIMEOUT_NANOS = 30_000_000_000L
        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
        private val ACCEPTED_CONTENT_TYPES = setOf(
            "image/jpeg",
            "image/jpg",
            "image/pjpeg",
            "image/png",
            "image/webp",
            "application/octet-stream",
            "binary/octet-stream",
        )
    }
}
