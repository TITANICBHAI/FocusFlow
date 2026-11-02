package com.tbtechs.focusflow.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SubtaskPhotoSlotsSummaryTest {
    private fun subtask(id: String, photo: PhotoSlot?, image: SubtaskImage? = null) =
        Subtask(id = id, title = "Item $id", image = image, photo = photo)

    @Test
    fun summaryClassifiesSlotsByWhatCanStillHelp() {
        val list = listOf(
            subtask("1", PhotoSlot(url = "https://example.com/a.png")),
            subtask("2", PhotoSlot(url = "https://example.com/a.png")),
            subtask("3", PhotoSlot(url = "http://example.com/b.png")),
            subtask("4", PhotoSlot(brief = "a plank")),
            subtask("5", null),
        )
        val summary = SubtaskPhotoSlots.summarize(list)
        assertEquals(4, summary.total)
        assertEquals(1, summary.downloadableLinks)
        assertEquals(1, summary.brokenLinkSlots)
        assertEquals(1, summary.noLinkSlots)
    }

    @Test
    fun clearUnresolvedRemovesOnlyEmptySlots() {
        val withImage = subtask("1", null, SubtaskImage(asset = "a".repeat(64), alt = "x"))
        val empty = subtask("2", PhotoSlot(brief = "a plank"))
        val (cleaned, count) = SubtaskPhotoSlots.clearUnresolved(listOf(withImage, empty))
        assertEquals(1, count)
        assertNotNull(cleaned[0].image)
        assertNull(cleaned[1].photo)
        assertEquals("Item 2", cleaned[1].title)
        assertEquals(0, SubtaskPhotoSlots.count(cleaned))
    }

    @Test
    fun withoutMissingImagesKeepsDescriptionsAndOtherPictures() {
        val gone = "a".repeat(64)
        val kept = "b".repeat(64)
        val list = listOf(
            subtask("1", null, SubtaskImage(asset = gone, alt = "a plank")),
            subtask("2", null, SubtaskImage(asset = kept, alt = "a mat")),
            subtask("3", null, SubtaskImage(asset = gone)),
        )
        val (result, affected) = SubtaskPhotoSlots.withoutMissingImages(list, setOf(gone))
        assertEquals(2, affected)
        assertNull(result[0].image)
        assertEquals("a plank", result[0].photo?.brief)
        assertEquals(kept, result[1].image?.asset)
        assertNull(result[2].image)
        assertNull(result[2].photo)
    }

    @Test
    fun withoutMissingImagesCoversStepsAndIsANoOpWhenNothingIsMissing() {
        val gone = "c".repeat(64)
        val step = SubtaskStep(text = "Hold", image = SubtaskImage(asset = gone, alt = "hold pose"))
        val item = subtask("1", null).copy(steps = listOf(step))
        val (result, affected) = SubtaskPhotoSlots.withoutMissingImages(listOf(item), setOf(gone))
        assertEquals(1, affected)
        assertNull(result[0].steps[0].image)
        assertEquals("hold pose", result[0].steps[0].photo?.brief)
        val untouched = SubtaskPhotoSlots.withoutMissingImages(listOf(item), emptySet())
        assertEquals(0, untouched.second)
    }
}
