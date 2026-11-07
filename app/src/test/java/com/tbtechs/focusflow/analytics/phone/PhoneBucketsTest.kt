package com.tbtechs.focusflow.analytics.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneBucketsTest {
    @Test
    fun bucketZeroIsUnder30Seconds() {
        assertEquals(0, PhoneBuckets.indexFor(0L))
        assertEquals(0, PhoneBuckets.indexFor(29_999L))
    }

    @Test
    fun bucketOneIs30SecondsToUnderAMinute() {
        assertEquals(1, PhoneBuckets.indexFor(30_000L))
        assertEquals(1, PhoneBuckets.indexFor(59_999L))
    }

    @Test
    fun bucketTwoIsOneToUnderFiveMinutes() {
        assertEquals(2, PhoneBuckets.indexFor(60_000L))
        assertEquals(2, PhoneBuckets.indexFor(299_999L))
    }

    @Test
    fun bucketThreeIsFiveToUnderTwentyMinutes() {
        assertEquals(3, PhoneBuckets.indexFor(300_000L))
        assertEquals(3, PhoneBuckets.indexFor(1_199_999L))
    }

    @Test
    fun bucketFourIsTwentyMinutesAndMore() {
        assertEquals(4, PhoneBuckets.indexFor(1_200_000L))
        assertEquals(4, PhoneBuckets.indexFor(9_999_999L))
    }

    @Test
    fun negativeDurationFallsInBucketZero() {
        assertEquals(0, PhoneBuckets.indexFor(-1L))
        assertEquals(0, PhoneBuckets.indexFor(Long.MIN_VALUE))
    }

    @Test
    fun labelsMatchBucketCount() {
        assertEquals(PhoneBuckets.COUNT, PhoneBuckets.LABELS.size)
        assertEquals(PhoneBuckets.COUNT - 1, PhoneBuckets.BOUNDS_MS.size)
    }
}
