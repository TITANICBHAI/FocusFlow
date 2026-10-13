package com.tbtechs.focusflow.ui.launcher

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.StatusReady
import com.tbtechs.focusflow.ui.theme.StatusReadyText
import com.tbtechs.focusflow.ui.theme.StatusNotSetUp
import com.tbtechs.focusflow.ui.theme.StatusNotSetUpText
import kotlinx.coroutines.launch
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
    val isAlwaysOn = packageName in settings.alwaysBlockPackages
    val isTemporarilyBlocked = settings.standaloneBlockActive &&
        packageName in settings.standaloneBlockPackages &&
        settings.standaloneBlockUntilMs > System.currentTimeMillis()

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

    fun applyUntil(untilMs: Long) {
        applyTemporary(untilMs - System.currentTimeMillis())
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = DarkBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DarkTextMuted),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BrandPrimary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Quick Block $appName",
                        maxLines = 1,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DarkTextPrimary,
                    )
                    Text(
                        when {
                            isAlwaysOn && isTemporarilyBlocked ->
                                "Always-On and temporary blocking are both active"
                            isAlwaysOn -> "Always-On protection is active"
                            isTemporarilyBlocked ->
                                "Blocked until ${formatExpiry(settings.standaloneBlockUntilMs)}"
                             else -> "Choose how long to block this app"
                        },
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = DarkTextSecondary,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = DarkTextMuted)
                }
            }

            Text(
                "TEMPORARY BLOCK",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = DarkTextSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickBlockAction(
                    icon = Icons.Outlined.AccessTime,
                    label = "1 hour",
                    onClick = { applyTemporary(60 * 60 * 1000L) },
                    enabled = !loading,
                    modifier = Modifier.weight(1f),
                )
                QuickBlockAction(
                    icon = Icons.Outlined.WbSunny,
                    label = "Until tonight",
                    onClick = {
                        val now = LocalDateTime.now()
                        val tonight = LocalDateTime.of(now.toLocalDate(), LocalTime.of(20, 0))
                        applyUntil(tonight.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    },
                    enabled = !loading,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickBlockAction(
                    icon = Icons.Outlined.Nightlight,
                    label = "Tomorrow morning",
                    onClick = {
                        val tomorrow = LocalDate.now().plusDays(1)
                        val wakeUp = LocalDateTime.of(tomorrow, LocalTime.of(7, 0))
                        applyUntil(wakeUp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    },
                    enabled = !loading,
                    modifier = Modifier.weight(1f),
                )
                QuickBlockAction(
                    icon = Icons.Outlined.CalendarMonth,
                    label = if (customExpiry == null) "Choose time" else "Time selected",
                    onClick = {
                        showCustomDateTimePicker(context) { until ->
                            if (until <= System.currentTimeMillis()) {
                                error = "Choose a time in the future."
                            } else {
                                customExpiry = until
                                applyUntil(until)
                            }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                "ALWAYS-ON PROTECTION",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = DarkTextSecondary,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isAlwaysOn) DarkSurfaceVariant
                        else StatusNotSetUp.copy(alpha = 0.10f),
                    )
                    .border(
                        1.dp,
                        if (isAlwaysOn) DarkBorder else StatusNotSetUp.copy(alpha = 0.35f),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isAlwaysOn) Icons.Outlined.CheckCircle else Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = if (isAlwaysOn) StatusReady else StatusNotSetUp,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isAlwaysOn) "Already Always-On" else "Block always",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isAlwaysOn) StatusReadyText else DarkTextPrimary,
                    )
                    Text(
                        "Keep this app blocked until you remove it from the Always-On list",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = DarkTextSecondary,
                    )
                }
                if (!isAlwaysOn) {
                    TextButton(
                        onClick = {
                            if (isProtectedSystemApp(context, packageName)) {
                                protectedWarning = true
                            } else {
                                scope.launch {
                                    settingsRepository.setAlwaysBlockActive(
                                        active = true,
                                        packages = (settings.alwaysBlockPackages + packageName).distinct(),
                                    )
                                    onClose()
                                    onOpenAlwaysOn()
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Block", color = StatusNotSetUpText, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.HelpOutline,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(19.dp),
                        )
                        Text(
                            "Need to remove this later?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DarkTextPrimary,
                        )
                    }
                    Text(
                        when {
                             isTemporarilyBlocked && isAlwaysOn ->
                                "This app has both a temporary block and Always-On protection. Manage them separately:"
                            isTemporarilyBlocked ->
                                "This timed block is managed from Active. It expires automatically."
                            isAlwaysOn ->
                                "Always-On protection stays until you remove the app from the Always-On list."
                            else ->
                                "Temporary blocks expire automatically. Always-On apps are managed from Settings."
                        },
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = DarkTextSecondary,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isTemporarilyBlocked) {
                            TextButton(
                                onClick = {
                                    onClose()
                                    onOpenActive()
                                },
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Open Active", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (isAlwaysOn) {
                            TextButton(
                                onClick = {
                                    onClose()
                                    onOpenAlwaysOn()
                                },
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Open Always-On", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            error?.let {
                Text(it, color = Color(0xFFF87171), fontSize = 13.sp)
            }

            if (loading) {
                Text(
                    "Updating protection…",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 12.sp,
                    color = BrandPrimary,
                )
            }
            Text(
                 "Quick Block adds a temporary block to FocusFlow's existing protection list. It does not create a separate block history.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = DarkTextSecondary,
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (protectedWarning) ProtectedSystemAppDialog(onDismiss = { protectedWarning = false })
}

@Composable
private fun QuickBlockAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceVariant)
            .border(
                width = 1.dp,
                color = DarkBorder.copy(alpha = if (enabled) 1f else 0.5f),
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary,
            )
        }
    }
}

@Composable
private fun ProtectedSystemAppDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = DarkCard,
        titleContentColor = DarkTextPrimary,
        textContentColor = DarkTextSecondary,
        title = { Text("Protected system app", fontWeight = FontWeight.Bold) },
        text = { Text("This system app cannot be blocked because doing so could make the device unusable.") },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("OK")
            }
        },
    )
}

private fun formatExpiry(untilMs: Long): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(untilMs)

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
