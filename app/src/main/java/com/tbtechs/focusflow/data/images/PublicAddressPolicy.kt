package com.tbtechs.focusflow.data.images

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Decides whether a resolved address is an ordinary address on the public internet.
 * Anything private, local, reserved or unknown is refused.
 */
object PublicAddressPolicy {
    fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }
        return when (address) {
            is Inet4Address -> isPublicV4(address.address)
            is Inet6Address -> isPublicV6(address.address)
            else -> false
        }
    }

    private fun isPublicV4(bytes: ByteArray): Boolean {
        val a = bytes[0].toInt() and 0xFF
        val b = bytes[1].toInt() and 0xFF
        val c = bytes[2].toInt() and 0xFF
        return when {
            a == 0 -> false // 0.0.0.0/8 "this network"
            a == 10 -> false // 10.0.0.0/8 private
            a == 100 && (b and 0xC0) == 64 -> false // 100.64.0.0/10 carrier-grade NAT
            a == 127 -> false // loopback
            a == 169 && b == 254 -> false // link-local (cloud metadata lives here)
            a == 172 && (b and 0xF0) == 16 -> false // 172.16.0.0/12 private
            a == 192 && b == 0 && c == 0 -> false // 192.0.0.0/24 protocol assignments
            a == 192 && b == 0 && c == 2 -> false // 192.0.2.0/24 documentation
            a == 192 && b == 168 -> false // 192.168.0.0/16 private
            a == 198 && (b and 0xFE) == 18 -> false // 198.18.0.0/15 benchmarking
            a == 198 && b == 51 && c == 100 -> false // 198.51.100.0/24 documentation
            a == 203 && b == 0 && c == 113 -> false // 203.0.113.0/24 documentation
            a >= 224 -> false // multicast, reserved and broadcast
            else -> true
        }
    }

    private fun isPublicV6(bytes: ByteArray): Boolean {
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        // Only global unicast 2000::/3 is allowed. This refuses ::, ::1, ::ffff:x.x.x.x,
        // 64:ff9b::/96 (NAT64), fc00::/7, fe80::/10 and ff00::/8 in one rule.
        if ((b0 and 0xE0) != 0x20) return false
        // 2001:0000::/23 holds Teredo and other special-purpose ranges.
        if (b0 == 0x20 && b1 == 0x01 && (b2 and 0xFE) == 0x00) return false
        // 2001:db8::/32 documentation.
        if (b0 == 0x20 && b1 == 0x01 && b2 == 0x0D && b3 == 0xB8) return false
        // 6to4 (2002::/16) embeds an IPv4 address in bytes 2..5; that address must be public too.
        if (b0 == 0x20 && b1 == 0x02) return isPublicV4(bytes.copyOfRange(2, 6))
        return true
    }
}
