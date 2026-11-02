package com.tbtechs.focusflow.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoLinkPolicyTest {
    private fun accepted(link: String?) = PhotoLinkPolicy.check(link) is PhotoLinkCheck.Accepted

    @Test
    fun acceptsPlainHttpsLinksToPublicWebsites() {
        listOf(
            "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a1/Push-up.jpg/640px-Push-up.jpg",
            "https://images.unsplash.com/photo-123?w=800&q=80",
            "https://example.com:443/a.png",
            "HTTPS://EXAMPLE.COM/A.PNG",
            "  https://example.com/a.webp  ",
            "https://xn--bcher-kva.example/a.jpg",
        ).forEach { assertTrue(it, accepted(it)) }
    }

    @Test
    fun rejectsLinksThatAreNotHttps() {
        listOf(
            "http://example.com/a.png",
            "ftp://example.com/a.png",
            "file:///sdcard/a.png",
            "content://media/external/images/1",
            "javascript:alert(1)",
            "data:image/png;base64,AAAA",
            "//example.com/a.png",
            "example.com/a.png",
        ).forEach { assertFalse(it, accepted(it)) }
    }

    @Test
    fun rejectsCredentialsPortsFragmentsAndOddSyntax() {
        listOf(
            "https://user:pass@example.com/a.png",
            "https://user@example.com/a.png",
            "https://example.com:443@evil.example/a.png",
            "https://good.example\\@evil.example/a.png",
            "https://example.com:8443/a.png",
            "https://example.com:80/a.png",
            "https://example.com/a.png#section",
            "https://example.com/a b.png",
            "https://example.com/a\u0000.png",
            "https://example.com/a\n.png",
            "https://exämple.com/a.png",
            "https://example.com/é.png",
            "https:///a.png",
            "https://",
            "",
            "   ",
        ).forEach { assertFalse(it, accepted(it)) }
        assertFalse(accepted(null))
    }

    @Test
    fun rejectsIpAddressesAndLocalNames() {
        listOf(
            "https://127.0.0.1/a.png",
            "https://127.1/a.png",
            "https://2130706433/a.png",
            "https://0x7f.0x0.0x0.0x1/a.png",
            "https://0177.0.0.1/a.png",
            "https://10.0.0.5/a.png",
            "https://192.168.1.1/a.png",
            "https://169.254.169.254/latest/meta-data",
            "https://8.8.8.8/a.png",
            "https://[::1]/a.png",
            "https://[2606:4700:4700::1111]/a.png",
            "https://localhost/a.png",
            "https://printer.local/a.png",
            "https://router.lan/a.png",
            "https://nas.home/a.png",
            "https://intranet/a.png",
            "https://server.internal/a.png",
            "https://example.invalid/a.png",
            "https://example.com./a.png",
            "https://-bad.example.com/a.png",
            "https://bad-.example.com/a.png",
            "https://exa_mple.com/a.png",
        ).forEach { assertFalse(it, accepted(it)) }
    }

    @Test
    fun enforcesTheLengthLimit() {
        val prefix = "https://example.com/"
        val justFits = prefix + "a".repeat(SubtaskLimits.MAX_URL_CHARS - prefix.length)
        assertEquals(SubtaskLimits.MAX_URL_CHARS, justFits.length)
        assertTrue(accepted(justFits))
        assertFalse(accepted(justFits + "a"))
    }

    @Test
    fun rejectionsExplainWhy() {
        val rejected = PhotoLinkPolicy.check("http://example.com/a.png") as PhotoLinkCheck.Rejected
        assertTrue(rejected.reason.contains("https"))
    }
}
