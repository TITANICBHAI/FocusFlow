package com.tbtechs.focusflow.ui.support

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
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class ChangeSection(val heading: String, val items: List<String>)
private data class ChangeEntry(val version: String, val date: String, val sections: List<ChangeSection>)

/*
 * Static release data mirrors the source CHANGELOG array. The c-prefixed entries
 * are retained because they are part of the legacy release history.
 */
private val CHANGELOG = listOf(
    ChangeEntry("1.4.0", "September 2026", listOf(
        ChangeSection("Stats and insights", listOf("Added local-only analytics, insight cards, trend views, achievements, and session debriefs.")),
        ChangeSection("Reliability", listOf("Improved database recovery, background enforcement, and navigation stability.")),
    )),
    ChangeEntry("1.1.3", "September 2026", listOf(
        ChangeSection("Release Metadata", listOf("Updated FocusFlow to v1.1.3 (build 14).")),
        ChangeSection("Protect System Controls", listOf("Fixed protected Android settings screens when the feature is enabled.")),
        ChangeSection("Daily Allowance", listOf("Daily allowance enforcement improvements remain in progress.")),
    )),
    ChangeEntry("1.1.1", "August 2026", listOf(
        ChangeSection("Database Reliability", listOf("Solved database errors that could interrupt loading and prevent settings or task changes from being saved.")),
    )),
    ChangeEntry("1.1.0", "August 2026", listOf(
        ChangeSection("More Reliable App Blocking", listOf("Added a UsageStats foreground-app watchdog and improved recent-app return handling.", "Improved OEM fallback behavior when Usage Access is unavailable.")),
        ChangeSection("FocusFlow Safety", listOf("Focus, Stats, Settings, and onboarding remain exempt from app blocking while protection is active.")),
    )),
    ChangeEntry("1.0.9", "August 2026", listOf(
        ChangeSection("Block Overlay Cleanup", listOf("Removed the duplicate React Native blocked-app banner; native blocking remains unchanged.")),
        ChangeSection("Navigation and Focus Workflow", listOf("Reordered bottom navigation and improved task-form keyboard behavior.")),
        ChangeSection("Backup and Import Safety", listOf("Task imports now handle complete-database ID collisions safely.")),
    )),
    ChangeEntry("1.0.8", "August 2026", listOf(
        ChangeSection("Accessibility and Enforcement", listOf("Improved recovery when Android restricts or stops AccessibilityService.")),
    )),
    ChangeEntry("1.0.7", "August 2026", listOf(
        ChangeSection("Focus and Defense", listOf("Improved PIN protection, standalone blocks, and defense-state recovery.")),
    )),
    ChangeEntry("1.0.6", "June 2026", listOf(
        ChangeSection("Stability", listOf("Improved boot recovery, alarms, notifications, and settings persistence.")),
    )),
    ChangeEntry("1.0.5", "June 2026", listOf(
        ChangeSection("Withdrawn release", listOf("This release was withdrawn.")),
    )),
    ChangeEntry("1.0.4", "May 2026", listOf(
        ChangeSection("Blocking and Permissions", listOf("Improved app blocking, permission guidance, launcher setup, and VPN protection.")),
    )),
    ChangeEntry("1.0.3", "May 2026", listOf(
        ChangeSection("Focus sessions", listOf("Improved active-session controls, task alarms, and break handling.")),
    )),
    ChangeEntry("1.0.2", "May 2026", listOf(
        ChangeSection("Core features", listOf("Added standalone blocks, daily allowance tools, blocked keywords, and stronger protection controls.")),
    )),
    ChangeEntry("1.0.1", "May 2026", listOf(
        ChangeSection("Fixes", listOf("Improved startup reliability and task scheduling.")),
    )),
    ChangeEntry("c1.0.9", "April 2026", listOf(
        ChangeSection("Initial native hardening", listOf("Improved AccessibilityService enforcement and native block overlays.")),
    )),
    ChangeEntry("c1.0.9", "April 2026", listOf(
        ChangeSection("Protection", listOf("Added additional device and system-control protection.")),
    )),
    ChangeEntry("c1.0.7", "April 2026", listOf(
        ChangeSection("Launcher", listOf("Added FocusLauncherActivity and launcher protection options.")),
    )),
    ChangeEntry("c1.0.6", "April 2026", listOf(
        ChangeSection("Widgets and reports", listOf("Added the home-screen widget and weekly temptation report groundwork.")),
    )),
    ChangeEntry("c1.0.5", "April 2026", listOf(
        ChangeSection("Enforcement", listOf("Added native foreground service and accessibility enforcement foundations.")),
    )),
    ChangeEntry("c1.0.4", "April 2026", listOf(
        ChangeSection("Permissions", listOf("Added permission setup guidance and recovery paths.")),
    )),
    ChangeEntry("c1.0.3", "April 2026", listOf(
        ChangeSection("Tasks", listOf("Added scheduled tasks, focus sessions, and reminders.")),
    )),
    ChangeEntry("c1.0.2", "April 2026", listOf(
        ChangeSection("FocusDay", listOf("Added the first complete focus-session and app-blocking workflows.")),
    )),
    ChangeEntry("1.0.0", "Initial Release", listOf(
        ChangeSection("FocusFlow", listOf("Initial release with focus sessions, app blocking, schedules, permissions, and settings.")),
    )),
)

@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What's New") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.RocketLaunch, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Changelog", style = MaterialTheme.typography.headlineMedium)
                Text("Every improvement, fix, and new feature across all versions.", style = MaterialTheme.typography.bodySmall)
            }
            CHANGELOG.forEach { entry ->
                Card {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (entry.version.startsWith("c")) entry.version else "v${entry.version}", style = MaterialTheme.typography.titleMedium)
                            Text(entry.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        entry.sections.forEach { section ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(section.heading, style = MaterialTheme.typography.titleSmall)
                                section.items.forEach { item ->
                                    Text("• $item", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            Text("Privacy Policy: titanicbhai.github.io/FocusFlow", style = MaterialTheme.typography.bodySmall)
        }
    }
}