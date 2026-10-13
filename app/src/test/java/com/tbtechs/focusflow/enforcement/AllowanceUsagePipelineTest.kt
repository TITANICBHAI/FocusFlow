package com.tbtechs.focusflow.enforcement

import com.tbtechs.focusflow.analytics.ForegroundSession
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowanceUsagePipelineTest {
    @Test
    fun freshnessUsesPermissionAndTheDocumentedTimeWindows() {
        assertEquals(
            AllowanceUsageFreshness.UNAVAILABLE,
            AllowanceUsageFreshnessReducer.reduce(false, 99_000, 0, 100_000),
        )
        assertEquals(
            AllowanceUsageFreshness.FRESH,
            AllowanceUsageFreshnessReducer.reduce(true, 40_000, 0, 100_000),
        )
        assertEquals(
            AllowanceUsageFreshness.STALE,
            AllowanceUsageFreshnessReducer.reduce(true, 39_999, 0, 100_000),
        )
        assertEquals(
            AllowanceUsageFreshness.STALE,
            AllowanceUsageFreshnessReducer.reduce(true, 0, 100_000, 399_999),
        )
        assertEquals(
            AllowanceUsageFreshness.UNAVAILABLE,
            AllowanceUsageFreshnessReducer.reduce(true, 0, 100_000, 400_000),
        )
    }

    @Test
    fun measurementClipsTimeAndBridgesOnlyShortAllowedDialogsForOpens() {
        val sessions = listOf(
            session("target", 1_000, 3_000, isNewOpen = true),
            session("com.android.permissioncontroller", 3_000, 4_000, isNewOpen = true),
            session("target", 4_000, 8_000, isNewOpen = true),
            session("other", 8_000, 9_000, isNewOpen = true),
            session("target", 9_000, 12_000, isNewOpen = true),
        )

        val usage = AllowanceUsagePipeline.measure(
            target = AllowanceUsageTarget("target", AllowanceLedger.MODE_TIME_BUDGET),
            sessions = sessions,
            todayStartMs = 0,
            nowMs = 15_000,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(9_000L, usage.usedMs)
        assertEquals(2, usage.count)
    }

    @Test
    fun dialogBridgeDoesNotApplyAfterItsTimeWindow() {
        val sessions = listOf(
            session("target", 1_000, 2_000, isNewOpen = true),
            session("com.android.permissioncontroller", 2_000, 5_001, isNewOpen = true),
            session("target", 5_001, 7_000, isNewOpen = true),
        )

        val usage = AllowanceUsagePipeline.measure(
            target = AllowanceUsageTarget("target", AllowanceLedger.MODE_COUNT),
            sessions = sessions,
            todayStartMs = 0,
            nowMs = 10_000,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(2, usage.count)
    }

    @Test
    fun intervalMeasurementClipsToTheConfiguredWindow() {
        val sessions = listOf(
            session("target", 1_000, 3_000, isNewOpen = true),
            session("target", 4_000, 9_000, isNewOpen = false),
        )

        val usage = AllowanceUsagePipeline.measure(
            target = AllowanceUsageTarget(
                packageName = "target",
                mode = AllowanceLedger.MODE_INTERVAL,
                windowStartMs = 2_000,
                windowMs = 5_000,
            ),
            sessions = sessions,
            todayStartMs = 0,
            nowMs = 10_000,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(4_000L, usage.usedMs)
    }

    @Test
    fun aSessionStartedBeforeTodayIsNotCountedAsANewOpenToday() {
        val sessions = listOf(
            session("target", 1_000, 10_000, isNewOpen = true),
            session("target", 11_000, 12_000, isNewOpen = true),
        )

        val usage = AllowanceUsagePipeline.measure(
            target = AllowanceUsageTarget("target", AllowanceLedger.MODE_COUNT),
            sessions = sessions,
            todayStartMs = 10_000,
            nowMs = 15_000,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(1, usage.count)
    }

    private fun session(
        packageName: String,
        startedAtMs: Long,
        endedAtMs: Long,
        isNewOpen: Boolean,
    ) = ForegroundSession(
        packageName = packageName,
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        activityClassName = null,
        isNewOpen = isNewOpen,
    )
}
