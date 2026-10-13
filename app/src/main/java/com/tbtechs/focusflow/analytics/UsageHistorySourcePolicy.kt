package com.tbtechs.focusflow.analytics

enum class UsageHistoryReadPhase { SHADOW, CUTOVER }

enum class UsageHistoryConsumer {
    DEVICE_STATS,
    DETECTOR,
    RATING_ELIGIBILITY,
    DATA_HEALTH,
}

enum class UsageHistorySource {
    CURRENT_STATS,
    LEGACY,
    LIVE_PIPELINE,
    ROLLUP,
    PARTIAL_ROLLUP,
    ON_DEMAND_PIPELINE,
    MISSING,
    UNKNOWN,
    EXCLUDED,
}

/** Pure source selection contract shared by tests and the future cutover reader. */
object UsageHistorySourcePolicy {
    fun shouldWriteLegacy(cutoverDate: String?, today: String): Boolean {
        if (cutoverDate == null) return true
        return today < java.time.LocalDate.parse(cutoverDate).plusDays(14).toString()
    }

    fun selectDeviceStats(
        phase: UsageHistoryReadPhase,
        date: String,
        today: String,
        rollupStatus: String?,
        eventsAvailable: Boolean,
    ): UsageHistorySource {
        if (phase == UsageHistoryReadPhase.SHADOW) return UsageHistorySource.CURRENT_STATS
        if (date == today) {
            return if (eventsAvailable) UsageHistorySource.LIVE_PIPELINE else UsageHistorySource.UNKNOWN
        }
        return when {
            rollupStatus == "COMPLETE" -> UsageHistorySource.ROLLUP
            rollupStatus == "PARTIAL" -> UsageHistorySource.PARTIAL_ROLLUP
            eventsAvailable -> UsageHistorySource.ON_DEMAND_PIPELINE
            else -> UsageHistorySource.MISSING
        }
    }

    fun selectHistory(
        phase: UsageHistoryReadPhase,
        date: String,
        today: String,
        cutoverDate: String?,
        consumer: UsageHistoryConsumer,
        rollupStatus: String?,
        eventsAvailable: Boolean,
        liveHasSession: Boolean = false,
    ): UsageHistorySource {
        if (phase == UsageHistoryReadPhase.SHADOW || cutoverDate == null) {
            return UsageHistorySource.LEGACY
        }
        if (date < cutoverDate) return UsageHistorySource.LEGACY
        if (date == today) {
            if (consumer == UsageHistoryConsumer.DETECTOR) return UsageHistorySource.EXCLUDED
            return if (liveHasSession) UsageHistorySource.LIVE_PIPELINE else UsageHistorySource.MISSING
        }
        return when {
            rollupStatus == "COMPLETE" -> UsageHistorySource.ROLLUP
            rollupStatus == "PARTIAL" && consumer == UsageHistoryConsumer.DETECTOR ->
                UsageHistorySource.MISSING
            eventsAvailable -> UsageHistorySource.ON_DEMAND_PIPELINE
            else -> UsageHistorySource.MISSING
        }
    }

    fun crossesSeam(startDate: String, endDate: String, cutoverDate: String?): Boolean =
        cutoverDate != null && startDate < cutoverDate && cutoverDate <= endDate
}
