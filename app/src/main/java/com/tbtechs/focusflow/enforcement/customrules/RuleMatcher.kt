package com.tbtechs.focusflow.enforcement.customrules

/** Minimal view of a UI node. Tests and snapshots use this; the service walks the real tree. */
interface NodeView {
    val viewId: String?
    val className: String?
    val text: String?
    val desc: String?
    val children: List<NodeView>
}

/**
 * The four values a matcher can compare. Both the pure walk below and the service's direct
 * AccessibilityNodeInfo walk build one of these, so the comparison rules live in one place.
 */
data class NodeFields(
    val viewId: String?,
    val className: String?,
    val text: String?,
    val desc: String?,
)

/**
 * Tracks which matchers of one rule have been satisfied while a tree is walked.
 * Feed it every visited node with [accept]; read [isSatisfied] to stop early.
 */
class RuleEvaluator(val rule: CustomScreenRule) {
    private val seen = BooleanArray(rule.matchers.size)

    val isSatisfied: Boolean
        get() {
            if (rule.matchers.isEmpty()) return false
            return when (rule.matchMode) {
                MatchMode.ALL -> seen.all { it }
                MatchMode.ANY -> seen.any { it }
            }
        }

    fun accept(fields: NodeFields) {
        for (i in rule.matchers.indices) {
            if (!seen[i] && RuleMatcher.fieldsMatch(fields, rule.matchers[i])) seen[i] = true
        }
    }
}

object RuleMatcher {
    const val MAX_NODES = 3_000
    const val MAX_DEPTH = 40

    /** Every non-blank field of [m] must match. A matcher with no fields never matches. */
    fun fieldsMatch(f: NodeFields, m: NodeMatcher): Boolean {
        if (m.isEmpty()) return false
        if (!m.viewIdSuffix.isNullOrBlank()) {
            val id = f.viewId ?: return false
            if (!id.endsWith(m.viewIdSuffix, ignoreCase = true)) return false
        }
        if (!m.className.isNullOrBlank()) {
            val cls = f.className ?: return false
            if (!cls.equals(m.className, ignoreCase = true)) return false
        }
        if (!m.textContains.isNullOrBlank()) {
            val t = f.text ?: return false
            if (!t.contains(m.textContains, ignoreCase = true)) return false
        }
        if (!m.descContains.isNullOrBlank()) {
            val d = f.desc ?: return false
            if (!d.contains(m.descContains, ignoreCase = true)) return false
        }
        return true
    }

    fun nodeMatches(node: NodeView, m: NodeMatcher): Boolean =
        fieldsMatch(NodeFields(node.viewId, node.className, node.text, node.desc), m)

    /** True when the rule's matchers are satisfied by the tree under [root]. */
    fun ruleMatches(root: NodeView, rule: CustomScreenRule): Boolean {
        val evaluator = RuleEvaluator(rule)
        if (rule.matchers.isEmpty()) return false
        // Iterative depth-first walk: a very deep tree cannot overflow the stack.
        val stack = ArrayDeque<Pair<NodeView, Int>>()
        stack.addLast(root to 0)
        var visited = 0
        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeLast()
            if (depth > MAX_DEPTH) continue
            if (visited >= MAX_NODES) break
            visited++
            evaluator.accept(NodeFields(node.viewId, node.className, node.text, node.desc))
            if (evaluator.isSatisfied) return true
            val kids = node.children
            for (i in kids.indices.reversed()) stack.addLast(kids[i] to depth + 1)
        }
        return evaluator.isSatisfied
    }
}
