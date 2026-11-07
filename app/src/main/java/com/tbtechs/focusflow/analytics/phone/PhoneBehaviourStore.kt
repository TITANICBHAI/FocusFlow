package com.tbtechs.focusflow.analytics.phone

import androidx.room.withTransaction
import com.tbtechs.focusflow.data.local.FocusFlowDatabase
import com.tbtechs.focusflow.data.local.dao.SessionOverrideCountRow
import com.tbtechs.focusflow.data.local.entity.BlockedAttemptDayEntity
import com.tbtechs.focusflow.data.local.entity.PhoneDayRollupEntity
import com.tbtechs.focusflow.data.local.entity.PhoneFirstAppDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupAppDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupSessionEntity

/**
 * Everything the phone-analytics code needs from storage. Keeping it behind an interface lets the writer, the
 * blocked-attempt sync and the report builders be unit-tested with an in-memory fake instead of Room.
 * Date arguments are inclusive `YYYY-MM-DD` strings; focus-session arguments are UTC ISO-8601 strings.
 */
interface PhoneBehaviourStore {
    suspend fun phoneDays(startDate: String, endDate: String): List<PhoneDayRollupEntity>
    suspend fun firstApps(startDate: String, endDate: String): List<PhoneFirstAppDayEntity>

    /** Atomically replaces the day row and all of that day's first-app rows. */
    suspend fun replacePhoneDay(day: PhoneDayRollupEntity, firstApps: List<PhoneFirstAppDayEntity>)

    /** Existing per-app rollup table (read only). */
    suspend fun appDays(startDate: String, endDate: String): List<UsageRollupAppDayEntity>

    /** Existing per-app session table (read only). */
    suspend fun appSessions(startDate: String, endDate: String): List<UsageRollupSessionEntity>

    suspend fun blockedDays(startDate: String, endDate: String): List<BlockedAttemptDayEntity>

    /** Stores each incoming row only if it has no existing row or more attempts than the existing one. */
    suspend fun mergeBlockedMax(rows: List<BlockedAttemptDayEntity>)

    /** Existing focus-session DAO (read only). */
    suspend fun focusSessions(startIso: String, endIso: String): List<SessionOverrideCountRow>

    /** Earliest date present in either `phone_day_rollup` or `usage_rollup_day`. */
    suspend fun earliestDate(): String?

    /** Deletes phone-behaviour rows older than [cutoffDate] (exclusive). */
    suspend fun prune(cutoffDate: String)
}

/**
 * The "max" merge rule for blocked attempts, kept pure so the Room store and the test fake share one implementation.
 * Returns the incoming rows that must be written: those with no existing row, or with strictly more attempts.
 * If [incoming] repeats a (date, package) key, only its highest-attempt row is considered.
 */
internal fun blockedRowsToWrite(
    existing: List<BlockedAttemptDayEntity>,
    incoming: List<BlockedAttemptDayEntity>,
): List<BlockedAttemptDayEntity> {
    val existingAttempts = existing.associate { (it.date to it.packageName) to it.attempts }
    val bestIncoming = LinkedHashMap<Pair<String, String>, BlockedAttemptDayEntity>()
    for (row in incoming) {
        val key = row.date to row.packageName
        val current = bestIncoming[key]
        if (current == null || row.attempts > current.attempts) bestIncoming[key] = row
    }
    return bestIncoming.values.filter { row ->
        val old = existingAttempts[row.date to row.packageName]
        old == null || row.attempts > old
    }
}

class RoomPhoneBehaviourStore(private val db: FocusFlowDatabase) : PhoneBehaviourStore {

    override suspend fun phoneDays(startDate: String, endDate: String): List<PhoneDayRollupEntity> =
        db.phoneBehaviourDao().getDays(startDate, endDate)

    override suspend fun firstApps(startDate: String, endDate: String): List<PhoneFirstAppDayEntity> =
        db.phoneBehaviourDao().getFirstApps(startDate, endDate)

    override suspend fun replacePhoneDay(day: PhoneDayRollupEntity, firstApps: List<PhoneFirstAppDayEntity>) {
        db.withTransaction {
            val dao = db.phoneBehaviourDao()
            dao.deleteFirstApps(day.date)
            dao.upsertDay(day)
            if (firstApps.isNotEmpty()) dao.upsertFirstApps(firstApps)
        }
    }

    override suspend fun appDays(startDate: String, endDate: String): List<UsageRollupAppDayEntity> =
        db.usageRollupDao().getAppDays(startDate, endDate)

    override suspend fun appSessions(startDate: String, endDate: String): List<UsageRollupSessionEntity> =
        db.usageRollupDao().getSessions(startDate, endDate)

