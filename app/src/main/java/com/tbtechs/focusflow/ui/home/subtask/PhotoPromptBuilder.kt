package com.tbtechs.focusflow.ui.home.subtask

import com.tbtechs.focusflow.data.model.PhotoSlotRef

/**
 * Builds the text the person copies and pastes into any AI image tool. Pure text, no Android.
 * Every value that came from a file is cleaned first: control characters and line breaks become
 * single spaces, and each value is cut to a fixed length.
 */
object PhotoPromptBuilder {
    const val MAX_ITEMS = 40
    const val MAX_STYLE_CHARS = 200
    const val MAX_TITLE_CHARS = 120
    const val MAX_LINE_PART_CHARS = 300
    const val DEFAULT_STYLE = "Clean, bright instructional photo on a plain background, no text on the picture."

    private val whitespaceRuns = Regex("\\s+")

    fun build(taskTitle: String, slots: List<PhotoSlotRef>, style: String): String {
        val shown = slots.take(MAX_ITEMS)
        val styleText = clean(style, MAX_STYLE_CHARS).ifEmpty { DEFAULT_STYLE }
        return buildString {
            appendLine("Create one picture for each numbered item below, in the same order.")
            appendLine("Use the same style for every picture.")
            appendLine("Style: $styleText")
            val planTitle = clean(taskTitle, MAX_TITLE_CHARS)
            if (planTitle.isNotEmpty()) appendLine("Plan: $planTitle")
            appendLine()
            shown.forEachIndexed { index, ref -> appendLine("${index + 1}. ${describe(ref)}") }
            if (slots.size > shown.size) {
                appendLine()
                appendLine("(${slots.size - shown.size} more pictures are not listed.)")
            }
        }.trimEnd()
    }

    private fun describe(ref: PhotoSlotRef): String {
        val title = clean(ref.subtaskTitle, MAX_TITLE_CHARS).ifEmpty { "Item" }
        val stepIndex = ref.stepIndex
        val label = if (stepIndex == null) {
            title
        } else {
            val stepName = clean(ref.stepTitle.orEmpty(), MAX_TITLE_CHARS).ifEmpty { "Step ${stepIndex + 1}" }
            "$title - $stepName"
        }
        val what = clean(ref.slot.brief ?: ref.slot.alt ?: title, MAX_LINE_PART_CHARS)
        return "$label: $what"
    }

    private fun clean(value: String, limit: Int): String {
        val spaced = buildString {
            value.forEach { char ->
                append(if (Character.isISOControl(char) || char.isWhitespace()) ' ' else char)
            }
        }
        return spaced.replace(whitespaceRuns, " ").trim().take(limit).trim()
    }
}
