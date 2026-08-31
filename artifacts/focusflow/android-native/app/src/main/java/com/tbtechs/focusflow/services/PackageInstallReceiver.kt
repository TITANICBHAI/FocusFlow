package com.tbtechs.focusflow.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import org.json.JSONArray

/**
 * PackageInstallReceiver
 *
 * Listens for package add/remove broadcasts so the effective policy is
 * recalculated whenever an installed target changes.
 *
 * Behaviour during an active session:
 *   1. Reads the current focus/standalone block state from SharedPreferences.
 *   2. If task-based focus is active, adds the new package to the BLOCKED set
 *      (i.e. removes it from allowed_packages if it was somehow there, or — for
 *      an allowlist-based session — the app was never in the allowed list so it
 *      will be blocked automatically by the AccessibilityService's "not in
 *      allowed_packages" logic without any extra work).
 *      Additionally, the new package is appended to a "runtime_install_flagged"
 *      list so the JS layer can surface a warning banner on next app open.
 *   3. If standalone-block is active, appends the new package to
 *      standalone_blocked_packages so it is immediately covered by the block.
 *   4. Starts a brief aversive deterrent (vibration) to alert the user that
 *      the install was noticed.
 *   5. If neither session mode is active, does nothing.
 *
 * Declared in AndroidManifest.xml with:
 *   <action android:name="android.intent.action.PACKAGE_ADDED" />
 *   <action android:name="android.intent.action.PACKAGE_REMOVED" />
 *   <action android:name="android.intent.action.PACKAGE_FULLY_REMOVED" />
 *   <data android:scheme="package" />
 */
class PackageInstallReceiver : BroadcastReceiver() {

    companion object {
        /**
         * SharedPrefs key: JSON array of package names installed during a session.
         * Reset by JS when the session ends so the warning banner clears.
         */
        const val PREF_RUNTIME_INSTALLS = "runtime_install_flagged"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                // PACKAGE_REMOVED also fires during replacement. The add
                // broadcast will handle the replacement after installation.
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                reconcileVpnIfConfigured(context)
                return
            }
            Intent.ACTION_PACKAGE_ADDED -> Unit
            else -> return
        }

        val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        if (isReplacing) return

        val newPkg = intent.data?.schemeSpecificPart ?: return
        if (newPkg.isBlank()) return

        val prefs = context.getSharedPreferences(
            AppBlockerAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE
        )

        val now = System.currentTimeMillis()

        val focusActive = prefs.getBoolean(AppBlockerAccessibilityService.PREF_FOCUS_ON, false).let { on ->
            if (on) {
                val endMs = prefs.getLong("task_end_ms", 0L)
                endMs == 0L || now < endMs
            } else false
        }

        val saActive = prefs.getBoolean(AppBlockerAccessibilityService.PREF_SA_ACTIVE, false).let { on ->
            if (on) {
                val untilMs = prefs.getLong(AppBlockerAccessibilityService.PREF_SA_UNTIL, 0L)
                untilMs == 0L || now < untilMs
            } else false
        }

        if (hasConfiguredVpnPolicy(prefs)) {
            // Revalidate explicit targets as well as Focus-derived targets. A
            // package that was unavailable during the previous reconciliation
            // can become installable after this broadcast.
            VpnPolicyCoordinator.reconcile(context)
        }

        if (!focusActive && !saActive) return

        // Keep an opted-in Focus → VPN mirror current when a new launchable
        // package appears during the session. This only updates VPN-derived
        // targets; overlay and standalone package state remain unchanged.
        val editor = prefs.edit()

        flagNewInstall(newPkg, prefs, editor)

        if (saActive) {
            appendToSaBlockedPackages(newPkg, prefs, editor)
        }

        editor.apply()

        AversiveActionsManager.onBlockedApp(context)
    }

    private fun reconcileVpnIfConfigured(context: Context) {
        val prefs = context.getSharedPreferences(
            AppBlockerAccessibilityService.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val explicit = prefs.getString("vpn_selected_packages", "[]") ?: "[]"
        val canonical = prefs.getString("net_block_packages", "[]") ?: "[]"
        val hasTargets = explicit != "[]" || canonical != "[]"
        val hasVpnPolicy = prefs.getBoolean("net_block_enabled", false) &&
            prefs.getBoolean("net_block_vpn", true) &&
            (prefs.getBoolean("net_block_global", false) ||
                hasTargets ||
                prefs.getBoolean("vpn_focus_mirror_enabled", false))
        if (hasVpnPolicy) VpnPolicyCoordinator.reconcile(context)
    }

    private fun hasConfiguredVpnPolicy(prefs: SharedPreferences): Boolean {
        val explicit = prefs.getString("vpn_selected_packages", "[]") ?: "[]"
        val canonical = prefs.getString("net_block_packages", "[]") ?: "[]"
        return prefs.getBoolean("net_block_enabled", false) &&
            prefs.getBoolean("net_block_vpn", true) &&
            (prefs.getBoolean("net_block_global", false) ||
                explicit != "[]" ||
                canonical != "[]" ||
                prefs.getBoolean("vpn_focus_mirror_enabled", false))
    }

    private fun flagNewInstall(
        pkg: String,
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor
    ) {
        val existing = prefs.getString(PREF_RUNTIME_INSTALLS, "[]") ?: "[]"
        val arr = try { JSONArray(existing) } catch (_: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            if (arr.getString(i) == pkg) return
        }
        arr.put(pkg)
        editor.putString(PREF_RUNTIME_INSTALLS, arr.toString())
    }

    private fun appendToSaBlockedPackages(
        pkg: String,
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor
    ) {
        val existing = prefs.getString(AppBlockerAccessibilityService.PREF_SA_PKGS, "[]") ?: "[]"
        val arr = try { JSONArray(existing) } catch (_: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            if (arr.getString(i) == pkg) return
        }
        arr.put(pkg)
        editor.putString(AppBlockerAccessibilityService.PREF_SA_PKGS, arr.toString())
    }
}
