package com.tbtechs.focusflow.di

import android.content.Context
import androidx.room.Room
import com.tbtechs.focusflow.notifications.AndroidReminderChainAlarmDriver
import com.tbtechs.focusflow.notifications.ReminderChainLedger
import com.tbtechs.focusflow.notifications.ReminderNotificationPublisher
import com.tbtechs.focusflow.notifications.LiveTaskStatusNotificationPublisher
import com.tbtechs.focusflow.notifications.ReminderChainScheduler
import com.tbtechs.focusflow.notifications.SharedPreferencesReminderLedgerStore
import com.tbtechs.focusflow.data.local.FocusFlowDatabase
import com.tbtechs.focusflow.data.backup.BackupSource
import com.tbtechs.focusflow.data.images.AndroidBitmapImageCodec
import com.tbtechs.focusflow.data.images.SubtaskImageStore
import com.tbtechs.focusflow.data.repository.FocusSessionRepository
import com.tbtechs.focusflow.data.repository.ForegroundServiceController
import com.tbtechs.focusflow.data.repository.AlarmRepository
import com.tbtechs.focusflow.data.repository.TaskAlarmReconciler
import com.tbtechs.focusflow.data.repository.BlockOverlayController
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.ReportNotesRepository
import com.tbtechs.focusflow.data.repository.TaskRepository
import com.tbtechs.focusflow.data.repository.GreyoutRepository
import com.tbtechs.focusflow.data.repository.UsageStatsRepository
import com.tbtechs.focusflow.analytics.AnalyticsProcessor
import com.tbtechs.focusflow.analytics.AchievementEngine
import com.tbtechs.focusflow.analytics.InsightEngine
import com.tbtechs.focusflow.analytics.AppUsageAndSessionTracker
import com.tbtechs.focusflow.analytics.UsageRollupCoordinator
import com.tbtechs.focusflow.analytics.UsageRollupWriter
import com.tbtechs.focusflow.analytics.UsageHistoryRepository
import com.tbtechs.focusflow.analytics.RoomUsageHistoryStore
import com.tbtechs.focusflow.analytics.DeviceUsageSource
import com.tbtechs.focusflow.analytics.DeviceUsageAppMetadata
import com.tbtechs.focusflow.analytics.UsageAppMetadata
import com.tbtechs.focusflow.analytics.detection.FindingDetectionRunner
import com.tbtechs.focusflow.domain.PinManager
import com.tbtechs.focusflow.domain.SchedulerEngine
import com.tbtechs.focusflow.data.repository.DayRatingRepository
import com.tbtechs.focusflow.data.repository.FindingRepository
import com.tbtechs.focusflow.data.repository.BehaviouralHypothesisRepository
import com.tbtechs.focusflow.data.repository.ClarifyingQuestionRepository
import com.tbtechs.focusflow.data.restore.RestoreGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.tbtechs.focusflow.ui.common.AppErrorEvents
import java.time.Clock
import java.time.ZoneId

/**
 * Manual DI singleton — the single source of truth for every repository
 * instance in the app.
 *
 * Initialised once in [FocusFlowApp.onCreate] via [AppModule.init].
 * After that, any ViewModel or service that needs a repository accesses it as:
 *
 * ```kotlin
 * val tasks = AppModule.taskRepository
 * val session = AppModule.focusSessionRepository
 * ```
 *
 * ## Adding a new repository
 * 1. Declare a `lateinit var` property below with `private set`.
 * 2. Instantiate it inside [init], after [database] is assigned.
 * 3. For repositories that wrap a DAO, call `database.xyzDao()`.
 *
 * ## Future: Hilt
 * If Hilt is adopted later, delete this object and replace call sites with
 * `@Inject constructor(...)`. The repository constructors are already written
 * for constructor injection, so the migration is mechanical.
 *
 * ## Thread safety
 * [init] is called from [FocusFlowApp.onCreate], which runs on the main thread
 * before any component starts. All properties are assigned exactly once before
 * any reader can access them, so no synchronisation is needed.
 */
object AppModule {

    lateinit var applicationContext: Context
        private set

    lateinit var restoreGate: RestoreGate
        private set

    private lateinit var applicationScope: CoroutineScope

    // ─── Database ─────────────────────────────────────────────────────────────

    lateinit var database: FocusFlowDatabase
        private set

    lateinit var subtaskImageStore: SubtaskImageStore
        private set

    // ─── Repositories ─────────────────────────────────────────────────────────

    /**
     * SharedPreferences-backed settings store. Used by [FocusSessionViewModel],
     * [SettingsViewModel], and the enforcement layer.
     */
    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var reportNotesRepository: ReportNotesRepository
        private set

    lateinit var foregroundServiceController: ForegroundServiceController
        private set

    lateinit var alarmRepository: AlarmRepository
        private set

    lateinit var taskAlarmReconciler: TaskAlarmReconciler
        private set

    lateinit var reminderChainScheduler: ReminderChainScheduler
        private set

    lateinit var reminderChainLedger: ReminderChainLedger
        private set

    lateinit var blockOverlayController: BlockOverlayController
        private set

