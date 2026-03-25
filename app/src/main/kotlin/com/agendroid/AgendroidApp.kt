package com.agendroid

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.agendroid.feature.assistant.health.ServiceHealthWatchdog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AgendroidApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var serviceHealthWatchdog: ServiceHealthWatchdog

    override fun onCreate() {
        super.onCreate() // Hilt field injection fires here
        serviceHealthWatchdog // access the field to ensure singleton is constructed and init{} runs
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
