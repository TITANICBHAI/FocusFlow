package com.tbtechs.focusflow.enforcement.customrules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyWithHandleTest {
    private fun node(
        bounds: String,
        id: String? = null,
        text: String? = null,
        desc: String? = null,
        cls: String = "android.view.View",
    ) = NodeSnapshot(
        depth = 0,
        viewId = id?.let { "com.app:id/$it" },
        className = cls,
        text = text,
        desc = desc,
        bounds = bounds,
    )

    // A 1000 x 2000 screen with a bottom tab bar holding a "Reels" tab that holds an icon.
    private val root = node("0,0,1000,2000", id = "content")
    private val bar = node("0,1800,1000,2000", id = "tab_bar")
    private val reelsTab = node("400,1800,600,2000", desc = "Reels")
    private val icon = node("450,1850,550,1950", cls = "android.widget.ImageView")

    @Test
    fun boxIsReadFromFourNumbersAndRefusesAnythingElse() {
        assertEquals(listOf(1, 2, 3, 4), node("1,2,3,4").boxOrNull()?.toList())
        assertEquals(listOf(1, 2, 3, 4), node(" 1, 2 ,3,4 ").boxOrNull()?.toList())
        assertNull(node("1,2,3").boxOrNull())
        assertNull(node("a,b,c,d").boxOrNull())
        assertNull(node("5,5,5,9").boxOrNull()) // no width
        assertNull(node("5,5,9,3").boxOrNull()) // upside down
    }

    @Test
    fun anIconInsideANamedTabSuggestsTheSmallestNamedElementAroundIt() {
        val picked = nearbyWithHandle(listOf(root, bar, reelsTab, icon), icon)
        assertSame(reelsTab, picked)
    }

    @Test
    fun whenOnlyABigContainerHasAHandleThatContainerIsUsed() {
        assertSame(bar, nearbyWithHandle(listOf(root, bar, icon), icon))
    }

    @Test
    fun anElementThatFillsMostOfTheScreenIsNeverSuggested() {
        assertNull(nearbyWithHandle(listOf(root, icon), icon))
    }

    @Test
    fun anUnnamedContainerSuggestsTheNamedElementInsideItClosestToItsMiddle() {
        val container = node("0,100,1000,300")
        val middle = node("450,150,550,250", text = "Following")
        val edge = node("700,150,900,250", text = "For you")
        assertSame(middle, nearbyWithHandle(listOf(container, edge, middle), container))
    }

    @Test
    fun aNamedNeighbourIsUsedOnlyWhenItIsClose() {
        val unnamed = node("100,100,200,200", cls = "android.widget.ImageView")
        val near = node("210,100,300,200", text = "Reels")
        val farAway = node("100,900,400,1000", text = "Reels")
        assertSame(near, nearbyWithHandle(listOf(root, unnamed, farAway, near), unnamed))
        assertNull(nearbyWithHandle(listOf(unnamed, farAway), unnamed))
    }

    @Test
    fun nothingIsSuggestedWhenTheBoxesCannotBeRead() {
        val broken = node("oops", cls = "android.widget.ImageView")
        assertNull(nearbyWithHandle(listOf(root, reelsTab, broken), broken))
        assertNull(nearbyWithHandle(listOf(broken), broken))
    }

    @Test
    fun theTargetItselfAndOtherUnnamedElementsAreNeverReturned() {
        val other = node("440,1840,560,1960", cls = "android.widget.ImageView")
        assertNull(nearbyWithHandle(listOf(icon, other), icon))
    }
}
