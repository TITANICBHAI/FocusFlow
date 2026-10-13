package com.tbtechs.focusflow.ui.support

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.scaledSp

data class GuideStep(
    val heading: String,
    val body: String,
    val destination: String? = null,
    val destinationLabel: String? = null,
)

data class GuideSection(
    val title: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val steps: List<GuideStep>,
)

internal val GUIDE = listOf(
    GuideSection(
        "All Modes",
        Icons.Outlined.Layers,
        Color(0xFF1E1B4B),
        Color(0xFF818CF8),
        listOf(
            GuideStep("Focus Mode", "Focus Mode is connected to a task. Start a task from the Focus tab, choose the apps allowed during that session, and start Focus Mode when you are ready to work."),
            GuideStep("Standalone Block", "Standalone Block does not need a task. Open it from the Focus tab, choose the apps you want blocked, and set how long the block should last. It is the best choice when you want a timed block right now."),
            GuideStep("Keyword Blocker", "Keyword Blocker watches visible text, searches, and URLs for words you add. When a blocked word appears, FocusFlow sends the current app home. It works independently of an app list or focus session."),
            GuideStep("VPN Network Protection", "VPN protection cuts internet access for the apps you select. Use the VPN list for always-on network blocking, or add VPN protection to a standalone block or group schedule when that block is running."),
            GuideStep("Group Schedules", "Group schedules are recurring block windows. Add several apps to one window, choose the days and times, and let the same group run automatically every week. A schedule can also include VPN protection."),
            GuideStep("Home Launcher", "Home Launcher replaces your home screen with a focused launcher. Choose Classic or Glassy, select the apps shown in the drawer, and use launcher protections to make switching away harder during a standalone block."),
        ),
    ),
    GuideSection(
        "Absolute Blocking",
        Icons.Outlined.Security,
        Color(0xFF450A0A),
        Color(0xFFF87171),
        listOf(
            GuideStep("Start with Standalone Block", "Go to the Focus tab, choose Standalone Block, select every app you want blocked, and set the time. This works without creating a task and stays active until the timer ends."),
            GuideStep("Grant the important permissions first", "Open Settings → Permissions and grant Accessibility and Usage Access so FocusFlow can detect and stop blocked apps. Grant Device Admin as an additional layer before starting a serious standalone block; it adds resistance to force-stop and uninstall escape paths, but it is not a magic guarantee by itself."),
            GuideStep("Turn on Protect system controls", "In the Defense tab, enable Protect system controls before the block starts. This protects system screens and navigation paths that could otherwise be used to weaken an active block."),
            GuideStep("Add the extra layers you need", "From the Defense tab, enable Network Protection, launcher protections, aversion deterrents, Shorts/Reels blocking, or other safeguards. These layers work alongside the app block instead of replacing it."),
            GuideStep("Know what absolute means", "FocusFlow blocks the apps and escape routes you configured. Keep emergency, phone, launcher, and other protected system apps available, and do not treat any Android protection as a substitute for emergency access."),
        ),
    ),
    GuideSection(
        "What Can and Cannot Change",
        Icons.Outlined.Lock,
        Color(0xFF451A03),
        Color(0xFFFBBF24),
        listOf(
            GuideStep("Always-On and VPN lists", "You can add more apps to the Always-On list or VPN list while protection is running. Removing apps from either list is locked during an active Focus Mode or Standalone Block so the block cannot be weakened halfway through."),
            GuideStep("Keyword Blocker", "You can add keywords without a password. Removing keywords or clearing the list is protected, and an active standalone block can lock those removals completely."),
            GuideStep("Group schedules", "You can add a new group schedule without a PIN. When Defense PIN protection is enabled, editing a schedule, removing apps, disabling a window or its VPN protection, shortening its duration, or deleting it can require the Defense PIN. An active Standalone Block can also prevent deleting a schedule."),
            GuideStep("Why FocusFlow locks changes", "A protection tool is only useful if it cannot be quietly weakened after it starts. FocusFlow allows safer additions, but guards removals, shorter windows, disabled toggles, and other changes that reduce protection."),
        ),
    ),
    GuideSection(
        "PIN System",
        Icons.Outlined.VpnKey,
        Color(0xFF2E1065),
        Color(0xFFC084FC),
        listOf(
            GuideStep("Focus Session PIN", "When configured, the Focus Session PIN is required to stop an active Focus Mode session early or turn off the full-duration rule. Without a PIN, stopping still requires the normal confirmation."),
            GuideStep("Defense PIN", "The Defense PIN guards actions that weaken protection: disabling protected Defense toggles, removing apps from Always-On or VPN lists, removing keywords, and changing protected settings."),
            GuideStep("Group schedules are guarded more heavily", "You can add a new schedule without a PIN. When Defense PIN protection is enabled, editing, removing apps, disabling protection, shortening, or deleting a schedule can require the Defense PIN. An active Standalone Block can prevent deleting it."),
            GuideStep("Adding is intentionally easier in three lists", "Adding apps to Always-On, adding apps to the VPN list, and adding keywords do not normally require a PIN. The protection is focused on preventing removal or weakening, not on stopping you from adding another safeguard."),
            GuideStep("Set both passwords before a serious block", "Open Defense → PIN Protection to configure the Focus Session PIN and Defense PIN. Keep them somewhere safe; forgetting them can leave a protection active until its normal expiry or until the correct recovery path is used."),
        ),
    ),
    GuideSection(
        "Other Toggles",
        Icons.Outlined.Tune,
        Color(0xFF042F2E),
        Color(0xFF2DD4BF),
        listOf(
            GuideStep("Protect system controls", "Blocks or redirects sensitive system-control paths such as power-menu, Settings, and other escape routes. It cannot be turned off while Focus Mode or Standalone Block is active."),
            GuideStep("Network Protection and self-heal", "Network Protection uses the local VPN to cut internet access for selected apps. Self-heal watches the VPN and helps restore it if Android disconnects it. Android VPN permission is required."),
            GuideStep("Launcher protections", "Home Launcher protections can lock the default launcher choice, protect against uninstall attempts, and keep FocusFlow in control during a standalone block. Configure them from Home Launcher or the Defense tab."),
            GuideStep("Aversion deterrents", "Vibration, screen dimming, and sound alerts react when a blocked app opens. They are optional deterrents that reinforce the block; they do not replace Accessibility, Usage Access, or the block list."),
            GuideStep("Content and Focus settings", "Shorts/Reels blocking, Auto-enable Focus Mode, and keeping focus active for the full task duration change how enforcement behaves. Enable only the layers that match your routine, then test them before starting a long block."),
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToUseScreen(
    isOnboarding: Boolean = false,
    onBack: () -> Unit,
    onGetStarted: () -> Unit,
    additionalSections: List<GuideSection> = emptyList(),
    additionalNote: String? = null,
    onOpenRoute: (String) -> Unit = {},
) {
    val dimensions = LocalFocusFlowDimensions.current
    var expanded by remember { mutableStateOf<Int?>(null) }
    val sections = remember(additionalSections) { GUIDE + additionalSections }
    if (isOnboarding) {
        BackHandler { onGetStarted() }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (!isOnboarding) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = DarkTextPrimary)
                        }
                    }
                },
                actions = {
                    if (isOnboarding) {
                        TextButton(onClick = onGetStarted) {
                            Text(
                                text = "Skip",
                                fontSize = 14.scaledSp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkTextSecondary,
                            )
                        }
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
                .padding(horizontal = dimensions.screenPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
        ) {
            // Header Section matching 2.jpg
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (isOnboarding) "Welcome to FocusFlow" else "How to Use FocusFlow",
                    fontSize = 24.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (isOnboarding) {
                        "A quick tour before you get started"
                    } else {
                        "Blocking modes, protection rules, and where to find each setting"
                    },
                    fontSize = 14.scaledSp,
                    color = DarkTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            if (!additionalNote.isNullOrBlank()) {
                Text(
                    text = additionalNote,
                    fontSize = 12.scaledSp,
                    lineHeight = 17.scaledSp,
                    color = DarkTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 5 Accordion Cards matching 2.jpg
            sections.forEachIndexed { index, section ->
                val isOpen = expanded == index
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .clickable { expanded = if (isOpen) null else index }
                        .padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            // Icon Badge
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(section.iconBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = section.icon,
                                    contentDescription = null,
                                    tint = section.iconTint,
                                    modifier = Modifier.size(22.dp),
                                )
                            }

                            // Title
                            Text(
                                text = section.title,
                                fontSize = 16.scaledSp,
                                fontWeight = FontWeight.Bold,
                                color = DarkTextPrimary,
                                modifier = Modifier.weight(1f),
                            )

                            // Chevron
                            Icon(
                                imageVector = if (isOpen) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = if (isOpen) "Collapse" else "Expand",
                                tint = DarkTextMuted,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        if (isOpen) {
                            HorizontalDivider(color = DarkBorder)
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                section.steps.forEachIndexed { stepIndex, step ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Text(
                                            text = "${stepIndex + 1}.",
                                            fontSize = 14.scaledSp,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandPrimary,
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = step.heading,
                                                fontSize = 14.scaledSp,
                                                fontWeight = FontWeight.Bold,
                                                color = DarkTextPrimary,
                                            )
                                            Text(
                                                text = step.body,
                                                fontSize = 13.scaledSp,
                                                lineHeight = 18.scaledSp,
                                                color = DarkTextSecondary,
                                            )
                                            val destination = step.destination
                                            val destinationLabel = step.destinationLabel
                                            if (destination != null && destinationLabel != null) {
                                                TextButton(onClick = { onOpenRoute(destination) }) {
                                                    Text(
                                                        text = destinationLabel,
                                                        fontSize = 13.scaledSp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = BrandPrimary,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom CTA Button matching 2.jpg
            if (isOnboarding) {
                Button(
                    onClick = onGetStarted,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Got it — let's start",
                            fontSize = 16.scaledSp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
