package com.tbtechs.focusflow.ui.stats.phone

import android.content.Context
import com.tbtechs.focusflow.ui.theme.scaledSp
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tbtechs.focusflow.analytics.phone.PhoneText
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.notifications.HabitNudge
import com.tbtechs.focusflow.notifications.HabitNudgeScheduler
import com.tbtechs.focusflow.ui.stats.StatsBlue
import com.tbtechs.focusflow.ui.stats.StatsCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import kotlinx.coroutines.launch

/**
 * The switch for heads-up notifications, plus a preview of the ones the person would get today.
 *
 * [promoOnly] is the short version for the Stats screen: it shows only while nudges are off, as an invitation,
 * and disappears once they are on. On the Habits tab ([promoOnly] = false) it is always there as the control.
 */
@Composable
internal fun NudgeCard(modifier: Modifier = Modifier, promoOnly: Boolean = false) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(HabitNudgeScheduler.isEnabled(context)) }
    var allowed by remember { mutableStateOf(true) }
    var preview by remember { mutableStateOf<List<HabitNudge>?>(null) }
    var resumes by remember { mutableStateOf(0) }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumes += 1
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumes) {
        enabled = HabitNudgeScheduler.isEnabled(context)
        allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        preview = runCatching { HabitNudgeScheduler.plan(context) }.getOrDefault(emptyList())
    }

    if (promoOnly && enabled) return

    val toggle: (Boolean) -> Unit = { on ->
        scope.launch {
            AppModule.settingsRepository.putBoolean(HabitNudgeScheduler.ENABLED_KEY, on)
            enabled = on
            HabitNudgeScheduler.rescheduleAsync(context)
            resumes += 1
        }
    }

    StatsCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.NotificationsActive, null, tint = StatsBlue, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text("Nudges", fontSize = 16.scaledSp, fontWeight = FontWeight.ExtraBold)
                    Text("A heads-up before the hours you slip", fontSize = 12.scaledSp, color = DarkTextMuted)
                }
                Switch(checked = enabled, onCheckedChange = toggle)
            }
            if (!promoOnly) {
                val items = preview
                when {
                    items == null -> Unit
                    items.isEmpty() -> Text(
                        "Nothing to nudge about yet. Nudges appear once a clear habit shows up.",
                        fontSize = 13.scaledSp,
                        color = DarkTextMuted,
                    )
                    else -> items.forEach { nudge ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                PhoneText.clock(nudge.minuteOfDay),
                                modifier = Modifier.width(78.dp),
                                fontSize = 13.scaledSp,
                                fontWeight = FontWeight.Bold,
                                color = StatsBlue,
                            )
                            Text(nudge.title, fontSize = 13.scaledSp, modifier = Modifier.weight(1f), maxLines = 2)
                        }
                    }
                }
                if (enabled && !allowed) {
                    TextButton(
                        onClick = { openNotificationSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Notifications are off — allow them") }
                }
            }
        }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
