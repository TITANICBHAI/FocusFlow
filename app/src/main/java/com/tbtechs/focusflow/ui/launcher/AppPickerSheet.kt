package com.tbtechs.focusflow.ui.launcher

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.tbtechs.focusflow.data.model.AllowedAppPreset
import com.tbtechs.focusflow.data.model.BLOCK_ALL_SENTINEL
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.missingInstalledAppInfo
import com.tbtechs.focusflow.ui.common.rememberInstalledApps
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp

private data class SensitiveApp(
    val reason: String,
    val category: String,
)

private val sensitiveApps = mapOf(
    "com.android.phone" to SensitiveApp("Phone service", "System"),
    "com.google.android.dialer" to SensitiveApp("Phone dialer", "System"),
    "com.samsung.android.dialer" to SensitiveApp("Phone dialer", "System"),
    "com.android.server.telecom" to SensitiveApp("Emergency calls", "System"),
    "com.google.android.apps.messaging" to SensitiveApp("SMS / 2FA verification", "Communication"),
    "com.samsung.android.messaging" to SensitiveApp("SMS / 2FA verification", "Communication"),
    "com.android.mms" to SensitiveApp("SMS / 2FA verification", "Communication"),
    "com.android.settings" to SensitiveApp("System Settings", "System"),
    "com.google.android.calculator" to SensitiveApp("Calculator utility", "Utility"),
    "com.google.android.calendar" to SensitiveApp("Calendar schedule", "Productivity"),
    "com.google.android.deskclock" to SensitiveApp("Clock & Alarms", "Utility"),
    "com.sec.android.app.clockpackage" to SensitiveApp("Clock & Alarms", "Utility"),
)

