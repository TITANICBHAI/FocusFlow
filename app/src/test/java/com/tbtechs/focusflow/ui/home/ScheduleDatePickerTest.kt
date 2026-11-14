package com.tbtechs.focusflow.ui.home

import java.time.LocalDate
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleDatePickerTest {
    @Test
    fun pickerMillisRoundTripToTheSameDayInAnyTimeZone() {
        val original = TimeZone.getDefault()
        try {
            val day = LocalDate.of(2026, 11, 20)
            listOf("UTC", "Asia/Kolkata", "America/Los_Angeles", "Pacific/Kiritimati", "Pacific/Pago_Pago")
                .forEach { zone ->
                    TimeZone.setDefault(TimeZone.getTimeZone(zone))
                    assertEquals(zone, day, day.toPickerMillis().fromPickerMillis())
                }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun scheduleFollowsTodayUntilADateIsPickedAndGoesBackWhenReset() {
        ScheduleDateState.resetToToday()
        assertTrue(ScheduleDateState.isToday())

        ScheduleDateState.select(LocalDate.now())
        assertNull(ScheduleDateState.selected)

        val later = LocalDate.now().plusDays(3)
        ScheduleDateState.select(later)
        assertEquals(later, ScheduleDateState.current())
        assertTrue(!ScheduleDateState.isToday())

        ScheduleDateState.resetToToday()
        assertNull(ScheduleDateState.selected)
        assertEquals(LocalDate.now(), ScheduleDateState.current())
    }
}
