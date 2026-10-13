package com.tbtechs.focusflow.enforcement

import org.json.JSONObject

/** The single JSON schema adapter used by [AllowanceLedger]. */
internal object AllowanceLedgerJsonCodec {
    fun writeInto(json: JSONObject, record: AllowanceUsageRecord) {
        json.put("mode", record.mode ?: JSONObject.NULL)
        json.put("date", record.date ?: JSONObject.NULL)
        json.put("count", record.count.coerceAtLeast(0))
        json.put("usedMs", record.usedMs.coerceAtLeast(0L))
        json.put("windowStartMs", record.windowStartMs.coerceAtLeast(0L))
        json.put("confirmedUsedMs", record.confirmedUsedMs.coerceAtLeast(0L))
        json.put("confirmedCount", record.confirmedCount.coerceAtLeast(0))
        json.put("confirmedAtMs", record.confirmedAtMs.coerceAtLeast(0L))
        json.put("estimatedExtraMs", record.estimatedExtraMs.coerceAtLeast(0L))
        json.put("estimatedExtraOpens", record.estimatedExtraOpens.coerceAtLeast(0))
    }

    fun parse(json: JSONObject): AllowanceUsageRecord {
        val legacyUsedMs = json.optLong("usedMs", 0L).coerceAtLeast(0L)
        val legacyCount = json.optInt("count", 0).coerceAtLeast(0)
        val confirmedUsedMs = json.optLong("confirmedUsedMs", legacyUsedMs).coerceAtLeast(0L)
        val confirmedCount = json.optInt("confirmedCount", legacyCount).coerceAtLeast(0)
        val estimatedExtraMs = json.optLong("estimatedExtraMs", 0L).coerceAtLeast(0L)
        val estimatedExtraOpens = json.optInt("estimatedExtraOpens", 0).coerceAtLeast(0)
        return AllowanceUsageRecord(
            mode = json.optString("mode").takeIf(String::isNotBlank),
            date = json.optString("date").takeIf(String::isNotBlank),
            count = saturatedAdd(confirmedCount, estimatedExtraOpens),
            windowStartMs = json.optLong("windowStartMs", 0L).coerceAtLeast(0L),
            usedMs = saturatedAdd(confirmedUsedMs, estimatedExtraMs),
            confirmedUsedMs = confirmedUsedMs,
            confirmedCount = confirmedCount,
            confirmedAtMs = json.optLong("confirmedAtMs", 0L).coerceAtLeast(0L),
            estimatedExtraMs = estimatedExtraMs,
            estimatedExtraOpens = estimatedExtraOpens,
        )
    }

    private fun saturatedAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

    private fun saturatedAdd(left: Int, right: Int): Int =
        if (Int.MAX_VALUE - left < right) Int.MAX_VALUE else left + right
}
