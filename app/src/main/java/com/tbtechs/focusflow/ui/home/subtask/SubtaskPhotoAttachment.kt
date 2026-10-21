package com.tbtechs.focusflow.ui.home.subtask

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.tbtechs.focusflow.data.images.SubtaskImageStore
import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.SubtaskImage
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SubtaskPhotoAttachment(
    task: Task,
    subtask: Subtask,
    imageStore: SubtaskImageStore?,
    visible: Boolean,
    onReplaceSubtasks: (List<Subtask>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingTarget by remember(task.id, subtask.id) {
        mutableStateOf<SubtaskPhotoTarget?>(null)
    }
    var replacementTarget by remember(task.id, subtask.id) {
        mutableStateOf<SubtaskPhotoTarget?>(null)
    }
    var removalTarget by remember(task.id, subtask.id) {
        mutableStateOf<SubtaskPhotoTarget?>(null)
    }
    var busy by remember(task.id, subtask.id) { mutableStateOf(false) }
    var errorMessage by remember(task.id, subtask.id) { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val target = pendingTarget
        pendingTarget = null
        if (uri != null && target != null) {
            scope.launch {
                busy = true
                try {
                    val store = requireNotNull(imageStore) { "Image storage is unavailable." }
                    val assetId = withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("The selected photo could not be opened.")
                        input.use { store.ingest(it).getOrThrow() }
                    }
                    val currentSubtask = task.subtasks.firstOrNull { it.id == subtask.id }
                        ?: error("This subtask is no longer available.")
                    val previousImage = currentSubtask.photoAt(target)
                    val defaultAlt = when (target) {
                        SubtaskPhotoTarget.Item -> "Attached item photo"
                        is SubtaskPhotoTarget.Step -> "Step ${target.index + 1} photo"
                    }
                    val attachedImage = SubtaskImage(
                        asset = assetId,
                        alt = previousImage?.alt ?: defaultAlt,
                    )
                    onReplaceSubtasks(
                        task.subtasks.map { item ->
                            if (item.id == currentSubtask.id) {
                                item.withPhoto(target, attachedImage)
                            } else {
                                item
                            }
                        },
                    )
                    onDismiss()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    errorMessage = exception.message?.take(300)
                        ?: "The photo could not be attached. Try another image."
                } finally {
                    busy = false
                }
            }
        }
    }

    fun launchPicker(target: SubtaskPhotoTarget) {
        pendingTarget = target
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    if (visible) {
        SubtaskPhotoManagementDialog(
            subtask = subtask,
            busy = busy,
            onChoosePhoto = { target ->
                if (subtask.photoAt(target) == null) {
                    launchPicker(target)
                } else {
                    onDismiss()
                    replacementTarget = target
                }
            },
            onRemovePhoto = {
                onDismiss()
                removalTarget = it
            },
            onDismiss = onDismiss,
        )
    }

    replacementTarget?.let { target ->
        val label = target.labelFor(subtask)
        AlertDialog(
            onDismissRequest = { replacementTarget = null },
            title = { Text("Replace photo?", fontSize = 20.scaledSp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Choose a new photo to replace the existing photo for $label.",
                    fontSize = 14.scaledSp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        replacementTarget = null
                        launchPicker(target)
                    },
                ) { Text("Choose photo", fontSize = 14.scaledSp) }
            },
            dismissButton = {
                TextButton(onClick = { replacementTarget = null }) {
                    Text("Cancel", fontSize = 14.scaledSp)
                }
            },
        )
    }

    removalTarget?.let { target ->
        val label = target.labelFor(subtask)
        AlertDialog(
            onDismissRequest = { removalTarget = null },
            title = { Text("Remove photo?", fontSize = 20.scaledSp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Remove the photo from $label? This does not change its text or progress.",
                    fontSize = 14.scaledSp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReplaceSubtasks(
                            task.subtasks.map { item ->
                                if (item.id == subtask.id) item.withPhoto(target, null) else item
                            },
                        )
                        removalTarget = null
                        onDismiss()
                    },
                ) { Text("Remove", fontSize = 14.scaledSp) }
            },
            dismissButton = {
                TextButton(onClick = { removalTarget = null }) {
                    Text("Cancel", fontSize = 14.scaledSp)
                }
            },
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Photo not attached", fontSize = 20.scaledSp, fontWeight = FontWeight.Bold) },
            text = { Text(message, fontSize = 14.scaledSp) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK", fontSize = 14.scaledSp)
                }
            },
        )
    }
}

private fun SubtaskPhotoTarget.labelFor(subtask: Subtask): String = when (this) {
    SubtaskPhotoTarget.Item -> "this item"
    is SubtaskPhotoTarget.Step -> subtask.steps.getOrNull(index)?.title
        ?.takeIf(String::isNotBlank)
        ?: "step ${index + 1}"
}
