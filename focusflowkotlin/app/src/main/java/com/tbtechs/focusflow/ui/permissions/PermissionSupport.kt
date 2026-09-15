package com.tbtechs.focusflow.ui.permissions

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.tbtechs.focusflow.data.repository.LauncherController
import com.tbtechs.focusflow.data.repository.UsageStatsRepository

enum class PermissionId {
    ACCESSIBILITY, USAGE, BATTERY, NOTIFICATIONS, DEVICE_ADMIN, OVERLAY, MEDIA, VPN, LAUNCHER,
}

enum class PermissionStatus { GRANTED, DENIED, UNKNOWN }

data class PermissionDefinition(
    val id: PermissionId,
    val title: String,
    val description: String,
    val whyNeeded: String,
    val brokenWithout: List<String>,
    val optional: Boolean,
)

val permissionDefinitions = listOf(
    PermissionDefinition(PermissionId.ACCESSIBILITY, "Accessibility Service", "Reads only the app name and redirects blocked apps instantly. Nothing leaves your device.", "This is how FocusFlow redirects you the moment you open a blocked app.", listOf("App blocking will not work", "Blocked apps open freely during focus sessions", "Blocks can be bypassed"), false),
    PermissionDefinition(PermissionId.USAGE, "Usage Access", "Lets FocusFlow see which app is in the foreground.", "Without this, FocusFlow cannot detect which app you opened.", listOf("Foreground app detection fails", "App blocking can silently fail", "Stats and focus tracking are inaccurate"), false),
    PermissionDefinition(PermissionId.NOTIFICATIONS, "Notifications", "Shows task reminders and the persistent focus notification.", "Required for alerts and keeping the foreground service visible.", listOf("No task reminders", "The focus notification disappears", "Android may kill the blocking service"), false),
    PermissionDefinition(PermissionId.BATTERY, "Battery Optimization", "Keeps FocusFlow alive in the background on aggressive OEM ROMs.", "Samsung, Xiaomi, Realme, and OnePlus may otherwise kill background services.", listOf("Blocking can stop after the screen turns off", "Focus sessions may stop enforcing"), true),
    PermissionDefinition(PermissionId.OVERLAY, "Appear on Top", "Draws the block screen directly over blocked apps.", "It prevents a brief flash of the blocked app before redirect.", listOf("The overlay may open inside FocusFlow", "A blocked app may flash briefly"), true),
    PermissionDefinition(PermissionId.MEDIA, "Media & Files", "Lets you choose a custom wallpaper for the block screen.", "The default wallpaper works without this access.", listOf("Custom block-screen wallpaper is unavailable"), true),
    PermissionDefinition(PermissionId.VPN, "VPN Network Blocking", "Cuts internet access for selected apps when enabled.", "Android requires one-time consent before FocusFlow can create a VPN.", listOf("Selected apps keep internet access", "Network Blocking cannot start"), true),
    PermissionDefinition(PermissionId.DEVICE_ADMIN, "Device Admin", "Adds resistance to OEM force-stop paths during a focus session.", "Some OEMs expose force-stop paths from recents or system controls.", listOf("Stopping FocusFlow may be easier on some OEMs"), true),
    PermissionDefinition(PermissionId.LAUNCHER, "Home Launcher", "Lets FocusFlow intercept app launches before Android opens them.", "The launcher can prevent a blocked app from appearing even briefly.", listOf("Blocked apps may flash before the overlay", "Launcher filtering is unavailable"), true),
)

suspend fun checkPermission(context: Context, id: PermissionId): PermissionStatus = try {
    when (id) {
        PermissionId.ACCESSIBILITY -> if (UsageStatsRepository(context).hasAccessibilityPermission()) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.USAGE -> if (UsageStatsRepository(context).hasPermission()) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.BATTERY -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
        ) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.NOTIFICATIONS -> if (NotificationManagerCompat.from(context).areNotificationsEnabled()) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.OVERLAY -> if (LauncherController(context).hasOverlayPermission()) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.MEDIA -> {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) PermissionStatus.GRANTED else PermissionStatus.DENIED
        }
        PermissionId.VPN -> if (VpnService.prepare(context) == null) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.DEVICE_ADMIN -> {
            val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val receiver = android.content.ComponentName(context, com.tbtechs.focusflow.enforcement.receivers.FocusDayDeviceAdminReceiver::class.java)
            if (manager.isAdminActive(receiver)) PermissionStatus.GRANTED else PermissionStatus.DENIED
        }
        PermissionId.LAUNCHER -> {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = context.packageManager.resolveActivity(home, 0)
            if (resolved?.activityInfo?.packageName == context.packageName) PermissionStatus.GRANTED else PermissionStatus.DENIED
        }
    }
} catch (_: Exception) {
    PermissionStatus.UNKNOWN
}

suspend fun openPermissionSettings(context: Context, id: PermissionId) {
    when (id) {
        PermissionId.ACCESSIBILITY -> UsageStatsRepository(context).openAccessibilitySettings()
        PermissionId.USAGE -> UsageStatsRepository(context).openUsageAccessSettings()
        PermissionId.BATTERY -> UsageStatsRepository(context).openBatteryOptimizationSettings()
        PermissionId.DEVICE_ADMIN -> UsageStatsRepository(context).openDeviceAdminSettings()
        PermissionId.OVERLAY -> LauncherController(context).requestOverlayPermission()
        PermissionId.LAUNCHER -> context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        PermissionId.NOTIFICATIONS -> context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        PermissionId.MEDIA, PermissionId.VPN -> Unit
    }
}
