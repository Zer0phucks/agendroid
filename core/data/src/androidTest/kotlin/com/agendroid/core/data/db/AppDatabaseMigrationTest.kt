package com.agendroid.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test.db"

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration_1_to_2_preserves_existing_tables_and_creates_settings_tables() {
        // Create version 1 database with an existing row in a known table.
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO notes (id, title, body, createdAt, updatedAt) VALUES (1, 'hello', 'world', 0, 0)"
            )
        }

        // Run migration 1 → 2.
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // Existing data must still be present.
        db.query("SELECT * FROM notes WHERE id = 1").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("hello", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        }

        // New tables must exist.
        db.query("SELECT * FROM app_settings").use { cursor ->
            assertNotNull(cursor)
            assertEquals(0, cursor.count) // empty on first migration — no default row inserted
        }
        db.query("SELECT * FROM pending_sms_replies").use { cursor ->
            assertNotNull(cursor)
            assertEquals(0, cursor.count)
        }
        db.query("SELECT * FROM indexed_sources").use { cursor ->
            assertNotNull(cursor)
            assertEquals(0, cursor.count)
        }
    }

    @Test
    fun appSettings_roundTrips_global_autonomy_and_model_choice() {
        helper.createDatabase(TEST_DB, 1).use { /* create v1 */ }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // Insert a settings row and verify it round-trips correctly.
        db.execSQL(
            """
            INSERT INTO app_settings (id, smsAutonomyMode, callAutonomyMode, assistantEnabled, selectedModel)
            VALUES (1, 'AUTO', 'FULL_AGENT', 1, 'gemma3-2b')
            """.trimIndent()
        )

        db.query("SELECT * FROM app_settings WHERE id = 1").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("AUTO", cursor.getString(cursor.getColumnIndexOrThrow("smsAutonomyMode")))
            assertEquals("FULL_AGENT", cursor.getString(cursor.getColumnIndexOrThrow("callAutonomyMode")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("assistantEnabled")))
            assertEquals("gemma3-2b", cursor.getString(cursor.getColumnIndexOrThrow("selectedModel")))
        }
    }
}
