package com.tbtechs.focusflow.data.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import com.tbtechs.focusflow.enforcement.NetworkBlockerVpnService
import com.tbtechs.focusflow.enforcement.VpnPolicyCoordinator
import com.tbtechs.focusflow.enforcement.receivers.VpnWatchdogReceiver
import org.json.JSONArray
import org.json.JSONObject

data class NetworkBlockSettings(
    val enabled: Boolean = false,
    val vpn: Boolean = true,
    val wifi: Boolean = true,
    val mobile: Boolean = false,
    val global: Boolean = false,
    val restore: Boolean = true,
    val packages: List<String> = emptyList(),
    val standalonePackages: List<String> = emptyList(),
    val focusMirrorEnabled: Boolean = false,
)

data class NetworkBlockStatus(
    val state: String,
    val running: Boolean,
    val error: String?,
    val failedPackages: List<String>,
    val desiredPolicy: String?,
    val policyGeneration: Long,
    val appliedPolicyGeneration: Long,
)

/**
 * VpnRepository
 *
 * Converted from NetworkBlockModule.
 * Controls network-blocking settings, VPN lifecycle, WiFi/mobile data toggles,
 * and self-healing watchdogs.
 *
 * Named Risk Preserved:
 * Directly invokes VpnPolicyCoordinator for all policy state changes, syncs, and recoveries.
 * The policy-generation counter, atomic lock serialization, launcher package cache, and
 * debounce logic in VpnPolicyCoordinator remain fully authoritative.
 */
