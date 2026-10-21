package com.tbtechs.focusflow.ui.home.subtask

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.hapticfeedback.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.images.SubtaskImageStore
import com.tbtechs.focusflow.data.model.GoalFormatter
import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.common.SubtaskImageView
import com.tbtechs.focusflow.ui.home.RefBorder
import com.tbtechs.focusflow.ui.home.RefCard
import com.tbtechs.focusflow.ui.home.RefMuted
import com.tbtechs.focusflow.ui.home.RefSecondary
import com.tbtechs.focusflow.ui.home.RefText
import com.tbtechs.focusflow.ui.home.FocusFlowInternalCard
import com.tbtechs.focusflow.ui.home.FocusFlowPrimaryButton
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.scaledSp

@Composable
internal fun SubtaskProgressSection(
    task: Task,
    accent: Color,
    imageStore: SubtaskImageStore?,
    animationsEnabled: Boolean,
    onOpen: (String) -> Unit,
    onToggle: (Subtask, Boolean) -> Unit,
    onReset: () -> Unit,
    onCompleteTask: () -> Unit,
) {
    var menuExpanded by remember(task.id) { mutableStateOf(false) }
    var confirmReset by remember(task.id) { mutableStateOf(false) }
    val completedCount = task.subtasks.count(Subtask::done)
    val allDone = task.subtasks.isNotEmpty() && completedCount == task.subtasks.size
    val canAct = task.status !in setOf("completed", "skipped")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$completedCount of ${task.subtasks.size} done",
                modifier = Modifier.weight(1f),
                color = RefText,
                fontSize = 15.scaledSp,
                fontWeight = FontWeight.SemiBold,
            )
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Subtask options",
                        tint = RefSecondary,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Reset progress", fontSize = 14.scaledSp) },
                        onClick = {
                            menuExpanded = false
                            confirmReset = true
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .semantics(mergeDescendants = true) {
                    stateDescription = "$completedCount of ${task.subtasks.size} subtasks done"
                },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            task.subtasks.forEach { subtask ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .align(Alignment.CenterVertically)
                        .clip(CircleShape)
                        .background(
                            if (subtask.done) accent
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        ),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            task.subtasks.forEachIndexed { index, subtask ->
                SubtaskListRow(
                    subtask = subtask,
                    index = index + 1,
                    accent = accent,
                    imageStore = imageStore,
                    animationsEnabled = animationsEnabled,
                    onOpen = { onOpen(subtask.id) },
                    onToggle = { done -> onToggle(subtask, done) },
                )
            }
        }

        if (allDone && canAct) {
            AllSubtasksDoneBanner(onComplete = onCompleteTask)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset subtask progress", fontWeight = FontWeight.Bold) },
            text = { Text("This will clear all completed subtasks and set progress.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onReset()
                    },
                ) { Text("Reset", color = BrandPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Cancel", color = RefSecondary)
                }
            },
        )
    }
}

@Composable
private fun SubtaskListRow(
    subtask: Subtask,
    index: Int,
    accent: Color,
    imageStore: SubtaskImageStore?,
    animationsEnabled: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RefCard)
            .border(1.dp, RefBorder.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SubtaskCheckControl(
            subtask = subtask,
            accent = accent,
            animationsEnabled = animationsEnabled,
            onToggle = onToggle,
        )
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (subtask.image != null) {
                SubtaskImageView(
                    assetId = subtask.image.asset,
                    imageStore = imageStore,
                    contentDescription = subtask.image.alt?.takeIf(String::isNotBlank) ?: subtask.title,
                    targetWidthPx = with(density) { 56.dp.roundToPx() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = index.toString(),
                        color = accent,
                        fontSize = 18.scaledSp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = subtask.title,
                color = if (subtask.done) RefMuted else RefText,
                fontSize = 14.scaledSp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (subtask.done) TextDecoration.LineThrough else TextDecoration.None,
            )
            subtask.subtitle?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    color = RefSecondary,
                    fontSize = 11.scaledSp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GoalSummary(subtask.goal, subtask.setsDone, subtask.done)
        }
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = RefMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SubtaskCheckControl(
    subtask: Subtask,
    accent: Color,
    animationsEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (subtask.done) 1f else 0.88f,
        animationSpec = if (animationsEnabled) spring(stiffness = 500f) else tween(0),
        label = "subtask-check-scale",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (subtask.done) accent else Color.Transparent)
            .border(
                width = 2.dp,
                color = if (subtask.done) accent else RefBorder,
                shape = CircleShape,
            )
            .toggleable(
                value = subtask.done,
                role = Role.Checkbox,
                onValueChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(it)
                },
            )
            .semantics {
                stateDescription = if (subtask.done) "Completed" else "Not completed"
            },
        contentAlignment = Alignment.Center,
    ) {
        if (subtask.done) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = contentColorForAccent(accent),
                modifier = Modifier.size(22.dp).scale(scale),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalSummary(goal: com.tbtechs.focusflow.data.model.SubtaskGoal?, setsDone: Int, done: Boolean) {
    val formatted = goal?.let(GoalFormatter::format) ?: return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        GoalChip(text = formatted.primary)
        formatted.details.forEach { GoalChip(it) }
        if (goal.kind == "sets" && goal.sets != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(goal.sets) { index ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (done || index < setsDone) BrandPrimary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalChip(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        color = RefSecondary,
        fontSize = 10.scaledSp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
internal fun AllSubtasksDoneBanner(onComplete: () -> Unit) {
    FocusFlowInternalCard(
        radius = 14.dp,
        contentPadding = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "All done — complete this task?",
            color = RefText,
            fontSize = 14.scaledSp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        FocusFlowPrimaryButton(
            text = "Complete task",
            onClick = onComplete,
            color = BrandPrimary,
        )
    }
}

@Composable
private fun contentColorForAccent(accent: Color): Color =
    if (accent.luminance() > 0.52f) Color.Black else Color.White
