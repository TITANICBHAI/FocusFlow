package com.tbtechs.focusflow.enforcement.customrules

import kotlinx.coroutines.flow.MutableStateFlow

/** What one matcher found on the tested screen. */
enum class MatcherOutcome {
    /** Found, visible, and selected when the matcher needs that. Counts toward blocking. */
    MATCHED,

    /** Found and visible, but the matcher needs it selected and it is not. */
    NOT_SELECTED,

    /** Found in the screen tree but hidden or off screen, so it does not count. */
    HIDDEN,

    /** No element with these values. */
    NOT_FOUND,
}

data class MatcherReport(val matcher: NodeMatcher, val outcome: MatcherOutcome)

enum class RuleTestVerdict {
    WOULD_BLOCK,
    WOULD_NOT_BLOCK,

    /** Another app was in front when the test ran. */
    WRONG_APP,

    /** Nothing readable was in front, or FocusFlow itself was. */
    NOTHING_READABLE,
}

/**
 * The outcome of "Test this rule". [testedRule] is the rule as it was when the test started, so a
 * result can be recognised as stale once the user edits the rule ([isFor]).
 */
data class RuleTestResult(
    val verdict: RuleTestVerdict,
    val packageSeen: String,
    val reports: List<MatcherReport>,
    val testedRule: CustomScreenRule,
    val atMs: Long,
) {
    /** True when [rule] still has the package, mode and matchers this test ran against. */
    fun isFor(rule: CustomScreenRule): Boolean =
        testedRule.packageName.equals(rule.packageName, ignoreCase = true) &&
            testedRule.matchMode == rule.matchMode &&
            testedRule.matchers == rule.matchers
}

/** The latest rule test, published by the accessibility service and read by the rule editor. */
object RuleTestHolder {
    val result = MutableStateFlow<RuleTestResult?>(null)
}

/**
 * While a test runs the tested rule must not close the app the user is switching to, or the test
 * would read the launcher instead. The service skips this rule id until the window ends.
 */
object RuleTestWindow {
    @Volatile private var ruleId: String? = null
    @Volatile private var untilMs: Long = 0L

    fun start(id: String, until: Long) {
        ruleId = id
        untilMs = until
    }

    fun end() {
        ruleId = null
        untilMs = 0L
    }

    fun suppresses(id: String, nowMs: Long): Boolean = ruleId == id && nowMs < untilMs
}

object RuleTester {
    /** How one matcher fares against every element that was on the tested screen. */
    fun outcomeFor(matcher: NodeMatcher, nodes: List<NodeFields>): MatcherOutcome {
        var best = MatcherOutcome.NOT_FOUND
        for (f in nodes) {
            if (!RuleMatcher.attributesMatch(f, matcher)) continue
            if (RuleMatcher.fieldsMatch(f, matcher)) return MatcherOutcome.MATCHED
            best = when {
                !f.visible -> if (best == MatcherOutcome.NOT_FOUND) MatcherOutcome.HIDDEN else best
                else -> MatcherOutcome.NOT_SELECTED // visible, so only the selected requirement failed
            }
        }
        return best
    }

    /**
     * Runs [rule] against the elements of the screen that was in front. [nodes] must include
     * hidden elements too (with `visible = false`) so a hidden match can be reported as such.
     * The verdict follows exactly the same rule as live blocking: all matchers (ALL) or one (ANY).
     */
    fun evaluate(
        rule: CustomScreenRule,
        packageSeen: String,
        nodes: List<NodeFields>,
        atMs: Long,
    ): RuleTestResult {
        if (!packageSeen.equals(rule.packageName, ignoreCase = true)) {
            return RuleTestResult(RuleTestVerdict.WRONG_APP, packageSeen, emptyList(), rule, atMs)
        }
        val reports = rule.matchers.map { MatcherReport(it, outcomeFor(it, nodes)) }
        val matched = reports.map { it.outcome == MatcherOutcome.MATCHED }
        val blocks = reports.isNotEmpty() && when (rule.matchMode) {
            MatchMode.ALL -> matched.all { it }
            MatchMode.ANY -> matched.any { it }
        }
        val verdict = if (blocks) RuleTestVerdict.WOULD_BLOCK else RuleTestVerdict.WOULD_NOT_BLOCK
        return RuleTestResult(verdict, packageSeen, reports, rule, atMs)
    }

    fun nothingReadable(rule: CustomScreenRule, atMs: Long): RuleTestResult =
        RuleTestResult(RuleTestVerdict.NOTHING_READABLE, "", emptyList(), rule, atMs)
}

/** Plain-language text for a [RuleTestResult], shared by the editor card and the toast. */
object RuleTestText {
    fun headline(result: RuleTestResult): String = when (result.verdict) {
        RuleTestVerdict.WOULD_BLOCK -> "This rule would block this screen."
        RuleTestVerdict.WOULD_NOT_BLOCK -> "This rule would not block this screen."
        RuleTestVerdict.WRONG_APP ->
            "A different app was in front (${result.packageSeen}), not ${result.testedRule.packageName}. " +
                "Open the right app and test again."
        RuleTestVerdict.NOTHING_READABLE ->
            "Nothing readable was on screen. Switch to the other app first, then wait for the countdown to finish."
    }

    fun toast(result: RuleTestResult): String = when (result.verdict) {
        RuleTestVerdict.WOULD_BLOCK -> "FocusFlow test passed: this rule would block this screen. Open FocusFlow to continue"
        RuleTestVerdict.WOULD_NOT_BLOCK -> "FocusFlow test: this rule would not block this screen. Open FocusFlow to see why"
        RuleTestVerdict.WRONG_APP -> "FocusFlow test: a different app was in front. Open FocusFlow for details"
        RuleTestVerdict.NOTHING_READABLE -> "FocusFlow test: nothing readable was on screen"
    }

    /** Short label for a matcher, using the values the user chose. */
    fun describe(m: NodeMatcher): String {
        val parts = buildList {
            if (!m.viewIdSuffix.isNullOrBlank()) add("id ending ${m.viewIdSuffix}")
            if (!m.textContains.isNullOrBlank()) add("text \"${m.textContains}\"")
            if (!m.descContains.isNullOrBlank()) add("description \"${m.descContains}\"")
            if (!m.className.isNullOrBlank()) add("class ${m.className.substringAfterLast('.')}")
        }
        val base = if (parts.isEmpty()) "empty matcher" else parts.joinToString(", ")
        return if (m.selectedOnly) "$base, only when selected" else base
    }

    fun outcome(o: MatcherOutcome): String = when (o) {
        MatcherOutcome.MATCHED -> "found"
        MatcherOutcome.NOT_SELECTED ->
            "found, but not selected. Select that tab first, or turn off Only when selected"
        MatcherOutcome.HIDDEN -> "found, but hidden or off screen, so it does not count"
        MatcherOutcome.NOT_FOUND -> "not on this screen"
    }

    fun lines(result: RuleTestResult): List<String> =
        result.reports.map { "${describe(it.matcher)}: ${outcome(it.outcome)}" }
}
