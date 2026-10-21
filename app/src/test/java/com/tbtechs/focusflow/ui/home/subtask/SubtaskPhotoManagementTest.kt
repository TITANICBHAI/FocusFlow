package com.tbtechs.focusflow.ui.home.subtask

import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.SubtaskImage
import com.tbtechs.focusflow.data.model.SubtaskStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskPhotoManagementTest {
    @Test
    fun replacingOrRemovingStepPhotoPreservesOtherSubtaskData() {
        val source = Subtask(
            id = "press",
            title = "Press",
            done = true,
            image = SubtaskImage("item-asset", "Item photo"),
            steps = listOf(
                SubtaskStep(
                    title = "Set up",
                    text = "Place your hands evenly.",
                    image = SubtaskImage("old-step-asset", "Hand position"),
                ),
            ),
        )

        val replaced = source.withPhoto(
            SubtaskPhotoTarget.Step(0),
            SubtaskImage("new-step-asset", "Updated position"),
        )
        val removed = replaced.withPhoto(SubtaskPhotoTarget.Step(0), null)

        assertEquals("press", replaced.id)
        assertTrue(replaced.done)
        assertEquals(SubtaskImage("item-asset", "Item photo"), replaced.image)
        assertEquals(
            SubtaskImage("new-step-asset", "Updated position"),
            replaced.steps.single().image,
        )
        assertEquals("Place your hands evenly.", removed.steps.single().text)
        assertEquals(null, removed.steps.single().image)
        assertEquals(SubtaskImage("item-asset", "Item photo"), removed.image)
    }

    @Test
    fun invalidStepTargetDoesNotMutateSubtask() {
        val source = Subtask(
            id = "press",
            title = "Press",
            steps = listOf(SubtaskStep(text = "Place your hands evenly.")),
        )

        assertSame(source, source.withPhoto(SubtaskPhotoTarget.Step(2), SubtaskImage("asset")))
    }
}
