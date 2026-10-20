package com.tbtechs.focusflow.data.repository

import android.util.Log
import com.tbtechs.focusflow.data.model.Subtask
import kotlinx.serialization.decodeFromJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import java.util.LinkedHashMap

/**
 * Bounded LRU for the frequently re-mapped subtask column.
 *
 * Each array element is decoded independently so one damaged legacy element
 * cannot erase the other subtasks on the next write.
 */
internal class SubtaskJsonCache(
    private val json: Json,
    private val maxEntries: Int = MAX_ENTRIES,
) {
    private val entries = object : LinkedHashMap<String, List<Subtask>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Subtask>>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun decode(raw: String): List<Subtask> {
        entries[raw]?.let { return it }
        val root = runCatching { json.parseToJsonElement(raw) }
            .onFailure { logWarning("Could not parse stored subtask JSON.", it) }
            .getOrNull()
        val decoded = when (root) {
            is JsonArray -> root.mapNotNull { element ->
                runCatching { json.decodeFromJsonElement<Subtask>(element) }
                    .onFailure { logWarning("Skipping an unreadable stored subtask.", it) }
                    .getOrNull()
            }
            null -> emptyList()
            else -> {
                logWarning("Stored subtask JSON is not an array.", IllegalArgumentException("Expected JSON array."))
                emptyList()
            }
        }
        entries[raw] = decoded
        return decoded
    }

    @Synchronized
    fun size(): Int = entries.size

    private fun logWarning(message: String, error: Throwable) {
        // android.util.Log is unavailable in local JVM tests; logging must never
        // turn a fail-soft decode into a task-list failure.
        runCatching { Log.w(TAG, message, error) }
    }

    companion object {
        const val MAX_ENTRIES = 512
        private const val TAG = "SubtaskJsonCache"
    }
}
