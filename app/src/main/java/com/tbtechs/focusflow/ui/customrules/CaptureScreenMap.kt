package com.tbtechs.focusflow.ui.customrules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.enforcement.customrules.CaptureMap
import com.tbtechs.focusflow.enforcement.customrules.NodeBox
import com.tbtechs.focusflow.enforcement.customrules.NodeSnapshot
import com.tbtechs.focusflow.enforcement.customrules.hasStableHandle
import com.tbtechs.focusflow.enforcement.customrules.parseNodeBounds
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary

private const val MAX_MAP_LABELS = 400

/**
 * Beta. Draws the scanned elements as outlines at their real positions (the scan has no pixels, so
 * this is a wireframe, not a screenshot). Tap to select the smallest element under the finger.
 * Takes the height it is given and keeps the phone's proportions inside it.
 */
@Composable
internal fun CaptureScreenMap(
    nodes: List<NodeSnapshot>,
    screen: NodeBox,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = remember(nodes, screen) { CaptureMap.visibleIndices(nodes, screen) }
    val boxes = remember(nodes) { nodes.map { parseNodeBounds(it.bounds) } }
    val stable = remember(nodes) { nodes.map { it.hasStableHandle() } }
    val ratio = (screen.width.toFloat() / screen.height.toFloat()).coerceIn(0.2f, 5f)
    val measurer = rememberTextMeasurer()
    val labels = remember(nodes) { nodes.map { (it.text ?: it.desc)?.trim()?.takeIf { t -> t.isNotEmpty() } } }
    val labelColor = DarkTextPrimary
    val outline = DarkTextMuted
    val faint = DarkBorder
    val accent = BrandPrimary

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .aspectRatio(ratio, matchHeightConstraintsFirst = true)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkBackground)
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .semantics { contentDescription = "Map of ${visible.size} scanned elements. Tap one to select it." }
            .pointerInput(nodes, screen) {
                detectTapGestures { tap ->
                    val scale = size.width.toFloat() / screen.width.toFloat()
                    val x = tap.x / scale + screen.left
                    val y = tap.y / scale + screen.top
                    onSelect(CaptureMap.hitTest(nodes, screen, x, y))
                }
            },
    ) {
        val scale = size.width / screen.width.toFloat()
        for (i in visible) {
            val box = boxes[i] ?: continue
            if (i == selectedIndex) continue
            drawRect(
                color = if (stable[i]) outline.copy(alpha = 0.55f) else faint,
                topLeft = Offset((box.left - screen.left) * scale, (box.top - screen.top) * scale),
                size = Size(box.width * scale, box.height * scale),
                style = Stroke(width = 1f),
            )
        }
        // Text and descriptions, so the map reads like the screen it came from.
        var drawnLabels = 0
        for (i in visible) {
            if (drawnLabels >= MAX_MAP_LABELS) break
            val label = labels[i] ?: continue
            val box = boxes[i] ?: continue
            val w = box.width * scale
            val h = box.height * scale
            if (w < 28f || h < 9f) continue
            drawText(
                textMeasurer = measurer,
                text = label,
                topLeft = Offset((box.left - screen.left) * scale + 2f, (box.top - screen.top) * scale),
                style = TextStyle(fontSize = (h * 0.6f).coerceIn(6f, 12f).toSp(), color = labelColor.copy(alpha = 0.85f)),
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                maxLines = 1,
                size = Size((w - 4f).coerceAtLeast(1f), h),
            )
            drawnLabels++
        }
        // Drawn last so it is never hidden under its neighbours.
        selectedIndex?.let { i ->
            boxes.getOrNull(i)?.let { box ->
                val topLeft = Offset((box.left - screen.left) * scale, (box.top - screen.top) * scale)
                val size = Size(box.width * scale, box.height * scale)
                drawRect(color = accent.copy(alpha = 0.30f), topLeft = topLeft, size = size)
                drawRect(color = accent, topLeft = topLeft, size = size, style = Stroke(width = 4f))
            }
        }
    }
}
