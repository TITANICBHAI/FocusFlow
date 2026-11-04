package com.tbtechs.focusflow.analytics.detection

import com.tbtechs.focusflow.analytics.TemptationEntry
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickupDetectorsTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val zone = ZoneOffset.UTC

    private fun atMs(date: LocalDate, hour: Int, minute: Int = 0, second: Int = 0): Long =
        date.atTime(hour, minute, second).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun session(pkg: String, startMs: Long, seconds: Long) = AppSessionEntity(
        packageName = pkg,
        appName = pkg,
        startedAt = startMs,
        endedAt = startMs + seconds * 1_000L,
        durationMs = seconds * 1_000L,
        localDate = java.time.Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate().toString(),
    )

    private val categories = mapOf<String, String?>(
        "feed" to "social",
        "video" to "entertainment",
        "mail" to "communication",
    )

    /** Six pickups a day, an hour apart; [entryFor] decides the app of each one. */
    private fun dailyPickups(days: Int, seconds: Long = 20, entryFor: (Int) -> String): List<AppSessionEntity> =
        (0 until days).flatMap { day ->
            (0 until 6).map { k -> session(entryFor(k), atMs(start.plusDays(day.toLong()), 8 + k), seconds) }
        }

    @Test
    fun entryAppIsTheAppOpenedFirstMostOften() {
        val sessions = dailyPickups(10) { k -> if (k == 0) "mail" else "feed" }
        val finding = detectEntryApp(sessions, categories, 14, zone)
        assertNotNull(finding)
        assertEquals("ENTRY_APP", finding!!.detectionType)
        assertEquals("feed", finding.subjectPackage)
        assertTrue(finding.evidenceJson.contains("\"share_pct\":83"))
        assertTrue(finding.evidenceJson.contains("\"blockable\":true"))
    }

    @Test
    fun entryAppIsNotBlockableWhenItIsAMessagingApp() {
        val sessions = dailyPickups(10) { k -> if (k == 0) "feed" else "mail" }
        val finding = detectEntryApp(sessions, categories, 14, zone)
        assertNotNull(finding)
        assertEquals("mail", finding!!.subjectPackage)
        assertTrue(finding.evidenceJson.contains("\"blockable\":false"))
    }

    @Test
    fun entryAppNeedsEnoughPickupsDaysAndAClearFavourite() {
        assertNull(detectEntryApp(dailyPickups(4) { "feed" }, categories, 14, zone))
        val spread = detectEntryApp(
            dailyPickups(10) { k -> listOf("feed", "video", "mail", "a", "b", "c")[k] },
            categories,
            14,
            zone,
        )
        assertNull(spread)
    }

    @Test
    fun sessionsCloserThanTwoMinutesBelongToTheSamePickup() {
        val day = start
        val sessions = listOf(
            session("feed", atMs(day, 9, 0, 0), 30),
            session("video", atMs(day, 9, 1, 0), 30),
            session("mail", atMs(day, 11, 0, 0), 30),
        )
        val pickups = buildPickups(sessions)
        assertEquals(2, pickups.size)
        assertEquals("feed", pickups[0].entryPackage)
        assertEquals(60_000L, pickups[0].foregroundMs)
    }

    @Test
    fun quickCheckHabitNeedsManyBriefVisitsEveryDay() {
        val manyBrief = (0 until 14).flatMap { day ->
            (0 until 45).map { k -> session("feed", atMs(start.plusDays(day.toLong()), 6) + k * 600_000L, 15) }
        }
        val finding = detectQuickCheckHabit(manyBrief, 14, zone)
        assertNotNull(finding)
        assertEquals("QUICK_CHECK_HABIT", finding!!.detectionType)
        assertTrue(finding.evidenceJson.contains("\"checks_per_day\":45"))

        val longVisits = (0 until 14).flatMap { day ->
            (0 until 45).map { k -> session("feed", atMs(start.plusDays(day.toLong()), 6) + k * 600_000L, 120) }
        }
        assertNull(detectQuickCheckHabit(longVisits, 14, zone))
        assertNull(detectQuickCheckHabit(dailyPickups(14) { "feed" }, 14, zone))
    }

    @Test
    fun appHoppingFindsAFeedThatIsAlwaysFollowedByAnother() {
        val sessions = (0 until 6).flatMap { day ->
            (0 until 3).flatMap { k ->
                val t = atMs(start.plusDays(day.toLong()), 9 + k * 3)
                listOf(session("feed", t, 60), session("video", t + 65_000L, 60))
            }
        }
        val finding = detectAppHopping(sessions, categories, 14, zone)
        assertNotNull(finding)
        assertEquals("APP_HOPPING", finding!!.detectionType)
        assertEquals("feed", finding.subjectPackage)
        assertTrue(finding.evidenceJson.contains("\"to_package\":\"video\""))

        val tools = mapOf<String, String?>("feed" to "utility", "video" to "utility")
        assertNull(detectAppHopping(sessions, tools, 14, zone))
    }

    @Test
    fun blockedAttemptPeakFindsTheHourAttemptsClusterAround() {
        val now = atMs(start.plusDays(10), 12)
        val night = (0 until 6).flatMap { day ->
            listOf(23, 23).map { _ ->
                TemptationEntry("feed", "Feed", atMs(start.plusDays(day.toLong()), 23, 15))
            }
        }
        val afternoon = (0 until 4).map { day ->
            TemptationEntry("video", "Video", atMs(start.plusDays(day.toLong()), 14))
        }
        val finding = detectBlockedAttemptPeak(night + afternoon, now, zone)
        assertNotNull(finding)
        assertEquals("TEMPTATION_PEAK", finding!!.detectionType)
        assertEquals("feed", finding.subjectPackage)
        assertTrue(finding.evidenceJson.contains("\"peak_hour\":23"))

        assertNull(detectBlockedAttemptPeak(night.take(6), now, zone))
        assertNull(detectBlockedAttemptPeak(night + afternoon, atMs(start.plusDays(80), 12), zone))
    }

    @Test
    fun entryAppRecordsTheHourItIsReachedForMostOftenWhenItStandsOut() {
        val evening = (0 until 10).flatMap { day ->
            (0 until 5).map { k -> session("feed", atMs(start.plusDays(day.toLong()), 21) + k * 600_000L, 20) }
        }
        val finding = detectEntryApp(evening, categories, 14, zone)
        assertNotNull(finding)
        assertTrue(finding!!.evidenceJson.contains("\"peak_hour\":21"))

        val spreadOut = detectEntryApp(dailyPickups(10) { "feed" }, categories, 14, zone)
        assertNotNull(spreadOut)
        assertTrue(!spreadOut!!.evidenceJson.contains("peak_hour"))
    }
}
