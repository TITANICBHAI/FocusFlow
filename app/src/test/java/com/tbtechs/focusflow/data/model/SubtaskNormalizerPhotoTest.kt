package com.tbtechs.focusflow.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskNormalizerPhotoTest {
    private fun normalize(vararg subtasks: Subtask) = SubtaskNormalizer.normalize(subtasks.toList())

    @Test
    fun keepsATidyBriefAndAValidLink() {
        val result = normalize(
            Subtask(
                id = "a",
                title = "Push-ups",
                photo = PhotoSlot(
                    brief = "  Side view\nof a push-up ",
                    url = " https://upload.wikimedia.org/p.jpg ",
                    alt = "Push-up",
                ),
            ),
        )

        assertEquals(
            PhotoSlot("Side view\nof a push-up", "https://upload.wikimedia.org/p.jpg", "Push-up"),
            result.subtasks.single().photo,
        )
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun dropsAnUnusableLinkButKeepsTheBrief() {
        val result = normalize(
            Subtask(id = "a", title = "A", photo = PhotoSlot(brief = "A plank", url = "http://example.com/p.jpg")),
        )

        assertEquals(PhotoSlot(brief = "A plank", url = null, alt = null), result.subtasks.single().photo)
        assertTrue(result.warnings.any { it.contains("1 photo links were removed") })
    }

    @Test
    fun removesSlotsThatSayNothingUseful() {
        val result = normalize(
            Subtask(id = "a", title = "A", photo = PhotoSlot(url = "javascript:alert(1)")),
            Subtask(id = "b", title = "B", photo = PhotoSlot(brief = "   ", url = "  ", alt = null)),
        )

        assertNull(result.subtasks[0].photo)
        assertNull(result.subtasks[1].photo)
        assertTrue(result.warnings.any { it.contains("1 photo links were removed") })
        assertTrue(result.warnings.any { it.contains("1 empty photo fields were removed") })
    }

    @Test
    fun altTextStandsInForAMissingBrief() {
        val slot = normalize(
            Subtask(id = "a", title = "A", photo = PhotoSlot(alt = "Plank from the side")),
        ).subtasks.single().photo

        assertEquals(PhotoSlot(brief = "Plank from the side", url = null, alt = "Plank from the side"), slot)
    }

    @Test
    fun aStoredImageAlwaysWinsOverASlot() {
        val result = normalize(
            Subtask(
                id = "a",
                title = "A",
                image = SubtaskImage("abc"),
                photo = PhotoSlot(brief = "x"),
                steps = listOf(
                    SubtaskStep(text = "One", image = SubtaskImage("def"), photo = PhotoSlot(brief = "y")),
                    SubtaskStep(text = "Two", photo = PhotoSlot(brief = "z")),
                ),
            ),
        )

        val subtask = result.subtasks.single()
        assertNull(subtask.photo)
        assertNull(subtask.steps[0].photo)
        assertEquals(PhotoSlot(brief = "z"), subtask.steps[1].photo)
    }

    @Test
    fun trimsLongValues() {
        val slot = normalize(
            Subtask(
                id = "a",
                title = "A",
                photo = PhotoSlot(brief = "b".repeat(400), alt = "a".repeat(300)),
            ),
        ).subtasks.single().photo!!

        assertEquals(SubtaskLimits.MAX_BRIEF_CHARS, slot.brief!!.length)
        assertEquals(SubtaskLimits.MAX_ALT_CHARS, slot.alt!!.length)
    }

    @Test
    fun removesControlCharactersFromBriefs() {
        val slot = normalize(
            Subtask(id = "a", title = "A", photo = PhotoSlot(brief = "Bar\u0000bell\u0007 row")),
        ).subtasks.single().photo!!

        assertEquals("Barbell row", slot.brief)
    }

    @Test
    fun anOverlongLinkIsRemoved() {
        val link = "https://example.com/" + "a".repeat(SubtaskLimits.MAX_URL_CHARS)
        val result = normalize(Subtask(id = "a", title = "A", photo = PhotoSlot(brief = "x", url = link)))

        assertNull(result.subtasks.single().photo!!.url)
    }
}
