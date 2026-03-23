package com.agendroid.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migrations for AppDatabase.
 *
 * MIGRATION_1_2: Adds the three settings / management tables introduced in v1 product completion.
 *   - app_settings        (singleton row, global autonomy and model choice)
 *   - pending_sms_replies (AI-drafted SMS replies awaiting send or approval)
 *   - indexed_sources     (file/URL sources tracked for knowledge indexing)
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `app_settings` (
                `id` INTEGER NOT NULL DEFAULT 1,
                `smsAutonomyMode` TEXT NOT NULL,
                `callAutonomyMode` TEXT NOT NULL,
                `assistantEnabled` INTEGER NOT NULL,
                `selectedModel` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_sms_replies` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `threadId` INTEGER NOT NULL,
                `sender` TEXT NOT NULL,
                `draftText` TEXT NOT NULL,
                `scheduledSendAt` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `indexed_sources` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sourceType` TEXT NOT NULL,
                `uri` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `indexedAt` INTEGER NOT NULL,
                `status` TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}
