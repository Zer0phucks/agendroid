package com.agendroid.core.data.repository

import dagger.Binds
import dagger.Module
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
}
