package com.tbtechs.focusflow.enforcement.customrules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleTestTest {
    private val pkg = "com.instagram.android"

    private fun rule(
        matchers: List<NodeMatcher>,
        mode: MatchMode = MatchMode.ALL,
        id: String = "r1",
    ) = CustomScreenRule(id, "Reels", pkg, false, mode, matchers)

    private fun f(
        id: String? = null,
        text: String? = null,
        desc: String? = null,
        visible: Boolean = true,
        selected: Boolean = false,
    ) = NodeFields(id, null, text, desc, visible, selected)

    private val tabMatcher = NodeMatcher(descContains = "Reels", selectedOnly = true)
    private val playerMatcher = NodeMatcher(viewIdSuffix = "clips_viewer")

    @Test
    fun matchedWhenEverythingIsVisibleAndSelected() {
        val nodes = listOf(f(id = "a:id/clips_viewer"), f(desc = "Reels", selected = true))
        val r = RuleTester.evaluate(rule(listOf(playerMatcher, tabMatcher)), pkg, nodes, 1L)
        assertEquals(RuleTestVerdict.WOULD_BLOCK, r.verdict)
        assertTrue(r.reports.all { it.outcome == MatcherOutcome.MATCHED })
    }

    @Test
    fun tellsNotSelectedFromHiddenFromNotFound() {
        val nodes = listOf(
            f(desc = "Reels", selected = false), // visible, wrong state
            f(id = "a:id/clips_viewer", visible = false), // hidden page
        )
        val r = RuleTester.evaluate(rule(listOf(tabMatcher, playerMatcher, NodeMatcher(textContains = "nope"))), pkg, nodes, 1L)
        assertEquals(
            listOf(MatcherOutcome.NOT_SELECTED, MatcherOutcome.HIDDEN, MatcherOutcome.NOT_FOUND),
            r.reports.map { it.outcome },
        )
        assertEquals(RuleTestVerdict.WOULD_NOT_BLOCK, r.verdict)
    }

    @Test
    fun aMatchedNodeWinsOverAHiddenOrUnselectedTwin() {
        val nodes = listOf(
            f(desc = "Reels", selected = false),
            f(desc = "Reels", visible = false, selected = true),
            f(desc = "Reels", selected = true),
        )
        assertEquals(MatcherOutcome.MATCHED, RuleTester.outcomeFor(tabMatcher, nodes))
        // Visible-but-unselected is reported ahead of hidden: it is the more useful hint.
        val two = listOf(f(desc = "Reels", visible = false, selected = true), f(desc = "Reels", selected = false))
        assertEquals(MatcherOutcome.NOT_SELECTED, RuleTester.outcomeFor(tabMatcher, two))
    }

    @Test
    fun anyModeNeedsOnlyOneMatcherAndAllModeNeedsEvery() {
        val nodes = listOf(f(id = "a:id/clips_viewer"))
        val matchers = listOf(playerMatcher, tabMatcher)
        assertEquals(RuleTestVerdict.WOULD_BLOCK, RuleTester.evaluate(rule(matchers, MatchMode.ANY), pkg, nodes, 1L).verdict)
        assertEquals(RuleTestVerdict.WOULD_NOT_BLOCK, RuleTester.evaluate(rule(matchers, MatchMode.ALL), pkg, nodes, 1L).verdict)
    }

    @Test
    fun verdictAlwaysAgreesWithTheLiveEvaluator() {
        val screens = listOf(
            listOf(f(id = "a:id/clips_viewer"), f(desc = "Reels", selected = true)),
            listOf(f(id = "a:id/clips_viewer", visible = false), f(desc = "Reels", selected = true)),
            listOf(f(id = "a:id/clips_viewer"), f(desc = "Reels", selected = false)),
            listOf(f(desc = "Reels", selected = true)),
            emptyList(),
        )
        for (mode in MatchMode.values()) {
            val r = rule(listOf(playerMatcher, tabMatcher), mode)
            for (nodes in screens) {
                val live = RuleEvaluator(r).also { e -> nodes.forEach(e::accept) }.isSatisfied
                val tested = RuleTester.evaluate(r, pkg, nodes, 1L).verdict == RuleTestVerdict.WOULD_BLOCK
                assertEquals("mode=$mode nodes=$nodes", live, tested)
            }
        }
    }

    @Test
    fun anotherAppIsReportedAsWrongApp() {
        val r = RuleTester.evaluate(rule(listOf(playerMatcher)), "com.android.launcher", listOf(f(id = "a:id/clips_viewer")), 1L)
        assertEquals(RuleTestVerdict.WRONG_APP, r.verdict)
        assertTrue(RuleTestText.headline(r).contains("com.android.launcher"))
        assertTrue(RuleTestText.headline(r).contains(pkg))
    }

    @Test
    fun packageComparisonIgnoresCase() {
        val r = RuleTester.evaluate(rule(listOf(playerMatcher)), "COM.Instagram.Android", listOf(f(id = "a:id/clips_viewer")), 1L)
        assertEquals(RuleTestVerdict.WOULD_BLOCK, r.verdict)
    }

    @Test
    fun noMatchersNeverBlocksAndEmptyMatcherIsNotFound() {
        assertEquals(RuleTestVerdict.WOULD_NOT_BLOCK, RuleTester.evaluate(rule(emptyList()), pkg, listOf(f()), 1L).verdict)
        assertEquals(MatcherOutcome.NOT_FOUND, RuleTester.outcomeFor(NodeMatcher(), listOf(f(text = "x"))))
    }

    @Test
    fun nothingReadableHasItsOwnVerdictAndMessage() {
        val r = RuleTester.nothingReadable(rule(listOf(playerMatcher)), 1L)
        assertEquals(RuleTestVerdict.NOTHING_READABLE, r.verdict)
        assertTrue(RuleTestText.headline(r).contains("Switch to the other app"))
    }

    @Test
    fun aResultIsOnlyForTheRuleItWasRunAgainst() {
        val tested = rule(listOf(playerMatcher))
        val r = RuleTester.evaluate(tested, pkg, emptyList(), 1L)
        assertTrue(r.isFor(tested.copy(name = "Renamed")))
        assertTrue(r.isFor(tested.copy(packageName = pkg.uppercase())))
        assertFalse(r.isFor(tested.copy(matchers = listOf(playerMatcher, tabMatcher))))
        assertFalse(r.isFor(tested.copy(matchMode = MatchMode.ANY)))
        assertFalse(r.isFor(tested.copy(packageName = "com.other")))
        assertFalse(r.isFor(tested.copy(matchers = listOf(playerMatcher.copy(selectedOnly = true)))))
    }

    @Test
    fun linesDescribeEachMatcherInPlainWords() {
        val nodes = listOf(f(desc = "Reels", selected = false))
        val r = RuleTester.evaluate(rule(listOf(tabMatcher, playerMatcher)), pkg, nodes, 1L)
        val lines = RuleTestText.lines(r)
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("description \"Reels\", only when selected"))
        assertTrue(lines[0].contains("not selected"))
        assertTrue(lines[1].contains("id ending clips_viewer"))
        assertTrue(lines[1].contains("not on this screen"))
        assertTrue(RuleTestText.toast(r).contains("would not block"))
    }

    @Test
    fun theTestWindowOnlySuppressesItsOwnRuleUntilItEnds() {
        RuleTestWindow.start("r1", 1_000L)
        assertTrue(RuleTestWindow.suppresses("r1", 999L))
        assertFalse(RuleTestWindow.suppresses("r1", 1_000L))
        assertFalse(RuleTestWindow.suppresses("r2", 500L))
        RuleTestWindow.end()
        assertFalse(RuleTestWindow.suppresses("r1", 500L))
    }
}
