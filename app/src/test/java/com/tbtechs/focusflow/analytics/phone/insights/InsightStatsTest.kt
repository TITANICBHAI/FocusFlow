package com.tbtechs.focusflow.analytics.phone.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightStatsTest {
    @Test fun medianOfOddCountIsTheMiddleValue() = assertEquals(5L, InsightStats.median(listOf(9L, 1L, 5L)))
    @Test fun medianOfEvenCountAveragesTheTwoMiddleValuesWithIntegerDivision() {
        assertEquals(2L, InsightStats.median(listOf(10L, 3L, 1L, 2L)))
        assertEquals(3L, InsightStats.median(listOf(2L, 5L)))
    }
    @Test fun medianOfEmptyIsZero() = assertEquals(0L, InsightStats.median(emptyList()))
    @Test fun medianIgnoresInputOrder() = assertEquals(InsightStats.median(listOf(1L, 2L, 3L, 4L, 5L)), InsightStats.median(listOf(5L, 3L, 1L, 4L, 2L)))

    @Test fun meanOfValuesAndOfEmpty() {
        assertEquals(2.5, InsightStats.mean(listOf(1L, 2L, 3L, 4L)), 0.0)
        assertEquals(0.0, InsightStats.mean(emptyList()), 0.0)
    }

    @Test fun stdevIsThePopulationStandardDeviation() {
        assertEquals(2.0, InsightStats.stdev(listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)), 1e-12)
        assertEquals(0.0, InsightStats.stdev(listOf(7.0, 7.0, 7.0)), 0.0)
        assertEquals(0.0, InsightStats.stdev(emptyList()), 0.0)
    }

    @Test fun percentChangeRoundsHalfUp() {
        assertEquals(5, InsightStats.percentChange(105.0, 100.0))
        assertEquals(1, InsightStats.percentChange(100.5, 100.0))
        assertEquals(0, InsightStats.percentChange(100.4, 100.0))
        assertEquals(-12, InsightStats.percentChange(87.5, 100.0)) // −12.5 rounds up to −12
        assertEquals(-13, InsightStats.percentChange(87.4, 100.0))
        assertEquals(100, InsightStats.percentChange(60.0, 30.0))
    }

    @Test fun percentChangeIsNullWhenBeforeIsZero() {
        assertNull(InsightStats.percentChange(10.0, 0.0))
        assertNull(InsightStats.percentChange(0.0, 0.0))
    }

    @Test fun roundHalfUpHandlesNegativesAndExactHalves() {
        assertEquals(0, InsightStats.roundHalfUp(-0.5))
        assertEquals(1, InsightStats.roundHalfUp(0.5))
        assertEquals(-1, InsightStats.roundHalfUp(-0.6))
    }
}
