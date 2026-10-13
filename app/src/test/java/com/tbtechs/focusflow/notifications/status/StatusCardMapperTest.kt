package com.tbtechs.focusflow.notifications.status

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusCardMapperTest {
    @Test
    fun idleUsesCountUpChronometerBaseFromServiceStart() {
        val model = StatusCardMapper.idle(
            serviceStartMs = 4_000L,
            clock = clock(wallClockMs = 10_000L, elapsedRealtimeMs = 50_000L),
        )

        assertEquals(44_000L, model.chronometerBaseMs)
        assertFalse(model.chronometerCountDown)
        assertTrue(model.usesChronometer)
        assertTrue(model.showWhen)
        assertTrue(model.ongoing)
        assertTrue(model.onlyAlertOnce)
        assertEquals(StatusCardPriority.MIN, model.priority)
        assertEquals("FocusFlow", model.title)
        assertEquals("Monitoring active — tap to open", model.text)
    }

    @Test
    fun idleAttentionStateChangesTextAndTapTarget() {
        val model = StatusCardMapper.idle(
            serviceStartMs = 4_000L,
            clock = clock(wallClockMs = 10_000L, elapsedRealtimeMs = 50_000L),
            needsAttention = true,
        )

        assertEquals("Needs attention — tap to fix", model.text)
        assertEquals(StatusCardTapTarget.PERMISSIONS, model.tapTarget)
    }

    @Test
    fun idleUnknownHealthShowsCheckingStateAndOpensHome() {
        val model = StatusCardMapper.idle(
            serviceStartMs = 4_000L,
            clock = clock(wallClockMs = 10_000L, elapsedRealtimeMs = 50_000L),
            healthKnown = false,
        )

        assertEquals("Checking protection status — tap to open", model.text)
        assertEquals(StatusCardTapTarget.HOME, model.tapTarget)
    }

    @Test
    fun focusEndLabelHandlesMidnightAndTwelveThirtyPm() {
        assertEquals(
            "ends 12:00 AM",
            focusModel(endHour = 0, endMinute = 0).text,
        )
        assertEquals(
            "ends 12:30 PM",
            focusModel(endHour = 12, endMinute = 30).text,
        )
    }

    @Test
    fun focusProgressIsClampedToZeroAndOneHundred() {
        assertEquals(0, focusModel(remainingMs = 1_500L).progressPercent)
        assertEquals(100, focusModel(remainingMs = -500L).progressPercent)
    }

    @Test
    fun focusProgressUsesTheOriginalStartTimeAfterRecovery() {
        val model = StatusCardMapper.focus(
            taskId = "task-1",
            taskName = "Deep work",
            startTimeMs = 1_000_000L,
            endTimeMs = 2_000_000L,
            remainingMs = 250_000L,
            nextName = null,
            clock = clock(wallClockMs = 1_750_000L, elapsedRealtimeMs = 50_000L),
        )

        assertEquals(75, model.progressPercent)
    }

    @Test
    fun focusAndBreakChronometerBasesCountDownFromTheirEndTimes() {
        val clock = clock(wallClockMs = 1_000_000L, elapsedRealtimeMs = 500_000L)
        val focus = StatusCardMapper.focus(
            taskId = "task-1",
            taskName = "Deep work",
            startTimeMs = 1_000_000L,
            endTimeMs = 1_120_000L,
            remainingMs = 120_000L,
            nextName = null,
            clock = clock,
        )
        val breakCard = StatusCardMapper.breakTime(
            taskName = "Deep work",
            breakUntilMs = 1_050_000L,
            clock = clock,
        )

        assertEquals(620_000L, focus.chronometerBaseMs)
        assertEquals(550_000L, breakCard.chronometerBaseMs)
        assertTrue(focus.chronometerCountDown)
        assertTrue(breakCard.chronometerCountDown)
    }

    @Test
    fun focusKeepsActionOrderPriorityAndPersistentFlags() {
        val model = focusModel()

        assertEquals(
            listOf(
                StatusCardAction.DONE,
                StatusCardAction.EXTEND_15,
                StatusCardAction.EXTEND_30,
                StatusCardAction.SKIP,
            ),
            model.actions,
        )
        assertEquals(StatusCardPriority.LOW, model.priority)
        assertTrue(model.usesChronometer)
        assertTrue(model.showWhen)
        assertTrue(model.ongoing)
        assertTrue(model.onlyAlertOnce)
        assertEquals("Next: Read", model.subText)
    }

    private fun focusModel(
        endHour: Int = 12,
        endMinute: Int = 0,
        remainingMs: Long = 500L,
    ): StatusCardModel {
        val endTimeMs = LocalDate.of(2026, 6, 1)
            .atTime(endHour, endMinute)
            .atZone(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
        return StatusCardMapper.focus(
            taskId = "task-1",
            taskName = "Deep work",
            startTimeMs = endTimeMs - 1_000L,
            endTimeMs = endTimeMs,
            remainingMs = remainingMs,
            nextName = "Read",
            clock = clock(
                wallClockMs = 1_000L,
                elapsedRealtimeMs = 2_000L,
                zoneId = ZoneId.of("UTC"),
            ),
        )
    }

    private fun clock(
        wallClockMs: Long,
        elapsedRealtimeMs: Long,
        zoneId: ZoneId = ZoneId.of("UTC"),
    ) = StatusCardClock(
        wallClockMs = wallClockMs,
        elapsedRealtimeMs = elapsedRealtimeMs,
        zoneId = zoneId,
        locale = Locale.US,
    )
}