    lateinit var pinManager: PinManager
        private set

    /**
     * Room-backed task store. Feeds [TaskViewModel.tasks] as a reactive Flow.
     * Also consumed by [SchedulerEngine] for conflict detection.
     */
    lateinit var taskRepository: TaskRepository
        private set

    /** Pure schedule transformation engine used by task mutations. */
    lateinit var schedulerEngine: SchedulerEngine
        private set

    /**
     * Room-backed focus session, override, and daily-completion store.
     * Feeds [FocusSessionViewModel.focusSession] and the streak counter.
     *
     * TODO (Track C): after [FocusSessionRepository.startFocusSession] /
     * [FocusSessionRepository.endFocusSession] writes to Room, also mirror
     * the enforcement SharedPreferences keys via [settingsRepository] so
     * [ForegroundTaskService] and [AppBlockerAccessibilityService] pick up
     * the change without a restart.
     */
    lateinit var focusSessionRepository: FocusSessionRepository
        private set

    lateinit var greyoutRepository: GreyoutRepository
        private set

    lateinit var usageStatsRepository: UsageStatsRepository
        private set

    lateinit var usageRollupWriter: UsageRollupWriter
        private set

    lateinit var usageRollupCoordinator: UsageRollupCoordinator
        private set

    lateinit var usageHistoryRepository: UsageHistoryRepository
        private set

    fun createAppUsageAndSessionTracker(context: Context): AppUsageAndSessionTracker =
        AppUsageAndSessionTracker(
            context = context,
            dailyUsageDao = database.dailyAppUsageDao(),
            sessionDao = database.appSessionDao(),
            legacyWriteAllowed = {
                usageHistoryRepository.shouldWriteLegacy(java.time.LocalDate.now().toString())
            },
        )

    lateinit var analyticsProcessor: AnalyticsProcessor
        private set

    lateinit var insightEngine: InsightEngine
        private set

    lateinit var achievementEngine: AchievementEngine
        private set

    lateinit var dayRatingRepository: DayRatingRepository
        private set

    lateinit var findingRepository: FindingRepository
        private set

    lateinit var behaviouralHypothesisRepository: BehaviouralHypothesisRepository
        private set

    lateinit var clarifyingQuestionRepository: ClarifyingQuestionRepository
        private set

    lateinit var findingDetectionRunner: FindingDetectionRunner
        private set

    // ─── Init ─────────────────────────────────────────────────────────────────