    override suspend fun blockedDays(startDate: String, endDate: String): List<BlockedAttemptDayEntity> =
        db.phoneBehaviourDao().getBlocked(startDate, endDate)

    override suspend fun mergeBlockedMax(rows: List<BlockedAttemptDayEntity>) {
        if (rows.isEmpty()) return
        db.withTransaction {
            val dao = db.phoneBehaviourDao()
            val minDate = rows.minOf { it.date }
            val maxDate = rows.maxOf { it.date }
            val toWrite = blockedRowsToWrite(dao.getBlocked(minDate, maxDate), rows)
            if (toWrite.isNotEmpty()) dao.upsertBlocked(toWrite)
        }
    }

    override suspend fun focusSessions(startIso: String, endIso: String): List<SessionOverrideCountRow> =
        db.focusSessionDao().getSessionsWithOverrideCount(startIso, endIso)

    override suspend fun earliestDate(): String? {
        val phone = db.phoneBehaviourDao().earliestPhoneDate()
        val usage = db.usageRollupDao().getDays("0000-01-01", "9999-12-31").firstOrNull()?.date
        return listOfNotNull(phone, usage).minOrNull()
    }

    override suspend fun prune(cutoffDate: String) {
        db.withTransaction {
            val dao = db.phoneBehaviourDao()
            dao.deleteDaysBefore(cutoffDate)
            dao.deleteFirstAppsBefore(cutoffDate)
            dao.deleteBlockedBefore(cutoffDate)
        }
    }
}

/** Decodes a stored day. [firstApps] (package → unlock count) comes from `phone_first_app_day`, passed separately. */
fun PhoneDayRollupEntity.toPhoneDay(firstApps: Map<String, Int>): PhoneDay = PhoneDay(
    date = date,
    status = if (status == PhoneDayRollupEntity.COMPLETE) PhoneDayStatus.COMPLETE else PhoneDayStatus.PARTIAL,
    screenOnMs = screenOnMs,
    screenOnHourlyMs = PhoneCodec.decodeLongs(screenOnHourlyMs, 24),
    wakeCount = wakeCount,
    wakeHourly = PhoneCodec.decodeInts(wakeHourly, 24),
    unlockCount = unlockCount,
    unlockHourly = PhoneCodec.decodeInts(unlockHourly, 24),
    unlockSignal = unlockSignal,
    sessionBasis = if (unlockSignal) SessionBasis.UNLOCK else SessionBasis.SCREEN,
    sessionCount = sessionCount,
    sessionBucketCounts = PhoneCodec.decodeInts(sessionBucketCounts, PhoneBuckets.COUNT),
    sessionBucketMs = PhoneCodec.decodeLongs(sessionBucketMs, PhoneBuckets.COUNT),
    longestSessionMs = longestSessionMs,
    firstUnlockAtMs = firstUnlockAtMs,
    unlockGapSumMs = unlockGapSumMs,
    unlockGapCount = unlockGapCount,
    firstApps = firstApps,
    unlocksWithoutApp = unlocksWithoutApp,
)

/** Encodes a finished day. LIVE days (today) are never persisted, so they are rejected. */
fun PhoneDay.toEntity(computedAtMs: Long, coverageStartMs: Long?): PhoneDayRollupEntity {
    require(status != PhoneDayStatus.LIVE) { "LIVE days are never stored" }
    return PhoneDayRollupEntity(
        date = date,
        status = if (status == PhoneDayStatus.COMPLETE) PhoneDayRollupEntity.COMPLETE else PhoneDayRollupEntity.PARTIAL,
        behaviourVersion = PhoneThresholds.BEHAVIOUR_VERSION,
        computedAtMs = computedAtMs,
        coverageStartMs = coverageStartMs,
        screenOnMs = screenOnMs,
        screenOnHourlyMs = PhoneCodec.encodeLongs(screenOnHourlyMs),
        wakeCount = wakeCount,
        wakeHourly = PhoneCodec.encodeInts(wakeHourly),
        unlockCount = unlockCount,
        unlockHourly = PhoneCodec.encodeInts(unlockHourly),
        unlockSignal = unlockSignal,
        sessionCount = sessionCount,
        sessionBucketCounts = PhoneCodec.encodeInts(sessionBucketCounts),
        sessionBucketMs = PhoneCodec.encodeLongs(sessionBucketMs),
        longestSessionMs = longestSessionMs,
        firstUnlockAtMs = firstUnlockAtMs,
        unlockGapSumMs = unlockGapSumMs,
        unlockGapCount = unlockGapCount,
        unlocksWithoutApp = unlocksWithoutApp,
    )
}
