package com.tbtechs.focusflow.enforcement

import java.util.TimeZone
import org.json.JSONArray

internal object ForegroundTaskGreyoutPolicy {
    fun isPackageBlocked(
        greyoutJson: String,
        packageName: String,
        atMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Boolean {
        if (greyoutJson.isBlank() || greyoutJson == "[]") return false

        return try {
            val windows = JSONArray(greyoutJson)
            for (index in 0 until windows.length()) {
                val window = windows.optJSONObject(index) ?: continue
                if (!window.optString("pkg").equals(packageName, ignoreCase = true)) continue
                val days = window.optJSONArray("days") ?: continue
                if (
                    GreyoutWindowMath.isActive(
                        daysOfWeek = (0 until days.length()).map(days::optInt),
                        startMinuteOfDay =
                            window.optInt("startHour") * 60 + window.optInt("startMin"),
                        endMinuteOfDay =
                            window.optInt("endHour") * 60 + window.optInt("endMin"),
                        atMs = atMs,
                        timeZone = timeZone,
                    )
                ) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }
}
