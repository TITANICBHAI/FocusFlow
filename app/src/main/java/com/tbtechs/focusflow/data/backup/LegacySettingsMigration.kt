package com.tbtechs.focusflow.data.backup

data class LegacySettingsMigrationPlan(
    val preferences: Map<String, LegacyPreferenceValue>,
    val markComplete: Boolean,
)

/**
 * Pure decision and mapping layer for the SQLite settings-blob migration.
 * A missing table means a fresh install; a missing row in an existing table
 * must remain retryable.
 */
object LegacySettingsMigration {
    fun prepare(
        settingsTableExists: Boolean,
        settingsJson: String?,
    ): LegacySettingsMigrationPlan {
        if (!settingsTableExists) {
            return LegacySettingsMigrationPlan(emptyMap(), markComplete = true)
        }
        if (settingsJson == null) {
            return LegacySettingsMigrationPlan(emptyMap(), markComplete = false)
        }

        val source = LegacySettingsAdapter.parseLegacySettingsJson(settingsJson)
        val preferences = LegacySettingsAdapter.toSharedPreferencesValues(source)
        return LegacySettingsMigrationPlan(preferences, markComplete = true)
    }
}
