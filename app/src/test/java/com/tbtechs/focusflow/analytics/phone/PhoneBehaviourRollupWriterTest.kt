package com.tbtechs.focusflow.analytics.phone

import com.tbtechs.focusflow.analytics.ForegroundEventType
import com.tbtechs.focusflow.analytics.ForegroundEventType.ACTIVITY_PAUSED
import com.tbtechs.focusflow.analytics.ForegroundEventType.ACTIVITY_RESUMED
import com.tbtechs.focusflow.analytics.ForegroundEventType.KEYGUARD_HIDDEN
import com.tbtechs.focusflow.analytics.ForegroundEventType.SCREEN_INTERACTIVE
import com.tbtechs.focusflow.analytics.ForegroundEventType.SCREEN_NON_INTERACTIVE
import com.tbtechs.focusflow.analytics.ForegroundSession
import com.tbtechs.focusflow.analytics.ForegroundUsageEvent
import com.tbtechs.focusflow.analytics.UsageEventRead
import com.tbtechs.focusflow.analytics.UsageEventsSource
import com.tbtechs.focusflow.data.local.entity.BlockedAttemptDayEntity
import com.tbtechs.focusflow.data.local.entity.PhoneDayRollupEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class PhoneBehaviourRollupWriterTest {
    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 7)
    private val now = ms(today, 12, 0)
    private val hour = 3_600_000L

    private fun ms(date: LocalDate, h: Int, m: Int = 0, s: Int = 0): Long =
        ZonedDateTime.of(date.year, date.monthValue, date.dayOfMonth, h, m, s, 0, utc).toInstant().toEpochMilli()

    private fun d(back: Int): LocalDate = today.minusDays(back.toLong())

    /** One short phone use of com.app.a: wake, unlock, open app for 10 minutes, screen off. */
    private fun usageOn(date: LocalDate, pkg: String = "com.app.a"): List<ForegroundUsageEvent> = listOf(
        ForegroundUsageEvent(SCREEN_INTERACTIVE, ms(date, 9, 0, 0)),
        ForegroundUsageEvent(KEYGUARD_HIDDEN, ms(date, 9, 0, 3)),
        ForegroundUsageEvent(ACTIVITY_RESUMED, ms(date, 9, 0, 5), pkg, "$pkg.Main"),
        ForegroundUsageEvent(ACTIVITY_PAUSED, ms(date, 9, 10, 0), pkg, "$pkg.Main"),
        ForegroundUsageEvent(SCREEN_NON_INTERACTIVE, ms(date, 9, 10, 1)),
    )

    private class CountingSource(var result: UsageEventRead) : UsageEventsSource {
        var calls = 0
        val ranges = mutableListOf<Pair<Long, Long>>()
        override suspend fun readForegroundEvents(startMs: Long, endMs: Long): UsageEventRead {
            calls++
            ranges += startMs to endMs
            return result
        }
    }

    private fun available(events: List<ForegroundUsageEvent>, earliest: Long?) =
        UsageEventRead.Available(events, earliest)

    private fun writer(
        store: FakePhoneBehaviourStore,
        source: UsageEventsSource,
        hook: (suspend (String, String, List<ForegroundSession>, Long?, Long) -> Unit)? = null,
        nameOf: (String) -> String = { "Name:$it" },
        clockNow: Long = now,
    ) = PhoneBehaviourRollupWriter(
        store = store,
        eventSource = source,
        excludedPackages = emptySet(),
        appNameResolver = nameOf,
        appRollupHook = hook,
        clock = Clock.fixed(Instant.ofEpochMilli(clockNow), utc),
        zoneId = utc,
    )

    private fun completeRow(date: LocalDate, computedAt: Long = now - 48 * hour, version: Int = PhoneThresholds.BEHAVIOUR_VERSION) =
        PhoneDay.empty(date.toString(), PhoneDayStatus.COMPLETE).toEntity(computedAt, null).copy(behaviourVersion = version)

    private fun partialRow(date: LocalDate, computedAt: Long) =
        PhoneDay.empty(date.toString(), PhoneDayStatus.PARTIAL).toEntity(computedAt, null)

    private fun fillComplete(store: FakePhoneBehaviourStore, except: Set<LocalDate> = emptySet()) {
        for (back in 1..PhoneThresholds.BACKFILL_DAYS) {
            val date = d(back)
            if (date !in except) store.days += completeRow(date)
        }
    }

    // 1
    @Test
    fun writesPastDaysWithEventsAndNeverToday() = runBlocking {
        val store = FakePhoneBehaviourStore()
        val events = (1..3).flatMap { usageOn(d(it)) } + usageOn(today)
        val source = CountingSource(available(events, earliest = ms(d(3), 0, 0)))
        val pass = writer(store, source).writeMissingPastDays(now)

        assertEquals(PhoneRollupPass.Written(listOf(d(3), d(2), d(1)).map { it.toString() }), pass)
        assertEquals(listOf(d(3), d(2), d(1)).map { it.toString() }, store.days.map { it.date }.sorted())
        assertTrue(store.days.none { it.date == today.toString() })
        assertTrue(store.days.all { it.status == PhoneDayRollupEntity.COMPLETE })
        assertTrue(store.days.all { it.unlockCount == 1 && it.wakeCount == 1 })
        assertTrue(store.days.all { it.computedAtMs == now && it.coverageStartMs == ms(d(3), 0, 0) })
        assertEquals(1, source.calls)
    }

    // 1b
    @Test
    fun readWindowStartsOneLookbackBeforeTheFirstCandidate() = runBlocking {
        val store = FakePhoneBehaviourStore()
        val source = CountingSource(available(emptyList(), earliest = ms(d(14), 0, 0)))
        writer(store, source).writeMissingPastDays(now)
        assertEquals(ms(d(14), 0, 0) - PhoneThresholds.EVENT_LOOKBACK_MS, source.ranges.single().first)
        assertEquals(now, source.ranges.single().second)
    }

    // 2
    @Test
    fun secondPassWithCompleteRowsDoesNotReadEvents() = runBlocking {
        val store = FakePhoneBehaviourStore()
        val events = (1..3).flatMap { usageOn(d(it)) }
        val source = CountingSource(available(events, earliest = ms(d(3), 0, 0)))
        val w = writer(store, source)
        w.writeMissingPastDays(now)
        val replaceBefore = store.replaceCalls
        val pass = w.writeMissingPastDays(now + 60_000)
        assertEquals(1, source.calls)
        assertEquals(PhoneRollupPass.Written(emptyList()), pass)
        assertEquals(replaceBefore, store.replaceCalls)
    }

    // 2b
    @Test
    fun noEventsAtAllWritesNothingAndIsNotReReadByTheSameWriter() = runBlocking {
        val store = FakePhoneBehaviourStore()
        val source = CountingSource(available(emptyList(), earliest = null))
        val w = writer(store, source)
        assertEquals(PhoneRollupPass.Written(emptyList()), w.writeMissingPastDays(now))
        assertTrue(store.days.isEmpty())
        w.writeMissingPastDays(now + 1_000)
        assertEquals(1, source.calls)
    }

    // 3
    @Test
    fun daysBeforeTheEarliestEventGetNoRowAndTheFirstObservedDayIsPartial() = runBlocking {
        val store = FakePhoneBehaviourStore()
        val source = CountingSource(available(usageOn(d(5)), earliest = ms(d(5), 12, 0)))
        writer(store, source).writeMissingPastDays(now)
        val byDate = store.days.associateBy { it.date }
        assertTrue(byDate.keys.none { it <= d(6).toString() })
        assertEquals(PhoneDayRollupEntity.PARTIAL, byDate.getValue(d(5).toString()).status)
        assertEquals(PhoneDayRollupEntity.COMPLETE, byDate.getValue(d(4).toString()).status)
        assertEquals(PhoneDayRollupEntity.COMPLETE, byDate.getValue(d(1).toString()).status)
    }

    // 3b
    @Test
    fun unresolvedTailOnYesterdayMakesItPartial() = runBlocking {
        val store = FakePhoneBehaviourStore()
        val earlyToday = ms(today, 1, 0)
        val events = listOf(
            ForegroundUsageEvent(SCREEN_INTERACTIVE, ms(d(1), 23, 30)),
            ForegroundUsageEvent(KEYGUARD_HIDDEN, ms(d(1), 23, 30, 2)),
            ForegroundUsageEvent(ACTIVITY_RESUMED, ms(d(1), 23, 30, 5), "com.app.a", "com.app.a.Main"),
        )
        val source = CountingSource(available(events, earliest = ms(d(3), 0, 0)))
        writer(store, source, clockNow = earlyToday).writeMissingPastDays(earlyToday)
        assertEquals(PhoneDayRollupEntity.PARTIAL, store.days.single { it.date == d(1).toString() }.status)
        assertEquals(PhoneDayRollupEntity.COMPLETE, store.days.single { it.date == d(2).toString() }.status)
    }

    // 4
    @Test
    fun recentPartialRowIsRecomputedButAFreshOldPartialIsNot() = runBlocking {
        val store = FakePhoneBehaviourStore()
        fillComplete(store, except = setOf(d(1), d(10)))
        store.days += partialRow(d(1), computedAt = now - hour)
        store.days += partialRow(d(10), computedAt = now - hour)
        val source = CountingSource(available(usageOn(d(1)), earliest = ms(d(14), 0, 0)))
        val pass = writer(store, source).writeMissingPastDays(now)

        assertEquals(PhoneRollupPass.Written(listOf(d(1).toString())), pass)
        assertEquals(1, source.calls)
        assertEquals(now, store.days.single { it.date == d(1).toString() }.computedAtMs)
        assertEquals(now - hour, store.days.single { it.date == d(10).toString() }.computedAtMs)
        assertEquals(PhoneRollupPass.Written(emptyList()), writer(store, source).writeMissingPastDays(now))
    }

    // 4b
    @Test
    fun oldPartialRowIsOnlyRecomputedAfter24Hours() = runBlocking {
        val fresh = FakePhoneBehaviourStore().also {
            fillComplete(it, except = setOf(d(10)))
            it.days += partialRow(d(10), computedAt = now - 23 * hour)
        }
        val freshSource = CountingSource(available(emptyList(), earliest = ms(d(14), 0, 0)))
        writer(fresh, freshSource).writeMissingPastDays(now)
        assertEquals(0, freshSource.calls)

        val stale = FakePhoneBehaviourStore().also {
            fillComplete(it, except = setOf(d(10)))
            it.days += partialRow(d(10), computedAt = now - 25 * hour)
        }
        val staleSource = CountingSource(available(emptyList(), earliest = ms(d(14), 0, 0)))
        val pass = writer(stale, staleSource).writeMissingPastDays(now)
        assertEquals(1, staleSource.calls)
        assertEquals(PhoneRollupPass.Written(listOf(d(10).toString())), pass)
        assertEquals(PhoneDayRollupEntity.COMPLETE, stale.days.single { it.date == d(10).toString() }.status)
    }

    // 4c
    @Test
    fun completeRowOfAnOlderBehaviourVersionIsRecomputed() = runBlocking {
        val store = FakePhoneBehaviourStore()
        fillComplete(store, except = setOf(d(4)))
        store.days += completeRow(d(4), version = PhoneThresholds.BEHAVIOUR_VERSION - 1)
        val source = CountingSource(available(usageOn(d(4)), earliest = ms(d(14), 0, 0)))
        writer(store, source).writeMissingPastDays(now)
        val row = store.days.single { it.date == d(4).toString() }
        assertEquals(PhoneThresholds.BEHAVIOUR_VERSION, row.behaviourVersion)
        assertEquals(1, row.unlockCount)
    }

    // 5
    @Test
    fun unknownReadReturnsUnknownAndTouchesNothing() = runBlocking {
        val store = FakePhoneBehaviourStore()
        var hookCalls = 0
        val source = CountingSource(UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.PERMISSION_MISSING))
        val pass = writer(store, source, hook = { _, _, _, _, _ -> hookCalls++ }).writeMissingPastDays(now)
        assertEquals(PhoneRollupPass.Unknown(UsageEventRead.Unknown.Reason.PERMISSION_MISSING), pass)
        assertEquals(0, store.replaceCalls)
        assertTrue(store.days.isEmpty())
        assertTrue(store.pruneCutoffs.isEmpty())
        assertEquals(0, hookCalls)
    }

    // 6
    @Test
    fun firstAppRowsUseTheResolvedAppName() = runBlocking {
        val store = FakePhoneBehaviourStore()
        val source = CountingSource(available(usageOn(d(1), "com.app.a"), earliest = ms(d(2), 0, 0)))
        writer(store, source, nameOf = { "Pretty $it" }).writeMissingPastDays(now)
        val row = store.firstAppRows.single()
        assertEquals(d(1).toString(), row.date)
        assertEquals("com.app.a", row.packageName)
        assertEquals("Pretty com.app.a", row.appName)
        assertEquals(1, row.unlockCount)
    }

    // 6b
    @Test
    fun recomputingADayReplacesItsFirstAppRows() = runBlocking {
        val store = FakePhoneBehaviourStore()
        store.days += partialRow(d(1), now - hour)
        store.firstAppRows += com.tbtechs.focusflow.data.local.entity.PhoneFirstAppDayEntity(d(1).toString(), "old.pkg", "Old", 9)
        fillComplete(store, except = setOf(d(1)))
        val source = CountingSource(available(usageOn(d(1), "com.app.a"), earliest = ms(d(14), 0, 0)))
        writer(store, source).writeMissingPastDays(now)
        assertEquals(listOf("com.app.a"), store.firstAppRows.filter { it.date == d(1).toString() }.map { it.packageName })
    }

    // 7
    @Test
    fun oldRowsArePrunedOnTheSlowPath() = runBlocking {
        val store = FakePhoneBehaviourStore()
        val cutoff = today.minusDays(PhoneThresholds.DAY_ROW_RETENTION_DAYS.toLong())
        store.days += completeRow(cutoff.minusDays(1))
        store.days += completeRow(cutoff)
        store.blockedRows += BlockedAttemptDayEntity(cutoff.minusDays(5).toString(), "p", "P", 1, "")
        store.firstAppRows += com.tbtechs.focusflow.data.local.entity.PhoneFirstAppDayEntity(cutoff.minusDays(5).toString(), "p", "P", 1)
        val source = CountingSource(available(usageOn(d(1)), earliest = ms(d(2), 0, 0)))
        writer(store, source).writeMissingPastDays(now)
        assertEquals(listOf(cutoff.toString()), store.pruneCutoffs)
        assertTrue(store.days.none { it.date == cutoff.minusDays(1).toString() })
        assertTrue(store.days.any { it.date == cutoff.toString() })
        assertTrue(store.blockedRows.isEmpty())
        assertTrue(store.firstAppRows.none { it.date < cutoff.toString() })
        assertTrue(store.firstAppRows.any { it.date == d(1).toString() })
    }

    // 7b
    @Test
    fun oldRowsArePrunedOnTheFastPathToo() = runBlocking {
        val store = FakePhoneBehaviourStore()
        fillComplete(store)
        val cutoff = today.minusDays(PhoneThresholds.DAY_ROW_RETENTION_DAYS.toLong())
        store.days += completeRow(cutoff.minusDays(30))
        val source = CountingSource(available(emptyList(), null))
        writer(store, source).writeMissingPastDays(now)
        assertEquals(0, source.calls)
        assertEquals(listOf(cutoff.toString()), store.pruneCutoffs)
        assertTrue(store.days.none { it.date == cutoff.minusDays(30).toString() })
    }

    // 8
    @Test
    fun hookIsCalledOnceWithTheRightRangeAndSessions() = runBlocking {
        val store = FakePhoneBehaviourStore()
        data class Call(val start: String, val end: String, val sessions: List<ForegroundSession>, val earliest: Long?, val now: Long)
        val calls = mutableListOf<Call>()
        val earliest = ms(d(3), 0, 0)
        val source = CountingSource(available((1..3).flatMap { usageOn(d(it)) }, earliest))
        writer(store, source, hook = { s, e, sess, er, n -> calls += Call(s, e, sess, er, n) }).writeMissingPastDays(now)
        val call = calls.single()
        assertEquals(d(14).toString(), call.start)
        assertEquals(d(1).toString(), call.end)
        assertEquals(earliest, call.earliest)
        assertEquals(now, call.now)
        assertEquals(3, call.sessions.size)
    }

    // 8b
    @Test
    fun aThrowingHookDoesNotFailThePassOrLoseTheRows() = runBlocking {
        val store = FakePhoneBehaviourStore()
        val source = CountingSource(available(usageOn(d(1)), ms(d(2), 0, 0)))
        val pass = writer(store, source, hook = { _, _, _, _, _ -> error("boom") }).writeMissingPastDays(now)
        assertTrue(pass is PhoneRollupPass.Written)
        assertEquals(listOf(d(2), d(1)).map { it.toString() }, store.days.map { it.date }.sorted())
        assertEquals(1, store.pruneCutoffs.size)
    }

    // 8c
    @Test
    fun cancellationFromTheHookPropagates() {
        val store = FakePhoneBehaviourStore()
        val source = CountingSource(available(usageOn(d(1)), ms(d(2), 0, 0)))
        var cancelled = false
        try {
            runBlocking {
                writer(store, source, hook = { _, _, _, _, _ -> throw CancellationException("stop") })
                    .writeMissingPastDays(now)
            }
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    // 8d
    @Test
    fun hookIsNotCalledWhenNothingIsMissing() = runBlocking {
        val store = FakePhoneBehaviourStore()
        fillComplete(store)
        var calls = 0
        writer(store, CountingSource(available(emptyList(), null)), hook = { _, _, _, _, _ -> calls++ })
            .writeMissingPastDays(now)
        assertEquals(0, calls)
    }

    // 9
    @Test
    fun excludedPackagesDoNotCountAsFirstApps() = runBlocking {
        val store = FakePhoneBehaviourStore()
        val source = CountingSource(available(usageOn(d(1), "com.launcher.x"), ms(d(2), 0, 0)))
        PhoneBehaviourRollupWriter(
            store, source, excludedPackages = setOf("com.launcher.x"), appNameResolver = { it },
            clock = Clock.fixed(Instant.ofEpochMilli(now), utc), zoneId = utc,
        ).writeMissingPastDays(now)
        assertTrue(store.firstAppRows.isEmpty())
        val yesterday = store.days.single { it.date == d(1).toString() }
        assertEquals(1, yesterday.unlockCount)
        assertEquals(1, yesterday.unlocksWithoutApp)
    }

    @Test
    fun resultDoesNotDependOnWriterTimeZoneDefaultOrOrderOfCandidates() = runBlocking {
        val store = FakePhoneBehaviourStore()
        val source = CountingSource(available((1..3).flatMap { usageOn(d(it)) }, ms(d(3), 0, 0)))
        val pass = writer(store, source).writeMissingPastDays(now) as PhoneRollupPass.Written
        assertEquals(pass.dates.sorted(), pass.dates)
        assertNull(store.days.firstOrNull { it.date == today.toString() })
        assertFalse(pass.dates.contains(today.toString()))
    }
}
