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
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.data.repository.LauncherController
import com.tbtechs.focusflow.data.repository.UsageStatsRepository

enum class PermissionId {
    ACCESSIBILITY, USAGE, BATTERY, NOTIFICATIONS, DEVICE_ADMIN, OVERLAY, MEDIA, VPN, EXACT_ALARMS,
    FULL_SCREEN_INTENT, LAUNCHER,
}

enum class PermissionStatus { GRANTED, DENIED, UNKNOWN }

data class PermissionDefinition(
    val id: PermissionId,
    val title: String,
    val description: String,
    val whyNeeded: String,
    val brokenWithout: List<String>,
    val optional: Boolean,
    val actionLabel: String,
)

val permissionDefinitions = listOf(
    // Core protection paths are first. Overlay is required for the block screen
    // to appear above the app the user is trying to open.
    PermissionDefinition(PermissionId.ACCESSIBILITY, "Accessibility Service", "Reads the foreground app name only. Data stays on your device.", "Redirects blocked apps before they open.", listOf("App blocking will not work", "Blocked apps can open during focus", "Blocks can be bypassed"), false, "Open Accessibility Settings"),
    PermissionDefinition(PermissionId.USAGE, "Usage Access", "Detects which app is in the foreground.", "Needed to detect blocked apps.", listOf("Foreground app detection fails", "Blocking can silently fail", "Stats may be inaccurate"), false, "Open Usage Access Settings"),
    PermissionDefinition(PermissionId.OVERLAY, "Appear on Top", "Shows the block screen over other apps.", "Prevents blocked apps from flashing first.", listOf("The block screen may open inside FocusFlow", "A blocked app may flash"), false, "Allow Display Over Other Apps"),

    // Helpful but optional protections. Keep Notifications immediately before
    // Device Admin so the everyday service requirement is easy to find.
    PermissionDefinition(PermissionId.BATTERY, "Battery Optimization", "Helps FocusFlow keep running in the background.", "Some phones stop background services.", listOf("Blocking can stop when the screen turns off", "Focus sessions may stop"), true, "Disable Battery Optimization"),
    PermissionDefinition(PermissionId.EXACT_ALARMS, "Exact Alarms", "Allows task-end alerts to fire on time.", "Without it, task-end alerts are deferred until access returns.", listOf("Task-end alerts are deferred"), true, "Allow Alarms & Reminders"),
    PermissionDefinition(PermissionId.NOTIFICATIONS, "Notifications", "Shows reminders and the focus notification.", "Keeps reminders and the foreground service notification visible.", listOf("No task reminders", "The focus notification may be hidden", "You may not see service status"), true, "Open Notification Settings"),
    PermissionDefinition(PermissionId.FULL_SCREEN_INTENT, "Full-screen Alarms", "Lets Android show the task-end screen over the lock screen.", "Android 14+ may restrict full-screen alarm screens.", listOf("The task-end notification may appear without opening the alarm screen"), true, "Allow Full-screen Alarms"),
    PermissionDefinition(PermissionId.DEVICE_ADMIN, "Device Admin", "Adds resistance to force-stop controls.", "Some phones expose force-stop paths.", listOf("Stopping FocusFlow may be easier"), true, "Activate Device Admin"),
    PermissionDefinition(PermissionId.LAUNCHER, "Home Launcher", "Intercepts blocked launches before they open.", "Stops blocked apps from flashing.", listOf("Blocked apps may flash first", "Launcher filtering is unavailable"), true, "Set as Home Launcher"),
)

suspend fun checkPermission(context: Context, id: PermissionId): PermissionStatus = try {
    when (id) {
        PermissionId.ACCESSIBILITY -> if (UsageStatsRepository(context).hasAccessibilityPermission()) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.USAGE -> if (UsageStatsRepository(context).hasPermission()) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.BATTERY -> if (
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        ) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.NOTIFICATIONS -> if (NotificationManagerCompat.from(context).areNotificationsEnabled()) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.OVERLAY -> if (LauncherController(context).hasOverlayPermission()) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.MEDIA -> {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
            if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) PermissionStatus.GRANTED else PermissionStatus.DENIED
        }
        PermissionId.VPN -> if (VpnService.prepare(context) == null) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.EXACT_ALARMS -> if (AppModule.alarmRepository.canScheduleExactAlarms()) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.FULL_SCREEN_INTENT -> if (AppModule.alarmRepository.canUseFullScreenIntent()) PermissionStatus.GRANTED else PermissionStatus.DENIED
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
        PermissionId.EXACT_ALARMS -> AppModule.alarmRepository.requestExactAlarmPermission()
        PermissionId.FULL_SCREEN_INTENT -> AppModule.alarmRepository.requestFullScreenIntentPermission()
        PermissionId.MEDIA, PermissionId.VPN -> Unit
    }
}
