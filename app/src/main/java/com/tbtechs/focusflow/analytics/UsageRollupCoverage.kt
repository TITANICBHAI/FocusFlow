package com.tbtechs.focusflow.analytics

import com.tbtechs.focusflow.data.local.entity.UsageRollupDayEntity

object UsageRollupCoverage {
    fun status(
        earliestEventAtMs: Long?,
        dayStartMs: Long,
        unresolvedSessionStartedOnDate: Boolean,
    ): String = if (
        earliestEventAtMs != null &&
        earliestEventAtMs <= dayStartMs &&
        !unresolvedSessionStartedOnDate
    ) {
        UsageRollupDayEntity.COMPLETE
    } else {
        UsageRollupDayEntity.PARTIAL
    }
}
