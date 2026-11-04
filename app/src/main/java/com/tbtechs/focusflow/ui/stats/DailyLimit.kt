package com.tbtechs.focusflow.ui.stats

import org.json.JSONArray
import org.json.JSONObject

/**
 * Adds or replaces one app's daily time limit in the stored allowance configuration, leaving the
 * other apps' limits untouched. Uses the JSON shape the Settings screen writes, plus the
 * `packageName` key the enforcement layer looks entries up by.
 */
internal object DailyLimit {
    fun merge(existingJson: String?, packageName: String, minutes: Int): String {
        val budget = minutes.coerceIn(5, 600)
        val result = JSONArray()
        val old = runCatching { JSONArray(existingJson ?: "[]") }.getOrDefault(JSONArray())
        for (index in 0 until old.length()) {
            val item = old.optJSONObject(index) ?: continue
            val existingPackage = item.optString("package").ifBlank { item.optString("packageName") }
            if (existingPackage != packageName) result.put(item)
        }
        result.put(
            JSONObject().apply {
                // The Settings screen writes "package"; the enforcement code reads "packageName".
                // Writing both keeps this entry readable by either.
                put("package", packageName)
                put("packageName", packageName)
                put("dailyAllowanceMs", budget * 60_000L)
                put("mode", "time_budget")
                put("countPerDay", 1)
                put("budgetMinutes", budget)
                put("intervalMinutes", 5)
                put("intervalHours", 1)
            },
        )
        return result.toString()
    }
}
