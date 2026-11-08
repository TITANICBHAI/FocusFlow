package com.tbtechs.focusflow.ui.stats.phone

import com.tbtechs.focusflow.analytics.phone.DistractionApp
import com.tbtechs.focusflow.analytics.phone.FirstAppRow
import com.tbtechs.focusflow.analytics.phone.PhoneBehaviourSection
import com.tbtechs.focusflow.analytics.phone.PhoneReportFixtures
import com.tbtechs.focusflow.analytics.phone.ReflexApp
import com.tbtechs.focusflow.analytics.phone.RisingApp
import com.tbtechs.focusflow.analytics.phone.SessionBasis
import com.tbtechs.focusflow.analytics.phone.UsageCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneTabModelsTest {
    private val base = PhoneReportFixtures.emptyReport()
    private val min = 60_000L

    private fun hours(vararg at: Pair<Int, Int>): List<Int> = MutableList(24) { 0 }.also { l -> at.forEach { (h, v) -> l[h] = v } }

    private fun b(
        unlocks: Int = 0, perDay: Double? = null, wakes: Int = 0, wakesPerDay: Double? = null,
        unlocksByHour: List<Int> = List(24) { 0 }, wakesByHour: List<Int> = List(24) { 0 },
        sessions: Int = 0, quick: Int = 0, quickShare: Int? = null, basis: SessionBasis = SessionBasis.UNLOCK, without: Int = 0,
    ): PhoneBehaviourSection = base.behaviour.copy(
        unlocks = unlocks, unlocksPerDay = perDay, wakes = wakes, wakesPerDay = wakesPerDay,
        unlocksByHour = unlocksByHour, wakesByHour = wakesByHour, sessions = sessions, quickChecks = quick,
        quickCheckSharePercent = quickShare, sessionBasis = basis, unlocksWithoutApp = without,
    )

    // ---- busiestHour ----
    @Test fun busiestHourPicksEarliestMax() {
        assertEquals(9, busiestHour(hours(9 to 5, 14 to 5, 3 to 2)))
        assertNull(busiestHour(hours()))
        assertNull(busiestHour(emptyList()))
    }

    // ---- unlockHourCaption ----
    @Test fun unlockCaptionWithPeak() {
        val x = b(unlocks = 63, perDay = 47.6, unlocksByHour = hours(21 to 9))
        assertEquals("63 unlocks in total · 48 a day · busiest around 9 PM", unlockHourCaption(x, showWakes = false))
    }

    @Test fun unlockCaptionNoPeakNoPerDay() {
        assertEquals("1 unlock in total · — a day", unlockHourCaption(b(unlocks = 1), false))
    }

    @Test fun wakeCaptionUsesWakeFields() {
        val x = b(unlocks = 99, perDay = 99.0, wakes = 30, wakesPerDay = 10.4, wakesByHour = hours(0 to 4), unlocksByHour = hours(5 to 50))
        assertEquals("30 wake-ups in total · 10 a day · busiest around 12 AM", unlockHourCaption(x, true))
    }

    @Test fun wakeCaptionSingular() {
        assertEquals("1 wake-up in total · 1 a day", unlockHourCaption(b(wakes = 1, wakesPerDay = 1.0), true))
    }

    // ---- session ----
    @Test fun sessionHeadline() {
        assertEquals("12 quick checks — 40% of 30 sessions", sessionHeadline(b(sessions = 30, quick = 12, quickShare = 40)))
        assertEquals("1 quick check — 100% of 1 session", sessionHeadline(b(sessions = 1, quick = 1, quickShare = 100)))
        assertEquals("No sessions recorded", sessionHeadline(b(sessions = 0, quick = 0, quickShare = null)))
    }

    @Test fun sessionHeadlineComputesShareWhenMissing() {
        assertEquals("1 quick check — 33% of 3 sessions", sessionHeadline(b(sessions = 3, quick = 1, quickShare = null)))
    }

    @Test fun sessionBasisNotes() {
        assertEquals("Sessions run from unlock to screen-off.", sessionBasisNote(b(basis = SessionBasis.UNLOCK)))
        assertEquals(
            "This phone doesn't report unlocks, so sessions are counted from screen-on to screen-off.",
            sessionBasisNote(b(basis = SessionBasis.SCREEN)),
        )
    }

    // ---- first app ----
    @Test fun firstAppSubtitleRounding() {
        assertEquals("12 unlocks · 2.5 a day", firstAppSubtitle(FirstAppRow("p", "n", 12, 20.0, 2.45)))
        assertEquals("1 unlock · 0.3 a day", firstAppSubtitle(FirstAppRow("p", "n", 1, 5.0, 0.25)))
        assertEquals("3 unlocks · 3.0 a day", firstAppSubtitle(FirstAppRow("p", "n", 3, 5.0, 3.0)))
    }

    @Test fun noAppRow() {
        val row = noAppRow(b(unlocks = 40, without = 10))!!
        assertEquals(TopAppRow("Nothing opened", "checked the time or notifications", 0.25f, "25%"), row)
        assertNull(noAppRow(b(unlocks = 40, without = 0)))
        assertNull(noAppRow(b(unlocks = 0, without = 5)))
    }

    @Test fun noAppRowRoundsAndClamps() {
        assertEquals("33%", noAppRow(b(unlocks = 3, without = 1))!!.trailing)
        assertEquals(1f, noAppRow(b(unlocks = 2, without = 5))!!.fraction)
    }

    // ---- distraction ----
    @Test fun distractionHeadline() {
        val d = base.distraction
        assertEquals("1h 30m — 42% of your app time", distractionHeadline(d.copy(distractionMs = 90 * min, sharePercent = 42)))
        assertEquals("1h 30m", distractionHeadline(d.copy(distractionMs = 90 * min, sharePercent = null)))
        assertEquals("0s — 0% of your app time", distractionHeadline(d.copy(distractionMs = 0, sharePercent = 0)))
    }

    @Test fun distractionChange() {
        val d = base.distraction
        assertEquals("+35% vs the previous period", distractionChangeText(d.copy(changePercent = 35)))
        assertEquals("−12% vs the previous period", distractionChangeText(d.copy(changePercent = -12)))
        assertEquals("0% vs the previous period", distractionChangeText(d.copy(changePercent = 0)))
        assertNull(distractionChangeText(d.copy(changePercent = null)))
    }

    @Test fun distractionSubtitleJoinsReasons() {
        fun a(r: List<String>) = DistractionApp("p", "n", UsageCategory.SOCIAL, 0, 0.0, 0, 0, r)
        assertEquals("Social media · 3 blocked attempts", distractionSubtitle(a(listOf("Social media", "3 blocked attempts"))))
        assertEquals("Games", distractionSubtitle(a(listOf("Games"))))
        assertEquals("", distractionSubtitle(a(emptyList())))
    }

    @Test fun reflexSubtitle() {
        assertEquals("14 opens a day · 80% under 30 s", reflexSubtitle(ReflexApp("p", "n", 13.6, 80)))
        assertEquals("1 open a day · 0% under 30 s", reflexSubtitle(ReflexApp("p", "n", 1.2, 0)))
    }

    @Test fun risingSubtitle() {
        assertEquals("1h 12m/day vs 48m/day (+50%)", risingSubtitle(RisingApp("p", "n", 72 * min, 48 * min, 50)))
        assertEquals("10m/day vs 20m/day (−50%)", risingSubtitle(RisingApp("p", "n", 10 * min, 20 * min, -50)))
    }

    @Test fun oneDecimalIsLocaleIndependent() {
        assertEquals("2.0", oneDecimal(2.0))
        assertEquals("0.1", oneDecimal(0.14))
        assertEquals("1234.6", oneDecimal(1234.56))
    }
}
