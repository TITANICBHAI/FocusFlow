package com.tbtechs.focusflow.ui.defense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.tbtechs.focusflow.data.model.DailyAllowanceEntry
import com.tbtechs.focusflow.data.model.StandaloneBlockAndAllowanceConfig
import com.tbtechs.focusflow.ui.SettingsViewModel

@Composable
fun StandaloneBlockSetupScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit = {},
) {
    val settings by settingsViewModel.settings.collectAsState()
    var modalVisible by remember { mutableStateOf(false) }
    val active = settings.standaloneBlockActive &&
        settings.standaloneBlockUntilMs > System.currentTimeMillis() &&
        settings.standaloneBlockPackages.isNotEmpty()

    Scaffold(topBar = { TopAppBar(title = { Text("Standalone Block") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(if (active) "Standalone block is active" else "Block apps without a task", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (active) {
                            "${settings.standaloneBlockPackages.size} app(s) blocked until ${java.text.DateFormat.getDateTimeInstance().format(settings.standaloneBlockUntilMs)}."
                        } else {
                            "Choose apps, an expiry time, and optional daily allowances. This block runs independently of scheduled focus tasks."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Button(onClick = { modalVisible = true }) {
                androidx.compose.material3.Icon(Icons.Outlined.Block, contentDescription = null)
                Text(if (active) "Manage Block" else "Choose Apps to Block", modifier = Modifier.padding(start = 8.dp))
            }
            Button(onClick = onBack) { Text("Back") }
        }
    }

    StandaloneBlockModal(
        visible = modalVisible,
        blockedPackages = settings.standaloneBlockPackages,
        blockUntilMs = settings.standaloneBlockUntilMs,
        locked = active,
        onSave = { packages, untilMs, allowances, _, pin ->
            settingsViewModel.setStandaloneBlockAndAllowance(
                StandaloneBlockAndAllowanceConfig(
                    standaloneBlockActive = packages.isNotEmpty() && untilMs != null,
                    standaloneBlockPackages = packages,
                    standaloneBlockUntilMs = untilMs ?: 0L,
                    allowanceEntries = allowances,
                    pinHash = pin,
                ),
            )
            modalVisible = false
        },
        onClose = { modalVisible = false },
        verifyPin = settingsViewModel::verifyPin,
    )
}
