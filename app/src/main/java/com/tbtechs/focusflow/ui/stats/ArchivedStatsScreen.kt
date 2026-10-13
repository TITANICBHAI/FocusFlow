package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.HourglassEmpty
 import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tbtechs.focusflow.analytics.ANALYTICS_ALL_TIME
import com.tbtechs.focusflow.analytics.ANALYTICS_TODAY
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import com.tbtechs.focusflow.analytics.ANALYTICS_YESTERDAY
import com.tbtechs.focusflow.analytics.WEEK_MODE_DYNAMIC
import com.tbtechs.focusflow.analytics.WEEK_MODE_FIXED
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot
import com.tbtechs.focusflow.analytics.LifetimeStats
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.SunAmber
import com.tbtechs.focusflow.ui.focus.ActiveStatusIndicator
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Kotlin port of the archived Stats tab layout.
 *
 * It intentionally keeps the RN information hierarchy: a sticky four-option
 * filter row, one focused report per period, compact summary cards, and clear
 * empty/loading/permission states. It consumes the existing repositories and
 * usage-permission path instead of duplicating native UsageStats code.
 */
@Composable
fun ArchivedStatsScreen(
    statsViewModel: StatsViewModel,
    onOpenUsageAccessSettings: () -> Unit = {},
    onOpenActiveBlocks: () -> Unit = {},
    onOpenQuickBlock: (String?) -> Unit = {},
    onOpenExtra: () -> Unit = {},
) {
    val snapshot by statsViewModel.analyticsSnapshot.collectAsState()
    val lifetime by statsViewModel.lifetimeStats.collectAsState()
    val loadState by statsViewModel.loadState.collectAsState()
    val window by statsViewModel.activeWindow.collectAsState()
    val weekViewMode by statsViewModel.weekViewMode.collectAsState()
    val weekStartDay by statsViewModel.weekStartDay.collectAsState()
    var usagePermission by remember { mutableStateOf<Boolean?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        usagePermission = runCatching {
            com.tbtechs.focusflow.di.AppModule.usageStatsRepository.hasPermission()
        }.getOrDefault(false)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    usagePermission = runCatching {
                        com.tbtechs.focusflow.di.AppModule.usageStatsRepository.hasPermission()
                    }.getOrDefault(false)
                    statsViewModel.reload()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
    ) {
        StatsHeader(
            window = window,
            weekViewMode = weekViewMode,
            weekStartDay = weekStartDay,
            onOpenActiveBlocks = onOpenActiveBlocks,
        )
        StatsFilterRow(
            activeWindow = window,
            onSelect = statsViewModel::setWindow,
        )

        when (val state = loadState) {
            StatsLoadState.Loading -> StatsLoadingState()
            is StatsLoadState.Error -> StatsErrorState(onRetry = statsViewModel::reload)
            StatsLoadState.PermissionNeeded -> {
                StatsPermissionState(onOpenUsageAccessSettings)
            }
            StatsLoadState.Ready, StatsLoadState.Unavailable -> {
                val loaded = snapshot
                if (loaded == null) {
                    StatsEmptyState(window)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 14.dp,
                            end = 16.dp,
                            bottom = 28.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            loaded.phoneUsage
                                ?.takeUnless { window == ANALYTICS_ALL_TIME }
                                ?.let { phoneUsage ->
                                    DeviceUsageCard(
                                        usage = phoneUsage,
                                        window = window,
                                        weekViewMode = weekViewMode,
                                        weekStartDay = weekStartDay,
                                        rangeStartISO = loaded.range.startISO,
                                        focusMinutesByDate = loaded.sessions.focusMinutesByDate,
                                        focusMinutesByDayOfWeek = loaded.sessions.focusMinutesByDayOfWeek,
                                        onOpenQuickBlock = onOpenQuickBlock,
                                        onSelectWeekMode = statsViewModel::setWeekViewMode,
                                    )
                                }
                        }
                        item {
                            when (window) {
                                ANALYTICS_YESTERDAY -> YesterdayReport(loaded)
                                ANALYTICS_WEEK -> WeekReport(
                                    snapshot = loaded,
                                    weekViewMode = weekViewMode,
                                    weekStartDay = weekStartDay,
                                    showWeekModeMenu = loaded.phoneUsage == null,
                                    onSelectWeekMode = statsViewModel::setWeekViewMode,
                                )
                                ANALYTICS_ALL_TIME -> AllTimeReport(loaded, lifetime)
                                else -> TodayReport(loaded)
                            }
                        }
                        item {
                            if (window != ANALYTICS_ALL_TIME) {
                                DataHealthNotice(loaded)
                            }
                        }
                        item {
                            UsageAccessCard(
                                permission = usagePermission,
                                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                            )
                        }
                        item {
                            BlockingReport(
                                snapshot = loaded,
                                onOpenActiveBlocks = onOpenActiveBlocks,
                                onOpenQuickBlock = onOpenQuickBlock,
                            )
                        }
                        item {
                            ExtraButton(onClick = onOpenExtra)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsHeader(
    window: String,
    weekViewMode: String,
    weekStartDay: Int,
    onOpenActiveBlocks: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Stats", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
            Text(
                statsSubtitle(window, weekViewMode, weekStartDay),
                fontSize = 13.sp,
                color = DarkTextSecondary,
            )
        }
        ActiveStatusIndicator(
            onOpenActiveBlocks = onOpenActiveBlocks,
        )
    }
}

@Composable
private fun ExtraButton(onClick: () -> Unit) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
    ) {
        Icon(Icons.Outlined.Analytics, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Extra")
    }
}

@Composable
private fun StatsFilterRow(
    activeWindow: String,
    onSelect: (String) -> Unit,
) {
    val filters = listOf(
        ANALYTICS_TODAY to "Today",
        ANALYTICS_YESTERDAY to "Yesterday",
        ANALYTICS_WEEK to "Week",
        ANALYTICS_ALL_TIME to "All Time",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEach { (filter, label) ->
                StatsPeriodPill(
                    label = label,
                    selected = activeWindow == filter,
                    onClick = { onSelect(filter) },
                )
            }
        }
    }
}

@Composable
private fun WeekModeMenu(
    weekViewMode: String,
    weekStartDay: Int,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = "Choose week range",
                tint = DarkTextSecondary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Last 7 days") },
                onClick = {
                    expanded = false
                    onSelect(WEEK_MODE_DYNAMIC)
                },
                trailingIcon = {
                    if (weekViewMode == WEEK_MODE_DYNAMIC) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = BrandPrimary)
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(weekRangeLabel(weekStartDay)) },
                onClick = {
                    expanded = false
                    onSelect(WEEK_MODE_FIXED)
                },
                trailingIcon = {
                    if (weekViewMode == WEEK_MODE_FIXED) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = BrandPrimary)
                    }
                },
            )
        }
    }
}

