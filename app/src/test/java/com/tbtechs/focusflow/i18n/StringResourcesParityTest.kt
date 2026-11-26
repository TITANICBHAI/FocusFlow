package com.tbtechs.focusflow.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every language must carry every translatable English string, with the same placeholders, so no
 * screen falls back to English by accident and no translated text crashes on a wrong format.
 */
class StringResourcesParityTest {
    private val res: File = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
    private val placeholder = Regex("%(?:\\d+\\$)?[sd]")

    private fun read(dir: File): Map<String, Pair<String, Boolean>> {
        val file = File(dir, "strings.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val el = nodes.item(i) as org.w3c.dom.Element
            el.getAttribute("name") to (el.textContent to (el.getAttribute("translatable") != "false"))
        }
    }

    private val languages = listOf("de", "es", "fr", "ja", "ko", "zh")
    private val english get() = read(File(res, "values")).filterValues { it.second }

    @Test
    fun everyLanguageHasEveryTranslatableString() {
        languages.forEach { lang ->
            val keys = read(File(res, "values-$lang")).keys
            val missing = english.keys - keys
            assertTrue("values-$lang is missing: ${missing.take(10)}", missing.isEmpty())
        }
    }

    @Test
    fun noLanguageHasLeftoverStrings() {
        languages.forEach { lang ->
            val extra = read(File(res, "values-$lang")).keys - english.keys
            assertTrue("values-$lang has strings English does not: ${extra.take(10)}", extra.isEmpty())
        }
    }

    @Test
    fun placeholdersMatchEnglish() {
        languages.forEach { lang ->
            read(File(res, "values-$lang")).forEach { (key, value) ->
                val en = english[key]?.first ?: return@forEach
                assertEquals(
                    "values-$lang/$key placeholders",
                    placeholder.findAll(en).map { it.value }.sorted().toList(),
                    placeholder.findAll(value.first).map { it.value }.sorted().toList(),
                )
            }
        }
    }

    @Test
    fun translationsAreNotBlank() {
        languages.forEach { lang ->
            read(File(res, "values-$lang")).forEach { (key, value) ->
                assertTrue("values-$lang/$key is blank", value.first.isNotBlank())
            }
        }
    }

    @Test
    fun datePatternsAreValidInEveryLanguage() {
        // Keys named *date_pattern* are java.time patterns, not text: a bad letter would crash the screen.
        val date = java.time.LocalDate.of(2026, 11, 25)
        (listOf("") + languages).forEach { lang ->
            val dir = File(res, if (lang.isEmpty()) "values" else "values-$lang")
            val patterns = read(dir).filterKeys { "date_pattern" in it }
            assertTrue("values$lang has no date patterns", patterns.isNotEmpty())
            patterns.forEach { (key, value) ->
                val text = java.time.format.DateTimeFormatter.ofPattern(value.first, java.util.Locale.forLanguageTag(lang.ifEmpty { "en" })).format(date)
                assertTrue("values-$lang/$key produced blank text", text.isNotBlank())
            }
        }
    }

    @Test
    fun localeConfigListsEveryTranslatedLanguage() {
        val text = File(res, "xml/locales_config.xml").readText()
        (languages + "en").forEach { assertTrue("locales_config lacks $it", text.contains("android:name=\"$it\"")) }
    }
}
