package com.tbtechs.focusflow.data.repository

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.tbtechs.focusflow.enforcement.receivers.FocusDayDeviceAdminReceiver
import java.util.Calendar

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val foregroundMinutes: Int,
    val launchCount: Int,
    val lastUsedAt: Long,
)

data class UsageSummary(
    val totalMinutes: Int,
    val apps: List<AppUsageInfo>,
)

data class HourlyUsageSummary(
    val foregroundMillisecondsByHour: List<Long>,
    val totalForegroundMilliseconds: Long,
)

/**
 * UsageStatsRepository
 *
 * Converted from UsageStatsModule.
 * Queries device foreground usage, aggregates hourly and daily usage,
 * checks system permissions (Usage Access, Accessibility, Device Admin, Battery Optimization),
 * and navigates to relevant OS configuration screens.
 *
 * Named Risk Preserved:
 * Every usage-stats query is strictly guarded by the AppOpsManager.checkOpNoThrow check.
 * Without this guard, a revoked permission would silently return empty or inaccurate data.
 */
class UsageStatsRepository(private val context: Context) {

    /**
     * Returns whether the PACKAGE_USAGE_STATS (Usage Access) permission has been granted.
     *
     * Primary check: AppOpsManager.checkOpNoThrow — the standard API.
     * Secondary check: try a live query via UsageStatsManager; if results return,
     * the permission is actually granted even if AppOps reports MODE_DEFAULT (seen on Samsung One UI).
     */
    suspend fun hasPermission(): Boolean {
        return checkUsageAccessPermission()
    }

