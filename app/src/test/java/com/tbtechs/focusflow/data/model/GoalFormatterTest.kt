package com.tbtechs.focusflow.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalFormatterTest {
    @Test
    fun formatsSetGoalsAndOptionalDetails() {
        assertEquals(
            FormattedSubtaskGoal(
                primary = "3 × 12",
                details = listOf("20 kg", "per side"),
                rest = "Rest 60 s",
            ),
            GoalFormatter.format(
                SubtaskGoal(
                    kind = "sets",
                    sets = 3,
                    reps = 12,
                    restSeconds = 60,
                    weightKg = 20.0,
                    label = "per side",
                ),
            ),
        )
        assertEquals(
            FormattedSubtaskGoal(primary = "3 × 30 s", rest = null),
            GoalFormatter.format(SubtaskGoal(kind = "sets", sets = 3, seconds = 30, restSeconds = 0)),
        )
    }

    @Test
    fun formatsRepetitionDurationDistanceAndTextGoals() {
        assertEquals(
            "12 reps",
            GoalFormatter.format(SubtaskGoal(kind = "reps", reps = 12))?.primary,
        )
        assertEquals(
            "45 s",
            GoalFormatter.format(SubtaskGoal(kind = "duration", seconds = 45))?.primary,
        )
        assertEquals(
            "500 m",
            GoalFormatter.format(SubtaskGoal(kind = "distance", meters = 500))?.primary,
        )
        assertEquals(
            "2.5 km",
            GoalFormatter.format(SubtaskGoal(kind = "distance", meters = 2_500))?.primary,
        )
        assertEquals(
            "Hold until steady",
            GoalFormatter.format(
                SubtaskGoal(kind = "text", label = "Hold until steady"),
            )?.primary,
        )
    }

    @Test
    fun formatsRepetitionRangesForSetsAndRepetitionGoals() {
        assertEquals(
            "2 × 6–10",
            GoalFormatter.format(
                SubtaskGoal(kind = "sets", sets = 2, reps = 6, repsMax = 10),
            )?.primary,
        )
        assertEquals(
            "12–15 reps",
            GoalFormatter.format(
                SubtaskGoal(kind = "reps", reps = 12, repsMax = 15),
            )?.primary,
        )
        assertEquals(
            "12 reps",
            GoalFormatter.format(
                SubtaskGoal(kind = "reps", reps = 12, repsMax = 11),
            )?.primary,
        )
    }

    @Test
    fun rejectsGoalsThatDoNotMeetTheRequiredShapeOrRanges() {
        assertNull(GoalFormatter.format(SubtaskGoal(kind = "sets", sets = 0, reps = 12)))
        assertNull(GoalFormatter.format(SubtaskGoal(kind = "sets", sets = 3)))
        assertNull(GoalFormatter.format(SubtaskGoal(kind = "reps", reps = 10_000)))
        assertNull(GoalFormatter.format(SubtaskGoal(kind = "text", label = " ")))
        assertNull(GoalFormatter.format(SubtaskGoal(kind = "unknown")))
    }
}
