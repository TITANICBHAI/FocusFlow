package com.tbtechs.focusflow.ui.defense

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.model.DailyAllowanceEntry
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar

data class BlockPresetUi(
    val id: String,
    val name: String,
    val packages: List<String>,
)

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
    verifyPin: ((String) -> Boolean)? = null,
) {
    if (!visible) return
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var selected by remember(visible, blockedPackages) { mutableStateOf(blockedPackages.toSet()) }
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
    var clearPin by remember { mutableStateOf("") }

    LaunchedEffect(visible) {
        apps = withContext(Dispatchers.IO) {
            runCatching {
                InstalledAppsRepository(context).getInstalledApps()
                    .sortedBy { it.appName.lowercase() }
            }.getOrDefault(emptyList())
        }
    }

    val results = apps.filter {
        search.isBlank() ||
            it.appName.contains(search, true) ||
            it.packageName.contains(search, true)
    }
    val selectedApps = apps.filter { it.packageName in selected }
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

    fun save() {
        if (selected.isEmpty() || until <= System.currentTimeMillis()) return
        onSave(
            selected.toList(),
            until,
            allowances.values.toList(),
            vpn.toList(),
            null,
        )
        onClose()
    }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onClose) { Text("Cancel") }
                Text(if (locked) "🔒 Block Active" else "Block Schedule", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = ::save, enabled = selected.isNotEmpty() && until > System.currentTimeMillis()) {
                    Text("Save")
                }
            }
            if (locked) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Outlined.Lock, contentDescription = null)
                        Text(
                            "The expiry and existing blocked apps are locked. You can still add apps and extend the block.",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = ::chooseDate, enabled = !locked) {
                    Icon(Icons.Outlined.CalendarToday, contentDescription = null)
                    Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(until))
                }
                OutlinedButton(onClick = ::chooseTime, enabled = !locked) {
                    Icon(Icons.Outlined.Timer, contentDescription = null)
                    Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(until))
                }
            }
            if (locked) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Add time:", modifier = Modifier.padding(top = 8.dp))
                    listOf(30L, 60L, 120L, 240L).forEach { minutes ->
                        FilterChip(
                            selected = false,
                            onClick = { until += minutes * 60_000L },
                            label = { Text("+${if (minutes >= 60) "${minutes / 60}h" else "${minutes}m"}") },
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    if (presets.isNotEmpty() || selected.isNotEmpty()) {
                        Text("PRESETS", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge)
                        FlowRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            presets.forEach { preset ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        selected = if (locked) selected + preset.packages else preset.packages.toSet()
                                    },
                                    label = { Text("${preset.name} (${preset.packages.size})") },
                                    trailingIcon = {
                                        IconButton(onClick = { onDeletePreset?.invoke(preset.id) }) {
                                            Icon(Icons.Outlined.Delete, "Delete preset")
                                        }
                                    },
                                )
                            }
                        }
                    }
                    if (selected.isNotEmpty() && !showPresetForm) {
                        TextButton(onClick = { showPresetForm = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("+ Save current selection")
                        }
                    }
                    if (showPresetForm) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(presetName, { presetName = it }, label = { Text("Preset name") }, modifier = Modifier.weight(1f), singleLine = true)
                            Button(
                                onClick = {
                                    if (presetName.isNotBlank()) {
                                        onSavePreset?.invoke(BlockPresetUi(System.currentTimeMillis().toString(), presetName.trim(), selected.toList()))
                                        presetName = ""
                                        showPresetForm = false
                                    }
                                },
                                enabled = presetName.isNotBlank(),
                            ) { Text("Save") }
                        }
                    }
                    OutlinedButton(
                        onClick = { advanced = !advanced },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                        Text(if (advanced) "Hide Advanced" else "Advanced — Add by Package Name")
                    }
                    if (advanced) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                manual,
                                { manual = it },
                                label = { Text("com.example.app") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            Button(
                                onClick = {
                                    val pkg = manual.trim().lowercase()
                                    if (pkg.contains('.')) {
                                        selected = selected + pkg
                                        manual = ""
                                    }
                                },
                                enabled = manual.contains('.'),
                            ) { Text("Add") }
                        }
                    }
                    OutlinedTextField(
                        search,
                        { search = it },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        label = { Text("Search installed apps") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                    )
                    Text(
                        "${selected.size} app${if (selected.size == 1) "" else "s"} will be blocked",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (manualPackages.isNotEmpty()) {
                        manualPackages.forEach { pkg ->
                            AppSelectionRow(pkg, "Manual Entry", pkg in selected) { selected = selected.toggle(pkg, locked) }
                        }
                    }
                }
                items(results, key = { it.packageName }) { app ->
                    if (app.packageName !in manualPackages) {
                        AppSelectionRow(app.packageName, app.appName, app.packageName in selected) {
                            selected = selected.toggle(app.packageName, locked)
                        }
                        if (app.packageName in selected) {
                            AllowanceRow(
                                app = app,
                                entry = allowances[app.packageName],
                                locked = locked,
                                onToggle = {
                                    allowances = if (app.packageName in allowances) {
                                        allowances - app.packageName
                                    } else {
                                        allowances + (app.packageName to DailyAllowanceEntry(app.packageName, 30L * 60_000L))
                                    }
                                },
                                onMinutes = { minutes ->
                                    allowances = allowances + (app.packageName to DailyAllowanceEntry(app.packageName, minutes * 60_000L))
                                },
                                vpnEnabled = app.packageName in vpn,
                                onVpnToggle = { vpn = vpn.toggle(app.packageName, locked) },
                            )
                        }
                    }
                }
                item {
                    if (selected.isNotEmpty() && !locked) {
                        TextButton(
                            onClick = { confirmClear = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null)
                            Text("Clear Block")
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear Block") },
            text = { Text("This disables the standalone block and allows all apps again.") },
            confirmButton = {
                Button(onClick = {
                    confirmClear = false
                    if (verifyPin != null) pinPrompt = true
                    else {
                        onSave(emptyList(), null, allowances.values.toList(), emptyList(), null)
                        onClose()
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
    if (pinPrompt) {
        AlertDialog(
            onDismissRequest = { pinPrompt = false; clearPin = "" },
            title = { Text("Session Password Required") },
            text = {
                OutlinedTextField(
                    clearPin,
                    { clearPin = it },
                    label = { Text("Password") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (verifyPin?.invoke(clearPin) == true) {
                        onSave(emptyList(), null, allowances.values.toList(), emptyList(), clearPin)
                        onClose()
                    }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { pinPrompt = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AppSelectionRow(packageName: String, name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilterChip(selected = selected, onClick = onClick, label = { Text(if (selected) "Blocked" else "Block") })
    }
}

@Composable
private fun AllowanceRow(
    app: InstalledAppInfo,
    entry: DailyAllowanceEntry?,
    locked: Boolean,
    onToggle: () -> Unit,
    onMinutes: (Long) -> Unit,
    vpnEnabled: Boolean,
    onVpnToggle: () -> Unit,
) {
    Column(modifier = Modifier.padding(start = 32.dp, end = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (entry == null) "Add daily allowance" else "Daily allowance: ${entry.dailyAllowanceMs / 60_000L} min/day", style = MaterialTheme.typography.bodySmall)
            Switch(checked = entry != null, onCheckedChange = { onToggle() }, enabled = !locked)
        }
        if (entry != null && !locked) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5L, 15L, 30L, 60L).forEach { minutes ->
                    FilterChip(
                        selected = entry.dailyAllowanceMs == minutes * 60_000L,
                        onClick = { onMinutes(minutes) },
                        label = { Text("${minutes}m") },
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                Icon(Icons.Outlined.Shield, contentDescription = null)
                Text(if (vpnEnabled) "Network block: on" else "Add network block (VPN)", modifier = Modifier.padding(start = 4.dp))
            }
            Switch(checked = vpnEnabled, onCheckedChange = { onVpnToggle() }, enabled = !locked)
        }
    }
}

private fun Set<String>.toggle(value: String, locked: Boolean): Set<String> {
    if (locked && value in this) return this
    return if (value in this) this - value else this + value
}

private fun defaultExpiry(): Long =
    Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
