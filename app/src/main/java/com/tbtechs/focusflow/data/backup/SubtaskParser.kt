package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.SubtaskGoal
import com.tbtechs.focusflow.data.model.SubtaskImage
import com.tbtechs.focusflow.data.model.SubtaskNormalizer
import com.tbtechs.focusflow.data.model.SubtaskStep
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

internal data class ParsedSubtasks(
    val subtasks: List<Subtask>,
    val warnings: List<String>,
)

/** Parses each subtask independently so corrupt optional data never drops its parent task. */
internal object SubtaskParser {
    private val assetIdPattern = Regex("^[A-Za-z0-9_-]{1,64}$")

    fun parse(
        element: JsonElement?,
        assetIds: Set<String>,
        sharedUnknownFields: UnknownFieldWarnings? = null,
    ): ParsedSubtasks {
        val warnings = WarningCounts()
        val unknownFields = sharedUnknownFields ?: UnknownFieldWarnings()
        val array = when {
            element == null || element == JsonNull -> JsonArray(emptyList())
            element is JsonArray -> element
            else -> {
                warnings.invalidSubtaskCollections++
                JsonArray(emptyList())
            }
        }
        if (array.toString().toByteArray(StandardCharsets.UTF_8).size >
            BackupJsonLimits.MAX_SUBTASK_JSON_BYTES_PER_TASK
        ) {
            warnings.oversizedSubtaskPayloads++
            return ParsedSubtasks(emptyList(), warnings.toMessages())
        }
        val candidates = array.take(BackupJsonLimits.MAX_SUBTASKS_PER_TASK)
        warnings.truncatedSubtasks = (array.size - candidates.size).coerceAtLeast(0)
        val parsed = candidates.mapNotNull { item ->
            runCatching { parseCandidate(item, assetIds, warnings, unknownFields) }
                .onFailure { warnings.malformedSubtasks++ }
                .getOrNull()
        }

        val normalized = SubtaskNormalizer.normalize(parsed)
        warnings.addNormalizerWarnings(normalized.warnings)
        val messages = warnings.toMessages().toMutableList()
        if (sharedUnknownFields == null) unknownFields.warning()?.let(messages::add)
        return ParsedSubtasks(normalized.subtasks, messages)
    }

    private fun parseCandidate(
        item: JsonElement,
        assetIds: Set<String>,
        warnings: WarningCounts,
        unknownFields: UnknownFieldWarnings,
    ): Subtask? {
        val objectValue = item as? JsonObject
        if (objectValue == null) {
            warnings.malformedSubtasks++
            return null
        }
        unknownFields.record(objectValue, SUBTASK_KEYS)
        val title = objectValue.string("title")
        if (title.isNullOrBlank()) {
            warnings.missingTitles++
            return null
        }

        val goal = parseGoal(objectValue["goal"], warnings, unknownFields)
        val image = parseImage(objectValue["image"], assetIds, warnings, unknownFields)
        val steps = parseSteps(objectValue["steps"], assetIds, warnings, unknownFields)
        val tips = parseTextList(objectValue["tips"], warnings)
        val avoid = parseTextList(objectValue["avoid"], warnings)
        return Subtask(
            id = objectValue.string("id").orEmpty(),
            title = title,
            done = objectValue.boolean("done") ?: false,
            subtitle = objectValue.string("subtitle"),
            goal = goal,
            setsDone = objectValue.int("setsDone") ?: 0,
            image = image,
            layout = objectValue.string("layout"),
            explanation = objectValue.string("explanation"),
            steps = steps,
            tips = tips,
            avoid = avoid,
        )
    }

