// core/data/src/main/kotlin/com/agendroid/core/data/db/DatabaseModule.kt
package com.agendroid.core.data.db

import android.content.Context
import androidx.room.Room
import com.agendroid.core.data.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keyManager: KeystoreKeyManager,
    ): AppDatabase {
        // sqlcipher-android 4.x does not auto-load its native library; it must
        // be loaded before any database call is made.
        System.loadLibrary("sqlcipher")

        // sqlcipher-android 4.5.x uses net.zetetic.database.sqlcipher.SupportOpenHelperFactory
        // and accepts the raw passphrase as ByteArray directly.
        val factory = SupportOpenHelperFactory(keyManager.getOrCreateKey())
        return Room.databaseBuilder(context, AppDatabase::class.java, "agendroid.db")
            .openHelperFactory(factory)
            .build()
    }

    @Provides @Singleton fun provideChunkDao(db: AppDatabase): ChunkDao = db.chunkDao()
    @Provides @Singleton fun provideKnowledgeDocumentDao(db: AppDatabase): KnowledgeDocumentDao = db.knowledgeDocumentDao()
    @Provides @Singleton fun provideConversationSummaryDao(db: AppDatabase): ConversationSummaryDao = db.conversationSummaryDao()
    @Provides @Singleton fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()
    @Provides @Singleton fun provideContactPreferenceDao(db: AppDatabase): ContactPreferenceDao = db.contactPreferenceDao()
}
