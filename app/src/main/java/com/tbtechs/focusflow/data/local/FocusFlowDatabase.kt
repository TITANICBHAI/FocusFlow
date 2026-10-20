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
import com.tbtechs.focusflow.data.local.dao.ReportNotesDao
import com.tbtechs.focusflow.data.local.dao.DailyAppUsageDao
import com.tbtechs.focusflow.data.local.dao.AppSessionDao
import com.tbtechs.focusflow.data.local.dao.DayRatingDao
import com.tbtechs.focusflow.data.local.dao.FindingDao
import com.tbtechs.focusflow.data.local.dao.FindingAcknowledgementDao
import com.tbtechs.focusflow.data.local.dao.BehaviouralHypothesisDao
import com.tbtechs.focusflow.data.local.dao.ClarifyingQuestionDao
import com.tbtechs.focusflow.data.local.dao.UsageRollupDao
import com.tbtechs.focusflow.data.local.entity.DailyCompletionEntity
import com.tbtechs.focusflow.data.local.entity.AchievementEntity
import com.tbtechs.focusflow.data.local.entity.FocusOverrideEntity
import com.tbtechs.focusflow.data.local.entity.FocusSessionEntity
import com.tbtechs.focusflow.data.local.entity.TaskEntity
import com.tbtechs.focusflow.data.local.entity.WeeklyInsightEntity
import com.tbtechs.focusflow.data.local.entity.ReportNoteEntity
import com.tbtechs.focusflow.data.local.entity.DailyAppUsageEntity
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.DayRatingEntity
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import com.tbtechs.focusflow.data.local.entity.FindingAcknowledgementEntity
import com.tbtechs.focusflow.data.local.entity.BehaviouralHypothesisEntity
import com.tbtechs.focusflow.data.local.entity.ClarifyingQuestionEntity
import com.tbtechs.focusflow.data.local.entity.UsagePipelineStateEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupAppDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupSessionEntity
import com.tbtechs.focusflow.data.backup.LegacySettingsMigration
import com.tbtechs.focusflow.data.backup.LegacyPreferenceValue
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService

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
 * | [ReportNoteEntity] | `report_notes` |
 *
 * ## Out-of-scope tables (no Room entities — not managed here)
 * - `settings` — single JSON-blob row; handled by [SettingsRepository] via SharedPreferences.
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
 * | 1 | Original schema: `tasks` (without `focus_allowed_packages`), `focus_sessions`, `focus_overrides`, and `daily_completions`. |
 * | 2 | `tasks.focus_allowed_packages` is added. |
 * | 3 | The achievements ledger becomes Room-managed. |
 * | 4 | `weekly_insights` becomes Room-managed. |
 * | 5 | `report_notes` becomes Room-managed and active-session indexes are hardened. |
 * | 6 | Behavioural-intelligence tables, including legacy daily usage and app sessions, are added. |
 * | 7 | Shadow usage rollup tables and pipeline state are added. |
 * | 8 | Data-free bridge version; it must not create an upgrade-day cutover. |
 * | 9 | Stale cutover dates are cleared and missing pipeline state is seeded. |
 * | 10 | Adds JSON-encoded subtasks to tasks. |
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
 * so Room subsequently runs the registered migrations through version 10.
 *
 * ### Usage pipeline state on both install paths
 * A fresh database gets the complete schema from Room and the
 * [DATABASE_CALLBACK] inserts its initial pipeline-state row after table
 * creation. An upgrade from versions 6–8 passes through [MIGRATION_8_9],
 * which clears only stale cutover dates, resets their shadow start to the
 * current local date, and inserts the singleton row if it is missing. The
 * read model also performs an idempotent insert-if-missing before reading state.
 *
 * ### [MIGRATION_0_1]
 * Baseline schema (version 1, no `focus_allowed_packages`). This migration
 * is included for completeness; in practice it **does not run** for hybrid-app
 * users (they land at version 1 via [prepareLegacyDatabase]) or for fresh
 * installs (Room calls `onCreate()` directly and creates the full current
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
        ReportNoteEntity::class,
        DailyAppUsageEntity::class,
        AppSessionEntity::class,
        DayRatingEntity::class,
        FindingEntity::class,
        FindingAcknowledgementEntity::class,
        BehaviouralHypothesisEntity::class,
        ClarifyingQuestionEntity::class,
        UsagePipelineStateEntity::class,
        UsageRollupDayEntity::class,
        UsageRollupAppDayEntity::class,
        UsageRollupSessionEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class FocusFlowDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun focusOverrideDao(): FocusOverrideDao
    abstract fun dailyCompletionDao(): DailyCompletionDao
    abstract fun achievementDao(): AchievementDao
    abstract fun weeklyInsightDao(): WeeklyInsightDao
    abstract fun reportNotesDao(): ReportNotesDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao
    abstract fun appSessionDao(): AppSessionDao
    abstract fun dayRatingDao(): DayRatingDao
    abstract fun findingDao(): FindingDao
    abstract fun findingAcknowledgementDao(): FindingAcknowledgementDao
    abstract fun behaviouralHypothesisDao(): BehaviouralHypothesisDao
    abstract fun clarifyingQuestionDao(): ClarifyingQuestionDao
    abstract fun usageRollupDao(): UsageRollupDao

    companion object {

        const val DB_NAME = "focusday.db"

        private const val TAG = "FocusFlowDatabase"
        private const val SETTINGS_BLOB_MIGRATED_KEY = "_settings_blob_migrated"

        val DATABASE_CALLBACK: RoomDatabase.Callback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                ensurePipelineStateRow(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `idx_focus_sessions_one_active` " +
                        "ON `focus_sessions` (`is_active`) WHERE `is_active` = 1",
                )
            }
        }

        private fun ensurePipelineStateRow(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                INSERT OR IGNORE INTO `usage_pipeline_state`
                    (`id`, `cutover_date`, `pipeline_version`, `shadow_started_on`)
                VALUES (1, NULL, 1, date('now', 'localtime'))
                """.trimIndent(),
            )
        }

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

        /**
         * Makes report notes Room-managed and hardens active-session lookup.
         *
         * Existing RN databases already contain report_notes in this shape.
         * The guarded column repair also handles early files that predate the
         * updated_at column without dropping their notes.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `report_notes` (
                        `ref_date` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `updated_at` TEXT NOT NULL,
                        PRIMARY KEY(`ref_date`, `type`)
                    )
                """.trimIndent())

                val reportColumns = db.query("PRAGMA table_info(`report_notes`)")
                val hasUpdatedAt = reportColumns.use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    generateSequence {
                        if (cursor.moveToNext()) cursor.getString(nameIndex) else null
                    }.any { it == "updated_at" }
                }
                if (!hasUpdatedAt) {
                    db.execSQL("ALTER TABLE `report_notes` RENAME TO `report_notes_legacy`")
                    db.execSQL("""
                        CREATE TABLE `report_notes` (
                            `ref_date` TEXT NOT NULL,
                            `type` TEXT NOT NULL,
                            `note` TEXT NOT NULL,
                            `updated_at` TEXT NOT NULL,
                            PRIMARY KEY(`ref_date`, `type`)
                        )
                    """.trimIndent())
                    db.execSQL("""
                        INSERT INTO `report_notes` (`ref_date`, `type`, `note`, `updated_at`)
                        SELECT `ref_date`, `type`, `note`, ''
                        FROM `report_notes_legacy`
                    """.trimIndent())
                    db.execSQL("DROP TABLE `report_notes_legacy`")
                }

                // Preserve the newest active session if an older database has
                // duplicate active rows before adding the partial unique index.
                val activeIds = db.query(
                    "SELECT id FROM `focus_sessions` WHERE is_active = 1 ORDER BY id DESC",
                )
                val idsToDeactivate = buildList {
                    activeIds.use { cursor ->
                        val idIndex = cursor.getColumnIndexOrThrow("id")
                        if (cursor.moveToNext()) {
                            while (cursor.moveToNext()) add(cursor.getLong(idIndex))
                        }
                    }
                }
                idsToDeactivate.forEach { id ->
                    db.execSQL(
                        "UPDATE `focus_sessions` SET is_active = 0 WHERE id = ?",
                        arrayOf<Any?>(id),
                    )
                }

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_focus_sessions_active` " +
                        "ON `focus_sessions` (`is_active`, `id`)",
                )
            }
        }

        /** Adds the Phase 1 behavioural-intelligence tables. */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `daily_app_usage` (
                        `date` TEXT NOT NULL,
                        `package_name` TEXT NOT NULL,
                        `app_name` TEXT NOT NULL,
                        `category` TEXT,
                        `foreground_ms` INTEGER NOT NULL DEFAULT 0,
                        `hourly_ms` TEXT NOT NULL DEFAULT '',
                        `launch_count` INTEGER NOT NULL DEFAULT 0,
                        `last_used_at` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`date`, `package_name`)
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_dau_package_date` " +
                        "ON `daily_app_usage` (`package_name`, `date`)",
                )

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `app_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `package_name` TEXT NOT NULL,
                        `app_name` TEXT NOT NULL,
                        `started_at` INTEGER NOT NULL,
                        `ended_at` INTEGER NOT NULL,
                        `duration_ms` INTEGER NOT NULL,
                        `local_date` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_as_package_date` " +
                        "ON `app_sessions` (`package_name`, `local_date`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_as_started_at` " +
                        "ON `app_sessions` (`started_at`)",
                )

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `day_ratings` (
                        `date` TEXT NOT NULL,
                        `rating` INTEGER NOT NULL,
                        `context_tag` TEXT,
                        `note` TEXT,
                        `app_tags` TEXT NOT NULL DEFAULT '[]',
                        `word_tags` TEXT NOT NULL DEFAULT '[]',
                        `created_at` TEXT NOT NULL,
                        `updated_at` TEXT NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `findings` (
                        `id` TEXT NOT NULL,
                        `detection_type` TEXT NOT NULL,
                        `subject_package` TEXT,
                        `subject_app_name` TEXT,
                        `state` TEXT NOT NULL DEFAULT 'detected',
                        `evidence_fingerprint` TEXT NOT NULL,
                        `evidence_json` TEXT NOT NULL,
                        `headline` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `evidence_line` TEXT NOT NULL,
                        `first_detected_at` TEXT NOT NULL,
                        `last_updated_at` TEXT NOT NULL,
                        `seen_at` TEXT,
                        `resolved_at` TEXT,
                        `suppressed_until` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_findings_state` ON `findings` (`state`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_findings_package` ON `findings` (`subject_package`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `finding_acknowledgements` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `finding_id` TEXT NOT NULL,
                        `response` TEXT NOT NULL,
                        `evidence_fingerprint` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL,
                        `note` TEXT,
                        FOREIGN KEY(`finding_id`) REFERENCES `findings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_finding_ack_finding_id` " +
                        "ON `finding_acknowledgements` (`finding_id`)",
                )

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `behavioural_hypotheses` (
                        `id` TEXT NOT NULL,
                        `question_id` TEXT NOT NULL,
                        `answer_text` TEXT NOT NULL,
                        `answer_package` TEXT,
                        `created_at` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `clarifying_questions` (
                        `id` TEXT NOT NULL,
                        `question_type` TEXT NOT NULL,
                        `date_of_concern` TEXT NOT NULL,
                        `context_json` TEXT NOT NULL,
                        `asked_at` TEXT NOT NULL,
                        `response` TEXT,
                        `responded_at` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_cq_asked_at` " +
                        "ON `clarifying_questions` (`asked_at`)",
                )
            }
        }

        /** Adds the shadow usage pipeline without changing any legacy usage rows. */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `usage_pipeline_state` (
                        `id` INTEGER NOT NULL,
                        `cutover_date` TEXT,
                        `pipeline_version` INTEGER NOT NULL,
                        `shadow_started_on` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR IGNORE INTO `usage_pipeline_state`
                        (`id`, `cutover_date`, `pipeline_version`, `shadow_started_on`)
                    VALUES (1, NULL, 1, date('now', 'localtime'))
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `usage_rollup_day` (
                        `date` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `coverage_start_ms` INTEGER,
                        `coverage_end_ms` INTEGER,
                        `pipeline_version` INTEGER NOT NULL,
                        `computed_at_ms` INTEGER NOT NULL,
                        `total_foreground_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `usage_rollup_app_day` (
                        `date` TEXT NOT NULL,
                        `package_name` TEXT NOT NULL,
                        `app_name` TEXT NOT NULL,
                        `category` TEXT,
                        `foreground_ms` INTEGER NOT NULL,
                        `hourly_ms` TEXT NOT NULL,
                        `launch_count` INTEGER NOT NULL,
                        `session_count` INTEGER NOT NULL,
                        `first_start_at_ms` INTEGER,
                        `last_used_at_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`date`, `package_name`)
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_urad_package_date` " +
                        "ON `usage_rollup_app_day` (`package_name`, `date`)",
                )
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `usage_rollup_session` (
                        `package_name` TEXT NOT NULL,
                        `started_at_ms` INTEGER NOT NULL,
                        `ended_at_ms` INTEGER NOT NULL,
                        `duration_ms` INTEGER NOT NULL,
                        `local_date` TEXT NOT NULL,
                        PRIMARY KEY(`package_name`, `started_at_ms`)
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_urs_date_package` " +
                        "ON `usage_rollup_session` (`local_date`, `package_name`)",
                )
            }
        }

        /**
         * Retains the upgrade edge for already-installed version-7 databases.
         * This migration must remain data-free; an upgrade-day cutover skips
         * the required shadow period. Version 9 repairs stale state instead.
         */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        /**
         * Removes cutover dates written by the old 7→8 migration and restarts
         * the shadow window from the current local day. The insert is harmless
         * when a healthy singleton row already exists.
         */
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE `usage_pipeline_state`
                    SET `cutover_date` = NULL,
                        `shadow_started_on` = date('now', 'localtime')
                    WHERE `cutover_date` IS NOT NULL
                    """.trimIndent(),
                )
                ensurePipelineStateRow(db)
            }
        }

        /** Adds the subtask JSON column while remaining safe on partially migrated databases. */
        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = db.query("PRAGMA table_info(`tasks`)")
                val hasSubtasks = columns.use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    generateSequence {
                        if (cursor.moveToNext()) cursor.getString(nameIndex) else null
                    }.any { it == "subtasks" }
                }
                if (!hasSubtasks) {
                    db.execSQL("ALTER TABLE `tasks` ADD COLUMN `subtasks` TEXT NOT NULL DEFAULT '[]'")
                }
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

        /**
         * One-time migration: reads the legacy TS-app settings JSON blob from the
         * `settings` table and writes individual keys to SharedPreferences.
         * Called AFTER [prepareLegacyDatabase] and AFTER Room is opened (so the DB
         * file is readable), but the keys are only written if they are absent from
         * SharedPreferences (first-launch-only guard).
         *
         * Safe to call on every launch — the guard `if (!prefs.contains(key))` is
         * idempotent.
         */
        fun migrateSettingsBlobToSharedPrefs(context: Context, db: FocusFlowDatabase) {
            try {
                val target = context.getSharedPreferences(
                    AppBlockerAccessibilityService.PREFS_NAME,
                    Context.MODE_PRIVATE,
                )
                // The old FocusFlowPrefs marker intentionally does not suppress this upgrade path.
                if (target.contains(SETTINGS_BLOB_MIGRATED_KEY)) return

                val tableCheck = db.openHelper.readableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='settings'",
                )
                val hasSettingsTable = tableCheck.use { it.moveToFirst() }
                val legacyJson = if (hasSettingsTable) {
                    val cursor = db.openHelper.readableDatabase.query(
                        "SELECT value FROM settings WHERE key = 'app_settings' LIMIT 1",
                    )
                    cursor.use { if (it.moveToFirst()) it.getString(0) else null }
                } else {
                    null
                }
                val plan = LegacySettingsMigration.prepare(hasSettingsTable, legacyJson)
                if (!plan.markComplete) {
                    // A present table with a missing row may be populated later; retry next launch.
                    Log.w(TAG, "'app_settings' row not found; settings migration will retry.")
                    return
                }

                val editor = target.edit()
                plan.preferences.forEach { (key, value) ->
                    if (!target.contains(key)) {
                        when (value) {
                            is LegacyPreferenceValue.StringValue -> editor.putString(key, value.value)
                            is LegacyPreferenceValue.BooleanValue -> editor.putBoolean(key, value.value)
                            is LegacyPreferenceValue.IntValue -> editor.putInt(key, value.value)
                        }
                    }
                }

                editor.putBoolean(SETTINGS_BLOB_MIGRATED_KEY, true)
                if (!editor.commit()) {
                    Log.e(TAG, "Settings blob migration commit failed; it will retry.")
                } else {
                    Log.i(TAG, "Settings blob migrated to focusday_prefs.")
                }
            } catch (error: Exception) {
                // Non-fatal: leave the marker absent so a later launch can retry.
                Log.e(
                    TAG,
                    "Settings blob migration failed; it will retry (${error.javaClass.simpleName}).",
                )
            }
        }

    }
}
