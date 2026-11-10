package com.tbtechs.focusflow.ui.support

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogDataTest {
    @Test
    fun bundledChangelogParsesAndStartsWithCurrentRelease() {
        val source = File("src/main/assets/changelog.json").readText(Charsets.UTF_8)

        val changelog = parseChangelog(source)

        assertEquals("1.1.6", changelog.first().version)
        assertEquals("November 2026", changelog.first().date)
        assertTrue(changelog.first().sections.isNotEmpty())
        assertEquals("Custom Screen Rules (beta)", changelog.first().sections.first().heading)
        val releaseNotes = changelog.first().sections.flatMap { it.items }.joinToString(" ")
        assertTrue(releaseNotes.contains("My rules"))
        assertTrue(releaseNotes.contains("Test this rule"))
        assertTrue(releaseNotes.contains("WhatsApp"))
        assertTrue(releaseNotes.contains("select its tab before you capture"))

        val previous = changelog.first { it.version == "1.1.5" }
        assertEquals("Subtasks and rich imports", previous.sections.first().heading)
        val previousNotes = previous.sections.flatMap { it.items }.joinToString(" ")
        assertTrue(previousNotes.contains("ZIP bundles"))
        assertTrue(previousNotes.contains("version 2"))
        assertTrue(previousNotes.contains("rep ranges"))
        assertTrue(previousNotes.contains("repsMax"))
    }

    @Test
    fun legacyReleaseHistoryRemainsAvailable() {
        val source = File("src/main/assets/changelog.json").readText(Charsets.UTF_8)

        val changelog = parseChangelog(source)

        assertTrue(changelog.any { it.version == "c1.0.9" })
        assertEquals("1.0.0", changelog.last().version)
    }
}
