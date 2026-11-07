package com.tbtechs.focusflow.analytics.phone.insights

/**
 * Every number the insight engine uses lives here as a named constant (T13). Ratios are kept as
 * numerator / denominator integer pairs so that "exactly 1.4×" style boundaries are compared exactly.
 * Window bounds are "days back from today": `(7, 1)` = today−7 … today−1.
 */
object InsightThresholds {
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60L * MINUTE_MS

    // ---- windows (days back from `today`, inclusive) -------------------------------------------------
    const val RECENT_FROM = 7
    const val RECENT_TO = 1
    const val PRIOR_FROM = 28
    const val PRIOR_TO = 8
    const val LAST14_FROM = 14
    const val LAST14_TO = 1
    const val PRIOR14_FROM = 28
    const val PRIOR14_TO = 15
    const val WEEK_LAST_FROM = 14
    const val WEEK_LAST_TO = 8
    const val MONTH_FROM = 30
    const val MONTH_TO = 1
    const val MONTH_BEFORE_FROM = 60
    const val MONTH_BEFORE_TO = 31
    const val UNUSUAL_BASELINE_FROM = 15
    const val UNUSUAL_BASELINE_TO = 2

    // ---- shared ---------------------------------------------------------------------------------------
    const val HIGH_CONFIDENCE_FACTOR = 2
    const val MAX_INSIGHTS = 12
    const val MAX_NOTES = 2
    /** Screen-on hours counted as "late night": 10 PM – 5 AM. */
    val LATE_NIGHT_HOURS: List<Int> = listOf(22, 23, 0, 1, 2, 3, 4)

    // ---- 1 WEEKLY_SUMMARY -----------------------------------------------------------------------------
    const val WEEKLY_MIN_COMPLETE_DAYS = 4
    const val WEEKLY_MIN_UNLOCK_DAYS = 4
    /** Screen-time change at or beyond ±this many percent is UP / DOWN, otherwise FLAT (weekly and monthly). */
    const val SUMMARY_DIRECTION_PERCENT = 5
    const val WEEKLY_PRIORITY = 80

    // ---- 2 MONTHLY_SUMMARY ----------------------------------------------------------------------------
    const val MONTHLY_MIN_COMPLETE_DAYS = 20
    const val MONTHLY_BEFORE_MIN_COMPLETE_DAYS = 10
    const val MONTHLY_MIN_UNLOCK_DAYS = 4
    const val MONTHLY_TOP_APPS = 3
    const val MONTHLY_PRIORITY = 45

    // ---- 3 UNUSUAL_DAY --------------------------------------------------------------------------------
    const val UNUSUAL_MIN_BASELINE_DAYS = 7
    const val UNUSUAL_UP_NUM = 14
    const val UNUSUAL_UP_DEN = 10
    const val UNUSUAL_DOWN_NUM = 6
    const val UNUSUAL_DOWN_DEN = 10
    const val UNUSUAL_MIN_DELTA_MS = 45L * MINUTE_MS
    const val UNUSUAL_UP_PRIORITY = 90
    const val UNUSUAL_DOWN_PRIORITY = 55

    // ---- 4 APP_SURGE / APP_DROP -----------------------------------------------------------------------
    const val APP_MIN_RECENT_DAYS = 5
    const val APP_MIN_PRIOR_DAYS = 14
    const val SURGE_MIN_ACTIVE_DAYS = 3
    const val SURGE_RATIO_NUM = 3
    const val SURGE_RATIO_DEN = 2
    const val SURGE_MIN_DELTA_MS = 15L * MINUTE_MS
    const val SURGE_NEW_MIN_RECENT_MS = 20L * MINUTE_MS
    const val SURGE_MAX = 2
    const val SURGE_PRIORITY = 85
    const val DROP_MIN_PRIOR_MS = 20L * MINUTE_MS
    const val DROP_RATIO_NUM = 1
    const val DROP_RATIO_DEN = 2
    const val DROP_MIN_DELTA_MS = 15L * MINUTE_MS
    const val DROP_MAX = 1
    const val DROP_PRIORITY = 60

    // ---- 5 UNLOCKS_CHANGE -----------------------------------------------------------------------------
    const val UNLOCKS_MIN_RECENT_DAYS = 5
    const val UNLOCKS_MIN_PRIOR_DAYS = 14
    const val UNLOCKS_UP_NUM = 5
    const val UNLOCKS_UP_DEN = 4
    const val UNLOCKS_DOWN_NUM = 3
    const val UNLOCKS_DOWN_DEN = 4
    const val UNLOCKS_MIN_DELTA_PER_DAY = 10
    const val UNLOCKS_PRIORITY = 70

