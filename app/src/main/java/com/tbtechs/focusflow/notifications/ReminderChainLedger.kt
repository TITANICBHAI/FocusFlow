package com.tbtechs.focusflow.notifications

import android.content.Context
import com.tbtechs.focusflow.data.model.Task
import java.nio.charset.StandardCharsets

interface ReminderLedgerStore {
    fun read(): Map<String, Long>
    fun write(entries: Map<String, Long>): Boolean
}

class SharedPreferencesReminderLedgerStore(context: Context) : ReminderLedgerStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun read(): Map<String, Long> =
        preferences.getStringSet(POSTED_SLOTS_KEY, emptySet())
            .orEmpty()
            .mapNotNull(::decodeEntry)
            .toMap()

    override fun write(entries: Map<String, Long>): Boolean {
        val encoded = entries.mapTo(mutableSetOf()) { (slotId, triggerMs) ->
            encodeEntry(slotId, triggerMs)
        }
        return preferences.edit()
            .putStringSet(POSTED_SLOTS_KEY, encoded)
            .commit()
    }

    private fun encodeEntry(slotId: String, triggerMs: Long): String {
        val encodedId = java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(slotId.toByteArray(StandardCharsets.UTF_8))
        return "$triggerMs|$encodedId"
    }

    private fun decodeEntry(entry: String): Pair<String, Long>? {
        val separator = entry.indexOf('|')
        if (separator <= 0 || separator == entry.lastIndex) return null
        val triggerMs = entry.substring(0, separator).toLongOrNull() ?: return null
        val slotId = runCatching {
            java.util.Base64.getUrlDecoder()
                .decode(entry.substring(separator + 1))
                .toString(StandardCharsets.UTF_8)
        }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return null
        return slotId to triggerMs
    }

    private companion object {
        const val PREFERENCES_NAME = "focusflow_reminder_chain"
        const val POSTED_SLOTS_KEY = "posted_slots"
    }
}

/**
 * Posts due slots once per (slot ID, trigger time), retaining only recent and
 * bounded history. Notification posting happens before persistence as required
 * by the reminder contract; a failed post is therefore not marked delivered.
 */
class ReminderChainLedger(
    private val store: ReminderLedgerStore,
) {
    fun cancelNotificationsForIneligibleTasks(
        tasks: List<Task>,
        cancel: (String) -> Unit,
    ) = synchronized(PROCESS_LOCK) {
        val eligibleTaskIds = tasks.asSequence()
            .filter { it.status == "scheduled" || it.status == "active" }
            .mapTo(mutableSetOf(), Task::id)
        store.read().keys
            .filter { slotId ->
                val taskId = ReminderPlanner.taskIdFromSlotId(slotId)
                taskId != null && taskId !in eligibleTaskIds
            }
            .forEach(cancel)
    }

    fun deliverDue(
        slots: List<ReminderSlot>,
        nowMs: Long,
        post: (ReminderSlot) -> Unit,
        onPostFailure: (ReminderSlot, Exception) -> Unit = { _, _ -> },
    ): List<ReminderSlot> = synchronized(PROCESS_LOCK) {
        val original = store.read()
        val ledger = prune(original, nowMs).toMutableMap()
        val delivered = mutableListOf<ReminderSlot>()

        ReminderDelivery.dueSlots(slots, nowMs).forEach { slot ->
            if (ledger[slot.id] == slot.triggerMs) return@forEach
            try {
                post(slot)
                ledger[slot.id] = slot.triggerMs
                delivered += slot
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onPostFailure(slot, error)
            }
        }

        val bounded = bound(ledger, nowMs)
        check(store.write(bounded)) {
            "The reminder delivery ledger could not be persisted."
        }
        delivered
    }

    private fun prune(entries: Map<String, Long>, nowMs: Long): Map<String, Long> {
        val oldestAllowed = nowMs - RETENTION_MS
        val newestAllowed = safeAdd(nowMs, ReminderPlanner.DELIVERY_TOLERANCE_MS)
        return entries.filterValues { triggerMs ->
            triggerMs >= oldestAllowed && triggerMs <= newestAllowed
        }
    }

    private fun bound(entries: Map<String, Long>, nowMs: Long): Map<String, Long> =
        prune(entries, nowMs)
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Long>> { it.value }
                    .thenByDescending { it.key },
            )
            .take(MAX_LEDGER_ENTRIES)
            .associate { it.key to it.value }

    private fun safeAdd(value: Long, offset: Long): Long =
        if (value > Long.MAX_VALUE - offset) Long.MAX_VALUE else value + offset

    private companion object {
        val PROCESS_LOCK = Any()
        const val RETENTION_MS = 48L * 60L * 60L * 1_000L
        const val MAX_LEDGER_ENTRIES = ReminderPlanner.MAX_SLOTS
    }
}

object ReminderDelivery {
    fun dueSlots(slots: List<ReminderSlot>, nowMs: Long): List<ReminderSlot> {
        val latestDue = if (nowMs > Long.MAX_VALUE - ReminderPlanner.DELIVERY_TOLERANCE_MS) {
            Long.MAX_VALUE
        } else {
            nowMs + ReminderPlanner.DELIVERY_TOLERANCE_MS
        }
        return slots.asSequence()
            .filter { it.triggerMs <= latestDue }
            .sortedWith(compareBy<ReminderSlot> { it.triggerMs }.thenBy { it.id })
            .toList()
    }
}