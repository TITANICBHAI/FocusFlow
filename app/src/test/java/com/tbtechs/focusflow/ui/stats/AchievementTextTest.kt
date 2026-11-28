package com.tbtechs.focusflow.ui.stats

import com.tbtechs.focusflow.analytics.ACHIEVEMENTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementTextTest {
    @Test
    fun everyCatalogAchievementHasTranslatableText() {
        val missing = ACHIEVEMENTS.map { it.id }.filter { it !in ACHIEVEMENT_TEXT }
        assertTrue("Achievements without string resources: $missing", missing.isEmpty())
    }

    @Test
    fun noTextEntryPointsAtAnUnknownAchievement() {
        val extra = ACHIEVEMENT_TEXT.keys - ACHIEVEMENTS.map { it.id }.toSet()
        assertEquals(emptySet<String>(), extra)
    }
}