    /**
     * Builds every singleton. Called once from [FocusFlowApp.onCreate],
     * after [FocusFlowDatabase.prepareLegacyDatabase] has run. The complete
     * migration chain, including report notes and the active-session hardening,
     * is registered here.
     *
     * **Do not call this more than once.** Calling it a second time will throw
     * because the `lateinit` properties are already set.
     */
    fun init(context: Context) {
        val app = context.applicationContext
        applicationContext = app

        restoreGate = RestoreGate()
        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Room database — name must match the hybrid app's file ("focusday.db").
        database = Room.databaseBuilder(
            app,
            FocusFlowDatabase::class.java,
            FocusFlowDatabase.DB_NAME,
        )
            .addMigrations(
                FocusFlowDatabase.MIGRATION_0_1,
                FocusFlowDatabase.MIGRATION_1_2,
                FocusFlowDatabase.MIGRATION_2_3,
                FocusFlowDatabase.MIGRATION_3_4,
                FocusFlowDatabase.MIGRATION_4_5,
                FocusFlowDatabase.MIGRATION_5_6,
                FocusFlowDatabase.MIGRATION_6_7,
                FocusFlowDatabase.MIGRATION_7_8,
                FocusFlowDatabase.MIGRATION_8_9,
                FocusFlowDatabase.MIGRATION_9_10,
            )
            .addCallback(FocusFlowDatabase.DATABASE_CALLBACK)
            .build()

        subtaskImageStore = SubtaskImageStore(
            directory = java.io.File(app.filesDir, "task_images"),
            codec = AndroidBitmapImageCodec(),
        )

        // Repositories — order within this block does not matter.
        settingsRepository = SettingsRepository(app, restoreGate)
        reportNotesRepository = ReportNotesRepository(app, database.reportNotesDao())
        foregroundServiceController = ForegroundServiceController(app)
        alarmRepository = AlarmRepository(app, restoreGate)
        blockOverlayController = BlockOverlayController(app)
        pinManager = PinManager(app)

        taskRepository = TaskRepository(
            taskDao = database.taskDao(),
            restoreGate = restoreGate,
            imageStore = subtaskImageStore,
        )
        applicationScope.launch {
            runCatching { taskRepository.sweepOrphanedSubtaskImages() }
                .onFailure {
                    AppErrorEvents.report(
                        tag = "Subtask images",
                        message = "Unused task images could not be cleaned up.",
                        throwable = it,
                    )
                }
        }
        reminderChainScheduler = ReminderChainScheduler(AndroidReminderChainAlarmDriver(app))
        reminderChainLedger = ReminderChainLedger(SharedPreferencesReminderLedgerStore(app))
        taskAlarmReconciler = TaskAlarmReconciler(
            taskRepository = taskRepository,
            alarmRepository = alarmRepository,
            restoreGate = restoreGate,
            settingsRepository = settingsRepository,
            reminderChainScheduler = reminderChainScheduler,
            reminderChainLedger = reminderChainLedger,
            cancelReminderNotification = { slotId ->
                ReminderNotificationPublisher.cancel(app, slotId)
            },
            syncLiveTaskStatus = { tasks, nowMs ->
                LiveTaskStatusNotificationPublisher.sync(app, tasks, nowMs)
            },
        )
        schedulerEngine = SchedulerEngine()

        focusSessionRepository = FocusSessionRepository(
            focusSessionDao  = database.focusSessionDao(),
            focusOverrideDao = database.focusOverrideDao(),
            dailyCompletionDao = database.dailyCompletionDao(),
            taskDao          = database.taskDao(),
            restoreGate      = restoreGate,
        )

        greyoutRepository = GreyoutRepository(app)
        usageStatsRepository = UsageStatsRepository(app)
        usageHistoryRepository = UsageHistoryRepository(
            RoomUsageHistoryStore(
                context = app,
                dailyAppUsageDao = database.dailyAppUsageDao(),
                appSessionDao = database.appSessionDao(),
                usageRollupDao = database.usageRollupDao(),
                eventSource = usageStatsRepository,
                packageName = app.packageName,
            ),
        )
        usageRollupWriter = UsageRollupWriter(
            context = app,
            database = database,
            eventSource = usageStatsRepository,
            clock = Clock.systemDefaultZone(),
            zoneId = ZoneId.systemDefault(),
        )
        usageRollupCoordinator = UsageRollupCoordinator(
            context = app,
            applicationScope = applicationScope,
            writer = usageRollupWriter,
        ).also { it.registerCalendarChangeReceiver() }
        analyticsProcessor = AnalyticsProcessor(
            taskRepository = taskRepository,
            focusSessionRepository = focusSessionRepository,
            greyoutRepository = greyoutRepository,
            usageStatsRepository = usageStatsRepository,
            deviceUsageSource = DeviceUsageSource(
                appPackageName = app.packageName,
                eventSource = usageStatsRepository,
                usageHistoryRepository = usageHistoryRepository,
                persistRollupDays = { startDate, endDate, sessions, earliestEventAtMs, nowMs ->
                    usageRollupWriter.writeDaysFromEvents(
                        startDate = startDate,
                        endDate = endDate,
                        sessions = sessions,
                        earliestEventAtMs = earliestEventAtMs,
                        nowMs = nowMs,
                    )
                },
                resolveAppMetadata = { packageName ->
                    DeviceUsageAppMetadata(
                        appName = UsageAppMetadata.resolveAppName(app, packageName),
                        category = UsageAppMetadata.resolveCategory(app, packageName),
                    )
                },
                isLaunchable = { packageName ->
                    try {
                        app.packageManager.getLaunchIntentForPackage(packageName) != null
                    } catch (_: Exception) {
                        false
                    }
                },
            ),
        )
        insightEngine = InsightEngine(database.weeklyInsightDao())
        achievementEngine = AchievementEngine(
            focusSessionRepository = focusSessionRepository,
            achievementDao = database.achievementDao(),
        )

        dayRatingRepository = DayRatingRepository(database.dayRatingDao())
        findingRepository = FindingRepository(
            findingDao = database.findingDao(),
            ackDao = database.findingAcknowledgementDao(),
        )
        behaviouralHypothesisRepository = BehaviouralHypothesisRepository(
            database.behaviouralHypothesisDao(),
        )
        clarifyingQuestionRepository = ClarifyingQuestionRepository(
            database.clarifyingQuestionDao(),
        )
        findingDetectionRunner = FindingDetectionRunner(
            taskDao = database.taskDao(),
            focusSessionDao = database.focusSessionDao(),
            findingRepository = findingRepository,
            dailyAppUsageDao = database.dailyAppUsageDao(),
            appSessionDao = database.appSessionDao(),
            dayRatingDao = database.dayRatingDao(),
            clarifyingQuestionRepository = clarifyingQuestionRepository,
            usageHistoryRepository = usageHistoryRepository,
            clock = Clock.systemDefaultZone(),
            zoneId = ZoneId.systemDefault(),
        )

    }

    fun requestTaskAlarmReconciliation(reason: String) {
        if (!::taskAlarmReconciler.isInitialized) return
        applicationScope.launch {
            runCatching { taskAlarmReconciler.reconcile(reason) }
                .onFailure {
                    AppErrorEvents.report(
                        tag = "Task alarms",
                        message = "Task-end alarms could not be fully reconciled.",
                        throwable = it,
                    )
                }
        }
    }

    fun sweepStaleBackupBundles(cacheDir: java.io.File) {
        applicationScope.launch {
            runCatching { BackupSource.sweepStaleBundleFiles(cacheDir) }
                .onFailure {
                    AppErrorEvents.report(
                        tag = "Backup cleanup",
                        message = "Expired temporary backup files could not be removed.",
                        throwable = it,
                    )
                }
        }
    }
}
