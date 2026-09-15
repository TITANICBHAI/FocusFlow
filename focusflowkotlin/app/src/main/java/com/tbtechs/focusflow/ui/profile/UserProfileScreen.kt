package com.tbtechs.focusflow.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.repository.FocusSessionRepository
import com.tbtechs.focusflow.data.repository.SettingsRepository
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Profile questionnaire and profile summary.
 *
 * Profile metadata intentionally remains in the legacy SharedPreferences JSON
 * envelope so an imported user profile survives the hybrid-to-native migration.
 * Enforcement settings continue to go through SettingsViewModel elsewhere.
 */
@Composable
fun UserProfileScreen(
    settingsRepository: SettingsRepository,
    isEditMode: Boolean = true,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    onImportBackup: (suspend () -> Unit)? = null,
    focusSessionRepository: FocusSessionRepository? = null,
) {
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
    var saving by remember { mutableStateOf(false) }
    var importBusy by remember { mutableStateOf(false) }
    var usageVisible by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<ProfileStats?>(null) }

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
            }
            runCatching {
                settingsRepository.putString(PROFILE_KEY, profile.toString())
                settingsRepository.putString("onboarding_complete", "true")
            }
            // NEEDS: AppSettings/SettingsViewModel fields for defaultDuration,
            // pomodoroDuration, and pomodoroBreak. The current Kotlin model does
            // not expose those legacy settings, so profile JSON remains complete
            // while this screen avoids writing an unenforced duplicate key.
            // NEEDS: NotificationRepository wiring for morning-digest and weekly
            // report scheduling after the profile save.
            saving = false
            editing = false
            onFinished()
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
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Your Profile" else "Tell Us About You") },
                navigationIcon = {
                    if (isEditMode) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isEditMode && !editing) {
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Outlined.Create, contentDescription = "Edit profile")
                        }
                    }
                    if (!isEditMode) {
                        TextButton(onClick = ::skip) { Text("Skip") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!isEditMode) {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("New here, or returning?", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Answer a few short questions, or import a previous backup to restore your setup.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (onImportBackup != null) {
                            OutlinedButton(
                                onClick = {
                                    if (!importBusy) {
                                        importBusy = true
                                        scope.launch {
                                            try {
                                                onImportBackup()
                                            } finally {
                                                importBusy = false
                                            }
                                        }
                                    }
                                },
                                enabled = !importBusy,
                            ) {
                                Text(if (importBusy) "Importing…" else "Import previous backup")
                            }
                        } else {
                            Text(
                                "NEEDS: connect the native backup picker callback before importing from this screen.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    "This helps FocusFlow personalize daily summaries and weekly reports. Everything stays on this device.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (isEditMode && stats != null && stats!!.hasHistory) {
                JourneyCard(stats = stats!!, name = name, goalHours = dailyGoalHours)
            }

            ProfileSection("What's your name?") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    enabled = editing,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Alex") },
                    singleLine = true,
                )
            }
            ProfileSection("What best describes you?") {
                ChoiceChips(
                    choices = listOf(
                        "student" to "Student",
                        "professional" to "Professional",
                        "freelancer" to "Freelancer",
                        "creator" to "Creator",
                        "other" to "Other",
                    ),
                    selected = setOf(occupation),
                    enabled = editing,
                    multiSelect = false,
                ) { occupation = if (occupation == it) "" else it }
            }
            ProfileSection("Daily focus goal (hours)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { dailyGoalHours = (dailyGoalHours - 1).coerceAtLeast(1) },
                        enabled = editing && dailyGoalHours > 1,
                    ) { Text("−") }
                    Text(
                        "$dailyGoalHours h",
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedButton(
                        onClick = { dailyGoalHours = (dailyGoalHours + 1).coerceAtMost(16) },
                        enabled = editing && dailyGoalHours < 16,
                    ) { Text("+") }
                }
                Text(
                    "FocusFlow tracks your daily progress toward this goal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ProfileSection("When do you usually wake up?") {
                ChoiceChips(
                    choices = WAKE_TIMES,
                    selected = setOf(wakeUpTime),
                    enabled = editing,
                    multiSelect = false,
                ) { wakeUpTime = if (wakeUpTime == it) "" else it }
            }
            ProfileSection("What are your main focus goals?") {
                ChoiceChips(
                    choices = FOCUS_GOALS,
                    selected = focusGoals,
                    enabled = editing,
                    multiSelect = true,
                ) { focusGoals = focusGoals.toggle(it) }
            }
            ProfileSection("When do you usually go to sleep?") {
                ChoiceChips(
                    choices = SLEEP_TIMES,
                    selected = setOf(sleepTime),
                    enabled = editing,
                    multiSelect = false,
                ) { sleepTime = if (sleepTime == it) "" else it }
            }
            ProfileSection("When do you focus best?") {
                ChoiceChips(
                    choices = CHRONOTYPES,
                    selected = setOf(chronotype),
                    enabled = editing,
                    multiSelect = false,
                ) { chronotype = if (chronotype == it) "" else it }
            }
            ProfileSection("Your ideal focus block") {
                ChoiceChips(
                    choices = FOCUS_LENGTHS.map { it.toString() to "$it min" },
                    selected = setOf(focusLength?.toString() ?: ""),
                    enabled = editing,
                    multiSelect = false,
                ) { focusLength = if (focusLength?.toString() == it) null else it.toIntOrNull() }
            }
            ProfileSection("How do you like to break?") {
                ChoiceChips(
                    choices = BREAK_STYLES,
                    selected = setOf(breakStyle),
                    enabled = editing,
                    multiSelect = false,
                ) { breakStyle = if (breakStyle == it) "" else it }
            }
            ProfileSection("What pulls you off track most?") {
                ChoiceChips(
                    choices = DISTRACTION_TRIGGERS,
                    selected = distractionTriggers,
                    enabled = editing,
                    multiSelect = true,
                ) { distractionTriggers = distractionTriggers.toggle(it) }
            }
            ProfileSection("What motivates you?") {
                ChoiceChips(
                    choices = MOTIVATION_STYLES,
                    selected = motivationStyle,
                    enabled = editing,
                    multiSelect = true,
                ) { motivationStyle = motivationStyle.toggle(it) }
            }
            ProfileSection("Weekly review day") {
                ChoiceChips(
                    choices = REVIEW_DAYS,
                    selected = setOf(weeklyReviewDay),
                    enabled = editing,
                    multiSelect = false,
                ) { weeklyReviewDay = if (weeklyReviewDay == it) "" else it }
            }

            OutlinedButton(
                onClick = { usageVisible = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("How your information is used")
            }

            if (editing) {
                Button(
                    onClick = ::save,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (saving) "Saving…" else if (isEditMode) "Save Changes" else "Save & Continue") }
            }
            if (!isEditMode) {
                TextButton(onClick = ::skip, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip for now — I'll set this up later in Settings")
                }
            }
        }
    }

    if (usageVisible) {
        AlertDialog(
            onDismissRequest = { usageVisible = false },
            title = { Text("How your profile is used") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UsageRow("Daily focus goal", "${dailyGoalHours}h", "Used for progress summaries.")
                    UsageRow("Wake-up time", wakeUpTime.ifBlank { "Not set" }, "Used for the morning digest.")
                    UsageRow("Focus goals", "${focusGoals.size} selected", "Used to label focus summaries.")
                    UsageRow("Sleep time", sleepTime.ifBlank { "Not set" }, "Defines your available focus window.")
                    UsageRow("Best focus time", chronotype.ifBlank { "Not set" }, "Helps with task scheduling suggestions.")
                    UsageRow("Ideal focus block", focusLength?.let { "$it min" } ?: "Not set", "Default length for new focus blocks.")
                    UsageRow("Break style", breakStyle.ifBlank { "Not set" }, "Sets the default break length.")
                    UsageRow("Distraction triggers", "${distractionTriggers.size} selected", "Stored for future personalization.")
                    UsageRow("Motivation style", "${motivationStyle.size} selected", "Guides the encouragement shown in the app.")
                    UsageRow("Weekly review day", weeklyReviewDay.ifBlank { "Not set" }, "Used for weekly recap scheduling.")
                }
            },
            confirmButton = { Button(onClick = { usageVisible = false }) { Text("OK") } },
        )
    }
}

