package com.tbtechs.focusflow.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskNormalizerTest {
    @Test
    fun generatesMissingIdsAndMakesLaterDuplicatesUnique() {
        val result = SubtaskNormalizer.normalize(
            listOf(
                subtask(id = "same"),
                subtask(id = "same"),
                subtask(id = "same-2"),
                subtask(id = ""),
            ),
        )

        assertEquals(listOf("same", "same-2", "same-2-2", "s4"), result.subtasks.map(Subtask::id))
        assertTrue(result.warnings.any { it.contains("IDs were generated or normalized") })
    }

    @Test
    fun trimsStringsRemovesControlsAndNormalizesProgress() {
        val result = SubtaskNormalizer.normalize(
            listOf(
                subtask(
                    id = "bad/id",
                    title = "  Lift\u0000\nwith control\t ",
                    subtitle = " \u0000 ",
                    explanation = "  Keep\r\nspine neutral  ",
                    goal = SubtaskGoal(kind = "sets", sets = 3, reps = 12),
                    setsDone = 9,
                    steps = listOf(
                        SubtaskStep(title = "  Start\t ", text = "  Brace\u0000 first  "),
                    ),
                ),
            ),
        )

        val normalized = result.subtasks.single()
        assertEquals("bad_id", normalized.id)
        assertEquals("Lift\nwith control", normalized.title)
        assertNull(normalized.subtitle)
        assertEquals("Keep\nspine neutral", normalized.explanation)
        assertEquals(3, normalized.setsDone)
        assertEquals("Start", normalized.steps.single().title)
        assertEquals("Brace first", normalized.steps.single().text)
    }

    @Test
    fun completedSetGoalFillsProgressAndInvalidGoalDoesNotDropSubtask() {
        val result = SubtaskNormalizer.normalize(
            listOf(
                subtask(
                    goal = SubtaskGoal(kind = "sets", sets = 4, reps = 8),
                    done = true,
                    setsDone = 1,
                ),
                subtask(id = "bad-goal", goal = SubtaskGoal(kind = "sets", sets = 0, reps = 8)),
            ),
        )

        assertEquals(4, result.subtasks[0].setsDone)
        assertNull(result.subtasks[1].goal)
        assertEquals(2, result.subtasks.size)
        assertTrue(result.warnings.any { it.contains("invalid subtask goals were removed") })
    }

    @Test
    fun skipsBlankRequiredTextAndDropsInvalidOptionalImageAndLayout() {
        val result = SubtaskNormalizer.normalize(
            listOf(
                subtask(
                    title = " \u0000 ",
                ),
                subtask(
                    id = "valid",
                    image = SubtaskImage(asset = "../unsafe"),
                    layout = "sideways",
                    steps = listOf(SubtaskStep(text = " \t ")),
                ),
            ),
        )

        assertEquals(1, result.subtasks.size)
        assertNull(result.subtasks.single().image)
        assertNull(result.subtasks.single().layout)
        assertTrue(result.subtasks.single().steps.isEmpty())
        assertTrue(result.warnings.any { it.contains("blank titles were skipped") })
        assertTrue(result.warnings.any { it.contains("invalid subtask images were removed") })
        assertTrue(result.warnings.any { it.contains("invalid layout values were reset") })
        assertTrue(result.warnings.any { it.contains("blank text were skipped") })
    }

    @Test
    fun truncatesStringsAndListsWithWarningCounts() {
        val source = subtask(
            title = "T".repeat(SubtaskLimits.MAX_TITLE_CHARS + 3),
            explanation = "E".repeat(SubtaskLimits.MAX_EXPLANATION_CHARS + 2),
            steps = List(SubtaskLimits.MAX_STEPS + 1) { SubtaskStep(text = "Step") },
            tips = List(SubtaskLimits.MAX_TIPS + 1) { "Tip" },
            avoid = List(SubtaskLimits.MAX_TIPS + 2) { "Avoid" },
        )
        val result = SubtaskNormalizer.normalize(
            List(SubtaskLimits.MAX_SUBTASKS + 1) { index ->
                if (index == 0) source else subtask(id = "item-$index")
            },
        )

        assertEquals(SubtaskLimits.MAX_SUBTASKS, result.subtasks.size)
        assertEquals(SubtaskLimits.MAX_TITLE_CHARS, result.subtasks.first().title.length)
        assertEquals(SubtaskLimits.MAX_EXPLANATION_CHARS, result.subtasks.first().explanation?.length)
        assertEquals(SubtaskLimits.MAX_STEPS, result.subtasks.first().steps.size)
        assertEquals(SubtaskLimits.MAX_TIPS, result.subtasks.first().tips.size)
        assertEquals(SubtaskLimits.MAX_TIPS, result.subtasks.first().avoid.size)
        assertTrue(result.warnings.any { it.startsWith("1 subtasks were truncated") })
        assertTrue(result.warnings.any { it.startsWith("1 steps were truncated") })
        assertTrue(result.warnings.any { it.startsWith("3 tips or avoid items were truncated") })
        assertTrue(result.warnings.any { it.startsWith("2 strings were trimmed") })
    }

    @Test
    fun allowsNewlineButRemovesOtherControlCharacters() {
        val normalized = SubtaskNormalizer.normalize(
            listOf(subtask(title = "First\r\nSecond\u0000Third")),
        ).subtasks.single()

        assertEquals("First\nSecondThird", normalized.title)
        assertFalse(normalized.title.contains('\r'))
    }

    private fun subtask(
        id: String = "item",
        title: String = "Exercise",
        done: Boolean = false,
        subtitle: String? = null,
        goal: SubtaskGoal? = null,
        setsDone: Int = 0,
        image: SubtaskImage? = null,
        layout: String? = null,
        explanation: String? = null,
        steps: List<SubtaskStep> = emptyList(),
        tips: List<String> = emptyList(),
        avoid: List<String> = emptyList(),
    ) = Subtask(
        id = id,
        title = title,
        done = done,
        subtitle = subtitle,
        goal = goal,
        setsDone = setsDone,
        image = image,
        layout = layout,
        explanation = explanation,
        steps = steps,
        tips = tips,
        avoid = avoid,
    )
}
