package com.tbtechs.focusflow.ui.home.subtask

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tbtechs.focusflow.data.model.SubtaskGoal
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SubtaskTimerPanelUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun restToggleAndDurationStartAreAvailableForTimedGoals() {
        val durationStarted = AtomicInteger()
        val restEnabled = AtomicBoolean(true)
        val goal = SubtaskGoal(
            kind = "sets",
            sets = 3,
            seconds = 30,
            restSeconds = 10,
        )

        composeRule.setContent {
            MaterialTheme {
                SubtaskTimerPanel(
                    goal = goal,
                    state = SubtaskTimerState().start(SubtaskTimerMode.REST, 10),
                    restTimerEnabled = restEnabled.get(),
                    onRestTimerEnabledChange = restEnabled::set,
                    onStartDurationTimer = durationStarted::set,
                    onStopTimer = {},
                )
            }
        }

        composeRule.onNodeWithText("Rest timer").assertExists()
        composeRule.onNodeWithText("Rest · 10s").assertExists()
        composeRule.onNodeWithContentDescription("Start duration timer").performClick()
        composeRule.onNodeWithContentDescription("Rest timer").performClick()

        assertEquals(30, durationStarted.get())
        assertEquals(false, restEnabled.get())
    }
}
