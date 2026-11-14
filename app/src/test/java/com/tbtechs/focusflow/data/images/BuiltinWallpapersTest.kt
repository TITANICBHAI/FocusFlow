package com.tbtechs.focusflow.data.images

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinWallpapersTest {
    @Test
    fun everyWallpaperHasAUniqueIdAThreeColourPaletteAndASensibleGlassHint() {
        val all = BuiltinWallpapers.all
        assertTrue(all.size >= 12)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        all.forEach {
            assertEquals(it.id, 3, it.colors.size)
            assertTrue(it.id, it.bestAt in 0..100)
            assertTrue(it.id, it.name.isNotBlank())
        }
    }

    @Test
    fun lookupByIdIgnoresUnknownAndBlankIds() {
        assertNotNull(BuiltinWallpapers.byId("dusk"))
        assertNull(BuiltinWallpapers.byId("adaptive"))
        assertNull(BuiltinWallpapers.byId(""))
        assertNull(BuiltinWallpapers.byId(null))
    }
}
