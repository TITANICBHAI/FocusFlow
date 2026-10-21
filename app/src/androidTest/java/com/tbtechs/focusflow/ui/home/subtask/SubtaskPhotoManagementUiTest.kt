package com.tbtechs.focusflow.ui.home.subtask

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.SubtaskImage
import com.tbtechs.focusflow.data.model.SubtaskStep
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SubtaskPhotoManagementUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun itemAndStepPhotoControlsReportTheirTargets() {
        val chosenTarget = AtomicReference<SubtaskPhotoTarget?>()
        val removedTarget = AtomicReference<SubtaskPhotoTarget?>()
        val subtask = Subtask(
            id = "press",
            title = "Press",
            steps = listOf(
                SubtaskStep(
                    title = "Set up",
                    text = "Place your hands evenly.",
                    image = SubtaskImage("step-asset"),
                ),
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                SubtaskPhotoManagementDialog(
                    subtask = subtask,
                    busy = false,
                    onChoosePhoto = chosenTarget::set,
                    onRemovePhoto = removedTarget::set,
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Add photo for Item photo")
            .assertExists()
            .performClick()
        composeRule.onNodeWithContentDescription("Replace photo for Set up")
            .assertExists()
        composeRule.onNodeWithContentDescription("Remove photo from Set up")
            .performClick()

        assertEquals(SubtaskPhotoTarget.Item, chosenTarget.get())
        assertEquals(SubtaskPhotoTarget.Step(0), removedTarget.get())
    }
}
