package com.tbtechs.focusflow.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TextScaleSettingsPersistenceTest {

    @Test
    fun textScalesRoundTripAndNullOverridesRemoveTheirStoredValues() = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val preferencesName = "text-scale-test-${UUID.randomUUID()}"
        val testPreferences = targetContext.getSharedPreferences(
            preferencesName,
            Context.MODE_PRIVATE,
        )
        val isolatedContext = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                targetContext.getSharedPreferences(preferencesName, mode)
        }
        val repository = SettingsRepository(isolatedContext)

        try {
            val defaults = repository.readAppSettings()
            assertEquals(1f, defaults.generalTextScale, 0f)
            assertNull(defaults.homeTextScale)
            assertNull(defaults.focusTextScale)
            assertNull(defaults.statsTextScale)
            assertNull(defaults.settingsTextScale)
            assertNull(defaults.defenseTextScale)

            val configured = defaults.copy(
                generalTextScale = 1.27f,
                homeTextScale = 1.11f,
                focusTextScale = 0.81f,
                statsTextScale = 1.25f,
                settingsTextScale = 0.94f,
                defenseTextScale = 1.5f,
                screenTextScales = mapOf(
                    "stats::reports" to 0.87f,
                    "home::schedule:task_details" to 1.42f,
                ),
            )
            repository.setNotificationPreferences(configured)

            val persisted = repository.readAppSettings()
            assertEquals(configured.generalTextScale, persisted.generalTextScale, 0f)
            assertEquals(configured.homeTextScale!!, persisted.homeTextScale!!, 0f)
            assertEquals(configured.focusTextScale!!, persisted.focusTextScale!!, 0f)
            assertEquals(configured.statsTextScale!!, persisted.statsTextScale!!, 0f)
            assertEquals(configured.settingsTextScale!!, persisted.settingsTextScale!!, 0f)
            assertEquals(configured.defenseTextScale!!, persisted.defenseTextScale!!, 0f)
            assertEquals(configured.screenTextScales, persisted.screenTextScales)
            assertTrue(testPreferences.contains("focus_text_scale"))
            assertTrue(testPreferences.contains("home_text_scale"))
            assertTrue(testPreferences.contains("stats_text_scale"))
            assertTrue(testPreferences.contains("settings_text_scale"))
            assertTrue(testPreferences.contains("defense_text_scale"))
            assertTrue(testPreferences.contains("screen_text_scales"))

            repository.setNotificationPreferences(
                persisted.copy(
                    focusTextScale = null,
                    homeTextScale = null,
                    statsTextScale = null,
                    settingsTextScale = null,
                    defenseTextScale = null,
                    screenTextScales = emptyMap(),
                ),
            )

            val reset = repository.readAppSettings()
            assertEquals(1.27f, reset.generalTextScale, 0f)
            assertNull(reset.homeTextScale)
            assertNull(reset.focusTextScale)
            assertNull(reset.statsTextScale)
            assertNull(reset.settingsTextScale)
            assertNull(reset.defenseTextScale)
            assertTrue(reset.screenTextScales.isEmpty())
            assertFalse(testPreferences.contains("focus_text_scale"))
            assertFalse(testPreferences.contains("home_text_scale"))
            assertFalse(testPreferences.contains("stats_text_scale"))
            assertFalse(testPreferences.contains("settings_text_scale"))
            assertFalse(testPreferences.contains("defense_text_scale"))
        } finally {
            testPreferences.edit().clear().commit()
        }
    }
}