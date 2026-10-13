package com.tbtechs.focusflow.enforcement

import java.time.Instant
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundTaskGreyoutPolicyTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun epoch(value: String): Long = Instant.parse(value).toEpochMilli()

    @Test
    fun jsonScheduleUsesSharedWindowMathForActiveWeekdayWindows() {
        val json =
            """[{"pkg":"com.example.app","days":[${Calendar.MONDAY}],"startHour":9,"startMin":0,"endHour":10,"endMin":0}]"""

        assertTrue(
            ForegroundTaskGreyoutPolicy.isPackageBlocked(
                greyoutJson = json,
                packageName = "com.example.app",
                atMs = epoch("2026-10-05T09:30:00Z"),
                timeZone = utc,
            ),
        )
        assertFalse(
            ForegroundTaskGreyoutPolicy.isPackageBlocked(
                greyoutJson = json,
                packageName = "com.example.app",
                atMs = epoch("2026-10-05T10:00:00Z"),
                timeZone = utc,
            ),
        )
    }

    @Test
    fun overnightWindowUsesThePreviousConfiguredWeekdayAfterMidnight() {
        val json =
            """[{"pkg":"com.example.app","days":[${Calendar.MONDAY}],"startHour":22,"startMin":0,"endHour":2,"endMin":0}]"""

        assertTrue(
            ForegroundTaskGreyoutPolicy.isPackageBlocked(
                greyoutJson = json,
                packageName = "com.example.app",
                atMs = epoch("2026-10-06T01:00:00Z"),
                timeZone = utc,
            ),
        )
    }

    @Test
    fun packageMismatchAndMalformedJsonDoNotBlock() {
        val json =
            """[{"pkg":"com.example.app","days":[${Calendar.MONDAY}],"startHour":9,"startMin":0,"endHour":10,"endMin":0}]"""

        assertFalse(
            ForegroundTaskGreyoutPolicy.isPackageBlocked(
                greyoutJson = json,
                packageName = "com.other.app",
                atMs = epoch("2026-10-05T09:30:00Z"),
                timeZone = utc,
            ),
        )
        assertFalse(
            ForegroundTaskGreyoutPolicy.isPackageBlocked(
                greyoutJson = "not-json",
                packageName = "com.example.app",
                atMs = epoch("2026-10-05T09:30:00Z"),
                timeZone = utc,
            ),
        )
    }
}
