package com.tbtechs.focusflow

import android.app.Application
import com.tbtechs.focusflow.background.UsageMaintenanceWorker
import com.tbtechs.focusflow.data.local.FocusFlowDatabase
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.data.repository.StartupLogger
import com.tbtechs.focusflow.notifications.NotificationChannels
import com.tbtechs.focusflow.enforcement.DayRatingNotificationScheduler

/**
 * Application subclass for FocusFlow.
 *
 * **Must be declared in AndroidManifest.xml:**
 * ```xml
 * <application
 *     android:name=".FocusFlowApp"
 *     ... >
 * ```
 *
 * ## Startup order — matters, do not reorder
 *
 * 1. [FocusFlowDatabase.prepareLegacyDatabase] — must run before Room opens
 *    the database. Stamps the hybrid-app `focusday.db` (user_version = 0) to
 *    user_version = 1 so Room enters its `onUpgrade` path instead of `onCreate`,
 *    preserving all existing user data. No-op on fresh installs.
 *
 * 2. [AppModule.init] — builds the Room database (Room.databaseBuilder runs
 *    here) and wires every repository singleton. Must come after step 1.
 *
 * 3. [NotificationChannels.createAll] — registers the app's notification
 *    channels. Channels are sticky on the OS side so this is idempotent, but
 *    it must happen before any code posts a notification. The channels owned
 *    by the enforcement layer (focusday_foreground, task_alarm, etc.) are
 *    created by their respective components; only the three app-level channels
 *    (task-reminders, morning-digest, weekly-report) are created here.
 */
class FocusFlowApp : Application() {

    override fun onCreate() {
        super.onCreate()
        StartupLogger.initialize(this)
        StartupLogger.info("FocusFlowApp", "Application startup began")

        // 1. Pre-migrate the legacy SQLite database before Room touches it.
        StartupLogger.info("Room", "Preparing legacy database before Room initialization")
        FocusFlowDatabase.prepareLegacyDatabase(this)

        // 2. Build the Room database and wire all repository singletons.
        StartupLogger.info("Room", "Initializing Room database and repositories")
        AppModule.init(this)
        AppModule.sweepStaleBackupBundles(cacheDir)

        // 3. One-time migration: copy the legacy settings blob to SharedPreferences.
        //    Report notes remain in Room and are upgraded by MIGRATION_4_5.
        StartupLogger.info("Migration", "Checking legacy settings migration")
        FocusFlowDatabase.migrateSettingsBlobToSharedPrefs(this, AppModule.database)

        // 4. Register app-level notification channels.
        StartupLogger.info("Notifications", "Creating application notification channels")
        NotificationChannels.createAll(this)
        DayRatingNotificationScheduler.ensureScheduled(this)
        UsageMaintenanceWorker.enqueuePeriodic(this)
        AppModule.usageRollupCoordinator.trigger("app_open")
        StartupLogger.info("FocusFlowApp", "Application startup completed")
    }
}
