package com.tbtechs.focusflow.enforcement.customrules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {
    private class FakeNode(
        override val viewId: String? = null,
        override val className: String? = null,
        override val text: String? = null,
        override val desc: String? = null,
        override val children: List<NodeView> = emptyList(),
        override val visible: Boolean = true,
        override val selected: Boolean = false,
    ) : NodeView

    private fun rule(
        matchers: List<NodeMatcher>,
        matchMode: MatchMode = MatchMode.ALL,
    ) = CustomScreenRule(
        id = "r",
        name = "r",
        packageName = "com.example",
        enabled = true,
        matchMode = matchMode,
        matchers = matchers,
    )

    private val tree = FakeNode(
        className = "android.widget.FrameLayout",
        children = listOf(
            FakeNode(
                viewId = "com.example:id/reel_recycler",
                className = "androidx.recyclerview.widget.RecyclerView",
                children = listOf(FakeNode(text = "Watch Reels Now", className = "android.widget.TextView")),
            ),
            FakeNode(desc = "Home tab", className = "android.widget.ImageView"),
        ),
    )

    @Test
    fun viewIdSuffixMatchesTheEndCaseInsensitively() {
        assertTrue(RuleMatcher.ruleMatches(tree, rule(listOf(NodeMatcher(viewIdSuffix = "REEL_RECYCLER")))))
        assertTrue(RuleMatcher.ruleMatches(tree, rule(listOf(NodeMatcher(viewIdSuffix = ":id/reel_recycler")))))
        assertFalse(RuleMatcher.ruleMatches(tree, rule(listOf(NodeMatcher(viewIdSuffix = "reel_recycl")))))
    }

    @Test
    fun textAndDescriptionMatchAsCaseInsensitiveSubstrings() {
        assertTrue(RuleMatcher.ruleMatches(tree, rule(listOf(NodeMatcher(textContains = "reels now")))))
        assertTrue(RuleMatcher.ruleMatches(tree, rule(listOf(NodeMatcher(descContains = "HOME")))))
        assertFalse(RuleMatcher.ruleMatches(tree, rule(listOf(NodeMatcher(textContains = "shorts")))))
    }

    @Test
    fun allFieldsOfOneMatcherMustMatchTheSameNode() {
        // id is on the recycler, text is on its child: no single node has both.
        val split = NodeMatcher(viewIdSuffix = "reel_recycler", textContains = "Reels")
        assertFalse(RuleMatcher.ruleMatches(tree, rule(listOf(split))))
        val sameNode = NodeMatcher(viewIdSuffix = "reel_recycler", className = "androidx.recyclerview.widget.RecyclerView")
        assertTrue(RuleMatcher.ruleMatches(tree, rule(listOf(sameNode))))
    }

    @Test
    fun allVersusAny() {
        val present = NodeMatcher(viewIdSuffix = "reel_recycler")
        val absent = NodeMatcher(textContains = "no such text")
        assertFalse(RuleMatcher.ruleMatches(tree, rule(listOf(present, absent), MatchMode.ALL)))
        assertTrue(RuleMatcher.ruleMatches(tree, rule(listOf(present, absent), MatchMode.ANY)))
        assertTrue(RuleMatcher.ruleMatches(tree, rule(listOf(present, NodeMatcher(descContains = "home")), MatchMode.ALL)))
        assertFalse(RuleMatcher.ruleMatches(tree, rule(listOf(absent, NodeMatcher(descContains = "nope")), MatchMode.ANY)))
    }

    @Test
    fun aMatcherThatMatchesNothingAndEmptyRulesNeverMatch() {
        assertFalse(RuleMatcher.ruleMatches(tree, rule(listOf(NodeMatcher(viewIdSuffix = "zzz")))))
        assertFalse(RuleMatcher.ruleMatches(tree, rule(emptyList(), MatchMode.ALL)))
        assertFalse(RuleMatcher.ruleMatches(tree, rule(emptyList(), MatchMode.ANY)))
        assertFalse(RuleMatcher.ruleMatches(tree, rule(listOf(NodeMatcher()))))
    }

    private fun chain(length: Int, leaf: FakeNode): FakeNode {
        var node: FakeNode = leaf
        repeat(length) { node = FakeNode(children = listOf(node)) }
        return node
    }

    @Test
    fun nodeAtMaxDepthIsFoundButDeeperIsNot() {
        val target = NodeMatcher(textContains = "deep")
        val atLimit = chain(RuleMatcher.MAX_DEPTH, FakeNode(text = "deep"))
        val tooDeep = chain(RuleMatcher.MAX_DEPTH + 1, FakeNode(text = "deep"))
        assertTrue(RuleMatcher.ruleMatches(atLimit, rule(listOf(target))))
        assertFalse(RuleMatcher.ruleMatches(tooDeep, rule(listOf(target))))
    }

    @Test
    fun hugeTreeReturnsAndOnlyLooksAtMaxNodes() {
        val wide = FakeNode(children = List(RuleMatcher.MAX_NODES * 3) { FakeNode(text = "n$it") })
        val firstSeen = NodeMatcher(textContains = "n0")
        val beyondCap = NodeMatcher(textContains = "n${RuleMatcher.MAX_NODES * 2}")
        assertTrue(RuleMatcher.ruleMatches(wide, rule(listOf(firstSeen))))
        assertFalse(RuleMatcher.ruleMatches(wide, rule(listOf(beyondCap))))
    }

    @Test
    fun nodeMatchesUsesTheSharedFieldRules() {
        val n = FakeNode(viewId = "a:id/x", className = "android.widget.Button", text = "Go", desc = null)
        assertTrue(RuleMatcher.nodeMatches(n, NodeMatcher(viewIdSuffix = "id/x", className = "ANDROID.WIDGET.BUTTON", textContains = "go")))
        assertFalse(RuleMatcher.nodeMatches(n, NodeMatcher(descContains = "anything")))
        assertFalse(RuleMatcher.nodeMatches(n, NodeMatcher()))
    }

    @Test
    fun evaluatorTracksMatchersAcrossNodesAndStopsWhenSatisfied() {
        val all = RuleEvaluator(rule(listOf(NodeMatcher(textContains = "a"), NodeMatcher(descContains = "b")), MatchMode.ALL))
        assertFalse(all.isSatisfied)
        all.accept(NodeFields(viewId = null, className = null, text = "xax", desc = null))
        assertFalse(all.isSatisfied)
        all.accept(NodeFields(viewId = null, className = null, text = null, desc = "B!"))
        assertTrue(all.isSatisfied)

        val any = RuleEvaluator(rule(listOf(NodeMatcher(textContains = "a"), NodeMatcher(descContains = "b")), MatchMode.ANY))
        any.accept(NodeFields(viewId = null, className = null, text = "a", desc = null))
        assertTrue(any.isSatisfied)

        val none = RuleEvaluator(rule(emptyList(), MatchMode.ALL))
        none.accept(NodeFields(viewId = null, className = null, text = "a", desc = null))
        assertFalse(none.isSatisfied)
    }

    // ----- visible-only and selected-only (rules must react like the Shorts / Reels toggles) -----

    @Test
    fun anElementThatIsNotVisibleNeverMatches() {
        val tree = FakeNode(children = listOf(FakeNode(desc = "Reels", visible = false)))
        assertFalse(RuleMatcher.ruleMatches(tree, rule(listOf(NodeMatcher(descContains = "Reels")))))
        val shown = FakeNode(children = listOf(FakeNode(desc = "Reels", visible = true)))
        assertTrue(RuleMatcher.ruleMatches(shown, rule(listOf(NodeMatcher(descContains = "Reels")))))
    }

    @Test
    fun selectedOnlyNeedsTheElementToBeSelected() {
        val notSelected = FakeNode(children = listOf(FakeNode(desc = "Reels", selected = false)))
        val selected = FakeNode(children = listOf(FakeNode(desc = "Reels", selected = true)))
        val onlySelected = rule(listOf(NodeMatcher(descContains = "Reels", selectedOnly = true)))
        assertFalse(RuleMatcher.ruleMatches(notSelected, onlySelected))
        assertTrue(RuleMatcher.ruleMatches(selected, onlySelected))
        // Without selectedOnly the tab button alone is enough, which is the old behaviour.
        assertTrue(RuleMatcher.ruleMatches(notSelected, rule(listOf(NodeMatcher(descContains = "Reels")))))
    }

    @Test
    fun selectedOnlyAloneIsStillAnEmptyMatcher() {
        val tree = FakeNode(children = listOf(FakeNode(selected = true)))
        assertFalse(RuleMatcher.ruleMatches(tree, rule(listOf(NodeMatcher(selectedOnly = true)))))
        assertTrue(NodeMatcher(selectedOnly = true).isEmpty())
    }

    @Test
    fun hiddenPagesInTheTreeDoNotTriggerAllModeRules() {
        // A pager keeps the Reels page in the tree while the Home page is showing.
        val tree = FakeNode(
            children = listOf(
                FakeNode(viewId = "a:id/clips_viewer", visible = false),
                FakeNode(desc = "Reels tab", selected = false),
            ),
        )
        val r = rule(listOf(NodeMatcher(viewIdSuffix = "clips_viewer"), NodeMatcher(descContains = "Reels tab", selectedOnly = true)))
        assertFalse(RuleMatcher.ruleMatches(tree, r))
    }
}
