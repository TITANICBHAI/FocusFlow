package com.tbtechs.focusflow.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySettingsMigrationTest {
    @Test
    fun missingSettingsTableCompletesFreshInstallMigrationWithoutValues() {
        val plan = LegacySettingsMigration.prepare(
            settingsTableExists = false,
            settingsJson = null,
        )

        assertTrue(plan.markComplete)
        assertTrue(plan.preferences.isEmpty())
    }

    @Test
    fun missingRowInExistingSettingsTableDoesNotSetCompletionMarker() {
        val plan = LegacySettingsMigration.prepare(
            settingsTableExists = true,
            settingsJson = null,
        )

        assertFalse(plan.markComplete)
        assertTrue(plan.preferences.isEmpty())
    }

    @Test
    fun existingRowMapsThroughAdapterAndCompletesMigration() {
        val plan = LegacySettingsMigration.prepare(
            settingsTableExists = true,
            settingsJson = """{"darkMode":false,"defaultDuration":45,"allowedInFocus":["com.example.focus"]}""",
        )

        assertTrue(plan.markComplete)
        assertEquals(
            LegacyPreferenceValue.BooleanValue(false),
            plan.preferences["dark_mode_enabled"],
        )
        assertEquals(
            LegacyPreferenceValue.IntValue(45),
            plan.preferences["default_duration_minutes"],
        )
        assertEquals(
            LegacyPreferenceValue.StringValue("""["com.example.focus"]"""),
            plan.preferences["allowed_focus_packages"],
        )
    }

    @Test
    fun malformedRowFailsWithoutReturningACompletionPlan() {
        val error = try {
            LegacySettingsMigration.prepare(
                settingsTableExists = true,
                settingsJson = """{"darkMode":""",
            )
            null
        } catch (caught: BackupJsonFormatException) {
            caught
        }

        assertNotNull(error)
    }
}
