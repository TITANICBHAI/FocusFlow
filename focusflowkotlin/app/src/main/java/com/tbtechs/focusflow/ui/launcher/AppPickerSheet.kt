package com.tbtechs.focusflow.ui.launcher

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tbtechs.focusflow.data.model.AllowedAppPreset
import com.tbtechs.focusflow.data.model.BLOCK_ALL_SENTINEL
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import kotlinx.coroutines.launch

internal object LauncherDimensions {
    val pagePadding = 20.dp
    val itemPadding = 12.dp
    val smallGap = 6.dp
    val gap = 12.dp
    val icon = 42.dp
    val radius = 16.dp
}

private data class SensitiveApp(
    val category: String,
    val warning: String,
)

private val sensitiveApps = mapOf(
    "com.android.settings" to SensitiveApp("Settings", "Blocking Settings can make it difficult to change device permissions."),
    "com.google.android.dialer" to SensitiveApp("the dialer", "Blocking the dialer may prevent emergency calls."),
    "com.android.dialer" to SensitiveApp("the dialer", "Blocking the dialer may prevent emergency calls."),
    "com.android.systemui" to SensitiveApp("System UI", "Blocking System UI can make the device difficult to operate."),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    visible: Boolean,
    title: String = "Allowed Apps",
    initialSelected: List<String>,
    noneWhenEmpty: Boolean = false,
    presets: List<AllowedAppPreset>,
    installedAppsRepository: InstalledAppsRepository,
    onSave: (List<String>) -> Unit,
    onSavePreset: (AllowedAppPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onClose: () -> Unit,
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var presetName by remember { mutableStateOf("") }
    var showPresetInput by remember { mutableStateOf(false) }
    var deletePreset by remember { mutableStateOf<AllowedAppPreset?>(null) }
    var confirmSave by remember { mutableStateOf(false) }
    var warning by remember { mutableStateOf<Pair<String, SensitiveApp>?>(null) }
    val initialWasBlockAll = initialSelected.contains(BLOCK_ALL_SENTINEL)

    LaunchedEffect(Unit) {
        loading = true
        apps = runCatching {
            installedAppsRepository.getInstalledApps()
                .sortedBy { it.appName.lowercase() }
        }.getOrDefault(emptyList())
        val packageSet = apps.mapTo(mutableSetOf()) { it.packageName }
        selected = when {
            initialWasBlockAll -> emptySet()
            initialSelected.isEmpty() && !noneWhenEmpty -> packageSet
            else -> initialSelected.filter { it in packageSet }.toSet()
        }
        loading = false
    }

    val filteredApps = remember(apps, search) {
        val query = search.trim().lowercase()
        if (query.isBlank()) apps
        else apps.filter {
            it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }
    }
    val selectedPackages = {
        when {
            initialWasBlockAll && selected.isEmpty() -> listOf(BLOCK_ALL_SENTINEL)
            !noneWhenEmpty && apps.isNotEmpty() && selected.size == apps.size -> emptyList()
            else -> selected.toList().sorted()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
    ) {
        PickerContent(
            title = title,
            apps = filteredApps,
            selected = selected,
            search = search,
            loading = loading,
            presets = presets,
            showPresetInput = showPresetInput,
            presetName = presetName,
            onSearch = { search = it },
            onClose = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() }
            },
            onToggle = { app ->
                val sensitive = sensitiveApps[app.packageName]
                if (selected.contains(app.packageName) && sensitive != null) {
                    warning = app.packageName to sensitive
                } else {
                    selected = selected.toggle(app.packageName)
                }
            },
            onSelectAll = { selected = apps.mapTo(mutableSetOf()) { it.packageName } },
            onDeselectAll = {
                selected = apps
                    .map { it.packageName }
                    .filterNot { it in sensitiveApps }
                    .toSet()
            },
            onApplyPreset = { preset ->
                selected = preset.packages
                    .filter { packageName -> apps.any { it.packageName == packageName } }
                    .toSet()
            },
            onLongPressPreset = { deletePreset = it },
            onShowPresetInput = { showPresetInput = true },
            onPresetNameChange = { presetName = it },
            onSavePreset = {
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
            onCancelPreset = {
                presetName = ""
                showPresetInput = false
            },
            onRequestSave = { confirmSave = true },
        )
    }

    warning?.let { (packageName, sensitive) ->
        AlertDialog(
            onDismissRequest = { warning = null },
            title = { Text("Block ${sensitive.category}?") },
            text = { Text(sensitive.warning + "\n\nYou can re-enable it any time from this screen.") },
            confirmButton = {
                Button(onClick = {
                    selected = selected - packageName
                    warning = null
                }) { Text("Block anyway") }
            },
            dismissButton = { TextButton(onClick = { warning = null }) { Text("Cancel") } },
        )
    }

    deletePreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { deletePreset = null },
            title = { Text("Delete preset?") },
            text = { Text("Delete “${preset.name}”?") },
            confirmButton = {
                Button(onClick = {
                    onDeletePreset(preset.id)
                    deletePreset = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletePreset = null }) { Text("Cancel") } },
        )
    }

    if (confirmSave) {
        val blockedCount = (apps.size - selected.size).coerceAtLeast(0)
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text("Save app selection?") },
            text = {
                Text(
                    if (noneWhenEmpty && selected.isEmpty()) {
                        "No apps are allowed during Focus. This will block all installed apps."
                    } else {
                        "${selected.size} apps allowed, $blockedCount blocked."
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    onSave(selectedPackages())
                    confirmSave = false
                    onClose()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PickerContent(
    title: String,
    apps: List<InstalledAppInfo>,
    selected: Set<String>,
    search: String,
    loading: Boolean,
    presets: List<AllowedAppPreset>,
    showPresetInput: Boolean,
    presetName: String,
    onSearch: (String) -> Unit,
    onClose: () -> Unit,
    onToggle: (InstalledAppInfo) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onApplyPreset: (AllowedAppPreset) -> Unit,
    onLongPressPreset: (AllowedAppPreset) -> Unit,
    onShowPresetInput: () -> Unit,
    onPresetNameChange: (String) -> Unit,
    onSavePreset: () -> Unit,
    onCancelPreset: () -> Unit,
    onRequestSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LauncherDimensions.pagePadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Close")
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text("Search apps") },
            trailingIcon = {
                if (search.isNotBlank()) {
                    IconButton(onClick = { onSearch("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                }
            },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(LauncherDimensions.smallGap),
            modifier = Modifier.padding(vertical = LauncherDimensions.gap),
        ) {
            OutlinedButton(onClick = onSelectAll) { Text("Select all") }
            OutlinedButton(onClick = onDeselectAll) { Text("Deselect all") }
            OutlinedButton(onClick = onRequestSave) { Text("Save") }
        }
        if (presets.isNotEmpty() || showPresetInput) {
            Text("Presets", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(LauncherDimensions.smallGap),
                modifier = Modifier.padding(vertical = LauncherDimensions.smallGap),
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = false,
                        onClick = {},
                        modifier = Modifier.combinedClickable(
                            onClick = { onApplyPreset(preset) },
                            onLongClick = { onLongPressPreset(preset) },
                        ),
                        label = { Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete ${preset.name}",
                            )
                        },
                    )
                }
            }
        }
        if (showPresetInput) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LauncherDimensions.smallGap),
            ) {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = onPresetNameChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Preset name") },
                )
                TextButton(onClick = onSavePreset, enabled = presetName.isNotBlank()) { Text("Save") }
                TextButton(onClick = onCancelPreset) { Text("Cancel") }
            }
        } else {
            TextButton(onClick = onShowPresetInput) { Text("Save current as preset") }
        }
        Spacer(Modifier.height(LauncherDimensions.smallGap))
        when {
            loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            apps.isEmpty() -> Column(
                modifier = Modifier.fillMaxWidth().padding(LauncherDimensions.pagePadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.Apps, contentDescription = null)
                Text("No installed apps found", style = MaterialTheme.typography.titleMedium)
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(LauncherDimensions.smallGap),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppPickerRow(app, selected.contains(app.packageName), onToggle)
                }
            }
        }
        Spacer(Modifier.height(LauncherDimensions.gap))
    }
}

@Composable
private fun AppPickerRow(
    app: InstalledAppInfo,
    checked: Boolean,
    onToggle: (InstalledAppInfo) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (checked) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(LauncherDimensions.radius),
            )
            .clickable { onToggle(app) }
            .padding(LauncherDimensions.itemPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LauncherDimensions.gap),
    ) {
        AppIcon(app.icon)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                sensitiveApps[app.packageName]?.let {
                    AssistChip(
                        onClick = {},
                        label = { Text("Sensitive") },
                        modifier = Modifier.padding(start = LauncherDimensions.smallGap),
                    )
                }
            }
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = if (checked) Icons.Outlined.Check else Icons.Outlined.Close,
            contentDescription = if (checked) "Allowed" else "Blocked",
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
internal fun AppIcon(drawable: Drawable?) {
    val bitmap = remember(drawable) {
        runCatching { drawable?.toBitmap()?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(LauncherDimensions.icon),
            contentScale = ContentScale.Fit,
        )
    } else {
        Icon(
            Icons.Outlined.Apps,
            contentDescription = null,
            modifier = Modifier.size(LauncherDimensions.icon),
        )
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value