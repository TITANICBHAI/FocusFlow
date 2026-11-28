package com.tbtechs.focusflow.ui.stats

import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
import androidx.compose.animation.AnimatedVisibility
import com.tbtechs.focusflow.ui.theme.scaledSp
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.local.entity.DayRatingEntity
import com.tbtechs.focusflow.data.repository.DayRatingRepository.RatableDateEntry
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class ChipType { WORD, APP }

data class SuggestedChip(
    val type: ChipType,
    val label: String,
    val packageName: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayRatingBar(
    selectedDate: String,
    currentRating: DayRatingEntity?,
    ratableDates: List<RatableDateEntry>,
    suggestedChips: List<SuggestedChip>,
    onSelectDate: (String) -> Unit,
    onLoadChips: (String) -> Unit,
    requestFocusNonce: Int = 0,
    onFocusRequestHandled: (Int) -> Unit = {},
    onSubmit: suspend (
        date: String,
        rating: Int,
        contextTag: String?,
        note: String?,
        appTags: List<String>,
        wordTags: List<String>,
    ) -> Boolean,
) {
    var dateMenuOpen by remember { mutableStateOf(false) }
    var tappedRating by remember(selectedDate) { mutableStateOf(currentRating?.rating) }
    var contextTag by remember(selectedDate) { mutableStateOf(currentRating?.contextTag) }
    var noteText by remember(selectedDate) { mutableStateOf(currentRating?.note.orEmpty()) }
    var editorDirty by remember(selectedDate) { mutableStateOf(false) }
    var isSaving by remember(selectedDate) { mutableStateOf(false) }
    var isEditing by remember(selectedDate) { mutableStateOf(currentRating == null) }
    var saveFailed by remember(selectedDate) { mutableStateOf(false) }
    // Resource id of the message under the rating, or null when there is none.
    var saveMessageRes by remember(selectedDate) {
        mutableStateOf<Int?>(if (currentRating != null) R.string.stats_saved_on_device else null)
    }
    var pendingDate by remember { mutableStateOf<String?>(null) }
    val wordTags = remember(selectedDate) {
        mutableStateListOf<String>().apply {
            addAll(decodeStorageList(currentRating?.wordTags))
        }
    }
    val appTags = remember(selectedDate) {
        mutableStateListOf<String>().apply {
            addAll(decodeStorageList(currentRating?.appTags))
        }
    }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    fun markDirty() {
        editorDirty = true
        isEditing = true
        saveFailed = false
        saveMessageRes = null
    }

    fun save() {
        val rating = tappedRating ?: return
        if (!editorDirty || isSaving) return
        val date = selectedDate
        scope.launch {
            isSaving = true
            val saved = try {
                onSubmit(
                    date,
                    rating,
                    contextTag,
                    noteText.ifBlank { null },
                    appTags.toList(),
                    wordTags.toList(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            isSaving = false
            if (saved) {
                editorDirty = false
                isEditing = false
                saveFailed = false
                saveMessageRes = R.string.stats_saved_on_device
            } else {
                saveFailed = true
                saveMessageRes = R.string.stats_save_failed_storage
            }
        }
    }

    LaunchedEffect(currentRating, selectedDate) {
        if (editorDirty) return@LaunchedEffect
        tappedRating = currentRating?.rating
        isEditing = currentRating == null
        contextTag = currentRating?.contextTag
        noteText = currentRating?.note.orEmpty()
        wordTags.clear()
        wordTags.addAll(decodeStorageList(currentRating?.wordTags))
        appTags.clear()
        appTags.addAll(decodeStorageList(currentRating?.appTags))
        saveFailed = false
        saveMessageRes = if (currentRating != null) R.string.stats_saved_on_device else null
    }
    LaunchedEffect(selectedDate) {
        onLoadChips(selectedDate)
    }
    LaunchedEffect(requestFocusNonce, selectedDate, currentRating, isEditing) {
        if (requestFocusNonce <= 0) return@LaunchedEffect
        if (
            shouldRequestDayRatingInputFocus(
                requestNonce = requestFocusNonce,
                isEditing = isEditing,
                hasSavedRating = currentRating != null,
            )
        ) {
            withFrameNanos { }
            try {
                focusRequester.requestFocus()
            } catch (error: IllegalStateException) {
                android.util.Log.w(
                    "DayRatingBar",
                    "Rating field was not available for focus",
                    error,
                )
            }
        }
        onFocusRequestHandled(requestFocusNonce)
    }

    StatsCard(
        modifier = Modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box {
                    TextButton(onClick = { dateMenuOpen = true }, enabled = !isSaving) {
                        Text(
                            "${friendlyDate(selectedDate)} ▾",
                            fontSize = 14.scaledSp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandPrimary,
                        )
                    }
                    DropdownMenu(
                        expanded = dateMenuOpen,
                        onDismissRequest = { dateMenuOpen = false },
                    ) {
                        if (ratableDates.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.stats_no_other_days_yet), fontSize = 13.scaledSp) },
                                onClick = { dateMenuOpen = false },
                            )
                        } else {
                            ratableDates.forEach { entry ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            friendlyDate(entry.date) +
                                                if (entry.hasRating) "  ●" else "",
                                            fontSize = 13.scaledSp,
                                            color = if (entry.hasRating) BrandPrimary
                                            else DarkTextPrimary,
                                        )
                                    },
                                    onClick = {
                                        if (editorDirty) {
                                            pendingDate = entry.date
                                        } else {
                                            onSelectDate(entry.date)
                                        }
                                        dateMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
                Text(
                    text = tappedRating?.let { "$it/10" } ?: stringResource(R.string.stats_how_was_it),
                    fontSize = 13.scaledSp,
                    color = DarkTextSecondary,
                )
            }

            if (!isEditing && currentRating != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(saveMessageRes ?: R.string.stats_saved_on_device),
                        fontSize = 12.scaledSp,
                        color = DarkTextSecondary,
                    )
                    TextButton(onClick = { isEditing = true }) {
                        Text(stringResource(R.string.stats_edit_rating))
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .focusRequester(focusRequester)
                        .focusable(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    (1..10).forEach { number ->
                        val selected = tappedRating == number
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) BrandPrimary else DarkSurfaceVariant)
                                .clickable {
                                    markDirty()
                                    tappedRating = number
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "$number",
                                fontSize = 13.scaledSp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) Color.White else DarkTextSecondary,
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = tappedRating != null,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.stats_any_context_optional), fontSize = 12.scaledSp, color = DarkTextSecondary)
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                stringResource(R.string.stats_ctx_rest_day) to "rest_day",
                                stringResource(R.string.stats_ctx_sick) to "sick",
                                stringResource(R.string.stats_ctx_travel) to "travel",
                                stringResource(R.string.stats_ctx_holiday) to "holiday",
                                stringResource(R.string.stats_ctx_off_schedule) to "off_schedule",
                            ).forEach { (label, tag) ->
                                val selected = contextTag == tag
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (selected) BrandPrimary else DarkSurfaceVariant)
                                        .clickable {
                                            markDirty()
                                            contextTag = if (selected) null else tag
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    Text(
                                        label,
                                        fontSize = 12.scaledSp,
                                        color = if (selected) Color.White else DarkTextSecondary,
                                    )
                                }
                            }
                        }

                        if (suggestedChips.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                suggestedChips.forEach { chip ->
                                    val selected = when (chip.type) {
                                        ChipType.WORD -> chip.label in wordTags
                                        ChipType.APP -> chip.packageName in appTags
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (selected) BrandPrimary else DarkSurfaceVariant)
                                            .border(
                                                1.dp,
                                                if (selected) BrandPrimary else DarkBorder,
                                                RoundedCornerShape(20.dp),
                                            )
                                            .clickable {
                                                markDirty()
                                                when (chip.type) {
                                                    ChipType.WORD -> if (selected) {
                                                        wordTags.remove(chip.label)
                                                    } else {
                                                        wordTags.add(chip.label)
                                                    }
                                                    ChipType.APP -> chip.packageName?.let { packageName ->
                                                        if (selected) appTags.remove(packageName)
                                                        else appTags.add(packageName)
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                    ) {
                                        Text(
                                            chip.label,
                                            fontSize = 12.scaledSp,
                                            color = if (selected) Color.White else DarkTextSecondary,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = {
                                if (it.length <= 200) {
                                    markDirty()
                                    noteText = it
                                }
                            },
                            placeholder = {
                                Text(
                                    stringResource(R.string.stats_anything_else_optional),
                                    fontSize = 13.scaledSp,
                                    color = DarkTextSecondary,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandPrimary,
                                unfocusedBorderColor = DarkBorder,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() },
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = ::save,
                            enabled = editorDirty && !isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when {
                                    isSaving -> stringResource(R.string.stats_saving)
                                    !editorDirty -> stringResource(R.string.stats_saved)
                                    else -> stringResource(R.string.stats_save_rating)
                                },
                            )
                        }
                        if (currentRating != null && !editorDirty && !isSaving) {
                            TextButton(
                                onClick = { isEditing = false },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.photo_mgmt_done))
                            }
                        }
                        saveMessageRes?.let { messageRes ->
                            Text(
                                stringResource(messageRes),
                                fontSize = 12.scaledSp,
                                color = if (saveFailed) MaterialTheme.colorScheme.error
                                else DarkTextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDate?.let { targetDate ->
        AlertDialog(
            onDismissRequest = { pendingDate = null },
            title = { Text(stringResource(R.string.stats_discard_unsaved_rating)) },
            text = { Text(stringResource(R.string.stats_your_rating_context_tags_and)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDate = null
                        onSelectDate(targetDate)
                    },
                ) {
                    Text(stringResource(R.string.stats_discard_change_date))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDate = null }) {
                    Text(stringResource(R.string.stats_keep_editing))
                }
            },
        )
    }
}

@Composable
private fun friendlyDate(dateString: String): String {
    val today = LocalDate.now()
    val date = runCatching { LocalDate.parse(dateString) }.getOrNull() ?: return dateString
    return when (date) {
        today -> stringResource(R.string.stats_window_today)
        today.minusDays(1) -> stringResource(R.string.stats_window_yesterday)
        else -> "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${date.dayOfMonth}"
    }
}

private fun decodeStorageList(value: String?): List<String> {
    if (value.isNullOrBlank()) return emptyList()
    return runCatching {
        kotlinx.serialization.json.Json.decodeFromString<List<String>>(value)
    }.getOrElse {
        value.removePrefix("[").removeSuffix("]")
            .split(",")
            .map { it.trim().trim('"') }
            .filter(String::isNotBlank)
    }
}