package com.tbtechs.focusflow.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyAllowanceSavePolicyTest {
    @Test
    fun addedManualPackageCanAppearInListWithoutCreatingAnAllowance() {
        val available = missingAllowancePackageNames(
            installedPackages = setOf("com.example.installed"),
            configuredPackages = emptyList(),
            manuallyAddedPackages = setOf("com.example.manual"),
        )

        assertEquals(listOf("com.example.manual"), available)
        assertTrue(dailyAllowanceEntriesForSave(emptyList()).isEmpty())
    }

    @Test
    fun onlyConfiguredDraftsAreSavedWithTheirSelectedMode() {
        val entries = dailyAllowanceEntriesForSave(
            listOf(
                DailyAllowanceDraft(
                    packageName = "com.example.manual",
                    mode = AllowanceMode.TimeBudget,
                    budgetMinutes = 45,
                ),
            ),
        )

        assertEquals(1, entries.size)
        assertEquals("com.example.manual", entries.single().packageName)
        assertEquals("time_budget", entries.single().mode)
        assertEquals(45, entries.single().budgetMinutes)
        assertEquals(45L * 60_000L, entries.single().dailyAllowanceMs)
    }
}
