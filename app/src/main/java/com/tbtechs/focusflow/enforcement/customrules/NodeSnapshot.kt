package com.tbtechs.focusflow.enforcement.customrules

import kotlinx.coroutines.flow.MutableStateFlow

/** One captured UI node, flattened depth-first with its depth so the picker can indent it. */
data class NodeSnapshot(
    val depth: Int,
    val viewId: String?,
    val className: String?,
    val text: String?,
    val desc: String?,
    val bounds: String, // "l,t,r,b"
    val selected: Boolean = false, // the element is in its selected state (highlighted tab, chosen page)
)

/**
 * A finished capture. An empty [nodes] list with a blank [packageName] means the capture failed
 * (nothing readable, or FocusFlow itself was on screen), so the UI can ask the user to switch apps.
 */
data class CaptureResult(
    val packageName: String,
    val nodes: List<NodeSnapshot>,
    val atMs: Long,
)

/** The part of a view id after the "/" ("com.app:id/reel_recycler" gives "reel_recycler"). */
fun NodeSnapshot.idSuffix(): String? {
    val id = viewId ?: return null
    if (!id.contains('/')) return null
    return id.substringAfter('/').takeIf { it.isNotBlank() }
}

/** True when the node has an id, text or description a rule can hold on to. */
fun NodeSnapshot.hasStableHandle(): Boolean =
    idSuffix() != null || !text.isNullOrBlank() || !desc.isNullOrBlank()

/**
 * The matcher tapping this node adds to a rule: its view id if it has one, else its text, else
 * its description, else only its class. An element captured in its selected state (the tab you
 * were on) gets [NodeMatcher.selectedOnly], so the rule fires only while that tab is the chosen one.
 * A class-only matcher is too broad to save, so the editor's validation makes the user add more.
 */
fun NodeSnapshot.toMatcher(): NodeMatcher {
    val suffix = idSuffix()
    return when {
        suffix != null -> NodeMatcher(viewIdSuffix = suffix, selectedOnly = selected)
        !text.isNullOrBlank() -> NodeMatcher(textContains = text, selectedOnly = selected)
        !desc.isNullOrBlank() -> NodeMatcher(descContains = desc, selectedOnly = selected)
        else -> NodeMatcher(className = className?.takeIf { it.isNotBlank() }, selectedOnly = selected)
    }
}

/** The element's box as left, top, right, bottom, or null when the captured text is not four numbers. */
fun NodeSnapshot.boxOrNull(): IntArray? {
    val parts = bounds.split(',').map { it.trim().toIntOrNull() ?: return null }
    if (parts.size != 4 || parts[2] <= parts[0] || parts[3] <= parts[1]) return null
    return intArrayOf(parts[0], parts[1], parts[2], parts[3])
}

private fun IntArray.area(): Long = (this[2] - this[0]).toLong() * (this[3] - this[1])

private fun IntArray.contains(inner: IntArray): Boolean =
    this[0] <= inner[0] && this[1] <= inner[1] && this[2] >= inner[2] && this[3] >= inner[3]

/**
 * For an element with nothing to recognise it by (no id, text or description, so a rule on it would
 * match every element of its type), the closest element that does have something. First choice is the
 * smallest named element around it (the tab that holds an icon); next a named element inside it; last
 * a named element right beside it. A named element that covers more than 40% of the screen is never
 * suggested, because a rule on it would match nearly every screen. Null when nothing fits.
 */
fun nearbyWithHandle(nodes: List<NodeSnapshot>, target: NodeSnapshot): NodeSnapshot? {
    val box = target.boxOrNull() ?: return null
    val screenArea = nodes.mapNotNull { it.boxOrNull() }.let { boxes ->
        if (boxes.isEmpty()) return null
        (boxes.maxOf { it[2] }.toLong()) * (boxes.maxOf { it[3] }.toLong())
    }
    if (screenArea <= 0L) return null
    val named = nodes.mapNotNull { n ->
        if (n === target || !n.hasStableHandle()) return@mapNotNull null
        val b = n.boxOrNull() ?: return@mapNotNull null
        if (b.area() * 10 > screenArea * 4) return@mapNotNull null
        n to b
    }
    named.filter { (_, b) -> b.contains(box) }.minByOrNull { (_, b) -> b.area() }?.let { return it.first }
    val cx = (box[0] + box[2]) / 2.0
    val cy = (box[1] + box[3]) / 2.0
    fun distance(b: IntArray) = Math.hypot((b[0] + b[2]) / 2.0 - cx, (b[1] + b[3]) / 2.0 - cy)
    named.filter { (_, b) -> box.contains(b) }.minByOrNull { (_, b) -> distance(b) }?.let { return it.first }
    val reach = maxOf(box[2] - box[0], box[3] - box[1]) * 1.5
    return named.filter { (_, b) -> distance(b) <= reach }.minByOrNull { (_, b) -> distance(b) }?.first
}

object CaptureHolder {
    val snapshot = MutableStateFlow<CaptureResult?>(null)
}
