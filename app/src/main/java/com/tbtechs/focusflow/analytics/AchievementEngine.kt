package com.tbtechs.focusflow.analytics

import com.tbtechs.focusflow.data.local.dao.AchievementDao
import com.tbtechs.focusflow.data.repository.FocusSessionRepository
import com.tbtechs.focusflow.data.local.entity.AchievementEntity
import java.time.Duration
import java.time.Instant

data class AchievementDefinition(
    val id: String,
    val category: String,
    val title: String,
    val description: String,
    val icon: String,
    val hidden: Boolean = false,
    val condition: (lifetime: LifetimeStats, snapshot: AnalyticsSnapshot) -> Boolean,
)

data class AchievementState(
    val definitions: List<AchievementDefinition>,
    val earnedIds: List<String>,
    val newlyEarnedIds: List<String>,
)

/**
 * Achievement registry. Conditions intentionally mirror the completed
 * TypeScript engine, including the distinction between window and lifetime
 * metrics.
 */
val ACHIEVEMENTS: List<AchievementDefinition> = listOf(
    AchievementDefinition(
        id = "RESISTANCE_10_CLEAN_SESSIONS",
        category = "resistance",
        title = "Stayed with it",
        description = "10 focus sessions without a blocked-app override.",
        icon = "shield-checkmark-outline",
    ) { lifetime, _ -> lifetime.cleanSessions >= 10 },
    AchievementDefinition(
        id = "HONEST_ESTIMATOR",
        category = "honesty",
        title = "Honest estimator",
        description = "Your recent task estimates are usually within 15 minutes.",
        icon = "resize-outline",
    ) { _, snapshot ->
        val errors = snapshot.tasks.estimationErrorMinutes
        errors.size >= 5 &&
            errors.map { kotlin.math.abs(it) }.average() <= 15.0
    },
    AchievementDefinition(
        id = "PRESENCE_7_DAYS",
        category = "presence",
        title = "Showed up",
        description = "You scheduled work on all seven days of a week.",
        icon = "calendar-outline",
    ) { _, snapshot ->
        snapshot.window == "week" &&
            snapshot.tasks.byDayOfWeek.values.count { it.total > 0 } == 7
    },
    AchievementDefinition(
        id = "PATTERN_BREAKER",
        category = "pattern_breaking",
        title = "Pattern breaker",
        description = "No single blocked app accounts for most of your recent attempts.",
        icon = "git-compare-outline",
    ) { _, snapshot ->
        snapshot.blocking.totalAttempts >= 10 &&
            (snapshot.blocking.topAppShare ?: 1.0) < 0.5
    },
    AchievementDefinition(
        id = "QUIET_WIN",
        category = "hidden",
        title = "Quiet win",
        description = "A full hour of focus with no blocked-app attempts.",
        icon = "eye-off-outline",
        hidden = true,
    ) { lifetime, snapshot ->
        lifetime.totalFocusMinutes >= 60.0 &&
            snapshot.sessions.cleanCount > 0 &&
            snapshot.blocking.totalAttempts == 0
    },
    AchievementDefinition(
        id = "IRON_SESSION",
        category = "resistance",
        title = "Iron session",
        description = "Completed a focus session without opening a blocked app once.",
        icon = "flash-outline",
    ) { _, snapshot -> snapshot.sessions.cleanCount > 0 },
    AchievementDefinition(
        id = "THREE_WEEKS",
        category = "presence",
        title = "Three weeks",
        description = "You have kept your streak going for at least 21 days.",
        icon = "trophy-outline",
    ) { lifetime, _ -> lifetime.currentStreakDays >= 21 },
    AchievementDefinition(
        id = "LONG_GAME",
        category = "hidden",
        title = "The long game",
        description = "Finished three or more tasks well ahead of schedule.",
        icon = "hourglass-outline",
        hidden = true,
    ) { _, snapshot ->
        snapshot.tasks.estimationErrorMinutes.count { it <= -30.0 } >= 3
    },
    AchievementDefinition(
        id = "BACK_AGAIN",
        category = "presence",
        title = "Back again",
        description = "You returned after more than five days away.",
        icon = "return-up-forward-outline",
    ) { lifetime, snapshot ->
        gapDays(lifetime.lastSessionAt, snapshot.generatedAt)?.let { it > 5.0 } == true
    },
    AchievementDefinition(
        id = "RESET",
        category = "hidden",
        title = "Reset",
        description = "Came back after a long break and completed something.",
        icon = "refresh-outline",
        hidden = true,
    ) { lifetime, snapshot ->
        gapDays(lifetime.lastSessionAt, snapshot.generatedAt)?.let {
            it > 5.0 && snapshot.tasks.completed >= 1
        } == true
    },
)

fun evaluateAchievements(
    lifetime: LifetimeStats,
    snapshot: AnalyticsSnapshot,
): List<AchievementDefinition> =
    ACHIEVEMENTS.filter { it.condition(lifetime, snapshot) }

/**
 * Reads and writes through Room, preserving the TypeScript engine's
 * one-time earning and stable earned-ID ordering.
 */
suspend fun syncAchievements(
    lifetime: LifetimeStats,
    snapshot: AnalyticsSnapshot,
    achievementDao: AchievementDao,
    earnedAt: String = Instant.now().toString(),
): AchievementState {
    val earnedIds = dbGetEarnedAchievementIds(achievementDao)
    val earnedSet = earnedIds.toSet()
    val newlyEarnedIds = evaluateAchievements(lifetime, snapshot)
        .map { it.id }
        .filterNot(earnedSet::contains)

    dbRecordEarnedAchievements(achievementDao, newlyEarnedIds, earnedAt)

    val nextEarnedIds = (earnedIds + newlyEarnedIds).distinct()
    return AchievementState(
        definitions = ACHIEVEMENTS.filter { it.id in nextEarnedIds },
        earnedIds = nextEarnedIds,
        newlyEarnedIds = newlyEarnedIds,
    )
}

suspend fun dbGetEarnedAchievementIds(achievementDao: AchievementDao): List<String> =
    achievementDao.getEarnedIds()

suspend fun dbRecordEarnedAchievements(
    achievementDao: AchievementDao,
    ids: List<String>,
    earnedAt: String,
) {
    achievementDao.recordEarned(
        ids.map { id -> AchievementEntity(id = id, earnedAt = earnedAt) },
    )
}

private fun gapDays(lastSessionAt: String?, generatedAt: String): Double? {
    if (lastSessionAt == null) return null
    return runCatching {
        Duration.between(Instant.parse(lastSessionAt), Instant.parse(generatedAt))
            .toMillis() / (24.0 * 60.0 * 60.0 * 1000.0)
    }.getOrNull()
}

/**
 * Stats-facing facade around the pure achievement evaluator and Room ledger.
 */
class AchievementEngine(
    private val focusSessionRepository: FocusSessionRepository,
    private val achievementDao: AchievementDao,
) {
    suspend fun syncAchievements(snapshot: AnalyticsSnapshot): AchievementState =
        com.tbtechs.focusflow.analytics.syncAchievements(
            lifetime = focusSessionRepository.getLifetimeStats(),
            snapshot = snapshot,
            achievementDao = achievementDao,
            earnedAt = snapshot.generatedAt,
        )
}