@Composable
private fun TodayReport(snapshot: AnalyticsSnapshot) {
    val focusMinutes = snapshot.sessions.totalFocusMinutes.roundToInt()
    ArchivedCard {
        Icon(Icons.Outlined.Timer, null, tint = BrandPrimary, modifier = Modifier.size(28.dp))
        Text(
            formatStatsMinutes(focusMinutes),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = BrandPrimary,
        )
        Text("focus time today", color = DarkTextSecondary)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("${snapshot.sessions.total}", "Sessions", BrandPrimary)
            StatTile("${snapshot.tasks.completed}", "Done", Color(0xFF55C98A))
            StatTile("${snapshot.blocking.totalAttempts}", "Blocked", SunAmber)
        }
        Spacer(Modifier.height(16.dp))
        TaskBreakdown(snapshot)
    }
}

@Composable
private fun YesterdayReport(snapshot: AnalyticsSnapshot) {
    ArchivedCard {
        Icon(Icons.Outlined.CalendarMonth, null, tint = BrandPrimary, modifier = Modifier.size(26.dp))
        Text("Yesterday's report", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
        Text(
            "${snapshot.tasks.completed} of ${snapshot.tasks.total} tasks completed",
            color = DarkTextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        TaskBreakdown(snapshot)
        Spacer(Modifier.height(14.dp))
        snapshot.tasks.resultRows.orEmpty().take(8).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (row.status == "completed") Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                    null,
                    tint = if (row.status == "completed") Color(0xFF55C98A) else SunAmber,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    row.title,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    color = DarkTextPrimary,
                    maxLines = 1,
                )
                Text(statusLabel(row.status), fontSize = 11.sp, color = DarkTextSecondary)
            }
        }
    }
}

