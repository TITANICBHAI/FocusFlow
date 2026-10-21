package com.tbtechs.focusflow.data.backup

import kotlinx.serialization.json.JsonObject

/** Collects unknown object keys across a file and emits one bounded warning. */
internal class UnknownFieldWarnings {
    private var total = 0
    private val examples = linkedSetOf<String>()

    fun record(value: JsonObject, knownKeys: Set<String>) {
        value.keys.forEach { key ->
            if (key !in knownKeys) {
                total++
                if (examples.size < MAX_EXAMPLES) {
                    examples += key.take(MAX_EXAMPLE_LENGTH)
                }
            }
        }
    }

    fun warning(): String? {
        if (total == 0) return null
        val details = examples.joinToString(", ") { key ->
            val suggestion = nearestSuggestion(key)
            if (suggestion == null) "\"$key\"" else "\"$key\" → did you mean \"$suggestion\"?"
        }
        return if (details.isEmpty()) {
            "$total unknown fields ignored."
        } else {
            "$total unknown fields ignored (e.g. $details)."
        }
    }

    private fun nearestSuggestion(unknown: String): String? {
        if (unknown.length > MAX_SUGGESTION_LENGTH) return null
        return KNOWN_KEYS.asSequence()
            .map { candidate -> candidate to editDistanceAtMostTwo(unknown, candidate) }
            .filter { (_, distance) -> distance <= MAX_EDIT_DISTANCE }
            .minByOrNull { it.second }
            ?.first
    }

    private fun editDistanceAtMostTwo(left: String, right: String): Int {
        if (kotlin.math.abs(left.length - right.length) > MAX_EDIT_DISTANCE) {
            return MAX_EDIT_DISTANCE + 1
        }
        var previous = IntArray(right.length + 1) { it }
        for (leftIndex in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            for (rightIndex in right.indices) {
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + if (left[leftIndex] == right[rightIndex]) 0 else 1,
                )
            }
            previous = current
        }
        return previous.last()
    }

    private companion object {
        const val MAX_EXAMPLES = 3
        const val MAX_EXAMPLE_LENGTH = 64
        const val MAX_SUGGESTION_LENGTH = 64
        const val MAX_EDIT_DISTANCE = 2
        val KNOWN_KEYS = setOf(
            "id", "title", "done", "subtitle", "goal", "setsDone", "image", "layout",
            "explanation", "steps", "tips", "avoid", "kind", "sets", "reps", "repsMax",
            "seconds", "restSeconds", "meters", "weightKg", "label", "text", "asset", "alt",
            "mime", "data", "file", "sha256",
        )
    }
}
