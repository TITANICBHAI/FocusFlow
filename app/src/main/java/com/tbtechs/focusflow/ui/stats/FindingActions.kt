package com.tbtechs.focusflow.ui.stats

import com.tbtechs.focusflow.data.local.entity.FindingEntity

/** One thing the person can do about a finding. Today that is always "block the app". */
internal data class FindingAction(
    val label: String,
    val packageName: String,
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
)

internal fun findingActions(finding: FindingEntity): List<FindingAction> {
    val packageName = finding.subjectPackage
    if (packageName.isNullOrBlank() || finding.detectionType !in APP_FINDING_TYPES) {
        return emptyList()
    }
    val name = finding.subjectAppName?.takeIf { it.isNotBlank() } ?: "this app"
    return listOf(FindingAction(label = "Block $name now", packageName = packageName))
}
