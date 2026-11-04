package com.tbtechs.focusflow.ui.stats

import com.tbtechs.focusflow.data.local.entity.FindingEntity

internal enum class FindingActionKind { BLOCK_NOW, SET_LIMIT }

/** One thing the person can do about a finding: block the app now, or cap its daily time. */
internal data class FindingAction(
    val kind: FindingActionKind,
    val label: String,
    val packageName: String,
    val minutes: Int? = null,
)

/** Findings about a single app, where blocking that app is a sensible response. */
internal val APP_FINDING_TYPES = setOf(
    "VARIABLE_REWARD_LOOP",
    "INFINITE_SESSION_DESIGN",
    "MORNING_HIJACK",
    "ESCALATING_CAPTURE",
    "STREAK_LOCK_IN",
    "LATE_NIGHT_USE",
    "HEAVY_DAILY_USE",
    "ALLOWANCE_SUGGESTION",
    "ENTRY_APP",
    "APP_HOPPING",
    "TEMPTATION_PEAK",
    "WEEKEND_SPIKE",
)

/** Findings that carry a suggested daily limit in their evidence. */
internal val LIMIT_FINDING_TYPES = setOf("HEAVY_DAILY_USE", "ALLOWANCE_SUGGESTION", "WEEKEND_SPIKE")

private val SUGGESTED_MINUTES = Regex("\"suggested_min\":(\\d+)")

internal fun findingActions(finding: FindingEntity): List<FindingAction> {
    val packageName = finding.subjectPackage
    if (packageName.isNullOrBlank() || finding.detectionType !in APP_FINDING_TYPES) {
        return emptyList()
    }
    // "What you open first" can be a messaging app; only offer a block when the app is a feed.
    if (finding.detectionType == "ENTRY_APP" && !finding.evidenceJson.contains("\"blockable\":true")) {
        return emptyList()
    }
    val name = finding.subjectAppName?.takeIf { it.isNotBlank() } ?: "this app"
    val actions = mutableListOf(
        FindingAction(FindingActionKind.BLOCK_NOW, "Block $name now", packageName),
    )
    if (finding.detectionType in LIMIT_FINDING_TYPES) {
        val minutes = SUGGESTED_MINUTES.find(finding.evidenceJson)?.groupValues?.get(1)?.toIntOrNull()
        if (minutes != null && minutes in 5..600) {
            actions += FindingAction(
                kind = FindingActionKind.SET_LIMIT,
                label = "Limit $name to $minutes min a day",
                packageName = packageName,
                minutes = minutes,
            )
        }
    }
    return actions
}
