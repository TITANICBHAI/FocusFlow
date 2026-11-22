package com.tbtechs.focusflow.enforcement

import com.tbtechs.focusflow.enforcement.HomeGrid.Cell
import com.tbtechs.focusflow.enforcement.HomeGrid.Item
import com.tbtechs.focusflow.enforcement.HomeGrid.Kind
import com.tbtechs.focusflow.enforcement.HomeGrid.Status
import com.tbtechs.focusflow.enforcement.HomeGrid.Wanted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGridTest {
    private val cols = 4
    private val rows = 6

    private fun app(name: String, col: Int, row: Int) = Item(Kind.APP, "APP|$name|", col, row, 1, 1)
    private fun card(name: String, col: Int, row: Int, sx: Int, sy: Int) = Item(Kind.CARD, name, col, row, sx, sy)
    private fun widget(id: Int, col: Int, row: Int, sx: Int, sy: Int) = Item(Kind.WIDGET, id.toString(), col, row, sx, sy)

    private fun noOverlapAndInBounds(items: List<Item>) {
        for (i in items) {
            assertTrue("${i.id} out of bounds", i.col >= 0 && i.row >= 0 && i.right <= cols && i.bottom <= rows)
        }
        for (a in items.indices) for (b in a + 1 until items.size) {
            assertFalse("${items[a].id} overlaps ${items[b].id}", items[a].overlaps(items[b]))
        }
    }

    // ── fits / firstFree / nearestFree ────────────────────────────────────────

    @Test
    fun fitsChecksBoundsAndOccupancy() {
        val items = listOf(card("clock", 0, 0, 4, 2))
        assertFalse(HomeGrid.fits(items, cols, rows, 0, 1, 1, 1)) // inside the clock
        assertTrue(HomeGrid.fits(items, cols, rows, 0, 2, 1, 1))
        assertFalse(HomeGrid.fits(items, cols, rows, 3, 5, 2, 1)) // sticks out right
        assertFalse(HomeGrid.fits(items, cols, rows, 0, 5, 1, 2)) // sticks out bottom
        assertFalse(HomeGrid.fits(items, cols, rows, -1, 2, 1, 1))
        assertTrue(HomeGrid.fits(items, cols, rows, 0, 0, 4, 2, ignoreId = items[0].id)) // its own spot
    }

    @Test
    fun firstFreeReadsLeftToRightThenTopToBottom() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0))
        assertEquals(Cell(2, 0), HomeGrid.firstFree(items, cols, rows, 1, 1))
        // A 2 wide block does not fit at (2,0) once (3,0) is taken, so it drops to the next row.
        val more = items + app("c", 3, 0)
        assertEquals(Cell(0, 1), HomeGrid.firstFree(more, cols, rows, 2, 1))
    }

    @Test
    fun firstFreeIsNullWhenThereIsNoRoom() {
        val full = listOf(card("limits", 0, 0, 4, 6))
        assertNull(HomeGrid.firstFree(full, cols, rows, 1, 1))
    }

    @Test
    fun nearestFreePicksTheClosestOpenSpotAndBreaksTiesUpThenLeft() {
        val items = listOf(app("a", 1, 1))
        // Finger sits exactly on the taken cell: the four neighbours are equally far; up wins, then left.
        assertEquals(Cell(1, 0), HomeGrid.nearestFree(items, cols, rows, 1, 1, 1f, 1f))
        assertEquals(Cell(3, 3), HomeGrid.nearestFree(items, cols, rows, 1, 1, 3f, 3f))
    }

    @Test
    fun nearestFreeIsNullWhenNothingFits() {
        val items = listOf(card("limits", 0, 0, 4, 6))
        assertNull(HomeGrid.nearestFree(items, cols, rows, 1, 1, 0f, 0f))
    }

    @Test
    fun snapRoundsAndStaysInsideTheGrid() {
        assertEquals(Cell(1, 2), HomeGrid.snap(cols, rows, 1, 1, 0.6f, 1.7f))
        assertEquals(Cell(2, 4), HomeGrid.snap(cols, rows, 2, 2, 9f, 9f))
        assertEquals(Cell(0, 0), HomeGrid.snap(cols, rows, 1, 1, -3f, -0.4f))
    }

    // ── add / remove ──────────────────────────────────────────────────────────

    @Test
    fun addPutsItInTheFirstFreeSpot() {
        val first = HomeGrid.add(emptyList(), cols, rows, Wanted(Kind.APP, "APP|a|", 1, 1))
        assertEquals(Status.ADDED, first.status)
        assertEquals(Cell(0, 0), Cell(first.items[0].col, first.items[0].row))
        val second = HomeGrid.add(first.items, cols, rows, Wanted(Kind.APP, "APP|b|", 1, 1))
        assertEquals(Cell(1, 0), Cell(second.items[1].col, second.items[1].row))
    }

    @Test
    fun addTellsApartDuplicateAndFull() {
        val one = HomeGrid.add(emptyList(), cols, rows, Wanted(Kind.APP, "APP|a|", 1, 1)).items
        val dup = HomeGrid.add(one, cols, rows, Wanted(Kind.APP, "APP|a|", 1, 1))
        assertEquals(Status.DUPLICATE, dup.status)
        assertEquals(one, dup.items)

        val full = listOf(card("limits", 0, 0, 4, 6))
        val out = HomeGrid.add(full, cols, rows, Wanted(Kind.APP, "APP|b|", 1, 1))
        assertEquals(Status.FULL, out.status)
        assertEquals(full, out.items)
    }

    @Test
    fun removeDropsOnlyThatItem() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0))
        assertEquals(listOf(items[1]), HomeGrid.remove(items, items[0].id))
        assertEquals(items, HomeGrid.remove(items, "APP:nope"))
    }

    // ── place / move / resize ─────────────────────────────────────────────────

    @Test
    fun moveToAFreeSpotChangesOnlyThatItem() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0))
        val out = HomeGrid.move(items, cols, rows, items[0].id, 3, 4)!!
        assertEquals(Cell(3, 4), Cell(out[0].col, out[0].row))
        assertEquals(items[1], out[1])
    }

    @Test
    fun moveKeepsTheOrderOfTheList() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0), app("c", 2, 0))
        val out = HomeGrid.move(items, cols, rows, items[1].id, 0, 3)!!
        assertEquals(items.map { it.id }, out.map { it.id })
    }

    @Test
    fun movingOntoAnotherPushesItDown() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0))
        val out = HomeGrid.move(items, cols, rows, items[0].id, 1, 0)!!
        assertEquals(Cell(1, 0), Cell(out[0].col, out[0].row))
        assertEquals(Cell(1, 1), Cell(out[1].col, out[1].row))
    }

    @Test
    fun aPushCascadesDownAColumn() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0), app("c", 1, 1), app("d", 1, 2))
        val out = HomeGrid.move(items, cols, rows, items[0].id, 1, 0)!!
        assertEquals(Cell(1, 0), Cell(out[0].col, out[0].row))
        assertEquals(Cell(1, 1), Cell(out[1].col, out[1].row))
        assertEquals(Cell(1, 2), Cell(out[2].col, out[2].row))
        assertEquals(Cell(1, 3), Cell(out[3].col, out[3].row))
        noOverlapAndInBounds(out)
    }

    @Test
    fun aBigBlockPushesEverythingUnderIt() {
        val items = listOf(
            card("task", 0, 4, 4, 2),
            app("a", 0, 0), app("b", 3, 1), app("c", 2, 3),
        )
        val out = HomeGrid.move(items, cols, rows, items[0].id, 0, 0)
        assertNotNull(out)
        noOverlapAndInBounds(out!!)
        assertEquals(Cell(0, 0), Cell(out[0].col, out[0].row))
        // Each of the three was under the new spot, so each ended up just below it.
        for (i in 1..3) assertTrue(out[i].row >= 2)
    }

    @Test
    fun whenThereIsNoRoomBelowThePushGoesUp() {
        val items = listOf(app("a", 0, 5), app("b", 1, 4), app("c", 1, 5))
        // Dropping a onto b at the bottom: b and c cannot go further down, so the block above gives way.
        val out = HomeGrid.move(items, cols, rows, items[0].id, 1, 5)
        assertNotNull(out)
        noOverlapAndInBounds(out!!)
        assertEquals(Cell(1, 5), Cell(out[0].col, out[0].row))
    }

    @Test
    fun anImpossibleMoveReturnsNull() {
        // A full-width, full-height card with another 1x1 beside nothing: shrink the grid to 4x2 so
        // that a 4x2 card has to sit on rows 0..1 and the two apps below cannot go anywhere.
        val items = listOf(card("clock", 0, 0, 4, 1), app("a", 0, 1), app("b", 1, 1))
        val out = HomeGrid.place(items, 4, 2, items[0].id, 0, 0, 4, 2)
        assertNull(out)
    }

    @Test
    fun anUnknownIdReturnsNull() {
        assertNull(HomeGrid.move(listOf(app("a", 0, 0)), cols, rows, "APP:zzz", 1, 1))
    }

    @Test
    fun placeKeepsThePositionAndSizeInsideTheGrid() {
        val items = listOf(card("task", 0, 0, 4, 2))
        val out = HomeGrid.place(items, cols, rows, items[0].id, 3, 5, 9, 9)!!
        assertEquals(Item(Kind.CARD, "task", 0, 0, 4, 6), out[0])
    }

    @Test
    fun resizeKeepsTheCornerAndPushesWhatItGrowsInto() {
        val items = listOf(card("status", 0, 0, 2, 1), app("a", 0, 1), app("b", 2, 0))
        val out = HomeGrid.resize(items, cols, rows, items[0].id, 3, 2)!!
        assertEquals(Item(Kind.CARD, "status", 0, 0, 3, 2), out[0])
        noOverlapAndInBounds(out)
        assertTrue(out[1].row >= 2) // a was under the grown card
        assertTrue(out[2].row >= 2) // so was b
    }

    // A fixed-seed pseudo-random walk: whatever is dropped wherever, the result is either null
    // or a valid grid that still holds exactly the same items.
    @Test
    fun anyMoveEitherFailsCleanlyOrLeavesAValidGrid() {
        var seed = 12345L
        fun next(n: Int): Int {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return (((seed ushr 33) % n).toInt() + n) % n
        }
        var items = listOf(
            card("clock", 0, 0, 4, 2), card("status", 0, 2, 2, 1),
            app("a", 2, 2), app("b", 3, 2), app("c", 0, 3), app("d", 1, 3),
            widget(7, 0, 4, 4, 2),
        )
        var applied = 0
        repeat(400) {
            val pick = items[next(items.size)]
            val c = next(cols + 1) - 1
            val r = next(rows + 1) - 1
            val resize = next(4) == 0
            val sx = if (resize) 1 + next(cols) else pick.spanX
            val sy = if (resize) 1 + next(3) else pick.spanY
            val out = HomeGrid.place(items, cols, rows, pick.id, c, r, sx, sy)
            if (out != null) {
                noOverlapAndInBounds(out)
                assertEquals(items.map { it.id }, out.map { it.id })
                items = out
                applied++
            }
        }
        assertTrue("random walk should succeed sometimes", applied > 20)
    }

    @Test
    fun aPushLeavesFarAwayItemsExactlyWhereTheyWere() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0), app("far", 3, 5))
        val out = HomeGrid.move(items, cols, rows, items[0].id, 1, 0)!!
        assertEquals(items[2], out[2])
    }

    // ── resizing by an edge ───────────────────────────────────────────────────

    @Test
    fun draggingTheRightEdgeChangesOnlyTheWidth() {
        val item = card("task", 1, 2, 2, 2)
        assertEquals(item.copy(spanX = 3), HomeGrid.resizedBlock(item, HomeGrid.Edge.RIGHT, 4.2f, 9f, cols, rows))
        assertEquals(item.copy(spanX = 1), HomeGrid.resizedBlock(item, HomeGrid.Edge.RIGHT, -3f, 0f, cols, rows))
        assertEquals(item.copy(spanX = 3), HomeGrid.resizedBlock(item, HomeGrid.Edge.RIGHT, 99f, 0f, cols, rows)) // stops at the grid edge
    }

    @Test
    fun draggingTheLeftEdgeKeepsTheRightEdgeFixed() {
        val item = card("task", 1, 2, 2, 2) // columns 1..3, so right = 3
        val left0 = HomeGrid.resizedBlock(item, HomeGrid.Edge.LEFT, 0f, 0f, cols, rows)
        assertEquals(0, left0.col)
        assertEquals(3, left0.right)
        val tooFar = HomeGrid.resizedBlock(item, HomeGrid.Edge.LEFT, 9f, 0f, cols, rows)
        assertEquals(2, tooFar.col) // one cell wide at most-shrunk
        assertEquals(3, tooFar.right)
    }

    @Test
    fun draggingTopAndBottomEdgesWorkTheSameWayVertically() {
        val item = card("limits", 0, 2, 4, 2) // rows 2..3, bottom = 4
        assertEquals(item.copy(spanY = 4), HomeGrid.resizedBlock(item, HomeGrid.Edge.BOTTOM, 0f, 6f, cols, rows))
        assertEquals(item.copy(spanY = 1), HomeGrid.resizedBlock(item, HomeGrid.Edge.BOTTOM, 0f, 0f, cols, rows))
        val up = HomeGrid.resizedBlock(item, HomeGrid.Edge.TOP, 0f, 0.4f, cols, rows)
        assertEquals(0, up.row)
        assertEquals(4, up.bottom)
        val down = HomeGrid.resizedBlock(item, HomeGrid.Edge.TOP, 0f, 99f, cols, rows)
        assertEquals(3, down.row)
        assertEquals(4, down.bottom)
    }

    @Test
    fun edgeHitFindsTheNearestEdgeWithinTheBand() {
        // A rectangle from (100,100) to (300,200), band 20.
        fun hit(x: Float, y: Float) = HomeGrid.edgeHit(100f, 100f, 300f, 200f, x, y, 20f)
        assertEquals(HomeGrid.Edge.LEFT, hit(105f, 150f))
        assertEquals(HomeGrid.Edge.LEFT, hit(90f, 150f)) // just outside still counts
        assertEquals(HomeGrid.Edge.RIGHT, hit(295f, 150f))
        assertEquals(HomeGrid.Edge.TOP, hit(200f, 102f))
        assertEquals(HomeGrid.Edge.BOTTOM, hit(200f, 210f))
        assertNull(hit(200f, 150f)) // the middle is not an edge
        assertNull(hit(60f, 150f)) // too far out
        assertEquals(HomeGrid.Edge.LEFT, hit(103f, 108f)) // corner: closer edge (x) wins
        assertEquals(HomeGrid.Edge.TOP, hit(108f, 103f))
    }

    @Test
    fun aResizeThatPushesOthersIsSafeToPreview() {
        val items = listOf(card("status", 0, 0, 2, 1), app("a", 0, 1), app("b", 1, 1))
        val grown = HomeGrid.resizedBlock(items[0], HomeGrid.Edge.BOTTOM, 0f, 2f, cols, rows)
        val out = HomeGrid.place(items, cols, rows, items[0].id, grown.col, grown.row, grown.spanX, grown.spanY)!!
        noOverlapAndInBounds(out)
        assertEquals(2, out[0].spanY)
    }

    // ── reconcile ─────────────────────────────────────────────────────────────

    @Test
    fun reconcileKeepsSavedPositionsAndAppendsNewOnesInOrder() {
        val saved = listOf(app("a", 3, 5))
        val wanted = listOf(
            Wanted(Kind.APP, "APP|a|", 1, 1),
            Wanted(Kind.APP, "APP|b|", 1, 1),
            Wanted(Kind.APP, "APP|c|", 1, 1),
        )
        val layout = HomeGrid.reconcile(saved, wanted, cols, rows)
        assertEquals(Cell(3, 5), Cell(layout.items[0].col, layout.items[0].row))
        assertEquals(Cell(0, 0), Cell(layout.items[1].col, layout.items[1].row))
        assertEquals(Cell(1, 0), Cell(layout.items[2].col, layout.items[2].row))
        assertTrue(layout.unplaced.isEmpty())
    }

    @Test
    fun reconcileDropsWhatIsNoLongerWanted() {
        val saved = listOf(app("a", 0, 0), app("gone", 1, 0))
        val layout = HomeGrid.reconcile(saved, listOf(Wanted(Kind.APP, "APP|a|", 1, 1)), cols, rows)
        assertEquals(listOf("APP:APP|a|"), layout.items.map { it.id })
    }

    @Test
    fun reconcileTreatsASavedItemThatNowOverlapsAsNew() {
        val saved = listOf(app("a", 1, 1), app("b", 1, 1)) // damaged: two on one cell
        val wanted = listOf(Wanted(Kind.APP, "APP|a|", 1, 1), Wanted(Kind.APP, "APP|b|", 1, 1))
        val layout = HomeGrid.reconcile(saved, wanted, cols, rows)
        assertEquals(2, layout.items.size)
        noOverlapAndInBounds(layout.items)
        assertEquals(Cell(1, 1), Cell(layout.items[0].col, layout.items[0].row))
    }

    @Test
    fun reconcileFitsASmallerGrid() {
        val saved = listOf(app("a", 3, 5), card("limits", 0, 0, 4, 3))
        val wanted = listOf(Wanted(Kind.APP, "APP|a|", 1, 1), Wanted(Kind.CARD, "limits", 4, 3))
        val layout = HomeGrid.reconcile(saved, wanted, 3, 4)
        noOverlapAndInBounds3x4(layout.items)
        assertEquals(2, layout.items.size + layout.unplaced.size)
    }

    private fun noOverlapAndInBounds3x4(items: List<Item>) {
        for (i in items) assertTrue(i.col >= 0 && i.row >= 0 && i.right <= 3 && i.bottom <= 4)
    }

    @Test
    fun reconcileReportsWhatDoesNotFitInsteadOfLosingIt() {
        val wanted = listOf(
            Wanted(Kind.CARD, "limits", 4, 6),
            Wanted(Kind.APP, "APP|a|", 1, 1),
        )
        val layout = HomeGrid.reconcile(emptyList(), wanted, cols, rows)
        assertEquals(listOf("CARD:limits"), layout.items.map { it.id })
        assertEquals(listOf("APP:APP|a|"), layout.unplaced.map { it.id })
    }

    @Test
    fun reconcileIgnoresRepeatedWantedItems() {
        val w = Wanted(Kind.APP, "APP|a|", 1, 1)
        val layout = HomeGrid.reconcile(emptyList(), listOf(w, w), cols, rows)
        assertEquals(1, layout.items.size)
    }

    // ── saving ────────────────────────────────────────────────────────────────

    @Test
    fun serializeThenParseGivesTheSameItems() {
        val items = listOf(
            card("clock", 0, 0, 4, 2),
            Item(Kind.APP, "APP|com.example|", 2, 2, 1, 1),
            Item(Kind.SHORTCUT, "SHORTCUT|com.example|a b\tc\nd", 3, 2, 1, 1),
            widget(42, 0, 4, 4, 2),
        )
        assertEquals(items, HomeGrid.parse(HomeGrid.serialize(items)))
    }

    @Test
    fun parseSkipsGarbageAndRepeats() {
        val raw = listOf(
            "CARD\tclock\t0\t0\t4\t2",
            "NOPE\tx\t0\t0\t1\t1",
            "APP\tabc\tx\t0\t1\t1",
            "APP\tabc\t0\t0\t0\t1",
            "APP\tabc\t-1\t0\t1\t1",
            "APP\t\t0\t0\t1\t1",
            "APP\tabc\t0\t0",
            "",
            "CARD\tclock\t1\t1\t1\t1", // same id again
        ).joinToString("\n")
        assertEquals(listOf(card("clock", 0, 0, 4, 2)), HomeGrid.parse(raw))
    }

    @Test
    fun parseOfNothingIsEmpty() {
        assertTrue(HomeGrid.parse(null).isEmpty())
        assertTrue(HomeGrid.parse("   ").isEmpty())
    }

    // ── moving over from the list home screen ─────────────────────────────────

    @Test
    fun rowsForRoundsUp() {
        assertEquals(1, HomeGrid.rowsFor(0, 100))
        assertEquals(2, HomeGrid.rowsFor(101, 100))
        assertEquals(3, HomeGrid.rowsFor(260, 100))
    }

    @Test
    fun wantedInListOrderFollowsTheSavedOrderAndSkipsCardsThatAreOff() {
        val wanted = HomeGrid.wantedInListOrder(
            order = listOf("status", "clock", "pinned", "widgets", "task", "limits"),
            cards = setOf("clock", "status"),
            pinnedKeys = listOf("APP|a|", "SHORTCUT|b|x"),
            widgets = listOf(HomeGrid.WidgetWant(9, 180)),
            columns = cols,
            cellHeightDp = 90,
        )
        assertEquals(
            listOf("CARD:status", "CARD:clock", "APP:APP|a|", "SHORTCUT:SHORTCUT|b|x", "WIDGET:9"),
            wanted.map { it.id },
        )
        val widget = wanted.last()
        assertEquals(cols, widget.spanX)
        assertEquals(2, widget.spanY)
    }

    @Test
    fun aTypicalListHomeMigratesWithEverythingCardsShownAndSqueezedToFit() {
        val wanted = HomeGrid.wantedInListOrder(
            order = listOf("clock", "status", "task", "limits", "pinned", "widgets"),
            cards = setOf("clock", "status", "task", "limits"),
            pinnedKeys = (1..6).map { "APP|p$it|" },
            widgets = emptyList(),
            columns = cols,
            cellHeightDp = 90,
        )
        val layout = HomeGrid.reconcile(emptyList(), wanted, cols, rows)
        noOverlapAndInBounds(layout.items)
        assertEquals(Cell(0, 0), cell(layout, "CARD:clock"))
        assertEquals(Cell(0, 2), cell(layout, "CARD:status"))
        assertEquals(Cell(0, 3), cell(layout, "CARD:task"))
        // The limits card wants 2 rows but only one full-width row is left, so it is squeezed
        // into it rather than left off.
        assertEquals(Item(Kind.CARD, "limits", 0, 5, 4, 1), layout.items.first { it.id == "CARD:limits" })
        // Icons take the gap beside the status card; the rest have no room and are reported.
        assertEquals(Cell(2, 2), cell(layout, "APP:APP|p1|"))
        assertEquals(Cell(3, 2), cell(layout, "APP:APP|p2|"))
        assertEquals(4, layout.unplaced.size)
        // Everything wanted is either placed or reported.
        assertEquals(wanted.size, layout.items.size + layout.unplaced.size)
    }

    @Test
    fun aNewItemIsSqueezedToFewerRowsBeforeBeingLeftOut() {
        val saved = listOf(card("clock", 0, 0, 4, 2), card("task", 0, 2, 4, 2))
        val wanted = listOf(
            Wanted(Kind.CARD, "clock", 4, 2),
            Wanted(Kind.CARD, "task", 4, 2),
            Wanted(Kind.WIDGET, "5", 4, 3), // wants 3 rows, only 2 are left
        )
        val layout = HomeGrid.reconcile(saved, wanted, cols, rows)
        assertEquals(Item(Kind.WIDGET, "5", 0, 4, 4, 2), layout.items.last())
        assertTrue(layout.unplaced.isEmpty())
    }

    @Test
    fun anItemThatCannotFitEvenOneRowHighIsReported() {
        val saved = listOf(card("limits", 0, 0, 4, 6))
        val wanted = listOf(Wanted(Kind.CARD, "limits", 4, 2), Wanted(Kind.WIDGET, "5", 4, 3))
        val layout = HomeGrid.reconcile(saved, wanted, cols, rows)
        assertEquals(listOf("WIDGET:5"), layout.unplaced.map { it.id })
    }

    @Test
    fun aDormantCardKeepsItsSavedPlaceWithoutTakingRoom() {
        val saved = listOf(card("task", 0, 2, 4, 2), app("a", 0, 0))
        val wanted = listOf(Wanted(Kind.APP, "APP|a|", 1, 1), Wanted(Kind.APP, "APP|b|", 1, 1))
        val layout = HomeGrid.reconcile(saved, wanted, cols, rows, dormant = setOf("CARD:task"))
        // b does not land in the task card's old place; the card is parked, not placed.
        assertEquals(listOf("APP:APP|a|", "APP:APP|b|"), layout.items.map { it.id })
        assertEquals(listOf(card("task", 0, 2, 4, 2)), layout.parked)
        // When the card is wanted again it returns to its place.
        val back = HomeGrid.reconcile(layout.items + layout.parked, wanted + Wanted(Kind.CARD, "task", 4, 2), cols, rows)
        assertEquals(Cell(0, 2), cell(back, "CARD:task"))
    }

    @Test
    fun aCardThatIsNotWantedAndNotDormantIsForgotten() {
        val saved = listOf(card("task", 0, 2, 4, 2))
        val layout = HomeGrid.reconcile(saved, emptyList(), cols, rows)
        assertTrue(layout.items.isEmpty())
        assertTrue(layout.parked.isEmpty())
    }

    private fun cell(layout: HomeGrid.Layout, id: String): Cell {
        val item = layout.items.first { it.id == id }
        return Cell(item.col, item.row)
    }
}
