package com.tbtechs.focusflow.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tbtechs.focusflow.data.local.dao.DailyCompletionDao
import com.tbtechs.focusflow.data.local.dao.AchievementDao
import com.tbtechs.focusflow.data.local.dao.FocusOverrideDao
import com.tbtechs.focusflow.data.local.dao.FocusSessionDao
import com.tbtechs.focusflow.data.local.dao.TaskDao
import com.tbtechs.focusflow.data.local.dao.WeeklyInsightDao
import com.tbtechs.focusflow.data.local.entity.DailyCompletionEntity
import com.tbtechs.focusflow.data.local.entity.AchievementEntity
import com.tbtechs.focusflow.data.local.entity.FocusOverrideEntity
import com.tbtechs.focusflow.data.local.entity.FocusSessionEntity
import com.tbtechs.focusflow.data.local.entity.TaskEntity
import com.tbtechs.focusflow.data.local.entity.WeeklyInsightEntity

/**
 * Room database for FocusFlow.
 *
 * ## Covered tables (6 Room entities)
 * | Entity | Table |
 * |---|---|
 * | [TaskEntity] | `tasks` |
 * | [FocusSessionEntity] | `focus_sessions` |
 * | [FocusOverrideEntity] | `focus_overrides` |
 * | [DailyCompletionEntity] | `daily_completions` |
 * | [AchievementEntity] | `achievements` |
 *
 * ## Out-of-scope tables (no Room entities — not managed here)
 * - `settings` — single JSON-blob row; handled by [SettingsRepository] via SharedPreferences.
 * - `report_notes` — still handled outside Room.
 *
 * ## Database file
 * The hybrid React-Native app stored data in `focusday.db` (not `focusflow.db`).
 * Pass this name to `Room.databaseBuilder()` to open the same file.
 *
 * ## Migration strategy
 *
 * ### Schema version history
 * | Version | Change |
 * |---|---|
 * | 1 | Original schema: `tasks` (without `focus_allowed_packages`), `focus_sessions`, `focus_overrides`, `daily_completions`, plus `settings`/`report_notes`/`achievements`/`weekly_insights` (not Room-managed). |
 * | 2 | `ALTER TABLE tasks ADD COLUMN focus_allowed_packages TEXT` — the column added by the hybrid app's inline try/catch migration in `initSchema`. |
 * | 3 | `CREATE TABLE IF NOT EXISTS achievements (id TEXT PRIMARY KEY, earned_at TEXT NOT NULL)`. |
 * | 4 | `weekly_insights` becomes Room-managed. |
 *
 * ### Hybrid-app database bootstrap — IMPORTANT
 * The hybrid app used expo-sqlite and **never set SQLite `user_version`**, so
 * existing user databases have `user_version = 0`. Android's SQLiteOpenHelper
 * calls `onCreate()` (not `onUpgrade()`) when it sees `user_version = 0`,
 * bypassing the migration chain entirely.
 *
 * **Before** calling `Room.databaseBuilder()`, call [prepareLegacyDatabase]:
 *
 * ```kotlin
 * // In di/AppModule.kt, FocusFlowApp.onCreate(), or wherever the DB singleton is built:
 * FocusFlowDatabase.prepareLegacyDatabase(context)
 * val db = Room.databaseBuilder(context, FocusFlowDatabase::class.java, DB_NAME)
 *     .addMigrations(MIGRATION_0_1, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
 *     .build()
 * ```
 *
 * [prepareLegacyDatabase] opens the raw SQLite file, adds the
 * `focus_allowed_packages` column if it is absent, and sets `user_version = 1`
 * so Room subsequently calls `onUpgrade(db, 1, 3)` → [MIGRATION_1_2] and
 * [MIGRATION_2_3] run → schema is validated against version 3.
 *
 * ### [MIGRATION_0_1]
 * Baseline schema (version 1, no `focus_allowed_packages`). This migration
 * is included for completeness; in practice it **does not run** for hybrid-app
 * users (they land at version 1 via [prepareLegacyDatabase]) or for fresh
 * installs (Room calls `onCreate()` directly and creates the full version-2
 * schema). It would only execute for a device that somehow holds version 1
 * without having passed through [prepareLegacyDatabase].
 *
 * ### [MIGRATION_1_2]
 * Adds `focus_allowed_packages TEXT` to `tasks`. Guards against the column
 * already existing (hybrid-app users whose inline migration had already run)
 * via `PRAGMA table_info`.
 */
