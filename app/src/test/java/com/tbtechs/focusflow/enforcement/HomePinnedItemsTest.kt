package com.tbtechs.focusflow.enforcement

import com.tbtechs.focusflow.enforcement.HomePinnedItems.Entry
import com.tbtechs.focusflow.enforcement.HomePinnedItems.Kind
import com.tbtechs.focusflow.enforcement.HomePinnedItems.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePinnedItemsTest {
    private fun app(pkg: String) = Entry(Kind.APP, pkg, "", pkg.substringAfterLast('.'))
    private fun site(id: String, label: String = "Site") = Entry(Kind.SHORTCUT, "com.chrome", id, label)

    @Test
    fun addingKeepsOrderAndReportsAdded() {
        val first = HomePinnedItems.add(emptyList(), app("com.a"))
        val second = HomePinnedItems.add(first.items, site("s1"))
        assertEquals(Status.ADDED, second.status)
        assertEquals(listOf("com.a", "com.chrome"), second.items.map { it.packageName })
    }

    @Test
    fun theSameThingCannotBePinnedTwice() {
        val once = HomePinnedItems.add(emptyList(), app("com.a")).items
        val again = HomePinnedItems.add(once, app("com.a"))
        assertEquals(Status.DUPLICATE, again.status)
        assertEquals(1, again.items.size)
    }

    @Test
    fun twoWebsitesFromTheSameBrowserAreDifferentItems() {
        val one = HomePinnedItems.add(emptyList(), site("s1")).items
        val two = HomePinnedItems.add(one, site("s2"))
        assertEquals(Status.ADDED, two.status)
        assertEquals(2, two.items.size)
    }

    @Test
    fun aFullGridRefusesAnotherItem() {
        val capacity = HomePinnedItems.capacity(4)
        var items = emptyList<Entry>()
        for (i in 1..capacity) items = HomePinnedItems.add(items, app("com.app$i"), capacity).items
        val extra = HomePinnedItems.add(items, app("com.extra"), capacity)
        assertEquals(Status.FULL, extra.status)
        assertEquals(capacity, extra.items.size)
    }

    @Test
    fun gridsAreSquareAndLimitedToThreeToFive() {
        assertEquals(9, HomePinnedItems.capacity(3))
        assertEquals(16, HomePinnedItems.capacity(4))
        assertEquals(25, HomePinnedItems.capacity(5))
        assertEquals(25, HomePinnedItems.MAX_ITEMS)
    }

    @Test
    fun theSavedSizeFallsBackToFourWhenMissingOrOutOfRange() {
        assertEquals(4, HomePinnedItems.columnsFromPref(null))
        assertEquals(4, HomePinnedItems.columnsFromPref(""))
        assertEquals(4, HomePinnedItems.columnsFromPref("2"))
        assertEquals(4, HomePinnedItems.columnsFromPref("9"))
        assertEquals(4, HomePinnedItems.columnsFromPref("big"))
        assertEquals(3, HomePinnedItems.columnsFromPref("3"))
        assertEquals(5, HomePinnedItems.columnsFromPref("5"))
    }

    @Test
    fun aBiggerGridKeepsEverythingASmallerOneHid() {
        // Shrinking the grid never deletes anything: the stored text still holds all of it.
        var items = emptyList<Entry>()
        for (i in 1..20) items = HomePinnedItems.add(items, app("com.app$i"), HomePinnedItems.capacity(5)).items
        assertEquals(20, HomePinnedItems.parse(HomePinnedItems.serialize(items)).size)
    }

    @Test
    fun removingByKeyOrByAppLeavesTheRest() {
        val items = listOf(app("com.a"), site("s1"), app("com.b"))
        assertEquals(listOf("com.a", "com.b"), HomePinnedItems.remove(items, site("s1").key).map { it.packageName })
        assertEquals(2, HomePinnedItems.removeApp(items, "com.a").size)
        // Removing an app never removes a shortcut that happens to share its package.
        val mixed = listOf(Entry(Kind.APP, "com.chrome", "", "Chrome"), site("s1"))
        assertEquals(1, HomePinnedItems.removeApp(mixed, "com.chrome").size)
    }

    @Test
    fun saveAndLoadRoundTripAnyCharacters() {
        val items = listOf(
            app("com.a"),
            site("id with spaces/and|bars", "Wiki | pedia\tnew\nline ✓ 日本語"),
        )
        assertEquals(items, HomePinnedItems.parse(HomePinnedItems.serialize(items)))
    }

    @Test
    fun damagedValuesAreSkippedNotFatal() {
        assertTrue(HomePinnedItems.parse(null).isEmpty())
        assertTrue(HomePinnedItems.parse("").isEmpty())
        assertTrue(HomePinnedItems.parse("nonsense\nAPP\tonly-two").isEmpty())
        // A shortcut with no id cannot be launched, so it is dropped.
        assertTrue(HomePinnedItems.parse("SHORTCUT\tcom.x\t\tLabel").isEmpty())
        val good = HomePinnedItems.serialize(listOf(app("com.ok")))
        assertEquals(1, HomePinnedItems.parse("garbage\n$good\nmore garbage").size)
    }

    @Test
    fun repeatedEntriesInStoredTextCollapse() {
        val one = HomePinnedItems.serialize(listOf(app("com.a")))
        assertEquals(1, HomePinnedItems.parse("$one\n$one").size)
    }

    @Test
    fun containsMatchesKindPackageAndId() {
        val items = listOf(app("com.a"), site("s1"))
        assertTrue(HomePinnedItems.contains(items, Kind.APP, "com.a", ""))
        assertTrue(HomePinnedItems.contains(items, Kind.SHORTCUT, "com.chrome", "s1"))
        assertFalse(HomePinnedItems.contains(items, Kind.SHORTCUT, "com.chrome", "s2"))
        assertFalse(HomePinnedItems.contains(items, Kind.APP, "com.chrome", ""))
    }
}
