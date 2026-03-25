package com.agendroid.feature.assistant.di

import android.content.Context
import androidx.work.WorkManager
import com.agendroid.feature.assistant.knowledge.WorkRequestEnqueuer
import com.agendroid.core.embeddings.TextChunker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AssistantModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideWorkRequestEnqueuer(
        @ApplicationContext context: Context,
    ): WorkRequestEnqueuer = WorkRequestEnqueuer { request ->
        WorkManager.getInstance(context).enqueue(request)
    }

    @Provides
    @Singleton
    fun provideTextChunker(): TextChunker = TextChunker()
}
