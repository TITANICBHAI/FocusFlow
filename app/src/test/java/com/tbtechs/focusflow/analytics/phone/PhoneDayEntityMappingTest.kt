package com.tbtechs.focusflow.analytics.phone

import com.tbtechs.focusflow.data.local.entity.BlockedAttemptDayEntity
import com.tbtechs.focusflow.data.local.entity.PhoneDayRollupEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneDayEntityMappingTest {

    private fun fullDay(status: PhoneDayStatus, unlockSignal: Boolean) = PhoneDay(
        date = "2026-11-03",
        status = status,
        screenOnMs = 12_345_678L,
        screenOnHourlyMs = List(24) { (it + 1) * 1_000L },
        wakeCount = 31,
        wakeHourly = List(24) { it },
        unlockCount = if (unlockSignal) 47 else 0,
        unlockHourly = List(24) { if (unlockSignal) it + 100 else 0 },
        unlockSignal = unlockSignal,
        sessionBasis = if (unlockSignal) SessionBasis.UNLOCK else SessionBasis.SCREEN,
        sessionCount = 29,
        sessionBucketCounts = listOf(1, 2, 3, 4, 5),
        sessionBucketMs = listOf(10L, 20L, 30L, 40L, 50L),
        longestSessionMs = 987_654L,
        firstUnlockAtMs = 1_762_150_000_000L,
        unlockGapSumMs = 5_555_555L,
        unlockGapCount = 17,
        firstApps = mapOf("com.a" to 3, "com.b" to 1),
        unlocksWithoutApp = 4,
    )

    @Test
    fun roundTripKeepsEveryFieldWhenCompleteWithUnlocks() {
        val day = fullDay(PhoneDayStatus.COMPLETE, unlockSignal = true)
        val entity = day.toEntity(computedAtMs = 99L, coverageStartMs = 77L)
        assertEquals(day, entity.toPhoneDay(day.firstApps))
        assertEquals(PhoneDayRollupEntity.COMPLETE, entity.status)
        assertEquals(PhoneThresholds.BEHAVIOUR_VERSION, entity.behaviourVersion)
        assertEquals(99L, entity.computedAtMs)
        assertEquals(77L, entity.coverageStartMs)
    }

    @Test
    fun roundTripKeepsEveryFieldWhenPartialWithoutUnlockSignal() {
        val day = fullDay(PhoneDayStatus.PARTIAL, unlockSignal = false)
        val entity = day.toEntity(computedAtMs = 1L, coverageStartMs = null)
        assertEquals(PhoneDayRollupEntity.PARTIAL, entity.status)
        assertEquals(null, entity.coverageStartMs)
        assertEquals(day, entity.toPhoneDay(day.firstApps))
        assertEquals(SessionBasis.SCREEN, entity.toPhoneDay(emptyMap()).sessionBasis)
    }

    @Test
    fun nullableFirstUnlockSurvives() {
        val day = fullDay(PhoneDayStatus.COMPLETE, true).copy(firstUnlockAtMs = null)
        assertEquals(null, day.toEntity(1L, 1L).toPhoneDay(day.firstApps).firstUnlockAtMs)
    }

    @Test
    fun firstAppsAreNotStoredInTheDayRowAndComeFromTheArgument() {
        val day = fullDay(PhoneDayStatus.COMPLETE, true)
        val entity = day.toEntity(1L, 1L)
        assertEquals(emptyMap<String, Int>(), entity.toPhoneDay(emptyMap()).firstApps)
        assertEquals(mapOf("x" to 9), entity.toPhoneDay(mapOf("x" to 9)).firstApps)
    }

    @Test
    fun encodedColumnsUseCsv() {
        val entity = PhoneDay.empty("2026-11-03", PhoneDayStatus.COMPLETE).toEntity(1L, 1L)
        assertEquals(List(24) { "0" }.joinToString(","), entity.wakeHourly)
        assertEquals("0,0,0,0,0", entity.sessionBucketCounts)
    }

    @Test
    fun corruptCsvDecodesToFixedSizeZeros() {
        val entity = PhoneDay.empty("2026-11-03", PhoneDayStatus.COMPLETE).toEntity(1L, 1L)
            .copy(screenOnHourlyMs = "x,y", wakeHourly = "", sessionBucketMs = "1,2")
        val day = entity.toPhoneDay(emptyMap())
        assertEquals(List(24) { 0L }, day.screenOnHourlyMs)
        assertEquals(List(24) { 0 }, day.wakeHourly)
        assertEquals(listOf(1L, 2L, 0L, 0L, 0L), day.sessionBucketMs)
    }

    @Test
    fun unknownStatusTextReadsAsPartial() {
        val entity = PhoneDay.empty("2026-11-03", PhoneDayStatus.COMPLETE).toEntity(1L, 1L).copy(status = "weird")
        assertEquals(PhoneDayStatus.PARTIAL, entity.toPhoneDay(emptyMap()).status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun liveDaysCannotBePersisted() {
        PhoneDay.empty("2026-11-07", PhoneDayStatus.LIVE).toEntity(1L, 1L)
    }

    // ---- blocked-attempt max rule ----

    private fun blocked(date: String, pkg: String, attempts: Int) =
        BlockedAttemptDayEntity(date, pkg, pkg, attempts, List(24) { 0 }.joinToString(","))

    @Test
    fun maxRuleWritesNewRowsOnly() {
        val out = blockedRowsToWrite(emptyList(), listOf(blocked("d1", "a", 2), blocked("d1", "b", 1)))
        assertEquals(2, out.size)
    }

    @Test
    fun maxRuleKeepsHigherExistingAndIgnoresEqual() {
        val existing = listOf(blocked("d1", "a", 5), blocked("d1", "b", 3))
        val out = blockedRowsToWrite(existing, listOf(blocked("d1", "a", 2), blocked("d1", "b", 3)))
        assertTrue(out.isEmpty())
    }

    @Test
    fun maxRuleWritesStrictlyHigherIncoming() {
        val existing = listOf(blocked("d1", "a", 2), blocked("d2", "a", 9))
        val out = blockedRowsToWrite(existing, listOf(blocked("d1", "a", 3), blocked("d2", "a", 8)))
        assertEquals(listOf(blocked("d1", "a", 3)), out)
    }

    @Test
    fun maxRuleKeysOnDateAndPackageTogether() {
        val existing = listOf(blocked("d1", "a", 5))
        val out = blockedRowsToWrite(existing, listOf(blocked("d2", "a", 1), blocked("d1", "b", 1)))
        assertEquals(2, out.size)
        assertFalse(out.any { it.date == "d1" && it.packageName == "a" })
    }

    @Test
    fun maxRuleCollapsesDuplicateIncomingKeysToTheHighest() {
        val out = blockedRowsToWrite(emptyList(), listOf(blocked("d1", "a", 2), blocked("d1", "a", 7), blocked("d1", "a", 4)))
        assertEquals(listOf(blocked("d1", "a", 7)), out)
    }
}
