package com.tbtechs.focusflow.ui.defense

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.model.DailyAllowanceEntry
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.ui.launcher.AppIcon
import com.tbtechs.focusflow.ui.common.FocusFlowSwitch
import com.tbtechs.focusflow.ui.common.rememberInstalledApps
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.BrandPrimaryLight
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp
import java.text.DateFormat
import java.util.Calendar

data class BlockPresetUi(
    val id: String,
    val name: String,
    val packages: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StandaloneBlockModal(
    visible: Boolean,
    blockedPackages: List<String>,
    blockUntilMs: Long,
    locked: Boolean,
    dailyAllowanceEntries: List<DailyAllowanceEntry> = emptyList(),
    vpnPackages: List<String> = emptyList(),
    presets: List<BlockPresetUi> = emptyList(),
    onSave: (List<String>, Long?, List<DailyAllowanceEntry>, List<String>, String?) -> Unit,
    onSavePreset: ((BlockPresetUi) -> Unit)? = null,
    onDeletePreset: ((String) -> Unit)? = null,
    onClose: () -> Unit,
    hintDismissed: Boolean = false,
    onDismissHint: () -> Unit = {},
    verifyPin: ((String) -> Boolean)? = null,
    sessionPinSet: Boolean = false,
    hashPin: ((String) -> String)? = null,
) {
    if (!visible) return
    val context = LocalContext.current
    val installedAppsState = rememberInstalledApps(
        remember(context) { InstalledAppsRepository(context) },
    )
    val apps = remember(installedAppsState.apps, context.packageName) {
        installedAppsState.apps
            .filterNot { isNeverBlockPackage(it.packageName, context.packageName) }
            .sortedBy { it.appName.lowercase() }
    }
    var showStrongerBlockHint by remember(visible, hintDismissed) { mutableStateOf(!hintDismissed) }

    var selected by remember(visible, blockedPackages) {
        mutableStateOf(
            blockedPackages
                .filterNot { isNeverBlockPackage(it, context.packageName) }
                .toSet(),
        )
    }
    var allowances by remember(visible, dailyAllowanceEntries) {
        mutableStateOf(dailyAllowanceEntries.associateBy { it.packageName })
    }
    var vpn by remember(visible, vpnPackages) { mutableStateOf(vpnPackages.toSet()) }
    var search by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var showPresetForm by remember { mutableStateOf(false) }
    var until by remember(visible, blockUntilMs) {
        mutableLongStateOf(blockUntilMs.takeIf { it > System.currentTimeMillis() } ?: defaultExpiry())
    }
    var confirmClear by remember { mutableStateOf(false) }
    var pinPrompt by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf(false) }
    var clearPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val loadingApps = installedAppsState.loading
    var expandedAllowancePackage by remember(visible, dailyAllowanceEntries) {
        mutableStateOf(dailyAllowanceEntries.firstOrNull()?.packageName)
    }

    val results = apps.filter {
        search.isBlank() ||
            it.appName.contains(search, true) ||
            it.packageName.contains(search, true)
    }
    val installedPackages = apps.mapTo(mutableSetOf()) { it.packageName }
    val manualPackages = selected.filter { it !in installedPackages }.toList()

    fun chooseDate() {
        val calendar = Calendar.getInstance().apply { timeInMillis = until }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val next = Calendar.getInstance().apply {
                    timeInMillis = until
                    set(year, month, day)
                }
                until = next.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    fun chooseTime() {
        val calendar = Calendar.getInstance().apply { timeInMillis = until }
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val next = Calendar.getInstance().apply {
                    timeInMillis = until
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }
                until = next.timeInMillis
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false,
        ).show()
    }

    fun commitSave(pinHash: String?) {
        onSave(
            selected.toList(),
            until,
            allowances.values.toList(),
            vpn.toList(),
            pinHash,
        )
        onClose()
    }

    fun save() {
        when {
            selected.isEmpty() -> errorMessage = "Select at least one app to block."
            until <= System.currentTimeMillis() -> errorMessage = "Choose an expiry time in the future."
            locked && sessionPinSet && verifyPin != null -> {
                pendingSave = true
                clearPin = ""
                pinPrompt = true
            }
            else -> commitSave(null)
        }
    }

    fun createAllowance(packageName: String) {
        allowances = allowances + (
            packageName to DailyAllowanceEntry(
                packageName = packageName,
                dailyAllowanceMs = 30L * 60_000L,
                mode = "time_budget",
                budgetMinutes = 30,
            )
        )
        expandedAllowancePackage = packageName
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DarkBackground,
        ) {
            Scaffold(
                containerColor = DarkBackground,
                contentWindowInsets = WindowInsets.statusBars,
                topBar = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = onClose,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                            ) {
                                Text("Cancel", color = DarkTextSecondary, fontSize = 15.scaledSp)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (locked) {
                                    Icon(
                                        Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Text(
                                    text = if (locked) "Block Active" else "Standalone Blocking",
                                    fontSize = 18.scaledSp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkTextPrimary,
                                )
                            }
                            Button(
                                onClick = ::save,
                                enabled = selected.isNotEmpty() && until > System.currentTimeMillis(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandPrimary,
                                    contentColor = Color.White,
                                    disabledContainerColor = DarkSurfaceVariant,
                                    disabledContentColor = DarkTextMuted,
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                            ) {
                                Text("Save", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        HorizontalDivider(color = DarkBorder)
                    }
                },
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    // Locked Warning Banner
                    if (locked) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
                                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = "The expiry and existing blocked apps are locked. You can still add apps and extend the block.",
                                        fontSize = 13.scaledSp,
                                        color = Color(0xFFFDE68A),
                                        lineHeight = 18.scaledSp,
                                    )
                                }
                            }
                        }
                    }

                    errorMessage?.let { msg ->
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                            ) {
                                Text(msg, color = Color(0xFFFCA5A5), fontSize = 13.scaledSp)
                            }
                        }
                    }

                    if (showStrongerBlockHint) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BrandPrimary.copy(alpha = 0.10f))
                                    .border(1.dp, BrandPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Shield,
                                        contentDescription = null,
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "Want a stronger block?",
                                            fontSize = 14.scaledSp,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkTextPrimary,
                                        )
                                        Text(
                                            text = "1. Select Settings in this app list.\n2. In the Defense tab, turn on Protect system controls.",
                                            fontSize = 12.scaledSp,
                                            lineHeight = 17.scaledSp,
                                            color = DarkTextSecondary,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            showStrongerBlockHint = false
                                            onDismissHint()
                                        },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = "Dismiss",
                                            tint = DarkTextMuted,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                // Expiry & Quick Extension Controls
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (locked) "BLOCK UNTIL (LOCKED)" else "BLOCK EXPIRY",
                            fontSize = 12.scaledSp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextMuted,
                            letterSpacing = 0.8.scaledSp,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (locked) {
                                ExpiryLockedChip(
                                    text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(until),
                                    modifier = Modifier.weight(1f),
                                )
                                ExpiryLockedChip(
                                    text = DateFormat.getTimeInstance(DateFormat.SHORT).format(until),
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                OutlinedButton(
                                    onClick = ::chooseDate,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 44.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = DarkCard,
                                        contentColor = DarkTextPrimary,
                                    ),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
                                ) {
                                    Icon(
                                        Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = BrandPrimary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(until),
                                        fontSize = 14.scaledSp,
                                    )
                                }

                                OutlinedButton(
                                    onClick = ::chooseTime,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 44.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = DarkCard,
                                        contentColor = DarkTextPrimary,
                                    ),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
                                ) {
                                    Icon(
                                        Icons.Outlined.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = BrandPrimary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        DateFormat.getTimeInstance(DateFormat.SHORT).format(until),
                                        fontSize = 14.scaledSp,
                                    )
                                }
                            }
                        }

                        if (locked) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Add time:",
                                    fontSize = 13.scaledSp,
                                    color = DarkTextSecondary,
                                    fontWeight = FontWeight.Medium,
                                )
                                listOf(30L, 60L, 120L, 240L).forEach { minutes ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(DarkSurfaceVariant)
                                            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                            .clickable { until += minutes * 60_000L }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            "+${if (minutes >= 60) "${minutes / 60}h" else "${minutes}m"}",
                                            fontSize = 13.scaledSp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = BrandPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Presets Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(DarkCard)
                                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                                .padding(14.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "PRESETS",
                                        fontSize = 12.scaledSp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkTextMuted,
                                        letterSpacing = 0.8.scaledSp,
                                    )
                                    Text(
                                        text = "+ Save current selection",
                                        fontSize = 12.scaledSp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected.isNotEmpty()) BrandPrimary else DarkTextMuted,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = selected.isNotEmpty()) {
                                                showPresetForm = true
                                            }
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                    )
                                }

                                if (presets.isEmpty()) {
                                    Text(
                                        "Select apps below, then tap “+ Save current selection” to create your first preset.",
                                        color = DarkTextSecondary,
                                        fontSize = 12.scaledSp,
                                        lineHeight = 17.scaledSp,
                                    )
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        presets.forEach { preset ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(DarkSurfaceVariant)
                                                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                                    .clickable {
                                                        val safePackages = preset.packages.filterNot {
                                                            isNeverBlockPackage(it, context.packageName)
                                                        }
                                                        selected = if (locked) selected + safePackages else safePackages.toSet()
                                                    }
                                                    .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "${preset.name} (${preset.packages.size})",
                                                        fontSize = 13.scaledSp,
                                                        color = DarkTextPrimary,
                                                    )
                                                    IconButton(
                                                        onClick = { onDeletePreset?.invoke(preset.id) },
                                                        modifier = Modifier.size(28.dp),
                                                    ) {
                                                        Icon(
                                                            Icons.Outlined.Delete,
                                                            contentDescription = "Delete preset",
                                                            tint = DarkTextMuted,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showPresetForm) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(DarkCard)
                                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = presetName,
                                    onValueChange = { presetName = it },
                                    placeholder = { Text("Preset name", color = DarkTextMuted) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = DarkTextPrimary,
                                        unfocusedTextColor = DarkTextPrimary,
                                        focusedBorderColor = BrandPrimary,
                                        unfocusedBorderColor = DarkBorder,
                                    ),
                                )
                                Button(
                                    onClick = {
                                        if (presetName.isNotBlank()) {
                                            onSavePreset?.invoke(
                                                BlockPresetUi(
                                                    System.currentTimeMillis().toString(),
                                                    presetName.trim(),
                                                    selected.toList(),
                                                ),
                                            )
                                            presetName = ""
                                            showPresetForm = false
                                        }
                                    },
                                    enabled = presetName.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                                ) {
                                    Text("Save")
                                }
                                IconButton(onClick = { showPresetForm = false }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Cancel", tint = DarkTextMuted)
                                }
                            }
                        }
                    }
                }

                // Advanced Package Input
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { advanced = !advanced },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DarkCard,
                                contentColor = DarkTextPrimary,
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Advanced",
                                fontSize = 14.scaledSp,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = if (advanced) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = if (advanced) "Collapse Advanced" else "Expand Advanced",
                                tint = DarkTextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        if (advanced) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = manual,
                                    onValueChange = { manual = it },
                                    placeholder = { Text("com.example.app", color = DarkTextMuted) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = DarkTextPrimary,
                                        unfocusedTextColor = DarkTextPrimary,
                                        focusedBorderColor = BrandPrimary,
                                        unfocusedBorderColor = DarkBorder,
                                    ),
                                )
                                Button(
                                    onClick = {
                                        val pkg = manual.trim().lowercase()
                                        if (pkg.contains('.') && isNeverBlockPackage(pkg, context.packageName)) {
                                            errorMessage = "This system app is protected and cannot be blocked."
                                        } else if (pkg.contains('.')) {
                                            selected = selected + pkg
                                            manual = ""
                                            errorMessage = null
                                        }
                                    },
                                    enabled = manual.contains('.'),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                                ) {
                                    Text("Add")
                                }
                            }
                        }
                    }
                }

                // Search & Counter
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search installed apps", color = DarkTextMuted) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Search, contentDescription = null, tint = DarkTextSecondary)
                            },
                            trailingIcon = {
                                if (search.isNotBlank()) {
                                    IconButton(onClick = { search = "" }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = DarkTextMuted)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkCard,
                                unfocusedContainerColor = DarkCard,
                                focusedTextColor = DarkTextPrimary,
                                unfocusedTextColor = DarkTextPrimary,
                                focusedBorderColor = BrandPrimary,
                                unfocusedBorderColor = DarkBorder,
                            ),
                        )
                        Text(
                            text = if (selected.isEmpty()) {
                                "Tap apps below to block them"
                            } else {
                                "${selected.size} app${if (selected.size == 1) "" else "s"} selected — tap to toggle"
                            },
                            fontSize = 13.scaledSp,
                            color = DarkTextSecondary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // Loading animation when querying apps
                if (loadingApps) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(
                                    color = BrandPrimary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp),
                                )
                                Text(
                                    text = "Loading installed apps…",
                                    fontSize = 14.scaledSp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkTextPrimary,
                                )
                                Text(
                                    text = "Scanning device applications",
                                    fontSize = 12.scaledSp,
                                    color = DarkTextMuted,
                                )
                            }
                        }
                    }
                } else if (results.isEmpty() && manualPackages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = DarkTextMuted,
                                    modifier = Modifier.size(32.dp),
                                )
                                Text(
                                    text = if (search.isNotBlank()) "No apps matching \"$search\"" else "No installed apps found",
                                    fontSize = 13.5.scaledSp,
                                    color = DarkTextSecondary,
                                )
                            }
                        }
                    }
                }
                if (!loadingApps && installedAppsState.error != null) {
                    item {
                        Text(
                            "Installed apps could not be loaded. Close and reopen this screen to retry.",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            fontSize = 12.scaledSp,
                            color = DarkTextSecondary,
                        )
                    }
                }

                // Manual packages if present
                if (manualPackages.isNotEmpty()) {
                    items(manualPackages) { pkg ->
                        val isBlocked = pkg in selected
                        val allowance = allowances[pkg]
                        val vpnBlocked = pkg in vpn

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(DarkCard)
                                .border(
                                    1.dp,
                                    if (isBlocked) BrandPrimary.copy(alpha = 0.45f) else DarkBorder,
                                    RoundedCornerShape(16.dp),
                                ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !(locked && isBlocked)) {
                                        selected = selected.toggle(pkg, locked)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Manual Package",
                                        fontSize = 14.5.scaledSp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DarkTextPrimary,
                                    )
                                    Text(
                                        text = pkg,
                                        fontSize = 11.5.scaledSp,
                                        color = DarkTextMuted,
                                    )
                                }
                                AppSelectionIndicator(
                                    selected = isBlocked,
                                    locked = locked && isBlocked,
                                )
                            }
                        }
                    }
                }

                // Installed Apps List matching 4a.jpg reference
                items(results, key = { it.packageName }) { app ->
                    if (app.packageName !in manualPackages) {
                        val isBlocked = app.packageName in selected
                        val allowance = allowances[app.packageName]
                        val vpnBlocked = app.packageName in vpn

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkCard)
                                .border(
                                    1.dp,
                                    if (isBlocked) BrandPrimary.copy(alpha = 0.45f) else DarkBorder,
                                    RoundedCornerShape(12.dp),
                                ),
                        ) {
                            // Top Row: App info + Red block icon / rounded checkbox
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !(locked && isBlocked)) {
                                        expandedAllowancePackage = null
                                        selected = selected.toggle(app.packageName, locked)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    AppIcon(app.icon, size = 34.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appName,
                                            fontSize = 14.scaledSp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DarkTextPrimary,
                                            maxLines = 1,
                                        )
                                        Text(
                                            text = app.packageName,
                                            fontSize = 11.scaledSp,
                                            color = DarkTextMuted,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                AppSelectionIndicator(
                                    selected = isBlocked,
                                    locked = locked && isBlocked,
                                )
                            }

                            HorizontalDivider(
                                color = DarkBorder,
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )

                            // Daily allowance is an inline editor, matching the picker reference.
                            if (allowance != null && expandedAllowancePackage == app.packageName) {
                                InlineAllowanceEditor(
                                    entry = allowance,
                                    onUpdate = { updated ->
                                        allowances = allowances + (app.packageName to updated)
                                    },
                                    onRemove = {
                                        allowances = allowances - app.packageName
                                        expandedAllowancePackage = null
                                    },
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkSurfaceVariant.copy(alpha = 0.9f))
                                        .clickable {
                                        expandedAllowancePackage = null
                                            if (allowance == null) {
                                                createAllowance(app.packageName)
                                            } else {
                                                expandedAllowancePackage = app.packageName
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.WbSunny,
                                        contentDescription = null,
                                        tint = if (allowance != null) Color(0xFFF59E0B) else DarkTextMuted,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = if (allowance == null) "Add daily allowance" else allowanceSummary(allowance),
                                        fontSize = 11.scaledSp,
                                        color = if (allowance != null) Color(0xFFFBBF24) else DarkTextSecondary,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            HorizontalDivider(
                                color = DarkBorder.copy(alpha = 0.5f),
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )

                            // Sub-row 2: Network block (VPN)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSurfaceVariant.copy(alpha = 0.9f))
                                    .clickable(enabled = !locked) {
                                        expandedAllowancePackage = null
                                        vpn = vpn.toggle(app.packageName, locked)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = if (vpnBlocked) BrandPrimary else DarkTextMuted,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = if (vpnBlocked) "Blocked from internet (VPN)" else "Add network block (VPN)",
                                    fontSize = 11.scaledSp,
                                    color = if (vpnBlocked) BrandPrimaryLight else DarkTextSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                // Clear Block Option (when not locked)
                item {
                    if (selected.isNotEmpty() && !locked) {
                        TextButton(
                            onClick = { confirmClear = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                .defaultMinSize(minHeight = 44.dp),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                            Spacer(Modifier.width(8.dp))
                            Text("Clear Block", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Clear Block", fontWeight = FontWeight.Bold) },
            text = { Text("This disables the standalone block and allows all apps again.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClear = false
                        if (verifyPin != null && sessionPinSet) {
                            pinPrompt = true
                        } else {
                            onSave(emptyList(), null, allowances.values.toList(), emptyList(), null)
                            onClose()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Clear", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmClear = false },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    if (pinPrompt) {
        AlertDialog(
            onDismissRequest = {
                pinPrompt = false
                clearPin = ""
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Session Password Required", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = clearPin,
                    onValueChange = { clearPin = it },
                    label = { Text("Password", color = DarkTextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DarkTextPrimary,
                        unfocusedTextColor = DarkTextPrimary,
                        focusedBorderColor = BrandPrimary,
                        unfocusedBorderColor = DarkBorder,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (verifyPin?.invoke(clearPin) == true) {
                            val hash = hashPin?.invoke(clearPin)
                            pinPrompt = false
                            if (pendingSave) {
                                pendingSave = false
                                commitSave(hash)
                            } else {
                                onSave(emptyList(), null, allowances.values.toList(), emptyList(), hash)
                                onClose()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Confirm", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pinPrompt = false },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

}

@Composable
private fun AppSelectionIndicator(
    selected: Boolean,
    locked: Boolean,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    selected && locked -> DarkTextMuted
                    selected -> Color(0xFFEF4444)
                    else -> Color(0xFFF4F4F5)
                },
            )
            .border(
                width = if (selected) 0.dp else 1.5.dp,
                color = if (selected) Color.Transparent else Color(0xFFCBD5E1),
                shape = RoundedCornerShape(6.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Outlined.Block,
                contentDescription = "Blocked",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ExpiryLockedChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFE5E7EB))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = "Locked",
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                color = Color(0xFF6B7280),
                fontSize = 14.scaledSp,
                maxLines = 1,
            )
        }
    }
}

private fun allowanceSummary(entry: DailyAllowanceEntry): String =
    when (entry.mode) {
        "count" -> "${entry.countPerDay}×/day"
        "interval" -> "${entry.intervalMinutes} min every ${entry.intervalHours} hr"
        else -> "${entry.budgetMinutes} min/day"
    }

@Composable
private fun InlineAllowanceEditor(
    entry: DailyAllowanceEntry,
    onUpdate: (DailyAllowanceEntry) -> Unit,
    onRemove: () -> Unit,
) {
    fun update(
        mode: String = entry.mode,
        count: Int = entry.countPerDay,
        budgetMinutes: Int = entry.budgetMinutes,
        intervalMinutes: Int = entry.intervalMinutes,
        intervalHours: Int = entry.intervalHours,
    ) {
        val dailyAllowanceMs = when (mode) {
            "count" -> 0L
            "interval" -> intervalMinutes.coerceAtLeast(1).toLong() * 60_000L
            else -> budgetMinutes.coerceAtLeast(1).toLong() * 60_000L
        }
        onUpdate(
            entry.copy(
                mode = mode,
                countPerDay = count.coerceAtLeast(1),
                budgetMinutes = budgetMinutes.coerceAtLeast(1),
                intervalMinutes = intervalMinutes.coerceAtLeast(1),
                intervalHours = intervalHours.coerceAtLeast(1),
                dailyAllowanceMs = dailyAllowanceMs,
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Outlined.WbSunny,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    "Daily allowance:",
                    color = Color(0xFFF59E0B),
                    fontSize = 12.5.scaledSp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                allowanceSummary(entry),
                color = Color(0xFFFBBF24),
                fontSize = 12.5.scaledSp,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(26.dp),
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Remove daily allowance",
                    tint = DarkTextMuted,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AllowanceModeButton(
                label = "Count",
                selected = entry.mode == "count",
                onClick = { update(mode = "count") },
                modifier = Modifier.weight(1f),
            )
            AllowanceModeButton(
                label = "Time",
                selected = entry.mode == "time_budget",
                onClick = { update(mode = "time_budget") },
                modifier = Modifier.weight(1f),
            )
            AllowanceModeButton(
                label = "Interval",
                selected = entry.mode == "interval",
                onClick = { update(mode = "interval") },
                modifier = Modifier.weight(1f),
            )
        }

        when (entry.mode) {
            "count" -> AllowanceStepperRow(
                label = "Opens per day",
                value = "${entry.countPerDay}",
                onDecrease = { update(count = entry.countPerDay - 1) },
                onIncrease = { update(count = entry.countPerDay + 1) },
            )
            "interval" -> {
                AllowanceStepperRow(
                    label = "Minutes per window",
                    value = "${entry.intervalMinutes} min",
                    onDecrease = { update(intervalMinutes = entry.intervalMinutes - 1) },
                    onIncrease = { update(intervalMinutes = entry.intervalMinutes + 1) },
                )
                AllowanceStepperRow(
                    label = "Window size",
                    value = "${entry.intervalHours} hr",
                    onDecrease = { update(intervalHours = entry.intervalHours - 1) },
                    onIncrease = { update(intervalHours = entry.intervalHours + 1) },
                )
            }
            else -> AllowanceStepperRow(
                label = "Minutes per day",
                value = "${entry.budgetMinutes} min",
                onDecrease = { update(budgetMinutes = entry.budgetMinutes - 1) },
                onIncrease = { update(budgetMinutes = entry.budgetMinutes + 1) },
            )
        }
    }
}

@Composable
private fun AllowanceModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Color(0xFF30271D) else Color(0xFFF4F4F5))
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFFF59E0B) else Color.Transparent,
                shape = RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color(0xFFF59E0B) else Color(0xFF8B929C),
            fontSize = 11.5.scaledSp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AllowanceStepperRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = DarkTextSecondary,
            fontSize = 12.5.scaledSp,
        )
        StepperButton(icon = Icons.Outlined.Remove, contentDescription = "Decrease", onClick = onDecrease)
        Text(
            value,
            modifier = Modifier.width(64.dp),
            color = Color(0xFFFBBF24),
            fontSize = 12.5.scaledSp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepperButton(icon = Icons.Outlined.Add, contentDescription = "Increase", onClick = onIncrease)
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.7f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color(0xFFF59E0B),
            modifier = Modifier.size(14.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StandaloneAllowanceDialog(
    app: InstalledAppInfo,
    currentEntry: DailyAllowanceEntry?,
    locked: Boolean,
    onConfirm: (DailyAllowanceEntry) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(currentEntry?.mode ?: "time_budget") }
    var minutes by remember { mutableIntStateOf(currentEntry?.budgetMinutes?.takeIf { it > 0 } ?: 30) }
    var count by remember { mutableIntStateOf(currentEntry?.countPerDay?.takeIf { it > 0 } ?: 3) }
    var interval by remember { mutableIntStateOf(currentEntry?.intervalMinutes?.takeIf { it > 0 } ?: 15) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = DarkCard,
        titleContentColor = DarkTextPrimary,
        textContentColor = DarkTextSecondary,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppIcon(app.icon)
                Column {
                    Text(
                        text = "Daily Allowance",
                        fontSize = 16.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                    Text(
                        text = app.appName,
                        fontSize = 13.scaledSp,
                        color = DarkTextSecondary,
                        maxLines = 1,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Allow limited daily access during active block periods.",
                    fontSize = 12.5.scaledSp,
                    color = DarkTextSecondary,
                )

                // Mode Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val modes = listOf(
                        "time_budget" to "Time Limit",
                        "count" to "Launches",
                        "interval" to "Cooldown",
                    )
                    modes.forEach { (m, label) ->
                        val isSelected = mode == m
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) BrandPrimary else Color.Transparent)
                                .clickable { mode = m }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.5.scaledSp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else DarkTextSecondary,
                            )
                        }
                    }
                }

                when (mode) {
                    "time_budget" -> {
                        Text(
                            text = "Daily Time Budget",
                            fontSize = 12.scaledSp,
                            fontWeight = FontWeight.Medium,
                            color = DarkTextPrimary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(10, 15, 20, 30, 45, 60, 90, 120).forEach { mins ->
                                val isSelected = minutes == mins
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) BrandPrimary else DarkSurfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isSelected) BrandPrimary else DarkBorder,
                                            RoundedCornerShape(12.dp),
                                        )
                                        .clickable { minutes = mins }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = "${mins}m",
                                        fontSize = 12.scaledSp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) Color.White else DarkTextPrimary,
                                    )
                                }
                            }
                        }
                    }
                    "count" -> {
                        Text(
                            text = "Max Launches Per Day",
                            fontSize = 12.scaledSp,
                            fontWeight = FontWeight.Medium,
                            color = DarkTextPrimary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(1, 2, 3, 5, 10).forEach { c ->
                                val isSelected = count == c
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) BrandPrimary else DarkSurfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isSelected) BrandPrimary else DarkBorder,
                                            RoundedCornerShape(12.dp),
                                        )
                                        .clickable { count = c }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$c",
                                        fontSize = 13.scaledSp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else DarkTextPrimary,
                                    )
                                }
                            }
                        }
                    }
                    "interval" -> {
                        Text(
                            text = "Cooldown Interval Between Opens",
                            fontSize = 12.scaledSp,
                            fontWeight = FontWeight.Medium,
                            color = DarkTextPrimary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(5, 15, 30, 60).forEach { mins ->
                                val isSelected = interval == mins
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) BrandPrimary else DarkSurfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isSelected) BrandPrimary else DarkBorder,
                                            RoundedCornerShape(12.dp),
                                        )
                                        .clickable { interval = mins }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${mins}m",
                                        fontSize = 12.scaledSp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) Color.White else DarkTextPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val entry = when (mode) {
                        "count" -> DailyAllowanceEntry(
                            packageName = app.packageName,
                            dailyAllowanceMs = 0L,
                            mode = "count",
                            countPerDay = count,
                            budgetMinutes = minutes,
                            intervalMinutes = interval,
                        )
                        "interval" -> DailyAllowanceEntry(
                            packageName = app.packageName,
                            dailyAllowanceMs = interval * 60_000L,
                            mode = "interval",
                            countPerDay = count,
                            budgetMinutes = minutes,
                            intervalMinutes = interval,
                        )
                        else -> DailyAllowanceEntry(
                            packageName = app.packageName,
                            dailyAllowanceMs = minutes * 60_000L,
                            mode = "time_budget",
                            budgetMinutes = minutes,
                            countPerDay = count,
                            intervalMinutes = interval,
                        )
                    }
                    onConfirm(entry)
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
            ) {
                Text("Set Allowance", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (currentEntry != null && !locked) {
                    TextButton(
                        onClick = onRemove,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    ) {
                        Text("Remove", color = Color(0xFFEF4444), fontSize = 13.scaledSp)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Cancel", color = DarkTextSecondary, fontSize = 13.scaledSp)
                }
            }
        },
    )
}

private fun Set<String>.toggle(value: String, locked: Boolean): Set<String> {
    if (locked && value in this) return this
    return if (value in this) this - value else this + value
}

private fun defaultExpiry(): Long =
    Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis

private fun isNeverBlockPackage(packageName: String, ownPackageName: String): Boolean =
    packageName.equals(ownPackageName, ignoreCase = true) ||
        packageName.equals("android", ignoreCase = true) ||
        packageName.equals("com.android.systemui", ignoreCase = true) ||
        packageName.equals("com.android.phone", ignoreCase = true) ||
        packageName.equals("com.android.server.telecom", ignoreCase = true) ||
        packageName.equals("com.google.android.dialer", ignoreCase = true) ||
        packageName.equals("com.google.android.gms", ignoreCase = true) ||
        packageName.equals("com.google.android.gsf", ignoreCase = true) ||
        packageName.contains("packageinstaller", ignoreCase = true) ||
        packageName.contains("permissioncontroller", ignoreCase = true) ||
        packageName.contains("launcher", ignoreCase = true)
