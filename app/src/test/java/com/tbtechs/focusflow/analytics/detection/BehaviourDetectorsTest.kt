package com.tbtechs.focusflow.analytics.detection

import com.tbtechs.focusflow.data.local.dao.AppUsageRangeRow
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviourDetectorsTest {
    private val start = LocalDate.of(2026, 10, 1)

    private fun session(pkg: String, date: LocalDate, hour: Int, minutes: Long) = AppSessionEntity(
        packageName = pkg,
        appName = pkg,
        startedAt = date.atTime(hour, 30).toInstant(ZoneOffset.UTC).toEpochMilli(),
        endedAt = date.atTime(hour, 30).toInstant(ZoneOffset.UTC).toEpochMilli() + minutes * 60_000L,
        durationMs = minutes * 60_000L,
        localDate = date.toString(),
    )

    private fun usage(pkg: String, date: LocalDate, minutes: Long, category: String?, hourly: String = "") = AppUsageRangeRow(
        packageName = pkg,
        appName = pkg,
        category = category,
        date = date.toString(),
        foregroundMs = minutes * 60_000L,
        hourlyMs = hourly,
        launchCount = 1,
        lastUsedAt = 0L,
    )

    private fun lateNight(nights: Int, pkg: String = "feed") =
        (0 until nights).map { session(pkg, start.plusDays(it.toLong()), hour = 23, minutes = 20) }

    @Test
    fun lateNightUseNeedsFiveNightsAndCountsAnEarlyMorningSessionForTheEveningBefore() {
        val categories = mapOf<String, String?>("feed" to "social")
        val finding = detectLateNightUse(lateNight(6), categories, 14, ZoneOffset.UTC)
        assertNotNull(finding)
        assertEquals("LATE_NIGHT_USE", finding!!.detectionType)
        assertEquals("feed", finding.subjectPackage)
        assertTrue(finding.evidenceJson.contains("\"nights\":6"))

        assertNull(detectLateNightUse(lateNight(4), categories, 14, ZoneOffset.UTC))

        val afterMidnight = (0 until 5).map { session("feed", start.plusDays(it + 1L), hour = 1, minutes = 15) }
        val viaEarlyMorning = detectLateNightUse(afterMidnight, categories, 14, ZoneOffset.UTC)
        assertNotNull(viaEarlyMorning)
        assertTrue(viaEarlyMorning!!.evidenceJson.contains("\"nights\":5"))
    }

    @Test
    fun lateNightUseIgnoresDaytimeShortAndIneligibleUse() {
        val categories = mapOf<String, String?>("feed" to "social", "music" to "entertainment", "chat" to "communication")
        val daytime = (0 until 8).map { session("feed", start.plusDays(it.toLong()), hour = 14, minutes = 30) }
        assertNull(detectLateNightUse(daytime, categories, 14, ZoneOffset.UTC))

        val brief = (0 until 8).map { session("feed", start.plusDays(it.toLong()), hour = 23, minutes = 3) }
        assertNull(detectLateNightUse(brief, categories, 14, ZoneOffset.UTC))

        assertNull(detectLateNightUse(lateNight(8, "chat"), categories, 14, ZoneOffset.UTC))
        assertNull(detectLateNightUse(lateNight(8, "music"), categories, 14, ZoneOffset.UTC))
    }

    @Test
    fun heavyDailyUsePicksTheBiggestFeedAppAndSuggestsAThreeQuarterLimit() {
        val rows = (0 until 14).flatMap { day ->
            val date = start.plusDays(day.toLong())
            listOf(
                usage("video", date, 120, "entertainment"),
                usage("feed", date, 70, "social"),
                usage("browser", date, 240, "utility"),
            )
        }
        val finding = detectHeavyDailyUse(rows, 14)
        assertNotNull(finding)
        assertEquals("HEAVY_DAILY_USE", finding!!.detectionType)
        assertEquals("video", finding.subjectPackage)
        assertTrue(finding.evidenceJson.contains("\"avg_min\":120"))
        assertTrue(finding.evidenceJson.contains("\"suggested_min\":90"))
    }

    @Test
    fun heavyDailyUseNeedsAnHourADayOnTenDays() {
        val light = (0 until 14).map { usage("video", start.plusDays(it.toLong()), 40, "entertainment") }
        assertNull(detectHeavyDailyUse(light, 14))

        val fewDays = (0 until 6).map { usage("video", start.plusDays(it.toLong()), 200, "entertainment") }
        assertNull(detectHeavyDailyUse(fewDays, 14))
    }

    @Test
    fun heavyDailyUseRecordsTheHourItPeaksAtSoANudgeCanBeTimed() {
        val hourly = (0 until 24).joinToString(",") { if (it == 20) "7200000" else "0" }
        val rows = (0 until 14).map { usage("video", start.plusDays(it.toLong()), 120, "entertainment", hourly) }
        val finding = detectHeavyDailyUse(rows, 14)
        assertNotNull(finding)
        assertTrue(finding!!.evidenceJson.contains("\"peak_hour\":20"))
    }

    @Test
    fun weekendSpikeFindsAnAppUsedFarMoreOnWeekends() {
        val monday = LocalDate.of(2026, 10, 5)
        val rows = (0 until 28).flatMap { day ->
            val date = monday.plusDays(day.toLong())
            val weekend = date.dayOfWeek.value >= 6
            listOf(
                usage("video", date, if (weekend) 120 else 40, "entertainment"),
                usage("steady", date, 50, "social"),
            )
        }
        val finding = detectWeekendSpike(rows, 28)
        assertNotNull(finding)
        assertEquals("WEEKEND_SPIKE", finding!!.detectionType)
        assertEquals("video", finding.subjectPackage)
        assertTrue(finding.evidenceJson.contains("\"weekend_avg_min\":120"))
        assertTrue(finding.evidenceJson.contains("\"weekday_avg_min\":40"))
        assertTrue(finding.evidenceJson.contains("\"suggested_min\":50"))
    }

    @Test
    fun weekendSpikeNeedsAClearGapAndEnoughWeekendDays() {
        val monday = LocalDate.of(2026, 10, 5)
        val flat = (0 until 28).map { usage("video", monday.plusDays(it.toLong()), 60, "entertainment") }
        assertNull(detectWeekendSpike(flat, 28))

        val tooShort = (0 until 10).map { day ->
            val date = monday.plusDays(day.toLong())
            usage("video", date, if (date.dayOfWeek.value >= 6) 120 else 40, "entertainment")
        }
        assertNull(detectWeekendSpike(tooShort, 28))

        val tools = (0 until 28).map { day ->
            val date = monday.plusDays(day.toLong())
            usage("video", date, if (date.dayOfWeek.value >= 6) 120 else 40, "utility")
        }
        assertNull(detectWeekendSpike(tools, 28))
    }
}
