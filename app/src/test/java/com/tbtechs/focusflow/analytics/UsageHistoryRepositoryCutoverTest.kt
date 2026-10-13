package com.tbtechs.focusflow.analytics

import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.UsagePipelineStateEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupDayEntity
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageHistoryRepositoryCutoverTest {
    private val today = "2026-10-08"

    @Test
    fun missingPipelineStateIsSeededIdempotentlyWithTheLocalReadDate() = runBlocking {
        val store = FakeStore(state = null)
        val repository = UsageHistoryRepository(store)

        assertTrue(repository.shouldWriteLegacy(today))
        val seeded = store.state
        assertNotNull(seeded)
        assertEquals(today, seeded?.shadowStartedOn)
        assertEquals(null, seeded?.cutoverDate)

        assertTrue(repository.shouldWriteLegacy(today))
        assertEquals(2, store.ensureCalls)
        assertEquals(today, store.state?.shadowStartedOn)
        assertEquals(0, store.cutoverWrites)
    }

    @Test
    fun fewerThanSevenCompleteDaysDoNotCutOver() = runBlocking {
        val store = FakeStore(
            state = newPipelineState(shadowStartedOn = "2026-10-01"),
            rollups = completeDays(count = 6),
        )

        assertTrue(UsageHistoryRepository(store).shouldWriteLegacy(today))
        assertEquals(null, store.state?.cutoverDate)
        assertEquals(0, store.cutoverWrites)
    }

    @Test
    fun sevenCompleteCurrentVersionDaysCutOver() = runBlocking {
        val store = FakeStore(
            state = newPipelineState(shadowStartedOn = "2026-10-01"),
            rollups = completeDays(count = 7),
        )

        UsageHistoryRepository(store).shouldWriteLegacy(today)

        assertEquals(today, store.state?.cutoverDate)
        assertEquals(1, store.cutoverWrites)
    }

    @Test
    fun olderPipelineVersionDoesNotSatisfyTheSevenDayWindow() = runBlocking {
        val store = FakeStore(
            state = newPipelineState(shadowStartedOn = "2026-10-01"),
            rollups = completeDays(
                count = 7,
                pipelineVersion = UsagePipelineStateEntity.CURRENT_PIPELINE_VERSION - 1,
            ),
        )

        assertTrue(UsageHistoryRepository(store).shouldWriteLegacy(today))
        assertEquals(null, store.state?.cutoverDate)
        assertEquals(0, store.cutoverWrites)
    }

    @Test
    fun repeatedCutoverChecksKeepTheFirstCutoverDate() = runBlocking {
        val store = FakeStore(
            state = newPipelineState(shadowStartedOn = "2026-10-01"),
            rollups = completeDays(count = 7),
        )
        val repository = UsageHistoryRepository(store)

        repository.shouldWriteLegacy(today)
        repository.shouldWriteLegacy(today)

        assertEquals(today, store.state?.cutoverDate)
        assertEquals(1, store.cutoverWrites)
    }

    private fun newPipelineState(
        shadowStartedOn: String,
        cutoverDate: String? = null,
    ) = UsagePipelineStateEntity(
        cutoverDate = cutoverDate,
        pipelineVersion = UsagePipelineStateEntity.CURRENT_PIPELINE_VERSION,
        shadowStartedOn = shadowStartedOn,
    )

    private fun completeDays(
        count: Int,
        pipelineVersion: Int = UsagePipelineStateEntity.CURRENT_PIPELINE_VERSION,
    ): List<UsageRollupDayEntity> =
        (1L..count.toLong()).map { daysAgo ->
            UsageRollupDayEntity(
                date = LocalDate.parse(today).minusDays(daysAgo).toString(),
                status = UsageRollupDayEntity.COMPLETE,
                coverageStartMs = null,
                coverageEndMs = null,
                pipelineVersion = pipelineVersion,
                computedAtMs = 1L,
                totalForegroundMs = 1L,
            )
        }

    private class FakeStore(
        var state: UsagePipelineStateEntity?,
        private val rollups: List<UsageRollupDayEntity> = emptyList(),
    ) : UsageHistoryStore {
        var ensureCalls = 0
            private set
        var cutoverWrites = 0
            private set

        override suspend fun legacyAppDays(
            startDate: String,
            endDate: String,
        ): List<UsageHistoryAppDay> = emptyList()

        override suspend fun legacySessions(
            startDate: String,
            endDate: String,
        ): List<AppSessionEntity> = emptyList()

        override suspend fun pipelineState(): UsagePipelineStateEntity? = state

        override suspend fun ensurePipelineState(shadowStartedOn: String) {
            ensureCalls += 1
            if (state == null) {
                state = UsagePipelineStateEntity(
                    cutoverDate = null,
                    pipelineVersion = UsagePipelineStateEntity.CURRENT_PIPELINE_VERSION,
                    shadowStartedOn = shadowStartedOn,
                )
            }
        }

        override suspend fun rollupDays(
            startDate: String,
            endDate: String,
        ): List<UsageRollupDayEntity> =
            rollups.filter { it.date in startDate..endDate }

        override suspend fun setCutoverDateIfShadowMature(
            cutoverDate: String,
            shadowStartedBy: String,
        ): Boolean {
            val current = state ?: return false
            if (current.cutoverDate != null || current.shadowStartedOn > shadowStartedBy) return false
            cutoverWrites += 1
            state = current.copy(cutoverDate = cutoverDate)
            return true
        }
    }
}
