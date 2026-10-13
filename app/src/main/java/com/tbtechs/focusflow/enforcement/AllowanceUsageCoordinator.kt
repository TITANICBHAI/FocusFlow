package com.tbtechs.focusflow.enforcement

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tbtechs.focusflow.analytics.UsageEventRead
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Reads allowance usage from the shared foreground-event pipeline. Its timer
 * exists only while an allowance app is in the foreground.
 */
internal class AllowanceUsageCoordinator(
    context: Context,
    private val prefs: SharedPreferences,
    private val ledger: AllowanceLedger,
    private val scope: CoroutineScope,
    private val onReconciled: (String) -> Unit,
) {
    private val stateStore = AllowanceUsageStateStore(prefs, ledger)
    private val reader = AllowanceUsageReader(
        context = context,
        ledger = ledger,
        zoneId = ZoneId.systemDefault(),
        onReconciled = onReconciled,
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private val zoneId = ZoneId.systemDefault()
    private val accumulator = AllowanceUsageAccumulator(ledger, zoneId)
    private var currentTarget: AllowanceUsageTarget? = null
    private var pendingBridgePackage: String? = null
    private var pendingBridgeAtMs = 0L
    private var lastSuccessfulReadAtMs = 0L
    private var unlockedAtMs = System.currentTimeMillis()
    private var usageAccessAvailable = true
    private var freshness = AllowanceUsageFreshness.STALE
    private var readJob: Job? = null
    private var readAgain = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            val target = currentTarget ?: return
            val now = System.currentTimeMillis()
            freshness = freshness(now)
            checkpointIfStale(target, now)
            refresh("foreground_tick")
            val delay = if (freshness == AllowanceUsageFreshness.FRESH) {
                AllowanceUsageFreshnessReducer.FRESH_FOR_MS / 2
            } else {
                CHECKPOINT_INTERVAL_MS
            }
            mainHandler.postDelayed(this, delay)
        }
    }

    fun onServiceStarted(savedForegroundPackage: String?) {
        val now = System.currentTimeMillis()
        stateStore.clearRetiredPreferenceKeys()
        stateStore.recoverPersistedCheckpoint(now)
        lastSuccessfulReadAtMs = stateStore.configuredTargets().maxOfOrNull { target ->
            ledger.usage(target.packageName).confirmedAtMs
        } ?: 0L
        val target = savedForegroundPackage?.let(stateStore::targetForPackage)
        if (target != null) {
            currentTarget = target
            accumulator.start(now)
            if (target.mode == AllowanceLedger.MODE_INTERVAL) {
                val start = ledger.ensureIntervalWindowStarted(
                    target.packageName,
                    AllowanceUsageTimeAccounting.localDateKey(now, zoneId),
                    now,
                    target.windowMs,
                )
                currentTarget = target.copy(windowStartMs = start)
            }
            stateStore.persistMarker(target.packageName, now)
            scheduleTick()
        }
        refresh("service_start")
    }

    fun onForegroundPackage(packageName: String, atMs: Long = System.currentTimeMillis()) {
        if (packageName in AllowanceUsagePipeline.dialogBridgePackages) {
            val active = currentTarget
            if (active != null) {
                finishSegment(active, atMs)
                pendingBridgePackage = active.packageName
                pendingBridgeAtMs = atMs
                currentTarget = null
                accumulator.clear()
                stateStore.clearMarker(active.packageName)
                mainHandler.removeCallbacks(tickRunnable)
            }
            return
        }

        val target = stateStore.targetForPackage(packageName)
        val active = currentTarget
        if (active?.packageName.equals(target?.packageName, ignoreCase = true) &&
            active != null && target != null
        ) {
            return
        }
        if (active != null) {
            finishSegment(active, atMs)
            stateStore.clearMarker(active.packageName)
            currentTarget = null
            accumulator.clear()
            mainHandler.removeCallbacks(tickRunnable)
        }
        if (target == null) {
            pendingBridgePackage = null
            pendingBridgeAtMs = 0L
            refresh("foreground_left_allowance")
            return
        }

        val bridgeReturn = pendingBridgePackage.equals(target.packageName, ignoreCase = true) &&
            atMs >= pendingBridgeAtMs &&
            atMs - pendingBridgeAtMs <= AllowanceUsagePipeline.DIALOG_BRIDGE_MAX_MS
        pendingBridgePackage = null
        pendingBridgeAtMs = 0L
        val currentDay = AllowanceUsageTimeAccounting.localDateKey(atMs, zoneId)
        var nextTarget = target
        if (target.mode == AllowanceLedger.MODE_INTERVAL) {
            val start = ledger.ensureIntervalWindowStarted(
                target.packageName,
                currentDay,
                atMs,
                target.windowMs,
            )
            nextTarget = target.copy(windowStartMs = start)
        }
        if (target.mode == AllowanceLedger.MODE_COUNT && !bridgeReturn) {
            ledger.addEstimatedOpen(target.packageName, currentDay)
        }
        currentTarget = nextTarget
        accumulator.start(atMs, ledger.usage(target.packageName).confirmedAtMs)
        stateStore.persistMarker(target.packageName, atMs)
        scheduleTick()
        refresh("allowance_app_open")
    }

    fun onScreenOff(atMs: Long = System.currentTimeMillis()) {
        currentTarget?.let { finishSegment(it, atMs) }
        currentTarget?.let { stateStore.clearMarker(it.packageName) }
        currentTarget = null
        accumulator.clear()
        pendingBridgePackage = null
        pendingBridgeAtMs = 0L
        mainHandler.removeCallbacks(tickRunnable)
    }

    fun onUserPresent() {
        unlockedAtMs = System.currentTimeMillis()
    }

    fun onConfigurationChanged(atMs: Long = System.currentTimeMillis()) {
        currentTarget?.let {
            finishSegment(it, atMs)
            stateStore.clearMarker(it.packageName)
        }
        currentTarget = null
        accumulator.clear()
        pendingBridgePackage = null
        pendingBridgeAtMs = 0L
        mainHandler.removeCallbacks(tickRunnable)
        refresh("allowance_config_changed")
    }

    fun onServiceStopping(atMs: Long = System.currentTimeMillis()) {
        currentTarget?.let {
            finishSegment(it, atMs)
            stateStore.persistMarker(it.packageName, atMs)
        }
        currentTarget = null
        accumulator.clear()
        mainHandler.removeCallbacks(tickRunnable)
        readJob?.cancel()
    }

    fun setActiveSessionEnd(packageName: String, endAtMs: Long) {
        stateStore.setActiveSessionEnd(packageName, endAtMs)
    }

    fun freshness(nowMs: Long = System.currentTimeMillis()): AllowanceUsageFreshness =
        AllowanceUsageFreshnessReducer.reduce(
            hasUsageAccess = usageAccessAvailable,
            lastSuccessfulReadAtMs = lastSuccessfulReadAtMs,
            unlockedAtMs = unlockedAtMs,
            nowMs = nowMs,
        )

    private fun refresh(reason: String) {
        if (readJob?.isActive == true) {
            readAgain = true
            return
        }
        val now = System.currentTimeMillis()
        val targets = stateStore.configuredTargets().map { target ->
            if (target.mode == AllowanceLedger.MODE_INTERVAL) {
                target.copy(windowStartMs = ledger.usage(target.packageName).windowStartMs)
            } else {
                target
            }
        }
        if (targets.isEmpty()) return
        val todayStart = AllowanceUsageTimeAccounting.localMidnight(now, zoneId)
        val earliestPeriod = targets.mapNotNull { target ->
            when (target.mode) {
                AllowanceLedger.MODE_INTERVAL -> target.windowStartMs.takeIf { it > 0L }
                else -> todayStart
            }
        }.minOrNull() ?: todayStart
        val queryStart = (earliestPeriod - LOOKBACK_MS).coerceAtLeast(0L)
        val activePackage = currentTarget?.packageName
        val activeSegmentStartedAtMs = accumulator.segmentStartedAtMs()
        readJob = scope.launch {
            val outcome = reader.readAndReconcile(
                targets = targets,
                queryStartMs = queryStart,
                nowMs = now,
                activePackage = activePackage,
            )
            val read = outcome.read
            val completedAt = outcome.completedAtMs
            when (read) {
                is UsageEventRead.Unknown -> {
                    usageAccessAvailable =
                        read.reason != UsageEventRead.Unknown.Reason.PERMISSION_MISSING &&
                            read.reason != UsageEventRead.Unknown.Reason.ACCESS_REVOKED
                    val newState = AllowanceUsageFreshnessReducer.reduce(
                        hasUsageAccess = usageAccessAvailable,
                        lastSuccessfulReadAtMs = lastSuccessfulReadAtMs,
                        unlockedAtMs = unlockedAtMs,
                        nowMs = completedAt,
                    )
                    if (newState != freshness) {
                        currentTarget?.let {
                            stateStore.persistMarker(it.packageName, completedAt)
                        }
                    }
                    freshness = newState
                    currentTarget?.let { checkpointIfStale(it, completedAt) }
                    currentTarget?.let {
                        mainHandler.removeCallbacks(tickRunnable)
                        mainHandler.postDelayed(tickRunnable, CHECKPOINT_INTERVAL_MS)
                    }
                    Log.i(TAG, "allowance pipeline state=$freshness reason=$reason")
                }
                is UsageEventRead.Available -> {
                    usageAccessAvailable = true
                    currentTarget?.takeIf {
                        it.packageName.equals(activePackage, ignoreCase = true) &&
                            accumulator.segmentStartedAtMs() == activeSegmentStartedAtMs
                    }?.let { active ->
                        outcome.activeSessionStartedAtMs?.let(accumulator::updateSessionStart)
                        accumulator.successfulRead(completedAt)
                        stateStore.persistMarker(active.packageName, completedAt)
                    }
                    lastSuccessfulReadAtMs = completedAt
                    freshness = AllowanceUsageFreshness.FRESH
                    Log.d(TAG, "allowance pipeline read succeeded reason=$reason")
                }
            }
            readJob = null
            if (readAgain) {
                readAgain = false
                refresh("coalesced")
            }
        }
    }

    private fun checkpointIfStale(target: AllowanceUsageTarget, nowMs: Long) {
        val state = freshness(nowMs)
        if (state == AllowanceUsageFreshness.FRESH) return
        accumulator.checkpoint(target, nowMs)
        stateStore.persistMarker(target.packageName, nowMs)
    }

    private fun finishSegment(target: AllowanceUsageTarget, atMs: Long) {
        accumulator.finish(target, atMs)
    }

    private fun scheduleTick() {
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.postDelayed(tickRunnable, INITIAL_REFRESH_DELAY_MS)
    }

    companion object {
        const val CHECKPOINT_INTERVAL_MS = 15_000L
        const val MAX_RECOVERABLE_CHECKPOINT_GAP_MS = 2 * CHECKPOINT_INTERVAL_MS
        private const val INITIAL_REFRESH_DELAY_MS = 30_000L
        private const val LOOKBACK_MS = 6 * 60 * 60 * 1_000L
        private const val TAG = "AllowanceUsage"
    }
}
