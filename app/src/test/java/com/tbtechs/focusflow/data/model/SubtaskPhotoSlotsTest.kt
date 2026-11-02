package com.tbtechs.focusflow.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskPhotoSlotsTest {
    private fun task(vararg subtasks: Subtask) = Task(
        id = "task",
        title = "Task",
        startTime = "2026-10-21T10:00:00Z",
        endTime = "2026-10-21T10:30:00Z",
        durationMinutes = 30,
        status = "scheduled",
        priority = "medium",
        color = "#22c55e",
        focusMode = false,
        createdAt = "2026-10-20T10:00:00Z",
        updatedAt = "2026-10-20T10:00:00Z",
        subtasks = subtasks.toList(),
    )

    private val sample = listOf(
        Subtask(
            id = "pushup",
            title = "Push-ups",
            done = true,
            setsDone = 2,
            photo = PhotoSlot(brief = "Side view", url = "https://a.example/p.jpg"),
            steps = listOf(
                SubtaskStep(title = "Start", text = "Plank", photo = PhotoSlot(brief = "Start position")),
                SubtaskStep(text = "Lower"),
            ),
        ),
        Subtask(id = "plank", title = "Plank", image = SubtaskImage("keep"), photo = PhotoSlot(brief = "ignored")),
        Subtask(id = "squat", title = "Squat", photo = PhotoSlot(brief = "Front view")),
    )

    @Test
    fun collectsItemAndStepSlotsInReadingOrderAndSkipsPicturesThatExist() {
        val refs = SubtaskPhotoSlots.collect(sample)

        assertEquals(
            listOf("pushup" to null, "pushup" to 0, "squat" to null),
            refs.map { it.subtaskId to it.stepIndex },
        )
        assertEquals("Start", refs[1].stepTitle)
        assertEquals("Push-ups", refs[1].subtaskTitle)
        assertEquals(3, SubtaskPhotoSlots.count(sample))
    }

    @Test
    fun attachesADownloadOnlyWhileTheSlotStillHasThatLink() {
        val updated = SubtaskPhotoSlots.applyDownloads(
            sample,
            listOf(
                DownloadedPhoto("pushup", null, "https://a.example/p.jpg", "asset-1"),
                DownloadedPhoto("squat", null, "https://changed.example/p.jpg", "asset-2"),
                DownloadedPhoto("pushup", 0, null, "asset-3"),
                DownloadedPhoto("missing", null, null, "asset-4"),
                DownloadedPhoto("pushup", 9, null, "asset-5"),
            ),
        )

        val pushup = updated[0]
        assertEquals(SubtaskImage("asset-1", "Side view"), pushup.image)
        assertNull(pushup.photo)
        assertEquals(SubtaskImage("asset-3", "Start position"), pushup.steps[0].image)
        assertNull(pushup.steps[0].photo)
        assertTrue(pushup.done)
        assertEquals(2, pushup.setsDone)
        assertEquals(PhotoSlot(brief = "Front view"), updated[2].photo)
        assertNull(updated[2].image)
        assertEquals("keep", updated[1].image?.asset)
    }

    @Test
    fun neverOverwritesAnImageThatAppearedMeanwhile() {
        val current = listOf(
            Subtask(id = "a", title = "A", image = SubtaskImage("user-photo"), photo = PhotoSlot(brief = "x", url = "https://a.example/p.jpg")),
        )
        val updated = SubtaskPhotoSlots.applyDownloads(
            current,
            listOf(DownloadedPhoto("a", null, "https://a.example/p.jpg", "downloaded")),
        )

        assertEquals("user-photo", updated.single().image?.asset)
    }

    @Test
    fun noDownloadsReturnsTheSameList() {
        assertSame(sample, SubtaskPhotoSlots.applyDownloads(sample, emptyList()))
    }

    @Test
    fun usesTheSlotAltTextAndCapsIt() {
        val long = "x".repeat(250)
        val updated = SubtaskPhotoSlots.applyDownloads(
            listOf(Subtask(id = "a", title = "A", photo = PhotoSlot(brief = long, alt = "Short alt", url = null))),
            listOf(DownloadedPhoto("a", null, null, "id")),
        )
        assertEquals("Short alt", updated.single().image?.alt)

        val fromBrief = SubtaskPhotoSlots.applyDownloads(
            listOf(Subtask(id = "a", title = "A", photo = PhotoSlot(brief = long))),
            listOf(DownloadedPhoto("a", null, null, "id")),
        )
        assertEquals(SubtaskLimits.MAX_ALT_CHARS, fromBrief.single().image?.alt?.length)
    }

    @Test
    fun attachesPicturesInReadingOrderToTheFirstSlots() {
        val (updated, used) = SubtaskPhotoSlots.attachInOrder(sample, listOf("one", "two"))

        assertEquals(2, used)
        assertEquals("one", updated[0].image?.asset)
        assertEquals("two", updated[0].steps[0].image?.asset)
        assertNull(updated[2].image)
        assertEquals(PhotoSlot(brief = "Front view"), updated[2].photo)
    }

    @Test
    fun attachingMorePicturesThanSlotsUsesOnlyWhatFits() {
        val (updated, used) = SubtaskPhotoSlots.attachInOrder(sample, listOf("1", "2", "3", "4", "5"))

        assertEquals(3, used)
        assertEquals("3", updated[2].image?.asset)
        assertEquals(0, SubtaskPhotoSlots.attachInOrder(sample, emptyList()).second)
        assertEquals(0, SubtaskPhotoSlots.attachInOrder(emptyList(), listOf("1")).second)
    }

    @Test
    fun titleKeysIgnoreCaseWidthAndSpacing() {
        val expected = SubtaskPhotoSlots.titleKey("push-ups")
        assertEquals(expected, SubtaskPhotoSlots.titleKey("  Push-Ups  "))
        assertEquals(expected, SubtaskPhotoSlots.titleKey("PUSH-UPS"))
        assertEquals(SubtaskPhotoSlots.titleKey("push up"), SubtaskPhotoSlots.titleKey("Push\t\n  UP"))
        assertEquals("push", SubtaskPhotoSlots.titleKey("ＰＵＳＨ"))
        assertEquals("a b", SubtaskPhotoSlots.titleKey("a\u0000b"))
        assertEquals("", SubtaskPhotoSlots.titleKey(" \t "))
    }

    @Test
    fun theTitleLibraryOnlyOffersPicturesThatStillExistAndKeepsTheFirst() {
        val tasks = listOf(
            task(
                Subtask(id = "a", title = "Push-ups", image = SubtaskImage("first")),
                Subtask(id = "b", title = "Plank", image = SubtaskImage("gone")),
                Subtask(id = "c", title = "No picture"),
                Subtask(id = "d", title = "   ", image = SubtaskImage("blank-title")),
            ),
            task(Subtask(id = "e", title = "push-ups", image = SubtaskImage("second"))),
        )

        val library = SubtaskPhotoSlots.buildTitleLibrary(tasks) { it != "gone" }

        assertEquals(setOf("push-ups"), library.keys)
        assertEquals("first", library.getValue("push-ups").asset)
    }

    @Test
    fun reuseOnlyFillsItemsThatAskedForAPictureAndHaveNone() {
        val library = mapOf(
            "push-ups" to SubtaskImage("pushup-image", "Push-up"),
            "plank" to SubtaskImage("plank-image"),
        )
        val incoming = listOf(
            Subtask(id = "1", title = "PUSH-UPS", photo = PhotoSlot(brief = "Side view", url = "https://a.example/p.jpg")),
            Subtask(id = "2", title = "Plank"),
            Subtask(id = "3", title = "Plank", image = SubtaskImage("own"), photo = PhotoSlot(brief = "x")),
            Subtask(id = "4", title = "Burpees", photo = PhotoSlot(brief = "y")),
        )

        val (updated, reused) = SubtaskPhotoSlots.reuseByTitle(incoming, library)

        assertEquals(1, reused)
        assertEquals(SubtaskImage("pushup-image", "Push-up"), updated[0].image)
        assertNull(updated[0].photo)
        assertNull(updated[1].image)
        assertEquals("own", updated[2].image?.asset)
        assertNull(updated[3].image)
        assertEquals(PhotoSlot(brief = "y"), updated[3].photo)
        assertSame(incoming, SubtaskPhotoSlots.reuseByTitle(incoming, emptyMap()).first)
    }
    @Test
    fun exportWithoutImagesKeepsADescriptionOfEachPicture() {
        val original = listOf(
            Subtask(
                id = "a",
                title = "A",
                done = true,
                image = SubtaskImage("one", "Side view"),
                steps = listOf(
                    SubtaskStep(text = "Start", image = SubtaskImage("two")),
                    SubtaskStep(text = "End", image = SubtaskImage("three", "End position"), photo = PhotoSlot(brief = "Mine")),
                ),
            ),
            Subtask(id = "b", title = "B", photo = PhotoSlot(brief = "Keep me")),
        )

        val stripped = SubtaskPhotoSlots.withoutImages(original)

        assertNull(stripped[0].image)
        assertEquals(PhotoSlot("Side view", null, "Side view"), stripped[0].photo)
        assertNull(stripped[0].steps[0].image)
        assertNull(stripped[0].steps[0].photo)
        assertEquals(PhotoSlot(brief = "Mine"), stripped[0].steps[1].photo)
        assertTrue(stripped[0].done)
        assertEquals(PhotoSlot(brief = "Keep me"), stripped[1].photo)
    }

    @Test
    fun aLostPictureWithoutAltTextLeavesNoSlot() {
        assertNull(SubtaskImage("x").asPhotoSlot())
        assertNull(SubtaskImage("x", "   ").asPhotoSlot())
        assertNull((null as SubtaskImage?).asPhotoSlot())
        assertEquals(PhotoSlot("Alt", null, "Alt"), SubtaskImage("x", "Alt").asPhotoSlot())
    }

    @Test
    fun remapPicturesSwapsIdsAndKeepsADescriptionWhenAPictureCouldNotBeStored() {
        val original = listOf(
            Subtask(
                id = "a",
                title = "A",
                image = SubtaskImage("file-1", "Side view"),
                steps = listOf(
                    SubtaskStep(text = "Start", image = SubtaskImage("file-2", "Start position")),
                    SubtaskStep(text = "Middle", image = SubtaskImage("file-3")),
                    SubtaskStep(text = "End", photo = PhotoSlot(brief = "Finish")),
                ),
            ),
            Subtask(id = "b", title = "B", image = SubtaskImage("file-4"), photo = null),
        )

        val result = SubtaskPhotoSlots.remapPictures(original, mapOf("file-1" to "stored-1", "file-2" to "stored-2"))

        assertEquals(SubtaskImage("stored-1", "Side view"), result[0].image)
        assertNull(result[0].photo)
        assertEquals(SubtaskImage("stored-2", "Start position"), result[0].steps[0].image)
        assertNull(result[0].steps[1].image)
        assertNull(result[0].steps[1].photo)
        assertEquals(PhotoSlot(brief = "Finish"), result[0].steps[2].photo)
        assertNull(result[1].image)
        assertNull(result[1].photo)
    }

    @Test
    fun aPictureThatCouldNotBeStoredTurnsIntoASlotFromItsAltText() {
        val result = SubtaskPhotoSlots.remapPictures(
            listOf(Subtask(id = "a", title = "A", image = SubtaskImage("gone", "Plank from the side"))),
            emptyMap(),
        )

        assertNull(result.single().image)
        assertEquals(PhotoSlot("Plank from the side", null, "Plank from the side"), result.single().photo)
    }
}
