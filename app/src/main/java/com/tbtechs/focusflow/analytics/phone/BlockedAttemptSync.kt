package com.tbtechs.focusflow.analytics.phone

import com.tbtechs.focusflow.analytics.TemptationEntry
import com.tbtechs.focusflow.data.local.entity.BlockedAttemptDayEntity
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId

/** Parses the greyout temptation log: `[{"pkg":"…","appName":"…","timestamp":123}, …]`. */
object TemptationLogParser {
    /** Items without a non-blank `pkg` are skipped. Any malformed input yields an empty list. */
    fun parse(raw: String): List<TemptationEntry> = try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            if (item.isNull("pkg")) return@mapNotNull null
            val pkg = item.optString("pkg").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = if (item.isNull("appName")) null else item.optString("appName").takeIf { it.isNotBlank() }
            TemptationEntry(pkg = pkg, appName = name, timestamp = item.optLong("timestamp", 0L))
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Turns the capped (500-entry) temptation log into durable per-day blocked-attempt counts.
 * The store keeps `max(existing, incoming)` per (date, package), so a day that the log cap has partly
 * truncated can never lower a count that was stored earlier.
 */
class BlockedAttemptSync(
    private val temptationLogProvider: suspend () -> String,
    private val store: PhoneBehaviourStore,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    /** Returns the number of rows handed to the store (0 when the log is empty or unreadable). */
    suspend fun sync(): Int {
        val entries = TemptationLogParser.parse(temptationLogProvider()).filter { it.timestamp > 0L }
        if (entries.isEmpty()) return 0

        val groups = LinkedHashMap<Pair<String, String>, MutableList<TemptationEntry>>()
        for (entry in entries) {
            val date = Instant.ofEpochMilli(entry.timestamp).atZone(zoneId).toLocalDate().toString()
            groups.getOrPut(date to entry.pkg) { mutableListOf() } += entry
        }
        val rows = groups.map { (key, group) ->
            val hourly = IntArray(24)
            for (entry in group) hourly[Instant.ofEpochMilli(entry.timestamp).atZone(zoneId).hour]++
            BlockedAttemptDayEntity(
                date = key.first,
                packageName = key.second,
                appName = group.lastOrNull { !it.appName.isNullOrBlank() }?.appName ?: key.second,
                attempts = group.size,
                hourlyAttempts = PhoneCodec.encodeInts(hourly.toList()),
            )
        }.sortedWith(compareBy({ it.date }, { it.packageName }))
        store.mergeBlockedMax(rows)
        return rows.size
    }
}
