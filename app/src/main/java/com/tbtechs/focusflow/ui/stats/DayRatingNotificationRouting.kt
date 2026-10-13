package com.tbtechs.focusflow.ui.stats

internal data class DayRatingNotificationPlan(
    val requestNonce: Int,
    val openRatingExperience: Boolean,
)

internal fun planDayRatingNotification(
    requestNonce: Int,
    lastHandledRequestNonce: Int,
    reflectionPromptsEnabled: Boolean,
): DayRatingNotificationPlan? {
    if (requestNonce <= 0 || requestNonce <= lastHandledRequestNonce) return null
    return DayRatingNotificationPlan(
        requestNonce = requestNonce,
        openRatingExperience = reflectionPromptsEnabled,
    )
}