    // ---- 6 LATE_NIGHT_SHIFT ---------------------------------------------------------------------------
    const val LATE_MIN_RECENT_DAYS = 5
    const val LATE_MIN_PRIOR_DAYS = 14
    const val LATE_MIN_DELTA_MS = 30L * MINUTE_MS
    const val LATE_UP_RATIO_NUM = 3
    const val LATE_UP_RATIO_DEN = 2
    const val LATE_UP_SMALL_PRIOR_MS = 5L * MINUTE_MS
    const val LATE_UP_SMALL_PRIOR_MIN_RECENT_MS = 30L * MINUTE_MS
    const val LATE_DOWN_RATIO_NUM = 1
    const val LATE_DOWN_RATIO_DEN = 2
    const val LATE_UP_PRIORITY = 75
    const val LATE_DOWN_PRIORITY = 50

    // ---- 7 FIRST_APP_PATTERN --------------------------------------------------------------------------
    const val FIRST_APP_MIN_UNLOCKS = 30
    const val FIRST_APP_MIN_SHARE_PERCENT = 25
    const val FIRST_APP_SECOND_SHARE_PERCENT = 15
    const val FIRST_APP_PRIORITY = 65

    // ---- 8 CHECKING_HABIT -----------------------------------------------------------------------------
    const val CHECKING_MIN_SESSIONS = 50
    const val CHECKING_MIN_QUICK_SHARE_PERCENT = 50
    const val CHECKING_MIN_QUICK_PER_DAY = 10
    /** Session buckets 0 and 1 (under one minute) count as quick checks. */
    const val CHECKING_QUICK_BUCKETS = 2
    const val CHECKING_PRIORITY = 64

    // ---- 9 TIME_OF_DAY_HABIT --------------------------------------------------------------------------
    const val TOD_TOP_APPS = 3
    const val TOD_MIN_APP_MS = HOUR_MS
    const val TOD_MIN_SHARE_PERCENT = 55
    const val TOD_MAX = 2
    const val TOD_PRIORITY = 55

    // ---- 10 CONSISTENCY -------------------------------------------------------------------------------
    const val CONSISTENCY_MIN_DAYS = 10
    const val CONSISTENCY_STEADY_BELOW = 0.25
    const val CONSISTENCY_VARIABLE_BELOW = 0.5
    const val CONSISTENCY_TREND_MIN_DIFF = 0.1
    const val CONSISTENCY_FIRST_UNLOCK_MIN_DAYS = 10
    const val CONSISTENCY_REGULAR_MAX_STDEV_MIN = 45.0
    const val CONSISTENCY_VARIES_MIN_STDEV_MIN = 120.0
    const val CONSISTENCY_PRIORITY = 50

    // ---- 11 RECORD ------------------------------------------------------------------------------------
    const val RECORD_MIN_DAYS = 7
    const val RECORD_PHONE_SESSION_MIN_DAYS = 3
    const val RECORD_BUSIEST_HOUR_MIN_DAYS = 7
    const val RECORD_MAX = 7
    const val RECORD_PRIORITY = 40
    const val RECORD_NEW_BONUS = 5
    const val RECORD_NEW_WITHIN_DAYS = 7

    // ---- 12 FOCUS_TREND -------------------------------------------------------------------------------
    const val FOCUS_MIN_FOCUS_MS = HOUR_MS
    const val FOCUS_MIN_CHANGE_PERCENT = 20
    const val FOCUS_MIN_ENDED_SESSIONS = 3
    const val FOCUS_MIN_HELD_DIFF_POINTS = 15
    const val FOCUS_PRIORITY = 58

    // ---- 13 DATA_NOTE ---------------------------------------------------------------------------------
    const val NOTE_HISTORY_TARGET_DAYS = 14
    const val NOTE_HISTORY_PRIORITY = 10
    const val NOTE_UNLOCKS_MIN_WAKES_PER_DAY = 5
    const val NOTE_UNLOCKS_PRIORITY = 12

    // ---- helpers for tests and callers ----------------------------------------------------------------
    const val MINUTE = MINUTE_MS
    const val HOUR = HOUR_MS
}
