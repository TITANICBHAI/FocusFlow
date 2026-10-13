package com.tbtechs.focusflow.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmCapabilitySnapshotTest {
    @Test
    fun diagnosticStringContainsEveryV14CapabilityField() {
        val snapshot = AlarmCapabilitySnapshot(
            postNotificationsGranted = true,
            channelImportance = 4,
            channelIsBlocked = false,
            canUseFullScreenIntent = false,
            canDrawOverlays = true,
            canScheduleExactAlarms = false,
            batteryOptimizationExempt = null,
            targetSdk = 35,
            deviceInteractive = false,
            keyguardLocked = true,
            alarmTierUsed = "DEFERRED_EXACT_UNAVAILABLE",
        )

        assertEquals(
            listOf(
                "postNotificationsGranted=true",
                "channelImportance=4",
                "channelIsBlocked=false",
                "canUseFullScreenIntent=false",
                "canDrawOverlays=true",
                "canScheduleExactAlarms=false",
                "batteryOptimizationExempt=unknown",
                "targetSdk=35",
                "deviceInteractive=false",
                "keyguardLocked=true",
                "alarmTierUsed=DEFERRED_EXACT_UNAVAILABLE",
            ),
            snapshot.toDiagnosticString().split(" "),
        )
    }
}