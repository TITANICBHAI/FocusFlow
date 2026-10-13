package com.tbtechs.focusflow.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.ui.graphics.Color
import com.tbtechs.focusflow.ui.navigation.Routes
import com.tbtechs.focusflow.ui.support.GuideSection
import com.tbtechs.focusflow.ui.support.GuideStep
import com.tbtechs.focusflow.ui.support.HowToUseScreen

@androidx.compose.runtime.Composable
internal fun SettingsHowToUseScreen(
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit,
) {
    HowToUseScreen(
        isOnboarding = false,
        onBack = onBack,
        onGetStarted = {},
        additionalSections = GUARDED_ADJUSTMENT_GUIDE,
        additionalNote = "PINs protect changes that weaken a block; not every Settings value requires one.",
        onOpenRoute = onOpenRoute,
    )
}

private val GUARDED_ADJUSTMENT_GUIDE = listOf(
    GuideSection(
        title = "Focus and active sessions",
        icon = Icons.Outlined.VpnKey,
        iconBg = Color(0xFF2E1065),
        iconTint = Color(0xFFC084FC),
        steps = listOf(
            GuideStep(
                heading = "Why does stopping Focus ask for my Focus Session PIN?",
                body = "When a Focus Session PIN is set, stopping an active session early requires that PIN before app blocking ends. Without a Focus Session PIN, the app still asks you to confirm the stop.",
                destination = Routes.FOCUS,
                destinationLabel = "Open Focus",
            ),
            GuideStep(
                heading = "Why is full-duration Focus protected?",
                body = "Turning off the rule that keeps Focus active until a task ends asks for the Focus Session PIN when one is set. This prevents a running task from becoming easier to interrupt.",
                destination = Routes.DEFENSE,
                destinationLabel = "Open Defense",
            ),
            GuideStep(
                heading = "Why does clearing saved Standalone Block apps ask for a password?",
                body = "Clearing the saved app list from Active Blocks asks for the Defense PIN when PIN protection is enabled, then shows a separate confirmation. This is different from stopping an active Focus session, which uses the Focus Session PIN.",
                destination = Routes.ACTIVE,
                destinationLabel = "Open Active Blocks",
            ),
            GuideStep(
                heading = "Why does Clear All Tasks ask for my Focus Session PIN?",
                body = "If a Focus session is active and a Focus Session PIN is set, confirming Clear All Tasks asks for that PIN before clearing the tasks and ending the session. Canceling the PIN prompt keeps the active task and clears the other tasks.",
                destination = Routes.SETTINGS,
                destinationLabel = "Open Settings",
            ),
            GuideStep(
                heading = "Why does deleting a task ask for my Focus Session PIN?",
                body = "When a Focus Session PIN is configured, deleting a task from its edit screen asks for that PIN before removing the task. Without one, the task is deleted directly.",
                destination = Routes.HOME,
                destinationLabel = "Open Schedule",
            ),
        ),
    ),
    GuideSection(
        title = "Apps and keywords",
        icon = Icons.Outlined.Apps,
        iconBg = Color(0xFF1E1B4B),
        iconTint = Color(0xFF818CF8),
        steps = listOf(
            GuideStep(
                heading = "When can I remove apps from Always-On or VPN lists?",
                body = "Removing an app is blocked while Focus Mode or Standalone Block is active. When no block is active, removal asks for the Defense PIN if PIN protection is enabled. Adding apps is intentionally allowed without a PIN.",
                destination = Routes.ALWAYS_ON,
                destinationLabel = "Open Always-On",
            ),
            GuideStep(
                heading = "Can I remove blocked keywords?",
                body = "Adding keywords does not require a PIN. Removing or clearing keywords asks for the Defense PIN when PIN protection is enabled; an active Standalone Block can lock removals completely.",
                destination = Routes.KEYWORD_BLOCKER,
                destinationLabel = "Open Keyword Blocker",
            ),
            GuideStep(
                heading = "Why is removing a VPN-blocked app locked?",
                body = "A Focus session or Standalone Block prevents removing protected VPN apps. Outside an active block, removal asks for the Defense PIN when protection is enabled. Adding another VPN-blocked app remains available.",
                destination = Routes.VPN_BLOCK_LIST,
                destinationLabel = "Open VPN Block List",
            ),
        ),
    ),
    GuideSection(
        title = "Schedules and allowances",
        icon = Icons.Outlined.CalendarMonth,
        iconBg = Color(0xFF451A03),
        iconTint = Color(0xFFFBBF24),
        steps = listOf(
            GuideStep(
                heading = "Why does changing a group schedule ask for my Defense PIN?",
                body = "New schedules can be added without a PIN. When Defense PIN protection is enabled, editing a schedule, removing apps, disabling its window or VPN protection, shortening its duration, or deleting it can require the Defense PIN. An active Standalone Block also prevents deleting a schedule.",
                destination = Routes.DEFENSE,
                destinationLabel = "Open Defense schedules",
            ),
            GuideStep(
                heading = "Why can't I remove an existing daily allowance?",
                body = "While a block is active, existing allowance values are read-only and those entries cannot be removed. You can add and configure allowances for new apps; clearing the list then removes only those new additions. After the block, removing an entry asks for the Defense PIN when protection is enabled.",
                destination = Routes.DEFENSE,
                destinationLabel = "Open Defense allowances",
            ),
            GuideStep(
                heading = "What can I change during a Standalone Block?",
                body = "The active block's expiry and existing blocked apps are locked. The setup flow still allows adding apps or extending the block; saving those updates asks for the Focus Session PIN when one is set.",
                destination = Routes.BLOCK_DEFENSE,
                destinationLabel = "Open Standalone Block",
            ),
            GuideStep(
                heading = "Why does stopping a Standalone Block ask for my Focus Session PIN?",
                body = "If a Focus Session PIN is set, clearing an active Standalone Block asks for it before the block ends. Without one, FocusFlow still asks you to confirm.",
                destination = Routes.ACTIVE,
                destinationLabel = "Open Active Blocks",
            ),
        ),
    ),
    GuideSection(
        title = "Protection settings and access",
        icon = Icons.Outlined.Security,
        iconBg = Color(0xFF042F2E),
        iconTint = Color(0xFF2DD4BF),
        steps = listOf(
            GuideStep(
                heading = "Why can't I turn off some Defense settings?",
                body = "Turning off Always-On Enforcement, System Guard, Shorts/Reels blocking, Screen Dimmer, Vibration Harassment, Sound Alert, Network Blocking, or VPN Self-Healing is guarded. An active Focus or Standalone Block can block the change; otherwise a configured Defense PIN is required. Turning protections on is allowed without a PIN, and not every Defense setting uses this rule.",
                destination = Routes.DEFENSE,
                destinationLabel = "Open Defense",
            ),
            GuideStep(
                heading = "Why are permission settings unavailable during a block?",
                body = "Permission settings are disabled while Focus or Standalone Block is active, so an enforcement permission cannot be changed mid-block.",
                destination = Routes.PERMISSIONS,
                destinationLabel = "Open Permissions",
            ),
            GuideStep(
                heading = "Why are launcher settings locked?",
                body = "Launcher settings cannot be modified while a Standalone Block is active.",
                destination = Routes.HOME_LAUNCHER_SETUP,
                destinationLabel = "Open Launcher settings",
            ),
            GuideStep(
                heading = "Why must I verify a password before changing it?",
                body = "Removing or replacing an existing Focus Session or Defense PIN first asks you to verify that current PIN. You can set a new PIN when one is not already protecting the feature.",
                destination = Routes.PASSWORD_PROTECTION,
                destinationLabel = "Open PIN Protection",
            ),
        ),
    ),
    GuideSection(
        title = "Protection changes",
        icon = Icons.Outlined.Security,
        iconBg = Color(0xFF450A0A),
        iconTint = Color(0xFFF87171),
        steps = listOf(
            GuideStep(
                heading = "Why do some changes stay locked instead of showing a PIN prompt?",
                body = "An active-block lock is stronger than a PIN prompt for some actions: the change is unavailable until the block ends. Adding a safeguard may still be allowed when removing or weakening it is not.",
            ),
        ),
    ),
)