package com.tbtechs.focusflow.data.model

import java.math.BigDecimal
import kotlinx.serialization.Serializable

@Serializable
data class Subtask(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val subtitle: String? = null,
    val goal: SubtaskGoal? = null,
    val setsDone: Int = 0,
    val image: SubtaskImage? = null,
    val layout: String? = null,
    val explanation: String? = null,
    val steps: List<SubtaskStep> = emptyList(),
    val tips: List<String> = emptyList(),
    val avoid: List<String> = emptyList(),
)

@Serializable
data class SubtaskGoal(
    val kind: String,
    val sets: Int? = null,
    val reps: Int? = null,
    val seconds: Int? = null,
    val restSeconds: Int? = null,
    val meters: Int? = null,
    val weightKg: Double? = null,
    val label: String? = null,
)

@Serializable
data class SubtaskStep(
    val title: String? = null,
    val text: String,
    val image: SubtaskImage? = null,
)

@Serializable
data class SubtaskImage(
    val asset: String,
    val alt: String? = null,
)

object SubtaskLimits {
    const val MAX_SUBTASKS = 50
    const val MAX_ID_CHARS = 64
    const val MAX_TITLE_CHARS = 200
    const val MAX_SUBTITLE_CHARS = 120
    const val MAX_EXPLANATION_CHARS = 4_000
    const val MAX_STEPS = 30
    const val MAX_STEP_TITLE_CHARS = 120
    const val MAX_STEP_TEXT_CHARS = 1_500
    const val MAX_TIPS = 10
    const val MAX_TIP_CHARS = 300
    const val MAX_ALT_CHARS = 200
    const val MAX_GOAL_LABEL_CHARS = 80
    const val MAX_JSON_BYTES_PER_TASK = 96 * 1024
}

data class SubtaskNormalizationResult(
    val subtasks: List<Subtask>,
    val warnings: List<String>,
)

/**
 * Applies the shared domain invariants before subtask data is persisted or imported.
 * Invalid optional goals/images are removed without discarding their parent subtask.
 */
object SubtaskNormalizer {
    fun normalize(input: List<Subtask>): SubtaskNormalizationResult {
        val counts = WarningCounts()
        val normalized = mutableListOf<Subtask>()
        val usedIds = mutableSetOf<String>()

        val selected = input.take(SubtaskLimits.MAX_SUBTASKS)
        counts.truncatedSubtasks = (input.size - selected.size).coerceAtLeast(0)

        selected.forEachIndexed { index, source ->
            val title = boundedRequired(source.title, SubtaskLimits.MAX_TITLE_CHARS, counts)
            if (title == null) {
                counts.blankTitles++
                return@forEachIndexed
            }

            val id = uniqueId(source.id, index, usedIds, counts)
            val subtitle = boundedOptional(source.subtitle, SubtaskLimits.MAX_SUBTITLE_CHARS, counts)
            val explanation = boundedOptional(
                source.explanation,
                SubtaskLimits.MAX_EXPLANATION_CHARS,
                counts,
            )
            val goal = normalizeGoal(source.goal, counts)
            val image = normalizeImage(source.image, counts)
            val layout = normalizeLayout(source.layout, counts)

            val selectedSteps = source.steps.take(SubtaskLimits.MAX_STEPS)
            counts.truncatedSteps += (source.steps.size - selectedSteps.size).coerceAtLeast(0)
            val steps = selectedSteps.mapNotNull { step ->
                val text = boundedRequired(step.text, SubtaskLimits.MAX_STEP_TEXT_CHARS, counts)
                if (text == null) {
                    counts.blankSteps++
                    null
                } else {
                    step.copy(
                        title = boundedOptional(
                            step.title,
                            SubtaskLimits.MAX_STEP_TITLE_CHARS,
                            counts,
                        ),
                        text = text,
                        image = normalizeImage(step.image, counts),
                    )
                }
            }

            val tips = normalizeTextList(source.tips, SubtaskLimits.MAX_TIPS, counts)
            val avoid = normalizeTextList(source.avoid, SubtaskLimits.MAX_TIPS, counts)
            val setsDone = if (goal?.kind == "sets" && goal.sets != null) {
                source.setsDone.coerceIn(0, goal.sets)
            } else {
                0
            }
            val done = source.done

            normalized += source.copy(
                id = id,
                title = title,
                done = done,
                subtitle = subtitle,
                goal = goal,
                setsDone = if (done && goal?.kind == "sets") goal.sets ?: setsDone else setsDone,
                image = image,
                layout = layout,
                explanation = explanation,
                steps = steps,
                tips = tips,
                avoid = avoid,
            )
        }

        return SubtaskNormalizationResult(normalized, counts.toWarnings())
    }