private data class ProfileStats(
    val todayMinutes: Int,
    val streakDays: Int,
    val bestStreakDays: Int,
    val allTimeMinutes: Int,
    val sessions: Int,
) {
    val hasHistory: Boolean
        get() = todayMinutes > 0 || streakDays > 0 || allTimeMinutes > 0 || sessions > 0
}

@Composable
private fun JourneyCard(stats: ProfileStats, name: String, goalHours: Int) {
    val goalMinutes = (goalHours * 60).coerceAtLeast(1)
    val progress = (stats.todayMinutes * 100 / goalMinutes).coerceIn(0, 100)
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(if (name.isBlank()) "Your journey" else "$name's journey", style = MaterialTheme.typography.titleMedium)
            }
            Text("Streak: ${stats.streakDays}d", style = MaterialTheme.typography.bodyMedium)
            Text("Today: ${formatMinutes(stats.todayMinutes)} · $progress% of goal", style = MaterialTheme.typography.bodyMedium)
            Text("All time: ${formatMinutes(stats.allTimeMinutes)} · ${stats.sessions} sessions", style = MaterialTheme.typography.bodyMedium)
            Text("Best streak: ${stats.bestStreakDays}d", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (progress >= 100) "Daily goal hit — ${stats.todayMinutes}m focused today"
                else "${goalMinutes - stats.todayMinutes.coerceAtMost(goalMinutes)}m to go to today's ${goalHours}h goal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun ChoiceChips(
    choices: List<Pair<String, String>>,
    selected: Set<String>,
    enabled: Boolean,
    multiSelect: Boolean,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.chunked(2).forEach { rowChoices ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowChoices.forEach { (id, label) ->
                    FilterChip(
                        selected = id in selected,
                        enabled = enabled,
                        onClick = { onSelected(id) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageRow(label: String, value: String, detail: String) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, color = MaterialTheme.colorScheme.primary)
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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