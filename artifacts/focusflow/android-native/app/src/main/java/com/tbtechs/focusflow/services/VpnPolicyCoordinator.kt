package com.tbtechs.focusflow.services

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import org.json.JSONArray

/**
 * Computes and dispatches the effective per-app VPN policy.
 *
 * Explicit VPN selections are persisted independently in vpn_selected_packages.
 * The derived Focus set is written only to the service-facing canonical key,
 * allowing focus teardown or the opt-out toggle to remove only derived targets.
 */
object VpnPolicyCoordinator {
    private const val PREFS_NAME = AppBlockerAccessibilityService.PREFS_NAME
    private const val PREF_EXPLICIT_PACKAGES = "vpn_selected_packages"
    private const val PREF_CANONICAL_PACKAGES = "net_block_packages"
    private const val PREF_FOCUS_MIRROR = "vpn_focus_mirror_enabled"
    private const val PREF_ALLOWED_READY = "focus_allowed_ready"
    private const val PREF_FOCUS_ACTIVE = "focus_active"
    private const val PREF_TASK_END = "task_end_ms"

    fun reconcile(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val explicitJson = prefs.getString(PREF_EXPLICIT_PACKAGES, null)
        val explicit = parsePackages(
            explicitJson ?: prefs.getString(PREF_CANONICAL_PACKAGES, "[]") ?: "[]",
        )
        val installedLaunchable = installedLaunchablePackages(context)
        val focusActive = isFocusActive(prefs)
        val allowedReady = prefs.getBoolean(
            PREF_ALLOWED_READY,
            prefs.contains("allowed_packages"),
        )
        val allowed = parsePackages(prefs.getString("allowed_packages", "[]") ?: "[]")
        val globalMode = prefs.getBoolean("net_block_global", false)
        val result = VpnPolicyCalculator.calculate(
            explicitPackages = explicit,
            installedLaunchablePackages = installedLaunchable,
            allowedPackages = allowed,
            focusMirrorEnabled = prefs.getBoolean(PREF_FOCUS_MIRROR, false),
            focusActive = focusActive,
            allowedPackagesReady = allowedReady,
            globalMode = globalMode,
            focusFlowPackage = context.packageName,
        )

        val canonicalJson = JSONArray(result.packages.toList().sorted()).toString()
        prefs.edit()
            .putString(PREF_EXPLICIT_PACKAGES, JSONArray(explicit.toList().sorted()).toString())
            .putString(PREF_CANONICAL_PACKAGES, canonicalJson)
            .putString("net_block_mode", result.mode)
            .apply()

        val shouldRun = prefs.getBoolean("net_block_enabled", false) &&
            prefs.getBoolean("net_block_vpn", true) &&
            (globalMode || result.packages.isNotEmpty())

        if (!shouldRun) {
            if (NetworkBlockerVpnService.isRunning ||
                prefs.getString("vpn_status", null) == NetworkBlockerVpnService.STATUS_STARTING
            ) {
                dispatchStop(context)
            }
            return
        }

        if (VpnService.prepare(context) != null) {
            prefs.edit()
                .putBoolean("vpn_permission_lost", true)
                .putString("vpn_status", NetworkBlockerVpnService.STATUS_PERMISSION_MISSING)
                .putString("vpn_error", "VPN permission must be granted before FocusFlow can apply network blocking")
                .apply()
            VpnRecoveryNotifier.postPermissionRequired(context)
            return
        }

        if (!NetworkBlockerVpnService.isRunning && hasAnotherVpn(context)) {
            prefs.edit()
                .putString("vpn_status", NetworkBlockerVpnService.STATUS_ANOTHER_VPN)
                .putString("vpn_error", "Another VPN is active; FocusFlow will not replace it automatically")
                .apply()
            return
        }

        try {
            val intent = Intent(context, NetworkBlockerVpnService::class.java).apply {
                action = NetworkBlockerVpnService.ACTION_START
                putExtra(NetworkBlockerVpnService.EXTRA_PACKAGES, canonicalJson)
                putExtra(NetworkBlockerVpnService.EXTRA_MODE, result.mode)
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
                .apply()
        }
    }

    private fun dispatchStop(context: Context) {
        try {
            context.startService(Intent(context, NetworkBlockerVpnService::class.java).apply {
                action = NetworkBlockerVpnService.ACTION_STOP
            })
        } catch (_: Exception) {
            // The next reconciliation or watchdog run can retry teardown.
        }
    }

    private fun parsePackages(json: String): Set<String> {
        return try {
            val array = JSONArray(json)
            (0 until array.length())
                .map { array.optString(it) }
                .filter { it.isNotBlank() }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun installedLaunchablePackages(context: Context): Set<String> {
        return try {
            val packageManager = context.packageManager
            packageManager.getInstalledApplications(0)
                .asSequence()
                .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
                .map { it.packageName }
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