package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasRole
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.SubtaskGoal
import com.tbtechs.focusflow.data.model.SubtaskImage
import com.tbtechs.focusflow.data.model.SubtaskStep
import com.tbtechs.focusflow.data.model.Task
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TaskSubtaskUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun taskCardShowsAccessibleProgressOnlyForTasksWithSubtasks() {
        composeRule.setContent {
            MaterialTheme {
                Column {
                    TaskCard(
                        task = task(subtasks = emptyList()),
                        isActive = false,
                        onOpen = {},
                        onComplete = {},
                        onSkip = {},
                        onExtend = {},
                    )
                    TaskCard(
                        task = task(
                            subtasks = listOf(
                                Subtask(id = "press", title = "Press", done = true),
                                Subtask(id = "row", title = "Row"),
                            ),
                        ),
                        isActive = false,
                        onOpen = {},
                        onComplete = {},
                        onSkip = {},
                        onExtend = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("1 of 2 subtasks done").assertExists()
        composeRule.onNodeWithText("1/2").assertExists()
        composeRule.onNodeWithText("0/0").assertDoesNotExist()
    }

    @Test
    fun checkboxUpdatesProgressAndOnlyExplicitActionCompletesTask() {
        val state = mutableStateOf(
            task(
                subtasks = listOf(
                    Subtask(id = "press", title = "Press"),
                    Subtask(id = "row", title = "Row"),
                ),
            ),
        )
        val completeCalls = AtomicInteger()

        composeRule.setContent {
            MaterialTheme {
                TaskDetailModal(
                    task = state.value,
                    onDismiss = {},
                    onComplete = { completeCalls.incrementAndGet() },
                    onSkip = {},
                    onExtend = {},
                    onStartFocus = {},
                    onEdit = {},
                    onSubtaskDone = { taskId, subtaskId, done ->
                        assertEquals("task-1", taskId)
                        state.value = state.value.copy(
                            subtasks = state.value.subtasks.map { subtask ->
                                if (subtask.id == subtaskId) subtask.copy(done = done) else subtask
                            },
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithText("0 of 2 done").assertExists()
        val incompleteCheckbox = hasRole(Role.Checkbox) and hasStateDescription("Not completed")
        composeRule.onNode(incompleteCheckbox).performClick()
        composeRule.onNodeWithText("1 of 2 done").assertExists()
        composeRule.onNodeWithText("All done — complete this task?").assertDoesNotExist()

        composeRule.onNode(incompleteCheckbox).performClick()
        composeRule.onNodeWithText("2 of 2 done").assertExists()
        composeRule.onNodeWithText("All done — complete this task?").assertExists()
        assertEquals(0, completeCalls.get())

        composeRule.onNodeWithText("Complete task").performClick()
        assertEquals(1, completeCalls.get())
    }

    @Test
    fun subtaskScreenTracksSetsAndCanReturnToDetails() {
        val state = mutableStateOf(
            task(
                subtasks = listOf(
                    Subtask(
                        id = "press",
                        title = "Press",
                        goal = SubtaskGoal(kind = "sets", sets = 2, reps = 8),
                        explanation = "Keep your back supported.",
                        steps = listOf(
                            SubtaskStep(title = "Set up", text = "Place your hands evenly."),
                            SubtaskStep(
                                title = "Press",
                                text = "Press upward with control.",
                                image = SubtaskImage(asset = "step-image", alt = "Pressing position"),
                            ),
                        ),
                    ),
                    Subtask(id = "row", title = "Row"),
                ),
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                TaskDetailModal(
                    task = state.value,
                    onDismiss = {},
                    onComplete = {},
                    onSkip = {},
                    onExtend = {},
                    onStartFocus = {},
                    onEdit = {},
                    onSubtaskSetsDone = { taskId, subtaskId, count ->
                        assertEquals("task-1", taskId)
                        state.value = state.value.copy(
                            subtasks = state.value.subtasks.map { subtask ->
                                if (subtask.id != subtaskId) {
                                    subtask
                                } else {
                                    val goalSets = subtask.goal?.sets ?: 0
                                    subtask.copy(setsDone = count, done = count == goalSets)
                                }
                            },
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithText("Press").performClick()
        composeRule.onNodeWithText("Item 1 of 2").assertExists()
        composeRule.onNodeWithText("2 × 8").assertExists()
        composeRule.onNodeWithText("Steps").assertExists()
        composeRule.onNodeWithText("Set up").assertExists()

        composeRule.onNodeWithContentDescription("Set 1, not completed").performClick()
        composeRule.onNodeWithText("1 of 2 sets").assertExists()
        composeRule.onNodeWithContentDescription("Set 2, not completed").performClick()
        composeRule.onNodeWithText("2 of 2 sets").assertExists()
        composeRule.onNodeWithText("Undo done").assertExists()

        composeRule.onNodeWithContentDescription("Back to task details").performClick()
        composeRule.onNodeWithText("2 of 2 done").assertExists()
    }

    @Test
    fun systemBackReturnsToTaskDetailsThenDismissesTheModal() {
        val dismissed = AtomicBoolean(false)
        composeRule.setContent {
            MaterialTheme {
                TaskDetailModal(
                    task = task(subtasks = listOf(Subtask(id = "press", title = "Press"))),
                    onDismiss = { dismissed.set(true) },
                    onComplete = {},
                    onSkip = {},
                    onExtend = {},
                    onStartFocus = {},
                    onEdit = {},
                )
            }
        }

        composeRule.onNodeWithText("Press").performClick()
        composeRule.onNodeWithText("Item 1 of 1").assertExists()

        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("0 of 1 done").assertExists()
        assertFalse(dismissed.get())

        Espresso.pressBack()
        composeRule.waitForIdle()
        assertTrue(dismissed.get())
    }

    @Test
    fun taskProgressResetRequiresConfirmation() {
        val resetCalls = AtomicInteger()
        composeRule.setContent {
            MaterialTheme {
                TaskDetailModal(
                    task = task(
                        subtasks = listOf(
                            Subtask(id = "press", title = "Press", done = true),
                        ),
                    ),
                    onDismiss = {},
                    onComplete = {},
                    onSkip = {},
                    onExtend = {},
                    onStartFocus = {},
                    onEdit = {},
                    onResetSubtasks = { resetCalls.incrementAndGet() },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Subtask options").performClick()
        composeRule.onNodeWithText("Reset progress").performClick()
        composeRule.onNodeWithText("Reset subtask progress").assertExists()
        assertEquals(0, resetCalls.get())
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(0, resetCalls.get())

        composeRule.onNodeWithContentDescription("Subtask options").performClick()
        composeRule.onNodeWithText("Reset progress").performClick()
        composeRule.onNodeWithText("Reset").performClick()
        assertEquals(1, resetCalls.get())
    }

    @Test
    fun itemProgressResetRequiresConfirmation() {
        val resetValues = mutableListOf<Int>()
        composeRule.setContent {
            MaterialTheme {
                TaskDetailModal(
                    task = task(
                        subtasks = listOf(
                            Subtask(
                                id = "press",
                                title = "Press",
                                goal = SubtaskGoal(kind = "sets", sets = 2, reps = 8),
                                setsDone = 1,
                            ),
                        ),
                    ),
                    onDismiss = {},
                    onComplete = {},
                    onSkip = {},
                    onExtend = {},
                    onStartFocus = {},
                    onEdit = {},
                    onSubtaskSetsDone = { _, _, value -> resetValues += value },
                )
            }
        }

        composeRule.onNodeWithText("Press").performClick()
        composeRule.onNodeWithContentDescription("Subtask options").performClick()
        composeRule.onNodeWithText("Reset this item").performClick()
        composeRule.onNodeWithText("Clear this subtask’s completion and set progress?").assertExists()
        assertTrue(resetValues.isEmpty())
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue(resetValues.isEmpty())

        composeRule.onNodeWithContentDescription("Subtask options").performClick()
        composeRule.onNodeWithText("Reset this item").performClick()
        composeRule.onNodeWithText("Reset").performClick()
        assertEquals(listOf(0), resetValues)
    }

    @Test
    fun overviewModeShowsStepsAsANumberedTextList() {
        composeRule.setContent {
            MaterialTheme {
                TaskDetailModal(
                    task = task(
                        subtasks = listOf(
                            Subtask(
                                id = "overview",
                                title = "Overview item",
                                layout = "overview",
                                image = SubtaskImage(asset = "overview-image", alt = "Overview illustration"),
                                steps = listOf(
                                    SubtaskStep(title = "Hidden step title", text = "Hidden step body."),
                                ),
                            ),
                        ),
                    ),
                    onDismiss = {},
                    onComplete = {},
                    onSkip = {},
                    onExtend = {},
                    onStartFocus = {},
                    onEdit = {},
                )
            }
        }

        composeRule.onNodeWithText("Overview item").performClick()
        composeRule.onNodeWithText("Overview").assertExists()
        composeRule.onNodeWithContentDescription("Image unavailable: Overview illustration").assertExists()
        composeRule.onNodeWithText("Steps").assertExists()
        composeRule.onNodeWithText("Hidden step title").assertExists()
        composeRule.onNodeWithText("Hidden step body.").assertExists()
    }

    @Test
    fun missingImageShowsAccessibleUnavailablePlaceholder() {
        composeRule.setContent {
            MaterialTheme {
                TaskDetailModal(
                    task = task(
                        subtasks = listOf(
                            Subtask(
                                id = "image",
                                title = "Image item",
                                image = SubtaskImage(asset = "missing-image", alt = "A clear image description"),
                            ),
                        ),
                    ),
                    onDismiss = {},
                    onComplete = {},
                    onSkip = {},
                    onExtend = {},
                    onStartFocus = {},
                    onEdit = {},
                )
            }
        }

        composeRule.onNodeWithText("Image item").performClick()
        composeRule.onNodeWithContentDescription("Image unavailable: A clear image description").assertExists()
        composeRule.onNodeWithText("Image unavailable").assertExists()
    }

    @Test
    fun subtaskImageOpensAndClosesFullScreenViewer() {
        composeRule.setContent {
            MaterialTheme {
                TaskDetailModal(
                    task = task(
                        subtasks = listOf(
                            Subtask(
                                id = "image",
                                title = "Image item",
                                layout = "overview",
                                image = SubtaskImage("image-asset", "Overview illustration"),
                            ),
                        ),
                    ),
                    onDismiss = {},
                    onComplete = {},
                    onSkip = {},
                    onExtend = {},
                    onStartFocus = {},
                    onEdit = {},
                )
            }
        }

        composeRule.onNodeWithText("Image item").performClick()
        composeRule.onNodeWithContentDescription(
            "Open full-screen image: Overview illustration",
        ).performClick()
        composeRule.onNodeWithContentDescription("Close full-screen image")
            .assertExists()
            .performClick()
        composeRule.onNodeWithContentDescription("Close full-screen image").assertDoesNotExist()
    }

    @Test
    fun existingTaskActionsRemainAvailableWithSubtasks() {
        val editCalls = AtomicInteger()
        val completeCalls = AtomicInteger()
        val skipCalls = AtomicInteger()
        val extendCalls = AtomicInteger()
        val startFocusCalls = AtomicInteger()
        composeRule.setContent {
            MaterialTheme {
                TaskDetailModal(
                    task = task(subtasks = listOf(Subtask(id = "press", title = "Press")))
                        .copy(focusMode = true),
                    onDismiss = {},
                    onComplete = { completeCalls.incrementAndGet() },
                    onSkip = { skipCalls.incrementAndGet() },
                    onExtend = { extendCalls.incrementAndGet() },
                    onStartFocus = { startFocusCalls.incrementAndGet() },
                    onEdit = { editCalls.incrementAndGet() },
                )
            }
        }

        composeRule.onNodeWithText("Edit").assertExists().performClick()
        composeRule.onNodeWithText("Complete").assertExists().performClick()
        composeRule.onNodeWithText("Skip").assertExists().performClick()
        composeRule.onNodeWithText("Extend").assertExists().performClick()
        composeRule.onNodeWithText("Start Focus").assertExists().performScrollTo().performClick()
        assertEquals(1, editCalls.get())
        assertEquals(1, completeCalls.get())
        assertEquals(1, skipCalls.get())
        assertEquals(1, extendCalls.get())
        assertEquals(1, startFocusCalls.get())
    }

    private fun task(subtasks: List<Subtask>) = Task(
        id = "task-1",
        title = "Strength session",
        description = null,
        startTime = "2026-10-21T08:00:00Z",
        endTime = "2026-10-21T09:00:00Z",
        durationMinutes = 60,
        status = "scheduled",
        priority = "medium",
        tags = emptyList(),
        reminders = emptyList(),
        color = "#4F46E5",
        focusMode = false,
        focusAllowedPackages = null,
        createdAt = "2026-10-20T08:00:00Z",
        updatedAt = "2026-10-20T08:00:00Z",
        subtasks = subtasks,
    )
}
