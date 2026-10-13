package com.tbtechs.focusflow.enforcement

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.tbtechs.focusflow.data.restore.RestoreGate
import com.tbtechs.focusflow.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Native owner for VPN target calculation, desired-policy persistence, and
 * service dispatch. Sources are explicit VPN selections, active standalone
 * packages, opt-in focus mirroring, and schedule packages inside their
 * configured local-time windows. Invalid/uninstalled targets are persisted
 * separately from VPN service registration failures.
 *
 * The desired policy is persisted before asynchronous service commands are
 * dispatched, so recovery can recalculate from durable settings without a UI.
 */
object VpnPolicyCoordinator {
    private const val PREFS_NAME = "focusday_prefs"
    private const val PREF_EXPLICIT_PKGS = "net_block_explicit_packages"
    private const val PREF_STANDALONE_VPN_PKGS = "net_block_standalone_vpn_packages"
    private const val PREF_FOCUS_MIRROR = "net_block_focus_mirror"
    private const val PREF_DESIRED_POLICY = "net_block_desired_policy"
    private const val PREF_POLICY_GENERATION = "net_block_policy_generation"
    private const val PREF_INVALID_PKGS = "net_block_invalid_packages"
    private const val PREF_EXPLICIT_MIGRATED = "net_block_explicit_migrated"
    private const val POLICY_VERSION = 1
    private const val DISPATCH_DEBOUNCE_MS = 150L
    private const val LAUNCHER_CACHE_TTL_MS = 30_000L

    private val syncLock = Any()
    private val explicitMigrationLock = Any()
    private val explicitMigrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val launcherCacheExecutor = Executors.newSingleThreadExecutor()
    private var pendingDispatch: Runnable? = null
    private var migrationInFlight = false
    private var pendingMigrationContext: Context? = null
    private var pendingMigrationRecovery = false
    @Volatile private var cachedLauncherPackages: List<String> = emptyList()
    @Volatile private var cacheRefreshedAtMs: Long = 0L

    /**
     * These packages must remain reachable for emergency calls, messaging, and
     * core Android operation. FocusFlow itself is also excluded at dispatch.
     */
    val ALWAYS_EXCLUDED = listOf(
        "android",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.app.telephonyui",
        "com.android.server.telecom",
        "com.android.mms",
        "com.android.messaging",
        "com.google.android.apps.messaging",
    )

    private data class EffectivePolicy(
        val targets: List<String>,
        val explicit: List<String>,
        val standalone: List<String>,
        val schedule: List<String>,
        val focus: List<String>,
        val invalid: List<String>,
    )

    private data class PersistResult(
        val generation: Long,
        val serviceStateChanged: Boolean,
    )

    /**
     * Returns whether a durable VPN policy still requires protection without
     * depending on a live focus or standalone overlay session.
     */
    fun hasPersistentVpnConfiguration(prefs: SharedPreferences): Boolean {
        if (!prefs.getBoolean("net_block_enabled", false) ||
            !prefs.getBoolean("net_block_vpn", true)
        ) return false

        if (prefs.getBoolean("net_block_global", false)) return true
        if (prefs.getBoolean(PREF_FOCUS_MIRROR, false) &&
            isFocusBlockActive(prefs)
        ) return true
        if (isStandaloneBlockActive(prefs) &&
            parsePackageJson(
                prefs.getString(PREF_STANDALONE_VPN_PKGS, "[]") ?: "[]",
            ).isNotEmpty()
        ) return true
        if (VpnPolicyBoundaryScheduler.currentScheduleTargets(prefs).isNotEmpty()) return true

        return ExplicitVpnPolicy.hasPersistentExplicitTargets(explicitCandidates(prefs))
    }

