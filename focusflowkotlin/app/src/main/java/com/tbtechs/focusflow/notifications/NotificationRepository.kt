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
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

/**
 * Port of notificationService.ts.
 *
 * Notification delivery is injected because the current Kotlin scaffold has
 * native task-end alarms but no generic reminder receiver yet. The adapter is
 * responsible for persisting/scheduling [NotificationRequest] instances and
 * must not silently discard them.
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
            scheduleTaskRemindersUnlocked(task)
        }
    }

    suspend fun scheduleTaskRemindersBatch(tasks: List<Task>) {
        writeMutex.withLock {
            val uniqueTasks = tasks
                .associateBy { it.id }
                .values
                .toList()
            cancelTaskRemindersUnlocked(uniqueTasks.map { it.id })

            val actionable = uniqueTasks.filter { it.isActionable() }
            if (actionable.isEmpty() || !requestPermissions()) return@withLock

            val slotBudget = NotificationSlotBudget(
                remaining = max(
                    0,
                    MAX_SCHEDULED_NOTIFICATIONS - scheduler.getScheduledNotifications().size,
                ),
            )
            for (task in actionable) {
                scheduleTaskRemindersUnlocked(
                    task = task,
                    skipCancel = true,
                    permissionsGranted = true,
                    slotBudget = slotBudget,
                )
            }
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
            val scheduled = scheduler.getScheduledNotifications()
            val taskIds = scheduled
                .mapNotNull { it.data["taskId"]?.takeIf(String::isNotBlank) }
                .distinct()
            scheduler.cancelAll()
            taskIds.forEach { alarmRepository.cancelAlarm(it) }
        }
    }

    suspend fun cancelAllRemindersExcept(taskId: String) {
        writeMutex.withLock {
            val scheduled = scheduler.getScheduledNotifications()
            val toCancel = scheduled.filter { it.data["taskId"] != taskId }
            val taskIds = toCancel
                .mapNotNull { it.data["taskId"]?.takeIf(String::isNotBlank) }
                .distinct()
            toCancel.forEach { scheduler.cancel(it.identifier) }
            taskIds.forEach { alarmRepository.cancelAlarm(it) }
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

    private suspend fun scheduleTaskRemindersUnlocked(
        task: Task,
        skipCancel: Boolean = false,
        permissionsGranted: Boolean? = null,
        slotBudget: NotificationSlotBudget? = null,
    ) {
        if (!skipCancel) cancelTaskRemindersUnlocked(listOf(task.id))
        if (!task.isActionable()) return

        val granted = permissionsGranted ?: requestPermissions()
        if (!granted) return

        val budget = slotBudget ?: NotificationSlotBudget(
            remaining = max(
                0,
                MAX_SCHEDULED_NOTIFICATIONS - scheduler.getScheduledNotifications().size,
            ),
        )
        val now = clock.millis()
        val startMs = parseInstant(task.startTime).toEpochMilli()
        val endMs = parseInstant(task.endTime).toEpochMilli()
        val endLabel = formatTime(task.endTime)
        val durationLabel = formatDuration(task.durationMinutes)
        val preStart = listOf(
            ReminderSpec(-10 * 60_000L, "Starting in 10 min · ends at $endLabel · $durationLabel total", false),
            ReminderSpec(-5 * 60_000L, "Starting in 5 min · ends at $endLabel", false),
            ReminderSpec(-60_000L, "Starting in 1 min — get ready! Ends at $endLabel", false),
            ReminderSpec(0L, "$durationLabel session · ends at $endLabel — tap to open", true),
        )

        for (reminder in preStart) {
            val fireAt = startMs + reminder.offsetMs
            if (fireAt - now < 1_000L) continue
            schedule(
                NotificationRequest(
                    identifier = "${task.id}-pre${reminder.offsetMs}",
                    title = "🎯 ${task.title}",
                    body = reminder.body,
                    data = mapOf(
                        "taskId" to task.id,
                        "type" to if (reminder.isStart) "task-start" else "reminder",
                    ),
                    categoryIdentifier = if (reminder.isStart) "task-active" else "task-reminder",
                    channelId = NotificationChannels.TASK_REMINDERS,
                    trigger = NotificationTrigger.AtMillis(fireAt),
                ),
                budget,
            )
        }

        val midSession = listOf(
            ReminderSpec(15 * 60_000L, "15 minutes in — how's it going?", false),
            ReminderSpec(30 * 60_000L, "Half hour in — keep going!", false),
        )
        for (reminder in midSession) {
            val fireAt = startMs + reminder.offsetMs
            if (fireAt - now < 1_000L || fireAt >= endMs || endMs - fireAt < TEN_MINUTES_MS) {
                continue
            }
            schedule(
                NotificationRequest(
                    identifier = "${task.id}-mid${reminder.offsetMs}",
                    title = "🟢 ${task.title}",
                    body = reminder.body,
                    data = mapOf("taskId" to task.id, "type" to "checkin"),
                    categoryIdentifier = "task-active",
                    channelId = NotificationChannels.TASK_REMINDERS,
                    trigger = NotificationTrigger.AtMillis(fireAt),
                ),
                budget,
            )
        }

        val almostDone = endMs - 60_000L
        if (almostDone - now > 1_000L) {
            schedule(
                NotificationRequest(
                    identifier = "${task.id}-almost",
                    title = "⏳ ${task.title} — 1 minute left",
                    body = "Start wrapping up!",
                    data = mapOf("taskId" to task.id, "type" to "almost-done"),
                    categoryIdentifier = "task-active",
                    channelId = NotificationChannels.TASK_REMINDERS,
                    trigger = NotificationTrigger.AtMillis(almostDone),
                ),
                budget,
            )
        }

        if (endMs - now > 1_000L) {
            schedule(
                NotificationRequest(
                    identifier = "${task.id}-end",
                    title = "⏰ ${task.title} — Time's up!",
                    body = "Mark as done, or extend your session.",
                    data = mapOf("taskId" to task.id, "type" to "OVERRUN_CHECK"),
                    categoryIdentifier = "task-active",
                    channelId = NotificationChannels.TASK_REMINDERS,
                    trigger = NotificationTrigger.AtMillis(endMs),
                ),
                budget,
            )
        }

        if (endMs > now) {
            alarmRepository.scheduleAlarm(task.id, task.title, endMs)
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
        uniqueIds.forEach { alarmRepository.cancelAlarm(it) }
    }

    private suspend fun schedule(
        request: NotificationRequest,
        budget: NotificationSlotBudget?,
    ) {
        if (budget != null && budget.remaining <= 0) return
        scheduler.schedule(request)
        if (budget != null) budget.remaining--
    }

    private fun Task.isActionable() =
        status != TaskStatus.COMPLETED &&
            status != TaskStatus.SKIPPED &&
            status != TaskStatus.OVERDUE

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

    private fun formatTime(value: String): String =
        parseInstant(value).atZone(clock.zone).let {
            "%02d:%02d".format(it.hour, it.minute)
        }

    private fun formatDuration(minutes: Int): String {
        val hours = minutes / 60
        val remainder = minutes % 60
        return when {
            hours > 0 && remainder > 0 -> "${hours}h ${remainder}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
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
        private const val TEN_MINUTES_MS = 10 * 60_000L
    }
}

data class NotificationSlotBudget(var remaining: Int)

data class ReminderSpec(
    val offsetMs: Long,
    val body: String,
    val isStart: Boolean,
)

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