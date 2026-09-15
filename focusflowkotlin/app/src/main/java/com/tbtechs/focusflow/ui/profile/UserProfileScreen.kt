package com.tbtechs.focusflow.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.repository.SettingsRepository
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    settingsRepository: SettingsRepository,
    isEditMode: Boolean = true,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var occupation by remember { mutableStateOf("") }
    var dailyGoal by remember { mutableStateOf("4") }
    var wakeTime by remember { mutableStateOf("") }
    var focusGoals by remember { mutableStateOf(setOf<String>()) }
    var sleepTime by remember { mutableStateOf("") }
    var focusLength by remember { mutableStateOf("") }
    var breakStyle by remember { mutableStateOf("") }
    var usageVisible by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { settingsRepository.getString(PROFILE_KEY) }.getOrNull()?.let { json ->
            runCatching {
                val value = JSONObject(json)
                name = value.optString("name")
                occupation = value.optString("occupation")
                dailyGoal = value.optString("dailyGoalHours", "4")
                wakeTime = value.optString("wakeUpTime")
                sleepTime = value.optString("sleepTime")
                focusLength = value.optString("focusSessionLength")
                breakStyle = value.optString("breakStyle")
                focusGoals = value.optJSONArray("focusGoals").toStringSet()
            }
        }
    }

    fun save() {
        scope.launch {
            saving = true
            val profile = JSONObject().apply {
                put("name", name.trim())
                put("occupation", occupation)
                put("dailyGoalHours", dailyGoal.toIntOrNull()?.coerceIn(1, 16) ?: 4)
                put("wakeUpTime", wakeTime)
                put("sleepTime", sleepTime)
                put("focusSessionLength", focusLength)
                put("breakStyle", breakStyle)
                put("focusGoals", JSONArray(focusGoals.toList()))
            }
            settingsRepository.putString(PROFILE_KEY, profile.toString())
            saving = false
            onFinished()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Your Profile" else "Tell Us About You") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("What's your name?") }, singleLine = true)
            ChoiceRow("What best describes you?", listOf("Student", "Professional", "Freelancer", "Creator", "Other"), occupation) { occupation = it }
            OutlinedTextField(
                dailyGoal,
                { dailyGoal = it.filter(Char::isDigit).take(2) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Daily focus goal (hours)") },
                singleLine = true,
            )
            ChoiceRow("When do you usually wake up?", listOf("5 am", "6 am", "7 am", "8 am", "9 am", "10 am", "11 am"), wakeTime) { wakeTime = it }
            MultiChoiceRow("Main focus goals", listOf("Deep Work", "Study", "No Social Media", "Reading", "Exercise", "Creative", "Coding", "Writing"), focusGoals) {
                focusGoals = if (focusGoals.contains(it)) focusGoals - it else focusGoals + it
            }
            ChoiceRow("When do you usually go to sleep?", listOf("9 pm", "10 pm", "11 pm", "12 am", "1 am", "2 am"), sleepTime) { sleepTime = it }
            ChoiceRow("Your ideal focus block", listOf("15 min", "25 min", "45 min", "60 min", "90 min"), focusLength) { focusLength = it }
            ChoiceRow("How do you like to break?", listOf("Short & frequent", "Balanced", "Long & infrequent", "No breaks"), breakStyle) { breakStyle = it }
            TextButton(onClick = { usageVisible = true }) {
                Icon(Icons.Outlined.Info, contentDescription = null)
                Text("How your information is used")
            }
            Button(onClick = ::save, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text(if (saving) "Saving…" else if (isEditMode) "Save Changes" else "Save & Continue")
            }
            if (!isEditMode) TextButton(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
        }
    }

    if (usageVisible) {
        AlertDialog(
            onDismissRequest = { usageVisible = false },
            title = { Text("How your information is used") },
            text = { Text("Your profile stays on this device and personalizes focus goals, summaries, and scheduling preferences. It is not shared.") },
            confirmButton = { Button(onClick = { usageVisible = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun ChoiceRow(title: String, choices: List<String>, selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.take(4).forEach { choice ->
                FilterChip(selected = selected == choice, onClick = { onSelected(if (selected == choice) "" else choice) }, label = { Text(choice) })
            }
        }
        if (choices.size > 4) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.drop(4).forEach { choice ->
                FilterChip(selected = selected == choice, onClick = { onSelected(if (selected == choice) "" else choice) }, label = { Text(choice) })
            }
        }
    }
}

@Composable
private fun MultiChoiceRow(title: String, choices: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { choice ->
                FilterChip(selected = selected.contains(choice), onClick = { onToggle(choice) }, label = { Text(choice) })
            }
        }
    }
}

private fun JSONArray?.toStringSet(): Set<String> =
    if (this == null) emptySet() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }.toSet()

private const val PROFILE_KEY = "user_profile"