    private fun normalizeTextList(
        source: List<String>,
        limit: Int,
        counts: WarningCounts,
    ): List<String> {
        val selected = source.take(limit)
        counts.truncatedListItems += (source.size - selected.size).coerceAtLeast(0)
        return selected.mapNotNull { boundedOptional(it, SubtaskLimits.MAX_TIP_CHARS, counts) }
    }

    private fun normalizeGoal(
        source: SubtaskGoal?,
        counts: WarningCounts,
    ): SubtaskGoal? {
        source ?: return null
        val kind = boundedRequired(source.kind, 20, counts)
        val label = boundedOptional(source.label, SubtaskLimits.MAX_GOAL_LABEL_CHARS, counts)
        val numericFieldsInRange =
            (source.sets == null || source.sets in 1..99) &&
                (source.reps == null || source.reps in 1..9_999) &&
                (source.seconds == null || source.seconds in 1..86_400) &&
                (source.restSeconds == null || source.restSeconds in 0..3_600) &&
                (source.meters == null || source.meters in 1..1_000_000) &&
                (source.weightKg == null || source.weightKg.isFinite() && source.weightKg in 0.0..1_000.0)

        val requiredFieldsPresent = when (kind) {
            "sets" -> source.sets != null && (source.reps != null || source.seconds != null)
            "reps" -> source.reps != null
            "duration" -> source.seconds != null
            "distance" -> source.meters != null
            "text" -> label != null
            else -> false
        }
        if (kind == null || !numericFieldsInRange || !requiredFieldsPresent) {
            counts.invalidGoals++
            return null
        }
        return source.copy(kind = kind, label = label)
    }

    private fun normalizeImage(
        source: SubtaskImage?,
        counts: WarningCounts,
    ): SubtaskImage? {
        source ?: return null
        val asset = clean(source.asset)
        val safeAsset = asset.take(SubtaskLimits.MAX_ID_CHARS)
        if (safeAsset.isEmpty() || safeAsset.any { !it.isAssetIdCharacter() }) {
            counts.invalidImages++
            return null
        }
        if (asset.length > SubtaskLimits.MAX_ID_CHARS) counts.truncatedStrings++
        return source.copy(
            asset = safeAsset,
            alt = boundedOptional(source.alt, SubtaskLimits.MAX_ALT_CHARS, counts),
        )
    }

    private fun normalizeLayout(source: String?, counts: WarningCounts): String? {
        val layout = boundedOptional(source, 20, counts) ?: return null
        if (layout == "steps" || layout == "overview") return layout
        counts.invalidLayouts++
        return null
    }

    private fun uniqueId(
        source: String,
        index: Int,
        usedIds: MutableSet<String>,
        counts: WarningCounts,
    ): String {
        val cleaned = clean(source)
        val base = cleaned
            .take(SubtaskLimits.MAX_ID_CHARS)
            .map { if (it.isAssetIdCharacter()) it else '_' }
            .joinToString("")
        if (cleaned.length > SubtaskLimits.MAX_ID_CHARS) counts.truncatedStrings++
        if (base != cleaned) counts.normalizedIds++

        val candidateBase = base.ifEmpty { "s${index + 1}".also { counts.normalizedIds++ } }
        if (usedIds.add(candidateBase)) return candidateBase

        counts.normalizedIds++
        var suffix = 2
        while (true) {
            val suffixText = "-$suffix"
            val candidate = candidateBase
                .take(SubtaskLimits.MAX_ID_CHARS - suffixText.length) + suffixText
            if (usedIds.add(candidate)) return candidate
            suffix++
        }
    }

    private fun boundedRequired(
        source: String,
        limit: Int,
        counts: WarningCounts,
    ): String? = boundedOptional(source, limit, counts)?.takeIf(String::isNotEmpty)

