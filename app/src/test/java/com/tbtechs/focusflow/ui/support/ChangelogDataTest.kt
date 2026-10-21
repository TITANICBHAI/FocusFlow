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

        assertEquals("1.1.5", changelog.first().version)
        assertEquals("October 2026", changelog.first().date)
        assertTrue(changelog.first().sections.isNotEmpty())
        assertEquals("Subtasks and rich imports", changelog.first().sections.first().heading)
        val releaseNotes = changelog.first().sections.flatMap { it.items }.joinToString(" ")
        assertTrue(releaseNotes.contains("ZIP bundles"))
        assertTrue(releaseNotes.contains("version 2"))
        assertTrue(releaseNotes.contains("rep ranges"))
    }

    @Test
    fun legacyReleaseHistoryRemainsAvailable() {
        val source = File("src/main/assets/changelog.json").readText(Charsets.UTF_8)

        val changelog = parseChangelog(source)

        assertTrue(changelog.any { it.version == "c1.0.9" })
        assertEquals("1.0.0", changelog.last().version)
    }
}
