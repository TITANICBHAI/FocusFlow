package com.tbtechs.focusflow.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.FocusSession
import com.tbtechs.focusflow.data.repository.NetworkBlockStatus
import com.tbtechs.focusflow.data.repository.UsageStatsRepository
import com.tbtechs.focusflow.data.repository.VpnRepository
import kotlinx.coroutines.delay
import org.json.JSONArray

enum class ActiveStatusLevel {
    INACTIVE,
    ACTIVE,
    WARNING,
}

data class ActiveStatusSummary(
    val level: ActiveStatusLevel,
    val description: String,
    val activeLayersCount: Int,
)

/**
 * Evaluates the live protection state across all 7 layers:
 * 1. Focus Session
 * 2. Standalone Block
 * 3. Always-On Apps
 * 4. Daily Allowance
 * 5. Keyword Blocker
 * 6. VPN Blocking
 * 7. Scheduled Blocks
 */
fun evaluateActiveProtection(
    settings: AppSettings,
    focusSession: FocusSession?,
    vpnStatus: NetworkBlockStatus?,
    hasAccessibility: Boolean = true,
    hasUsageStats: Boolean = true,
): ActiveStatusSummary {
    val now = System.currentTimeMillis()
    val isFocusActive = focusSession?.isActive == true
    val isStandaloneActive = settings.standaloneBlockActive &&
        settings.standaloneBlockPackages.isNotEmpty() &&
        settings.standaloneBlockUntilMs > now
    val isAlwaysOnActive = settings.alwaysBlockEnabled && settings.alwaysBlockPackages.isNotEmpty()

    val allowancePackages = runCatching {
        val arr = JSONArray(settings.dailyAllowanceConfigJson ?: "[]")
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("package")?.takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())
    val hasDailyAllowance = allowancePackages.isNotEmpty()
    val hasKeywords = settings.blockedWords.isNotEmpty()
    val isVpnRunning = vpnStatus?.running == true
    val activeSchedules = settings.recurringBlockSchedules.filter { it.isActiveNow() }
    val hasActiveSchedule = activeSchedules.isNotEmpty()

    val vpnProblem = vpnStatus != null && (
        vpnStatus.failedPackages.isNotEmpty() ||
            vpnStatus.invalidPackages.isNotEmpty() ||
            vpnStatus.state in setOf(
                "permission_missing",
                "another_vpn_active",
                "package_registration_failed",
                "startup_failed",
            )
        )

    val activeCount = listOf(
        isFocusActive,
        isStandaloneActive,
        isAlwaysOnActive,
        hasDailyAllowance,
        hasKeywords,
        isVpnRunning,
        hasActiveSchedule,
    ).count { it }

    return when {
        vpnProblem -> ActiveStatusSummary(
            level = ActiveStatusLevel.WARNING,
            description = "Protection warning: VPN block requires attention. Tap to view Active status.",
            activeLayersCount = activeCount,
        )
        activeCount > 0 -> ActiveStatusSummary(
            level = ActiveStatusLevel.ACTIVE,
            description = "Protections active ($activeCount layers running). Tap to view Active status.",
            activeLayersCount = activeCount,
        )
        else -> ActiveStatusSummary(
            level = ActiveStatusLevel.INACTIVE,
            description = "No protection active. Tap to view Active status.",
            activeLayersCount = 0,
        )
    }
}

private fun com.tbtechs.focusflow.data.model.RecurringBlockSchedule.isActiveNow(): Boolean {
    if (!enabled) return false
    val now = java.time.ZonedDateTime.now()
    val minute = now.hour * 60 + now.minute
    val start = startHour * 60
    val end = endHour * 60
    val day = now.dayOfWeek.value % 7
    return day in daysOfWeek && if (start <= end) {
        minute in start until end
    } else {
        minute >= start || minute < end
    }
}

/**
 * Shared persistent Active status indicator appearing across all main tabs.
 */
@Composable
fun ActiveStatusIndicator(
    focusSession: FocusSession? = null,
    settings: AppSettings? = null,
    vpnStatus: NetworkBlockStatus? = null,
    onOpenActiveBlocks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The host screen owns the configured ViewModels and passes their current
    // values here. Do not call the default Compose viewModel() factory: the
    // native ViewModels require constructor dependencies and have no
    // no-argument constructor.
    val resolvedSettings = settings ?: AppSettings()
    val resolvedSession = focusSession

    val vpnRepo = remember {
        VpnRepository(context, com.tbtechs.focusflow.di.AppModule.restoreGate)
    }
    val usageRepo = remember { UsageStatsRepository(context) }

    val resolvedVpnStatus by produceState(initialValue = vpnStatus) {
        if (vpnStatus != null) {
            value = vpnStatus
            return@produceState
        }
        while (true) {
            value = runCatching { vpnRepo.getNetworkBlockStatus() }.getOrNull()
            delay(5_000)
        }
    }

    val permissions by produceState(initialValue = true to true) {
        while (true) {
            val acc = runCatching { usageRepo.hasAccessibilityPermission() }.getOrDefault(true)
            val use = runCatching { usageRepo.hasPermission() }.getOrDefault(true)
            value = acc to use
            delay(10_000)
        }
    }

    val summary = remember(resolvedSettings, resolvedSession, resolvedVpnStatus, permissions) {
        evaluateActiveProtection(
            settings = resolvedSettings,
            focusSession = resolvedSession,
            vpnStatus = resolvedVpnStatus,
            hasAccessibility = permissions.first,
            hasUsageStats = permissions.second,
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                onClick = onOpenActiveBlocks,
                onClickLabel = "Open active protections",
            )
            .semantics {
                contentDescription = summary.description
                role = Role.Button
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        EcgHeartPulseMonitor(
            level = summary.level,
            activeCount = summary.activeLayersCount,
        )
    }
}

/**
 * Small, quiet protection-status mark used in the top app bars.
 *
 * The status remains tappable, but the indicator itself stays visually simple
 * so it reads as a navigation/status icon rather than a second control.
 */
@Composable
private fun EcgHeartPulseMonitor(
    level: ActiveStatusLevel,
    activeCount: Int,
    modifier: Modifier = Modifier,
) {
    val iconColor = when {
        level == ActiveStatusLevel.WARNING -> Color(0xFFFBBF24)
        activeCount > 0 -> Color(0xFF2BAE66)
        else -> Color(0xFFCBD5E1)
    }

    Canvas(
        modifier = modifier.size(
             width = 21.dp,
             height = 19.dp,
        ),
    ) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.02f, h * 0.58f)
            lineTo(w * 0.18f, h * 0.58f)
            lineTo(w * 0.30f, h * 0.10f)
            lineTo(w * 0.43f, h * 0.92f)
            lineTo(w * 0.53f, h * 0.43f)
            lineTo(w * 0.61f, h * 0.58f)
            lineTo(w * 0.78f, h * 0.58f)
        }

        drawPath(
            path = path,
            color = iconColor,
            style = Stroke(
                 width = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        drawCircle(
            color = iconColor,
            radius = h * 0.14f,
            center = Offset(
                x = w * 0.88f,
                y = h * 0.58f,
            ),
            style = Stroke(
                 width = 1.6.dp.toPx(),
            ),
        )
    }
}

/** Backward-compatible alias for existing call sites. */
@Composable
fun ActiveHeaderButton(
    focusSession: FocusSession?,
    settings: AppSettings,
    onOpenActiveBlocks: () -> Unit,
) {
    ActiveStatusIndicator(
        focusSession = focusSession,
        settings = settings,
        onOpenActiveBlocks = onOpenActiveBlocks,
    )
}
