package com.tbtechs.focusflow.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtaskLayoutPolicyTest {
    private val step = SubtaskStep(text = "Move with control.")

    @Test
    fun explicitStepsRequiresAtLeastOneStep() {
        assertEquals(
            SubtaskLayoutMode.STEPS,
            SubtaskLayoutPolicy.resolve(subtask(layout = "steps", steps = listOf(step))),
        )
        assertEquals(
            SubtaskLayoutMode.OVERVIEW,
            SubtaskLayoutPolicy.resolve(subtask(layout = "steps")),
        )
    }

    @Test
    fun explicitOverviewWinsEvenWhenStepsExist() {
        assertEquals(
            SubtaskLayoutMode.OVERVIEW,
            SubtaskLayoutPolicy.resolve(subtask(layout = "overview", steps = listOf(step))),
        )
    }

    @Test
    fun missingOrUnknownLayoutAutomaticallyUsesStepsWhenAvailable() {
        assertEquals(
            SubtaskLayoutMode.STEPS,
            SubtaskLayoutPolicy.resolve(subtask(steps = listOf(step))),
        )
        assertEquals(
            SubtaskLayoutMode.OVERVIEW,
            SubtaskLayoutPolicy.resolve(subtask()),
        )
        assertEquals(
            SubtaskLayoutMode.STEPS,
            SubtaskLayoutPolicy.resolve(subtask(layout = "unexpected", steps = listOf(step))),
        )
    }

    private fun subtask(
        layout: String? = null,
        steps: List<SubtaskStep> = emptyList(),
    ) = Subtask(id = "item", title = "Item", layout = layout, steps = steps)
}