@Database(
    entities = [
        TaskEntity::class,
        FocusSessionEntity::class,
        FocusOverrideEntity::class,
        DailyCompletionEntity::class,
        AchievementEntity::class,
        WeeklyInsightEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class FocusFlowDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun focusOverrideDao(): FocusOverrideDao
    abstract fun dailyCompletionDao(): DailyCompletionDao
    abstract fun achievementDao(): AchievementDao
    abstract fun weeklyInsightDao(): WeeklyInsightDao

    companion object {

        const val DB_NAME = "focusday.db"

        private const val TAG = "FocusFlowDatabase"

        // ─── Migrations ───────────────────────────────────────────────────────

        /**
         * Baseline schema — version 1 (tasks table without `focus_allowed_packages`).
         * Uses `CREATE TABLE IF NOT EXISTS` so it is safe to run against a
         * database that already has the tables (existing hybrid-app databases).
         * Indices use the exact names from `initSchema` in `database.ts` so
         * Room's schema-hash validation finds them.
         *
         * NOTE: This migration **does not run automatically** for hybrid-app
         * users (see class-level KDoc). It is retained as a documented baseline.
         */
        val MIGRATION_0_1: Migration = object : Migration(0, 1) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ── tasks (v1 schema — no focus_allowed_packages) ─────────────
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

                // ── focus_sessions ─────────────────────────────────────────────
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

                // ── focus_overrides ────────────────────────────────────────────
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

                // ── daily_completions ──────────────────────────────────────────
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
         * Adds `focus_allowed_packages TEXT` to the `tasks` table.
         *
         * Guarded by a `PRAGMA table_info` check: hybrid-app users whose inline
         * `initSchema` migration had already run will have the column; the guard
         * prevents the `ALTER TABLE` from throwing a "duplicate column" error.
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

        /** Adds the existing hybrid-app achievement ledger to Room. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `achievements` (
                        `id` TEXT NOT NULL,
                        `earned_at` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        /** Adds the existing hybrid-app weekly standout ledger to Room. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `weekly_insights` (
                        `week_start` TEXT NOT NULL,
                        `insight_id` TEXT NOT NULL,
                        `selected_at` TEXT NOT NULL,
                        PRIMARY KEY(`week_start`)
                    )
                """.trimIndent())
            }
        }

        // ─── Hybrid-app bootstrap ─────────────────────────────────────────────

        /**
         * **Must be called before `Room.databaseBuilder()`.**
         *
         * The hybrid React-Native app (expo-sqlite) left the SQLite `user_version`
         * at 0. Android's SQLiteOpenHelper treats `user_version = 0` as "brand-new
         * database" and calls `onCreate()` — bypassing the migration chain.
         * Room's `onCreate()` then runs `CREATE TABLE IF NOT EXISTS` with the
         * full version-2 schema (which includes `focus_allowed_packages`). If the
         * existing table is missing that column, Room's schema validation throws.
         *
         * This function pre-migrates the raw SQLite file so Room sees
         * `user_version = 1` and runs `onUpgrade(db, 1, 2)` → [MIGRATION_1_2]:
         *
         * 1. If `focusday.db` does not exist: returns immediately (fresh install;
         *    Room will call `onCreate()` and create everything from scratch).
         * 2. If `user_version = 0`:
         *    a. Adds `focus_allowed_packages TEXT` if absent (handles users whose
         *       hybrid-app inline migration never ran, or was rolled back).
         *    b. Sets `user_version = 1` so Room's `onUpgrade` path runs.
         * 3. If `user_version ≥ 1`: returns immediately (Room handles it).
         *
         * Failures are logged and swallowed — if the pre-migration cannot run,
         * Room will open the database anyway and fail at schema validation, which
         * surfaces as a startup crash with a clear error message.
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
                    /* factory = */ null,
                    SQLiteDatabase.OPEN_READWRITE,
                )
                rawDb.use { db ->
                    val currentVersion = db.version
                    Log.d(TAG, "prepareLegacyDatabase: $DB_NAME user_version=$currentVersion")

                    if (currentVersion >= 1) {
                        // Room will run normal migration path; nothing to do.
                        return
                    }

                    // user_version == 0 — hybrid-app database.
                    // Add the column if absent, then stamp version = 1.
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
                            Log.d(TAG, "prepareLegacyDatabase: adding focus_allowed_packages column.")
                            db.execSQL("ALTER TABLE tasks ADD COLUMN focus_allowed_packages TEXT")
                        } else {
                            Log.d(TAG, "prepareLegacyDatabase: focus_allowed_packages already present.")
                        }
                        db.version = 1  // Room will now call onUpgrade(db, 1, 2).
                        db.setTransactionSuccessful()
                        Log.d(TAG, "prepareLegacyDatabase: user_version set to 1.")
                    } finally {
                        db.endTransaction()
                    }
                }
            }.onFailure { e ->
                Log.e(TAG, "prepareLegacyDatabase: failed — Room will attempt its own schema resolution. Error: ${e.message}", e)
            }
        }
    }
}
