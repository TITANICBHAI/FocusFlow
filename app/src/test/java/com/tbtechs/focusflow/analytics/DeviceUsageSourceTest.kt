package com.tbtechs.focusflow.analytics

import com.tbtechs.focusflow.data.local.entity.UsageRollupAppDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupDayEntity
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceUsageSourceTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.parse("2026-10-11")
    private val todayStartMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
    private val dayMs = 24L * 60L * 60L * 1_000L
    private val minuteMs = 60L * 1_000L
    private val sampleEvents = listOf(
        appSession("com.instagram.android", 9 * 60L, 40 * minuteMs),
        appSession("com.whatsapp", 11 * 60L, 15 * minuteMs),
        appSession("com.google.android.apps.maps", 12 * 60L, 3 * minuteMs),
        appSession("com.android.settings", 13 * 60L, 400L),
    ).flatten()

    @Test
    fun multiAppDateKeepsSummaryDailyHourlyAndAppRowsConsistent() = runBlocking {
        val metadataReads = mutableMapOf<String, Int>()
        val debugLines = mutableListOf<String>()
        val result = source(
            events = UsageEventRead.Available(sampleEvents, todayStartMs),
            resolveMetadata = { packageName ->
                metadataReads[packageName] = metadataReads.getOrDefault(packageName, 0) + 1
                DeviceUsageAppMetadata(packageName.substringAfterLast('.'), "test")
            },
            debugLog = debugLines::add,
        ).read(todayStartMs, todayStartMs + dayMs - 1L, todayStartMs + dayMs - 1L)!!

        assertEquals(58, result.summary.totalMinutes)
        assertEquals(58, result.daily.single().totalMinutes)
        assertEquals(58.0066, result.hourly.totalForegroundMilliseconds / minuteMs.toDouble(), 0.001)
        assertEquals(
            setOf("com.instagram.android", "com.whatsapp", "com.google.android.apps.maps"),
            result.summary.apps.map { it.packageName }.toSet(),
        )
        assertEquals(
            mapOf(
                "com.instagram.android" to 40,
                "com.whatsapp" to 15,
                "com.google.android.apps.maps" to 3,
            ),
            result.summary.apps.associate { it.packageName to it.foregroundMinutes },
        )
        assertEquals(setOf(1), metadataReads.values.toSet())
        assertEquals(1, debugLines.size)
        assertTrue(debugLines.single().contains("eventCount=8"))
        assertTrue(debugLines.single().contains("sessionCount=4"))
        assertTrue(debugLines.single().contains("rowsPerDateBeforeGrouping="))
        assertTrue(debugLines.single().contains("dateSourcesAndStatuses="))
        assertTrue(debugLines.single().contains("selectedRows=4 displayedRows=3"))
    }

    @Test
    fun fourStoredDaysAndLiveTodayTotal478Minutes() = runBlocking {
        val storedMinutes = listOf(120, 90, 150, 60)
        val storedDates = (4 downTo 1).map { today.minusDays(it.toLong()) }
        val rollups = storedDates.mapIndexed { index, date ->
            rollup(date, "com.example.stored", storedMinutes[index])
        }
        val result = source(
            events = UsageEventRead.Available(sampleEvents, todayStartMs),
            rollups = rollups,
        ).read(
            rangeStartMs = storedDates.first().atStartOfDay(zone).toInstant().toEpochMilli(),
            rangeEndMs = todayStartMs + 17L * 60L * minuteMs,
            nowMs = todayStartMs + 17L * 60L * minuteMs,
        )!!

        assertEquals(478, result.summary.totalMinutes)
        assertEquals(listOf(120, 90, 150, 60, 58), result.daily.map { it.totalMinutes })
        assertEquals(478.0066, result.hourly.totalForegroundMilliseconds / minuteMs.toDouble(), 0.001)
    }

    @Test
    fun liveAndCompleteRollupSourcesProduceIdenticalPerAppResults() = runBlocking {
        val nextDayStartMs = todayStartMs + dayMs
        val live = source(
            events = UsageEventRead.Available(sampleEvents, todayStartMs),
        ).read(todayStartMs, nextDayStartMs - 1L, nextDayStartMs + 60L * minuteMs)!!
        val rolled = source(
            events = UsageEventRead.Available(sampleEvents, todayStartMs),
            rollups = listOf(rollup(today, null, null, sampleEvents)),
        ).read(todayStartMs, nextDayStartMs - 1L, nextDayStartMs + 60L * minuteMs)!!

        assertEquals(live.summary, rolled.summary)
        assertEquals(live.hourly, rolled.hourly)
        assertEquals(live.daily, rolled.daily)
    }

    @Test
    fun unavailableEventsBecomeUnavailableStateInsteadOfZeroUsage() = runBlocking {
        val debugLines = mutableListOf<String>()
        val failureLogs = mutableListOf<String>()
        val source = source(
            events = UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.ACCESS_REVOKED),
            debugLog = debugLines::add,
        )
        val result = readSource(
            sourceName = "device_usage",
            read = {
                source.read(
                    todayStartMs,
                    todayStartMs + dayMs - 1L,
                    todayStartMs + dayMs - 1L,
                )
            },
            fallback = null,
            logFailure = { message, _ -> failureLogs += message },
        )

        assertNull(result.value)
        assertEquals(SOURCE_UNAVAILABLE, result.state)
        assertEquals("DeviceUsageUnavailableException", result.failure?.exceptionType)
        assertEquals("ACCESS_REVOKED", result.failure?.reason)
        assertEquals(1, debugLines.size)
        assertTrue(debugLines.single().contains("eventCount=0"))
        assertTrue(debugLines.single().contains("selectedRows=0 displayedRows=0"))
        assertTrue(failureLogs.single().contains("unavailable: DeviceUsageUnavailableException: ACCESS_REVOKED"))
    }

    @Test
    fun unexpectedReadFailuresKeepTheirTypeAndReasonInTheFailureStateAndLog() = runBlocking {
        val failureLogs = mutableListOf<String>()
        val result = readSource(
            sourceName = "device_usage",
            read = { throw IllegalArgumentException("event adapter failed") },
            fallback = null,
            logFailure = { message, _ -> failureLogs += message },
        )

        assertNull(result.value)
        assertEquals(SOURCE_FAILED, result.state)
        assertEquals("IllegalArgumentException", result.failure?.exceptionType)
        assertEquals("event adapter failed", result.failure?.reason)
        assertTrue(failureLogs.single().contains("failed: IllegalArgumentException: event adapter failed"))
    }

    private fun source(
        events: UsageEventRead,
        rollups: List<Pair<UsageRollupDayEntity, List<UsageRollupAppDayEntity>>> = emptyList(),
        resolveMetadata: (String) -> DeviceUsageAppMetadata = {
            DeviceUsageAppMetadata(it.substringAfterLast('.'), null)
        },
        debugLog: (String) -> Unit = {},
    ) = DeviceUsageSource(
        appPackageName = "com.tbtechs.focusflow",
        eventSource = UsageEventsSource { _, _ -> events },
        usageHistoryRepository = UsageHistoryRepository(FakeHistoryStore(rollups)),
        persistRollupDays = { _, _, _, _, _ -> },
        resolveAppMetadata = resolveMetadata,
        isLaunchable = { true },
        zoneId = { zone },
        debugLog = debugLog,
    )

    private fun rollup(
        date: LocalDate,
        packageName: String?,
        minutes: Int?,
        events: List<ForegroundUsageEvent> = emptyList(),
    ): Pair<UsageRollupDayEntity, List<UsageRollupAppDayEntity>> {
        val rows = if (packageName != null && minutes != null) {
            listOf(appDay(date, packageName, minutes * minuteMs))
        } else {
            val sessions = ForegroundSpanTracker().sessions(
                events = events,
                windowStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                windowEndMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                nowMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
            UsageCalendarAggregator.aggregateByDate(
                sessions = sessions,
                rangeStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                rangeEndMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                zoneId = zone,
            )[date.toString()].orEmpty().map { app ->
                UsageRollupAppDayEntity(
                    date = date.toString(),
                    packageName = app.packageName,
                    appName = app.packageName.substringAfterLast('.'),
                    category = "test",
                    foregroundMs = app.foregroundMs,
                    hourlyMs = app.hourlyMs.joinToString(","),
                    launchCount = app.launchCount,
                    sessionCount = app.sessionCount,
                    firstStartAtMs = app.firstStartAtMs,
                    lastUsedAtMs = app.lastUsedAtMs,
                )
            }
        }
        val dayEntity = UsageRollupDayEntity(
            date = date.toString(),
            status = UsageRollupDayEntity.COMPLETE,
            coverageStartMs = null,
            coverageEndMs = null,
            pipelineVersion = 1,
            computedAtMs = 0L,
            totalForegroundMs = rows.sumOf { it.foregroundMs },
        )
        return dayEntity to rows
    }

    private fun appDay(date: LocalDate, packageName: String, foregroundMs: Long) =
        UsageRollupAppDayEntity(
            date = date.toString(),
            packageName = packageName,
            appName = packageName.substringAfterLast('.'),
            category = "test",
            foregroundMs = foregroundMs,
            hourlyMs = List(24) { if (it == 12) foregroundMs else 0L }.joinToString(","),
            launchCount = 1,
            sessionCount = 1,
            firstStartAtMs = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            lastUsedAtMs = date.atStartOfDay(zone).toInstant().toEpochMilli() + foregroundMs,
        )

    private fun appSession(
        packageName: String,
        startMinuteOfDay: Long,
        durationMs: Long,
    ): List<ForegroundUsageEvent> {
        val start = todayStartMs + startMinuteOfDay * minuteMs
        return listOf(
            ForegroundUsageEvent(ForegroundEventType.ACTIVITY_RESUMED, start, packageName),
            ForegroundUsageEvent(ForegroundEventType.ACTIVITY_PAUSED, start + durationMs, packageName),
        )
    }

    private class FakeHistoryStore(
        private val rollups: List<Pair<UsageRollupDayEntity, List<UsageRollupAppDayEntity>>>,
    ) : UsageHistoryStore {
        override suspend fun legacyAppDays(startDate: String, endDate: String) = emptyList<UsageHistoryAppDay>()

        override suspend fun legacySessions(startDate: String, endDate: String) =
            emptyList<com.tbtechs.focusflow.data.local.entity.AppSessionEntity>()

        override suspend fun rollupDays(startDate: String, endDate: String) =
            rollups.map { it.first }.filter { it.date in startDate..endDate }

        override suspend fun rollupAppDays(startDate: String, endDate: String) =
            rollups.flatMap { it.second }.filter { it.date in startDate..endDate }
    }
}
