package com.tbtechs.focusflow.data.images

import java.io.IOException
import java.io.InputStream
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/**
 * One plain https GET with the platform HTTP stack. It sends no cookies, no referrer and no
 * credentials, never follows redirects by itself and never caches.
 */
class UrlConnectionFetch : HttpFetch {
    override fun get(uri: URI, connectTimeoutMillis: Int, readTimeoutMillis: Int): HttpReply {
        val connection = uri.toURL().openConnection() as? HttpsURLConnection
            ?: throw IOException("Only https connections are allowed.")
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.doInput = true
            connection.doOutput = false
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Accept", "image/webp,image/png,image/jpeg")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            return Reply(connection, connection.responseCode)
        } catch (exception: IOException) {
            connection.disconnect()
            throw exception
        }
    }

    private class Reply(
        private val connection: HttpsURLConnection,
        override val status: Int,
    ) : HttpReply {
        override fun header(name: String): String? = connection.getHeaderField(name)
        override fun body(): InputStream = connection.inputStream
        override fun close() = connection.disconnect()
    }

    private companion object {
        const val USER_AGENT = "FocusFlow-Android (photo download requested by the user)"
    }
}
