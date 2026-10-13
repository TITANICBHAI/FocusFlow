package com.tbtechs.focusflow.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.tbtechs.focusflow.analytics.ANALYTICS_YESTERDAY
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import com.tbtechs.focusflow.analytics.AnalyticsProcessor
import com.tbtechs.focusflow.analytics.InsightEngine
import com.tbtechs.focusflow.data.repository.AlarmRepository
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.TaskRepository
import com.tbtechs.focusflow.domain.Task
import com.tbtechs.focusflow.domain.TaskStatus
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

/**
 * Port of notificationService.ts.
 *
 * Production task reminders use [ReminderPlanner] and the single-alarm chain.
 * This injected scheduler interface remains for the repository's existing
 * digest, report, and one-shot notification API; AppModule does not install a
 * per-slot production scheduler.
 */
class NotificationRepository(
    context: Context,
    private val scheduler: NotificationScheduler,
    private val alarmRepository: AlarmRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    /**
     * Analytics/content dependencies are optional to preserve the existing
     * scheduling-only construction path. Content methods fail explicitly when
     * the analytics layer has not been wired.
     */
    private val analyticsProcessor: AnalyticsProcessor? = null,
    private val insightEngine: InsightEngine? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val taskRepository: TaskRepository? = null,
) {
    private val appContext = context.applicationContext
    private val writeMutex = Mutex()

    suspend fun requestPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    fun setupNotificationChannels() {
        NotificationChannels.createAll(appContext)
    }

    suspend fun scheduleTaskReminders(task: Task) {
        writeMutex.withLock {
            scheduleTaskRemindersUnlocked(listOf(task))
        }
    }

    suspend fun scheduleTaskRemindersBatch(tasks: List<Task>) {
        writeMutex.withLock {
            scheduleTaskRemindersUnlocked(tasks)
        }
    }

    suspend fun cancelTaskReminders(taskId: String) {
        writeMutex.withLock {
            cancelTaskRemindersUnlocked(listOf(taskId))
        }
    }

    suspend fun cancelTaskRemindersBatch(taskIds: List<String>) {
        writeMutex.withLock {
            cancelTaskRemindersUnlocked(taskIds)
        }
    }

    suspend fun cancelAllReminders() {
        writeMutex.withLock {
            scheduler.getScheduledNotifications()
            scheduler.cancelAll()
        }
    }

    suspend fun cancelAllRemindersExcept(taskId: String) {
        writeMutex.withLock {
            val scheduled = scheduler.getScheduledNotifications()
            val toCancel = scheduled.filter { it.data["taskId"] != taskId }
            toCancel.forEach { scheduler.cancel(it.identifier) }
        }
    }

    /** The native foreground service owns the persistent in-progress notification. */
    suspend fun dismissPersistentNotification() = Unit

    suspend fun scheduleStandaloneBlockExpiry(untilMs: Long, blockedCount: Int) {
        writeMutex.withLock {
            runCatching { scheduler.cancel(STANDALONE_EXPIRY_ID) }
            if (!requestPermissions()) return@withLock

            val warnAt = untilMs - FIVE_MINUTES_MS
            if (warnAt - clock.millis() <= 1_000L) return@withLock

            schedule(
                NotificationRequest(
                    identifier = STANDALONE_EXPIRY_ID,
                    title = "🔓 App Block Expiring Soon",
                    body = "Your block on $blockedCount app${if (blockedCount != 1) "s" else ""} expires in 5 minutes.",
                    data = mapOf("type" to "standalone-expiry"),
                    channelId = NotificationChannels.TASK_REMINDERS,
                    trigger = NotificationTrigger.AtMillis(warnAt),
                ),
                null,
            )
        }
    }

    suspend fun cancelStandaloneBlockExpiry() {
        runCatching { scheduler.cancel(STANDALONE_EXPIRY_ID) }
    }

    suspend fun scheduleMorningDigest(
        profile: NotificationUserProfile?,
        tasks: List<Task>,
    ) {
        if (profile?.wakeUpTime.isNullOrBlank() || !requestPermissions()) return

        val wake = parseTime(profile!!.wakeUpTime!!) ?: return
        val now = Instant.now(clock)
        val localNow = now.atZone(clock.zone)
        val tomorrow = localNow.toLocalDate()
            .plusDays(1)
            .atTime(wake.first, wake.second)
            .atZone(clock.zone)
            .toInstant()
        if (tomorrow.toEpochMilli() <= clock.millis()) return

        val completed = tasks.filter { it.status == TaskStatus.COMPLETED }
        val skipped = tasks.filter { it.status == TaskStatus.SKIPPED }
        val total = tasks.filter { it.status != TaskStatus.SKIPPED }
        val focusMin = completed.sumOf { it.durationMinutes }
        val focusHrs = focusMin / 60
        val focusRem = focusMin % 60
        val timeString = if (focusHrs > 0) {
            "$focusHrs${if (focusRem > 0) "h ${focusRem}m" else "h"}"
        } else {
            "${focusMin}m"
        }
        val firstName = profile.name
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?.takeIf(String::isNotBlank)
        val greeting = if (firstName != null) {
            "Good morning, $firstName! ☀️"
        } else {
            "Good morning! ☀️"
        }

        val body = if (total.isEmpty()) {
            "No tasks were scheduled yesterday. Ready to make today count?"
        } else {
            val names = completed.take(3).joinToString(", ") { it.title }
            val namesString = if (names.isNotEmpty()) {
                " · ✅ $names${if (completed.size > 3) " +${completed.size - 3} more" else ""}"
            } else {
                ""
            }
            val skippedString = if (skipped.isNotEmpty()) {
                " · ⏭ ${skipped.size} skipped"
            } else {
                ""
            }
            val time = if (focusMin > 0) " · $timeString focused" else ""
            "Yesterday: ${completed.size}/${total.size} tasks done$time$namesString$skippedString"
        }

        runCatching { scheduler.cancel(MORNING_DIGEST_ID) }
        scheduler.schedule(
            NotificationRequest(
                identifier = MORNING_DIGEST_ID,
                title = greeting,
                body = body,
                data = mapOf("type" to "morning-digest"),
                channelId = NotificationChannels.MORNING_DIGEST,
                trigger = NotificationTrigger.AtMillis(tomorrow.toEpochMilli()),
            ),
        )
    }

    suspend fun cancelMorningDigest() {
        runCatching { scheduler.cancel(MORNING_DIGEST_ID) }
    }

    suspend fun scheduleWeeklyReport(
        profile: NotificationUserProfile?,
        weeklyReportEnabled: Boolean,
    ) {
        runCatching { scheduler.cancel(WEEKLY_REPORT_ID) }
        if (!weeklyReportEnabled || profile?.weeklyReviewDay == null) return
        if (!requestPermissions()) return

        val wake = profile.wakeUpTime?.let(::parseTime)
        val hour = wake?.first ?: 9
        val minute = wake?.second ?: 0
        scheduler.schedule(
            NotificationRequest(
                identifier = WEEKLY_REPORT_ID,
                title = profile.name
                    ?.trim()
                    ?.split(Regex("\\s+"))
                    ?.firstOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?.let { "$it's weekly review 📊" }
                    ?: "Weekly review 📊",
                body = "Tap to see how your week went — focus time, streaks, and completed tasks.",
                data = mapOf("type" to "weekly-report"),
                channelId = NotificationChannels.WEEKLY_REPORT,
                trigger = NotificationTrigger.Weekly(
                    weekday = profile.weeklyReviewDay.expoWeekday,
                    hour = hour,
                    minute = minute,
                ),
            ),
        )
    }

    suspend fun cancelWeeklyReport() {
        runCatching { scheduler.cancel(WEEKLY_REPORT_ID) }
    }

    /**
     * Builds the body for the morning digest from yesterday's highest-priority
     * insight. A nothing-to-report card is intentionally not surfaced as the
     * digest body; it gets the short honest fallback instead.
     */
    suspend fun buildMorningDigestBody(): String {
        val processor = requireNotNull(analyticsProcessor) {
            "AnalyticsProcessor is required to build notification content"
        }
        val engine = requireNotNull(insightEngine) {
            "InsightEngine is required to build notification content"
        }
        val snapshot = processor.buildAnalyticsSnapshot(ANALYTICS_YESTERDAY)
        val insight = engine.buildInsights(snapshot)
            .firstOrNull { it.category != "nothing_to_report" }
        return insight?.body ?: MORNING_DIGEST_FALLBACK
    }

    /**
     * Builds the weekly report body and records the selected standout through
     * InsightEngine's weekly deduplication ledger.
     */
    suspend fun buildWeeklyReportBody(): String {
        val processor = requireNotNull(analyticsProcessor) {
            "AnalyticsProcessor is required to build notification content"
        }
        val engine = requireNotNull(insightEngine) {
            "InsightEngine is required to build notification content"
        }
        val snapshot = processor.buildAnalyticsSnapshot(ANALYTICS_WEEK)
        val standout = engine.syncWeeklyStandout(snapshot)
        return if (standout.category == "nothing_to_report") {
            WEEKLY_REPORT_FALLBACK
        } else {
            standout.body
        }
    }

    /**
     * Returns the seven local calendar days beginning tomorrow, matching the
     * "tomorrow + 6 days" definition in the reference plan.
     */
    suspend fun buildWeekAheadBody(): String? {
        val repository = requireNotNull(taskRepository) {
            "TaskRepository is required to build week-ahead notification content"
        }
        val now = Instant.now(clock).atZone(clock.zone)
        val start = now.toLocalDate()
            .plusDays(1)
            .atStartOfDay(clock.zone)
        val end = start.toLocalDate()
            .plusDays(6)
            .atTime(23, 59, 59, 999_999_999)
            .atZone(clock.zone)
        val tasks = repository.getTasksInDateRange(
            startISO = start.toInstant().toString(),
            endISO = end.toInstant().toString(),
        ).sortedBy { parseInstant(it.startTime) }
        val first = tasks.firstOrNull() ?: return null
        val firstStart = parseInstant(first.startTime).atZone(clock.zone)
        val firstTime = firstStart.format(
            DateTimeFormatter.ofPattern("HH:mm 'on' EEEE", Locale.getDefault()),
        )
        return "You have ${tasks.size} tasks scheduled. First up: ${first.title} at $firstTime."
    }

    suspend fun fireLateStartWarning(task: Task, minutesLate: Int) {
        if (!requestPermissions()) return
        scheduler.schedule(
            NotificationRequest(
                identifier = "${task.id}-late",
                title = "⏰ You're late: ${task.title}",
                body = "This task was supposed to start ${minutesLate}m ago.",
                data = mapOf(
                    "taskId" to task.id,
                    "type" to "LATE_START_WARNING",
                ),
                channelId = NotificationChannels.TASK_REMINDERS,
                categoryIdentifier = "task-reminder",
                trigger = NotificationTrigger.Immediate,
            ),
        )
    }

    private suspend fun scheduleTaskRemindersUnlocked(tasks: List<Task>) {
        val uniqueTasks = tasks
            .filter { it.id.isNotBlank() }
            .associateBy { it.id }
            .values
            .toList()
        cancelTaskRemindersUnlocked(uniqueTasks.map { it.id })
        if (uniqueTasks.isEmpty() || !requestPermissions()) return

        val budget = NotificationSlotBudget(
            remaining = max(
                0,
                MAX_SCHEDULED_NOTIFICATIONS - scheduler.getScheduledNotifications().size,
            ),
        )
        val plan = ReminderPlanner.plan(uniqueTasks, clock.millis(), clock.zone)
        for (slot in plan.filter { it.kind != ReminderKind.LIVE_STATUS_START }) {
            if (budget.remaining <= 0) break
            schedule(
                NotificationRequest(
                    identifier = slot.id,
                    title = slot.title,
                    body = slot.text,
                    data = mapOf(
                        "taskId" to slot.taskId,
                        "type" to slot.kind.notificationType,
                    ),
                    channelId = NotificationChannels.TASK_REMINDERS,
                    categoryIdentifier = slot.kind.categoryIdentifier,
                    trigger = NotificationTrigger.AtMillis(slot.triggerMs),
                ),
                budget,
            )
        }
    }

    private suspend fun cancelTaskRemindersUnlocked(taskIds: List<String>) {
        val uniqueIds = taskIds.filter(String::isNotBlank).distinct()
        if (uniqueIds.isEmpty()) return

        scheduler.getScheduledNotifications()
            .filter { notification ->
                uniqueIds.any { taskId ->
                    notification.identifier.startsWith("$taskId-")
                }
            }
            .forEach { scheduler.cancel(it.identifier) }
    }

    private suspend fun schedule(
        request: NotificationRequest,
        budget: NotificationSlotBudget?,
    ) {
        if (budget != null && budget.remaining <= 0) return
        scheduler.schedule(request)
        if (budget != null) budget.remaining--
    }

    private fun parseInstant(value: String): Instant =
        runCatching { Instant.parse(value) }.getOrElse {
            java.time.OffsetDateTime.parse(value).toInstant()
        }

    private fun parseTime(value: String): Pair<Int, Int>? {
        val parts = value.split(':')
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    companion object {
        const val REMINDER_CHANNEL_ID = NotificationChannels.TASK_REMINDERS
        const val MORNING_DIGEST_CHANNEL_ID = NotificationChannels.MORNING_DIGEST
        const val WEEKLY_REPORT_CHANNEL_ID = NotificationChannels.WEEKLY_REPORT
        const val ACHIEVEMENTS_CHANNEL_ID = NotificationChannels.ACHIEVEMENTS
        const val INSIGHTS_CHANNEL_ID = NotificationChannels.INSIGHTS
        const val RESISTANCE_CHANNEL_ID = NotificationChannels.RESISTANCE

        private const val STANDALONE_EXPIRY_ID = "standalone-expiry"
        private const val MORNING_DIGEST_ID = "morning-digest"
        private const val WEEKLY_REPORT_ID = "weekly-report"
        private const val MORNING_DIGEST_FALLBACK = "Ordinary day yesterday. You showed up."
        private const val WEEKLY_REPORT_FALLBACK = "Consistent week. Nothing stood out."
        private const val MAX_SCHEDULED_NOTIFICATIONS = 450
        private const val FIVE_MINUTES_MS = 5 * 60_000L
    }
}

data class NotificationSlotBudget(var remaining: Int)

data class NotificationUserProfile(
    val name: String? = null,
    val wakeUpTime: String? = null,
    val weeklyReviewDay: WeeklyReviewDay? = null,
)

enum class WeeklyReviewDay(val expoWeekday: Int) {
    SUN(1),
    MON(2),
    TUE(3),
    WED(4),
    THU(5),
    FRI(6),
    SAT(7),
}

data class NotificationRequest(
    val identifier: String,
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val channelId: String,
    val categoryIdentifier: String? = null,
    val trigger: NotificationTrigger,
)

sealed interface NotificationTrigger {
    data object Immediate : NotificationTrigger
    data class AtMillis(val epochMs: Long) : NotificationTrigger
    data class Weekly(val weekday: Int, val hour: Int, val minute: Int) : NotificationTrigger
}

data class ScheduledNotification(
    val identifier: String,
    val data: Map<String, String>,
)

/**
 * Native scheduling implementation boundary. A production implementation
 * persists requests through the app's AlarmManager/WorkManager notification
 * receiver; tests can use an in-memory implementation.
 */
interface NotificationScheduler {
    suspend fun getScheduledNotifications(): List<ScheduledNotification>
    suspend fun schedule(request: NotificationRequest)
    suspend fun cancel(identifier: String)
    suspend fun cancelAll()
}