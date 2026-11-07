package com.tbtechs.focusflow.analytics.phone

import com.tbtechs.focusflow.data.local.dao.SessionOverrideCountRow
import com.tbtechs.focusflow.data.local.entity.BlockedAttemptDayEntity
import com.tbtechs.focusflow.data.local.entity.PhoneDayRollupEntity
import com.tbtechs.focusflow.data.local.entity.PhoneFirstAppDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupAppDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupSessionEntity

/** In-memory [PhoneBehaviourStore]. The blocked-attempt merge uses the same max rule as the Room store. */
class FakePhoneBehaviourStore : PhoneBehaviourStore {
    val days = mutableListOf<PhoneDayRollupEntity>()
    val firstAppRows = mutableListOf<PhoneFirstAppDayEntity>()
    val blockedRows = mutableListOf<BlockedAttemptDayEntity>()
    val appDayRows = mutableListOf<UsageRollupAppDayEntity>()
    val appSessionRows = mutableListOf<UsageRollupSessionEntity>()
    val focusRows = mutableListOf<SessionOverrideCountRow>()
    /** Stand-in for the `usage_rollup_day` dates that `earliestDate()` also considers. */
    val usageRollupDates = mutableListOf<String>()

    var replaceCalls = 0
    val pruneCutoffs = mutableListOf<String>()
    val mergeCalls = mutableListOf<List<BlockedAttemptDayEntity>>()

    private fun inRange(date: String, start: String, end: String) = date >= start && date <= end

    override suspend fun phoneDays(startDate: String, endDate: String) =
        days.filter { inRange(it.date, startDate, endDate) }.sortedBy { it.date }

    override suspend fun firstApps(startDate: String, endDate: String) =
        firstAppRows.filter { inRange(it.date, startDate, endDate) }

    override suspend fun replacePhoneDay(day: PhoneDayRollupEntity, firstApps: List<PhoneFirstAppDayEntity>) {
        replaceCalls++
        days.removeAll { it.date == day.date }
        days += day
        firstAppRows.removeAll { it.date == day.date }
        firstAppRows += firstApps
    }

    override suspend fun appDays(startDate: String, endDate: String) =
        appDayRows.filter { inRange(it.date, startDate, endDate) }

    override suspend fun appSessions(startDate: String, endDate: String) =
        appSessionRows.filter { inRange(it.localDate, startDate, endDate) }

    override suspend fun blockedDays(startDate: String, endDate: String) =
        blockedRows.filter { inRange(it.date, startDate, endDate) }

    override suspend fun mergeBlockedMax(rows: List<BlockedAttemptDayEntity>) {
        mergeCalls.add(rows)
        if (rows.isEmpty()) return
        val minDate = rows.minOf { it.date }
        val maxDate = rows.maxOf { it.date }
        val existing = blockedRows.filter { inRange(it.date, minDate, maxDate) }
        for (row in blockedRowsToWrite(existing, rows)) {
            blockedRows.removeAll { it.date == row.date && it.packageName == row.packageName }
            blockedRows += row
        }
    }

    override suspend fun focusSessions(startIso: String, endIso: String) = focusRows.toList()

    override suspend fun earliestDate(): String? = (days.map { it.date } + usageRollupDates).minOrNull()

    override suspend fun prune(cutoffDate: String) {
        pruneCutoffs += cutoffDate
        days.removeAll { it.date < cutoffDate }
        firstAppRows.removeAll { it.date < cutoffDate }
        blockedRows.removeAll { it.date < cutoffDate }
    }
}
