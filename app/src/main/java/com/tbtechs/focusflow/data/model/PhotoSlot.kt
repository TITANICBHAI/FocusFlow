package com.tbtechs.focusflow.data.model

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * A picture an item or step still needs. It holds no image data.
 *
 * - [brief]: one sentence that says what the picture should show.
 * - [url]: optional https link FocusFlow may download the picture from when the
 *   user asks. The link is never opened in a browser or WebView.
 * - [alt]: optional short description used once a picture is attached.
 *
 * A slot never replaces a stored [SubtaskImage]. [SubtaskNormalizer] clears the
 * slot as soon as the same item or step has an image.
 */
@Serializable
data class PhotoSlot(
    val brief: String? = null,
    val url: String? = null,
    val alt: String? = null,
)

/** A slot that stands in for a picture that is gone, described by its alt text (if it had one). */
fun SubtaskImage?.asPhotoSlot(): PhotoSlot? =
    this?.alt?.takeIf { it.isNotBlank() }?.let { PhotoSlot(brief = it, alt = it) }

sealed interface PhotoLinkCheck {
    data class Accepted(val uri: URI) : PhotoLinkCheck
    data class Rejected(val reason: String) : PhotoLinkCheck
}

/**
 * Syntax-only rules for a photo link. This never touches the network.
 * The downloader applies the same rules again to the link and to every redirect,
 * and separately refuses hosts that resolve to private addresses.
 */
object PhotoLinkPolicy {
    private const val HTTPS_DEFAULT_PORT = 443
    private const val MAX_HOST_CHARS = 253
    private val dnsLabel = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")
    private val blockedLastLabels = setOf(
        "local", "localhost", "internal", "lan", "home", "corp", "intranet", "invalid", "onion",
    )

    fun check(raw: String?): PhotoLinkCheck {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return reject("The link is empty.")
        if (text.length > SubtaskLimits.MAX_URL_CHARS) return reject("The link is too long.")
        if (text.any { it.code < 0x21 || it.code > 0x7E }) {
            return reject("The link has spaces, control characters or non-ASCII characters.")
        }
        val uri = try {
            URI(text)
        } catch (_: URISyntaxException) {
            return reject("The link is not a valid web address.")
        }
        if (!"https".equals(uri.scheme, ignoreCase = true)) {
            return reject("Only https links are accepted.")
        }
        if (uri.rawUserInfo != null) return reject("Links with a user name or password are not accepted.")
        if (uri.rawFragment != null) return reject("Links with a # fragment are not accepted.")
        if (uri.port != -1 && uri.port != HTTPS_DEFAULT_PORT) {
            return reject("Links with a custom port are not accepted.")
        }
        val host = uri.host ?: return reject("The link has no usable host name.")
        if (!isPlainPublicName(host)) {
            return reject("The link must use a normal website name, not an IP address or a local name.")
        }
        return PhotoLinkCheck.Accepted(uri)
    }

    private fun reject(reason: String): PhotoLinkCheck = PhotoLinkCheck.Rejected(reason)

    /** At least two DNS labels, and the last one starts with a letter (so no 127.1 or 2130706433). */
    private fun isPlainPublicName(host: String): Boolean {
        if (host.length > MAX_HOST_CHARS) return false
        val labels = host.split('.')
        if (labels.size < 2) return false
        if (!labels.all { dnsLabel.matches(it) }) return false
        val last = labels.last().lowercase(Locale.ROOT)
        if (last[0] !in 'a'..'z') return false
        return last !in blockedLastLabels
    }
}