    private fun parseGoal(
        element: JsonElement?,
        warnings: WarningCounts,
        unknownFields: UnknownFieldWarnings,
    ): SubtaskGoal? {
        if (element == null || element == JsonNull) return null
        val objectValue = element as? JsonObject
        if (objectValue == null) {
            warnings.invalidGoals++
            return null
        }
        unknownFields.record(objectValue, GOAL_KEYS)
        val kind = objectValue.string("kind")
        val label = objectValue.stringOrNull("label") ?: run {
            if (objectValue["label"] != null && objectValue["label"] != JsonNull) {
                warnings.invalidGoals++
                return null
            }
            null
        }
        val sets = objectValue.intOrNull("sets") ?: if (objectValue.hasBadInteger("sets")) {
            warnings.invalidGoals++
            return null
        } else null
        val reps = objectValue.intOrNull("reps") ?: if (objectValue.hasBadInteger("reps")) {
            warnings.invalidGoals++
            return null
        } else null
        val rawRepsMax = objectValue.intOrNull("repsMax")
        val hasInvalidRepsMax = objectValue.hasBadInteger("repsMax")
        val seconds = objectValue.intOrNull("seconds") ?: if (objectValue.hasBadInteger("seconds")) {
            warnings.invalidGoals++
            return null
        } else null
        val restSeconds = objectValue.intOrNull("restSeconds")
            ?: if (objectValue.hasBadInteger("restSeconds")) {
                warnings.invalidGoals++
                return null
            } else null
        val meters = objectValue.intOrNull("meters") ?: if (objectValue.hasBadInteger("meters")) {
            warnings.invalidGoals++
            return null
        } else null
        val weightKg = objectValue.doubleOrNull("weightKg") ?: if (objectValue.hasBadDouble("weightKg")) {
            warnings.invalidGoals++
            return null
        } else null

        if (kind == null) {
            warnings.invalidGoals++
            return null
        }
        val repsMax = rawRepsMax?.takeIf {
            kind in REP_RANGE_KINDS && reps != null && it in reps..MAX_REPS
        }
        if (hasInvalidRepsMax || rawRepsMax != null && repsMax == null) {
            warnings.invalidRepRanges++
        }
        return SubtaskGoal(
            kind = kind,
            sets = sets,
            reps = reps,
            repsMax = repsMax,
            seconds = seconds,
            restSeconds = restSeconds,
            meters = meters,
            weightKg = weightKg,
            label = label,
        )
    }

    private fun parseSteps(
        element: JsonElement?,
        assetIds: Set<String>,
        warnings: WarningCounts,
        unknownFields: UnknownFieldWarnings,
    ): List<SubtaskStep> {
        if (element == null || element == JsonNull) return emptyList()
        val array = element as? JsonArray
        if (array == null) {
            warnings.invalidStepCollections++
            return emptyList()
        }
        val selected = array.take(BackupJsonLimits.MAX_STEPS_PER_SUBTASK)
        warnings.truncatedSteps += (array.size - selected.size).coerceAtLeast(0)
        return selected.mapNotNull { stepElement ->
            val step = stepElement as? JsonObject
            step?.let { unknownFields.record(it, STEP_KEYS) }
            val text = step?.string("text")
            if (step == null || text.isNullOrBlank()) {
                warnings.malformedSteps++
                return@mapNotNull null
            }
            SubtaskStep(
                title = step.string("title"),
                text = text,
                image = parseImage(step["image"], assetIds, warnings, unknownFields),
            )
        }
    }

    private fun parseTextList(
        element: JsonElement?,
        warnings: WarningCounts,
    ): List<String> {
        if (element == null || element == JsonNull) return emptyList()
        val array = element as? JsonArray
        if (array == null) {
            warnings.invalidTextCollections++
            return emptyList()
        }
        val selected = array.take(BackupJsonLimits.MAX_TIPS_PER_SUBTASK)
        warnings.truncatedTextItems += (array.size - selected.size).coerceAtLeast(0)
        return selected.mapNotNull { item ->
            (item as? JsonPrimitive)
                ?.takeIf(JsonPrimitive::isString)
                ?.content
                ?: run {
                    warnings.invalidTextItems++
                    null
                }
        }
    }

    private fun parseImage(
        element: JsonElement?,
        assetIds: Set<String>,
        warnings: WarningCounts,
        unknownFields: UnknownFieldWarnings,
    ): SubtaskImage? {
        if (element == null || element == JsonNull) return null
        val image = element as? JsonObject
        if (image == null) {
            warnings.unsupportedImages++
            return null
        }
        unknownFields.record(image, IMAGE_KEYS)
        if ("url" in image) {
            warnings.remoteImages++
            return null
        }
        if ("asset" !in image) {
            warnings.unsupportedImages++
            return null
        }
        val asset = image.string("asset")
        if (asset == null || !assetIdPattern.matches(asset)) {
            warnings.invalidImages++
            return null
        }
        if (asset !in assetIds) {
            warnings.missingImages++
            return null
        }
        val alt = image.stringOrNull("alt")
        if (image["alt"] != null && image["alt"] != JsonNull && alt == null) {
            warnings.invalidImages++
            return SubtaskImage(asset = asset)
        }
        return SubtaskImage(asset = asset, alt = alt)
    }

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = get(key) ?: return null
        if (element == JsonNull) return null
        return (element as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
    }

    private fun JsonObject.boolean(key: String): Boolean? =
        (get(key) as? JsonPrimitive)
            ?.takeUnless(JsonPrimitive::isString)
            ?.content
            ?.toBooleanStrictOrNull()

    private fun JsonObject.int(key: String): Int? =
        (get(key) as? JsonPrimitive)
            ?.takeUnless(JsonPrimitive::isString)
            ?.intOrNull

