package com.tbtechs.focusflow.ui.defense

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.model.RecurringBlockSchedule
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GreyoutScheduleModal(
    visible: Boolean,
    windows: List<RecurringBlockSchedule>,
    standaloneActive: Boolean,
    requireDefensePin: ((String, String, () -> Unit) -> Unit)? = null,
    onSave: (List<RecurringBlockSchedule>) -> Unit,
    onClose: () -> Unit,
) {
    if (!visible) return
    val context = LocalContext.current
    var localWindows by remember(visible, windows) { mutableStateOf(windows) }
    var editing by remember { mutableStateOf<ScheduleDraft?>(null) }
    var confirmDelete by remember { mutableStateOf<Int?>(null) }
    var pinPrompt by remember { mutableStateOf<PendingScheduleAction?>(null) }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(modifier = Modifier.padding(bottom = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Block Schedules", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") }
            }
            if (localWindows.isEmpty()) {
                Text(
                    "No batches yet. Add a batch to block selected apps during specific hours and days.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(localWindows) { index, schedule ->
                    ScheduleRow(
                        schedule = schedule,
                        onEdit = {
                            val action = { editing = ScheduleDraft.from(schedule, index) }
                            if (requireDefensePin != null) {
                                requireDefensePin("Edit Block Window", "Enter your defense password to edit this window.", action)
                            } else action()
                        },
                        onDelete = {
                            if (standaloneActive) {
                                pinPrompt = PendingScheduleAction("A standalone block is active; this window cannot be deleted.")
                            } else {
                                val action = { confirmDelete = index }
                                if (requireDefensePin != null) {
                                    requireDefensePin("Delete Block Window", "Enter your defense password to delete this window.", action)
                                } else action()
                            }
                        },
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editing = ScheduleDraft.empty() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("Add Batch")
                }
                Button(onClick = { onSave(localWindows); onClose() }, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }

    editing?.let { draft ->
        ScheduleEditor(
            context = context,
            draft = draft,
            onBack = { editing = null },
            onCommit = { committed ->
                localWindows = if (committed.index == null) {
                    localWindows + committed.toSchedule()
                } else {
                    localWindows.mapIndexed { index, old ->
                        if (index == committed.index) committed.toSchedule() else old
                    }
                }
                editing = null
            },
        )
    }
    confirmDelete?.let { index ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove Window") },
            text = { Text("Remove this scheduled block window?") },
            confirmButton = {
                Button(onClick = {
                    localWindows = localWindows.filterIndexed { itemIndex, _ -> itemIndex != index }
                    confirmDelete = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
    pinPrompt?.let {
        AlertDialog(
            onDismissRequest = { pinPrompt = null },
            title = { Text("Block is Active") },
            text = { Text(it.message) },
            confirmButton = { TextButton(onClick = { pinPrompt = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun ScheduleRow(
    schedule: RecurringBlockSchedule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(schedule.packages.firstOrNull() ?: "(no app)", style = MaterialTheme.typography.titleSmall)
            Text(
                "${schedule.startHour}:00 – ${schedule.endHour}:00 · ${schedule.daysOfWeek.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit") }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete") }
    }
}

private data class PendingScheduleAction(val message: String)

private data class ScheduleDraft(
    val index: Int?,
    val packages: List<String>,
    val appNames: List<String>,
    val query: String,
    val startHour: Int,
    val endHour: Int,
    val days: List<Int>,
    val enabled: Boolean,
) {
    companion object {
        fun empty() = ScheduleDraft(null, emptyList(), emptyList(), "", 9, 18, listOf(1, 2, 3, 4, 5), true)
        fun from(schedule: RecurringBlockSchedule, index: Int) = ScheduleDraft(
            index,
            schedule.packages,
            schedule.packages,
            "",
            schedule.startHour,
            schedule.endHour,
            schedule.daysOfWeek,
            schedule.enabled,
        )
    }

    fun toSchedule() = RecurringBlockSchedule(
        id = "schedule-${index ?: System.currentTimeMillis()}",
        packages = packages,
        startHour = startHour.coerceIn(0, 23),
        endHour = endHour.coerceIn(0, 23),
        daysOfWeek = days.sorted(),
        enabled = enabled,
    )
}

@Composable
private fun ScheduleEditor(
    context: Context,
    draft: ScheduleDraft,
    onBack: () -> Unit,
    onCommit: (ScheduleDraft) -> Unit,
) {
    var current by remember(draft) { mutableStateOf(draft) }
    var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var search by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            runCatching { InstalledAppsRepository(context).getInstalledApps() }.getOrDefault(emptyList())
        }
    }
    val results = apps.filter {
        search.isNotBlank() &&
            (it.appName.contains(search, true) || it.packageName.contains(search, true)) &&
            it.packageName !in current.packages
    }.take(8)

    ModalBottomSheet(onDismissRequest = onBack) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    Text("Back")
                }
                Text(if (current.index == null) "Add Window" else "Edit Window", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search apps by name") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
            )
            current.packages.forEachIndexed { index, pkg ->
                FilterChip(
                    selected = true,
                    onClick = {
                        current = current.copy(
                            packages = current.packages.filterIndexed { packageIndex, _ -> packageIndex != index },
                            appNames = current.appNames.filterIndexed { packageIndex, _ -> packageIndex != index },
                        )
                    },
                    label = { Text(current.appNames.getOrNull(index) ?: pkg) },
                )
            }
            results.forEach { app ->
                TextButton(onClick = {
                    current = current.copy(
                        packages = current.packages + app.packageName,
                        appNames = current.appNames + app.appName,
                    )
                    search = ""
                }) { Text("${app.appName} (${app.packageName})") }
            }
            Text("Start hour: ${current.startHour}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { current = current.copy(startHour = (current.startHour + 23) % 24) }) { Text("−") }
                TextButton(onClick = { current = current.copy(startHour = (current.startHour + 1) % 24) }) { Text("+") }
                Text("End hour: ${current.endHour}", modifier = Modifier.padding(start = 16.dp))
                TextButton(onClick = { current = current.copy(endHour = (current.endHour + 23) % 24) }) { Text("−") }
                TextButton(onClick = { current = current.copy(endHour = (current.endHour + 1) % 24) }) { Text("+") }
            }
            Text("Active days")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { index, day ->
                    FilterChip(
                        selected = index in current.days,
                        onClick = {
                            current = current.copy(
                                days = if (index in current.days) current.days - index else current.days + index,
                            )
                        },
                        label = { Text(day) },
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Enabled")
                Switch(checked = current.enabled, onCheckedChange = { current = current.copy(enabled = it) })
            }
            Button(
                onClick = { if (current.packages.isNotEmpty() && current.days.isNotEmpty()) onCommit(current) },
                enabled = current.packages.isNotEmpty() && current.days.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (current.index == null) "Add Window" else "Update Window") }
        }
    }
}
