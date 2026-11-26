package com.tbtechs.focusflow.ui.home.subtask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.R
import com.tbtechs.focusflow.data.model.PhotoSlot
import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.SubtaskImage
import com.tbtechs.focusflow.ui.theme.scaledSp

internal sealed interface SubtaskPhotoTarget {
    data object Item : SubtaskPhotoTarget
    data class Step(val index: Int) : SubtaskPhotoTarget
}

internal fun Subtask.photoAt(target: SubtaskPhotoTarget): SubtaskImage? = when (target) {
    SubtaskPhotoTarget.Item -> image
    is SubtaskPhotoTarget.Step -> steps.getOrNull(target.index)?.image
}

internal fun Subtask.slotAt(target: SubtaskPhotoTarget): PhotoSlot? = when (target) {
    SubtaskPhotoTarget.Item -> photo
    is SubtaskPhotoTarget.Step -> steps.getOrNull(target.index)?.photo
}

@Composable
internal fun PhotoSlot.neededText(): String =
    stringResource(R.string.photo_mgmt_needed, brief ?: alt ?: stringResource(R.string.photo_mgmt_see_item))

internal fun Subtask.withPhoto(
    target: SubtaskPhotoTarget,
    image: SubtaskImage?,
): Subtask = when (target) {
    SubtaskPhotoTarget.Item -> copy(image = image)
    is SubtaskPhotoTarget.Step -> {
        if (target.index !in steps.indices) {
            this
        } else {
            copy(
                steps = steps.mapIndexed { index, step ->
                    if (index == target.index) step.copy(image = image) else step
                },
            )
        }
    }
}

@Composable
internal fun SubtaskPhotoManagementDialog(
    subtask: Subtask,
    busy: Boolean,
    onChoosePhoto: (SubtaskPhotoTarget) -> Unit,
    onRemovePhoto: (SubtaskPhotoTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.photo_mgmt_title), fontSize = 20.scaledSp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.photo_mgmt_intro),
                    fontSize = 13.scaledSp,
                )
                PhotoTargetRow(
                    label = stringResource(R.string.photo_mgmt_item_photo),
                    target = SubtaskPhotoTarget.Item,
                    hasPhoto = subtask.image != null,
                    neededText = subtask.photo?.neededText(),
                    busy = busy,
                    onChoosePhoto = onChoosePhoto,
                    onRemovePhoto = onRemovePhoto,
                )
                subtask.steps.forEachIndexed { index, step ->
                    HorizontalDivider()
                    PhotoTargetRow(
                        label = step.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.photo_mgmt_step_n, index + 1),
                        target = SubtaskPhotoTarget.Step(index),
                        hasPhoto = step.image != null,
                        neededText = step.photo?.neededText(),
                        busy = busy,
                        onChoosePhoto = onChoosePhoto,
                        onRemovePhoto = onRemovePhoto,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.photo_mgmt_done), fontSize = 14.scaledSp)
            }
        },
    )
}

@Composable
private fun PhotoTargetRow(
    label: String,
    target: SubtaskPhotoTarget,
    hasPhoto: Boolean,
    neededText: String?,
    busy: Boolean,
    onChoosePhoto: (SubtaskPhotoTarget) -> Unit,
    onRemovePhoto: (SubtaskPhotoTarget) -> Unit,
) {
    val replaceDescription = stringResource(R.string.photo_mgmt_replace_for, label)
    val addDescription = stringResource(R.string.photo_mgmt_add_for, label)
    val removeDescription = stringResource(R.string.photo_mgmt_remove_for, label)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, fontSize = 14.scaledSp, fontWeight = FontWeight.SemiBold)
        Text(
            text = if (hasPhoto) stringResource(R.string.photo_mgmt_attached) else neededText ?: stringResource(R.string.photo_mgmt_none),
            fontSize = 12.scaledSp,
        )
        Row {
            TextButton(
                onClick = { onChoosePhoto(target) },
                enabled = !busy,
                modifier = Modifier.semantics {
                    contentDescription = if (hasPhoto) replaceDescription else addDescription
                },
            ) {
                Text(stringResource(if (hasPhoto) R.string.photo_mgmt_replace else R.string.photo_mgmt_add), fontSize = 13.scaledSp)
            }
            if (hasPhoto) {
                TextButton(
                    onClick = { onRemovePhoto(target) },
                    enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = removeDescription },
                ) {
                    Text(stringResource(R.string.photo_mgmt_remove), fontSize = 13.scaledSp)
                }
            }
        }
    }
}
