package com.tbtechs.focusflow.ui.launcher

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.repository.SettingsRepository
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickBlockSheet(
    visible: Boolean,
    packageName: String,
    appName: String,
    settings: AppSettings,
    settingsRepository: SettingsRepository,
    onClose: () -> Unit,
    onOpenActive: () -> Unit,
    onOpenAlwaysOn: () -> Unit,
) {
    if (!visible) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customExpiry by remember { mutableStateOf<Long?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var protectedWarning by remember { mutableStateOf(false) }

    fun applyTemporary(durationMs: Long) {
        if (isProtectedSystemApp(context, packageName)) {
            protectedWarning = true
            return
        }
        scope.launch {
            loading = true
            error = null
            try {
                val until = System.currentTimeMillis() + durationMs.coerceAtLeast(1L)
                settingsRepository.setStandaloneBlock(
                    active = true,
                    packages = (settings.standaloneBlockPackages + packageName).distinct(),
                    untilMs = until,
                )
                onClose()
            } catch (exception: Exception) {
                error = exception.message ?: "Could not start the temporary block."
            } finally {
                loading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(LauncherDimensions.pagePadding),
            verticalArrangement = Arrangement.spacedBy(LauncherDimensions.gap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quick Block", style = MaterialTheme.typography.headlineSmall)
                    Text(appName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close")
                }
            }
            Button(
                onClick = { applyTemporary(60 * 60 * 1000L) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.AccessTime, contentDescription = null)
                Text("  Block for one hour")
            }
            Button(
                onClick = {
                    val now = LocalDateTime.now()
                    val tonight = LocalDateTime.of(now.toLocalDate(), LocalTime.of(23, 59))
                    applyTemporary(Duration.between(now, tonight).toMillis())
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Until tonight") }
            Button(
                onClick = {
                    val tomorrow = LocalDate.now().plusDays(1)
                    val wakeUp = LocalDateTime.of(tomorrow, LocalTime.of(7, 0))
                    applyTemporary(Duration.between(LocalDateTime.now(), wakeUp).toMillis())
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Until tomorrow morning") }
            OutlinedButton(
                onClick = {
                    showCustomDateTimePicker(context) { until ->
                        customExpiry = until
                        applyTemporary(until - System.currentTimeMillis())
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (customExpiry == null) "Choose custom expiry" else "Custom expiry selected") }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null)
                Column(Modifier.padding(start = LauncherDimensions.gap).weight(1f)) {
                    Text("Always-On", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Keep this app blocked until you remove it from Always-On.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        if (isProtectedSystemApp(context, packageName)) protectedWarning = true
                        else scope.launch {
                            settingsRepository.setAlwaysBlockActive(
                                active = true,
                                packages = (settings.alwaysBlockPackages + packageName).distinct(),
                            )
                            onClose()
                            onOpenAlwaysOn()
                        }
                    },
                ) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                    Text("Enable")
                }
            }
            if (settings.standaloneBlockActive) {
                Text(
                    "A temporary block is already active until ${settings.standaloneBlockUntilMs}.",
                    color = MaterialTheme.colorScheme.tertiary,
                )
                TextButton(onClick = onOpenActive) { Text("Open Active") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(
                "Quick Block uses FocusFlow's existing block lists. No separate block history is created.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (protectedWarning) {
        AlertDialog(
            onDismissRequest = { protectedWarning = false },
            title = { Text("Protected system app") },
            text = { Text("This system app cannot be blocked because doing so could make the device unusable.") },
            confirmButton = { TextButton(onClick = { protectedWarning = false }) { Text("OK") } },
        )
    }
}

private fun isProtectedSystemApp(context: Context, packageName: String): Boolean =
    runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        info.flags and ApplicationInfo.FLAG_SYSTEM != 0
    }.getOrDefault(false)

private fun showCustomDateTimePicker(context: Context, onSelected: (Long) -> Unit) {
    val now = LocalDateTime.now()
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val selected = LocalDateTime.of(
                        LocalDate.of(year, month + 1, day),
                        LocalTime.of(hour, minute),
                    )
                    onSelected(selected.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                },
                now.hour,
                now.minute,
                false,
            ).show()
        },
        now.year,
        now.monthValue - 1,
        now.dayOfMonth,
    ).show()
}