class VpnRepository(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "focusday_prefs"
        private const val PREF_DEFENSE_PIN_HASH = "defense_pin_hash"
        private const val PREF_SESSION_PIN_HASH = "session_pin_hash"
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─── VPN permission ───────────────────────────────────────────────────────

    /**
     * Returns true if the VPN permission has already been granted by the user.
     * VpnService.prepare() returns null when the permission is already held.
     */
    suspend fun isVpnPermissionGranted(): Boolean {
        return try {
            val intent = VpnService.prepare(context)
            intent == null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Shows the system "FocusFlow wants to set up a VPN" consent dialog.
     * Must be called from an Activity context or with an Activity reference.
     * Returns true if the dialog intent was started, or true if already granted.
     */
    suspend fun requestVpnPermission(activity: Activity? = null): Boolean {
        val targetActivity = activity ?: (context as? Activity)
        val vpnIntent = VpnService.prepare(context) ?: return true // already granted

        if (targetActivity != null && !targetActivity.isFinishing) {
            targetActivity.startActivityForResult(vpnIntent, 2001)
            return true
        } else {
            throw IllegalStateException("No foreground activity to show VPN consent dialog")
        }
    }

    // ─── Settings ─────────────────────────────────────────────────────────────

    /**
     * Returns all network-block settings as a structured data object.
     */
    suspend fun getNetworkBlockSettings(): NetworkBlockSettings {
        val rawExplicit = prefs.getString("net_block_explicit_packages", null)
            ?: prefs.getString("net_block_packages", "[]")
            ?: "[]"
        val rawStandalone = prefs.getString("net_block_standalone_vpn_packages", "[]") ?: "[]"

        return NetworkBlockSettings(
            enabled = prefs.getBoolean("net_block_enabled", false),
            vpn = prefs.getBoolean("net_block_vpn", true),
            wifi = prefs.getBoolean("net_block_wifi", true),
            mobile = prefs.getBoolean("net_block_mobile", false),
            global = prefs.getBoolean("net_block_global", false),
            restore = prefs.getBoolean("net_block_restore", true),
            packages = parsePackageList(rawExplicit),
            standalonePackages = parsePackageList(rawStandalone),
            focusMirrorEnabled = prefs.getBoolean("net_block_focus_mirror", false),
        )
    }

    /**
     * Returns all network-block settings as a JSON object string.
     */
    suspend fun getNetworkBlockSettingsJson(): String {
        val obj = JSONObject().apply {
            put("enabled",  prefs.getBoolean("net_block_enabled", false))
            put("vpn",      prefs.getBoolean("net_block_vpn",     true))
            put("wifi",     prefs.getBoolean("net_block_wifi",    true))
            put("mobile",   prefs.getBoolean("net_block_mobile",  false))
            put("global",   prefs.getBoolean("net_block_global",  false))
            put("restore",  prefs.getBoolean("net_block_restore", true))
            put("packages", prefs.getString("net_block_packages", "[]") ?: "[]")
        }
        return obj.toString()
    }

    /**
     * Persists network-block settings from a JSON object string.
     * Only keys present in [settingsJson] are updated; missing keys are left unchanged.
     */
    suspend fun setNetworkBlockSettings(settingsJson: String) {
        val obj = JSONObject(settingsJson)
        val currentEnabled = prefs.getBoolean("net_block_enabled", false)
        val currentVpn = prefs.getBoolean("net_block_vpn", true)
        val requestedEnabled = if (obj.has("enabled")) obj.getBoolean("enabled") else currentEnabled
        val requestedVpn = if (obj.has("vpn")) obj.getBoolean("vpn") else currentVpn

        if ((currentEnabled && !requestedEnabled) || (currentVpn && !requestedVpn)) {
            if (isBlockingSessionActive()) {
                throw IllegalStateException("Network blocking cannot be disabled while Focus or Standalone Block is active")
            }
            val storedDefenseHash = prefs.getString(PREF_DEFENSE_PIN_HASH, null)
            val suppliedDefenseHash = obj.optString("defensePinHash", null)
            if (!storedDefenseHash.isNullOrBlank() &&
                (suppliedDefenseHash.isNullOrBlank() ||
                    !storedDefenseHash.equals(suppliedDefenseHash, ignoreCase = true))
            ) {
                throw SecurityException("A Defense Password is required to disable network blocking")
            }
        }

        val editor = prefs.edit()
        if (obj.has("enabled")) {
            val enabled = obj.getBoolean("enabled")
            editor.putBoolean("net_block_enabled", enabled)
            if (!obj.has("vpn")) editor.putBoolean("net_block_vpn", enabled)
        }
        if (obj.has("vpn"))      editor.putBoolean("net_block_vpn",     obj.getBoolean("vpn"))
        if (obj.has("wifi"))     editor.putBoolean("net_block_wifi",    obj.getBoolean("wifi"))
        if (obj.has("mobile"))   editor.putBoolean("net_block_mobile",  obj.getBoolean("mobile"))
        if (obj.has("global"))   editor.putBoolean("net_block_global",  obj.getBoolean("global"))
        if (obj.has("restore"))  editor.putBoolean("net_block_restore", obj.getBoolean("restore"))
        if (obj.has("packages")) {
            editor.putString("net_block_explicit_packages", obj.getString("packages"))
        }
        if (obj.has("standalonePackages")) {
            editor.putString("net_block_standalone_vpn_packages", obj.getString("standalonePackages"))
        }
        if (obj.has("focusMirrorEnabled")) {
            editor.putBoolean("net_block_focus_mirror", obj.getBoolean("focusMirrorEnabled"))
        }
        editor.apply()

        // Calls directly into VpnPolicyCoordinator to recalculate policy generation and dispatch
        VpnPolicyCoordinator.requestSync(context)
    }

    /**
     * Typed overload for setting network-block settings.
     */
    suspend fun setNetworkBlockSettings(settings: NetworkBlockSettings, defensePinHash: String? = null) {
        val json = JSONObject().apply {
            put("enabled", settings.enabled)
            put("vpn", settings.vpn)
            put("wifi", settings.wifi)
            put("mobile", settings.mobile)
            put("global", settings.global)
            put("restore", settings.restore)
            put("packages", JSONArray(settings.packages).toString())
            put("standalonePackages", JSONArray(settings.standalonePackages).toString())
            put("focusMirrorEnabled", settings.focusMirrorEnabled)
            if (defensePinHash != null) {
                put("defensePinHash", defensePinHash)
            }
        }
        setNetworkBlockSettings(json.toString())
    }

    /**
     * Returns the last native VPN health state.
     */
    suspend fun getNetworkBlockStatus(): NetworkBlockStatus {
        val state = prefs.getString("vpn_status", NetworkBlockerVpnService.STATUS_STOPPED)
            ?: NetworkBlockerVpnService.STATUS_STOPPED
        val error = prefs.getString("vpn_error", null)
        val failed = prefs.getString("vpn_failed_packages", "[]") ?: "[]"
        val desiredPolicy = prefs.getString("net_block_desired_policy", null)
        val policyGeneration = prefs.getLong("net_block_policy_generation", 0L)
        val appliedPolicyGeneration = prefs.getLong("net_block_applied_generation", 0L)

        return NetworkBlockStatus(
            state = state,
            running = NetworkBlockerVpnService.isRunning,
            error = error,
            failedPackages = parsePackageList(failed),
            desiredPolicy = desiredPolicy,
            policyGeneration = policyGeneration,
            appliedPolicyGeneration = appliedPolicyGeneration,
        )
    }

    /**
     * Returns status JSON object string.
     */
    suspend fun getNetworkBlockStatusJson(): String {
        val status = getNetworkBlockStatus()
        return JSONObject().apply {
            put("state", status.state)
            put("running", status.running)
            put("error", status.error ?: JSONObject.NULL)
            put("failedPackages", JSONArray(status.failedPackages).toString())
            put("desiredPolicy", status.desiredPolicy ?: JSONObject.NULL)
            put("policyGeneration", status.policyGeneration)
            put("appliedPolicyGeneration", status.appliedPolicyGeneration)
        }.toString()
    }

    // ─── Active control ───────────────────────────────────────────────────────

    /**
     * Activates network blocking for [packagesJson] (JSON array of package names).
     * Combines all enabled mechanisms: VPN tunnel + direct WiFi disable.
     */
    suspend fun startNetworkBlock(packagesJson: String): String {
        if (!prefs.getBoolean("net_block_enabled", false)) {
            return NetworkBlockerVpnService.STATUS_DISABLED
        }

        val useVpn    = prefs.getBoolean("net_block_vpn",    true)
        val useWifi   = prefs.getBoolean("net_block_wifi",   true)
        val useMobile = prefs.getBoolean("net_block_mobile", false)
        val global    = prefs.getBoolean("net_block_global", false)

        if (useVpn) {
            if (!prefs.contains("net_block_explicit_packages")) {
                prefs.edit().putString("net_block_explicit_packages", packagesJson).apply()
            }
            val effectivePackagesJson = if (global) packagesJson
                else NetworkBlockerVpnService.effectivePackagesJson(context, prefs)
            if (!global && effectivePackagesJson == "[]") {
                return NetworkBlockerVpnService.STATUS_DISABLED
            }
        }

        if (useVpn && !NetworkBlockerVpnService.isRunning) {
            val vpnPermission = VpnService.prepare(context)
            if (vpnPermission != null) {
                val conflict = isAnotherVpnActiveInternal()
                prefs.edit()
                    .putBoolean("vpn_permission_lost", !conflict)
                    .putString(
                        "vpn_status",
                        if (conflict) NetworkBlockerVpnService.STATUS_ANOTHER_VPN
                        else NetworkBlockerVpnService.STATUS_PERMISSION_MISSING,
                    )
                    .apply()
                if (useWifi) tryDisableWifiInternal()
                if (useMobile) tryDisableMobileDataInternal()
                throw SecurityException(
                    if (conflict) "Another VPN is currently active"
                    else "VPN permission must be granted before network blocking can start"
                )
            }
            // Direct call to VpnPolicyCoordinator for counter-aware serialized dispatch
            VpnPolicyCoordinator.requestSync(context)
        }

        if (useWifi) {
            tryDisableWifiInternal()
        }

        if (useMobile) {
            tryDisableMobileDataInternal()
        }

        return if (useVpn) NetworkBlockerVpnService.STATUS_STARTING else NetworkBlockerVpnService.STATUS_DISABLED
    }

    /**
     * Typed overload for starting network block.
     */
    suspend fun startNetworkBlock(packages: List<String>): String =
        startNetworkBlock(JSONArray(packages).toString())

    /**
     * Deactivates all network-blocking mechanisms and restores connectivity
     * if the net_block_restore setting is true.
     */
    suspend fun stopNetworkBlock(pinHash: String? = null) {
        if (isBlockingSessionActive()) {
            throw IllegalStateException("Network blocking cannot be stopped while Focus or Standalone Block is active")
        }

        val storedSessionHash = prefs.getString(PREF_SESSION_PIN_HASH, null)
        val storedDefenseHash = prefs.getString(PREF_DEFENSE_PIN_HASH, null)
        val suppliedHash = pinHash?.lowercase()
        val matchesSessionPin = !storedSessionHash.isNullOrBlank() &&
            !suppliedHash.isNullOrBlank() &&
            storedSessionHash.equals(suppliedHash, ignoreCase = true)
        val matchesDefensePin = !storedDefenseHash.isNullOrBlank() &&
            !suppliedHash.isNullOrBlank() &&
            storedDefenseHash.equals(suppliedHash, ignoreCase = true)
        if ((!storedSessionHash.isNullOrBlank() || !storedDefenseHash.isNullOrBlank()) &&
            !matchesSessionPin && !matchesDefensePin
        ) {
            throw SecurityException("A session PIN or Defense Password is required to stop network block")
        }

        if (NetworkBlockerVpnService.hasPersistentVpnConfiguration(prefs)) {
            VpnPolicyCoordinator.requestSync(context)
            return
        }

        // Direct call to VpnPolicyCoordinator
        VpnPolicyCoordinator.requestSync(context)

        val restore = prefs.getBoolean("net_block_restore", true)
        if (restore) {
            if (prefs.getBoolean("net_block_wifi", true)) {
                tryRestoreWifiInternal()
            }
            if (prefs.getBoolean("net_block_mobile", false)) {
                tryRestoreMobileDataInternal()
            }
        }
    }

    /**
     * Returns true if the VPN tunnel is currently active.
     */
    suspend fun isNetworkBlockActive(): Boolean {
        return NetworkBlockerVpnService.isRunning
    }

    /**
     * Returns true if a VPN from another app is currently active on the device.
     */
    suspend fun isAnotherVpnActive(): Boolean {
        return try {
            isAnotherVpnActiveInternal()
        } catch (e: Exception) {
            false
        }
    }

    private fun isAnotherVpnActiveInternal(): Boolean {
        return NetworkBlockerVpnService.isAnotherVpnActive(context)
    }

    private fun isBlockingSessionActive(): Boolean {
        val now = System.currentTimeMillis()
        val focusActive = prefs.getBoolean("focus_active", false).let { active ->
            if (!active) false
            else {
                val endMs = prefs.getLong("task_end_ms", 0L)
                endMs <= 0L || now < endMs
            }
        }
        val standaloneActive = prefs.getBoolean("standalone_block_active", false).let { active ->
            if (!active) false
            else {
                val untilMs = prefs.getLong("standalone_block_until_ms", 0L)
                untilMs <= 0L || now < untilMs
            }
        }
        return focusActive || standaloneActive
    }

    /**
     * Persists the "net_block_self_heal" flag and updates watchdogs.
     */
    suspend fun setVpnSelfHealEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("net_block_self_heal", enabled).apply()
        if (!enabled) {
            VpnWatchdogReceiver.cancel(context)
        } else {
            VpnPolicyCoordinator.requestRecoverySync(context)
        }
    }

    /**
     * Direct WiFi disable.
     */
    suspend fun tryDisableWifi() {
        tryDisableWifiInternal()
    }

    /**
     * Direct WiFi restore.
     */
    suspend fun tryRestoreWifi() {
        tryRestoreWifiInternal()
    }

    private fun tryDisableWifiInternal() {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            wm.isWifiEnabled = false
        } else {
            wm.disconnect()
        }
    }

    private fun tryRestoreWifiInternal() {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            wm.isWifiEnabled = true
        }
    }

    private fun tryDisableMobileDataInternal() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            val method = cm?.javaClass?.getDeclaredMethod("setMobileDataEnabled", Boolean::class.java)
            method?.isAccessible = true
            method?.invoke(cm, false)
        } catch (_: Exception) {}
    }

    private fun tryRestoreMobileDataInternal() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            val method = cm?.javaClass?.getDeclaredMethod("setMobileDataEnabled", Boolean::class.java)
            method?.isAccessible = true
            method?.invoke(cm, true)
        } catch (_: Exception) {}
    }

    private fun parsePackageList(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