@Composable
private fun WeekReport(
    snapshot: AnalyticsSnapshot,
    weekViewMode: String,
    weekStartDay: Int,
    showWeekModeMenu: Boolean,
    onSelectWeekMode: (String) -> Unit,
) {
    ArchivedCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (weekViewMode == WEEK_MODE_DYNAMIC) "Last 7 days" else "${weekRangeLabel(weekStartDay)} report",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                )
            }
            if (showWeekModeMenu) {
                WeekModeMenu(
                    weekViewMode = weekViewMode,
                    weekStartDay = weekStartDay,
                    onSelect = onSelectWeekMode,
                )
            }
        }
        Text(
            "${snapshot.tasks.completed} of ${snapshot.tasks.total} tasks completed",
            color = DarkTextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        WeekCompletionBars(snapshot, snapshot.range.startISO)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("${snapshot.sessions.totalFocusMinutes.roundToInt()}m", "Focus", BrandPrimary)
            StatTile("${snapshot.sessions.cleanCount}", "Clean", Color(0xFF55C98A))
            StatTile("${snapshot.blocking.totalAttempts}", "Blocked", SunAmber)
        }
    }
}

@Composable
private fun AllTimeReport(snapshot: AnalyticsSnapshot, lifetime: LifetimeStats?) {
    ArchivedCard {
        Icon(Icons.Outlined.HourglassEmpty, null, tint = BrandPrimary, modifier = Modifier.size(27.dp))
        Text(
            formatStatsMinutes((lifetime?.totalFocusMinutes ?: snapshot.sessions.totalFocusMinutes).roundToInt()),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = BrandPrimary,
        )
        Text("total focus time", color = DarkTextSecondary)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("${lifetime?.completedTasks ?: snapshot.tasks.completed}", "Tasks", Color(0xFF55C98A))
            StatTile("${lifetime?.totalSessions ?: snapshot.sessions.total}", "Sessions", BrandPrimary)
            StatTile("${lifetime?.currentStreakDays ?: 0}d", "Streak", SunAmber)
        }
    }
}

@Composable
private fun TaskBreakdown(snapshot: AnalyticsSnapshot) {
    Text("Task breakdown", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
    BreakdownRow("Completed", snapshot.tasks.completed, Color(0xFF55C98A), snapshot.tasks.total)
    BreakdownRow("Remaining", (snapshot.tasks.total - snapshot.tasks.completed - snapshot.tasks.skipped - snapshot.tasks.missed).coerceAtLeast(0), BrandPrimary, snapshot.tasks.total)
    BreakdownRow("Skipped", snapshot.tasks.skipped, DarkTextMuted, snapshot.tasks.total)
    BreakdownRow("Missed", snapshot.tasks.missed, Color(0xFFE06B6B), snapshot.tasks.total)
}

@Composable
private fun BreakdownRow(label: String, value: Int, color: Color, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(74.dp), fontSize = 11.sp, color = DarkTextSecondary)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkBorder),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(if (total == 0) 0f else (value.toFloat() / total).coerceIn(0f, 1f))
                    .height(7.dp)
                    .background(color),
            )
        }
        Text(value.toString(), modifier = Modifier.width(28.dp), fontSize = 11.sp, color = color)
    }
}

