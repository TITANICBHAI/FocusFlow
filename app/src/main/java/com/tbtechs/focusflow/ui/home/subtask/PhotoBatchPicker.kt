package com.tbtechs.focusflow.ui.home.subtask

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.tbtechs.focusflow.data.images.SubtaskImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Returns a function that opens the system photo picker for several pictures at once.
 * The pictures are stored in the order they come back and handed to [onPicked] as stored ids;
 * the caller attaches them to the first empty photo slots, in reading order.
 * If any picture cannot be read, nothing is attached, so no picture lands on the wrong slot.
 */
@Composable
internal fun rememberBatchPhotoPicker(
    slotCount: Int,
    imageStore: SubtaskImageStore?,
    onPicked: (List<String>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnPicked = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = slotCount.coerceIn(2, 30)),
    ) { uris ->
        val store = imageStore
        if (uris.isEmpty() || store == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ids = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    runCatching {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("The selected photo could not be opened.")
                        input.use { store.ingest(it).getOrThrow() }
                    }.getOrNull()
                }
            }
            if (ids.any { it == null }) {
                Toast.makeText(
                    context,
                    "Some pictures could not be read, so none were attached. Try again with other pictures.",
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                latestOnPicked.value(ids.filterNotNull())
            }
        }
    }
    return remember(launcher) {
        {
            launcher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                ),
            )
        }
    }
}
