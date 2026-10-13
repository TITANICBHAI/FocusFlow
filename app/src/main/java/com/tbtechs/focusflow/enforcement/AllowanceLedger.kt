package com.tbtechs.focusflow.enforcement

import org.json.JSONObject

/** Owns the persisted allowance state, process lock/cache, and enforcement math. */
class AllowanceLedger internal constructor(
    private val store: AllowanceLedgerStore,
) {
    private val lock = Any()
    private var cachedUsage: JSONObject? = null
    private var cachedJson: String? = null

    fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    fun snapshot(): AllowanceLedgerSnapshot = withLock {
        val root = usageObjectLocked()
        val usages = linkedMapOf<String, AllowanceUsageRecord>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val packageName = keys.next()
            root.optJSONObject(packageName)?.let { usages[packageName] = AllowanceLedgerJsonCodec.parse(it) }
        }
        AllowanceLedgerSnapshot(cachedJson, usages)
    }

    fun usage(packageName: String): AllowanceUsageRecord = withLock {
        AllowanceLedgerJsonCodec.parse(usageObjectLocked().optJSONObject(packageName) ?: JSONObject())
    }

    fun ensureIntervalWindowStarted(
        packageName: String,
        today: String,
        nowMs: Long,
        windowMs: Long,
    ): Long = withLock {
        val root = usageObjectLocked()
        val json = root.optJSONObject(packageName) ?: JSONObject()
        val record = AllowanceLedgerJsonCodec.parse(json)
        val isCurrent = record.windowStartMs > 0L &&
            nowMs <= record.windowStartMs + windowMs
        if (isCurrent) return@withLock record.windowStartMs

        val startedAt = nowMs.coerceAtLeast(1L)
        writeRecordLocked(
            root,
            packageName,
            json,
            record.copy(
                mode = MODE_INTERVAL,
                date = today,
                windowStartMs = startedAt,
                usedMs = 0L,
                confirmedUsedMs = 0L,
                confirmedAtMs = 0L,
                estimatedExtraMs = 0L,
            ),
        )
        startedAt
    }

    fun addEstimatedTimeUsage(
        packageName: String,
        mode: String,
        today: String,
        windowStartMs: Long,
        deltaMs: Long,
    ): Boolean = withLock {
        if (deltaMs <= 0L || (mode != MODE_TIME_BUDGET && mode != MODE_INTERVAL)) {
            return@withLock false
        }
        val root = usageObjectLocked()
        val json = root.optJSONObject(packageName) ?: JSONObject()
        val record = AllowanceLedgerJsonCodec.parse(json)
        val samePeriod = if (mode == MODE_TIME_BUDGET) {
            record.date == today
        } else {
            record.windowStartMs == windowStartMs && windowStartMs > 0L
        }
        if (mode == MODE_INTERVAL && !samePeriod) return@withLock false

        val base = if (samePeriod) record else AllowanceLedgerJsonCodec.parse(JSONObject())
        val extra = saturatedAdd(base.estimatedExtraMs, deltaMs)
        writeRecordLocked(
            root,
            packageName,
            json,
            base.copy(
                mode = mode,
                date = today,
                windowStartMs = if (mode == MODE_INTERVAL) windowStartMs else base.windowStartMs,
                usedMs = saturatedAdd(base.confirmedUsedMs, extra),
                estimatedExtraMs = extra,
            ),
        )
        true
    }

    fun addEstimatedOpen(packageName: String, today: String): Boolean = withLock {
        val root = usageObjectLocked()
        val json = root.optJSONObject(packageName) ?: JSONObject()
        val record = AllowanceLedgerJsonCodec.parse(json)
        val base = if (record.date == today) record else AllowanceLedgerJsonCodec.parse(JSONObject())
        val extra = saturatedAdd(base.estimatedExtraOpens, 1)
        writeRecordLocked(
            root,
            packageName,
            json,
            base.copy(
                mode = MODE_COUNT,
                date = today,
                count = saturatedAdd(base.confirmedCount, extra),
                estimatedExtraOpens = extra,
            ),
        )
        true
    }

    fun recoverCheckpointTime(
        packageName: String,
        mode: String,
        today: String,
        windowStartMs: Long,
        elapsedMs: Long,
        maximumRecoveryMs: Long,
    ): Long = withLock {
        val recoveredMs = elapsedMs.coerceAtLeast(0L).coerceAtMost(maximumRecoveryMs)
        if (recoveredMs == 0L) return@withLock 0L
        if (
            addEstimatedTimeUsage(
                packageName = packageName,
                mode = mode,
                today = today,
                windowStartMs = windowStartMs,
                deltaMs = recoveredMs,
            )
        ) {
            recoveredMs
        } else {
            0L
        }
    }

    fun reconcileTimeUsage(
        packageName: String,
        mode: String,
        today: String,
        windowStartMs: Long,
        usedMs: Long,
        atMs: Long,
    ): Boolean = withLock {
        if (mode != MODE_TIME_BUDGET && mode != MODE_INTERVAL) return@withLock false
        val root = usageObjectLocked()
        val json = root.optJSONObject(packageName) ?: JSONObject()
        val record = AllowanceLedgerJsonCodec.parse(json)
        val samePeriod = if (mode == MODE_TIME_BUDGET) {
            record.date == today
        } else {
            record.windowStartMs == windowStartMs && windowStartMs > 0L
        }
        val priorConfirmed = if (samePeriod) record.confirmedUsedMs else 0L
        val priorEffective = if (samePeriod && record.confirmedAtMs <= 0L) {
            record.usedMs
        } else {
            0L
        }
        val confirmed = maxOf(priorConfirmed, priorEffective, usedMs.coerceAtLeast(0L))
        writeRecordLocked(
            root,
            packageName,
            json,
            record.copy(
                mode = mode,
                date = today,
                windowStartMs = if (mode == MODE_INTERVAL) windowStartMs else record.windowStartMs,
                usedMs = confirmed,
                confirmedUsedMs = confirmed,
                confirmedAtMs = maxOf(if (samePeriod) record.confirmedAtMs else 0L, atMs),
                estimatedExtraMs = 0L,
            ),
        )
        true
    }

    fun reconcileCountUsage(
        packageName: String,
        today: String,
        count: Int,
        atMs: Long,
    ): Boolean = withLock {
        val root = usageObjectLocked()
        val json = root.optJSONObject(packageName) ?: JSONObject()
        val record = AllowanceLedgerJsonCodec.parse(json)
        val sameDay = record.date == today
        val confirmed = maxOf(
            if (sameDay) record.confirmedCount else 0,
            count.coerceAtLeast(0),
        )
        writeRecordLocked(
            root,
            packageName,
            json,
            record.copy(
                mode = MODE_COUNT,
                date = today,
                count = confirmed,
                confirmedCount = confirmed,
                confirmedAtMs = maxOf(if (sameDay) record.confirmedAtMs else 0L, atMs),
                estimatedExtraOpens = 0,
            ),
        )
        true
    }

    fun readAllowance(
        packageName: String,
        mode: String,
        today: String,
        nowMs: Long,
        limit: Long,
        windowMs: Long = 0L,
    ): AllowanceReadResult = withLock {
        val record = AllowanceLedgerJsonCodec.parse(
            usageObjectLocked().optJSONObject(packageName) ?: JSONObject(),
        )
        val windowExpired = mode == MODE_INTERVAL && nowMs > record.windowStartMs + windowMs
        val count = if (mode == MODE_COUNT && record.date == today) record.count else 0
        val usedMs = when (mode) {
            MODE_TIME_BUDGET -> if (record.date == today) record.usedMs else 0L
            MODE_INTERVAL -> if (windowExpired) 0L else record.usedMs
            else -> 0L
        }
        val consumed = if (mode == MODE_COUNT) count.toLong() else usedMs
        val remaining = (limit - consumed).coerceAtLeast(0L)
        val exhausted = when (mode) {
            MODE_COUNT, MODE_TIME_BUDGET -> consumed >= limit
            MODE_INTERVAL -> !windowExpired && consumed >= limit
            else -> false
        }
        AllowanceReadResult(
            count = count,
            usedMs = usedMs,
            windowStartMs = record.windowStartMs,
            windowExpired = windowExpired,
            remaining = remaining,
            exhausted = exhausted,
        )
    }

    fun recordOpen(
        packageName: String,
        mode: String,
        today: String,
        nowMs: Long,
        limit: Long,
        windowMs: Long = 0L,
    ): Long = withLock {
        val root = usageObjectLocked()
        val json = root.optJSONObject(packageName) ?: JSONObject()
        val record = AllowanceLedgerJsonCodec.parse(json)
        val sameDay = record.date == today
        val windowExpired = mode == MODE_INTERVAL && nowMs > record.windowStartMs + windowMs
        val sessionEndMs: Long
        val updated = when (mode) {
            MODE_COUNT -> {
                val nextCount = saturatedAdd(if (sameDay) record.count else 0, 1)
                sessionEndMs = 0L
                record.copy(
                    mode = mode,
                    date = today,
                    count = nextCount,
                    confirmedCount = nextCount,
                    confirmedAtMs = if (sameDay) record.confirmedAtMs else 0L,
                    estimatedExtraOpens = 0,
                )
            }
            MODE_TIME_BUDGET -> {
                val used = if (sameDay) record.usedMs else 0L
                sessionEndMs = nowMs + (limit - used).coerceAtLeast(0L)
                record.copy(
                    mode = mode,
                    date = today,
                    usedMs = used,
                    confirmedUsedMs = used,
                    confirmedAtMs = if (sameDay) record.confirmedAtMs else 0L,
                    estimatedExtraMs = 0L,
                )
            }
            MODE_INTERVAL -> {
                val start = if (windowExpired) nowMs else record.windowStartMs
                val used = if (windowExpired) 0L else record.usedMs
                sessionEndMs = nowMs + (limit - used).coerceAtLeast(0L)
                record.copy(
                    mode = mode,
                    date = today,
                    windowStartMs = start,
                    usedMs = used,
                    confirmedUsedMs = used,
                    confirmedAtMs = if (windowExpired) 0L else record.confirmedAtMs,
                    estimatedExtraMs = 0L,
                )
            }
            else -> return@withLock 0L
        }
        writeRecordLocked(root, packageName, json, updated)
        sessionEndMs
    }

    fun accumulateTimedUsage(
        packageName: String,
        mode: String,
        today: String,
        openedAtMs: Long,
        nowMs: Long,
        midnightMs: Long,
        limitMs: Long,
        windowMs: Long,
    ): Boolean = withLock {
        val elapsed = (nowMs - openedAtMs).coerceAtLeast(0L)
        if (elapsed == 0L || (mode != MODE_TIME_BUDGET && mode != MODE_INTERVAL)) {
            return@withLock false
        }
        val root = usageObjectLocked()
        val json = root.optJSONObject(packageName) ?: JSONObject()
        val record = AllowanceLedgerJsonCodec.parse(json)
        val sameDay = record.date == today
        val updated = when (mode) {
            MODE_TIME_BUDGET -> {
                val used = if (openedAtMs < midnightMs) {
                    val elapsedToday =
                        (nowMs - midnightMs).coerceAtLeast(0L).coerceAtMost(limitMs)
                    maxOf(if (sameDay) record.usedMs else 0L, elapsedToday)
                } else {
                    ((if (sameDay) record.usedMs else 0L) + elapsed).coerceAtMost(limitMs)
                }
                record.copy(
                    mode = mode,
                    date = today,
                    usedMs = used,
                    confirmedUsedMs = used,
                    confirmedAtMs = if (sameDay) record.confirmedAtMs else 0L,
                    estimatedExtraMs = 0L,
                )
            }
            else -> {
                val windowEndMs = record.windowStartMs + windowMs
                val used = if (record.windowStartMs > 0L && nowMs > windowEndMs) {
                    val inWindow = (windowEndMs - openedAtMs).coerceAtLeast(0L)
                    (record.usedMs + inWindow).coerceAtMost(limitMs)
                } else {
                    (record.usedMs + elapsed).coerceAtMost(limitMs)
                }
                record.copy(
                    mode = mode,
                    usedMs = used,
                    confirmedUsedMs = used,
                    confirmedAtMs = record.confirmedAtMs,
                    estimatedExtraMs = 0L,
                )
            }
        }
        writeRecordLocked(root, packageName, json, updated)
        true
    }

    fun raiseCountUsage(today: String, updates: List<AllowanceCountUpdate>, atMs: Long): Boolean =
        withLock {
            val root = usageObjectLocked()
            var changed = false
            for (update in updates) {
                val json = root.optJSONObject(update.packageName) ?: JSONObject()
                val record = AllowanceLedgerJsonCodec.parse(json)
                val current = if (record.date == today) record.count else 0
                val target = update.count.coerceAtLeast(0)
                if (target <= current) continue
                val updated = record.copy(
                    mode = MODE_COUNT,
                    date = today,
                    count = target,
                    confirmedCount = target,
                    confirmedAtMs = maxOf(
                        if (record.date == today) record.confirmedAtMs else 0L,
                        atMs.coerceAtLeast(0L),
                    ),
                    estimatedExtraOpens = 0,
                )
                writeRecordLocked(root, update.packageName, json, updated, persist = false)
                changed = true
            }
            if (changed) persistLocked()
            changed
        }

    fun raiseTimeUsage(today: String, updates: List<AllowanceTimeUpdate>, atMs: Long): Boolean =
        withLock {
            val root = usageObjectLocked()
            var changed = false
            for (update in updates) {
                if (update.mode != MODE_TIME_BUDGET && update.mode != MODE_INTERVAL) continue
                val json = root.optJSONObject(update.packageName) ?: JSONObject()
                val record = AllowanceLedgerJsonCodec.parse(json)
                val samePeriod = if (update.mode == MODE_TIME_BUDGET) {
                    record.date == today
                } else {
                    record.windowStartMs == update.windowStartMs
                }
                val current = if (samePeriod) record.usedMs else 0L
                val target = update.usedMs.coerceAtLeast(0L)
                if (target <= current) continue
                val updated = record.copy(
                    mode = update.mode,
                    date = today,
                    windowStartMs = if (update.mode == MODE_INTERVAL) {
                        update.windowStartMs.coerceAtLeast(0L)
                    } else {
                        record.windowStartMs
                    },
                    usedMs = target,
                    confirmedUsedMs = target,
                    confirmedAtMs = maxOf(
                        if (samePeriod) record.confirmedAtMs else 0L,
                        atMs.coerceAtLeast(0L),
                    ),
                    estimatedExtraMs = 0L,
                )
                writeRecordLocked(root, update.packageName, json, updated, persist = false)
                changed = true
            }
            if (changed) persistLocked()
            changed
        }

    fun reset(packageName: String? = null) = withLock {
        val root = usageObjectLocked()
        if (packageName == null) {
            root.keys().asSequence().toList().forEach(root::remove)
        } else {
            root.remove(packageName)
        }
        persistLocked()
    }

    private fun writeRecordLocked(
        root: JSONObject,
        packageName: String,
        json: JSONObject,
        record: AllowanceUsageRecord,
        persist: Boolean = true,
    ) {
        AllowanceLedgerJsonCodec.writeInto(json, record)
        root.put(packageName, json)
        if (persist) persistLocked()
    }

    private fun usageObjectLocked(): JSONObject {
        cachedUsage?.let { return it }
        val raw = store.readUsageJson()
        cachedJson = raw
        cachedUsage = try {
            if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
        return cachedUsage!!
    }

    private fun persistLocked() {
        val json = usageObjectLocked().toString()
        cachedJson = json
        store.writeUsageJson(json)
    }

    companion object {
        const val PREFS_NAME = "focusday_prefs"
        const val PREF_DAILY_ALLOWANCE_USED = "daily_allowance_used"
        const val MAX_ESTIMATED_SEGMENT_MS = 4L * 60 * 60 * 1_000L

        const val MODE_COUNT = "count"
        const val MODE_TIME_BUDGET = "time_budget"
        const val MODE_INTERVAL = "interval"

        private fun saturatedAdd(left: Int, right: Int): Int =
            if (Int.MAX_VALUE - left < right) Int.MAX_VALUE else left + right

        private fun saturatedAdd(left: Long, right: Long): Long =
            if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }
}
