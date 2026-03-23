package com.agendroid.feature.sms.di

import android.content.Context
import androidx.work.WorkManager
import com.agendroid.feature.sms.autonomy.SmsAutonomyPolicy
import com.agendroid.feature.sms.autonomy.WorkEnqueuer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SmsModule {

    @Provides
    @Singleton
    fun provideSmsAutonomyPolicy(): SmsAutonomyPolicy = SmsAutonomyPolicy()

    @Provides
    @Singleton
    fun provideWorkEnqueuer(
        @ApplicationContext context: Context,
    ): WorkEnqueuer = WorkEnqueuer { request ->
        WorkManager.getInstance(context).enqueue(request)
    }
}
