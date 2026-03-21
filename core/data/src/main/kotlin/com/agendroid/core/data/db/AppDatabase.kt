// core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabase.kt
package com.agendroid.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.agendroid.core.data.dao.ChunkDao
import com.agendroid.core.data.dao.ConversationSummaryDao
import com.agendroid.core.data.dao.KnowledgeDocumentDao
import com.agendroid.core.data.entity.ChunkEntity
import com.agendroid.core.data.entity.ConversationSummaryEntity
import com.agendroid.core.data.entity.KnowledgeDocumentEntity

@Database(
    entities = [
        ChunkEntity::class,
        KnowledgeDocumentEntity::class,
        ConversationSummaryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chunkDao(): ChunkDao
    abstract fun knowledgeDocumentDao(): KnowledgeDocumentDao
    abstract fun conversationSummaryDao(): ConversationSummaryDao
}
