package com.tbtechs.focusflow.enforcement

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowanceLedgerTest {
    @Test
    fun legacyJsonIsReadAsConfirmedUsageWithoutBeingRewritten() {
        val legacy = """
            {
              "com.example.timer": {
                "mode": "time_budget",
                "date": "2026-10-08",
                "usedMs": 1200000
              },
              "com.example.counter": {
                "mode": "count",
                "date": "2026-10-08",
                "count": 2
              }
            }
        """.trimIndent()
        val store = MemoryAllowanceStore(legacy)
        val ledger = AllowanceLedger(store)

        val timer = ledger.usage("com.example.timer")
        assertEquals(1_200_000L, timer.usedMs)
        assertEquals(1_200_000L, timer.confirmedUsedMs)
        assertEquals(0L, timer.estimatedExtraMs)
        val counter = ledger.usage("com.example.counter")
        assertEquals(2, counter.count)
        assertEquals(2, counter.confirmedCount)
        assertEquals(0, counter.estimatedExtraOpens)
        assertEquals(legacy, ledger.snapshot().usageJson)
        assertEquals(0, store.writes)
    }

    @Test
    fun effectiveUsageIsConfirmedPlusNonNegativeEstimate() {
        val ledger = ledger(
            """{"pkg":{"mode":"time_budget","date":"2026-10-08","usedMs":1500,"confirmedUsedMs":1000,"estimatedExtraMs":500,"count":5,"confirmedCount":3,"estimatedExtraOpens":2}}""",
        )

        val usage = ledger.usage("pkg")
        assertEquals(1_500L, usage.usedMs)
        assertEquals(5, usage.count)
        assertEquals(1_000L + usage.estimatedExtraMs, usage.usedMs)
        assertEquals(3 + usage.estimatedExtraOpens, usage.count)
    }

    @Test
    fun negativePersistedValuesAreClampedBeforeEnforcement() {
        val ledger = ledger(
            """{"pkg":{"mode":"time_budget","usedMs":-5,"confirmedUsedMs":-10,"estimatedExtraMs":-1,"count":-2,"confirmedCount":-3,"estimatedExtraOpens":-4}}""",
        )

        val usage = ledger.usage("pkg")
        assertEquals(0L, usage.usedMs)
        assertEquals(0L, usage.confirmedUsedMs)
        assertEquals(0L, usage.estimatedExtraMs)
        assertEquals(0, usage.count)
        assertEquals(0, usage.confirmedCount)
        assertEquals(0, usage.estimatedExtraOpens)
    }

    @Test
    fun confirmedValuesOnlyRiseWithinTheSameDayOrWindow() {
        val store = MemoryAllowanceStore(
            """{"time":{"mode":"time_budget","date":"2026-10-08","usedMs":1250,"confirmedUsedMs":1000,"estimatedExtraMs":250},"count":{"mode":"count","date":"2026-10-08","count":3,"confirmedCount":2,"estimatedExtraOpens":1}}""",
        )
        val ledger = AllowanceLedger(store)

        assertFalse(
            ledger.raiseTimeUsage(
                "2026-10-08",
                listOf(AllowanceTimeUpdate("time", AllowanceLedger.MODE_TIME_BUDGET, 1_200)),
                10_000,
            ),
        )
        assertFalse(
            ledger.raiseCountUsage(
                "2026-10-08",
                listOf(AllowanceCountUpdate("count", 3)),
                10_000,
            ),
        )
        assertTrue(
            ledger.raiseTimeUsage(
                "2026-10-08",
                listOf(AllowanceTimeUpdate("time", AllowanceLedger.MODE_TIME_BUDGET, 2_000)),
                11_000,
            ),
        )
        assertTrue(
            ledger.raiseCountUsage(
                "2026-10-08",
                listOf(AllowanceCountUpdate("count", 4)),
                11_000,
            ),
        )

        val time = ledger.usage("time")
        assertEquals(2_000L, time.confirmedUsedMs)
        assertEquals(0L, time.estimatedExtraMs)
        assertEquals(2_000L, time.usedMs)
        val count = ledger.usage("count")
        assertEquals(4, count.confirmedCount)
        assertEquals(0, count.estimatedExtraOpens)
        assertEquals(4, count.count)
        val persisted = JSONObject(store.value!!)
        assertEquals(
            2_000L,
            persisted.getJSONObject("time").getLong("usedMs"),
        )
        assertEquals(4, persisted.getJSONObject("count").getInt("count"))
    }

    @Test
    fun readMathPreservesDailyAndStrictIntervalBoundaries() {
        val start = 1_000_000L
        val ledger = ledger(
            """{"count":{"mode":"count","date":"2026-10-08","count":2},"time":{"mode":"time_budget","date":"2026-10-08","usedMs":4000},"interval":{"mode":"interval","date":"2026-10-08","windowStartMs":$start,"usedMs":4000},"yesterday":{"mode":"time_budget","date":"2026-10-07","usedMs":5000}}""",
        )

        val count = ledger.readAllowance("count", "count", "2026-10-08", start, 3)
        assertEquals(2, count.count)
        assertEquals(1L, count.remaining)
        assertFalse(count.exhausted)
        val time = ledger.readAllowance("time", "time_budget", "2026-10-08", start, 5_000)
        assertEquals(1_000L, time.remaining)
        val yesterday = ledger.readAllowance(
            "yesterday",
            "time_budget",
            "2026-10-08",
            start,
            5_000,
        )
        assertEquals(0L, yesterday.usedMs)
        assertEquals(5_000L, yesterday.remaining)
        val intervalAtBoundary = ledger.readAllowance(
            "interval",
            "interval",
            "2026-10-08",
            start + 10_000,
            5_000,
            10_000,
        )
        assertFalse(intervalAtBoundary.windowExpired)
        assertEquals(1_000L, intervalAtBoundary.remaining)
        val intervalAfterBoundary = ledger.readAllowance(
            "interval",
            "interval",
            "2026-10-08",
            start + 10_001,
            5_000,
            10_000,
        )
        assertTrue(intervalAfterBoundary.windowExpired)
        assertEquals(5_000L, intervalAfterBoundary.remaining)
        assertFalse(intervalAfterBoundary.exhausted)
    }

    @Test
    fun timedMidnightRecoveryNeverReducesConfirmedUsageForToday() {
        val ledger = ledger(
            """{"pkg":{"mode":"time_budget","date":"2026-10-08","usedMs":5000,"confirmedUsedMs":5000}}""",
        )

        assertTrue(
            ledger.accumulateTimedUsage(
                packageName = "pkg",
                mode = AllowanceLedger.MODE_TIME_BUDGET,
                today = "2026-10-08",
                openedAtMs = 5_000,
                nowMs = 10_000,
                midnightMs = 7_000,
                limitMs = 20_000,
                windowMs = 0,
            ),
        )
        assertEquals(5_000L, ledger.usage("pkg").usedMs)
        assertEquals(5_000L, ledger.usage("pkg").confirmedUsedMs)
    }

    @Test
    fun openRecordingResetsOnlyExpiredPeriodsAndKeepsCacheAuthoritative() {
        val store = MemoryAllowanceStore(
            """{"count":{"mode":"count","date":"2026-10-07","count":6},"time":{"mode":"time_budget","date":"2026-10-07","usedMs":900},"interval":{"mode":"interval","date":"2026-10-08","windowStartMs":1000,"usedMs":700}}""",
        )
        val ledger = AllowanceLedger(store)

        assertEquals(0L, ledger.recordOpen("count", "count", "2026-10-08", 10_000, 4))
        assertEquals(1, ledger.usage("count").count)
        assertEquals(15_000L, ledger.recordOpen("time", "time_budget", "2026-10-08", 10_000, 5_000))
        assertEquals(0L, ledger.usage("time").usedMs)
        assertEquals(
            20_000L,
            ledger.recordOpen("interval", "interval", "2026-10-08", 15_000, 5_000, 10_000),
        )
        assertEquals(15_000L, ledger.usage("interval").windowStartMs)
        assertEquals(0L, ledger.usage("interval").usedMs)
        assertEquals(1, store.reads)
        assertTrue(store.writes > 0)
    }

    @Test
    fun resetRemovesOnePackageOrAllPackagesThroughTheLedger() {
        val ledger = ledger("""{"a":{"count":1},"b":{"count":2}}""")

        ledger.reset("a")
        assertEquals(null, ledger.usage("a").mode)
        assertEquals(2, ledger.usage("b").count)
        ledger.reset()
        assertTrue(ledger.snapshot().usageByPackage.isEmpty())
    }

    private fun ledger(json: String): AllowanceLedger =
        AllowanceLedger(MemoryAllowanceStore(json))

    private class MemoryAllowanceStore(
        var value: String?,
    ) : AllowanceLedgerStore {
        var reads = 0
        var writes = 0

        override fun readUsageJson(): String? {
            reads++
            return value
        }

        override fun writeUsageJson(value: String) {
            writes++
            this.value = value
        }
    }
}
