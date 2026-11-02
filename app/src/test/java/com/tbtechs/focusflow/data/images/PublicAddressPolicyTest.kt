package com.tbtechs.focusflow.data.images

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicAddressPolicyTest {
    private fun v4(text: String): InetAddress =
        InetAddress.getByAddress(text.split('.').map { it.toInt().toByte() }.toByteArray())

    private fun v6(vararg groups: Int): InetAddress {
        val bytes = ByteArray(16)
        groups.forEachIndexed { index, group ->
            bytes[index * 2] = (group shr 8).toByte()
            bytes[index * 2 + 1] = group.toByte()
        }
        return InetAddress.getByAddress(bytes)
    }

    @Test
    fun acceptsOrdinaryPublicAddresses() {
        listOf(
            "8.8.8.8", "1.1.1.1", "93.184.216.34", "11.0.0.1", "126.255.255.255", "128.0.0.1",
            "172.15.255.255", "172.32.0.1", "100.63.255.255", "100.128.0.1", "169.253.255.255",
            "169.255.0.1", "192.0.1.1", "192.169.0.1", "198.17.255.255", "198.20.0.1",
            "223.255.255.254",
        ).forEach { assertTrue(it, PublicAddressPolicy.isPublic(v4(it))) }
        assertTrue(PublicAddressPolicy.isPublic(v6(0x2606, 0x4700, 0x4700, 0, 0, 0, 0, 0x1111)))
        assertTrue(PublicAddressPolicy.isPublic(v6(0x2001, 0x4860, 0x4860, 0, 0, 0, 0, 0x8888)))
        assertTrue(PublicAddressPolicy.isPublic(v6(0x2a00, 0x1450, 0x4001, 0x081b, 0, 0, 0, 0x200e)))
        assertTrue(PublicAddressPolicy.isPublic(v6(0x2001, 0x0200, 0, 0, 0, 0, 0, 1)))
        // 6to4 wrapping a public IPv4 address (8.8.8.8).
        assertTrue(PublicAddressPolicy.isPublic(v6(0x2002, 0x0808, 0x0808, 0, 0, 0, 0, 0)))
    }

    @Test
    fun refusesPrivateLocalAndReservedIpv4() {
        listOf(
            "0.0.0.0", "0.1.2.3", "10.0.0.1", "10.255.255.255", "100.64.0.1", "100.127.255.255",
            "127.0.0.1", "127.255.255.254", "169.254.169.254", "172.16.0.1", "172.31.255.255",
            "192.0.0.1", "192.0.2.1", "192.168.0.1", "198.18.0.1", "198.19.255.255",
            "198.51.100.7", "203.0.113.9", "224.0.0.1", "239.255.255.250", "240.0.0.1",
            "255.255.255.255",
        ).forEach { assertFalse(it, PublicAddressPolicy.isPublic(v4(it))) }
    }

    @Test
    fun refusesPrivateLocalAndReservedIpv6() {
        assertFalse(PublicAddressPolicy.isPublic(v6(0, 0, 0, 0, 0, 0, 0, 1))) // ::1
        assertFalse(PublicAddressPolicy.isPublic(v6(0, 0, 0, 0, 0, 0, 0, 0))) // ::
        assertFalse(PublicAddressPolicy.isPublic(v6(0xfe80, 0, 0, 0, 0, 0, 0, 1)))
        assertFalse(PublicAddressPolicy.isPublic(v6(0xfec0, 0, 0, 0, 0, 0, 0, 1)))
        assertFalse(PublicAddressPolicy.isPublic(v6(0xfc00, 0, 0, 0, 0, 0, 0, 1)))
        assertFalse(PublicAddressPolicy.isPublic(v6(0xfd12, 0x3456, 0x789a, 0, 0, 0, 0, 1)))
        assertFalse(PublicAddressPolicy.isPublic(v6(0xff02, 0, 0, 0, 0, 0, 0, 1)))
        assertFalse(PublicAddressPolicy.isPublic(v6(0, 0, 0, 0, 0, 0xffff, 0x0a00, 0x0001))) // ::ffff:10.0.0.1
        assertFalse(PublicAddressPolicy.isPublic(v6(0x0064, 0xff9b, 0, 0, 0, 0, 0x0808, 0x0808))) // NAT64
        assertFalse(PublicAddressPolicy.isPublic(v6(0x2001, 0x0db8, 0, 0, 0, 0, 0, 1)))
        assertFalse(PublicAddressPolicy.isPublic(v6(0x2001, 0x0000, 0, 0, 0, 0, 0, 1))) // Teredo
        assertFalse(PublicAddressPolicy.isPublic(v6(0x2001, 0x0002, 0, 0, 0, 0, 0, 1)))
        assertFalse(PublicAddressPolicy.isPublic(v6(0x2002, 0x7f00, 0x0001, 0, 0, 0, 0, 0))) // 6to4 of 127.0.0.1
        assertFalse(PublicAddressPolicy.isPublic(v6(0x2002, 0x0a00, 0x0001, 0, 0, 0, 0, 0))) // 6to4 of 10.0.0.1
    }
}
