package com.tbtechs.focusflow.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TaskAlertSettingsPersistenceTest {
    @Test
    fun alertDefaultsPreserveExistingBehaviorAndNewChoicesPersist() = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val preferencesName = "task-alert-settings-${UUID.randomUUID()}"
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
            testPreferences.edit().putBoolean("task_reminders_enabled", false).commit()
            val defaults = repository.readAppSettings()
            assertFalse(defaults.taskRemindersEnabled)
            assertTrue(defaults.taskPreStartRemindersEnabled)
            assertTrue(defaults.taskAtStartReminderEnabled)
            assertTrue(defaults.taskMidSessionRemindersEnabled)
            assertTrue(defaults.taskWrapUpReminderEnabled)
            assertTrue(defaults.taskEndAlarmRings)
            assertTrue(SettingsRepository.taskEndAlarmRings(isolatedContext))

            repository.setNotificationPreferences(
                defaults.copy(
                    taskPreStartRemindersEnabled = false,
                    taskAtStartReminderEnabled = false,
                    taskMidSessionRemindersEnabled = false,
                    taskWrapUpReminderEnabled = false,
                    taskEndAlarmRings = false,
                ),
            )

            val saved = repository.readAppSettings()
            assertFalse(saved.taskPreStartRemindersEnabled)
            assertFalse(saved.taskAtStartReminderEnabled)
            assertFalse(saved.taskMidSessionRemindersEnabled)
            assertFalse(saved.taskWrapUpReminderEnabled)
            assertFalse(saved.taskEndAlarmRings)
            assertFalse(SettingsRepository.taskEndAlarmRings(isolatedContext))
        } finally {
            testPreferences.edit().clear().commit()
        }
    }
}
