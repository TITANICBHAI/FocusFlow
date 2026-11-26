package com.tbtechs.focusflow.ui.home

import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.theme.scaledSp
import androidx.compose.ui.unit.Dp
import com.tbtechs.focusflow.data.backup.BackupJsonLimits
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.InfoBorder
import com.tbtechs.focusflow.ui.theme.InfoSurface
import com.tbtechs.focusflow.ui.theme.InfoText

/**
 * Screenshot 6c: ActiveTaskBanner for running or awaiting-decision tasks
 */
@Composable
internal fun ActiveTaskBanner(
    task: Task,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onExtend: () -> Unit,
    onSkip: () -> Unit,
    onStartFocus: () -> Unit,
) {
    val isRunning = task.isRunningNow()
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val bannerShape = RoundedCornerShape(22.dp)
    val bannerColor = when {
        isRunning && isLightTheme -> com.tbtechs.focusflow.ui.theme.BrandPrimaryHover
        isRunning -> BrandPrimary
        else -> RefAmber
    }
    val foregroundColor = if (!isRunning && isLightTheme) Color(0xFF422006) else Color.White
    val actionOverlay = foregroundColor.copy(alpha = if (!isRunning && isLightTheme) 0.12f else 0.18f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(bannerShape)
            .background(bannerColor)
            .border(1.dp, foregroundColor.copy(alpha = 0.24f), bannerShape)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(actionOverlay)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        stringResource(if (isRunning) R.string.home_badge_now else R.string.home_badge_times_up),
                        fontSize = 10.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = foregroundColor,
                        letterSpacing = 0.8.scaledSp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(foregroundColor),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        task.title,
                        modifier = Modifier.weight(1f),
                        fontSize = 15.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = foregroundColor,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Text(
                    if (isRunning) stringResource(R.string.home_until_time, task.endTime.asLocalTime())
                    else stringResource(R.string.home_ended_pick_one, task.endTime.asLocalTime()),
                    fontSize = 12.scaledSp,
                    color = foregroundColor.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onComplete,
                    modifier = Modifier.size(30.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(actionOverlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = stringResource(R.string.home_complete),
                            tint = foregroundColor,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                IconButton(
                    onClick = onExtend,
                    modifier = Modifier.size(30.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(actionOverlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.home_extend),
                            tint = foregroundColor,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                if (!isRunning) {
                    IconButton(
                        onClick = onSkip,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(actionOverlay),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.home_skip_2),
                                tint = foregroundColor,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                } else if (task.focusMode) {
                    IconButton(
                        onClick = onStartFocus,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(actionOverlay),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Shield,
                                contentDescription = stringResource(R.string.home_start_focus),
                                tint = foregroundColor,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskTagsEditor(
    tags: List<String>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onTagsChange: (List<String>) -> Unit,
) {
    var tagError by remember(tags) { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    fun addDraftTags() {
        try {
            onTagsChange(addTaskTagsFromDraft(tags, draft))
            onDraftChange("")
            tagError = null
        } catch (error: IllegalArgumentException) {
            tagError = when {
                error is TagLimitException && error.kind == TagLimitException.Kind.TAG_TOO_LONG ->
                    context.getString(R.string.home_error_tag_too_long, error.limit)
                error is TagLimitException ->
                    context.getString(R.string.home_error_too_many_tags, error.limit)
                else -> error.message ?: context.getString(R.string.home_error_tag_invalid)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReferenceSectionLabel(stringResource(R.string.home_tags))
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    TaskTagChip(
                        tag = tag,
                        fontSizeSp = 13,
                        onRemove = { onTagsChange(tags.filterNot { it == tag }) },
                    )
                }
            }
        }
        HomeTextField(
            value = draft,
            onValueChange = {
                onDraftChange(it)
                tagError = null
            },
            label = stringResource(R.string.home_add_a_tag),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addDraftTags() }),
        )
        Text(
            tagError ?: stringResource(R.string.home_tags_hint),
            fontSize = 12.scaledSp,
            color = if (tagError == null) RefSecondary else MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Thrown when a tag breaks a limit. The English [message] is for logs and tests; the screen shows
 * the same sentence from string resources in the person's language.
 */
internal class TagLimitException(
    val kind: Kind,
    val limit: Int,
    message: String,
) : IllegalArgumentException(message) {
    enum class Kind { TAG_TOO_LONG, TOO_MANY_TAGS }
}

internal fun addTaskTagsFromDraft(tags: List<String>, draft: String): List<String> {
    val candidates = draft
        .split(',', '\n')
        .map { it.trim().removePrefix("#").trim() }
        .filter(String::isNotBlank)
    val updated = tags.toMutableList()
    candidates.forEach { candidate ->
        if (updated.none { it.equals(candidate, ignoreCase = true) }) {
            if (candidate.length > BackupJsonLimits.MAX_TAG_CHARS) {
                throw TagLimitException(
                    TagLimitException.Kind.TAG_TOO_LONG,
                    BackupJsonLimits.MAX_TAG_CHARS,
                    "Tags must be ${BackupJsonLimits.MAX_TAG_CHARS} characters or fewer.",
                )
            }
            if (updated.size >= BackupJsonLimits.MAX_TAGS_PER_TASK) {
                throw TagLimitException(
                    TagLimitException.Kind.TOO_MANY_TAGS,
                    BackupJsonLimits.MAX_TAGS_PER_TASK,
                    "A task can have at most ${BackupJsonLimits.MAX_TAGS_PER_TASK} tags.",
                )
            }
            updated += candidate
        }
    }
    return updated
}

@Composable
internal fun TaskTagChip(
    tag: String,
    fontSizeSp: Int = 11,
    onRemove: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    val removeLabel = stringResource(R.string.home_remove_tag, tag)
    val trailingSurface = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        Color(0xFFC7D2FE)
    } else {
        Color(0xFF3D4380)
    }
    Row(
        modifier = Modifier
            .clip(shape)
            .background(InfoSurface)
            .border(1.dp, InfoBorder, shape)
            .then(
                if (onRemove == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        onClickLabel = removeLabel,
                        onClick = onRemove,
                    )
                },
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#$tag",
            fontSize = fontSizeSp.scaledSp,
            color = InfoText,
            maxLines = 1,
        )
        if (onRemove != null) {
            Spacer(Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(trailingSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    tint = InfoText,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

@Composable
internal fun HomeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    multilineMinHeight: Dp = 160.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    ReferenceField(
        value = value,
        onValueChange = onValueChange,
        placeholder = label,
        singleLine = singleLine,
        minHeight = if (singleLine) null else multilineMinHeight,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

@Composable
internal fun ExtendTaskDialog(task: Task, onDismiss: () -> Unit, onExtend: (Int) -> Unit) {
    var minutes by remember { mutableStateOf("15") }
    val presetOptions = listOf(10, 15, 25, 30, 45, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = DarkCard,
        titleContentColor = DarkTextPrimary,
        textContentColor = DarkTextSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Alarm, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_extend_2, task.title), fontWeight = FontWeight.Bold, fontSize = 17.scaledSp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.home_extend_hint),
                    fontSize = 13.scaledSp,
                    color = DarkTextSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presetOptions.take(4).forEach { opt ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.small)
                                .background(if (minutes == opt.toString()) BrandPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                .border(1.dp, if (minutes == opt.toString()) BrandPrimary else DarkBorder, MaterialTheme.shapes.small)
                                .clickable { minutes = opt.toString() }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "+${opt}m",
                                fontSize = 13.scaledSp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (minutes == opt.toString()) BrandPrimary else DarkTextPrimary,
                            )
                        }
                    }
                }
                HomeTextField(value = minutes, onValueChange = { minutes = it }, label = stringResource(R.string.home_custom_minutes))
            }
        },
        confirmButton = {
            Button(
                onClick = { minutes.toIntOrNull()?.takeIf { it > 0 }?.let(onExtend) },
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
            ) {
                Text(stringResource(R.string.home_extend))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
            ) {
                Text(stringResource(R.string.home_cancel), color = DarkTextSecondary)
            }
        },
    )
}
