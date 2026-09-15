package com.tbtechs.focusflow.ui.support

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class GuideStep(val heading: String, val body: String)
private data class GuideSection(val title: String, val steps: List<GuideStep>)

private val GUIDE = listOf(
    GuideSection(
        "All Modes",
        listOf(
            GuideStep("Focus Mode", "Focus Mode is connected to a task. Start a task from the Focus tab, choose the apps allowed during that session, and start Focus Mode when you are ready to work."),
            GuideStep("Standalone Block", "Standalone Block does not need a task. Open it from the Focus tab, choose the apps you want blocked, and set how long the block should last."),
            GuideStep("Keyword Blocker", "Keyword Blocker watches visible text, searches, and URLs for words you add. When a blocked word appears, FocusFlow sends the current app home."),
            GuideStep("VPN Network Protection", "VPN protection cuts internet access for selected apps. Use the VPN list for always-on network blocking, or add VPN protection to a standalone block or group schedule."),
            GuideStep("Group Schedules", "Group schedules are recurring block windows. Add apps, choose the days and times, and let the same group run automatically every week."),
            GuideStep("Home Launcher", "Home Launcher replaces your home screen with a focused launcher. Choose Classic or Glassy, select the apps shown in the drawer, and use launcher protections during standalone blocks."),
        ),
    ),
    GuideSection(
        "Absolute Blocking",
        listOf(
            GuideStep("Start with Standalone Block", "Go to the Focus tab, choose Standalone Block, select every app you want blocked, and set the time."),
            GuideStep("Grant the important permissions first", "Open Settings → Permissions and grant Accessibility and Usage Access. Grant Device Admin as an additional layer before a serious standalone block."),
            GuideStep("Turn on Protect system controls", "In the Defense tab, enable Protect system controls before the block starts. This protects system screens and navigation paths that could weaken an active block."),
            GuideStep("Add the extra layers you need", "From Defense, enable Network Protection, launcher protections, aversion deterrents, Shorts/Reels blocking, or other safeguards."),
            GuideStep("Know what absolute means", "FocusFlow blocks the apps and escape routes you configured. Keep emergency, phone, launcher, and other protected system apps available."),
        ),
    ),
    GuideSection(
        "What Can and Cannot Change",
        listOf(
            GuideStep("Always-On and VPN lists", "You can add apps while protection is running. Removing apps from either list is locked during active Focus Mode or Standalone Block."),
            GuideStep("Keyword Blocker", "You can add keywords without a password. Removing keywords or clearing the list is protected."),
            GuideStep("Group schedules", "You can manage schedules when they are not locked. Edits, removals, shortening a window, or deleting a schedule can require the Defense PIN."),
            GuideStep("Why FocusFlow locks changes", "FocusFlow allows safer additions but guards removals, shorter windows, disabled toggles, and other changes that reduce protection."),
        ),
    ),
    GuideSection(
        "PIN System",
        listOf(
            GuideStep("Focus Session PIN", "The Focus Session PIN guards ending an active Focus Mode session. Starting a session can mean committing to its full duration."),
            GuideStep("Defense PIN", "The Defense PIN guards actions that weaken protection: disabling toggles, removing apps, removing keywords, and changing protected settings."),
            GuideStep("Group schedules are guarded more heavily", "Adding, editing, shortening, or deleting a group schedule can require the Defense PIN."),
            GuideStep("Adding is intentionally easier", "Adding apps to Always-On, adding apps to the VPN list, and adding keywords do not normally require a PIN."),
            GuideStep("Set both passwords before a serious block", "Open Defense → PIN Protection to configure both passwords. Keep them somewhere safe."),
        ),
    ),
    GuideSection(
        "Other Toggles",
        listOf(
            GuideStep("Protect system controls", "This blocks or redirects sensitive system-control paths. It cannot be turned off while Focus Mode or Standalone Block is active."),
            GuideStep("Network Protection and self-heal", "Network Protection uses the local VPN to cut internet access for selected apps. Android VPN permission is required."),
            GuideStep("Launcher protections", "Home Launcher protections can lock the default launcher choice, protect against uninstall attempts, and keep FocusFlow in control."),
            GuideStep("Aversion deterrents", "Vibration, screen dimming, and sound alerts react when a blocked app opens. They reinforce the block but do not replace required permissions."),
            GuideStep("Content and Focus settings", "Shorts/Reels blocking, Auto-enable Focus Mode, and full-duration focus settings change enforcement behavior. Test them before a long block."),
        ),
    ),
)

@Composable
fun HowToUseScreen(
    isOnboarding: Boolean = false,
    onBack: () -> Unit,
    onGetStarted: () -> Unit,
) {
    var expanded by remember { mutableStateOf<Int?>(0) }
    if (isOnboarding) {
        BackHandler { onGetStarted() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (isOnboarding) "Welcome to FocusFlow" else "How to Use FocusFlow")
                        if (isOnboarding) {
                            Text("A quick tour before you get started", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                navigationIcon = {
                    if (!isOnboarding) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isOnboarding) TextButton(onClick = onGetStarted) { Text("Skip") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GUIDE.forEachIndexed { index, section ->
                val isOpen = expanded == index
                Card {
                    Column {
                        TextButton(
                            onClick = { expanded = if (isOpen) null else index },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Info, contentDescription = null)
                                Text(section.title, modifier = Modifier.weight(1f))
                                Icon(
                                    if (isOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = if (isOpen) "Collapse" else "Expand",
                                )
                            }
                        }
                        if (isOpen) {
                            Column(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                section.steps.forEachIndexed { stepIndex, step ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("${stepIndex + 1}.", style = MaterialTheme.typography.titleSmall)
                                        Column {
                                            Text(step.heading, style = MaterialTheme.typography.titleSmall)
                                            Text(step.body, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (isOnboarding) {
                Button(onClick = onGetStarted, modifier = Modifier.fillMaxWidth()) {
                    Text("Got it — let’s start")
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = null)
                }
            }
        }
    }
}