/**
 * Redesigned Allowed During Focus App Picker matching Screenshot 10.
 * Reusable across Settings -> Manage Allowed Apps and Task Focus Mode setup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    visible: Boolean,
    title: String = "Allowed During Focus",
    initialSelected: List<String>,
    noneWhenEmpty: Boolean = false,
    presets: List<AllowedAppPreset> = emptyList(),
    installedAppsRepository: InstalledAppsRepository,
    onSave: (List<String>) -> Unit,
    onSavePreset: (AllowedAppPreset) -> Unit = {},
    onDeletePreset: (String) -> Unit = {},
    onClose: () -> Unit,
) {
    if (!visible) return

    var search by remember { mutableStateOf("") }
    var warning by remember { mutableStateOf<Pair<String, SensitiveApp>?>(null) }
    var deletePreset by remember { mutableStateOf<AllowedAppPreset?>(null) }
    var showPresetInput by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    val context = LocalContext.current
    var showSensitiveInfo by remember {
        mutableStateOf(
            !context.getSharedPreferences("focusflow_ui", android.content.Context.MODE_PRIVATE)
                .getBoolean("sensitive_picker_info_dismissed", false),
        )
    }
    val appsState = rememberInstalledApps(installedAppsRepository)
    val apps = appsState.apps
    val loading = appsState.loading
    val loadError = appsState.error?.let { "Installed apps could not be loaded. Close and try again." }

    val initialWasBlockAll = remember(initialSelected) {
        initialSelected.contains(BLOCK_ALL_SENTINEL)
    }

    var selected by remember(initialSelected) {
        mutableStateOf(
            if (initialWasBlockAll) emptySet()
            else initialSelected.filter { it != BLOCK_ALL_SENTINEL }.toSet(),
        )
    }
    var autoSelectAll by remember(initialSelected, initialWasBlockAll, noneWhenEmpty) {
        mutableStateOf(!initialWasBlockAll && !noneWhenEmpty && initialSelected.isEmpty())
    }

    LaunchedEffect(apps) {
        if (autoSelectAll && apps.isNotEmpty()) {
            selected = apps.mapTo(mutableSetOf()) { it.packageName }
        }
    }

    val missingSelectedApps = remember(selected, apps, loading, loadError) {
        if (loading) {
            emptyList()
        } else {
            val installedPackages = apps.mapTo(mutableSetOf()) { it.packageName }
            selected.asSequence()
                .filterNot { it in installedPackages }
                .map { packageName ->
                    missingInstalledAppInfo(
                        packageName = packageName,
                        label = if (loadError == null) "App not installed" else "App details unavailable",
                    )
                }
                .toList()
        }
    }
    val displayedApps = remember(apps, missingSelectedApps) {
        (apps + missingSelectedApps).distinctBy { it.packageName }
    }
    val filteredApps = remember(displayedApps, search) {
        val query = search.trim().lowercase()
        displayedApps.filter { app ->
            val matchesSearch = query.isEmpty() ||
                app.appName.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query)
            matchesSearch
        }
    }

    val selectedPackages = {
        when {
            (initialWasBlockAll || noneWhenEmpty) && selected.isEmpty() ->
                listOf(BLOCK_ALL_SENTINEL)
            !noneWhenEmpty && apps.isNotEmpty() && selected.size == apps.size -> emptyList()
            else -> selected.toList().sorted()
        }
    }

    val sensitiveSelectedCount = selected.count { it in sensitiveApps }
    val allAppsSelected = apps.isNotEmpty() && selected.size == apps.size
    val selectionSummary = when {
        allAppsSelected -> "All apps allowed"
        selected.isEmpty() -> "All apps blocked"
        selected.all { it in sensitiveApps } ->
            "All apps blocked · $sensitiveSelectedCount sensitive kept allowed"
        else ->
            "${selected.size} app${if (selected.size == 1) "" else "s"} allowed · ${apps.size - selected.size} blocked"
    }
    val hasNonSensitiveSelection = selected.any { it !in sensitiveApps }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            color = DarkBackground,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Full-height header matching the native-style reference.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkCard)
                        .statusBarsPadding()
                        .height(48.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier.width(68.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text("Cancel", fontSize = 14.scaledSp, color = DarkTextSecondary)
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            title,
                            fontSize = 16.scaledSp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary,
                            maxLines = 1,
                        )
                    }
                    TextButton(
                        onClick = {
                            onSave(selectedPackages())
                            onClose()
                        },
                        modifier = Modifier.width(68.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text("Save", fontSize = 14.scaledSp, fontWeight = FontWeight.Bold, color = BrandPrimary)
                    }
                }

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                ) {
                    item {
                        Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                        Text(
                            "PRESETS",
                            fontSize = 12.scaledSp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.scaledSp,
                            color = DarkTextMuted,
                        )
                        TextButton(
                            onClick = { showPresetInput = true },
                            enabled = !showPresetInput,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(BrandPrimary.copy(alpha = 0.08f)),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 10.dp,
                                vertical = 0.dp,
                            ),
                        ) {
                            Icon(
                                Icons.Outlined.BookmarkAdd,
                                contentDescription = null,
                                tint = BrandPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Save current", fontSize = 11.scaledSp, color = BrandPrimary)
                        }
                        }
                    }

                        if (presets.isEmpty() && !showPresetInput) {
                            item {
                                Text(
                            "No presets yet — save the current selection as a named preset.",
                            fontSize = 12.scaledSp,
                            color = DarkTextMuted,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                )
                            }
                        } else if (presets.isNotEmpty() && !showPresetInput) {
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(presets, key = { it.id }) { preset ->
                                        Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(DarkCard)
                                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                                        .combinedClickable(
                                            onClick = {
                                                autoSelectAll = false
                                                selected = when {
                                                    preset.packages.contains(BLOCK_ALL_SENTINEL) ->
                                                        apps.map { it.packageName }.filter { it in sensitiveApps }.toSet()
                                                    preset.packages.isEmpty() ->
                                                        apps.mapTo(mutableSetOf()) { it.packageName }
                                                    else ->
                                                        (preset.packages + apps.map { it.packageName }.filter { it in sensitiveApps })
                                                            .filter { packageName -> apps.any { it.packageName == packageName } }
                                                            .toSet()
                                                }
                                            },
                                            onLongClick = { deletePreset = preset },
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                        ) {
                                            Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Text(preset.name, fontSize = 11.scaledSp, color = DarkTextPrimary)
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "Delete preset",
                                            tint = DarkTextMuted,
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable { deletePreset = preset },
                                        )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showPresetInput) {
                            item {
                                Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                            OutlinedTextField(
                                value = presetName,
                                onValueChange = { presetName = it.take(32) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("Preset name", color = DarkTextMuted, fontSize = 11.scaledSp) },
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DarkCard,
                                    unfocusedContainerColor = DarkCard,
                                    focusedBorderColor = BrandPrimary,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = DarkTextPrimary,
                                    unfocusedTextColor = DarkTextPrimary,
                                ),
                            )
                            TextButton(
                                onClick = {
                                    val trimmed = presetName.trim()
                                    if (trimmed.isNotEmpty()) {
                                        onSavePreset(
                                            AllowedAppPreset(
                                                id = "${System.currentTimeMillis()}-$trimmed",
                                                name = trimmed,
                                                packages = selectedPackages(),
                                            ),
                                        )
                                        presetName = ""
                                        showPresetInput = false
                                    }
                                },
                                enabled = presetName.isNotBlank(),
                            ) {
                                Text("Save", color = BrandPrimary)
                            }
                            TextButton(
                                onClick = {
                                    presetName = ""
                                    showPresetInput = false
                                },
                            ) {
                                Text("Cancel", color = DarkTextSecondary)
                            }
                                }
                            }
                        }

                    item {
                        Text(
                        selectionSummary,
                        fontSize = 14.scaledSp,
                        fontWeight = FontWeight.Medium,
                        color = DarkTextSecondary,
                        )
                    }

                    item {
                        Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                        PickerAction(
                            label = "Select All",
                            enabled = apps.isNotEmpty() && !allAppsSelected,
                            onClick = {
                                autoSelectAll = false
                                selected = apps.mapTo(mutableSetOf()) { it.packageName }
                            },
                        )
                        PickerAction(
                            label = "Deselect All",
                            enabled = hasNonSensitiveSelection,
                            onClick = {
                                autoSelectAll = false
                                selected = apps
                                    .map { it.packageName }
                                    .filter { it in sensitiveApps }
                                    .toSet()
                            },
                        )
                        }
                    }

                    item {
                        OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = DarkTextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        placeholder = { Text("Search by name or package...", color = DarkTextMuted, fontSize = 14.scaledSp) },
                        trailingIcon = {
                            if (search.isNotBlank()) {
                                IconButton(onClick = { search = "" }) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Clear search",
                                        tint = DarkTextSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkCard,
                            unfocusedContainerColor = DarkCard,
                            focusedBorderColor = BrandPrimary,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = DarkTextPrimary,
                            unfocusedTextColor = DarkTextPrimary,
                        ),
                        )
                    }

                    if (showSensitiveInfo) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceVariant.copy(alpha = 0.55f))
                                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                    .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Apps,
                                    contentDescription = null,
                                    tint = DarkTextMuted,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    "Checked apps are allowed during Focus. Sensitive apps warn before blocking. Phone, launcher, and WhatsApp protections stay usable.",
                                    modifier = Modifier.weight(1f),
                                    fontSize = 10.scaledSp,
                                    lineHeight = 14.scaledSp,
                                    color = DarkTextMuted,
                                )
                                IconButton(
                                    onClick = {
                                        showSensitiveInfo = false
                                        context.getSharedPreferences("focusflow_ui", android.content.Context.MODE_PRIVATE)
                                            .edit()
                                            .putBoolean("sensitive_picker_info_dismissed", true)
                                            .apply()
                                    },
                                    modifier = Modifier.size(22.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Dismiss information",
                                        tint = DarkTextMuted,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }

                    when {
                        loading && apps.isEmpty() -> item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = BrandPrimary, modifier = Modifier.size(26.dp))
                            }
                        }
                        loadError != null -> item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(loadError.orEmpty(), fontSize = 12.scaledSp, color = DarkTextSecondary)
                            }
                        }
                        filteredApps.isEmpty() && !loading -> item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(Icons.Outlined.Apps, contentDescription = null, tint = DarkTextMuted, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("No matching apps found", fontSize = 13.scaledSp, color = DarkTextSecondary)
                            }
                        }
                        else -> items(filteredApps, key = { it.packageName }) { app ->
                            AppPickerRow(
                                app = app,
                                checked = selected.contains(app.packageName),
                                onToggle = { clickedApp ->
                                    val sensitive = sensitiveApps[clickedApp.packageName]
                                    if (selected.contains(clickedApp.packageName) && sensitive != null) {
                                        warning = clickedApp.packageName to sensitive
                                    } else {
                                        autoSelectAll = false
                                        selected = selected.toggle(clickedApp.packageName)
                                    }
                                },
                            )
                        }
                    }
                    if (loading && apps.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(color = BrandPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Loading more apps…", fontSize = 10.scaledSp, color = DarkTextMuted)
                            }
                        }
                    }
                }
            }
        }
    }

    warning?.let { (packageName, sensitive) ->
        AlertDialog(
            onDismissRequest = { warning = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Block ${sensitive.reason}?") },
            text = {
                Text(
                    "Blocking this system app can prevent incoming phone calls, verification messages, or alarms. Are you sure you want to block it?",
                    fontSize = 13.scaledSp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        autoSelectAll = false
                        selected = selected - packageName
                        warning = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                ) {
                    Text("Block anyway", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { warning = null },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Keep allowed", color = DarkTextSecondary)
                }
            },
        )
    }

    deletePreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { deletePreset = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Delete preset?") },
            text = { Text("Delete preset “${preset.name}”?", fontSize = 13.scaledSp) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePreset(preset.id)
                        deletePreset = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletePreset = null },
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
private fun PickerAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (enabled) BrandPrimary.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            fontSize = 13.scaledSp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) BrandPrimary else DarkTextMuted,
        )
    }
}

@Composable
private fun AppPickerRow(
    app: InstalledAppInfo,
    checked: Boolean,
    onToggle: (InstalledAppInfo) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .clickable { onToggle(app) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppIcon(app.icon, size = 38.dp)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        app.appName,
                        fontSize = 14.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    sensitiveApps[app.packageName]?.let {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFFEDE9FE))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                "Sensitive",
                                fontSize = 8.scaledSp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6D5BD0),
                            )
                        }
                    }
                }
                Text(
                    app.packageName,
                    fontSize = 11.scaledSp,
                    color = DarkTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier = Modifier
                    .size(21.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (checked) BrandPrimary else Color.Transparent)
                    .border(
                        1.dp,
                        if (checked) BrandPrimary else DarkBorder,
                        RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "Allowed",
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppIcon(drawable: Drawable?, size: Dp = 36.dp) {
    val bitmap = remember(drawable) {
        runCatching { drawable?.toBitmap()?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Apps,
                contentDescription = null,
                tint = DarkTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value
