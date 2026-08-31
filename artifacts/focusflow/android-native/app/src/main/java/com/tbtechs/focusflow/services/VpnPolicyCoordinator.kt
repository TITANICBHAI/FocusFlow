package com.tbtechs.focusflow.services

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Computes, persists, and dispatches the effective per-app VPN policy.
 *
 * Source inputs remain separate from the service-facing compatibility snapshot:
 * explicit VPN selections are stored independently, while focus mirroring is
 * derived from the native focus state. The desired-policy record is durable
 * evidence of intent; the VPN service status remains the evidence of application.
 */
object VpnPolicyCoordinator {
    private const val PREFS_NAME = AppBlockerAccessibilityService.PREFS_NAME
    private const val PREF_EXPLICIT_PACKAGES = "vpn_selected_packages"
    private const val PREF_ALWAYS_ON_PACKAGES = "vpn_always_on_packages"
    private const val PREF_SESSION_PACKAGES = "vpn_session_packages"
    private const val PREF_CANONICAL_PACKAGES = "net_block_packages"
    private const val PREF_FOCUS_MIRROR = "vpn_focus_mirror_enabled"
    private const val PREF_ALLOWED_READY = "focus_allowed_ready"
    private const val PREF_FOCUS_ACTIVE = "focus_active"
    private const val PREF_TASK_END = "task_end_ms"
    private const val PREF_DESIRED_POLICY = "vpn_desired_policy"
    private const val PREF_DESIRED_GENERATION = "vpn_desired_generation"
    private const val PREF_APPLIED_GENERATION = "vpn_applied_generation"
    private const val PREF_RECOVERY_PENDING = "vpn_recovery_pending"
    private const val POLICY_SCHEMA_VERSION = 1
    private const val DISPATCH_DEBOUNCE_MS = 150L

    private val dispatchHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var pendingDispatch: Runnable? = null

    private data class DispatchRequest(
        val generation: Long,
        val packagesJson: String,
        val mode: String,
        val shouldRun: Boolean,
    )

    /**
     * Persists the latest desired policy immediately, then coalesces its native
     * start/stop dispatch. This keeps rapid focus/settings writes from tearing
     * down and rebuilding the VPN repeatedly.
     */
    fun reconcile(context: Context) {
        val appContext = context.applicationContext
        val request = synchronized(lock) {
            buildAndPersistPolicy(appContext)
        }

        synchronized(lock) {
            pendingDispatch?.let(dispatchHandler::removeCallbacks)
            val runnable = Runnable {
                val isLatest = synchronized(lock) {
                    pendingDispatch = null
                    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.getLong(PREF_DESIRED_GENERATION, 0L) == request.generation
                }
                if (isLatest) dispatch(appContext, request)
            }
            pendingDispatch = runnable
            dispatchHandler.postDelayed(runnable, DISPATCH_DEBOUNCE_MS)
        }
    }

    private fun buildAndPersistPolicy(context: Context): DispatchRequest {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasSeparatedSources = prefs.contains(PREF_ALWAYS_ON_PACKAGES) ||
            prefs.contains(PREF_SESSION_PACKAGES)
        val explicitJson = prefs.getString(PREF_EXPLICIT_PACKAGES, null)
        val explicit = if (hasSeparatedSources) {
            parsePackages(prefs.getString(PREF_ALWAYS_ON_PACKAGES, "[]") ?: "[]") +
                parsePackages(prefs.getString(PREF_SESSION_PACKAGES, "[]") ?: "[]")
        } else {
            parsePackages(
                explicitJson ?: prefs.getString(PREF_CANONICAL_PACKAGES, "[]") ?: "[]",
            )
        }.toSet()
        val installed = installedPackages(context)
        val installedLaunchable = installedLaunchablePackages(context)
        val focusActive = isFocusActive(prefs)
        val allowedReady = prefs.getBoolean(
            PREF_ALLOWED_READY,
            prefs.contains("allowed_packages"),
        )
        val allowed = parsePackages(prefs.getString("allowed_packages", "[]") ?: "[]")
        val globalMode = prefs.getBoolean("net_block_global", false)
        val focusMirrorEnabled = prefs.getBoolean(PREF_FOCUS_MIRROR, false)
        val result = VpnPolicyCalculator.calculate(
            explicitPackages = explicit,
            installedLaunchablePackages = installedLaunchable,
            allowedPackages = allowed,
            focusMirrorEnabled = focusMirrorEnabled,
            focusActive = focusActive,
            allowedPackagesReady = allowedReady,
            globalMode = globalMode,
            focusFlowPackage = context.packageName,
            installedPackages = installed,
        )

        val canonicalJson = JSONArray(result.packages.toList().sorted()).toString()
        val generation = prefs.getLong(PREF_DESIRED_GENERATION, 0L) + 1L
        val shouldRun = prefs.getBoolean("net_block_enabled", false) &&
            prefs.getBoolean("net_block_vpn", true) &&
            (globalMode || result.packages.isNotEmpty())
        val reasonsJson = JSONObject()
        result.reasons.toSortedMap().forEach { (pkg, reasons) ->
            reasonsJson.put(pkg, JSONArray(reasons.toList().sorted()))
        }
        val desiredPolicy = JSONObject()
            .put("version", POLICY_SCHEMA_VERSION)
            .put("generation", generation)
            .put("enabled", prefs.getBoolean("net_block_enabled", false))
            .put("vpnEnabled", prefs.getBoolean("net_block_vpn", true))
            .put("mode", result.mode)
            .put("targetPackages", JSONArray(result.packages.toList().sorted()))
            .put("explicitPackages", JSONArray(result.packages
                .filter { result.reasons[it]?.contains("explicit_vpn") == true }
                .toList()
                .sorted()))
            .put("focusMirrorEnabled", focusMirrorEnabled)
            .put("reasons", reasonsJson)
            .put("failedPackages", JSONArray(result.unavailablePackages.toList().sorted()))
            .put("updatedAt", System.currentTimeMillis())

        prefs.edit()
            .putString(PREF_EXPLICIT_PACKAGES, JSONArray(explicit.toList().sorted()).toString())
            .putString(PREF_CANONICAL_PACKAGES, canonicalJson)
            .putString("net_block_mode", result.mode)
            .putString(PREF_DESIRED_POLICY, desiredPolicy.toString())
            .putLong(PREF_DESIRED_GENERATION, generation)
            .putString("vpn_failed_packages", JSONArray(result.unavailablePackages.toList().sorted()).toString())
            .putBoolean(PREF_RECOVERY_PENDING, shouldRun)
            .apply()

        return DispatchRequest(generation, canonicalJson, result.mode, shouldRun)
    }

