package com.tbtechs.focusflow.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageDataGuideTest {
    private val guide = usageDataGuide()
    private val allText = guide.joinToString("\n") { s -> s.title + "\n" + s.entries.joinToString("\n") { it.question + "\n" + it.answer } }

    private fun answer(question: String) = guide.flatMap { it.entries }.single { it.question == question }.answer

    @Test
    fun everySectionAndEntryHasText() {
        assertTrue(guide.isNotEmpty())
        guide.forEach { section ->
            assertTrue(section.title.isNotBlank())
            assertTrue(section.title, section.entries.isNotEmpty())
            section.entries.forEach {
                assertTrue(it.question.isNotBlank())
                assertTrue(it.question, it.answer.isNotBlank())
            }
        }
    }

    @Test
    fun sectionTitlesAndQuestionsAreUnique() {
        assertEquals(guide.size, guide.map { it.title }.toSet().size)
        val questions = guide.flatMap { it.entries }.map { it.question }
        assertEquals(questions.size, questions.toSet().size)
    }

    @Test
    fun numbersComeFromTheRealThresholds() {
        assertTrue(answer("What is a session and a quick check?").contains("shorter than 60 seconds"))
        assertTrue(answer("What is a session and a quick check?").contains("Under 30s, 30s–1m, 1–5m, 5–20m, 20m+"))
        assertTrue(answer("What is the first app after unlocking?").contains("within 15 seconds"))
        assertTrue(answer("How is the gap between unlocks measured?").contains("longer than 3 hours"))
        assertTrue(answer("How far back does it go?").contains("up to 14 days"))
        assertTrue(answer("How far back does it go?").contains("up to 365 days"))
        assertTrue(answer("What is a reflex app?").contains("at least 8 times"))
        assertTrue(answer("What is a reflex app?").contains("at least 3 days"))
        assertTrue(answer("What is a rising app?").contains("1.5×"))
        assertTrue(answer("What is a rising app?").contains("15 minutes more"))
        assertTrue(answer("What is a rising app?").contains("at least 5 minutes"))
    }

    @Test
    fun nudgeAnswersMatchThePlanner() {
        assertTrue(answer("What are nudges?").contains("15 minutes before the hour"))
        assertTrue(answer("How many will I get?").contains("At most 3 a day"))
        assertTrue(answer("How many will I get?").contains("30 minutes apart"))
    }

    @Test
    fun theGuideIsAboutUsageDataOnlyAndNeverMentionsHowToUse() {
        assertFalse(allText.contains("achievement", ignoreCase = true))
        assertFalse(allText.contains("PIN", ignoreCase = false))
        assertTrue(USAGE_GUIDE_TITLE != "How to Use")
    }
}
