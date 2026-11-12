package com.tbtechs.focusflow.ui.home.subtask

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.images.SubtaskImageStore
import com.tbtechs.focusflow.data.model.PhotoSlotRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pictures that were stored successfully, plus a plain-language reason for each one that was not. */
private class BatchOutcome(
    val assetIds: List<String>,
    val failures: List<String>,
)

/**
 * Returns a function that opens the system photo picker for several pictures at once.
 *
 * Every picked picture is stored on its own, so one bad picture no longer cancels the rest.
 * Before anything is attached, a preview shows which picture is going to which empty slot
 * (in reading order). Pictures can be moved up or down or removed there, and any picture
 * that could not be read is listed with the reason.
 */
@Composable
internal fun rememberBatchPhotoPicker(
    slots: List<PhotoSlotRef>,
    imageStore: SubtaskImageStore?,
    onPicked: (List<String>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnPicked = rememberUpdatedState(onPicked)
    var review by remember { mutableStateOf<BatchOutcome?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = slots.size.coerceIn(2, 30),
        ),
    ) { uris ->
        val store = imageStore
        if (uris.isEmpty() || store == null) return@rememberLauncherForActivityResult
        Toast.makeText(context, "Reading pictures…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val ids = mutableListOf<String>()
                val failures = mutableListOf<String>()
                uris.forEachIndexed { index, uri ->
                    val result = runCatching {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("The selected photo could not be opened.")
                        input.use { store.ingest(it).getOrThrow() }
                    }
                    result.onSuccess { ids += it }
                    result.onFailure { failures += "Picture ${index + 1}: ${it.message ?: "could not be read"}" }
                }
                BatchOutcome(ids, failures)
            }
            if (outcome.assetIds.isEmpty()) {
                Toast.makeText(
                    context,
                    outcome.failures.firstOrNull() ?: "None of those pictures could be read.",
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                review = outcome
            }
        }
    }

    review?.let { outcome ->
        PhotoBatchReviewDialog(
            slots = slots,
            outcome = outcome,
            imageStore = imageStore,
            onConfirm = { ordered ->
                review = null
                latestOnPicked.value(ordered)
            },
            onCancel = { review = null },
        )
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

@Composable
private fun PhotoBatchReviewDialog(
    slots: List<PhotoSlotRef>,
    outcome: BatchOutcome,
    imageStore: SubtaskImageStore?,
    onConfirm: (List<String>) -> Unit,
    onCancel: () -> Unit,
) {
    val ordered = remember(outcome) {
        mutableStateListOf<String>().apply { addAll(outcome.assetIds) }
    }
    val usable = minOf(ordered.size, slots.size)

    fun swap(a: Int, b: Int) {
        if (a !in ordered.indices || b !in ordered.indices) return
        val first = ordered[a]
        ordered[a] = ordered[b]
        ordered[b] = first
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Match pictures to slots", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "Pictures fill the empty slots in this order. Move a picture up or down, or remove it, to change where it goes.",
                        fontSize = 13.sp,
                    )
                }
                if (outcome.failures.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                if (outcome.failures.size == 1) "1 picture could not be added:"
                                else "${outcome.failures.size} pictures could not be added:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFEF4444),
                            )
                            outcome.failures.forEach { Text(it, fontSize = 12.sp) }
                        }
                    }
                }
                itemsIndexed(slots) { index, slot ->
                    val assetId = ordered.getOrNull(index)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PickedThumb(imageStore, assetId)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = slot.subtaskTitle + (slot.stepTitle?.let { " · $it" } ?: ""),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (assetId != null) "Picture ${index + 1}" else "No picture",
                                fontSize = 11.sp,
                            )
                        }
                        if (assetId != null) {
                            TextButton(onClick = { swap(index, index - 1) }, enabled = index > 0) { Text("▲") }
                            TextButton(
                                onClick = { swap(index, index + 1) },
                                enabled = index < ordered.size - 1,
                            ) { Text("▼") }
                            TextButton(onClick = { ordered.removeAt(index) }) { Text("✕") }
                        }
                    }
                }
                if (ordered.size > slots.size) {
                    item {
                        val extra = ordered.size - slots.size
                        Text(
                            if (extra == 1) "1 extra picture has no empty slot and will be skipped."
                            else "$extra extra pictures have no empty slot and will be skipped.",
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(ordered.take(slots.size).toList()) },
                enabled = usable > 0,
            ) {
                Text(if (usable == 1) "Attach 1 picture" else "Attach $usable pictures")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun PickedThumb(imageStore: SubtaskImageStore?, assetId: String?) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, assetId, imageStore) {
        value = if (assetId == null || imageStore == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                val file = imageStore.file(assetId)?.takeIf { it.isFile } ?: return@withContext null
                runCatching {
                    BitmapFactory.decodeFile(
                        file.path,
                        BitmapFactory.Options().apply { inSampleSize = 4 },
                    )?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x22888888)),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
