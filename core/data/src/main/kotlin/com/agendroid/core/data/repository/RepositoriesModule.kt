package com.agendroid.core.data.repository

import com.agendroid.core.data.dao.AppSettingsDao
import com.agendroid.core.data.dao.IndexedSourceDao
import com.agendroid.core.data.dao.PendingSmsReplyDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoriesModule {

    @Binds @Singleton
    abstract fun bindKnowledgeIndexRepository(impl: KnowledgeIndexRepositoryImpl): KnowledgeIndexRepository

    @Binds @Singleton
    abstract fun bindContactsRepository(impl: ContactsRepositoryImpl): ContactsRepository

    @Binds @Singleton
    abstract fun bindSmsThreadRepository(impl: SmsThreadRepositoryImpl): SmsThreadRepository

    @Binds @Singleton
    abstract fun bindCallLogRepository(impl: CallLogRepositoryImpl): CallLogRepository

    companion object {

        @Provides @Singleton
        fun provideAppSettingsRepository(dao: AppSettingsDao): AppSettingsRepository =
            AppSettingsRepository(dao)

        @Provides @Singleton
        fun providePendingSmsReplyRepository(dao: PendingSmsReplyDao): PendingSmsReplyRepository =
            PendingSmsReplyRepository(dao)

        @Provides @Singleton
        fun provideIndexedSourceRepository(dao: IndexedSourceDao): IndexedSourceRepository =
            IndexedSourceRepository(dao)
    }
}
