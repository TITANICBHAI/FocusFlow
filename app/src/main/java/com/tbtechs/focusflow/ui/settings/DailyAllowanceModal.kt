package com.tbtechs.focusflow.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tbtechs.focusflow.data.model.DailyAllowanceEntry
import com.tbtechs.focusflow.data.repository.AllowanceUsage
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.missingInstalledAppInfo
import com.tbtechs.focusflow.ui.launcher.AppIcon
import com.tbtechs.focusflow.ui.common.rememberInstalledApps
import com.tbtechs.focusflow.ui.home.FocusFlowModalCard
import com.tbtechs.focusflow.ui.home.FocusFlowModalField
import com.tbtechs.focusflow.ui.home.FocusFlowPrimaryButton
import com.tbtechs.focusflow.ui.home.FocusFlowSecondaryButton
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.InfoBodyText
import com.tbtechs.focusflow.ui.theme.InfoSurface
import com.tbtechs.focusflow.ui.theme.scaledSp
import org.json.JSONArray

private val SunAmber = Color(0xFFF59E0B)

/**
 * Per-app daily allowance editor.
 *
 * Implements the reference collapsed and expanded states with theme-aware
 * styling, expandable mode configurations, and custom steppers.
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
                    "time_budget" -> AllowanceMode.TimeBudget
                    else -> AllowanceMode.Count
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
    var manualPackageDialogVisible by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<AllowanceMessage?>(null) }
    var pendingRemoval by remember { mutableStateOf<PendingAllowanceRemoval?>(null) }
    var pin by remember { mutableStateOf("") }
    val context = LocalContext.current
    val installedAppsRepository = remember { InstalledAppsRepository(context) }
    val installedAppsState = rememberInstalledApps(installedAppsRepository)
    val installedApps = installedAppsState.apps
    val appsLoading = installedAppsState.loading

    fun updateDraft(packageName: String, transform: (DailyAllowanceDraft) -> DailyAllowanceDraft) {
        drafts = drafts.map { draft -> if (draft.packageName == packageName) transform(draft) else draft }
    }

    fun remove(packageName: String) {
        drafts = drafts.filterNot { it.packageName == packageName }
        if (expandedPackage == packageName) expandedPackage = null
    }

    fun requestRemoval(packageName: String) {
        if (locked && packageName in originalPackages) {
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
            drafts = drafts.filter { it.packageName in originalPackages }
            expandedPackage = null
        } else if (requireDefensePin) {
            pendingRemoval = PendingAllowanceRemoval.All
            pin = ""
        } else {
            drafts = emptyList()
            expandedPackage = null
        }
    }

    fun addPackage(): Boolean {
        val packageName = packageDraft.trim()
        return when {
            packageName.isEmpty() -> false
            !PACKAGE_NAME.matches(packageName) -> {
                message = AllowanceMessage("Enter a package name", "Use an Android package name such as com.example.app.")
                false
            }
            drafts.any { it.packageName == packageName } -> {
                expandedPackage = packageName
                packageDraft = ""
                true
            }
            else -> {
                drafts = drafts + DailyAllowanceDraft(packageName = packageName)
                expandedPackage = packageName
                packageDraft = ""
                true
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

    val shownApps = remember(installedApps, drafts, search) {
        val query = search.trim().lowercase()
        val manualApps = drafts
            .filter { draft -> installedApps.none { it.packageName == draft.packageName } }
            .map { draft ->
                missingInstalledAppInfo(
                    packageName = draft.packageName,
                    label = when {
                        appsLoading -> "Checking app"
                        installedAppsState.error != null -> "App details unavailable"
                        else -> "App not installed"
                    },
                )
            }
        (installedApps + manualApps)
            .distinctBy { it.packageName }
            .filter {
                query.isEmpty() ||
                    it.appName.lowercase().contains(query) ||
                    it.packageName.lowercase().contains(query)
            }
    }

    fun entryIsLocked(packageName: String): Boolean =
        locked && packageName in originalPackages

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) {
                    Text("Cancel", color = DarkTextSecondary, fontSize = 13.scaledSp)
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = SunAmber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Daily Allowance", fontSize = 15.scaledSp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                }
                TextButton(onClick = ::save) {
                    Text("Save", color = BrandPrimary, fontWeight = FontWeight.Bold, fontSize = 13.scaledSp)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (locked) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF5DF))
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(Icons.Outlined.Security, contentDescription = null, tint = SunAmber, modifier = Modifier.size(14.dp))
                            Text(
                                "Block is active — existing allowances are locked. You can add new apps but cannot remove them until the block expires.",
                                modifier = Modifier.weight(1f),
                                fontSize = 11.scaledSp,
                                lineHeight = 16.scaledSp,
                                color = SunAmber,
                            )
                        }
                    }
                }
            }

            if (requireDefensePin && !locked) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(InfoSurface)
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(Icons.Outlined.Security, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(14.dp))
                            Text(
                                "Removing apps from the allowance list requires your defense password.",
                                modifier = Modifier.weight(1f),
                                fontSize = 11.scaledSp,
                                lineHeight = 16.scaledSp,
                                color = InfoBodyText,
                            )
                        }
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF5DF))
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = SunAmber, modifier = Modifier.size(14.dp))
                        Text(
                            "Allowances count all usage today, not just during blocks. Tap an app to enable its allowance, tap again to expand its settings, or long-press to remove it.",
                            fontSize = 11.scaledSp,
                            lineHeight = 16.scaledSp,
                            color = SunAmber,
                        )
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp),
                        singleLine = true,
                        placeholder = { Text("Search apps...", color = DarkTextMuted, fontSize = 13.scaledSp) },
                        leadingIcon = {
                             Icon(Icons.Outlined.Search, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            if (search.isNotBlank()) {
                                IconButton(onClick = { search = "" }) {
                                    Icon(Icons.Outlined.Clear, contentDescription = "Clear", tint = DarkTextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkCard,
                            unfocusedContainerColor = DarkCard,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = DarkTextPrimary,
                            unfocusedTextColor = DarkTextPrimary,
                        ),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (drafts.isEmpty()) "No allowances configured"
                        else "${drafts.size} app${if (drafts.size == 1) "" else "s"} with daily allowance",
                        fontSize = 13.scaledSp,
                        fontWeight = FontWeight.Medium,
                        color = DarkTextSecondary,
                    )
                    TextButton(onClick = { packageDraft = ""; manualPackageDialogVisible = true }) {
                        Text("Add package", color = BrandPrimary, fontSize = 12.scaledSp)
                    }
                }
            }

            if (appsLoading && installedApps.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = BrandPrimary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Loading apps…", color = DarkTextSecondary, fontSize = 13.scaledSp)
                    }
                }
            }

            if (!appsLoading && shownApps.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (search.isBlank()) "No apps found" else "No apps match \"$search\"",
                            color = DarkTextSecondary,
                            fontSize = 14.scaledSp,
                        )
                    }
                }
            }

            items(shownApps, key = { app -> app.packageName }) { app ->
                val draft = drafts.firstOrNull { it.packageName == app.packageName }
                val isActive = draft != null
                val isExpanded = expandedPackage == app.packageName
                val isEntryLocked = locked && app.packageName in originalPackages

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkCard)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (draft == null) {
                                        drafts = drafts + DailyAllowanceDraft(
                                            packageName = app.packageName,
                                            mode = AllowanceMode.Count,
                                        )
                                        expandedPackage = app.packageName
                                    } else {
                                        expandedPackage = if (isExpanded) null else app.packageName
                                    }
                                },
                                onLongClick = { if (isActive) requestRemoval(app.packageName) },
                            )
                             .heightIn(min = 64.dp)
                             .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                             horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(SunAmber),
                            )
                        } else {
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                         AppIcon(app.icon, size = 40.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                 fontSize = 13.scaledSp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = app.packageName,
                                 fontSize = 11.scaledSp,
                                color = DarkTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (draft != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.WbSunny,
                                        contentDescription = null,
                                        tint = SunAmber,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = draft.summary(),
                                 fontSize = 11.scaledSp,
                                        fontWeight = FontWeight.Medium,
                                        color = SunAmber,
                                    )
                                }
                            }
                        }

                        if (isActive) {
                            IconButton(onClick = { expandedPackage = if (isExpanded) null else app.packageName }) {
                                Icon(
                                    if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = DarkTextSecondary,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                 .size(32.dp)
                                 .clip(RoundedCornerShape(8.dp))
                                .background(if (isActive) SunAmber.copy(alpha = 0.14f) else DarkSurfaceVariant.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.WbSunny,
                                contentDescription = if (isActive) "Allowance enabled" else "Enable allowance",
                                tint = if (isActive) SunAmber else DarkTextMuted,
                                 modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    if (isExpanded && draft != null) {
                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.6f))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            AllowanceConfiguration(
                                draft = draft,
                                locked = isEntryLocked,
                                onUpdate = { updated -> updateDraft(draft.packageName) { updated } },
                            )
                        }
                    }
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.45f))
                }
            }

            if (appsLoading && installedApps.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            color = BrandPrimary,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Loading more apps…", color = DarkTextMuted, fontSize = 11.scaledSp)
                    }
                }
            }

            if (drafts.any { !entryIsLocked(it.packageName) }) {
                item {
                    OutlinedButton(
                        onClick = ::requestClear,
                         modifier = Modifier.fillMaxWidth().height(44.dp),
                         shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                    ) {
                        Text(
                            if (locked) "Clear new allowances" else "Clear all daily allowances",
                             fontSize = 13.scaledSp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

        }
    }

    if (manualPackageDialogVisible) {
        Dialog(
            onDismissRequest = { manualPackageDialogVisible = false; packageDraft = "" },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            FocusFlowModalCard(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                radius = 16.dp,
                contentPadding = 16.dp,
            ) {
                Text("Add package manually", fontSize = 18.scaledSp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                FocusFlowModalField(
                    value = packageDraft,
                    onValueChange = { packageDraft = it },
                    placeholder = "com.example.app",
                    minHeight = 44.dp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FocusFlowSecondaryButton(
                        text = "Cancel",
                        onClick = { manualPackageDialogVisible = false; packageDraft = "" },
                        modifier = Modifier.weight(1f),
                    )
                    FocusFlowPrimaryButton(
                        text = "Add",
                    onClick = {
                        if (addPackage()) {
                            manualPackageDialogVisible = false
                        }
                    },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    pendingRemoval?.let { pending ->
        Dialog(
            onDismissRequest = { pendingRemoval = null; pin = "" },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            FocusFlowModalCard(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                radius = 16.dp,
                contentPadding = 16.dp,
            ) {
                Text("Defense password required", fontSize = 18.scaledSp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (pending is PendingAllowanceRemoval.All) {
                            "Enter your defense password to remove all apps from the daily allowance list."
                        } else {
                            "Enter your defense password to remove this app from the daily allowance list."
                        },
                        fontSize = 11.scaledSp,
                        color = DarkTextSecondary,
                    )
                    FocusFlowModalField(
                        value = pin,
                        onValueChange = { pin = it },
                        placeholder = "Defense password",
                        minHeight = 44.dp,
                        secure = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FocusFlowSecondaryButton(
                        text = "Cancel",
                        onClick = { pendingRemoval = null; pin = "" },
                        modifier = Modifier.weight(1f),
                    )
                    FocusFlowPrimaryButton(
                        text = "Remove",
                    onClick = {
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
                    },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    message?.let { notice ->
        Dialog(
            onDismissRequest = { message = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            FocusFlowModalCard(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                radius = 16.dp,
                contentPadding = 16.dp,
            ) {
                Text(notice.title, fontSize = 18.scaledSp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                Text(notice.body, fontSize = 11.scaledSp, lineHeight = 17.scaledSp, color = DarkTextSecondary, modifier = Modifier.padding(top = 8.dp))
                FocusFlowPrimaryButton(
                    text = "OK",
                    onClick = { message = null },
                    modifier = Modifier.padding(top = 12.dp),
                )
                }
            }
    }

}

@Composable
private fun AllowanceConfiguration(
    draft: DailyAllowanceDraft,
    locked: Boolean,
    onUpdate: (DailyAllowanceDraft) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (locked) {
            Text(
                "Values locked while block is active",
                fontSize = 12.scaledSp,
                color = SunAmber,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "ALLOWANCE MODE",
                fontSize = 11.scaledSp,
                fontWeight = FontWeight.SemiBold,
                color = DarkTextMuted,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                AllowanceMode.values().forEach { mode ->
                    val isSelected = draft.mode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) SunAmber else DarkBorder.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(10.dp),
                            )
                            .combinedClickable(
                                enabled = !locked,
                                onClick = { onUpdate(draft.copy(mode = mode)) },
                            )
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = when (mode) {
                                    AllowanceMode.Count -> Icons.Outlined.Fingerprint
                                    AllowanceMode.TimeBudget -> Icons.Outlined.HourglassEmpty
                                    AllowanceMode.Interval -> Icons.Outlined.Schedule
                                },
                                contentDescription = null,
                                tint = if (isSelected) SunAmber else DarkTextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = mode.label,
                                fontSize = 11.scaledSp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) SunAmber else DarkTextSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        when (draft.mode) {
            AllowanceMode.Count -> {
                StepperRow(
                    label = "Opens per day",
                    value = draft.countPerDay.toString(),
                    locked = locked,
                    onDecrease = { onUpdate(draft.copy(countPerDay = (draft.countPerDay - 1).coerceAtLeast(1))) },
                    onIncrease = { onUpdate(draft.copy(countPerDay = (draft.countPerDay + 1).coerceAtMost(20))) },
                )
            }
            AllowanceMode.TimeBudget -> {
                StepperRow(
                    label = "Total minutes per day",
                    value = "${draft.budgetMinutes} min",
                    locked = locked,
                    onDecrease = { onUpdate(draft.copy(budgetMinutes = (draft.budgetMinutes - 5).coerceAtLeast(1))) },
                    onIncrease = { onUpdate(draft.copy(budgetMinutes = (draft.budgetMinutes + 5).coerceAtMost(480))) },
                )
            }
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
                    fontSize = 12.scaledSp,
                    color = DarkTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    locked: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 13.scaledSp,
            color = DarkTextSecondary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .combinedClickable(enabled = !locked, onClick = onDecrease),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Remove, contentDescription = "Decrease", tint = if (locked) DarkTextMuted else DarkTextPrimary, modifier = Modifier.size(16.dp))
            }
            Text(
                text = value,
                fontSize = 13.scaledSp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary,
                modifier = Modifier.width(52.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .combinedClickable(enabled = !locked, onClick = onIncrease),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Increase", tint = if (locked) DarkTextMuted else DarkTextPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private enum class AllowanceMode(val label: String) {
    Count("Count"),
    TimeBudget("Time Budget"),
    Interval("Interval"),
}

private data class DailyAllowanceDraft(
    val packageName: String,
    val mode: AllowanceMode = AllowanceMode.Count,
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
            dailyAllowanceMs = value.optLong("dailyAllowanceMs", 0L),
            mode = value.optString("mode", "count"),
            countPerDay = value.optInt("countPerDay", 1).coerceAtLeast(1),
            budgetMinutes = value.optInt("budgetMinutes", 30).coerceAtLeast(1),
            intervalMinutes = value.optInt("intervalMinutes", 5).coerceAtLeast(1),
            intervalHours = value.optInt("intervalHours", 1).coerceAtLeast(1),
        )
    }
}.getOrDefault(emptyList())