    private fun JsonObject.intOrNull(key: String): Int? {
        val element = get(key) ?: return null
        if (element == JsonNull) return null
        return (element as? JsonPrimitive)
            ?.takeUnless(JsonPrimitive::isString)
            ?.intOrNull
    }

    private fun JsonObject.doubleOrNull(key: String): Double? {
        val element = get(key) ?: return null
        if (element == JsonNull) return null
        return (element as? JsonPrimitive)
            ?.takeUnless(JsonPrimitive::isString)
            ?.doubleOrNull
    }

    private fun JsonObject.hasBadInteger(key: String): Boolean {
        val element = get(key) ?: return false
        if (element == JsonNull) return false
        return (element as? JsonPrimitive)
            ?.takeUnless(JsonPrimitive::isString)
            ?.intOrNull == null
    }

    private fun JsonObject.hasBadDouble(key: String): Boolean {
        val element = get(key) ?: return false
        if (element == JsonNull) return false
        return (element as? JsonPrimitive)
            ?.takeUnless(JsonPrimitive::isString)
            ?.doubleOrNull == null
    }

    private class WarningCounts {
        var malformedSubtasks = 0
        var missingTitles = 0
        var invalidSubtaskCollections = 0
        var invalidGoals = 0
        var invalidRepRanges = 0
        var invalidImages = 0
        var unsupportedImages = 0
        var remoteImages = 0
        var missingImages = 0
        var malformedSteps = 0
        var invalidStepCollections = 0
        var invalidTextCollections = 0
        var invalidTextItems = 0
        var oversizedSubtaskPayloads = 0
        var truncatedSubtasks = 0
        var truncatedSteps = 0
        var truncatedTextItems = 0
        private val normalization = linkedMapOf<String, Int>()

        fun addNormalizerWarnings(messages: List<String>) {
            messages.forEach { message ->
                val match = Regex("^(\\d+) (.+)$").matchEntire(message)
                if (match == null) {
                    normalization[message] = (normalization[message] ?: 0) + 1
                } else {
                    val count = match.groupValues[1].toInt()
                    val detail = match.groupValues[2]
                    normalization[detail] = (normalization[detail] ?: 0) + count
                }
            }
        }

        fun toMessages(): List<String> = buildList {
            if (malformedSubtasks > 0) add("$malformedSubtasks malformed subtasks were skipped.")
            if (missingTitles > 0) add("$missingTitles subtasks without a valid title were skipped.")
            if (invalidSubtaskCollections > 0) {
                add("$invalidSubtaskCollections invalid subtasks fields were ignored.")
            }
            if (oversizedSubtaskPayloads > 0) {
                add("$oversizedSubtaskPayloads subtask payloads over 96 KiB were ignored.")
            }
            if (invalidGoals > 0) add("$invalidGoals invalid subtask goals were removed.")
            if (invalidRepRanges > 0) add("$invalidRepRanges invalid repsMax fields were removed.")
            if (invalidImages > 0) add("$invalidImages invalid images were removed.")
            if (unsupportedImages > 0) add("$unsupportedImages unsupported images were skipped.")
            if (remoteImages > 0) {
                add("$remoteImages remote images were skipped because remote images aren't supported.")
            }
            if (missingImages > 0) add("$missingImages images referencing missing assets were removed.")
            if (malformedSteps > 0) add("$malformedSteps malformed steps were skipped.")
            if (invalidStepCollections > 0) {
                add("$invalidStepCollections invalid step lists were ignored.")
            }
            if (invalidTextCollections > 0) {
                add("$invalidTextCollections invalid tips/avoid lists were ignored.")
            }
            if (invalidTextItems > 0) add("$invalidTextItems invalid tips/avoid items were skipped.")
            if (truncatedSubtasks > 0) {
                add("$truncatedSubtasks subtasks were truncated at the per-task limit.")
            }
            if (truncatedSteps > 0) add("$truncatedSteps steps were truncated at the per-subtask limit.")
            if (truncatedTextItems > 0) add("$truncatedTextItems tips or avoid items were truncated.")
            normalization.forEach { (message, count) ->
                add("$count $message")
            }
        }
    }

    private val SUBTASK_KEYS = setOf(
        "id", "title", "done", "subtitle", "goal", "setsDone", "image", "layout",
        "explanation", "steps", "tips", "avoid",
    )
    private val GOAL_KEYS = setOf(
        "kind", "sets", "reps", "repsMax", "seconds", "restSeconds", "meters", "weightKg", "label",
    )
    private val STEP_KEYS = setOf("title", "text", "image")
    private val IMAGE_KEYS = setOf("asset", "alt")
    private val REP_RANGE_KINDS = setOf("sets", "reps")
    private const val MAX_REPS = 9_999
}
