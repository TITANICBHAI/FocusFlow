package com.tbtechs.focusflow.ui.keyword

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.defense.BlockedWordsModal
import com.tbtechs.focusflow.ui.home.FocusFlowInternalHeader
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp

private data class KeywordPreset(
    val label: String,
    val description: String,
    val words: List<String>,
)

private val keywordPresets = listOf(
    KeywordPreset(
        "Doomscroll bait",
        "Outrage headlines, viral controversy, breaking-news loops",
        listOf("breaking", "shocking", "must see", "gone wrong", "you wont believe", "controversy", "drama", "reaction"),
    ),
    KeywordPreset(
        "Social-media drama",
        "Celebrity feuds, beef tracks, trending arguments",
        listOf("cancelled", "feud", "expose", "beef", "callout", "roasted", "clapback", "tea"),
    ),
    KeywordPreset(
        "Shorts/Reels bait",
        "Short-form-video rabbit-hole terms",
        listOf("short", "reel", "tiktok", "fyp", "viral", "trending", "compilation", "pov"),
    ),
    KeywordPreset(
        "Impulse-buy traps",
        "Sale-pressure words that pull you into shopping apps",
        listOf("flash sale", "deal of the day", "limited time", "lightning deal", "cart", "buy now", "discount"),
    ),
    KeywordPreset(
        "Gambling triggers",
        "Betting lines, casino lure, and loot-box language",
        listOf("bet", "odds", "spin", "jackpot", "casino", "parlay", "wager", "free spins"),
    ),
    KeywordPreset(
        "NSFW content",
        "Adult-content terms across browsers, search, and feeds",
        listOf("nsfw", "porn", "xxx", "onlyfans", "adult", "nude"),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordBlockerScreen(
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
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
    var pendingPreset by remember { mutableStateOf<KeywordPreset?>(null) }

    fun clearWords() {
        if (settings.pinProtectionEnabled) {
            pinPrompt = true
        } else {
            settingsViewModel.setBlockedWords(emptyList())
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            FocusFlowInternalHeader(
                title = "Keyword Blocker",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Top Header Card matching 3e_6
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrandPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.TextFields,
                                contentDescription = null,
                                tint = BrandPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Block by keyword",
                                fontSize = 15.scaledSp,
                                fontWeight = FontWeight.Bold,
                                color = DarkTextPrimary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "When a blocked word appears in a URL, search bar, or on-screen text, the Accessibility Service redirects away from the content.",
                                fontSize = 11.scaledSp,
                                lineHeight = 18.scaledSp,
                                color = DarkTextSecondary,
                            )
                        }
                    }
                }
            }

            // Status Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                    .size(12.dp)
                                        .clip(CircleShape)
                                        .background(if (active) Color(0xFF10B981) else DarkTextMuted),
                                )
                                Text(
                                    text = if (active) "Active" else "Inactive",
                                    fontSize = 15.scaledSp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (active) Color(0xFF34D399) else DarkTextSecondary,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (active) "${words.size} keyword${if (words.size == 1) "" else "s"} on the block list"
                                else "Add keywords below to start filtering content",
                                fontSize = 11.scaledSp,
                                color = DarkTextSecondary,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.TextFields,
                                contentDescription = null,
                                tint = DarkTextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            // Action Button
            item {
                Button(
                    onClick = { modalVisible = true },
                    modifier = Modifier
                        .fillMaxWidth()
                    .height(46.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                ) {
                    Icon(
                        if (active) Icons.Outlined.TextFields else Icons.Outlined.AddCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (active) "Manage Keywords" else "Add Keywords",
                        fontSize = 15.scaledSp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }

            if (active && !locked) {
                item {
                    TextButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Clear all keywords",
                            color = Color(0xFFEF4444),
                            fontSize = 13.scaledSp,
                        )
                    }
                }
            }

            if (locked) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF451A03))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                            Text(
                                "Locked — block is active. Keywords cannot be removed until it ends.",
                                color = Color(0xFFFBBF24),
                                 fontSize = 11.scaledSp,
                            )
                        }
                    }
                }
            }

            // QUICK PRESETS Section Header
            item {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "QUICK PRESETS",
                    fontSize = 11.scaledSp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.scaledSp,
                        color = DarkTextSecondary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap a category to add a curated set of keywords. You can edit the full list anytime.",
                        fontSize = 12.scaledSp,
                        color = DarkTextMuted,
                    )
                }
            }

            // Preset Cards matching 3e_6
            items(keywordPresets) { preset ->
                val existing = words.map(String::lowercase).toSet()
                val additions = preset.words.filterNot { it.lowercase() in existing }
                val allAdded = additions.isEmpty()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .clickable {
                            if (allAdded) {
                                pendingPreset = preset.copy(description = "Every keyword in this preset is already on your block list.")
                            } else {
                                pendingPreset = preset.copy(
                                    description = "Adds ${additions.size} keyword${if (additions.size == 1) "" else "s"} to your block list: ${additions.take(5).joinToString(", ")}${if (additions.size > 5) "…" else ""}",
                                    words = additions,
                                )
                            }
                        }
                        .padding(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.label,
                                fontSize = 15.scaledSp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkTextPrimary,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = preset.description,
                                fontSize = 11.scaledSp,
                                lineHeight = 17.scaledSp,
                                color = DarkTextSecondary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (allAdded) "All added ✓" else "+${preset.words.size} keywords",
                                fontSize = 11.scaledSp,
                                fontWeight = FontWeight.Medium,
                                color = if (allAdded) Color(0xFF10B981) else BrandPrimary,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (allAdded) Color(0xFF064E3B) else BrandPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (allAdded) Icons.Outlined.Check else Icons.Outlined.Add,
                                contentDescription = null,
                                tint = if (allAdded) Color(0xFF34D399) else BrandPrimary,
                                 modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = DarkTextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Keyword detection runs entirely on-device. Nothing is sent to the cloud. Accessibility service is required.",
                        fontSize = 12.scaledSp,
                        lineHeight = 16.scaledSp,
                        color = DarkTextMuted,
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
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
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Clear all keywords?") },
            text = { Text("Removes all ${words.size} keywords from the block list. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClear = false
                        clearWords()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                ) {
                    Text("Clear All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    if (pinPrompt) {
        AlertDialog(
            onDismissRequest = { pinPrompt = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Defense Password Required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your defense password to clear blocked keywords.", fontSize = 13.scaledSp)
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedBorderColor = BrandPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = DarkTextPrimary,
                            unfocusedTextColor = DarkTextPrimary,
                        ),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (settingsViewModel.verifyPin(pin)) {
                            settingsViewModel.setBlockedWords(emptyList())
                            pinPrompt = false
                            pin = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                ) {
                    Text("Clear", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { pinPrompt = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    pendingPreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingPreset = null },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = {
                Text(
                    if (preset.words.isEmpty()) "${preset.label} already added"
                    else "Add ${preset.label}?",
                )
            },
            text = { Text(preset.description, fontSize = 13.scaledSp) },
            confirmButton = {
                if (preset.words.isNotEmpty()) {
                    Button(
                        onClick = {
                            settingsViewModel.setBlockedWords(words + preset.words)
                            pendingPreset = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    ) {
                        Text("Add", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPreset = null }) {
                    Text(if (preset.words.isEmpty()) "Close" else "Cancel", color = DarkTextSecondary)
                }
            },
        )
    }
}