@Composable
private fun WeekCompletionBars(snapshot: AnalyticsSnapshot, rangeStartISO: String) {
    val startDate = rangeStartISO
        .let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull() }
        ?: LocalDate.now().minusDays(6)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        (0..6).forEach { offset ->
            val date = startDate.plusDays(offset.toLong())
            val bucket = snapshot.tasks.byDate[date.toString()]
            val total = bucket?.total ?: 0
            val rate = if (total == 0) 0f
            else ((bucket?.completed ?: 0).toFloat() / total).coerceIn(0f, 1f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .width(24.dp)
                        .height((20 + 72 * rate).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (rate > 0f) BrandPrimary else DarkBorder),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    date.format(DateTimeFormatter.ofPattern("M/d", Locale.getDefault())),
                    fontSize = 10.sp,
                    color = DarkTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun UsageAccessCard(
    permission: Boolean?,
    onOpenUsageAccessSettings: () -> Unit,
) {
    if (permission == true) return
    ArchivedCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Settings, null, tint = BrandPrimary, modifier = Modifier.size(21.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Device usage is not connected", fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                Text(
                    "Grant Usage Access when you want FocusFlow to explain phone-use patterns. Your focus stats above continue to work without it.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = DarkTextSecondary,
                )
            }
            Button(
                onClick = onOpenUsageAccessSettings,
                contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
            ) {
                Text("Open", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DeviceUsageCard(
    usage: AnalyticsSnapshot.PhoneUsage,
    window: String,
    weekViewMode: String,
    weekStartDay: Int,
    rangeStartISO: String,
    focusMinutesByDate: Map<String, Double>,
    focusMinutesByDayOfWeek: Map<Int, Double>,
    onOpenQuickBlock: (String?) -> Unit,
    onSelectWeekMode: (String) -> Unit,
) {
    ArchivedCard {
        val totalMinutes = if (usage.totalMinutes > 0) {
            usage.totalMinutes
        } else {
            usage.byHour.values.sum().roundToInt()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.PhoneAndroid,
                null,
                tint = Color(0xFF63A7FF),
                modifier = Modifier.size(21.dp),
            )
            Text(
                "Observed Device Time",
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DarkTextPrimary,
            )
            Spacer(Modifier.weight(1f))
            if (window == ANALYTICS_WEEK) {
                WeekModeMenu(
                    weekViewMode = weekViewMode,
                    weekStartDay = weekStartDay,
                    onSelect = onSelectWeekMode,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (window == ANALYTICS_WEEK) {
                            BrandPrimary.copy(alpha = 0.12f)
                        } else {
                            Color(0xFF55C98A).copy(alpha = 0.12f)
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    if (window == ANALYTICS_WEEK) {
                        if (weekViewMode == WEEK_MODE_DYNAMIC) {
                            "Dynamic"
                        } else {
                            weekRangeLabel(weekStartDay)
                        }
                    } else {
                        "On device"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (window == ANALYTICS_WEEK) BrandPrimary else Color(0xFF55C98A),
                )
            }
        }
        Text(
            formatStatsMinutes(totalMinutes),
            modifier = Modifier.padding(top = 12.dp),
            fontSize = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF63A7FF),
        )
        Text(
            "Android app foreground time — FocusFlow home-screen time excluded",
            fontSize = 12.sp,
            color = DarkTextSecondary,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(1.dp)
                .background(DarkBorder),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            UsageMetric(formatStatsMinutes(focusMinutesByDayOfWeek.values.sum().roundToInt()), "Focus time", BrandPrimary)
            Box(Modifier.width(1.dp).height(44.dp).background(DarkBorder))
            val ratio = if (totalMinutes > 0) {
                (focusMinutesByDayOfWeek.values.sum() * 100 / totalMinutes).roundToInt()
            } else {
                0
            }
            UsageMetric(if (totalMinutes > 0) "$ratio%" else "0%", "Focus / observed", if (ratio > 0) Color(0xFF55C98A) else DarkTextMuted)
            Box(Modifier.width(1.dp).height(44.dp).background(DarkBorder))
            UsageMetric("0", "Blocked attempts", Color(0xFF55C98A))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DarkBorder),
        )

        if (window == ANALYTICS_WEEK) {
            WeeklyObservationChart(
                observedMinutesByDate = usage.observedMinutesByDate,
                focusMinutesByDate = focusMinutesByDate,
                rangeStartISO = rangeStartISO,
            )
        }

        if (usage.apps.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Top apps by observed time",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkTextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text("minutes · launches", fontSize = 12.sp, color = DarkTextMuted)
            }
            val maxMinutes = usage.apps.firstOrNull()?.minutes?.coerceAtLeast(1.0) ?: 1.0
            usage.apps.take(5).forEachIndexed { index, app ->
                UsageAppRow(
                    app = app,
                    rank = index + 1,
                    maxMinutes = maxMinutes,
                    onOpenQuickBlock = onOpenQuickBlock,
                )
                if (index < usage.apps.take(5).lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(DarkBorder),
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageMetric(value: String, label: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
        )
        Text(label, fontSize = 11.sp, color = DarkTextMuted)
    }
}

@Composable
private fun UsageAppRow(
    app: AnalyticsSnapshot.HeaviestApp,
    rank: Int,
    maxMinutes: Double,
    onOpenQuickBlock: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = app.packageName != null,
                onClick = { app.packageName?.let(onOpenQuickBlock) },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 24.dp)
                .clip(RoundedCornerShape(50))
                .background(if (rank == 1) Color(0xFF63A7FF).copy(alpha = 0.14f) else DarkSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "#$rank",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (rank == 1) Color(0xFF63A7FF) else DarkTextMuted,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                app.appName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkTextPrimary,
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(DarkBorder),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((app.minutes / maxMinutes).toFloat().coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF63A7FF)),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${app.minutes.roundToInt()}m · ${app.launchCount}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = DarkTextPrimary,
        )
    }
}

@Composable
private fun WeeklyObservationChart(
    observedMinutesByDate: Map<String, Double>,
    focusMinutesByDate: Map<String, Double>,
    rangeStartISO: String,
) {
    val startDate = runCatching {
        Instant.parse(rangeStartISO).atZone(ZoneId.systemDefault()).toLocalDate()
    }.getOrElse { LocalDate.now().minusDays(6) }
    val dates = (0..6).map { startDate.plusDays(it.toLong()) }
    val maxMinutes = (0..6)
        .maxOf { offset ->
            val date = dates[offset].toString()
            maxOf(
                observedMinutesByDate[date] ?: 0.0,
                focusMinutesByDate[date] ?: 0.0,
            )
        }
        .coerceAtLeast(1.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "WEEKLY OBSERVATION",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = DarkTextMuted,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            (0..6).forEach { offset ->
                val date = dates[offset]
                val dateKey = date.toString()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.height(88.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        WeeklyUsageBar(
                            value = observedMinutesByDate[dateKey] ?: 0.0,
                            maxMinutes = maxMinutes,
                            color = Color(0xFF63A7FF),
                        )
                        WeeklyUsageBar(
                            value = focusMinutesByDate[dateKey] ?: 0.0,
                            maxMinutes = maxMinutes,
                            color = BrandPrimary,
                        )
                    }
                    Text(
                        date.format(DateTimeFormatter.ofPattern("M/d", Locale.getDefault())),
                        fontSize = 10.sp,
                        color = DarkTextMuted,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            UsageLegend(Color(0xFF63A7FF), "Observed device time")
            Spacer(Modifier.width(16.dp))
            UsageLegend(BrandPrimary, "Focus time")
        }
    }
}

@Composable
private fun WeeklyUsageBar(value: Double, maxMinutes: Double, color: Color) {
    Box(
        modifier = Modifier
            .width(9.dp)
            .height(88.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(DarkSurfaceVariant),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight((value / maxMinutes).toFloat().coerceIn(0f, 1f))
                .clip(RoundedCornerShape(5.dp))
                .background(color),
        )
    }
}

@Composable
private fun UsageLegend(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
        Text(label, fontSize = 11.sp, color = DarkTextMuted)
    }
}

@Composable
private fun BlockingReport(
    snapshot: AnalyticsSnapshot,
    onOpenActiveBlocks: () -> Unit,
    onOpenQuickBlock: (String?) -> Unit,
) {
    if (snapshot.blocking.totalAttempts == 0 && snapshot.blocking.topApp == null) return
    ArchivedCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Security, null, tint = SunAmber, modifier = Modifier.size(22.dp))
            Text("Blocked attempts", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
        }
        Text(
            "${snapshot.blocking.totalAttempts} app opening${if (snapshot.blocking.totalAttempts == 1) "" else "s"} intercepted in this period.",
            modifier = Modifier.padding(top = 6.dp),
            color = DarkTextSecondary,
        )
        snapshot.blocking.topApp?.let { app ->
            val topPackage = snapshot.blocking.byApp.entries
                .firstOrNull { (_, entry) -> entry == app }
                ?.key
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            enabled = topPackage != null,
                            onClick = { topPackage?.let(onOpenQuickBlock) },
                        )
                        .padding(vertical = 4.dp),
                ) {
                    Text(app.appName, color = DarkTextPrimary, fontWeight = FontWeight.Medium)
                    Text("${app.count} attempts", fontSize = 11.sp, color = DarkTextSecondary)
                }
            }
        }
        Button(
            onClick = onOpenActiveBlocks,
            modifier = Modifier.padding(top = 10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandPrimary),
        ) {
            Text("Open active blocks")
        }
    }
}