    private fun checkUsageAccessPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
            if (mode == AppOpsManager.MODE_ALLOWED) {
                return true
            }
            if (mode == AppOpsManager.MODE_DEFAULT) {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val now = System.currentTimeMillis()
                val stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    now - 24 * 60 * 60 * 1_000L,
                    now,
                )
                return !stats.isNullOrEmpty()
            }
            if (mode != AppOpsManager.MODE_IGNORED && mode != AppOpsManager.MODE_ERRORED) {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val now = System.currentTimeMillis()
                val stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    now - 24 * 60 * 60 * 1_000L,
                    now,
                )
                if (!stats.isNullOrEmpty()) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Throws SecurityException if the PACKAGE_USAGE_STATS permission is missing or revoked.
     */
    private fun ensureUsageAccessPermission() {
        if (!checkUsageAccessPermission()) {
            throw SecurityException("PACKAGE_USAGE_STATS permission not granted (AppOps check failed)")
        }
    }

    /**
     * Returns the package name of the current foreground app.
     * Queries the last 10 seconds of usage events and returns the latest
     * foreground transition.
     *
     * Guarded by AppOps check to ensure revoked permissions fail loudly.
     */
    suspend fun getForegroundApp(): String? {
        ensureUsageAccessPermission()

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 10_000L, now)
        val event = UsageEvents.Event()
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == foregroundType) {
                latest = event.packageName
            }
        }
        return latest
    }

    /**
     * Aggregates observed foreground usage for the requested time range.
     *
     * Guarded by AppOps check to ensure revoked permissions fail loudly.
     */
    suspend fun getUsageSummary(startMs: Long, endMs: Long): UsageSummary {
        ensureUsageAccessPermission()

        val start = startMs
        val end = endMs
        if (start >= end) {
            return UsageSummary(totalMinutes = 0, apps = emptyList())
        }

        val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager
        val ownPackage = context.packageName

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            UsageEvents.Event.ACTIVITY_RESUMED else UsageEvents.Event.MOVE_TO_FOREGROUND
        val backgroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            UsageEvents.Event.ACTIVITY_PAUSED  else UsageEvents.Event.MOVE_TO_BACKGROUND

        // Per-package accumulators
        val fgStartMs    = mutableMapOf<String, Long>()
        val foregroundMs = mutableMapOf<String, Long>()
        val launchCounts = mutableMapOf<String, Int>()
        val lastUsedAt   = mutableMapOf<String, Long>()
        val lastCountedLaunchAt = mutableMapOf<String, Long>()
        var lastFgPkg: String? = null
        val launchDebounceMs = 30_000L

        val queryStart = start - 6L * 60 * 60 * 1000L
        val events = usageManager.queryEvents(queryStart, end)
        val event  = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            if (pkg == ownPackage) continue

            lastUsedAt[pkg] = maxOf(lastUsedAt[pkg] ?: 0L, event.timeStamp)

            when (event.eventType) {
                foregroundType -> {
                    if (!fgStartMs.containsKey(pkg)) {
                        fgStartMs[pkg] = event.timeStamp
                        if (pkg != lastFgPkg &&
                            event.timeStamp >= start &&
                            (lastCountedLaunchAt[pkg] == null ||
                                event.timeStamp - lastCountedLaunchAt.getValue(pkg) >= launchDebounceMs)
                        ) {
                            launchCounts[pkg] = (launchCounts[pkg] ?: 0) + 1
                            lastCountedLaunchAt[pkg] = event.timeStamp
                        }
                        lastFgPkg = pkg
                    }
                }
                backgroundType -> {
                    val fgStart = fgStartMs.remove(pkg) ?: continue
                    val clippedStart = maxOf(fgStart, start)
                    val clippedEnd   = minOf(event.timeStamp, end)
                    if (clippedEnd > clippedStart) {
                        foregroundMs[pkg] = (foregroundMs[pkg] ?: 0L) + (clippedEnd - clippedStart)
                    }
                }
            }
        }

        val now = System.currentTimeMillis()
        for ((pkg, fgStart) in fgStartMs) {
            val clippedStart = maxOf(fgStart, start)
            val clippedEnd   = minOf(now, end)
            if (clippedEnd > clippedStart) {
                foregroundMs[pkg] = (foregroundMs[pkg] ?: 0L) + (clippedEnd - clippedStart)
            }
        }

        val displayForegroundMs = foregroundMs.filter { (pkg, ms) ->
            ms >= 500L && try {
                packageManager.getLaunchIntentForPackage(pkg) != null
            } catch (_: Exception) {
                false
            }
        }

        val apps = displayForegroundMs.entries
            .map { (pkg, ms) ->
                val appName = try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                } catch (_: Exception) { pkg }

                AppUsageInfo(
                    packageName = pkg,
                    appName = appName,
                    foregroundMinutes = (ms / 60_000L).toInt(),
                    launchCount = launchCounts[pkg] ?: 0,
                    lastUsedAt = lastUsedAt[pkg] ?: 0L,
                )
            }
            .sortedByDescending { it.foregroundMinutes }

        val totalMs      = displayForegroundMs.values.sumOf { it }
        val totalMinutes = (totalMs / 60_000L).toInt()

        return UsageSummary(totalMinutes = totalMinutes, apps = apps)
    }

    /** Overload for Double millisecond timestamp compatibility */
    suspend fun getUsageSummary(startMs: Double, endMs: Double): UsageSummary =
        getUsageSummary(startMs.toLong(), endMs.toLong())

    /**
     * Aggregates raw foreground time into local clock-hour buckets.
     *
     * Guarded by AppOps check to ensure revoked permissions fail loudly.
     */
    suspend fun getHourlyUsageSummary(startMs: Long, endMs: Long): HourlyUsageSummary {
        ensureUsageAccessPermission()

        val start = startMs
        val end = endMs
        val hourlyMilliseconds = LongArray(24)
        if (start < end) {
            val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val ownPackage = context.packageName
            val stats = usageManager.queryUsageStats(
                UsageStatsManager.INTERVAL_HOURLY,
                start,
                end,
            ) ?: emptyList()

            for (stat in stats) {
                if (stat.packageName == ownPackage) continue
                val foregroundMs = stat.totalTimeInForeground
                if (foregroundMs <= 0L) continue

                val calendar = Calendar.getInstance().apply {
                    timeInMillis = stat.firstTimeStamp
                }
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                if (hour in 0..23) {
                    hourlyMilliseconds[hour] += foregroundMs
                }
            }
        }

        return HourlyUsageSummary(
            foregroundMillisecondsByHour = hourlyMilliseconds.toList(),
            totalForegroundMilliseconds = hourlyMilliseconds.sum(),
        )
    }

    /** Overload for Double millisecond timestamp compatibility */
    suspend fun getHourlyUsageSummary(startMs: Double, endMs: Double): HourlyUsageSummary =
        getHourlyUsageSummary(startMs.toLong(), endMs.toLong())

    /**
     * Opens the Usage Access settings list.
     */
    suspend fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Opens the Accessibility Settings screen with an OEM fallback chain.
     */
    suspend fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val launched = try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }

        if (launched) return

        try {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        } catch (_: Exception) {}
    }

    /**
     * Opens the Device Admin activation dialog for FocusDayDeviceAdminReceiver.
     */
    suspend fun openDeviceAdminSettings() {
        val component = ComponentName(context, FocusDayDeviceAdminReceiver::class.java)
        val adminIntent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Prevents aggressive battery killers from stopping FocusFlow's blocking service."
            )
        }

        val launched = if (context is Activity && !context.isFinishing) {
            try { context.startActivityForResult(adminIntent, 1001); true } catch (_: Exception) { false }
        } else {
            try {
                adminIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(adminIntent)
                true
            } catch (_: Exception) { false }
        }

        if (launched) return

        val fallbackIntents = listOf(
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.DeviceAdminSettings")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                setClassName(
                    "com.samsung.android.settings",
                    "com.samsung.android.settings.deviceadmin.DeviceAdminSettings",
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
        )
        for (fb in fallbackIntents) {
            try {
                context.startActivity(fb)
                return
            } catch (_: Exception) { /* try next */ }
        }

        throw IllegalStateException("Could not open device admin settings")
    }

    /**
     * Returns whether the AppBlockerAccessibilityService is enabled.
     */
    suspend fun hasAccessibilityPermission(): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val found = enabled.any { info ->
                info.resolveInfo.serviceInfo.packageName == context.packageName
            }
            if (found) return true

            val raw = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            raw.split(":").any { component ->
                component.contains(context.packageName, ignoreCase = true) &&
                component.contains("AppBlockerAccessibilityService", ignoreCase = true)
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Opens the battery optimization exemption dialog directly for this app.
     */
    suspend fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val launch = { intent: Intent ->
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                true
            } catch (_: Exception) { false }
        }

        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        if (launch(direct)) return

        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (launch(list)) return

        val settings = Intent(Settings.ACTION_SETTINGS)
        launch(settings)
    }

    /**
     * Returns whether the app is exempted from battery optimization.
     */
    suspend fun isIgnoringBatteryOptimizations(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns whether the app has an active Device Admin component registered.
     */
    suspend fun isDeviceAdminActive(): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val component = ComponentName(context, FocusDayDeviceAdminReceiver::class.java)
            dpm.isAdminActive(component)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns whether sensitive permissions are locked behind "Restricted Settings" (Android 13+).
     */
    suspend fun isRestrictedSettingsBlocked(): Boolean {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return false
            }

            val installer = try {
                val pm = context.packageManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pm.getInstallSourceInfo(context.packageName).installingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstallerPackageName(context.packageName)
                }
            } catch (_: Exception) { null }

            val trustedInstallers = setOf(
                "com.android.vending",
                "com.google.android.feedback",
                "com.sec.android.app.samsungapps",
                "com.heytap.market",
                "com.oppo.market",
                "com.xiaomi.market",
                "com.bbk.appstore",
                "com.huawei.appmarket",
            )
            if (installer != null && trustedInstallers.contains(installer)) {
                return false
            }

            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = try {
                appOps.unsafeCheckOpNoThrow(
                    "android:access_restricted_settings",
                    Process.myUid(),
                    context.packageName,
                )
            } catch (_: Exception) {
                return false
            }

            return mode != AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Opens the system App Info screen for FocusFlow to access "Allow restricted settings".
     */
    suspend fun openAppInfoSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (_: Exception) {}
        }
    }

    /**
     * Returns the package name of the app that installed FocusFlow on this device.
     */
    suspend fun getInstallerPackage(): String? {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
        } catch (_: Exception) {
            null
        }
    }
}
