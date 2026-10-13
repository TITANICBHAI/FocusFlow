package com.tbtechs.focusflow.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.ui.home.FocusFlowInternalCard
import com.tbtechs.focusflow.ui.home.RefRed
import com.tbtechs.focusflow.ui.home.RefSecondary
import com.tbtechs.focusflow.ui.home.RefText

@Composable
fun ErrorAlertBanner(
    onViewLogs: () -> Unit,
) {
    var latest by remember { mutableStateOf<AppErrorEvent?>(null) }
    var errorCount by remember { mutableStateOf(0) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AppErrorEvents.events.collect { event ->
            latest = event
            errorCount += 1
            dismissed = false
        }
    }

    val visible = latest != null && !dismissed
    BackHandler(enabled = visible) {
        dismissed = true
        errorCount = 0
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        latest?.let { event ->
            FocusFlowInternalCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                radius = 14.dp,
                contentPadding = 12.dp,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(RefRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = RefRed, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = if (errorCount == 1) {
                            "${event.tag}: ${event.message}"
                        } else {
                            "$errorCount errors detected · ${event.message}"
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = RefText,
                    )
                    TextButton(onClick = {
                        dismissed = true
                        errorCount = 0
                        onViewLogs()
                    }) {
                        Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("View logs", fontSize = 12.sp, color = RefSecondary)
                    }
                    IconButton(onClick = { dismissed = true; errorCount = 0 }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Dismiss")
                    }
                }
            }
        }
    }
}