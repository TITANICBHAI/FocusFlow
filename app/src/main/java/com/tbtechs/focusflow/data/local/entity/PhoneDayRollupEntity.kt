package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * One derived row per past local day of phone-level behaviour (screen-on, wakes, unlocks, session buckets).
 * Rebuilt from OS usage events; not part of any backup. Hourly and bucket lists are CSV strings made by `PhoneCodec`.
 */
@Entity(tableName = "phone_day_rollup", primaryKeys = ["date"])
data class PhoneDayRollupEntity(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "behaviour_version") val behaviourVersion: Int,
    @ColumnInfo(name = "computed_at_ms") val computedAtMs: Long,
    @ColumnInfo(name = "coverage_start_ms") val coverageStartMs: Long?,
    @ColumnInfo(name = "screen_on_ms") val screenOnMs: Long,
    @ColumnInfo(name = "screen_on_hourly_ms") val screenOnHourlyMs: String,
    @ColumnInfo(name = "wake_count") val wakeCount: Int,
    @ColumnInfo(name = "wake_hourly") val wakeHourly: String,
    @ColumnInfo(name = "unlock_count") val unlockCount: Int,
    @ColumnInfo(name = "unlock_hourly") val unlockHourly: String,
    @ColumnInfo(name = "unlock_signal") val unlockSignal: Boolean,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    @ColumnInfo(name = "session_bucket_counts") val sessionBucketCounts: String,
    @ColumnInfo(name = "session_bucket_ms") val sessionBucketMs: String,
    @ColumnInfo(name = "longest_session_ms") val longestSessionMs: Long,
    @ColumnInfo(name = "first_unlock_at_ms") val firstUnlockAtMs: Long?,
    @ColumnInfo(name = "unlock_gap_sum_ms") val unlockGapSumMs: Long,
    @ColumnInfo(name = "unlock_gap_count") val unlockGapCount: Int,
    @ColumnInfo(name = "unlocks_without_app") val unlocksWithoutApp: Int,
) {
    companion object {
        const val COMPLETE = "COMPLETE"
        const val PARTIAL = "PARTIAL"
    }
}
