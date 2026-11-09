package com.tbtechs.focusflow.enforcement.customrules

import java.time.Instant

/** A node's rectangle in screen pixels. */
data class NodeBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Long get() = width.toLong().coerceAtLeast(0L) * height.toLong().coerceAtLeast(0L)
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom

    fun intersects(other: NodeBox): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

/** Reads the "l,t,r,b" string a [NodeSnapshot] stores. Returns null for anything malformed. */
fun parseNodeBounds(bounds: String): NodeBox? {
    val parts = bounds.split(',')
    if (parts.size != 4) return null
    val n = parts.map { it.trim().toIntOrNull() ?: return null }
    return NodeBox(n[0], n[1], n[2], n[3])
}

/**
 * Pure helpers behind the "screen map" (beta): the scan has no pixels, so the map is drawn from
 * each element's bounds.
 */
object CaptureMap {
    /**
     * The rectangle the map is drawn in: the first (root) node when it has an area, otherwise the
     * union of every node that has one. Null when nothing can be drawn.
     */
    fun screenBox(nodes: List<NodeSnapshot>): NodeBox? {
        val boxes = nodes.map { parseNodeBounds(it.bounds) }
        boxes.firstOrNull()?.takeIf { !it.isEmpty }?.let { return it }
        val drawable = boxes.filterNotNull().filterNot { it.isEmpty }
        if (drawable.isEmpty()) return null
        return NodeBox(
            left = drawable.minOf { it.left },
            top = drawable.minOf { it.top },
            right = drawable.maxOf { it.right },
            bottom = drawable.maxOf { it.bottom },
        )
    }

    /** Indices of the nodes worth drawing: they have an area and overlap [screen]. */
    fun visibleIndices(nodes: List<NodeSnapshot>, screen: NodeBox): List<Int> =
        nodes.indices.filter { i ->
            val box = parseNodeBounds(nodes[i].bounds)
            box != null && !box.isEmpty && box.intersects(screen)
        }

    /**
     * The node under the point (screen pixels): the one with the smallest area, and the deeper
     * one (later in the list) when areas tie. Null when no drawable node contains the point.
     */
    fun hitTest(nodes: List<NodeSnapshot>, screen: NodeBox, x: Float, y: Float): Int? {
        var best: Int? = null
        var bestArea = Long.MAX_VALUE
        for (i in visibleIndices(nodes, screen)) {
            val box = parseNodeBounds(nodes[i].bounds) ?: continue
            if (!box.contains(x, y)) continue
            if (box.area <= bestArea) {
                best = i
                bestArea = box.area
            }
        }
        return best
    }
}

private const val EXPORT_INDENT_LEVELS = 12
const val CAPTURE_EXPORT_MAX_CHARS = 150_000

/**
 * The scan as plain text for sharing. It holds whatever text was visible on the captured screen
 * (typed text and passwords are never captured), so the header says to review it first. Long
 * scans are cut at [maxChars] with a note, so the share sheet is never handed something huge.
 */
fun CaptureResult.toExportText(maxChars: Int = CAPTURE_EXPORT_MAX_CHARS): String {
    val header = buildString {
        appendLine("FocusFlow screen scan (beta)")
        appendLine("Package: ${packageName.ifBlank { "(unknown)" }}")
        appendLine("Captured: ${Instant.ofEpochMilli(atMs)}")
        appendLine("Elements: ${nodes.size}")
        appendLine("Contains text that was visible on that screen. Review before sharing.")
        appendLine("To turn this into a rule: give it to an assistant with the prompt from Custom Screen Rules → Guide.")
        appendLine("---")
    }
    val out = StringBuilder(header)
    var written = 0
    for ((i, node) in nodes.withIndex()) {
        val line = buildString {
            repeat(minOf(node.depth, EXPORT_INDENT_LEVELS)) { append("  ") }
            append('[').append(i).append("] ")
            append(node.className?.substringAfterLast('.') ?: "View")
            node.idSuffix()?.let { append(" id=").append(it) }
            node.text?.takeIf { it.isNotBlank() }?.let { append(" text=\"").append(oneLine(it)).append('"') }
            node.desc?.takeIf { it.isNotBlank() }?.let { append(" desc=\"").append(oneLine(it)).append('"') }
            append(" bounds=").append(node.bounds)
            append('\n')
        }
        if (out.length + line.length > maxChars) break
        out.append(line)
        written++
    }
    if (written < nodes.size) {
        out.append("… ${nodes.size - written} more element(s) not shown (export size limit)\n")
    }
    return out.toString()
}

private fun oneLine(value: String): String = value.replace('\n', ' ').replace('"', '\'')
