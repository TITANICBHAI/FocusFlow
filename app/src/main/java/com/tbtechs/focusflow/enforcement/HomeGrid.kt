package com.tbtechs.focusflow.enforcement

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Where things sit on the launcher's grid home screen: a fixed grid of cells, like a stock
 * launcher. Every item has a top-left cell and a size in cells. Icons are 1 by 1; an app widget or
 * a FocusFlow card (clock, status, task, limits) covers several cells.
 *
 * This file only knows positions. Which icons are pinned, which widgets are hosted and which cards
 * are switched on stay in their own saved settings (HomePinnedItems, HomeWidgetEntries and the card
 * styles), so switching between the list home and the grid home never loses anything. [reconcile]
 * lines the two up: it keeps the positions that are still wanted, drops the ones that are gone and
 * puts anything new in the first free spot.
 *
 * Kept free of Android so the rules can be tested.
 */
object HomeGrid {
    /** Preference key for the saved positions. */
    const val PREF_KEY = "launcher_home_grid"

    const val DEFAULT_COLUMNS = 4
    const val DEFAULT_ROWS = 6

    enum class Kind { CARD, APP, SHORTCUT, WIDGET }

    /**
     * One item on the grid. [key] says which one it is: the card's name ("clock"), a pinned entry's
     * key (see HomePinnedItems.Entry.key), or an app widget's id written as text.
     */
    data class Item(
        val kind: Kind,
        val key: String,
        val col: Int,
        val row: Int,
        val spanX: Int,
        val spanY: Int,
    ) {
        val id: String get() = id(kind, key)
        val right: Int get() = col + spanX
        val bottom: Int get() = row + spanY

        fun overlaps(other: Item): Boolean =
            col < other.right && other.col < right && row < other.bottom && other.row < bottom
    }

    fun id(kind: Kind, key: String): String = "${kind.name}:$key"

    /** Something that should be on the home screen, and the size it takes when it first goes there. */
    data class Wanted(val kind: Kind, val key: String, val spanX: Int, val spanY: Int) {
        val id: String get() = id(kind, key)
    }

    /** A cell, named by its column and row. */
    data class Cell(val col: Int, val row: Int)

    /** What [reconcile] made: the placed items, and the wanted ones that had no room. */
    class Layout(val items: List<Item>, val unplaced: List<Wanted>)

    enum class Status { ADDED, DUPLICATE, FULL }

    class AddOutcome(val items: List<Item>, val status: Status)

    // ── Looking at the grid ────────────────────────────────────────────────────

    /** True when the block lies inside the grid and no item (other than [ignoreId]) is in it. */
    fun fits(
        items: List<Item>,
        columns: Int,
        rows: Int,
        col: Int,
        row: Int,
        spanX: Int,
        spanY: Int,
        ignoreId: String? = null,
    ): Boolean {
        if (spanX < 1 || spanY < 1 || col < 0 || row < 0) return false
        if (col + spanX > columns || row + spanY > rows) return false
        return items.none { it.id != ignoreId && overlapsBlock(it, col, row, spanX, spanY) }
    }

    /** The first free spot reading left to right, then top to bottom, or null when it is full. */
    fun firstFree(
        items: List<Item>,
        columns: Int,
        rows: Int,
        spanX: Int,
        spanY: Int,
        ignoreId: String? = null,
    ): Cell? {
        for (row in 0..rows - spanY) for (col in 0..columns - spanX) {
            if (fits(items, columns, rows, col, row, spanX, spanY, ignoreId)) return Cell(col, row)
        }
        return null
    }

