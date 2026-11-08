package com.tbtechs.focusflow.analytics.phone.insights

import com.tbtechs.focusflow.analytics.phone.AppSessionRow
import com.tbtechs.focusflow.analytics.phone.PhoneDay
import com.tbtechs.focusflow.analytics.phone.PhoneDayStatus
import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures
import com.tbtechs.focusflow.analytics.phone.PhoneText
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.MIN
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.ago
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.appSpan
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.complete
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.gen
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.input
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.ofKind
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordAndAssemblyTest {
    private fun records(input: PhoneInsightInput) = gen(input).ofKind(InsightKind.RECORD).associateBy { it.id }

    /** 60 complete days of 100 min / 20 unlocks, with specific days overridden. */
    private fun sixty(override: (Int) -> PhoneDay? = { null }): List<PhoneDay> =
        span(60, 1) { date -> override(InsightFixtures.today.toEpochDay().minus(date.toEpochDay()).toInt()) ?: complete(date, 100, 20) }

    private fun wd(back: Int) = PhoneText.weekdayDate(ago(back))

    // ---- RECORD ----------------------------------------------------------------------------------------
    @Test
    fun highestAndLowestDayRecords() {
        val days = sixty { back ->
            when (back) {
                3 -> complete(ago(3), 300, 20)
                40 -> complete(ago(40), 10, 20)
                else -> null
            }
        }
        val r = records(input(days))
        val high = r.getValue("record:highest_day")
        assertEquals("Highest screen-time day", high.title)
        assertEquals("5h on ${wd(3)}.", high.message)
        assertTrue(high.isNew)
        assertEquals(45, high.priority)
        assertEquals(InsightGroup.RECORD, high.group)
        val low = r.getValue("record:lowest_day")
        assertEquals("Lowest screen-time day", low.title)
        assertEquals("10m on ${wd(40)}.", low.message)
        assertTrue(!low.isNew)
        assertEquals(40, low.priority)
    }

    @Test
    fun recordTiesGoToTheLaterDate() {
        val r = records(input(sixty()))
        assertEquals("100m".let { "1h 40m" } + " on ${wd(1)}.", r.getValue("record:highest_day").message)
        assertEquals("1h 40m on ${wd(1)}.", r.getValue("record:lowest_day").message)
    }

    @Test
    fun isNewIsTrueForRecordsInTheLastSevenDays() {
        fun isNew(back: Int) = records(input(sixty { if (it == back) complete(ago(back), 300, 20) else null })).getValue("record:highest_day").isNew
        assertTrue(isNew(7))
        assertTrue(!isNew(8))
        assertTrue(isNew(1))
    }

    @Test
    fun highestAndLowestNeedSevenCompleteDays() {
        fun count(days: Int) = records(input(span(days, 1) { complete(it, 100 + it.dayOfMonth) })).keys.filter { it == "record:highest_day" || it == "record:lowest_day" }
        assertEquals(2, count(7).size)
        assertEquals(0, count(6).size)
    }

    @Test
    fun mostUnlocksDayNeedsSevenDaysWithSignal() {
        val days = sixty { if (it == 5) complete(ago(5), 100, 200) else null }
        val record = records(input(days)).getValue("record:most_unlocks_day")
        assertEquals("Most unlocks in a day", record.title)
        assertEquals("200 unlocks on ${wd(5)}.", record.message)
        val fewSignal = span(60, 7) { complete(it, 100, 0) } + span(6, 1) { complete(it, 100, 20) }
        assertTrue(!records(input(fewSignal)).containsKey("record:most_unlocks_day"))
        val sevenSignal = span(60, 8) { complete(it, 100, 0) } + span(7, 1) { complete(it, 100, 20) }
        assertTrue(records(input(sevenSignal)).containsKey("record:most_unlocks_day"))
    }

    @Test
    fun longestPhoneSessionNeedsThreeCompleteDaysAndAPositiveValue() {
        val withSession = span(3, 1) { PhoneTestFixtures.day(it, screenOnMin = 100, longestMs = if (it == ago(2)) 90 * MIN else 10 * MIN) }
        val record = records(input(withSession)).getValue("record:longest_phone_session")
        assertEquals("Longest phone session", record.title)
        assertEquals("1h 30m on ${wd(2)}.", record.message)
        assertTrue(!records(input(span(2, 1) { PhoneTestFixtures.day(it, screenOnMin = 100, longestMs = 90 * MIN) })).containsKey("record:longest_phone_session"))
        assertTrue(!records(input(span(3, 1) { complete(it) })).containsKey("record:longest_phone_session"))
    }

    @Test
    fun longestAppSessionUsesAnyGivenSession() {
        val sessions = listOf(
            AppSessionRow("a", ago(9).toString(), 1_000L, 20 * MIN),
            AppSessionRow("b", ago(4).toString(), 2_000L, 50 * MIN),
            AppSessionRow("a", ago(2).toString(), 3_000L, 5 * MIN),
        )
        val record = records(input(emptyList(), appSessions = sessions, names = mapOf("b" to "App B"))).getValue("record:longest_app_session")
        assertEquals("Longest app session", record.title)
        assertEquals("App B for 50m on ${wd(4)}.", record.message)
        assertTrue(record.isNew)
        assertTrue(!records(input(emptyList())).containsKey("record:longest_app_session"))
    }

    @Test
    fun mostUsedAppSumsEveryGivenDay() {
        val rows = appSpan(10, 1, "a", 30, "App A") + appSpan(10, 1, "b", 20, "App B")
        val record = records(input(emptyList(), rows, names = mapOf("a" to "App A"))).getValue("record:most_used_app")
        assertEquals("Most-used app", record.title)
        assertEquals("App A: 5h over 10 days.", record.message)
        assertTrue(!record.isNew)
        assertEquals(40, record.priority)
    }

    @Test
    fun busiestHourNeedsSevenCompleteDays() {
        val days = span(7, 1) { complete(it, 60, 5, InsightFixtures.hourly(lateMin = 0, otherMin = 60)) } // all screen time at noon
        val record = records(input(days)).getValue("record:busiest_hour")
        assertEquals("Busiest hour", record.title)
        assertEquals("Around 12 PM, your screen is on 60 minutes of the hour on average.", record.message)
        val six = span(6, 1) { complete(it, 60, 5, InsightFixtures.hourly(lateMin = 0, otherMin = 60)) }
        assertTrue(!records(input(six)).containsKey("record:busiest_hour"))
    }

    @Test
    fun busiestHourUsesTheMeanOverCompleteDays() {
        // Noon: 60 min on 3 of 7 days (mean ≈ 26 min); 11 PM: 20 min every day (mean 20 min).
        val days = span(7, 1) { date ->
            val noon = if (date.dayOfMonth % 7 < 3) 60 else 0
            complete(date, 20 + noon, 0, InsightFixtures.hourly(lateMin = 20, otherMin = noon))
        }
        val message = records(input(days)).getValue("record:busiest_hour").message
        assertTrue(message, message.startsWith("Around 12 PM") || message.startsWith("Around 11 PM"))
    }

    @Test
    fun allSevenRecordsAreProducedWithCompleteData() {
        val days = span(60, 1) { complete(it, 100, 20, InsightFixtures.hourly(10, 90)).copy(longestSessionMs = 30 * MIN) }
        val sessions = listOf(AppSessionRow("a", ago(3).toString(), 1L, 10 * MIN))
        val keys = records(input(days, appDays = appSpan(5, 1, "a", 30), appSessions = sessions)).keys
        assertEquals(
            setOf("highest_day", "lowest_day", "most_unlocks_day", "longest_phone_session", "longest_app_session", "most_used_app", "busiest_hour")
                .map { "record:$it" }.toSet(),
            keys,
        )
    }

    // ---- DATA NOTES ------------------------------------------------------------------------------------
    @Test
    fun historyNoteAppearsUntilFourteenCompleteDays() {
        fun note(days: Int) = gen(input(span(days, 1) { complete(it) })).filter { it.id == "note:history" }
        val thirteen = note(13).single()
        assertEquals("Still building your history", thirteen.title)
        assertEquals("FocusFlow has 13 full days of phone data. Insights get sharper after 14.", thirteen.message)
        assertEquals(10, thirteen.priority)
        assertEquals(InsightGroup.NOTE, thirteen.group)
        assertEquals(InsightKind.DATA_NOTE, thirteen.kind)
        assertTrue(note(14).isEmpty())
        assertEquals("FocusFlow has 1 full day of phone data. Insights get sharper after 14.", note(1).single().message)
    }

    @Test
    fun historyNoteCountsOnlyCompleteDays() {
        // 13 complete days (19..7 back) plus 6 PARTIAL days: only the complete ones are counted.
        val days = span(19, 7) { complete(it) } + span(6, 1) { PhoneTestFixtures.day(it, PhoneDayStatus.PARTIAL, screenOnMin = 10) }
        val note = gen(input(days)).single { it.id == "note:history" }
        assertEquals("FocusFlow has 13 full days of phone data. Insights get sharper after 14.", note.message)
    }

    @Test
    fun unlocksNoteAppearsWhenWakesAreRegularButNoDayHasUnlockSignal() {
        fun note(wakes: Int, unlocksOnOneDay: Int = 0) = gen(
            input(span(14, 1) { complete(it, 100, if (it == ago(5)) unlocksOnOneDay else 0, wakes = wakes) }),
        ).filter { it.id == "note:unlocks" }
        val n = note(5).single()
        assertEquals("Unlock counts aren't available", n.title)
        assertEquals("This phone doesn't report unlocks (is a screen lock set?). Screen wake-ups are shown instead.", n.message)
        assertEquals(12, n.priority)
        assertTrue(note(4).isEmpty())
        assertTrue(note(5, unlocksOnOneDay = 3).isEmpty())
    }

    @Test
    fun bothNotesCanAppearTogetherAndTheUnlocksNoteSortsFirst() {
        val result = gen(input(span(10, 1) { complete(it, 100, 0, wakes = 6) })).filter { it.kind == InsightKind.DATA_NOTE }
        assertEquals(listOf("note:unlocks", "note:history"), result.map { it.id })
    }

    @Test
    fun emptyInputGivesOnlyNotesAndNeverThrows() {
        val result = gen(input())
        assertTrue(result.all { it.kind == InsightKind.DATA_NOTE })
        assertEquals(listOf("note:history"), result.map { it.id })
    }

    // ---- ASSEMBLY --------------------------------------------------------------------------------------
    /** Data that makes thirteen or more non-note insights fire, with wakes but no unlock signal (so the unlocks note appears too). */
    private fun richInput(): PhoneInsightInput {
        val days = span(60, 1) { date ->
            val back = InsightFixtures.today.toEpochDay() - date.toEpochDay()
            val minutes = when {
                back == 1L -> 300
                back % 2 == 0L -> 100
                else -> 160
            }
            complete(date, minutes, 0, wakes = 6).copy(longestSessionMs = 20 * MIN)
        }
        val apps = appSpan(7, 1, "s1", 30) + appSpan(7, 1, "s2", 30) + appSpan(7, 1, "s3", 30) +
            appSpan(28, 8, "d1", 60) + appSpan(7, 1, "d1", 10)
        val sessions = listOf(AppSessionRow("s1", ago(3).toString(), 5L, 40 * MIN))
        return input(days, apps, sessions)
    }

    @Test
    fun theResultKeepsTwelveInsightsPlusNotes() {
        val result = gen(richInput())
        val main = result.filter { it.kind != InsightKind.DATA_NOTE }
        assertEquals(12, main.size)
        assertEquals(listOf("note:unlocks"), result.filter { it.kind == InsightKind.DATA_NOTE }.map { it.id })
        // The thirteenth-or-later candidates are the lowest priority ones; the 90-priority unusual day survives.
        assertTrue(main.any { it.kind == InsightKind.UNUSUAL_DAY })
        assertTrue(main.minOf { it.priority } >= 40)
    }

    @Test
    fun theCapDropsTheLowestPriorityInsightsFirst() {
        val all = gen(richInput())
        val dropped = RecordInsights.records(InsightContext(richInput())) // lowest group
        val keptIds = all.map { it.id }.toSet()
        assertTrue(dropped.any { it.id !in keptIds })
        val minKept = all.filter { it.kind != InsightKind.DATA_NOTE }.minOf { it.priority }
        assertTrue(dropped.filter { it.id !in keptIds }.all { it.priority <= minKept })
    }

    @Test
    fun resultIsSortedByPriorityThenId() {
        val result = gen(richInput())
        val sorted = result.sortedWith(compareByDescending<PhoneInsight> { it.priority }.thenBy { it.id })
        assertEquals(sorted.map { it.id }, result.map { it.id })
    }

    @Test
    fun idsAreUnique() {
        val ids = gen(richInput()).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun generationIsDeterministic() {
        assertEquals(gen(richInput()), gen(richInput()))
    }

    @Test
    fun everyInsightHasTheFourEvidenceLines() {
        for (insight in gen(richInput())) {
            assertEquals(insight.id, listOf("What:", "How:", "Period:", "Evidence:"), insight.evidence.map { it.substringBefore(" ") })
        }
    }

    @Test
    fun inputOrderDoesNotChangeTheResult() {
        val rich = richInput()
        val shuffled = rich.copy(days = rich.days.reversed(), appDays = rich.appDays.reversed())
        assertEquals(gen(rich), gen(shuffled))
    }

    @Test
    fun noInsightsAreProducedForNonCompleteDaysOnly() {
        val days = span(30, 1) { PhoneTestFixtures.day(it, PhoneDayStatus.PARTIAL, screenOnMin = 100) }
        assertEquals(listOf("note:history"), gen(input(days)).map { it.id })
    }
}
