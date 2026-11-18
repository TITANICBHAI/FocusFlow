package com.tbtechs.focusflow.enforcement

import com.tbtechs.focusflow.enforcement.HomeWidgetEntries.Entry
import com.tbtechs.focusflow.enforcement.HomeWidgetEntries.Size
import com.tbtechs.focusflow.enforcement.HomeWidgetEntries.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWidgetEntriesTest {
    private fun w(id: Int, provider: String = "com.x/.W$id", size: Size = Size.MEDIUM) = Entry(id, provider, size)

    @Test
    fun addingKeepsOrder() {
        val one = HomeWidgetEntries.add(emptyList(), w(1))
        val two = HomeWidgetEntries.add(one.items, w(2))
        assertEquals(Status.ADDED, two.status)
        assertEquals(listOf(1, 2), two.items.map { it.appWidgetId })
    }

    @Test
    fun theSameWidgetIdIsNotAddedTwice() {
        val once = HomeWidgetEntries.add(emptyList(), w(1)).items
        val again = HomeWidgetEntries.add(once, w(1))
        assertEquals(Status.DUPLICATE, again.status)
        assertEquals(1, again.items.size)
    }

    @Test
    fun theSameWidgetAddedTwiceUnderDifferentIdsIsAllowed() {
        // Two clocks, say: Android gives each placement its own id.
        val once = HomeWidgetEntries.add(emptyList(), w(1, "com.x/.Clock")).items
        val two = HomeWidgetEntries.add(once, w(2, "com.x/.Clock"))
        assertEquals(Status.ADDED, two.status)
    }

    @Test
    fun aFullHomeRefusesAnotherWidget() {
        var items = emptyList<Entry>()
        for (i in 1..HomeWidgetEntries.MAX_WIDGETS) items = HomeWidgetEntries.add(items, w(i)).items
        val extra = HomeWidgetEntries.add(items, w(99))
        assertEquals(Status.FULL, extra.status)
        assertEquals(HomeWidgetEntries.MAX_WIDGETS, extra.items.size)
    }

    @Test
    fun removeAndResizeOnlyTouchTheNamedWidget() {
        val items = listOf(w(1), w(2), w(3))
        assertEquals(listOf(1, 3), HomeWidgetEntries.remove(items, 2).map { it.appWidgetId })
        val resized = HomeWidgetEntries.setSize(items, 2, Size.LARGE)
        assertEquals(listOf(Size.MEDIUM, Size.LARGE, Size.MEDIUM), resized.map { it.size })
    }

    @Test
    fun aWidgetIsNeverDrawnShorterThanItNeeds() {
        assertEquals(180, HomeWidgetEntries.heightDp(Size.MEDIUM, 90))
        assertEquals(300, HomeWidgetEntries.heightDp(Size.MEDIUM, 300))
        assertEquals(110, HomeWidgetEntries.heightDp(Size.SMALL, 0))
    }

    @Test
    fun saveAndLoadRoundTrip() {
        val items = listOf(w(7, "com.google.android.apps.weather/.WeatherWidget$1", Size.SMALL), w(9, "a b/.C", Size.LARGE))
        assertEquals(items, HomeWidgetEntries.parse(HomeWidgetEntries.serialize(items)))
    }

    @Test
    fun damagedValuesAreSkippedNotFatal() {
        assertTrue(HomeWidgetEntries.parse(null).isEmpty())
        assertTrue(HomeWidgetEntries.parse("").isEmpty())
        assertTrue(HomeWidgetEntries.parse("x\ty\tz\nonly-two\tfields").isEmpty())
        assertTrue(HomeWidgetEntries.parse("5\t\tm").isEmpty())
        val good = HomeWidgetEntries.serialize(listOf(w(4)))
        assertEquals(1, HomeWidgetEntries.parse("junk\n$good\nmore junk").size)
        // An unknown size falls back to medium instead of dropping the widget.
        assertEquals(Size.MEDIUM, HomeWidgetEntries.parse("3\tcom.x%2F.W\tzzz").single().size)
    }

    @Test
    fun repeatedIdsInStoredTextCollapse() {
        val one = HomeWidgetEntries.serialize(listOf(w(1)))
        assertEquals(1, HomeWidgetEntries.parse("$one\n$one").size)
    }
}