    /**
     * The free spot whose top-left cell is closest to ([x], [y]), given in cells (so 1.5 is the
     * middle of the second column). Ties go to the upper, then the left one. Null when nothing fits.
     */
    fun nearestFree(
        items: List<Item>,
        columns: Int,
        rows: Int,
        spanX: Int,
        spanY: Int,
        x: Float,
        y: Float,
        ignoreId: String? = null,
    ): Cell? {
        var best: Cell? = null
        var bestDistance = Float.MAX_VALUE
        for (row in 0..rows - spanY) for (col in 0..columns - spanX) {
            if (!fits(items, columns, rows, col, row, spanX, spanY, ignoreId)) continue
            val dx = col - x
            val dy = row - y
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                best = Cell(col, row)
                bestDistance = distance
            }
        }
        return best
    }

    /** The cell a block's top-left corner is nearest to, kept inside the grid. */
    fun snap(columns: Int, rows: Int, spanX: Int, spanY: Int, x: Float, y: Float): Cell = Cell(
        Math.round(x).coerceIn(0, (columns - spanX).coerceAtLeast(0)),
        Math.round(y).coerceIn(0, (rows - spanY).coerceAtLeast(0)),
    )

    // ── Changing the grid ──────────────────────────────────────────────────────

    /** Puts a new item in the first free spot. */
    fun add(items: List<Item>, columns: Int, rows: Int, wanted: Wanted): AddOutcome {
        if (items.any { it.id == wanted.id }) return AddOutcome(items, Status.DUPLICATE)
        val spot = firstFree(items, columns, rows, wanted.spanX, wanted.spanY)
            ?: return AddOutcome(items, Status.FULL)
        val item = Item(wanted.kind, wanted.key, spot.col, spot.row, wanted.spanX, wanted.spanY)
        return AddOutcome(items + item, Status.ADDED)
    }

    fun remove(items: List<Item>, id: String): List<Item> = items.filterNot { it.id == id }

    /**
     * Moves and/or resizes one item, and moves any it lands on out of the way: down as far as needed,
     * or up when there is no room below. The position and size are first kept inside the grid.
     * Returns null when the item is unknown or there is no way to make room, in which case nothing
     * should change. Items the move does not touch stay exactly where they are.
     */
    fun place(
        items: List<Item>,
        columns: Int,
        rows: Int,
        id: String,
        col: Int,
        row: Int,
        spanX: Int,
        spanY: Int,
    ): List<Item>? {
        val old = items.firstOrNull { it.id == id } ?: return null
        if (columns < 1 || rows < 1) return null
        val sx = spanX.coerceIn(1, columns)
        val sy = spanY.coerceIn(1, rows)
        val moved = old.copy(
            col = col.coerceIn(0, columns - sx),
            row = row.coerceIn(0, rows - sy),
            spanX = sx,
            spanY = sy,
        )
        val others = items.filter { it.id != id }
        val pushed = pushDown(others, moved, rows)
            ?: pushUp(others, moved, rows)
            ?: return null
        val byId = pushed.associateBy { it.id }
        return items.map { if (it.id == id) moved else byId.getValue(it.id) }
    }

    /** [place] with the size left alone. */
    fun move(items: List<Item>, columns: Int, rows: Int, id: String, col: Int, row: Int): List<Item>? {
        val old = items.firstOrNull { it.id == id } ?: return null
        return place(items, columns, rows, id, col, row, old.spanX, old.spanY)
    }

    /** [place] with the top-left corner left alone. */
    fun resize(items: List<Item>, columns: Int, rows: Int, id: String, spanX: Int, spanY: Int): List<Item>? {
        val old = items.firstOrNull { it.id == id } ?: return null
        return place(items, columns, rows, id, old.col, old.row, spanX, spanY)
    }

    // Reads the others top to bottom and, whenever one overlaps something already settled (the moved
    // item first), slides it down to just below. Items that overlap nothing do not move.
    private fun pushDown(others: List<Item>, anchor: Item, rows: Int): List<Item>? {
        val settled = ArrayList<Item>()
        settled += anchor
        val out = ArrayList<Item>()
        for (item in others.sortedWith(compareBy({ it.row }, { it.col }))) {
            var current = item
            while (true) {
                val hit = settled.filter { current.overlaps(it) }
                if (hit.isEmpty()) break
                current = current.copy(row = hit.maxOf { it.bottom })
                if (current.bottom > rows) return null
            }
            settled += current
            out += current
        }
        return out
    }

    // The same push, mirrored, for when there is no room below.
    private fun pushUp(others: List<Item>, anchor: Item, rows: Int): List<Item>? {
        val flipped = pushDown(others.map { flip(it, rows) }, flip(anchor, rows), rows) ?: return null
        return flipped.map { flip(it, rows) }
    }

    private fun flip(item: Item, rows: Int): Item = item.copy(row = rows - item.row - item.spanY)

    private fun overlapsBlock(item: Item, col: Int, row: Int, spanX: Int, spanY: Int): Boolean =
        item.col < col + spanX && col < item.right && item.row < row + spanY && row < item.bottom

    // ── Resizing by an edge ────────────────────────────────────────────────────

    enum class Edge { LEFT, TOP, RIGHT, BOTTOM }

    /**
     * The block that results from dragging one [edge] of [item] to ([x], [y]), given in cells
     * (so 2.0 is the line between the second and third column). Only the dragged edge moves, in
     * whole cells; the opposite edge stays put, and the block never gets smaller than one cell or
     * leaves the grid.
     */
    fun resizedBlock(item: Item, edge: Edge, x: Float, y: Float, columns: Int, rows: Int): Item =
        when (edge) {
            Edge.RIGHT -> item.copy(spanX = (Math.round(x) - item.col).coerceIn(1, (columns - item.col).coerceAtLeast(1)))
            Edge.BOTTOM -> item.copy(spanY = (Math.round(y) - item.row).coerceIn(1, (rows - item.row).coerceAtLeast(1)))
            Edge.LEFT -> {
                val left = Math.round(x).coerceIn(0, (item.right - 1).coerceAtLeast(0))
                item.copy(col = left, spanX = item.right - left)
            }
            Edge.TOP -> {
                val top = Math.round(y).coerceIn(0, (item.bottom - 1).coerceAtLeast(0))
                item.copy(row = top, spanY = item.bottom - top)
            }
        }

    /**
     * Which edge of the rectangle a touch at ([x], [y]) is grabbing, or null if it is not near one.
     * "Near" is within [band] of the edge, on either side of it, along the edge's length. In a
     * corner the closer edge wins.
     */
    fun edgeHit(left: Float, top: Float, right: Float, bottom: Float, x: Float, y: Float, band: Float): Edge? {
        if (x < left - band || x > right + band || y < top - band || y > bottom + band) return null
        var best: Edge? = null
        var bestDistance = band + 0.001f
        fun consider(edge: Edge, distance: Float) {
            if (distance < bestDistance) {
                best = edge
                bestDistance = distance
            }
        }
        consider(Edge.LEFT, Math.abs(x - left))
        consider(Edge.RIGHT, Math.abs(x - right))
        consider(Edge.TOP, Math.abs(y - top))
        consider(Edge.BOTTOM, Math.abs(y - bottom))
        return best
    }

    // ── Lining the grid up with what is wanted ─────────────────────────────────

    /**
     * Makes the saved positions match [wanted]. An item that is still wanted keeps its place and
     * size (cut down to fit if the grid got smaller). One that is no longer wanted is dropped. A
     * wanted item with no saved place, or whose saved place is now taken or outside the grid, goes
     * in the first free spot, in the order given. Anything with no room is returned in
     * [Layout.unplaced] and never silently lost.
     */
    fun reconcile(saved: List<Item>, wanted: List<Wanted>, columns: Int, rows: Int): Layout {
        val wantedById = LinkedHashMap<String, Wanted>()
        for (w in wanted) wantedById.putIfAbsent(w.id, w)
        val kept = ArrayList<Item>()
        for (item in saved) {
            val want = wantedById[item.id] ?: continue
            if (kept.any { it.id == item.id }) continue
            val sx = item.spanX.coerceIn(1, columns.coerceAtLeast(1))
            val sy = item.spanY.coerceIn(1, rows.coerceAtLeast(1))
            val fitted = item.copy(spanX = sx, spanY = sy)
            if (fits(kept, columns, rows, fitted.col, fitted.row, sx, sy)) kept += fitted
        }
        val keptIds = kept.map { it.id }.toSet()
        val unplaced = ArrayList<Wanted>()
        for (want in wantedById.values) {
            if (want.id in keptIds) continue
            val sx = want.spanX.coerceIn(1, columns.coerceAtLeast(1))
            val sy = want.spanY.coerceIn(1, rows.coerceAtLeast(1))
            val spot = firstFree(kept, columns, rows, sx, sy)
            if (spot == null) unplaced += want
            else kept += Item(want.kind, want.key, spot.col, spot.row, sx, sy)
        }
        return Layout(kept, unplaced)
    }

    // ── Moving over from the list home screen ──────────────────────────────────

    /** The default size, in cells, of a FocusFlow card the first time it goes on the grid. */
    fun cardSpan(card: String, columns: Int): Pair<Int, Int> = when (card) {
        "clock" -> columns to 2
        "status" -> minOf(2, columns) to 1
        "task" -> columns to 2
        "limits" -> columns to 3
        else -> columns to 1
    }

    /** A widget as the list home screen knew it: its id and how tall it was drawn. */
    data class WidgetWant(val appWidgetId: Int, val heightDp: Int)

    /** Cells tall needed to be at least as tall as [heightDp]. */
    fun rowsFor(heightDp: Int, cellHeightDp: Int): Int =
        ((heightDp + cellHeightDp - 1) / cellHeightDp.coerceAtLeast(1)).coerceAtLeast(1)

    /**
     * Everything the list home screen shows, as wanted items, in the order it showed them: the saved
     * block [order] ("clock", "status", "task", "limits", "pinned", "widgets"), the cards that are
     * switched on in [cards], each pinned icon in [pinnedKeys] order, and each hosted widget. Feed
     * this to [reconcile] with nothing saved to get the first grid layout.
     */
    fun wantedInListOrder(
        order: List<String>,
        cards: Set<String>,
        pinnedKeys: List<String>,
        widgets: List<WidgetWant>,
        columns: Int,
        cellHeightDp: Int,
    ): List<Wanted> {
        val out = ArrayList<Wanted>()
        for (block in order) when (block) {
            "clock", "status", "task", "limits" -> if (block in cards) {
                val (sx, sy) = cardSpan(block, columns)
                out += Wanted(Kind.CARD, block, sx, sy)
            }
            "pinned" -> for (key in pinnedKeys) {
                val kind = if (key.startsWith("${HomePinnedItems.Kind.APP.name}|")) Kind.APP else Kind.SHORTCUT
                out += Wanted(kind, key, 1, 1)
            }
            "widgets" -> for (w in widgets) {
                out += Wanted(Kind.WIDGET, w.appWidgetId.toString(), columns, rowsFor(w.heightDp, cellHeightDp))
            }
        }
        return out
    }

    // ── Saving ─────────────────────────────────────────────────────────────────

    /** One item per line: kind, key (URL-encoded), column, row, width, height, separated by tabs. */
    fun serialize(items: List<Item>): String = items.joinToString("\n") { i ->
        listOf(i.kind.name, URLEncoder.encode(i.key, "UTF-8"), i.col, i.row, i.spanX, i.spanY)
            .joinToString("\t")
    }

    /**
     * Skips anything malformed or repeated, so a damaged value can never break the home screen. It
     * does not check the items against a grid size; [reconcile] does that.
     */
    fun parse(raw: String?): List<Item> {
        if (raw.isNullOrBlank()) return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<Item>()
        for (line in raw.split('\n')) {
            val parts = line.split('\t')
            if (parts.size != 6) continue
            val kind = Kind.values().firstOrNull { it.name == parts[0] } ?: continue
            val key = try {
                URLDecoder.decode(parts[1], "UTF-8")
            } catch (_: Exception) {
                continue
            }
            if (key.isBlank()) continue
            val col = parts[2].toIntOrNull() ?: continue
            val row = parts[3].toIntOrNull() ?: continue
            val sx = parts[4].toIntOrNull() ?: continue
            val sy = parts[5].toIntOrNull() ?: continue
            if (col < 0 || row < 0 || sx < 1 || sy < 1) continue
            val item = Item(kind, key, col, row, sx, sy)
            if (seen.add(item.id)) out += item
        }
        return out
    }
}
