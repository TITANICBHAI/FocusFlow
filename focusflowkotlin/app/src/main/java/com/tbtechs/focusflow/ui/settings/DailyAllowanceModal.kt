package com.tbtechs.focusflow.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.tbtechs.focusflow.data.model.DailyAllowanceEntry
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.AllowanceUsage
import com.tbtechs.focusflow.ui.launcher.AppPickerSheet
import org.json.JSONArray
import java.time.LocalDate

/**
 * Per-app daily allowance editor.
 *
     * The JSON contract is shared with the accessibility and fallback services,
     * so mode/count/interval values are persisted without converting them into a
     * different kind of allowance.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DailyAllowanceModal(
    visible: Boolean,
    selectedEntries: List<DailyAllowanceEntry>,
    locked: Boolean = false,
    requireDefensePin: Boolean = false,
    onSave: (List<DailyAllowanceEntry>) -> Unit,
    onVerifyDefensePin: (String) -> Boolean,
    onClose: () -> Unit,
    usageByPackage: Map<String, AllowanceUsage> = emptyMap(),
) {
    if (!visible) return

    val initialDrafts = remember(selectedEntries) {
        selectedEntries.map { entry ->
            DailyAllowanceDraft(
                packageName = entry.packageName,
                mode = when (entry.mode) {
                    "count" -> AllowanceMode.Count
                    "interval" -> AllowanceMode.Interval
                    else -> AllowanceMode.TimeBudget
                },
                countPerDay = entry.countPerDay,
                budgetMinutes = entry.budgetMinutes.coerceAtLeast(1),
                intervalMinutes = entry.intervalMinutes.coerceAtLeast(1),
                intervalHours = entry.intervalHours.coerceAtLeast(1),
            )
        }
    }
    var drafts by remember(selectedEntries) { mutableStateOf(initialDrafts) }
    val originalPackages = remember(selectedEntries) { selectedEntries.mapTo(mutableSetOf()) { it.packageName } }
    var expandedPackage by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var packageDraft by remember { mutableStateOf("") }
    var pickerVisible by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<AllowanceMessage?>(null) }
    var pendingRemoval by remember { mutableStateOf<PendingAllowanceRemoval?>(null) }
    var pin by remember { mutableStateOf("") }

    fun updateDraft(packageName: String, transform: (DailyAllowanceDraft) -> DailyAllowanceDraft) {
        drafts = drafts.map { draft -> if (draft.packageName == packageName) transform(draft) else draft }
    }

    fun remove(packageName: String) {
        drafts = drafts.filterNot { it.packageName == packageName }
        if (expandedPackage == packageName) expandedPackage = null
    }

    fun requestRemoval(packageName: String) {
        if (locked) {
            message = AllowanceMessage("Allowances locked", "A block is active, so allowances cannot be removed until it expires.")
        } else if (requireDefensePin) {
            pendingRemoval = PendingAllowanceRemoval.Package(packageName)
            pin = ""
        } else {
            remove(packageName)
        }
    }

    fun requestClear() {
        if (locked) {
            message = AllowanceMessage("Allowances locked", "A block is active, so allowances cannot be removed until it expires.")
        } else if (requireDefensePin) {
            pendingRemoval = PendingAllowanceRemoval.All
            pin = ""
        } else {
            drafts = emptyList()
            expandedPackage = null
        }
    }

    fun addPackage() {
        val packageName = packageDraft.trim()
        when {
            packageName.isEmpty() -> Unit
            !PACKAGE_NAME.matches(packageName) -> {
                message = AllowanceMessage("Enter a package name", "Use an Android package name such as com.example.app.")
            }
            drafts.any { it.packageName == packageName } -> {
                expandedPackage = packageName
                packageDraft = ""
            }
            else -> {
                drafts = drafts + DailyAllowanceDraft(packageName = packageName)
                expandedPackage = packageName
                packageDraft = ""
            }
        }
    }

    fun save() {
        onSave(
            drafts.map {
                DailyAllowanceEntry(
                    packageName = it.packageName,
                    dailyAllowanceMs = when (it.mode) {
                        AllowanceMode.TimeBudget -> it.budgetMinutes.coerceAtLeast(1).toLong() * MINUTE_MS
                        AllowanceMode.Interval -> it.intervalMinutes.coerceAtLeast(1).toLong() * MINUTE_MS
                        AllowanceMode.Count -> 0L
                    },
                    mode = when (it.mode) {
                        AllowanceMode.Count -> "count"
                        AllowanceMode.Interval -> "interval"
                        AllowanceMode.TimeBudget -> "time_budget"
                    },
                    countPerDay = it.countPerDay.coerceAtLeast(1),
                    budgetMinutes = it.budgetMinutes.coerceAtLeast(1),
                    intervalMinutes = it.intervalMinutes.coerceAtLeast(1),
                    intervalHours = it.intervalHours.coerceAtLeast(1),
                )
            },
        )
        onClose()
    }

    val shownDrafts = remember(drafts, search) {
        val query = search.trim().lowercase()
        if (query.isEmpty()) drafts else drafts.filter { it.packageName.lowercase().contains(query) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Allowance") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Cancel") } },
                actions = { TextButton(onClick = ::save) { Text("Save") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            if (locked) {
                item {
                    AllowanceNotice("Block is active — existing allowances are locked. You can add apps, but cannot remove allowances until the block expires.")
                }
            }
            if (requireDefensePin && !locked) {
                item {
                    AllowanceNotice("Removing apps from the allowance list requires your defense password.")
                }
            }
            item {
                AllowanceNotice("Tap an app to expand its mode settings. Long-press or use Remove to remove it.")
            }
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search configured apps") },
                    singleLine = true,
                )
            }
            item {
                Text(
                    text = if (drafts.isEmpty()) {
                        "No apps have a daily allowance — add one below."
                    } else {
                        "${drafts.size} app${if (drafts.size == 1) "" else "s"} with daily allowance"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = packageDraft,
                        onValueChange = { packageDraft = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Add app package name") },
                        supportingText = { Text("For example, com.example.app") },
                        singleLine = true,
                    )
                    Button(onClick = ::addPackage, enabled = packageDraft.trim().isNotEmpty()) { Text("Add") }
                }
                OutlinedButton(onClick = { pickerVisible = true }) {
                    Text("Choose installed apps")
                }
            }
            if (shownDrafts.isEmpty()) {
                item {
                    Text(
                        text = if (search.isBlank()) "No configured apps yet." else "No configured apps match your search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(shownDrafts, key = { draft -> draft.packageName }) { draft ->
                val isExpanded = expandedPackage == draft.packageName
                val isEntryLocked = locked && draft.packageName in originalPackages
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                expandedPackage = if (isExpanded) null else draft.packageName
                            },
                            onLongClick = { requestRemoval(draft.packageName) },
                        ),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(draft.packageName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    draft.summary(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { expandedPackage = if (isExpanded) null else draft.packageName }) {
                                Text(if (isExpanded) "Collapse" else "Expand")
                            }
                            TextButton(
                                enabled = !locked,
                                onClick = { requestRemoval(draft.packageName) },
                            ) { Text("Remove") }
                        }
                        if (isExpanded) {
                            AllowanceConfiguration(
                                draft = draft,
                                locked = isEntryLocked,
                                usage = usageByPackage[draft.packageName],
                                onUpdate = { updated -> updateDraft(draft.packageName) { updated } },
                            )
                        }
                    }
                }
            }
            if (drafts.isNotEmpty()) {
                item {
                    OutlinedButton(onClick = ::requestClear, modifier = Modifier.fillMaxWidth()) {
                        Text(if (locked) "Clear locked allowances" else "Clear all daily allowances")
                    }
                }
            }
        }
    }

    pendingRemoval?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null; pin = "" },
            title = { Text("Defense password required") },
            text = {
                Column {
                    Text(
                        if (pending is PendingAllowanceRemoval.All) {
                            "Enter your defense password to remove all apps from the daily allowance list."
                        } else {
                            "Enter your defense password to remove this app from the daily allowance list."
                        },
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("Defense password") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (!onVerifyDefensePin(pin)) {
                        message = AllowanceMessage("Incorrect password", "The defense password did not match.")
                    } else {
                        when (pending) {
                            is PendingAllowanceRemoval.Package -> remove(pending.packageName)
                            PendingAllowanceRemoval.All -> {
                                drafts = emptyList()
                                expandedPackage = null
                            }
                        }
                        pendingRemoval = null
                        pin = ""
                    }
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null; pin = "" }) { Text("Cancel") }
            },
        )
    }

    message?.let { notice ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(notice.title) },
            text = { Text(notice.body) },
            confirmButton = { Button(onClick = { message = null }) { Text("OK") } },
        )
    }

    if (pickerVisible) {
        val repository = remember { InstalledAppsRepository(LocalContext.current) }
        AppPickerSheet(
            visible = true,
            title = "Choose allowance apps",
            initialSelected = drafts.map { it.packageName },
            noneWhenEmpty = true,
            presets = emptyList(),
            installedAppsRepository = repository,
            onSave = { selected ->
                val selectedPackages = selected.filter { it.isNotBlank() }.toSet()
                drafts = drafts + selectedPackages
                    .filterNot { packageName -> drafts.any { it.packageName == packageName } }
                    .map { packageName -> DailyAllowanceDraft(packageName = packageName) }
                pickerVisible = false
            },
            onSavePreset = {},
            onDeletePreset = {},
            onClose = { pickerVisible = false },
        )
    }
}

@Composable
private fun AllowanceConfiguration(
    draft: DailyAllowanceDraft,
    locked: Boolean,
    usage: AllowanceUsage?,
    onUpdate: (DailyAllowanceDraft) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (locked) {
            Text(
                "Values locked while block is active",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            allowanceUsageLabel(draft, usage),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Allowance mode", style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.fillMaxWidth()) {
            AllowanceMode.values().forEach { mode ->
                OutlinedButton(
                    enabled = !locked,
                    onClick = { onUpdate(draft.copy(mode = mode)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (draft.mode == mode) "${mode.label} selected" else mode.label)
                }
            }
        }
        when (draft.mode) {
            AllowanceMode.Count -> StepperRow(
                label = "Opens per day",
                value = draft.countPerDay.toString(),
                locked = locked,
                onDecrease = { onUpdate(draft.copy(countPerDay = (draft.countPerDay - 1).coerceAtLeast(1))) },
                onIncrease = { onUpdate(draft.copy(countPerDay = (draft.countPerDay + 1).coerceAtMost(20))) },
            )
            AllowanceMode.TimeBudget -> StepperRow(
                label = "Total minutes per day",
                value = "${draft.budgetMinutes} min",
                locked = locked,
                onDecrease = { onUpdate(draft.copy(budgetMinutes = (draft.budgetMinutes - 5).coerceAtLeast(1))) },
                onIncrease = { onUpdate(draft.copy(budgetMinutes = (draft.budgetMinutes + 5).coerceAtMost(480))) },
            )
            AllowanceMode.Interval -> {
                StepperRow(
                    label = "Minutes allowed per window",
                    value = "${draft.intervalMinutes} min",
                    locked = locked,
                    onDecrease = { onUpdate(draft.copy(intervalMinutes = (draft.intervalMinutes - 1).coerceAtLeast(1))) },
                    onIncrease = { onUpdate(draft.copy(intervalMinutes = (draft.intervalMinutes + 1).coerceAtMost(120))) },
                )
                StepperRow(
                    label = "Window size (hours)",
                    value = "${draft.intervalHours} hr",
                    locked = locked,
                    onDecrease = { onUpdate(draft.copy(intervalHours = (draft.intervalHours - 1).coerceAtLeast(1))) },
                    onIncrease = { onUpdate(draft.copy(intervalHours = (draft.intervalHours + 1).coerceAtMost(24))) },
                )
                Text(
                    "App is allowed for ${draft.intervalMinutes} min every ${draft.intervalHours} hour${if (draft.intervalHours == 1) "" else "s"}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun allowanceUsageLabel(
    draft: DailyAllowanceDraft,
    usage: AllowanceUsage?,
): String {
    if (usage == null) return "No usage recorded in the current allowance window."

    val today = LocalDate.now().toString()
    return when (draft.mode) {
        AllowanceMode.Count -> {
            val count = if (usage.date == today) usage.count else 0
            "Used today: $count of ${draft.countPerDay} opens"
        }
        AllowanceMode.TimeBudget -> {
            val usedMs = if (usage.date == today) usage.usedMs else 0L
            "Used today: ${formatAllowanceMinutes(usedMs)} of ${draft.budgetMinutes} min"
        }
        AllowanceMode.Interval -> {
            val windowEnd = usage.windowStartMs +
                draft.intervalHours.coerceAtLeast(1).toLong() * 60L * MINUTE_MS
            val usedMs = if (usage.windowStartMs > 0L && System.currentTimeMillis() < windowEnd) {
                usage.usedMs
            } else {
                0L
            }
            "Used in current window: ${formatAllowanceMinutes(usedMs)} of ${draft.intervalMinutes} min"
        }
    }
}

private fun formatAllowanceMinutes(usedMs: Long): String {
    val minutes = (usedMs / MINUTE_MS).toInt()
    return if (minutes == 0 && usedMs > 0L) "<1 min" else "$minutes min"
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    locked: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(enabled = !locked, onClick = onDecrease) { Text("−") }
        Text(value, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(enabled = !locked, onClick = onIncrease) { Text("+") }
    }
}

@Composable
private fun AllowanceNotice(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class AllowanceMode(val label: String) {
    Count("Count"),
    TimeBudget("Time Budget"),
    Interval("Interval"),
}

private data class DailyAllowanceDraft(
    val packageName: String,
    val mode: AllowanceMode = AllowanceMode.TimeBudget,
    val countPerDay: Int = 1,
    val budgetMinutes: Int = 30,
    val intervalMinutes: Int = 5,
    val intervalHours: Int = 1,
) {
    fun summary(): String = when (mode) {
        AllowanceMode.Count -> "$countPerDay open${if (countPerDay == 1) "" else "s"}/day"
        AllowanceMode.TimeBudget -> "$budgetMinutes min/day"
        AllowanceMode.Interval -> "$intervalMinutes min every ${intervalHours}hr"
    }
}

private sealed interface PendingAllowanceRemoval {
    data class Package(val packageName: String) : PendingAllowanceRemoval
    object All : PendingAllowanceRemoval
}

private data class AllowanceMessage(val title: String, val body: String)

private const val MINUTE_MS = 60_000L
private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

internal fun dailyAllowanceEntriesFromJson(raw: String?): List<DailyAllowanceEntry> = runCatching {
    val values = JSONArray(raw ?: "[]")
    List(values.length()) { index ->
        val value = values.getJSONObject(index)
        DailyAllowanceEntry(
            packageName = value.getString("package"),
            dailyAllowanceMs = value.getLong("dailyAllowanceMs"),
        )
    }
}.getOrDefault(emptyList())
