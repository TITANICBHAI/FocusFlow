package com.tbtechs.focusflow.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NudgeScheduleTest {
    private fun nudge(minute: Int, title: String = "n$minute") = HabitNudge(minute, title, "t")

    @Test
    fun mergeKeepsPrimaryFirstAndReturnsClockOrder() {
        val merged = NudgeSchedule.merge(listOf(nudge(20 * 60)), listOf(nudge(8 * 60)))
        assertEquals(listOf(8 * 60, 20 * 60), merged.map { it.minuteOfDay })
    }

    @Test
    fun mergeDropsAnythingWithinThirtyMinutesOfAnEarlierPick() {
        val merged = NudgeSchedule.merge(listOf(nudge(600, "first")), listOf(nudge(629, "close"), nudge(630, "ok")))
        assertEquals(listOf("first", "ok"), merged.map { it.title })
    }

    @Test
    fun primaryBeatsSecondaryWhenTheyCollide() {
        val merged = NudgeSchedule.merge(listOf(nudge(700, "p")), listOf(nudge(710, "s")))
        assertEquals(listOf("p"), merged.map { it.title })
    }

    @Test
    fun mergeNeverReturnsMoreThanTheCap() {
        val many = (0 until 8).map { nudge(it * 120) }
        assertEquals(NudgeSchedule.MAX_NUDGES, NudgeSchedule.merge(many, emptyList()).size)
        assertEquals(2, NudgeSchedule.merge(many, emptyList(), max = 2).size)
        assertEquals(emptyList<HabitNudge>(), NudgeSchedule.merge(emptyList(), emptyList()))
    }

    @Test
    fun lateNightMinuteIsHalfAnHourBeforeBedtimeButNeverAfterTenThirty() {
        assertEquals(22 * 60 + 30, NudgeSchedule.lateNightMinute(null))
        assertEquals(22 * 60 + 30, NudgeSchedule.lateNightMinute(60))
        assertEquals(22 * 60 + 30, NudgeSchedule.lateNightMinute(23 * 60 + 30))
        assertEquals(21 * 60 + 30, NudgeSchedule.lateNightMinute(22 * 60))
        assertEquals(12 * 60, NudgeSchedule.lateNightMinute(12 * 60 + 30))
    }

    @Test
    fun hourTextUsesTwelveHourClock() {
        assertEquals("12 am", NudgeSchedule.hourText(0))
        assertEquals("9 am", NudgeSchedule.hourText(9))
        assertEquals("12 pm", NudgeSchedule.hourText(12))
        assertEquals("9 pm", NudgeSchedule.hourText(21))
    }
}
