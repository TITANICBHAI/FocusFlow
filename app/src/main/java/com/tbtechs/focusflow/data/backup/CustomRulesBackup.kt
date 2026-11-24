package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRule
import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Custom screen rules inside a `.focusflow` backup.
 *
 * The rules travel as a `customScreenRules` list inside `settings`, using the same rule shape as the
 * rules export file. They are not one of the portable settings (those are plain preference values),
 * so this object owns both directions and nothing else has to know about them.
 *
 * Restore only ever adds: rules already on the phone stay exactly as they are, a rule that is the
 * same as one already here is not added again, and every rule that comes in gets a new id. A
 * backup without the key leaves the phone's rules alone.
 */
internal object CustomRulesBackup {
    const val SETTINGS_KEY = "customScreenRules"

    private val json = Json

    data class Plan(
        /** The full list to store, or null when the backup says nothing about rules. */
        val merged: List<CustomScreenRule>?,
        val added: Int = 0,
        val alreadyThere: Int = 0,
        val skipped: Int = 0,
        val warnings: List<String> = emptyList(),
    )

    /** The rules as a backup list, or null when there are none (the key is then left out). */
    fun toJson(rules: List<CustomScreenRule>): JsonArray? {
        if (rules.isEmpty()) return null
        return json.parseToJsonElement(CustomScreenRules.toJson(rules)) as? JsonArray
    }

    fun plan(settings: JsonObject, existing: List<CustomScreenRule>, ownPackage: String): Plan {
        val element = settings[SETTINGS_KEY] ?: return Plan(merged = null)
        val array = element as? JsonArray
            ?: return Plan(
                merged = null,
                warnings = listOf("Screen rules in this backup could not be read and were left out."),
            )
        if (array.isEmpty()) return Plan(merged = null)

        val objects = array.filterIsInstance<JsonObject>()
        val text = JsonObject(
            mapOf(
                "format" to JsonPrimitive(CustomScreenRules.EXPORT_FORMAT),
                "version" to JsonPrimitive(CustomScreenRules.EXPORT_VERSION),
                "rules" to JsonArray(objects),
            ),
        ).toString()
        val parsed = CustomScreenRules.parseImport(text, ownPackage, existing, keepEnabled = true)

        val room = (CustomScreenRules.MAX_RULES - existing.size).coerceAtLeast(0)
        val toAdd = parsed.accepted.take(room)
        val alreadyThere = parsed.duplicates.size
        val skipped = (array.size - toAdd.size - alreadyThere).coerceAtLeast(0)

        val warnings = mutableListOf<String>()
        warnings += summary(toAdd.size, alreadyThere, skipped)
        if (parsed.accepted.size > room) {
            warnings += "Only $room of the screen rules from this backup fit: the limit is " +
                "${CustomScreenRules.MAX_RULES} rules."
        }
        return Plan(
            merged = existing + toAdd,
            added = toAdd.size,
            alreadyThere = alreadyThere,
            skipped = skipped,
            warnings = warnings,
        )
    }

    private fun summary(added: Int, alreadyThere: Int, skipped: Int): String {
        val parts = mutableListOf("$added screen ${if (added == 1) "rule" else "rules"} added")
        if (alreadyThere > 0) parts += "$alreadyThere already on this phone"
        if (skipped > 0) parts += "$skipped skipped because they were invalid or over the limit"
        return parts.joinToString(", ") + "."
    }
}
