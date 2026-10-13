package com.tbtechs.focusflow.analytics.detection

import com.tbtechs.focusflow.data.local.dao.EstimationErrorRow
import com.tbtechs.focusflow.data.local.dao.SessionOverrideCountRow
import com.tbtechs.focusflow.data.local.entity.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FindingDetectorsTest {

    @Test
    fun postFailureCascadeRequiresSixDaysInBothComparisonGroups() {
        val tasks = buildList {
            repeat(6) { index ->
                add(task("failed-$index", date = LocalDate.of(2026, 1, 1).plusDays(index.toLong()), "skipped", 0))
                add(task("failed-rest-a-$index", LocalDate.of(2026, 1, 1).plusDays(index.toLong()), "skipped", 1))
                add(task("failed-rest-b-$index", LocalDate.of(2026, 1, 1).plusDays(index.toLong()), "overdue", 2))
            }
            repeat(6) { index ->
                val date = LocalDate.of(2026, 2, 1).plusDays(index.toLong())
                add(task("completed-$index", date, "completed", 0))
                add(task("completed-rest-a-$index", date, "completed", 1))
                add(task("completed-rest-b-$index", date, "completed", 2))
            }
        }

        assertNotNull(detectPostFailureCascade(tasks))
        assertNull(detectPostFailureCascade(tasks.filterNot { it.id == "failed-0" }))
    }

    @Test
    fun sessionSweetSpotRequiresTwentySessionsAndFindsLaterDropoff() {
        val sessions = buildList {
            repeat(3) { add(session(it.toLong(), 15, 0)) }
            repeat(3) { add(session((it + 3).toLong(), 30, 0)) }
            repeat(3) { add(session((it + 6).toLong(), 60, 1)) }
            repeat(11) { add(session((it + 9).toLong(), 90, 1)) }
        }

        val finding = detectSessionSweetSpot(sessions)

        assertNotNull(finding)
        assertTrue(finding!!.evidenceJson.contains("\"peak_bin\":\"30-45m\""))
        assertTrue(finding.evidenceJson.contains("\"dropoff_bin\":\"60-90m\""))
    }

    @Test
    fun estimationDriftRequiresFiveSamplesPerTimePeriodAndTenMinuteGap() {
        val errors = buildList {
            repeat(5) {
                add(EstimationErrorRow("morning-$it", 20, 30.0, 9))
                add(EstimationErrorRow("afternoon-$it", 20, 45.0, 15))
            }
        }

        val finding = detectEstimationDrift(errors)

        assertNotNull(finding)
        assertTrue(finding!!.evidenceJson.contains("\"morning_n\":5"))
        assertTrue(finding.evidenceJson.contains("\"afternoon_n\":5"))
        assertNull(detectEstimationDrift(errors.take(9)))
    }

    @Test
    fun dayOfWeekOutlierUsesPersonalBaselineAndRequiresFiveWeekdays() {
        val dates = listOf(
            LocalDate.of(2026, 9, 21), // Monday: worst day
            LocalDate.of(2026, 9, 22),
            LocalDate.of(2026, 9, 23),
            LocalDate.of(2026, 9, 24),
            LocalDate.of(2026, 9, 25),
        )
        val tasks = dates.flatMapIndexed { dayIndex, date ->
            (0 until 3).map { taskIndex ->
                task(
                    id = "dow-$dayIndex-$taskIndex",
                    date = date,
                    status = if (dayIndex == 0) "skipped" else "completed",
                    order = taskIndex,
                )
            }
        }

        val finding = detectDayOfWeekOutlier(tasks)

        assertNotNull(finding)
        assertTrue(finding!!.headline.contains("Monday"))
    }

    @Test
    fun evidenceFingerprintIsStableWithinBucketsAndChangesAcrossBuckets() {
        val first = evidenceFingerprint("TEST", null, 20, 0.21, 0.05)
        val sameBucket = evidenceFingerprint("TEST", null, 24, 0.24, 0.05)
        val differentBucket = evidenceFingerprint("TEST", null, 25, 0.26, 0.05)

        assertEquals(first, sameBucket)
        assertTrue(first != differentBucket)
        assertEquals(40, first.length)
    }

    private fun task(
        id: String,
        date: LocalDate,
        status: String,
        order: Int,
    ): TaskEntity {
        val start = date.atTime(8 + order, 0).toInstant(ZoneOffset.UTC).toString()
        val end = date.atTime(8 + order, 30).toInstant(ZoneOffset.UTC).toString()
        return TaskEntity(
            id = id,
            title = id,
            description = null,
            startTime = start,
            endTime = end,
            durationMinutes = 30,
            status = status,
            priority = "medium",
            tags = "[]",
            reminders = "[]",
            color = "#6366f1",
            focusMode = false,
            focusAllowedPackages = null,
            createdAt = start,
            updatedAt = end,
        )
    }

    private fun session(id: Long, minutes: Int, overrideCount: Int): SessionOverrideCountRow {
        val start = Instant.parse("2026-01-01T08:00:00Z").plusSeconds(id * 86_400)
        val end = start.plusSeconds(minutes * 60L)
        return SessionOverrideCountRow(
            sessionId = id,
            taskId = "task-$id",
            startedAt = start.toString(),
            endedAt = end.toString(),
            overrideCount = overrideCount,
        )
    }
}