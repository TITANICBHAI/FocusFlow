package com.tbtechs.focusflow.data.local

import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the highest-risk schema transition.
 *
 * The setup uses the same table/column names as the RN database, including both
 * report note types and duplicate active sessions. The migration must preserve
 * both notes and deterministically keep the newest active session.
 */
@RunWith(AndroidJUnit4::class)
class FocusFlowDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FocusFlowDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrateV9AddsEmptySubtasksAndPreservesExistingTaskColumns() {
        helper.createDatabase(SUBTASK_MIGRATION_DB_NAME, 9).apply {
            execSQL(
                """
                INSERT INTO tasks (
                    id, title, description, start_time, end_time, duration_minutes,
                    status, priority, tags, reminders, color, focus_mode,
                    focus_allowed_packages, created_at, updated_at
                ) VALUES (
                    'task-before-10', 'Keep this task', 'notes',
                    '2026-10-20T08:00:00Z', '2026-10-20T09:00:00Z', 60,
                    'scheduled', 'high', '["training"]', '[]', '#123456', 1,
                    '["pkg.allowed"]', '2026-10-19T08:00:00Z', '2026-10-19T09:00:00Z'
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            SUBTASK_MIGRATION_DB_NAME,
            10,
            true,
            FocusFlowDatabase.MIGRATION_9_10,
        )
        migrated.query(
            "SELECT title, updated_at, subtasks FROM tasks WHERE id = 'task-before-10'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Keep this task", cursor.getString(0))
            assertEquals("2026-10-19T09:00:00Z", cursor.getString(1))
            assertEquals("[]", cursor.getString(2))
        }
        migrated.close()
    }

    @Test
    fun migrateV4PreservesReportNotesAndReconcilesActiveSessions() {
        helper.createDatabase(DB_NAME, 4).apply {
            FocusFlowDatabase.MIGRATION_0_1.migrate(this)
            FocusFlowDatabase.MIGRATION_1_2.migrate(this)
            FocusFlowDatabase.MIGRATION_2_3.migrate(this)
            FocusFlowDatabase.MIGRATION_3_4.migrate(this)

            execSQL(
                """
                CREATE TABLE report_notes (
                    ref_date TEXT NOT NULL,
                    type TEXT NOT NULL,
                    note TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY(ref_date, type)
                )
                """.trimIndent(),
            )
            execSQL(
                "INSERT INTO report_notes VALUES ('2026-09-18', 'day', 'day note', '2026-09-18T10:00:00Z')",
            )
            execSQL(
                "INSERT INTO report_notes VALUES ('2026-09-15', 'week', 'week note', '2026-09-15T10:00:00Z')",
            )
            execSQL(
                """
                INSERT INTO focus_sessions
                    (task_id, started_at, ended_at, is_active, allowed_packages)
                VALUES
                    ('old', '2026-09-20T08:00:00Z', NULL, 1, '[]'),
                    ('new', '2026-09-20T09:00:00Z', NULL, 1, '[]')
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME,
            5,
            true,
            FocusFlowDatabase.MIGRATION_4_5,
        )

        migrated.query(
            "SELECT COUNT(*) FROM report_notes WHERE type IN ('day', 'week')",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM focus_sessions WHERE is_active = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query(
            "SELECT task_id FROM focus_sessions WHERE is_active = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("new", cursor.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migrateV6AddsShadowTablesWithoutChangingLegacyUsageRows() {
        helper.createDatabase(USAGE_DB_NAME, 6).apply {
            execSQL(
                """
                INSERT INTO daily_app_usage
                    (date, package_name, app_name, category, foreground_ms, hourly_ms, launch_count, last_used_at)
                VALUES ('2026-10-07', 'app.example', 'Example', 'utility', 123456,
                        '1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24', 3, 1791331200000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO app_sessions
                    (package_name, app_name, started_at, ended_at, duration_ms, local_date)
                VALUES ('app.example', 'Example', 1791390000000, 1791390123456, 123456, '2026-10-07')
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            USAGE_DB_NAME,
            9,
            true,
            FocusFlowDatabase.MIGRATION_6_7,
            FocusFlowDatabase.MIGRATION_7_8,
            FocusFlowDatabase.MIGRATION_8_9,
        )
        val expectedShadowStart = localToday(migrated)
        migrated.query(
            """
            SELECT date, package_name, app_name, category, foreground_ms, hourly_ms,
                   launch_count, last_used_at
            FROM daily_app_usage
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-10-07", cursor.getString(0))
            assertEquals("app.example", cursor.getString(1))
            assertEquals("Example", cursor.getString(2))
            assertEquals("utility", cursor.getString(3))
            assertEquals(123456L, cursor.getLong(4))
            assertEquals(
                "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24",
                cursor.getString(5),
            )
            assertEquals(3, cursor.getInt(6))
            assertEquals(1791331200000L, cursor.getLong(7))
            assertFalse(cursor.moveToNext())
        }
        migrated.query(
            """
            SELECT id, package_name, app_name, started_at, ended_at, duration_ms, local_date
            FROM app_sessions
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals("app.example", cursor.getString(1))
            assertEquals("Example", cursor.getString(2))
            assertEquals(1791390000000L, cursor.getLong(3))
            assertEquals(1791390123456L, cursor.getLong(4))
            assertEquals(123456L, cursor.getLong(5))
            assertEquals("2026-10-07", cursor.getString(6))
            assertFalse(cursor.moveToNext())
        }
        migrated.query(
            "SELECT cutover_date, pipeline_version, shadow_started_on FROM usage_pipeline_state WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(expectedShadowStart, cursor.getString(2))
        }
        listOf("usage_rollup_day", "usage_rollup_app_day", "usage_rollup_session").forEach { table ->
            migrated.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
        migrated.close()
    }

    @Test
    fun migrateV7To9LeavesTheV7To8StepDataFreeAndRepairsStaleCutover() {
        helper.createDatabase(V7_DB_NAME, 7).apply {
            execSQL(
                """
                INSERT INTO usage_pipeline_state
                    (id, cutover_date, pipeline_version, shadow_started_on)
                VALUES (1, '2026-10-01', 1, '2026-09-20')
                """.trimIndent(),
            )
            close()
        }

        val versionEight = helper.runMigrationsAndValidate(
            V7_DB_NAME,
            8,
            true,
            FocusFlowDatabase.MIGRATION_7_8,
        )
        versionEight.query(
            "SELECT cutover_date, shadow_started_on FROM usage_pipeline_state WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-10-01", cursor.getString(0))
            assertEquals("2026-09-20", cursor.getString(1))
        }
        versionEight.close()

        val migrated = helper.runMigrationsAndValidate(
            V7_DB_NAME,
            9,
            true,
            FocusFlowDatabase.MIGRATION_8_9,
        )
        migrated.query(
            "SELECT cutover_date, shadow_started_on FROM usage_pipeline_state WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertEquals(localToday(migrated), cursor.getString(1))
        }
        migrated.close()
    }

    @Test
    fun migrateV8To9ClearsStaleCutoverAndResetsShadowStart() {
        helper.createDatabase(V8_DB_NAME, 8).apply {
            execSQL(
                """
                INSERT INTO usage_pipeline_state
                    (id, cutover_date, pipeline_version, shadow_started_on)
                VALUES (1, '2026-10-01', 1, '2026-09-20')
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            V8_DB_NAME,
            9,
            true,
            FocusFlowDatabase.MIGRATION_8_9,
        )
        migrated.query(
            "SELECT cutover_date, shadow_started_on FROM usage_pipeline_state WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertEquals(localToday(migrated), cursor.getString(1))
        }
        migrated.close()
    }

    @Test
    fun migrateV8To9SeedsMissingPipelineState() {
        helper.createDatabase(MISSING_STATE_DB_NAME, 8).apply {
            execSQL("DELETE FROM usage_pipeline_state")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            MISSING_STATE_DB_NAME,
            9,
            true,
            FocusFlowDatabase.MIGRATION_8_9,
        )
        migrated.query(
            "SELECT cutover_date, pipeline_version, shadow_started_on " +
                "FROM usage_pipeline_state WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(localToday(migrated), cursor.getString(2))
        }
        migrated.close()
    }

    @Test
    fun freshRoomDatabaseSeedsPipelineStateAfterSchemaCreation() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        targetContext.deleteDatabase(FRESH_INSTALL_DB_NAME)
        val database = Room.databaseBuilder(
            targetContext,
            FocusFlowDatabase::class.java,
            FRESH_INSTALL_DB_NAME,
        )
            .addCallback(FocusFlowDatabase.DATABASE_CALLBACK)
            .build()

        try {
            val db = database.openHelper.writableDatabase
            db.query(
                "SELECT cutover_date, pipeline_version, shadow_started_on " +
                    "FROM usage_pipeline_state WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(localToday(db), cursor.getString(2))
            }
        } finally {
            database.close()
            targetContext.deleteDatabase(FRESH_INSTALL_DB_NAME)
        }
    }

    @Test
    fun legacySchemaCanReachVersionNineThroughTheFullMigrationChain() {
        helper.createDatabase(FRESH_DB_NAME, 0).apply {
            FocusFlowDatabase.MIGRATION_0_1.migrate(this)
            FocusFlowDatabase.MIGRATION_1_2.migrate(this)
            FocusFlowDatabase.MIGRATION_2_3.migrate(this)
            FocusFlowDatabase.MIGRATION_3_4.migrate(this)
            FocusFlowDatabase.MIGRATION_4_5.migrate(this)
            FocusFlowDatabase.MIGRATION_5_6.migrate(this)
            execSQL(
                """
                INSERT INTO daily_app_usage
                    (date, package_name, app_name, category, foreground_ms, hourly_ms,
                     launch_count, last_used_at)
                VALUES ('2026-10-07', 'app.fresh', 'Fresh', 'other', 9, '', 1, 10)
                """.trimIndent(),
            )
            execSQL("PRAGMA user_version = 6")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            FRESH_DB_NAME,
            9,
            true,
            FocusFlowDatabase.MIGRATION_6_7,
            FocusFlowDatabase.MIGRATION_7_8,
            FocusFlowDatabase.MIGRATION_8_9,
        )
        migrated.query(
            "SELECT foreground_ms FROM daily_app_usage WHERE package_name = 'app.fresh'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(9L, cursor.getLong(0))
        }
        migrated.query("SELECT cutover_date FROM usage_pipeline_state WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        migrated.close()
    }

    @Test
    fun legacyUserVersionZeroDatabaseIsPreparedAndUpgradedToVersionNine() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseFile = File(targetContext.cacheDir, LEGACY_USER_VERSION_ZERO_DB_NAME)
        databaseFile.delete()
        val legacyContext = object : ContextWrapper(targetContext) {
            override fun getDatabasePath(name: String): File = databaseFile

            override fun openOrCreateDatabase(
                name: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?,
            ): SQLiteDatabase = SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                factory,
                mode or SQLiteDatabase.CREATE_IF_NECESSARY,
            )

            override fun openOrCreateDatabase(
                name: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?,
                errorHandler: DatabaseErrorHandler?,
            ): SQLiteDatabase = SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                factory,
                mode or SQLiteDatabase.CREATE_IF_NECESSARY,
                errorHandler,
            )
        }

        try {
            val legacyOpenHelper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(legacyContext)
                    .name(FocusFlowDatabase.DB_NAME)
                    .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            FocusFlowDatabase.MIGRATION_0_1.migrate(db)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                            error("Unexpected upgrade while creating the legacy test database")
                        }
                    })
                    .build(),
            )
            legacyOpenHelper.writableDatabase.use { it.version = 0 }
            legacyOpenHelper.close()

            FocusFlowDatabase.prepareLegacyDatabase(legacyContext)

            val preparedDb = SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            try {
                assertEquals(1, preparedDb.version)
                preparedDb.rawQuery("PRAGMA table_info(tasks)", null).use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    val columns = buildList {
                        while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                    }
                    assertTrue("focus_allowed_packages" in columns)
                }
            } finally {
                preparedDb.close()
            }

            val database = Room.databaseBuilder(
                legacyContext,
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
            try {
                val migrated = database.openHelper.writableDatabase
                assertEquals(10, migrated.version)
                migrated.query(
                    "SELECT cutover_date, pipeline_version, shadow_started_on " +
                        "FROM usage_pipeline_state WHERE id = 1",
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertTrue(cursor.isNull(0))
                    assertEquals(1, cursor.getInt(1))
                    assertNotNull(cursor.getString(2))
                }
            } finally {
                database.close()
            }
        } finally {
            databaseFile.delete()
        }
    }

    private fun localToday(db: SupportSQLiteDatabase): String =
        db.query("SELECT date('now', 'localtime')").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }

    companion object {
        private const val DB_NAME = "focusflow-migration-test.db"
        private const val SUBTASK_MIGRATION_DB_NAME = "focusflow-migration-v9-to-v10.db"
        private const val USAGE_DB_NAME = "focusflow-usage-migration-test.db"
        private const val FRESH_DB_NAME = "focusflow-usage-zero-migration-test.db"
        private const val V7_DB_NAME = "focusflow-migration-v7-to-v9.db"
        private const val V8_DB_NAME = "focusflow-migration-v8-to-v9.db"
        private const val MISSING_STATE_DB_NAME = "focusflow-migration-missing-state.db"
        private const val FRESH_INSTALL_DB_NAME = "focusflow-fresh-install-state.db"
        private const val LEGACY_USER_VERSION_ZERO_DB_NAME = "legacy-user-version-zero-test.db"
    }
}