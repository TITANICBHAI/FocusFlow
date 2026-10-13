package com.tbtechs.focusflow.data.repository

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tbtechs.focusflow.enforcement.TaskAlarmActivity
import com.tbtechs.focusflow.enforcement.receivers.TaskEndAlarmReceiver
import com.tbtechs.focusflow.data.restore.RestoreGate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskEndAlarmIdentityInstrumentedTest {

    @Test
    fun cancellingMissingOrExistingAlarmDoesNotCreatePendingIntents() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val taskId = "cancel-lookup-test-${UUID.randomUUID()}"
        val repository = AlarmRepository(context, RestoreGate())
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        assertNull(existingAlarmPendingIntent(context, taskId))
        assertNull(existingShowPendingIntent(context, taskId))
        assertTrue(repository.cancelAlarm(taskId))
        assertNull(existingAlarmPendingIntent(context, taskId))
        assertNull(existingShowPendingIntent(context, taskId))

        val alarm = AlarmRepository.buildAlarmPendingIntent(
            context,
            taskId,
            "Task",
            System.currentTimeMillis() + 60_000L,
            flags,
        )
        val show = AlarmRepository.buildShowPendingIntent(
            context,
            taskId,
            "Task",
            System.currentTimeMillis() + 60_000L,
            flags,
        )
        try {
            assertNotNull(existingAlarmPendingIntent(context, taskId))
            assertNotNull(existingShowPendingIntent(context, taskId))
            assertTrue(repository.cancelAlarm(taskId))
            assertNull(existingAlarmPendingIntent(context, taskId))
            assertNull(existingShowPendingIntent(context, taskId))
        } finally {
            alarm.cancel()
            show.cancel()
        }
    }

    @Test
    fun twoTasksHaveIndependentAlarmAndActivityPendingIntents() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val firstId = "alarm-test-${UUID.randomUUID()}-one"
        val secondId = "alarm-test-${UUID.randomUUID()}-two"
        val endMs = System.currentTimeMillis() + 60_000L

        val firstAlarm = AlarmRepository.buildAlarmPendingIntent(
            context, firstId, "first", endMs, flags,
        )
        val secondAlarm = AlarmRepository.buildAlarmPendingIntent(
            context, secondId, "second", endMs, flags,
        )
        val firstShow = AlarmRepository.buildShowPendingIntent(
            context, firstId, "first", endMs, flags,
        )
        val secondShow = AlarmRepository.buildShowPendingIntent(
            context, secondId, "second", endMs, flags,
        )

        try {
            assertNotEquals(firstAlarm, secondAlarm)
            assertNotEquals(firstShow, secondShow)
            assertNotEquals(
                TaskEndAlarmIdentity.notificationTag(firstId),
                TaskEndAlarmIdentity.notificationTag(secondId),
            )

            val sameIdentityUpdated = AlarmRepository.buildAlarmPendingIntent(
                context, firstId, "renamed", endMs + 30_000L, flags,
            )
            assertEquals(firstAlarm, sameIdentityUpdated)
            sameIdentityUpdated.cancel()
            firstShow.cancel()

            assertNull(existingAlarmPendingIntent(context, firstId))
            assertNotNull(existingAlarmPendingIntent(context, secondId))
            assertNull(existingShowPendingIntent(context, firstId))
            assertNotNull(existingShowPendingIntent(context, secondId))
        } finally {
            firstAlarm.cancel()
            secondAlarm.cancel()
            firstShow.cancel()
            secondShow.cancel()
        }
    }

    @Test
    fun notificationDedupeIsPerTaskAndPerEndTime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val registry = TaskAlarmRegistry(context)
        val firstId = "dedupe-test-${UUID.randomUUID()}-one"
        val secondId = "dedupe-test-${UUID.randomUUID()}-two"
        val nowMs = System.currentTimeMillis()
        val firstEnd = nowMs + 5_000L
        val posts = AtomicInteger()

        assertTrue(registry.postOnce(firstId, firstEnd, nowMs) { posts.incrementAndGet() })
        assertFalse(registry.postOnce(firstId, firstEnd, nowMs) { posts.incrementAndGet() })
        assertTrue(registry.postOnce(secondId, firstEnd, nowMs) { posts.incrementAndGet() })
        assertTrue(registry.postOnce(firstId, firstEnd + 60_000L, nowMs) { posts.incrementAndGet() })
        assertEquals(3, posts.get())
    }

    @Test
    fun capabilitySnapshotsPersistByPhaseAndFullscreenPromptIsOneTime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferencesName = "alarm-capability-test-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val registry = TaskAlarmRegistry(context, preferencesName)

        try {
            preferences.edit().putBoolean("full_screen_prompt_shown", true).commit()
            assertTrue(registry.recordCapabilitySnapshot("schedule", "schedule-snapshot", 1_000L))
            assertFalse(registry.recordCapabilitySnapshot("schedule", "schedule-snapshot", 2_000L))
            assertTrue(registry.recordCapabilitySnapshot("fire", "fire-snapshot", 3_000L))

            val byPhase = registry.capabilitySnapshots().associateBy { it.phase }
            assertEquals("schedule-snapshot", byPhase["schedule"]?.summary)
            assertEquals(2_000L, byPhase["schedule"]?.capturedAtEpochMs)
            assertEquals("fire-snapshot", byPhase["fire"]?.summary)
            assertEquals(3_000L, byPhase["fire"]?.capturedAtEpochMs)

            assertTrue(registry.markFullScreenPromptShownOnce())
            assertFalse(registry.markFullScreenPromptShownOnce())
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun alarmActivityReplacesTaskWhenAnotherAlarmArrivesViaNewIntent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousId = "activity-test-${UUID.randomUUID()}-previous"
        val incomingId = "activity-test-${UUID.randomUUID()}-incoming"
        val scenario = ActivityScenario.launch<TaskAlarmActivity>(
            alarmActivityIntent(context, previousId, "Previous task"),
        )

        try {
            scenario.onActivity { activity ->
                assertEquals(previousId, activity.intent.getStringExtra(TaskAlarmActivity.EXTRA_TASK_ID))
                assertTrue(activity.window.decorView.containsText("Previous task"))

                activity.startActivity(
                    alarmActivityIntent(context, incomingId, "Incoming task").apply {
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    },
                )
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(incomingId, activity.intent.getStringExtra(TaskAlarmActivity.EXTRA_TASK_ID))
                assertEquals(
                    "Incoming task",
                    activity.intent.getStringExtra(TaskAlarmActivity.EXTRA_TASK_NAME),
                )
                assertTrue(activity.window.decorView.containsText("Incoming task"))
                assertFalse(activity.window.decorView.containsText("Previous task"))
            }

            val instrumentation = InstrumentationRegistry.getInstrumentation()
            context.sendBroadcast(dismissAlarmIntent(context, previousId))
            instrumentation.waitForIdleSync()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)

            context.sendBroadcast(dismissAlarmIntent(context, incomingId))
            instrumentation.waitForIdleSync()
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        } finally {
            scenario.close()
        }
    }

    private fun existingAlarmPendingIntent(context: Context, taskId: String): PendingIntent? {
        val intent = Intent(context, TaskEndAlarmReceiver::class.java).apply {
            action = TaskEndAlarmReceiver.ACTION_FIRE
            data = TaskEndAlarmIdentity.dataUri(taskId)
            `package` = context.packageName
        }
        return PendingIntent.getBroadcast(
            context,
            TaskEndAlarmIdentity.REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun existingShowPendingIntent(context: Context, taskId: String): PendingIntent? {
        val intent = Intent(context, TaskAlarmActivity::class.java).apply {
            action = TaskAlarmActivity.ACTION_SHOW_ALARM
            data = TaskEndAlarmIdentity.dataUri(taskId)
            `package` = context.packageName
        }
        return PendingIntent.getActivity(
            context,
            TaskEndAlarmIdentity.REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmActivityIntent(
        context: Context,
        taskId: String,
        taskName: String,
    ) = Intent(context, TaskAlarmActivity::class.java).apply {
        action = TaskAlarmActivity.ACTION_SHOW_ALARM
        data = TaskEndAlarmIdentity.dataUri(taskId)
        putExtra(TaskAlarmActivity.EXTRA_TASK_ID, taskId)
        putExtra(TaskAlarmActivity.EXTRA_TASK_NAME, taskName)
        putExtra(TaskAlarmActivity.EXTRA_END_MS, System.currentTimeMillis() + 60_000L)
    }

    private fun dismissAlarmIntent(context: Context, taskId: String) =
        Intent(TaskAlarmActivity.ACTION_DISMISS_ALARM).apply {
            `package` = context.packageName
            putExtra(TaskAlarmActivity.EXTRA_TASK_ID, taskId)
        }

    private fun View.containsText(expected: String): Boolean = when (this) {
        is TextView -> text.toString() == expected
        is ViewGroup -> (0 until childCount).any { getChildAt(it).containsText(expected) }
        else -> false
    }
}