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

object CaptureHolder {
    val snapshot = MutableStateFlow<CaptureResult?>(null)
}
