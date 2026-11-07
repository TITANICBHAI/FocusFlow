package com.tbtechs.focusflow.analytics.phone

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class BlockedAttemptSyncTest {
    private val utc = ZoneId.of("UTC")
    private val kolkata = ZoneId.of("Asia/Kolkata") // UTC+05:30, no DST

    private fun utcMs(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0, s: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, mi, s, 0, utc).toInstant().toEpochMilli()

    private fun log(vararg entries: Triple<String, String?, Long>): String =
        entries.joinToString(prefix = "[", postfix = "]", separator = ",") { (pkg, name, ts) ->
            val n = if (name == null) "" else ""","appName":"$name""""
            """{"pkg":"$pkg"$n,"timestamp":$ts}"""
        }

    private fun sync(store: FakePhoneBehaviourStore, zone: ZoneId, raw: String) =
        runBlocking { BlockedAttemptSync({ raw }, store, zone).sync() }

    @Test
    fun groupsByLocalDateAndPackage() {
        val store = FakePhoneBehaviourStore()
        val raw = log(
            Triple("com.a", "A", utcMs(2026, 11, 5, 10)),
            Triple("com.a", "A", utcMs(2026, 11, 5, 11)),
            Triple("com.b", "B", utcMs(2026, 11, 5, 11)),
            Triple("com.a", "A", utcMs(2026, 11, 6, 9)),
        )
        assertEquals(3, sync(store, utc, raw))
        val rows = store.blockedRows.associateBy { it.date to it.packageName }
        assertEquals(2, rows.getValue("2026-11-05" to "com.a").attempts)
        assertEquals(1, rows.getValue("2026-11-05" to "com.b").attempts)
        assertEquals(1, rows.getValue("2026-11-06" to "com.a").attempts)
    }

    @Test
    fun hourlyArrayFollowsTheLocalZone() {
        val store = FakePhoneBehaviourStore()
        // 18:29:59Z = 23:59:59 IST on the 5th; 18:30:00Z = 00:00:00 IST on the 6th; 20:00Z = 01:30 IST on the 6th.
        val raw = log(
            Triple("com.a", "A", utcMs(2026, 11, 5, 18, 29, 59)),
            Triple("com.a", "A", utcMs(2026, 11, 5, 18, 30, 0)),
            Triple("com.a", "A", utcMs(2026, 11, 5, 20, 0, 0)),
        )
        assertEquals(2, sync(store, kolkata, raw))
        val fifth = store.blockedRows.single { it.date == "2026-11-05" }
        val sixth = store.blockedRows.single { it.date == "2026-11-06" }
        assertEquals(1, fifth.attempts)
        assertEquals(List(23) { 0 } + 1, PhoneCodec.decodeInts(fifth.hourlyAttempts, 24))
        assertEquals(2, sixth.attempts)
        val hourly = PhoneCodec.decodeInts(sixth.hourlyAttempts, 24)
        assertEquals(1, hourly[0])
        assertEquals(1, hourly[1])
        assertEquals(2, hourly.sum())
    }

    @Test
    fun sameInstantsLandOnDifferentDatesInUtc() {
        val store = FakePhoneBehaviourStore()
        sync(store, utc, log(Triple("com.a", "A", utcMs(2026, 11, 5, 18, 30, 0))))
        assertEquals("2026-11-05", store.blockedRows.single().date)
    }

    @Test
    fun aSecondSyncWithFewerEntriesNeverLowersStoredCounts() {
        val store = FakePhoneBehaviourStore()
        val first = log(
            Triple("com.a", "A", utcMs(2026, 11, 5, 10)),
            Triple("com.a", "A", utcMs(2026, 11, 5, 11)),
            Triple("com.a", "A", utcMs(2026, 11, 5, 12)),
        )
        sync(store, utc, first)
        assertEquals(3, store.blockedRows.single().attempts)
        // The log cap dropped the two oldest entries.
        sync(store, utc, log(Triple("com.a", "A", utcMs(2026, 11, 5, 12))))
        assertEquals(3, store.blockedRows.single().attempts)
        assertEquals(1, store.blockedRows.size)
    }

    @Test
    fun aLaterSyncWithMoreEntriesRaisesTheCount() {
        val store = FakePhoneBehaviourStore()
        sync(store, utc, log(Triple("com.a", "A", utcMs(2026, 11, 5, 10))))
        sync(store, utc, log(Triple("com.a", "A", utcMs(2026, 11, 5, 10)), Triple("com.a", "A", utcMs(2026, 11, 5, 14))))
        val row = store.blockedRows.single()
        assertEquals(2, row.attempts)
        assertEquals(1, PhoneCodec.decodeInts(row.hourlyAttempts, 24)[14])
    }

    @Test
    fun twoAttemptsInTheSameMillisecondStillCountTwice() {
        val store = FakePhoneBehaviourStore()
        val t = utcMs(2026, 11, 5, 10)
        sync(store, utc, log(Triple("com.a", "A", t), Triple("com.a", "A", t)))
        assertEquals(2, store.blockedRows.single().attempts)
    }

    @Test
    fun timestampZeroOrNegativeIsIgnored() {
        val store = FakePhoneBehaviourStore()
        val raw = log(Triple("com.a", "A", 0L), Triple("com.a", "A", -5L), Triple("com.a", "A", utcMs(2026, 11, 5, 10)))
        assertEquals(1, sync(store, utc, raw))
        assertEquals(1, store.blockedRows.single().attempts)
    }

    @Test
    fun logWithOnlyInvalidTimestampsReturnsZeroWithoutTouchingTheStore() {
        val store = FakePhoneBehaviourStore()
        assertEquals(0, sync(store, utc, log(Triple("com.a", "A", 0L))))
        assertTrue(store.mergeCalls.isEmpty())
    }

    @Test
    fun emptyOrInvalidLogReturnsZeroWithoutTouchingTheStore() {
        val store = FakePhoneBehaviourStore()
        assertEquals(0, sync(store, utc, "[]"))
        assertEquals(0, sync(store, utc, ""))
        assertEquals(0, sync(store, utc, "garbage"))
        assertTrue(store.mergeCalls.isEmpty())
        assertTrue(store.blockedRows.isEmpty())
    }

    @Test
    fun appNameIsTheLastNonBlankNameElseThePackage() {
        val store = FakePhoneBehaviourStore()
        val raw = log(
            Triple("com.a", "Old name", utcMs(2026, 11, 5, 10)),
            Triple("com.a", "New name", utcMs(2026, 11, 5, 11)),
            Triple("com.a", null, utcMs(2026, 11, 5, 12)),
            Triple("com.b", null, utcMs(2026, 11, 5, 12)),
        )
        sync(store, utc, raw)
        val rows = store.blockedRows.associateBy { it.packageName }
        assertEquals("New name", rows.getValue("com.a").appName)
        assertEquals("com.b", rows.getValue("com.b").appName)
    }

    @Test
    fun todayIsWrittenToo() {
        val store = FakePhoneBehaviourStore()
        sync(store, utc, log(Triple("com.a", "A", utcMs(2026, 11, 7, 9))))
        assertEquals("2026-11-07", store.blockedRows.single().date)
    }

    @Test
    fun returnsTheNumberOfRowsHandedToTheStoreEvenIfTheyAreNotAllStored() {
        val store = FakePhoneBehaviourStore()
        val raw = log(Triple("com.a", "A", utcMs(2026, 11, 5, 10)), Triple("com.b", "B", utcMs(2026, 11, 5, 10)))
        assertEquals(2, sync(store, utc, raw))
        assertEquals(2, sync(store, utc, raw)) // nothing new to store, still two rows handed over
        assertEquals(2, store.blockedRows.size)
        assertEquals(listOf(2, 2), store.mergeCalls.map { it.size })
    }

    @Test
    fun theProviderIsCalledOncePerSync() {
        val store = FakePhoneBehaviourStore()
        var calls = 0
        val sync = BlockedAttemptSync({ calls++; "[]" }, store, utc)
        runBlocking { sync.sync(); sync.sync() }
        assertEquals(2, calls)
    }
}
