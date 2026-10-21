package com.tbtechs.focusflow.data.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskParserTest {
    @Test
    fun parsesGoalsStepsAndReferencedImages() {
        val result = parse(
            """
            [{
              "id":"pushup",
              "title":"Push-ups",
              "goal":{"kind":"sets","sets":3,"reps":12,"restSeconds":60},
              "steps":[{"title":"Start","text":"Hands under shoulders","image":{"asset":"photo-1"}}],
              "tips":["Brace your core"]
            }]
            """.trimIndent(),
            setOf("photo-1"),
        )

        assertEquals(1, result.subtasks.size)
        assertEquals("3 × 12", com.tbtechs.focusflow.data.model.GoalFormatter
            .format(requireNotNull(result.subtasks.single().goal))?.primary)
        assertEquals("photo-1", result.subtasks.single().steps.single().image?.asset)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun missingTitleDropsOnlyThatSubtaskAndAggregatesWarning() {
        val result = parse("""[{"id":"no-title"},{"id":"kept","title":"Valid"}]""")

        assertEquals(listOf("Valid"), result.subtasks.map { it.title })
        assertTrue(result.warnings.any { it.contains("1 subtasks without a valid title") })
    }

    @Test
    fun malformedSubtaskEntryDoesNotDropValidSiblings() {
        val result = parse("""[null,{"id":"kept","title":"Valid"}]""")

        assertEquals(listOf("Valid"), result.subtasks.map { it.title })
        assertTrue(result.warnings.any { it.contains("1 malformed subtasks were skipped") })
    }

    @Test
    fun invalidGoalAndRemoteImageAreRemovedButSubtaskSurvives() {
        val result = parse(
            """[{"id":"item","title":"Keep me","goal":{"kind":"sets","sets":0},"image":{"url":"https://example.invalid/image.png"}}]""",
        )

        assertEquals(1, result.subtasks.size)
        assertNull(result.subtasks.single().goal)
        assertNull(result.subtasks.single().image)
        assertTrue(result.warnings.any { it.contains("invalid subtask goals") })
        assertTrue(result.warnings.any { it.contains("remote images aren't supported") })
    }

    @Test
    fun invalidNestedValuesAreDroppedWithoutDroppingValidSubtaskContent() {
        val result = parse(
            """
            [{
              "id":"kept","title":"Kept",
              "goal":false,
              "image":42,
              "steps":[{"title":"No text"},{"text":7},{"text":"Valid step","image":[]}],
              "tips":["Keep this",42],
              "avoid":[null,"Avoid this"]
            },{
              "id":"empty-image","title":"Empty image","image":{}
            }]
            """.trimIndent(),
        )

        assertEquals(2, result.subtasks.size)
        val subtask = result.subtasks.first()
        assertEquals("Kept", subtask.title)
        assertNull(subtask.goal)
        assertNull(subtask.image)
        assertEquals(listOf("Valid step"), subtask.steps.map { it.text })
        assertNull(subtask.steps.single().image)
        assertEquals(listOf("Keep this"), subtask.tips)
        assertEquals(listOf("Avoid this"), subtask.avoid)
        assertNull(result.subtasks[1].image)
        assertTrue(result.warnings.any { it.contains("invalid subtask goals") })
        assertTrue(result.warnings.any { it.contains("unsupported images") })
        assertTrue(result.warnings.any { it.contains("malformed steps were skipped") })
        assertTrue(result.warnings.any { it.contains("invalid tips/avoid items") })
    }

    @Test
    fun unknownFieldsAreWarnedWithTypoSuggestionAndDoNotRejectSubtask() {
        val result = parse(
            """
            [{
              "id":"item","title":"Kept","explanaton":"plain text",
              "goal":{"kind":"sets","sets":2,"reps":6,"goall":"ignored"},
              "image":{"asset":"photo","decription":"ignored"},
              "steps":[{"text":"Do this","teh":true}]
            }]
            """.trimIndent(),
            setOf("photo"),
        )

        assertEquals(1, result.subtasks.size)
        val warning = result.warnings.joinToString(" ")
        assertTrue(warning.contains("unknown fields ignored"))
        assertTrue(warning.contains("explanaton"))
        assertTrue(warning.contains("explanation"))
        assertTrue(warning.contains("goall"))
        assertTrue(warning.contains("decription"))
        assertTrue(warning.contains("4 unknown fields"))
    }

    @Test
    fun parsesRepRangesForSetAndRepetitionGoals() {
        val result = parse(
            """
            [
              {"id":"sets","title":"Sets","goal":{"kind":"sets","sets":2,"reps":6,"repsMax":10}},
              {"id":"reps","title":"Reps","goal":{"kind":"reps","reps":12,"repsMax":15}},
              {"id":"invalid-range","title":"Invalid range","goal":{"kind":"sets","sets":2,"reps":6,"repsMax":5}}
            ]
            """.trimIndent(),
        )

        assertEquals(10, result.subtasks[0].goal?.repsMax)
        assertEquals(15, result.subtasks[1].goal?.repsMax)
        assertEquals(6, result.subtasks[2].goal?.reps)
        assertNull(result.subtasks[2].goal?.repsMax)
        assertTrue(result.warnings.any { it.contains("invalid repsMax fields") })
    }

    @Test
    fun missingAssetIsRemovedAndUnknownFieldsAreIgnored() {
        val result = parse(
            """[{"id":"item","title":"Valid","futureField":true,"goal":{"kind":"duration","seconds":45,"future":1},"image":{"asset":"missing"}}]""",
        )

        assertEquals(1, result.subtasks.size)
        assertNull(result.subtasks.single().image)
        assertTrue(result.warnings.any { it.contains("missing assets") })
        assertFalse(result.warnings.any { it.contains("malformed subtasks") })
    }

    @Test
    fun truncatesOverLimitSubtasksAndSkipsMalformedStep() {
        val many = (1..(BackupJsonLimits.MAX_SUBTASKS_PER_TASK + 1)).joinToString(",") {
            """{"id":"s$it","title":"Item $it","steps":[{"title":"Missing text"}]}"""
        }
        val result = parse("[$many]")

        assertEquals(BackupJsonLimits.MAX_SUBTASKS_PER_TASK, result.subtasks.size)
        assertTrue(result.warnings.any { it.contains("truncated at the per-task limit") })
        assertTrue(result.warnings.any { it.contains("malformed steps were skipped") })
    }

    @Test
    fun ignoresAnOversizedPerTaskSubtaskPayload() {
        val longText = "x".repeat(BackupJsonLimits.MAX_STEP_TEXT_CHARS)
        val steps = (1..30).joinToString(",") { """{"text":"$longText"}""" }
        val taskSubtasks = (1..3).joinToString(",") {
            """{"id":"s$it","title":"Subtask $it","steps":[$steps]}"""
        }
        val result = parse("[$taskSubtasks]")

        assertTrue(result.subtasks.isEmpty())
        assertTrue(result.warnings.any { it.contains("subtask payloads over 96 KiB were ignored") })
    }

    private fun parse(json: String, assets: Set<String> = emptySet()) =
        SubtaskParser.parse(Json.parseToJsonElement(json), assets)
}
