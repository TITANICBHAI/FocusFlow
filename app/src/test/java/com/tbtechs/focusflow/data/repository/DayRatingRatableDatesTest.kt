package com.tbtechs.focusflow.data.repository

import com.tbtechs.focusflow.analytics.UsageHistoryAppDay
import com.tbtechs.focusflow.analytics.UsageHistoryRepository
import com.tbtechs.focusflow.analytics.UsageHistoryStore
import com.tbtechs.focusflow.data.local.dao.DailyAppUsageDao
import com.tbtechs.focusflow.data.local.dao.DayRatingDao
import com.tbtechs.focusflow.data.local.dao.TaskDao
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.DailyAppUsageEntity
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DayRatingRatableDatesTest {
    @Test
    fun mergedUsageHistoryKeepsDatesThatHaveTasksButNoUsage() = runBlocking {
        val taskOnlyDate = LocalDate.now().minusDays(2).toString()
        val dailyUsageDao = EmptyDailyAppUsageDao()
        val taskDao = fakeDao(TaskDao::class.java) { method, _ ->
            if (method.name == "getTaskDatesInRange") listOf(taskOnlyDate) else null
        }
        val dayRatingDao = fakeDao(DayRatingDao::class.java) { method, _ ->
            if (method.name == "getRecentRatedDates") emptyList<String>() else null
        }
        val history = UsageHistoryRepository(EmptyUsageHistoryStore())

        val dates = DayRatingRepository(dayRatingDao).getRatableDates(
            dailyAppUsageDao = dailyUsageDao,
            taskDao = taskDao,
            usageHistoryRepository = history,
        )

        assertEquals(listOf(DayRatingRepository.RatableDateEntry(taskOnlyDate, false)), dates)
    }

    private class EmptyUsageHistoryStore : UsageHistoryStore {
        override suspend fun legacyAppDays(
            startDate: String,
            endDate: String,
        ): List<UsageHistoryAppDay> = emptyList()

        override suspend fun legacySessions(
            startDate: String,
            endDate: String,
        ): List<AppSessionEntity> = emptyList()
    }

    private class EmptyDailyAppUsageDao : DailyAppUsageDao() {
        override suspend fun upsert(entity: DailyAppUsageEntity) = Unit

        override suspend fun getForPackageAndDate(
            date: String,
            packageName: String,
        ): DailyAppUsageEntity? = null

        override suspend fun getForDateRange(
            startDate: String,
            endDate: String,
        ) = emptyList<com.tbtechs.focusflow.data.local.dao.AppUsageRangeRow>()

        override suspend fun deleteOlderThan(cutoffDate: String) = Unit

        override suspend fun countDistinctDates(): Int = 0
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> fakeDao(
        daoType: Class<T>,
        answer: (Method, Array<out Any?>) -> Any?,
    ): T = Proxy.newProxyInstance(
        daoType.classLoader,
        arrayOf(daoType),
    ) { _, method, arguments ->
        answer(method, arguments ?: emptyArray())
    } as T
}
