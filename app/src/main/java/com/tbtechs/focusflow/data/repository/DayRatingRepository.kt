package com.tbtechs.focusflow.data.repository

import android.util.Log
import com.tbtechs.focusflow.data.local.dao.DailyAppUsageDao
import com.tbtechs.focusflow.data.local.dao.DayRatingDao
import com.tbtechs.focusflow.data.local.dao.TaskDao
import com.tbtechs.focusflow.data.local.entity.DayRatingEntity
import com.tbtechs.focusflow.analytics.UsageHistoryConsumer
import com.tbtechs.focusflow.analytics.UsageHistoryRepository
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DayRatingRepository(private val dao: DayRatingDao) {
    private companion object {
        private const val TAG = "DayRatingRepository"
        private const val RETRO_DAYS = 14L
        private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
    }

    suspend fun upsert(rating: DayRatingEntity): Boolean =
        try {
            dao.upsert(rating)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "upsert(${rating.date}) failed", error)
            false
        }

    suspend fun getForDate(date: String): DayRatingEntity? =
        runCatching { dao.getForDate(date) }.getOrNull()

    suspend fun getForDateRange(startDate: String, endDate: String): List<DayRatingEntity> =
        runCatching { dao.getForDateRange(startDate, endDate) }.getOrDefault(emptyList())

    suspend fun count(): Int = runCatching { dao.count() }.getOrDefault(0)

    suspend fun getRatableDates(
        dailyAppUsageDao: DailyAppUsageDao,
        taskDao: TaskDao,
        usageHistoryRepository: UsageHistoryRepository? = null,
    ): List<RatableDateEntry> {
        val today = LocalDate.now()
        val cutoff = today.minusDays(RETRO_DAYS).format(DATE_FMT)
        val todayString = today.format(DATE_FMT)
        val usageDates = runCatching {
            usageHistoryRepository?.groupBUsageDates(
                startDate = cutoff,
                endDate = todayString,
                today = todayString,
                consumer = UsageHistoryConsumer.RATING_ELIGIBILITY,
            ) ?: dailyAppUsageDao.getForDateRange(cutoff, todayString).map { it.date }.toSet()
        }.getOrDefault(emptySet())
        val taskDates = runCatching {
            taskDao.getTaskDatesInRange(cutoff, todayString).toSet()
        }.getOrDefault(emptySet())
        val rated = runCatching { dao.getRecentRatedDates(RETRO_DAYS.toInt()).toSet() }
            .getOrDefault(emptySet())
        return (usageDates + taskDates).sortedDescending()
            .map { RatableDateEntry(it, it in rated) }
    }

    data class RatableDateEntry(val date: String, val hasRating: Boolean)
}