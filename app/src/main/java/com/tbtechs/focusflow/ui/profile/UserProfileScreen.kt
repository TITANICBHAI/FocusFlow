package com.tbtechs.focusflow.ui.profile

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.repository.FocusSessionRepository
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.enforcement.DayRatingNotificationScheduler
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val ProfileAccent = Color(0xFF7777FF)
private val ProfileAccentButton = Color(0xFF6366F1)

/**
 * Profile questionnaire, journey stats, and profile management screen.
 * Matches design specification 9a_(1) through 9a_(5).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserProfileScreen(
    settingsRepository: SettingsRepository,
    isEditMode: Boolean = true,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    focusSessionRepository: FocusSessionRepository? = null,
    settingsViewModel: SettingsViewModel? = null,
) {
    val dimensions = LocalFocusFlowDimensions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember(isEditMode) { mutableStateOf(!isEditMode) }
    var name by remember { mutableStateOf("") }
    var occupation by remember { mutableStateOf("") }
    var dailyGoalHours by remember { mutableStateOf(4) }
    var wakeUpTime by remember { mutableStateOf("") }
    var focusGoals by remember { mutableStateOf(setOf<String>()) }
    var sleepTime by remember { mutableStateOf("") }
    var chronotype by remember { mutableStateOf("") }
    var focusLength by remember { mutableStateOf<Int?>(null) }
    var breakStyle by remember { mutableStateOf("") }
    var distractionTriggers by remember { mutableStateOf(setOf<String>()) }
    var motivationStyle by remember { mutableStateOf(setOf<String>()) }
    var weeklyReviewDay by remember { mutableStateOf("") }
    var weekUsageReportStartDay by remember { mutableStateOf("mon") }
    var saving by remember { mutableStateOf(false) }
    var usageVisible by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<ProfileStats?>(null) }
    var showSavedToast by remember { mutableStateOf(false) }

    LaunchedEffect(settingsRepository) {
        runCatching { settingsRepository.getString(PROFILE_KEY) }
            .getOrNull()
            ?.let { json ->
                runCatching {
                    val profile = JSONObject(json)
                    name = profile.optString("name")
                    occupation = profile.optString("occupation")
                    dailyGoalHours = profile.optInt("dailyGoalHours", 4).coerceIn(1, 16)
                    wakeUpTime = profile.optString("wakeUpTime")
                    focusGoals = profile.optJSONArray("focusGoals").toStringSet()
                    sleepTime = profile.optString("sleepTime")
                    chronotype = profile.optString("chronotype")
                    focusLength = profile.optIntOrNull("focusSessionLength")
                    breakStyle = profile.optString("breakStyle")
                    distractionTriggers = profile.optJSONArray("distractionTriggers").toStringSet()
                    motivationStyle = profile.optJSONArray("motivationStyle").toStringSet()
                    weeklyReviewDay = profile.optString("weeklyReviewDay")
                    weekUsageReportStartDay = profile.optString(
                        "weekUsageReportStartDay",
                        "mon",
                    )
                }
            }
    }

    LaunchedEffect(focusSessionRepository, isEditMode) {
        if (!isEditMode || focusSessionRepository == null) return@LaunchedEffect
        stats = runCatching {
            val lifetime = focusSessionRepository.getLifetimeStats()
            ProfileStats(
                todayMinutes = focusSessionRepository.getTodayFocusMinutes(),
                streakDays = lifetime.currentStreakDays,
                bestStreakDays = focusSessionRepository.getBestStreakDays(),
                allTimeMinutes = lifetime.totalFocusMinutes.toInt(),
                sessions = lifetime.totalSessions,
            )
        }.getOrNull()
    }

    fun save() {
        if (saving) return
        scope.launch {
            saving = true
            val profile = JSONObject().apply {
                put("name", name.trim().takeIf(String::isNotBlank) ?: JSONObject.NULL)
                put("occupation", occupation.takeIf(String::isNotBlank) ?: JSONObject.NULL)
                put("dailyGoalHours", dailyGoalHours)
                put("wakeUpTime", wakeUpTime.takeIf(String::isNotBlank) ?: JSONObject.NULL)
                put("focusGoals", JSONArray(focusGoals.toList()))
                put("sleepTime", sleepTime.takeIf(String::isNotBlank) ?: JSONObject.NULL)
                put("chronotype", chronotype.takeIf(String::isNotBlank) ?: JSONObject.NULL)
                put("focusSessionLength", focusLength ?: JSONObject.NULL)
                put("breakStyle", breakStyle.takeIf(String::isNotBlank) ?: JSONObject.NULL)
                put("distractionTriggers", JSONArray(distractionTriggers.toList()))
                put("motivationStyle", JSONArray(motivationStyle.toList()))
                put("weeklyReviewDay", weeklyReviewDay.takeIf(String::isNotBlank) ?: JSONObject.NULL)
                put("weekUsageReportStartDay", weekUsageReportStartDay)
            }
            runCatching {
                settingsRepository.putString(PROFILE_KEY, profile.toString())
                settingsRepository.putString("onboarding_complete", "true")
                DayRatingNotificationScheduler.ensureScheduled(context)
            }
            focusLength?.let { duration ->
                settingsViewModel?.updateSettings(
                    settingsViewModel.settings.value.copy(
                        defaultDurationMinutes = duration,
                        pomodoroWorkMinutes = duration,
                    ),
                )
            }
            breakStyle
                .takeIf(String::isNotBlank)
                ?.let { style -> breakMinutes(style) }
                ?.let { breakMinutes ->
                    settingsViewModel?.updateSettings(
                        settingsViewModel.settings.value.copy(
                            pomodoroBreakMinutes = breakMinutes,
                        ),
                    )
                }
            saving = false
            editing = false
            showSavedToast = true
            if (!isEditMode) {
                onFinished()
            }
        }
    }

    fun skip() {
        if (isEditMode) {
            onBack()
            return
        }
        scope.launch {
            settingsRepository.putString("onboarding_complete", "true")
            onFinished()
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditMode) "Profile" else "Tell Us About You",
                        fontSize = 18.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                },
                navigationIcon = {
                    if (isEditMode) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = DarkTextPrimary,
                            )
                        }
                    }
                },
                actions = {
                    if (isEditMode) {
                        if (!editing) {
                            TextButton(onClick = { editing = true }) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = null,
                                     tint = ProfileAccent,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                 Text("Edit", color = ProfileAccent, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            TextButton(onClick = ::save, enabled = !saving) {
                                Text(
                                    if (saving) "Saving…" else "Done",
                                     color = ProfileAccent,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    } else {
                        TextButton(onClick = ::skip) {
                            Text("Skip", color = DarkTextSecondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = dimensions.screenPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
        ) {
            Spacer(Modifier.height(2.dp))

            // 1. Profile Avatar & User Header Card (9a_(1))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(ProfileAccent.copy(alpha = 0.16f))
                            .border(2.dp, ProfileAccent.copy(alpha = 0.42f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            tint = ProfileAccent,
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name.ifBlank { "FocusFlow User" },
                            fontSize = 20.scaledSp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (occupation.isNotBlank()) {
                                labelFor(OCCUPATION_LABELS, occupation)
                            } else {
                                "Set up your focus profile"
                            },
                            fontSize = 13.scaledSp,
                            color = DarkTextSecondary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Daily goal: ${dailyGoalHours}h · ${focusGoals.size} active goals",
                            fontSize = 12.scaledSp,
                            color = ProfileAccent,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // 2. Journey Statistics Card (9a_(1))
            val currentStats = stats ?: ProfileStats(
                todayMinutes = 0,
                streakDays = 0,
                bestStreakDays = 0,
                allTimeMinutes = 0,
                sessions = 0,
            )
            ProfileJourneyCard(
                stats = currentStats,
                name = name,
                goalHours = dailyGoalHours,
            )

            // 3. Name & Occupation (9a_(1))
            ProfileFieldCard(
                title = "WHAT'S YOUR NAME?",
                subtitle = "Used to personalize your daily summaries and reports",
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    enabled = editing,
                    placeholder = { Text("e.g. Alex", color = DarkTextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ProfileAccent,
                        unfocusedBorderColor = DarkBorder,
                        disabledBorderColor = DarkBorder.copy(alpha = 0.5f),
                        focusedTextColor = DarkTextPrimary,
                        unfocusedTextColor = DarkTextPrimary,
                        disabledTextColor = DarkTextSecondary,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        disabledContainerColor = DarkSurfaceVariant.copy(alpha = 0.5f),
                    ),
                )
            }

            ProfileFieldCard(
                title = "WHAT BEST DESCRIBES YOU?",
                subtitle = "Helps tailor focus recommendations for your routine",
            ) {
                FlowChoiceChips(
                    choices = OCCUPATION_LABELS,
                    selected = setOf(occupation),
                    enabled = editing,
                    multiSelect = false,
                ) { occupation = if (occupation == it) "" else it }
            }

            // 4. Daily Goal (9a_(1))
            ProfileFieldCard(
                title = "DAILY FOCUS GOAL",
                subtitle = "FocusFlow tracks your daily progress toward this target",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${dailyGoalHours} hours / day",
                        fontSize = 16.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = ProfileAccent,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { dailyGoalHours = (dailyGoalHours - 1).coerceAtLeast(1) },
                            enabled = editing && dailyGoalHours > 1,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextPrimary),
                        ) {
                            Text("−", fontSize = 18.scaledSp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            "$dailyGoalHours",
                            fontSize = 16.scaledSp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )

                        OutlinedButton(
                            onClick = { dailyGoalHours = (dailyGoalHours + 1).coerceAtMost(16) },
                            enabled = editing && dailyGoalHours < 16,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextPrimary),
                        ) {
                            Text("+", fontSize = 18.scaledSp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 5. Wake-up time & Focus goals (9a_(2))
            ProfileFieldCard(
                title = "WHEN DO YOU USUALLY WAKE UP?",
                subtitle = "Aligns your morning focus schedule and daily digest",
            ) {
                FlowChoiceChips(
                    choices = WAKE_TIMES,
                    selected = setOf(wakeUpTime),
                    enabled = editing,
                    multiSelect = false,
                ) { wakeUpTime = if (wakeUpTime == it) "" else it }
            }

            ProfileFieldCard(
                title = "WHAT ARE YOUR MAIN FOCUS GOALS?",
                subtitle = "Select all that apply to categorize your sessions",
            ) {
                FlowChoiceChips(
                    choices = FOCUS_GOALS,
                    selected = focusGoals,
                    enabled = editing,
                    multiSelect = true,
                ) { focusGoals = focusGoals.toggle(it) }
            }

            // 6. Sleep time & Chronotype (9a_(2))
            ProfileFieldCard(
                title = "WHEN DO YOU USUALLY GO TO SLEEP?",
                subtitle = "Defines your available focus window and night routine",
            ) {
                FlowChoiceChips(
                    choices = SLEEP_TIMES,
                    selected = setOf(sleepTime),
                    enabled = editing,
                    multiSelect = false,
                ) { sleepTime = if (sleepTime == it) "" else it }
            }

            ProfileFieldCard(
                title = "WHEN DO YOU FOCUS BEST?",
                subtitle = "Helps suggest your golden focus hours each day",
            ) {
                FlowChoiceChips(
                    choices = CHRONOTYPES,
                    selected = setOf(chronotype),
                    enabled = editing,
                    multiSelect = false,
                ) { chronotype = if (chronotype == it) "" else it }
            }

            // 7. Ideal focus block & Break style (9a_(3))
            ProfileFieldCard(
                title = "YOUR IDEAL FOCUS BLOCK",
                subtitle = "Default duration used when starting a new session",
            ) {
                FlowChoiceChips(
                    choices = FOCUS_LENGTHS.map { it.toString() to "$it min" },
                    selected = setOf(focusLength?.toString() ?: ""),
                    enabled = editing,
                    multiSelect = false,
                ) { focusLength = if (focusLength?.toString() == it) null else it.toIntOrNull() }
            }

            ProfileFieldCard(
                title = "HOW DO YOU LIKE TO BREAK?",
                subtitle = "Sets the default pause duration in Pomodoro mode",
            ) {
                FlowChoiceChips(
                    choices = BREAK_STYLES,
                    selected = setOf(breakStyle),
                    enabled = editing,
                    multiSelect = false,
                ) { breakStyle = if (breakStyle == it) "" else it }
            }

            // 8. Distraction triggers & Motivation style (9a_(3), 9a_(4))
            ProfileFieldCard(
                title = "WHAT PULLS YOU OFF TRACK MOST?",
                subtitle = "Identifies triggers so we can suggest better block filters",
            ) {
                FlowChoiceChips(
                    choices = DISTRACTION_TRIGGERS,
                    selected = distractionTriggers,
                    enabled = editing,
                    multiSelect = true,
                ) { distractionTriggers = distractionTriggers.toggle(it) }
            }

            ProfileFieldCard(
                title = "WHAT MOTIVATES YOU?",
                subtitle = "Personalizes positive reinforcement and quotes",
            ) {
                FlowChoiceChips(
                    choices = MOTIVATION_STYLES,
                    selected = motivationStyle,
                    enabled = editing,
                    multiSelect = true,
                ) { motivationStyle = motivationStyle.toggle(it) }
            }

            // 9. Weekly review day (9a_(4))
            ProfileFieldCard(
                title = "WEEKLY REVIEW DAY",
                subtitle = "Day to generate your weekly focus recap report",
            ) {
                FlowChoiceChips(
                    choices = REVIEW_DAYS,
                    selected = setOf(weeklyReviewDay),
                    enabled = editing,
                    multiSelect = false,
                ) { weeklyReviewDay = if (weeklyReviewDay == it) "" else it }
            }

            ProfileFieldCard(
                title = "STATS WEEK STARTS",
                subtitle = "First day used by the fixed week report in Stats",
            ) {
                FlowChoiceChips(
                    choices = USAGE_WEEK_START_DAYS,
                    selected = setOf(weekUsageReportStartDay),
                    enabled = editing,
                    multiSelect = false,
                ) { weekUsageReportStartDay = it }
            }

            // 10. Information sheet button (9a_(4) / 9a(5))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { usageVisible = true }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ProfileAccent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = ProfileAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "How your profile is used",
                            fontSize = 14.5.scaledSp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkTextPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Private, offline, and stored only on this device",
                            fontSize = 12.scaledSp,
                            color = DarkTextSecondary,
                        )
                    }

                    Text("View →", fontSize = 13.scaledSp, color = ProfileAccent, fontWeight = FontWeight.Bold)
                }
            }

            // 11. Save Action / Edit Actions (9a_(4))
            if (editing) {
                Button(
                    onClick = ::save,
                    enabled = !saving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ProfileAccentButton),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(
                        if (saving) "Saving…" else if (isEditMode) "Save Changes" else "Save & Continue",
                        fontSize = 15.scaledSp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (!isEditMode) {
                TextButton(
                    onClick = ::skip,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Skip for now — I'll set this up later in Settings",
                        color = DarkTextSecondary,
                        fontSize = 13.scaledSp,
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // 12. "How your profile is used" Bottom Sheet (9a(5))
    if (usageVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { usageVisible = false },
            sheetState = sheetState,
            containerColor = DarkSurfaceVariant,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = null,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(top = 14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = ProfileAccent,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            "How your profile is used",
                            fontSize = 18.scaledSp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary,
                        )
                    }

                    IconButton(onClick = { usageVisible = false }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    color = DarkBorder.copy(alpha = 0.9f),
                    thickness = 1.dp,
                )

            ProfileUsageItem(
                icon = Icons.Outlined.AccessTime,
                label = "Daily focus goal",
                value = "${dailyGoalHours}h",
                detail = "Tracked on the Stats screen and on your home-screen widget.",
            )
            ProfileUsageItem(
                icon = Icons.Outlined.WbSunny,
                label = "Wake-up time",
                value = labelFor(WAKE_TIMES, wakeUpTime).ifBlank { "Not set" },
                detail = "Set a wake-up time to get a morning plan notification.",
            )
            ProfileUsageItem(
                icon = Icons.Outlined.WorkOutline,
                label = "Occupation",
                value = labelFor(OCCUPATION_LABELS, occupation).ifBlank { "Not set" },
                detail = "Pick one so we can tailor the app to your routine.",
            )
            ProfileUsageItem(
                icon = Icons.Outlined.Flag,
                label = "Focus goals",
                value = if (focusGoals.isEmpty()) "None" else "${focusGoals.size} selected",
                detail = "Add goals so we can group and label your focus time.",
            )
            ProfileUsageItem(
                icon = Icons.Outlined.Person,
                label = "Name",
                value = name.ifBlank { "Not set" },
                detail = "Add a name to personalise your morning digest.",
            )
            ProfileUsageItem(
                icon = Icons.Outlined.Bedtime,
                label = "Sleep time",
                value = labelFor(SLEEP_TIMES, sleepTime).ifBlank { "Not set" },
                detail = "Add a sleep time to define your day's focus window.",
            )
            ProfileUsageItem(
                icon = Icons.Outlined.WbSunny,
                label = "Best focus time",
                value = labelFor(CHRONOTYPES, chronotype).ifBlank { "Not set" },
                detail = "Tell us when you focus best for smarter task scheduling.",
            )
            ProfileUsageItem(
                icon = Icons.Outlined.HourglassEmpty,
                label = "Ideal focus block",
                value = focusLength?.let { "$it min" } ?: "Not set",
                detail = "Pick a length and we'll use it as your default for new tasks.",
            )
            ProfileUsageItem(
                icon = Icons.Outlined.Info,
                label = "Break style",
                value = labelFor(BREAK_STYLES, breakStyle).ifBlank { "Not set" },
                detail = "Pick a style to set your default Pomodoro break length.",
            )
            ProfileUsageItem(
                icon = Icons.Outlined.Block,
                label = "Distraction triggers",
                value = if (distractionTriggers.isEmpty()) "None" else "${distractionTriggers.size} selected",
                detail = "Pick what details you most want the app to adapt over time.",
            )
            ProfileUsageItem(
                icon = Icons.Outlined.EmojiEvents,
                label = "Motivation style",
                value = if (motivationStyle.isEmpty()) "None" else "${motivationStyle.size} selected",
                detail = "Pick what motivates you so we can lean into it across the app.",
            )
            ProfileUsageItem(
                icon = Icons.Outlined.CalendarToday,
                label = "Weekly review day",
                value = labelFor(REVIEW_DAYS, weeklyReviewDay).ifBlank { "Not set" },
                detail = "Pick a day to receive your weekly recap.",
            )
            ProfileUsageItem(
                icon = Icons.Outlined.CalendarToday,
                label = "Stats week starts",
                value = labelFor(USAGE_WEEK_START_DAYS, weekUsageReportStartDay),
                detail = "Sets the start day for the fixed week view in Stats.",
            )

            Spacer(Modifier.height(18.dp))
        }
    }
}

}

@Composable
private fun ProfileJourneyCard(
    stats: ProfileStats,
    name: String,
    goalHours: Int,
) {
    val goalMinutes = (goalHours * 60).coerceAtLeast(1)
    val progressPercent = (stats.todayMinutes * 100 / goalMinutes).coerceIn(0, 100)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.TrendingUp,
                        contentDescription = null,
                        tint = ProfileAccent,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = if (name.isBlank()) "Journey Statistics" else "$name's Journey",
                        fontSize = 15.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                }

                Text(
                    text = "${stats.todayMinutes}m / ${goalHours}h today",
                    fontSize = 12.5.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                    color = ProfileAccent,
                )
            }

            // Stat columns in a row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                JourneyStatItem(label = "Current Streak", value = "${stats.streakDays}d")
                JourneyStatItem(label = "Best Streak", value = "${stats.bestStreakDays}d")
                JourneyStatItem(label = "Total Time", value = formatMinutes(stats.allTimeMinutes))
                JourneyStatItem(label = "Sessions", value = "${stats.sessions}")
            }

            // Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Today's Goal Progress",
                        fontSize = 12.scaledSp,
                        color = DarkTextSecondary,
                    )
                    Text(
                        "$progressPercent%",
                        fontSize = 12.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = if (progressPercent >= 100) Color(0xFF34D399) else ProfileAccent,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(DarkSurfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (progressPercent / 100f).coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (progressPercent >= 100) Color(0xFF34D399) else ProfileAccent),
                    )
                }
            }
        }
    }
}

@Composable
private fun JourneyStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 16.scaledSp,
            fontWeight = FontWeight.Bold,
            color = DarkTextPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.scaledSp,
            color = DarkTextMuted,
        )
    }
}

@Composable
private fun ProfileFieldCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 12.scaledSp,
                fontWeight = FontWeight.Bold,
                color = ProfileAccent,
                letterSpacing = 0.5.scaledSp,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.scaledSp,
                    color = DarkTextSecondary,
                    lineHeight = 16.scaledSp,
                )
            }
        }
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChoiceChips(
    choices: List<Pair<String, String>>,
    selected: Set<String>,
    enabled: Boolean,
    multiSelect: Boolean,
    onSelected: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { (id, label) ->
            val isSelected = id in selected
            val chipBg = if (isSelected) ProfileAccent.copy(alpha = 0.16f) else DarkSurfaceVariant
            val chipBorder = if (isSelected) ProfileAccent else DarkBorder
            val chipText = if (isSelected) ProfileAccent else DarkTextSecondary

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(chipBg)
                    .border(1.dp, chipBorder, RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled) { onSelected(id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = ProfileAccent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = label,
                        fontSize = 13.scaledSp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else chipText,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileUsageItem(
    icon: ImageVector,
    label: String,
    value: String,
    detail: String,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(ProfileAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = ProfileAccent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    label,
                    fontSize = 16.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    fontSize = 13.5.scaledSp,
                    color = DarkTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                value,
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 14.scaledSp,
                fontWeight = FontWeight.Bold,
                color = ProfileAccent,
                maxLines = 1,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp),
            color = DarkBorder.copy(alpha = 0.85f),
            thickness = 1.dp,
        )
    }
}

private data class ProfileStats(
    val todayMinutes: Int,
    val streakDays: Int,
    val bestStreakDays: Int,
    val allTimeMinutes: Int,
    val sessions: Int,
)

private fun labelFor(options: List<Pair<String, String>>, value: String): String =
    options.firstOrNull { it.first == value }?.second ?: value

private fun breakMinutes(value: String): Int? =
    when (value) {
        "short_frequent" -> 5
        "balanced" -> 10
        "long_infrequent" -> 15
        "no_break" -> 0
        else -> null
    }

private fun formatMinutes(minutes: Int): String {
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (remainder == 0) "${hours}h" else "${hours}h ${remainder}m"
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

private fun JSONArray?.toStringSet(): Set<String> =
    if (this == null) emptySet()
    else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }.toSet()

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key).takeIf { it > 0 }

private const val PROFILE_KEY = "user_profile"

private val OCCUPATION_LABELS = listOf(
    "student" to "Student",
    "professional" to "Professional",
    "freelancer" to "Freelancer",
    "creator" to "Creator",
    "other" to "Other",
)

private val WAKE_TIMES = listOf(
    "05:00" to "5 am", "06:00" to "6 am", "07:00" to "7 am", "08:00" to "8 am",
    "09:00" to "9 am", "10:00" to "10 am", "11:00" to "11 am",
)

private val FOCUS_GOALS = listOf(
    "deep_work" to "Deep Work", "study" to "Study", "no_social" to "No Social Media",
    "reading" to "Reading", "exercise" to "Exercise", "creative" to "Creative",
    "coding" to "Coding", "writing" to "Writing",
)

private val SLEEP_TIMES = listOf(
    "21:00" to "9 pm", "22:00" to "10 pm", "23:00" to "11 pm", "00:00" to "12 am",
    "01:00" to "1 am", "02:00" to "2 am",
)

private val CHRONOTYPES = listOf(
    "morning" to "Early morning (5–9 am)", "midday" to "Late morning (9–12)",
    "afternoon" to "Afternoon (12–5 pm)", "evening" to "Evening (5–9 pm)",
    "night" to "Late night (9 pm+)", "flexible" to "Varies day to day",
)

private val FOCUS_LENGTHS = listOf(15, 25, 45, 60, 90)

private val BREAK_STYLES = listOf(
    "short_frequent" to "Short & frequent",
    "balanced" to "Balanced",
    "long_infrequent" to "Long & infrequent",
    "no_break" to "No breaks",
)

private val DISTRACTION_TRIGGERS = listOf(
    "social" to "Social media", "video" to "Videos / TV", "news" to "News",
    "games" to "Games", "shopping" to "Shopping", "messaging" to "Messaging",
)

private val MOTIVATION_STYLES = listOf(
    "streaks" to "Streaks", "stats" to "Stats & charts",
    "milestones" to "Milestones", "quotes" to "Daily quotes",
)

private val REVIEW_DAYS = listOf(
    "sun" to "Sun", "mon" to "Mon", "tue" to "Tue", "wed" to "Wed",
    "thu" to "Thu", "fri" to "Fri", "sat" to "Sat",
)

private val USAGE_WEEK_START_DAYS = listOf(
    "sun" to "Sunday", "mon" to "Monday", "tue" to "Tuesday", "wed" to "Wednesday",
    "thu" to "Thursday", "fri" to "Friday", "sat" to "Saturday",
)
