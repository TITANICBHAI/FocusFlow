package com.tbtechs.focusflow.analytics

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tbtechs.focusflow.data.local.FocusFlowDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class UsageRollupWriterInstrumentedTest {
    private lateinit var database: FocusFlowDatabase
    private lateinit var context: Context
    private val zone: ZoneId = ZoneId.systemDefault()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, FocusFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun runningWriterTwiceWithSameEventsLeavesIdenticalRows() = runBlocking {
        val today = LocalDate.now(zone)
        val date = today.minusDays(1)
        val start = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val end = date.atTime(9, 30).atZone(zone).toInstant().toEpochMilli()
        val events = listOf(resumed(start, "app.example"), paused(end, "app.example"))
        val writer = writer(events, start - 1)

        writer.writeRecentPastDays(at(today, 12, 0))
        val beforeDay = database.usageRollupDao().getDay(date.toString())
        val beforeApps = database.usageRollupDao().getAppDays(date.toString())
        val beforeSessions = database.usageRollupDao().getSessions(date.toString(), date.toString())
        writer.writeRecentPastDays(at(today, 12, 0))

        assertEquals(beforeDay, database.usageRollupDao().getDay(date.toString()))
        assertEquals(beforeApps, database.usageRollupDao().getAppDays(date.toString()))
        assertEquals(beforeSessions, database.usageRollupDao().getSessions(date.toString(), date.toString()))
        assertEquals(1, beforeApps.size)
        assertEquals(1, beforeSessions.size)
    }

    @Test
    fun completeDateIsNotOverwrittenByLaterPartialRead() = runBlocking {
        val today = LocalDate.now(zone)
        val date = today.minusDays(1)
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val start = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val originalEnd = date.atTime(9, 30).atZone(zone).toInstant().toEpochMilli()
        writer(
            listOf(
                ForegroundUsageEvent(ForegroundEventType.DEVICE_STARTUP, dayStart - 1),
                resumed(start, "app.example"),
                paused(originalEnd, "app.example"),
            ),
            dayStart - 1,
        ).writeRecentPastDays(at(today, 12, 0))

        val shorterEnd = date.atTime(9, 15).atZone(zone).toInstant().toEpochMilli()
        writer(
            listOf(resumed(start, "app.example"), paused(shorterEnd, "app.example")),
            start + 1,
        ).writeRecentPastDays(at(today, 12, 0))

        val savedDay = database.usageRollupDao().getDay(date.toString())
        val savedApp = database.usageRollupDao().getAppDays(date.toString()).single()
        assertEquals("COMPLETE", savedDay?.status)
        assertEquals(30 * MINUTE, savedApp.foregroundMs)
    }

    @Test
    fun todayNeverReceivesRollupRows() = runBlocking {
        val today = LocalDate.now(zone)
        val start = at(today, 10, 0)
        val end = at(today, 10, 20)
        val writer = writer(
            listOf(resumed(start, "app.example"), paused(end, "app.example")),
            start - 1,
        )
        repeat(3) { writer.writeRecentPastDays(at(today, 12, 0)) }

        assertNull(database.usageRollupDao().getDay(today.toString()))
        assertEquals(emptyList<Any>(), database.usageRollupDao().getAppDays(today.toString()))
    }

    @Test
    fun partialDateReplacementDeletesOldPackageRowsBeforeInsert() = runBlocking {
        val today = LocalDate.now(zone)
        val date = today.minusDays(1)
        val firstStart = at(date, 9, 0)
        val firstEnd = at(date, 9, 30)
        writer(
            listOf(resumed(firstStart, "app.old"), paused(firstEnd, "app.old")),
            firstStart + 1,
        ).writeRecentPastDays(at(today, 12, 0))

        val replacementStart = at(date, 10, 0)
        val replacementEnd = at(date, 10, 20)
        writer(
            listOf(resumed(replacementStart, "app.new"), paused(replacementEnd, "app.new")),
            replacementStart + 1,
        ).writeRecentPastDays(at(today, 12, 0))

        val appRows = database.usageRollupDao().getAppDays(date.toString())
        val sessionRows = database.usageRollupDao().getSessions(date.toString(), date.toString())
        assertEquals(listOf("app.new"), appRows.map { it.packageName })
        assertEquals(listOf("app.new"), sessionRows.map { it.packageName })
        assertEquals("PARTIAL", database.usageRollupDao().getDay(date.toString())?.status)
    }

    @Test
    fun openSessionKeepsStartDatePartialThenCompletesWithoutDuplicateSession() = runBlocking {
        val today = LocalDate.now(zone)
        val date = today.minusDays(1)
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val start = at(date, 23, 30)
        val firstNow = at(today, 1, 30)
        val sourceEvents = mutableListOf(
            ForegroundUsageEvent(ForegroundEventType.DEVICE_STARTUP, dayStart - 1),
            resumed(start, "app.example"),
        )
        val writer = writer(sourceEvents, dayStart - 1)

        writer.writeRecentPastDays(firstNow)
        assertEquals("PARTIAL", database.usageRollupDao().getDay(date.toString())?.status)
        assertEquals(emptyList<Any>(), database.usageRollupDao().getSessions(date.toString(), date.toString()))

        val closedAt = at(today, 1, 40)
        sourceEvents += paused(closedAt, "app.example")
        writer.writeRecentPastDays(at(today, 1, 45))

        assertEquals("COMPLETE", database.usageRollupDao().getDay(date.toString())?.status)
        val sessions = database.usageRollupDao().getSessions(date.toString(), date.toString())
        assertEquals(1, sessions.size)
        assertEquals(start, sessions.single().startedAtMs)
        assertEquals(closedAt, sessions.single().endedAtMs)
        assertNotNull(database.usageRollupDao().getDay(date.toString()))
    }

    private fun writer(events: List<ForegroundUsageEvent>, earliestEventAtMs: Long) =
        UsageRollupWriter(
            context = context,
            database = database,
            eventSource = UsageEventsSource { _, _ ->
                UsageEventRead.Available(events, earliestEventAtMs)
            },
        )

    private fun resumed(at: Long, packageName: String) = ForegroundUsageEvent(
        ForegroundEventType.ACTIVITY_RESUMED,
        at,
        packageName,
        "$packageName.MainActivity",
    )

    private fun paused(at: Long, packageName: String) = ForegroundUsageEvent(
        ForegroundEventType.ACTIVITY_PAUSED,
        at,
        packageName,
        "$packageName.MainActivity",
    )

    private fun at(date: LocalDate, hour: Int, minute: Int) =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private companion object {
        const val MINUTE = 60_000L
    }
}
