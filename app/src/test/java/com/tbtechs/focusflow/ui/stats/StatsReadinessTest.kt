package com.tbtechs.focusflow.ui.stats

import com.tbtechs.focusflow.ui.permissions.PermissionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsReadinessTest {
    @Test
    fun nothingMissingMeansNothingToShow() {
        assertTrue(readinessItems(emptySet(), nudgesOn = true).isEmpty())
    }

    @Test
    fun coreProtectionPermissionsAreListedInOrder() {
        val items = readinessItems(
            setOf(PermissionId.OVERLAY, PermissionId.ACCESSIBILITY, PermissionId.USAGE),
            nudgesOn = false,
        )
        assertEquals(
            listOf(PermissionId.USAGE, PermissionId.ACCESSIBILITY, PermissionId.OVERLAY),
            items.map { it.id },
        )
    }

    @Test
    fun notificationsOnlyMatterOnceHeadsUpsAreOn() {
        val missing = setOf(PermissionId.NOTIFICATIONS)
        assertTrue(readinessItems(missing, nudgesOn = false).isEmpty())
        assertEquals(listOf(PermissionId.NOTIFICATIONS), readinessItems(missing, nudgesOn = true).map { it.id })
    }

    @Test
    fun durationsPickMinutesHoursOrBoth() {
        assertEquals(DurationShape.MINUTES, durationShape(45))
        assertEquals(DurationShape.HOURS, durationShape(120))
        assertEquals(DurationShape.HOURS_MINUTES, durationShape(80))
        assertEquals(DurationShape.MINUTES, durationShape(0))
    }
}
