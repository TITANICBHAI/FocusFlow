package com.tbtechs.focusflow.ui.defense

import android.content.Context
import android.net.VpnService
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tbtechs.focusflow.data.model.RecurringBlockSchedule
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.ui.common.InstalledAppsLoadState
import com.tbtechs.focusflow.ui.common.rememberInstalledApps
import com.tbtechs.focusflow.ui.common.resolve
import com.tbtechs.focusflow.ui.launcher.AppIcon
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.ui.alwayson.VpnConsentModal
import com.tbtechs.focusflow.ui.alwayson.VpnConsentPolicy
import com.tbtechs.focusflow.ui.alwayson.rememberVpnConsentRequester
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.scaledSp
import com.tbtechs.focusflow.ui.common.FocusFlowSwitch
import kotlinx.coroutines.launch

/**
 * Block schedules modal and editor.
 *
 * Implements screenshots 3e_8 and 3e_9.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GreyoutScheduleModal(
    visible: Boolean,
    windows: List<RecurringBlockSchedule>,
    standaloneActive: Boolean,
    requireDefensePin: ((String, String, () -> Unit) -> Unit)? = null,
    vpnRepository: VpnRepository? = null,
    onNetworkProtectionRequired: () -> Unit = {},
    onSave: (List<RecurringBlockSchedule>) -> Unit,
    onClose: () -> Unit,
) {
    if (!visible) return
    val dimensions = LocalFocusFlowDimensions.current
    val context = LocalContext.current
    var localWindows by remember(visible, windows) { mutableStateOf(windows) }
    var editing by remember { mutableStateOf<ScheduleDraft?>(null) }
    var confirmDelete by remember { mutableStateOf<Int?>(null) }
    var pinPrompt by remember { mutableStateOf<PendingScheduleAction?>(null) }
    val installedAppsState = rememberInstalledApps(
        remember(context) { InstalledAppsRepository(context) },
    )
    val apps = installedAppsState.apps
    val appInfoByPackage = remember(
        installedAppsState.apps,
        installedAppsState.loading,
        installedAppsState.error,
        localWindows,
    ) {
        val resolved = installedAppsState.apps.associateBy { it.packageName }.toMutableMap()
        localWindows.flatMap { it.packages }.distinct().forEach { packageName ->
            installedAppsState.resolve(packageName)?.let { resolved[packageName] = it }
        }
        resolved
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
            ) {
                // Header matching 3e_8
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onClose) {
                        Text("Cancel", color = DarkTextSecondary, fontSize = 14.scaledSp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Block Schedules",
                            fontSize = 17.scaledSp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary,
                        )
                    }
                    TextButton(onClick = { onSave(localWindows); onClose() }) {
                        Text(
                            text = "Save",
                            color = BrandPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.scaledSp,
                        )
                    }
                }

                // Info Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                ) {
                    Text(
                        text = "Scheduled blocks activate automatically at specified hours and days. Configure which apps to block for each window.",
                        fontSize = 12.scaledSp,
                        lineHeight = 16.scaledSp,
                        color = DarkTextSecondary,
                    )
                }

                if (localWindows.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = DarkTextMuted,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No schedules yet",
                            fontSize = 15.scaledSp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkTextPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Create recurring time windows to automatically restrict apps.",
                            fontSize = 12.scaledSp,
                            color = DarkTextMuted,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(localWindows) { index, schedule ->
                            ScheduleCard(
                                schedule = schedule,
                                appInfoByPackage = appInfoByPackage,
                                loading = installedAppsState.loading,
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
                }

                // Add Batch / Window button
                OutlinedButton(
                    onClick = { editing = ScheduleDraft.empty() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandPrimary),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Schedule Window", fontSize = 14.scaledSp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    editing?.let { draft ->
        ScheduleEditor(
            context = context,
            draft = draft,
            catalogState = installedAppsState,
            requireDefensePin = requireDefensePin,
            vpnRepository = vpnRepository,
            onNetworkProtectionRequired = onNetworkProtectionRequired,
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
            shape = RoundedCornerShape(24.dp),
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Remove Window", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove this scheduled block window?", fontSize = 13.scaledSp, lineHeight = 18.scaledSp) },
            confirmButton = {
                Button(
                    onClick = {
                        localWindows = localWindows.filterIndexed { itemIndex, _ -> itemIndex != index }
                        confirmDelete = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = null },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    pinPrompt?.let {
        AlertDialog(
            onDismissRequest = { pinPrompt = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Block is Active", fontWeight = FontWeight.Bold) },
            text = { Text(it.message, fontSize = 13.scaledSp, lineHeight = 18.scaledSp) },
            confirmButton = {
                Button(
                    onClick = { pinPrompt = null },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: RecurringBlockSchedule,
    appInfoByPackage: Map<String, InstalledAppInfo>,
    loading: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val firstPackage = schedule.packages.firstOrNull()
            val firstApp = firstPackage?.let(appInfoByPackage::get)
            if (firstPackage != null) {
                AppIcon(firstApp?.icon, size = 30.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                val appLabel = when {
                    firstPackage == null -> "(no app)"
                    firstApp != null -> firstApp.appName
                    loading -> "Loading app details…"
                    else -> "App details unavailable"
                }
                Text(
                    text = if (schedule.packages.size > 1) "$appLabel +${schedule.packages.size - 1} more" else appLabel,
                    fontSize = 15.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (firstPackage != null && (firstApp == null || !firstApp.isInstalled)) {
                    Text(
                        text = firstPackage,
                        fontSize = 10.scaledSp,
                        color = DarkTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.AccessTime,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "${schedule.startHour.toString().padStart(2, '0')}:${schedule.startMinute.toString().padStart(2, '0')} – " +
                            "${schedule.endHour.toString().padStart(2, '0')}:${schedule.endMinute.toString().padStart(2, '0')}",
                        fontSize = 13.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = schedule.daysOfWeek.toScheduleDayLabel(),
                        fontSize = 12.scaledSp,
                        color = DarkTextSecondary,
                    )
                    if (schedule.vpnEnabled) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF064E3B))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text("VPN", color = Color(0xFF34D399), fontSize = 10.scaledSp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (!schedule.enabled) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text("Disabled", color = DarkTextMuted, fontSize = 10.scaledSp)
                        }
                    }
                }
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = DarkTextSecondary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
            }
        }
    }
}

private data class PendingScheduleAction(val message: String)

private data class ScheduleDraft(
    val index: Int?,
    val packages: List<String>,
    val appNames: List<String>,
    val query: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val days: List<Int>,
    val enabled: Boolean,
    val vpnEnabled: Boolean,
    val originalDurationMinutes: Int?,
    val id: String = "",
) {
    companion object {
        fun empty() = ScheduleDraft(
            null,
            emptyList(),
            emptyList(),
            "",
            9,
            0,
            18,
            0,
            listOf(1, 2, 3, 4, 5),
            true,
            false,
            null,
        )
        fun from(schedule: RecurringBlockSchedule, index: Int) = ScheduleDraft(
            index,
            schedule.packages,
            schedule.packages,
            "",
            schedule.startHour,
            schedule.startMinute,
            schedule.endHour,
            schedule.endMinute,
            schedule.daysOfWeek,
            schedule.enabled,
            schedule.vpnEnabled,
            schedule.durationMinutes(),
            schedule.id,
        )
    }

    fun toSchedule() = RecurringBlockSchedule(
        id = id.ifBlank { "schedule-${System.currentTimeMillis()}" },
        packages = packages,
        startHour = startHour.coerceIn(0, 23),
        startMinute = startMinute.coerceIn(0, 59),
        endHour = endHour.coerceIn(0, 23),
        endMinute = endMinute.coerceIn(0, 59),
        daysOfWeek = days.sorted(),
        enabled = enabled,
        vpnEnabled = vpnEnabled,
        vpnPackages = if (vpnEnabled) packages else emptyList(),
    )

    fun durationMinutes(): Int {
        val start = startHour.coerceIn(0, 23) * 60 + startMinute.coerceIn(0, 59)
        val end = endHour.coerceIn(0, 23) * 60 + endMinute.coerceIn(0, 59)
        return if (end > start) end - start else 24 * 60 - start + end
    }
}

/**
 * Full-fidelity schedule editor matching screenshot 3e_9.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditor(
    context: Context,
    draft: ScheduleDraft,
    catalogState: InstalledAppsLoadState,
    requireDefensePin: ((String, String, () -> Unit) -> Unit)?,
    vpnRepository: VpnRepository?,
    onNetworkProtectionRequired: () -> Unit,
    onBack: () -> Unit,
    onCommit: (ScheduleDraft) -> Unit,
) {
    val dimensions = LocalFocusFlowDimensions.current
    var current by remember(draft) { mutableStateOf(draft) }
    val apps = catalogState.apps
    var search by remember { mutableStateOf("") }
    var validationError by remember(draft) { mutableStateOf<String?>(null) }
    var vpnConsentVisible by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(catalogState.apps, catalogState.loading, catalogState.error) {
        if (!catalogState.loading) {
            current = current.copy(
                appNames = current.packages.map { packageName ->
                    catalogState.resolve(packageName)?.appName
                        ?: "App details unavailable"
                },
            )
        }
    }
    val results = apps.filter {
        search.isNotBlank() &&
            (it.appName.contains(search, true) || it.packageName.contains(search, true)) &&
            it.packageName !in current.packages
    }.take(6)

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DarkBackground,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = dimensions.modalPadding, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
            ) {
                // Header matching 3e_9
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Back", color = DarkTextSecondary)
                    }
                    Text(
                        text = if (current.index == null) "Add Schedule Window" else "Edit Schedule Window",
                        fontSize = 17.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }

            // Target Apps section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "TARGET APPS",
                    fontSize = 12.scaledSp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.scaledSp,
                    color = DarkTextSecondary,
                )

                // Search Box
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search apps to add…", color = DarkTextMuted, fontSize = 13.scaledSp) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedBorderColor = BrandPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = DarkTextPrimary,
                        unfocusedTextColor = DarkTextPrimary,
                    ),
                )

                // Search Results
                if (results.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
                    ) {
                        results.forEach { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        current = current.copy(
                                            packages = current.packages + app.packageName,
                                            appNames = current.appNames + app.appName,
                                        )
                                        search = ""
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(app.icon, size = 28.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.appName, fontSize = 13.scaledSp, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                                    Text(app.packageName, fontSize = 11.scaledSp, color = DarkTextSecondary)
                                }
                                Icon(Icons.Outlined.Add, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Selected Apps Chips
                if (current.packages.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        current.packages.forEachIndexed { index, pkg ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurfaceVariant)
                                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        val remove = {
                                            current = current.copy(
                                                packages = current.packages.filterIndexed { packageIndex, _ -> packageIndex != index },
                                                appNames = current.appNames.filterIndexed { packageIndex, _ -> packageIndex != index },
                                            )
                                        }
                                        if (requireDefensePin != null) {
                                            requireDefensePin(
                                                "Remove App from Window",
                                                "Enter your defense password to remove this app from the block window.",
                                                remove,
                                            )
                                        } else {
                                            remove()
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    val appInfo = catalogState.resolve(pkg)
                                    AppIcon(appInfo?.icon, size = 18.dp)
                                    Text(
                                        text = current.appNames.getOrNull(index)
                                            ?.takeIf { it != pkg }
                                            ?: appInfo?.appName
                                            ?: if (catalogState.loading) {
                                                "Loading app details…"
                                            } else {
                                                "App details unavailable"
                                            },
                                        fontSize = 12.scaledSp,
                                        color = DarkTextPrimary,
                                    )
                                    if (appInfo == null || !appInfo.isInstalled) {
                                        Text(
                                            pkg,
                                            fontSize = 9.scaledSp,
                                            color = DarkTextSecondary,
                                        )
                                    }
                                    Icon(
                                        Icons.Outlined.Clear,
                                        contentDescription = "Remove",
                                        tint = DarkTextSecondary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Time Settings
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "TIME WINDOW",
                    fontSize = 12.scaledSp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.scaledSp,
                    color = DarkTextSecondary,
                )
                TimeStepperRow(
                    label = "Start time",
                    hour = current.startHour,
                    minute = current.startMinute,
                    onHourChange = { current = current.copy(startHour = it) },
                    onMinuteChange = { current = current.copy(startMinute = it) },
                )
                TimeStepperRow(
                    label = "End time",
                    hour = current.endHour,
                    minute = current.endMinute,
                    onHourChange = { current = current.copy(endHour = it) },
                    onMinuteChange = { current = current.copy(endMinute = it) },
                )
            }

            // Active Days
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "ACTIVE DAYS",
                    fontSize = 12.scaledSp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.scaledSp,
                    color = DarkTextSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val dayNames = listOf("S", "M", "T", "W", "T", "F", "S")
                    dayNames.forEachIndexed { index, day ->
                        val isSelected = index in current.days
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) BrandPrimary else DarkSurfaceVariant)
                                .border(1.dp, if (isSelected) BrandPrimary else DarkBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    current = current.copy(
                                        days = if (isSelected) current.days - index else current.days + index,
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = day,
                                fontSize = 14.scaledSp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else DarkTextSecondary,
                            )
                        }
                    }
                }
            }

            // Toggles
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Window Enabled", fontSize = 14.scaledSp, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                        Text("Turn off to temporarily suspend this schedule", fontSize = 12.scaledSp, color = DarkTextSecondary)
                    }
                    FocusFlowSwitch(
                        checked = current.enabled,
                        onCheckedChange = { enabled ->
                            if (current.enabled != enabled && requireDefensePin != null) {
                                requireDefensePin(
                                    "Change Window Protection",
                                    "Enter your defense password to change whether this scheduled window is enabled.",
                                ) {
                                    current = current.copy(enabled = enabled)
                                }
                            } else {
                                current = current.copy(enabled = enabled)
                            }
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.VpnKey, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(16.dp))
                            Text("Block Network (VPN)", fontSize = 14.scaledSp, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                        }
                        Text("Cut internet access for these apps during this window", fontSize = 12.scaledSp, color = DarkTextSecondary)
                    }
                    FocusFlowSwitch(
                        checked = current.vpnEnabled,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                val disable = {
                                    current = current.copy(vpnEnabled = false)
                                }
                                if (requireDefensePin != null) {
                                    requireDefensePin(
                                        "Disable Network Blocking",
                                        "Enter your defense password to disable VPN blocking for this window.",
                                        disable,
                                    )
                                } else {
                                    disable()
                                }
                            } else {
                                scope.launch {
                                    val permissionGranted = VpnConsentPolicy.permissionGranted(
                                        repositoryPermission = vpnRepository?.isVpnPermissionGranted(),
                                        platformPermissionCheck = {
                                            VpnService.prepare(context) == null
                                        },
                                    )
                                    if (!permissionGranted) {
                                        vpnConsentVisible = true
                                    } else {
                                        current = current.copy(vpnEnabled = true)
                                        onNetworkProtectionRequired()
                                    }
                                }
                            }
                        },
                    )
                }
            }

            // Summary Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = "Summary: ${current.packages.size} app${if (current.packages.size == 1) "" else "s"} · " +
                        "${current.startHour.toString().padStart(2, '0')}:${current.startMinute.toString().padStart(2, '0')} – " +
                        "${current.endHour.toString().padStart(2, '0')}:${current.endMinute.toString().padStart(2, '0')} · " +
                        current.days.toScheduleDayLabel(),
                    fontSize = 12.scaledSp,
                    color = DarkTextSecondary,
                )
            }

            validationError?.let {
                Text(it, color = Color(0xFFEF4444), fontSize = 12.scaledSp)
            }

            // Commit Button
            Button(
                onClick = {
                    if (current.packages.isEmpty()) {
                        validationError = "Select at least one app."
                    } else if (current.days.isEmpty()) {
                        validationError = "Select at least one day."
                    } else {
                        validationError = null
                        val commit = { onCommit(current) }
                        val originalDurationMinutes = current.originalDurationMinutes
                        if (
                            originalDurationMinutes != null &&
                            current.durationMinutes() < originalDurationMinutes &&
                            requireDefensePin != null
                        ) {
                            requireDefensePin(
                                "Shorten Block Window",
                                "You are decreasing the block duration. Enter your defense password to confirm.",
                                commit,
                            )
                        } else {
                            commit()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
            ) {
                Text(
                    text = if (current.index == null) "Add Window" else "Update Window",
                    fontSize = 15.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

    val requestVpnConsent = rememberVpnConsentRequester(vpnRepository) { outcome ->
        if (outcome.isGranted) {
            current = current.copy(vpnEnabled = true)
            onNetworkProtectionRequired()
        } else {
            validationError = "VPN permission was not granted. Network protection remains off."
        }
    }

    VpnConsentModal(
        visible = vpnConsentVisible,
        onCancel = { vpnConsentVisible = false },
        onConfirm = {
            vpnConsentVisible = false
            requestVpnConsent()
        },
    )

}

@Composable
private fun TimeStepperRow(
    label: String,
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.scaledSp, color = DarkTextPrimary)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Hours
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCard)
                    .clickable { onHourChange((hour + 23) % 24) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Remove, contentDescription = null, tint = DarkTextPrimary, modifier = Modifier.size(14.dp))
            }
            Text(
                text = hour.toString().padStart(2, '0'),
                fontSize = 15.scaledSp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary,
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCard)
                    .clickable { onHourChange((hour + 1) % 24) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = DarkTextPrimary, modifier = Modifier.size(14.dp))
            }

            Text(":", fontSize = 15.scaledSp, fontWeight = FontWeight.Bold, color = DarkTextSecondary)

            // Minutes
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCard)
                    .clickable { onMinuteChange((minute + 55) % 60) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Remove, contentDescription = null, tint = DarkTextPrimary, modifier = Modifier.size(14.dp))
            }
            Text(
                text = minute.toString().padStart(2, '0'),
                fontSize = 15.scaledSp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary,
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCard)
                    .clickable { onMinuteChange((minute + 5) % 60) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = DarkTextPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

private fun RecurringBlockSchedule.durationMinutes(): Int {
    val start = startHour.coerceIn(0, 23) * 60 + startMinute.coerceIn(0, 59)
    val end = endHour.coerceIn(0, 23) * 60 + endMinute.coerceIn(0, 59)
    return if (end > start) end - start else 24 * 60 - start + end
}

private fun List<Int>.toScheduleDayLabel(): String {
    if (size == 7) return "Every day"
    if (this == listOf(1, 2, 3, 4, 5)) return "Weekdays"
    if (this == listOf(0, 6)) return "Weekends"
    val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    return sorted().mapNotNull { names.getOrNull(it) }.joinToString(", ").ifBlank { "No days" }
}
