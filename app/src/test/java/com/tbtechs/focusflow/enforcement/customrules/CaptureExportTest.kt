package com.tbtechs.focusflow.enforcement.customrules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureExportTest {
    private fun node(
        depth: Int,
        bounds: String,
        id: String? = null,
        text: String? = null,
        cls: String? = "android.widget.TextView",
    ) = NodeSnapshot(depth, id, cls, text, null, bounds)

    @Test
    fun parsesBounds() {
        assertEquals(NodeBox(0, 10, 100, 200), parseNodeBounds("0,10,100,200"))
        assertEquals(NodeBox(1, 2, 3, 4), parseNodeBounds(" 1, 2 ,3,4 "))
        assertNull(parseNodeBounds("0,10,100"))
        assertNull(parseNodeBounds("a,b,c,d"))
        assertNull(parseNodeBounds(""))
    }

    @Test
    fun boxMath() {
        val box = NodeBox(10, 20, 110, 220)
        assertEquals(100, box.width)
        assertEquals(200, box.height)
        assertEquals(20_000L, box.area)
        assertTrue(box.contains(10f, 20f))
        assertFalse(box.contains(110f, 20f))
        assertTrue(NodeBox(5, 5, 5, 50).isEmpty)
        assertEquals(0L, NodeBox(50, 50, 10, 10).area)
        assertTrue(box.intersects(NodeBox(100, 200, 300, 300)))
        assertFalse(box.intersects(NodeBox(110, 0, 300, 300)))
    }

    @Test
    fun screenBoxPrefersTheRootNode() {
        val nodes = listOf(node(0, "0,0,1080,2400"), node(1, "0,0,5000,9000"))
        assertEquals(NodeBox(0, 0, 1080, 2400), CaptureMap.screenBox(nodes))
    }

    @Test
    fun screenBoxFallsBackToTheUnion() {
        val nodes = listOf(node(0, "0,0,0,0"), node(1, "10,10,100,100"), node(1, "50,60,200,300"))
        assertEquals(NodeBox(10, 10, 200, 300), CaptureMap.screenBox(nodes))
        assertNull(CaptureMap.screenBox(listOf(node(0, "bad"), node(1, "0,0,0,0"))))
        assertNull(CaptureMap.screenBox(emptyList()))
    }

    @Test
    fun visibleIndicesSkipEmptyMalformedAndOffScreenNodes() {
        val screen = NodeBox(0, 0, 1000, 2000)
        val nodes = listOf(
            node(0, "0,0,1000,2000"),
            node(1, "0,0,0,0"),
            node(1, "oops"),
            node(1, "0,2500,1000,3000"), // scrolled off screen
            node(1, "100,100,300,300"),
        )
        assertEquals(listOf(0, 4), CaptureMap.visibleIndices(nodes, screen))
    }

    @Test
    fun hitTestPicksTheSmallestNodeAndTheDeeperOneOnTies() {
        val screen = NodeBox(0, 0, 1000, 2000)
        val nodes = listOf(
            node(0, "0,0,1000,2000"),
            node(1, "0,0,1000,500"),
            node(2, "100,100,400,300"),
            node(3, "100,100,400,300"), // same box, deeper
        )
        assertEquals(3, CaptureMap.hitTest(nodes, screen, 150f, 150f))
        assertEquals(1, CaptureMap.hitTest(nodes, screen, 800f, 400f))
        assertEquals(0, CaptureMap.hitTest(nodes, screen, 800f, 1500f))
        assertNull(CaptureMap.hitTest(nodes, screen, 2000f, 2000f))
    }

    @Test
    fun exportListsEveryNodeWithIndentAndDetails() {
        val result = CaptureResult(
            packageName = "com.example.app",
            nodes = listOf(
                node(0, "0,0,1080,2400", cls = "android.widget.FrameLayout"),
                node(1, "0,0,500,100", id = "com.example.app:id/reel_title", text = "Hello\n\"you\""),
            ),
            atMs = 0L,
        )
        val text = result.toExportText()
        assertTrue(text.startsWith("FocusFlow screen scan (beta)\n"))
        assertTrue(text.contains("Package: com.example.app"))
        assertTrue(text.contains("Captured: 1970-01-01T00:00:00Z"))
        assertTrue(text.contains("Elements: 2"))
        assertTrue(text.contains("[0] FrameLayout bounds=0,0,1080,2400"))
        assertTrue(text.contains("  [1] TextView id=reel_title text=\"Hello 'you'\" bounds=0,0,500,100"))
        assertFalse(text.contains("more element(s)"))
    }

    @Test
    fun exportIsCutAtTheLimitWithANote() {
        val nodes = List(500) { node(1, "0,0,10,10", text = "row $it") }
        val text = CaptureResult("p", nodes, 0L).toExportText(maxChars = 2_000)
        assertTrue(text.length < 2_200)
        assertTrue(text.contains("more element(s) not shown"))
        val shown = Regex("""\[(\d+)] TextView""").findAll(text).count()
        assertTrue(shown in 1..499)
        assertNotNull(Regex("""… (\d+) more""").find(text))
        assertEquals(500 - shown, Regex("""… (\d+) more""").find(text)!!.groupValues[1].toInt())
    }

    @Test
    fun blankPackageIsLabelled() {
        assertTrue(CaptureResult("", emptyList(), 0L).toExportText().contains("Package: (unknown)"))
    }
}
