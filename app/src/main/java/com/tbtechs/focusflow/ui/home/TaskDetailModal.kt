package com.tbtechs.focusflow.ui.home

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.theme.scaledSp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Screenshot 6d: Task Details screen / modal
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailModal(
    task: Task,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onExtend: () -> Unit,
    onStartFocus: () -> Unit,
    onEdit: () -> Unit,
) {
    val canAct = task.status !in setOf("completed", "skipped")
    val accent = runCatching {
        Color(android.graphics.Color.parseColor(task.color))
    }.getOrDefault(BrandPrimary)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RefBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
            ,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RefHeader)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    task.title,
                    modifier = Modifier.weight(1f),
                    fontSize = 22.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = RefText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = RefSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ReferenceSectionLabel("SCHEDULE")
                Text(
                    "${task.startTime.asLocalTime()} – ${task.endTime.asLocalTime()}",
                    fontSize = 15.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                    color = RefText,
                )
                Text(
                    task.startTime.asLocalDate(),
                    fontSize = 15.scaledSp,
                    color = RefSecondary,
                )

                ReferenceSectionLabel("PRIORITY")
                Text(
                    task.priority.replaceFirstChar(Char::titlecase),
                    fontSize = 15.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                    color = priorityColor(task.priority),
                )

                if (task.tags.isNotEmpty()) {
                    ReferenceSectionLabel("TAGS")
                    Text(
                        task.tags.joinToString(" ") { "#$it" },
                        fontSize = 15.scaledSp,
                        fontWeight = FontWeight.SemiBold,
                        color = RefText,
                    )
                }

                ReferenceSectionLabel("STATUS")
                Text(
                    task.status.replaceFirstChar(Char::titlecase),
                    fontSize = 15.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (task.status.lowercase()) {
                        "completed" -> RefGreen
                        "skipped" -> RefMuted
                        else -> RefText
                    },
                )

                task.description?.takeIf { it.isNotBlank() }?.let { notes ->
                    ReferenceSectionLabel("NOTES")
                    Text(
                        notes,
                        fontSize = 15.scaledSp,
                        color = RefSecondary,
                    )
                }

                if (canAct && task.focusMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BrandPrimary.copy(alpha = 0.14f))
                            .border(1.dp, BrandPrimary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .clickable(onClick = onStartFocus)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Shield, contentDescription = "Start focus", tint = BrandPrimary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Start Focus", fontSize = 15.scaledSp, fontWeight = FontWeight.SemiBold, color = RefText)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RefHeader)
                    .border(1.dp, RefBorder)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ReferenceTaskAction(Icons.Outlined.Edit, "Edit", RefBlue, onEdit)
                if (canAct) {
                    ReferenceTaskAction(Icons.Outlined.Check, "Complete", RefGreen, onComplete)
                    ReferenceTaskAction(Icons.Outlined.Close, "Skip", RefMuted, onSkip)
                    ReferenceTaskAction(Icons.Outlined.Alarm, "Extend", RefAmber, onExtend)
                }
            }
        }
    }
}

private fun String.asLocalDate(): String = runCatching {
    Instant.parse(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
}.getOrDefault(this)
