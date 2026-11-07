package com.tbtechs.focusflow.analytics.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneCodecTest {
    @Test
    fun roundTripOf24Longs() {
        val values = List(24) { it * 1_000_003L }
        assertEquals(values, PhoneCodec.decodeLongs(PhoneCodec.encodeLongs(values), 24))
    }

    @Test
    fun roundTripOfInts() {
        val values = List(24) { it * 7 }
        assertEquals(values, PhoneCodec.decodeInts(PhoneCodec.encodeInts(values), 24))
    }

    @Test
    fun nullAndEmptyDecodeToZeros() {
        assertEquals(List(24) { 0L }, PhoneCodec.decodeLongs(null, 24))
        assertEquals(List(24) { 0L }, PhoneCodec.decodeLongs("", 24))
        assertEquals(List(5) { 0 }, PhoneCodec.decodeInts(null, 5))
        assertEquals(List(5) { 0 }, PhoneCodec.decodeInts("   ", 5))
    }

    @Test
    fun shortInputIsPaddedWithZeros() {
        assertEquals(listOf(1L, 2L, 0L, 0L, 0L), PhoneCodec.decodeLongs("1,2", 5))
        assertEquals(listOf(4, 0, 0), PhoneCodec.decodeInts("4", 3))
    }

    @Test
    fun longInputIsTruncated() {
        assertEquals(listOf(1L, 2L, 3L), PhoneCodec.decodeLongs("1,2,3,4,5", 3))
        assertEquals(listOf(1, 2), PhoneCodec.decodeInts("1,2,3", 2))
    }

    @Test
    fun garbageTokenBecomesZero() {
        assertEquals(listOf(1L, 0L, 3L, 0L), PhoneCodec.decodeLongs("1,x,3", 4))
        assertEquals(listOf(1, 0, 3), PhoneCodec.decodeInts("1,x,3", 3))
    }

    @Test
    fun negativeNumbersSurvive() {
        assertEquals(listOf(-5L, 7L), PhoneCodec.decodeLongs("-5,7", 2))
        assertEquals(listOf(-5, 7), PhoneCodec.decodeInts("-5,7", 2))
    }

    @Test
    fun decodedSizeAlwaysMatchesRequest() {
        assertEquals(24, PhoneCodec.decodeLongs("1,2,3", 24).size)
        assertEquals(0, PhoneCodec.decodeInts("1,2,3", 0).size)
    }
}
