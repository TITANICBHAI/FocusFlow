package com.tbtechs.focusflow.data.model

import java.text.Normalizer
import java.util.Locale

/** One item or step that still needs a picture. [stepIndex] is null for the item itself. */
data class PhotoSlotRef(
    val subtaskId: String,
    val subtaskTitle: String,
    val stepIndex: Int?,
    val stepTitle: String?,
    val slot: PhotoSlot,
)

/** A picture that was stored on this device and is ready to be attached to the slot it came from. */
data class DownloadedPhoto(
    val subtaskId: String,
    val stepIndex: Int?,
    val url: String?,
    val assetId: String,
)

/** Pure helpers for photo slots. They never touch the network, the database or the file system. */
object SubtaskPhotoSlots {
    private val repeatedSpaces = Regex(" {2,}")

    /** Every item and step that has a slot and no image, in reading order. */
    fun collect(subtasks: List<Subtask>): List<PhotoSlotRef> = buildList {
        subtasks.forEach { subtask ->
            val itemSlot = subtask.photo
            if (subtask.image == null && itemSlot != null) {
                add(PhotoSlotRef(subtask.id, subtask.title, null, null, itemSlot))
            }
            subtask.steps.forEachIndexed { index, step ->
                val stepSlot = step.photo
                if (step.image == null && stepSlot != null) {
                    add(PhotoSlotRef(subtask.id, subtask.title, index, step.title, stepSlot))
                }
            }
        }
    }

    fun count(subtasks: List<Subtask>): Int = collect(subtasks).size

    /**
     * Attaches stored pictures. A slot is only filled while it still has an empty image and the
     * same link, so a change the user made while a download ran is never overwritten.
     */
    fun applyDownloads(subtasks: List<Subtask>, downloads: List<DownloadedPhoto>): List<Subtask> {
        if (downloads.isEmpty()) return subtasks
        return subtasks.map { subtask ->
            downloads
                .filter { it.subtaskId == subtask.id }
                .fold(subtask) { current, download -> current.withDownload(download) }
        }
    }

    /**
     * Fills the first slots, in reading order, with the given stored pictures.
     * Returns the new list and how many pictures were used.
     */
    fun attachInOrder(subtasks: List<Subtask>, assetIds: List<String>): Pair<List<Subtask>, Int> {
        val targets = collect(subtasks)
        val used = minOf(targets.size, assetIds.size)
        if (used == 0) return subtasks to 0
        val downloads = (0 until used).map { index ->
            val target = targets[index]
            DownloadedPhoto(target.subtaskId, target.stepIndex, target.slot.url, assetIds[index])
        }
        return applyDownloads(subtasks, downloads) to used
    }

    /** Lower-case, accent-folded-by-NFKC, single-spaced form of a title used for exact matching. */
    fun titleKey(title: String): String {
        val folded = Normalizer.normalize(title, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val spaced = buildString {
            folded.forEach { char ->
                append(if (char.isWhitespace() || Character.isISOControl(char)) ' ' else char)
            }
        }
        return spaced.trim().replace(repeatedSpaces, " ")
    }

    /**
     * Pictures the user already attached to items, keyed by [titleKey]. Only pictures whose file
     * still exists are offered, and the first one found for a title wins.
     */
    fun buildTitleLibrary(tasks: List<Task>, exists: (String) -> Boolean): Map<String, SubtaskImage> {
        val library = linkedMapOf<String, SubtaskImage>()
        tasks.forEach { task ->
            task.subtasks.forEach { subtask ->
                val image = subtask.image ?: return@forEach
                val key = titleKey(subtask.title)
                if (key.isNotEmpty() && key !in library && exists(image.asset)) {
                    library[key] = image
                }
            }
        }
        return library
    }

    /**
     * Gives an item that has a slot and no image the picture the user already attached to an item
     * with the same title. Items without a slot are never touched. Returns the new list and the
     * number of items that received a picture.
     */
    fun reuseByTitle(
        subtasks: List<Subtask>,
        library: Map<String, SubtaskImage>,
    ): Pair<List<Subtask>, Int> {
        if (library.isEmpty()) return subtasks to 0
        var reused = 0
        val result = subtasks.map { subtask ->
            if (subtask.image != null || subtask.photo == null) return@map subtask
            val image = library[titleKey(subtask.title)] ?: return@map subtask
            reused++
            subtask.copy(image = image, photo = null)
        }
        return result to reused
    }

    /**
     * Removes stored pictures (for an export without images) but keeps a description of each
     * one as a photo slot, so the receiver can still see which pictures were there.
     */
    fun withoutImages(subtasks: List<Subtask>): List<Subtask> = subtasks.map { subtask ->
        subtask.copy(
            image = null,
            photo = subtask.photo ?: subtask.image.asPhotoSlot(),
            steps = subtask.steps.map { step ->
                step.copy(image = null, photo = step.photo ?: step.image.asPhotoSlot())
            },
        )
    }

    /**
     * Swaps the file ids of imported pictures for the ids of the copies stored on this device
     * ([remapped]: file id -> stored id). A picture that could not be stored is removed. If it had
     * alt text, that text is kept as a photo slot, so the item still shows that a picture is
     * missing. Existing slots are never changed.
     */
    fun remapPictures(subtasks: List<Subtask>, remapped: Map<String, String>): List<Subtask> =
        subtasks.map { subtask ->
            val (image, photo) = remapOne(subtask.image, subtask.photo, remapped)
            subtask.copy(
                image = image,
                photo = photo,
                steps = subtask.steps.map { step ->
                    val (stepImage, stepPhoto) = remapOne(step.image, step.photo, remapped)
                    step.copy(image = stepImage, photo = stepPhoto)
                },
            )
        }

    private fun remapOne(
        image: SubtaskImage?,
        photo: PhotoSlot?,
        remapped: Map<String, String>,
    ): Pair<SubtaskImage?, PhotoSlot?> {
        if (image == null) return null to photo
        val storedId = remapped[image.asset]
        if (storedId != null) return image.copy(asset = storedId) to null
        return null to (photo ?: image.asPhotoSlot())
    }

    private fun Subtask.withDownload(download: DownloadedPhoto): Subtask {
        val stepIndex = download.stepIndex
        if (stepIndex == null) {
            val slot = photo ?: return this
            if (image != null || slot.url != download.url) return this
            return copy(image = attached(slot, download.assetId), photo = null)
        }
        val step = steps.getOrNull(stepIndex) ?: return this
        val slot = step.photo ?: return this
        if (step.image != null || slot.url != download.url) return this
        return copy(
            steps = steps.mapIndexed { index, item ->
                if (index == stepIndex) {
                    item.copy(image = attached(slot, download.assetId), photo = null)
                } else {
                    item
                }
            },
        )
    }

    private fun attached(slot: PhotoSlot, assetId: String): SubtaskImage =
        SubtaskImage(
            asset = assetId,
            alt = (slot.alt ?: slot.brief)?.take(SubtaskLimits.MAX_ALT_CHARS),
        )
}
