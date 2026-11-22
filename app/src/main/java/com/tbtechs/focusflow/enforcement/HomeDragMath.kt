package com.tbtechs.focusflow.enforcement

/**
 * The arithmetic behind dragging a block (clock, task card, limits, pinned apps, app widgets) up
 * or down on the launcher's home screen.
 *
 * The blocks are different heights, so nothing here assumes a fixed row. Each block is a [Slot]:
 * where it sits in the column, how tall it is and how much margin it has. The lifted block follows
 * the finger, the blocks it passes slide out of the way by exactly its own height, and letting go
 * puts it where its slot now is. Kept free of Android so the rules can be tested.
 */
object HomeDragMath {
    /**
     * One block in the column's own coordinates. [top] and [height] are the view's; the margins
     * are its layout margins. Slots sit end to end, so a block's [size] is the room it takes.
     */
    class Slot(val top: Int, val height: Int, val marginTop: Int = 0, val marginBottom: Int = 0) {
        val center: Float get() = top + height / 2f
        val size: Int get() = marginTop + height + marginBottom
    }

    /** The block whose band (its margins included) holds [y], or -1 when [y] is in none. */
    fun indexAt(slots: List<Slot>, y: Float): Int =
        slots.indexOfFirst { y >= it.top - it.marginTop && y < it.top + it.height + it.marginBottom }

    /**
     * Where block [from] would end up if it were let go with its centre at [draggedCenter]: the
     * number of other blocks that sit above that point. Letting go in place gives [from] back.
     */
    fun targetIndex(slots: List<Slot>, from: Int, draggedCenter: Float): Int =
        slots.indices.count { it != from && slots[it].center < draggedCenter }

    /** How far each block slides to make room. The lifted block itself stays 0: it follows the finger. */
    fun shifts(slots: List<Slot>, from: Int, to: Int): FloatArray {
        val out = FloatArray(slots.size)
        val room = slots[from].size.toFloat()
        when {
            to > from -> for (i in from + 1..to) out[i] = -room
            to < from -> for (i in to until from) out[i] = room
        }
        return out
    }

    /** How far the lifted block travels from where it sat to rest in its new place. */
    fun dropOffset(slots: List<Slot>, from: Int, to: Int): Float = when {
        to > from -> (from + 1..to).sumOf { slots[it].size }.toFloat()
        to < from -> -(to until from).sumOf { slots[it].size }.toFloat()
        else -> 0f
    }

    /** The lifted block's top edge while it follows the finger, kept inside the stack of blocks. */
    fun clampTop(slots: List<Slot>, from: Int, desiredTop: Float): Float {
        val min = slots.first().top.toFloat()
        val max = (slots.last().top + slots.last().height - slots[from].height).toFloat()
        return desiredTop.coerceIn(min, max.coerceAtLeast(min))
    }

    /**
     * The saved order after moving one of the blocks that are showing. [fullOrder] lists every
     * block, hidden ones included; [visible] lists only those on screen, top to bottom. Hidden
     * blocks keep their place, and the visible ones take the same places in their new order.
     */
    fun reorder(fullOrder: List<String>, visible: List<String>, from: Int, to: Int): List<String> {
        val moved = visible.toMutableList()
        moved.add(to, moved.removeAt(from))
        val shown = visible.toSet()
        val next = moved.iterator()
        return fullOrder.map { if (it in shown) next.next() else it }
    }
}
