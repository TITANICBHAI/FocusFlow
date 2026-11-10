package com.tbtechs.focusflow.ui.support

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogV114Test {
    private val changelog = parseChangelog(
        File("src/main/assets/changelog.json").readText(Charsets.UTF_8),
    )

    @Test
    fun version114SitsBetween115And113() {
        val versions = changelog.map { it.version }
        assertEquals("1.1.6", versions[0])
        assertEquals("1.1.5", versions[1])
        assertEquals("1.1.4", versions[2])
        assertEquals("1.1.3", versions[3])
    }

    @Test
    fun version114SaysTheAppWasRewrittenInKotlin() {
        val entry = changelog.first { it.version == "1.1.4" }
        assertEquals("Rewritten in Kotlin", entry.sections.first().heading)
        assertTrue(entry.sections.flatMap { it.items }.joinToString(" ").contains("Kotlin"))
    }
}
