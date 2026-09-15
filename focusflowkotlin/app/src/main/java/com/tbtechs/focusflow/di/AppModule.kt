package com.tbtechs.focusflow.di

import android.content.Context
import androidx.room.Room
import com.tbtechs.focusflow.data.local.FocusFlowDatabase
import com.tbtechs.focusflow.data.repository.FocusSessionRepository
import com.tbtechs.focusflow.data.repository.ForegroundServiceController
import com.tbtechs.focusflow.data.repository.AlarmRepository
import com.tbtechs.focusflow.data.repository.BlockOverlayController
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.TaskRepository
import com.tbtechs.focusflow.data.repository.GreyoutRepository
import com.tbtechs.focusflow.data.repository.UsageStatsRepository
import com.tbtechs.focusflow.analytics.AnalyticsProcessor
import com.tbtechs.focusflow.analytics.AchievementEngine
import com.tbtechs.focusflow.analytics.InsightEngine
import com.tbtechs.focusflow.domain.PinManager

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

    // ─── Database ─────────────────────────────────────────────────────────────

    lateinit var database: FocusFlowDatabase
        private set

    // ─── Repositories ─────────────────────────────────────────────────────────

    /**
     * SharedPreferences-backed settings store. Used by [FocusSessionViewModel],
     * [SettingsViewModel], and the enforcement layer.
     */
    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var foregroundServiceController: ForegroundServiceController
        private set

    lateinit var alarmRepository: AlarmRepository
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

    lateinit var analyticsProcessor: AnalyticsProcessor
        private set

    lateinit var insightEngine: InsightEngine
        private set

    lateinit var achievementEngine: AchievementEngine
        private set

    // ─── Init ─────────────────────────────────────────────────────────────────

    /**
     * Builds every singleton. Called once from [FocusFlowApp.onCreate],
     * after [FocusFlowDatabase.prepareLegacyDatabase] has run. The complete
     * migration chain, including the achievement ledger, is registered here.
     *
     * **Do not call this more than once.** Calling it a second time will throw
     * because the `lateinit` properties are already set.
     */
    fun init(context: Context) {
        val app = context.applicationContext

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
            )
            .build()

        // Repositories — order within this block does not matter.
        settingsRepository = SettingsRepository(app)
        foregroundServiceController = ForegroundServiceController(app)
        alarmRepository = AlarmRepository(app)
        blockOverlayController = BlockOverlayController(app)
        pinManager = PinManager(app)

        taskRepository = TaskRepository(
            taskDao = database.taskDao(),
        )

        focusSessionRepository = FocusSessionRepository(
            focusSessionDao  = database.focusSessionDao(),
            focusOverrideDao = database.focusOverrideDao(),
            dailyCompletionDao = database.dailyCompletionDao(),
            taskDao          = database.taskDao(),
        )

        greyoutRepository = GreyoutRepository(app)
        usageStatsRepository = UsageStatsRepository(app)
        analyticsProcessor = AnalyticsProcessor(
            taskRepository = taskRepository,
            focusSessionRepository = focusSessionRepository,
            greyoutRepository = greyoutRepository,
            usageStatsRepository = usageStatsRepository,
        )
        insightEngine = InsightEngine(database.weeklyInsightDao())
        achievementEngine = AchievementEngine(
            focusSessionRepository = focusSessionRepository,
            achievementDao = database.achievementDao(),
        )
    }
}