@Composable
private fun ArchivedCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun StatTile(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = DarkTextSecondary)
    }
}

@Composable
private fun StatsLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = BrandPrimary)
            Spacer(Modifier.height(14.dp))
            Text("Reading your local history…", color = DarkTextSecondary)
        }
    }
}

@Composable
private fun StatsErrorState(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Analytics, null, tint = DarkTextMuted, modifier = Modifier.size(42.dp))
            Text("Stats unavailable", fontSize = 20.sp, color = DarkTextPrimary)
            Text(
                "FocusFlow could not read this period. Try again without leaving the tab.",
                color = DarkTextSecondary,
            )
            Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                Text("Try again")
            }
        }
    }
}

@Composable
private fun StatsPermissionState(onOpenUsageAccessSettings: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ArchivedCard {
            Icon(Icons.Outlined.Settings, null, tint = BrandPrimary, modifier = Modifier.size(28.dp))
            Text("Connect device usage", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
            Text(
                "This report needs Android Usage Access to read phone behaviour. Nothing leaves the device.",
                color = DarkTextSecondary,
            )
            Button(onClick = onOpenUsageAccessSettings, modifier = Modifier.padding(top = 12.dp)) {
                Text("Grant Usage Access")
            }
        }
    }
}

@Composable
private fun StatsEmptyState(window: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ArchivedCard {
            Icon(Icons.Outlined.Analytics, null, tint = DarkTextMuted, modifier = Modifier.size(38.dp))
            Text(
                if (window == ANALYTICS_YESTERDAY) "Nothing recorded yesterday" else "No activity recorded yet",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary,
            )
            Text(
                "Insights will appear here once FocusFlow has a task, session, or blocked-app attempt to read.",
                color = DarkTextSecondary,
            )
        }
    }
}

private fun statsSubtitle(window: String, weekViewMode: String, weekStartDay: Int): String = when (window) {
    ANALYTICS_YESTERDAY -> "The day that just ended"
    ANALYTICS_WEEK -> if (weekViewMode == WEEK_MODE_DYNAMIC) {
        "The last 7 days, including today"
    } else {
        "${weekRangeLabel(weekStartDay)} calendar week"
    }
    ANALYTICS_ALL_TIME -> "The record you are building"
    else -> "Your focus so far today"
}

private fun formatStatsMinutes(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val hours = minutes / 60
    val remaining = minutes % 60
    return if (hours > 0) "${hours}h ${remaining}m" else "${remaining}m"
}

private val WEEKDAY_SHORT = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

private fun weekRangeLabel(startDay: Int): String {
    val start = startDay.coerceIn(0, 6)
    val end = (start + 6) % 7
    return "${WEEKDAY_SHORT[start]}–${WEEKDAY_SHORT[end]}"
}

private fun statusLabel(status: String): String = when (status) {
    "completed" -> "Done"
    "skipped" -> "Skipped"
    "overdue" -> "Missed"
    else -> "Not completed"
}