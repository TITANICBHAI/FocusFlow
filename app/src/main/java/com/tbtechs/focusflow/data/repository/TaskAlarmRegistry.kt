package com.tbtechs.focusflow.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * Durable local index of task-end alarms. It is intentionally stored apart
 * from enforcement preferences and is only a reconciliation aid; Room remains
 * the source of truth.
 */
data class AlarmCapabilitySnapshotRecord(
    val phase: String,
    val capturedAtEpochMs: Long,
    val summary: String,
)

class TaskAlarmRegistry(
    context: Context,
    preferencesName: String? = null,
) {
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences(preferencesName ?: PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun registeredTaskIds(): Set<String> = synchronized(registryLock) {
        preferences.getStringSet(KEY_REGISTRY, emptySet()).orEmpty().toSet()
    }

    fun deferredTaskIds(): Set<String> = synchronized(registryLock) {
        preferences.getStringSet(KEY_DEFERRED, emptySet()).orEmpty().toSet()
    }

    fun triggerAtMillis(taskId: String): Long? = synchronized(registryLock) {
        if (!preferences.contains(triggerKey(taskId))) null
        else preferences.getLong(triggerKey(taskId), 0L)
    }

    fun tierFor(taskId: String): AlarmTier? = synchronized(registryLock) {
        preferences.getString(tierKey(taskId), null)
            ?.let { runCatching { AlarmTier.valueOf(it) }.getOrNull() }
    }

    /** Keeps the latest schedule/fire capability snapshot in the alarm-only store. */
    fun recordCapabilitySnapshot(
        phase: String,
        summary: String,
        capturedAtEpochMs: Long = System.currentTimeMillis(),
    ): Boolean =
        synchronized(registryLock) {
            require(phase == SNAPSHOT_SCHEDULE || phase == SNAPSHOT_FIRE)
            val key = "$KEY_SNAPSHOT_PREFIX$phase"
            val timestampKey = "$KEY_SNAPSHOT_TIMESTAMP_PREFIX$phase"
            val changed = preferences.getString(key, null) != summary
            commit(
                preferences.edit()
                    .putString(key, summary)
                    .putLong(timestampKey, capturedAtEpochMs),
                "record task-end alarm capability snapshot",
            )
            changed
        }

    fun capabilitySnapshots(): List<AlarmCapabilitySnapshotRecord> =
        synchronized(registryLock) {
            listOf(SNAPSHOT_SCHEDULE, SNAPSHOT_FIRE).mapNotNull { phase ->
                preferences.getString("$KEY_SNAPSHOT_PREFIX$phase", null)?.let { summary ->
                    AlarmCapabilitySnapshotRecord(
                        phase = phase,
                        capturedAtEpochMs = preferences.getLong(
                            "$KEY_SNAPSHOT_TIMESTAMP_PREFIX$phase",
                            0L,
                        ),
                        summary = summary,
                    )
                }
            }
        }

    fun markFullScreenPromptShownOnce(): Boolean = synchronized(registryLock) {
        if (preferences.getBoolean(KEY_FSI_ACTIONABLE_PROMPT_SHOWN, false)) {
            return@synchronized false
        }
        commit(
            preferences.edit().putBoolean(KEY_FSI_ACTIONABLE_PROMPT_SHOWN, true),
            "record full-screen alarm capability prompt",
        )
        true
    }

    /** Adds desired IDs and trigger values in one synchronous commit before OS scheduling. */
    fun prepareDesired(alarms: Map<String, Long>) = synchronized(registryLock) {
        if (alarms.isEmpty()) return@synchronized
        val registry = preferences.getStringSet(KEY_REGISTRY, emptySet()).orEmpty().toMutableSet()
        registry.addAll(alarms.keys)
        val editor = preferences.edit().putStringSet(KEY_REGISTRY, registry)
        alarms.forEach { (taskId, triggerAtMs) -> editor.putLong(triggerKey(taskId), triggerAtMs) }
        commit(editor, "prepare desired task-end alarms")
    }

    fun markScheduled(taskId: String, tier: AlarmTier) = synchronized(registryLock) {
        val deferred = preferences.getStringSet(KEY_DEFERRED, emptySet()).orEmpty().toMutableSet()
        deferred.remove(taskId)
        commit(
            preferences.edit()
                .putStringSet(KEY_DEFERRED, deferred)
                .putString(tierKey(taskId), tier.name),
            "record scheduled task-end alarm",
        )
    }

    /** Returns true only when the task newly entered the deferred state. */
    fun markDeferred(taskId: String): Boolean = synchronized(registryLock) {
        val deferred = preferences.getStringSet(KEY_DEFERRED, emptySet()).orEmpty().toMutableSet()
        val newlyDeferred = deferred.add(taskId)
        commit(
            preferences.edit()
                .putStringSet(KEY_DEFERRED, deferred)
                .remove(tierKey(taskId)),
            "record deferred task-end alarm",
        )
        newlyDeferred
    }

    fun markFailed(taskId: String) = synchronized(registryLock) {
        val deferred = preferences.getStringSet(KEY_DEFERRED, emptySet()).orEmpty().toMutableSet()
        deferred.remove(taskId)
        commit(
            preferences.edit()
                .putStringSet(KEY_DEFERRED, deferred)
                .remove(tierKey(taskId)),
            "record failed task-end alarm",
        )
    }

    /** Removes an ID only after its PendingIntent has been cancelled. */
    fun remove(taskId: String) = synchronized(registryLock) {
        val registry = preferences.getStringSet(KEY_REGISTRY, emptySet()).orEmpty().toMutableSet()
        val deferred = preferences.getStringSet(KEY_DEFERRED, emptySet()).orEmpty().toMutableSet()
        registry.remove(taskId)
        deferred.remove(taskId)
        commit(
            preferences.edit()
                .putStringSet(KEY_REGISTRY, registry)
                .putStringSet(KEY_DEFERRED, deferred)
                .remove(triggerKey(taskId))
                .remove(tierKey(taskId)),
            "remove cancelled task-end alarm",
        )
    }

    /**
     * Posts at most once for a (task ID, end time) pair, then commits the
     * bounded 48-hour dedupe ledger. The shared lock also covers service and
     * receiver paths in this process.
     */
    fun postOnce(
        taskId: String,
        endMs: Long,
        nowMs: Long,
        post: () -> Unit,
    ): Boolean = synchronized(notificationLock) {
        val entries = readPostedEntries(nowMs)
        if (entries[taskId] == endMs) {
            persistPostedEntries(entries)
            return@synchronized false
        }
        post()
        entries[taskId] = endMs
        persistPostedEntries(entries)
        true
    }

    private fun readPostedEntries(nowMs: Long): MutableMap<String, Long> {
        val entries = linkedMapOf<String, Long>()
        preferences.getStringSet(KEY_POSTED, emptySet()).orEmpty().forEach { encoded ->
            val delimiter = encoded.lastIndexOf('|')
            if (delimiter <= 0) return@forEach
            val taskId = encoded.substring(0, delimiter)
            val endMs = encoded.substring(delimiter + 1).toLongOrNull() ?: return@forEach
            if (nowMs - endMs <= DEDUPE_RETENTION_MS) entries[taskId] = endMs
        }
        return entries
    }

    private fun persistPostedEntries(entries: Map<String, Long>) {
        val encoded = entries.mapTo(mutableSetOf()) { (taskId, endMs) -> "$taskId|$endMs" }
        commit(
            preferences.edit().putStringSet(KEY_POSTED, encoded),
            "persist task-end notification dedupe ledger",
        )
    }

    private fun commit(editor: SharedPreferences.Editor, operation: String) {
        check(editor.commit()) { "Could not $operation." }
    }

    private fun triggerKey(taskId: String) = "$KEY_TRIGGER_PREFIX$taskId"
    private fun tierKey(taskId: String) = "$KEY_TIER_PREFIX$taskId"

    companion object {
        const val PREFERENCES_NAME = "focusflow_alarm_state"
        const val KEY_REGISTRY = "alarm_registry"
        const val KEY_DEFERRED = "alarm_deferred_exact"
        const val KEY_POSTED = "alarm_posted"
        private const val KEY_TRIGGER_PREFIX = "trigger:"
        private const val KEY_TIER_PREFIX = "tier:"
        private const val KEY_SNAPSHOT_PREFIX = "capability_snapshot:"
        private const val KEY_SNAPSHOT_TIMESTAMP_PREFIX = "capability_snapshot_time:"
        private const val KEY_FSI_ACTIONABLE_PROMPT_SHOWN = "full_screen_actionable_prompt_shown_v2"
        private const val SNAPSHOT_SCHEDULE = "schedule"
        private const val SNAPSHOT_FIRE = "fire"
        private const val DEDUPE_RETENTION_MS = 48L * 60L * 60L * 1_000L
        private val registryLock = Any()
        private val notificationLock = Any()
    }
}

enum class AlarmTier {
    ALARM_CLOCK,
    EXACT_ALLOW_IDLE,
}

sealed interface AlarmScheduleResult {
    data object Scheduled : AlarmScheduleResult
    data object PastTrigger : AlarmScheduleResult
    data object DeferredExactUnavailable : AlarmScheduleResult
    data class Failed(val cause: Throwable) : AlarmScheduleResult
}