package com.tbtechs.focusflow.ui.stats.phone

import com.tbtechs.focusflow.analytics.phone.DayCoverage
import com.tbtechs.focusflow.analytics.phone.DayPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PhoneChartMathTest {
    // ---- barFractions ----------------------------------------------------------------------------------
    @Test fun allZerosGiveZeros() = assertEquals(listOf(0f, 0f, 0f), barFractions(listOf(0, 0, 0)))
    @Test fun emptyListGivesEmptyList() = assertEquals(emptyList<Float>(), barFractions(emptyList<Int>()))
    @Test fun theMaximumIsOne() = assertEquals(listOf(0.5f, 1f, 0.25f), barFractions(listOf(50L, 100L, 25L)))
    @Test fun aTinyPositiveValueIsRaisedToMinVisible() {
        val result = barFractions(listOf(1, 1000))
        assertEquals(0.04f, result[0], 0f)
        assertEquals(1f, result[1], 0f)
    }
    @Test fun minVisibleIsConfigurable() = assertEquals(0.1f, barFractions(listOf(1, 1000), minVisible = 0.1f)[0], 0f)
    @Test fun negativesAreZero() = assertEquals(listOf(0f, 1f), barFractions(listOf(-5, 10)))
    @Test fun allNegativeGivesZeros() = assertEquals(listOf(0f, 0f), barFractions(listOf(-5, -1)))
    @Test fun aSingleValueIsFull() = assertEquals(listOf(1f), barFractions(listOf(7)))
    @Test fun mixedNumberTypesAreAccepted() = assertEquals(listOf(0.5f, 1f, 0.25f), barFractions(listOf(2.5f, 5.0, 1L * 1.25)))
    @Test fun nonFiniteValuesAreTreatedAsZero() = assertEquals(listOf(0f, 1f, 0f), barFractions(listOf(Double.NaN, 4.0, Double.POSITIVE_INFINITY)))
    @Test fun aValueExactlyAtTheFloorStaysThere() = assertEquals(0.04f, barFractions(listOf(4, 100))[0], 0f)

    // ---- dayBarLabel -----------------------------------------------------------------------------------
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun sevenBarsUseWeekdayInitials() {
        val labels = (0 until 7).map { dayBarLabel(monday.plusDays(it.toLong()), 7, it) }
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), labels)
    }

    @Test
    fun fewerThanSevenBarsAlsoUseInitials() {
        assertEquals("M", dayBarLabel(monday, 1, 0))
        assertEquals("S", dayBarLabel(monday.plusDays(6), 3, 2))
    }

    @Test
    fun thirtyBarsShowTheDayNumberEverySeventhAndOnTheLastBar() {
        val start = LocalDate.of(2026, 10, 9)
        fun label(i: Int) = dayBarLabel(start.plusDays(i.toLong()), 30, i)
        for (i in listOf(0, 7, 14, 21, 28, 29)) assertEquals(start.plusDays(i.toLong()).dayOfMonth.toString(), label(i))
        for (i in listOf(1, 3, 6, 8, 13, 20, 27)) assertEquals("", label(i))
    }

    @Test
    fun eightBarsSwitchToDayNumbers() {
        assertEquals("2", dayBarLabel(monday, 8, 0))
        assertEquals("", dayBarLabel(monday.plusDays(1), 8, 1))
        assertEquals("9", dayBarLabel(monday.plusDays(7), 8, 7)) // index 7 is both a 7th bar and the last bar
    }

    // ---- toDayBarPoints --------------------------------------------------------------------------------
    private fun point(date: LocalDate, coverage: DayCoverage, ms: Long = 1_000L) = DayPoint(date, coverage, ms, 0L, 0, 0)

    @Test
    fun missingAndPartialDaysAreDimmed() {
        val days = listOf(
            point(monday, DayCoverage.COMPLETE), point(monday.plusDays(1), DayCoverage.PARTIAL),
            point(monday.plusDays(2), DayCoverage.MISSING), point(monday.plusDays(3), DayCoverage.LIVE),
        )
        assertEquals(listOf(false, true, true, false), toDayBarPoints(days, null).map { it.dimmed })
    }

    @Test
    fun highlightFollowsTheHighestDate() {
        val days = (0 until 3).map { point(monday.plusDays(it.toLong()), DayCoverage.COMPLETE) }
        assertEquals(listOf(false, true, false), toDayBarPoints(days, monday.plusDays(1)).map { it.highlighted })
        assertTrue(toDayBarPoints(days, null).none { it.highlighted })
        assertTrue(toDayBarPoints(days, monday.plusDays(10)).none { it.highlighted })
    }

    @Test
    fun valueIsScreenOnMillisAndLabelsComeFromTheDate() {
        val days = (0 until 7).map { point(monday.plusDays(it.toLong()), DayCoverage.COMPLETE, ms = (it + 1) * 60_000L) }
        val points = toDayBarPoints(days, null)
        assertEquals(60_000f, points[0].value, 0f)
        assertEquals(420_000f, points[6].value, 0f)
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), points.map { it.label })
        assertFalse(points.any { it.dimmed })
    }

    @Test
    fun longRangesUseDayNumberLabels() {
        val days = (0 until 30).map { point(monday.plusDays(it.toLong()), DayCoverage.COMPLETE) }
        val labels = toDayBarPoints(days, null).map { it.label }
        assertEquals("2", labels[0])
        assertEquals("", labels[1])
        assertEquals(monday.plusDays(29).dayOfMonth.toString(), labels[29])
    }

    @Test fun emptyDaysGiveNoPoints() = assertTrue(toDayBarPoints(emptyList(), null).isEmpty())

    // ---- shareBarFraction ------------------------------------------------------------------------------
    @Test
    fun shareBarFractionClampsAndKeepsASliver() {
        assertEquals(0f, shareBarFraction(0f), 0f)
        assertEquals(0f, shareBarFraction(-1f), 0f)
        assertEquals(0f, shareBarFraction(Float.NaN), 0f)
        assertEquals(0.02f, shareBarFraction(0.001f), 0f)
        assertEquals(0.5f, shareBarFraction(0.5f), 0f)
        assertEquals(1f, shareBarFraction(3f), 0f)
    }
}
