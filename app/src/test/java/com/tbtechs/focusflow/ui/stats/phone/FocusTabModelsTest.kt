package com.tbtechs.focusflow.ui.stats.phone

import com.tbtechs.focusflow.analytics.phone.BlockedAppRow
import com.tbtechs.focusflow.analytics.phone.FocusTrend
import com.tbtechs.focusflow.analytics.phone.FocusWeekPoint
import com.tbtechs.focusflow.analytics.phone.PhoneReportFixtures
import com.tbtechs.focusflow.analytics.phone.TrendDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FocusTabModelsTest {
    private val base = PhoneReportFixtures.emptyReport().focus
    private val min = 60_000L

    private fun week(i: Int, attempts: Int) = FocusWeekPoint(LocalDate.of(2026, 9, 1).plusWeeks(i.toLong()), 0, 0, 0, attempts)

    @Test fun heldRate() {
        assertNull(base.copy(heldSessions = 0, interruptedSessions = 0).let(::heldRatePercent))
        assertEquals(75, heldRatePercent(base.copy(heldSessions = 3, interruptedSessions = 1)))
        assertEquals(67, heldRatePercent(base.copy(heldSessions = 2, interruptedSessions = 1)))
        assertEquals(0, heldRatePercent(base.copy(heldSessions = 0, interruptedSessions = 4)))
        assertEquals(100, heldRatePercent(base.copy(heldSessions = 4, interruptedSessions = 0)))
    }

    @Test fun durationLabels() {
        assertEquals(listOf("<15m", "15–30m", "30–60m", "1–2h", "2h+"), focusDurationLabels())
        assertEquals(5, focusDurationLabels().size)
    }

    @Test fun blockedRows() {
        val f = base.copy(blockedApps = listOf(BlockedAppRow("a", "Insta", 3, 60.0), BlockedAppRow("b", "Reddit", 1, 20.4)))
        assertEquals(
            listOf(TopAppRow("Insta", "3 attempts", 0.6f, "60%"), TopAppRow("Reddit", "1 attempt", 0.204f, "20%")),
            blockedAppRows(f),
        )
        assertEquals(emptyList<TopAppRow>(), blockedAppRows(base))
    }

    @Test fun blockedRowsClampFraction() {
        assertEquals(1f, blockedAppRows(base.copy(blockedApps = listOf(BlockedAppRow("a", "A", 1, 100.5))))[0].fraction)
    }

    @Test fun weekBarsFull() {
        val f = base.copy(weeks = (0 until 8).map { week(it, it + 1) })
        val pts = weekBarPoints(f)
        assertEquals(8, pts.size)
        assertEquals(listOf("−7w", "−6w", "−5w", "−4w", "−3w", "−2w", "−1w", "Now"), pts.map { it.label })
        assertEquals((1..8).map { it.toFloat() }, pts.map { it.value })
        assertEquals(listOf(false, false, false, false, false, false, false, true), pts.map { it.highlighted })
        assertTrue(pts.none { it.dimmed })
    }

    @Test fun weekBarsPadOlderWeeks() {
        val pts = weekBarPoints(base.copy(weeks = listOf(week(0, 5), week(1, 7))))
        assertEquals(8, pts.size)
        assertEquals(listOf(0f, 0f, 0f, 0f, 0f, 0f, 5f, 7f), pts.map { it.value })
        assertEquals(listOf(true, true, true, true, true, true, false, false), pts.map { it.dimmed })
        assertTrue(pts.last().highlighted)
    }

    @Test fun weekBarsNoWeeksAndTooMany() {
        assertEquals(8, weekBarPoints(base.copy(weeks = emptyList())).size)
        assertTrue(weekBarPoints(base.copy(weeks = emptyList())).all { it.dimmed && it.value == 0f })
        val pts = weekBarPoints(base.copy(weeks = (0 until 10).map { week(it, it) }))
        assertEquals((2..9).map { it.toFloat() }, pts.map { it.value })
    }

    private fun trend(d: TrendDirection, now: Double, before: Double, pct: Int) =
        base.copy(trend = FocusTrend(d, now, before, pct))

    @Test fun trendTexts() {
        assertEquals(
            "Not enough focus time yet — this needs about an hour of focus in each two-week period.",
            focusTrendText(base.copy(trend = null)),
        )
        assertEquals(
            "Blocked attempts per focus hour fell from 4.0 to 2.5 (−38%) over the last two weeks.",
            focusTrendText(trend(TrendDirection.DOWN, 2.5, 4.0, -38)),
        )
        assertEquals(
            "Blocked attempts per focus hour rose from 2.0 to 3.0 (+50%) over the last two weeks.",
            focusTrendText(trend(TrendDirection.UP, 3.0, 2.0, 50)),
        )
        assertEquals(
            "Blocked attempts per focus hour stayed about the same (2.0 → 2.1).",
            focusTrendText(trend(TrendDirection.FLAT, 2.1, 2.0, 5)),
        )
    }

    @Test fun summaryLine() {
        assertEquals("3 sessions · 2h 10m of focus", focusSummaryLine(base.copy(sessions = 3, focusMs = 130 * min)))
        assertEquals("1 session · 25m of focus", focusSummaryLine(base.copy(sessions = 1, focusMs = 25 * min)))
        assertEquals("0 sessions · 0s of focus", focusSummaryLine(base.copy(sessions = 0, focusMs = 0)))
    }
}
