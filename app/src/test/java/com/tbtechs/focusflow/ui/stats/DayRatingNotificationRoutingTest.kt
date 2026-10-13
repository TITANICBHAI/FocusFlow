package com.tbtechs.focusflow.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayRatingNotificationRoutingTest {
    @Test
    fun newRequestOpensRatingExperienceWhenPromptsAreEnabled() {
        assertEquals(
            DayRatingNotificationPlan(requestNonce = 4, openRatingExperience = true),
            planDayRatingNotification(
                requestNonce = 4,
                lastHandledRequestNonce = 3,
                reflectionPromptsEnabled = true,
            ),
        )
    }

    @Test
    fun handledRequestIsNotPlayedAgain() {
        assertNull(
            planDayRatingNotification(
                requestNonce = 4,
                lastHandledRequestNonce = 4,
                reflectionPromptsEnabled = true,
            ),
        )
    }

    @Test
    fun disabledPromptsKeepTheNormalStatsDestination() {
        assertEquals(
            DayRatingNotificationPlan(requestNonce = 4, openRatingExperience = false),
            planDayRatingNotification(
                requestNonce = 4,
                lastHandledRequestNonce = 3,
                reflectionPromptsEnabled = false,
            ),
        )
    }

    @Test
    fun focusSkipsTheNonComposedInputForAnAlreadySavedRating() {
        assertEquals(
            false,
            shouldRequestDayRatingInputFocus(
                requestNonce = 4,
                isEditing = false,
                hasSavedRating = true,
            ),
        )
    }

    @Test
    fun focusRequestsAnAvailableInputForAnUnratedDay() {
        assertEquals(
            true,
            shouldRequestDayRatingInputFocus(
                requestNonce = 4,
                isEditing = true,
                hasSavedRating = false,
            ),
        )
    }
}