    private fun boundedOptional(
        source: String?,
        limit: Int,
        counts: WarningCounts,
    ): String? {
        source ?: return null
        val value = clean(source)
        if (value.length <= limit) return value.takeIf(String::isNotEmpty)
        counts.truncatedStrings++
        return value.take(limit).trim().takeIf(String::isNotEmpty)
    }

    private fun clean(value: String): String =
        value.filter { it == '\n' || !Character.isISOControl(it) }.trim()

    private fun Char.isAssetIdCharacter(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '_' || this == '-'

    private class WarningCounts {
        var blankTitles = 0
        var normalizedIds = 0
        var invalidGoals = 0
        var invalidImages = 0
        var invalidLayouts = 0
        var blankSteps = 0
        var truncatedStrings = 0
        var truncatedSubtasks = 0
        var truncatedSteps = 0
        var truncatedListItems = 0

        fun toWarnings(): List<String> = buildList {
            if (blankTitles > 0) add("$blankTitles subtasks with blank titles were skipped.")
            if (normalizedIds > 0) add("$normalizedIds subtask IDs were generated or normalized.")
            if (invalidGoals > 0) add("$invalidGoals invalid subtask goals were removed.")
            if (invalidImages > 0) add("$invalidImages invalid subtask images were removed.")
            if (invalidLayouts > 0) add("$invalidLayouts invalid layout values were reset.")
            if (blankSteps > 0) add("$blankSteps steps with blank text were skipped.")
            if (truncatedStrings > 0) add("$truncatedStrings strings were trimmed to their length limits.")
            if (truncatedSubtasks > 0) add("$truncatedSubtasks subtasks were truncated at the list limit.")
            if (truncatedSteps > 0) add("$truncatedSteps steps were truncated at the per-subtask list limit.")
            if (truncatedListItems > 0) {
                add("$truncatedListItems tips or avoid items were truncated at the list limit.")
            }
        }
    }
}

data class FormattedSubtaskGoal(
    val primary: String,
    val details: List<String> = emptyList(),
    val rest: String? = null,
)

object GoalFormatter {
    fun format(goal: SubtaskGoal): FormattedSubtaskGoal? {
        val sets = goal.sets?.takeIf { it in 1..99 }
        val reps = goal.reps?.takeIf { it in 1..9_999 }
        val seconds = goal.seconds?.takeIf { it in 1..86_400 }
        val meters = goal.meters?.takeIf { it in 1..1_000_000 }
        val primary = when (goal.kind) {
            "sets" -> {
                val validSets = sets ?: return null
                when {
                    reps != null -> "$validSets × $reps"
                    seconds != null -> "$validSets × $seconds s"
                    else -> return null
                }
            }
            "reps" -> "${reps ?: return null} reps"
            "duration" -> "${seconds ?: return null} s"
            "distance" -> {
                val validMeters = meters ?: return null
                if (validMeters < 1_000) "$validMeters m"
                else "${decimal(validMeters / 1_000.0)} km"
            }
            "text" -> goal.label?.trim()?.takeIf(String::isNotEmpty) ?: return null
            else -> return null
        }

        val details = buildList {
            if (goal.kind == "sets" && reps != null && seconds != null) {
                add("$seconds s")
            }
            goal.weightKg
                ?.takeIf { it.isFinite() && it in 0.0..1_000.0 }
                ?.let { add("${decimal(it)} kg") }
            if (goal.kind != "text") goal.label?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
        }
        val rest = goal.restSeconds
            ?.takeIf { it in 1..3_600 }
            ?.let { "Rest $it s" }
        return FormattedSubtaskGoal(primary = primary, details = details, rest = rest)
    }

    private fun decimal(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

enum class SubtaskLayoutMode {
    STEPS,
    OVERVIEW,
}

object SubtaskLayoutPolicy {
    fun resolve(subtask: Subtask): SubtaskLayoutMode = when (subtask.layout) {
        "steps" -> if (subtask.steps.isNotEmpty()) SubtaskLayoutMode.STEPS else SubtaskLayoutMode.OVERVIEW
        "overview" -> SubtaskLayoutMode.OVERVIEW
        else -> if (subtask.steps.isNotEmpty()) SubtaskLayoutMode.STEPS else SubtaskLayoutMode.OVERVIEW
    }
}
