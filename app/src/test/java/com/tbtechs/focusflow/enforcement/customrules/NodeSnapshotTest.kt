package com.tbtechs.focusflow.enforcement.customrules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeSnapshotTest {
    private fun node(
        viewId: String? = null,
        className: String? = "android.widget.TextView",
        text: String? = null,
        desc: String? = null,
    ) = NodeSnapshot(depth = 0, viewId = viewId, className = className, text = text, desc = desc, bounds = "0,0,1,1")

    @Test
    fun idSuffixIsThePartAfterTheSlash() {
        assertEquals("reel_recycler", node(viewId = "com.app:id/reel_recycler").idSuffix())
        assertNull(node(viewId = "no_slash").idSuffix())
        assertNull(node(viewId = "com.app:id/").idSuffix())
        assertNull(node().idSuffix())
    }

    @Test
    fun matcherPrefersIdThenTextThenDescriptionThenClass() {
        assertEquals(
            NodeMatcher(viewIdSuffix = "reel_recycler"),
            node(viewId = "com.app:id/reel_recycler", text = "Reels").toMatcher(),
        )
        assertEquals(NodeMatcher(textContains = "Reels"), node(text = "Reels", desc = "tab").toMatcher())
        assertEquals(NodeMatcher(descContains = "tab"), node(desc = "tab").toMatcher())
        assertEquals(NodeMatcher(className = "android.widget.TextView"), node().toMatcher())
    }

    @Test
    fun classOnlyPickIsTooBroadSoTheEditorWillAskForMore() {
        assertTrue(node().toMatcher().isTooBroad())
        assertFalse(node(text = "Reels").toMatcher().isTooBroad())
    }

    @Test
    fun stableHandleNeedsAnIdTextOrDescription() {
        assertFalse(node().hasStableHandle())
        assertFalse(node(text = " ").hasStableHandle())
        assertTrue(node(text = "x").hasStableHandle())
        assertTrue(node(desc = "x").hasStableHandle())
        assertTrue(node(viewId = "a:id/b").hasStableHandle())
    }
}
