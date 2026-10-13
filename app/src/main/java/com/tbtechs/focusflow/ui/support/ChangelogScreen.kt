package com.tbtechs.focusflow.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp

private data class ChangeSection(val heading: String, val items: List<String>)
private data class ChangeEntry(val version: String, val date: String, val sections: List<ChangeSection>)

/*
 * Static release data mirrors the source CHANGELOG array. The c-prefixed entries
 * are retained because they are part of the legacy release history.
 */
private val CHANGELOG = listOf(
    ChangeEntry("1.1.4", "September 2026", listOf(
        ChangeSection("Protection controls", listOf(
            "Improved app-list loading, scheduled block labels, VPN permission handling, and active-protection guidance.",
        )),
        ChangeSection("Release Metadata", listOf("Updated FocusFlow to v1.1.4 (build 13).")),
    )),
    ChangeEntry("1.1.3", "September 2026", listOf(
        ChangeSection("Home Launcher", listOf(
            "Added Classic and Glassy launcher themes with wallpaper-aware styling, a focused home layout, and configurable app-drawer controls.",
            "Updated the fixed FocusFlow launcher shortcut to use the official FocusFlow app icon in both themes and their configuration previews.",
        )),
        ChangeSection("Focus Session Reliability", listOf(
            "Fixed stale Focus Mode and timed standalone state lingering after a session expired or the device restarted, so old sessions no longer keep apps blocked.",
            "Deferred enforcement actions now re-check the current session state before redirecting or dismissing screens, preventing delayed callbacks from acting on an ended focus session.",
        )),
        ChangeSection("Release Metadata", listOf("Updated FocusFlow to v1.1.3 (build 14).")),
        ChangeSection("Protect System Controls", listOf("Fixed protected Android settings screens when the feature is enabled.")),
        ChangeSection("Daily Allowance", listOf("Daily allowance enforcement improvements are still in progress and will be addressed in a later update.")),
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
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "What's New",
                        color = DarkTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.scaledSp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = DarkTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(BrandPrimary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.RocketLaunch,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
                Text("Changelog", fontSize = 28.scaledSp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                Text(
                    "Every improvement, fix, and new feature across all versions.",
                    fontSize = 13.scaledSp,
                    color = DarkTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            CHANGELOG.forEach { entry ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = DarkCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder.copy(alpha = 0.9f)),
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BrandPrimary)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (entry.version.startsWith("c")) entry.version else "v${entry.version}",
                                    fontSize = 14.scaledSp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            Text(entry.date, fontSize = 13.scaledSp, color = DarkTextMuted)
                        }
                        entry.sections.forEach { section ->
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        sectionIcon(section.heading),
                                        contentDescription = null,
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        section.heading,
                                        fontSize = 16.scaledSp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DarkTextPrimary,
                                    )
                                }
                                section.items.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 8.dp)
                                                .size(4.dp)
                                                .background(BrandPrimary, CircleShape),
                                        )
                                        Text(
                                            item,
                                            modifier = Modifier.weight(1f),
                                            fontSize = 14.scaledSp,
                                            color = DarkTextSecondary,
                                            lineHeight = 20.scaledSp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "Privacy Policy: titanicbhai.github.io/FocusFlow",
                fontSize = 12.scaledSp,
                color = DarkTextMuted,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

private fun sectionIcon(heading: String): ImageVector = when {
    heading.contains("fix", ignoreCase = true) ||
        heading.contains("reliab", ignoreCase = true) -> Icons.Outlined.CheckCircle
    heading.contains("permission", ignoreCase = true) ||
        heading.contains("protect", ignoreCase = true) -> Icons.Outlined.Security
    heading.contains("block", ignoreCase = true) ||
        heading.contains("focus", ignoreCase = true) -> Icons.Outlined.Tune
    heading.contains("database", ignoreCase = true) ||
        heading.contains("stability", ignoreCase = true) -> Icons.Outlined.BugReport
    else -> Icons.Outlined.AutoAwesome
}