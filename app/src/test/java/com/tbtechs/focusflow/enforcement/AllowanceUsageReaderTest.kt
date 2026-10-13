package com.tbtechs.focusflow.enforcement

import com.tbtechs.focusflow.analytics.UsageEventRead
import com.tbtechs.focusflow.analytics.ForegroundEventType
import com.tbtechs.focusflow.analytics.ForegroundUsageEvent
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowanceUsageReaderTest {
    @Test
    fun availableEventsReconcileMeteredUsageWithAnInjectedClockAndSource() = runBlocking {
        val dayStartMs = Instant.parse("2026-10-08T00:00:00Z").toEpochMilli()
        val nowMs = dayStartMs + 5_000L
        val completedAtMs = dayStartMs + 6_000L
        val sourceWindows = mutableListOf<Pair<Long, Long>>()
        val reconciledPackages = mutableListOf<String>()
        val store = MemoryStore(null)
        val ledger = AllowanceLedger(store)
        val reader = AllowanceUsageReader(
            readEvents = { startMs, endMs ->
                sourceWindows.add(startMs to endMs)
                UsageEventRead.Available(
                    events = listOf(
                        ForegroundUsageEvent(
                            type = ForegroundEventType.ACTIVITY_RESUMED,
                            timestampMs = dayStartMs + 1_000L,
                            packageName = "pkg",
                        ),
                        ForegroundUsageEvent(
                            type = ForegroundEventType.ACTIVITY_PAUSED,
                            timestampMs = dayStartMs + 4_000L,
                            packageName = "pkg",
                        ),
                    ),
                    earliestEventAtMs = dayStartMs + 1_000L,
                )
            },
            packageName = "com.example.focusflow",
            ledger = ledger,
            zoneId = ZoneId.of("UTC"),
            onReconciled = { reconciledPackages.add(it) },
            clockNowMs = { completedAtMs },
        )

        val outcome = reader.readAndReconcile(
            targets = listOf(
                AllowanceUsageTarget("pkg", AllowanceLedger.MODE_TIME_BUDGET),
            ),
            queryStartMs = dayStartMs,
            nowMs = nowMs,
            activePackage = null,
        )

        assertTrue(outcome.read is UsageEventRead.Available)
        assertEquals(completedAtMs, outcome.completedAtMs)
        assertEquals(listOf(dayStartMs to nowMs), sourceWindows)
        assertEquals(listOf("pkg"), reconciledPackages)
        assertEquals(3_000L, ledger.usage("pkg").usedMs)
        assertEquals(3_000L, ledger.usage("pkg").confirmedUsedMs)
        assertEquals(0L, ledger.usage("pkg").estimatedExtraMs)
        assertEquals(1, store.writes)
    }

    @Test
    fun unknownRevokedTimedOutAndFailedReadsKeepLastKnownAllowance() = runBlocking {
        val original = """{"pkg":{"mode":"time_budget","date":"2026-10-08","usedMs":1200,"confirmedUsedMs":1000,"estimatedExtraMs":200,"confirmedAtMs":1000}}"""
        val store = MemoryStore(original)
        val ledger = AllowanceLedger(store)
        val failures: List<suspend (Long, Long) -> UsageEventRead> = listOf(
            { _, _ ->
                UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.EVENTS_UNAVAILABLE)
            },
            { _, _ -> throw SecurityException("usage access revoked") },
            { _, _ ->
                delay(50)
                UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.EVENTS_UNAVAILABLE)
            },
            { _, _ -> throw IllegalStateException("slow or unavailable source") },
        )

        failures.forEach { readEvents ->
            val reader = AllowanceUsageReader(
                readEvents = readEvents,
                packageName = "com.example.focusflow",
                ledger = ledger,
                zoneId = ZoneId.of("UTC"),
                onReconciled = {},
                readTimeoutMs = 5L,
                clockNowMs = { 2L },
            )

            val outcome = reader.readAndReconcile(
                targets = listOf(
                    AllowanceUsageTarget("pkg", AllowanceLedger.MODE_TIME_BUDGET),
                ),
                queryStartMs = 1L,
                nowMs = 2L,
                activePackage = null,
            )

            assertFalse(outcome.read is UsageEventRead.Available)
            assertEquals(1_200L, ledger.usage("pkg").usedMs)
            assertEquals(1_000L, ledger.usage("pkg").confirmedUsedMs)
            assertEquals(200L, ledger.usage("pkg").estimatedExtraMs)
            assertEquals(original, store.value)
            assertEquals(0, store.writes)
        }
    }

    private class MemoryStore(var value: String?) : AllowanceLedgerStore {
        var writes = 0

        override fun readUsageJson(): String? = value

        override fun writeUsageJson(value: String) {
            this.value = value
            writes += 1
        }
    }
}
