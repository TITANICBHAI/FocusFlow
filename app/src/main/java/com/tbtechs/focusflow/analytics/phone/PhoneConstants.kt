package com.tbtechs.focusflow.analytics.phone

object PhoneBuckets {
    const val COUNT = 5
    /** Exclusive upper bounds of buckets 0..3, in ms. Bucket 4 is open-ended. */
    val BOUNDS_MS: LongArray = longArrayOf(30_000L, 60_000L, 300_000L, 1_200_000L)
    val LABELS: List<String> = listOf("Under 30s", "30s–1m", "1–5m", "5–20m", "20m+")

    fun indexFor(durationMs: Long): Int {
        val d = durationMs.coerceAtLeast(0L)
        for (i in BOUNDS_MS.indices) if (d < BOUNDS_MS[i]) return i
        return COUNT - 1
    }
}

object PhoneThresholds {
    const val BEHAVIOUR_VERSION = 1
    const val FIRST_APP_BEFORE_MS = 2_000L
    const val FIRST_APP_AFTER_MS = 15_000L
    const val UNLOCK_GAP_CAP_MS = 3L * 60 * 60 * 1000
    const val OPEN_SESSION_CAP_MS = 6L * 60 * 60 * 1000
    const val EVENT_LOOKBACK_MS = 6L * 60 * 60 * 1000
    const val MIN_OPEN_MS = 1_000L
    const val QUICK_CHECK_MAX_MS = 60_000L
    const val BACKFILL_DAYS = 14
    const val DAY_ROW_RETENTION_DAYS = 365
    const val REFLEX_MIN_ACTIVE_DAYS = 3
    const val REFLEX_MIN_OPENS_PER_DAY = 8.0
    const val REFLEX_MIN_SHORT_SHARE = 0.5
    const val RISING_MIN_RATIO = 1.5
    const val RISING_MIN_DELTA_MS = 15L * 60 * 1000
    const val RISING_MIN_PREVIOUS_MS = 5L * 60 * 1000
}

enum class PhonePeriod(val label: String) {
    MORNING("Morning"), AFTERNOON("Afternoon"), EVENING("Evening"), LATE_NIGHT("Late night");

    companion object {
        fun of(hour: Int): PhonePeriod = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..21 -> EVENING
            else -> LATE_NIGHT
        }
    }
}

enum class UsageCategory(val label: String, val isDistraction: Boolean) {
    SOCIAL("Social media", true),
    COMMUNICATION("Messaging", false),
    ENTERTAINMENT("Entertainment", true),
    GAMES("Games", true),
    EDUCATION("Education", false),
    PRODUCTIVITY("Productivity", false),
    NEWS_READING("News & reading", false),
    TOOLS("Tools & utilities", false),
    OTHER("Other", false),
}
