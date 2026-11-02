package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.model.PhotoSlot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskParserPhotoTest {
    @Test
    fun aPhotoObjectBecomesASlotOnTheItemAndOnSteps() {
        val result = parse(
            """
            [{
              "id":"pushup","title":"Push-ups",
              "photo":{"brief":"Side view of a push-up","url":"https://upload.wikimedia.org/x.jpg","alt":"Push-up"},
              "steps":[{"title":"Start","text":"Plank position","photo":{"brief":"Start position"}}]
            }]
            """.trimIndent(),
        )

        val subtask = result.subtasks.single()
        assertEquals(
            PhotoSlot("Side view of a push-up", "https://upload.wikimedia.org/x.jpg", "Push-up"),
            subtask.photo,
        )
        assertEquals(PhotoSlot(brief = "Start position"), subtask.steps.single().photo)
        assertNull(subtask.image)
        assertTrue(result.warnings.toString(), result.warnings.isEmpty())
    }

    @Test
    fun aLinkOnlyPhotoIsKept() {
        val result = parse("""[{"id":"a","title":"A","photo":{"url":"https://example.com/p.png"}}]""")

        assertEquals(PhotoSlot(url = "https://example.com/p.png"), result.subtasks.single().photo)
    }

    @Test
    fun anImageObjectThatOnlyDescribesAPictureBecomesASlotWithoutComplaints() {
        val result = parse(
            """
            [{
              "id":"a","title":"A","image":{"url":"https://example.com/p.png","brief":"A plank"},
              "steps":[{"text":"One","image":{"alt":"Hands under shoulders"}}]
            }]
            """.trimIndent(),
        )

        val subtask = result.subtasks.single()
        assertNull(subtask.image)
        assertEquals(PhotoSlot("A plank", "https://example.com/p.png", null), subtask.photo)
        assertEquals(
            PhotoSlot("Hands under shoulders", null, "Hands under shoulders"),
            subtask.steps.single().photo,
        )
        assertTrue(result.warnings.toString(), result.warnings.isEmpty())
    }

    @Test
    fun anUnusableLinkIsDroppedButTheBriefSurvives() {
        val result = parse(
            """[{"id":"a","title":"A","photo":{"brief":"A plank","url":"http://example.com/p.png"}}]""",
        )

        assertEquals(PhotoSlot(brief = "A plank"), result.subtasks.single().photo)
        assertTrue(result.warnings.any { it.contains("photo links were removed") })
    }

    @Test
    fun aMissingAssetBecomesASlotFromItsAltText() {
        val result = parse(
            """[{"id":"a","title":"A","image":{"asset":"gone","alt":"Plank from the side"}}]""",
        )

        val subtask = result.subtasks.single()
        assertNull(subtask.image)
        assertEquals("Plank from the side", subtask.photo?.brief)
        assertTrue(result.warnings.any { it.contains("images referencing missing assets were removed") })
    }

    @Test
    fun aRealImageWinsOverAPhotoOnTheSameItem() {
        val result = parse(
            """[{"id":"a","title":"A","image":{"asset":"photo-1"},"photo":{"brief":"ignored"}}]""",
            setOf("photo-1"),
        )

        assertEquals("photo-1", result.subtasks.single().image?.asset)
        assertNull(result.subtasks.single().photo)
    }

    @Test
    fun photoFieldsAreKnownAndUnknownKeysInsideThemAreReported() {
        val clean = parse("""[{"id":"a","title":"A","photo":{"brief":"x"}}]""")
        assertFalse(clean.warnings.toString(), clean.warnings.any { it.contains("unknown fields") })

        val typo = parse("""[{"id":"a","title":"A","photo":{"brief":"x","prompt":"y","urll":"z"}}]""")
        val text = typo.warnings.joinToString(" ")
        assertTrue(text, text.contains("2 unknown fields ignored"))
        assertTrue(text, text.contains("\"prompt\""))
        assertTrue(text, text.contains("did you mean \"url\""))
    }

    @Test
    fun invalidPhotoShapesAreRemovedWithOneWarning() {
        val result = parse(
            """
            [
              {"id":"a","title":"A","photo":"https://example.com/p.jpg"},
              {"id":"b","title":"B","photo":{}},
              {"id":"c","title":"C","photo":42},
              {"id":"d","title":"D","photo":{"brief":7,"url":false}}
            ]
            """.trimIndent(),
        )

        assertEquals(4, result.subtasks.size)
        assertTrue(result.subtasks.all { it.photo == null })
        assertTrue(result.warnings.toString(), result.warnings.any { it.contains("4 invalid photo fields were removed") })
    }

    @Test
    fun anImageObjectWithNothingUsefulIsStillUnsupported() {
        val result = parse("""[{"id":"a","title":"A","image":{}}]""")

        assertNull(result.subtasks.single().photo)
        assertTrue(result.warnings.any { it.contains("unsupported images") })
    }

    @Test
    fun theOldLinkOnlyImageNoLongerProducesTheRemoteImageWarning() {
        val result = parse("""[{"id":"a","title":"A","image":{"url":"https://example.invalid/image.png"}}]""")

        assertNull(result.subtasks.single().photo)
        assertFalse(result.warnings.any { it.contains("remote images") })
        assertTrue(result.warnings.any { it.contains("photo links were removed") })
    }

    private fun parse(json: String, assets: Set<String> = emptySet()) =
        SubtaskParser.parse(Json.parseToJsonElement(json), assets)
}
