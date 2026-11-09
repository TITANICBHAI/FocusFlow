package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Arrangement
import com.tbtechs.focusflow.ui.theme.scaledSp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tbtechs.focusflow.notifications.HabitNudgeScheduler
import com.tbtechs.focusflow.ui.permissions.PermissionId
import com.tbtechs.focusflow.ui.permissions.PermissionStatus
import com.tbtechs.focusflow.ui.permissions.checkPermission
import com.tbtechs.focusflow.ui.permissions.openPermissionSettings
import kotlinx.coroutines.launch

internal data class ReadinessItem(
    val id: PermissionId,
    val title: String,
    val why: String,
    val actionLabel: String,
)

/**
 * What the habit analysis and the "Block now" buttons need and do not have yet.
 * Usage Access feeds the analysis; Accessibility and Appear on top make a block work;
 * Notifications only matter once heads-ups are switched on.
 */
internal fun readinessItems(missing: Set<PermissionId>, nudgesOn: Boolean): List<ReadinessItem> =
    buildList {
        if (PermissionId.USAGE in missing) {
            add(
                ReadinessItem(
                    PermissionId.USAGE,
                    "Usage Access is off",
                    "Without it FocusFlow cannot see which apps you use, so there is nothing to analyse.",
                    "Open Usage Access",
                ),
            )
        }
        if (PermissionId.ACCESSIBILITY in missing) {
            add(
                ReadinessItem(
                    PermissionId.ACCESSIBILITY,
                    "Accessibility is off",
                    "Blocking runs on it. \"Block now\" and every block will not work until it is on.",
                    "Open Accessibility",
                ),
            )
        }
        if (PermissionId.OVERLAY in missing) {
            add(
                ReadinessItem(
                    PermissionId.OVERLAY,
                    "Appear on top is off",
                    "The block screen needs it to cover the app you are trying to open.",
                    "Allow",
                ),
            )
        }
        if (PermissionId.NOTIFICATIONS in missing && nudgesOn) {
            add(
                ReadinessItem(
                    PermissionId.NOTIFICATIONS,
                    "Notifications are off",
                    "Heads-ups are switched on but cannot reach you.",
                    "Open Notification Settings",
                ),
            )
        }
    }

/**
 * Shows what is missing for the habit tools to work, with a button to fix each one, and
 * nothing else (the heads-up switch lives in Extra · Habits). It re-checks every time the screen is resumed, so the
 * item disappears as soon as the person comes back from the system settings page.
 */
@Composable
internal fun StatsReadinessSection() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var resumes by remember { mutableStateOf(0) }
    var missing by remember { mutableStateOf<Set<PermissionId>>(emptySet()) }
    var nudgesOn by remember { mutableStateOf(HabitNudgeScheduler.isEnabled(context)) }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumes += 1
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumes) {
        nudgesOn = HabitNudgeScheduler.isEnabled(context)
        missing = listOf(
            PermissionId.USAGE,
            PermissionId.ACCESSIBILITY,
            PermissionId.OVERLAY,
            PermissionId.NOTIFICATIONS,
        ).filter { checkPermission(context, it) == PermissionStatus.DENIED }.toSet()
    }

    val items = readinessItems(missing, nudgesOn)
    if (items.isEmpty()) return
    StatsCard {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("TURN THESE ON", style = MaterialTheme.typography.labelLarge)
            items.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, fontSize = 14.scaledSp, fontWeight = FontWeight.Bold)
                    Text(item.why, fontSize = 12.scaledSp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { scope.launch { openPermissionSettings(context, item.id) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(item.actionLabel, fontSize = 13.scaledSp)
                    }
                }
            }
        }
    }
}
