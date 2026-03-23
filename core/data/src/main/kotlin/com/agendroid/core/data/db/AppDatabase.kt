// core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabase.kt
package com.agendroid.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.agendroid.core.data.dao.*
import com.agendroid.core.data.entity.*

@Database(
    entities = [
        ChunkEntity::class,
        KnowledgeDocumentEntity::class,
        ConversationSummaryEntity::class,
        NoteEntity::class,
        ContactPreferenceEntity::class,
        AppSettingsEntity::class,
        PendingSmsReplyEntity::class,
        IndexedSourceEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chunkDao(): ChunkDao
    abstract fun knowledgeDocumentDao(): KnowledgeDocumentDao
    abstract fun conversationSummaryDao(): ConversationSummaryDao
    abstract fun noteDao(): NoteDao
    abstract fun contactPreferenceDao(): ContactPreferenceDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun pendingSmsReplyDao(): PendingSmsReplyDao
    abstract fun indexedSourceDao(): IndexedSourceDao
}
