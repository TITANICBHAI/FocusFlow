package com.tbtechs.focusflow.ui.stats

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import org.junit.Rule
import org.junit.Test

class FindingCardViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allowanceSuggestionUsesItsOwnLabelAndHidesResponseActions() {
        composeRule.setContent {
            MaterialTheme {
                FindingCardView(
                    finding = finding(
                        detectionType = "ALLOWANCE_SUGGESTION",
                        state = "seen",
                    ),
                    onMarkSeen = {},
                    onIntentional = { _, _ -> },
                    onAware = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("BASED ON YOUR DATA").assertExists()
        composeRule.onNodeWithText("I chose this").assertDoesNotExist()
        composeRule.onNodeWithText("I didn't know").assertDoesNotExist()
    }

    @Test
    fun substitutionFindingStillShowsResponseActions() {
        composeRule.setContent {
            MaterialTheme {
                FindingCardView(
                    finding = finding(
                        detectionType = "SUBSTITUTION",
                        state = "seen",
                    ),
                    onMarkSeen = {},
                    onIntentional = { _, _ -> },
                    onAware = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("I chose this").assertExists()
        composeRule.onNodeWithText("I didn't know").assertExists()
    }

    private fun finding(
        detectionType: String,
        state: String,
    ) = FindingEntity(
        id = "finding-id",
        detectionType = detectionType,
        subjectPackage = "com.example.app",
        subjectAppName = "Example",
        state = state,
        evidenceFingerprint = "fingerprint",
        evidenceJson = "{}",
        headline = "Finding headline",
        body = "Finding body",
        evidenceLine = "Evidence",
        firstDetectedAt = "2026-01-01T00:00:00Z",
        lastUpdatedAt = "2026-01-01T00:00:00Z",
        seenAt = "2026-01-01T00:00:00Z",
        resolvedAt = null,
        suppressedUntil = null,
    )
}