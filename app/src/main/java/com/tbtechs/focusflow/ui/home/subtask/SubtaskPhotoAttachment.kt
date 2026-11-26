package com.tbtechs.focusflow.ui.home.subtask

import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
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
import com.tbtechs.focusflow.data.model.SubtaskLimits
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
                    val store = requireNotNull(imageStore) { context.getString(R.string.photo_attach_storage_unavailable) }
                    val assetId = withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error(context.getString(R.string.photo_batch_cannot_open))
                        input.use { store.ingest(it).getOrThrow() }
                    }
                    val currentSubtask = task.subtasks.firstOrNull { it.id == subtask.id }
                        ?: error(context.getString(R.string.photo_attach_subtask_gone))
                    val previousImage = currentSubtask.photoAt(target)
                    val defaultAlt = when (target) {
                        SubtaskPhotoTarget.Item -> context.getString(R.string.photo_attach_alt_item)
                        is SubtaskPhotoTarget.Step -> context.getString(R.string.photo_attach_alt_step, target.index + 1)
                    }
                    val slot = currentSubtask.slotAt(target)
                    val attachedImage = SubtaskImage(
                        asset = assetId,
                        alt = previousImage?.alt
                            ?: (slot?.alt ?: slot?.brief)?.take(SubtaskLimits.MAX_ALT_CHARS)
                            ?: defaultAlt,
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
                        ?: context.getString(R.string.photo_attach_failed)
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
            title = { Text(stringResource(R.string.home_replace_photo), fontSize = 20.scaledSp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.photo_attach_replace_body, label),
                    fontSize = 14.scaledSp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        replacementTarget = null
                        launchPicker(target)
                    },
                ) { Text(stringResource(R.string.home_choose_photo), fontSize = 14.scaledSp) }
            },
            dismissButton = {
                TextButton(onClick = { replacementTarget = null }) {
                    Text(stringResource(R.string.home_cancel), fontSize = 14.scaledSp)
                }
            },
        )
    }

    removalTarget?.let { target ->
        val label = target.labelFor(subtask)
        AlertDialog(
            onDismissRequest = { removalTarget = null },
            title = { Text(stringResource(R.string.home_remove_photo), fontSize = 20.scaledSp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.photo_attach_remove_body, label),
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
                ) { Text(stringResource(R.string.home_remove), fontSize = 14.scaledSp) }
            },
            dismissButton = {
                TextButton(onClick = { removalTarget = null }) {
                    Text(stringResource(R.string.home_cancel), fontSize = 14.scaledSp)
                }
            },
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(stringResource(R.string.home_photo_not_attached), fontSize = 20.scaledSp, fontWeight = FontWeight.Bold) },
            text = { Text(message, fontSize = 14.scaledSp) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text(stringResource(R.string.home_ok), fontSize = 14.scaledSp)
                }
            },
        )
    }
}

@Composable
private fun SubtaskPhotoTarget.labelFor(subtask: Subtask): String = when (this) {
    SubtaskPhotoTarget.Item -> stringResource(R.string.photo_attach_target_item)
    is SubtaskPhotoTarget.Step -> subtask.steps.getOrNull(index)?.title
        ?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.photo_attach_target_step, index + 1)
}
