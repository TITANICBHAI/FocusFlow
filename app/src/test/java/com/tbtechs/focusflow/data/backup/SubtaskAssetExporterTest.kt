package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.SubtaskImage
import com.tbtechs.focusflow.data.model.SubtaskStep
import com.tbtechs.focusflow.data.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtaskAssetExporterTest {
    @Test
    fun collectsUniqueOverviewAndStepAssetReferences() {
        val tasks = listOf(
            task(
                "first",
                Subtask(
                    id = "part",
                    title = "Part",
                    image = SubtaskImage("overview"),
                    steps = listOf(
                        SubtaskStep("Start", "Set up.", SubtaskImage("step")),
                        SubtaskStep("Finish", "Complete.", SubtaskImage("overview")),
                    ),
                ),
            ),
            task(
                "second",
                Subtask(id = "other", title = "Other", image = SubtaskImage("another")),
            ),
        )

        assertEquals(
            setOf("overview", "step", "another"),
            SubtaskAssetExporter().referencedAssetIds(tasks),
        )
    }

    private fun task(id: String, subtask: Subtask) = Task(
        id = id,
        title = id,
        startTime = "2026-10-21T10:00:00Z",
        endTime = "2026-10-21T10:30:00Z",
        durationMinutes = 30,
        status = "completed",
        priority = "medium",
        color = "#22c55e",
        focusMode = false,
        createdAt = "2026-10-20T10:00:00Z",
        updatedAt = "2026-10-20T10:30:00Z",
        subtasks = listOf(subtask),
    )
}
