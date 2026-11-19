package com.tbtechs.focusflow.enforcement

import com.tbtechs.focusflow.enforcement.HomeDragMath.Slot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDragMathTest {
    // A short clock, a tall limits card and a medium task card, stacked with 10 px between them.
    //   clock  : 0..100     (margin below 10)
    //   limits : 110..410   (margin below 10)
    //   task   : 420..520
    private val slots = listOf(
        Slot(top = 0, height = 100, marginTop = 0, marginBottom = 10),
        Slot(top = 110, height = 300, marginTop = 0, marginBottom = 10),
        Slot(top = 420, height = 100, marginTop = 0, marginBottom = 0),
    )

    @Test
    fun indexAtFindsTheBlockUnderTheFinger() {
        assertEquals(0, HomeDragMath.indexAt(slots, 50f))
        assertEquals(0, HomeDragMath.indexAt(slots, 105f)) // in the margin below the clock
        assertEquals(1, HomeDragMath.indexAt(slots, 300f))
        assertEquals(2, HomeDragMath.indexAt(slots, 519f))
    }

    @Test
    fun indexAtIsMinusOneOutsideEveryBlock() {
        assertEquals(-1, HomeDragMath.indexAt(slots, -5f))
        assertEquals(-1, HomeDragMath.indexAt(slots, 520f))
    }

    @Test
    fun lettingGoInPlaceChangesNothing() {
        for (from in slots.indices) {
            assertEquals(from, HomeDragMath.targetIndex(slots, from, slots[from].center))
        }
    }

    @Test
    fun draggingPastTheNextBlocksCentreSwapsThem() {
        // The clock's centre is 50; the limits card's centre is 260.
        assertEquals(0, HomeDragMath.targetIndex(slots, 0, 259f))
        assertEquals(1, HomeDragMath.targetIndex(slots, 0, 261f))
    }

    @Test
    fun aTallBlockNeedsTheFingerToTravelFurther() {
        // Dragging the tall limits card up: it passes the clock once its centre is above 50.
        assertEquals(1, HomeDragMath.targetIndex(slots, 1, 51f))
        assertEquals(0, HomeDragMath.targetIndex(slots, 1, 49f))
    }

    @Test
    fun draggingToTheFarEndsGivesTheFirstAndLastPlaces() {
        assertEquals(0, HomeDragMath.targetIndex(slots, 2, -1000f))
        assertEquals(2, HomeDragMath.targetIndex(slots, 0, 5000f))
    }

    @Test
    fun movingDownSlidesThePassedBlocksUpByTheLiftedBlocksRoom() {
        // Clock (room 110) moves to the end: limits and task each slide up by 110.
        assertArrayEquals(floatArrayOf(0f, -110f, -110f), HomeDragMath.shifts(slots, 0, 2), 0f)
        assertArrayEquals(floatArrayOf(0f, -110f, 0f), HomeDragMath.shifts(slots, 0, 1), 0f)
    }

    @Test
    fun movingUpSlidesThePassedBlocksDownByTheLiftedBlocksRoom() {
        // Limits (room 310) moves to the top: the clock slides down by 310.
        assertArrayEquals(floatArrayOf(310f, 0f, 0f), HomeDragMath.shifts(slots, 1, 0), 0f)
        // Task (room 100) moves to the top: clock and limits each slide down by 100.
        assertArrayEquals(floatArrayOf(100f, 100f, 0f), HomeDragMath.shifts(slots, 2, 0), 0f)
    }

    @Test
    fun noMoveMeansNoShift() {
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), HomeDragMath.shifts(slots, 1, 1), 0f)
        assertEquals(0f, HomeDragMath.dropOffset(slots, 1, 1), 0f)
    }

    @Test
    fun theLiftedBlockRestsWhereThePassedBlocksUsedToBe() {
        // Clock to the end: it lands below limits (310) and task (100).
        assertEquals(410f, HomeDragMath.dropOffset(slots, 0, 2), 0f)
        // Task to the top: it lands 410 above where it was, the room of clock plus limits.
        assertEquals(-420f, HomeDragMath.dropOffset(slots, 2, 0), 0f)
    }

    // The same three blocks, but with margins above and below, as the real home screen has.
    //   clock  : slot 0..110    view 0..100
    //   limits : slot 110..430  view 122..422
    //   task   : slot 430..540  view 440..540
    private val marginSlots = listOf(
        Slot(top = 0, height = 100, marginTop = 0, marginBottom = 10),
        Slot(top = 122, height = 300, marginTop = 12, marginBottom = 8),
        Slot(top = 440, height = 100, marginTop = 10, marginBottom = 0),
    )

    @Test
    fun afterAnyMoveEveryBlockSitsWhereAFreshLayoutWouldPutIt() {
        for (stack in listOf(slots, marginSlots)) for (from in stack.indices) for (to in stack.indices) {
            val shifts = HomeDragMath.shifts(stack, from, to)
            val finalTops = stack.indices.map { i ->
                stack[i].top + if (i == from) HomeDragMath.dropOffset(stack, from, to) else shifts[i]
            }
            val newOrder = stack.indices.toMutableList().also { it.add(to, it.removeAt(from)) }
            var cursor = (stack[0].top - stack[0].marginTop).toFloat()
            newOrder.forEach { i ->
                assertEquals("from $from to $to, block $i", cursor + stack[i].marginTop, finalTops[i], 0f)
                cursor += stack[i].size
            }
        }
    }

    @Test
    fun theFingerCannotPullABlockOutOfTheStack() {
        assertEquals(0f, HomeDragMath.clampTop(slots, 0, -80f), 0f)
        // The clock is 100 high and the stack ends at 520, so its top can go no lower than 420.
        assertEquals(420f, HomeDragMath.clampTop(slots, 0, 900f), 0f)
        assertEquals(250f, HomeDragMath.clampTop(slots, 0, 250f), 0f)
    }

    @Test
    fun reorderMovesOnlyTheVisibleBlocksAndLeavesHiddenOnesPut() {
        val full = listOf("clock", "status", "task", "limits", "pinned", "widgets")
        // "status" and "pinned" are hidden; the visible ones are clock, task, limits, widgets.
        val visible = listOf("clock", "task", "limits", "widgets")
        assertEquals(
            listOf("task", "status", "limits", "clock", "pinned", "widgets"),
            HomeDragMath.reorder(full, visible, from = 0, to = 2),
        )
        assertEquals(
            listOf("widgets", "status", "clock", "task", "pinned", "limits"),
            HomeDragMath.reorder(full, visible, from = 3, to = 0),
        )
    }

    @Test
    fun reorderInPlaceKeepsTheSavedOrder() {
        val full = listOf("clock", "status", "task", "limits", "pinned", "widgets")
        assertEquals(full, HomeDragMath.reorder(full, full, from = 3, to = 3))
    }

    @Test
    fun reorderKeepsEveryBlockExactlyOnce() {
        val full = listOf("clock", "status", "task", "limits", "pinned", "widgets")
        val visible = listOf("status", "limits", "widgets")
        for (from in visible.indices) for (to in visible.indices) {
            val result = HomeDragMath.reorder(full, visible, from, to)
            assertEquals(full.sorted(), result.sorted())
        }
    }
}
