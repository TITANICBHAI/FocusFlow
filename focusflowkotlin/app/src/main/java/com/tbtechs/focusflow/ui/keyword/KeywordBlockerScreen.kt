package com.tbtechs.focusflow.ui.keyword

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.defense.BlockedWordsModal

private data class KeywordPreset(
    val label: String,
    val description: String,
    val words: List<String>,
)

private val keywordPresets = listOf(
    KeywordPreset("Doomscroll bait", "Outrage headlines, viral controversy, breaking-news loops", listOf("breaking", "shocking", "must see", "gone wrong", "you wont believe", "controversy", "drama", "reaction")),
    KeywordPreset("Social-media drama", "Celebrity feuds, beef tracks, trending arguments", listOf("cancelled", "feud", "expose", "beef", "callout", "roasted", "clapback", "tea")),
    KeywordPreset("Shorts/Reels bait", "Short-form-video rabbit-hole terms", listOf("short", "reel", "tiktok", "fyp", "viral", "trending", "compilation", "pov")),
    KeywordPreset("Impulse-buy traps", "Sale-pressure words that pull you into shopping apps", listOf("flash sale", "deal of the day", "limited time", "lightning deal", "cart", "buy now", "discount")),
    KeywordPreset("Gambling triggers", "Betting lines, casino lure, and loot-box language", listOf("bet", "odds", "spin", "jackpot", "casino", "parlay", "wager", "free spins")),
    KeywordPreset("NSFW content", "Adult-content terms across browsers, search, and feeds", listOf("nsfw", "porn", "xxx", "onlyfans", "adult", "nude")),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordBlockerScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit = {},
) {
    val settings by settingsViewModel.settings.collectAsState()
    val words = settings.blockedWords
    val active = words.isNotEmpty()
    val locked = settings.standaloneBlockActive &&
        settings.standaloneBlockPackages.isNotEmpty() &&
        settings.standaloneBlockUntilMs > System.currentTimeMillis()
    var modalVisible by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var pinPrompt by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }

    fun clearWords() {
        if (settings.pinProtectionEnabled) {
            pinPrompt = true
        } else {
            settingsViewModel.setBlockedWords(emptyList())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Keyword Blocker") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Outlined.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text("Block by keyword", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "When a blocked word appears in a URL, search bar, or visible text, the Accessibility Service sends the app home.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item {
                Card {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (active) "Active" else "Inactive", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (active) "${words.size} keyword${if (words.size == 1) "" else "s"} on the block list"
                                else "Add keywords below to start filtering content",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(Icons.Outlined.TextFields, contentDescription = null)
                    }
                }
            }
            item {
                Button(onClick = { modalVisible = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (active) Icons.Outlined.TextFields else Icons.Outlined.AddCircle, contentDescription = null)
                    Text(if (active) "Manage Keywords" else "Add Keywords", modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (active && !locked) {
                item {
                    TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Clear all keywords", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (locked) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Text("Locked — block is active", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            item {
                Text("QUICK PRESETS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Tap a category to add a curated set of keywords. You can edit the full list anytime.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(keywordPresets) { preset ->
                Card(onClick = {
                    val existing = words.map(String::lowercase).toSet()
                    settingsViewModel.setBlockedWords(words + preset.words.filterNot { it.lowercase() in existing })
                }) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.label, style = MaterialTheme.typography.titleSmall)
                            Text(preset.description, style = MaterialTheme.typography.bodySmall)
                            Text("+${preset.words.size} keywords", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                        Icon(Icons.Outlined.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Keyword detection runs on the device. Nothing is sent to the cloud. Accessibility access is required.",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    BlockedWordsModal(
        visible = modalVisible,
        words = words,
        locked = locked,
        requireDefensePin = settings.pinProtectionEnabled,
        verifyPin = settingsViewModel::verifyPin,
        onSave = settingsViewModel::setBlockedWords,
        onClose = { modalVisible = false },
    )

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all keywords?") },
            text = { Text("Removes all ${words.size} keywords from the block list. This cannot be undone.") },
            confirmButton = {
                Button(onClick = { confirmClear = false; clearWords() }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
    if (pinPrompt) {
        AlertDialog(
            onDismissRequest = { pinPrompt = false },
            title = { Text("Defense Password Required") },
            text = {
                androidx.compose.material3.OutlinedTextField(pin, { pin = it }, label = { Text("Password") }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    if (settingsViewModel.verifyPin(pin)) {
                        settingsViewModel.setBlockedWords(emptyList())
                        pinPrompt = false
                        pin = ""
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { pinPrompt = false }) { Text("Cancel") } },
        )
    }
}
