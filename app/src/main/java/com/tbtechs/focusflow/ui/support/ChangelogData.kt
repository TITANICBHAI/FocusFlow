package com.tbtechs.focusflow.ui.support

import android.content.Context
import org.json.JSONArray

internal data class ChangeSection(val heading: String, val items: List<String>)
internal data class ChangeEntry(val version: String, val date: String, val sections: List<ChangeSection>)

internal fun loadChangelog(context: Context): List<ChangeEntry> {
    val source = context.assets.open("changelog.json").bufferedReader().use { it.readText() }
    return parseChangelog(source)
}

internal fun parseChangelog(source: String): List<ChangeEntry> {
    val entries = JSONArray(source)
    if (entries.length() == 0) {
        throw IllegalArgumentException("The FocusFlow changelog must contain at least one release.")
    }

    return (0 until entries.length()).map { entryIndex ->
        val entry = entries.getJSONObject(entryIndex)
        val sectionArray = entry.getJSONArray("sections")
        val sections = (0 until sectionArray.length()).map { sectionIndex ->
            val section = sectionArray.getJSONObject(sectionIndex)
            val itemArray = section.getJSONArray("items")
            ChangeSection(
                heading = section.getString("heading"),
                items = (0 until itemArray.length()).map(itemArray::getString),
            )
        }
        ChangeEntry(
            version = entry.getString("version"),
            date = entry.getString("date"),
            sections = sections,
        )
    }
}