    fun isFocusMirrorActive(
        prefs: SharedPreferences,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean =
        prefs.getBoolean(PREF_FOCUS_MIRROR, false) && isFocusBlockActive(prefs, nowMs)

    fun effectivePackages(context: Context, prefs: SharedPreferences): List<String> {
        return effectivePolicy(context, prefs).targets
    }

    fun effectivePackagesJson(context: Context, prefs: SharedPreferences): String {
        return JSONArray(effectivePolicy(context, prefs).targets).toString()
    }

    internal suspend fun ensureExplicitPackagesMigrated(
        prefs: SharedPreferences,
        restoreGate: RestoreGate,
    ) {
        if (prefs.getBoolean(PREF_EXPLICIT_MIGRATED, false)) return
        withContext(Dispatchers.IO) {
            restoreGate.write("VpnPolicyCoordinator.migrateExplicitVpnPackages") {
                synchronized(explicitMigrationLock) {
                    migrateExplicitPackagesNow(prefs)
                }
            }
        }
    }

    fun currentPolicyGeneration(prefs: SharedPreferences): Long =
        prefs.getLong(PREF_POLICY_GENERATION, 0L)

    /**
     * Recalculates, persists, and dispatches the latest desired VPN policy.
     *
     * The lock serializes native writers. The generation on every command
     * prevents a delayed stop/start from applying an older policy.
     */
    fun requestSync(context: Context) {
        requestSyncInternal(context, forceRecovery = false)
    }

    /**
     * Re-dispatches the current durable policy from a recovery path.
     *
     * A service can die while vpn_status is still "starting". Ordinary source
     * synchronization avoids duplicating that in-flight start, but a watchdog,
     * service recreation, or health check has evidence that the old attempt is
     * no longer alive and must be allowed to retry it.
     */
    fun requestRecoverySync(context: Context) {
        requestSyncInternal(context, forceRecovery = true)
    }

    private fun requestSyncInternal(context: Context, forceRecovery: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_EXPLICIT_MIGRATED, false)) {
            deferSyncUntilExplicitMigration(context.applicationContext, forceRecovery)
            return
        }
        refreshLauncherPackageCacheIfStale(context.applicationContext)
        val policyTimeMs = synchronized(syncLock) {
            val nowMs = System.currentTimeMillis()
            val enabled = prefs.getBoolean("net_block_enabled", false)
            val vpnEnabled = prefs.getBoolean("net_block_vpn", true)
            val global = prefs.getBoolean("net_block_global", false)
            val policy = effectivePolicy(context, prefs, nowMs)
            val packagesJson = JSONArray(policy.targets).toString()

            val persisted = persistDesiredPolicy(
                prefs = prefs,
                enabled = enabled,
                vpnEnabled = vpnEnabled,
                global = global,
                policy = policy,
            )

            // This compatibility snapshot is intentionally kept in sync with
            // the desired policy, but recovery recalculates instead of trusting
            // it as authoritative input.
            prefs.edit()
                .putString("net_block_packages", packagesJson)
                .putString(PREF_INVALID_PKGS, JSONArray(policy.invalid).toString())
                .putString(
                    "net_block_mode",
                    if (global) NetworkBlockerVpnService.MODE_GLOBAL
                    else NetworkBlockerVpnService.MODE_PER_APP,
                )
                .apply()

            val expectsRunning = enabled && vpnEnabled &&
                (global || policy.targets.isNotEmpty())
            val status = prefs.getString(
                "vpn_status",
                NetworkBlockerVpnService.STATUS_STOPPED,
            )
            // A fresh process can observe STOPPED or a persisted failure even
            // though durable policy still requires protection. Do not wait for
            // the status to say RUNNING before recovering; that is exactly the
            // state that is lost after process death or a failed establish().
            // STARTING is the one deliberate exception so a slow, in-flight
            // start is not duplicated by a concurrent source update.
            val recoveryNeeded = expectsRunning &&
                !NetworkBlockerVpnService.isRunning &&
                (forceRecovery || status != NetworkBlockerVpnService.STATUS_STARTING)

            // Persist every source/reason/failure update, but do not issue a
            // service command when the effective service state is unchanged
            // and the current tunnel is healthy. A stale persisted "running"
            // state after process death is the explicit recovery exception.
            if (persisted.serviceStateChanged || recoveryNeeded) {
                if (forceRecovery) {
                    // Recovery callers include BroadcastReceivers and the
                    // watchdog. Do not leave their only restart command behind
                    // a delayed callback after onReceive() returns.
                    dispatchLatest(context.applicationContext)
                } else {
                    scheduleDispatch(context.applicationContext)
                }
            }
            nowMs
        }
        // Use the policy snapshot's clock value. If a boundary passes while
        // synchronization is running, its alarm is then set in the past and
        // fires immediately instead of skipping that transition until tomorrow.
        VpnPolicyBoundaryScheduler.scheduleNextBoundary(
            context.applicationContext,
            policyTimeMs,
        )
    }

    private fun deferSyncUntilExplicitMigration(context: Context, forceRecovery: Boolean) {
        val shouldLaunchMigration = synchronized(explicitMigrationLock) {
            pendingMigrationContext = context.applicationContext
            pendingMigrationRecovery = pendingMigrationRecovery || forceRecovery
            if (migrationInFlight) {
                false
            } else {
                migrationInFlight = true
                true
            }
        }
        if (!shouldLaunchMigration) return

        explicitMigrationScope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ensureExplicitPackagesMigrated(prefs, AppModule.restoreGate)
                val pending = synchronized(explicitMigrationLock) {
                    val nextContext = pendingMigrationContext ?: context
                    val nextRecovery = pendingMigrationRecovery
                    pendingMigrationContext = null
                    pendingMigrationRecovery = false
                    migrationInFlight = false
                    nextContext to nextRecovery
                }
                requestSyncInternal(pending.first, pending.second)
            } catch (error: Throwable) {
                synchronized(explicitMigrationLock) {
                    pendingMigrationContext = null
                    pendingMigrationRecovery = false
                    migrationInFlight = false
                }
                throw error
            }
        }
    }

    /**
     * Coalesces rapid source changes such as focus start ordering, allow-list
     * writes, and package-install broadcasts. Every call persists immediately;
     * only the native service command is delayed and replaced by the latest one.
     */
    private fun scheduleDispatch(context: Context) {
        pendingDispatch?.let(mainHandler::removeCallbacks)
        val next = Runnable {
            synchronized(syncLock) {
                pendingDispatch = null
                dispatchLatest(context)
            }
        }
        pendingDispatch = next
        mainHandler.postDelayed(next, DISPATCH_DEBOUNCE_MS)
    }

    private fun dispatchLatest(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("net_block_enabled", false)
        val vpnEnabled = prefs.getBoolean("net_block_vpn", true)
        val global = prefs.getBoolean("net_block_global", false)
        val packagesJson = effectivePackagesJson(context, prefs)
        val generation = currentPolicyGeneration(prefs)

        if (!enabled || !vpnEnabled || (!global && parsePackageJson(packagesJson).isEmpty())) {
            dispatch(
                context,
                action = NetworkBlockerVpnService.ACTION_STOP,
                generation = generation,
            )
            return
        }

        dispatch(
            context,
            action = NetworkBlockerVpnService.ACTION_START,
            packagesJson = packagesJson,
            mode = if (global) NetworkBlockerVpnService.MODE_GLOBAL
            else NetworkBlockerVpnService.MODE_PER_APP,
            generation = generation,
        )
    }

    private fun dispatch(
        context: Context,
        action: String,
        packagesJson: String = "[]",
        mode: String = NetworkBlockerVpnService.MODE_PER_APP,
        generation: Long,
    ) {
        val intent = Intent(context, NetworkBlockerVpnService::class.java).apply {
            this.action = action
            putExtra(NetworkBlockerVpnService.EXTRA_POLICY_GENERATION, generation)
            if (action == NetworkBlockerVpnService.ACTION_START) {
                putExtra(NetworkBlockerVpnService.EXTRA_PACKAGES, packagesJson)
                putExtra(NetworkBlockerVpnService.EXTRA_MODE, mode)
            }
        }
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            // A background start can be rejected before the service gets a
            // chance to record its own failure (for example, by Android's
            // foreground-service restrictions). Preserve an honest status for
            // the next foreground UI read.
            if (action == NetworkBlockerVpnService.ACTION_START) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(
                        "vpn_status",
                        NetworkBlockerVpnService.STATUS_STARTUP_FAILED,
                    )
                    .putString(
                        "vpn_error",
                        e.message ?: "Android rejected the VPN service start",
                    )
                    .apply()
            }
        }
    }

    private fun effectivePolicy(
        context: Context,
        prefs: SharedPreferences,
        nowMs: Long = System.currentTimeMillis(),
    ): EffectivePolicy {
        val explicitCandidates = explicitCandidates(prefs)
        val standaloneCandidates = if (isStandaloneBlockActive(prefs, nowMs)) {
            parsePackageJson(
                prefs.getString(PREF_STANDALONE_VPN_PKGS, "[]") ?: "[]",
            )
        } else {
            emptyList()
        }
        val scheduleCandidates = VpnPolicyBoundaryScheduler.currentScheduleTargets(prefs, nowMs)

        val focusTargets = if (
            prefs.getBoolean(PREF_FOCUS_MIRROR, false) &&
            isFocusBlockActive(prefs)
        ) {
            val launcherPackages = getCachedLauncherPackages(context)
            val allowed = parsePackageJson(
                prefs.getString("allowed_packages", "[]") ?: "[]",
            ).toSet()
            launcherPackages
                .filterNot { isExcludedPackage(it, context.packageName) }
                .filter { it !in allowed }
                .distinct()
        } else {
            emptyList()
        }

        val sourcePackages = (
            ExplicitVpnPolicy.effectiveTargets(
                explicitCandidates = explicitCandidates,
                focusTargets = focusTargets,
            ) + standaloneCandidates + scheduleCandidates
        )
            .filterNot { isExcludedPackage(it, context.packageName) }
            .distinct()
            .sorted()
        val invalid = sourcePackages.filterNot { isInstalled(context, it) }

        return EffectivePolicy(
            targets = sourcePackages.filterNot { it in invalid },
            explicit = explicitCandidates.distinct().sorted(),
            standalone = standaloneCandidates.distinct().sorted(),
            schedule = scheduleCandidates.distinct().sorted(),
            focus = focusTargets.distinct().sorted(),
            invalid = invalid,
        )
    }

    internal fun explicitCandidates(prefs: SharedPreferences): List<String> {
        val explicitRaw = prefs.getString(PREF_EXPLICIT_PKGS, null)
        val migrationComplete = prefs.getBoolean(PREF_EXPLICIT_MIGRATED, false)
        val derivedSnapshot = if (explicitRaw == null && !migrationComplete) {
            parsePackageJson(prefs.getString("net_block_packages", "[]") ?: "[]")
        } else {
            emptyList()
        }
        return ExplicitVpnPolicy.selectExplicitCandidates(
            explicitKeyExists = prefs.contains(PREF_EXPLICIT_PKGS),
            explicitPackages = explicitRaw?.let(::parsePackageJson),
            migrationComplete = migrationComplete,
            derivedSnapshot = derivedSnapshot,
            policyGeneration = currentPolicyGeneration(prefs),
        )
    }

    private fun migrateExplicitPackagesNow(prefs: SharedPreferences) {
        if (prefs.getBoolean(PREF_EXPLICIT_MIGRATED, false)) return

        val explicitKeyExists = prefs.contains(PREF_EXPLICIT_PKGS)
        val derivedSnapshot = if (explicitKeyExists) {
            emptyList()
        } else {
            parsePackageJson(prefs.getString("net_block_packages", "[]") ?: "[]")
        }
        val migration = ExplicitVpnPolicy.migrationPlan(
            alreadyMigrated = false,
            explicitKeyExists = explicitKeyExists,
            derivedSnapshot = derivedSnapshot,
            policyGeneration = currentPolicyGeneration(prefs),
        ) ?: return

        val editor = prefs.edit()
        migration.explicitPackagesToWrite?.let { packages ->
            editor.putString(PREF_EXPLICIT_PKGS, JSONArray(packages).toString())
        }
        editor.putBoolean(PREF_EXPLICIT_MIGRATED, migration.markMigrated)
        check(editor.commit()) {
            "Could not persist the one-time explicit VPN package migration"
        }
    }

    private fun getCachedLauncherPackages(context: Context): List<String> {
        refreshLauncherPackageCacheIfStale(context.applicationContext)
        return cachedLauncherPackages
    }

    private fun refreshLauncherPackageCacheIfStale(context: Context) {
        val now = System.currentTimeMillis()
        if (now - cacheRefreshedAtMs < LAUNCHER_CACHE_TTL_MS) return

        launcherCacheExecutor.execute {
            val refreshStartedAt = System.currentTimeMillis()
            if (refreshStartedAt - cacheRefreshedAtMs < LAUNCHER_CACHE_TTL_MS) return@execute

            val launcherPackages = try {
                val launcherIntent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                context.packageManager.queryIntentActivities(launcherIntent, 0)
                    .map { it.activityInfo.packageName }
                    .distinct()
            } catch (_: Exception) {
                emptyList()
            }

            cachedLauncherPackages = launcherPackages
            cacheRefreshedAtMs = refreshStartedAt
            requestSync(context)
        }
    }

    private fun persistDesiredPolicy(
        prefs: SharedPreferences,
        enabled: Boolean,
        vpnEnabled: Boolean,
        global: Boolean,
        policy: EffectivePolicy,
    ): PersistResult {
        val reasons = JSONObject()

        fun addReasons(packages: List<String>, reason: String) {
            packages.forEach { pkg ->
                val existing = reasons.optJSONArray(pkg) ?: JSONArray()
                var alreadyPresent = false
                for (index in 0 until existing.length()) {
                    if (existing.optString(index) == reason) {
                        alreadyPresent = true
                        break
                    }
                }
                if (!alreadyPresent) existing.put(reason)
                reasons.put(pkg, existing)
            }
        }

        addReasons(policy.explicit, "explicit_vpn")
        addReasons(policy.standalone, "standalone_vpn")
        addReasons(policy.schedule, "schedule_vpn")
        addReasons(policy.focus, "focus_blocked")
        addReasons(policy.invalid, "invalid_package")

        val previousRecord = prefs.getString(PREF_DESIRED_POLICY, null)
        val serviceStateChanged = !sameServiceState(
            previousRecord = previousRecord,
            enabled = enabled,
            vpnEnabled = vpnEnabled,
            global = global,
            targets = policy.targets,
        )
        val generation = currentPolicyGeneration(prefs) + 1L
        val record = JSONObject().apply {
            put("version", POLICY_VERSION)
            put("generation", generation)
            put("enabled", enabled)
            put("vpnEnabled", vpnEnabled)
            put("mode", if (global) NetworkBlockerVpnService.MODE_GLOBAL
                else NetworkBlockerVpnService.MODE_PER_APP)
            put("targetPackages", JSONArray(policy.targets))
            put("explicitPackages", JSONArray(policy.explicit))
            put("invalidPackages", JSONArray(policy.invalid))
            put("focusMirrorEnabled", prefs.getBoolean(PREF_FOCUS_MIRROR, false))
            put("reasons", reasons)
            put("updatedAt", System.currentTimeMillis())
        }
        prefs.edit()
            .putLong(PREF_POLICY_GENERATION, generation)
            .putString(PREF_DESIRED_POLICY, record.toString())
            .apply()
        return PersistResult(
            generation = generation,
            serviceStateChanged = serviceStateChanged,
        )
    }

    private fun sameServiceState(
        previousRecord: String?,
        enabled: Boolean,
        vpnEnabled: Boolean,
        global: Boolean,
        targets: List<String>,
    ): Boolean {
        if (previousRecord.isNullOrBlank()) return false
        return try {
            val previous = JSONObject(previousRecord)
            val previousTargets = parsePackageJson(
                previous.optJSONArray("targetPackages")?.toString() ?: "[]",
            ).distinct().sorted()
            previous.optBoolean("enabled", false) == enabled &&
                previous.optBoolean("vpnEnabled", false) == vpnEnabled &&
                previous.optString("mode", NetworkBlockerVpnService.MODE_PER_APP) ==
                    (if (global) NetworkBlockerVpnService.MODE_GLOBAL
                    else NetworkBlockerVpnService.MODE_PER_APP) &&
                previousTargets == targets.distinct().sorted()
        } catch (_: Exception) {
            false
        }
    }

    private fun isInstalled(context: Context, packageName: String): Boolean =
        try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }

    private fun isExcludedPackage(packageName: String, ownPackageName: String): Boolean =
        packageName.equals(ownPackageName, ignoreCase = true) ||
            ALWAYS_EXCLUDED.any { packageName.equals(it, ignoreCase = true) }

    private fun isStandaloneBlockActive(
        prefs: SharedPreferences,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!prefs.getBoolean("standalone_block_active", false)) return false
        val untilMs = prefs.getLong("standalone_block_until_ms", 0L)
        return untilMs <= 0L || untilMs > nowMs
    }

    private fun isFocusBlockActive(
        prefs: SharedPreferences,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!prefs.getBoolean("focus_active", false)) return false
        val endMs = prefs.getLong("task_end_ms", 0L)
        return endMs <= 0L || endMs > nowMs
    }

    private fun parsePackageJson(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length())
                .map { arr.optString(it).trim() }
                .filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }
}