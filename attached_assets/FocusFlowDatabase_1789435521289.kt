package com.tbtechs.focusflow.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tbtechs.focusflow.data.local.dao.AchievementDao
import com.tbtechs.focusflow.data.local.dao.DailyCompletionDao
import com.tbtechs.focusflow.data.local.dao.FocusOverrideDao
import com.tbtechs.focusflow.data.local.dao.FocusSessionDao
import com.tbtechs.focusflow.data.local.dao.TaskDao
import com.tbtechs.focusflow.data.local.entity.AchievementEntity
import com.tbtechs.focusflow.data.local.entity.DailyCompletionEntity
import com.tbtechs.focusflow.data.local.entity.FocusOverrideEntity
import com.tbtechs.focusflow.data.local.entity.FocusSessionEntity
import com.tbtechs.focusflow.data.local.entity.TaskEntity

/**
 * Room database for FocusFlow.
 *
 * ## Covered tables (5 Room entities as of version 3)
 * | Entity | Table |
 * |---|---|
 * | [TaskEntity] | `tasks` |
 * | [FocusSessionEntity] | `focus_sessions` |
 * | [FocusOverrideEntity] | `focus_overrides` |
 * | [DailyCompletionEntity] | `daily_completions` |
 * | [AchievementEntity] | `achievements` |
 *
 * ## Out-of-scope tables (still no Room entity)
 * - `settings` — SharedPreferences facade; [SettingsRepository] owns it.
 * - `report_notes`, `weekly_insights` — pending Track D.
 *
 * ## Schema version history
 * | Version | Change |
 * |---|---|
 * | 1 | Original hybrid-app schema: `tasks` (without `focus_allowed_packages`), `focus_sessions`, `focus_overrides`, `daily_completions`. |
 * | 2 | `ALTER TABLE tasks ADD COLUMN focus_allowed_packages TEXT`. |
 * | 3 | `CREATE TABLE IF NOT EXISTS achievements (id TEXT PRIMARY KEY, earned_at TEXT NOT NULL)`. The table already exists in the raw SQLite file written by the hybrid app's `initSchema`; `IF NOT EXISTS` makes [MIGRATION_2_3] safe to run on both new and migrated databases. |
 */
@Database(
    entities = [
        TaskEntity::class,
        FocusSessionEntity::class,
        FocusOverrideEntity::class,
        DailyCompletionEntity::class,
        AchievementEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class FocusFlowDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun focusOverrideDao(): FocusOverrideDao
    abstract fun dailyCompletionDao(): DailyCompletionDao
    abstract fun achievementDao(): AchievementDao

    companion object {

        const val DB_NAME = "focusday.db"
        private const val TAG = "FocusFlowDatabase"

        // ─── Migrations ───────────────────────────────────────────────────────

        /** Baseline schema — creates the 4 core tables. Rarely called; see class KDoc. */
        val MIGRATION_0_1: Migration = object : Migration(0, 1) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tasks` (
                        `id`               TEXT NOT NULL,
                        `title`            TEXT NOT NULL,
                        `description`      TEXT,
                        `start_time`       TEXT NOT NULL,
                        `end_time`         TEXT NOT NULL,
                        `duration_minutes` INTEGER NOT NULL,
                        `status`           TEXT NOT NULL DEFAULT 'scheduled',
                        `priority`         TEXT NOT NULL DEFAULT 'medium',
                        `tags`             TEXT NOT NULL DEFAULT '[]',
                        `reminders`        TEXT NOT NULL DEFAULT '[]',
                        `color`            TEXT NOT NULL DEFAULT '#6366f1',
                        `focus_mode`       INTEGER NOT NULL DEFAULT 0,
                        `created_at`       TEXT NOT NULL,
                        `updated_at`       TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_tasks_start_time` ON `tasks` (`start_time`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_tasks_status` ON `tasks` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_tasks_status_end` ON `tasks` (`status`, `end_time`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `focus_sessions` (
                        `id`               INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `task_id`          TEXT NOT NULL,
                        `started_at`       TEXT NOT NULL,
                        `ended_at`         TEXT,
                        `is_active`        INTEGER NOT NULL DEFAULT 1,
                        `allowed_packages` TEXT NOT NULL DEFAULT '[]'
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_focus_sessions_task_active` ON `focus_sessions` (`task_id`, `is_active`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_focus_sessions_started_at` ON `focus_sessions` (`started_at`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `focus_overrides` (
                        `id`            INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `task_id`       TEXT NOT NULL,
                        `app_name`      TEXT NOT NULL,
                        `overridden_at` TEXT NOT NULL,
                        `reason`        TEXT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_focus_overrides_overridden_at` ON `focus_overrides` (`overridden_at`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `daily_completions` (
                        `date`      TEXT NOT NULL,
                        `completed` INTEGER NOT NULL DEFAULT 0,
                        `total`     INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`date`)
                    )
                """.trimIndent())
            }
        }

        /**
         * Adds `focus_allowed_packages TEXT` to `tasks`.
         * PRAGMA-guarded: safe to run when the hybrid app's inline migration
         * already created the column.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(`tasks`)")
                val columnExists = cursor.use { c ->
                    val nameIndex = c.getColumnIndexOrThrow("name")
                    generateSequence { if (c.moveToNext()) c.getString(nameIndex) else null }
                        .any { it == "focus_allowed_packages" }
                }
                if (!columnExists) {
                    db.execSQL("ALTER TABLE `tasks` ADD COLUMN `focus_allowed_packages` TEXT")
                }
            }
        }

        /**
         * Adds the `achievements` table.
         *
         * The hybrid app's `initSchema` already created this table, so
         * `CREATE TABLE IF NOT EXISTS` is intentional — it is safe when the
         * table already exists and still creates it for fresh Kotlin installs.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `achievements` (
                        `id`        TEXT NOT NULL,
                        `earned_at` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        // ─── Hybrid-app bootstrap ─────────────────────────────────────────────

        /**
         * Must be called before `Room.databaseBuilder()`.
         *
         * Stamps `user_version = 1` on a hybrid-app `focusday.db` (which has
         * `user_version = 0`) so Room enters its `onUpgrade` path instead of
         * `onCreate`, preserving all existing user data.
         */
        fun prepareLegacyDatabase(context: Context) {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) {
                Log.d(TAG, "prepareLegacyDatabase: $DB_NAME not found — fresh install, skipping.")
                return
            }
            runCatching {
                val rawDb = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                )
                rawDb.use { db ->
                    val currentVersion = db.version
                    Log.d(TAG, "prepareLegacyDatabase: $DB_NAME user_version=$currentVersion")
                    if (currentVersion >= 1) return
                    db.beginTransaction()
                    try {
                        val cursor = db.rawQuery("PRAGMA table_info(tasks)", null)
                        val columnExists = cursor.use { c ->
                            val nameIndex = c.getColumnIndex("name")
                            if (nameIndex == -1) return@use false
                            generateSequence { if (c.moveToNext()) c.getString(nameIndex) else null }
                                .any { it == "focus_allowed_packages" }
                        }
                        if (!columnExists) {
                            db.execSQL("ALTER TABLE tasks ADD COLUMN focus_allowed_packages TEXT")
                        }
                        db.version = 1
                        db.setTransactionSuccessful()
                        Log.d(TAG, "prepareLegacyDatabase: user_version set to 1.")
                    } finally {
                        db.endTransaction()
                    }
                }
            }.onFailure { e ->
                Log.e(TAG, "prepareLegacyDatabase failed: ${e.message}", e)
            }
        }
    }
}