    private fun dispatch(context: Context, request: DispatchRequest) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getLong(PREF_DESIRED_GENERATION, 0L) != request.generation) return

        if (!request.shouldRun) {
            if (NetworkBlockerVpnService.isRunning ||
                prefs.getString("vpn_status", null) == NetworkBlockerVpnService.STATUS_STARTING
            ) {
                dispatchStop(context, request.generation)
            }
            prefs.edit().putBoolean(PREF_RECOVERY_PENDING, false).apply()
            return
        }

        if (VpnService.prepare(context) != null) {
            prefs.edit()
                .putBoolean("vpn_permission_lost", true)
                .putString("vpn_status", NetworkBlockerVpnService.STATUS_PERMISSION_MISSING)
                .putString("vpn_error", "VPN permission must be granted before FocusFlow can apply network blocking")
                .putBoolean(PREF_RECOVERY_PENDING, true)
                .apply()
            VpnRecoveryNotifier.postPermissionRequired(context)
            return
        }

        if (!NetworkBlockerVpnService.isRunning && hasAnotherVpn(context)) {
            prefs.edit()
                .putString("vpn_status", NetworkBlockerVpnService.STATUS_ANOTHER_VPN)
                .putString("vpn_error", "Another VPN is active; FocusFlow will not replace it automatically")
                .putBoolean(PREF_RECOVERY_PENDING, true)
                .apply()
            VpnWatchdogReceiver.cancel(context)
            return
        }

        try {
            val intent = Intent(context, NetworkBlockerVpnService::class.java).apply {
                action = NetworkBlockerVpnService.ACTION_START
                putExtra(NetworkBlockerVpnService.EXTRA_PACKAGES, request.packagesJson)
                putExtra(NetworkBlockerVpnService.EXTRA_MODE, request.mode)
                putExtra(NetworkBlockerVpnService.EXTRA_POLICY_GENERATION, request.generation)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            prefs.edit()
                .putString("vpn_status", NetworkBlockerVpnService.STATUS_STARTUP_FAILED)
                .putString("vpn_error", e.message ?: "Could not start FocusFlow VPN")
                .putBoolean(PREF_RECOVERY_PENDING, true)
                .apply()
        }
    }

    private fun dispatchStop(context: Context, generation: Long) {
        try {
            context.startService(Intent(context, NetworkBlockerVpnService::class.java).apply {
                action = NetworkBlockerVpnService.ACTION_STOP
                putExtra(NetworkBlockerVpnService.EXTRA_POLICY_GENERATION, generation)
            })
        } catch (_: Exception) {
            // The next reconciliation or watchdog run can retry teardown.
        }
    }

    private fun parsePackages(json: String): Set<String> {
        return try {
            val array = JSONArray(json)
            (0 until array.length())
                .map { array.optString(it).trim() }
                .filter { it.isNotBlank() }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun installedPackages(context: Context): Set<String>? {
        return try {
            context.packageManager.getInstalledApplications(0)
                .asSequence()
                .map { it.packageName.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        } catch (_: Exception) {
            null
        }
    }

    private fun installedLaunchablePackages(context: Context): Set<String> {
        return try {
            val packageManager = context.packageManager
            packageManager.getInstalledApplications(0)
                .asSequence()
                .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
                .map { it.packageName.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun isFocusActive(prefs: android.content.SharedPreferences): Boolean {
        if (!prefs.getBoolean(PREF_FOCUS_ACTIVE, false)) return false
        val endMs = prefs.getLong(PREF_TASK_END, 0L)
        return endMs <= 0L || System.currentTimeMillis() < endMs
    }

    private fun hasAnotherVpn(context: Context): Boolean {
        if (NetworkBlockerVpnService.isRunning || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return connectivity.allNetworks.any { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }
}