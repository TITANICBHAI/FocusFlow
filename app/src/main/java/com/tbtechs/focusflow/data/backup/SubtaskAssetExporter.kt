package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.images.SubtaskImageStore
import com.tbtechs.focusflow.data.model.Task
internal class SubtaskAssetExporter(
    @Suppress("UNUSED_PARAMETER") imageStore: SubtaskImageStore,
) {
    fun referencedAssetIds(tasks: List<Task>): Set<String> =
        SubtaskImageStore.referencedAssetIds(tasks)
}
