package com.tbtechs.focusflow.analytics.detection

import com.tbtechs.focusflow.data.local.dao.AppUsageRangeRow
import com.tbtechs.focusflow.data.local.dao.FirstSessionRow
import com.tbtechs.focusflow.data.local.dao.SessionStatRow
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.DayRatingEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManipulationDetectorsTest {

    @Test
    fun variableRewardLoopRequiresHighFrequencyShortSessionsAndChoosesHighestCv() {
        val stats = listOf(
            *(1..10).map {
                SessionStatRow("steady", "2026-01-$it", 5, 120_000.0, 120_000, 120_000, 600_000)
            }.toTypedArray(),
            *(1..10).map {
                SessionStatRow("variable", "2026-01-$it", 5, 120_000.0, 60_000, 1_200_000, 600_000)
            }.toTypedArray(),
        )
        val raw = mapOf(
            "steady" to (1..50).map { appSession("steady", it.toLong(), 120_000) },
            "variable" to (1..50).map {
                appSession("variable", it.toLong(), if (it % 2 == 0) 60_000 else 1_200_000)
            },
        )

        val finding = detectVariableRewardLoop(
            dailyStats = stats,
            rawSessionsByPackage = raw,
            appNames = mapOf("steady" to "Steady", "variable" to "Variable"),
        )

        assertNotNull(finding)
        assertEquals("variable", finding!!.subjectPackage)
    }

    @Test
    fun infiniteSessionDesignExcludesUtilityAndRequiresLongVariance() {
        val stats = (1..14).map {
            SessionStatRow("video", "2026-01-$it", 1, 600_000.0, 60_000, 6_000_000, 600_000)
        }
        val raw = mapOf(
            "video" to buildList {
                repeat(13) { add(appSession("video", it.toLong(), 60_000)) }
                add(appSession("video", 99, 6_000_000))
            },
        )

        val finding = detectInfiniteSessionDesign(
            dailyStats = stats,
            rawSessionsByPackage = raw,
            appNames = mapOf("video" to "Video"),
            categories = mapOf("video" to "entertainment"),
        )

        assertNotNull(finding)
        assertEquals("video", finding!!.subjectPackage)
        assertNull(
            detectInfiniteSessionDesign(
                stats,
                raw,
                mapOf("video" to "Video"),
                mapOf("video" to "utility"),
            ),
        )
    }

    @Test
    fun inflatedSixHourSessionFindingDisappearsWhenPipelineSplitsObservedForeground() {
        val firstDay = LocalDate.of(2026, 7, 1)
        val sixHourStart = firstDay.atTime(23, 50).toInstant(ZoneOffset.UTC)
        val oldSessions = buildList {
            add(datedSession("video", sixHourStart, 6 * 60 * 60_000L))
            (1..13).forEach { dayOffset ->
                add(
                    datedSession(
                        "video",
                        firstDay.plusDays(dayOffset.toLong()).atTime(9, 0)
                            .toInstant(ZoneOffset.UTC),
                        60_000L,
                    ),
                )
            }
        }
        val pipelineSessions = buildList {
            repeat(12) { part ->
                add(
                    datedSession(
                        "video",
                        sixHourStart.plusSeconds(part * 30 * 60L),
                        30 * 60_000L,
                    ),
                )
            }
            (1..13).forEach { dayOffset ->
                add(
                    datedSession(
                        "video",
                        firstDay.plusDays(dayOffset.toLong()).atTime(8, 0)
                            .toInstant(ZoneOffset.UTC),
                        60_000L,
                    ),
                )
            }
        }
        val categories = mapOf("video" to "entertainment")
        val appNames = mapOf("video" to "Video")

        val oldFinding = detectInfiniteSessionDesign(
            dailyStats = sessionStatsFrom(oldSessions),
            rawSessionsByPackage = mapOf("video" to oldSessions),
            appNames = appNames,
            categories = categories,
        )
        val pipelineFinding = detectInfiniteSessionDesign(
            dailyStats = sessionStatsFrom(pipelineSessions),
            rawSessionsByPackage = mapOf("video" to pipelineSessions),
            appNames = appNames,
            categories = categories,
        )

        assertNotNull("The old six-hour row reproduces the inflated finding", oldFinding)
        assertEquals(373 * 60_000L, oldSessions.sumOf { it.durationMs })
        assertEquals(oldSessions.sumOf { it.durationMs }, pipelineSessions.sumOf { it.durationMs })
        assertEquals(14, sessionStatsFrom(pipelineSessions).size)
        assertNull("Observed shorter sessions no longer meet the infinite-session rule", pipelineFinding)
    }

    @Test
    fun midnightSessionIsAttributedOnceInMorningVariableAndInfiniteDetectors() {
        val startDate = LocalDate.of(2026, 8, 1)
        val nextDate = startDate.plusDays(1)
        val midnightStart = startDate.atTime(23, 50).toInstant(ZoneOffset.UTC)
        val midnightEndMs = midnightStart.toEpochMilli() + 30 * 60_000L
        val morningSessions = buildList {
            add(datedSession("social", midnightStart, 30 * 60_000L))
            (1..6).forEach { dayOffset ->
                val date = startDate.plusDays(dayOffset.toLong())
                add(
                    datedSession(
                        if (dayOffset == 1) "calendar" else "social",
                        date.atTime(7, 0).toInstant(ZoneOffset.UTC),
                        60_000L,
                    ),
                )
            }
        }
        val firstSessions = morningSessions.groupBy { it.localDate }
            .map { (date, rows) ->
                val first = rows.minBy { it.startedAt }
                FirstSessionRow(date, first.packageName, first.appName, first.startedAt)
            }
            .sortedBy { it.localDate }
        val morning = detectMorningHijack(
            firstSessions,
            mapOf("social" to "social", "calendar" to "utility"),
        )

        val variableSessions = buildList {
            (0 until 10).forEach { dayOffset ->
                val date = startDate.plusDays(dayOffset.toLong())
                repeat(5) { sessionIndex ->
                    val isMidnightSession = dayOffset == 0 && sessionIndex == 4
                    add(
                        datedSession(
                            "variable",
                            if (isMidnightSession) {
                                midnightStart
                            } else {
                                date.atTime(7, sessionIndex * 10).toInstant(ZoneOffset.UTC)
                            },
                            if (isMidnightSession) 30 * 60_000L else 60_000L,
                        ),
                    )
                }
            }
        }
        val variable = detectVariableRewardLoop(
            dailyStats = sessionStatsFrom(variableSessions),
            rawSessionsByPackage = mapOf("variable" to variableSessions),
            appNames = mapOf("variable" to "Variable"),
        )

        val infiniteSessions = buildList {
            (1..13).forEach { dayOffset ->
                add(
                    datedSession(
                        "video",
                        startDate.plusDays(dayOffset.toLong()).atTime(8, 0)
                            .toInstant(ZoneOffset.UTC),
                        60_000L,
                    ),
                )
            }
            (1..10).forEach { dayOffset ->
                add(
                    datedSession(
                        "video",
                        startDate.plusDays(dayOffset.toLong()).atTime(10, 0)
                            .toInstant(ZoneOffset.UTC),
                        60_000L,
                    ),
                )
            }
            add(datedSession("video", midnightStart, 6 * 60 * 60_000L))
        }
        val infinite = detectInfiniteSessionDesign(
            dailyStats = sessionStatsFrom(infiniteSessions),
            rawSessionsByPackage = mapOf("video" to infiniteSessions),
            appNames = mapOf("video" to "Video"),
            categories = mapOf("video" to "entertainment"),
        )

        assertTrue(midnightEndMs > nextDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        assertEquals(startDate.toString(), morningSessions.first().localDate)
        assertEquals(7, firstSessions.size)
        assertNotNull(morning)
        assertEquals(6, morning!!.qualifyingDates.size)
        assertFalse(nextDate.toString() in morning.qualifyingDates)
        assertEquals(50, variableSessions.size)
        assertEquals(50, sessionStatsFrom(variableSessions).sumOf { it.sessionCount })
        assertNotNull(variable)
        assertTrue(variable!!.evidenceLine.contains("50 sessions"))
        assertEquals(24, infiniteSessions.size)
        assertEquals(24, sessionStatsFrom(infiniteSessions).sumOf { it.sessionCount })
        assertEquals(
            startDate.toString(),
            infiniteSessions.single { it.durationMs == 6 * 60 * 60_000L }.localDate,
        )
        assertNotNull(infinite)
        assertTrue(infinite!!.evidenceLine.contains("24 sessions"))
    }

    @Test
    fun morningHijackRequiresSevenDaysAndReturnsQualifyingDates() {
        val firstSessions = (1..7).map { day ->
            FirstSessionRow(
                localDate = "2026-02-${"%02d".format(day)}",
                packageName = if (day <= 5) "social" else "calendar",
                appName = if (day <= 5) "Social" else "Calendar",
                startedAt = day.toLong(),
            )
        }

        val result = detectMorningHijack(
            firstSessions = firstSessions,
            categories = mapOf("social" to "social", "calendar" to "utility"),
        )

        assertNotNull(result)
        assertEquals("social", result!!.packageName)
        assertEquals(5, result.qualifyingDates.size)
        assertEquals("MORNING_HIJACK", result.finding.detectionType)
    }

    @Test
    fun escalatingCaptureRequiresFourPopulatedIncreasingWeeks() {
        val today = LocalDate.of(2026, 3, 28)
        val rows = (1..28).map { daysAgo ->
            val week = 4 - ((daysAgo - 1) / 7)
            usageRow(
                packageName = "capture",
                date = today.minusDays(daysAgo.toLong()),
                foregroundMs = week * 10L * 60_000L,
            )
        }

        val finding = detectEscalatingCapture(rows, today)

        assertNotNull(finding)
        assertEquals("capture", finding!!.subjectPackage)
        assertTrue(finding.evidenceJson.contains("\"growth_pct\":300"))
        assertNull(computeWeeklyAverages(rows.drop(7), today)["capture"])
    }

    @Test
    fun weeklyAveragesUseDMinusSevenThroughYesterdayAndIgnoreToday() {
        val today = LocalDate.of(2026, 3, 28)
        val minutesByWeek = mapOf(1 to 10, 2 to 20, 3 to 30, 4 to 40)
        val rows = (1..28).map { daysAgo ->
            val week = when (daysAgo) {
                in 1..7 -> 4
                in 8..14 -> 3
                in 15..21 -> 2
                else -> 1
            }
            usageRow(
                packageName = "capture",
                date = today.minusDays(daysAgo.toLong()),
                foregroundMs = minutesByWeek.getValue(week) * 60_000L,
            )
        } + usageRow(
            packageName = "capture",
            date = today,
            foregroundMs = 10_000 * 60_000L,
        )

        val weeks = computeWeeklyAverages(rows, today).getValue("capture")

        assertEquals(10 * 60_000.0, weeks.week1, 0.0)
        assertEquals(20 * 60_000.0, weeks.week2, 0.0)
        assertEquals(30 * 60_000.0, weeks.week3, 0.0)
        assertEquals(40 * 60_000.0, weeks.week4, 0.0)
    }

    @Test
    fun escalatingTrendCausedOnlyByTodaysPartialUsageIsNotDetected() {
        val today = LocalDate.of(2026, 3, 28)
        val rows = (1..28).map { daysAgo ->
            val minutes = when (daysAgo) {
                in 1..7 -> 30
                in 8..14 -> 30
                in 15..21 -> 20
                else -> 10
            }
            usageRow(
                packageName = "capture",
                date = today.minusDays(daysAgo.toLong()),
                foregroundMs = minutes * 60_000L,
            )
        } + usageRow(
            packageName = "capture",
            date = today,
            foregroundMs = 1_000 * 60_000L,
        )

        val weeks = computeWeeklyAverages(rows, today).getValue("capture")

        assertEquals(30 * 60_000.0, weeks.week4, 0.0)
        assertNull(detectEscalatingCapture(rows, today))
    }

    @Test
    fun substitutionFindsOpposingChangesWithStableCombinedTimeAndChoosesStrongestPair() {
        val today = LocalDate.of(2026, 5, 28)
        val rows = buildList {
            addAll(weeklyRows("down-strong", today, listOf(100, 80, 65, 50)))
            addAll(weeklyRows("up-strong", today, listOf(100, 120, 140, 150)))
            addAll(weeklyRows("down-mild", today, listOf(10, 9, 8, 7)))
            addAll(weeklyRows("up-mild", today, listOf(10, 11, 12, 13)))
        }

        val finding = detectSubstitution(rows, today)

        assertNotNull(finding)
        assertEquals("SUBSTITUTION", finding!!.detectionType)
        assertNull(finding.subjectPackage)
        assertTrue(finding.evidenceJson.contains("\"down_app\":\"down-strong\""))
        assertTrue(finding.evidenceJson.contains("\"up_app\":\"up-strong\""))
    }

    @Test
    fun substitutionRejectsWeakTrendsAndPairsWhoseCombinedTimeShiftsTooMuch() {
        val today = LocalDate.of(2026, 5, 28)
        val weakRows = buildList {
            addAll(weeklyRows("down", today, listOf(100, 90, 80, 71)))
            addAll(weeklyRows("up", today, listOf(100, 110, 120, 129)))
        }
        val largeShiftRows = buildList {
            addAll(weeklyRows("down", today, listOf(100, 90, 80, 70)))
            addAll(weeklyRows("up", today, listOf(10, 11, 12, 13)))
        }

        assertNull(detectSubstitution(weakRows, today))
        assertNull(detectSubstitution(largeShiftRows, today))
    }

    @Test
    fun allowanceSuggestionUsesCompletedAllDayUsageForManualLimitCalculation() {
        val today = LocalDate.of(2026, 6, 28)
        val ratedDays = (1..8).map { today.minusDays(it.toLong()) }
        val ratings = ratedDays.mapIndexed { index, date ->
            rating(date, if (index < 5) 8 else 3)
        }
        val rows = buildList {
            ratedDays.forEachIndexed { index, date ->
                val highDay = index < 5
                add(
                    usageRow(
                        packageName = "small-gap",
                        date = date,
                        foregroundMs = (if (highDay) 20 else 40) * 60_000L,
                        category = "social",
                        appName = "Small Gap",
                    ),
                )
                add(
                    usageRow(
                        packageName = "best-gap",
                        date = date,
                        foregroundMs = (if (highDay) 15 else 55) * 60_000L,
                        category = "entertainment",
                        appName = "Best Gap",
                    ),
                )
                add(
                    usageRow(
                        packageName = "utility",
                        date = date,
                        foregroundMs = (if (highDay) 1 else 90) * 60_000L,
                        category = "utility",
                    ),
                )
            }
        }

        val finding = detectAllowanceSuggestion(rows, ratings)

        assertNotNull(finding)
        assertEquals("ALLOWANCE_SUGGESTION", finding!!.detectionType)
        assertEquals("best-gap", finding.subjectPackage)
        assertEquals("Best Gap", finding.subjectAppName)
        assertTrue(finding.evidenceJson.contains("\"high_avg_min\":15"))
        assertTrue(finding.evidenceJson.contains("\"low_avg_min\":55"))
        assertTrue(finding.evidenceJson.contains("\"suggested_min\":15"))
        assertNull(detectAllowanceSuggestion(rows, ratings.take(6)))
    }

    @Test
    fun allowanceSuggestionRequiresMoreThanFifteenMinutesOfDifference() {
        val today = LocalDate.of(2026, 6, 28)
        val ratedDays = (0..7).map { today.minusDays(it.toLong()) }
        val ratings = ratedDays.mapIndexed { index, date ->
            rating(date, if (index < 5) 7 else 4)
        }
        val rows = ratedDays.mapIndexed { index, date ->
            usageRow(
                packageName = "social",
                date = date,
                foregroundMs = (if (index < 5) 20 else 35) * 60_000L,
                category = "social",
            )
        }

        assertNull(detectAllowanceSuggestion(rows, ratings))
    }

    @Test
    fun streakLockInRequiresEligibleCategoryConsistencyAndLowRatings() {
        val start = LocalDate.of(2026, 4, 1)
        val rows = (0 until 14).map {
            usageRow(
                packageName = "social",
                date = start.plusDays(it.toLong()),
                foregroundMs = 10 * 60_000L,
                category = "social",
                launchCount = 1,
            )
        }
        val ratings = listOf(0, 1, 2).map {
            rating(start.plusDays(it.toLong()), 4)
        }

        val finding = detectStreakLockIn(rows, ratings, windowDays = 14)

        assertNotNull(finding)
        assertEquals("social", finding!!.subjectPackage)
        assertNull(detectStreakLockIn(rows, emptyList(), windowDays = 14))
    }

    private fun appSession(packageName: String, id: Long, durationMs: Long) =
        AppSessionEntity(
            id = id,
            packageName = packageName,
            appName = packageName,
            startedAt = id,
            endedAt = id + durationMs,
            durationMs = durationMs,
            localDate = "2026-01-01",
        )

    private fun datedSession(
        packageName: String,
        startedAt: Instant,
        durationMs: Long,
    ) = AppSessionEntity(
        id = startedAt.toEpochMilli(),
        packageName = packageName,
        appName = packageName,
        startedAt = startedAt.toEpochMilli(),
        endedAt = startedAt.toEpochMilli() + durationMs,
        durationMs = durationMs,
        localDate = startedAt.atZone(ZoneOffset.UTC).toLocalDate().toString(),
    )

    private fun sessionStatsFrom(sessions: List<AppSessionEntity>): List<SessionStatRow> =
        sessions.groupBy { it.packageName to it.localDate }
            .map { (key, rows) ->
                val durations = rows.map { it.durationMs }
                SessionStatRow(
                    packageName = key.first,
                    localDate = key.second,
                    sessionCount = durations.size,
                    avgDurationMs = durations.average(),
                    minDurationMs = durations.min(),
                    maxDurationMs = durations.max(),
                    totalMs = durations.sum(),
                )
            }
            .sortedBy { it.localDate }

    private fun usageRow(
        packageName: String,
        date: LocalDate,
        foregroundMs: Long,
        category: String? = "social",
        launchCount: Int = 1,
        appName: String = packageName,
    ) = AppUsageRangeRow(
        packageName = packageName,
        appName = appName,
        category = category,
        date = date.toString(),
        foregroundMs = foregroundMs,
        hourlyMs = "",
        launchCount = launchCount,
        lastUsedAt = 0L,
    )

    private fun weeklyRows(
        packageName: String,
        today: LocalDate,
        weeklyMinutes: List<Int>,
        category: String? = "social",
    ): List<AppUsageRangeRow> =
        (1..28).map { daysAgo ->
            val weekNumber = 4 - (daysAgo - 1) / 7
            usageRow(
                packageName = packageName,
                date = today.minusDays(daysAgo.toLong()),
                foregroundMs = weeklyMinutes[weekNumber - 1] * 60_000L,
                category = category,
            )
        }

    private fun rating(date: LocalDate, value: Int) =
        DayRatingEntity(
            date = date.toString(),
            rating = value,
            contextTag = null,
            note = null,
            appTags = "[]",
            wordTags = "[]",
            createdAt = Instant.EPOCH.toString(),
            updatedAt = Instant.EPOCH.toString(),
        )
}