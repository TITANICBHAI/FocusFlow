package com.tbtechs.focusflow.analytics.phone

import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures.MIN
import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures.app
import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures.d
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DistractionBuilderTest {
    private val cat = mapOf(
        "social" to UsageCategory.SOCIAL, "game" to UsageCategory.GAMES, "video" to UsageCategory.ENTERTAINMENT,
        "tool" to UsageCategory.TOOLS, "mail" to UsageCategory.COMMUNICATION,
    )
    private val categoryOf: (String) -> UsageCategory = { cat[it] ?: UsageCategory.OTHER }

    private fun row(
        pkg: String, totalMs: Long = 60 * MIN, opens: Int = 0, activeDays: Int = 5, short: Int = 0,
        avgPerDay: Long = totalMs / 5, prevAvg: Long? = null, change: Int? = null, category: UsageCategory = categoryOf(pkg),
        name: String = pkg,
    ) = AppUsageRow(
        packageName = pkg, appName = name, category = category, totalMs = totalMs, sharePercent = 0.0,
        opens = opens, avgSessionMs = 0L, longestSessionMs = 0L,
        sessionBucketCounts = listOf(short, 0, 0, 0, 0), periodMs = List(4) { 0L }, dailyMs = List(7) { 0L },
        activeDays = activeDays, avgPerDayMs = avgPerDay, previousAvgPerDayMs = prevAvg, changePercent = change,
        firstAfterUnlock = 0,
    )

    private fun section(rows: List<AppUsageRow>, appDataDays: Int = 5, total: Long = rows.sumOf { it.totalMs }) =
        AppsSection(total, appDataDays, rows, 0L, emptyList(), emptyList())

    private fun build(
        apps: AppsSection,
        appDays: List<AppDayRow> = emptyList(),
        blocked: Map<String, Int> = emptyMap(),
        previous: PreviousAppPeriod? = null,
    ) = DistractionBuilder.build(apps, appDays, blocked, categoryOf, previous)

    @Test
    fun emptyInput() {
        val s = build(section(emptyList(), 0, 0L))
        assertEquals(0L, s.distractionMs)
        assertNull(s.sharePercent)
        assertTrue(s.apps.isEmpty() && s.reflexApps.isEmpty() && s.risingApps.isEmpty())
        assertEquals(List(24) { 0L }, s.byHourMs)
        assertNull(s.peakHour)
        assertNull(s.changePercent)
    }

    @Test
    fun setMembershipByCategoryAndByBlockedAttempts() {
        val appDays = listOf(app(d(1), "social", 30 * MIN), app(d(1), "tool", 20 * MIN), app(d(1), "mail", 10 * MIN), app(d(1), "other", 5 * MIN))
        val apps = section(listOf(row("social", 30 * MIN), row("tool", 20 * MIN), row("mail", 10 * MIN), row("other", 5 * MIN)))
        val s = build(apps, appDays, blocked = mapOf("tool" to 3, "mail" to 0))
        assertEquals(50 * MIN, s.distractionMs) // social + blocked tool; mail has 0 attempts
        assertEquals(listOf("social", "tool"), s.apps.map { it.packageName })
        assertEquals(77, s.sharePercent) // 50 / 65 = 76.9
    }

    @Test
    fun shareIsNullWhenThereIsNoAppTime() {
        assertNull(build(section(emptyList(), 0, 0L), listOf(app(d(1), "social", 0L))).sharePercent)
    }

    @Test
    fun reasonsAreInTheFixedOrder() {
        val apps = section(listOf(row("social", 60 * MIN, opens = 30, activeDays = 5)))
        val s = build(apps, listOf(app(d(1), "social", 60 * MIN)), blocked = mapOf("social" to 4))
        val a = s.apps.single()
        assertEquals(listOf("Social media", "Blocked 4×", "6 opens a day"), a.reasons)
        assertEquals(4, a.blockedAttempts)
        assertEquals(30, a.opens)
    }

    @Test
    fun aCategoryDistractionWithoutBlocksHasOnlyTheCategoryReason() {
        val s = build(section(listOf(row("social", opens = 2, activeDays = 5))), listOf(app(d(1), "social", MIN)))
        assertEquals(listOf("Social media"), s.apps.single().reasons)
        assertEquals(0, s.apps.single().blockedAttempts)
    }

    @Test
    fun opensADayReasonThresholdAndRounding() {
        fun reasons(opens: Int, days: Int) =
            build(section(listOf(row("tool", opens = opens, activeDays = days)))).apps // tool is not a distraction by itself
        assertTrue(reasons(100, 3).isEmpty())
        val blocked = mapOf("tool" to 1)
        fun withBlock(opens: Int, days: Int) =
            build(section(listOf(row("tool", opens = opens, activeDays = days))), listOf(app(d(1), "tool", MIN)), blocked).apps.single().reasons
        assertEquals(listOf("Blocked 1×"), withBlock(49, 10)) // 4.9
        assertEquals(listOf("Blocked 1×", "5 opens a day"), withBlock(50, 10))
        assertEquals(listOf("Blocked 1×", "7 opens a day"), withBlock(66, 10)) // 6.6
        assertEquals(listOf("Blocked 1×"), withBlock(50, 0)) // no active days → no ratio
    }

    @Test
    fun appsAreCappedAtEightByTimeAndMustAppearInTheAppsSection() {
        val rows = (1..10).map { row("social", 0L).copy(packageName = "s$it", appName = "s$it", totalMs = it * MIN, category = UsageCategory.SOCIAL) }
        val s = DistractionBuilder.build(section(rows), emptyList(), emptyMap(), { UsageCategory.SOCIAL }, null)
        assertEquals(8, s.apps.size)
        assertEquals("s10", s.apps.first().packageName)
        assertEquals("s3", s.apps.last().packageName)
        // in appDays but not in the (listed) apps section → counts in the minutes, not in the list
        val extra = DistractionBuilder.build(section(emptyList(), 1, 10 * MIN), listOf(app(d(1), "social", 5 * MIN)), emptyMap(), categoryOf, null)
        assertEquals(5 * MIN, extra.distractionMs)
        assertTrue(extra.apps.isEmpty())
    }

    @Test
    fun hourlySumsAndPeakUseOnlyTheSet() {
        val appDays = listOf(
            app(d(1), "social", 30 * MIN, hour = 22), app(d(2), "game", 20 * MIN, hour = 22),
            app(d(2), "video", 10 * MIN, hour = 9), app(d(2), "mail", 500 * MIN, hour = 3),
        )
        val s = build(section(listOf(row("social"))), appDays)
        assertEquals(50 * MIN, s.byHourMs[22])
        assertEquals(10 * MIN, s.byHourMs[9])
        assertEquals(0L, s.byHourMs[3])
        assertEquals(22, s.peakHour)
    }

    @Test
    fun peakHourTieGoesToEarliest() {
        val s = build(section(emptyList(), 1, 1L), listOf(app(d(1), "social", MIN, hour = 20), app(d(1), "game", MIN, hour = 7)))
        assertEquals(7, s.peakHour)
    }

    @Test
    fun reflexThresholds() {
        val rows = listOf(
            row("a", opens = 79, activeDays = 10, short = 79),   // 7.9/day → out
            row("b", opens = 80, activeDays = 10, short = 40),   // 8.0/day, exactly 50% short → in
            row("c", opens = 80, activeDays = 10, short = 39),   // 48.75% short → out
            row("d", opens = 100, activeDays = 2, short = 100),  // only 2 active days → out
            row("e", opens = 100, activeDays = 10, short = 99),  // 10/day, 99% → in
        )
        val s = build(section(rows))
        assertEquals(listOf("e", "b"), s.reflexApps.map { it.packageName }) // by opens per active day desc
        assertEquals(99, s.reflexApps[0].shortSessionSharePercent)
        assertEquals(50, s.reflexApps[1].shortSessionSharePercent)
        assertEquals(10.0, s.reflexApps[0].opensPerActiveDay, 1e-9)
    }

    @Test
    fun reflexListIsCappedAtFive() {
        val rows = (1..7).map { row("p$it", opens = 100 + it, activeDays = 10, short = 100 + it) }
        assertEquals(5, build(section(rows)).reflexApps.size)
    }

    @Test
    fun reflexApp_withZeroOpensIsIgnored() {
        assertTrue(build(section(listOf(row("a", opens = 0, activeDays = 5)))).reflexApps.isEmpty())
    }

    @Test
    fun risingThresholds() {
        val rows = listOf(
            row("below", avgPerDay = 89_400L * 60, prevAvg = 60 * MIN, change = 49),        // 1.49x (89.4 min vs 60) and +29 min → out
            row("exact", avgPerDay = 45 * MIN, prevAvg = 30 * MIN, change = 50),            // 1.5x and +15 min → in
            row("smallDelta", avgPerDay = 15 * MIN, prevAvg = 10 * MIN, change = 50),        // 1.5x but only +5 min → out
            row("tinyPrev", avgPerDay = 30 * MIN, prevAvg = 4 * MIN, change = null),         // change null → out
            row("tinyPrev2", avgPerDay = 30 * MIN, prevAvg = 4 * MIN, change = 650),         // 7.5x, +26 min, but previous < 5 min → out
            row("fewDays", avgPerDay = 90 * MIN, prevAvg = 30 * MIN, change = 200, activeDays = 2), // 2 active days → out
            row("big", avgPerDay = 120 * MIN, prevAvg = 30 * MIN, change = 300),             // in
        )
        val s = build(section(rows))
        assertEquals(listOf("big", "exact"), s.risingApps.map { it.packageName }) // by absolute increase
        assertEquals(RisingApp("big", "big", 120 * MIN, 30 * MIN, 300), s.risingApps[0])
    }

    @Test
    fun risingListIsCappedAtThree() {
        val rows = (1..5).map { row("p$it", avgPerDay = (100 + it) * MIN, prevAvg = 30 * MIN, change = 200 + it) }
        val s = build(section(rows))
        assertEquals(3, s.risingApps.size)
        assertEquals(listOf("p5", "p4", "p3"), s.risingApps.map { it.packageName })
    }

    @Test
    fun categoryOnlyChangeGating() {
        val appDays = (1..4).map { app(d(it), "social", 60 * MIN) } // 60 min/day over 4 data days
        val prevMs = mapOf("social" to 4 * 30 * MIN, "mail" to 4 * 500 * MIN) // mail is not a distraction
        val apps = section(emptyList(), appDataDays = 4, total = 1L)
        assertEquals(100, build(apps, appDays, previous = PreviousAppPeriod(4, prevMs)).changePercent)
        assertNull(build(apps, appDays, previous = null).changePercent)
        assertNull(build(apps, appDays, previous = PreviousAppPeriod(3, prevMs)).changePercent)
        assertNull(build(section(emptyList(), 3, 1L), appDays, previous = PreviousAppPeriod(4, prevMs)).changePercent)
        // previous distraction average below 5 min/day → null
        assertNull(build(apps, appDays, previous = PreviousAppPeriod(4, mapOf("social" to 4 * 4 * MIN))).changePercent)
        // category-only: a blocked non-distraction app is not part of the category change
        val blockedOnly = build(apps, appDays + app(d(1), "tool", 100 * MIN), blocked = mapOf("tool" to 5), previous = PreviousAppPeriod(4, prevMs))
        assertEquals(100, blockedOnly.changePercent)
    }

    @Test
    fun categoryChangeCanBeNegative() {
        val appDays = (1..4).map { app(d(it), "game", 15 * MIN) }
        val s = build(section(emptyList(), 4, 1L), appDays, previous = PreviousAppPeriod(4, mapOf("game" to 4 * 30 * MIN)))
        assertEquals(-50, s.changePercent)
    }
}
